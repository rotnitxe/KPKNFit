# SE6a · Catálogo de ejercicios: la lista (cabecera, filtros, filas e isla de selección) (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1`; el mensaje dice qué paquetes SE ya están). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md`, §0, §4, §8 y §9: TU especificación;
- el mapa `C:\kw\analysis\maps\sessioneditor.md`, §1.1 (catálogo), §2.1 («Catálogo V2», «Tarjetas del selector»), §6 y §7. Las
  líneas son de ANTES de SE0: búscalas por nombre;
- la fila de ejercicio de SE3a en `screens/sessioneditor/design/` (miniatura con la imagen del catálogo o el glifo del patrón), para
  usar la misma miniatura.

El catálogo (`components/ExercisePickerV2Catalog.kt`, unas 3.000 líneas) lo usan el editor (página completa por la ruta
`exerciseCatalog` y hoja `ExercisePickerSheet`) y la sesión en vivo (`WorkoutStructureSheetsHost`, para añadir o reemplazar). Este
paquete reviste la LISTA. La ficha, crear ejercicio, el configurador de superseries y el asistente de variantes son de SE6b. La
lógica NO cambia: misma búsqueda, mismos filtros, misma selección y mismas llamadas.

## Qué haces
1. **Cabecera** (sobre el negro):
   - en la página completa, `KpknTopRow` con «Atrás» y el título «Añadir ejercicios» en `KpknType.display`. En la hoja, solo el
     título en `KpknType.title`;
   - el buscador `KpknTextField` con `KpknLineIcons.Search` y la placeholder «Buscar ejercicio…». Sustituye a
     `FloatingCatalogSearch`;
   - una fila de `KpknChip` desplazable con los filtros de hoy (músculo, material, patrón y «Mis ejercicios»). Un filtro activo se
     ve seleccionado y con su valor («Pecho»). Al tocarlo se abre el mismo selector de hoy. Sustituye a `CatalogFilterHeader` y a
     `catalogFilterChipColors`.
2. **Una fila por ejercicio** (`screens/sessioneditor/design/CatalogExerciseRow.kt`, sin estado, 64 dp):
   - la miniatura de 48 dp con radio 12: la imagen del ejercicio o su glifo (la misma fuente que la fila de SE3a);
   - el nombre en `bodyStrong` (una línea con «…»);
   - «Pecho · Barra» en `caption` `textMuted`;
   - a la derecha, el círculo de selección (multi-selección): vacío con anillo `divider` y, elegido, relleno de tinta con `Check`
     negro;
   - si la definición tiene variantes, al tocar la fila se despliegan como `KpknChip` debajo (sustituye a
     `CatalogFoldedVariantChips`), como hoy. Si no, tocar la fila abre la ficha de hoy;
   - las coincidencias de búsqueda (`searchMatchChipLabels`) como `caption` en `textFaint`, no como chips.
3. **Isla inferior de selección** (vidrio v2 `KpknGlassVariant.ISLAND`), visible con algo elegido:
   - `KpknPrimaryPill("Añadir 3")` (el número es lo elegido), que hace lo que hoy `IntegratedCatalogAddButton`;
   - a su izquierda, `KpknTextAction("Superserie")` si hoy existe crear superserie desde la selección;
   - se esconde con el teclado abierto.
   - `SelectedExercisesAppendix` pasa a una línea en la isla («Press banca, Remo con barra y 1 más») o se quita si la isla ya lo
     dice.
4. **Estados:** cargando con `KpknSkeleton` (5 filas); sin resultados, `KpknEmptyState` con «No encontramos “<búsqueda>”» y
   «Crear ejercicio» como acción (lo de hoy, `shouldShowCatalogCreateSuggestion`).
5. **Lo que queda sin uso** tras el cambio: bórralo con Grep en todo `app/src`. Las funciones de lógica (`visibleDefinitionsForQuery`,
   `prepareCatalogDefinitionProjection`, `restoreSelectedCatalogRows`…) se quedan.
6. **Textos:** tipo frase, sin MAYÚSCULAS escritas a mano, «…» de un carácter. Antes de cambiar un texto, Grep en `src/androidTest`
   (`ExercisePickerCardsUiTest` busca «Información del ejercicio», «Músculos · aporte por 1 serie», «Cerrado» y «Mostrar/Ocultar
   información de…»).

## Pruebas
- **Siguen en verde:**
  - las 33 clases de `src/test/.../screens/sessioneditor/`, sobre todo `ExercisePickerCatalogLogicTest` (8);
  - las de `screens/workout/` que usan el catálogo (`*Workout*`);
  - la compilación de `androidTest`.
- **Robolectric** `CatalogExerciseRowTest`:
  - la fila se lee con nombre, músculo y material;
  - tocar el círculo llama a seleccionar;
  - una definición con variantes las despliega;
  - la isla dice «Añadir 2» con dos elegidos y llama a añadir.
- **Hoja de render** `render/CatalogListRenderSheetTest`, con las piezas montadas con datos de ejemplo (sin el repositorio si no se
  puede):
  - la cabecera con un filtro activo;
  - 6 filas (con imagen y con glifo, una con variantes desplegadas, dos elegidas);
  - la isla con 2 elegidos;
  - cargando;
  - sin resultados;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/se6a`.
  - Mira cada PNG con Read y critica con el listón del usuario: buscar y añadir tres ejercicios tiene que ser más rápido y más
    claro que en Hevy y Lyfta. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/se6a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*Workout*" --tests "*CatalogExerciseRow*" --tests "*CatalogList*" > /c/kw/logs/screens-se6a-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(sessioneditor): catálogo de ejercicios con buscador, filtros, filas con miniatura e isla de selección KPKN (SE6a)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La ficha del ejercicio, crear ejercicio (`SmartExerciseEditorPage`, `SmartExerciseForm`), el configurador de superseries y el
  asistente de variantes (`CatalogSelectionWizard`): son de SE6b.
- La firma de `ExercisePickerV2Catalog`, `ExercisePickerSheet` y `ExerciseCatalogScreen` (los usan la sesión en vivo y la ruta).
- La lógica de búsqueda, filtros y selección.
- `MainActivity.kt`, `navigation/` y cualquier archivo de `screens/workout/`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Pruebas: número y resultado.
- PNG y crítica.
