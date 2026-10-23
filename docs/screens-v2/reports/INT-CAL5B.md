# INT-CAL5B — revision y correcciones

Base revisada: `cc94b308a`, diff contra `acc75872c`. Correcciones autorizadas en `codex/sv2-cal5b` (u21). Implementacion y pruebas preparadas, sin Gradle/JVM/adb. No hay declaracion de compilacion, Room ni renders verdes.

## Hallazgos corregidos

1. **Test1RM ocultado por la bolsa anterior.** `recordPendingOneRmTestMarks` cambiaba el perfil y rematerializaba sin actualizar las capturas existentes. Ahora `ProgramProgressEngine.withTestedProfile` captura la evidencia confirmada con `ExerciseTrainingMax.captureTestProfile` ANTES del avance del metodo. Conserva los cuatro lifts, incluido militar, origen TEST, fecha `resolution.resolvedAtMs`, motivo e identidad del evento. Tambien deja evidencia si el valor numerico se repite; no propaga a variantes ajenas ni a lifts sin resultado. Los incrementos posteriores siguen por AUTHOR_PROGRESSION.
2. **Test1RM con publicacion optimista y fallback incoherente.** El ViewModel ahora resuelve test, referencias, reconstruccion y cursor de programa dentro de `mutateProgramNow`. Si reconstruir falla, aborta antes del guardado; si Room rechaza, no publica candidato, avance ni exito. El test sigue pendiente y puede reintentarse. Las notificaciones y el cursor activo derivado se actualizan solo despues del resultado durable exitoso.
3. **PanelTM cerraba al lanzar un callback Unit.** El panel usa el resultado suspend `updateExerciseTrainingMaxAndAwait`. Conserva formulario y valor durante guardado/fallo; muestra guardando/error/reintento y cierra solo tras exito. No admite otro guardar ni cancelar mientras esta guardando. Se mantienen componentes comunes de 48dp y los nombres completos.
4. **Segunda ocurrencia compatible sin base en vivo.** Confirmar la primera ocurrencia actualizaba la bolsa global de ambas, pero solo hidrataba la primera en la sesion; `missing` eximia la segunda por su bolsa aunque su referencia raw seguia null. Ahora `sessionWithConfirmedTrainingMax` hidrata todas las ocurrencias de identidad global exacta en la sesion, partes y variantes. Mantiene otras configuraciones/convenios, dosis hechas, overrides/drafts manuales y bases explicitas de sesiones manuales. Un lado hecho conserva su dosis mientras el otro pendiente puede recalcularse. APRE OBSERVED conserva su bloque. Se comprueba identidad de ejecucion antes de publicar la confirmacion asincrona.

## Pruebas preparadas

11 metodos nuevos y refuerzo de expectativas existentes; sin duplicar el recorrido CAL7 completo de G1 en u23:

- `com.example.kpkn.domain.training.ExerciseTrainingMaxTest`: +1 evidencia TEST incluso con valor repetido, fecha, idempotencia y variante ajena intacta.
- `com.example.kpkn.domain.training.Cal5OneRmTestTest`: refuerzo del caso militar con bolsa ya migrada, TEST/fecha y TM real.
- `com.example.kpkn.screens.programdetail.ProgramDetailViewModelTest`: +2 casos; fallo Room del test/retry y resultado await TM con fallo de reconstruccion + Room/retry. La expectativa antigua de guardar perfil nuevo con semanas viejas se reemplaza por conservar programa/bolsa/cursor completos y reintentar. Exito con bolsa ya migrada verifica nueva captura TEST; el test de inventario espera ahora la confirmacion durable.
- `com.example.kpkn.screens.programdetail.Cal5bTrainingMaxPanelTest`: +1 caso de suspension real del callback, estado guardando, conservar valor95 ante fallo y reenviarlo en retry; cerrar solo al resultado exitoso. Clicks del overlay NATIVE.
- `com.example.kpkn.screens.workout.Cal5bTrainingMaxUiTest`: +2 casos de texto completo y click real Reintentar TM al130 en740/880. Mantiene cuatro renders 360x740/880 fuente100/130.
- `com.example.kpkn.screens.workout.Cal5bLiveTrainingMaxRoomTest`: nueva, 2 casos con VM/Room reales; dos ocurrencias compatibles reciben TM100 y peso80 antes de desaparecer la solicitud; otra configuracion queda pendiente; recreacion mantiene ambas. Fallo Room conserva referencias anteriores y solicitudes, retry actualiza ambas.
- `com.example.kpkn.screens.workout.Cal5bTrainingMaxSessionReferencesTest`: nueva, 3 casos de dosis hechas/manuales, aislamiento de configuracion, lado izquierdo hecho/derecho pendiente y variante manual con base propia.

