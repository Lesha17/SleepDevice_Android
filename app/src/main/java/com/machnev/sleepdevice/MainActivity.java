package com.machnev.sleepdevice;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
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

    private static final String DEVICE_ADDRESS_KEY = "com.machnev.sleepdevice.MainActivity.DEVICE_ADDRESS_KEY";

    private Button fab;
    private Button chooseAnotherDeviceButton;
    private TextView sensorValue;
    private TextView onBedStatus;

    private String deviceAddress;

    private boolean permissionGranted;
    private DeviceServiceConnection serviceConnection;
    private boolean isBound;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STRANGE_BT_PERMISSIONS_CODE) {
            boolean permissionGranted = true;
            for(int result : grantResults) {
                permissionGranted &= result == PackageManager.PERMISSION_GRANTED;
            }
            this.permissionGranted = permissionGranted;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        configureElements();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(MainActivity.class.getName(), "OnResume");
        requestPermissionsThenObtainAddressAndBindToService();
    }

    @Override
    protected void onDestroy() {
        if(isBound) {
            stopListenNotifications();
        }

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if(deviceAddress != null) {
            outState.putString(DEVICE_ADDRESS_KEY, deviceAddress);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        deviceAddress = savedInstanceState.getString(DEVICE_ADDRESS_KEY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_DEVICE_ADDR_CODE) {
            deviceAddress = data.getStringExtra(DeviceListActivity.DEVICE_ADDRESS_RESULT);
            Log.i(MainActivity.class.getName(), "Obtained device addr: " + deviceAddress);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void configureElements()
    {
        configureFab();
        chooseAnotherDeviceButton = findViewById(R.id.choose_another_device);
        chooseAnotherDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deviceAddress = null;
                stopListenNotifications();

                obtainAddressAndBindToService();
            }
        });
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

    private void requestPermissionsThenObtainAddressAndBindToService() {
        if (Build.VERSION.SDK_INT >= 23 && !permissionGranted) {
            requestBLEPermissions();
        } else {
            obtainAddressAndBindToService();
        }
    }

    @TargetApi(23)
    private void requestBLEPermissions()
    {
        requestPermissions(
                new String[] {"android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"},
                REQUEST_STRANGE_BT_PERMISSIONS_CODE);
    }

    private void obtainAddressAndBindToService()
    {
        if(deviceAddress == null) {
            Log.i(MainActivity.class.getName(), "Obtaining device address...");
            obtainDeviceAddress();
            return;
        }

        if(!isBound) {
            Log.i(MainActivity.class.getName(), "Binding to service...");
            bindToDeviceService();
        }

    }

    private void obtainDeviceAddress() {
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(intent, REQUEST_DEVICE_ADDR_CODE);
    }

    private void bindToDeviceService() {
        Intent intent = new Intent(this, DeviceService.class);
        serviceConnection = new DeviceServiceConnection(deviceAddress);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void stopListenNotifications() {
        serviceConnection.sendRequest(DeviceService.STOP_LISTEN_SENSOR_NOTIFICATIONS);
        serviceConnection.sendRequest(DeviceService.STOP_LISTEN_ONBED_NOTIFICATIONS);
        unbindService(serviceConnection);
    }

    private class SensorValueHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(msg.what == DeviceService.SENSOR_VALUE) {
                        Float value = (Float) msg.obj;
                        sensorValue.setText(value.toString());
                    }
                    if(msg.what == DeviceService.ON_BED_STATUS) {
                        int status = msg.arg1;
                        if(status == DeviceService.STATUS_ON_BED) {
                            onBedStatus.setText("On bed");
                        } else if (status == DeviceService.STATUS_NOT_ON_BED) {
                            onBedStatus.setText("Not on bed");
                        } else {
                            onBedStatus.setText("Undefined");
                        }
                    }
                }
            });

            super.handleMessage(msg);
        }
    }

    private class DeviceServiceConnection implements ServiceConnection {
        private final String deviceAddress;

        private Messenger deviceServiceMessenger;
        private Messenger source;

        private DeviceServiceConnection(String deviceAddress) {
            this.deviceAddress = deviceAddress;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            isBound = true;
            deviceServiceMessenger = new Messenger(service);
            source = new Messenger(new SensorValueHandler());

            sendRequest(DeviceService.REQUEST_SENSORS_NOTIFICATIONS);
  //          sendRequest(DeviceService.REQUEST_ONBED_NOTIFICATIONS);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            serviceConnection = null;
        }

        public void sendRequest(int what){
            Message message = Message.obtain(null, what, deviceAddress);
            message.replyTo = source;
            try {
                deviceServiceMessenger.send(message);
            } catch (RemoteException e) {
                Log.e(MainActivity.class.getName(), e.getMessage(), e);
            }
        }
    }
}
