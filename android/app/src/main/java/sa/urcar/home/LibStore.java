package sa.urcar.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.widget.Toast;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A reciter's whole recitation kept on the screen (files/library/<reciter>/001.mp3 … 114.mp3), for the library and the
 * khatma to play without the internet. «تنزيل الكل» fetches the surahs one after another in the background (a surah
 * already there is skipped, a failed one tried once more), and stops when pressed again; «حذف» removes them all.
 */
final class LibStore {
    private LibStore() {}
    /** reciter -> the surah being fetched now */
    static final Map<String, Integer> running = new ConcurrentHashMap<>();
    private static final Set<String> cancel = ConcurrentHashMap.newKeySet();

    static File dir(Context c, String id) { return new File(c.getFilesDir(), "library/" + id); }
    static File file(Context c, String id, int s) { return new File(dir(c, id), String.format(java.util.Locale.US, "%03d.mp3", s)); }
    static int count(Context c, String id) {
        String[] l = dir(c, id).list();
        int n = 0;
        if (l != null) for (String x : l) if (x.endsWith(".mp3")) n++;
        return n;
    }
    static long bytes(Context c, String id) {
        File[] l = dir(c, id).listFiles();
        long n = 0;
        if (l != null) for (File x : l) n += x.length();
        return n;
    }
    static long free(Context c) { try { return new StatFs(c.getFilesDir().getPath()).getAvailableBytes(); } catch (Exception e) { return -1; }
    }
    static String gb(long b) { return Ui.ar(String.format(java.util.Locale.US, b >= 1e9 ? "%.1f" : "%.2f", b / 1e9)) + " غ.ب"; }

    /** Start, or stop when already running. */
    static void downloadAll(Context c, String id, String name, String server) {
        if (running.containsKey(id)) { cancel.add(id); return; }
        final Context app = c.getApplicationContext();
        final Handler h = new Handler(Looper.getMainLooper());
        cancel.remove(id);
        running.put(id, 1);
        new Thread(() -> {
            int failed = 0;
            dir(app, id).mkdirs();
            for (int s = 1; s <= 114 && !cancel.contains(id); s++) {
                running.put(id, s);
                File f = file(app, id, s);
                if (f.length() > 0) continue;
                String url = server + String.format(java.util.Locale.US, "%03d.mp3", s);
                try { Net.download(url, f); }
                catch (Exception e) {
                    try { Net.download(url, f); } catch (Exception e2) { failed++; new File(f.getPath() + ".part").delete(); }
                }
            }
            boolean stopped = cancel.remove(id);
            running.remove(id);
            final int fl = failed;
            h.post(() -> Toast.makeText(app, stopped ? "أُوقف تنزيل تلاوات " + name
                    : fl == 0 ? "نُزّلت تلاوات " + name + " كاملة" : "نُزّلت تلاوات " + name + " إلا " + Ui.ar(fl) + " سورة: أعد التنزيل لإكمالها",
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    static void delete(Context c, String id) {
        cancel.add(id);
        File[] l = dir(c, id).listFiles();
        if (l != null) for (File x : l) x.delete();
        dir(c, id).delete();
    }
}
