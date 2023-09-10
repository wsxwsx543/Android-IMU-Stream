package com.example.imu_data_collection_smartphone;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends Activity implements AccListener, GyroListener {
    final private String TAG = MainActivity.class.getName();
    final static private String ACC_UUID = "680ac785-9ed1-42cd-bdf0-76cf6b708f3b";
    final static private String GYRO_UUID = "e0740e55-4d73-4e97-b0fb-90afc0ebb980";
    final static private int REQUEST_ENABLE_BT = 0;
    private BluetoothAdapter bluetoothAdapter;
    final private static int MOTION_PREVIEW_SIZE = 1000;

    // colors to display for graphs on the screen
    final private int graphColor[] = {
            Color.argb(255, 255, 180, 9), // orange
            Color.argb(255, 46, 168, 255), // blue
            Color.argb(255, 129, 209, 24), // green
            Color.argb(255, 225, 225, 0), // yellow
            Color.argb(255, 150, 150, 150)};

    private static final Object gyroResLock = new Object();
    private static final Object accResLock = new Object();

    private static final ArrayList<LineGraphSeries<DataPoint>> accDisplayBuf = new ArrayList<>();
    private static final ArrayList<ArrayList<Float>> accResultBuf = new ArrayList<>();
    private final ArrayList<Float> accTimestamp = new ArrayList<>();
    private final ArrayList<Integer> accIdx = new ArrayList<>();
    private static final ArrayList<LineGraphSeries<DataPoint>> gyroDisplayBuf = new ArrayList<>();
    private static final ArrayList<ArrayList<Float>> gyroResultBuf = new ArrayList<>();
    private final ArrayList<Float> gyroTimestamp = new ArrayList<>();
    private final ArrayList<Integer> gyroIdx = new ArrayList<>();
    private GraphView accGraph, gyroGraph;
    private String fName = null;

    private void init() {
        initGraphs();
        initBluetooth();
        initCamera();
        initButton();
    }

    private void initGraphs() {
        Viewport vp1 = accGraph.getViewport();
        vp1.setXAxisBoundsManual(true);
        vp1.setMinX(0);
        vp1.setMaxX(1000);

        Viewport vp2 = gyroGraph.getViewport();
        vp2.setXAxisBoundsManual(true);
        vp2.setMinX(0);
        vp2.setMaxX(1000);

        for (int i = 0; i < 3; i++) {
            accDisplayBuf.add(new LineGraphSeries<>());
            accGraph.addSeries(accDisplayBuf.get(i));
            accResultBuf.add(new ArrayList<>());
            accDisplayBuf.get(i).setColor(graphColor[i]);
            accDisplayBuf.get(i).setThickness(10);

            gyroDisplayBuf.add(new LineGraphSeries<>());
            gyroGraph.addSeries(gyroDisplayBuf.get(i));
            gyroResultBuf.add(new ArrayList<>());
            gyroDisplayBuf.get(i).setColor(graphColor[i]);
            gyroDisplayBuf.get(i).setThickness(10);
        }
    }

    private void initCamera() {
        surfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                texture_surface = new Surface(textureView.getSurfaceTexture());
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        };
        textureView.setSurfaceTextureListener(surfaceTextureListener);
        //B1. 准备工作：初始化ImageReader
        imageReader = ImageReader.newInstance(1000, 1000, ImageFormat.JPEG, 2);
        //B2. 准备工作：设置ImageReader收到图片后的回调函数
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
            }
        }, null);
        //B3 配置：获取ImageReader的Surface
        imageReaderSurface = imageReader.getSurface();
    }

    private void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.d(TAG, "onCreate: This device doesn't have bluetooth");
            return;
        } else {
            Log.d(TAG, "onCreate: bluetooth is available on this device");
        }
        assert true;
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "onCreate: bluetooth is not enabled");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onCreate: Request Permission");
            requestPermissions(new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.WAKE_LOCK,
                            Manifest.permission.CAMERA,
                            Manifest.permission.FOREGROUND_SERVICE,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    2);
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        Log.d(TAG, "onCreate: pairedDevices length: " + pairedDevices.size());
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.d(TAG, "onCreate: Bluetooth device name: " + deviceName + " MAC Address: "
                        + deviceHardwareAddress);
            }
        }

        AcceptThread acceptThread = new AcceptThread();
        acceptThread.start();
    }

    @SuppressLint("SetTextI18n")
    private void initButton() {
        recButton.setOnClickListener(v -> {
            if (!isRecording) { // start
                // clear buffer first, then make isRecording be true to accept new data
                synchronized (accResLock) {
                    accResultBuf.get(0).clear();
                    accResultBuf.get(1).clear();
                    accResultBuf.get(2).clear();
                    accTimestamp.clear();
                    accIdx.clear();
                }
                synchronized (gyroResLock) {
                    gyroResultBuf.get(0).clear();
                    gyroResultBuf.get(1).clear();
                    gyroResultBuf.get(2).clear();
                    gyroTimestamp.clear();
                    gyroIdx.clear();
                }
                beginAccTime = null;
                beginGyroTime = null;
                @SuppressLint("SimpleDateFormat")
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH_mm_ssZ");
                fName = simpleDateFormat.format(new Date());
                recButton.setText("Stop Rec");
                isRecording = true;
            } else { // stop
                // change is recording first to stop accept new data
                isRecording = false;
                try {
                    saveToFile();
                    Toast.makeText(this, "Save file successfully", Toast.LENGTH_SHORT).show();
                } catch (JSONException | IOException e) {
                    Log.d(TAG, "initButton: Save file failed");
                    e.printStackTrace();
                }
                recButton.setText("Start Rec");
            }
        });
    }

    TextureView textureView;
    TextureView.SurfaceTextureListener surfaceTextureListener;
    CameraManager cameraManager;
    CameraDevice.StateCallback cam_stateCallback;
    CameraDevice opened_camera;
    Surface texture_surface;
    CameraCaptureSession.StateCallback cam_capture_session_stateCallback;
    CameraCaptureSession cameraCaptureSession;
    CaptureRequest.Builder requestBuilder;
    ImageReader imageReader;
    Surface imageReaderSurface;
    CaptureRequest request;

    private boolean isRecording = false;
    private Button recButton;

    @TargetApi(Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accGraph = findViewById(R.id.acc_graph);
        gyroGraph = findViewById(R.id.gyro_graph);
        textureView = findViewById(R.id.texture_view);
        recButton = findViewById(R.id.rec_button);
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 如果 textureView可用，就直接打开相机
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            // 否则，就开启它的可用时监听。
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        // 先把相机的session关掉
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
        }
        // 再关闭相机
        if (null != opened_camera) {
            opened_camera.close();
        }
        // 最后关闭ImageReader
        if (null != imageReader) {
            imageReader.close();
        }
        // 最后交给父View去处理
        super.onPause();
    }

    private void openCamera() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);  // 初始化
        cam_stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                opened_camera = camera;
                try {
                    requestBuilder = opened_camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    requestBuilder.addTarget(texture_surface);
                    request = requestBuilder.build();
                    cam_capture_session_stateCallback = new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraCaptureSession = session;
                            try {
                                session.setRepeatingRequest(request, null, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        }
                    };
                    opened_camera.createCaptureSession(
                            Arrays.asList(texture_surface, imageReaderSurface)
                            , cam_capture_session_stateCallback
                            , null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
            }
        };
        checkPermission();
        try {
            cameraManager.openCamera(cameraManager.getCameraIdList()[0], cam_stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void checkPermission() {
        // 检查是否申请了权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this
                    , Manifest.permission.CAMERA)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, 1);
            }
        }
    }

    private static final int X = 0, Y = 1, Z = 2, TIMESTAMP = 3, NAME = 4, INDEX = 5;
    public static final float GYRO = 0.f, ACC = 1.f;

    private ConnectedThread accConnectedThread, gyroConnectedThread;
    private Float beginAccTime = null, beginGyroTime = null;

    @Override
    public void accListener(float[] sensorData) {
        DataPoint x, y, z;
        x = new DataPoint((int) (sensorData[INDEX] / 2), sensorData[X]);
        y = new DataPoint((int) (sensorData[INDEX] / 2), sensorData[Y]);
        z = new DataPoint((int) (sensorData[INDEX] / 2), sensorData[Z]);
        synchronized (accResLock) {
            if (isRecording) {
                accResultBuf.get(0).add(sensorData[X]);
                accResultBuf.get(1).add(sensorData[Y]);
                accResultBuf.get(2).add(sensorData[Z]);
                if (beginAccTime == null) {
                    beginAccTime = sensorData[TIMESTAMP];
                    accTimestamp.add(0.f);
                } else {
                    accTimestamp.add((sensorData[TIMESTAMP] - beginAccTime) / 1000000);
                }
                accIdx.add((int) (sensorData[INDEX] / 2));
            }
        }
        runOnUiThread(() -> {
            accDisplayBuf.get(0).appendData(x, true, MOTION_PREVIEW_SIZE);
            accDisplayBuf.get(1).appendData(y, true, MOTION_PREVIEW_SIZE);
            accDisplayBuf.get(2).appendData(z, true, MOTION_PREVIEW_SIZE);
        });
    }

    @Override
    public void gyroListener(float[] sensorData) {
        DataPoint x, y, z;
        x = new DataPoint((int) (sensorData[INDEX] / 2), sensorData[X]);
        y = new DataPoint((int) (sensorData[INDEX] / 2), sensorData[Y]);
        z = new DataPoint((int) (sensorData[INDEX] / 2), sensorData[Z]);
        synchronized (gyroResLock) {
            if (isRecording) {
                gyroResultBuf.get(0).add(sensorData[X]);
                gyroResultBuf.get(1).add(sensorData[Y]);
                gyroResultBuf.get(2).add(sensorData[Z]);
                if (beginGyroTime == null) {
                    beginGyroTime = sensorData[TIMESTAMP];
                    gyroTimestamp.add(0.f);
                } else {
                    gyroTimestamp.add((sensorData[TIMESTAMP] - beginGyroTime) / 1000000);
                }
                gyroIdx.add((int) (sensorData[INDEX] / 2));
            }
        }
        runOnUiThread(() -> {
            gyroDisplayBuf.get(0).appendData(x, true, MOTION_PREVIEW_SIZE);
            gyroDisplayBuf.get(1).appendData(y, true, MOTION_PREVIEW_SIZE);
            gyroDisplayBuf.get(2).appendData(z, true, MOTION_PREVIEW_SIZE);
        });
    }

    private void saveToFile() throws JSONException, IOException {
        JSONObject accJsonObj = new JSONObject();
        accJsonObj.put("timestamp", accTimestamp);
        accJsonObj.put("x", accResultBuf.get(0));
        accJsonObj.put("y", accResultBuf.get(1));
        accJsonObj.put("z", accResultBuf.get(2));
        accJsonObj.put("idx", accIdx);

        JSONObject gyroJsonObj = new JSONObject();
        gyroJsonObj.put("timestamp", gyroTimestamp);
        gyroJsonObj.put("x", gyroResultBuf.get(0));
        gyroJsonObj.put("y", gyroResultBuf.get(1));
        gyroJsonObj.put("z", gyroResultBuf.get(2));
        gyroJsonObj.put("idx", gyroIdx);

        JSONObject imuJsonObj = new JSONObject();
        imuJsonObj.put("acc", accJsonObj);
        imuJsonObj.put("gyro", gyroJsonObj);


        File dir = new File(Environment.getExternalStorageDirectory(), "IMU-Stream/");
        if (!dir.exists()) {
            boolean r = dir.mkdirs();
            Log.d(TAG, r ? "True" : "False");
        }
        File currDataDir = new File(dir.getAbsolutePath(), fName + "/");
        if (!currDataDir.exists()) {
            boolean r = currDataDir.mkdir();
            Log.d(TAG, r ? "True" : "False");
        }
        File file = new File(currDataDir.getAbsolutePath() + "/" + fName + ".json");
        Writer output = new BufferedWriter(new FileWriter(file));
        output.write(imuJsonObj.toString());
        output.close();
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket accServerSocket, gyroServerSocket;

        @SuppressLint("MissingPermission")
        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp1 = null, tmp2 = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp1 = bluetoothAdapter.listenUsingRfcommWithServiceRecord("IMU_Data_Collection"
                        , UUID.fromString(ACC_UUID));
                tmp2 = bluetoothAdapter.listenUsingRfcommWithServiceRecord("IMU_Data_Collection"
                        , UUID.fromString(GYRO_UUID));
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            accServerSocket = tmp1;
            gyroServerSocket = tmp2;
        }

        boolean accConnected = false, gyroConnected = false;

        @Override
        public void run() {
            BluetoothSocket accSocket = null, gyroSocket = null;
            Log.d(TAG, "run: trying to connect...");
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    accSocket = accServerSocket.accept();
                    gyroSocket = gyroServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (accSocket != null && !accConnected) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    Log.d(TAG, "run: bluetooth acc connected SUCCESSFULLY!");
//                    manageMyConnectedSocket(socket);
                    accConnectedThread = new ConnectedThread(accSocket, MainActivity.this,
                            MainActivity.this);
                    accConnectedThread.start();
                    try {
                        accServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    accConnected = true;
                }
                if (gyroSocket != null && !gyroConnected) {
                    Log.d(TAG, "run: bluetooth gyroconnected SUCCESSFULLY!");
                    gyroConnectedThread = new ConnectedThread(gyroSocket, MainActivity.this,
                            MainActivity.this);
                    gyroConnectedThread.start();
                    try {
                        gyroServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    gyroConnected = true;
                }
                if (accConnected && gyroConnected) break;
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                accServerSocket.close();
                gyroServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}