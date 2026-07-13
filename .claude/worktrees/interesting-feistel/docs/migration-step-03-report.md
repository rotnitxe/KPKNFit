# Paso 3 - Paridad de logica, matematicas y servicios

Fecha: 2026-03-18

## Veredicto

- Estado: aprobado con reservas
- Objetivo cumplido: ya existe un mapa operativo de la capa de logica, matematicas y servicios PWA -> RN
- Objetivo no cumplido: todavia no hay prueba de paridad 1:1 total ni harness automatizado suficiente

## Lectura ejecutiva

La migracion RN ya tiene bastante mas logica real de la que parecia al principio, sobre todo en:

- calculos base
- AUGE/recovery/fatigue
- volumen y calibracion
- nutrition plan
- migration/hydration
- contracts compartidos

Pero todavia hay tres brechas estructurales fuertes:

1. RN no tiene un `aiService.ts` unico que replique la orquestacion PWA
2. RN no tiene equivalente directo de `computeWorkerService.ts` / `workers/computeWorker.ts`
3. servicios ricos como `foodIndexService.ts` y `aiNutritionParser.ts` siguen muy por debajo del PWA o fragmentados

## Lo que confirme como bastante fuerte

### Calculos y motores con buena base RN

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\utils\calculations.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\utils\calculations.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\analysisService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\analysisService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\alchemyEngine.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\alchemyEngine.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\loopEngine.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\loopEngine.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\nutritionPlanEngine.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\nutritionPlanEngine.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\volumeCalculator.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\volumeCalculator.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\volumeCalibrationService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\volumeCalibrationService.ts`

Lectura:

- hay version RN real de casi todos los motores matematicos clave
- en varios casos la diferencia ya no es "no existe", sino "hay que probar paridad exacta"

### AUGE / fatigue / recovery bien encaminado

- PWA:
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\auge.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\augeAdaptiveService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\fatigueService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\recoveryService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\structuralReadinessService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\tendonRecoveryService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\tendonAlertsService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\ttcService.ts`
- RN:
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\auge.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\augeRuntimeService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\fatigueService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\recoveryService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\structuralReadinessService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\tendonRecoveryService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\tendonAlertsService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\ttcService.ts`

Ademas RN ya consume piezas canonicas desde shared-domain/shared-types:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\packages\shared-domain\src\auge\fatigue.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\packages\shared-domain\src\auge\recovery.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\packages\shared-domain\src\auge\structuralReadiness.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\packages\shared-domain\src\auge\tendonRecovery.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\packages\shared-domain\src\auge\tendonAlerts.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\packages\shared-domain\src\auge\ttc.ts`

Lectura:

- este bloque esta mucho mas sano que otros
- el problema aqui ya no es ausencia total, sino terminar de probar identidad numerica

## Lo que queda claramente parcial

### 1. Capa AI general fragmentada

PWA:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\aiService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\backendAIService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\geminiService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\gptService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\deepseekService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\localAiService.ts`

RN:

- no existe `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\aiService.ts`
- no existe `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\backendAIService.ts`
- si existen piezas separadas:
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\coachChatService.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\modules\localAi.ts`
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\nutritionAnalyzer.ts`

Decision:

- esto no es 1:1
- es una fragmentacion funcional parcial

### 2. Worker de calculo sin equivalente RN claro

PWA:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\workers\computeWorker.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\computeWorkerService.ts`

RN:

- no existe `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\computeWorkerService.ts`
- el reemplazo mas cercano es:
  - `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\augeRuntimeService.ts`

Decision:

- no cuenta como paridad 1:1
- RN tiene runtime y servicios de calculo, pero no wrapper worker-backed equivalente

### 3. Food index muy por debajo de la PWA

PWA:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\foodIndexService.ts`
- 1136 lineas
- 8 exports
- incluye:
  - ranking mas rico
  - memoria de resoluciones
  - caches
  - OFF/USDA offline packs
  - estado de packs
  - resolucion de descripciones

RN:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\foodIndexService.ts`
- 127 lineas
- 9 exports
- implementa busqueda simplificada y favoritos/recientes en memoria

