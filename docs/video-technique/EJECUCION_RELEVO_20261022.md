# Ejecución del relevo VT · 22 de octubre de 2026

## Estado vigente · prevalece sobre las entradas históricas

- Fases 0 y 1 completadas; M4b integrado en `108146025`. Integración conserva motor 9 y Room v29.
- Teléfono aplazado por orden expresa del usuario: no reanudar extracción MM-Fit, QA, ADB ni VPN hasta su regreso. Se conservan 72/616 resultados; Fases 2 y 5 pendientes.
- Fase 4: compilación Android y nueve clases de Room/comparador correctas, 39 pruebas sin fallos, errores ni omisiones; log `C:/kw/logs/vt-fase4-room-compare-20261022.log` (sesión57868 terminada). Faltan las demás tandas, JVM completo y R8.
- M5 se desarrolla en `codex/vt-m5-20261022`, sin integrar. Se preparan sintéticos y CV real sobre ajuste OpenPose/vt-tune; los controles de partida del motor9 ya terminaron. OpenPose es preliminar hasta disponer de MediaPipe completo.
- Única corrida Gradle propia en curso: sesión35330, `C:/kw/logs/vt-m5-boundary-cv-synthetic-20261022.log`, tres clases sintéticas M5 en el carril JVM. Sin bancos ni teléfono.

Las secciones siguientes conservan la cronología; sus sesiones «actuales» anteriores ya no describen el estado vigente.

## Autorización y alcance

Se ejecuta `PLAN_RELEVO.md`, en su orden. El usuario autorizó expresamente subagentes Sol 6 High y Luna 6 Max en lugar de la obligación de Haiku, y las lecturas/escrituras necesarias para aplicar el plan. Se preservan el trabajo concurrente, los datos personales del teléfono, los bancos de solo lectura y la separación entre ajuste y control ciego. Sin push.

## Fase 0 · Contexto completado

Leídos el plan, RELEVO, BANCO_COMPLETO, M4, UI_PLAN, DECISIONES, las guías de Gradle y Haiku y las notas de contexto indicadas.

- Integración: `feat/vt-ui` en `3596ae3a2`.
- M4b: WIP en `C:/kw/vt-a`, rama `vt/m4b`, base `d6f0bd7bc`, 28 archivos iniciales.
- El worktree M4b tiene sparse checkout. Las consultas de Git y procesos fuera de la carpeta autorizada requieren acceso escalado al host; con ese acceso funcionan correctamente.
- Room permanece en v29. No se autoriza ninguna migración nueva para VT.
- El examen histórico ya está consumido. M5 se valida con desarrollo y los controles nuevos descritos en el plan; los agentes que ajusten no reciben datos individuales ciegos.
- El objetivo de M5 ALTA >=98 % no equivale por sí solo al listón de mercado >=99 % de BANCO_COMPLETO.

## Fase 1 · Completada en 108146025

La corrida 2 no sigue activa ni en la cola. Su log quedó sin línea final; los XML fechados el 22 de octubre a las 02:01 contienen 268 pruebas: 266 correctas y 2 fallos, ambos en `WorkoutVideoRepsRenderSheetTest.restLines100/130` por espera de miniaturas en el arnés.

La revisión encontró dos rutas anfitrionas que aún aplicaban parciales con confianza BAJA: `SetExecutionCard` (Usar) y `WorkoutV2Body` (Corregir desde Modo visual). Se corrigen con regresiones antes de la verificación final. No se cambia dominio ni Room.

Renders existentes al 100 % y 130 % en `C:/kw/shots/vt/ui/m4b`. Revisadas inicialmente las sugerencias de parciales, el menú con confianza MEDIA y el desglose de análisis al 130 %; la validación completa se cierra después de regenerar los renders afectados.

## Extracción MM-Fit · Reanudada

El conductor anterior ya no estaba vivo. Había 60/616 trazas y el log terminaba en un error de adb. Tras la reconexión del Z Flip confirmada por el usuario, se reanudó con la orden de RELEVO, sin reinstalar la app ni alterar datos personales.

