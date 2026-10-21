# SE4 · Editor de sesiones: impacto en los RINGS y asistente de la sesión (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con SE0, SE1 y SE2 integrados). Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md`, §0, §2 (punto 3), §5, §8 y §9: TU especificación;
- el mapa `C:\kw\analysis\maps\sessioneditor.md`, §1.2, §1.6 (AUGE), §3 y §8. Las líneas son de ANTES de SE0 y SE2: búscalas por
  nombre;
- `ui/components/rings/` (`KpknTowerRings`, `KpknRingColors`) y la barra de músculo de la pestaña Volumen de Program Detail (P4, en
  `screens/programdetail/design/`), con sus zonas y su marca.

Este paquete hace el IMPACTO de la sesión (un módulo bajo la cabecera) y el ASISTENTE (la hoja que hoy es `AssistantGlassOverlay`). La
lógica de AUGE (`SessionEditorAugeComputation`, las acciones de AUGE del VM, las plantillas y sus fatigas) NO cambia: solo la piel y
dónde se ve.

## Qué haces
1. **Módulo «Impacto de la sesión»** (`screens/sessioneditor/design/SessionImpactModule.kt`, sin estado), justo debajo de la cabecera
   de SE2 y antes de la preparación:
   - a la izquierda, la torre de los RINGS (`KpknTowerRings`) de 72 dp con lo que gastará la sesión;
   - a la derecha, tres cifras en `KpknType.metricM` tabular con su nombre en `caption` y su color de `KpknRingColors`: «Músculos
     −32», «Energía −18» y «Columna −12». El signo es «−» (U+2212);
   - si el motor no tiene datos (`augeSummary` nulo o sin canales), el módulo NO se pinta: nada de cifras inventadas ni ceros;
   - tocarlo abre el asistente en «Impacto»;
   - sustituye a `EstimatedRingsRow` / `SessionEstimatedRings`. Si quedan sin uso, se borran (Grep en todo `app/src`; si los usa
     `screens/workout/`, se quedan).
2. **Asistente de la sesión** (`SessionAssistantSheet.kt`), una `KpknSheet` casi a pantalla completa:
   - el título «Asistente de la sesión» en `KpknType.title`;
   - un `KpknSegmentedControl`: «Impacto · Sugerencias · Plantillas». Abre en la pestaña que pida quien la abre (el módulo, en
     «Impacto»; la isla inferior de SE2, en la última usada o en «Impacto»);
   - **Impacto:**
     - la torre grande (120 dp) y el gasto por canal, como en el módulo;
     - debajo, una fila por músculo con la barra de P4 (zonas y marca). Reutiliza esa pieza si se puede sin cambiarla; si no, cópiala
       con un comentario de dónde sale;
     - ordenadas de más a menos carga;
   - **Sugerencias:** una fila por sugerencia del motor con su porqué en `caption` y `KpknTextAction("Aplicar")`. Sin sugerencias,
     `KpknEmptyState` con «Sin sugerencias para esta sesión»;
   - **Plantillas:** un buscador `KpknTextField` con `Search` y la placeholder «Buscar plantilla…», y un módulo por plantilla con su
     nombre, su fatiga y «Aplicar». Aplicar pide confirmación con `KpknAlertDialog`, como hoy;
   - cargando: `KpknSkeleton`, con «Calculando análisis…» en `caption` si tarda más de 600 ms;
   - sustituye al `AssistantGlassOverlay`; la isla inferior y el módulo de impacto abren esta hoja.
3. **Textos:** «RINGS de la sesión», si lo usas como eyebrow, en mayúsculas por estilo y no en la cadena. Nada de «Defaults» ni de
   mayúsculas escritas a mano en las cadenas.
4. **Accesibilidad:** el módulo de impacto se lee «Impacto de la sesión: músculos menos 32, energía menos 18, columna menos 12. Abrir
   el asistente». La torre lleva su descripción.

## Pruebas
- **Siguen en verde:** las 33 clases de `src/test/.../screens/sessioneditor/` (las de AUGE y plantillas sobre todo), las de
  `screens/workout/` que usan piezas del editor (`*Workout*`) y la compilación de `androidTest`. `SessionEditorFocusUiTest` busca
  «assistant-sheet» con su propio composable: debe seguir compilando.
- **Robolectric** `SessionImpactModuleTest`:
  - sin datos no se pinta;
  - con datos se leen las tres cifras con «−»;
  - tocarlo llama a abrir el asistente.
- **Robolectric** `SessionAssistantContentTest`, con el CUERPO de la hoja montado suelto (los toques dentro de `KpknSheet` no llegan en
  Robolectric):
  - cambiar de pestaña;
  - «Aplicar» de una sugerencia llama con su id;
  - «Aplicar» de una plantilla pide confirmación.
- **Hoja de render** `render/SessionImpactRenderSheetTest`:
  - el módulo con datos;
  - el asistente en sus tres pestañas;
  - sin sugerencias;
  - cargando;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/se4`.
  - Mira cada PNG con Read y critica con el listón del usuario: esto es lo que nadie más tiene; tiene que entenderse de un vistazo.
    Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/se4 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*Workout*" --tests "*SessionImpact*" --tests "*SessionAssistant*" > /c/kw/logs/screens-se4-<n>.log 2>&1`

## Commit
- `feat(sessioneditor): impacto de la sesión con la torre de los RINGS y asistente en hoja KPKN (SE4)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lista de partes y ejercicios (SE3a trabaja en paralelo en `SessionEditorScreen.kt`: tú solo añades el ítem del módulo de impacto
  tras la cabecera y cambias cómo se abre el asistente), las hojas de SE7, el cardio y la preparación (SE5) y el catálogo (SE6).
- La lógica de AUGE, de las sugerencias y de las plantillas.
- Las firmas de las piezas que usa `screens/workout/`.
- `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas.
- Pruebas: número y resultado.
- PNG y crítica.
