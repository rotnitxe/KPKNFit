# Kotlin Nativo — Estructura y Estado

## Stack
- Kotlin 2.0.21 + Jetpack Compose (BOM 2025.07.00)
- Material 3 + NavigationSuiteScaffold (adaptive navigation)
- Min SDK: 24 (Android 7.0) | Target/Compile SDK: 36 (Android 15)
- Java 11 | AGP 9.0.1
- **SIN** ViewModels, Room, Hilt, ni backend todavía

## Ruta del proyecto
`C:/Users/valen/Downloads/kpkn-fit-(beta-test)/android-native/`

## Estructura de archivos

```
android-native/
├── app/src/main/
│   ├── java/com/example/kpkn/
│   │   ├── MainActivity.kt          ← TODA la UI actual (290 líneas)
│   │   └── ui/theme/
│   │       ├── Color.kt             ← Paleta Material 3 (light/dark)
│   │       ├── Theme.kt             ← KPKNTheme() con soporte dinámico
│   │       └── Type.kt              ← Tipografía (bodyLarge base)
│   ├── res/
│   │   ├── drawable/kpknicon.png    ← Logo principal
│   │   ├── values/strings.xml       ← app_name = "KPKN"
│   │   └── xml/ (backup_rules, data_extraction_rules)
│   └── AndroidManifest.xml          ← Sin permisos, 1 activity
├── app/build.gradle.kts
├── build.gradle.kts (raíz)
└── gradle/libs.versions.toml
```

## Package name actual
`com.example.kpkn` ← **renombrar a `com.kpkn.fit` al publicar**

## Navegación actual

```kotlin
enum class AppDestinations(val label: String) {
    HOME("Inicio"),
    TRAINING("Entreno"),       // placeholder GenericScreen()
    NUTRITION("Alimentación"), // placeholder GenericScreen()
    WIKILAB("WikiLab")         // placeholder GenericScreen()
}
```
Usa `NavigationSuiteScaffold` (adaptive: bottom nav en móvil, rail en tablet).

## Composables implementados

| Composable | Estado |
|-----------|--------|
| `KPKNApp()` | ✅ Contenedor raíz con nav + scaffold |
| `HomeTopBar()` | ✅ Header dinámico (scroll: 100dp→70dp, logo se mueve) |
| `HomeWithProgramScreen()` | ✅ LazyColumn: saludo + RINGS card + sesión del día |
| `AugeRings()` | ✅ Canvas: 3 anillos (Muscular=rojo, SNC=azul, Columna=amarillo) |
| `CalibrationOverlay()` | ✅ Modal oscuro con drag vertical para calibrar rings |
| `RingLabel()` | ✅ Etiqueta con punto de color + porcentaje |
| `TodaySessionCard()` | ✅ Card con nombre sesión + botón START |
| `SectionHeader()` | ✅ Título sección en mayúsculas |
| `GenericScreen()` | ⚠️ Placeholder para pantallas sin implementar |
| `WikiIcon()` | ✅ "W" serif canvas |
| `DumbbellIcon()` | ✅ Mancuerna dibujada en Canvas |
| `NutritionIcon()` | ✅ Tenedor + plato en Canvas |

## Estado actual (en memoria de Composables, sin ViewModel)

```kotlin
// En KPKNApp()
var currentDestination    // pestaña activa
var userName              // nombre del usuario
var muscularProgress      // 0f–1f (actualmente 0.85f)
var sncProgress           // 0f–1f (actualmente 0.70f)
var columnaProgress       // 0f–1f (actualmente 0.90f)
var selectedRingIndex     // -1 = ninguno, 0/1/2 = ring activo
val listState             // scroll state para header dinámico
val scrollProgress        // derived: 0f=top, 1f=scrolled
```

## Modelos de datos (hardcoded, sin Room)

```kotlin
data class Program(val id: String, val name: String, val coverImage: Int? = null)
data class Session(val id: String, val name: String, val exercises: List<String>, val isCompleted: Boolean = false)

val samplePrograms = listOf(3 programas de ejemplo)
val todaySessions = listOf(1 sesión: "Pecho y Tríceps")
```

## Dependencias (libs.versions.toml)

- `androidx.core:core-ktx:1.18.0`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.6.1`
- `androidx.activity:activity-compose:1.8.0`
- `compose-bom:2025.07.00` → ui, material3, material3-adaptive-navigation-suite
- `haze:1.7.2` (importado, no usado todavía)

## Estado de migración

| Feature | PWA | Kotlin |
|---------|-----|--------|
| Home + RINGS | ✅ | ✅ Implementado |
| Top bar dinámica | ✅ | ✅ Implementado |
| Calibración rings | ✅ | ✅ Implementado |
| Programas | ✅ | 🔲 Solo datos hardcoded |
| Entrenamiento | ✅ | 🔲 GenericScreen placeholder |
| Nutrición | ✅ | 🔲 GenericScreen placeholder |
| WikiLab | ✅ | 🔲 GenericScreen placeholder |
| IA / Coach | ✅ | 🔲 Sin empezar |
| Perfil / AthleteID | ✅ | 🔲 Sin empezar |
| Settings | ✅ | 🔲 Sin empezar |
| Backend / Supabase | ✅ | 🔲 Sin empezar |
| Room / Base de datos | N/A | 🔲 Sin empezar |
| ViewModels | N/A | 🔲 Sin empezar |
