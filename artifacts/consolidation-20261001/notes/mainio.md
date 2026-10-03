# mainio: DiskReadViolation de StrictMode en el hilo Main

Fuente: `device-evidence\*\base\*\logcat.txt`, filtrando `D StrictMode:`.
Recuento sobre toda la evidencia (APK integrado): 189 `DiskReadViolation` y 3 `LeakedClosableViolation`.
Los 189 se reparten en tres causas (las tres tratadas aquí):

| Origen | Frames de app | Drivers |
|---|---|---|
| `LocaleManager.getSavedLanguage` (:86) <- `wrapContext` (:97) <- `MainActivity.attachBaseContext` (:150) | 152 | todos (cada arranque en frío y cada recreación) |
| `ProgramSnapshotStore.<init>` (:23) y `.list` (:32) <- `ProgramDetailScreen` (:102/:103) y `ProgramDetailViewModel.refreshProgramSnapshots` (:823) | 10 y 5 | editor-* |
| `coil.key.FileKeyer.key` (`File.lastModified`) | 18 | media-import, camerax-capture |

Las líneas `WorkoutMediaRepository.<init>`, `WorkoutMediaStore.<init>`, `LegacyLearnPreferencesCleanup` y `MainActivity.kt:146/174/175` solo aparecen en `migration-27-28\...-probe`. Son del APK baseline histórico, no del integrado, y ya están resueltas en el árbol integrado: `WorkoutMediaRepository` crea `store`, `importer` y `captureJournal` con `by lazy`, y la limpieza de preferencias Learn corre en `lifecycleScope.launch(Dispatchers.IO)` en `MainActivity`.

## 1. LocaleManager (idioma) — fixed

### Diagnóstico
Pila del Activity (dos violaciones por arranque, mismo instante):

```
File.exists <- ContextImpl.getDataDir          <- getPreferencesDir <- getSharedPreferencesPath
File.exists <- ContextImpl.ensurePrivateDirExists <- getPreferencesDir <- getSharedPreferencesPath
  <- ContextImpl.getSharedPreferences <- LocaleManager.getSavedLanguage(:86) <- wrapContext(:97) <- MainActivity.attachBaseContext(:150)
```

- No hay frame `SharedPreferencesImpl.awaitLoadedLocked`. La violación no es el parseo del XML: es la resolución de la ruta del archivo (`access()` sobre el directorio de datos y de preferencias).
- Esa resolución se repite en cada `ContextImpl` nuevo (cada Activity tiene el suyo), aunque el `SharedPreferencesImpl` ya esté cargado en el proceso. Por eso la sugerencia "precalentar la lectura en un hilo de fondo" **no elimina esta pila**.
- Los 175–312 ms que reporta StrictMode (`~duration`) son una estimación hasta el fin del mensaje del Looper (todo `performLaunchActivity`). Es una inferencia sobre la semántica de StrictMode, no una medición del syscall. No es el coste real del `access()`.
- `Application.attachBaseContext` también llama a `getSharedPreferences` en Main, pero ahí aún no hay política StrictMode (se instala en `KpknApplication.onCreate`) y no aparece en los logs.

### Cambio
`android-native\app\src\main\java\com\example\kpkn\ui\locale\LocaleManager.kt`:
- `@Volatile private var cachedLanguage: String?`.
- `getSavedLanguage`: devuelve la caché si existe; si no, lee las preferencias una vez y la rellena.
- `persist`: escribe las preferencias (`apply()`) y actualiza la caché (lo usan `applyAndPersist` y la sincronización Room -> prefs de `MainActivity`).
- `internal fun clearCachedLanguageForTests()`: simula un proceso nuevo en tests.

`KpknApplication.kt` y `MainActivity.kt` **no se tocan**: `Application.attachBaseContext` ya llama a `wrapContext`, lo que rellena la caché antes de que exista la Activity.

### Por qué no cambia el comportamiento
- Primera ejecución: sin archivo, la lectura devuelve `"system"` y la caché lo guarda. `wrapContext` devuelve el mismo contexto, como antes.
- Proceso nuevo con idioma guardado: `Application.attachBaseContext` lee el archivo (igual que antes) y el Activity reutiliza el valor.
- Cambio de idioma: `persist` actualiza prefs y caché; la recreación (`recreateEvent` en API <= 32, o el sistema en API >= 33) lee el valor nuevo.
- Proceso `:voice`: tiene su propio `Application` y su propia caché. Antes ya veía solo el valor cargado al arrancar (`MODE_PRIVATE` no refresca entre procesos), así que no empeora.

