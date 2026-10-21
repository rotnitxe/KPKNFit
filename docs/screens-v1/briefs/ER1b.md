# ER1b · Editor original refinado: la lista (espacios, grupos, ejercicio plegado y superseries) (2026-10-15)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con ER0 integrado: el editor de la PR11). Lee ENTEROS:
- `docs/screens-v1/SESSION_EDITOR_R1.md`, sobre todo §0, §1 y §3. Es tu contrato;
- el mapa del original, `C:\kw\analysis\maps\session_editor_original_map.md`: E5–E8, E13 (solo la vista), E14 (solo la fila del
  cardio), E19 y E26;
- `docs/screens-v1/briefs/LR1_COMUN.md` (la piel) y `docs/wizard-v3/BRIEF_COMUN.md`.

**Regla de oro.** REFINAR el original. Todo lo que hacía la lista del original se sigue pudiendo hacer, con los mismos gestos:
- grupos visibles, que se crean, se renombran en sitio, se pliegan, se arrastran y se borran deslizando con su diálogo;
- arrastrar ejercicios y bloques con su indicador;
- deslizar un ejercicio para borrarlo;
- la pulsación larga en el nombre para la ficha;
- la estrella de Meta en la fila;
- el espacio de cardio y su orden.
Lo único que se quita (lo pidió el usuario): el COLOR de los grupos y su panel de colores.

**Piezas que puedes reaprovechar.** El rediseño archivado tiene piezas VISUALES. Míralas con
`git show archive/session-editor-redesign-v1:android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/<ruta>`:
- `design/SessionPartModule.kt`, `design/SessionExerciseRow.kt`, `design/SessionSupersetCaption.kt`, `design/SessionGroupLabel.kt`,
  `design/SessionModuleSurface.kt`, `design/SessionCardioModule.kt` y `design/SessionMotion.kt`;
- `SessionExerciseDose.kt` y `SessionCardioDose.kt` (formateadores de la dosis).
Úsalas como piel sobre la estructura del original. No traigas nada que quite o esconda una función del original.

## Qué haces (R1 §3)

### 0. Primero, sin cambiar conducta
Separa `components/ExerciseEditorCard.kt` en dos archivos:
- el plegado, que es tuyo;
- el desplegado, a un archivo nuevo (`components/ExerciseEditorExpanded.kt`). NO lo cambies: es de ER1c, que va detrás.

Commit propio: `refactor(sessioneditor): el desplegado del ejercicio a su archivo (ER1b)`.

