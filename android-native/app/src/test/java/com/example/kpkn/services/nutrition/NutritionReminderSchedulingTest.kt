package com.example.kpkn.services.nutrition

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.NutritionTrackingChoice
import com.example.kpkn.data.models.Settings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * WP-U14 / C10: programación de los recordatorios de comida y del chequeo de macros.
 *
 * - Siempre alarmas de UN solo uso (jamás `setRepeating`): `setExactAndAllowWhileIdle` si se pueden programar alarmas
 *   exactas (siempre en API < 31) y, si no, `setWindow` de 10 min.
 * - El receiver de arranque las restaura desde los ajustes guardados en Room tras reinicio, actualización o cambio de
 *   zona horaria, o las cancela si el aviso está deshabilitado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NutritionReminderSchedulingTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase

    private val zone = ZoneId.of("America/Santiago")

    // Sábado 2026-10-03 13:05 en Santiago.
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 10, 3, 13, 5, 0, 0, zone)

    private val breakfast = NutritionNotificationManager.TYPE_BREAKFAST
    private val lunch = NutritionNotificationManager.TYPE_LUNCH
    private val dinner = NutritionNotificationManager.TYPE_DINNER
    private val macroCheck = NutritionNotificationManager.TYPE_MACRO_CHECK

    private val customTimes = Settings(
        mealReminderEnabled = true,
        mealReminderBreakfast = "07:15",
        mealReminderLunch = "12:45",
        mealReminderDinner = "21:00",
        dailyProteinGoal = 150,
    )

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        db = KpknDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
        ShadowAlarmManager.reset()
    }

    // ─── Utilidades ───────────────────────────────────────────────────────

    private fun alarms() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms

    // `operation` está marcado como obsoleto en Robolectric pero no tiene reemplazo: es la única forma de leer el PendingIntent.
    @Suppress("DEPRECATION")
    private fun alarmType(alarm: ShadowAlarmManager.ScheduledAlarm): String? =
        shadowOf(alarm.operation).savedIntent.getStringExtra(NutritionNotificationManager.EXTRA_NOTIF_TYPE)

    private fun alarmsByType() = alarms().associateBy { alarmType(it) }

    private fun millis(day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun localTimeOf(alarm: ShadowAlarmManager.ScheduledAlarm): LocalTime =
        Instant.ofEpochMilli(alarm.triggerAtMs).atZone(ZoneId.systemDefault()).toLocalTime()

    // ─── Alarmas de un solo uso ───────────────────────────────────────────

    @Test
    fun scheduleMealRemindersCreatesThreeOneShotExactAlarmsAtTheConfiguredLocalTimes() {
        val before = Instant.now().toEpochMilli()
        NutritionNotificationManager(context).scheduleMealReminders("08:00", "13:00", "20:00")
        val after = Instant.now().toEpochMilli()

        val byType = alarmsByType()
        assertEquals(setOf(breakfast, lunch, dinner), byType.keys)
        mapOf(breakfast to LocalTime.of(8, 0), lunch to LocalTime.of(13, 0), dinner to LocalTime.of(20, 0))
            .forEach { (type, time) ->
                val alarm = byType.getValue(type)
                assertEquals("$type: nunca repetitiva", 0L, alarm.intervalMs)
                assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType())
                assertTrue("$type: exacta y activa en Doze", alarm.isAllowWhileIdle)
                assertEquals(time, localTimeOf(alarm))
                assertTrue("$type: próximo disparo en el futuro", alarm.triggerAtMs > before)
                assertTrue("$type: a lo sumo en un día", alarm.triggerAtMs <= after + 25 * 3_600_000L)
            }
    }

    @Test
    fun scheduleDailyMacroCheckDefaultsToTwentyThirtyAndIsOneShot() {
        NutritionNotificationManager(context).scheduleDailyMacroCheck()

        val alarm = alarms().single()
        assertEquals(macroCheck, alarmType(alarm))
        assertEquals(0L, alarm.intervalMs)
        assertEquals(LocalTime.of(20, 30), localTimeOf(alarm))
    }

    @Test
    fun cancelRemovesTheMealAlarmsAndTheMacroCheckIndependently() {
        val manager = NutritionNotificationManager(context)
        manager.scheduleMealReminders()
        manager.scheduleDailyMacroCheck()
        assertEquals(4, alarms().size)

        manager.cancelMealReminders()
        assertEquals(setOf(macroCheck), alarmsByType().keys)

        manager.cancelDailyMacroCheck()
        assertTrue(alarms().isEmpty())
    }

    @Test
    fun withoutExactAlarmPermissionTheAlarmIsATenMinuteWindow() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        NutritionNotificationManager(context).syncReminders(customTimes, now)

        assertEquals(4, alarms().size)
        alarms().forEach { alarm ->
            assertEquals(10 * 60 * 1000L, alarm.windowLengthMs)
            assertFalse(alarm.isAllowWhileIdle)
            assertEquals(0L, alarm.intervalMs)
        }
    }

    @Test
    @Config(sdk = [28])
    fun belowApi31ExactAlarmsAreAlwaysAllowed() {
        NutritionNotificationManager(context).syncReminders(customTimes, now)

        assertEquals(4, alarms().size)
        alarms().forEach { alarm ->
            assertTrue(alarm.isAllowWhileIdle)
            assertEquals(0L, alarm.intervalMs)
        }
    }

    // ─── Alinear con los ajustes ──────────────────────────────────────────

    @Test
    fun syncRemindersSchedulesTheNextLocalOccurrenceOfEachEnabledAlarm() {
        NutritionNotificationManager(context).syncReminders(customTimes, now)

        val byType = alarmsByType()
        assertEquals(setOf(breakfast, lunch, dinner, macroCheck), byType.keys)
        assertEquals(millis(4, 7, 15), byType.getValue(breakfast).triggerAtMs) // ya pasó hoy: mañana
        assertEquals(millis(4, 12, 45), byType.getValue(lunch).triggerAtMs) // pasó a las 12:45: mañana
        assertEquals(millis(3, 21, 0), byType.getValue(dinner).triggerAtMs) // todavía hoy
        assertEquals(millis(3, 20, 30), byType.getValue(macroCheck).triggerAtMs) // todavía hoy
    }

    @Test
    fun syncRemindersCancelsEverythingWhenRemindersAreOff() {
        val manager = NutritionNotificationManager(context)
        manager.syncReminders(customTimes, now)
        assertEquals(4, alarms().size)

        manager.syncReminders(customTimes.copy(mealReminderEnabled = false), now)

        assertTrue(alarms().isEmpty())
    }

    @Test
    fun trackingOnlyKeepsEveryAlarmSoTheChainSurvivesAModeSwitch() {
        // El receiver decide al disparar si hay algo que avisar (sin metas, no): las alarmas siguen programadas.
        NutritionNotificationManager(context).syncReminders(customTimes.copy(nutritionTrackingOnly = true), now)

        assertEquals(setOf(breakfast, lunch, dinner, macroCheck), alarmsByType().keys)
    }

    @Test
    fun skippedNutritionCancelsEverything() {
        val manager = NutritionNotificationManager(context)
        manager.syncReminders(customTimes, now)

        manager.syncReminders(customTimes.copy(nutritionTrackingChoice = NutritionTrackingChoice.SKIPPED), now)

        assertTrue(alarms().isEmpty())
    }

    @Test
    fun malformedTimesInSettingsNeverThrowAndAreClamped() {
        val garbled = customTimes.copy(mealReminderLunch = "99:99", mealReminderDinner = "abc", mealReminderBreakfast = "")

        NutritionNotificationManager(context).syncReminders(garbled, now)

        val byType = alarmsByType()
        assertEquals(millis(3, 23, 59), byType.getValue(lunch).triggerAtMs)
        assertEquals(millis(4, 8, 0), byType.getValue(dinner).triggerAtMs) // «abc» -> 08:00 de siempre
        assertEquals(millis(4, 8, 0), byType.getValue(breakfast).triggerAtMs)
    }

    // ─── Receiver de arranque ─────────────────────────────────────────────

    @Test
    fun bootRestoresEveryAlarmFromTheSettingsStoredInRoom() = runBlocking {
        db.settingsDao().upsert(customTimes.toEntity())

        NutritionReminderBootReceiver.restoreReminders(context, db, now)

        val byType = alarmsByType()
        assertEquals(setOf(breakfast, lunch, dinner, macroCheck), byType.keys)
        assertEquals(millis(4, 7, 15), byType.getValue(breakfast).triggerAtMs)
        assertEquals(millis(3, 21, 0), byType.getValue(dinner).triggerAtMs)
        assertEquals(millis(3, 20, 30), byType.getValue(macroCheck).triggerAtMs)
    }

    @Test
    fun bootCancelsWhateverIsLeftWhenTheRemindersAreOff() = runBlocking {
        val manager = NutritionNotificationManager(context)
        manager.scheduleMealReminders()
        manager.scheduleDailyMacroCheck()
        assertEquals(4, alarms().size)

        // Sin fila de ajustes (instalación nueva) rigen los valores por defecto: recordatorios apagados.
        NutritionReminderBootReceiver.restoreReminders(context, db, now)

        assertTrue(alarms().isEmpty())
    }

    @Test
    fun theBootReceiverHandlesBootUpdateAndTimezoneChangesOnly() {
        assertEquals(
            setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED),
            NutritionReminderBootReceiver.HANDLED_ACTIONS,
        )

        NutritionReminderBootReceiver().onReceive(context, Intent("com.example.kpkn.SOMETHING_ELSE"))
        NutritionReminderBootReceiver().onReceive(context, Intent())

        assertTrue(alarms().isEmpty())
    }

    @Test
    fun theTimezoneBroadcastCarriesTheNewZoneAndItWinsOverTheProcessDefault() {
        val tokyo = Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra(Intent.EXTRA_TIMEZONE, "Asia/Tokyo")
        assertEquals(ZoneId.of("Asia/Tokyo"), NutritionReminderBootReceiver.zoneFor(tokyo))

        val noExtra = Intent(Intent.ACTION_BOOT_COMPLETED)
        assertEquals(ZoneId.systemDefault(), NutritionReminderBootReceiver.zoneFor(noExtra))

        val unreadable = Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra(Intent.EXTRA_TIMEZONE, "Not/AZone")
        assertEquals(ZoneId.systemDefault(), NutritionReminderBootReceiver.zoneFor(unreadable))
    }
}
