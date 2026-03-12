package com.yourprime.app.modules;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

import org.json.JSONObject;

public class WidgetModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "KPKNWidgets";
    private static final String PREFS_NAME = "kpkn_widget";
    private static final String ACTION_RELOAD_WIDGETS = "com.yourprime.app.WIDGETS_RELOAD";

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

    @ReactMethod
    public void reloadWidget(Promise promise) {
        Intent intent = new Intent(ACTION_RELOAD_WIDGETS);
        intent.setPackage(getReactApplicationContext().getPackageName());
        getReactApplicationContext().sendBroadcast(intent);
        promise.resolve(null);
    }

    @ReactMethod
    public void syncDashboardState(ReadableMap snapshot, Promise promise) {
        SharedPreferences prefs = getReactApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        try {
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

            if (snapshot.hasKey("nutritionCaloriesToday") && !snapshot.isNull("nutritionCaloriesToday")) {
                JSONObject nutrition = new JSONObject();
                nutrition.put("calories", Math.round(snapshot.getDouble("nutritionCaloriesToday")));
                nutrition.put(
                    "protein",
                    snapshot.hasKey("nutritionProteinToday") && !snapshot.isNull("nutritionProteinToday")
                        ? Math.round(snapshot.getDouble("nutritionProteinToday"))
                        : 0
                );
                nutrition.put(
                    "carbs",
                    snapshot.hasKey("nutritionCarbsToday") && !snapshot.isNull("nutritionCarbsToday")
                        ? Math.round(snapshot.getDouble("nutritionCarbsToday"))
                        : 0
                );
                nutrition.put(
                    "fats",
                    snapshot.hasKey("nutritionFatsToday") && !snapshot.isNull("nutritionFatsToday")
                        ? Math.round(snapshot.getDouble("nutritionFatsToday"))
                        : 0
                );
                nutrition.put(
                    "calorieGoal",
                    snapshot.hasKey("nutritionCalorieGoal") && !snapshot.isNull("nutritionCalorieGoal")
                        ? Math.round(snapshot.getDouble("nutritionCalorieGoal"))
                        : 0
                );
                editor.putString("nutrition", nutrition.toString());
            }

            if (
                (snapshot.hasKey("batteryCnsScore") && !snapshot.isNull("batteryCnsScore")) ||
                (snapshot.hasKey("batteryMuscularScore") && !snapshot.isNull("batteryMuscularScore")) ||
                (snapshot.hasKey("batterySpinalScore") && !snapshot.isNull("batterySpinalScore")) ||
                (snapshot.hasKey("augeBatteryScore") && !snapshot.isNull("augeBatteryScore"))
            ) {
                int fallbackScore = snapshot.hasKey("augeBatteryScore") && !snapshot.isNull("augeBatteryScore")
                    ? (int) Math.round(snapshot.getDouble("augeBatteryScore"))
                    : 0;
                JSONObject battery = new JSONObject();
                battery.put(
                    "cns",
                    snapshot.hasKey("batteryCnsScore") && !snapshot.isNull("batteryCnsScore")
                        ? Math.round(snapshot.getDouble("batteryCnsScore"))
                        : fallbackScore
                );
                battery.put(
                    "muscular",
                    snapshot.hasKey("batteryMuscularScore") && !snapshot.isNull("batteryMuscularScore")
                        ? Math.round(snapshot.getDouble("batteryMuscularScore"))
                        : fallbackScore
                );
                battery.put(
                    "spinal",
                    snapshot.hasKey("batterySpinalScore") && !snapshot.isNull("batterySpinalScore")
                        ? Math.round(snapshot.getDouble("batterySpinalScore"))
                        : fallbackScore
                );
                editor.putString("battery_auge", battery.toString());
            }

            if (snapshot.hasKey("effectiveVolumeToday") && !snapshot.isNull("effectiveVolumeToday")) {
                JSONObject volume = new JSONObject();
                volume.put("completed", Math.round(snapshot.getDouble("effectiveVolumeToday")));
                volume.put(
                    "planned",
                    snapshot.hasKey("effectiveVolumePlanned") && !snapshot.isNull("effectiveVolumePlanned")
                        ? Math.round(snapshot.getDouble("effectiveVolumePlanned"))
                        : 0
                );
                editor.putString("effective_volume", volume.toString());
            }
        } catch (Exception error) {
            promise.reject("widget_sync_failed", error);
            return;
        }

        editor.apply();
        reloadWidget(promise);
    }
}
