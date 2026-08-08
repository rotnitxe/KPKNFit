# AUDITORÍA: SUPERSETS Y SUPERSETS OPCIONALES — Editor de Sesiones vs Sesión en Vivo

**Fecha:** 2026-08 — **Repo:** KPKNFit (`android-native/`)
**Ámbito:** lógica de negocio (solo lectura; no se modificó código).
**Archivos auditados:** `screens/sessioneditor/` (VM de supersets, estructura, tarjetas, sheets), `domain/workout/SupersetRules.kt`, `domain/workout/WorkoutStructuralEditor.kt`, `data/models/Session.kt`, `screens/workout/` (WorkoutViewModel mediante búsquedas dirigidas, Navigator, StepRules, Hydrator, RestTimerOrchestrator, SetRecorder, StructureSheetsHost, StructuralPersistenceController, VoiceCommandHandler), tests `SupersetRulesTest`, `WorkoutSessionRulesTest`, `WorkoutStepRulesTest`.

Rutas relativas a `android-native/app/src/main/java/com/example/kpkn/` salvo tests (`app/src/test/java/com/example/kpkn/`).

---

## RESUMEN EJECUTIVO

La traducción editor→vivo pasa por el modelo persistido `Session.supersetGroups: List<SupersetGroup>` (`data/models/Session.kt:34, 64-75`) más refs por ejercicio (`supersetGroupRef` / `supersetId`, helpers en `Session.kt:438-450`). El vivo reconstruye los pasos de forma **derivada y stateless**: `WorkoutStepRules.buildSteps` (`screens/workout/WorkoutStepRules.kt:33-49`), invocado desde `WorkoutStepNavigator.workoutStepPositions` (`screens/workout/WorkoutStepNavigator.kt:89-96`), usando `visibleExercises` (`screens/workout/WorkoutViewModel.kt:1510-1516`). El motor compartido `SupersetRules` (`domain/workout/SupersetRules.kt`) se usa en ambos lados, aportando consistencia en orden de miembros y rondas… **salvo dos fallos estructurales graves:**

1. La funcionalidad **"Superset Opcional" no existe en el vivo** (ni lectura del flag, ni activación) y además `SupersetRules.normalizeSession` **borra el flag** (`isOptional` → `false`) cada vez que se normaliza, lo que ocurre en hidratación y en casi cualquier edición estructural → pérdida silenciosa del dato.
2. **`group.rounds` puede quedar por debajo del número de sets** de algún miembro → los sets sobrantes nunca se materializan como pasos en vivo (inalcanzables), pero sí cuentan en el denominador de progreso de sesión.

**Nota positiva de arquitectura:** el plan de pasos es derivado (no persistido), lo que elimina toda una clase de desync editor↔vivo; los problemas se concentran en (a) metadatos no propagados por `normalizeSession` y (b) escrituras de `rounds` no transaccionales respecto a los sets.

---

## HALLAZGOS PRIORIZADOS

### 🔴 CRÍTICO-1 · "Superset Opcional": flag persistido por el editor pero 100 % ignorado en vivo, y la normalización lo borra (pérdida de datos)

**(Pregunta 3) — Desconexión total: el editor guarda pero el vivo ignora; y la capa de dominio destruye el dato.**

**Editor (dónde se marca y persiste):**

- Modelo: `isOptional` en `data/models/Session.kt:74` (`data class SupersetGroup`, `:64-75`).
- Toggle del ViewModel: `screens/sessioneditor/SessionEditorViewModelSupersets.kt:280-286` (`toggleSupersetOptional` hace `g.copy(isOptional = !g.isOptional)` directamente, sin pasar por `normalizeSession`).
- UI: check + estilo en `screens/sessioneditor/components/SupersetGroupEditorCard.kt:325-361`; diálogo informativo en `:370-408`.

**La UI del editor promete comportamiento en vivo que NO existe:**

> *"Un Superset opcional es un tipo de superset que se muestra únicamente cuando lo activas desde tu sesión de entreno; si no lo invocas, te mostrará los ejercicios separados."* — `SupersetGroupEditorCard.kt:381-384`.

