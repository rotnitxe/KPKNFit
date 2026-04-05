# Plan Maestro: Ajustes Empoderados para KPKN Fit (Kotlin)

## Contexto

La pantalla de Ajustes actual (`SettingsScreen.kt`) es una lista plana de 5 secciones colapsables con ~20 opciones basicas. No expone muchas configuraciones que ya existen en el modelo de datos (`Settings.kt`) ni opciones nuevas que el usuario necesita. El objetivo es transformarla en una experiencia de ajustes completa, profesional, y nativa Android (patron Material 3: lista de categorias -> sub-pantallas), sin romper logicas internas ni la identidad visual OLED/high-contrast de KPKN.

---

## Arquitectura: Navegacion por Categorias

**Patron**: Lista de categorias (top-level) con navegacion a sub-pantallas individuales. Es el estandar Android (System Settings, Google Fit, Samsung Health). Cada categoria es un `ListItem` clickable que abre su propia pantalla con `TopAppBar` + `LazyColumn`.

```
SettingsScreen (top-level)
  [Profile Header Card] -- avatar + nombre + tipo atleta (clickable -> Perfil)
  7 categorias:
    General         -> SettingsGeneralScreen
    Perfil Personal -> SettingsProfileScreen
    Nutricion       -> SettingsNutritionScreen
    Entrenamiento   -> SettingsTrainingScreen
    Sistema AUGE    -> SettingsAugeScreen
    Notificaciones  -> SettingsNotificationsScreen
    Datos y App     -> SettingsDataScreen
  [Footer: version app]
```

**ViewModel**: Un unico `SettingsViewModel` (extraido a su propio archivo), compartido entre todas las sub-pantallas. Ya usa `StateFlow<Settings>` + `ProgramRepository.updateSettings()` con write-through cache. No necesita cambios arquitectonicos.

**Persistencia**: Sin migracion de Room. `Settings` se serializa como JSON blob (`ignoreUnknownKeys = true`). Campos nuevos con valores default deserializan correctamente.

---

## Cambios al Modelo de Datos

### Archivo: `android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt`

### Estado actual del modelo Settings

```kotlin
@Serializable
data class Settings(
    // Onboarding flags
    val hasSeenWelcome: Boolean = false,
    val hasSeenHomeTour: Boolean = false,

    // Perfil
    val username: String = "Usuario",
    val profilePicture: String? = null,
    val age: Int? = null,
    val athleteType: AthleteType = AthleteType.ENTHUSIAST,

    // Entrenamiento
    val weightUnit: WeightUnit = WeightUnit.KG,
    val intensityMetric: IntensityMetric = IntensityMetric.RIR,
    val barbellWeight: Double = 20.0,
    val restTimerDefaultSeconds: Int = 90,
    val restTimerAutoStart: Boolean = false,
    val showPRsInWorkout: Boolean = true,
    val oneRMFormula: OneRMFormula = OneRMFormula.BRZYCKI,
    val workoutLoggerMode: WorkoutLoggerMode = WorkoutLoggerMode.PRO,
    val sessionCompactView: Boolean = false,

    // IA
    val apiProvider: ApiProvider = ApiProvider.GEMINI,
    val apiKeys: ApiKeys = ApiKeys(),
    val aiTemperature: Double = 0.7,

    // UI
    val appTheme: AppTheme = AppTheme.DEFAULT,
    val themePrimaryColor: String = "#6750A4",
    val enableAnimations: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,

    // Biometria / Nutricion
    val userVitals: UserVitals = UserVitals(),
    val dailyCalorieGoal: Int? = null,
    val dailyProteinGoal: Int? = null,
    val dailyCarbGoal: Int? = null,
    val dailyFatGoal: Int? = null,
    val calorieGoalObjective: CalorieGoalObjective = CalorieGoalObjective.MAINTENANCE,

    // Sueno
    val sleepTargetHours: Double = 8.0,
    val smartSleepEnabled: Boolean = false,

    // Algoritmo
    val algorithmSettings: AlgorithmSettings = AlgorithmSettings(),
)
```

### Nuevos campos a AGREGAR en `Settings`

