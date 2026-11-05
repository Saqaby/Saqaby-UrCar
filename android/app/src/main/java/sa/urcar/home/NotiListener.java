package sa.urcar.home;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

import java.util.List;

/**
 * «الوصول إلى الإشعارات»: Android shows the media sessions of other apps only to an app the owner allowed this.
 * Through it the music page follows whatever played last: YouTube, SoundCloud, Spotify … (probe v3: YouTube gives
 * title, artist and controls; the phone over Bluetooth gives the state only).
 */
public class NotiListener extends NotificationListenerService {

    static boolean allowed(Context c) {
        String on = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return on != null && on.contains(c.getPackageName());
    }

    /** The session to show: the one playing, else the first; never our own. */
    static MediaController session(Context c) {
        if (!allowed(c)) return null;
        try {
            MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> all = m.getActiveSessions(new ComponentName(c, NotiListener.class));
            MediaController first = null;
            for (MediaController mc : all) {
                if (mc.getPackageName().equals(c.getPackageName())) continue;
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) return mc;
                if (first == null && mc.getMetadata() != null) first = mc;
            }
            return first;
        } catch (Exception e) { return null; }
    }

    static String title(MediaController mc) {
        MediaMetadata md = mc.getMetadata();
        String t = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        return t == null || t.equals("Not Provided") ? "" : t;
    }
    static String artist(MediaController mc) {
        MediaMetadata md = mc.getMetadata();
        String a = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        return a == null || a.equals("null") ? "" : a;
    }
    static boolean playing(MediaController mc) {
        PlaybackState st = mc.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }
    static String appName(Context c, String pkg) {
        if (pkg.equals("com.tw.bt")) return "الجوال (بلوتوث)";
        try { return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }
}
