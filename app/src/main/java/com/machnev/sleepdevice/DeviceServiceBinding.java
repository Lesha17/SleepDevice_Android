package com.machnev.sleepdevice;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.machnev.sleepdevice.core.StatusSettingsData;

import java.util.Objects;

public class DeviceServiceBinding {
    private final DeviceServiceCallbacks callbacks;

    private Context context;
    private DeviceServiceConnection serviceConnection;
    private boolean isConnected;

    public DeviceServiceBinding(DeviceServiceCallbacks callbacks) {
        this.callbacks = callbacks;
    }


    public void connect(Context context, String deviceAddress) {
        this.context = context;

        Intent bindingIntent = new Intent(context, DeviceService.class);
        bindingIntent.putExtra(DeviceService.DEVICE_ADDRESS, deviceAddress);
        serviceConnection = new DeviceServiceConnection(deviceAddress);
        context.bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void setStatusSettings(float onBedValue, float notOnBedValue) {
        if(serviceConnection != null) {
            StatusSettingsData data = new StatusSettingsData(serviceConnection.deviceAddress, onBedValue, notOnBedValue);
            serviceConnection.sendRequest(DeviceService.SET_STATUS_SETTINGS, data);
        }
    }

    public void disconnect() {
        if(serviceConnection != null) {
            serviceConnection.sendRequest(DeviceService.STOP_LISTEN_SENSOR_NOTIFICATIONS);

            context.unbindService(serviceConnection);
        }
    }

    public static interface DeviceServiceCallbacks {
        public void onReceivedSensorValue(float value);

        public void onReceivedOnBedStatus(int status);

        public void onStatusSet();

        public void onDeviceConnected();

        public void onDeviceDisconnected();

        public void onDeviceNotSupported();
    }

    private class SensorValueHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

                    if(msg.what == DeviceService.SENSOR_VALUE_AND_ONBED_STATUS) {
                        Float value = (Float) msg.obj;
                        int status = msg.arg1;

                        callbacks.onReceivedSensorValue(value);
                        callbacks.onReceivedOnBedStatus(status);
                    }
                    if(msg.what == DeviceService.STATUS_VALUES_SET) {
                        callbacks.onStatusSet();
                    }
                    if(msg.what == DeviceService.DEVICE_CONNECTED) {
                        isConnected = true;
                        callbacks.onDeviceConnected();
                    }
                    if(msg.what == DeviceService.DEVICE_DISCONNECTED) {
                        isConnected = false;
                        callbacks.onDeviceDisconnected();
                    }
                    if (msg.what == DeviceService.DEVICE_NOT_SUPPORTED) {
                        callbacks.onDeviceNotSupported();
                        disconnect();
                    }

            super.handleMessage(msg);
        }
    }

    private class DeviceServiceConnection implements ServiceConnection {
        public final String deviceAddress;

        private Messenger deviceServiceMessenger;
        private Messenger source;

        private DeviceServiceConnection(String deviceAddress) {
            this.deviceAddress = deviceAddress;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            deviceServiceMessenger = new Messenger(service);
            source = new Messenger(new SensorValueHandler());

            sendRequest(DeviceService.REQUEST_SENSORS_NOTIFICATIONS);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if(isConnected) {
                isConnected = false;
                callbacks.onDeviceDisconnected();
            }
        }

        public void sendRequest(int what) {
            sendRequest(what, null);
        }

        public void sendRequest(int what, Object obj){
            Message message = Message.obtain(null, what, obj);
            message.replyTo = source;
            try {
                deviceServiceMessenger.send(message);
            } catch (RemoteException e) {
                Log.e(MainActivity.class.getName(), e.getMessage(), e);
            }
        }
    }
}