## Limites y validacion pendiente

La revision del resto de CAL5b no encontro otro defecto accionable confirmado en migracion exacta, invalidacion de referencias, separacion TM/1RM ni PROMOTE_TM. No se ejecutaron pruebas ni se abrieron PNG nuevos. La geometria requiere la corrida central y revision visual de los renders; las aserciones nuevas no prueban pixeles antes de ejecutarse.

El helper de hidratacion reutiliza la proteccion lateral compartida `manuallyFixedCalibrationSet`; la correccion side-aware de u29 debe convivir con esta base al integrar. El guardado global de referencia/rematerializacion es una mutacion durable de programa; la sesion en curso conserva su canal existente de persistencia y se rehidrata de la bolsa al reiniciar. No se afirma una nueva transaccion multi-entidad programa+ongoing.

No se cambio Room, DAO, entidades, esquemas, datasets ni canal APRE/NativeProgressionCardModel. Verificados estaticamente UTF8 estricto de rutas propias, delimitadores y `git diff --check`; eso no equivale a compilacion.

## Base preparatoria APRE integrada

Se integra `992a9bbee` (u20 APRE) sobre `849843aa4` (u21 CAL5b con base fase3). Preflight `merge-tree` detecto exclusivamente dos add/add: `ExerciseTrainingMax.kt` y `ExerciseTrainingMaxTest.kt`. La comparacion completa confirmo que la version CAL5b contiene todo el contrato compartido de APRE y agrega unicamente `captureTestProfile` y su regresion; se conserva esa version completa en ambos conflictos.

Se revisaron los auto-merges de PlanMaterializer, ProgramAutoregulationEngine, ProgramProgressEngine y WorkoutViewModel contra ambos padres. El materializador mantiene migracion/resolucion KIND de TM/1RM y consume XRM fisico al marcar el bloque APRE; el canal de propuestas permanece separado del perfil de capacidades. El avance conserva captura TEST previa al metodo y el nuevo cierre APRE con ciclos fuente/destino separados. El VM mantiene guardado durable de referencias, hidratacion TM de ocurrencias y refresh de capturas S4 sin S3.

Esta es una BASE PREPARATORIA: APRE+CAL5b juntos todavia no se han compilado ni ejecutado aqui. No se corrio Gradle/JVM/adb ni se tocaron u11/u20/u22. La validacion central debe repetir referencias TEST/TM, canal APRE y rematerializacion, protecciones hechas/manuales/laterales, y las regresiones de VM/Room/UI enumeradas arriba. El merge limpio y UTF8/diff-check no equivalen a verde.


## Correccion aislada de dependencia comun tras corrida APRE JVM

La corrida central `int-apre-jvm-codex-20261023-3` detecto un defecto real en recetas legacy: sus dias sin id generaban ejercicios temporales y consultaban la bolsa sin identidad persistida. La adopcion posterior recuperaba ids pero no las capacidades ya omitidas. La rematerializacion ahora empareja primero la sesion persistida por dia/orden y sus ejercicios por configuracion/orden, igual que la adopcion existente, para consultar su bolsa antes del calculo. No se consulta por configuracion global ni se altera la identidad generada, la receta ajena o la proteccion de sesiones ejecutadas/manuales. `Cal5bMaterializerReferenceTest.pendingDeclarationsUseLatestPoolCaptureOfTheirOwnKind` mantiene sus expectativas TM160, 1RM200 y cargas128/100, sin debilitarlas.

