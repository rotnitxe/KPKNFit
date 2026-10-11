# H2a · Importador: ajustar cada ejercicio, deshacer y revisión final (pedido del usuario, 2026-10-11)

Worktree `C:\kw\u1`, rama `v3/h2a` (sale de `feat/wizard-v3`, que ya trae H1). Lee antes `docs/wizard-v3/BRIEF_COMUN.md` y `docs/wizard-v3/briefs/H1.md`.
Tareas Gradle `...Base...`. Español neutro, sin género gramatical. «Símbolos, no tarjetas»: superficie negra, el panel es el vidrio tenue que ya usa «Elegir».

## Objetivo
La persona quiere **ajustar lo que importó** para que el programa final quede perfecto. H1 dejó la base pura (`ImportDraftEditor`, `ImportDraftValidation`). H2a pone la interfaz de lo esencial:
1. Tocar un ejercicio abre su **panel de ajuste**: identidad, series, repeticiones, carga, esfuerzo, descanso, notas, «aplicar en las otras semanas» y orden.
2. **Deshacer** cualquier cambio.
3. **Revisión final** que impide guardar o seguir con algo a medias.

Vale igual en el paso del alta (`EntrenoProgramImportStep`) y en Programas → Importar (`ImportProgramScreen`). Los menús de día y semana, añadir ejercicios y los avisos accionables van en H2b: no los hagas.

## 1. Retoques de dominio (`domain/training/importer/`)
- `ImportDraftEditor`: las series pasan a 1–`ProgramImportParser.MAX_SETS` (20, como el analizador). `MAX_SETS` pasa a `internal`. Quita `EDIT_MAX_SETS` y corrige el KDoc.
- `ImportDraftEditor.rename(draft, exerciseId, rawName, allWeeks: Boolean = false)`: con `allWeeks`, renombra también «el mismo ejercicio» de las otras semanas. Usa la misma regla que `update`, a partir del nombre ANTERIOR.
- Nuevo `ImportDraftEditor.sameExerciseInOtherWeeks(draft, exerciseId): Int`: cuántas otras semanas tienen «el mismo ejercicio» según esa regla. Con id inexistente, 0.
- Pruebas en `ImportDraftEditorTest`: 15 series se aceptan y 21 no; rename con `allWeeks`; el contador con 3 semanas, una sin el ejercicio.

## 2. Operaciones de la interfaz (nuevo `screens/programs/import/ImportEdits.kt`, puro)
- `data class ImportSnapshot(val imported: ImportedProgramDraft, val resolutions: Map<String, String?>)`.
- **Operaciones:** `sealed interface ImportEditOp`, cada una con su texto de deshacer:
  - `Update(exerciseId, patch: ImportExercisePatch, allWeeks: Boolean)`: «Cambiaste «X».»
  - `Rename(exerciseId, rawName, allWeeks)`: «Renombraste «X».»
    - Si el nombre nuevo no tiene decisión, hereda la del nombre anterior. Un ejercicio propio sigue siendo propio, y uno elegido conserva su configuración.
    - Las decisiones de otros nombres no se tocan.
  - `Remove(exerciseId)`: «Quitaste «X».»
  - `Duplicate(exerciseId)`: «Duplicaste «X».»
  - `Move(exerciseId, toDayId, toIndex)`: si el día es otro, «Moviste «X» a «Día»»; si es el mismo, «Cambiaste el orden de «Día»».
  - `IgnoreAllWarnings`: marca como revisados todos los avisos; «Ignoraste N avisos.» o «Ignoraste 1 aviso.»
- **Aplicación:**
  - `fun apply(snapshot, op): Pair<ImportSnapshot, String>?` aplica la operación con `ImportDraftEditor` y devuelve el nuevo estado y el texto de deshacer.
  - Si la operación no cambia nada (id inexistente o parche sin cambios), devuelve `null`: no hay entrada de deshacer.
  - «X» es el nombre que se muestra: el del catálogo si se eligió uno (pásale un `nameOf: (rawName) -> String`) o el del archivo.
