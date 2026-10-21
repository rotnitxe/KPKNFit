package com.example.kpkn.data.media.pose

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.os.Process
import com.example.kpkn.BuildConfig

/** Dónde anota la sonda sus marcas: SharedPreferences en la app y un mapa en las pruebas. */
interface GpuProbeStore {
    /** El valor guardado, o `null` si no hay ninguno. */
    fun getInt(key: String): Int?

    /** SÍNCRONO: la marca tiene que estar en disco antes de que la GPU pueda matar el proceso. */
    fun putInt(key: String, value: Int)

    /** Síncrono, como [putInt]. */
    fun remove(key: String)
}

internal class SharedPreferencesGpuProbeStore(private val prefs: SharedPreferences) : GpuProbeStore {
    override fun getInt(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).commit()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }
}

/** Cuánto pesa el dispositivo en este instante, para el modo degradado. [thermalStatus] vale −1 si no se conoce (API < 29). */
data class DeviceLoad(val isLowRamDevice: Boolean, val totalMemBytes: Long, val thermalStatus: Int)

/** Respuesta de [GpuProbe.beginGpuAttempt]: usar la GPU o, si [useGpu] es `false`, la CPU por el motivo [reason]. */
class GpuDecision(val useGpu: Boolean, val reason: String?)

/**
 * Sonda del delegado de GPU de MediaPipe (§9 punto 3). Una GPU defectuosa suele matar el proceso con un fallo nativo que
 * ningún `try` atrapa, y el siguiente arranque volvería a caer en lo mismo. Por eso, antes de crear con GPU:
 * - si el dispositivo no llega a GLES 3.1 (`reqGlEsVersion` ≥ 0x30001), se usa la CPU;
 * - se escriben `gpu_probe=<versionCode>` y `gpu_probe_pid` (síncronos), y se borran tras la 1.ª detección correcta;
 * - una marca de la MISMA versionCode veta la GPU solo si el historial confirma un fallo nativo, o tras dos salidas
 *   desconocidas seguidas (incluido ANR). Un fallo Java u otro cierre del sistema no veta y reinicia esos avisos;
 * - una detección correcta o una marca de otra versión reinician los avisos. El veto guardado (`gpu_vetoed`) dura toda su
 *   versionCode; una versión nueva vuelve a probar.
 * Una excepción atrapada (crear o detectar con GPU) no veta: se cierra el intento con [abandonGpuAttempt] y se recrea en CPU.
 *
 * Con varios intentos a la vez en el proceso, la marca solo se borra cuando termina el último.
 * El modo degradado ([recommendedPoseStride] = 2) cubre los móviles justos: `isLowRamDevice`, menos de 3 GB de RAM o un
 * estado térmico ≥ MODERATE (API 29+).
 */
