# SE3b · Editor de sesiones: el ejercicio desplegado (tabla de series y opciones) (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con SE0, SE1, SE2 y SE3a integrados; el mensaje dice
si ya están SE4 y SE7). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md`, §0, §2 (punto 6, «desplegada»), §3, §8 y §9: TU especificación;
- el mapa `C:\kw\analysis\maps\sessioneditor.md`, §1.4, §3, §6 y §7 («Acoplamiento»). Las líneas son de ANTES de SE0, SE2 y SE3a:
  búscalas por nombre;
- las piezas de SE1 (`ui/design/KpknSetTable.kt`, `KpknSetColumns.kt`, `KpknSetFormat.kt` y `KpknNumberCell.kt`) y lo que dejó SE3a
  en `screens/sessioneditor/design/` (módulo de parte, fila de ejercicio con su dosis y cómo despliega).

SE3a dejó cada ejercicio en una fila de una línea. Al desplegarla hoy se ve el contenido viejo de `ExerciseEditorCard`. Este paquete
hace ese desplegado: la tabla de series y las opciones del ejercicio. La lógica NO cambia: mismas llamadas al VM para editar, añadir,
duplicar y quitar series, y para cada opción.

## Qué haces
1. **El ejercicio desplegado** (`screens/sessioneditor/design/SessionExerciseExpanded.kt` y piezas, sin estado), debajo de su fila
   de SE3a, dentro del módulo de la parte:
   - **la tabla:** `KpknSetTable` con las columnas del modo del ejercicio (`KpknSetColumns.rir`, `rpe`, `percent`, `time`…):
     - una función pura `toKpknSetRows(…)` (`SessionExerciseSetRows.kt`, con su prueba) que traduce las series del ejercicio a
       `KpknSetRow`: «1, 2, 3…» las de trabajo, «A» las de aproximación y «D» los drops; los rangos «6–8»; sin dato, «–»;
     - cada celda edita con las MISMAS funciones del VM que hoy usa `InlineSetRow` (peso, repeticiones, RIR o RPE, %, tiempo);
     - la celda de descanso abre el selector de descanso de hoy (`KpknRestPickerChain`);
     - la pulsación larga en el número abre un `KpknContextMenu` con «Tipo de serie» (Trabajo, Aproximación o Drop), «Duplicar» y
       «Eliminar» (en `danger`). Hacen lo mismo que hoy;
     - al pie, «Añadir serie» con el «+» DELANTE (decisión 96). Si SE3a dejó en `KpknTextAction` una forma de poner el icono delante,
       úsala desde `KpknSetTable`; si no, añade a `KpknTextAction` un parámetro opcional al final (`leadingIcon`) sin cambiar lo
       demás;
   - **las opciones:** una fila de `KpknChip` debajo de la tabla (en `FlowRow` si no caben):
     - «Unilateral»: abre un `KpknSegmentedControl` con «Ambos lados · Alterno · Por lados»;
     - la intensidad: un `KpknSegmentedControl` compacto «RIR · RPE · %» que cambia el modo y con él las columnas;
     - «Carga inteligente»: su `KpknSwitch` en una `KpknSheet` con el texto de hoy (sustituye al diálogo de
       `ExerciseEditorDialogs`);
     - «Técnica»: drop set o rest-pause, con la hoja de hoy (`InlineSetRowTechniqueChips` por dentro, tal cual);
     - «Nota»: abre la nota de hoy;
     - «Cambiar ejercicio»: el reemplazo de hoy;
     - cada chip activo (unilateral, carga inteligente, técnica o nota con texto) se ve seleccionado.
   - **Cardio, movilidad y calentamiento** dentro de un ejercicio: siguen con su contenido de hoy (son de SE5).
   - **Superseries:** si las series de un miembro son sus rondas, la tabla muestra una fila por ronda. Si no encaja sin tocar la
     lógica, deja el contenido de hoy para ese caso y anótalo.
2. **Lo que queda sin uso** (`InlineSetRow`, `AccentSetValueField`, `SetEditorCard`, el interior de `ExerciseEditorCard` o la tarjeta
   entera…): bórralo con Grep en todo `app/src`.
   - `ExerciseSetsCarousel`, `CompactModeSelector`, `UnilateralModeSelector`, `SideOrderChip`, `RestPausePlanDefaults` y
     `DropSetPlanDefaults` los usa `screens/workout/`: se quedan con su firma.
3. **Movimiento:** desplegar y plegar con `animateContentSize` (220 ms); con `wizardReducedMotion()`, sin animación.
4. **Textos:** «Reps» solo como cabecera de columna. En lo demás, «repeticiones». Nada de «Defaults», emojis ni «...» en ASCII.

## Pruebas
- **Siguen en verde:** las 33 clases de `src/test/.../screens/sessioneditor/`, sobre todo:
  - `SessionEditorManualOverrideTest` (30);
  - `SessionEditorDraftPersistenceTest` (28);
  - `AuthoredTmLoadConsumerContractTest` (13).
  - También las de `screens/workout/` que usan piezas del editor (`*Workout*`), las de SE1 (`*KpknSetTable*`, `*KpknNumberCell*`),
    las de SE3a y la compilación de `androidTest`.
- **Unitarias** `SessionExerciseSetRowsTest`:
  - trabajo, aproximación y drop con su etiqueta;
  - rangos y celdas vacías;
  - columnas por modo (RIR, RPE, % y tiempo).
- **Robolectric** `SessionExerciseExpandedTest`:
  - editar una celda de kg llama con el valor;
  - «Añadir serie» llama a añadir;
  - el menú del número llama a duplicar y a eliminar;
  - cambiar «RIR · RPE · %» llama con el modo;
  - «Cambiar ejercicio» llama al reemplazo.
- **Hoja de render** `render/SessionExerciseExpandedRenderSheetTest`, con las piezas montadas con datos de ejemplo:
  - un ejercicio desplegado en RIR con 2 de aproximación y 3 de trabajo;
  - el mismo en RPE, en % y uno por tiempo;
  - uno con drop y técnica activa;
  - la fila de opciones con chips activos;
  - el menú del número abierto;
  - una parte de SE3a con un ejercicio desplegado entre dos plegados;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/se3b`.
  - Mira cada PNG con Read y critica con el listón del usuario: tiene que leerse mejor que las tablas de Hevy y editarse más rápido
    que MacroFactor Workouts. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/se3b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*Workout*" --tests "*KpknSetTable*" --tests "*KpknNumberCell*" --tests "*SessionExerciseSetRows*" --tests "*SessionExerciseExpanded*" > /c/kw/logs/screens-se3b-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(sessioneditor): ejercicio desplegado con la tabla de series y sus opciones en piezas KPKN (SE3b)`.
- Si borras piezas viejas, aparte: `refactor(sessioneditor): quita la fila de serie y la tarjeta de ejercicio viejas (SE3b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lista plegada, las partes y el arrastre (SE3a, ya integrado: solo enganchas el desplegado), el impacto y el asistente (SE4),
  el cardio y la preparación por dentro (SE5), el catálogo (SE6) y las hojas de SE7.
- `SessionEditorDragController`, `SessionEditorListGeometry` y la lógica del VM. Lo que falte, AL FINAL del VM.
- Las firmas de las piezas que usa `screens/workout/`.
- `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Pruebas: número y resultado.
- PNG y crítica.
