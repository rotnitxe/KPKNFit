package com.yourprime.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;
import org.json.JSONObject;

public class KpknNextSessionWidgetProvider extends AppWidgetProvider {

    private static final String PREFS = "kpkn_widget";
    private static final String KEY = "next_session";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        String sessionName = "Ninguna sesión hoy";
        String programName = "Tu Programa";

        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                sessionName = o.optString("sessionName", sessionName);
                programName = o.optString("programName", programName);
            } catch (Exception ignored) {}
        }

        for (int id : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_next_session);
            views.setTextViewText(R.id.widget_session_name, sessionName);
            views.setTextViewText(R.id.widget_program_name, programName);
            appWidgetManager.updateAppWidget(id, views);
        }
    }
}
