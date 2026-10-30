# Mapa · paquete EDITOR (`ed`, pestaña «Editor»)

Archivos: `src/scenes-ed.js` (un solo editor interactivo; cada escena es un estado suyo) y `src/ed.css` (clases `ed-*`).
Puntos del usuario: 3 (bis), 10, 12, 12 (bis). Todo lo de abajo está en `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/`.

## 1. Piezas reales de la app que corresponden a cada parte
| Parte del prototipo | Archivo / composable de hoy |
|---|---|
| Isla superior (atrás · ‹ día ▾ › · ⋯) | `frame/SessionEditorFrameTopBar.kt` · `SessionEditorFrameTopBar`, `FrameDayArrow`; menú ⋯ = `ui/design/KpknContextMenu` |
| Hoja del día / semana | `frame/SessionEditorFrameWeekSheet.kt` · `SessionEditorFrameWeekContent`, `FrameDayCircle`, `FrameVariantRows` |
| Cabecera y chips de resumen | `frame/SessionEditorFrameHeader.kt` · `SessionEditorFrameHeader`, `SummaryChips` |
| Barra inferior | `frame/SessionEditorFrameBottomBar.kt` · `SessionEditorFrameBottomBar`, `FrameTimeRing`, `FrameBarTextAction`; tono/fracción en `frame/SessionEditorFrameLogic.kt` (`frameTimeRingTone`, `frameTimeRingFraction`) |
| Fila de ejercicio, asa, estrella, flecha | `components/ExerciseEditorCard.kt` · `ExerciseEditorCard`, `Thumbnail` |
| Borrar deslizando | `ui/components/SwipeToDeleteCard` (ejercicio) y `design/SessionSwipeDeleteRow.kt` (grupo); aviso en `design/SessionExerciseUndoNotice.kt` (5 s, `SESSION_UNDO_WINDOW_MS`); estado en `SessionEditorViewModel.requestExerciseRemoval` / `undoExerciseRemoval`, `pendingExerciseRemoval` |
| Arrastrar para ordenar | asa en `ExerciseEditorCard` (`detectDragGesturesAfterLongPress`), `SessionEditorDragController.kt`, `SessionEditorDropPlan.kt`, `SessionEditorScrollRenderer.kt` |
| Tarjeta abierta | `components/ExerciseEditorExpanded.kt` · `ExerciseEditorExpanded`, `ExercisePreparationSection`, `RelationshipPanel` |
| Barra de opciones y sus hojas | `components/ExerciseOptionsBar.kt` · `ExerciseOptionsBar`, `ExerciseModeSheet`, `LateralitySheet`, `SideOptionsModule`, `SmartLoadModule`, `ExerciseRestChain` |
| Series (fichas + foco) | `components/ExerciseSetList.kt` · `ExerciseSetList`, `setRowSummary`; ficha `SessionEditorSeriesChip.kt` (85 × 76) |
| Campos de la serie | `components/InlineSetRow.kt` · `InlineSetRow` (`LoadTypeRow`, `ListFieldsGrid`, `IntensityRow`, `EstimateBox`), `SetValueCell`, `AmrapTag` |
| Modos Tiempo / Distancia / Personalizado / Solo RPE | `components/SetModeGoals.kt` · `DurationGoal`, `MetricGoal`, `SoloRpeGoal`; RM y AMRAP: `RmSetFocus.kt`, `AmrapSetFocus` |
| Descanso de la serie / del ejercicio | `components/SetFocusControls.kt` · `SetRestControl`; `SessionEditorControls.kt` · `RestSheet` / `RestBundleDialog` |
| Técnicas «más allá del fallo» | `components/InlineSetRowTechniqueChips.kt` (`DropSetPlanDefaults`, `RestPausePlanDefaults`, `MyoRepsPreset`) |
| Confirmar «Aplicar a todas» | `SetFocusControls.kt` · `EditorConfirmationSheet` |
| Aproximación y movilidad | `components/PreparationChips.kt` (`WarmupPreparationChips`, `MobilityPreparationChips`), `PreparationCarouselCards.kt` |

