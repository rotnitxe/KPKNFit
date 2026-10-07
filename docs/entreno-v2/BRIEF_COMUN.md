# Brief común de los agentes · Entreno v2

Aplica a TODOS los paquetes de la ola. Cada paquete tiene además su brief propio en `docs/entreno-v2/briefs/<ID>.md`.

## Entorno
- Repo KPKN Fit (Android, Kotlin, Compose). Tu worktree es `C:\kw\<nombre>` (checkout parcial) en la rama `ent/<nombre>`,
  creada desde `feat/wizard-entreno-v2`. **Trabaja solo ahí**; jamás toques `C:\Users\valen\Documents\KPKNFit`.
- La app vive en `android-native/app/src/main/java/com/example/kpkn/`. Lee primero `CLAUDE.md`, `docs/WIZARD_ENTRENO_V2.md`
  (contrato de la ola) y `docs/WIZARD_PAGINA_LARGA.md` (reglas del wizard). `domain/` no importa nada de `android.*`.
- Solo flavor **Base**. Idioma de UI, comentarios y KDoc: **español**, tono breve; imita el estilo del código vecino.
- No toques archivos fuera de tu lista permitida. Si necesitas un cambio en un archivo compartido, descríbelo en tu
  informe (qué, dónde, por qué) en vez de hacerlo; el orquestador lo integra.

## Compilar y probar (la máquina es justa: 31 GB de RAM compartidos)
- **Todo Gradle va por el envoltorio** `C:/kw/tools/gradle_slot.sh`, en segundo plano y con la salida a un log: espera una de las 2 ranuras globales y ≥ 3 GB de RAM libres, y ya añade
  `--no-daemon -Dorg.gradle.jvmargs=-Xmx4g -Pkotlin.compiler.execution.strategy=in-process` (no los repitas). Formato:
  `bash /c/kw/tools/gradle_slot.sh /c/kw/<tu-worktree>/android-native [-I C:/kw/tools/dbg-suffix.init.gradle] <tareas>`; por ejemplo `:app:assembleBaseDebug` o
  `:app:testBaseDebugUnitTest --tests "<filtro>"`.
- **Nunca** `./gradlew --stop`, **nunca** `clean`, **un solo** Gradle tuyo a la vez. El primer build de un worktree tarda 10–20 min (más con la cola): agrupa tus ediciones antes de
  compilar, corre solo los tests de lo que tocaste (`--tests`), no la suite entera, y mientras esperas turno sigue editando o leyendo (no sondees).
- Las herramientas de shell expiran a los 10 min: lanza Gradle con `run_in_background` y redirige la salida a un log.

## Emulador (solo para paquetes visuales)
- Emulador compartido `emulator-5554`: SOLO con `python C:\kw\tools\emu_run.py` (candado; lee su docstring). Pantalla
  1344×2992 a 480 dpi. Capturas en `C:\kw\shots\<nombre>\`; míralas con Read y critícalas.
- El APK debug se instala como `com.example.kpkn.dbg` (componente `com.example.kpkn.dbg/com.example.kpkn.debug.<Actividad>`).
- Vistas previas: `app/src/debug/AndroidManifest.xml` + actividades en `app/src/debug/java/com/example/kpkn/debug/`
  (patrón: `git show wiz/s9:android-native/app/src/debug/AndroidManifest.xml`). Van en un commit aparte `debug: …`.

## Teléfono real (dispositivo PRINCIPAL de pruebas visuales; orden expresa del usuario, 2026-10-07)
- **El usuario ordenó usar su teléfono para las pruebas** (Galaxy Z Flip5 por depuración inalámbrica, 1080×2640 a 480 dpi = 360 dp de ancho). El emulador se cae con frecuencia y queda solo como respaldo si el teléfono no está disponible (desconectado o con la batería baja); no inicies sesiones largas ahí. Es su teléfono personal: la app de producción `com.example.kpkn` tiene sus datos reales.
- SOLO con `python C:\kw\tools\phone_run.py` (mismo formato que `emu_run.py`, candado `C:\kw\phone.lock`; lee su docstring). Solo instala y abre el APK debug con sufijo `.dbg` (`com.example.kpkn.dbg`), nunca toca la app de producción, despierta la pantalla y la vuelve a apagar al terminar si estaba apagada. Nada de `adb` directo, `pm clear`, `uninstall` ni ajustes del sistema.
- **No cambies ajustes del teléfono** (ni `font_scale`, ni `animator_duration_scale`, ni tamaño o densidad de pantalla): para probar letra al 130 %, movimiento reducido o anchos distintos usa extras del arnés/actividad de depuración (`fontScale`, `reducedMotion`, `widthDp`, como ya hacen las vistas previas de U1–U5) o las pruebas de Robolectric.
- Úsalo para TODAS las comprobaciones visuales (a 360 dp reales, fluidez, desenfoque, ritmo del scroll, gestos). Sesiones agrupadas y breves; la pantalla puede estar encendida y desbloqueada porque el usuario la está usando, así que no la dejes en un estado raro: termina siempre volviendo a una pantalla neutra (`--actions "key:3"` = Inicio) salvo que hayas de dejar algo abierto. Si el teléfono está bloqueado, las actividades de depuración deben pedir `setShowWhenLocked(true)` y `setTurnScreenOn(true)`. Capturas en `C:\kw\shots\<nombre>\phone\`.
- Gestos largos (pulsación larga y arrastre): `phone_run.py` solo hace toques y deslizamientos; para arrastres con dedo mantenido usa `input motionevent DOWN/MOVE/UP` como `C:\kw\shots\u5\drag_probe.py`, adaptado al teléfono con su mismo serial y sin saltarte el candado `C:\kw\phone.lock`.

## Diseño (no negociable)
1. **Símbolos e ilustraciones animadas sobre la página negra; nunca tarjetas** (ni cajas con borde, ni filas con radio, ni
   resplandores de color, ni degradados de borde, ni sombras de color). Se permite un disco de vidrio neutro muy tenue
   (blanco 6–8 %) con filete uniforme de 1 dp (`WizardColors.glassFill/glassBorder`).
2. Estética KPKN / Liquid Glass: tinta cálida `#F2EEE6` sobre negro; acentos de módulo músculo `#F49A6E`, columna `#8FB2FF`,
   energía `#F7CF73`, mente `#C9B8FF`, ok `#43D18C`. Syne (`WizardFonts.display`) en lo importante, Inter (`WizardFonts.body`)
   en lectura; solo roles de `WizardTypography`, mínimo 13 sp. Nada de aspecto «Material por defecto».
3. Movimiento con sentido y respeto a `wizardReducedMotion()` (con movimiento reducido: cuadro final estático, sin bucles).
   Un reloj compartido por escena, sin asignaciones por cuadro (reutiliza `Path`).
4. Accesibilidad: objetivos táctiles ≥ 48 dp, `Role`/`contentDescription`/`stateDescription` reales, contraste suficiente.
5. Textos: títulos de pregunta ≤ 44 caracteres, subtítulos ≤ 100; español neutro, sin género gramatical cuando se pueda.
6. **Nada de UI de relleno**: todo control escribe un dato real y ese dato lo lee el motor.

## Git
- Commits pequeños en español (`feat(entreno): …`, `test(entreno): …`, `refactor(entreno): …`), cada uno terminado con la línea
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Nunca `git add -A`: añade por rutas explícitas. Sin `stash`,
  `reset --hard` ni `push`.

## Informe final (≤ 400 palabras)
Archivos entregados y firmas públicas, qué probaste (tests, capturas con ruta), cambios que necesitas en archivos
compartidos, riesgos y pendientes. Sin pegar código.
