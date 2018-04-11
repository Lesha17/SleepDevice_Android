package com.machnev.sleepdevice;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
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

import com.machnev.sleepdevice.core.BLEController;
import com.machnev.sleepdevice.core.BLEScanner;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DeviceListActivity extends Activity {

    private final static int REQUEST_ENABLE_BT = 1;
    private ViewGroup rootView;

    private ProgressBar progressBar;
    private ListView listView;
    private DeviceLIstAdapter adapter;

    private BluetoothAdapter bluetoothAdapter;

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
        bluetoothAdapter = bluetoothManager.getAdapter();
        if(bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported", Toast.LENGTH_SHORT);
            finish();
            return;
        }

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
        BLEScanner scanner = new BLEScanner(bluetoothAdapter);
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

    private void log(String message) {
        Log.i(DeviceListActivity.class.getName(), message);
    }

    private class ListViewItemClick implements AdapterView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            BluetoothDevice device = (BluetoothDevice) adapter.getItem(position);
            BLEController controller = new BLEController(bluetoothAdapter, device.getAddress());
            controller.connect(DeviceListActivity.this, new BluetoothGattCallback() {
                private Map<UUID, BluetoothGattCharacteristic> characteristicsToRead = new HashMap<>();
                private Map<UUID, BluetoothGattDescriptor> descriptorsToRead = new HashMap<>();

                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if(newState == BluetoothGatt.STATE_CONNECTED) {
                        log("Connected to " + gatt.getDevice().getName());
                        log("Discover services: " + gatt.discoverServices());
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    log("Total services: " + gatt.getServices().size());
                    for(BluetoothGattService service : gatt.getServices()) {
                        log("\tService: " + service.getUuid() + ". Total chars: " + service.getCharacteristics().size());
                        for(BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            log("\t\tCharacteristic: " + characteristic.getUuid() + ".  Total descriptiors: " + characteristic.getDescriptors().size());
                            characteristicsToRead.put(characteristic.getUuid(), characteristic);
                            for(BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                                log("\t\t\tDescriptor: " + descriptor.getUuid());
                                descriptorsToRead.put(characteristic.getUuid(), descriptor);
                            }
                        }
                    }

                    BluetoothGattCharacteristic tx = characteristicsToRead.get(UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"));
                    log("Set characteristic notification: " + gatt.setCharacteristicNotification(tx, true));
                    BluetoothGattDescriptor descriptor = tx.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);

                    super.onServicesDiscovered(gatt, status);
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    String value = new String(characteristic.getValue());
                    log("CharacteristicR " + characteristic.getUuid() + " value: " + value);

                    super.onCharacteristicRead(gatt, characteristic, status);
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    String value = new String(characteristic.getValue());
                    log("Characteristic " + characteristic.getUuid() + " changed. New value: " + value);


                    super.onCharacteristicChanged(gatt, characteristic);
                }

                @Override
                public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    String value = new String(descriptor.getValue());
                    log("Descriptor " + descriptor.getUuid() + " value: " + value);

                    super.onDescriptorRead(gatt, descriptor, status);
                }
            });
            Toast.makeText(DeviceListActivity.this, "Connected to device " + device.getName(), Toast.LENGTH_SHORT);
        }
    }
}
