package com.machnev.sleepdevice;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.machnev.sleepdevice.core.BLEDeviceViewModel;

public class MainActivity extends Activity {

    private static final int REQUEST_STRANGE_BT_PERMISSIONS_CODE = 1;
    private final static int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_DEVICE_ADDR_CODE = 3;
    private static final int REQUEST_STATUS_SETTINGS = 4;

    private static final String DEVICE_ADDRESS_KEY = "com.machnev.sleepdevice.MainActivity.DEVICE_ADDRESS_KEY";
    private static final String DEVICE_NAME_KEY = "com.machnev.sleepdevice.MainActivity.DEVICE_NAME_KEY";
    private static final String SHOULD_BE_CONNECTED_KEY = "com.machnev.sleepdevice.MainActivity.SHOULD_BE_CONNECTED_KEY";

    private TextView connectionStatus;
    private TextView sensorValue;
    private TextView onBedStatus;

    private Button connectToThisDeviceButton;
    private Button connectToOtherDeviceButton;
    private Button disconnectButton;
    private Button configureOnBedButton;

    private boolean permissionGranted;
    private boolean bluetoothEnabled;
    private BLEDeviceViewModel device;
    private boolean shouldBeConnected;
    private boolean isConnected;

    private ProgressDialog connectingToDeviceDialog;

    private DeviceServiceBinding serviceBinding;

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
    protected void onStart() {
        super.onStart();

        if(shouldBeConnected && !isConnected && device != null) {
            connectToDevice(device.address);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        configureBluetooth();

        if(isConnected) {
            setConnectedState();
        } else if (device != null) {
            setNotConnectedStateWithSavedDevice();
        } else {
            setNotConnectedState();
        }
    }

    @Override
    protected void onStop() {
        disconnectDevice();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if(device != null) {
            outState.putString(DEVICE_ADDRESS_KEY, device.address);
            outState.putString(DEVICE_NAME_KEY, device.name);
        }
        outState.putBoolean(SHOULD_BE_CONNECTED_KEY, shouldBeConnected);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        String deviceAddress = savedInstanceState.getString(DEVICE_ADDRESS_KEY);
        String deviceName = savedInstanceState.getString(DEVICE_NAME_KEY);
        device = new BLEDeviceViewModel(deviceAddress, deviceName);

        shouldBeConnected = savedInstanceState.getBoolean(SHOULD_BE_CONNECTED_KEY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT) {
            if(resultCode == RESULT_OK) {
                bluetoothEnabled = true;
                Log.i(MainActivity.class.getName(), "Bluetooth just has been enabled.");
            }
        }
        if(requestCode == REQUEST_DEVICE_ADDR_CODE) {
            deviceAddressActivityResultCallback(resultCode, data);
        }
        if(requestCode == REQUEST_STATUS_SETTINGS) {
            statusSettingActivityResultCallback(resultCode, data);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void configureElements()
    {
        configureConnectionStatus();
        configureValue();
        configureOnBedStatus();

        configureConnectToThisDeviceButton();
        configureConnectToOtherDeviceButton();
        configureDisconnectButton();
        configureOnBedButton();
    }

    private void configureConnectionStatus()
    {
        connectionStatus = findViewById(R.id.connection_status);
    }

    private void configureValue()
    {
        sensorValue = (TextView) findViewById(R.id.sensor_value);
    }

    private void configureOnBedStatus()
    {
        onBedStatus = (TextView) findViewById(R.id.on_bed_status);
    }

    private void configureConnectToThisDeviceButton() {
        connectToThisDeviceButton = findViewById(R.id.connect_to_this_device);
        connectToThisDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shouldBeConnected = true;
                connectToDevice(device.address);
            }
        });
    }

