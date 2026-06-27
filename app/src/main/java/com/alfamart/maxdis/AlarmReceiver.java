package com.alfamart.maxdis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;

public class AlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_ALARM    = "com.alfamart.maxdis.ALARM";
    public static final String EXTRA_HOUR      = "hour";
    public static final String EXTRA_REQ_CODE  = "req_code";
    public static final String PREF_NAME       = "MaxdisPrefs";
    public static final String PREF_ALARM_ON   = "alarm_on";
    public static final String PREF_TOKO24     = "toko24_on";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        // Acquire WakeLock segera agar CPU tidak tidur sebelum service start
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = null;
        if (pm != null) {
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "MaxdisAssistant::ReceiverWakeLock");
            wl.acquire(30 * 1000L); // 30 detik cukup untuk start service
        }

        try {
            String action = intent.getAction();
            if (action == null) return;

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            boolean alarmOn = prefs.getBoolean(PREF_ALARM_ON, true);

            if (ACTION_ALARM.equals(action) && alarmOn) {
                int hour    = intent.getIntExtra(EXTRA_HOUR, -1);
                int reqCode = intent.getIntExtra(EXTRA_REQ_CODE, 0);
                if (hour < 0) return;

                int sesiJam = hour + 1;

                // Start AlarmService
                Intent svcIntent = new Intent(context, AlarmService.class);
                svcIntent.setAction(AlarmService.ACTION_START);
                svcIntent.putExtra(AlarmService.EXTRA_SESI_JAM, sesiJam);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent);
                } else {
                    context.startService(svcIntent);
                }

                // Jadwalkan ulang untuk besok (karena pakai setExact, tidak auto-repeat)
                AlarmScheduler.rescheduleOne(context, hour, reqCode);
            }
        } finally {
            if (wl != null && wl.isHeld()) wl.release();
        }
    }
}
