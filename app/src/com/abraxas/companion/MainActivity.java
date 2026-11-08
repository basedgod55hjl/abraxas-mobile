package com.abraxas.companion;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * ABRASAX UNIFIED v2 - shell + always-on Telegram backend.
 * Online: brain UI over the adb tunnel (127.0.0.1:8090).
 * Offline: local vault page (file:///android_asset/local.html).
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                view.loadUrl("file:///android_asset/local.html");
            }
        });
        w.loadUrl("http://127.0.0.1:8090");
        setContentView(w);

        Intent svc = new Intent(this, BotService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }
}
