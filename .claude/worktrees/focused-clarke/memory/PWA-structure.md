# PWA — Estructura y Mapa de Archivos

## Stack
- React 19 + TypeScript + esbuild + Tailwind
- Capacitor 7 (wrapper Android/iOS)
- TanStack Router (44 rutas, hash history)
- Zustand (estado persistido) + AppContext (estado global)
- Supabase (cloud sync), Gemini/DeepSeek/OpenAI (IA)
- Python FastAPI (cálculos pesados en backend)

## Carpetas clave

| Carpeta | Contenido |
|---------|-----------|
| `components/` | 132 componentes React (ver lista abajo) |
| `services/` | 50+ servicios (IA, nutrición, entrenamiento, storage) |
| `stores/` | 12 Zustand stores con persistencia |
| `contexts/` | AppContext.tsx (86KB), UIContext.tsx |
| `routes/` | router.tsx (44 rutas), navigation.ts |
| `hooks/` | Custom hooks (useAchievements, useLocalStorage, etc.) |
| `utils/` | colorUtils, theme, logging, cálculos |
| `data/` | USDA foods JSON, OpenFoodFacts JSON, ejercicios |
| `backend/` | Python FastAPI (engines, models, routers) |
| `packages/` | shared-types, shared-domain, design-tokens |
| `workers/` | computeWorker.ts (Web Worker para cálculos) |
| `plugins/` | capacitor-widget-bridge (plugin personalizado) |
| `scripts/` | Build scripts, extractores de DB, AI model staging |
| `public/` | Assets estáticos (SVGs, PNGs de Caupolican) |

## Archivos raíz

| Archivo | Propósito |
|---------|-----------|
| `index.tsx` | Entry point PWA — renderiza React con AppProvider |
| `App.tsx` | Raíz de componentes (47KB) — modales, vistas, listeners |
| `types.ts` | Tipos globales TS (900+ líneas) |
| `config.ts` | Configuración de la app |
| `index.html` | HTML base |
| `manifest.json` | PWA manifest |
| `service-worker.js` | Service Worker offline |
| `capacitor.config.json` | Config Capacitor |

## Componentes principales

### Vistas
| Componente | Propósito |
|-----------|----------|
| `Home.tsx` | Dashboard principal |
| `ProgramsView.tsx` | Listado de programas |
| `ProgramDetail.tsx` | Detalle (Analytics, DayView, History, Loops, Protocols) — lazy |
| `ProgramEditor.tsx` | Editor de programas — lazy |
| `SessionEditor.tsx` | Editor de sesiones |
| `WorkoutSession.tsx` | Tracker en vivo (sets, reps, peso, RIR, RPE) |
| `LogWorkoutView.tsx` | Registrar entrenamiento completado |
| `NutritionView.tsx` | Dashboard nutrición |
| `NutritionWizard.tsx` | Setup wizard 5 pasos (meta calórica, macros) |
| `FoodDatabaseView.tsx` | Base de datos de alimentos |
| `SmartMealPlannerView.tsx` | Planificador de comidas IA |
| `RegisterFoodDrawer.tsx` | Registro de alimentos con IA (80KB) |
| `CoachView.tsx` | Asistente coach |
| `WikiHomeView.tsx` | Wiki de ejercicios |
| `ExerciseDetailView.tsx` | Detalle ejercicio — biomecánica, variantes (49KB) |
| `AthleteIDDashboard.tsx` | Perfil atleta — puntuaciones, estimaciones (36KB) |
| `BodyLabView.tsx` | Mediciones corporales |
| `RecoveryView.tsx` | Recuperación muscular |
| `SettingsComponent.tsx` | Configuración — lazy |

### UI / Shared
| Componente | Propósito |
|-----------|----------|
| `TabBar.tsx` | Nav inferior: Home, Wiki, Coach, Nutrition, Profile |
| `CaupolicanBody.tsx` | Avatar interactivo — muestra músculos |
| `CoachChatModal.tsx` | Chat IA tipo coach |
| `AppBackground.tsx` | Fondo animado de la app |
| `ui/` | Button, Card, Modal, Toast, etc. |