class GpuProbe internal constructor(
    private val store: GpuProbeStore,
    private val versionCode: Int,
    private val glEsVersion: Int,
    private val load: () -> DeviceLoad,
    private val exitHistory: GpuExitHistory = GpuExitHistory { null },
    private val pid: Int = Process.myPid(),
) {
    /** Intentos con GPU abiertos en ESTE proceso (la marca de otro proceso no cuenta). */
    private var inFlight = 0
    private var lastExitCause: GpuExitCause? = null

    /** `true` si la GPU está vetada para esta versionCode. */
    val isVetoed: Boolean
        @Synchronized get() = store.getInt(KEY_VETOED) == versionCode

    /** 2 si el dispositivo va justo de RAM o está caliente (pose cada 2 fotogramas); si no, 1. */
    val recommendedPoseStride: Int get() = strideFor(load())

    /** Una línea para depurar: GLES, veto, avisos, última causa de salida, stride y carga del dispositivo. */
    val diagnostics: String
        @Synchronized get() {
            val l = load()
            return "glEs=${describeGl(glEsVersion)} versionCode=$versionCode vetada=$isVetoed " +
                "marca=${store.getInt(KEY_PROBE)} avisos=${store.getInt(KEY_STRIKES) ?: 0} ultimaCausa=$lastExitCause " +
                "stride=${strideFor(l)} lowRam=${l.isLowRamDevice} " +
                "ramMb=${l.totalMemBytes shr 20} termico=${l.thermalStatus}"
        }

    /**
     * Antes de crear el landmarker con GPU. Con `useGpu = true` deja la marca escrita y hay que cerrar el intento con
     * [confirmGpuWorks] o [abandonGpuAttempt]; con `false`, usa la CPU por [GpuDecision.reason] y no hay nada que cerrar.
     */
    @Synchronized
    fun beginGpuAttempt(): GpuDecision {
        if (glEsVersion < MIN_GL_ES_VERSION) {
            return GpuDecision(false, "GLES ${describeGl(glEsVersion)} < 3.1")
        }
        // Tras un veto o un abandono puede no quedar marca: los avisos tampoco deben pasar a otro APK.
        if (store.getInt(KEY_STRIKES_VERSION)?.let { it != versionCode } == true) clearStrikes()
        val pending = store.getInt(KEY_PROBE)
        if (pending != null && inFlight == 0) {
            // Ninguno de los nuestros sigue abierto: consulta cómo terminó el proceso que dejó la marca.
            if (pending == versionCode) {
                lastExitCause = store.getInt(KEY_PROBE_PID)?.let(exitHistory::causeOf)
                when (lastExitCause) {
                    GpuExitCause.NATIVE_CRASH -> store.putInt(KEY_VETOED, versionCode)
                    GpuExitCause.JAVA_CRASH, GpuExitCause.OTHER -> clearStrikes()
                    GpuExitCause.ANR, null -> {
                        val strikes = (store.getInt(KEY_STRIKES) ?: 0).coerceIn(0, MAX_STRIKES - 1) + 1
                        store.putInt(KEY_STRIKES_VERSION, versionCode)
                        store.putInt(KEY_STRIKES, strikes)
                        if (strikes >= MAX_STRIKES) store.putInt(KEY_VETOED, versionCode)
                    }
                }
            } else {
                clearStrikes()
            }
            clearMarker()
        }
        if (store.getInt(KEY_VETOED) == versionCode) {
            return GpuDecision(false, "GPU vetada en la versión $versionCode")
        }
        if (inFlight == 0) {
            // Primero el pid: si se corta entre escrituras, nunca queda una marca nueva con el pid de otro intento.
            store.putInt(KEY_PROBE_PID, pid)
            store.putInt(KEY_PROBE, versionCode)
        }
        inFlight++
        return GpuDecision(true, null)
    }

    /** Tras la 1.ª detección correcta con GPU: reinicia los avisos y cierra el intento (y la marca si era el último). */
    @Synchronized
    fun confirmGpuWorks() {
        clearStrikes()
        endAttempt()
    }

    /** Cierra un intento sin veto: la GPU lanzó una excepción atrapada (se pasa a CPU) o el estimador se cerró sin detectar. */
    @Synchronized
    fun abandonGpuAttempt() = endAttempt()

    private fun endAttempt() {
        if (inFlight > 0) inFlight--
        if (inFlight == 0) clearMarker()
    }

    private fun clearMarker() {
        store.remove(KEY_PROBE)
        store.remove(KEY_PROBE_PID)
    }

    private fun clearStrikes() {
        store.remove(KEY_STRIKES)
        store.remove(KEY_STRIKES_VERSION)
    }

    companion object {
        /** GLES 3.1 en el formato de `ConfigurationInfo.reqGlEsVersion` (mayor en los 16 bits altos, menor en los bajos). */
        const val MIN_GL_ES_VERSION = 0x30001

        /** Por debajo de esta RAM total, pose cada 2 fotogramas. */
        const val LOW_RAM_BYTES = 3L * 1024 * 1024 * 1024

        /** `PowerManager.THERMAL_STATUS_MODERATE` (API 29), para no depender del nivel de API al compilar la lógica. */
        const val THERMAL_MODERATE = 2

        const val KEY_PROBE = "gpu_probe"
        const val KEY_PROBE_PID = "gpu_probe_pid"
        const val KEY_STRIKES = "gpu_strikes"
        const val KEY_VETOED = "gpu_vetoed"
        private const val MAX_STRIKES = 2
        private const val KEY_STRIKES_VERSION = "gpu_strikes_version"
        private const val PREFS_NAME = "vt_gpu_probe"

        /** El stride de pose que recomienda [load]: 2 con poca RAM o calor, 1 en el resto. */
        fun strideFor(load: DeviceLoad): Int {
            val tight = load.isLowRamDevice || (load.totalMemBytes in 1 until LOW_RAM_BYTES)
            return if (tight || load.thermalStatus >= THERMAL_MODERATE) 2 else 1
        }

        private fun describeGl(version: Int): String = "${version ushr 16}.${version and 0xFFFF}"

        @Volatile
        private var instance: GpuProbe? = null

        /** La sonda del proceso, con las SharedPreferences `vt_gpu_probe` y el `versionCode` de este APK. */
        fun get(context: Context): GpuProbe = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(app: Context): GpuProbe {
            val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return GpuProbe(
                store = SharedPreferencesGpuProbeStore(prefs),
                versionCode = BuildConfig.VERSION_CODE,
                glEsVersion = activityManager.deviceConfigurationInfo.reqGlEsVersion,
                load = { readLoad(app, activityManager) },
                exitHistory = AndroidGpuExitHistory(app),
            )
        }

        private fun readLoad(app: Context, activityManager: ActivityManager): DeviceLoad {
            val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (app.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
            } else {
                -1
            }
            return DeviceLoad(activityManager.isLowRamDevice, memory.totalMem, thermal)
        }
    }
}
