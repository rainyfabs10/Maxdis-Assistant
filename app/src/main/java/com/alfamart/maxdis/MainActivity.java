package com.alfamart.maxdis;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private WebView webView;
    private Vibrator vibrator;
    private ToneGenerator toneGenerator;
    private Handler alarmHandler;
    private Runnable beepRunnable;
    private Runnable ttsLoopRunnable;
    private boolean alarmRunning = false;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private boolean isAlarmOn  = true;
    private boolean isToko24On = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        vibrator     = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        alarmHandler = new Handler(Looper.getMainLooper());
        tts          = new TextToSpeech(this, this);

        SharedPreferences prefs = getSharedPreferences(AlarmReceiver.PREF_NAME, MODE_PRIVATE);
        isAlarmOn  = prefs.getBoolean(AlarmReceiver.PREF_ALARM_ON, true);
        isToko24On = prefs.getBoolean(AlarmReceiver.PREF_TOKO24, false);

        webView = findViewById(R.id.webview);
        setupWebView();
        webView.loadUrl("file:///android_asset/index.html");

        // Jadwalkan alarm harian
        AlarmScheduler.scheduleAll(this);

        // Start KeepAliveService agar alarm tetap jalan di background MIUI
        startKeepAlive();
    }

    private void startKeepAlive() {
        Intent intent = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // ── TTS ──────────────────────────────────────────────────────────────────

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(new Locale("id", "ID"));
            ttsReady = (r == TextToSpeech.LANG_AVAILABLE
                     || r == TextToSpeech.LANG_COUNTRY_AVAILABLE);
        }
    }

    private void speak(String text) {
        if (tts != null && ttsReady)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_" + System.currentTimeMillis());
    }

    private void startTtsLoop(final String text) {
        ttsLoopRunnable = new Runnable() {
            @Override public void run() {
                if (!alarmRunning) return;
                speak(text);
                alarmHandler.postDelayed(this, 4000);
            }
        };
        alarmHandler.postDelayed(ttsLoopRunnable, 800);
    }

    // ── WEBVIEW ───────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return !url.startsWith("file://");
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
    }

    // ── JAVASCRIPT BRIDGE ─────────────────────────────────────────────────────

    public class AndroidBridge {

        @JavascriptInterface
        public void startAlarm() {
            runOnUiThread(() -> {
                alarmRunning = true;
                startNativeAlarm();
                startTtsLoop("selesaikan max display sekarang");
            });
        }

        @JavascriptInterface
        public void startAlarmLorong() {
            runOnUiThread(() -> {
                alarmRunning = true;
                startNativeAlarm();
                startTtsLoop("selesaikan lorong sekarang");
            });
        }

        @JavascriptInterface
        public void stopAlarm() {
            runOnUiThread(() -> {
                alarmRunning = false;
                stopNativeAlarm();
                if (tts != null) tts.stop();
            });
        }

        @JavascriptInterface
        public void stopAlarmHarian() {
            runOnUiThread(() -> {
                // Stop AlarmService jika sedang berjalan
                Intent stopIntent = new Intent(MainActivity.this, AlarmService.class);
                stopIntent.setAction(AlarmService.ACTION_STOP);
                startService(stopIntent);
            });
        }

        @JavascriptInterface
        public void requestToggleState() {
            runOnUiThread(() -> webView.evaluateJavascript(
                "setTogglesDariAndroid(" + isAlarmOn + "," + isToko24On + ");", null));
        }

        @JavascriptInterface
        public void saveAlarmSettings(boolean alarmOn, boolean toko24On) {
            runOnUiThread(() -> {
                isAlarmOn  = alarmOn;
                isToko24On = toko24On;
                getSharedPreferences(AlarmReceiver.PREF_NAME, MODE_PRIVATE).edit()
                        .putBoolean(AlarmReceiver.PREF_ALARM_ON, alarmOn)
                        .putBoolean(AlarmReceiver.PREF_TOKO24, toko24On)
                        .apply();
                AlarmScheduler.scheduleAll(MainActivity.this);

                // Kelola KeepAliveService sesuai toggle
                if (alarmOn) {
                    startKeepAlive();
                } else {
                    stopService(new Intent(MainActivity.this, KeepAliveService.class));
                }

                Toast.makeText(MainActivity.this,
                        alarmOn ? "Alarm pengingat aktif ✓" : "Alarm pengingat dimatikan",
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    // ── NATIVE ALARM ─────────────────────────────────────────────────────────

    private void startNativeAlarm() {
        stopNativeAlarm();
        long[] pattern = {0, 600, 300, 600, 300, 600};
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            else
                vibrator.vibrate(pattern, 0);
        }
        try { toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100); }
        catch (Exception e) { e.printStackTrace(); }

        beepRunnable = new Runnable() {
            @Override public void run() {
                if (!alarmRunning) return;
                if (toneGenerator != null)
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500);
                alarmHandler.postDelayed(this, 1500);
            }
        };
        alarmHandler.post(beepRunnable);
    }

    private void stopNativeAlarm() {
        if (alarmHandler != null) {
            if (beepRunnable    != null) alarmHandler.removeCallbacks(beepRunnable);
            if (ttsLoopRunnable != null) alarmHandler.removeCallbacks(ttsLoopRunnable);
        }
        if (toneGenerator != null) {
            try { toneGenerator.stopTone(); toneGenerator.release(); } catch (Exception e) {}
            toneGenerator = null;
        }
        if (vibrator != null) vibrator.cancel();
    }

    // ── LIFECYCLE ─────────────────────────────────────────────────────────────

    @Override protected void onPause()  { super.onPause();  if (webView != null) webView.onPause(); }
    @Override protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }

    @Override
    protected void onDestroy() {
        stopNativeAlarm();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        super.onDestroy();
        // KeepAliveService tetap jalan meski activity destroy
    }

    @Override public void onBackPressed() { /* Disabled */ }
}
