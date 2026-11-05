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
    private WindowManager wm;
    private Panel panel;
    private boolean shown;
    private final Day day = new Day();
    private Sound sound;
    private double[] wx;   // {°C, code}
    private long wxAt, fmAt, updAt;
    private double wxLat, wxLon;
    private String lastFg = "", playedFor = "";
    private LocationManager lm;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        live = this;
        startForeground(1, notification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        sound = new Sound(this);
        sound.changed = () -> h.post(this::drawRadio);
        day.lat = Prefs.of(this).getFloat("lat", (float) day.lat);
        day.lon = Prefs.of(this).getFloat("lon", (float) day.lon);
        startLocation();
        new Thread(() -> { sound.cacheAdhan(); Net.refreshFm(this); }).start();
        h.post(tick);
    }

    @Override public int onStartCommand(Intent i, int f, int id) { h.post(this::applyPrefs); return START_STICKY; }

    @Override public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        hide();
        sound.release();
        if (lm != null) try { lm.removeUpdates(gps); } catch (Exception ignored) { }
        if (live == this) live = null;
        super.onDestroy();
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("panel", "لوحة سيارتك", NotificationManager.IMPORTANCE_MIN));
        return new Notification.Builder(this, "panel").setContentTitle("سيارتك تعمل").setSmallIcon(android.R.drawable.ic_menu_compass).build();
    }

    // ---------------------------------------------------------------- the floating panel
    private void show() {
        if (shown || !Settings.canDrawOverlays(this)) return;
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        int w = Math.max(m.widthPixels, m.heightPixels) * 2 / 5;
        if (panel == null) panel = new Panel(this, this, Math.min(m.widthPixels, m.heightPixels));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.OPAQUE);
        lp.gravity = (Prefs.side(this).equals("right") ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
        try { wm.addView(panel, lp); shown = true; draw(System.currentTimeMillis()); } catch (Exception ignored) { }
    }
    private void hide() {
        if (!shown) return;
        try { wm.removeView(panel); } catch (Exception ignored) { }
        shown = false;
    }
    /** After «الإعدادات»: the side may have changed, so the panel is placed again. */
    void applyPrefs() { hide(); panel = null; lastFg = ""; }

    /** Is the navigation app (or our home screen) in front? From the last screens that came to the front. */
    private String foreground() {
        AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        if (ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName()) != AppOpsManager.MODE_ALLOWED)
            return HomeActivity.resumed ? getPackageName() : "";   // without the permission we cannot tell: only on our home screen
        long now = System.currentTimeMillis();
        UsageEvents ev = ((UsageStatsManager) getSystemService(USAGE_STATS_SERVICE)).queryEvents(now - (lastFg.isEmpty() ? 3600_000 : 10_000), now);   // the first time: the last hour
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) lastFg = e.getPackageName() + "/" + e.getClassName();
        }
        return lastFg;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            String fg = foreground();
            String nav = Prefs.nav(PanelService.this);
            // over the navigation app, or our home screen while it shows the setup; hidden over anything else
            boolean want = Settings.canDrawOverlays(PanelService.this) && ((!nav.isEmpty() && fg.startsWith(nav + "/")) || fg.equals(nav) || HomeActivity.resumed);
            if (want) show(); else hide();
            adhan(now);
            if (shown) draw(now);
            background(now);
            h.postDelayed(this, 1000);
        }
    };

    // ---------------------------------------------------------------- what the panel shows
    private void draw(long now) {
        day.ensure(now);
        boolean d = themeDay(now);
        if (d != panel.isDay()) panel.setDay(d);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int hh = c.get(Calendar.HOUR_OF_DAY), h12 = hh % 12 == 0 ? 12 : hh % 12;
        panel.showTime(Ui.ar(h12 + ":" + Ui.pad(c.get(Calendar.MINUTE))), hh < 12 ? "ص" : "م", Day.hijriText(now), Day.gregText(now));
        panel.showPlace(day.placeName());
        if (wx != null) {
            int code = (int) wx[1];
            int icon = code <= 1 ? (day.night(now) ? Ui.Icon.MOON : Ui.Icon.SUN) : code == 2 ? (day.night(now) ? Ui.Icon.MOON : Ui.Icon.SUN) : Ui.Icon.CLOUD;
            panel.showWeather(Ui.ar(Math.round(wx[0])) + "°", icon, now - wxAt > 3 * 3600_000L);
        } else panel.showWeather("", Ui.Icon.SUN, false);
        panel.showBar(day.bar(now));
        int cur = day.current(now);
        if (cur >= 0) {
            int since = (int) ((now - day.at(cur)) / 60_000L);
            panel.showPrayer(since < 1 ? "حان الآن" : "الصلاة الحالية", day.name(cur, now), Day.clockOf(day.at(cur)),
                    since < 1 ? "وقت الأذان" : "مضى على الأذان " + Ui.minutes(since), day.ramadan(now), true);
        } else {
            long[] n = day.next(now);
            panel.showPrayer("الصلاة القادمة", day.name((int) n[0], now), Day.clockOf(n[1]),
                    "باقٍ " + Ui.remaining(Math.max(60, Math.round((n[1] - now) / 1000.0))), day.ramadan(now), false);
            // half an hour before an adhan the card turns back to the prayer, once
            String key = n[0] + "@" + n[1];
            if (n[1] - now <= Day.BEFORE_MIN * 60_000L && !key.equals(turnedFor)) { turnedFor = key; panel.showPage("p"); }
        }
        MediaController mc = NotiListener.session(this);
        panel.showMusic(mc, mc == null ? "" : NotiListener.appName(this, mc.getPackageName()),
                mc == null ? "" : NotiListener.title(mc), mc == null ? "" : NotiListener.artist(mc), mc != null && NotiListener.playing(mc));
        drawRadio();
    }
    private String turnedFor = "";

    private boolean themeDay(long now) {
        String t = Prefs.theme(this);
        return t.equals("light") || (t.equals("auto") && !day.night(now));
    }

    private void drawRadio() {
        if (panel == null) return;
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
        if (now - day.at(cur) < 60_000L && !key.equals(playedFor)) { playedFor = key; sound.playAdhan(); }
    }

    /** The temperature every 30 minutes or after 10 km, the FM table daily, the update check every 6 hours. */
    private void background(long now) {
        if (!Net.online(this)) return;
        boolean moved = Day.km(wxLat, wxLon, day.lat, day.lon) > 10;
        if (now - wxAt > 30 * 60_000L || moved) {
            wxAt = now; wxLat = day.lat; wxLon = day.lon;
            final double la = day.lat, lo = day.lon;
            new Thread(() -> { double[] r = Net.weather(la, lo); if (r != null) h.post(() -> { wx = r; wxAt = System.currentTimeMillis(); }); }).start();
        }
        if (now - fmAt > 24 * 3600_000L) { fmAt = now; new Thread(() -> Net.refreshFm(this)).start(); }
        if (now - updAt > 6 * 3600_000L) { updAt = now; new Thread(() -> Updater.check(this, false)).start(); }
    }

    // ---------------------------------------------------------------- GPS
    private final LocationListener gps = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            day.lat = l.getLatitude(); day.lon = l.getLongitude();
            Prefs.of(PanelService.this).edit().putFloat("lat", (float) day.lat).putFloat("lon", (float) day.lon).apply();
        }
        @Override public void onStatusChanged(String p, int s, Bundle b) { }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { }
    };
    void startLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (!lm.getAllProviders().contains(p)) continue;
                lm.requestLocationUpdates(p, 60_000, 500, gps, Looper.getMainLooper());
                Location last = lm.getLastKnownLocation(p);
                if (last != null) gps.onLocationChanged(last);
            }
        } catch (SecurityException ignored) { }
    }

    // ---------------------------------------------------------------- the buttons
    private void launch(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { Toast.makeText(this, "لم يُفتح", Toast.LENGTH_SHORT).show(); }
    }
    private void launchPkg(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) Toast.makeText(this, "التطبيق غير مثبّت", Toast.LENGTH_SHORT).show(); else launch(i);
    }
    @Override public void youtube() { launchPkg("com.google.android.youtube"); }
    @Override public void bluetooth() { launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
    @Override public void settings() { launch(new Intent(this, SettingsActivity.class)); }
    @Override public void apps() { launch(new Intent(this, AppsActivity.class)); }
    @Override public void openMusic() {
        MediaController mc = NotiListener.session(this);
        if (mc != null && mc.getSessionActivity() != null) { try { mc.getSessionActivity().send(); return; } catch (Exception ignored) { } }
        if (mc != null) { launchPkg(mc.getPackageName()); return; }
        String last = Prefs.of(this).getString("last_audio", "com.google.android.youtube");
        launchPkg(last);
    }
    /** FM near: the screen's radio opens and the frequency is shown (probe: com.tw.radio takes no command). Else the stream. */
    @Override public void radio() {
        if (sound.radioOn) { sound.stopRadio(); return; }
        Object[] fm = Prefs.radioSource(this).equals("fm") ? Net.fmHere(this, day.lat, day.lon) : null;
        if (fm != null) {
            Intent i = new Intent().setComponent(new ComponentName("com.tw.radio", "com.tw.radio.RadioActivity"));
            launch(i);
            Toast.makeText(this, "اضبط الراديو على " + Ui.ar(String.format(java.util.Locale.US, "%.1f", ((Number) fm[1]).doubleValue())) + " FM، إذاعة القرآن في " + fm[0]
                    + (Boolean.TRUE.equals(fm[2]) ? "" : " (تردد غير مؤكد)"), Toast.LENGTH_LONG).show();
            return;
        }
        if (!Net.online(this)) { Toast.makeText(this, "لا إنترنت، ولا تردد FM معروف هنا", Toast.LENGTH_LONG).show(); return; }
        sound.startRadio();
    }

    static void start(Context c) { c.startForegroundService(new Intent(c, PanelService.class)); }
}
