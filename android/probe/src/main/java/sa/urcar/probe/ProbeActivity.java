package sa.urcar.probe;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.SharedPreferences;
import android.os.Handler;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.service.notification.StatusBarNotification;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * «فحص سيارتك»: reads what the UrCar launcher needs to know about this head unit and runs the tests the owner watches.
 * Read-only: it starts other apps' screens when a test button is pressed, and sends no broadcast to any app.
 * No identifiers (serial, IMEI, MAC) are read or written.
 */
public class ProbeActivity extends Activity {
    private static final String[] NAV = {
            "com.google.android.apps.maps", "com.waze", "com.here.app.maps", "com.sygic.aura",
            "net.osmand", "com.huawei.maps.app", "ru.yandex.yandexnavi"};
    private TextView out;
    private String report = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(11, 19, 20));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(16, 16, 16, 16);
        side.addView(button("الخريطة تحت لوحة عائمة", new View.OnClickListener() { public void onClick(View v) { testOverlay(); } }));
        side.addView(button("فتح كاميرا الشاشة", new View.OnClickListener() { public void onClick(View v) { pickAndOpen("camera|avm|reverse|backcar|360|dvr|rightview|com\\.tw\\.service|carinfoservice"); } }));
        side.addView(button("الكاميرا داخل التطبيق", new View.OnClickListener() { public void onClick(View v) { testAndroidCamera(); } }));
        side.addView(button("راقب ما يُفتح (٦٠ ثانية)", new View.OnClickListener() { public void onClick(View v) { watchForeground(); } }));
        side.addView(button("الإشعارات والموسيقى", new View.OnClickListener() { public void onClick(View v) { testNotifications(); } }));
        side.addView(button("فتح راديو الشاشة", new View.OnClickListener() { public void onClick(View v) { pickAndOpen("radio|fm|tuner"); } }));
        side.addView(button("حفظ التقرير", new View.OnClickListener() { public void onClick(View v) { save(); } }));
        root.addView(side, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        ScrollView sv = new ScrollView(this);
        out = new TextView(this);
        out.setTextColor(Color.rgb(238, 241, 234));
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(13);
        out.setPadding(16, 16, 16, 16);
        out.setGravity(Gravity.LEFT);
        out.setTextDirection(View.TEXT_DIRECTION_LTR);
        sv.addView(out);
        root.addView(sv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3));
        setContentView(root);

        report = "probe v5\n" + collect() + embedInfo() + "\n[earlier tests]\n" + prefs().getString("tests", "");
        out.setText(report);
    }

    private Button button(String text, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(18);
        btn.setAllCaps(false);
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        lp.setMargins(0, 8, 0, 8);
        btn.setLayoutParams(lp);
        return btn;
    }

    // ---------------------------------------------------------------- the report
    private String collect() {
        StringBuilder s = new StringBuilder();
        PackageManager pm = getPackageManager();
        s.append("UrCar probe ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date())).append("\n\n");

        s.append("[android]\n");
        s.append("sdk_int=").append(Build.VERSION.SDK_INT).append("  (29=Android 10, 30=11, 31=12, 33=13)\n");
        s.append("release_reported=").append(Build.VERSION.RELEASE).append("\n");
        s.append("manufacturer=").append(Build.MANUFACTURER).append("  model=").append(Build.MODEL).append("\n");
        s.append("board=").append(Build.BOARD).append("  hardware=").append(Build.HARDWARE).append("  device=").append(Build.DEVICE).append("\n");
        if (Build.VERSION.SDK_INT >= 23) s.append("security_patch=").append(Build.VERSION.SECURITY_PATCH).append("\n");

        s.append("\n[screen]\n");
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        s.append("pixels=").append(m.widthPixels).append("x").append(m.heightPixels)
                .append("  dpi=").append(m.densityDpi).append("  density=").append(m.density).append("\n");

        s.append("\n[memory]\n");
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        s.append("ram_mb=").append(mi.totalMem / (1024 * 1024)).append("  free_mb=").append(mi.availMem / (1024 * 1024))
                .append("  low_ram=").append(am.isLowRamDevice()).append("\n");

        s.append("\n[windows]\n");
        s.append("freeform_feature=").append(pm.hasSystemFeature("android.software.freeform_window_management")).append("\n");
        s.append("picture_in_picture=").append(pm.hasSystemFeature("android.software.picture_in_picture")).append("\n");
        s.append("enable_freeform_support=").append(global("enable_freeform_support")).append("\n");
        s.append("force_resizable_activities=").append(global("force_resizable_activities")).append("\n");
        if (Build.VERSION.SDK_INT >= 24) s.append("in_multiwindow_now=").append(isInMultiWindowMode()).append("\n");

        s.append("\n[camera]\n");
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String[] ids = cm.getCameraIdList();
            s.append("android_cameras=").append(ids.length).append("\n");
            for (String id : ids) {
                Integer f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                s.append("  id=").append(id).append(" facing=").append(f == null ? "?" : f == 0 ? "front" : f == 1 ? "back" : "external").append("\n");
            }
        } catch (Exception e) { s.append("android_cameras=error ").append(e.getClass().getSimpleName()).append("\n"); }
        File[] dev = new File("/dev").listFiles();
        int video = 0;
        if (dev != null) for (File f : dev) if (f.getName().startsWith("video")) video++;
        s.append("dev_video=").append(video).append("\n");

        s.append("\n[home]\n");
        ResolveInfo home = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        s.append("current_home=").append(home == null ? "none" : home.activityInfo.packageName + "/" + home.activityInfo.name).append("\n");

        s.append("\n[navigation apps]\n");
        for (String p : NAV) s.append(p).append("=").append(installed(p)).append("\n");
        s.append("google_play=").append(installed("com.android.vending")).append("  youtube=").append(installed("com.google.android.youtube")).append("\n");

        // the screen's own apps: every package of com.tw.* or that sounds like radio, camera, car or canbus
        s.append("\n[the screen's own apps]\n");
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String> names = new ArrayList<>();
        for (ApplicationInfo a : apps) names.add(a.packageName);
        Collections.sort(names);
        for (String p : names) {
            if (!p.matches("(?i)^com\\.tw\\..*|.*(radio|camera|avm|canbus|carinfo|tuner|reverse|backcar|launcher|bt|bluetooth|music|media).*")) continue;
            if (p.startsWith("com.android.") && !p.contains("launcher") && !p.contains("camera")) continue;
            s.append("== ").append(p).append("\n");
            Intent li = pm.getLaunchIntentForPackage(p);
            if (li != null && li.getComponent() != null) s.append("  launch: ").append(li.getComponent().flattenToShortString()).append("\n");
            try {
                PackageInfo pi = pm.getPackageInfo(p, PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES);
                if (pi.activities != null) for (ActivityInfo a : pi.activities) if (a.exported) s.append("  activity: ").append(shortName(p, a.name)).append("\n");
                if (pi.receivers != null) for (ActivityInfo a : pi.receivers) if (a.exported) s.append("  receiver: ").append(shortName(p, a.name)).append("\n");
                if (pi.services != null) for (ServiceInfo a : pi.services) if (a.exported) s.append("  service: ").append(shortName(p, a.name)).append("\n");
            } catch (Exception e) { s.append("  (").append(e.getClass().getSimpleName()).append(")\n"); }
        }

        s.append("\n[all packages] ").append(names.size()).append("\n");
        for (String p : names) s.append(p).append("\n");
        return s.toString();
    }

    private String global(String key) {
        try { String v = Settings.Global.getString(getContentResolver(), key); return v == null ? "unset" : v; }
        catch (Exception e) { return "error"; }
    }

    private boolean installed(String p) {
        try { getPackageManager().getPackageInfo(p, 0); return true; } catch (Exception e) { return false; }
    }

    private static String shortName(String pkg, String cls) {
        return cls.startsWith(pkg + ".") ? cls.substring(pkg.length()) : cls;
    }

    // ---------------------------------------------------------------- the tests
    /** Opens the first navigation app found in a window over the left three fifths of the screen. A window = freeform works. */
    private void testFreeform() {
        Intent nav = null;
        for (String p : NAV) { nav = getPackageManager().getLaunchIntentForPackage(p); if (nav != null) break; }
        if (nav == null) { toast("لا يوجد تطبيق خرائط مثبّت"); return; }
        nav.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        if (Build.VERSION.SDK_INT < 24) { startActivity(nav); toast("أندرويد أقدم من ٧: لا نوافذ"); return; }
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        Rect r = new Rect(0, m.heightPixels / 8, m.widthPixels * 3 / 5, m.heightPixels);
        try {
            startActivity(nav, ActivityOptions.makeBasic().setLaunchBounds(r).toBundle());
            toast("نافذة صغيرة؟ الخطة أ ممكنة. ملء الشاشة؟ غير ممكنة");
        } catch (Exception e) { toast("فشل: " + e.getClass().getSimpleName()); }
    }

    /** Lists the launchable screens of the apps whose package matches, and opens the one picked. */
    private void pickAndOpen(String pattern) {
        final List<ComponentName> found = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (ApplicationInfo a : pm.getInstalledApplications(0)) {
            if (!a.packageName.matches("(?i).*(" + pattern + ").*")) continue;
            try {
                PackageInfo pi = pm.getPackageInfo(a.packageName, PackageManager.GET_ACTIVITIES);
                if (pi.activities == null) continue;
                for (ActivityInfo ai : pi.activities) {
                    if (!ai.exported) continue;
                    found.add(new ComponentName(ai.packageName, ai.name));
                    labels.add(ai.packageName + shortName(ai.packageName, ai.name).replace(ai.packageName, ""));
                }
            } catch (Exception ignored) { }
        }
        if (found.isEmpty()) { toast("لم أجد تطبيقًا مناسبًا"); return; }
        new AlertDialog.Builder(this)
                .setTitle("اختر واحدة، ثم أخبرنا ماذا ظهر")
                .setItems(labels.toArray(new String[0]), (d, i) -> {
                    try {
                        startActivity(new Intent().setComponent(found.get(i)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        note("opened " + found.get(i).flattenToShortString());
                    } catch (Exception e) { toast("لم تُفتح: " + e.getClass().getSimpleName()); note("failed " + found.get(i).flattenToShortString() + " " + e.getClass().getSimpleName() + " " + e.getMessage()); }
                })
                .show();
    }

    // ---------------------------------------------------------------- saving
    private void save() {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File f = new File(dir, "urcar-probe.txt");
        try {
            dir.mkdirs();
            FileOutputStream o = new FileOutputStream(f);
            o.write(report.getBytes("UTF-8"));
            o.close();
            toast("حُفظ في " + f.getAbsolutePath());
        } catch (Exception e) {
            File g = new File(getExternalFilesDir(null), "urcar-probe.txt");
            try { FileOutputStream o = new FileOutputStream(g); o.write(report.getBytes("UTF-8")); o.close(); toast("حُفظ في " + g.getAbsolutePath()); }
            catch (Exception e2) { toast("لم يُحفظ: " + e2.getClass().getSimpleName()); }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        boolean ok = r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED;
        if (code == 2) { if (ok) testAndroidCamera(); else toast("بلا إذن الكاميرا لا أستطيع التجربة"); return; }
        if (ok) save(); else toast("بلا إذن التخزين لا أستطيع الحفظ في التنزيلات");
    }

    // ---------------------------------------------------------------- v2: the camera shown by this app itself
    /** Lists the cameras Android knows (Camera2 ids and the old Camera API), and shows the one picked full screen. */
    private void testAndroidCamera() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 2);
            return;
        }
        final List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                String facing = "?";
                try { Integer f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                    facing = f == null ? "?" : f == 0 ? "أمامية" : f == 1 ? "خلفية" : "خارجية"; } catch (Exception ignored) { }
                keys.add("c2:" + id); labels.add("كاميرا " + id + " (" + facing + ")");
            }
        } catch (Exception e) { note("camera2 list " + e.getClass().getSimpleName()); }
        int legacy = 0;
        try { legacy = android.hardware.Camera.getNumberOfCameras(); } catch (Exception ignored) { }
        for (int i = 0; i < legacy; i++) { keys.add("old:" + i); labels.add("الطريقة القديمة " + i); }
        if (keys.isEmpty()) { toast("أندرويد لا يرى أي كاميرا"); return; }
        new AlertDialog.Builder(this)
                .setTitle("اختر واحدة؛ رجوع يغلقها. أيّها أظهرت صورة؟")
                .setItems(labels.toArray(new String[0]), (d, i) -> {
                    String k = keys.get(i);
                    if (k.startsWith("c2:")) showCamera2(k.substring(3)); else showLegacy(Integer.parseInt(k.substring(4)));
                })
                .show();
    }

    private void showCamera2(final String id) {
        final TextureView tv = new TextureView(this);
        final Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.setContentView(tv);
        final CameraDevice[] dev = new CameraDevice[1];
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(final SurfaceTexture st, int w, int h) {
                try {
                    CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
                    cm.openCamera(id, new CameraDevice.StateCallback() {
                        public void onOpened(CameraDevice c) {
                            dev[0] = c;
                            try {
                                st.setDefaultBufferSize(1280, 720);
                                final Surface s = new Surface(st);
                                final CaptureRequest.Builder rb = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                rb.addTarget(s);
                                c.createCaptureSession(Collections.singletonList(s), new CameraCaptureSession.StateCallback() {
                                    public void onConfigured(CameraCaptureSession ss) {
                                        try { ss.setRepeatingRequest(rb.build(), null, null); note("camera2 " + id + " preview started"); }
                                        catch (Exception e) { note("camera2 " + id + " repeat " + e.getClass().getSimpleName()); }
                                    }
                                    public void onConfigureFailed(CameraCaptureSession ss) { note("camera2 " + id + " configure failed"); }
                                }, null);
                            } catch (Exception e) { note("camera2 " + id + " session " + e.getClass().getSimpleName()); }
                        }
                        public void onDisconnected(CameraDevice c) { c.close(); }
                        public void onError(CameraDevice c, int e) { note("camera2 " + id + " error " + e); c.close(); }
                    }, null);
                } catch (Exception e) { note("camera2 " + id + " open " + e.getClass().getSimpleName() + " " + e.getMessage()); }
            }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { if (dev[0] != null) dev[0].close(); return true; }
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
            public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });
        d.setOnDismissListener(x -> { if (dev[0] != null) dev[0].close(); });
        d.show();
    }

    @SuppressWarnings("deprecation")
    private void showLegacy(final int idx) {
        final TextureView tv = new TextureView(this);
        final Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.setContentView(tv);
        final android.hardware.Camera[] cam = new android.hardware.Camera[1];
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                try { cam[0] = android.hardware.Camera.open(idx); cam[0].setPreviewTexture(st); cam[0].startPreview(); note("legacy " + idx + " preview started"); }
                catch (Exception e) { note("legacy " + idx + " " + e.getClass().getSimpleName() + " " + e.getMessage()); }
            }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { release(); return true; }
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
            public void onSurfaceTextureUpdated(SurfaceTexture st) { }
            private void release() { if (cam[0] != null) { try { cam[0].stopPreview(); cam[0].release(); } catch (Exception ignored) { } cam[0] = null; } }
        });
        d.setOnDismissListener(x -> { if (cam[0] != null) { try { cam[0].release(); } catch (Exception ignored) { } cam[0] = null; } });
        d.show();
    }

    // ---------------------------------------------------------------- v2: what the navigation and audio apps publish
    /** Needs «الوصول إلى الإشعارات» once. Then lists the notifications of the maps and audio apps, and the media sessions. */
    private void testNotifications() {
        String on = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (on == null || !on.contains(getPackageName())) {
            toast("فعّل «فحص سيارتك» في الصفحة التالية، ثم ارجع واضغط الزر مرة أخرى");
            try { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); }
            catch (Exception e) { toast("لم تُفتح صفحة الإذن: " + e.getClass().getSimpleName()); }
            return;
        }
        StringBuilder s = new StringBuilder("\n[notifications] ").append(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())).append("\n");
        NotiListener l = NotiListener.live;
        if (l == null) s.append("listener not connected yet; try again in a few seconds\n");
        else {
            try {
                for (StatusBarNotification n : l.getActiveNotifications()) {
                    String p = n.getPackageName();
                    if (!p.matches("(?i).*(maps|waze|igo|nng|radio|music|youtube|seen|zlink|tw\\.).*")) continue;
                    Bundle x = n.getNotification().extras;
                    s.append("== ").append(p).append(n.isOngoing() ? " (ongoing)" : "").append("\n");
                    for (String k : new String[]{Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT,
                            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_INFO_TEXT, Notification.EXTRA_TEMPLATE}) {
                        Object v = x.get(k);
                        if (v != null) s.append("  ").append(k.replace("android.", "")).append(": ").append(v).append("\n");
                    }
                    s.append("  has_custom_view=").append(n.getNotification().contentView != null || n.getNotification().bigContentView != null).append("\n");
                }
            } catch (Exception e) { s.append("notifications error ").append(e.getClass().getSimpleName()).append("\n"); }
        }
        s.append("[media sessions]\n");
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            for (MediaController c : msm.getActiveSessions(new ComponentName(this, NotiListener.class))) {
                MediaMetadata md = c.getMetadata();
                s.append("== ").append(c.getPackageName())
                        .append(" state=").append(c.getPlaybackState() == null ? "?" : c.getPlaybackState().getState())
                        .append(" title=").append(md == null ? "" : md.getString(MediaMetadata.METADATA_KEY_TITLE))
                        .append(" artist=").append(md == null ? "" : md.getString(MediaMetadata.METADATA_KEY_ARTIST))
                        .append(" actions=").append(c.getPlaybackState() == null ? "?" : c.getPlaybackState().getActions()).append("\n");
            }
        } catch (Exception e) { s.append("media error ").append(e.getClass().getSimpleName()).append("\n"); }
        note(s.toString());
        new AlertDialog.Builder(this).setTitle("ما تنشره التطبيقات الآن").setMessage(s.toString()).setPositiveButton("حسنًا", null).show();
    }

    private SharedPreferences prefs() { return getSharedPreferences("probe", MODE_PRIVATE); }

    /** Every test result is kept at once, so a test done in another session of the app is not lost. */
    private void note(final String t) {
        final String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " " + t + "\n";
        prefs().edit().putString("tests", prefs().getString("tests", "") + line).apply();
        runOnUiThread(() -> { report += "[test] " + line; out.setText(report); });
    }

    // ---------------------------------------------------------------- v3: what the screen's own launcher opens
    /** For 60 seconds, records every screen that comes to the front (needs «الوصول إلى بيانات الاستخدام» once). */
    private void watchForeground() {
        AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
        if (mode != AppOpsManager.MODE_ALLOWED) {
            toast("فعّل «فحص سيارتك» في الصفحة التالية، ثم ارجع واضغط الزر مرة أخرى");
            try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } catch (Exception e) { toast("لم تُفتح صفحة الإذن"); }
            return;
        }
        final long start = System.currentTimeMillis();
        note("watch start");
        toast("خلال دقيقة: افتح الكاميرا من واجهة الشاشة، ثم ارجع إلى هنا");
        new Handler().postDelayed(() -> {
            UsageStatsManager um = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            UsageEvents ev = um.queryEvents(start - 1000, System.currentTimeMillis());
            UsageEvents.Event e = new UsageEvents.Event();
            String last = "";
            StringBuilder seen = new StringBuilder();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() != UsageEvents.Event.MOVE_TO_FOREGROUND) continue;
                String c = e.getPackageName() + "/" + e.getClassName();
                if (c.equals(last)) continue;
                last = c;
                seen.append("  ").append(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(e.getTimeStamp()))).append(" ").append(c).append("\n");
            }
            note("watch end, screens in front:\n" + (seen.length() == 0 ? "  (none)\n" : seen));
            new AlertDialog.Builder(this).setTitle("ما فُتح خلال الدقيقة").setMessage(seen.length() == 0 ? "لا شيء" : seen.toString()).setPositiveButton("حسنًا", null).show();
        }, 60000);
    }

    // ---------------------------------------------------------------- v4: can a navigation app run inside our screen?
    private static final int FLAG_ALLOW_EMBEDDED = 0x80000000;   // ActivityInfo.FLAG_ALLOW_EMBEDDED (hidden)
    private static final String[] NAV_ALL = {"com.google.android.apps.maps", "com.waze", "com.nng.igo.primong.igoworld"};

    /** How the screen's own launcher can host the map, and whether the navigation apps allow being hosted. Read-only. */
    private String embedInfo() {
        StringBuilder s = new StringBuilder("\n[embedding]\n");
        PackageManager pm = getPackageManager();
        String[] perms = {"android.permission.ACTIVITY_EMBEDDING", "android.permission.MANAGE_ACTIVITY_STACKS",
                "android.permission.INJECT_EVENTS", "android.permission.CAPTURE_VIDEO_OUTPUT", "android.permission.INTERNAL_SYSTEM_WINDOW"};
        for (String pkg : new String[]{"com.dofun.variety", getPackageName()}) {
            s.append("== ").append(pkg).append("\n");
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                s.append("  system_app=").append((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                        .append("  platform_signed=").append(pm.checkSignatures("android", pkg) == PackageManager.SIGNATURE_MATCH)
                        .append("  shared_uid=").append(pm.getNameForUid(ai.uid)).append("\n");
            } catch (Exception e) { s.append("  ").append(e.getClass().getSimpleName()).append("\n"); }
            for (String perm : perms)
                s.append("  ").append(perm.replace("android.permission.", "")).append("=")
                        .append(pm.checkPermission(perm, pkg) == PackageManager.PERMISSION_GRANTED).append("\n");
        }
        for (String n : NAV_ALL) {
            Intent li = pm.getLaunchIntentForPackage(n);
            if (li == null || li.getComponent() == null) continue;
            try {
                ActivityInfo a = pm.getActivityInfo(li.getComponent(), 0);
                s.append(n).append(" allow_embedded=").append((a.flags & FLAG_ALLOW_EMBEDDED) != 0).append("\n");
            } catch (Exception e) { s.append(n).append(" ").append(e.getClass().getSimpleName()).append("\n"); }
        }
        s.append("multi_display_feature=").append(pm.hasSystemFeature("android.software.activities_on_secondary_displays")).append("\n");
        return s.toString();
    }

    private void pickEmbed() {
        final List<String> pk = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String n : NAV_ALL) if (getPackageManager().getLaunchIntentForPackage(n) != null) {
            pk.add("vd:" + n); labels.add("شاشة افتراضية: " + n);
            pk.add("av:" + n); labels.add("ActivityView: " + n);
        }
        if (pk.isEmpty()) { toast("لا يوجد تطبيق ملاحة"); return; }
        new AlertDialog.Builder(this).setTitle("أيّها أظهر الخريطة داخل الإطار؟ رجوع يغلق")
                .setItems(labels.toArray(new String[0]), (d, i) -> {
                    String k = pk.get(i);
                    if (k.startsWith("vd:")) embedVirtualDisplay(k.substring(3)); else embedActivityView(k.substring(3));
                }).show();
    }

    /** Our own virtual display drawn in a frame; the navigation app is started on it. Shows the map only (no touch). */
    private void embedVirtualDisplay(final String pkg) {
        final TextureView tv = new TextureView(this);
        final Dialog d = frame(tv);
        final VirtualDisplay[] vd = new VirtualDisplay[1];
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
                int[] flagSets = {DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY};
                for (int f : flagSets) {
                    try {
                        vd[0] = dm.createVirtualDisplay("urcar-nav", w, h, 160, new Surface(st), f);
                        note("virtual display flags=" + f + " created id=" + vd[0].getDisplay().getDisplayId());
                        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                        ActivityOptions o = ActivityOptions.makeBasic();
                        o.setLaunchDisplayId(vd[0].getDisplay().getDisplayId());
                        startActivity(i, o.toBundle());
                        note("virtual display start " + pkg + " sent (no exception)");
                        return;
                    } catch (Exception e) {
                        note("virtual display flags=" + f + " " + pkg + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        if (vd[0] != null) { vd[0].release(); vd[0] = null; }
                    }
                }
            }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { if (vd[0] != null) vd[0].release(); return true; }
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
            public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });
        d.setOnDismissListener(x -> { if (vd[0] != null) { vd[0].release(); vd[0] = null; } });
        d.show();
    }

    /** android.app.ActivityView (hidden, what system launchers use), through reflection. */
    private void embedActivityView(final String pkg) {
        try {
            Class<?> c = Class.forName("android.app.ActivityView");
            final View av = (View) c.getConstructor(android.content.Context.class).newInstance(this);
            final Dialog d = frame(av);
            final java.lang.reflect.Method start = c.getMethod("startActivity", Intent.class);
            note("ActivityView created");
            d.show();
            av.postDelayed(() -> {
                try {
                    Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                    start.invoke(av, i);
                    note("ActivityView start " + pkg + " sent (no exception)");
                } catch (Throwable t) {
                    Throwable x = t.getCause() != null ? t.getCause() : t;
                    note("ActivityView start " + pkg + " " + x.getClass().getSimpleName() + ": " + x.getMessage());
                }
            }, 1500);
        } catch (Throwable t) {
            Throwable x = t.getCause() != null ? t.getCause() : t;
            note("ActivityView " + x.getClass().getSimpleName() + ": " + x.getMessage());
        }
    }

    /** A frame over the left three fifths of the screen, like the launcher's map area. */
    private Dialog frame(View content) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        d.setContentView(content);
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        if (d.getWindow() != null) d.getWindow().setLayout(m.widthPixels * 3 / 5, m.heightPixels * 3 / 4);
        return d;
    }

    // ---------------------------------------------------------------- v5: the map full screen, our panel floating over part of it
    static View panel;   // the floating panel, kept while the process lives

    /** Starts Google Maps full screen, then floats a panel over the left two fifths. Touches outside the panel reach the map. */
    private void testOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            toast("فعّل «الظهور فوق التطبيقات» لـ«فحص سيارتك»، ثم ارجع واضغط الزر مرة أخرى");
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { toast("لم تُفتح صفحة الإذن: " + e.getClass().getSimpleName()); }
            return;
        }
        Intent maps = null;
        for (String n : NAV_ALL) { maps = getPackageManager().getLaunchIntentForPackage(n); if (maps != null) break; }
        if (maps == null) { toast("لا يوجد تطبيق ملاحة"); return; }
        startActivity(maps.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
        if (panel != null) { try { wm.removeView(panel); } catch (Exception ignored) { } panel = null; }
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);

        LinearLayout box = new LinearLayout(getApplicationContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.rgb(18, 29, 31));
        box.setPadding(24, 24, 24, 24);
        TextView t = new TextView(getApplicationContext());
        t.setText("لوحة تجربة\nالصلاة القادمة: العصر\nباقٍ ٣٤ دقيقة\n\nجرّب تحريك الخريطة بجانبها");
        t.setTextColor(Color.rgb(238, 241, 234));
        t.setTextSize(22);
        box.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        Button close = new Button(getApplicationContext());
        close.setText("إغلاق اللوحة");
        close.setTextSize(20);
        close.setOnClickListener(v -> { try { wm.removeView(panel); } catch (Exception ignored) { } panel = null; note("overlay closed by the owner"); });
        box.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                m.widthPixels * 2 / 5, WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        try { wm.addView(box, lp); panel = box; note("overlay shown over " + maps.getPackage()); }
        catch (Exception e) { note("overlay " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    private void toast(String t) { Toast.makeText(this, t, Toast.LENGTH_LONG).show(); }
}
