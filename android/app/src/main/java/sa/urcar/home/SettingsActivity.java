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

    private void build() {
        list.removeAllViews();
        LinearLayout top = new LinearLayout(this);
        TextView title = Ui.text(this, 36, Ui.medium(this), th.fg);
        title.setText("الإعدادات");
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(chip("رجوع", false, this::finish));
        list.addView(top);

        head("تطبيق الملاحة بجانب اللوحة");
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
        TextView fmInfo = note(fm == null ? "لا تردد معروف قرب موقعك؛ الزر يشغّل الإذاعة عبر الإنترنت. إن عرفت التردد فاكتبه هنا:"
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
            if (fm == null) { Toast.makeText(this, "لا مدينة في الجدول قرب موقعك بعد؛ أرسل التردد ليُضاف إلى الجدول", Toast.LENGTH_LONG).show(); return; }
            String city = (String) fm[0];
            Prefs.of(this).edit().putFloat("fm_own_" + city, v).apply();
            Toast.makeText(this, "حُفظ " + Ui.ar(v) + " لـ" + city, Toast.LENGTH_SHORT).show();
            changed();
        }));
        list.addView(fix);

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
