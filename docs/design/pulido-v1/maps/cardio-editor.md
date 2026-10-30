# Paquete CARDIO-EDITOR · mapa (prefijo `ce`, pestaña «Cardio · editor») · puntos 5, 6, 7, 8, 9, 11, 26
Código: `src/scenes-ce.js`, `src/ce.css`. Auditoría de «Avanzado»: `maps/cardio-avanzado.md`. Todo en `screens/sessioneditor/` salvo indicación.
Las escenas son **una sola maqueta navegable** (la lista, las tarjetas y las hojas se enlazan): cada escena es un estado inicial. Mantén pulsada una fila de cardio (o clic derecho) para abrir su menú.

## 1 · Inventario escena → pieza de la app
| Escena | Pieza real que sustituye o rediseña |
|---|---|
| `ce-anadir` | `components/CardioCatalogSheet.kt` (`CardioCatalogSheet`, `CardioCatalogTile`, grupos `EN INTERIOR / AL AIRE LIBRE / SPRINT Y POTENCIA`) |
| `ce-cambiar` | La misma hoja con `isReplacing = true` (`openCardioPicker(partId, exerciseId)` → `Exercise.withCardioItem`) |
| `ce-ilustraciones-ref` | `design/CardioTypeIcon.kt` (`cardioTypeIconSpec`): hoy un icono de línea o glifo de patrón por tipo |
| `ce-lista`, `ce-solo` | `SessionEditorScrollRenderer.kt` (`CardioDivider`, `CardioPlacement`, `CardioAddActions`, `PartAddExercise`), `SessionListItems.kt`, `components/SessionEditorEmptyState.kt` (`SessionEditorCardioPlacementControl`), `SessionEditorViewModelCardio.kt` |
| `ce-continuo`, `ce-continuo-gps`, `ce-series`, `ce-intervalos` | `components/CardioEditorCard.kt` (`CardioEditorCard`, `CardioSteadyForm`, `CardioSeriesForm`, `CardioAdvancedPanel`, `CardioHistoryPanel`, `CardioTemplatesRow`), `CardioIntervalsEditor.kt`, `CardioFormSwitch.kt`, `CardioDoseLine.kt`, `CardioMagnitudeCell.kt`; fila cerrada en `ExerciseEditorCard.kt` + `Thumbnail` |
| `ce-duracion`, `-teclado`, `-error`, `ce-esfuerzo`, `ce-calentamiento`, `-libre`, `ce-distancia` | `components/CardioMagnitudePickers.kt` (`CardioMinutesWheelDialog`, `CardioMinutesSecondsWheelDialog`, `CardioSecondsWheelDialog`, `CardioDistanceWheelDialog`, `CardioPaceWheelDialog`, `CardioIntWheelDialog`, `CardioTenthsWheelDialog`) sobre `NativeWheelPicker` |
| `ce-zona` | picker `HR` (`CardioIntWheelDialog` «% FC», 50–100) |
| `ce-protocolo`, `ce-avisos`, `ce-avisos-continuo` | `CardioAdvancedPanel` (series, pausa, objetivo, avisos y pantalla) |
| `ce-plantillas` | `CardioTemplatesRow` + `CardioFeaturedTemplates` (7) y los patrones de `CardioIntervalsEditor` (`CardioIntervalPrograms.specs`, 7) |
| `ce-menu`, `ce-menu-eliminado` | `components/sheets/TemplatesAndQuickActionSheets.kt` (`ExerciseQuickActionRows`) y `SessionEditorViewModelQuickActions.kt` (duplicar, borrado blando) |
| `ce-ficha`, `ce-ficha-exterior`, `ce-fichas-textos` | `components/ExerciseFicha.kt` (hoy «Sin imagen revisada…» + «Ver progreso» para un cardio) |

