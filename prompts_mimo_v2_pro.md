# PROMPTS MAESTROS — Migración KPKN Fit PWA → React Native
## Optimizados para MiMo v2 Pro (Xiaomi)

> **Instrucciones de uso**: Cada prompt es **auto-contenido**. Copia y pega UN prompt por sesión.  
> Cada prompt incluye todo el contexto necesario para que MiMo ejecute sin ambigüedad.  
> **NO necesitas dar contexto previo** — cada prompt tiene la arquitectura, reglas, y archivos exactos.

---

# FASE 0 — CLEANUP Y BASELINE

```text
<role>
Eres un arquitecto de software senior especializado en React Native y monorepos TypeScript.
Trabajas en el proyecto KPKN Fit, una app de gimnasio que migra de PWA (Capacitor) a React Native.
</role>

<context>
PROYECTO: C:\Users\valen\Downloads\kpkn-fit-(beta-test)
MONOREPO con workspaces:
- Raíz: PWA (esbuild + TailwindCSS + Capacitor)
- apps/mobile: React Native (@kpkn/mobile)
- packages/shared-types: tipos compartidos
- packages/shared-domain: lógica de negocio platform-neutral
- packages/design-tokens: tokens de diseño RN

REGLAS ABSOLUTAS:
- En apps/mobile: PROHIBIDO Tailwind/className. Solo Native StyleSheet + useColors() hook.
- Stores: Zustand con persistencia MMKV (no AsyncStorage, no localStorage).
- Navegación RN: React Navigation con native stack.
- AUGE engine: single source of truth en services/auge.ts.
</context>

<task>
FASE 0: CLEANUP DE CÓDIGO MUERTO Y BASELINE

PASO 1 — Eliminar archivos muertos CONFIRMADOS (0 bytes o nunca importados):
- Eliminar: untitled.tsx (raíz del proyecto)
- Eliminar: data/activationExercises.ts (0 bytes)
- Eliminar: data/warmupExercises.ts (0 bytes)

PASO 2 — Verificar ANTES de eliminar (pueden tener contenido real):
- data/gyms.ts (412 bytes) → Leer contenido. Si solo tiene comments/mock data sin exports usados, eliminar.
- data/supplements.ts (3029 bytes) → Leer contenido. Si tiene datos reales exportados, NO eliminar.
- Buscar si algún archivo importa gyms.ts o supplements.ts con: grep -r "from.*gyms" y grep -r "from.*supplements"

PASO 3 — Verificar imports de archivos eliminados:
- grep -rn "CoachAnalysis" --include="*.ts" --include="*.tsx" (si 0 resultados → safe to delete components/CoachAnalysis.tsx si existe y está vacío)
- grep -rn "WorkoutSession.tsx.backup" (confirmar que es solo un backup)
- grep -rn "SessionEditorScreen.tsx.backup" en apps/mobile/

PASO 4 — Generar Baseline:
- Ejecutar: npx tsc --noEmit 2>&1 | tail -5 (contar errores PWA)
- Ejecutar: cd apps/mobile && npm run typecheck 2>&1 | tail -5 (contar errores RN)
- Reportar: X errores PWA, Y errores RN

PASO 5 — Inventario de archivos críticos:
Generar tabla con columnas [Archivo | PWA bytes | RN bytes | Delta%] para:
- Todos los .ts en services/ (ambos)
- Todos los .ts en data/ (ambos)
- Todos los .ts en stores/ (ambos)
- Todos los .ts en utils/ (ambos)

NO crear código nuevo. Solo limpiar y reportar.
</task>

<deliverables>
1. Lista de archivos eliminados con justificación
2. Lista de archivos NO eliminados con razón
3. Baseline: errores TypeScript en PWA y RN
4. Tabla comparativa de tamaños de archivos
</deliverables>
```

---

# FASE 1 — MIGRACIÓN DE DATOS ESTÁTICOS

