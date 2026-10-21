# P3b · Program Detail: Estructura, parte 2 (bloques, mesociclos y semanas) (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con P0, P1, P2, P3a, P4, CU3, I9 y D1 integrados). Lee
antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/PROGRAM_DETAIL_V1.md`, «Pestaña Estructura» (bloques y sin bloques): TU especificación;
- el mapa `C:\kw\analysis\maps\programdetail.md`;
- lo que dejaron P2 (`ProgramPhasesModule`, `ProgramWeekStrip`, `ProgramItemMenu`, los colores por fase) y P3a
  (`design/structure/`: herramientas, calendario, `ProgramDateRow`) en `screens/programdetail/design/`.

Hoy la pestaña Estructura pinta los bloques DOS veces:
- `components/BlockRoadmap.kt` (unas 860 líneas) arriba, desde `ProgramDetailScreen.kt` (aprox. 771), con pastillas de bloque,
  círculos de semana, copiar y pegar semana y sus diálogos de metadatos (nombre, descripción, objetivo y esquema de progresión del
  bloque);
- debajo, en `components/editor/MacrocycleEditorLegacy.kt`, `MacroHeader`, `BlockNode` y `WeekPillGrid` (aprox. 1996-2140), con
  `OutlinedButton` «Agregar mesociclo», «Agregar semana» y «Agregar bloque», `IconButton` con `Icons.*` y sus diálogos
  (`LegacyBlockDialog`, `LegacyMesoDialog`, `LegacyWeekDialog`, `LegacyWeekMetadataDialog`), más los de borrar y convertir.

Este paquete deja UNA sola lista de bloques. La LÓGICA no cambia: las funciones de `Program` del Legacy (`addBlockToMacro`,
`renameBlock`, `addMesocycle`, `renameMesocycle`, `addWeekToBlock`, `removeBlock`, `removeMesocycle`, `removeWeek`, `updateWeekAt`,
`convertSimpleCalendarizedToAdvanced`, `alignTemporalMetadata`) y las del VM que usa `BlockRoadmap` (`updateBlockMetadata`,
`previewBlockProgression`, copiar y pegar semana, `deleteWeekFromRoadmap`…) se llaman igual que hoy.

## Qué haces
1. **Un módulo por bloque** (`screens/programdetail/design/structure/ProgramBlockModule.kt`), en el orden del programa:
   - **cabecera:** el eyebrow con la fase en su color (la misma tabla que `ProgramPhasesModule`: Acumulación = `musculo`,
     Intensificación = `energia`, Realización = `mente`, Descarga = `columna` y Competición = `danger` con `Flag`); debajo, el nombre
     en `bodyStrong` y «3 semanas · 12 sesiones» en `caption`; a la derecha, `KpknIconButton(More, "Opciones del bloque")` con un menú
     de vidrio (`ProgramItemMenu`): «Editar bloque», «Eliminar bloque» en `danger`. «Duplicar» solo si ya existe la función: no la
     inventes;
   - **mesociclos:** una fila por mesociclo (48 dp): nombre en `bodyStrong`, objetivo en `caption` y `ChevronRight`; tocarla abre la
     hoja del mesociclo (punto 3). Si el bloque tiene un solo mesociclo, la fila se omite y las semanas cuelgan del bloque;
   - **semanas:** círculos de 40 dp en un `FlowRow` (el mismo dibujo que la fila de semanas de P2: número dentro, la actual con anillo
     de tinta, la elegida rellena de tinta; una semana de ciclo lleva un `Repeat` pequeño y la de competición un `Flag`). Tocar un
     círculo enfoca esa semana en la pestaña Semana (lo que hace hoy `onFocusWeek`). La pulsación larga abre el menú de la semana:
     «Editar semana», «Copiar semana», «Pegar aquí» (solo con una semana copiada) y «Eliminar semana» en `danger`;
   - **pie:** `KpknTextAction("Añadir semana")` y `KpknTextAction("Añadir mesociclo")`.
2. **Al final de la lista:** `KpknSecondaryPill("Añadir bloque")` a todo el ancho, que abre la hoja del bloque en modo nuevo. En un
   programa simple, la acción es «Convertir en programa por bloques» y abre el diálogo de conversión (punto 4).
3. **Hojas** (`KpknSheet`, en `design/structure/`; fuera los diálogos del Legacy y de `BlockRoadmap`):
   - `ProgramBlockSheet`: nombre (`KpknTextField`), descripción (opcional, 2-4 líneas), objetivo del bloque como fila de `KpknChip`
     con los valores de `BlockGoal`, esquema de progresión como `KpknRadioRow` con los valores de `BlockProgressionScheme` y, debajo, la
     vista previa de `previewBlockProgression` en `caption`. «Guardar» como `KpknPrimaryPill`;
   - `ProgramMesoSheet`: nombre y objetivo (`KpknChip` con los objetivos de mesociclo), «Guardar» y, al editar,
     `KpknTextAction("Eliminar mesociclo")` en `danger` (solo si el bloque tiene más de uno, como hoy);
   - `ProgramWeekSheet`: nombre y descripción, «Guardar» y `KpknTextAction("Eliminar semana")` en `danger` (solo si el programa tiene
     más de una semana, como hoy).
   - Etiquetas en español neutro: los nombres de `BlockGoal`, `BlockProgressionScheme` y de los objetivos de mesociclo salen de una
     función pura nueva con prueba (nada de `entry.name` a la vista).
4. **Diálogos** (`KpknAlertDialog`):
   - borrar bloque, mesociclo o semana: los textos de hoy, con «Eliminar» destructiva y «Cancelar»;
   - convertir a programa por bloques: título «¿Convertir en programa por bloques?» y el texto de hoy en español neutro (sin «loops»:
     «ciclos»), con «Convertir» como principal.
5. **Fuera la doble lista:** quita `BlockRoadmap` de la pestaña Estructura en `ProgramDetailScreen.kt` (solo esa llamada). Si después
   `BlockRoadmap.kt` queda sin uso en `src/main` (Grep), bórralo junto con sus pruebas propias, y anota lo borrado.
6. **Programa simple:** el único bloque se pinta con el mismo módulo; en vez del eyebrow de fase, «SEMANA QUE SE REPITE» o
   «CALENDARIZADO», y debajo de las semanas, en `caption`, «Ciclo 5» si hay ciclo.
7. **Entrada y ritmo:** los módulos siguen a los de P3a con `kpknEntrance("programdetail.estructura", i)` y 12 dp entre módulos.

## Pruebas
- **Siguen en verde:** `ProgramDetailViewModelTest`, lo que encuentres con Grep sobre `BlockRoadmap`, `MacrocycleEditor`,
  `addBlockToMacro`, `updateBlockMetadata` y `copyWeek`, lo que dejó P3a (`ProgramStructureToolsTest`) y la compilación de
  `androidTest`.
- **Unitarias:** las etiquetas de `BlockGoal`, `BlockProgressionScheme` y objetivos de mesociclo.
- **Robolectric** `ProgramBlockModuleTest`: tocar una semana llama a enfocar; la pulsación larga abre el menú y «Copiar semana» llama a
  copiar; «Añadir semana» llama a su lambda; en un simple, la acción final es convertir.
- **Hoja de render** `render/ProgramStructureBlocksRenderSheetTest`:
  - un programa de 4 bloques (uno con 2 mesociclos y una semana de competición);
  - un programa simple con ciclo;
  - el menú de una semana con «Pegar aquí»;
  - las tres hojas (bloque nuevo, mesociclo al editar y semana);
  - el diálogo de conversión;
  - la pestaña Estructura entera de arriba abajo con lo de P3a (dos o tres capturas);
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/p3b`.
  - Mira cada PNG con Read y critica con el listón del usuario. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/p3b bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "*ProgramDetail*" --tests "*ProgramStructure*" --tests "*ProgramBlock*" --tests "*BlockRoadmap*" > /c/kw/logs/screens-p3b-<n>.log 2>&1`

## Commit
- Por rutas: `feat(programdetail): Estructura con un módulo por bloque, semanas en círculos y hojas KPKN para editar (P3b)`.
- Si borras `BlockRoadmap.kt`, en un commit aparte: `refactor(programdetail): quita BlockRoadmap, ya sin uso (P3b)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Lo de P3a (herramientas, calendario y hojas de fechas) salvo encajar los módulos debajo.
- Las hojas de ciclos, ruta avanzada, plantillas e historial (P3c, en paralelo).
- La lógica de estructura del programa.
- En `ProgramDetailScreen.kt`, todo lo que no sea quitar `BlockRoadmap` de Estructura.
- `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y código borrado o sin uso.
- Pruebas: número y resultado.
- PNG y crítica.
