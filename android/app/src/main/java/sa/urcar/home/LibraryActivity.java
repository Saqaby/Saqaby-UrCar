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
 * «المكتبة الصوتية» (the headphones on the panel): the reciters on one side, each with «تنزيل الكل» (its progress, or a
 * tick when all 114 are on the screen) and «حذف»; the 114 surahs on the other, a surah playing at once by the reciter
 * chosen (Listen), with «إيقاف مؤقت» and «إيقاف» at the top. At the bottom, when nothing plays, «تابع» goes on with
 * the last surah from the second it stopped. The reciter is remembered.
 */
public class LibraryActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private Ui.Theme th;
    private LinearLayout reciters;
    private GridLayout surahs;
    private TextView now, pause, resume, space;
    private final TextView[] surahViews = new TextView[114];
    private String keptFor = "";   // the reciter whose downloaded surahs the frames show
    private final boolean[] kept = new boolean[114];
    private final java.util.List<Object[]> rows = new java.util.ArrayList<>();   // {reciter id, progress text, delete cell, download icon}
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
        LinearLayout bottom = new LinearLayout(this);   // go on from the last stop; the room left on the screen
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        resume = Ui.text(this, 24, Ui.medium(this), th.sandInk);
        resume.setPadding(26, 14, 26, 14);
        resume.setBackground(Ui.box(th.sand, 0, 14));
        resume.setOnClickListener(v -> { if (PanelService.live != null) PanelService.live.listenOn(); refresh(); });
        bottom.addView(resume);
        space = Ui.text(this, 19, Ui.regular(this), th.muted);
        LinearLayout.LayoutParams spl = new LinearLayout.LayoutParams(0, -2, 1);
        spl.setMarginStart(20);
        bottom.addView(space, spl);
        LinearLayout.LayoutParams bol = new LinearLayout.LayoutParams(-1, -2);
        bol.topMargin = 12;
        root.addView(bottom, bol);
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
        rows.clear();
        for (int i = 0; i < rec.length(); i++) {
            JSONObject r = rec.optJSONObject(i);
            final String id = r.optString("id"), name = r.optString("name"), server = r.optString("server");
            boolean on = id.equals(chosen());
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = chip(name, on, () -> { Prefs.put(this, "lib_reciter", id); build(); });
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
            row.addView(t, tl);
            // «تنزيل الكل»: an arrow, its progress («٢٣/١١٤») while it runs, a tick when all are here
            LinearLayout dl = iconCell(Ui.Icon.DOWNLOAD);
            TextView prog = Ui.text(this, 17, Ui.medium(this), th.sand);
            prog.setVisibility(View.GONE);
            dl.addView(prog);
            dl.setOnClickListener(v -> download(id, name, server));
            row.addView(dl, cellLp());
            LinearLayout del = iconCell(Ui.Icon.TRASH);
            del.setOnClickListener(v -> delete(id, name));
            row.addView(del, cellLp());
            rows.add(new Object[]{id, prog, del, dl.getChildAt(0)});
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
            l.bottomMargin = 10;
            reciters.addView(row, l);
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
            surahViews[i] = t;
        }
        keptFor = "";
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

    /** A gold frame around each surah of the chosen reciter that is on the screen (it plays without the internet). */
    private void frames() {
        String id = chosen();
        boolean all = !id.equals(keptFor);
        keptFor = id;
        for (int i = 0; i < surahViews.length; i++) {
            TextView t = surahViews[i];
            if (t == null) continue;
            boolean k = LibStore.file(this, id, i + 1).length() > 0;
            if (!all && k == kept[i]) continue;
            kept[i] = k;
            android.graphics.drawable.GradientDrawable g = Ui.box(th.panel, th.line, 14);
            if (k) g.setStroke(4, th.sand);
            t.setBackground(g);
        }
    }

    private LinearLayout iconCell(int kind) {
        LinearLayout cell = new LinearLayout(this);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(14, 10, 14, 10);
        cell.setBackground(Ui.box(th.panel, th.line, 14));
        cell.addView(new Ui.Icon(this, kind, th.sand), new LinearLayout.LayoutParams(40, 40));
        return cell;
    }
    private LinearLayout.LayoutParams cellLp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -1);
        l.setMarginStart(8);
        return l;
    }

    /** All 114 surahs of a reciter onto the screen, after saying how much room it needs; pressed again, it stops. */
    private void download(String id, String name, String server) {
        if (LibStore.running.containsKey(id)) { LibStore.downloadAll(this, id, name, server); refresh(); return; }
        if (LibStore.count(this, id) >= 114) { android.widget.Toast.makeText(this, "تلاوات " + name + " كاملة على الشاشة", android.widget.Toast.LENGTH_SHORT).show(); return; }
        new android.app.AlertDialog.Builder(this)
                .setTitle("تنزيل تلاوات " + name)
                .setMessage("تُنزَّل السور الـ١١٤ لتعمل بلا إنترنت، في المكتبة والختمة. تأخذ عادة بين نصف غيغابايت وغيغابايتين بحسب القارئ، "
                        + "ومن باقة الجوال إن كانت الشاشة متصلة به. المتاح على الشاشة: " + LibStore.gb(LibStore.free(this)) + ".")
                .setPositiveButton("نزّل", (d, w) -> { LibStore.downloadAll(this, id, name, server); refresh(); })
                .setNegativeButton("لا", null).show();
    }
    private void delete(String id, String name) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("حذف تلاوات " + name)
                .setMessage("تُحذف من الشاشة " + Ui.ar(LibStore.count(this, id)) + " سورة (" + LibStore.gb(LibStore.bytes(this, id)) + "). تبقى تعمل عبر الإنترنت.")
                .setPositiveButton("احذف", (d, w) -> { LibStore.delete(this, id); refresh(); })
                .setNegativeButton("لا", null).show();
    }

    private void refresh() {
        Listen l = listen();
        boolean on = l != null && l.on();
        now.setText(on ? "سورة " + l.surah + "، " + l.reciter + (l.paused() ? " (موقوفة)" : "") : "اختر القارئ ثم السورة");
        pause.setText(on && l.paused() ? "تابع" : "إيقاف مؤقت");
        pause.setAlpha(on ? 1f : .4f);
        String[] last = l == null ? null : l.last();
        resume.setVisibility(last == null ? View.GONE : View.VISIBLE);
        if (last != null) resume.setText("▶  تابع سورة " + last[0] + " بصوت " + last[1] + " من " + last[2]);
        space.setText("الإطار الذهبي: سورة على الشاشة تعمل بلا إنترنت.  المتاح على الشاشة " + LibStore.gb(LibStore.free(this)));
        frames();
        for (Object[] r : rows) {   // each reciter's download: its progress, a tick when complete; the bin when there is something
            String id = (String) r[0];
            Integer at = LibStore.running.get(id);
            int have = LibStore.count(this, id);
            TextView prog = (TextView) r[1];
            Ui.Icon icon = (Ui.Icon) r[3];
            prog.setVisibility(at != null ? View.VISIBLE : View.GONE);
            if (at != null) prog.setText(Ui.ar(have) + "/١١٤");
            icon.setVisibility(at != null ? View.GONE : View.VISIBLE);
            if (at == null) icon.set(have >= 114 ? Ui.Icon.DONE : Ui.Icon.DOWNLOAD, th.sand);
            ((View) r[2]).setVisibility(have > 0 || at != null ? View.VISIBLE : View.INVISIBLE);
        }
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
