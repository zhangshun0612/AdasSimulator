package com.example.zhangshun.adassimulator;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.IntDef;
import android.support.v4.app.NavUtils;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.body.MultipartFormDataBody;
import com.koushikdutta.async.http.body.Part;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.text.DecimalFormat;

public class WebService extends Service {

    public final int HTTP_PORT = 1234;


    static final String ACTION_START_WEB_SERVICE = "com.example.zhangshun.action.START_WEB_SERVICE";
    static final String ACTION_STOP_WEB_SERVICE = "com.example.zhangshun.action.STOP_WEB_SERVICE";

    private static final String TEXT_CONTENT_TYPE = "text/html;charset=utf-8";
    private static final String CSS_CONTENT_TYPE = "text/css;charset=utf-8";
    private static final String BINARY_CONTENT_TYPE = "application/octet-stream";
    private static final String JS_CONTENT_TYPE = "application/javascript";
    private static final String PNG_CONTENT_TYPE = "application/x-png";
    private static final String JPG_CONTENT_TYPE = "application/jpeg";
    private static final String SWF_CONTENT_TYPE = "application/x-shockwave-flash";
    private static final String WOFF_CONTENT_TYPE = "application/x-font-woff";
    private static final String TTF_CONTENT_TYPE = "application/x-font-truetype";
    private static final String SVG_CONTENT_TYPE = "image/svg+xml";
    private static final String EOT_CONTENT_TYPE = "image/vnd.ms-fontobject";
    private static final String MP3_CONTENT_TYPE = "audio/mp3";
    private static final String MP4_CONTENT_TYPE = "video/mpeg4";

    private AsyncHttpServer server = new AsyncHttpServer();
    private AsyncServer mAsyncServer = new AsyncServer();
    private FileUploadHolder fileUploadHolder = new FileUploadHolder();


    public WebService() {
    }

    public static void start(Context context){
        Intent intent = new Intent(context, WebService.class);
        intent.setAction(ACTION_START_WEB_SERVICE);
        context.startService(intent);
    }

