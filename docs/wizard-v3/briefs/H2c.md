# H2c · Importador: avisos uno a uno, con su acción (2026-10-11)

Worktree `C:\kw\u1`, rama `v3/h2c` (sale de `feat/wizard-v3`, que ya trae H2a y H2b). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/H1.md`, `H2a.md`, `H2b.md` y `H3c.md` (el aviso `SHEET_NOTE`).
Tareas Gradle `...Base...`. Español neutro, sin género gramatical. Mismo vidrio y componentes del paquete (`ImportTextAction`, `ImportSegmented`, `importGlassPanel`, los selectores y `ImportAddExercisePanel`).

## Objetivo
Hoy los avisos son una lista de textos («2 filas no se entendieron · Ver las filas»), y la revisión final solo deja «Ignorar todos». La persona debe poder **resolver cada aviso**:
- añadir como ejercicio una fila que no se entendió;
- ir al ejercicio al que se le descartó un valor;
- o darlo por visto.

Así nada del archivo se pierde en silencio.

## 1. Dominio y operación
- **`ImportDraftEditor.addFromWarning`** gana `toDayId: String? = null`:
  - si `toDayId` existe, el ejercicio va al final de ese día;
  - si no, usa el `dayId` del aviso;
  - si tampoco existe, crea un día al final de la semana del aviso (o de la semana 1) llamado **«Día <n+1>»**, donde n es el número de días que ya tiene esa semana. Hoy se llama siempre «Día 1».
- **`ImportEditOp.AddFromWarning(warningId, toDayId: String?, rawName, sets, repsMin, repsMax, configId: String?, decided: Boolean)`:**
  - la decisión del nombre funciona como en `AddExercise`;
  - el texto de deshacer es «Añadiste «X» desde un aviso.».
- **`ImportEditOp.IgnoreWarning(warningId)`:** «Marcaste un aviso como revisado.».
- **Pruebas:** en `ImportDraftEditorTest` y `ImportEditsTest`.

## 2. Panel de avisos (nuevo `ImportWarningsPanel.kt`; sustituye a `ImportWarningsNote` en las dos pantallas)
- **Cabecera:**
  - el título «Avisos de la importación»;
  - debajo, en `note`, «N sin revisar» o «Todo revisado»;
  - la acción «Ignorar todos» (solo con alguno sin revisar).
- **Plegado:**
  - si no queda ninguno sin revisar, el panel sale plegado en una línea («Avisos: todo revisado · Ver») y no aparece la lista de revisados;
  - «Ver avisos» de la revisión final (H2a) lo despliega y lo trae a la vista.
- **Un aviso sin revisar:**
  - arriba, la ubicación en `note`/`textMuted` («Hoja «Día 2», fila 4»);
  - luego el mensaje;
  - y, si hay `sourceText` distinto del mensaje, «Tu archivo dice «…»», en 3 líneas como mucho.
- **Acciones según el tipo:**
  - **`SKIPPED_ROW`:** «Añadir como ejercicio» e «Ignorar».
    - «Añadir como ejercicio» abre debajo el panel de añadir (`ImportAddExercisePanel`).
    - **Nombre propuesto:** el primer tramo de `sourceText` (separado por « · ») con 3 letras o más, sin la notación de series, repeticiones ni carga, y como mucho 60 caracteres.
    - **Series y repeticiones:** 3 × 10, o las que se lean en el texto si las hay.
    - **Selector de día:** lleva la línea «Añadir a:» y los días de la semana del aviso como opciones, con «✓» en la elegida, más «Un día nuevo». Por defecto, el día del aviso si existe; si no, el primero de la semana.
    - Al añadir, aplica `AddFromWarning` y el aviso queda revisado.
  - **`IGNORED_VALUE`:** «Revisar el ejercicio» y «Entendido».
    - «Revisar el ejercicio» abre el panel de ajuste de H2a de ese `exerciseId` y lo trae a la vista; el aviso no cambia.
    - Si el ejercicio ya no existe, solo queda «Entendido».
  - **`EMPTY_DAY`, `NOTHING_FOUND` y `SHEET_NOTE`:** «Entendido».
  - «Ignorar» y «Entendido» hacen `IgnoreWarning`.
- **Revisados:** desaparecen de la lista. La acción «Ver los revisados (N)» los muestra atenuados y sin acciones.
- **Orden:** el del archivo, con la ubicación como clave estable.

## 3. Arreglo de H2b
- El panel «Añade un ejercicio» que se abre desde el menú de un día debe salir **debajo del último ejercicio de ese día**, que es donde aparecerá el nuevo. Hoy sale entre el nombre del día y sus ejercicios.

## 4. Pruebas
- **Robolectric** (`ImportWarningsPanelTest`):
  - un borrador con un aviso de cada tipo; las acciones que salen en cada uno;
  - «Añadir como ejercicio» con el nombre propuesto, otro día elegido y 4 × 8: el ejercicio aparece en ese día y el aviso deja de estar sin revisar;
  - «Un día nuevo» crea «Día <n+1>»;
  - «Revisar el ejercicio» abre su panel;
  - «Ignorar todos» y la revisión final pasan a «Listo»;
  - «Deshacer» de un `AddFromWarning` devuelve el aviso y quita el ejercicio.
- **Hoja de render** (`ImportWarningsRenderTest`, `KPKN_RENDER_DIR=C:/kw/shots/v3/h2c`, a 411 dp y a 320 dp con letra al 130 %):
  - (a) el panel con 4 avisos de tipos distintos;
  - (b) «Añadir como ejercicio» abierto, con el selector de día;
  - (c) el panel plegado «todo revisado»;
  - (d) el arreglo de H2b, con el panel de añadir debajo del último ejercicio.
  - Máximo 3 rondas.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/u1/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ImportWarnings*" --tests "*ImportEdits*" --tests "*ImportStructure*" --tests "*ImportExerciseEditor*" --tests "*ImportDraft*" --tests "*ImportProgramScreen*" --tests "*ProgramImportStep*" --tests "*ImportActivation*"`
- Las hojas de render, aparte, con `KPKN_RENDER_DIR`.

## Commit
- Por rutas, en `v3/h2c`, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- El conmutador kg/lb y la interfaz del OCR (H4c).
- El analizador, el lector y el emparejador.
- `WizardUnitToggle` y `EntrenoTextAction`.
- `MainActivity.kt`.
- Push.

## Informe
- Firmas nuevas.
- Pruebas: número y resultado.
- Las rutas de los PNG y lo que corregiste en cada ronda.