**El vivo no contiene ninguna referencia a `isOptional`:** búsqueda exhaustiva en `screens/workout/*.kt` — los únicos "opcional" son textos sin relación: `WorkoutContextComponents.kt:707`, `WorkoutFinishHost.kt:793`, `WorkoutV2Body.kt:234`. `WorkoutStepRules.buildSteps` (`WorkoutStepRules.kt:37-45`) emite pasos de superserie para cualquier grupo con ≥ 2 miembros, **sin consultar el flag**; no existe mecanismo para "invocar" la superserie ni para ejecutar los ejercicios separados. Tampoco aparece en `domain/workout/*.kt`, en el cálculo de descansos (`WorkoutSetRecorder.kt`) ni en voz (`WorkoutVoiceCommandHandler.kt`).

**Agravante — pérdida silenciosa del dato:** `SupersetRules.normalizeSession` reconstruye cada `SupersetGroup` copiando campo a campo y **omite `isOptional`** (`domain/workout/SupersetRules.kt:33-46`: copia `id`, `exerciseOrder`, `restBetweenExercises`, `restAfterSuperset`, `rounds`, `visualPlacement`, `roundRestBetweenExercises`, `roundRestAfterSuperset`; `isOptional` recupera su default `false`). `normalizeSession` se ejecuta en:

- Hidratación del entreno: `screens/workout/WorkoutSessionHydrator.kt:119` (→ `WorkoutViewModel.kt:271`, `:510`, `:1109-1113`).
- `createSuperset` (`SupersetRules.kt:179`), `updateRest` (`SupersetRules.kt:277`), `updateRoundRest` (`SupersetRules.kt:304-305`), `removeExercise` (`SupersetRules.kt:341`).
- Persistencia de mutaciones del vivo al programa: `screens/workout/WorkoutStructuralPersistenceController.kt:316` (re-normaliza) y `:383-397` (`persistSessionToProgram`).

**Resultado:** entrenar la sesión una sola vez, o casi cualquier edición estructural posterior en el editor (crear otra superserie, cambiar descansos, quitar un miembro, cambiar descanso de una ronda), **revierte todos los "supersets opcionales" a `false` en los datos persistidos**, incluidos grupos no relacionados con la edición.

**Contraste (los clones SÍ lo preservan):** `domain/templates/SessionTemplateEngine.kt:66-72` y `screens/sessioneditor/SessionEditorCloneHelpers.kt:68-82` usan `group.copy(...)` → `isOptional` sobrevive al clonado; la pérdida es exclusiva de `normalizeSession`.

**Cobertura de tests:** nula (ningún test menciona `isOptional`).

**Impacto:** feature documentada en UI del editor e inexistente en runtime; destrucción transversal del dato del usuario al mero hidratar/persistir.

---

### 🔴 CRÍTICO-2 · Desacople `group.rounds` ↔ nº de sets de miembros: sets inalcanzables en vivo y sesión que nunca llega al 100 %

**(Preguntas 1, 2 y 6)**

- El vivo materializa pasos SOLO para `repeat(rounds)` con `setIndex = roundIdx` (`WorkoutStepRules.kt:118-134`), y `roundCount` prioriza `group.rounds > 0` sobre el máximo de sets de los miembros (`SupersetRules.roundCount`, `SupersetRules.kt:205-210`). **Si `rounds < máx sets`, los sets por encima de `rounds` nunca se convierten en pasos** → incompletables por la navegación de pasos.
- Pero el denominador de progreso cuenta TODOS los sets: `totalSetsInSession = allExercises.sumOf { it.sets.size }` (`screens/workout/WorkoutSetRecorder.kt:491-495`) → `sessionProgress` nunca llega a 1.0 aunque se complete todo lo alcanzable; afecta a progreso mostrado, cálculo AUGE y reportes de fin de sesión. Además el multiplicador de densidad AUGE usa `supersetRounds = supersetGroup?.rounds` (`WorkoutSetRecorder.kt:474-480`), sesgado cuando `rounds` no coincide con la realidad.

**Caminos reales para desincronizar (desde ambos lados):**

