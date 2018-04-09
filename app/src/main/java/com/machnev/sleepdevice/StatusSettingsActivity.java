package com.machnev.sleepdevice;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class StatusSettingsActivity extends Activity {

    private TextView messageText;
    private Button okButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status_settings);

        configureElements();
    }


    private void configureElements()
    {
        configureMessageText();
        configureOkButton();
    }

    private void configureMessageText()
    {
        messageText = findViewById(R.id.message_text);
    }

    private void configureOkButton()
    {
        okButton = findViewById(R.id.ok_button);
    }
}
