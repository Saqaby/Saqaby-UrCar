package sa.urcar.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A card opened to the whole screen, over the home screen: «gauge» (a speedometer and a compass with the qibla) or
 * «quote» (the verse, hadith or saying of the hour, large). A tap on «رجوع» or anywhere outside the buttons closes it.
 */
final class Full extends FrameLayout {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Ui.Theme th;
    private final Runnable close;
    private Gauge gauge;
    private Rose rose;
    private TextView headingText, qiblaText, tripText, qTitle, qText, qSrc;
    private int quoteOffset;   // «التالي» in the quote view: hours ahead of the current one

    Full(Context c, String kind, boolean day, Runnable close, Runnable openNav) {
        super(c);
        this.close = close;
        th = new Ui.Theme(day);
        setBackgroundColor(th.bg);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        setClickable(true);
        setOnClickListener(v -> close.run());
        if (kind.equals("gauge")) buildGauge(c, openNav); else buildQuote(c);
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(button(c, "رجوع", close));
        if (kind.equals("gauge")) bar.addView(button(c, "افتح الملاحة", openNav));
        else bar.addView(button(c, "التالي", () -> { quoteOffset++; tick.run(); }));
        LayoutParams bl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        bl.setMargins(24, 20, 24, 0);
        addView(bar, bl);
        h.post(tick);
    }

    private TextView button(Context c, String t, Runnable r) {
        TextView b = Ui.text(c, 26, Ui.medium(c), th.fg);
        b.setText(t);
        b.setPadding(32, 16, 32, 16);
        b.setBackground(Ui.box(th.panel, th.line, 16));
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.setMarginEnd(14);
        b.setLayoutParams(l);
        return b;
    }

