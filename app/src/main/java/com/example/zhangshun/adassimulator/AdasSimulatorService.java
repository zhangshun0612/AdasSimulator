package com.example.zhangshun.adassimulator;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AdasSimulatorService extends Service {
    public final int SENSOR_PORT = 8899;

    private String filePath;
    private List<String> gprmcLines = new ArrayList<>();
    private List<String> inspvLines = new ArrayList<>();

    private IBinder mBinder = new AdasSimulatorBinder();
    private Handler mHandler = new Handler();

    private AdasSimulatorCallback mCallback = null;

    private ServerSocket serverSocket = null;
    private Socket socket = null;
    private boolean isTcpServerRunning = false;
    private Timer timer = null;

    int inspvCount = 0;
    int gprmcCount = 0;

    public AdasSimulatorService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        filePath = intent.getStringExtra("filePath");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        closeTcpServer();
        return super.onUnbind(intent);
    }

    private void readAdasDataFileTask(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileInputStream inputStream = new FileInputStream(filePath);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                    String line;
                    while((line = reader.readLine()) != null){
                        if(line.startsWith("$GPRMC")){
                            gprmcLines.add(line);
                        }else if(line.startsWith("%INSPVASA")){
                            inspvLines.add(line);
                        }
                    }

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if(mCallback != null){
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mCallback.readFileCompleted(gprmcLines.size(), inspvLines.size());
                        }
                    });
                }
            }
        }).start();
    }

    private void startTcpServerTask(){
        isTcpServerRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(SENSOR_PORT);
                    while (isTcpServerRunning) {
                        Socket nSocket = serverSocket.accept();
                        //暂时不想搞链接池，只支持一个客户端
                        if(socket != null){
                            nSocket.close();
                        }else{
                            socket = nSocket;
                            startDataSendTask();
                        }

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    closeSocket();
                }
            }
        }).start();
    }

    private void startDataSendTask(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                timer = new Timer(true);
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            if (inspvCount < inspvLines.size()) {
                                String line = inspvLines.get(inspvCount);
                                socket.getOutputStream().write((line + "\r\n").getBytes("UTF-8"));
                                inspvCount++;

                                if (((inspvCount % 10) == 0) && gprmcCount < gprmcLines.size()) {
                                    line = gprmcLines.get(gprmcCount);
                                    socket.getOutputStream().write((line + "\r\n").getBytes("UTF-8"));
                                    gprmcCount++;

                                    String[] strList = line.split(",");
                                    if(strList.length >= 9){
                                        String latStr = strList[3];
                                        String lonStr = strList[5];
                                        String dirStr = strList[8];


                                        final double lat = Double.parseDouble(latStr.substring(0, 2)) + (Double.parseDouble(latStr.substring(2)) / 60.0);

                                        final double lon = Double.parseDouble(lonStr.substring(0, 3)) + (Double.parseDouble(lonStr.substring(3)) / 60.0);

                                        final double dir = Double.parseDouble(dirStr);

                                        mHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if(mCallback != null){
                                                    mCallback.locationUpdate(lat, lon, dir);
                                                }
                                            }
                                        });

                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            closeSocket();
                            timer.cancel();
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mCallback.remoteClosed();
                                }
                            });

                        }
                    }
                }, new Date(), 100);
            }
        }).start();
    }

    private void closeSocket(){
        if(socket != null){
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = null;
        }
    }

    private void closeTcpServer(){
        isTcpServerRunning = false;
        if(timer != null) {
            timer.cancel();
        }
        closeSocket();
        if(serverSocket != null){
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class AdasSimulatorBinder extends Binder{
        public void setAdasSimulatorCallback(AdasSimulatorCallback callback){
            mCallback = callback;
        }

        public void startReadDataFile() {
            readAdasDataFileTask();
        }

        public void startSensorServer(){
            inspvCount = 0;
            gprmcCount = 0;
            startTcpServerTask();
        }

        public void closeSensorServer() {
            closeTcpServer();
        }
    }

    public interface AdasSimulatorCallback{
        void readFileCompleted(int gprmcSize, int inspvSize);
        void locationUpdate(double lat, double lon, double dir);
        void remoteClosed();
    }
}
