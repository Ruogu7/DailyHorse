package com.dailyrecord.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Foreground location monitor implementing the daily arrival and exit rules. */
public class MonitorService extends Service implements LocationListener {
    private static final double FENCE_RADIUS_METERS = 80.0;
    private static final long EXIT_ALERT_COOLDOWN_MS = 30L * 60L * 1000L;
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_STARTUP_CHECK_PENDING = "eveningStartupCheckPending";
    private static final String KEY_LAST_EXIT_ALERT_AT = "lastEveningAlertAt";

    private SharedPreferences prefs;
    private LocationManager manager;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("daily", MODE_PRIVATE);
        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        createNotificationChannels();
        startForeground(1, buildNotification("正在监测打卡地点", "monitor"));

        if (!prefs.getBoolean(KEY_ENABLED, false)) {
            setState("提醒未启用");
            stopSelf();
            return;
        }
        if (!hasRequiredLocationPermissions()) {
            setState("缺少始终允许的定位权限，请授权后重新启动");
            prefs.edit().putBoolean(KEY_ENABLED, false).apply();
            stopSelf();
            return;
        }
        if (savedPlaceCount() == 0) {
            setState("没有打卡地点，监控已停止");
            prefs.edit().putBoolean(KEY_ENABLED, false).apply();
            stopSelf();
            return;
        }

        try {
            int registeredProviders = 0;
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (!manager.getAllProviders().contains(provider)) continue;
                try {
                    // Keep listeners registered while a provider is disabled so monitoring resumes when enabled.
                    manager.requestLocationUpdates(provider, 4000L, 0.0f, this);
                    registeredProviders++;
                } catch (IllegalArgumentException e) {
                    android.util.Log.w("DailyRecord", "Location provider unavailable: " + provider, e);
                }
            }
            if (registeredProviders == 0) {
                setState("没有可用的定位源，监控已停止");
                prefs.edit().putBoolean(KEY_ENABLED, false).apply();
                stopSelf();
                return;
            }

