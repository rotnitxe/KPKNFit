package com.yourprime.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;
import com.yourprime.app.localai.LocalAiPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LocalAiPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
