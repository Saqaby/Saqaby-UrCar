package sa.urcar.home;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;

import java.io.File;

/**
 * The adhan at each prayer time, and the Quran radio stream when there is no FM station near. The adhan takes the
 * audio focus for a moment (AUDIOFOCUS_GAIN_TRANSIENT): what was playing pauses and comes back after it, as in UrTV.
 */
final class Sound {
    /** عبدالملك الملا, from UrTV's Adhan.java (archive.org, Public Domain Mark 1.0) */
    static final String ADHAN_URL = "https://archive.org/download/azaan-of-world-mosques_202608/"
            + "%D8%B9%D8%A8%D8%AF%D8%A7%D9%84%D9%85%D9%84%D9%83%20%D8%A7%D9%84%D9%85%D9%84%D8%A7_%D9%85%D9%83%D8%A9"
            + "%20%28www.alqurantranslation.com%29.mp3";
    /** إذاعة القرآن الكريم السعودية, the first station of UrTV's stations.json */
    static final String RADIO_URL = "https://stream.radiojar.com/0tpy1h0kxtzuv";

    private final Context c;
    private final AudioManager am;
    private MediaPlayer adhan, radio;
    private AudioFocusRequest adhanFocus, radioFocus;
    boolean radioOn;
    Runnable changed = () -> { };

    Sound(Context c) { this.c = c.getApplicationContext(); am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE); }

    private File adhanFile() { return new File(c.getFilesDir(), "adhan.mp3"); }

    /** Keeps the adhan on the screen, so it plays without the internet. */
    void cacheAdhan() {
        File f = adhanFile();
        if (f.length() > 50_000) return;
        try { Net.download(ADHAN_URL, f); } catch (Exception ignored) { }
    }

    /** mode: full, takbir (the first 15 seconds, faded out) or silent (the bar only); volume 0..1 of the alarm volume */
    void playAdhan(String mode, float volume) {
        if (mode.equals("silent")) return;
        playAdhan();
        if (adhan != null) adhan.setVolume(volume, volume);
        if (mode.equals("takbir")) fadeAfter(15_000, volume);
    }
    private final android.os.Handler hd = new android.os.Handler(android.os.Looper.getMainLooper());
    private void fadeAfter(long ms, float from) {
        final MediaPlayer mp = adhan;
        for (int i = 1; i <= 10; i++) {
            final float v = from * (10 - i) / 10f;
            hd.postDelayed(() -> { if (adhan == mp && mp != null) { if (v > 0) mp.setVolume(v, v); } }, ms + i * 200L);
        }
        hd.postDelayed(() -> { if (adhan == mp && completion != null) completion.run(); }, ms + 2200);
    }
    private Runnable completion;

    void playAdhan() {
        boolean wasRadio = radioOn;
        if (wasRadio) stopRadio();
        stopAdhan();
        File f = adhanFile();
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        adhanFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(aa).build();
        am.requestAudioFocus(adhanFocus);
        try {
            adhan = new MediaPlayer();
            adhan.setAudioAttributes(aa);
            adhan.setDataSource(f.length() > 50_000 ? f.getPath() : ADHAN_URL);
            completion = () -> { stopAdhan(); if (wasRadio) startRadio(); };
            adhan.setOnCompletionListener(mp -> completion.run());
            adhan.setOnErrorListener((mp, w, e) -> { stopAdhan(); return true; });
            adhan.setOnPreparedListener(MediaPlayer::start);
            adhan.prepareAsync();
        } catch (Exception e) { stopAdhan(); }
    }
    void stopAdhan() {
        if (adhan != null) { try { adhan.release(); } catch (Exception ignored) { } adhan = null; }
        if (adhanFocus != null) { am.abandonAudioFocusRequest(adhanFocus); adhanFocus = null; }
    }

    void startRadio() {
        stopRadio();
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        radioFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(aa)
                .setOnAudioFocusChangeListener(fc -> { if (fc == AudioManager.AUDIOFOCUS_LOSS) stopRadio(); }).build();
        am.requestAudioFocus(radioFocus);
        try {
            radio = new MediaPlayer();
            radio.setAudioAttributes(aa);
            radio.setDataSource(RADIO_URL);
            radio.setOnPreparedListener(MediaPlayer::start);
            radio.setOnErrorListener((mp, w, e) -> { stopRadio(); return true; });
            radio.prepareAsync();
            radioOn = true;
        } catch (Exception e) { stopRadio(); }
        changed.run();
    }
    void stopRadio() {
        if (radio != null) { try { radio.release(); } catch (Exception ignored) { } radio = null; }
        if (radioFocus != null) { am.abandonAudioFocusRequest(radioFocus); radioFocus = null; }
        radioOn = false;
        changed.run();
    }

    /** The owner's own recordings of the riding dhikr and the travel du'a (chosen in the settings, kept in the app). */
    static File duaFile(Context c, String which) { return new File(c.getFilesDir(), "dua_" + which + ".mp3"); }

    private MediaPlayer dua;
    private AudioFocusRequest duaFocus;
    /** Plays it once over what is playing (the music pauses for it and comes back), unless the adhan is on. */
    void playDua(String which, float volume) {
        File f = duaFile(c, which);
        if (!f.exists() || adhan != null) return;
        stopDua();
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        duaFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(aa).build();
        am.requestAudioFocus(duaFocus);
        try {
            dua = new MediaPlayer();
            dua.setAudioAttributes(aa);
            dua.setDataSource(f.getPath());
            dua.setVolume(volume, volume);
            dua.setOnCompletionListener(mp -> stopDua());
            dua.setOnErrorListener((mp, w, e) -> { stopDua(); return true; });
            dua.prepare();
            dua.start();
        } catch (Exception e) { stopDua(); }
    }
    void stopDua() {
        if (dua != null) { try { dua.release(); } catch (Exception ignored) { } dua = null; }
        if (duaFocus != null) { am.abandonAudioFocusRequest(duaFocus); duaFocus = null; }
    }

    void release() { stopAdhan(); stopRadio(); stopDua(); }
}
