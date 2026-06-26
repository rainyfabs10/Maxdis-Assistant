package com.alfamart.maxdis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Menerima broadcast dari AlarmManager (alarm harian & boot completed).
 * Langsung start AlarmService di foreground agar bisa bunyi di background.
 */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_ALARM    = "com.alfamart.maxdis.ALARM";
    public static final String ACTION_STOP     = "com.alfamart.maxdis.STOP_ALARM";
    public static final String EXTRA_HOUR      = "hour";
    public static final String PREF_NAME       = "MaxdisPrefs";
    public static final String PREF_ALARM_ON   = "alarm_on";
    public static final String PREF_TOKO24     = "toko24_on";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean alarmOn = prefs.getBoolean(PREF_ALARM_ON, true);

        // Saat HP restart → pasang ulang semua alarm
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            AlarmScheduler.scheduleAll(context);
            return;
        }

        // Alarm harian tiba
        if (ACTION_ALARM.equals(action) && alarmOn) {
            int hour = intent.getIntExtra(EXTRA_HOUR, -1);
            if (hour < 0) return;

            Intent svcIntent = new Intent(context, AlarmService.class);
            svcIntent.putExtra(EXTRA_HOUR, hour);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svcIntent);
            } else {
                context.startService(svcIntent);
            }
        }

        // Stop dari notifikasi
        if (ACTION_STOP.equals(action)) {
            Intent svcIntent = new Intent(context, AlarmService.class);
            svcIntent.setAction(ACTION_STOP);
            context.startService(svcIntent);
        }
    }
}
