# Integración de fase 4 y regresión final

## Estado más reciente · 23-oct-2026 10:30

JVM final5 está verde en una única corrida: 207 clases/2062 pruebas/0 fallos/0 errores/0 omitidas; fuente `8890750dbe109c8aa5a42bc4b898abb0e0e69de8`, wrapper exit0. Android completo ejecutó 399 clases/3135 pruebas con26 fallos en10 clases; wrapper exit1, sin errores/omitidas/ausentes y436 PNG nuevos. Clasificación, fuentes exactas y límites en [INT-FINAL-REGRESSION.md](INT-FINAL-REGRESSION.md).

Las correcciones posteriores requieren repetición Android. La fuente pura RIR corregida sí está incluida en JVM5. QA de dispositivo sigue abierta y utiliza APK anteriores a las últimas correcciones; hay un defecto G1 confirmado en guardado de modo/dosis, en reparación. Los bloques con horas anteriores que dicen «sin instalación/QA» o «sin completa pura verde» son historial, sustituidos por este estado.


Fecha: 23-oct-2026. Rama `feat/screens-v2`, u11. Base verde de fase 3: `2499dae6b`; fuente validada `2e14b0700`. La regresión dirigida previa sumó 1936 JVM + 169 Android + 173 Android = 2278 casos en tres corridas, sin fallos, errores ni omitidas (INT-1.md). No equivale a toda la suite del repositorio.

## CAT-B cerrado de forma dirigida

Merge `5b4a6e842` desde u19 actualizado con fase 3. La revisión de los renders encontró el identificador técnico `knee_dominant` en una descripción. `64de531b5`, integrado en `b2d37d0ec`, usa el nombre humano de la referencia confirmada, conserva texto manual y abstención, y no cambia ranking, corpus ni métricas.

| Corrida en C:/kw/logs/ | Clases | Casos | Resultado |
|---|---:|---:|---|
| int-catb-android-codex-20261023-1.log | 13 | 80 | 0 fallos/errores/omitidas; BUILD 8 min 23 s; wrapper exit0, antes del ajuste de texto |
| int-catb-jvm-codex-20261023-1.log | 3 | 26 | 0 fallos/errores/omitidas; BUILD 2 min 5 s, 214 s con cola/resumen; exit0 |
| int-catb-android-codex-20261023-2.log | 3 | 27 | 0 fallos/errores/omitidas; BUILD 8 min 5 s; exit0; rutas afectadas repetidas |

No se suman 80+27 como casos únicos ni se atribuyen a una sola corrida. XML archivados por nombre de corrida bajo C:/kw/logs/screens-v2-evidence/.

Renders en C:/kw/shots/int-catb y int-catb-2. Revisados cuatro iniciales y tres nuevos: E7 880/100, F1 detalle 740/130 y F2 detalle 880/100 después del ajuste, contra E7/F1/F2. Se mantienen revisión, cuatro fuentes de portada, procedencia y cues reales, cuerpo desplazable y pie. F1 ofrece Leer más para descripción larga. Las acciones de 48 dp y textos de botones pasan en 740/880 al 100/130. Dispositivo/IME y barrido final pendientes.

## CAL4-V y dependencia compartida de referencias

Merge APRE `f47cc64e5` desde u20 `992a9bbee`. Se incorporan captura durable, canal XRM y almacén JSON compartido; la UI completa de TM sigue pendiente en u21. Las preparaciones APRE↔TM y APRE↔PLAN-OBS resolvieron add/add conservando ambos contratos antes de validar.

