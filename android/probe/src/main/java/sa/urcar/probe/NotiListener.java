package sa.urcar.probe;

import android.service.notification.NotificationListenerService;

/** Exists only so the owner can grant «الوصول إلى الإشعارات»; the activity reads through it. */
public class NotiListener extends NotificationListenerService {
    static NotiListener live;
    @Override public void onListenerConnected() { live = this; }
    @Override public void onListenerDisconnected() { if (live == this) live = null; }
}
