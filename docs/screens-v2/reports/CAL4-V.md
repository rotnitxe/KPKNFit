# CAL4-V · APRE en vivo y XRM de la próxima semana

Estado: implementación completa preparada en u20, codex/sv2-cal4v, **sin ejecución todavía**. Base provisional acc75872c; antes de validar o integrar se actualizará con fase3 completa y verde. El usuario autorizó autonomía y paralelismo preparatorio. Sin cambios Room, push, master, paquetes personales ni firma.

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
