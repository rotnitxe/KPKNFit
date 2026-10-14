# VT7b · Pantalla «Análisis de técnica» y calibración del disco

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt7b`, desde `feat/video-technique` con VT7a integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`, `S_COMUN.md` y `KPKN_UI_V2.md`;
  - `VIDEO_TECHNIQUE_V1.md` §1 (puntos 3–4, «Honestidad»), §3.5, §3.9, §7 («Observaciones») y §9 (puntos 4 y 8);
  - el reproductor de VT7a;
  - `VideoAnalysisRepository` y `TechniqueSidecars` (VT5).
- **Logs:** `C:\kw\logs\vt-7b-<n>.log`.
- **PNG:** `C:/kw/shots/vt/7b`.

## Heredado de VT3b (en el informe; los nombres REALES mandan)
- **m/s** solo si `velocityUnit == MPS` (disco calibrado y señal de conteo del disco). `PEAK_VELOCITY` se marca solo en m/s.
- **Pérdida de velocidad:** `VELOCITY_RELATIVE` puede estar con `velocityLossPct == null`. Pasa cuando el ruido no deja medir la pérdida, pero sí las barras relativas. En ese caso, «Pérdida de velocidad: sin dato fiable», nunca un 0 %.
- **Trayectoria:** en cm si `PATH_ABSOLUTE` está y `barDeviationUnit == "cm"`; si no, en % del recorrido (`"%ROM"`). El desvío respecto del medio pie puede faltar por PARALLAX_SUPPRESSED, aunque estén la deriva y la excursión de cada repetición.
- **Avisos de la escala:** PLATE_ELLIPSE, PLATE_REJECTED, PARALLAX_SUPPRESSED y PLATE_SCALE_MISMATCH (VT6a). Van al módulo del disco, en una línea con la causa.
- **Observaciones:** `report.observations`, como mucho 3, con el texto ya escrito y su repetición. Se pintan tal cual: no se reescriben.

La pantalla vive en `screens/technique/analysis/`. Su ruta (`TechniqueAnalysis(mediaId)`) la registra VT9a. Aquí se entregan la pantalla, su ViewModel y una entrada `TechniqueAnalysisRoute(mediaId, onBack, onOpenTrends: (exerciseKey) -> Unit)` lista para enchufar.

## Archivos permitidos (nuevos)
- **`screens/technique/analysis/`:**
  - `TechniqueAnalysisScreen.kt`;
  - `TechniqueAnalysisViewModel.kt`;
  - `TechniqueAnalysisUiState.kt`;
  - `PlateCalibrationDialog.kt`.
- **`screens/technique/analysis/design/`** (sin estado):
  - `AnalysisSummaryModule.kt`;
  - `RepBreakdownModule.kt`, con la gráfica en Canvas;
  - `ObservationsModule.kt`;
  - `PlateCalibrationModule.kt`;
  - `CameraTipsModule.kt`;
  - `AnalysisStatusModule.kt`: analizando, sin soporte, elige el movimiento y falló.
- **Pruebas:**
  - `src/test/.../render/TechniqueAnalysisRenderSheetTest.kt`;
  - añadir al final de `TechniqueSamples.kt`;
  - `src/test/.../screens/technique/analysis/TechniqueAnalysisViewModelTest.kt`.

## Diseño (lenguaje KPKN v2; Material prohibido según `S_COMUN`)
1. **Fila superior.**
   - `KpknTopRow(onBack)`.
   - A la derecha, `KpknIconButton(More)` abre un `KpknContextMenu` con:
     - «Reanalizar»;
     - «Ver tendencia»;
     - «Elegir el tipo de movimiento», solo si es un ejercicio propio o el perfil vino de una elección.
2. **Cabecera:**
   - eyebrow «ANÁLISIS DE TÉCNICA»;
   - el nombre del ejercicio en `KpknType.title`, 2 líneas como máximo;
   - en `caption`, «Serie 2 · 80 kg × 8 · 13 oct». Sin datos de la serie, solo la fecha.
3. **Reproductor** (`TechniqueVideoPlayer`), con esquinas de 20 dp.
   - Alto = min(ancho × proporción, 62 % del alto de pantalla), con banda negra si hace falta.
   - Debajo: `TechniqueScrubber`, `TechniquePlayerControls` y `TechniqueRepChips`.
4. **Estado (`AnalysisStatusModule`)** según la fila de Room:
   - QUEUED/RUNNING: «Analizando el vídeo…» con `KpknProgressBar` y el % (de 0 a 100, nunca hacia atrás);
   - UNSUPPORTED: el texto del motivo;
   - NEEDS_PROFILE: «¿Qué movimiento es?» con una lista de `KpknRadioRow` (los perfiles en español: «Sentadilla», «Peso muerto», «Press de banca»…) y «Analizar» → `setUserProfileOverride`;
   - FAILED: «No pudimos analizar este vídeo» con «Reintentar».
