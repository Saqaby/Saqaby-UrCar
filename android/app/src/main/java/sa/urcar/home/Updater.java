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
 * version.json ({"versionCode": n}). A newer one is downloaded and handed to Android's installer, which asks the
 * owner once («تثبيت»). Every 6 hours while online, or «تحديث الآن» in the settings.
 */
final class Updater {
    private Updater() {}
    static final String BASE = "https://github.com/Saqaby/Saqaby-UrCar/releases/download/app-latest/";

    static void check(Context c, boolean manual) {
        try {
            int ours = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
            int theirs = new JSONObject(Net.get(BASE + "version.json")).getInt("versionCode");
            if (theirs <= ours) { if (manual) toast(c, "سيارتك محدّثة (الإصدار " + Ui.ar(ours) + ")"); return; }
            if (manual) toast(c, "يُنزَّل الإصدار " + Ui.ar(theirs) + "…");
            File apk = new File(c.getCacheDir(), "urcar.apk");
            Net.download(BASE + "urcar.apk", apk);
            install(c, apk);
        } catch (Exception e) {
            if (manual) toast(c, e instanceof java.net.UnknownHostException ? "لا إنترنت في الشاشة الآن: وصّلها بشبكة الجوال ثم أعد المحاولة"
                    : "لم أصل إلى التحديث: " + e.getClass().getSimpleName());
        }
    }

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
