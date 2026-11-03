# Mapa · CARDIO EN VIVO (prefijo `cv`, pestaña `cardio-vivo`) — punto 25 / 25.1

Archivos: `src/scenes-cv.js`, `src/cv.css`. 30 escenas, todas interactivas y encadenadas (cualquiera continúa por el recorrido completo).
Ruta de la app: `android-native/app/src/main/java/com/example/kpkn/screens/workout/`.

## 1. Piezas reales de la app
- `CardioLiveCard.kt`: máquina de estados (READY / RUNNING / PAUSED / AWAITING_CONFIRMATION / RECORDED), páginas `CardioNowPage`, `CardioPlanPage`, `CardioSummaryPage`, `CardioReadyView`, `CardioFinishView`, `CardioRecordedView`, hoja `CardioManualHeartRateSheet`.
- `CardioLiveSpace.kt`: `CardioClockPanel` (recuadro del reloj + figura), `CardioTape`, `CardioProfile`, `CardioStatusLine`, `CardioChip`, `CardioDock`, `CardioPauseVeil`, `CardioCountdown`, `CardioTransitionIn/Out`, `CardioSwipeToStart`, `CardioRpePicker`.
- `components/CardioActivityFigure.kt` (figura de palitos: SE ELIMINA), `WorkoutV2Body.kt` (host: cabecera + `stageBottomClearance` con `dockBottomClearance = 140.dp`), `WorkoutScreen.kt:1052` (`emptyCardioDock`), `LiveSessionListView.kt:1741` (`ListCardioBlock`), `RestLiveCard.kt:151` (aviso «pasas al espacio de cardio»).

## 2. Inventario escena → pieza
| Escena | Pieza actual |
|---|---|
| cv-flujo / cv-descanso | RestLiveCard + RestRef02 (existente, otro paquete) |
| cv-trans-in | `CardioTransitionIn` |
| cv-listo, cv-listo-gps | `CardioReadyView` |
| cv-cuenta | `CardioCountdown` |
| cv-calentamiento, cv-trabajo, cv-sobre-zona, cv-bajo-zona, cv-recuperacion, cv-continuo, cv-pulso-manual, cv-series, cv-gps* | `CardioNowPage` + `CardioClockPanel` + `CardioDock` |
| cv-hoja-pulso | `CardioManualHeartRateSheet` |
| cv-plan / cv-datos / cv-datos-sin-pulso | `CardioPlanPage` / `CardioSummaryPage` |
| cv-pausa / cv-bloqueo | `CardioPauseVeil` / `CardioHoldToUnlock` |
| cv-terminado (+vacio, +error) | `CardioFinishView` |
| cv-registrado | `CardioRecordedView` |
| cv-trans-out | `CardioTransitionOut` |
| cv-lista | `ListCardioBlock` |
| cv-ondas | nueva (sustituye `CardioActivityFigure`) |