El fallo `NativeProgressionRealPlanTest.carryForward_isIdempotent_andKeepsLastReferences` era la expectativa anterior de una unica referencia: la bolsa ahora conserva capturas append-only. Se mantiene `once == twice`, se exige prefijo historico intacto y ausencia de eventos identicos duplicados, y se resuelve la referencia vigente con identidad exacta (kind/configuracion/convencion/lado/contexto y campos OBSERVED), conservando carga62.5. No se reduce a last ni se borra historial.

Commit deliberadamente separado de UI y demas CAL5b para poder incorporarlo antes de TM; compatible conceptualmente con u20 APRE `94aaf1865`. No se ejecutaron Gradle/JVM/adb. Pendiente repetir centralmente las dos clases citadas y las regresiones compartidas de rematerializacion/APRE.

La regresion nueva `PlanMaterializerLegacyReferenceTest` conserva dos ocurrencias legacy de igual configuracion con capturas distintas160/140 y pesos128/112; una captura antigua insertada despues no desplaza la latest. El reloj del fixture de arrastre pasa de99/123 a2000/3000 porque las observaciones previas estan fechadas1000: el arrastre debe ser posterior para comprobar latest cronologico, sin alterar el orden de produccion. Se mantienen todas las aserciones anteriores de carga/ids/logs.


### Fixture legacy corregido tras cuarta JVM central

La nueva prueba anterior reducia indebidamente la receta y fue rechazada por los validadores de composicion antes de ejercitar el materializador. Se conserva ahora Wendler BBB completo (cuatro dias, semanas, dosis, AMRAP, roles y `supplementalOf` reales); unicamente se declaran referencias pendientes en t1 y su BBB ya existente. Las capturas por ocurrencia siguen siendo160/140 y la evidencia130@20 insertada despues160@30 sigue sin ganar; con los porcentajes publicados65/50 las cargas esperadas son104/70. Se mantienen ids e historial intactos. Ningun validador se desactiva y no cambia produccion. Pendiente ejecucion central; sin Gradle/JVM/adb local.


## Base Android TM y selectores asincronos

Preflight merge-tree limpio e integracion preparatoria de `846213eda` en u21. Esa corrida central TM Android ejecuto143 pruebas y detecto cinco fallos; no es una base verde. Se corrigen primero tres expectativas del arnes: el campo TM deshabilitado conserva EditableText95 aunque no publica SetText; ahora se comprueban valor95, disabled, error/reintento y cierre solo tras exito durable. La inyeccion de metadata ausente se mantiene hasta recibir el error asincrono. La comprobacion de Semana incluye panel TM y tarjeta de decisiones dentro de la misma rama, conservando navegacion y prohibicion de aceptar/rechazar desde Ver propuesta. No cambian produccion ni expectativas de cargas en este ajuste. Sin ejecucion local.


### Bases derivadas y resultado durable de rematerializacion

El 132.3 esperado frente a126 no era lectura prematura en la confirmacion de perfil: el perfil HIGH_BAR189 se guardaba, pero la receta SQ_LOW conservaba su captura MATERIALIZED_RECIPE180. `syncProfile` ahora sigue exclusivamente el vinculo explicito `authoredLift` cuando la referencia vigente tiene procedencia derivada de receta; no toma cualquier variante por LiftSlot. `RECIPE_PROFILE` conserva ese vinculo y la razon de confirmacion para ediciones sucesivas, sin disfrazarlo de una capacidad manual propia. Las variantes con captura propia MANUAL/TEST/EXPLICIT o invalidacion no heredan cambios por ese vinculo. Se mantienen TM y1RM separados.

