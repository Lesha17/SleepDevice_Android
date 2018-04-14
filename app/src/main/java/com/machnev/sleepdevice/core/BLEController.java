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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BLEController
{
    private final BluetoothAdapter bluetoothAdapter;
    private final String deviceAddr;
    private final Context context;
    private final BluetoothGattCallback callback = new GattCallback();
    private final List<IDeviceValueListener> valueListeners = new ArrayList<>();
    private final List<IDeviceConnectionListener> deviceConnectionListeners = new ArrayList<>();

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

    public void addValueListener(IDeviceValueListener listener) {
        valueListeners.add(listener);
    }

    public void removeValueListener(IDeviceValueListener listener) {
        valueListeners.remove(listener);
    }

    public boolean hasValueListeners() {
        return !valueListeners.isEmpty();
    }

    public void addDeviceConnectionListener(IDeviceConnectionListener listener) {
        if(!deviceConnectionListeners.contains(listener)) {
            deviceConnectionListeners.add(listener);
        }
    }

    public void removeDeiceConnectionListener(IDeviceConnectionListener listener) {
        deviceConnectionListeners.remove(listener);
    }

    protected void notifyValueListeners(float value) {
        for(IDeviceValueListener listener : valueListeners) {
            listener.onValueChanged(value);
        }
    }

    protected void notifyDeviceConnected() {
        for(IDeviceConnectionListener listener : deviceConnectionListeners) {
            listener.onConnected();
        }
    }

    protected void notifyDeviceDisconnected() {
        for(IDeviceConnectionListener listener : deviceConnectionListeners) {
            listener.onDisconnected();
        }
    }

    public static interface IDeviceValueListener {

        public void onValueChanged(float newValue);
    }

    public static interface IDeviceConnectionListener {
        public void onConnected();

        public void onDisconnected();
    }

    private void log(String message) {
        Log.i(BLEController.class.getName(), message);
    }

    private class GattCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(newState == BluetoothGatt.STATE_CONNECTED) {
                isConnected = true;
                notifyDeviceConnected();
                log("Connected to " + gatt.getDevice().getName() + " status");
                log("Discover services: " + gatt.discoverServices());
            } else if (status == BluetoothGatt.STATE_CONNECTING) {
                log("Connecting to " + gatt.getDevice().getName() + " status");
            } else if(status == BluetoothGatt.STATE_DISCONNECTING) {
                log("Disconnecting: " + gatt.getDevice().getName()+ " status");
            }
            else if(status == BluetoothGatt.STATE_DISCONNECTED) {
                // Any state in which gatt is not connected implies that isConnected is false
                notifyDeviceDisconnected();
                log("Disconnected: " + gatt.getDevice().getName() + " status");
                isConnected = false;
            }
            log("Connection status changed. New status: " + status);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            log("Total services count: " + gatt.getServices().size());
            for(BluetoothGattService service : gatt.getServices()) {
                log("Service: " + service.getUuid()
                        + "; total included services: " + service.getIncludedServices().size()
                        + "; total characteristics: " + service.getCharacteristics().size());
                for(BluetoothGattService includedService : service.getIncludedServices())
                {
                    log("\tIncluded service: " + includedService.getUuid());
                }

                for(BluetoothGattCharacteristic characteristic : service.getCharacteristics())
                {
                    log("\tCharacteristic " + characteristic.getUuid() + "; total descriptors: " + characteristic.getDescriptors().size());
                    for(BluetoothGattDescriptor descriptor : characteristic.getDescriptors())
                    {
                        log("\t\tDescriptor " + descriptor.getUuid());
                    }
                }
            }

            if(status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"));
                BluetoothGattCharacteristic tx = service.getCharacteristic(UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"));
                log("Set characteristic notification: " + gatt.setCharacteristicNotification(tx, true));
                BluetoothGattDescriptor descriptor = tx.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            } else {
                log("Error discovering services");
            }
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
            byte[] byteValue = characteristic.getValue();
            float value = ByteBuffer.wrap(byteValue).order(ByteOrder.LITTLE_ENDIAN).getFloat();
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
