# Brief común de los agentes · Rings v4

Aplica a TODOS los paquetes de la ola «Rings v4» (auditoría y mejora de RINGS/AUGE: cálculos, modelos de drenaje y recuperación,
aprendizaje del usuario). Cada paquete tiene además su brief propio en `docs/rings-v4/briefs/<ID>.md`.

El plan aprobado por el usuario es `docs/audits/2026-10-rings-v4/PLAN.md` (en tu worktree). **Léelo SOLO en lo que cite tu brief**:
los hallazgos `F-xx` (sección 4), el diseño (6.x), tu fila de la sección 7 y los anexos B (cifras) y C (pruebas). Los informes
completos de los 7 agentes de la auditoría están en `docs/audits/2026-10-rings-v4/informes/` (los cita tu brief cuando hacen falta;
sus referencias `archivo:línea` son de `master` @ `00431a27d`, que para AUGE coincide con la base de la ola).

Rutas cortas: `K/` = `android-native/app/src/main/java/com/example/kpkn/` · `T/` = `android-native/app/src/test/java/com/example/kpkn/`.

## Entorno
- Repo KPKN Fit (Android nativo: Kotlin, Compose, Room). Tu worktree y tu rama los dice tu brief (`C:\kw\<nombre>`, checkout parcial,
  rama `r4/<id>` ya creada desde `feat/rings-v4`). **Trabaja solo ahí**; jamás toques `C:\Users\valen\Documents\KPKNFit` ni otro worktree.
- Lee primero `CLAUDE.md`. `domain/` no importa nada de `android.*` y **no añade imports dominio→datos nuevos** (los modelos de
  `data/models/` que `domain/auge` ya usa sí se pueden usar).
- Solo flavor **Base**. Idioma de comentarios, KDoc y textos de UI: **español**, breve; imita el estilo y la densidad de comentarios del
  código vecino. Kotlin idiomático: `val`, `data class`, sin `!!` nuevos salvo en pruebas.
- **Antes de editar un archivo, léelo entero** (o al menos la clase o función completa que vas a tocar). No inventes APIs: busca la
  firma real con Grep antes de llamarla. Si algo del brief no cuadra con el código, no improvises: elige la opción más conservadora y
  anótalo en el informe.
- No toques archivos fuera de tu lista permitida. Si necesitas un cambio fuera de ella, descríbelo en el informe (qué, dónde, por qué)
  en vez de hacerlo; el orquestador lo integra.
- El disco de la máquina está casi lleno: no copies carpetas del repo, no dejes salidas ni volcados grandes dentro del repo y borra tus
  archivos temporales.

## Reglas propias de AUGE (no negociables)
1. **Los números no cambian a escondidas.** Salvo que tu brief lo declare, tu cambio no puede mover ninguna cifra visible; si una prueba
   existente cambia de valor, no la «arregles» sin más: cuéntalo en el informe con el valor de antes y el de después.
2. **Ninguna prueba nueva usa el reloj real.** Pasa siempre `nowOverride`/`nowOverrideMs`/`nowMs` y una zona fija
   (`ZoneId.of("UTC")`, nunca `ZoneId.systemDefault()` implícito). Si una función de motor no admite reloj inyectado
   (`AugeClock.nowMs()`, `System.currentTimeMillis()`, `ZoneId.systemDefault()` por defecto), **no la uses en esa prueba** y anótalo en
   el informe como candidata al paquete 1.7.
3. **Doubles hasta el borde de la UI.** No introduzcas `toInt()` nuevos sobre baterías (usa `roundToInt()`); el paquete 1.5 retira los
   existentes.
4. **Sin migración Room.** El caché de AUGE es JSON dentro de `AugeAdaptiveCacheEntity`; no subas la versión de `KpknDatabase`.
5. **El sueño no es un problema.** No lo reportes ni lo arregles (la decisión de no trackear sueño está tomada). Tampoco reabras: el
   cuestionario de 24 h, los sliders de AUGE en Ajustes, Health Connect ni iOS/backend.
6. **No borres `automaticBatteryAtAnchor`** (es el único dato supervisado) ni cambies el JSON persistido sin que tu brief lo pida
   (los datos de usuarios existentes deben seguir decodificando).
7. Los pines F0 (Energía 80/92/97, Columna 93/96/99, Pecho 72/80/85/97) de `AugeRingCharacterizationTest` solo cambian cuando un
   paquete numérico lo declare, con tabla antes/después.

