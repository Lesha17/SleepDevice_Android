package com.machnev.sleepdevice.core;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;

public class BLEController
{
    private final BluetoothAdapter bluetoothAdapter;
    private final String deviceAddr;

    private BluetoothGatt gatt;

    public BLEController(BluetoothAdapter bluetoothAdapter, String deviceAddr) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.deviceAddr = deviceAddr;
    }

    public boolean isConnected()
    {
        return gatt != null;
    }

    public boolean connect(Context context, BluetoothGattCallback callback)
    {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddr);
        gatt = device.connectGatt(context, false, callback);
        log("Connecting to " + device.getName());
        return gatt != null;
    }

    public boolean disconnect()
    {
        if(!isConnected()){
            throw new IllegalStateException("Not connected");
        }
        gatt.disconnect();
        log("Disconnected " + gatt.getDevice().getName());
        gatt = null;
        return true;
    }

    public float getCurrentValue()
    {
        return 0f;
    }

    private void log(String message) {
        Log.i(BLEController.class.getName(), message);
    }
}