## 3. Diferencias app actual → diseño nuevo (la implementación debe ser 1:1; lo que no está aquí se elimina)
**Estructura general**
1. Título duplicado: la cabecera del host ya pone «Escaladora» + micrófono; `CardioNowPage`/`CardioReadyView` lo repetían (Syne 17). **Se elimina el segundo.** Queda: título (host, 26 px) + línea de contexto con punto de zona.
2. Línea de contexto: antes «Continuo · Paso 1 de 1». Ahora, con un solo paso, «Continuo · 20 min» (sin «Paso 1 de 1»); con varios, igual que hoy («Trabajo · Vuelta 3 de 4 · Paso 6 de 9»).
3. Durante transiciones, cierre y registrado, el host NO pinta el título/micro (`showExerciseContext=false`); solo la isla.
4. El recuadro carbón del reloj (`CardioClockPanel`, radio 24, borde, velo de zona) **desaparece**: la cifra va sobre el negro continuo con las ondas detrás.
5. **Figura de palitos y `CardioActivityFigure`: se eliminan.** Las consignas de la máquina (velocidad, inclinación, «Objetivo 6:10 /km», «GPS activo/Sin señal GPS/GPS en pausa») pasan a dos fichas de vidrio sobre las ondas.
6. Cifra: 80 px Syne 800 (68 con 5 caracteres, 54 con 6+), escalada al ancho (`cqw`), tabular. Encima, una línea: «restante del paso · quedan 15:37» / «tiempo restante» / «km restantes · de 5,00 km». Debajo del hero, barra fina de avance del paso con punto de zona (antes, barra de 3 dp dentro del recuadro).
7. Fondo: `cardio-bg` radial se mantiene (14 %), más las ondas animadas (ver §4).
**Pulso / zona**
8. `.vitals` + `CardioTape` + `CardioStatusLine` (3 piezas sueltas) → **un módulo «Pulso»**: eyebrow «Pulso · reloj|manual», lpm en Syne 36 con corazón que late al ritmo real, «Objetivo · Z4 / Umbral / 152–171 lpm», cinta con aguja, línea de estado. Aviso (sobre/bajo zona): el borde del módulo toma el color de la zona real.
9. Sin pulso: «Sin datos de pulso» ya no es una cápsula-error; el módulo muestra «— sin pulso», «Guía por esfuerzo · RPE 5–6 · Algo duro», cinta de 10 tramos y la acción **Añadir pulso** (borde, 44 px) a la derecha. Se elimina la ficha «Añadir pulso» del `FlowRow` de chips y la ficha «Reloj».
10. Pulso manual: toda la zona del lpm es un botón (lápiz); «anotado hace 0:35» en la línea de estado (antes ficha «Manual · hace 0:30»).
11. Se elimina la fila de chips (`FlowRow`) completa; sus datos se reparten (consigna → hero; GPS → hero; pulso → módulo).
**Métricas y plan**
12. Nuevo módulo de 2 celdas con datos que ya se calculan: cinta = Distancia ≈ / Energía ≈; continuo = Activo / Energía ≈; GPS = Ritmo / Activo. Solo si el dato existe (sin celdas «—»).
13. `CardioChip(Siguiente)` + `CardioProfile` → módulo «Plan del cardio»: perfil con posición, «24:23 / 40:00» y fila «Siguiente ● Recuperación 3 · 3:00 · 7,0 km/h». Cardio de un solo paso: sin este módulo (la barra del hero y «Activo» bastan).
14. En 360×740 y varios pasos se oculta el módulo de métricas (viven en Datos).
**Panel inferior y páginas**
15. `CardioDock`: tres puntos sin nombre arriba → **pestañas con texto «Ahora · Plan · Datos»** con indicador que se desliza; vidrio (`pl-glass`), 124 dp de alto en 880 (112 en compacto), pegado al inset inferior + 20 dp (antes quedaba a ~190 dp del borde por el `dockBottomClearance` de 140 dp del host y cortaba el contenido). El contenido pasa por debajo del vidrio.
16. Controles: 56 dp de alto; Pausa/Reanudar crema (1,5×), Bloquear y Saltar paso a un lado. Saltar paso solo en planes con intervalos (igual que hoy). Pulsar = onda de contacto.
17. Swipe horizontal: vale sobre el contenido y sobre las pestañas; sigue al dedo, resistencia en los extremos, umbral 50 px, asienta con deslizamiento (420 ms) + fundido; el contenido de la página entra escalonado. Antes cambiaba de página sin transición.
18. «Resumen» pasa a llamarse **«Datos»**; título de página propio (Syne 18 + «medidos hasta ahora»).
19. Plan: lista como línea de tiempo (rail con puntos; hecho = ✓ verde atenuado; actual = resaltado con progreso; resto normal) + perfil grande arriba. Datos: 4 celdas, tiempo por zona (5 barras), % en zona; sin pulso → bloque punteado con acción.
**Estados y flujos**
20. Pausa: velo con desenfoque (10 px) en vez de negro 85 %; ondas congeladas y apagadas; botón Terminar cardio visible (hoy `RestRef02Button` secundario casi invisible).
21. Bloqueo: contenido y cabecera al 50 %, pestañas apagadas, etiqueta flotante «Pantalla bloqueada», anillo de 58 dp que se llena en 1,2 s.
22. Avisos GPS: «Sin señal GPS · seguimos con el tiempo» y «Ubicación no disponible» + «Reintentar permiso» son un aviso de UNA línea (44 dp) sobre el hero; con aviso, el hero se acorta en 360×740 (128 dp) para que todo quepa sin ocultarse bajo el dock. Hoy era un texto de 11 px bajo el dock.
23. Antes de empezar: «Ver el plan» es una píldora de vidrio con contenedor (también en «Ahora vamos con cardio»); mismas tres comprobaciones con estado a la derecha (Listo / RPE / Revisar), primer bloque, perfil, «Ver el plan»; «Empezar» en panel de vidrio inferior. Se elimina el título repetido y la figura.
24. Cuenta 3-2-1: número 168 px con anillo en la zona y ondas despertando; subtítulo en cápsula.
25. Transiciones: titular 34 px (antes 34 sp con 44 de interlineado), línea de trayecto que se dibuja y se enciende en la zona, ondas a pantalla completa que se levantan (2,2 s), tarjeta que sube, deslizador de vidrio con brillo. Con «Reducir movimiento»: estado final directo.
26. Cierre: ondas que se aquietan arriba; RPE: etiqueta «RPE 7 · Duro» al marcar; plan = raya bajo el número; «Guardar cardio» en panel de vidrio; error visible bajo el RPE. Celdas editables con borde + lápiz.
27. Registrado: «RPE real» en la celda de esfuerzo; «Actualizar cardio» en panel de vidrio.
28. Lista: bloque sin cambios de contenido; solo el botón y el perfil se alinean al lenguaje nuevo.

