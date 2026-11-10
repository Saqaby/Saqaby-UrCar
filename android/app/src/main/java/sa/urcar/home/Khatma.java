package sa.urcar.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

/**
 * «ختمة السيارة»: a recitation that goes on from trip to trip. It plays surah after surah from where it stopped (the
 * surah and the second are kept on the screen) and counts the completed khatmas. assets/khatma.json (src/khatma_for_app.py, from mp3quran.net's API):
 * mp3quran.net reciters (server + 001.mp3 … 114.mp3) and the surahs with their ayah counts (sum 6236).
 * The adhan, a call or another sound pauses it and it goes on by itself after, 3 seconds back so nothing is missed.
 */
final class Khatma {
    private final Context c;
    private final AudioManager am;
    private final Handler h = new Handler(Looper.getMainLooper());
    private JSONArray reciters = new JSONArray(), surahs = new JSONArray();
    private int total = 6236;
    private MediaPlayer mp;
    private AudioFocusRequest focus;
    /** Why the recitation waits, paused, to go on by itself: «focus» (the adhan) or «call»; null when it is not held. */
    private String held;
    /** Rewound on a pause, so the words cut by the interruption are heard again. */
    private static final int BACK_MS = 3000;
    boolean on;
    Runnable changed = () -> { };
    private void notifyChanged() { session(); changed.run(); }

