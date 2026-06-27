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
 * Foreground service ringan — jaga proses hidup di MIUI/HyperOS.
 * START_STICKY = Android restart otomatis jika dikill.
 * onDestroy = restart diri sendiri sebagai failsafe.
 */
public class KeepAliveService extends Service {

    private static final String CH_ID   = "maxdis_ka_v3";
    private static final int    NOTIF_ID = 4002;
    private static final long   INTERVAL = 15 * 60 * 1000L; // 15 menit

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
        startForeground(NOTIF_ID, buildNotif());

        // Ping tiap 15 menit: pastikan alarm masih terjadwal
        if (pingRunnable == null) {
            pingRunnable = new Runnable() {
                @Override public void run() {
                    AlarmScheduler.scheduleAll(getApplicationContext());
                    handler.postDelayed(this, INTERVAL);
                }
            };
            handler.postDelayed(pingRunnable, INTERVAL);
        }

        return START_STICKY; // restart otomatis jika dikill sistem
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH_ID) != null) return; // sudah ada

        NotificationChannel ch = new NotificationChannel(
                CH_ID, "Maxdis Berjalan", NotificationManager.IMPORTANCE_MIN);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotif() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CH_ID)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("Maxdis Assistant")
                .setContentText("Alarm pengingat aktif di background")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setShowWhen(false)
                .setSilent(true)
                .setContentIntent(pi)
                .build();
    }

    @Override
    public void onDestroy() {
        if (handler != null && pingRunnable != null)
            handler.removeCallbacks(pingRunnable);

        // Failsafe: restart diri sendiri
        handler.postDelayed(() -> {
            Intent restart = new Intent(getApplicationContext(), KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(restart);
            else
                startService(restart);
        }, 1000);

        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
