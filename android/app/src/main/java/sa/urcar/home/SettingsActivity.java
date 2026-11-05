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
            du.addView(chip("اختر ملف " + d[1] + (have ? " (محفوظ)" : ""), have, () -> {
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
            if (Sound.duaFile(this, d[0]).exists())
                du2.addView(chip("جرّب " + d[1], false, () -> { if (PanelService.live != null) PanelService.live.testDua(d[0]); }));
        list.addView(du2, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 12; }});
        list.addView(note("انقل الملفين إلى الشاشة (فلاش أو تنزيل) ثم اختر كلًّا منهما. يُحفظان داخل التطبيق، ولا يُرفعان إلى أي مكان. الصوت بمستوى صوت الأذان."));

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