```text
<role>
Eres un ingeniero de datos TypeScript senior. Tu trabajo es migrar bases de datos estáticas
de una PWA a React Native manteniendo integridad de tipos y compatibilidad con el sistema AUGE
(motor de fatiga/recuperación muscular).
</role>

<context>
PROYECTO: C:\Users\valen\Downloads\kpkn-fit-(beta-test)
TARGET: apps/mobile/src/data/

ARQUITECTURA DE TIPOS:
- packages/shared-types: tipos compartidos entre PWA y RN
- apps/mobile/src/types/workout.ts: tipos específicos RN (ExerciseCatalogEntry, MuscleGroupInfo, MuscleHierarchy)
- PWA usa ExerciseMuscleInfo como tipo de ejercicio
- RN usa ExerciseCatalogEntry como tipo de ejercicio

REGLAS:
- NO sobrescribir archivos a ciegas. Siempre verificar que los tipos destino aceptan los campos fuente.
- Los datos estáticos en RN se importan directamente (no se cargan de red).
- exerciseStore.ts de RN se hidrata desde migración PWA, pero necesita FALLBACK ESTÁTICO para usuarios nuevos.
</context>

<task>
FASE 1: MIGRACIÓN DE BASES DE DATOS ESTÁTICAS

═══════════════════════════════════════════
BLOQUE 1.1: EXERCISE DATABASE (CRÍTICO)
═══════════════════════════════════════════

SITUACIÓN ACTUAL:
- PWA tiene 7 archivos de ejercicios (~575KB total):
  * data/exerciseDatabase.ts (77,701 bytes)
  * data/exerciseDatabaseCentral.ts (157,149 bytes)
  * data/exerciseDatabaseExpansion.ts (89,590 bytes)
  * data/exerciseDatabaseExpansion2.ts (32,221 bytes)
  * data/exerciseDatabaseExpansion3.ts (238,140 bytes)
  * data/exerciseDatabaseExtended.json (196,648 bytes)
  * data/exerciseDatabaseMerged.ts (11,027 bytes) ← PUNTO DE PARTIDA
  * data/exerciseList.ts (970 bytes)

- RN: exerciseStore.ts se hidrata de datos migrados. Si el usuario nunca usó la PWA → 0 ejercicios.

PROCESO:
1. LEER data/exerciseDatabaseMerged.ts → Analizar si consolida todas las bases de datos.
2. LEER apps/mobile/src/types/workout.ts → Extraer la interfaz ExerciseCatalogEntry completa.
3. MAPEAR campos: PWA Exercise → RN ExerciseCatalogEntry:
   - id → id
   - name → name
   - primaryMuscle / involvedMuscles → involvedMuscles[]
   - equipment → equipment
   - type (Básico/Accesorio/Aislamiento) → type
   - movementPattern → verificar si existe en RN
4. CREAR: apps/mobile/src/data/exerciseDatabase.ts
   - Exporta: STATIC_EXERCISE_DATABASE: ExerciseCatalogEntry[]
   - Debe tener al menos 800+ ejercicios
   - Sin duplicados (normalizar por nombre lowercase para detectar)
5. COPIAR: data/exerciseList.ts → apps/mobile/src/data/exerciseList.ts (lista simple de strings)
6. MIGRAR: data/inferMusclesFromName.ts (17,474 bytes) → apps/mobile/src/data/inferMusclesFromName.ts
   - Es algoritmo determinista con regex y mapeos de sinónimos
   - NO depende de UI ni browser APIs
   - Adaptar imports si es necesario
7. MODIFICAR: apps/mobile/src/stores/exerciseStore.ts
   - En hydrateFromMigration(): si no hay datos migrados ni cache MMKV, cargar STATIC_EXERCISE_DATABASE como fallback
   - Pseudocódigo del cambio:
     ```
     // Después de verificar que payload está vacío:
     import { STATIC_EXERCISE_DATABASE } from '../data/exerciseDatabase';
     set({ exerciseList: STATIC_EXERCISE_DATABASE, status: 'ready', hasHydrated: true });
     ```

═══════════════════════════════════════════
BLOQUE 1.2: MUSCLE GROUP DATABASE (CRÍTICO)
═══════════════════════════════════════════

SITUACIÓN ACTUAL:
- PWA: data/initialMuscleGroupDatabase.ts (37,470 bytes) → 40+ grupos musculares detallados
- RN: apps/mobile/src/data/initialMuscleGroupDatabase.ts (2,082 bytes) → SOLO 6 categorías básicas:
  pectoral, espalda, deltoides, cuadriceps, isquiosurales, gluteos

PROCESO:
1. LEER la versión PWA completa de initialMuscleGroupDatabase.ts
2. LEER apps/mobile/src/types/workout.ts → interfaz MuscleGroupInfo
3. COMPARAR campos:
   - PWA puede tener: id, name, canonicalName, anatomyInfo, relatedJoints, relatedTendons,
     exercisesPrimary, exercisesSecondary, defaultRepRanges, recoveryHours, notes, tags, role, aliases[]
   - RN MuscleGroupInfo tiene: id, name, description, importance{movement,health}, volumeRecommendations{mev,mav,mrv}
4. SI los campos de PWA no caben en MuscleGroupInfo de RN:
   - Extender la interfaz MuscleGroupInfo en apps/mobile/src/types/workout.ts con campos opcionales
   - O crear una interfaz MuscleGroupDataExtended que extienda MuscleGroupInfo
5. SOBRESCRIBIR apps/mobile/src/data/initialMuscleGroupDatabase.ts con los datos de PWA
   adaptados a la interfaz correcta de RN.
6. MIGRAR: data/initialMuscleHierarchy.ts (2,234 bytes) → apps/mobile/src/data/initialMuscleHierarchy.ts
   - Define relaciones parent-child entre músculos
   - Verificar que MuscleHierarchy type existe en RN types

═══════════════════════════════════════════
BLOQUE 1.3: JOINT Y TENDON DATABASES
═══════════════════════════════════════════

SITUACIÓN ACTUAL:
- PWA: data/jointDatabase.ts (13,969 bytes), data/tendonDatabase.ts (7,868 bytes)
- RN: NO existen como archivos estáticos. Pero los servicios tendonRecoveryService.ts (1,455B) y
  tendonAlertsService.ts (1,101B) SÍ existen en RN.

PROCESO:
1. VERIFICAR si los tendon services de RN importan datos de alguna parte (¿shared-domain?).
2. COPIAR data/jointDatabase.ts → apps/mobile/src/data/jointDatabase.ts
3. COPIAR data/tendonDatabase.ts → apps/mobile/src/data/tendonDatabase.ts
4. Adaptar imports si los tipos JointDefinition/TendonDefinition no existen → crear en apps/mobile/src/types/anatomy.ts
5. ACTUALIZAR imports en tendon services de RN si apuntan a ubicación incorrecta.

═══════════════════════════════════════════
BLOQUE 1.4: FOOD DATABASE
═══════════════════════════════════════════

SITUACIÓN ACTUAL:
- PWA: data/foodDatabase.ts (37,885B), data/foodDatabaseExpansion.ts (8,972B),
  data/foodSynonyms.ts (3,818B), data/foodSynonymsExpansion.ts (2,317B),
  data/foodTaxonomy.ts (8,086B), data/localChileanFoods.ts (2,270B)
- RN: apps/mobile/src/data/foodDatabase.ts (11,156B), foodSynonyms.ts (3,539B),
  foodTaxonomy.ts (2,871B), localChileanFoods.ts (1,962B)

⚠️ ESTRUCTURAS DIFERENTES:
- PWA FoodItem: caloriesPer100g, proteinPer100g, carbsPer100g, fatPer100g, portionSize, portionUnit, tags[], aliases[]
- RN FoodItem: calories, protein, carbs, fat, servingSize (sin tags[], sin aliases[])

PROCESO:
1. COMPARAR ambas interfaces FoodItem (PWA vs RN)
2. Unificar: La PWA tiene estructura más rica. Extender la interfaz RN con campos opcionales que PWA usa (tags?, aliases?)
3. MERGE foodDatabase: combinar datos de PWA que no existan en RN
4. MERGE foodSynonyms: agregar contenido de foodSynonymsExpansion.ts de PWA
5. SOBRESCRIBIR foodTaxonomy con versión PWA (más completa, 8KB vs 2.8KB)
6. COMPARAR localChileanFoods y tomar la más completa

NO MIGRAR los JSON enormes (16MB+): openFoodFactsOffline.json, usdaFoodsOffline.json, usdaFoundationFoods.json

═══════════════════════════════════════════
BLOQUE 1.5: DATOS COMPLEMENTARIOS
═══════════════════════════════════════════

1. articularBatteryConfig.ts: SOBRESCRIBIR RN (5,168B) con PWA (8,224B)
   - PWA tiene más alias mappings (face pulls, manguito rotador, etc.)
2. splitTemplates.ts: SOBRESCRIBIR RN (9,944B) con PWA (29,380B)
3. movementPatternDatabase.ts (6,965B): CREAR en apps/mobile/src/data/
4. discomfortList.ts: COMPARAR PWA (8,967B) vs RN (8,966B) — probablemente idénticos, verificar
5. portionReferences.ts: COMPARAR PWA (6,409B) vs RN (2,999B) — merge si hay diferencias
6. cookingMethodFactors.ts: COMPARAR PWA (3,926B) vs RN (2,773B) — merge si hay diferencias
</task>

<verification>
Después de completar CADA bloque:
1. cd apps/mobile && npm run typecheck → debe pasar sin errores NUEVOS
2. Importar los nuevos archivos en un test temporal y verificar que exportan datos

Tests específicos:
- exerciseDatabase: import { STATIC_EXERCISE_DATABASE } → length >= 800
- initialMuscleGroupDatabase: import → length >= 20
- jointDatabase: import → length >= 40
- inferMusclesFromName("Barbell Bench Press") → debe retornar array con "Pectorales" o similar
</verification>

<deliverables>
- apps/mobile/src/data/exerciseDatabase.ts (NUEVO, fallback estático)
- apps/mobile/src/data/exerciseList.ts (NUEVO)
- apps/mobile/src/data/inferMusclesFromName.ts (NUEVO)
- apps/mobile/src/data/initialMuscleGroupDatabase.ts (SOBRESCRITO)
- apps/mobile/src/data/initialMuscleHierarchy.ts (NUEVO)
- apps/mobile/src/data/jointDatabase.ts (NUEVO)
- apps/mobile/src/data/tendonDatabase.ts (NUEVO)
- apps/mobile/src/data/movementPatternDatabase.ts (NUEVO)
- apps/mobile/src/data/articularBatteryConfig.ts (SOBRESCRITO)
- apps/mobile/src/data/splitTemplates.ts (SOBRESCRITO)
- apps/mobile/src/stores/exerciseStore.ts (MODIFICADO — fallback estático)
- apps/mobile/src/data/foodDatabase.ts (MERGEADO)
- apps/mobile/src/data/foodTaxonomy.ts (SOBRESCRITO)
</deliverables>
```