- Proceso al arrancar: 17928.
- APK esperado: `28e7b3d3e58a5936fd03fb205d99b379840587d02d7b3bd60230b5f53950a990`.
- Salida: `C:/kw/vt-mmfit/phone/runall-resume-20261022.log`.
- Errores: `C:/kw/vt-mmfit/phone/runall-resume-20261022.err.log`.
- Inicio confirmado: 616 seleccionados, 60 completos, 556 pendientes, 47 lotes.

## Fases posteriores

Fases 2–7 pendientes. La fase 8 requiere una petición posterior del usuario. No se ha declarado cumplimiento de fiabilidad, QA físico, firma ni entrega.

### Avance registrado durante la validación

- Código M4b guardado en `3a0ae5054` (16 archivos de producto).
- Pruebas y regresiones guardadas en `7d078a373` (15 archivos de prueba).
- `git diff --check` correcto; sin cambios en dominio ni Room.
- Única corrida Android actual: 16 clases concretas, `KPKN_RENDER_DIR=C:/kw/shots/vt/ui/m4b`, log `C:/kw/logs/vt-m4b-codex-20261022-1.log`. Entró en la cola a las 16:26; no se duplica ni se cambia la prioridad de otras sesiones.
- Se añadieron después cuatro estados en `LiveCardRenderSheetMediaTest` para el Modo visual: estiramiento, acortada, MEDIA y BAJA. Este archivo aún requiere commit. Después de terminar la corrida actual, ejecutar únicamente sus métodos `analisisEnLaTarjeta100` y `analisisEnLaTarjeta130`, con el mismo directorio de renders; revisar PNG y ausencia de recorte antes del merge.
- La revisión de renders iniciales también detectó un recorte preexistente del botón «Jugar mientras descansas» en el descanso compacto al 130 %. No corresponde al texto de VT; queda registrado para valorar en QA, sin confundirlo con una regresión de las parciales.
- La extracción MM-Fit confirmó su preflight (`batch=m1 ... complete`) y continúa con el candado propio del conductor. A las 16:35 seguían publicados 60 pares; las trazas se publican por lotes.

### Preparación para cerrar la Fase 2

- Herramienta externa revisada: `C:/kw/vt-mmfit/tools/build_mmfit_mp_bank.py`.
- Solo lectura: `python -I C:/kw/vt-mmfit/tools/build_mmfit_mp_bank.py --check-only`; devuelve 2 mientras falten extracciones. Verifica las 616 filas y 1.848 hashes del banco original; el corte actual confirma 60 recibos/timings completos. No crea `bank-mp`, staging ni bytecode.
- El constructor fija el APK `28e7b3d3e58a5936fd03fb205d99b379840587d02d7b3bd60230b5f53950a990`, exige coherencia de estado y timing y valida preflight GPU, stride 1 y al menos 20 Hz. Copia etiquetas byte por byte y publica un destino nuevo con rename después de verificarlo; no modifica el banco original.
- Cuando estén las 616 filas completas: ejecutar el mismo script sin `--check-only`, después el evaluador de RELEVO con `KPKN_FULL_BANK_EXAM=0` explícito y `-PappRoot` de integración. Se preservan los dos fallos históricos esperados de `FullBankReportTest`: `optionalFullBankReport` exige 475 filas del banco anterior y `historicalAutomaticPlateIsNowRejectedAndPoseExplainsTheDifference` busca una fila que MM-Fit no contiene. Los resultados se producen antes de esas aserciones; cualquier otro fallo exige revisión.
- Los agregados se generan con `exam_report.py report-mp-1 development-clips`, evitando publicar los IDs que el script imprime al final. La comparación y los tiempos siguen pendientes de completar la extracción.
- Los casos adicionales de Modo visual ya están guardados en `4af1e54b1`. Los métodos `analisisEnLaTarjeta100/130` todavía deben ejecutarse después de la corrida actual.

### Último estado · continuidad autorizada

