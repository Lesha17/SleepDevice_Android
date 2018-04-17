package com.machnev.sleepdevice;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.machnev.sleepdevice.core.BLEController;
import com.machnev.sleepdevice.core.StatusSettingsData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceService extends Service {

    public static final String DEVICE_ADDRESS = "com.machnev.sleepdevice.DeviceService.DEVICE_ADDRESS";

    public static final int REQUEST_SENSORS_NOTIFICATIONS = 0;
    public static final int STOP_LISTEN_SENSOR_NOTIFICATIONS = 1;
    public static final int SET_STATUS_SETTINGS = 2;

    public static final int DEVICE_CONNECTED = 12;
    public static final int DEVICE_DISCONNECTED = 13;
    public static final int DEVICE_NOT_SUPPORTED = 14;
    public static final int CONNECTION_TIMEOUT = 15;
    public static final int SENSOR_VALUE_AND_ONBED_STATUS = 16;
    public static final int STATUS_VALUES_SET = 17;

    public static final int STATUS_NOT_INITIALIZED = -1;
    public static final int STATUS_NOT_ON_BED = 0;
    public static final int STATUS_ON_BED = 1;

    private final Handler handler = new DeviceServiceHandler();
    private final Messenger messenger = new Messenger(handler);
    private BLEController controller;
    private final List<Messenger> listeners = new ArrayList<>();
    private BluetoothAdapter adapter;

    @Override
    public void onCreate() {

        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();

        Log.i(DeviceService.class.getName(), "onCreate: DeviceService");

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initializeController(intent);

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        initializeController(intent);
        return messenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);

        initializeController(intent);
    }

    @Override
    public void onDestroy() {
        freeController();

        super.onDestroy();
    }

    private void initializeController(Intent intent) {
        String deviceAddress = intent.getStringExtra(DEVICE_ADDRESS);

        if(controller == null) {
            controller = new BLEController(adapter, deviceAddress, this, new ClientNotificationValueListener());
            controller.connect();
        }
    }

    private void freeController() {
        if(controller != null) {
            controller.disconnect();
            controller = null;
        }
    }

    private class DeviceServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REQUEST_SENSORS_NOTIFICATIONS:
                    listeners.add(msg.replyTo);
                    break;
                case STOP_LISTEN_SENSOR_NOTIFICATIONS:
                    listeners.remove(msg.replyTo);
                    break;
                case SET_STATUS_SETTINGS:
                    StatusSettingsData data = (StatusSettingsData) msg.obj;
                    controller.setStatusValues(data.onBedValue, data.notBedValue);
                    break;
                    default:
                        break;
            }
            super.handleMessage(msg);
        }
    }

    private class ClientNotificationValueListener implements BLEController.IDeviceListener {

        @Override
        public void onValueChanged(float newValue) {
            int onBedStatus;
            if(controller.isOnBedInitialized()) {
                if (controller.isOnBed(newValue)) {
                    onBedStatus = STATUS_ON_BED;
                } else {
                    onBedStatus = STATUS_NOT_ON_BED;
                }
            } else {
                onBedStatus = STATUS_NOT_INITIALIZED;
            }

            sendMessage(SENSOR_VALUE_AND_ONBED_STATUS, onBedStatus, newValue);
        }

        @Override
        public void onNewStatusSettings(float onBedValue, float notOnBedValue) {
            Log.i(DeviceService.class.getName(), "New values: " + onBedValue + ", " + notOnBedValue);
            sendMessage(STATUS_VALUES_SET, 0, null);
        }

        @Override
        public void onConnected() {
            sendMessage(DEVICE_CONNECTED, 0, null);
        }

        @Override
        public void onDisconnected() {
            sendMessage(DEVICE_DISCONNECTED, 0, null);
            stopSelf();
        }

        @Override
        public void deviceNotSupported() {
            sendMessage(DEVICE_NOT_SUPPORTED, 0, null);
            stopSelf();
        }

        @Override
        public void couldNotConnect() {
            sendMessage(CONNECTION_TIMEOUT, 0, null);
            stopSelf();
        }

        private void sendMessage(int what, int arg, Object obj) {
            for(Messenger client : listeners) {
                Message message = Message.obtain(null, what);
                message.arg1  = arg;
                message.obj = obj;
                message.replyTo = messenger;
                try {
                    client.send(message);
                } catch (RemoteException e) {
                    Log.e(DeviceService.class.getName(), e.getMessage(), e);
                }
            }
        }
    }

}
