# LV-BF · Arreglos de la QA de LV-B y pruebas instrumentadas en rojo (después de LV-AF)

Lee `docs/screens-v2/reports/QA_EMU_LVB.md`. Los registros están en `C:\kw\logs\emu-lvb-instr.log`, `emu-lva-baseline.log` y `emu-lqa1-baseline.log`, y los renders en `C:\kw\shots\emu-lvb\renders\` (todo solo lectura).

1. **Regresión de los commits guiados.** Cuatro casos de `SetExecutionCardUiTest`: `guidedDropFinalCommitRetry…`, `guidedRestPauseFinalCommitRetry…`, `guidedSkipRetryKeepsExplicitEmptyCommit…` y `guidedRestPauseCountdownSkipRetry…`.
   - Pasaban con el APK de LQA1 (`cacde0aae`) y fallan desde LV-A.
   - Localiza el commit con un diff de `SetExecutionCard*`, `WorkoutSetRecorder` y `SetAdvancedFeedback` (candidatos: AUD2, CAL1, CAL2 y LV-A).
   - **Arregla la app** si se perdió comportamiento: el reintento de un commit guiado no debe añadir otra fila ni perder drop-sets o rest-pauses. Si el cambio era intencionado y aprobado, adapta la prueba conservando su invariante.
2. **Siete pruebas instrumentadas que ya fallaban antes**:
   - `SetExecutionCardUiTest`: `suggestedLoadUsesChip…`, `scheduledVolumeReplacedKeepsCard…` y `advancedOptionsCtaUsesNewCopy…`;
   - `RelatorActionAckDeviceTest` × 2;
   - `WorkoutRestModalUiTest#rest_modal_shows_manual_badge…`;
   - `Revision14PhotosDeviceTest#captureProductionFinish…`.

   Para cada una, decide con evidencia si la app perdió una función (y la restauras) o si la prueba quedó desfasada por un diseño aprobado (y la actualizas sin rebajar la invariante).
   - Si `Revision14Photos` depende de una revisión congelada o de inyectar toques que el emulador no permite, documéntalo.
   - Usa Robolectric donde puedas reproducirlo. Las instrumentadas solo se compilan; las corre el orquestador.
3. **Detalles visuales de LV-B:**
   - elipsis o tamaño adaptable para los títulos largos de la cabecera;
   - RM a 740 dp sin tapar el relator, y deslizador de % con los colores de la paleta;
   - reps de Solo RPE centradas en un valor útil;
   - separación entre «Objetivo» y la etiqueta en Distancia y Personalizado.

**Pruebas:** `screens.workout.*` en verde, `androidTest` compilado y APK `.dbg`.

**Reglas:** las de siempre. Informe en `docs/screens-v2/reports/LV-BF.md`.