```kotlin
    // General (nuevos)
    val localAiFoodEnabled: Boolean = true,          // IA local para alimentos (on por defecto)
    val reducedMotionMode: Boolean = false,           // Modo ahorro rendimiento (reduce particulas, blur)
    val hapticIntensity: HapticIntensity = HapticIntensity.MEDIUM,  // 3 niveles de vibracion

    // Entrenamiento (nuevos)
    val sessionAutoAdvanceFields: Boolean = true,     // Avanzar campos auto despues de loguear set
    val showTimeSaverPrompt: Boolean = true,          // Prompt ahorro de tiempo al finalizar sesion
    val defaultVolumeSystem: VolumeSystem = VolumeSystem.KPNK,  // Sistema volumen por defecto (KPKN)
    val gymName: String? = null,                       // Nombre del gimnasio del usuario

    // IA (nuevos)
    val aiFallbackEnabled: Boolean = true,            // Fallback entre proveedores si falla el primario
    val aiMaxTokens: Int = 512,                       // Tokens max de respuesta IA

    // Notificaciones (seccion nueva completa)
    val workoutReminderEnabled: Boolean = false,
    val workoutReminderTime: String = "18:00",        // formato HH:mm
    val mealReminderEnabled: Boolean = false,
    val mealReminderBreakfast: String = "08:00",
    val mealReminderLunch: String = "13:00",
    val mealReminderDinner: String = "20:00",
    val sleepReminderEnabled: Boolean = false,
    val sleepReminderTime: String = "22:00",
```

### Nuevo enum a AGREGAR (junto a los otros enums en Settings.kt)

```kotlin
enum class HapticIntensity { LIGHT, MEDIUM, STRONG }
```

### Nuevos campos a AGREGAR en `AlgorithmSettings`

Estado actual:
```kotlin
@Serializable
data class AlgorithmSettings(
    val oneRMDecayRate: Double = 0.03,
    val failureFatigueFactor: Double = 1.5,
    val legVolumeMultiplier: Double = 1.0,
    val torsoVolumeMultiplier: Double = 1.0,
    val synergistFactor: Double = 0.25,
    val augeEnableNutritionTracking: Boolean = false,
    val augeEnableSleepTracking: Boolean = false,
)
```

Agregar estos 5 campos:
```kotlin
    val augeRecoverySensitivity: Double = 1.0,    // Multiplicador recuperacion (0.5 leniente - 2.0 estricto)
    val augeFatigueSensitivity: Double = 1.0,      // Multiplicador fatiga (0.5-2.0)
    val augeReadinessThreshold: Int = 60,          // Umbral minimo readiness para status "Listo" (0-100)
    val augeAutoDeload: Boolean = false,           // Auto-sugerir semanas de deload
    val augeShowAlertsInSession: Boolean = true,   // Mostrar alertas AUGE durante sesion activa
```

### Nota sobre VolumeSystem

El enum `VolumeSystem` ya existe en `Program.kt` linea 45:
```kotlin
enum class VolumeSystem { ISRAETEL, KPNK, MANUAL }
```
**NO renombrar `KPNK`** -- romperia la deserializacion de programas existentes guardados en Room. En el UI se muestra "KPKN" via el parametro `optionLabel` del dropdown.

---

## Desglose Completo de Categorias (60+ opciones)

### 1. GENERAL (14 opciones)

**Seccion APARIENCIA:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Tema de la app | Dropdown | `appTheme` | DEFAULT | Opciones: DEFAULT, DARK, DEEP_BLACK, VOLT, LIGHT |
| Animaciones | Switch | `enableAnimations` | true | Activa/desactiva todas las animaciones UI |
| Modo ahorro rendimiento | Switch | `reducedMotionMode` | false | Reduce particulas, blur y animaciones pesadas |

**Seccion SENSORIAL:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Vibracion haptica | Switch | `hapticFeedbackEnabled` | true | Master on/off para feedback haptico |
| Intensidad haptica | Dropdown | `hapticIntensity` | MEDIUM | LIGHT/MEDIUM/STRONG. Solo visible cuando haptica esta ON |
| Sonidos | Switch | `soundsEnabled` | true | Sonidos in-app (beep timer, celebracion PR) |

**Seccion INTELIGENCIA ARTIFICIAL:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| IA local para alimentos | Switch | `localAiFoodEnabled` | true | Usa modelo on-device para parsear alimentos |
| Proveedor IA | Dropdown | `apiProvider` | GEMINI | GEMINI/GPT/DEEPSEEK |
| Clave API Gemini | SecureTextField | `apiKeys.gemini` | null | Masked con PasswordVisualTransformation |
| Clave API GPT | SecureTextField | `apiKeys.gpt` | null | Masked con PasswordVisualTransformation |
| Clave API DeepSeek | SecureTextField | `apiKeys.deepseek` | null | Masked con PasswordVisualTransformation |
| Temperatura IA | Slider(0.0-1.0, step 0.1) | `aiTemperature` | 0.7 | Controla creatividad de la IA |
| Usar fallback IA | Switch | `aiFallbackEnabled` | true | Si falla el proveedor primario, intenta el siguiente |
| Tokens maximos | NumberField | `aiMaxTokens` | 512 | Maximo tokens en respuesta IA |

