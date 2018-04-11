package com.machnev.sleepdevice;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQUEST_STRANGE_BT_PERMISSIONS_CODE = 1;
    private static final int REQUEST_DEVICE_ADDR_CODE = 2;

    private Button fab;
    private TextView sensorValue;
    private TextView onBedStatus;

    private Messenger deviceService;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STRANGE_BT_PERMISSIONS_CODE) {
            startDeviceListActivity();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_DEVICE_ADDR_CODE) {
            String deviceAddr = data.getStringExtra(DeviceListActivity.DEVICE_ADDRESS_RESULT);
            bindToDeviceService(deviceAddr);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configureElements();
        checkBLEAndConnectToDevice();
    }

    private void configureElements()
    {
        configureFab();
        configureValue();
        configureStatus();
    }


    private void configureFab()
    {
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startStatusSettingActivity();
            }
        });
    }

    private void startStatusSettingActivity()
    {
        Intent intent = new Intent(this, StatusSettingsActivity.class);
        startActivity(intent);
    }

    private void configureValue()
    {
        sensorValue = (TextView) findViewById(R.id.sensor_value);
    }

    private void configureStatus()
    {
        onBedStatus = (TextView) findViewById(R.id.on_bed_status);
    }

    private void checkBLEAndConnectToDevice()
    {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions();
        }
        else {
            startDeviceListActivity();
        }
    }

    @TargetApi(23)
    private void requestPermissions() {
        requestPermissions(
                new String[] {"android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"},
                REQUEST_STRANGE_BT_PERMISSIONS_CODE);
    }

    private void startDeviceListActivity() {
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(intent, REQUEST_DEVICE_ADDR_CODE);
    }

    private void bindToDeviceService(String deviceAddr) {
        Intent intent = new Intent(this, DeviceService.class);
        bindService(intent, new DeviceServiceConnection(deviceAddr), Context.BIND_AUTO_CREATE);
    }

    private class SensorValueHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.i(MainActivity.class.getName(), "Received message: " + msg);
                    if(msg.what == DeviceService.SENSOR_VALUE) {
                        String value = (String) msg.obj;
                        sensorValue.setText(value);
                    }
                }
            });

            super.handleMessage(msg);
        }
    }

    private class DeviceServiceConnection implements ServiceConnection {
        private final String deviceAddress;

        private DeviceServiceConnection(String deviceAddress) {
            this.deviceAddress = deviceAddress;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            deviceService = new Messenger(service);
            Message message = Message.obtain(null, DeviceService.REQUEST_SENSORS_NOTIFICATION, deviceAddress);
            message.replyTo = new Messenger(new SensorValueHandler());
            try {
                deviceService.send(message);
            } catch (RemoteException e) {
                Log.e(MainActivity.class.getName(), e.getMessage(), e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            deviceService = null;
        }
    }
}
