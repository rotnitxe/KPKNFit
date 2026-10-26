# Remediación de sesión en vivo — 25 octubre 2026

Base de revisión: `1d399c1fc`, checkout `master`, con WIP previo conservado.
Durante la ejecución, otros trabajos avanzaron master desde `0441aa64c` hasta
`e47cfce68`. La comparación final de 3605 fuentes/configuraciones cubiertas por
el manifiesto no detecta cambios respecto al inicio de la validación final.
Esta tarea no ha creado commits ni push.
Estado: **aceptación funcional Android cerrada**. La APK definitiva está instalada
en KPKN Dev;631 pruebas/66 clases consolidadas sin fallos. El recorrido del
emulador terminó y su APK, ajustes y cursor originales están restaurados.
Los apartados cronológicos conservan la evidencia de cada candidato; el último
apartado, `FINAL_ACCEPTANCE.json` y `ACCEPTANCE_MATRIX.json` contienen el estado vigente.

## Contrato de conservación

Las modificaciones del plan no borran observaciones. Un registro conserva su ID,
sesión concreta, variante, ocurrencia, ID de serie, lado y configuración. Si deja
de encajar en el plan pasa al archivo de ejecución. Deshacer el plan no restaura
copias antiguas de registros o borradores. Los reemplazos reciben solamente la
dosis pendiente, incluyendo el lado pendiente de una serie unilateral.

Registro, preparación, correcciones, omisiones y cambios estructurales comparten
la puerta de escritura. El cambio se publica después de la confirmación de Room.
Los derivados se reconstruyen desde observaciones, sobre las referencias previas;
no se añaden muestras por volver a evaluar o reintentar una escritura.

## Técnicas, cantidades y herramientas

- Las miniseries planificadas no se convierten en resultados. Una técnica sin
  miniseries requiere cierre explícito. Saltada y completada son estados distintos.
- La captura de la serie principal conserva también su informe de esfuerzo.
- La principal y cada miniserie informada se confirman como una observación
  `OPEN` con el mismo ID. La fase siguiente aparece después de Room; el punto de
  recuperación se guarda en esa misma escritura. El cierre actualiza la
  observación y dispara avance/descanso una vez. Terminar el entreno cierra como
  `SKIPPED` las técnicas abiertas conservando la principal y las miniseries reales.
  Durante una técnica abierta se rechazan correcciones que pudieran competir con
  su captura; primero debe cerrarse la técnica.
- Distancia y cantidades personalizadas conservan decimales en el payload original.
- Cardio no rellena duración, distancia ni RPE realizados desde la prescripción.
- Acortar sesión tiene propuesta y confirmación propias; el registro rápido es
  una acción separada. El recorte protege IDs realizados fuera de orden.
- Ajustar o rehidratar un descanso conserva su tipo. El objetivo temporal no
  autoriza a reducir automáticamente descansos.
- AMRAP conserva la reserva declarada. Las parciales permanecen separadas de las
  completas. Reducir carga efectiva en asistencia implica aumentar la ayuda.

## Transporte y medios

Se añaden campos opcionales al JSON existente; no se modifica el esquema Room.
Los medios nuevos conservan una asociación inmutable además del mapa de la sesión.
Las asociaciones antiguas ambiguas no se reutilizan automáticamente tras cambios
de estructura o configuración. El álbum sigue siendo el lugar para verlas.
Los modelos iOS y backend conservan los nuevos campos opcionales al transportarlos.
Las referencias de capacidad aceptadas mantienen su versión; los nuevos derivados
de rendimiento tienen una versión distinta.

## Correspondencia con los hallazgos

