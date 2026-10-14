# VT3b · VBT con disco (escala, m/s, carga-velocidad) y observaciones (dominio puro)

- **Worktree y rama:** `C:\kw\vt-b` (carril de dominio), rama `vt/vt3b`, desde `feat/video-technique` con VT3a integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3.5, §3.6, §7 («Velocidad», «Perfil carga-velocidad», «Observaciones») y §8 punto 6 (escala, elipse y paralaje);
  - el motor de VT3a.
- **Logs:** `C:\kw\logs\vt-3b-<n>.log`.

## Heredado de VT3a (ya integrado; los nombres REALES mandan sobre este brief)
- **Firmas:**
  - `TechniqueAnalysisInput(pose, bar, profile, loadKg, userHeightCm, setHints, tuning)`;
  - `TechniqueAnalysisEngine.analyze(input): SetTechniqueReport`;
  - `MetricContext(prepared, series, seg, phases, profile, tuning)`;
  - `RomMetrics`, `DepthRules`, `TrunkMetrics`, `PathMetrics` y `RelativeVelocity` exponen `.compute(ctx)` y devuelven `*Summary`. La trayectoria va en BH, para que tú la pases a cm.
- **Campos añadidos:** `SetTechniqueReport.noRepsReason` y `romSlopeDegPerRep`, y `RepReport.trunkMaxDeg`.
- **`AnalysisTuning` está al límite de la JVM:** como mucho 255 palabras de parámetros, y un Double ocupa 2. VT3a ya movió sus 30 umbrales a `MetricsTuning`, que es UN campo.
  - Tú añades tus umbrales en grupos propios, `VbtTuning` y `ObservationsTuning`, cada uno como UN campo al final de `AnalysisTuning`.
  - No añadas campos sueltos: darían `ClassFormatError`.
- **Pérdida de velocidad ruidosa en la sentadilla sin disco** (aviso de VT3a: 7 % con una pérdida real de 0). La causa es que las fases de la cadera tienen ±0,5 s de error. Arréglalo en `RelativeVelocity`, que se permite para esto:
  - para la pérdida (VL%), mide la velocidad de cada repetición en la ventana del 10 % al 90 % del desplazamiento de la CON: 0,8·recorrido / (t90 − t10), con los cruces de `MovementPieces`. Es robusta al error de la salida y de la llegada, y como se mide igual en todas las repeticiones, el porcentaje no se sesga;
  - el MCV en m/s con el disco calibrado sigue siendo el de la CON completa (convención VBT), con los instantes de `RepPhaseResult`;
  - si aun así la dispersión entre repeticiones de la misma serie sin fatiga no es fiable (CV de la velocidad de las 3 primeras > umbral), no se da la VL y queda `available` sin VELOCITY_RELATIVE;
  - **pruebas nuevas:**
    - sentadilla sin disco con una pérdida real de 0 → |VL| ≤ 3 %;
    - con una pérdida real del 33 % → 33 ± 4 %;
    - las mismas con disco.
- **No hecho en VT3a:** SYMMETRY (valgo y asimetría de frente, nivel C) no entra en v1. Si una regla de observaciones de §7 lo necesita, esa regla calla; anótalo.

## Archivos permitidos
- **`domain/biomechanics/vbt/` (nuevos):** `PlateScale.kt`, `CalibratedVelocity.kt` y `LoadVelocityProfile.kt`.
- **`domain/biomechanics/metrics/` (nuevos):** `Observations.kt` (las reglas) y `ObservationText.kt` (los textos).
- **Para integrar:** `TechniqueAnalysisEngine.kt`, `PathMetrics.kt`, `RelativeVelocity.kt` (la ventana 10–90 % de la VL) y `AnalysisTuning.kt` (solo tus grupos nuevos, al final).
- **Pruebas:** en `src/test/java/com/example/kpkn/domain/biomechanics/{vbt,metrics}/`.

## Qué haces
1. **`PlateScale.estimate(bar: BarTrack, fps: Double, focalLengthPx: Float?): PlateScaleResult`** (§8 punto 6).
   - Solo si `plateDiameterMm != null`.
   - **Radio de calibración** r̂_cal = mediana de los radios HOUGH con q ≥ 0,5 en los fotogramas lentos: velocidad < 0,4·r̂ por segundo, para quitar el desenfoque.
   - **Elipse.** Tiene que dar `axisRatio`:
     - si `BarSample.axisRatio` viene medido (≠ 1), se usa la mediana;
     - si no, se toma 1.
   - **Escalas:** mPerPxV y mPerPxH, corregidas por la elipse.
     - a/b < 0,85 → PLATE_ELLIPSE.
     - a/b < 0,70 → PLATE_REJECTED y sin escala.
   - **Paralaje:**
     - Z_p = f_px·(D/2)/r̂, con f_px de la traza o, si falta, de un FOV horizontal de 70° y el ancho del fotograma;
     - Z_s = Z_p + 0,70;
     - el error del desvío absoluto va en `parallaxErrorCm`;
     - si f_px es conocido, se corrige; si no lo es y el error es > 2 cm, PARALLAX_SUPPRESSED.