1. **Editor — sets por miembro sin tocar `rounds`:** los botones +/- de sets dentro de la tarjeta del grupo actualizan al ejercicio pero no `group.rounds` (wiring `onAddSet`/`onRemoveSet` por miembro: `screens/sessioneditor/SessionEditorScrollRenderer.kt:492-495`, `:616-619`, `:694-698` → `viewModel.addSet`/`removeSet`, no `updateSupersetRest`).
2. **Vivo — hoja "Rondas y descansos":** permite teclear rondas libres (`screens/workout/WorkoutStructureSheetsHost.kt:332-377`, campo `roundsText` limitado a `.take(2)` dígitos) → `viewModel.updateLiveSupersetRest(...)` (`WorkoutViewModel.kt:1834-1849`) → `SupersetRules.updateRest` (`SupersetRules.kt:250-294`), que fija `rounds` **sin añadir ni quitar sets**; y `applySessionMutation` persiste al programa (`WorkoutStructuralPersistenceController.kt:383-397`).
3. **Editor — campo "Rondas (opcional)" del creator:** `components/sheets/SupersetCreatorAndManagerSheets.kt:224-232` → `createSupersetGroupFromDraft` (`SessionEditorViewModelSupersets.kt:165-188`) → `SupersetRules.createSuperset(rounds = draft.rounds)` (`SupersetRules.kt:100`), que fija `rounds` sin ajustar sets.

**Los únicos caminos sincronizados:**

- `onAddRound` (`SessionEditorScrollRenderer.kt:578-582` y `:706-711`): incrementa `rounds` **y** añade el set faltante a cada miembro (`if (member.sets.size < nextRound) viewModel.addSet(...)`).
- `removeSupersetRound` (`SessionEditorViewModelSupersets.kt:210-237`): quita el set en `roundIndex` de cada miembro que lo tenga, decrementa `rounds` (mínimo 1) y **re-indexa correctamente** los mapas de descansos (`:221-227`), aunque solo opera sobre el `partId` indicado.

**Subcaso inverso (`rounds > máx sets`):** inocuo para los pasos (`WorkoutStepRules.kt:121`: `if (roundIdx !in exercise.sets.indices) return@forEachIndexed` simplemente salta índices sin set), pero el título en vivo "Superserie N rondas" (`WorkoutStructureSheetsHost.kt:208`, con `?: SupersetRules.roundCount`) puede anunciar rondas vacías que no generan trabajo.

**Visualización editor (misma fórmula que el vivo):** `rounds = (group.rounds ?: exercises.maxOfOrNull { it.sets.size } ?: 1).coerceAtLeast(1)` (`SupersetGroupEditorCard.kt:115`) — el editor renderiza el carrusel con el mismo valor que usará el vivo; **el desync no es evidente comparando pantallas: se manifiesta como sets "fantasma" en los miembros que exceden `rounds`.**

**Gap de tests:** `SupersetRulesTest.kt` no cubre `rounds < sets` ni el materializado de pasos con `rounds` fijo; `WorkoutStepRulesTest.kt` crea superseries siempre con `rounds = null`.


---

### 🟠 ALTO-3 · Supersets de > 4 miembros por voz/UI en vivo: truncamiento silencioso a 4 con feedback engañoso

**(Preguntas 2 y 6).** El cap de 4 es coherente entre dominio y editor (`SupersetRules.MaxSupersetMembers = 4`, `SupersetRules.kt:11`; recorte en `createSuperset` `SupersetRules.kt:90`; creator sheet del editor `components/sheets/SupersetCreatorAndManagerSheets.kt:122` — `clickable(enabled = selected || draft.exerciseIds.size < 4)`; vivo `addCatalogExerciseToLiveSuperset` `WorkoutViewModel.kt:1782`). Pero la hoja de creación en vivo **solo exige ≥ 2** seleccionados (`screens/workout/WorkoutStructureSheetsHost.kt:673`) y el mensaje de confirmación dice *"Superserie creada con X, Y, Z…"* incluyendo **todos** los elementos seleccionados (`screens/workout/WorkoutVoiceCommandHandler.kt:556-559`), aunque el dominio descarte los que sobren (`createSuperset` hace `take(MaxSupersetMembers)`, `SupersetRules.kt:90`). Los ejercicios descartados quedan en su sitio pero sin grupo: divergencia entre lo hablado/mostrado y lo creado.

