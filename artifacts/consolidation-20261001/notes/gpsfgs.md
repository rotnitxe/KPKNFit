# gpsfgs: "startForegroundService sin startForeground" en CardioGpsForegroundService (+ toast de sesion vacia)

Solo edicion; no se ejecuto Gradle/adb/emulador/tests. Los archivos tocados son LF (el arbol de trabajo de `services/cardio` y `WorkoutViewModel.kt` ya era LF; contado con `tr -cd`, CR == 0). `CardioGpsPersistence.kt`, `CardioGpsTracker.kt` y las funciones puras de la reparacion GPS NO se modificaron.

## Diagnostico

`CardioGpsForegroundService.onStartCommand` recibe `ACTION_START` y `ACTION_RESUME` por `ContextCompat.startForegroundService`. Antes, varias ramas retornaban SIN `startForeground()`:

- `ACTION_RESUME`: `sessionKey` nula/blanca, o `!ownsSessionKey` en una instancia nueva (`activeServiceSessionKey == null` y el tracker con otra clave, o sin clave tras reiniciar el proceso).
- `ACTION_START`: `sessionKey` blanca, o `acceptsStart == false`.
- accion desconocida / sin accion con intent no nulo (`else`).

En una instancia nueva el AMS espera `startForeground()` ~10 s y mata el proceso con `ForegroundServiceDidNotStartInTimeException` (mismo mecanismo documentado en `restfgs.md`). Las paradas ya iban por `startService(ACTION_STOP)` (sin `stopService`), asi que no hay carrera start->stop como en el servicio de descanso.

## Cambio (`services/cardio/CardioGpsForegroundService.kt`)

- `onStartCommand`: para todo intent no nulo cuya accion promete foreground (`cardioGpsIntentPromisesForeground`: todo salvo `ACTION_PAUSE` y `ACTION_STOP`, que se envian con `startService` y no deben levantar notificacion) se llama de inmediato e incondicionalmente a `keepForegroundPromise(action)` ANTES de cualquier retorno temprano. Intent nulo (reinicio sticky): sin promesa, mismo comportamiento de antes (rama `else`: si no hay sesion propia, `stopForeground(REMOVE)` + `stopSelf(startId)`).
- `keepForegroundPromise`: notificacion normal (la de siempre) y, si falla, placeholder minimo (`buildPlaceholderNotification`: solo icono + titulo del canal GPS, sin PendingIntent). Ambos con `FOREGROUND_SERVICE_TYPE_LOCATION` en API 29+ y sin tipo antes (igual que el archivo ya hacia). Todo dentro de `runCatching`; si ambos fallan se registra `gps_fgs_start_foreground_failed` (KpknDiagnosticLogger, dentro de `runCatching`) y el comando sigue (ver "no resuelto").
- `declineCommand(startId, restartWhileServing)`: salida de comandos que no corresponden (clave blanca, no propietaria, START obsoleto). Si la instancia NO atiende una sesion (`activeServiceSessionKey == null`): `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf(startId)` (no `stopSelf()`: un comando mas nuevo ya encolado mantiene vivo el servicio para cumplir su propia promesa) y `START_NOT_STICKY`. Si la instancia atiende una sesion: no para nada y devuelve exactamente el valor que ya devolvia (`START_NOT_STICKY` para blanca/START obsoleto, `START_REDELIVER_INTENT` para RESUME ajeno). No escribe en `CardioGpsTracker` ni en `activeServiceSessionKey`/`activeExecutionStartedAtMs`: la identidad de la ejecucion vigente (clave + inicio de ejecucion; el token/generacion vive en el tracker y no se toca) queda intacta.
- `startAsForeground()` pasa a `startAsForeground(notification)`; la llamada que estaba en las ramas START/RESUME aceptadas se elimino (ya se hizo arriba). El resto de las ramas (coroutines de START/RESUME, PAUSE, STOP, `onDestroy`) no cambio.
- `NOTIFICATION_ID` pasa de `private` a `internal const` (lo usan las pruebas). El comentario de `CardioGpsAvdLifecycleInstrumentedTest.kt` l.603 ("is private") queda desactualizado; no se toco (androidTest ajeno).
- Funcion pura nueva (fin del archivo): `internal fun cardioGpsIntentPromisesForeground(action: String?): Boolean`.

