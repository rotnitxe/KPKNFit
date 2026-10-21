# VT9b · Hub «Técnica en vídeo», rutas, importar de la galería, ajustes y entradas

- **Worktree y rama:** `C:\kw\vt-a`, rama `vt/vt9b`, desde `feat/video-technique` con VT9a integrado.
- **Antes de empezar, lee:**
  - `VT_COMUN.md`, `S_COMUN.md` y `KPKN_UI_V2.md`;
  - `VIDEO_TECHNIQUE_V1.md` §1 (puntos 5–6), §3.10 y §9 (punto 8);
  - del código de hoy:
    - `screens/sessioneditor/CatalogContracts.kt` y el ida y vuelta del catálogo en `MainActivity.kt` (~L1506-1555 y ~L1599-1634);
    - `screens/profile/ProfileScreen.kt` (`ProfileAlbumsCard`, ~L307 y ~L481);
    - `screens/albums/WorkoutMediaViewerScreen.kt`;
    - `screens/workout/components/WorkoutSessionAlbumSheet.kt` (`WorkoutMediaPreviewDialog`);
    - `data/repository/WorkoutMediaRepository.submitUriCapture`.
- **Logs:** `C:\kw\logs\vt-9b-<n>.log`.
- **PNG:** `C:/kw/shots/vt/9b`.

## Decisiones ya tomadas
- **Ajustes.** No existe pantalla de ajustes de Entreno: `SettingsTraining` está declarada pero sin registrar. Por eso los ajustes de técnica en vídeo viven en el propio hub, en un módulo «Grabación y análisis». No se crea una pantalla de ajustes nueva.
- **Catálogo.** Se añade `CatalogLaunchOrigin.TECHNIQUE`, con selección SINGLE. Revisa en el catálogo todo `when`/`if` sobre `origin` (textos del botón, validaciones de `isValidFor`): el botón debe decir «Elegir».

## Archivos permitidos
- **Nuevos:**
  - `screens/technique/hub/`: `TechniqueHubScreen.kt`, `TechniqueHubViewModel.kt`, `design/TechniqueExerciseRow.kt` y `design/TechniqueSettingsModule.kt`;
  - `screens/technique/importer/`: `VideoImportSheet.kt` y `VideoImportViewModel.kt`;
  - pruebas y hoja de render.
- **`navigation/Navigation.kt`:** `TechniqueHub("technique")` y `TechniqueAnalysis("technique/analysis/{mediaId}")`, con `create`.
- **`MainActivity.kt`** (solo bloques nuevos y callbacks):
  - las 2 rutas, más la conexión `onOpenTrends`/compare con las rutas de VT9a;
  - el ida y vuelta del catálogo para el hub, como en la ruta de Workout;
  - en Profile, `onOpenTechnique`;
  - en el visor de medios, `onOpenAnalysis`.
- **`screens/sessioneditor/CatalogContracts.kt`:** añadir `TECHNIQUE` al enum. Y lo mínimo del catálogo para que funcione con ese origen.
- **`ProfileScreen.kt`:** una tarjeta hermana de `ProfileAlbumsCard` («Técnica en vídeo»), con el mismo estilo de esa pantalla.
- **`WorkoutMediaViewerScreen.kt`:** en los vídeos, la acción «Ver análisis» en la barra de arriba, con el mismo estilo que sus acciones; callback nuevo con valor por defecto.
- **Lecturas de `poseTrajectoryEnabled`:**
  - quítalas de `WorkoutViewModel` (~L424), `WorkoutScreen` (~L1337 y ~L1388), `WorkoutAlbumsViewModel` (~L134) y su prueba (~L20);
  - la capa vieja deja de mostrarse en los diálogos de vídeo, y el campo de `Settings` se queda;
  - **NO toques** la UI de la sesión en vivo más allá de esas lecturas: es de VT8b.

## Diseño
1. **Hub** (`TechniqueHub`):
   - `KpknTopRow(onBack)`, con el título «Técnica en vídeo» en `KpknType.title`.
   - `KpknPrimaryPill("Analizar un vídeo de la galería")`.
   - **«Tus ejercicios»:** filas `TechniqueExerciseRow` con el nombre, «6 vídeos · último 13 oct» y una mini-gráfica de la pérdida de velocidad o del ROM si hay datos. Tocar una fila → `TechniqueTrends`.
   - **«Analizar vídeos anteriores (N)»:** `KpknTextAction`, solo si hay vídeos sin análisis. Encola FULL de cada uno; WorkManager ya va de uno en uno.
   - **`TechniqueSettingsModule`** («Grabación y análisis»), con filas de interruptor `KpknSwitch`. El patrón está en `WorkoutStructureSettingRow` (`WorkoutStructureRows.kt`) y `WorkoutSetSettingsBody.kt` ~L97-114. Contenido:
     - «Analizar la técnica de los vídeos»;
     - «Grabar a 60 fps cuando se pueda»;
     - «Disco de calibración», con chips de 45, 40 y 35 cm, y «Otro» con un `KpknNumberCell`;
     - «Duración máxima», con un `KpknSegmentedControl` «1 · 2 · 3 · 5 min».
     - Se guarda con `SettingsViewModel.update`, o con la función del repositorio que la use: busca la real.
   - **Vacío:** `KpknEmptyState`, «Graba una serie desde la sesión o analiza un vídeo de tu galería».
2. **Importar** (`VideoImportSheet`, una `KpknSheet`):
   - `PickVisualMedia(VideoOnly)`;
   - «¿Qué ejercicio es?», con los ejercicios recientes (de `observeExerciseSummaries`) y «Buscar en el catálogo» (TECHNIQUE, SINGLE);
   - «Carga (opcional)», en kg, con `KpknNumberCell`;
   - `KpknPrimaryPill("Analizar")`;
   - **al pulsar:**
     - `submitUriCapture(uri, seed)` sin sesión, con los ids del catálogo, nombres y `weightKg`;
     - `createdAtMs` de `METADATA_KEY_DATE`, si existe;
     - después se espera el `Deferred` y se navega a `TechniqueAnalysis(mediaId)`;
     - si falla, el error se muestra en la hoja.
   - **Límites:** vídeos de 10 min como máximo; con más de 4K/60, un aviso.
3. **Entradas:**
   - la tarjeta en Perfil;
   - «Ver análisis» en el visor de medios;
   - `WorkoutMediaPreviewDialog`: queda para VT8b, porque está dentro de la sesión.

## Pruebas
- **`TechniqueHubViewModelTest`:** lista y número de pendientes.
- **`VideoImportViewModelTest`:** con un repositorio falso, `submitUriCapture` lleva los ids.
- **Comprobación del catálogo:** `TECHNIQUE`/SINGLE se valida.
- **Hoja de render:**
  - hub con datos;
  - hub vacío;
  - el módulo de ajustes;
  - la hoja de importar;
  - 360 dp, 100 % y 130 %.
- **Corridas:**
  1. `:app:compileBaseDebugKotlin`
  2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.screens.technique.*" --tests "com.example.kpkn.screens.sessioneditor.*Catalog*" --tests "com.example.kpkn.screens.albums.*"`
  3. `:app:compileBaseDebugAndroidTestKotlin`
  4. la hoja
  5. **emulador:** APK `.dbg` → Perfil → «Técnica en vídeo», con capturas y `--crash-log`.

## Commits
- `feat(technique): hub de técnica en vídeo con ajustes de grabación y análisis`
- `feat(technique): importar un vídeo de la galería y analizarlo`
- `feat(technique): rutas y entradas desde Perfil y el visor de medios`