- **Formulario:** `data class ImportExerciseForm(...)` es el estado local del panel. Lo crea `ImportExerciseForm.of(exercise)`.
  - `patchFrom(original, form): ImportExercisePatch` pone `Set`/`Clear` SOLO en lo que cambió; lo demás queda `Keep`.
  - **Clave:** con «aplicar en las otras semanas», cada semana conserva su propia progresión en lo que la persona no tocó. Por ejemplo, la semana 2 sigue con sus 105 kg si solo cambiaste las series en la semana 1.
- **Pruebas** (`ImportEditsTest`):
  - cada operación, con su texto;
  - `null` sin cambios;
  - la herencia de decisión al renombrar;
  - `patchFrom` campo a campo;
  - 3 semanas con cargas distintas, cambiando solo las series con `allWeeks`: las cargas siguen distintas.

## 3. Estado y deshacer en los dos ViewModels
- **`SetupWizardViewModel`** (añade al final de su bloque de importación; cambios mínimos):
  - `fun editImport(op: ImportEditOp)`:
    - lee `importedProgram` e `importResolutions` del borrador y aplica `ImportEdits.apply`;
    - si el resultado no es nulo, guarda el estado anterior en una pila de deshacer en memoria (máximo 30, no se persiste);
    - escribe con `mutateDraft(SetupStepId.PROGRAM_IMPORT)`.
  - `fun undoImportEdit()`: vuelve al estado anterior.
  - El texto del último cambio va en `SetupWizardState.importUndoLabel: String?`.
  - `clearImport` y cada lectura nueva vacían la pila.
- **`ImportProgramViewModel`:** lo mismo (`edit(op)`, `undo()`, `undoLabel` en `ImportProgramState`).
  - `openedName` pasa a `openedExerciseId`.
  - `save(level)`, antes de construir, ejecuta `ImportDraftValidation.check`. Con problemas, no guarda; pone `error` = el texto de la primera línea de la revisión (§5).
- **Paso del alta:** `importProblem()` (`SetupWizardModels.kt`) también usa `ImportDraftValidation.check`. Orden de los mensajes:
  1. sin ejercicios: «No encontramos ejercicios en lo que importaste.» (como hoy);
  2. sin decidir: «Elige o deja como está los ejercicios con dudas.» (como hoy);
  3. semana vacía: «Hay semanas sin ejercicios: quítalas o añade ejercicios.»;
  4. día vacío: «Hay días sin ejercicios: quítalos o añade ejercicios.»;
  5. avisos sin revisar: «Revisa los avisos de la importación o ignóralos.»;
  6. más sesiones que días (como hoy).
- **Pruebas:** de VM con el patrón de `ImportProgramScreenTest` y `ProgramImportStepTest`.
  - editar → deshacer → mismo borrador;
  - pila de 30;
  - `clearImport` la vacía;
  - `save` bloqueado con un día vacío;
  - `importProblem` en cada caso.

## 4. Interfaz (`screens/programs/import/`; la vista previa la comparten las dos pantallas)
### 4.1 Fila de ejercicio
- La de hoy. Al tocarla (`onClickLabel = "Ajustar ejercicio"`) se abre su panel justo debajo; tocarla otra vez lo cierra. Solo hay un panel abierto, identificado por el id del ejercicio.
- Los ejercicios sin decidir siguen mostrando «Elegir» a la derecha.

