# QA de LV-B en el emulador con las medidas del Z Flip 5 (2026-10-21)

- **Emulador:** `ZFlip5_QA` (360 × 880 dp, barra de estado de 45 dp).
- **APK:** el de LV-B (`c6d4da4d3`), copiado en `C:\kw\shots\emu-lvb\apk`.
- **Registros:**
  - `C:\kw\logs\emu-lvb-instr.log`: paquete `screens.workout` completo.
  - `emu-lva-baseline.log` y `emu-lqa1-baseline.log`: las mismas clases con los APK de LV-A y de LQA1.
- **Renders:** `C:\kw\shots\emu-lvb\renders\`.

## Paquete `screens.workout` en el dispositivo: 192 pruebas

| Resultado | Cantidad |
|---|---:|
| Pasan | 172 |
| Omitidas por suposición | 7 |
| Fallan | 13 |

**Omitidas:**
- 4 pertenecen a fixtures congelados de las revisiones 14 y 15.
- 3 están en `WorkoutWarmupInventoryIntegrationTest`, que exige el «usuario QA 10».

| Fallo | ¿Ya fallaba con LQA1? | ¿Y con LV-A? | Lectura |
|---|---|---|---|
| `SessionRevisionFlowDeviceTest#storyFromTheHeader…` | — | Sí | Ya está en LV-AF |
| `SessionRevisionFlowDeviceTest#exerciseMenuOpensRmAndQuickRecords…` | — | Intermitente | Ya está en LV-AF (hoja «Registrando…») |
| `SetExecutionCardUiTest#guidedDropFinalCommitRetryDoesNotAppendAnotherRow`, `#guidedRestPauseFinalCommitRetry…`, `#guidedSkipRetryKeepsExplicitEmptyCommit…`, `#guidedRestPauseCountdownSkipRetry…` | **No** | **Sí** | **Regresión** introducida entre LQA1 y LV-A (AUD2, CAL1, CAL2 o LV-A). El `SetAdvancedFeedback` del reintento del commit guiado de drop-set o rest-pause no coincide con lo esperado |
| `SetExecutionCardUiTest#suggestedLoadUsesChip…`, `#scheduledVolumeReplacedKeepsCard…`, `#advancedOptionsCtaUsesNewCopy…` | Sí | Sí | Venían de antes |
| `RelatorActionAckDeviceTest` × 2 (`awaitCopy` sin texto en 5 s) | Sí | Sí | Venían de antes |
| `WorkoutRestModalUiTest#rest_modal_shows_manual_badge…` | Sí | Sí | Venía de antes |
| `Revision14PhotosDeviceTest#captureProductionFinish…` («Failed to inject touch input») | Sí | Sí | Venía de antes; puede ser propio del emulador |

## Revisión visual de los renders

**Bien:**
- el deslizador con degradado, destello y chispas (`lv-b-failure-880`);
- los cinco modos, con la tarjeta de 312 × 425 y la línea V1;
- el reloj de Tiempo.

**Detalles:**
1. Los títulos largos de la cabecera se cortan sin elipsis («Paseo del granjer», «Elevaciones latera»).
2. RM a 740 dp: con el editor de % abierto, la tarjeta tapa la parte baja del texto del relator, y el deslizador de % usa un amarillo puro que no es de la paleta (energía #F7CF73).
3. Solo RPE: los chips de reps arrancan en «0 1 2»; deberían centrarse en un valor útil (objetivo, última serie o 8).
4. Distancia y Personalizado: «Objetivo 40 m» queda pegado a «METROS RECORRIDOS» / «CANTIDAD REALIZADA»; falta separación.
