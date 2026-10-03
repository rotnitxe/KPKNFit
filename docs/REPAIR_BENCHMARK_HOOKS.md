# Ganchos de medición `RepairBenchmarkTrace` (inertes en producción)

Estado: **se mantienen** (decisión del dueño, 2026-10-02, ítem D1.5 del plan de cierre). Este documento explica qué son, dónde están, cómo se reactivan y cómo se retirarían si algún día ya no hacen falta.

## 1. Qué son

`RepairBenchmarkTrace` es un objeto interno (`internal object`) con marcas de medición de rendimiento. Se creó para comparar «antes y después» de las reparaciones de la sesión en vivo y del editor en un mismo dispositivo: cuánto tarda en dibujarse el primer fotograma útil tras una acción del usuario (elegir un ejercicio, abrir el selector, buscar, abrir el editor, entrar o salir de un entrenamiento).

Cada marca abre y cierra de inmediato una sección de traza de Android (`android.os.Trace`) con un nombre que empieza por `KPKNRepair|`. Una marca «de petición» (`..._request`) se empareja por su `id=` con la marca «de dibujo» (`..._draw`) que le corresponde; la diferencia de tiempos entre ambas, vista en Perfetto, es la medición.

## 2. Por qué están inertes en producción

- El archivo: `android-native/app/src/main/java/com/example/kpkn/screens/workout/RepairBenchmarkTrace.kt` (309 líneas).
- El interruptor: `private val enabled = BuildConfig.BUILD_TYPE == "repairBenchmark"` (línea 24). Todas las funciones públicas empiezan con `if (!enabled) return ...`, así que en `release` y `debug` no emiten trazas, no escriben en logcat y no tocan StrictMode.
- `android-native/app/build.gradle.kts` solo define los tipos de build `release` y `debug` (bloque `buildTypes`, líneas ~56-80). El tipo `repairBenchmark` **no existe en el producto**: por eso nada se activa nunca.
- Matiz: no es «solo un booleano». La ruta Home registra siempre un observador de ciclo de vida y un `drawWithContent` extra (`MainActivity.kt`, composable `KpknRoute.Home`, ~l.1143-1163) y hay un parámetro público extra `repairBenchmarkEditorRequestId` (`MainActivity.kt` ~l.1639/1677 y `SessionEditorScreen.kt` ~l.147). El coste es despreciable (una llamada por fotograma que retorna al instante).
- Peso: el plan de cierre estimó unos 32 KB sobre un APK de 553.850.254 bytes (0,006 %); el archivo fuente pesa 13.237 bytes. Cifra del plan de cierre, no re-medida.

**Atención al guardar el trabajo (commit):** `RepairBenchmarkTrace.kt` está **sin rastrear en git** y `MainActivity.kt` lo importa (línea 109). Si un commit no incluye el archivo, el proyecto no compila.

## 3. Dónde están las llamadas

21 llamadas en 6 archivos (sin contar el propio objeto). Los números de línea son del 2026-10-02 y se mueven con cada edición; para listarlas hoy:

```bash
grep -rn "RepairBenchmarkTrace" android-native/app/src/main
```

| Archivo (bajo `android-native/app/src/main/java/com/example/kpkn/`) | Llamadas | Qué marca |
|---|---|---|
| `MainActivity.kt` | 4 | `workoutEntryIntentReceived` en `onCreate` (~l.168) y `onNewIntent` (~l.285); `workoutHomeDrawn` al dibujar el Home (~l.1160); `editorCompositionRequested` al componer la ruta del editor (~l.1640) |
| `screens/sessioneditor/SessionEditorScreen.kt` | 1 | `editorReadyDrawn` cuando el editor está listo (~l.700) |
| `screens/workout/WorkoutScreen.kt` | 6 | ventana de lectura/escritura en disco del hilo principal (`beginUiIoWindow` / `endUiIoWindow`, ~l.221-222); `workoutExitNavigationCommitted` (~l.387); `workoutReadyDrawn` (~l.982); `selectionRequested` (~l.1134); `workoutExitRequested("finish")` (~l.1682) |
| `screens/workout/WorkoutSessionOverlaysHost.kt` | 2 | `workoutExitRequested("pause_exit")` y `workoutExitNavigationCommitted` al pulsar «Pausar y salir» en el diálogo de salida (~l.224-227) |
| `screens/workout/WorkoutV2Body.kt` | 2 | `selectionRequested` al cambiar de paso (~l.1119); `workoutBodyDrawn` al dibujar el paso pedido (~l.1622) |
| `screens/sessioneditor/components/ExercisePickerV2Catalog.kt` | 6 | ventana de disco `exercise_picker` (~l.479-481); `pickerOpenRequested` (~l.485); `pickerSearchRequested` (~l.497); `pickerResultsDrawn` (~l.1143); `pickerSearchResultsDrawn` (~l.1148) |