### 2. PERFIL PERSONAL (10 opciones)

**Seccion IDENTIDAD:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Nombre de usuario | TextField | `username` | "Usuario" | Nombre mostrado en Home greeting |
| Tipo de atleta | Dropdown | `athleteType` | ENTHUSIAST | 8 tipos: ENTHUSIAST, POWERLIFTER, BODYBUILDER, POWERBUILDER, ZERCHER_LIFTER, HYBRID, WEIGHTLIFTER, CALISTHENICS |
| Nombre del gimnasio | TextField | `gymName` | null | Gimnasio del usuario (opcional) |

**Seccion MEDIDAS CORPORALES:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Genero | Dropdown | `userVitals.gender` | null | MALE/FEMALE/OTHER |
| Edad | NumberField | `userVitals.age` | null | Edad en anos |
| Peso | NumberField + etiqueta unidad | `userVitals.weight` | null | En kg o lbs segun `weightUnit` |
| Altura (cm) | NumberField | `userVitals.height` | null | Altura en centimetros |

**Seccion COMPOSICION CORPORAL:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Grasa corporal (%) | NumberField | `userVitals.bodyFatPercentage` | null | Porcentaje grasa corporal |
| Masa muscular (%) | NumberField | `userVitals.muscleMassPercentage` | null | Porcentaje masa muscular |
| Peso objetivo | NumberField + etiqueta unidad | `userVitals.targetWeight` | null | Meta de peso |

### 3. NUTRICION (8 opciones)

**Seccion OBJETIVOS:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Objetivo calorico | SegmentedButton (3) | `calorieGoalObjective` | MAINTENANCE | DEFICIT/MAINTENANCE/SURPLUS |
| Calorias diarias | NumberField | `dailyCalorieGoal` | null | Meta calorica diaria |

**Seccion MACRONUTRIENTES:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Proteinas diarias (g) | NumberField | `dailyProteinGoal` | null | Meta proteina en gramos |
| Carbohidratos diarios (g) | NumberField | `dailyCarbGoal` | null | Meta carbohidratos en gramos |
| Grasas diarias (g) | NumberField | `dailyFatGoal` | null | Meta grasas en gramos |

**Seccion SUENO:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Meta de sueno (horas) | Slider(4.0-12.0, step 0.5) | `sleepTargetHours` | 8.0 | Horas objetivo de sueno |
| Sueno inteligente | Switch | `smartSleepEnabled` | false | Ajustes adaptativos de sueno |

**Seccion HERRAMIENTAS:**

| Opcion | Componente | Accion |
|---|---|---|
| Recalcular macros | ActionButton | Navega a `KpknRoute.NutritionWizard` para rehacer el wizard de macros |

### 4. ENTRENAMIENTO (12 opciones)

**Seccion UNIDADES:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Unidad de peso | SegmentedButton (2) | `weightUnit` | KG | KG/LBS |
| Metrica de intensidad | SegmentedButton (2) | `intensityMetric` | RIR | RPE/RIR |
| Formula 1RM | Dropdown | `oneRMFormula` | BRZYCKI | BRZYCKI/EPLEY/LANDER |
| Peso barra por defecto | NumberField + etiqueta unidad | `barbellWeight` | 20.0 | Peso de la barra olimpica |

**Seccion TEMPORIZADOR:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Descanso por defecto (seg) | Slider(30-300, step 15) | `restTimerDefaultSeconds` | 90 | Segundos de descanso default entre series |
| Iniciar descanso automatico | Switch | `restTimerAutoStart` | false | Inicia el timer automaticamente al completar set |

**Seccion EXPERIENCIA EN SESION:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Mostrar PRs en entrenamiento | Switch | `showPRsInWorkout` | true | Muestra records personales durante sesion |
| Modo del logger | SegmentedButton (2) | `workoutLoggerMode` | PRO | PRO (completo) / SIMPLE (minimalista) |
| Vista compacta de sesion | Switch | `sessionCompactView` | false | Reduce tamano de cards de ejercicio |
| Avanzar campos automaticamente | Switch | `sessionAutoAdvanceFields` | true | Auto-focus al siguiente campo despues de loguear |
| Prompt ahorro de tiempo | Switch | `showTimeSaverPrompt` | true | Pregunta si quieres saltar sets vacios al finalizar |

