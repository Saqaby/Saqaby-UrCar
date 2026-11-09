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

    /** {temperature °C, Open-Meteo weather code}, or null */
    static double[] weather(double lat, double lon) {
        try {
            JSONObject j = new JSONObject(get(String.format(java.util.Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code", lat, lon)));
            JSONObject cur = j.getJSONObject("current");
            return new double[]{cur.getDouble("temperature_2m"), cur.optInt("weather_code", 0)};
        } catch (Exception e) { return null; }
    }

    /** The weather page's data (Open-Meteo, CC BY 4.0): now, today, and the next five hours. */
    static final class Wx {
        double temp, feels, wind, max, min;
        int code, hum, rain;
        int[] hour = new int[5], hcode = new int[5];
        double[] htemp = new double[5];
    }
    static Wx forecast(double lat, double lon) {
        try {
            JSONObject j = new JSONObject(get(String.format(java.util.Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&timezone=auto&forecast_days=2"
                            + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code"
                            + "&hourly=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max", lat, lon)));
            JSONObject c = j.getJSONObject("current"), d = j.getJSONObject("daily"), h = j.getJSONObject("hourly");
            Wx w = new Wx();
            w.temp = c.getDouble("temperature_2m"); w.feels = c.optDouble("apparent_temperature", w.temp);
            w.hum = c.optInt("relative_humidity_2m", -1); w.wind = c.optDouble("wind_speed_10m", -1); w.code = c.optInt("weather_code", 0);
            w.max = d.getJSONArray("temperature_2m_max").getDouble(0); w.min = d.getJSONArray("temperature_2m_min").getDouble(0);
            w.rain = d.getJSONArray("precipitation_probability_max").optInt(0, -1);
            // the hours after the current one, from the place's own clock («2026-11-09T14:00»)
            String now = c.getString("time").substring(0, 13);
            org.json.JSONArray times = h.getJSONArray("time"), ht = h.getJSONArray("temperature_2m"), hc = h.getJSONArray("weather_code");
            int k = 0;
            for (int i = 0; i < times.length() && k < 5; i++) {
                String t = times.getString(i);
                if (t.substring(0, 13).compareTo(now) <= 0) continue;
                w.hour[k] = Integer.parseInt(t.substring(11, 13)); w.htemp[k] = ht.getDouble(i); w.hcode[k] = hc.getInt(i); k++;
            }
            return w;
        } catch (Exception e) { return null; }
    }
    /** The WMO weather code in Arabic. */
    static String sky(int code) {
        if (code == 0) return "صحو";
        if (code == 1) return "صحو غالبًا";
        if (code == 2) return "غائم جزئيًا";
        if (code == 3) return "غائم";
        if (code == 45 || code == 48) return "ضباب";
        if (code >= 51 && code <= 57) return "رذاذ";
        if (code >= 61 && code <= 67) return "مطر";
        if (code >= 71 && code <= 77) return "ثلج";
        if (code >= 80 && code <= 82) return "زخات مطر";
        if (code >= 95) return "عاصفة رعدية";
        return "";
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
