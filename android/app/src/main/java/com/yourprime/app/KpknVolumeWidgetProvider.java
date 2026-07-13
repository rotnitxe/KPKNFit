package com.yourprime.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;
import org.json.JSONObject;

public class KpknVolumeWidgetProvider extends AppWidgetProvider {

    private static final String PREFS = "kpkn_widget";
    private static final String KEY = "effective_volume";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        int completed = 0, planned = 0;

        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                completed = o.optInt("completed", 0);
                planned = o.optInt("planned", 0);
            } catch (Exception ignored) {}
        }

        int progress = (planned > 0) ? Math.min(100, (completed * 100) / planned) : 0;
        String summary = completed + " / " + planned;

        for (int id : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_volume);
            views.setTextViewText(R.id.widget_volume_summary, summary);
            views.setProgressBar(R.id.widget_volume_bar, 100, progress, false);
            appWidgetManager.updateAppWidget(id, views);
        }
    }
}
