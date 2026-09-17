# DoD live-visual 2026-08-25 — status

## Veredicto
**INCOMPLETO (estricto).** No cerrar goal.

## Progreso este turno
1. **Diagnóstico:** fuentes Live* habían desaparecido del árbol; APK instalado previo solo tenía `WorkoutSetPager` (UI horizontal).
2. **Recuperación:** backups en `artifacts/live-visual-2026-08-25/_recovered/`, `_V2Body_wired.kt`, `_LiveRoadmapStepper.kt`; plan restaurado `.opencode/plans/2026-08-25_live-workout-visual-language.md` (`flags: []`).
3. **ANR cursor-nav fix (código):** `WorkoutStepNavigator` → `persistOngoingState(immediate = false)` (sigue en árbol).
4. **Assemble:** `app-base-debug.apk` con clases Live verificadas en dex: `WorkoutLiveConnectedStage`, `LiveIsthmusLayer`, `LiveRoadmapStepper`, `LiveConnectedCardShell`.
5. **Install emulator-5554:** OK vía `adb install` tras storage; gradle `installBaseDebug` falló por `INSTALL_FAILED_INSUFFICIENT_STORAGE` a veces.
6. **Relanzar live seed:** **ANR inmediato** (`48-post-install-live.png`, `logcat-anr-live-restore.txt`, traces `/data/anr/anr_2026-08-25-18-19-*`). Skipped 251–1014 frames. **S3 exact limpia: NO.**
7. **Wipe intermitente:** `LiveRoadmapStepper.kt` / `WorkoutLive*.kt` volvieron a desaparecer mid-session (posible agente concurrente). Re-restaurados al cierre del turno.

## Tabla DoD mayor
| Requisito | Estado | Evidencia |
|---|---|---|
| Roadmap vertical 23/48 + M/A + ventana | **Unproven en device este turno** | Clases en APK sí; UI live ANR antes de captura limpia |
| Istmo Bézier white→gray anclado | **Unproven** (previos S1/mid/last OK en APK viejo) | `40`/`41`/`42`/`43` previos; `44` actual **inválida** |
| Cards nuevas ≤2dp (no wrap) | Parcial código / unproven UI | `LiveConnectedCardShell` en APK |
| Peek 88–128 no interactivo | Código métricas sí / UI unproven | `WorkoutLiveMetrics.kt` |
| Haze sibling header | Unproven | `46-transition-blur-attempt.png` débil |
| Dock discreto | Unproven | — |
| PagerSync intacto | Probable | tests previos; no regresado a propósito |
| ConceptosClave→CanonicalKnowledge | Diffs presentes | androidTest modificados |
| Tests enfocados workout | BUILD SUCCESSFUL (intento previo) | LiveMetrics/Pager/Navigator |
| assembleBaseDebug | Sí (APK en outputs) | `app-base-debug.apk` |
| installBaseDebug 5554 | Sí (adb) / gradle flaky storage | — |
| 8 variantes | Previas no revalidadas | `dod-*-live-stage.png` |
| logcat no FATAL | OK en ventanas vistas | FATAL 0; **ANR sí** |
| **44 S3 exact clean** | **FAIL** | permisos/UI vieja/ANR; no pillY≈S3 |

## Siguiente P0
1. Estabilizar fuentes Live* (evitar wipe concurrente).
2. Fix ANR de **cold composition** del ConnectedStage (no solo persist debounce) — stack en `logcat-anr-live-restore.txt` / `anr-trace-live-restore.txt`.
3. Capturar `44-isthmus-s3-exact-clean.png` real + revalidar 8 variantes.
