# Reparto · interfaz, tanda 1 (rama `pulido/rpu`, worktree `C:\kw\pu-rpu`)

Escenas hechas: `rp-catalogo`, `rp-semana`, `rp-mas-dias`, `rp-menos-dias`, `rp-en-curso`, `rp-vacia`, `rp-calculando`, `rp-revision`, `rp-revision-musculo`, `rp-decisiones`, `rp-aplicado`, `rp-deshecho`, `rp-rechazo`, `rp-autor`.
Tres corridas de la cola (1.ª: error de sintaxis; 2.ª y 3.ª: compila y pasa). La última corrida: 31 pruebas, 0 fallos. Se mergeó `feat/pulido-v1` (movimiento) antes de la 2.ª.

## Archivos (`android-native/app/src/main/java/com/example/kpkn/`)
Nuevos en `screens/programdetail/design/split/`: `SplitFlowModels` (modelos y constructores de pantalla), `SplitFlowController` (estado + planificador fuera del hilo principal), `SplitFlowAssembler`,
`SplitFlowTheme`, `SplitFlowControls`, `SplitFlowMotion`, `SplitFlowSheets` (decisiones, resumen, diálogo de autor), `SplitCatalogCard`, `SplitAgenda` (arrastre, «Mover a», detalle), `SplitPreviewScreen`, `SplitReviewScreen`.
Reescritos: `SplitCatalogScreen`, `components/SplitView.kt` (navegación catálogo → vista previa → revisión; lo aplicado sube a `ProgramDetailScreen`). Tocados: `ProgramDetailScreen.kt` (hoja de resumen con Deshacer/Rehacer, brillo 0,55, ids de sesiones hechas, número y fechas de la semana),
`ProgramWeekMenus.kt` (fila «Deshacer el cambio de reparto» si `undoState == AVAILABLE`). Borrados: `SplitCatalogModule.kt`, `SplitSearchField.kt`. Pruebas: `SplitFlowModelsTest` (15), `SplitFlowControllerTest` (9), `SplitCatalogModuleTest` (6, reescrita),
`render/RepartoRenderSheetTest` (+ `src/test/resources/reparto/lienzo.json`, volcado de `scenes-rp.js`), `SplitViewRenderSheetTest` (sin las páginas viejas).

## Números por escena (error medio /255 · % de píxeles > 28; `compare.cjs`, 360×880, render a 3× reducido a 1×)
catálogo 14,2 · 10,7 % | semana 9,0 · 7,8 % | más días 9,4 · 8,1 | menos días 9,9 · 8,5 | en curso 9,1 · 8,1 | vacía 9,4 · 8,3 | calculando 8,2 · 5,5 | revisión 20,4 · 13,0 | revisión por músculo 13,1 · 9,5 | decisiones 23,2 · 17,1 |
aplicado 10,1 · 9,3 | deshecho 7,5 · 7,9 | rechazo 11,3 · 10,8 | autor 7,2 · 6,5.
Páginas enteras (360×2400): catálogo 22,2 · 14,9 | semana 8,2 · 6,7 | revisión 20,4 · 14,0 | en curso 9,9 · 7,6. A 360×740 con letra al 130 %: 22–27/255 en las siete densas (el texto reflota distinto: el lienzo y Compose no parten igual las líneas).
**No se llega al objetivo de 2,5/255.** Causas medidas: el lienzo trae marco de teléfono, esquinas y franja de estado de 38 px que la app no pinta; el texto de Chrome y el de Compose no coinciden píxel a píxel; quedan desfases de 2–4 dp acumulados en tarjetas con
varias líneas (catálogo, revisión) cuyo origen no se llegó a medir; el vidrio con desenfoque no se dibuja en Robolectric (al Flip). La geometría (anchos, bandas, tiras, chips, tablas) coincide a ojo en las capturas lado a lado de `paridad/capturas/`.
Medido del lienzo y ya aplicado: el filete de 1 px ocupa sitio (border-box), el descanso mide 47 dp, el selector de repartos mide 192 dp (mínimo contenido de la tira), los márgenes de bloque contiguos se funden, las alturas de línea «normal» (Inter 1,21).

## Decisiones
1. «Cómo aplicar» (alcance y contenido) sigue siendo, hasta la tanda 2, la hoja `SplitApplySheet` de siempre: se abre desde el botón de la barra, desde «Cambiar» y desde la acción del rechazo, y **aplica por su cuenta** (cierra la página; sin resumen).
2. Semana vacía: «Aplicar reparto» va directo (sin revisión) por `SplitApplicationEngine` con ejercicios de plantilla; arrastrar cambia los días; «Otra plantilla» se ve pero aún no cambia nada (tanda 2).
3. «Deshacer» tras aplicar: por planificador usa `SplitMigrationPlanner.undo`; por el camino de siempre, restaura el programa de antes. En el ⋯ de la semana: fila nueva «Deshacer el cambio de reparto · Devuelve la semana a como estaba» (texto mío: el lienzo no lo trae).
4. `targetMinutes` queda en null en la app: no hay un «tiempo por sesión» accesible desde Program Detail; el parámetro está cableado en `SplitView` y la decisión de tiempo se verá cuando alguien lo pase.
5. «Mover a» mide 34 dp como el lienzo (menos de 48): fidelidad sobre accesibilidad; la asa mide 44 × 48.
6. Quitado por no estar en el lienzo: radio de las tarjetas, barras de 6 px, motivo «sin plantilla» de la tarjeta, entrada escalonada de las tarjetas.
7. Textos propios (no están en el lienzo): subtítulo de «Antes de aplicar» cuando solo hay una de las dos decisiones; «Aplicar con sesiones vacías» solo si el motor lo permite.

## Lo abierto
- Filas rp-S11 y rp-A05 (escenas de la tanda 2). rp-S03 `[~]` DESV-1000 (`KpknSheet` no pinta la sombra de hoja; es un componente compartido).
- No probado en teléfono: arrastre real (el gesto mide en coordenadas de ventana), vidrio, hoja de resumen sobre la semana, `LineBreak.Heading` del título, animaciones (cableadas y con versión estática; sin fotogramas propios: `rp-in` y `rp-pop` usan valores exactos propios, la familia canónica sube 14 dp y parte de 0,86).
- Tras la última corrida se cambió una línea sin recompilar: se quitó `LineBreak.Heading` de `RpType.cardTitle` (el lienzo no equilibra los títulos de las tarjetas).
- Pendientes de pulido visual: grip de 6 puntos más grueso en el lienzo; negrita del veredicto de la revisión; desfase de 2–4 dp en tarjetas de varias líneas.
