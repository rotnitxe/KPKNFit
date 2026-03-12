package com.yourprime.app;

import android.content.ComponentName;
import android.content.Intent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class KpknNutritionWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_RELOAD_WIDGETS = "com.yourprime.app.WIDGETS_RELOAD";

    private static final String PREFS = "kpkn_widget";
    private static final String KEY = "nutrition";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) KpknWidgetHelper.updateNutritionWidget(context, appWidgetManager, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || !ACTION_RELOAD_WIDGETS.equals(intent.getAction())) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, KpknNutritionWidgetProvider.class));
        onUpdate(context, manager, ids);
    }
}
