package com.machnev.sleepdevice.core;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.util.Log;
import android.widget.Toast;

import com.machnev.sleepdevice.DeviceListActivity;

import java.util.ArrayList;
import java.util.Collection;

public class BLEScanner
{
    public static final long SLEEP_PERIOD = 10000;

    private final BluetoothAdapter bluetoothAdapter;

    public BLEScanner(BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public void scan(final ScanEndedCallback callback)
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                LeScanCallback  leCallback = new LeScanCallback();
                bluetoothAdapter.startLeScan(leCallback);

                try {
                    Thread.sleep(SLEEP_PERIOD);
                } catch (InterruptedException e) {
                    callback.onException(e);
                }

                bluetoothAdapter.stopLeScan(leCallback);
                callback.onScanningResult(leCallback.devices);
            }
        }).start();
    }

    public static interface ScanEndedCallback
    {
        public void onScanningResult(Collection<BluetoothDevice> devices);

        public void onException(Exception e);
    }

    private class LeScanCallback implements BluetoothAdapter.LeScanCallback {

        public final Collection<BluetoothDevice> devices = new ArrayList<>();

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            if(!devices.contains(device)) {
                devices.add(device);
            }
        }
    }
}
