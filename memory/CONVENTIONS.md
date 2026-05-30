# Convenciones del Proyecto (PWA y Kotlin Nativo)

Este archivo establece los estándares de codificación, nomenclatura y arquitectura que deben respetarse en todo momento para mantener la coherencia entre la aplicación PWA original y la aplicación nativa en Kotlin.

---

## 🌐 1. PWA — Nomenclatura y Patrones

### Nomenclatura (PWA)
| Elemento | Convención | Ejemplo |
|---------|-----------|---------|
| **Componentes / Vistas** | PascalCase | `ExerciseDetailView.tsx`, `WorkoutSession.tsx` |
| **Servicios** | camelCase + sufijo `Service` | `aiService.ts`, `volumeCalculator.ts` |
| **Zustand Stores** | camelCase + sufijo `Store` | `useProgramStore`, `useNutritionStore` |
| **Hooks** | camelCase con prefijo `use` | `useAchievements`, `useLocalStorage` |
| **Tipos de Datos** | PascalCase | `AthleteProfileScore`, `OngoingWorkoutState` |
| **Constantes** | UPPER_SNAKE_CASE | `RING_MUSCULAR_COLOR`, `EPLEY_FORMULA` |

### Patrones Clave (PWA)
- **Lazy Loading**: `React.lazy()` para pantallas y diálogos pesados (ej. `ProgramDetail`, `SettingsComponent`, `RegisterFoodDrawer`).
- **Gestión de Estado**: Zustand para estados con persistencia local en base de datos IndexedDB. `AppContext.tsx` actúa como capa puente para compatibilidad heredada, pero no debe recibir estado nuevo.
- **Offloading de Cálculo**: Lógica matemática compleja (fatiga, readiness, volumen efectivo del motor AUGE) se descarga a `computeWorker.ts` mediante `computeWorkerService.ts`.
- **Validación de Datos**: Schemas de Zod para validar APIs y caches, infiriendo tipos TS mediante `z.infer<typeof Schema>`.
- **Offline-First**: Uso principal de `storageService.ts` (IndexedDB / LocalStorage) antes de realizar peticiones de sincronización a Supabase.

---

## 🤖 2. Kotlin Nativo — Arquitectura, Nomenclatura y Patrones

La aplicación nativa Android se divide estrictamente según los patrones de **Clean Architecture** y **MVVM (Model-View-ViewModel)**.

### Estructura de Capas
Cualquier nueva feature en el módulo `android-native` debe ubicarse en su capa correspondiente:
1. **`data/`**: Repositorios, DAOs de Room, Clases de Entidad (`Entities.kt`), modelos de serialización, cliente Supabase.
2. **`domain/`**: Casos de uso de negocio (ej. cálculos de fatiga, 1RM, macronutrientes, perfiles de tensión).
3. **`navigation/`**: Rutas selladas en `KpknRoute`, manejo de Deep Links y mensajería en segundo plano con `NavigationBus`.
4. **`screens/`**: UI Declarativa en Jetpack Compose (`HomeScreen.kt`) y su respectivo `ViewModel` para control reactivo.
5. **`services/`**: Controladores de hardware o sistemas (Text-to-Speech, háptica, reproductores de audio, alarmas en background).
6. **`ui/`**: Componentes visuales genéricos, temas de Material 3 (`Theme.kt`, `Color.kt`) y recursos de traducción.

---

### Nomenclatura (Kotlin Nativo)

| Elemento | Convención | Ejemplo |
|---------|-----------|---------|
| **Composables (Pantalla)** | PascalCase + sufijo `Screen` | `HomeScreen()`, `WorkoutScreen()`, `NutritionScreen()` |
| **Composables (Componente)** | PascalCase | `HomeRingsSection()`, `WorkoutSetInputCard()`, `AugeRings()` |
| **ViewModels** | PascalCase + sufijo `ViewModel` | `HomeViewModel`, `WorkoutViewModel`, `NutritionViewModel` |
| **StateFlows / States** | camelCase (sufijo `State` u `UiState`) | `uiState`, `muscularProgressState` |
| **Clases de Datos (Domain/Model)**| PascalCase | `Program`, `Session`, `Block`, `WorkoutLog` |
| **Entidades de BD** | PascalCase + sufijo `Entity` | `PerformanceSnapshotEntity`, `PerformanceRangeEntity` |
| **DAOs de Room** | PascalCase + sufijo `Dao` | `WorkoutDao`, `WikiLabDao` |

---

### Patrones Clave (Kotlin Nativo)

- **UI Declarativa Activa**: Uso exclusivo de Jetpack Compose. Evitar layouts en XML heredados.
- **Flujos Reactivos (`StateFlow` / `SharedFlow`)**:
  - Los ViewModels exponen estados de UI inmutables usando `StateFlow` (ej. `val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()`).
  - Las pantallas en Compose observan estos estados utilizando `.collectAsStateWithLifecycle()`.
