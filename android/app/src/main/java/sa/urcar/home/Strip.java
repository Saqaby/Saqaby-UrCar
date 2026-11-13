package sa.urcar.home;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A narrow strip over the navigation app: the next prayer and the time left to it, and the sound's button, so the map
 * need not be left for them (the speed and the limit were on it at first: the navigation app shows them itself).
 * In another app while a guidance runs (YouTube, …) it turns into the guidance's next step instead, across the top
 * of the screen, and a tap on it goes back to the navigation. Each of the two keeps its own place. A tap on it comes back to the home screen; it is dragged to where it hides
 * nothing, and stays there. Needs «العرض فوق التطبيقات».
 */
final class Strip {
    private final Context c;
    private final WindowManager wm;
    private final WindowManager.LayoutParams lp;
    private final LinearLayout box;
    private final TextView left, prayer;
    private final Ui.Icon play;
    private final android.widget.ImageView arrow;
    private boolean shown, day, navMode;
    private final Runnable home, toNav;
    private final android.util.DisplayMetrics m;

    Strip(Context c, Runnable home, Runnable toNav, Runnable toggle) {
        this.c = c;
        this.home = home;
        this.toNav = toNav;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, 76,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        m = c.getResources().getDisplayMetrics();
        place();
        box = new LinearLayout(c);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(22, 0, 10, 0);
        prayer = Ui.text(c, 28, Ui.medium(c), 0);
        left = Ui.text(c, 36, Ui.display(c), 0);
        left.setPadding(16, 0, 14, 0);
        play = new Ui.Icon(c, Ui.Icon.PLAY, 0);
        LinearLayout pb = new LinearLayout(c);
        pb.setGravity(Gravity.CENTER);
        pb.addView(play, new LinearLayout.LayoutParams(30, 30));
        pb.setOnClickListener(v -> toggle.run());
        arrow = new android.widget.ImageView(c);
        arrow.setVisibility(View.GONE);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(52, 52);
        al.setMarginEnd(12);
        box.addView(arrow, al);
        box.addView(prayer);
        box.addView(left);
        prayer.setEllipsize(android.text.TextUtils.TruncateAt.END);
        prayer.setMaxWidth(620);
        box.addView(pb, new LinearLayout.LayoutParams(64, 64));
        box.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0; int px, py; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: x0 = e.getRawX(); y0 = e.getRawY(); px = lp.x; py = lp.y; moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
                        if (!moved && Math.abs(dx) + Math.abs(dy) < 24) return true;
                        moved = true;
                        lp.x = px + (int) dx; lp.y = py + (int) dy;
                        try { wm.updateViewLayout(box, lp); } catch (Exception ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP:
                        String k = navMode ? "stripn_" : "strip_";
                        if (moved) Prefs.of(c).edit().putInt(k + "x", lp.x).putInt(k + "y", lp.y).apply(); else (navMode ? toNav : home).run();
                        return true;
                    default: return true;
                }
            }
        });
        paint(false);
    }

    private void paint(boolean d) {
        day = d;
        Ui.Theme th = new Ui.Theme(d);
        box.setBackground(Ui.box(th.panel, th.sand, 22));
        prayer.setTextColor(th.sand);
        left.setTextColor(th.fg);
        play.set(play.kind, th.sand);
    }

    /** Shown or not; a refusal by Android (the permission taken back) is swallowed and tried again next time. */
    void show(boolean on) {
        if (on == shown) return;
        try { if (on) wm.addView(box, lp); else wm.removeView(box); shown = on; } catch (Exception ignored) { }
    }
    boolean shown() { return shown; }

    /** «العصر» and «١ س ٢٠ د» (or «إقامة العصر» and «٨ د» while it is awaited). */
    void update(boolean d, String prayerText, String leftText, boolean playing) {
        mode(false);
        if (d != day) paint(d);
        Ui.Theme th0 = new Ui.Theme(d);
        prayer.setTextColor(th0.sand);
        left.setTextColor(th0.fg);
        if (!prayerText.contentEquals(prayer.getText())) prayer.setText(prayerText);
        if (!leftText.contentEquals(left.getText())) left.setText(leftText);
        int k = playing ? Ui.Icon.PAUSE : Ui.Icon.PLAY;
        if (play.kind != k) play.set(k, new Ui.Theme(d).sand);
    }

    /** The prayer's strip low on the driver's side; the guidance's across the top, in the middle. */
    private void place() {
        boolean right = Prefs.side(c).equals("right");
        if (navMode) {
            lp.x = Prefs.of(c).getInt("stripn_x", m.widthPixels / 2 - 420);
            lp.y = Prefs.of(c).getInt("stripn_y", 12);
        } else {
            lp.x = Prefs.of(c).getInt("strip_x", right ? m.widthPixels - 440 : 24);
            lp.y = Prefs.of(c).getInt("strip_y", m.heightPixels - 190);
        }
    }
    private void mode(boolean nav) {
        if (nav == navMode) return;
        navMode = nav;
        arrow.setVisibility(nav && arrow.getDrawable() != null ? View.VISIBLE : View.GONE);
        place();
        if (shown) try { wm.updateViewLayout(box, lp); } catch (Exception ignored) { }
    }
    /** The guidance's next step: its arrow, «انعطف يمينًا إلى …» and «٣٥٠ م». */
    void updateNav(boolean d, android.graphics.drawable.Drawable icon, String step, String dist, boolean playing) {
        mode(true);
        if (d != day) paint(d);
        Ui.Theme th = new Ui.Theme(d);
        if (arrow.getDrawable() != icon) {
            arrow.setImageDrawable(icon);
            if (icon != null) arrow.setColorFilter(th.sand, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        arrow.setVisibility(icon == null ? View.GONE : View.VISIBLE);
        if (!step.contentEquals(prayer.getText())) prayer.setText(step);
        if (!dist.contentEquals(left.getText())) left.setText(dist);
        prayer.setTextColor(th.fg);
        left.setTextColor(th.sand);
        int k = playing ? Ui.Icon.PAUSE : Ui.Icon.PLAY;
        if (play.kind != k) play.set(k, th.sand);
    }

    /** Back to their first places (the settings), for a strip dragged out of sight. */
    static void resetPlace(Context c) { Prefs.of(c).edit().remove("strip_x").remove("strip_y").remove("stripn_x").remove("stripn_y").apply(); }
}
