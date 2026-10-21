# ER0 · Editor de sesiones: vuelve el ORIGINAL de la PR11 (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- el MAPA del original, `C:\kw\analysis\maps\session_editor_original_map.md`, entero;
- como modelo, `docs/screens-v1/briefs/LR0.md`: hicimos lo mismo con la sesión en vivo.

**Por qué.** El usuario rechazó el editor rediseñado (SE0–SE8). Desaparecían o quedaban escondidas cosas importantes: los grupos, los
controles de serie y de descanso, y el acceso a la aproximación, la movilidad y las superseries. Eligió «original + refinar». Este
paquete SOLO restaura y porta los arreglos de lógica. El refinado irá después, en otros paquetes y con su visto bueno.

El rediseño queda archivado en la etiqueta `archive/session-editor-redesign-v1`. No la borres ni la cambies.

## Qué haces

### 1. Restaura la superficie de edición de la PR11 (`8f0afc9dc`)
Con `git checkout 8f0afc9dc -- <rutas>`, bajo `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/`:
- todos los archivos de la raíz del paquete, MENOS `CatalogContracts.kt` y `CatalogSelectionWizard.kt` (son del catálogo: ver
  abajo);
- `components/`, MENOS las piezas del catálogo:
  - `ExerciseCatalogScreen.kt`, `ExercisePickerV2Catalog.kt`, `ExercisePickerSheet.kt`, `ExercisePickerCards.kt`;
  - `ExercisePickerCatalogLogic.kt`, `ExerciseAspectChips.kt`, `ExerciseCatalogInfoDialog.kt`, `ExerciseCatalogV2Info.kt`;
  - `ExerciseCatalogV2Labels.kt` y `CardioCatalogSheet.kt`;
- `components/sheets/`, entero.

**El CATÁLOGO NO se restaura.** Se queda como está hoy, porque al usuario le gusta y otro paquete (CF1) está refinando la ficha.
Son los archivos de catálogo de arriba, más `CatalogContracts.kt`, `CatalogSelectionWizard.kt` y `design/Catalog*.kt`.
- Si el editor restaurado no compila contra el catálogo de hoy, adapta LO JUSTO en el lado del EDITOR (la llamada), no en el
  catálogo.

**Lo que se creó después de la PR11 dentro de `screens/sessioneditor/`:**
- `git checkout` NO borra archivos. Lista lo añadido con
  `git diff --name-only --diff-filter=A 8f0afc9dc HEAD -- android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor android-native/app/src/test/java/com/example/kpkn/screens/sessioneditor android-native/app/src/test/java/com/example/kpkn/render`.
- Bórralo con `git rm`, SALVO:
  - lo del catálogo (`design/Catalog*.kt` y sus pruebas);
  - los ayudantes de lógica que se portan en el punto 2: `SessionEditorCatalogGateMessage.kt` (SE10),
    `SessionEditorEditableNumber.kt` (SE11), `SessionEditorDuration.kt` (SE12) y sus pruebas;
  - cualquier archivo que use otra pantalla (compílalo y lo verás). Si lo usa la sesión en vivo, Program Detail o Inicio, se queda.

**Pruebas:**
- las de `src/test/.../screens/sessioneditor/` vuelven a las de la PR11 (`git checkout 8f0afc9dc -- …`), más las de los arreglos
  portados;
- las hojas de render del rediseño (`render/SessionEditor*`, `render/SessionPart*`…), fuera;
- las del catálogo (`CatalogDetailRenderSheetTest`, `CatalogListRenderSheetTest`, `CatalogDetailContentTest`…), se quedan.

### 2. Porta los arreglos de LÓGICA que salieron después y valen para el original
Revisa
`git log --no-merges --format='%h %s' 8f0afc9dc..HEAD -- android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor android-native/app/src/main/java/com/example/kpkn/screens/home android-native/app/src/main/java/com/example/kpkn/screens/programdetail`.
Como mínimo:
- **SE10, el guardado:**
  - `40ba298e6`: el cardio guarda `custom:cardio-<tipo>`;
  - `7509b4d25`: reemplazar guarda el `occurrenceId`;
  - `a76d3c90c`: el bloqueo del catálogo se explica con el nombre del ejercicio;
  - `92f6d275a`: el guardado cura el cardio con id viejo.
  El original tenía los mismos fallos (lo dice el mapa, §3). Porta cada uno sobre `SessionEditorViewModelCardio.kt`,
  `SessionEditorViewModelStructure.kt` y `SessionEditorViewModelNavigation.kt` de la PR11, con sus pruebas.
