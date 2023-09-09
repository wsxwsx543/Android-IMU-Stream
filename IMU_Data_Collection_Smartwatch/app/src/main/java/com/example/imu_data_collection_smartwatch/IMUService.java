package com.example.imu_data_collection_smartwatch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.example.imu_data_collection_smartwatch.databinding.ActivityMainBinding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class IMUService extends Service implements SensorEventListener {

    private final String TAG = this.getClass().getName();
    final static private String ACC_UUID = "680ac785-9ed1-42cd-bdf0-76cf6b708f3b";
    final static private String GYRO_UUID = "e0740e55-4d73-4e97-b0fb-90afc0ebb980";
    private BluetoothAdapter bluetoothAdapter;

    private ConnectedThread connectedThreadAcc;
    private ConnectedThread connectedThreadGyro;
    public IMUService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
        // TODO: Return the communication channel to the service.
//        throw new UnsupportedOperationException("Not yet implemented");
    }

    private SensorManager sensorManager;
    private Sensor linearAccelerometer, gyroscope;

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.d(TAG, "onCreate: This device doesn't have bluetooth");
            return super.onStartCommand(intent, flags, startId);
        } else {
            Log.d(TAG, "onCreate: bluetooth is available on this device");
        }
        assert true;
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        Log.d(TAG, "onCreate: pairedDevices length: " + pairedDevices.size());
        BluetoothDevice discoverableDevice = null;
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                discoverableDevice = device;
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.d(TAG, "onCreate: Bluetooth device name: " + deviceName);
            }
        }
        if (discoverableDevice == null) {
            Log.d(TAG, "onCreate: no available bluetooth device");
        } else {
            ConnectThread connectThread = new ConnectThread(discoverableDevice);
            Log.d(TAG, "onStartCommand: trying to connect...");
            connectThread.start();
        }

        sendingAccThread = new Thread(accSendRunnable);
        sendingGyroThread = new Thread(gyroSendRunnable);
        sendingAccThread.start();
        sendingGyroThread.start();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        sensorManager.registerListener(this, linearAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);

        final String CHANNEL_ID = "IMUService";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_ID,
                NotificationManager.IMPORTANCE_LOW);

        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification.Builder notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentText("IMU Data is Collecting")
                .setContentTitle("IMU Stream");
        startForeground(1001, notification.build());

        return super.onStartCommand(intent, flags, startId);
    }

//    private SensorData sensorData = new SensorData();
    private final Object changeLock = new Object();
    // x, y, z, timestamp, name, index
    int cnt = 0;
    BlockingQueue<float[]> accBuffer = new LinkedBlockingQueue<>(1024);
    BlockingQueue<float[]> gyroBuffer = new LinkedBlockingQueue<>(1024);
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
//        synchronized (changeLock) {
//            sensorData.timestamp = sensorEvent.timestamp;
            float[] sensorData = new float[6];
            sensorData[0] = sensorEvent.values[0];
            sensorData[1] = sensorEvent.values[1];
            sensorData[2] = sensorEvent.values[2];
            sensorData[3] = sensorEvent.timestamp;
            sensorData[5] = cnt++;
//            Log.d(TAG, "run: Receive updated sensor data:"
//                + " index: " + sensorData[5]
//                + " name: " + sensorData[4]
//                + " timestamp: " + sensorData[3]
//                + " x: " + sensorData[0]
//                + " y: " + sensorData[1]
//                + " z: " + sensorData[2]);
            if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                sensorData[4] = 0;
                gyroBuffer.add(sensorData);
            } else if (sensorEvent.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                sensorData[4] = 1;
                accBuffer.add(sensorData);
            }
//            Log.d(TAG, "onSensorChanged: write to socket");
//        }
    }

    private Thread sendingAccThread;
    private Thread sendingGyroThread;
    private final Runnable accSendRunnable = new Runnable() {
        @Override
        public void run() {
            while (!sendingAccThread.isInterrupted()) {
                try {
                    float[] sensorData = accBuffer.take();
                    if (connectedThreadAcc != null)
                        connectedThreadAcc.write(floatArrayToByteArray(sensorData));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private final Runnable gyroSendRunnable = new Runnable() {
        @Override
        public void run() {
            while (!sendingGyroThread.isInterrupted()) {
                try {
                    float[] sensorData = gyroBuffer.take();
                    if (connectedThreadGyro != null)
                        connectedThreadGyro.write(floatArrayToByteArray(sensorData));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    public static byte[] floatArrayToByteArray(float[] floatArray) {
        ByteBuffer buffer = ByteBuffer.allocate(floatArray.length * 4); // 4 bytes per float
        for (float value : floatArray) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        Log.d(TAG, "accuracy changed");
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket accSocket;
        private final BluetoothSocket gyroSocket;
        private final BluetoothDevice mmDevice;

        @SuppressLint("MissingPermission")
        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp1 = null, tmp2 = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp1 = device.createRfcommSocketToServiceRecord(UUID.fromString(ACC_UUID));
                tmp2 = device.createRfcommSocketToServiceRecord(UUID.fromString(GYRO_UUID));
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            accSocket = tmp1;
            gyroSocket = tmp2;
        }

        @Override
        @SuppressLint("MissingPermission")
        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                Log.d(TAG, "run: connecting");
                accSocket.connect();
                gyroSocket.connect();
                Log.d(TAG, "run: Connected Successfully!");
            } catch (IOException connectException) {
                Log.d(TAG, "run: cannot connect");
                // Unable to connect; close the socket and return.
                try {
                    accSocket.close();
                    gyroSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            Log.d(TAG, "run: Connected Successfully!");
            connectedThreadAcc = new ConnectedThread(accSocket);
            connectedThreadGyro = new ConnectedThread(gyroSocket);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                accSocket.close();
                gyroSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }
}