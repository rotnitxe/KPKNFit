# Tarjeta de serie Ref02 · lo que pinta la interfaz nueva (para tests)

Código: `android-native/app/src/main/java/com/example/kpkn/screens/workout/components/SetExecutionCard.kt` (`SetInputCardV2`), `LiveEffortRange.kt`, `SetCardTechniqueBack.kt`, `LiveCalibrationResult.kt`, `GuidedTechniquePanel.kt`.

**Una sola maqueta para todos los modos** (`legacyModeLayout = true`): REPS, AMRAP, RM, SOLO_RPE, TIME, DISTANCE y CUSTOM pintan la misma tarjeta. `LiveModeSetContent` ya no se usa en el frente (sus cabeceras «<modo> · S3», «CARGA UTILIZADA», «TIEMPO REALIZADO · min:s», «METROS RECORRIDOS», «CANTIDAD REALIZADA», «Sin carga fija…», «Registra el resultado realizado con ✓», `live-mode-status`, el cronómetro grande `LiveSetClockHero` y el bloque de RM en línea `LiveRmFocus` desaparecen del frente).

## Kicker (tag `live-card-series-kicker`)
- Texto: «SERIE 3 / 4» (unilateral: «SERIE 3L / 8» o «SERIE 3R / 8»), «AMRAP / 4», «Calibrador / 4», superserie «RONDA 2 · A1 / 3» (parámetro nuevo `supersetLabel`). El total va atenuado en el mismo Text (AnnotatedString).
- Lado (unilateral): caja con borde, texto «Izquierda»/«Derecha», tag `live-card-side`; pulsar cambia de lado.
- Configurar: contentDescription «Configurar esta serie».
- YA NO EXISTEN: la insignia naranja «Mínimo 6 · AMRAP» en el kicker; el TextButton de lado; el bloque «Izquierda (L) / Derecha (R)».

## Carga (`LegacyLoadHero`)
- Rótulo «CARGA» (o «LASTRE» / «AYUDA EXTERNA»); con discos, el rótulo lleva tag `load-plate-label` y la contentDescription con el texto de los discos («por lado: 25 + 10 + 5»), pero el TEXTO visible es «CARGA».
- Cifra: BasicTextField con contentDescription «Carga en kg», tag de la fila `live-card-load-hero`; «kg»; −/+ con contentDescription «Disminuir carga» / «Aumentar carga».
- Referencia (tag `live-card-load-reference`): «100 × 8 · RIR 2» (SIN «Anterior»), «Sin historial anterior», «Lastre · corporal 78 kg», «Asistida · añade tu peso corporal», o en RM «80% · RM 125 kg».
- Botón de historial: icono, contentDescription «Consultar referencia», tag `live-card-reference-history` (en RM abre la hoja «Referencia de RM», tag `live-rm-sheet`, con el `LiveRmFocus` de siempre). YA NO EXISTE el enlace de texto «Historial».

## Cantidad (`ValueStepperBlock`, tag de la cabecera `set_reps_header`)
- Cabecera: «REPETICIONES», «TIEMPO», «DISTANCIA» o la unidad propia en mayúsculas. A la derecha: en AMRAP/calibrador un texto tag `amrap-purpose-badge` con «Mínimo 6», «Sin mínimo» o «0 reps permitido» (calibrador sin mínimo); en TIME un botón tag `live-card-set-clock` (contentDescription «Iniciar el cronómetro de la serie» / «Pausar el cronómetro de la serie») con «m:ss»; el chip de vídeo sigue igual.
- REPS: cinco botones redondos (tag de la fila `live-card-reps`, cada uno `live-card-reps-<n>`, contentDescription «<n> repeticiones», `selected` en el actual) con las repeticiones actuales en el centro (dos menos y dos más). YA NO EXISTE la rueda `WorkoutHorizontalWheelPicker` en el frente.
- TIME / DISTANCE / CUSTOM: campo (tag de la fila `live-card-quantity-input`, BasicTextField con contentDescription «Cantidad registrada»), unidad «s» / «m» / la unidad propia, y −/+ de 1 con contentDescription «Reducir cantidad» / «Aumentar cantidad».

## Esfuerzo (`LiveEffortRange`)
- Tag del bloque `live-effort-range`; «ESFUERZO»; valor tag `live-effort-value`: «Sin marcar» o «RIR 2» / «RPE 8» SIN la palabra («· exigente» ya no se pinta). Tocarlo abre la hoja «Esfuerzo realizado» (tag `live-effort-sheet`).
- Pista: tag `EffortSliderTag` (= "effort_fire_slider"), con la MISMA semántica de antes (contentDescription `effortSpokenDescription`, p. ej. «Esfuerzo RIR 2, exigente»; stateDescription; ProgressBarRangeInfo; SetProgress). El margen interior de la pista es 12,5 dp (antes 18/23): para tocar una marca con `effortCenterPx` usa `12.5f * densidad`.
- Marca del plan: tag `effort-plan-mark`. Leyenda: primera marca («RIR 10»), «RIR 2 · plan», y «Fallo» (tag `live-effort-failure`, pulsable).
- YA NO EXISTEN en la tarjeta: el fuego (`effort-fire-track`, `effort-fire-halo`), el aviso dentro del deslizador.

