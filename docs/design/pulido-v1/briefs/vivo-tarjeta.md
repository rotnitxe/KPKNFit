# Paquete VIVO-TARJETA · prefijo `lv` · pestaña `vivo-tarjeta` · `maps/vivo-tarjeta.md`
Lee antes `BRIEF_COMUN.md`. Puntos del usuario: **13, 14, 15**.

## Palabras del usuario (literal)
> 13. En la sesión de entreno en vivo, el selector de repeticiones se ve muy mal, los numeros tienen diferentes alturas (esto es por la fuente) y no tienen efecto carrusel para ir seleccionando via scroll horizontal, también debería pasar que el boton central esté mas enfocado y los otros para dar un buen efecto, y que los valores que están dentro del rango tengan un enfasis diferente, dame un prototipo para aprobar. Y el slider de "esfuerzo" debería tener una animación contenida conforme se acerque al fallo, algo que parezca como un destello o fuego mas intenso conforme la intensidad se vea mayor.
> 14. El roadmap inferior sigue viendose muy feo, aunque ha mejorado, quiero un rediseño para que me entregues un prototipo. Lo que busco es: que el usuario sepa en qué serie está, un acceso rapido para guardar una serie, que le permita moverse a otro ejercicio, etc pero sin cambiar la altura/tamaño del roadmap original, sino que se rediseñe.
> 15. En todos los ejercicios, en el sheet de "detalles del registro" sale la opción para habilitar registrar rom, sin embargo temo que no esté bien conectado con la logica de la app, por tanto quiero que lo conectes bien, que esté conectado al editor de entreno y que al habilitarlo, la tarjeta muestre ese slider de ROM, y que efectivamente el ROM cuente dentro de la serie.

## Dónde está hoy (léelo)
`screens/workout/components/SetExecutionCard.kt` (tarjeta de serie: CARGA, REPETICIONES, ESFUERZO, «Parciales, ayuda y ROM», Avanzado/Modo visual),
`LiveSetDetailSheet.kt` (hoja «Detalle del registro» con «Registrar ROM»), el roadmap inferior (búscalo: «SERIES · 0 DE 18», círculos 1-2-3, «Registrar serie»,
flechas ‹ ›), el slider de esfuerzo y el selector de reps. Opciones de ROM en `screens/sessioneditor/components/ExerciseOptionsBar.kt`,
`SessionEditorUiUtils.kt`, `data/models/Session.kt`, `WorkoutViewModel.kt`.
Capturas: `h15-reps-selector`, `h16-roadmap`, `h17-rom`. Leyes de la sesión en vivo (aprobadas el 16-oct, siguen vigentes): la **tarjeta de la serie
nunca cambia de tamaño**; RIR/RPE empieza en el mínimo y el plan es una marca tenue; el roadmap conserva su altura. Decisiones del Refinamiento 02 en
`artifacts/refinamiento-02-qa/parity/DECISIONES.md` (en el checkout principal `C:\Users\valen\Documents\KPKNFit`; léelas, no las contradigas sin decirlo)
y el prototipo de la tarjeta en `docs/design/live-session-v5` / `live-series-cards-v1`.

## Qué debe contener el prototipo
1. **Selector de repeticiones en carrusel**: cifras alineadas (lining + tabulares, no Syne), desplazamiento horizontal con imán al centro, el
   valor central grande y enfocado y los vecinos con escala/opacidad decrecientes, los valores **dentro del rango objetivo** con otro énfasis (p. ej.
   el rango del plan como un riel o un trazo bajo las cifras; fuera del rango, más tenue), el valor del plan marcado, háptico visual al encajar,
   tocar un vecino lo centra, y el campo de entrada directa si la app lo ofrece (léelo). Interactivo (arrastrar con puntero).
2. **Slider de esfuerzo (RPE/RIR)** con animación contenida que sube con la intensidad: sin calor en valores bajos, un resplandor sutil hacia 8,
   un destello/llama contenida cerca del fallo (nada de confeti ni fuego caricaturesco; lenguaje abstracto, controlado: partículas cortas o
   un halo que respira dentro del riel). Interactivo; versión estática para movimiento reducido. Mantén la marca «plan» y el estado «Sin marcar».
3. **Roadmap inferior** rediseñado **a la misma altura** que el actual: dónde estoy (ejercicio y serie N / total), registrar serie en un toque (la
   acción principal), ir a la anterior/siguiente serie, saltar a otro ejercicio (lista/selector), progreso global («0 de 18»), y lo que ya existe
   hoy. Estados: serie sin registrar, registrada, última serie del ejercicio, ejercicio terminado, último ejercicio, descanso activo (si el roadmap
   cambia), texto 130 %, 360 × 740.
4. **ROM conectado**: (a) en el editor de entreno, la opción por ejercicio «Registrar ROM» (muéstrala en su sitio real: ExerciseOptionsBar o la
   configuración del ejercicio; la escena del editor puede ser un fragmento) ; (b) en la tarjeta en vivo, al estar activa, aparece el **slider de ROM**
   (0–100 %, 100 por defecto, con marcas útiles: parcial / casi completo / completo) SIN cambiar el tamaño de la tarjeta (ley 1: decide dónde vive —p. ej.
   sustituye a la fila de «Parciales, ayuda y ROM» o va en su mismo hueco—); (c) la hoja «Detalle del registro» con el ROM reflejado; (d) cómo se ve la
   serie registrada con ROM en el roadmap, el resumen de serie y el historial («4 reps · ROM 80 %»). Documenta en el mapa cómo debe contar ese ROM
   «dentro de la serie» (qué se guarda, qué métricas lo usan: volumen efectivo, calidad, progreso).
Un ejercicio con peso externo (peso muerto) como caso principal; añade un caso de peso corporal y uno de tiempo si la tarjeta cambia.
