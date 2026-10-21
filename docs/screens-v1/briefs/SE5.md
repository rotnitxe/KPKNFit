# SE5 · Editor de sesiones: cardio y preparación con piel KPKN (2026-10-13)

Worktree y rama: los del mensaje de lanzamiento (salen de `feat/sessions-v1` con SE0 a SE3a integrados; el mensaje dice qué más hay).
Lee antes:
- `docs/wizard-v3/BRIEF_COMUN.md`;
- `docs/screens-v1/briefs/S_COMUN.md`;
- `docs/design/KPKN_UI_V2.md`;
- `docs/screens-v1/SESSION_EDITOR_V1.md`, §0, §2 (puntos 4, 5 y 7), §8 y §9: TU especificación;
- el mapa `C:\kw\analysis\maps\sessioneditor.md`, §1.4, §2.1 (cardio, intervalos, HIIT, ruedas y preparación), §6 y §7
  («Acoplamiento»). Las líneas son de ANTES de SE0, SE2 y SE3a: búscalas por nombre;
- lo que dejó SE3a en `screens/sessioneditor/design/` (módulo de parte, etiquetas «CARDIO» y «FUERZA») para seguir su lenguaje.

Este paquete reviste el CARDIO y la PREPARACIÓN del editor. La lógica NO cambia: mismos datos, mismas acciones y mismas llamadas al
VM. Cuidado: la sesión en vivo importa `CardioValuePill`, `CardioDistanceWheelDialog` y `CardioIntWheelDialog`
(`CardioMagnitudePickers.kt`). Se revisten por dentro sin cambiar su firma.

## Qué haces
1. **Módulo de preparación** (`screens/sessioneditor/design/SessionPreparationModule.kt`, sin estado). Solo si la sesión tiene
   calentamiento o movilidad, y va después del impacto (SE4) y antes de «CARDIO» y «FUERZA»:
   - `KpknModule` con el eyebrow «Preparación» (mayúsculas por estilo);
   - una fila por elemento: el nombre en `body` y su dosis en `caption` tabular («2 min» o «2 × 10»);
   - `KpknTextAction("Editar")`, que abre lo que hoy edita la preparación (`PreparationCarouselCards` o su hoja).
2. **Bloque de cardio = un módulo** (`SessionCardioModule.kt`, sin estado):
   - **plegado** (64 dp): el icono del tipo, el nombre en `bodyStrong` y la dosis en `caption` tabular. Ejemplos: «30 min · zona 2»,
     «5 km · 5:30 /km» y «8 × 0:30 / 1:30»;
   - la dosis sale de una función pura (`SessionCardioDose.kt`, con su prueba);
   - en intervalos y HIIT, debajo, una mini línea de tiempo: barras de 6 dp de alto proporcionales a la duración, el trabajo en
     `SymbolPalette.energia` y la recuperación en blanco al 12 %;
   - `ChevronDown` que gira al desplegar (sin giro con `wizardReducedMotion()`);
   - **desplegado:** los editores de hoy (`CardioEditorCard`, `CardioIntervalsEditor` y `CardioHiitEditor`) revestidos por dentro.
     Fuera `Surface` con color, `IconButton`, `Icons.*`, `Color(0x…)` y tamaños de letra sueltos; dentro, `KpknModule`,
     `KpknIconButton`, `KpknChip`, `KpknSegmentedControl`, `KpknNumberCell` (SE1) y `KpknLineIcons`.
3. **Iconos del cardio:** añade a `KpknLineIcons` cinco glifos con el trazo y la rejilla de los demás: `Run`, `Walk`, `Bike`, `Row` y
   `Stairs`. Asígnalos así:
   - `Run`: `TREADMILL`, `RUN_OUTDOOR` y `CURVED_TREADMILL`;
   - `Walk`: `WALK` y `ELLIPTICAL`;
   - `Bike`: `BIKE_STATIONARY`, `BIKE_OUTDOOR` y `AIR_BIKE`;
   - `Row`: `ROW_MACHINE` y `SKI_ERG`;
   - `Stairs`: `STAIR_CLIMBER`;
   - `Training`: `SLED`.
   - Píntalos en la hoja de render a 24 dp junto a los existentes para comprobar que son de la misma familia.
4. **Ruedas de magnitudes** (`CardioMagnitudePickers.kt`): revisten por dentro `CardioValuePill` y los diálogos de rueda (vidrio KPKN,
   cifras en `metricM` tabular, sin colores fijos), con su firma intacta. La sesión en vivo los verá igual de bien: compruébalo
   en la hoja de render.
5. **Código muerto:** la función de `CardioHiitEditor.kt` que el mapa marca como muerta (líneas 59-327 de antes), y lo que quede sin
   uso tras el cambio. Todo confirmado con Grep en `app/src`.
6. **Textos:**
   - «HIIT» sin emojis;
   - «Zona 2», «Ritmo», «Distancia» y «Tiempo» en tipo frase;
   - «…» de un carácter;
   - nada de «Warm-up»: «Calentamiento». Antes de cambiar un texto, Grep en `src/androidTest`.

## Pruebas
- **Siguen en verde:**
  - las 33 clases de `src/test/.../screens/sessioneditor/`, sobre todo `SessionEditorCardioSpaceTest` (8);
  - las de `screens/workout/` que usan piezas del editor (`*Workout*`, en especial las de cardio);
  - la compilación de `androidTest`.
- **Unitarias** `SessionCardioDoseTest`:
  - continuo por tiempo con zona;
  - por distancia con ritmo;
  - intervalos y HIIT;
  - sin datos, sin dosis inventada.
- **Robolectric** `SessionCardioModuleTest`:
  - la dosis se lee;
  - tocar despliega;
  - la mini línea tiene una barra por tramo;
  - la preparación llama a «Editar».
- **Hoja de render** `render/SessionCardioRenderSheetTest`, con las piezas montadas con datos de ejemplo:
  - la preparación;
  - el cardio continuo plegado y desplegado;
  - intervalos con su mini línea y desplegados;
  - HIIT desplegado;
  - un diálogo de rueda y un `CardioValuePill`;
  - los iconos nuevos junto a los existentes;
  - 360 dp, al 100 % y al 130 %, PNG en `C:/kw/shots/screens/se5`.
  - Mira cada PNG con Read y critica con el listón del usuario. Máximo 3 rondas.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/screens/se5 bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:compileBaseDebugKotlin :app:compileBaseDebugAndroidTestKotlin :app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.sessioneditor.*" --tests "*Workout*" --tests "*Cardio*" --tests "*SessionCardio*" > /c/kw/logs/screens-se5-<n>.log 2>&1`

Las corridas intermedias van sin `:app:compileBaseDebugAndroidTestKotlin` (S_COMUN); la final, con el comando entero.

## Commit
- `feat(sessioneditor): cardio y preparación en módulos KPKN, con su dosis y la línea de intervalos (SE5)`.
- Si borras código muerto, aparte: `refactor(sessioneditor): quita el editor de HIIT muerto y piezas sin uso (SE5)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- La lista de fuerza, las partes, el desplegado de un ejercicio de fuerza y el arrastre (SE3a y SE3b), el impacto y el asistente
  (SE4), el catálogo (SE6) y las hojas de SE7.
- La lógica del VM y del cardio. Lo que falte, AL FINAL del VM.
- Las firmas de las piezas que usa `screens/workout/` y cualquier archivo de `screens/workout/`.
- `MainActivity.kt`.
- Push.

## Informe
- Qué hiciste, por punto.
- Piezas nuevas y borradas (con sus líneas).
- Pruebas: número y resultado.
- PNG y crítica.
