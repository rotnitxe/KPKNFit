# Plan — Estabilizar drag & drop editor de sesiones (scroll largo / muchos grupos)

**Fecha:** 2026-08-06  
**Autor:** orquestador (muse-spark-1.2)  
**Estado:** `pending_approval` (no editar código hasta aprobación)  
**Solicitud original:** “El sistema de drag and drop del editor de sesiones ha mejorado un montón, pero sigue siendo muy errático. El principal problema es que en los grupos y ejercicios que están al comienzo de la pantalla funciona, pero una vez agrego más grupos abajo y más ejercicios, deja de funcionar o bien, hace un movimiento forzado a tirar los ejercicios dentro del grupo o fuera hacia el grupo que está más arriba.”

---

## 1. Resumen ejecutivo

El drag & drop actual congela `boundsInWindow` en `beginExerciseDrag` y nunca los refresca durante el gesto. `LazyColumn` virtualiza; los ítems fuera de viewport no tienen `Rect`. Sin auto-scroll, arrastrar hacia la cola con 6+ grupos obliga a `nearestDropZone` a elegir el grupo visible más cercano (arriba) o `null`. Además `GroupEditorCard` ignora `dragOffsetY` y `mergeBounds` acumula área suelta.

El plan corrige la fuente sin tocar `domain/`:
1) pasar de coordenadas ventana congeladas a coordenadas compensadas por scroll,
2) usar `LazyListState.layoutInfo.visibleItemsInfo` como verdad para hit-testing,
3) implementar auto-scroll por threshold (96 dp) durante drag,
4) sanear `GroupEditorCard` y `mergeBounds`,
5) añadir tests de regresión y validación manual con 8×4.

No requiere migración Room, ni AUGE, ni voz.

---

## 2. Contexto y reproducción

- **Repro fiable:** Programa nuevo → 8 grupos (cada uno 4 ejercicios sueltos o 1 superset de 3) sin colapsar. Arrastrar ejercicio del grupo 1 intentando soltar en grupo 7-8 manteniendo dedo cerca del borde inferior sin soltar. Actual: indicador desaparece, al levantar dedo cae en grupo 3-4 o no se mueve; con superset el salto es más errático.
- **Síntoma “arriba”:** `nearestDropZone` con `bestDistance > 96f` descarta el grupo inferior virtualizado y devuelve el superior más cercano (Manhattan `dx+dy`).
- **Síntoma “no funciona abajo”:** `isExerciseDragging==true` bloquea `onGloballyPositioned` en `SessionEditorScreen.kt:440-447` y `SessionEditorScrollRenderer.kt:150`, por lo que grupos que entran a viewport durante el gesto nunca registran `Rect`.
- **WIP actual (2026-08-06):** `git diff HEAD` muestra cambios de layout (`SessionEditorScreen`, `SessionHero`, `SessionContextNavigator` a dock inferior, `navigatorHeightDp`, FABs draggables) pero **ninguno** toca `SessionEditorDragController.kt`. El bug persiste.

---

## 3. Hallazgos de investigación (con subagentes)

### 3.1 Investigador — arquitectura y evidencia `archivo:línea`

