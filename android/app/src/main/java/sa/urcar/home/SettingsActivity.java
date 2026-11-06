package sa.urcar.home;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** «الإعدادات»: the navigation app, the driver's side, day/night, the Quran radio, Android's settings, the update. */
public class SettingsActivity extends Activity {
    private LinearLayout list;
    private Ui.Theme th;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        list.setPadding(48, 32, 48, 48);
        sv.addView(list);
        th = new Ui.Theme(false);
        sv.setBackgroundColor(th.bg);
        setContentView(sv);
    }
    @Override protected void onResume() { super.onResume(); build(); }

    private String pending = "";
    /** The chosen recording is copied into the app, so it plays without the file or the flash drive. */
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != 7 || result != RESULT_OK || data == null || data.getData() == null || pending.isEmpty()) return;
        java.io.File to = Sound.duaFile(this, pending);
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData());
             java.io.OutputStream out = new java.io.FileOutputStream(to)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            Toast.makeText(this, "حُفظ الصوت", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            to.delete();
            Toast.makeText(this, "لم يُحفظ: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
        build();
    }

    private void build() {
        list.removeAllViews();
        LinearLayout top = new LinearLayout(this);
        TextView title = Ui.text(this, 36, Ui.medium(this), th.fg);
        title.setText("الإعدادات");
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(chip("رجوع", false, this::finish));
        list.addView(top);

        head("تطبيق الملاحة (يُفتح من بطاقة السرعة)");
        LinearLayout navRow = grid();
        String nav = Prefs.nav(this);
        for (String[] n : Prefs.NAV) {
            if (!Prefs.installed(this, n[0])) continue;
            navRow.addView(chip(n[1], n[0].equals(nav), () -> { Prefs.put(this, "nav", n[0]); changed(); }));
        }
        list.addView(navRow);

        head("جهة السائق");
        LinearLayout side = grid();
        String s = Prefs.side(this);
        side.addView(chip("يسار", s.equals("left"), () -> { Prefs.put(this, "side", "left"); changed(); }));
        side.addView(chip("يمين", s.equals("right"), () -> { Prefs.put(this, "side", "right"); changed(); }));
        list.addView(side);

        head("المظهر");
        LinearLayout look = grid();
        String t = Prefs.theme(this);
        look.addView(chip("تلقائي (نهاري من الشروق إلى المغرب)", t.equals("auto"), () -> { Prefs.put(this, "theme", "auto"); changed(); }));
        look.addView(chip("نهاري", t.equals("light"), () -> { Prefs.put(this, "theme", "light"); changed(); }));
        look.addView(chip("ليلي", t.equals("dark"), () -> { Prefs.put(this, "theme", "dark"); changed(); }));
        list.addView(look);

        head("إذاعة القرآن");
        LinearLayout radio = grid();
        String r = Prefs.radioSource(this);
        radio.addView(chip("FM من راديو الشاشة", r.equals("fm"), () -> { Prefs.put(this, "radio", "fm"); changed(); }));
        radio.addView(chip("عبر الإنترنت", r.equals("net"), () -> { Prefs.put(this, "radio", "net"); changed(); }));
        list.addView(radio);
        float lat = Prefs.of(this).getFloat("lat", 24.7136f), lon = Prefs.of(this).getFloat("lon", 46.6753f);
        Object[] fm = Net.fmHere(this, lat, lon);
        TextView fmInfo = note(fm == null ? "لا تردد معروف قرب موقعك؛ الزر يشغّل الإذاعة عبر الإنترنت. اكتب التردد هنا ليُحفظ لهذا المكان:"
                : "التردد هنا (" + fm[0] + "): " + Ui.ar(String.format(java.util.Locale.US, "%.1f", ((Number) fm[1]).doubleValue()))
                + (Boolean.TRUE.equals(fm[2]) ? "" : "، غير مؤكد. صحّحه إن لم يكن الإذاعة:"));
        list.addView(fmInfo);
        LinearLayout fix = grid();
        EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        in.setHint("مثل 100.0");
        in.setTextSize(24);
        fix.addView(in, new LinearLayout.LayoutParams(260, -2));
        fix.addView(chip("حفظ", false, () -> {
            float v;
            try { v = Float.parseFloat(in.getText().toString().replace('٫', '.')); } catch (Exception e) { v = 0; }
            if (v < 87.5f || v > 108f) { Toast.makeText(this, "التردد بين 87.5 و108", Toast.LENGTH_SHORT).show(); return; }
            Day here = new Day();
            here.lat = lat; here.lon = lon;
            String city = here.placeName().isEmpty() ? (fm != null ? (String) fm[0] : "موقعك") : here.placeName();
            Net.saveMine(this, city, lat, lon, v);   // kept for this place (80 km around it), table or not
            Toast.makeText(this, "حُفظ " + Ui.ar(v) + " لـ" + city, Toast.LENGTH_SHORT).show();
            changed();
        }));
        list.addView(fix);

        head("الوجهات السريعة (تحت السرعة في الشاشة الرئيسية)");
        LinearLayout dest = grid();
        for (String[] d : new String[][]{{"home", "البيت"}, {"work", "العمل"}}) {
            boolean saved = Prefs.of(this).getFloat(d[0] + "_lat", 999) < 90;
            dest.addView(chip("احفظ موقعي الآن كـ" + d[1] + (saved ? " (محفوظ)" : ""), saved, () -> {
                Prefs.of(this).edit().putFloat(d[0] + "_lat", Prefs.of(this).getFloat("lat", 999)).putFloat(d[0] + "_lon", Prefs.of(this).getFloat("lon", 999)).apply();
                Toast.makeText(this, "حُفظ " + d[1] + " على موقع السيارة الآن", Toast.LENGTH_SHORT).show();
                build();
            }));
        }
        list.addView(dest);
        list.addView(note("قف عند البيت أو العمل واضغط الزر، فيُحفظ موقع السيارة من GPS."));

        head("الأذان في السيارة");
        LinearLayout ad = grid();
        String am = Prefs.of(this).getString("adhan", "full");
        ad.addView(chip("كاملًا", am.equals("full"), () -> { Prefs.put(this, "adhan", "full"); build(); }));
        ad.addView(chip("التكبير الأول فقط", am.equals("takbir"), () -> { Prefs.put(this, "adhan", "takbir"); build(); }));
        ad.addView(chip("بلا صوت (الشريط فقط)", am.equals("silent"), () -> { Prefs.put(this, "adhan", "silent"); build(); }));
        list.addView(ad);
        LinearLayout vol = grid();
        float av = Prefs.of(this).getFloat("adhan_vol", 1f);
        for (Object[] v : new Object[][]{{"منخفض", .35f}, {"متوسط", .65f}, {"عالٍ", 1f}}) {
            float f = (Float) v[1];
            vol.addView(chip("الصوت " + v[0], Math.abs(av - f) < .01, () -> { Prefs.of(this).edit().putFloat("adhan_vol", f).apply(); build(); }));
        }
        list.addView(vol, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 12; }});

        head("صوت ذكر الركوب ودعاء السفر (يُشغَّل عند بدء الرحلة)");
        LinearLayout du = grid();
        for (String[] d : new String[][]{{"riding", "ذكر الركوب"}, {"travel", "دعاء السفر"}}) {
            boolean have = Sound.duaFile(this, d[0]).exists();
            du.addView(chip(have ? d[1] + ": صوتك (استبدله)" : d[1] + ": الصوت الأساسي (استبدله بصوتك)", have, () -> {
                pending = d[0];
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*");
                try { startActivityForResult(i, 7); } catch (Exception e) { Toast.makeText(this, "لا يوجد مدير ملفات لاختيار الصوت", Toast.LENGTH_LONG).show(); }
            }));
        }
        list.addView(du);
        LinearLayout du2 = grid();
        boolean on = Prefs.of(this).getBoolean("dua_sound", true);
        du2.addView(chip("الصوت يعمل", on, () -> { Prefs.of(this).edit().putBoolean("dua_sound", true).apply(); build(); }));
        du2.addView(chip("بلا صوت (النص فقط)", !on, () -> { Prefs.of(this).edit().putBoolean("dua_sound", false).apply(); build(); }));
        for (String[] d : new String[][]{{"riding", "ذكر الركوب"}, {"travel", "دعاء السفر"}})
            du2.addView(chip("جرّب " + d[1], false, () -> { if (PanelService.live != null) PanelService.live.testDua(d[0]); }));
        list.addView(du2, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 12; }});
        LinearLayout du3 = grid();
        for (String[] d : new String[][]{{"riding", "ذكر الركوب"}, {"travel", "دعاء السفر"}})
            if (Sound.duaFile(this, d[0]).exists())
                du3.addView(chip("أرجع " + d[1] + " إلى الصوت الأساسي", false, () -> { Sound.duaFile(this, d[0]).delete(); build(); }));
        if (du3.getChildCount() > 0) list.addView(du3, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 12; }});
        list.addView(note("في التطبيق صوتان أساسيان. ولك أن تستبدل أيًّا منهما بتسجيل على الشاشة، فيُحفظ داخل التطبيق ولا يُرفع إلى أي مكان. الصوت بمستوى صوت الأذان."));

        head("ختمة السيارة");
        Khatma k = PanelService.live != null ? PanelService.live.khatmaPlayer() : new Khatma(this);
        list.addView(note("وصلت إلى " + Ui.ar(k.percent()) + "٪، سورة " + k.surahName() + (k.done() > 0 ? "، وأتممت " + Ui.ar(k.done()) + " ختمة" : "") + ". القارئ: " + k.reciterName() + "."));
        LinearLayout kr = grid();
        kr.addView(chip("تغيير القارئ", false, () -> {
            org.json.JSONArray rs = k.reciters();
            String[] names = new String[rs.length()];
            for (int i = 0; i < rs.length(); i++) names[i] = rs.optJSONObject(i).optString("name");
            new android.app.AlertDialog.Builder(this).setTitle("القارئ").setItems(names, (d, i) -> { k.setReciter(rs.optJSONObject(i).optString("id")); build(); }).show();
        }));
        boolean ka = Prefs.of(this).getBoolean("khatma_auto", true);
        kr.addView(chip("تبدأ وحدها مع الرحلة", ka, () -> { Prefs.of(this).edit().putBoolean("khatma_auto", true).apply(); build(); }));
        kr.addView(chip("بالزر فقط", !ka, () -> { Prefs.of(this).edit().putBoolean("khatma_auto", false).apply(); build(); }));
        list.addView(kr);
        LinearLayout kr2 = grid();
        kr2.addView(chip("ابدأ من الفاتحة", false, () -> { k.restart(); build(); }));
        list.addView(kr2, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 12; }});
        String kerr = Prefs.of(this).getString("k_err", "");
        if (!kerr.isEmpty()) list.addView(note("آخر ما أوقف الختمة: " + kerr));
        list.addView(note("تبدأ مع الرحلة إن لم يكن صوت آخر يعمل، وتتوقف بعد ٣ دقائق من الوقوف، وتكمل حين تتحرك. تقف مع المكالمة أو أي صوت آخر (مقطع، موسيقى الجوال، إشعار) وتكمل وحدها بعد انتهائه، راجعةً ٣ ثوانٍ. وإن بقيت تنتظر فزرها يكملها. التلاوات من mp3quran.net وتحتاج إنترنت."));

        head("سجل الصوت");
        list.addView(note("ما تخبر به الشاشة أثناء المكالمة وصوت الجوال، لمعرفة ما يوقف الختمة. جرّب مكالمة والختمة تعمل، ثم صوّر هذا السجل."));
        TextView sl = Ui.text(this, 16, Ui.regular(this), th.fg);
        sl.setSingleLine(false);
        sl.setTextDirection(View.TEXT_DIRECTION_RTL);
        sl.setText(SoundLog.last(this, 45));
        sl.setPadding(16, 12, 16, 12);
        sl.setBackground(Ui.box(th.panel, th.line, 12));
        list.addView(sl, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout sr = grid();
        sr.addView(chip("حدّث", false, this::build));
        sr.addView(chip("امسح السجل", false, () -> { SoundLog.clear(this); build(); }));
        list.addView(sr, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 12; }});

        head("دفتر الطريق");
        list.addView(note(tripLog()));

        head("الجهاز");
        LinearLayout dev = grid();
        dev.addView(chip("إعدادات أندرويد", false, () -> startActivity(new Intent(Settings.ACTION_SETTINGS))));
        dev.addView(chip("الواجهة الأصلية", false, () -> {
            try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        }));
        dev.addView(chip("تحديث الآن", false, () -> new Thread(() -> Updater.check(this, true)).start()));
        list.addView(dev);
        String v;
        try { v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { v = "?"; }
        list.addView(note("الإصدار " + v + ". التحديثات من github.com/Saqaby/Saqaby-UrCar"));
        list.addView(note("© ٢٠٢٦ سلمان الصقعبي (Salman Alsaqabi)، linkedin.com/in/سلمان-الصقعبي-78a098159. جميع الحقوق محفوظة. الاستخدام الشخصي مجاني؛ ولا يُنسخ التطبيق أو يُعدَّل أو يُعاد نشره بلا إذن.\n"
                + "بيانات الطقس من Open-Meteo.com (CC BY 4.0). الخط IBM Plex Sans Arabic (OFL). التلاوات من mp3quran.net. التفاصيل في LICENSE وTHIRD-PARTY.md في المستودع."));
    }

    /** «هذا الشهر: ٤٢ رحلة، ١٢٣٠ كم، ٣١ ساعة» and the month before, from the trips kept on the screen. */
    private String tripLog() {
        org.json.JSONArray a;
        try { a = new org.json.JSONArray(Prefs.of(this).getString("trips", "[]")); } catch (Exception e) { a = new org.json.JSONArray(); }
        java.util.Calendar c = java.util.Calendar.getInstance();
        int ym = c.get(java.util.Calendar.YEAR) * 12 + c.get(java.util.Calendar.MONTH);
        int[] n = new int[2]; double[] km = new double[2]; long[] ms = new long[2];
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONArray t = a.optJSONArray(i);
            if (t == null) continue;
            c.setTimeInMillis(t.optLong(0));
            int k = ym - (c.get(java.util.Calendar.YEAR) * 12 + c.get(java.util.Calendar.MONTH));
            if (k < 0 || k > 1) continue;
            n[k]++; km[k] += t.optDouble(2); ms[k] += Math.max(0, t.optLong(1) - t.optLong(0));
        }
        String[] lbl = {"هذا الشهر", "الشهر الماضي"};
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < 2; k++) {
            if (k > 0) b.append("\n");
            b.append(lbl[k]).append(": ");
            if (n[k] == 0) { b.append("لا رحلات بعد"); continue; }
            b.append(Ui.ar(n[k])).append(n[k] >= 3 && n[k] <= 10 ? " رحلات" : " رحلة").append("، ").append(Ui.ar(Math.round(km[k]))).append(" كم، ")
             .append(Ui.remaining(ms[k] / 1000));
        }
        b.append("\nتُسجَّل الرحلة حين تنتهي (وقوف ٢٥ دقيقة أو أكثر)، وتبقى على الشاشة فقط.");
        return b.toString();
    }

    private void changed() {
        if (PanelService.live != null) PanelService.live.applyPrefs();
        build();
    }

    private void head(String t) {
        TextView h = Ui.text(this, 22, Ui.regular(this), th.muted);
        h.setText(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = 30;
        lp.bottomMargin = 10;
        list.addView(h, lp);
    }
    private TextView note(String t) {
        TextView n = Ui.text(this, 19, Ui.regular(this), th.muted);
        n.setSingleLine(false);
        n.setText(t);
        n.setPadding(0, 12, 0, 8);
        return n;
    }
    private LinearLayout grid() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.HORIZONTAL);
        return g;
    }
    private TextView chip(String text, boolean on, Runnable r) {
        TextView c = Ui.text(this, 24, Ui.medium(this), on ? th.sand : th.fg);
        c.setText(text);
        c.setPadding(28, 20, 28, 20);
        c.setBackground(Ui.box(th.panel, on ? th.sand : th.line, 14));
        c.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(14);
        c.setLayoutParams(lp);
        return c;
    }
}
