# Técnica en vídeo v1 · analizador de técnica y conteo automático de repeticiones (ola VT, 2026-10-13)

## 0. Encargo, fuentes y reglas
**Encargo del usuario.** Al grabar una serie desde la sesión en vivo, la app debe:
- contar sola las repeticiones;
- analizar la técnica: ROM, verticalidad, desvíos, trayectoria, velocidad de A a B y TUT.

Va solo para los ejercicios compatibles.

**Decisiones del usuario** (2026-10-13):
- **1.ª entrega:**
  - **Núcleo:** análisis al terminar la serie, reps, TUT/tempo, ROM, trayectoria, pérdida de velocidad y «Usar/Corregir N reps».
  - **Barra calibrada con disco** (m/s reales).
  - **Análisis de vídeos de la galería.**
  - **Tendencias en el historial**, incluida la comparación de dos vídeos.
- **Fase 2:** el contador en vivo con voz.
- **Quién escribe:** subagentes **Sonnet 5.5**.
- **Banco dorado:** vídeos públicos con licencia, con permiso del usuario para cada descarga.

**Documentos de la ola:**
- Plan aprobado: `C:\Users\valen\.claude\plans\iridescent-enchanting-dragonfly.md`.
- Registro de decisiones: `docs/video-technique/DECISIONES.md`.
- Modelos: `docs/video-technique/MODELS.md`.
- Briefs: `docs/video-technique/briefs/`.

**Rama y worktrees:**
- Integración: `feat/video-technique`, desde `feat/sessions-v1` @ `e77840cae`.
- Agentes: `C:\kw\vt-a` (y `C:\kw\vt-b` si el disco lo permite).

**Reglas de siempre:**
- `CLAUDE.md`: `domain/` sin `android.*`; los ViewModels exponen `StateFlow` con `asStateFlow()`; IO en `Dispatchers.IO`.
- Lenguaje visual: `docs/design/KPKN_UI_V2.md`.
- Brief común de la ola: `docs/video-technique/briefs/VT_COMUN.md`.

**Estado de LS2b.** Integrado en `feat/sessions-v1` el 13-oct, en el merge `a645adb88`. Se fusiona en `feat/video-technique` antes de VT8a.
- `design/WorkoutSetPanel.kt` sustituye la cara de `SetInputCardV2`.
- La hoja «Técnica» la abre la tarjeta activa (`WorkoutTechniqueSheetHolder`), desde el «Info» y desde «Opciones avanzadas».
- Dentro de la hoja van `SetCardTechniqueBack` y, con «Ver ejercicio/Fotos», `SetCardExerciseMediaBack`.
- LS4 hará que el «Info» abra directamente los medios.

**Coordinación con la ola LS** (rediseño de la sesión en vivo en `feat/sessions-v1`):
- LS2b monta `SetCardTechniqueBack` y `SetCardExerciseMediaBack` TAL CUAL dentro de una `KpknSheet` «Técnica», que se abre con el «Info» de la cabecera.
- LS3 deja `videoSlot: (@Composable () -> Unit)? = null` al final de `WorkoutRestPanel`.
- LS7 no toca el grabador ni los medios.
- La v29 de Room es de VT.

**Fuentes de las cifras:**
- **Conteo:** Oliosi 2026 (JMIR mHealth e82412): ~61 % con el móvil en cualquier sitio frente a 95–99 % bien encuadrado.
- **Velocidad (VBT):** Renner 2024 (PMC11575817): Qwik VBT con un error cuadrático medio de 0,01–0,03 m/s, Metric de 0,04–0,08.
- **Ángulos 2D:** Stenum (PMC8099131): 4–7°.
- **Rastreadores genéricos:** Achermann 2022, KCF/MOSSE con r = 0,36–0,62.
- **MVT:** González-Badillo y Sánchez-Medina 2010; Helms 2017; Balsalobre-Fernández 2018; Sánchez-Medina 2014 y 2017.
- **Pérdida de velocidad:** Sánchez-Medina y González-Badillo 2011; Pareja-Blanco 2017.
- **Fiabilidad del perfil carga-velocidad:** Banyard 2017; Hughes 2019.

---

## 1. Experiencia
1. **Grabar.**
   - **Entrada:** en la hoja «Técnica» de la serie, «Grabar serie» abre `TechniqueRecorderScreen`, solo vídeo y a pantalla completa. No es una ruta de navegación: es una capa sobre `WorkoutScreen`.
   - **Guía del perfil (§5):**
     - siempre: «De lado · cuerpo entero con margen · cara visible · a 2–4 m»;
     - con barra: «móvil a media altura del recorrido: en el suelo para el peso muerto; el disco siempre en cuadro».
   - **Comprobación de encuadre** (`FramingEvaluator`, §4.8):
     - analiza `PreviewView.bitmap` a ~2 Hz;
     - la vista previa es 16:9, igual que la grabación;
     - chips: «✓ Encuadre», «Aléjate: no se ven los pies», «Gírate de lado», «Hay 2 personas», «Endereza el móvil», «No veo el disco»;
     - orienta, nunca bloquea.
   - **REC:**
     - cronómetro;
     - 60 fps en perfiles con barra si el móvil lo garantiza;
     - límite de 180 s.
2. **Al parar:**
   - se vuelve a la serie;
   - el análisis corre en WorkManager y se pausa mientras la cámara graba;
   - la serie muestra «Analizando el vídeo… 42 %».
3. **Resultado.** Tarda unos 20–60 s y casi siempre llega con la serie ya registrada.
   - **Serie registrada:** en el descanso (`videoSlot` de LS3) o en la serie, «Registraste 8; el vídeo cuenta 7» con «Corregir».
   - **Serie sin registrar:** «El vídeo cuenta 8 reps · +1 parcial» con «Usar».
   - **Nunca se aplica solo.** Según la confianza:
     - ALTA: «Usar» o «Corregir» de un toque;
     - MEDIA: «≈ 8 · Revisar», que abre el análisis;
     - BAJA: «Vídeo no concluyente».
   - Una repetición FALLIDA propone marcar «Fallo».
4. **Pantalla «Análisis de técnica»** (`TechniqueAnalysis(mediaId)`):
   - **Reproductor:**
     - capa: esqueleto tenue, trayectoria coloreada por velocidad, guía vertical y marcas de repetición;
     - controles: 0,25×, 0,5× y 1×, paso a paso por fotograma y bucle por repetición.
   - **Módulos:**
     - Resumen;
     - Por repetición: barras de velocidad, ROM y tiempos;
     - Observaciones: como mucho 3, y cada una salta a su instante;
     - Calibrar disco: círculo que se arrastra y se pellizca; 45/40/35 cm u otro;
     - Consejos de cámara si la confianza es baja.
5. **Hub «Técnica en vídeo»** (tarjeta en Perfil, junto a Álbumes):
   - **Ejercicios analizados.** Cada uno abre `TechniqueTrends(exerciseKey)`, con:
     - el perfil carga-velocidad y su 1RM ≈ (beta);
     - la pérdida de velocidad;
     - ROM y profundidad;
     - TUT;
     - la lista de vídeos;
     - «Comparar»: dos vídeos con el deslizador sincronizado en la rep 1 y las trayectorias superpuestas.
   - **«Analizar un vídeo de la galería»:** selector de fotos → ejercicio (recientes, o el catálogo con `CatalogLaunchRequest`) → carga opcional.
   - **«Analizar vídeos anteriores (N)»:** solo si se pide.
6. **Ajustes de Entreno** (`Settings.techniqueVideo`):
   - «Analizar la técnica de los vídeos» (activado);
   - «Grabar a 60 fps cuando se pueda»;
   - «Disco de calibración» (450 mm);
   - duración máxima (180 s).

**Honestidad:**
- sin dato, la línea no se pinta;
- las estimaciones llevan «≈»;
- hay m/s solo con disco calibrado, y con «≈» hasta validarlos en el teléfono;
- 1RM solo en tendencias, con «orientativo · beta»;
- texto de privacidad: «El análisis se realiza en tu teléfono». El recorrido de análisis no sube el vídeo; la exportación manual de medios sí puede enviarlo al destino que elija la persona. La ausencia de telemetría en el APK actual requiere medición propia (VT4b).

---

## 2. Mapa del código
```
domain/biomechanics/                 (puro, JVM; kotlinx.serialization y java.util.zip permitidos)
  pose/       PoseTrackV2.kt, PoseLandmark.kt, PoseTrackCodec.kt, LegacyPoseTrackAdapter.kt
  bar/        BarTrack.kt, BarTrackCodec.kt
  signal/     Resampler.kt, Hampel.kt, ZeroPhaseLowPass.kt, Derivative.kt, Autocorrelation.kt, GapFiller.kt,
              LeftRightSwapRepair.kt, TimeSeries.kt
  kinematics/ FrameGeometry.kt, BodyScale.kt, NearSide.kt, ViewClassifier.kt, Facing.kt
  framing/    FramingEvaluator.kt
  profiles/   AnalysisProfile.kt, AnalysisProfiles.kt, AnalysisProfileResolver.kt, ExerciseRef.kt
  reps/       RepSegmenter.kt, RepConfidence.kt, RepPhases.kt
  metrics/    SetTechniqueReport.kt, TechniqueAnalysisEngine.kt, AnalysisTuning.kt, RomMetrics.kt, DepthRules.kt,
              PathMetrics.kt, TrunkMetrics.kt, Observations.kt, ObservationText.kt
  vbt/        PlateScale.kt, VelocityMetrics.kt, LoadVelocityProfile.kt, MinimumVelocityThresholds.kt
  vision/     LumaFrame.kt, PlateDetector.kt, PlateTracker.kt, CircleFit.kt, ConstantVelocityKalman.kt, Ncc.kt
  TrajectoryMetrics.kt                (v1; fachada del overlay hasta VT7)
data/media/video/      VideoFrameSource.kt, GlVideoFrameSource.kt, RetrieverVideoFrameSource.kt, VideoInfoProbe.kt
data/media/pose/       PoseEstimator.kt, MediaPipePoseEstimator.kt, GpuProbe.kt
data/media/technique/  TechniqueAnalyzer.kt, TechniqueAnalysisPipeline.kt, TechniqueSidecars.kt, CaptureMetadata.kt,
                       AnalysisCheckpoint.kt, ExerciseRefResolver.kt (catálogo → ExerciseRef)
data/db/               VideoAnalysisEntity.kt (+DAO), VideoAnalysisMigration.kt
data/repository/       VideoAnalysisRepository.kt
data/work/             TechniqueAnalysisWorker.kt, TechniqueAnalysisScheduler.kt, AnalysisGate.kt
data/models/           VideoAnalysisSummary.kt, (Settings.kt: TechniqueVideoSettings)
screens/technique/analysis|hub|trends|compare|importer/   (+ design/ sin estado)
screens/workout/technique/   TechniqueRecorderScreen.kt, RecorderSessionFactory.kt, FramingCheckController.kt
screens/workout/design/WorkoutVideoRepsSuggestion.kt, screens/workout/WorkoutVideoAnalysisController.kt
src/debug/.../debug/TechniqueBenchActivity.kt, src/debug/.../data/media/pose/MlKitPoseEstimator.kt
```

---

## 3. Contratos

Son fijos. Quien necesite cambiar una firma lo anota en su informe; el orquestador decide.

