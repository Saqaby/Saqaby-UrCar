package sa.urcar.home;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Calendar;

/**
 * The quote card: a verse, a hadith or a saying of the salaf, a new one every hour (UrTV changes every 8 hours).
 * The hour of the year picks the kind (verse, hadith, salaf, in turn) and which one of it, so the same hour of the
 * same day always shows the same text. assets/quotes.json is made by src/quotes_for_app.py.
 */
final class Quotes {
    private JSONArray verses, hadith, salaf;

    Quotes(Context c) {
        try (InputStream in = c.getAssets().open("quotes.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            JSONObject j = new JSONObject(b.toString("UTF-8"));
            verses = j.getJSONArray("verses");
            hadith = j.getJSONArray("hadith");
            salaf = j.getJSONArray("salaf");
        } catch (Exception e) {
            verses = hadith = salaf = new JSONArray();
        }
    }

    /** {title, text, source} for that moment. */
    String[] at(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int hour = (c.get(Calendar.YEAR) * 366 + c.get(Calendar.DAY_OF_YEAR)) * 24 + c.get(Calendar.HOUR_OF_DAY);
        int kind = hour % 3, n = hour / 3;
        JSONArray list = kind == 0 ? verses : kind == 1 ? hadith : salaf;
        if (list.length() == 0) list = verses.length() > 0 ? verses : hadith;
        if (list.length() == 0) return new String[]{"", "", ""};
        JSONObject q = list.optJSONObject(Math.floorMod(n, list.length()));
        String title = list == verses ? "آية" : list == hadith ? "حديث" : "من أقوال السلف";
        String text = q.optString("t");
        if (list == verses) text = "﴿" + text + "﴾";
        return new String[]{title, text, Ui.ar(q.optString("s"))};
    }
}
