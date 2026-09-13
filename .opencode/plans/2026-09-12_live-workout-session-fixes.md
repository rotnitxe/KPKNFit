---
flags: [room, auge, voice]
---

# Fixes sesión en vivo — contrato de construcción

Fecha: 2026-09-12. Fuente: auditoría `auditoría_bugs_sesión_en_vivo` (Cursor). Este documento **bloquea D1–D6** y ordena las fases F0–F7. Los planes detallados viven al lado:

| Fase | Archivo | Flags |
|------|---------|-------|
| F0 P0 hotfix | [2026-09-12_live-workout-f0-p0-hotfix.md](2026-09-12_live-workout-f0-p0-hotfix.md) | room |
| F1 persistencia | [2026-09-12_live-workout-f1-persistencia.md](2026-09-12_live-workout-f1-persistencia.md) | room |
| F2 métricas/AUGE | [2026-09-12_live-workout-f2-metricas-auge.md](2026-09-12_live-workout-f2-metricas-auge.md) | auge |
| F3 navegación | [2026-09-12_live-workout-f3-navegacion-registro.md](2026-09-12_live-workout-f3-navegacion-registro.md) | — |
| F4 estructura/plan | [2026-09-12_live-workout-f4-edicion-estructural.md](2026-09-12_live-workout-f4-edicion-estructural.md) | room |
| F5 timers/FGS | [2026-09-12_live-workout-f5-timers-servicios.md](2026-09-12_live-workout-f5-timers-servicios.md) | voice |
| F6 voz | [2026-09-12_live-workout-f6-voz.md](2026-09-12_live-workout-f6-voz.md) | voice |
| F7 UI/UX | [2026-09-12_live-workout-f7-ui-ux.md](2026-09-12_live-workout-f7-ui-ux.md) | — |

Orden de construcción: **F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7**. No saltar F0. F2 no empieza hasta que F0 tenga tests de remapeo verdes (el builder único consume `completedSets` ya sanos). F4 reusa el remapeo de F0; no reinventarlo.

WIP concurrente: el árbol Android ya tiene cambios de nutrición/workout. Integrar; prohibido `reset`/`stash` masivo.

## Decisiones de producto D1–D6 (bloqueadas)

No reabrir en construcción salvo que el usuario las anule por escrito.

### D1 — Unilateral: L+R = 1 serie lógica

AUGE ya escala `sideScale = 0.5` (`AugeFatigueEngine.kt:403`) y `AugeUnilateralImpactTest` exige L+R ≈ bilateral. La UI/log/surplus cuentan 2. **Canon:**

- **Serie lógica** = un `setIdx` del plan. L y R son mitades. Volumen efectivo, surplus (`computeMuscleSetSurplus`), “Series: N” del finish y progreso de sesión usan series lógicas.
- **Roadmap/pager** siguen mostrando dos slots físicos (el usuario ejecuta ambos lados). Un slot físico incompleto = la serie lógica no está hecha.
- **Tonelaje** suma carga×reps de ambos lados (trabajo físico real).
- **PRs / homologación** comparan por `contextKey` + lado; no inflar `setCount` contando L y R como dos series de volumen.

Helper único: `logicalSetCount(completedSets, session)` en `screens/workout` (puros, testeable). `isSetDone` sigue exigiendo todos los `expectedSidesForSet`.

### D2 — Tonelaje con peso corporal: carga homologada

`normalizeLoad` pone BODYWEIGHT=0, LASTRE=solo extra, ASSISTED=asistencia positiva. El usuario ve 0 kg en dominadas.

**Canon:** kcal, drain y tonelaje de sesión usan **carga homologada en kg**:

| Modo | Carga homologada |
|------|------------------|
| LOAD | `loggedLoad` |
| BODYWEIGHT | `userWeightKg` |
| LASTRE | `userWeightKg + loggedLoad` |
| ASSISTED | `(userWeightKg - loggedLoad).coerceAtLeast(0)` |

El log puede seguir guardando `CompletedSet.weight` como carga homologada (hoy ya es `augeEquivalentLoad`). No persistir asistencia como si fuera peso positivo. No reconvertir `userVitals.weight` (ya está en kg).

### D3 — Reemplazo: series hechas se archivan con la identidad original

Hoy `replaceExercise` borra `completedSets` del id y el finish no las ve. El usuario sí hizo ese trabajo.