### 3.1 Pose v2 (`domain/biomechanics/pose`)
```kotlin
/** Los 33 puntos de MediaPipe Pose. [mp] es el índice del modelo. */
enum class PoseLandmark(val mp: Int) {
    NOSE(0), LEFT_EYE_INNER(1), LEFT_EYE(2), LEFT_EYE_OUTER(3), RIGHT_EYE_INNER(4), RIGHT_EYE(5), RIGHT_EYE_OUTER(6),
    LEFT_EAR(7), RIGHT_EAR(8), MOUTH_LEFT(9), MOUTH_RIGHT(10), LEFT_SHOULDER(11), RIGHT_SHOULDER(12),
    LEFT_ELBOW(13), RIGHT_ELBOW(14), LEFT_WRIST(15), RIGHT_WRIST(16), LEFT_PINKY(17), RIGHT_PINKY(18),
    LEFT_INDEX(19), RIGHT_INDEX(20), LEFT_THUMB(21), RIGHT_THUMB(22), LEFT_HIP(23), RIGHT_HIP(24),
    LEFT_KNEE(25), RIGHT_KNEE(26), LEFT_ANKLE(27), RIGHT_ANKLE(28), LEFT_HEEL(29), RIGHT_HEEL(30),
    LEFT_FOOT_INDEX(31), RIGHT_FOOT_INDEX(32);
    companion object {
        /** 27 puntos de imagen guardados: 0 y 7..32 (los ojos no se usan). */
        val KEPT_IMAGE: List<PoseLandmark>
        /** 12 puntos «world» guardados: 11..16 y 23..28. */
        val KEPT_WORLD: List<PoseLandmark>
    }
}

/** Punto de imagen: x,y normalizados (0..1) sobre el fotograma YA GIRADO de análisis (frameWidth×frameHeight). */
data class LandmarkSample(val x: Float, val y: Float, val z: Float, val visibility: Float, val presence: Float)
/** Punto «world» en metros, centrado en la cadera (solo para vista, lado cercano y respaldo en oblicuo). */
data class WorldSample(val x: Float, val y: Float, val z: Float)

data class PoseEngineInfo(val id: String, val model: String, val delegate: String)   // "mediapipe-pose", "full", "GPU"

/** Un fotograma: la pose del atleta elegido (o ninguna) y cuántas poses vio el modelo. */
class PoseFrameV2(
    val tUs: Long,
    val posesDetected: Int,
    image: Map<PoseLandmark, LandmarkSample>?,     // null = sin atleta en este fotograma
    world: Map<PoseLandmark, WorldSample>?,
) {
    val hasPose: Boolean
    fun point(lm: PoseLandmark): LandmarkSample?
    fun world(lm: PoseLandmark): WorldSample?
}

data class PoseTrackV2(
    val mediaId: String,
    val engine: PoseEngineInfo,
    val frameWidth: Int, val frameHeight: Int,     // píxeles del fotograma girado que vio el estimador
    val rotationApplied: Int,                      // 0/90/180/270 aplicada al decodificar
    val nominalFps: Float,                         // 1/mediana(ΔPTS) de los fotogramas con pose
    val poseStride: Int,                           // 1 = cada fotograma; 2 = uno de cada dos (60 fps)
    val vfr: Boolean,
    val captureFps: Float? = null,                 // cámara lenta de galería
    val focalLengthPx: Float? = null,              // de CameraCharacteristics; null en galería
    val gravityRollDeg: Float? = null,
    val gravityPitchDeg: Float? = null,
    val aspectKnown: Boolean = true,               // false en trazas v1 → solo reps
    val frames: List<PoseFrameV2>,
    val version: Int = 2,
)
```
`PoseTrackCodec` (puro):
```kotlin
object PoseTrackCodec {
    fun encode(track: PoseTrackV2): ByteArray          // JSON columnar cuantizado + gzip (§3.8)
    fun decode(bytes: ByteArray): PoseTrackV2?          // null si no es v2 válido
}
object LegacyPoseTrackAdapter {
    /** v1 (`PoseTrack` de TrajectoryMetrics) → v2: vis = pres = inFrameLikelihood, z = 0, sin world, fps 10, aspectKnown = false. */
    fun toV2(v1: com.example.kpkn.domain.biomechanics.PoseTrack): PoseTrackV2
}
```

### 3.2 Barra (`domain/biomechanics/bar`)
```kotlin
enum class BarSampleSource { HOUGH, NCC, POSE_PRIOR, PREDICTED }
/** En PÍXELES del mismo fotograma girado de análisis que la pose. */
data class BarSample(val tUs: Long, val cx: Float, val cy: Float, val r: Float, val axisRatio: Float, val quality: Float,
                     val source: BarSampleSource)
data class PlateSeed(val tUs: Long, val cx: Float, val cy: Float, val r: Float, val userConfirmed: Boolean)
data class BarTrack(
    val mediaId: String,
    val frameWidth: Int, val frameHeight: Int,
    val plateDiameterMm: Int?,          // null = sin calibrar (sirve como señal, sin m/s)
    val seed: PlateSeed?,
    val samples: List<BarSample>,
    val version: Int = 1,
)
object BarTrackCodec { fun encode(t: BarTrack): ByteArray; fun decode(b: ByteArray): BarTrack? }
```

### 3.3 Visión (`domain/biomechanics/vision`)
```kotlin
/** Luma de 8 bits sin signo, fila a fila (stride = width). */
class LumaFrame(val width: Int, val height: Int, val data: ByteArray)
data class PlateCandidate(val cx: Float, val cy: Float, val r: Float, val score: Float, val coverage: Float)
object PlateDetector {
    /** Propone hasta [max] discos cerca de las muñecas (píxeles). [expectedRadiusPx] sale de la escala corporal. */
    fun propose(frame: LumaFrame, wristsPx: List<Pair<Float, Float>>, expectedRadiusPx: Float, max: Int = 3): List<PlateCandidate>
}
class PlateTracker(seed: PlateSeed, fps: Float, config: PlateTrackerConfig = PlateTrackerConfig()) {
    /** Una muestra por fotograma; [wristPriorPx] = muñeca cercana si es válida. */
    fun track(frame: LumaFrame, tUs: Long, wristPriorPx: Pair<Float, Float>?): BarSample
}
```

### 3.4 Perfiles (`domain/biomechanics/profiles`)
`ViewKind` y `FramingSpec` aparecen en el bloque de abajo solo como referencia: los define VT1b, en `kinematics/ViewKind.kt` y `framing/FramingSpec.kt`.
```kotlin
enum class AnalysisProfileId { SQUAT, LUNGE, STEP_UP, MACHINE_SQUAT, DEADLIFT, HINGE, HIP_THRUST, BACK_EXTENSION, BENCH, DIP,
    PUSH_UP, OHP, PULL_VERTICAL, ROW, CURL, TRICEPS_EXTENDED_REST, TRICEPS_FLEXED_REST, RAISE, LEG_EXTENSION, LEG_CURL,
    CALF, SHRUG, FLY }
enum class ProfileTier { A, B, C }
enum class StartPhase { ECCENTRIC_FIRST, CONCENTRIC_FIRST }
enum class ViewKind { SIDE, FRONT, OBLIQUE, BACK, LYING, UNKNOWN }
enum class CountSignal { DROP_HIP_MID, RISE_HIP_MID, KNEE_FLEXION, KNEE_EXTENSION_ANGLE, HIP_FLEXION, HIP_ANGLE,
    RISE_SHOULDER_NEAR, DROP_SHOULDER_NEAR, DROP_HAND_NEAR, RISE_HAND, ELBOW_FLEXION, ELBOW_ANGLE, SHOULDER_TO_WRIST_CLOSE,
    ARM_ELEVATION, RISE_TRUNK_MID, RISE_KNEE_NEAR, SHRUG_SIGNAL, DROP_WRIST_NEAR, PLATE_DROP, PLATE_RISE }
enum class RomAngle { KNEE, HIP, ELBOW, ARM_ELEVATION, TRUNK }
enum class BarPointRule { PLATE_THEN_SHOULDER, PLATE_THEN_HAND, PLATE_THEN_HIP, HAND, SHOULDER_MID, NONE }
enum class DepthRule { SQUAT_PARALLEL, CHIN_OVER_BAR, PUSH_UP_ELBOW, DIP_SHOULDER, BENCH_RELATIVE }
enum class LockoutRule { DEADLIFT, HIP_THRUST, OHP }
enum class PathRule { MIDFOOT_VERTICAL, CONSISTENCY, NONE }

data class FramingSpec(val preferredViews: Set<ViewKind>, val needsPlate: Boolean, val needsFeet: Boolean, val needsFace: Boolean = true)

data class AnalysisProfile(
    val id: AnalysisProfileId,
    val tier: ProfileTier,
    val startPhase: StartPhase,
    val countViews: Set<ViewKind>,
    val metricViews: Set<ViewKind>,
    val poseSignal: CountSignal,                  // señal de conteo con pose
    val plateSignal: CountSignal?,                // PLATE_DROP/PLATE_RISE si el disco es la señal principal
    val romAngle: RomAngle?,
    val barPoint: BarPointRule,
    val perSide: Boolean,                         // puede contar por lado (alterno)
    val depthRule: DepthRule?,
    val lockoutRule: LockoutRule?,
    val pathRule: PathRule,
    val mvtMps: Double?,                          // null = sin 1RM por velocidad
    val minAmplitude: Double,                     // grados o BH según la señal (§6)
    val framing: FramingSpec,
    val usesPlate: Boolean = false,               // lo fija el resolver según el material
    val plateMinAmplitude: Double? = null,        // amplitud mínima en BH cuando cuenta el disco (VT2a)
)

/** Lo que el data layer sabe del ejercicio (catálogo v2 + sesión). */
data class ExerciseRef(
    val definitionId: String?, val configurationId: String?, val movementPatternId: String?,
    val equipmentId: String?, val laterality: String?, val name: String, val isCustom: Boolean,
    val userProfileOverride: AnalysisProfileId? = null,
    val guessedMovementPatternId: String? = null,   // ejercicio propio: conjetura de ExercisePatternDetector (data layer)
)
sealed interface ProfileResolution {
    data class Supported(val profile: AnalysisProfile, val source: String) : ProfileResolution   // "pattern", "definition", "user", "name"
    data class Unsupported(val reason: String) : ProfileResolution
    data class NeedsUserChoice(val guess: AnalysisProfileId?) : ProfileResolution                 // ejercicio propio sin elección
}
object AnalysisProfileResolver { fun resolve(ref: ExerciseRef): ProfileResolution }
object AnalysisProfiles { fun of(id: AnalysisProfileId): AnalysisProfile; val all: List<AnalysisProfile> }
```

### 3.5 Informe (`domain/biomechanics/metrics`, `@Serializable`)
```kotlin
enum class CountConfidence { ALTA, MEDIA, BAJA }
enum class RepKind { FULL, PARTIAL, FAILED }
enum class PartialKind { FAR_END, REST_END }
enum class DepthClass { BELOW, PARALLEL, ABOVE }
enum class VelocityUnit { MPS, BODY_HEIGHTS_PER_S, DEG_PER_S }
enum class ScaleSource { PLATE, NONE }
enum class MetricKey { REPS, PHASES, TUT, TEMPO, ROM, DEPTH, LOCKOUT, PATH, PATH_ABSOLUTE, TRUNK, VELOCITY_RELATIVE,
    VELOCITY_MPS, PEAK_VELOCITY, SYMMETRY }
enum class AnalysisFlag { MULTI_PERSON, POLARITY_FLIPPED, FAR_SIDE_WORKING, LEGACY_TRACK, VFR, LOW_FPS, OBLIQUE_VIEW,
    BACK_VIEW, PLATE_ELLIPSE, PLATE_REJECTED, PARALLAX_SUPPRESSED, SIDE_UNKNOWN, ROLL_CORRECTED, HDR_SOURCE }

@Serializable data class RepPhaseTimes(val departUs: Long, val turnStartUs: Long, val turnEndUs: Long, val arriveUs: Long)
@Serializable data class RepReport(
    val index: Int, val kind: RepKind, val partialKind: PartialKind? = null, val side: String? = null,   // "left"/"right"
    val phases: RepPhaseTimes,
    val eccentricMs: Int?, val turnPauseMs: Int?, val concentricMs: Int?, val restPauseMs: Int?,
    val amplitudeFraction: Double, val romDeg: Double?,
    val depthClass: DepthClass? = null, val depthDeg: Double? = null,
    val criteria: Map<String, Boolean> = emptyMap(),          // "chinOverBar", "lockout", "fullHang"...
    val mcv: Double? = null, val peakVelocity: Double? = null,
    val pathOffsetMean: Double? = null, val pathDrift: Double? = null, val pathExcursionFraction: Double? = null,
    val pathDeviationRms: Double? = null,                    // banca: PD
    val trunkAtTurnDeg: Double? = null, val trunkRangeDeg: Double? = null, val trunkEarlyDeltaDeg: Double? = null,
)
@Serializable data class TempoSummary(val eccentricMs: Int?, val pauseAfterEccentricMs: Int?, val concentricMs: Int?,
                                       val pauseAfterConcentricMs: Int?) { fun label(): String /* "3-1-1-0" */ }
@Serializable data class ScaleInfo(val source: ScaleSource, val plateDiameterMm: Int?, val metersPerPxH: Double?,
    val metersPerPxV: Double?, val axisRatio: Double?, val parallaxErrorCm: Double?)
@Serializable data class TechniqueObservation(val code: String, val text: String, val repIndices: List<Int>, val atUs: Long?,
    val magnitude: Double, val rank: Double)
@Serializable data class SetTechniqueReport(
    val engineVersion: Int, val profileId: AnalysisProfileId, val tier: ProfileTier,
    val view: ViewKind, val nearSide: String?, val confidence: CountConfidence, val confidenceScore: Double,
    val reps: List<RepReport>, val repCount: Int, val partialReps: Int, val failedReps: Int, val miniSets: Int,
    val perSideCounts: Map<String, Int> = emptyMap(),
    val tutMs: Long?, val setDurationMs: Long?, val restPositionMs: Long?, val tempo: TempoSummary?,
    val romMeanDeg: Double?, val romSdDeg: Double?, val romCvPct: Double?, val romTrendDeg: Double?,
    val velocityUnit: VelocityUnit?, val velocityLossPct: Double?,
    val mcvFirst: Double?, val mcvBest: Double?, val mcvLast: Double?, val peakBest: Double?,
    val scale: ScaleInfo?, val trunkMeanDeg: Double?, val barDeviation: Double?, val barDeviationUnit: String?,   // "cm" / "%ROM"
    val observations: List<TechniqueObservation>,
    val available: Set<MetricKey>, val flags: Set<AnalysisFlag>,
    val analyzedFps: Double, val sourceFps: Double?,
)
```

