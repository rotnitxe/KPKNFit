# VT1b · Cinemática, vista, encuadre y generador sintético (dominio puro)

- **Worktree y rama:** `C:\kw\vt-b` (carril de dominio), rama `vt/vt1b`, desde `feat/video-technique` con VT1a integrado.
- **Antes de empezar, lee:**
  - `docs/video-technique/briefs/VT_COMUN.md`;
  - `docs/video-technique/VIDEO_TECHNIQUE_V1.md` §3.1, §4 (entero) y §10 puntos 1–3;
  - el código de VT1a: `domain/biomechanics/{pose,signal}`.
- **Logs:** `C:\kw\logs\vt-1b-<n>.log`.

Todo es Kotlin puro con pruebas JVM.
- El **generador sintético** es la herramienta de pruebas de toda la ola (VT2, VT3, VT6a y VT10). Hazlo sólido y determinista.
- La **geometría** corrige SIEMPRE el aspecto. En v1, un tronco a 30° en un vídeo vertical marcaba 46°: ese error no puede repetirse.

## Archivos permitidos (todos nuevos)
- `domain/biomechanics/kinematics/`: `Vec2.kt`, `Side.kt`, `FrameGeometry.kt`, `Angles.kt`, `BodyScale.kt`, `NearSide.kt`, `Facing.kt`, `ViewKind.kt`, `ViewClassifier.kt`, `LeftRightSwapRepair.kt`, `AthleteSelector.kt`.
- `domain/biomechanics/framing/`: `FramingSpec.kt` y `FramingEvaluator.kt`.
- `src/test/java/com/example/kpkn/domain/biomechanics/testing/SyntheticLifter.kt`, más sus tipos en ese paquete.
- Pruebas en `src/test/java/com/example/kpkn/domain/biomechanics/{kinematics,framing,testing}/`.

## Firmas
`ViewKind` vive en `kinematics` y `FramingSpec` en `framing`. VT2 los importa desde ahí.
```kotlin
data class Vec2(val x: Double, val y: Double)      // operadores +, -, *, norm(), dot(), cross()
enum class Side { LEFT, RIGHT }
class FrameGeometry(val track: PoseTrackV2, val minVisibility: Float = 0.5f, val minPresence: Float = 0.5f, val rollDeg: Double = 0.0) {
    /** Punto en unidades FH (X = x·W/H, Y = y; Y hacia abajo), girado −roll alrededor del centro; null si falta o no
     *  supera la confianza. Con weak = true basta visibilidad ≥ 0.3 (solo para contar). */
    fun point(i: Int, lm: PoseLandmark, weak: Boolean = false): Vec2?
    fun mid(i: Int, a: PoseLandmark, b: PoseLandmark, weak: Boolean = false): Vec2?
    fun handCenter(i: Int, side: Side): Vec2?          // ½·muñeca + ¼·(índice + meñique); si falta un dedo, la muñeca
    fun midFoot(i: Int, side: Side): Vec2?             // ½·(talón + punta)
    val frameCount: Int
}
object Angles {
    fun included(a: Vec2, b: Vec2, c: Vec2): Double    // θ(A,B,C) = atan2(|BA×BC|, BA·BC) en grados, 0..180
    fun trunkLean(shoulder: Vec2, hip: Vec2, facing: Int): Double   // τ = atan2(f·(X_S − X_H), Y_H − Y_S) en grados; + = adelante
}
enum class ScaleSegment { LEG, TRUNK, ARM }
data class BodyScale(val bodyHeightFh: Double, val segment: ScaleSegment, val segmentLengthFh: Double, val robustCv: Double)
object BodyScaleEstimator { fun estimate(geo: FrameGeometry, frames: IntRange): BodyScale? }
data class SideEstimate(val near: Side, val margin: Double, val method: String)   // "visibility" | "depth"
object NearSideSelector { fun select(track: PoseTrackV2, frames: IntRange): SideEstimate? }
object FacingEstimator { fun sign(geo: FrameGeometry, near: Side, frames: IntRange, lying: Boolean = false): Int }   // +1 / −1
enum class ViewKind { SIDE, FRONT, OBLIQUE, BACK, LYING, UNKNOWN }
data class ViewEstimate(val kind: ViewKind, val psiDeg: Double?, val psi2dDeg: Double?, val psiWorldDeg: Double?, val reliable: Boolean)
object ViewClassifier { fun classify(geo: FrameGeometry, frames: IntRange): ViewEstimate }
object LeftRightSwapRepair { fun repair(track: PoseTrackV2, scaleFh: Double): PoseTrackV2 }
object AthleteSelector {
    fun select(candidates: List<Map<PoseLandmark, LandmarkSample>>, frameWidth: Int, frameHeight: Int, previousTorsoCenter: Vec2?): Int
    fun torsoCenter(pose: Map<PoseLandmark, LandmarkSample>, frameWidth: Int, frameHeight: Int): Vec2?
}
data class FramingSpec(val preferredViews: Set<ViewKind>, val needsPlate: Boolean, val needsFeet: Boolean, val needsFace: Boolean = true)
enum class FramingIssue { NO_PERSON, TWO_PEOPLE, TOO_CLOSE, FEET_OUT, HEAD_OUT, TOO_SMALL, WRONG_VIEW, FACE_HIDDEN, TILTED, NO_PLATE }
data class FramingVerdict(val ok: Boolean, val issues: List<FramingIssue>, val view: ViewKind?)
class FramingEvaluator(private val okStreakRequired: Int = 3) {
    fun evaluate(poses: List<Map<PoseLandmark, LandmarkSample>>, frameWidth: Int, frameHeight: Int, spec: FramingSpec,
                 rollDeg: Float?, plateSeen: Boolean?): FramingVerdict
    fun reset()
}
```