### 4.2 Panel de ajuste (nuevo `ImportExerciseEditor.kt`)
- **Aspecto:** el vidrio tenue de «Elegir» (`glassFill`, borde `glassBorder` 1 dp, radio 16, padding 16), con `testTag("import-edit-panel")`. En orden:
1. **Título** «Ajusta el ejercicio» (`controlLabel`) y, debajo, «Tu archivo dice «…».» con `sourceText` (o `rawName` si no hay), en `note` y `textMuted`. Máximo 3 líneas con elipsis.
2. **Identidad:**
   - **Elegido:** «Ejercicio del catálogo: <nombre>» y la acción «Cambiar».
   - **Propio:** «Ejercicio propio», un campo «Nombre» con el `rawName` (`import-edit-name`) y la acción «Elegir del catálogo».
   - **Sin decidir:** el contenido del panel «Elegir» de hoy, desplegado.
   - «Cambiar» y «Elegir del catálogo» despliegan ese mismo contenido: sugerencias, búsqueda y «Dejarlo como está (ejercicio propio)». Reutiliza `ImportChoicePanel` como bloque interno; no dupliques su código.
   - Bajo la identidad, en `note`: «Se aplica a las N veces que aparece «rawName».». Solo si N > 1; N cuenta los ejercicios con ese mismo `rawName` en todo el borrador.
3. **Series:** selector «−  4  +» de 1 a 20 (`import-edit-sets`).
4. **Repeticiones:** conmutador «Fijas | Rango» (`WizardUnitToggle`).
   - Con «Fijas», un selector de 1 a 100.
   - Con «Rango», dos selectores, «de» y «a». Si «de» sube por encima de «a», «a» sube con él; si «a» baja por debajo de «de», «de» baja con él.
5. **Carga:** conmutador «Sin carga | kg | % 1RM».
   - kg: campo decimal (`KeyboardType.Decimal`, acepta coma o punto), 0,5–1000. Si está fuera de rango, la pista «Entre 0,5 y 1000 kg.» en `danger` y «Guardar cambios» desactivado.
   - %: campo numérico de 1 a 100, con la pista «Entre 1 y 100 %.».
6. **Esfuerzo:** conmutador «Sin esfuerzo | RPE | RIR». RPE: selector de 1 a 10 en pasos de 0,5. RIR: selector de 0 a 10.
7. **Descanso:** conmutador «Sin indicar | Indicar». Con «Indicar», selector de 0 a 30 min en pasos de 15 s, que se lee «45 s», «2 min» o «2 min 30 s».
8. **Notas:** campo de texto (`SetupFormTextField`) de hasta 200 caracteres.
9. **«Aplicar también en las otras N semanas»** (`import-edit-allweeks`; con N = 1, «en la otra semana»):
   - Interruptor con `Role.Switch`. Solo aparece si `sameExerciseInOtherWeeks > 0`.
   - Debajo, en `note`: «Solo cambia lo que tocaste aquí.».
10. **Acciones:**
   - «Guardar cambios» (`import-edit-save`): aplica `Update` (y `Rename` si cambió el nombre) y cierra el panel.
   - «Cancelar» (`import-edit-cancel`): cierra sin cambiar nada.
   - Si no hay cambios, «Guardar cambios» está desactivado.
11. **Orden y estructura**, bajo un divisor `divider`. Cada acción es inmediata y deja deshacer:
   - «Subir» y «Bajar» dentro del día (`import-edit-up`, `import-edit-down`). Desactivadas en los extremos.
   - «Mover a otro día» (`import-edit-move`): despliega los otros días de la misma semana como acciones de texto («Día 2 — Pierna»). Al elegir uno, el ejercicio va al final de ese día y el panel se cierra.
   - «Duplicar» (`import-edit-duplicate`): la copia queda debajo y el panel sigue abierto en el original.
   - «Quitar ejercicio» (`import-edit-remove`), en `danger`: quita el ejercicio y cierra el panel.

- **Selector reutilizable** (`ImportStepper`, privado del paquete):
  - botones «−» y «+» de 48 dp con `contentDescription` «Menos <campo>» y «Más <campo>», y el valor en `controlValue` en medio;
  - se desactiva en los extremos;
  - semántica de rango (`progressBarRangeInfo`) o `stateDescription` con el valor.

### 4.3 Deshacer
- Si hay `undoLabel`, arriba de la vista previa (bajo el título del programa) va una línea: el texto en `note`/`textMuted` y la acción «Deshacer» (`import-undo`). Cada toque deshace un cambio.

