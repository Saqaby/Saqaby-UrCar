package sa.urcar.home;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * «ملقّن القرآن» (prompter.alketab.app, by AlKetab): their web app as it is, in a window of the screen's own — it
 * listens to the recitation (the recognition runs in the page, the voice sent nowhere, they say), finds the place in
 * the mushaf and moves the page along. Nothing of it is copied here. The microphone is given to that site only.
 * Opened at any speed (the owner's choice: a passenger follows or recites).
 */
public class PrompterActivity extends Activity {
    static final String HOST = "prompter.alketab.app";
    private WebView web;
    private PermissionRequest pending;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff000000);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
                return true;   // other sites in the browser, not here
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest r) {
                runOnUiThread(() -> {
                    boolean ours = r.getOrigin() != null && HOST.equals(r.getOrigin().getHost());
                    boolean mic = false;
                    for (String x : r.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(x)) mic = true;
                    if (!ours || !mic) { r.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        r.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                        return;
                    }
                    pending = r;   // Android asks the owner first; the page's request is answered after
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                });
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        TextView close = Ui.text(this, 22, Ui.medium(this), 0xffeef1ea);
        close.setText("رجوع");
        close.setPadding(26, 12, 26, 12);
        close.setBackground(Ui.box(0xcc121d1f, 0xff24383b, 14));
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
        cl.leftMargin = 16; cl.bottomMargin = 16;
        root.addView(close, cl);
        setContentView(root);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
        web.loadUrl("https://" + HOST + "/");
    }
    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        boolean ok = r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED;
        if (pending != null) { if (ok) pending.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE}); else pending.deny(); pending = null; }
        else if (ok && web != null) web.reload();
    }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else finish(); }
    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
