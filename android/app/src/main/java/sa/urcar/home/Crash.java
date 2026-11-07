package sa.urcar.home;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * «آخر عطل»: when the app stops on an error, its time, version and the first lines of where it happened are kept on
 * the screen (files/crash.txt) and shown in the settings, so a fault on someone's screen can be told and fixed.
 * Nothing leaves the screen.
 */
final class Crash {
    private Crash() {}
    private static boolean installed;

    static synchronized void install(Context c) {
        if (installed) return;
        installed = true;
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter w = new StringWriter();
                e.printStackTrace(new PrintWriter(w));
                String[] lines = w.toString().split("\n");
                StringBuilder b = new StringBuilder();
                String v;
                try { v = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName; } catch (Exception x) { v = "?"; }
                b.append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date()))
                        .append("  الإصدار ").append(v).append('\n');
                for (int i = 0; i < Math.min(14, lines.length); i++) b.append(lines[i].trim()).append('\n');
                try (FileOutputStream o = new FileOutputStream(file(app))) { o.write(b.toString().getBytes("UTF-8")); }
            } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
    static File file(Context c) { return new File(c.getFilesDir(), "crash.txt"); }
    static String last(Context c) {
        try { return new String(java.nio.file.Files.readAllBytes(file(c).toPath()), "UTF-8"); } catch (Exception e) { return ""; }
    }
}