### 3.6 Motor
```kotlin
data class TechniqueAnalysisInput(
    val pose: PoseTrackV2, val bar: BarTrack?, val profile: AnalysisProfile,
    val loadKg: Double?, val userHeightCm: Double?, val setHints: Set<String>,     // nombres de SetTechniqueV2
    val tuning: AnalysisTuning = AnalysisTuning.DEFAULT,
)
object TechniqueAnalysisEngine {
    const val ENGINE_VERSION = 1
    fun analyze(input: TechniqueAnalysisInput): SetTechniqueReport
}
/** TODOS los umbrales de §4–§8, versionados; VT10 solo ajusta aquí, con el reparto de ajuste. */
data class AnalysisTuning(...) { companion object { val DEFAULT: AnalysisTuning } }
```
- **Carga-velocidad** (sobre varios informes; la calcula el repositorio de tendencias):
```kotlin
data class LoadVelocityPoint(val loadKg: Double, val mcvMps: Double, val recordedAtMs: Long)
sealed interface LoadVelocityResult { data class Estimate(val e1rmKg: Double, val slope: Double, val intercept: Double,
    val r2: Double, val points: Int) : LoadVelocityResult; data class NotEnough(val reason: String) : LoadVelocityResult }
object LoadVelocityProfile { fun estimate(points: List<LoadVelocityPoint>, mvtMps: Double): LoadVelocityResult }
```

### 3.7 Datos
```kotlin
// data/media/video
data class VideoInfo(val codedWidth: Int, val codedHeight: Int, val rotationDegrees: Int, val displayWidth: Int,
    val displayHeight: Int, val durationUs: Long, val mime: String, val nominalFps: Float?, val captureFps: Float?, val isHdr: Boolean)
enum class FrameNeeds { SKIP, RGBA, LUMA, RGBA_AND_LUMA }
/** Del pool: válido solo hasta que vuelve onFrame. RGBA directo, stride = width*4, filas de arriba abajo, ya girado. */
class DecodedFrame(val index: Int, val ptsUs: Long, val width: Int, val height: Int, val rgba: ByteBuffer?, val luma: ByteArray?)
interface VideoFrameSource : Closeable {
    val info: VideoInfo
    suspend fun decode(fromUs: Long = 0L, targetLongEdge: Int = 720,
                       needs: (index: Int, ptsUs: Long) -> FrameNeeds, onFrame: suspend (DecodedFrame) -> Unit)
}
object VideoFrameSources { fun open(file: File): VideoFrameSource }       // GL primero; respaldo retriever
// Extras de VT4a (aditivos, fuera del contrato mínimo):
enum class VideoDecodePath { GL, RETRIEVER }
interface VideoDecodeReport { val path: VideoDecodePath; val fallbackReason: String?; val details: String }   // `source as? VideoDecodeReport`
class VideoDecodeException(message: String, cause: Throwable?) : RuntimeException  // + consumedPtsUs y nextIndex: el respaldo sigue desde ahí
interface ResumableVideoFrameSource : VideoFrameSource { suspend fun decodeFrom(fromUs, targetLongEdge, needs, onFrame, startIndex: Int) }
// Pool de FramePool.SLOTS = 3 huecos por turnos: con Channel(C), SLOTS ≥ C + 2 (VT4c lo sube a 4).
// targetLongEdge nunca amplía; el lado corto se redondea al par más cercano (720 → 406x720 en 9:16).

// data/media/pose
class RgbaFrame(val width: Int, val height: Int, val buffer: ByteBuffer)   // directo, RGBA_8888, stride = width*4
data class DetectedPose(val image: List<LandmarkSample>, val world: List<WorldSample>)   // 33 cada una, orden MediaPipe
interface PoseEstimator : Closeable {
    val engine: PoseEngineInfo
    /** Bloqueante, solo desde el hilo dueño; en VIDEO, timestampMs estrictamente creciente. */
    fun estimate(frame: RgbaFrame, timestampMs: Long): List<DetectedPose>
}

// data/media/technique
enum class AnalysisMode { FULL, RECOMPUTE, PLATE_ONLY }
data class TechniqueAnalysisRequest(val mediaId: String, val videoFile: File, val mode: AnalysisMode, val exercise: ExerciseRef,
    val loadKg: Double?, val plateDiameterMm: Int?, val plateSeed: PlateSeed?, val userHeightCm: Double?, val setHints: Set<String>)
sealed interface TechniqueAnalysisOutcome {
    data class Done(val report: SetTechniqueReport) : TechniqueAnalysisOutcome
    data class Unsupported(val reason: String) : TechniqueAnalysisOutcome
    data class NeedsProfile(val guess: AnalysisProfileId?) : TechniqueAnalysisOutcome
    data class Failed(val errorCode: String, val transient: Boolean) : TechniqueAnalysisOutcome
}
interface TechniqueAnalyzer {
    suspend fun analyze(request: TechniqueAnalysisRequest, onProgress: suspend (percent: Int) -> Unit): TechniqueAnalysisOutcome
}
```

### 3.8 Sidecars
Viven junto al vídeo, en `workout_media/yyyy-MM/`. Se escriben con archivo temporal + rename.

| Archivo | Contenido |
|---|---|
| `<id>.pose2.json.gz` | `PoseTrackV2`, en JSON columnar y gzip (detalle debajo) |
| `<id>.bar.json.gz` | `BarTrack`, igual de columnar: `t[]`, `cx[]`, `cy[]`, `r[]` ×10, `ax[]` ×1000, `q[]` ×100, `src[]`, más la semilla y el diámetro |
| `<id>.analysis.json` | **FUENTE DE VERDAD**: `{"summaryVersion":1,"report":SetTechniqueReport,"exercise":ExerciseRef,"loadKg":…,"setKey":…,"analyzedAtMs":…}`. La fila de Room se reconstruye desde aquí |
| `<id>.capture.json` | metadatos de captura: fps pedidos y concedidos, `gravityRollDeg`, `gravityPitchDeg`, `focalLengthPx`, lente, modelo del dispositivo y `recordedAtMs` |
| `<id>.pose.part` | punto de control cada 150 fotogramas; se borra al terminar |

**Formato de `<id>.pose2.json.gz`:**
- cabecera: `{"v":2, "mediaId", "engine":{…}, "w", "h", "rot", "fps", "stride", "vfr", "captureFps", "fxPx", "roll", "pitch", "aspectKnown", "img":[índices mp], "wld":[índices mp]}`;
- tiempos: `"t0"` (µs) y `"dt"` (µs, frames − 1);
- poses: `"n"` (poses detectadas por fotograma; 0 = sin atleta);
- cada canal por fotograma × punto: `"x"`, `"y"` = round(v·10⁴), con clamp a [−5000, 15000];
- `"z"` = round(z·10³);
- `"vis"`, `"pres"` = round(p·100);
- `"wx"`, `"wy"`, `"wz"` en mm;
- si el fotograma no tiene atleta, todo va a 0 y lo marca `n = 0`.

Tamaño ≈ 0,35 MB/min a 30 Hz.

### 3.9 Room v29 (`video_analysis`) y resumen
- **La migración** está en `data/db/VideoAnalysisMigration.kt`:
  - `fun videoAnalysisMigration(from: Int) = object : Migration(from, from + 1) {…}`, para poder renumerarla;
  - va registrada como 28→29.
- **En `workout_media`:** columnas nuevas `catalogDefinitionId TEXT` y `catalogConfigurationId TEXT`, más `index_workout_media_sessionKey`.
- **Tabla `video_analysis`:**
  - PK `mediaId`;
  - FK a `workout_media(id)` con `ON DELETE CASCADE`.
- **Columnas:**
  - estado: `status` (TEXT: QUEUED/RUNNING/DONE/UNSUPPORTED/NEEDS_PROFILE/FAILED), `progressPct` (INT NOT NULL), `errorCode`;
  - versiones y motor: `engineVersion` (INT NOT NULL), `poseVersion` (INT NOT NULL), `poseEngine`, `profileId`;
  - ejercicio y serie: `exerciseKey`, `configurationKey`, `exerciseName`, `setKey`, `loadKg` (REAL), `recordedAtMs` (INT NOT NULL);
  - captura y vista: `sourceFps`, `analyzedFps`, `viewKind` (NO `view`: es palabra reservada de SQL), `confidence`;
  - repeticiones: `repCount`, `partialReps`, `failedReps`;
  - tiempos: `tutMs`, `tempoEccMs`, `tempoPauseAfterEccMs`, `tempoConMs`, `tempoPauseAfterConMs`;
  - ROM: `romMeanDeg`, `romCvPct`;
  - velocidad: `velocityUnit`, `velocityLossPct`, `mcvFirst`, `mcvBest`, `mcvLast`, `peakBest`;
  - escala y trayectoria: `scaleSource`, `plateDiameterMm`, `barDeviation`, `barDeviationUnit`, `trunkMeanDeg`;
  - `flagsJson` (TEXT NOT NULL), `reportPath`;
  - fechas: `createdAtMs` y `updatedAtMs` (INT NOT NULL).
- **Índices:** `(status)` y `(exerciseKey, recordedAtMs)`.
- **Claves:**
  - `exerciseKey` = `catalogDefinitionId` ?: `canonicalExerciseId` ?: `"name:" + nombre normalizado`; agrupa el hub y las tendencias de ROM y TUT;
  - `configurationKey` = `catalogConfigurationId`; es la clave del perfil carga-velocidad, porque la referencia de carga solo vale en la misma configuración.
- **Copias:** en `BackupTableManifest`, `video_analysis` va como **CACHE**. La restauración hace `DELETE FROM workout_media` y la cascada borra los análisis; `TechniqueAnalysisScheduler.reconcile()` los reconstruye desde `<id>.analysis.json`. De paso, se añade `workout_media_session_associations`, que hoy falta.
- **Cascada:** `@Upsert` es «insert y luego update» y NO dispara la cascada. Nunca pasar escrituras de `workout_media` a `REPLACE`.
- **`VideoAnalysisSummary`** (`data/models`): espejo de la fila, con un mapeador desde `SetTechniqueReport`.

### 3.10 Ajustes
Los `Settings` se guardan con `encodeDefaults = true`, así que cambiar el valor por defecto de la bandera vieja no migraría a nadie. Por eso el campo es nuevo:
```kotlin
@Serializable data class TechniqueVideoSettings(
    val analyzeVideos: Boolean = true, val prefer60Fps: Boolean = true,
    val plateDiameterMm: Int = 450, val maxRecordSeconds: Int = 180,
)
// Settings: val techniqueVideo: TechniqueVideoSettings = TechniqueVideoSettings()   (al final de la clase)
```
`poseTrajectoryEnabled` queda sin uso (VT9 retira sus lecturas).

---

## 4. Motor: preprocesado, geometría, vista y encuadre (VT1)

**Convenciones:**
- **Unidad «FH»** (alto de fotograma):
  - puntos de imagen: X = x·W/H, Y = y (Y hacia abajo);
  - disco: X = px/H, Y = py/H.
- **Notación:**
  - n = lado cercano; f = signo de orientación;
  - θ(A,B,C) = atan2(|BA×BC|, BA·BC), el ángulo incluido en B, en grados;
  - HC (centro de la mano) = ½·muñeca + ¼·(índice + meñique);
  - MF (medio pie) = ½·(talón + punta).
- **Convenios fijados por VT1b** (los respetan VT4c y VT8a):
  - roll > 0 = la imagen está girada en sentido horario;
  - con ψ = 90 (de lado, lado izquierdo del atleta hacia la cámara), el atleta mira a la izquierda de la imagen (f = −1);
  - «world» va en ejes de cámara (x a la derecha, y hacia abajo, z en profundidad), centrado en la cadera;
  - en el generador, PARTIAL_FAR = del 0 al 55 % del recorrido (en el segmentador, REST_END) y PARTIAL_REST = del 45 al 100 % (en el segmentador, FAR_END).
