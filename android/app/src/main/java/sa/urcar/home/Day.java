package sa.urcar.home;

import android.icu.util.IslamicCalendar;
import android.icu.util.ULocale;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The day at the car's place: the prayer times (ISNA, PrayerMath from UrTV), the Hijri date (Umm al-Qura), the
 * Ramadan window, and the adhan countdown: from 30 minutes before each adhan, then the time since it for 20 minutes.
 */
final class Day {
    static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    static final int[] FIVE = {PrayerMath.FAJR, PrayerMath.DHUHR, PrayerMath.ASR, PrayerMath.MAGHRIB, PrayerMath.ISHA};
    static final int BEFORE_MIN = 30, AFTER_MIN = 20;

    double lat = 24.7136, lon = 46.6753;   // Riyadh until the first GPS fix
    private long[] times, tomorrow;
    private String timesKey = "";

    /** Recomputed when the day changes or the car moved 10 km. */
    void ensure(long now) {
        String tz = TimeZone.getDefault().getID();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        String key = c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR) + "@" + Math.round(lat * 10) + "," + Math.round(lon * 10) + tz;
        if (key.equals(timesKey)) return;
        timesKey = key;
        times = PrayerMath.compute(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), lat, lon, tz, false);
        c.add(Calendar.DAY_OF_MONTH, 1);
        tomorrow = PrayerMath.compute(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), lat, lon, tz, false);
    }
    long at(int k) { return times[k]; }
    String name(int k, long now) {
        if (k == PrayerMath.DHUHR) { Calendar c = Calendar.getInstance(); c.setTimeInMillis(now); if (c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) return "الجمعة"; }
        return NAMES[k];
    }

    /** The prayer whose adhan was less than 20 minutes ago, or -1. */
    int current(long now) {
        for (int k : FIVE) if (now >= times[k] && now < times[k] + AFTER_MIN * 60_000L) return k;
        return -1;
    }
    /** The next adhan: {prayer, its moment}; after Isha it is tomorrow's Fajr. */
    long[] next(long now) {
        for (int k : FIVE) if (times[k] > now) return new long[]{k, times[k]};
        return new long[]{PrayerMath.FAJR, tomorrow[PrayerMath.FAJR]};
    }

    /** The bar: «أذان العصر بعد ٢٤ دقيقة», «حان الآن أذان العصر», «العصر، مضى على الأذان ٧ دقائق», or null. */
    String bar(long now) {
        int cur = current(now);
        if (cur >= 0) {
            int since = (int) ((now - times[cur]) / 60_000L);
            return since < 1 ? "حان الآن أذان " + name(cur, now) : name(cur, now) + "، مضى على الأذان " + Ui.minutes(since);
        }
        long[] n = next(now);
        long left = n[1] - now;
        if (left <= BEFORE_MIN * 60_000L) return "أذان " + name((int) n[0], now) + " بعد " + Ui.remaining(Math.max(60, Math.round(left / 1000.0)));
        return null;
    }

    static String clockOf(long at) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        return Ui.clock12(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    /** Before sunrise or from Maghrib on. */
    boolean night(long now) { ensure(now); return now < times[PrayerMath.SUNRISE] || now >= times[PrayerMath.MAGHRIB]; }

    // ---------------------------------------------------------------- dates
    private static IslamicCalendar hijri(long at) {
        IslamicCalendar h = new IslamicCalendar(android.icu.util.TimeZone.getDefault(), ULocale.forLocale(new Locale("ar", "SA")));
        h.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        h.setTimeInMillis(at);
        return h;
    }
    private static final String[] HIJRI_MONTHS = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};
    private static final String[] GREG_MONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس",
            "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};
    private static final String[] WEEKDAYS = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};

    /** «٢٥ جمادى الأولى ١٤٤٨ هـ» */
    static String hijriText(long at) {
        IslamicCalendar h = hijri(at);
        return Ui.ar(h.get(IslamicCalendar.DAY_OF_MONTH)) + " " + HIJRI_MONTHS[h.get(IslamicCalendar.MONTH)] + " " + Ui.ar(h.get(IslamicCalendar.YEAR)) + " هـ";
    }
    /** «الخميس، ٥ نوفمبر ٢٠٢٦م» */
    static String gregText(long at) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        return WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + "، " + Ui.ar(c.get(Calendar.DAY_OF_MONTH)) + " " + GREG_MONTHS[c.get(Calendar.MONTH)] + " " + Ui.ar(c.get(Calendar.YEAR)) + "م";
    }

    /** In Ramadan «٥ رمضان، الإفطار ٥:٤٦ م، بعد …»; before it «باقٍ على رمضان ١٤٤٨: ٩٥ يومًا». */
    String ramadan(long now) {
        IslamicCalendar h = hijri(now);
        if (h.get(IslamicCalendar.MONTH) == 8) {
            long fajr = times[PrayerMath.FAJR], maghrib = times[PrayerMath.MAGHRIB], target; String l;
            if (now < fajr) { l = "الإمساك"; target = fajr; }
            else if (now < maghrib) { l = "الإفطار"; target = maghrib; }
            else { l = "الإمساك"; target = tomorrow[PrayerMath.FAJR]; }
            return Ui.ar(h.get(IslamicCalendar.DAY_OF_MONTH)) + " رمضان، " + l + " " + clockOf(target) + "، بعد " + Ui.remaining((target - now) / 1000);
        }
        for (int i = 1; i < 400; i++) {
            IslamicCalendar d = hijri(now + i * 86_400_000L);
            if (d.get(IslamicCalendar.MONTH) == 8 && d.get(IslamicCalendar.DAY_OF_MONTH) == 1)
                return "باقٍ على رمضان " + Ui.ar(d.get(IslamicCalendar.YEAR)) + ": " + Ui.ar(i) + (i >= 3 && i <= 10 ? " أيام" : " يومًا");
        }
        return "";
    }

    /** Days to the first of Ramadan, -1 during Ramadan (then ramadan() gives iftar and imsak). */
    int ramadanDays(long now) {
        if (hijri(now).get(IslamicCalendar.MONTH) == 8) return -1;
        for (int i = 1; i < 400; i++) {
            IslamicCalendar d = hijri(now + i * 86_400_000L);
            if (d.get(IslamicCalendar.MONTH) == 8 && d.get(IslamicCalendar.DAY_OF_MONTH) == 1) return i;
        }
        return -1;
    }

    /** The great-circle bearing from one place to another, degrees from north. */
    static float bearing(double la1, double lo1, double la2, double lo2) {
        double r = Math.PI / 180, dLo = (lo2 - lo1) * r;
        double y = Math.sin(dLo) * Math.cos(la2 * r), x = Math.cos(la1 * r) * Math.sin(la2 * r) - Math.sin(la1 * r) * Math.cos(la2 * r) * Math.cos(dLo);
        return (float) ((Math.atan2(y, x) / r + 360) % 360);
    }
    /** The sun's {altitude, azimuth from north} in degrees at a place and time (the low-precision almanac formulas, ~1°). */
    static double[] sun(double lat, double lon, long ms) {
        double r = Math.PI / 180, d = ms / 86_400_000.0 + 2440587.5 - 2451545.0;
        double g = (357.529 + 0.98560028 * d) * r, q = 280.459 + 0.98564736 * d;
        double L = (q + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g)) * r, e = (23.439 - 0.00000036 * d) * r;
        double ra = Math.atan2(Math.cos(e) * Math.sin(L), Math.cos(L)), dec = Math.asin(Math.sin(e) * Math.sin(L));
        double gmst = (18.697374558 + 24.06570982441908 * d) % 24;
        double H = (gmst * 15 + lon) * r - ra, la = lat * r;
        double alt = Math.asin(Math.sin(la) * Math.sin(dec) + Math.cos(la) * Math.cos(dec) * Math.cos(H));
        double az = (Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(la) - Math.tan(dec) * Math.cos(la)) / r + 180) % 360;
        return new double[]{alt / r, az};
    }

    /** The bearing to the Kaaba from the car's place, degrees from north (great circle). */
    float qibla() {
        double r = Math.PI / 180, la = lat * r, lo = lon * r, kla = 21.4225 * r, klo = 39.8262 * r;
        double y = Math.sin(klo - lo), x = Math.cos(la) * Math.tan(kla) - Math.sin(la) * Math.cos(klo - lo);
        return (float) ((Math.atan2(y, x) / r + 360) % 360);
    }

    /**
     * One reminder for the quote card, most important first, as UrTV's fasting card: tomorrow is Arafah, Ashura or
     * Tasu'a, one of the white days (13-15), or a Monday or Thursday (not in Ramadan, never on the days of Eid or
     * Tashreeq); else on Friday «سورة الكهف». null when there is none.
     */
    String remind(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        IslamicCalendar h = hijri(now), t = hijri(now + 86_400_000L);
        int tm = t.get(IslamicCalendar.MONTH) + 1, td = t.get(IslamicCalendar.DAY_OF_MONTH);
        boolean noFast = tm == 9 || (tm == 10 && td == 1) || (tm == 12 && td >= 10 && td <= 13) || h.get(IslamicCalendar.MONTH) == 8;
        if (!noFast) {
            if (tm == 12 && td == 9) return "غدًا يوم عرفة: يُستحب صيامه";
            if (tm == 1 && td == 10) return "غدًا عاشوراء: يُستحب صيامه";
            if (tm == 1 && td == 9) return "غدًا تاسوعاء: يُستحب صيامه مع عاشوراء";
            if (td >= 13 && td <= 15) return "غدًا من الأيام البيض: يُستحب صيامه";
            int wd = (c.get(Calendar.DAY_OF_WEEK) % 7) + 1;   // tomorrow
            if (wd == Calendar.MONDAY) return "غدًا الاثنين: يُستحب صيامه";
            if (wd == Calendar.THURSDAY) return "غدًا الخميس: يُستحب صيامه";
        }
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) return "اليوم الجمعة: سورة الكهف";
        return null;
    }

    // ---------------------------------------------------------------- the place
    static final Object[][] CITIES = {
            {"الرياض", 24.7136, 46.6753}, {"جدة", 21.4858, 39.1925}, {"مكة المكرمة", 21.4225, 39.8262}, {"المدينة المنورة", 24.4672, 39.6024},
            {"الدمام", 26.4207, 50.0888}, {"الخبر", 26.2172, 50.1971}, {"الظهران", 26.2361, 50.0393}, {"الأحساء", 25.3833, 49.5864},
            {"الجبيل", 27.0046, 49.6460}, {"بريدة", 26.3592, 43.9818}, {"حائل", 27.5114, 41.7208}, {"تبوك", 28.3835, 36.5662},
            {"أبها", 18.2164, 42.5053}, {"خميس مشيط", 18.3000, 42.7333}, {"نجران", 17.5656, 44.2289}, {"جازان", 16.8892, 42.5511},
            {"الطائف", 21.2703, 40.4158}, {"ينبع", 24.0895, 38.0618}, {"عرعر", 30.9753, 41.0381}, {"سكاكا", 29.9697, 40.2064},
            {"الكويت", 29.3759, 47.9774}, {"المنامة", 26.2285, 50.5860}, {"الدوحة", 25.2854, 51.5310}, {"دبي", 25.2048, 55.2708},
            {"أبوظبي", 24.4539, 54.3773}, {"مسقط", 23.5880, 58.3829},
    };
    static double km(double la1, double lo1, double la2, double lo2) {
        double r = Math.PI / 180, dLa = (la2 - la1) * r, dLo = (lo2 - lo1) * r;
        double h = Math.pow(Math.sin(dLa / 2), 2) + Math.cos(la1 * r) * Math.cos(la2 * r) * Math.pow(Math.sin(dLo / 2), 2);
        return 12742 * Math.asin(Math.sqrt(h));
    }
    /** «الرياض» within 40 km, «قرب حائل» within 120, else empty (no name is better than a wrong one). */
    String placeName() {
        String best = ""; double d = 1e9;
        for (Object[] c : CITIES) { double k = km(lat, lon, (Double) c[1], (Double) c[2]); if (k < d) { d = k; best = (String) c[0]; } }
        return d < 40 ? best : d < 120 ? "قرب " + best : "";
    }
}
