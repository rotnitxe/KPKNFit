# VT1a · Pose v2, barra, códecs y procesado de señal (dominio puro)

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt1a`, desde `feat/video-technique` con VT-A integrado.
- **Antes de empezar, lee:**
  - `docs/video-technique/briefs/VT_COMUN.md`;
  - `docs/video-technique/VIDEO_TECHNIQUE_V1.md` §3.1, §3.2, §3.8 (formatos) y §4 puntos 1, 4 y 6–10;
  - el código v1, `domain/biomechanics/TrajectoryMetrics.kt` (clases `PoseTrack`, `PoseFrame`, `PoseJoint`, `NormalizedPoint`), que se queda tal cual.
- **Logs:** `C:\kw\logs\vt-1a-<n>.log`.

Todo es Kotlin puro en `domain/biomechanics/`, sin `android.*`, y con pruebas JVM. Es la base sobre la que se apoyan VT1b, VT2, VT3 y VT6a, así que las firmas de §3 se respetan al pie de la letra.

## Archivos permitidos (todos nuevos)
- `domain/biomechanics/pose/`: `PoseLandmark.kt`, `PoseTrackV2.kt` (con `PoseFrameV2`, `LandmarkSample`, `WorldSample`, `PoseEngineInfo`), `PoseTrackCodec.kt`, `LegacyPoseTrackAdapter.kt`.
- `domain/biomechanics/bar/`: `BarTrack.kt` (con `BarSample`, `BarSampleSource`, `PlateSeed`) y `BarTrackCodec.kt`.
- `domain/biomechanics/signal/`: `RobustStats.kt`, `Timebase.kt`, `Resampler.kt`, `GapFiller.kt`, `Hampel.kt`, `ZeroPhaseLowPass.kt`, `Derivative.kt`, `Autocorrelation.kt`, `PeakFinder.kt`.
- Pruebas en `src/test/java/com/example/kpkn/domain/biomechanics/{pose,bar,signal}/`.

## Qué haces

### 1. `pose/`
Implementa §3.1 tal cual.
- **`PoseLandmark`:** con `KEPT_IMAGE` (0 y 7..32, 27 puntos) y `KEPT_WORLD` (11..16 y 23..28, 12 puntos).
- **`PoseFrameV2`:**
  - Por dentro, sin mapas: `FloatArray` de 27×5 (x, y, z, vis, pres) para la imagen y de 12×3 para «world», más `posesDetected`.
  - `point(lm)` y `world(lm)` devuelven `null` en tres casos: el punto no se guarda, no hay pose o falta el dato.
  - Constructor de fábrica `PoseFrameV2.of(tUs, posesDetected, image: Map<PoseLandmark, LandmarkSample>?, world: Map<PoseLandmark, WorldSample>?)`.
  - Constructor interno a partir de arrays, para el códec.
- **`PoseTrackV2`:** la `data class` de §3.1.
- **`PoseTrackCodec.encode/decode`:**
  - JSON columnar cuantizado (§3.8) con un DTO privado `@Serializable` (`IntArray`) y `kotlinx.serialization.json`, todo comprimido con `java.util.zip.GZIPOutputStream`.
  - `decode` devuelve `null` si el contenido no es gzip válido, no es JSON o `v != 2`.
  - Un fotograma sin pose (`n = 0`) se escribe con ceros y al decodificar vuelve como `hasPose = false`.
- **`LegacyPoseTrackAdapter.toV2(v1: PoseTrack)`:**
  - mapea `PoseJoint` → `PoseLandmark`;
  - vis = pres = `inFrameLikelihood`; z = 0; sin «world»;
  - `nominalFps = sampleFps`; `aspectKnown = false`;
  - `frameWidth/Height` = `videoWidth/Height` de v1, o 1×1 si faltan;
  - `engine = PoseEngineInfo("mlkit-pose", "base", "CPU")`; tiempo = `timestampMs·1000`.

### 2. `bar/`
§3.2.
- `BarTrackCodec`: JSON columnar con gzip y su propia cuantización (r ×10, ax ×1000, q ×100); `src` como índice del enum.

### 3. `signal/`
Todo con `DoubleArray`, y `NaN` = falta. Sin asignar dentro de los bucles por muestra.

**Firmas:**
```kotlin
object RobustStats { fun median(x: DoubleArray): Double; fun mad(x: DoubleArray): Double /* 1.4826·MAD */;
    fun percentile(x: DoubleArray, p: Double): Double; fun cv(x: DoubleArray): Double }        // ignoran NaN; NaN si vacío
