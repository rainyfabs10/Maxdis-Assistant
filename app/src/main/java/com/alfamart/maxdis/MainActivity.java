package com.alfamart.maxdis;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private WebView webView;
    private Vibrator vibrator;
    private ToneGenerator toneGenerator;
    private Handler alarmHandler;
    private Runnable alarmRunnable;
    private boolean alarmRunning = false;

    private TextToSpeech tts;
    private AlarmReceiver alarmReceiver;
    private boolean isAlarmConfigEnabled = true;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        alarmHandler = new Handler(Looper.getMainLooper());

        tts = new TextToSpeech(this, this);

        SharedPreferences prefs = getSharedPreferences("MaxdisPrefs", MODE_PRIVATE);
        isAlarmConfigEnabled = prefs.getBoolean("daily_alarm_on", true);

        webView = findViewById(R.id.webview);
        setupWebView();
        webView.loadUrl("file:///android_asset/index.html");

        setupDailyAlarms();

        alarmReceiver = new AlarmReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alarmReceiver, new IntentFilter("com.alfamart.maxdis.START_ALARM"), Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(alarmReceiver, new IntentFilter("com.alfamart.maxdis.START_ALARM"));
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("id", "ID"));
        }
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MaxdisTTS");
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(100);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith("file://");
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                webView.evaluateJavascript("document.getElementById('alarmToggle').checked = " + isAlarmConfigEnabled + ";", null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
    }

    private void setupDailyAlarms() {
        int[] targetHours = {2, 9, 13, 20};
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        for (int i = 0; i < targetHours.length; i++) {
            Intent intent = new Intent("com.alfamart.maxdis.START_ALARM");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, i, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (!isAlarmConfigEnabled) {
                if (alarmManager != null) {
                    alarmManager.cancel(pendingIntent);
                }
                continue;
            }

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR_OF_DAY, targetHours[i]);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);

            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }

            if (alarmManager != null) {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                );
            }
        }
    }

    // ─── JavaScript Bridge ───────────────────────────────────────────────────

    public class AndroidBridge {

        @JavascriptInterface
        public void startAlarm() {
            runOnUiThread(() -> {
                alarmRunning = true;
                startNativeAlarm();
                speak("selesaikan maxdisplay");
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
        public void setAlarmEnabled(boolean enabled) {
            runOnUiThread(() -> {
                isAlarmConfigEnabled = enabled;
                SharedPreferences.Editor editor = getSharedPreferences("MaxdisPrefs", MODE_PRIVATE).edit();
                editor.putBoolean("daily_alarm_on", enabled);
                editor.apply();

                setupDailyAlarms();

                String statusTxt = enabled ? "Alarm harian aktif!" : "Alarm harian dimatikan!";
                Toast.makeText(MainActivity.this, statusTxt, Toast.LENGTH_SHORT).show();
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

    public class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isAlarmConfigEnabled) {
                Toast.makeText(context, "Saatnya Maxdisplay!", Toast.LENGTH_LONG).show();
                speak("saatnya maxdisplay");
            }
        }
    }

    private void startNativeAlarm() {
        stopNativeAlarm();
        long[] pattern = {0, 500, 300, 500, 300, 500};

        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }

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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        try {
            unregisterReceiver(alarmReceiver);
        } catch (Exception e) {
            // ignore
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Disabled
    }
}
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

        // Cache (Sudah diperbaiki dengan menghapus setAppCacheEnabled jadul)
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

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
