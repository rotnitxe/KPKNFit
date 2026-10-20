# UI-4 · Perfil y hub: «Técnica en vídeo» como función con contenido

- **Rama:** `vt/ui4` en `C:\kw\vt-a`, que sale de `feat/vt-ui` con UI-1, UI-2 y UI-3 integrados.
- **Logs:** `C:\kw\logs\vt-ui4-<n>.log`.
- **PNG:** `C:/kw/shots/vt/ui/4`.
- **Antes de empezar, lee:** `UI_COMUN.md` (y lo que cita) y `docs/video-technique/UI_PLAN.md` (D38).
- **Código:**
  - `screens/profile/ProfileScreen.kt`: `ProfileUiState`, `ProfileViewModel`, `ProfileAlbumsCard` y `ProfileTechniqueCard` (~L497);
  - `screens/technique/hub/TechniqueHubScreen.kt`, `TechniqueHubViewModel.kt` y `design/`;
  - `data/db/VideoAnalysisEntity.kt` (el DAO);
  - `data/repository/VideoAnalysisRepository.kt`;
  - `screens/workout/design/VisualModeAnalysis.kt` (insignias de UI-1a);
  - `screens/workout/components/WorkoutMediaThumb.kt`.

## Por qué
En el Perfil, «Técnica en vídeo» es hoy un `TextButton` de Material con una frase, sin ningún dato. El usuario pidió que esté «como
función en el perfil». El hub funciona, pero parece una lista de ajustes: no enseña los vídeos ni su estado.

## Qué haces

### 1. Datos: los vídeos recientes
- **DAO:** `VideoAnalysisDao.observeRecent(limit: Int)` devuelve las filas ordenadas por `recordedAtMs` descendente, con su `mediaId`,
  `status`, `progressPct`, `confidence`, `repCount`, `exerciseName` y `recordedAtMs`.
  - Es solo una consulta: SIN migración ni columnas nuevas.
- **Repositorio:** `VideoAnalysisRepository.observeRecent(limit)` la mapea a `VideoAnalysisSummary`.
- **Conteo:** el número total de vídeos analizados (estado DONE) sale de una consulta `COUNT` con `Flow`, o de la suma de
  `observeExerciseSummaries()`.

### 2. Perfil: un módulo con contenido
- **Contenedor:** el mismo que sus vecinos (`ProfileCard`, con su título «Técnica en vídeo» y su icono), para que el Perfil siga siendo
  coherente. Es la única excepción a «nada de Material» de esta ronda: el Perfil entero aún usa su sistema. Por dentro:
  - **una tira de hasta 3 miniaturas** de los vídeos más recientes (56 dp, esquinas de 12 dp), cada una con la insignia de UI-1a
    («8», «≈8», «?», «!» o el anillo). Al tocarla se abre su análisis;
  - **una línea de resumen:** «12 vídeos analizados · último el 18 oct»;
  - **la última conclusión**, en una línea: «Sentadilla trasera · ≈ 8 repeticiones · confianza media». Con BAJA: «Sentadilla trasera ·
    vídeo no concluyente»;
  - **la acción** de la tarjeta, como la de «Ver álbumes»: «Abrir técnica en vídeo».
  - **Sin ningún vídeo:** la frase de hoy más una pista: «Graba una serie desde el Modo visual de la sesión o analiza un vídeo de tu
    galería».
- **ViewModel:** `ProfileViewModel` combina, AL FINAL de su `combine`, los vídeos recientes y el conteo, con `ProfileUiState.technique`
  nuevo y con valor por defecto. Si el `combine` ya está al máximo de flujos, anida uno.
- **Navegación:** `ProfileScreen` recibe `onOpenTechniqueAnalysis: (String) -> Unit = {}`, último parámetro, para las miniaturas.
  `MainActivity` lo conecta con `KpknRoute.TechniqueAnalysis.create(id)` en sus DOS llamadas a `ProfileScreen` (~L1203 y ~L1417).
  Solo esa línea en cada una.

### 3. Hub: cabecera y vídeos recientes
- **Arriba**, tras el título, un resumen en `KpknModule`:
  - las cifras de vídeos analizados y de ejercicios, en `KpknType.metricM`;
  - debajo, una tira desplazable de los 10 vídeos más recientes (64 dp, con la insignia de UI-1a; al tocarla, su análisis).
- **Después:**
  - «Analizar un vídeo de la galería», como hoy;
  - «Tus ejercicios», con la última conclusión de cada uno en la segunda línea: «4 vídeos · último 18 oct · ≈ 8 reps»;
  - «Analizar vídeos anteriores (N)», como hoy;
  - «Grabación y análisis», como hoy.
- **Sin datos:** el estado vacío de hoy, con la pista del Modo visual.

## Archivos permitidos
- `data/db/VideoAnalysisEntity.kt`, solo la consulta nueva del DAO, y `data/repository/VideoAnalysisRepository.kt`, solo el flujo nuevo.
- `screens/profile/ProfileScreen.kt`.
- `screens/technique/hub/TechniqueHubScreen.kt`, `TechniqueHubViewModel.kt` y `design/*`, con piezas nuevas si hace falta.
- `MainActivity.kt`: SOLO el parámetro nuevo en las dos llamadas a `ProfileScreen`.
- Pruebas:
  - del DAO y el repositorio (Robolectric con BD en memoria);
  - del hub (`screens/technique/*Hub*`);
  - del Perfil, si existen;
  - las hojas de render del hub (`hub_data`, `hub_empty`) y una escena nueva del módulo del Perfil.

## Pruebas
- **DAO:** el orden por fecha descendente y el límite.
- **Perfil:** el estado con 0, 1 y 4 vídeos, con la última conclusión correcta en cada caso.
- **Hub:** la cabecera con cifras y la tira de recientes.
- **Hoja de render**, al 100 % y al 130 %:
  - el módulo del Perfil vacío y con 3 vídeos (analizando, ≈8 y no concluyente);
  - el hub con datos y vacío.

## Corridas
1. `:app:compileBaseDebugKotlin`
2. `:app:testBaseDebugUnitTest --tests "com.example.kpkn.data.repository.*VideoAnalysis*" --tests "com.example.kpkn.data.db.*VideoAnalysis*" --tests "com.example.kpkn.screens.technique.*" --tests "com.example.kpkn.screens.profile.*"`
3. Las hojas, con `KPKN_RENDER_DIR=C:/kw/shots/vt/ui/4`.
4. `-I C:/kw/tools/dbg-suffix.init.gradle :app:assembleBaseDebug`

## Commits
- `feat(technique): vídeos recientes en el repositorio de análisis`
- `feat(profile): «Técnica en vídeo» como módulo con los últimos vídeos y su estado`
- `feat(technique): el hub con resumen y vídeos recientes`
- `test(technique): hojas del Perfil y del hub`
