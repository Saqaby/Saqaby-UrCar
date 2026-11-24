package sa.urcar.home;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * What the screen learns from the car's own trips, kept on the screen only: each trip's two ends («legs»), the places
 * the car keeps stopping at («places»), and from them the likely destination when a trip starts. Each of the five
 * extras has its switch in the settings («smart_…»).
 */
final class Smart {
    private Smart() {}

    /** Two points this close (km) are the same place: a car park, a street's two sides, the GPS's own error. */
    static final double NEAR = 0.3;
    /** A place stopped at this many times is a destination of the owner's. */
    static final int VISITS = 3;

    static boolean on(Context c, String key, boolean def) { return Prefs.of(c).getBoolean("smart_" + key, def); }
    static void set(Context c, String key, boolean v) { Prefs.of(c).edit().putBoolean("smart_" + key, v).apply(); }

    private static JSONArray arr(Context c, String key) {
        try { return new JSONArray(Prefs.of(c).getString(key, "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    /**
     * A trip starts here: so the trip before ended here. That trip's two ends are kept as a leg {start time, from, to}
     * (the last 400), and its end counts as a visit to a place.
     */
    static void tripStarts(Context c, double la, double lo, long now) {
        SharedPreferences p = Prefs.of(c);
        float fl = p.getFloat("leg_lat", 999), fo = p.getFloat("leg_lon", 999);
        long ft = p.getLong("leg_t", 0);
        if (fl < 90 && ft > 0 && Day.km(fl, fo, la, lo) > NEAR) {
            try {
                JSONArray legs = arr(c, "legs");
                legs.put(new JSONArray().put(ft).put(r5(fl)).put(r5(fo)).put(r5(la)).put(r5(lo)));
                while (legs.length() > 400) legs.remove(0);
                p.edit().putString("legs", legs.toString()).apply();
                visit(c, la, lo, now);
            } catch (Exception ignored) { }
        }
        p.edit().putFloat("leg_lat", (float) la).putFloat("leg_lon", (float) lo).putLong("leg_t", now).apply();
    }
    private static double r5(double v) { return Math.round(v * 1e5) / 1e5; }

    private static boolean at(Context c, String key, double la, double lo) {
        float hl = Prefs.of(c).getFloat(key + "_lat", 999), ho = Prefs.of(c).getFloat(key + "_lon", 999);
        return hl < 90 && Day.km(hl, ho, la, lo) <= NEAR;
    }

    /** One more stop at a place that is neither the home nor the work: counted on its place, or a new place. */
    private static void visit(Context c, double la, double lo, long now) throws Exception {
        if (at(c, "home", la, lo) || at(c, "work", la, lo)) return;
        JSONArray ps = arr(c, "places");
        int i = find(ps, la, lo);
        if (i >= 0) { JSONObject o = ps.getJSONObject(i); o.put("n", o.optInt("n") + 1).put("last", now); }
        else ps.put(new JSONObject().put("lat", r5(la)).put("lon", r5(lo)).put("n", 1).put("name", "").put("last", now));
        while (ps.length() > 60) {   // the least visited unnamed one makes room
            int drop = 0, least = Integer.MAX_VALUE;
            for (int k = 0; k < ps.length(); k++) {
                JSONObject o = ps.getJSONObject(k);
                if (o.optString("name").isEmpty() && o.optInt("n") < least) { least = o.optInt("n"); drop = k; }
            }
            ps.remove(drop);
        }
        Prefs.put(c, "places", ps.toString());
    }
    private static int find(JSONArray ps, double la, double lo) {
        for (int i = 0; i < ps.length(); i++) {
            JSONObject o = ps.optJSONObject(i);
            if (o != null && Day.km(o.optDouble("lat"), o.optDouble("lon"), la, lo) <= NEAR) return i;
        }
        return -1;
    }

    /** The owner's places: stopped at three times or more and not removed, the most visited first; each {index, name, n, lat, lon}. */
    static java.util.List<Object[]> places(Context c) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        JSONArray ps = arr(c, "places");
        for (int i = 0; i < ps.length(); i++) {
            JSONObject o = ps.optJSONObject(i);
            if (o == null || o.optBoolean("hidden") || o.optInt("n") < VISITS) continue;
            out.add(new Object[]{i, o.optString("name"), o.optInt("n"), o.optDouble("lat"), o.optDouble("lon")});
        }
        java.util.Collections.sort(out, (a, b) -> (Integer) b[2] - (Integer) a[2]);
        return out;
    }
    static String placeLabel(Object[] p) { return ((String) p[1]).isEmpty() ? "مكان متكرر" : (String) p[1]; }

    /** The place the car stands at, when it is one of the owner's, has no name and was never asked about: its index, or -1. */
    static int toName(Context c, double la, double lo) {
        JSONArray ps = arr(c, "places");
        int i = find(ps, la, lo);
        if (i < 0) return -1;
        JSONObject o = ps.optJSONObject(i);
        return o.optInt("n") >= VISITS && !o.optBoolean("hidden") && !o.optBoolean("asked") && o.optString("name").isEmpty() ? i : -1;
    }
    /** name: a new name, or null to keep it; hidden / asked: set when true. */
    static void edit(Context c, int index, String name, boolean hidden, boolean asked) {
        try {
            JSONArray ps = arr(c, "places");
            JSONObject o = ps.getJSONObject(index);
            if (name != null) o.put("name", name.trim());
            if (hidden) o.put("hidden", true);
            if (asked) o.put("asked", true);
            Prefs.put(c, "places", ps.toString());
        } catch (Exception ignored) { }
    }

    private static boolean weekend(Calendar c) { int d = c.get(Calendar.DAY_OF_WEEK); return d == Calendar.FRIDAY || d == Calendar.SATURDAY; }

    /**
     * Where a trip starting here and now most likely goes: {label, lat, lon}, or null when nothing stands out.
     * From the legs that started at this place: each counts 1, one more on the same kind of day (work day or weekend),
     * two more within two hours of this hour. The place with the most must have two legs or more at this hour (an
     * evening habit says nothing of the morning) and half the weight. Otherwise: the home to the work on a work-day morning, the work to the home from noon.
     */
    static Object[] predict(Context c, double la, double lo, long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        boolean we = weekend(cal);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        JSONArray legs = arr(c, "legs");
        java.util.List<double[]> spots = new java.util.ArrayList<>();   // {lat, lon, weight, legs at this hour}
        double total = 0;
        for (int i = 0; i < legs.length(); i++) {
            JSONArray g = legs.optJSONArray(i);
            if (g == null || Day.km(g.optDouble(1), g.optDouble(2), la, lo) > NEAR) continue;
            cal.setTimeInMillis(g.optLong(0));
            int dh = Math.abs(cal.get(Calendar.HOUR_OF_DAY) - hour);
            boolean sameHour = Math.min(dh, 24 - dh) <= 2;
            double w = 1 + (weekend(cal) == we ? 1 : 0) + (sameHour ? 2 : 0);
            total += w;
            double tla = g.optDouble(3), tlo = g.optDouble(4);
            double[] hit = null;
            for (double[] s : spots) if (Day.km(s[0], s[1], tla, tlo) <= NEAR) { hit = s; break; }
            if (hit == null) spots.add(hit = new double[]{tla, tlo, 0, 0});
            hit[2] += w; if (sameHour) hit[3]++;
        }
        double[] best = null;
        for (double[] s : spots) if (best == null || s[2] > best[2]) best = s;
        if (best != null && best[3] >= 2 && best[2] >= total / 2) return new Object[]{nameOf(c, best[0], best[1]), best[0], best[1]};
        SharedPreferences p = Prefs.of(c);
        boolean home = p.getFloat("home_lat", 999) < 90, work = p.getFloat("work_lat", 999) < 90;
        if (home && work && at(c, "home", la, lo) && !we && hour >= 5 && hour <= 10)
            return new Object[]{"العمل", (double) p.getFloat("work_lat", 0), (double) p.getFloat("work_lon", 0)};
        if (home && work && at(c, "work", la, lo) && hour >= 12)
            return new Object[]{"البيت", (double) p.getFloat("home_lat", 0), (double) p.getFloat("home_lon", 0)};
        return null;
    }
    private static String nameOf(Context c, double la, double lo) {
        if (at(c, "home", la, lo)) return "البيت";
        if (at(c, "work", la, lo)) return "العمل";
        JSONArray ps = arr(c, "places");
        int i = find(ps, la, lo);
        String n = i < 0 ? "" : ps.optJSONObject(i).optString("name");
        return n.isEmpty() ? "وجهتك المعتادة" : n;
    }

    /** Minutes from the adhan to the iqama, by the owner's mosque (the settings); Fajr 25, Maghrib 10, the rest 20 to begin with. */
    static int iqama(Context c, int prayer) {
        int def = prayer == PrayerMath.FAJR ? 25 : prayer == PrayerMath.MAGHRIB ? 10 : 20;
        return Prefs.of(c).getInt("iqama_" + prayer, def);
    }
}