- **Ningún ángulo ni distancia** se calcula sin corregir el aspecto. El `trunkAngleDeg` de v1 lo hacía mal: con un tronco a 30°, un vídeo vertical marcaba 46°.

1. **Tiempo:**
   - ordenar y quitar PTS duplicados; fs = 1/mediana(ΔPTS);
   - VFR si P95/P5 de ΔPTS > 1,3;
   - cámara lenta: tiempo × captureFps/playbackFps;
   - con fs < 20, sin fases ni velocidad (flag LOW_FPS).
2. **Atleta:**
   - el estimador corre con `numPoses = 2`;
   - se elige la pose cuyo centro de torso está más cerca del anterior; en el primer segundo, la de caja más grande;
   - MULTI_PERSON si la 2.ª pose aparece en más del 10 % de los fotogramas.

   Esta elección la hace el pipeline (VT4c) al construir `PoseTrackV2`; VT1 la ofrece como función pura `AthleteSelector`.
3. **Inclinación del móvil:** si |gravityRoll| > 2°, se giran las coordenadas −roll (flag ROLL_CORRECTED). Solo importa en las métricas contra la vertical.
4. **Confianza de un punto:**
   - válido: presencia ≥ 0,5 y visibilidad ≥ 0,5;
   - débil: visibilidad entre 0,3 y 0,5, solo para contar;
   - resto: falta.
5. **Cruces izquierda/derecha** (MediaPipe los cambia de lado). Se reparan ANTES de elegir el lado:
   - Viterbi de 2 estados por par de extremidades: piernas (rodilla, tobillo, talón, punta) y brazos (codo, muñeca);
   - coste = Σ desplazamiento entre fotogramas con el etiquetado dado; penalización por cambio = 0,1·S.
6. **Atípicos:**
   - Hampel por X e Y con k = max(2, round(0,1·fs)) y umbral 3·1,4826·MAD → mediana;
   - salto: |Δp| > 4,5·S·Δt → falta;
   - huesos (solo en SIDE): un segmento 2D fuera de [0,65, 1,25]·L̂ deja la articulación distal como falta.
7. **Remuestreo** lineal a t_k = t0 + k/fs.
8. **Huecos:**
   - ≤ 0,25 s: relleno lineal, marcado;
   - 0,25–0,6 s: relleno solo en la señal de conteo;
   - > 0,6 s: corte.
   - Un giro dentro de un relleno marca la repetición como `turnInGap`.
9. **Paso bajo de fase cero** (`ZeroPhaseLowPass`): Butterworth de 2.º orden en ida y vuelta, con la corrección de Winter aplicada sobre la frecuencia YA deformada: K = tan(π·fc/fs)/0,802 (no fd = fc/0,802, que a 30 fps/5 Hz daba una ganancia de 0,755 en fc; corregido en VT1b).
   - **Coeficientes:** n = 1 + √2·K + K²;
     - b0 = K²/n, b1 = 2·b0, b2 = b0;
     - a1 = 2(K² − 1)/n, a2 = (1 − √2·K + K²)/n.
   - **Implementación:** DF-II transpuesta.
     - Estado inicial estacionario: z2 = (b2 − a2)·x0, z1 = (1 − b0)·x0.
     - Relleno por reflexión impar, padlen = min(N − 1, ⌈1,5·fs/fc⌉).
     - Cada tramo válido va por separado; un tramo demasiado corto, con media móvil centrada.
   - **fc por uso:**
     - métricas: 5 Hz (4 si fs < 25);
     - conteo: clamp(4/T0, 1,5, 4) Hz, o 2,5 Hz antes de conocer T0;
     - disco: 8 Hz a ≥ 50 fps, 6 Hz a 30 fps;
     - gemelos y encogimientos: 3 Hz.
   - **Pruebas:** ganancia en continua 1; un seno en fc sale a 0,707 ± 0,02; correlación cruzada con pico en desfase 0.
10. **Derivadas:** centrales sobre lo filtrado, v_k = (x_{k+1} − x_{k−1})·fs/2. La aceleración solo para el disco calibrado a ≥ 50 fps.
11. **Lado cercano y orientación:**
    - puntuación por lado = mediana de visibilidad de {S, E, W, H, K, A, talón, punta} en la ventana activa;
    - gana el lado con ≥ 0,1 más; si no, el de menor z mediana; queda fijo para la serie;
    - en unilaterales, el lado de trabajo es el de mayor amplitud; si es el lejano, FAR_SIDE_WORKING (confianza máx. MEDIA);
    - f = signo(mediana(X_punta − X_talón)); respaldos: nariz frente a orejas, y tumbado, hombro frente a cadera.
12. **Vista:**
    - ρ = |S_L − S_R| / |S_mid − H_mid|; ψ_2D = acos(clamp(ρ/0,80, 0, 1));
    - ψ_w = atan2(|Δz|, |Δx|) de S_L−S_R y H_L−H_R en «world», promediados;
    - ψ = mediana, en la ventana activa, de la media de ambos. Si discrepan > 25°, la vista es poco fiable.
    - **Clases:**
      - SIDE si ψ ≥ 65° (error ≤ 3° en segmentos sagitales);
      - OBLIQUE si está entre 25° y 65°;
      - FRONT si es ≤ 25°;
      - BACK si la visibilidad de la nariz es < 0,3 y la de las orejas < 0,5;
      - LYING si |τ| > 60° en más del 50 % de los fotogramas.
13. **Escala corporal S** (BH, alturas corporales):
    - L̂ = P90 de la longitud 2D de cada candidato: pierna = muslo + pierna baja, tronco = S_n–H_n, brazo = brazo + antebrazo;
    - se elige el candidato con menor CV robusto (MAD/mediana) y ≥ 60 % de fotogramas válidos;
    - S = L̂/ratio, con Drillis–Contini: pierna 0,491·H, tronco 0,288·H, brazo 0,332·H;
    - se guarda el segmento usado.
14. **`FramingEvaluator`** (puro):
    - **Entrada:** poses de una imagen fija, el `FramingSpec` del perfil, la gravedad y si se vio un disco.
    - **Salida:** `FramingVerdict(ok, issues: List<FramingIssue>)`.
    - **Problemas detectados:**
      - FEET_OUT o HEAD_OUT: puntos fuera del cuadro o a menos del 5 % del borde;
      - TOO_SMALL: el cuerpo ocupa < 40 % del alto;
      - TOO_CLOSE: cuerpo cortado o a < 1 m;
      - WRONG_VIEW: la vista no está en `preferredViews`;
      - TWO_PEOPLE;
      - TILTED: |roll| > 3°;
      - NO_PLATE: `needsPlate` y no se ve el disco;
      - FACE_HIDDEN.
    - **Histéresis:** 3 OK seguidos para «✓ Encuadre».

---

## 5. Perfiles y señales (VT2)

### Señal de conteo
r(t) se orienta para que el reposo o el inicio sea el MÍNIMO y el giro lejano el MÁXIMO. Así sirve un solo segmentador:
- rise(P) = −Y_P/S;
- drop(P) = +Y_P/S;
- arranque E: la subida de r es la excéntrica; arranque C: es la concéntrica;
- el chequeo de polaridad (§6 paso 7) puede darle la vuelta.

### Disco como señal principal
- **Cuándo:** el disco es la señal principal si se cumplen ambas:
  - equipamiento ∈ {barbell, smith_machine, hex_bar, safety_bar, t_bar};
  - cobertura del disco (fotogramas HOUGH o NCC) ≥ 85 % de la ventana activa.
- **Por qué:** de lado, el disco tapa la mano en banca, press, el arranque del peso muerto y el hip thrust.
- **Desfase:** antes de fusionar, se corrige el retraso pose↔disco con la correlación de HC_y con disco_y en ±3 fotogramas.

### Tabla de perfiles
En cursiva, las excepciones por `definitionId`.

| Perfil | Patrones / definiciones | r(t) | Arranque | Ángulo de ROM | Punto de barra | Vistas (contar / medir) | Nivel | MVT |
|---|---|---|---|---|---|---|---|---|
| SQUAT | knee_dominant, knee_hip_dominant, *belt_squat* | drop(H_mid); disco: PLATE_DROP | E (*anderson*: los datos dan C) | rodilla 180−θ(H,K,A); cadera 180−θ(S,H,K) | disco › S_n (barra en la espalda) › HC (goblet/DB) | cualquiera / SIDE | A | 0,30 (barra y smith) |
| LUNGE | unilateral_knee_dominant | drop(H_mid) | E | rodilla delantera (el tobillo adelantado según f en el giro) | disco › S_n › H_mid | SIDE | B | — |
| STEP_UP | *step_up* | rise(H_mid) | C | rodilla delantera | — | SIDE | B | — |
| MACHINE_SQUAT | *prensa, hack, v_squat, pendulum* | 180−θ(H,K,A) | E | rodilla | tobillo (prensa) o S_n (hack), sobre el eje PCA | SIDE | B | — |
| DEADLIFT | deadlift, hip_hinge_deficit (convencional/sumo/hex/DB) | disco: PLATE_RISE; pose: rise(S_n) | C | cadera θ(S,H,K), rodilla | disco › HC (vis ≥ 0,6) › S_n | SIDE | A | 0,15 |
| HINGE | romanian_deadlift, hip_hinge, hip_hinge_lengthened, good morning, pull-through | 180−θ(S_n,H_n,K_n) (a una pierna: τ) | E | cadera | disco › HC (rumano) / S_n (GM) | SIDE | A | — |
| HIP_THRUST | hip thrust, puentes | θ(S_n,H_n,K_n) | C | cadera | disco › H_n | SIDE | A | — |
| BACK_EXTENSION | *hiperextensión 45°, lumbar* | 180−θ(S,H,K) | E | cadera | — | SIDE | B | — |
| BENCH | press horizontales (*máquina/cable/banda: C*) | barra: PLATE_DROP; resto: drop(HC_n) y luego 180−θE | E | codo y ROM_v de la barra | disco › HC › E_n | SIDE | B (A si VT10 la valida) | 0,17 |
| DIP | *fondos, fondos en banco* | drop(S_n) | E | codo | — | SIDE | B | — |
| PUSH_UP | flexiones (+ rodillas, diamante, *pike*) | drop(S_n) | E | codo | — | SIDE | A | — |
| OHP | vertical_push sin jerks | disco: PLATE_RISE; si no, θ(S,E,W) | C | codo | disco › HC | SIDE (mancuernas: FRONT vale) | A | 0,19 (de pie) |
| PULL_VERTICAL | dominadas, jalón, *remo al cuello* (*negative_pull_up*: solo excéntrica) | 1 − d_SW/L̂, con d_SW = \|W − S\| y L̂ = P90(d_SW) (media de lados válidos) | C | codo | velocidad: rise(S_mid) (dominada) o HC (jalón) | FRONT/SIDE, nunca BACK | A | — |
| ROW | remos, invertido, face pull (*renegade/gorilla*: alternos) | como PULL_VERTICAL | C | codo | disco (barra o T) › HC | SIDE | B | 0,50 (solo remo prono / seal row) |
| CURL | elbow_flexion | 180−θ(S,E,W) por lado | C | codo | HC | SIDE; FRONT si es alterno | B | — |
| TRICEPS_EXTENDED_REST | francés, skull crusher, JM, por encima de la cabeza… | 180−θE | E | codo | HC | SIDE | B | — |
| TRICEPS_FLEXED_REST | *polea, patada, katana, cruzado, máquina* | θE | C | codo | HC | SIDE | B | — |
| RAISE | shoulder_abduction_*, shoulder_flexion, *rear_delt_raise* | β = acos(clamp((Y_E − Y_S)/L̂_brazo, −1, 1)) por lado; solo la componente vertical, así que no depende del giro | C | β | — | cualquiera; la simetría necesita FRONT | B | — |
| LEG_EXTENSION | knee_extension | θ(H,K,A) | C | rodilla | — | SIDE | B | — |
| LEG_CURL | knee_flexion | 180−θ(H,K,A) | C | rodilla | — | SIDE | B | — |
| CALF | gemelos (de pie y sentado) | de pie: rise(½(S_mid+H_mid)); sentado: rise(K_n) | C | — | — | SIDE | C | — |
| SHRUG | encogimientos | rise(S_mid) − rise(EAR_mid) | C | — | — | FRONT/SIDE | C | — |
| FLY | *aperturas con mancuernas (plano, inclinado, declinado)* | drop(W_n) | E | — | — | SIDE | C | — |

