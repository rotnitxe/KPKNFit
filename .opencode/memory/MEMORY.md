KPKN is local-first and private; Room is the source of truth on-device.
§
The Android product is under android-native/ and uses Kotlin, Compose, Clean Architecture, MVVM, manual DI, and StateFlow.
§
AUGE changes must preserve behavior across Android, iOS, and backend implementations.
§
The voice subsystem under android-native/app/src/main/java/com/example/kpkn/services/workout/ is large and active; use focused tests and diagnostics before broad refactors.
§
Android module has product flavors base/health; targeted tasks are compileBaseDebugKotlin and testBaseDebugUnitTest (bare compileDebugKotlin is ambiguous; assembleDebug builds both flavors).
§
Catalog V2 picker perf (2026-06): process cache in data/exercises/catalogv2/CatalogV2ProcessCache.kt (decodes exercise_catalog_v2.json once per process, prefetched in MainActivity step 9); ExercisePickerV2Catalog search is debounced 150ms on Dispatchers.Default via produceState; card compatibility/exactInfo/volume contributions are remember-memoized.
§
NutriTelemetry (2026-06, telemetry/nutrition/*): JSONL on-device exclusivo de nutrición en filesDir/nutrition_telemetry/ (rotación 512KB, retención 30d/10MB/24 archivos), sanitizer sin texto de comidas ni secretos, trazas por análisis con spans de etapa, marcadores in-flight + crash hook en proceso principal (KpknApplication), export SAF en Ajustes > Datos y app, análisis con android-native/scripts/analyze_nutrition_logs.py. Desactivable vía prefs flag "nutrition_telemetry/telemetry_enabled".
§
Crash al pulsar "Analizar" (FoodLoggerDrawer): causa raíz probable = regex no posesivo en FoodTemplateMatcher.QUANTITY_ANCHOR_PATTERN (backtracking exponencial → StackOverflowError en main); fix = cuantificadores posesivos + entrada acotada a 600 chars. Además: pipeline/Templates ahora corren dentro de la coroutine con try/Throwable (menos CancellationException), fallback con try anidado, CoroutineExceptionHandler en el scope del drawer, y !! de estado Compose reemplazados por lecturas seguras (comentarios CRASH-FIX).
§
The working tree may contain untracked WIP; check git status before blaming your own edits for build errors. (Nota previa resuelta: telemetry/nutrition/* ya compila y está integrado — ver entradas anteriores.)
§
Catalog V2 picker perf measured on desktop JVM (JDK17): decode 2.76MB asset ~34ms + index ~90ms per open (old path), search ~9.2ms/call on main thread per keystroke (old), compatibility ~5µs/call. On mid-range device expect ~3-5x: ~0.4-0.8s per picker open and ~30-50ms main-thread per keystroke before the fix; ~0 after.