## Services

### IA
- `aiService.ts` — orquesta Gemini/DeepSeek con fallback
- `geminiService.ts` — wrapper Google Gemini (24KB)
- `deepseekService.ts` — wrapper DeepSeek (21KB)
- `aiNutritionParser.ts` — parse de alimentos con IA
- `backendAIService.ts` — comunicación con FastAPI

### Entrenamiento
- `analysiService.ts` — volumen, fuerza, densidad (25KB)
- `augeAdaptiveService.ts` — sistema AUGE adaptativo (16KB)
- `auge.ts` — AUGE core logic
- `fatigueService.ts` — modelo de fatiga acumulativa
- `recoveryService.ts` — tiempo de recuperación muscular (39KB)
- `volumeCalculator.ts` — RPE→RIR, volumen efectivo (27KB)

### Nutrición
- `foodIndexService.ts` — indexación USDA + OpenFoodFacts (41KB)
- `nutritionPlanEngine.ts` — generador de planes
- `aiNutritionParser.ts` — parse con IA
- `volumeCalibrationService.ts` — calibración de porciones

### Storage / Sync
- `storageService.ts` — LocalStorage + IndexedDB
- `supabaseSyncService.ts` — sync cloud
- `supabaseService.ts` — cliente Supabase

### Utilidades
- `notificationService.ts`, `soundService.ts`, `cameraService.ts`
- `videoService.ts`, `sentryService.ts`, `shareService.ts`
- `widgetSyncService.ts` — sync con widgets del SO
- `computeWorkerService.ts` — orquesta Web Worker

## Stores (Zustand)

| Store | Estado que guarda |
|-------|------------------|
| `settingsStore` | tema, unidades, fórmulas RM, opciones IA |
| `programStore` | programas, sesiones, blocks, ejercicios |
| `workoutStore` | sesión en progreso, ejercicio actual, sets |
| `nutritionStore` | comidas, porciones, metas |
| `bodyStore` | peso, body fat, mediciones |
| `wellbeingStore` | sueño, estrés, readiness |
| `exerciseStore` | historial personal de ejercicios |
| `mealTemplateStore` | plantillas de comidas |
| `uiStore` | modales abiertos, pestaña activa |
| `authStore` | autenticación |

## Tipos clave (types.ts)

- `View` — 48 vistas de la app
- `Session` — sesión de entrenamiento
- `WorkoutLog` — registro completado
- `Program` — programa de entrenamiento
- `Exercise` — información de ejercicio
- `OngoingWorkoutState` — estado actual del entrenamiento
- `NutritionGoal`, `CalorieGoalConfig`
- `AthleteProfileScore`

**@kpkn/shared-types**: `CanonicalMuscle`, `MuscleRole`, `MuscleRecoveryStatus`, `AugeAdaptiveCache`, `ArticularBatteryId`

## Rutas (TanStack Router)

- `/` Home, `/programs`, `/programs/:programId`, `/programs/:programId/edit`
- `/workout`, `/log-workout`
- `/nutrition`, `/progress`, `/sleep`, `/recovery`
- `/coach`, `/settings`
- `/kpkn/*` (9 rutas wiki), `/body-lab`, `/mobility-lab`

## Backend (Python FastAPI)

```
backend/
├── main.py
├── engines/   adaptive, recovery, fatigue, volume, analysis, ai, banister
├── models/    adaptive, common, ai, recovery
└── routers/   adaptive, ai, recovery
```

## Path aliases (tsconfig)
- `@kpkn/shared-types` → `./packages/shared-types/src/index.ts`
- `@kpkn/shared-domain` → `./packages/shared-domain/src/index.ts`
- `@kpkn/design-tokens` → `./packages/design-tokens/src/index.ts`
- `capacitor-widget-bridge` → `./plugins/capacitor-widget-bridge/src/index.ts`