    public static void stop(Context context){
        Intent intent = new Intent(context, WebService.class);
        intent.setAction(ACTION_STOP_WEB_SERVICE);
        context.stopService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null){
            String action = intent.getAction();

            if(ACTION_START_WEB_SERVICE.equals(action)){
                startServer();
            }else if(ACTION_STOP_WEB_SERVICE.equals(action)){
                stopSelf();
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(server != null){
            server.stop();
        }
        if(mAsyncServer != null){
            mAsyncServer.stop();
        }
    }

    private void startServer() {
        server.get("/images/.*", sendResourcesCallback);
        server.get("/scripts/.*", sendResourcesCallback);
        server.get("/css/.*", sendResourcesCallback);
        //index page
        server.get("/", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                response.send(getIndexContent());
            }
        });
        //query upload list
        server.get("/files", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                JSONArray array = new JSONArray();
                File dir = Constants.DIR;
                if(dir.exists() && dir.isDirectory()){
                    String[] fileNames = dir.list();
                    if(fileNames != null){
                        for(String fileName : fileNames){
                            File file = new File(dir, fileName);
                            if(file.exists() && file.isFile()){
                                JSONObject jsonObject = new JSONObject();
                                try {
                                    jsonObject.put("name", fileName);
                                    long fileLen = file.length();
                                    DecimalFormat df = new DecimalFormat("0.00");
                                    if (fileLen > 1024 * 1024) {
                                        jsonObject.put("size", df.format(fileLen * 1f / 1024 / 1024) + "MB");
                                    } else if (fileLen > 1024) {
                                        jsonObject.put("size", df.format(fileLen * 1f / 1024) + "KB");
                                    } else {
                                        jsonObject.put("size", fileLen + "B");
                                    }
                                    array.put(jsonObject);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
                response.send(array.toString());
            }
        });

        //upload
        server.post("/files", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, final AsyncHttpServerResponse response) {
                Log.d("WebService", "upload post request");
                final MultipartFormDataBody body = (MultipartFormDataBody) request.getBody();
                body.setMultipartCallback(new MultipartFormDataBody.MultipartCallback() {
                    @Override
                    public void onPart(Part part) {
                        if(part.isFile()){
                            body.setDataCallback(new DataCallback() {
                                @Override
                                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                                    Log.d("WebService", "OK");
                                    fileUploadHolder.write(bb.getAllByteArray());
                                    bb.recycle();
                                }
                            });
                        }else{
                            if(body.getDataCallback() == null){
                                body.setDataCallback(new DataCallback() {
                                    @Override
                                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                                        String fileName = URLDecoder.decode(new String(bb.getAllByteArray()));
                                        fileUploadHolder.setFileName(fileName);
                                        bb.recycle();
                                    }
                                });
                            }
                        }
                    }
                });
                request.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        fileUploadHolder.reset();
                        response.end();

                        Toast.makeText(getApplicationContext(), "上传完成", Toast.LENGTH_SHORT).show();

                    }
                });
            }
        });
        server.get("/progress/.*", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                JSONObject res = new JSONObject();
                String path = request.getPath().replace("/progress/", "");

                if(path.equals(fileUploadHolder.fileName)){
                    try {
                        res.put("fileName", fileUploadHolder.fileName);
                        res.put("size", fileUploadHolder.totalSize);
                        res.put("progress", fileUploadHolder.fileOutputStream == null ? 1 : 0.1);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        server.listen(mAsyncServer, HTTP_PORT);

    }

    private HttpServerRequestCallback sendResourcesCallback = new HttpServerRequestCallback() {
        @Override
        public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
            try {
                String fullPath = request.getPath();
                fullPath = fullPath.replace("%20", " ");
                String resourceName = fullPath;
                if (resourceName.startsWith("/")) {
                    resourceName = resourceName.substring(1);
                }
                if (resourceName.indexOf("?") > 0) {
                    resourceName = resourceName.substring(0, resourceName.indexOf("?"));
                }
                if (!TextUtils.isEmpty(getContentTypeByResourceName(resourceName))) {
                    response.setContentType(getContentTypeByResourceName(resourceName));
                }
                BufferedInputStream bInputStream = new BufferedInputStream(getAssets().open("wifi/" + resourceName));
                response.sendStream(bInputStream, bInputStream.available());
            } catch (IOException e) {
                e.printStackTrace();
                response.code(404).end();
                return;
            }
        }
    };

    private String getIndexContent() {
        BufferedInputStream bInputStream = null;
        try {
            bInputStream = new BufferedInputStream(getAssets().open("wifi/index.html"));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int len = 0;
            byte[] tmp = new byte[10240];
            while ((len = bInputStream.read(tmp)) > 0){
                baos.write(tmp, 0, len);
            }

            return new String(baos.toByteArray(), "utf-8");
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(bInputStream != null){
                try {
                    bInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
        return "";
    }

    private String getContentTypeByResourceName(String resourceName) {
        if (resourceName.endsWith(".css")) {
            return CSS_CONTENT_TYPE;
        } else if (resourceName.endsWith(".js")) {
            return JS_CONTENT_TYPE;
        } else if (resourceName.endsWith(".swf")) {
            return SWF_CONTENT_TYPE;
        } else if (resourceName.endsWith(".png")) {
            return PNG_CONTENT_TYPE;
        } else if (resourceName.endsWith(".jpg") || resourceName.endsWith(".jpeg")) {
            return JPG_CONTENT_TYPE;
        } else if (resourceName.endsWith(".woff")) {
            return WOFF_CONTENT_TYPE;
        } else if (resourceName.endsWith(".ttf")) {
            return TTF_CONTENT_TYPE;
        } else if (resourceName.endsWith(".svg")) {
            return SVG_CONTENT_TYPE;
        } else if (resourceName.endsWith(".eot")) {
            return EOT_CONTENT_TYPE;
        } else if (resourceName.endsWith(".mp3")) {
            return MP3_CONTENT_TYPE;
        } else if (resourceName.endsWith(".mp4")) {
            return MP4_CONTENT_TYPE;
        }
        return "";
    }

    public class FileUploadHolder{
        private String fileName;
        private File recivedFile;
        private BufferedOutputStream fileOutputStream;
        private long totalSize;

        public BufferedOutputStream getFileOutputStream() {
            return fileOutputStream;
        }


        public void setFileName(String fileName){
            this.fileName = fileName;
            totalSize = 0;
            if(!Constants.DIR.exists()){
                Constants.DIR.mkdirs();
            }

            this.recivedFile = new File(Constants.DIR, this.fileName);
            try {
                fileOutputStream = new BufferedOutputStream(new FileOutputStream(recivedFile));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        public void reset(){
            if(fileOutputStream != null){
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            fileOutputStream = null;
        }

        public void write(byte[] data){
            if(fileOutputStream != null){
                try {
                    fileOutputStream.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            totalSize += data.length;
        }
    }

}
