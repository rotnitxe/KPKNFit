# Auditoría de «Avanzado» en la tarjeta de cardio (paquete CARDIO-EDITOR · punto 7)

Fuente de la edición: `screens/sessioneditor/components/CardioEditorCard.kt` (`CardioAdvancedPanel`, líneas 907–1112, y los selectores 308–515).
Consumidores: `screens/workout/` (cardio en vivo), `domain/cardio/`, `domain/calculations/`. Líneas de la rama `feat/pulido-v1` a 29-oct-2026.

Veredictos: **USADO** (el motor lo lee y cambia lo que ves o oyes) · **A MEDIAS** (se guarda pero solo funciona en parte o se pierde) · **NO USADO**.

| Campo (dónde se edita) | Quién lo lee | Veredicto |
|---|---|---|
| **Ritmo** `targetPaceSecondsPerKm` (Avanzado, celda «Ritmo», picker `PACE` 374) | En continuo: consigna y velocidad de la figura (`CardioLiveSpace.kt:123-125`, `CardioLiveCard.kt:262`), línea «ahora/siguiente» (`CardioPrescriptionFormatter.kt:149`), dosis del editor (`CardioDoseLine.kt:35`). En series/intervalos el picker lo copia a los bloques de esfuerzo (386-388), pero cualquier cambio posterior de la forma reconstruye los bloques y lo pierde (`CardioHiitProgramBuilder.build` 47-103 no lo copia). | **A MEDIAS** · USADO solo en Continuo. Sentido solo en cinta, cinta curva, carrera y caminata (el ritmo se mide en min/km). |
| **FC %** `targetHrPercent` (celda «FC», picker 393) | Zona y color del paso (`CardioLiveSpace.kt:104`, `:123`), consigna (`CardioPrescriptionFormatter.kt:150,138`), dosis «Z2» (`CardioDoseLine.kt:44`). Mismo problema de reconstrucción en series. | **A MEDIAS** · USADO en Continuo e Intervalos (por bloque, inspector). |
| **Vatios** (celda, picker 415) | Consigna y kcal por bloque (`CardioLiveSpace.kt:115`, `CardioIntervalEngine.metForBlock` 105). El picker hace `intervalBlocks.map { copy(watts) }` (423): en **Continuo la lista está vacía y no hace nada**; en Series se pisa con `suggestedWatts` al siguiente cambio (`CardioHiitProgramBuilder.block` ~120). | **A MEDIAS** · USADO solo por bloque, en Intervalos. |
| **Velocidad** (picker 427) | Consigna, kcal, km estimados (`CardioLiveSpace.kt:114`, `CardioLiveCard.kt:264-276`, `metForBlock` 102). Mismo defecto: no-op en Continuo, se pisa en Series. | **A MEDIAS** · USADO por bloque en Intervalos. |
| **Inclinación** (picker 439) | Solo muestra: chip en vivo (`CardioLiveCard.kt:618`), gráfica (`CardioIntervalChart.kt:312`), texto (`Formatter.kt:136`). No entra en las kcal. Mismo defecto de no-op/pisado. | **A MEDIAS** · USADO por bloque en Intervalos (cinta). |
| **RPM** (picker 451) | Consigna en vivo (`CardioLiveSpace.kt:116`) y texto (`Formatter.kt:135`). Mismo defecto. | **A MEDIAS** · USADO por bloque en Intervalos. |
| **Series** (stepper 1–5) `hiit.sets` | `CardioHiitProgramBuilder.build` 50-90 repite el bloque de rondas. | **USADO** (solo Series). |
| **Entre series** `restBetweenSetsSeconds` | `build` 84-91 inserta la pausa. | **USADO** (solo con más de 1 serie). |
| **Pausa activa / pasiva** `restNature` | Nivel de la pausa (`build` 62) y voz «Descanso activo…/alto total» (`CardioCueRules.kt:38`). | **USADO**. |
| **Objetivo de trabajo** tiempo / kcal / distancia + «kcal / m por esfuerzo» `workTargetType/Value` | Se vuelcan a `targetKcal`/`targetDistanceMeters` del bloque (`build` 55-75) y cortan el esfuerzo al llegar (`WorkoutViewModel.kt` `autoCutCardioBlockIfReached` 5885, 5939-5975; la distancia exige GPS). | **USADO**. |
| **Chip HIIT / SIT** `protocol` (en la forma Series) | Solo cambia el RPE por defecto 9 → 10 al pulsarlo (`CardioEditorCard.kt:654-668`). El motor no distingue protocolos: ambos dicen «¡Sprint!» (`CardioCueRules.kt:34-37`). | **NO USADO** (el valor útil es el RPE, que ya se edita). |
| **Beeps** `beepsEnabled` | Tono de fase y cuenta atrás 3-2-1 (`CardioCueRules.kt:27,54,65-67`; `WorkoutViewModel.kt:5910-5922`). Solo hay cambios de fase con bloques. | **USADO** en Series e Intervalos. En Intervalos sin `hiit` el interruptor no existe (se muestra apagado) pero el aviso suena siempre (por defecto `true`). |
| **Voz** `voiceCuesEnabled` | Frase de cada fase (`CardioCueRules.kt:26-45`; `WorkoutViewModel.kt:5919`). | **USADO** (igual que Beeps). |
| **Vibra** `vibrationEnabled` | `CardioCueRules.kt:28,47-52,68`. | **USADO** (igual que Beeps). |
| **Pantalla encendida** `keepScreenOn` (+ `hiit.keepScreenOn`) | `WorkoutScreen.kt:445-448` (flag de ventana mientras corre) y pantalla previa (`CardioLiveCard.kt:369,744`). | **USADO**. |
| **GPS** `requiresGps` (celda en la forma, exterior) | Todo el seguimiento (`WorkoutViewModel.kt:1202,1303,5825`, `CardioLiveCard.kt:161`). | **USADO**. |
| **Distancia** `targetDistanceKm` (celda en Continuo **y en Series**) | Continuo: cuenta atrás por distancia en GPS (`CardioLiveCard.kt:517,560-567`), hitos GPS (`WorkoutViewModel.kt:1209`). En Series/Intervalos el vivo la ignora (la duración sale de los bloques). | **USADO** en Continuo · **NO USADO** en Series. |
| **Mensaje «Los avisos de fase son del cardio por series»** | Texto explicativo. | Se conserva, mejor dicho (hoja «Avisos y pantalla», en Continuo). |
| `intensity` / `intensityLevel` (RPE) | Zona y nivel por defecto del bloque (`cardioSteps` 123), kcal (`levelToMet`). | **USADO**. |
| `metBase` | Kcal en continuo (`CardioLiveCard.kt:285`); sin control en el editor. | Fuera del alcance (sin UI). |

