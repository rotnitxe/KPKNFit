package com.yourprime.app.modules;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONObject;

public class WidgetModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "KPKNWidgets";
    private static final String PREFS_NAME = "kpkn_widget";
    private static final String ACTION_RELOAD_WIDGETS = "com.yourprime.app.WIDGETS_RELOAD";
    private static final String KEY_SYNC_LAST_SYNC_AT = "sync_last_sync_at";
    private static final String KEY_SYNC_LAST_RELOAD_AT = "sync_last_reload_at";
    private static final String KEY_SYNC_LAST_ERROR = "sync_last_error";
    private static final String KEY_SYNC_STALE = "sync_stale";
    private static final String KEY_SYNC_STALE_REASON = "sync_stale_reason";
    private static final String KEY_SYNC_SOURCE = "sync_source";

    public WidgetModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void setItem(String key, String value, Promise promise) {
        SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(key, value).apply();
        promise.resolve(null);
    }

    private void reloadWidgetsInternal() {
        SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_SYNC_LAST_RELOAD_AT, System.currentTimeMillis()).apply();

        Intent intent = new Intent(ACTION_RELOAD_WIDGETS);
        intent.setPackage(getReactApplicationContext().getPackageName());
        getReactApplicationContext().sendBroadcast(intent);
    }

    private Double getFiniteNumber(ReadableMap snapshot, String key) {
        if (!snapshot.hasKey(key) || snapshot.isNull(key)) {
            return null;
        }
        double value = snapshot.getDouble(key);
        return Double.isFinite(value) ? value : null;
    }

    private void markSuccess(SharedPreferences.Editor editor, String source) {
        editor.putLong(KEY_SYNC_LAST_SYNC_AT, System.currentTimeMillis());
        editor.putBoolean(KEY_SYNC_STALE, false);
        editor.remove(KEY_SYNC_STALE_REASON);
        editor.remove(KEY_SYNC_LAST_ERROR);
        editor.putString(KEY_SYNC_SOURCE, source);
    }

    @ReactMethod
    public void reloadWidget(Promise promise) {
        reloadWidgetsInternal();
        promise.resolve(null);
    }

    @ReactMethod
    public void syncDashboardState(ReadableMap snapshot, Promise promise) {
        SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        try {
            String source = snapshot.hasKey("widgetSyncSource") && !snapshot.isNull("widgetSyncSource")
                ? snapshot.getString("widgetSyncSource")
                : "unknown";

            if (snapshot.hasKey("nextSessionLabel") && !snapshot.isNull("nextSessionLabel")) {
                JSONObject nextSession = new JSONObject();
                nextSession.put("sessionName", snapshot.getString("nextSessionLabel"));
                nextSession.put(
                    "programName",
                    snapshot.hasKey("nextSessionProgramName") && !snapshot.isNull("nextSessionProgramName")
                        ? snapshot.getString("nextSessionProgramName")
                        : "KPKN"
                );
                editor.putString("next_session", nextSession.toString());
            }

            Double calories = getFiniteNumber(snapshot, "nutritionCaloriesToday");
            if (calories != null) {
                JSONObject nutrition = new JSONObject();
                nutrition.put("calories", Math.round(calories));
                Double protein = getFiniteNumber(snapshot, "nutritionProteinToday");
                Double carbs = getFiniteNumber(snapshot, "nutritionCarbsToday");
                Double fats = getFiniteNumber(snapshot, "nutritionFatsToday");
                Double calorieGoal = getFiniteNumber(snapshot, "nutritionCalorieGoal");
                nutrition.put("protein", protein != null ? Math.round(protein) : 0);
                nutrition.put("carbs", carbs != null ? Math.round(carbs) : 0);
                nutrition.put("fats", fats != null ? Math.round(fats) : 0);
                nutrition.put("calorieGoal", calorieGoal != null ? Math.round(calorieGoal) : 0);
                editor.putString("nutrition", nutrition.toString());
            }

            Double fallbackScore = getFiniteNumber(snapshot, "augeBatteryScore");
            Double cns = getFiniteNumber(snapshot, "batteryCnsScore");
            Double muscular = getFiniteNumber(snapshot, "batteryMuscularScore");
            Double spinal = getFiniteNumber(snapshot, "batterySpinalScore");
            if (fallbackScore != null || cns != null || muscular != null || spinal != null) {
                int safeFallback = fallbackScore != null ? (int) Math.round(fallbackScore) : 0;
                JSONObject battery = new JSONObject();
                battery.put("cns", cns != null ? Math.round(cns) : safeFallback);
                battery.put("muscular", muscular != null ? Math.round(muscular) : safeFallback);
                battery.put("spinal", spinal != null ? Math.round(spinal) : safeFallback);
                editor.putString("battery_auge", battery.toString());
            }

            Double completed = getFiniteNumber(snapshot, "effectiveVolumeToday");
            if (completed != null) {
                JSONObject volume = new JSONObject();
                Double planned = getFiniteNumber(snapshot, "effectiveVolumePlanned");
                volume.put("completed", Math.round(completed));
                volume.put("planned", planned != null ? Math.round(planned) : 0);
                editor.putString("effective_volume", volume.toString());
            }

            markSuccess(editor, source);
        } catch (Exception error) {
            editor.putBoolean(KEY_SYNC_STALE, true);
            editor.putString(KEY_SYNC_STALE_REASON, "widget-sync-failed");
            editor.putString(KEY_SYNC_LAST_ERROR, error.getMessage());
            editor.apply();
            promise.reject("widget_sync_failed", error);
            return;
        }

        editor.apply();
        reloadWidgetsInternal();
        promise.resolve(null);
    }

    @ReactMethod
    public void getStatus(Promise promise) {
        SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        WritableMap result = Arguments.createMap();
        long lastSyncAt = prefs.getLong(KEY_SYNC_LAST_SYNC_AT, 0L);
        long lastReloadAt = prefs.getLong(KEY_SYNC_LAST_RELOAD_AT, 0L);
        String lastError = prefs.getString(KEY_SYNC_LAST_ERROR, null);
        String staleReason = prefs.getString(KEY_SYNC_STALE_REASON, null);
        String source = prefs.getString(KEY_SYNC_SOURCE, "unknown");
        boolean stale = prefs.getBoolean(KEY_SYNC_STALE, true);

        if (lastSyncAt <= 0L) {
            result.putNull("lastSyncAtMs");
        } else {
            result.putDouble("lastSyncAtMs", lastSyncAt);
        }
        if (lastReloadAt <= 0L) {
            result.putNull("lastReloadAtMs");
        } else {
            result.putDouble("lastReloadAtMs", lastReloadAt);
        }
        if (lastError == null) {
            result.putNull("lastError");
        } else {
            result.putString("lastError", lastError);
        }
        result.putBoolean("stale", stale);
        if (staleReason == null) {
            result.putNull("staleReason");
        } else {
            result.putString("staleReason", staleReason);
        }
        result.putString("source", source);
        promise.resolve(result);
    }

    @ReactMethod
    public void markStale(String reason, Promise promise) {
        SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(KEY_SYNC_STALE, true)
            .putString(KEY_SYNC_STALE_REASON, reason)
            .putString(KEY_SYNC_LAST_ERROR, reason)
            .apply();
        reloadWidgetsInternal();
        promise.resolve(null);
    }
}
