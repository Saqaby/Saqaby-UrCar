package sa.urcar.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.regex.Pattern;

/**
 * The screen's own Bluetooth (phone) and radio apps, which differ from one head unit to another. In order: the app the
 * owner chose in the settings; the owner's screen's (com.tw.bt, com.tw.radio); else the first app whose name says so
 * («بلوتوث», «Bluetooth», «هاتف» … / «راديو», «Radio», «FM»). "" when there is none: Bluetooth then opens Android's
 * Bluetooth settings, and the Quran radio plays over the internet.
 */
final class Device {
    private Device() {}
    private static final Pattern BT = Pattern.compile("(?i)بلوتوث|bluetooth|\\bbt\\b|هاتف|phone|dialer|اتصال");
    private static final Pattern RADIO = Pattern.compile("(?i)راديو|radio|\\bfm\\b|إذاعة");

    static String btApp(Context c) { return find(c, "bt_app", "com.tw.bt", BT); }
    static String radioApp(Context c) { return find(c, "radio_app", "com.tw.radio", RADIO); }

    private static String find(Context c, String pref, String ours, Pattern names) {
        PackageManager pm = c.getPackageManager();
        String chosen = Prefs.of(c).getString(pref, "");
        if (!chosen.isEmpty() && pm.getLaunchIntentForPackage(chosen) != null) return chosen;
        if (pm.getLaunchIntentForPackage(ours) != null) return ours;
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || pkg.startsWith("com.android.settings")) continue;
            if (names.matcher(ri.loadLabel(pm).toString()).find()) return pkg;
        }
        return "";
    }
    static String label(Context c, String pkg) {
        if (pkg.isEmpty()) return "لا يوجد";
        try { return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }
}
