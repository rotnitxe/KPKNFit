package com.example.kpkn.data.media.technique

import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Contenido de `<id>.analysis.json` (§3.8): el informe de la serie con lo necesario para rehacer la fila de Room sin volver
 * a mirar el vídeo. Es la FUENTE DE VERDAD del análisis; `video_analysis` es solo su caché.
 *
 * @property summaryVersion versión de este formato ([TechniqueSidecars.SUMMARY_VERSION]).
 * @property exercise con qué ejercicio se midió (la referencia que resolvió el perfil).
 * @property loadKg carga de la serie en kg, o `null` si no se conoce.
 * @property setKey clave de la serie en la sesión (`<ejercicio>_<serie>[_L|_R]`), o `null` si el vídeo no es de una serie.
 */
@Serializable
data class AnalysisSidecar(
    val summaryVersion: Int = TechniqueSidecars.SUMMARY_VERSION,
    val report: SetTechniqueReport,
    val exercise: ExerciseRef,
    val loadKg: Double? = null,
    val setKey: String? = null,
    val analyzedAtMs: Long,
)

/** La confianza propia de las parciales no puede superar la confianza general del informe. */
internal fun SetTechniqueReport.capPartialConfidenceAtOverall(): SetTechniqueReport {
    val partial = partialConfidence ?: return this
    val capped = when {
        confidence == CountConfidence.BAJA -> CountConfidence.BAJA
        confidence == CountConfidence.MEDIA && partial == CountConfidence.ALTA -> CountConfidence.MEDIA
        else -> partial
    }
    return if (capped == partial) this else copy(partialConfidence = capped)
}

/**
 * Los archivos que el análisis de técnica deja junto al vídeo, en `workout_media/yyyy-MM/` (§3.8), y cómo se leen y se
 * escriben:
 *
 * | Archivo | Contenido |
 * |---|---|
 * | `<id>.pose2.json.gz` | la traza de pose v2 |
 * | `<id>.bar.json.gz` | la traza del disco |
 * | `<id>.analysis.json` | el informe ([AnalysisSidecar]); fuente de verdad |
 * | `<id>.capture.json` | metadatos de la captura (fps, gravedad, lente…) |
 * | `<id>.pose.part` | punto de control del análisis en curso; se borra al terminar |
 *
 * `<id>` es el nombre del vídeo sin extensión, que es el id del medio. Todo es E/S bloqueante: llamar desde `Dispatchers.IO`.
 * Cada escritura es atómica (archivo temporal + renombrado): un corte a medias nunca deja un sidecar truncado.
 */
object TechniqueSidecars {
    /** Versión del formato de `<id>.analysis.json`. Un sidecar de una versión posterior no se lee (se mostrará como «sin análisis»). */
    const val SUMMARY_VERSION = 1

    const val POSE_SUFFIX = ".pose2.json.gz"
    const val BAR_SUFFIX = ".bar.json.gz"
    const val ANALYSIS_SUFFIX = ".analysis.json"
    const val CAPTURE_SUFFIX = ".capture.json"
    const val PART_SUFFIX = ".pose.part"

    /** Sufijo del archivo temporal de una escritura atómica: `<id>.analysis.json.tmp`. */
    const val TEMP_SUFFIX = ".tmp"

    // Solo en el reemplazo sin renombrado atómico (ver [commit]); dura lo que un renombrado.
    private const val BACKUP_SUFFIX = ".bak"

    private val SUFFIXES = listOf(POSE_SUFFIX, BAR_SUFFIX, ANALYSIS_SUFFIX, CAPTURE_SUFFIX, PART_SUFFIX)

