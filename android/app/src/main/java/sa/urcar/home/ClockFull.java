package sa.urcar.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * The clock card opened to the whole screen: a dial with hands in gold, its seconds hand sweeping, and beside it the
 * day, the Hijri date and the Gregorian one. A tap anywhere (or «رجوع») closes it.
 */
final class ClockFull extends FrameLayout {
    private final Dial dial;
    private final TextView day, hijri, greg, digital;
    private final Runnable tick = new Runnable() {
        @Override public void run() { fill(); postDelayed(this, 1000); }
    };

    ClockFull(Context c, boolean light, Runnable close) {
        super(c);
        Ui.Theme th = new Ui.Theme(light);
        setBackgroundColor(th.bg);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        setOnClickListener(v -> close.run());

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(60, 40, 60, 40);
        dial = new Dial(c, th);
        row.addView(dial, new LinearLayout.LayoutParams(620, 620));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER);
        day = Ui.text(c, 54, Ui.display(c), th.fg);
        digital = Ui.text(c, 34, Ui.medium(c), th.muted);
        digital.setTextDirection(TEXT_DIRECTION_LTR);
        View rule = new View(c);
        rule.setBackgroundColor(Ui.alpha(th.sand, .6f));
        hijri = Ui.text(c, 46, Ui.medium(c), th.sand);
        greg = Ui.text(c, 34, Ui.regular(c), th.muted);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.gravity = Gravity.CENTER_HORIZONTAL;
        text.addView(day, l);
        text.addView(digital, lp(10));
        text.addView(rule, new LinearLayout.LayoutParams(260, 2) {{ topMargin = 34; bottomMargin = 34; gravity = Gravity.CENTER_HORIZONTAL; }});
        text.addView(hijri, lp(0));
        text.addView(greg, lp(14));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        TextView back = Ui.text(c, 24, Ui.medium(c), th.fg);
        back.setText("رجوع");
        back.setPadding(28, 14, 28, 14);
        back.setBackground(Ui.box(th.panel, th.line, 14));
        back.setOnClickListener(v -> close.run());
        addView(back, new LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT) {{ leftMargin = 30; topMargin = 26; }});
        fill();
    }
    private static LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.topMargin = top;
        l.gravity = Gravity.CENTER_HORIZONTAL;
        return l;
    }

    private void fill() {
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        String g = Day.gregText(now);   // «الخميس، ٥ نوفمبر ٢٠٢٦م»
        int comma = g.indexOf('،');
        set(day, Day.weekday(now));
        set(greg, comma > 0 ? g.substring(comma + 1).trim() : g);
        set(hijri, Day.hijriText(now));
        int h = c.get(Calendar.HOUR_OF_DAY);
        set(digital, Ui.clock12(h, c.get(Calendar.MINUTE)).replace(" ", ":" + Ui.ar(Ui.pad(c.get(Calendar.SECOND))) + " "));
    }
    private static void set(TextView t, String s) { if (!s.contentEquals(t.getText())) t.setText(s); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(tick); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }

    /** The dial: a gold rim, the hour marks with Arabic numerals at 12, 3, 6 and 9, three hands, the seconds sweeping. */
    static final class Dial extends View {
        private final Ui.Theme th;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Dial(Context c, Ui.Theme th) {
            super(c);
            this.th = th;
            setLayerType(LAYER_TYPE_SOFTWARE, null);   // the hands' shadow
        }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - 8;
            int gold = th.sand, gold2 = Ui.alpha(th.sand, .55f);
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.clearShadowLayer();
            // the face: a soft radial light in the panel's colour, a double gold rim
            p.setShader(new RadialGradient(cx, cy - r * .2f, r * 1.1f, th.panel2, th.panel, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, p);
            p.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, new int[]{0xfff6e2a8, gold, 0xff8a6420, gold, 0xfff0d08a}, null, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * .045f);
            cv.drawCircle(cx, cy, r - r * .0225f, p);
            p.setShader(null);
            p.setColor(gold2);
            p.setStrokeWidth(2);
            cv.drawCircle(cx, cy, r * .9f, p);
            // the minute and hour marks
            for (int i = 0; i < 60; i++) {
                double a = Math.toRadians(i * 6);
                boolean hour = i % 5 == 0;
                if (hour && i % 15 == 0) continue;   // the numerals stand there
                float r1 = r * (hour ? .76f : .84f), r2 = r * .87f;
                p.setColor(hour ? gold : th.muted);
                p.setStrokeWidth(hour ? r * .022f : r * .006f);
                p.setStrokeCap(Paint.Cap.ROUND);
                cv.drawLine(cx + (float) Math.sin(a) * r1, cy - (float) Math.cos(a) * r1, cx + (float) Math.sin(a) * r2, cy - (float) Math.cos(a) * r2, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Ui.display(getContext()));
            p.setTextSize(r * .17f);
            p.setColor(gold);
            String[] nums = {"١٢", "٣", "٦", "٩"};
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(i * 90);
                float x = cx + (float) Math.sin(a) * r * .72f, y = cy - (float) Math.cos(a) * r * .72f;
                Paint.FontMetrics fm = p.getFontMetrics();
                cv.drawText(nums[i], x, y - (fm.ascent + fm.descent) / 2, p);
            }
            // «سيارتك» small under the centre
            p.setTypeface(Ui.medium(getContext()));
            p.setTextSize(r * .065f);
            p.setColor(th.muted);
            cv.drawText("سيارتك", cx, cy + r * .38f, p);

            Calendar c = Calendar.getInstance();
            long ms = System.currentTimeMillis();
            c.setTimeInMillis(ms);
            float sec = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND) / 1000f;
            float min = c.get(Calendar.MINUTE) + sec / 60f;
            float hr = c.get(Calendar.HOUR) + min / 60f;
            p.setShadowLayer(r * .03f, 0, r * .012f, 0x66000000);
            hand(cv, cx, cy, hr * 30, r * .5f, r * .12f, r * .045f, gold);
            hand(cv, cx, cy, min * 6, r * .74f, r * .14f, r * .03f, gold);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xffe0533d);
            p.setStrokeWidth(r * .012f);
            double a = Math.toRadians(sec * 6);
            cv.drawLine(cx - (float) Math.sin(a) * r * .16f, cy + (float) Math.cos(a) * r * .16f, cx + (float) Math.sin(a) * r * .82f, cy - (float) Math.cos(a) * r * .82f, p);
            p.setStyle(Paint.Style.FILL);
            p.clearShadowLayer();
            p.setColor(gold); cv.drawCircle(cx, cy, r * .045f, p);
            p.setColor(0xffe0533d); cv.drawCircle(cx, cy, r * .02f, p);
            postInvalidateOnAnimation();   // the seconds hand sweeps
        }
        /** A tapered hand: wide at the centre, a point at its end. */
        private void hand(Canvas cv, float cx, float cy, float deg, float len, float tail, float width, int color) {
            cv.save();
            cv.rotate(deg, cx, cy);
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(cx, cy - len);
            path.lineTo(cx + width / 2, cy - len * .15f);
            path.lineTo(cx + width / 2.6f, cy + tail);
            path.lineTo(cx - width / 2.6f, cy + tail);
            path.lineTo(cx - width / 2, cy - len * .15f);
            path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            cv.drawPath(path, p);
            cv.restore();
        }
    }
}
