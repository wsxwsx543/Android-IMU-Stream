package com.example.imu_data_collection_smartwatch;

import static android.content.Context.WIFI_SERVICE;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class UdpClient {
    private final static String TAG = UdpClient.class.getName();
    private InetAddress ipAddress;
    private InetAddress gateway;
    final static private int CLIENT_PORT = 1234;
    final static private int SERVER_PORT = 12345;

    private final DatagramSocket socket;

    private final BlockingQueue<float[]> packetBuf;

    public UdpClient(Context context, String dest_ip) throws SocketException, UnknownHostException {
        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        this.ipAddress = ipInt2InetAddress(wifiInfo.getIpAddress());
        this.gateway = InetAddress.getByName(dest_ip);
        socket = new DatagramSocket(CLIENT_PORT);
        packetBuf = new LinkedBlockingQueue<>(1024);
        sendThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!sendThread.isInterrupted()) {
                    try {
                        Log.d(TAG, "run: packbuf length: " + packetBuf.size());
                        float[] top = packetBuf.take();
                        byte[] bytes = IMUService.floatArrayToByteArray(top);
                        send(bytes);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        sendThread.start();
    }

    public void add2Buffer(float[] bytes) {
        packetBuf.add(bytes);
    }

    private final Thread sendThread;

    public void send(byte[] msg) {
        DatagramPacket packet = new DatagramPacket(msg, 0, msg.length, this.gateway, SERVER_PORT);
        try {
            Log.d(TAG, "send: sending packet");
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String ipInt2Str(int ipAddress) {
        return (ipAddress & 0xff)
                + "." + (ipAddress>>8 & 0xff)
                + "." + (ipAddress>>16 & 0xff)
                + "." + (ipAddress >> 24 & 0xff);
    }

    private InetAddress ipInt2InetAddress(int ipAddress) throws UnknownHostException {
        byte[] bytes = BigInteger.valueOf(ipAddress).toByteArray();
        return InetAddress.getByAddress(bytes);
    }

    public void close() {
        sendThread.interrupt();
        socket.close();
    }
}
