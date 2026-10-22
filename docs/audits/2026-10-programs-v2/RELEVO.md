# RELEVO · Ola «Programas y periodización» (2026-10-22)

Hola — soy el agente que ejecutó el plan `lively-squishing-bonbon.md` hasta la T-14. Este es el relevo para que sigas **el trabajo completo** (Ola A restante y Olas B–E). Lee primero `ESTADO.md` de esta misma carpeta: tiene los commits, las desviaciones ya aprobadas y los datos congelados que no puedes romper.

## Dónde y cómo trabajar

- **Worktree**: `C:/Users/valen/Documents/KPKNFit/.claude/worktrees/kpkn-programs-audit-periodization-7abb9c` (rama `claude/kpkn-programs-audit-periodization-7abb9c`, base master `8ac57c9d4`). Trabaja SOLO ahí; no cambies de rama; **sin `git push`**.
- **Compilar y testear JVM** (motor, recetas, datos):
  ```
  "C:\Program Files\Git\bin\bash.exe" /c/kw/tools/kpkn_jvm.sh /c/Users/valen/Documents/KPKNFit/.claude/worktrees/kpkn-programs-audit-periodization-7abb9c "-PtestInclude=<globs>" --tests "<ClaseFQN>"
  ```
  - `bash` NO está en el PATH de cmd: usa la ruta completa de Git Bash de arriba.
  - `testInclude` = rutas relativas a `src/test/java` separadas por comas, y hay que añadir POR ARCHIVO los fixtures: para los tests de `data/protocols` hace falta `com/example/kpkn/domain/training/CatalogCompositionTestSupport.kt` y `com/example/kpkn/domain/templates/CatalogV2TestFixture.kt`. Un glob demasiado amplio (p. ej. `data/protocols/**`) arrastra tests que no compilan en este arnés (`TrainingPlanRecipeJsonCompatTest` usa `dbJson` de Room).
  - Éxito = `[kpkn_jvm] RESUMEN: … 0 fallos, 0 errores` o `BUILD SUCCESSFUL` (si el RESUMEN no aparece, confirma con los XML en `C:\kw\kpkn-jvm-kpkn-programs-audit-periodization-7abb9c\build\test-results\test\`).
- **Tests [ANDROID]** (pantallas/Compose) — solo por la ranura:
  ```
  "C:\Program Files\Git\bin\bash.exe" /c/kw/tools/gradle_slot.sh /c/Users/valen/Documents/KPKNFit/.claude/worktrees/kpkn-programs-audit-periodization-7abb9c/android-native :app:testBaseDebugUnitTest --tests "<ClaseFQN>"
  ```
  Tardan 10–25 min. Lánzalos en **segundo plano** y confirma con los XML en `android-native/app/build/test-results/testBaseDebugUnitTest/`. El capturador de salida en segundo plano de esta sesión a veces deja el log vacío: fíate del exit code y de los XML.
- **Un solo carril a la vez** (JVM o Android): correrlos en paralelo ha reventado corridas por falta de RAM. Si `gradle_slot` dice «sigo esperando turno», es la cola compartida con otras sesiones (u11, mfp…): espera, no matenada. `bash /c/kw/tools/gradle_cola.sh` muestra el estado.
- **Nunca** `./gradlew` directo. **Nunca** tocar `K/data/db/**` (Room congelado v28), `K/domain/auge/**`, `NativeWorkoutProgressionRuntime`, `Calculations.kt`, `LoadSuggestionEngine`, `VolumeCalculator`, `VolumeView`, `screens/sessioneditor/**` (salvo lo que una tarea nombre), `build.gradle.kts` ni los scripts de `/c/kw/tools`.

## Reglas de trabajo del plan (resumen)

- Una tarea por vez, en orden: Ola A T-50→T-53, luego B (T-15→T-26), C (T-30→T-35, T-80→T-84, T-70→T-74), D (T-100→T-124), E (T-40→T-43, T-60→T-62). No empieces una tarea si la anterior quedó con dudas abiertas.
- Commit por tarea: `git add <rutas explícitas>` (nunca `-A`) y mensaje `feat|fix|test|docs(<área>): <qué> [T-xx]` con el pie del plan. Verificación verde ANTES del commit. Tope de 3 reintentos por verificación; si algo no está en el plan, **no lo inventes**: para y repórtalo.
- Código: `val`, funciones puras en `domain/` (sin `android.*`, fechas por parámetro), textos al usuario en español con tilde y coma decimal, campos nuevos en modelos `@Serializable` SIEMPRE con default (JSON antiguo debe seguir decodificando), cambios aditivos en archivos existentes (sin renombrar ni reformatear), un test nuevo por motor nuevo con JUnit 4 y nombres sin `; . : [ ] / \ < >`.
- Informe por tarea en el formato fijo del plan (TAREA / Archivos / Cambio / Verificación / Commit / No hecho-dudas).

## Decisiones del usuario que ya están tomadas (22-oct)

1. **Protocolo de autor = fidelidad al pie de la letra** (con exenciones justificadas y con fuente si las reglas genéricas chocan). **Lo que lleve sello KPKN = reglas de volumen y sesión de KPKN** (se corrige el dato, sin exenciones). Está guardado en taste.
2. **T-13**: los top set RPE llevan sus anclas de %1RM implícito en las series de respaldo (convención L-05); el top set es RPE puro. Ya hecho.

## Trucos aprendidos (te ahorrarán ciclos)

- Los contratos de receta muerden en cadena: al tocar una receta revisa C1/C2/C5/H6/H7/H9/H11/H11b/W2/BLOCK/suelo de 1RM y las pruebas «%TM en fases previas vs %1RM en pico». El desglose está en `ESTADO.md` §4.
- Los planes propios KPKN (`KPKN_NATIVE`) no pueden declarar exenciones: corrige la receta. Los de autor sí, con `RecipeCompositionExemption(rule, scope, justificación ≥25, sourceUrl)` y ámbitos acotados (`w9`, `w*/Día`, `recipe`).
- Cuando cambien las exenciones de un protocolo, actualiza **las matrices congeladas** `ProtocolExemptionMatrixTest` y `ExemptionScopeMigrationTest`. Cuando cambie una receta, sube `contentVersion` y actualiza `the_recipes_changed_in_b_s6…`.
- `PercentBasis.effective1RmPercent` devuelve null para bases RPE; los picos y bloques miden con porcentajes. `PercentResolver`/`Prescriber.percentOfOneRm` son la tabla de equivalencias de la app.
- `PlanCatalogEditorialContractTest` fija `subtitle == technicalSubtitle`: no cambies `PlanLabels.subtitle` (usa `scopeLabel`, que ya existe).
- La cola tiene memoria (`REUTILIZO`): mismo árbol + mismas tareas en <2 h no repite; `KPKN_SLOT_FORCE=1` fuerza.

## Estado al cierre

Commits `7f7f618f0`…`58ae30509` (9 tareas, ver tabla en `ESTADO.md`). Worktree limpio y en verde (91 tests de recetas/contratos, 0 fallos). Última tarea completada: **T-14**. La siguiente es **T-50** (`ProgramProgressReport`, motor puro). Ánimo y sigue el plan al pie de la letra: hasta ahora, cada vez que el plan y el contrato chocaron, «corregir el dato con criterio documentado» ha sido la respuesta que mantiene todo verde.