El usuario autorizó explícitamente la continuación programada cada 20 minutos. Se creó el heartbeat `continuar-el-plan-de-relevo-vt` (ACTIVE); no crear otro. El rechazo previo de revisión automática quedó resuelto tras esa autorización expresa.

La corrida `vt-m4b-codex-20261022-1.log` terminó con fallo de compilación por memoria JVM insuficiente (heap 4 GiB, `SetInputCardV2`, `Not enough memory to run compilation`, BUILD FAILED). No llegó a ejecutar las pruebas nuevas. No se cambió código por ese error ni se mató Java.

Se lanzó una única repetición por la cola con `-Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8`:
- Log actual: `C:/kw/logs/vt-m4b-codex-20261022-2.log`.
- Sesión de herramienta inicial: 24580.
- Incluye las 16 clases de antes Y los dos métodos `LiveCardRenderSheetMediaTest.analisisEnLaTarjeta100/130`. Si esta corrida pasa y los XML confirman ambos métodos, NO lanzar una tercera corrida rutinaria para esos renders.
- Revisar XML y PNG, resolver fallos reales, luego merge --no-ff en integración. Fase 1 todavía no se declara completa.

MM-Fit publicó el primer lote reanudado: 72/616. En 72 timings: mediana 114,6 s de análisis, vídeo mediano 21,57 s y ratio mediano 5,32. Quedan 544 clips y 7,47 GiB de vídeos por enviar. El cálculo orientativo es unas 17 horas de análisis adicional, más transferencias y pausas; no es una promesa de plazo ni precisión final.

El usuario necesita llevar el Z Flip a otro lugar y especificó DATOS MÓVILES. Está en preparación una conexión privada Tailscale + ADB autenticado; todavía NO está configurada ni verificada. Tailscale no estaba instalado en el PC ni el teléfono. Se inició instalación oficial en Windows mediante winget, paquete Tailscale.Tailscale 1.104.2, hash MSI verificado por winget; sesión de herramienta 80578 seguía en instalación al último control. No iniciar un segundo instalador. El teléfono aún requiere instalación, inicio SSO y aprobación VPN por el usuario. No se habilitó ADB TCP/5555 ni se tocó su configuración de depuración. Antes de depender de acceso móvil, validar conexión con Wi-Fi apagado; conservar el acceso local actual mientras se configura. El volumen pendiente de vídeos debe tenerse en cuenta antes de enviarlo por la red móvil; se está valorando precarga por Wi-Fi, sin modificaciones aún del conductor.

## Cambio expreso del usuario · pruebas del teléfono aplazadas

El usuario CANCELÓ la configuración remota y dejó las pruebas del Z Flip pendientes hasta que vuelva, manteniendo la orden de continuar el plan. Esta instrucción prevalece sobre el seguimiento anterior y las instrucciones de reanudar automáticamente el conductor.

- NO reanudar MM-Fit ni QA del teléfono, VPN, ADB remoto o precargas hasta que el usuario avise que regresó.
- Se detuvo únicamente el conductor propio PID17928 y se pidió detener `com.example.kpkn.dbg/com.example.kpkn.debug.TechniqueBenchService` mediante phone_run. La verificación posterior por `dumpsys activity services` confirmó `(nothing)`; no hay servicio del banco activo ni candado phone.lock.
- Se preservan 72/616 resultados completos en el PC. El estado del extractor puede conservar el último texto `extracting`, porque la pausa externa no altera los recibos originales; este registro y la ausencia de proceso/servicio son la evidencia actual de pausa.
- Tailscale1.104.2 quedó instalado en Windows, sin autenticación; se ejecutó `tailscale down`. No se habilitó ADB TCP ni se llegó a configurar la VPN del teléfono. No desinstalar ni volver a configurar sin nueva indicación.
- El heartbeat `continuar-el-plan-de-relevo-vt` fue actualizado para respetar la suspensión del teléfono y continuar SOLO tareas de PC independientes.
- Puede prepararse M5 sobre MM-Fit OpenPose y vt-tune ya disponibles, manteniendo los folds0–3 de ajuste y fold4/dev-check ciegos del orquestador. La medición completa MediaPipe (Fase2) y el QA físico (Fase5) siguen pendientes y no deben declararse aprobados.
- Adelantar verificación de PC/R8 sin instalación y documentación según dependencias. La entrega definitiva mantiene sus gates pendientes del teléfono.

