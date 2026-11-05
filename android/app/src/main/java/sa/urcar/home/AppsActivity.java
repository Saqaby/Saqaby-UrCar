package sa.urcar.home;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

/** «التطبيقات»: every app that can be opened, alphabetically, six to a row. */
public class AppsActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.Theme th = new Ui.Theme(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(th.bg);
        root.setPadding(40, 24, 40, 24);
        LinearLayout top = new LinearLayout(this);
        TextView title = Ui.text(this, 34, Ui.medium(this), th.fg);
        title.setText("التطبيقات");
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView back = Ui.text(this, 24, Ui.medium(this), th.fg);
        back.setText("رجوع");
        back.setPadding(28, 16, 28, 16);
        back.setBackground(Ui.box(th.panel, th.line, 14));
        back.setOnClickListener(v -> finish());
        top.addView(back);
        root.addView(top);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        Collections.sort(apps, (x, y) -> x.loadLabel(pm).toString().compareTo(y.loadLabel(pm).toString()));
        for (ResolveInfo ri : apps) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(8, 18, 8, 18);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(ri.loadIcon(pm));
            cell.addView(icon, new LinearLayout.LayoutParams(84, 84));
            TextView label = Ui.text(this, 19, Ui.regular(this), th.fg);
            label.setText(ri.loadLabel(pm));
            label.setGravity(Gravity.CENTER);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(label, new LinearLayout.LayoutParams(180, -2));
            final String pkg = ri.activityInfo.packageName, cls = ri.activityInfo.name;
            cell.setOnClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_MAIN).setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            grid.addView(cell, lp);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(grid);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
        lp.topMargin = 16;
        root.addView(sv, lp);
        setContentView(root);
    }
}
