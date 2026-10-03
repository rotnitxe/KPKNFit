# Lote 3: rodilla aislada. Informe para aprobación

Fecha: 2026-10-03 · 10 definiciones · 23 configuraciones · estado: **aplicado el
2026-10-03 con el OK del usuario.** Decisiones: las 4 variantes dudosas se quedan (el
usuario confirmó que son ejecutables); se corrigen ya los patrones de balón, sliders y GHR
(`knee_flexion`) y el bug de `CompositionTaxonomy.kt`. El tipo articular queda para la pasada
estructural.

## Resumen

- Las 10 definiciones pasan de LEGACY a CURATED: los tres curls de isquiosurales (tumbado,
  sentado, de pie), los curls con balón y con sliders, el curl nórdico, el Glute Ham Raise,
  la extensión de cuádriceps en máquina, la extensión de pie en polea y el curl nórdico
  inverso.
- Lint conjunto: **0 errores y 0 avisos**. 106 citas, todas verificadas.
- Mismo proceso que el lote 2: 3 autores, 2 revisores limpios (contrastaron las fuentes con
  sus resúmenes y, cuando hizo falta, con el texto completo), una ronda de correcciones.
- **No cambia el músculo principal de ninguna configuración**, así que no hay nada que
  escalar en la composición de sesiones.

## Errores graves corregidos

- **Los tres curls de isquiosurales no tenían la rodilla como articulación** (solo la columna
  lumbar como estabilizadora). Ahora la rodilla es principal en las 14 configuraciones.
- **La extensión de cuádriceps arrastraba la plantilla de la sentadilla**: cadera que se
  flexiona y extiende, tobillo en dorsiflexión. Ninguno de los dos se mueve. Quedó como lo que
  es, un aislamiento de rodilla.
- **El nórdico inverso tenía la cadera como principal y la rodilla como secundaria**, al revés
  de lo real. Además llevaba la sacroilíaca sin medición.
- **El curl nórdico y los curls con balón y sliders** tenían la cadera como secundaria aunque
  va extendida e inmóvil: ahora es estabilizadora.
- **Textos:** la frase rota «Con a dos lados», cues que hablaban de «máquina» en la versión con
  mancuerna, «almohadilla» donde no la hay, «pantorrillas sobre el balón» (apoyan los talones),
  clichés vetados («el más temido», «rito de paso», «la referencia mundial para proteger de
  lesiones», sin respaldo) y la plantilla «La trayectoria permanece estable…».
- **Revisión de las fuentes:** el revisor encontró que un estudio clave (Maeo 2021) hizo el
  curl tumbado con la cadera a unos 30°, no recta; se corrigieron todas las citas que decían lo
  contrario.

## Cambios de anatomía (volumen)

- **Core:** sale de los curls tumbado y sentado y de las extensiones (10 configuraciones):
  un banco sostiene el tronco (regla L6; Saeterbakken 2022, extrapolado).
- **Glúteo mayor:** sale del curl tumbado (6; no cruza la rodilla y fue el menos activo de seis
  ejercicios, Stevens 2022) y pasa de secundario a estabilizador en balón, sliders, nórdico y
  GHR (4; la cadera no se mueve).
- **Gemelos:** entran como secundarios en 8 configuraciones (el gastrocnemio cruza la rodilla y
  la flexiona: Li 2002, Marchetti 2021; en el nórdico llegó al 82-84 % de su máximo, Comfort
  2017).
- **Glúteo medio** estabilizador en la pierna de apoyo de los ejercicios de pie (6) y **flexores
  de cadera** estabilizadores donde sostienen el muslo o el tronco (3).
- **No se añadieron** músculos deducidos sin electromiografía (aductores, tibial anterior,
  glúteo en el curl de pie con polea): inflarían el volumen sin respaldo.
- Tabla completa en el anexo de anatomía.

## Decisiones del coordinador (puedes revertirlas)

- **GHR canónico:** arranca desde la horizontal con la cadera extendida y sube solo flexionando
  la rodilla (estilo Westside; McAllister 2014). Por eso la cadera y el glúteo quedan como
  estabilizadores. La versión larga, desde el tronco colgando, no es esta definición.
- **Curl con balón:** la imagen se fija a mitad del curl, porque el final (rodillas a 90°) es
  idéntico a un puente de glúteo con los pies en el balón.

## Preguntas para ti