## Detalle (tag `live-card-detail`)
- «Parciales, ayuda y ROM ›» o el resumen «+2 parciales · 1 con ayuda · ROM 90%» (SIN «Nota»). Abre «Detalle del registro».
- El aviso de esfuerzo o de cantidad sustituye a la fila: texto tag `live-card-inline-error`, «Marca tu esfuerzo antes de registrar.» (antes `EffortRequiredHint` dentro del deslizador).

## Pie
- `live-card-actions`: «Avanzado», «Modo visual» y el historial (44 dp de zona táctil).

## Serie con incidencia (tag `live-card-failed`)
- «SERIE 3», «!», «Error de ejecución», «100 kg × 8» + «El fallo muscular se registra en esfuerzo.», «Revisar motivo» (tag `live-card-failed-reason`, abre la hoja `live-card-failed-sheet` con «Molestia articular», «Fallo técnico», «Carga excesiva»), «Recuperar serie» (tag `live-card-failed-revert`, hace lo que hacía «Revertir»). YA NO EXISTEN: «Marcaste esta serie como fallida», el campo de motivo en la tarjeta, «Pérdida de agarre», «Revertir».

## Avanzado (`SetCardTechniqueBack`, tag `technique-body`)
- «Serie 3» con ‹ (contentDescription «Volver a la serie»), «AVANZADO».
- Pestañas «Normal», «Drop», «Rest-pause», «Myo» (tags `technique-tab-0..3`, `selected`). Antes «Drop-set» y «Myo-reps».
- «CADENA DE LA SERIE» + «N tramos ›» (tag `TechniqueChainTag`; abre la hoja «Cadena de la serie», tag `technique-chain-sheet`, con una fila por tramo y «Añadir tramo»).
- Conmutador (tag `TechniqueLinkTag`): «PRINCIPAL» / «DROP n» / «TRAMO n» y «100 × 8»; flechas «Tramo anterior» / «Tramo siguiente».
- Con un tramo abierto: campos «Carga · kg» + «Repeticiones» (drop) o «Pausa · s» + «Repeticiones» (rest-pause/myo), cada BasicTextField con su contentDescription igual a la etiqueta. En el principal: «Carga, repeticiones y esfuerzo de la serie principal se conservan.».
- Enlaces: «Añadir nota» / «Ver nota» (hoja «Nota de la serie», tag `technique-note-sheet`, campo «Observación»), «Añadir tramo», «Error de ejecución» (hoja tag `technique-failure-sheet` con «Marcar error de ejecución» / «Quitar error de ejecución», tag `technique-mark-failed`).
- Pie «Volver al registro» (tag `technique-back-to-record`): guarda la técnica (llama a onCompleteTechniques con los tramos) o, sin técnica, vuelve (onFlipBack).
- YA NO EXISTEN: el círculo «Hecho» (`TechniqueDoneToggle`) ni el registro automático al marcar todo; «ASÍ SE VE UN DROP-SET/REST-PAUSE/MYO-REPS»; «Sugerido … kg»; «Añadir drop/pausa/mini-serie»; «Elige drop-set o rest-pause»; «NOTA DE LA SERIE» con campo en línea; la píldora «Error de ejecución» que conmutaba; el texto de myo «Activación cerca del fallo…»; el atajo de la foto del récord.

## Resultado de calibración (`LiveCalibrationResult`, tag `calibration-result`)
- Kicker «CALIBRACIÓN» / «AMRAP» / «APRE» y estado (tag `calibration-status`): «Registrada», «Aplicada», «Plan conservado», «Revertida».
- «RESULTADO», «8 reps», «100 kg · RIR 2»; propuesta en kg (tag `apre-set4-result`); explicación («Propuesta para S3, S4. <motivo>», o la resolución con tag `calibration-resolution`); sin decisión: «Mínimo cumplido» / «Mínimo superado · 9/6» / «Mínimo no alcanzado · 4/6» / «Sin mínimo» + el mensaje del programa.
- Botones: «Mantener el plan» (tag `calibration-keep`), «Revertir» (tag `calibration-revert`), «Corregir registro» (tag `calibration-edit`, 48 dp), pie «Aplicar propuesta» (tag `calibration-apply`, contentDescription con la etiqueta de siempre, p. ej. «Aplicar a S3–S4», 48 dp) o «Continuar» (tag `calibration-continue`, 48 dp). YA NO EXISTEN: «Editar», «Conservar», «Capacidad ≈ …», «Referencia … · historial», «Subir/Mantener/Bajar las cargas», filas «S3 a → b kg», «Calibrador · S3».

## Técnica guiada (`GuidedTechniquePanel`, tag `guided-technique-sheet`)
- «Técnica en curso», «Rest-pause · tramo 1 de 2» (o «Drop-set», «Myo-reps»), reloj «3 s», «Pausa de 15 s. Después podrás registrar el siguiente tramo.», campos «Carga · kg» / «Repeticiones», «Principal: 100 kg × 8 · RIR 2», «Terminar técnica con lo realizado» (misma alerta «¿Terminar la técnica?»). YA NO EXISTEN: «REST-PAUSE 1/2», «DROP-SET 1/2», «Pausa fija de…», «Sin descanso. Reduce la carga…», «Sugerido: …», «Peso sugerido».
