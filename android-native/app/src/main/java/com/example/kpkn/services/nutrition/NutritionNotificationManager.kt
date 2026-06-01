package com.example.kpkn.services.nutrition

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kpkn.R
import com.example.kpkn.navigation.KpknDeepLinks
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.domain.nutrition.MacroGoals
import java.util.Calendar

/**
 * NutritionNotificationManager — Alertas inteligentes de macros y recordatorios de comidas.
 *
 * Tipos de notificaciones:
 * 1. Recordatorios de comidas (desayuno, almuerzo, cena) — programados via AlarmManager.
 * 2. Alerta de déficit de proteína (tarde/noche cuando queda >30% sin cubrir).
 * 3. Alerta de calorías sobrantes o excedidas al final del día.
 * 4. Recordatorio de medición corporal programada.
 */
class NutritionNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_MEALS = "nutrition_meals"
        const val CHANNEL_MACROS = "nutrition_macros"
        const val CHANNEL_MEASUREMENT = "nutrition_measurement"

        // Notification IDs
        private const val NOTIF_BREAKFAST = 5001
        private const val NOTIF_LUNCH = 5002
        private const val NOTIF_DINNER = 5003
        private const val NOTIF_MACRO_DEFICIT = 5010
        private const val NOTIF_MEASUREMENT = 5020

        // Request codes for PendingIntents
        private const val REQ_BREAKFAST = 6001
        private const val REQ_LUNCH = 6002
        private const val REQ_DINNER = 6003
        private const val REQ_MACRO_CHECK = 6010
        private const val REQ_MEASUREMENT = 6020

        // Extras
        const val EXTRA_NOTIF_TYPE = "notif_type"
        const val TYPE_BREAKFAST = "breakfast"
        const val TYPE_LUNCH = "lunch"
        const val TYPE_DINNER = "dinner"
        const val TYPE_MACRO_CHECK = "macro_check"
        const val TYPE_MEASUREMENT = "measurement"
    }

    private val appCtx = context.applicationContext
    private val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notifManager = NotificationManagerCompat.from(appCtx)

    // ─── Channel Setup ────────────────────────────────────────────────────────

    fun createChannels() {
        val mealsChannel = NotificationChannel(
            CHANNEL_MEALS,
            appCtx.getString(com.example.kpkn.R.string.notif_channel_meals_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appCtx.getString(com.example.kpkn.R.string.notif_channel_meals_desc)
            enableVibration(true)
        }
        val macrosChannel = NotificationChannel(
            CHANNEL_MACROS,
            appCtx.getString(com.example.kpkn.R.string.notif_channel_macros_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appCtx.getString(com.example.kpkn.R.string.notif_channel_macros_desc)
            enableVibration(true)
        }
        val measurementChannel = NotificationChannel(
            CHANNEL_MEASUREMENT,
            appCtx.getString(com.example.kpkn.R.string.notif_channel_measurement_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appCtx.getString(com.example.kpkn.R.string.notif_channel_measurement_desc)
        }

        val manager = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(listOf(mealsChannel, macrosChannel, measurementChannel))
    }

    // ─── Permission Check ─────────────────────────────────────────────────────

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appCtx,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // ─── Meal Reminders ───────────────────────────────────────────────────────

    /**
     * Programa recordatorios diarios para cada comida.
     * @param breakfastTime "08:00"
     * @param lunchTime "13:00"
     * @param dinnerTime "20:00"
     */
    fun scheduleMealReminders(
        breakfastTime: String = "08:00",
        lunchTime: String = "13:00",
        dinnerTime: String = "20:00",
    ) {
        scheduleDaily(REQ_BREAKFAST, parseHour(breakfastTime), parseMin(breakfastTime), TYPE_BREAKFAST)
        scheduleDaily(REQ_LUNCH, parseHour(lunchTime), parseMin(lunchTime), TYPE_LUNCH)
        scheduleDaily(REQ_DINNER, parseHour(dinnerTime), parseMin(dinnerTime), TYPE_DINNER)
    }

    fun cancelMealReminders() {
        val types = listOf(REQ_BREAKFAST to TYPE_BREAKFAST, REQ_LUNCH to TYPE_LUNCH, REQ_DINNER to TYPE_DINNER)
        types.forEach { (req, type) ->
            val intent = buildReceiverIntent(type)
            val pi = PendingIntent.getBroadcast(
                appCtx, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pi)
        }
    }

    /**
     * Programa una verificación diaria de macros a las 20:30.
     * La recibe NutritionAlertReceiver, que evalúa el estado real.
     */
    fun scheduleDailyMacroCheck(hour: Int = 20, minute: Int = 30) {
        scheduleDaily(REQ_MACRO_CHECK, hour, minute, TYPE_MACRO_CHECK)
    }

    fun cancelDailyMacroCheck() {
        val pi = PendingIntent.getBroadcast(
            appCtx, REQ_MACRO_CHECK,
            buildReceiverIntent(TYPE_MACRO_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pi)
    }

    /**
     * Programa recordatorio de medición corporal para una fecha + hora específica.
     */
    fun scheduleMeasurementReminder(dateIso: String, hour: Int = 9, minute: Int = 0) {
        try {
            val date = java.time.LocalDate.parse(dateIso)
            val triggerMs = Calendar.getInstance().apply {
                set(date.year, date.monthValue - 1, date.dayOfMonth, hour, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            if (triggerMs <= System.currentTimeMillis()) return

            val pi = PendingIntent.getBroadcast(
                appCtx, REQ_MEASUREMENT,
                buildReceiverIntent(TYPE_MEASUREMENT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        } catch (e: Exception) {
            android.util.Log.e("NutritionNotif", "Failed to schedule measurement reminder", e)
        }
    }

    fun cancelMeasurementReminder() {
        val pi = PendingIntent.getBroadcast(
            appCtx, REQ_MEASUREMENT,
            buildReceiverIntent(TYPE_MEASUREMENT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pi)
    }

    // ─── Immediate Notifications ──────────────────────────────────────────────

    /**
     * Envía alerta de déficit de macros si el usuario no ha cubierto sus objetivos.
     * Llamar desde NutritionViewModel al final del día o manualmente.
     */
    fun sendMacroDeficitAlert(totals: DailyMacroTotals, goals: MacroGoals) {
        if (!hasPermission()) return

        var hasCalorieExcess = false
        val deficitItems = buildList {
            val protPct = if (goals.proteinGoal > 0) totals.protein / goals.proteinGoal else 1.0
            val carbPct = if (goals.carbGoal > 0) totals.carbs / goals.carbGoal else 1.0
            val fatPct = if (goals.fatGoal > 0) totals.fats / goals.fatGoal else 1.0
            val calPct = if (goals.calorieGoal > 0) totals.calories / goals.calorieGoal else 1.0

            if (protPct < 0.70) add(appCtx.getString(com.example.kpkn.R.string.notif_macro_protein, totals.protein.toInt(), goals.proteinGoal, (protPct * 100).toInt()))
            if (carbPct < 0.60) add(appCtx.getString(com.example.kpkn.R.string.notif_macro_carbs, totals.carbs.toInt(), goals.carbGoal, (carbPct * 100).toInt()))
            if (fatPct < 0.60) add(appCtx.getString(com.example.kpkn.R.string.notif_macro_fats, totals.fats.toInt(), goals.fatGoal, (fatPct * 100).toInt()))
            if (calPct > 1.10) {
                hasCalorieExcess = true
                add(appCtx.getString(com.example.kpkn.R.string.notif_macro_calories_exceeded, totals.calories.toInt(), goals.calorieGoal))
            }
        }

        if (deficitItems.isEmpty()) return

        val body = deficitItems.joinToString("\n")
        val title = if (hasCalorieExcess)
            appCtx.getString(com.example.kpkn.R.string.notif_macro_title_exceeded)
        else
            appCtx.getString(com.example.kpkn.R.string.notif_macro_title_incomplete)

        val notification = NotificationCompat.Builder(appCtx, CHANNEL_MACROS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(deficitItems.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent())
            .build()

        try {
            notifManager.notify(NOTIF_MACRO_DEFICIT, notification)
        } catch (e: Exception) {
            android.util.Log.w("NutritionNotif", "Could not send macro deficit alert", e)
        }
    }

    fun sendMeasurementReminderNotification(nextDate: String) {
        if (!hasPermission()) return
        val label = try {
            java.time.LocalDate.parse(nextDate)
                .format(java.time.format.DateTimeFormatter.ofPattern(
                    appCtx.getString(com.example.kpkn.R.string.date_format_measurement),
                    com.example.kpkn.ui.locale.LocaleManager.getEffectiveLocale(appCtx),
                ))
        } catch (_: Exception) { nextDate }

        val notification = NotificationCompat.Builder(appCtx, CHANNEL_MEASUREMENT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appCtx.getString(com.example.kpkn.R.string.notif_measurement_title))
            .setContentText(appCtx.getString(com.example.kpkn.R.string.notif_measurement_text, label))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent())
            .build()

        try {
            notifManager.notify(NOTIF_MEASUREMENT, notification)
        } catch (e: Exception) {
            android.util.Log.w("NutritionNotif", "Could not send measurement reminder", e)
        }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun scheduleDaily(requestCode: Int, hour: Int, minute: Int, type: String) {
        val triggerMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        val pi = PendingIntent.getBroadcast(
            appCtx, requestCode,
            buildReceiverIntent(type),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerMs, AlarmManager.INTERVAL_DAY, pi)
            } else {
                alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerMs, AlarmManager.INTERVAL_DAY, pi)
            }
        } catch (e: Exception) {
            android.util.Log.e("NutritionNotif", "Failed to schedule $type alarm", e)
        }
    }

    private fun buildReceiverIntent(type: String) =
        Intent(appCtx, NutritionAlertReceiver::class.java).apply {
            putExtra(EXTRA_NOTIF_TYPE, type)
        }

    private fun mainActivityPendingIntent(): PendingIntent =
        KpknDeepLinks.pendingActivityIntent(
            context = appCtx,
            requestCode = 0,
            path = "nutrition",
        )

    private fun parseHour(time: String) = time.split(":").getOrNull(0)?.toIntOrNull() ?: 8
    private fun parseMin(time: String) = time.split(":").getOrNull(1)?.toIntOrNull() ?: 0
}

// ═══════════════════════════════════════════════════════════════════════
// BROADCAST RECEIVER — Handles alarm triggers
// ═══════════════════════════════════════════════════════════════════════

class NutritionAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(NutritionNotificationManager.EXTRA_NOTIF_TYPE) ?: return
        val manager = NutritionNotificationManager(context)
        manager.createChannels()

        when (type) {
            NutritionNotificationManager.TYPE_BREAKFAST -> sendMealReminder(context, manager, context.getString(com.example.kpkn.R.string.notif_breakfast_text), NutritionNotificationManager.TYPE_BREAKFAST)
            NutritionNotificationManager.TYPE_LUNCH -> sendMealReminder(context, manager, context.getString(com.example.kpkn.R.string.notif_lunch_text), NutritionNotificationManager.TYPE_LUNCH)
            NutritionNotificationManager.TYPE_DINNER -> sendMealReminder(context, manager, context.getString(com.example.kpkn.R.string.notif_dinner_text), NutritionNotificationManager.TYPE_DINNER)
            NutritionNotificationManager.TYPE_MACRO_CHECK -> checkAndSendMacroAlert(context, manager)
            NutritionNotificationManager.TYPE_MEASUREMENT -> {
                // La fecha exacta la maneja el scheduler, aquí solo enviamos la notificación
                manager.sendMeasurementReminderNotification(java.time.LocalDate.now().toString())
            }
        }
    }

    private fun sendMealReminder(context: Context, manager: NutritionNotificationManager, text: String, type: String) {
        // Verificar si ya hay registros de esa comida hoy
        try {
            val repo = com.example.kpkn.data.repository.NutritionRepository.getInstance()
            val today = java.time.LocalDate.now().toString()
            val todayLogs = repo.nutritionLogs.value.filter { it.date.take(10) == today }

            val mealType = when (type) {
                NutritionNotificationManager.TYPE_BREAKFAST -> com.example.kpkn.data.models.MealType.BREAKFAST
                NutritionNotificationManager.TYPE_LUNCH -> com.example.kpkn.data.models.MealType.LUNCH
                NutritionNotificationManager.TYPE_DINNER -> com.example.kpkn.data.models.MealType.DINNER
                else -> return
            }
            if (todayLogs.any { it.mealType == mealType }) return // ya registró
        } catch (_: Exception) { /* repo no inicializado, enviar igual */ }

        if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) return

        val notifId = when (type) {
            NutritionNotificationManager.TYPE_BREAKFAST -> 5001
            NutritionNotificationManager.TYPE_LUNCH -> 5002
            else -> 5003
        }

        val notification = NotificationCompat.Builder(context, NutritionNotificationManager.CHANNEL_MEALS)
            .setSmallIcon(com.example.kpkn.R.mipmap.ic_launcher)
            .setContentTitle(context.getString(com.example.kpkn.R.string.notif_app_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(
                KpknDeepLinks.pendingActivityIntent(
                    context = context,
                    requestCode = 0,
                    path = "nutrition",
                )
            )
            .build()

        try {
            androidx.core.app.NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: Exception) {}
    }

    private fun checkAndSendMacroAlert(context: Context, manager: NutritionNotificationManager) {
        try {
            // Asegurar que los repos estén inicializados (puede que la app esté cerrada)
            val nutritionRepo = com.example.kpkn.data.repository.NutritionRepository.init(context)
            val programRepo = com.example.kpkn.data.repository.ProgramRepository.init(context)
            val today = java.time.LocalDate.now().toString()
            val todayLogs = nutritionRepo.nutritionLogs.value.filter {
                it.date.take(10) == today &&
                it.status != com.example.kpkn.data.models.NutritionStatus.PLANNED
            }
            if (todayLogs.isEmpty()) return

            val totals = com.example.kpkn.domain.nutrition.computeDailyTotals(todayLogs)
            val settings = programRepo.settings.value
            val goals = com.example.kpkn.domain.nutrition.MacroGoals(
                calorieGoal = settings.dailyCalorieGoal ?: 2500,
                proteinGoal = settings.dailyProteinGoal ?: 150,
                carbGoal = settings.dailyCarbGoal ?: 250,
                fatGoal = settings.dailyFatGoal ?: 70,
            )
            manager.sendMacroDeficitAlert(totals, goals)
        } catch (e: Exception) {
            android.util.Log.e("NutritionAlert", "Macro check failed", e)
        }
    }
}
