# Auditoría del Plan Maestro de Migración MiniMax 2.7

## Resumen

Auditoría completa del plan de migración PWA → React Native, con verificación línea por línea contra el codebase real. Se encontraron **15+ errores fácticos**, **gaps subestimados**, y **oportunidades de mejora arquitectónica** que el plan original no contempla. Este documento es el plan maestro perfeccionado y corregido.

> [!CAUTION]
> El plan MiniMax 2.7 ([PLAN_MIGRACION_PRO_PLUS.md](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/PLAN_MIGRACION_PRO_PLUS.md)) tiene solo 121 líneas con 6 pasos genéricos. El plan largo ([PLAN_MIGRACION_MAESTRO_PRO.md](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/PLAN_MIGRACION_MAESTRO_PRO.md) de 79KB) tiene mucha más profundidad pero contiene datos incorrectos que invalidan decisiones de migración. Esta auditoría corrige ambos.

---

## Parte 1: Errores Fácticos Encontrados

### 🔴 Datos incorrectos que cambian decisiones de migración

| # | Claim del plan | Realidad verificada | Impacto |
|---|---|---|---|
| 1 | "RN tiene 63 servicios" | RN tiene **68 servicios** (incluye servicios exclusivos de RN como [activeSessionPersistenceService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/apps/mobile/src/services/activeSessionPersistenceService.ts), [coachChatService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/apps/mobile/src/services/coachChatService.ts), [augeRuntimeService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/apps/mobile/src/services/augeRuntimeService.ts), [mobilePersistenceService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/apps/mobile/src/services/mobilePersistenceService.ts), etc.) | Subestima madurez RN |
| 2 | "RN tiene 19 stores (8 nuevos)" | RN tiene **21 stores** (`augeRuntimeStore`, `bootstrapStore`, `coachStore`, `cutoverStore`, `localAiDiagnosticsStore`, `mealPlannerStore`, `nutritionFlowDiagnosticsStore`, `pantryStore` son exclusivos de RN) | Subestima arquitectura RN |
| 3 | "RN tiene ~47 screens" | RN tiene exactamente **48 screens** verificados | Error menor |
| 4 | "RN ignora modulación nutricional en baterías" → hardcoded 1.0 | **PARCIALMENTE CIERTO**: RN [calculateMuscleBattery](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/recoveryService.ts#179-256) sí acepta `nutritionLogs` como parámetro pero no llama a `computeNutritionRecoveryMultiplier()`. Usa `nutritionMultiplier = 1.0` con fallback a `1.25` si hay déficit. Es un **stub genérico**, no hardcoded "1.0" como dice el plan | Gap real pero mal descrito |
| 5 | "calculateGlobalBatteriesAsync FALTA en RN" | **FALSO**: Existe en [computeWorkerService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-(beta-test)/apps/mobile/src/services/computeWorkerService.ts) como [withAsyncFallback(calculateGlobalBatteries)](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/apps/mobile/src/services/computeWorkerService.ts#17-26). Lo que falta es la **re-exportación desde [auge.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/auge.ts)** | Maldiagnóstico |
| 6 | "classifyACWR no se re-exporta desde auge.ts" | **FALSO**: [classifyACWR](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/auge.ts#123-134) está definida localmente en [auge.ts:95-100](file:///c:/Users/valen/Downloads/kpkn-fit-(beta-test)/apps/mobile/src/services/auge.ts#L95-L100) | No es un gap |
| 7 | "RN no tiene classifyStressZone, ACWR_ZONE_LABELS, etc." | **CORRECTO**: RN [auge.ts](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/services/auge.ts) no re-exporta desde `@kpkn/shared-domain` los classifiers (`classifyAcwrZone`, `classifyStressZone`, y tipos `AcwrZone`, [StressLevel](file:///c:/Users/valen/Downloads/kpkn-fit-%28beta-test%29/apps/mobile/src/services/auge.ts#88-94)). Estos SÍ existen en [shared-domain/src/auge/classifiers.ts](file:///c:/Users/valen/Downloads/kpkn-fit-(beta-test)/packages/shared-domain/src/auge/classifiers.ts) | Gap real, fácil de cerrar |
| 8 | "Hooks: 8 archivos → integrados en RN" | RN tiene hooks en `/data/` (ej: `useExerciseSearch.ts`, `useBodyProgressData.ts`, `useChartDataFormatter.ts`) distribuidos, no "0 hooks" | Maldiagnóstico |
| 9 | "Base de datos ejercicios: 575KB+ no existen en RN" | **PARCIALMENTE CIERTO**: RN no tiene archivos estáticos de exercise database, pero `exerciseStore.ts` se hidrata desde datos migrados de la PWA via `mobilePersistenceService`. Si el usuario nunca usó la PWA, el store está vacío | Este es el gap real: falta **fallback estático** |
| 10 | "PWA `calculateGlobalBatteries` retorna articularBatteries, articularAverage, muscleArticularBlend — RN no" | **CORRECTO Y CONFIRMADO**: PWA líneas 648-666 calculan baterías articulares. RN líneas 590-711 retornan solo `{cns, muscular, spinal, auditLogs, verdict}` | **Gap CRÍTICO confirmado** |
| 11 | "Adaptive half-life tuning (Bayesian curve) no existe en RN" | **CORRECTO**: PWA líneas 508-514 mezclan `muscHalfLife` con `adaptiveRecoverySamples`. RN `calculateGlobalBatteries` no tiene este bloque | **Gap CRÍTICO confirmado** |
| 12 | "calculateSleepRecommendations: RN no tiene check de stress > 200" | **CORRECTO pero mal descrito**: PWA check es `volume > 15 || stress > 200` (línea 429). RN solo tiene `volume > 15` (línea 423). No es "stress > 200" sino `sessionStressScore > 200` | Gap real, trivial |
| 13 | "Compute Worker: RN 50% paridad" | RN tiene **100% paridad funcional** via `withAsyncFallback`. Las 9 funciones están wrapeadas. Solo falta `InteractionManager` para deferral real | Sobreestima el gap |
| 14 | "Google Drive: stub 548 bytes" | **CORRECTO**: [googleDriveService.ts](file:///c:/Users/valen/Downloads/kpkn-fit-(beta-test)/apps/mobile/src/services/googleDriveService.ts) = 548 bytes | Confirmado |
| 15 | "Supabase Sync: 85% más chico" | **CORRECTO**: PWA 7804 vs RN 1194 bytes | Confirmado |

### 🟡 Omisiones Importantes del Plan

| Omisión | Detalle |
|---|---|
| **`computeNutritionRecoveryMultiplier`** | Función clave del PWA que modula el muscHalfLife dinámicamente basándose en ingesta calórica y proteica. RN no la tiene. El plan la menciona vagamente pero no da instrucciones de migración |
| **`shared-domain` ya tiene lógica AUGE** | El plan no aprovecha que `@kpkn/shared-domain` ya contiene `fatigue.ts`, `recovery.ts`, `classifiers.ts`, `ttc.ts`, `tendonRecovery.ts`, `tendonAlerts.ts`. Gran parte de la lógica ya es **platform-neutral** |
| **RN tiene servicios exclusivos ausentes en PWA** | `augeRuntimeService.ts` (15KB), `mobileDomainStateService.ts` (11KB), `mobilePersistenceService.ts` (10KB), `sessionRecoveryService.ts`, `oneRMPropagationService.ts` — RN ya tiene innovaciones propias que el plan no reconoce |
| **`exerciseDatabaseMerged.ts`** | Archivo de 11KB en PWA `/data/` que YA consolida las bases de datos — el plan ignora su existencia |
| **`splitTemplates.ts` diferencia de tamaño** | PWA: 29,380 bytes vs RN: 9,944 bytes. Gap serio no mencionado |
| **`foodSynonymsExpansion.ts`** | Archivo en PWA que el plan no menciona (2,317 bytes de sinónimos adicionales) |

---

## Parte 2: Gaps REALES Confirmados (Priorizados)

### 🔴 CRÍTICO — Bloquean MVP de usuario nuevo

| # | Gap | PWA | RN | Acción |
|---|---|---|---|---|
| 1 | **Exercise DB estática fallback** | 575KB+ en 7 archivos | 0 bytes estáticos (solo migración) | Consolidar en 1-2 archivos, cargar como fallback en `exerciseStore` |
| 2 | **Onboarding** | 13 archivos en `components/onboarding/` | 0 archivos | Crear OnboardingStack completo |
| 3 | **`computeNutritionRecoveryMultiplier`** | Modula `muscHalfLife` dinámicamente | Hardcode `1.0` | Migrar función desde PWA o `shared-domain` |
| 4 | **Articular batteries en `calculateGlobalBatteries`** | Retorna `articularBatteries`, `articularAverage`, `muscleArticularBlend` | No retorna articular data | Agregar cálculo articular al return |
| 5 | **Adaptive half-life Bayesian blend** | Mezcla `muscHalfLife` con recovery samples | No existe | Copiar bloque PWA L508-514 |

### 🟡 ALTO — Afectan UX significativamente

| # | Gap | Acción |
|---|---|---|
| 6 | **Google Drive backup** (548B stub) | Implementar con `@react-native-google-signin` |
| 7 | **Supabase sync** (1194B vs 7804B) | Expandir sync service |
| 8 | **`initialMuscleGroupDatabase.ts`** (6 grupos vs 40+) | Sobrescribir con versión PWA completa |
| 9 | **`articularBatteryConfig.ts`** (5168B vs 8224B) | Sobrescribir con versión PWA |
| 10 | **`splitTemplates.ts`** (9944B vs 29380B) | Sobrescribir con versión PWA |
| 11 | **AI Coach Chat** (reglas keyword vs streaming LLM) | Implementar backend proxy |

### 🟢 MEDIO — Post-MVP

| # | Gap | Acción |
|---|---|---|
| 12 | **Shared-domain classifiers en auge.ts** | Agregar re-exports |
| 13 | **`foodDatabase.ts`** estructura diferente | Unificar estructura |
| 14 | **`jointDatabase.ts`** y **`tendonDatabase.ts`** | No existen en RN data/ (pero tendon services sí existen) |
| 15 | **`movementPatternDatabase.ts`** | No existe en RN |
| 16 | **`inferMusclesFromName.ts`** | No existe en RN |
| 17 | **`programHelpers.ts`** y **`sessionDayLabel.ts`** | No existen en RN utils/ |
| 18 | **Correlation/Powerlifting dashboards** | No existen como screens en RN |

---

## Parte 3: Plan de Ejecución Corregido

### FASE 0: Cleanup (1-2h)

**Sin cambios al plan original.** Eliminar archivos muertos confirmados:
- `untitled.tsx`, `components/CoachAnalysis.tsx` (verificar que está vacío)
- `components/WorkoutSession.tsx.backup`, backups de SessionEditor
- `data/activationExercises.ts` (0 bytes), `data/warmupExercises.ts` (0 bytes)

> [!WARNING]
> `data/gyms.ts` (412 bytes) y `data/supplements.ts` (3029 bytes) **NO son 0 bytes** como dice el plan. Verificar si tienen contenido real antes de eliminar.

---

### FASE 1: Datos Estáticos (4-8h)

**Correcciones vs plan original:**

#### 1.1 Exercise Database — Usar `exerciseDatabaseMerged.ts` como base

El plan ignora que YA existe [exerciseDatabaseMerged.ts](file:///c:/Users/valen/Downloads/kpkn-fit-(beta-test)/data/exerciseDatabaseMerged.ts) (11KB) que consolida las bases de datos. **Usarlo como punto de partida** en vez de parsear 7 archivos.

La estrategia correcta:
1. Verificar qué hace `exerciseDatabaseMerged.ts`
2. Si consolida correctamente → copiar + adaptar tipos
3. Crear `apps/mobile/src/data/exerciseDatabase.ts` como fallback estático
4. Modificar `exerciseStore.hydrateFromMigration()` para usar fallback si no hay datos migrados

#### 1.2 Muscle Group Database — Confirmar sobrescritura

RN tiene 6 categorías (49 líneas). PWA tiene 37KB con 40+ grupos. **Sobrescribir pero verificar que el tipo `MuscleGroupInfo` de RN pueda acomodar los campos de PWA** (hay campos como `role`, `aliases[]`, `relatedJoints` que podrían no existir en el tipo RN).

#### 1.3 Joint/Tendon — Verificar dependencias primero

Los tendon services de RN (`tendonRecoveryService.ts` 1455B, `tendonAlertsService.ts` 1101B) ya existen pero ¿de dónde importan datos? Verificar si usan shared-domain o si esperan archivos locales.

#### 1.4 Alimentos — NO sobrescribir a ciegas

El plan dice sobrescribir `foodDatabase.ts` de RN con PWA. **Error**: las estructuras son diferentes (`FoodItem` tiene campos distintos). Proceso correcto:
1. Unificar tipo `FoodItem` en `@kpkn/shared-types`
2. Luego migrar datos

#### 1.5 Datos adicionales no mencionados en plan

- `splitTemplates.ts`: RN tiene 9944B vs PWA 29380B — gap serio para templates de programa
- `portionReferences.ts`: RN 2999B vs PWA 6409B
- `cookingMethodFactors.ts`: RN 2773B vs PWA 3926B

---

### FASE 2: AUGE Core (6-10h)

**Correcciones críticas:**

#### 2.1 NO sobrescribir auge.ts/recoveryService.ts/fatigueService.ts

El plan dice "SOBRESCRIBIR" repetidamente. **Esto es DESTRUCTIVO**. RN tiene adaptaciones necesarias:
- `recoveryService.ts` usa `ExerciseCatalogEntry` (tipo RN) en vez de `ExerciseMuscleInfo` (tipo PWA)
- `calculateMuscleBattery` de RN tiene signatura adaptada sin `waterLogs[]` y `postSessionFeedback[]`
- RN usa `getJsonValue`/`setJsonValue` (MMKV) en vez de `localStorage`

**Proceso correcto**: Merge quirúrgico, NO copy-paste:
1. Agregar `computeNutritionRecoveryMultiplier` importándola de shared-domain o portándola
2. Agregar bloque Bayesian adaptive (líneas 508-514 de PWA) a `calculateGlobalBatteries` de RN
3. Agregar cálculo articular al return de `calculateGlobalBatteries` de RN
4. Agregar check `stress > 200` a `calculateSleepRecommendations` de RN
5. Agregar re-exports de classifiers a `auge.ts`

#### 2.2 Compute Worker — Ya está listo

El plan dedica una sección entera a esto pero **RN ya tiene paridad funcional** (9 funciones async wrapeadas). Solo optimización: reemplazar `Promise.resolve` con `InteractionManager.runAfterInteractions` para uso real en producción. Esto es **30 minutos**, no una fase completa.

---

### FASE 3: Stores y Persistencia (3-4h)

**Corrección:** `uiStore` gap (19KB PWA vs 3KB RN) NO es un problema real. PWA tiene modales/drawers HTML que no aplican a RN (que usa React Navigation con modales nativos). El estado de UI en RN es más distribuido por diseño.

`authStore` sí necesita paridad en funciones de auth.

---

### FASE 4: Hooks y Utils (3-4h)

**Corrección:** RN ya tiene equivalentes funcionales de varios hooks mencionados:
- `useExerciseSearch.ts` en `data/` → equivalente de `useExerciseDatabase.ts`
- `useBodyProgressData.ts` y `useChartDataFormatter.ts` → funcionalidad derivada

Solo faltan: `useMuscleRecovery` (hook dedicado) y `programHelpers.ts` + `sessionDayLabel.ts` en utils.

---

### FASE 5: Onboarding (6-10h)

**Sin cambios mayores** — este gap es real y confirmado. 13 archivos PWA → 0 en RN.

---

### FASE 6: UI y Analytics (10-16h)

**Reducida vs plan original** — RN ya tiene 48 screens funcionales. El gap real está en:
- Articular battery visualization en Session Editor
- Dashboards de analytics avanzados (Correlation, Powerlifting trends)
- Components de fatigue indicators en tiempo real

---

### FASE 7-9: AI, Testing, Cutover

**Sin cambios mayores** al plan original.

---

## Parte 4: Estimación Corregida

| Fase | Plan Original | Estimación Corregida | Razón |
|---|---|---|---|
| F0: Cleanup | 2-4h | **1-2h** | Menos archivos de lo que dice |
| F1: Data | 6-10h | **4-8h** | `exerciseDatabaseMerged.ts` ya existe |
| F2: AUGE | 10-16h | **6-10h** | Merge quirúrgico, no sobrescritura total. Compute Worker ya funciona |
| F3: Stores | 4-6h | **2-4h** | uiStore gap es falso. Solo authStore + sync |
| F4: Hooks/Utils | 4-6h | **2-4h** | Muchos ya existen en RN |
| F5: Onboarding | 8-12h | **6-10h** | Sin cambios |
| F6: UI | 16-24h | **10-16h** | 48 screens ya existen |
| F7: AI | 8-12h | **4-8h** | Backend proxy reduce scope |
| F8: Testing | 6-8h | **4-6h** | Sin cambios |
| F9: Cutover | 4-6h | **3-4h** | Sin cambios |
| **TOTAL** | **70-100h** | **42-72h** | **35-40% menos** gracias a trabajo ya hecho |

---

## Verification Plan

### Automated Tests (existentes)
```bash
npm run test:nutrition-logging    # Tests de nutrición PWA
npm run mobile:test               # Jest tests de RN
npx tsc --noEmit                  # Typecheck PWA
npm --workspace @kpkn/mobile run typecheck  # Typecheck RN
```

### Manual Verification
Cada fase finalizada debe pasar:
1. `npm --workspace @kpkn/mobile run typecheck` sin errores nuevos
2. `npm run mobile:test` sin regresiones
3. Smoke test en emulador Android (cold start sin crash)

---

## Decisiones Resueltas

| Decisión | Resolución |
|---|---|
| **Exercise Database** | ✅ **Fallback estático** compilado en el bundle, igual que PWA |
| **`computeNutritionRecoveryMultiplier`** | ✅ Mover a **`@kpkn/shared-domain`** (platform-neutral) |
| **Google Drive** | ⏸️ **Diferido a post-MVP** — no implementar ahora |
