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
    private boolean ttsReady = false;
    private AlarmReceiver alarmReceiver;

    private boolean isAlarmOn = true;
    private boolean isToko24On = false;

    // Jadwal maxdis: jam 10, 14, 18, 21 → alarm 1 jam sebelum = 9, 13, 17, 20
    private static final int[] ALARM_HOURS = {9, 13, 17, 20};
    private static final int ALARM_HOUR_24JAM = 2; // 1 jam sebelum 03:00
    private static final String PREF_NAME = "MaxdisPrefs";
    private static final String ACTION_ALARM = "com.alfamart.maxdis.ALARM";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        alarmHandler = new Handler(Looper.getMainLooper());
        tts = new TextToSpeech(this, this);

        // Muat pengaturan tersimpan
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        isAlarmOn = prefs.getBoolean("alarm_on", true);
        isToko24On = prefs.getBoolean("toko24_on", false);

        webView = findViewById(R.id.webview);
        setupWebView();
        webView.loadUrl("file:///android_asset/index.html");

        // Daftarkan receiver alarm harian
        alarmReceiver = new AlarmReceiver();
        IntentFilter filter = new IntentFilter(ACTION_ALARM);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alarmReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(alarmReceiver, filter);
        }

        // Setup alarm harian
        setupDailyAlarms();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("id", "ID"));
            if (result == TextToSpeech.LANG_AVAILABLE || result == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                ttsReady = true;
            }
        }
    }

    private void speak(final String text) {
        alarmHandler.post(() -> {
            if (tts != null && ttsReady) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_" + System.currentTimeMillis());
            }
        });
    }

    // Ucapkan teks berulang selama alarm menyala
    private void speakLoop(final String text) {
        if (!alarmRunning) return;
        speak(text);
        alarmHandler.postDelayed(() -> {
            if (alarmRunning) speakLoop(text);
        }, 3000);
    }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
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

    // ── ALARM HARIAN ─────────────────────────────────────────────────────────

    private void setupDailyAlarms() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // Batalkan semua alarm dulu
        cancelAllAlarms(am);
        if (!isAlarmOn) return;

        // Set alarm untuk setiap jam
        for (int i = 0; i < ALARM_HOURS.length; i++) {
            setDailyAlarm(am, ALARM_HOURS[i], i);
        }
        // Alarm toko 24 jam (jam 02:00)
        if (isToko24On) {
            setDailyAlarm(am, ALARM_HOUR_24JAM, 99);
        }
    }

    private void setDailyAlarm(AlarmManager am, int hour, int requestCode) {
        Intent intent = new Intent(ACTION_ALARM);
        intent.putExtra("hour", hour);
        PendingIntent pi = PendingIntent.getBroadcast(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        am.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);
    }

    private void cancelAllAlarms(AlarmManager am) {
        int[] allCodes = {0, 1, 2, 3, 99};
        for (int code : allCodes) {
            Intent intent = new Intent(ACTION_ALARM);
            PendingIntent pi = PendingIntent.getBroadcast(this, code, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) am.cancel(pi);
        }
    }

    // ── ALARM RECEIVER ────────────────────────────────────────────────────────

    public class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isAlarmOn) return;
            int hour = intent.getIntExtra("hour", -1);
            if (hour < 0) return;

            // Tampilkan di UI WebView
            int jadwal = hour + 1; // jam pengingat + 1 = jam maxdis sebenarnya
            runOnUiThread(() -> {
                webView.evaluateJavascript("tampilkanAlarmHarian(" + jadwal + ");", null);
                // Native alarm + TTS
                alarmRunning = true;
                startNativeAlarm();
                speakLoop("saatnya maxdisplay");
            });
        }
    }

    // ── JAVASCRIPT BRIDGE ─────────────────────────────────────────────────────

    public class AndroidBridge {

        // Alarm: Waktu maxdis total habis
        @JavascriptInterface
        public void startAlarm() {
            runOnUiThread(() -> {
                alarmRunning = true;
                startNativeAlarm();
                speakLoop("selesaikan max display sekarang");
            });
        }

        // Alarm: Waktu per lorong habis
        @JavascriptInterface
        public void startAlarmLorong() {
            runOnUiThread(() -> {
                alarmRunning = true;
                startNativeAlarm();
                speakLoop("selesaikan lorong sekarang");
            });
        }

        // Alarm: Pengingat harian (dipanggil dari JS jika perlu)
        @JavascriptInterface
        public void startAlarmHarian() {
            runOnUiThread(() -> {
                alarmRunning = true;
                startNativeAlarm();
                speakLoop("saatnya maxdisplay");
            });
        }

        // Stop semua alarm
        @JavascriptInterface
        public void stopAlarm() {
            runOnUiThread(() -> {
                alarmRunning = false;
                stopNativeAlarm();
                if (tts != null) tts.stop();
            });
        }

        // Kirim state toggle ke WebView saat app dibuka
        @JavascriptInterface
        public void requestToggleState() {
            runOnUiThread(() -> webView.evaluateJavascript(
                "setTogglesDariAndroid(" + isAlarmOn + "," + isToko24On + ");", null));
        }

        // Simpan pengaturan dari WebView
        @JavascriptInterface
        public void saveAlarmSettings(boolean alarmOn, boolean toko24On) {
            runOnUiThread(() -> {
                isAlarmOn = alarmOn;
                isToko24On = toko24On;
                getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
                        .putBoolean("alarm_on", alarmOn)
                        .putBoolean("toko24_on", toko24On)
                        .apply();
                setupDailyAlarms();
                Toast.makeText(MainActivity.this,
                        alarmOn ? "Alarm pengingat aktif ✓" : "Alarm pengingat dimatikan",
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    // ── NATIVE ALARM (TONE + VIBRATE) ─────────────────────────────────────────

    private void startNativeAlarm() {
        stopNativeAlarm();
        // Vibrasi berulang
        long[] pattern = {0, 600, 300, 600, 300, 600};
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
        // Tone beep
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (Exception e) { e.printStackTrace(); }

        alarmRunnable = new Runnable() {
            @Override
            public void run() {
                if (!alarmRunning) return;
                if (toneGenerator != null)
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500);
                alarmHandler.postDelayed(this, 1200);
            }
        };
        alarmHandler.post(alarmRunnable);
    }

    private void stopNativeAlarm() {
        if (alarmHandler != null && alarmRunnable != null)
            alarmHandler.removeCallbacks(alarmRunnable);
        if (toneGenerator != null) {
            try { toneGenerator.stopTone(); toneGenerator.release(); } catch(Exception e){}
            toneGenerator = null;
        }
        if (vibrator != null) vibrator.cancel();
    }

    // ── LIFECYCLE ─────────────────────────────────────────────────────────────

    @Override
    protected void onPause() { super.onPause(); if (webView != null) webView.onPause(); }

    @Override
    protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }

    @Override
    protected void onDestroy() {
        stopNativeAlarm();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        try { unregisterReceiver(alarmReceiver); } catch (Exception e) {}
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() { /* Disabled agar timer tidak terganggu */ }
}
