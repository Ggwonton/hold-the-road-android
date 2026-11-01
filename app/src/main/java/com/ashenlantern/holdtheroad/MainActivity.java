package com.ashenlantern.holdtheroad;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity extends AppCompatActivity {
    private WebView web;
    private Monetize monetize;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        setContentView(web);
        hideSystemBars();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // saves progress in localStorage
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                    return true;   // links (e.g. the Rotmarch page) open in the phone's browser
                }
                return false;
            }
        });
        // Ads and purchases must never stop the game from starting.
        try {
            monetize = new Monetize(this, web);
            web.addJavascriptInterface(monetize, "HTRNative");
        } catch (Throwable t) { monetize = null; App.record(this, "Ads/billing setup failed", t); }
        web.loadUrl("file:///android_asset/index.html");
        if (monetize != null) {
            try { monetize.start(); } catch (Throwable t) { App.record(this, "Ads/billing start failed", t); }
        }
        showLastCrash();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript("window.androidBack&&window.androidBack()", value -> {
                    if (!"true".equals(value)) moveTaskToBack(true);
                });
            }
        });
    }

    /** If the last run crashed (or ads failed), show what happened so it can be sent to the developer. */
    private void showLastCrash() {
        try {
            File f = new File(getFilesDir(), App.CRASH_FILE);
            if (!f.exists()) return;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (FileInputStream in = new FileInputStream(f)) { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) bo.write(b, 0, n); }
            final String text = bo.toString("UTF-8");
            f.delete();
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(11);
            tv.setTextIsSelectable(true);
            tv.setPadding(40, 24, 40, 24);
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.addView(tv);
            new AlertDialog.Builder(this)
                    .setTitle("Something went wrong last time")
                    .setView(sv)
                    .setPositiveButton("Copy report", (d, w) -> {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Hold the Road crash", text));
                    })
                    .setNegativeButton("Close", null)
                    .show();
        } catch (Throwable ignored) { }
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            web.evaluateJavascript("window.androidPause&&window.androidPause()", null);
            web.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            web.evaluateJavascript("window.androidResume&&window.androidResume()", null);
        }
    }

    @Override
    protected void onDestroy() {
        if (monetize != null) monetize.destroy();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
