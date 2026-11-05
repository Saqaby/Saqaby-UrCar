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
import java.io.InputStream;

/**
 * «ختمة السيارة»: a recitation that goes on from trip to trip. It plays surah after surah from where it stopped (the
 * surah and the second are kept on the screen) and counts the completed khatmas. assets/khatma.json: UrTV's
 * mp3quran.net reciters (server + 001.mp3 … 114.mp3) and the surahs with their ayah counts (sum 6236).
 * The adhan takes the sound for a moment and the recitation pauses and comes back (audio focus).
 */
final class Khatma {
    private final Context c;
    private final AudioManager am;
    private final Handler h = new Handler(Looper.getMainLooper());
    private JSONArray reciters = new JSONArray(), surahs = new JSONArray();
    private int total = 6236;
    private MediaPlayer mp;
    private AudioFocusRequest focus;
    private boolean pausedByFocus;
    boolean on;
    Runnable changed = () -> { };

    Khatma(Context c) {
        this.c = c.getApplicationContext();
        am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try (InputStream in = c.getAssets().open("khatma.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            JSONObject j = new JSONObject(b.toString("UTF-8"));
            reciters = j.getJSONArray("reciters");
            surahs = j.getJSONArray("surahs");
            total = 0;
            for (int i = 0; i < surahs.length(); i++) total += surahs.getJSONObject(i).getInt("ayahs");
        } catch (Exception ignored) { }
    }

    private SharedPreferences p() { return Prefs.of(c); }
    int surah() { return Math.max(1, Math.min(114, p().getInt("k_surah", 1))); }
    int done() { return p().getInt("k_done", 0); }
    JSONArray reciters() { return reciters; }
    String reciterId() { return p().getString("k_reciter", reciters.length() > 0 ? reciters.optJSONObject(0).optString("id") : ""); }
    String reciterName() {
        for (int i = 0; i < reciters.length(); i++) if (reciters.optJSONObject(i).optString("id").equals(reciterId())) return reciters.optJSONObject(i).optString("name");
        return "";
    }
    private String server() {
        for (int i = 0; i < reciters.length(); i++) if (reciters.optJSONObject(i).optString("id").equals(reciterId())) return reciters.optJSONObject(i).optString("server");
        return reciters.length() > 0 ? reciters.optJSONObject(0).optString("server") : "";
    }
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
    void restart() { boolean was = on; stop(); p().edit().putInt("k_surah", 1).putInt("k_pos", 0).putInt("k_dur", 0).apply(); if (was) start(); changed.run(); }

    void start() {
        stop();
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(aa).setOnAudioFocusChangeListener(fc -> {
            if (mp == null) return;
            if (fc == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || fc == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {   // the adhan
                if (mp.isPlaying()) { mp.pause(); pausedByFocus = true; save(); }
            } else if (fc == AudioManager.AUDIOFOCUS_GAIN) {
                if (pausedByFocus) { pausedByFocus = false; mp.start(); }
            } else if (fc == AudioManager.AUDIOFOCUS_LOSS) stop();   // another app took the sound for good
        }).build();
        am.requestAudioFocus(focus);
        final int s = surah(), pos = p().getInt("k_pos", 0);
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(aa);
            mp.setDataSource(server() + String.format(java.util.Locale.US, "%03d.mp3", s));
            mp.setOnPreparedListener(m -> {
                p().edit().putInt("k_dur", m.getDuration()).apply();
                if (pos > 0 && pos < m.getDuration() - 3000) m.seekTo(pos);
                m.start();
            });
            mp.setOnCompletionListener(m -> next());
            mp.setOnErrorListener((m, w, e) -> { stop(); return true; });
            mp.prepareAsync();
            on = true;
            h.postDelayed(saver, 10_000);
        } catch (Exception e) { stop(); }
        changed.run();
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
        @Override public void run() { if (mp != null) { save(); h.postDelayed(this, 10_000); } }
    };
    private void save() {
        try { if (mp != null && mp.getCurrentPosition() > 0) p().edit().putInt("k_pos", mp.getCurrentPosition()).apply(); } catch (Exception ignored) { }
    }

    void stop() {
        h.removeCallbacks(saver);
        if (mp != null) { save(); try { mp.release(); } catch (Exception ignored) { } mp = null; }
        if (focus != null) { am.abandonAudioFocusRequest(focus); focus = null; }
        pausedByFocus = false;
        if (on) { on = false; changed.run(); }
    }
}
