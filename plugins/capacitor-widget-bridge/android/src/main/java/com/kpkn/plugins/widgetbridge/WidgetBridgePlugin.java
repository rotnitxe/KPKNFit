package com.kpkn.plugins.widgetbridge;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {

    private static final String PREFS_GROUP = "kpkn_widget";

    @PluginMethod
    public void setItem(PluginCall call) {
        String key = call.getString("key");
        if (key == null) {
            call.reject("Must provide key");
            return;
        }
        String value = call.getString("value");
        if (value == null) {
            call.reject("Must provide value");
            return;
        }

        getContext().getSharedPreferences(PREFS_GROUP, Context.MODE_PRIVATE)
                .edit()
                .putString(key, value)
                .apply();

        call.resolve();
    }

    @PluginMethod
    public void getItem(PluginCall call) {
        String key = call.getString("key");
        if (key == null) {
            call.reject("Must provide key");
            return;
        }

        String value = getContext().getSharedPreferences(PREFS_GROUP, Context.MODE_PRIVATE)
                .getString(key, null);

        JSObject ret = new JSObject();
        ret.put("value", value);
        call.resolve(ret);
    }

    @PluginMethod
    public void reloadWidget(PluginCall call) {
        String packageName = getContext().getPackageName();
        String[] widgetClasses = {
            packageName + ".KpknNextSessionWidgetProvider",
            packageName + ".KpknBatteryWidgetProvider",
            packageName + ".KpknNutritionWidgetProvider",
            packageName + ".KpknVolumeWidgetProvider"
        };
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getContext());

        for (String widgetClassName : widgetClasses) {
            try {
                Class<?> widgetClass = Class.forName(widgetClassName);
                ComponentName componentName = new ComponentName(getContext(), widgetClass);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);

                if (appWidgetIds.length > 0) {
                    Intent intent = new Intent(getContext(), widgetClass);
                    intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
                    getContext().sendBroadcast(intent);
                }
            } catch (ClassNotFoundException e) {
                // Widget provider not yet registered
            }
        }

        call.resolve();
    }
}
