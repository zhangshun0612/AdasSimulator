package com.example.zhangshun.adassimulator;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;

public class AdasSimulatorActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {

    private ProgressDialog progressDialog;
    AdasSimulatorService.AdasSimulatorBinder binder = null;

    private Switch serverSwitch;
    private MapView mMapView;
    private BaiduMap mMap;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            binder = (AdasSimulatorService.AdasSimulatorBinder) iBinder;
            binder.setAdasSimulatorCallback(callback);
            binder.startReadDataFile();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    private AdasSimulatorService.AdasSimulatorCallback callback = new AdasSimulatorService.AdasSimulatorCallback() {

        @Override
        public void readFileCompleted(int gprmcSize, int inspvSize) {
            progressDialog.dismiss();
            Toast.makeText(AdasSimulatorActivity.this, "读取GPRMC: " + gprmcSize + "条\n" + "读取INSPV: " + inspvSize + "条\n",
                    Toast.LENGTH_SHORT).show();

            binder.startSensorServer();
        }

        @Override
        public void locationUpdate(double lat, double lon, double dir) {
            MyLocationData locData = new MyLocationData.Builder()
                .direction((float)dir)
                .accuracy((float) 1.0)
                .latitude(lat)
                .longitude(lon)
                .build();
            mMap.setMyLocationData(locData);
            // 开始移动百度地图的定位地点到中心位置
            LatLng ll = new LatLng(locData.latitude, locData.longitude);
            MapStatusUpdate u = MapStatusUpdateFactory.newLatLngZoom(ll, 16.0f);
            mMap.animateMapStatus(u);
        }

        @Override
        public void remoteClosed() {
            Toast.makeText(AdasSimulatorActivity.this, "远端连接已关闭", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_adas_simulator);

        String filePath = getIntent().getStringExtra("filePath");
        Intent intent = new Intent(AdasSimulatorActivity.this, AdasSimulatorService.class);
        intent.putExtra("filePath", filePath);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);


        serverSwitch = (Switch) findViewById(R.id.server_switch);
        serverSwitch.setOnCheckedChangeListener(this);

        mMapView = (MapView) findViewById(R.id.map_view);

        progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setMessage("读取数据中.....");
        progressDialog.setCancelable(false);
        progressDialog.show();

        mMap = mMapView.getMap();
        mMap.setMyLocationEnabled(true);


    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
        mMapView.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if(b){
            binder.startSensorServer();
        }else{
            binder.closeSensorServer();
        }
    }
}