**Seccion VOLUMEN:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Sistema de volumen por defecto | Dropdown | `defaultVolumeSystem` | KPNK (mostrar como "KPKN") | KPKN (personalizado) / ISRAETEL (generico Mike Israetel) / MANUAL |

**Nota**: El dropdown debe usar `optionLabel = { when(it) { VolumeSystem.KPNK -> "KPKN (personalizado)"; VolumeSystem.ISRAETEL -> "Israetel (generico)"; VolumeSystem.MANUAL -> "Manual" } }` para NO exponer el typo del enum.

### 5. SISTEMA AUGE (12 opciones)

**Seccion SEGUIMIENTO:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Seguimiento de nutricion | Switch | `algorithmSettings.augeEnableNutritionTracking` | false | AUGE considera la nutricion para calculos de recuperacion |
| Seguimiento de sueno | Switch | `algorithmSettings.augeEnableSleepTracking` | false | AUGE considera el sueno para calculos de fatiga/recuperacion |
| Alertas durante sesion | Switch | `algorithmSettings.augeShowAlertsInSession` | true | Muestra alertas de fatiga/recuperacion durante el workout activo |
| Auto-deload sugerido | Switch | `algorithmSettings.augeAutoDeload` | false | AUGE sugiere automaticamente semanas de deload cuando la fatiga acumulada excede umbrales |

**Seccion SENSIBILIDAD:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Umbral de readiness | Slider(20-90) | `algorithmSettings.augeReadinessThreshold` | 60 | Puntuacion minima para que AUGE muestre status "Listo para entrenar" (verde) |
| Sensibilidad de fatiga | Slider(0.5-2.0, step 0.1) | `algorithmSettings.augeFatigueSensitivity` | 1.0 | Multiplicador sobre calculo de fatiga. <1 = mas tolerante, >1 = mas estricto |
| Sensibilidad de recuperacion | Slider(0.5-2.0, step 0.1) | `algorithmSettings.augeRecoverySensitivity` | 1.0 | Multiplicador sobre tiempos de recuperacion. <1 = recupera mas rapido, >1 = mas conservador |

**Seccion AVANZADO** (seccion colapsable con advertencia "Parametros avanzados del algoritmo - modifica solo si sabes lo que haces"):

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Tasa decaimiento 1RM | NumberField | `algorithmSettings.oneRMDecayRate` | 0.03 | Tasa a la que los estimados 1RM decaen con el tiempo sin datos nuevos |
| Factor fatiga por fallo | NumberField | `algorithmSettings.failureFatigueFactor` | 1.5 | Multiplicador de fatiga para series llevadas al fallo muscular |
| Multiplicador vol. piernas | Slider(0.5-2.0, step 0.1) | `algorithmSettings.legVolumeMultiplier` | 1.0 | Escala calculos de volumen para piernas |
| Multiplicador vol. torso | Slider(0.5-2.0, step 0.1) | `algorithmSettings.torsoVolumeMultiplier` | 1.0 | Escala calculos de volumen para torso |
| Factor sinergista | Slider(0.0-1.0, step 0.05) | `algorithmSettings.synergistFactor` | 0.25 | Peso de la contribucion de musculos sinergistas al volumen total |

### 6. NOTIFICACIONES (8 opciones)

**Seccion ENTRENAMIENTO:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Recordatorio de entrenamiento | Switch | `workoutReminderEnabled` | false | Notificacion diaria para entrenar |
| Hora del recordatorio | TimePicker | `workoutReminderTime` | "18:00" | Solo visible cuando switch esta ON (AnimatedVisibility) |

**Seccion COMIDAS:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Recordatorio de comidas | Switch | `mealReminderEnabled` | false | Notificaciones para las comidas principales |
| Desayuno | TimePicker | `mealReminderBreakfast` | "08:00" | Solo visible cuando switch esta ON |
| Almuerzo | TimePicker | `mealReminderLunch` | "13:00" | Solo visible cuando switch esta ON |
| Cena | TimePicker | `mealReminderDinner` | "20:00" | Solo visible cuando switch esta ON |

**Seccion SUENO:**

| Opcion | Componente | Campo Settings | Default | Descripcion |
|---|---|---|---|---|
| Recordatorio de sueno | Switch | `sleepReminderEnabled` | false | Notificacion para ir a dormir |
| Hora recordatorio | TimePicker | `sleepReminderTime` | "22:00" | Solo visible cuando switch esta ON |

