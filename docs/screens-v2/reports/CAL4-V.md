# CAL4-V · APRE en vivo y XRM de la próxima semana

Estado: integrado en u11 y verificado de forma dirigida. Producción corregida 420acaf4c, fixture 66993303c y sólo documentación posterior. 1989 casos puros únicos verdes mediante corrida completa más repetición del único fixture; Android final 14 clases/201 casos sin fallos, errores ni omitidas. Los wrappers cerraron y los XML están archivados. No es aún barrido ampliado ni QA de dispositivo. Room congelado, sin push/master.

## Contrato y cálculos

La identidad del bloque procede de sus cuatro IDs y variante. Un marcador inválido no vuelve a identificarse por parecido. El respaldo legado requiere apre-mann, regla ApreXrm y cuatro prescripciones compatibles. S3/S4 son AMRAP, nunca CALIBRATOR; una quinta AMRAP no sustituye la S4 marcada.

S3 usa Apre.set4LoadKg y S4 usa Apre.nextWeekXrmKg, con la carga externa real, reps completas de CAL1 y salto del material. No se descuentan dos veces las parciales. Corporal/asistido, fuentes incompatibles o sin carga externa se abstienen. El ejemplo155×17 produce10RM162,5kg por Mann; jamás1RM niTM. Se conservan fechas desconocidas como null.

## S3, V7 y durabilidad

V7 muestra «APRE · S4 → X kg (tabla de Mann)», fuente y reps válidas, Aplicar a S4, Conservar y Revertir con objetivos48 dentro del marco312×425. capacityKg/referenceKg/ratio quedan null. Sólo cambia la S4 pendiente del contexto y lado exactos; hechas, fijadas a mano y marcadores de otro contexto se conservan.

Recorder guarda decisión y snapshot antes de publicar. Correcciones, voz/vídeo y undo recalculan sin descartar completedSets: candidato durable, Room, estado y resultado confirmado comparten la captura. La protección manual distingue lados. Revertir conserva los kilos externos de una S4 ya ejecutada. Ver INT-APRE-DURABLE.md para los defectos encontrados y las pruebas con Room real.

## S4 y propuestas

CompletedSet y AutoregulationProposal llevan ApreXrmCapture opcional en el JSON existente. ADJUST_XRM es un carril físico separado: OFF no actúa; PROPONER conserva un pendiente sin cambiar bolsa/prescripciones; AUTO o aceptación guardan OBSERVED_WORKING_SET, rango XRM exacto y procedencia APRE_XRM. No exige LiftSlot, perfil ni receta para un bloque manual marcado.

La asociación verifica configuración/contexto registrados y no reetiqueta un log de varianteA comoB. Admite el contrato real de fin de sesión: log con sessionId baseA y ejercicios activosB/C/D; identidades ambiguas se abstienen. Se preservan sesiones ejecutadas y manuales. Al cerrar ciclo se lee el cicloN y se dirige a la primera ocurrenciaN+1, sin confundir el ID de plantilla reutilizado con una sesión hecha. La progresión autora conserva su orden.

Aceptación/rechazo parcial, expiración y reintentos dejan auditoría observable. La corrección162,5→155→162,5 añade revisiones; repetir el último payload no duplica. El mensaje pendiente contiene sólo lo aún pendiente. Si sólo se guarda referencia, el motivo lo dice y no afirma que cambiaron series. Ver INT-APRE-CHANNEL.md.

## Materialización y almacenamiento compartido

El materializador marca el bloque APRE y consume referencias ligadas por ID o slot real y contexto/lado/variante exactos. Las cargas observadas se expresan enkg/reps, como el bloque del editor; no mantienen un porcentaje TM ficticio. Se calculan0,5/0,75/1/1 y se conserva metadata.loadIncrementKg. Ejercicios con filas adicionales o fijadas se conservan.

Se reutiliza la bolsa JSON ExerciseLoadReferenceStore y metadatos opcionales de CAL5b, incorporados como dependencia común para no duplicar almacenes. El resto de CAL5b (UI, confirmación, preparación y sincronización del test1RM) sigue en u21. No se toca Entity/DAO/migración/schema ni versión30.

## Pruebas preparadas y siguiente verificación

Puras: LiveApreBlockTest, ApreXrmCaptureFactoryTest, ApreXrmPrescriptionTest, ProgramAutoregulationApreXrmChannelTest y Cal6AprePrescriptionTest. Cubren las tres tablas, identidad, material, contextos/lados, referencias, correcciones, propuestas mixtas, variantes y ciclo. Se repetirán protocolos/training por JVM, incluidos los fixtures por archivo.

