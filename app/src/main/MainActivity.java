package com.alfamart.maxdis;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    private Vibrator vibrator;
    private ToneGenerator toneGenerator;
    private Handler alarmHandler;
    private Runnable alarmRunnable;
    private boolean alarmRunning = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen / immersive
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        // Keep screen on selama app aktif
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        alarmHandler = new Handler(Looper.getMainLooper());

        webView = findViewById(R.id.webview);
        setupWebView();
        webView.loadUrl("file:///android_asset/index.html");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // JavaScript
        settings.setJavaScriptEnabled(true);

        // DOM Storage (untuk localStorage jika dipakai)
        settings.setDomStorageEnabled(true);

        // Zoom controls off
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Cache
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAppCacheEnabled(true);

        // Viewport
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Text size
        settings.setTextZoom(100);

        // Mixed content (API 21+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Hardware acceleration
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Scroll bars
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // WebViewClient — prevent external navigation
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("file://")) {
                    return false; // load internal
                }
                return true; // block external
            }
        });

        // WebChromeClient — for JS alerts etc
        webView.setWebChromeClient(new WebChromeClient());

        // JavaScript Interface untuk native alarm
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
    }

    // ─── JavaScript Bridge ───────────────────────────────────────────────────

    public class AndroidBridge {

        @JavascriptInterface
        public void startAlarm() {
            runOnUiThread(() -> {
                alarmRunning = true;
                startNativeAlarm();
            });
        }

        @JavascriptInterface
        public void stopAlarm() {
            runOnUiThread(() -> {
                alarmRunning = false;
                stopNativeAlarm();
            });
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            if (vibrator == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        }

        @JavascriptInterface
        public int getSdkVersion() {
            return Build.VERSION.SDK_INT;
        }
    }

    // ─── Native Alarm (Tone + Vibrate) ───────────────────────────────────────

    private void startNativeAlarm() {
        stopNativeAlarm(); // stop previous if any

        // Vibration pattern: on 500ms, off 300ms, repeat
        long[] pattern = {0, 500, 300, 500, 300, 500};

        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }

        // Tone beep loop
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
        } catch (Exception e) {
            e.printStackTrace();
        }

        alarmRunnable = new Runnable() {
            @Override
            public void run() {
                if (!alarmRunning) return;
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400);
                }
                alarmHandler.postDelayed(this, 1000);
            }
        };
        alarmHandler.post(alarmRunnable);
    }

    private void stopNativeAlarm() {
        alarmRunning = false;
        if (alarmHandler != null && alarmRunnable != null) {
            alarmHandler.removeCallbacks(alarmRunnable);
        }
        if (toneGenerator != null) {
            toneGenerator.stopTone();
            toneGenerator.release();
            toneGenerator = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        stopNativeAlarm();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Disable back button agar timer tidak ter-interrupt
        // Atau bisa uncomment bawah untuk allow back:
        // super.onBackPressed();
    }
}
