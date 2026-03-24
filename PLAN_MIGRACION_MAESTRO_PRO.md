# PLAN MIGRACIÓN MAESTRO PRO+
## KPKN Fit: PWA → React Native (MVP Production Ready)

> **Versión**: 1.0-PRO  
> **Fecha**: 2026-03-20  
> **Alcance**: Migración completa de lógica, servicios, bases de datos y UI de PWA a React Native  
> **Filosofía**: NO es un clon. Es una re-ingeniería que mejora todo lo que toca.  

---

# RESUMEN EJECUTIVO

## Estado Actual

| Dimensión | PWA | RN | Paridad |
|-----------|-----|----|---------|
| Servicios core (AUGE) | 46 | 63 | **~70% lógica** |
| Stores Zustand | 11 | 19 (8 nuevos) | **100% cobert Peters** |
| Bases de datos (datos puros) | 38 archivos | 16 archivos | **~40% migrado** |
| Componentes UI | 174 | ~150 | **~70% paridad** |
| Screens/Views | ~100 | ~47 | **~60% paridad** |
| Hooks | 8 | Integrados | **~50% cobert Peters** |
| AI (multi-provider) | 50+ funciones | ~7 + stubs | **~15% paridad** |
| Worker (compute) | Web Worker real | Sync fallback | **50%** |

## Gap Crítico Detectado

### 🔴 CRÍTICO (Bloquea MVP)
1. **Base de datos de ejercicios COMPLETA** — 575KB+ de ejercicios no existen en RN
2. **Sistema AUGE con nutrición** — RN ignora modulation nutricional en baterías
3. **AI Coach Chat** — RN usa reglas keyword, NO IA
4. **Onboarding completo** — 12 archivos PWA, RN tiene 0

### 🟡 ALTO (Afecta UX)
1. **Google Drive Backup** — Stub de 548 bytes vs 5995 bytes
2. **Supabase Sync** — 85% más chico en RN
3. **Compute Worker** — Sin thread separado en RN
4. **Image Generation** — Stub bloqueante en RN

### 🟢 MEDIO (Mejoras post-MVP)
1. Analytics avanzados (Correlation, Powerlifting Dashboard)
2. Progress Photos
3. Video Analysis
4. Social Feed (es placeholder)

---

# PARTE I: ANÁLISIS DE CÓDIGO VIVO vs MUERTO

## Archivos TOTALMENTE MUERTOS (0 bytes o nunca importados)

| Archivo | Acción |
|---------|--------|
| `untitled.tsx` | ELIMINAR |
| `components/CoachAnalysis.tsx` | ELIMINAR (vacío) |
| `components/WorkoutSession.tsx.backup` | ELIMINAR |
| `apps/mobile/src/screens/Workout/SessionEditorScreen.tsx.backup` | ELIMINAR |
| `data/activationExercises.ts` | ELIMINAR (vacío) |
| `data/warmupExercises.ts` | ELIMINAR (vacío) |
| `data/gyms.ts` | ELIMINAR (solo comments/mock) |
| `data/supplements.ts` | ELIMINAR (solo comments) |

## Archivos VIVOS que NO se migran (UI PWA nomás)

| Archivo | Razón | Acción |
|---------|-------|--------|
| `utils/theme.ts` | Sistema Tailwind/M3 PWA | OMITIR — RN tiene su propio theming |
| `utils/shapes.ts` | Diseño tokens PWA | OMITIR — Cubierto por design-tokens |
| `utils/typography.ts` | Tipografía PWA | OMITIR — Cubierto por design-tokens |
| `components/TabBar.tsx` | HTML tabs PWA | OMITIR — RN usa KpknBottomBar nativo |
| `components/SubTabBar.tsx` | HTML tabs PWA | OMITIR — Navegación nativa |

## Archivos VIVOS que SÍ se migran

Todo lo que sea **lógica pura de negocio, cálculos, o datos** DEBE migrar:
- ✅ Services de AUGE (fatiga, recovery, volume, adaptive)
- ✅ Todas las bases de datos (ejercicios, alimentos, músculos, articulaciones)
- ✅ Utils de cálculo puro
- ✅ Hooks con lógica derivada
- ❌ Pero NO las UI components raw de PWA — se re-implementan en RN con Native StyleSheet

---

# PARTE II: FILOSOFÍA DE MEJORA CONTINUA

## Principio "Millonario en el Destino"

Cada migración NO es un copy-paste. Es una re-ingeniería donde:

| Aspecto | PWA | RN PRO+ |
|---------|-----|---------|
| **Rendering** | DOM + CSS | Native StyleSheet + Skia |
| **Charts** | SVG/Canvas | Skia (60fps) |
| **Navegación** | Hash history | Native stack (instantáneo) |
| **Storage** | IndexedDB | MMKV (10x más rápido) |
| **Computación** | Web Worker (main thread fallback) | InteractionManager + profiling |
| **Notificaciones** | Browser | Native + Haptics |
| **Background** | Service Worker | Native Background Tasks |
| **Widgets** | No | Android Widgets con tiles |

---

# PARTE III: PLAN DE MIGRACIÓN POR FASES

## FASE 0: Cleanup y Validación de Baseline
## Duración estimada: 2-4 horas

### Paso 0.1: ELIMINAR Código Muerto
**Prompt para IA:**

```
EJECUTAR en C:\Users\valen\Downloads\kpkn-fit-(beta-test):

1. ELIMINAR los siguientes archivos (nunca se usan, pesan 0 bytes o son backups):
   - rm "untitled.tsx"
   - rm "components/CoachAnalysis.tsx"
   - rm "components/WorkoutSession.tsx.backup"
   - rm "apps/mobile/src/screens/Workout/SessionEditorScreen.tsx.backup"
   - rm "data/activationExercises.ts"
   - rm "data/warmupExercises.ts"
   - rm "data/gyms.ts"
   - rm "data/supplements.ts"

2. VERIFICAR que no hay imports referencing estos archivos

3. HACER git status y confirmar los cambios
```

### Paso 0.2: Setup Ambiente y Verificación Inicial
**Prompt para IA:**

```
EJECUTAR en orden:

1. npm install  # Asegurar todas las dependencias instaladas

2. VERIFICAR que npx tsc --noEmit pasa sin errores en PWA

3. cd apps/mobile && npm install && npm run typecheck

4. CREAR reporte de:
   - Cuántos archivos tienen errores de tipos en PWA
   - Cuántos archivos tienen errores de tipos en RN
   - Cuál es el estado actual de mobile (buildable o no)

5. GENERAR lista de TODOS los archivos .ts/.tsx en:
   - /services/
   - /stores/
   - /hooks/
   - /utils/
   - /data/
   
   Para cada archivo, indicar:
   - Tamaño en bytes
   - Líneas de código
   - Cantidad de exports
   
6. Guardar este inventario en ./INVENTORY_BASELINE.json
```

---

## FASE 1: Migración de Bases de Datos (DATA FIRST)
## Duración estimada: 6-10 horas

> **Principio**: Las bases de datos son el fundamento. Sin datos, nada funciona.

### Paso 1.1: Base de Datos de Ejercicios (CRÍTICO)
**Prompt para IA - PARTE HUMANA:**

La PWA tiene **575KB+ de datos de ejercicios** en múltiples archivos:
- `exerciseDatabase.ts` (77KB)
- `exerciseDatabaseCentral.ts` (157KB)  
- `exerciseDatabaseExpansion.ts` (89KB)
- `exerciseDatabaseExpansion2.ts` (32KB)
- `exerciseDatabaseExpansion3.ts` (238KB)
- `exerciseDatabaseExtended.json` (196KB)
- `exerciseList.ts` (970 bytes)
- `inferMusclesFromName.ts` (17KB)

En RN **NO EXISTE** ninguno de estos archivos. El `exerciseStore` de RN únicamente tiene un subconjunto de `initialMuscleGroupDatabase.ts` con solo 6 categorías musculares básicas.

**Impacto**: Sin esta base de datos, el sistema AUGE no puede:
- Calcular fatiga por músculo
- Calcular baterías de recuperación
- Inferir músculos desde ejercicios custom
- Clasificar ejercicios por movimiento

**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE BASE DE DATOS DE EJERCICIOS

ARCHIVO FUENTE (PWA): 
- data/exerciseDatabase.ts
- data/exerciseDatabaseCentral.ts
- data/exerciseDatabaseExpansion.ts
- data/exerciseDatabaseExpansion2.ts
- data/exerciseDatabaseExpansion3.ts
- data/exerciseDatabaseExtended.json
- data/exerciseList.ts
- data/inferMusclesFromName.ts

ARCHIVO DESTINO: 
- apps/mobile/src/data/exerciseDatabase.ts (NUEVO)
- apps/mobile/src/data/exerciseList.ts (NUEVO)
- apps/mobile/src/data/inferMusclesFromName.ts (NUEVO)

PROCESO:

1. LEER y PARSEAR todos los archivos fuente en orden:
   - Los archivos expansion*.ts importan y re-exportan desde exerciseDatabase.ts
   - exerciseDatabase.ts importa desde exerciseDatabaseExpansion.ts (que a su vez importa desde expansion2, expansion3)
   - exerciseDatabaseExtended.json es formato JSON standalone
   - exerciseList.ts es una lista simple de strings (nombres de ejercicios)
   - inferMusclesFromName.ts tiene función heuristic para inferir músculos desde nombre

2. CONSOLIDAR en un único archivo:
   - exerciseDatabase.ts exports: Exercise[] (todas las propiedades: id, name, musclePrimary, muscleSecondary, equipment, movementPattern, etc.)
   - exerciseList.ts exports: string[] (solo nombres)
   - inferMusclesFromName.ts exports: inferMusclesFromName(exerciseName: string): MuscleId[]

3. ADAPTAR para RN:
   - Cambiar imports de '../data/...' a './exerciseDatabase' relativo
   - Verificar que los tipos MuscleId, ExerciseId, MovementPattern existan en @kpkn/shared-types
   - Si hay tipos locales en PWA (ej: LocalExercise), convertirlos a tipos compartidos

4. VERIFICAR que el archivo consolidado tiene:
   - Al menos 800+ ejercicios
   - Todos los campos necesarios para AUGE (primaryMuscle, secondaryMuscles, equipment)
   - Sin duplicados (verificar por nombre normalizado)

5. MIGRAR la función inferMusclesFromName:
   - Mantener el algoritmo heurístico exacto
   - Esta función usa regex y mapeos de sinónimos para detectar músculos en el nombre
   - NO usar IA — es un algoritmo determinista

6. ACTUALIZAR exerciseStore de RN:
   - Leer el nuevo archivo exerciseDatabase.ts
   - Mantener la interfaz: ExerciseStore tiene exercises: Exercise[]
   - Verificar que todas las funciones de búsqueda (search, filterByMuscle, filterByEquipment) funcionan