| Área | Evidencia | Hallazgo |
|---|---|---|
| Registro bounds | `SessionEditorDragController.kt:18-22` maps; `ExerciseEditorCard.kt:221`, `GroupEditorCard.kt:379/561`, `SupersetGroupEditorCard.kt:120`, `SessionEditorScrollRenderer.kt:415/484/549/596/664/709`, `SessionEditorScreen.kt:377` | Todo en `boundsInWindow()` (ventana). |
| Congelado | `DragController.kt:40-42` `frozen*`; `68-93` `beginExerciseDrag` hace `frozen=toMap()` + `freshLoose/freshParts` vía `unionRects` | Snapshot del frame inicial, no se invalida con scroll. Comentario `79-81` ya advierte “no acumular historial”. |
| Guard | `SessionEditorScreen.kt:440-447` `if(!isExerciseDragging)`; `SessionEditorScrollRenderer.kt:150` | Durante drag se deja de reportar bounds → grupos que aparecen tras scroll manual no existen. |
| Pointer stale | `DragController.kt:159-164` `pointer = startRect.left/top + grab + offset`; `37` `dragStartGrabOffset` default `24f,24f`; `ExerciseEditorCard.kt:253` `onDragStart(offset)` | `startRect` es window congelado; `draggingExerciseOffset` es incremental local sin compensar `listState` scroll. |
| insertionY | `DragController.kt:182` `insertionY = startRect.top + grab.y + offset.y`; `187` `orderedKeys.sortedBy{top}` sobre `frozen` | Compara Y stale vs `top` stale → orden desfasado >300 px con 8 grupos. |
| nearestDropZone | `DragController.kt:115-139` `containsTolerant 28f`, `gapDistance dx+dy`, `bestDistance<=96f else null` | Umbral 96 px descarta grupos lejanos virtualizados. |
| Virtualización | `SessionEditorScreen.kt:385-418` `LazyColumn(state=listState, items(key=stableKey))`; `SessionListItems.kt:79-117` `buildSessionListItems`; `52-66` `pruneBounds` | Solo visibles tienen `Rect`. `unionRects:95` subestima si falta cola virtualizada. |
| Auto-scroll | búsqueda `autoScroll\|scrollBy\|bringIntoView` sin hits en `sessioneditor/` salvo `SessionEditorScreen.kt:297` `animateScrollToItem` de auto-expand | **No existe auto-scroll durante drag**. |
| ViewModel | `SessionEditorViewModelStructure.kt:276-381` `moveExerciseToPart` con `adjustedIndex` y `coerceIn`; `58-66` `movePartToIndex`; `DragController.kt:211-251` `endExerciseDrag` 3 ramas | Lógica off-by-one controlada pero depende de `targetIndex` calculado sobre lista pre-remoción; si `before==null` fuera de viewport cae en `toIndex=null` (append) no en Y del dedo. |
| Bug visual | `GroupEditorCard.kt:380` `graphicsLayer{translationY=0f}` vs `ExerciseEditorCard.kt:224` `translationY=dragOffset.y` | Grupo arrastrado no se desplaza (solo preview `DragPartLiftPreview:702`). |
| Superset | `SupersetGroupEditorCard.kt:120` 1 `Rect` por bloque; `SessionEditorScrollRenderer.kt:549/596` guarda solo `firstMember` | Gap intra-superset no mapeado; hit-test cuenta superset como 1 ítem alto. |

Investigador concluye: causa raíz es **coordenadas ventana congeladas + virtualización sin auto-scroll**.

### 3.2 android-compose — MVVM/UDF y Compose

- **UDF roto:** `SessionEditorScreen.kt:204` `dragController = remember(session?.id){...}` vive en composición, no en VM. Expone `mutableStateMapOf` públicos sin `StateFlow asStateFlow` (`DragController.kt:18-42`). `Screen` llama `begin/update/end` directo (`230-243`) en lugar de `ViewModel.onEvent`.
- **Recomposition:** cada `onGloballyPositioned` invalida `mutableStateMapOf` y cada `animateFloatAsState:137/182 SessionEditorScrollRenderer` invalida por ítem → con 30 ejercicios + `hazeSource:379` coste GPU alto.
- **Gesture consume:** `ExerciseEditorCard.kt:257` `change.consume()` + `GroupEditorCard.kt:433` bloquea `NestedScroll` del `LazyColumn`. `pointerInput(key=exercise.id)` recompone detector si cambia `id`.
- **zIndex/haze:** previews fuera de `hazeSource` (`SessionEditorScreen.kt:379` envuelve `Scaffold+LazyColumn`, previews `692-709` `zIndex 500f` siblings) es correcto; pero `y=rect.top - root.top + offset` usa `Rect` ventana congelado → desancla con scroll/`imePadding:389`.
- **Propuesta minimal (ya validada contra `compose-mvvm` skill):** no tocar `Navigation.kt`, mantener dominio puro, llevar estado drag a VM o al menos compensar scroll y usar `layoutInfo.visibleItemsInfo`.

---

## 4. Diseño propuesto

### 4.1 Objetivo

Drag estable con 12 grupos × 5 ejercicios, scrolled, con feedback visual fiel y drop determinista. Mantener 60 fps, no romper haze, respetar Clean Architecture y manual DI.

