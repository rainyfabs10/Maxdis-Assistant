package com.alfamart.maxdis;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";

    // 1 jam sebelum jadwal maxdis: 10,14,18,21 → 9,13,17,20
    private static final int[] ALARM_HOURS = {9, 13, 17, 20};
    private static final int   HOUR_24JAM  = 2; // 1 jam sebelum 03:00

    public static void scheduleAll(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(
                AlarmReceiver.PREF_NAME, Context.MODE_PRIVATE);
        boolean alarmOn  = prefs.getBoolean(AlarmReceiver.PREF_ALARM_ON, true);
        boolean toko24On = prefs.getBoolean(AlarmReceiver.PREF_TOKO24, false);

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        cancelAll(ctx, am);
        if (!alarmOn) { Log.d(TAG, "Alarm OFF, skipping schedule"); return; }

        for (int i = 0; i < ALARM_HOURS.length; i++) {
            scheduleOne(ctx, am, ALARM_HOURS[i], i);
        }
        if (toko24On) {
            scheduleOne(ctx, am, HOUR_24JAM, 99);
        }
    }

    private static void scheduleOne(Context ctx, AlarmManager am, int hour, int reqCode) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Jika jam sudah lewat hari ini → jadwalkan besok
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        PendingIntent pi = buildPI(ctx, hour, reqCode);

        // Gunakan setExactAndAllowWhileIdle agar tidak diblock Doze/MIUI
        // Untuk repeat: kita reschedule manual di AlarmReceiver setelah trigger
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }

        Log.d(TAG, "Scheduled alarm jam " + hour + ":00 → " + cal.getTime());
    }

    /** Jadwalkan ulang untuk besok — dipanggil dari AlarmReceiver setelah trigger */
    public static void rescheduleOne(Context ctx, int hour, int reqCode) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, 1); // selalu besok

        PendingIntent pi = buildPI(ctx, hour, reqCode);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }

        Log.d(TAG, "Rescheduled alarm jam " + hour + ":00 → besok " + cal.getTime());
    }

    public static void cancelAll(Context ctx, AlarmManager am) {
        int[] codes = {0, 1, 2, 3, 99};
        int[] hours = {9, 13, 17, 20, 2};
        for (int i = 0; i < codes.length; i++) {
            PendingIntent pi = PendingIntent.getBroadcast(ctx, codes[i],
                    buildIntent(ctx, hours[i]),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) am.cancel(pi);
        }
    }

    private static PendingIntent buildPI(Context ctx, int hour, int reqCode) {
        return PendingIntent.getBroadcast(ctx, reqCode, buildIntent(ctx, hour),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent buildIntent(Context ctx, int hour) {
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.setAction(AlarmReceiver.ACTION_ALARM);
        intent.putExtra(AlarmReceiver.EXTRA_HOUR, hour);
        intent.putExtra(AlarmReceiver.EXTRA_REQ_CODE, getReqCode(hour));
        return intent;
    }

    private static int getReqCode(int hour) {
        if (hour == 9)  return 0;
        if (hour == 13) return 1;
        if (hour == 17) return 2;
        if (hour == 20) return 3;
        if (hour == 2)  return 99;
        return 0;
    }
}