2. **`CalibratedVelocity`.** Con escala válida y `source == "plate"`:
   - desplazamiento vertical del disco × mPerPxV → MCV en m/s por repetición;
   - pico, solo con fs ≥ 50;
   - VL% como en VT3a;
   - `velocityUnit = MPS`.
   - Si no, se quedan las relativas de VT3a.
3. **`PathMetrics` calibrada.** Con escala:
   - desvío, deriva y excursión en cm (`barDeviationUnit = "cm"`);
   - el desvío respecto al medio pie, solo si no hay PARALLAX_SUPPRESSED; la deriva y la excursión, siempre.
4. **`LoadVelocityProfile.estimate(points, mvtMps)`** (§3.6 y §7 «Perfil carga-velocidad»): todas las condiciones.
   - Regresión lineal de v = a + b·carga.
   - 1RM = (MVT − a)/b, redondeado a 2,5 kg.
   - Se oculta si supera 1,6× la carga máxima, si queda por debajo de ella o si el intervalo de predicción al 95 % es > ±10 %.
   - Devuelve `NotEnough(reason)` con estos códigos: `few_loads`, `narrow_range`, `low_velocity_spread`, `heaviest_too_fast`, `positive_slope`, `low_r2`, `implausible`, `wide_interval`.
5. **`Observations.evaluate(report, prepared, profile, scaleInfo): List<TechniqueObservation>`.** Implementa la tabla de §7 «Observaciones» regla por regla, con sus umbrales en `AnalysisTuning`.
   - **Requisitos:** cada regla comprueba los suyos (vista, visibilidad, disco, fps); si le falta alguno, calla.
   - **Orden:** por magnitud × número de reps × confianza.
   - **Máximo 3.**
   - **Lista de prohibidas:** se respeta (§7 «NUNCA se emite»), y una prueba lo comprueba.
6. **`ObservationText`.** Textos en español neutro, en tipo frase, con cifra y repetición. Ejemplos:
   - «En las repeticiones 7 y 8 la cadera no bajó del paralelo (≈ 6° por encima).»
   - «El tronco se inclinó 9° más en la última repetición que en la primera.»
   - «La barra se desplazó 5 cm hacia delante durante la subida en la repetición 4.»
   - «Barbilla sobre la barra en 6 de 8 repeticiones.»
   - **Prohibido:** «error», «incorrecto», «peligroso», «riesgo» y emojis.
   - **Unidades:** «≈» cuando no hay calibración; en cm o en °, según corresponda.
7. **Motor.** `TechniqueAnalysisEngine` usa `PlateScale` y `CalibratedVelocity` cuando hay disco calibrado y añade las observaciones.
   - Rellena `scale`, `available` (VELOCITY_MPS, PEAK_VELOCITY, PATH_ABSOLUTE) y las flags.
   - `ENGINE_VERSION` sigue en 1: aún no se ha publicado nada.

## Pruebas (JVM)
- **`PlateScaleTest`:**
  - disco de 450 mm con r = 90 px → 0,0025 m/px ± 1 %;
  - a/b = 0,8 → PLATE_ELLIPSE y escalas distintas por eje;
  - a/b = 0,65 → rechazada;
  - paralaje suprimido sin f_px cuando el desvío lo exige.
- **`CalibratedVelocityTest`:** traza sintética de disco con desplazamiento y tiempo conocidos → MCV a ±0,02 m/s a 60 fps y a ±0,03 m/s a 30 fps.
- **`LoadVelocityProfileTest`:**
  - perfil limpio de 4 cargas (60, 80, 100 y 120 kg; v = 1,3 − 0,0085·carga) con MVT 0,17 → 1RM ≈ 132,5 kg;
  - cada código de `NotEnough` con su caso.
- **`ObservationsTest`:**
  - una prueba por regla de la tabla: dispara cuando debe y calla sin sus datos;
  - máximo 3;
  - ninguna palabra prohibida en ningún texto (recorre todas las reglas con datos extremos);
  - nunca m/s ni 1RM sin calibración.

**Comandos** (carril de dominio: SOLO el arnés, por su carril ligero; ver VT_COMUN):
- `KPKN_SLOT_LANE=jvm bash /c/kw/tools/gradle_slot.sh /c/kw/vt-jvm-b test --offline --tests "com.example.kpkn.domain.biomechanics.vbt.*" --tests "com.example.kpkn.domain.biomechanics.metrics.*" --tests "com.example.kpkn.domain.biomechanics.reps.*" --console=plain > /c/kw/logs/vt-3b-jvm-<n>.log 2>&1`
- Las corridas `:app:…` las hace el orquestador, en lote, en vt-a.

## Commits
- `feat(technique): escala por disco, velocidad en m/s y perfil carga-velocidad`
- `feat(technique): observaciones de técnica con sus textos`