### 🟠 ALTO-4 · Disolver (en cualquier lado) sobrescribe `restTime` del ejercicio con `restAfterSuperset`

`SupersetRules.dissolve` (`SupersetRules.kt:348-366`, claves `:350` y `:358`) copia el descanso post-superserie como `restTime` individual al disolver, destruyendo el descanso original del ejercicio. Es consistente editor↔vivo (dominio compartido) y está codificado como comportamiento esperado en test (`app/src/test/.../SupersetRulesTest.kt:172-177`), pero es destructivo e irreversible: enlazar→disolver no es idempotente (se pierde el `restTime` previo). Usado por el editor (`SessionEditorViewModelSupersets.kt:276-278`) y por el vivo (`WorkoutViewModel.kt:1824-1832`).

### 🟡 MEDIO-5 · Clasificación de descansos INTRA vs ROUND en rondas asimétricas + descanso "post-ronda" tras la última ronda

**(Pregunta 4).** La base es consistente (ver "Q4" abajo), pero:

- `sameSupersetRound` exige `nextStepForRest.supersetRoundIndex == targetSetIdx` **y** `nextStepForRest.exerciseId != exercise.id` (`screens/workout/WorkoutSetRecorder.kt:441-444`). Con sets desiguales por miembro, cuando el siguiente paso incompleto no es "otro miembro, misma ronda", se clasifica `SUPERSET_ROUND` y se aplica el descanso post-ronda en medio del bloque (p. ej. cuando el miembro con menos sets agota su parte y el siguiente paso es otro miembro en una ronda posterior). Coherente con la construcción de pasos, pero distinto de lo que sugiere la UI del editor ("post-ronda" al cierre de cada ronda del carrusel, `components/SetEditorUnilateralAndSuperset.kt:194-233` — "Descanso: X / post-ronda: Y" en `:229`).
- El último set de la última ronda se marca `SUPERSET_ROUND` (`WorkoutStepRules.kt:122-130`: `isLastMemberWithSet` incluye la ronda final) → tras acabar la superserie se inicia el descanso "post-superserie" antes del siguiente ejercicio, en vez del descanso estándar de este. Posible "descanso doble" respecto a la expectativa del usuario.
- Superserie degenerada por skip: si se omite un miembro, `visibleExercises` lo filtra (`WorkoutViewModel.kt:1510-1516`) y el grupo queda con 1 miembro visible; los pasos se siguen emitiendo como superserie de 1 (`WorkoutStepRules.kt:94-97`, `:119-134`), con descansos INTRA/ROUND de superserie aplicando a un solo ejercicio. El dominio nunca permite grupos persistidos de 1 (disuelve en `SupersetRules.kt:91`, `:28-31`, `:335-339`; editor igual en `SessionEditorViewModelSupersets.kt:71-75`), así que el caso solo surge vía skips temporales.

### 🟡 MEDIO-6 · `moveSupersetGroupToIndex` (editor) no tiene efecto en la sesión en vivo

`SessionEditorViewModelSupersets.kt:292-301` reordena la lista `session.supersetGroups`, pero el orden de ejecución en vivo lo define exclusivamente la posición de los ejercicios miembros en `visibleExercises` (`WorkoutStepRules.kt:37-46`: cada grupo se emite donde aparece su primer miembro). Reorden "cosmético" salvo para listados que lean la lista de grupos. El reorden real del bloque es `moveSupersetGroupToPart` → `SupersetRules.moveGroup` (`SupersetRules.kt:368-399`), que sí mueve los miembros; test `SupersetRulesTest.kt:223-247` ("moveGroup_movesSupersetAsSingleVisualBlock").

### 🟡 MEDIO-7 · `moveExercise` en vivo (y mover libre en editor) puede separar visualmente un miembro de su grupo

