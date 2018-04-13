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
import java.util.function.Consumer;

public class DeviceService extends Service {

    public static final int REQUEST_SENSORS_NOTIFICATIONS = 0;
    public static final int STOP_LISTEN_SENSOR_NOTIFICATIONS = 1;
    public static final int REQUEST_ONBED_NOTIFICATIONS = 2;
    public static final int STOP_LISTEN_ONBED_NOTIFICATIONS = 3;
    public static final int ON_BED_STATUS = 14;
    public static final int SENSOR_VALUE = 15;

    public static final int STATUS_NOT_ON_BED = 0;
    public static final int STATUS_ON_BED = 1;

    private final Handler handler = new DeviceServiceHandler();
    private final Messenger messenger = new Messenger(handler);
    private final Map<String, BLEController> controllers = new HashMap<>();
    private final Map<Messenger, BLEController.IControllerListener> valueListeners = new HashMap<>();
    private final Map<Messenger, BLEController.IControllerListener> onBedStatusListeners = new HashMap<>();

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
            for(BLEController.IControllerListener listener : valueListeners.values()) {
                removeControllerListener(controller, listener);
            }
            for(BLEController.IControllerListener listener : onBedStatusListeners.values()) {
                removeControllerListener(controller, listener);
            }
        }

        return super.onUnbind(intent);
    }

    private BLEController.IControllerListener getOrCreateValueListener(Messenger client) {
        BLEController.IControllerListener valueListener = valueListeners.get(client);
        if(valueListener == null)
        {
            valueListener = new ClientNotificationValueListener(client);
            valueListeners.put(client, valueListener);
        }
        return valueListener;
    }

    private BLEController.IControllerListener getOrCreateOnBedStatusListener(Messenger client, BLEController controller) {
        BLEController.IControllerListener onBedStatusListener = onBedStatusListeners.get(client);
        if(onBedStatusListener == null)
        {
            onBedStatusListener = new ClientNotificationOnBedStatusLIstener(client,controller);
            onBedStatusListeners.put(client, onBedStatusListener);
        }
        return onBedStatusListener;
    }

    private BLEController getConnectedController(String deviceAddress) {
        BLEController controller = controllers.get(deviceAddress);
        if(controller == null) {
            controller = new BLEController(adapter, deviceAddress, DeviceService.this);
            controllers.put(deviceAddress, controller);
        }
        if(!controller.isConnected()) {
            controller.connect();
        }
        return controller;
    }

    private void removeControllerListener(BLEController controller, BLEController.IControllerListener listener) {
        controller.removeListener(listener);

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

                    BLEController.IControllerListener valueListener = getOrCreateValueListener(msg.replyTo);
                    BLEController controller = getConnectedController(deviceAddress);
                    controller.addListener(valueListener);
                    break;
                case STOP_LISTEN_SENSOR_NOTIFICATIONS:
                    deviceAddress = (String) msg.obj;

                    controller = controllers.get(deviceAddress);
                    if(controller != null) {
                        valueListener = valueListeners.get(msg.replyTo);
                        if(valueListener != null) {
                            removeControllerListener(controller, valueListener);
                        }
                    }
                case REQUEST_ONBED_NOTIFICATIONS:
                    deviceAddress = (String) msg.obj;

                    controller = getConnectedController(deviceAddress);
                    BLEController.IControllerListener onBedStatusListener = getOrCreateOnBedStatusListener(msg.replyTo, controller);
                    controller.addListener(onBedStatusListener);
                    break;
                case STOP_LISTEN_ONBED_NOTIFICATIONS:
                    deviceAddress = (String) msg.obj;

                    controller = controllers.get(deviceAddress);
                    if(controller != null) {
                        onBedStatusListener = onBedStatusListeners.get(msg.replyTo);
                        if(onBedStatusListener != null) {
                            removeControllerListener(controller, onBedStatusListener);
                        }
                    }
                    break;
                    default:
                        break;
            }
            super.handleMessage(msg);
        }
    }

    private class ClientNotificationValueListener implements BLEController.IControllerListener {
        private final Messenger client;

        private ClientNotificationValueListener(Messenger client) {
            this.client = client;
        }

        @Override
        public void onValueChanged(float newValue) {
            Message message = Message.obtain(null, SENSOR_VALUE, newValue);
            message.replyTo = messenger;
            try {
                client.send(message);
            } catch (RemoteException e) {
                Log.e(DeviceService.class.getName(), e.getMessage(), e);
            }

        }
    }

    private class ClientNotificationOnBedStatusLIstener implements BLEController.IControllerListener {
        private final Messenger client;
        private final BLEController controller;

        private ClientNotificationOnBedStatusLIstener(Messenger client, BLEController controller) {
            this.client = client;
            this.controller = controller;
        }

        @Override
        public void onValueChanged(float newValue) {
            int onBedStatus;
            if(controller.isOnBed(newValue)) {
                onBedStatus = STATUS_ON_BED;
            } else {
                onBedStatus = STATUS_NOT_ON_BED;
            }

            Message message = Message.obtain(null, ON_BED_STATUS);
            message.arg1 = onBedStatus;
            message.replyTo = messenger;
            try {
                client.send(message);
            } catch (RemoteException e) {
                Log.e(DeviceService.class.getName(), e.getMessage(), e);
            }
        }
    }

}
