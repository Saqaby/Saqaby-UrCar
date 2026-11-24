package sa.urcar.home;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * The city the car is in, offline: assets/cities.json, the Gulf's inhabited places with their Arabic names from
 * GeoNames (CC BY 4.0; src/cities_for_app.py). A place reaches as far as its size suggests (3 km for a town, 30 km
 * for Riyadh: 3 + √population / 60, at most 30), and the car is in the one whose reach it is deepest inside.
 */
final class Cities {
    private double[] lat = new double[0], lon = new double[0], reach = new double[0];
    private String[] name = new String[0];

    Cities(Context c) {
        try (InputStream in = c.getAssets().open("cities.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            JSONArray a = new JSONObject(b.toString("UTF-8")).getJSONArray("places");
            int n = a.length();
            lat = new double[n]; lon = new double[n]; reach = new double[n]; name = new String[n];
            for (int i = 0; i < n; i++) {
                JSONArray e = a.getJSONArray(i);
                name[i] = e.getString(0); lat[i] = e.getDouble(1); lon[i] = e.getDouble(2);
                reach[i] = Math.min(30, 3 + Math.sqrt(e.getDouble(3)) / 60);
            }
        } catch (Exception ignored) { }
    }

    /** The city at a place, or "" outside them all. */
    String at(double la, double lo) {
        String best = "";
        double depth = 1;
        for (int i = 0; i < name.length; i++) {
            if (Math.abs(lat[i] - la) > .3 || Math.abs(lon[i] - lo) > .35) continue;   // cheap first
            double d = Day.km(la, lo, lat[i], lon[i]) / reach[i];
            if (d < depth) { depth = d; best = name[i].equals("المدينة") ? "المدينة المنورة" : name[i]; }
        }
        return best;
    }
}
