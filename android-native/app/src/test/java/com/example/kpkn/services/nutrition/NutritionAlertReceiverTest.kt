package com.example.kpkn.services.nutrition

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.DailyGoalSnapshotEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionActiveStateEntity
import com.example.kpkn.data.db.NutritionLogEntity
import com.example.kpkn.data.db.SettingsEntity
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.NutritionTrackingChoice
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * WP-U14 / C10: el receiver de alarmas de Nutrición lee ROOM (no los StateFlow del repositorio, que en un proceso
 * recién creado por el broadcast están vacíos) y reprograma la siguiente alarma de un solo uso.
 *
 * Casi todo se ejercita con `handleAlarm` (lo que hace `onReceive` salvo `goAsync` + corrutina) sobre una base en
 * memoria y un reloj fijo; un caso recorre `onReceive` completo con la base real. Ids de notificación de siempre:
 * 5001/5002/5003 comidas, 5010 macros, 5020 medición.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NutritionAlertReceiverTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase

    private val zone = ZoneId.of("America/Santiago")
    private val today = "2026-10-03"

    // Sábado 2026-10-03 13:05 en Santiago: acaba de pasar la hora del almuerzo (13:00).
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 10, 3, 13, 5, 0, 0, zone)

    // La misma noche a las 20:35: acaba de pasar el chequeo de macros (20:30).
    private val night: ZonedDateTime = ZonedDateTime.of(2026, 10, 3, 20, 35, 0, 0, zone)

    /** Recordatorios activos y una meta de proteína de 150 g en los ajustes (suficiente para el chequeo de macros). */
    private val remindersOn = Settings(mealReminderEnabled = true, dailyProteinGoal = 150)

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        // Sin repositorios: otras pruebas pudieron dejarlos inicializados y aquí se afirma que el receiver no los arranca.
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationManager().cancelAll()
        db = KpknDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
        ShadowAlarmManager.reset()
    }

    // ─── Utilidades ───────────────────────────────────────────────────────

    private fun notificationManager() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Ids de las notificaciones de Nutrición publicadas, en orden. */
    private fun postedIds(): List<Int> {
        val shadow = shadowOf(notificationManager())
        return listOf(5001, 5002, 5003, 5010, 5020).filter { shadow.getNotification(it) != null }
    }

    private fun alarms() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms

    // `operation` está marcado como obsoleto en Robolectric pero no tiene reemplazo: es la única forma de leer el PendingIntent.
    @Suppress("DEPRECATION")
    private fun alarmType(alarm: ShadowAlarmManager.ScheduledAlarm): String? =
        shadowOf(alarm.operation).savedIntent.getStringExtra(NutritionNotificationManager.EXTRA_NOTIF_TYPE)

    private fun millis(day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun seedSettings(settings: Settings) = runBlocking { db.settingsDao().upsert(settings.toEntity()) }

    private fun seedLog(
        id: String,
        mealType: MealType,
        date: String = "${today}T12:00:00.000Z",
        status: NutritionStatus = NutritionStatus.CONSUMED,
        protein: Double = 0.0,
    ) = runBlocking {
        val food = LoggedFood(id = "food-$id", foodName = "Comida", amount = 100.0, calories = 100.0, protein = protein)
        db.nutritionDao().upsertLog(
            NutritionLog(id = id, date = date, mealType = mealType, foods = listOf(food), status = status).toEntity(),
        )
    }

    private fun fire(type: String, firedHour: Int = -1, firedMinute: Int = -1, at: ZonedDateTime = now) = runBlocking {
        NutritionAlertReceiver.handleAlarm(context, type, firedHour, firedMinute, database = db, now = at)
    }

    private fun fireMacroCheck() = fire(macroCheck, firedHour = 20, firedMinute = 30, at = night)

    /** Espera (hasta ~20 s reales) a que la corrutina de `onReceive` termine; sin System.nanoTime: Robolectric lo congela. */
    private fun awaitUntil(condition: () -> Boolean) {
        repeat(800) {
            if (condition()) return
            Thread.sleep(25)
        }
    }

    private val breakfast = NutritionNotificationManager.TYPE_BREAKFAST
    private val lunch = NutritionNotificationManager.TYPE_LUNCH
    private val dinner = NutritionNotificationManager.TYPE_DINNER
    private val macroCheck = NutritionNotificationManager.TYPE_MACRO_CHECK

    // ─── Recordatorios de comida ──────────────────────────────────────────

    @Test
    fun lunchReminderIsSilentWhenLunchIsAlreadyLoggedToday() {
        seedSettings(remindersOn)
        seedLog("lunch-1", MealType.LUNCH)

        fire(lunch)

        assertTrue(shadowOf(notificationManager()).allNotifications.isEmpty())
        // Aun sin avisar, la cadena diaria sigue: mañana a las 13:00.
        assertEquals(millis(4, 13, 0), alarms().single().triggerAtMs)
    }

    @Test
    fun lunchReminderFiresWithTheLunchIdWhenNothingIsLogged() {
        seedSettings(remindersOn)

        fire(lunch)

        val shadow = shadowOf(notificationManager())
        assertEquals(1, shadow.size())
        assertEquals(listOf(5002), postedIds())
        assertEquals(NutritionNotificationManager.CHANNEL_MEALS, shadow.getNotification(5002).channelId)
    }

    @Test
    fun eachReminderHasItsOwnIdAndOnlyItsOwnMealSuppressesIt() {
        seedSettings(remindersOn)
        seedLog("lunch-1", MealType.LUNCH)

        fire(breakfast)
        fire(lunch)
        fire(dinner)

        assertEquals(listOf(5001, 5003), postedIds())
    }

    @Test
    fun aLogWithAPlainIsoDateAlsoCounts() {
        seedSettings(remindersOn)
        seedLog("dinner-1", MealType.DINNER, date = today)

        fire(dinner)

        assertTrue(postedIds().isEmpty())
    }

    @Test
    fun aPlannedMealOrAnotherDaysMealDoesNotSuppressTheReminder() {
        seedSettings(remindersOn)
        seedLog("planned", MealType.LUNCH, status = NutritionStatus.PLANNED)
        seedLog("yesterday", MealType.LUNCH, date = "2026-10-02T12:00:00.000Z")
        seedLog("tomorrow", MealType.LUNCH, date = "2026-10-04T12:00:00.000Z")

        fire(lunch)

        assertEquals(listOf(5002), postedIds())
    }

    @Test
    fun anUnreadableLogDoesNotBreakTheReminder() {
        seedSettings(remindersOn)
        runBlocking {
            db.nutritionDao().upsertLog(
                NutritionLogEntity(
                    id = "corrupt", date = "${today}T12:00:00.000Z", mealType = "LUNCH", data = "{not json",
                ),
            )
        }

        fire(lunch)

        assertEquals(listOf(5002), postedIds())
    }

    // ─── Ajustes: avisos deshabilitados ───────────────────────────────────

    @Test
    fun aStaleAlarmDoesNothingAndIsCancelledWhenMealRemindersAreOff() {
        NutritionNotificationManager(context).scheduleMealReminders()
        assertEquals(3, alarms().size)
        seedSettings(Settings(mealReminderEnabled = false))

        fire(lunch)

        assertTrue(postedIds().isEmpty())
        assertEquals(setOf(breakfast, dinner), alarms().map { alarmType(it) }.toSet())
    }

    @Test
    fun withoutASettingsRowRemindersAreOff() {
        fire(lunch)
        fire(macroCheck)

        assertTrue(postedIds().isEmpty())
        assertTrue(alarms().isEmpty())
    }

    @Test
    fun skippedNutritionSilencesEverything() {
        seedSettings(remindersOn.copy(nutritionTrackingChoice = NutritionTrackingChoice.SKIPPED))

        fire(lunch)
        fire(macroCheck)

        assertTrue(postedIds().isEmpty())
        assertTrue(alarms().isEmpty())
    }

    @Test
    fun anUnknownAlarmTypeIsIgnored() {
        seedSettings(remindersOn)

        fire("weird")

        assertTrue(postedIds().isEmpty())
        assertTrue(alarms().isEmpty())
    }

    // ─── Reprogramación de la siguiente alarma ────────────────────────────

    @Test
    fun firingSchedulesTheNextOccurrenceAsAOneShotExactAlarm() {
        seedSettings(remindersOn)

        fire(lunch, firedHour = 13, firedMinute = 0)

        val alarm = alarms().single()
        assertEquals(lunch, alarmType(alarm))
        assertEquals(millis(4, 13, 0), alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType())
        assertEquals("nunca repetitiva", 0L, alarm.intervalMs)
        assertTrue(alarm.isAllowWhileIdle)
    }

    @Test
    fun theNextOccurrenceFollowsTheCurrentSettingsNotTheTimeTheAlarmWasSetWith() {
        seedSettings(remindersOn.copy(mealReminderLunch = "14:30"))

        fire(lunch, firedHour = 13, firedMinute = 0)

        // Son las 13:05 y el almuerzo ahora es a las 14:30: todavía cae hoy.
        assertEquals(millis(3, 14, 30), alarms().single().triggerAtMs)
    }

    @Test
    fun withoutExactAlarmPermissionTheNextOccurrenceUsesATenMinuteWindow() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        seedSettings(remindersOn)

        fire(lunch)

        val alarm = alarms().single()
        assertEquals(millis(4, 13, 0), alarm.triggerAtMs)
        assertEquals(10 * 60 * 1000L, alarm.windowLengthMs)
        assertFalse(alarm.isAllowWhileIdle)
        assertEquals(0L, alarm.intervalMs)
    }

    @Test
    fun unreadableSettingsKeepTheDailyChainAliveFromTheFiredTimeWithoutNotifying() {
        runBlocking { db.settingsDao().upsert(SettingsEntity(data = "{not json")) }

        fire(lunch, firedHour = 13, firedMinute = 0)

        assertTrue(postedIds().isEmpty())
        assertEquals(millis(4, 13, 0), alarms().single().triggerAtMs)
    }

    @Test
    fun unreadableSettingsWithoutTheFiredTimeScheduleNothing() {
        runBlocking { db.settingsDao().upsert(SettingsEntity(data = "{not json")) }

        fire(lunch)

        assertTrue(postedIds().isEmpty())
        assertTrue(alarms().isEmpty())
    }

    // ─── Chequeo de macros ────────────────────────────────────────────────

    @Test
    fun macroAlertIsSilentWhenNothingWasLoggedToday() {
        seedSettings(remindersOn) // meta de proteína en ajustes, pero ni una comida registrada hoy

        fireMacroCheck()

        assertTrue("un día sin registros no genera aviso de macros", postedIds().isEmpty())
        // La cadena diaria sigue: mañana a las 20:30.
        assertEquals(millis(4, 20, 30), alarms().single().triggerAtMs)
    }

    @Test
    fun yesterdaysAndPlannedMealsAloneAreStillADayWithoutLogs() {
        seedSettings(remindersOn)
        seedLog("yesterday", MealType.LUNCH, date = "2026-10-02T12:00:00.000Z", protein = 10.0)
        seedLog("planned", MealType.DINNER, status = NutritionStatus.PLANNED, protein = 10.0)

        fireMacroCheck()

        assertTrue(postedIds().isEmpty())
    }

    @Test
    fun macroAlertFiresForAPartialLogWithAMaterialDeficit() {
        seedSettings(remindersOn)
        seedLog("lunch", MealType.LUNCH, protein = 60.0) // 40% de 150 g: muy por debajo del 70% mínimo

        fireMacroCheck()

        val shadow = shadowOf(notificationManager())
        assertEquals(listOf(5010), postedIds())
        assertEquals(NutritionNotificationManager.CHANNEL_MACROS, shadow.getNotification(5010).channelId)
        // Y el chequeo de mañana a las 20:30 queda programado.
        assertEquals(millis(4, 20, 30), alarms().single().triggerAtMs)
    }

    @Test
    fun macroAlertIsSilentWhenTodaysMealsCoverTheGoal() {
        seedSettings(remindersOn)
        seedLog("lunch", MealType.LUNCH, protein = 120.0) // 80% de 150 g: por encima del 70% mínimo

        fireMacroCheck()

        assertTrue(postedIds().isEmpty())
    }

    @Test
    fun macroAlertOnlyCountsTodaysConsumedMeals() {
        seedSettings(remindersOn)
        seedLog("breakfast", MealType.BREAKFAST, protein = 30.0) // lo único consumido hoy: 20% de 150 g
        seedLog("yesterday", MealType.LUNCH, date = "2026-10-02T12:00:00.000Z", protein = 150.0)
        seedLog("planned", MealType.DINNER, status = NutritionStatus.PLANNED, protein = 150.0)

        fireMacroCheck()

        // Si el día anterior o lo planificado sumaran serían 330 g y no habría aviso.
        assertEquals(listOf(5010), postedIds())
    }

    @Test
    fun macroAlertNeedsRealGoals() {
        seedSettings(Settings(mealReminderEnabled = true)) // sin metas en ajustes ni plan activo
        seedLog("lunch", MealType.LUNCH, protein = 10.0)

        fireMacroCheck()

        assertTrue(postedIds().isEmpty())
    }

    @Test
    fun macroAlertNeedsMealRemindersOn() {
        seedSettings(remindersOn.copy(mealReminderEnabled = false))
        seedLog("lunch", MealType.LUNCH, protein = 10.0)

        fireMacroCheck()

        assertTrue(postedIds().isEmpty())
        assertTrue(alarms().isEmpty())
    }

    @Test
    fun trackingOnlyKeepsTheMealReminderButSilencesTheMacroAlert() {
        seedSettings(remindersOn.copy(nutritionTrackingOnly = true))
        seedLog("breakfast", MealType.BREAKFAST, protein = 10.0) // aun con comidas registradas: no hay metas

        fireMacroCheck()
        assertTrue("sin metas no hay déficit que avisar", postedIds().isEmpty())
        // La cadena sigue viva (mañana 20:30) para cuando el usuario vuelva a un plan con metas.
        assertEquals(millis(4, 20, 30), alarms().single().triggerAtMs)

        fire(lunch)
        assertEquals(listOf(5002), postedIds())
    }

    @Test
    fun macroAlertUsesTheActivePlanAndTheDaysSnapshotWins() {
        seedSettings(Settings(mealReminderEnabled = true))
        runBlocking {
            val dao = db.nutritionDao()
            dao.upsertPlan(NutritionPlan(id = "plan-1", name = "Plan", proteinGoal = 200, isActive = true).toEntity())
            dao.upsertActiveState(NutritionActiveStateEntity(activePlanId = "plan-1"))
        }
        seedLog("lunch", MealType.LUNCH, protein = 80.0)

        // Meta del plan activo: 200 g -> 80 g son el 40%.
        fireMacroCheck()
        assertEquals(listOf(5010), postedIds())
        notificationManager().cancelAll()

        // El snapshot de hoy (100 g) manda sobre el plan actual: 80 g son el 80%.
        runBlocking {
            db.nutritionDao().insertDailyGoalSnapshot(
                DailyGoalSnapshotEntity(
                    date = today, planId = "plan-1", calorieTargetKcal = null, proteinGoalG = 100, carbGoalG = null,
                    fatGoalG = null, direction = null, calculationOrigin = "PLAN", capturedAtEpochMs = 0L,
                ),
            )
        }
        fireMacroCheck()
        assertTrue(postedIds().isEmpty())
    }

    // ─── Sin repositorios, y la medición intacta ──────────────────────────

    @Test
    fun theReceiverNeverStartsTheRepositories() {
        seedSettings(remindersOn)
        seedLog("lunch", MealType.LUNCH)

        fire(lunch)
        fire(macroCheck)

        assertThrows(IllegalStateException::class.java) { NutritionRepository.getInstance() }
        assertThrows(IllegalStateException::class.java) { ProgramRepository.getInstance() }
    }

    @Test
    fun theLunchBroadcastRunsThroughOnReceiveOnTheRealDatabaseAndSchedulesTheNextAlarm() {
        // onReceive -> goAsync() (null al invocarlo a mano) -> corrutina en IO -> KpknDatabase real, con el reloj real.
        val realDb = KpknDatabase.getInstance(context)
        try {
            runBlocking { realDb.settingsDao().upsert(remindersOn.toEntity()) }
            val intent = Intent(context, NutritionAlertReceiver::class.java)
                .putExtra(NutritionNotificationManager.EXTRA_NOTIF_TYPE, lunch)
                .putExtra(NutritionNotificationManager.EXTRA_HOUR, 13)
                .putExtra(NutritionNotificationManager.EXTRA_MINUTE, 0)

            NutritionAlertReceiver().onReceive(context, intent)

            awaitUntil { postedIds().isNotEmpty() && alarms().isNotEmpty() }
            assertEquals(listOf(5002), postedIds())
            assertEquals(lunch, alarmType(alarms().single()))
        } finally {
            KpknDatabase.closeInstance()
        }
    }

    @Test
    fun theMeasurementBroadcastStillPostsItsOwnNotification() {
        val intent = Intent(context, NutritionAlertReceiver::class.java)
            .putExtra(NutritionNotificationManager.EXTRA_NOTIF_TYPE, NutritionNotificationManager.TYPE_MEASUREMENT)

        NutritionAlertReceiver().onReceive(context, intent)

        assertEquals(listOf(5020), postedIds())
    }
}