## Reglas
Los umbrales de cada regla están en §4.
- **`ViewClassifier`:**
  - ψ_2D = acos(clamp(ρ/0,80, 0, 1)), con ρ = |S_L − S_R| / |S_mid − H_mid|;
  - ψ_w sale de los puntos «world» (si existen) de S_L−S_R y H_L−H_R: atan2(|Δz|, |Δx|), promediado;
  - ψ = mediana de la media de los dos; si discrepan más de 25°, `reliable = false`;
  - BACK y LYING, según §4.12.
- **`NearSideSelector`:**
  - mediana de visibilidad de {hombro, codo, muñeca, cadera, rodilla, tobillo, talón, punta} por lado;
  - si la diferencia es < 0,1, gana el de menor z mediana.
- **`BodyScaleEstimator`:** con P90 y CV robusto, como en §4.13.
- **`LeftRightSwapRepair`:**
  - Viterbi de 2 estados por par de extremidades: piernas (rodilla, tobillo, talón, punta) y brazos (codo, muñeca);
  - el coste es la suma de desplazamientos entre fotogramas; la penalización por cambio, 0,1·S;
  - en el estado «cruzado» se intercambian los puntos izquierdo y derecho de ese grupo;
  - devuelve una traza nueva.
- **`FramingEvaluator`:**
  - **Orden de prioridad de los problemas:**
    1. NO_PERSON;
    2. TWO_PEOPLE (2.ª pose con caja ≥ 30 % de la principal);
    3. TOO_CLOSE (el cuerpo ocupa > 95 % del alto o le faltan las dos puntas de los extremos);
    4. FEET_OUT o HEAD_OUT (punto fuera o a < 5 % del borde, si `needsFeet`/`needsFace`);
    5. TOO_SMALL (< 40 % del alto);
    6. WRONG_VIEW (solo ψ_2D);
    7. FACE_HIDDEN;
    8. TILTED (|roll| > 3°);
    9. NO_PLATE (`needsPlate` y `plateSeen == false`).
  - `ok` exige 3 evaluaciones seguidas sin problemas; con un problema, la racha vuelve a 0.

