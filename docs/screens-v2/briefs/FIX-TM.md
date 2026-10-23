# FIX-TM · Pruebas de TM y 1RM en rojo desde CAL1

**Worktree:** `C:\kw\u13`, rama `sv2/programas`, encima de PROG. Lee `docs/screens-v2/reports/CAL1.md` y `PROG.md`. Trabaja **solo** ahí.

## Síntoma

Dos agentes, cada uno en su pista, vieron pruebas en rojo sin relación con su trabajo:

- `ProgramProtocolEngineTest` (≈ :248 y :268). Comparan `calculateSuggestedLoad` con 1RM × %. Desde CAL1 (`af6e6e546`, `Calculations.kt` ≈ :257) esa función usa la referencia de TM.
- 5 pruebas de TM/plan en `domain.training`, que esperan 90 o 180 y reciben 100 o 200. Encuéntralas con una corrida filtrada de `com.example.kpkn.domain.training.*`.

## Qué hacer

1. **Confirma el origen.** Ejecuta las mismas clases sobre `373729ae0`, o compara con el código anterior a `af6e6e546`.
2. **Decide con evidencia si es un fallo de la app o de la prueba.** La regla de CAL1 es:
   - `reference1RM` significa siempre 1RM;
   - `referenceTrainingMax` es el TM;
   - una serie `PERCENT_1RM` usa el 1RM;
   - una serie `PERCENT_TM` usa el TM, y si no hay TM, 1RM × el porcentaje de la receta.

   Si la app usa el TM en una serie de 1RM, o al revés, **arregla la app**. Si la prueba codificaba la mezcla antigua de significados, adapta la prueba y explica por qué.
3. **Barre los demás consumidores** de `reference1RM` y `referenceTrainingMax`: `PlanMaterializer`, `WorkoutLoadSuggestionController`, `Calculations` y el diálogo «Carga inteligente». No debe quedar ninguna confusión.

## Pruebas

- `:app:testBaseDebugUnitTest` filtrado a `com.example.kpkn.domain.training.*` y `com.example.kpkn.domain.calculations.*`, más las clases afectadas, todo en verde.
- Añade casos explícitos para PERCENT_1RM y para PERCENT_TM, con TM y sin él.

## Reglas

- **Gradle:**
  - solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u13/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`, en primer plano y respetando la cola;
  - solo pruebas filtradas.
- **Prohibido:** `--stop`, matar java, `GRADLE_USER_HOME` paralelo, teléfono, adb y push.
- **Commits** en español, que terminen con `Co-Authored-By: Codex <noreply@openai.com>`.
- **Informe** en `docs/screens-v2/reports/FIX-TM.md`.
