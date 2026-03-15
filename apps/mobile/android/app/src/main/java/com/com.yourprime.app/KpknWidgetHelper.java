package com.yourprime.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.RemoteViews;

import org.json.JSONException;
import org.json.JSONObject;

/** Shared logic for KPKN widgets. */
public class KpknWidgetHelper {

    static final String PREFS_GROUP = "kpkn_widget";
    static final String KEY_NEXT_SESSION = "next_session";
    static final String KEY_BATTERY = "battery_auge";
    static final String KEY_NUTRITION = "nutrition";
    static final String KEY_VOLUME = "effective_volume";
    static final String KEY_SYNC_LAST_SYNC_AT = "sync_last_sync_at";
    static final String KEY_SYNC_STALE = "sync_stale";

    private static boolean isStale(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_GROUP, Context.MODE_PRIVATE);
        boolean explicitStale = prefs.getBoolean(KEY_SYNC_STALE, true);
        long lastSyncAt = prefs.getLong(KEY_SYNC_LAST_SYNC_AT, 0L);
        boolean expired = lastSyncAt == 0L || (System.currentTimeMillis() - lastSyncAt) > 24L * 60L * 60L * 1000L;
        return explicitStale || expired;
    }

    private static String staleSuffix(Context context) {
        return isStale(context) ? " · STALE" : "";
    }

    static void updateNextSessionWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_next_session);

        String json = context.getSharedPreferences(PREFS_GROUP, Context.MODE_PRIVATE)
                .getString(KEY_NEXT_SESSION, null);

        String sessionName = "Ninguna sesión hoy";
        String programName = "Tu Programa";

        if (json != null) {
            try {
                JSONObject obj = new JSONObject(json);
                sessionName = obj.optString("sessionName", sessionName);
                programName = obj.optString("programName", programName);
            } catch (JSONException ignored) {}
        }

        if (isStale(context)) {
            programName = programName + " · desactualizado";
        }

        views.setTextViewText(R.id.widget_title, "NEXT SESSION" + staleSuffix(context));
        views.setTextViewText(R.id.widget_session_name, sessionName);
        views.setTextViewText(R.id.widget_program_name, programName);

        Intent clickIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("kpkn://workout"), context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_next_session_root, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    static void updateBatteryWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_battery);

        String json = context.getSharedPreferences(PREFS_GROUP, Context.MODE_PRIVATE)
                .getString(KEY_BATTERY, null);

        int cns = 0, muscular = 0, spinal = 0;

        if (json != null) {
            try {
                JSONObject obj = new JSONObject(json);
                cns = obj.optInt("cns", 0);
                muscular = obj.optInt("muscular", 0);
                spinal = obj.optInt("spinal", 0);
            } catch (JSONException ignored) {}
        }

        views.setProgressBar(R.id.widget_cns_bar, 100, cns, false);
        views.setProgressBar(R.id.widget_muscular_bar, 100, muscular, false);
        views.setProgressBar(R.id.widget_spinal_bar, 100, spinal, false);
        views.setTextViewText(R.id.widget_cns_value, cns + "%" + (isStale(context) ? "*" : ""));
        views.setTextViewText(R.id.widget_muscular_value, muscular + "%" + (isStale(context) ? "*" : ""));
        views.setTextViewText(R.id.widget_spinal_value, spinal + "%" + (isStale(context) ? "*" : ""));

        Intent clickIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("kpkn://progress"), context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_battery_root, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    static void updateNutritionWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_nutrition);

        String json = context.getSharedPreferences(PREFS_GROUP, Context.MODE_PRIVATE)
                .getString(KEY_NUTRITION, null);

        int calories = 0, protein = 0, carbs = 0, fats = 0, calorieGoal = 0;

        if (json != null) {
            try {
                JSONObject obj = new JSONObject(json);
                calories = obj.optInt("calories", 0);
                protein = obj.optInt("protein", 0);
                carbs = obj.optInt("carbs", 0);
                fats = obj.optInt("fats", 0);
                calorieGoal = obj.optInt("calorieGoal", 0);
            } catch (JSONException ignored) {}
        }

        String caloriesText = calorieGoal > 0
                ? calories + " / " + calorieGoal + " kcal"
                : calories + " kcal";
        if (isStale(context)) {
            caloriesText = caloriesText + " *";
        }
        views.setTextViewText(R.id.widget_calories_main, caloriesText);
        views.setTextViewText(R.id.widget_protein_value, String.valueOf(protein));
        views.setTextViewText(R.id.widget_carbs_value, String.valueOf(carbs));
        views.setTextViewText(R.id.widget_fats_value, String.valueOf(fats));

        Intent clickIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("kpkn://nutrition"), context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 2, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_nutrition_root, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    static void updateVolumeWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_volume);

        String json = context.getSharedPreferences(PREFS_GROUP, Context.MODE_PRIVATE)
                .getString(KEY_VOLUME, null);

        int completed = 0;
        int planned = 0;

        if (json != null) {
            try {
                JSONObject obj = new JSONObject(json);
                completed = obj.optInt("completed", 0);
                planned = obj.optInt("planned", 0);
            } catch (JSONException ignored) {}
        }

        int progress = planned > 0 ? Math.min(100, (completed * 100) / planned) : 0;
        String summary = completed + " / " + planned + (isStale(context) ? " *" : "");
        views.setTextViewText(R.id.widget_volume_summary, summary);
        views.setProgressBar(R.id.widget_volume_bar, 100, progress, false);

        Intent clickIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("kpkn://workout"), context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 3, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_volume_root, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