**Sin soporte en v1** (`Unsupported`, con su motivo):
- hip_hinge_explosive (olímpicos y swings con kettlebell);
- *jerks*, *zercher_carry*, *wall_sit*, *zancadas caminando*;
- familias CORE, GRIP y NECK;
- abductor y aductor en máquina, plancha Copenhague;
- patada de glúteo y reverse hyper;
- crossover y pec deck; banda pull-apart;
- encogimiento Kelso, dominadas escapulares, tibial;
- nórdico inverso; nórdico y GHR (v2, solo excéntrica);
- cossack y Jefferson.

En total, ≈ 108 de las 553 configuraciones.

### Resolver
- **Orden de resolución:**
  1. `userProfileOverride`;
  2. excepciones por `definitionId`;
  3. tabla `movementPatternId` → perfil;
  4. si `isCustom`: `NeedsUserChoice` con la conjetura de `ExercisePatternDetector`.
- **El nombre nunca decide.** `ExercisePatternDetector` falla con «curl femoral», «encogimientos», «hip thrust», «flexión de codo» y «elevaciones laterales».
- **La familia de `CompositionTaxonomy` es demasiado gruesa:**
  - HIP_EXTENSION incluye abductor y aductor;
  - HINGE incluye olímpicos y zercher_carry;
  - knee_dominant incluye wall_sit;
  - HORIZONTAL_PUSH incluye aperturas, crossover y fondos.
- **`usesPlate`:** es true si el equipamiento es de barra (lista de arriba) y el perfil tiene `plateSignal`.
- **El conteo por lado sale de los datos** (§6 paso 9). `laterality` no sirve: NOT_APPLICABLE cubre las mancuernas.
- **Prueba de cobertura** (con el catálogo real `src/main/assets/exercise_catalog_v2.json`, patrón de `ExerciseCatalogContractTest`):
  - cada configuración resuelve a `Supported` o a `Unsupported` explícito, sin `else` silencioso;
  - `Supported` ≥ 75 %;
  - ~40 pares `definitionId` → perfil escritos a mano;
  - el ángulo de ROM del perfil aparece entre las articulaciones PRIMARY o SECONDARY de `jointInvolvement` (ids en español: rodilla, cadera, codo, glenohumeral…).

---

## 6. Segmentador, confianza, fases y TUT (VT2)
```
entrada r[k] (reposo = mínimo, NaN en cortes), fs, perfil, aux{walk[k], barXY[k]?, hints}
1  σn  = 1.4826·MAD(r − LP(r,5))
2  rd  = LP(r, 2.5)
   T0  = primer máximo local de la ACF normalizada de rd en [0.6, 10] s con ρ ≥ 0.3 (o null)
   si T0: rd = LP(r, clamp(4/T0, 1.5, 4))
   dmin = T0 ? clamp(0.45·T0, 0.35, 3.0) : 0.6 s ;  wlen = max(3·T0, 12 s)
3  span = P99(rd) − P1(rd)
   P0   = picos(rd, prominencia ≥ 0.25·span, distancia ≥ dmin, wlen)
   Aref = |P0| ≥ 3 ? mediana(3 mayores prominencias) : máx prominencia
   P    = picos(rd, prominencia ≥ 0.30·Aref, distancia ≥ dmin, wlen)      // meseta → punto medio
4  vL_i = argmin rd en (p_{i−1}, p_i)   (primero: [p_0 − 1.5T, p_0));  vR_i = argmin rd en (p_i, p_{i+1}) (último: hasta +1.5T)
   top = {i: prom_i ≥ 0.7·Aref};  Lrest = mediana(rd[vL], rd[vR] | top);  Afull = mediana(rd[p] − Lrest | top)
   si Afull < perfil.minAmplitude o Afull < 6σn → sin reps (motivo)
      // minAmplitude: ángulos 25–40°; desplazamiento 0.05–0.10 BH; gemelos/encogimientos 0.015–0.025 BH
5  por ciclo: aUp = (rd[p] − rd[vL])/Afull; aDn = (rd[p] − rd[vR])/Afull; e = (rd[p] − Lrest)/Afull; a = min(aUp, aDn)
   FULL si a ≥ 0.70 | PARTIAL si 0.30 ≤ a < 0.70 (FAR_END si e ≥ 0.85, si no REST_END) | ruido si menor
   NONREP si caminar solapa > 20 % (|dX_tobillos/dt| > 0.12 BH/s ≥ 0.3 s, dilatado ±0.3 s; apagado en LUNGE)
          o perfil vertical con ΔX del punto de barra > 1.5·ΔY  (unrack, walkout, re-rack)
   FAILED (arranque C): 0.30 ≤ aUp < 0.70 ∧ concéntrica > 1.5·mediana FULL ∧ |v| < v_th ≥ 0.3 s antes de volver
   último ciclo, arranque C: aUp ≥ 0.70 ∧ (sin vR por fin/corte ∨ sostenido ≥ 0.3 s en ≥ Lrest+0.7A) → FULL
   último ciclo, arranque E: aUp ≥ 0.70 ∧ aDn < 0.5 → FAILED si un intento de vuelta ≥ 0.15A se estanca ≥ 0.5 s; si no, SETDOWN (se ignora)
6  ventana activa = [primera FULL, última FULL]; PARTIAL antes → preparación (salvo gap ≤ 1.5·Tmed y PARTIALS en hints);
   PARTIAL después → se conserva si gap ≤ 1.5·Tmed (≤ miniSetGap con REST_PAUSE); si no, re-rack
7  polaridad: tiempo quieto (|v| ≤ v_th) abajo vs arriba; si quieto_arriba > 2·quieto_abajo ∧ ≥ 2 s → r = −r, una sola vez,
   flag POLARITY_FLIPPED
8  mini-series: corte donde el reposo ≥ max(8 s, 3·mediana del reposo)
9  por lado (perfil.perSide ∧ ambos lados válidos ≥ 70 %): segmentar rL y rR por separado;
   ALTERNO si xcorr(rL,rR,0) < −0.3 o ≥ 80 % de picos alternan → cuenta por lado; SIMÉTRICO si corr > 0.5 → cuenta el lado
   de más confianza (flag si nL ≠ nR); ÚNICO si Afull de un lado < 0.3× el otro; solo el lado cercano válido → lado
   desconocido, confianza máx. MEDIA, consejo «graba de frente»
10 salida: reps = #FULL, partialReps = #PARTIAL, failed = #FAILED
```

### Confianza del conteo
La disponibilidad de cada métrica se lleva aparte.
```
c_track = clamp((fracción válida observada − 0.70)/0.25, 0, 1)
c_snr   = clamp(log10(Afull/σn/5)/log10(6), 0, 1)
c_amp   = clamp(1 − (CV_a(FULL) − 0.10)/0.30, 0, 1)
c_rhy   = clamp(1 − (CV_duración(FULL) − 0.30)/0.50, 0, 1)
c_view  = 1 si la vista ∈ countViews; 0.7 si OBLIQUE; 0.4 en otro caso
n_amb   = ciclos con a ∈ [.22,.38] ∪ [.62,.78], prominencia a ±15 % del umbral, o turnInGap
score   = √c_track · c_view · (0.45·c_snr + 0.30·c_amp + 0.25·c_rhy) · 0.8^n_amb
ALTA  ⇔ score ≥ 0.75 ∧ n_amb = 0 ∧ ¬legado ∧ ¬MULTI_PERSON ∧ ¬POLARITY_FLIPPED
MEDIA ⇔ score ≥ 0.45 ; BAJA en otro caso
BAJA forzada: sin FULL, fracción válida < 0.6 o fs < 20
```
VT10 calibra estos umbrales para que P(conteo exacto | ALTA) ≥ 98 %.

**Ajustes de VT2b al segmentador** (en `AnalysisTuning`, con su razón en el KDoc):
- `failedReturnBelow` sube de 0,5 a 0,70.
- La primera o la última FULL con más de 1,35× el RECORRIDO (no la duración) es preparación o dejada, con ≥ 3 FULL. Sirve para recoger la barra del suelo o dejarla.
- Si la serie acaba arriba, se añade una bajada virtual.
- La distancia mínima entre picos conserva los rebotes de las parciales.
- La «parada» de un intento fallido es un tramo quieto.
- La alternancia por lado se mide entre los centros de los ciclos.
- La polaridad no se invierte si la serie empieza y acaba abajo.
- Con REST_PAUSE, un parcial sigue en la serie con un hueco ≤ 30 s.
- La detección de caminar exige la cadera.
- La puerta de huesos (§4.6) no se aplica al conteo.

### Fases, TUT y tempo
- **Señal:** r_m = LP(r, 5 Hz), o el disco a 8 Hz si es la señal principal y fs ≥ 50.
- **Método (VT2b; reemplaza al umbral del 10 % de la velocidad):** cada media repetición (del reposo al giro y del giro al reposo) se ajusta con el perfil de jerk mínimo s(τ) = 10τ³ − 15τ⁴ + 6τ⁵ (`MovementPieces`):
  - primero, los cruces del 10 %, el 50 % y el 90 % del recorrido, que son robustos porque ahí la pendiente es máxima, dan una salida y una llegada por extrapolación;
  - después, un ajuste de Levenberg–Marquardt, con los niveles libres y límites de cordura, las afina; si el ajuste se sale de esos límites, vale el de los cruces.
  - **Por qué:** con el umbral del 10 % de |v|máx, cada extremo se adelanta 0,087·T. En una fase de 2 s son 0,17 s, y una pausa de 0,2 s saldría de 0,46 s.
- **Eventos:** salida = inicio del tramo de subida; giro = fin de la subida e inicio de la bajada. Si entre ellos hay ≥ 0,25 s, es pausa de giro; si no, es un solo instante. Llegada = fin de la bajada.
- **Límite conocido:** con la cadera como señal, la sentadilla sin disco tiene fases aproximadas (±0,5 s en el generador), porque la altura de la cadera no es lineal con los ángulos.
- **Pausas < 0,25 s:** se dan como 0. A 30 fps no se resuelven.
- **Orden de fases:**
  - arranque E: EXC → P1 (giro) → CON → P2 (reposo);
  - arranque C: CON → P2 (arriba) → EXC → P1 (reposo antes de la siguiente CON).
- **TUT** = Σ (EXC + CON + pausa de giro) de FULL, PARTIAL y FAILED.
  - **No cuenta:**
    - el reposo en la posición inicial (de pie entre sentadillas, barra en el suelo, colgado);
    - los descansos entre mini-series;
    - la preparación, el unrack y el re-rack.
  - **Se guardan también** `setDurationMs` y `restPositionMs`.
  - **Solo excéntrica:** TUT = EXC + giro.
- **Tempo «E-P-C-P»:**
  - mediana de las FULL;
  - en detalle, a 0,1 s; en compacto, enteros, con «X» si C ≤ 0,5 s;
  - fuera de la mediana: P1 de la primera rep en arranque C y P2 de la última en arranque E.

---

## 7. Métricas, observaciones y VBT (VT3)

### ROM
- **Por rep:** |ángulo(giro) − ángulo(salida)|, con la señal de 5 Hz. En RAISE, β.
- **Por serie:** media, DE y CV sobre las FULL.
- **Tendencia** (≥ 4 FULL): Δ = media de las 2 últimas − media de las 2 primeras, más la pendiente OLS en °/rep.
  - Genera observación si |Δ| ≥ 6° y |Δ|/media ≥ 10 %.
- **Vista:**
  - el ROM absoluto solo se da en SIDE (RAISE, en cualquier vista);
  - en OBLIQUE se usan ángulos «world», marcados con ≈ y confianza BAJA.

### Profundidad y criterios
- **Sentadilla.**
  - α = asin(clamp((Y_H − Y_K)/L̂_muslo, −1, 1)) en el fondo; > 0 significa la cadera por debajo de la rodilla.
  - En SIDE se contrasta con atan2(Y_H − Y_K, |X_K − X_H|).
  - Clases: BELOW si α ≥ +5°; PARALLEL si |α| < 5°; ABOVE si α ≤ −5°.
  - No es arbitraje IPF.
  - VT10 calibra el sesgo del punto de cadera de MediaPipe.
- **Dominada.**
  - Mentón ≈ bocaMedia + 1,5·(bocaMedia − nariz), solo en vertical.
  - Altura de la barra ≈ Y media de índice y meñique (los nudillos rodean la barra).
  - `chinOverBar` si Y_mentón ≤ Y_barra − 0,02·L̂_tronco.
  - `fullHang` si θE ≥ 155°.
