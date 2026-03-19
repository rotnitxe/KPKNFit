# Paso 1 - Ledger inicial de paridad PWA -> React Native

Fecha: 2026-03-18

## Veredicto

- Estado: aprobado con reservas
- Objetivo cumplido: existe una base realista para seguir con la migracion 1:1
- Objetivo no cumplido: el ledger todavia no es exhaustivo ni demuestra paridad completa

## Lo que si quedo resuelto

- Se confirmo una base amplia de equivalencias reales por archivo entre PWA y RN.
- Se detectaron matched inflados por basename o por reubicacion de carpetas.
- Se identificaron dominios que todavia no tienen cobertura suficiente:
  - settings
  - coach
  - wiki
  - progress
  - background/widgets/notifications
  - migration
  - workout secundario
  - navegacion secundaria

## Criterio operativo adoptado

- El Paso 1 no se reabre.
- Cualquier hallazgo adicional se agrega como backlog del dominio correspondiente.
- El ledger actual se usa como mapa de trabajo para el Paso 2.

## Gaps criticos que guian el Paso 2

1. Navegacion raiz y secundaria no mapeada 1:1.
2. Superficie de workout secundaria incompleta.
3. Bloque coach incompleto.
4. Bloque wiki incompleto.
5. Bloque progress incompleto.
6. Migration y stores de transicion no ledgerados por completo.
7. Background/widgets/notifications sin matriz navegable completa.
8. `services/aiService.ts` sin equivalente RN unico.
9. `workers/computeWorker.ts` sin equivalente RN claro.

## Nota de control

- Spot-check local realizado sobre archivos criticos del ledger y del backlog.
- Se confirmo la existencia de piezas importantes como:
  - `components/SettingsComponent.tsx`
  - `components/CoachView.tsx`
  - `components/WikiHomeView.tsx`
  - `components/BodyProgressView.tsx`
  - `components/StartWorkoutModal.tsx`
  - `components/StartWorkoutDrawer.tsx`
  - `components/SessionDetailView.tsx`
  - `apps/mobile/src/screens/Settings/SettingsScreen.tsx`
  - `apps/mobile/src/screens/Wiki/WikiHomeScreen.tsx`
  - `apps/mobile/src/screens/Workout/ActiveSessionScreen.tsx`
  - `apps/mobile/src/stores/bootstrapStore.ts`
  - `apps/mobile/src/stores/cutoverStore.ts`
- Se detecto que algunos candidatos del backlog no existen como archivo PWA directo:
  - `components/WarmupDrawer.tsx`
  - `components/WorkoutDrawer.tsx`

## Decision

Paso 1 cerrado como inventario operativo, no como prueba final de paridad.
