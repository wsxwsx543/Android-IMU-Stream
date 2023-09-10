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
import android.graphics.Camera;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.util.Size;
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
import java.util.Collections;
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

    // camera 2
    private TextureView mTextureView;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraDevice.StateCallback mCameraDeviceStateCallback;
    private CameraCaptureSession.StateCallback mSessionStateCallback;
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback;
    private CaptureRequest.Builder mPreviewCaptureRequest;
    private CaptureRequest.Builder mRecorderCaptureRequest;
    private MediaRecorder mMediaRecorder;
    private String mCurrentSelectCamera;
    private Handler mChildHandler;

    private void init() {
        initGraphs();
        initBluetooth();
        initCamera();
        initButton();
    }

    private void initCamera() {
        initChildHandler();
        initTextureViewStateListener();
        initMediaRecorder();
        initCameraDeviceStateCallback();
        initSessionStateCallback();
        initSessionCaptureCallback();
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
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onCreate: Request Permission");
            requestPermissions(new String[]{
                            Manifest.permission.RECORD_AUDIO,
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
                config();
                startRecorder();
                recButton.setText("Stop Rec");
                isRecording = true;
            } else { // stop
                // change is recording first to stop accept new data
                isRecording = false;
                stopRecorder();
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


    private boolean isRecording = false;
    private Button recButton;

    @TargetApi(Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accGraph = findViewById(R.id.acc_graph);
        gyroGraph = findViewById(R.id.gyro_graph);
        mTextureView = findViewById(R.id.texture_view);
        recButton = findViewById(R.id.rec_button);
        init();
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


    // camera2

    /**
     * 初始化子线程Handler，操作Camera2需要一个子线程的Handler
     */
    private void initChildHandler() {
        HandlerThread handlerThread = new HandlerThread("Camera2Demo");
        handlerThread.start();
        mChildHandler = new Handler(handlerThread.getLooper());
    }

    /**
     * 初始化TextureView的纹理生成监听，只有纹理生成准备好了。我们才能去进行摄像头的初始化工作让TextureView接收摄像头预览画面
     */
    private void initTextureViewStateListener() {
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                //可以使用纹理
                initCameraManager();
                selectCamera();
                openCamera();

            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                //纹理尺寸变化

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                //纹理被销毁
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                //纹理更新

            }
        });
    }

    /**
     * 初始化MediaRecorder
     */
    private void initMediaRecorder() {
        mMediaRecorder = new MediaRecorder();
    }

    /**
     * 配置录制视频相关数据
     */
    private void configMediaRecorder() {
        File dir = new File(Environment.getExternalStorageDirectory(), "IMU-Stream/" + fName + "/");
        if (!dir.exists()) {
            boolean r = dir.mkdirs();
            Log.d(TAG, r ? "true" : "false");
        }
        File file = new File(Environment.getExternalStorageDirectory(), "IMU-Stream/" + fName + "/" + fName + ".mp4");
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);//设置音频来源
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);//设置视频来源
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);//设置输出格式
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);//设置音频编码格式，请注意这里使用默认，实际app项目需要考虑兼容问题，应该选择AAC
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);//设置视频编码格式，请注意这里使用默认，实际app项目需要考虑兼容问题，应该选择H264
        mMediaRecorder.setVideoEncodingBitRate(8 * 720 * 1280);//设置比特率 一般是 1*分辨率 到 10*分辨率 之间波动。比特率越大视频越清晰但是视频文件也越大。
        mMediaRecorder.setVideoFrameRate(30);//设置帧数 选择 30即可， 过大帧数也会让视频文件更大当然也会更流畅，但是没有多少实际提升。人眼极限也就30帧了。
        Size size = getMatchingSize2();
        mMediaRecorder.setVideoSize(size.getWidth(), size.getHeight());
        mMediaRecorder.setOrientationHint(90);
        Surface surface = new Surface(mTextureView.getSurfaceTexture());
        mMediaRecorder.setPreviewDisplay(surface);
        mMediaRecorder.setOutputFile(file.getAbsolutePath());
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 重新配置录制视频时的CameraCaptureSession
     */
    private void config() {
        try {
            mCameraCaptureSession.stopRepeating();//停止预览，准备切换到录制视频
            mCameraCaptureSession.close();//关闭预览的会话，需要重新创建录制视频的会话
            mCameraCaptureSession = null;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        configMediaRecorder();
        Size cameraSize = getMatchingSize2();
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(cameraSize.getWidth(), cameraSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);
        Surface recorderSurface = mMediaRecorder.getSurface();//从获取录制视频需要的Surface
        try {
            mPreviewCaptureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewCaptureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewCaptureRequest.addTarget(previewSurface);
            mPreviewCaptureRequest.addTarget(recorderSurface);
            //请注意这里设置了Arrays.asList(previewSurface,recorderSurface) 2个Surface，很好理解录制视频也需要有画面预览，第一个是预览的Surface，第二个是录制视频使用的Surface
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recorderSurface), mSessionStateCallback, mChildHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * 开始录制视频
     */
    private void startRecorder() {
        mMediaRecorder.start();


    }

    /**
     * 暂停录制视频（暂停后视频文件会自动保存）
     */
    private void stopRecorder() {
        mMediaRecorder.stop();
        mMediaRecorder.reset();
    }

    /**
     * 初始化Camera2的相机管理，CameraManager用于获取摄像头分辨率，摄像头方向，摄像头id与打开摄像头的工作
     */
    private void initCameraManager() {
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

    }

    /**
     * 选择一颗我们需要使用的摄像头，主要是选择使用前摄还是后摄或者是外接摄像头
     */
    private void selectCamera() {
        if (mCameraManager != null) {
            Log.e(TAG, "selectCamera: CameraManager is null");

        }
        try {
            assert mCameraManager != null;
            String[] cameraIdList = mCameraManager.getCameraIdList();   //获取当前设备的全部摄像头id集合
            if (cameraIdList.length == 0) {
                Log.e(TAG, "selectCamera: cameraIdList length is 0");
            }
            for (String cameraId : cameraIdList) { //遍历所有摄像头
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);//得到当前id的摄像头描述特征
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING); //获取摄像头的方向特征信息
                if (facing == CameraCharacteristics.LENS_FACING_BACK) { //这里选择了后摄像头
                    mCurrentSelectCamera = cameraId;

                }
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void initCameraDeviceStateCallback() {
        mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                //摄像头被打开
                try {
                    mCameraDevice = camera;
                    Size cameraSize = getMatchingSize2();//计算获取需要的摄像头分辨率
                    SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();//得到纹理
                    surfaceTexture.setDefaultBufferSize(cameraSize.getWidth(), cameraSize.getHeight());
                    Surface previewSurface = new Surface(surfaceTexture);
                    mPreviewCaptureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    mPreviewCaptureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    mPreviewCaptureRequest.addTarget(previewSurface);
                    mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), mSessionStateCallback, mChildHandler);//创建数据捕获会话，用于摄像头画面预览，这里需要等待mSessionStateCallback回调
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                //摄像头断开

            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                //异常

            }
        };
    }

    private void initSessionStateCallback() {
        mSessionStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                mCameraCaptureSession = session;
                try {
                    //执行重复获取数据请求，等于一直获取数据呈现预览画面，mSessionCaptureCallback会返回此次操作的信息回调
                    mCameraCaptureSession.setRepeatingRequest(mPreviewCaptureRequest.build(), mSessionCaptureCallback, mChildHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

            }
        };
    }

    private void initSessionCaptureCallback() {
        mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
            }
        };
    }

    /**
     * 打开摄像头，这里打开摄像头后，我们需要等待mCameraDeviceStateCallback的回调
     */
    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            mCameraManager.openCamera(mCurrentSelectCamera, mCameraDeviceStateCallback, mChildHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 计算需要的使用的摄像头分辨率
     *
     * @return
     */
    private Size getMatchingSize2() {
        Size selectSize = null;
        try {
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCurrentSelectCamera);
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics(); //因为我这里是将预览铺满屏幕,所以直接获取屏幕分辨率
            int deviceWidth = displayMetrics.widthPixels; //屏幕分辨率宽
            int deviceHeigh = displayMetrics.heightPixels; //屏幕分辨率高
            Log.e(TAG, "getMatchingSize2: 屏幕密度宽度=" + deviceWidth);
            Log.e(TAG, "getMatchingSize2: 屏幕密度高度=" + deviceHeigh);
            /**
             * 循环40次,让宽度范围从最小逐步增加,找到最符合屏幕宽度的分辨率,
             * 你要是不放心那就增加循环,肯定会找到一个分辨率,不会出现此方法返回一个null的Size的情况
             * ,但是循环越大后获取的分辨率就越不匹配
             */
            for (int j = 1; j < 41; j++) {
                for (int i = 0; i < sizes.length; i++) { //遍历所有Size
                    Size itemSize = sizes[i];
                    Log.e(TAG, "当前itemSize 宽=" + itemSize.getWidth() + "高=" + itemSize.getHeight());
                    //判断当前Size高度小于屏幕宽度+j*5  &&  判断当前Size高度大于屏幕宽度-j*5  &&  判断当前Size宽度小于当前屏幕高度
                    if (itemSize.getHeight() < (deviceWidth + j * 5) && itemSize.getHeight() > (deviceWidth - j * 5)) {
                        if (selectSize != null) { //如果之前已经找到一个匹配的宽度
                            if (Math.abs(deviceHeigh - itemSize.getWidth()) < Math.abs(deviceHeigh - selectSize.getWidth())) { //求绝对值算出最接近设备高度的尺寸
                                selectSize = itemSize;
                                continue;
                            }
                        } else {
                            selectSize = itemSize;
                        }

                    }
                }
                if (selectSize != null) { //如果不等于null 说明已经找到了 跳出循环
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "getMatchingSize2: 选择的分辨率宽度=" + selectSize.getWidth());
        Log.e(TAG, "getMatchingSize2: 选择的分辨率高度=" + selectSize.getHeight());
        return selectSize;
    }
}