| Hallazgos | Cambio implementado | Validación específica |
|---|---|---|
| F01–F03 | Archivo de observaciones por identidad; deshacer solo el plan; lateralidad sin clonar o fusionar hechos. La píldora permite deshacer aunque haya un registro posterior. | Conservación, editor, píldora; QA física de borrado del plan y registro posterior. |
| F04 | Remapeo y superseries sobre la variante activa; progreso, borradores y preparación separados por variante. | Conservación B y cambio ida/vuelta con banderas distintas. |
| F05–F06 | Evaluación pura, publicación después de Room, puerta compartida y resultados tipados; correcciones mantienen contexto y prescripción originales. | Fallo/reintento de Room, edición sin cambios, mezcla de deltas y orden estadístico estable. |
| F07–F08 | Resultado independiente del plan, captura del informe principal, estados explícitos y cierre anticipado con las miniseries confirmadas. | Registrador durable y técnicas; regresión adicional del panel manual encontrado en Z Flip. |
| F09 | Referencia inmutable por medio; remapeo por ID de serie y configuración; medios antiguos ambiguos sin proyección automática. | Reordenación/reemplazo con referencia y exclusión de asociación antigua ambigua. |
| F10 | Cardio por voz exige duración realizada; no toma distancia ni esfuerzo prescritos como resultados. | Validaciones de voz/Room y GPS físico: pausa, reanudación, segundo plano, registro real y conservación tras reentrada. |
| F11 | Registro rápido, acortar sesión y aplicar técnica son acciones distintas; propuesta de recorte confirmable y protegida por IDs. Técnicas por lote en una sola transacción. | UltraFastEngine y mutación de técnicas con registros fuera de orden o de un solo lado. |
| F12 | Reemplazo recibe solo dosis pendiente; cursor y paso se resuelven por identidad. | Regresión2/4 y QA Z Flip0/2 con originales archivados; texto del catálogo corregido después del recorrido. |
| F13 | Ritmo no recorta automáticamente descansos; ajustes conservan tipo y propietario. | Descanso estándar físico; calentamiento, entre lados y superserie en emulador; ajustes±15 y regresiones focalizadas de restauración. |
| F14–F15 | Derivados reconstruidos, cantidades por unidad y lados esperados centralizados. | Voz, decimales, lateralidad y resumen. |
| F16 | Preparación, calentamientos, variantes, omisiones y asociaciones de medios en JSON opcional del historial. | Paridad del constructor, conservación, transporte backend y migraciones. |

La etiqueta AMRAP respeta la reserva informada; asistencia aumenta para reducir
carga efectiva; las parciales no completan objetivos de completas ni añaden medio
volumen por defecto. El transporte iOS conserva los campos opcionales y también
separa completas de parciales; no se ha compilado iOS.

## Validación final

- Android: **331 pruebas en 29 clases, cero fallos, errores u omisiones**, más
  `assembleBaseDebug` correcto. Log: `artifacts/live-session-remediation-20261025/android-final-11.log`.
  XML preservados en `tests-final-11/` y agregado en `test-summary-final.json`.
- Las fuentes Kotlin y `app/build.gradle.kts` no cambiaron durante la compilación
  (comparación SHA256 con `source-validation-11-start.json`: cero diferencias).
- Backend: `test_live_execution_transport.py` y `test_descriptive_history.py`: **4 passed**.
- Existe reproducción roja del orden estadístico en `statistics-order-red.log`;
  la regresión pasa en la ronda final. No se afirma que cada hallazgo se reprodujera
  físicamente ni que todas las pruebas se ejecutaran primero en rojo.
- APK final: `apk-accepted/app-base-debug.apk`, SHA256
  `bbf29eb0e1ac80c8e59d8f93ef743141ecccf478db39f368cd232edceb84b5e8`.
  Identidad `com.example.kpkn.dbg`, nombre KPKN Dev, versionCode52, certificado
  `f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4`.
- iOS: transporte opcional y tratamiento de parciales actualizados. No compilado
  en este host Windows.

## QA física y conservación

Toda operación del Z Flip se realizó mediante `phone_run` y `phone.lock`, respetando
las guardas de plegado y foco. Se reutilizó KPKN Dev; no se modificó la app personal.
La tarea «Nutrición PRO» cedió el dispositivo y el emulador tras autorización humana
específica. No se borraron datos ni se instalaron otras identidades.

