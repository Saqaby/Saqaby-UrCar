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

/** The few things the panel fetches: the temperature (Open-Meteo), and the FM table from this repository. */
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
        to.delete();
        if (!part.renameTo(to)) throw new Exception("rename");
    }

    /** {temperature °C, Open-Meteo weather code, visibility in metres or -1}, or null */
    static double[] weather(double lat, double lon) {
        try {
            JSONObject j = new JSONObject(get(String.format(java.util.Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,visibility", lat, lon)));
            JSONObject cur = j.getJSONObject("current");
            return new double[]{cur.getDouble("temperature_2m"), cur.optInt("weather_code", 0), cur.optDouble("visibility", -1)};
        } catch (Exception e) { return null; }
    }

    /** The larger of PM10 and dust (µg/m³) now at that place, Open-Meteo's air quality; -1 when unknown. */
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