**Canon:** al reemplazar, las series ya registradas del original pasan a `archivedCompletedExercises: List<CompletedExercise>` (o mapa `archivedCompletedSets` + snapshot de identidad). El finish las incluye en el log bajo el **nombre/id de catálogo original**, no las atribuye al sucesor. Warmup/omit/skip del original no se heredan. Volumen/AUGE del original sobreviven al replace. El sucesor empieza en 0.

### D4 — Voz en background: manos libres + recuperación al volver

El FGS `:voice` es la promesa de manos libres en el gym. **No** matar captura en `ON_PAUSE`.

**Canon:**

- `ON_PAUSE`: no-op de captura (como hoy), **pero** el comentario del VM debe coincidir.
- `ON_RESUME`: revalidar `RECORD_AUDIO`; si falta, apagar sesión con mensaje. Si stage es `MIC_BUSY` / `ERROR_RECOVERY` / controller disabled con `voiceSessionEnabled`, llamar `enable()`/`resume`.
- Permiso revocado a mitad: no bucle `MIC_BUSY`; parar y hablar/mostrar el permiso.

### D5 — FGS de descanso: se queda, tipo explícito, alarmas como fuente de verdad

No es P0 (el tipo del manifiesto cubre `startForeground` sin overload). El countdown con pantalla apagada sí necesita FGS o alarma.

**Canon:** conservar `WorkoutRestForegroundService` + `AlarmManager`. Pasar `FOREGROUND_SERVICE_TYPE_DATA_SYNC` explícito al `startForeground` (paridad con voz/GPS). No migrar a `specialUse` en esta ola (Play Console + ficha). No eliminar el FGS. Alarmas siguen siendo quien avisa en Doze. No cancelar `NOTIF_ID_FINISHED` ni el ID del FGS en ±15 s.

### D6 — PERMANENT estructural = esta semana; PERMANENT de reemplazo = programa

Hoy no significan lo mismo. Ampliar estructural a todo el programa es peligroso (reescribe sesiones futuras con pesos live).

**Canon:**

- Estructural `PERMANENT`: **solo la semana actual** (`upsertSessionInWeek`). Copy UI: “Guardar en esta semana”.
- Reemplazo `PERMANENT`: **todas las sesiones del programa** (como hoy). Copy: “Reemplazar en todo el programa”.
- `BLOCK_MATCHING`: bloque actual, sesión unwrapped del modo activo (arreglar el doble `withModeSession`).
- `MESOCYCLE_MATCHING`: no ofrecerlo; borrar el branch que lo degrada a `SESSION_ONLY` o dejarlo interno no-op **sin** etiqueta en UI.
- Nunca volcar el objeto live entero al programa (replay quirúrgico, F4).

## Rutas

Solo Android `android-native/`. Paridad iOS/backend de AUGE (F2) se documenta en F2 como follow-up, no se implementa en esta ola salvo que un cambio de fórmula se copie en comentarios de `backend/engines`.

## Impacto

Sesión en vivo deja de perder borradores, deja de corromper `completedSets` al editar, alinea volumen/AUGE/unilateral/kg, y repara navegación, timers, voz y overlays. Sin migración Room de versión: `OngoingWorkoutState` gana campos con default (F1) bajo el mismo `dbJson`.

## Pruebas

Cada fase declara tests dirigidos. Cierre de ola:

```bash
cd android-native
./gradlew --no-daemon --console=plain --warning-mode=summary testBaseDebugUnitTest --tests 'com.example.kpkn.screens.workout.*' --tests 'com.example.kpkn.domain.workout.*' --tests 'com.example.kpkn.domain.auge.*' --tests 'com.example.kpkn.services.workout.*' --tests 'com.example.kpkn.data.repository.ProgramRepositoryFinalizeWorkoutTest' --tests 'com.example.kpkn.data.repository.OngoingPersistContractTest'
```

Linux: `JAVA_HOME=/home/rotnitxe/.local/share/kpkn-android/jdk`, `ANDROID_HOME=/home/rotnitxe/.local/share/kpkn-android/sdk`.

## Riesgos

- Árbol sucio (nutrición + workout). Diffs acotados.
- `completedSets` por índice es la bomba de datos: F0 primero.
- F2 toca AUGE: no recalibrar sigmoidal; solo inputs/builders/unidades.
- Voz: no reintroducir doble-save (guards 2026-08-07).
- No `adb install -r` incremental sobre perfil poblado (lección 2026-09-12 MEMORY).