- **SE11:**
  - la normalización sin pérdida: `normalizeSet`, rama RM, ya no borra el rango ni el RIR;
  - los campos de número que no se pisan (`SessionEditorEditableNumber`): aplícalo a los campos del ORIGINAL con el mismo patrón,
    es decir, `ExerciseGoalDialog`, los de `SupersetRoundsAndConfig` y la carga inteligente;
  - las series por defecto de un ejercicio añadido, en `createExerciseFromInfo`.
  Con sus pruebas, adaptadas a las funciones del original.
- **SE12, una sola duración:** el editor original calcula con `calculateSessionTimeBreakdown`. Tiene que dar lo mismo que Inicio y
  Program Detail (`SessionDurationEstimator`), como dejó SE12; conecta el original a la misma fuente. Con su prueba de que coinciden.
- **SE8 y SE9:** busca en sus `fix(` lo que sea LÓGICA y valga para el original (formatos de descanso como Program Detail, «72,5 %
  1RM», sesión vacía sin «≈ 1 min»…). Lo de aspecto NO se porta: lo hará el refinado.

Un commit por arreglo portado. Cada uno con una prueba que falle sin él, o la prueba original adaptada.

### 3. Documentación
Arriba de `docs/screens-v1/SESSION_EDITOR_V1.md`, una nota: «2026-10-15: el usuario rechazó el rediseño del editor y pidió volver al
de la PR11 y refinarlo. Esta especificación queda archivada con el código en la etiqueta `archive/session-editor-redesign-v1`. El
refinado irá en `SESSION_EDITOR_R1.md`».

### 4. El `.dbg` para la prueba en el teléfono (la hago yo)
Al final: `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native -I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug > /c/kw/logs/screens-er0-dbg-<n>.log 2>&1`.
Copia el APK a `C:/kw/shots/er0/app-er0-dbg.apk` y avísame con SendMessage a "main".

## Pruebas
- **En verde:**
  - `com.example.kpkn.screens.sessioneditor.*`, `com.example.kpkn.screens.programdetail.*` y `com.example.kpkn.screens.home.*`;
  - `com.example.kpkn.screens.workout.*`, por las piezas compartidas;
  - `*CatalogDetail*` y `*CatalogList*`;
  - `com.example.kpkn.data.exercises.*`;
  - la compilación de `androidTest`.
- Si una prueba de la PR11 falla con el original restaurado, mira si ya fallaba en la PR11 y dilo. No la «arregles» cambiando la
  conducta.

## Comando
`KPKN_SLOT_PRIORITY=1 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "com.example.kpkn.screens.programdetail.*" --tests "com.example.kpkn.screens.home.*" --tests "com.example.kpkn.screens.workout.*" --tests "*CatalogDetail*" --tests "*CatalogList*" --tests "com.example.kpkn.data.exercises.*" > /c/kw/logs/screens-er0-<n>.log 2>&1`

- Las corridas intermedias pueden ir sin `:app:compileBaseDebugAndroidTestKotlin` y con menos filtros.
- Tope: 5 corridas con pruebas.

## Commit
- `revert(sessioneditor): vuelve el editor de la PR11 (el rediseño queda en archive/session-editor-redesign-v1) (ER0)`.
- Un commit por cada arreglo portado: `fix(sessioneditor): … (ER0)`.
- `docs(sessioneditor): la especificación del rediseño queda archivada (ER0)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Cambiar el aspecto o la conducta del editor original: eso es el refinado.
- El catálogo, salvo lo justo en la llamada.
- `screens/workout/`, salvo lo justo para compilar con las piezas compartidas.
- `MainActivity.kt`, salvo lo justo para compilar.
- El teléfono, el emulador y push.

## Informe
- Qué restauraste y qué se quedó como hoy (el catálogo y los ayudantes), y por qué.
- Los arreglos portados (commit de origen → commit nuevo, con su prueba).
- Pruebas: número y resultado.
- La ruta del `.dbg`.
