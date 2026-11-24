package sa.urcar.probe;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Only to ask Android for its split screen (GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN); it reads nothing on the screen. */
public class SplitA11y extends AccessibilityService {
    static SplitA11y live;
    @Override protected void onServiceConnected() { live = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }
    @Override public boolean onUnbind(android.content.Intent i) { if (live == this) live = null; return super.onUnbind(i); }
}
