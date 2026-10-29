# Arreglos v1 · paridad 1:1 con el lienzo

Lienzo aprobado el 29-oct-2026 (`README.md`, artifact YBycPadM4g1VTxWWTMMxQX). La implementación sigue `src/scenes-*.js` y
`src/arreglos.css` al pie de la letra; lo que la pantalla vieja tenía y el lienzo no, se quitó, salvo las FUNCIONES
alcanzables. Aquí van las diferencias inevitables y las decisiones de implementación, por paquete.

## Regla añadida al aprobar

«Cuando un ejercicio tenga al lado una miniatura, esa debe ser la imagen del ejercicio que TIENE KPKN. No otra cosa.»
Toda miniatura de ejercicio sale de `ExerciseTechniqueImageLookup.resolveImageResId(...)`; si KPKN no tiene imagen de ese
ejercicio, la fila va sin miniatura (ni glifo, ni inicial, ni icono).

## N1 · Huella de vitaminas y minerales

- La huella es radial (`npRad`): 24 radios en tres grupos, pista, relleno, topes huecos (macizos en rojo si pasan del
  máximo), círculo discontinuo «sin dato», entrada escalonada de 900 ms + 22 ms por radio.
- La lista completa muestra los 38 nutrientes que guarda la app en 5 grupos seguidos (Vitaminas, Minerales, Grasas, Carbos,
  Otros), sin chips. El lienzo dibuja 24 en 3 grupos; así no se pierde el acceso a grasas y carbos.
- El pie de fuentes y licencias («Datos de alimentos: USDA…, Open Food Facts (ODbL)») se conserva en la lista del día en
  letra pequeña: la atribución ODbL es obligatoria.
- Las líneas de cobertura usan los textos que ya calcula el modelo («3 con ficha completa · 1 estimado»…), no los de
  ejemplo del lienzo.
- «Sin dato» no cuenta como «por debajo» en la leyenda del día (lo desconocido no es 0; el lienzo lo contaba por `pct||0`).
- Sin perfil de referencias (modo relativo, que el lienzo no contempla): radios de largo relativo a opacidad 0,6, sin
  códigos ni topes huecos, y el aviso en la fila de cobertura.
- Los módulos del día y de la comida se tocan en toda su superficie; la fila de cobertura lleva el mismo clic.
- Las cifras grandes de la hoja de un nutriente van en Inter SemiBold (Syne queda solo para el total de la comida).
- Fuera: «A vigilar», chips de grupo, 4 cifras KPI, puntos de cobertura, «Ver todos ›», notas bajo cada fila, rayado de lo
  estimado, marca del 100 %, flecha y acento naranja.

## N2 · Pantalla del registro

- El campo es plano con el micrófono a la izquierda y «Analizar» a la derecha. La tecla Enter usa `ImeAction.Go`: Android
  no deja poner la palabra «Analizar» en la tecla de todos los teclados; la tecla analiza igual.
- Si hay dudas que bloquean el guardado, la barra dice «Resuelve N dudas» (función ya existente que se conserva).
- Al responder una duda, la pregunta se va a los 1,2 s (mecánica del registro: deja ver la respuesta elegida).
- Opciones de porción sin repetidas (`loggerDistinctPortions`: mismos gramos = misma opción), con mayúscula inicial y la
  cantidad en gris tras « · ». Fuera «Seguir con la estimación»: guardar ya sigue con lo estimado.
- Las gotas del aceite dejan de dibujarse: todas las dudas son chips, como en el lienzo.

## N4 · Inicio y día de Nutrición

- «Nutrición de hoy» es una tarjeta plana con el anillo fino de 96 dp y las filas de macros; «＋ Registrar comida» abre el
  registro completo. Sale la barra «¿Qué comiste?» de Inicio con su descripción rápida y su «Deshacer».
- Sale «te quedan / te pasaste» (el lienzo no lo muestra).
- Las comidas de Inicio siguen como 4 celdas, ahora en su propio módulo; «Tu día» conserva su borde y la tira de 7 días
  (contexto, no se aprobó como cambio).
- El rebote del anillo al guardar desde Inicio se conserva.

## N3 · Hoja de alimento y transición de las hojas

- La hoja sigue `npSheetInner`: cabecera, Cantidad (− / +), Tamaño en 4 tramos, «Cómo lo preparaste» con su Aceite,
  «Aporta» (barra en tres tonos y columnas) y la huella. Salen los círculos de gramos/ración, la variante repetida, las
  cuatro cajas de cifras y «Con mantequilla».
