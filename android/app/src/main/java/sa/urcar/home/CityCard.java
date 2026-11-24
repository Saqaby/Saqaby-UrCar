package sa.urcar.home;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * «مرحبًا بك في الخبر» over whatever app is in front (YouTube, the navigation …), when the home screen is not:
 * the same card as on the home screen, in a window of its own («العرض فوق التطبيقات», the permission the strip
 * uses). It takes no touch, so the app under it goes on working; it comes in, stays four seconds and fades away.
 */
final class CityCard {
    private CityCard() {}

    /** False when Android does not allow it (no permission): the welcome then waits for the home screen. */
    static boolean show(Context c, String city, boolean day) {
        if (!android.provider.Settings.canDrawOverlays(c)) return false;
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        Ui.Theme th = new Ui.Theme(day);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(70, 34, 70, 40);
        card.setBackground(Ui.box(th.panel, th.sand, 28));
        TextView a = Ui.text(c, 30, Ui.regular(c), th.muted);
        a.setText("مرحبًا بك في");
        TextView b = Ui.text(c, 76, Ui.medium(c), th.sand);
        b.setText(city);
        card.addView(a, new LinearLayout.LayoutParams(-2, -2));
        card.addView(b, new LinearLayout.LayoutParams(-2, -2));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        try { wm.addView(card, lp); } catch (Exception e) { return false; }
        card.setAlpha(0f); card.setScaleX(.85f); card.setScaleY(.85f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
        card.postDelayed(() -> card.animate().alpha(0f).translationY(-40).setDuration(900)
                .withEndAction(() -> remove(wm, card)).start(), 4700);
        card.postDelayed(() -> remove(wm, card), 7000);   // whatever happens to the animation, gone
        return true;
    }
    private static void remove(WindowManager wm, View v) {
        if (v.isAttachedToWindow()) try { wm.removeView(v); } catch (Exception ignored) { }
    }
}
