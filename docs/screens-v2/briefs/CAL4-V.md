# CAL4-V · APRE en vivo

**Worktree:** nuevo desde `feat/screens-v2` cuando la Fase 3 esté integrada (sugerido `C:\kw\u20`, rama `sv2/cal4v`). Trabaja **solo** ahí.

## Qué leer

- `CLAUDE.md` y `docs/screens-v2/DISENO_CONTEXTO.md`.
- `docs/screens-v2/CALIBRADOR_V1.md` §4 (tabla de Mann y bloque APRE) y la decisión del 21-oct.
- `docs/screens-v2/reports/PROG.md` §APRE y riesgo 9; `reports/ED-B.md` (marcador APRE del bloque) y `reports/CAL3.md` (tarjeta V7 y `calibration_decisions`).
- `domain/training/Apre.kt`: `set4LoadKg`, `validReps`, `nextWeekXrmKg`, `block`.
- `domain/training/ProgramAutoregulationEngine.kt` y el canal de propuestas OFF / PROPONER / AUTO.
- El código de la tarjeta V7 y del registro de series (`WorkoutSetRecorder`).

## Qué construir

Al registrar la S3 de un bloque APRE (marcador de ED-B2 o receta `apre-mann`):

1. La tarjeta V7 muestra **«APRE · S4 → X kg (tabla de Mann)»**, con **Aplicar**, **Conservar** y **Revertir**.
2. La carga de S4 sale de `Apre.set4LoadKg`, redondeada al material.
3. Las repeticiones con mala técnica **no cuentan**: se descuentan con `Apre.validReps` antes de calcular.
4. La S4 propone el XRM de la semana siguiente con `Apre.nextWeekXrmKg`, por el carril de propuestas OFF / PROPONER / AUTO. En OFF no propone; en PROPONER pide confirmación; en AUTO aplica.
5. **Sin tocar Room.** Reutiliza `calibration_decisions` con un `referenceSource` o un `scope` que ya existan. Si de verdad hace falta un cambio de esquema, **para** y anótalo para la v32; no lo implementes.
6. **Sin propagación:** el APRE solo afecta al mismo ejercicio en su contexto, como el calibrador.

**Leyes:**
- Refinar, no quitar.
- No inventes opciones ni números: todo sale de `Apre` y de la tabla de Mann.
- Objetivos táctiles de al menos 48 dp; paleta y tipografía de `DISENO_CONTEXTO.md`.
- **Esquema de Room congelado.**

## Pruebas

- Con los números de las tres tablas (APRE 3, 6 y 10): S3 → S4 con `set4LoadKg`, redondeo al material y mala técnica descontada.
- Aplicar / Conservar / Revertir son idempotentes y no tocan series hechas ni cargas fijadas a mano.
- La S4 propone el XRM de la semana siguiente en PROPONER y AUTO; en OFF no.
- Un AMRAP normal o un calibrador que no sea bloque APRE no entra en esta ruta.
- Comprobación explícita de que no cambió el esquema de Room.

## Gradle

- `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "<clases concretas>"`.
- Log en `C:/kw/logs/cal4v-<n>.log`, espera con `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)" C:/kw/logs/cal4v-<n>.log; do sleep 30; done'`, máximo 3 corridas.
- Nunca `--stop`, nunca matar java, nunca `compileBaseDebugAndroidTestKotlin` ni `assembleBaseDebugAndroidTest`.

## Commits e informe

- Commits pequeños en español, `git add` solo de tus rutas, terminados en `Co-Authored-By: Qoder <noreply@anthropic.com>`.
- Informe `docs/screens-v2/reports/CAL4-V.md`: cambios, decisiones, pruebas con números, renders si tocan UI y riesgos.
