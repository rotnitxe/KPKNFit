# tour-a · Motor del recorrido v2 (Opus high)

Worktree `C:\kw\gu-tour`, rama `guia/tour`. Plan: «Fase 2» de `PLAN.md` (puntos 1–11). El diagnóstico de los 13 fallos actuales está en el «Contexto» del plan.

## Qué construir
Rehaz `ui/tour/` como máquina de estados (Inactivo → Esperando → Preparando → Mostrando → En pausa → Terminado) con los puntos 1–11 del plan:
- `TourRequest(id, steps, ready, route, prepare)` composable que las pantallas usan en vez de `LaunchedEffect { startTour }`; la petición vive mientras la pantalla está compuesta.
- Puertas: `ready`, ruta actual (lee la ruta del NavController de `MainActivity`), `KpknModalTracker.openCount == 0`, sin diálogo de permisos ni overlays del alta, primer ancla medida y estable (sin cambios ≥ 250 ms y no vacía).
- «Visto» solo si se mostró ≥ 1 paso. Ancla perdida → pausa hasta 4 s y luego salta solo ese paso. Cambio de ruta → suspende sin marcar.
- Registro con `LayoutCoordinates` vivas (rect en ventana sin recorte, recalculado por cuadro mientras se asienta) + `BringIntoViewRequester` por ancla; `prepare` por ancla para listas perezosas, expandir o cambiar de pestaña.
- Capa propia en `MainActivity` (zIndex 450, fuera de `KpknOverlayHost.entries`), oculta y en pausa con hojas abiertas; «Pruébalo» que abre una hoja continúa al cerrarla.
- Estado en un ViewModel de la Activity con `SavedStateHandle` (sobrevive a plegar y girar). Cola de peticiones. TalkBack: lo de debajo oculto mientras hay recorrido, `liveRegion` en la burbuja.
- Ids `-v2`; quita `HOME_RINGS`, `conTorre` y `Settings.hasSeenHomeTour` (comprueba antes que no tiene lectores). «Repetir la guía» de Ajustes: reiniciar + ir a Inicio + arrancar (cambio mínimo en `SettingsScreen`/`SettingsViewModel`).
- Migra las 6 pantallas actuales a `TourRequest` con su señal real de carga (busca en cada ViewModel) y los guiones actuales, quitando los pasos con anclas inexistentes (`workout.siguiente`). Los guiones definitivos y nuevas anclas vienen después del lienzo: no los inventes.
- Capa visual: deja `KpknTourOverlay` funcionando sobre el motor nuevo con el aspecto actual, corrigiendo solo la colocación (burbuja anclada abajo si el ancla no deja sitio, altura máxima con scroll, insets e IME, ancho máx. 420 dp). El diseño final llega con el lienzo.

## Archivos permitidos
`ui/tour/**`, `ui/components/KpknOverlayHost.kt`, `MainActivity.kt` (montaje, ruta, puertas), las 6 pantallas solo en su línea de arranque del recorrido (`HomeScreen.kt`, `ProgramDetailScreen.kt`, `WorkoutScreen.kt`, `NutritionScreen.kt`, `BodyProgressScreen.kt`, `ProfileScreen.kt`), `SettingsScreen.kt`/`SettingsViewModel.kt` (solo «Repetir»), `data/models/Settings.kt` (solo `hasSeenHomeTour`), y pruebas `ui/tour/**`, `render/KpknTourRenderSheetTest.kt`.

## Pruebas
Reescribe `TourControllerTest` (puertas, pausa, reanudar, cola, ruta, nunca «visto» sin mostrarse), `KpknTourOverlayTest` (colocación: ancla enorme, borde inferior, letra 130/200 %), nuevo `TourAnchorCoverageTest` (lee los .kt de `src/main` y exige que cada `TourAnchors.X` usado en `TourScripts` aparezca en exactamente un `tourAnchor(TourAnchors.X)`). Una corrida Android con todas las clases juntas; `:app:compileBaseDebugKotlin` incluido implícitamente.
