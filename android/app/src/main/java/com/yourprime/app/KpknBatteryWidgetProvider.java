package com.yourprime.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;
import org.json.JSONObject;

public class KpknBatteryWidgetProvider extends AppWidgetProvider {

    private static final String PREFS = "kpkn_widget";
    private static final String KEY = "battery_auge";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        int cns = 0, muscular = 0, spinal = 0;

        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                cns = o.optInt("cns", 0);
                muscular = o.optInt("muscular", 0);
                spinal = o.optInt("spinal", 0);
            } catch (Exception ignored) {}
        }

        for (int id : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_battery);
            views.setProgressBar(R.id.widget_cns_bar, 100, cns, false);
            views.setProgressBar(R.id.widget_muscular_bar, 100, muscular, false);
            views.setProgressBar(R.id.widget_spinal_bar, 100, spinal, false);
            views.setTextViewText(R.id.widget_cns_value, cns + "%");
            views.setTextViewText(R.id.widget_muscular_value, muscular + "%");
            views.setTextViewText(R.id.widget_spinal_value, spinal + "%");
            appWidgetManager.updateAppWidget(id, views);
        }
    }
}
