package sa.urcar.home;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * «السلاسل» in the audio library: series of talks, each part a YouTube playlist the owner chose, opened in the YouTube
 * app (their sound is not copied or played here: they belong to their makers and are published on YouTube). The part
 * last opened is marked, to go on from it.
 */
public class SeriesActivity extends Activity {
    /** {series, part's name, playlist id}. */
    static final String[][] PARTS = {
            {"قصة النور", "الجزء الأول", "PLHJHnMgllLINSe_mNRxIbRmXQ5dJJSLuu"},
            {"قصة النور", "الجزء الثاني", "PLHJHnMgllLIOOC7mJnqOYRld8J3izWLau"},
            {"قصة النور", "الجزء الثالث", "PLHJHnMgllLIP422lV0MdJtMuWSy9VD3Bz"},
    };
    static final String[] BY = {"قصة النور", "الشيخ فهد الجريوي، في سيرة النبي ﷺ"};
    private Ui.Theme th;
    private LinearLayout list;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        th = new Ui.Theme(PanelService.live != null && PanelService.live.dayNow());
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
        TextView back = Ui.text(this, 24, Ui.medium(this), th.fg);
        back.setText("رجوع");
        back.setPadding(28, 16, 28, 16);
        back.setBackground(Ui.box(th.panel, th.line, 14));
        back.setOnClickListener(v -> finish());
        top.addView(back);
        root.addView(top);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1) {{ topMargin = 18; }});
        setContentView(root);
    }
    @Override protected void onResume() { super.onResume(); build(); }

    private void build() {
        list.removeAllViews();
        String last = Prefs.of(this).getString("series_last", "");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(30, 24, 30, 28);
        card.setBackground(Ui.box(th.panel, th.line, 18));
        TextView name = Ui.text(this, 30, Ui.display(this), th.sand);
        name.setText(BY[0]);
        TextView by = Ui.text(this, 21, Ui.regular(this), th.muted);
        by.setText(BY[1]);
        card.addView(name);
        card.addView(by, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 4; bottomMargin = 18; }});
        LinearLayout row = new LinearLayout(this);
        for (String[] p : PARTS) {
            boolean on = p[2].equals(last);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(16, 22, 16, 22);
            cell.setBackground(on ? Ui.box(th.sand, 0, 16) : Ui.box(th.panel2, th.line, 16));
            Ui.Icon ic = new Ui.Icon(this, Ui.Icon.YOUTUBE, on ? th.sandInk : th.sand);
            cell.addView(ic, new LinearLayout.LayoutParams(46, 46));
            TextView t = Ui.text(this, 25, Ui.medium(this), on ? th.sandInk : th.fg);
            t.setText(p[1]);
            cell.addView(t, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 8; }});
            TextView s = Ui.text(this, 18, Ui.regular(this), on ? th.sandInk : th.muted);
            s.setText(on ? "آخر ما فتحت" : "يفتح في يوتيوب");
            cell.addView(s, new LinearLayout.LayoutParams(-2, -2) {{ topMargin = 2; }});
            cell.setOnClickListener(v -> open(p[2]));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1);
            if (row.getChildCount() > 0) cl.setMarginStart(16);
            row.addView(cell, cl);
        }
        card.addView(row);
        list.addView(card, new LinearLayout.LayoutParams(-1, -2));
        TextView note = Ui.text(this, 18, Ui.regular(this), th.muted);
        note.setSingleLine(false);
        note.setText("تُفتح الحلقات في تطبيق يوتيوب من قناتها، ويتابع يوتيوب من حيث توقفت في كل حلقة. يتوقف الصوت إذا خرجت من يوتيوب، إلا مع اشتراك يوتيوب بريميوم.");
        list.addView(note, new LinearLayout.LayoutParams(-1, -2) {{ topMargin = 16; }});
    }

    /** The playlist in the YouTube app; the browser if it is missing. */
    private void open(String id) {
        Prefs.put(this, "series_last", id);
        Uri u = Uri.parse("https://www.youtube.com/playlist?list=" + id);
        Intent i = new Intent(Intent.ACTION_VIEW, u).setPackage("com.google.android.youtube");
        PanelService.front = "com.google.android.youtube";
        try { startActivity(i); }
        catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
            catch (Exception e2) { Toast.makeText(this, "لا يوجد يوتيوب ولا متصفح", Toast.LENGTH_LONG).show(); }
        }
    }
}
