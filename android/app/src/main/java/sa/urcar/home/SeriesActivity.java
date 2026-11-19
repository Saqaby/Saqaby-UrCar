package sa.urcar.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * «السلاسل» in the audio library: series of talks the owner chose, published on YouTube by their makers. Their
 * episodes, in order (assets/series.json, series.yml), are listed here; one opens in the YouTube app at that very
 * episode, in its playlist, so YouTube goes on to the next by itself. «تابع» opens the last one: the one tapped here,
 * or the one YouTube was last seen playing (PanelService.seriesWatch). Nothing of their sound is copied or played here.
 */
public class SeriesActivity extends Activity {
    private Ui.Theme th;
    private LinearLayout list;
    private JSONArray series;
    private int part = -1;

    static JSONArray load(Context c) {
        try (java.io.InputStream in = c.getAssets().open("series.json")) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return new JSONObject(b.toString("UTF-8")).getJSONArray("series");
        } catch (Exception e) { return new JSONArray(); }
    }
    /** The episode's title without the speaker's name after it: «قصة النور (3) |1|». */
    static String shortTitle(String t) {
        String s = t.replaceAll("\\s*[|│ـ-]*\\s*(لل?شيخ|الشيخ)\\s*:?.*$", "").trim();
        return s.isEmpty() ? t : s;
    }
    /** {series, part, episode} of a video id, or null. */
    static int[] find(JSONArray series, String id) {
        for (int s = 0; s < series.length(); s++) {
            JSONArray parts = series.optJSONObject(s).optJSONArray("parts");
            for (int p = 0; p < parts.length(); p++) {
                JSONArray eps = parts.optJSONObject(p).optJSONArray("episodes");
                for (int e = 0; e < eps.length(); e++) if (eps.optJSONObject(e).optString("id").equals(id)) return new int[]{s, p, e};
            }
        }
        return null;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        th = new Ui.Theme(PanelService.live != null && PanelService.live.dayNow());
        series = load(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(th.bg);
        root.setPadding(36, 22, 36, 18);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, 32, Ui.medium(this), th.fg);
        title.setText("السلاسل");
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(chip("رجوع", false, this::finish));
        root.addView(top);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1) {{ topMargin = 14; }});
        setContentView(root);
    }
    @Override protected void onResume() { super.onResume(); build(); }

    private void build() {
        list.removeAllViews();
        String last = Prefs.of(this).getString("series_last", "");
        int[] at = find(series, last);
        if (series.length() == 0) { list.addView(note("لا سلاسل بعد.")); return; }
        JSONObject s = series.optJSONObject(at != null ? at[0] : 0);
        JSONArray parts = s.optJSONArray("parts");
        if (part < 0) part = at != null ? at[1] : 0;

        TextView name = Ui.text(this, 30, Ui.display(this), th.sand);
        name.setText(s.optString("name"));
        list.addView(name);
        TextView by = Ui.text(this, 20, Ui.regular(this), th.muted);
        by.setText(s.optString("by"));
        list.addView(by, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 2; }});

        // «تابع»: the last episode, big
        if (at != null) {
            JSONObject e = parts.optJSONObject(at[1]).optJSONArray("episodes").optJSONObject(at[2]);
            LinearLayout go = new LinearLayout(this);
            go.setGravity(Gravity.CENTER_VERTICAL);
            go.setPadding(26, 18, 26, 18);
            go.setBackground(Ui.box(th.sand, 0, 16));
            go.addView(new Ui.Icon(this, Ui.Icon.PLAY, th.sandInk), new LinearLayout.LayoutParams(34, 34));
            TextView t = Ui.text(this, 25, Ui.medium(this), th.sandInk);
            t.setText("تابع: " + parts.optJSONObject(at[1]).optString("name") + "، " + shortTitle(e.optString("title")));
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
            tl.setMarginStart(16);
            go.addView(t, tl);
            final String id = e.optString("id"), pl = parts.optJSONObject(at[1]).optString("playlist");
            go.setOnClickListener(v -> open(id, pl));
            list.addView(go, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 16; }});
        }

        // the parts as tabs
        LinearLayout tabs = new LinearLayout(this);
        for (int p = 0; p < parts.length(); p++) {
            final int pp = p;
            JSONObject po = parts.optJSONObject(p);
            TextView t = chip(po.optString("name") + "  (" + Ui.ar(po.optJSONArray("episodes").length()) + ")", p == part, () -> { part = pp; build(); });
            tabs.addView(t);
        }
        list.addView(tabs, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 18; }});

        // the episodes of the part chosen, the last one marked
        JSONObject po = parts.optJSONObject(part);
        JSONArray eps = po.optJSONArray("episodes");
        for (int e = 0; e < eps.length(); e++) {
            JSONObject ep = eps.optJSONObject(e);
            final String id = ep.optString("id"), pl = po.optString("playlist");
            boolean on = id.equals(last);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(22, 14, 22, 14);
            row.setBackground(on ? Ui.box(th.panel2, th.sand, 14) : Ui.box(th.panel, th.line, 14));
            TextView n = Ui.text(this, 22, Ui.medium(this), th.sand);
            n.setText(Ui.ar(e + 1));
            n.setGravity(Gravity.CENTER);
            row.addView(n, new LinearLayout.LayoutParams(52, -2));
            TextView t = Ui.text(this, 22, Ui.regular(this), th.fg);
            t.setText(shortTitle(ep.optString("title")));
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
            tl.setMarginStart(12);
            row.addView(t, tl);
            if (on) { TextView m = Ui.text(this, 18, Ui.medium(this), th.sand); m.setText("آخر ما شاهدت"); row.addView(m); }
            row.setOnClickListener(v -> open(id, pl));
            list.addView(row, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 8; }});
        }
        list.addView(note("تُفتح الحلقة في يوتيوب ضمن قائمتها، فينتقل يوتيوب إلى التالية وحده، وتتذكر الشاشة آخر حلقة شُغّلت. يتوقف الصوت إذا خرجت من يوتيوب، إلا مع اشتراك يوتيوب بريميوم."));
    }

    /** The episode in the YouTube app, in its playlist; the browser if it is missing. */
    private void open(String id, String playlist) {
        Prefs.put(this, "series_last", id);
        Uri u = Uri.parse("https://www.youtube.com/watch?v=" + id + "&list=" + playlist);
        PanelService.front = "com.google.android.youtube";
        try { startActivity(new Intent(Intent.ACTION_VIEW, u).setPackage("com.google.android.youtube")); }
        catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
            catch (Exception e2) { Toast.makeText(this, "لا يوجد يوتيوب ولا متصفح", Toast.LENGTH_LONG).show(); }
        }
    }

    private TextView note(String s) {
        TextView n = Ui.text(this, 18, Ui.regular(this), th.muted);
        n.setSingleLine(false);
        n.setText(s);
        n.setPadding(0, 16, 0, 8);
        return n;
    }
    private TextView chip(String text, boolean on, Runnable r) {
        TextView c = Ui.text(this, 22, Ui.medium(this), on ? th.sandInk : th.fg);
        c.setText(text);
        c.setPadding(24, 14, 24, 14);
        c.setBackground(on ? Ui.box(th.sand, 0, 14) : Ui.box(th.panel, th.line, 14));
        c.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(12);
        c.setLayoutParams(lp);
        return c;
    }
}
