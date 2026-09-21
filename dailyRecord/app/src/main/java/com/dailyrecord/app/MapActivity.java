package com.dailyrecord.app;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.CoordinateConverter;
import com.amap.api.maps.CoordinateConverter.CoordType;

import org.json.JSONArray;
import org.json.JSONObject;

/** Map view for all saved check-in places and the handset's live system location. */
public class MapActivity extends Activity implements LocationListener {
    private MapView mapView;
    private AMap map;
    private LocationManager locationManager;
    private SharedPreferences prefs;
    private Marker currentMarker;
    private TextView hint;
    private boolean mapLoaded;
    private boolean initialCameraSet;
    private boolean userMovedMap;
    private com.amap.api.maps.model.BitmapDescriptor officeIcon;
    private com.amap.api.maps.model.BitmapDescriptor personIcon;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("daily", MODE_PRIVATE);
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        officeIcon = markerIcon(R.drawable.ic_marker_office);
        personIcon = markerIcon(R.drawable.ic_marker_person);
        buildView(state);
    }

    private void buildView(Bundle state) {
        FrameLayout frame = new FrameLayout(this);
        mapView = new MapView(this);
        mapView.onCreate(state);
        map = mapView.getMap();
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.setOnMapLoadedListener(() -> { mapLoaded = true; frameMarkersIfReady(); });
        map.setOnMapTouchListener(event -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) userMovedMap = true;
        });
        frame.addView(mapView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.WHITE);
        panelBg.setCornerRadius(dp(20));
        panelBg.setStroke(dp(1), Color.rgb(235, 237, 233));
        panel.setBackground(panelBg);
        panel.setElevation(dp(8));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("地点地图");
        title.setTextColor(Color.rgb(18, 56, 72));
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleStack.addView(title);
        TextView legend = new TextView(this);
        String legendText = "● 手机位置     ● 打卡地点";
        SpannableString legendStyled = new SpannableString(legendText);
        legendStyled.setSpan(new ForegroundColorSpan(Color.rgb(30, 145, 205)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int placeDot = legendText.indexOf("●", 1);
        if (placeDot >= 0) legendStyled.setSpan(new ForegroundColorSpan(Color.rgb(242, 151, 45)), placeDot, placeDot + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        legend.setText(legendStyled);
        legend.setTextColor(Color.rgb(105, 115, 120));
        legend.setTextSize(12);
        LinearLayout.LayoutParams legendLp = new LinearLayout.LayoutParams(-2, -2);
        legendLp.topMargin = dp(3);
        titleStack.addView(legend, legendLp);
        titleRow.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = new TextView(this);
        count.setText(savedPlaceCount() + " 个地点");
        count.setTextColor(Color.rgb(18, 86, 112));
        count.setTextSize(12);
        count.setGravity(Gravity.CENTER);
        count.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable countBg = new GradientDrawable();
        countBg.setColor(Color.rgb(232, 243, 245));
        countBg.setCornerRadius(dp(14));
        count.setBackground(countBg);
        titleRow.addView(count);
        panel.addView(titleRow);
        hint = new TextView(this);
        hint.setText(savedPlaceCount() == 0 ? "还没有打卡地点，请先在设置中添加" : "正在获取手机位置…");
        hint.setTextColor(Color.rgb(65, 83, 91));
        hint.setTextSize(12);
        hint.setSingleLine(true);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, dp(30));
        hintLp.topMargin = dp(7);
        panel.addView(hint, hintLp);
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        panelLp.setMargins(dp(16), dp(14), dp(16), 0);
        frame.addView(panel, panelLp);

        TextView center = new TextView(this);
        center.setText("⌖");
        center.setTextSize(29);
        center.setTextColor(Color.rgb(18, 86, 112));
        center.setGravity(Gravity.CENTER);
        center.setContentDescription("定位到手机当前位置");
        GradientDrawable centerBg = new GradientDrawable();
        centerBg.setShape(GradientDrawable.OVAL);
        centerBg.setColor(Color.WHITE);
        centerBg.setStroke(dp(1), Color.rgb(230, 234, 231));
        center.setBackground(centerBg);
        center.setElevation(dp(8));
        center.setOnClickListener(v -> {
            if (currentMarker != null) {
                userMovedMap = false;
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentMarker.getPosition(), 16));
            } else Toast.makeText(this, "正在等待定位", Toast.LENGTH_SHORT).show();
        });
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.END);
        centerLp.setMargins(0, 0, dp(18), dp(28));
        frame.addView(center, centerLp);
        setContentView(frame);
        showSavedPlaces();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private com.amap.api.maps.model.BitmapDescriptor markerIcon(int resourceId) {
        Drawable drawable = getDrawable(resourceId);
        int width = dp(48), height = dp(56);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private LatLng toMap(double latitude, double longitude) {
        try {
            LatLng converted = new CoordinateConverter(this).from(CoordType.GPS).coord(new LatLng(latitude, longitude)).convert();
            return converted == null ? new LatLng(latitude, longitude) : converted;
        } catch (Exception e) {
            android.util.Log.w("DailyRecordMap", "Coordinate conversion failed; using source coordinate", e);
            return new LatLng(latitude, longitude);
        }
    }

    private int savedPlaceCount() {
        try { return new JSONArray(prefs.getString("places", "[]")).length(); }
        catch (Exception e) { return 0; }
    }

    private void showSavedPlaces() {
        if (map == null) return;
        map.clear();
        currentMarker = null;
        JSONArray places;
        try { places = new JSONArray(prefs.getString("places", "[]")); }
        catch (Exception e) { places = new JSONArray(); }

        LatLng first = null;
        for (int i = 0; i < places.length(); i++) {
            JSONObject p = places.optJSONObject(i);
            if (p == null) continue;
            try {
                LatLng point = toMap(p.getDouble("lat"), p.getDouble("lon"));
                if (first == null) first = point;
                map.addMarker(new MarkerOptions().position(point).title(p.optString("name", "打卡地点"))
                        .snippet(String.format(java.util.Locale.getDefault(), "%.6f, %.6f", p.getDouble("lat"), p.getDouble("lon")))
                        .icon(officeIcon).anchor(0.5f, 1.0f).zIndex(5));
            } catch (Exception e) { android.util.Log.e("DailyRecordMap", "Could not add saved place marker", e); }
        }

        long at = prefs.getLong("lastLocationAt", 0);
        if (at > 0 && System.currentTimeMillis() - at <= 120000) {
            showPhone(prefs.getFloat("lastLatitude", 0), prefs.getFloat("lastLongitude", 0), false);
        } else if (first != null && !initialCameraSet) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(first, 14));
        }
        frameMarkersIfReady();
    }

    private void showPhone(double latitude, double longitude, boolean animate) {
        LatLng point = toMap(latitude, longitude);
        if (currentMarker == null) {
            currentMarker = map.addMarker(new MarkerOptions().position(point).title("手机当前位置")
                    .snippet(String.format(java.util.Locale.getDefault(), "%.6f, %.6f", latitude, longitude))
                    .icon(personIcon).anchor(0.5f, 1.0f).zIndex(10));
        } else {
            currentMarker.setPosition(point);
            currentMarker.setSnippet(String.format(java.util.Locale.getDefault(), "%.6f, %.6f", latitude, longitude));
        }
        if (animate && !userMovedMap) map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15));
        hint.setText(String.format(java.util.Locale.getDefault(), "● 手机实时位置  %.5f, %.5f  ·  ● 打卡地点 %d 个", latitude, longitude, savedPlaceCount()));
        frameMarkersIfReady();
    }

    private void frameMarkersIfReady() {
        if (!mapLoaded || initialCameraSet || userMovedMap || map == null || currentMarker == null) return;
        LatLngBounds.Builder bounds = LatLngBounds.builder();
        boolean any = false;
        int pointCount = 0;
        JSONArray places;
        try { places = new JSONArray(prefs.getString("places", "[]")); }
        catch (Exception e) { places = new JSONArray(); }
        for (int i = 0; i < places.length(); i++) {
            JSONObject p = places.optJSONObject(i);
            if (p == null) continue;
            try { bounds.include(toMap(p.getDouble("lat"), p.getDouble("lon"))); any = true; pointCount++; }
            catch (Exception ignored) { }
        }
        if (currentMarker != null) { bounds.include(currentMarker.getPosition()); any = true; pointCount++; }
        if (!any) return;
        try {
            if (pointCount == 1) map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentMarker.getPosition(), 15));
            else {
                LatLngBounds all = bounds.build();
                if (Math.abs(all.northeast.latitude - all.southwest.latitude) < 0.0004
                        && Math.abs(all.northeast.longitude - all.southwest.longitude) < 0.0004)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentMarker.getPosition(), 16));
                else map.animateCamera(CameraUpdateFactory.newLatLngBounds(all, dp(72)));
            }
            initialCameraSet = true;
        } catch (Exception ignored) { }
    }

    private void beginLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 31);
            return;
        }
        try {
            boolean requested = false;
            for (String provider : locationManager.getProviders(true)) {
                if (LocationManager.GPS_PROVIDER.equals(provider) || LocationManager.NETWORK_PROVIDER.equals(provider)) {
                    locationManager.requestLocationUpdates(provider, 4000, 0, this, Looper.getMainLooper());
                    requested = true;
                }
            }
            if (!requested) hint.setText("手机定位已关闭，请在系统设置中开启定位服务");
            Location latest = null;
            for (String provider : locationManager.getProviders(true)) {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate != null && (latest == null || candidate.getTime() > latest.getTime())) latest = candidate;
            }
            if (latest != null) showPhone(latest.getLatitude(), latest.getLongitude(), !initialCameraSet);
        } catch (SecurityException e) {
            hint.setText(savedPlaceCount() + " 个打卡地点已显示；授予定位权限后显示手机位置");
        }
    }

    @Override public void onLocationChanged(Location location) {
        showPhone(location.getLatitude(), location.getLongitude(), false);
        prefs.edit().putLong("mapLastLocationAt", location.getTime()).apply();
    }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();
        initialCameraSet = false;
        showSavedPlaces();
        beginLocation();
    }
    @Override protected void onPause() {
        try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        mapView.onPause();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); mapView.onSaveInstanceState(out); }
    @Override protected void onDestroy() { mapView.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}