`WorkoutViewModel.moveExercise` (`WorkoutViewModel.kt:1851-1869`) → `WorkoutStructuralEditor.moveExerciseById` (`domain/workout/WorkoutStructuralEditor.kt:45-71`) mueve un miembro dentro de su parte sin actualizar `visualPlacement` ni el resto de miembros. Funcionalmente OK (los pasos se agrupan de nuevo donde cae el primer miembro visible, al ser construcción derivada), pero la UI del editor re-renderiza el bracket por posición (`screens/sessioneditor/SessionListItems.kt`, `screens/sessioneditor/SessionEditorScrollRenderer.kt:226-283`), pudiendo mostrar el grupo dividido si otro ejercicio queda intercalado. El editor tiene el mismo riesgo con `moveExerciseFreely` (`SessionEditorViewModelSupersets.kt:324-353`).

### 🟢 BAJO-8 · Divergencia de defaults de descanso según la ruta de creación

- `linkExerciseWithNext`: 60/120 hardcodeados (`SessionEditorViewModelSupersets.kt:22-23`), ignorando `ruleDefaults.supersetBetweenRestSeconds` / `supersetRoundRestSeconds` que sí usa `openSupersetCreator` (`SessionEditorViewModelSupersets.kt:117-121`).
- Creación en vivo (voz o UI): 60/120 hardcodeados (`WorkoutViewModel.kt:466` y `:1752`), sin consultar `ruleDefaults` (concepto del editor) ni el descanso propio de los ejercicios.
- Fallback legacy: 60/120 del primer miembro (`data/models/Session.kt:45-58`).


---

## RESPUESTAS A LAS PREGUNTAS (con evidencia)

### Q1 — ¿`supersetGroupId`/rondas/descansos del editor se traducen correctamente al plan de pasos del vivo? ¿Quién construye la secuencia?

**Quién construye:** la secuencia la deriva `WorkoutStepRules.buildSteps` (`screens/workout/WorkoutStepRules.kt:33-49`), siempre calculada en caliente desde `WorkoutStepNavigator.workoutStepPositions` (`WorkoutStepNavigator.kt:89-96`), alimentada por `visibleExercises` (`WorkoutViewModel.kt:1510-1516`: ejercicios de la variante activa menos `skippedExerciseIds`). La hidratación normaliza antes: `WorkoutSessionHydrator.kt:118-120` (`(resumedState?.session ?: session).let(ports::normalizeSupersetsForWorkout)` → `WorkoutViewModel.kt:271`, `:510`, normalización de `:1109-1113` incluyendo `sessionB/C/D`).

**Traducción:**

- **`supersetGroupId`:** los ejercicios llevan dual-write `supersetGroupRef` + `supersetId` (normalización `SupersetRules.kt:64-69`); el lector canónico es `supersetGroupRefOrLegacyId()` (`data/models/Session.kt:449-450`) usado en ambos lados. En pasos: `WorkoutStep.kt:26-27` (`domain models` en `screens/workout/WorkoutStepRules.kt:17-30`). ✔ correcta (salvo CRÍTICO-1 para `isOptional`).
- **Orden/rondas:** `SupersetRules.orderedMembers` (`SupersetRules.kt:195-203`) respeta `exerciseOrder`; `roundCount` (:205-210) traduce `rounds` al plan (`WorkoutStepRules.kt:118`). ✔ correcta con la excepción CRÍTICO-2.
- **Descansos:** llegan con prioridad por ronda → grupo → ejercicio → base (`WorkoutSetRecorder.kt:451-464`); el orquestador marca contexto de voz por tipo (`screens/workout/WorkoutRestTimerOrchestrator.kt:111-112`). ✔ correcta (caveats MEDIO-5).

### Q2 — Supersets de 3+ ejercicios, rondas parciales, sets distintos por miembro: ¿soporte igual en ambos lados?

**Sí en estructura, con caveats:**