### 4.2 No objetivos

- Reescribir a `Draggable` completo ni a librería externa.
- Migrar Room / AUGE / voz / backend.
- Cambiar `domain/auge`, `domain/training`, `domain/workout`.

### 4.3 Estrategia en 2 fases (solo Fase 1 bloquea el bug, Fase 2 pulida si se aprueba)

**Fase 1 — hotfix determinista (mínimo riesgo):**
- Compensar scroll en `updateExerciseDrag`/`updatePartDrag` + auto-scroll threshold + fix `GroupEditorCard` translation + sanear `mergeBounds` guard.

**Fase 2 — robustez (si la review ve necesario mover estado a VM):**
- Mover `dragController` a `SessionEditorViewModel` como `StateFlow` inmutable o al menos exponer `dragUiState` con `asStateFlow()` y eventos `onDragStarted/Updated/Ended`.

El plan detalla Fase 1 completa; Fase 2 queda como follow-up opcional.

### 4.4 Cambios detallados por archivo

#### A. `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorDragController.kt`

- **Líneas 16-43:** añadir `var initialScrollOffset: Int = 0` y `var initialFirstVisibleIndex: Int = 0` (o `Pair`). No exponer `MutableStateFlow`, mantener `mutableStateOf` pero documentar que VM lo setea.
- **Líneas 68-93 `beginExerciseDrag`:** sobrecargar `beginExerciseDrag(partId, exerciseId, grab, listState: LazyListState)`; capturar `initialScrollOffset = listState.firstVisibleItemScrollOffset` + `initialFirstVisibleIndex = listState.firstVisibleItemIndex`; `dragStartExerciseRect` sigue igual pero además guardar `dragStartWindowTop`. Mantener `frozen*` pero documentar que es “frozen + scroll-compensated”.
- **Líneas 155-209 `updateExerciseDrag`:** firma `updateExerciseDrag(delta, session, listState)`; tras `draggingExerciseOffset += delta` calcular `scrollDelta = (listState.firstVisibleItemIndex - initialFirstVisibleIndex) * approxItemHeight + (listState.firstVisibleItemScrollOffset - initialScrollOffset)` — mejor aún leer `layoutInfo.viewportStartOffset` si disponible; restar a `pointer.y`/`insertionY`: `val compensatedPointerY = pointer.y - scrollCompensation`. Alternativa más simple y precisa: en vez de `startRect.top + offset`, recalcular `pointer` como `dragStartWindowTop + scrollCompensation + offset.y` donde `scrollCompensation` se resta. Comparar contra `frozenExerciseBounds` cuyo `top` también se compensa `top - scrollCompensation`. Dejar `frozenPartContentBounds` compensado igual antes de `containsTolerant`.
- **Líneas 115-139 `nearestDropZone` / `111 containsTolerant`:** aumentar tolerancia vertical a `36f` si se valida en device, pero mantener `96f` como fallback; añadir rama: si `targetPartId==null` y `pointer.y > viewportBottom - 48f`, proyectar `targetPartId = lastVisiblePartId` (no elegir el primero arriba).
- **Líneas 279-296 `updatePartDrag`:** igual compensación de scroll: `updatePartDrag(deltaY, groupedParts, listState)`.
- **Líneas 271-277 `beginPartDrag`:** sobrecarga con `listState` para capturar offset inicial.

> Nota: si se prefiere no pasar `LazyListState` dentro del controller (puro), alternativa: `SessionEditorScreen` calcula `scrollDelta` y lo pasa como `Int`/`Float`. Mantiene `domain/` puro (controller está en `screens/`, no en `domain/`, así que puede importar `LazyListState`).

#### B. `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScreen.kt`

