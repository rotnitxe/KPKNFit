# SE2 · Editor de sesiones: fila superior, cabecera, navegador e islas (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/wizard-v3` con SE0 y D2 integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md`, §0, §2 (puntos 1, 2 y 9), §8 y §9: TU especificación;
- el mapa `C:\kw\analysis\maps\sessioneditor.md`, §1.1 a §1.3, §3, §4 y §6. Las líneas son de ANTES de SE0: búscalas por nombre;
- `screens/home/` y `screens/programdetail/design/` como referencia de `KpknTopRow`, `KpknTopIsland` y de las islas de vidrio.

Este paquete hace la PARTE DE ARRIBA del editor y sus dos islas. La lista de ejercicios (SE3), el impacto y el asistente (SE4), el
cardio (SE5), el catálogo (SE6) y las hojas (SE7) NO son tuyos: déjalos como están, aunque se vean viejos debajo de lo nuevo.

## Qué haces
1. **Fila superior** (`KpknTopRow`, sobre el negro):
   - atrás (`ChevronLeft`) con el diálogo de salida sin guardar de hoy, que ya es `KpknAlertDialog`;
   - a la derecha, el estado del guardado en `caption` `textMuted`: «Guardado», «Guardando…» o «Sin guardar», con lo que el VM ya
     sepa (`hasUnsavedChanges` y el estado del autoguardado). Sin dato fiable, «Sin guardar» solo si hay cambios;
   - `KpknIconButton(More)` con un menú de vidrio (`KpknContextMenu` de `ui/design/`, que dejó N2) con:
     - «Portada», «Historial de versiones», «Copiar a otras sesiones», «Guardar como plantilla» y «Reglas de la sesión»;
     - cada uno abre la hoja que ya existe (`BACKGROUND`, `HISTORY`, `TRANSFER`, `SAVE`, `RULES`);
     - la acción de peso corporal de competición que hoy vive en la cabecera (`updateSessionMeetBodyweight`) pasa a este menú, con la
       misma condición para aparecer.
2. **Cabecera de la sesión** (`screens/sessioneditor/design/SessionEditorHeader.kt`, sin estado):
   - el nombre en `KpknType.display` (2 líneas como máximo). Al tocarlo se edita en el sitio con un `KpknTextField` del mismo tamaño;
     «Hecho» o perder el foco llama a `updateSessionName`;
   - la descripción en `body` `textMuted`, o «Añadir una descripción» en `textFaint`; se edita igual y llama a
     `updateSessionDescription`;
   - la línea de datos en `caption` tabular: «6 ejercicios · 22 series · ≈ 58 min». Los números salen de la sesión y la duración de
     `estimatedDurationMinutes`. Sin duración, la línea no la nombra;
   - **el navegador**: un `KpknChip` con el día y la sesión («Lun · Torso A») y `ChevronDown`. Abre una `KpknSheet` con lo que hoy
     enseña `SessionContextNavigator`: las sesiones de la semana, las variantes, «Nueva sesión» y la sesión principal.
     - Cada fila, con piezas KPKN (`KpknRadioRow` o filas de 56 dp); la principal lleva «Principal» en `caption`, como dejó SE0.
     - La lógica de cambiar de sesión, crear y marcar la principal NO cambia.
   - **la portada:** deja de ocupar la cabecera. Es una franja de color de 4 dp a todo el ancho bajo la fila superior (el degradado o
     el sólido de la portada de la sesión) y se elige en la hoja «Portada».
3. **Isla superior** (`KpknTopIsland`), cuando la cabecera sale de la vista:
   - el nombre, «≈ 58 min» y la acción `Plus` («Añadir ejercicio»), que hace lo mismo que la isla inferior;
   - sustituye al hero compacto (`SessionHeroCompactOverlay`) y a `CompactSessionTitlePill`.