    private void configureConnectToOtherDeviceButton() {
        connectToOtherDeviceButton = findViewById(R.id.connect_to_other_device);
        connectToOtherDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                obtainDeviceAddressAndConnect();
            }
        });
    }

    private void configureDisconnectButton() {
        disconnectButton = findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shouldBeConnected = false;
                disconnectDevice();
            }
        });
    }

    private void configureOnBedButton()
    {
        configureOnBedButton = findViewById(R.id.configure_on_bed);
        configureOnBedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startStatusSettingActivity();
            }
        });
    }

    private void setNotConnectedState(){
        isConnected = false;
        connectionStatus.setText("Not connected");
        sensorValue.setVisibility(View.GONE);
        onBedStatus.setVisibility(View.GONE);
        connectToThisDeviceButton.setVisibility(View.GONE);
        connectToOtherDeviceButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        configureOnBedButton.setEnabled(false);
    }

    private void setNotConnectedStateWithSavedDevice() {
        setNotConnectedState();
        connectToThisDeviceButton.setText("Connect to " + device.name);
        connectToThisDeviceButton.setVisibility(View.VISIBLE);
        connectToThisDeviceButton.setEnabled(true);
    }

    private void setConnectingState() {
        connectionStatus.setText("Connecting to " + device.name + "..");
        if(connectingToDeviceDialog == null) {
            connectingToDeviceDialog = ProgressDialog
                    .show(this, "Connecting to device..",
                            "Connecting to device " + device.name, true,
                            false);
        }
        isConnected = false;
    }

    private void setConnectedState() {
        isConnected = true;
        connectionStatus.setText("Connected to " + device.name);
        if(connectingToDeviceDialog != null) {
            connectingToDeviceDialog.dismiss();
            connectingToDeviceDialog = null;
        }
        sensorValue.setVisibility(View.VISIBLE);
        onBedStatus.setVisibility(View.VISIBLE);
        connectToThisDeviceButton.setEnabled(false);
        connectToOtherDeviceButton.setEnabled(true);
        disconnectButton.setEnabled(true);
        configureOnBedButton.setEnabled(true);
    }

    private void configureBluetooth() {
        if(!permissionGranted) {
            if (Build.VERSION.SDK_INT >= 23 ) {
                requestBLEPermissions();
                return;
            } else {
                permissionGranted = true;
            }
        }

        if (!bluetoothEnabled){
            requestEnableBluetooth();
            return;
        }
    }

    @TargetApi(23)
    private void requestBLEPermissions()
    {
        requestPermissions(
                new String[] {"android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"},
                REQUEST_STRANGE_BT_PERMISSIONS_CODE);
    }

    private void requestEnableBluetooth()
    {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if(bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported", Toast.LENGTH_SHORT);
            finish();
            return;
        }

        if(bluetoothAdapter.isEnabled()) {
            bluetoothEnabled = true;
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void obtainDeviceAddressAndConnect() {
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(intent, REQUEST_DEVICE_ADDR_CODE);
    }

    private void deviceAddressActivityResultCallback(int resultCode, Intent data) {
        if(resultCode == RESULT_OK) {
            shouldBeConnected = true;
            String deviceAddress = data.getStringExtra(DeviceListActivity.DEVICE_ADDRESS_RESULT);
            String deviceName = data.getStringExtra(DeviceListActivity.DEVICE_NAME_RESULT);
            device = new BLEDeviceViewModel(deviceAddress, deviceName);
            Log.i(MainActivity.class.getName(), "Obtained device addr: " + device.address);
            connectToDevice(device.address);
        }
    }

    private void startStatusSettingActivity()
    {
        Intent intent = new Intent(this, StatusSettingsActivity.class);
        intent.putExtra(StatusSettingsActivity.DEVICE_ADDRESS, device.address);
        startActivityForResult(intent, REQUEST_STATUS_SETTINGS);
    }

    private void statusSettingActivityResultCallback(int resultCode, Intent data) {
        if(resultCode == RESULT_OK) {
            float onBed = data.getFloatExtra(StatusSettingsActivity.ON_BED, 0.0f);
            float notOnBed = data.getFloatExtra(StatusSettingsActivity.NOT_ON_BED, 0.0f);
        }
    }

    private void connectToDevice(String deviceAddress) {
        setConnectingState();

        if(serviceBinding == null) {
            serviceBinding = new DeviceServiceBinding(new DeviceServiceCallbacks());
        }
        serviceBinding.connect(this, deviceAddress);
    }

    private void disconnectDevice() {
        if(serviceBinding != null) {
            serviceBinding.disconnect();
        }
    }

    private class DeviceServiceCallbacks implements DeviceServiceBinding.DeviceServiceCallbacks {

        @Override
        public void onReceivedSensorValue(float value) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    sensorValue.setText(String.valueOf(value));
                }
            });
        }

        @Override
        public void onReceivedOnBedStatus(int status) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(status == DeviceService.STATUS_ON_BED) {
                        onBedStatus.setText("On bed");
                    } else if (status == DeviceService.STATUS_NOT_ON_BED) {
                        onBedStatus.setText("Not on bed");
                    } else if (status == DeviceService.STATUS_NOT_INITIALIZED) {
                        onBedStatus.setText("Please configure on bed / not on bed values to see if human is on bed or not on bed");
                    } else {
                        Log.i(MainActivity.class.getName(), "So strange on bed status: " + status);
                        onBedStatus.setText("Undefined");
                    }
                }
            });
        }

        @Override
        public void onStatusSet() {

        }

        @Override
        public void onDeviceConnected() {
            setConnectedState();
        }

        @Override
        public void onDeviceDisconnected() {
            setNotConnectedStateWithSavedDevice();
        }

        @Override
        public void onDeviceNotSupported() {
            Toast.makeText(MainActivity.this, "Device " + device.name + " is not supproted.", Toast.LENGTH_SHORT);
        }
    }
}
