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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BLEController
{
    private static final UUID SERVICE_UUID = UUID.fromString("a22b1352-4007-11e8-b467-0ed5f89f718b");
    private static final UUID CHARACTERISTIC_SENSOR_VALUE = UUID.fromString("a22b15dc-4007-11e8-b467-0ed5f89f718b");
    private static final UUID CHARACTERISTIC_ON_BED_VALUE = UUID.fromString("a22b1730-4007-11e8-b467-0ed5f89f718b");
    private static final UUID CHARACTERISTIC_NOT_ON_BED_VALUE = UUID.fromString("a22b1852-4007-11e8-b467-0ed5f89f718b");


    private final BluetoothAdapter bluetoothAdapter;
    private final String deviceAddr;
    private final Context context;
    private final BluetoothGattCallback callback = new GattCallback();
    private final List<IDeviceListener> valueListeners = new ArrayList<>();

    private BluetoothGatt gatt;
    private boolean isConnecting;

    private boolean isConnected;

    private boolean isOnBedInitialized;
    private float onBedValue;

    private boolean isNotInBedInitialized;
    private float notOnBedValue;

    public BLEController(BluetoothAdapter bluetoothAdapter, String deviceAddr, Context context) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.deviceAddr = deviceAddr;
        this.context = context;
    }

    public boolean isConnecting()
    {
        return isConnecting;
    }

    public void connect()
    {
        if(isConnecting()){
            return;
        }

        isConnecting = true;

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
        if(!isConnecting()){
            throw new IllegalStateException("Not connected");
        }

        gatt.disconnect();
        log("Disconnecting " + gatt.getDevice().getName());
        isConnecting = false;
    }

    public boolean isOnBedInitialized() {
        return isOnBedInitialized && isNotInBedInitialized;
    }

    public boolean isOnBed(float value) {
        return Math.abs(onBedValue - value) < Math.abs(notOnBedValue - value);
    }

    public void addValueListener(IDeviceListener listener) {
        valueListeners.add(listener);
        if(isConnected) {
            listener.onConnected();
        }
    }

    public void removeValueListener(IDeviceListener listener) {
        valueListeners.remove(listener);
    }

    public boolean hasValueListeners() {
        return !valueListeners.isEmpty();
    }

    protected void notifyValueListeners(float value) {
        for(IDeviceListener listener : valueListeners) {
            listener.onValueChanged(value);
        }
    }

    protected void notifyDeviceConnected() {
        for(IDeviceListener listener : valueListeners) {
            listener.onConnected();
        }
    }

    protected void notifyDeviceDisconnected() {
        for(IDeviceListener listener : valueListeners) {
            listener.onDisconnected();
        }
    }

    protected void notifyDeviceNotSupported() {
        for(IDeviceListener listener : valueListeners) {
            listener.deviceNotSupported();
        }
    }

    protected void deviceNotSupported() {
        notifyDeviceNotSupported();
        disconnect();
    }

    public static interface IDeviceListener {

        public void onValueChanged(float newValue);

        public void onConnected();

        public void onDisconnected();

        public void deviceNotSupported();
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
                isConnected = false;
                log("Connecting to " + gatt.getDevice().getName() + " status");
            } else if(status == BluetoothGatt.STATE_DISCONNECTING) {
                isConnected = false;
                log("Disconnecting: " + gatt.getDevice().getName()+ " status");
            }
            else if(status == BluetoothGatt.STATE_DISCONNECTED) {
                // Any state in which gatt is not connected implies that isConnected is false
                isConnected = false;
                notifyDeviceDisconnected();
                log("Disconnected: " + gatt.getDevice().getName() + " status");
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
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if(service == null) {
                    deviceNotSupported();
                }

                BluetoothGattCharacteristic currentValueCharacteristic = service.getCharacteristic(CHARACTERISTIC_SENSOR_VALUE);
                if(currentValueCharacteristic == null) {
                    deviceNotSupported();
                }

                log("Set characteristic notification: " + gatt.setCharacteristicNotification(currentValueCharacteristic, true));
                BluetoothGattDescriptor descriptor = currentValueCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);

            } else {
                log("Error discovering services");
            }
            super.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(CHARACTERISTIC_ON_BED_VALUE.equals(characteristic.getUuid())) {
                if(!isZeros(characteristic.getValue())) {
                    isOnBedInitialized = true;
                    float value = getFloat(characteristic);
                    log("OnBed characteristic value: " + value);
                    onBedValue = value;
                }

                BluetoothGattCharacteristic notOnBedValueCharacteristic = characteristic.getService().getCharacteristic(CHARACTERISTIC_NOT_ON_BED_VALUE);
                if(notOnBedValueCharacteristic == null) {
                    deviceNotSupported();
                }
                gatt.readCharacteristic(notOnBedValueCharacteristic);
            }
            if(CHARACTERISTIC_NOT_ON_BED_VALUE.equals(characteristic.getUuid())) {
                if(!isZeros(characteristic.getValue())) {
                    isNotInBedInitialized = true;
                    float value = getFloat(characteristic);
                    log("NotOnBed characteristic value: " + value);
                    notOnBedValue = value;
                }
            }

            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            if(CHARACTERISTIC_SENSOR_VALUE.equals(characteristic.getUuid())) {
                float value = getFloat(characteristic);
                log("Characteristic " + characteristic.getUuid() + " changed. New value: " + value);

                notifyValueListeners(value);

                BluetoothGattCharacteristic onBedValueCharacteristic = characteristic.getService().getCharacteristic(CHARACTERISTIC_ON_BED_VALUE);
                if(onBedValueCharacteristic == null) {
                    deviceNotSupported();
                }
                gatt.readCharacteristic(onBedValueCharacteristic);
            }

            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            String value = new String(descriptor.getValue());
            log("Descriptor " + descriptor.getUuid() + " value: " + value);

            super.onDescriptorRead(gatt, descriptor, status);
        }

        private float getFloat(BluetoothGattCharacteristic characteristic) {
            byte[] byteValue = characteristic.getValue();
            return ByteBuffer.wrap(byteValue).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        }

        private boolean isZeros(byte[] bytes) {
            boolean result = true;
            for(byte b : bytes) {
                result &= (b == 0);
            }
            return  result;
        }
    }
}
