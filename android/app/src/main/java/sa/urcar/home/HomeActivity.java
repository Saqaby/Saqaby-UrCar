package sa.urcar.home;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The home screen, all ours (the owner's choice after probe v7: no map on it). The driver's side: the panel (clock,
 * dates, adhan bar, prayer/music card, Quran radio, four buttons). The other side: speed and heading from GPS, a
 * Radarbot alert, and the quote of the hour. PanelService fills both every second. Until the location permission
 * is given it shows the setup list instead.
 */
public class HomeActivity extends Activity {
    static boolean resumed;
    static HomeActivity live;   // while the home screen is in front: the welcome to a city shows on it
    private FrameLayout root;
    private String builtFor = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Crash.install(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(new Ui.Theme(false).bg);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        live = this;
        PanelService.front = "";
        PanelService.start(this);
        root.post(this::build);
    }
    @Override protected void onPause() {
        resumed = false;
        live = null;
        closeFull();
        if (PanelService.live != null) PanelService.live.attach(null, null);
        super.onPause();
    }

    private boolean located() { return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }

    private void build() {
        if (!resumed) return;
        if (PanelService.live == null) { root.postDelayed(this::build, 300); return; }
        if (!located() || !Prefs.of(this).getBoolean("setup_done", true)) { builtFor = ""; setup(); return; }
        if (PanelService.greetDue) root.post(() -> { if (resumed && PanelService.greetDue) greet(); });   // the screen just started: over what is built
        String wc = PanelService.live.takeWelcome();   // a city entered while another app was in front
        if (wc != null) root.postDelayed(() -> { if (resumed) welcome(wc); }, 600);
        String key = Prefs.side(this) + root.getWidth() + "x" + root.getHeight();
        if (key.equals(builtFor) && !PanelService.prefsChanged && root.getChildCount() > 0 && root.getChildAt(0) instanceof LinearLayout) {
            attach();
            return;
        }
        PanelService.prefsChanged = false;
        builtFor = key;
        root.removeAllViews();
        int w = root.getWidth(), h = root.getHeight();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        // the panel on the driver's side; LTR so that «left» means the screen's left
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        Panel panel = new Panel(this, PanelService.live, h);
        PanelService s = PanelService.live;
        Drive drive = new Drive(this, h, () -> showFull("gauge"), () -> showFull("quote"), () -> s.goPlace("home", "البيت"), () -> s.goPlace("work", "العمل"), s::mosque, s::station, s::navApp, s::places);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(w * 2 / 5, ViewGroup.LayoutParams.MATCH_PARENT);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        if (Prefs.side(this).equals("right")) { row.addView(drive, dl); row.addView(panel, pl); }
        else { row.addView(panel, pl); row.addView(drive, dl); }
        root.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (greeting != null && greeting.getParent() == null)   // the greeting came first: it stays over the screen just built
            root.addView(greeting, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        attach();
    }
    private void attach() {
        LinearLayout row = (LinearLayout) root.getChildAt(0);
        Panel p = null; Drive d = null;
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Panel) p = (Panel) v; else if (v instanceof Drive) d = (Drive) v;
        }
        PanelService.live.attach(p, d);
    }

    /** Only the location is required; the rest is shown with what it is for. */
    private void setup() {
        root.removeAllViews();
        Ui.Theme th = new Ui.Theme(false);
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        list.setPadding(80, 40, 80, 40);
        sv.addView(list);
        root.addView(sv);
        TextView t = Ui.text(this, 34, Ui.medium(this), th.fg);
        t.setText("أهلًا بك في سيارتك");
        list.addView(t);
        item(list, th, located(), "الموقع", "للمواقيت والسرعة والاتجاه والحرارة. مطلوب.",
                () -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1));
        item(list, th, NotiListener.allowed(this), "الوصول إلى الإشعارات", "لصفحة الموسيقى وتنبيهات Radarbot.",
                () -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        item(list, th, getPackageManager().canRequestPackageInstalls(), "تثبيت التحديثات", "لتحدّث سيارتك نفسها من GitHub.",
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))));
        // the first run: the home, the driver's side, the khatma's reciter; then «ابدأ»
        boolean home = Prefs.of(this).getFloat("home_lat", 999) < 90, fix = Prefs.of(this).getFloat("lat", 999) < 90;
        item(list, th, home, "موقع البيت", fix ? "لدعاء دخول المنزل وبوصلة البيت. إن كانت السيارة عند البيت الآن"
                        : "لدعاء دخول المنزل وبوصلة البيت. ينتظر تحديد الموقع، أو احفظه لاحقًا من الإعدادات.",
                () -> {
                    if (Prefs.of(this).getFloat("lat", 999) > 90) return;
                    Prefs.of(this).edit().putFloat("home_lat", Prefs.of(this).getFloat("lat", 999)).putFloat("home_lon", Prefs.of(this).getFloat("lon", 999)).apply();
                    setup();
                });
        boolean right = Prefs.side(this).equals("right");
        item(list, th, false, "جهة السائق: " + (right ? "اليمين" : "اليسار"), "اللوحة تكون في جهة السائق. للتبديل",
                () -> { Prefs.put(this, "side", right ? "left" : "right"); setup(); });
        String bt = Device.btApp(this), ra = Device.radioApp(this);
        item(list, th, false, "البلوتوث: " + Device.label(this, bt) + "، الراديو: " + Device.label(this, ra),
                "اختيرا تلقائيًا من تطبيقات شاشتك. إن كان أحدهما خطأ فغيّره من الإعدادات،",
                () -> startActivity(new Intent(this, SettingsActivity.class)));
        item(list, th, false, "قارئ الختمة والأذان والملاحة", "تُختار من الإعدادات، ولها اختيارات جاهزة. لفتحها",
                () -> startActivity(new Intent(this, SettingsActivity.class)));
        TextView go = Ui.text(this, 30, Ui.medium(this), located() ? th.sandInk : th.muted);
        go.setText(located() ? "ابدأ" : "ابدأ (بعد إذن الموقع)");
        go.setGravity(android.view.Gravity.CENTER);
        go.setPadding(24, 22, 24, 22);
        go.setBackground(Ui.box(located() ? th.sand : th.panel, 0, 18));
        go.setOnClickListener(v -> { if (!located()) return; Prefs.of(this).edit().putBoolean("setup_done", true).apply(); PanelService.prefsChanged = true; build(); });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
        gl.topMargin = 26;
        list.addView(go, gl);
    }

    private void item(LinearLayout list, Ui.Theme th, boolean done, String title, String sub, Runnable fix) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(24, 18, 24, 18);
        row.setBackground(Ui.box(th.panel, done ? th.go : th.sand, 16));
        TextView a = Ui.text(this, 26, Ui.medium(this), th.fg);
        a.setText((done ? "✓ " : "") + title);
        TextView s = Ui.text(this, 19, Ui.regular(this), th.muted);
        s.setSingleLine(false);
        s.setText(done ? "جاهز" : sub + "  اضغط هنا.");
        row.addView(a);
        row.addView(s);
        if (!done) row.setOnClickListener(v -> { try { fix.run(); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = 14;
        list.addView(row, lp);
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (PanelService.live != null) PanelService.live.startLocation();
        root.post(this::build);
    }

    /** A card opened to the whole screen; «رجوع», a tap or the back key closes it. */
    private Full full;
    private void showFull(String kind) {
        closeFull();
        PanelService s = PanelService.live;
        full = new Full(this, kind, s != null && s.dayNow(), this::closeFull, () -> { closeFull(); if (PanelService.live != null) PanelService.live.openNav(); });
        root.addView(full, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
    private void closeFull() { if (full != null) { root.removeView(full); full = null; } }

    /**
     * «مرحبًا بك» over the whole screen, in a large Ruqaa hand, when the screen starts: it comes in, stays about two
     * seconds and opens onto the home screen. A tap skips it. Off in the settings («الشاشة»).
     */
    private View greeting;
    void greet() {
        PanelService.greetDue = false;
        if (greeting != null || !Prefs.of(this).getBoolean("greet", true)) return;
        boolean day = PanelService.live != null && PanelService.live.dayNow();
        Ui.Theme th = new Ui.Theme(day);
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setGravity(android.view.Gravity.CENTER);
        g.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                day ? new int[]{0xfffbf8f1, 0xffefe7d6} : new int[]{0xff10201f, 0xff070d0e}));
        // the words in gold leaf: a gradient from pale to deep gold and back, a soft glow behind
        final int[] gold = day ? new int[]{0xffb8862b, 0xff7a5414, 0xffb8862b} : new int[]{0xfff6e2a8, 0xffc9973f, 0xfff0d08a};
        TextView t = new TextView(this) {
            @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
                super.onSizeChanged(w, h, ow, oh);
                getPaint().setShader(new android.graphics.LinearGradient(0, h * .2f, 0, h * .85f, gold, new float[]{0, .55f, 1}, android.graphics.Shader.TileMode.CLAMP));
            }
        };
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 200);
        t.setTypeface(Ui.tajawal(this));
        t.setTextColor(gold[1]);
        t.setText("مرحبًا بك");
        t.setGravity(android.view.Gravity.CENTER);
        t.setShadowLayer(28, 0, 6, day ? 0x33000000 : 0x55c9973f);
        t.setLayerType(View.LAYER_TYPE_SOFTWARE, null);   // the glow needs it
        // an ornament: two fine lines with a diamond between
        View orn = new View(this) {
            final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas c) {
                float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
                p.setColor(gold[1]); p.setStrokeWidth(2);
                c.drawLine(cx - 260, cy, cx - 26, cy, p); c.drawLine(cx + 26, cy, cx + 260, cy, p);
                android.graphics.Path d = new android.graphics.Path();
                d.moveTo(cx, cy - 11); d.lineTo(cx + 11, cy); d.lineTo(cx, cy + 11); d.lineTo(cx - 11, cy); d.close();
                c.drawPath(d, p);
                c.drawCircle(cx - 270, cy, 4, p); c.drawCircle(cx + 270, cy, 4, p);
            }
        };
        TextView s = Ui.text(this, 34, Ui.medium(this), th.muted);
        s.setText("في سيارتك");
        s.setGravity(android.view.Gravity.CENTER);
        g.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        g.addView(orn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 30));
        g.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{ topMargin = 14; }});
        g.setOnClickListener(v -> endGreet());
        greeting = g;
        root.addView(g, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        t.setAlpha(0f); t.setScaleX(.92f); t.setScaleY(.92f); t.setTranslationY(24);
        orn.setAlpha(0f); orn.setScaleX(.2f);
        s.setAlpha(0f);
        t.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setDuration(1100)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        orn.animate().alpha(1f).scaleX(1f).setStartDelay(500).setDuration(900).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        s.animate().alpha(1f).setStartDelay(900).setDuration(700).start();
        root.postDelayed(this::endGreet, 3400);
    }
    private void endGreet() {
        View g = greeting;
        if (g == null) return;
        greeting = null;
        g.setOnClickListener(null);
        g.animate().alpha(0f).setDuration(700).withEndAction(() -> root.removeView(g)).start();
    }

    /** «مرحبًا بك في الخبر»: a card that grows in over the middle, stays four seconds and fades away. */
    void welcome(String city) {
        Ui.Theme th = new Ui.Theme(PanelService.live != null && PanelService.live.dayNow());
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        card.setPadding(70, 34, 70, 40);
        card.setBackground(Ui.box(th.panel, th.sand, 28));
        card.setElevation(24);
        TextView a = Ui.text(this, 30, Ui.regular(this), th.muted);
        a.setText("مرحبًا بك في");
        TextView b = Ui.text(this, 76, Ui.medium(this), th.sand);
        b.setText(city);
        card.addView(a);
        card.addView(b);
        root.addView(card, new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER));
        card.setAlpha(0f); card.setScaleX(.85f); card.setScaleY(.85f);
        card.setOnClickListener(v -> root.removeView(card));   // a tap closes it at once
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
        // the fade as its own step, not from inside the first animation's end (a rebuilt screen or a second
        // welcome could leave a card standing)
        root.postDelayed(() -> card.animate().alpha(0f).translationY(-40).setDuration(900)
                .withEndAction(() -> { if (card.getParent() != null) root.removeView(card); }).start(), 4700);
    }

    // ---------------------------------------------------------------- two fingers: the sound, without looking
    private boolean two;
    private float gx, gy;
    private long gAt;
    /**
     * Two fingers anywhere on the home screen: a tap stops the sound or plays it again, a sweep to the right or the left
     * goes to the next or the previous track. From the second finger on, what is under the fingers is not pressed.
     */
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (!Smart.on(this, "gestures", true) || full != null) return super.dispatchTouchEvent(e);
        int a = e.getActionMasked();
        if (!two && a == android.view.MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() == 2) {
            two = true;
            gx = (e.getX(0) + e.getX(1)) / 2; gy = (e.getY(0) + e.getY(1)) / 2; gAt = e.getEventTime();
            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(e);
            cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }
        if (!two) return super.dispatchTouchEvent(e);
        if (a == android.view.MotionEvent.ACTION_POINTER_UP && e.getPointerCount() == 2 && gAt > 0) {
            float dx = (e.getX(0) + e.getX(1)) / 2 - gx, dy = (e.getY(0) + e.getY(1)) / 2 - gy;
            long ms = e.getEventTime() - gAt;
            gAt = 0;
            PanelService s = PanelService.live;
            if (s != null) {
                if (Math.abs(dx) > 140 && Math.abs(dx) > 2 * Math.abs(dy)) s.soundSkip(dx < 0);   // to the left: the next, as a page turns in Arabic
                else if (ms < 600 && Math.abs(dx) < 40 && Math.abs(dy) < 40) s.soundToggle();
            }
        }
        if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) two = false;
        return true;
    }

    // ---------------------------------------------------------------- the owner's places
    /** A place the car keeps stopping at: its name makes it a destination; «ليس وجهة» drops it for good. */
    void namePlace(int index) {
        Smart.edit(this, index, null, false, true);   // asked once, whatever the answer
        android.widget.EditText in = new android.widget.EditText(this);
        in.setHint("مثل: النادي، بيت الوالد");
        in.setTextSize(26);
        new android.app.AlertDialog.Builder(this).setTitle("اسم هذا المكان").setView(in)
                .setPositiveButton("حفظ", (d, w) -> { if (in.getText().toString().trim().length() > 0) Smart.edit(this, index, in.getText().toString(), false, false); })
                .setNeutralButton("لاحقًا", null)
                .setNegativeButton("ليس وجهة", (d, w) -> Smart.edit(this, index, null, true, false)).show();
    }
    /** «أماكني»: the list, the most visited first; one tap starts the way to it. */
    void pickPlace() {
        java.util.List<Object[]> ps = Smart.places(this);
        if (ps.isEmpty() || PanelService.live == null) return;
        String[] names = new String[ps.size()];
        for (int i = 0; i < names.length; i++) names[i] = Smart.placeLabel(ps.get(i)) + "  (" + Ui.ar(ps.get(i)[2]) + " زيارة)";
        new android.app.AlertDialog.Builder(this).setTitle("أماكني").setItems(names, (d, i) -> {
            Object[] p = ps.get(i);
            PanelService.live.goTo((Double) p[3], (Double) p[4], Smart.placeLabel(p));
        }).setNegativeButton("إغلاق", null).show();
    }

    @Override public void onBackPressed() { closeFull(); /* the home screen stays */ }
}
