package com.yourprime.app;

import android.content.ComponentName;
import android.content.Intent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class KpknNextSessionWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_RELOAD_WIDGETS = "com.yourprime.app.WIDGETS_RELOAD";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) KpknWidgetHelper.updateNextSessionWidget(context, appWidgetManager, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || !ACTION_RELOAD_WIDGETS.equals(intent.getAction())) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, KpknNextSessionWidgetProvider.class));
        onUpdate(context, manager, ids);
    }
}
