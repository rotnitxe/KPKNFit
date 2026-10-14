# Brief común de la ola VT (técnica en vídeo y conteo de repeticiones)

Aplica a TODOS los paquetes de la ola. Cada paquete tiene su brief en `docs/video-technique/briefs/<ID>.md`.

La especificación es `docs/video-technique/VIDEO_TECHNIQUE_V1.md`. Lee SOLO las secciones que te indique tu brief, más §3 (contratos), que es de todos.
- Los contratos de §3 son fijos. Si necesitas cambiar una firma, no lo hagas: explícalo en el informe.
- Decisiones de la ola: `docs/video-technique/DECISIONES.md`.

## Entorno
- **Repo:** KPKN Fit (Android, Kotlin 2.2, AGP 9, Jetpack Compose).
- **Worktree:** `C:\kw\vt-a`, un checkout parcial. Trabaja en la rama `vt/<paquete>`, que el orquestador crea desde `feat/video-technique`.
  - **Trabaja solo ahí.** Jamás toques `C:\Users\valen\Documents\KPKNFit` ni otro worktree.
- **Dónde está el código:**
  - app: `android-native/app/src/main/java/com/example/kpkn/`;
  - pruebas JVM: `android-native/app/src/test/java/com/example/kpkn/`;
  - depuración: `android-native/app/src/debug/`.
- **Lee primero `CLAUDE.md`.** Las reglas que más pesan aquí:
  - **`domain/` NO importa nada de `android.*`.** `kotlinx.serialization`, `java.util.zip` y `java.nio` sí se pueden usar. Nada de `android.util.Log` en el dominio.
  - **ViewModels:** exponen `StateFlow` con `asStateFlow()`. Los campos y funciones nuevos van AL FINAL; no reformatees el resto.
  - **IO:** en `Dispatchers.IO`. El cálculo pesado va en `Dispatchers.Default` o en un hilo propio, según el brief.
- **Solo el flavor `base`:** tareas `…Base…`.
- **Idioma:** español en UI, comentarios y KDoc. Breve, imitando la densidad de comentarios del código vecino.
- **Antes de editar un archivo, léelo entero**, o al menos la clase o función que vas a tocar.
  - No inventes APIs: busca la firma real con Grep. Para una librería externa (MediaPipe, CameraX, Media3, WorkManager), mira las clases dentro del jar o aar en la caché de Gradle (`~/.gradle/caches/`) o en su documentación oficial.
  - Si algo del brief no cuadra con el código, elige la opción más conservadora y anótalo.
- **Fuera de tu lista de archivos:** no los toques. Describe en el informe el cambio que necesitas (qué, dónde y por qué).
- **Código caliente** (filtros, segmentador, visión, decodificación):
  - sin asignaciones por fotograma: reutiliza arrays y buffers;
  - sin boxing en los bucles (`DoubleArray`, `FloatArray`, `IntArray`);
  - complejidad lineal en el número de fotogramas, salvo que el brief diga otra cosa.

## Compilar y probar (pruebas MÍNIMAS; el usuario no quiere corridas largas innecesarias)
- **Todo Gradle va por el envoltorio, en segundo plano** (`run_in_background`), con la salida a `C:\kw\logs\vt-<paquete>-<n>.log`:
  `bash /c/kw/tools/gradle_slot.sh /c/kw/vt-a/android-native <tareas> > /c/kw/logs/vt-<paquete>-<n>.log 2>&1`
  - El envoltorio espera una de las 2 ranuras globales, que comparte con otra ola, y ya añade `--no-daemon`, `-Xmx4g` y la estrategia en proceso. No las repitas.
  - Mira el final del log (`BUILD SUCCESSFUL` / `BUILD FAILED`) sin bucles de `sleep`.
  - Las herramientas de shell expiran a los 10 min: por eso Gradle va en segundo plano.
- **Tareas permitidas:**
  - `:app:compileBaseDebugKotlin`;
  - `:app:testBaseDebugUnitTest --tests "<filtro>"`, SOLO con las clases nuevas o afectadas que diga tu brief;
  - otras tareas (`assembleBaseDebug`, `compileBaseReleaseKotlin`, `compileBaseDebugAndroidTestKotlin`, instrumentadas), solo si tu brief las nombra.
- **Prohibido:**
  - la suite entera, `clean` y `--stop`;
  - más de un Gradle tuyo a la vez.
