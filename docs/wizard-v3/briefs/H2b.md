# H2b · Importador: menús de día y semana, añadir días y ejercicios (2026-10-11)

Worktree `C:\kw\u1`, rama `v3/h2b` (sale de `feat/wizard-v3`, que ya trae H1, H2a, H3b y H3c). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`, `docs/wizard-v3/briefs/H1.md` y `docs/wizard-v3/briefs/H2a.md`.
Tareas Gradle `...Base...`. Español neutro, sin género gramatical. «Símbolos, no tarjetas»: los menús son el mismo vidrio tenue del panel de ajuste, en línea en la página; nada de `DropdownMenu` de Material.

## Objetivo
H2a dejó el panel de cada ejercicio, «Deshacer» y la revisión final. H2b completa la estructura: la persona puede **renombrar, ordenar, unir y quitar días**, **duplicar y quitar semanas**, y **añadir días y ejercicios**. Todo deshace con la pila de H2a y vale igual en el alta y en Programas → Importar.

## 1. Operaciones nuevas (`ImportEdits.kt`, con el `ImportDraftEditor` que ya existe)
- **`ImportEditOp`** nuevas, cada una con su texto de deshacer:
  - `RenameDay(dayId, name)`: «Renombraste «D».»;
  - `MoveDay(dayId, toIndex)`: «Cambiaste el orden de la semana N.»;
  - `MergeWithNext(dayId)`: «Uniste «D» con «D2».»;
  - `AddDay(weekIndex, name)`: «Añadiste «D».»;
  - `DuplicateWeek(index)`: «Duplicaste la semana N.»;
  - `AddExercise(dayId, rawName, sets, repsMin, repsMax, configId: String?, decided: Boolean)`: «Añadiste «X».».
    - Añade al final del día.
    - Si `decided`, fija la decisión del nombre: `configId` del catálogo, o `null` = propio.
    - Si el nombre ya tenía decisión, no la pisa.
- `RemoveDay` y `RemoveWeek` ya existen (H2a).
- **Pruebas** en `ImportEditsTest`: cada operación con su texto, y `null` cuando no cambia nada.

## 2. Interfaz (`screens/programs/import/`; nuevo `ImportStructureMenus.kt` para no engordar `ImportPreview.kt`)
### 2.1 Día
- A la derecha del nombre del día va un botón «⋯» de 48 dp (`import-day-menu-<dayId>`, `contentDescription` «Opciones de «D»»).
- Al tocarlo se abre, justo debajo, un panel de vidrio con acciones de texto (`ImportTextAction`):
  - **«Renombrar»:**
    - Muestra un campo «Nombre del día» con el nombre actual y las acciones «Guardar» y «Cancelar».
    - Con el campo vacío, «Guardar» está desactivado.
  - **«Subir» y «Bajar»:** mueven el día dentro de su semana. Están desactivadas en los extremos.
  - **«Unir con el siguiente»:** lleva sus ejercicios al final de este día. Está desactivada si es el último de la semana.
  - **«Añadir ejercicio»:** abre el panel del punto 2.3 en este día.
  - **«Quitar el día»,** en `danger`.
- Solo puede haber abierto un menú o panel a la vez, contando también el del ejercicio de H2a.

### 2.2 Semana
- A la derecha de «Semana N» va un «⋯» (`import-week-menu-<n>`). Su panel de vidrio trae:
  - «Duplicar semana»: la copia queda justo después;
  - «Añadir día»: añade «Día <n+1>» al final de la semana;
  - «Quitar semana», en `danger`, con el texto «Se quitan sus N días.» debajo, en `note`.
- Al final de cada semana va también la acción de texto «Añadir día» (`import-add-day-<n>`).

### 2.3 Panel «Añadir ejercicio» (nuevo `ImportAddExercisePanel.kt`)
- **Aspecto:** el mismo vidrio que el panel de ajuste, con el título «Añade un ejercicio».
- **Buscar en el catálogo:** reutiliza la búsqueda de `ImportChoicePanel`.
  - Al elegir un resultado, el nombre queda fijado («Ejercicio del catálogo: <nombre>») y la decisión será ese `configId`.
- **«O escribe un nombre propio»:** un campo «Nombre»; la decisión será propia (`null`).
- **Series y repeticiones:** los selectores de H2a (series de 1 a 20, repeticiones «Fijas | Rango»). Por defecto, 3 × 10.
- **Acciones:**
  - «Añadir» (`import-add-exercise-save`): solo activo con un nombre (del catálogo o propio). Añade y cierra el panel.
  - «Cancelar».
- Tras añadir, el ejercicio nuevo queda visible en su día y se puede ajustar con su panel de H2a, igual que cualquier otro.

## 3. Accesibilidad
- Objetivos de 48 dp.
- Los «⋯» llevan `Role.Button` y `stateDescription` «abierto» o «cerrado».
- Los paneles anuncian su título.

## 4. Pruebas
- **Robolectric** (`ImportStructureMenusTest`, con un contenedor con estado como `ImportExerciseEditorTest`):
  - renombrar un día;
  - subir un día y comprobar el orden;
  - unir con el siguiente: los ejercicios pasan y el día desaparece;
  - quitar un día y «Deshacer» lo devuelve;
  - duplicar una semana: aparece «Semana 2» con los mismos ejercicios;
  - quitar una semana;
  - «Añadir día» y, en él, «Añadir ejercicio» con un nombre propio y 4 × 8: la revisión pasa a «Listo» si no queda nada más;
  - añadir desde la búsqueda (con un `search` falso que devuelve un candidato): el nombre queda decidido.
- **Hoja de render** (`ImportStructureRenderTest`, `KPKN_RENDER_DIR=C:/kw/shots/v3/h2b`, a 411 dp y a 320 dp con letra al 130 %):
  - (a) el menú de día abierto;
  - (b) el menú de semana abierto;
  - (c) el panel «Añadir ejercicio» con un resultado elegido;
  - (d) el panel con nombre propio.
  - Mira cada PNG y corrígelo: máximo 3 rondas.

## Comando
`bash /c/kw/tools/gradle_slot.sh /c/kw/u1/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*ImportEdits*" --tests "*ImportStructure*" --tests "*ImportExerciseEditor*" --tests "*ImportDraft*" --tests "*ImportProgramScreen*" --tests "*ProgramImportStep*" --tests "*ImportActivation*"`
- Las hojas de render, aparte, con `KPKN_RENDER_DIR`.

## Commit
- Por rutas, en `v3/h2b`, con mensaje en español y la línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Los avisos uno a uno y el conmutador kg/lb: van en H2c.
- El analizador, el lector y el emparejador.
- `WizardUnitToggle` y `EntrenoTextAction`: usa los componentes propios del paquete (`ImportSegmented`, `ImportTextAction`).
- `MainActivity.kt`.
- Push.

## Informe
- Firmas nuevas.
- Pruebas: número y resultado.
- Las rutas de los PNG y lo que corregiste en cada ronda.
