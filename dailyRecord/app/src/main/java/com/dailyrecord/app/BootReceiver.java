package com.dailyrecord.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Restores the explicitly enabled monitor after boot or an app update. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;

        android.content.SharedPreferences prefs = context.getSharedPreferences("daily", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("enabled", false)) return;

        prefs.edit().putBoolean("eveningStartupCheckPending", true).apply();
        Intent service = new Intent(context, MonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (RuntimeException e) {
            prefs.edit().putString("monitorState", "开机恢复监控失败：" + e.getClass().getSimpleName()).apply();
            android.util.Log.e("DailyRecord", "Could not restart location monitor", e);
        }
    }
}
