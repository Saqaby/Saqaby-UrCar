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
 * A narrow strip over the navigation app: the speed and the road's limit, the next prayer, and the sound's button, so
 * the map need not be left for them. A tap on it comes back to the home screen; it is dragged to where it hides
 * nothing, and stays there. Needs «العرض فوق التطبيقات».
 */
final class Strip {
    private final Context c;
    private final WindowManager wm;
    private final WindowManager.LayoutParams lp;
    private final LinearLayout box;
    private final TextView speed, prayer;
    private final Drive.Sign sign;
    private final Ui.Icon play;
    private boolean shown, day;

    Strip(Context c, Runnable home, Runnable toggle) {
        this.c = c;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, 76,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        boolean right = Prefs.side(c).equals("right");
        lp.x = Prefs.of(c).getInt("strip_x", right ? m.widthPixels - 440 : 24);
        lp.y = Prefs.of(c).getInt("strip_y", m.heightPixels - 190);
        box = new LinearLayout(c);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(22, 0, 10, 0);
        speed = Ui.text(c, 44, Ui.display(c), 0);
        sign = new Drive.Sign(c);
        prayer = Ui.text(c, 26, Ui.medium(c), 0);
        prayer.setPadding(22, 0, 14, 0);
        play = new Ui.Icon(c, Ui.Icon.PLAY, 0);
        LinearLayout pb = new LinearLayout(c);
        pb.setGravity(Gravity.CENTER);
        pb.addView(play, new LinearLayout.LayoutParams(30, 30));
        pb.setOnClickListener(v -> toggle.run());
        box.addView(speed);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(52, 52);
        sl.setMarginStart(12);
        box.addView(sign, sl);
        box.addView(prayer);
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
                        if (moved) Prefs.of(c).edit().putInt("strip_x", lp.x).putInt("strip_y", lp.y).apply(); else home.run();
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
        play.set(play.kind, th.sand);
    }

    /** Shown or not; a refusal by Android (the permission taken back) is swallowed and tried again next time. */
    void show(boolean on) {
        if (on == shown) return;
        try { if (on) wm.addView(box, lp); else wm.removeView(box); shown = on; } catch (Exception ignored) { }
    }
    boolean shown() { return shown; }

    void update(boolean d, int kmh, int limit, String prayerText, boolean playing) {
        if (d != day) paint(d);
        Ui.Theme th = new Ui.Theme(d);
        String s = kmh < 0 ? "—" : Ui.ar(kmh);
        if (!s.contentEquals(speed.getText())) speed.setText(s);
        speed.setTextColor(limit > 0 && kmh > limit ? 0xffe0533d : th.fg);
        if (sign.limit != limit) { sign.limit = limit; sign.invalidate(); }
        sign.setVisibility(limit > 0 ? View.VISIBLE : View.GONE);
        if (!prayerText.contentEquals(prayer.getText())) prayer.setText(prayerText);
        int k = playing ? Ui.Icon.PAUSE : Ui.Icon.PLAY;
        if (play.kind != k) play.set(k, th.sand);
    }

    /** Back to its first place (the settings), for a strip dragged out of sight. */
    static void resetPlace(Context c) { Prefs.of(c).edit().remove("strip_x").remove("strip_y").apply(); }
}
