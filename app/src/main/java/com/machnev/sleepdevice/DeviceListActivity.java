package com.machnev.sleepdevice;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.machnev.sleepdevice.core.BLEDevice;

public class DeviceListActivity extends Activity {

    private final static int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000;

    private ViewGroup rootView;

    private ProgressBar progressBar;
    private ListView listView;

    private DeviceLIstAdapter adapter;

    private BluetoothAdapter bluetoothAdapter;

    private boolean mScanning;
    private Handler mHandler;
    private BluetoothAdapter.LeScanCallback callback = new LeScanCallback();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_device_list);

        configureAdapter();
        configureRootView();

        configureBluetooth();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT) {
            onEnableBluetoothResult();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void configureAdapter()
    {
        adapter = new DeviceLIstAdapter(this);
    }

    private void configureRootView()
    {
        rootView = findViewById(R.id.device_list_root);

        configureProgressBar();
        configureListView();
    }

    private void configureListView()
    {
        listView = findViewById(R.id.listView);
        listView.setEmptyView(progressBar);
    }

    private void configureProgressBar()
    {
        // Create a progress bar to display while the list loads
        progressBar = new ProgressBar(this);
        progressBar.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        progressBar.setIndeterminate(true);
        rootView.addView(progressBar);
    }

    private void  configureBluetooth()
    {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if(bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported", Toast.LENGTH_SHORT);
            finish();
            return;
        }

        if(bluetoothAdapter.isEnabled()) {
            scanLeDevice(true);
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void onEnableBluetoothResult() {
        scanLeDevice(true);
    }

    private void scanLeDevice(final boolean enable) {
        if(mHandler == null) {
            mHandler = new Handler(){
                public void handleMessage(Message msg){
                    Log.i(getClass().getName(), msg.toString());

                    super.handleMessage(msg);
                }
            };
        }

        if (enable) {
            bluetoothAdapter.startLeScan(callback);
            mScanning = true;
            Log.i(getClass().getName(), "Started scanning");

            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanLeDevice(false);
                    listView.setAdapter(adapter);
                }
            }, SCAN_PERIOD);

        } else {
            mScanning = false;
            bluetoothAdapter.stopLeScan(callback);
        }
    }

    private class LeScanCallback implements BluetoothAdapter.LeScanCallback {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(device != null) {
                        BLEDevice bleDevice = new BLEDevice(device.getName(), device.getAddress());
                        boolean wasAdded = adapter.addDevice(bleDevice);
                        if(wasAdded) {
                            Log.i(DeviceListActivity.class.getName(), "Found device: " + (device != null ? device.getName() : "null"));
                        }
                    }
                }
            });
        }
    }
}
