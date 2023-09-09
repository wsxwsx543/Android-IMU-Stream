package com.example.imu_data_collection_smartwatch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
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
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.example.imu_data_collection_smartwatch.databinding.ActivityMainBinding;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private final String TAG = "MainActivity";
    final static private String MY_UUID = "680ac785-9ed1-42cd-bdf0-76cf6b708f3b";
    private BluetoothAdapter bluetoothAdapter;

    private TextView mTextView;
    private final static int REQUEST_ENABLE_BT = 1;
    private ActivityMainBinding binding;

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mTextView = binding.text;
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
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
//                != PackageManager.PERMISSION_GRANTED) {
//            return;
//        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onCreate: Request Permission");
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION},
                    2);
        }
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
            connectThread.start();
        }
        Intent serviceIntent = new Intent(this, IMUService.class);
        startForegroundService(serviceIntent);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        @SuppressLint("MissingPermission")
        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        @Override
        @SuppressLint("MissingPermission")
        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            Log.d(TAG, "run: Connected Successfully!");
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }
}