## `SyntheticLifter` (fuentes de prueba)
### Tipos
```kotlin
enum class SynthMovement { SQUAT, LUNGE, DEADLIFT, RDL, HIP_THRUST, BENCH, PUSH_UP, OHP, PULL_UP, LAT_PULLDOWN, ROW, CURL,
    TRICEPS_PUSHDOWN, LATERAL_RAISE, LEG_EXTENSION, CALF_RAISE }
enum class SynthRepKind { FULL, PARTIAL_FAR, PARTIAL_REST, FAILED }
data class SynthCamera(val yawDeg: Double = 90.0, val pitchDeg: Double = 0.0, val rollDeg: Double = 0.0, val distanceM: Double = 3.0,
    val heightM: Double = 1.0, val hfovDeg: Double = 70.0, val widthPx: Int = 720, val heightPx: Int = 1280)
sealed interface SynthSegment { Idle(seconds); Walk(seconds, dxM); Unrack(seconds = 1.5); Rerack(seconds = 1.5); Rest(seconds);
    Rep(eccS, p1S, conS, p2S, amplitude = 1.0, side: Side? = null, kind = FULL) }
data class SynthNoise(val sigmaFh: Double = 0.004, val outlierRate: Double = 0.01, val dropRate: Double = 0.01, val ptsJitterMs: Double = 3.0,
    val farSideVisibility: Float = 0.3f, val swapGlitches: Int = 0, val secondPersonFraction: Double = 0.0,
    val occlusionWindowsS: List<Pair<Double, Double>> = emptyList(), val occludedLandmarks: Set<PoseLandmark> = emptySet()) {
    companion object { val NONE: SynthNoise }   // todo a 0 y visibilidad lejana 0.95
}
data class SynthConfig(val movement: SynthMovement, val heightM: Double = 1.75, val camera: SynthCamera = SynthCamera(),
    val fps: Double = 30.0, val script: List<SynthSegment>, val noise: SynthNoise = SynthNoise(), val seed: Long = 42L,
    val alternating: Boolean = false)
data class SynthRepTruth(val index: Int, val kind: SynthRepKind, val side: Side?, val departUs: Long, val turnStartUs: Long,
    val turnEndUs: Long, val arriveUs: Long, val romDeg: Double)
class SynthResult(val track: PoseTrackV2, val reps: List<SynthRepTruth>, val primaryAngleDeg: DoubleArray /* verdad por fotograma */)
object SyntheticLifter { fun generate(config: SynthConfig): SynthResult }
```

### Modelo
1. **Cuerpo 3D:**
   - **Ejes:** X hacia delante del atleta, Y arriba, Z hacia su izquierda.
   - **Longitudes** (fracción de la altura, Drillis–Contini):
     - pierna: muslo 0,245, pierna baja 0,246, pie 0,152 (talón→punta), altura del tobillo 0,039;
     - tronco: cadera→hombro 0,288, hombro→nariz 0,13 (nariz 0,05 hacia delante), orejas a ±0,07;
     - brazo: brazo 0,186, antebrazo 0,146, mano 0,108 (índice, meñique y pulgar en la punta, con desvíos pequeños);
     - anchuras: hombros 0,259, caderas 0,191.
   - **Boca:** dos puntos a ±0,025 bajo la nariz.
2. **Postura por fotograma:**
   - raíz: posición y orientación (de pie, boca arriba, boca abajo, colgado);
   - τ del tronco;
   - por lado: flexión de cadera y de rodilla, tobillo, flexión y abducción del hombro, flexión del codo.
3. **Movimientos:** cada `SynthMovement` define dos posturas, de inicio y de giro, y su arranque (E o C, como en §5 de la especificación).
   - **Restricciones:**
     - de pie, con los pies fijos: la cadena se calcula desde el tobillo hacia arriba;
     - PULL_UP: con las manos fijas en la barra, se calcula desde las manos;
     - BENCH: el tronco va fijo en el banco, boca arriba, y se mueven los brazos;
     - PUSH_UP: el cuerpo es una tabla que gira sobre las puntas, según la flexión del codo.
   - **Interpolación:** jerk mínimo s(τ) = 10τ³ − 15τ⁴ + 6τ⁵ en cada fase.
   - **Tipos de repetición:**
     - `amplitude` escala el cambio de ángulo;
     - PARTIAL_FAR: el 55 % del recorrido desde el inicio;
     - PARTIAL_REST: el 55 % alrededor del giro;
     - FAILED: la CON llega al 50 %, se queda quieta 0,8 s y vuelve.
   - **`alternating`** reparte las repeticiones entre izquierda y derecha, alternando (CURL y LATERAL_RAISE).
   - **Fuera de las repeticiones:**
     - `Walk` desplaza la raíz;
     - `Unrack` y `Rerack` desplazan las manos y la barra en horizontal, sin ciclo vertical completo.
