# INT-APRE-CHANNEL · Canal de XRM para bloques APRE

Worktree exclusivo: `C:\kw\u28-apre-channel`, rama `codex/sv2-apre-channel`, base provisional `bfaa78fc5`. Implementación local pendiente de integración y validación centralizada.

## Resultado

- Se añadió `ADJUST_XRM` con un payload `ApreXrmCapture` separado de kilos/porcentajes de TM. El resultado conserva XRM en kg y reps; no deriva 1RM ni crea o modifica TM.
- `evaluate` genera el XRM desde S4 observada y marcada por `resolveLiveApreBlock`. S3 y bloques APRE sin captura válida quedan fuera de TM. Las capturas aportadas por señales se descartan y señales ambiguas coincidentes con APRE real no reintroducen ese hit en la regla TM.
- `evaluateApreXrmOnly` permite procesar programas sin receta fuente cuando el bloque APRE lleva su marcador explícito; no sintetiza identidad de protocolo ni perfil.
- `apply` / `resolvePending` implementan OFF (sin acción), PROPOSE (pendiente, sin guardar/aplicar) y AUTO (guardar y modificar solo bloques compatibles de la semana futura). La referencia se agrega al pool para la ocurrencia origen y destinos compatibles; se conserva al rematerializar.
- La deduplicación compara la revisión más reciente del mismo evento e identidad. Así, una corrección 162,5 → 155 → 162,5 agrega revisiones 0/1/2 y un reintento del último payload no agrega otra. La resolución parcial actualiza el mensaje para mostrar solo lo pendiente.
- La asociación del log ahora rechaza configuraciones registradas que contradicen la variante actual, aunque se hayan conservado IDs de ejercicio y serie. También contrasta snapshot/contexto, convención, lado, variante APRE y slots conocidos; ante contradicción no recaptura el set bajo la configuración nueva. Los campos legacy desconocidos siguen siendo tolerados solo cuando no existe evidencia contradictoria.
- `collectAmrapHits`, las sesiones ya ejecutadas y la aplicación/pool de XRM recorren sesiones A/B/C/D de forma recursiva, manteniendo su `sessionId` para el filtrado y las protecciones.
- La asociación de registros admite que el log exterior conserve el `sessionId` de A mientras sus `CompletedExercise` pertenecen a B/C/D. Para APRE, una identidad repetida entre variantes necesita un snapshot/contexto que seleccione una sola; sin esa evidencia se abstiene.
- Al cerrar el ciclo, la última semana evalúa solo APRE de la ocurrencia cerrada y dirige S4 a la primera semana del ciclo siguiente. El ciclo de los logs observados se separa del ciclo destino, por lo que un `weekId` reutilizado no trata el log viejo como una sesión nueva. OFF sigue sin generar acción XRM; el cierre de progresión de autor conserva su orden.
- No se añadieron entidades, DAOs, migraciones ni schemas de Room. Se conserva el resumen de propuesta en `explanation` para el canal de Programas.

## Archivos

Producción: `ProgramScheduleModels.kt`, `ProgramAutoregulationEngine.kt`, `ProgramProgressEngine.kt` y `ApreXrmProgression.kt`.

Pruebas preparadas: `Cal6AprePrescriptionTest` (se amplía el caso real de S4 y rematerialización) y `ProgramAutoregulationApreXrmChannelTest` (flujo manual sin receta, configuración A→B con IDs conservados y snapshot/no snapshot, sesión B + protección de su ID, cierre de ciclo de una semana en AUTO/PROPOSE/OFF, propuesta parcial/rechazo/caducidad, corrección/reversión/reintento, receta mixta con señales obsoletas y propuesta TM mixta). Ambas son pruebas unitarias puras en `src/test`; no se agregó `androidTest`.

## Validación y límites

Revisión estática del diff y `git diff --check`; no se ejecutaron Gradle, JVM, adb, build, instalación ni pruebas, por coordinación centralizada. No hay evidencia aún de compilación ni ejecución. Sin push.


## Validación central de integración · 23-oct

La entrega de origen descrita arriba fue revisada e integrada en u11. CAL4-V.md e INT-4.md registran las correcciones productivas, de compilación y de fixtures, con su evidencia real. Producción420acaf4c: 1989 casos puros únicos verdes en completa más repetición de un fixture; Android final int-apre-android-codex-20261023-2.log:14 clases/201 casos,0 fallos/errores/omitidas, BUILD8min11s, wrapper exit0. XML archivados; tres renders finales revisados. Pendientes el barrido ampliado y la QA de dispositivo; no se declara una única corrida completa JVM verde ni la suite entera del repositorio.
