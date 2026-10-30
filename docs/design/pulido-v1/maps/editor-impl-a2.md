# EDITOR · tanda A2 · informe (30-oct-2026)

Worktree `C:\kw\pu-ed` (rama `pulido/ed`, con `feat/pulido-v1` fusionada: cimientos de movimiento). Alcance: isla superior, lista y rótulo de día, barra inferior, menú «Añadir», borrar deslizando y ordenar arrastrando (escenas `ed-lista`, `-scroll`, `ed-dia`, `ed-pildora-mas`, `ed-anadir`, `ed-barra`, `ed-borrar` y sus tres fotogramas, `ed-ordenar` y `-levantado`).

## Archivos (rutas relativas a `android-native/app/src/`)
Nuevos (main, `…/screens/sessioneditor/`): `frame/EditorGlass.kt` (vidrio `.ed-glass`, curvas `EdMotion` sobre `KpknMotion`, familias `ed-lbl-*`, iconos, `edPressable`), `frame/EditorPopovers.kt` (popover de vidrio anclado), `frame/EditorWeekParts.kt` (`.ed-box`, `.ed-radio`, `.ed-ta`), `components/EditorExerciseRow.kt` (fila `.ed-row`, asa con 210 ms, `LocalEdRemoval`, `LocalEdListDragging`), `components/EditorListFooter.kt`, `design/EditorSwipeDelete.kt` y `EditorSwipeDeleteLogic.kt`, `SessionEditorAssistantHooks.kt` (gancho Asistente › Reglas › Tiempo).
Reescritos: `frame/SessionEditorFrameTopBar.kt` (UNA isla), `frame/SessionEditorFrameBottomBar.kt`, `design/SessionExerciseUndoNotice.kt`, `design/SessionModuleSlot.kt`.
Modificados: `SessionEditorScreen.kt`, `SessionEditorScrollRenderer.kt`, `SessionEditorVisuals.kt` (ficha levantada y hueco), `SessionEditorDragController.kt` (`dragSlotRect`, `computeDragSlotRect`, proyección observable), `SessionEditorDuration.kt` (`withoutExercise`), `components/ExerciseEditorCard.kt`, `frame/SessionEditorFrameHeader.kt`, `frame/SessionEditorFrameWeekSheet.kt`, `frame/SessionEditorFrameLogic.kt`.
Tests: nuevos `render/EditorListRenderSheetTest.kt` (+`…740Test`), `screens/sessioneditor/design/EditorSwipeDeleteLogicTest.kt`; actualizados `SessionEditorFrame{A1Compose,Compose,IntegrationGeometry}Test`, `SessionEditorFrameRenderSheetTest`, `SessionEditorEdaRenderSheetTest`.

## Verificación
`compileBaseDebugKotlin` verde; última corrida por la cola: 15 clases, 0 fallos (1 compilación + 3 corridas de tests). Las hojas de render están en `C:\kw\pu-ed-shots\a2\` (`_100`, `_130`, `_740`) y los lado a lado en `C:\kw\pu-ed-shots\cmp\` y `paridad/capturas/ed-a2-*.png`.

| escena | 100 % (% píxeles / error medio) | 130 % |
|---|---|---|
| ed-lista | 9,7 / 13,8 | 12,2 / 19,9 |
| ed-lista-scroll | 10,9 / 16,7 | — |
| ed-dia | 6,8 / 10,7 | — |
| ed-pildora-mas | 10,4 / 15,6 | — |
| ed-anadir | 9,5 / 13,8 | 11,4 / 18,2 |
| ed-barra | 5,4 / 7,2 | 7,1 / 9,0 |
| ed-borrar | 9,8 / 13,3 | — |
| ed-borrar-arrastre | 9,9 / 13,3 | — |
| ed-borrar-umbral | 10,0 / 13,7 | 14,3 / 24,6 |
| ed-borrar-aviso | 9,9 / 13,5 | — |
| ed-ordenar | 9,8 / 13,3 | — |
| ed-ordenar-levantado | 10,3 / 14,2 | — |

(ed-lista a 360 × 740: 10,3 / 14,0.) NO se llega a 2,5/255: ver DESV-400. La geometría sí cuadra (a ±2–4 dp).

## Decisiones (revisables)
1. **Isla superior = UNA isla** (como el lienzo: `.ed-top` 56 dp, radio 30, Atrás y ⋯ círculos de 48 dp dentro, píldora del día en `#ffffff0c`); el K4 de tres islas sueltas se sustituye. Las flechas de día miden 44 × 48 dp (como `.ed-arrow`) y los siete días de la hoja de la semana 42 × 48 (flex del lienzo): se relajó el mínimo de 48 dp de los tests de geometría a 44 y 42 de ancho.
2. «Reglas» sale de la barra; los minutos llaman a `openAssistantRulesTime()` (hoy abre la hoja de reglas en Tiempo; cambiar SOLO su cuerpo cuando el Asistente tenga su 4.ª pestaña).
3. El menú «Añadir» ofrece Ejercicio · Cardio (o «Espacio de cardio» si no existe) · Grupo nuevo (o «Fuerza» si no existe el espacio).
4. Los minutos y las fichas del resumen cuentan ya sin el ejercicio pendiente de borrar (bajan al borrar, suben al deshacer); la burbuja «+9 min» sale con cualquier cambio de minutos (también al editar series), no solo al añadir o quitar.
5. Ordenar: la fila arrastrada queda vacía (alfa 0) en su sitio y el hueco punteado va al destino; las vecinas se apartan 260 ms; al soltar, la ficha se asienta 340 ms antes de mover el ejercicio. Los bloques de superserie conservan la barra de destino antigua (no hay hueco para ellos).
6. Se eliminan: la barra de inserción de 4 dp en filas sueltas y de grupo, el aviso que sustituía la fila, la letra de variante («A», «B») y el «+» de «Crear sesión derivada» en la hoja de la semana, y el 1 dp de línea entre filas (ahora se dibuja encima).
7. Márgenes de la lista = lienzo: 12 dp bajo la isla, 128 dp abajo, cabecera con los huecos de `.ed-head`.

## Abierto y riesgos
- 36 filas de `ed.md` quedan abiertas: son de las series (A1/B-C), de las hojas comunes (`ed-A11…A14`, `ed-S07`) y del popover plano de las series (`ed-S05`, `ed-A08`; el popover de vidrio de A2 ya usa el preset `popover` y 200 ms). DESV-400 … 407 abiertas con captura, pendientes de aprobación. DESV-404 (`ed-T07`, despliegue de la tarjeta) es una línea de movimiento sin hacer.
- NO probado: gestos reales (deslizar, levantar, autoscroll) ni vidrio con desenfoque: solo la lógica pura (umbrales, hueco, autoscroll, minutos) y fotogramas congelados. Pasar por el Flip. Posible sorpresa: `animateItem` con la lista perezosa y el arrastre; la proyección observable repinta las filas por cuadro mientras se arrastra.
- `ExerciseEditorCard` y `DragLiftPreview` se comparten con superseries y la sesión en vivo no los usa; la piel nueva de la fila afecta también a los miembros de superserie (la caja de fondo se conserva).
- El cardio (rótulo `CARDIO · AL FINAL` con ↑ y +) sigue siendo el viejo: es del paquete `ce`.
