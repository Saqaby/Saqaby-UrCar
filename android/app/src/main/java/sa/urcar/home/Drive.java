package sa.urcar.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The other side of the home screen: the speed and the car's heading from GPS, a Radarbot alert when it has one,
 * and the quote card that changes every hour. Tapping the speed opens the navigation app.
 */
final class Drive extends LinearLayout {
    private final float H;
    private Ui.Theme th;
    private final LinearLayout top, alertBox, quoteBox;
    private final TextView speed, unit, heading, alert, qTitle, qText, qSrc;
    private final Compass compass;

    Drive(Context c, int height, Runnable openNav) {
        super(c);
        H = height / 100f;
        setOrientation(VERTICAL);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        setPadding(px(2.6f), px(2), px(2.6f), px(2));

        // ---- speed and heading
        top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(px(4), px(2.4f), px(4), px(2.4f));
        LinearLayout sp = new LinearLayout(c);
        sp.setOrientation(VERTICAL);
        sp.setGravity(Gravity.CENTER);
        speed = Ui.text(c, px(22), Ui.display(c), 0);
        speed.setGravity(Gravity.CENTER);
        unit = Ui.text(c, px(3.6f), Ui.regular(c), 0);
        unit.setText("كم/س");
        sp.addView(speed);
        sp.addView(unit);
        top.addView(sp, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        LinearLayout cp = new LinearLayout(c);
        cp.setOrientation(VERTICAL);
        cp.setGravity(Gravity.CENTER);
        compass = new Compass(c);
        heading = Ui.text(c, px(3.8f), Ui.medium(c), 0);
        heading.setGravity(Gravity.CENTER);
        cp.addView(compass, new LayoutParams(px(26), px(26)));
        cp.addView(heading, lp(px(1.2f)));
        top.addView(cp, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        top.setOnClickListener(v -> openNav.run());
        addView(top, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.25f));

        // ---- Radarbot
        alertBox = new LinearLayout(c);
        alertBox.setGravity(Gravity.CENTER_VERTICAL);
        alertBox.setPadding(px(2.4f), 0, px(2.4f), 0);
        alert = Ui.text(c, px(3.6f), Ui.medium(c), 0);
        alert.setEllipsize(android.text.TextUtils.TruncateAt.END);
        alertBox.addView(alert);
        alertBox.setVisibility(GONE);
        addView(alertBox, new LayoutParams(LayoutParams.MATCH_PARENT, px(8)) {{ topMargin = px(1.6f); }});

        // ---- the quote of the hour
        quoteBox = new LinearLayout(c);
        quoteBox.setOrientation(VERTICAL);
        quoteBox.setGravity(Gravity.CENTER_VERTICAL);
        quoteBox.setPadding(px(3), px(2), px(3), px(2));
        qTitle = Ui.text(c, px(2.8f), Ui.regular(c), 0);
        qText = Ui.text(c, px(3.8f), Ui.medium(c), 0);
        qText.setSingleLine(false);
        qText.setMaxLines(3);
        qText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        qText.setLineSpacing(0, 1.35f);
        qSrc = Ui.text(c, px(2.5f), Ui.regular(c), 0);
        quoteBox.addView(qTitle);
        quoteBox.addView(qText, lp(px(1.2f)));
        quoteBox.addView(qSrc, lp(px(1.2f)));
        addView(quoteBox, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1) {{ topMargin = px(1.6f); }});
        setDay(false);
    }
    private int px(float h) { return Math.round(h * H); }
    private LayoutParams lp(int top) { LayoutParams l = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); l.topMargin = top; return l; }

    void setDay(boolean d) {
        th = new Ui.Theme(d);
        setBackgroundColor(th.bg);
        top.setBackground(Ui.box(th.panel, th.line, px(2.6f)));
        speed.setTextColor(th.fg); unit.setTextColor(th.muted); heading.setTextColor(th.sand);
        compass.colors(th);
        alertBox.setBackground(Ui.box(0xffe9a24b, 0, px(1.6f))); alert.setTextColor(0xff1b1408);
        quoteBox.setBackground(Ui.box(th.panel, th.line, px(2.6f)));
        qTitle.setTextColor(th.sand); qText.setTextColor(th.fg); qSrc.setTextColor(th.muted);
    }

    private static final String[] DIRS = {"شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب"};

    /** kmh < 0: no GPS fix; bearing < 0: unknown (only while moving does GPS give one). */
    void showSpeed(int kmh, float bearing) {
        set(speed, kmh < 0 ? "—" : Ui.ar(kmh));
        if (bearing >= 0) {
            compass.bearing = bearing;
            compass.invalidate();
            set(heading, DIRS[Math.round(bearing / 45f) % 8]);
        } else if (compass.bearing < 0) set(heading, "الاتجاه أثناء السير");
    }
    void showAlert(String text) {
        alertBox.setVisibility(text == null ? GONE : VISIBLE);
        if (text != null) set(alert, text);
    }
    void showQuote(String[] q) { set(qTitle, q[0]); set(qText, q[1]); set(qSrc, q[2]); }

    private static void set(TextView t, String s) { if (!s.contentEquals(t.getText())) t.setText(s); }

    /** A compass rose that turns: the car's arrow points up, north («ش») moves around it. */
    static final class Compass extends View {
        float bearing = -1;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG),
                car = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);
        Compass(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(Ui.medium(c));
        }
        void colors(Ui.Theme th) {
            ring.setColor(th.line); mark.setColor(th.muted); car.setColor(th.fg); label.setColor(th.sand);
            invalidate();
        }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), cx = w / 2, cy = getHeight() / 2f, r = Math.min(w, getHeight()) / 2f - 4;
            ring.setStrokeWidth(r * .08f);
            cv.drawCircle(cx, cy, r, ring);
            cv.save();
            cv.rotate(bearing < 0 ? 0 : -bearing, cx, cy);   // north turns against the heading
            for (int i = 0; i < 8; i++) {
                cv.save();
                cv.rotate(i * 45, cx, cy);
                mark.setStrokeWidth(i % 2 == 0 ? r * .06f : r * .03f);
                cv.drawLine(cx, cy - r, cx, cy - r * (i % 2 == 0 ? .8f : .88f), mark);
                cv.restore();
            }
            label.setTextSize(r * .3f);
            cv.drawText("ش", cx, cy - r * .5f, label);
            cv.restore();
            Path p = new Path();   // the car: an arrow pointing up
            p.moveTo(cx, cy - r * .42f);
            p.lineTo(cx + r * .26f, cy + r * .32f);
            p.lineTo(cx, cy + r * .16f);
            p.lineTo(cx - r * .26f, cy + r * .32f);
            p.close();
            car.setAlpha(bearing < 0 ? 110 : 255);
            cv.drawPath(p, car);
        }
    }
}