## 2. Inventario escena → pieza
| Escena | Estado que enseña |
|---|---|
| `ed-lista` · `ed-lista-scroll` | Editor en reposo y con scroll (vidrio real arriba y abajo) |
| `ed-dia` · `ed-pildora-mas` | Hoja de la semana · menú «⋯» |
| `ed-anadir` · `ed-barra` | «Añadir» desplegado y minutos que cambian · estados del tiempo (sin ejercicios, reposo, cerca, superado, sin límite) |
| `ed-borrar` (+ `-arrastre`, `-umbral`, `-aviso`) | Deslizar para borrar: en vivo y tres fotogramas |
| `ed-ordenar` (+ `-levantado`) | Arrastrar para ordenar: en vivo y fotograma |
| `ed-serie` | Serie abierta (carga, reps, intensidad, descanso, acciones) |
| `ed-serie-carga` · `-rango` · `-intensidad` · `-fallo` · `-descanso` · `-acciones` · `-aplicar` | Menú de tipo de carga · rango · unidad · Fallo con técnicas · descanso editándose · menú ⋯ de la serie · confirmación |
| `ed-modo` · `ed-serie-amrap/rm/solorpe/tiempo/distancia` | Hoja de los 7 modos y cómo queda la serie en cada uno |
| `ed-preparacion` · `ed-unilateral` | Aproximación + movilidad · lados L/R |
| `ed-opciones` · `ed-descanso-ejercicio` | Hoja «⋯» del ejercicio · hoja del descanso del ejercicio |

## 3. Propuestas y decisiones (con su razón)
- **Una isla de vidrio arriba** (atrás · día · ⋯) en lugar de tres piezas opacas: una sola superficie con desenfoque real deja pasar la lista sin cortarla ni dejar texto «colgando» entre piezas. Sin velo propio; solo un degradado negro de 44 px detrás de la barra de estado.
- **Tipo de carga**: un único control «Externa ⌄» con menú de 4 (descripción de una línea cada una). La cifra de carga (kg estimados) es el botón de **Carga inteligente** → `move`: el módulo «Carga inteligente» deja de ser una fila aparte.
- **Reps**: campo con − / + y valor arrastrable; «Fijas | Rango» pequeño en la misma línea; el rango son dos campos (mín, máx). Se guarda igual que hoy (`targetReps = máx`, `targetRepsRange`).
- **Intensidad en un solo campo**: la unidad (RPE / RIR / Fallo) vive dentro del campo y se cambia con un menú; no hay segmentado. Razón: RPE necesita medios puntos, RIR enteros, el teclado sigue sirviendo y es el mismo patrón que reps y descanso. «Fallo» sustituye el valor por «Hasta el fallo» y muestra las técnicas (Drop-set, Rest-pause, Myo-reps) como hoy.
- **Descanso interactivo**: −15 · tiempo · +15, tiempo arrastrable y atajos (0:30…5:00); etiqueta «Igual que el ejercicio» / «Propio · Restablecer». El del ejercicio usa los mismos controles en su hoja.
- **Acciones**: «Aplicar a todas» y «Duplicar» como botones con icono; Subir, Bajar y Eliminar en «⋯» (Eliminar en rosa, separado). `move` de Subir/Bajar/Eliminar.
- **Series**: «+ Añadir serie» pasa a ser la última ficha punteada (`move`).
- **Borrar deslizando**: píldora oscura que crece desde el borde con la papelera; umbral 104 px (tic + píldora rosa + «Eliminar»); resistencia más allá; salida 220 ms; el aviso con cuenta atrás ocupa el hueco de la propia fila (misma altura) y luego se pliega 260 ms. Deshacer devuelve la fila con rebote. Los minutos bajan al borrar y suben al deshacer.
- **Ordenar**: asa (200 ms) → fila a capa gris #1B1B1D, escala 1,025, sombra, anillo de tinta una vez; hueco punteado; vecinas con transición 260 ms; autoscroll en bordes (hasta 16 px/cuadro); asentamiento 340 ms; la barra de abajo se atenúa al 38 % y no recibe toques. Si hay una tarjeta abierta, se pliega al levantar.
- **Barra inferior**: [anillo + «74 min · de 90»] [Añadir ▸ ⌃ partido] [círculo Asistente con número]. «Reglas» sale de la barra (`move` a la 4.ª pestaña del Asistente). No se esconde al hacer scroll; baja con el teclado; se atenúa al arrastrar. Con letra al 130 % su tamaño de letra se limita a 115 % (patrón habitual de barras).
- **«Añadir» desplegado** (`new`): Ejercicio · Cardio · Grupo nuevo (hoy solo existen como enlaces al final de la lista, que se mantienen); más «Espacio de cardio / Fuerza» si faltan.
- **Sección de cardio** (coherencia con CARDIO-EDITOR): se adopta la suya tal cual: rótulo «CARDIO · AL FINAL» con ↑ (pasar antes/después de la fuerza) y «+», sin selector grande «Al inicio / Al final» ni «Añadir cardio» dentro del módulo, filas con miniatura ilustrada, dosis en una línea y mini barras de intervalos (copiado con prefijo `ed`). La hoja de añadir cardio la dibuja ese paquete.
- Tono del anillo: misma lógica que hoy (verde < 85 %, amarillo hasta 108 %, rojo por encima, gris sin límite).

