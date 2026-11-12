package sa.urcar.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/** Colours, fonts (IBM Plex Sans Arabic, OFL), Arabic digits and the drawn icons. */
final class Ui {
    private Ui() {}

    /** The weather's icon for a WMO code (Open-Meteo), the moon for a clear night. */
    static int wxIcon(int code, boolean night) {
        if (code <= 2) return night ? Icon.MOON : Icon.SUN;
        if (code == 45 || code == 48) return Icon.FOG;
        if (code >= 95) return Icon.STORM;
        if (code >= 51) return Icon.RAIN;
        return Icon.CLOUD;
    }

    static final class Theme {
        final int bg, panel, panel2, line, fg, muted, sand, sandInk, go;
        Theme(boolean day) {
            if (day) {
                // white with a cool cast: car screens render warm, and the owner saw the old grey-green as light brown
                bg = 0xffeef2f6; panel = 0xffffffff; panel2 = 0xfff6f8fb; line = 0xffd3dbe3;
                fg = 0xff122022; muted = 0xff5b6b67; sand = 0xff9a6d1c; sandInk = 0xfffffaf0; go = 0xff1d8a62;
            } else {
                bg = 0xff0b1314; panel = 0xff121d1f; panel2 = 0xff18272a; line = 0xff24383b;
                fg = 0xffeef1ea; muted = 0xff93a39f; sand = 0xffd9b46a; sandInk = 0xff1b1408; go = 0xff58c49a;
            }
        }
    }

    private static Typeface regular, medium, display;
    static Typeface regular(Context c) { if (regular == null) regular = load(c, "IBMPlexSansArabic-Regular.otf"); return regular; }
    static Typeface medium(Context c) { if (medium == null) medium = load(c, "IBMPlexSansArabic-Medium.otf"); return medium; }
    static Typeface display(Context c) { if (display == null) display = load(c, "IBMPlexSansArabic-Bold.otf"); return display; }
    private static Typeface load(Context c, String f) {
        try { return Typeface.createFromAsset(c.getAssets(), "fonts/" + f); } catch (Exception e) { return Typeface.DEFAULT; }
    }