### 1. Espacios (E6)
- Fuera los divisores de color («ESPACIO DE FUERZA» en primary y «ESPACIO DE CARDIO» en #10B981). En su lugar, una ceja «FUERZA» o
  «CARDIO» (`KpknType.eyebrow`, `textMuted`) con «Mover al inicio» / «Mover al final» como `KpknTextAction`
  (`toggleCardioPlacement`).
- **Pie de fuerza:**
  - «+ Añadir ejercicio» (`KpknTextAction`), además del botón de la barra inferior;
  - «Nuevo grupo» (`KpknSecondaryPill`);
  - sin cardio, «Crear espacio de cardio» (`KpknSecondaryPill`, sin verde).
- **Pie de cardio:** «+ Añadir cardio».

### 2. Grupos (E7): visibles siempre
- **Cada grupo es un módulo** (`#111`, radio 24 y filete del 7 %). La cabecera, arriba dentro del módulo:
  - el asa (`KpknLineIcons.Grip`), con pulsación larga para arrastrar el grupo como el original;
  - el nombre editable en sitio (Inter SemiBold 15, como lo escribió la persona; marcador «Grupo»);
  - el resumen «3 ejercicios · 9 series» (`textMuted`);
  - la flecha de plegar.
- Sin punto ni panel de color. Los datos de color se quedan; solo no se pintan ni se eligen.
- **Dentro, los ejercicios del grupo.** Al pie, `KpknTextAction("+ Añadir ejercicio a <grupo>")`.
- **Borrar:** deslizando la cabecera, con los diálogos del original («Eliminar grupo» y «¿Qué hacemos con los ejercicios?», con
  «Conservar ejercicios» y «Borrar grupo y ejercicios») en `KpknAlertDialog`. El destructivo, en `danger`.
- **Grupos de movilidad** (`MobilityGroupInlineEditor`): el mismo módulo, con la ceja «MOVILIDAD».
- **Ejercicios sueltos** (fuera de un grupo): en un módulo sin cabecera, para que todo se lea igual.

### 3. Ejercicio plegado (E8)
- **Fila de 64 dp dentro del módulo de su grupo**, separada de la siguiente por una línea blanca al 6 %:
  - el asa a la izquierda (pulsación larga para arrastrar, como el original);
  - la miniatura de 40 dp: la imagen del catálogo si la hay (`ExerciseTechniqueImageLookup`) o el glifo de su patrón;
  - el nombre en Inter SemiBold 15, en 2 líneas como mucho;
  - la línea de dosis con `KpknSetFormat`: «4 × 4–6 · 80 % 1RM · RIR 2 · 3:15». Reutiliza `sessionExerciseDose`. En unilateral,
    «por lado»;
  - la estrella de Meta, como en el original: vacía en `textMuted` y llena en Energía (#F7CF73) si está activa. Mismo toque;
  - «Movimiento de competición», si aplica, como ceja;
  - la flecha.
- **Gestos:**
  - tocar despliega;
  - la pulsación larga en el nombre abre la ficha (E15);
  - deslizar a la izquierda borra, como el original, pero con un aviso «Ejercicio eliminado» y «Deshacer» (5 s) en lugar de borrar
    sin vuelta. Añade al VM lo justo para deshacer: devolverlo a su sitio.
- **El cardio en la lista:** su fila con el icono de su máquina, el nombre, su dosis («20 min · Z2», «8 × 30 s / 30 s · RPE 9»;
  reutiliza `SessionCardioDose`) y la mini gráfica. Sin el degradado verde.

### 4. Superseries, la vista (E13)
- **Un bloque dentro del grupo,** con un raíl vertical de 3 dp en tinta al 30 % que une a sus miembros, que se ven SIEMPRE (el
  original los escondía hasta desplegar: es el único cambio).
- **Cabecera:**
  - «Superserie · 3 rondas · 1:00 / 2:00», con «Opcional» si lo es;
  - el asa del bloque, que se arrastra entero como el original;
  - un «⋯» con «Rondas y descansos» (abre lo que abría el original), «Convertir en opcional», «Disolver» y «Eliminar superserie»
    (con el diálogo del original).
- Lo de DENTRO del editor de rondas (`SupersetRoundsAndConfig`) es de ER1c: tú solo lo abres como hoy.

### 5. Sesión vacía (E5) y arrastre (E26)
- **Sesión vacía:** «¿Qué quieres entrenar en esta sesión?» con dos filas (`KpknOptionRow` o equivalente): «Fuerza» y «Cardio», con
  su icono y la misma acción.
- **Arrastre** (`SessionEditorVisuals.kt`):
  - la vista flotante con sombra suave y la escala del original;
  - la línea de inserción en tinta;
  - la pancarta «Zona no válida» en `danger`.
  - Fuera `DarkEditor*`, #F43F5E y el resto de hex: a tokens.

## Pruebas
- Siguen en verde: `com.example.kpkn.screens.sessioneditor.*`.
- **Nuevas:**
  - el grupo pinta su nombre y su resumen sin color;
  - renombrar en sitio;
  - plegar;
  - deslizar el grupo abre su diálogo;
  - deslizar un ejercicio lo quita y «Deshacer» lo devuelve a su sitio;
  - la línea de dosis («4 × 4–6 · 80 % 1RM · RIR 2 · 3:15»; «por lado»; cardio);
  - la superserie muestra a sus miembros plegada.
- **Hoja de render** `render/SessionEditorListRenderSheetTest`, a 360 dp, al 100 % y al 130 %. PNG en `C:/kw/shots/screens/er1b`:
  - dos grupos, uno plegado;
  - ejercicios sueltos;
  - una superserie de 3;
  - un grupo de movilidad;
  - el espacio de cardio con 2 cardios;
  - la sesión vacía;
  - el arrastre (vista flotante y zona no válida);
  - el aviso de «Deshacer».

## Permitido y prohibido
- **Permitido:**
  - `SessionEditorScrollRenderer.kt`, `SessionListItems.kt` y `SessionEditorVisuals.kt`;
  - `components/GroupEditorCard.kt`, `components/ExerciseEditorCard.kt` (el plegado), `components/SupersetGroupEditorCard.kt` (la
    cáscara y la cabecera) y `components/SessionEditorEmptyState.kt`;
  - `SessionEditorViewModelStructure.kt`, solo para «Deshacer»;
  - archivos nuevos para las piezas.
- **Prohibido** (lo hacen otros paquetes a la vez):
  - el desplegado del ejercicio y el editor de rondas (ER1c);
  - el cardio por dentro (ER1d);
  - el asistente, las reglas y los diálogos (ER1e);
  - el marco: fila superior, cabecera, barra inferior y guardado (ER1a);
  - el catálogo;
  - `ui/design` y `ui/components`, salvo usarlos;
  - `screens/workout/`;
  - push;
  - el teléfono y el emulador.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/er1b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*SessionEditorList*" > /c/kw/logs/screens-er1b-<n>.log 2>&1`

- Mira los PNG antes de lanzar otra corrida. Tope: 5 corridas.
- Sin `.dbg`.

## Commit
- El `refactor` del punto 0.
- `style(sessioneditor): espacios y grupos con la piel nueva, sin color (ER1b)`.
- `style(sessioneditor): el ejercicio plegado con su dosis, y deslizar para borrar con Deshacer (ER1b)`.
- `style(sessioneditor): las superseries con sus miembros a la vista (ER1b)`.
- `test(sessioneditor): hoja de render de la lista (ER1b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió por punto y dónde.
- Los PNG, con tu comparación frente al original.
- Pruebas: número y resultado.