- **Flexiones:** θE,min ≤ 90° = completa; entre 90 y 110° = ≈; > 110° = corta.
- **Banca.** No hay regla absoluta. Una repetición se marca «más corta» si su fondo queda más de un 10 % de ROM_v (o > 3 cm) por encima de la mediana.
- **Fondos:** asin((Y_S − Y_E)/L̂_brazo) ≥ 0.
- **Bloqueos:**
  - peso muerto: θH ≥ 165° y θK ≥ 160°;
  - hip thrust: θH ≥ 165°;
  - press militar: θE ≥ 160° e Y_W < Y_nariz.

### Trayectoria
Los valores horizontales llevan el signo de f.
- **Referencia:** x_ref = mediana de X_MF, con visibilidad ≥ 0,6 y pies quietos (DE < 0,02 S).
- **Sentadilla, peso muerto y press militar, por repetición:**
  - offMean = media|X − x_ref|;
  - offMax, con signo;
  - drift_con = X(fin CON) − X(inicio CON);
  - excursión = máx X − mín X;
  - VI = 1 − min(1, excursión/ROM_v).
  - Unidades: cm si el disco está calibrado; si no, % de ROM_v con ≈.
  - El desvío absoluto respecto al medio pie pasa por la puerta de paralaje (§8).
- **Banca.** La J es normal (Madsen y McLaughlin 1984). Se mide:
  - las trayectorias CON y EXC remuestreadas en 21 niveles de progreso vertical;
  - X̃(k) = trayectoria mediana;
  - PD_i = RMS_k(X_i(k) − X̃(k)); consistencia = mediana de PD;
  - atípica si PD_i ≥ max(2·mediana PD, 3 cm o 6 % de ROM_v);
  - DE del punto de contacto;
  - desplazamiento de la J = X(bloqueo) − X(contacto), solo descriptivo.

### Tronco
- τ = atan2(f·(X_Sn − X_Hn), Y_Hn − Y_Sn); positivo = inclinado hacia delante. Solo en SIDE y con el giro del móvil corregido.
- **Por rep:** τ en el giro, τ máx., rango, Δτ_temprano (del inicio de la CON al 30 % de su desplazamiento) y τ en el bloqueo.
- **Por serie:** media ± DE, y primera frente a última.

### Velocidad
- **MCV (principal):** desplazamiento en [inicio CON, fin CON] / duración.
  - Se usa la MCV, no la MPV, porque la MPV necesita la segunda derivada.
  - Cerca del 1RM, MPV ≈ MCV, así que las MVT publicadas valen.
  - La MPV solo se guarda si hay calibración y fs ≥ 50; no se muestra en v1.
- **Pico:** solo a fs ≥ 50.
- **Pérdida de velocidad:**
  - VL% = (MCV_mejor − MCV_última)/MCV_mejor·100;
  - mejor = la FULL más rápida; última = la última FULL;
  - requiere ≥ 3 FULL;
  - se excluyen las parciales y las fallidas;
  - no hay pérdida en los perfiles de solo excéntrica.
- **Sin calibrar:**
  - v en BH/s, mostrada solo en % (pérdida, y barras relativas a la mejor);
  - en el historial, solo es comparable con la misma vista y segmento de escala;
  - nunca se da en m/s.
- **Perfil carga-velocidad:**
  - **Puntos:** carga frente a la MCV de la FULL más rápida de cada serie; misma `configurationKey`, calibrado y ≤ 14 días.
  - **Condiciones** (si no se cumplen, «faltan datos»):
    - ≥ 3 cargas distintas;
    - rango de carga ≥ 25 % de la mayor;
    - rango de MCV ≥ 0,30 m/s;
    - MCV del punto más pesado ≤ 0,60 m/s (banca: ≤ 0,50);
    - pendiente negativa;
    - R² ≥ 0,95 con 3 puntos, o ≥ 0,90 con 4 o más.
  - **1RM ≈** (MVT − a)/b, con v = a + b·carga; se redondea a 2,5 kg.
  - **Se oculta si:**
    - supera 1,6× la carga mayor;
    - queda por debajo de la carga mayor;
    - el intervalo de predicción al 95 % es más ancho que ±10 %.
  - **Etiqueta:** «orientativo · beta».
  - Un 1RM real registrado manda sobre la estimación.

### Observaciones
- **Formato:**
  - hechos con su cifra y su repetición; ≈ si no hay calibración;
  - prohibido «error», «incorrecto», «peligroso» o «riesgo»;
  - como mucho 3 por serie, ordenadas por magnitud × reps × confianza;
  - una regla sin sus datos calla.

| Perfil | Regla → umbral | Necesita |
|---|---|---|
| Sentadilla | clase de profundidad por rep; profundidad irregular si DE(α) > 5° o una rep queda ≥ 8° más alta que la mediana | cadera y rodilla con vis ≥ 0,7 en el fondo |
| | cadera adelantada: Δτ_temprano ≥ 8° y subida de cadera / subida de hombro en el 1.er 30 % de la CON ≥ 1,5 | SIDE |
| | deriva de la barra: \|drift_con\| ≥ 5 cm (calibrado) o ≥ 10 % de ROM_v (≈) | disco o S_n |
| | talón que se levanta: talón ≥ 0,06·largo del pie con la punta quieta | SIDE, pie con vis ≥ 0,7 |
| | convergencia de rodillas (nivel C): separación rodillas/tobillos ≤ 0,85× la del fondo en el 1.er 50 % de la CON | FRONT |
| Peso muerto | cadera antes que la barra: ΔY_cadera/ΔY_barra ≥ 2 en el 1.er 15 % de la ROM de la barra, y Δτ ≥ 5° | SIDE |
| | bloqueo: θH < 165° o θK < 160° arriba; inclinación atrás τ ≤ −10° | SIDE |
| | estilo por rep: parado si en el suelo ≥ 0,25 s, si no, continuo; «rebote» si < 0,1 s y la velocidad se invierte en el suelo | disco, ≥ 50 fps para el rebote |
| Rumano / GM | la flexión de rodilla crece ≥ 25° en la EXC; CV del ROM de cadera > 10 %; barra ≥ 8 cm por delante del medio pie en el fondo | SIDE; la barra, solo con disco calibrado y sin paralaje |
| Hip thrust | θH arriba < 165°; pausa media arriba; CV del ROM | SIDE |
| Banca | rep atípica (PD); DE del punto de contacto ≥ 3 cm o 6 % del ROM; rep más corta; en banca con pausa, pausa en el pecho < 0,5 s | disco preferente |
| Flexiones | θE,min > 110° (corta); línea del cuerpo θ(S,H,A) < 165° (cadera que cae o que sube, según el signo del producto vectorial) | SIDE |
| Press militar | inclinación atrás τ ≤ −12° en el pico de velocidad; bloqueo θE < 160°; en press estricto, flexión de rodilla ≥ 15° antes de la CON | SIDE |
| Dominada / jalón | mentón sobre la barra por rep (≈); θE abajo < 155°; balanceo: X de cadera ≥ 0,10 BH o flexión de rodilla ≥ 30°; jalón: Δτ atrás ≥ 15° | FRONT o SIDE |
| Remo | el torso sube ≥ 10° en la CON; remo en polea: balanceo ≥ 15° | SIDE |
| Curl / tríceps | balanceo del tronco ≥ 10° en una rep; deriva del codo: θ(H,S,E) cambia ≥ 25° (curl) o ≥ 20° (tríceps); curl: θE,máx abajo < 150° | SIDE |
| Elevaciones | β pico por rep; asimetría \|β_L − β_R\| arriba ≥ 10°; impulso: rango de τ ≥ 8° o flexión de rodilla ≥ 15° | FRONT para la simetría |

**NUNCA se emite:**
- «rodillas por delante de la punta» (Fry 2003);
- flexión lumbar, «butt wink», espalda redondeada o neutra (no hay puntos de columna);
- cabeza, cuello o mirada;
- valgo como riesgo, o valgo visto de lado;
- desplazamiento de cadera o asimetrías vistas de lado; rotaciones;
- agarre, codos abiertos en banca, arco, barra alta/baja, ancho o ángulo de pies como errores;
- «válido/nulo»;
- m/s o 1RM sin calibrar;
- RIR o RPE por pérdida de velocidad (no en v1);
- diagnósticos de movilidad.

---

## 8. Disco (VT6a, `vision/` sobre luma)
1. **Bordes:**
   - reducción s ∈ {1, 2, 3, 4} para que r/s quede en [20, 45] px;
   - Gauss σ = 1 y Sobel 3×3;
   - bordes con |g| ≥ max(P85 de la ROI, 20).
2. **Hough de gradiente.** Cada borde p con n = g/|g| vota en p ± r·n, para r ∈ [rmin, rmax] y las dos polaridades.
   - **Centro:** acumulador suavizado 3×3 → argmax, con subpíxel cuadrático.
   - **Radio:** histograma de |p − c| ponderado por |g|·|cos∠(n, p − c)|.
   - **Cobertura:** fracción de 36 sectores con apoyo alineado (|cos| ≥ 0,9, a ±1,5 px).
   - **Elección:**
     - al seguir, el pico de radio más cercano a r_prev;
     - al adquirir, el pico MÁS EXTERNO con cobertura ≥ 0,4, para que gane el borde del disco y no el aro ni el buje.
   - **Refino:** Kåsa (círculo por mínimos cuadrados) a resolución completa, con inliers a ±3 px.
   - **Calidad:** q = cobertura × media|cos|.
3. **Adquisición:**
   - **Dónde busca:** ROI de ±4·r_prior alrededor de las dos muñecas.
   - **Radio esperado:**
     - r_b = 0,5·D·k_s, con k_s = S/H_usuario en px por metro (H_usuario = 1,75 m si no se conoce);
     - se busca r en [0,75, 1,7]·r_b, porque el disco está más cerca de la cámara que el cuerpo;
     - se usan los fotogramas con la barra quieta.
   - **Candidatos:** los 3 mejores, con puntuación acumulador × cobertura × exp(−d²/(2(1,5r)²)), donde d es la distancia a la muñeca.
   - **Semilla:** el usuario confirma uno, y ese es la semilla.
4. **Kalman** de velocidad constante, con estado [cx, cy, vx, vy]:
   - Q por eje = σa²·[[Δt⁴/4, Δt³/2], [Δt³/2, Δt²]], con σa = 6 m/s²·k_p;
   - R = σm²·I, con σm = max(0,5, 0,02·r̂·(1,5 − q)) px;
   - ROI = 1,6·r̂ + 3·√P_xx;
   - radio buscado en [0,9, 1,1]·r̂, con r̂ = mediana de los 15 últimos radios aceptados;
   - acepta si q ≥ 0,35 y Mahalanobis² ≤ 9,21.
5. **Pérdidas:**
   - **1–2 fallos:** solo predicción.
   - **≥ 2 fallos, NCC** (detrás de bandera, activada por defecto):
     - plantilla de 2,2·r̂ del último fotograma con q ≥ 0,6;
     - busca en la ROI ×1,5;
     - acepta un pico ≥ 0,6 si el 2.º pico (fuera de 0,5·r̂) es < 0,9·pico;
     - σ de la medida ×2.
   - **Sin NCC y con la muñeca válida:** pseudo-medida W_n + mediana(c − W_n), con σ = 0,25·r̂ y fuente POSE_PRIOR, solo para continuidad.
   - **0,25 s de fallos:** readquisición cerca de las muñecas, con r en [0,85, 1,15]·r̂.
   - **Para la cinemática** solo cuentan HOUGH y NCC. Se interpolan huecos ≤ 0,1 s; con más, esa rep no tiene velocidad.
6. **Escala y geometría:**
   - **Radio de calibración** r̂_cal = mediana de los radios HOUGH con q ≥ 0,5 y |v| < 0,2 m/s (el desenfoque infla los bordes verticales).
   - **Escala** = D/(2·r̂_cal) m/px.
   - **Elipse.**
     - Ajuste alineado a ejes con el centro conocido: minimizar Σ(u·dx² + w·dy² − 1)², con a = 1/√u y b = 1/√w.
     - Da una escala horizontal y otra vertical.
     - Si a/b < 0,85: aviso «cámara no perpendicular a la barra» y corrección (PLATE_ELLIPSE).
     - Si a/b < 0,70: se rechaza (PLATE_REJECTED).
   - **Paralaje.** El plano del disco está ≈ 0,70 m más cerca que el plano sagital (el collarín interior queda a 0,655 m del centro de la barra).
     - La escala del disco vale para la traslación de la barra.
     - NUNCA se aplica a puntos del cuerpo: a 3 m los subestima ~20 %.
     - Error en el desvío respecto al medio pie: e_par ≈ |X_p − c_x|·(1 − Z_p/Z_s)·m/px, con Z_p = f_px·(D/2)/r̂ y Z_s = Z_p + 0,70.
     - Con f_px conocido se corrige X_s = c_x + (X_p − c_x)·Z_p/Z_s. En galería se supone un FOV horizontal de 70°.
     - Si no se puede corregir y e_par > 2 cm, se ocultan los desvíos absolutos (PARALLAX_SUPPRESSED). La deriva y la excursión siguen.
   - **Cordura.** El muslo según la escala del disco × Z_s/Z_p debe quedar a ±25 % del muslo «world»; si no, aviso.