Valores de retorno por rama (sin cambios): START blanca -> NOT_STICKY; START rechazada -> NOT_STICKY; START aceptada -> REDELIVER; RESUME blanca -> NOT_STICKY; RESUME ajena -> REDELIVER si hay sesion viva, si no NOT_STICKY; RESUME propia -> REDELIVER.

## Tarea 2: toast de sesion vacia

`screens/workout/WorkoutViewModel.kt`, `handleEmptySessionFinishBlocked`: `emptyFinishGuardNotice = FINISH_EMPTY_SESSION_GUIDANCE` (constante `internal` del mismo paquete, definida en `WorkoutFinishController.kt`: "Registra al menos una serie para terminar o abandona sin guardar."). Una sola linea; el `emptyFinishGuardNotice` lo muestra `WorkoutScreen.kt` ~l.1565 (toast) y lo consume `consumeEmptyFinishGuardNotice()`. El texto hablado (`voiceController.speakFeedbackUpdated`) y `WorkoutVoiceCommandHandler.kt` l.1334 NO se tocaron (fuera del alcance pedido). Ninguna prueba referencia el texto antiguo.

## Pruebas anadidas (no ejecutadas)

JVM puro: `src/test/.../services/cardio/CardioGpsForegroundPromisePolicyTest.kt` (3 metodos): START/RESUME prometen foreground; PAUSE/STOP no; accion nula, vacia o desconocida si.

Robolectric (sdk 34, mismo `@Config` que `WorkoutRestForegroundServiceStartStopOrderTest`): `src/test/.../services/cardio/CardioGpsForegroundServiceStartPromiseTest.kt` (12 metodos). El servicio real corre con `Robolectric.buildService(...).create()` y `service.onStartCommand(intent, 0, startId)` (se llama directo para poder comprobar el valor de retorno). Permisos de ubicacion DENEGADOS a proposito (`shadowOf(app).denyPermissions`): un START valido termina en `PERMISSION_DENIED` sin tocar FusedLocation. Cada test limpia las claves usadas con `CardioGpsTracker.clearSession`.

- `resumeForAForeignSessionOnAFreshInstanceCallsStartForegroundAndThenLeaves`: RESUME ajeno en instancia nueva (tracker sin clave) => `startForeground(NOTIFICATION_ID, tipo LOCATION)`, luego `stopForeground` (orden comprobado con `isForegroundStopped`), `stopSelf(startId)`, `START_NOT_STICKY`, estado del tracker identico antes/despues de `onDestroy`.
- `resumeForAnotherSessionLeavesTheSessionHeldByTheTrackerUntouched`: igual pero con el tracker sosteniendo otra sesion (`restoreIfAvailable`); su estado no cambia.
- `resumeWithoutAUsableSessionKeyOnAFreshInstanceCallsStartForegroundAndThenLeaves`: clave nula / "" / "   ".
- `startWithoutAUsableSessionKeyOnAFreshInstanceCallsStartForegroundAndThenLeaves`: lo mismo para START; no crea sesion GPS en el tracker.
- `unknownOrMissingActionOnAFreshInstanceStillCallsStartForegroundAndThenLeaves`: accion desconocida y sin accion.
- `nullIntentFromAStickyRestartKeepsLeavingWithoutAForegroundPromise`: intent nulo => sin `startForeground`, sale como antes.
- `pauseAndStopCommandsAreDeliveredWithStartServiceAndRaiseNoNotification`: PAUSE/STOP no levantan notificacion.
- `validStartEntersTheForegroundWithTheLocationTypeAndKeepsServingUntilTheTrackerAnswers`: START valido => comportamiento actual intacto (REDELIVER, notificacion + tipo LOCATION, sigue vivo; luego el tracker responde `PERMISSION_DENIED` y el servicio se detiene con `stopSelf(startId)`).
- `resumeOfTheSessionHeldByTheTrackerEntersTheForegroundAndKeepsServingUntilTheTrackerAnswers`: RESUME propio => igual (el tracker responde INACTIVE y se va).
- `foreignResumeOnALiveInstanceKeepsServingAndLeavesTheRunningExecutionIdentityAlone` y `staleStartOnALiveInstanceKeepsServingAndLeavesTheRunningExecutionIdentityAlone`: instancia viva con un START valido en vuelo recibe un RESUME ajeno / un START obsoleto: re-promete foreground, NO se detiene, devuelve los valores de antes, y despues la limpieza propia del START (que solo actua mientras `activeServiceSessionKey == LIVE_KEY`) sigue funcionando (`stopSelfId == 1`): oraculo de que la identidad de la ejecucion no se piso.
- `onlyStartAndResumeAreDispatchedWithStartForegroundService`: guarda la premisa (companion: START/RESUME por `startForegroundService`; PAUSE/STOP por `startService`).

