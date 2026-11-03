# Paquete TÉCNICA (tc) · punto 2 del usuario

Archivos del prototipo: `src/scenes-tc.js`, `src/tc.css` (pestaña «Técnica por vídeo», 33 escenas). Compilar: `node build.cjs --out=shots/tc`.
El vídeo es sintético (SVG): un peso muerto de lado, con disco de 45 cm visto de frente; barra, pose, trayectoria y repeticiones salen del mismo modelo.
Muestra = el caso de la captura del usuario: vista oblicua, seguimiento del disco interrumpido, confianza media (≈ 5 + 1 parcial).

## 1. Piezas reales de la app que corresponden
Carpeta `android-native/app/src/main/java/com/example/kpkn/screens/technique/`:
- `analysis/TechniqueAnalysisScreen.kt` (pantalla, `AnalysisPanel` = las 3 pestañas que se eliminan, `ReviewFooter`, `AnalysisNotice`, `AnalysisSurfaceTop`, menú ⋯, ayuda «Cómo leer este análisis»).
- `analysis/TechniqueAnalysisUiState.kt` (`AnalysisStatusUi`, `SummaryTileUi`, `RepBreakdownUi`, `CameraTip`, `buildResults`, `noRepsMessage`, `failureMessage`, `profileOptions`).
- `analysis/design/`: `AnalysisStatusModule` (analizando/mensajes/¿qué movimiento?), `AnalysisSummaryModule`, `RepBreakdownModule`, `ObservationsModule`, `PlateCalibrationModule`, `CameraTipsModule`.
- `analysis/PlateCalibrationDialog.kt` (marcar el disco, «Otro» diámetro). `player/` (`TechniqueVideoPlayer`, `TechniqueOverlay`, `TechniquePlayerControls`, `TechniqueScrubber`, `TechniqueRepChips`).
- `importer/VideoImportSheet.kt` y `hub/TechniqueHubScreen.kt` (entrada desde el Perfil). Sesión en vivo: `MainActivity.kt` (`KpknRoute.TechniqueAnalysis`, `deliverToDraft`), `screens/workout/components/SetCardExerciseMediaBack.kt` (Modo visual, grabador), `screens/workout/WorkoutVideoAnalysisController.kt` (línea del análisis).

## 2. Inventario escena → pieza
| Escena | Pieza actual |
|---|---|
| tc-perfil-importar / -vacio / -guardando | `VideoImportSheet` / `VideoImportContent` (miniatura del vídeo es nueva) |
| tc-grabar | grabador del Modo visual + `CameraTipsModule` (movido ANTES de grabar) |
| tc-analizando · tc-exito · tc-cola · tc-actualizando | `AnalyzingBody` (`Analyzing(progressPct, updating, queued)`) |
| tc-resultado-vivo / -perfil / -ideal | `TechniqueAnalysisScreen` (reproductor + pestañas + `ReviewFooter`) |
| tc-repeticion | `RepBreakdownModule` + corrección completa/parcial (`buildReview`) |
| tc-detalle | `AnalysisSummaryModule`, `ObservationsModule`, `PlateCalibrationModule`, `CameraTipsModule`, capas (chips de `TrajectoryPanel`) |
| tc-solo-trayectoria · tc-limpio | `TechniqueOverlayOptions(skeleton, trail)` (capas) |
| tc-horizontal | `videoAspect` > 1 (`PlayerSection`) |
| tc-calidad · tc-aviso | avisos de `buildPlate` / `plateWarnings`, `buildTips`, `AnalysisNotice` |
| tc-marcar · tc-marcar-otro · tc-disco | `PlateCalibrationDialog`, `PlateOtherDiameterDialog`, `PlateCalibrationModule` (3 estados) |
| tc-menu · tc-ayuda | `MenuRow` (Reanalizar, Ver tendencia, Elegir el tipo de movimiento) y hoja `analysis-help` |
| tc-elegir-movimiento | `AnalysisStatusUi.NeedsProfile` / `ProfileBody` |
| tc-sin-analizar · tc-fallo · tc-sin-soporte · tc-sin-informe | `NotAnalyzed`, `Failed`, `Unsupported`, `ReportMissing` (`MessageBody`) |
| tc-sin-video · tc-cargando | `AnalysisPhase.MEDIA_MISSING` / `LOADING` (`LoadingBody`) |
| tc-sin-repeticiones · tc-corto · tc-sin-persona | `noRepsMessage` (`no_pose`, `too_short`, `low_amplitude`, `poor_capture`, `no_scale`, otro) |