7. TEST: 
   - Buscar "press" debe retornar ejercicios de press (bench, shoulder, etc.)
   - Buscar "curl" debe retornar ejercicios de bíceps
   - inferMusclesFromName("Barbell Bench Press") debe retornar ["pectoral"] o similar

ENTREGABLE:
- apps/mobile/src/data/exerciseDatabase.ts (archivo consolidado)
- apps/mobile/src/data/exerciseList.ts 
- apps/mobile/src/data/inferMusclesFromName.ts
- exerciseStore.ts actualizado
```

### Paso 1.2: Jerarquía Muscular Completa
**Prompt para IA - PARTE HUMANA:**

PWA tiene:
- `initialMuscleHierarchy.ts` (2,234 bytes) — árbol completo de músculos
- `muscleGroupDatabase.ts` (165 bytes) — re-export 
- `initialMuscleGroupDatabase.ts` (37,470 bytes) — 195 líneas con 40+ grupos musculares detallados

RN tiene:
- `initialMuscleGroupDatabase.ts` (2,082 bytes) — solo 6 categorías básicas

**Gap**: RN no tiene la jerarquía completa de músculos, solo categorías de nivel superior.

**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE JERARQUÍA MUSCULAR

ARCHIVO FUENTE (PWA):
- data/initialMuscleHierarchy.ts
- data/muscleGroupDatabase.ts
- data/initialMuscleGroupDatabase.ts

ARCHIVO DESTINO:
- apps/mobile/src/data/initialMuscleHierarchy.ts (NUEVO)
- apps/mobile/src/data/muscleGroupDatabase.ts (NUEVO, si es diferente a PWA)
- apps/mobile/src/data/initialMuscleGroupDatabase.ts (SOBREESCRIBIR existente)

PROCESO:

1. LEER initialMuscleHierarchy.ts de PWA:
   - Formato: estructura de árbol con MuscleHierarchyNode[]
   - Campos: id, name, children[], parentId, canonicalName
   - Este archivo define PARENT-CHILD relationships entre músculos

2. LEER initialMuscleGroupDatabase.ts de PWA:
   - 37KB, contiene MuscleGroupData[]
   - Campos por grupo: id, name, canonicalName, anatomyInfo, relatedJoints, 
     relatedTendons, exercisesPrimary, exercisesSecondary, defaultRepRanges,
     recoveryHours, notes, tags, role (push/pull/hinge/squat), aliases[]

3. IMPORTANTE: 
   - La versión RN de initialMuscleGroupDatabase.ts tiene solo 6 categorías
   - SOBRESCRIBIR con la versión PWA completa
   - Esta es la base para TODOS los cálculos AUGE

4. VERIFICAR tipos en @kpkn/shared-types:
   - MuscleHierarchyNode debe existir
   - MuscleGroupData debe existir
   - Si no existen, definir en apps/mobile/src/types/muscleTypes.ts

5. CONSOLIDAR:
   - MuscleHierarchy define qué músculos pertenecen a qué grupos
   - initialMuscleGroupDatabase tiene los datos detallados
   - Son archivos SEPARADOS pero relacionados

6. ACTUALIZAR imports en:
   - apps/mobile/src/stores/exerciseStore.ts
   - apps/mobile/src/services/auge.ts
   - apps/mobile/src/services/fatigueService.ts
   - apps/mobile/src/services/recoveryService.ts
   - Cualquier service que use MuscleHierarchy o MuscleGroupData

7. TEST:
   - Verificar que MuscleHierarchy tiene todos los músculos (40+)
   - Verificar que cada músculo tiene parentId válido o null (root)
   - Verificar que exerciseStore puede buscar por grupo muscular

ENTREGABLE:
- initialMuscleHierarchy.ts migrado
- initialMuscleGroupDatabase.ts sobreescrito con versión completa
- imports actualizados en todos los services
```

### Paso 1.3: Bases de Datos Anatómicas (Articulaciones, Tendones)
**Prompt para IA - PARTE HUMANA:**

PWA tiene bases de datos de articulaciones y tendones que RN no tiene:
- `jointDatabase.ts` (13,969 bytes) — 60+ articulaciones con injuries, protective exercises
- `tendonDatabase.ts` (7,868 bytes) — tendones con injury risks

Estas alimentan el sistema AUGE de articulaciones (articular batteries).

**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE JOINT Y TENDON DATABASES

ARCHIVO FUENTE (PWA):
- data/jointDatabase.ts
- data/tendonDatabase.ts

ARCHIVO DESTINO:
- apps/mobile/src/data/jointDatabase.ts (NUEVO)
- apps/mobile/src/data/tendonDatabase.ts (NUEVO)

PROCESO:

1. LEER jointDatabase.ts:
   - JointDefinition[] con campos:
     - id, name, aliases[], category, movements[], 
     - commonInjuries[], protectiveExercises[], musclesInvolved[],
     - mobilityNotes, strengthNotes
   - 60+ articulaciones

2. LEER tendonDatabase.ts:
   - TendonDefinition[] con campos:
     - id, name, aliases[], muscleGroup, jointRelated[],
     - commonInjuries[], riskFactors[], recoveryExercises[]
   - Usado por tendonRecoveryService y tendonAlertsService

3. VERIFICAR que existen tipos en @kpkn/shared-types:
   - JointDefinition
   - TendonDefinition
   - Si no existen, crear en apps/mobile/src/types/anatomy.ts

4. MIGRAR a RN:
   - Copiar archivos tal cual (son datos puros, sin lógica de UI)
   - Solo adaptar imports relativos si es necesario

5. ACTUALIZAR imports en services que los usan:
   - tendonRecoveryService.ts (verificar que importa desde ubicación correcta)
   - tendonAlertsService.ts
   - tendonRecoveryService.ts de RN

6. TEST:
   - Importar jointDatabase y verificar que tiene 60+ entradas
   - Importar tendonDatabase y verificar que tiene 20+ entradas
   - Verificar que tendonRecoveryService puede acceder a estos datos

ENTREGABLE:
- apps/mobile/src/data/jointDatabase.ts
- apps/mobile/src/data/tendonDatabase.ts
- imports actualizados en tendon services
```

### Paso 1.4: Base de Datos de Alimentos (ALIMENTOS + CHILE)
**Prompt para IA - PARTE HUMANA:**

PWA tiene una base de datos de alimentos MASSIVA con 30MB+ de datos:
- `foodDatabase.ts` (37KB)
- `foodDatabaseExpansion.ts` (8KB)
- `openFoodFactsOffline.json` (16.7MB)
- `usdaFoodsOffline.json` (6.9MB)
- `usdaFoundationFoods.json` (6.8MB)
- `localChileanFoods.ts` (2KB) — foods chilenos específicos
- `foodSynonyms.ts` (3KB)
- `foodTaxonomy.ts` (8KB)

RN tiene `foodDatabase.ts` pero con estructura DIFERENTE y sin los JSON de USDA.

**Gap principal**: 
- Los JSON de USDA y OpenFoodFacts están en PWA pero **NO se usan** (size 0 import)
- `localChileanFoods.ts` existe en ambos pero con estructura ligeramente diferente
- La estructura de `FoodItem` es diferente entre PWA y RN

**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE BASE DE DATOS DE ALIMENTOS

ARCHIVO FUENTE (PWA):
- data/foodDatabase.ts (37KB)
- data/foodDatabaseExpansion.ts (8KB)
- data/foodSynonyms.ts (3KB)
- data/foodTaxonomy.ts (8KB)
- data/localChileanFoods.ts (2KB)
- data/openFoodFactsOffline.json (16.7MB) — OPCIONAL,巨大的
- data/usdaFoodsOffline.json (6.9MB) — OPCIONAL,巨大
- data/usdaFoundationFoods.json (6.8MB) — OPCIONAL,巨大

ARCHIVO DESTINO:
- apps/mobile/src/data/foodDatabase.ts (SOBRESCRIBIR — diferentes estructura)
- apps/mobile/src/data/foodSynonyms.ts (SOBRESCRIBIR)
- apps/mobile/src/data/foodTaxonomy.ts (SOBRESCRIBIR)
- apps/mobile/src/data/localChileanFoods.ts (SOBRESCRIBIR)
- apps/mobile/src/data/localChileanFoods.ts en packages/shared-domain/src/nutrition/ (verificar)

PROCESO:

1. ANALIZAR diferencias de estructura FoodItem:

   PWA foodDatabase.ts:
   - export interface FoodItem { id, name, category, tags[], caloriesPer100g, proteinPer100g, carbsPer100g, fatPer100g, portionSize, portionUnit, aliases[] }
   - Usa enrichFoodCatalog() que combina BASE_FOOD_DATABASE + FOOD_DATABASE_EXPANSION

   RN foodDatabase.ts:
   - export interface FoodItem { id, name, category, calories, protein, carbs, fat, servingSize, ... }
   - DIFERENTE: RN usa 'category' string, PWA usa 'tags[]'
   - DIFERENTE: RN no tiene aliases[] en el tipo base

2. DECIDIR: Mantener estructura PWA (más rica) o RN (más simple)?

   RECOMENDACIÓN: Mantener estructura PWA porque:
   - El parser de nutrición usa tags[] para matching
   - aliases[] es crítico para synonyms
   - El aiNutritionParser usa synonyms para fuzzy matching

3. MIGRAR foodDatabase.ts:
   - Combinar BASE_FOOD_DATABASE + FOOD_DATABASE_EXPANSION como hace PWA
   - Usar el tipo FoodItem de PWA (con tags[] y aliases[])
   - Asegurar que tiene 200+ alimentos básicos

4. MIGRAR foodSynonyms.ts:
   - 3KB, define canonical food names
   - Usado por nutritionDescriptionParser para resolver "huevos" → "egg"
   - Copiar tal cual

5. MIGRAR foodTaxonomy.ts:
   - 8KB, categorize foods
   - Campos: categories[], subcategories[], hierarchy
   - Usado para agrupar y buscar

6. MIGRAR localChileanFoods.ts:
   - IMPORTANTE: Existe en DOS ubicaciones en PWA:
     a) data/localChileanFoods.ts (2KB)
     b) packages/shared-domain/src/nutrition/localChileanFoods.ts (70B)
   - RN tiene versión en shared-domain que usa 6 items
   - SOBRESCRIBIR con versión PWA completa (que tiene más items)

7. NO migrar los JSON enormes (16MB+):
   - openFoodFactsOffline.json
   - usdaFoodsOffline.json
   - usdaFoundationFoods.json
   - Estos son datos de backup, la app usa API o base slim
   - Si se necesitan, se descargan bajo demanda

8. ACTUALIZAR imports en:
   - apps/mobile/src/services/foodIndexService.ts
   - apps/mobile/src/services/aiNutritionParser.ts
   - apps/mobile/src/stores/nutritionStore.ts
   - packages/shared-domain/src/nutrition/localChileanFoods.ts

9. TEST:
   - Buscar "pollo" debe retornar Chicken con macros
   - Buscar "arroz" debe retornar Rice
   - foodSynonyms["huevos"] debe resolver a "egg"
   - Verificar que nutritionStore puede guardar logs de comida

ENTREGABLE:
- apps/mobile/src/data/foodDatabase.ts (estructura PWA)
- apps/mobile/src/data/foodSynonyms.ts
- apps/mobile/src/data/foodTaxonomy.ts  
- packages/shared-domain/src/nutrition/localChileanFoods.ts (actualizado)
```