## 4. Ondas (técnica de `covers.js` «ondas», en movimiento)
Crestas apiladas con relleno negro (se tapan), trazo crema, **una línea con el color de la zona real** (la de tu pulso; sin pulso, la del plan) y su resplandor. Parámetros por estado (`CVW`): amplitud, velocidad, frecuencia, aspereza, intensidad. Estados: idle, warm, cont, work, recover, cool, above (áspera, coral), below, pause (congelada, 42 %), done. La velocidad sale de `cardioFigureCadence(kind, zone, moving, rpm)` (ya existe); el latido, de `hr`. Transición entre estados: interpolación exponencial (≈ 0,3 s). Movimiento reducido: una sola pose fija con los parámetros del estado. Dibujo: `Canvas` con `Path` por línea (18 líneas × ~72 puntos) — ligero, sin blur; el resplandor es un trazo ancho al 22 %.

## 5. Cambios de motor / datos
- Ninguno de Room. `WorkoutV2Body`: el cardio deja de reservar `dockBottomClearance` (140 dp) y de pintar el título propio; `showExerciseContext=false` en transiciones/cierre.
- Ritmo/pulso: `CardioGpsState.paceSecondsPerKm` y `liveHeartRateBpm` ya existen. FC media viva en pantalla Ahora: no se muestra (solo en Datos).
- Energía ≈ requiere peso corporal (hoy igual); distancia ≈ en cinta solo con velocidad por bloque.
- Zonas sin `maxHeartRateBpm`: la aguja y el rango se dicen en % FC máx (sin cambios; la maqueta usa FC máx 190).

## 6. Dudas para el usuario
1. **Conectar reloj** desde «Sin datos de pulso»: hoy no existe en el cardio en vivo (solo flavor `health`). No lo dibujé; ¿lo quieres?
2. Nombre de la tercera página: «Datos» (propuesta) o «Resumen» (actual).
3. «Parciales por km» del GPS (`kmSplitPaces` existe y no se muestra): ¿en Datos?
4. Terminar cardio sigue siendo «pausa → Terminar cardio» (como hoy). ¿Atajo directo en el panel?
5. La isla de sesión y la tarjeta de descanso son piezas de otros paquetes; aquí solo se dibujan como contexto (el texto largo de la isla se trunca igual que hoy).
