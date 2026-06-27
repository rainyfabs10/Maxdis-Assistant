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
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Locale;

public class AlarmService extends Service implements TextToSpeech.OnInitListener {

    public static final String ACTION_START    = "com.alfamart.maxdis.svc.START";
    public static final String ACTION_STOP     = "com.alfamart.maxdis.svc.STOP";
    public static final String EXTRA_SESI_JAM  = "sesi_jam";

    private static final String CH_ALARM      = "maxdis_alarm_v3";
    private static final String CH_KEEPALIVE  = "maxdis_ka_v3";
    private static final int    ID_ALARM      = 4001;
    private static final int    ID_KEEPALIVE  = 4002;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Vibrator vibrator;
    private ToneGenerator toneGen;
    private Handler handler;
    private Runnable beepRunnable;
    private Runnable ttsRunnable;
    private boolean alarmRunning = false;
    private PowerManager.WakeLock wakeLock;

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
        // STOP — dari tombol notifikasi
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            doStop();
            return START_NOT_STICKY;
        }

        int sesiJam = (intent != null) ? intent.getIntExtra(EXTRA_SESI_JAM, 10) : 10;

        // 1. Langsung startForeground dengan notifikasi — WAJIB cepat di Android 8+
        Notification notif = buildAlarmNotif(sesiJam);
        startForeground(ID_ALARM, notif);

        // 2. Acquire WakeLock agar CPU tidak tidur saat alarm bunyi
        acquireWakeLock();

        // 3. Mulai bunyi setelah notifikasi sudah tampil
        alarmRunning = true;
        handler.postDelayed(() -> {
            startBeep();
            startTtsLoop("saatnya maxdisplay");
        }, 500);

        // Auto stop setelah 10 menit jika tidak dihentikan manual
        handler.postDelayed(this::doStop, 10 * 60 * 1000L);

        return START_NOT_STICKY;
    }

    // ── STOP ─────────────────────────────────────────────────────────────────

    private void doStop() {
        alarmRunning = false;

        if (handler != null) {
            if (beepRunnable != null) handler.removeCallbacks(beepRunnable);
            if (ttsRunnable  != null) handler.removeCallbacks(ttsRunnable);
            handler.removeCallbacksAndMessages(null);
        }
        if (toneGen != null) {
            try { toneGen.stopTone(); toneGen.release(); } catch (Exception e) {}
            toneGen = null;
        }
        if (vibrator != null) vibrator.cancel();
        if (tts != null) tts.stop();
        releaseWakeLock();

        // Hapus notifikasi alarm, tapi tampilkan notifikasi "Alarm dimatikan" sebentar
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(ID_ALARM);

        stopForeground(true);
        stopSelf();
    }

    // ── WAKELOCK ─────────────────────────────────────────────────────────────

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "MaxdisAssistant::AlarmWakeLock");
            wakeLock.acquire(10 * 60 * 1000L); // max 10 menit
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception e) {}
            wakeLock = null;
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

    private void startTtsLoop(final String text) {
        ttsRunnable = new Runnable() {
            @Override public void run() {
                if (!alarmRunning) return;
                if (tts != null && ttsReady)
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts");
                handler.postDelayed(this, 4000);
            }
        };
        handler.postDelayed(ttsRunnable, 1500);
    }

    // ── BEEP + VIBRATE ────────────────────────────────────────────────────────

    private void startBeep() {
        long[] pattern = {0, 700, 300, 700, 300, 700};
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
                    toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700);
                handler.postDelayed(this, 1800);
            }
        };
        handler.post(beepRunnable);
    }

    // ── NOTIFIKASI ────────────────────────────────────────────────────────────

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);

        // Channel alarm — HIGH, tanpa suara channel (suara dari ToneGenerator)
        NotificationChannel chAlarm = new NotificationChannel(
                CH_ALARM, "Alarm Maxdisplay", NotificationManager.IMPORTANCE_HIGH);
        chAlarm.setSound(null, null);
        chAlarm.enableVibration(false);
        chAlarm.setShowBadge(true);
        chAlarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(chAlarm);

        // Channel keepalive — MIN, senyap
        NotificationChannel chKA = new NotificationChannel(
                CH_KEEPALIVE, "Maxdis Berjalan", NotificationManager.IMPORTANCE_MIN);
        chKA.setSound(null, null);
        chKA.enableVibration(false);
        chKA.setShowBadge(false);
        nm.createNotificationChannel(chKA);
    }

    private Notification buildAlarmNotif(int sesiJam) {
        // Tap → buka app
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Tombol STOP → langsung ke service ini
        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent piStop;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            piStop = PendingIntent.getForegroundService(this, 1, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            piStop = PendingIntent.getService(this, 1, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        return new NotificationCompat.Builder(this, CH_ALARM)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ Maxdisplay jam " + sesiJam + ":00")
                .setContentText("Saatnya maxdisplay! 1 jam lagi.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Jadwal maxdisplay jam " + sesiJam
                                + ":00 — 1 jam lagi!\nTekan STOP untuk matikan alarm."))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)          // tidak bisa di-swipe
                .setAutoCancel(false)
                .setShowWhen(true)
                .setFullScreenIntent(piOpen, true)  // munculkan layar saat HP terkunci
                .setContentIntent(piOpen)
                .addAction(android.R.drawable.ic_delete, "⏹ STOP", piStop)
                .setDeleteIntent(piStop)   // swipe = stop
                .build();
    }

    @Override
    public void onDestroy() {
        alarmRunning = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (toneGen  != null) { try { toneGen.release(); } catch (Exception e) {} }
        if (tts      != null) { tts.shutdown(); tts = null; }
        releaseWakeLock();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
