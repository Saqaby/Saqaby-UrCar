package sa.urcar.home;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.session.MediaController;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.Calendar;

/**
 * Keeps the panel floating on the driver's side (probe v5: the map stays live and touchable beside it) while the
 * navigation app or our home screen is in front, and hides it over any other app. Also: GPS, the temperature,
 * the adhan at each prayer time, the music, the Quran radio, and the daily update check.
 */
public class PanelService extends Service implements Panel.Actions {
    static PanelService live;

    private final Handler h = new Handler(Looper.getMainLooper());
    private Panel panel;     // the home screen's two halves, while it is open
    private Drive drive;
    private Quotes quotes;
    private float kmh = -1, bearing = -1, lastBearing = -1;
    private long fixAt;
    // the road's speed limit (OpenStreetMap, in the app)
    private int limit;
    private long limitAt;
    private double limitLat = 999, limitLon;
    // the trip: starts when the car moves after an hour without moving (or when the screen starts)
    private long tripStart, lastMoveAt, duaUntil, savedMoveAt;
    private double tripKm, prevLat = 999, prevLon;
    private String duaKind = "riding";   // what the quote card shows for its minute: riding, travel or home
    /** A stop this long, the car on or off, makes the next move a new trip (the riding dhikr). */
    static final long STOP_MS = 25 * 60_000L;
    private double dust = -1;
    private long dustAt;
    private final java.util.Map<String, String[]> duas = new java.util.HashMap<>();
    static volatile boolean prefsChanged;
    private final Day day = new Day();
    private Sound sound;
    private Khatma khatma;
    private Listen library;
    private long stillSince, restUntil;
    private int restFor;
    private boolean khatmaPausedByStop, khatmaMoved;
    private double[] wx;   // {°C, code}
    private long wxAt, fmAt, updAt;
    private double wxLat, wxLon;
    private String lastFg = "", playedFor = "";
    private LocationManager lm;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        live = this;
        Crash.install(this);
        // a screen that used the app before the channels and the first-run setup (version 41) keeps the test channel
        // and skips the setup; a new one gets the stable channel and the setup
        android.content.SharedPreferences pr = Prefs.of(this);
        if (!pr.contains("channel")) {
            boolean before = pr.contains("lat") || pr.contains("trip_start") || pr.contains("k_surah");
            pr.edit().putString("channel", before ? "test" : "stable").putBoolean("setup_done", before).apply();
        }
        startForeground(1, notification());
        quotes = new Quotes(this);
        try {
            java.io.InputStream in = getAssets().open("duas.json");
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            in.close();
            org.json.JSONObject j = new org.json.JSONObject(b.toString("UTF-8"));
            for (String k : new String[]{"riding", "travel", "home"}) {
                org.json.JSONObject d = j.optJSONObject(k);
                if (d != null) duas.put(k, new String[]{d.getString("title"), d.getString("text"), d.getString("src")});
            }
        } catch (Exception ignored) { }
        sound = new Sound(this);
        sound.changed = () -> h.post(this::drawRadio);
        khatma = new Khatma(this);
        new java.io.File(getFilesDir(), "soundlog.txt").delete();   // the v27 test log, no longer kept
        khatma.changed = () -> h.post(this::drawRadio);
        library = new Listen(this);
        library.changed = () -> h.post(this::drawRadio);
        day.lat = Prefs.of(this).getFloat("lat", (float) day.lat);
        day.lon = Prefs.of(this).getFloat("lon", (float) day.lon);
        startLocation();
        new Thread(() -> { sound.cacheAdhan(); Net.refreshFm(this); }).start();
        h.post(tick);
    }

    @Override public int onStartCommand(Intent i, int f, int id) { h.post(this::applyPrefs); return START_STICKY; }

    @Override public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        sound.release();
        if (strip != null) strip.show(false);
        khatma.stop();
        library.stop();
        if (lm != null) try { lm.removeUpdates(gps); } catch (Exception ignored) { }
        if (live == this) live = null;
        super.onDestroy();
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("panel", "لوحة سيارتك", NotificationManager.IMPORTANCE_MIN));
        return new Notification.Builder(this, "panel").setContentTitle("سيارتك تعمل").setSmallIcon(android.R.drawable.ic_menu_compass).build();
    }

    // ---------------------------------------------------------------- the home screen's views
    /** HomeActivity hands its two halves over while it is open; the service fills them every second. */
    void attach(Panel p, Drive d) { panel = p; drive = d; if (p != null) draw(System.currentTimeMillis()); }
    /** After «الإعدادات»: the home screen builds itself again (the driver's side may have changed). */
    void applyPrefs() { prefsChanged = true; }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            adhan(now);
            restAndStop(now);
            guardKhatma();
            askLimit(now);
            homeTick(now);
            glare(now);
            askCity(now);
            if (panel != null && HomeActivity.resumed) draw(now);
            strip(now);
            background(now);
            h.postDelayed(this, 1000);
        }
    };

    // ---------------------------------------------------------------- what the panel shows
    private void draw(long now) {
        day.ensure(now);
        boolean d = themeDay(now);
        if (d != panel.isDay()) { panel.setDay(d); if (drive != null) drive.setDay(d); }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int hh = c.get(Calendar.HOUR_OF_DAY), h12 = hh % 12 == 0 ? 12 : hh % 12;
        panel.showTime(Ui.ar(h12 + ":" + Ui.pad(c.get(Calendar.MINUTE))), hh < 12 ? "ص" : "م", Day.hijriText(now), Day.gregText(now));
        panel.showPlace(day.placeName());
        if (wx != null) {
            int icon = Ui.wxIcon((int) wx[1], day.night(now));
            panel.showWeather(Ui.ar(Math.round(wx[0])) + "°", icon, now - wxAt > 3 * 3600_000L);
        } else panel.showWeather("", Ui.Icon.SUN, false);
        String iq = iqamaBar(now);
        panel.showBar(iq != null ? iq : day.bar(now));
        int cur = day.current(now);
        if (cur >= 0) {
            int since = (int) ((now - day.at(cur)) / 60_000L);
            panel.showPrayer(since < 1 ? "حان الآن" : "الصلاة الحالية", day.name(cur, now), Day.clockOf(day.at(cur)),
                    since < 1 ? "وقت الأذان" : "مضى على الأذان " + Ui.minutes(since), day.ramadan(now), day.ramadanDays(now), true);
        } else {
            long[] n = day.next(now);
            panel.showPrayer("الصلاة القادمة", day.name((int) n[0], now), Day.clockOf(n[1]),
                    "باقٍ " + Ui.remaining(Math.max(60, Math.round((n[1] - now) / 1000.0))), day.ramadan(now), day.ramadanDays(now), false);
            // half an hour before an adhan the card turns back to the prayer, once
            String key = n[0] + "@" + n[1];
            if (n[1] - now <= Day.BEFORE_MIN * 60_000L && !key.equals(turnedFor)) { turnedFor = key; panel.showPage("p"); }
        }
        String[] dn = new String[5], dc = new String[5];
        int hot = cur;
        if (hot < 0) { long[] n = day.next(now); hot = n[1] == day.at((int) n[0]) ? (int) n[0] : -1; }   // tomorrow's Fajr: none of today's
        int hotAt = -1;
        for (int i = 0; i < 5; i++) {
            int k = Day.FIVE[i];
            dn[i] = day.name(k, now);
            String t = Day.clockOf(day.at(k));
            dc[i] = t.substring(0, t.indexOf(' '));
            if (k == hot) hotAt = i;
        }
        panel.showDay(dn, dc, hotAt);
        MediaController mc = NotiListener.session(this);
        panel.showMusic(mc, mc == null ? "" : NotiListener.appName(this, mc.getPackageName()),
                mc == null ? "" : NotiListener.title(mc), mc == null ? "" : NotiListener.artist(mc), mc != null && NotiListener.playing(mc));
        drawRadio();
        if (drive != null) {
            boolean fresh = now - fixAt < 6000, moving = fresh && kmh >= 10;
            drive.showSpeed(fresh ? Math.round(kmh) : (fixAt == 0 ? -1 : 0), fresh ? bearing : -1, limitNow());
            drive.showQibla(day.qibla());
            // Radarbot first; else dust (dustNow)
            String alert = now - NotiListener.radarAt < 20_000 ? NotiListener.radar : null;
            if (alert == null && now < glareUntil) alert = "🕶 الشمس أمامك مباشرة";
            boolean rest = false;
            if (alert == null && now < restUntil) { alert = "قدت " + Ui.hours((int) ((now - tripStart) / 3600_000L)) + "، قف للراحة. اضغط لأقرب محطة"; rest = true; }
            drive.setAlertAction(rest ? this::station : null);
            if (alert == null && dustNow(now)) alert = wx[2] < 1000 ? "غبار كثيف في موقعك: خفّف السرعة وأشعل الأنوار" : "غبار في موقعك";
            if (alert == null) { alert = catchPrayer(now); if (alert != null) drive.setAlertAction(this::mosque); }
            drive.showAlert(alert);
            suggestLine(now);
            drive.showPlaces(Smart.on(this, "places", true) && !Smart.places(this).isEmpty());
            drive.setMosqueHot(day.bar(now) != null);
            drive.setMosqueAhead(mosqueAhead(now));
            drive.showTrip(join(tripNow(), homeNow()));
            drive.showHome(homeBearing());
            drive.showNav(NotiListener.navigating(), NotiListener.navDist, NotiListener.navStep, NotiListener.navEta, NotiListener.navIcon);
            drive.showRemind(day.remind(now));
            // the quote card: the riding (or travel) dhikr for a minute when a trip starts, else the hour's quote
            if (now < duaUntil && duas.get(duaKind) != null) drive.showQuote(duas.get(duaKind), moving);
            else drive.showQuote(quotes.at(now), moving);   // one text the whole hour: v7 took a short one while moving, so each stop changed it
        }
    }
    private String turnedFor = "";

    /**
     * Dust only when it is seen: the modelled dust (Open-Meteo's air quality, a forecast for a wide square) at 150 µg/m³
     * or more, and the visibility under 5 km, with no rain, drizzle or fog to explain it (weather code 0-3). The owner
     * saw «غبار» on a clear day (the model at 200, the visibility 17 km) and «غبار كثيف» under rain clouds (the
     * visibility 2 km from the rain). Dense: under 1 km.
     */
    private boolean dustNow(long now) {
        if (dust < 150 || now - dustAt > 3 * 3600_000L || wx == null || wx.length < 3) return false;
        double vis = wx[2];
        int code = (int) wx[1];
        return vis >= 0 && vis < 5000 && code <= 3;
    }


    private boolean themeDay(long now) {
        String t = Prefs.theme(this);
        return t.equals("light") || (t.equals("auto") && !day.night(now));
    }

    /**
     * The owner heard the khatma go silent under a Bluetooth call or the phone's music and go on unheard, without
     * Android telling it (the screen's Bluetooth app takes no audio focus). So every second, while it plays:
     * a call (the audio mode, or a call's sound playing) or another app's media session that begins to play (YouTube,
     * «الجوال (بلوتوث)», a notification's sound …) pauses it, and it goes on by itself after 2 seconds of quiet (v26
     * stopped it for good on another sound: the owner wanted it back). A session already playing when the khatma
     * started does not count, until it stops and plays again; its button makes it go on over whatever plays.
     */
    private final java.util.Set<String> playingBefore = new java.util.HashSet<>();
    private boolean khatmaWasOn;
    private void guardKhatma() {
        if (!khatma.on) { khatmaWasOn = false; heldWhy = ""; return; }
        java.util.Set<String> now = NotiListener.playingPackages(this);
        if (!khatmaWasOn) { khatmaWasOn = true; playingBefore.clear(); playingBefore.addAll(now); quiet = 0; }
        playingBefore.retainAll(now);   // one that stopped playing counts again when it plays again
        String why = inCall() ? "مكالمة" : null;
        if (why == null) for (String pkg : now) if (!playingBefore.contains(pkg)) { why = NotiListener.appName(this, pkg) + " يعمل"; break; }
        if (why != null) {
            quiet = 0;
            if (khatma.held() == null) { khatma.hold("other"); Prefs.put(this, "k_err", why); }
            if (!why.equals(heldWhy)) { heldWhy = why; drawRadio(); }
            return;
        }
        if (khatma.held() != null && !"focus".equals(khatma.held()) && ++quiet >= 2) {   // 2 seconds of quiet: it goes on
            heldWhy = "";
            khatma.resume();
        }
    }
    private int quiet;
    private String heldWhy = "";
    /** The khatma's button while it waits: it goes on now, and what is playing now is let be. */
    private void khatmaGoOn() {
        playingBefore.addAll(NotiListener.playingPackages(this));
        heldWhy = "";
        khatma.resume();
    }
    private boolean inCall() {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        int m = am.getMode();
        if (m == android.media.AudioManager.MODE_IN_CALL || m == android.media.AudioManager.MODE_IN_COMMUNICATION || m == android.media.AudioManager.MODE_RINGTONE) return true;
        try {   // a call's sound playing (isActive is hidden: if Android refuses it, this signal is skipped)
            java.lang.reflect.Method act = android.media.AudioPlaybackConfiguration.class.getMethod("isActive");
            for (android.media.AudioPlaybackConfiguration pc : am.getActivePlaybackConfigurations()) {
                int u = pc.getAudioAttributes().getUsage();
                if ((u == android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION || u == android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        && (Boolean) act.invoke(pc)) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /** Every two hours of a trip, a minute's reminder to rest; the khatma pauses after 3 minutes still and goes on when moving. */
    private void restAndStop(long now) {
        boolean moving = now - fixAt < 6000 && kmh >= 10;
        if (tripStart > 0) {
            int hours = (int) ((now - tripStart) / 3600_000L);
            if (hours >= 2 && hours % 2 == 0 && hours != restFor && moving) { restFor = hours; restUntil = now + 60_000; }
        }
        // only a khatma that played while the car moved pauses after 3 minutes still: one started while parked plays on
        if (moving) {
            stillSince = 0;
            if (khatma.on) khatmaMoved = true;
            if (khatmaPausedByStop) { khatmaPausedByStop = false; if (!library.on()) khatma.start(); }
        } else {
            if (stillSince == 0) stillSince = now;
            if (khatma.on && khatmaMoved && now - stillSince > 3 * 60_000L) { khatma.stop(); khatmaPausedByStop = true; khatmaMoved = false; }
        }
    }

    private void drawRadio() {
        if (panel == null) return;
        panel.showLibrary(library.on());
        if (khatma.on && khatma.held() != null)   // waiting for a call or another sound to end
            panel.showKhatma("الختمة تنتظر", (heldWhy.isEmpty() ? "صوت آخر" : heldWhy) + "، اضغط لتكمل", true);
        else panel.showKhatma(khatma.on ? "إيقاف الختمة" : "ختمة السيارة", Ui.ar(khatma.percent()) + "٪، " + khatma.surahName()
                + (khatma.done() > 0 ? "، الختمة " + Ui.ar(khatma.done() + 1) : ""), khatma.on);
        Object[] fm = Prefs.radioSource(this).equals("fm") ? Net.fmHere(this, day.lat, day.lon) : null;
        panel.showRadio(sound.radioOn ? "إيقاف الإذاعة" : "إذاعة القرآن",
                fm != null ? "FM " + Ui.ar(String.format(java.util.Locale.US, "%.1f", ((Number) fm[1]).doubleValue())) : (sound.radioOn ? "" : "عبر الإنترنت"), sound.radioOn);
    }

    /** The adhan at the minute of each prayer, once. */
    private void adhan(long now) {
        day.ensure(now);
        int cur = day.current(now);
        if (cur < 0) return;
        String key = cur + "@" + day.at(cur);
        if (now - day.at(cur) < 60_000L && !key.equals(playedFor)) { playedFor = key; sound.playAdhan(Prefs.of(this).getString("adhan", "full"), Prefs.of(this).getFloat("adhan_vol", 1f)); }
    }

    /** The temperature every 30 minutes or after 10 km, the FM table daily, the update check every 6 hours. */
    private void background(long now) {
        if (!Net.online(this)) return;
        boolean moved = Day.km(wxLat, wxLon, day.lat, day.lon) > 10;
        if (now - wxAt > 30 * 60_000L || moved) {
            wxAt = now; wxLat = day.lat; wxLon = day.lon;
            final double la = day.lat, lo = day.lon;
            new Thread(() -> {
                double[] r = Net.weather(la, lo);
                if (r != null) h.post(() -> { wx = r; wxAt = System.currentTimeMillis(); });
                double d = Net.dust(la, lo);
                if (d >= 0) h.post(() -> { dust = d; dustAt = System.currentTimeMillis(); });
            }).start();
        }
        if (now - fmAt > 24 * 3600_000L) { fmAt = now; new Thread(() -> Net.refreshFm(this)).start(); }
        if (now - updAt > 6 * 3600_000L) { updAt = now; new Thread(() -> Updater.check(this, false)).start(); }
    }

    // ---------------------------------------------------------------- GPS
    private final LocationListener gps = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            day.lat = l.getLatitude(); day.lon = l.getLongitude();
            if (LocationManager.GPS_PROVIDER.equals(l.getProvider()) || l.hasSpeed()) {
                kmh = l.hasSpeed() ? l.getSpeed() * 3.6f : 0;
                trip(l, System.currentTimeMillis());
                // GPS gives a heading only while moving; below 5 km/h it wanders, so the last one stays
                bearing = l.hasBearing() && kmh >= 5 ? l.getBearing() : -1;
                fixAt = System.currentTimeMillis();
            }
            Prefs.of(PanelService.this).edit().putFloat("lat", (float) day.lat).putFloat("lon", (float) day.lon).apply();
        }
        @Override public void onStatusChanged(String p, int s, Bundle b) { }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { }
    };
    /** A trip starts when the car moves (10 km/h) after 25 minutes or more without moving, with the screen on or off (the
     *  last move is kept on the screen, so a restart of the screen alone does not count as a stop). */
    private void trip(Location l, long now) {
        if (kmh >= 10) {
            if (lastMoveAt == 0) {   // the screen (re)started: a short stop goes on with the saved trip
                lastMoveAt = Prefs.of(this).getLong("last_move", 0);
                if (now - lastMoveAt < STOP_MS) {
                    tripStart = Prefs.of(this).getLong("trip_start", 0);
                    tripKm = Prefs.of(this).getFloat("trip_km", 0);
                    if (tripStart == 0) tripStart = now;   // a trip from before this version: counted from now
                }
            }
            if (now - lastMoveAt >= STOP_MS) {
                logTrip();
                tripStart = now; tripKm = 0; prevLat = 999; restFor = 0;
                Smart.tripStarts(this, l.getLatitude(), l.getLongitude(), now);
                suggest = Smart.on(this, "predict", true) ? Smart.predict(this, l.getLatitude(), l.getLongitude(), now) : null;
                suggestUntil = suggest == null ? 0 : now + 45_000;
                if (Prefs.of(this).getBoolean("khatma_auto", true) && !khatma.on && !sound.radioOn) {
                    h.postDelayed(() -> {   // after the dhikr, and only if nothing else is playing
                        MediaController mc = NotiListener.session(this);
                        if (!khatma.on && !sound.radioOn && !library.on() && (mc == null || !NotiListener.playing(mc))) khatma.start();
                    }, 20_000);
                }
                duaUntil = now + 60_000;
                duaKind = "riding";   // the travel du'a has its own moment: travelTick
                final String kind = duaKind;
                if (Prefs.of(this).getBoolean("dua_sound", true))
                    h.postDelayed(() -> sound.playDua(kind, Prefs.of(this).getFloat("adhan_vol", 1f)), 1500);
            }
            lastMoveAt = now;
        }
        if (kmh >= 3 && prevLat < 90) tripKm += Day.km(prevLat, prevLon, l.getLatitude(), l.getLongitude());
        arrivedHome(l, now);
        travelTick(l, now);
        prevLat = l.getLatitude(); prevLon = l.getLongitude();
        // kept on the screen every 30 seconds, so a restart of the screen goes on with the same trip
        if (tripStart > 0 && now - savedMoveAt > 30_000) {
            savedMoveAt = now;
            Prefs.of(this).edit().putLong("last_move", lastMoveAt).putLong("trip_start", tripStart).putFloat("trip_km", (float) tripKm).apply();
        }
    }

    /** «دفتر الطريق»: the trip that ended, kept on the screen: {start, end, km}. The last 600. */
    private void logTrip() {
        if (tripStart == 0 || tripKm < 0.3) return;
        try {
            org.json.JSONArray a = new org.json.JSONArray(Prefs.of(this).getString("trips", "[]"));
            a.put(new org.json.JSONArray().put(tripStart).put(lastMoveAt).put(Math.round(tripKm * 10) / 10.0));
            while (a.length() > 600) a.remove(0);
            Prefs.put(this, "trips", a.toString());
        } catch (Exception ignored) { }
    }

    /** «جرّب» in the settings. */
    /**
     * Home again: a trip of 2 km or more that comes within 150 m of the saved home plays the du'a for entering the
     * home and shows it for a minute, once a trip (kept on the screen, so a restart at home does not play it again).
     */
    /**
     * Home again: near the saved home (150 m: the GPS can be 70 to 80 m off) the du'a for entering the home waits for
     * the car to stop: its speed under 3 km/h for 3 seconds (or no fix for 5, as when the GPS goes quiet in a car
     * standing still), while still within 250 m. Driving on past the home cancels the wait. Once a trip of 2 km or more.
     */
    private boolean homeNear;
    private long homeStillSince;
    private void arrivedHome(Location l, long now) {
        float hl = Prefs.of(this).getFloat("home_lat", 999), ho = Prefs.of(this).getFloat("home_lon", 999);
        if (hl > 90 || tripStart == 0 || tripKm < 2 || duas.get("home") == null) { homeNear = false; return; }
        if (Prefs.of(this).getLong("home_done", 0) == tripStart) return;
        double k = Day.km(hl, ho, l.getLatitude(), l.getLongitude());
        if (!homeNear) { if (k <= 0.15) { homeNear = true; homeStillSince = 0; } return; }
        if (k > 0.25) homeNear = false;   // drove on past the home
    }
    /** Each second: near home, has the car stopped? */
    private void homeTick(long now) {
        if (!homeNear) return;
        boolean still = now - fixAt > 5000 || kmh < 3;
        if (!still) { homeStillSince = 0; return; }
        if (homeStillSince == 0) { homeStillSince = now; return; }
        if (now - homeStillSince < 3000) return;
        homeNear = false;
        if (Prefs.of(this).getLong("home_done", 0) == tripStart) return;
        Prefs.of(this).edit().putLong("home_done", tripStart).apply();
        duaKind = "home";
        duaUntil = now + 60_000;
        if (Prefs.of(this).getBoolean("dua_sound", true)) sound.playDua("home", Prefs.of(this).getFloat("adhan_vol", 1f));
    }
    void testDua(String which) { sound.playDua(which, Prefs.of(this).getFloat("adhan_vol", 1f)); }

    /**
     * The travel du'a, once a journey: when the car, moving, gets 40 km from the saved home. It is not said again (the
     * owner heard it at every start while away) until the car has come back within 40 km of the home and gone out again:
     * a new journey. Kept on the screen, so turning the car off and on while away changes nothing. A screen that is
     * already away when this version arrives is taken as travelling, without the du'a.
     */
    private void travelTick(Location l, long now) {
        float hl = Prefs.of(this).getFloat("home_lat", 999), ho = Prefs.of(this).getFloat("home_lon", 999);
        if (hl > 90) return;
        boolean far = Day.km(hl, ho, l.getLatitude(), l.getLongitude()) >= 40;
        android.content.SharedPreferences pr = Prefs.of(this);
        if (!pr.contains("travelling")) { pr.edit().putBoolean("travelling", far).apply(); return; }
        if (far == pr.getBoolean("travelling", false)) return;
        pr.edit().putBoolean("travelling", far).apply();
        if (!far || kmh < 10 || duas.get("travel") == null) return;
        duaKind = "travel";
        duaUntil = now + 60_000;
        if (pr.getBoolean("dua_sound", true)) sound.playDua("travel", pr.getFloat("adhan_vol", 1f));
    }

    void startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (!lm.getAllProviders().contains(p)) continue;
                lm.requestLocationUpdates(p, p.equals(LocationManager.GPS_PROVIDER) ? 1000 : 60_000, 0, gps, Looper.getMainLooper());
                Location last = lm.getLastKnownLocation(p);
                if (last != null) gps.onLocationChanged(last);
            }
        } catch (SecurityException ignored) { }
    }

    // ---------------------------------------------------------------- the buttons
    /** The app last opened from the home screen (the strip shows over the navigation app only). */
    static volatile String front = "";
    private void launch(Intent i) {
        front = i.getPackage() != null ? i.getPackage() : i.getComponent() != null ? i.getComponent().getPackageName() : "";
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { Toast.makeText(this, "لم يُفتح", Toast.LENGTH_SHORT).show(); }
    }
    private void launchPkg(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) Toast.makeText(this, "التطبيق غير مثبّت", Toast.LENGTH_SHORT).show(); else launch(i);
    }
    @Override public void youtube() { launchPkg("com.google.android.youtube"); }
    /** The screen's own «بلوتوث» app (com.tw.bt: calls, the phone's music); Android's Bluetooth settings if it is missing. */
    @Override public void bluetooth() {
        String bt = Device.btApp(this);   // this screen's own, or the one chosen in the settings
        Intent i = bt.isEmpty() ? null : getPackageManager().getLaunchIntentForPackage(bt);
        launch(i != null ? i : new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
    }
    @Override public void settings() { launch(new Intent(this, SettingsActivity.class)); }
    @Override public void apps() { launch(new Intent(this, AppsActivity.class)); }
    /** «البيت» / «العمل»: the place saved in the settings, in the chosen navigation app. */
    void goPlace(String key, String label) {
        float la = Prefs.of(this).getFloat(key + "_lat", 999), lo = Prefs.of(this).getFloat(key + "_lon", 999);
        if (la > 90) { Toast.makeText(this, "احفظ موقع " + label + " من الإعدادات أولًا", Toast.LENGTH_LONG).show(); return; }
        goTo(la, lo, label);
    }
    /** A point, in the chosen navigation app: the home, the work, a place of the owner's, the guessed destination. */
    void goTo(double la, double lo, String label) {
        String nav = Prefs.nav(this);
        front = nav;
        Intent i;
        if (nav.equals("com.waze")) i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://waze.com/ul?ll=" + la + "," + lo + "&navigate=yes"));
        else if (nav.equals("com.google.android.apps.maps")) i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("google.navigation:q=" + la + "," + lo));
        else i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:" + la + "," + lo + "?q=" + la + "," + lo));
        if (!nav.isEmpty()) i.setPackage(nav);
        if (!nav.isEmpty() && i.resolveActivity(getPackageManager()) == null) {   // the chosen app takes no place from another app
            launchPkg(nav);
            Toast.makeText(this, "اختر " + label + " في " + Prefs.navName(this, nav), Toast.LENGTH_LONG).show();
            return;
        }
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { launch(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:" + la + "," + lo + "?q=" + la + "," + lo))); }
    }
    /**
     * Around each adhan (the bar's time: 30 minutes before to 20 after) while moving, the nearest mosque ahead of the car
     * from the app's own list (Mosques), every 5 seconds: «٣ كم» beside the mosque's symbol, which then leads to it.
     * Kept 20 seconds when it falls out of view for a moment; otherwise the button searches around as before.
     */
    private Mosques mosques;
    private double[] ahead;
    private long aheadAt, aheadAskedAt;
    private String mosqueAhead(long now) {
        if (day.bar(now) == null || now - fixAt > 6000 || kmh < 10) { ahead = null; return null; }
        if (now - aheadAskedAt >= 5000) {
            aheadAskedAt = now;
            if (mosques == null) mosques = new Mosques(this);
            double[] a = mosques.ahead(day.lat, day.lon, bearing);
            if (a != null) { ahead = a; aheadAt = now; }
        }
        if (ahead == null || now - aheadAt > 20_000) { ahead = null; return null; }
        double k = Day.km(day.lat, day.lon, mosques.lat((int) ahead[0]), mosques.lon((int) ahead[0]));
        return Ui.ar(String.format(java.util.Locale.US, k < 10 ? "%.1f" : "%.0f", k)) + " كم";
    }

    /** «أقرب مسجد»: a search for mosques around the car in the navigation app (Google Maps if it is there). */
    void mosque() {
        boolean jumua = jumuaTime(System.currentTimeMillis());
        // the one ahead: straight to it; before the Friday prayer only when it is a «جامع» by its name
        if (ahead != null && mosques != null && (!jumua || mosques.name((int) ahead[0]).contains("جامع"))) {
            int i = (int) ahead[0];
            float la = (float) mosques.lat(i), lo = (float) mosques.lon(i);
            Prefs.of(this).edit().putFloat("ahead_lat", la).putFloat("ahead_lon", lo).apply();
            goPlace("ahead", "المسجد");
            return;
        }
        if (jumua) searchNearby("جامع", "أقرب جامع"); else searchNearby("مسجد", "أقرب مسجد");
    }
    /** Friday from 10 in the morning until an hour after the Dhuhr adhan: the owner is on the way to the Friday prayer, held in a «جامع». */
    private boolean jumuaTime(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        if (c.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY || c.get(Calendar.HOUR_OF_DAY) < 10) return false;
        day.ensure(now);
        return now < day.at(PrayerMath.DHUHR) + 3600_000L;
    }
    /**
     * A search around the car in the navigation app chosen in the settings (Hudhud for the owner), not Google Maps: Waze
     * by its link, any other by a «geo:» search sent to that app alone. An app that takes no such search is opened as it
     * is, with a word to search in it; Google Maps only when no navigation app is chosen.
     */
    void searchNearby(String what, String label) {
        String q = android.net.Uri.encode(what), nav = Prefs.nav(this);
        Intent i;
        if (nav.equals("com.waze")) i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://waze.com/ul?q=" + q));
        else i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:" + day.lat + "," + day.lon + "?q=" + q));
        if (nav.isEmpty()) { if (Prefs.installed(this, "com.google.android.apps.maps")) i.setPackage("com.google.android.apps.maps"); launch(i); return; }
        i.setPackage(nav);
        if (i.resolveActivity(getPackageManager()) != null) { launch(i); return; }
        launchPkg(nav);   // it takes no search from another app
        Toast.makeText(this, "ابحث عن «" + what + "» في " + Prefs.navName(this, nav), Toast.LENGTH_LONG).show();
    }
    /** For the full-screen gauge: {km/h or -1 without a recent fix, bearing or -1, qibla}. */
    float[] driveNow() {
        long now = System.currentTimeMillis();
        boolean fresh = now - fixAt < 6000;
        return new float[]{fresh ? kmh : (fixAt == 0 ? -1 : 0), fresh ? bearing : -1, day.qibla(), limitNow()};
    }
    /** The speed limit to show: the last one found, for 8 seconds and 150 m (a moment off the road's line keeps it); else 0. */
    int limitNow() {
        long now = System.currentTimeMillis();
        if (!Prefs.of(this).getBoolean("limit_on", true) || limit <= 0 || now - limitAt > 8_000 || limitLat > 90) return 0;
        return Day.km(limitLat, limitLon, day.lat, day.lon) < 0.15 ? limit : 0;
    }
    /** Once a second while moving: the road's limit from the app's own map (LimitMap), no server asked. */
    private LimitMap limits;
    private void askLimit(long now) {
        if (!Prefs.of(this).getBoolean("limit_on", true) || now - fixAt > 6000 || kmh < 15) return;
        if (limits == null) limits = new LimitMap(this);
        int v = limits.at(day.lat, day.lon, bearing);
        if (v > 0) { limit = v; limitAt = now; limitLat = day.lat; limitLon = day.lon; }
    }
    static String join(String a, String b) { return a.isEmpty() ? b : b.isEmpty() ? a : a + "  ·  " + b; }

    /** «🏠 ٣٤ كم» (the house sign, not the word) in a straight line, when the home is saved and the car more than 300 m from it. */
    String homeNow() {
        float hl = Prefs.of(this).getFloat("home_lat", 999), ho = Prefs.of(this).getFloat("home_lon", 999);
        if (hl > 90 || day.lat == 0) return "";
        double k = Day.km(day.lat, day.lon, hl, ho);
        if (k < 0.3) return "";
        return "🏠 " + Ui.ar(String.format(java.util.Locale.US, k < 10 ? "%.1f" : "%.0f", k)) + " كم";
    }
    /** The bearing to the home from north, or -1 (not saved, or at home). */
    float homeBearing() {
        float hl = Prefs.of(this).getFloat("home_lat", 999), ho = Prefs.of(this).getFloat("home_lon", 999);
        if (hl > 90 || day.lat == 0 || Day.km(day.lat, day.lon, hl, ho) < 0.3) return -1;
        return Day.bearing(day.lat, day.lon, hl, ho);
    }

    /** The sun low (under 15°) and within 25° of the way the car goes: a line for 20 seconds, then not for 20 minutes. */
    private long glareUntil, glareAt;
    private void glare(long now) {
        if (now - fixAt > 6000 || kmh < 20 || bearing < 0 || now - glareAt < 20 * 60_000L) return;
        double[] sn = Day.sun(day.lat, day.lon, now);
        double diff = Math.abs(((sn[1] - bearing) % 360 + 540) % 360 - 180);
        if (sn[0] > 0 && sn[0] < 15 && diff < 25) { glareAt = now; glareUntil = now + 20_000; }
    }

    /**
     * «مرحبًا بك في الخبر»: the city from the app's own list (Cities: GeoNames, offline, no server shared by all the
     * app's users) every 30 seconds while moving; a new name found twice in a row is the new city, and is welcomed
     * unless it was welcomed in the last half hour (the edges of Dammam, Khobar and Dhahran touch).
     */
    private String cityPending = "";
    private long cityAskedAt;
    private Cities cities;
    private void askCity(long now) {
        if (now - fixAt > 6000 || kmh < 10 || now - cityAskedAt < 30_000) return;
        cityAskedAt = now;
        if (cities == null) cities = new Cities(this);
        heardCity(cities.at(day.lat, day.lon));
    }
    private void heardCity(String c) {
        String cur = Prefs.of(this).getString("city", "");
        if (c.isEmpty() || c.equals(cur)) { cityPending = ""; return; }
        if (!c.equals(cityPending)) { cityPending = c; return; }
        cityPending = "";
        Prefs.put(this, "city", c);
        long last = Prefs.of(this).getLong("welcomed_" + c, 0), now = System.currentTimeMillis();
        if (cur.isEmpty() || now - last < 30 * 60_000L) return;   // the first city ever is not «new»
        Prefs.of(this).edit().putLong("welcomed_" + c, now).apply();
        if (HomeActivity.live != null) HomeActivity.live.welcome(c);
    }

    /** The trip line: «📄 ١٢٫٤ كم، ٣٥ دقيقة» (a report sheet, not the word), or empty before the first trip. */
    String tripNow() {
        if (tripStart == 0) return "";
        return "📄 " + Ui.ar(String.format(java.util.Locale.US, tripKm < 10 ? "%.1f" : "%.0f", tripKm))
                + " كم، " + Ui.remaining((System.currentTimeMillis() - tripStart) / 1000);
    }
    /** For the full-screen quote: the hour's quote, or one some hours later («التالي»). */
    String[] quoteNow(int hoursAhead) { return quotes.at(System.currentTimeMillis() + hoursAhead * 3600_000L); }
    boolean dayNow() { return panel != null ? panel.isDay() : themeDay(System.currentTimeMillis()); }

    /** The speed card opens the chosen navigation app. */
    void openNav() {
        String nav = Prefs.nav(this);
        if (nav.isEmpty()) Toast.makeText(this, "لا يوجد تطبيق ملاحة؛ اختره من الإعدادات", Toast.LENGTH_LONG).show(); else launchPkg(nav);
    }
    @Override public void openMusic() {
        if (!NotiListener.allowed(this)) { launch(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); return; }
        MediaController mc = NotiListener.session(this);
        if (mc != null && mc.getSessionActivity() != null) { try { mc.getSessionActivity().send(); return; } catch (Exception ignored) { } }
        if (mc != null) { launchPkg(mc.getPackageName()); return; }
        String last = Prefs.of(this).getString("last_audio", "com.google.android.youtube");
        launchPkg(last);
    }
    /** The headphones: the audio library. */
    @Override public void library() { launch(new Intent(this, LibraryActivity.class)); }
    Listen libraryPlayer() { return library; }
    /** A surah from the library, now: the khatma (which keeps its place) and the radio stop. */
    void listen(String id, String name, String server, int s, String surah) {
        quietForLibrary();
        library.play(id, name, server, s, surah);
    }
    /** The library's «تابع»: the last surah from the second it stopped. */
    void listenOn() { quietForLibrary(); library.resumeLast(); }
    private void quietForLibrary() {
        if (khatma.on) { khatma.stop(); khatmaPausedByStop = false; }
        sound.stopRadio();
    }

    @Override public void khatma() {
        if (khatma.on && khatma.held() != null) { khatmaGoOn(); return; }
        if (khatma.on) { khatma.stop(); khatmaPausedByStop = false; return; }
        sound.stopRadio();
        library.stop();
        khatmaMoved = false;
        khatma.start();
    }
    /** The nearest stations (the rest reminder). */
    /** The navigation arrow beside the destinations: the chosen navigation app, on its guidance if one runs. */
    void navApp() {
        if (NotiListener.navigating()) { front = NotiListener.navPkg; NotiListener.openNav(this); return; }
        String nav = Prefs.nav(this);
        if (nav.isEmpty()) { Toast.makeText(this, "اختر تطبيق الملاحة من الإعدادات", Toast.LENGTH_LONG).show(); launch(new Intent(this, SettingsActivity.class)); return; }
        launchPkg(nav);
    }
    void station() { searchNearby("محطة وقود", "أقرب محطة"); }

    // ---------------------------------------------------------------- the five extras (Smart), each with its switch
    private Object[] suggest;   // the guessed destination {label, lat, lon}, for 45 seconds from the trip's start
    private long suggestUntil, nameAskedAt;
    private int nameIndex = -1;
    /** The gold line under the speed: «إلى العمل؟» when a trip starts, or, standing at a place of the owner's with no name, an offer to name it. */
    private void suggestLine(long now) {
        if (suggest != null && now < suggestUntil) {
            final Object[] g = suggest;
            drive.showSuggest("إلى " + g[0] + "؟ اضغط لبدء المسار", () -> { suggestUntil = 0; goTo((Double) g[1], (Double) g[2], (String) g[0]); });
            return;
        }
        boolean still = now - fixAt > 6000 || kmh < 3;
        if (now - nameAskedAt > 10_000) {
            nameAskedAt = now;
            nameIndex = still && Smart.on(this, "places", true) ? Smart.toName(this, day.lat, day.lon) : -1;
        }
        if (nameIndex >= 0 && still) {
            final int i = nameIndex;
            drive.showSuggest("تقف هنا كثيرًا: اضغط لتسميته وجهة", () -> { nameIndex = -1; if (HomeActivity.live != null) HomeActivity.live.namePlace(i); });
        } else drive.showSuggest(null, null);
    }

    /** The prayer whose adhan has been called and whose iqama has not: {prayer, minutes left}, or null. Not Friday's. */
    private int[] awaitingIqama(long now) {
        if (!Smart.on(this, "iqama", true)) return null;
        for (int k : Day.FIVE) {
            long t = day.at(k), iq = t + Smart.iqama(this, k) * 60_000L;
            if (now >= t && now < iq && !day.name(k, now).equals("الجمعة")) return new int[]{k, (int) Math.ceil((iq - now) / 60_000.0)};
        }
        return null;
    }
    /** The adhan bar while the iqama is awaited: «العصر: الإقامة بعد ٨ دقائق». */
    private String iqamaBar(long now) {
        int[] a = awaitingIqama(now);
        return a == null || now - day.at(a[0]) < 60_000 ? null : day.name(a[0], now) + ": الإقامة بعد " + Ui.minutes(a[1]);
    }
    /**
     * «هل ألحق الصلاة؟»: while moving with a mosque ahead (the one beside the mosque's symbol), the minutes to it against
     * the minutes to the iqama. The road is taken as a third longer than the straight line, at the car's speed (30 km/h at
     * least); two minutes are kept for parking and walking in.
     */
    private String catchPrayer(long now) {
        int[] a = awaitingIqama(now);
        if (a == null || ahead == null || mosques == null || now - fixAt > 6000 || kmh < 10) return null;
        double k = Day.km(day.lat, day.lon, mosques.lat((int) ahead[0]), mosques.lon((int) ahead[0]));
        int eta = (int) Math.ceil(k * 1.3 / Math.max(kmh, 30) * 60);
        return "الإقامة بعد " + Ui.minutes(a[1]) + "، والمسجد على " + Ui.minutes(Math.max(1, eta)) + (eta + 2 <= a[1] ? ": تلحق" : ": قد لا تلحق");
    }

    /** Two fingers on the home screen: what plays stops, and what was stopped this way plays again. */
    private String pausedByTap = "";
    void soundToggle() {
        MediaController mc = NotiListener.session(this);
        String say;
        if (khatma.on) { khatma.stop(); khatmaPausedByStop = false; pausedByTap = "khatma"; say = "أُوقفت الختمة"; }
        else if (sound.radioOn) { sound.stopRadio(); pausedByTap = "radio"; say = "أُوقفت الإذاعة"; }
        else if (library.on()) { library.stop(); pausedByTap = "library"; say = "أُوقفت التلاوة"; }
        else if (mc != null && NotiListener.playing(mc)) { mc.getTransportControls().pause(); pausedByTap = "mc"; say = "أُوقف الصوت"; }
        else if (pausedByTap.equals("khatma")) { khatma(); say = "الختمة"; }
        else if (pausedByTap.equals("radio")) { radio(); say = "الإذاعة"; }
        else if (pausedByTap.equals("library")) { listenOn(); say = "التلاوة"; }
        else if (mc != null) { mc.getTransportControls().play(); say = "تشغيل"; }
        else say = "لا صوت لتشغيله";
        Toast.makeText(this, say, Toast.LENGTH_SHORT).show();
    }
    void soundSkip(boolean next) {
        MediaController mc = NotiListener.session(this);
        if (mc == null) { Toast.makeText(this, "لا مقطع للتنقل", Toast.LENGTH_SHORT).show(); return; }
        if (next) mc.getTransportControls().skipToNext(); else mc.getTransportControls().skipToPrevious();
        Toast.makeText(this, next ? "التالي" : "السابق", Toast.LENGTH_SHORT).show();
    }
    private boolean soundPlaying() {
        MediaController mc = NotiListener.session(this);
        return khatma.on || sound.radioOn || library.on() || (mc != null && NotiListener.playing(mc));
    }

    /** The strip over the navigation app: while that app is the one opened from here and the home screen is not in front. */
    private Strip strip;
    private void strip(long now) {
        boolean want = Smart.on(this, "strip", false) && android.provider.Settings.canDrawOverlays(this) && !HomeActivity.resumed && !Updater.installing()
                && !front.isEmpty() && (front.equals(Prefs.nav(this)) || front.equals(NotiListener.navPkg));
        if (!want) { if (strip != null) strip.show(false); return; }
        if (strip == null) strip = new Strip(this, () -> { front = ""; launch(new Intent(this, HomeActivity.class)); front = ""; }, this::soundToggle);
        day.ensure(now);
        long[] n = day.next(now);
        int[] a = awaitingIqama(now);
        long m = a != null ? a[1] : Math.max(1, (n[1] - now + 59_999) / 60_000L);
        String left = m >= 60 ? Ui.ar(m / 60) + " س" + (m % 60 == 0 ? "" : " " + Ui.ar(m % 60) + " د") : Ui.ar(m) + " د";
        strip.update(themeDay(now), a != null ? "إقامة " + day.name(a[0], now) : day.name((int) n[0], now), left, soundPlaying());
        strip.show(true);
    }
    /** The settings: the strip's switch or its place changed. */
    void stripReset() { if (strip != null) { strip.show(false); strip = null; } }
    /** «أماكني»: the owner's places, to choose one and go. */
    void places() { if (HomeActivity.live != null) HomeActivity.live.pickPlace(); }
    Khatma khatmaPlayer() { return khatma; }

    /** FM near: the screen's radio opens and the frequency is shown (probe: com.tw.radio takes no command). Else the stream. */
    @Override public void radio() {
        if (sound.radioOn) { sound.stopRadio(); return; }
        Object[] fm = Prefs.radioSource(this).equals("fm") ? Net.fmHere(this, day.lat, day.lon) : null;
        String ra = Device.radioApp(this);   // this screen's own radio app, if it has one
        if (fm != null && !ra.isEmpty()) {
            Intent i = ra.equals("com.tw.radio") ? new Intent().setComponent(new ComponentName("com.tw.radio", "com.tw.radio.RadioActivity"))
                    : getPackageManager().getLaunchIntentForPackage(ra);
            launch(i);
            Toast.makeText(this, "اضبط الراديو على " + Ui.ar(String.format(java.util.Locale.US, "%.1f", ((Number) fm[1]).doubleValue())) + " FM، إذاعة القرآن في " + fm[0]
                    + (Boolean.TRUE.equals(fm[2]) ? "" : " (تردد غير مؤكد)"), Toast.LENGTH_LONG).show();
            return;
        }
        if (!Net.online(this)) { Toast.makeText(this, "لا إنترنت، ولا تردد FM معروف هنا", Toast.LENGTH_LONG).show(); return; }
        khatma.stop();
        library.stop();
        sound.startRadio();
    }

    static void start(Context c) { c.startForegroundService(new Intent(c, PanelService.class)); }
}