    private void buildGauge(Context c, Runnable openNav) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER);
        // the speedometer big on one side; on the other the compass, and the trip under it
        gauge = new Gauge(c);
        row.addView(gauge, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.8f));
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        right.setPadding(0, 90, 0, 0);   // under «رجوع» and «افتح الملاحة»
        rose = new Rose(c);
        right.addView(rose, new LinearLayout.LayoutParams(-1, 0, 1));
        headingText = Ui.text(c, 34, Ui.medium(c), th.sand);
        headingText.setGravity(Gravity.CENTER);
        qiblaText = Ui.text(c, 22, Ui.regular(c), th.muted);
        qiblaText.setGravity(Gravity.CENTER);
        right.addView(headingText, new LinearLayout.LayoutParams(-1, -2));
        right.addView(qiblaText, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 6; }});
        tripText = Ui.text(c, 28, Ui.medium(c), th.fg);
        tripText.setGravity(Gravity.CENTER);
        tripText.setSingleLine(false);
        tripText.setPadding(20, 14, 20, 14);
        tripText.setBackground(Ui.box(th.panel, th.line, 16));
        right.addView(tripText, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 18; bottomMargin = 20; }});
        row.addView(right, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        row.setPadding(10, 10, 24, 10);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void buildQuote(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(90, 90, 90, 50);
        qTitle = Ui.text(c, 30, Ui.medium(c), th.sand);
        qTitle.setGravity(Gravity.CENTER);
        qText = Ui.text(c, 46, Ui.medium(c), th.fg);
        qText.setSingleLine(false);
        qText.setGravity(Gravity.CENTER);
        qText.setLineSpacing(0, 1.5f);
        qSrc = Ui.text(c, 24, Ui.regular(c), th.muted);
        qSrc.setGravity(Gravity.CENTER);
        col.addView(qTitle);
        col.addView(qText, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 28; }});
        col.addView(qSrc, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 28; }});
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private static final String[] DIRS = {"شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب"};

    /** Four times a second from the service's GPS: the needle and the rose move smoothly toward the new values. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            PanelService s = PanelService.live;
            if (s != null && gauge != null) {
                float[] d = s.driveNow();   // {km/h or -1, bearing or -1, qibla}
                gauge.target = Math.max(0, d[0]);
                gauge.known = d[0] >= 0;
                gauge.invalidate();
                if (d[1] >= 0) { rose.bearing = d[1]; set(headingText, DIRS[Math.round(d[1] / 45f) % 8]); }
                else if (rose.bearing < 0) set(headingText, "الاتجاه أثناء السير");
                String t = s.tripNow();
                set(tripText, t.isEmpty() ? "الرحلة تبدأ مع السير" : t);
                rose.qibla = d[2];
                rose.invalidate();
                if (rose.bearing < 0) set(qiblaText, "القبلة " + Ui.ar(Math.round(d[2])) + "° من الشمال");
                else {
                    int rel = Math.round(((d[2] - rose.bearing) % 360 + 360) % 360);
                    set(qiblaText, rel <= 15 || rel >= 345 ? "القبلة أمام السيارة" : rel >= 165 && rel <= 195 ? "القبلة خلف السيارة"
                            : rel < 180 ? "القبلة يمين السيارة بـ" + Ui.ar(rel) + "°" : "القبلة يسار السيارة بـ" + Ui.ar(360 - rel) + "°");
                }
            }
            if (s != null && qText != null) {
                String[] q = s.quoteNow(quoteOffset);
                set(qTitle, q[0]); set(qText, q[1]); set(qSrc, q[2]);
                qText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, q[1].length() > 220 ? 34 : q[1].length() > 120 ? 40 : 48);
            }
            h.postDelayed(this, 250);
        }
    };
    private static void set(TextView t, String s) { if (!s.contentEquals(t.getText())) t.setText(s); }

    @Override protected void onDetachedFromWindow() { h.removeCallbacksAndMessages(null); super.onDetachedFromWindow(); }

    // ---------------------------------------------------------------- the speedometer
    /** 0–220 km/h on a 240° arc: graduated ring, a gold sweep up to the speed, the needle, and the number in the middle. */
    final class Gauge extends View {
        float target, shown;
        boolean known;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), tick = new Paint(Paint.ANTI_ALIAS_FLAG), sweep = new Paint(Paint.ANTI_ALIAS_FLAG),
                needle = new Paint(Paint.ANTI_ALIAS_FLAG), num = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG);
        Gauge(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE); ring.setColor(th.line);
            tick.setColor(th.muted); tick.setStrokeCap(Paint.Cap.ROUND);
            sweep.setStyle(Paint.Style.STROKE); sweep.setStrokeCap(Paint.Cap.ROUND);
            needle.setColor(th.fg);
            num.setTextAlign(Paint.Align.CENTER); num.setTypeface(Ui.display(c)); num.setColor(th.fg);
            small.setTextAlign(Paint.Align.CENTER); small.setTypeface(Ui.regular(c)); small.setColor(th.muted);
        }
        @Override protected void onDraw(Canvas cv) {
            shown += (target - shown) * .25f;   // eased toward the new speed
            if (Math.abs(target - shown) > .3f) postInvalidateDelayed(40);
            // the dial reaches from cy - r (its top) to cy + .65r (the unit under the number): as big as the view allows
            float w = getWidth(), hgt = getHeight(), cx = w / 2, r = Math.min(w * .47f, hgt * .96f / 1.65f), cy = (hgt - 1.65f * r) / 2 + r;
            RectF box = new RectF(cx - r, cy - r, cx + r, cy + r);
            ring.setStrokeWidth(r * .07f);
            cv.drawArc(box, 150, 240, false, ring);
            sweep.setStrokeWidth(r * .07f);
            sweep.setShader(new LinearGradient(cx - r, 0, cx + r, 0, th.sand, shown > 140 ? 0xffe9a24b : th.sand, Shader.TileMode.CLAMP));
            cv.drawArc(box, 150, 240 * Math.min(1, shown / 220f), false, sweep);
            for (int v = 0; v <= 220; v += 10) {   // every 10, labels every 20
                double a = Math.toRadians(150 + 240 * v / 220.0);
                boolean major = v % 20 == 0;
                tick.setStrokeWidth(major ? r * .022f : r * .012f);
                float r1 = r * (major ? .80f : .85f), r2 = r * .9f;
                cv.drawLine(cx + (float) (r1 * Math.cos(a)), cy + (float) (r1 * Math.sin(a)), cx + (float) (r2 * Math.cos(a)), cy + (float) (r2 * Math.sin(a)), tick);
                if (major) {
                    small.setTextSize(r * .085f);
                    float rl = r * .68f;
                    cv.drawText(Ui.ar(v), cx + (float) (rl * Math.cos(a)), cy + (float) (rl * Math.sin(a)) + r * .03f, small);
                }
            }
            double a = Math.toRadians(150 + 240 * Math.min(1, shown / 220f));
            Path p = new Path();
            float tipX = cx + (float) (r * .78f * Math.cos(a)), tipY = cy + (float) (r * .78f * Math.sin(a));
            float bx = (float) (r * .035f * Math.cos(a + Math.PI / 2)), by = (float) (r * .035f * Math.sin(a + Math.PI / 2));
            p.moveTo(tipX, tipY); p.lineTo(cx + bx, cy + by); p.lineTo(cx - bx, cy - by); p.close();
            needle.setColor(th.sand);
            cv.drawPath(p, needle);
            needle.setColor(th.panel2);
            cv.drawCircle(cx, cy, r * .2f, needle);
            num.setTextSize(r * .3f);
            cv.drawText(known ? Ui.ar(Math.round(target)) : "—", cx, cy + r * .45f, num);
            small.setTextSize(r * .08f);
            cv.drawText("كم/س", cx, cy + r * .6f, small);
        }
    }

    // ---------------------------------------------------------------- the compass
    /** The rose turns with the car (its arrow up), the eight points in Arabic, the qibla as a gold Kaaba mark. */
    final class Rose extends View {
        float bearing = -1, qibla = -1, shown;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG),
                car = new Paint(Paint.ANTI_ALIAS_FLAG), kaaba = new Paint(Paint.ANTI_ALIAS_FLAG);
        Rose(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE); ring.setColor(th.line);
            mark.setColor(th.muted); mark.setStrokeCap(Paint.Cap.ROUND);
            label.setTextAlign(Paint.Align.CENTER); label.setTypeface(Ui.medium(c)); label.setColor(th.fg);
            car.setColor(th.fg); kaaba.setColor(th.sand);
        }
        @Override protected void onDraw(Canvas cv) {
            float target = bearing < 0 ? 0 : bearing;
            float diff = ((target - shown) % 360 + 540) % 360 - 180;   // the short way round
            shown = (shown + diff * .2f + 360) % 360;
            if (Math.abs(diff) > .5f) postInvalidateDelayed(40);
            float w = getWidth(), hgt = getHeight(), cx = w / 2, cy = hgt / 2, r = Math.min(w, hgt) * .44f;
            ring.setStrokeWidth(r * .05f);
            cv.drawCircle(cx, cy, r, ring);
            cv.save();
            cv.rotate(-shown, cx, cy);
            for (int i = 0; i < 72; i++) {   // every 5°
                cv.save();
                cv.rotate(i * 5, cx, cy);
                boolean major = i % 9 == 0;
                mark.setStrokeWidth(major ? r * .025f : r * .01f);
                cv.drawLine(cx, cy - r * .97f, cx, cy - r * (major ? .84f : .91f), mark);
                cv.restore();
            }
            String[] pts = {"ش", "ق", "ج", "غ"};   // north, east, south, west
            for (int i = 0; i < 4; i++) {
                cv.save();
                cv.rotate(i * 90, cx, cy);
                label.setTextSize(r * .17f);
                label.setColor(i == 0 ? 0xffe0533d : th.fg);
                cv.drawText(pts[i], cx, cy - r * .62f, label);
                cv.restore();
            }
            if (qibla >= 0) {
                cv.save();
                cv.rotate(qibla, cx, cy);
                cv.drawRect(cx - r * .07f, cy - r * 1.08f, cx + r * .07f, cy - r * .94f, kaaba);
                kaaba.setStrokeWidth(r * .02f);
                cv.drawLine(cx, cy - r * .9f, cx, cy - r * .4f, kaaba);
                cv.restore();
            }
            cv.restore();
            Path p = new Path();
            p.moveTo(cx, cy - r * .32f); p.lineTo(cx + r * .2f, cy + r * .26f); p.lineTo(cx, cy + r * .13f); p.lineTo(cx - r * .2f, cy + r * .26f); p.close();
            car.setAlpha(bearing < 0 ? 110 : 255);
            cv.drawPath(p, car);
        }
    }
}
