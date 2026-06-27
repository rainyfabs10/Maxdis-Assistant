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

    public static final String ACTION_START = "com.alfamart.maxdis.svc.START";
    public static final String ACTION_STOP  = "com.alfamart.maxdis.svc.STOP";

    private static final String CHANNEL_ID  = "maxdis_alarm_v2";
    private static final int    NOTIF_ID    = 3001;
    private static final int    NOTIF_REMIND = 3002;

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
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        String action = intent.getAction();

        // STOP — dari tombol ✕ di notifikasi
        if (ACTION_STOP.equals(action)) {
            doStop();
            return START_NOT_STICKY;
        }

        // START — alarm harian tiba
        int hour = intent.getIntExtra(AlarmReceiver.EXTRA_HOUR, -1);
        if (hour < 0) { stopSelf(); return START_NOT_STICKY; }

        int sesiJam = hour + 1;

        // Langsung tampil notifikasi foreground (wajib cepat di Android 8+)
        startForeground(NOTIF_ID, buildNotif(sesiJam));

        // Mulai alarm
        alarmRunning = true;
        startBeep();
        startTtsLoop("saatnya maxdisplay");

        return START_NOT_STICKY;
    }

    // ── STOP ─────────────────────────────────────────────────────────────────

    private void doStop() {
        alarmRunning = false;
        // Stop callbacks
        if (handler != null) {
            if (beepRunnable != null) handler.removeCallbacks(beepRunnable);
            if (ttsRunnable  != null) handler.removeCallbacks(ttsRunnable);
        }
        // Stop tone
        if (toneGen != null) {
            try { toneGen.stopTone(); toneGen.release(); } catch (Exception e) {}
            toneGen = null;
        }
        // Stop vibrate
        if (vibrator != null) vibrator.cancel();
        // Stop TTS
        if (tts != null) tts.stop();
        // Hapus semua notifikasi & stop service
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIF_ID);
            nm.cancel(NOTIF_REMIND);
        }
        stopForeground(true);
        stopSelf();
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

    // ── NOTIFIKASI ────────────────────────────────────────────────────────────

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Alarm Maxdisplay", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Pengingat jadwal maxdisplay harian");
        ch.setSound(null, null);       // suara dari ToneGenerator
        ch.enableVibration(false);     // vibrasi dari kode
        ch.setShowBadge(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    /**
     * Notifikasi gaya compact seperti MIUI timer:
     * - Ikon jam pasir ⏳
     * - Teks singkat satu baris
     * - Tombol ✕ untuk stop (deleteIntent = swipe = stop juga)
     */
    private Notification buildNotif(int sesiJam) {
        // Tap notif → buka app
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Stop intent → langsung ke Service
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

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                // Ikon jam pasir — mirip notif MIUI timer
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⏳ Maxdisplay jam " + sesiJam + ":00")
                .setContentText("Saatnya maxdisplay! Sentuh untuk buka app")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(true)
                .setContentIntent(piOpen)
                // Tombol ✕ — stop alarm
                .addAction(android.R.drawable.ic_delete, "✕ Stop", piStop)
                // Swipe notif juga stop (deleteIntent)
                .setDeleteIntent(piStop)
                .build();
    }

    @Override
    public void onDestroy() {
        alarmRunning = false;
        if (handler != null) {
            if (beepRunnable != null) handler.removeCallbacks(beepRunnable);
            if (ttsRunnable  != null) handler.removeCallbacks(ttsRunnable);
        }
        if (toneGen != null) { try { toneGen.release(); } catch (Exception e) {} }
        if (tts != null) { tts.shutdown(); tts = null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