Antes de actualizar se preservaron APK y Room con WAL. La migración real inicial de
Room34 a Room36 dio integridad `ok` e identidad coincidente con el esquema publicado.
Esta tarea no modificó esquemas Room: Room36 procede del WIP anterior del checkout.
Tras actualizar a la APK final, todos los IDs originales siguen presentes: programa1,
historial1, observaciones4 y medios6. Después de guardar la QA hay dos historiales:
el original y el sintético, ambos conservados.

Recorridos realizados:

- S3 registrada fuera de orden, retirada desde «Editar series», registro posterior
  de S1, cierre/reapertura y reanudación: las observaciones conservan IDs y valores.
- Reemplazo tras2/4 activas: sentadilla trasera a frontal queda en0/2. Los originales
  permanecen archivados. Se corrigió además el texto del catálogo que anunciaba
  incorrectamente la dosis completa.
- Se encontró y corrigió en dispositivo un panel manual de dropset que quedaba
  oculto sobre una serie normal. La prueba Compose correspondiente pasa.
- Con la APK final se registró una principal `20 kg ×12`, RIR2:
  `18baf00c-1476-45a0-abd3-fbc6bdef499e`, estado `OPEN`, sin drops ni rest-pauses.
  Tras `force-stop` de KPKN Dev, arranque y reanudación se recuperó `DROP-SET1/1`.
  Al terminar con lo realizado y confirmar su actualización se conservó el mismo
  ID, instante y referencia, estado `SKIPPED`, cero miniseries; comenzó el descanso.
  Evidencias: `phone/db-open-before-restart/` y `phone/db-recovered-committed/`.
- Descanso estándar: +15 segundos y cinco segundos en segundo plano, retorno HOT.
  Room conserva tipo STANDARD, propietario, fin y ajuste manual.
  Evidencia: `phone/db-rest-background/`. Después se saltó el descanso para cerrar.
- Voz: micrófono autorizado temporalmente por el usuario; «siguiente ejercicio»
  avanzó de sentadilla frontal a peso muerto sin añadir registros. Micrófono
  desactivado después. Este recorrido fue anterior a la APK final y valida solo
  ese comando, no toda la gramática ni las correcciones finales de voz.
- Cámara: Modo visual abrió el dispositivo0 para KPKN Dev y volvió a la tarjeta
  sin capturar ni borrar medios. No valida grabación, análisis ni precisión.
- Cierre con la APK final: resumen e historial contienen las **siete observaciones**
  sintéticas, incluidas las tres archivadas. La omisión parcial del peso muerto
  conserva la serie realizada y sus cuatro series restantes como `omittedSeries`.
  El historial tiene nota **QA AUTOMATIZADA - NO ES ENTRENO REAL** y no queda
  `ongoing_workout`. No se informaron preparaciones en este recorrido; su transporte
  se verifica mediante las pruebas. Evidencia: `phone/db-finished-accepted/`.

El Z Flip quedó en Inicio con la APK final. El buffer de fallos revisado no contenía
crashes de KPKN Dev correspondientes al tramo final; los fallos antiguos no se
atribuyen a esta APK.

## Emulador y límites de aceptación

Se actualizó temporalmente KPKN Dev en `KPKN_Nutrition_Local_AI_QA` / emulator5590,
con respaldo previo de APK y Room36. La app mostró su bienvenida. El arranque del
emulador presentó «System UI isn't responding»; al elegir Wait continuó. El inspector
estándar funcionó; el lector app_process terminó137 en este AVD. Por ello la evidencia
del emulador se limita a instalación y arranque, **no a un recorrido completo de
sesión**. Se restauró la APK original de nutrición con -r y se verificó su hash instalado, sin borrar datos:
SHA256 `a6627f8c418ee49c62500e85cd473abecb5c3b55990d9404f390a7e300d57319`.
La comparación de las 50 tablas de Room antes/después no detectó ninguna fila
original perdida o alterada, incluidos los seis registros de nutrición. El AVD
quedó encendido; se liberó phone.lock y se avisó a la tarea propietaria.
Evidencia: `emulator/restored/preservation.json`.

