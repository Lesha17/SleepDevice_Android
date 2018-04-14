package com.machnev.sleepdevice;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.machnev.sleepdevice.core.BLEScanner;

import java.util.Collection;

public class DeviceListActivity extends Activity {

    public static final String DEVICE_ADDRESS_RESULT = "com.machnev.sleepdevice.DeviceListActivity.DEVICE_ADDRESS_RESULT";
    public static final String DEVICE_NAME_RESULT = "com.machnev.sleepdevice.DeviceListActivity.DEVICE_NAME_RESULT";

    private ViewGroup rootView;

    private ListView listView;
    private DeviceLIstAdapter adapter;

    private ProgressDialog scanningForDevicesDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_device_list);

        configureRootView();
    }

    @Override
    protected void onStart() {
        super.onStart();

        scanBLE();
    }

    private void configureRootView()
    {
        rootView = findViewById(R.id.device_list_root);

        configureListView();
    }

    private void configureListView()
    {
        listView = findViewById(R.id.listView);
        listView.setOnItemClickListener(new ListViewItemClick());
    }

    private void scanBLE()
    {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        BLEScanner scanner = new BLEScanner(bluetoothAdapter);

        scanningForDevicesDialog = ProgressDialog.show(this, "Scanning for devices..",
                "", true,
                false);

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
                    scanningForDevicesDialog.dismiss();
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

            Intent intent = new Intent();
            intent.putExtra(DEVICE_ADDRESS_RESULT, device.getAddress());
            intent.putExtra(DEVICE_NAME_RESULT, device.getName());
            setResult(RESULT_OK, intent);
            finish();
        }
    }
}
