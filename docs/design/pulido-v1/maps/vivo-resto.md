# Mapa · paquete VIVO-RESTO (prefijo `lx`, pestaña «En vivo · más»)
Puntos 16–19 del 29-oct. Escenas en `src/scenes-lx.js`, estilos en `src/lx.css` (todo con prefijo `lx-`). Compilar con
`node build.cjs --out=shots/lx`; `check --prefix=lx` da 0 problemas y las 29 escenas se ejecutan sin errores de JS.

## 1. Piezas reales de la app (rutas bajo `screens/workout/`)
- **Cápsula y píldora de tiempo**: `WorkoutHeaderBar.kt` → `WorkoutHeaderBar` (isla de 60 dp, `kpknFloatingSurface`) y `WorkoutChronometer`
  (píldora `0:47` con icono de cronómetro; hoy solo es el **tiempo de la sesión**: `elapsedSeconds = now − startTimeMs`, no se puede pausar).
- **Hoja «Tiempo de sesión»**: `components/SessionTimeSheet.kt` (`SessionTimeSheetContent`, `PaceProjectionLines`), `LiveSheetKit.kt`
  (chips/filas), `PacingAlertMode` y `SessionTimeCues` (avisos), `KpknNativeTimePickerDialog` («Otro…»), `projectSessionPace`.
- **Descanso** (no se toca): `RestTimerController.kt`, `RestClockMath.kt`, `components/RestRef02.kt`, `WorkoutRestOverlay.kt`.
- **Modo visual**: `components/SetCardExerciseMediaBack.kt` (`SetCardExerciseMediaBack`, `Stage(compact)`, `ExpandedMediaOverlay`,
  `ExpandGlyph`, `MediaRef02TopPill`). En la cara compacta (`compact = true`) **no hay ningún botón que ponga `expanded = true`**: solo existe el de
  plegar dentro del overlay. En la Lista, `LiveSessionListView.kt` ~1179–1360 (`EDITOR_FACE_VISUAL`, botón suelto «Ampliar»).
- **Cierre**: `RecordedSessionFinishSheet.kt` (`SummaryTab`, `ProgressTab`, `NotesTab`, `MetricCell`, `LiftRow`, `Sparkline`, `KpknSwitch`),
  `WorkoutFinishPresentation.kt` (`FinishPresentationContext`, `buildFinishLiftProgress`, `finishNumber`, `finishDeltaLabel`),
  `WorkoutFinishController.kt` (`FINISH_EMPTY_SESSION_GUIDANCE`, `FINISH_SAVE_FAILED_MESSAGE`), `WorkoutScreen.kt` ~1430 (uso).
  `WorkoutFinishPages.kt` (`FinishPagerLayout`, 5 páginas) **parece código muerto**: no lo llama nada; conviene retirarlo al implementar.
- **Historia**: `WorkoutStorySheet.kt`, `WorkoutStoryRenderer.kt` (`WorkoutStoryTemplate` General/Ejercicio/Fotos, `WorkoutStoryOptions`),
  `screens/stories/WorkoutStory.kt` (`workoutStoryFrom`), `WorkoutShareService.kt`.
- **Lista**: `LiveSessionListView.kt` 541–566 (máscara `DstIn`: arriba `fadeEnd = max(topInset, 56 dp)` con 40 dp de degradado; **abajo 72 dp**),
  `WorkoutV2Body.kt` 546–583 (`padding(bottom = dockBottomClearance)` → la lista termina antes del borde y el degradado queda ahí),
  `WorkoutScreen.kt` 1027 (`dockBottomClearance = 16.dp` en Lista), `WorkoutHeaderBar.kt` 445–452 (cápsula plana opaca).

## 2. Inventario escena → pieza
| Escena | Pieza |
|---|---|
| `lx-t-sesion` | `SessionTimeSheetContent` (todo lo de hoy) + esfera nueva |
| `lx-t-cron-cero / -vueltas / -pausa` | **nuevo**: cronómetro libre (sin pieza hoy) |
| `lx-t-temp-config / -corriendo / -fin` | **nuevo**: temporizador libre |
| `lx-t-coexiste` | `WorkoutHeaderBar` (píldora de 2 líneas) + aviso; descanso intacto |
| `lx-v-tarjeta / -expandido / -sin-camara` | `SetCardExerciseMediaBack` / `ExpandedMediaOverlay` |
| `lx-v-lista` | fila de editor de `LiveSessionListView` con cara visual |
| `lx-l-reposo / -scroll / -medidas` | `LiveSessionListView` + `WorkoutHeaderBar` en Lista |
| `lx-f-normal / -record / -cardio / -corta / -vacia` | `RecordedSessionFinishSheet` (Resumen + Progreso) |
| `lx-f-guardando / -error` | `saving` y `FINISH_SAVE_FAILED_MESSAGE` |
| `lx-f-notas` | `NotesTab` (notas, tareas, esfuerzo, fotos y medios) |
| `lx-f-nivel-b / -nivel-c` | degradación sin scroll (nuevo) |
| `lx-f-historia / -ej / -fotos / -error` | `WorkoutStorySheet` |

