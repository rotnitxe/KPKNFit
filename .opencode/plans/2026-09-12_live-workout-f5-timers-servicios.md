---
flags: [voice]
---

# F5 — Timers, FGS, notificaciones y lifecycle de servicios

Depende de F1 (flush/GPS stop ya cableados; no duplicar). D5 bloqueada. Cierra: **TMR-01, TMR-02, TMR-03, TMR-04, TMR-06, TMR-07, TMR-08, TMR-09, TMR-10, TMR-11, TMR-13**. TMR-05/12 son F1.

Flag `voice` porque se toca `services/workout/` (FGS descanso, receivers, pacing). No tocar Vosk/parser.

## Objetivo

Movilidad/cardio no derivan en background. Las notificaciones de descanso no se auto-borran. ±15 s no tumba el FGS. Pacing avisa con la app en segundo plano. El FGS de descanso declara tipo explícito (D5).

## Rutas

| Ítem | Archivos |
|------|----------|
| Tipo FGS | `services/workout/WorkoutRestForegroundService.kt:65`, manifiesto ya `dataSync` |
| Notif finish | `RestTimerController.kt:211-214`, `WorkoutRestAlertManager.cancelRestAlerts` `:196-201` |
| Acciones ±15 | `TimerNotificationActionReceiver.kt:12-27` |
| Movilidad/cardio | `WorkoutViewModel.kt` jobs ~3954-4236; hydrator `resumeRestored*` |
| Pacing | `WorkoutPacingController.kt:56,107`, `WorkoutViewModel` ctor ~356 |
| IDs notif | `WorkoutRestAlertManager` `NOTIF_ID_FINISHED` vs `CardioGpsForegroundService` |
| Chip permisos | overlay rest, `RestAlertCapabilityState.needsPersistentChip` |
| Overlay stuck | `WorkoutOverlayHost.kt`, `WorkoutRestOverlay.kt` |
| Recordatorios | `WorkoutReminderManager.kt:143-148` |
| Menores | vibración amplitudes, executors, `ACTION_UPDATE`, header `0:00` flash |

`targetSdk = 35`.

## Impacto

### TMR-01 (P2) + D5 — `startForeground` con tipo

Como voz/GPS:

```kotlin
if (Build.VERSION.SDK_INT >= 34) {
    startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
} else {
    startForeground(NOTIF_ID, notification)
}
```

No `specialUse`. `ACTION_UPDATE` (`:41-45`): si el servicio no está en FG, no llamar `startForegroundService` sin `startForeground`; o reusar `startOrUpdateForeground`. Hoy no hay callers externos; igual cerrar la bomba.

`runCatching` de start desde background (TMR-08): se queda el fallback a notif; si `POST_NOTIFICATIONS` denegado, TMR-09.

### TMR-02 — No borrar la notif de fin

`cancelRestAlerts()` no toca `NOTIF_ID_FINISHED`. El finish in-app llama `deliverCompletionAlert` y **luego** para el FGS/ongoing **sin** cancelar finished. Al empezar un descanso nuevo sí se cancela la finished anterior.

### TMR-03 — Receiver ±15 s

Cancelar `WorkoutRestForegroundService.NOTIF_ID` **solo** en SKIP y COMPLETE. ADD/SUBTRACT no tocan el ID del FGS (el orquestador re-`startForeground`).

### TMR-04 — `endsAtMs` movilidad y cardio

Mismo patrón que `RestTimerController` (`endMs - currentTimeMillis`):

- Al start: `endsAtMs = now + remaining * 1000`.
- Tick: recalcular remaining; no `remaining - 1`.
- `ON_RESUME` / hydrator: `resumeRestored*IfNeeded` ya existe para process death; llamarlo también desde `WorkoutScreen ON_RESUME`.
- Persistir `endsAtMs` en `MobilityTotalTimerState` / `CardioTimerState` (campos; defaults). F1 deja de persistir cada segundo: solo al start/pause/resume.

Pausa: cancela job, guarda remaining, `isRunning=false`. No doble job (`?.cancel()` antes de launch, ya).

### TMR-06 — Pacing en background

Inyectar `isAppInForeground` real (`ProcessLifecycleOwner` / Activity started). Entonces `pacingNotifications.notify` corre. Countdown de sesión ya usa `startTimeMs` persistido: no tocar.

### TMR-07 — IDs

`NOTIF_ID_FINISHED` descanso ≠ 42042 del GPS. Asignar 42043 (u otro libre). Grep todos los 42042.

### TMR-09 — Permiso denegado

Pintar `needsPersistentChip` / `needsAlarmWarning` en overlay de rest. Copy: “Sin notificaciones el aviso es solo sonoro.” `canPostNotifications` también mira `areNotificationsEnabled()`.

### TMR-11 — Overlay remaining=0

Si `remaining==0 && isRestTimerRunning` → `handleNaturalFinish` / `stop()`. Overlay de **feedback**: botón “Saltar descanso” o auto-minimizar (F7 copy). Re-registro no relanza (ya).

### TMR-10 — Recordatorios diarios

`setRepeating` inexacto → one-shot `setExactAndAllowWhileIdle` (ya gated) + reprogramar en el receiver. Fuera del path crítico del vivo; hacer si el diff es chico.

### TMR-13 menores

- Preferir `elapsedRealtime` para remaining de rest **nuevo**; alarmas RTC se quedan (D5). Si el cambio es riesgoso, dejar wall clock y solo documentar salto por cambio de hora.
- Persistir `restStartedAtMs` para % recovery (F1 campo).
- Header: no `remember(startTimeMs) { 0 }`; inicializar con elapsed real.
- Vibración: alinear arrays patrón/amplitud.
- Apagar `playExecutor` / `CardioCuePlayer` en `onCleared`.
- `CardioGpsTracker.startTickerLocked`: cancelar ticker en `pause()`.

## Pruebas

| Test | Qué |
|------|-----|
| `RestTimerControllerTest` | finish in-app no exige que el sink cancele FINISHED (ajustar fake) |
| `TimerNotificationActionPolicyTest` | puro: skip/complete cancelan FGS id; add/sub no |
| `MobilityCardioEndsAtTest` | avanzar clock 5 s → remaining -5, no -1 |
| `WorkoutPacingControllerTest` | `isAppInForeground=false` → notify |

No hay emulador Doze en JVM; TMR-08 queda como `runCatching` + comentario.

```bash
./gradlew --no-daemon --console=plain testBaseDebugUnitTest --tests '*RestTimer*' --tests '*RestAlert*' --tests '*Pacing*' --tests '*CardioTimer*'
```

## Riesgos

- API 35 `dataSync` timeout si el descanso dura > límite de tipo. Mitigación D5: alarmas independientes del FGS (ya). Si Play rechaza, follow-up `specialUse`.
- `elapsedRealtime` vs alarmas RTC: no mezclar en el mismo `endAt` enviado al AlarmManager (sigue wall clock).
- GPS ticker pause: no romper restore (`CardioGpsRestoreStateTest`).

## Fuera de alcance

Vosk, TTS (F6), iOS rest service, Health Connect.