### Paso 1.5: Movement Patterns y Otros Datos
**Prompt para IA:**

```
MIGRACIÓN DE DATOS RESTANTES

ARCHIVOS FUENTE (PWA):
- data/movementPatternDatabase.ts (6,965 bytes) — push/pull/hinge/squat/carry
- data/discomfortList.ts (8,967 bytes) — discomfort types con muscles
- data/articularBatteryConfig.ts (8,224 bytes) — Configuración de 6 articular batteries
- data/structureTemplates.ts (18,024 bytes) — Program structure templates
- data/terminology.ts (5,605 bytes) — Technical terms

ARCHIVO DESTINO:
- apps/mobile/src/data/movementPatternDatabase.ts (NUEVO)
- apps/mobile/src/data/discomfortList.ts (SOBREESCRIBIR — idéntico)
- apps/mobile/src/data/articularBatteryConfig.ts (SOBRESCRIBIR — diferentes datos)
- apps/mobile/src/data/structureTemplates.ts (NUEVO)
- apps/mobile/src/data/terminology.ts (NUEVO, si se usa en lógica)

PROCESO:

1. movementPatternDatabase.ts:
   - MovementPattern[] con: id, name, description, primaryMuscles[], 
     secondaryMuscles[], exampleExercises[], relatedStructures[]
   - Usado para clasificar ejercicios por patrón de movimiento
   - IMPORTANTE para el sistema AUGE de fatiga

2. discomfortList.ts:
   - DiscomfortItem[] con: id, name, description, musclesToDrain[], 
     severity, chronicity, suggestedActions[]
   - 8,967 bytes de datos
   - RN tiene versión idêntica (8,966 bytes) según análisis previo
   - VERIFICAR si son realmente idénticos, si sí, no necesita migración

3. articularBatteryConfig.ts:
   - PWA: 8,224 bytes con más alias mapping (face pulls, manguito rotador, etc.)
   - RN: 5,168 bytes con alias simpler
   - SOBRESCRIBIR RN con versión PWA completa
   - Define las 6 articular batteries: shoulder, elbow, knee, hip, ankle, cervical

4. structureTemplates.ts:
   - 18KB de templates para program structures
   - Usado por ProgramEditor de PWA
   - RN tiene ProgramWizardScreen pero no tiene estos templates?
   - VERIFICAR si RN usa estos o si tiene sus propios templates
   - MIGRAR si se usan

5. terminology.ts:
   - 5KB de términos técnicos del fitness
   - Solo usado por InfoTooltip en PWA
   - Si no se usa en RN, OMITIR

ENTREGABLE:
- movementPatternDatabase.ts migrado
- discomfortList.ts verificado (parity o migrado)
- articularBatteryConfig.ts sobreescrito con versión completa
- structureTemplates.ts migrado si se usa
```

---

## FASE 2: Migración de Lógica de Negocio Core (AUGE)
## Duración estimada: 10-16 horas

> **Principio**: El sistema AUGE es el corazón de la app. Debe ser IDENTICO en resultados.

### Paso 2.1: AUGE Service - Paridad Completa
**Prompt para IA - PARTE HUMANA:**

El archivo `services/auge.ts` es el HUB central que re-exporta de fatigue y recovery services. En PWA tiene 5715 bytes y en RN tiene 3005 bytes (la mitad).

Gaps detectados:
1. RN no exporta `calculateGlobalBatteriesAsync`
2. RN no re-exporta `classifyACWR`, `classifyAcwrZone`, `classifyStressZone`
3. RN usa hex colors en vez de Tailwind class names (menor)
4. RN no tiene tipos `AcwrZone`, `StressLevel` exportados

**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE AUGE SERVICE - PARIDAD COMPLETA

ARCHIVO FUENTE (PWA):
- services/auge.ts (5715 bytes)
- services/fatigueService.ts
- services/recoveryService.ts

ARCHIVO DESTINO:
- apps/mobile/src/services/auge.ts (SOBRESCRIBIR)
- apps/mobile/src/services/fatigueService.ts (SOBRESCRIBIR)
- apps/mobile/src/services/recoveryService.ts (SOBRESCRIBIR)

PROCESO:

1. LEER PWA services/auge.ts completo:
   - Identificar TODAS las re-exportaciones de fatigueService
   - Identificar TODAS las re-exportaciones de recoveryService
   - Identificar TODAS las funciones locales (ttcService, tendonAlerts, etc.)
   - Identificar TODOS los tipos exportados

2. COMPARAR con RN:
   
   DELTA en exports de auge.ts:
   - FALTA: calculateGlobalBatteriesAsync
   - FALTA: classifyACWR (re-export from fatigue)
   - FALTA: classifyAcwrZone, ACWR_ZONE_LABELS, ACWR_ZONE_COLORS
   - FALTA: classifyStressZone, STRESS_ZONE_LABELS, STRESS_ZONE_COLORS
   - FALTA: AcwrZone, StressLevel types
   - DIFERENTE: classifyStressLevel retorna hex en vez de Tailwind

3. MIGRAR funciones faltantes a RN:

   a) calculateGlobalBatteriesAsync:
      - Es un wrapper async que llama a calculateGlobalBatteries
      - En RN NO EXISTE esta función async
      - AGREGAR a RN: export const calculateGlobalBatteriesAsync = async(...) => calculateGlobalBatteries(...)
   
   b) classifyACWR:
      - Ya existe en RN fatigueService.ts pero NO se re-exporta desde auge.ts
      - AGREGAR re-export en RN: export { classifyACWR } from './fatigueService'
   
   c) classifyAcwrZone, ACWR_ZONE_LABELS, ACWR_ZONE_COLORS:
      - Ya existen en RN fatigueService.ts pero no en auge.ts
      - AGREGAR re-exports
   
   d) classifyStressZone*, STRESS_ZONE*:
      - NO EXISTEN en RN en ningún lado
      - COPIAR de PWA fatigueService.ts a RN fatigueService.ts
      - Definir umbrales idénticos (las zonas de estrés)
   
   e) AcwrZone, StressLevel types:
      - COPIAR de PWA a RN (o usar de @kpkn/shared-types si ya existen)

4. VERIFICAR classifyStressLevel:
   - PWA usa Tailwind: 'text-sky-400', 'bg-sky-500'
   - RN usa hex: '#38BDF8', '#0EA5E9'
   - ACCEPTABLE - son colores equivalentes, no afecta lógica
   - PERO asegurar que los valores umbrales son idénticos

5. TEST DE PARIDAD:
   - Crear test que dados los mismos inputs a calculateDailyReadiness()
   - PWA y RN deben producir exactamente el mismo resultado (±0.01)
   - Ver tests en packages/shared-domain/tests/auge.test.ts

ENTREGABLE:
- apps/mobile/src/services/auge.ts con paridad completa
- apps/mobile/src/services/fatigueService.ts con classifyStressZone* agregado
- apps/mobile/src/services/recoveryService.ts sin cambios (ya tiene lo necesario)
```

### Paso 2.2: Recovery Service - Nutrición y Adaptive
**Prompt para IA - PARTE HUMANA:**

El `recoveryService.ts` tiene gaps CRÍTICOS en RN:

| Función | PWA | RN | Gap |
|---------|-----|----|-----|
| calculateMuscleBattery | Nutrition multiplier dinámico | Hardcoded 1.0 | **CRÍTICO** |
| calculateGlobalBatteries | Articular batteries + nutrition + adaptive | Solo CNS/muscular/spinal | **CRÍTICO** |
| calculateSleepRecommendations | Check de stress > 200 | No tiene check | **MODERADO** |
| Adaptive half-life tuning | Tiene block | No tiene | **MODERADO** |

**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE RECOVERY SERVICE - PARIDAD NUTRICIÓN

ARCHIVO FUENTE:
- services/recoveryService.ts (PWA: 39295 bytes, RN: 30389 bytes)

ARCHIVO DESTINO:
- apps/mobile/src/services/recoveryService.ts (SOBRESCRIBIR)

PROCESO:

1. LEER ambos archivos y COMPARAR función por función

2. GAP CRÍTICO #1: calculateMuscleBattery
   
   PWA:
   ```
   const nutritionMultiplier = computeNutritionRecoveryMultiplier(
     nutritionLogs,
     muscleId,
     48 // 48h window
   );
   ```
   
   RN:
   ```
   const nutritionMultiplier = 1.0; // or 1.25 if deficit
   ```
   
   IMPACTO: PWA modulates recovery based on protein/calorie intake. RN ignores nutrition.
   
   MIGRACIÓN:
   - Si nutritionStore existe en RN con logs de comida:
     AGREGAR llamada a computeNutritionRecoveryMultiplier()
   - Si no, dejar como 1.0 por ahora (gap documentado)
   - Crear TODO comment: // TODO: Implement nutrition multiplier

3. GAP CRÍTICO #2: calculateGlobalBatteries
   
   PWA retorna:
   ```
   {
     cns, muscular, spinal,
     articularBatteries,     // <-- NO EXISTE EN RN
     articularAverage,        // <-- NO EXISTE EN RN  
     muscleArticularBlend,    // <-- NO EXISTE EN RN
     auditLogs,
     verdict
   }
   ```
   
   RN retorna:
   ```
   { cns, muscular, spinal, auditLogs, verdict }
   ```
   
   MIGRACIÓN:
   - AGREGAR articularBatteries a calculateGlobalBatteries de RN
   - Necesita imports de tendonRecoveryService
   - Necesita computeArticularBatteries() 
   - Calcular articularAverage y muscleArticularBlend

4. GAP CRÍTICO #3: Adaptive half-life tuning
   
   PWA tiene:
   ```
   if (adaptiveRecoverySamples.length > 0) {
     // Blend muscHalfLife with Bayesian curve
     personalizedHours = adaptiveRecoverySamples.reduce(...) 
   }
   ```
   
   RN: NO EXISTE este bloque
   
   MIGRACIÓN:
   - COPIAR bloque de PWA a RN
   - Necesita acceso a adaptiveCache (de augeAdaptiveService)
   - getAdaptiveRecoveryHours() existe en RN? Verificar

5. GAP MODERADO: calculateSleepRecommendations
   
   PWA tiene:
   ```
   if (stress > 200) {
     return { ..., recommendation: "Muy alto estrés..." }
   }
   if (volume > 15) {
     return { ..., recommendation: "Alto volumen..." }
   }
   ```
   
   RN tiene solo check de volume, no stress
   
   MIGRACIÓN:
   - AGREGAR check de stress > 200 antes del check de volume
   - Copiar las recomendaciones de PWA

6. TEST:
   - Llamar calculateMuscleBattery() con el mismo historial
   - En PWA debe dar X, en RN debe dar X (mismo ±0.01)
   - Si no hay parity, documentar como known gap

ENTREGABLE:
- recoveryService.ts de RN actualizado con nutrición y articular
- Documento de GAPs conocido (si hay irreducible differences)
```