7. **Salida:**
   - `BarSample` por fotograma;
   - por traza: r̂_cal, D, m/px (h, v), cobertura y `poseLag`.

---

## 9. Integración Android
1. **Dependencias (VT-A).**
   - **Versiones:**
     - `com.google.mediapipe:tasks-vision:1.1.0` fijo; nunca `+` ni `latest.release`. Ojo: `0.20230731` es una versión vieja que se ordena como si fuera más nueva.
     - CameraX 1.4.2 → **1.5.3** (pila Camera2, sin Media3). La 1.6.x arrastra Media3 1.9 y estrena la pila nueva (CameraPipe).
     - Media3 se queda en 1.5.1.
     - WorkManager `work = "2.7.1"` → **2.11.2**, más `work-testing`. Hoy nadie lo usa.
     - ML Kit pose → `debugImplementation`; se queda `text-recognition`.
   - **Código:**
     - `PoseTrajectoryAnalyzer.kt` se mueve a `src/debug` y se adapta a `MlKitPoseEstimator`.
     - `WorkoutMediaCaptureController` pierde `attachPoseTrack`, con su `upsert` viejo.
   - **Modelo y empaquetado:**
     - `androidResources { noCompress += "task" }`.
     - El modelo full va en `src/main/assets/mediapipe/pose_landmarker_full.task`.
     - El heavy NO va en el repo: se empuja al dispositivo para el banco.
   - **Reglas R8:**
     ```
     -keep class com.google.mediapipe.** { *; }
     -keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
     -dontwarn com.google.mediapipe.**
     -dontwarn com.google.auto.value.**
     -dontwarn javax.lang.model.**
     ```
     Se quitan solo las reglas de pose de ML Kit.
   - **Manifiesto:**
     - `FOREGROUND_SERVICE_MEDIA_PROCESSING`;
     - `SystemForegroundService` con `foregroundServiceType="dataSync|mediaProcessing"` y `tools:node="merge"`.
   - **Telemetría:** el árbol de dependencias puede traer `com.google.android.datatransport:transport-backend-cct`. VT-A lo deja anotado y VT4b lo comprueba en ejecución.
2. **`VideoFrameSource` (VT4a).** Patrón «ExtractMpegFrames» de bigflake.
   - **Pipeline de decodificación:**
     - EGL14 con pbuffer 1×1, GLES2, textura OES → FBO del tamaño de display escalado a `targetLongEdge`;
     - `glReadPixels` a RGBA directo, con la Y invertida para que las filas vayan de arriba abajo;
     - luma en CPU: `(77r + 150g + 29b) shr 8`.
   - **Rotación:** se lee `KEY_ROTATION` y se pone a 0 antes de `configure()`; la rotación va en la matriz.
   - **Bucle:**
     - un solo buffer con `render = true` → `awaitNewImage(2500 ms)` → `updateTexImage()` → `getTransformMatrix()` → dibujo → lectura;
     - SKIP → `releaseOutputBuffer(i, false)`.
   - **Tiempos:** PTS reales de `BufferInfo`, reparando los no monótonos.
   - **Decodificador:**
     - `KEY_PRIORITY = 1`;
     - `KEY_OPERATING_RATE = Short.MAX_VALUE`; si `configure` falla, se reintenta sin él;
     - en 4K/60 se elige uno con `areSizeAndRateSupported` (hardware primero).
   - **HDR (ST2084/HLG):** con API 31+, se pide `KEY_COLOR_TRANSFER_REQUEST = SDR`; si no, flag HDR_SOURCE.
   - **Respaldo:** con `CodecException` no transitoria repetida → `getFramesAtIndex` en bloques de 30 (API 28+), o `getScaledFrameAtTime` a ≤ 15 fps; confianza máx. MEDIA.
   - **Cancelación y limpieza:** `ensureActive()` en cada fotograma; todo se libera en `finally`, en el hilo GL.
   - **Prueba instrumentada:** con clips generados en la propia prueba (MediaCodec + MediaMuxer: un disco blanco que se mueve, 320×240).
     - Variantes: 30 fps, 60 fps, `setOrientationHint(90)` y VFR.
     - Comprueba: número de fotogramas, PTS, dimensiones giradas y centroide a ±1 px.
3. **`MediaPipePoseEstimator` (VT4b).**
   - **Opciones:** `setNumPoses(2)`, confianzas a 0,5 y sin máscaras.
   - **GPU:**
     - comprobación previa `reqGlEsVersion ≥ 0x30001`;
     - antes de crear, se escribe `gpu_probe=<versionCode>` en SharedPreferences y se borra tras la 1.ª detección; si sigue ahí al arrancar, la GPU queda vetada en esa versión;
     - si crear o detectar lanza una excepción, se recrea en CPU.
   - **Hilos:**
     - `vt-pose` (crear, ejecutar y cerrar) y `vt-decode` (GL);
     - `Channel(2)` y un pool de 3 buffers RGBA.
   - **Tiempos:** ts = max(pts/1000, último + 1).
   - **Imagen:**
     - `ByteBufferImageBuilder(rgba, w, h, MPImage.IMAGE_FORMAT_RGBA)`;
     - si no lo acepta, un Bitmap reutilizado.
     - **Nunca `MPImage.close()`**: recicla el bitmap.
   - **Modo degradado:** con poca RAM (< 3 GB) o temperatura ≥ MODERATE, pose cada 2 fotogramas.
   - **Banco** (`src/debug`, `TechniqueBenchActivity`):
     - analiza un clip con MediaPipe full, heavy (desde un archivo empujado) y ML Kit;
     - vuelca JSON con los ms por fotograma, separados en decodificación y pose;
     - escribe la traza v2.
   - **Telemetría:** comprobar en el emulador (logcat de TransportRuntime y tráfico) que no sale nada; si sale, excluir `transport-backend-cct` y volver a probar.
4. **Pipeline y Worker (VT4c, VT5).**
   - **`TechniqueAnalysisWorker`** (CoroutineWorker): entrada `mediaId` y `mode`.
     - FULL: usuario, `setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)`, política KEEP.
     - RECOMPUTE: con batería no baja, REPLACE.
     - PLATE_ONLY: REPLACE.
   - **`AnalysisGate`:**
     - `Mutex`: analiza de uno en uno;
     - `cameraBusy: MutableStateFlow<Boolean>`: el análisis se suspende mientras se graba, o con temperatura ≥ SEVERE.
   - **Progreso:** en Room, ≤ 1 vez/s y solo con cambios de ≥ 5 %.
   - **Primer plano:** `setForeground` con vídeos > 45 s o importados.
     - `ForegroundInfo`: MEDIA_PROCESSING en API 35+ y DATA_SYNC en 29–34.
   - **Reintentos:** si la excepción es transitoria, `Result.retry()` con backoff de 30 s; con 3 fallos, FAILED.
   - **Sin perfil:** UNSUPPORTED o NEEDS_PROFILE sin decodificar.
   - **Punto de control** `.pose.part` cada 150 fotogramas; al reanudar, `seekTo(lastPts, SEEK_TO_PREVIOUS_SYNC)`.
   - **Borrado:** `repository.delete()` cancela el trabajo y borra los sidecars y los `.part`.
   - **`reconcile()`:**
     - reconstruye las filas desde `<id>.analysis.json`;
     - limpia los huérfanos;
     - con la versión del motor anterior, lanza RECOMPUTE de forma perezosa.
   - **Versiones:**
     - `ENGINE_VERSION` → RECOMPUTE desde los sidecars;
     - `POSE_VERSION` → solo cuando se abre o con «Reanalizar».
   - **Lo que lee la UI:** solo Room.
5. **Repositorios (VT5).**
   - **`VideoAnalysisRepository`:** Flows por sesión, por vídeo, por ejercicio y por configuración.
   - **`WorkoutMediaRepository`:**
     - `setPoseTrackPath` dirigido;
     - `setSetSnapshot(id, reps, weightKg)`;
     - `syncSetSnapshots(sessionKey, completedSets)`;
     - `assignExercise(...)`;
     - `delete` completo.
   - **Captura:** `WorkoutMediaCaptureRequest` y `WorkoutMedia` ganan `catalogDefinitionId` y `catalogConfigurationId` (null por defecto).
6. **Grabador (VT8a).**
   - **`TechniqueRecorderScreen`:** solo vídeo, con `RecorderSessionFactory`.
   - **Niveles de fps:**
     1. grupo de funciones de 60 fps (`SessionConfig` + `isFeatureGroupSupported`);
     2. rango (60, 60) si está entre los soportados;
     3. 30 fps.

     Nunca se combina el grupo con `setFrameRateRange`.
   - **La foto** conserva su enlace de hoy, con 3 casos de uso.
   - **Vista previa:** 16:9 (`RATIO_16_9_FALLBACK_AUTO_STRATEGY`) y FIT_CENTER.
   - **Encuadre:**
     - `PreviewView.bitmap` a ~2 Hz, en modo COMPATIBLE;
     - se reduce a 256 px fuera del hilo principal;
     - MediaPipe IMAGE en CPU, en el hilo `vt-framing`;
     - si la anterior no acabó, se salta.
   - **REC:**
     - empieza con el evento `Start` y avanza con un reloj Compose;
     - se corrige con `recordedDurationNanos`;
     - `setDurationLimitMillis(maxRecordSeconds·1000)`.
   - **Parar al cerrar:** con `DisposableEffect`. Se quita la rama «seguir enlazado si se graba» (`SetCardExerciseMediaBack` ~L441).
   - **Orientación:** `OrientationEventListener` → `targetRotation`; bloqueada mientras se graba.
   - **Gravedad:** `TYPE_GRAVITY` → roll/pitch en `<id>.capture.json`, junto con los fps pedidos y concedidos y `focalLengthPx`. Con |roll| > 3°, «Endereza el móvil».
   - **Lado:** en bilaterales es `null`; `sideToken` sale de `WorkoutMediaPrFlags` a un ayudante común.
   - **Cifras:** se leen de nuevo al finalizar (`requestProvider`).
   - **Tira de miniaturas:** por serie y lado normalizado.
   - **Análisis:** `AnalysisGate.cameraBusy` según `isRecording`.
7. **Resultado en la sesión (VT8b, tras LS2b y LS3).**
   - **`WorkoutVideoAnalysisController`:**
     - `videoAnalysisBySetKey: StateFlow<Map<String, SetVideoAnalysisUi>>` al final del VM;
     - con varios vídeos para una serie, manda el último DONE.
   - **Pieza `WorkoutVideoRepsSuggestion`:**
     - estados `Analyzing(p)` / `Suggest` / `Mismatch` / `Inconclusive`;
     - va en `WorkoutSetPanel` (parámetro opcional al final) y en `videoSlot` de `WorkoutRestPanel`.
   - **«Usar» en una serie sin registrar:** local en la tarjeta (texto de reps y parciales).
   - **«Corregir» en una serie registrada:**
     - `patchCompletedSet(key, CompletedSetPatch)`: misma puerta que la voz y Room primero;
     - reutiliza `applyVoiceEditPatch` y las reps lógicas = reps + 0,5·parciales (`WorkoutSetRecorder` ~L236; se extrae `logicalReps()`);
     - el 1RM estimado y los PR quedan como estaban, igual que con la voz;
     - después, `syncSetSnapshots`.
   - **Tras «Terminar»:** solo lectura, en la pantalla de análisis.