    // Con los valores por defecto incluidos y tolerante con campos nuevos; un NaN suelto no debe tirar el análisis entero.
    private val codec = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
    }

    /** El id del medio al que pertenece [videoFile]: su nombre sin extensión. */
    fun mediaId(videoFile: File): String = videoFile.nameWithoutExtension

    fun poseFile(videoFile: File): File = sibling(videoFile, POSE_SUFFIX)
    fun barFile(videoFile: File): File = sibling(videoFile, BAR_SUFFIX)
    fun analysisFile(videoFile: File): File = sibling(videoFile, ANALYSIS_SUFFIX)
    fun captureFile(videoFile: File): File = sibling(videoFile, CAPTURE_SUFFIX)
    fun partFile(videoFile: File): File = sibling(videoFile, PART_SUFFIX)

    fun poseFile(directory: File, mediaId: String): File = File(directory, mediaId + POSE_SUFFIX)
    fun barFile(directory: File, mediaId: String): File = File(directory, mediaId + BAR_SUFFIX)
    fun analysisFile(directory: File, mediaId: String): File = File(directory, mediaId + ANALYSIS_SUFFIX)
    fun captureFile(directory: File, mediaId: String): File = File(directory, mediaId + CAPTURE_SUFFIX)
    fun partFile(directory: File, mediaId: String): File = File(directory, mediaId + PART_SUFFIX)

    /** Todos los sidecars posibles de [videoFile] y sus temporales, existan o no. */
    fun allFiles(videoFile: File): List<File> =
        SUFFIXES.flatMap { suffix ->
            val sidecar = sibling(videoFile, suffix)
            listOf(sidecar, File(sidecar.parentFile, sidecar.name + TEMP_SUFFIX))
        }

    /** El id del medio de un sidecar (o de su temporal) por su nombre, o `null` si [file] no es un sidecar de la técnica. */
    fun sidecarMediaId(file: File): String? {
        val name = file.name.removeSuffix(TEMP_SUFFIX)
        val suffix = SUFFIXES.firstOrNull { name.endsWith(it) } ?: return null
        return name.removeSuffix(suffix).takeIf { it.isNotEmpty() }
    }

    /**
     * Escribe [bytes] en [file] de forma atómica: primero a `<archivo>.tmp` en la misma carpeta, con `fsync`, y luego lo
     * renombra encima. Quien lea ve el contenido anterior o el nuevo, nunca uno a medias. Crea la carpeta si falta.
     */
    fun writeAtomically(file: File, bytes: ByteArray) {
        val temporary = stage(file, bytes)
        var committed = false
        try {
            commit(temporary, file)
            committed = true
        } finally {
            if (!committed) temporary.delete()
        }
    }

    /** Primera mitad de la escritura atómica: deja el contenido nuevo completo en el temporal; [file] no se toca. */
    internal fun stage(file: File, bytes: ByteArray): File {
        val parent = file.absoluteFile.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("No se pudo preparar la carpeta de ${file.name}.")
        }
        val temporary = File(parent, file.name + TEMP_SUFFIX)
        var staged = false
        try {
            FileOutputStream(temporary).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            staged = true
        } finally {
            if (!staged) temporary.delete()
        }
        return temporary
    }

    /**
     * Segunda mitad: el renombrado. En Android (POSIX) `renameTo` reemplaza el destino de forma atómica y no hace falta más.
     * Si no reemplaza (Windows, en las pruebas de JVM), se aparta el destino, se renombra el temporal y se borra lo apartado;
     * si algo falla se devuelve lo que había. El destino nunca se borra antes de tener el contenido nuevo en su sitio.
     */
    internal fun commit(temporary: File, target: File) {
        if (temporary.renameTo(target)) return
        if (target.isDirectory) throw IOException("${target.name} es una carpeta.")
        if (!target.exists()) throw IOException("No se pudo guardar ${target.name}.")
        val aside = File(target.absoluteFile.parentFile, target.name + BACKUP_SUFFIX)
        aside.delete()
        if (!target.renameTo(aside)) throw IOException("No se pudo reemplazar ${target.name}.")
        if (!temporary.renameTo(target)) {
            aside.renameTo(target)
            throw IOException("No se pudo guardar ${target.name}.")
        }
        aside.delete()
    }

    /** Escribe `<id>.analysis.json` de forma atómica. */
    fun writeAnalysis(file: File, sidecar: AnalysisSidecar) {
        val normalized = sidecar.copy(report = sidecar.report.capPartialConfidenceAtOverall())
        val bytes = codec.encodeToString(AnalysisSidecar.serializer(), normalized).toByteArray(Charsets.UTF_8)
        writeAtomically(file, bytes)
    }

    fun writeAnalysis(
        file: File,
        report: SetTechniqueReport,
        exercise: ExerciseRef,
        loadKg: Double?,
        setKey: String?,
        analyzedAtMs: Long,
    ): Unit = writeAnalysis(file, AnalysisSidecar(SUMMARY_VERSION, report, exercise, loadKg, setKey, analyzedAtMs))

    /**
     * Lee `<id>.analysis.json`. `null` si no existe, está vacío o corrupto, o es de una versión posterior del formato:
     * nunca lanza, porque un sidecar ilegible solo significa «sin análisis» y se puede volver a calcular.
     */
    fun readAnalysis(file: File): AnalysisSidecar? {
        if (!file.isFile) return null
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: IOException) {
            return null
        }
        if (text.isBlank()) return null
        val sidecar = try {
            codec.decodeFromString(AnalysisSidecar.serializer(), text)
        } catch (_: IllegalArgumentException) {
            // SerializationException hereda de IllegalArgumentException: JSON truncado, campo que falta o enum desconocido.
            return null
        }
        return sidecar.takeIf { it.summaryVersion in 1..SUMMARY_VERSION }
            ?.let { it.copy(report = it.report.capPartialConfidenceAtOverall()) }
    }

    /**
     * Borra todos los sidecars de [videoFile] y sus temporales. Solo toca los archivos que [isDeletable] acepta (el
     * repositorio pasa «está en el almacén privado»). Devuelve cuántos borró.
     */
    fun deleteAll(videoFile: File, isDeletable: (File) -> Boolean = { true }): Int =
        allFiles(videoFile).count { file -> file.exists() && isDeletable(file) && file.delete() }

    private fun sibling(videoFile: File, suffix: String): File =
        File(videoFile.absoluteFile.parentFile, mediaId(videoFile) + suffix)
}