La implementación F01–F16 y las regresiones focalizadas están completadas. La
aceptación integral todavía requiere GPS físico, grabación/análisis de vídeo,
gramática de voz completa, todos los tipos de descanso en segundo plano, deshacer
tras registro posterior en dispositivo y un recorrido completo en emulador estable.
No se declara precisión biomecánica ni paridad iOS compilada. No se reconectaron
herramientas sobre fallos conocidos; cualquier integración debe conservar estas
limitaciones de QA. Esta tarea no creó commits ni hizo push.

## Continuación de aceptación: voz, cámara y conservación (en curso)

- La cámara frontal ya captura vídeo: clip `a8b2b42b-32c2-4fcc-b831-6e821a2b45cb`,
  15.881 s, 1280×720. Sidecar confirma lente frontal; análisis local devuelve dos
  repeticiones completas y la UI solicita revisión. Conserva la referencia de S2
  y no añade una observación de entrenamiento. No acredita precisión biomecánica.
- La corrección oral reprodujo dos fallos: decimales truncados y esfuerzo omitido.
  El ASR real transcribió «erre pe e siete». Se normalizan números hablados y las
  letras de RPE/RIR, sin sustituir reservas numéricas por valores inventados.
  Se sustituyó el voseo de los mensajes de recuperación por tuteo.
- Candidato d2: assembleBaseDebug y 486 pruebas de 47 clases correctas. Falta repetir
  la corrección completa en dispositivo con el nuevo candidato de persistencia.
- **Nuevo P1 abierto durante QA:** tras un arranque en frío desaparecieron las filas
  `active_program` y `ongoing_workout`; programa, historial, observaciones y medios
  originales permanecieron intactos. Se reprodujo en pruebas que el flush de ciclo
  de vida borra el estado antes de publicarse la carga inicial y elimina un JSON
  de sesión ilegible. Las dos regresiones fallaron con el código anterior.
- Corrección aplicada, pendiente de validación: el flush requiere hidratación
  inicial exitosa y conserva el JSON ilegible hasta descarte explícito. Sin cambios
  de esquema. Se incluyen las pruebas existentes de finalización y concurrencia.
- Recuperación preparada de las dos filas ausentes, insert-only y con hash del
  respaldo. Prueba sobre copia aislada en Android: integridad ok, resto de tablas
  idéntico y segunda ejecución idempotente. La base real aún no se ha recuperado.
- El emulador continúa en configuración de un perfil sintético. Los avances de
  configuración no equivalen a la aceptación de un recorrido de sesión completo.

Evidencias de esta continuación en `artifacts/live-session-acceptance-20261025/`.
GPS físico, descansos restantes y aceptación integral siguen pendientes.

### Fallo adicional reproducido en aproximaciones

En el emulador con candidato97bb, «Marcar hecha aproximación1» guardó 5 kg ×6
pero mantuvo0/2 y no inició descanso. La tarjeta llamaba primero a
`recordWarmupWeight` y después a `markWarmupComplete`; la segunda operación se
rechazaba al encontrar ocupada la puerta compartida. Además, el mensaje afirmaba
«hecha» antes de confirmar Room. Evidencia: `emulator/warmup-first/`.
Se cambia a una única llamada `completeWarmupStep` y se espera a las filas
confirmadas para mostrar éxito. Validación y repetición runtime en curso.

### Resultados posteriores de conservación y voz

- Candidato97bb: assembleBaseDebug y **514 pruebas/50 clases**, sin fallos ni omitidas.
  Incluye regresiones de hidratación inicial y JSON ilegible, más cierre concurrente.
- Recuperación real del Z Flip: se insertaron exclusivamente las dos filas ausentes.
  Integridad ok, resto de tablas idéntico. Después del arranque en frío la ejecución
  y el ID original permanecen. Evidencia: `recovery/actual-recovery.json`.
