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
    private final GattCallback callback = new GattCallback();
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

    public boolean isConnected() {
        return isConnected;
    }

    public void setStatusValues(float onBedValue, float notOnBedValue) {
        callback.writeStatusSettings(onBedValue, notOnBedValue);
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

    protected void notifyNewStatusSettings() {
        for(IDeviceListener listener : valueListeners) {
            listener.onNewStatusSettings(onBedValue, notOnBedValue);
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

        public void onNewStatusSettings(float onBedValue, float notOnBedValue);

        public void onConnected();

        public void onDisconnected();

        public void deviceNotSupported();
    }

    private void log(String message) {
        Log.i(BLEController.class.getName(), message);
    }

    private class GattCallback extends BluetoothGattCallback {

        private BluetoothGattService service;
        private BluetoothGattCharacteristic currentValueCharacteristic;

        private boolean readingStatusSettingsCharacteristics;

        private boolean writingStatusSettingsCharacteristics;
        private float onBedValueToWrite;
        private float notOnBedValueToWrite;


        private BluetoothGattCharacteristic onBedValueCharacteristic;
        private BluetoothGattCharacteristic notOnBedValueCharacteristic;

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

            if(status == BluetoothGatt.GATT_SUCCESS) {
                service = gatt.getService(SERVICE_UUID);
                if(service == null) {
                    deviceNotSupported();
                }

                currentValueCharacteristic = service.getCharacteristic(CHARACTERISTIC_SENSOR_VALUE);
                if(currentValueCharacteristic == null) {
                    deviceNotSupported();
                }
                onBedValueCharacteristic = service.getCharacteristic(CHARACTERISTIC_ON_BED_VALUE);
                if(onBedValueCharacteristic == null) {
                    deviceNotSupported();
                }
                notOnBedValueCharacteristic = service.getCharacteristic(CHARACTERISTIC_NOT_ON_BED_VALUE);
                if(notOnBedValueCharacteristic == null) {
                    deviceNotSupported();
                }

                readStatusSettings();

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
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            if(CHARACTERISTIC_SENSOR_VALUE.equals(characteristic.getUuid())) {
                float value = getFloat(characteristic);
                log("Characteristic " + characteristic.getUuid() + " changed. New value: " + value);

                notifyValueListeners(value);
            }

            if(readingStatusSettingsCharacteristics) {
                startReadStatusSettings();
            }

            super.onCharacteristicChanged(gatt, characteristic);
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

                if(readingStatusSettingsCharacteristics) {
                    readNotOnBed();
                }
            }
            if(CHARACTERISTIC_NOT_ON_BED_VALUE.equals(characteristic.getUuid())) {
                if(!isZeros(characteristic.getValue())) {
                    isNotInBedInitialized = true;
                    float value = getFloat(characteristic);
                    log("NotOnBed characteristic value: " + value);
                    notOnBedValue = value;

                    if(readingStatusSettingsCharacteristics) {
                        readingStatusSettingsCharacteristics = false;
                        notifyNewStatusSettings();
                    }
                }
            }

            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(CHARACTERISTIC_ON_BED_VALUE.equals(characteristic.getUuid())) {
                if(writingStatusSettingsCharacteristics) {
                    writeNotOnBed();
                }
            }
            if(CHARACTERISTIC_NOT_ON_BED_VALUE.equals(characteristic.getUuid())) {
                if(writingStatusSettingsCharacteristics) {
                    writingStatusSettingsCharacteristics = false;
                    readStatusSettings();
                }
            }

            super.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            String value = new String(descriptor.getValue());
            log("Descriptor " + descriptor.getUuid() + " value: " + value);

            super.onDescriptorRead(gatt, descriptor, status);
        }

        public void readStatusSettings() {
            readingStatusSettingsCharacteristics = true;
        }

        public void writeStatusSettings(float onBedValue, float notOnBedValue) {
            this.onBedValueToWrite = onBedValue;
            this.notOnBedValueToWrite = notOnBedValue;
            writingStatusSettingsCharacteristics = true;
            startWriteStatusSettings();
        }

        private void startReadStatusSettings() {
            readOnBed();
        }

        private void readOnBed() {
            gatt.readCharacteristic(onBedValueCharacteristic);
        }

        private void readNotOnBed() {
            gatt.readCharacteristic(notOnBedValueCharacteristic);
        }

        private void startWriteStatusSettings() {
            writeOnBed();
        }

        private void writeOnBed() {
            onBedValueCharacteristic.setValue(toBytes(onBedValueToWrite));
            gatt.writeCharacteristic(onBedValueCharacteristic);
        }

        private void writeNotOnBed() {
            notOnBedValueCharacteristic.setValue(toBytes(notOnBedValueToWrite));
            gatt.writeCharacteristic(notOnBedValueCharacteristic);
        }


        private float getFloat(BluetoothGattCharacteristic characteristic) {
            byte[] byteValue = characteristic.getValue();
            return ByteBuffer.wrap(byteValue).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        }

        private byte[] toBytes(float f) {
            return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(f).array();
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
