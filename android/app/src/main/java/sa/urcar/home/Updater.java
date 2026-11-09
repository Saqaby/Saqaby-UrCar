package sa.urcar.home;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The app updates itself from this repository: app-apk.yml publishes the release «app-latest» with urcar.apk and
 * version.json ({"versionCode": n}), and promote.yml copies a tried one to «app-stable» (see base()). A newer one is downloaded and handed to Android's installer, which asks the
 * owner once («تثبيت»). Every 6 hours while online, or «تحديث الآن» in the settings.
 */
final class Updater {
    private Updater() {}
    static final String REPO = "https://github.com/Saqaby/Saqaby-UrCar/releases/download/";
    /**
     * Two channels: «test» follows every build (app-latest: the owner's screen, where a change is tried first),
     * «stable» only what was promoted to app-stable (promote.yml) once it worked there. New screens are on «stable».
     */
    static String base(Context c) { return REPO + (Prefs.of(c).getString("channel", "stable").equals("test") ? "app-latest/" : "app-stable/"); }

    /** The same files kept in the repository, served by raw.githubusercontent.com without a redirect to another host. */
    static final String RAW = "https://raw.githubusercontent.com/Saqaby/Saqaby-UrCar/claude/urcar-arabic-head-unit-2e7knc/apk/";
    static String mirror(Context c) { return RAW + (Prefs.of(c).getString("channel", "stable").equals("test") ? "" : "stable/"); }

    /**
     * The release first, then its copy in the repository; each tried twice. The owner's screen got 404s and 504s from
     * the release downloads (which redirect to another host) and stopped updating at version 50.
     */
    static void check(Context c, boolean manual) {
        String[] bases = {base(c), mirror(c)};
        Exception last = null;
        try {
            int ours = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
            int theirs = -1;
            for (String b : bases) {
                for (int i = 0; i < 2 && theirs < 0; i++) {
                    try { theirs = new JSONObject(Net.get(b + "version.json")).getInt("versionCode"); }
                    catch (Exception e) { last = e; sleep(2000); }
                }
                if (theirs >= 0) break;
            }
            if (theirs < 0) throw last;
            if (theirs <= ours) { if (manual) toast(c, "سيارتك محدّثة (الإصدار " + Ui.ar(ours) + ")"); return; }
            if (manual) toast(c, "يُنزَّل الإصدار " + Ui.ar(theirs) + "…");
            File apk = new File(c.getCacheDir(), "urcar.apk");
            boolean got = false;
            for (String b : bases) {
                for (int i = 0; i < 2 && !got; i++) {
                    try {
                        apk.delete();
                        Net.download(b + "urcar.apk", apk);
                        got = isApk(apk);
                        if (!got) last = new Exception("الملف المنزَّل ليس تطبيقًا");
                    } catch (Exception e) { last = e; sleep(3000); }
                }
                if (got) break;
            }
            if (!got) throw last;
            install(c, apk);
        } catch (Exception e) {
            if (manual) toast(c, e instanceof java.net.UnknownHostException ? "لا إنترنت في الشاشة الآن: وصّلها بشبكة الجوال ثم أعد المحاولة"
                    : "لم أصل إلى التحديث: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
        }
    }
    /** An APK is a zip of some size: «PK» first, more than half a megabyte. */
    private static boolean isApk(File f) {
        if (f.length() < 500_000) return false;
        try (FileInputStream in = new FileInputStream(f)) { return in.read() == 'P' && in.read() == 'K'; } catch (Exception e) { return false; }
    }
    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }

    private static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(c.getPackageName());
        int id = pi.createSession(sp);
        try (PackageInstaller.Session s = pi.openSession(id);
             InputStream in = new FileInputStream(apk);
             OutputStream out = s.openWrite("urcar", 0, apk.length())) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            s.fsync(out);
            out.close();
            Intent done = new Intent(c, Done.class);
            s.commit(PendingIntent.getBroadcast(c, id, done, PendingIntent.FLAG_UPDATE_CURRENT).getIntentSender());
        }
    }

    private static void toast(Context c, String t) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(c.getApplicationContext(), t, Toast.LENGTH_LONG).show());
    }

    /** The installer's answer: it needs the owner's «تثبيت» first (an app that is not part of the system). */
    public static class Done extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (st != PackageInstaller.STATUS_SUCCESS) {
                Toast.makeText(c, "لم يكتمل التحديث", Toast.LENGTH_LONG).show();
            }
        }
    }
}
