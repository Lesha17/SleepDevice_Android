package com.machnev.sleepdevice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class StatusSettingsActivity extends Activity {

    public static final String DEVICE_ADDRESS = "com.machnev.sleepdevice.StatusSettingsActivity.DEVICE_ADDRESS";

    public static final String ON_BED = "com.machnev.sleepdevice.StatusSettingsActivity.ON_BED";
    public static final String NOT_ON_BED = "com.machnev.sleepdevice.StatusSettingsActivity.NOT_ON_BED";

    private TextView currentValueText;
    private TextView messageText;
    private Button okButton;

    private String deviceAddress;
    private DeviceServiceBinding serviceBinding;

    private float currentValue;
    private Float notOnBedValue;
    private Float onBedValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status_settings);

        configureElements();

        serviceBinding = new DeviceServiceBinding(new DeviceServiceCallbacks());
        if(savedInstanceState != null) {
            deviceAddress = savedInstanceState.getString(DEVICE_ADDRESS);
        } else  {
            Intent startingIntent = getIntent();
            deviceAddress = startingIntent.getStringExtra(DEVICE_ADDRESS);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        serviceBinding.connect(this, deviceAddress);
    }

    @Override
    protected void onStop() {
        serviceBinding.disconnect();

        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(DEVICE_ADDRESS, deviceAddress);

        super.onSaveInstanceState(outState);
    }

    private void configureElements()
    {
        configureCurrentValueText();
        configureMessageText();
        configureOkButton();
    }

    private void configureCurrentValueText() {
        currentValueText = findViewById(R.id.current_value);
    }

    private void configureMessageText()
    {
        messageText = findViewById(R.id.message_text);
    }

    private void configureOkButton()
    {
        okButton = findViewById(R.id.ok_button);
        okButton.setEnabled(false);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(notOnBedValue == null) {
                    notOnBedValue = currentValue;
                    messageText.setText("Lie on bed and click OK");
                } else if (onBedValue == null) {
                    onBedValue = currentValue;

                    serviceBinding.setStatusSettings(onBedValue, notOnBedValue);

                    Intent intent = new Intent();
                    intent.putExtra(ON_BED, onBedValue);
                    intent.putExtra(NOT_ON_BED, notOnBedValue);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        });
    }

    private class DeviceServiceCallbacks implements DeviceServiceBinding.DeviceServiceCallbacks {

        @Override
        public void onReceivedSensorValue(float value) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    currentValue = value;
                    currentValueText.setText(String.valueOf(currentValue));
                }
            });
        }

        @Override
        public void onReceivedOnBedStatus(int status) {

        }

        @Override
        public void onStatusSet() {
            Toast.makeText(StatusSettingsActivity.this, "Status values set", Toast.LENGTH_SHORT);
        }

        @Override
        public void onDeviceConnected() {
            okButton.setEnabled(true);
            messageText.setText("Stand up from bed and click OK");
        }

        @Override
        public void onDeviceDisconnected() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(StatusSettingsActivity.this, "Device disconnected", Toast.LENGTH_SHORT);
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });
        }

        @Override
        public void onDeviceNotSupported() {
            CommonMessages.deviceIsNotSupported(StatusSettingsActivity.this);
            setResult(RESULT_CANCELED);
            finish();
        }

        @Override
        public void onConnectionTimeout() {
            CommonMessages.connectionTimeout(StatusSettingsActivity.this);
            setResult(RESULT_CANCELED);
            finish();
        }
    }
}
