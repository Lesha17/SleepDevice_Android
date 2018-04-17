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

import com.machnev.sleepdevice.core.StatusSettingsData;

public class DeviceServiceBinding {
    private final DeviceServiceCallbacks callbacks;

    private Context context;
    private DeviceServiceConnection serviceConnection;
    private boolean isBound;

    private boolean notifiedConnected;

    public DeviceServiceBinding(DeviceServiceCallbacks callbacks) {
        this.callbacks = callbacks;
    }


    public void connect(Context context, String deviceAddress) {
        this.context = context;

        Intent startIntet = new Intent(context, DeviceService.class);
        startIntet.putExtra(DeviceService.DEVICE_ADDRESS, deviceAddress);
        context.startService(startIntet);

        if(!isBound) {
            Intent bindingIntent = new Intent(context, DeviceService.class);
            bindingIntent.putExtra(DeviceService.DEVICE_ADDRESS, deviceAddress);

            serviceConnection = new DeviceServiceConnection(deviceAddress);
            context.bindService(bindingIntent, serviceConnection, 0);
            isBound = true;
            notifiedConnected = false;
        }
    }

    public void setStatusSettings(float onBedValue, float notOnBedValue) {
        if(serviceConnection != null) {
            StatusSettingsData data = new StatusSettingsData(serviceConnection.deviceAddress, onBedValue, notOnBedValue);
            serviceConnection.sendRequest(DeviceService.SET_STATUS_SETTINGS, data);
        }
    }

    public void disconnect(boolean isFinishing) {
        if(serviceConnection != null) {
            if(isBound) {
                serviceConnection.sendRequest(DeviceService.STOP_LISTEN_SENSOR_NOTIFICATIONS);
                context.unbindService(serviceConnection);
                isBound = false;

                if (isFinishing) {
                    serviceConnection = null;
                    context.stopService(new Intent(context, DeviceService.class));
                    callbacks.onDeviceDisconnected();
                }
            }
        }
    }

    public static interface DeviceServiceCallbacks {
        public void onReceivedSensorValue(float value);

        public void onReceivedOnBedStatus(int status);

        public void onStatusSet();

        public void onDeviceConnected();

        public void onDeviceDisconnected();

        public void onDeviceNotSupported();

        public void onConnectionTimeout();
    }

    private class SensorValueHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

                    if(msg.what == DeviceService.SENSOR_VALUE_AND_ONBED_STATUS) {
                        Float value = (Float) msg.obj;
                        int status = msg.arg1;

                        if(!notifiedConnected) {
                            callbacks.onDeviceConnected();
                            notifiedConnected = true;
                        }
                        callbacks.onReceivedSensorValue(value);
                        callbacks.onReceivedOnBedStatus(status);
                    }
                    if(msg.what == DeviceService.STATUS_VALUES_SET) {
                        callbacks.onStatusSet();
                    }
                    if(msg.what == DeviceService.DEVICE_CONNECTED) {
                        callbacks.onDeviceConnected();
                        notifiedConnected = true;
                    }
                    if(msg.what == DeviceService.DEVICE_DISCONNECTED) {
                        callbacks.onDeviceDisconnected();
                        notifiedConnected = false;
                        disconnect(false);
                    }
                    if (msg.what == DeviceService.DEVICE_NOT_SUPPORTED) {
                        callbacks.onDeviceNotSupported();
                        disconnect(false);
                    }
                    if(msg.what == DeviceService.CONNECTION_TIMEOUT) {
                        callbacks.onConnectionTimeout();
                        disconnect(false);
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
            isBound = true;

            sendRequest(DeviceService.REQUEST_SENSORS_NOTIFICATIONS);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            callbacks.onDeviceDisconnected();
            isBound = false;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.i(DeviceServiceBinding.class.getName(), "Binding " + name + " died");
            onServiceDisconnected(name);
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