## 2 · Decisiones y propuestas (cada una con su razón)
- **Miniaturas (5)** `new`: 12 ilustraciones a medida, lienzo 64, trazo redondeado, dos tintas (máquina crema apagada, persona azul cardio) y una figura con proporciones fijas. Hoy: elíptica = icono de pulso, SkiErg = patrón de tracción, caminata = figura de pie, Air Bike = llama. Se usan en la hoja (84 px), en la fila (56), en el menú (60) y en la ficha (168, trazo más fino para que no pese). El icono de línea de 24 px sigue donde ya vive (chips, resúmenes).
- **Hoja de añadir** `keep/move`: mismas 12 modalidades y tres grupos; 2 columnas con la ilustración sobre un «ventanal» y la descripción completa (sin «…»); GPS como etiqueta sobre la ilustración; «+» pequeño. En «Cambiar» la actual lleva marca (`new`, trivial: el tipo ya se conoce).
- **Tarjeta (6, 11)** `new`: orden Forma → magnitudes → esfuerzo → objetivos → accesos. Se quita la dosis repetida en grande (la fila ya la dice), el chip «Al final», el texto «Añadir cardio» y el carrusel de plantillas incrustado (pasa a la hoja «Plantillas»). Esfuerzo = 10 barras con las zonas reales del cardio en vivo (`CardioZoneColors`, zona = (RPE+1)/2) en vez del deslizador de puntos.
- **Series**: cinco filas (calentamiento, esfuerzo, pausa, rondas, enfriamiento) + gráfica con el total. Omitir/Libre → modos del selector. Lo raro (series, entre series, pausa activa/pasiva, objetivo kcal/m) → hoja «Protocolo».
- **Intervalos** `move`: tres vistas del mismo dato (gráfica, «peldaños», fila por bloque) → gráfica tocable + inspector. Las acciones de la fila (duplicar, mover, quitar) siguen en el «⋯» del inspector. El inspector solo ofrece magnitudes que la máquina admite (flags de `CardioCatalogItem`).
- **Selector (8)** `new`: cifra grande tocable (teclado numérico), regla con arrastre, −/+, atajos y Aplicar/Cancelar. Un único control para tiempo (min y m:ss), distancia, velocidad, inclinación, cadencia, vatios y ritmo; límites actuales (0–180 min, 0–50 km…). Estados: normal, Libre, escribiendo, error, desactivado (modo Libre/Omitir).
- **Avanzado (7)** `out`: ver `cardio-avanzado.md`. Reparto: Objetivos (continuo), Protocolo, Avisos y pantalla.
- **Menú de pulsación larga (9)** `new`: Ficha, Cambiar máquina o lugar, Duplicar, Pasar el cardio antes/después de la fuerza, Eliminar. `out`: «+ Aproximación» y «+ Movilidad» (series de calentamiento/movilidad que el cardio en vivo nunca ejecuta; el calentamiento real es la fase de Series/Intervalos), «Crear superserie» (el cardio va en su propio espacio), la descripción (pasa a la ficha) y el subtítulo «1 × 8 · 2:00» (ahora la dosis real).
- **Ficha (9)** `new`: ilustración, qué es, qué entrena, zonas y RPE reales, formatos (plantillas existentes con su forma) y técnica/seguridad. Contenido nuevo para las 12 (ver `ce-fichas-textos`). «Ver progreso» sube arriba.
- **Marco del editor**: isla superior («‹ · ‹ Jue · Semana 3 ▾ › · ⋯», vidrio) y barra inferior (anillo «N min de 90» según el cardio añadido, «Añadir» partido con flecha, Asistente con insignia; sin «Reglas») copiados de `ed-lista` con clases `ce-*`; solo es contexto, lo define el paquete EDITOR.
- **Posición Al inicio / Al final (26)** `move`: no se quita. El selector grande y el chip desaparecen; el rótulo del espacio dice «CARDIO · AL FINAL» y lleva una flecha pequeña que lo pasa antes o después de la fuerza, con aviso que explica el efecto; la misma acción está en el menú. Solo aparece si la sesión tiene fuerza y cardio.
- **Añadir cardio** `ask`: el alta sale del «+» del rótulo y del menú de la barra «Añadir» (ver duda 1).

## 3 · Qué exige implementarlo (motor y datos)
- Sin migración de Room ni de JSON: todos los campos de `CardioDetails` siguen igual; solo se dejan de editar `protocol` y las magnitudes de bloque desde la tarjeta continua/series.
- `CardioDetails` + flags de avisos para **Intervalos** sin `hiit` (beeps/voz/vibración): hoy solo existen en `CardioHiitConfig`; el vivo los lee con `true` por defecto (`CardioCueRules.kt:26-28`). Cambio pequeño: leerlos de `details` y de `hiit` como respaldo.
- Zona de pulso: se guarda `targetHrPercent` con el punto medio de la zona (55/65/75/85/95); sin dato nuevo.
- Ritmo solo para cinta, cinta curva, carrera y caminata: regla de presentación (`CardioCatalogItem` puede ganar `supportsPace`).
- Regla de arrastre, cifra grande y barras de esfuerzo: composables nuevos (no hay equivalente en `KpknUi`); las ilustraciones son dos listas de trazos por tipo (`machine`, `person`) con `StrokeCap.Round` y escala de trazo según tamaño (fórmula en `ceArt`).
- La barra «Añadir» del editor abre hoy solo el catálogo de fuerza (`SessionEditorScreen.kt:836`): si se convierte en menú (Ejercicio · Cardio), esa es la entrada «Cardio».

## 4 · Dudas que sí necesita decidir el usuario
1. **Dónde se añade un segundo cardio**: propongo el «+» del rótulo CARDIO y una entrada «Cardio» en el menú de «Añadir» (lo decide el paquete del editor). ¿Vale?
2. **Fichas**: los textos de las 12 son nuevos y de mi mano; conviene que los lea (están en la escena «Textos de las 12 fichas»), sobre todo las cifras de RPE/cadencia y las advertencias de seguridad.
3. **Avisos en Intervalos**: ¿persistirlos por cardio (cambio pequeño) o dejar siempre activos en Intervalos y mostrar la hoja solo para Series? La maqueta asume lo primero.
4. **Ritmo** en bici exterior, elíptica, remo, etc.: lo oculto por no tener sentido en min/km; ¿prefieres «ritmo/500 m» para el remo (requiere motor) o dejarlo fuera?
5. **Atajos de distancia** (1, 3, 5, 10 km) y el orden de las plantillas son míos; ¿quieres otros (5 K, 10 K, media)?
