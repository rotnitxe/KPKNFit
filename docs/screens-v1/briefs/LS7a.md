# LS7a · Sesión en vivo: las hojas de estructura (`WorkoutDrawer` y lo que cuelga de él) (2026-10-14)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con LS0 a LS2b integrados; el mensaje dice qué más hay).
Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/LIVE_SESSION_V1.md`, §0, §6 y §8 (LS7): TU especificación;
- el mapa `C:\kw\analysis\maps\workout.md`, §2.1 («Hojas de estructura», «Hojas de contexto») y §6. Las líneas son de ANTES: búscalas
  por nombre;
- las hojas del editor ya rehechas (SE7 en `screens/sessioneditor/components/sheets/`) para seguir su lenguaje.

Las hojas que abre la sesión en vivo cuelgan de un cajón propio, `WorkoutDrawer` (`WorkoutDrawerHost.kt`), con piel vieja: `Surface`,
`Icons.*`, `Color(0x…)`. Las monta sobre todo `WorkoutStructureSheetsHost.kt` (unas 1.580 líneas: el selector para añadir o
reemplazar, las etiquetas, los ajustes de la serie y las superseries) y el historial (`WorkoutSessionOverlaysHost.kt`). Este paquete
pasa el cajón a `KpknSheet` y reviste por dentro esas hojas. La lógica NO cambia: mismos datos, mismas acciones y mismas llamadas.

## Qué haces
1. **`WorkoutDrawer` → `KpknSheet`:**
   - `WorkoutDrawer` pasa a ser una capa fina sobre `KpknSheet` (vidrio v2), con el título en `KpknType.title` y la misma firma
     (añade parámetros opcionales al final si hace falta);
   - fuera su `Surface`, sus colores fijos y su asa propia;
   - si queda sin sentido como pieza propia, que sus llamadas usen `KpknSheet` directamente y bórrala (Grep en todo `app/src`).
2. **Las hojas de `WorkoutStructureSheetsHost`**, por dentro y con piezas KPKN:
   - **añadir o reemplazar ejercicio** (`LiveExercisePickerWithCreator`): usa el catálogo que rehízo SE6a y SE6b (`ExercisePickerSheet`
     o su cuerpo), sin otra lista propia. Si hoy pinta algo distinto, explícalo;
   - **etiquetas:** `KpknChip` por etiqueta, multi-selección, y «Nueva etiqueta» con `KpknTextField`;
   - **ajustes de la serie:** filas de 56 dp con `KpknSwitch`, `KpknNumberCell` (SE1) y `KpknSegmentedControl`, en módulos;
   - **superseries** (crear, rondas y descanso): con el mismo lenguaje que las hojas de superseries del editor (SE7): miembros con
     `KpknCheckRow`, rondas con `KpknNumberCell` y descanso en m:ss;
   - `ContextActionButton` pasa a `KpknOptionRow` o a una fila de 56 dp con su icono KPKN Line.
3. **Historial del ejercicio** (`WorkoutDrawer(title = "Historial")` en `WorkoutSessionOverlaysHost.kt`): una fila por sesión con la
   fecha larga («martes 13 de octubre») y sus series («80 × 8 · 80 × 8 · 82,5 × 6») en `caption` tabular, y el mejor intento
   marcado. Mismos datos.
4. **Lo que queda sin uso:** bórralo con Grep en todo `app/src`.
5. **Textos:** tipo frase, «…» de un carácter, sin MAYÚSCULAS escritas a mano. Antes de cambiar un texto, Grep en
   `src/androidTest` (`WorkoutSupportCardsUiTest` y otras buscan textos de estas hojas).

## Pruebas
- **Siguen en verde:**
  - las JVM de `screens/workout/` y `domain/workout/`;
  - `*OngoingWorkout*`, `*FinalizeWorkout*` y `*StartWorkoutConflict*`;
  - la compilación de `androidTest`.
- **Robolectric** `WorkoutStructureSheetsTest`, con los CUERPOS de las hojas montados sueltos:
  - marcar una etiqueta llama con su id;
  - un interruptor de los ajustes llama con el valor;
  - crear una superserie con dos miembros llama con ellos.
- **Hoja de render** `render/WorkoutStructureSheetsRenderSheetTest`:
  - cada hoja con datos de ejemplo;
  - el historial con tres sesiones;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/ls7a`.
  - Mira cada PNG con Read y critica con el listón del usuario. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/ls7a bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.*" --tests "com.example.kpkn.domain.workout.*" --tests "*OngoingWorkout*" --tests "*FinalizeWorkout*" --tests "*StartWorkoutConflict*" --tests "*WorkoutStructureSheets*" > /c/kw/logs/screens-ls7a-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(workout): hojas de la sesión en vivo en KpknSheet (selector, etiquetas, ajustes de serie, superseries e historial) (LS7a)`.
- Si borras piezas viejas, aparte: `refactor(workout): quita el cajón propio de la sesión en vivo (LS7a)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- **Los medios y el grabador de la técnica** (`SetCardExerciseMediaBack`, `WorkoutMediaCaptureController`, `screens/workout/technique/`):
  los trabaja la ola VT en otra rama. No los toques, ni siquiera por fuera.
- La lógica de la sesión (`WorkoutViewModel` y sus controladores). Lo que falte, AL FINAL del VM.
- Las hojas de contexto y el contenido avanzado de la serie (LS7b), la serie, el descanso, la ruta, la preparación y el resumen
  (LS2 a LS6).
- Las firmas de las piezas de LS1 a LS6 y de las del editor: solo parámetros opcionales al final.
- `MainActivity.kt` y `screens/sessioneditor/`.
- Push.

## Informe
- Qué hiciste, por hoja.
- Piezas nuevas y borradas (con sus líneas).
- Textos cambiados y pruebas actualizadas.
- Pruebas: número y resultado.
- PNG y crítica.
