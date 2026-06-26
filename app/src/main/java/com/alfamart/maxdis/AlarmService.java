package com.alfamart.maxdis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

/**
 * Foreground Service yang menangani alarm harian maxdisplay.
 * Berjalan di background, muncul sebagai notifikasi permanen saat alarm aktif.
 */
public class AlarmService extends Service implements TextToSpeech.OnInitListener {

    private static final String CHANNEL_ID      = "maxdis_alarm";
    private static final String CHANNEL_REMIND  = "maxdis_reminder";
    private static final int    NOTIF_ID_ALARM  = 1001;
    private static final int    NOTIF_ID_REMIND = 1002;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Vibrator vibrator;
    private ToneGenerator toneGen;
    private Handler handler;
    private Runnable beepRunnable;
    private Runnable ttsRunnable;
    private boolean alarmRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler   = new Handler(Looper.getMainLooper());
        vibrator  = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        tts       = new TextToSpeech(this, this);
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        String action = intent.getAction();

        // Stop alarm dari tombol notifikasi
        if (AlarmReceiver.ACTION_STOP.equals(action)) {
            stopAlarm();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // Mulai alarm
        int hour = intent.getIntExtra(AlarmReceiver.EXTRA_HOUR, -1);
        if (hour < 0) { stopSelf(); return START_NOT_STICKY; }

        int jadwalJam = hour + 1; // alarm 1 jam sebelum → +1 = jam maxdis sebenarnya
        String pesan  = "Saatnya Maxdisplay! 1 jam lagi maxdisplay jam " + jadwalJam + ":00";

        // Tampilkan notifikasi foreground agar service tidak dikill Android
        startForeground(NOTIF_ID_ALARM, buildAlarmNotification(jadwalJam));

        // Juga kirim notifikasi pengingat terpisah (tetap ada meski alarm dimatikan)
        sendReminderNotification(jadwalJam);

        // Mulai bunyi + TTS
        alarmRunning = true;
        startBeep();
        startTtsLoop("saatnya maxdisplay");

        return START_NOT_STICKY;
    }

    // ── TTS ──────────────────────────────────────────────────────────────────

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(new Locale("id", "ID"));
            ttsReady = (r == TextToSpeech.LANG_AVAILABLE || r == TextToSpeech.LANG_COUNTRY_AVAILABLE);
        }
    }

    private void speak(String text) {
        if (tts != null && ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_" + System.currentTimeMillis());
        }
    }

    private void startTtsLoop(final String text) {
        ttsRunnable = new Runnable() {
            @Override public void run() {
                if (!alarmRunning) return;
                speak(text);
                handler.postDelayed(this, 4000);
            }
        };
        // Delay sedikit agar TTS sempat init
        handler.postDelayed(ttsRunnable, 1000);
    }

    // ── BEEP + VIBRATE ────────────────────────────────────────────────────────

    private void startBeep() {
        // Vibrasi pola berulang
        long[] pattern = {0, 600, 300, 600, 300, 600};
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
        // Tone alarm berulang
        try {
            toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (Exception e) { e.printStackTrace(); }

        beepRunnable = new Runnable() {
            @Override public void run() {
                if (!alarmRunning) return;
                if (toneGen != null)
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600);
                handler.postDelayed(this, 1500);
            }
        };
        handler.post(beepRunnable);
    }

    private void stopAlarm() {
        alarmRunning = false;
        if (handler != null) {
            if (beepRunnable  != null) handler.removeCallbacks(beepRunnable);
            if (ttsRunnable   != null) handler.removeCallbacks(ttsRunnable);
        }
        if (toneGen != null) {
            try { toneGen.stopTone(); toneGen.release(); } catch (Exception e) {}
            toneGen = null;
        }
        if (vibrator != null) vibrator.cancel();
        if (tts != null) tts.stop();
    }

    // ── NOTIFIKASI ────────────────────────────────────────────────────────────

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);

        // Channel alarm (suara keras, penting tinggi)
        NotificationChannel chAlarm = new NotificationChannel(
                CHANNEL_ID, "Alarm Maxdisplay", NotificationManager.IMPORTANCE_HIGH);
        chAlarm.setDescription("Bunyi alarm saat waktu maxdisplay tiba");
        chAlarm.enableVibration(true);
        chAlarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(chAlarm);

        // Channel pengingat (notifikasi biasa)
        NotificationChannel chRemind = new NotificationChannel(
                CHANNEL_REMIND, "Pengingat Maxdisplay", NotificationManager.IMPORTANCE_DEFAULT);
        chRemind.setDescription("Pengingat jadwal maxdisplay harian");
        nm.createNotificationChannel(chRemind);
    }

    /** Notifikasi foreground alarm — ada tombol BERHENTI */
    private Notification buildAlarmNotification(int jadwalJam) {
        // Intent buka app saat notif ditekan
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent stop alarm dari tombol notif
        Intent stopIntent = new Intent(this, AlarmReceiver.class);
        stopIntent.setAction(AlarmReceiver.ACTION_STOP);
        PendingIntent piStop = PendingIntent.getBroadcast(this, 99, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("🔔 Saatnya Maxdisplay!")
                .setContentText("1 jam lagi jadwal maxdisplay jam " + jadwalJam + ":00 — Segera siapkan!")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("1 jam lagi jadwal maxdisplay jam " + jadwalJam
                                + ":00\nSegera siapkan display toko Anda!"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(piOpen)
                .addAction(android.R.drawable.ic_media_pause, "✕ Berhenti", piStop)
                .build();
    }

    /** Notifikasi pengingat biasa (tetap di notification bar) */
    private void sendReminderNotification(int jadwalJam) {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(this, 1, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_REMIND)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("📋 Pengingat Maxdisplay " + jadwalJam + ":00")
                .setContentText("Maxdisplay jam " + jadwalJam + ":00 — jangan lupa!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(piOpen)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID_REMIND, notif);
    }

    @Override
    public void onDestroy() {
        stopAlarm();
        if (tts != null) { tts.shutdown(); tts = null; }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