| Corrida en C:/kw/logs/ | Clases/casos | Resultado real |
|---|---|---|
| int-apre-jvm-codex-20261023-1.log | 0/0 | Falló compilación principal: import ApreBlockReference y argumento week. Corregidos en 0497b9f68. Sin XML nuevos |
| int-apre-jvm-codex-20261023-2.log | 0/0 | Producción pura compila; falla tipo anidado ProgressAdvanceResult en test. Corregido en 94aaf1865. Sin XML nuevos |
| int-apre-jvm-codex-20261023-3.log | 192/1988 | 5 fallos, 0 errores/omitidas; BUILD 3 min 51 s; exit1, 415 s con cola/resumen |
| int-apre-android-codex-20261023-1.log | 14/201 | 0 fallos/errores/omitidas; BUILD 8 min 41 s; exit0; fuente 539b951e1, anterior a correcciones de identidad futura |
| int-apre-jvm-codex-20261023-4.log | 193/1989 | 1988 correctos; único fallo del fixture nuevo por composición inválida. BUILD 5 min 35 s; exit1, 514 s con cola/resumen; producción 420acaf4c |
| int-apre-jvm-codex-20261023-5.log | 1/1 | Fixture corregido, 0 fallos/errores/omitidas; BUILD 1 min 2 s; exit0; 66993303c sólo cambia test/informe |
| int-apre-android-codex-20261023-2.log | 14/201 | 0 fallos/errores/omitidas; BUILD 8 min 11 s; exit0, XML archivados; producción corregida |

Hay 1989 casos puros únicos verdes mediante la completa y la repetición del único fixture, no una corrida completa final verde. Los cinco fallos funcionales/expectativas de la tercera corrida quedaron verdes en la cuarta. XML de todas las corridas que ejecutaron tests archivados; nunca se renombraron XML antiguos como evidencia de compilaciones fallidas.

### Causas y correcciones

- La receta APRE no declaraba identidad de día. La captura S4 era correcta, pero no se podía enlazar a la ocurrencia futura. `ee283eda7` declara días estables por lift y conserva validaciones de contexto, variante y slot. No se amplía el emparejamiento por configuración global. `64a2850b3` corrige sólo un nombre duplicado del fixture antes de integrarlo; merge `80613d630`.
- Rematerializar recetas legacy adoptaba IDs después de calcular: la bolsa se consultaba con ID nulo y se perdían capturas TM/1RM. `8c2870511`, trasladado como `420acaf4c`, recupera la ocurrencia persistida antes del cálculo con el mismo emparejamiento local de sesión/configuración/slot/orden. Una receta ajena no reutiliza esas ocurrencias. Se conserva el informe completo INT-CAL5B ante el único conflicto documental modify/delete del cherry-pick; no se incorpora todavía la UI de TM.
- La regresión de carryForward esperaba una sola referencia. Ahora comprueba historial conservado, resolución por identidad exacta y reintento idempotente. La cronología del fixture pasa de captura1000 a cierre2000/reintento3000; no se cambia la prioridad temporal de producción.
- La expectativa antigua APRE→TM se sustituye por S4 real 155×17 → ADJUST_XRM 162,5 sin delta ni slot TM, manteniendo las reglas de los otros métodos. S1/S2 se verifican en 82,5/122,5 según incremento real de 2,5 kg, no 81,25 sin redondear.
- La prueba adicional de identidad legacy se rehízo en `3b5eedab0`, trasladada como `66993303c`, sobre Wendler BBB íntegro: dos ocurrencias reales, capturas160/140, dosis104/70 según porcentajes publicados, latest por fecha, IDs y bolsa intactos. Se conservan validadores y AMRAP; no hay exenciones nuevas.

### Renders V7

La primera Android generó 12 PNG APRE3/6/10 en C:/kw/shots/int-apre-1. Revisados apre-v7-3-740-130-proposed, apre-v7-6-880-100-proposed y apre-v7-10-880-130-proposed: Mann, carga propuesta y acciones completos dentro del marco312×425, objetivos48. Son componentes reales en Robolectric; aún no QA de dispositivo. La segunda corrida repitió las medidas y los tres renders con la fuente corregida en int-apre-2; revisión completa, sin recortes. CAL4-V cerrado de forma dirigida; barrido y dispositivo pendientes.

## Siguientes paquetes y barrido final

El resto de TM u21 está preparado; su preflight con los arreglos compartidos es limpio. Después PLAN-OBS u22 y CAL7 u23 actualizado con toda fase 4 validada. CAL7 mantiene 21 recorridos Room; `6cad6e0d0` corrige sólo el fixture manual que confundía el argumento 1RM de Apre.block con un XRM físico. Todavía no se ha ejecutado.