## Avance de PC tras aplazar el teléfono

La corrida M4b `vt-m4b-codex-20261022-2.log` terminó: 285 pruebas, 283 correctas y 2 fallos reales de texto recortado en `restLines100/130`. Los 17 XML incluyen los dos métodos nuevos del Modo visual, correctos. Se revisaron los PNG de parciales en estiramiento, MEDIA y BAJA del Modo visual al 130 %: texto completo y distinción de confianza visibles.

- Corrección `981423d46` en `vt/m4b`: mide también el resumen compacto de la línea del descanso, conserva las acciones en la segunda línea cuando es necesario y adapta a 12/16sp si el compacto aún desborda. La lámina crece a 1240dp para mostrar todos los estados; se conserva la aserción de ausencia de overflow.
- Única corrida Gradle propia actual: sesión `98724`, log `C:/kw/logs/vt-m4b-codex-20261022-3.log`. Solo repite `WorkoutVideoRepsRenderSheetTest`, `WorkoutRestVideoLineTest` y `WorkoutVideoPartialsTextTest`, afectadas por el arreglo. No duplicarla. Fase 1 aún espera sus resultados, inspección PNG y merge --no-ff.

### M5 · Infraestructura y partidas

- Constructor externo `C:/kw/vt-mmfit/tools/build_mmfit_m5_packages.py`, revisado: verifica el SHA padre fijado y los 1.848 archivos, no modifica el banco y publica dos destinos nuevos tras comprobar las copias. Las etiquetas se conservan byte por byte y no hay vídeos.
- Paquetes ya construidos: `m5-openpose-tune` (509 filas, 17 grupos; G1 405/G3 104) y `m5-openpose-control` (107 filas, 4 grupos; G1 89/G3 18). Cada uno contiene físicamente solo su partición. El paquete de ajuste no contiene filas, rutas ni archivos del control.
- Commits `2cd70f780` y `5b7282551`: `FullBankMmfitReportTest` exige `EXAM=0` explícito, paquete/partición válidos, salida nueva/vacía, recuentos dinámicos y todas las filas procesadas. Publica agregados antes de fallar por una extracción incompleta. Su desglose de folds está rotulado `splitPlanOnly=true`: no acredita CV.
- Partida motor 9 de vt-tune: `C:/kw/vt-tune-out/m5-baseline-20261022`, log `C:/kw/logs/vt-m5-tune-baseline-20261022.log`, BUILD SUCCESSFUL. 720/720 procesados; G1: salida 269/345, FULL exactas 230/269, ALTA 45/45, MEDIA 104/105; G2 erróneas entre ofrecidas 1/21. «Errónea» usa la definición del evaluador (rango), distinta de «no exacta».
- Partida motor 9 de MM-Fit ajuste: `C:/kw/vt-mmfit/m5-out/openpose-baseline-tune-20261022`, log `C:/kw/logs/vt-m5-mmfit-baseline-tune-20261022.log`, BUILD SUCCESSFUL, 509/509 procesados. Tríceps 0/52 exactas (ALTA 0/34), zancada 41/51 (ALTA 41/49). No es la medición MediaPipe ni incluye el control.
- Nuevo worktree aislado: `C:/Users/valen/Documents/KPKNFit/.claude/worktrees/vt-m5-codex-20261022`, rama `codex/vt-m5-20261022`, base `5b7282551`; sparse checkout para dominio/tests/docs, no build Android allí. Su brief `docs/video-technique/briefs/M5.md` fija datos permitidos, CV real y límites. El agente ajustador comienza el diagnóstico de bordes; no recibe datos individuales de los controles.
- Fase 6 adelantada en `4a027c024`: mapas de arquitectura, pantallas, estructura y decisiones; rutas/sidecars/referencia PERSONAL/parciales y privacidad estática. D27 conserva su evidencia histórica API36 y distingue la verificación física pendiente. Revisar los IDs nuevos de decisiones para evitar colisión con UI_PLAN. No hay cifras finales ni cierre de privacidad dinámica.

