package sa.urcar.home;

import android.app.SearchManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The microphone on the panel: what the owner says, in Arabic, taken by Android's speech recognition and done here,
 * so that the way goes to the navigation app chosen in the settings (Hudhud) and not only to Google Maps:
 * «شغّل في يوتيوب سورة الكهف»، «ودّني إلى البيت»، «خذني إلى أقرب محطة»، «افتح واتساب»، «شغّل الختمة». What is not
 * understood goes to Google as a search (the Google app answers it, by voice too).
 */
final class Voice {
    private Voice() {}

    private static final Pattern YT = Pattern.compile("يوت?يوب|youtube", Pattern.CASE_INSENSITIVE);
    private static final Pattern GO = Pattern.compile("^(?:.*?)(?:ودّ?ي?ني|خذني|وصّ?لني|وجّ?هني|توجّ?ه|اتجه|نروح|روح|اذهب|أريد أروح|ابي اروح|أبي أروح|دلني|دلّني)\\s*(?:على|إلى|الى|ل|لـ)?\\s*(.+)$");
    private static final Pattern OPEN = Pattern.compile("^(?:افتح|إفتح|شغّ?ل)\\s+(?:لي\\s+)?(?:تطبيق\\s+)?(.+)$");

    /** Does the first thing of the recognizer's guesses that is understood; returns what to tell the owner. */
    static String run(PanelService s, List<String> heard) {
        String t = heard.isEmpty() ? "" : clean(heard.get(0));
        if (t.isEmpty()) return "لم أسمع شيئًا";

        if (YT.matcher(t).find()) {   // «افتح يوتيوب وابحث عن …»، «شغّل في يوتيوب …»
            String q = YT.matcher(t).replaceAll(" ").replaceAll("(?U)\\b(?:و?ابحث|بحث)\\s+عن\\b", " ")   // «عن» of «ابحث عن» only: «محاضرة عن الصبر» keeps its own
                    .replaceAll("(?U)\\b(?:افتح|إفتح|شغّ?ل|لي|في|على|و?ابحث|بحث|مقطع|فيديو|و)\\b", " ")
                    .replaceAll("\\s+", " ").trim();
            Intent i = q.isEmpty() ? s.getPackageManager().getLaunchIntentForPackage("com.google.android.youtube")
                    : new Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra(SearchManager.QUERY, q).putExtra("query", q);
            if (i == null || !s.startApp(i)) return "يوتيوب غير مثبّت";
            return q.isEmpty() ? "يوتيوب" : "يوتيوب: " + q;
        }

        Matcher g = GO.matcher(t);
        if (g.find()) {
            String d = g.group(1).replaceAll("^(?:ال)?(?:أقرب|اقرب)\\s+", "أقرب ").trim();
            if (d.matches(".*(?:البيت|بيتي|المنزل|منزلي).*")) { s.goPlace("home", "البيت"); return "إلى البيت"; }
            if (d.matches(".*(?:العمل|الدوام|الشغل|مكتبي).*")) { s.goPlace("work", "العمل"); return "إلى العمل"; }
            if (d.matches(".*(?:مسجد|جامع).*") && d.contains("أقرب")) { s.mosque(); return "أقرب مسجد"; }
            if (d.matches(".*(?:محطة|محطه|بنزين|وقود).*")) { s.station(); return "أقرب محطة"; }
            s.searchNearby(d.replace("أقرب ", ""), d);
            return "إلى " + d;
        }

        if (t.matches(".*(?:إذاعة|اذاعة|اذاعه)\\s*القرآن.*")) { s.radio(); return "إذاعة القرآن"; }
        if (t.matches(".*الختم[ةه].*")) { s.khatma(); return "الختمة"; }

        Matcher o = OPEN.matcher(t);
        if (o.find()) {
            String name = o.group(1).trim();
            String pkg = app(s, name);
            if (pkg != null) { s.startApp(s.getPackageManager().getLaunchIntentForPackage(pkg)); return "افتح " + name; }
        }

        // anything else: Google answers it
        Intent web = new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, t);
        if (s.startApp(web)) return "بحث: " + t;
        return "لم أفهم: " + t;
    }

    private static String clean(String t) { return t.replaceAll("[؟?.!،,]", " ").replaceAll("\\s+", " ").trim(); }

    /** An installed app whose name is said (Arabic or English), or null. */
    private static String app(PanelService s, String said) {
        PackageManager pm = s.getPackageManager();
        String want = said.replaceAll("^ال", "").toLowerCase();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            String label = ri.loadLabel(pm).toString().toLowerCase();
            if (ri.activityInfo.packageName.equals(s.getPackageName())) continue;
            if (label.contains(want) || want.contains(label)) return ri.activityInfo.packageName;
        }
        return null;
    }
}
