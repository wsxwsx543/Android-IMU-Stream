package com.example.imu_data_collection_smartphone;

import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class ConnectedThread extends Thread {
    private static final String TAG = ConnectedThread.class.getName();
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private byte[] mmBuffer; // mmBuffer store for the stream

    public ConnectedThread(BluetoothSocket socket) {
        mmSocket = socket;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the input and output streams; using temp objects because
        // member streams are final.
        try {
            tmpIn = socket.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating input stream", e);
        }
        try {
            tmpOut = socket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating output stream", e);
        }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }

    @Override
    public void run() {
        mmBuffer = new byte[1024];
        int numBytes; // bytes returned from read()

        // Keep listening to the InputStream until an exception occurs.
        while (true) {
            try {
                // Read from the InputStream.
                numBytes = mmInStream.read(mmBuffer);
                float[] sensorData = byteArrayToFloatArray(mmBuffer);
//                Object sensorData = (Object) deserialize(mmBuffer);
                Log.d(TAG, "run: Receive updated sensor data:"
                        + " name: " + sensorData[3]
                        + " x: " + sensorData[0]
                        + " y: " + sensorData[1]
                        + " z: " + sensorData[2]);
            } catch (IOException e) {
                Log.d(TAG, "Input stream was disconnected", e);
                break;
            }
        }
    }

    public static float[] byteArrayToFloatArray(byte[] byteArray) {
        if (byteArray.length % 4 != 0) {
            throw new IllegalArgumentException("Byte array length must be a multiple of 4");
        }

        float[] floatArray = new float[byteArray.length / 4];
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        for (int i = 0; i < floatArray.length; i++) {
            floatArray[i] = buffer.getFloat();
        }
        return floatArray;
    }

    // Call this from the main activity to send data to the remote device.
    public void write(byte[] bytes) {
        try {
            mmOutStream.write(bytes);
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when sending data", e);
        }
    }

    // Call this method from the main activity to shut down the connection.
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the connect socket", e);
        }
    }

    public static Object deserialize(byte[] data)
            throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = new ObjectInputStream(in);
        return is.readObject();
    }
}