- Las preparaciones (Crudo, Cocido, Al horno, A la plancha, Salteado, Frito…) salen de las fichas que tiene el concepto
  (`domain/nutrition/FoodPreparations.kt`). «Salteado» = ficha cocida + aceite. «Crudo» solo aparece si hay ficha cruda y
  el alimento no es de un grupo que se pesa en crudo pero no se come así (tubérculos, cereales y panes, pasta y arroz,
  legumbres, carnes y aves, huevos), salvo que ya estuviera en crudo al abrir la hoja.
- Las kcal del aceite salen del modelo real de absorción por tipo de alimento (`OilAbsorptionCategory`), no de la cifra de
  ejemplo del lienzo (200 g de papas fritas «Normal»: +216 kcal en vez de +222). Frito «Normal» = la ficha frita tal cual
  (ya trae su aceite); «Poco» resta y «Mucho» suma. Horno y plancha abren en «Sin»: abrir la hoja no cambia ninguna cifra.
- Los gramos de Tamaño son los de la app (×0,75 / 1 / 1,25 / 1,5 de la cantidad al abrir: 150 / 200 / 250 / 300 g), no
  100 / 200 / 300 / 400.
- Texto de lo cocido en agua: «Cocido en agua: no hay aceite que sumar.» (concordancia neutra; el lienzo dice «Cocidas»).
- Textos de 11–12,5 px del lienzo suben a 13 sp (mínimo legible de la app); subtextos y cifras se encogen antes que cortarse.
- Se conservan «Excluir de la comida», la papelera y «Listo» con los estilos `.np-row`, `.np-foot` y `.np-trash` de
  `arreglos.css` (funciones existentes que la escena no dibuja).
- Las vitaminas de la hoja van sin tarjeta propia: el lienzo pone `.np-sheet .np-mod{background:transparent}` sobre la
  hoja #141414.
- Transición de TODAS las hojas (`KpknSheet`): fondo 220 ms; hoja 420 ms con (0,22, 1, 0,36, 1) desde su alto medido;
  contenido 120 ms después, 340 ms, subiendo 10 dp; cierre 260 ms con la curva inversa; con «reducir movimiento», fundido de
  150 ms. El arrastre para cerrar se conserva. Las hojas que su pantalla quita de golpe (sin `LocalKpknSheetDismiss`) siguen
  cerrándose sin animación de salida.
- Fuera de Inicio sale también lo que solo servía a la barra «¿Qué comiste?»: la media hoja QUICK, la línea «Esta comida»
  de la huella y su subtítulo.

## P1 · Barra de vidrio y ⋯

- La barra flotante lleva solo atrás y pestañas y desenfoca de verdad: la columna del programa es su `hazeSource` local
  (`programHazeState`), pasado por parámetro a la barra.
- El ⋯ va en la fila del nombre del programa, con el punto de acento si hay pendientes, y se va con la cabecera al bajar
  (literal al lienzo, que no lo fija).

## P2 · «Tus últimas 4 semanas»

- Página nueva (`KpknRoute.ProgramFourWeeks`, «program-four-weeks/{programId}») a la que lleva la tarjeta de entrada de
  Progreso (antes iba al panel de volumen, que sigue igual para sus otros usos). Las 4 semanas son las de calendario
  (lunes a domingo) que terminan hoy; las anteriores al programa no cuentan («semanas 1 a 2»).
- A cada semana de calendario le toca la semana del plan más repetida en sus registros; sin registros, la que toca contando
  hacia atrás desde la semana en curso. Las rutinas usan la misma cuenta por calendario que Progreso.
- Saltada = sesión obligatoria del plan sin registro cuyo día ya pasó (la de hoy no cuenta); previstas = hechas + saltadas.
- Récords con la detección que ya existía (`ExerciseProgress`): la primera vez no cuenta, solo series con carga externa,
  uno por ejercicio. Músculos con las mismas piezas y rangos que Progreso (`mergePersonalizedTargets`, con sus respaldos).
- Cada sección se oculta sola si no tiene datos (fuerza sin e1RM, récords, esfuerzo y molestias, «Lo que viene» en rutinas
  o en la última semana).
- La frase de arriba usa el nombre completo de los ejercicios.
- Músculos: se clasifica por los datos (el lienzo marcaba Tríceps 7 con mínimo 6 como «Por debajo», contradiciéndose):
  por debajo del mínimo útil, «Por debajo»; tercio bajo del rango, «Justo»; y se añade «Por encima» (ámbar) para lo que
  pasa del máximo recuperable.