### 4.4 Revisión final (nuevo `ImportReview.kt`, `testTag("import-review")`)
- **Dónde:** encima de «Guardar programa» en la app y al final del paso en el alta.
- **Con problemas:** el título «Falta revisar» (`controlLabel`) y una línea por problema, cada una con su acción de texto:
  - **Sin decidir:** «Falta decidir «X».» → «Elegir»: abre el panel de ese ejercicio y lo trae a la vista con `BringIntoViewRequester`.
  - **Día vacío:** «El día «D» de la semana N no tiene ejercicios.» → «Quitar el día» (`ImportDraftEditor.removeDay`, con deshacer: añade `RemoveDay(dayId)` a `ImportEditOp`, con el texto «Quitaste «D».»).
  - **Semana vacía:** «La semana N no tiene ejercicios.» → «Quitar la semana» (añade `RemoveWeek(index)`, con el texto «Quitaste la semana N.»).
  - **Avisos sin revisar:** «Hay N avisos sin revisar.» o «Hay 1 aviso sin revisar.» → «Ver avisos» (despliega `ImportWarningsNote`) e «Ignorar todos» (`IgnoreAllWarnings`).
  - **Sin semanas:** «No quedó ninguna semana.» → «Empezar de nuevo».
- **Sin problemas:** una línea en `SymbolPalette.ok`: «Listo: S semanas · D días · E ejercicios.» (singulares bien escritos: «1 semana», «1 día», «1 ejercicio»).
- **App:** «Guardar programa» queda desactivado mientras haya problemas.
- **Alta:** la confirmación del paso ya la bloquea `importProblem()`.

## 5. Accesibilidad
- Cada acción con objetivo ≥ 48 dp.
- El panel abierto anuncia su título.
- Los conmutadores llevan `Role.RadioButton` y `selected` (ya los trae `WizardUnitToggle`).

## 6. Pruebas
- **Robolectric** (`ImportExerciseEditorTest`, con un contenedor con estado como `ProgramImportStepTest`):
  - abrir el panel, subir series y guardar → la fila dice «5 × 6»;
  - cambiar a kg con 0,2 → pista visible y «Guardar» desactivado;
  - Rango «de» por encima de «a» empuja «a»;
  - «Quitar ejercicio» y «Deshacer» lo restauran;
  - con 3 semanas, «aplicar en las otras» cambia solo las series de las tres;
  - la revisión lista lo sin decidir y bloquea «Guardar programa»;
  - «Ignorar todos» deja la revisión en «Listo».
- **Hoja de render** (`ImportEditorRenderTest`, con `KPKN_RENDER_DIR`, patrón `WizardRenderSheetTest`, 411 dp):
  - (a) la lista con un panel abierto en kg y RPE;
  - (b) un panel de un ejercicio sin decidir;
  - (c) la revisión con tres problemas;
  - (d) la revisión «Listo».
  - Mira cada PNG y corrígelo contra este brief: máximo 3 rondas.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/u1/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ImportDraft*" --tests "*ImportEdits*" --tests "*ImportExerciseEditor*" --tests "*ImportProgramScreen*" --tests "*ProgramImportStep*" --tests "*ImportActivation*" --tests "*ProgramImportParser*" --tests "*ImportedProgramBuilder*" --tests "*DomainHasNoAndroidImports*"`
- Las hojas de render, aparte, con `KPKN_RENDER_DIR=C:/kw/shots/v3/h2a`.
- Comprueba con `grep` que cada filtro casa con alguna clase.

## Commit
- Por rutas, en `v3/h2a`, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Menús de día y semana, añadir ejercicios o días, y avisos uno a uno: es H2b.
- Cambiar el analizador, el lector o los textos de los avisos.
- `MainActivity.kt`.
- Push.

## Informe
- Firmas nuevas.
- Pruebas: número y resultado.
- Las rutas de los PNG y lo que corregiste en cada ronda.
- Lo que tuviste que cambiar de pruebas que ya existían, y por qué.