**Nota importante**: Solo se guardan las preferencias de horario. El scheduling real de notificaciones Android via `AlarmManager` o `WorkManager` es scope futuro separado. Los TimePickers en M3 Compose no tienen un Dialog wrapper built-in -- se debe wrappear `TimePicker` dentro de `AlertDialog` con botones "Cancelar"/"Aceptar".

### 7. DATOS Y APP (5 items)

| Item | Componente | Accion/Detalle |
|---|---|---|
| Exportar datos | ActionButton | Genera y comparte archivo JSON con todos los programas + settings + logs. Usa `viewModel.exportData()` |
| Restablecer ajustes | ActionButton (destructivo, color error) | Reset `Settings()` a defaults. Requiere AlertDialog de confirmacion: "Esto restablecera todos los ajustes a sus valores por defecto. Esta accion no se puede deshacer." |
| Restablecer bienvenida | ActionButton | Sets `hasSeenWelcome=false, hasSeenHomeTour=false` para re-ver los tours de onboarding |
| Version de la app | InfoRow (no clickable) | Muestra `BuildConfig.VERSION_NAME` + `BuildConfig.VERSION_CODE` |
| Licencias open source | NavigationRow (con chevron) | Abre pantalla de licencias OSS o `OssLicensesActivity` |

---

## Diseno UI Detallado

### Top-Level Settings Screen

```
+------------------------------------------+
| <- Ajustes                               |
+------------------------------------------+
|                                          |
|  [  Avatar 96dp  ]                       |
|  Nombre de Usuario                       |
|  [ENTHUSIAST]  (badge chip)              |
|                                          |
+------------------------------------------+
|                                          |
| (o) General                          >   |
|     Tema, animaciones, IA, haptics       |
| ─────────────────────────────────────    |
| (o) Perfil Personal                  >   |
|     Nombre, peso, altura, genero         |
| ─────────────────────────────────────    |
| (o) Nutricion                        >   |
|     Macros, calorias, sueno              |
| ─────────────────────────────────────    |
| (o) Entrenamiento                    >   |
|     Timer, unidades, modo del logger     |
| ─────────────────────────────────────    |
| (o) Sistema AUGE                     >   |
|     Fatiga, recuperacion, alertas        |
| ─────────────────────────────────────    |
| (o) Notificaciones                   >   |
|     Recordatorios de entreno y comida    |
| ─────────────────────────────────────    |
| (o) Datos y App                      >   |
|     Exportar, restablecer, version       |
|                                          |
|      KPKN Fit v1.0.0 (beta)             |
+------------------------------------------+
```

### Profile Header Card
- Avatar: Circulo 96dp con iniciales del usuario sobre `MaterialTheme.colorScheme.primaryContainer`
- Username: `titleLarge`, `fontWeight = Bold`
- Athlete type: Chip con `secondaryContainer` background
- Card completa es clickable -> navega a `SettingsProfile`
- Usa `Card(shape = RoundedCornerShape(16.dp))` con padding interno de 24dp

### Category Row (SettingsCategoryRow)
- Usa Material 3 `ListItem`
- `leadingContent`: Icono en `Surface(shape = CircleShape, color = primaryContainer)` de 40dp
- `headlineContent`: Titulo en `bodyLarge`, `fontWeight = SemiBold`
- `supportingContent`: Subtitulo en `bodySmall`, color `onSurfaceVariant`
- `trailingContent`: `Icons.AutoMirrored.Filled.KeyboardArrowRight`
- `Modifier.clickable { onClick() }`
- `HorizontalDivider(color = outlineVariant.copy(alpha = 0.3f))` entre rows

### Iconos por Categoria
- General: `Icons.Default.Settings`
- Perfil Personal: `Icons.Default.Person`
- Nutricion: `Icons.Default.Restaurant`
- Entrenamiento: `Icons.Default.FitnessCenter`
- Sistema AUGE: `Icons.Default.Psychology`
- Notificaciones: `Icons.Default.Notifications`
- Datos y App: `Icons.Default.Storage`

### Sub-Screen Skeleton