### Paso 2.3: AUGE Adaptive Service - Paridad
**Prompt para IA - PARTE HUMANA:**

`augeAdaptiveService.ts` es crítico para el motor de adaptación automática. PWA tiene 16263 bytes, RN tiene solo 5200 bytes (un tercio). Esto significa:

- **PWA**: localStorage, 4 endpoints separados, cola de 5 arrays, GP curves completas, Banister model, priors, self-improvement
- **RN**: MMKV storage, 1 endpoint combinado, cache simplificado

**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE AUGE ADAPTIVE SERVICE

ARCHIVO FUENTE:
- services/augeAdaptiveService.ts (PWA: 16263 bytes, RN: 5200 bytes)

ARCHIVO DESTINO:
- apps/mobile/src/services/augeAdaptiveService.ts (SOBRESCRIBIR)

PROCESO:

1. ANALIZAR PWA:
   
   PWA tiene:
   - Storage: localStorage via safeStorage
   - Endpoints: 4 separados (/recovery/update, /fatigue/predict, /banister/auge, /self-improve)
   - Cache structure:
     * priors, totalObservations, personalizedRecoveryHours
     * confidenceIntervals, gpCurve, banister, selfImprovement, banisterHistory
   - 5 queues: recoveryObservations, fatigueDataPoints, predictions, outcomes, trainingImpulses
   - Funciones: mergeTrainingHistory(), getAdaptiveRecoveryHours(), getConfidenceLabel/Color()
   - Thresholds: 20/10/3 samples para alta/media/baja

2. ANALIZAR RN:
   
   RN tiene:
   - Storage: MMKV (más rápido, mobile-native)
   - Endpoint: 1 combinado (/auge/adaptive/sync)
   - Cache simplificado: totalObservations, modelAccuracy, cnsDelta, muscularDelta, 
     spinalDelta, muscleDeltas, personalizedRecoveryHours
   - NO GP curves, NO Banister completo, NO priors, NO self-improvement
   - Funciones: getAdaptiveSystemBiasCorrection(), getConfidenceLabel/Color()
   - Thresholds: 120/40 samples (DIFERENTES!)

3. MIGRACIÓN OPCIONES:

   OPCIÓN A (Completa): Migrar TODO de PWA a RN
   - Más trabajo, más features
   - GP curves, Banister, priors
   - Necesita verificar que backend soporta los 4 endpoints
   
   OPCIÓN B (Mínimo viable): Mantener RN simplificado
   - Ya tiene lo core: bias correction, confidence
   - Agregar solo los thresholds idénticos a PWA (20/10/3)
   - Documentar gaps

   RECOMENDACIÓN: OPCIÓN B para MVP, OPCIÓN A post-MVP

4. MIGRAR si se elige OPCIÓN A:

   a) Agregar GPFatiguePrediction type y exports
   b) Agregar BanisterSystemResult type y exports
   c) Agregar ModelAccuracy (MAE/RMSE/bias/r-squared)
   d) Agregar mergeTrainingHistory() function
   e) Agregar 4 endpoints separados (o verificar que backend los soporta)
   f) Cambiar thresholds de 120/40 a 20/10/3

5. VERIFICAR storage:
   - RN usa MMKV (bueno, más rápido que localStorage)
   - Mantener MMKV, solo agregar las keys que faltan

6. TEST:
   - Adaptive cache debe persistir entre sesiones
   - Recovery observations deben acumularse
   - Confidence label debe cambiar después de suficientes samples

ENTREGABLE:
--apps/mobile/src/services/augeAdaptiveService.ts actualizado
- Documento de qué features de PWA no están en RN (si se elige B)
```

### Paso 2.4: Volume Calculator y Volume Calibration
**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE VOLUME CALCULATOR Y CALIBRATION

ARCHIVO FUENTE:
- services/volumeCalculator.ts (PWA: 27631 bytes, RN: 19416 bytes)
- services/volumeCalibrationService.ts (PWA: 9753 bytes, RN: 7127 bytes)

ARCHIVO DESTINO:
- apps/mobile/src/services/volumeCalculator.ts (SOBRESCRIBIR)
- apps/mobile/src/services/volumeCalibrationService.ts (SOBRESCRIBIR)

PROCESO:

1. COMPARAR volumen de funciones:
   
   volumeCalculator.ts:
   - PWA tiene ~8KB más que RN
   - Buscar funciones en PWA que NO están en RN:
     * calculateEffectiveVolume()
     * calculateWeeklyVolumeDistribution()
     * getVolumeZoneStatus (MEV/MAV/MRV)
     * calculateFrequency()
     * calculateTonnage()
   - Si faltan, AGREGAR desde PWA

   volumeCalibrationService.ts:
   - PWA ~2.6KB más que RN
   - Buscar funciones faltantes
   - El servicio de calibración personaliza MEV/MRV basado en feedback

2. FUNCIONES CRÍTICAS para AUGE:
   - calculateEffectiveVolume(exerciseId, sets) → number
   - getVolumeZoneStatus(muscleId) → { zone: 'defficient'|'optimal'|'advanced', percentage: number }
   - calculateWeeklyTonnage(workoutHistory) → number
   - calibrateVolumeLimits(userFeedback, currentMEV, currentMRV) → { newMEV, newMRV }

3. VERIFICAR que usan initialMuscleGroupDatabase.ts:
   - El archivo de muscle groups tiene defaultRepRanges y recoveryHours
   - volumeCalculator depende de estos datos

4. TEST:
   - Crear programa con 3 sesiones
   - Verificar que getVolumeZoneStatus('pectoral') retorna valores coherentes
   - Verificar que calibrateVolumeLimits() actualiza settings

ENTREGABLE:
- volumeCalculator.ts con paridad
- volumeCalibrationService.ts con paridad
```

### Paso 2.5: Compute Worker Strategy
**Prompt para IA - PARTE TÉCNICA:**

```
ESTRATEGIA DE COMPUTE WORKER PARA RN

CONTEXTO:
- PWA: Web Worker real (workers/computeWorker.ts)
- RN: No hay worker, todo sync en main thread (wrapped in Promise.resolve())

ANALISIS:

Las 9 funciones del worker:
1. calculateMuscleBattery
2. calculateGlobalBatteries  
3. calculateSystemicFatigue
4. calculateDailyReadiness
5. calculatePredictedSessionDrain
6. calculateCompletedSessionStress
7. calculateACWR
8. calculateAverageVolumeForWeeks
9. calculateWeeklyTonnageComparison

PROBLEMA:
- En RN no existe Web Worker
- Todo corre en main thread
- Con >100 sesiones de historial, podría bloquear UI

OPCIONES:

A) Mantener como está (sync fallback):
   - computeWorkerService.ts ya tiene wrappers Async
   - Cuando worker no disponible, ejecuta sync
   - Usar InteractionManager.runAfterInteractions() para no bloquear navegación
   
B) Implementar con react-native-reanimated worklets:
   - NO RECOMENDADO: worklets son para animaciones, no cálculos pesados
   
C) Implementar con react-native-multithreading (JSI):
   - POSIBLE: permite JS en threads separados
   - EVALUAR estabilidad
   
D) Módulo nativo (Kotlin/Swift):
   - MÁXIMO rendimiento
   - MÁS trabajo

RECOMENDACIÓN MVP: OPCIÓN A

IMPLMENTACIÓN:

1. En computeWorkerService.ts de RN:
   ```typescript
   import { InteractionManager } from 'react-native';
   
   export async function calculateMuscleBatteryAsync(...): Promise<MuscleBatteryResult> {
     return new Promise((resolve) => {
       InteractionManager.runAfterInteractions(() => {
         // Only run after interactions complete
         const result = calculateMuscleBattery(...);
         resolve(result);
       });
     });
   }
   ```

2. Para cálculos grandes (>50 sesiones), mostrar skeleton/loading

3. PROFILING: Medir tiempo de ejecución en dispositivo real:
   - < 16ms = instantáneo, OK
   - 16-100ms = puede causar jank, considerar optimización
   - > 100ms = necesita async/loading

4. TEST:
   - Simular 200 sesiones de historial
   - Medir tiempo de calculateDailyReadiness()
   - Verificar que UI no crashea ni freezea

ENTREGABLE:
- Documento de estrategia elegida
- Si hay jank medido, ticket para optimizar post-MVP
```

---

## FASE 3: Migración de Stores y Persistencia
## Duración estimada: 4-6 horas

### Paso 3.1: Paridad de Stores
**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN/PARIDAD DE STORES

STORES A AUDITAR:
- stores/authStore.ts (PWA: 2654 bytes, RN: 1513 bytes) ⚠️ GAP
- stores/uiStore.ts (PWA: 19352 bytes, RN: 3452 bytes) ⚠️⚠️ CRÍTICO GAP

ANALISIS GAP:

uiStore PWA (19352 bytes) vs RN (3452 bytes):
- PWA tiene: modales activos, drawer states, secciones expandidas, tabs seleccionados,
  breadcrumb state, toast queue, action bars, contextual menus, scroll positions
- RN tiene: MUCHISIMO menos

DECISIÓN:
- UI state de PWA vive mucho en AppContext.tsx (86509 bytes!)
- RN tiene architecture diferente - menos UI state centralizado
- VERIFICAR si la funcionalidad de uiStore de PWA está dispersa en RN de otra forma

authStore PWA (2654 bytes) vs RN (1513 bytes):
- PWA: Supabase auth completo (register, login, reset, session refresh, OAuth)
- RN: ¿Tiene todos los flujos?
- VERIFICAR: authStore de RN tiene: signIn, signUp, signOut, resetPassword, refreshSession?

PROCESO:

1. LEER stores/uiStore.ts de PWA:
   - Listar TODOS los campos y acciones
   - Identificar cuáles son CRÍTICOS para funcionalidad (no solo UI state)
   
   Campos típicos de uiStore:
   - activeModal: string | null
   - activeDrawer: string | null
   - toasts: Toast[]
   - expandedSections: Record<string, boolean>
   - selectedTab: string
   - etc.

2. LEER stores/uiStore.ts de RN:
   - Comparar campos
   - Si hay campos CRÍTICOS en PWA que no están en RN, AGREGAR

3. LEER stores/authStore.ts de PWA:
   - Funciones: createUser, signIn, signOut, resetPassword, confirmEmail, refreshSession
   - Verificar si RN tiene paridad

4. ACTUALIZAR stores que tengan gaps:
   - uiStore: agregar campos faltantes si son críticos
   - authStore: agregar funciones faltantes

5. NO MIGRAR:
   - UI state puramente cosmético (ej: "¿estaba abierto el drawer?")
   - Esto es preferences del usuario, no estado de negocio

ENTREGABLE:
- stores actualizados con paridad
- Documento de qué UI state se omite (y por qué)
```

### Paso 3.2: Google Drive y Supabase Sync
**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE BACKUP Y SYNC

GAP CRÍTICO:

1. Google Drive:
   - PWA: services/googleDriveService.ts (5995 bytes) — implementación completa
   - RN: services/googleDriveService.ts (548 bytes) — STUB

2. Supabase Sync:
   - PWA: services/supabaseSyncService.ts (7804 bytes)
   - RN: services/supabaseSyncService.ts (1194 bytes) — 85% más chico

PROCESO:

1. GOOGLE DRIVE (Prioridad: ALTA para MVP)

   Funcionalidades de PWA:
   - backupToGoogleDrive(): Exporta todos los stores como JSON
   - restoreFromGoogleDrive(): Importa y migra datos
   - listBackups(): Muestra fechas de backups
   - deleteBackup()
   - detectConflicts()

   Implementación en RN:
   - Necesita @react-native-google-signin/google-signin
   - Necesita Google Drive API (REST)
   - Formato de backup debe ser COMPATIBLE con PWA (mismo JSON structure)

   MIGRACIÓN:
   - COPIAR funciones de PWA googleDriveService.ts a RN
   - Adaptar storage calls de localStorage a MMKV
   - Adaptar file upload/download de Browser API a fetch + Google Drive API
   - Mantener el mismo JSON schema para backup

   NOTA: Esta es una feature grande. Para MVP, podría ser MEDIUM.

2. SUPABASE SYNC (Prioridad: ALTA)

   PWA tiene:
   - syncWorkouts()
   - syncPrograms()
   - syncNutrition()
   - syncBody()
   - syncSettings()
   - syncAchievements()
   - Conflict resolution (last-write-wins o merge)
   - Offline queue con pending changes

   RN tiene 1194 bytes — casi nada.

   MIGRACIÓN:
   - COPIAR sync logic de PWA
   - Adaptar storage calls
   - IMPORTANTE: el sync debe funcionar bidireccionalmente
   - Un usuario que migra de PWA a RN debe poder synquear con sus datos existentes

3. TEST:
   - Crear backup en PWA
   - Restaurar en RN (debe funcionar)
   - Hacer cambios en RN
   - Sync con Supabase
   - Ver cambios en PWA (y viceversa)

ENTREGABLE:
- services/googleDriveService.ts de RN implementado (o stub documentado)
- services/supabaseSyncService.ts de RN con paridad
```

---

## FASE 4: Migración de Hooks y Utils
## Duración estimada: 4-6 horas

### Paso 4.1: Hooks Migración
**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE HOOKS

HOOKS PWA (8 archivos):
- hooks/useMuscleRecovery.ts — Crítico, calcula recovery de 11 grupos musculares
- hooks/useExerciseDatabase.ts — carga y filtra base de datos ejercicios
- hooks/useAchievements.ts — sistema de logros
- hooks/useSettings.ts + useSettingsStoreShallow.ts — wrappers de settings
- hooks/useGoogleDrive.ts — backup/restore
- hooks/useKeyboardOverlayMode.ts — gestión de teclado numérico
- hooks/useLocalStorage.ts — persistencia genérica

EN RN: Los hooks están integrados en los componentes/screens

PROCESO:

1. useMuscleRecovery.ts (PRIORIDAD MÁXIMA):
   
   PWA:
   - export function useMuscleRecovery() { ... }
   - Usa calculateMuscleBatteryAsync del computeWorker
   - Escucha workoutStore, wellbeingStore, settingsStore
   - Retorna: { muscleBatteries, globalBatteries, readiness, isLoading }
   - Tiene versionRef pattern para cancelar computaciones stale

   RN:
   - ¿Ya existe hook similar?
   - Si no, CREAR en apps/mobile/src/hooks/useMuscleRecovery.ts
   - Seguir el mismo patrón de PWA
   - Usar stores de RN

2. useExerciseDatabase.ts:
   
   PWA:
   - Filtra y busca exerciseDatabase
   - searchExercises(query), filterByMuscle(muscleId), filterByEquipment(equipment)
   
   RN:
   - exerciseStore ya tiene exercises[]
   - Posiblemente ya tiene selectors de Zustand
   - VERIFICAR antes de crear hook

3. useAchievements.ts:
   
   PWA:
   - data/achievements.ts (120 líneas)
   - check() functions por achievement
   - guarda unlocked achievements en localStorage
   
   RN:
   - NO existe achievements.ts en data/
   - NO existe hook de achievements
   - VERIFICAR si achievements screen tiene su propia lógica

   MIGRACIÓN:
   - Si achievements.ts no está migrado (Paso 1), migrarlo primero
   - Crear apps/mobile/src/data/achievements.ts
   - O crear apps/mobile/src/hooks/useAchievements.ts que viva en el store

4. useSettings.ts, useSettingsStoreShallow.ts:
   
   PWA:
   - Son convencience wrappers sobre settingsStore
   - useSettings() retorna todo el objeto settings
   - useSettingsStoreShallow() usa Zustand shallow comparison

   RN:
   - Posiblemente redundant - componentes pueden importar useSettingsStore directamente
   - VERIFICAR si hay lógica derivada (computed values) que falten

5. useGoogleDrive.ts:
   
   PWA: 4006 bytes
   RN: services/googleDriveService.ts es stub
   
   MIGRACIÓN:
   - Si se implementa Google Drive (Paso 3.2), el hook es simple wrapper
   - Por ahora OMITIR

6. useKeyboardOverlayMode.ts:
   
   PWA: Gestiona keyboard en modo overlay para inputs numéricos durante sesión
   RN: native KeyboardAvoidingView, react-native-keyboard-controller
   
   VERIFICAR: ¿La UX de input numérico durante sesión activa está cubierta?
   - Si sí, OMITIR hook
   - Si no, crear componente de NumPad overlay

7. useLocalStorage.ts:
   
   PWA: Hook genérico de persistencia key-value
   RN: Reemplazado por AsyncStorage + storageService
   
   OMITIR - no tiene sentido en RN

ENTREGABLE:
- apps/mobile/src/hooks/useMuscleRecovery.ts (nuevo)
- apps/mobile/src/hooks/useAchievements.ts (si falta)
- Documento de qué hooks se migran y cuáles se omiten
```

### Paso 4.2: Utils Migración
**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE UTILS

UTILS PWA (20 archivos):
- utils/programHelpers.ts — Crítico, funciones de programas
- utils/sessionDayLabel.ts — Genera labels inteligentes para sesiones
- utils/programEditorUtils.ts — Constantes de editor
- utils/calculations.ts — Cálculos generales (1RM, rep debt, etc.)
- utils/dateUtils.ts — Manipulación de fechas
- utils/exerciseIndex.ts — Indexación de ejercicios
- utils/canonicalMuscles.ts — Canonical muscle definitions
- utils/plateCalculator.ts — Cálculo de platos
- utils/calorieFormulas.ts — Fórmulas de macros
- utils/nutritionDescriptionParser.ts — Parseador de descripciones de comida
- utils/colorUtils.ts — Manipulación de colores M3
- utils/sessionArticularBatteries.ts — Baterías articulares para sesión
- utils/sessionMusclesForBattery.ts — Músculos para batería
- utils/inAppBrowser.ts — Apertura de URLs
- utils/theme.ts — Sistema de temas (OMITIR)
- utils/shapes.ts — Tokens de diseño (OMITIR)
- utils/typography.ts — Tipografía (OMITIR)

PROCESO:

1. programHelpers.ts (PRIORIDAD MÁXIMA):
   
   124 líneas con funciones críticas:
   - getAbsoluteWeekIndex(program, date) → number
   - generateSessionsForWeek(program, weekIndex) → Session[]
   - isProgramSimple(program) / isProgramComplex(program)
   - getRoadmapBlocks(program) → Block[]
   - getTotalWeeks(program) / countTrainingDays(program)
   - getDayName(dayIndex) / DAYS_LABELS
   
   MIGRACIÓN:
   - CREAR apps/mobile/src/utils/programHelpers.ts
   - COPIAR todas las funciones
   - Adaptar imports (ej: crypto.randomUUID → generateId de RN)
   - generateId.ts ya existe en RN

2. sessionDayLabel.ts (PRIORIDAD ALTA):
   
   81 líneas:
   - Genera label como "Día de pecho y espalda"
   - Analiza primary muscles de ejercicios de la sesión
   - muscleToGroup() mapping en español
   
   MIGRACIÓN:
   - CREAR apps/mobile/src/utils/sessionDayLabel.ts
   - COPIAR función
   - Verificar que muscle names coinciden con RN muscle database

3. sessionArticularBatteries.ts y sessionMusclesForBattery.ts:
   
   Funciones de soporte para AUGE
   - COPIAR a RN si no existen

4. calculations.ts, dateUtils.ts, exerciseIndex.ts, canonicalMuscles.ts:
   
   Posiblemente YA existen en RN
   - VERIFICAR antes de migrar
   - Si existen, comparar funciones exportadas

5. plateCalculator.ts:
   
   PWA: utils/plateCalculator.ts (USA)
   RN: apps/mobile/src/utils/plateCalculator.ts (EXISTE)
   
   COMPARAR: ¿Tienen las mismas funciones?
   - calculatePlates(totalWeight, barWeight, availablePlates) → number[]
   - ¿Compatible con métricas KG y disponibilidad en Chile?

6. calorieFormulas.ts:
   
   PWA: utils/calorieFormulas.ts (USA)
   RN: Posiblemente en nutritionPlanEngine
   
   VERIFICAR: ¿Las fórmulas de TDEE, macronutrientes están en RN?
   - Harris-Benedict o Mifflin-St Jeor
   - Ratios de macros según objetivo (cut/bulk/maintain)

7. nutritionDescriptionParser.ts:
   
   PWA: 3KB de parsing de texto libre a macros
   RN: apps/mobile/src/utils/nutritionDescriptionParser.ts
   
   COMPARAR: ¿Son idénticos?
   - Si RN tiene versión, no migrar
   - Si RN es diferente, sobeescribir con PWA

8. colorUtils.ts, inAppBrowser.ts:
   
   colorUtils: OMITIR si RN tiene useColors() hook
   inAppBrowser: CREAR usando Linking.openURL() de React Native

ENTREGABLE:
- apps/mobile/src/utils/programHelpers.ts (NUEVO)
- apps/mobile/src/utils/sessionDayLabel.ts (NUEVO)
-验证 que todos los utils críticos existen y funcionan
```

