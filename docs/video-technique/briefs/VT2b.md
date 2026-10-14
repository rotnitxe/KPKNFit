# VT2b · Señal de conteo, segmentador, confianza, fases y TUT (dominio puro)

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt2b`, desde `feat/video-technique` con VT2a integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3.4–§3.6, §4 (puntos 1–13), §5 («Señal de conteo», «Disco como señal principal» y la tabla) y §6, ENTERA;
  - el código de VT1a, VT1b y VT2a (`signal`, `kinematics`, `profiles` y `testing/SyntheticLifter`).
- **Logs:** `C:\kw\logs\vt-2b-<n>.log`.

Este paquete es el corazón del conteo. Los umbrales van TODOS en `AnalysisTuning`; nada de números mágicos sueltos.

## Archivos permitidos (nuevos)
- **`domain/biomechanics/metrics/`:** solo estos tipos, que VT3a completará:
  - `AnalysisTuning.kt`: la parte de §4 y §6;
  - `AnalysisFlag.kt`;
  - `CountConfidence.kt`;
  - `RepKinds.kt`: `RepKind` y `PartialKind`;
  - `TempoSummary.kt`: `@Serializable`, con `label()`.
- **`domain/biomechanics/reps/`:**
  - `TrackPreparation.kt`;
  - `CountSignal.kt`: `CountSignal` y `CountSignalBuilder`;
  - `RepSegmenter.kt`;
  - `RepConfidence.kt`;
  - `RepPhases.kt`.
- **Pruebas:** en `src/test/java/com/example/kpkn/domain/biomechanics/reps/`.

## Firmas
```kotlin
// metrics/AnalysisTuning.kt — data class con valores por defecto = los de §4/§6; companion DEFAULT. VT3a añadirá campos AL FINAL.
// metrics/AnalysisFlag.kt — el enum de §3.5. metrics/CountConfidence.kt — ALTA, MEDIA, BAJA.

class PreparedTrack(
    val track: PoseTrackV2,            // tras LeftRightSwapRepair
    val geo: FrameGeometry,            // con rollDeg corregido si |roll| > 2°
    val timebase: Timebase,
    val scale: BodyScale?,
    val near: SideEstimate?,
    val facing: Int,
    val view: ViewEstimate,
    val flags: Set<AnalysisFlag>,      // VFR, LOW_FPS, LEGACY_TRACK, MULTI_PERSON (> 10 % con 2 poses), ROLL_CORRECTED, OBLIQUE_VIEW, BACK_VIEW
)
object TrackPreparation { fun prepare(track: PoseTrackV2, tuning: AnalysisTuning = AnalysisTuning.DEFAULT): PreparedTrack }

enum class SignalUnit { BODY_HEIGHTS, DEGREES, RATIO }
class CountSignal(
    val t0Us: Long, val fs: Double,
    val r: DoubleArray,                 // orientada (reposo = mínimo), remuestreada, Hampel, huecos ≤ 0.25 s rellenos; NaN = corte
    val filled: BooleanArray, val weak: BooleanArray,
    val walk: BooleanArray,             // caminar (dilatado ±0.3 s); todo false en LUNGE/STEP_UP
    val barX: DoubleArray?, val barY: DoubleArray?,   // punto de barra en BH (disco o sustituto) para NONREP horizontal
    val perSide: Map<Side, DoubleArray>?,             // rL/rR si perfil.perSide y ambos lados válidos ≥ 70 %
    val source: String,                  // "plate" | "pose"
    val unit: SignalUnit,
    val validFraction: Double,           // observada (sin rellenos ni débiles)
)
sealed interface CountSignalResult { data class Ok(val signal: CountSignal, val prepared: PreparedTrack) : CountSignalResult;
    data class NoSignal(val reason: String) : CountSignalResult }
object CountSignalBuilder {
    fun build(prepared: PreparedTrack, bar: BarTrack?, profile: AnalysisProfile, tuning: AnalysisTuning = AnalysisTuning.DEFAULT): CountSignalResult
}

enum class SideMode { BOTH_SYNC, ALTERNATING, SINGLE, NEAR_ONLY, NOT_APPLICABLE }
data class RepCycle(val index: Int, val kind: RepKind, val partialKind: PartialKind?, val side: Side?,
    val leftValley: Int, val peak: Int, val rightValley: Int?, val amplitudeFraction: Double, val turnInGap: Boolean)