Cada sub-pantalla sigue este esqueleto:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings[Category]Screen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("[Nombre Categoria]", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // SettingsSectionHeader + setting items
        }
    }
}
```

### Section Headers dentro de Sub-Screens

```kotlin
@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,   // Yellow en tema OLED
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp),
    )
}
```

### Tema Visual
- Background/Surface: `Color.Black` (OLED)
- SurfaceVariant: `Color(0xFF333333)` para cards
- Primary: `Color.Yellow` -- section headers, iconos de categoria activos, switch tracks activos
- Secondary: `Color.Cyan` -- valores informativos, badges secundarios
- Tertiary: `Color.Magenta` -- footer version
- OnSurface: `Color.White` -- texto principal
- Dividers: `outlineVariant.copy(alpha = 0.3f)`

---

## Componentes Reutilizables

### Archivo: `screens/settings/components/SettingsListItems.kt`

9 componentes:

#### 1. SettingsSwitchItem
```kotlin
@Composable
fun SettingsSwitchItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
)
```
- Row con titulo (bodyMedium) + switch trailing
- Description opcional en bodySmall, onSurfaceVariant
- Icono opcional leading (24dp)
- Padding: horizontal 16dp, vertical 14dp
- HorizontalDivider al final

#### 2. SettingsDropdownItem
```kotlin
@Composable
fun <T> SettingsDropdownItem(
    title: String,
    description: String? = null,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
)
```
- Row con titulo + TextButton mostrando seleccion actual (fontWeight Bold)
- DropdownMenu con opciones

#### 3. SettingsSegmentedButtonItem
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsSegmentedButtonItem(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
)
```
- Titulo arriba + SingleChoiceSegmentedButtonRow debajo
- Requiere @OptIn(ExperimentalMaterial3Api::class)

#### 4. SettingsSliderItem
```kotlin
@Composable
fun SettingsSliderItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueLabel: (Float) -> String,
)
```
- Titulo + valor actual a la derecha
- Slider debajo con rango y steps configurables

#### 5. SettingsTextFieldItem
```kotlin
@Composable
fun SettingsTextFieldItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
)
```
- Label arriba + OutlinedTextField debajo
- Soporta PasswordVisualTransformation para API keys

#### 6. SettingsTimePickerItem
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTimePickerItem(
    title: String,
    value: String,           // formato "HH:mm"
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
)
```
- Row clickable que muestra la hora actual
- Al clickear abre AlertDialog con TimePicker de M3 + botones Cancelar/Aceptar
- M3 no tiene TimePickerDialog built-in, hay que wrappear manualmente

#### 7. SettingsActionItem
```kotlin
@Composable
fun SettingsActionItem(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
)
```
- Row clickable, sin trailing widget
- Si destructive=true, titulo en MaterialTheme.colorScheme.error

#### 8. SettingsInfoRow
```kotlin
@Composable
fun SettingsInfoRow(title: String, value: String)
```
- Row simple: titulo leading + valor trailing en onSurfaceVariant

#### 9. SettingsSectionHeader
```kotlin
@Composable
fun SettingsSectionHeader(title: String)
```
- Texto uppercase, labelSmall, color primary, letterSpacing 1.sp

### Archivo: `screens/settings/components/SettingsCategoryRow.kt`

```kotlin
@Composable
fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
)
```
- Material 3 ListItem
- Leading: icono en circulo 40dp primaryContainer
- Headline: titulo bodyLarge SemiBold
- Supporting: subtitulo bodySmall onSurfaceVariant
- Trailing: chevron derecho

### Archivo: `screens/settings/components/SettingsProfileHeader.kt`

```kotlin
@Composable
fun SettingsProfileHeader(
    username: String,
    athleteType: AthleteType,
    onClick: () -> Unit,
)
```
- Card clickable con RoundedCornerShape(16dp)
- Avatar circular 96dp con iniciales en primaryContainer
- Username en titleLarge Bold
- AthleteType en chip secondaryContainer

---

## Estructura de Archivos

### Archivos a MODIFICAR (4)

| Archivo | Cambios |
|---|---|
| `android-native/.../data/models/Settings.kt` | +15 campos Settings, +5 campos AlgorithmSettings, +1 enum HapticIntensity |
| `android-native/.../navigation/Navigation.kt` | +7 objetos KpknRoute para sub-pantallas settings |
| `android-native/.../MainActivity.kt` | +7 bloques `composable(...)` en KPKNNavGraph |
| `android-native/.../screens/settings/SettingsScreen.kt` | Reescritura completa: reemplazar layout actual por lista de categorias. Eliminar SettingsViewModel embebido (se mueve a archivo propio). Eliminar composables privados (SettingsTextField, SettingsNumberField, SettingsSwitch, SettingsDropdown, SettingsSection) |

### Archivos NUEVOS a CREAR (11)

```
android-native/app/src/main/java/com/example/kpkn/screens/settings/
├── SettingsViewModel.kt                  -- Extraido de SettingsScreen.kt, mejorado con exportData() y resetSettings()
├── SettingsGeneralScreen.kt              -- Tema, animaciones, haptics, sonidos, IA
├── SettingsProfileScreen.kt              -- Nombre, vitals, composicion corporal
├── SettingsNutritionScreen.kt            -- Macros, calorias, sueno
├── SettingsTrainingScreen.kt             -- Timer, unidades, 1RM, logger, volumen
├── SettingsAugeScreen.kt                 -- Toggles AUGE, sensibilidad, algoritmo avanzado
├── SettingsNotificationsScreen.kt        -- Recordatorios entreno/comida/sueno
├── SettingsDataScreen.kt                 -- Export, reset, version, licencias
└── components/
    ├── SettingsListItems.kt              -- 9 componentes reutilizables
    ├── SettingsCategoryRow.kt            -- Row de categoria para top-level
    └── SettingsProfileHeader.kt          -- Avatar + nombre + badge card
