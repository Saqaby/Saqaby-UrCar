package sa.urcar.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The welcome when the screen starts, after the owner's design (welcome.html): a road running to the horizon across
 * the foot, the mark «سيارتك / URCAR», «صباح الخير» or «مساء الخير», the day and the Hijri date, the riding du'a
 * (az-Zukhruf 13-14), and at the bottom the next prayer and the place, with a bar that fills over six seconds. Sizes
 * in hundredths of the screen's height (the design's cqh).
 */
final class Welcome extends FrameLayout {
    static long MS = 6000;   // not final: a test holds it longer
    private final float H;
    private final Road road;

    /** info: {greeting, date, next prayer, place}. */
    Welcome(Context c, int height, boolean day, String[] info, Runnable skip) {
        super(c);
        H = height / 100f;
        Ui.Theme th = new Ui.Theme(day);
        setBackgroundColor(th.bg);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        setOnClickListener(v -> skip.run());

        road = new Road(c, th);
        addView(road, new LayoutParams(LayoutParams.MATCH_PARENT, Math.round(height * .26f), Gravity.BOTTOM));

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        mid.setPadding(px(6), 0, px(6), px(12));

        // the mark: a gold square with the car, and the name
        LinearLayout mark = new LinearLayout(c);
        mark.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout logo = new FrameLayout(c);
        logo.setBackground(Ui.box(th.sand, 0, px(3)));
        logo.addView(new Ui.Icon(c, Ui.Icon.CAR, th.sandInk), new LayoutParams(px(7), px(7), Gravity.CENTER));
        mark.addView(logo, new LinearLayout.LayoutParams(px(11), px(11)));
        LinearLayout word = new LinearLayout(c);
        word.setOrientation(LinearLayout.VERTICAL);
        word.setGravity(Gravity.START);
        TextView name = Ui.text(c, px(10), Ui.display(c), th.fg);
        name.setText("سيارتك");
        name.setMinWidth(Math.round(name.getPaint().measureText("سيارتك") * 1.15f));   // the bold face's measure fell short of its drawing
        TextView en = Ui.text(c, px(2.6f), Ui.regular(c), th.muted);
        en.setText("URCAR");
        en.setLetterSpacing(.3f);
        en.setTextDirection(TEXT_DIRECTION_LTR);
        word.addView(name);
        word.addView(en, lp(px(1)));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.setMarginStart(px(2.4f));
        mark.addView(word, wl);
        mid.addView(mark, lp(0));

        TextView hi = Ui.text(c, px(5.4f), Ui.medium(c), th.fg);
        hi.setText(info[0]);
        mid.addView(hi, lp(px(2.2f)));
        TextView date = Ui.text(c, px(3), Ui.regular(c), th.muted);
        date.setText(info[1]);
        mid.addView(date, lp(px(2.2f)));

        LinearLayout ayah = new LinearLayout(c);
        ayah.setOrientation(LinearLayout.VERTICAL);
        ayah.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView a = Ui.text(c, px(4.6f), Ui.quran(c), th.fg);
        a.setSingleLine(false);
        a.setMaxLines(3);
        a.setHorizontallyScrolling(false);
        a.setGravity(Gravity.CENTER);
        a.setLineSpacing(0, 1.5f);
        a.setIncludeFontPadding(true);
        a.setMaxWidth(px(120));
        a.setText("﴿سُبۡحَٰنَ ٱلَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُۥ مُقۡرِنِينَ ۝ وَإِنَّآ إِلَىٰ رَبِّنَا لَمُنقَلِبُونَ﴾");
        TextView src = Ui.text(c, px(2.5f), Ui.regular(c), th.muted);
        src.setText("دعاء الركوب، سورة الزخرف ١٣–١٤");
        ayah.addView(a, lp(0));
        ayah.addView(src, lp(px(.8f)));
        mid.addView(ayah, lp(px(3.8f)));
        addView(mid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // the foot: the next prayer, the place, and the bar
        LinearLayout foot = new LinearLayout(c);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(px(6), 0, px(6), px(4.4f));
        foot.addView(pill(c, th, Ui.Icon.CLOCK, info[2]));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.setMarginStart(px(2.4f));
        if (!info[3].isEmpty()) foot.addView(pill(c, th, Ui.Icon.PIN, info[3]), pl);
        foot.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        TextView go = Ui.text(c, px(3), Ui.regular(c), th.muted);
        go.setText("اضغط للتخطي");
        foot.addView(go);
        FrameLayout bar = new FrameLayout(c);
        bar.setBackground(Ui.box(th.line, 0, px(.4f)));
        View fill = new View(c);
        fill.setBackground(Ui.box(th.sand, 0, px(.4f)));
        fill.setPivotX(px(22));   // fills from the right, as the design's transform-origin
        fill.setScaleX(0);
        bar.addView(fill, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(22), Math.max(3, px(.8f)));
        bl.setMarginStart(px(2));
        foot.addView(bar, bl);
        addView(foot, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        up(mark, 100, 700); up(hi, 350, 700); up(date, 500, 700); up(ayah, 750, 800); up(foot, 1000, 700);
        fill.animate().scaleX(1).setDuration(MS).setInterpolator(null).start();
    }
    private int px(float h) { return Math.round(h * H); }
    /** Centred in the column (said for each, as a right-to-left column's own gravity did not centre them). */
    private static LinearLayout.LayoutParams lp(int top) { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2); l.topMargin = top; l.gravity = Gravity.CENTER_HORIZONTAL; return l; }
    /** «wel-up»: in from a little below. */
    private void up(View v, long delay, long ms) {
        v.setAlpha(0); v.setTranslationY(px(2.4f));
        v.animate().alpha(1).translationY(0).setStartDelay(delay).setDuration(ms).setInterpolator(new DecelerateInterpolator()).start();
    }
    private LinearLayout pill(Context c, Ui.Theme th, int icon, String text) {
        LinearLayout p = new LinearLayout(c);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setBackground(Ui.box(th.panel, th.line, px(5)));
        p.setPadding(px(2.6f), px(1.2f), px(2.6f), px(1.2f));
        p.addView(new Ui.Icon(c, icon, th.sand), new LinearLayout.LayoutParams(px(3), px(3)));
        TextView t = Ui.text(c, px(3), Ui.medium(c), th.fg);
        t.setText(text);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.setMarginStart(px(1.2f));
        p.addView(t, tl);
        return p;
    }

    /** The way out: a fade and a slight swell, then gone. */
    void out(Runnable after) {
        setOnClickListener(null);
        animate().alpha(0).scaleX(1.03f).scaleY(1.03f).setDuration(600).withEndAction(after).start();
    }
    @Override protected void onDetachedFromWindow() { road.stop(); super.onDetachedFromWindow(); }

    /** The road: its two edges from a point on the horizon, the horizon's line and glow, and the centre line moving. */
    static final class Road extends View {
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG), dash = new Paint(Paint.ANTI_ALIAS_FLAG),
                glow = new Paint(Paint.ANTI_ALIAS_FLAG), hz = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float phase;
        private boolean on = true;
        static boolean NOANIM;
        Road(Context c, Ui.Theme th) {
            super(c);
            edge.setColor(th.line); edge.setStyle(Paint.Style.STROKE);
            dash.setColor(Ui.alpha(th.muted, .35f)); dash.setStyle(Paint.Style.STROKE);
            glow.setColor(Ui.alpha(th.sand, .10f));
            hz.setColor(Ui.alpha(th.sand, .45f)); hz.setStyle(Paint.Style.STROKE);
        }
        void stop() { on = false; }
        @Override protected void onDraw(Canvas cv) {
            // the design's 1600×300 box, «xMidYMax slice»: as wide as the screen, cut at the top if need be
            float w = getWidth(), h = getHeight(), s = Math.max(w / 1600f, h / 300f);
            cv.save();
            cv.translate((w - 1600 * s) / 2, h - 300 * s);
            cv.scale(s, s);
            cv.drawOval(new RectF(240, -60, 1360, 60), glow);
            hz.setStrokeWidth(2 / s * s); cv.drawLine(300, 0, 1300, 0, hz);
            edge.setStrokeWidth(3);
            cv.drawLine(785, 0, 330, 300, edge); cv.drawLine(815, 0, 1270, 300, edge);
            dash.setStrokeWidth(4);
            dash.setPathEffect(new android.graphics.DashPathEffect(new float[]{26, 34}, phase));
            Path p = new Path(); p.moveTo(800, 0); p.lineTo(800, 300);
            cv.drawPath(p, dash);
            cv.restore();
            if (on && !NOANIM) { phase = (phase - 60f / 84) % 60; postInvalidateOnAnimation(); }   // 60 units in 1.4 s, toward the car
        }
    }
}
