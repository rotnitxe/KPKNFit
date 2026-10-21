package com.example.kpkn.data.media.technique

import android.content.Context
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import java.io.File

/**
 * Qué parte del análisis se hace (§3.7).
 * - [FULL]: decodifica el vídeo, estima la pose (y el disco si el perfil lo usa) y mide. Escribe `pose2` y `bar`.
 * - [RECOMPUTE]: vuelve a medir con los sidecars de pose y disco que ya existen, sin decodificar (cambió el motor o el
 *   ejercicio).
 * - [PLATE_ONLY]: vuelve a seguir solo el disco desde la semilla confirmada por el usuario y mide de nuevo.
 *
 * Se guarda por NOMBRE en los datos de entrada del Worker: no reordenar ni renombrar.
 */
enum class AnalysisMode { FULL, RECOMPUTE, PLATE_ONLY }

/**
 * Lo que el analizador necesita de un vídeo (§3.7). [exercise] es lo que el data layer sabe del ejercicio; [loadKg] la
 * carga de la serie; [plateDiameterMm] y [plateSeed] el disco de calibración; [userHeightCm] la estatura del perfil (escala
 * corporal) y [setHints] los nombres de `SetTechniqueV2` de la serie.
 */
data class TechniqueAnalysisRequest(
    val mediaId: String,
    val videoFile: File,
    val mode: AnalysisMode,
    val exercise: ExerciseRef,
    val loadKg: Double?,
    val plateDiameterMm: Int?,
    val plateSeed: PlateSeed?,
    val userHeightCm: Double?,
    val setHints: Set<String>,
)

/** Cómo termina un análisis (§3.7). El Worker lo traduce al estado de `video_analysis`. */
sealed interface TechniqueAnalysisOutcome {
    /** Informe listo: DONE. */
    data class Done(
        val report: SetTechniqueReport,
        val exercise: ExerciseRef? = null,
        val poseEngine: String? = null,
        val poseVersion: Int? = null,
    ) : TechniqueAnalysisOutcome

    /** El ejercicio no se analiza en esta versión; [reason] es un código estable de `UnsupportedReasons`: UNSUPPORTED. */
    data class Unsupported(val reason: String) : TechniqueAnalysisOutcome

    /** Ejercicio propio sin perfil elegido; [guess] es la sugerencia, si la hay: NEEDS_PROFILE. */
    data class NeedsProfile(val guess: AnalysisProfileId?) : TechniqueAnalysisOutcome

    /**
     * No se pudo analizar. Con [transient] (un códec ocupado, un corte de E/S) el Worker lo reintenta con espera creciente;
     * si no, o a los 3 intentos, el estado es FAILED con [errorCode].
     */
    data class Failed(val errorCode: String, val transient: Boolean) : TechniqueAnalysisOutcome
}

/**
 * Analiza el vídeo de una serie. Nunca lanza hacia fuera por un fallo del análisis (lo devuelve como [TechniqueAnalysisOutcome.Failed]);
 * solo propaga la cancelación de la corrutina. [onProgress] recibe un porcentaje de 0 a 100; el Worker decide cuántas veces lo
 * escribe en Room.
 */
interface TechniqueAnalyzer {
    suspend fun analyze(
        request: TechniqueAnalysisRequest,
        onProgress: suspend (percent: Int) -> Unit,
    ): TechniqueAnalysisOutcome
}

/**
 * El analizador de la app: el [TechniqueAnalysisPipeline] real (MediaPipe en modo VIDEO con el modelo full), uno por proceso.
 * [UnavailableTechniqueAnalyzer] se queda como respaldo y para las pruebas: todo vídeo acaba en FAILED con `analyzer_unavailable`,
 * sin reintentos.
 *
 * [overrideForTests] sustituye al analizador del proceso (las pruebas del Worker meten un falso); `null` lo restablece.
 */
object TechniqueAnalyzerProvider {
    @Volatile private var override: TechniqueAnalyzer? = null
    @Volatile private var shared: TechniqueAnalyzer? = null

    fun get(context: Context): TechniqueAnalyzer =
        override ?: shared ?: synchronized(this) {
            shared ?: create(context.applicationContext).also { shared = it }
        }

    internal fun overrideForTests(analyzer: TechniqueAnalyzer?) {
        override = analyzer
    }

    /** El pipeline real, con el `PoseEstimator` de MediaPipe en modo VIDEO (se crea en cada análisis y se cierra al terminar). */
    private fun create(context: Context): TechniqueAnalyzer = TechniqueAnalysisPipeline(context)
}

/** El analizador de relleno: no mira el vídeo y falla sin reintento (`analyzer_unavailable`). */
internal object UnavailableTechniqueAnalyzer : TechniqueAnalyzer {
    const val ERROR_CODE = "analyzer_unavailable"

    override suspend fun analyze(
        request: TechniqueAnalysisRequest,
        onProgress: suspend (percent: Int) -> Unit,
    ): TechniqueAnalysisOutcome = TechniqueAnalysisOutcome.Failed(ERROR_CODE, transient = false)
}
