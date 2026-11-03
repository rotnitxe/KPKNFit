# Informe de implementación · VIVO-RESTO (`lx`) · 30-oct-2026

Worktree `C:\kw\pu-lx`, rama `pulido/lx` (fusionada con `feat/pulido-v1` en ef45c55cf, sin conflictos; SIN recompilar tras esa fusión). Sin commit.

## Qué hay (en orden de prioridad)
1. **Hoja «Tiempo»** (punto 16): Sesión · Cronómetro · Temporizador. Dominio puro `domain/workout/FreeClock.kt` + `FreeClockController.kt` (16 pruebas verdes por el carril JVM: `FreeClockTest`, `FreeClockControllerTest`). Todo se calcula con hora de inicio/fin; el fin del temporizador lo dispara una alarma del sistema (`services/workout/FreeClockAndroid.kt`, receptor nuevo en el manifiesto) con sonido y vibración ×3 y notificación; sobrevive a pantalla apagada y a matar el proceso (SharedPreferences). La píldora pasa a dos líneas; la franja «Listo» (vidrio) persiste y al tocarla abre la hoja en Temporizador. Si el descanso termina a la vez, suena primero el descanso y el temporizador 1,5 s después (`RestEndSignal`, 2 líneas en `RestTimerController`).
2. **Sesión completada** (punto 18): hoja única sin scroll con niveles A/B/C (`FinishSheetModel.kt`, `FinishSheetUi.kt`, `RecordedSessionFinishSheet.kt` reescrito, misma firma + `plannedSets`, `targetMinutes`, `errorMessage`). Panel de Anotaciones que entra por la derecha. Hoja de compartir: vista previa grande con resplandor, plantillas como miniaturas reales, «Guardar imagen» solo icono. `finishNumber` ahora da «8.420».
3. **Modo Lista** (punto 19): máscara 24 dp arriba (desde y = 0) y 28 dp abajo hasta el borde físico (`LiveListEdgeMask.kt`), `contentPadding` de abajo = inset de navegación + 24; la lista ya no cede el hueco del dock; cápsula con `kpknShadow`/`vidrio-isla`/tinte 68 % (Lista) y plana con `flotante` (Tarjetas).
4. **Modo visual** (punto 17): «Expandir» y «Plegar» como píldoras con nombre; se quita el «Álbum» del overlay y el «Ampliar» de la Lista; sombra `modal` y velo .62.

## Números (render Robolectric 360×880 contra el lienzo; error medio/255 · % píxeles > 28)
Hoja Tiempo: sesión 17,1 · 11,4 %; cron-cero 6,1 · 4,5; cron-vueltas 8,6 · 6,7; cron-pausa 8,6 · 6,5; temp-config 23,5 · 12,4; temp-corriendo 4,4 · 3,7; temp-fin 4,8 · 4,1.
Cierre: normal 14,4 · 10,6; record 17,4 · 13,0; cardio 15,2 · 10,9; corta 10,7 · 8,2; vacía 47,3 · 33,9; guardando 14,4 · 10,5; error 15,9 · 11,7; notas 10,0 · 7,2; nivel B 13,3 · 9,9; nivel C 11,6 · 8,5.
**NINGUNA escena llega al objetivo de 2,5.** Causa conocida: un desplazamiento vertical de ~8 dp en las cifras grandes (que se acumula hasta ~14 dp en columnas densas como el teclado) que NO pude resolver (probé `LineHeightStyle.Center`, sin efecto), más la rasterización del texto. `vacía` (47) además tiene otra altura de hoja (auto) y no se arregló tras la última corrida. Escenas `lx-f-historia*`, `lx-l-*` y `lx-v-*` NO se compararon con el lienzo (solo se dibujan con las hojas existentes `LiveStoryEntryTest`, `LiveListRenderSheetTest`, `LiveCardRenderSheetMediaTest`; PNG en `paridad/capturas/lx-fotogramas/`).
Fotogramas (0/25/50/75/100 %): fin del temporizador (350 ms de paso hasta 1,4 s), franja «Listo» (80 ms hasta 320) y entrada de la hoja de cierre (0–2,4 s) dibujados; no se compararon con el lienzo (solo cableado y aspecto).

## Matriz
`paridad/lx.md`: 69 filas, 50 `[x]` y 19 `[~]` (DESV-700, 701, 702, 703, 705, 706, 708). `paridad-check lx` en verde. Varias capturas de DESV son una lado-a-lado genérica, no la específica de la fila. El vidrio y los filtros de desenfoque no se miden en Robolectric (DESV-100/101): comprobar en el Flip5.

## Pruebas
44 ejecutadas por la cola en la última corrida: 40 verdes (las de `VivoRestoRenderSheetTest`, `RedesignFinishSheetTest`, `LiveStoryEntryTest`, `LiveHeaderTouchTargetsTest`, `LiveListRenderSheetTest`) y **4 rojas en `LiveCardRenderSheetMediaTest`** (`analisisEnLaTarjeta100/130`: la línea de análisis mide 11/28 en vez de 48; `modoVisualExpandido100/130`: no encuentra el texto «Foto»). Salieron igual con la cabecera de la cara sin cambiar de alto, así que probablemente no vienen de mi cambio (la rama ya trae la tarjeta del paquete `lv`), pero NO lo pude comprobar contra la base. Se borró `Cal3SummaryUiTest` (el resumen de calibración ya no cabe en la hoja del lienzo; la decisión se sigue guardando). Se actualizaron `RedesignFinishSheetTest` y `LiveStoryEntryTest`. `WorkoutFinishPages.kt` se conserva (código sin uso, pero lo usan `FinishPhotosNavigationTest` y `LiveQaLayoutTest`).

## Abierto / riesgos
- Falta la pasada de números hasta ≤ 2,5 y el offset vertical; falta comparar historia, Lista y modo visual contra el lienzo.
- La franja «Listo» empuja el contenido (DESV-701); la hoja de cierre usa la animación de `ModalBottomSheet` (DESV-702).
- El aviso de «No se pudo guardar» sale inline porque `WorkoutScreen` ya no lo muestra como toast con la hoja abierta (3 líneas tocadas ahí).
- Tras fusionar ef45c55cf no se recompiló.
