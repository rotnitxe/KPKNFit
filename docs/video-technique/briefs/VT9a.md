# VT9a · Tendencias por ejercicio y comparar dos vídeos

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt9a`, desde `feat/video-technique` con VT8a integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`, `S_COMUN.md` y `KPKN_UI_V2.md`;
  - `VIDEO_TECHNIQUE_V1.md` §1 punto 5, §3.6 (carga-velocidad), §3.9 (claves) y §7 («Perfil carga-velocidad»);
  - `docs/android/SKILL_COMPOSE_CANVAS.md`;
  - el reproductor de VT7a;
  - `VideoAnalysisRepository` (VT5b).
- **Logs:** `C:\kw\logs\vt-9a-<n>.log`.
- **PNG:** `C:/kw/shots/vt/9a`.

## Heredado (ya integrado; los nombres REALES mandan sobre este brief)
- **`VideoAnalysisRepository`** (`data/repository`, VT5b), con `init(context)` y `getInstance()`:
  - `observeTrend(exerciseKey): Flow<List<VideoAnalysisSummary>>`, las series de un ejercicio;
  - `observeConfigurationPoints(configurationKey): Flow<List<VideoAnalysisLoadPoint>>`, con `mediaId`, `loadKg`, `mcvBest`, `velocityUnit` (texto) y `recordedAtMs`. Son los puntos de carga y velocidad de una configuración;
  - `observeExerciseSummaries()` y `getByMedia(mediaId)`.
- **`VideoAnalysisSummary`** (`data/models`): `status`, `confidence`, `repCount`, `partialReps`, `failedReps`, `tutMs`, `romMeanDeg`, `romCvPct`, `velocityUnit`, `velocityLossPct`, `mcvBest`, `mcvLast`, `loadKg`, `recordedAtMs`, `exerciseName`, `configurationKey`, `profileId`, `barDeviation`/`barDeviationUnit` y `flagsJson`.
  - Un campo `null` es SIN DATO: no se pinta, nunca como 0.
- **Carga-velocidad** (`domain/biomechanics/vbt/LoadVelocityProfile`):
  - Llamada: `estimate(points: List<LoadVelocityPoint(loadKg, mcvMps, recordedAtMs)>, mvtMps, heaviestMaxMps = LoadVelocityProfile.heaviestMaxMps(profileId))`.
  - Devuelve `LoadVelocityResult.Estimate(e1rmKg, slope, intercept, r2, points)` o `NotEnough(reason)`.
  - **Solo con puntos en m/s** (`velocityUnit` de MPS). Sin ninguno, el módulo dice «Calibra el disco en tus vídeos para ver la velocidad real».
  - **La MVT** sale del perfil RESUELTO del ejercicio, `AnalysisProfileResolver.resolve(sidecar.exercise)`, como en `TechniqueAnalysisViewModel` (VT7b): el resolver la ajusta al material, así que no vale la del perfil base. Si `mvtMps` es `null`, no se estima 1RM: el módulo enseña solo la dispersión, sin recta ni 1RM.
  - **Textos de `NotEnough`** (escríbelos así, en tipo frase):

    | Código | Texto |
    |---|---|
    | `few_loads` | «Graba series con al menos 3 cargas distintas» |
    | `narrow_range` | «Las cargas están muy juntas: amplía el rango» |
    | `low_velocity_spread` | «La velocidad apenas cambia entre cargas» |
    | `heaviest_too_fast` | «Falta una serie pesada (más lenta)» |
    | `positive_slope` | «Los datos no siguen la tendencia esperada» |
    | `low_r2` | «Los datos están muy dispersos» |
    | `implausible` | «La estimación no es creíble con estos datos» |
    | `wide_interval` | «Hace falta más precisión: graba más series» |

    Con un código desconocido: «Faltan datos».
- **Pérdida de velocidad (D29):** `velocityLossPct` puede ser `null` aunque haya velocidades: el ruido no dejó medirla. Esas series no van en la gráfica de pérdida. Si todas lo son, el módulo no se pinta.
- **Reproductor (VT7a):**
  - `TechniqueOverlayData.from(track, bar, report, videoWidth, videoHeight)`, con los sidecars pose2, bar y analysis.json, leídos como en `TechniqueAnalysisViewModel` (VT7b);
  - `rememberTechniquePlayerState(overlay)`, `TechniqueVideoPlayer(file, overlay, state, modifier, onTap, …)` y `state.seekToUs`.
- **Análisis (VT7b):** `TechniqueAnalysisRoute(mediaId, onBack, onOpenTrends)`. Su ruta la registra VT9b; tú solo registras las tuyas.
- **Honestidad (VT_COMUN, «Pantallas»):** «≈» en toda estimación; m/s solo con `velocityUnit` MPS; el 1RM con «orientativo · beta».