- **El primer build del worktree tarda 10–20 min, más la cola.** Agrupa las ediciones antes de compilar y sigue trabajando mientras esperas.
- **Arnés JVM para los paquetes de dominio puro** (VT1a, VT1b, VT2a, VT2b, VT3a, VT3b y VT6a). Para ITERAR usa el arnés `C:\kw\vt-jvm`:
  - compila y prueba solo `domain/biomechanics/**` del worktree;
  - va sin Android y tarda ~1 min una vez que tiene ranura;
  - **va SIEMPRE por `gradle_slot.sh`**, como cualquier Gradle: espera ranura y al menos 3 GB de RAM libre. La RAM del equipo se comparte con otras sesiones, y una corrida fuera de las ranuras las ahoga (14-oct, D25). Nunca `./gradlew` directo.

  Comando (en segundo plano):
  `bash /c/kw/tools/gradle_slot.sh /c/kw/vt-jvm test --offline -PappRoot=C:/kw/vt-a/android-native/app -Dorg.gradle.jvmargs=-Xmx1g --tests "<filtro>" --console=plain > /c/kw/logs/vt-<paquete>-jvm-<n>.log 2>&1`
  - La espera en la cola puede ser larga. Mientras tanto, avanza en lo que no dependa de esa corrida y mira el log después.
  - Haz POCAS corridas: revisa a mano antes, junta varios `--tests` en una sola corrida y nada de corridas de sondeo.
  - Resultados XML en `C:\kw\vt-jvm\build\test-results\test\`.
  - No toques los archivos del arnés: si falla por su configuración, avisa en el informe.
  - Al final, la verificación OFICIAL del paquete se hace UNA vez, por `gradle_slot.sh`: `:app:compileBaseDebugKotlin` y luego `:app:testBaseDebugUnitTest --tests …`.
- **Regla de aislamiento:** `domain/biomechanics/**` solo importa de sí mismo, de `kotlin.*`/`kotlinx.*` y de `java.*`. Nada de otros paquetes `domain/`, ni de `data/`, ni de `android.*`. Si no, el arnés no compila.
- **Carril de dominio** (VT1a, VT1b, VT2a, VT2b, VT3a, VT3b y VT6a), en el worktree `C:\kw\vt-b`:
  - **NO lances Gradle de Android** (ni `gradle_slot.sh` ni `gradlew` de `android-native`): ese worktree no compila la app, para no gastar 4,5 GB de disco.
  - Usa SOLO el arnés propio, `C:\kw\vt-jvm-b`, y siempre por `gradle_slot.sh`:
    `bash /c/kw/tools/gradle_slot.sh /c/kw/vt-jvm-b test --offline -Dorg.gradle.jvmargs=-Xmx1g --tests "<filtro>" --console=plain > /c/kw/logs/vt-<paquete>-jvm-<n>.log 2>&1`
  - Las corridas `:app:…` que aparecen en esos briefs las hace el orquestador, en lote, en `C:\kw\vt-a`.
  - En el informe, pon el comando del arnés y su resultado (los XML de `C:\kw\vt-jvm-b\build\test-results\test\`).
- **Si falla una prueba ajena a tu cambio,** no la persigas: anótala.
- **Estilo de pruebas:**
  - JUnit4 con `org.junit.Assert`; no hay Truth ni MockK;
  - Robolectric 4.12 para lo que toque Android;
  - los generadores y fixtures van en `src/test`, nunca en `main`.
- **Pruebas Compose (Robolectric):** copia los imports de una prueba existente (p. ej. `screens/onboarding/entreno/EntrenoStepsSmokeTest.kt`).
  - `createComposeRule()` viene de `androidx.compose.ui.test.junit4`.
  - Son MIEMBROS y no se importan: `assertExists()`, `assertDoesNotExist()`, `onNode()`, `onAllNodes()`.
  - Son EXTENSIONES y SÍ se importan de `androidx.compose.ui.test.*`:
    - `onNodeWithTag`, `onNodeWithText`, `onAllNodesWithTag`, `performClick`, `assertIsDisplayed`;
    - `assert(matcher)`;
    - dentro de `performTouchInput`, `click()` y `swipe…()`.
  - Repasa los imports antes de lanzar Gradle: cada olvido cuesta una vuelta de cola.

## Pantallas (paquetes VT7, VT8a, VT8b y VT9)
- **Reglas:**
  - las de `docs/screens-v1/briefs/S_COMUN.md` (léelo entero): piezas de `ui/design/`, colores solo de `WizardColors`, `SymbolPalette` y `KpknSurface`, y los componentes de Material prohibidos que lista;
  - el lenguaje de `docs/design/KPKN_UI_V2.md`.
- **Composables nuevos:**
  - sin estado, en `screens/<pantalla>/design/`, uno por archivo;
  - datos simples y lambdas, para que la hoja de render los pinte con datos de ejemplo.
- **Honestidad:**
  - sin dato, la línea no se pinta;
  - las estimaciones llevan «≈»;
  - m/s solo con disco calibrado;
  - el 1RM lleva «orientativo · beta».
- **Textos:**
  - español neutro, sin voseo, en tipo frase;
  - «…» de un carácter;
  - sin emojis.
- **Accesibilidad:**
  - todo lo tocable mide ≥ 48 dp y lleva `Role` y una descripción;
  - las gráficas llevan una descripción resumen;
  - `wizardReducedMotion()` se respeta.
- **Hojas de render:**
  - clase en `src/test/java/com/example/kpkn/render/`, con `@GraphicsMode(NATIVE)`, que escribe PNG en `KPKN_RENDER_DIR`;
  - 360 dp, al 100 % y al 130 %;
  - PNG en `C:/kw/shots/vt/<paquete>`;
  - como mucho 3 rondas de crítica.
  - Mira cada PNG con Read y critícalo con el listón: ¿se entiende de un vistazo? ¿Va cada cifra con su unidad? ¿Se corta algo al 130 %? ¿Parece Material genérico?
- **Emulador:** solo si tu brief lo pide, y SIEMPRE con `python C:\kw\tools\emu_run.py` (con el candado `C:\kw\emu.lock`).
  - Si no está arriba: `python C:\kw\tools\emu_start.py`.
  - Nunca `adb` suelto contra el emulador.
  - **El teléfono del usuario NO está disponible:** no lo toques.

## Git
- **Commits:**
  - pequeños, en español: `feat(technique): …`, `fix(technique): …`, `test(technique): …`, `build(technique): …`;
  - cada uno termina con la línea `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- **Prohibido:**
  - `git add -A`: se añade por rutas explícitas;
  - `stash`, `reset --hard`, `rebase` y `push`.

## Informe final (≤ 400 palabras, sin pegar código)
1. Commits (hash y mensaje).
2. Archivos tocados y firmas públicas nuevas o cambiadas.
3. Qué probaste: el comando exacto y la línea de resultado (`X tests completed, Y failed`); las rutas de PNG si hubo hoja de render.
4. Cambios que necesitas en archivos fuera de tu lista.
5. Riesgos, dudas y lo que no hiciste.