4. **Cámara** (estenopeica): posición a `distanceM` del atleta según el yaw ψ.
   - **Orientación:**
     - ψ = 90 → de lado, con el lado izquierdo del atleta hacia la cámara;
     - ψ = −90 → el lado derecho;
     - ψ = 0 → de frente.
   - **Geometría:** pitch y roll, HFOV 70°, `widthPx×heightPx`.
   - **Salida:**
     - x e y normalizados;
     - z = profundidad relativa a la cadera, escalada como x;
     - visibilidad:
       - 0,95 en el lado cercano;
       - en el lado lejano, interpolada entre 0,95 (de frente) y `farSideVisibility` (de lado, ψ = ±90);
       - la nariz y las orejas bajan si la cara mira hacia el otro lado;
     - presencia 0,99;
     - «world» = 3D − punto medio de las caderas + N(0, 2 cm).
5. **Ruido y defectos** (deterministas con `seed`):
   - **Ruido:** gaussiano σ (en FH) en x e y; atípicos ×10σ; fotogramas sin pose (`dropRate`); fluctuación del PTS.
   - **`swapGlitches`:** N tramos de 2 a 5 fotogramas con las piernas cruzadas.
   - **`secondPersonFraction`:** fotogramas con `posesDetected = 2`.
   - **Oclusiones:** visibilidad 0,1 y posición con ruido ×5.
6. **Verdad:**
   - por repetición: salida, inicio y fin del giro, llegada y ROM del ángulo principal (rodilla en sentadilla y zancada, cadera en los de bisagra, codo en los de brazo, β en laterales);
   - `primaryAngleDeg` por fotograma.

## Pruebas (JVM)
- **`SyntheticLifterTest`:**
  - la misma semilla da la misma traza;
  - el número de repeticiones verdaderas coincide con el guion;
  - SQUAT de lado (ψ = 90, sin ruido): el ángulo de rodilla medido con `FrameGeometry` + `Angles` coincide con la verdad a ±2°, en vertical y en horizontal;
  - PULL_UP: las manos fijas.
- **`FrameGeometryTest`:**
  - un tronco a 30° en 3D se mide como 30 ± 1° en un vídeo vertical de 720×1280 y en uno horizontal de 1280×720;
  - el giro del móvil se corrige;
  - los puntos débiles solo se ven con `weak = true`.
- **`BodyScaleTest`:** H estimada a ±5 % de lado, sin ruido; a ±8 % con el ruido por defecto.
- **`NearSideTest` y `FacingTest`:**
  - ψ = 90 → LEFT; ψ = −90 → RIGHT;
  - el signo cambia al girar el atleta.
- **`ViewClassifierTest`:** barrido de ψ de 0 a 90 en pasos de 5°, de pie (SQUAT).
  - Clases correctas en ≥ 95 % de los casos, excluyendo ±5° alrededor de 25° y de 65°.
  - BENCH → LYING.
  - Con nariz y orejas de visibilidad 0,1 → BACK.
- **`LeftRightSwapRepairTest`:** 5 cruces inyectados → se corrigen ≥ 95 % de sus fotogramas, sin cruces nuevos en la traza limpia.
- **`AthleteSelectorTest`:** dos poses; se sigue a la que mantiene la continuidad aunque la otra sea más grande.
- **`FramingEvaluatorTest`:** un caso por cada `FramingIssue`, más la histéresis: 3 OK seguidos y la racha que se rompe.

**Comandos:**
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.biomechanics.kinematics.*" --tests "com.example.kpkn.domain.biomechanics.framing.*" --tests "com.example.kpkn.domain.biomechanics.testing.*"`

## Commits
- `feat(technique): geometría con aspecto, escala corporal, lado cercano, vista y cruces izquierda/derecha`
- `feat(technique): evaluador de encuadre`
- `test(technique): generador sintético de levantamientos (SyntheticLifter)`