Decision:

- existe, pero la paridad real es parcial
- este es uno de los gaps mas claros de toda la capa de servicios

### 4. Parser de nutricion no equivale al PWA

PWA:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\aiNutritionParser.ts`

RN:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\nutritionAnalyzer.ts`

Lectura:

- PWA tiene pipeline mas rico con modos, hints, timeout, sanitize y fallback detallado
- RN hoy resuelve:
  - bridge nativo local
  - fallback via shared-domain
- funciona, pero no replica todo el pipeline del PWA

Decision:

- parcial, no matched

### 5. Persistencia y migracion equivalentes solo en parte

PWA:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\storageService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\migrationSnapshotService.ts`

RN:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\mobilePersistenceService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\migrationImportService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\migrationHydrationService.ts`

Lectura:

- RN tiene infraestructura real de persistencia e importacion
- pero la equivalencia no es 1:1:
  - PWA = IndexedDB/Preferences + servicio general
  - RN = SQLite/MMKV + servicios separados

Decision:

- bloque operativo fuerte
- paridad literal parcial

### 6. Notificaciones/widgets existen, pero no son clon literal

PWA:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\notificationService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\widgetSyncService.ts`

RN:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\mobileNotificationService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\widgetSyncService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\services\backgroundSyncTask.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\modules\background.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\apps\mobile\src\modules\widgets.ts`

Decision:

- existe capa RN real
- pero no es clon 1:1 porque cambia plataforma, API y cobertura

## Lo que sigue directamente faltando

No encontre equivalentes RN directos para:

- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\aiService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\computeWorkerService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\backendAIService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\appUpdateService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\googleDriveService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\healthConnectService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\networkService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\cameraService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\hapticsService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\shareService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\socialService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\soundService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\statusBarService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\imageService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\videoService.ts`
- `C:\Users\valen\Downloads\kpkn-fit-(beta-test)\services\nutritionAiTelemetryService.ts`

## Decision por bloque

### Matched o casi matched operativamente

- `utils/calculations.ts`
- `analysisService.ts`
- `alchemyEngine.ts`
- `loopEngine.ts`
- `nutritionPlanEngine.ts`
- `fatigueService.ts`
- `recoveryService.ts`
- `structuralReadinessService.ts`
- `tendonRecoveryService.ts`
- `tendonAlertsService.ts`
- `ttcService.ts`
- `volumeCalculator.ts`
- `volumeCalibrationService.ts`

### Parcial fuerte

- `auge.ts` + `augeAdaptiveService.ts` vs `auge.ts` + `augeRuntimeService.ts`
- `storageService.ts` vs `mobilePersistenceService.ts`
- `notificationService.ts` vs `mobileNotificationService.ts`
- `widgetSyncService.ts`
- `migrationSnapshotService.ts` vs `migrationImportService.ts` + `migrationHydrationService.ts`
- `aiNutritionParser.ts` vs `nutritionAnalyzer.ts`

### Faltante o fragmentado

- `aiService.ts`
- `computeWorkerService.ts`
- `backendAIService.ts`
- gran parte del bloque de servicios nativos/utilitarios
- `foodIndexService.ts` como paridad real

## Decision

Paso 3 cerrado como mapa operativo de logica, matematicas y servicios.

- No demuestra paridad matematica final.
- Si demuestra donde RN ya tiene motor real.
- Si demuestra donde el repo todavia esta en fragmentacion o recorte de capacidades.

## Backlog oficial que deja este paso

1. Construir harness de paridad matematica PWA vs RN para AUGE, recovery, volume y nutrition.
2. Unificar estrategia RN para AI general.
3. Decidir equivalente RN real para compute worker.
4. Elevar `foodIndexService.ts` RN al nivel del PWA.
5. Elevar `nutritionAnalyzer.ts` al nivel funcional de `aiNutritionParser.ts`.
6. Definir que servicios nativos/utilitarios son obligatorios para el corte 1:1 y cuales son explicitamente N/A.
