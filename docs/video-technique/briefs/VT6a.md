# VT6a · Disco de la barra: detección y seguimiento sobre luma (dominio puro)

- **Worktree y rama:** `C:\kw\vt-b` (carril de dominio), rama `vt/vt6a`, desde `feat/video-technique` con VT3b integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`;
  - `VIDEO_TECHNIQUE_V1.md` §3.2, §3.3 y §8 (entero, salvo el punto 6, que ya hizo VT3b) y §10 punto 4;
  - el código de `bar/`.
- **Logs:** `C:\kw\logs\vt-6a-<n>.log`.

Visión clásica en Kotlin puro sobre `ByteArray` de luma. **Sin OpenCV ni librerías nuevas.** Rendimiento: un fotograma de 720×1280 se procesa en menos de 8 ms en JVM, porque solo se busca dentro de la ventana (ROI). Sin asignaciones por fotograma: se reutilizan los buffers del rastreador.

## Archivos permitidos (nuevos)
- `domain/biomechanics/vision/`:
  - `LumaFrame.kt`;
  - `Gradients.kt` (Gauss + Sobel en una ROI);
  - `CircleHough.kt` (votación por gradiente, radio, cobertura y subpíxel);
  - `CircleFit.kt` (Kåsa por mínimos cuadrados y la elipse alineada a ejes con el centro fijo);
  - `ConstantVelocityKalman.kt`;
  - `Ncc.kt`;
  - `PlateDetector.kt`;
  - `PlateTracker.kt` (con `PlateTrackerConfig`).
- `src/test/java/com/example/kpkn/domain/biomechanics/vision/`, con `SyntheticPlateFrames.kt`, el generador de §10.4.

## Qué haces
§8, puntos 1–5 y 7, al pie de la letra:
1. **Bordes por ROI.**
   - Reducción para que r/s quede entre 20 y 45 px.
   - Gauss σ = 1 y Sobel 3×3.
   - Umbral max(P85, 20).
2. **Hough de gradiente.**
   - Votos en p ± r·n, con las dos polaridades.
   - Centro: acumulador 3×3 y subpíxel cuadrático.
   - Radio: histograma ponderado.
   - Cobertura: 36 sectores.
   - Elección:
     - al ADQUIRIR, el pico más externo con cobertura ≥ 0,4;
     - al SEGUIR, el más cercano a r_prev.
   - Refino: Kåsa a resolución completa con inliers a ±3 px.
   - Calidad: q = cobertura × media|cos|.
3. **Elipse.** Con el centro fijo, minimizar Σ(u·dx² + w·dy² − 1)². Rellena `axisRatio = a/b` de cada muestra (a = semieje horizontal).
4. **`PlateDetector.propose`.** Busca en una ROI de ±4·r_prior alrededor de cada muñeca, con r ∈ [0,75, 1,7]·r_prior.
   - Puntúa cada candidato con acumulador × cobertura × exp(−d²/(2(1,5r)²)).
   - Devuelve los 3 mejores, sin duplicados (centros a < 0,5r).
5. **`PlateTracker.track`.**
   - **Kalman** de velocidad constante:
     - σa = 6 m/s²·k_p, con k_p = r̂_semilla/(D/2) px/m;
     - si no hay D, el diámetro por defecto (450 mm).
   - **ROI y radio:** ventana de 1,6·r̂ + 3√P_xx; radio en [0,9, 1,1]·r̂.
   - **Aceptación:** q ≥ 0,35 y Mahalanobis² ≤ 9,21.
   - **Pérdidas:**
     - 1–2 fallos → PREDICTED;
     - ≥ 2 → NCC, si `config.nccEnabled` (por defecto, true):
       - plantilla de 2,2·r̂ de la última muestra con q ≥ 0,6;
       - pico ≥ 0,6 y el 2.º < 0,9·pico;
       - σ de la medida ×2;
     - si no, POSE_PRIOR con la muñeca;
     - a los 0,25 s de fallos, readquisición cerca de la muñeca con r ∈ [0,85, 1,15]·r̂.
6. **Salida.** Un `BarSample` por fotograma con `source`, `quality` y `axisRatio`.

## Pruebas (JVM, `SyntheticPlateFrames`)
§10.4: fondo con degradado, ruido, barras de rack y un distractor; disco con aro, buje, agujero y letras; contraste y polaridad aleatorios; repeticiones de jerk mínimo a 30 y 60 fps; desenfoque, oclusores, ruido, parpadeo y elipses.
- **`PlateDetectorTest`:** el candidato n.º 1 cae a < 2 % de D del centro real en ≥ 95 % de 50 semillas, sin elegir nunca el distractor.
- **`PlateTrackerTest`:**
  - error del centro: mediana ≤ 1 % de D y P95 ≤ 2 % de D;
  - error del radio ≤ 1 %;
  - readquisición en ≤ 5 fotogramas tras una oclusión de 0,3 s;
  - a/b estimada a ±0,02 con elipses de 0,94 y 0,85.
- **`PlateTrackerVelocityTest`:** convierte la traza a m/s con el diámetro real y calcula la MCV como VT3b (llama a `CalibratedVelocity`) → error ≤ 0,02 m/s a 60 fps y ≤ 0,03 a 30 fps.
- **`PlateTrackerPerfTest`:** mide los ms por fotograma en JVM y los imprime en el informe. Sin aserción dura salvo < 30 ms.

**Comandos:**
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.biomechanics.vision.*"`

## Commits
- `feat(technique): detección del disco por Hough de gradiente y ajuste de círculo y elipse`
- `feat(technique): seguimiento del disco con Kalman, NCC y readquisición`
- `test(technique): fotogramas sintéticos del disco`
