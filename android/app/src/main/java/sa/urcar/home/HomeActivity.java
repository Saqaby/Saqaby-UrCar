package sa.urcar.home;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The home screen. Once the permissions are given it starts the chosen navigation app full screen, and the panel
 * (PanelService) floats beside it. Until then it shows what is missing, one big button each.
 */
public class HomeActivity extends Activity {
    static boolean resumed;
    private LinearLayout list;
    private long navStartedAt;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        list.setPadding(560, 40, 40, 40);   // the panel covers the left two fifths
        sv.addView(list);
        sv.setBackgroundColor(new Ui.Theme(false).bg);
        setContentView(sv);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        PanelService.start(this);
        if (PanelService.live != null) PanelService.live.startLocation();
        if (build()) startNav();
    }
    @Override protected void onPause() { resumed = false; super.onPause(); }

    /** The navigation app in front; at most once every 2 seconds, so a navigation app that fails cannot loop. */
    private void startNav() {
        String nav = Prefs.nav(this);
        if (nav.isEmpty() || System.currentTimeMillis() - navStartedAt < 2000) return;
        Intent i = getPackageManager().getLaunchIntentForPackage(nav);
        if (i == null) return;
        navStartedAt = System.currentTimeMillis();
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
    }

    /** The setup list. True when nothing required is missing. */
    private boolean build() {
        list.removeAllViews();
        Ui.Theme th = new Ui.Theme(false);
        TextView t = Ui.text(this, 34, Ui.medium(this), th.fg);
        t.setText("تجهيز سيارتك");
        list.addView(t);
        boolean overlay = Settings.canDrawOverlays(this);
        boolean location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean usage = ((AppOpsManager) getSystemService(APP_OPS_SERVICE)).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
        boolean noti = NotiListener.allowed(this);
        boolean install = Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
        boolean nav = !Prefs.nav(this).isEmpty();

        item(th, overlay, "الظهور فوق التطبيقات", "لتظهر اللوحة بجانب الخريطة. مطلوب.",
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        item(th, location, "الموقع", "للمواقيت والحرارة حسب مكان السيارة. مطلوب.",
                () -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1));
        item(th, usage, "الوصول إلى بيانات الاستخدام", "لتظهر اللوحة بجانب الخريطة وتختفي فوق يوتيوب والتطبيقات الأخرى. مطلوب.",
                () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        item(th, noti, "الوصول إلى الإشعارات", "لصفحة الموسيقى: ما يعمل الآن وأزرار التحكّم.",
                () -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        item(th, install, "تثبيت التحديثات", "لتحدّث سيارتك نفسها من GitHub.",
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))));
        item(th, nav, "تطبيق الملاحة", nav ? Prefs.navName(this, Prefs.nav(this)) : "لا يوجد تطبيق ملاحة مثبّت",
                () -> startActivity(new Intent(this, SettingsActivity.class)));
        TextView note = Ui.text(this, 20, Ui.regular(this), th.muted);
        note.setSingleLine(false);
        note.setText("بعد اكتمال المطلوب تفتح الخريطة وحدها، واللوحة بجانبها. للرجوع إلى واجهة الشاشة الأصلية: الإعدادات ← الواجهة الأصلية.");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = 24;
        list.addView(note, lp);
        return overlay && location && usage && nav;
    }

    private void item(Ui.Theme th, boolean done, String title, String sub, Runnable fix) {
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
        row.setGravity(Gravity.START);
        if (!done) row.setOnClickListener(v -> { try { fix.run(); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = 14;
        list.addView(row, lp);
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (PanelService.live != null) PanelService.live.startLocation();
        if (build()) startNav();
    }

    @Override public void onBackPressed() { /* the home screen stays */ }
}