### Verificación
- JVM: `LocaleManagerTest` (5 tests: primera ejecución, lectura única y servida de memoria, `persist` inmediato y escrito en prefs, `wrapContext` ante cambios, `getEffectiveLocale`).
- Dispositivo:
  1. `adb shell pm clear com.example.kpkn`, arrancar en frío y `adb logcat -d -s StrictMode:D | grep -c LocaleManager` debe dar 0.
  2. Ajustes -> idioma (en/es) -> la Activity se recrea con el idioma nuevo y sin `StrictMode` de `LocaleManager`.
  3. `am force-stop` y relanzar: mantiene el idioma elegido.
  4. Con idioma distinto de `system`, comprobar que el wrapper no añade violaciones en `attachBaseContext` (`createConfigurationContext` no se vio en la evidencia porque el AVD usaba `system`).

## 2. ProgramSnapshotStore (copias recuperables) — fixed

### Diagnóstico
- `ProgramDetailScreen.kt:103` (`LaunchedEffect`, Main) llama a `ProgramSnapshotStore.getInstance(context)`. El constructor hacía `context.getSharedPreferences(...)` (misma resolución de rutas que arriba).
- `ProgramDetailViewModel.attachSnapshotStore -> refreshProgramSnapshots` (Main) llamaba a `store.list(...)`. Su `prefs.getString` espera la carga del archivo (`awaitLoadedLocked`) y además decodifica hasta 10 programas en JSON.
- `applyProgramTemplate(overwrite = true)` llamaba a `pushProgramSnapshot` (lectura + `commit()` síncrono) en Main. No salía en los logs (el driver no ejerce ese flujo) pero es la misma clase de defecto.

### Cambio
- `domain\training\ProgramSnapshotStore.kt`: `prefs` pasa a `by lazy`. El constructor ya no toca el disco y el primer acceso real ocurre dentro de `list`/`push`/`restore`, todos en IO. Esto evita editar `ProgramDetailScreen.kt` (otro propietario).
- `screens\programdetail\ProgramDetailViewModel.kt` (solo llamadas al store):
  - `refreshProgramSnapshots`: `viewModelScope.launch { snapshotStoreLock.withLock { _programSnapshots.value = withContext(Dispatchers.IO) { store.list(target) } } }`. La firma pública no cambia.
  - `pushProgramSnapshot` pasa a `suspend`: mismo `withLock` + `withContext(Dispatchers.IO)`. Su llamada en `applyProgramTemplate` queda en IO; la de `applyProtocolOverwrite` (ya dentro de `withContext(IO)`) anida sin coste.
  - `restoreProgramSnapshot`: `Dispatchers.Default` -> `Dispatchers.IO` (es E/S).
  - `Mutex` (`snapshotStoreLock`): conserva el orden de llamada y evita que una lectura vieja pise el resultado de un `push`. El lock se toma antes de cualquier suspensión, así que es FIFO respecto al orden de las llamadas. `_programSnapshots` sigue siendo privado y se expone como `StateFlow`.

### Por qué no cambia el comportamiento
El estado final es el mismo (`list` tras `push`, ordenado). La diferencia es que la lista llega unos milisegundos después de abrir el editor (el estado inicial ya era `emptyList()`). Ningún test existente adjuntaba el store, y el sheet de copias observa el `StateFlow`.

### Verificación
- JVM: `ProgramSnapshotStoreTest` (el constructor no pide preferencias; push/list/restore con una sola consulta) y, en `ProgramDetailViewModelTest`, `attachSnapshotStore_loads_saved_copies_and_refresh_picks_up_new_ones` y `replacing_protocol_records_the_previous_plan_as_a_recoverable_copy`.
- Dispositivo: drivers `editor-*`; `grep -c ProgramSnapshotStore logcat.txt` debe dar 0. Manualmente: abrir un programa, ver el historial de copias, aplicar un protocolo con reemplazo y comprobar que aparece "Antes de ...". Repetir con una plantilla con sobrescritura.

## 3. Coil FileKeyer en miniaturas de medios — fixed

### Diagnóstico
`coil.key.FileKeyer.key(FileKeyer.kt:10)` <- `ComponentRegistry.key` <- `MemoryCacheService.newCacheKey(:48)` <- `EngineInterceptor.intercept(:66)` <- `RealImageLoader.executeMain`, disparado desde `ConstraintsSizeResolver.measure` (layout de Compose en Main). Con `File` como modelo y sin `memoryCacheKey`, Coil 2.7 calcula la clave en Main con `File.lastModified()`.

### Cambio
- Nuevo `ui\components\LocalMediaImage.kt`:
  - `LocalMediaImageSource(file, memoryCacheKey)` y `localMediaImageSource(file)` calculan la clave `kpkn-media:<ruta>:<lastModified>:<length>` (bloqueante, solo en IO).
  - `rememberLocalMediaImageSource(file)` la resuelve con `produceState` en `Dispatchers.IO`. Al cambiar de archivo conserva la fuente anterior hasta tener la nueva, como hace Coil con `model = File`.
  - `LocalMediaImage(source, ...)` construye `ImageRequest.Builder(context).data(file).memoryCacheKey(key)` y llama a `AsyncImage`. Con `source == null` equivale a `AsyncImage(model = null)`.
  - `rememberStableUriImageRequest(uri)` para miniaturas por `uri` inmutable (archivos con nombre UUID): `memoryCacheKey(uri)`.