## Compilar y probar (pruebas MÍNIMAS y por filtro)
- **Todo Gradle va por el envoltorio** `C:/kw/tools/gradle_slot.sh`, en segundo plano (`run_in_background`) y con la salida a un log en
  `C:\kw\logs\r4-<paquete>-<n>.log`. Espera una de las 2 ranuras globales (compartidas con otras sesiones) y ≥ 3 GB de RAM libres, y
  ya añade `--no-daemon -Dorg.gradle.jvmargs=-Xmx4g -Pkotlin.compiler.execution.strategy=in-process` (no los repitas). Formato:
  `bash /c/kw/tools/gradle_slot.sh /c/kw/<tu-worktree>/android-native <tareas> > /c/kw/logs/r4-<paquete>-<n>.log 2>&1`
- **La app tiene flavors** (`base` y `health`, `android-native/app/build.gradle.kts:29-36`): usa SIEMPRE las tareas `Base`:
  `:app:compileBaseDebugKotlin` y `:app:testBaseDebugUnitTest --tests "<filtro>"`. Ignora cualquier brief antiguo que diga lo contrario.
- **Filtros por NOMBRE DE CLASE, nunca por nombre de archivo.** Tres archivos definen varias clases de prueba
  (`AugeRingsAudit2026Test.kt` define 13, `AugeRingsGuideAndMonitorTest.kt` 6 y `AxialLoadMonitorTest.kt` 2): un `--tests` con el
  nombre del archivo no las selecciona y el lote pasaría en vacío. Cada `--tests` va en su propio argumento y con el paquete completo
  (`--tests "com.example.kpkn.domain.auge.NombreDeLaClase"`).
- **Tras cada ejecución de pruebas, comprueba que realmente corrió algo:** desde la raíz de tu worktree,
  `python -I docs/rings-v4/tools/count_tests.py` (añade `--clases` para el detalle). Si el número de pruebas es 0 o menor que el
  esperado, el filtro está mal: corrígelo antes de informar. Gradle borra los XML al empezar cada ejecución, así que el recuento es el
  de la última invocación.
- Tareas permitidas: las dos de arriba con SOLO las clases nuevas o afectadas que diga tu brief. **Nunca** la suite entera, nunca
  `androidTest`, nunca `./gradlew --stop`, nunca `clean`, **un solo** Gradle tuyo a la vez.
- El primer build de un worktree tarda 10–25 min (más con la cola). Agrupa tus ediciones antes de compilar; mientras esperas, sigue
  leyendo o editando. Las herramientas de shell expiran a los 10 min: por eso Gradle va en segundo plano. Para saber si terminó, mira
  el final del log (`BUILD SUCCESSFUL` / `BUILD FAILED`), sin sondeos en bucle con `sleep`.
- Si Gradle lleva más de 40 min sin escribir nada nuevo en su log, **no lo mates ni lo repitas**: dilo en el informe (el orquestador lo
  supervisa). Una prueba ajena a tu cambio que falle no se persigue: se anota en el informe con su nombre y el primer renglón del mensaje.
- **Tope de 3 iteraciones** por paquete (compilar → probar → corregir). Si al tercer intento no cierra, entrega lo que haya, explica la
  causa raíz y propón el arreglo con su coste: decide el orquestador.

## Git
- Commits pequeños en español (`test(auge): …`, `fix(auge): …`, `docs(auge): …`), cada uno terminado con la línea de firma de TU modelo
  que indique tu brief (`Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>` o `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`).
- Nunca `git add -A` ni `git add .`: añade por rutas explícitas. Sin `stash`, `reset --hard`, `rebase`, `push` ni `checkout` de otras ramas.
- Antes de cada commit: `git status --short` y comprueba que solo aparecen archivos de tu lista permitida.

## Informe final (≤ 400 palabras, en español, sin pegar código)
1. Commits (hash y mensaje). 2. Archivos tocados y firmas públicas nuevas o cambiadas. 3. Qué probaste: comando exacto y la línea de
`count_tests.py` (pruebas/fallos/omitidas), y las rutas de los logs. 4. Cambios que necesitas en archivos fuera de tu lista.
5. Cifras que cambiaron respecto a la base (antes → después), si las hubo. 6. Riesgos, dudas y lo que no hiciste.
