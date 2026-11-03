# Paquete VIVO-RESTO · prefijo `lx` · pestaña `vivo-resto` · `maps/vivo-resto.md`
Lee antes `BRIEF_COMUN.md`. Puntos del usuario: **16, 17, 18, 19**.

## Palabras del usuario (literal)
> 16. La función del cronómetro debe mejorarse y que se vea como un cronómetro real, y agregar también la función de un temporizador y mejorar su diseño.
> 17. En el modo visual de las tarjetas no aparece la opción para expandir la vista.
> 18. El sheet de sesión completada se ve muy mal, mal diseñado, simplista. Dame un rediseño que contemple buen diseño, animaciones, que muestre información de forma visual, sin abusar del texto y que la opcion para la historia de instagram, guardan sesión etc se vean bonitos. Como regla principal: no debe ser necesario scrollear para ver todo.
> 19. En la sesión en vivo modo lista, aplicaste un efecto desvanecido arriba y abajo, sin embargo, en la parte inferior el efecto está demasiado arriba, cortando mucho antes del tamaño de la pantalla y además la pildora superior con el nombre de la sesión no está funcionando como flotante, debería permitir que los elementos pasen por abajo y tener un efecto blur, el desvanecido es a los extremos, no antes, es para refinar ese término.

## Dónde está hoy (léelo)
`screens/workout/` (WorkoutScreen.kt, `components/`: cápsula del header con el tiempo «0:21» + ⋯, modo Lista, modo visual de la tarjeta, hoja de sesión
completada/resumen y compartir en historias; busca «cronómetro», «temporizador», «Stopwatch», «Timer», «Expandir», «Modo visual», «completada»).
Capturas: `h16-roadmap` y `h15-reps-selector` (cápsula superior con «0:21»), `h18-lista-vivo` (modo lista: la cápsula superior corta el contenido y el
desvanecido inferior empieza demasiado arriba, rodeado en azul por el usuario). Decisiones del Refinamiento 02 en
`C:\Users\valen\Documents\KPKNFit\artifacts\refinamiento-02-qa\parity\DECISIONES.md` y prototipos en `docs/design/live-session-v5`, `live-session-iteration-03/04`.

## Qué debe contener el prototipo
1. **Cronómetro y temporizador** (punto 16): descubre qué hace hoy el cronómetro (¿solo el tiempo de la sesión? ¿hay otro?) y diseña un
   cronómetro de verdad (disco/esfera o cifras grandes con décimas, iniciar/pausar/vuelta/reiniciar, vueltas con parcial y total) y un
   **temporizador** (rueda o teclado para fijar tiempo, atajos, cuenta atrás con anillo, avisos al terminar), en una hoja desde la cápsula del header
   o donde hoy se abra. Estados: cronómetro corriendo, con vueltas, pausado; temporizador configurando, corriendo, terminado; coexistencia con el
   descanso de la sesión (no deben pisarse; decide y explica).
2. **Modo visual con «Expandir»** (punto 17): la tarjeta en modo visual con la opción de expandir la vista visible (ley 1 del 16-oct: «Modo visual»
   con «Expandir» encima de todo), el estado expandido y cómo se vuelve. Es un arreglo directo (se implementa ya): dibuja el estado correcto
   para que quede el contrato.
3. **Hoja de sesión completada** (punto 18): rediseño total **sin scroll**: cifras clave en visual (duración, volumen, series, récords, esfuerzo
   medio, músculos trabajados…, lo que el resumen real calcula hoy: léelo), animación de entrada tipo celebración contenida en el lenguaje de las
   portadas (líneas que se dibujan, cifras que cuentan), acciones: **compartir en historias de Instagram** (vista previa de la historia),
   guardar sesión, y todo lo que ofrece hoy (notas, valorar, volver al inicio…). Estados: sesión normal, con récord, con cardio, sesión corta,
   sin series registradas, 360 × 740 y 130 % (aquí también debe caber o, si no cabe, degradarse con criterio sin scroll de lo esencial).
4. **Modo Lista** (punto 19, arreglo directo): la píldora superior con el nombre de la sesión **flotante en vidrio con desenfoque** (el contenido pasa
   por debajo) y el **desvanecido en los extremos reales** de la pantalla (arriba junto al borde y abajo en el borde inferior, sobre la barra de
   sistema), no a media pantalla. Dibuja el estado correcto con scroll para dejar el contrato exacto (alturas del desvanecido en dp).
