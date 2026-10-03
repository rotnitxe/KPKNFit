# Lote 2: sentadillas. Informe para aprobación

Fecha: 2026-10-03 · 25 definiciones · 47 configuraciones · estado: **aplicado el
2026-10-03 con el OK del usuario**, que aprobó todos los cambios escalados.

## Resumen

- Las 25 definiciones pasan de LEGACY a CURATED con ficha completa: texto público, técnica,
  anatomía con su porqué, brief de imagen y fuentes.
- Lint conjunto de las 25: **0 errores y 0 avisos**. Fuentes: 272 citas, todas verificadas en
  PubMed o Crossref.
- Proceso: 5 autores en paralelo, 2 revisores limpios que contrastaron cada rol con el
  resumen de su fuente y la técnica con la web y con tus correcciones, una ronda de
  correcciones y la integración (se reescribieron 26 frases que se repetían entre grupos).
- Tus correcciones previas de imagen quedaron fijadas en la técnica, el prompt, `forbidden` y
  `qa`: Bazuca (barra en un hombro, el brazo del mismo lado), Somersault (barra en la cadera,
  sin disco ni rodilleras), Anderson (desde los pines, rack completo), hack en Smith (barra
  por detrás, baja con el cuerpo), hack invertida (pies en la plataforma normal), V-Squat
  (máquina real), Sissy (talones arriba, cadera extendida, la máquina solo sujeta los pies,
  Smith sin inclinarse como en un remo) y búlgara en polea (banco detrás, poleas bajas de una
  máquina real).

## Errores heredados que se corrigieron (los más graves)

- **Bazuca:** describía pies juntos con la barra en la espalda: otro ejercicio.
- **Somersault:** describía brazos cruzados delante del pecho, que es una sentadilla frontal.
- **Anderson:** decía que se arranca sentado en el suelo; Anderson frontal, desde el suelo.
- **Hack invertida:** un cue decía «de espaldas a la hack», al revés de lo real.
- **Pendular:** decía que oscila la plataforma; la plataforma está fija y oscila el respaldo.
- **V-Squat:** «ángulo fijo, carga guiada detrás»: el carro se mueve en arco sobre una palanca.
- **Búlgara:** la definición se casaba con las mancuernas y las 6 configuraciones eran una
  sola plantilla con el implemento cambiado.
- **Sissy:** nombraba isquiosurales que no declaraba y llamaba «guiada» a la máquina.
- **Sumo:** decía que la postura ancha da protagonismo al interior del muslo (falso).
- **Copa:** los cues hablaban de kettlebell en una configuración de mancuerna.
- **Plantillas:** en todas, los cues eran clones («Preparación: …» + la misma frase) y había
  frases vetadas («la trayectoria permanece estable…», «se equilibra solo», «por excelencia»).

## Cambios que necesitan tu OK (afectan a planes y volumen)

El **músculo dominante no cambia en ninguna configuración**: el cuádriceps sigue primero, así
que la composición de sesiones (máximo 3 ejercicios con el mismo dominante, adyacencias) no se
mueve. Lo que cambia es cuánto suma cada serie a cada músculo en el volumen semanal
(principal 1,0; secundario 0,5; estabilizador 0,4).

1. **Glúteo mayor principal** en las sentadillas cargadas donde estaba rebajado sin base
   (11 configuraciones). El revisor mostró que las hermanas con barra ya lo tenían como
   principal con la misma evidencia (Wretenberg 1996, Bryanton 2012, Contreras 2016, Kubo
   2019). 7 son configuraciones que usa la app o sus tests:
   `high_bar_back_squat__safety_bar`, `quads_sentadilla_copa__default`,
   `quads_sentadilla_anderson__default`, `quads_sentadilla_cajon__default` (código) y
   `quads_sentadilla_anderson_frontal_barra_recta__default`,
   `quads_sentadilla_bazuca__default`, `bulgarian_zercher__barbell__zercher` (tests).
   Efecto: cada serie de estos ejercicios suma 1,0 de glúteo en lugar de 0,5 (o 0).
