package sa.urcar.home;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * «المكتبة الصوتية» (the headphones on the panel): the reciters on one side, the 114 surahs on the other; a surah
 * plays at once by the reciter chosen (Listen), with «إيقاف مؤقت» and «إيقاف» at the top. The reciter is remembered.
 */
public class LibraryActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private Ui.Theme th;
    private LinearLayout reciters;
    private GridLayout surahs;
    private TextView now, pause;
    private JSONArray rec, sur;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        th = new Ui.Theme(PanelService.live != null && PanelService.live.dayNow());
        Khatma k = PanelService.live != null ? PanelService.live.khatmaPlayer() : new Khatma(this);
        rec = k.reciters();
        sur = k.surahs();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(th.bg);
        root.setPadding(36, 22, 36, 18);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, 32, Ui.medium(this), th.fg);
        title.setText("المكتبة الصوتية");
        top.addView(title);
        now = Ui.text(this, 22, Ui.regular(this), th.sand);
        now.setSingleLine(true);
        now.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
        nl.setMarginStart(28);
        top.addView(now, nl);
        pause = chip("إيقاف مؤقت", false, () -> { Listen l = listen(); if (l != null) l.toggle(); refresh(); });
        top.addView(pause);
        top.addView(chip("إيقاف", false, () -> { Listen l = listen(); if (l != null) l.stop(); refresh(); }));
        top.addView(chip("رجوع", false, this::finish));
        root.addView(top);

        LinearLayout body = new LinearLayout(this);
        body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView rs = new ScrollView(this);
        reciters = new LinearLayout(this);
        reciters.setOrientation(LinearLayout.VERTICAL);
        rs.addView(reciters);
        body.addView(rs, new LinearLayout.LayoutParams(0, -1, 0.9f));
        ScrollView ss = new ScrollView(this);
        surahs = new GridLayout(this);
        surahs.setColumnCount(4);
        ss.addView(surahs);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, -1, 2.1f);
        sl.setMarginStart(24);
        body.addView(ss, sl);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, 0, 1);
        bl.topMargin = 16;
        root.addView(body, bl);
        setContentView(root);
        build();
    }

    private Listen listen() { return PanelService.live != null ? PanelService.live.libraryPlayer() : null; }
    private String chosen() {
        String id = Prefs.of(this).getString("lib_reciter", "");
        return id.isEmpty() && rec.length() > 0 ? rec.optJSONObject(0).optString("id") : id;
    }

    private void build() {
        reciters.removeAllViews();
        for (int i = 0; i < rec.length(); i++) {
            JSONObject r = rec.optJSONObject(i);
            boolean on = r.optString("id").equals(chosen());
            TextView t = chip(r.optString("name"), on, () -> { Prefs.put(this, "lib_reciter", r.optString("id")); build(); });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
            l.bottomMargin = 10;
            reciters.addView(t, l);
        }
        surahs.removeAllViews();
        for (int i = 0; i < sur.length(); i++) {
            final int s = i + 1;
            final String name = sur.optJSONObject(i).optString("name");
            TextView t = chip(Ui.ar(s) + ". " + name, false, () -> play(s, name));
            t.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams l = new GridLayout.LayoutParams();
            l.width = 0;
            l.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            l.setMargins(5, 5, 5, 5);
            surahs.addView(t, l);
        }
        refresh();
    }

    private void play(int s, String name) {
        PanelService ps = PanelService.live;
        if (ps == null) return;
        JSONObject r = null;
        for (int i = 0; i < rec.length(); i++) if (rec.optJSONObject(i).optString("id").equals(chosen())) r = rec.optJSONObject(i);
        if (r == null) return;
        ps.listen(r.optString("id"), r.optString("name"), r.optString("server"), s, name);
        refresh();
    }

    private void refresh() {
        Listen l = listen();
        boolean on = l != null && l.on();
        now.setText(on ? "سورة " + l.surah + "، " + l.reciter + (l.paused() ? " (موقوفة)" : "") : "اختر القارئ ثم السورة");
        pause.setText(on && l.paused() ? "تابع" : "إيقاف مؤقت");
        pause.setAlpha(on ? 1f : .4f);
    }
    private final Runnable tick = new Runnable() { @Override public void run() { refresh(); h.postDelayed(this, 1000); } };
    @Override protected void onResume() { super.onResume(); h.post(tick); }
    @Override protected void onPause() { h.removeCallbacks(tick); super.onPause(); }

    private TextView chip(String text, boolean on, Runnable r) {
        TextView t = Ui.text(this, 22, Ui.medium(this), on ? th.sandInk : th.fg);
        t.setText(text);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(22, 14, 22, 14);
        t.setBackground(on ? Ui.box(th.sand, 0, 14) : Ui.box(th.panel, th.line, 14));
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.setMarginStart(10);
        t.setLayoutParams(l);
        return t;
    }
}