    private static final char[] AR = {'٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'};
    static String ar(Object o) {
        String s = String.valueOf(o);
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            if (ch >= '0' && ch <= '9') b.append(AR[ch - '0']);
            else if (ch == '.') b.append('٫');
            else b.append(ch);
        }
        return b.toString();
    }
    static String pad(int n) { return n < 10 ? "0" + n : String.valueOf(n); }

    /** «٤:٥٨ ص» */
    static String clock12(int h, int m) {
        h %= 24;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return ar(h12 + ":" + pad(m)) + (h < 12 ? " ص" : " م");
    }

    /** Arabic counting: «دقيقتين»، «٥ دقائق»، «١١ دقيقة» */
    static String qty(int n, String one, String two, String few) {
        if (n == 1) return one;
        if (n == 2) return two;
        if (n >= 3 && n <= 10) return ar(n) + " " + few;
        return ar(n) + " " + one;
    }
    static String minutes(int n) { return qty(n, "دقيقة", "دقيقتين", "دقائق"); }
    static String hours(int n) { return qty(n, "ساعة", "ساعتين", "ساعات"); }
    /** «ساعتين و ١٢ دقيقة» from seconds */
    static String remaining(long sec) {
        int h = (int) (sec / 3600), m = (int) ((sec % 3600) / 60);
        if (h > 0 && m > 0) return hours(h) + " و " + minutes(m);
        if (h > 0) return hours(h);
        return minutes(Math.max(1, m));
    }

    static TextView text(Context c, float px, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    static GradientDrawable box(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (stroke != 0) g.setStroke(1, stroke);
        return g;
    }

    /** The icons of the panel, drawn on a 24-unit grid like the preview's SVGs. */
    static final class Icon extends View {
        static final int PLAY = 1, PAUSE = 2, PREV = 3, NEXT = 4, YOUTUBE = 5, BT = 6, GEAR = 7, APPS = 8, RADIO = 9,
                PIN = 10, SUN = 11, MOON = 12, CLOUD = 13, PHONES = 14, HOUSE = 15, BRIEFCASE = 16, MOSQUE = 17, FUEL = 18, DOWNLOAD = 19, TRASH = 20, DONE = 21, RAIN = 22, STORM = 23, FOG = 24, NAVI = 25;
        int kind;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context c, int kind, int color) {
            super(c);
            this.kind = kind;
            p.setColor(color);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        void set(int kind, int color) { this.kind = kind; p.setColor(color); invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) / 24f;
            c.save();
            c.translate((getWidth() - 24 * s) / 2, (getHeight() - 24 * s) / 2);
            c.scale(s, s);
            p.setStrokeWidth(1.8f);
            Path path = new Path();
            switch (kind) {
                case PLAY: fill(); path.moveTo(7, 4); path.lineTo(20, 12); path.lineTo(7, 20); path.close(); c.drawPath(path, p); break;
                case PAUSE: fill(); c.drawRect(6, 4, 10, 20, p); c.drawRect(14, 4, 18, 20, p); break;
                case PREV: fill(); c.drawRect(6, 5, 8, 19, p); path.moveTo(20, 5); path.lineTo(20, 19); path.lineTo(9, 12); path.close(); c.drawPath(path, p); break;
                case NEXT: fill(); c.drawRect(16, 5, 18, 19, p); path.moveTo(4, 5); path.lineTo(4, 19); path.lineTo(15, 12); path.close(); c.drawPath(path, p); break;
                case YOUTUBE: stroke(); c.drawRoundRect(new RectF(2.5f, 5, 21.5f, 19), 4, 4, p);
                    fill(); path.moveTo(10, 9); path.lineTo(15, 12); path.lineTo(10, 15); path.close(); c.drawPath(path, p); break;
                case BT: stroke(); path.moveTo(7, 7); path.lineTo(17, 17); path.lineTo(12, 21); path.lineTo(12, 3); path.lineTo(17, 7); path.lineTo(7, 17); c.drawPath(path, p); break;
                case GEAR: stroke(); c.drawCircle(12, 12, 3, p); c.drawCircle(12, 12, 7, p);
                    for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4;
                        c.drawLine((float) (12 + 7 * Math.cos(a)), (float) (12 + 7 * Math.sin(a)), (float) (12 + 9.5 * Math.cos(a)), (float) (12 + 9.5 * Math.sin(a)), p); }
                    break;
                case APPS: fill(); float r = 1.5f;
                    c.drawRoundRect(new RectF(4, 4, 10, 10), r, r, p); c.drawRoundRect(new RectF(14, 4, 20, 10), r, r, p);
                    c.drawRoundRect(new RectF(4, 14, 10, 20), r, r, p); c.drawRoundRect(new RectF(14, 14, 20, 20), r, r, p); break;
                case RADIO: stroke(); c.drawRoundRect(new RectF(3, 8, 21, 20), 2, 2, p); c.drawLine(7, 8, 17, 3, p); c.drawCircle(15.5f, 14, 2.5f, p); break;
                case PIN: stroke(); path.moveTo(12, 21); path.cubicTo(12, 21, 19, 14.8f, 19, 9.5f); path.arcTo(new RectF(5, 2.5f, 19, 16.5f), 0, -180);
                    path.cubicTo(5, 14.8f, 12, 21, 12, 21); c.drawPath(path, p); c.drawCircle(12, 9.5f, 2.5f, p); break;
                case SUN: stroke(); c.drawCircle(12, 12, 4.2f, p);
                    for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4;
                        c.drawLine((float) (12 + 7 * Math.cos(a)), (float) (12 + 7 * Math.sin(a)), (float) (12 + 9.5 * Math.cos(a)), (float) (12 + 9.5 * Math.sin(a)), p); }
                    break;
                case MOON: stroke(); path.addCircle(11, 12, 7.5f, Path.Direction.CW); c.drawPath(path, p); break;
                case CLOUD: stroke(); c.drawRoundRect(new RectF(3, 9, 21, 18.5f), 4.75f, 4.75f, p); c.drawCircle(10, 10, 4, p); break;
                case PHONES: stroke(); path.moveTo(4, 15); path.arcTo(new RectF(4, 4, 20, 20), 180, 180); c.drawPath(path, p);   // headphones
                    fill(); c.drawRoundRect(new RectF(2.5f, 13, 7.5f, 21), 1.8f, 1.8f, p); c.drawRoundRect(new RectF(16.5f, 13, 21.5f, 21), 1.8f, 1.8f, p); break;
                case HOUSE: stroke(); path.moveTo(2.5f, 11.5f); path.lineTo(12, 3.5f); path.lineTo(21.5f, 11.5f);   // the roof
                    path.moveTo(5, 9.5f); path.lineTo(5, 20.5f); path.lineTo(19, 20.5f); path.lineTo(19, 9.5f); c.drawPath(path, p);
                    c.drawRect(10, 14.5f, 14, 20.5f, p); break;
                case BRIEFCASE: stroke(); c.drawRoundRect(new RectF(3, 7.5f, 21, 20), 2.2f, 2.2f, p);
                    c.drawRoundRect(new RectF(9, 4, 15, 7.5f), 1.2f, 1.2f, p); c.drawLine(3, 13, 21, 13, p);
                    fill(); c.drawRect(10.8f, 11.8f, 13.2f, 14.4f, p); break;
                case MOSQUE: fill(); path.moveTo(5.5f, 13); path.arcTo(new RectF(5.5f, 7, 16.5f, 18), 180, 180); path.close(); c.drawPath(path, p);   // the dome
                    c.drawRect(4.5f, 13, 17.5f, 21, p);
                    c.drawRect(18.6f, 8.5f, 21.4f, 21, p);   // the minaret
                    path.reset(); path.moveTo(18.6f, 8.5f); path.lineTo(20, 5); path.lineTo(21.4f, 8.5f); path.close(); c.drawPath(path, p);
                    stroke(); c.drawLine(11, 7.2f, 11, 4.6f, p); fill(); c.drawCircle(11, 3.6f, 1.1f, p); break;   // the finial
                case FUEL: stroke(); c.drawRoundRect(new RectF(4, 3.5f, 14, 21), 1.6f, 1.6f, p); c.drawLine(2.5f, 21, 15.5f, 21, p);
                    fill(); c.drawRect(6.3f, 6, 11.7f, 10.5f, p);
                    stroke(); path.moveTo(14, 9); path.lineTo(17, 9); path.lineTo(19.5f, 11.5f); path.lineTo(19.5f, 17.5f);
                    path.arcTo(new RectF(16.5f, 16, 19.5f, 19), 0, 180); path.lineTo(16.5f, 13); path.lineTo(14, 13); c.drawPath(path, p); break;
                case DOWNLOAD: stroke(); c.drawLine(12, 3.5f, 12, 15, p); path.moveTo(7, 10.5f); path.lineTo(12, 15.5f); path.lineTo(17, 10.5f);
                    c.drawPath(path, p); c.drawLine(4.5f, 20, 19.5f, 20, p); break;
                case TRASH: stroke(); c.drawLine(4, 6.5f, 20, 6.5f, p); c.drawRect(9.5f, 3.5f, 14.5f, 6.5f, p);
                    path.moveTo(6, 6.5f); path.lineTo(7.2f, 20.5f); path.lineTo(16.8f, 20.5f); path.lineTo(18, 6.5f); c.drawPath(path, p);
                    c.drawLine(10.2f, 10, 10.2f, 17, p); c.drawLine(13.8f, 10, 13.8f, 17, p); break;
                case DONE: stroke(); path.moveTo(4.5f, 12.5f); path.lineTo(9.5f, 17.5f); path.lineTo(19.5f, 6.5f); c.drawPath(path, p); break;
                case RAIN: stroke(); c.drawRoundRect(new RectF(3, 5, 21, 14.5f), 4.75f, 4.75f, p);
                    c.drawLine(8, 17, 7, 21, p); c.drawLine(12.5f, 17, 11.5f, 21, p); c.drawLine(17, 17, 16, 21, p); break;
                case STORM: stroke(); c.drawRoundRect(new RectF(3, 4, 21, 13.5f), 4.75f, 4.75f, p);
                    path.moveTo(13, 14); path.lineTo(10, 18); path.lineTo(14, 18); path.lineTo(11, 22.5f); c.drawPath(path, p); break;
                case FOG: stroke(); c.drawLine(4, 8, 20, 8, p); c.drawLine(3, 12, 21, 12, p); c.drawLine(5, 16, 19, 16, p); break;
                case NAVI: fill(); path.moveTo(12, 2.5f); path.lineTo(20.5f, 21); path.lineTo(12, 16.6f); path.lineTo(3.5f, 21); path.close(); c.drawPath(path, p); break;   // the navigation arrow
                default: break;
            }
            c.restore();
        }
        private void fill() { p.setStyle(Paint.Style.FILL); }
        private void stroke() { p.setStyle(Paint.Style.STROKE); }
    }

    static int alpha(int color, float a) { return Color.argb((int) (255 * a), Color.red(color), Color.green(color), Color.blue(color)); }
}
