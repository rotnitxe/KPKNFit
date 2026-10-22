## Reglas para este agente (obligatorias)

- Escribe en español: commits, informe y comentarios nuevos.
- Trabaja **solo** en tu worktree. No toques ninguna otra carpeta de `C:\kw`, ni el checkout principal, ni `artifacts/`.
- Puede que haya cambios sin commitear de un agente anterior en esta misma tarea. Revísalos con `git status` y `git diff`, y continúa desde ahí: no los descartes.
- **No termines tu turno mientras quede trabajo o una corrida de Gradle sin terminar.** Ninguna notificación te va a despertar: tienes que vigilar tú el log.
- **Lee antes `C:/kw/tools/GRADLE_GUIA.md`** (cola v10):
  - Las pruebas de `domain/**` y `data/protocols|programs|models|sessions` van por el **carril JVM**: `bash /c/kw/tools/kpkn_jvm.sh <wt> "-PtestInclude=com/example/kpkn/domain/<paquete>/**" --tests "<FQN>"`, que tarda de 30 s a 8 min.
  - El carril Android solo sirve para `screens/**`, `render/**` y Room, con **clases concretas** en `--tests`.
  - No pidas `compileBaseDebugAndroidTestKotlin` ni `assembleBaseDebugAndroidTest`.
- **Gradle Android**, solo con:
  `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas> > C:/kw/logs/<prefijo>-<n>.log 2>&1 &`
  - Espera con:
    `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)" C:/kw/logs/<prefijo>-<n>.log; do sleep 30; done'`
    y repítelo hasta que salga BUILD SUCCESSFUL o BUILD FAILED. La cola es global y una corrida puede tardar en empezar más de una hora: es normal, sigue esperando.
  - Nunca dos corridas tuyas a la vez.
  - Nunca `--stop`, nunca matar java, nunca editar `build.gradle.kts` para filtrar pruebas: usa `--tests`.
  - Lee los fallos en `android-native/app/build/test-results/testBaseDebugUnitTest/*.xml` o en el log.
  - **Ahorra corridas:** antes de lanzar, revisa bien que el código compile (imports, firmas, tipos). Haz como mucho **3** corridas en total, juntando la compilación y las pruebas en cada una.
- `ExerciseCatalogImageCoverageTest` falla siempre en estos worktrees porque falta `catalog/exercises/images/`. Es del entorno: ignóralo.
- Sin teléfono, sin adb, sin emulador y sin push.
- **Commits:**
  - pequeños, en español;
  - `git add` solo de tus rutas, nunca `git add -A`;
  - terminados en la línea `Co-Authored-By: <TU_MODELO> <noreply@opencode.ai>`, en vez de la que diga el brief.
- **Esquema de Room congelado:** no toques entidades, migraciones, `schemas/` ni la versión de `KpknDatabase`.
- **Refinar, no quitar:** ninguna función existente desaparece. No inventes opciones ni números.
- **Al final:**
  - escribe el informe que pide el brief;
  - commitéalo;
  - termina con un resumen de 10 líneas como máximo: commits, pruebas con su resultado, renders y riesgos.