## 4. Qué exigiría implementarlo
- **Compose**: vidrio con `hazeSource/hazeEffect` ya existe (`kpknGlass`); la isla superior pasa a usarlo (hoy `KpknSurface.inset` opaco) y el contenido de la lista debe empezar bajo ella (`contentPadding` superior + `statusBarsPadding`).
- **Gestos**: reescribir `SwipeToDeleteCard` (píldora creciente, umbral, resistencia, haptic en umbral) y la capa de arrastre (fila levantada con `graphicsLayer`, hueco, animaciones de vecinas con `animateItem`, autoscroll). El estado de borrado pendiente ya existe; añadir que el aviso conserve la altura de la fila.
- **Datos**: ninguno nuevo. Texto nuevo: descripciones del menú de tipo de carga y de unidades de intensidad.
- **Motor**: nada; los pasos (RPE 0,5; RIR 0–5; descanso 15 s; atajos) son de UI. «Añadir» → «Grupo nuevo» usa `addPart`, «Cardio» `openCardioPicker`.
- Accesibilidad: acciones personalizadas «Eliminar ejercicio» (TalkBack), `stateDescription` del gesto, valor con `spinbutton`/`progressBarRangeInfo` en los campos con − / +.
- Reducir transparencia (Android): isla y barra pasan a #1B1B1D plano.

## 5. Dudas para el usuario
1. **Asistente solo con icono** en la barra (círculo con número). ¿O prefieres texto «Asistente» y quitar el anillo de minutos de la barra?
2. **Tocar los minutos**: hoy abre la pestaña «Tiempo» de la hoja de Reglas. Si «Reglas» es la 4.ª pestaña del Asistente, ¿el tiempo se queda con ella (abrir Asistente › Reglas)?
3. **Eliminar serie** hoy es inmediato; ¿añadimos «Serie eliminada · Deshacer» (función nueva)?
4. **Unidad de RIR**: el código no limita el RIR; he usado 0–5. ¿Te sirve?
5. **Peso corporal / lastre / asistido** no tienen campo de kg editable hoy (la carga la estima el motor con tu récord). ¿Queremos poder escribir los kg a mano?
6. Los datos del prototipo (nombres de movilidad, sesiones de la semana, cardio) son de muestra; las miniaturas reales son imágenes del catálogo.
