package sa.urcar.home;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;

/**
 * «المكتبة الصوتية»: one surah by the reciter chosen, played now and to its end, apart from the khatma (which keeps its
 * place). Streamed with a browser's User-Agent, or downloaded first when the screen's player refuses the stream, as
 * the khatma does; only the last surah is kept on the screen. The adhan or a call pauses it, and it goes on after.
 */
final class Listen {
    private final Context c;
    private final AudioManager am;
    private final Handler h = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private AudioFocusRequest focus;
    private boolean paused, pausedByFocus;
    String reciter = "", surah = "";
    Runnable changed = () -> { };

    Listen(Context c) { this.c = c.getApplicationContext(); am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE); }

    boolean on() { return mp != null; }
    boolean playing() { try { return mp != null && mp.isPlaying(); } catch (Exception e) { return false; } }
    boolean paused() { return mp != null && paused; }

    void play(String reciterId, String reciterName, String server, int s, String surahName) {
        stop();
        reciter = reciterName; surah = surahName;
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(aa).setOnAudioFocusChangeListener(fc -> {
            if (mp == null) return;
            if (fc == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || fc == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) { if (mp.isPlaying()) { mp.pause(); pausedByFocus = true; } }
            else if (fc == AudioManager.AUDIOFOCUS_GAIN) { if (pausedByFocus && !paused) { pausedByFocus = false; mp.start(); } }
            else if (fc == AudioManager.AUDIOFOCUS_LOSS) { mp.pause(); paused = true; changed.run(); }
        }).build();
        am.requestAudioFocus(focus);
        String url = server + String.format(java.util.Locale.US, "%03d.mp3", s);
        File f = new File(c.getCacheDir(), "l_" + reciterId + "_" + s + ".mp3");
        if (f.length() > 0) start(aa, f.getPath(), false, url, f);
        else start(aa, url, true, url, f);
        changed.run();
    }

    private void start(AudioAttributes aa, String src, boolean stream, String url, File f) {
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(aa);
            if (stream) {
                java.util.Map<String, String> hd = new java.util.HashMap<>();
                hd.put("User-Agent", "Mozilla/5.0 (Linux; Android 10) UrCar");
                mp.setDataSource(c, android.net.Uri.parse(src), hd);
            } else mp.setDataSource(src);
            mp.setOnPreparedListener(m -> { if (!paused) m.start(); changed.run(); });
            mp.setOnCompletionListener(m -> stop());
            mp.setOnErrorListener((m, w, e) -> { if (stream) download(aa, url, f); else fail("الخطأ " + w + "/" + e); return true; });
            mp.prepareAsync();
        } catch (Exception e) { if (stream) download(aa, url, f); else fail(e.getClass().getSimpleName()); }
    }

    private void download(AudioAttributes aa, String url, File f) {
        if (mp != null) { try { mp.release(); } catch (Exception ignored) { } mp = null; }
        Toast.makeText(c, "تُنزَّل سورة " + surah + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File[] old = c.getCacheDir().listFiles();
                if (old != null) for (File x : old) if (x.getName().startsWith("l_") && !x.equals(f)) x.delete();
                Net.download(url, f);
                h.post(() -> { if (focus != null) start(aa, f.getPath(), false, url, f); });
            } catch (Exception e) { h.post(() -> fail("لم تُنزَّل: " + e.getMessage())); }
        }).start();
    }

    private void fail(String why) {
        stop();
        Toast.makeText(c, "لم تعمل التلاوة: " + why + ". تأكد من الإنترنت ثم أعد المحاولة", Toast.LENGTH_LONG).show();
    }

    /** Pause or go on. */
    void toggle() {
        if (mp == null) return;
        try {
            if (mp.isPlaying()) { mp.pause(); paused = true; }
            else { paused = false; if (focus != null) am.requestAudioFocus(focus); mp.start(); }
        } catch (Exception ignored) { }
        changed.run();
    }

    void stop() {
        boolean was = mp != null;
        if (mp != null) { try { mp.release(); } catch (Exception ignored) { } mp = null; }
        if (focus != null) { am.abandonAudioFocusRequest(focus); focus = null; }
        paused = pausedByFocus = false;
        if (was) changed.run();
    }
}
