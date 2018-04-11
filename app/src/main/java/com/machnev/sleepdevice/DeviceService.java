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

    public static final int REQUEST_SENSORS_NOTIFICATION = 0;
    public static final int STOP_LISTEN_NOTIFICATIONS = 1;
    public static final int SENSOR_VALUE = 2;

    private final Handler handler = new DeviceServiceHandler();
    private final Messenger messenger = new Messenger(handler);
    private final Map<String, BLEController> contollers = new HashMap<>();
    private final Map<Messenger, BLEController.IValueListener> valueListeners = new HashMap<>();

    private BluetoothAdapter adapter;

    @Override
    public void onCreate() {

        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();

        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private class DeviceServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REQUEST_SENSORS_NOTIFICATION:
                    String deviceAddress = (String) msg.obj;

                    BLEController.IValueListener valueListener = getOrCreateValueListener(msg.replyTo);
                    BLEController controller = getConnectedController(deviceAddress);
                    controller.addValueListener(valueListener);
                    break;
                case STOP_LISTEN_NOTIFICATIONS:
                    deviceAddress = (String) msg.obj;

                    controller = contollers.get(deviceAddress);
                    if(controller != null) {
                        valueListener = valueListeners.get(msg.replyTo);
                        if(valueListener != null) {
                            controller.removecValueListener(valueListener);
                        }

                        if(!controller.hasValueListeners()) {
                            controller.disconnect();
                        }
                    }
                    break;
                    default:
                        break;
            }
            super.handleMessage(msg);
        }

        private BLEController.IValueListener getOrCreateValueListener(Messenger client) {
            BLEController.IValueListener valueListener = valueListeners.get(client);
            if(valueListener == null)
            {
                valueListener = new ClientNotificationValueListener(client);
                valueListeners.put(client, valueListener);
            }
            return valueListener;
        }

        private BLEController getConnectedController(String deviceAddress) {
            BLEController controller = contollers.get(deviceAddress);
            if(controller == null) {
                controller = new BLEController(adapter, deviceAddress, DeviceService.this);
                contollers.put(deviceAddress, controller);
            }
            if(!controller.isConnected()) {
                controller.connect();
            }
            return controller;
        }

        private class ClientNotificationValueListener implements BLEController.IValueListener {
            private final Messenger client;

            private ClientNotificationValueListener(Messenger client) {
                this.client = client;
            }

            @Override
            public void onValueChanged(String newValue) {
                Message message = Message.obtain(null, SENSOR_VALUE, newValue);
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
