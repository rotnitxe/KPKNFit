# Paquete EDITOR · prefijo `ed` · pestaña `editor` · `maps/editor.md`
Lee antes `BRIEF_COMUN.md`. Puntos del usuario: **3 (bis), 10, 12, 12 (bis)**.

## Palabras del usuario (literal)
> 3 (bis). En el editor, para configurar series hay 4 botones GIGANTES de "externa" "corporal" "lastre" y "asistido", esto es una pésima decisión de diseño, debería verse por defecto "externa" como tipo de carga y el resto como opciones que se despliegan desde un menú al lado de ese botón. La parte de "reps-rangos" y la intensidad deberían verse mejor, en intensidad en vez de verse botones de RPE, RIR Y FALLO, que esas opciones se vean integradas por el input de intensidad. La pe del descanso de la serie se ve HORRIBLE, debería verse más interactivo. Y las opciones de "aplicar a todos" "duplicar" "bajar" se ven mal diseñadas, parecen texto suelto sin sentido y el boton eliminar también parece un texto suelto diseñado de mala gana.
> 10. Al eliminar un ejercicio con swipe la animación es horrenda y está mal hecho, dame una propuesta de borrar por swipe que realmente se vea bien y lo mismo para el drag and drop, funciona de forma batante regular y se ve mal animado.
> 12. La pildora superior de ir atrás, dia de semana y tres puntos estan cortando el contenido al scroll, cuando deberían tener efecto Blur y glass y dejar que las cosas pasen por abajo.
> 12 (bis). La pildora inferior de los botones "añadir" "asistente" "reglas" y un timer estimado se ven horrendos, quiero un rediseño. Dame un prototipo a aprobar.

## Dónde está hoy (léelo)
`screens/sessioneditor/`: `SessionEditorScrollRenderer.kt`, `SessionListItems.kt`, `SessionEditorModels.kt`, `components/` (barra de opciones del
ejercicio `ExerciseOptionsBar.kt`, filas de serie, barra inferior, sheets). Isla superior: `ui/design/KpknTopIsland.kt`, `KpknIslandVisibility.kt`.
Capturas: `h12-swipe-eliminar`, `h13-editor-lista`, `h14-apre`, `h07-cardio-tarjeta` (ahí se ve la barra inferior con «80», «Añadir», «Asistente», «Reglas»).
Las series de hoy se editan en la tarjeta del ejercicio abierta («SERIES EFECTIVAS», «Modo: Reps ▾», «Descanso 3:00», «⋯»): abre el Kotlin para ver
TODAS las opciones reales (tipos de carga, modos reps/rango, RPE/RIR/Fallo, descanso, aproximación, movilidad, superserie, grupos, tipos de serie…).

## Qué debe contener el prototipo (mínimo, todas interactivas donde tenga sentido)
1. **Tarjeta de ejercicio abierta con sus series**: «Externa» como tipo de carga por defecto en un solo control con menú desplegable al lado
   (Corporal, Lastre, Asistido… lee las reales y qué cambia en el campo de carga de cada una); reps/rangos mejor resueltos; **intensidad en un
   solo campo** donde RPE/RIR/Fallo van integrados en el propio input (decide el patrón: unidad conmutable dentro del campo, deslizador, rueda…);
   **descanso interactivo** (no una etiqueta fea: tocar abre un control táctil ágil); acciones de la serie (aplicar a todos, duplicar, bajar,
   eliminar) como acciones con forma de botón/menú y jerarquía (eliminar es destructiva y se distingue sin gritar). Estados: menú de carga
   abierto, modo rango, cambio RPE→RIR→Fallo, descanso editándose, menú de acciones, serie de aproximación y de movilidad si existen.
2. **Borrar con deslizamiento**: reposo, arrastre parcial, umbral superado (señal visual clara), salida, colapso suave de la fila, aviso
   «Ejercicio eliminado · Deshacer» con cuenta atrás visible. Versión interactiva (arrastra de verdad con puntero/táctil). Mira `h12` para ver el
   defecto actual (el fondo se corta y la tarjeta se parte).
3. **Arrastrar para ordenar**: asa → levantar (escala + sombra + vibración visual), hueco que se abre, autoscroll en los bordes, soltar con
   asentamiento. Interactivo.
4. **Píldora superior** (atrás · día de la semana ‹ Jue ▾ › · ⋯) en **vidrio con desenfoque real**: el contenido pasa por debajo sin cortarse.
   Estados: reposo, con scroll (se ve el contenido borroso detrás), selector de día abierto.
5. **Barra inferior** rediseñada: hoy tiene un anillo con los minutos estimados, «Añadir», «Asistente» y «Reglas». Decisión del usuario en el
   paquete `asistente`: **«Reglas» pasa a ser la 4.ª pestaña del Asistente** (márcalo `move`). Diseña la barra con minutos estimados + Añadir +
   Asistente; estados: reposo, «Añadir» desplegado (ejercicio, cardio, grupo… lo que ofrezca la app hoy), minutos que cambian al añadir/quitar,
   y cómo se comporta al hacer scroll. Misma altura aproximada que hoy salvo que justifiques lo contrario.

Cardio y su tarjeta NO son tuyos (paquete `cardio-editor`); sí es tuya la barra y la píldora que aparecen también en las pantallas de cardio.