---

## FASE 5: Onboarding Completo
## Duración estimada: 8-12 horas

### Paso 5.1: Onboarding Wizard
**Prompt para IA - PARTE HUMANA:**

PWA tiene un módulo de onboarding completo con 12 archivos. RN tiene **0 archivos** de onboarding.

Esto es CRÍTICO para nuevos usuarios que nunca han usado la PWA.

**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE ONBOARDING WIZARD

ARCHIVOS FUENTE (PWA):
components/onboarding/
- UnifiedWelcomeWizard.tsx — Orquestador principal
- GeneralOnboardingWizard.tsx — Wizard general
- WelcomeWizard.tsx — Versión base
- AnimatedSvgBackground.tsx — Background animado
- steps/AthleteTypeStep.tsx — Tipo de atleta
- steps/PhysicalDataStep.tsx — Datos físicos (peso, altura, edad, sexo)
- steps/ProgramNameStep.tsx — Nombre del programa
- steps/SplitStep.tsx — Selección de split
- steps/VolumeStep.tsx — Configuración de volumen
- steps/RecentWorkoutsStep.tsx — Importar entrenamientos recientes
- steps/BatteryRingsStep.tsx — Introducción a AUGE
- steps/BatteryPrecalibrationStep.tsx — Precalibración de baterías

ARCHIVO DESTINO:
- apps/mobile/src/screens/Onboarding/ (NUEVO directorio)
- apps/mobile/src/components/onboarding/ (NUEVO directorio)

PROCESO:

1. ANALIZAR cada step:

   AthleteTypeStep:
   - Opciones: principiante, intermedio, avanzado, powerlifter, bodybuilder
   - Guarda en settingsStore: athleteType
   - Afecta cálculos AUGE (resholds de fatigue)

   PhysicalDataStep:
   - Inputs: peso, altura, edad, sexo, nivel de actividad
   - Guarda en bodyStore: peso/altura/edad/sexo iniciales
   - Calcula TDEE inicial

   ProgramNameStep:
   - Input: nombre del programa
   - Validación: no vacío, max 50 caracteres

   SplitStep:
   - Opciones: Push/Pull/Legs, Bro Split, Upper/Lower, Full Body, Custom
   - Guarda en programStore: splitTemplate

   VolumeStep:
   - Sliders: MEV target, MRV target
   - Guarda en settingsStore: volumeCalibration

   RecentWorkoutsStep:
   - Opción de importar historial de PWA (si existe)
   - O usar datos de ejemplo

   BatteryRingsStep:
   - Introducción visual al sistema de anillos AUGE
   - Usa componentes de BatteryHero de RN
   - Explicación de readiness, energy, stress

   BatteryPrecalibrationStep:
   - Pide al usuario que califique su nivel de recuperación percibida
   - Ajusta las batteries iniciales según respuesta
   - USA calculatePrecalibration() del recovery service

2. IMPLEMENTACIÓN en RN:

   a) Crear OnboardingStack en React Navigation:
      ```typescript
      const OnboardingStack = createNativeStackNavigator();
      
      <OnboardingStack.Navigator>
        <OnboardingStack.Screen name="Welcome" component={WelcomeScreen} />
        <OnboardingStack.Screen name="AthleteType" component={AthleteTypeScreen} />
        <OnboardingStack.Screen name="PhysicalData" component={PhysicalDataScreen} />
        <OnboardingStack.Screen name="ProgramName" component={ProgramNameScreen} />
        <OnboardingStack.Screen name="Split" component={SplitScreen} />
        <OnboardingStack.Screen name="Volume" component={VolumeScreen} />
        <OnboardingStack.Screen name="RecentWorkouts" component={RecentWorkoutsScreen} />
        <OnboardingStack.Screen name="BatteryRings" component={BatteryRingsScreen} />
        <OnboardingStack.Screen name="BatteryPrecalibration" component={BatteryPrecalibrationScreen} />
      </OnboardingStack.Navigator>
      ```

   b) Persistencia:
      - Cada step guarda en el store correspondiente
      - Al final, settingsStore.onboardingCompleted = true

   c) Animaciones:
      - Usar react-native-reanimated
      - NO usar Animated API legacy
      - Background: usar LinearGradient o react-native-svg

   d) Validaciones:
      -edad: 14-100
      - peso: 30-300 kg
      - altura: 100-250 cm
      - sex: 'male' | 'female' | 'other'

3. NAVEGACIÓN:
   - OnboardingStack solo accesible si !settingsStore.onboardingCompleted
   - Después de completar, redirigir a MainTabs
   - Botón "Saltar" en cada step (opcional)

4. TEST:
   - Crear cuenta nueva
   - Pasar por todos los steps
   - Verificar que settingsStore tiene los valores correctos
   - Verificar que AUGE batteries dan valores coherentes con los datos

