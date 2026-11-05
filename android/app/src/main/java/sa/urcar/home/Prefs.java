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
    };

    static SharedPreferences of(Context c) { return c.getSharedPreferences("urcar", Context.MODE_PRIVATE); }

    static String nav(Context c) {
        String v = of(c).getString("nav", "");
        if (!v.isEmpty() && installed(c, v)) return v;
        for (String[] n : NAV) if (installed(c, n[0])) return n[0];
        return "";
    }
    static String navName(Context c, String pkg) {
        for (String[] n : NAV) if (n[0].equals(pkg)) return n[1];
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