            boolean locationEnabled = hasEnabledProvider();
            setState(locationEnabled
                    ? "后台定位监控运行中（目标间隔 4 秒）"
                    : "定位服务已关闭；开启后将继续监控");
            Location latest = latestKnownLocation();
            if (latest != null && System.currentTimeMillis() - latest.getTime() <= 120000L) {
                onLocationChanged(latest);
            }
        } catch (SecurityException e) {
            setState("定位权限不足：" + e.getClass().getSimpleName());
            android.util.Log.e("DailyRecord", "Location permission lost", e);
            stopSelf();
        } catch (Exception e) {
            setState("定位启动失败：" + e.getClass().getSimpleName());
            android.util.Log.e("DailyRecord", "Location start failed", e);
            stopSelf();
        }
    }

    private boolean hasRequiredLocationPermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return false;
        return Build.VERSION.SDK_INT < 29
                || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasEnabledProvider() {
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            if (!manager.getAllProviders().contains(provider)) continue;
            try {
                if (manager.isProviderEnabled(provider)) return true;
            } catch (IllegalArgumentException ignored) { }
        }
        return false;
    }

    private int savedPlaceCount() {
        try { return new JSONArray(prefs.getString("places", "[]")).length(); }
        catch (Exception e) { return 0; }
    }

    private Location latestKnownLocation() {
        Location latest = null;
        for (String provider : manager.getProviders(true)) {
            try {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (latest == null || candidate.getTime() > latest.getTime())) {
                    latest = candidate;
                }
            } catch (SecurityException e) {
                android.util.Log.w("DailyRecord", "Cannot read last known location from " + provider, e);
            }
        }
        return latest;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        NotificationChannel monitor = new NotificationChannel(
                "monitor", "DailyRecord 定位状态", NotificationManager.IMPORTANCE_LOW);
        notifications.createNotificationChannel(monitor);

        NotificationChannel alerts = new NotificationChannel(
                "alerts", "DailyRecord 闹铃提醒", NotificationManager.IMPORTANCE_HIGH);
        alerts.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        alerts.enableVibration(true);
        alerts.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 800});
        notifications.createNotificationChannel(alerts);
    }

    private Notification buildNotification(String text, String channel) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channel) : new Notification.Builder(this);
        return builder.setContentTitle("DailyRecord")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(contentIntent)
                .build();
    }

    private void setState(String state) {
        prefs.edit().putString("monitorState", state).apply();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.getBoolean(KEY_ENABLED, false)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override public void onLocationChanged(Location location) {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return;
        try {
            JSONArray places = new JSONArray(prefs.getString("places", "[]"));
            long now = System.currentTimeMillis();
            String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(now));
            String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(now));
            String morningStart = prefs.getString("morningStart", "07:00");
            String morningEnd = prefs.getString("morningEnd", "09:00");
            String eveningStart = prefs.getString("eveningStart", "17:30");
            String eveningEnd = prefs.getString("eveningEnd", "23:30");
            boolean morningWindow = time.compareTo(morningStart) >= 0 && time.compareTo(morningEnd) < 0;
            boolean eveningWindow = time.compareTo(eveningStart) >= 0 && time.compareTo(eveningEnd) < 0;

            double nearest = Double.MAX_VALUE;
            String nearestName = "打卡地点";
            boolean nearAnyOffice = false;
            boolean enteredAnyOffice = false;
            Set<String> enteredNames = new LinkedHashSet<>();
            Set<String> exitedNames = new LinkedHashSet<>();
            SharedPreferences.Editor state = prefs.edit();

            for (int i = 0; i < places.length(); i++) {
                JSONObject place = places.optJSONObject(i);
                if (place == null) continue;
                String id = place.optString("id", String.valueOf(i));
                String name = place.optString("name", "打卡地点");
                float[] distance = new float[1];
                Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                        place.getDouble("lat"), place.getDouble("lon"), distance);

                boolean isInside = distance[0] <= FENCE_RADIUS_METERS;
                String insideKey = "inside_" + id;
                boolean hadBaseline = prefs.contains(insideKey);
                boolean wasInside = prefs.getBoolean(insideKey, isInside);
                if (distance[0] < nearest) {
                    nearest = distance[0];
                    nearestName = name;
                }
                if (isInside) nearAnyOffice = true;

                // A first fix inside during the morning counts as the initial arrival.
                if (isInside && (!hadBaseline || !wasInside)) {
                    enteredAnyOffice = true;
                    enteredNames.add(name);
                }
                if (hadBaseline && wasInside && !isInside && eveningWindow) {
                    exitedNames.add(name);
                }
                state.putBoolean(insideKey, isInside);
            }

            boolean startupCheckPending = prefs.getBoolean(KEY_STARTUP_CHECK_PENDING, false);
            if (eveningWindow && startupCheckPending) {
                // Consume the one-time start/boot check after the first usable evening fix.
                state.putBoolean(KEY_STARTUP_CHECK_PENDING, false);
            }
            if (nearest == Double.MAX_VALUE) nearest = -1.0;
            state.putLong("lastLocationAt", now)
                    .putFloat("lastLatitude", (float) location.getLatitude())
                    .putFloat("lastLongitude", (float) location.getLongitude())
                    .putFloat("lastNearestMeters", (float) nearest)
                    .putString("lastNearestName", nearestName)
                    .putString("monitorState", nearest < 0
                            ? "定位正常，暂无打卡地点"
                            : "定位正常，最近地点 " + Math.round(nearest) + " 米")
                    .apply();

            if (morningWindow && enteredAnyOffice
                    && !day.equals(prefs.getString("morningDay", ""))) {
                String names = String.join("、", enteredNames);
                prefs.edit().putString("morningDay", day).apply();
                alert("早间首次进入打卡地点 80 米范围：" + names, "morning", day);
            }

            if (eveningWindow && startupCheckPending && places.length() > 0 && !nearAnyOffice) {
                sendEveningAlert("监控启动时，当前位置已在所有打卡地点 80 米范围外", now);
            } else if (eveningWindow && !exitedNames.isEmpty()) {
                sendEveningAlert("已走出 80 米围栏：" + String.join("、", exitedNames), now);
            }
        } catch (Exception e) {
            setState("处理定位时出错：" + e.getClass().getSimpleName());
            android.util.Log.e("DailyRecord", "Location processing failed", e);
        }
    }

    private void sendEveningAlert(String text, long now) {
        long lastAlertAt = prefs.getLong(KEY_LAST_EXIT_ALERT_AT, 0L);
        if (lastAlertAt > 0L && now - lastAlertAt <= EXIT_ALERT_COOLDOWN_MS) {
            setState("已在 80 米范围外；距离上次晚间提醒不足 30 分钟，已抑制提醒");
            return;
        }
        prefs.edit().putLong(KEY_LAST_EXIT_ALERT_AT, now).apply();
        alert(text, "exit", String.valueOf(now));
    }

    private void alert(String text, String tag, String id) {
        NotificationManager notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "alerts") : new Notification.Builder(this);
        builder.setContentTitle("DailyRecord 打卡提醒")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    .setVibrate(new long[]{0, 500, 250, 500, 250, 800});
        }
        notifications.notify((tag + id).hashCode(), builder.build());
    }

    @Override public void onProviderEnabled(String provider) {
        setState("定位已恢复，正在监控打卡地点");
    }

    @Override public void onProviderDisabled(String provider) {
        setState(hasEnabledProvider()
                ? "A location provider changed; monitoring continues"
                : "Location is off; monitoring will resume when enabled");
    }

    @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) { }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        try { if (manager != null) manager.removeUpdates(this); }
        catch (Exception ignored) { }
        super.onDestroy();
    }
}
