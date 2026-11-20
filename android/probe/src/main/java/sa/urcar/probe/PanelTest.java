package sa.urcar.probe;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** A stand-in for our panel in the split-screen test: it shows its own width and whether it is in a split. */
public class PanelTest extends Activity {
    static PanelTest live;
    private TextView t;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        live = this;
        t = new TextView(this);
        t.setTextSize(26);
        t.setTextColor(Color.rgb(238, 241, 234));
        t.setBackgroundColor(Color.rgb(18, 29, 31));
        t.setGravity(Gravity.CENTER);
        setContentView(t);
    }
    @Override protected void onResume() { super.onResume(); show(); }
    @Override public void onMultiWindowModeChanged(boolean m, android.content.res.Configuration c) { super.onMultiWindowModeChanged(m, c); show(); }
    @Override public void onConfigurationChanged(android.content.res.Configuration c) { super.onConfigurationChanged(c); show(); }
    void show() {
        t.post(() -> t.setText("لوحة تجربة\nالعرض: " + getWindow().getDecorView().getWidth() + " نقطة\nالشاشة مقسومة: " + (isInMultiWindowMode() ? "نعم" : "لا")
                + "\n\nاسحب الخط الفاصل لتكبير الخريطة\nثم ارجع إلى «فحص سيارتك»"));
    }
    @Override protected void onDestroy() { if (live == this) live = null; super.onDestroy(); }
}