    /**
     * The steering wheel's buttons and the screen's own media widget: a media session for the khatma. Play goes on
     * (from the second it stopped), pause stops it as its button does, previous goes back 10 seconds; no skipping
     * between surahs (the owner's choice: the khatma goes from Al-Fatiha to An-Nas; single surahs are in the library). It stays active while the app runs, so «play» on the wheel can start it again.
     */
    private android.media.session.MediaSession ms;
    private void session() {
        try {
            if (ms == null) {
                ms = new android.media.session.MediaSession(c, "UrCarKhatma");
                ms.setCallback(new android.media.session.MediaSession.Callback() {
                    @Override public void onPlay() { if (on && held != null) resume(); else if (!on) start(); }
                    @Override public void onPause() { if (on) stop(); }
                    @Override public void onStop() { if (on) stop(); }
                    @Override public void onSkipToPrevious() { back(10_000); }
                });
            }
            ms.setMetadata(new android.media.MediaMetadata.Builder()
                    .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "سورة " + surahName())
                    .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, reciterName())
                    .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, "ختمة السيارة").build());
            long pos = 0;
            try { pos = mp != null ? mp.getCurrentPosition() : p().getInt("k_pos", 0); } catch (Exception ignored) { }
            int st = on && held == null ? android.media.session.PlaybackState.STATE_PLAYING : android.media.session.PlaybackState.STATE_PAUSED;
            ms.setPlaybackState(new android.media.session.PlaybackState.Builder()
                    .setActions(android.media.session.PlaybackState.ACTION_PLAY | android.media.session.PlaybackState.ACTION_PAUSE
                            | android.media.session.PlaybackState.ACTION_PLAY_PAUSE | android.media.session.PlaybackState.ACTION_STOP
                            | android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(st, pos, st == android.media.session.PlaybackState.STATE_PLAYING ? 1f : 0f).build());
            ms.setActive(true);
        } catch (Exception ignored) { }
    }
    /** Back some seconds in the surah (the wheel's «previous»). */
    void back(int millis) {
        try { if (mp != null && on && held == null) mp.seekTo(Math.max(0, mp.getCurrentPosition() - millis)); } catch (Exception ignored) { }
    }

    Khatma(Context c) {
        this.c = c.getApplicationContext();
        am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try (InputStream in = c.getAssets().open("khatma.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            JSONObject j = new JSONObject(b.toString("UTF-8"));
            reciters = new JSONArray();
            JSONArray rest = j.getJSONArray("reciters");
            for (int i = 0; i < rest.length(); i++) reciters.put(rest.getJSONObject(i));
            // in the order of the Arabic alphabet (the owner's wish), as the library and the settings list them
            java.util.List<JSONObject> all = new java.util.ArrayList<>();
            for (int i = 0; i < reciters.length(); i++) all.add(reciters.getJSONObject(i));
            final java.text.Collator ar = java.text.Collator.getInstance(new java.util.Locale("ar"));
            java.util.Collections.sort(all, (x, y) -> ar.compare(x.optString("name"), y.optString("name")));
            reciters = new JSONArray(all);
            surahs = j.getJSONArray("surahs");
            total = 0;
            for (int i = 0; i < surahs.length(); i++) total += surahs.getJSONObject(i).getInt("ayahs");
        } catch (Exception ignored) { }
    }

    private SharedPreferences p() { return Prefs.of(c); }
    int surah() { return Math.max(1, Math.min(114, p().getInt("k_surah", 1))); }
    int done() { return p().getInt("k_done", 0); }
    JSONArray reciters() { return reciters; }
    /** Has this reciter this surah? (a reciter with «surahs» has only those: the library only, not the khatma) */
    static boolean has(JSONObject r, int s) {
        JSONArray l = r.optJSONArray("surahs");
        if (l == null) return true;
        for (int i = 0; i < l.length(); i++) if (l.optInt(i) == s) return true;
        return false;
    }
    static int total(JSONObject r) { JSONArray l = r.optJSONArray("surahs"); return l == null ? 114 : l.length(); }
    /** The reciters with all 114 surahs: those the khatma can be read by. */
    JSONArray complete() {
        JSONArray a = new JSONArray();
        for (int i = 0; i < reciters.length(); i++) if (!reciters.optJSONObject(i).has("surahs")) a.put(reciters.optJSONObject(i));
        return a;
    }
    String reciterId() { return p().getString("k_reciter", "asim-luhaidan"); }   // the first choice stays Asim Al-Luhaidan
    String reciterName() {
        for (int i = 0; i < reciters.length(); i++) if (reciters.optJSONObject(i).optString("id").equals(reciterId())) return reciters.optJSONObject(i).optString("name");
        return "";
    }
    private String server() {
        for (int i = 0; i < reciters.length(); i++) if (reciters.optJSONObject(i).optString("id").equals(reciterId())) return reciters.optJSONObject(i).optString("server");
        return reciters.length() > 0 ? reciters.optJSONObject(0).optString("server") : "";
    }
    JSONArray surahs() { return surahs; }
    String surahName() { JSONObject s = surahs.optJSONObject(surah() - 1); return s == null ? "" : s.optString("name"); }

    /** The share of the Quran heard, by ayahs: the surahs before this one, and this one by the second reached. */
    int percent() {
        int before = 0;
        for (int i = 0; i < surah() - 1 && i < surahs.length(); i++) before += surahs.optJSONObject(i).optInt("ayahs");
        JSONObject s = surahs.optJSONObject(surah() - 1);
        int ayahs = s == null ? 0 : s.optInt("ayahs");
        float frac = 0;
        int dur = p().getInt("k_dur", 0);
        if (dur > 0) frac = Math.min(1f, p().getInt("k_pos", 0) / (float) dur);
        return Math.round(100f * (before + ayahs * frac) / Math.max(1, total));
    }
    /** «ختمتك ٣٧٪، سورة يوسف» */
    String label() { return "الختمة " + Ui.ar(percent()) + "٪، " + surahName(); }

    void setReciter(String id) {
        boolean was = on;
        stop();
        p().edit().putString("k_reciter", id).putInt("k_pos", 0).putInt("k_dur", 0).apply();   // a new voice: the surah from its start
        if (was) start();
    }
    /** From the first surah again (the count of completed khatmas stays). */
    void restart() { boolean was = on; stop(); p().edit().putInt("k_surah", 1).putInt("k_pos", 0).putInt("k_dur", 0).apply(); if (was) start(); notifyChanged(); }

    void start() {
        stop();
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(aa).setOnAudioFocusChangeListener(fc -> {
            if (mp == null) return;
            if (fc == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || fc == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) hold("focus");   // the adhan, a call
            else if (fc == AudioManager.AUDIOFOCUS_GAIN) { if ("focus".equals(held)) resume(); }
            else if (fc == AudioManager.AUDIOFOCUS_LOSS) hold("other");   // another app took the sound: back when it is quiet (PanelService)
        }).build();
        am.requestAudioFocus(focus);
        this.aa = aa;
        on = true;
        notifyChanged();
        final int s = surah();
        File f = cacheFile(s), kept = LibStore.file(c, reciterId(), s);
        if (kept.length() > 0) play(kept.getPath(), false, s);   // the reciter's recitation downloaded in the library
        else if (f.length() > 0) play(f.getPath(), false, s);   // already on the screen
        else play(url(s), true, s);                        // streamed; if the screen's player refuses it, downloaded
        prefetch(s >= 114 ? 1 : s + 1);
    }
    private AudioAttributes aa;

    private String url(int s) { return server() + String.format(java.util.Locale.US, "%03d.mp3", s); }
    /** One reciter's surah kept on the screen: the current one and the next, so the next starts at once. */
    private File cacheFile(int s) { return new File(c.getCacheDir(), "k_" + reciterId() + "_" + s + ".mp3"); }

    private void play(String src, boolean stream, final int s) {
        if (!on || surah() != s) return;
        if (mp != null) { try { mp.release(); } catch (Exception ignored) { } mp = null; }
        final int pos = p().getInt("k_pos", 0);
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(aa);
            if (stream) {
                java.util.Map<String, String> hd = new java.util.HashMap<>();
                hd.put("User-Agent", "Mozilla/5.0 (Linux; Android 10) UrCar");
                mp.setDataSource(c, android.net.Uri.parse(src), hd);
            } else mp.setDataSource(src);
            mp.setOnPreparedListener(m -> {
                p().edit().putInt("k_dur", m.getDuration()).apply();
                if (pos > 0 && pos < m.getDuration() - 3000) m.seekTo(pos);
                if (held == null) m.start();   // a call that came while it loaded: it waits for resume()
            });
            mp.setOnCompletionListener(m -> next());
            mp.setOnErrorListener((m, w, e) -> {
                if (stream) { downloadThenPlay(s); return true; }   // the owner's screen refused the stream (1/0)
                fail("الخطأ " + w + "/" + e + " في " + surahName());
                return true;
            });
            mp.prepareAsync();
            h.removeCallbacks(saver);
            h.postDelayed(saver, 10_000);
        } catch (Exception e) {
            if (stream) downloadThenPlay(s); else fail(e.getClass().getSimpleName());
        }
    }

    /** The surah downloaded by the app itself (as the adhan), then played from the screen. */
    private void downloadThenPlay(final int s) {
        if (mp != null) { try { mp.release(); } catch (Exception ignored) { } mp = null; }
        android.widget.Toast.makeText(c, "تُنزَّل سورة " + surahName() + "…", android.widget.Toast.LENGTH_SHORT).show();
        final File f = cacheFile(s);
        new Thread(() -> {
            try {
                synchronized (f.getPath().intern()) { if (f.length() == 0) Net.download(url(s), f); }
                h.post(() -> play(f.getPath(), false, s));
            } catch (Exception e) {
                h.post(() -> fail("لم تُنزَّل سورة " + surahName() + ": " + e.getMessage()));
            }
        }).start();
    }

    /** The next surah, fetched while this one plays; older ones are deleted. */
    private void prefetch(final int n) {
        final File keep = cacheFile(n), cur = cacheFile(surah());
        File[] all = c.getCacheDir().listFiles();
        if (all != null) for (File x : all) if (x.getName().startsWith("k_") && !x.equals(keep) && !x.equals(cur)) x.delete();
        if (keep.length() > 0) return;
        new Thread(() -> { try { synchronized (keep.getPath().intern()) { if (keep.length() == 0) Net.download(url(n), keep); } } catch (Exception ignored) { } }).start();
    }

    private void fail(String why) {
        stop();
        p().edit().putString("k_err", why).apply();
        android.widget.Toast.makeText(c, "لم تعمل التلاوة: " + why + ". تأكد من الإنترنت ثم أعد المحاولة", android.widget.Toast.LENGTH_LONG).show();
    }

    /** The next surah; after An-Nas a khatma is complete and it begins again from Al-Fatiha. */
    private void next() {
        int s = surah();
        SharedPreferences.Editor e = p().edit().putInt("k_pos", 0).putInt("k_dur", 0);
        if (s >= 114) e.putInt("k_surah", 1).putInt("k_done", done() + 1); else e.putInt("k_surah", s + 1);
        e.apply();
        start();
    }

    private final Runnable saver = new Runnable() {
        @Override public void run() { if (mp != null) { if (held == null) save(); h.postDelayed(this, 10_000); } }
    };
    private void save() {
        try { if (mp != null && mp.getCurrentPosition() > 0) p().edit().putInt("k_pos", mp.getCurrentPosition()).apply(); } catch (Exception ignored) { }
    }

    String held() { return held; }
    boolean playing() { try { return mp != null && mp.isPlaying(); } catch (Exception e) { return false; } }

    /** A call, another sound or the adhan: paused at once, a little back, and it goes on by itself with resume(). */
    void hold(String why) {
        if (!on || held != null) return;
        held = why;
        try { if (mp != null && mp.isPlaying()) mp.pause(); } catch (Exception ignored) { }
        saveBack();
        notifyChanged();
    }
    void resume() {
        if (held == null) return;
        held = null;
        try {
            if (focus != null) am.requestAudioFocus(focus);   // lost to another app: asked back
            if (mp != null) { mp.seekTo(Math.max(0, p().getInt("k_pos", 0))); mp.start(); }
            else if (on) start();
        } catch (Exception e) { start(); }
        notifyChanged();
    }
    private void saveBack() {
        try { if (mp != null && mp.getCurrentPosition() > 0) p().edit().putInt("k_pos", Math.max(0, mp.getCurrentPosition() - BACK_MS)).apply(); } catch (Exception ignored) { }
    }

    void stop() {
        h.removeCallbacks(saver);
        if (mp != null) { if (held == null) save(); try { mp.release(); } catch (Exception ignored) { } mp = null; }
        if (focus != null) { am.abandonAudioFocusRequest(focus); focus = null; }
        held = null;
        if (on) { on = false; notifyChanged(); }
    }
}
