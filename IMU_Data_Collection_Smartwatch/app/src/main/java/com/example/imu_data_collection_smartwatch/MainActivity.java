package com.example.imu_data_collection_smartwatch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
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
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.example.imu_data_collection_smartwatch.databinding.ActivityMainBinding;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private final String TAG = "MainActivity";

    private boolean isStarting = false;
    public static boolean saveLocal = false;
    private Button button;
    private ActivityMainBinding binding;
    private String ip;
    public static UdpClient udpClient = null;

    @SuppressLint("WakelockTimeout")
    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        EditText editText = findViewById(R.id.dest_ip);
        CheckBox checkBox = findViewById(R.id.local_checkbox);

        button = findViewById(R.id.button);
        String[] permissions = new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "onCreate: Request Permission");
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{
                                permission
                        },
                        2);
            }
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent serviceIntent = new Intent(MainActivity.this, IMUService.class);
                saveLocal = checkBox.isChecked();
                if (!isStarting) {
                    if (!saveLocal) {
                        try {
                            udpClient = new UdpClient(MainActivity.this, editText.getText().toString());
                        } catch (SocketException | UnknownHostException e) {
                            e.printStackTrace();
                        }
                    } else {
                        udpClient = null;
                    }
                    startForegroundService(serviceIntent);
                    button.setText("STOP");
                } else {
                    if (!saveLocal) {
                        if (udpClient != null) udpClient.close();
                    }
                    stopService(serviceIntent);
                    button.setText("START");
                }
                isStarting = !isStarting;
            }
        });
    }

}