## Archivos permitidos
- **Nuevos:**
  - `screens/technique/trends/`: `TechniqueTrendsScreen.kt`, `TechniqueTrendsViewModel.kt`, `TechniqueTrendsUiState.kt`;
  - `screens/technique/trends/design/`: `LoadVelocityChart.kt`, `VelocityLossChart.kt`, `RomTrendChart.kt`, `TutChart.kt`, `AnalyzedVideoRow.kt`;
  - `screens/technique/compare/`: `TechniqueCompareScreen.kt`, `TechniqueCompareViewModel.kt` y `design/OverlaidPathsChart.kt`;
  - pruebas y hoja de render.
- **`navigation/Navigation.kt`:** añadir `TechniqueTrends("technique/trends/{exerciseKey}")` y `TechniqueCompare("technique/compare/{mediaA}/{mediaB}")`, con `create(...)` y `Uri.encode`.
- **`MainActivity.kt`:** registrar las dos rutas en el NavHost, junto a las de álbumes (~L1410-1444). Solo bloques nuevos.
- **`screens/technique/analysis/TechniqueAnalysisScreen.kt`:** si hace falta, conectar `onOpenTrends`. La ruta del análisis la registra VT9b.

## Diseño (KPKN v2, Canvas propio, cada gráfica con su descripción para TalkBack)
1. **Tendencias** (`TechniqueTrends(exerciseKey)`):
   - `KpknTopRow(onBack)` y el título con el nombre del ejercicio.
   - **Módulo «Velocidad por carga».**
     - Dispersión de puntos (carga, MCV de la mejor completa de cada serie) de la misma `configurationKey` en las últimas 8 semanas.
     - Recta de regresión y línea horizontal de la MVT del perfil.
     - Con estimación: «1RM ≈ 142,5 kg» en `metricL` y el rótulo «orientativo · beta».
     - Sin estimación: «Faltan datos» y el motivo en texto llano. Los códigos de `NotEnough` se traducen; por ejemplo, `few_loads` → «Graba series con al menos 3 cargas distintas».
     - Solo con m/s. Si no hay disco calibrado: «Calibra el disco en tus vídeos para ver la velocidad real».
   - **Módulo «Pérdida de velocidad»:** barras por serie (fecha en el eje), con la media.
   - **Módulo «ROM y profundidad»:** línea de ROM medio (°) por sesión, con una banda de ±DE, y en `caption` las veces bajo el paralelo (sentadilla).
   - **Módulo «TUT por serie»:** barras.
   - **«Vídeos»:** filas `AnalyzedVideoRow`, con miniatura, fecha, «80 kg × 8», reps detectadas y la confianza.
     - Tocar una fila abre el análisis.
     - «Comparar» activa la selección de 2 → `TechniqueCompare`.
   - Sin datos de una gráfica, el módulo no se pinta. Sin ningún análisis, `KpknEmptyState`.
2. **Comparar** (`TechniqueCompare(a, b)`):
   - **Reproductores:** dos `TechniqueVideoPlayer` apilados, A arriba y B abajo, cada uno con su capa.
     - Pasa `onTap = null` a los dos: un toque en uno no debe parar solo ese, porque el Play es compartido.
     - Cada uno con su `rememberTechniquePlayerState(overlay)`; el deslizador compartido llama a `seekToUs` en los dos (VT7a).
   - **Deslizador compartido**, alineado al inicio de la rep 1 de cada vídeo:
     - mover el deslizador busca en los dos;
     - Play reproduce los dos, «lo mejor posible»: se resincroniza B en cada límite de repetición.
   - **Trayectorias superpuestas** (`OverlaidPathsChart`):
     - las de la barra de A y B, cada una desde su punto de inicio;
     - en cm si los dos están calibrados; si no, en % del ROM, con «≈»;
     - A en tinta y B en `SymbolPalette.columna`, con leyenda.
   - **Tabla de diferencias** (`KpknModule` con filas): reps, TUT, ROM, pérdida de velocidad y MCV, en A, B y Δ.

## Pruebas
- **ViewModels** (Robolectric con BD en memoria): puntos de carga-velocidad desde las filas y la llamada a `LoadVelocityProfile.estimate`.
- **Hoja de render** con `TechniqueSamples`:
  - tendencias con datos y con «faltan datos»;
  - comparar con dos trazas;
  - cada gráfica suelta;
  - 360 dp, 100 % y 130 %;
  - ≤ 3 rondas.
- **Corridas:**
  1. `:app:compileBaseDebugKotlin`
  2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.technique.*"`
  3. la hoja.

## Commits
- `feat(technique): tendencias por ejercicio (carga-velocidad, pérdida de velocidad, ROM y TUT)`
- `feat(technique): comparar dos vídeos con trayectorias superpuestas`