- **Líneas 149-154 `listState`:** ya existe `rememberLazyListState()`. Añadir `val scrollDelta = rememberUpdatedState(listState.firstVisibleItemScrollOffset)` y exponer a drag.
- **Líneas 204-228 `dragController` y `LaunchedEffect` de `pruneBounds`:** mantener `remember(session?.id)` pero añadir `LaunchedEffect(isExerciseDragging, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)` que cuando `isExerciseDragging` sea true, **no** llame `pruneBounds`, pero sí actualice `frozen*` compensación (o dispare recomposición compensada). No vaciar maps durante drag.
- **Líneas 230-243 `beginExerciseDrag/updateExerciseDrag/endExerciseDrag`:** pasar `listState` (o `scrollDeltaPx`) a controller. Ejemplo: `beginExerciseDrag(partId, id, grab, listState)` y `updateExerciseDrag(delta, session, listState)`.
- **Líneas 377 `editorRootBounds`:** cambiar `boundsInWindow()` a `boundsInParent` o mantener pero documentar que preview usa `boundsInWindow` compensado por `windowInsets`; asegurar que `DragLiftPreview` reciba `rootBounds` en mismas coordenadas.
- **Líneas 385-455 `LazyColumn`:** añadir `LaunchedEffect(draggingExerciseId, draggingPartId)` con auto-scroll:
  ```kotlin
  LaunchedEffect(draggingExerciseId, draggingPartId) {
    if (draggingExerciseId==null && draggingPartId==null) return@LaunchedEffect
    snapshotFlow { listState.layoutInfo.viewportSize to listState.layoutInfo.visibleItemsInfo }
      .collect { /* no-op, trigger */ }
    while (isActive && (draggingExerciseId!=null || draggingPartId!=null)) {
      val pointerY = /* exponer desde controller: currentPointerY compensado */
      val viewportTop = listState.layoutInfo.viewportStartOffset
      val viewportBottom = listState.layoutInfo.viewportEndOffset
      val threshold = 120.dp.toPx()
      val speed = 15.dp.toPx()
      when {
        pointerY < viewportTop + threshold -> listState.scrollBy(-speed)
        pointerY > viewportBottom - threshold -> listState.scrollBy(+speed)
      }
      delay(16)
    }
  }
  ```
  Usar `LocalDensity` ya disponible (`197`). Debe ejecutarse en `Dispatchers.Main` (default de `LaunchedEffect`).
- **Líneas 440-448 `onLooseBoundsReport/onPartContentBoundsReport`:** cambiar guard `if(!isExerciseDragging)` a permitir actualización durante drag **solo si** el `Rect` viene de `layoutInfo.visibleItemsInfo` (item visible). Es decir, si `isExerciseDragging` y `partBounds` ya contiene key, no sobreescribir; si no contiene (item entró a viewport por auto-scroll), sí insertar. Evita “suelta engulle pantalla” pero permite nuevos grupos abajo.
- **Líneas 585-637 FABs draggables:** sin cambios, pero asegurar que `pointerInput` de FABs no interfiera con drag del editor (ya usan `zIndex 260` fuera de `LazyColumn`, correcto).
- **Líneas 692-710 previews:** pasar `currentPointer` compensado en lugar de `rect + offset` stale. O recalcular `x/y` como `dragStartRect.left - root.left + offset.x` donde `offset` ya está compensado por scroll.

#### C. `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScrollRenderer.kt`

- **Líneas 42-43 imports:** añadir `androidx.compose.foundation.lazy.LazyListState` si se pasa desde Screen.
- **Líneas 146-152 / 198-199 / 245 / 298 / 337 `onGloballyPositioned`:** mantener pero cambiar a `boundsInParent` o `positionInParent` si se decide unificar. Si se mantiene `boundsInWindow`, al menos documentar que controller compensa scroll; no cambiar ambos a la vez para minimizar riesgo.
- **Líneas 137-145 / 182-190 / 231 / 281 `animateFloatAsState`:** envolver `projectedShiftFor` en `derivedStateOf` para reducir recompositions; no recalcular shift por cada frame si target no cambió.
- **Líneas 773-781 `mergeBounds`:** reemplazar acumulación monotónica `mergeBounds(existing,incoming)` por `unionRects(visibleRects)` fresco cuando `isExerciseDragging==false`; durante drag, no acumular, solo actualizar map con `Rect` del item que reporta (no union). Evita zona suelta gigante.
- **Líneas 415/484 etc `exerciseBounds["$partId|id"]=rect`:** ya correcto; añadir comentario que durante drag solo se insertan keys nuevas, no se hace `merge`.

#### D. `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/GroupEditorCard.kt`

