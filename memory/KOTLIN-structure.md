# Kotlin Nativo — Estructura y Estado Real del Proyecto

## Stack Tecnológico Activo
- **Kotlin 2.0.21 + Jetpack Compose** (BOM `2025.07.00`)
- **Material 3** con soporte de Dynamic Color / Material You.
- **Compose Navigation** (`androidx.navigation:navigation-compose`) para routing dinámico en Jetpack Compose.
- **Room Database** (`androidx.room`) para la persistencia local de SQLite (offline-first).
- **Android Architecture Components**: ViewModels con reactividad basada en `StateFlow`/`SharedFlow`, `lifecycleScope`.
- **Haze Blur Effect** (`dev.chrisbanes.haze`) para efectos de glassmorphism visual.
- **Sentry Native SDK** para monitorización de errores y telemetría de rendimiento.
- **Configuración SDK**: Min SDK: 24 (Android 7.0) | Target/Compile SDK: 36 (Android 15) | AGP: 9.0.1 | Java: 11.

## Ruta del Proyecto
`C:/Users/valen/Downloads/kpkn-fit-(beta-test)/android-native/`

## Puntos de Entrada Principales
1. **`com.example.kpkn.KpknApplication`**: Inicializa la base de datos Room, configuración de telemetría, canales de notificación, gestores de alarmas de fondo e inyecciones iniciales.
2. **`com.example.kpkn.MainActivity`**: Contenedor principal de la app. Gestiona Edge-to-Edge, el `HazeState` global para difuminado de fondos, la estructura adaptativa de navegación (`NavigationSuiteScaffold`), el `NavHost` de Jetpack Compose y la resolución de Deep Links (`DeepLinkRouter`).

---

## Mapa Detallado de Archivos y Arquitectura

```
android-native/app/src/main/java/com/example/kpkn/
├── KpknApplication.kt                ← Inicializador del ciclo de vida de la aplicación
├── MainActivity.kt                   ← Entry point UI, setup de NavigationSuiteScaffold y HazeState
│
├── data/                             ← Capa de datos y persistencia
│   ├── db/                           ← Base de datos Room
│   │   ├── KpknDatabase.kt           ← Base de datos central (Tablas, Entidades, Migraciones)
│   │   ├── Daos.kt                   ← Consultas Room para entrenamiento, nutrición, etc.
│   │   ├── Entities.kt               ← Esquemas de tablas SQLite (Program, Session, Block, etc.)
│   │   ├── WikiLabDao.kt             ← DAO para la enciclopedia local de biomecánica
│   │   ├── WikiLabEntities.kt        ← Tablas para ejercicios, músculos, tendones y articulaciones
│   │   ├── PerformanceSnapshotEntity.kt
│   │   └── PerformanceRangeEntity.kt
│   ├── models/                       ← Clases de datos del dominio (Block, Program, Session, etc.)
│   ├── repository/                   ← Abstracción de orígenes de datos (ProgramRepository, NutritionRepository, etc.)
│   ├── remote/                       ← Cliente Supabase y APIs para sincronización con la nube
│   └── [modulos]/                    ← Datos específicos de splits, food, learn, voice, wikilab
│
├── domain/                           ← Reglas y lógica de negocio (Clean Architecture)
│   ├── auge/                         ← Motor AUGE adaptativo (Fatiga, Readiness, Baterías de recuperación)
│   ├── biomechanics/                 ← Biomecánica, perfiles de resistencia y tensión muscular
│   ├── calculations/                 ← Fórmulas 1RM (Epley, Brzycki), volumen efectivo y densidad
│   ├── energy/                       ← Cálculo de necesidades calóricas y reparto de macronutrientes
│   └── [casos-de-uso]/               ← Lógica de entrenamiento, nutrición, rendimiento y templates
│
├── navigation/                       ← Routing y Deep Linking
│   ├── Navigation.kt                 ← Definición sellada de KpknRoute y menús de navegación
│   ├── DeepLinkRouter.kt             ← Enrutador de enlaces profundos al recibir intents
│   ├── KpknDeepLinks.kt              ← Patrones de deep links soportados
│   └── NavigationBus.kt              ← Bus reactivo para navegación global entre hilos de fondo/UI
│
├── screens/                          ← Capa de presentación (Vistas Compose + ViewModels)
│   ├── home/                         ← Pantalla principal (RINGS card, saludo dinámico, sesión diaria)
│   │   ├── HomeScreen.kt             ← Layout reactivo y scroll adaptativo de la cabecera
│   │   ├── HomeViewModel.kt          ← Estado y eventos de la Home
│   │   ├── HomeRingsSection.kt       ← Canvas personalizado de los 3 anillos AUGE (Muscular, SNC, Columna)
│   │   ├── HomeSessionSection.kt     ← Tarjeta de sesión activa y enlace a entrenamiento
│   │   └── [componentes-home]/       ← HomeCards, HomePrograms, HomeWikiLab, AnimatedIconBackground
│   │
│   ├── workout/                      ← Tracker de entrenamiento en vivo
│   │   ├── WorkoutScreen.kt          ← Interfaz principal del entrenamiento (Cronómetro, Sets, Reps)
│   │   ├── WorkoutViewModel.kt       ← Máquina de estados compleja que controla el entreno en tiempo real
│   │   ├── WorkoutSetInputCard.kt    ← Control interactivo de registro de sets con RPE/RIR
│   │   ├── WorkoutVoiceInput.kt      ← Integración de control por voz offline/online para manos libres
│   │   ├── ReadinessGateScreen.kt    ← Puerta de validación de fatiga matutina antes del entreno
│   │   └── [componentes-entreno]/    BarbellPlateVisualizer, WorkoutRestRecoveryModel, Rest timers, etc.
│   │
│   ├── nutrition/                    ← Seguimiento dietético
│   │   ├── NutritionScreen.kt        ← Dashboard de calorías, macros, vasos de agua y comidas del día
│   │   ├── NutritionViewModel.kt     ← Gestión de ingesta de alimentos diaria y metas
│   │   ├── BodyProgressScreen.kt     ← Seguimiento avanzado de peso, porcentaje graso y perímetros
│   │   └── MealHistoryScreen.kt      ← Historial cronológico de ingestas
│   │
│   ├── wikilab/                      ← Enciclopedia interactiva de entrenamiento
│   │   ├── WikiHomeScreen.kt         ← Buscador de ejercicios y perfiles anatómicos
│   │   ├── WikiViewModel.kt          ← Carga reactiva de biomecánica e índices musculares
│   │   ├── ExerciseDetailScreen.kt   ← Detalle del ejercicio (Ángulos mecánicos, ejecución, músculos)
│   │   └── [sub-pantallas]/          Detalles de articulaciones, tendones, cadenas mecánicas y patrones
│   │
│   ├── settings/                     ← Menú de configuraciones
│   │   ├── SettingsScreen.kt         ← Menú principal
│   │   └── [sub-settings]/           General, Profile, Nutrition, Training, Auge, Notifications, Data
│   │
│   └── [otros]/                      competitions, learn (cursos/quizzes), profile, programdetail, auge
│
├── services/                         ← Servicios nativos del sistema operativo
│   ├── nutrition/                    ← Inferencia del modelo IA local kpkn-food-fg270m-v1
│   ├── workout/                      ← Text-To-Speech (asistente de voz), vibración háptica, WorkoutRestAlertManager
│   └── competition/                  ← Sincronización en tiempo real de retos
│
├── telemetry/                        ← Telemetría e informes de fallos
│   └── TelemetryHelper.kt            ← Wrapper personalizado de Sentry para eventos críticos y métricas
│
├── ui/                               ← Elementos de diseño global y temas
│   ├── components/                   ← Widgets compartidos (KpknSnackbar, SharedComponents, iconos Canvas)
│   ├── theme/                        ← Color.kt, Theme.kt, Type.kt (Tokens de diseño)
│   └── locale/                       ← LocaleManager para localización completa en Español
│
└── widgets/                          ← Widgets de escritorio del sistema Android
```

