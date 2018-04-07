package com.example.zhangshun.adassimulator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.zhangshun.adassimulator.bean.AdasDataFile;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mAdasDataFilesView;
    private TextView mIpAddressTextView;

    private Handler mHandler = new Handler();

    private List<AdasDataFile> mDataList = new ArrayList<>();
    private AdasDataFileAdapter mAdapter;

    private NetworkChangeReceiver mReceiver;
    private IntentFilter mIntentFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        mReceiver = new NetworkChangeReceiver();
        registerReceiver(mReceiver, mIntentFilter);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.fresh_layout);
        mAdasDataFilesView = (RecyclerView) findViewById(R.id.recycler_view);
        mIpAddressTextView = (TextView) findViewById(R.id.ip_address_tv);

        mAdasDataFilesView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        mAdapter = new AdasDataFileAdapter();
        mAdasDataFilesView.setLayoutManager(new LinearLayoutManager(this));
        mAdasDataFilesView.setAdapter(mAdapter);

        loadAdasDataFileList();

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadAdasDataFileList();
            }
        });

        WebService.start(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WebService.stop(this);
    }

    private String getLocalIpAddress(){
        String ipAddress = "";
        NetworkInfo info = ((ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE))
                .getActiveNetworkInfo();
        if(info != null && info.isConnected()){
            if(info.getType() == ConnectivityManager.TYPE_WIFI){
                WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                ipAddress = intIP2StringIP(wifiInfo.getIpAddress());
            }
        }

        return ipAddress;
    }

    private String intIP2StringIP(int ip){
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                (ip >> 24 & 0xFF);
    }

    private void loadAdasDataFileList(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AdasDataFile> list = new ArrayList<AdasDataFile>();
                File dir = Constants.DIR;
                if(dir.exists() && dir.isDirectory()){
                    File[] fileNames = dir.listFiles();
                    if(fileNames != null){
                        for (File file : fileNames){
                            AdasDataFile dataFile = new AdasDataFile();
                            dataFile.setName(file.getName());
                            dataFile.setSize(getFileSize(file.length()));
                            dataFile.setPath(file.getPath());
                            list.add(dataFile);
                        }
                    }
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mDataList.clear();
                        mDataList.addAll(list);
                        mAdapter.notifyDataSetChanged();

                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        }).start();
    }

    private String getFileSize(long length) {
        DecimalFormat df = new DecimalFormat("######0.00");
        double d1 = 3.23456;
        double d2 = 0.0;
        double d3 = 2.0;
        df.format(d1);
        df.format(d2);
        df.format(d3);
        long l = length / 1000;//KB
        if ( l < 1024 ) {
            return df.format(l) + "KB";
        } else if ( l < 1024 * 1024.f ) {
            return df.format((l / 1024.f)) + "MB";
        }
        return df.format(l / 1024.f / 1024.f) + "GB";
    }

    class NetworkChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            //Toast.makeText(MainActivity.this, "网络状态变化", Toast.LENGTH_SHORT).show();
            String ipAddress = getLocalIpAddress();
            if(ipAddress.isEmpty()){
                mIpAddressTextView.setText("请链入局域网");
            }else{
                mIpAddressTextView.setText("浏览器访问:" + ipAddress + ":" + WebService.HTTP_PORT + "管理Adas数据");
            }
        }
    }

    class AdasDataFileAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            RecyclerView.ViewHolder holder  = new AdasDataFileViewHolder(LayoutInflater.from(MainActivity.this).inflate(R.layout.adas_data_file_item, parent, false));
            return holder;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            AdasDataFileViewHolder viewHolder = (AdasDataFileViewHolder) holder;
            final AdasDataFile dataFile = mDataList.get(position);

            viewHolder.fileNameTextView.setText(dataFile.getName());
            viewHolder.fileSizeTextView.setText(dataFile.getSize());
            viewHolder.filePathTextView.setText(dataFile.getPath());

            viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //Toast.makeText(MainActivity.this, dataFile.getPath(), Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(MainActivity.this, AdasSimulatorActivity.class);
                    intent.putExtra("filePath", dataFile.getPath());
                    startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mDataList.size();
        }

        class AdasDataFileViewHolder extends RecyclerView.ViewHolder{

            public TextView fileNameTextView;
            public TextView fileSizeTextView;
            public TextView filePathTextView;


            public AdasDataFileViewHolder(View itemView) {
                super(itemView);

                fileNameTextView = itemView.findViewById(R.id.file_name_tv);
                fileSizeTextView = itemView.findViewById(R.id.file_size_tv);
                filePathTextView = itemView.findViewById(R.id.file_path_tv);
            }
        }
    }
}
