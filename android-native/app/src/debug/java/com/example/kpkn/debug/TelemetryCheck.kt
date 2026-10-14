package com.example.kpkn.debug

import android.app.job.JobScheduler
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.kpkn.BuildConfig
import com.example.kpkn.data.media.pose.MediaPipePoseEstimators
import com.example.kpkn.data.media.pose.PoseEstimator
import com.example.kpkn.data.media.pose.PoseEstimatorReport
import com.example.kpkn.data.media.pose.PoseModel
import com.example.kpkn.data.media.pose.RgbaFrame
import com.example.kpkn.data.media.video.FrameNeeds
import com.example.kpkn.data.media.video.VideoFrameSources
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * SOLO DEPURACIÓN (no se integra): modo `telemetry_check` de [TechniqueBenchActivity] (VT4b). Comprueba si MediaPipe saca algo
 * del teléfono. El código de `tasks-core` 1.1.0 crea sin condiciones un `RemoteLoggingClient` (datatransport + `CCTDestination`,
 * fuente «COREML_ON_DEVICE_SOLUTIONS») que anota inicio, informes periódicos y fin de sesión; aquí se mira qué pasa de verdad.
 *
 * Ejecuta 100 detecciones con `mp-full` sobre el primer clip de `in` (o uno sintético), cierra el estimador (que cierra la
 * sesión) y espera [waitSeconds] s (datatransport sube los eventos de prioridad normal pasados unos 30 s). Escribe
 * `telemetry.json` en `out` con:
 * - (a) las líneas del logcat PROPIO del proceso (`logcat -d -v time`) que casan con `TRuntime|TransportRuntime|datatransport|
 *   clearcut|play.googleapis` sin distinguir mayúsculas, y las de `RemoteLoggingClient|TasksStatsProtoLogger`;
 * - (b) `TrafficStats.getUidTxBytes/RxBytes` del UID antes, después de las detecciones y a lo largo de la espera;
 * - (c) si se pueden cargar `TransportRuntime` y otras clases de telemetría (`Class.forName`);
 * - (d) los trabajos pendientes de `JobScheduler` y (e) la base de eventos de datatransport (tablas, filas, texto imprimible
 *   de los datos en cola), que dicen qué se encoló aunque no se haya subido todavía.
 */
