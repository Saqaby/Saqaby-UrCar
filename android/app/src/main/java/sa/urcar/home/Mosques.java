package sa.urcar.home;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * «مسجد أمامك»: the Gulf's mosques, offline (assets/mosques.bin, src/mosques_for_app.py; © OpenStreetMap
 * contributors, ODbL), sorted by latitude, all in memory (a few hundred kilobytes).
 */
final class Mosques {
    private int[] lat = new int[0], lon = new int[0];
    private String[] name = new String[0];

    Mosques(Context c) {
        try (InputStream in = c.getAssets().open("mosques.bin")) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
            ByteBuffer b = ByteBuffer.wrap(o.toByteArray());
            byte[] magic = new byte[4];
            b.get(magic);
            if (!new String(magic, "US-ASCII").equals("UCM1")) return;
            b.getInt();
            int n = b.getInt();
            lat = new int[n]; lon = new int[n]; name = new String[n];
            for (int i = 0; i < n; i++) {
                lat[i] = b.getInt(); lon[i] = b.getInt();
                byte[] nm = new byte[b.get() & 0xff];
                b.get(nm);
                name[i] = new String(nm, "UTF-8");
            }
        } catch (Exception ignored) { }
    }

    /**
     * The nearest mosque ahead of the car: between 300 m and 8 km away, within 25° of its heading (seen from the car),
     * as {index, km}; or null. Ahead, not merely near: one behind would mean turning back.
     */
    double[] ahead(double la, double lo, float bearing) {
        if (bearing < 0 || lat.length == 0) return null;
        int from = lower((int) Math.round((la - 0.08) * 1e5)), to = lower((int) Math.round((la + 0.08) * 1e5));
        double best = 1e9; int bi = -1;
        for (int i = from; i < to; i++) {
            double mla = lat[i] / 1e5, mlo = lon[i] / 1e5;
            if (Math.abs(mlo - lo) > 0.09) continue;
            double k = Day.km(la, lo, mla, mlo);
            if (k < 0.3 || k > 8 || k >= best) continue;
            double diff = Math.abs(((Day.bearing(la, lo, mla, mlo) - bearing) % 360 + 540) % 360 - 180);
            if (diff < 25) { best = k; bi = i; }
        }
        return bi < 0 ? null : new double[]{bi, best};
    }
    double lat(int i) { return lat[i] / 1e5; }
    double lon(int i) { return lon[i] / 1e5; }
    String name(int i) { return name[i]; }

    private int lower(int v) {   // the first index with lat >= v
        int a = 0, b = lat.length;
        while (a < b) { int m = (a + b) >>> 1; if (lat[m] < v) a = m + 1; else b = m; }
        return a;
    }
}
