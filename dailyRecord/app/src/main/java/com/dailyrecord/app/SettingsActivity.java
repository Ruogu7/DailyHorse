package com.dailyrecord.app;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsActivity extends Activity {
    SharedPreferences prefs;LinearLayout places;EditText name,lat,lon,radius;TextView morningRow,eveningRow;
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("daily",0);migrateOldPlace();build();}
    void migrateOldPlace(){if(getPlaces().length()==0&&prefs.contains("lat")&&prefs.contains("lon")){try{JSONObject p=new JSONObject();p.put("id",java.util.UUID.randomUUID().toString());p.put("name","原有地点");p.put("lat",Double.parseDouble(prefs.getString("lat","0")));p.put("lon",Double.parseDouble(prefs.getString("lon","0")));JSONArray a=new JSONArray();a.put(p);prefs.edit().putString("places",a.toString()).apply();}catch(Exception ignored){}}}
    void build(){
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(28,24,28,28);
        TextView title=new TextView(this);title.setText("设置");title.setTextSize(26);content.addView(title);
        morningRow=timeRow(content,"早间提醒开始时间","morningTime","07:00");eveningRow=timeRow(content,"晚间提醒开始时间","eveningTime","17:40");
        TextView fixed=new TextView(this);fixed.setText("提醒方式：闹铃 + 震动（固定）");fixed.setTextSize(16);fixed.setPadding(0,12,0,14);content.addView(fixed);
        field(content,"早间附近半径（米，50–1000）","radius","100");radius=(EditText)content.getChildAt(content.getChildCount()-1);
        TextView locTitle=new TextView(this);locTitle.setText("打卡地点");locTitle.setTextSize(20);locTitle.setPadding(0,18,0,8);content.addView(locTitle);
        places=new LinearLayout(this);places.setOrientation(LinearLayout.VERTICAL);content.addView(places);renderPlaces();
        name=field(content,"地点名称","name","");lat=field(content,"纬度","lat","");lon=field(content,"经度","lon","");
        Button current=new Button(this);current.setText("用当前位置填写坐标");current.setOnClickListener(v->fillCurrent());content.addView(current);
        Button add=new Button(this);add.setText("添加地点");add.setOnClickListener(v->addPlace());content.addView(add);
        Button permissions=new Button(this);permissions.setText("定位权限与后台运行设置");permissions.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));content.addView(permissions);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);setContentView(scroll);
        radius.setText(prefs.getString("radius","100"));
        radius.setOnFocusChangeListener((v,has)->{if(!has)saveRadius();});
    }
    EditText field(LinearLayout root,String label,String key,String def){TextView t=new TextView(this);t.setText(label);t.setPadding(0,10,0,0);root.addView(t);EditText e=new EditText(this);e.setSingleLine();e.setInputType(8194);e.setText(prefs.getString(key,def));root.addView(e);return e;}
    TextView timeRow(LinearLayout root,String label,String key,String def){TextView row=new TextView(this);row.setText(label+"："+prefs.getString(key,def));row.setTextSize(18);row.setPadding(0,14,0,14);row.setOnClickListener(v->{String[] t=prefs.getString(key,def).split(":");new TimePickerDialog(this,(picker,h,m)->{String value=String.format(java.util.Locale.US,"%02d:%02d",h,m);prefs.edit().putString(key,value).apply();row.setText(label+"："+value);},Integer.parseInt(t[0]),Integer.parseInt(t[1]),true).show();});root.addView(row);return row;}
    JSONArray getPlaces(){try{return new JSONArray(prefs.getString("places","[]"));}catch(Exception e){return new JSONArray();}}
    void renderPlaces(){places.removeAllViews();JSONArray a=getPlaces();if(a.length()==0){TextView empty=new TextView(this);empty.setText("还没有地点。添加后，列表中的每个地点都会启用早晚提醒规则。");places.addView(empty);}for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,5,0,5);TextView info=new TextView(this);info.setText(p.optString("name","地点")+"\n"+p.optDouble("lat")+", "+p.optDouble("lon"));info.setTextSize(15);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));Button del=new Button(this);del.setText("删除");final String id=p.optString("id");del.setOnClickListener(v->removePlace(id));row.addView(del);places.addView(row);}}
    void fillCurrent(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},12);return;}try{LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);Location best=null;for(String provider:lm.getProviders(true)){Location x=lm.getLastKnownLocation(provider);if(x!=null&&(best==null||x.getTime()>best.getTime()))best=x;}if(best==null){Toast.makeText(this,"暂无可用的当前位置，请开启定位后重试",Toast.LENGTH_LONG).show();return;}lat.setText(String.valueOf(best.getLatitude()));lon.setText(String.valueOf(best.getLongitude()));if(name.getText().length()==0)name.setText("地点 "+(getPlaces().length()+1));}catch(SecurityException e){Toast.makeText(this,"定位权限不足",Toast.LENGTH_SHORT).show();}}
    void addPlace(){try{String n=name.getText().toString().trim();double la=Double.parseDouble(lat.getText().toString().trim()),lo=Double.parseDouble(lon.getText().toString().trim()),r=Double.parseDouble(radius.getText().toString().trim());if(n.isEmpty())n="地点 "+(getPlaces().length()+1);if(la < -90||la>90||lo < -180||lo>180||r<50||r>1000)throw new IllegalArgumentException();saveRadius();JSONArray a=getPlaces();JSONObject p=new JSONObject();String id=java.util.UUID.randomUUID().toString();p.put("id",id);p.put("name",n);p.put("lat",la);p.put("lon",lo);a.put(p);prefs.edit().putString("places",a.toString()).apply();name.setText("");lat.setText("");lon.setText("");renderPlaces();if(prefs.getBoolean("enabled",false))restartMonitor();Toast.makeText(this,"地点已添加",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"请检查名称、坐标和半径（50–1000 米）",Toast.LENGTH_SHORT).show();}}
    void removePlace(String id){JSONArray src=getPlaces(),dst=new JSONArray();for(int i=0;i<src.length();i++){JSONObject p=src.optJSONObject(i);if(p!=null&&!id.equals(p.optString("id")))dst.put(p);}prefs.edit().putString("places",dst.toString()).apply();renderPlaces();if(prefs.getBoolean("enabled",false))restartMonitor();}
    void saveRadius(){try{double r=Double.parseDouble(radius.getText().toString());if(r>=50&&r<=1000)prefs.edit().putString("radius",String.valueOf(r)).apply();}catch(Exception ignored){}}
    void restartMonitor(){stopService(new Intent(this,MonitorService.class));Intent i=new Intent(this,MonitorService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
}