## Qué queda en el prototipo
- **Continuo**: ritmo (solo cinta/curva/carrera/caminata) y zona de pulso como «Objetivos (opcional)»; GPS; avisos y pantalla (solo pantalla aplicable).
- **Series**: calentamiento, esfuerzo, pausa, rondas, enfriamiento; hoja «Protocolo» (series, entre series, pausa activa/pasiva, objetivo kcal/m); hoja «Avisos y pantalla».
- **Intervalos**: por bloque, solo lo que la máquina admite (velocidad, inclinación, cadencia, vatios) + RPE y zona de pulso (el inspector del bloque, que es donde estas magnitudes sí funcionan).
- **Se elimina de la interfaz**: el panel «Avanzado» entero (Ritmo/FC/vatios/velocidad/inclinación/RPM sueltos), el chip HIIT/SIT, la Distancia dentro de Series, la celda «Ritmo» por bloque en máquinas con velocidad (el vivo muestra la velocidad y no el ritmo), y los chips Omitir/Libre sueltos (pasan a ser modos del selector).
- **Datos**: los campos del modelo (`protocol`, `targetPaceSecondsPerKm` en series, magnitudes de bloque) **no se migran ni se borran**; solo dejan de editarse desde la tarjeta. El JSON antiguo sigue cargando.
- **Con motor (propuesta, no dibujada como activable)**: que los avisos de fase (Beeps/Voz/Vibra) existan también en Intervalos sin `hiit` (hoy el vivo los lee con valores por defecto: bastaría guardarlos en `CardioDetails`). Sin ello, la hoja «Avisos» los muestra activos en Intervalos pero sin interruptor persistente: ver duda 3 de `cardio-editor.md`.
