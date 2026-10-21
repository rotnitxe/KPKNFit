# AUD1 · Auditoría de motores del editor y del catálogo (solo lectura)

Worktree `C:\kw\u11` (rama `feat/screens-v2`). **No cambies código ni pruebas.** El único archivo que escribes es el informe: `docs/screens-v2/audits/AUD1_motores_editor.md`. Sin Gradle, sin adb, sin teléfono. Puedes usar Python (`python -I`) para analizar los JSON del catálogo y las plantillas en `android-native/app/src/main/assets/`.

Contexto: decisiones del usuario en `docs/screens-v2/DECISIONES_PROTOTIPOS_2026-10-20.md` y mapa del editor en `docs/screens-v2/maps/MAPA_editor_catalogo.md`. Código en `android-native/app/src/main/java/com/example/kpkn/`.

## 1 · Impacto: Columna siempre en 0 (bug)

El usuario: «analiza un bug de por qué COLUMNA siempre queda en 0 independientemente del tipo de ejercicios que incluya».

- Localiza el cálculo que alimenta la pestaña Impacto del asistente del editor (la hoja `SessionEditorSheet` de impacto y su ViewModel o motor).
- Encuentra la causa raíz con evidencia (archivo:línea, valores intermedios para una sesión con sentadilla trasera y peso muerto), y explica por qué Músculos y Energía sí salen.
- Propón el arreglo exacto y las pruebas que lo fijarían (con datos reales del catálogo: carga axial de ejercicios, `axialLoadFactor` u otro campo equivalente).

## 2 · Sugerencias: más inteligentes y menos alarmistas

El usuario: «sigue muchas veces dando sugerencias bastante malas, debería ser más inteligente, entender mucho mejor el contexto de la sesión, los ejercicios, etc. y no ser tan alarmista».

- Inventario completo de las reglas del motor de sugerencias (cada regla: disparador, umbral, texto, severidad, archivo:línea).
- Prueba el motor «en papel» con al menos 8 sesiones reales de las plantillas o programas incluidos (fuerza, hipertrofia, full body, pierna, torso, sesión corta de 30 min, sesión solo de cardio, sesión con superseries) y anota qué sugerencias saldrían y cuáles son malas o alarmistas, y por qué.
- Diagnostica los fallos de fondo (falta de contexto: objetivo, rol de cada ejercicio, nivel, fase del bloque, tiempo disponible; umbrales fijos; duplicados; tono).
- Propón un rediseño concreto: entradas de contexto, reglas nuevas o recalibradas, prioridad y techo de avisos, deduplicación, umbrales con fundamento y guía de tono (sin alarmismo: describe, sugiere, no ordena). Incluye ejemplos de antes y después.

## 3 · Crear ejercicio: parecidos, imágenes y descripción automática

El usuario: «mejora los motores para detectar ejercicios similares, y haz una auditoría para mejorar mucho más esta función, como agregar imágenes, y la generación automática de descripciones».

- Ya existen `domain/exercises/SmartExerciseCreator.kt` y `domain/exercises/AutoExerciseDescription.kt`; revisa también `ExerciseMatchLexicon`, `ExerciseCatalogV2Resolver.search` y `AprendeSimilarityEngine`.
- Mide la calidad de la detección de parecidos con al menos 30 nombres escritos como los escribiría una persona (con faltas, sinónimos, inglés y español, material en el nombre): precisión del primer resultado y de los 3 primeros. Deja la lista y los resultados en el informe.
- Evalúa la descripción generada en 10 casos (qué dice, qué falta, qué es incorrecto).
- Imágenes: cómo podría un ejercicio propio tener imagen (foto propia, reutilizar la de un parecido del catálogo, ilustración del patrón); dónde se guardaría y qué existe hoy.
- Propón mejoras con prioridad y coste.

## Formato del informe

Para cada uno de los tres bloques: hallazgos con evidencia (archivo:línea), causa raíz, propuesta, pruebas que la fijarían y paquetes de implementación (tamaño S/M/L). Al final, un resumen de una página en español para el usuario. Tu último mensaje es ese resumen.
