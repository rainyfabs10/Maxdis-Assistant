package com.alfamart.maxdis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

/**
 * Foreground service ringan yang selalu jalan di background.
 * Tugasnya: jaga proses app tetap hidup di MIUI/HyperOS
 * agar AlarmManager bisa trigger tepat waktu.
 *
 * Notifikasinya sangat kecil (priority MIN) — hampir tidak terlihat.
 */
public class KeepAliveService extends Service {

    private static final String CHANNEL_ID  = "maxdis_keepalive";
    private static final int    NOTIF_ID    = 9001;
    private static final long   PING_INTERVAL = 60 * 1000L; // 1 menit

    private Handler handler;
    private Runnable pingRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Foreground dengan notifikasi minimal
        startForeground(NOTIF_ID, buildKeepAliveNotif());

        // Ping tiap 1 menit — pastikan AlarmScheduler masih terdaftar
        pingRunnable = new Runnable() {
            @Override
            public void run() {
                AlarmScheduler.scheduleAll(getApplicationContext());
                handler.postDelayed(this, PING_INTERVAL);
            }
        };
        handler.postDelayed(pingRunnable, PING_INTERVAL);

        // START_STICKY → Android/MIUI restart service ini jika dikill
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Maxdis Berjalan", NotificationManager.IMPORTANCE_MIN);
        ch.setDescription("Menjaga alarm maxdisplay tetap aktif");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableLights(false);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildKeepAliveNotif() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("Maxdis Assistant")
                .setContentText("Alarm pengingat maxdisplay aktif")
                .setPriority(NotificationCompat.PRIORITY_MIN)  // paling kecil, nyaris tersembunyi
                .setOngoing(true)
                .setShowWhen(false)
                .setSilent(true)
                .setContentIntent(piOpen)
                .build();
    }

    @Override
    public void onDestroy() {
        if (handler != null && pingRunnable != null)
            handler.removeCallbacks(pingRunnable);
        // Restart diri sendiri jika dikill
        Intent restart = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
