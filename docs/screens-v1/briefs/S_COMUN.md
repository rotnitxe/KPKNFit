# Brief común de las olas P, N y CU (Program Detail, Nutrición y Cuerpo)

Aplica a todos sus paquetes. El entorno, Gradle, las pruebas mínimas, las hojas de render y Git son los de
`docs/wizard-v3/BRIEF_COMUN.md`: léelo entero. El lenguaje visual es `docs/design/KPKN_UI_V2.md` y el diseño de cada pantalla está
en `docs/screens-v1/<PANTALLA>_V1.md`.
- Logs: `C:\kw\logs\screens-<paquete>-<n>.log`.
- PNG: `C:/kw/shots/screens/<paquete>`.

## Listón
El usuario pidió nivel top mundial, por encima de Lyfta, MacroFactor Workouts y Hevy. Critica tus PNG con ese listón:
- ¿se entiende de un vistazo?
- ¿cada cifra lleva su unidad en la misma línea base?
- ¿nada se corta al 130 %?
- ¿los iconos son todos KPKN Line?
- ¿algo parece de Material genérico?

## Reglas
- **Piezas:** usa las de `ui/design/`:
  - `KpknSurface`, `KpknShape` y `KpknType`;
  - `KpknModule` y `KpknModuleHeader`;
  - `KpknPrimaryPill`, `KpknSecondaryPill`, `KpknIconButton` y `KpknTextAction`;
  - `KpknMetric` y `Modifier.kpknEntrance(grupo, índice)`;
  - los iconos de `ui/components/icons/KpknLineIcons.kt`.
  - Si falta un icono, añádelo AL FINAL de `KpknLineIcons` con la misma regla (retícula de 24, trazo de 1,75 y extremos redondos),
    y muéstralo en tu hoja.
- **Colores:** solo `WizardColors`, `SymbolPalette`, `KpknSurface` y `MacroColors` (macros). Nada de `Color(0x…)` sueltos ni de
  `MaterialTheme.colorScheme`.
- **Componentes de Material prohibidos** en las pantallas rediseñadas: `Card`, `ElevatedCard`, `Surface` con color, `FilterChip`,
  `Switch`, `RadioButton`, `Checkbox`, `AlertDialog`, `TopAppBar`, `Scaffold`, `OutlinedTextField`, `TextField`, `Button`,
  `OutlinedButton`, `IconButton` con `Icons.*` y `LinearProgressIndicator`.
  - Si una pieza propia no existe todavía (interruptor, segmentos, campo de texto, radio), créala en `ui/design/` con un KDoc,
    sin estado, y anótala en el informe.
- **Hojas y diálogos:** `KpknSheet` y `KpknAlertDialog` / `KpknGlassDialog`, que ya llevan el vidrio v2.
- **Honestidad:** sin dato, «—» o se oculta. Nunca un valor por defecto inventado. Las estimaciones llevan «≈».
- **Textos:**
  - español neutro (sin voseo) y en tipo frase;
  - «Hidratos», «Merienda»;
  - «…» como un solo carácter;
  - sin emojis y sin anglicismos que tengan traducción.
- **Etiquetas y anclas:** no quites ninguna etiqueta de prueba ni ancla del recorrido. Si mueves el elemento, la etiqueta va con él.
  Grep en `src/test` y `src/androidTest` antes de cambiar un texto que puedan buscar las pruebas.
- **Composables nuevos:** sin estado, en `screens/<pantalla>/design/`, un archivo por pieza, con datos simples y lambdas. Así la
  hoja de render los pinta con datos de ejemplo.
- **ViewModels:** los campos y funciones nuevos van AL FINAL. No reformatees lo demás.
- **Movimiento:**
  - entrada de módulos con `kpknEntrance`;
  - cifras que cuentan;
  - cambios de contenido con `AnimatedContent`;
  - con `wizardReducedMotion()`, valores finales y sin bucles.
- **Accesibilidad:** todo lo tocable mide al menos 48 dp, con `Role` y un texto o `contentDescription`. Las gráficas llevan una
  descripción con su resumen.
- **Hojas de render:** tu clase en `src/test/java/com/example/kpkn/render/`, con `@GraphicsMode(NATIVE)` y `KPKN_RENDER_DIR`, a
  360 dp y al 100 % y 130 %. Datos de ejemplo en `src/test/.../screens/<pantalla>/<Pantalla>Samples.kt`: crea el archivo si no
  existe y, si existe, añade al final. Máximo 3 rondas de crítica.
- **Git:** commits `feat(<pantalla>): …` / `fix(…)` / `test(…)` por rutas, con la línea Co-Authored-By de Haiku 5.5 del
  BRIEF_COMUN. Sin push.

## Gradle: la cola es compartida (añadido 2026-10-13, 15:55)
- Hay 2 ranuras para toda la máquina y 7 agentes. Cada corrida que ahorras la ganan todos.
- **Corridas intermedias** (mientras arreglas): sin `:app:compileBaseDebugAndroidTestKotlin`, solo
  `:app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests …` con tus filtros.
- **La última corrida antes del commit**, el comando COMPLETO del brief, con la compilación de `androidTest`.
- Antes de lanzar una corrida, revisa a mano los imports y las firmas que cambiaste: un error de compilación cuesta una vuelta entera
  de cola.
- Nunca pares una corrida con TaskStop: deja que termine. La cola es por orden de llegada (`gradle_slot.sh` v5).
