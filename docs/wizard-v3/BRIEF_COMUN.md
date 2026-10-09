# Brief común de los agentes · Wizard v3

Aplica a TODOS los paquetes de la ola «Wizard v3» (24 correcciones del wizard). Cada paquete tiene además su brief propio en
`docs/wizard-v3/briefs/<ID>.md`. El plan aprobado por el usuario está en `C:\Users\valen\.claude\plans\memoized-spinning-hickey.md`
(léelo SOLO en la sección de tu punto: ahí están el diagnóstico con rutas y líneas, el diseño y el cableado).

## Entorno
- Repo KPKN Fit (Android, Kotlin, Jetpack Compose). Tu worktree es el que diga tu brief (`C:\kw\<nombre>`, checkout parcial) en la
  rama `v3/<paquete>`, creada desde `feat/wizard-v3`. **Trabaja solo ahí**; jamás toques `C:\Users\valen\Documents\KPKNFit` ni otro
  worktree.
- La app vive en `android-native/app/src/main/java/com/example/kpkn/`; las pruebas en `android-native/app/src/test/java/com/example/kpkn/`.
  Lee primero `CLAUDE.md`, `docs/WIZARD_PAGINA_LARGA.md` (reglas del wizard) y, si tocas Entreno, `docs/WIZARD_ENTRENO_V2.md`.
  `domain/` no importa nada de `android.*`.
- Solo flavor **Base**. Idioma de UI, comentarios y KDoc: **español**, breve; imita el estilo y la densidad de comentarios del código vecino.
- **Antes de editar un archivo, léelo entero** (o al menos la clase o función completa que vas a tocar). No inventes APIs: busca la firma
  real con Grep antes de llamarla. Si algo del brief no cuadra con el código, no improvises: elige la opción más conservadora y
  anótalo en el informe.
- No toques archivos fuera de tu lista permitida. Si necesitas un cambio en otro archivo, descríbelo en el informe (qué, dónde, por qué)
  en vez de hacerlo; el orquestador lo integra.

## Compilar y probar (pruebas MÍNIMAS: el usuario no quiere pruebas largas innecesarias)
- **Todo Gradle va por el envoltorio** `C:/kw/tools/gradle_slot.sh`, en segundo plano (`run_in_background`) y con la salida a un log en
  `C:\kw\logs\v3-<paquete>-<n>.log`. Espera una de las 2 ranuras globales y ≥ 3 GB de RAM libres, y ya añade
  `--no-daemon -Dorg.gradle.jvmargs=-Xmx4g -Pkotlin.compiler.execution.strategy=in-process` (no los repitas). Formato:
  `bash /c/kw/tools/gradle_slot.sh /c/kw/<tu-worktree>/android-native <tareas> > /c/kw/logs/v3-<paquete>-<n>.log 2>&1`.
- **La app ya no tiene flavors** (se retiró Health Connect en `master`): las tareas son SIN «Base» — `:app:compileDebugKotlin`,
  `:app:testDebugUnitTest`, `:app:assembleDebug`. Donde un brief diga `compileBaseDebugKotlin` o `testBaseDebugUnitTest`, usa la tarea sin
  «Base». El build ejecuta `scripts/stability/optimize_resources.py` (ya está en tu checkout parcial; necesita `python` con Pillow, que hay).
- Tareas permitidas: `:app:compileDebugKotlin` y `:app:testDebugUnitTest --tests "<filtro>"` con SOLO las clases nuevas o
  afectadas que diga tu brief. **Nunca** la suite entera, nunca `androidTest`, nunca `./gradlew --stop`, nunca `clean`, **un solo**
  Gradle tuyo a la vez.
- El primer build de un worktree tarda 10–20 min (más con la cola). Agrupa tus ediciones antes de compilar; mientras esperas, sigue
  leyendo o editando. Las herramientas de shell expiran a los 10 min: por eso Gradle va en segundo plano. Para saber si terminó, mira
  el final del log (`BUILD SUCCESSFUL` / `BUILD FAILED`), sin sondeos en bucle con `sleep`.
- Si una prueba ajena a tu cambio falla, no la persigas: anótala en el informe.

## Hojas de render (paquetes visuales)
- Para ver dibujos sin teléfono: pruebas Robolectric con `@GraphicsMode(NATIVE)` que escriben PNG en la carpeta de la variable
  `KPKN_RENDER_DIR` y se saltan sin ella (patrón: `src/test/java/com/example/kpkn/render/WizardRenderSheetTest.kt`; para símbolos, la
  hoja compartida `SymbolRenderSheet` que crea el paquete B1). Comando:
  `KPKN_RENDER_DIR=C:/kw/shots/v3/<paquete> bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native :app:testDebugUnitTest --tests "<clase>"`.
- Mira cada PNG con Read y critícalo contra el brief (proporciones, legibilidad a 60 dp, nada cortado). Máximo **3 rondas** de ajuste;
  si no llegas, entrega lo mejor y explica qué falta.
- Teléfono y emulador: **no** los uses salvo que tu brief lo pida expresamente.

## Diseño (no negociable)
1. **Símbolos e ilustraciones animadas sobre la página negra; nunca tarjetas** (ni cajas con borde, ni filas con radio, ni
   resplandores de color, ni degradados de borde, ni sombras de color). Se permite vidrio neutro muy tenue (`WizardColors.glassFill`,
   `glassBorder` de 1 dp) donde el brief lo diga.
2. Estética KPKN: tinta cálida `#F2EEE6` (`WizardColors.text`) sobre negro; acentos de módulo (`SymbolPalette`): músculo `#F49A6E`,
   columna `#8FB2FF`, energía `#F7CF73`, mente `#C9B8FF`, ok `#43D18C`. Syne en lo importante, Inter en lectura; solo roles de
   `WizardTypography`, mínimo 13 sp. Nada de aspecto «Material por defecto».
3. Movimiento con sentido y respeto a `wizardReducedMotion()` (con movimiento reducido: cuadro final estático, sin bucles). Un reloj
   compartido por escena, sin asignaciones por cuadro (reutiliza `Path`).
4. Accesibilidad: objetivos táctiles ≥ 48 dp, `Role`/`contentDescription`/`stateDescription` reales, contraste suficiente.
5. Textos: títulos de pregunta ≤ 44 caracteres, subtítulos ≤ 100; español neutro, sin género gramatical cuando se pueda.
6. **Nada de UI de relleno**: todo control escribe un dato real y ese dato lo lee el motor.

## Git
- Commits pequeños en español (`feat(wizard): …`, `fix(wizard): …`, `test(wizard): …`), cada uno terminado con la línea
  `Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>`. Nunca `git add -A`: añade por rutas explícitas. Sin `stash`,
  `reset --hard`, `rebase` ni `push`.

## Informe final (≤ 400 palabras)
1. Commits (hash y mensaje). 2. Archivos tocados y firmas públicas nuevas o cambiadas. 3. Qué probaste: comando exacto y la línea de
resultado (`X tests completed, Y failed`), rutas de PNG si hubo hoja de render. 4. Cambios que necesitas en archivos fuera de tu lista.
5. Riesgos, dudas y lo que no hiciste. Sin pegar código.
