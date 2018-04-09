package com.machnev.sleepdevice;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQUEST_STRANGE_BT_PERMISSIONS_CODE = 1;

    private Button fab;
    private TextView sensorValue;
    private TextView onBedStatus;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STRANGE_BT_PERMISSIONS_CODE) {
            startDeviceListActivity();
        }
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
        startActivity(intent);
    }
}
