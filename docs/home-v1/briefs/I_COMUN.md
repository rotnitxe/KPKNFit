# Brief común de la ola I (Home v1)

Aplica a todos los paquetes I1 a I6. El entorno, Gradle, las pruebas mínimas, las hojas de render, el diseño y Git son los de
`docs/wizard-v3/BRIEF_COMUN.md`: léelo entero. El diseño de la página completa está en `docs/home-v1/HOME_V1.md`. Logs:
`C:\kw\logs\home-<paquete>-<n>.log`. PNG: `C:/kw/shots/home/<paquete>`.

## Reglas propias de Home
- **Rutas:** la pantalla vive en `screens/home/`. Las piezas visuales nuevas van en `screens/home/design/`, un archivo por pieza,
  como composables SIN estado: reciben datos simples y lambdas, y no leen ViewModels. Así la hoja de render puede pintarlas con datos
  de ejemplo.
- **Tokens:**
  - Usa `WizardColors`, `WizardTypography`, `WizardSpacing`, `SymbolPalette` y `wizardReducedMotion()` (de
    `screens/onboarding/design/`), igual que el wizard.
  - En Home NO se usa `MaterialTheme.colorScheme` ni `Card`, `Surface` con color, `LinearProgressIndicator`, `FilledIconButton` o
    `ModalBottomSheet` de Material.
  - Para botones, píldoras propias. La píldora principal mide 56 dp de alto: fondo `WizardColors.text`, texto negro Inter 700 a
    16 sp. La secundaria va con borde de 1 dp en `WizardColors.text` al 30 %, fondo transparente y texto en tinta.
- **Etiquetas de sección:** el rol `WizardTypography.eyebrow`, en mayúsculas y en `WizardColors.textMuted`. Usa la pieza compartida
  `HomeSectionLabel` que crea I1. Las acciones de texto a la derecha (p. ej. «Ver todos») miden al menos 48 dp.
- **Animaciones:**
  - Entrada con `Modifier.homeEntrance(index)` (la crea I1).
  - Para cifras, `animateIntAsState` de 900 ms y para trazos, `animateFloatAsState`, ambos con `FastOutSlowInEasing`, solo la
    primera vez que se ven.
  - Los bucles van con `rememberSymbolClock` y solo corren si la pieza está visible (`onSymbolsVisible`).
  - Con movimiento reducido, valores finales y sin bucles.
- **Honestidad:**
  - Ningún valor por defecto inventado: sin dato se muestra «—» o se oculta.
  - No quites etiquetas existentes de prueba o del recorrido: `tourAnchor(TourAnchors.HOME_SESION | HOME_RINGS | HOME_PROGRAMAS)`,
    `HOME_PROGRAMS_ROW_TAG`, `homeProgramCardTag(id)` y `COMPETITION_DATE_PROMPT_TAG`. Si mueves el elemento, la etiqueta va con él.
- **Accesibilidad:**
  - Cada cosa tocable mide al menos 48 dp, con `Role` y `contentDescription` o un texto visible.
  - Las cifras animadas exponen su valor final en la semántica.
- **Archivos calientes compartidos:** `HomeScreen.kt` y `HomeViewModel.kt`.
  - En `HomeScreen.kt` toca SOLO lo que diga tu brief: tu `item(...)` de la `LazyColumn` o tus parámetros.
  - En `HomeViewModel.kt`, los campos y funciones nuevos van AL FINAL (de la clase y de `HomeUiState`).
  - No reordenes ni reformatees lo demás: otros paquetes editan los mismos archivos en paralelo.
- **Hojas de render:**
  - Cada paquete crea SU clase en `src/test/java/com/example/kpkn/render/` (p. ej. `HomeHeroRenderSheetTest`) con
    `@GraphicsMode(NATIVE)` y `KPKN_RENDER_DIR`.
  - Pinta a 360 dp de ancho y a 100 % y 130 % de letra.
  - Los datos de ejemplo van en `src/test/java/com/example/kpkn/screens/home/HomeSamples.kt` (lo crea I1; los demás AÑADEN funciones
    al final).
  - Mira cada PNG con Read y critícalo contra `HOME_V1.md`: ¿se ve bonito y de la marca?, ¿se lee a 130 %?, ¿algo cortado? Máximo 3
    rondas.
- **Git:** commits `feat(home): …` / `fix(home): …` / `test(home): …`, con la línea Co-Authored-By de Haiku 5.5 del BRIEF_COMUN. Sin
  push.

## Lo que dejó I1 (úsalo; no lo dupliques)
- **`screens/home/design/HomeDesign.kt`:**
  - `HomeSectionGap` (40 dp);
  - `HomeSectionLabel(text, modifier, action)`;
  - `Modifier.homeEntrance(index)`;
  - `HomePrimaryPill(text, onClick, modifier, tag)` y `HomeSecondaryPill(...)`;
  - `HomeDateSaver`;
  - `HomeTypography` (`internal`): `eyebrow` a 13 sp, `dayNumber`, `avatarInitial`, …
  - Si necesitas un estilo nuevo, añádelo AL FINAL de `HomeTypography`, en vez de crear estilos sueltos.
- **`domain/training/HomeWeekResolver.kt`:**
  - `HomeWeekDay` y `HomeDayKind` (DONE, PLANNED, MISSED, ONGOING, REST);
  - `HomeProgramLine(name, weekLabel, weekIndex, weekCount, phase)`;
  - `HomeWeekResolver.week(...)` y `programLine(...)`.
- **`HomeScreen.kt`:**
  - `selectedDate` (con `rememberSaveable`) y la cabecera y la semana como primeros ítems de la `LazyColumn`;
  - `HomeTopBar(barAlpha)`.
  - `selectedDate` puede quedar fuera de la semana actual tras cambiar de programa: trátalo como hoy.
- **Pruebas:** datos de ejemplo en `src/test/java/com/example/kpkn/screens/home/HomeSamples.kt` (añade los tuyos AL FINAL) y patrón de
  hoja en `render/HomeTopRenderSheetTest.kt`.
