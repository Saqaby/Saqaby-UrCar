package sa.urcar.home;

import android.content.Context;
import android.content.SharedPreferences;

/** What the owner chooses in «الإعدادات», and the last place and weather, kept on the screen. */
final class Prefs {
    private Prefs() {}

    /** The navigation apps the screen may run beside the panel, first installed one by default. */
    static final String[][] NAV = {
            {"com.google.android.apps.maps", "خرائط Google"},
            {"com.waze", "Waze"},
            {"com.nng.igo.primong.igoworld", "iGO"},
            {"com.here.app.maps", "HERE WeGo"},
            {"com.sygic.aura", "Sygic"},
            {"net.osmand", "OsmAnd"},
            {"com.vialsoft.radarbot_free", "Radarbot"},
    };

    static SharedPreferences of(Context c) { return c.getSharedPreferences("urcar", Context.MODE_PRIVATE); }

    static String nav(Context c) {
        String v = of(c).getString("nav", "");
        if (!v.isEmpty() && installed(c, v)) return v;
        for (String[] n : NAV) if (installed(c, n[0])) return n[0];
        return "";
    }
    /**
     * «هدهد» (Hudhud Maps, Saudi): its Android package is not published anywhere we could read, so it is found on the
     * screen by its name, «هدهد» or «HudHud»; "" when it is not installed.
     */
    static String hudhud(Context c) {
        android.content.pm.PackageManager pm = c.getPackageManager();
        java.util.regex.Pattern name = java.util.regex.Pattern.compile("(?i)هدهد|هُدهُد|hud\\s?hud");
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!pkg.equals(c.getPackageName()) && name.matcher(ri.loadLabel(pm).toString()).find()) return pkg;
        }
        return "";
    }
    static String navName(Context c, String pkg) {
        for (String[] n : NAV) if (n[0].equals(pkg)) return n[1];
        if (!pkg.isEmpty() && pkg.equals(hudhud(c))) return "هدهد";
        return pkg;
    }
    static boolean installed(Context c, String pkg) {
        try { c.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    /** left (the default: Saudi cars) or right: which side of the screen the panel is on */
    static String side(Context c) { return of(c).getString("side", "left"); }
    /** auto (day from sunrise to Maghrib), light or dark */
    static String theme(Context c) { return of(c).getString("theme", "auto"); }
    /** fm (the screen's radio) or net (the stream) */
    static String radioSource(Context c) { return of(c).getString("radio", "fm"); }
    /** p (the next prayer) or m (the music): the card's page */
    static String page(Context c) { return of(c).getString("page", "p"); }

    static void put(Context c, String k, String v) { of(c).edit().putString(k, v).apply(); }
}
