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
        if (kind.equals("gauge")) {
            // two symbols at the top: the map in the middle (the navigation app), the bent arrow beside it (back)
            int size = 86, gap = 20;
            Icon map = new Icon(c, true, openNav), back = new Icon(c, false, close);
            LayoutParams ml = new LayoutParams(size, size, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            ml.topMargin = 16;
            addView(map, ml);
            LayoutParams kl = new LayoutParams(size, size, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            kl.topMargin = 16;
            back.setTranslationX(size + gap);   // on the map's right
            addView(back, kl);
        } else {
            LinearLayout bar = new LinearLayout(c);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.addView(button(c, "رجوع", close));
            bar.addView(button(c, "التالي", () -> { quoteOffset++; tick.run(); }));
            LayoutParams bl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            bl.setMargins(24, 20, 24, 0);
            addView(bar, bl);
        }
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
        right.setPadding(0, 0, 0, 0);
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
        row.setPadding(10, 110, 24, 10);   // the two symbols sit above, in the middle
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void buildQuote(Context c) {
        addView(new Ornament(c), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));   // a framed panel, as a painted plate
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(150, 110, 150, 90);
        qTitle = Ui.text(c, 30, Ui.medium(c), th.sand);
        qTitle.setGravity(Gravity.CENTER);
        qText = Ui.text(c, 46, Ui.medium(c), th.fg);
        qText.setSingleLine(false);
        qText.setGravity(Gravity.CENTER);
        qText.setLineSpacing(0, 1.5f);
        qSrc = Ui.text(c, 24, Ui.regular(c), th.muted);
        qSrc.setGravity(Gravity.CENTER);
        col.addView(qTitle);
        col.addView(new Divider(c), new LinearLayout.LayoutParams(-1, 44) {{ topMargin = 10; }});
        col.addView(qText, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 18; }});
        col.addView(new Divider(c), new LinearLayout.LayoutParams(-1, 44) {{ topMargin = 18; }});
        col.addView(qSrc, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 10; }});
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
                gauge.limit = Math.round(d[3]);
                gauge.invalidate();
                if (d[1] >= 0) { rose.bearing = d[1]; set(headingText, DIRS[Math.round(d[1] / 45f) % 8]); }
                else if (rose.bearing < 0) set(headingText, "الاتجاه أثناء السير");
                String t = PanelService.join(s.tripNow(), s.homeNow());
                rose.home = s.homeBearing();
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

    // ---------------------------------------------------------------- the quote's ornament
    /** An eight-pointed star (the khatam: two squares, one turned 45°), as a path of 16 points. */
    static Path star(float cx, float cy, float r) {
        Path p = new Path();
        for (int i = 0; i < 16; i++) {
            double a = Math.PI / 8 * i - Math.PI / 2;
            float rr = i % 2 == 0 ? r : r * .72f;
            float x = cx + (float) (rr * Math.cos(a)), y = cy + (float) (rr * Math.sin(a));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    /**
     * Behind the quote: a faint field of eight-pointed stars, a clear panel for the text, a double gold frame with a
     * rosette at each corner and a small star in the middle of each side but the top (where the buttons are).
     */
    final class Ornament extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Ornament(Context c) { super(c); }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), hgt = getHeight(), s = 120;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f);
            p.setColor(th.sand);
            p.setAlpha(46);
            for (float y = 0, row = 0; y < hgt + s; y += s * .5f, row++)   // staggered rows of stars and their links
                for (float x = (row % 2) * s / 2; x < w + s; x += s) {
                    cv.drawPath(star(x, y, s * .3f), p);
                    cv.drawLine(x + s * .3f, y, x + s * .7f, y, p);
                }
            float in = 34, in2 = 46;
            p.setStyle(Paint.Style.FILL);   // the clear panel
            p.setColor(th.bg);
            p.setAlpha(214);   // the stars show faintly through
            cv.drawRoundRect(new RectF(in2 + 30, in2 + 30, w - in2 - 30, hgt - in2 - 30), 26, 26, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(th.sand);
            p.setAlpha(255);
            p.setStrokeWidth(3.2f);
            cv.drawRoundRect(new RectF(in, in, w - in, hgt - in), 18, 18, p);
            p.setStrokeWidth(1.4f);
            p.setAlpha(170);
            cv.drawRoundRect(new RectF(in2, in2, w - in2, hgt - in2), 12, 12, p);
            float[][] corners = {{in, in}, {w - in, in}, {in, hgt - in}, {w - in, hgt - in}};
            for (float[] k : corners) rosette(cv, k[0], k[1], 26);
            for (float[] k : new float[][]{{w / 2, hgt - in}, {in, hgt / 2}, {w - in, hgt / 2}}) rosette(cv, k[0], k[1], 15);
        }
        private void rosette(Canvas cv, float x, float y, float r) {
            p.setStyle(Paint.Style.FILL); p.setColor(th.bg); p.setAlpha(255);
            cv.drawCircle(x, y, r * 1.1f, p);
            p.setColor(th.sand);
            cv.drawPath(star(x, y, r), p);
            p.setColor(th.bg);
            cv.drawCircle(x, y, r * .34f, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f); p.setColor(th.sand);
            cv.drawCircle(x, y, r * .2f, p);
        }
    }

    /** A line that thins out to both sides, a star in the middle and a dot each side of it. */
    final class Divider extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Divider(Context c) { super(c); }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), cy = getHeight() / 2f, cx = w / 2, r = getHeight() * .32f;
            p.setStrokeWidth(2);
            p.setShader(new LinearGradient(cx - w * .3f, 0, cx - r * 2.2f, 0, 0x00000000 | (th.sand & 0xffffff), th.sand, Shader.TileMode.CLAMP));
            cv.drawLine(cx - w * .3f, cy, cx - r * 2.2f, cy, p);
            p.setShader(new LinearGradient(cx + r * 2.2f, 0, cx + w * .3f, 0, th.sand, 0x00000000 | (th.sand & 0xffffff), Shader.TileMode.CLAMP));
            cv.drawLine(cx + r * 2.2f, cy, cx + w * .3f, cy, p);
            p.setShader(null);
            p.setColor(th.sand);
            p.setStyle(Paint.Style.FILL);
            cv.drawPath(star(cx, cy, r), p);
            cv.drawCircle(cx - r * 1.6f, cy, 3.5f, p);
            cv.drawCircle(cx + r * 1.6f, cy, 3.5f, p);
        }
    }

    // ---------------------------------------------------------------- the two symbols
    /** A round button with a drawn symbol (no font has them all): a folded map, or a bent arrow back. */
    final class Icon extends View {
        private final boolean map;
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context c, boolean map, Runnable r) {
            super(c);
            this.map = map;
            bg.setColor(th.panel);
            line.setStyle(Paint.Style.STROKE); line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(map ? th.sand : th.fg);
            setContentDescription(map ? "افتح الملاحة" : "رجوع");
            setClickable(true);
            setOnClickListener(v -> r.run());
        }
        @Override protected void onDraw(Canvas cv) {
            float s = Math.min(getWidth(), getHeight());
            bg.setStyle(Paint.Style.FILL); bg.setColor(th.panel);
            cv.drawCircle(s / 2, s / 2, s / 2 - 2, bg);
            bg.setStyle(Paint.Style.STROKE); bg.setStrokeWidth(2); bg.setColor(th.line);
            cv.drawCircle(s / 2, s / 2, s / 2 - 2, bg);
            line.setStrokeWidth(s * .055f);
            Path p = new Path();
            if (map) {   // three panels, folded
                p.moveTo(s * .24f, s * .34f); p.lineTo(s * .41f, s * .27f); p.lineTo(s * .59f, s * .34f); p.lineTo(s * .76f, s * .27f);
                p.lineTo(s * .76f, s * .66f); p.lineTo(s * .59f, s * .73f); p.lineTo(s * .41f, s * .66f); p.lineTo(s * .24f, s * .73f); p.close();
                p.moveTo(s * .41f, s * .27f); p.lineTo(s * .41f, s * .66f);
                p.moveTo(s * .59f, s * .34f); p.lineTo(s * .59f, s * .73f);
            } else {     // bent back the Arabic way: the head points right, the bend on the left
                p.moveTo(s * .66f, s * .70f); p.lineTo(s * .44f, s * .70f);
                p.arcTo(new RectF(s * .26f, s * .38f, s * .58f, s * .70f), 90, 180);
                p.lineTo(s * .74f, s * .38f);
                p.moveTo(s * .63f, s * .28f); p.lineTo(s * .74f, s * .38f); p.lineTo(s * .63f, s * .48f);
            }
            cv.drawPath(p, line);
        }
    }

    // ---------------------------------------------------------------- the speedometer
    /** 0–220 km/h on a 240° arc: graduated ring, a gold sweep up to the speed, the needle, and the number in the middle. */
    final class Gauge extends View {
        float target, shown;
        boolean known;
        int limit;   // the road's, 0 when unknown
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), tick = new Paint(Paint.ANTI_ALIAS_FLAG), sweep = new Paint(Paint.ANTI_ALIAS_FLAG),
                needle = new Paint(Paint.ANTI_ALIAS_FLAG), num = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG),
                signPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Gauge(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE); ring.setColor(th.line);
            tick.setColor(th.muted); tick.setStrokeCap(Paint.Cap.ROUND);
            sweep.setStyle(Paint.Style.STROKE); sweep.setStrokeCap(Paint.Cap.ROUND);
            needle.setColor(th.fg);
            num.setTextAlign(Paint.Align.CENTER); num.setTypeface(Ui.display(c)); num.setColor(th.fg);
            small.setTextAlign(Paint.Align.CENTER); small.setTypeface(Ui.regular(c)); small.setColor(th.muted);
            signPaint.setTextAlign(Paint.Align.CENTER); signPaint.setTypeface(Ui.display(c));
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
            if (limit > 0) {   // the road's limit: a red mark on the dial, and its sign inside
                double la = Math.toRadians(150 + 240 * Math.min(1, limit / 220f));
                tick.setColor(0xffd7261e); tick.setStrokeWidth(r * .04f);
                cv.drawLine(cx + (float) (r * .76f * Math.cos(la)), cy + (float) (r * .76f * Math.sin(la)), cx + (float) (r * 1.04f * Math.cos(la)), cy + (float) (r * 1.04f * Math.sin(la)), tick);
                tick.setColor(th.muted);
                Drive.Sign.draw(cv, signPaint, cx, cy - r * .42f, r * .15f, limit);
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
            num.setColor(limit > 0 && target > limit ? 0xffe0533d : th.fg);
            num.setTextSize(r * .3f);
            cv.drawText(known ? Ui.ar(Math.round(target)) : "—", cx, cy + r * .45f, num);
            small.setTextSize(r * .08f);
            cv.drawText("كم/س", cx, cy + r * .6f, small);
        }
    }

    // ---------------------------------------------------------------- the compass
    /** The rose turns with the car (its arrow up), the eight points in Arabic, the qibla as a gold Kaaba mark. */
    final class Rose extends View {
        float bearing = -1, qibla = -1, home = -1, shown;
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
            if (home >= 0) { cv.save(); cv.rotate(home, cx, cy); Drive.house(cv, cx, cy - r * 1.0f, r * .09f, 0xff4fbf7a); cv.restore(); }
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
