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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.machnev.sleepdevice.core.BLEController;
import com.machnev.sleepdevice.core.BLEDevice;
import com.machnev.sleepdevice.core.BLEScanner;

import java.util.Collection;

public class DeviceListActivity extends Activity {

    private final static int REQUEST_ENABLE_BT = 1;
    private ViewGroup rootView;

    private ProgressBar progressBar;
    private ListView listView;
    private DeviceLIstAdapter adapter;

    private final BLEController controller = new BLEController();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_device_list);

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
        listView.setOnItemClickListener(new ListViewItemClick());
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
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if(bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported", Toast.LENGTH_SHORT);
            finish();
            return;
        }
        controller.setBluetoothAdapter(bluetoothAdapter);

        if(bluetoothAdapter.isEnabled()) {
            scanBLE();
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void onEnableBluetoothResult() {
        scanBLE();
    }

    private void scanBLE()
    {
        BLEScanner scanner = controller.getScanner();
        scanner.scan(new BleScanningEndedCallback());
    }

    private class BleScanningEndedCallback implements BLEScanner.ScanEndedCallback {

        @Override
        public void onScanningResult(Collection<BluetoothDevice> devices) {
            adapter = new DeviceLIstAdapter(DeviceListActivity.this, devices);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listView.setAdapter(adapter);
                }
            });
        }

        @Override
        public void onException(final Exception e) {
            Log.e(DeviceListActivity.class.getName(), e.getMessage(), e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DeviceListActivity.this,"Exception: " + e.getMessage(), Toast.LENGTH_LONG);
                }
            });
        }
    }

    private class ListViewItemClick implements AdapterView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            BluetoothDevice device = (BluetoothDevice) adapter.getItem(position);
            controller.connectTo(device, DeviceListActivity.this);
            Toast.makeText(DeviceListActivity.this, "Connected to device " + device.getName(), Toast.LENGTH_SHORT);
        }
    }
}
