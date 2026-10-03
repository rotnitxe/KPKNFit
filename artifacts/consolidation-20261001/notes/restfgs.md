# restfgs: carrera start->stop del servicio de descanso (WorkoutRestForegroundService)

Solo edicion; no se ejecuto Gradle/adb/emulador/tests. Base de comparacion: HEAD (el diff del servicio contiene unicamente este paquete). Los archivos ya eran CRLF en el arbol de trabajo (`git ls-files --eol`: `i/lf w/crlf`); los editados y los nuevos de `main`/`test` quedan CRLF sin mezcla (contado con `tr -cd`, CR == LF).

## Diagnostico (con evidencia)

Logcat `device-evidence/instrumentation/base/20261002T043651405422Z-6c1b74-workout-avd/logcat.txt` l.4229 ss. (04:37:19):

```
.486 ActivityManager: Background started FGS: Allowed [... act=...START_WORKOUT_REST_FGS ...]
.496 ActivityManager: Bringing down service while still waiting for start foreground: ServiceRecord{... WorkoutRestForegroundService}
.519 AndroidRuntime: FATAL EXCEPTION: main ... ForegroundServiceDidNotStartInTimeException
```

10 ms entre el start y el "bring down". Cadena exacta del test `manual_cancel_clears_active_rest_overlay_before_terminal_ack` (`vm.startRestTimer` y luego `vm.cancelWorkout`): `WorkoutRestAlertManager.scheduleRestEnd` -> `WorkoutRestForegroundService.start` -> `Context.startForegroundService` (el AMS marca `fgRequired`), y enseguida `cancelRestAlerts` -> `WorkoutRestForegroundService.stop` -> `Context.stopService` ANTES de que `onStartCommand` llegue a `startForeground`. El AMS (bringDownServiceLocked con `fgRequired`) encola `SERVICE_FOREGROUND_CRASH_MSG` y la app muere. Es defecto de producto (un usuario real cancela/termina justo al iniciar un descanso), no del test; carrera, por eso no se reprodujo en las 5 corridas posteriores.

Hallazgos adicionales en el mismo archivo:

1. `ACTION_UPDATE` (entregado con `startForegroundService`) NO llamaba `startForeground`: solo `notify`. Funciona unicamente si la instancia ya es foreground (el AMS limpia `fgRequired` si `r.isForeground`); en una instancia nueva (update tardio tras un stop) es el mismo crash tras 10 s. Hoy `updateEndTime(context, endAt)` no tiene llamadores, pero es API publica.
2. La rama `else` (accion desconocida) hacia `stopForeground` + `stopSelf()` sin `startForeground`: mismo defecto para cualquier intent no nulo.
3. Si ambos intentos de `startForeground` de `ACTION_START` fallaban (imagen y sin imagen) no habia ultimo recurso.

## Cambio

Se eligio el estado compartido "start pendiente" (la alternativa `ACTION_STOP` por `startForegroundService` obliga a arrancar/crear un servicio solo para pararlo, con la restriccion de arranque en background API 26+/31+ y un flash de notificacion; con el estado compartido `stop()` sigue siendo un `stopService()` normal cuando no hay nada pendiente).