- **3-4 miembros:** cap 4 coherente en dominio (`SupersetRules.kt:11`), editor (`SupersetCreatorAndManagerSheets.kt:122`) y vivo (`WorkoutViewModel.kt:1782`). Caveat ALTO-3 (selección > 4 en voz/UI en vivo trunca en silencio). Tests: `SupersetRulesTest.normalizeSession_capsLegacyGroupsAtFourMembers` (`SupersetRulesTest.kt:204-220`).
- **Orden de miembros configurable:** editor `updateSupersetOrder` (`SessionEditorViewModelSupersets.kt:239-245`) reordena `exerciseOrder`; el vivo lo respeta vía `orderedMembers` (`WorkoutStepRules.kt:95`). Test: `SupersetRulesTest.nextTarget_respectsConfiguredOrderAndUnevenSetCounts` (`SupersetRulesTest.kt:45-66`, superserie de 3 con A=2, B=1, C=3 sets y orden C,A,B).
- **Sets distintos por miembro (rondas parciales):** soportado por construcción en vivo: `if (roundIdx !in exercise.sets.indices) return@forEachIndexed` (`WorkoutStepRules.kt:121`) e `isLastMemberWithSet` (`:122-124`) que asigna SUPERSET_ROUND al último miembro con set en esa ronda; navegación entre miembros/rondas: `SupersetRules.nextTarget` (:212-248). Editor: permite editar sets por miembro libremente (riesgo CRÍTICO-2). Tests de rest kinds: `WorkoutStepRulesTest.buildSteps_interleavesSupersetRoundsAndRestKinds` (`WorkoutStepRulesTest.kt:89-108`) y unilateral en superserie (`:111-136`).
- **Superset de 1 ejercicio:** imposible por dominio en ambos lados (crear exige ≥2, `SupersetRules.kt:91`; normalizar disuelve <2, `:28-31`; quitar miembro disuelve si quedan ≤1, `:335-339`; editor `removeFromSuperset` disuelve con ≤2, `SessionEditorViewModelSupersets.kt:71-75`). Caso residual: superserie de 1 *visible* por skips (MEDIO-5).

### Q3 — SUPERSETS OPCIONALES: marcado, persistencia y consumo en vivo — ¿desconexión?

**Desconexión confirmada y total** (ver CRÍTICO-1): el editor marca y persiste (`SessionEditorViewModelSupersets.kt:280-286` → `Session.supersetGroups[].isOptional`, `Session.kt:74`; UI `SupersetGroupEditorCard.kt:325-408` con promesa de activación en vivo), el vivo la **ignora por completo** (ningún hit de `isOptional` en `screens/workout/` ni en `domain/workout/`, ni en voz; `buildSteps` no la consulta), y `SupersetRules.normalizeSession` **la borra** al reconstruir grupos (`SupersetRules.kt:33-46`) en hidratación, edición y persistencia en vivo. Los clones la preservan (`SessionTemplateEngine.kt:66-72`, `SessionEditorCloneHelpers.kt:68-82`). Dirección del desync: **el editor guarda y promete; el vivo ignora y el almacén corrompe el flag.**


### Q4 — Descansos entre miembros vs entre rondas: ¿consistente editor ↔ vivo?

**La fórmula es compartida y coherente:**

- **Editor — global:** `updateSupersetRestBetween/After` (`SessionEditorViewModelSupersets.kt:53-59`) y `updateSupersetRest` (:190-198) → `SupersetRules.updateRest` (`SupersetRules.kt:250-294`), que además **regenera** los mapas por ronda 0..roundCount-1 preservando overrides (:262-267) y sincroniza los campos legacy por ejercicio (:269-275).
- **Editor — por ronda:** carrusel `SupersetRoundsCarousel` (`SupersetGroupEditorCard.kt:292-311`) → `updateSupersetRoundRest` (`SessionEditorViewModelSupersets.kt:200-208`) → `SupersetRules.updateRoundRest` (:296-316), persistido en `Session.kt:72-73` (`roundRestBetweenExercises`, `roundRestAfterSuperset`). La fila del editor muestra "Descanso: X / post-ronda: Y" por ronda (`components/SetEditorUnilateralAndSuperset.kt:194-233`, texto en `:228-229`).
- **Vivo:** `WorkoutSetRecorder.kt:451-464`:
  - `SUPERSET_INTRA` → `roundRestBetweenExercises[targetSetIdx] ?: restBetweenExercises ?: supersetRestBetween ?: baseRest` (:453-456).
  - `SUPERSET_ROUND` → `roundRestAfterSuperset[targetSetIdx] ?: restAfterSuperset ?: supersetRestAfter ?: baseRest` (:457-460).
  - El índice `targetSetIdx` (:126) es el set recién registrado, que **coincide con el índice de ronda** porque los pasos de superserie usan `setIndex = roundIdx` (`WorkoutStepRules.kt:127-129`) → clave correcta: intra descansa dentro de la ronda r con clave r; la transición r→r+1 lee el post-ronda con la clave del set completado (r).
  - Clasificación del tipo: `restKind` (:445-450) prioriza BETWEEN_SIDES (lado pendiente unilateral, :430-431, :446), luego SUPERSET_INTRA (`sameSupersetRound`, :441-444), luego SUPERSET_ROUND, luego STANDARD. El orquestador consume el tipo para la voz (`WorkoutRestTimerOrchestrator.kt:111-121`).

