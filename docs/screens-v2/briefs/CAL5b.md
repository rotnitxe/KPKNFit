# CAL5b · Training Max por ejercicio

**Worktree:** nuevo desde `feat/screens-v2` cuando la Fase 3 esté integrada (sugerido `C:\kw\u21`, rama `sv2/cal5b`). Trabaja **solo** ahí.

## Qué leer

- `CLAUDE.md` y `docs/screens-v2/DISENO_CONTEXTO.md`.
- `docs/screens-v2/CALIBRADOR_V1.md` §5b completo, con el punto 6 (FIX-TM: sin TM se pide, no se infiere).
- `docs/screens-v2/reports/FIX-TM.md` (`percentLoadKg`, `percentLoadReferenceKg`, `percentOfOneRm`, `missingPercentReferences`).
- `docs/screens-v2/reports/PROG.md`, `reports/CAL3.md` y `reports/ED-B.md`.
- `PowerliftingProfile`, `PlanMaterializer`, `ProgramAutoregulationEngine`, la preparación de sesión y el detalle del programa.

## Qué construir

1. **Una referencia por ejercicio para todos los principales**, no solo los cuatro de `PowerliftingProfile`:
   - 1RM declarado, test, e1RM o calibración aceptada;
   - TM = 1RM × % del programa;
   - con origen e historial.
   - **Sin Room nuevo.** Si hace falta una tabla, anótalo para la v32 y usa el almacenamiento serializado que ya exista. No cambies el esquema.
2. **El TM se ve donde se usa:**
   - tarjeta en vivo: «85 % de tu TM (120) → 102,5 kg»;
   - foco del editor y modo RM, con la base 1RM o TM según `loadBasis`;
   - detalle del programa, con el press militar incluido y el historial de cada TM con su motivo (asistente, test, ciclo, nSuns, Juggernaut, calibración).
3. **Cambiar un TM rematerializa las semanas pendientes**:
   - conserva las cargas fijadas a mano;
   - sin el botón «Re-materializar».
4. **La preparación de sesión pide solo el TM que falta** («Falta tu TM de press militar») y propone 1RM × % del programa, que la persona confirma (§5b.6). Nunca lo infiere en silencio.
5. `PROMOTE_TM` compara con el 1RM, no con TM × 1,05.

**Leyes:**
- Refinar, no quitar.
- No inventes números ni orígenes.
- Objetivos táctiles de al menos 48 dp; paleta y tipografía de `DISENO_CONTEXTO.md`.
- **Esquema de Room congelado; cualquier cambio va a la v32 y se anota, no se implementa.**

## Pruebas

- Migración de los cuatro TM de `PowerliftingProfile` a la referencia por ejercicio.
- Cambiar un TM recalcula solo las series no fijadas; las fijadas a mano se conservan.
- La sesión pide solo el TM necesario y no pide los que ya existen.
- `PROMOTE_TM` con 1RM y con TM × 1,05: solo sube con el primero.
- El TM se ve en vivo, en el editor y en el detalle, con su motivo.
- Una serie %TM sin TM queda pendiente y no se rellena sola.

## Gradle

- `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "<clases concretas>"`.
- Log en `C:/kw/logs/cal5b-<n>.log`, espera con `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)" C:/kw/logs/cal5b-<n>.log; do sleep 30; done'`, máximo 3 corridas.
- Nunca `--stop`, nunca matar java, nunca `compileBaseDebugAndroidTestKotlin` ni `assembleBaseDebugAndroidTest`.

## Commits e informe

- Commits pequeños en español, `git add` solo de tus rutas, terminados en `Co-Authored-By: Qoder <noreply@anthropic.com>`.
- Informe `docs/screens-v2/reports/CAL5b.md`: cambios, almacenamiento elegido, pruebas, renders y riesgos.