## 3. Decisiones y razones
- **16 · Cronómetro**: «el cronómetro» de hoy es la píldora del tiempo de sesión. Decisión: **no se quita**; la hoja pasa a tres pestañas
  (Sesión · Cronómetro · Temporizador). Sesión conserva cada control y gana esfera con arco de objetivo. Cronómetro y temporizador son **libres**
  (independientes de la sesión y del descanso) y se calculan con la hora de inicio/fin, no con un contador.
- Cronómetro: esfera de 60 s con segundero y subesfera de 30 min, cifra grande con décimas, **Vuelta/Reiniciar + Iniciar/Pausar/Seguir** en sitio fijo,
  vueltas con parcial y total y marca de más rápida/lenta con **texto** además de color. Reiniciar sin confirmar (es desechable).
- Temporizador: **teclado numérico + 6 atajos** (en vez de rueda: un 2:45 exacto sin scroll fino), anillo que se vacía, +15 s/+30 s/+1 min, hora de fin,
  Sonido y Vibración (ambos activos). Al terminar: anillo completo, «Se acabó el tiempo», Repetir/Cerrar.
- **Coexistencia**: el reloj libre nunca toca el descanso. Con algo en marcha, la píldora pasa a **dos líneas** (sesión arriba, reloj libre debajo).
  Aviso con la hoja cerrada: franja de vidrio con «Listo», persistente; si coincide con el fin del descanso, suena primero el descanso y el otro 1,5 s después.
- **17 · Expandir**: botón con **nombre** (icono + «Expandir») en la fila de cabecera de la cara, a la derecha de «Álbum»; «Plegar» es su gemelo en el
  overlay (hoy glifo sin texto). Se quita el «Álbum» del encabezado del overlay porque «Ver álbum» ya está en el escenario (duplicado). En la Lista se
  quita el «Ampliar» suelto (la cara compartida ya trae «Expandir»): un solo nombre.
- **18 · Cierre sin scroll**: las tres pestañas desaparecen. Hero = volumen (Syne, cuenta hacia arriba) + tendencia de las últimas sesiones iguales;
  cuatro anillos (Duración, Series, Esfuerzo, Récords); músculos con barras (directas/total); mejoras como etiquetas; franja coral solo con récord;
  fila de cardio solo si lo hubo; historia con miniatura real + «Compartir al guardar»; Notas · Valorar · Fotos; Guardar; Volver. Anotaciones = panel
  lateral dentro de la misma hoja. **Niveles A/B/C** por alto útil ÷ escala de texto (A ≥ 760, B 600–759, C < 600); récord, cardio o error bajan un nivel.
  Contenido mínimo que nunca se esconde: nombre, volumen, anillos, historia, Notas/Valorar/Fotos, Guardar, Volver.
- Compartir: vista previa grande 9:16, plantillas como **miniaturas**, «Guardar imagen» pasa a botón **solo con icono** (accesible con etiqueta) para que
  «Compartir en historias» quepa entero a 360 dp y al 130 %.
- **19 · Lista** (contrato en `lx-l-medidas`): lista a pantalla completa; `contentPadding` arriba = cabecera + 14 dp, abajo = inset de navegación + 24 dp;
  desvanecido **24 dp arriba (desde y = 0)** y **28 dp abajo (hasta el borde físico)**; cápsula en vidrio con `hazeEffect` (22 dp, tinte #1B1B1D ~68 %),
  lista como `hazeSource` antes de la máscara. **Tarjetas no cambia** (cápsula plana).

## 4. Qué exige en motor/datos
- Reloj libre: estado nuevo en `WorkoutViewModel` (cronómetro: inicio/acumulado/vueltas; temporizador: fin/total/estado), guardado en `SavedStateHandle`
  para sobrevivir al giro; aviso por vibración y sonido con arbitraje frente a `RestTimerController`. Sin Room. Opcional: notificación del servicio.
- Cierre: pasar `plannedSets` (ya existe `totalWorkingSetCount`), `targetMinutes` (anillo de duración) y un resumen de cardio desde
  `cardioDetails` y las series de cardio (`timeSeconds`, `distanceKm`, `avgHeartRate`, `calories`). Resto ya se calcula. Unificar
  `finishNumber` (hoy devuelve «8 420» con espacio) a «8.420».
- Historia: miniaturas de plantilla = render a baja resolución en caché por plantilla. Nivel A/B/C: leer `LocalConfiguration.screenHeightDp` y `fontScale`.
- Expandir: añadir el botón a la cara compacta (`expanded = true`; `ExpandedMediaOverlay` ya anima desde `faceBounds`); desactivado mientras graba.

## 5. Dudas que de verdad hay que decidir
1. ¿Te vale que el cronómetro y el temporizador vivan **en la hoja del tiempo de sesión** (pestañas) en vez de en el menú ⋯?
2. ¿Sonido del temporizador = el mismo cue que el fin de descanso, o uno propio?
3. Anillo de Duración sin objetivo de tiempo: dibujé «vuelta de reloj de 60 min». ¿Lo prefieres sin arco cuando no hay objetivo?
4. «Guardar imagen» solo con icono: ¿de acuerdo o prefieres texto y dos líneas?
5. La cápsula de vidrio solo en Lista (Tarjetas sigue plana): ¿o también en Tarjetas por coherencia?
