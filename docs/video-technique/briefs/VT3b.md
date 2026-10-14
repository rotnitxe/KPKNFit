# VT3b · VBT con disco (escala, m/s, carga-velocidad) y observaciones (dominio puro)

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt3b`, desde `feat/video-technique` con VT3a integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3.5, §3.6, §7 («Velocidad», «Perfil carga-velocidad», «Observaciones») y §8 punto 6 (escala, elipse y paralaje);
  - el motor de VT3a.
- **Logs:** `C:\kw\logs\vt-3b-<n>.log`.

## Archivos permitidos
- **`domain/biomechanics/vbt/` (nuevos):** `PlateScale.kt`, `CalibratedVelocity.kt` y `LoadVelocityProfile.kt`.
- **`domain/biomechanics/metrics/` (nuevos):** `Observations.kt` (las reglas) y `ObservationText.kt` (los textos).
- **Para integrar:** `TechniqueAnalysisEngine.kt`, `PathMetrics.kt` y `AnalysisTuning.kt` (solo campos nuevos al final).
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

**Comandos:**
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.biomechanics.vbt.*" --tests "com.example.kpkn.domain.biomechanics.metrics.*"`

## Commits
- `feat(technique): escala por disco, velocidad en m/s y perfil carga-velocidad`
- `feat(technique): observaciones de técnica con sus textos`
