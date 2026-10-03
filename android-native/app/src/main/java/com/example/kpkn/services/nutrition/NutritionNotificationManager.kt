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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kpkn.R
import com.example.kpkn.navigation.KpknDeepLinks
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toDailyGoalSnapshot
import com.example.kpkn.data.db.toNutritionLog
import com.example.kpkn.data.db.toNutritionPlan
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.MacroAlertKind
import com.example.kpkn.domain.nutrition.NutritionReminderPlanner
import com.example.kpkn.domain.nutrition.areGoalDeficitAlertsAvailable
import com.example.kpkn.domain.nutrition.areMealRemindersAvailable
import com.example.kpkn.domain.nutrition.computeDailyTotals
import com.example.kpkn.domain.nutrition.macroDeficitAlerts
import com.example.kpkn.domain.nutrition.resolveDayGoals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Calendar

/**
 * NutritionNotificationManager — Alertas inteligentes de macros y recordatorios de comidas.
 *
 * Tipos de notificaciones:
 * 1. Recordatorios de comidas (desayuno, almuerzo, cena) — alarmas de UN solo uso en AlarmManager: el receiver
 *    reprograma la siguiente al disparar y [NutritionReminderBootReceiver] las restaura tras un reinicio.
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

        // Hora de reloj (local) con que se programó la alarma: si al disparar no se pueden leer los ajustes, el
        // receiver sigue reprogramando la cadena diaria a esa misma hora.
        const val EXTRA_HOUR = "notif_hour"
        const val EXTRA_MINUTE = "notif_minute"

        // Chequeo diario de macros: 20:30 locales.
        const val MACRO_CHECK_HOUR = 20
        const val MACRO_CHECK_MINUTE = 30

        // Sin permiso de alarmas exactas el aviso llega dentro de una ventana de 10 min desde la hora pedida.
        private const val INEXACT_WINDOW_MS = 10 * 60 * 1000L

        private val MEAL_TYPES = listOf(TYPE_BREAKFAST, TYPE_LUNCH, TYPE_DINNER)
        private val ALERT_TYPES = MEAL_TYPES + TYPE_MACRO_CHECK
    }

    private val appCtx = context.applicationContext
    private val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notifManager = NotificationManagerCompat.from(appCtx)

    // ─── Channel Setup ────────────────────────────────────────────────────────

    fun createChannels() {
        // Los canales existen desde API 26 (minSdk 24): antes `NotificationChannel` ni siquiera existe.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
     * Programa la PRÓXIMA ocurrencia del recordatorio de cada comida. Son alarmas de un solo uso: el receiver
     * reprograma la siguiente al disparar y [NutritionReminderBootReceiver] las restaura tras un reinicio, una
     * actualización de la app o un cambio de zona horaria (ver [scheduleNext]).
     * @param breakfastTime "08:00"
     * @param lunchTime "13:00"
     * @param dinnerTime "20:00"
     */
    fun scheduleMealReminders(
        breakfastTime: String = "08:00",
        lunchTime: String = "13:00",
        dinnerTime: String = "20:00",
    ) {
        scheduleNext(TYPE_BREAKFAST, parseHour(breakfastTime), parseMin(breakfastTime))
        scheduleNext(TYPE_LUNCH, parseHour(lunchTime), parseMin(lunchTime))
        scheduleNext(TYPE_DINNER, parseHour(dinnerTime), parseMin(dinnerTime))
    }

    fun cancelMealReminders() {
        MEAL_TYPES.forEach { cancelAlarm(it) }
    }

    /**
     * Programa la próxima verificación diaria de macros (20:30 por defecto).
     * La recibe NutritionAlertReceiver, que evalúa el estado real leyendo Room.
     */
    fun scheduleDailyMacroCheck(hour: Int = MACRO_CHECK_HOUR, minute: Int = MACRO_CHECK_MINUTE) {
        scheduleNext(TYPE_MACRO_CHECK, hour, minute)
    }

    fun cancelDailyMacroCheck() {
        cancelAlarm(TYPE_MACRO_CHECK)
    }

    // ─── Sync with settings ───────────────────────────────────────────────────

    /**
     * Hora de reloj (hora, minuto) con que [settings] pide disparar [type]; null si ese aviso no está habilitado.
     * Los recordatorios de comida siguen activos en modo «solo registro» (solo SKIPPED los silencia). El chequeo de
     * macros viaja con ellos (mismo interruptor): si hay algo que avisar lo decide el receiver al disparar (en «solo
     * registro» no hay metas y no avisa), así la cadena sigue viva cuando el usuario vuelve a un plan.
     */
    internal fun configuredTime(type: String, settings: Settings): Pair<Int, Int>? {
        if (!areMealRemindersAvailable(settings)) return null
        return when (type) {
            TYPE_BREAKFAST -> parseTime(settings.mealReminderBreakfast)
            TYPE_LUNCH -> parseTime(settings.mealReminderLunch)
            TYPE_DINNER -> parseTime(settings.mealReminderDinner)
            TYPE_MACRO_CHECK -> MACRO_CHECK_HOUR to MACRO_CHECK_MINUTE
            else -> null
        }
    }

    /**
     * Alinea la alarma de [type] con [settings]: programa su próxima ocurrencia o, si el aviso no está habilitado,
     * la cancela. Devuelve true si quedó programada.
     */
    internal fun syncReminder(type: String, settings: Settings, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        val time = configuredTime(type, settings)
        if (time == null) {
            cancelAlarm(type)
            return false
        }
        scheduleNext(type, time.first, time.second, now)
        return true
    }

    /** Realinea TODAS las alarmas de comidas y de macros con [settings] (arranque, actualización, cambio de zona). */
    fun syncReminders(settings: Settings, now: ZonedDateTime = ZonedDateTime.now()) {
        ALERT_TYPES.forEach { syncReminder(it, settings, now) }
    }

    // ─── Measurement Reminder ─────────────────────────────────────────────────

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
     *
     * Sin metas ([DayGoalsResult.Absent]) no se envía NINGÚN aviso de «te
     * faltan calorías/proteína»: no hay meta contra la que medir. Un campo
     * sin meta tampoco genera su línea de alerta.
     */
    fun sendMacroDeficitAlert(totals: DailyMacroTotals, goals: DayGoalsResult) {
        if (!hasPermission()) return

        val alerts = macroDeficitAlerts(totals, goals)
        if (alerts.items.isEmpty()) return

        val deficitItems = alerts.items.map { item ->
            when (item.kind) {
                MacroAlertKind.PROTEIN_DEFICIT ->
                    appCtx.getString(com.example.kpkn.R.string.notif_macro_protein, item.consumed, item.goal, item.percent)
                MacroAlertKind.CARB_DEFICIT ->
                    appCtx.getString(com.example.kpkn.R.string.notif_macro_carbs, item.consumed, item.goal, item.percent)
                MacroAlertKind.FAT_DEFICIT ->
                    appCtx.getString(com.example.kpkn.R.string.notif_macro_fats, item.consumed, item.goal, item.percent)
                MacroAlertKind.CALORIE_EXCESS ->
                    appCtx.getString(com.example.kpkn.R.string.notif_macro_calories_exceeded, item.consumed, item.goal)
            }
        }

        val body = deficitItems.joinToString("\n")
        val title = if (alerts.calorieExcess)
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

    /**
     * Notificación «registra tu comida» de [type] (desayuno, almuerzo o cena): mismos ids, canal y textos de
     * siempre. Quien la invoca decide si corresponde (p. ej. no si esa comida ya está registrada hoy).
     */
    fun sendMealReminderNotification(type: String) {
        if (!hasPermission()) return
        val (notifId, textRes) = when (type) {
            TYPE_BREAKFAST -> NOTIF_BREAKFAST to R.string.notif_breakfast_text
            TYPE_LUNCH -> NOTIF_LUNCH to R.string.notif_lunch_text
            TYPE_DINNER -> NOTIF_DINNER to R.string.notif_dinner_text
            else -> return
        }

        val notification = NotificationCompat.Builder(appCtx, CHANNEL_MEALS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appCtx.getString(R.string.notif_app_title))
            .setContentText(appCtx.getString(textRes))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent())
            .build()

        try {
            notifManager.notify(notifId, notification)
        } catch (e: Exception) {
            android.util.Log.w("NutritionNotif", "Could not send $type reminder", e)
        }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    /**
     * Programa UNA alarma para la próxima ocurrencia de [type] a las [hour]:[minute] (hora local). Nunca
     * `setRepeating`: un período fijo de 24 h deriva respecto del reloj de pared en cada cambio de hora y el sistema
     * lo agrupa o lo difiere. El receiver reprograma la siguiente al disparar y el receiver de arranque restaura la
     * cadena cuando el sistema borra las alarmas.
     *
     * Con permiso de alarmas exactas (siempre en API < 31) usa `setExactAndAllowWhileIdle`; sin él, una ventana
     * inexacta de 10 min.
     */
    internal fun scheduleNext(type: String, hour: Int, minute: Int, now: ZonedDateTime = ZonedDateTime.now()) {
        val requestCode = requestCodeFor(type) ?: return
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val triggerMs = NutritionReminderPlanner.nextTrigger(now, safeHour, safeMinute).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            appCtx, requestCode,
            buildReceiverIntent(type).putExtra(EXTRA_HOUR, safeHour).putExtra(EXTRA_MINUTE, safeMinute),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            if (canScheduleExactAlarms()) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                    return
                } catch (_: SecurityException) {
                    // El permiso se revocó entre la comprobación y la llamada: respaldo inexacto.
                }
            }
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerMs, INEXACT_WINDOW_MS, pi)
        } catch (e: Exception) {
            android.util.Log.e("NutritionNotif", "Failed to schedule $type alarm", e)
        }
    }

    /** API < 31: las alarmas exactas siempre están permitidas; desde la 31 dependen del permiso especial. */
    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun cancelAlarm(type: String) {
        val requestCode = requestCodeFor(type) ?: return
        val pi = PendingIntent.getBroadcast(
            appCtx, requestCode,
            buildReceiverIntent(type),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pi)
    }

    private fun requestCodeFor(type: String): Int? = when (type) {
        TYPE_BREAKFAST -> REQ_BREAKFAST
        TYPE_LUNCH -> REQ_LUNCH
        TYPE_DINNER -> REQ_DINNER
        TYPE_MACRO_CHECK -> REQ_MACRO_CHECK
        else -> null
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

    private fun parseTime(time: String): Pair<Int, Int> = parseHour(time) to parseMin(time)
    private fun parseHour(time: String) = time.split(":").getOrNull(0)?.toIntOrNull() ?: 8
    private fun parseMin(time: String) = time.split(":").getOrNull(1)?.toIntOrNull() ?: 0
}

