# Paquete CARDIO-EDITOR · prefijo `ce` · pestaña `cardio-editor` · `maps/cardio-editor.md` + `maps/cardio-avanzado.md`
Lee antes `BRIEF_COMUN.md`. Puntos del usuario: **5, 6, 7, 8, 9, 11, 26**.

## Palabras del usuario (literal)
> 5. En la parte de añadir cardio, las miniaturas en muchas opciones se ven mal, deberías cuidar mucho más las miniaturas para que representen de verdad lo que trata cada una.
> 6. En la parte de configurar Cardio, la tarjeta se ve horrible y mal diseñada, está mal distribuida la información y peca de ser excesivamente densa, dame un prototipo de rediseño.
> 7. Donde dice "avanzado" en la tarjeta de cardio hay cosas que ni siqueira se usan ni nada, están ahí de sobra.
> 8. En duración me tira un selector DE MIERDA de minutos que se ve mal diseñado.
> 9. Cuando hago longpress a un ejercicio de cardio, me tira las mismas opciones de un ejercicio normal, el problema es que los ejercicios de cardio NO TIENEN FICHA, y las aproximaciones y superserie no necesariamente funcionan acá. Quiero un que esas opciones realmente estén adaptadas para cardio y que funcionen, y sus fichas impliquen que tengan su espacio dedicado para explicar de qué tratan.
> 11. Las tarjetas de cardio se ven TODAS MAL DISEÑADAS, y hay un texto que dice "añadir cardio" incluso dentro de la tarjeta de cardio, no tiene sentido!
> 26. En el editor hay un boton que dice "al final / al comienzo" cuando tengo caradio y fuerza que no tiene sentido, está mal implementado y está en un lugar demasiado llamativo.

## Dónde está hoy (léelo)
`screens/sessioneditor/components/CardioEditorCard.kt`, `CardioCatalogSheet.kt`, `SessionEditorViewModelCardio.kt`, `SessionListItems.kt`,
`SessionEditorModels.kt`, el selector de duración (busca «Duración» / `Min`), la ficha de cardio («Sin imagen revisada para esta configuración. Ver
progreso»), el menú de pulsación larga de ejercicio, y el modelo de cardio (`data/models`, `domain/...cardio...`): tipos (cinta, elíptica, remo,
bici estática, escaladora, SkiErg, carrera/bici/caminata exterior… lee la lista COMPLETA), formas (Continuo / Series / Intervalos), objetivos
(tiempo/distancia), RPE, plantillas de intervalos («1'/1'», «8 × 1:00 / 1:00 · RPE 9»), posición «Al inicio/Al final», GPS.
Capturas: `h06-anadir-cardio`, `h07-cardio-tarjeta`, `h08-cardio-avanzado`, `h09-cardio-longpress`, `h10-cardio-duracion`, `h11-cardio-ficha`.

## PRIMERO: auditoría de «Avanzado» (punto 7) → `maps/cardio-avanzado.md`
Para cada campo de «Avanzado» (Ritmo, FC, Beeps, Voz, Vibra, Pantalla encendida, «Los avisos de fase son del cardio por series»…) y para cada
otro ajuste de la tarjeta, rastrea si de verdad lo CONSUME el motor del cardio en vivo (`services/workout/…`, `screens/workout/…cardio…`) o la
analítica. Tabla: campo · dónde se edita · quién lo lee (archivo:línea) · veredicto (USADO / NO USADO / A MEDIAS). El prototipo muestra solo lo
USADO (y lo que propongas `new` con motor). Lo NO USADO queda anotado para eliminarse del código y de los datos sin migración rota.

## Qué debe contener el prototipo
1. **Añadir cardio**: la hoja con TODAS las opciones reales (en interior / al aire libre, GPS), con **miniaturas nuevas dibujadas a medida**
   (SVG de línea, 1 trazo coherente con KPKN Line pero más ilustrativas: la silueta de la máquina o el lugar —cinta con la persona corriendo,
   elíptica con sus brazos, remo con riel y volante, bici estática, escaladora con escalones, SkiErg con las dos asas, carrera por camino, bici
   por carretera, caminata por sendero…—). Hoy: elíptica = icono de pulso, SkiErg = una «T», caminata = figura de pie. Entrega las ilustraciones
   como símbolos reutilizables (`<symbol>` o funciones JS con el SVG exacto): se portarán a Compose. 
2. **Tarjeta de cardio en el editor** (cerrada y abierta) y su **configuración**: Continuo / Series / Intervalos, Tiempo o Distancia, RPE, plantillas
   de intervalos. Menos densidad, mejor reparto, jerarquía clara; **sin el texto «Añadir cardio» dentro de la tarjeta** (punto 11: el alta de
   cardio sale de la barra «Añadir»/la sección, no de dentro de la tarjeta). Que se vea la forma del intervalo (barras) y el resumen en una línea.
3. **Selector de duración (y distancia)**: reemplazo del selector de minutos feo. Propón el control (rueda grande legible, regla, stepper +
   atajos 10/15/20/30/45/60, entrada numérica…). Que sirva para tiempo y distancia y para h:mm. Estados y teclado.
4. **«Avanzado»** reducido a lo realmente usado (según tu auditoría) y bien diseñado.
5. **Menú de pulsación larga de un ejercicio de cardio** adaptado: nada de «Ficha de ejercicio», «+ Aproximación» ni «Crear superserie» tal cual.
   Decide qué acciones tienen sentido y FUNCIONAN para cardio (p. ej. guía del cardio, cambiar máquina/lugar, duplicar, mover al inicio/final,
   eliminar, y los equivalentes de «aproximación» = calentamiento / «movilidad» si el modelo los admite — lee el modelo; si algo exige motor nuevo,
   etiqueta `new` y dilo). 
6. **Ficha propia del cardio** (espacio dedicado, no «Sin imagen revisada…»): qué es, qué entrena, cómo medir el esfuerzo (RPE/zonas), formatos
   recomendados, consejos de técnica/seguridad, enlace a su progreso. Un texto breve y bueno por tipo (los tipos reales; escribe tú el contenido y
   márcalo `new` donde no exista en la app).
7. **Posición «Al inicio / Al final»** (punto 26): hoy es un botón enorme y poco sensato en el editor cuando hay cardio y fuerza. Propón su sitio
   natural (p. ej. dentro del menú o de la configuración de cada bloque de cardio, o un indicador discreto que se arrastra) y que sea claro qué
   significa. Decisión del orquestador: **no se quita la función**, se recoloca y se explica.
Texto al 130 % y 360 × 740 en la configuración y en el selector.