```

### Rutas nuevas en Navigation.kt

Agregar dentro del sealed class `KpknRoute`:

```kotlin
object SettingsGeneral : KpknRoute("settings/general")
object SettingsProfile : KpknRoute("settings/profile")
object SettingsNutrition : KpknRoute("settings/nutrition")
object SettingsTraining : KpknRoute("settings/training")
object SettingsAuge : KpknRoute("settings/auge")
object SettingsNotifications : KpknRoute("settings/notifications")
object SettingsData : KpknRoute("settings/data")
```

### Composables en MainActivity.kt

Agregar 7 bloques dentro del `NavHost` existente (junto al composable de Settings actual):

```kotlin
composable(KpknRoute.SettingsGeneral.route) {
    SettingsGeneralScreen(onBack = { navController.popBackStack() })
}
composable(KpknRoute.SettingsProfile.route) {
    SettingsProfileScreen(onBack = { navController.popBackStack() })
}
composable(KpknRoute.SettingsNutrition.route) {
    SettingsNutritionScreen(
        onBack = { navController.popBackStack() },
        onNavigateToWizard = { navController.navigate(KpknRoute.NutritionWizard.route) },
    )
}
composable(KpknRoute.SettingsTraining.route) {
    SettingsTrainingScreen(onBack = { navController.popBackStack() })
}
composable(KpknRoute.SettingsAuge.route) {
    SettingsAugeScreen(onBack = { navController.popBackStack() })
}
composable(KpknRoute.SettingsNotifications.route) {
    SettingsNotificationsScreen(onBack = { navController.popBackStack() })
}
composable(KpknRoute.SettingsData.route) {
    SettingsDataScreen(onBack = { navController.popBackStack() })
}
```

### Modificar SettingsScreen composable existente

El composable `SettingsScreen` actual en `MainActivity.kt` ya existe:
```kotlin
composable(KpknRoute.Settings.route) {
    SettingsScreen(
        onBack = { navController.popBackStack() },
        themeMode = themeMode,
        onThemeChange = onThemeChange,
    )
}
```

Debe cambiar su signature para recibir callbacks de navegacion:
```kotlin
composable(KpknRoute.Settings.route) {
    SettingsScreen(
        onBack = { navController.popBackStack() },
        onNavigateToGeneral = { navController.navigate(KpknRoute.SettingsGeneral.route) },
        onNavigateToProfile = { navController.navigate(KpknRoute.SettingsProfile.route) },
        onNavigateToNutrition = { navController.navigate(KpknRoute.SettingsNutrition.route) },
        onNavigateToTraining = { navController.navigate(KpknRoute.SettingsTraining.route) },
        onNavigateToAuge = { navController.navigate(KpknRoute.SettingsAuge.route) },
        onNavigateToNotifications = { navController.navigate(KpknRoute.SettingsNotifications.route) },
        onNavigateToData = { navController.navigate(KpknRoute.SettingsData.route) },
    )
}
```

> Nota: Los parametros `themeMode` y `onThemeChange` se pueden eliminar si el tema se maneja completamente desde `Settings.appTheme` via el ViewModel.

---

## Secuencia de Implementacion

### Fase 1: Modelo + Componentes Compartidos
1. Agregar campos nuevos a `Settings.kt` (Settings + AlgorithmSettings + HapticIntensity enum)
2. Crear `screens/settings/components/SettingsListItems.kt` con los 9 composables
3. Crear `screens/settings/components/SettingsCategoryRow.kt`
4. Crear `screens/settings/components/SettingsProfileHeader.kt`

### Fase 2: Infraestructura de Navegacion
5. Agregar 7 rutas a `Navigation.kt`
6. Crear `screens/settings/SettingsViewModel.kt` (extraer de SettingsScreen.kt + agregar exportData() y resetSettings())

### Fase 3: Sub-Pantallas (pueden hacerse en paralelo)
7. `SettingsGeneralScreen.kt`
8. `SettingsProfileScreen.kt`
9. `SettingsNutritionScreen.kt`
10. `SettingsTrainingScreen.kt`
11. `SettingsAugeScreen.kt`
12. `SettingsNotificationsScreen.kt`
13. `SettingsDataScreen.kt`

### Fase 4: Integracion
14. Reescribir `SettingsScreen.kt` como lista de categorias top-level
15. Agregar 7 bloques `composable(...)` a `MainActivity.kt` y actualizar el de Settings
16. El boton de settings en HomeScreen ya navega a `KpknRoute.Settings.route` -- sin cambios necesarios

### Fase 5: Pulido
17. Agregar `AnimatedVisibility` para campos condicionales:
    - TimePicker rows solo visibles cuando su switch padre esta ON
    - Intensidad haptica solo visible cuando hapticFeedbackEnabled esta ON
    - Seccion AVANZADO en AUGE colapsable por defecto
18. Agregar `AlertDialog` de confirmacion para acciones destructivas en SettingsDataScreen
19. Verificar backward compatibility: instalar sobre version anterior, settings existentes se mantienen, campos nuevos usan defaults

---

## Decisiones Tecnicas y Notas Importantes

1. **VolumeSystem.KPNK**: NO renombrar el enum. Romperia la deserializacion de todos los Program JSON almacenados en Room. La UI muestra "KPKN" via el parametro `optionLabel` del dropdown.

2. **Notificaciones**: Solo se almacenan las preferencias de horario. El scheduling real de notificaciones Android via `AlarmManager` o `WorkManager` es un feature separado que observaria cambios en el `settings` flow.

3. **ProfileScreen.kt coexiste**: Es pantalla read-only. La nueva SettingsProfileScreen es el formulario de edicion. El card "edicion rapida" en ProfileScreen puede actualizarse para navegar directamente a `KpknRoute.SettingsProfile`.

4. **API keys**: Actualmente almacenadas como texto plano en el JSON blob de Room (ApiKeys data class). En el UI se usa `PasswordVisualTransformation()` para enmascarar. Migracion a `EncryptedSharedPreferences` es scope futuro.

5. **TimePickerDialog**: Material 3 Compose provee `TimePicker` y `TimeInput` composables pero NO un dialog wrapper. `SettingsTimePickerItem` debe wrappear `TimePicker` dentro de `AlertDialog` con botones "Cancelar"/"Aceptar".

6. **SegmentedButton**: `SingleChoiceSegmentedButtonRow` requiere `@OptIn(ExperimentalMaterial3Api::class)`. Es estandar en el codebase.

7. **Backward compatibility Room**: El JSON codec en `Entities.kt` usa `ignoreUnknownKeys = true` y `encodeDefaults = true`. Agregar nuevos campos con valores default a `Settings` es completamente seguro -- JSON viejo que no tenga estos campos deserializara correctamente usando los defaults de Kotlin.

8. **Cantidad de settings (60+)**: Mitigado por la navegacion jerarquica. Cada sub-pantalla tiene maximo 14 opciones, bien organizadas en secciones con headers.

---

## Verificacion (QA Checklist)

- [ ] `./gradlew assembleDebug` compila sin errores
- [ ] Abrir Settings desde Home -> muestra lista de 7 categorias con profile header
- [ ] Navegar a cada una de las 7 sub-pantallas -> back funcional
- [ ] Cambiar valores en cada sub-pantalla -> se persisten al cerrar/reabrir la app
- [ ] Instalar sobre version anterior de la app -> settings existentes se mantienen intactos
- [ ] Campos nuevos que no existian antes -> usan sus valores default correctamente
- [ ] Tema visual: fondo negro OLED, acentos Yellow/Cyan/Magenta consistentes
- [ ] TimePickers: ocultos cuando sus switches padre estan OFF
- [ ] Intensidad haptica: oculta cuando hapticFeedbackEnabled esta OFF
- [ ] Seccion AVANZADO en AUGE: colapsada por defecto, con advertencia visible
- [ ] Restablecer ajustes: muestra AlertDialog de confirmacion antes de ejecutar
- [ ] VolumeSystem dropdown: muestra "KPKN (personalizado)" (no "KPNK")
- [ ] SegmentedButtons: funcionan para WeightUnit, IntensityMetric, WorkoutLoggerMode, CalorieGoalObjective
- [ ] Profile header card: clickable, navega a Perfil Personal