- «Lo que viene» describe la semana real («3 sesiones · 34 series · cierra el programa») y la propuesta da la carga
  objetivo («sentadilla a 105 kg»), que es lo que guarda la app.
- Textos que el lienzo no tenía y la página necesita: «ninguna saltada» / «1 saltada»; «Más cerca del fallo», «Más lejos
  del fallo» y «El plan no fija un RIR».
- La página enseña las filas de calendario de las semanas que cuentan; la tarjeta de entrada, siempre 28 cuadros.
- «Compartir tus 4 semanas» abre la hoja de historias en la plantilla Progreso (si no está disponible, Semana).
- El botón atrás es un círculo de vidrio propio con el mismo estilo que el de la barra flotante.

## P3 · Ficha del asistente reutilizada

- La misma ficha del asistente (`PlanDetailOverlay`) sirve para el plan del catálogo («Configurar este plan»; los
  protocolos, con «Crear una copia» como acción secundaria) y para «Sobre el programa» en modo lectura («TU PROGRAMA ·
  ACTIVO», «Cargas y cambios», el origen como atribución y «Ver cómo funciona»).
- Miniaturas de «Ejercicios principales» y del panel «Cambiar»: SIEMPRE la imagen de KPKN
  (`ExerciseTechniqueImageLookup`); sin imagen o sin coincidencia, la fila va sin miniatura (nunca un glifo de patrón).
  Los círculos de cada día de «Tu semana» llevan el pictograma del patrón de la sesión, como el lienzo: son el icono del
  día, no la miniatura de un ejercicio.
- La portada es la de la app (`PlanCover` en banner) en el color del objetivo (PHAT, amarillo de powerbuilding, no el
  naranja del lienzo), sin icono en la cabecera (la portada no tiene hueco).
- El tercer chip es el alcance («6 semanas», «Una semana que se repite»): la frecuencia por músculo no se puede calcular
  para todos los planes. El catálogo no trae «Por qué este programa» y no se inventa: la sección no sale.
- Meta de «Sobre el programa»: «objetivo · N semanas · N bloques», como el lienzo.
- Salen de las hojas viejas: el glosario y «Ver en Conceptos clave» (Conceptos clave sigue en Perfil), los desplegables
  «Lo que publica el autor» y «Configuración inicial KPKN», las etiquetas de fila «Material» y «Calendario» (ahora chips) y
  los botones «Cancelar»/«Cerrar» (los sustituyen la X y el gesto de volver). Las prescripciones pasan al formato corto.
- Se conserva la función «Ver la fuente original»: la atribución lleva « ↗» y abre la fuente cuando el plan la publica.
- «Sobre el programa» enseña ahora las notas del plan en «Ten en cuenta», que antes no salían.

## Revisión visual (hojas de renderizado, 29-oct de madrugada)

La noche del 29-oct el teléfono estaba bloqueado (las pantallas de la app no se ven con el bloqueo), así que la revisión se
hizo con `render/ArreglosV1RenderSheetTest` (Robolectric, 360 dp, letra al 100 % y al 130 %; se lanza con
`KPKN_RENDER_DIR=<carpeta>`). Arreglado tras mirarla:

- Barra de guardar: al 130 % cortaba «508 kca» y «2 alimer». Ahora las cifras se miden primero y nunca se cortan; el
  botón se queda con el resto, encoge su verbo y, si ni así cabe, dice solo «Guardar» (la comida ya está en los chips de
  arriba; la descripción accesible conserva la frase entera).
- Ficha reutilizada: la portada en banda partía nombres cortos («Mi PHAT») y la etiqueta «TU PROGRAMA · ACTIVO» junto a
  la ilustración. La ficha del catálogo y «Sobre el programa» usan la portada en cartel, como `.pf-cover` del lienzo (arte
  arriba, título a todo el ancho); el asistente conserva su banda.
- Chip de material: con el catálogo real salía la lista entera de 18 piezas dentro de un chip. Ahora es corto como en el
  lienzo: hasta 3 piezas, la lista; 4 o 5, «Barra, banco y 3 más»; 6 o más, «Gimnasio completo».
- «Tus últimas 4 semanas»: el título de sección ya no se parte («Semana a semana»); si la nota de la derecha no cabe entera
  a su lado (letra grande), baja bajo el título. «S4 · hoy» encoge antes que cortarse. En cada semana, cifra y unidad no se separan («3 h», «4,3 t»): la línea se parte por el « · ».