El selector de ejercicios además lleva algo de estado auxiliar con prefijo `repairBenchmark` (~l.478-496, 593-594, 611).

## 4. Qué se mide

| Pareja de marcas (`KPKNRepair\|...`) | Intervalo |
|---|---|
| `selection_request` → `workout_body_draw` | pedir un paso del entrenamiento hasta dibujarlo (con el número de series completadas) |
| `picker_request` → `picker_results_draw` | abrir el selector de ejercicios hasta el primer dibujo con resultados asentados |
| `picker_search_request` → `picker_search_results_draw` | escribir una búsqueda hasta ver sus resultados definitivos (el texto buscado no se incluye en los nombres de traza) |
| `editor_open_request` → `editor_ready_draw` | abrir el editor de sesión hasta que está listo |
| `workout_entry_request` → `workout_ready_draw` | entrar a un entrenamiento por un intent etiquetado hasta que la sesión pedida está cargada |
| `workout_exit_request` → `workout_home_draw` | salir (pausar o terminar) hasta que el Home se dibuja ya en estado RESUMED |

Además, mientras una ventana de disco está abierta (pantalla de entrenamiento o selector), el objeto activa StrictMode en el hilo principal y escribe en logcat con la etiqueta `KPKNRepairIO` cada lectura o escritura de disco (solo desde Android 9 / API 28).

## 5. Cómo reactivarlos

1. **Declarar el tipo de build.** En `android-native/app/build.gradle.kts`, dentro de `android { buildTypes { ... } }`, añadir el bloque que usó la copia aislada de la sesión de reparación (`artifacts/session-repair/2026-09-30-01a0eede/workspace/android-native/app/build.gradle.kts`, líneas ~82-94):

   ```kotlin
   create("repairBenchmark") {
       initWith(getByName("release"))
       signingConfig = signingConfigs.getByName("debug")
       isDebuggable = false
       matchingFallbacks += listOf("release")
       ndk {
           abiFilters.clear()
           abiFilters += listOf("x86_64")   // emulador; usa "arm64-v8a" en un teléfono real
       }
   }
   ```

   No hace falta tocar ningún otro archivo: `buildFeatures { buildConfig = true }` ya está activo, que es lo que expone `BuildConfig.BUILD_TYPE`.
2. **Compilar solo el flavor Base** (Health no se usa): `cd android-native && ./gradlew assembleBaseRepairBenchmark`. El APK queda en `android-native/app/build/outputs/apk/base/repairBenchmark/`. Para medir «antes y después», compila **las dos versiones del código con el mismo tipo de build** y el mismo dispositivo.
3. **Instalar y medir.** Instalar el APK en el emulador o teléfono, grabar una traza de Perfetto con las categorías de aplicación habilitadas para `com.example.kpkn` y buscar las secciones cuyo nombre empieza por `KPKNRepair|` (procedimiento estándar de Android; en este repositorio no está automatizado). Para el entrenamiento hay una entrada por intent: `adb shell am start -n com.example.kpkn/.MainActivity --ez com.example.kpkn.extra.REPAIR_BENCHMARK_WORKOUT_ENTRY true --es com.example.kpkn.extra.REPAIR_BENCHMARK_WORKOUT_SESSION_ID <id de la sesión>`. Sin esos dos extras no se abre el intervalo `workout_entry_request`.
4. **Volver a dejarlos inertes.** Quitar el bloque `create("repairBenchmark")` del `build.gradle.kts`. No hay nada más que revertir.

Referencia histórica (no mantenida): la copia aislada de la sesión de reparación también tenía un conjunto de fuentes `src/repairBenchmark` (un `AndroidManifest.xml` y `RepairBenchmarkRoomSnapshotProvider.kt`, un lector de Room usado por el comparador A/B) y scripts de análisis de Perfetto, todo bajo `artifacts/session-repair/2026-09-30-01a0eede/`. **No se integró al producto**; para ver solo las marcas basta con el paso 1.

## 6. Cómo retirarlos si algún día ya no se necesitan

Trabajo estimado por el plan de cierre: 45-60 min más unos 30 min de máquina. Pasos:

1. Quitar las 21 llamadas de la tabla de la sección 3 (y las ventanas de disco `beginUiIoWindow` / `endUiIoWindow` con su variable local).
2. Quitar el parámetro `repairBenchmarkEditorRequestId` de `MainActivity.kt` y de `SessionEditorScreen.kt`, y el estado auxiliar `repairBenchmark*` del selector de ejercicios.
3. Quitar del Home el observador de ciclo de vida y el `drawWithContent` (`MainActivity.kt` ~l.1143-1163), conservando `HomeScreen`.
4. Borrar `RepairBenchmarkTrace.kt` y el `import` de `MainActivity.kt`.
5. Compilar Base, correr la suite completa y repetir la prueba de pantalla del entrenamiento y del editor.

Hacerlo en un commit aparte, después del commit que guarda el estado consolidado (ítem X-COMMIT del plan de cierre).