- Candidato771f: corrección atómica de aproximación validada con assembleBaseDebug y
  **116 pruebas/10 clases**, sin fallos ni omitidas. No se suman estas cifras como
  casos únicos: hay solapamiento con la ronda anterior.
- Voz física en771f: la frase completa corrige a **22,5 kg ×9, RPE7** y conserva
  `80e34d0d-acda-4b75-9c80-2d63cf5e78ee`, sin añadir registros. Evidencia:
  `phone/db-voice-final-rpe7/`. El fallo de RPE que informó el usuario queda corregido.
- Emulador97bb: S3 fuera de orden17,5 kg×10RIR2 se retira del plan y permanece en
  archivo con ID `86a012a7-93b0-4cb9-9251-b93a5313bd72`; S1 posterior20 kg×12
  se conserva. El cierre forzado y la reanudación mantienen mapas de ejecuciones
  y archivo idénticos. `emulator/cold-recovery-proof.json`.
- Descanso estándar del emulador: ajuste+15 visible como1:45, segundo plano y fin
  natural; al volver se muestra descanso cumplido y tiempo extra. El respaldo
  posterior ya tiene descanso nulo porque había vencido: no se atribuye a ese
  respaldo una comprobación de identidad durante un descanso aún activo.

La aceptación integral sigue abierta: GPS físico, descansos restantes, repetición
runtime de aproximaciones en771f y cierre completo del emulador.

### GPS real y nuevo bloqueo P1 de persistencia (aceptación abierta)

El Z Flip obtuvo20+ fijaciones reales; una pausa de unos75s mantuvo129s activos,
puntos y distancia constantes. Tras reanudar,20s en segundo plano añadieron puntos
sin matar el proceso. Se detectó un puente incorrecto entre posiciones antes y
después de la pausa. Reproducción pura:7 tests,2 fallos (111m indebidos y fijación
antigua aceptada). Corrección por segmentos:8 tests puros correctos, pendiente de
compilación Android y repetición física. Datos GPS crudos solo en artefactos locales.

**Nuevo P1 confirmado:** la UI confirmó cardio06:17 y avanzó, pero Room volvió a
`completedSets={}` y al temporizador RUNNING anterior. Una lectura nativa SQLite
atómica descartó que fuese un problema de copiarWAL activos. Archivo:
`phone/native-ongoing-after-cardio.json`. El retorno aHome y la reanudación pueden
mantener dos instancias deViewModel con escrituras de la misma ejecución; se añade
`WorkoutConcurrentOwnerRoomTest` para reproducir el guardado de la instancia vieja.
No se guardó historial de esta sesión fallida. La guarda de foco detuvo su limpieza;
se conserva el estado para repetir tras la corrección. La voz y el GPS están
inactivos; no se afirma que el temporizador antiguo haya dejado de persistir.
La ronda61-clases fue cancelada solo con su JVM identificada35392/2418.2644 para
incluir esta reproducción. La corrección GPS todavía no tiene aceptación Android.

### Repetición física y emulador con candidato4c57

El bloqueo P1 de cardio se corrigió con propiedad exclusiva de escritura por
ejecución: reclamación atómica en el repositorio, token de propietario dentro de
la transacción Room y generación transitoria para impedir publicaciones tardías
de una instancia anterior. La reentrada hidrata el estado confirmado; una instancia
revocada no puede adquirir la puerta, guardar ni detener servicios de la sucesora.
`WorkoutConcurrentOwnerRoomTest` comprueba dos ViewModels, escritura antigua,
rechazo de mutación obsoleta y reentrada. La reproducción física roja anterior
se conserva; no se atribuye una corrida roja al test Room que se canceló antes de ejecutarse.

Validación consolidada:617 pruebas/63 clases sin fallos, errores ni omitidas,
incluyendo las dos clases de arranque repetidas con reemplazo explícito de
ejecución. No fue una única corrida verde: los XML consolidados preservan los
últimos resultados aplicables. APK4c57e0bd, misma identidad, certificado y Room36.

Repetición real por depuración **inalámbrica**, `phone_run` y `phone.lock`:

