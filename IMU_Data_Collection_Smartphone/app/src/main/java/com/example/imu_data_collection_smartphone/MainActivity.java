package com.example.imu_data_collection_smartphone;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    final private String TAG = MainActivity.class.getName();
    final static private String MY_UUID = "680ac785-9ed1-42cd-bdf0-76cf6b708f3b";
    final static private int REQUEST_ENABLE_BT = 0;
    private BluetoothAdapter bluetoothAdapter;

    // colors to display for graphs on the screen
    final private int graphColor[] = {
            Color.argb(255, 255, 180, 9), // orange
            Color.argb(255, 46, 168, 255), // blue
            Color.argb(255, 129, 209, 24), // green
            Color.argb(255, 225, 225, 0), // yellow
            Color.argb(255, 150, 150, 150)};
//    private ArrayList<LineGraphSeries<DataPoint>> acc_display_buf = new ArrayList<>();
//    private ArrayList<LineGraphSeries<DataPoint>> gyro_display_buf = new ArrayList<>();

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onCreate: Request Permission");
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{
                            android.Manifest.permission.BLUETOOTH,
                            android.Manifest.permission.BLUETOOTH_ADMIN,
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                            android.Manifest.permission.WAKE_LOCK,
                            android.Manifest.permission.FOREGROUND_SERVICE,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
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

    private ConnectedThread connectedThread;
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        @SuppressLint("MissingPermission")
        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("IMU_Data_Collection"
                        , UUID.fromString(MY_UUID));
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        @Override
        public void run() {
            BluetoothSocket socket = null;
            Log.d(TAG, "run: trying to connect...");
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    Log.d(TAG, "run: bluetooth connected SUCCESSFULLY!");
//                    manageMyConnectedSocket(socket);
                    connectedThread = new ConnectedThread(socket);
                    connectedThread.start();
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}