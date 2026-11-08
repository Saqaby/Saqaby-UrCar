package sa.urcar.home;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The arithmetic of the prayer times and of their cache, with nothing of Android in it, so a plain JVM can run it.
 * Written for UrCar from NOAA's published solar formulas (see compute()); nothing in it comes from another project.
 */
final class PrayerMath {
    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;

    private static final double RAD = Math.PI / 180;
    private static final Pattern CLOCK = Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})");

    private PrayerMath() {}

    /** ISNA: Fajr and Isha with the sun this far below the horizon (Aladhan's method 2). */
    static final double ISNA_ANGLE = 15;

    /** Isha is never before Maghrib: past midnight (a high latitude) it is the next day's clock time, not one already gone. */
    static long afterMaghrib(long maghrib, long isha) { return isha < maghrib ? isha + 86_400_000L : isha; }

    /** Local midnight of that day in that zone. */
    static long midnight(int y, int mo, int d, String tz) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(tz), Locale.US);
        c.clear();
        c.set(y, mo - 1, d, 0, 0, 0);
        return c.getTimeInMillis();
    }

    /**
     * The six times of that local day (Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha) as moments, each rounded to the minute.
     * The sun's place comes from NOAA's solar calculator formulas (Meeus, «Astronomical Algorithms»; the NOAA Global
     * Monitoring Laboratory publishes them for anyone's use), worked out again at each moment found, twice, so the
     * sun's motion during the day is taken in. Fajr and Isha: the sun 15° below the horizon (ISNA); sunrise and Maghrib:
     * its upper edge at the horizon with refraction (0.833°); Dhuhr: the sun at its highest; Asr: an object's shadow its
     * own length plus its noon shadow. Far north in summer, where the sun never sinks 15°, Fajr is 90 minutes before
     * sunrise and Isha 90 after Maghrib. Isha is an absolute moment: past midnight it falls on the next day.
     */
    static long[] compute(int y, int mo, int d, double lat, double lng, String tz, boolean ramadan) {
        long day0 = midnight(y, mo, d, tz);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        utc.clear();
        utc.set(y, mo - 1, d, 0, 0, 0);
        long base = utc.getTimeInMillis();   // minutes below are counted from this day's 00:00 UTC

        double noon = solarNoon(base, lng);
        double sunrise = event(base, lat, lng, -0.833, false, noon);
        double maghrib = event(base, lat, lng, -0.833, true, noon);
        double fajr = event(base, lat, lng, -ISNA_ANGLE, false, noon);
        double isha = event(base, lat, lng, -ISNA_ANGLE, true, noon);
        if (Double.isNaN(fajr)) fajr = sunrise - 90;
        if (Double.isNaN(isha)) isha = maghrib + 90;
        double asr = asr(base, lat, lng, noon);

        long[] at = new long[6];
        at[FAJR] = minute(base, fajr);
        at[SUNRISE] = minute(base, sunrise);
        at[DHUHR] = minute(base, noon);
        at[ASR] = minute(base, asr);
        at[MAGHRIB] = minute(base, maghrib);
        at[ISHA] = afterMaghrib(at[MAGHRIB], minute(base, isha));
        // keep every time on the local day that was asked (a far-off zone could put the UTC day's result a day out)
        for (int i = 0; i < 5; i++) {
            while (at[i] < day0) at[i] += 86_400_000L;
            while (at[i] >= day0 + 86_400_000L) at[i] -= 86_400_000L;
        }
        at[ISHA] = afterMaghrib(at[MAGHRIB], at[ISHA]);
        return at;
    }

    /** That many minutes after base, to the nearest whole minute. */
    private static long minute(long base, double minutes) { return base + Math.round(minutes) * 60_000L; }

    /** The sun at its highest, in minutes after base (UTC): 12:00 at the place's own longitude, less the equation of time. */
    private static double solarNoon(long base, double lng) {
        double t = 720 - 4 * lng;
        for (int i = 0; i < 3; i++) t = 720 - 4 * lng - sun(base, t)[1];
        return t;
    }

    /**
     * When the sun's centre is at that altitude (degrees; below the horizon is negative), before noon (morning) or
     * after it, in minutes after base; NaN when it does not reach it that day.
     */
    private static double event(long base, double lat, double lng, double altitude, boolean afternoon, double noon) {
        double t = noon;
        for (int i = 0; i < 3; i++) {
            double[] s = sun(base, t);
            double ha = hourAngle(lat, s[0], altitude);
            if (Double.isNaN(ha)) return Double.NaN;
            double n = 720 - 4 * lng - s[1];   // noon from the sun as it is at that moment
            t = n + (afternoon ? 4 * ha : -4 * ha);
        }
        return t;
    }

    /** Asr: the altitude at which a shadow is its object's length more than at noon, worked out at the moment itself. */
    private static double asr(long base, double lat, double lng, double noon) {
        double t = noon + 180;
        for (int i = 0; i < 3; i++) {
            double[] s = sun(base, t);
            double alt = Math.atan(1 / (1 + Math.tan(Math.abs(lat - s[0]) * RAD))) / RAD;
            double ha = hourAngle(lat, s[0], alt);
            if (Double.isNaN(ha)) return noon + 180;
            t = 720 - 4 * lng - s[1] + 4 * ha;
        }
        return t;
    }

    /** The hour angle (degrees) at which the sun has that altitude, or NaN. */
    private static double hourAngle(double lat, double decl, double altitude) {
        double c = (Math.sin(altitude * RAD) - Math.sin(lat * RAD) * Math.sin(decl * RAD)) / (Math.cos(lat * RAD) * Math.cos(decl * RAD));
        if (c < -1 || c > 1) return Double.NaN;
        return Math.acos(c) / RAD;
    }

    /** The sun's {declination in degrees, equation of time in minutes} at that many minutes after base (UTC). */
    private static double[] sun(long base, double minutes) {
        double jd = (base + minutes * 60_000.0) / 86_400_000.0 + 2440587.5;
        double T = (jd - 2451545.0) / 36525.0;   // Julian centuries from J2000.0
        double L0 = norm(280.46646 + T * (36000.76983 + T * 0.0003032));   // geometric mean longitude
        double M = 357.52911 + T * (35999.05029 - 0.0001537 * T);           // mean anomaly
        double e = 0.016708634 - T * (0.000042037 + 0.0000001267 * T);      // eccentricity of the orbit
        double C = Math.sin(M * RAD) * (1.914602 - T * (0.004817 + 0.000014 * T))
                + Math.sin(2 * M * RAD) * (0.019993 - 0.000101 * T) + Math.sin(3 * M * RAD) * 0.000289;   // equation of centre
        double omega = 125.04 - 1934.136 * T;
        double lambda = L0 + C - 0.00569 - 0.00478 * Math.sin(omega * RAD);   // apparent longitude
        double eps0 = 23 + (26 + (21.448 - T * (46.815 + T * (0.00059 - T * 0.001813))) / 60) / 60;
        double eps = eps0 + 0.00256 * Math.cos(omega * RAD);                 // obliquity, corrected
        double decl = Math.asin(Math.sin(eps * RAD) * Math.sin(lambda * RAD)) / RAD;
        double yy = Math.pow(Math.tan(eps * RAD / 2), 2);
        double eot = 4 / RAD * (yy * Math.sin(2 * L0 * RAD) - 2 * e * Math.sin(M * RAD) + 4 * e * yy * Math.sin(M * RAD) * Math.cos(2 * L0 * RAD)
                - 0.5 * yy * yy * Math.sin(4 * L0 * RAD) - 1.25 * e * e * Math.sin(2 * M * RAD));
        return new double[]{decl, eot};
    }

    private static double norm(double a) { a %= 360; return a < 0 ? a + 360 : a; }

    /** The preference key of the day's times: the date in the city's zone and the city itself, so a new city refetches. */
    static String key(long millis, double lat, double lng, String tz) {
        return "times_isna_" + format("yyyyMMdd", millis, tz) + "_" + String.format(Locale.US, "%.3f_%.3f", lat, lng) + "_" + tz;
    }

    /** The date Aladhan is asked for: the city's own day, not the TV's. */
    static String aladhanDate(long millis, String tz) { return format("dd-MM-yyyy", millis, tz); }

    /** "5:12" or "05:12 (+03)" -> {5, 12}; null when it is not a time. Aladhan puts a zone after the time on some days. */
    static int[] parseClock(String v) {
        if (v == null) return null;
        Matcher m = CLOCK.matcher(v);
        if (!m.find()) return null;
        int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
        return h < 24 && min < 60 ? new int[]{h, min} : null;
    }

    /** "HH:mm" of that moment in that zone. */
    static String clockOf(long at, String tz) { return format("HH:mm", at, tz); }

    private static String format(String pattern, long millis, String tz) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone(tz));
        return f.format(millis);
    }

}