- GPS:25 puntos observados en dos segmentos activos; una pausa mantuvo11 puntos,
  10,191857m y46s activos en lecturas separadas.20s en segundo plano continuaron
  recibiendo fijaciones sin cierre forzado. La suma de distancias dentro de cada
  segmento coincide con37,2198479m; el puente de20,9995m durante la pausa queda
  excluido. No se afirma exactitud de distancia física: el teléfono estaba estable.
- Guardado: ID `34c0463c-7787-4bf5-b086-2ae33778004d`,151s y0,0405474km,
  RPE desconocido. Lecturas nativas SQLite inmediatas, tras segundo plano y tras
  reinicio/reentrada desde Inicio conservan el mismo registro y estado RECORDED.
- Cierre de GPS: historial etiquetado `QA AUTOMATIZADA - NO ES ENTRENO REAL`,
  una ejecución de Caminata, sin sesión en curso. `phone/final-conservation-proof.json`
  comprueba los valores originales del programa e historial, cuatro observaciones
  y seis medios anteriores. Se permiten exclusivamente campos JSON nuevos y los
  dos IDs de sesión completados por la QA en el progreso de KPKN Dev.
- La sesión GPS fallida anterior se descartó sin guardar historial, después de
  comprobar que no contenía registros durables. No se borraron datos originales.

Emulador4c57:

- Aproximaciones5kg×6 y15kg×3:2/2 confirmadas y descanso de calentamiento visible.
- Lado izquierdo10kg×12RIR2: BETWEEN_SIDES90s; ajuste+15 mantiene ENTRE LADOS.
  Lado derecho con ID propio; ambos permanecen, seguido de descanso estándar.
- Superserie: SUPERSET_INTRA90s, ajuste−15 conserva tipo; SUPERSET_ROUND90s,
  ajuste+15 conserva tipo. Fin natural y paso al siguiente miembro observados.
  El reinicio tardío se hizo después del vencimiento; no prueba restauración
  de un descanso todavía activo. Esta condición conserva cobertura de regresión.
- Añadir una serie pendiente, registrar después22,5kg×12 y omitir el ejercicio
  conserva ambas series hechas, la ejecución archivada, dos aproximaciones y
  dos movimientos de movilidad; solo la serie pendiente se marca omitida.
  Evidencia:`emulator/after-pending-mutation/`.
- No se consiguió accionar la píldora temporal de deshacer después del registro
  con esta automatización; ese orden tiene cobertura de regresión, sin atribuirle
  una reproducción física completa de la inversión.

Dos detalles adicionales detectados en este recorrido:

1. La confirmación GPS mostraba152s del temporizador y el guardado151s del reloj
   GPS. El selector de duración reproduce el fallo1/3 y pasa3/3 tras priorizar el
   mismo tiempo activo GPS que persiste el registro. Ronda Android9:15 pruebas
   en4 clases y assembleBaseDebug correctos. Consolidado posterior:628/65, cero
   fallos, errores u omitidas (`test-summary-with-duration-9.json`).
2. El resumen mostraba dos series para los dos lados, aunque historia y cálculo
   lógico ya agrupan la pareja. Se conecta el resumen al contador compartido;
   la ruta que enumera capturas por lado usa la etiqueta «Registros».
   Compilación y repetición visual finales pendientes en la ronda10.

El AVD se respaldó y detuvo temporalmente para liberar RAM. Sus ocho registros de
nutrición, uso de alimentos, publicación y catálogo coinciden exactamente con el
respaldo previo. El cierre del historial y la restauración de APK y ajustes de la
tarea propietaria continúan pendientes; la aceptación integral permanece abierta.

### Cierre del recorrido del emulador y actualización a979

La ronda10 terminó con assembleBaseDebug correcto;628 pruebas/65 clases
consolidadas sin fallos, errores u omitidas.3615 fuentes/configuraciones Kotlin
permanecieron idénticas durante la validación. APK a9792ff2, misma firma,
identidad y esquema36. Instalada en Z Flip por depuración inalámbrica; tras la
actualización, programas, cuatro historiales, cuatro observaciones y siete medios
coinciden exactamente con la copia anterior. No hay sesión en curso ni servicios
de la app en primer plano. Evidencia:`phone/accepted-update-conservation.json`
y `phone/final-idle-services.json`.