Android: ApreLiveCalibrationTest, ApreDurableRoomTest, ApreDurableViewModelRoomTest, ApreLateralCalibrationTest y components.ApreCardUiTest; además las regresiones WorkoutCalibrationTest, WorkoutCalibrationRecorderRoomTest, WorkoutVoiceSetMutationRoomTest, WorkoutVoiceSetMutationsTest, Cal3ViewModelRoomTest, components.Cal3CardUiTest, WorkoutFinishCalibrationTest, Cal3ProgramPreviewTest y TrainingPlanRecipeJsonCompatTest. Las19 nuevas de durabilidad/UI aún no se han ejecutado; las restantes tampoco se presentan como verdes.

Con KPKN_RENDER_DIR se preparan12PNG V7 de APRE3/6/10, viewport360×740/880 y fuente100/130; raíz debe abrirlos después de una corrida exitosa. Revisión estática y UTF-8 estricta correctas; compilación, tests, Room observado, revisión visual, QA y APK continúan pendientes. No se declara ninguna de esas evidencias a partir del código preparado.
## Incidencia de compilación previa a validación

La primera ejecución JVM centralizada en u11 (f47cc64e5) terminó con código 1 durante la compilación principal: 0 tests y ningún XML nuevo. Reportó el import ausente de ApreBlockReference en PlanMaterializer y el argumento con nombre incorrecto en evaluateApreXrmOnly; ambos se corrigen en este cambio. No se ejecutaron Gradle, JVM ni pruebas desde este worktree; queda pendiente la repetición centralizada.

## Segunda compilación APRE JVM

La segunda corrida centralizada compiló producción y falló durante compileTestKotlin. El error primario fue la referencia sin calificar a ProgressAdvanceResult en ProgramAutoregulationApreXrmChannelTest; los errores de advancedCycle, program y autoregulationProposals fueron derivados. Se corrigió la referencia al tipo anidado de ProgramProgressEngine. No se ejecutaron tests en esta corrida ni desde este worktree; queda pendiente repetir el wrapper centralizado.

## Correcciones tras la tercera corrida APRE JVM

La corrida centralizada reportó 192 clases, 1.988 tests y 5 fallos; u20 no ejecutó Gradle ni tests. Dos casos APRE mostraron que la receta no tenía identidad de día estable: cada semana recibía Exercise.id distinto, sin una clave común de día/slot para llevar el XRM a la ocurrencia siguiente. La receta ahora conserva apre-squat/apre-bench/apre-deadlift por lift y las regresiones verifican que los Exercise.id sigan siendo distintos mientras día y slot permanecen iguales. El matcher no se amplía.

La receta APRE también queda fuera del clasificador de cambios semanales de TM. Su caso de extremo a extremo espera ADJUST_XRM de 162,5 kg desde S4 (155 kg × 17 repeticiones), sin liftSlot, delta porcentual ni delta de TM. Las cargas de S1/S2 para ese XRM son 82,5/122,5 kg por redondeo al salto real de 2,5 kg.

La corrección de identidad, la aplicación al bloque siguiente y la rematerialización quedan pendientes de repetición JVM centralizada. Ninguna prueba se ejecutó desde este worktree.


## Cierre de integración · 23-oct

Ver INT-4.md para las cinco corridas JVM y los diagnósticos completos. La tercera ejecutó 1988 casos con cinco fallos; se corrigieron dos causas productivas (identidad APRE entre semanas y lectura de capturas antes de adoptar IDs legacy) y expectativas antiguas de TM/historial. La cuarta ejecutó 1989: 1988 pasaron y sólo falló la composición del fixture adicional. Tras conservar Wendler BBB íntegro, la quinta ejecutó ese caso: 1/1 verde, sin cambiar producción. Son 1989 únicos verdes en varias ejecuciones, no una corrida completa final verde.

Android final: int-apre-android-codex-20261023-2.log, BUILD SUCCESSFUL en 8 min 11 s, wrapper exit0, 14 clases/201 pruebas, 0 fallos/errores/omitidas. Repite las mismas clases de la primera Android (201/0), ahora sobre la producción corregida. Incluye S3/S4, durabilidad, voz/vídeo, lados, correcciones, fallos/reintentos, JSON y V7. XML por corrida en C:/kw/logs/screens-v2-evidence/.

Revisados tres renders nuevos de C:/kw/shots/int-apre-2/: APRE3 740/130, APRE6 880/100 y APRE10 880/130. Mann, cambio y acciones completos dentro de 312×425 y objetivos48; las tres tablas se verifican en doce PNG. No se atribuye a estos renders validación de barras/IME de dispositivo. Quedan el relator PLAN-OBS, el resto de TM, CAL7 y regresión/QA final.
