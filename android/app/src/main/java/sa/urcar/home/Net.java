package sa.urcar.home;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** The few things the panel fetches: the temperature (Open-Meteo, as UrTV), and the FM table from this repository. */
final class Net {
    private Net() {}

    static final String REPO_RAW = "https://raw.githubusercontent.com/Saqaby/Saqaby-UrCar/HEAD/";

    static boolean online(Context c) {
        try {
            NetworkInfo n = ((ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Exception e) { return false; }
    }

    static String get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(12000);
        h.setReadTimeout(15000);
        h.setInstanceFollowRedirects(true);
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally { h.disconnect(); }
    }

    /** To a file next to it first, renamed when complete, so a cut connection never leaves half a file in its place. */
    static void download(String url, File to) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(30000);
        h.setInstanceFollowRedirects(true);
        h.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) UrCar");
        int code = h.getResponseCode();
        if (code >= 400) { h.disconnect(); throw new Exception("HTTP " + code); }
        File part = new File(to.getPath() + ".part");
        try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(part)) {
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        } finally { h.disconnect(); }
        if (!part.renameTo(to)) throw new Exception("rename");
    }

    /**
     * The speed limit (km/h) of the road the car is on, from OpenStreetMap through the Overpass API: the roads with a
     * «maxspeed» within 25 m, the main one first (a slip road beside a highway gives the highway's). 0 when none has
     * one, -1 when the request failed. «© OpenStreetMap contributors» (ODbL) is credited in the settings.
     */
    static int maxspeed(double lat, double lon) {
        try {
            String q = String.format(java.util.Locale.US, "[out:json][timeout:10];way(around:25,%.6f,%.6f)[highway][maxspeed];out tags;", lat, lon);
            HttpURLConnection h = (HttpURLConnection) new URL("https://overpass-api.de/api/interpreter?data=" + java.net.URLEncoder.encode(q, "UTF-8")).openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(12000);
            h.setRequestProperty("User-Agent", "UrCar (car head unit)");
            if (h.getResponseCode() != 200) { h.disconnect(); return -1; }
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            try (InputStream in = h.getInputStream()) { byte[] buf = new byte[8192]; for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n); }
            finally { h.disconnect(); }
            org.json.JSONArray els = new JSONObject(b.toString("UTF-8")).getJSONArray("elements");
            String[] rank = {"motorway", "trunk", "primary", "secondary", "tertiary", "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link"};
            int best = 0, bestRank = 99;
            for (int i = 0; i < els.length(); i++) {
                JSONObject t = els.getJSONObject(i).optJSONObject("tags");
                if (t == null) continue;
                int v = kmh(t.optString("maxspeed"));
                if (v <= 0) continue;
                int r = java.util.Arrays.asList(rank).indexOf(t.optString("highway"));
                if (r < 0) r = 50;
                if (r < bestRank) { bestRank = r; best = v; }
            }
            return best;
        } catch (Exception e) { return -1; }
    }
    /** The city or town at a place, in Arabic, from OpenStreetMap's Nominatim (zoom 10); "" when none, null when the request failed. */
    static String city(double lat, double lon) {
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(String.format(java.util.Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&accept-language=ar&lat=%.5f&lon=%.5f", lat, lon)).openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(10000);
            h.setRequestProperty("User-Agent", "UrCar (car head unit; github.com/Saqaby/Saqaby-UrCar)");
            if (h.getResponseCode() != 200) { h.disconnect(); return null; }
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            try (InputStream in = h.getInputStream()) { byte[] buf = new byte[8192]; for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n); }
            finally { h.disconnect(); }
            JSONObject a = new JSONObject(b.toString("UTF-8")).optJSONObject("address");
            if (a == null) return "";
            for (String k : new String[]{"city", "town", "village"}) if (!a.optString(k).isEmpty()) return a.optString(k);
            return "";
        } catch (Exception e) { return null; }
    }

    /** «120», «120 km/h», «60 mph»; anything else (a country code, «none», «walk») is no number. */
    static int kmh(String v) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d{2,3})\\s*(mph|km/h|kmh|kph)?\\s*$").matcher(v);
        if (!m.find()) return 0;
        int n = Integer.parseInt(m.group(1));
        return "mph".equals(m.group(2)) ? Math.round(n * 1.609f) : n;
    }

    /** {temperature °C, Open-Meteo weather code}, or null */
    static double[] weather(double lat, double lon) {
        try {
            JSONObject j = new JSONObject(get(String.format(java.util.Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code", lat, lon)));
            JSONObject cur = j.getJSONObject("current");
            return new double[]{cur.getDouble("temperature_2m"), cur.optInt("weather_code", 0)};
        } catch (Exception e) { return null; }
    }

    /** The larger of PM10 and dust (µg/m³) now at that place, Open-Meteo's air quality (as UrTV); -1 when unknown. */
    static double dust(double lat, double lon) {
        try {
            JSONObject c = new JSONObject(get(String.format(java.util.Locale.US,
                    "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.3f&longitude=%.3f&current=pm10,dust", lat, lon))).getJSONObject("current");
            return Math.max(c.optDouble("pm10", 0), c.optDouble("dust", 0));
        } catch (Exception e) { return -1; }
    }

    // ---------------------------------------------------------------- the FM table (assets/fm-quran.json; refreshed from the repo)
    static JSONArray fmStations(Context c) {
        String s = Prefs.of(c).getString("fm_table", "");
        try {
            if (s.isEmpty()) {
                try (InputStream in = c.getAssets().open("fm-quran.json")) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
                    s = b.toString("UTF-8");
                }
            }
            return new JSONObject(s).getJSONArray("stations");
        } catch (Exception e) { return new JSONArray(); }
    }
    static void refreshFm(Context c) {
        try {
            String s = get(REPO_RAW + "assets/shared/fm-quran.json");
            new JSONObject(s).getJSONArray("stations");
            Prefs.put(c, "fm_table", s);
        } catch (Exception ignored) { }
    }
    /** The frequencies the owner saved where the table has none (or a wrong one): [{name, lat, lon, mhz}] */
    static JSONArray mine(Context c) {
        try { return new JSONArray(Prefs.of(c).getString("fm_mine", "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    /** Saves the owner's frequency for this place; one within 10 km is replaced. */
    static void saveMine(Context c, String name, double lat, double lon, double mhz) {
        JSONArray old = mine(c), out = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o != null && Day.km(lat, lon, o.optDouble("lat"), o.optDouble("lon")) > 10) out.put(o);
        }
        try { out.put(new JSONObject().put("name", name).put("lat", lat).put("lon", lon).put("mhz", mhz)); } catch (Exception ignored) { }
        Prefs.put(c, "fm_mine", out.toString());
    }
    /** {city, MHz, verified} for the car's place: the owner's own within 80 km first, else the table's nearest within 80 km; null when none. */
    static Object[] fmHere(Context c, double lat, double lon) {
        JSONArray my = mine(c);
        JSONObject near = null; double dm = 1e9;
        for (int i = 0; i < my.length(); i++) {
            JSONObject o = my.optJSONObject(i);
            if (o == null) continue;
            double k = Day.km(lat, lon, o.optDouble("lat"), o.optDouble("lon"));
            if (k < dm) { dm = k; near = o; }
        }
        if (near != null && dm <= 80) return new Object[]{near.optString("name"), near.optDouble("mhz"), true};
        JSONArray st = fmStations(c);
        JSONObject best = null; double d = 1e9;
        for (int i = 0; i < st.length(); i++) {
            JSONObject o = st.optJSONObject(i);
            if (o == null) continue;
            double k = Day.km(lat, lon, o.optDouble("lat"), o.optDouble("lon"));
            if (k < d) { d = k; best = o; }
        }
        if (best == null || d > 80) return null;
        String city = best.optString("city");
        float own = Prefs.of(c).getFloat("fm_own_" + city, 0);
        return new Object[]{city, own >= 87.5f && own <= 108f ? own : best.optDouble("mhz"), best.optBoolean("verified")};
    }
}