data class Timebase(val t0Us: Long, val fs: Double, val n: Int, val vfr: Boolean, val p95OverP5: Double)
object Resampler {
    fun timebase(tUs: LongArray): Timebase                                  // ordena/deduplica; fs = 1/mediana(ΔPTS); vfr si P95/P5 > 1.3
    fun toUniform(tUs: LongArray, v: DoubleArray, tb: Timebase, maxGapS: Double = 0.6): DoubleArray   // lineal; huecos > maxGapS → NaN
}
data class GapFillResult(val values: DoubleArray, val filled: BooleanArray)
object GapFiller { fun fill(x: DoubleArray, fs: Double, maxGapS: Double): GapFillResult }       // solo huecos interiores ≤ maxGapS
object Hampel { fun filter(x: DoubleArray, halfWindow: Int, nSigma: Double = 3.0): DoubleArray } // sustituye por la mediana
data class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)
object ZeroPhaseLowPass {
    fun design(fs: Double, fcEffective: Double): Biquad                     // fd = fc/0.802 (Winter), K = tan(π·fd/fs)
    fun filter(x: DoubleArray, fs: Double, fcEffective: Double): DoubleArray
}
object Derivative { fun central(x: DoubleArray, fs: Double): DoubleArray }   // extremos con diferencia hacia un lado; NaN se propaga
object Autocorrelation {
    fun normalized(x: DoubleArray, maxLag: Int): DoubleArray
    fun firstPeakLagS(x: DoubleArray, fs: Double, minLagS: Double = 0.6, maxLagS: Double = 10.0, minRho: Double = 0.3): Double?
}
data class Peak(val index: Int, val value: Double, val prominence: Double, val leftBase: Int, val rightBase: Int)
object PeakFinder {
    /** Máximos locales con prominencia ≥ minProminence y distancia ≥ minDistance (gana el mayor); mesetas → punto medio;
     *  prominencia calculada en una ventana de ±wlen/2 muestras (como scipy.signal.find_peaks con wlen). */
    fun find(x: DoubleArray, minProminence: Double, minDistance: Int, wlen: Int): List<Peak>
}
```

**`ZeroPhaseLowPass.filter`:**
- DF-II transpuesta, hacia delante y hacia atrás.
- Estado inicial estacionario: z2 = (b2 − a2)·x0, z1 = (1 − b0)·x0.
- Relleno por reflexión impar, padlen = min(N − 1, ⌈1,5·fs/fc⌉).
- Cada tramo sin `NaN` se filtra por separado; los `NaN` se conservan.
- Un tramo de menos de 3·padlen muestras va con media móvil centrada de ventana ≈ fs/fc.

## Pruebas (JVM, `org.junit.Assert`)
- **`PoseTrackCodecTest`:**
  - ida y vuelta de una traza con 300 fotogramas (algunos sin pose, otros con 2 poses detectadas, VFR): x e y con error ≤ 1e-4, vis ≤ 0,01, world ≤ 1 mm;
  - cabecera idéntica;
  - basura → `null`;
  - `v = 1` → `null`.
- **`LegacyPoseTrackAdapterTest`:** una traza v1 (con el estilo de los generadores de `TrajectoryMetricsTest`) → v2 con los mismos puntos, `aspectKnown = false` y el tiempo en µs.
- **`BarTrackCodecTest`:** ida y vuelta.
- **`ZeroPhaseLowPassTest`:**
  - ganancia en continua 1 (± 1e-6 lejos de los bordes);
  - un seno a la fc pedida sale con amplitud 0,707 ± 0,02, a fs 30 y 60;
  - correlación cruzada entrada/salida con pico en desfase 0;
  - un tramo con NaN en medio deja los NaN y filtra cada lado;
  - una serie corta (5 muestras) no lanza excepción.
- **`ResamplerTest`:** PTS con fluctuación de ±3 ms → fs ≈ 30; un 2 % de huecos → interpolados; un hueco de 1 s → NaN; VFR detectado.
- **`HampelTest`:** un atípico ×10 se sustituye; una rampa limpia no cambia.
- **`AutocorrelationTest`:** seno de 2,5 s de periodo con ruido → 2,5 ± 0,1 s; ruido blanco → `null`.
- **`PeakFinderTest`:** casos de scipy (prominencia, distancia, meseta y bordes).

**Comandos:**
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.biomechanics.pose.*" --tests "com.example.kpkn.domain.biomechanics.bar.*" --tests "com.example.kpkn.domain.biomechanics.signal.*"`

## Commits
- `feat(technique): modelo de pose v2, barra y sus códecs`
- `feat(technique): procesado de señal del analizador (remuestreo, Hampel, Butterworth de fase cero, picos)`
- `test(technique): …` si van aparte.
