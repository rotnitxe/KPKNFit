Eres un agente de KPKN Fit (Android, Kotlin). Tu worktree es `C:/kw/u13` (en bash: /c/kw/u13), rama `sv2/programas`. Tu prefijo de logs de Gradle es `inv`. Tu modelo para la línea Co-Authored-By es: DeepSeek v4.1 Flash.

## Tarea: `RecipeContractInventoryTest` en rojo tras PROG

En `android-native/app/src/test/.../data/protocols/RecipeContractInventoryTest.kt` fallan dos pruebas:
- «El inventario del contrato creció: 108 hallazgos superan el techo 100»;
- «Los hallazgos del contrato tras las exenciones crecieron: 21 superan el segundo techo 18 (C10 21)».

El crecimiento lo trajo el paquete PROG: las reglas de 5/3/1, nSuns, Juggernaut, GZCLP y J&T, el AMRAP de registro en Cube, Lilliebridge y Rippler, las plantillas `power-12-3/16-4/20-5` y el protocolo nuevo `apre-mann`. Están en `docs/screens-v2/reports/PROG.md` y en los commits `57c63d08d..59757a1fd`.

Pasos:
1. Lee la prueba y el contrato que evalúa. Saca la lista **exacta** de los hallazgos nuevos. Puedes compararla con el estado anterior a PROG (`git show 373729ae0:<ruta>`), o añadir temporalmente un `println` a la prueba (que no se commitea).
2. Por cada hallazgo nuevo:
   - si es un **error del dato** (receta mal declarada), corrige la receta, sin cambiar la regla del programa que describe `PROG.md`;
   - si es **deliberado**, porque la fuente lo exige (por ejemplo, la serie «+» de 5/3/1 o el AMRAP de APRE), declara una exención justificada y con fuente, con el mismo formato que las exenciones existentes. La fuente sale de `docs/screens-v2/audits/RES1_calibrador_fuentes.md`.
   - **No subas los techos** de la prueba.
3. Una sola corrida de Gradle:
   ```
   :app:testBaseDebugUnitTest --tests "com.example.kpkn.data.protocols.*" --tests "com.example.kpkn.domain.training.*"
   ```
   Repite solo si algo falla, con un máximo de 3 corridas en total.
4. Escribe `docs/screens-v2/reports/INV-PROG.md` con cada hallazgo, la decisión (corregido o exento), su motivo y el resultado de las pruebas. Commitéalo junto con los cambios.

Notas:
- `ExerciseCatalogImageCoverageTest` y las pruebas `domain.biomechanics.golden`/`metrics` fallan por el entorno: el checkout es disperso y los finales de línea alteran los hashes. Ignóralas.
- `docs/screens-v2/briefs/` es de solo lectura para ti.
## Reglas para este agente (obligatorias)

- Escribe en español: commits, informe y comentarios nuevos.
- Trabaja **solo** en tu worktree. No toques ninguna otra carpeta de `C:\kw`, ni el checkout principal, ni `artifacts/`.
- Puede que haya cambios sin commitear de un agente anterior en esta misma tarea. Revísalos con `git status` y `git diff`, y continúa desde ahí: no los descartes.
- **No termines tu turno mientras quede trabajo o una corrida de Gradle sin terminar.** Ninguna notificación te va a despertar: tienes que vigilar tú el log.
- **Gradle**, solo con:
  `bash /c/kw/tools/gradle_slot.sh /c/kw/u13/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas> > C:/kw/logs/inv-<n>.log 2>&1 &`
  - Espera con:
    `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)" C:/kw/logs/inv-<n>.log; do sleep 30; done'`
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
  - terminados en la línea `Co-Authored-By: DeepSeek v4.1 Flash <noreply@opencode.ai>`, en vez de la que diga el brief.
- **Esquema de Room congelado:** no toques entidades, migraciones, `schemas/` ni la versión de `KpknDatabase`.
- **Refinar, no quitar:** ninguna función existente desaparece. No inventes opciones ni números.
- **Al final:**
  - escribe el informe que pide el brief;
  - commitéalo;
  - termina con un resumen de 10 líneas como máximo: commits, pruebas con su resultado, renders y riesgos.