- NUEVO `services/workout/WorkoutRestForegroundStartGate.kt` (Kotlin puro, sin `android.*`, testeable en JVM). Cuenta los start intents despachados y aun no entregados. `dispatchStart(call)` registra el pendiente y ejecuta la llamada del sistema bajo el lock (si lanza, revierte y relanza la excepcion intacta, restaurando un stop diferido previo). `dispatchStop(call)` ejecuta `stopService` solo si no hay pendientes; si los hay, lo difiere y NO llama a `stopService`. `onStartDelivered()` (desde el servicio, DESPUES de `startForeground`) devuelve true cuando el ultimo pendiente se entrego y habia un stop diferido. Un start posterior a un stop diferido lo anula (gana la ultima peticion). TTL de 15 s por pendiente (el sistema ya agota a ~10 s): un intent que nunca se entregue no puede bloquear los stops para siempre.
- `WorkoutRestForegroundService.kt`:
  - `start`/`updateEndTime` pasan por `startGate.dispatchStart` (misma politica por API: `startForegroundService` en 26+, `startService` antes). Una excepcion (p. ej. `ForegroundServiceStartNotAllowedException` en 31+) se revierte en el gate y se relanza: la degradacion ya existente en `WorkoutRestAlertManager.scheduleRestEnd` (`runCatching { start }.onFailure { postOngoingNotification }`) se mantiene sin cambios.
  - `stop` pasa por `startGate.dispatchStop { stopService }`.
  - `onStartCommand`: intent nulo (reinicio sticky del sistema, no viene de `startForegroundService`) -> como antes, se detiene sin promesa. Para CUALQUIER accion de un intent no nulo, `startForeground` se llama de inmediato con notificacion valida: START (rica -> sin imagen -> placeholder minimo), UPDATE (re-publica la notificacion viva con `startForeground`; si no hay notificacion y la instancia es nueva, placeholder y se va; si la instancia ya es foreground, no-op), accion desconocida (placeholder y se va). Luego `startGate.onStartDelivered()` en `finally` (no se puede filtrar un pendiente) y, si habia un stop diferido, `stopForeground(REMOVE)` + `stopSelf(startId)`. `stopSelf(startId)` y no `stopSelf()`: un start mas nuevo ya encolado mantiene vivo el servicio para cumplir su propia promesa.
  - Estado de instancia `restForegroundActive` (solo Main) para distinguir un UPDATE sobre un servicio vivo de uno sobre una instancia nueva.
- `WorkoutRestAlertManager.kt`: SIN cambios. Los dos llamadores de `stop` (`cancelRestAlerts` y `onFinishAlert`) y el lock de preferencias quedan igual; las preferencias/IO movidas fuera de Main no se tocan. El orden de locks es siempre `timerPreferencesLock` -> lock del gate; el servicio solo toma el del gate (sin inversion).

Semantica de cancelacion conservada: tras `cancelRestAlerts` no queda servicio WorkoutRest vivo. Con un start en vuelo el servicio se detiene solo en cuanto entrega `onStartCommand` (milisegundos); la notificacion que `startForeground` reponga se elimina con `stopForeground(REMOVE)` (en Android 12+ ademas el sistema retrasa ~10 s mostrar notificaciones de FGS de vida corta).

## Otros llamadores (revision pedida)

- `WorkoutVoiceForegroundService` (services/workout, proceso `:voice`): SIN la carrera. `start` = `startForegroundService(ACTION_START)` y `onStartCommand(START)` llama `startAsForeground()` antes de nada (si falla, mata el proceso a proposito: politica ya documentada en el archivo). `stop` = `startService(ACTION_STOP)`, entregado en orden despues del START ya procesado; solo cae a `stopService` si `startService` lanza, y `startService` no lanza mientras hay un start pendiente (`startRequested` es true). No se modifica. Borde NO-crash observado (no corregido, otro patron): un START que llega a una instancia con `stopping == true` (parada asincrona de hasta 1,5 s) cumple `startForeground`, pero la corrutina de parada termina con `stopSelf()` y mata la sesion recien pedida; `stopping` nunca se rearma.
- `CardioGpsForegroundService` (services/cardio, FUERA de mi paquete, solo reporte): sus paradas son `startService(ACTION_STOP)` (sin `stopService`, sin esta carrera), pero SI hay el patron "startForegroundService sin startForeground" en `onStartCommand`: `ACTION_RESUME` (que se envia con `ContextCompat.startForegroundService`) retorna sin `startForeground` cuando `sessionKey` es nulo/en blanco o cuando `!ownsSessionKey(...)` en una instancia nueva (`activeServiceSessionKey == null` y `CardioGpsTracker.state.value.sessionKey != sessionKey`); `ACTION_START` con `sessionKey` en blanco o con `acceptsStart == false` tambien retorna antes de `startForeground`. En una instancia nueva eso es `ForegroundServiceDidNotStartInTimeException` a los ~10 s. Recomendado (otro paquete): llamar `startAsForeground()` al inicio para todo intent no nulo y salir con `stopForeground(REMOVE)` + `stopSelf(startId)` si no corresponde seguir.

## Pruebas anadidas (no ejecutadas)

JVM puro: `src/test/.../services/workout/WorkoutRestForegroundStartGateTest.kt` (10 casos): stop sin pendientes = `stopService` inmediato; start->stop inmediato difiere el stop y no emite `stopService` antes de `startForeground`; varios pendientes; start posterior rearma; stop tras `startForeground` directo; start rechazado se revierte (y restaura un stop diferido); expiracion por TTL (limite exacto y +1 ms); entrega sin pendiente ignorada; reset.