Las Fases 2 y 5 siguen aplazadas por el usuario. M5 se prepara con los datos disponibles; no se entrega ni integra un candidato que incumpla sus controles y dependencias.

## Cierre de M4b y siguiente medición

- La corrida selectiva `vt-m4b-codex-20261022-3.log` terminó BUILD SUCCESSFUL: 27 pruebas, cero fallos, errores u omisiones. Se revisaron los PNG nuevos `video_rest_lines_100/130.png` completos, los chips/sugerencias, los cuatro casos del Modo visual y el resumen/barras de parciales al 100 % y 130 %. La repetición resuelve los dos fallos de la corrida de 285 sin repetir sus 258 pruebas no afectadas.
- Merge local --no-ff de `vt/m4b` en `feat/vt-ui`: `108146025`, sin conflictos, cuatro commits M4b y 32 archivos; sin dominio ni Room. Fase 1 completada. Los IDs de decisiones M4b quedaron separados de los de UI_PLAN en `d44ea7b52`.
- Se actualizan los mapas para describir M4b como integrado. La aceptación física sigue pendiente y no se instaló nada después de la orden de aplazar el teléfono.
- Única corrida Gradle propia actual: JVM de partida del control MM-Fit, motor 9 de integración, sesión `87601`, log `C:/kw/logs/vt-m5-mmfit-baseline-control-20261022.log`, salida nueva `C:/kw/vt-mmfit/m5-out/openpose-baseline-control-20261022`. Usa el harness MM-Fit con EXAM=0. Su paquete y resultados individuales son exclusivos del orquestador.
- M5 tiene un candidato de guarda de borde en el worktree aislado, todavía sin commit ni validación. No suma una repetición cuya primera mitad no se observó; estudia marcar la incertidumbre para impedir publicarla como segura. La revisión independiente exigió excluir intervalos entre mini-series y usar la procedencia lateral de la señal correcta. Estos cambios y sus regresiones están en curso; no dar el candidato por aprobado ni presentar las proyecciones estáticas como medición del motor 10.
- Erratum de metadatos de MM-Fit: `C:/kw/vt-mmfit/tools/ERRATUM_M5_FOLD_SEMANTICS.md`. El plan usa sesión mod5; el manifiesto fuente decía sujeto. Los paquetes publicados no se modificaron y sus hashes se preservan; la descripción del constructor se corrigió para futuras construcciones. No cambia ninguna etiqueta, grupo o asignación de fold.

## Estado posterior · candidato M5 y verificación de PC