ENTREGABLE:
- apps/mobile/src/screens/Onboarding/*.tsx (9 screens)
- apps/mobile/src/components/onboarding/*.tsx (componentes internos)
- OnboardingStack configurado en navigation
```

---

## FASE 6: Módulos de UI Faltantes
## Duración estimada: 16-24 horas

### Paso 6.1: Session Editor Paridad
**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE SESSION EDITOR

PWA components/session-editor/ (16 archivos):
- SessionEditor.tsx — Editor principal
- DrawerSystem.tsx, AugeDrawer.tsx, AugeBottomSheet.tsx, AugeFAB.tsx — Sistema de drawers AUGE
- ExerciseCardCompact.tsx, ExerciseRow.tsx — Tarjetas de ejercicio
- InlineSetTable.tsx, SetCardGrid.tsx — Grillas de sets
- SessionEditorHeader.tsx, ContextualHeader.tsx — Headers
- FatigueIndicators.tsx, SessionMetricsBlock.tsx — Indicadores de fatiga
- SessionWeekRoadmap.tsx — Roadmap semanal
- PartSection.tsx — Secciones (warm-up, main, etc.)
- SwipeDeleteHintModal.tsx — Hint de swipe

RN tiene:
- SessionEditorScreen.tsx
- sessionEditorMutations.ts

PROCESO:

1. VERIFICAR qué funcionalidades de los 16 archivos PWA están en RN:

   SessionEditorScreen.tsx de RN:
   - ¿Tiene drawer de AUGE?
   - ¿Tiene indicadores de fatiga en tiempo real?
   - ¿Tiene roadmap de semana?
   - ¿Tiene sistema de sets completo?

2. GAPS DETECTADOS (basado en análisis previo):
   - AugeDrawer.tsx — ¿existe en RN?
   - FatigueIndicators.tsx — ¿existe en RN?
   - SessionWeekRoadmap.tsx — ¿existe en RN?

3. PARA CADA GAP:

   a) AugeDrawer / AugeBottomSheet:
      - RN tiene ReadinessBottomSheet.tsx?
      - ¿Muestra las batteries durante la sesión?
      - Si no, CREAR basado en PWA

   b) FatigueIndicators:
      - Muestra fatiga actual del músculo que se está ejercitando
      - Basado en calculateMuscleBattery() 
      - CREAR si no existe

   c) SessionWeekRoadmap:
      - Muestra las sesiones de la semana actual
      - Resalta la sesión actual
      - CREAR basado en PWA

   d) SetCardGrid / InlineSetTable:
      - RN tiene ejercicioInputState para manejar sets
      - VERIFICAR que la UI de sets es completa
      - Comparar con PWA SessionEditor

4. IMPLEMENTAR gaps en RN:
   - Usar Skia para gráficos de fatiga (como hace RN)
   - Usar Native StyleSheet (no className)
   - Usar react-native-reanimated para animaciones

ENTREGABLE:
- SessionEditorScreen.tsx con paridad completa
- Componentes faltantes creados
```

### Paso 6.2: Analytics y Progress Components
**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE COMPONENTES DE ANALYTICS

GAPS DETECTADOS (componentes PWA sin equivalente en RN):

1. PowerliftingDashboard.tsx — Wilks/DOTS/IPF GL calculators
   - RN tiene?: ¿IPFPointsCard? ¿RelativeStrengthAndBasicsWidget?
   - Si no, CREAR

2. CorrelationDashboard.tsx — Correlaciones entre variables
   - Análisis de correlación: volumen vs strength, sueño vs recovery
   - RN NO tiene
   - CREAR o DOCUMENTAR como post-MVP

3. EffectiveVolumeCard.tsx — Volumen efectivo por ejercicio
   - Basado en: sets × reps × intensity
   - RN tiene VolumeBudgetWidget?
   - CREAR basado en PWA

4. PersonalRecordsChart.tsx — PRs con progresión
   - RN tiene PersonalRecordsScreen
   - ¿Tiene charts de PRs?
   - CREAR componente si falta

5. TrendCard.tsx — Tendencias con regresión lineal
   - Muestra si estás mejorando o no
   - RN tiene en ProgressScreen?
   - CREAR si falta

6. VolumeBudgetWidget.tsx — Presupuesto de volumen semanal
   - Muestra volumen usado vs disponible
   - RN tiene en SessionEditor o Home?
   - CREAR si falta

7. FFMIChart.tsx, BodyFatChart.tsx, BodyWeightChart.tsx
   - Gráficos de composición corporal
   - RN tiene BodyProgressScreen
   - ¿Tiene estos charts?
   - CREAR si faltan

8. CalorieHistoryChart.tsx
   - Historial de calorías
   - RN tiene NutritionDashboard
   - CREAR si falta

PARA CADA COMPONENTE:

1. LEER el archivo PWA
2. IDENTIFICAR la lógica de cálculo (si tiene)
3. IMPLEMENTAR en RN con:
   - Skia para charts
   - Native StyleSheet
   - react-native-reanimated
4. TEST con datos reales

ENTREGABLE:
- Componentes de analytics implementados en RN
- Documento de qué se omite (post-MVP)
```

---

## FASE 7: AI Services - Cierre de Gaps
## Duración estimada: 8-12 horas

### Paso 7.1: AI Service - Multi-Provider Paridad
**Prompt para IA - PARTE TÉCNICA:**

```
MIGRACIÓN DE AI SERVICES - CIERRE DE GAPS

GAP CRÍTICO: RN tiene ~15% de paridad con PWA en AI

SITUACIÓN:
- PWA aiService.ts: 25253 bytes, 50+ funciones
- RN aiService.ts: 10290 bytes, ~7 funciones
- RN tiene stubs para image generation que lanzan errores

PROCESO:

1. ANALIZAR funciones PWA que NO están en RN:

   AI Functions (PWA → RN status):
   - generateWeeklyProgressAnalysis → ❌ NO
   - generatePerformanceAnalysis → ❌ NO  
   - generateBodyLabAnalysis → ❌ NO
   - generateBiomechanicalAnalysis → ❌ NO
   - generateCarpeDiemWeeklyPlan → ❌ NO
   - analyzeNutritionPlanDocument → ❌ NO
   - generateMobilityRoutine → ❌ NO
   - getAICoachAnalysis → ❌ NO
   - getAICoachInsights → ❌ NO
   - generateImage → ❌ STUB (bloqueante)
   - generateImages → ❌ STUB (bloqueante)

2. DECISIÓN ESTRATÉGICA:

   OPCIÓN A: Migrar TODO (6-8 horas de trabajo)
   - Implementar todas las funciones AI en RN
   - Usar backend proxy para llamadas reales
   - Requiere que backend tenga endpoints para cada función

   OPCIÓN B: Migrar CRÍTICOS SOLO (2-4 horas)
   - Coach Chat (el más usado)
   - Image Generation (aunque sea placeholder)
   - Dejar el resto como stubs documentados

   RECOMENDACIÓN: OPCIÓN B para MVP

3. IMPLEMENTACIÓN CRÍTICOS:

   a) Coach Chat en RN:
      
      PWA: Streaming AI con LLM (getCoachChatResponseStream)
      RN: Reglas keyword matching (coachChatService.ts)
      
      MEJORA: En vez de solo reglas, usar backend proxy:
      - Crear endpoint en backend para /coach/chat
      - RN llama a backendAIService → backend → LLM
      - Mantener fallback de reglas si backend no disponible

   b) Image Generation:
      
      RN tiene stubs que lanzan error bloqueante:
      ```typescript
      throw new Error("Generación de imagen no disponible en móvil...")
      ```
      
      OPCIONES:
      - Mantener stub y mostrar mensaje "Próximamente"
      - Implementar usando backend proxy (subir imagen → recibir imagen)
      
      RECOMENDACIÓN: Mantener stub con mensaje claro, implementar post-MVP

4. BACKEND PROXY:

   Si se elige migrar más funciones AI, verificar que backend tiene endpoints:
   - /ai/generateProgressAnalysis
   - /ai/generatePerformance
   - /ai/coach/chat
   - etc.

   Si no existen, crear o documentar como requirement

5. TEST:
   - Llamar generateCoachReply() y verificar que responde
   - Llamar generateImage() y verificar que muestra mensaje "Próximamente" (no crash)

ENTREGABLE:
- Documento de estado de AI en RN (post-migration)
- Lista de funciones AI disponibles vs stubs
```

---

## FASE 8: Testing y Paridad Validation
## Duración estimada: 6-8 horas

### Paso 8.1: Test Suite de Paridad
**Prompt para IA - PARTE TÉCNICA:**

```
CREACIÓN DE TEST SUITE DE PARIDAD

OBJETIVO: Verificar que PWA y RN producen los mismos resultados

TESTS A CREAR:

1. AUGE Parity Test:

   ```typescript
   // Test: calculateDailyReadiness produce mismo score en PWA y RN
   // Ubicación: packages/shared-domain/tests/augeParity.test.ts
   
   const FIXTURE_WORKOUT_HISTORY = [...]; // 50 sesiones de ejemplo
   const FIXTURE_SLEEP_LOGS = [...];
   const FIXTURE_WELLBEING = { ... };
   
   test('calculateDailyReadiness PWA vs RN', () => {
     // En PWA: calculateDailyReadiness(...)
     // En RN: calculateDailyReadiness(...)
     // Ambos deben dar ±0.01
   });
   
   test('calculateMuscleBattery("pectoral") PWA vs RN', () => {
     // Ambos deben dar mismo valor
   });
   ```

2. Volume Parity Test:

   ```typescript
   // Test: Volume calculator produce mismas métricas
   test('getVolumeZoneStatus("pectoral") PWA vs RN', () => {
     // Ambos deben retornar { zone, percentage }
   });
   ```

3. Nutrition Parity Test:

   ```typescript
   // Test: Parser de nutrición produce mismos macros
   test('parseFreeFormNutrition("2 huevos con arroz") PWA vs RN', () => {
     // Ambos deben dar { calories, protein, carbs, fat } ±5%
   });
   ```

4. Program Structure Test:

   ```typescript
   // Test: Generar programa produce misma estructura
   test('generateProgram() PWA vs RN', () => {
     // Ambos deben generar JSON con misma estructura
   });
   ```

EJECUCIÓN:

```bash
# Tests unitarios existentes
npm run test:nutrition-logging    # PWA nutrition
npm run mobile:test               # RN Jest

# Tests de paridad (por crear)
npm run test:parity               # Nuevo script
```

ENTREGABLE:
- packages/shared-domain/tests/augeParity.test.ts
- apps/mobile/__tests__/parityTests.ts
- Script npm run test:parity
```

---

## FASE 9: Verificación Final y Cutover
## Duración estimada: 4-6 horas

### Paso 9.1: Checklist Final
**Prompt para IA:**

```
CHECKLIST FINAL DE VERIFICACIÓN

EJECUTAR en orden:

1. TYPECHECK:
   npm run typecheck              # PWA
   cd apps/mobile && npm run typecheck  # RN

2. TESTS:
   npm run test                   # PWA
   cd apps/mobile && npm run test # RN
   npm run test:parity             # Paridad (si se creó)

3. BUILD:
   cd apps/mobile/android && ./gradlew assembleDebug
   # APK debe generarse sin errores

4. COLD START TEST:
   - Instalar APK en dispositivo
   - Abrir app desde frío
   - No debe crashear
   - Verificar onboarding aparece (si es usuario nuevo)

5. SMOKE TEST (usuario nuevo):
   - Completar onboarding
   - Crear programa simple (Full Body, 3 días)
   - Iniciar sesión de workout
   - Registrar 3 ejercicios con sets
   - Finalizar sesión
   - Ver resumen de fatiga AUGE
   - Verificar que las batteries muestran valores

6. SMOKE TEST (usuario existente):
   - Si hay datos migrados de PWA, importar
   - Verificar que workouts aparecen
   - Verificar que AUGE scores son correctos

7. NUTRITION SMOKE TEST:
   - Abrir Nutrition
   - Buscar "pollo"
   - Log 100g de pollo
   - Ver macros
   - Verificar que el parser de texto libre funciona

8. SETTINGS TEST:
   - Cambiar unidades (kg/lb)
   - Cambiar provider AI
   - Toggle notifications
   - Verificar que persisten

9. BACKUP TEST:
   - Crear backup manual
   - Desinstalar app
   - Reinstalar
   - Restaurar backup
   - Verificar datos intactos

10. PERFORMANCE:
    - Navegar entre 10+ screens
    - No debe haber jank notable
    - No memory leaks después de 10 min de uso

ENTREGABLE:
- Reporte final con PASS/FAIL por item
- Si hay FAILs, tickets creados para cada uno
```

---

# PARTE IV: BASE DE DATOS ESPECÍFICAS

## 4.1 Sistema AUGE - Detalle Completo

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                         SISTEMA AUGE - MIGRACIÓN COMPLETA                     ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  Los siguientes archivos constituyen el sistema AUGE y deben migrarse JUNTOS: ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                                                                              ║
║  SERVICES:                                                                   ║
║  ├── services/auge.ts (HUB central)                                          ║
║  ├── services/fatigueService.ts                                              ║
║  ├── services/recoveryService.ts ⚠️ CRÍTICO                                  ║
║  ├── services/augeAdaptiveService.ts ⚠️ CRÍTICO                              ║
║  ├── services/volumeCalculator.ts ⚠️ CRÍTICO                                ║
║  ├── services/volumeCalibrationService.ts                                    ║
║  ├── services/analysisService.ts                                             ║
║  ├── services/ttcService.ts (Time to compete)                                ║
║  ├── services/tendonRecoveryService.ts                                      ║
║  ├── services/tendonAlertsService.ts                                        ║
║  ├── services/structuralReadinessService.ts                                ║
║  ├── services/computeWorkerService.ts (adaptar worker → sync)                ║
║  └── workers/computeWorker.ts → MIGRAR LÓGICA, no worker mismo              ║
║                                                                              ║
║  DATA:                                                                      ║
║  ├── data/initialMuscleGroupDatabase.ts ⚠️ CRÍTICO                          ║
║  ├── data/initialMuscleHierarchy.ts ⚠️ CRÍTICO                              ║
║  ├── data/muscleGroupDatabase.ts                                            ║
║  ├── data/muscleHierarchy.ts                                                ║
║  ├── data/articularBatteryConfig.ts ⚠️ CRÍTICO                              ║
║  ├── data/jointDatabase.ts                                                  ║
║  ├── data/tendonDatabase.ts                                                 ║
║  ├── data/inferMusclesFromName.ts ⚠️ CRÍTICO                               ║
║  ├── data/discomfortList.ts                                                 ║
║  └── data/movementPatternDatabase.ts                                         ║
║                                                                              ║
║  STORES:                                                                    ║
║  ├── stores/workoutStore.ts (history para AUGE)                             ║
║  ├── stores/settingsStore.ts (calibration data)                            ║
║  └── stores/exerciseStore.ts (exercise database)                            ║
║                                                                              ║
║  SHARED-DOMAIN:                                                              ║
║  └── packages/shared-domain/src/auge/ (TODO: verificar paridad)              ║
║      ├── fatigue.ts                                                         ║
║      ├── recovery.ts                                                        ║
║      ├── ttc.ts                                                             ║
║      ├── tendonRecovery.ts                                                  ║
║      └── tendonAlerts.ts                                                    ║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  NOTAS:                                                                      ║
║  • fatigueService y recoveryService re-exportan de shared-domain             ║
║  • La mayoría de lógica CORE está en shared-domain (platform-neutral)        ║
║  • Los services PWA/RN son wrappers con adaptaciones de storage/UI          ║
║  • CRÍTICO: Verificar que las CONSTANTES son idénticas                      ║
║    - Recovery half-lives (horas por músculo)                                 ║
║    - Fatigue multipliers por tipo de ejercicio                              ║
║    - Thresholds de zonas (verde/amarillo/rojo)                              ║
║    - ACM/mF factores                                                        ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

## 4.2 Base de Datos de Alimentos

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                    BASE DE DATOS DE ALIMENTOS - MIGRACIÓN                    ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                                                                              ║
║  CHILE + UNIVERSAL:                                                         ║
║  ├── data/localChileanFoods.ts (FOODS CHILENOS) ⚠️ CRÍTICO                   ║
║  │   └── packages/shared-domain/src/nutrition/localChileanFoods.ts          ║
║  ├── data/foodDatabase.ts (247+ alimentos universales) ⚠️ CRÍTICO           ║
║  ├── data/foodDatabaseExpansion.ts                                          ║
║  ├── data/foodSynonyms.ts ⚠️ CRÍTICO                                        ║
║  ├── data/foodTaxonomy.ts                                                   ║
║  └── data/portionReferences.ts                                              ║
║                                                                              ║
║  LARGE DATA (BAJO DEMANDA - NO MIGRAR AHORA):                               ║
║  ├── data/openFoodFactsOffline.json (16.7MB) — descargar bajo demanda       ║
║  ├── data/usdaFoodsOffline.json (6.9MB) — descargar bajo demanda             ║
║  └── data/usdaFoundationFoods.json (6.8MB) — descargar bajo demanda         ║
║                                                                              ║
║  SERVICES:                                                                  ║
║  ├── services/aiNutritionParser.ts ⚠️ CRÍTICO                              ║
║  ├── services/localAiService.ts                                             ║
║  ├── services/foodIndexService.ts                                           ║
║  ├── services/nutritionPlanEngine.ts                                       ║
║  └── packages/shared-domain/src/nutrition/                                  ║
║      ├── localChileanFoods.ts                                               ║
║      ├── heuristicFoodCatalog.ts                                            ║
║      └── analyzeDescription.ts                                              ║
║                                                                              ║
║  AI ON-DEVICE (Android):                                                    ║
║  └── services/localAiService.ts (FunctionGemma 270M)                        ║
║      ├── parseFreeFormNutrition()                                           ║
║      └── fallback heuristics                                                 ║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  NOTAS:                                                                      ║
║  • Food database de PWA y RN tienen ESTRUCTURA DIFERENTE                    ║
║  • PWA usa tags[] y aliases[], RN usa category string                      ║
║  • DECISIÓN: Mantener estructura PWA (más rica)                              ║
║  • localChileanFoods debe ser IDENTICO en ambas ubicaciones                 ║
║  • El parser usa synonyms para fuzzy matching "huevos" → "egg"              ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

## 4.3 Base de Datos de Ejercicios (WikiLab)

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                   BASE DE DATOS DE EJERCICIOS - WIKILAB                      ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                                                                              ║
║  EJERCICIOS COMPLETOS (575KB+) ⚠️ CRÍTICO:                                  ║
║  ├── data/exerciseDatabase.ts (77KB)                                        ║
║  ├── data/exerciseDatabaseCentral.ts (157KB)                                ║
║  ├── data/exerciseDatabaseExpansion.ts (89KB)                               ║
║  ├── data/exerciseDatabaseExpansion2.ts (32KB)                             ║
║  ├── data/exerciseDatabaseExpansion3.ts (238KB)                            ║
║  ├── data/exerciseDatabaseExtended.json (196KB)                            ║
║  └── data/exerciseList.ts (970B)                                           ║
║                                                                              ║
║  INFERENCIA:                                                                 ║
║  └── data/inferMusclesFromName.ts (17KB) ⚠️ CRÍTICO                         ║
║      └── Algoritmo determinista para inferir músculos desde nombre           ║
║                                                                              ║
║  ANATOMÍA:                                                                   ║
║  ├── data/initialMuscleHierarchy.ts (2KB) ⚠️ CRÍTICO                        ║
║  ├── data/muscleGroupDatabase.ts (165B re-export)                            ║
║  ├── data/initialMuscleGroupDatabase.ts (37KB) ⚠️ CRÍTICO                  ║
║  ├── data/jointDatabase.ts (14KB)                                           ║
║  ├── data/tendonDatabase.ts (8KB)                                           ║
║  └── data/movementPatternDatabase.ts (7KB)                                 ║
║                                                                              ║
║  SERVICES:                                                                  ║
║  ├── stores/exerciseStore.ts (USA exerciseDatabase)                         ║
║  ├── services/fatigueService.ts (USA muscle data)                          ║
║  ├── services/recoveryService.ts (USA muscle data)                          ║
║  └── services/analysisService.ts                                            ║
║                                                                              ║
║  NAVEGACIÓN WIKI:                                                           ║
║  └── WikiStack:                                                             ║
║      ├── WikiHomeScreen.tsx                                                 ║
║      ├── WikiMuscleDetailScreen.tsx                                        ║
║      ├── WikiJointDetailScreen.tsx                                         ║
║      ├── WikiTendonDetailScreen.tsx                                       ║
║      ├── WikiPatternDetailScreen.tsx                                       ║
║      ├── WikiBiomechanicsScreen.tsx                                        ║
║      └── WikiMobilityScreen.tsx                                            ║
║                                                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  NOTAS:                                                                      ║
║  • RN NO tiene estos archivos — esgap CRÍTICO                               ║
║  • Sin database de ejercicios, AUGE no puede funcionar                      ║
║  • MIGRACIÓN OBLIGATORIA antes de testing AUGE                              ║
║  • Consolidar en 2-3 archivos para RN (no 7 como PWA)                      ║
║  • Verificar tipos en @kpkn/shared-types (Exercise, MuscleId, etc.)         ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

---

# PARTE V: ORDEN DE EJECUCIÓN RECOMENDADO

```
FASE 0: Cleanup y Baseline (2-4h)
│
├── Paso 0.1: Eliminar código muerto
├── Paso 0.2: Setup y verificación inicial
│
FASE 1: Bases de Datos (6-10h) ⚠️ FUNDAMENTO
│
├── Paso 1.1: Ejercicios COMPLETOS ⚠️ CRÍTICO
├── Paso 1.2: Jerarquía Muscular
├── Paso 1.3: Joint/Tendon Databases
├── Paso 1.4: Alimentos + Chile ⚠️ CRÍTICO
└── Paso 1.5: Movement Patterns y otros
│
FASE 2: Lógica AUGE (10-16h) ⚠️ CORAZÓN
│
├── Paso 2.1: AUGE Service Paridad
├── Paso 2.2: Recovery + Nutrición ⚠️ CRÍTICO
├── Paso 2.3: Adaptive Service
├── Paso 2.4: Volume Calculator
└── Paso 2.5: Compute Worker Strategy
│
FASE 3: Stores y Persistencia (4-6h)
│
├── Paso 3.1: Stores Paridad
└── Paso 3.2: Google Drive + Supabase Sync ⚠️ CRÍTICO
│
FASE 4: Hooks y Utils (4-6h)
│
├── Paso 4.1: Hooks Migration
└── Paso 4.2: Utils Migration
│
FASE 5: Onboarding (8-12h) ⚠️ CRÍTICO PARA NUEVOS USUARIOS
│
└── Paso 5.1: Onboarding Wizard Completo
│
FASE 6: UI Faltantes (16-24h)
│
├── Paso 6.1: Session Editor Paridad
└── Paso 6.2: Analytics Components
│
FASE 7: AI Services (8-12h)
│
└── Paso 7.1: AI Multi-Provider Paridad
│
FASE 8: Testing (6-8h)
│
└── Paso 8.1: Test Suite de Paridad
│
FASE 9: Verificación Final (4-6h)
│
└── Paso 9.1: Checklist Final + Cutover
│
TOTAL ESTIMADO: 70-100 horas
```

---

# PARTE VI: CRITERIOS DE ACEPTACIÓN MVP

## Must Have (Bloquea Release)
- [ ] exerciseDatabase.ts migrado con 800+ ejercicios
- [ ] muscleGroupDatabase.ts con 40+ grupos musculares
- [ ] AUGE batteries producen resultados par averaging (±0.01 vs PWA)
- [ ] Onboarding completo funcional
- [ ] Session Editor con fatiga en tiempo real
- [ ] Nutrition logging con parser de texto libre
- [ ] Supabase sync bidireccional
- [ ] No crashes en cold start
- [ ] typecheck limpio en RN

## Should Have (Post-MVP)
- [ ] Google Drive backup/restore
- [ ] AI Coach Chat con streaming (no reglas)
- [ ] Image generation funcional
- [ ] Correlation analytics
- [ ] Progress photos
- [ ] Video analysis

## Nice to Have (Backlog)
- [ ] Social feed completo
- [ ] Voice assistant
- [ ] Apple Watch app
- [ ] Apple Health integration

---

# PARTE VII: RIESGOS Y MITIGACIONES

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|-------------|---------|------------|
| Base de datos ejercicios muy grande para RN | Alta | Alto | Consolidar en archivos menores, lazy load si necesario |
| Paridad AUGE imposible sin datos de historial | Media | Alto | Crear fixture data para testing, migrar PWA data primero |
| Performance con compute worker | Media | Medio | Profile early, InteractionManager como fallback |
| Backend no soporta todos los endpoints AI | Alta | Medio | Implementar stubs con mensaje claro, roadmap para backend |
| Onboarding muy lento de implementar | Media | Medio | Reducir a pasos mínimos viables, resto post-MVP |

---

# PARTE VIII: DEFINICIONES Y ACRÓNIMOS

| Término | Definición |
|---------|------------|
| AUGE | Sistema de baterías de fatiga, recovery y readiness. El "cerebro" de la app. |
| MEV | Minimum Effective Volume — volumen mínimo para stimular gains |
| MAV | Maximum Adaptive Volume — volumen óptimo antes de estancamiento |
| MRV | Maximum Recoverable Volume — volumen máximo que puedes recuperar |
| ACWR | Acute:Chronic Workload Ratio — ratio de volumen agudo vs crónico |
| RPE | Rate of Perceived Exertion — percepción de esfuerzo (1-10) |
| RIR | Reps in Reserve — repeticiones en reserva |
| CNS | Central Nervous System — fatigue sistémica |
| GP | Gaussian Process — para predicción de fatiga |
| TDEE | Total Daily Energy Expenditure — gasto calórico diario |
| FFMI | Fat-Free Mass Index — índice de masa libre de grasa |

---

**Documento generado**: 2026-03-20  
**Versión**: 1.0-PRO  
**Autores**: Plan Maestro basado en auditoría Gemini 4.6, enriquecido con análisis detallado  
**Propósito**: Guía técnica completa para IA agentes ejecutores de la migración
