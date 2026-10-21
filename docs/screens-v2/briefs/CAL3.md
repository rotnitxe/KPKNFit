# CAL3 · Calibrador en vivo: persistencia, decisión y resultado (V7)

Worktree `C:\kw\u11`, rama `feat/screens-v2`, sobre CAL1, CAL2, LV-A y LV-B.

## Lectura previa

- `C:\kw\u11\CLAUDE.md`
- `docs/screens-v2/CALIBRADOR_V1.md`: §1 (tabla `calibration_decisions`), §2.5–2.6, §3 y la fila CAL3 de §6.
- `docs/screens-v2/reports/CAL1.md`, `CAL2.md`, `LV-A.md` y `LV-B.md`.
- `docs/screens-v2/reports/PROTO_R6B_amrap_vivo.md`.
- El tablero **V7-VivoAMRAP.dc.html** (ábrelo en un navegador local), en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\`.

## Qué construir

1. **Room v29 → v30.**
   - Crea la tabla `calibration_decisions` con los campos de CALIBRADOR_V1 §1.
   - Escribe su migración, su DAO y su repositorio en `data/`.
   - Añade una prueba de migración.
2. **Enganche tras registrar** una serie con propósito CALIBRATOR (`WorkoutSetRecorder` ≈ :983).
   - Sustituye el `amrapCalibrationMessage` roto, la regla 1,10 / 0,90, por la llamada al motor de CAL2 con la referencia resuelta (historial, calibraciones aceptadas, 1RM o PR declarados).
   - La decisión se guarda como PROPOSED.
   - Todo en `Dispatchers.IO`, y la interfaz se actualiza en Main.
3. **Resultado en la tarjeta (V7)**, con todo dentro de la tarjeta de 312 × 425.
   - **Antes de registrar:** una insignia «Mínimo N · Calibrador» o «· AMRAP».
   - **Calibrador:** panel con capacidad, referencia y cambio, y la lista «S2 60 → 62,5 kg». Botones **Aplicar a S2–S4** y **Conservar**. Seis estados: sube, mantiene, baja, primera referencia, no aplicada con su motivo, y próximas sesiones con «Propuesta guardada para la próxima sesión de este ejercicio».
   - **AMRAP normal:** mínimo superado o no alcanzado, «Registrado en tu historial y récords» y el efecto real de la regla del programa:
     - si la receta tiene regla de TM, una vista previa pura de `ProgramAutoregulationEngine` muestra la propuesta y respeta OFF / PROPONER / AUTO;
     - si no la tiene, «Sin regla de progresión: queda registrado».
4. **Aplicar, conservar y revertir.**
   - **Aplicar** cambia solo las series pendientes compatibles. No toca las hechas ni las fijadas a mano.
   - Es idempotente: nunca multiplica dos veces sobre una carga ya ajustada.
   - Las series ajustadas llevan la marca «calibrada» y la línea de LV-A lo refleja.
   - **Conservar** deja la decisión en KEPT.
   - **Revertir** restaura las cargas «antes» de las series aún no hechas y deja la decisión en REVERTED.
   - Si se edita y se vuelve a registrar la serie calibradora, la decisión anterior pasa a SUPERSEDED y no se acumulan ajustes.
5. **Persistencia y retorno.**
   - Al cerrar y volver a abrir la app, la decisión y las cargas aplicadas siguen ahí.
   - La sesión siguiente de ese ejercicio y contexto parte de la referencia aceptada:
     - en sesiones libres y programas KPKN, como base de la carga sugerida;
     - en programas gestionados con TM, por el canal de propuestas (OFF / PROPONER / AUTO), nunca por encima de la receta.
6. **Resumen final de la sesión:** una línea por calibración con su resolución.
7. **Nunca se toca otro ejercicio** (decisión del usuario).

## Pruebas

- **Unitarias y Robolectric:**
  - migración v29 → v30;
  - repositorio;
  - enganche del registro para cada estado;
  - aplicar solo a series pendientes no fijadas;
  - idempotencia;
  - conservar y revertir;
  - reemplazo de la decisión (SUPERSEDED);
  - reapertura tras reiniciar;
  - consumo de la referencia en la sesión siguiente;
  - respeto de OFF / PROPONER / AUTO;
  - resumen.
- **androidTest:** un recorrido de V7 en el marco real con render a 880 y a 740 de alto. Solo compilarlo; lo corre el orquestador.
- **Comandos:**
  - `:app:compileBaseDebugKotlin`;
  - `:app:compileBaseDebugAndroidTestKotlin`;
  - las pruebas filtradas de `data`, `domain.calibration` y `screens.workout`;
  - al final, `:app:assembleBaseDebug :app:assembleBaseDebugAndroidTest -I C:/kw/tools/dbg-suffix.init.gradle`.

## Reglas

Las de siempre:
- Gradle solo con `gradle_slot.sh`, en primer plano y con `-Xmx8g`.
- Sin `--stop` ni matar java, sin teléfono ni adb, nada fuera de `C:\kw\u11`, sin push.
- Commits en español con `Co-Authored-By: Codex <noreply@openai.com>`.
- No toques el editor (CAL4 va con ED-B) ni las reglas de cada programa (CAL5).

## Informe

En `docs/screens-v2/reports/CAL3.md`.
