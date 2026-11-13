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
    static HomeActivity live;
    /** The screen dark (the moon on the panel): the sound goes on; a tap anywhere lights it again. */
    static volatile boolean sleeping;
    private View dark;   // while the home screen is in front: the welcome to a city shows on it
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
        wake();
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

    /** «مرحبًا بك في الخبر»: a card that grows in over the middle, stays four seconds and fades away. */
    void welcome(String city) {
        if (sleeping) return;   // the dark screen stays dark
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
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .withEndAction(() -> card.animate().alpha(0f).translationY(-40).setStartDelay(4000).setDuration(900)
                        .withEndAction(() -> root.removeView(card)).start()).start();
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
        if (sleeping) { if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) wake(); return true; }   // the tap that wakes presses nothing
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

    // ---------------------------------------------------------------- the screen dark
    /**
     * Black over everything, the backlight as low as this window may set it (the screen's own setting is not
     * touched), the system bars hidden. The sound (the khatma, YouTube, …) goes on. A tap wakes it.
     */
    void sleep() {
        if (sleeping) return;
        sleeping = true;
        closeFull();
        dark = new View(this);
        dark.setBackgroundColor(0xff000000);
        root.addView(dark, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        android.view.WindowManager.LayoutParams w = getWindow().getAttributes();
        w.screenBrightness = 0.01f;
        getWindow().setAttributes(w);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    void wake() {
        if (!sleeping) return;
        sleeping = false;
        if (dark != null) { root.removeView(dark); dark = null; }
        android.view.WindowManager.LayoutParams w = getWindow().getAttributes();
        w.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(w);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
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

    @Override public void onBackPressed() { wake(); closeFull(); /* the home screen stays */ }
}