8. **Pantallas (VT7, VT9).**
   - **Rutas** en `Navigation.kt` y en el NavHost de **`MainActivity.kt`** (junto a los álbumes, ~L1530): `TechniqueHub` «technique», `TechniqueAnalysis` «technique/analysis/{mediaId}», `TechniqueTrends` «technique/trends/{exerciseKey}» (`Uri.encode`) y `TechniqueCompare` «technique/compare/{a}/{b}».
   - **Reproductor:**
     - Media3 1.5.1, `SeekParameters.EXACT`;
     - paso a paso: `floor(nextPtsUs/1000)` con los PTS del sidecar;
     - bucle por rep con `PlayerMessage` (`setDeleteAfterDelivery(false)`);
     - capa sin sondeo: `setVideoFrameMetadataListener` → `AtomicLong` → bucle `withFrameNanos`. Se dibuja en la fase de pintado; el rectángulo sale de `player.videoSize`.
   - **Calibración:**
     - fotograma del fondo de la rep 1;
     - `detectTransformGestures` y lupa (`Modifier.magnifier` en API 28+);
     - chips 45/40/35/otra;
     - guarda la semilla y lanza PLATE_ONLY.
   - **Galería fuera de sesión:**
     - `PickVisualMedia(VideoOnly)`;
     - ejercicio: recientes o catálogo con `CatalogLaunchRequest(origin = TECHNIQUE, SINGLE)` y `CatalogSavedStateKeys.RESULT` en el back stack;
     - `submitUriCapture` sin sesión (álbum `day:<fecha>`), con `createdAtMs` de `METADATA_KEY_DATE`;
     - límites: 10 min; aviso con más de 4K/60.
   - **Ajustes:** quitar las lecturas de `poseTrajectoryEnabled`:
     - `WorkoutViewModel` (~L424);
     - `WorkoutScreen` (~L1337 y ~L1388);
     - `WorkoutAlbumsViewModel` (~L134);
     - la prueba (~L20).

---

## 10. Pruebas
1. **`SyntheticLifter`** (VT1; en `src/test/.../domain/biomechanics/testing/`):
   - **Entradas:**
     - perfil;
     - antropometría a partir de la altura (Drillis–Contini);
     - cámara: ψ, inclinación, roll, distancia, HFOV 70°, vertical u horizontal;
     - guion: `Idle`, `Walk(dx)`, `Unrack`, `Rerack`, `Rest` y `Rep(E, P1, C, P2, amp, side, kind)`.
   - **Movimiento:** fases de jerk mínimo s(τ) = 10τ³ − 15τ⁴ + 6τ⁵ sobre ángulos → cinemática directa → proyección estenopeica. «World» = 3D − cadera, + 2 cm de ruido.
   - **Captura:**
     - fps 24/30/60, con fluctuación del PTS de ±3 ms y 1–3 % de fotogramas perdidos;
     - ruido σ = 0,002–0,01 FH, con 1 % de atípicos ×10;
     - visibilidad: 0,95 en el lado cercano y 0,3 en el lejano.
   - **Defectos:** oclusiones, cruces izquierda/derecha y una 2.ª persona.
   - **Semilla** fija.
   - **Salida:** `PoseTrackV2` + verdad (tiempos de rep, fases, ROM, clase y lado).
2. **Matriz:**
   - limpio y barrido de ruido;
   - walkout, unrack y re-rack;
   - sentadilla con pausa;
   - rest-pause con 3 mini-series;
   - parciales en estiramiento;
   - última fallida;
   - peso muerto continuo frente a parado;
   - rumano con recogida y dejada;
   - banca con la muñeca tapada (vis 0,1);
   - curl alterno de frente;
   - laterales de lado frente a de frente (β igual);
   - giro de 0 a 90° (vista);
   - VFR y pérdidas;
   - cruces izquierda/derecha;
   - vertical frente a horizontal (ángulos iguales);
   - v1.
3. **Aceptación sintética:**
   - conteo exacto con SNR ≥ 10;
   - fases a ±1 fotograma;
   - ROM ±2° con ψ ≥ 80°;
   - vista ≥ 95 % (fuera de ±5° de las fronteras).
4. **Disco sintético** (VT6a): 720×1280 luma.
   - **Fondo:** degradado + ruido de valor + 3–6 barras verticales (rack) + un círculo distractor estático de 0,7R.
   - **Disco:**
     - R de 40 a 130 px;
     - aro al 8 % de R, buje de 0,2R y agujero;
     - «letras» aleatorias;
     - contraste de 15 a 80 DN, con polaridad aleatoria.
   - **Movimiento:**
     - reps de jerk mínimo;
     - ROM vertical de 250 a 600 px y horizontal de ±15 px;
     - pausas;
     - 30 y 60 fps;
     - desenfoque |v|·t_exp, con t_exp de 1/120 a 1/60 s.
   - **Perturbaciones:**
     - oclusor con forma de brazo (≤ 35 % del aro) y montante (≤ 20 %);
     - ruido σ de 2 a 6 DN y parpadeo de ±5 %;
     - elipse a/b ∈ {1, 0,94, 0,85}.
   - **Aceptación:**
     - centro: error mediano ≤ 1 % de D, P95 ≤ 2 % de D;
     - radio ≤ 1 %;
     - MCV ≤ 0,02 m/s a 60 fps y ≤ 0,03 a 30 fps;
     - nunca se queda con el distractor;
     - readquiere en ≤ 5 fotogramas;
     - a/b a ±0,02.
5. **Banco dorado (VT10).**
   - **Fuentes:** solo con permiso del usuario por vídeo.
   - **Metadatos por clip:**
     - fuente, licencia y atribución;
     - `configurationId`;
     - vista y altura de la cámara;
     - fps real (sin clips editados ni en cámara lenta);
     - número de personas;
     - si se ve el disco y su diámetro;
     - carga, si se conoce.
   - **Etiquetas:**
     - por rep, con precisión de fotograma: salida, inicio y fin del giro, llegada;
     - clase (FULL, PARTIAL, FAILED o NONREP) y lado;
     - profundidad, mentón sobre la barra y bloqueo;
     - 10 fotogramas con el círculo del disco marcado.
   - **Reparto:**
     - 60 % para ajuste y 40 % reservado, separados por fuente;
     - como mucho 3 rondas de ajuste;
     - se congela antes de evaluar la reserva.
   - **Al repo solo van** las trazas derivadas, las etiquetas y la atribución; nunca el vídeo.
   - **Aceptación:**
     - **Conteo:**
       - exacto en ≥ 95 % (A) y ≥ 90 % (B) de las series;
       - ninguna serie con más de 1 de error;
       - precisión en ALTA ≥ 98 %, informando de su cobertura.
     - **Tiempos:**
       - eventos con MAE ≤ 50 ms a 30 fps;
       - ICC(2,1) de CON/EXC ≥ 0,90.
     - **Profundidad:** κ ≥ 0,6.
     - **Vista:** ≥ 90 %.
     - **Disco:** mediana ≤ 1 % de D y P95 ≤ 3 %.
     - **m/s:** NO se validan con vídeos públicos. Hace falta una sesión en el teléfono con un desplazamiento conocido antes de quitar el «≈».

---

## 11. Paquetes, archivos exclusivos y orden
| Paquete | Qué | Archivos exclusivos | Depende de |
|---|---|---|---|
| VT0 (orquestador) | esta especificación, decisiones, modelos, brief común y briefs | `docs/video-technique/` | — |
| VT-A | dependencias, manifiesto, R8, modelo, ML Kit pose a debug, quitar `attachPoseTrack` | `build.gradle.kts`, `libs.versions.toml`, `proguard-rules.pro`, `AndroidManifest.xml`, `src/main/assets/mediapipe/`, `PoseTrajectoryAnalyzer.kt` → `src/debug`, `WorkoutMediaCaptureController.kt` (solo quitar), arreglos de compilación por CameraX 1.5.3 | VT0 |
| VT1a | pose v2, barra y sus códecs, `signal/` | `domain/biomechanics/{pose,bar,signal}` + sus pruebas | VT-A |
| VT1b | `kinematics/` (geometría con aspecto, escala, lado, vista, cruces izquierda/derecha, atleta), `framing/`, `SyntheticLifter` | `domain/biomechanics/{kinematics,framing}`, `src/test/.../biomechanics/{testing,kinematics,framing}` | VT1a |
| VT2a | perfiles, resolver y cobertura del catálogo | `…/profiles` | VT1b |
| VT2b | preparación de la traza, señal de conteo, segmentador, confianza, fases/TUT/tempo (y los tipos `AnalysisTuning`, `AnalysisFlag`, `CountConfidence`, `RepKind`, `TempoSummary`) | `…/reps`, parte de `…/metrics` | VT2a |
| VT3a | métricas por repetición y por serie (ROM, profundidad, bloqueo, tronco, trayectoria, velocidad relativa), `SetTechniqueReport` y `TechniqueAnalysisEngine` (sin observaciones ni m/s) | `…/metrics` | VT2b |
| VT3b | `vbt/` (escala por disco con elipse y paralaje, m/s, pérdida, carga-velocidad, MVT) y observaciones con su texto, integradas en el motor | `…/vbt`, `…/metrics/Observations*` | VT3a |
| VT6a | disco sobre luma | `…/vision` | VT1 |
| VT4a | `VideoFrameSource` + prueba instrumentada | `data/media/video/`, `src/androidTest/.../data/media/video/` | VT-A |
| VT5a | Room v29, DAO, sidecars, resumen, ajustes, ids de catálogo en la captura, repositorio de medios dirigido | `data/db/*` (VideoAnalysis*, KpknDatabase, BackupTableManifest, WorkoutMediaEntity), `schemas/29.json`, `data/media/technique/TechniqueSidecars.kt`, `WorkoutMediaRepository.kt`, `data/models/{WorkoutMedia,VideoAnalysisSummary,Settings}.kt` | VT4a, VT3a (`SetTechniqueReport`) |
| VT5b | Worker, cola, `AnalysisGate`, `VideoAnalysisRepository`, encolado al guardar, `reconcile` (con un `TechniqueAnalyzer` «no disponible» hasta VT4c) | `data/work/*`, `data/repository/VideoAnalysisRepository.kt`, `data/media/technique/TechniqueAnalyzer.kt`, `AppBootstrap.kt` (1 línea) | VT5a |
| VT4b | `MediaPipePoseEstimator`, sonda de GPU, banco `src/debug` | `data/media/pose/`, `src/debug/` | VT-A |
| VT4c | pipeline (pose + disco), sidecars, puntos de control, `ExerciseRefResolver`, analizador real en el Worker | `data/media/technique/` | VT3, VT6a, VT4b, VT5 |
| VT7a | reproductor de técnica (vídeo, capa sincronizada, controles, línea de repeticiones) e iconos FrameBack/FrameForward/Target | `screens/technique/player/` | VT4c |
| VT7b | pantalla «Análisis de técnica» (VM, módulos, calibración del disco) | `screens/technique/analysis/` | VT7a |
| VT8a | grabador a pantalla completa (encuadre, 60 fps, REC, metadatos), lado normalizado, cifras al finalizar, miniaturas por serie, iconos Camera/Record | `screens/workout/technique/`, `WorkoutMediaCaptureController.kt`, `SetCardExerciseMediaBack.kt`, `data/media/WorkoutMediaSides.kt` (+ mínimos en `SetExecutionCard`/`WorkoutV2Body`) | VT7b |
| VT9a | tendencias por ejercicio y comparar dos vídeos (+ sus rutas) | `screens/technique/{trends,compare}/`, `Navigation.kt`/`MainActivity.kt` (bloques nuevos) | VT8a |
| VT9b | hub con ajustes, importar de la galería, rutas del hub y del análisis, entradas en Perfil y en el visor; quitar las lecturas de `poseTrajectoryEnabled` | `screens/technique/{hub,importer}/`, `Navigation.kt`, `MainActivity.kt`, `ProfileScreen.kt`, `WorkoutMediaViewerScreen.kt`, `CatalogContracts.kt` | VT9a |
| VT8b | resultado en la sesión en vivo (usar, corregir, fallo, ver análisis) | final de `WorkoutViewModel.kt`, `WorkoutVideoAnalysisController.kt`, `design/WorkoutVideoRepsSuggestion.kt`, `WorkoutSetPanel`/`WorkoutRestPanel` (parámetros al final), `WorkoutVoiceSet*`, `WorkoutSessionAlbumSheet.kt` | VT9b |
| VT10 | banco dorado, ajuste, QA en el emulador, docs y mapas | `src/test/.../golden`, `docs/` | todo |

**Orden:**
1. VT0, luego VT-A.
2. Carriles (1 agente si el disco tiene < 20 GB libres): dominio VT1a → VT1b → VT2a → VT2b → VT3a → VT3b → VT6a ‖ Android VT4a → VT4b → VT5a (cuando VT3a esté integrado) → VT5b (D26).
3. VT4c.
4. VT7a → VT7b → VT8a → VT9a → VT9b → VT8b (carril Android, en serie).
5. VT10.