## Como verificar

JVM (desde `android-native/`):
`./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.services.cardio.CardioGpsForegroundPromisePolicyTest" --tests "com.example.kpkn.services.cardio.CardioGpsForegroundServiceStartPromiseTest" --tests "com.example.kpkn.services.cardio.CardioGpsRestoreStateTest" --tests "com.example.kpkn.services.cardio.CardioGpsStartRestorePolicyTest" --tests "com.example.kpkn.services.cardio.CardioGpsPersistenceTest" --tests "com.example.kpkn.screens.workout.WorkoutFinishEmptySessionTest"`
y despues la bateria completa.

Dispositivo (emulador, orquestador): con la app en primer plano y ubicacion concedida, correr `CardioGpsAvdLifecycleInstrumentedTest` completo; en logcat no debe aparecer `Bringing down service while still waiting for start foreground` ni `ForegroundServiceDidNotStartInTimeException`. Escenario directo (no automatizado): desde el proceso de la app (p. ej. un test instrumentado puntual) llamar `CardioGpsForegroundService.resume(context, "clave-inexistente")` en proceso frio y comprobar que ~12 s despues el proceso sigue vivo y `adb shell dumpsys activity services com.example.kpkn | grep CardioGps` queda vacio. Un `adb shell am start-foreground-service` no sirve: el servicio es `exported=false`.

## No resuelto / supuestos

- startForeground rechazado en las dos variantes (p. ej. API 34+ sin permiso de ubicacion, SecurityException): no hay tipo valido alternativo (el manifiesto solo declara `location`), asi que se registra y el comando sigue; el tracker publica `PERMISSION_DENIED` y el servicio termina como antes. Si el AMS tampoco limpia `fgRequired` antes de lanzar la SecurityException, el proceso puede morir igual a los ~10 s; no verificable por lectura. Los llamadores de la app comprueban el permiso antes (`WorkoutScreen.onRequestCardioGps` -> `CardioGpsTracker.hasLocationPermission`; revocar el permiso en Ajustes mata el proceso). Esta rama (ambos intentos fallan) NO tiene prueba.
- No es crash, no se cambio (otro patron): PAUSE/STOP con clave no propia en una instancia nueva retornan sin `stopSelf`, dejando un servicio inactivo hasta que el sistema lo recoja. Tambien: si un RESUME ajeno llega a una instancia viva justo cuando la limpieza de `PERMISSION_DENIED` de un START ejecuta `stopSelf(startId_antiguo)`, el servicio queda inactivo (el AMS ignora el stopSelf viejo) hasta nueva orden.
- Supuestos de las pruebas Robolectric (nunca ejecutadas): looper PAUSED (por defecto; la limpieza de las corrutinas se publica en Main y se drena con `ShadowLooper.idleMainLooper()` con tope de 5 s), `Dispatchers.Main.immediate` funcional bajo Robolectric (hay 36 suites con `setMain`/`resetMain` balanceadas), `ShadowService` registra `foregroundServiceType` (verificado por `javap` en shadows-framework 4.12.2) y olvida la notificacion tras `stopForeground(REMOVE)` (por eso la notificacion solo se comprueba en los casos que siguen sirviendo), y `Config.NONE` se ignora con el Build System API (el manifiesto fusionado y los recursos si se cargan: `DefaultManifestFactory` lo registra en log). `CardioGpsTracker` es un singleton del sandbox y nadie mas lo usa en JVM tests; cada test limpia sus claves.
- `WorkoutVoiceForegroundService`: sin cambios (ver `restfgs.md`).
