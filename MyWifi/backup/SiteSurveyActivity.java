package com.example.eric.mywifi.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.eric.mywifi.R;
import com.example.eric.mywifi.Storage.MyDBOpenHelper;
import com.example.eric.mywifi.Storage.PrefUtils;
import com.example.eric.mywifi.activities.RecordListActivity;
import com.example.eric.mywifi.activities.SettingActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class SiteSurveyActivity extends AppCompatActivity implements View.OnClickListener{

    Timer timer;
    WifiManager wifi;
    File mytxt;

    ListView lv;
    TextView tv_tag;
    TextView tv_times;

    FileOutputStream fos;
    OutputStreamWriter osw;

    List<String> infolist=new ArrayList<>();
    static ArrayList<String> recordList=new ArrayList<>();

    static ArrayAdapter<String> listadapter;

    MyDBOpenHelper dbOpenHelper;
    SQLiteDatabase db;

    String currentTable;

    Date date;
    int recordfrequency;
    int showfrequency=1;
    static int times=0;
    final File sdcard= Environment.getExternalStorageDirectory();

    private Handler handler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 1:
                    listadapter.notifyDataSetChanged();
                    break;
                case 2:
                    listadapter.notifyDataSetChanged();
                    times++;
                    tv_times.setText(times+"");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_site_survey);

        preparedata();
        initViews();
        initWifiManager();

        timer = new Timer("apUpdates");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                normalTimerTask();
            }
        },0,showfrequency*1000);
    }

    public void initWifiManager(){
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (!wifi.isWifiEnabled())
            if (wifi.getWifiState() != WifiManager.WIFI_STATE_ENABLING)
                wifi.setWifiEnabled(true);
    }

    public void initViews(){
        lv=(ListView)findViewById(R.id.lv_wifilist);
        listadapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,infolist);
        lv.setAdapter(listadapter);

        findViewById(R.id.btn_record).setOnClickListener(this);
        findViewById(R.id.btn_begin).setOnClickListener(this);
        findViewById(R.id.btn_stop).setOnClickListener(this);

        tv_tag=(TextView)findViewById(R.id.tv_positiontag);
        tv_times=(TextView)findViewById(R.id.tv_times);
    }

    public void preparedata(){
        recordfrequency= PrefUtils.getInt(getApplicationContext(),PrefUtils.Keys.FREQUENCY);
        dbOpenHelper=new MyDBOpenHelper(getApplicationContext(),"wifirss.db",null,1);
        createTable("test");
    }

    public void createTXT(String name) {
        if(!sdcard.exists()){
            Toast.makeText(getApplicationContext(),"当前系统无sd卡目录",Toast.LENGTH_SHORT).show();
        }else{
            recordList.add(name);
            mytxt=new File(sdcard,name);
            if(!mytxt.exists()) {
                try {
                    if(mytxt.createNewFile()) {
                        Toast.makeText(getApplicationContext(), "文件已创建", Toast.LENGTH_SHORT).show();
                        fos=new FileOutputStream(mytxt);
                        osw=new OutputStreamWriter(fos);
                        writeData(name+"\n");
                    }
                    else Toast.makeText(getApplicationContext(), "文件创建失败", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void createTable(String tname){
        db=dbOpenHelper.getWritableDatabase();
        db.execSQL("create table "+tname+" (_id integer primary key autoincrement, rss1 double, rss2 double)");
        db.execSQL("insert into "+tname+" (rss1,rss2) VALUES (-92.0,-2.0);");
    }

    public void saveToRecord(){
        db.execSQL("ALTER TABLE pos10 ADD RSS6 DOUBLE");
    }

    public void writeData(String str){
        try {
            osw.write(str);
            osw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void normalTimerTask(){
        wifi.startScan();
        List<ScanResult> results=wifi.getScanResults();
        infolist.clear();

        for(ScanResult result :results){
            String info=result.BSSID +":"+result.level+"dBm";
            infolist.add(info);
        }
        Collections.sort(infolist);
        Message msg=new Message();
        msg.what=1;
        handler.sendMessage(msg);
    }

    public void recordTimerTask(){
        wifi.startScan();
        List<ScanResult> results=wifi.getScanResults();
        infolist.clear();
        date=new Date();
//        writeData("time:"+date.getTime()+"\n");
        for(ScanResult result :results){
            String info=result.BSSID +":"+result.level+"dBm";
            String str=info+"\n";
            //writeData(str);
            infolist.add(info);
        }
        Message msg=new Message();
        msg.what=2;
        handler.sendMessage(msg);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_begin:
                beginRecord();
                break;
            case R.id.btn_stop:
                stopRecord();
                break;
            case R.id.btn_record:
                startActivity(new Intent(this,RecordListActivity.class));
                break;
        }
    }

    public void beginRecord(){
        final EditText et = new EditText(this);
        new AlertDialog.Builder(this).setTitle("输入地点标签")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String input = et.getText().toString();
                        if (input.equals("")) {
                            Toast.makeText(getApplicationContext(), "内容不能为空！" + input, Toast.LENGTH_LONG).show();
                        }
                        else {
                            tv_tag.setText(input);
                            //createTXT(input+".txt");
                            createTable(input);

                            timer.cancel();
                            timer=new Timer();
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    recordTimerTask();
                                }
                            },100,recordfrequency*1000);
                        }
                    }
                })
                .show();
    }

    public void stopRecord(){
        writeData("times="+times);
        try {
            osw.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        timer.cancel();
        restartNormalTimer();
        times=0;
        Toast.makeText(this,"已停止",Toast.LENGTH_SHORT).show();
    }

    public void restartNormalTimer(){
        timer=new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                normalTimerTask();
            }
        },0,showfrequency*1000);
    }

}
