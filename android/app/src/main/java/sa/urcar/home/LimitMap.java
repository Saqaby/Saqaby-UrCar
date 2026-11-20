package sa.urcar.home;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * The road's speed limit, offline: assets/limits.bin (src/limits_for_app.py; © OpenStreetMap contributors, ODbL), the
 * Gulf's main roads with their limit, in tiles of 0.05°. Copied out of the app once per version so it can be read in
 * place; only the tile index stays in memory, and the tile the car is in.
 */
final class LimitMap {
    private RandomAccessFile f;
    private int[] keys = new int[0], offs = new int[0], counts = new int[0];
    private long dataAt;
    private int tileKey = Integer.MIN_VALUE;
    private byte[] tile = new byte[0];
    int built;

    LimitMap(Context c) {
        try {
            File out = new File(c.getFilesDir(), "limits.bin");
            long ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime;
            if (!out.exists() || Prefs.of(c).getLong("limits_ver", 0) != ver) {
                try (InputStream in = c.getAssets().open("limits.bin"); FileOutputStream o = new FileOutputStream(out)) {
                    byte[] buf = new byte[65536];
                    for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
                }
                Prefs.of(c).edit().putLong("limits_ver", ver).apply();
            }
            f = new RandomAccessFile(out, "r");
            byte[] magic = new byte[4];
            f.readFully(magic);
            if (!new String(magic, "US-ASCII").equals("UCL1")) { f = null; return; }
            built = f.readInt();
            int n = f.readInt();
            byte[] idx = new byte[n * 12];
            f.readFully(idx);
            java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(idx);
            keys = new int[n]; offs = new int[n]; counts = new int[n];
            for (int i = 0; i < n; i++) { keys[i] = b.getInt(); offs[i] = b.getInt(); counts[i] = b.getInt(); }
            dataAt = 12 + (long) n * 12;
        } catch (Exception e) { f = null; }
    }

    /**
     * The limit (km/h) of the road within 25 m of the car, or 0. With the car's heading (≥ 0), a road that runs the same
     * way (within 35°) comes before a closer one across it (a bridge, a slip road); then the nearest.
     */
    int at(double lat, double lon, float bearing) {
        if (f == null) return 0;
        int tx = (int) Math.floor((lon + 180) / 0.05), ty = (int) Math.floor((lat + 90) / 0.05), key = ty * 7200 + tx;
        if (key != tileKey) {
            tileKey = key;
            int i = java.util.Arrays.binarySearch(keys, key);
            tile = new byte[0];
            if (i >= 0) try {
                tile = new byte[counts[i] * 10];
                f.seek(dataAt + offs[i]);
                f.readFully(tile);
            } catch (Exception e) { tile = new byte[0]; }
        }
        double ox = tx * 5000 - 18_000_000, oy = ty * 5000 - 9_000_000;
        double px = lon * 1e5 - ox, py = lat * 1e5 - oy;
        double mx = 1.1132 * Math.cos(Math.toRadians(lat)), my = 1.1054;   // metres in 1e-5°
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(tile);
        int best = 0, bestAligned = 0;
        double bestD = 25, bestAlignedD = 25;
        for (int s = 0; s + 10 <= tile.length; s += 10) {
            int lim = b.get(s) & 0xff;
            double x1 = (b.getShort(s + 2) - px) * mx, y1 = (b.getShort(s + 4) - py) * my;
            double x2 = (b.getShort(s + 6) - px) * mx, y2 = (b.getShort(s + 8) - py) * my;
            double dx = x2 - x1, dy = y2 - y1, len = dx * dx + dy * dy;
            double t = len == 0 ? 0 : Math.max(0, Math.min(1, -(x1 * dx + y1 * dy) / len));
            double d = Math.hypot(x1 + t * dx, y1 + t * dy);
            if (d >= 25) continue;
            if (d < bestD) { bestD = d; best = lim; }
            if (bearing >= 0 && len > 0) {
                double a = Math.toDegrees(Math.atan2(dx, dy));   // the segment's direction from north
                double diff = Math.abs(((a - bearing) % 180 + 270) % 180 - 90);   // either way along it
                if (diff < 35 && d < bestAlignedD) { bestAlignedD = d; bestAligned = lim; }
            }
        }
        return bestAligned > 0 ? bestAligned : best;
    }
}