**Inconsistencias residuales (MEDIO-5):** clasificación INTRA/ROUND con sets asimétricos, ROUND también tras la última ronda del bloque (`WorkoutStepRules.kt:122-130`), y superserie de 1 visible por skips.

### Q5 — Crear/disolver supersets en vivo (voz/UI): ¿estado e ids coherentes?

**Sí, coherentes:**

- **Crear (UI):** hoja `showWorkoutSupersetCreator` (`WorkoutStructureSheetsHost.kt:612-681`) con ≥2 seleccionados (:673) → `viewModel.createLiveSuperset(ids, partId = anchor)` (:675-676).
- **Crear (voz):** `ConfirmCreateSuperset` → `ports.createLiveSuperset(command.members)` → override (`WorkoutViewModel.kt:465-467`) → `createLiveSuperset(ids, null, 60, 120)` (`:1752-1772`): UUID nuevo, `rounds = null` (rondas derivadas de sets), dominio `SupersetRules.createSuperset`, y `applySessionMutation(...preferredExercise)` (:1771) que re-ancla el paso y persiste al programa (`WorkoutStructuralPersistenceController.kt:309-397`, con guard `WorkoutEditingRules.canPersistLiveStructuralChanges` :390). Feedback hablado (`WorkoutVoiceCommandHandler.kt:551-560`).
- **Añadir miembro de catálogo:** `addCatalogExerciseToLiveSuperset` (`WorkoutViewModel.kt:1774-1816`): cap 4, hereda sets del primer miembro con ids nuevos (:1786-1789), inserta tras el último miembro (`WorkoutStructuralEditor.insertExerciseAfterSupersetMembers` :164-187) y recrea el grupo preservando rests/rounds/ancla (:1803-1812).
- **Disolver (UI/voz):** `dissolveCurrentSuperset` usa el grupo del ejercicio actual (`WorkoutViewModel.kt:468-473`) → `dissolveLiveSuperset` (:1824-1832) → `SupersetRules.dissolve` (:348-366) borra refs y grupo; caveats: sobrescribe `restTime` (ALTO-4) y la re-normalización borra `isOptional` de todos los grupos (CRÍTICO-1).
- **Actualizar rests/rondas:** `updateLiveSupersetRest` (`WorkoutViewModel.kt:1834-1849`).
- **Saltar ronda actual:** `skipCurrentSupersetRound` (`WorkoutStepNavigator.kt:155-190`) marca `skipped = true` los pasos restantes de la ronda — coherente con el modelo de sets completados.
- **Selección/navegación por grupo y ronda:** `selectSupersetGroup`/`selectSupersetRound`/`selectExerciseInSupersetRound` (`WorkoutViewModel.kt:2307-2319` → `WorkoutStepNavigator.kt:431-466, 533-567`).


### Q6 — Bugs concretos: índices, superset de 1 ejercicio, eliminar/reordenar miembros, desync de rounds editor↔vivo

