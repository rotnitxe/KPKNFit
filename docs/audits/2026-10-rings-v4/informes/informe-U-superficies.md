# Informe del agente U · superficies (auditoría RINGS/AUGE, 2026-10-12)

> Entregado por un subagente Haiku 5.5 (tipo Explore, solo lectura) y copiado **sin editar**. La verificación, las correcciones de severidad y las comprobaciones propias están en el anexo A del plan (`PLAN.md` de esta carpeta). Las referencias `archivo:línea` son de `master` @ `00431a27d`.

# Auditoría de superficies de usuario RINGS/AUGE (solo lectura)

Notación: **K/** = `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\java\com\example\kpkn\`. Las citas usan `K/ruta:línea`. Los documentos previos están en `C:\Users\valen\Documents\KPKNFit\docs\audits\`. No edité nada, no ejecuté Gradle ni tests; solo usé python de una línea para comprobar cálculos.

Veredicto corto: hay **dos fallos numéricos o de afirmación en silencio** (sin datos mostrados como 100 %, y truncado de Float) y **una pérdida de datos del usuario** al recrear la Activity (plegado del Z Flip, rotación o muerte del proceso). Los textos del diálogo de anillos exageran en varios puntos.

## A. Mapa de alcance, procedencia y pares

**A.1 Procedencia de cada número (Q1)**

| Superficie | Fuente exacta | Unidad / redondeo |
|---|---|---|
| Torre Home | `ringScore` (`K/data/models/AugeModels.kt:174-178`) ← `dashboard.channels[].score` ?: `batteries.*` | Entero 0-100; `toInt()` sobre Float, trunca (`K/screens/home/HomeRingsSection.kt:191`) |
| Etiquetas y chips Home | Mismo valor (`HomeRingsSection.kt:263, 283`) | «NN%» sin espacio |
| Cabecera mini (`MiniRingsWidget`) | Mismo, sin control de carga (`K/screens/home/HomeScreen.kt:949-951`) | Igual que torre |
| Detalle del anillo | `channelSnap.score` (`K/screens/home/RingDetailSheet.kt:43-44, 68`) | «NN %» con espacio, sin truncar |
| Chips por músculo (tarjeta de sesión) | `perMuscle` vía `lookupMuscleValue`; faltante = 100 (`K/screens/home/HomeSessionSection.kt:432, 474`) | Entero; ver U-12 |
| Readiness, torre | Energía = `ringScore(SYSTEM)` y Columna = `ringScore(STRUCTURE)` (`K/screens/workout/WorkoutScreen.kt:506-513`); Músculos = media de semillas de la sesión (`K/screens/workout/components/WorkoutReadinessSheet.kt:138-141`) | Entero; `toInt()` sobre la media |
| Readiness, etiquetas bajo la torre | `muscular` (global), `neural`, `spinal` (`WorkoutReadinessSheet.kt:443-445`) | «NN%»; ver U-09 |
| Readiness, líneas por canal | `dashboard.channels[].band` y `action` (`WorkoutReadinessSheet.kt:252-258`) | Banda; sin cobertura (U-01) |
| Veredicto → relator | `calculateDailyReadiness`: `score = dashboard.overallScore` (`K/domain/auge/AugeRecoveryEngine.kt:1512-1520`) → `RelatorContextBuilder.kt:105, 590-591` | Entero; ponderado 0,40/0,35/0,25 |
| Cierre (sliders y torre) | `postSessionPreview.neural` y `.spinal` (mostrados); `perMuscle` (`K/screens/workout/WorkoutFinishHost.kt:218-225`); Músculos = `recalibratedCanonicalMuscularBattery` (`:163-186`, `toInt()` en `:185`) | Entero; no coincide con Home (H6) |
| Editor de sesión | `buildAugeSummary` (`K/screens/sessioneditor/SessionEditorAugeComputation.kt:66`): **drenaje previsto** por canal | «%» de consumo, no batería |
| Detalle de programa | `MuscleOvertrainingStatus.isOvertrained` solo cambia el color (`K/screens/programdetail/components/VolumeView.kt:733`) | Sin cifra |
| Wizard, torre y líneas | Cobertura de la vista previa (`K/screens/onboarding/rings/RingsResultStep.kt:245, 289-290, 362`) | «NN %» o «Sin calibrar» |
| Voz («¿cuánto drenaje llevo?») | `liveDrainSummary` (`K/screens/workout/WorkoutViewModel.kt:2174-2195`) → `K/screens/workout/WorkoutVoiceCommandHandler.kt:1925` | **Drenaje** de la sesión, entero |
| Asistente de sesión | Drenajes `K/domain/sessionassistant/SessionAssistantEngine.kt:534-535, 969-971`; horas `:806` | «drenaje %» |
| Widgets | Ninguno de anillos (solo `NutritionQuickActionWidget`) | — |

**A.2 Pares de superficies con cifras distintas (Q2)**

| Par | Ejemplo numérico | Ref. |
|---|---|---|
| Torre Home vs detalle del mismo anillo | ringScore 53: torre y chip «52%», detalle «53 %» | U-02 |
| Músculos Home vs readiness | Sesión con Pectorales/Tríceps/Deltoides a 60: readiness 60; Home (global) 85 | U-09 |
| Torre vs etiqueta en la misma hoja readiness | Tras editar un músculo: torre 60, etiqueta 85 | U-09 |
| Columna: valor fijado vs mostrado (techo) | Piso articular 60 y guardia 70: máximo mostrable = 0,5·100 + 32,5 = 82,5 → muestra **83** aunque el usuario fije 90 (`K/domain/auge/AugeRecoveryEngine.kt:290-298, 304-316`). Ya confirmado; cifras nuevas | Techo |
| Energía Home vs voz | Home «Energía 60 %» (batería) vs voz «40 por ciento del sistema nervioso» (drenaje) | U-14 |
| «Energía alta» en Home vs editor | Home: recuperada. Editor: «Tu Energía ya va alta» con drenaje 88 | U-07 |
| Titular vs canal visible | Titular «Buen estado para entrenar» (73) vs Energía 40 «Baja» | U-06 |
| Relator vs Home | Relator «Óptimo para entrenar (100)» vs torre «Sin datos» | U-01 |
| Detalle vs torre sin datos | Detalle «100 % · Alta» vs torre «Sin calibrar» | U-01 |
| Cierre vs Home (Músculos) | Ya confirmado en el brief (H6); sin cifras nuevas | — |

**Unidades:** no aparece «pts» en la UI (solo en el documento previo). El mismo «%» significa batería restante (anillos) o drenaje consumido (voz, editor, asistente). Ver U-14.

**A.3 Entradas del usuario (Q3)**

| Control | Rango y paso | Semilla | Qué sobrevive a recrear la Activity | Gesto |
|---|---|---|---|---|
| Readiness Energía/Columna (`WorkoutReadinessSheet.kt:599-638`) | 0-100, continuo, `toInt()` (`:629`) | `ringScore` (`WorkoutScreen.kt:506-513`) | Sí: `rememberSaveable` (`:122-124, 142-143`) | Slider Material; sin saltos |
| Readiness músculos (`MinimalMuscleSlider`) | 0-100; toque y arrastre **absolutos** (`MinimalMuscleSlider.kt:76-88`) | `perMuscle` ?: 100 (`WorkoutScreen.kt:487-505`) | **No**: `userEditedMuscles` es `remember` (`WorkoutReadinessSheet.kt:144`); U-03 | Un toque cambia el valor sin deshacer |
| Hoja de readiness | `dismissible = false` (`:181-184`) | — | — | No se puede cancelar: hay que confirmar |
| Cierre Energía/Columna (`AdjustableRingCompact`) | Arrastre vertical, 1 punto = 1,25 px (`WorkoutReadinessSheet.kt:687`) | `preview` (`WorkoutFinishHost.kt:218-219`) | **No**: `remember(seed)` (`:226-230`); U-04 | 100 puntos ≈ 125 px de arrastre |
| Cierre músculos | Igual que readiness | `preview.perMuscle` (`:220-225`) | No (U-04) | Absoluto |
| Alta (`RingsChannelSlider`, `K/screens/onboarding/rings/RingsSlider.kt`) | 0-100, `roundToInt` (`:107`) | Estimación atenuada hasta tocar | Según su estado de pantalla | Toque confirma; arrastre previsualiza y confirma al soltar; `setProgress` para TalkBack |

Cancelar o atrás en el cierre: `BackHandler { onDismiss() }` (`WorkoutFinishHost.kt:361`) → `hideFinish()` (`K/screens/workout/WorkoutViewModel.kt:6328`), sin confirmación. Si la hoja se desmonta al ocultarse, las ediciones se pierden (no verificado el condicional de composición).

## B. Estado de los hallazgos previos

| ID | Estado | Evidencia |
|---|---|---|
| H1 | **Parcial** | El texto literal ya no existe (sin «día siguiente» ni «sin que intervengas»). Pero «Si no tocas los rings, el modelo igual puede ajustar…» (`HomeRingsSection.kt:560`) es **correcto**: `learnFromLatestFinishedLog` → `PerformanceTauLearner.applyToCache` aprende sin tocar (`K/screens/auge/AugeViewModel.kt:215-223, 898`). El «no aprende» del doc. §3 solo vale para el confirm manual |
| H2 | **Parcial** | Columna ya explica el blend (`HomeRingsSection.kt:547`). Músculos sigue sin explicarlo: blend articular (`K/domain/auge/AugeRecoveryEngine.kt:1145-1147`) y mezcla 0,85·media + 0,15·cuartil (`:1168-1172`); la descripción sigue diciendo «Promedio» (`K/domain/auge/RecoveryBands.kt:94`) |
| H4 | **Resuelto** | `systemicRecoveryMultiplier` = 1.0 (`AugeRecoveryEngine.kt:169-172`); readiness ya no escribe 7,5 (`WorkoutSessionOverlaysHost.kt:83`). Quedan causas por sueño (G) |
| H6 | **Vigente** | Sin cifras nuevas; el editor es plan y Home es histórico |
| H7 | **Vigente** | Voz drena (`WorkoutVoiceCommandHandler.kt:1919-1925`); no hay widgets de anillos |
| H9 | **Desfasado** | El `headline` **sí se pinta** (`HomeRingsSection.kt:121-129`) y es dañino (U-01, U-06). `InterferenceEngine` **sí tiene llamador** (`K/domain/sessionassistant/SessionAssistantEngine.kt:790`), así que «no afecta producto hoy» ya no vale. Siguen sin llamador `calculateSleepRecommendations` y `getTendonCompensationSuggestions` (`K/domain/auge/AugeTtcEngine.kt:464`) |
| H24 | **Vigente** | `predictedMuscleBatteries[muscle] ?: 100` en el ancla V2 (`K/screens/auge/AugeViewModel.kt:630`) |
| H26 | **Parcial** | Cierre: compara por valor (`K/screens/workout/WorkoutScreen.kt:1740-1744`), corregido. Readiness: sigue por bandera (`WorkoutReadinessSheet.kt:330-331`); U-05 |
| H32 | **Vigente** | Readiness escribe V1 (`WorkoutSessionOverlaysHost.kt:91-97`); el cierre escribe V2 (`applyManualBatteries`, `AugeViewModel.kt:596` y siguientes) |
| H33 | **Parcial** | Copy corregido: «Posible exceso de volumen» (`HomeScreen.kt:648`). Sin escritores de `PostSessionFeedback` (`AugeViewModel.kt:463-465` sin llamador): factores muertos. Persiste «Zonas con fatiga acumulada» (`HomeScreen.kt:354`) |
| H34 | **Parcial** | «Estimación: el plan de…» (`K/domain/auge/ExerciseReadinessEngine.kt:505`) es matizado; siguen «Tu cuerpo no está del todo recuperado» (`:550`) y «Tu columna no está del todo recuperada» (`:541`) |
| H35 | **Desfasado** | `LoadAdvisoryEngine.evaluate` no tiene flags (`K/domain/auge/LoadAdvisoryEngine.kt:106`). La tarjeta de Home aparece con cualquier aviso ≥ ADJUST (`HomeScreen.kt:634-644`) |
| H36 | **No verificado** | `K/domain/relator/CoachObserver.kt` no referencia readiness. Falta localizar el coach real |
| Doc. §7 SPF | **Desfasado** | El código usa erectores 45 %, dorsales 30 %, trapecio 25 % (`AugeRecoveryEngine.kt:915`), no 50/25/15/10. El copy de Columna sí coincide (`HomeRingsSection.kt:547`) |

## C. Hallazgos nuevos

**[U-01] [P0] [bug] Sin datos se presenta como 100 % afirmativo**
- `K/domain/auge/AugeRecoveryEngine.kt:1484-1489` (titular), `K/screens/home/HomeRingsSection.kt:121-129` (pinta sin mirar cobertura), `K/screens/workout/components/WorkoutReadinessSheet.kt:252-258` (sin cobertura ni carga), `AugeRecoveryEngine.kt:1508-1527`, `K/domain/relator/WarmupObserver.kt:20, 35-38`, `K/screens/workout/RelatorContextBuilder.kt:105, 590-591`, `K/screens/home/RingDetailSheet.kt:68`.
- Cita: `AugeViewModel.kt:1171-1173`: «…aunque el motor pinte 100 por estar sin calibrar.» Sin registros, `calculateSystemicFatigue` da Energía 100 (`AugeRecoveryEngine.kt:888-890`).
- Escenario: usuario que saltó RINGS («tus RINGS quedan sin calibrar», `K/domain/onboarding/WizChatCopyCatalog.kt:192`). Músculos = Energía = Columna = 100 → overall = 0,40·100 + 0,35·100 + 0,25·100 = 100 → HIGH. En pantalla: torre «Sin datos», titular «Listo para empujar», readiness «Músculos: Alta · Puedes meter volumen alto…», relator «Óptimo para entrenar (100)» y, al ser ≥80, «Salta aprox» en el calentamiento. Tocar la torre abre «100 % · Alta».
- Efecto: se recomienda saltar calentamiento o meter volumen sin ningún dato.
- Arreglo: sin cobertura con datos → sin titular, sin veredicto (`null`), canales en «Sin datos», y ninguna acción SKIP en el relator.

**[U-02] [P0] [matemática] Truncado de Float: 53 se ve como 52 y 59 como 58**
- `K/screens/home/HomeRingsSection.kt:191, 220, 263`; `K/screens/home/HomeScreen.kt:137-139, 950`.
- Cita: `val score = (progress * 100).toInt()` (`HomeRingsSection.kt:263`).
- Comprobado con python (float32, 0…100): solo fallan 53→52 y 59→58.
- Efecto: torre, chip, accesibilidad y mini cabecera muestran 52 % mientras el detalle muestra 53 %. La banda no cambia (ambos MODERATE).
- Arreglo: pasar el `Int` de `ringScore` a la torre, o `Math.round`.

**[U-03] [P0] [robustez] Los ajustes por músculo de readiness se pierden al recrear la Activity**
- `K/screens/workout/components/WorkoutReadinessSheet.kt:125-137` (`muscleAdjustments` saveable), `:144` (`userEditedMuscles` = `remember`, no saveable), `:166-178` (rama else reasigna semillas si la marca no existe), `:320-322` y `:333` (`editedMuscleMap`). `K/screens/workout/WorkoutSessionOverlaysHost.kt:91-97`. Manifiesto: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\AndroidManifest.xml:57-60`, sin `configChanges`.
- Mecanismo: tras la recreación, `initialized` (saveable, `:145`) es true, así que la primera composición ejecuta la rama else y reasigna todas las semillas.
- Escenario: Pectorales (semilla 85) arrastrado a 60 → plegar el Flip (o girar, o muerte de proceso) → vuelve a 85 en pantalla. Al confirmar, `editedMuscleMap` = {} y no se guarda ningún `manualMuscleBatteries`. Energía y Columna sí se conservan.
- Efecto: el dato del usuario se pierde en el guardado sin aviso.
- Arreglo: `userEditedMuscles` con `mapSaver`/`rememberSaveable`, o derivar «editado» como valor distinto de la semilla.

**[U-04] [P1] [robustez] El cierre pierde sus sliders al recrear la Activity o al cerrarlo**
- `K/screens/workout/WorkoutFinishHost.kt:218-237` (`remember(seed)`), `:275-291` (solo notas, molestias y compartir son saveable), `:361`; `K/screens/workout/WorkoutScreen.kt:1794`; `K/screens/workout/WorkoutViewModel.kt:6328`.
- Respuesta a Q3: **no** se conserva al plegar, girar o morir el proceso. Vuelven `neuralFinal`, `spinalFinal`, `muscleFinal` y las banderas `*Edited` a las semillas del preview.
- Efecto: las ediciones se pierden. Es visible, pero sin explicación; el usuario puede confirmar valores que no eligió.
- Arreglo: `rememberSaveable` para enteros y `mapSaver` para músculos, igual que readiness.

**[U-05] [P1] [inconsistencia] El mismo gesto tiene efectos distintos según la superficie**
- Readiness: `manualNeural = if (userEditedNeural) neural else null` (`WorkoutReadinessSheet.kt:330-331`), con la bandera activada por cualquier cambio (`:244`). `WorkoutSessionOverlaysHost.kt:87-88` arrastra el ajuste anterior de hoy (`?: todayWellbeing?.manualNeuralBattery`). `AugeViewModel.saveWellbeing` aprende con ese valor (`anchoredLog.manualNeuralBattery?.takeIf { it != predictedNeural }`).
- Cierre: `neuralChanged = finalNeuralBattery != preview.neural` (`WorkoutScreen.kt:1740-1744`), por valor.
- Escenarios: (a) a las 08:00 readiness fija Energía 60; a las 18:00 se confirma sin tocar, la predicción es 75, y se reenvía 60 como ajuste nuevo. (b) Tocar y devolver a 80 (= predicción) guarda `manualNeuralBattery = 80` con ancla nueva (`K/domain/auge/WellbeingSaveMerge.kt:32-43`). Desde entonces el anillo decae desde el ancla y solo cuentan los entrenos posteriores (`AugeRecoveryEngine.kt:784-800`).
- Efecto: el anillo queda anclado al valor manual. El aprendizaje recibe valores viejos (ver E).
- Arreglo: en readiness enviar manual solo si difiere de la predicción, como hace el cierre; no reenviar la fila de hoy como entrada nueva.

**[U-06] [P1] [matemática] El titular ponderado ignora el canal más bajo**
- `K/domain/auge/AugeRecoveryEngine.kt:1484-1489` (0,40 Energía, 0,35 Músculos, 0,25 Columna) → `K/domain/auge/RecoveryBands.kt:105-110`; pintado en `HomeRingsSection.kt:121-129`.
- Escenario: Energía 40 («Baja», `RecoveryBands.kt:23`), Músculos 95, Columna 95 → 16,00 + 33,25 + 23,75 = 73 → NORMAL → «Buen estado para entrenar». El mismo score alimenta el `dailyScore` del relator.
- Efecto: texto que contradice el anillo de Energía visible en la misma pantalla.
- Arreglo: que el titular tome la banda más baja de los canales con datos, o no mostrarlo.

**[U-07] [P1] [copy] «Energía alta» significa lo contrario en el editor y en el asistente**
- `K/screens/sessioneditor/SessionEditorAugeComputation.kt:192` (disparador `drain.cns >= 85 || averageRpe >= 9.3`) y `:197` («Tu Energía ya va alta para esta sesión…»). Mismo copy en `K/domain/sessionassistant/SessionAssistantEngine.kt:534-535`, con «(${drain.cns}%)». En Home, `K/domain/auge/RecoveryBands.kt:54`: «Energía alta: habitual con 3–4 sesiones/semana».
- Escenario: RPE medio 9,4 y drenaje SNC previsto 40 → «Tu Energía ya va alta» (falso: la sesión drena poco). El disparador por RPE no mide drenaje.
- Arreglo: «Esta sesión drena mucho el sistema nervioso (88 %)» y disparar solo por drenaje.

**[U-08] [P1] [copy] Diálogo «¿Qué son los RINGS?»: afirmaciones exageradas o falsas**
- `K/screens/home/HomeRingsSection.kt:511` («representación visual del estado de tu cuerpo»), `:523` («Muestra el promedio… puedes dirigirte al músculo en específico y corregir»), `:535` («Es tu sistema nervioso estructural»).
- Evidencia: Músculos no es un promedio simple (`AugeRecoveryEngine.kt:1168-1172`). Desde Home no hay corrector por músculo: `RingDetailSheet.kt` no tiene sliders y los chips de `HomeSessionSection.kt:474` son de solo lectura. Los correctores están en readiness (`WorkoutReadinessSheet.kt:528-568`) y en el cierre (`WorkoutFinishHost.kt:639-668`).
- Efecto: la ayuda promete una acción que no existe desde la pantalla donde se lee, y el título vende fisiología.
- Ver la tabla Q4 para el texto propuesto.

**[U-09] [P1] [inconsistencia] Tres cifras de «Músculos» en readiness y Home**
- `K/screens/workout/components/WorkoutReadinessSheet.kt:138-141` (torre: media de los músculos de la sesión), `:123, 158, 168` (`muscular` = semilla global), `:443` (la etiqueta usa `muscular`); semilla global en `WorkoutScreen.kt:514-517`.
- Escenario ilustrativo: Pectorales, Tríceps y Deltoides a 60; global en Home 85. Al abrir: torre 60, etiqueta 60. Tras el primer cambio de clave: etiqueta 85, torre 60. Editar un músculo mueve la torre, no la etiqueta.
- Además, `average().toInt()` trunca la media (`:140`).
- Arreglo: una sola fuente para torre y etiqueta.

**[U-10] [P1] [copy] Semántica invertida en los valores por músculo**
- `K/screens/workout/WorkoutFinishHost.kt:641` «Desgaste final por Músculo» sobre valores de recuperación (100 = fresco, `:654`). `:604`: «ajustar la frescura muscular final», «Energía (SNC) y Columna (Spinal)». Readiness: `WorkoutReadinessSheet.kt:544` «Frescura por Músculo».
- Efecto: no está claro si subir o bajar «desgaste».
- Arreglo: «Frescura por músculo» (100 = fresco) en ambas hojas; «Energía y Columna».

**[U-11] [P1] [accesibilidad] El ajuste por músculo no es accesible; contraste insuficiente**
- `K/screens/workout/components/MinimalMuscleSlider.kt:33-107`: cero usos de `semantics` o `contentDescription`; gestos con `detectTapGestures` (`:76`) y `detectHorizontalDragGestures` (`:84`) sobre un `Box` de 32 dp. Se usa en readiness (`WorkoutReadinessSheet.kt:556`) y en el cierre (`WorkoutFinishHost.kt:656`).
- Contraste WCAG (python): `#525252` sobre `#1F1F1F` = **2,11:1** para valores <55 (`MinimalMuscleSlider.kt:41-43`). Los umbrales de color (80/55) no coinciden con las bandas de Home (85/70/50/35, `RecoveryBands.kt:11-14`).
- Arreglo: `progressBarRangeInfo` + `setProgress`, como en `RingsSlider.kt:67-79`; color por banda.

**[U-12] [P1] [bug] Chips de músculo y semillas muestran 100 % cuando no hay dato**
- `K/screens/home/HomeSessionSection.kt:432` (media `?: 100`), `:474` y `:499`; `K/screens/workout/WorkoutScreen.kt:487-491, 503`; `WorkoutFinishHost.kt:654`.
- Escenario: sin historial, `getPerMuscleBatteries` devuelve entradas a 100 (sin drenaje). Un músculo fuera de `BATTERY_MUSCLES` da null → 100. Tarjeta de sesión: «Músculos involucrados 100%» en verde.
- Arreglo: `null` → «Sin datos», nunca 100.

**[U-13] [P2] [copy] «Modelo actualizado» aparece en instalaciones nuevas**
- `K/data/models/AugeAdaptiveModels.kt:26` (`modelUpdateNoticeShown = false`), `K/screens/auge/AugeViewModel.kt:361` (`showModelUpdateNotice = !…`), `:1073-1085` (se marca al abrir una hoja). Texto: `K/screens/home/RingDetailSheet.kt:79`: «…Energía/Columna recuperan con la misma semántica que el aprendizaje».
- Efecto: un usuario nuevo recibe la noticia de un cambio que nunca vio, con jerga.

**[U-14] [P2] [inconsistencia] La voz usa «drenaje» con el mismo símbolo que la batería**
- `K/screens/workout/WorkoutVoiceCommandHandler.kt:1925` («Llevas N por ciento de drenaje muscular y N del sistema nervioso») y `:1373` («Batería nerviosa en N»). Datos: drenaje de la sesión en vivo (`WorkoutViewModel.kt:2174-2195`).
- Escenario: drenaje SNC 40 → voz «40 por ciento del sistema nervioso»; Home «Energía 60 %». Son complementarios, pero el mismo símbolo tiene sentidos opuestos.
- Arreglo: «Has gastado 40 % de tu energía en esta sesión; te quedan 60 %».

**[U-15] [P2] [robustez] Carga inicial visible en readiness**
- `WorkoutReadinessSheet.kt:252-258` no comprueba `isLoading`. Placeholders de `K/screens/auge/AugeViewModel.kt:74-112`: 50, banda MODERATE, «Espera mientras cargamos tu estado muscular.»; semillas 50 en `WorkoutScreen.kt:506-517`.
- Escenario: abrir readiness antes de la primera publicación muestra «Músculos: Moderada · Espera mientras cargamos…».

**[U-16] [P2] [procedencia] Reescrituras de la fila de hoy pierden `source` y `capturedFields`**
- `K/screens/auge/AugeViewModel.kt` (`applyManualBatteries` crea un `DailyWellbeingLog` nuevo sin esos campos); `K/domain/auge/WellbeingSaveMerge.kt:38-46` (toma `source` del entrante; solo une `capturedFields`).
- Efecto: la rama de procedencia del alta (`K/domain/onboarding/RingsChannelCoverage.kt:226-244`) depende de `capturedFields`. HIPÓTESIS de baja frecuencia (alta el mismo día sin historial): no reproducida.

**Q4. Auditoría de textos (CORRECTA / EXAGERADA / FALSA / OBSOLETA)**

| Texto (ruta:línea) | Clasificación | Propuesta |
|---|---|---|
| «…representación visual del estado de tu cuerpo» (`HomeRingsSection.kt:511`) | EXAGERADA | «Estiman tu carga reciente y cómo te sientes; no miden tu cuerpo.» |
| «Muestra el promedio del estado de todos tus músculos» (`:523`) | INCOMPLETA | «Resume tus músculos; pesa más al más cargado y descuenta articulaciones.» |
| «…puedes dirigirte al músculo… y corregir» (`:523`) | FALSA desde Home | «Puedes corregir cada músculo antes de entrenar o al cerrar.» |
| «Es tu sistema nervioso estructural» (`:535`) | EXAGERADA | «Refleja la carga neural de entrenar y cómo te sientes.» |
| «si dormiste mal, eso ya llega en esta sensación» (`:535`) | Sueño (G) | Ver G |
| «Mezcla… dorsales, erectores/lumbar y trapecio» (`:547`) | CORRECTA | Mantener (coincide con `AugeRecoveryEngine.kt:915`) |
| «Si no tocas… el modelo igual puede ajustar…» (`:560`) | CORRECTA | Precisar: «ajusta tiempos de recuperación» |
| «esa corrección manda ese día» (`:560`) | APROXIMADA | «manda hasta 18 h» (ancla caducada, ya confirmado) |
| «Modelo actualizado: … misma semántica…» (`RingDetailSheet.kt:79`) | Jerga; U-13 | «Ahora Columna cuenta sentadillas, peso muerto y similares.» |
| «Tu normal aparecerá tras unas 4 sesiones» (`:91`) | CORRECTA | Mantener (`K/domain/auge/AxialLoadMonitor.kt`, `values.size < 4`) |
| «Cuentan los últimos 7 días» (`K/screens/onboarding/rings/RingsSessionDaysStep.kt:76`) | NO RESPALDADA | Los cortes del motor son 10 días (`AugeRecoveryEngine.kt:784, 938`) y 30 (`:1286`) |
| «Tu Energía ya va alta» (`SessionEditorAugeComputation.kt:197`; `SessionAssistantEngine.kt:535`) | FALSA (invertida) | U-07 |
| «Déficit activo… la recuperación no se caiga» (`SessionEditorAugeComputation.kt:230`) | EXAGERADA; nutrición apagada (H16, no re-verificado) | «Con déficit, conviene no subir el volumen por sesión.» |
| «Toleras bien carga axial y tensión conectiva» (`K/domain/auge/RecoveryBands.kt:85`) | EXAGERADA | Quitar «tensión conectiva» |
| «Estado esperado 24–48 h después de entrenar» (`RecoveryBands.kt:48`) | FALSA (contrasta con H18, no re-verificado) | «Estado normal tras entrenar.» |
| «Energía alta: habitual con 3–4 sesiones/semana» (`RecoveryBands.kt:54`) | NO RESPALDADA: no hay frecuencia en el código | Quitar la frecuencia |
| «La columna recupera más lento que un músculo» (`RecoveryBands.kt:64`) | EXAGERADA (plateau +18 h heurístico) | «La columna tarda más en asentarse.» |
| «KPKN detectó señales de fatiga…» (`K/screens/home/HomeScreen.kt:351`); «Zonas con fatiga acumulada» (`:354`) | EXAGERADA | «KPKN sugiere bajar carga según tu historial.» / «Zonas con más carga reciente.» |
| «Estimación: el plan… encaja con tu estado de hoy» (`ExerciseReadinessEngine.kt:505`) | CORRECTA (matizada) | Mantener |
| «Tu cuerpo no está del todo recuperado» (`ExerciseReadinessEngine.kt:550`) | EXAGERADA | «El plan pide más de lo que sugieren tus anillos hoy.» |
| «Overlap: al día siguiente» (`SessionAssistantEngine.kt:806`) | CORRECTA: gap planificado = días × 24 (`K/domain/auge/InterferenceEngine.kt:133`) | Mantener |
| «Posible exceso de volumen» (`HomeScreen.kt:648`) | CORRECTA (H17 corregido) | Mantener |
| «Readiness de hoy: Óptimo para entrenar (100)» (`K/domain/relator/ReadinessObserver.kt:10`) | FALSA sin datos | U-01 |
| «Sesión bien calibrada/a vigilar/fatigante» (`K/screens/sessioneditor/components/sheets/AugeSheetParts.kt:189-191`) | Código muerto | Eliminar |
| «Tus RINGS, para ver cómo te recuperas cada día» (`K/screens/onboarding/design/WizardPrologueOverlay.kt:208`) | APROXIMADA (proxy) | Mantener |
| «Cuánto se han recuperado tus músculos» / «Tu reserva para entrenar fuerte» (`K/screens/onboarding/rings/RingsIntroStep.kt:183-184`) | APROXIMADA | «Estimación de cuánto…» |

Consistencia terminológica: «Cobertura de datos» (`HomeRingsSection.kt:133`) frente a «Confianza» (`RingDetailSheet.kt:113`) para el mismo valor. «NN%» (Home, readiness, chips) frente a «NN %» (detalle, wizard, `K/ui/components/rings/KpknTowerRings.kt:142`). Para un mismo canal aparecen «Energía», «SNC», «Sistema nervioso», «Batería nerviosa» y «Spinal». Músculos aparece como «Frescura», «Desgaste», «Preparación» y «Recuperación».

**Q5. Estados límite**

| Caso | Resultado |
|---|---|
| Carga inicial | Home protegido (`HomeRingsSection.kt:74-77`); readiness no (U-15) |
| Sin datos | U-01, U-12 |
| Error | Sin estado de error. `recompute` (`AugeViewModel.kt:188-378`) no tiene `try/catch`: si un motor lanza, `isLoading` queda true. HIPÓTESIS no reproducida |
| Historial vacío | Igual que sin datos (U-01) |
| 0 / 100 | `RecoveryBands.band`: CRITICAL <35; «0 % no existe» (`RecoveryBands.kt:58`) es coherente con los pisos |
| Nombres largos, fuente 130 %, 320 dp | `MinimalMuscleSlider` sin `maxLines` (`:50`); «Sin calibrar» sin `maxLines` (`HomeRingsSection.kt:290-295`). NO VERIFICADO: requiere captura |
| Accesibilidad de la torre | Sí incluye el porcentaje (`HomeRingsSection.kt:217-228`; `KpknTowerRings.kt:142`) |
| Accesibilidad de sliders | Readiness por músculo y cierre: no (U-11). Sparkline sin semántica (`RingDetailSheet.kt:137-160`) |
| Claro / oscuro | `K/ui/theme/Theme.kt` solo declara `darkColorScheme`; no encontré `KpknTheme(`. NO VERIFICADO el punto de aplicación |

**Q6. Código muerto de UI y datos**
- `K/screens/sessioneditor/components/sheets/AugeSheetParts.kt`: el archivo completo (`:38-245`) no tiene llamadores externos.
- `MuscleSliderChip` (`WorkoutReadinessSheet.kt:755-767`): sin llamadores.
- `SessionClosingFeedback.neuralEdited`, `spinalEdited`, `musclesEdited`, `finalMuscularBattery` (`WorkoutFeedbackModels.kt:156-162`): sin consumidores. `environmentTags` siempre vacío (`WorkoutFinishHost.kt:401`).
- `calculateSleepRecommendations` y `getTendonCompensationSuggestions` (`AugeTtcEngine.kt:464`): sin llamadores.
- `dashboard.summary` y `dashboard.recommendation`: no se pintan.
- `WorkoutFinishHost.kt:654`: variable `start` casi muerta.

## D. Lo sano (no tocar)
- Cobertura con el mismo corte temporal (`AugeViewModel.kt:239-253, 285-292, 1175-1203`).
- Precedencia de procedencia (`RingsChannelCoverage.kt:184-216`) y «Estimación inicial/subjetiva» en chips (`HomeRingsSection.kt:264-268`).
- Torre sin datos como pista discontinua (`HomeRingsSection.kt:188-192`).
- Slider de alta con semántica y redondeo correctos (`RingsSlider.kt:67-107`).
- Fusión de la fila de hoy conserva ancla y decaimiento (`WellbeingSaveMerge.kt:16-17, 36-43`).
- Inversión de Columna exacta dentro del rango alcanzable (`AugeRecoveryEngine.kt:304-316`).
- `hoursToNormal` usa la misma referencia que la torre (`AugeViewModel.kt:321-331`).
- Texto de fatiga sistémica honesto (`K/data/wikilab/TrainingConceptsData.kt:48`).
- Tour correcto (`K/ui/tour/TourScripts.kt:52-53`).
- El aviso de modelo no entra en bucle (`AugeViewModel.kt:1073-1085`).

## E. Preguntas abiertas y no verificado
- H36: localizar el coach real.
- Matiz al doc. §3: el aprendizaje por rendimiento sí corre sin tocar (`AugeViewModel.kt:215-223, 898`).
- Hipótesis de escritura de caché: `cacheToPersist` (`AugeViewModel.kt:339-346, 395-402`) guarda una copia previa al mutex. Podría revertir `modelUpdateNoticeShown` (`:1078-1079`) o `dismissedAdvisoryKeys` (`:1064`). No reproducida.
- Notas de otras áreas (aprendizaje, ya auditado): U-05 alimenta el aprendizaje con valores viejos; H24 sigue en `AugeViewModel.kt:630`.
- No verificado: cuerpo de `SessionEditorViewModelAugeActions.kt` (solo firmas); `RingsIntroSimulator`; ventana de 7 días del calculador de vista previa; `ExerciseReadinessEngine` (solo textos); nutrición ×1,25 (H16).
- Gesto del cierre: 1 punto = 1,25 px (`WorkoutReadinessSheet.kt:687`). Con una densidad supuesta de 2,625, son ~0,48 dp por punto (100 puntos en ~48 dp). Dato supuesto, no medido.

## F. Cobertura real
- **Leído completo:** `HomeRingsSection.kt`, `RingDetailSheet.kt`, `WorkoutReadinessSheet.kt` (1238), `WorkoutSessionOverlaysHost.kt` (307), `MinimalMuscleSlider.kt`, `AugeSheetParts.kt`, `RingsSlider.kt`, `RecoveryBands.kt`, `RingsChannelCoverage.kt`, `AugeModels.kt`, `WellbeingSaveMerge.kt`, `WorkoutFeedbackModels.kt` (140-180).
- **Parcial, por rangos:** `HomeScreen.kt` (128-368, 540-669, 725-974); `HomeSessionSection.kt` (236-316, 420-512); `HomeViewModel.kt` (180-205); `AugeViewModel.kt` (60-370, 405-462, 596-660, 1040-1213); `AugeRecoveryEngine.kt` (55-73, 102-111, 149-172, 290-316, 461-505, 767-800, 880-934, 1101-1200, 1369-1527); `WorkoutScreen.kt` (360-392, 480-530, 1700-1795); `WorkoutFinishHost.kt` (150-430, 580-680); `SessionEditorAugeComputation.kt` (66-240, y grep de 297-330, 775-850); `LoadAdvisoryEngine.kt` (grep y 220-260); `SessionAssistantEngine.kt` (grep más 534-535, 786-812, 969-971).
- **Solo grep:** `SessionEditorViewModelAugeActions.kt`, wizard (`RingsIntroStep`, `RingsResultStep`, `RingsSessionDaysStep`, `RingsIntroSimulator`, `SetupRingsPreviewCalculator`, `SetupRingsMapper`), `TourScripts.kt`, `TrainingConceptsData.kt` (5-100), `WorkoutVoiceCommandHandler.kt`, `ProgramDetail` y `VolumeView.kt`, `KpknTowerRings.kt`, `Theme.kt`, `InterferenceEngine.kt`, `CoachObserver.kt`, `ExerciseReadinessEngine.kt`, widgets, `strings.xml` (sin cadenas de anillos; la UI usa literales Kotlin: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\values\strings.xml`).
- **No leído:** `SessionAssistantEngine.kt` 180-360 (solo por palabras clave); `SessionEditorAugeComputation.kt` 240-297, 330-775, 850-996; `WorkoutScreen.kt` fuera de rangos; cuerpo de `PerformanceTauLearner.kt`.
- **Ejecuciones:** solo lectura, git no usado, python de una línea (float32 de 0…100, contraste WCAG, cota de Columna). Sin Gradle ni tests.

## G. Observaciones de sueño (informativo)
1. El τ ya no depende del sueño: `systemicRecoveryMultiplier` = 1.0 (`K/domain/auge/AugeRecoveryEngine.kt:169-172`).
2. Las causas de Home todavía leen sueño: `calculateWeightedSleepHours` usa `SleepLog` antiguos y `wellbeing.sleepHours` (`AugeRecoveryEngine.kt:149-163`). «Poco sueño en las últimas noches» (`:1426`) puede aparecer en la línea de causas (`HomeRingsSection.kt:131-136`).
3. El copy «si dormiste mal, eso ya llega en esta sensación» (`HomeRingsSection.kt:535`) afirma que la sensación captura el sueño; el modelo no lo mide.
4. Readiness sigue enviando `sleepQuality` (default 3, `WorkoutSessionOverlaysHost.kt:79, 107`) y el relator lo usa («Sueño flojo» si ≤2, `ReadinessObserver.kt`).