// ═══════════════════════════════════════════════════════════════════════
// BROADCAST RECEIVER — Handles alarm triggers
// ═══════════════════════════════════════════════════════════════════════

/**
 * Recibe las alarmas de comidas y de macros. Lee Room directamente (WP-U14 / C10): en un proceso recién creado por el
 * broadcast los StateFlow del repositorio están vacíos (la alerta de macros nunca disparaba con la app cerrada y los
 * recordatorios sonaban aunque la comida ya estuviera registrada) y arrancar el repositorio aquí lanzaría todo su
 * arranque (carga + importación del catálogo) dentro del broadcast.
 *
 * Las alarmas son de un solo uso: al disparar se reprograma la siguiente según los ajustes vigentes.
 */
class NutritionAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(NutritionNotificationManager.EXTRA_NOTIF_TYPE) ?: return
        val appContext = context.applicationContext

        if (type == NutritionNotificationManager.TYPE_MEASUREMENT) {
            // Aviso de un solo uso (la fecha exacta la maneja el scheduler): sin Room ni reprogramación.
            val manager = NutritionNotificationManager(appContext)
            manager.createChannels()
            manager.sendMeasurementReminderNotification(LocalDate.now().toString())
            return
        }

        val firedHour = intent.getIntExtra(NutritionNotificationManager.EXTRA_HOUR, -1)
        val firedMinute = intent.getIntExtra(NutritionNotificationManager.EXTRA_MINUTE, -1)
        // goAsync() devuelve null si alguien invoca onReceive a mano (pruebas): finish() es opcional.
        val pendingResult: BroadcastReceiver.PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAlarm(appContext, type, firedHour, firedMinute)
            } catch (t: Throwable) {
                Log.e(TAG, "Nutrition alarm $type failed", t)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NutritionAlert"

        /**
         * Todo lo que hace una alarma de comida/macros, fuera de `onReceive` para poder probarlo. [database] y [now]
         * son los únicos puntos de inyección (mismo patrón que `CompetitionReminderBootReceiver.reschedulePersistedRecords`):
         * en producción son la base real y el reloj; las pruebas pasan una base en memoria y un instante fijo.
         *
         * Orden: 1) con los ajustes vigentes se reprograma la siguiente ocurrencia o se cancela si el aviso se
         * deshabilitó (una alarma vieja nunca avisa); 2) solo entonces se decide si hay algo que avisar.
         * [firedHour]/[firedMinute] son la hora con que se programó esta alarma (-1 si no viene).
         */
        internal suspend fun handleAlarm(
            context: Context,
            type: String,
            firedHour: Int = -1,
            firedMinute: Int = -1,
            database: KpknDatabase = KpknDatabase.getInstance(context),
            now: ZonedDateTime = ZonedDateTime.now(),
        ) {
            val mealType = mealTypeFor(type)
            if (mealType == null && type != NutritionNotificationManager.TYPE_MACRO_CHECK) return

            val manager = NutritionNotificationManager(context)
            manager.createChannels()

            val settings = try {
                database.settingsDao().get()?.toSettings() ?: Settings()
            } catch (e: Exception) {
                Log.e(TAG, "Could not read the settings for the $type alarm", e)
                null
            }
            if (settings == null) {
                // Sin ajustes no se puede decidir nada: se conserva la cadena diaria a la hora que acaba de disparar.
                if (firedHour in 0..23 && firedMinute in 0..59) manager.scheduleNext(type, firedHour, firedMinute, now)
                return
            }
            if (!manager.syncReminder(type, settings, now)) return

            val today = now.toLocalDate()
            if (mealType != null) {
                sendMealReminderIfPending(manager, database, type, mealType, today)
            } else {
                sendMacroAlertIfNeeded(manager, database, settings, today)
            }
        }

        private suspend fun sendMealReminderIfPending(
            manager: NutritionNotificationManager,
            database: KpknDatabase,
            type: String,
            mealType: MealType,
            today: LocalDate,
        ) {
            val alreadyLogged = try {
                loadLoggedMeals(database, today).any { it.mealType == mealType }
            } catch (e: Exception) {
                // Sin poder leer los registros es preferible un aviso de más a ninguno.
                Log.w(TAG, "Could not read today's meals for the $type reminder", e)
                false
            }
            if (!alreadyLogged) manager.sendMealReminderNotification(type)
        }

        private suspend fun sendMacroAlertIfNeeded(
            manager: NutritionNotificationManager,
            database: KpknDatabase,
            settings: Settings,
            today: LocalDate,
        ) {
            // «Solo registro»: no hay metas contra las que medir, luego tampoco hay déficit que avisar.
            if (!areGoalDeficitAlertsAvailable(settings)) return
            try {
                val todayLogs = loadLoggedMeals(database, today)
                // Regla de siempre: sin ninguna comida registrada hoy no hay aviso de macros (un día sin seguimiento no
                // es un déficit contra la meta; para eso están los recordatorios de comida).
                if (todayLogs.isEmpty()) return
                val dao = database.nutritionDao()
                val totals = computeDailyTotals(todayLogs)
                val activePlan = dao.getActiveState()?.activePlanId?.let { planId ->
                    dao.getAllPlans().firstOrNull { it.id == planId }?.toNutritionPlan()
                }
                val snapshot = dao.getDailyGoalSnapshot(today.toString())?.toDailyGoalSnapshot()
                // Metas del día resueltas por fecha: el snapshot del día manda sobre el plan actual. Sin metas
                // (Absent) no se envía ningún aviso de «te faltan calorías/proteína».
                val goals = resolveDayGoals(
                    date = today,
                    settings = settings,
                    activePlan = activePlan,
                    snapshot = snapshot,
                    today = today,
                )
                manager.sendMacroDeficitAlert(totals, goals)
            } catch (e: Exception) {
                Log.e(TAG, "Macro check failed", e)
            }
        }

        /** Comidas registradas (no planificadas) del día [day], leídas de Room por prefijo de fecha. */
        private suspend fun loadLoggedMeals(database: KpknDatabase, day: LocalDate): List<NutritionLog> =
            database.nutritionDao().getLogsForDayPrefix(day.toString())
                .mapNotNull { entity -> runCatching { entity.toNutritionLog() }.getOrNull() }
                .filter { it.status != NutritionStatus.PLANNED }

        private fun mealTypeFor(type: String): MealType? = when (type) {
            NutritionNotificationManager.TYPE_BREAKFAST -> MealType.BREAKFAST
            NutritionNotificationManager.TYPE_LUNCH -> MealType.LUNCH
            NutritionNotificationManager.TYPE_DINNER -> MealType.DINNER
            else -> null
        }
    }
}