4. **Isla inferior** (vidrio v2 `KpknGlassVariant.ISLAND`, centrada sobre el borde inferior y con su margen de sistema), que
   sustituye al grupo de botones flotantes arrastrable (`DraggableHeroFabGroup`, `HeroGlassFab` y `HeroTimeFab`):
   - `KpknPrimaryPill("Añadir ejercicio")` con `Plus`, que abre el catálogo como hoy (página completa si hay `onOpenCatalog`, si no, la
     hoja);
   - `KpknIconButton(Tower, "Asistente de la sesión")`, que abre el asistente de hoy (el overlay AUGE, sin cambiarlo);
   - un `KpknChip` con «≈ 58 min», que hace lo mismo que el botón de tiempo de hoy;
   - se esconde mientras `KpknModalTracker` tiene algo abierto y mientras se arrastra un ejercicio o una parte. Entra con fundido y
     8 dp de subida (220 ms); con `wizardReducedMotion()`, sin movimiento;
   - la lista deja aire abajo para que la isla no tape el último elemento.
5. **Limpieza de lo que sustituyes:** borra lo que quede sin uso tras el cambio (Grep en todo `app/src`, como en SE0):
   `SessionHero`, `SessionHeroCompactOverlay`, `CompactSessionTitlePill`, `HeroGlassActionChip`, `HeroSolidActionIcon`,
   `HeroGlassIconButton`, `HeroGlassFab`, `HeroTimeFab`, `SessionHeroActionChip`, `DraggableHeroFabGroup` y
   `SessionContextNavigator`, si su contenido ya vive en la hoja nueva. Si algo lo usa `screens/workout/`, NO se borra: anótalo.
6. **Carga y error** (`SessionEditorScreen.kt`):
   - la carga ya no es un `LinearProgressIndicator` suelto: un `KpknSkeleton` de cabecera y dos módulos sobre negro;
   - el error de carga, con «Volver» y «Reintentar», en un `KpknEmptyState` con su texto llano.
7. **Fondo:** `KpknSurface.canvas`, sin `Scaffold` si ya no hace falta (la isla inferior ocupa el lugar del FAB).

## Pruebas
- **Siguen en verde:** las 33 clases de `src/test/.../screens/sessioneditor/`, las de `screens/workout/` que usan piezas del editor
  (filtro `*Workout*`), `SessionEditorInvalidDropBannerUiTest` compila, y la compilación de `androidTest`.
- **Robolectric** `SessionEditorHeaderTest`:
  - tocar el nombre lo edita y «Hecho» llama con el texto;
  - la descripción vacía muestra «Añadir una descripción»;
  - la línea de datos sin duración no la nombra;
  - el chip del navegador llama a abrir su hoja.
- **Robolectric** `SessionEditorIslandsTest`: la isla inferior llama a añadir, al asistente y al tiempo; con un modal abierto
  (`KpknModalTracker`) no está.
- **Hoja de render** `render/SessionEditorTopRenderSheetTest`:
  - la parte de arriba con nombre de dos líneas, descripción y línea de datos;
  - sin descripción;
  - el menú «⋯» abierto;
  - la hoja del navegador con 4 sesiones, una variante y la principal;
  - la isla superior;
  - la isla inferior;
  - la carga y el error;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/se2`.
  - Mira cada PNG con Read y critica con el listón del usuario (la cabecera de MacroFactor Workouts es la referencia de calma). Máximo
    3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/se2 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*Workout*" --tests "*SessionEditorTop*" > /c/kw/logs/screens-se2-<n>.log 2>&1`

## Commit
- `feat(sessioneditor): fila superior, cabecera editable, navegador en hoja e islas de vidrio (SE2)`.
- Si borras piezas viejas, aparte: `refactor(sessioneditor): quita el hero y los botones flotantes (SE2)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lista de ejercicios, el impacto y el asistente por dentro, el cardio, el catálogo y las hojas (SE3 a SE7).
- Cambiar la lógica del VM (guardar, autoguardado, arrastre, navegación entre sesiones). Lo que falte, AL FINAL del VM.
- Las firmas de las piezas que usa `screens/workout/`.
- `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y piezas borradas (con su número de líneas).
- Lo que NO borraste porque tenía uso.
- Pruebas: número y resultado.
- PNG y crítica.