- **Línea 380 `graphicsLayer{translationY=0f}`:** corregir a `translationY = if(isDragging) dragOffsetY else 0f` (mismo patrón que `ExerciseEditorCard.kt:224`). Recibe `dragOffsetY:349` y ya se pasa `draggingPartOffsetY` en `SessionEditorScrollRenderer.kt`.
- **Líneas 428-437 `pointerInput`:** cambiar `detectDragGestures` a `detectDragGesturesAfterLongPress` (ya importado `29`) o mantener pero añadir `awaitPointerSlop` antes de `consume` para no robar scroll; o al menos `change.consume()` solo tras superar `ViewConfiguration.touchSlop`. Así el dedo en borde aún puede scrollear `LazyColumn` manualmente si auto-scroll no dispara.

#### E. `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/ExerciseEditorCard.kt`

- **Líneas 247-260 `pointerInput(exercise.id)`:** cambiar key de `exercise.id` a `Unit` o `exercise.id to isDragging` para no reiniciar detector en recomposición; añadir `awaitPointerSlop` o usar `detectDragGesturesAfterLongPress` (como `GroupEditorCard` ya sugiere) para distinguir tap/long-press de drag. Mantener `Box(48.dp)` hitbox.
- **Línea 257 `change.consume()`:** condicionar a haber superado slop, para no consumir todo el gesture antes de que `LazyColumn` pueda iniciar scroll.

#### F. `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/SupersetGroupEditorCard.kt` (opcional Fase 1, si no, Fase 2)

- **Línea 120 `onBoundsChange`:** registrar no solo bloque sino también rect por miembro visible para que `orderedKeys.filterKeys StartsWith targetPartId` vea gaps intra-superset. O documentar que superset se mueve como bloque indivisible (comportamiento actual) y no se permite drop intra-superset.

#### G. `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionListItems.kt` y `SessionEditorViewModelStructure.kt`

- Sin cambios de lógica; verificar que `stableKey:18-76` sigue único y que `moveExerciseToPart:326-381` mantiene `adjustedIndex` con `coerceIn`. Añadir test para caso `drop en grupo vacío virtualizado` (`targetIndex=size`).

---

## 5. Impacto

| Plataforma | Impacto | Detalle |
|---|---|---|
| **Android** | **Sí — directo** | Solo `screens/sessioneditor/**` y `components/*`. No toca `data/db`, `domain/auge`, `services/workout`, `navigation`. |
| **iOS** | No / Documentativo | `ios-native/` no tiene editor de sesiones con drag (en progreso SwiftUI). Si existe paridad, replicar threshold 96 pt + auto-scroll `UIScrollView` + `NSCollectionLayout`. Dejar nota en `docs/IOS_PARITY.md` si aplica. |
| **Backend** | **Nulo** | `backend/` es FastAPI de análisis opcional; no hay lógica de sesión. |

**Banderas:**

| Bandera | Afectada | Valor |
|---|---|---|
| **Room** | **No** | DB v20 autoridad (`KpknDatabase.kt`). No se tocan `data/db/`, `data/repository/`, `app/schemas/`. |
| **AUGE** | **No** | No se toca `domain/auge/`, `SessionEditorAugeComputation`, ni `auge-parity`. |
| **Voz** | **No** | No se toca `services/workout/` (Vosk/PTT). |

---

## 6. Pruebas a ejecutar

### 6.1 Unit (JVM, `android-native/`)

- `gradlew.bat testBaseDebugUnitTest --tests "*SessionEditorDragControllerTest*"` (crear nuevo o ampliar existente en `app/src/test/java/com/example/kpkn/screens/sessioneditor/`):
  - `begin + update sin scroll` → target correcto entre 2 visible.
  - `begin + update con scrollDelta 400px` → `pointer compensated` mantiene zona inferior (repro del bug, debe fallar antes del fix y pasar después).
  - `nearestDropZone con map vacío / bestDistance>96` → `null`.
  - `updatePartDrag con groupedParts 8` y scroll simulado → índice ajustado en borde.
  - `endExerciseDrag con finalTargetKey null pero finalTargetPart != null` → `onMoveExercise` con `toIndex=null` (append).
