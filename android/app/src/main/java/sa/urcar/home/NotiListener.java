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
    /** Radarbot's alert from its notification (title and text) and when it came; shown for 20 seconds. Not yet verified
     *  on the owner's screen that Radarbot puts the alert there: if it does not, the line stays empty. */
    static volatile String radar;
    static volatile long radarAt;
    static final String RADARBOT = "com.vialsoft.radarbot_free";

    /**
     * The navigation app's next step, from its ongoing navigation notification (Google Maps puts the distance in the
     * title, the instruction in the text, the arrival in the sub-text and the turn's arrow as the large icon). Kept
     * until the notification goes. Not yet seen on the owner's screen; Waze's notification may say less.
     */
    static volatile String navDist = "", navStep = "", navEta = "";
    static volatile android.graphics.drawable.Drawable navIcon;
    static volatile String navPkg = "";
    static final String[] NAV_APPS = {"com.google.android.apps.maps", "com.waze", "com.here.app.maps", "net.osmand", "net.osmand.plus", "com.sygic.aura"};
    static boolean navigating() { return !navPkg.isEmpty() && !(navDist.isEmpty() && navStep.isEmpty()); }

    private void nav(android.service.notification.StatusBarNotification sbn) {
        android.app.Notification n = sbn.getNotification();
        boolean navCat = android.app.Notification.CATEGORY_NAVIGATION.equals(n.category);
        if (!java.util.Arrays.asList(NAV_APPS).contains(sbn.getPackageName()) || !sbn.isOngoing() || !(navCat || sbn.getPackageName().equals("com.waze"))) return;
        android.os.Bundle x = n.extras;
        CharSequence t = x.getCharSequence(android.app.Notification.EXTRA_TITLE), s = x.getCharSequence(android.app.Notification.EXTRA_TEXT),
                sub = x.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT);
        navDist = t == null ? "" : t.toString().trim();
        navStep = s == null ? "" : s.toString().trim();
        navEta = sub == null ? "" : sub.toString().trim();
        try { navIcon = n.getLargeIcon() != null ? n.getLargeIcon().loadDrawable(this) : null; } catch (Exception e) { navIcon = null; }
        navPkg = sbn.getPackageName();
    }
    @Override public void onListenerConnected() {   // a navigation already running when the screen starts
        try { for (android.service.notification.StatusBarNotification sbn : getActiveNotifications()) nav(sbn); } catch (Exception ignored) { }
    }

    @Override public void onNotificationPosted(android.service.notification.StatusBarNotification sbn) {
        nav(sbn);
        if (!sbn.getPackageName().startsWith("com.vialsoft.radarbot")) return;
        android.os.Bundle x = sbn.getNotification().extras;
        CharSequence t = x.getCharSequence(android.app.Notification.EXTRA_TITLE), s = x.getCharSequence(android.app.Notification.EXTRA_TEXT);
        String v = ((t == null ? "" : t) + (s == null || s.length() == 0 ? "" : "، " + s)).trim();
        if (v.isEmpty() || v.toLowerCase().contains("radarbot")) return;   // its ongoing «running» notice is not an alert
        radar = v;
        radarAt = System.currentTimeMillis();
    }
    @Override public void onNotificationRemoved(android.service.notification.StatusBarNotification sbn) {
        if (sbn.getPackageName().startsWith("com.vialsoft.radarbot")) radarAt = 0;
        if (sbn.getPackageName().equals(navPkg) && sbn.isOngoing()) { navPkg = ""; navDist = navStep = navEta = ""; navIcon = null; }
    }


    /** Android's own answer first (API 27); the secure setting as a second opinion. */
    static boolean allowed(Context c) {
        try {
            android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
            if (nm != null && nm.isNotificationListenerAccessGranted(new ComponentName(c, NotiListener.class))) return true;
        } catch (Exception ignored) { }
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

    /** The other apps whose media session plays now. */
    static java.util.Set<String> playingPackages(Context c) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (!allowed(c)) return out;
        try {
            MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController mc : m.getActiveSessions(new ComponentName(c, NotiListener.class)))
                if (!mc.getPackageName().equals(c.getPackageName()) && playing(mc)) out.add(mc.getPackageName());
        } catch (Exception ignored) { }
        return out;
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
