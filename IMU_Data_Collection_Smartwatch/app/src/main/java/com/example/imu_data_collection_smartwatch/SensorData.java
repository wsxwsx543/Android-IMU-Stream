package com.example.imu_data_collection_smartwatch;

import java.io.Serializable;

public class SensorData implements Serializable {
    public char name;
    public long timestamp;
    public float x, y, z;

    public SensorData() {}
}