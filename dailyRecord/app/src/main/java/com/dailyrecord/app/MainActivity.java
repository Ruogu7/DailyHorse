package com.dailyrecord.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.amap.api.maps.MapsInitializer;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    SharedPreferences prefs;
    TextView status,positionInfo,nearestInfo,monitorInfo;
    final Handler uiHandler=new Handler(Looper.getMainLooper());
    final Runnable refreshTask=new Runnable(){@Override public void run(){refreshLocation();refreshMonitorInfo();uiHandler.postDelayed(this,4000);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);MapsInitializer.updatePrivacyShow(this,true,true);MapsInitializer.updatePrivacyAgree(this,true);prefs=getSharedPreferences("daily",0);showHome();}
    void showHome(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(dp(24),dp(12),dp(24),dp(24));root.setBackgroundColor(Color.rgb(250,247,239));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);Button mapButton=new Button(this);mapButton.setText("地图");mapButton.setTextSize(15);mapButton.setAllCaps(false);mapButton.setTextColor(Color.rgb(35,55,65));mapButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(232,235,232)));mapButton.setOnClickListener(v->startActivity(new Intent(this,MapActivity.class)));top.addView(mapButton,new LinearLayout.LayoutParams(dp(82),dp(48)));ImageButton settings=new ImageButton(this);settings.setImageResource(R.drawable.ic_settings);settings.setContentDescription("设置");settings.setScaleType(ImageView.ScaleType.CENTER);settings.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(232,235,232)));settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));LinearLayout.LayoutParams settingsLp=new LinearLayout.LayoutParams(dp(48),dp(48));settingsLp.leftMargin=dp(8);top.addView(settings,settingsLp);root.addView(top,new LinearLayout.LayoutParams(-1,dp(56)));
        ImageView logo=new ImageView(this);logo.setImageResource(com.dailyrecord.app.R.mipmap.ic_launcher);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(174),dp(174));lp.bottomMargin=dp(8);root.addView(logo,lp);
        TextView title=new TextView(this);title.setText("DailyRecord");title.setTextSize(28);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setTextColor(Color.rgb(18,56,72));title.setGravity(Gravity.CENTER);root.addView(title);
        TextView sub=new TextView(this);sub.setText("地点打卡提醒");sub.setTextSize(15);sub.setTextColor(Color.rgb(95,105,110));sub.setGravity(Gravity.CENTER);LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2);subLp.bottomMargin=dp(18);root.addView(sub,subLp);
        LinearLayout infoCard=new LinearLayout(this);infoCard.setOrientation(LinearLayout.VERTICAL);infoCard.setPadding(dp(18),dp(14),dp(18),dp(14));GradientDrawable cardBg=new GradientDrawable();cardBg.setColor(Color.WHITE);cardBg.setCornerRadius(dp(18));cardBg.setStroke(dp(1),Color.rgb(232,230,220));infoCard.setBackground(cardBg);
        status=new TextView(this);status.setTextSize(16);status.setTypeface(Typeface.DEFAULT,Typeface.BOLD);status.setTextColor(Color.rgb(25,100,75));status.setGravity(Gravity.CENTER_VERTICAL);status.setPadding(0,0,0,dp(8));infoCard.addView(status);refreshStatus();
        positionInfo=new TextView(this);positionInfo.setTextSize(14);positionInfo.setTextColor(Color.rgb(45,65,75));positionInfo.setPadding(0,dp(5),0,dp(5));infoCard.addView(positionInfo);
        nearestInfo=new TextView(this);nearestInfo.setTextSize(14);nearestInfo.setTextColor(Color.rgb(45,65,75));nearestInfo.setPadding(0,dp(5),0,dp(5));infoCard.addView(nearestInfo);
        monitorInfo=new TextView(this);monitorInfo.setTextSize(12);monitorInfo.setTextColor(Color.rgb(105,112,115));monitorInfo.setPadding(0,dp(5),0,0);infoCard.addView(monitorInfo);refreshLocation();refreshMonitorInfo();LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);cardLp.bottomMargin=dp(12);root.addView(infoCard,cardLp);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER);Button start=new Button(this);start.setText("启动提醒");start.setTextSize(16);start.setAllCaps(false);start.setTypeface(Typeface.DEFAULT,Typeface.BOLD);start.setTextColor(Color.WHITE);start.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(18,86,112)));start.setOnClickListener(v->startMonitoring());Button stop=new Button(this);stop.setText("停止提醒");stop.setTextSize(16);stop.setAllCaps(false);stop.setTypeface(Typeface.DEFAULT,Typeface.BOLD);stop.setTextColor(Color.rgb(125,35,35));stop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(250,226,218)));stop.setOnClickListener(v->{prefs.edit().putBoolean("enabled",false).apply();stopService(new Intent(this,MonitorService.class));refreshStatus();Toast.makeText(this,"提醒已停止",Toast.LENGTH_SHORT).show();});LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(60),1);bp.setMargins(dp(4),dp(4),dp(6),dp(8));actions.addView(start,bp);LinearLayout.LayoutParams bp2=new LinearLayout.LayoutParams(0,dp(60),1);bp2.setMargins(dp(6),dp(4),dp(4),dp(8));actions.addView(stop,bp2);root.addView(actions);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
    }
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onResume(){super.onResume();if(status!=null){refreshStatus();uiHandler.removeCallbacks(refreshTask);refreshTask.run();}}
    @Override protected void onPause(){uiHandler.removeCallbacks(refreshTask);super.onPause();}
    void refreshStatus(){if(status!=null)status.setText(prefs.getBoolean("enabled",false)?"● 提醒运行中":"○ 提醒已停止");}
    void refreshMonitorInfo(){if(monitorInfo==null)return;String state=prefs.getString("monitorState","尚未启动定位监测");long at=prefs.getLong("lastLocationAt",0);String age=at==0?"尚未收到定位更新":"最近定位更新："+new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(new java.util.Date(at));monitorInfo.setText(state+"\n"+age);}
    void refreshLocation(){if(positionInfo==null)return;if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){positionInfo.setText("当前位置：尚未授予定位权限");nearestInfo.setText("最近地点距离：—");return;}try{Location current=null;long update=prefs.getLong("lastLocationAt",0);if(update>0&&System.currentTimeMillis()-update<120000){current=new Location("DailyRecord");current.setLatitude(prefs.getFloat("lastLatitude",0));current.setLongitude(prefs.getFloat("lastLongitude",0));current.setTime(update);}else{LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);for(String provider:lm.getProviders(true)){Location p=lm.getLastKnownLocation(provider);if(p!=null&&(current==null||p.getTime()>current.getTime()))current=p;}}if(current==null){positionInfo.setText("当前位置：暂时无法获取");nearestInfo.setText("最近地点距离：暂时无法计算");return;}positionInfo.setText(String.format(java.util.Locale.getDefault(),"当前位置：%.6f, %.6f",current.getLatitude(),current.getLongitude()));JSONArray places=new JSONArray(prefs.getString("places","[]"));float min=Float.MAX_VALUE;String closest="";for(int i=0;i<places.length();i++){JSONObject p=places.optJSONObject(i);if(p==null)continue;float[] d=new float[1];Location.distanceBetween(current.getLatitude(),current.getLongitude(),p.getDouble("lat"),p.getDouble("lon"),d);if(d[0]<min){min=d[0];closest=p.optString("name","地点");}}nearestInfo.setText(min==Float.MAX_VALUE?"最近地点距离：尚未添加地点":String.format(java.util.Locale.getDefault(),"最近地点：%s，距离约 %.0f 米",closest,min));}catch(SecurityException e){positionInfo.setText("当前位置：定位权限不足");nearestInfo.setText("最近地点距离：—");}catch(Exception e){positionInfo.setText("当前位置：暂时无法获取");nearestInfo.setText("最近地点距离：暂时无法计算");}}
    void startMonitoring(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPerms();Toast.makeText(this,"请先授予精确定位权限",Toast.LENGTH_LONG).show();return;}if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"请在应用设置中将位置权限设为“始终允许”",Toast.LENGTH_LONG).show();startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));return;}if(prefs.getString("places","[]").equals("[]")){Toast.makeText(this,"请先在设置中添加至少一个地点",Toast.LENGTH_LONG).show();startActivity(new Intent(this,SettingsActivity.class));return;}prefs.edit().putBoolean("enabled",true).apply();Intent i=new Intent(this,MonitorService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);refreshStatus();Toast.makeText(this,"提醒已启动",Toast.LENGTH_SHORT).show();}
    void requestPerms(){if(Build.VERSION.SDK_INT>=23){String[] p=Build.VERSION.SDK_INT>=33?new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.POST_NOTIFICATIONS}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION};requestPermissions(p,10);}}
}
