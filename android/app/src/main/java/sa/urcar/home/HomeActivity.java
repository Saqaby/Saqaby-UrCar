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
    private FrameLayout root;
    private String builtFor = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(new Ui.Theme(false).bg);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        PanelService.start(this);
        root.post(this::build);
    }
    @Override protected void onPause() {
        resumed = false;
        closeFull();
        if (PanelService.live != null) PanelService.live.attach(null, null);
        super.onPause();
    }

    private boolean located() { return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }

    private void build() {
        if (!resumed) return;
        if (PanelService.live == null) { root.postDelayed(this::build, 300); return; }
        if (!located()) { builtFor = ""; setup(); return; }
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
        Drive drive = new Drive(this, h, () -> showFull("gauge"), () -> showFull("quote"), () -> s.goPlace("home", "البيت"), () -> s.goPlace("work", "العمل"), s::mosque);
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
        t.setText("تجهيز سيارتك");
        list.addView(t);
        item(list, th, located(), "الموقع", "للمواقيت والسرعة والاتجاه والحرارة. مطلوب.",
                () -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1));
        item(list, th, NotiListener.allowed(this), "الوصول إلى الإشعارات", "لصفحة الموسيقى وتنبيهات Radarbot.",
                () -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        item(list, th, getPackageManager().canRequestPackageInstalls(), "تثبيت التحديثات", "لتحدّث سيارتك نفسها من GitHub.",
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))));
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

    @Override public void onBackPressed() { closeFull(); /* the home screen stays */ }
}