Inventario read-only: 309 clases de workout/sessioneditor + 13 nuevas de fase 4 = 322 FQN. Barrido por clases completas y lotes de hasta15, sin excluir de antemano regresiones. Se añaden las clases Android de training excluidas del arnés y las de programdetail afectadas; JVM cubrirá protocolos/training/workout/calibration/MOT y fixtures por archivo. No usar domain/onboarding/** ni forzar Room/pantallas al carril puro. ExerciseCatalogImageCoverageTest se ejecutará y sólo se clasificará entorno si prueba recursos ausentes del checkout disperso.

El ejecutor serial preparado archiva cada lote y se detiene ante un fallo. La regresión completa del área y QA quedan pendientes; estos resultados no acreditan todos los tests del repositorio.

## Límites

Room v30 congelado, sin cambios Entity/DAO/migración/schema. Se conserva el artefacto ajeno sin seguimiento. Sin push/master; firma sólo por el orquestador. Aún no hay ensamblado de APK, instalación ni QA de dispositivo de esta continuación. QA sólo com.example.kpkn.dbg en emulator-5580 y teléfono mediante phone_run.py/20min, sin tocar paquetes personales ni la sesión de vídeo.


## CAL5b: núcleo verificado y ajuste visual pendiente · 23-oct 07:08

Integración inicial846213eda y correcciones643c0ee7e/e52b21dde integradas en6eb44c9d3. Las tres correcciones de arnés conservan valor, disabled, error/retry, navegación y ausencia de aceptación implícita. Los dos pesos126 en vez de132,3 procedían de evidencia derivada180 que no acompañaba al perfil189; se conserva la expectativa132,3. La sincronización ahora sigue sólo el vínculo real de receta y deja intactas capturas manuales, tests e invalidaciones. La rematerialización confirma el commit durable, rechaza false, conserva datos/flag tras fallo y permite retry.

| Corrida en C:/kw/logs/ | Clases/casos | Resultado |
|---|---|---|
| int-cal5b-jvm-codex-20261023-1.log | 11/133 | 0 fallos/errores/omitidas; anterior a correcciones Android |
| int-cal5b-android-codex-20261023-1.log | 14/143 | 5 fallos: tres arneses y dos casos de bases derivadas desactualizadas; exit1 |
| int-cal5b-jvm-codex-20261023-2.log | 11/135 | 0 fallos/errores/omitidas; BUILD3min23s; wrapper exit0 |
| int-cal5b-android-codex-20261023-2.log | 14/144 | 0 fallos/errores/omitidas; BUILD8min56s; wrapper exit0 |

XML nuevos archivados por corrida. No se suman repeticiones como casos únicos. Los14PNG de int-cal5b-2 incluyen preparación740/880 al100/130; abiertos preparación740/130,880/100 y tmPreview130. Preparación completa y legible. El último render permitió encontrar una omisión anterior en TrainingMaxWizard: hidrata militar pero hasValidInput/noMarks y el resumen sólo consideran sentadilla/banca/peso muerto. Se corrige aparte en u21 con regresión de militar como único dato. **CAL5b aún no se cierra completo por esa omisión visual/funcional**, aunque el núcleo anterior esté verde.

PLAN-OBS tiene un desvío concreto en revisión: APRE10 S4 155×17 con material5kg debe conservar XRM160, no recalcular162,5 con incremento de receta2,5. Se alinearán captura validada, nota y propuesta, incluyendo S3 y evidencia stale. Aún sin validación de esa corrección. CAL7 y barrido final siguen pendientes.

El ejecutor final fuerza tests frescos mediante init exclusivo para Test, desactiva reutilización del wrapper, falla si faltan XML recientes/clases o hay omitidas, conserva el historial al reanudar y comprueba fuente constante por lote. No fuerza recompilaciones ajenas vigentes. QA_FINAL.md inventaría70 pantallas HTML y distingue la entrada71 del lienzo, una pieza Div sin HTML local. No hay todavía APK instalado ni QA del dispositivo de esta continuación.


## Integración TM y PLAN-OBS · 23-oct 08:20

PLAN-OBS integrado en f5d33c379 y correcciones 800207ff3,741fba107,f1c165685,37a03e127. La completa pura3 ejecutó207clases/2062casos con3fallos; la repetición4 de las2clases afectadas pasó27/0, con lo que hay2062casos únicos verdes mediante ambas corridas. Los dos primeros intentos fallaron compilación y ejecutaron cero casos. No se suman repeticiones ni se presenta una única completa verde. El arreglo funcional conserva XRM160 para155×17 con material5kg frente al fallback162,5 con2,5kg; los otros arreglos son imports, tipos/etiquetas, puntuación y fixtures APRE completos.

Android1 de TM+Relator falló compilación por import LiveApreBlock ausente: cero casos, sin reutilizar XML. Android2 (`int-phase4-tm-relator-android-codex-20261023-2.log`) cerró exit1, BUILD8min34s:5clases/17casos,13correctos y4fallos del selector de layout en TrainingMaxWizardOverheadTest. RelatorPlanProgressionContextTest pasó4/4. El selector fusionado obtenía el título del panel; 4c37d4c4c, integrado en a61a6ba98, selecciona el Text exacto sin fusionar y conserva todas las aserciones de texto completo, cuatro levantamientos, pie48dp, viewport, desplazamiento y acciones. Sin cambios de producto en este arreglo. Repetición pendiente junto a CAL7. Los XML propios de Android2 quedan archivados; no se acredita todavía QA de dispositivo.

### Decisión de ejecución para la entrega

La guía v10 permite prioridad0 mediante KPKN_SLOT_PRIORITY=1 para bloqueantes de entrega y el lote amplio del coordinador. Se mantiene una sola corrida propia en todo el árbol, sin matar Java ni interrumpir la sesión de vídeo. Para evitar reservar27veces la cola, el barrido final usará una única reserva con FQN exactos cargados de manifiesto SHA-256, un worker, forkEvery15 y failFast. Esto cambia el límite anterior de15clases por invocación a15clases por JVM de pruebas; no reproduce el orden de los lotes originales. RAM Gradle6GB y heap de pruebas2GB según la configuración global. No modifica herramientas globales, build.gradle ni Room.

El init local fuerza solo Test fresco, verifica fuente/manifiesto/filtros al configurar y comenzar, y marca el inicio real después de la cola/compilación. Solo se archivan XML posteriores a ese marcador. Se rechazan clases ausentes, omitidas, suites inesperadas, errores de parseo o renders sin PNG propios. Ante failFast se conservan los resultados parciales y se prepara explícitamente el alcance restante tras resolver el fallo. El ejecutor anterior por tandas se conserva como fallback. La compatibilidad Groovy/runtime se comprobará primero con el piloto dirigido CAL7+TM, antes del barrido completo.


## Piloto CAL7 y APK de QA · 23-oct08:43

CAL7 integrado en0c642bff1 desde478c569024(u23). Piloto1 int-cal7-tm-pilot-codex-20261023-1 cerró exit1: compileTestKotlin falló por un cierre extra y un import CalibrationStatus inválido; cero casos. El ejecutor rechazó correctamente todos los XML/PNG anteriores por no existir marcador propio de Test. 1b4126649a corrige solo esos dos errores de tests y mantiene21recorridos. Repetición pendiente.

APKdebugQA ensamblado sobre0c642bff1: BUILD6min6s, wrapperexit0. Copia C:/kw/apk/20261023/screens-v2-qa-0c642bff1-base-debug.apk,485534900bytes, SHA256170a26fabb3930af3e5d06fbfd26b564c33c8dc1ca5ee3473eda4c7f8ab77f14. Aapt2 confirma com.example.kpkn.dbg y apksigner verifica AndroidDebug; no firma de distribución. Todavía no acredita instalación/arranque/QA. EmuladorZFlip5_QA5580 iniciado con3072MB; teléfono delegado solo por phone_run.py/20min, sin desbloqueos ni tocar paquetes personales.

El inventario se amplió de395a398clases al incluir tres runners concretos sin@Test propios que heredan RelatorActionAckContract,WorkoutRestModalUiContract ySetExecutionCardUiContract de sharedTest. No se incluyen como suites los contratos abstractos. Los tres manifiestos de catalog/exercises/images estaban en Git pero fuera del sparse-checkout. Se recuperaron mediante sparse-checkout add de esa única carpeta (solo tresJSON), sin modificar contenido ni regenerar datasets. Gitdiff de esa ruta sigue vacío. Por ello ExerciseCatalogImageCoverageTest se ejecutará íntegra; no se excluyen por adelantado sus dos fallos históricos de entorno.

Decisión para obtener todos los hallazgos en una reserva: la variante run_screen_regression_complete.py/final-screen-complete.init.gradle conserva un worker y reciclado15clases, pero usa failFast=false e ignoreFailures=false. Ejecuta todo el alcance y Gradle falla ante cualquier fallo; permite corregir los hallazgos en paralelo después, evitando reservar de nuevo por cada copia antigua. La política queda explícita en estadoJSON y Test.doFirst. Primero se usa en el piloto dirigido2 de9clases/38casos; no se presenta runtime verde hasta su cierre. El ejecutor failFast y el de tandas se conservan como fallback. Fuente, manifiesto y herramientas se fijan porSHA; la comprobación incluye catalog/exercises/images.

Herramientas/manifiestos locales en C:\Users\valen\.codex\visualizations\2026\10\22\01a164f0-4e61-7770-806a-529ee9e76a35. Inventario candidato screen-final-scope-candidate-20261023-2.json:398FQN/27grupos lógicos de≤15, no corridas ejecutadas. La fuente definitiva se regenerará tras corregir el piloto. QA_FINAL permanece pendiente de ejecución real. Sin push/master/firma; Room30 intacto frente a fase3.

## Piloto2, correcciones CAL7 y QA real · 23-oct09:30

int-cal7-tm-pilot-codex-20261023-2.log cerró exit1, BUILD12min29s: nueve clases,38casos,15fallos, cero errores/omitidas y ninguna clase ausente. La recolección completa y los controles de frescura funcionaron en runtime; XML/PNG propios archivados en C:/kw/logs/screens-v2-evidence/int-cal7-tm-pilot-codex-20261023-2. Los17TM/Relator están verdes; CAL7 tiene seis correctos y15fallidos.

Nueve expectativas managed no respetaban el redondeo publicado del material a2,5kg; cuatro APRE leían el snapshot120kg en vez de la sugerencia aplicada122,5kg que consume la UI; RINGS necesitaba esperar grabador/cierre, resolver el aviso real de volumen y limpiar el scope Room entre recorridos. b81131ebb corrige esos arneses y conserva21recorridos. APRE B sí expuso un defecto productivo: se validaban IDs contraA.64109519f resuelve dosis, aplicación y corrección desde la variante activa B/C/D, manteniendo la identidad del log en la sesión base y los controles estrictos de IDs; agrega cinco regresiones. Ambos cambios integrados, pendientes de ejecución central.

Decisión: el próximo barrido completo valida conjuntamente estos arreglos y todas las clases inventariadas; no se repite un tercer piloto para repetir inmediatamente los mismos casos. Un worker/forkEvery15, todos los fallos recolectados y ninguna exclusión nueva. La fuente se congelará después de integrar el defecto de cabecera detectado en QA real.

APKQA fuente0c642bff1 instalado y precompilado en ZFlip5_QA5580. G4 pasó cambio/deshacer y conservación de dosis en740/130; MainActivity real arrancó tras alta completa y DemoSeed. F1 presenta solapamiento reproducido entre título fijo y Tu historial al desplazar: corrección aparte y repetición con APK nuevo. Las70pantallas siguen en revisión.

APK adicional d24370583 versionCode51, SHA2567dd56f54fbb9a21948e91cf0291788a842c69f283d1eb6e5611e949ace8a81c7 y certificado AndroidDebug verificado. Override51 solo en assembleBaseDebug mediante init local, sin cambiar versión del repositorio ni release. El teléfono rechazó antes código50 por tener51; el intento con51 se detuvo por uso reciente (11min, regla20). Sin downgrade forzado, desbloqueo ni borrado. Aún no se acredita instalación telefónica.
