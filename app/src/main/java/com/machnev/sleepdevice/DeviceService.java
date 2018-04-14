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

import java.util.HashMap;
import java.util.Map;

public class DeviceService extends Service {

    public static final int REQUEST_SENSORS_NOTIFICATIONS = 0;
    public static final int STOP_LISTEN_SENSOR_NOTIFICATIONS = 1;

    public static final int DEVICE_CONNECTED = 12;
    public static final int DEVICE_DISCONNECTED = 13;
    public static final int SENSOR_VALUE_AND_ONBED_STATUS = 15;

    public static final int STATUS_NOT_ON_BED = 0;
    public static final int STATUS_ON_BED = 1;

    private final Handler handler = new DeviceServiceHandler();
    private final Messenger messenger = new Messenger(handler);
    private final Map<String, BLEController> controllers = new HashMap<>();
    private final Map<Messenger, BLEController.IDeviceListener> valueListeners = new HashMap<>();
    private final Map<Messenger, BLEController.IDeviceListener> onBedStatusListeners = new HashMap<>();

    private BluetoothAdapter adapter;

    @Override
    public void onCreate() {

        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();

        Log.i(DeviceService.class.getName(), "onCreate: DeviceService");

        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        for(BLEController controller : controllers.values()) {
            for(BLEController.IDeviceListener listener : valueListeners.values()) {
                removeControllerListener(controller, listener);
            }
            for(BLEController.IDeviceListener listener : onBedStatusListeners.values()) {
                removeControllerListener(controller, listener);
            }
        }

        return super.onUnbind(intent);
    }

    private BLEController.IDeviceListener getOrCreateValueListener(Messenger client, BLEController controller) {
        BLEController.IDeviceListener valueListener = valueListeners.get(client);
        if(valueListener == null)
        {
            valueListener = new ClientNotificationValueListener(client, controller);
            valueListeners.put(client, valueListener);
        }
        return valueListener;
    }

    private BLEController getConnectingController(String deviceAddress) {
        BLEController controller = controllers.get(deviceAddress);
        if(controller == null) {
            controller = new BLEController(adapter, deviceAddress, DeviceService.this);
            controllers.put(deviceAddress, controller);
        }
        if(!controller.isConnecting()) {
            controller.connect();
        }
        return controller;
    }

    private void removeControllerListener(BLEController controller, BLEController.IDeviceListener listener) {
        controller.removeValueListener(listener);

        if(!controller.hasValueListeners()) {
            controller.disconnect();
        }
    }

    private class DeviceServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REQUEST_SENSORS_NOTIFICATIONS:
                    String deviceAddress = (String) msg.obj;

                    BLEController controller = getConnectingController(deviceAddress);
                    BLEController.IDeviceListener valueListener = getOrCreateValueListener(msg.replyTo, controller);
                    controller.addValueListener(valueListener);
                    break;
                case STOP_LISTEN_SENSOR_NOTIFICATIONS:
                    deviceAddress = (String) msg.obj;

                    controller = controllers.get(deviceAddress);
                    if(controller != null) {
                        valueListener = valueListeners.get(msg.replyTo);
                        if(valueListener != null) {
                            removeControllerListener(controller, valueListener);
                            valueListener.onDisconnected();
                        }
                    }
                    default:
                        break;
            }
            super.handleMessage(msg);
        }
    }

    private class ClientNotificationValueListener implements BLEController.IDeviceListener {
        private final Messenger client;
        private final BLEController controller;

        private ClientNotificationValueListener(Messenger client, BLEController controller) {
            this.client = client;
            this.controller = controller;
        }

        @Override
        public void onValueChanged(float newValue) {
            Message message = Message.obtain(null, SENSOR_VALUE_AND_ONBED_STATUS);

            message.obj = newValue;

            int onBedStatus;
            if(controller.isOnBed(newValue)) {
                onBedStatus = STATUS_ON_BED;
            } else {
                onBedStatus = STATUS_NOT_ON_BED;
            }
            message.arg1 = onBedStatus;

            message.replyTo = messenger;
            try {
                client.send(message);
            } catch (RemoteException e) {
                Log.e(DeviceService.class.getName(), e.getMessage(), e);
            }
        }

        @Override
        public void onConnected() {
            Message message = Message.obtain(null, DEVICE_CONNECTED);
            message.replyTo = messenger;
            try {
                client.send(message);
            } catch (RemoteException e) {
                Log.e(DeviceService.class.getName(), e.getMessage(), e);
            }
        }

        @Override
        public void onDisconnected() {
            Message message = Message.obtain(null, DEVICE_DISCONNECTED);
            message.replyTo = messenger;
            try {
                client.send(message);
            } catch (RemoteException e) {
                Log.e(DeviceService.class.getName(), e.getMessage(), e);
            }
        }
    }

}
