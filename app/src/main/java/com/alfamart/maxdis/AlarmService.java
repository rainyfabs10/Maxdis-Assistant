package com.alfamart.maxdis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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

public class AlarmService extends Service implements TextToSpeech.OnInitListener {

    // Action yang dikirim langsung ke Service ini
    public static final String ACTION_START = "com.alfamart.maxdis.svc.START";
    public static final String ACTION_STOP  = "com.alfamart.maxdis.svc.STOP";

    private static final String CHANNEL_ALARM   = "maxdis_alarm_ch";
    private static final String CHANNEL_REMIND  = "maxdis_remind_ch";
    private static final int    NOTIF_ALARM_ID  = 2001;
    private static final int    NOTIF_REMIND_ID = 2002;

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
        handler  = new Handler(Looper.getMainLooper());
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        tts      = new TextToSpeech(this, this);
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        String action = intent.getAction();

        // ── STOP dari tombol notifikasi ────────────────────────────────────
        if (ACTION_STOP.equals(action)) {
            stopAlarmAndSelf();
            return START_NOT_STICKY;
        }

        // ── START alarm ────────────────────────────────────────────────────
        int hour = intent.getIntExtra(AlarmReceiver.EXTRA_HOUR, -1);
        if (hour < 0) { stopSelf(); return START_NOT_STICKY; }

        int sesiJam = hour + 1; // jam alarm = 1 jam sebelum sesi → +1 = jam sesi

        // Wajib startForeground segera agar tidak ANR di Android 8+
        startForeground(NOTIF_ALARM_ID, buildAlarmNotif(sesiJam));

        // Notifikasi pengingat biasa (bisa di-dismiss user)
        showReminderNotif(sesiJam);

        // Mulai alarm
        alarmRunning = true;
        startBeep();
        startTtsLoop("saatnya maxdisplay");

        return START_NOT_STICKY;
    }

    // ── TTS ───────────────────────────────────────────────────────────────────

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
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts");
    }

    private void startTtsLoop(final String text) {
        ttsRunnable = new Runnable() {
            @Override public void run() {
                if (!alarmRunning) return;
                speak(text);
                handler.postDelayed(this, 4000);
            }
        };
        handler.postDelayed(ttsRunnable, 1200);
    }

    // ── BEEP + VIBRATE ────────────────────────────────────────────────────────

    private void startBeep() {
        long[] pattern = {0, 600, 300, 600, 300, 600};
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            else
                vibrator.vibrate(pattern, 0);
        }
        try { toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 100); }
        catch (Exception e) { e.printStackTrace(); }

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
            if (beepRunnable != null) handler.removeCallbacks(beepRunnable);
            if (ttsRunnable  != null) handler.removeCallbacks(ttsRunnable);
        }
        if (toneGen != null) {
            try { toneGen.stopTone(); toneGen.release(); } catch (Exception e) {}
            toneGen = null;
        }
        if (vibrator != null) vibrator.cancel();
        if (tts != null) tts.stop();
    }

    private void stopAlarmAndSelf() {
        stopAlarm();
        // Hapus notifikasi lain juga
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_REMIND_ID);
        stopForeground(true);
        stopSelf();
    }

    // ── NOTIFIKASI ────────────────────────────────────────────────────────────

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);

        // Channel alarm — prioritas tinggi, tidak ada suara channel (suara dari ToneGenerator)
        NotificationChannel chA = new NotificationChannel(
                CHANNEL_ALARM, "Alarm Maxdisplay", NotificationManager.IMPORTANCE_HIGH);
        chA.setDescription("Alarm saat jadwal maxdisplay tiba");
        chA.setSound(null, null); // suara dari ToneGenerator, bukan channel
        chA.enableVibration(false); // vibrasi dari kode, bukan channel
        chA.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(chA);

        // Channel pengingat — prioritas normal
        NotificationChannel chR = new NotificationChannel(
                CHANNEL_REMIND, "Pengingat Maxdisplay", NotificationManager.IMPORTANCE_DEFAULT);
        chR.setDescription("Notifikasi pengingat jadwal harian");
        nm.createNotificationChannel(chR);
    }

    /**
     * Notifikasi foreground alarm — ongoing, ada tombol BERHENTI.
     * Stop intent dikirim LANGSUNG ke AlarmService (bukan lewat receiver).
     */
    private Notification buildAlarmNotif(int sesiJam) {
        // Tap notif → buka app
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Tombol BERHENTI → kirim ACTION_STOP langsung ke Service ini
        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent piStop;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            piStop = PendingIntent.getForegroundService(
                    this, 1, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            piStop = PendingIntent.getService(
                    this, 1, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        return new NotificationCompat.Builder(this, CHANNEL_ALARM)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("🔔 Saatnya Maxdisplay!")
                .setContentText("1 jam lagi jadwal maxdisplay jam " + sesiJam + ":00")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Jadwal maxdisplay jam " + sesiJam
                                + ":00 — 1 jam lagi!\nSegera siapkan display toko Anda."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)        // tidak bisa di-swipe
                .setAutoCancel(false)
                .setContentIntent(piOpen)
                // Tombol aksi — langsung stop alarm
                .addAction(android.R.drawable.ic_delete,
                           "⏹ Berhenti", piStop)
                .build();
    }

    /** Notifikasi pengingat biasa — bisa di-dismiss, tidak ada suara berulang */
    private void showReminderNotif(int sesiJam) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(
                this, 2, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Tombol stop juga ada di notif pengingat
        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent piStop;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            piStop = PendingIntent.getForegroundService(
                    this, 3, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            piStop = PendingIntent.getService(
                    this, 3, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_REMIND)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("📋 Pengingat: Maxdisplay jam " + sesiJam + ":00")
                .setContentText("Jangan lupa maxdisplay " + sesiJam + ":00 — 1 jam lagi!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(piOpen)
                .addAction(android.R.drawable.ic_delete, "⏹ Berhenti", piStop)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_REMIND_ID, notif);
    }

    @Override
    public void onDestroy() {
        stopAlarm();
        if (tts != null) { tts.shutdown(); tts = null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
