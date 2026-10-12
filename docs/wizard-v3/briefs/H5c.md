# H5c · El reloj de descanso del entreno no debe partirse en dos líneas (2026-10-12)

Worktree `C:\kw\u2`, rama `v3/h5c` (sale de `feat/wizard-v3`, versión 41). Lee antes `docs/wizard-v3/BRIEF_COMUN.md`. Tareas Gradle
`...Base...`. Español neutro.

## De dónde sale
Prueba de arranque de la PR8 en el emulador y en el Galaxy Z Flip5 (pantalla de 360 dp). El vídeo de bienvenida de Entreno, grabado
desde la app, muestra el reloj de descanso partido: «02:5» arriba y «7» abajo, o «02:» y «58».
- **Causa,** en `screens/workout/components/WorkoutRestOverlay.kt`, `NormalRestContent` (unas líneas 389-445):
  - el anillo mide 204 dp y la columna de dentro tiene 24 dp de relleno, así que el texto tiene unos 156 dp;
  - el texto es `formatTime(...)` con `displayMedium` (Syne, ancha), `FontWeight.Black` y `fontSize = 46.sp`;
  - no fija `maxLines` ni `softWrap`.
- **Mismo riesgo** en `FeedbackContent` (unas líneas 211-300). El anillo encoge de 204 dp a 152 o 132 dp y la letra de 45 a 34 o 30 sp, sin
  relleno. A 130 % no cabe.

## Cambios
1. **Componente del número:**
   - Un composable `internal` nuevo en el mismo archivo, por ejemplo `RestClockText(text, maxFontSize, modifier)`, que pinta el tiempo
     SIEMPRE en una línea (`maxLines = 1`, `softWrap = false`), sin cortar cifras y sin elipsis.
   - Si no cabe con `maxFontSize`, encoge la letra hasta que quepa, con un mínimo de 20 sp. Usa `BasicText` con
     `autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = maxFontSize, stepSize = 1.sp)`, que existe en Compose
     Foundation 1.8 (BOM 2025.07.00).
   - Si no compila en esta versión, mide con `rememberTextMeasurer` y elige el mayor tamaño que quepa en el ancho disponible
     (`BoxWithConstraints`).
   - Mismo estilo que hoy: Syne de `displayMedium`, `FontWeight.Black`, blanco y centrado.
2. **Dónde se usa:**
   - en `NormalRestContent`, con `maxFontSize = 46.sp`;
   - en `FeedbackContent`, con el `fontSize` animado de hoy como `maxFontSize`.
3. **Lo demás no cambia:** el tamaño de los anillos, el relleno, las animaciones y el resto de textos.
4. **`RestLiveCard.kt:172-178`** (reloj a 36 sp en la tarjeta a lo ancho): solo añade `maxLines = 1` y `softWrap = false`. Cabe, pero no debe
   partirse nunca.

## Pruebas (Robolectric, escritas a mano)
- **Clase:** `RestClockTextTest` en `src/test/java/com/example/kpkn/screens/workout/components/`.
- **Casos:** «02:57», «10:00» y «59:59» en una caja de 156 dp de ancho, y «02:57» en una de 124 dp. Cada uno con letra al 100 % y al 130 %
  (`CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f))`).
- **Comprobaciones:**
  - el texto tiene UNA línea (`SemanticsActions.GetTextLayoutResult` → `lineCount == 1`);
  - el texto no desborda: ancho del texto ≤ ancho de la caja;
  - con 100 % y 156 dp, el tamaño se queda en 46 sp si cabe; si no cabe, que sea el mayor que quepa.
- **Hoja de render** (solo con `KPKN_RENDER_DIR`, `@GraphicsMode(NATIVE)`, patrón de `render/WizardRenderSheetTest.kt`):
  - qué pinta: el anillo de 204 dp con «02:57» y «DESCANSO», a 100 % y a 130 %;
  - dónde: PNG en `C:/kw/shots/v3/h5c`;
  - mira los PNG con Read antes de entregar.
- Las pruebas que ya existían de `screens.workout` que tocan la superposición siguen en verde. Por ejemplo, `WorkoutBackNavigationTest`.

## Comando
`KPKN_RENDER_DIR=C:/kw/shots/v3/h5c bash /c/kw/tools/gradle_slot.sh /c/kw/u2/android-native :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "*RestClockText*" --tests "*WorkoutBackNavigation*" > /c/kw/logs/v3-h5c-1.log 2>&1`

## Commit
- Por rutas, con mensaje en español: `fix(workout): el reloj de descanso cabe siempre en una línea (H5c)`.
- La línea Co-Authored-By del BRIEF_COMUN.

## Prohibido
- Cambiar el diseño del anillo, sus tamaños o sus colores.
- Tocar el asistente (`screens/onboarding`): H5d va a la vez.
- `MainActivity.kt`.
- Los vídeos de `res/raw`: se vuelven a grabar después, lo hace el orquestador.
- Push.

## Informe
- Qué hiciste, con el tamaño final de letra en cada caso de la prueba (100 % y 130 %).
- Pruebas: número y resultado.
- Rutas de los PNG.
- Lo que no se pudo hacer y por qué.