---

# FASE 2 — AUGE ENGINE: MERGE QUIRÚRGICO

```text
<role>
Eres un especialista en algoritmia de rendimiento deportivo y TypeScript.
Tu trabajo es realizar un MERGE QUIRÚRGICO (NO copy-paste) de funciones faltantes del motor AUGE
desde la PWA hacia React Native, respetando la arquitectura existente de RN.
</role>

<context>
PROYECTO: C:\Users\valen\Downloads\kpkn-fit-(beta-test)

SISTEMA AUGE:
- El "cerebro" de la app. Calcula fatiga muscular, baterías de recuperación, readiness diario.
- PWA: services/auge.ts (5,715B) re-exporta de fatigueService.ts + recoveryService.ts
- RN: apps/mobile/src/services/auge.ts (3,005B) hace lo mismo pero le faltan algunos exports

SHARED-DOMAIN (platform-neutral):
- packages/shared-domain/src/auge/fatigue.ts
- packages/shared-domain/src/auge/recovery.ts
- packages/shared-domain/src/auge/classifiers.ts ← tiene classifyAcwrZone, classifyStressZone
- packages/shared-domain/src/auge/nutritionRecovery.ts ← tiene computeNutritionRecoveryMultiplier? VERIFICAR

REGLA ABSOLUTA: NO SOBRESCRIBIR archivos de RN con archivos de PWA.
RN tiene adaptaciones necesarias:
- Tipos: ExerciseCatalogEntry (RN) vs ExerciseMuscleInfo (PWA)
- Storage: MMKV (RN) vs localStorage (PWA)
- Servicios exclusivos de RN: augeRuntimeService.ts, mobileDomainStateService.ts
</context>

<task>
FASE 2: MERGE QUIRÚRGICO DEL SISTEMA AUGE

═══════════════════════════════════════════
BLOQUE 2.1: computeNutritionRecoveryMultiplier → shared-domain
═══════════════════════════════════════════

SITUACIÓN:
- PWA recoveryService.ts (líneas 196-207) llama a computeNutritionRecoveryMultiplier() que modula muscHalfLife.
- RN recoveryService.ts (líneas 226-231) tiene: nutritionMultiplier = 1.0 (hardcoded).
- La función DEBE vivir en packages/shared-domain (decisión del usuario).

PROCESO:
1. BUSCAR si computeNutritionRecoveryMultiplier ya existe en shared-domain:
   - grep -rn "computeNutritionRecoveryMultiplier" packages/shared-domain/
   - Si existe en nutritionRecovery.ts → saltar al paso 3
   - Si NO existe → paso 2

2. CREAR/MOVER a packages/shared-domain/src/auge/nutritionRecovery.ts:
   - Leer la implementación completa de PWA (buscar "computeNutritionRecoveryMultiplier" en services/recoveryService.ts)
   - Extraer la función con su interfaz de parámetros
   - La función recibe: { nutritionLogs, settings, stressLevel, hoursWindow }
   - Retorna: { recoveryTimeMultiplier: number, status: 'deficit'|'surplus'|'maintenance', factors: string[] }
   - Asegurar que NO depende de tipos PWA-only. Si usa Settings de PWA, abstraer a un parámetro genérico.
   - Exportar desde packages/shared-domain/src/index.ts

3. INTEGRAR en RN recoveryService.ts:
   - Importar computeNutritionRecoveryMultiplier desde '@kpkn/shared-domain'
   - En calculateMuscleBattery (línea ~226), REEMPLAZAR:
     ```typescript
     // ANTES (hardcoded):
     let nutritionMultiplier = 1.0;
     if ((settings as any).algorithmSettings?.augeEnableNutritionTracking !== false) {
         nutritionMultiplier = 1.0;
     }
     // DESPUÉS (dinámico):
     let nutritionMultiplier = 1.0;
     if ((settings as any).algorithmSettings?.augeEnableNutritionTracking !== false) {
         const nutritionResult = computeNutritionRecoveryMultiplier({
             nutritionLogs,
             settings,
             stressLevel: wellbeingLog?.stressLevel ?? 3,
             hoursWindow: 48,
         });
         nutritionMultiplier = nutritionResult.recoveryTimeMultiplier;
     }
     ```

═══════════════════════════════════════════
BLOQUE 2.2: ARTICULAR BATTERIES en calculateGlobalBatteries
═══════════════════════════════════════════

SITUACIÓN:
- PWA calculateGlobalBatteries (líneas 648-666) calcula articular batteries y retorna:
  { cns, muscular, spinal, auditLogs, verdict, articularBatteries, articularAverage, muscleArticularBlend }
- RN calculateGlobalBatteries (líneas 590-711) retorna:
  { cns, muscular, spinal, auditLogs, verdict }  ← FALTAN 3 campos

PROCESO:
1. LEER apps/mobile/src/services/tendonRecoveryService.ts → verificar que calculateArticularBatteries() existe
2. En RN calculateGlobalBatteries (apps/mobile/src/services/recoveryService.ts), ANTES del return final:
   AGREGAR:
   ```typescript
   // 7. BATERÍAS ARTICULARES (tendones y articulaciones)
   const articularBatteries = calculateArticularBatteries(history, exerciseList, [], settings);
   const articularScores = Object.values(articularBatteries).map((state: any) => state.recoveryScore);
   const articularAverage = articularScores.length
       ? Math.round(articularScores.reduce((sum: number, score: number) => sum + score, 0) / articularScores.length)
       : 100;
   const muscleArticularBlend = Math.round((Math.round(finalMusc) + articularAverage) / 2);
   ```
3. ACTUALIZAR el return para incluir los 3 campos nuevos:
   ```typescript
   return {
       cns: Math.round(finalCns),
       muscular: Math.round(finalMusc),
       spinal: Math.round(finalSpinal),
       auditLogs,
       verdict,
       articularBatteries,
       articularAverage,
       muscleArticularBlend,
   };
   ```
4. AGREGAR import de calculateArticularBatteries al top del archivo si no existe.

═══════════════════════════════════════════
BLOQUE 2.3: ADAPTIVE BAYESIAN HALF-LIFE TUNING
═══════════════════════════════════════════

SITUACIÓN:
- PWA calculateGlobalBatteries (líneas 507-514) mezcla muscHalfLife con datos adaptativos:
  ```
  const adaptiveRecoverySamples = Object.values(effectiveAdaptiveCache.personalizedRecoveryHours || {})
      .filter(v => typeof v === 'number' && v > 0);
  if (adaptiveRecoverySamples.length > 0) {
      const adaptiveMeanRecovery = adaptiveRecoverySamples.reduce((a,v) => a+v, 0) / adaptiveRecoverySamples.length;
      muscHalfLife = clamp((muscHalfLife * 0.6) + (adaptiveMeanRecovery * 0.4), 24, 96);
  }
  ```
- RN: NO tiene este bloque.

PROCESO:
1. En RN calculateGlobalBatteries, DESPUÉS de la línea que define muscHalfLife (let muscHalfLife = 40;)
   y ANTES de la sección de nutrición, AGREGAR el bloque Bayesian:
   ```typescript
   const effectiveAdaptiveCache = adaptiveCache ?? {};
   const adaptiveRecoverySamples = Object.values(
       (effectiveAdaptiveCache as any).personalizedRecoveryHours || {}
   ).filter((value): value is number => typeof value === 'number' && value > 0);

   if (adaptiveRecoverySamples.length > 0) {
       const adaptiveMeanRecovery = adaptiveRecoverySamples.reduce((acc, value) => acc + value, 0) / adaptiveRecoverySamples.length;
       muscHalfLife = clamp((muscHalfLife * 0.6) + (adaptiveMeanRecovery * 0.4), 24, 96);
       auditLogs.muscular.push({ icon: '🧪', label: 'Curva Bayesiana Personal', val: Math.round(muscHalfLife), type: 'info' });
   }
   ```

═══════════════════════════════════════════
BLOQUE 2.4: NUTRICIÓN EN calculateGlobalBatteries
═══════════════════════════════════════════

SITUACIÓN:
- PWA calculateGlobalBatteries (líneas 517-537) aplica computeNutritionRecoveryMultiplier al muscHalfLife global.
- RN: NO tiene esta modulación.

PROCESO:
1. DESPUÉS del bloque Bayesian (Bloque 2.3), AGREGAR:
   ```typescript
   // MODULADOR DE NUTRICIÓN (Afecta la recarga Muscular)
   if ((settings as any)?.algorithmSettings?.augeEnableNutritionTracking !== false) {
       const nutritionResult = computeNutritionRecoveryMultiplier({
           nutritionLogs,
           settings,
           stressLevel: recentWellbeing?.stressLevel ?? 3,
           hoursWindow: 48,
       });
       const nutMult = nutritionResult.recoveryTimeMultiplier;
       muscHalfLife *= nutMult;
       if (nutritionResult.status === 'deficit') {
           auditLogs.muscular.push({ icon: '📉', label: 'Déficit Calórico (Recarga Lenta)', val: '', type: 'info' });
       } else if (nutritionResult.status === 'surplus') {
           auditLogs.muscular.push({ icon: '🚀', label: 'Superávit Calórico (Recarga Acelerada)', val: '', type: 'info' });
       }
   } else if ((settings as any)?.calorieGoalObjective === 'deficit') {
       muscHalfLife *= 1.25;
       auditLogs.muscular.push({ icon: '📉', label: 'Régimen Déficit', val: '', type: 'info' });
   }
   ```

═══════════════════════════════════════════
BLOQUE 2.5: SLEEP RECOMMENDATIONS + STRESS CHECK
═══════════════════════════════════════════

SITUACIÓN:
- PWA calculateSleepRecommendations (línea 429): if (volume > 15 || stress > 200)
- RN calculateSleepRecommendations (línea 423): if (volume > 15) ← falta stress check

PROCESO:
1. En RN recoveryService.ts, función calculateSleepRecommendations, MODIFICAR:
   ```typescript
   // ANTES:
   if (volume > 15) {
   // DESPUÉS:
   const stress = (todayWorkout as any).sessionStressScore || 0;
   if (volume > 15 || stress > 200) {
   ```

═══════════════════════════════════════════
BLOQUE 2.6: RE-EXPORTS EN auge.ts
═══════════════════════════════════════════

SITUACIÓN:
- PWA auge.ts (líneas 139-148) re-exporta classifiers de shared-domain.
- RN auge.ts: NO tiene estos re-exports.

PROCESO:
1. AGREGAR al final de apps/mobile/src/services/auge.ts:
   ```typescript
   // Shared classifiers (platform-neutral)
   export {
       classifyAcwrZone,
       ACWR_ZONE_LABELS,
       ACWR_ZONE_COLORS,
       classifyStressZone,
       STRESS_ZONE_LABELS,
       STRESS_ZONE_COLORS,
       type AcwrZone,
       type StressLevel,
   } from '@kpkn/shared-domain';
   ```
2. VERIFICAR que @kpkn/shared-domain exporta estos desde su index.ts.

═══════════════════════════════════════════
BLOQUE 2.7: COMPUTE WORKER — InteractionManager
═══════════════════════════════════════════

SITUACIÓN:
- RN computeWorkerService.ts usa Promise.resolve() como fallback.
- Funciona pero no difiere el cálculo al idle time.

PROCESO:
1. MODIFICAR apps/mobile/src/services/computeWorkerService.ts:
   ```typescript
   import { InteractionManager } from 'react-native';

   function withAsyncFallback<TArgs extends unknown[], TResult>(fn: (...args: TArgs) => TResult) {
     return (...args: TArgs): Promise<TResult> => {
       return new Promise((resolve, reject) => {
         InteractionManager.runAfterInteractions(() => {
           try {
             resolve(fn(...args));
           } catch (error) {
             reject(error);
           }
         });
       });
     };
   }
   ```

═══════════════════════════════════════════
BLOQUE 2.8: VOLUME CALCULATOR DELTA
═══════════════════════════════════════════

PROCESO:
1. COMPARAR services/volumeCalculator.ts (PWA: 27,631B) vs apps/mobile/src/services/volumeCalculator.ts (RN: 19,416B)
2. Listar funciones exportadas en PWA que NO están en RN
3. Para cada función faltante que sea lógica pura (no UI):
   - Copiar función a RN volumeCalculator.ts
   - Adaptar tipos (ExerciseMuscleInfo → ExerciseCatalogEntry)
4. Hacer lo mismo con volumeCalibrationService.ts (PWA: 9,753B vs RN: 7,127B)
</task>

<verification>
1. cd apps/mobile && npm run typecheck → 0 errores nuevos
2. Verificar que calculateGlobalBatteries ahora retorna articularBatteries
3. Verificar que computeNutritionRecoveryMultiplier está en shared-domain y se exporta
</verification>

<deliverables>
- packages/shared-domain/src/auge/nutritionRecovery.ts (NUEVO o VERIFICADO)
- packages/shared-domain/src/index.ts (ACTUALIZADO con export)
- apps/mobile/src/services/recoveryService.ts (MERGEADO — 4 bloques nuevos)
- apps/mobile/src/services/auge.ts (ACTUALIZADO — re-exports)
- apps/mobile/src/services/computeWorkerService.ts (ACTUALIZADO — InteractionManager)
- apps/mobile/src/services/volumeCalculator.ts (MERGEADO — funciones faltantes)
</deliverables>
```

---

# FASE 3 — STORES Y SYNC

```text
<role>
Eres un ingeniero de estado de aplicación especializado en Zustand y sincronización offline-first.
</role>

<context>
PROYECTO: C:\Users\valen\Downloads\kpkn-fit-(beta-test)
ESTADO: Zustand stores con persistencia MMKV (react-native-mmkv).
SYNC: Supabase para backend. Google Drive DIFERIDO (no implementar).
</context>

<task>
FASE 3: STORES Y SINCRONIZACIÓN

═══════════════════════════════════════════
BLOQUE 3.1: authStore PARIDAD
═══════════════════════════════════════════

ARCHIVOS:
- PWA: stores/authStore.ts (2,654 bytes)
- RN: apps/mobile/src/stores/authStore.ts (1,513 bytes)

PROCESO:
1. COMPARAR funciones exportadas de ambos.
2. PWA típicamente tiene: signIn, signUp, signOut, resetPassword, refreshSession, setSession
3. RN probablemente le faltan algunas. AGREGAR las faltantes.
4. Adaptar de Supabase browser client a Supabase RN client si es necesario.

═══════════════════════════════════════════
BLOQUE 3.2: supabaseSyncService EXPANSIÓN
═══════════════════════════════════════════

ARCHIVOS:
- PWA: services/supabaseSyncService.ts (7,804 bytes)
- RN: apps/mobile/src/services/supabaseSyncService.ts (1,194 bytes)

PROCESO:
1. LEER PWA supabaseSyncService.ts completo.
2. Identificar todas las funciones de sync: syncWorkouts, syncPrograms, syncNutrition, syncBody, syncSettings
3. LEER RN supabaseSyncService.ts → verificar qué funciones tiene.
4. Para cada función faltante:
   - Copiar lógica de PWA
   - Adaptar storage calls (localStorage → MMKV via getJsonValue/setJsonValue)
   - Mantener la misma estructura de datos para compatibilidad bidireccional PWA↔RN
5. Implementar cola offline simple:
   - Guardar mutations pendientes en MMKV key 'rn.syncQueue'
   - Al detectar red disponible, procesar cola

NO implementar Google Drive. Dejar el stub actual con un TODO comment.
</task>

<verification>
1. npm run typecheck en RN → sin errores nuevos
2. Verificar que las funciones de sync existen pero no crashean si Supabase no está configurado
</verification>
```

---

# FASE 4 — HOOKS Y UTILS

```text
<role>
Eres un desarrollador React Native senior especializado en hooks performantes y utilidades TypeScript.
</role>

<context>
PROYECTO: C:\Users\valen\Downloads\kpkn-fit-(beta-test)
HOOKS PWA: hooks/ (8 archivos)
UTILS PWA: utils/ (20 archivos)
RN UTILS: apps/mobile/src/utils/ (13 archivos)
RN ya tiene algunos equivalentes funcionales en data/ (useExerciseSearch.ts, useBodyProgressData.ts)
</context>

<task>
FASE 4: HOOKS Y UTILS

═══════════════════════════════════════════
BLOQUE 4.1: HOOKS
═══════════════════════════════════════════

1. useMuscleRecovery:
   - PWA: hooks/useMuscleRecovery.ts (2,697 bytes)
   - Usa calculateMuscleBatteryAsync del computeWorker
   - Escucha workoutStore, wellbeingStore, settingsStore
   - Retorna: { muscleBatteries, globalBatteries, readiness, isLoading }
   
   PROCESO:
   - VERIFICAR si algún componente RN ya tiene esta lógica inline
   - Si no, CREAR apps/mobile/src/hooks/useMuscleRecovery.ts
   - Usar stores de RN (useWorkoutStore, useSettingsStore, etc.)
   - Usar calculateMuscleBatteryAsync de computeWorkerService.ts de RN

2. useAchievements:
   - PWA: hooks/useAchievements.ts (1,471 bytes) + data/achievements.ts (6,508 bytes)
   - RN: screens/Home/AchievementsScreen.tsx existe
   - VERIFICAR si la lógica de achievements está inline en el screen o si necesita hook separado

3. Los demás hooks (useSettings, useGoogleDrive, useKeyboardOverlayMode, useLocalStorage):
   - NO migrar. Son wrappers PWA que RN no necesita.

═══════════════════════════════════════════
BLOQUE 4.2: UTILS
═══════════════════════════════════════════

UTILS FALTANTES EN RN (verificar cada uno antes de crear):

1. programHelpers.ts:
   - PWA: utils/programHelpers.ts (4,361 bytes)
   - Funciones: getAbsoluteWeekIndex, generateSessionsForWeek, isProgramSimple, etc.
   - VERIFICAR que no está en programStore.ts de RN
   - Si falta → CREAR apps/mobile/src/utils/programHelpers.ts
   - Adaptar: crypto.randomUUID() → generateId() de RN

2. sessionDayLabel.ts:
   - PWA: utils/sessionDayLabel.ts (3,689 bytes)
   - Genera "Día de Pecho y Espalda" analizando músculos
   - CREAR apps/mobile/src/utils/sessionDayLabel.ts
   - Verificar que muscle names coinciden con RN muscle database

3. sessionArticularBatteries.ts y sessionMusclesForBattery.ts:
   - PWA: 1,743B y 1,664B
   - RN: 569B y 536B ← MUCHO más pequeños
   - COMPARAR y agregar funciones faltantes

4. calculations.ts, dateUtils.ts, exerciseIndex.ts, canonicalMuscles.ts:
   - YA EXISTEN en RN
   - COMPARAR tamaños y funciones export
   - Solo agregar funciones faltantes, NO sobrescribir
</task>

<verification>
1. npm run typecheck en RN → sin errores nuevos
2. Importar programHelpers y sessionDayLabel en un test y verificar que exportan las funciones
</verification>
```

---

# FASE 5 — ONBOARDING

```text
<role>
Eres un experto en UI/UX React Native con dominio de react-native-reanimated 3,
React Navigation y diseño "Material Liquid Glass White".
</role>

<context>
PROYECTO: C:\Users\valen\Downloads\kpkn-fit-(beta-test)
TARGET: apps/mobile/src/screens/Onboarding/ (NO EXISTE — crear)

REGLAS DE DISEÑO RN:
- PROHIBIDO Tailwind/className. Solo Native StyleSheet.
- Colores via useColors() hook (M3 dynamic theming).
- Tipografía desde packages/design-tokens.
- Animaciones con react-native-reanimated 3 (NO Animated legacy).
- UI en ESPAÑOL.

PWA ONBOARDING (13 archivos en components/onboarding/):
- UnifiedWelcomeWizard.tsx — Orquestador
- GeneralOnboardingWizard.tsx — Wizard general
- WelcomeWizard.tsx — Base
- AnimatedSvgBackground.tsx — Background animado
- steps/AthleteTypeStep.tsx — Tipo de atleta
- steps/PhysicalDataStep.tsx — Datos físicos
- steps/ProgramNameStep.tsx — Nombre programa
- steps/SplitStep.tsx — Selección split
- steps/VolumeStep.tsx — Config volumen
- steps/RecentWorkoutsStep.tsx — Importar
- steps/BatteryRingsStep.tsx — Intro AUGE
- steps/BatteryPrecalibrationStep.tsx — Pre-calibración

RN ONBOARDING: 0 archivos.
</context>

<task>
FASE 5: ONBOARDING COMPLETO

PROCESO:
1. LEER cada archivo de PWA components/onboarding/ para entender:
   - Qué datos recoge cada step
   - En qué store guarda los datos
   - Qué validaciones tiene

2. CREAR la estructura de navegación:
   apps/mobile/src/screens/Onboarding/
   ├── OnboardingNavigator.tsx (stack con screens)
   ├── WelcomeScreen.tsx
   ├── AthleteTypeScreen.tsx
   ├── PhysicalDataScreen.tsx
   ├── ProgramNameScreen.tsx
   ├── SplitScreen.tsx
   ├── VolumeScreen.tsx
   ├── BatteryIntroScreen.tsx
   └── PrecalibrationScreen.tsx

3. IMPLEMENTAR cada screen:

   WelcomeScreen:
   - Logo KPKN + título + subtítulo
   - Botón "Comenzar" con haptic feedback
   - Animación de entrada (FadeInDown)

   AthleteTypeScreen:
   - Opciones: Principiante, Intermedio, Avanzado, Powerlifter, Bodybuilder
   - Iconos o ilustraciones para cada tipo
   - Guarda en settingsStore: athleteType
   - Afecta umbrales de AUGE

   PhysicalDataScreen:
   - Inputs: peso (30-300 kg), altura (100-250 cm), edad (14-100), sexo (M/F/Otro)
   - Guarda en bodyStore
   - Calcula TDEE inicial

   ProgramNameScreen:
   - TextInput con validación (no vacío, max 50 chars)
   - Sugerencias de nombres

   SplitScreen:
   - Opciones: Push/Pull/Legs, Bro Split, Upper/Lower, Full Body, Custom
   - Visual card selection
   - Guarda en programStore: split template

   VolumeScreen:
   - Sliders para MEV y MRV target
   - Explicación visual de zonas de volumen
   - Guarda en settingsStore: volumeCalibration

   BatteryIntroScreen:
   - Explicación visual del sistema AUGE
   - Animación de los 3 anillos (CNS, Muscular, Spinal)
   - Solo informativo, no recoge datos

   PrecalibrationScreen:
   - Sliders: sleepQuality (1-5), stressLevel (1-5), DOMS (1-5), motivation (1-5)
   - Llama a applyPrecalibrationReadinessOnly() de recoveryService
   - Guarda resultado en settingsStore.batteryCalibration
   - Marca settingsStore.onboardingCompleted = true

4. NAVIGATION GUARD:
   - En el navigator principal de la app, verificar settingsStore.onboardingCompleted
   - Si false → mostrar OnboardingStack
   - Si true → mostrar MainTabs
   - Cada screen tiene botón "Siguiente" y opcionalmente "Saltar"

5. ANIMACIONES:
   - Transiciones entre steps: SlideInRight / SlideOutLeft
   - Progress bar animado (step X de 8)
   - Cards con haptic feedback al seleccionar
</task>

<verification>
1. npm run typecheck → sin errores
2. En emulador: la app debe mostrar onboarding al primer inicio
3. Completar todos los steps → la app debe ir al Home
4. Reabrir la app → NO debe mostrar onboarding de nuevo
</verification>
```

---

# FASE 6 — UI FALTANTES

```text
<role>
Eres un desarrollador React Native senior especializado en Skia, FlashList, y UX de
alta performance para apps de fitness.
</role>

<context>
PROYECTO: C:\Users\valen\Downloads\kpkn-fit-(beta-test)
RN tiene 48 screens funcionales. Los gaps son componentes DENTRO de screens existentes.

REGLAS:
- Solo Native StyleSheet + useColors() para theming M3
- Charts con @shopify/react-native-skia
- Listas largas con @shopify/flash-list
- Animaciones con react-native-reanimated 3
</context>

<task>
FASE 6: COMPONENTES UI FALTANTES

═══════════════════════════════════════════
BLOQUE 6.1: SESSION EDITOR — ARTICULAR BATTERIES
═══════════════════════════════════════════

Ahora que calculateGlobalBatteries retorna articularBatteries (Fase 2):
1. VERIFICAR si SessionEditorScreen.tsx muestra baterías articulares
2. Si no, crear un componente ArticularBatteryIndicator:
   - Muestra 6 articular batteries (shoulder, elbow, knee, hip, ankle, cervical)
   - Color coding: verde >70, amarillo 40-70, rojo <40
   - Integrar en la vista de sesión activa

═══════════════════════════════════════════
BLOQUE 6.2: FATIGUE INDICATORS EN SESIÓN
═══════════════════════════════════════════

PWA tiene FatigueIndicators.tsx que muestra fatiga del músculo actual durante la sesión:
1. VERIFICAR si ActiveSessionScreen.tsx tiene indicadores de fatiga en tiempo real
2. Si no, crear FatigueIndicator component:
   - Muestra battery del músculo primario del ejercicio actual
   - Se actualiza después de cada set completado
   - Usa calculateMuscleBattery() con datos en vivo

═══════════════════════════════════════════
BLOQUE 6.3: ANALYTICS DASHBOARDS
═══════════════════════════════════════════

VERIFICAR qué componentes de analytics faltan comparando PWA vs RN:
1. PersonalRecordsChart — ¿RN tiene charts en PersonalRecordsScreen?
2. VolumeBudgetWidget — ¿Existe presupuesto de volumen semanal?
3. TrendCard — ¿Existe análisis de tendencias con regresión lineal?
4. BodyComposition charts (FFMI, BodyFat, Weight) — ¿Existen en BodyProgressScreen?
5. CalorieHistoryChart — ¿Existe en NutritionDashboard?

PARA CADA gráfico faltante:
- Implementar con @shopify/react-native-skia
- Datos desde los stores correspondientes
- Diseño "Liquid Glass" con gradients sutiles

NO implementar: CorrelationDashboard, PowerliftingDashboard (post-MVP).
</task>

<verification>
1. npm run typecheck → sin errores
2. Visual check en emulador de cada componente nuevo
</verification>
```

---

# FASE 7 — AI SERVICES

```text
<role>
Eres un ingeniero de integración de IA multi-proveedor (Gemini, GPT, DeepSeek)
con experiencia en on-device inference y backend proxying.
</role>

<context>
PROYECTO: C:\Users\valen\Downloads\kpkn-fit-(beta-test)

AI EN PWA:
- services/aiService.ts (25,253 bytes) — 50+ funciones
- services/geminiService.ts (24,050B), gptService.ts (28,946B), deepseekService.ts (21,173B)

AI EN RN:
- services/aiService.ts (10,290 bytes) — ~7 funciones
- services/coachChatService.ts (6,772B) — reglas keyword matching
- services/geminiService.ts (1,938B), gptService.ts (1,923B), deepseekService.ts (1,948B) — stubs

AI ON-DEVICE:
- services/localAiService.ts (RN: 4,375B) — FunctionGemma pipeline
</context>

<task>
FASE 7: AI SERVICES — CIERRE DE GAPS CRÍTICOS

═══════════════════════════════════════════
BLOQUE 7.1: COACH CHAT — BACKEND PROXY
═══════════════════════════════════════════

SITUACIÓN:
- RN usa coachChatService.ts con reglas keyword → respuestas predefinidas
- PWA usa streaming LLM real

PROCESO:
1. LEER services/backendAIService.ts de RN (2,410B)
2. AGREGAR endpoint para coach chat:
   - POST /ai/coach/chat con body: { messages: ChatMessage[], context: UserContext }
   - El backend ya existe en backend/main.py
3. MODIFICAR coachChatService.ts:
   - Intentar backend proxy primero
   - Si falla (sin red o backend down) → usar reglas keyword como fallback
4. Mantener la misma interfaz de response para la UI

═══════════════════════════════════════════
BLOQUE 7.2: STUBS DOCUMENTADOS
═══════════════════════════════════════════

Para funciones AI que NO se implementan ahora:
1. VERIFICAR qué funciones en RN aiService.ts lanzan errores bloqueantes
2. REEMPLAZAR throws con returns de objeto vacío + console.warn
3. Image generation: mantener stub pero mostrar toast "Próximamente" en vez de crash
4. DOCUMENTAR cada stub con // TODO: Implementar post-MVP - requiere endpoint backend X
</task>

<verification>
1. npm run typecheck → sin errores
2. Llamar a cada función AI stub → debe retornar gracefully, no crashear
</verification>
```

---

# FASE 8 — TESTING

```text
<role>
Eres un ingeniero de QA especializado en testing de paridad entre plataformas.
</role>

<context>
PROYECTO: C:\Users\valen\Downloads\kpkn-fit-(beta-test)
TESTS EXISTENTES:
- npm run test:nutrition-logging → tests de nutrición PWA
- npm run mobile:test → Jest tests de RN
SHARED-DOMAIN: packages/shared-domain/src/ — lógica platform-neutral
</context>

<task>
FASE 8: TEST SUITE DE PARIDAD

1. CREAR packages/shared-domain/src/auge/__tests__/parity.test.ts:
   - Fixture: 10 sesiones de workout con exercises, sets, y RPE variados
   - Test: calculateMuscleBattery("Pectorales") produce score 0-100 coherente
   - Test: calculateGlobalBatteries retorna {cns, muscular, spinal} como números 0-100
   - Test: computeNutritionRecoveryMultiplier con deficit → multiplier > 1.0
   - Test: computeNutritionRecoveryMultiplier con surplus → multiplier < 1.0

2. CREAR apps/mobile/src/__tests__/exerciseDatabase.test.ts:
   - Test: STATIC_EXERCISE_DATABASE tiene >= 800 entries
   - Test: cada entry tiene id, name, involvedMuscles como campos no vacíos
   - Test: no hay duplicados por nombre normalizado

3. CREAR apps/mobile/src/__tests__/onboarding.test.ts:
   - Test: settingsStore.onboardingCompleted default es false
   - Test: applyPrecalibrationReadinessOnly retorna deltas numéricos

4. EJECUTAR todos los tests:
   npm run test:nutrition-logging
   npm run mobile:test

5. REPORTAR: X tests pasados, Y fallidos, Z skipped
</task>
```

---

# FASE 9 — VERIFICACIÓN FINAL

```text
<role>
Eres un ingeniero de release management que valida la app antes del cutover a producción.
</role>

<context>
PROYECTO: C:\Users\valen\Downloads\kpkn-fit-(beta-test)
TARGET: APK Android (debug)
PREREQUISITOS: Fases 0-8 completadas.
</context>

<task>
FASE 9: VERIFICACIÓN FINAL Y CHECKLIST

═══════════════════════════════════════════
CHECKLIST DE VERIFICACIÓN
═══════════════════════════════════════════

PASO 1 — TYPECHECK LIMPIO:
npx tsc --noEmit                              # PWA
cd apps/mobile && npm run typecheck            # RN
→ Ambos deben pasar con 0 errores

PASO 2 — TESTS:
npm run test:nutrition-logging                 # PWA nutrition
cd apps/mobile && npm run test                 # RN Jest
→ 0 tests fallidos

PASO 3 — BUILD:
cd apps/mobile/android && ./gradlew assembleDebug
→ APK debe generarse sin errores en app/build/outputs/apk/debug/

PASO 4 — COLD START:
- Instalar APK en dispositivo/emulador
- Abrir desde cero
- NO debe crashear

PASO 5 — SMOKE TEST USUARIO NUEVO:
- Verificar que onboarding aparece al primer inicio
- Completar todos los steps del wizard
- Crear programa simple (Full Body, 3 días)
- Iniciar sesión de workout
- Registrar 3 ejercicios con sets (peso + reps + RPE)
- Finalizar sesión
- Ver resumen de fatiga AUGE → batteries deben mostrar valores

PASO 6 — SMOKE TEST NUTRICIÓN:
- Abrir Nutrición
- Buscar "pollo" → debe encontrar resultado
- Log 100g → ver macros (calorías, proteína, carbs, grasa)
- Verificar parser de texto libre con "2 huevos con arroz"

PASO 7 — SETTINGS:
- Cambiar unidades (kg/lb)
- Cambiar tipo de atleta
- Toggle de sleep tracking
- Verificar que persisten al reabrir la app

PASO 8 — PERFORMANCE:
- Navegar entre 10+ screens ida y vuelta
- No debe haber jank notable ni memory warnings
- Verificar que la navegación es fluida (60fps)

ENTREGABLE FINAL:
- Reporte con PASS/FAIL por cada paso
- Si hay FAILs → crear lista priorizada de bugs con severidad
</task>
```

---

> **Nota Final**: Cada prompt está diseñado para ser **ejecutado secuencialmente**. La Fase N asume que la Fase N-1 está completada. Si MiMo reporta errores en una fase, resolverlos ANTES de avanzar a la siguiente.
