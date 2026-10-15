# LR1d · Sesión en vivo ORIGINAL: refinado de las opciones del ejercicio (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con LR1a, LR1a2, SE9 y SE10 integrados). Lee ENTERO
`docs/screens-v1/briefs/LR1_COMUN.md` y lo que pide leer.

## Tus pantallas y archivos (solo estos)
Todos bajo `screens/workout/`.

| Mapa | Lo que ve la persona | Archivo |
|---|---|---|
| D1 | Cajón de opciones del ejercicio (pulsación larga en la tira): Historial, Etiquetas, Editar, Reemplazar, Agregar otro, Reordenar, Crear superserie, Omitir; variantes de cardio y superserie | `WorkoutStructureSheetsHost.kt` (L321-479, `ContextActionButton` L161) |
| D2 | Cajón «Setup»: setups guardados, máquina, carga base, notas, «Listo» y «Nuevo Setup» | `WorkoutExerciseSheets.kt` (`ExerciseSetupSheetContent` ~L625) y `WorkoutContextComponents.kt` (`WorkoutExerciseSetupContent` ~L201) |
| D3 | Cajón «Editar series» (modo, unilateral, descanso entre lados, chip «Carga inteligente», «Confirmar cambios») | `WorkoutStructureSheetsHost.kt` (L919-1205). Las piezas compartidas con el editor (`CompactModeSelector`, `UnilateralModeSelector`, `SideOrderChip`, `ExerciseSetsCarousel`, `ExerciseSmartLoadDialog` y `CardioEditorCard`) NO se tocan |
| D4–D8 | Etiquetas: hoja rápida, gestor, lista, «Nueva etiqueta» y «Historial sin etiqueta» | `WorkoutExerciseSheets.kt` (L85, L382, L225, L300 y L692) |
| D9 | Cajón «Historial» (por semana o mes, tarjetas de serie, chip «Mostrando primero») y su copia dentro del gestor de etiquetas | `WorkoutHistorySheetContent.kt` y `WorkoutContextComponents.kt` (`WorkoutExerciseHistoryContent` ~L50) |
| D11–D15 | «Persistencia de reemplazo», guardar cambios de estructura, «Rondas y descansos», crear superserie, «Reordenar ejercicios» y «Cambio de grupo» | `WorkoutStructureSheetsHost.kt` (L418-1542 según el mapa) |

En `WorkoutContextComponents.kt` NO tocas `WorkoutRmCalcContent` (la calculadora de RM): es de LR1e.

## Qué cambias (solo la piel)
- **Cajón de opciones (D1).** Las acciones (`ContextActionButton`) pasan de Surface con `#2B2B2B` a filas de opción del lenguaje
  KPKN: icono en círculo `inset` de 36 dp, texto Inter 15 y la acción peligrosa («Omitir») en `danger`. Mismo orden y mismos
  textos.
- **Setup, Editar series, etiquetas, rondas, superserie y reordenar (D2, D3, D4–D8, D11–D15).** Lo de dentro de los cajones y
  diálogos, según la tabla del común:
  - `OutlinedTextField` / `TextField` (también `WorkoutTagFilledTextField`) → `KpknTextField`;
  - `FilterChip` → `KpknChip`; `Checkbox` → `KpknCheckRow`; `IconButton` → `KpknIconButton`;
  - `Button` → `KpknPrimaryPill`; `OutlinedButton` / `FilledTonalButton` → `KpknSecondaryPill`; `TextButton` → `KpknTextAction`;
  - el `Surface errorContainer` de «Cambio de grupo» → un módulo con el texto en `danger`.
  - Los diálogos `KpknAlertDialog` y los cajones `WorkoutDrawer` se quedan: solo cambia lo de dentro.
- **Historial (D9).**
  - Las tarjetas de sesión, en `module`; las series, en filas con cifras Inter tabulares, coma decimal y «×» con espacios.
  - El chip «Mostrando primero: …», como `KpknChip`.
  - `#2C2C2C`, `#222222`, `#FFD600`, `#B3261E` y `#FF8A80`, a tokens. Un récord se marca con el acento, no con amarillo.

## Arreglos de lógica (un commit por arreglo, cada uno con una prueba que falle sin él)
- **El «1RM» del cajón de editar** (`WorkoutStructureSheetsHost.kt` ~L943, `prWeightInput.toDoubleOrNull()`): con «102,5» el chip
  «1RM: x kg» no sale. Arreglo con `.replace(',', '.')`. Commit: `fix(workout): el 1RM del cajón de editar acepta coma decimal (LR1d)`.
- **La «Carga base (kg)» del setup** (`WorkoutContextComponents.kt` ~L326, `value.toDoubleOrNull()`): mismo fallo, mismo arreglo.
  Commit: `fix(workout): la carga base del setup acepta coma decimal (LR1d)`.

## Hoja de render
`render/LiveSessionOptionsRenderSheetTest`, a 360 dp, al 100 % y al 130 %. PNG en `C:/kw/shots/screens/lr1d`. Incluye:
- el cajón de opciones (fuerza y cardio);
- el setup;
- editar series;
- la hoja rápida de etiquetas;
- el gestor de etiquetas;
- el historial con 3 sesiones (una con récord);
- reordenar;
- crear superserie.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/lr1d bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*LiveSessionOptions*" > /c/kw/logs/screens-lr1d-<n>.log 2>&1`

Sin `.dbg`: lo compilo yo con todo integrado.

## Commit
- Los dos `fix(workout): …` de arriba.
- `style(workout): el cajón de opciones, el setup y editar series con la piel nueva (LR1d)`.
- `style(workout): etiquetas, historial, superserie y reordenar con la piel nueva (LR1d)`.
- `test(workout): hoja de render de las opciones del ejercicio (LR1d)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Informe
- Qué cambió por punto (archivo y composable).
- Los arreglos de lógica, con su prueba.
- Los PNG, con tu comparación frente a la original.
- Pruebas: número y resultado.
