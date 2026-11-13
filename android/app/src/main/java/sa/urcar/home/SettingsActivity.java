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
    private LinearLayout list, side, content;
    private ScrollView body;
    private Ui.Theme th;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        th = new Ui.Theme(false);
        // the sections down the side (the start, RTL), the chosen one's cards beside them
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(th.bg);
        side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(28, 32, 28, 28);
        side.setBackgroundColor(th.panel);
        root.addView(side, new LinearLayout.LayoutParams(300, -1));
        body = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(32, 12, 32, 40);
        body.addView(content);
        root.addView(body, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root);
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

    /** The sections, each a tab of the side list: what the driver sees, the road, the Quran and the sounds, the screen, about. */
    private static final String[][] TABS = {{"screen", "الشاشة"}, {"road", "الملاحة والطريق"}, {"quran", "القرآن والأذكار"}, {"smart", "المزايا الذكية"}, {"device", "الجهاز والتطبيقات"}, {"about", "عن التطبيق"}};

    private void build() {
        String tab = Prefs.of(this).getString("set_tab", "screen");
        side.removeAllViews();
        TextView title = Ui.text(this, 32, Ui.display(this), th.fg);
        title.setText("الإعدادات");
        side.addView(title, new LinearLayout.LayoutParams(-2, -2) {{ bottomMargin = 24; }});
        for (String[] t : TABS) {
            boolean on = t[0].equals(tab);
            TextView b = Ui.text(this, 24, Ui.medium(this), on ? th.sandInk : th.fg);
            b.setText(t[1]);
            b.setPadding(24, 18, 24, 18);
            b.setBackground(on ? Ui.box(th.sand, 0, 14) : null);
            b.setOnClickListener(v -> { Prefs.put(this, "set_tab", t[0]); body.scrollTo(0, 0); build(); });
            side.addView(b, new LinearLayout.LayoutParams(-1, -2) {{ bottomMargin = 6; }});
        }
        side.addView(new android.view.View(this), new LinearLayout.LayoutParams(1, 0, 1));
        TextView back = chip("رجوع", false, this::finish);
        back.setGravity(android.view.Gravity.CENTER);
        side.addView(back, new LinearLayout.LayoutParams(-1, -2));

        content.removeAllViews();
        switch (tab) {
            case "road": road(); break;
            case "quran": quran(); break;
            case "smart": smart(); break;
            case "device": device(); break;
            case "about": about(); break;
            default: screen();
        }
    }

    private void screen() {
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

    }

    private void road() {
        head("تطبيق الملاحة");
        LinearLayout navRow = grid();
        String nav = Prefs.nav(this);
        for (String[] n : Prefs.NAV) {
            if (!Prefs.installed(this, n[0])) continue;
            navRow.addView(chip(n[1], n[0].equals(nav), () -> { Prefs.put(this, "nav", n[0]); changed(); }));
        }
        final String hud = Prefs.hudhud(this);   // found by its name on the screen
        if (!hud.isEmpty()) navRow.addView(chip("هدهد", hud.equals(nav), () -> { Prefs.put(this, "nav", hud); changed(); }));
        list.addView(navRow);

        String seen;
        synchronized (NotiListener.navSeen) { seen = String.join("\n", NotiListener.navSeen); }
        list.addView(note("تنبيهات الطريق من تطبيق الملاحة (كاميرا، سرعة، مطب…) تظهر في السطر البرتقالي إن كتبها في إشعاراته. آخر ما أرسله:\n"
                + (seen.isEmpty() ? "لا شيء بعد: شغّل الملاحة إلى وجهة ثم ارجع هنا." : seen)));

        head("الوجهات السريعة");
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

        head("حد السرعة للطريق");
        boolean limOn = Prefs.of(this).getBoolean("limit_on", true);
        LinearLayout lr = grid();
        lr.addView(chip("يظهر", limOn, () -> { Prefs.of(this).edit().putBoolean("limit_on", true).apply(); build(); }));
        lr.addView(chip("لا يظهر", !limOn, () -> { Prefs.of(this).edit().putBoolean("limit_on", false).apply(); build(); }));
        list.addView(lr);
        list.addView(note("لوحة بجانب السرعة، وتحمرّ السرعة إذا تجاوزتها. من خرائط OpenStreetMap للطرق الرئيسية في الخليج، محفوظة في التطبيق وتعمل بلا إنترنت، وتُحدَّث شهريًا. ليست كل الطرق فيها حدودها، وقد لا تطابق اللوحات: المرجع لوحات الطريق."));

        head("دفتر الطريق");
        list.addView(note(tripLog()));

    }

    private void quran() {
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

        head("ختمة السيارة");
        Khatma k = PanelService.live != null ? PanelService.live.khatmaPlayer() : new Khatma(this);
        list.addView(note("وصلت إلى " + Ui.ar(k.percent()) + "٪، سورة " + k.surahName() + (k.done() > 0 ? "، وأتممت " + Ui.ar(k.done()) + " ختمة" : "") + ". القارئ: " + k.reciterName() + "."));
        LinearLayout kr = grid();
        kr.addView(chip("تغيير القارئ", false, () -> {
            org.json.JSONArray rs = k.complete();   // a khatma needs all 114 surahs
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

        head("أصوات الأذكار");
        list.addView(note("ذكر الركوب عند بدء الرحلة، ودعاء السفر مرة واحدة حين تبتعد ٤٠ كم عن البيت (ولا يُعاد حتى ترجع دون ٤٠ كم ثم تبتعد من جديد)، ودعاء دخول المنزل عند الوصول إليه."));
        LinearLayout du = grid();
        for (String[] d : new String[][]{{"riding", "ذكر الركوب"}, {"travel", "دعاء السفر"}, {"home", "دعاء دخول المنزل"}}) {
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
        for (String[] d : new String[][]{{"riding", "ذكر الركوب"}, {"travel", "دعاء السفر"}, {"home", "دعاء دخول المنزل"}})
            du2.addView(chip("جرّب " + d[1], false, () -> { if (PanelService.live != null) PanelService.live.testDua(d[0]); }));
        list.addView(du2, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 12; }});
        LinearLayout du3 = grid();
        for (String[] d : new String[][]{{"riding", "ذكر الركوب"}, {"travel", "دعاء السفر"}, {"home", "دعاء دخول المنزل"}})
            if (Sound.duaFile(this, d[0]).exists())
                du3.addView(chip("أرجع " + d[1] + " إلى الصوت الأساسي", false, () -> { Sound.duaFile(this, d[0]).delete(); build(); }));
        if (du3.getChildCount() > 0) list.addView(du3, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 12; }});
        list.addView(note("في التطبيق صوتان أساسيان. ولك أن تستبدل أيًّا منهما بتسجيل على الشاشة، فيُحفظ داخل التطبيق ولا يُرفع إلى أي مكان. الصوت بمستوى صوت الأذان."));

    }

    /** يعمل / متوقف for one of the five extras. */
    private void toggle(String key, boolean def, Runnable after) {
        boolean on = Smart.on(this, key, def);
        LinearLayout g = grid();
        g.addView(chip("يعمل", on, () -> { Smart.set(this, key, true); if (after != null) after.run(); build(); }));
        g.addView(chip("متوقف", !on, () -> { Smart.set(this, key, false); if (after != null) after.run(); build(); }));
        list.addView(g);
    }

    private void smart() {
        head("توقّع الوجهة");
        toggle("predict", true, null);
        list.addView(note("عند بدء الرحلة يظهر سطر ذهبي تحت السرعة: «إلى العمل؟»، وضغطة واحدة تبدأ المسار في تطبيق الملاحة. التوقع من رحلاتك السابقة من المكان نفسه في مثل هذا اليوم والساعة، ويحتاج رحلتين متشابهتين على الأقل. قبل ذلك: من البيت صباح أيام العمل إلى العمل، ومن العمل بعد الظهر إلى البيت. يختفي السطر بعد ٤٥ ثانية."));

        head("أماكني المتكررة");
        toggle("places", true, null);
        java.util.List<Object[]> ps = Smart.places(this);
        if (!ps.isEmpty()) {
            LinearLayout g = grid();
            for (Object[] pl : ps) {
                final int idx = (Integer) pl[0];
                g.addView(chip(Smart.placeLabel(pl) + " (" + Ui.ar(pl[2]) + ")", !((String) pl[1]).isEmpty(), () -> {
                    EditText in = new EditText(this);
                    in.setText((String) pl[1]);
                    in.setHint("مثل: النادي، بيت الوالد");
                    in.setTextSize(26);
                    new android.app.AlertDialog.Builder(this).setTitle("اسم المكان").setView(in)
                            .setPositiveButton("حفظ", (d, w) -> { Smart.edit(this, idx, in.getText().toString(), false, false); build(); })
                            .setNegativeButton("احذفه", (d, w) -> { Smart.edit(this, idx, null, true, false); build(); }).show();
                }));
            }
            list.addView(g, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 12; }});
        }
        list.addView(note("المكان الذي تقف عنده ٣ مرات أو أكثر (غير البيت والعمل) يصير وجهة: تظهر نجمة بجانب الوجهات تفتح قائمتها، وتسألك الشاشة عن اسمه مرة واحدة وأنت واقف عنده. اضغط مكانًا هنا لتسميته أو حذفه. الأماكن والرحلات تبقى على الشاشة فقط."
                + (ps.isEmpty() ? " لا أماكن بعد." : "")));

        head("اللحاق بالصلاة");
        toggle("iqama", true, null);
        LinearLayout iq = grid();
        for (int i = 0; i < 5; i++) {
            final int k = Day.FIVE[i];
            final int m = Smart.iqama(this, k);
            iq.addView(chip(Day.NAMES[k] + " " + Ui.ar(m), false, () -> { Prefs.of(this).edit().putInt("iqama_" + k, m >= 30 ? 5 : m + 5).apply(); build(); }));
        }
        list.addView(iq, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 12; }});
        list.addView(note("بعد الأذان يصير الشريط: «العصر: الإقامة بعد ٨ دقائق». وإن كنت تسير وأمامك مسجد يظهر: «الإقامة بعد ٨ دقائق، والمسجد على ٤ دقائق: تلحق». الأرقام أعلاه دقائق الانتظار بين الأذان والإقامة: اضغط الصلاة لتغييرها بحسب مسجدك (من ٥ إلى ٣٠). التقدير تقريبي، ولا يُحسب للجمعة."));

        head("إيماءات الصوت");
        toggle("gestures", true, null);
        list.addView(note("في الشاشة الرئيسية بإصبعين معًا في أي مكان: لمسة توقف الصوت أو تعيده (الختمة، الإذاعة، التلاوة، أو تطبيق الصوت)، وسحبة إلى اليسار للمقطع التالي وإلى اليمين للسابق. لا تحتاج النظر إلى الشاشة."));

        head("الشريط العائم فوق الملاحة");
        boolean may = Settings.canDrawOverlays(this);
        toggle("strip", false, () -> {
            if (PanelService.live != null) PanelService.live.stripReset();
            if (Smart.on(this, "strip", false) && !Settings.canDrawOverlays(this))
                try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { Toast.makeText(this, "فعّل «العرض فوق التطبيقات» لسيارتك من إعدادات أندرويد", Toast.LENGTH_LONG).show(); }
        });
        LinearLayout sg = grid();
        sg.addView(chip("أعده إلى مكانه الأول", false, () -> { Strip.resetPlace(this); if (PanelService.live != null) PanelService.live.stripReset(); Toast.makeText(this, "أُعيد", Toast.LENGTH_SHORT).show(); }));
        list.addView(sg, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 12; }});
        list.addView(note((Smart.on(this, "strip", false) && !may ? "ينقصه إذن «العرض فوق التطبيقات»: اضغط «يعمل» مرة أخرى لفتح صفحته. " : "")
                + "حين تفتح تطبيق الملاحة من الشاشة الرئيسية يبقى فوقه شريط صغير: السرعة وحدّها، والصلاة القادمة، وزر الصوت. لمسه يرجع إلى الشاشة الرئيسية، وسحبه ينقله إلى حيث لا يغطي شيئًا."));
    }

    private void device() {
        head("تطبيقا البلوتوث والراديو");
        LinearLayout ap = grid();
        for (String[] d : new String[][]{{"bt_app", "البلوتوث"}, {"radio_app", "الراديو"}}) {
            String now = d[0].equals("bt_app") ? Device.btApp(this) : Device.radioApp(this);
            ap.addView(chip(d[1] + ": " + Device.label(this, now) + " (غيّره)", false,
                    () -> startActivity(new Intent(this, AppsActivity.class).putExtra("pick", d[0]))));
        }
        list.addView(ap);
        if (!Prefs.of(this).getString("bt_app", "").isEmpty() || !Prefs.of(this).getString("radio_app", "").isEmpty()) {
            LinearLayout ap2 = grid();
            ap2.addView(chip("ارجع للاختيار التلقائي", false, () -> { Prefs.of(this).edit().remove("bt_app").remove("radio_app").apply(); build(); }));
            list.addView(ap2, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 12; }});
        }
        list.addView(note("يُختاران تلقائيًا بحسب الشاشة، فإن أخطأ الاختيار فاختر التطبيق الصحيح. بلا تطبيق بلوتوث تُفتح إعدادات البلوتوث، وبلا راديو تُشغَّل الإذاعة عبر الإنترنت."));

        head("الجهاز والتحديثات");
        LinearLayout dev = grid();
        dev.addView(chip("إعدادات أندرويد", false, () -> startActivity(new Intent(Settings.ACTION_SETTINGS))));
        dev.addView(chip("الواجهة الأصلية", false, () -> {
            try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        }));
        dev.addView(chip("تحديث الآن", false, () -> new Thread(() -> Updater.check(this, true)).start()));
        list.addView(dev);
        boolean test = Prefs.of(this).getString("channel", "stable").equals("test");
        LinearLayout ch = grid();
        ch.addView(chip("التحديثات: المستقرة", !test, () -> { Prefs.put(this, "channel", "stable"); build(); }));
        ch.addView(chip("التحديثات: التجريبية", test, () -> { Prefs.put(this, "channel", "test"); build(); }));
        list.addView(ch, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 12; }});
        list.addView(note(test ? "التجريبية: كل تعديل يصل فور بنائه، للتجربة قبل الجميع." : "المستقرة: لا يصل إلا ما جُرِّب واعتُمد."));
        String upd = Prefs.of(this).getString("upd_last", "");
        if (!upd.isEmpty()) list.addView(note("آخر محاولة تحديث: " + upd));
        String crash = Crash.last(this);
        if (!crash.isEmpty()) {
            list.addView(note("آخر عطل أوقف التطبيق (صوّره وأرسله لصاحب التطبيق):"));
            TextView cr = Ui.text(this, 15, Ui.regular(this), th.fg);
            cr.setSingleLine(false);
            cr.setTextDirection(View.TEXT_DIRECTION_LTR);
            cr.setText(crash);
            cr.setPadding(16, 12, 16, 12);
            cr.setBackground(Ui.box(th.panel, th.line, 12));
            list.addView(cr, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout cl = grid();
            cl.addView(chip("امسح العطل", false, () -> { Crash.file(this).delete(); build(); }));
            list.addView(cl, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 8; }});
        }
    }

    private void about() {
        head("عن التطبيق");
        String v;
        try { v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { v = "?"; }
        list.addView(note("الإصدار " + v + ". التحديثات من github.com/Saqaby/Saqaby-UrCar"));
        list.addView(note("© ٢٠٢٦ سلمان الصقعبي. جميع الحقوق محفوظة."));
        list.addView(note("الطقس Open-Meteo.com، حدود السرعة © OpenStreetMap، المدن GeoNames.org، الخط IBM Plex Sans Arabic، التلاوات mp3quran.net."));
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

    /** A section: a card of its own with its title; what follows goes into it. */
    private void head(String t) {
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(28, 22, 28, 26);
        list.setBackground(Ui.box(th.panel, th.line, 18));
        TextView h = Ui.text(this, 25, Ui.medium(this), th.sand);
        h.setText(t);
        list.addView(h, new LinearLayout.LayoutParams(-2, -2) {{ bottomMargin = 14; }});
        content.addView(list, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 20; }});
    }
    private TextView note(String t) {
        TextView n = Ui.text(this, 19, Ui.regular(this), th.muted);
        n.setSingleLine(false);
        n.setText(t);
        n.setPadding(0, 12, 0, 8);
        return n;
    }
    /** A row of chips that wraps to the next line when it runs out of room. */
    private LinearLayout grid() { return new Flow(this); }
    static final class Flow extends LinearLayout {
        Flow(android.content.Context c) { super(c); }
        @Override protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws), x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                measureChild(ch, MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int cw = ch.getMeasuredWidth() + 14;
                if (x > 0 && x + cw > w) { x = 0; y += rowH + 12; rowH = 0; }
                x += cw; rowH = Math.max(rowH, ch.getMeasuredHeight());
            }
            setMeasuredDimension(w, y + rowH);
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l, x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
                if (x > 0 && x + cw + 14 > w + 14) { x = 0; y += rowH + 12; rowH = 0; }
                ch.layout(w - x - cw, y, w - x, y + chh);   // right to left
                x += cw + 14; rowH = Math.max(rowH, chh);
            }
        }
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
