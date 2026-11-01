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
    static boolean picked;
    static final String[] MODES = {"Normal", "Simple graphics", "Software drawing", "No effects", "Lower resolution", "CPU canvas", "Hardware layer"};
    static final String[] QUERY = {"", "?gfx=1", "", "?nofx=1", "?lowres=1", "?cpu=1", ""};

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // If the last run died during start-up, show what happened instead of starting the game again.
        String problem = App.previousRunProblem(this);
        if (problem != null) { showReport(problem); return; }
        // Test builds: while a graphics test mode is on, let the tester pick a mode on every launch.
        if (BuildConfig.DEBUG && getSharedPreferences("diag", 0).getInt("gfx", 0) != 0 && !picked) {
            showReport("Graphics test mode is on: " + MODES[getSharedPreferences("diag", 0).getInt("gfx", 0)] + ". Pick a mode to try.\n\n" + App.exitReasons(this)); return; }
        App.startBoot(this);
        final boolean safe = getSharedPreferences("diag", 0).getBoolean("noAds", false);
        final int gfx = getSharedPreferences("diag", 0).getInt("gfx", 0);
        App.mark(this, "onCreate" + (safe ? " (ads off)" : "") + (gfx > 0 && gfx < MODES.length ? " MODE: " + MODES[gfx] : ""));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        App.mark(this, "WebView created");
        try { if (Build.VERSION.SDK_INT >= 26) { android.content.pm.PackageInfo wp = WebView.getCurrentWebViewPackage();
            if (wp != null) App.mark(this, "WebView " + wp.packageName + " " + wp.versionName); } } catch (Throwable ignored) { }
        if (gfx == 2) web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        if (gfx == 6) web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
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
        web.setWebChromeClient(new WebChromeClient() {
            int logged = 0;
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR && logged++ < 12)
                    App.mark(MainActivity.this, "JS error: " + m.message() + " (line " + m.lineNumber() + ")");
                return false;
            }
        });
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
            @Override
            public void onPageFinished(WebView view, String url) { App.mark(MainActivity.this, "page loaded"); }
            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                // The WebView's drawing process died (usually out of memory). Record it and restart cleanly instead of closing.
                App.mark(MainActivity.this, "WebView renderer died" + (Build.VERSION.SDK_INT >= 26 && detail.didCrash() ? " (crashed)" : " (killed for memory)"));
                if (web != null) { ((android.view.ViewGroup) web.getParent()).removeView(web); web.destroy(); web = null; }
                recreate();
                return true;
            }
        });
        web.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface public void log(String m) { App.mark(MainActivity.this, "game: " + m); }
        }, "HTRLog");
        App.mark(this, "WebView set up");
        // Ads and purchases must never stop the game from starting.
        if (!safe) try {
            monetize = new Monetize(this, web);
            web.addJavascriptInterface(monetize, "HTRNative");
        } catch (Throwable t) { monetize = null; App.record(this, "Ads/billing setup failed", t); }
        App.mark(this, monetize != null ? "ads object ready" : "ads skipped");
        web.loadUrl("file:///android_asset/index.html" + (gfx > 0 && gfx < QUERY.length ? QUERY[gfx] : ""));
        App.mark(this, "page loading");
        if (monetize != null) {
            try { monetize.start(); App.mark(this, "ads started"); } catch (Throwable t) { App.record(this, "Ads/billing start failed", t); }
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> App.bootOk(this), 10000);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript("window.androidBack&&window.androidBack()", value -> {
                    if (!"true".equals(value)) moveTaskToBack(true);
                });
            }
        });
    }

    /** Plain report screen (no WebView, no ads) shown after a failed start. */
    private void showReport(final String text) {
        android.widget.LinearLayout col = new android.widget.LinearLayout(this);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF1C0F0C);
        col.setPadding(48, 36, 48, 36);
        TextView title = new TextView(this);
        title.setText("Hold the Road closed last time. Please send this report to the developer.");
        title.setTextColor(0xFFF6E7C4); title.setTextSize(16);
        col.addView(title);
        String[] labels = {"Copy report", "Normal", "No effects", "Lower resolution", "CPU canvas", "Hardware layer", "Simple graphics", "Software drawing"};
        final int[] modeOf = {-1, 0, 3, 4, 5, 6, 1, 2};
        android.widget.LinearLayout row = null;
        for (int k = 0; k < labels.length; k++) {
            if (k % 4 == 0) { row = new android.widget.LinearLayout(this); row.setOrientation(android.widget.LinearLayout.HORIZONTAL); col.addView(row); }
            android.widget.Button b = new android.widget.Button(this);
            b.setText(labels[k]); b.setAllCaps(false);
            final int mode = modeOf[k];
            b.setOnClickListener(v -> {
                if (mode < 0) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Hold the Road report", text));
                    android.widget.Toast.makeText(this, "Report copied", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    getSharedPreferences("diag", 0).edit().putBoolean("noAds", false).putInt("gfx", mode).apply();
                    App.bootOk(this);
                    picked = true;
                    recreate();
                }
            });
            row.addView(b, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(0xFFE8DCC0); tv.setTextSize(11); tv.setTextIsSelectable(true);
        sv.addView(tv);
        col.addView(sv, new android.widget.LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(col);
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