El emulador completó el recorrido y guardó el historial etiquetado como QA.
`emulator/final-history/proof.json` verifica:

- Siete ejecuciones de trabajo con IDs, valores, lados y referencias intactos,
  incluyendo la ejecución retirada del plan y la registrada después del cambio.
- Seis series lógicas al agrupar los dos lados; dos calentamientos reales.
- Marcas de preparación e informes preservados sin inventar informes ausentes;
  seis omisiones explícitas y ausencia de sesión en curso.
- Nutrición, uso de alimentos, publicación y catálogo idénticos al respaldo inicial.

La captura `emulator/accepted-summary-final.png` muestra seis series totales y
una serie de Zancada, conservando sus dos registros individuales. Al volver a
Inicio se reprodujo otro contador antiguo que mostraba siete series; el listado
del historial utilizaba la misma suma de registros. Se conectan ambos al contador
lógico compartido, sin reescribir observaciones. También se corrige el singular
«1 serie». La ronda11 valida este ajuste visual antes de restaurar el AVD.

### Aceptación funcional Android cerrada

- Ronda11: `assembleBaseDebug` y pruebas focalizadas correctos. Consolidado final:
  **631 pruebas/66 clases, cero fallos, errores u omitidas**. Se conserva el último
  XML aplicable de cada clase; no se presenta como una sola corrida monolítica.
  3615 fuentes/configuraciones permanecieron sin cambios durante la validación.
- APK definitiva `apk-final-11/app-base-debug.apk`, SHA256
  `f185523fa6bed9747d85fcd0033ab874d6485dcd80eea9afffdd1c847ce46f51`.
  KPKN Dev, `com.example.kpkn.dbg`, versionCode52, mismo certificado y Room36.
  Instalada por ADB inalámbrico en el Z Flip; arranque en frío correcto y
  conservación exacta de programas, historiales, observaciones y medios tras
  actualizar. La app personal permanece intacta.
- Cierre e Inicio del emulador muestran **seis series lógicas**, manteniendo
  siete ejecuciones individuales. Evidencia:`emulator/accepted-home-counter.xml`.
  El texto del listado de historial usa el mismo contador compartido; la
  comprobación de contenido del historial se realizó directamente contra Room.
- Historial completo: siete ejecuciones con IDs y valores originales, dos
  calentamientos, marcas e informes de preparación conservados y seis omisiones.
  Cero sesión en curso; datos sintéticos identificados como QA.
- Restauración del emulador: APK propietaria9e25eb5b, ajustes originales y cursor
  originalmente vacío. **Ocho registros de nutrición, uso, publicación y catálogo
  idénticos al respaldo**; ninguna otra tabla cambió durante la restauración.
  Arranque posterior correcto en la bienvenida original. Se retiene el historial
  sintético etiquetado. Prueba:`emulator/owner-restoration/proof.json`.
- Dispositivos liberados y tarea propietaria avisada mediante la autorización de
  coordinación de esta QA. No se crearon commits ni se hizo push.

La evidencia física de voz, vídeo frontal y GPS corresponde a los candidatos
indicados arriba; las últimas diferencias son de presentación y contador, con
compilación, regresiones y repetición del contador en el emulador final. No se
atribuye una repetición física exhaustiva de todos los casos a una sola APK.
La inversión del plan después de registrar no llegó a accionarse con la píldora
temporal de esta automatización y queda respaldada por regresión. Las alarmas
exactas estaban denegadas en el emulador: no se afirma precisión en doze profundo.
No se realizó calibración científica de GPS/biomecánica ni compilación iOS en Windows.

Estado y referencias consolidados en
`artifacts/live-session-acceptance-20261025/FINAL_ACCEPTANCE.json`.