- Control MM-Fit de partida terminado: `vt-m5-mmfit-baseline-control-20261022.log`, BUILD SUCCESSFUL, 107/107 procesados, motor9. En G1 (89), salida89, FULL exactas65/89, ALTA64/81 y MEDIA1/4. Solo se publican agregados; el ajustador no recibe resultados individuales.
- Candidato aislado: guarda `initialFarEndReturn`, sin añadir FULL ni fases. Usa `selectedSignalSide` transitorio para SINGLE/BOTH_SYNC, excluye ALTERNATING y obtiene cadencia solo de pares FULL de la primera mini-serie. Mantiene los umbrales físicos existentes y marca `censoredStart`. Motor10 únicamente en esa rama; integración sigue en9.
- Primera corrida sintética M5, sesión6764, `C:/kw/logs/vt-m5-boundary-synthetic-20261022.log`: 17 pruebas, 15 correctas, dos fixtures nuevas incorrectas. `TechniqueAnalysisEngineTest` pasó8/8. El ajustador corrigió las fixtures de mini-series y dejada terminal, conservando sus objetivos de regresión; falta repetir los dos filtros antes de medir el candidato o hacer commits de producto.
- Única corrida propia actualmente pendiente: sesión23941, `C:/kw/logs/vt-m5-old-bank-baseline-control-20261022.log`, partida motor9 del control histórico de desarrollo (175 filas esperadas), salida nueva `C:/kw/vt-m5-control/old-bank-baseline-20261022`. EXAM=0, sin tocar el examen consumido. En el último control estaba en cola JVM por RAM disponible mientras `u14` ocupaba Android; no lanzar una segunda corrida propia.
- Esa orden recibió un rechazo automático inicial por confusión entre orquestador y ajustador. Tras comprobar PLAN_RELEVO59–60/121 y GRADLE_GUIA56–58, la misma acción fue aprobada: `/root` evalúa controles, el ajustador es otro agente y solo recibe agregados; `KPKN_SLOT_FORCE=1` evita la caché al cambiar de banco mediante variables y no cambia carril/prioridad. Rechazo resuelto, sin usar otra vía.
- Fase4: `3597310dd` corrige solo la fixture de `TechniqueCompareViewModelTest`: BarTrack confirmado para MCV y regresión sin sidecar. Aún no ejecutada. `WorkoutReadinessSheetAdjustTest` tiene un selector obsoleto ajeno a VT y no se cambia. Se está corrigiendo el inventario externo `C:/kw/logs/vt-fase4-inventario-20261022-feat-vt-ui.txt`: verificar FQN reales, no inferir paquetes ni soporte JVM por ausencia de Robolectric; no usar ciegamente su primera versión.

Siguiente orden: terminar la corrida23941 sin duplicarla; repetir sintéticos M5; fijar commit del candidato si pasa; medir ajuste por banco/fold y comprobar CV real para cualquier parámetro ajustado; después controles ciegos solo por el orquestador. No fusionar el candidato sin esos gates. Adelantar tandas Android/R8 de Fase4 según disponibilidad, sin instalación.

## Verificación de Room y protocolo M5

- La partida del control histórico motor9 terminó: sesión23941, `vt-m5-old-bank-baseline-control-20261022.log`, BUILD SUCCESSFUL, 175/175 procesados. G1: salida63/86, FULL exactas50/63, ALTA9/10, MEDIA21/24; G2 erróneas entre ofrecidas1/4. No se reabrió el examen histórico.
- La tanda Android sesión57868 terminó BUILD SUCCESSFUL en8m9s, nueve clases y39 pruebas: DAO/migraciones VideoAnalysis (15+7+2), WorkoutMedia (3+1+1+1+5) y comparador (4). Cero fallos, errores u omisiones. La corrección de fixture `3597310dd` queda verificada y `compileBaseDebugKotlin` también pasó como dependencia. No cambió Room ni sus schemas.
- El inventario corregido es `C:/kw/logs/vt-fase4-feat-vt-ui-20261022/README.txt` y sus once listas. Contiene312 FQN únicos verificados,80 de biomecánica/JVM; las pantallas, Compose, Room y repositorios quedan en Android con clases concretas. Descartar la primera lista que infería paquetes/carriles.
- El ejecutor de CV M5 usa la rejilla predeclarada0/0.5/0.75/1.0, tres folds para elegir y uno retenido para medir. Limita la pérdida de exactas G1 ofrecidas por banco/fold, conserva la guarda G2 y excluye G3 de selección/metas. Empates y consolidación usan el parámetro más conservador; no atribuye generalización estructural a esa CV.
- Revisión independiente corrigió selección/metas contaminadas por G3, metas fijas sin denominadores, default provisional activo y falta de identidad del corpus legacy. El default vuelve a0.0 hasta elegir; las pruebas activan explícitamente la guarda. La CV exige `KPKN_M5_LEGACY_MANIFEST_SHA256`, verificado antes de parsear filas; SHA autorizado de vt-tune: `4a1cd07b421917c29034a5e22dc350b888f542f080cc798eb8dbe2c8a061788d`.
- Ejecutor y candidato se validan primero con tres clases sintéticas, sesión35330. La CV externa se omite en esta corrida por no configurar bancos. Ninguna cifra del motor10 está aprobada todavía.
