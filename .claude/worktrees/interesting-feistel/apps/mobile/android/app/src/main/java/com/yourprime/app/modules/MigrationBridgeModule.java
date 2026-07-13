package com.yourprime.app.modules;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

public class MigrationBridgeModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "KPKNMigrationBridge";
    private static final String SNAPSHOT_RELATIVE_PATH = "migration/snapshot-v1.json";
    private static final String PREFS_NAME = "kpkn_mobile_bridge";
    private static final String PREF_MIGRATION_COMPLETE = "migration_complete";

    public MigrationBridgeModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void readMigrationSnapshot(Promise promise) {
        try {
            File snapshotFile = new File(getReactApplicationContext().getFilesDir(), SNAPSHOT_RELATIVE_PATH);
            // Diagnóstico explícito: log de la ruta absoluta buscada.
            // Visible en logcat: adb logcat -s KPKNMigrationBridge
            Log.d("KPKNMigrationBridge", "Buscando snapshot en: " + snapshotFile.getAbsolutePath());
            Log.d("KPKNMigrationBridge", "¿Archivo existe?: " + snapshotFile.exists() + " | Tamaño: " + (snapshotFile.exists() ? snapshotFile.length() + " bytes" : "N/A"));
            if (!snapshotFile.exists()) {
                promise.resolve(null);
                return;
            }

            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(snapshotFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            promise.resolve(builder.toString());
        } catch (Exception error) {
            promise.reject("bridge_snapshot_read_failed", error);
        }
    }

    @ReactMethod
    public void markMigrationComplete(Promise promise) {
        SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_MIGRATION_COMPLETE, true).apply();
        promise.resolve(null);
    }
}