## 3. Propuestas y decisiones (con su razón)
1. **Una sola pantalla, sin pestañas**: vídeo a pantalla (9:16 o 16:9), capas sobre él y una hoja con alturas. Razón: el usuario pidió no hacer scroll y datos «desde el mismo vídeo». `move`: Trayectoria · Métricas · Repeticiones desaparecen como pestañas.
2. **Hoja en 3 alturas + «limpio»**: peek (conteo + 3 cifras + línea de tiempo + mandos), media («Por repetición»), máxima (detalle). Los mandos están fijos abajo (zona del pulgar) y la hoja crece hacia arriba; el vídeo se encoge para no taparse nunca.
3. **Lecturas de cristal que cambian por instante**: fase (subida/bajada/en el suelo), velocidad pegada a la barra, ROM y tiempos de la repetición en curso, rep n de 6. `new` (lectura instantánea; ver §4).
4. **Línea de tiempo con una banda por repetición**: alto = velocidad (azul → ámbar como la estela), rayada = parcial, punto ámbar = observación, tocar salta. Sustituye a las marcas finas del `TechniqueScrubber`.
5. **Mandos**: ‹ › (fotograma, existe hoy), ◁ ▷ (repetición anterior/siguiente, `new`; hoy los iconos de captura son de fotograma), play, bucle, velocidad 0,25× · 0,5× · 1× sobre el vídeo. `move` de la fila de velocidad.
6. **Modos «Con datos · Solo trayectoria · Limpio»**: son presets de las capas reales (Trayectoria de barra, Articulaciones, que siguen sueltas en el detalle). `new` solo «limpio» (también recoge la hoja).
7. **Calidad = una etiqueta + una tarjeta**: confianza (punto verde/ámbar/rojo) siempre visible; abre avisos con causa, **Cambiar** y «Para medir mejor» (≤ 3, textos de `CameraTip`). `move` de los avisos que hoy viven al final de «Métricas».
8. **«Para medir mejor» previo**: tarjeta sobre la cámara antes de grabar (consejos que aplican al ejercicio) y pista dentro de la tarjeta de calidad al terminar. `new` colocación; textos reales.
9. **Una pieza, dos entradas**: Perfil = «80 kg · 13 oct», sin «Usar», con «Ver tendencia»; sesión en vivo = «Serie 3 · 80 kg × 6 · 13 oct», conteo propuesto y «Usar en la serie 3» (hoy «Revisar y usar resultado»: la revisión ya está dentro de la hoja). No añadí «Guardar en el registro»: usar solo cambia el borrador, registrar sigue siendo otro paso.
10. **«Analizando» en primer plano**: visor con el fotograma que se procesa (hora, pose, estela medida), señal «altura de la barra» que se dibuja al ritmo del %, las repeticiones se cuentan una a una al 95–98 %, porcentaje grande, 4 fases, **Cancelar análisis** y «Puedes salir: sigue en segundo plano». Éxito: el visor se abre al resultado y las lecturas entran escalonadas (versión estática con «reducir movimiento»).
11. **Marcado manual**: mismo diálogo; `new`: tocar el fotograma coloca el círculo y lupa al arrastrar.
12. ⓘ suelto → fila «Cómo leer este análisis» del menú ⋯ (`move`). Eyebrow «ANÁLISIS» fuera de la isla (`out`, ya no hace falta).
13. Estados con vídeo quieto + tarjeta de cristal (sin analizar, fallo, sin soporte, sin informe) y esqueleto de carga: mismos textos reales.

## 4. Cambios de motor o datos que exige
- **Fases con estado real**: hoy solo hay `progressPct` (0–95 decodificación, 100 final). Las 4 fases se derivan de umbrales (≥ 95, ≥ 98); pose y barra son una sola pasada. Para precisión, `Analyzing` podría llevar la fase.
- **Cancelar análisis**: cancelar el trabajo de `TechniqueAnalysisWorker` y dejar el vídeo «sin analizar» (no existe hoy).
- **Fotograma en curso durante el análisis**: el worker ya decodifica; hace falta exponer el último `ptsUs` (y, opcional, trayectoria parcial) al estado. Si no, el visor muestra el primer fotograma y la señal se sustituye por la barra.
- **Velocidad instantánea**: `TechniqueOverlayData.trail.speed` ya es relativa (0..1); para m/s hay que exponer el factor de escala del disco. Sin disco calibrado se muestra «% de la mejor».
- **Conteo en vivo sobre la señal**: la señal «altura de la barra» sale de la traza ya calculada (barra o pose); sin ella, solo el recorrido del fotograma.
- Bandas de velocidad por repetición y marcas de observación: datos que ya existen (`RepReport.mcv`, `Observation.atUs`). Nada de Room.
- No hay detección de «vídeo oscuro»: ver duda 3.

## 5. Dudas que debe decidir el usuario
1. **Scroll en el detalle completo**: el resumen de 7 cifras, observaciones, disco, consejos y capas caben mejor con scroll interno a altura máxima; lo clave se ve sin él. ¿Lo aceptas, o prefieres partirlo en dos alturas más?
2. **Cancelar análisis**: ¿cancelar descarta (vídeo «sin analizar») o solo pausa? Dibujé descartar.
3. **Vídeo oscuro**: el motor solo dice «No vimos a nadie en el vídeo». ¿Añadimos una detección de luz (texto nuevo) o lo dejamos así?
4. **Lectura instantánea de velocidad** sobre el vídeo: ¿la dejas siempre (con datos) o solo al pausar/tocar el vídeo?
5. **Reanalizar en la hoja** cuando no hay repeticiones: dibujé el botón visible (hoy solo en ⋯). ¿De acuerdo?