5. **Resumen** (`KpknModule` + `KpknModuleHeader("Resumen")`), en una rejilla de 2 columnas de `KpknMetric`:
   - **Repeticiones** «8»: debajo, en caption, «+1 parcial» y la confianza («Confianza alta»; con MEDIA, «≈ 8» y «Revisa el conteo»);
   - **TUT** «31 s»;
   - **Tempo** «3-1-1-0»;
   - **ROM** «112°», con «± 3°» en caption;
   - **Pérdida de velocidad** «18 %»;
   - **Velocidad media**: solo con m/s y siempre con «≈», «≈ 0,52 m/s».
   - Debajo, si hay profundidad, en una línea: «Bajo el paralelo en 7 de 8».
   - Sin dato, la cifra no se pinta.
6. **Por repetición:**
   - `KpknSegmentedControl` compacto «Velocidad · ROM · Tiempo»;
   - gráfica de barras en Canvas, una barra por repetición:
     - altura relativa a la mejor;
     - la elegida, en tinta, y las demás al 40 %;
     - parciales con trama y fallidas en `WizardColors.danger`;
     - eje sin cifras: la cifra de la elegida va arriba;
     - tocar una barra = elegir la repetición, y el vídeo salta a ella;
     - descripción para TalkBack con el resumen («Velocidad: la más rápida, la 1; la más lenta, la 8, un 18 % menos»).
   - Debajo, el detalle de la elegida en `caption`: «Rep 4 · bajada 2,1 s · subida 0,9 s · ROM 104° · bajo el paralelo».
7. **Observaciones:** hasta 3 filas.
   - Cada fila: el texto en `body` y, a la derecha, `KpknTextAction("Ver")`, que salta a `atUs`.
   - Sin observaciones, el módulo no se pinta.
8. **Velocidad real con el disco** (`PlateCalibrationModule`), solo si el perfil usa disco. Tres estados:
   - semilla automática sin confirmar: miniatura del fotograma con el círculo, «¿Es este el disco?», chips «45 · 40 · 35 · Otro» (cm), `KpknPrimaryPill("Confirmar")` y `KpknTextAction("Ajustar")`;
   - confirmado: «Disco de 45 cm · velocidad en m/s» con «Cambiar»;
   - sin disco: «No encontramos el disco» con «Marcarlo».
9. **`PlateCalibrationDialog`** (pantalla completa sobre negro):
   - el fotograma del fondo de la rep 1 (retriever);
   - un círculo que se arrastra y se redimensiona con `detectTransformGestures`;
   - lupa con `Modifier.magnifier` en API 28+;
   - chips de diámetro;
   - «Guardar» → `VideoAnalysisRepository.setPlateSeed(mediaId, seed, diameterMm)`, que encola PLATE_ONLY.
10. **Para medir mejor** (`CameraTipsModule`), solo con confianza no ALTA o con flags. Consejos según la flag:

    | Flag | Consejo |
    |---|---|
    | OBLIQUE_VIEW | «Pon el móvil de lado, perpendicular al movimiento» |
    | BACK_VIEW | «Graba de frente o de lado: de espaldas no vemos la cara» |
    | MULTI_PERSON | «Que solo salgas tú en el encuadre» |
    | LOW_FPS | «Graba a 30 fps o más» |
    | FAR_SIDE_WORKING | «Graba el lado que trabaja» |
    | PLATE_ELLIPSE | «Coloca el móvil a la altura de la barra y de frente al disco» |
    | SIDE_UNKNOWN | «En ejercicios alternos, graba de frente» |

    Como mucho, 3 consejos.
11. **Pie:** `KpknSecondaryPill("Ver tendencia")` → `onOpenTrends(exerciseKey)`.
12. **Movimiento:** `kpknEntrance` en los módulos y cifras que cuentan; con movimiento reducido, valores finales.

## ViewModel
- **`TechniqueAnalysisViewModel(app, mediaId)`:** `uiState: StateFlow<TechniqueAnalysisUiState>`.
- **Combina** el medio (`WorkoutMediaRepository`), la fila (`VideoAnalysisRepository.observeByMedia`) y, en IO, los sidecars: `analysis.json` → informe; pose2 y bar → `TechniqueOverlayData`.
- **Si la fila DONE tiene una `engineVersion` menor que la actual:** pide RECOMPUTE una vez y muestra «Actualizando el análisis…».
- **Acciones:** `selectRep`, `reanalyze`, `chooseProfile`, `confirmPlate(diameterMm)`, `savePlateSeed(seed, diameterMm)`.

## Pruebas
- **`TechniqueAnalysisViewModelTest`** (Robolectric, repositorios con BD en memoria):
  - estados Analizando → Listo al cambiar la fila;
  - RECOMPUTE perezoso con una versión vieja;
  - `confirmPlate` encola PLATE_ONLY.
- **Hoja de render**, a 360 dp, 100 % y 130 %:
  - la pantalla Lista: arriba (reproductor con fotograma de ejemplo, resumen) y desplazada (por repetición, observaciones, disco y consejos);
  - Analizando al 42 %;
  - NEEDS_PROFILE;
  - UNSUPPORTED;
  - FAILED;
  - los 3 estados del módulo del disco;
  - confianza MEDIA (≈).

  Critica con el listón (≤ 3 rondas).
- **Corridas:**
  1. `:app:compileBaseDebugKotlin`
  2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.technique.*"`
  3. la hoja de render.

## Commits
- `feat(technique): pantalla de análisis de técnica (resumen, por repetición, observaciones y consejos)`
- `feat(technique): calibración del disco en la pantalla de análisis`
- `test(technique): hoja de render y ViewModel del análisis`
