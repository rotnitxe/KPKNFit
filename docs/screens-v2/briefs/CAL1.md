# CAL1 · Contrato de AMRAP y calibrador (paquete 1 del plan)

Worktree `C:\kw\u11`, rama `feat/screens-v2`. Lee primero:
- `C:\kw\u11\CLAUDE.md`
- `docs/screens-v2/PLAN_IMPLEMENTACION.md`
- `docs/screens-v2/CALIBRADOR_V1.md`, sobre todo §0, §1, §4 y la fila CAL1 de §6
- `docs/screens-v2/audits/AUD3_amrap.md` §2–§4, §9-1 y §9-5
- `docs/screens-v2/audits/INV1_amrap_programas.md` §3 y §6-9/10/11

## Objetivo

Dejar un único contrato de datos para las series AMRAP y calibradoras. Es la base de los paquetes siguientes (motor, vivo, editor y programas). Sin cambios visuales salvo los imprescindibles para no romper lo existente: la interfaz nueva llega en CAL3 y CAL4.

## Qué hacer

1. **`SetPurpose { NONE, AMRAP, CALIBRATOR }` en `ExerciseSet`.** Sustituye a `isAmrap`, `isCalibrator` e `IntensityMode.AMRAP` como fuente de verdad.
   - **Lectura compatible:** JSON y Room viejos con `isCalibrator = true` → CALIBRATOR; con `isAmrap` o `IntensityMode.AMRAP` → AMRAP.
   - **Escritura:** decide si los campos viejos se siguen escribiendo para compatibilidad y documéntalo.
   - Retira `Exercise.isCalibratorAmrap`, que nadie lee.
   - `BlockTransitionEngine` (descarga) pasa a usar el propósito.
   - Todos los consumidores de INV1 §3 leen el propósito: `PlanMaterializer`, `NativeWorkoutProgressionRuntime`, `PlanDetailsSummary`, `WorkoutSetRecorder`, `SetExecutionCard`, `ProgramAutoregulationEngine`, `InlineSetRow` y las recetas (`SetRecipe.amrap` → AMRAP).
2. **`amrapMinimumReps: Int?`** es el **único** mínimo efectivo: positivo, o nulo con el significado explícito de «sin mínimo».
   - Lo usan la interfaz, el registro y la progresión.
   - Arregla AUD3 §9-1: 5 reps con mínimo 4 nunca cuentan como cortas por el máximo legado de 6.
   - Cero y negativos no pasan la validación del plan.
3. **`calibrationScope: SESSION | NEXT_SESSIONS`**, solo con CALIBRATOR. Por defecto, SESSION.
4. **`CompletedSet`** guarda:
   - el propósito realizado y el mínimo efectivo;
   - las reps completas y las parciales;
   - la reserva o el fallo declarados;
   - el tipo de carga, la carga original y la normalizada;
   - el lado y la clave de contexto.

   Antes de crear campos nuevos, revisa los que ya existen: `amrapPerformed`, `amrapMinimumReps`, `amrapBelowMinimum` y otros.
5. **Preservar el AMRAP al editar y al guardar.** Corrige la normalización del editor que quita `isAmrap` en modos distintos de AMRAP, por ejemplo en recetas en modo RM con la última serie AMRAP (INV1 §6-10: `SEVN:385-386`, `SESH:450-458, 474-479, 598-604`, `SERE:384-391`). Cambiar el modo **explícitamente** sigue pudiendo quitarlo; **normalizar** no.
   - AMRAP desactivado en vivo: no vuelve a aparecer por la posición de la serie, salvo en logs viejos sin evidencia (AUD3 §8, contrato común).
6. **Importador.** «5+», «3x5+», «1+» y «AMRAP» marcan `SetPurpose.AMRAP` con su mínimo (INV1 §6-11, `ProgramImportParser`), además de la nota.
7. **Separar el TM de `reference1RM`.** Crea un campo `referenceTrainingMax` para las series `PERCENT_TM`. `reference1RM` vuelve a significar solo 1RM.
   - Corrige los escritores: `PM:1059-1061, 1220-1225` y el diálogo «Carga inteligente» (`EED:156, 171`).
   - Corrige los lectores: `explicitTrainingMaxLoad` en `CALC:253-257` y `computeSessionImprovementAdjustment` en `WLSC:691-699`.
   - Migra los datos guardados de forma segura: si una serie `PERCENT_TM` tiene `reference1RM`, ese valor era el TM.
8. **Estimador común sin el +2,5 % por etiqueta AMRAP.** Quita el bonus de `calculateHybrid1RM` y de `amrapOverride` en `WPHE:550` y `CALC:60-71`, con un número de versión del estimador. Historial, récords, rendimiento, sugerencias y editor dan la misma capacidad para la misma serie y el mismo contexto. Los datos guardados no se reescriben; si algo depende de la versión, decide y documenta.
9. **Room.** Si cambias el esquema, sube `KpknDatabase` de v29 a v30 con su migración y su prueba de migración. Si no hace falta, no la subas: la tabla de decisiones llega en CAL3.

## Pruebas obligatorias (nuevas, en `app/src/test`)

1. Una receta RM con la última serie AMRAP sobrevive a editar otro campo y a guardar.
2. 5 reps con mínimo 4 nunca cuentan como cortas en el registro ni en la progresión.
3. Un AMRAP desactivado no reaparece por la posición.
4. La lectura de JSON viejo convierte `isAmrap`/`isCalibrator` en el propósito correcto, y `Exercise.isCalibratorAmrap` se ignora sin romper.
5. El importador marca «5+» y «3x5+» con su mínimo.
6. Series `PERCENT_TM` con `referenceTrainingMax`: carga sugerida y mejora de sesión correctas; la migración de datos viejos es correcta.
7. Misma serie → misma capacidad en historial, récords, homologación y sugerencias, sin bonus.
8. La migración de Room, si la hay.

## Reglas

- Gradle solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u11/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`, en primer plano.
- Nunca `--stop` ni matar java. Sin teléfono ni adb. Nada fuera de `C:\kw\u11`. Sin push.
- Tareas: `:app:compileBaseDebugKotlin`, `:app:compileBaseDebugAndroidTestKotlin` y `:app:testBaseDebugUnitTest --tests <filtros>` de tus pruebas y de las existentes afectadas (busca las que tocan AMRAP, PlanMaterializer, ProgramAutoregulation, importador y editor de series). No ejecutes la batería completa.
- Commits pequeños por tema, en español, con `Co-Authored-By: Codex <noreply@openai.com>`.
- No toques la interfaz más allá de adaptar lecturas y escrituras al propósito (la etiqueta del editor puede seguir igual).
- No implementes el motor del calibrador (CAL2) ni la tabla de decisiones (CAL3).

## Informe final (en español)

Escríbelo en `docs/screens-v2/reports/CAL1.md`:
- qué cambió, por archivo;
- las decisiones de compatibilidad;
- las pruebas, con el comando y su resultado;
- los riesgos;
- lo que dejas para CAL2 y CAL3.
