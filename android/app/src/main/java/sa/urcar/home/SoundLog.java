package sa.urcar.home;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * «سجل الصوت»: what the screen tells Android while a call or the phone's sound comes, to find out which of it can be
 * relied on (the owner's second call was not seen at all). Every second the audio mode, each media session and its
 * state, and the sounds playing (their use only) are compared with the second before, and a change is written down,
 * with the khatma's own events and the packages that post a notification (the package only: never a name or a number).
 * The last 300 lines, kept on the screen; the settings show them.
 */
final class SoundLog {
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static String lastMode = "", lastSessions = "", lastPlayback = "";
    private static final SimpleDateFormat T = new SimpleDateFormat("HH:mm:ss", Locale.US);

    static synchronized void add(Context c, String what) {
        if (lines.isEmpty()) load(c);
        lines.addLast(T.format(new Date()) + "  " + what);
        while (lines.size() > 300) lines.removeFirst();
        save(c);
    }
    static synchronized String last(Context c, int n) {
        if (lines.isEmpty()) load(c);
        StringBuilder b = new StringBuilder();
        Object[] a = lines.toArray();
        for (int i = Math.max(0, a.length - n); i < a.length; i++) b.append(a[i]).append('\n');
        return b.length() == 0 ? "لا شيء بعد" : b.toString();
    }
    static synchronized void clear(Context c) { lines.clear(); save(c); }

    /** Once a second from the service. */
    static void watch(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        String[] modes = {"عادي", "رنين", "مكالمة", "اتصال صوتي"};
        int m = am.getMode();
        String mode = m >= 0 && m < modes.length ? modes[m] : String.valueOf(m);
        if (!mode.equals(lastMode)) { lastMode = mode; add(c, "وضع الصوت: " + mode); }

        StringBuilder s = new StringBuilder();
        if (NotiListener.allowed(c)) try {
            MediaSessionManager msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController mc : msm.getActiveSessions(new ComponentName(c, NotiListener.class))) {
                PlaybackState st = mc.getPlaybackState();
                s.append(mc.getPackageName()).append('=').append(st == null ? "?" : state(st.getState())).append(' ');
            }
        } catch (Exception ignored) { }
        String ss = s.toString().trim();
        if (!ss.equals(lastSessions)) { lastSessions = ss; add(c, "الجلسات: " + (ss.isEmpty() ? "لا شيء" : ss)); }

        StringBuilder p = new StringBuilder();
        try {
            java.lang.reflect.Method act = null;
            try { act = AudioPlaybackConfiguration.class.getMethod("isActive"); } catch (Throwable ignored) { }
            for (AudioPlaybackConfiguration pc : am.getActivePlaybackConfigurations()) {
                String a = "";
                if (act != null) try { a = (Boolean) act.invoke(pc) ? "+" : "-"; } catch (Throwable ignored) { }
                p.append(a).append(usage(pc.getAudioAttributes().getUsage())).append(' ');
            }
        } catch (Exception ignored) { }
        p.append(am.isMusicActive() ? "| موسيقى تعمل" : "| لا موسيقى");
        String pp = p.toString().trim();
        if (!pp.equals(lastPlayback)) { lastPlayback = pp; add(c, "الأصوات: " + pp); }
    }

    private static String state(int s) {
        switch (s) {
            case PlaybackState.STATE_PLAYING: return "يعمل";
            case PlaybackState.STATE_PAUSED: return "موقوف";
            case PlaybackState.STATE_STOPPED: return "متوقف";
            case PlaybackState.STATE_BUFFERING: return "يحمّل";
            case PlaybackState.STATE_NONE: return "لا شيء";
            default: return String.valueOf(s);
        }
    }
    private static String usage(int u) {
        switch (u) {
            case 1: return "media";
            case 2: return "call";
            case 3: return "call-signal";
            case 4: return "alarm";
            case 5: return "notification";
            case 6: return "ringtone";
            case 12: return "navigation";
            case 14: return "game";
            case 16: return "assistant";
            default: return "u" + u;
        }
    }

    private static File file(Context c) { return new File(c.getFilesDir(), "soundlog.txt"); }
    private static void load(Context c) {
        try {
            byte[] b = java.nio.file.Files.readAllBytes(file(c).toPath());
            for (String l : new String(b, StandardCharsets.UTF_8).split("\n")) if (!l.isEmpty()) lines.addLast(l);
        } catch (Exception ignored) { }
    }
    private static void save(Context c) {
        try (FileOutputStream o = new FileOutputStream(file(c))) {
            StringBuilder b = new StringBuilder();
            for (String l : lines) b.append(l).append('\n');
            o.write(b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }
}
