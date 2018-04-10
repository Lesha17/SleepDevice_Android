package com.machnev.sleepdevice.core;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;

public class BLEController
{
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt gatt;

    public void setBluetoothAdapter(BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public  BLEScanner getScanner()
    {
        return new BLEScanner(bluetoothAdapter);
    }

    public boolean isConnected()
    {
        return gatt != null;
    }

    public boolean connectTo(BluetoothDevice device, Context context)
    {
        gatt = device.connectGatt(context, false, new GattCallback(context));
        log("Connected to " + device.getName());
        return gatt != null;
    }

    public boolean disconnect()
    {
        if(!isConnected()){
            throw new IllegalStateException("Not connected");
        }
        gatt.disconnect();
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

    private class GattCallback extends BluetoothGattCallback {
        private final Context context;

        private GattCallback(Context context) {
            this.context = context;
        }


        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            log("State is changed. New state: " + newState);
            super.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            log("Services discovered. Status: " + status);
            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String charValue = Arrays.toString(characteristic.getValue());
            log("Characteristic read. Value: " + charValue);
            Toast.makeText(context, "Characteristic read: " + charValue, Toast.LENGTH_SHORT);
            super.onCharacteristicRead(gatt, characteristic, status);
        }
    }
}