---

## Pantallas Clave y sus Implementaciones

1. **`HomeScreen.kt` / `HomeViewModel.kt`**:
   - Muestra el estado del atleta usando **`HomeRingsSection.kt`**, que dibuja con Canvas interactivo tres anillos: **Muscular** (Rojo), **SNC** (Azul), y **Columna** (Amarillo).
   - Ofrece un "CalibrationOverlay" para calibrar manualmente el estado diario mediante un gesto vertical de arrastre.
   - Conecta a la sesión actual mediante **`HomeSessionSection.kt`**, que calcula la readiness antes de permitir iniciar la rutina.

2. **`WorkoutScreen.kt` / `WorkoutViewModel.kt`**:
   - Una de las vistas más robustas del proyecto (260KB+ de lógica interactiva).
   - Controla las series de ejercicios con cronómetros adaptativos, sugerencia de pesos según perfiles de fuerza anteriores (`WorkoutLoadSuggestionRules`), registro del RPE (esfuerzo percibido) y RIR (repeticiones en reserva).
   - Incorpora entrada de datos por voz (`WorkoutVoiceInput.kt` y `WorkoutVoiceUi.kt`) para loguear series sin tocar la pantalla.
   - Integrado con **`WorkoutRestAlertManager`** para disparar recordatorios de descanso en el sistema operativo en segundo plano.

3. **`NutritionScreen.kt` / `NutritionViewModel.kt`**:
   - Dashboard completo de nutrición diaria.
   - Integra indexación de bases de datos offline (USDA Foods y OpenFoodFacts) e inferencia local de IA (`kpkn-food-fg270m-v1`) para análisis inteligente de frases escritas a mano ("2 huevos y una banana").

4. **`WikiLab` (Encyclopedia)**:
   - Base de conocimientos local que expone la relación anatómica-mecánica de los ejercicios.
   - Presenta detalladamente los perfiles de tensión de los músculos implicados, ángulos óptimos de trabajo articular, anatomía tendinosa y cadenas cinemáticas en pantallas nativas con visualizaciones interactivas de Canvas.

5. **`Learn` (Cursos y Aprendizaje)**:
   - Ofrece cursos formativos interactivos directamente en el dispositivo nativo.
   - Contiene un motor de exámenes (`LearnQuiz`), lector adaptativo (`LearnReader`) y entrega insignias de logros (`LearnBadge`).