2. **Aductores secundarios** en 35 configuraciones (sentadillas con barra, frontal, Zercher,
   copa, Anderson, máquinas, sumo, búlgara). Evidencia directa en sentadilla completa y prensa
   (Kubo 2019, Kinoshita 2026); en el resto, declarado como extrapolación. Efecto: +0,5 de
   aductores por serie.
3. **Búlgara:** isquiosurales y pantorrillas secundarios (7 configuraciones) y glúteo medio
   estabilizador.
4. **Estabilizadores añadidos** donde trabajan de verdad: erectores (18), core (15 y 7 que
   bajan de secundario a estabilizador), antebrazo cuando la mano sostiene la carga (9),
   glúteo medio en unilaterales (8), bíceps en las 3 Zercher (sostiene la barra en el codo).
5. **Excepciones de herencia** que declaré en `anatomy_rules.json`, con razón y fuente, como
   autorizaste: bíceps y antebrazo en la búlgara Zercher, bíceps en la sumo Zercher y
   erectores en la hack invertida y la V-Squat invertida.
6. **Articulaciones:** la cadera pasa a principal en las sentadillas cargadas; la rodilla está
   en todas; se quitó la sacroilíaca donde nadie la midió.

**Se mantiene por regla de producto:** el glúteo principal en `quads_sentadilla_sin_carga`.
El autor encontró que sin carga su activación es baja (Kang 2014, 7 % a 60° de rodilla);
quedó escrito en su porqué y la regla no se tocó.

**Pruebas:** el impacto real en los tests de planes, volumen y búsqueda se mide al aplicar.
El clasificador de permisos no deja simularlo antes de tu OK, y está bien. Si algún test se
rompe por estos cambios, te lo traigo antes de commitear.

## Preguntas para ti

Los autores escribieron la versión más común y verificable; donde ni tus correcciones ni la
web lo aclaraban, no inventaron. Si respondes, ajusto la ficha en un mini lote: los cambios de
técnica e imagen no tocan el catálogo de la app.

**Antes de regenerar imágenes:**
1. **Bazuca:** ¿la mano libre ayuda (dónde) o no toca la barra? ¿Qué barra y cómo va cargada?
2. **Somersault:** ¿qué fotograma, el fondo (como está) o el punto alto? ¿Añadimos una
   configuración Smith, que es la versión original?
3. **V-Squat:** la ficha sigue a la Hammer Strength PL-VSQ (eje detrás del atleta, hombreras
   con asas hacia delante, discos detrás a la altura de la cadera). ¿Es tu máquina? En la
   invertida, ¿el pecho va apoyado en el acolchado (como está)?
4. **Pendular:** ¿el respaldo va fijo al brazo? Si es así, en el fondo el tronco va reclinado
   (como quedó).
5. **Belt squat:** ¿máquina de palanca que pasa entre los pies (como está) o de cable con
   cadena por la plataforma?
6. **Búlgara en máquina:** ¿palanca con mangos a los lados y rodillo para el pie trasero
   (como está) o con hombreras?
7. **Búlgara en polea:** con las torres a los lados el cable baja en diagonal hacia fuera
   (como está). ¿Así, o es una máquina de poleas estrecha?
8. **Sumo con mancuerna y con kettlebell:** la ficha y la web dicen una sola carga colgando
   entre las piernas; las imágenes actuales usan dos mancuernas y la kettlebell en copa.
   ¿Confirmas colgando?
9. **Frontal con polea:** ¿barra en rack frontal con una polea baja (como está) o dos agarres a
   la altura del pecho? **Frontal con kettlebell:** ¿dos en rack (como está) o una?

**Detalles que no bloquean:**
10. **Jefferson:** ¿barra cruzada entre las piernas, pie trasero girado y una mano a cada lado
    (como está, según la USAWA) o la otra postura?
11. **Cajón:** ¿la versión de powerlifting, con postura ancha y tibia vertical (como está), o
    cualquier sentadilla que se siente en un cajón?
12. **Sissy en máquina:** ¿se muestra el cojín detrás de la corva? ¿El talón va sobre una cuña
    o se despega? **Con mancuerna o disco:** ¿las dos manos en la carga (como está)?