internal class TelemetryCheck(
    private val context: Context,
    private val layout: BenchLayout,
    private val waitSeconds: Int,
) {
    private class StopCheck : CancellationException("100 detecciones hechas")

    suspend fun run(): String {
        val out = File(layout.outDir, TELEMETRY_FILE)
        runCatching { out.delete() }
        val uid = Process.myUid()
        val report = JSONObject()
            .put("modo", "telemetry_check")
            .put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("api", Build.VERSION.SDK_INT)
            .put("paquete", context.packageName)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("enLinea", isOnline())
            .put("esperaSegundos", waitSeconds)
        var ok = false
        try {
            report.put("clases", classPresence())
            val startMs = System.currentTimeMillis()
            val txStart = TrafficStats.getUidTxBytes(uid)
            val rxStart = TrafficStats.getUidRxBytes(uid)
            report.put("trabajosAntes", pendingJobs())
            report.put("baseTransporteAntes", transportDatabase())
            val logBefore = matchingLogLines(TELEMETRY_PATTERN).size

            val clip = firstClip()
            report.put("clip", clip.name)
            val estimator = withContext(Dispatchers.Default) {
                MediaPipePoseEstimators.create(context, RunningMode.VIDEO, PoseModel.Full, preferGpu = true)
            }
            val detection = try {
                report.put("delegado", estimator.engine.delegate)
                report.put("notaDelegado", (estimator as? PoseEstimatorReport)?.delegateNote ?: JSONObject.NULL)
                detect(estimator, clip, DETECTIONS)
            } finally {
                runCatching { estimator.close() }
            }
            report.put("detecciones", detection.count)
            report.put("deteccionesConPose", detection.withPose)
            report.put("msPorDeteccion", detection.timesMs.stats(skipFirst = true))
            val txAfter = TrafficStats.getUidTxBytes(uid)
            val rxAfter = TrafficStats.getUidRxBytes(uid)

            // La espera: los eventos de prioridad normal se suben pasados ~30 s con una red disponible.
            val series = JSONArray()
            val sampleEveryMs = 5_000L
            var waited = 0L
            while (waited < waitSeconds * 1_000L) {
                delay(sampleEveryMs)
                waited += sampleEveryMs
                series.put(
                    JSONObject()
                        .put("s", waited / 1_000L)
                        .put("tx", TrafficStats.getUidTxBytes(uid))
                        .put("rx", TrafficStats.getUidRxBytes(uid))
                        .put("trabajos", pendingJobs().length())
                        .put("eventosEnBase", queuedEvents()),
                )
            }
            val txEnd = TrafficStats.getUidTxBytes(uid)
            val rxEnd = TrafficStats.getUidRxBytes(uid)
            val unsupported = txStart == TrafficStats.UNSUPPORTED.toLong()
            report.put(
                "trafico",
                JSONObject()
                    .put("soportado", !unsupported)
                    .put("txAlEmpezar", txStart)
                    .put("txTrasDetecciones", txAfter)
                    .put("txAlFinal", txEnd)
                    .put("rxAlEmpezar", rxStart)
                    .put("rxTrasDetecciones", rxAfter)
                    .put("rxAlFinal", rxEnd)
                    .put("txDuranteDetecciones", if (unsupported) JSONObject.NULL else txAfter - txStart)
                    .put("txDuranteTodo", if (unsupported) JSONObject.NULL else txEnd - txStart)
                    .put("segundosTotales", (System.currentTimeMillis() - startMs) / 1_000L)
                    .put("serie", series),
            )
            report.put("trabajosDespues", pendingJobs())
            report.put("baseTransporteDespues", transportDatabase())
            val logAfter = matchingLogLines(TELEMETRY_PATTERN)
            report.put(
                "logcat",
                JSONObject()
                    .put("patron", TELEMETRY_PATTERN.pattern)
                    .put("coincidenciasAntes", logBefore)
                    .put("coincidenciasDespues", logAfter.size)
                    .put("lineas", JSONArray(logAfter.takeLast(MAX_LOG_LINES)))
                    .put("lineasMediaPipe", JSONArray(matchingLogLines(MEDIAPIPE_PATTERN).takeLast(MAX_LOG_LINES))),
            )
            ok = true
        } catch (cancel: CancellationException) {
            report.put("error", "cancelado: la actividad se cerró antes de terminar")
            throw cancel
        } catch (error: Throwable) {
            report.put("error", describeError(error))
        } finally {
            report.put("ok", ok)
            val tmp = File(layout.outDir, "$TELEMETRY_FILE.tmp")
            tmp.writeText(report.toString(2))
            check(tmp.renameTo(out)) { "No se pudo renombrar $TELEMETRY_FILE" }
            Log.i(TAG, "telemetry_check ok=$ok")
        }
        val traffic = report.optJSONObject("trafico")
        return "telemetry_check ${if (ok) "OK" else "CON FALLOS"} · tx=${traffic?.opt("txDuranteTodo")} B · " +
            "eventos en cola=${report.optJSONObject("baseTransporteDespues")?.optJSONObject("tablas")?.opt("events")}"
    }

    private class Detection(val count: Int, val withPose: Int, val timesMs: Samples)

    /** [target] detecciones, repitiendo el clip si hace falta; el tiempo sube 33 ms por detección (no depende del PTS). */
    private suspend fun detect(estimator: PoseEstimator, clip: File, target: Int): Detection {
        val times = Samples()
        var count = 0
        var withPose = 0
        while (count < target) {
            val before = count
            VideoFrameSources.open(clip).use { source ->
                try {
                    source.decode(fromUs = 0L, targetLongEdge = 720, needs = { _, _ -> FrameNeeds.RGBA }) { frame ->
                        if (count >= target) throw StopCheck()
                        val startNs = System.nanoTime()
                        val poses = estimator.estimate(RgbaFrame(frame.width, frame.height, checkNotNull(frame.rgba)), count * 33L + 1L)
                        times.add((System.nanoTime() - startNs) / 1e6)
                        if (poses.isNotEmpty()) withPose++
                        count++
                    }
                } catch (stop: StopCheck) {
                    // Hecho.
                }
            }
            check(count > before) { "El clip ${clip.name} no entregó ningún fotograma" }
        }
        return Detection(count, withPose, times)
    }

    private suspend fun firstClip(): File {
        val found = layout.inDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }?.minByOrNull { it.name }
        if (found != null) return found
        val file = File(context.cacheDir, "vt-bench-synthetic.mp4")
        withContext(Dispatchers.IO) {
            SyntheticClipEncoder.encodeWithTimeout(file, SyntheticClipEncoder.Spec("synthetic", fps = 30, seconds = 3, orientationHint = 0, variableRate = false))
        }
        return file
    }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** `Class.forName` sin inicializar: ¿está la clase en el APK? */
    private fun classPresence(): JSONObject {
        val json = JSONObject()
        for (name in TELEMETRY_CLASSES) {
            val loaded = try {
                Class.forName(name, false, context.classLoader)
                true
            } catch (error: Throwable) {
                false
            }
            json.put(name, loaded)
        }
        return json
    }

    /** Las líneas del logcat propio (`logcat -d -v time`) que casan con [pattern]. */
    private fun matchingLogLines(pattern: Regex): List<String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
            val lines = process.inputStream.bufferedReader().use { reader -> reader.readLines() }
            runCatching { process.waitFor() }
            lines.filter { pattern.containsMatchIn(it) }
        } catch (error: Throwable) {
            listOf("(no se pudo leer logcat: ${describeError(error)})")
        }
    }

    private fun pendingJobs(): JSONArray {
        val json = JSONArray()
        try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            for (job in scheduler.allPendingJobs) {
                json.put(
                    JSONObject()
                        .put("id", job.id)
                        .put("servicio", job.service.className)
                        .put("latenciaMinMs", job.minLatencyMillis)
                        .put("red", job.networkType)
                        .put("periodico", job.isPeriodic),
                )
            }
        } catch (error: Throwable) {
            json.put("(no se pudo leer JobScheduler: ${describeError(error)})")
        }
        return json
    }

    /** Filas de `events` en la base de datatransport, o −1 si no hay base y −2 si no se pudo leer. */
    private fun queuedEvents(): Int {
        if (!context.getDatabasePath(TRANSPORT_DB).isFile) return -1
        return try {
            withTransportDatabase { db ->
                db.rawQuery("SELECT COUNT(*) FROM events", null).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
            }
        } catch (error: Throwable) {
            -2
        }
    }

    /**
     * Abre la base de datatransport SOLO para leer. Esa base va en modo WAL y otra conexión la tiene abierta: si el acceso de solo
     * lectura falla, se lee una copia (base, `-wal` y `-shm`) de la caché.
     */
    private fun <T> withTransportDatabase(block: (SQLiteDatabase) -> T): T {
        val file = context.getDatabasePath(TRANSPORT_DB)
        try {
            return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use(block)
        } catch (direct: Throwable) {
            val dir = File(context.cacheDir, "transport-copy").apply {
                deleteRecursively()
                mkdirs()
            }
            for (suffix in listOf("", "-wal", "-shm")) {
                val source = File(file.path + suffix)
                if (source.isFile) source.copyTo(File(dir, file.name + suffix), overwrite = true)
            }
            return SQLiteDatabase.openDatabase(File(dir, file.name).path, null, SQLiteDatabase.OPEN_READONLY).use(block)
        }
    }

    /** Lo que datatransport tiene en cola: tablas con su número de filas, destinos y el texto imprimible de los primeros eventos. */
    private fun transportDatabase(): JSONObject {
        val json = JSONObject()
        val file = context.getDatabasePath(TRANSPORT_DB)
        json.put("existe", file.isFile)
        if (!file.isFile) return json
        try {
            withTransportDatabase { db ->
                val tables = JSONObject()
                val names = ArrayList<String>()
                db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                    while (c.moveToNext()) names.add(c.getString(0))
                }
                for (name in names) {
                    db.rawQuery("SELECT COUNT(*) FROM \"$name\"", null).use { c -> tables.put(name, if (c.moveToFirst()) c.getInt(0) else 0) }
                }
                json.put("tablas", tables)
                if ("transport_contexts" in names) {
                    val contexts = JSONArray()
                    db.rawQuery("SELECT backend_name, priority FROM transport_contexts", null).use { c ->
                        while (c.moveToNext()) contexts.put("${c.getString(0)} prioridad=${c.getInt(1)}")
                    }
                    json.put("destinos", contexts)
                }
                if ("events" in names) {
                    val events = JSONArray()
                    db.rawQuery("SELECT transport_name, timestamp_ms, num_attempts, payload FROM events LIMIT $MAX_EVENTS", null).use { c ->
                        while (c.moveToNext()) {
                            val payload = if (c.isNull(3)) null else c.getBlob(3)
                            events.put(
                                JSONObject()
                                    .put("fuente", c.getString(0))
                                    .put("marcaMs", c.getLong(1))
                                    .put("intentos", c.getInt(2))
                                    .put("bytes", payload?.size ?: JSONObject.NULL)
                                    .put("texto", JSONArray(payload?.let { printableStrings(it) }.orEmpty())),
                            )
                        }
                    }
                    json.put("eventos", events)
                }
            }
        } catch (error: Throwable) {
            json.put("error", describeError(error))
        }
        return json
    }

    /** Las cadenas ASCII imprimibles de al menos 5 caracteres de [bytes]: el protobuf de MediaPipe lleva ahí el id de la app. */
    private fun printableStrings(bytes: ByteArray): List<String> {
        val found = ArrayList<String>()
        val current = StringBuilder()
        for (b in bytes) {
            val code = b.toInt() and 0xFF
            if (code in 0x20..0x7E) {
                current.append(code.toChar())
            } else {
                if (current.length >= 5) found.add(current.take(MAX_STRING).toString())
                current.setLength(0)
            }
            if (found.size >= MAX_STRINGS) break
        }
        if (current.length >= 5 && found.size < MAX_STRINGS) found.add(current.take(MAX_STRING).toString())
        return found
    }

    companion object {
        const val TELEMETRY_FILE = "telemetry.json"
        private const val TAG = "KpknTechniqueBench"
        private const val DETECTIONS = 100
        private const val MAX_LOG_LINES = 60
        private const val MAX_EVENTS = 10
        private const val MAX_STRINGS = 40
        private const val MAX_STRING = 80
        private const val TRANSPORT_DB = "com.google.android.datatransport.events"
        private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v")
        private val TELEMETRY_PATTERN = Regex("TRuntime|TransportRuntime|datatransport|clearcut|play\\.googleapis", RegexOption.IGNORE_CASE)
        private val MEDIAPIPE_PATTERN = Regex("RemoteLoggingClient|TasksStatsProtoLogger", RegexOption.IGNORE_CASE)
        private val TELEMETRY_CLASSES = listOf(
            "com.google.android.datatransport.runtime.TransportRuntime",
            "com.google.android.datatransport.cct.CCTDestination",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",
            "com.google.mediapipe.tasks.core.logging.RemoteLoggingClient",
            "com.google.mediapipe.tasks.core.logging.TasksStatsProtoLogger",
            "com.google.android.gms.clearcut.ClearcutLogger",
        )
    }
}