- Usos migrados:
  - `WorkoutMediaThumb.kt`: archivo y clave se resuelven juntos en el mismo `withContext(IO)` existente (nueva `mediaPreviewSource`), sin salto extra.
  - `WorkoutSessionAlbumSheet.kt` (vista previa en diálogo), `WorkoutAlbumsScreen.kt` (portada), `WorkoutMediaViewerScreen.kt` (visor) y `WorkoutSessionCockpit.kt` (fotos de sesión heredadas): `LocalMediaImage(rememberLocalMediaImageSource(...))`.
  - `ProfileCompetitionsArchive.kt` y `CompetitionWizardAlbumStep.kt` (URIs `file:`; Coil las mapea a `File` y pasaban por el mismo `FileKeyer`): `rememberStableUriImageRequest`. No salían en los logs, pero eran el mismo patrón.

### Por qué no cambia la apariencia ni la invalidación
- Una petición con `memoryCacheKey` explícito usa esa clave tal cual y no llama al keyer (la pila de `newCacheKey` pasa por la ruta lenta justo por la ausencia de clave). La validación de tamaño de la caché no depende de la clave.
- La clave por defecto era `<ruta>-<lastModified>`. La nueva añade `length`, así que regenerar una miniatura en la misma ruta (`store.thumbFile(id)` es determinista) sigue invalidando la entrada.
- Los composables mantienen modificador, `contentScale`, descripción y el fondo negro mientras no hay miniatura.
- Las URIs de competición son `Uri.fromFile(<UUID>.ext)` y nunca se reescriben, así que el `uri` como clave basta.

### Verificación
- JVM: `LocalMediaImageSourceTest` (clave estable, cambia al reemplazar el archivo, solo por `lastModified`, solo por `length`, distinta entre rutas) y `WorkoutMediaPreviewSourceTest` (elección miniatura/original/null).
- Dispositivo: drivers `media-import` y `camerax-capture`; `grep -c FileKeyer logcat.txt` debe dar 0. Visual: miniaturas de la cabina, hoja de álbum, álbumes y visor idénticas. Reintentar una captura con el mismo UUID debe mostrar la miniatura nueva, no la cacheada.

## Archivos
Producción: `ui\locale\LocaleManager.kt`, `domain\training\ProgramSnapshotStore.kt`, `screens\programdetail\ProgramDetailViewModel.kt`, `ui\components\LocalMediaImage.kt` (nuevo), `screens\workout\components\WorkoutMediaThumb.kt`, `WorkoutSessionAlbumSheet.kt`, `WorkoutSessionCockpit.kt`, `screens\albums\WorkoutAlbumsScreen.kt`, `WorkoutMediaViewerScreen.kt`, `screens\competitions\ProfileCompetitionsArchive.kt`, `screens\competitions\wizard\CompetitionWizardAlbumStep.kt`.
Tests (nuevos salvo el VM test): `ui\locale\LocaleManagerTest.kt`, `domain\training\ProgramSnapshotStoreTest.kt`, `ui\components\LocalMediaImageSourceTest.kt`, `screens\workout\components\WorkoutMediaPreviewSourceTest.kt`; ampliado `screens\programdetail\ProgramDetailViewModelTest.kt`.

## Observaciones fuera del alcance (sin tocar)
- `applyProgramTemplate` llama a `pushProgramSnapshot` fuera de `runCatching`: si `commit()` falla, el `check(...)` de `ProgramSnapshotStore.push` lanza `IllegalStateException` dentro de `viewModelScope.launch` y cierra la app. Ya era así. `applyProtocolOverwrite` sí lo captura y muestra snackbar.
- `ProgramSnapshotStore` vive en `domain/` e importa `android.*` (contradice la regla de CLAUDE.md). No se mueve en esta ola.
- Los 3 `LeakedClosableViolation` vienen de `WorkoutMediaLateRetryInstrumentedTest` (conexión SQLite de una BD en memoria finalizada por el GC). Es de test y no del producto; los tests pasan.
- `SessionHeroParts.kt` (`model = background.value`) y `CompactHeroBanner.kt` (`.data(coverValue)`) reciben un `String`. Si fuera una ruta local, Coil la mapearía a `File` y caería en `FileKeyer`. Los covers por defecto son `gradient://` y no se ejercen en los drivers. Sin tocar.
- No se ejecutó Gradle ni adb (regla del paquete): compilación revisada a mano, símbolos verificados con Grep, finales de línea comprobados con Python.