Robolectric (sdk 34, mismo `@Config` que `WorkoutRestAlertManagerPreferenceIoTest`): `src/test/.../services/workout/WorkoutRestForegroundServiceStartStopOrderTest.kt` (10 casos). Un `ContextWrapper` registra `startForegroundService`/`startService`/`stopService` y hace de AMS (marca violacion si hay `stopService` mientras el start espera `startForeground`); el servicio real corre con `Robolectric.buildService(...).create().withIntent(i).startCommand(0, startId)`. Cubre: el oraculo detecta el defecto original; start->stop inmediato (solo el start llega al sistema; al entregar, `startForeground` con `NOTIF_ID`, `stopForeground` y `stopSelf(startId)`, ninguna violacion); start sin stop se queda foreground; stop tras entrega = `stopService` normal; start/stop/start rearma con el ultimo timer (`Notification.when`); UPDATE en instancia nueva cumple `startForeground` y se va; UPDATE sobre servicio vivo reemplaza la cuenta atras y sigue; accion desconocida cumple `startForeground`; cadena completa por `WorkoutRestAlertManager` (`scheduleRestEnd` + `cancelRestAlerts`); start rechazado (excepcion) -> el siguiente cancel hace `stopService` inmediato (no queda pendiente huerfano). El gate es estado de proceso: cada test lo resetea en `@Before`/`@After`.

Dispositivo: `src/androidTest/.../services/workout/WorkoutRestForegroundServiceStopRaceInstrumentedTest.kt`: 40 iteraciones de `scheduleRestEnd` + `cancelRestAlerts` en una sola tarea de Main; si el defecto volviera, el crash mata el proceso de instrumentacion; al final espera <= 5 s a que `ActivityManager.getRunningServices` no liste `WorkoutRestForegroundService`.

## Como verificar

JVM (desde `android-native/`):
`./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.services.workout.WorkoutRestForegroundStartGateTest" --tests "com.example.kpkn.services.workout.WorkoutRestForegroundServiceStartStopOrderTest" --tests "com.example.kpkn.services.workout.WorkoutRestAlertManagerPreferenceIoTest" --tests "com.example.kpkn.services.workout.WorkoutRestAlertRulesTest"`
y despues la bateria completa (los tests de `WorkoutFinishController*` usan `WorkoutRestAlertManager` real).

Dispositivo (emulator-5580, orquestador):
1. `WorkoutLifecycleDurabilityInstrumentedTest#manual_cancel_clears_active_rest_overlay_before_terminal_ack` repetido >= 20 veces (la carrera es de ms) y la nueva `WorkoutRestForegroundServiceStopRaceInstrumentedTest`.
2. En cada corrida, logcat sin `Bringing down service while still waiting for start foreground` ni `ForegroundServiceDidNotStartInTimeException`.
3. Drivers `cancel-*`: tras cancelar, `adb shell dumpsys activity services com.example.kpkn | grep -E "WorkoutRest|WorkoutVoice|CardioGps"` vacio (esperar ~1-2 s).
4. Descanso normal (sin cancelar): notificacion "descanso en curso" con cronometro regresivo y acciones +15s/-15s/saltar/completar; +/-15 s reprograma (ruta `scheduleRestEnd(isAdjustment = true)` = nuevo `ACTION_START` sobre servicio vivo).

## Riesgos / supuestos a confirmar al ejecutar

- Los tests Robolectric asumen: `ShadowService.stopSelf(int)` -> `stopSelfId`, `startForeground` publica la notificacion en `ShadowNotificationManager` y `NotificationManagerCompat.activeNotifications` la devuelve (caso "UPDATE sobre servicio vivo"), y que `PendingIntent.getActivity(... MainActivity ...)` funciona dentro del servicio con `manifest = NONE` (el test existente de preferencias ya ejercita recursos y alarmas con esa config).
- `WorkoutRestAlertManagerPreferenceIoTest` (existente) deja un pendiente en el gate porque Robolectric no ejecuta el servicio; expira en 15 s y los tests nuevos resetean el gate, asi que no se contaminan.
- Si `startForeground` fallara las tres veces (placeholder incluido) el sistema mataria el proceso a los 10 s de todas formas ("bring down" tambien lo mata); no hay degradacion posible ahi, solo se evita enmascararlo.
