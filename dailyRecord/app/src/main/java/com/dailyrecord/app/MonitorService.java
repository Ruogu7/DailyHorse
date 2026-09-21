package com.dailyrecord.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MonitorService extends Service implements LocationListener {
    SharedPreferences prefs; LocationManager manager;
    @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("daily",0);manager=(LocationManager)getSystemService(LOCATION_SERVICE);channel();startForeground(1,notification("正在监测打卡地点","monitor"));if(!prefs.getBoolean("enabled",false)){setState("提醒未启用");stopSelf();return;}if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){setState("缺少精确定位权限，请授权后重新启动");stopSelf();return;}if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED){setState("缺少始终允许的后台定位权限");prefs.edit().putBoolean("enabled",false).apply();stopSelf();return;}try{int providers=0;if(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,4000,0,this);providers++;}if(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,4000,0,this);providers++;}if(providers==0){setState("手机定位已关闭，未发现可用定位源");return;}setState("高频定位监测已启动（目标间隔 4 秒）");Location last=null;for(String provider:manager.getProviders(true)){Location x=manager.getLastKnownLocation(provider);if(x!=null&&(last==null||x.getTime()>last.getTime()))last=x;}if(last!=null&&System.currentTimeMillis()-last.getTime()<120000)onLocationChanged(last);}catch(Exception e){setState("定位启动失败："+e.getClass().getSimpleName());android.util.Log.e("DailyRecord","Location start failed",e);stopSelf();}}
    void setState(String s){prefs.edit().putString("monitorState",s).apply();}
    void channel(){if(Build.VERSION.SDK_INT>=26){NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel monitor=new NotificationChannel("monitor","DailyRecord 定位状态",NotificationManager.IMPORTANCE_LOW);n.createNotificationChannel(monitor);NotificationChannel alerts=new NotificationChannel("alerts","DailyRecord 闹铃提醒",NotificationManager.IMPORTANCE_HIGH);alerts.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());alerts.enableVibration(true);alerts.setVibrationPattern(new long[]{0,500,250,500,250,800});n.createNotificationChannel(alerts);}}
    Notification notification(String text,String channel){PendingIntent p=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); if(Build.VERSION.SDK_INT>=26)return new Notification.Builder(this,channel).setContentTitle("DailyRecord").setContentText(text).setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(p).build(); return new Notification.Builder(this).setContentTitle("DailyRecord").setContentText(text).setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(p).build();}
    @Override public int onStartCommand(Intent i,int f,int id){return START_STICKY;}
    @Override public void onLocationChanged(Location l){if(!prefs.getBoolean("enabled",false))return;try{JSONArray places=new JSONArray(prefs.getString("places","[]"));String time=new SimpleDateFormat("HH:mm",Locale.US).format(new Date());String day=new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date());String morningStart=prefs.getString("morningStart","07:00"),morningEnd=prefs.getString("morningEnd","09:00"),eveningStart=prefs.getString("eveningStart","17:30"),eveningEnd=prefs.getString("eveningEnd","23:30");boolean morningWindow=time.compareTo(morningStart)>=0&&time.compareTo(morningEnd)<0;boolean eveningWindow=time.compareTo(eveningStart)>=0&&time.compareTo(eveningEnd)<0;double radius=Double.parseDouble(prefs.getString("radius","100"));double nearest=Double.MAX_VALUE;String nearestName="打卡地点";boolean nearAny=false;java.util.ArrayList<String> exits=new java.util.ArrayList<>();SharedPreferences.Editor state=prefs.edit();for(int i=0;i<places.length();i++){JSONObject p=places.optJSONObject(i);if(p==null)continue;String id=p.optString("id",String.valueOf(i));float[] d=new float[1];Location.distanceBetween(l.getLatitude(),l.getLongitude(),p.getDouble("lat"),p.getDouble("lon"),d);String name=p.optString("name","打卡地点");if(d[0]<nearest){nearest=d[0];nearestName=name;}if(d[0]<=radius)nearAny=true;String insideKey="inside_"+id;boolean wasInside=prefs.getBoolean(insideKey,d[0]<=80);boolean isInside=d[0]<=80;if(wasInside&&!isInside&&eveningWindow)exits.add(name);state.putBoolean(insideKey,isInside);}state.putLong("lastLocationAt",System.currentTimeMillis()).putFloat("lastLatitude",(float)l.getLatitude()).putFloat("lastLongitude",(float)l.getLongitude()).putFloat("lastNearestMeters",(float)nearest).putString("lastNearestName",nearestName).putString("monitorState","定位正常，最近地点 "+Math.round(nearest)+" 米").apply();if(morningWindow&&nearAny&&!day.equals(prefs.getString("morningDay",""))){prefs.edit().putString("morningDay",day).apply();alert("已到达打卡地点附近","morning","all");}for(String name:exits)alert(name+"：已走出 80 米范围","exit",name+System.currentTimeMillis());}catch(Exception e){setState("处理定位时出错："+e.getClass().getSimpleName());android.util.Log.e("DailyRecord","Location processing failed",e);}}
    void alert(String text,String tag,String id){NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"alerts"):new Notification.Builder(this);b.setContentTitle("DailyRecord 打卡提醒").setContentText(text).setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true);if(Build.VERSION.SDK_INT<26)b.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)).setVibrate(new long[]{0,500,250,500,250,800});n.notify((tag+id).hashCode(),b.build());}
    @Override public void onProviderEnabled(String p){} @Override public void onProviderDisabled(String p){} @Override public void onStatusChanged(String p,int s,android.os.Bundle b){}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){try{manager.removeUpdates(this);}catch(Exception ignored){}super.onDestroy();}
}