- `gradlew.bat testBaseDebugUnitTest --tests "*SessionListItems*"` si existe, si no smoke de `buildSessionListItems` con `collapsedPartIds`.

### 6.2 Instrumented / Compose

- `createComposeRule` en `androidTest` (si hay flavor) con `Session` 12×5 + superset:
  - `performTouchInput { longPress(dragHandle); moveBy(0, 800) }` sobre `ExerciseEditorCard` → assert `exerciseDropTargetPartId` cambia a grupo inferior y `listState.firstVisibleItemIndex` avanza (auto-scroll).
  - Sin auto-scroll el test debe fallar (regresión esperada pre-fix).
  - Verificar `GroupEditorCard` arrastra visualmente (`translationY != 0`).

Si no hay `androidTest` en repo, reemplazar por QA manual instrumentada (ver 6.3) y dejar TODO para añadir.

### 6.3 Manual QA — checklist dispositivo (mid-range, Android 13+)

1. **Cola larga:** crear 8 grupos × 4 ejercicios, sin colapsar. Drag ej. G1-E1 → G8, manteniendo dedo en threshold inferior 96 dp → debe auto-scrollear suave y mostrar indicador `SessionEditorDropIndicator` en G8. Soltar → cae en G8 posición donde estaba dedo.
2. **Intermedio:** drag G3-E2 → G5 posición 1 (entre E1-E2) → indicador entre tarjetas, gap `projectedShiftFor` de `(itemHeight+10)*movingCount`.
3. **Loose ↔ grupo:** drag ejercicio suelto (“sin categoría”) → grupo y viceversa, con lista scrolled a mitad.
4. **Grupo reorder:** drag header G2 → entre G6-G7 (cerca de borde inferior) → auto-scroll + `movePartToIndex` ajustado.
5. **Superset:** superset de 3 en G4 → arrastrar bloque completo a G7 → se mueve como bloque, sin dejar miembros huérfanos.
6. **Haze + teclado:** con `Assistant FAB` visible y `imePadding` (abrir rename grupo) → preview no parpadea, haze no muestrea preview.
7. **Colapsado:** colapsar G5-G6 → drag sobre header colapsado no debe dropear dentro (contenido no visible) sino antes/después.
8. **Rotación:** rotar durante drag (debe cancelar gesto sin crash, `resetExerciseDrag` limpia).

### 6.4 Build

- `gradlew.bat compileBaseDebugKotlin` (targeted, flavors base/health, `compileDebugKotlin` es ambiguo) — verificar que wiring no rompe.
- `gradlew.bat assembleDebug` (construye ambos flavors) — QA final antes de subir.
- `gradlew.bat testBaseDebugUnitTest` (completo) si hay tiempo; al menos tests de feature.

---

## 7. Documentación a actualizar

- `docs/ANDROID_UI_SCREENS_MAP.md`: añadir sección SessionEditor drag & drop: coordenadas, virtualización, auto-scroll threshold 96 dp, `LazyListState` como fuente.
- `docs/ARCHITECTURE.md` / `docs/ANDROID_ARCHITECTURE_MAP.md`: aclarar que drag state vive en `screens/sessioneditor/` (Fase 1 en composición, Fase 2 en VM) y que trabajo pesado sigue en `Dispatchers.IO`, UI en `Main`; no hay Room v21.
- `.opencode/kpkn-map.md`: regenerar vía `/map` si se añaden archivos de test.
- `README.md` no necesita cambio.

> Código y esquema Room v20 son autoridad si docs dicen v19 (regla del proyecto).

---

