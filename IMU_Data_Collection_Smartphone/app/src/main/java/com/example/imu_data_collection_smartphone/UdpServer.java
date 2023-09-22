package com.example.imu_data_collection_smartphone;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class UdpServer {
    final static private String TAG = UdpServer.class.getName();

    final static private int CLIENT_PORT = 1234;
    final static private int SERVER_PORT = 12345;
    private DatagramSocket socket;
    private Thread recThread;
    private byte[] buf;
    private AccListener accListener;
    private GyroListener gyroListener;

    public UdpServer(AccListener accListener, GyroListener gyroListener) {
        try {
            socket = new DatagramSocket(SERVER_PORT);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        this.accListener = accListener;
        this.gyroListener = gyroListener;
        recThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!recThread.isInterrupted()) {
                    try {
                        socket.receive(packet);
                        float[] data = ConnectedThread.byteArrayToFloatArray(packet.getData());
                        if (data[4] == 1) accListener.accListener(data);
                        else gyroListener.gyroListener(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        recThread.start();
    }

    public void close() {
        recThread.interrupt();
    }
}