- **Offline-First con Room**: Toda la información de entrenamiento, nutrición e historial del atleta se lee y escribe primero en la base de datos local `KpknDatabase` antes de intentar sincronizar remotamente.
- **Modularidad en Pantallas Complejas**:
  - Pantallas de gran escala (ej. `WorkoutScreen.kt` o `HomeScreen.kt`) deben subdividirse en secciones funcionales e independientes (ej. `HomeRingsSection.kt`, `WorkoutSetInputCard.kt`) para facilitar el mantenimiento y evitar archivos excesivamente masivos.
- **Traducción y Textos**:
  - Todo texto visible en interfaz de usuario **debe estar localizado en español**.
  - Utilizar recursos de cadenas nativos (`stringResource(R.string.key)`) a través de `strings.xml` o mediante el gestor dinámico de idiomas `LocaleManager` cuando provengan de lógica dinámica.
- **Cálculos en Hilos de Fondo**:
  - Para cálculos intensivos del motor AUGE, biomecánica o importaciones de base de datos masivas, usar Corrutinas en Kotlin especificando `Dispatchers.Default` o `Dispatchers.IO`.

---

## 🔗 3. Tabla de Equivalencias Críticas (PWA ➡️ Kotlin)

Al migrar código de la PWA a Kotlin nativo, sigue esta tabla de correspondencia directa de conceptos y tecnologías:

| PWA (React / TS / Capacitor) | Kotlin Nativo (Android / Compose) |
|-----------------------------|-----------------------------------|
| Zustand store (`useWorkoutStore`) | `ViewModel` + `StateFlow` (`WorkoutViewModel`) |
| AppContext.tsx (Bridge) | ViewModels Compartidos (`activityViewModels`) o `CompositionLocal` |
| `React.lazy()` / Dynamic Imports | Navigation Compose con Destinos Declarativos Lazy |
| `useEffect` / `useState` | `LaunchedEffect` / `remember { mutableStateOf() }` |
| `useMemo` | `remember(key) { ... }` o `derivedStateOf { ... }` |
| Supabase JS SDK | Supabase Kotlin Client (`io.github.jan-tennert.supabase`) |
| Esquemas de Zod | Data classes de Kotlin con anotación `@Serializable` |
| TanStack Router (Hash History) | Jetpack Navigation Compose (`NavHost` + `NavController`) |
| Web Worker (`computeWorker.ts`) | Coroutines (`Dispatchers.Default`) / Kotlin Flow |
| `@capacitor/preferences` (Storage) | Android `DataStore` (Preferencias) o Room `Database` (SQLite) |
| `@capacitor/haptics` / Text-To-Speech | Android `Vibrator` / Native `TextToSpeech` Service |

---

## 🎨 4. Paleta de Colores de los RINGS (M3 Compose)
Los tres anillos de rendimiento AUGE deben renderizarse exactamente con los siguientes valores de color en Compose Canvas:

```kotlin
val RING_MUSCULAR = Color(0xFFFF5252)  // Rojo vibrante para fatiga muscular
val RING_SNC      = Color(0xFF448AFF)  // Azul para estado del Sistema Nervioso Central
val RING_COLUMNA  = Color(0xFFFFD740)  // Amarillo/Dorado para estado articular/columna
```

---

## 📚 5. Habilidades y Guías de Desarrollo Experto (Skills)
Para guías detalladas paso a paso y patrones de código de producción para tareas específicas de Kotlin Native, consulta los siguientes manuales de referencia rápida en tu espacio de trabajo:
- **Canvas y Animaciones interactivas**: [SKILL_COMPOSE_CANVAS.md](file:///C:/Users/valen/Downloads/kpkn-fit-(beta-test)/docs/android/SKILL_COMPOSE_CANVAS.md)
- **Base de Datos offline y Repositorios**: [SKILL_ROOM_OFFLINE.md](file:///C:/Users/valen/Downloads/kpkn-fit-(beta-test)/docs/android/SKILL_ROOM_OFFLINE.md)
- **ViewModels, StateFlow y Asincronía**: [SKILL_VIEWMODEL_FLOWS.md](file:///C:/Users/valen/Downloads/kpkn-fit-(beta-test)/docs/android/SKILL_VIEWMODEL_FLOWS.md)
- **Servicios Nativos y APIs de Hardware (Háptica, Voz, Alarmas)**: [SKILL_NATIVE_HARDWARE.md](file:///C:/Users/valen/Downloads/kpkn-fit-(beta-test)/docs/android/SKILL_NATIVE_HARDWARE.md)
- **Inferencia IA Local y Heurísticas Offline**: [SKILL_LOCAL_AI.md](file:///C:/Users/valen/Downloads/kpkn-fit-(beta-test)/docs/android/SKILL_LOCAL_AI.md)
