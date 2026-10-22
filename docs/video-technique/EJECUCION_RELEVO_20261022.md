# Ejecución del relevo VT · 22 de octubre de 2026

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

## Fase 1 · En curso

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