data class DiscardedCycle(val peak: Int, val reason: String)     // "noise", "nonrep_walk", "nonrep_horizontal", "setup", "rerack", "setdown"
data class SegmentationResult(
    val cycles: List<RepCycle>, val discarded: List<DiscardedCycle>, val activeWindow: IntRange?, val miniSets: Int,
    val periodS: Double?, val noiseSigma: Double, val aFull: Double, val lRest: Double,
    val sideMode: SideMode, val perSideCounts: Map<Side, Int>, val polarityFlipped: Boolean, val noRepsReason: String?,
) { val fullCount: Int; val partialCount: Int; val failedCount: Int }
object RepSegmenter {
    fun segment(signal: CountSignal, profile: AnalysisProfile, hints: Set<String>, tuning: AnalysisTuning = AnalysisTuning.DEFAULT): SegmentationResult
}

data class ConfidenceBreakdown(val cTrack: Double, val cSnr: Double, val cAmp: Double, val cRhy: Double, val cView: Double,
    val nAmbiguous: Int, val score: Double, val level: CountConfidence, val forcedLowReason: String?)
object RepConfidence {
    fun score(signal: CountSignal, seg: SegmentationResult, view: ViewEstimate, profile: AnalysisProfile,
              flags: Set<AnalysisFlag>, tuning: AnalysisTuning = AnalysisTuning.DEFAULT): ConfidenceBreakdown
}

data class RepPhaseResult(val cycleIndex: Int, val departUs: Long, val turnStartUs: Long, val turnEndUs: Long, val arriveUs: Long,
    val eccentricMs: Int?, val turnPauseMs: Int?, val concentricMs: Int?, val restPauseMs: Int?)
data class PhaseSummary(val reps: List<RepPhaseResult>, val tutMs: Long?, val setDurationMs: Long?, val restPositionMs: Long?,
    val tempo: TempoSummary?)