- **Índices de descanso por ronda:** correctos. Re-indexado al borrar ronda (`SessionEditorViewModelSupersets.kt:221-227`), regeneración completa al cambiar `rounds` (`SupersetRules.kt:262-267`), mapas 0..rounds-1 al crear con draft (`SupersetRules.kt:105-110`). Las escrituras de `rounds` por texto libre pasan por `updateRest`, que sí regenera mapas — no deja huérfanos, pero sí el desync rounds↔sets (CRÍTICO-2).
- **Superset de 1 ejercicio:** imposible persistentemente (ver Q2). Caso residual por skips (MEDIO-5).
- **Eliminar miembro:** disuelve si quedan ≤1/≤2 en dominio y editor (`SupersetRules.kt:335-339`, `SessionEditorViewModelSupersets.kt:71-75`; test `SupersetRulesTest.removeExercise_dissolvesGroupWhenOnlyOneMemberWouldRemain`). El manager sheet del editor cierra el sheet tras quitar un miembro (`SupersetCreatorAndManagerSheets.kt:381-386`).
- **Reordenar:** `updateSupersetOrder` afecta al vivo ✔ (`SessionEditorViewModelSupersets.kt:239-245`); `moveSupersetGroupToIndex` cosmético (MEDIO-6); `moveExercise`/`moveExerciseFreely` pueden desarmar el bracket visual (MEDIO-7).
- **Desync rounds editor↔vivo:** CRÍTICO-2 — editable por texto libre en ambos lados sin ajustar sets; severidad amplificada por el denominador de progreso (`WorkoutSetRecorder.kt:491`).

---

## MATRIZ RÁPIDA

| # | Severidad | Hallazgo | Evidencia clave |
|---|---|---|---|
| 1 | 🔴 CRÍTICO | `isOptional` ignorado en vivo + borrado por `normalizeSession` | Session.kt:74; SupersetGroupEditorCard.kt:381-384; SupersetRules.kt:33-46; WorkoutSessionHydrator.kt:119; 0 hits en screens/workout |
| 2 | 🔴 CRÍTICO | `rounds < sets` → pasos inexistentes + progreso < 100 % | WorkoutStepRules.kt:118-134; SupersetRules.kt:205-210; WorkoutSetRecorder.kt:491; WorkoutStructureSheetsHost.kt:332-377; SessionEditorScrollRenderer.kt:694-698 |
| 3 | 🟠 ALTO | >4 miembros por voz/UI en vivo: trunca a 4 y lo anuncia completo | SupersetRules.kt:90; WorkoutStructureSheetsHost.kt:673; WorkoutVoiceCommandHandler.kt:556-559 |
| 4 | 🟠 ALTO | `dissolve` destruye `restTime` original (restAfter→restTime) | SupersetRules.kt:350,358; SupersetRulesTest.kt:172-177 |
| 5 | 🟡 MEDIO | Clasificación INTRA/ROUND asimétrica; ROUND tras última ronda | WorkoutSetRecorder.kt:441-449; WorkoutStepRules.kt:122-130 |
| 6 | 🟡 MEDIO | `moveSupersetGroupToIndex` sin efecto en vivo | SessionEditorViewModelSupersets.kt:292-301 vs WorkoutStepRules.kt:37-46 |
| 7 | 🟡 MEDIO | `moveExercise`/`moveExerciseFreely` desarman el bracket | WorkoutViewModel.kt:1851-1869; WorkoutStructuralEditor.kt:45-71; SessionEditorViewModelSupersets.kt:324-353 |
| 8 | 🟢 BAJO | Defaults de descanso divergentes por ruta de creación | SessionEditorViewModelSupersets.kt:22-23 vs :117-121; WorkoutViewModel.kt:466 |

---

## RECOMENDACIONES MÍNIMAS

1. Propagar `isOptional` en `SupersetRules.normalizeSession` (`SupersetRules.kt:33-46`) y decidir su semántica en vivo (activación bajo demanda / ejercicios separados) o retirar la UI del editor; añadir test de preservación del flag.
2. Hacer toda escritura de `rounds` transaccional con los sets (añadir/quitar sets de miembros en `updateRest` al variar `rounds`, o validar `rounds <= max sets`), y cubrir con tests `rounds < sets` + `buildSteps`.
3. En creación en vivo, limitar la selección a 4 (UI y feedback de voz), como ya hace el editor.
4. Al disolver, conservar el `restTime` original del ejercicio (o al menos no sobrescribirlo si ya tenía valor propio), o documentar el comportamiento en la UI.

---

**Colofón:** el diseño de pasos derivados y motor compartido es sólido; los defectos hallados se concentran en metadatos no propagados (`isOptional`) y en la no transaccionalidad de `rounds` respecto a los sets. Auditoría de solo lectura: no se modificó ningún archivo de código.

