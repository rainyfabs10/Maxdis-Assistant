package com.alfamart.maxdis;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Helper untuk menjadwalkan / membatalkan semua alarm harian maxdisplay.
 * Dipanggil dari MainActivity dan AlarmReceiver (saat boot).
 */
public class AlarmScheduler {

    // Alarm 1 jam sebelum jadwal maxdis: 10, 14, 18, 21 → 9, 13, 17, 20
    private static final int[] ALARM_HOURS    = {9, 13, 17, 20};
    private static final int   HOUR_24JAM     = 2; // 1 jam sebelum sesi 03:00

    public static void scheduleAll(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(
                AlarmReceiver.PREF_NAME, Context.MODE_PRIVATE);
        boolean alarmOn  = prefs.getBoolean(AlarmReceiver.PREF_ALARM_ON, true);
        boolean toko24On = prefs.getBoolean(AlarmReceiver.PREF_TOKO24, false);

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        cancelAll(ctx, am);
        if (!alarmOn) return;

        for (int i = 0; i < ALARM_HOURS.length; i++) {
            schedule(ctx, am, ALARM_HOURS[i], i);
        }
        if (toko24On) {
            schedule(ctx, am, HOUR_24JAM, 99);
        }
    }

    private static void schedule(Context ctx, AlarmManager am, int hour, int reqCode) {
        PendingIntent pi = buildPendingIntent(ctx, hour, reqCode);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        // setRepeating setiap hari
        am.setRepeating(AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pi);
    }

    public static void cancelAll(Context ctx, AlarmManager am) {
        int[] codes = {0, 1, 2, 3, 99};
        for (int code : codes) {
            // hour tidak penting untuk cancel, cukup reqCode sama
            PendingIntent pi = PendingIntent.getBroadcast(ctx, code,
                    new Intent(AlarmReceiver.ACTION_ALARM),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) am.cancel(pi);
        }
    }

    private static PendingIntent buildPendingIntent(Context ctx, int hour, int reqCode) {
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_ALARM);
        intent.putExtra(AlarmReceiver.EXTRA_HOUR, hour);
        return PendingIntent.getBroadcast(ctx, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