object RepPhases {
    fun compute(signal: CountSignal, seg: SegmentationResult, profile: AnalysisProfile, tuning: AnalysisTuning = AnalysisTuning.DEFAULT): PhaseSummary
}
```

## Reglas
1. **`TrackPreparation`** (§4):
   - `LeftRightSwapRepair` con una S provisional (mediana de |S_mid − A_mid| / 0,80);
   - `FrameGeometry` con roll si |roll| > 2°;
   - `Timebase`;
   - `BodyScale`, `NearSide`, `Facing` y `ViewClassifier` sobre los fotogramas con pose;
   - las flags.
2. **`CountSignalBuilder`.** Implementa cada `CountSignal` de §3.4 con la fórmula de §5, sobre la geometría de `PreparedTrack`, y la orienta con la posición de inicio o reposo en el MÍNIMO.
   - **Fórmulas** (BH = FH/S; S = `bodyHeightFh`):

     | Señal | Fórmula | Unidad |
     |---|---|---|
     | DROP_HIP_MID | +Y_Hmid/S | BH |
     | RISE_HIP_MID | −Y_Hmid/S | BH |
     | KNEE_FLEXION | 180 − θ(H,K,A) del lado cercano | ° |
     | KNEE_EXTENSION_ANGLE | θ(H,K,A) | ° |
     | HIP_FLEXION | 180 − θ(S,H,K) | ° |
     | HIP_ANGLE | θ(S,H,K) | ° |
     | RISE_SHOULDER_NEAR | −Y_Sn/S | BH |
     | DROP_SHOULDER_NEAR | +Y_Sn/S | BH |
     | DROP_HAND_NEAR | +Y_HCn/S | BH |
     | ELBOW_FLEXION | 180 − θ(S,E,W) | ° |
     | ELBOW_ANGLE | θ(S,E,W) | ° |
     | SHOULDER_TO_WRIST_CLOSE | 1 − d_SW/L̂, con L̂ = P90(d_SW) y la media de los lados válidos | RATIO |
     | ARM_ELEVATION | β = acos(clamp((Y_E − Y_S)/L̂_brazo, −1, 1)) | ° |
     | RISE_TRUNK_MID | −Y(½(S_mid + H_mid))/S | BH |
     | RISE_KNEE_NEAR | −Y_Kn/S | BH |
     | SHRUG_SIGNAL | (Y_EAR_mid − Y_S_mid)/S | BH |
     | DROP_WRIST_NEAR | +Y_Wn/S | BH |
     | PLATE_DROP | +(cy/H)/S | BH |
     | PLATE_RISE | −(cy/H)/S | BH |

     El valor absoluto da igual: el segmentador trabaja con diferencias.
   - **Disco como señal:** se usa el disco si `profile.usesPlate`, hay `bar` y la cobertura HOUGH+NCC es ≥ 85 % de los fotogramas con pose.
     - Antes, se corrige el desfase pose↔disco por correlación cruzada de HC_y con disco_y en ±3 fotogramas.
     - `source = "plate"`. Si no se cumple, se usa la pose.
   - **Sin escala:** si `scale == null` en una señal en BH → `NoSignal("no_scale")`.
   - **Huecos:** relleno ≤ 0,25 s; de 0,25 a 0,6 s también, pero marcado (solo vale para contar); > 0,6 s → NaN.
   - **`walk`:** |dX_tobillos/dt| > 0,12 BH/s durante ≥ 0,3 s, dilatado ±0,3 s; apagado en LUNGE y STEP_UP.
   - **`barX`/`barY`:** el punto de barra del perfil (`BarPointRule`): disco o sustituto de pose, en BH.
3. **`RepSegmenter`:** el pseudocódigo de §6, pasos 1–10, al pie de la letra.
   - `LP` = `ZeroPhaseLowPass`; los picos, con `PeakFinder`.
   - **Polaridad:** se comprueba con el tiempo quieto (paso 7) y se reintenta UNA vez con −r.
   - **`startPhase` del perfil:** decide FAILED y el último ciclo según arranque C o E.
   - **Pistas** (`hints`, nombres de `SetTechniqueV2`): REST_PAUSE (mini-series) y PARTIALS (parciales tras la última completa).
   - **`perSide`:** paso 9.
4. **`RepConfidence`:** la fórmula de §6 al pie de la letra, con sus casos de BAJA forzada.
5. **`RepPhases`** (§6, «Fases, TUT y tempo»):
   - señal r_m = LP(r, 5 Hz), o la señal del disco a 8 Hz si fs ≥ 50;
   - v = derivada central;
   - umbral de velocidad, pausas ≥ 0,25 s, interpolación lineal en los cruces y parabólica en los picos;
   - orden de las fases según `startPhase`;
   - TUT, duración de la serie, tiempo en reposo y tempo (mediana de las completas, con las exclusiones de §6).

## Pruebas (JVM, con `SyntheticLifter`)
- **`RepSegmenterMatrixTest`:** un caso por fila de §10.2 (matriz).
  - Para los casos que necesitan disco (banca con la muñeca tapada; peso muerto continuo frente a parado), construye un `BarTrack` sintético con las muñecas de la verdad: cx/cy en píxeles, radio constante y fuente HOUGH.
  - **Aceptación:**
    - conteo de completas exacto con SNR ≥ 10;
    - parciales y fallidas bien clasificadas en sus casos;
    - setup y re-rack descartados;
    - 3 mini-series en rest-pause;
    - el curl alterno de frente, contado por lado;
    - los laterales, de lado y de frente, con el mismo número.
- **`RepSegmenterNoiseSweepTest`:** σ de 0,002 a 0,012 FH. Imprime la tabla de aciertos; exacto en ≥ 95 % de las 40 semillas con σ ≤ 0,008.
- **`RepConfidenceTest`:**
  - limpio y de lado → ALTA;
  - oblicuo a 45° → no ALTA;
  - MULTI_PERSON, legado v1 o fs < 20 → no ALTA;
  - sin completas → BAJA.
- **`RepPhasesTest`:**
  - guion con EXC 2,0 s, P1 1,0 s, CON 1,0 s y P2 0,5 s → mediana de cada fase a ±1 fotograma (33 ms a 30 fps, 17 ms a 60 fps);
  - pausa de 0,2 s → 0;
  - TUT sin el reposo de pie entre repeticiones;
  - etiqueta de tempo «2-1-1-0».
- **`CountSignalBuilderTest`:**
  - orientación correcta en cada perfil A: el reposo es el mínimo;
  - la misma señal en vertical que en horizontal;
  - el disco sustituye a la pose cuando hay cobertura;
  - `NoSignal` sin escala.

**Comandos:**
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.biomechanics.reps.*"`

## Commits
- `feat(technique): señal de conteo por perfil y preparación de la traza`
- `feat(technique): segmentador de repeticiones v2 (parciales, fallidas, mini-series y por lado)`
- `feat(technique): confianza del conteo, fases, TUT y tempo`
- `test(technique): matriz sintética del conteo`
