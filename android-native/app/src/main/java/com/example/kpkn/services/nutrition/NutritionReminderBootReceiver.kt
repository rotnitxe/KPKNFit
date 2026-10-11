package com.example.kpkn.services.nutrition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Restaura los recordatorios de comida y el chequeo de macros cuando el sistema deja las alarmas sin programar:
 * reinicio del teléfono, actualización de la app y cambio de zona horaria (WP-U14 / C10). Antes no existía ningún
 * receiver de arranque para Nutrición y las alarmas quedaban muertas hasta abrir la app.
 *
 * Mismo patrón que `WorkoutReminderBootReceiver`: `goAsync()` + corrutina en IO, leyendo Room directamente (sin
 * arrancar el repositorio de nutrición dentro del broadcast). Según los ajustes vigentes programa la próxima
 * ocurrencia de cada aviso o cancela los deshabilitados.
 */
class NutritionReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val appContext = context.applicationContext
        val now = ZonedDateTime.now(zoneFor(intent))
        // goAsync() devuelve null si alguien invoca onReceive a mano (pruebas): finish() es opcional.
        val pendingResult: BroadcastReceiver.PendingResult? = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                restoreReminders(appContext, now = now)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to restore the nutrition reminders after $action", t)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NutritionReminderBoot"

        /** Acciones del sistema tras las cuales las alarmas faltan o apuntan a la zona horaria equivocada. */
        internal val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )

        /**
         * Realinea las alarmas con los ajustes guardados en Room. [database] y [now] son los puntos de inyección
         * (mismo patrón que `CompetitionReminderBootReceiver.reschedulePersistedRecords`): en producción son la base
         * real y el reloj; las pruebas pasan una base en memoria y un instante fijo.
         */
        internal suspend fun restoreReminders(
            context: Context,
            database: KpknDatabase = KpknDatabase.getInstance(context),
            now: ZonedDateTime = ZonedDateTime.now(),
        ) {
            val settings = database.settingsDao().get()?.toSettings() ?: Settings()
            val manager = NutritionNotificationManager(context)
            manager.createChannels()
            manager.syncReminders(settings, now)
        }

        /**
         * Zona con la que programar. `TIMEZONE_CHANGED` trae la nueva zona en el extra `time-zone`; el default de la
         * JVM puede tardar en actualizarse en un proceso que ya estaba vivo, así que se prefiere el extra. Sin extra
         * (arranque, actualización) o con uno ilegible vale la zona del sistema.
         */
        internal fun zoneFor(intent: Intent): ZoneId {
            val id = intent.getStringExtra(Intent.EXTRA_TIMEZONE) ?: return ZoneId.systemDefault()
            return try {
                ZoneId.of(id)
            } catch (_: DateTimeException) {
                ZoneId.systemDefault()
            }
        }
    }
}