## 8. Riesgos y mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| `scrollCompensation` mal calculado (item height variable) | Media | Drop desfasado | Usar `firstVisibleItemScrollOffset` + `layoutInfo.visibleItemsInfo` en lugar de altura fija; validar con device. |
| Auto-scroll demasiado rápido/lento, jank con haze | Media | UX mala / frame drops | `speed=12-18dp` por frame 16 ms, `delay(16)`, `derivedStateOf` para shift; probar con 30 ejercicios y haze activo. |
| `change.consume()` aún bloquea NestedScroll | Baja | No scrollea manual en borde | Consumir solo tras slop; usar `detectDragGesturesAfterLongPress`. |
| Preview desanclado con `imePadding/navigationBarsPadding` | Baja | Visual glitch | Preview fuera de `hazeSource` (ya correcto), recalcular `rootBounds` en cada frame o usar `positionInRoot`. |
| Recomposition excesiva por `mutableStateMapOf` | Media | Jank en lista larga | `derivedStateOf` + `key(stableKey)` ya mitiga; no añadir `snapshotFlow` pesado en cada ítem. |
| Superset intra-gap no mapeado | Baja | Drop intra-superset errático | Documentar como bloque indivisible Fase 1; Fase 2 mapear miembros visibles. |
| Regresión `movePartToIndex` off-by-one al final | Baja | Grupo al final se inserta uno antes | Test unit con `targetIndex=size` + `coerceIn` ya cubierto; validar manual. |
| Compensación rompe `DragLiftPreview` y `DragPartLiftPreview` | Baja | Preview salta | Pasar `compensatedPointer` al preview, no `rect + offset` stale. |

---

## 9. Criterios de aceptación

- [ ] Con 8 grupos × 4 ejercicios, drag G1→G8 con dedo en borde inferior auto-scrollea y suelta en G8 donde estaba dedo (no en G3-4).
- [ ] Drag intra-grupo y loose↔grupo funciona con lista scrolled a mitad.
- [ ] Drag de header de grupo traslada visualmente (`GroupEditorCard` `translationY`).
- [ ] Sin crash/ANR, 60 fps percibido, haze estable, preview alineado con dedo.
- [ ] `compileBaseDebugKotlin` y `assembleDebug` verdes; `testBaseDebugUnitTest` con nuevos tests pasa.
- [ ] Docs `ANDROID_UI_SCREENS_MAP.md` y `kpkn-map.md` actualizados.

---

## 10. Plan de entrega (requiere aprobación)

1. **Aprobación explícita** de este plan (pipeline `request_approval` → `construction`).
2. `constructor_kpkn` ejecuta Fase 1 en rama corta, con commits atomicos por archivo:
   - `SessionEditorDragController.kt` (compensación scroll + firmas)
   - `SessionEditorScreen.kt` (auto-scroll + wiring)
   - `SessionEditorScrollRenderer.kt` + `GroupEditorCard.kt` + `ExerciseEditorCard.kt`
3. Añade `SessionEditorDragControllerTest.kt` y QA manual checklist.
4. `gradlew.bat compileBaseDebugKotlin` → `gradlew.bat testBaseDebugUnitTest` → `gradlew.bat assembleDebug`.
5. Auditor revisa diff vs plan; `pipeline submit_audit` → `auditing`.
6. Si auditor pide Fase 2 (mover estado a VM), abrir plan follow-up en `.opencode/plans/`.

---

## 11. Alternativas descartadas

- **Reescribir con `LazyColumn` + `Reorderable` lib (Burner):** añade dependencia, rompe manual DI y paridad iOS; overkill para bug de coordenadas.
- **Migrar todo a `boundsInParent` puro sin compensación:** requiere tocar todos los `onGloballyPositioned`; mismo efecto que compensación pero más churn; se evaluará en Fase 2 si compensación no basta.
- **Desactivar virtualización (`Column` + `verticalScroll`):** con 30+ ejercicios el costo de composición es alto y rompe `rememberLazyListState` hero; no viable.

---

## 12. Referencias exactas (para auditor)

- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorDragController.kt:16-42`, `68-93`, `111-153`, `155-209`, `211-251`, `279-319`, `321-336`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScreen.kt:149`, `204-243`, `277-284`, `316-350`, `373-399`, `385-455`, `440-448`, `585-710`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScrollRenderer.kt:42-43`, `137-152`, `182-199`, `231/245`, `281/298`, `337`, `773-781`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/GroupEditorCard.kt:29-30`, `349-389`, `428-437`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/ExerciseEditorCard.kt:221`, `247-260`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/SupersetGroupEditorCard.kt:58-60`, `120`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionListItems.kt:18-117`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelStructure.kt:58-66`, `276-381`

---

> **Siguiente paso:** aprobar este plan para pasar a `construction`. No se editará código de producto hasta `pipeline.start` + `request_approval` confirmados.
