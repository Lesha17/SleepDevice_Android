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
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class BLEController
{
    private static final UUID SERVICE_UUID = UUID.fromString("a22b1352-4007-11e8-b467-0ed5f89f718b");
    private static final UUID CHARACTERISTIC_SENSOR_VALUE = UUID.fromString("a22b15dc-4007-11e8-b467-0ed5f89f718b");
    private static final UUID CHARACTERISTIC_ON_BED_VALUE = UUID.fromString("a22b1730-4007-11e8-b467-0ed5f89f718b");
    private static final UUID CHARACTERISTIC_NOT_ON_BED_VALUE = UUID.fromString("a22b1852-4007-11e8-b467-0ed5f89f718b");

    private static final int CONNECTION_TIMEOUT = 10000;

    private TimerTask couldNotConnectTimeoutTask;

    private final BluetoothAdapter bluetoothAdapter;
    private final String deviceAddr;
    private final Context context;
    private final GattCallback callback = new GattCallback();
    private final IDeviceListener listener;

    private BluetoothGatt gatt;

    private boolean isOnBedInitialized;
    private float onBedValue;

    private boolean isNotInBedInitialized;
    private float notOnBedValue;

    public BLEController(BluetoothAdapter bluetoothAdapter, String deviceAddr, Context context, IDeviceListener listener) {
        this.bluetoothAdapter = bluetoothAdapter;
        this.deviceAddr = deviceAddr;
        this.context = context;
        this.listener = listener;
    }

    public void setStatusValues(float onBedValue, float notOnBedValue) {
        callback.writeStatusSettings(onBedValue, notOnBedValue);
    }

    public void connect()
    {
        if(gatt == null) {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddr);
            gatt = device.connectGatt(context, false, callback);
        } else {
            gatt.connect();
        }

        couldNotConnectTimeoutTask = new TimerTask() {
            @Override
            public void run() {
                log("COuld not connect?!");
                couldNotConnect();
            }
        };
        new Timer().schedule(couldNotConnectTimeoutTask, CONNECTION_TIMEOUT);
        log("Connecting to " + gatt.getDevice().getName());
    }

    public void disconnect()
    {
        gatt.disconnect();
        gatt.close();
        log("Disconnecting " + gatt.getDevice().getName());
    }

    public boolean isOnBedInitialized() {
        return isOnBedInitialized && isNotInBedInitialized;
    }

    public boolean isOnBed(float value) {
        return Math.abs(onBedValue - value) < Math.abs(notOnBedValue - value);
    }

    protected void notifyValueListeners(float value) {
        listener.onValueChanged(value);
    }

    protected void notifyNewStatusSettings() {
        listener.onNewStatusSettings(onBedValue, notOnBedValue);
    }

    protected void notifyDeviceConnected() {
        listener.onConnected();
    }

    protected void notifyDeviceDisconnected() {
        listener.onDisconnected();
    }

    protected void notifyDeviceNotSupported() {
        listener.deviceNotSupported();
    }

    protected void notifyCouldNotConnect() {
        listener.couldNotConnect();
    }

    protected void deviceNotSupported() {
        notifyDeviceNotSupported();
        disconnect();
    }

    protected void couldNotConnect() {
        notifyCouldNotConnect();
        disconnect();
    }


    public static interface IDeviceListener {

        public void onValueChanged(float newValue);

        public void onNewStatusSettings(float onBedValue, float notOnBedValue);

        public void onConnected();

        public void onDisconnected();

        public void deviceNotSupported();

        public void couldNotConnect();
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
                couldNotConnectTimeoutTask.cancel();
                notifyDeviceConnected();
                log("Connected to " + gatt.getDevice().getName() + " status");
                log("Discover services: " + gatt.discoverServices());
            } else if (newState == BluetoothGatt.STATE_CONNECTING) {
                log("Connecting to " + gatt.getDevice().getName() + " status");
            } else if(newState == BluetoothGatt.STATE_DISCONNECTING) {
                log("Disconnecting: " + gatt.getDevice().getName()+ " status");
            }
            else if(newState == BluetoothGatt.STATE_DISCONNECTED) {
                notifyDeviceDisconnected();
                log("Disconnected: " + gatt.getDevice().getName() + " status");
            }
            log("Connection status changed. New status: " + newState);
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