El caso pendiente se espera ahora mediante resultado suspend durable. Su perfil189 con prescripcion/captura derivada180 tambien necesitaba reconciliacion: antes del rebuild se actualiza solo evidencia derivada/legacy de perfil, conservando overrides manuales e invalidaciones. Los pesos de bloques no pendientes y sesiones protegidas siguen fuera del rebuild. Reconciliar la referencia global no reescribe la dosis historica. Todo ocurre dentro de la misma mutacion Room. Ademas `rematerializePending` antes ignoraba `false` del commit Room y podia silenciar el fallo: ahora fallo de rebuild o persistencia devuelve failure, publica aviso rojo y conserva perfil/bolsa/semanas/flag; retry confirma exito solo despues de persistir.

Regresiones preparadas: `ExerciseTrainingMaxTest.profileEditsFollowRecipeDerivedVariantsRepeatedlyButPreserveManualVariantCapacity`, `pendingReconciliationUpdatesOnlyProfileDerivedEvidenceAndPreservesConfirmedClear`, y `ProgramDetailViewModelTest.rematerializePending_roomFailurePreservesProfilePoolWeeksAndFlagThenRetryCommits`. Se mantienen intactos132.3 y todas las expectativas de semanas entrenadas, variantes y TM ajustados. Clases a repetir: ExerciseTrainingMaxTest, ProgramDetailViewModelTest, Cal5bTrainingMaxPanelTest y shared de rematerializacion/APRE. No se ejecuto Gradle/JVM/adb; validacion central pendiente.


## Asistente TM: militar como unica marca y cuarta fila

Preflight merge-tree limpio e incorporacion de `6eb44c9d3` (root confirma Android14 clases144/0 y puro135/0). La revision visual posterior detecto un defecto residual real: TrainingMaxWizard hidrataba overhead1RM pero hasValidInput/noMarks y preview solo miraban S/B/D. La validacion y ausencia ahora recorren los mismos cuatro levantamientos del selector; la vista previa agrega Press militar y conserva las llamadas existentes de cuatro parametros mediante argumento overheadTM opcional al final. No se infiere1RM desde un TM almacenado ni se rellenan otros levantamientos.

La prueba nueva `TrainingMaxWizardOverheadTest` usa hoja/overlay real y clicks NATIVE: solo militar80 habilita Guardar, muestra TM72, no ofrece calcular historial aunque haya una estimacion S/B/D, y confirma perfil overhead80/TM72 con otros camposnull. Un TM72 sin1RM sigue sin habilitar guardado ni fabrica una marca. Cuatro casos de viewport real360x740/880 y fuente100/130 comprueban las cuatro filas completas con TextLayoutResult, botones visibles48dp dentro del viewport, scroll nativo hasta militar y callback real de Mas tarde. Escriben PNG opcionales con KPKN_RENDER_DIR, antes y despues de desplegar militar. Se conserva el layout existente de contenido desplazable y footer fijo; solo aumenta una fila cuando militar tiene valor.

Repetir centralmente `com.example.kpkn.screens.programs.TrainingMaxWizardOverheadTest`, `TrainingMaxContentTest`, `TrainingMaxWizardCopyTest` y `com.example.kpkn.render.ProgramOneRmRenderSheetTest`; mantener los144 casos anteriores. No se ejecuto Gradle/JVM/adb; estas nuevas pruebas y PNG siguen pendientes de root.


### Selector de layout de las cuatro filas TM

Tras merge preparatorio limpio de `37a03e127`, se confirma en XML archivados de `int-phase4-tm-relator-android-codex-20261023-2` que los cuatro casos geometricos fallan en la misma comparacion de layoutInput: se pide Sentadilla TM180, pero el ancestro semantico fusionado entrega el layout del titulo Maximo de entrenamiento. No hay evidencia de defecto productivo en ese fallo. `fullText` selecciona ahora el Text exacto con useUnmergedTree=true antes de obtener GetTextLayoutResult. Se conservan igualdad exacta de etiqueta, todos los caracteres, ausencia de elipsis/recorte vertical, bounds de linea, cuatro levantamientos, geometria740/880, botones48, scroll/callbacks y capturas. Solo cambia prueba/informe; sin Gradle/JVM/adb ni cambios de produccion. Pendiente repetir TrainingMaxWizardOverheadTest6casos centralmente.
