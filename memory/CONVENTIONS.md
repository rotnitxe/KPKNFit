# Convenciones del Proyecto

## PWA — Naming

| Elemento | Convención | Ejemplo |
|---------|-----------|---------|
| Componentes | PascalCase | `ExerciseDetailView.tsx` |
| Servicios | camelCase + sufijo Service | `aiService.ts`, `volumeCalculator.ts` |
| Stores | camelCase + sufijo Store | `useProgramStore`, `useNutritionStore` |
| Hooks | camelCase con prefijo `use` | `useAchievements`, `useLocalStorage` |
| Tipos | PascalCase | `AthleteProfileScore`, `OngoingWorkoutState` |
| Constantes | UPPER_SNAKE_CASE | |

## PWA — Patrones

- **Lazy loading**: `React.lazy()` para vistas pesadas (ProgramDetail, Settings, etc.)
- **Modales**: componentes separados con sufijo `Modal`, `Drawer`, o `Sheet`
- **Estado**: Zustand para persistido, AppContext para coordinación global, props para local
- **Workers**: cálculos pesados van a `computeWorker.ts` vía `computeWorkerService.ts`
- **Validación**: Zod schemas + `z.infer<typeof Schema>` para tipos
- **TypeScript**: `strict: true`, imports específicos (no `import *`)
- **Offline-first**: localStorage + IndexedDB como fuente primaria, Supabase como sync

## Kotlin — Naming y Patrones

| Elemento | Convención | Ejemplo |
|---------|-----------|---------|
| Composables | PascalCase | `HomeWithProgramScreen()`, `AugeRings()` |
| ViewModels (futuro) | PascalCase + VM | `HomeViewModel` |
| States | camelCase | `muscularProgress`, `currentDestination` |
| Data classes | PascalCase | `Program`, `Session` |
| Archivos de screen | `{Screen}Screen.kt` | `HomeScreen.kt`, `TrainingScreen.kt` |
| Archivos de componente | `{Component}.kt` | `AugeRings.kt`, `TodaySessionCard.kt` |

## Kotlin — Arquitectura objetivo (para la migración)

```
android-native/app/src/main/java/com/example/kpkn/
├── MainActivity.kt               ← Solo entry point + nav
├── screens/
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── training/
│   │   ├── TrainingScreen.kt
│   │   └── TrainingViewModel.kt
│   ├── nutrition/
│   │   ├── NutritionScreen.kt
│   │   └── NutritionViewModel.kt
│   └── wiki/
│       ├── WikiScreen.kt
│       └── WikiViewModel.kt
├── components/                   ← Composables reutilizables
│   ├── AugeRings.kt
│   ├── RingLabel.kt
│   └── ...
├── data/
│   ├── models/                   ← Data classes
│   ├── repository/               ← Repositorios
│   └── local/                    ← Room DAO + Entities
├── domain/
│   └── usecases/                 ← Lógica de negocio
└── ui/theme/                     ← Color, Theme, Type (ya existe)
```

## Equivalencias PWA → Kotlin

| PWA | Kotlin |
|-----|--------|
| Zustand store | ViewModel + StateFlow |
| AppContext | ViewModel compartido o CompositionLocal |
| React.lazy() | NavGraph con destinations lazy |
| useEffect | LaunchedEffect / SideEffect |
| useState | remember / mutableStateOf |
| useMemo | remember(key) |
| Supabase JS | Supabase Kotlin (ktor client) |
| Zod schema | Kotlinx Serialization + data class |
| TanStack Router | Navigation Compose |
| Web Worker | Coroutines (Dispatchers.Default) |
| localStorage | DataStore (preferencias) o Room (DB) |

## Colores de los RINGS (constantes Kotlin)

```kotlin
val RING_MUSCULAR = Color(0xFFFF5252)  // Rojo
val RING_SNC      = Color(0xFF448AFF)  // Azul
val RING_COLUMNA  = Color(0xFFFFD740)  // Amarillo
```