13. **Búlgara con kettlebell:** ¿una en copa al pecho (como está) o dos colgando?
14. **Unilaterales de belt squat y pendular:** ¿a una pierna con el pie libre en el aire (como
    está) o en zancada? **Prensa a una pierna:** ¿el pie libre va al suelo o al armazón?
15. **Para el lote 4:** en las dominadas, ¿el bíceps es secundario con cualquier agarre? Es
    el único criterio del piloto que quedó sin confirmar.

## Variantes dudosas y patrones (solo reporte, no se tocó nada)

- `low_bar_back_squat__smith_machine` es rara: los rieles quitan el equilibrio sobre el
  mediopié que define la barra baja.
- La sissy con mancuerna y la sissy con disco son casi el mismo ejercicio (candidatas a
  fusión).
- En la barra baja y en la búlgara se mide más momento de cadera que de rodilla; quizá su
  patrón sea `knee_hip_dominant`.
- El revisor sugiere declarar la herencia Anderson ← sentadilla con barra alta para que no
  vuelvan a separarse.

## Cola de imágenes del lote 2

Revisión hecha por inspectores limpios contra las preguntas `qa` y la lista `forbidden` de la
ficha nueva. «PASA» se refiere a la técnica; los defectos de estilo (texto en los discos,
logos, trazo de cómic, fondo blanco) se anotan aparte porque el estilo de la casa los prohíbe.

