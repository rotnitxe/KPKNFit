# EDITOR · pasada B/C de las series y las hojas comunes · informe (30-oct-2026)

Worktree `C:\kw\pu-ed` (rama `pulido/ed`, con `feat/pulido-v1` fusionada hasta `ef45c55cf`; merges ya commiteados, el trabajo B/C sigue SIN commit). `paridad/ed.md`: 117 filas con evidencia (101 `[x]`, 16 `[~]`); `paridad-check ed` en verde.

## Filas cerradas ahora
`[x]`: S05, S06, S07, G01, A08, A11, A12, A13, A14, A15, T01, T07 (antes DESV-404), T08, T09, T19, T20, T23, T26, T27, T28, T30–T34, X07, X09, J01, J03, J06, J07, J08, J09.
`[~]`: A06 (DESV-452), A10 (DESV-453), T24 (DESV-450), J05 (DESV-454). Transversales: DESV-451 (objetivos táctiles) y DESV-455 (error medio de las escenas).

## Archivos (rutas desde `android-native/app/src/`)
Nuevos (main, `…/screens/sessioneditor/components/`): `EditorSeriesMotion.kt` (`EdAnimations`: `ed-in` 6 dp/260 ms, `ed-flash`, `ed-popin` ±6 dp, `ed-bump`; `edTransitionColor`, `edChevronRotation`, `edEnter`, `edEnterOnChange`, `edBump`, `rememberEdPressed`), `EditorSheetHost.kt` (`EdSheetSpec`, `EdSheet`, `LocalEdSheetClose`).
Modificados: `EditorSeriesPrimitives.kt` (transiciones de color, rebote, sombra/degradado del deslizador, menú anclado con sombra `popover` y entrada), `EditorSeriesSections.kt` (chevrones, parpadeo de la carga inteligente, atajos con `ed-in`), `ExerciseSetList.kt` (foco con `ed-in`, destello tras aplicar), `SessionEditorSeriesChip.kt`, `InlineSetRowTechniqueChips.kt`, `PreparationCarouselCards.kt`, `ExerciseEditorCard.kt` (despliegue 300 ms), `ExerciseOptionsBar.kt`, `SetFocusControls.kt`, `SessionEditorControls.kt` (cierres con `LocalEdSheetClose`), `frame/SessionEditorFrameWeekSheet.kt` (hoja de la semana con `EdSheetSpec`), `ui/components/KpknSheet.kt` (solo `scrimOutEasing` en `KpknSheetSpec`, con valor por defecto null).
Tests: nuevos `EditorSeriesAnimationsTest`, `EditorSeriesTouchTargetsTest`, `render/EditorSeriesFramesRenderTest`; ajustados `SessionEditorFrameA1ComposeTest` y `…IntegrationGeometryTest` (área táctil 48 × 48 con `touchBoundsInRoot`), `ExerciseSetListComposeTest`, `EdbFocusControlsTest` (esperan la bajada de la hoja).

## Hoja del editor: cómo quedó `KpknSheetSpec` (para DESV-653 del roadmap y DESV-1000 del reparto)
No hay API nueva: se usa `KpknSheet(…, surfaceColor, lienzo = KpknSheetSpec(...))`. Único parámetro añadido (con valor por defecto): `scrimOutEasing: Easing? = null` (curva del velo al salir; null = `scrimEasing`).
```kotlin
internal val EdSheetSpec = KpknSheetSpec(
    shadow = KpknShadow.Hoja.spec, scrimInMs = 220, scrimEasing = KpknMotion.Estandar,
    sheetInMs = 340, sheetEasing = KpknMotion.Estandar, contentDelayMs = 0, contentInMs = 1, contentEasing = KpknMotion.Lineal, contentRise = 0.dp,
    outMs = 240, outEasing = KpknMotion.Cae, outTravel = 1f, scrimOutMs = 200, heightMs = 1, heightEasing = KpknMotion.Lineal, scrimOutEasing = KpknMotion.Suave)
// uso, 3 líneas:
KpknSheet(onDismissRequest = onDismiss, surfaceColor = Color(0xFF1B1B1D), maxHeightFraction = 0.88f, lienzo = EdSheetSpec) { /* contenido */ }
```
Otro paquete solo cambia los números de su `KpknSheetSpec`. `EdSheet(onDismiss) { … }` envuelve eso con 20 dp laterales y 14 de separación y da `LocalEdSheetClose` (`close { lo que confirma }`: la hoja baja y luego corre el callback).

## Verificación
Corrida de la cola (14 clases): todo en verde salvo 3 fallos, ya corregidos y relanzados en una corrida de solo esas dos clases (resultado en el informe final). Hojas de render en `C:\kw\pu-ed-shots\bc1\` (`_100`, `_130`, `_100_740`, `_130_740`), fotogramas `ed-fotograma-{bump,flash,chevron,popin,hoja-sube,hoja-baja}_tNNNN.png`, tabla `ed-series-animaciones.tsv`; lado a lado en `C:\kw\pu-ed-shots\cmp-bc1\`.

Error medio por escena (100 %, `compare.cjs`, /255): ed-serie 13,6 · carga 14,9 · rango 13,9 · intensidad 14,2 · fallo 16,1 · descanso 22,7 · acciones 15,8 · aplicar 14,7 · modo 24,9 · amrap 15,0 · rm 13,7 · solorpe 16,1 · tiempo 13,4 · distancia 13,2 · preparación 18,2 · unilateral 15,3 · opciones 11,5 · descanso-ejercicio 12,6. Ninguna llega a 2,5 (DESV-455, igual que DESV-400: texto, marco y vidrio).

Animaciones propias (puras, `EditorSeriesAnimationsTest`, 0/25/50/75/100 %): `ed-in` 260 ms, `ed-flash` 600 ms, `ed-bump` 240 ms, `ed-popin` 200 ms (±6 dp), velo 220/200 ms, hoja 340/240 ms, movimiento reducido = estado final. En pantalla (`EditorSeriesFramesRenderTest`, reloj parado): rebote de la cifra, destello de la ficha, giro del chevron (sigue la curva `--ed-ease` con <8°), entrada del menú anclado y subida/bajada de la hoja.

## Abierto y riesgos
- Sin Flip: gestos reales, vidrio con desenfoque, sombras de gran radio y el tacto de los 48 dp.
- Las fichas de aproximación/movilidad no destellan (DESV-453); el foco no se anima al abrir la tarjeta (DESV-452).
- Los fotogramas de pantalla tienen ±1–3 cuadros (16 ms) de desfase de arranque.
- La hoja de la semana (A2) pasó a `KpknSheet(lienzo = EdSheetSpec)` conservando su disposición.
