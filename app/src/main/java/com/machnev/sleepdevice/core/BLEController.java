package com.machnev.sleepdevice.core;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BLEController
{
    private final BluetoothAdapter bluetoothAdapter;
    private final String deviceAddr;
    private final Context context;
    private final BluetoothGattCallback callback = new GattCallback();
    private final List<IControllerListener> valueListeners = new ArrayList<>();

    private BluetoothGatt gatt;
    private boolean isConnected;

    public BLEController(BluetoothAdapter bluetoothAdapter, String deviceAddr, Context context) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.deviceAddr = deviceAddr;
        this.context = context;
    }

    public boolean isConnected()
    {
        return isConnected;
    }

    public void connect()
    {
        if(isConnected()){
            throw new IllegalStateException("Already connected");
        }

        if(gatt == null) {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddr);
            gatt = device.connectGatt(context, false, callback);
        } else {
            gatt.connect();
        }
        log("Connecting to " + gatt.getDevice().getName());
    }

    public void disconnect()
    {
        if(!isConnected()){
            throw new IllegalStateException("Not connected");
        }

        gatt.disconnect();
        log("Disconnected " + gatt.getDevice().getName());
    }

    public boolean isOnBed(float value) {
        return false; // TODO implement properly
    }

    public void addListener(IControllerListener listener) {
        valueListeners.add(listener);
    }

    public void removeListener(IControllerListener listener) {
        valueListeners.remove(listener);
    }

    public boolean hasValueListeners() {
        return !valueListeners.isEmpty();
    }

    protected void notifyValueListeners(float value) {
        for(IControllerListener listener : valueListeners) {
            listener.onValueChanged(value);
        }
    }

    public static interface IControllerListener {
        public void onValueChanged(float newValue);
    }

    private void log(String message) {
        Log.i(BLEController.class.getName(), message);
    }

    private class GattCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(newState == BluetoothGatt.STATE_CONNECTED) {
                isConnected = true;
                log("Connected to " + gatt.getDevice().getName());
                log("Discover services: " + gatt.discoverServices());
            } else {
                // Any state in which gatt is not connected implies that isConnected is false
                isConnected = false;
            }
            log("Connection status changed. New status: " + status);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            BluetoothGattService service = gatt.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"));
            BluetoothGattCharacteristic tx = service.getCharacteristic(UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"));
            log("Set characteristic notification: " + gatt.setCharacteristicNotification(tx, true));
            BluetoothGattDescriptor descriptor = tx.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);

            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String value = new String(characteristic.getValue());
            log("Read characteristic " + characteristic.getUuid() + " completed. New value: " + value);

            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            float value = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0);
            log("Characteristic " + characteristic.getUuid() + " changed. New value: " + value);

            notifyValueListeners(value);

            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            String value = new String(descriptor.getValue());
            log("Descriptor " + descriptor.getUuid() + " value: " + value);

            super.onDescriptorRead(gatt, descriptor, status);
        }
    }
}