| Par (definición × implemento) | Imagen actual | Veredicto | Motivo |
|---|---|---|---|
| high_bar_back_squat × barbell | exercise_sentadilla_trasera_barra_alta | FALLA | «20 KG» en los discos, logo en los calcetines; vista frontal; la cadera no baja claramente del paralelo |
| high_bar_back_squat × smith_machine | exercise_sentadilla_trasera_barra_alta_smith | FALLA | Logo en los calcetines; vista frontal |
| high_bar_back_squat × safety_bar | exercise_sentadilla_trasera_barra_alta_safety_bar | FALLA | Texto y logos (el yugo, las asas y la curva de la barra están bien) |
| low_bar_back_squat × barbell | exercise_sentadilla_trasera_barra_baja | FALLA | «20 KG» en los discos (la técnica es correcta) |
| low_bar_back_squat × smith_machine | exercise_sentadilla_trasera_barra_baja_smith | FALLA | «20 KG»; el disco lejano parece por dentro del riel |
| front_squat × barbell | exercise_sentadilla_frontal | FALLA | Codos bajos; «20 KG»; trazo de cómic y fondo blanco |
| front_squat × smith_machine | exercise_sentadilla_frontal_smith | FALLA | Codos caídos; barra sin discos; cómic |
| front_squat × dumbbells | exercise_sentadilla_frontal_mancuernas | FALLA | Mancuernas horizontales delante de la cara, sin apoyo en los hombros |
| front_squat × kettlebell | exercise_sentadilla_frontal_kettlebell | PASA | Estilo: cómic, fondo blanco |
| front_squat × cable | exercise_sentadilla_frontal_polea | FALLA | El cable termina en el aire, sin polea; codos bajos (y depende de tu respuesta sobre la versión con polea) |
| quads_sentadilla_zercher_barra_recta × barbell | exercise_sentadilla_zercher | FALLA | Puños junto a los hombros y barra cruzando los antebrazos (lo prohibido); «20 KG»; cómic |
| quads_sentadilla_copa × dumbbells | exercise_quads_sentadilla_copa_batch8 | PASA | Estilo: camiseta roja, acabado fotográfico |
| quads_sentadilla_anderson × barbell | exercise_sentadilla_anderson | FALLA | La barra flota sobre los brazos de seguridad; un brazo atraviesa los discos; soporte de dos postes |
| quads_sentadilla_anderson_frontal_barra_recta × barbell | exercise_sentadilla_anderson_frontal | FALLA | El brazo de seguridad atraviesa un disco; dos postes; codos no altos |
| quads_sentadilla_bazuca × barbell | exercise_sentadilla_bazuca | FALLA | La agarra la mano contraria cruzando la cara: el error que marcaste |
| quads_sentadilla_somersault × barbell | exercise_sentadilla_somersault | FALLA | No es el fondo del movimiento (lo demás cumple); depende de qué fotograma elijas |
| quads_sentadilla_hack × machine | exercise_sentadilla_hack_maquina | FALLA | Rodillas sin adelantar; parece sentada sobre un cojín; «45LB»; cómic |
| quads_sentadilla_hack × barbell | exercise_sentadilla_hack_barra | PASA | Estilo: cómic |
| quads_sentadilla_hack × smith_machine | exercise_sentadilla_hack_smith | PASA | Vista tres cuartos en vez de lateral |
| quads_sentadilla_hack_invertida_maquina × machine | exercise_sentadilla_hack_invertida | PASA | |
| quads_sentadilla_v_squat × machine | exercise_sentadilla_v_squat | FALLA | Cadera muy por debajo de las rodillas, reclinado; geometría de la máquina a confirmar contigo |
| quads_sentadilla_v_squat_invertida_maquina × machine | exercise_sentadilla_v_squat_invertida | FALLA | Máquina inventada (columna entre las piernas, pieza tipo asiento) |
| belt_squat × machine | exercise_sentadilla_belt_squat | FALLA | No es la máquina: discos apoyados en la base, la carga no cuelga del cinturón |
| pendulum_squat × machine | exercise_sentadilla_pendulo | FALLA | De espaldas a la estructura; plataforma plana; asiento sobre un poste; texto |
| sumo_squat × barbell | exercise_sentadilla_sumo | PASA | Estilo: «20 KG», cómic |
| sumo_squat × dumbbells | exercise_sentadilla_sumo_mancuernas | FALLA | Dos mancuernas, una en cada mano (la ficha y la web: una sola, colgando entre las piernas) |
| sumo_squat × kettlebell | exercise_sentadilla_sumo_kettlebell | FALLA | Kettlebell en copa al pecho (la ficha y la web: colgando del asa) |
| sissy_squat × machine | exercise_sentadilla_sissy_maquina | FALLA | Sentada sobre el rodillo con la cadera a 90°; talones planos |
| sissy_squat × smith_machine | exercise_sentadilla_sissy_smith | FALLA | Colgado de la barra como un remo invertido; sin suelo |
| sissy_squat × dumbbells | exercise_sentadilla_sissy_mancuernas | FALLA | Pies en el aire sin suelo; medio recorrido |
| sissy_squat × plate | exercise_sentadilla_sissy_disco | FALLA | Igual que la de mancuerna |
| bulgarian_split_squat × dumbbells | exercise_sentadilla_bulgara_mancuernas | PASA | |
| bulgarian_split_squat × barbell | exercise_sentadilla_bulgara_barra | PASA | |
| bulgarian_split_squat × smith_machine | exercise_sentadilla_bulgara_smith | FALLA | No hay Smith: barra libre y un soporte sin rieles |
| bulgarian_split_squat × machine | exercise_sentadilla_bulgara_maquina | FALLA | Estación con rodillo y asas sin carga; depende de qué máquina es |
| bulgarian_split_squat × cable | exercise_sentadilla_bulgara_polea | FALLA | Torres delante y detrás del atleta; un mango sin mano |
| bulgarian_split_squat × kettlebell | exercise_sentadilla_bulgara_kettlebell | PASA | Estilo: fondo blanco |
| bulgarian_zercher × barbell | exercise_sentadilla_bulgara_zercher | PASA | |

**Sin imagen todavía (6):** quads_sentadilla_sin_carga, quads_sentadilla_jefferson,
quads_sentadilla_cajon, quads_prensa_piernas, quads_sentadilla_sumo_frontal,
quads_sentadilla_sumo_zercher.

## Anexos

- [Textos nuevos, anatomía y prompt de imagen de cada definición](LOTE_02_TEXTOS.md)
- [Delta anatómico completo por configuración](LOTE_02_ANATOMIA.md)
