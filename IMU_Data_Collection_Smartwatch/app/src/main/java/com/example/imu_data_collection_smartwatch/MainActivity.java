package com.example.imu_data_collection_smartwatch;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import com.example.imu_data_collection_smartwatch.databinding.ActivityMainBinding;

public class MainActivity extends Activity {

    private final String TAG = "MainActivity";
    private TextView mTextView;

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mTextView = binding.text;

        Intent serviceIntent = new Intent(this, IMUService.class);
        startForegroundService(serviceIntent);
    }
}