1. **Variantes que no existen como tales** (los textos las describen con honestidad, por
   turnos o con poco estímulo; no las retiré). ¿Las retiro como la sissy con barra, con
   remapeo de lo guardado a la hermana más cercana?
   - `standing_leg_curl__bilateral__machine` (¡es la configuración por defecto!) y
     `standing_leg_curl__bilateral__cable`: de pie no se flexionan las dos rodillas a la vez.
   - `quads_extension_cuadriceps_pie_polea__bilateral` (también es la de por defecto): de pie
     no se extienden las dos rodillas contra una tobillera.
   - `lying_leg_curl__unilateral__dumbbells`: un solo pie no sostiene bien una mancuerna y
     casi no carga la rodilla.
2. **Extensión de pie en polea:** la ficha usa la polea baja (carga más cerca de la
   extensión). ¿Añadimos el eje de altura de polea, como en el cruce de poleas?
3. **Bug de la app (fuera del catálogo):** la composición de sesiones (`CompositionTaxonomy.kt`)
   clasifica el patrón del nórdico inverso como **flexión de codo**. Es una línea; propongo
   pasarlo a extensión de rodilla en este lote, con su test.
4. **Patrones y tipo articular** (no se tocaron; requieren tu OK, como los del lote 4):
   - Balón y sliders usan `knee_hip_flexion` («Flexión de rodilla y cadera»), pero la cadera no
     se flexiona: encajaría `knee_flexion`.
   - El GHR usa `knee_hip_extension` («Extensión de rodilla y cadera»), pero la rodilla se
     flexiona.
   - Nórdico, nórdico inverso y GHR figuran como multiarticulares; con la cadera inmóvil son
     de una sola articulación.
   - Sugerencia: que la regla «rodilla principal en patrones de rodilla» cubra también el
     patrón del nórdico inverso.

## Variantes dudosas menores

- La bilateral con polea del curl tumbado y del sentado existe (dos tobilleras al mismo
  mosquetón), pero es poco habitual.
- El nombre «Curl Nórdico (Nordic Hamstring Curl)» y «Curl Nórdico Inverso (Reverse Nordic
  Curl)» llevan doble nombre entre paréntesis (contra la regla R6).

## Cola de imágenes del lote 3

| Par (definición × implemento) | Imagen actual | Veredicto | Motivo |
|---|---|---|---|
| lying_leg_curl × cable | exercise_lying_leg_curl_cable_batch9 | PASA | Cámara en tres cuartos elevada, no lateral |
| lying_leg_curl × dumbbells | exercise_lying_leg_curl_dumbbells_batch9 | FALLA | Rodillas a 70-75°, espinillas inclinadas; asa en las puntas de los pies |
| lying_leg_curl × machine | exercise_lying_leg_curl_machine_batch9 | PASA, regenerar | Pasa las preguntas, pero el brazo del rodillo cruza por encima de los muslos: máquina imposible |
| seated_leg_curl × machine | exercise_seated_leg_curl_machine_batch10 | FALLA | Fotograma de arranque, piernas estiradas (se confunde con una extensión); eje del brazo muy adelantado |
| seated_leg_curl × cable | exercise_seated_leg_curl_cable_batch10 | PASA | |
| standing_leg_curl × machine | exercise_standing_leg_curl_machine_batch10 | PASA | |
| standing_leg_curl × cable | exercise_standing_leg_curl_cable_batch10 | PASA | |
| curl_isquios_con_balon × bodyweight | exercise_curl_isquios_con_balon_bodyweight_batch10 | PASA, regenerar | El nuevo fotograma es la mitad del curl; la actual parece un puente |
| curl_isquios_con_sliders × sliders | exercise_curl_isquios_con_sliders_sliders_batch10 | FALLA | Rodillas a 90° con los pies bajo ellas: es el inicio o un puente |
| hams_curl_nordic_peso_corporal × bodyweight | exercise_hams_curl_nordic_peso_corporal_bodyweight_batch10 | PASA | |
| quads_reverse_nordic_peso_corporal × bodyweight | exercise_quads_reverse_nordic_peso_corporal_bodyweight_batch10 | PASA | Sin colchoneta |

**Sin imagen (3):** quads_extension_cuadriceps, quads_extension_cuadriceps_pie_polea,
glute_ham_raise.

## Anexos

- [Textos nuevos, anatomía y prompt de imagen de cada definición](LOTE_03_TEXTOS.md)
- [Delta anatómico completo por configuración](LOTE_03_ANATOMIA.md)
