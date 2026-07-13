package com.yourprime.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;
import org.json.JSONObject;

public class KpknNutritionWidgetProvider extends AppWidgetProvider {

    private static final String PREFS = "kpkn_widget";
    private static final String KEY = "nutrition";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        int calories = 0, protein = 0, carbs = 0, fats = 0, calorieGoal = 0;

        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                calories = o.optInt("calories", 0);
                protein = o.optInt("protein", 0);
                carbs = o.optInt("carbs", 0);
                fats = o.optInt("fats", 0);
                calorieGoal = o.optInt("calorieGoal", 0);
            } catch (Exception ignored) {}
        }

        String caloriesText = calories + " / " + calorieGoal + " kcal";

        for (int id : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_nutrition);
            views.setTextViewText(R.id.widget_calories_main, caloriesText);
            views.setTextViewText(R.id.widget_protein_value, String.valueOf(protein));
            views.setTextViewText(R.id.widget_carbs_value, String.valueOf(carbs));
            views.setTextViewText(R.id.widget_fats_value, String.valueOf(fats));
            appWidgetManager.updateAppWidget(id, views);
        }
    }
}
