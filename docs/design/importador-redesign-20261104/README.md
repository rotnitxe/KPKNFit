# Importador de programas · rediseño para aprobar

4 nov 2026 · **Prototipo v4 estático, conservado como referencia visual**. El flujo aprobado el 7 nov simplifica la propuesta: Android revisa ejercicios y distribución semanal en una sola pantalla y, al guardar, abre el `ProgramDetail` existente. Las 12 pantallas y 7 hojas de las tablas siguientes son escenas históricas del prototipo, no requisitos del flujo activo.

## Ajuste de flujo indicado por el usuario · 7 nov 2026

La última instrucción simplifica las 12 pantallas descritas más abajo. Tras leer el origen, la app va directamente a una sola pantalla de
confirmación de ejercicios y revisión de la distribución semanal, sin un resumen intermedio. Cuando ambas están listas, «Guardar y abrir programa» navega directamente al
`ProgramDetail` existente para ajustar las semanas. El editor de sesión existente sigue accesible desde el programa; el importador no abre
un paso de sesiones ni una vista de edición paralela. Los borradores nuevos conservan el documento y las decisiones de revisión, no otra copia
completa de `Program`.

`revision-local.html` es la copia estática local para consultar esas escenas sin servidor ni dependencias externas.

## Archivos

- `revision-local.html` — **revisión estática para el agente ejecutor**: 19 escenas, sin JS, sin servidor y sin dependencias externas.
- `prototipo.html` — prototipo interactivo; fuente de verdad del diseño y las interacciones.
- `artifact.html` — espejo público opcional del prototipo interactivo.
- `README.md` — este mapa de instancias, QA y piezas a tocar.

## Flujo activo

La importación termina en una sola revisión conjunta:

- El asistente comprueba que cada ejercicio tenga la variante correcta del catálogo o quede marcado como propio; los nombres dudosos se resuelven en el catálogo compartido.
- En la misma pantalla se comprueba la distribución por semana y se asignan las sesiones a días.
- El guardado se habilita cuando ejercicios, avisos y distribución están resueltos.
- Al guardar, la app abre el `ProgramDetail` existente para ajustar las semanas. Desde allí se entra al `SessionEditorScreen` existente si hace falta editar una sesión.
- El importador no añade una etapa de sesiones ni un editor o una copia paralelos del programa.

La lectura, la selección de fuente y la recuperación de errores siguen siendo estados de entrada. Las escenas siguientes documentan la propuesta v4 y no describen pantallas activas adicionales.

## Instancias

### Pantallas del prototipo v4 (archivo de diseño)

| # | Escena | Qué resuelve |
|---|---|---|
| 01 | `entrada` | Fuente (archivo, texto, foto), borrador recuperable y límite Pro previo. |
| 02 | `pegar` | Pegado de texto con contador, ejemplo y explicación de lo que se reconoce. |
| 03 | `leyendo` | Animación de importación: 5 etapas, progreso, contadores y cancelación. |
| 04 | `resumen` | Mapa del programa antes de pedir decisiones: estructura, pendientes y avisos. |
| 05 | `ejercicios` | Revisión de coincidencias con confianza, variantes, catálogo y decisión por aparición. |
| 06 | `dias` | Calendario semanal, descansos, reparto por semana y choques de sesiones. |
| 07 | `sesiones` | Lista las 12 sesiones (4 por semana) y abre el editor real para series, reps, RIR, descanso y notas. |
| 08 | `sesion` | Editor de sesión de KPKN: tabla de series, cargas, RIR, descanso y notas. |
| 09 | `revision` | Pasaporte, checklist, volumen estimado, series por grupo y guardar como borrador. |
| 10 | `exito` | Confirmación con anillos, pasaporte y primera sesión. |
| 11 | `vacio` | Documento leído sin ejercicios: diagnóstico, texto extraído y recuperación. |
| 12 | `error` | Archivo ilegible y catálogo no disponible, con etapa, causa y salida. |

### Hojas del prototipo v4 (archivo de diseño)

| Escena | Qué resuelve |
|---|---|
| `reanudar` | Borrador durable con progreso, reanudar, empezar de nuevo o descartar. |
| `catalogo` | Catálogo compartido en contexto, con búsqueda, filtros y creador. |
| `estructura` | Semanas y días: duplicar, añadir y revisar la estructura. |
| `pro` | Límite Free/Pro explicado antes de bloquear, con la opción de 1 semana. |
| `sesion_menu` | Editar, mover, duplicar o quitar una sesión de un día. |
| `avisos` | Los 4 avisos del documento con ubicación, texto original y acción. |
| `texto` | Corrección del texto extraído y reintento sin empezar de cero. |

## Navegación y QA del prototipo

- Navegación del laboratorio: **Recorrido**, **Mapa de pantallas** y **Decisiones**.
- Selectores: entrada (Programas / Alta), pantalla (360 × 740, 360 × 880, 393 × 852, 412 × 915), texto (100 / 130 %) y movimiento (normal / reducido).
- URL para capturas de una escena:
  `prototipo.html?solo=1&scene=ejercicios&size=360x880&fs=1.3`
- `&static=1` congela la lectura en su estado final.
- `?check=1` ejecuta las 19 escenas y devuelve errores, ids duplicados, notas ausentes y desbordes.
- `?list=1` devuelve el inventario de escenas.

## Límites del prototipo

- No lee archivos, no ejecuta OCR y no guarda programas.
- Los datos son sintéticos: 3 semanas, 4 sesiones por semana, 66 ejercicios y 2 pendientes. Las cifras de volumen y series por grupo son estimaciones.
- Las acciones de estructura y algunas hojas muestran su intención con un aviso; no reescriben el modelo.
- El recorte Free a una semana no se simula; la hoja `pro` lo explica.
- La representación de la sesión no sustituye al editor real. Cualquier diferencia es un riesgo de paridad.
- `revision-local.html` es una copia estática: no reproduce interacciones ni animaciones; para eso está `prototipo.html`.
- No sustituye la QA nativa: insets, TalkBack, 130 %, coste del desenfoque y rotación.

## Estado de implementación Android

- `ImportProgramGuidedScreen.kt` reúne la confirmación de ejercicios y `ImportWeeklyPlanner` en la revisión conjunta.
- `MainActivity.kt` recibe el id guardado y navega a `KpknRoute.ProgramDetail.create(programId)`.
- `ImportExerciseEditor.kt` se retiró; la edición de sesiones usa el editor compartido de KPKN desde el programa.
- La decisión de flujo del 7 nov reemplaza las preguntas de separación por pasos que acompañaban al prototipo v4.

## Referencia visual

Sigue el lenguaje de `docs/design/pulido-v1` y `docs/design/KPKN_UI_V2.md`: negro continuo, módulos `#111`, hojas y píldoras planas `#1B1B1D`, vidrio solo en islas flotantes, Syne para títulos y cifras de impacto, Inter para UI y cifras tabulares, movimiento de 160–320 ms y objetivos táctiles de 44–48 dp.
