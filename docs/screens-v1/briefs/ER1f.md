# ER1f · Editor original refinado: integración y remates (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con ER0, ER1a, ER1b, ER1c1, ER1c2, ER1d y ER1e
integrados). Lee:
- `docs/screens-v1/SESSION_EDITOR_R1.md`;
- `docs/screens-v1/briefs/LR1_COMUN.md`;
- `docs/wizard-v3/BRIEF_COMUN.md`.

Los paquetes del refinado dejaron cabos sueltos fuera de sus archivos. Este paquete los ata. **Regla de oro:** no cambia el diseño,
solo conecta y remata lo que ya está.

## Qué haces

### 1. Conexiones
1. **«Duplicar» de las acciones rápidas** no hace nada: en `components/sheets/SessionEditorSheets.kt`, bloque `QUICK_ACTIONS`,
   pasa `onDuplicate`, conectado a `viewModel::duplicateQuickActionExercise` (`SessionEditorViewModelQuickActions.kt`, ER1c2) desde
   `SessionEditorScreen.kt`.
2. **«Crear superserie» sale sin grupos:** en el bloque `SUPERSET_CREATOR`, pasa `sessionParts = session.parts` a
   `SupersetMemberPickerSheet`.
3. **«Ficha» de las acciones rápidas** cierra la hoja y no hace nada: `MainActivity` pasa `onOpenExerciseDetail = {}`. Ábrela DENTRO
   del editor con la ficha del catálogo (`components/ExerciseCatalogInfoDialog.kt`, la de CF1: implementos sobre la foto y músculos
   a la vista), con el modelo construido como lo hace el catálogo (`ExercisePickerV2Catalog`). Sin «Añadir/Quitar» o con «Cambiar»,
   lo que ya admita la pieza. No toques `MainActivity.kt`.
4. **«Eliminar» de las acciones rápidas** pide confirmación porque no había «Deshacer». ER1b dejó un borrado blando con «Deshacer»
   (`pendingExerciseRemoval`): úsalo también aquí y quita el diálogo.
5. **El reloj de tiempo:** los umbrales (85 % y 108 %) están dos veces (`frame/SessionEditorFrameBottomBar.kt` de ER1a y
   `RulesTimeTab.rulesClockStatusOf` de ER1e). Una sola función, usada por los dos.
6. **La tabla de §7 de `SESSION_EDITOR_R1.md`:** `ClonerSaveWarmupSheets.kt` lo hizo ER1a, no ER1e. Corrígela.

### 2. Textos y formatos
- «Warmup» → «Calentamiento» (en la pestaña Tiempo).
- Los chips de duración «30m/45m/60m/90m» → «30 min», «45 min»…
- Busca en `screens/sessioneditor/` otros textos en inglés o con formatos sueltos: cifra pegada a «m» o «s», «Gap», «Prep» sin
  sentido, punto decimal, «x» en vez de «×». Corrígelos con `KpknSetFormat`.
- Añade una prueba que recorra los textos visibles del editor en la hoja de render y falle con esos patrones, como hizo LR1g con el
  relator.

### 3. Lógica
- **`applyDefaults`** (`SessionEditorRulesEngine.kt`) pone `targetPercentageRM` a nulo y `normalizeSet` (RM) lo fija a 75: aplicar
  reglas a un ejercicio RM reinicia su %RM. Aplicar reglas solo cambia lo que la regla fija; el %RM de un RM se conserva. Con prueba
  que falle sin el arreglo.
- **La prueba desactivada** `SessionListBehaviorTest.borrarDesdeLaCabeceraAbreSuDialogo` (ER1b): averigua si el diálogo de borrar
  grupo no se abre (fallo real) o si es la prueba. Arréglalo y quita el `@Ignore`.

### 4. Pieles que faltaron
- Los diálogos de gestión de plantillas (Editar, Borrar, Guardar sesión) y los desplegables de filtro de `TemplateCatalogBrowser`
  siguen en Material: a `KpknAlertDialog`, `KpknTextField` y `KpknContextMenu`.
- Las filas de chips que se desplazan (en Reglas y en Tiempo) cortan en seco en el borde: añade el fundido de borde que ya usa la
  cabecera de la sesión en vivo (`WorkoutHeaderBar`, LR1a2).
- La configuración de un miembro de superserie (`SupersetExerciseConfigOverlay`) usa controles compartidos que se quedaron con la piel
  vieja («Reps ▾», la estrella, «Bilateral»). Que use las mismas piezas que el ejercicio desplegado de ER1c1 (`ExerciseOptionsBar`).
- «+ Añadir ejercicio a <grupo>» queda pegado al borde del módulo: alinéalo con el contenido (16 dp).
- Las rondas de superserie (`SupersetRoundsAndConfig`, ER1c2) pintan las series de cada miembro con la presentación «Card» de
  `InlineSetRow`, la vieja. Pásalas a la presentación «List» que hizo ER1c1, la misma del ejercicio desplegado.
- La píldora punteada «+ Aproximación» de la preparación vacía (`PreparationDashedPill`, `PreparationCarouselCards.kt`) roza su borde
  inferior al 130 %: `padding(vertical = 6.dp)`.
- Decidido: las cifras editables van en la tipografía de `KpknNumberCell` (Inter SemiBold tabular). Syne se queda para las cifras
  grandes que solo se leen.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.sessioneditor.*` y `com.example.kpkn.screens.workout.*`.
- **Nuevas:** una por conexión (1.1 a 1.4), la de textos, la de `applyDefaults` y la reactivada de ER1b.
- **Hoja de render:** escenas nuevas en las hojas que ya existen (no una hoja nueva): la ficha abierta desde el editor, crear
  superserie con grupos, la configuración de un miembro y los diálogos de plantillas.

## Permitido y prohibido
- **Permitido:** `screens/sessioneditor/` entero (los otros paquetes ya terminaron) y `docs/screens-v1/SESSION_EDITOR_R1.md` (solo la
  tabla).
- **Prohibido:**
  - el catálogo, salvo leerlo para construir el modelo de la ficha;
  - `MainActivity.kt`;
  - `screens/workout/`;
  - `ui/design` y `ui/components`, salvo usarlos;
  - push;
  - el teléfono y el emulador.

## Comando
`KPKN_SLOT_PRIORITY=1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "com.example.kpkn.screens.workout.*" > /c/kw/logs/screens-er1f-<n>.log 2>&1`

- Mira los PNG antes de lanzar otra corrida. Tope: 5 corridas.
- `.dbg` al final, con prioridad: `KPKN_SLOT_PRIORITY=1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native -I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/screens-er1f-dbg-<n>.log 2>&1`.
  Cópialo a `C:/kw/shots/er1f/app-er1f-dbg.apk` y avísame con SendMessage a "main".

## Commit
- `fix(sessioneditor): duplicar, crear superserie con grupos, la ficha y eliminar con Deshacer desde las acciones rápidas (ER1f)`.
- `fix(sessioneditor): un solo reloj de tiempo y textos del editor en español de producto (ER1f)`.
- `fix(sessioneditor): aplicar reglas conserva el %RM (ER1f)`.
- `fix(sessioneditor): el diálogo de borrar grupo (ER1f)`.
- `style(sessioneditor): plantillas, filas de chips y la configuración de miembros con la piel nueva (ER1f)`.
- `docs(sessioneditor): la tabla de paquetes de R1 (ER1f)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Por punto: qué cambió, dónde y su prueba.
- Los PNG.
- Pruebas: número y resultado.
- La ruta del `.dbg`.
