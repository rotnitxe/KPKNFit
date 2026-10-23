# Integración de fase 4 y regresión final

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
| int-apre-android-codex-20261023-2.log | pendiente | En marcha sobre la producción corregida; no acreditar resultado hasta XML y cierre del wrapper |

Hay 1989 casos puros únicos verdes mediante la completa y la repetición del único fixture, no una corrida completa final verde. Los cinco fallos funcionales/expectativas de la tercera corrida quedaron verdes en la cuarta. XML de todas las corridas que ejecutaron tests archivados; nunca se renombraron XML antiguos como evidencia de compilaciones fallidas.

### Causas y correcciones

- La receta APRE no declaraba identidad de día. La captura S4 era correcta, pero no se podía enlazar a la ocurrencia futura. `ee283eda7` declara días estables por lift y conserva validaciones de contexto, variante y slot. No se amplía el emparejamiento por configuración global. `64a2850b3` corrige sólo un nombre duplicado del fixture antes de integrarlo; merge `80613d630`.
- Rematerializar recetas legacy adoptaba IDs después de calcular: la bolsa se consultaba con ID nulo y se perdían capturas TM/1RM. `8c2870511`, trasladado como `420acaf4c`, recupera la ocurrencia persistida antes del cálculo con el mismo emparejamiento local de sesión/configuración/slot/orden. Una receta ajena no reutiliza esas ocurrencias. Se conserva el informe completo INT-CAL5B ante el único conflicto documental modify/delete del cherry-pick; no se incorpora todavía la UI de TM.
- La regresión de carryForward esperaba una sola referencia. Ahora comprueba historial conservado, resolución por identidad exacta y reintento idempotente. La cronología del fixture pasa de captura1000 a cierre2000/reintento3000; no se cambia la prioridad temporal de producción.
- La expectativa antigua APRE→TM se sustituye por S4 real 155×17 → ADJUST_XRM 162,5 sin delta ni slot TM, manteniendo las reglas de los otros métodos. S1/S2 se verifican en 82,5/122,5 según incremento real de 2,5 kg, no 81,25 sin redondear.
- La prueba adicional de identidad legacy se rehízo en `3b5eedab0`, trasladada como `66993303c`, sobre Wendler BBB íntegro: dos ocurrencias reales, capturas160/140, dosis104/70 según porcentajes publicados, latest por fecha, IDs y bolsa intactos. Se conservan validadores y AMRAP; no hay exenciones nuevas.

### Renders V7

La primera Android generó 12 PNG APRE3/6/10 en C:/kw/shots/int-apre-1. Revisados apre-v7-3-740-130-proposed, apre-v7-6-880-100-proposed y apre-v7-10-880-130-proposed: Mann, carga propuesta y acciones completos dentro del marco312×425, objetivos48. Son componentes reales en Robolectric; aún no QA de dispositivo. La segunda corrida repetirá la comprobación con la fuente corregida.

## Siguientes paquetes y barrido final

El resto de TM u21 está preparado; su preflight con los arreglos compartidos es limpio. Después PLAN-OBS u22 y CAL7 u23 actualizado con toda fase 4 validada. CAL7 mantiene 21 recorridos Room; `6cad6e0d0` corrige sólo el fixture manual que confundía el argumento 1RM de Apre.block con un XRM físico. Todavía no se ha ejecutado.

Inventario read-only: 309 clases de workout/sessioneditor + 13 nuevas de fase 4 = 322 FQN. Barrido por clases completas y lotes de hasta15, sin excluir de antemano regresiones. Se añaden las clases Android de training excluidas del arnés y las de programdetail afectadas; JVM cubrirá protocolos/training/workout/calibration/MOT y fixtures por archivo. No usar domain/onboarding/** ni forzar Room/pantallas al carril puro. ExerciseCatalogImageCoverageTest se ejecutará y sólo se clasificará entorno si prueba recursos ausentes del checkout disperso.

El ejecutor serial preparado archiva cada lote y se detiene ante un fallo. La regresión completa del área y QA quedan pendientes; estos resultados no acreditan todos los tests del repositorio.

## Límites

Room v30 congelado, sin cambios Entity/DAO/migración/schema. Se conserva el artefacto ajeno sin seguimiento. Sin push/master; firma sólo por el orquestador. Aún no hay build, instalación, QA ni APK de esta continuación. QA sólo com.example.kpkn.dbg en emulator-5580 y teléfono mediante phone_run.py/20min, sin tocar paquetes personales ni la sesión de vídeo.
