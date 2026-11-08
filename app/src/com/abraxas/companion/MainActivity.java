package com.abraxas.companion;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * ABRASAX Companion v1 - a native shell for the ABRASAX brain.
 * Loads the web UI over the adb reverse tunnel (127.0.0.1:8090).
 * No resources, no R references: minimal hand-built APK.
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        w.loadUrl("http://127.0.0.1:8090");
        setContentView(w);
    }
}
