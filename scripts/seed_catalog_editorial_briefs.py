#!/usr/bin/env python3
"""Build the reviewable editorial brief source from the curated v4 copy.

The v4 copy is already authored per exercise/configuration.  This migration
turns it into an explicit source contract so later catalog passes cannot fall
back to pattern-level prose.
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from curaduria_v4_descripciones import CFG_DESC, DEF_DESC


ROOT = Path(__file__).resolve().parents[1]
FAMILIES = ROOT / "catalog" / "exercises" / "v2" / "source" / "families"
OUTPUT = ROOT / "catalog" / "exercises" / "v2" / "curation" / "editorial_briefs.json"
REVISION = "v2-approved-2026-08-10-c"


NEW_DEFINITIONS: dict[str, str] = {
    "hammer_curl": (
        "El agarre neutro deja los pulgares arriba y reparte el esfuerzo entre bíceps, braquial y antebrazo. "
        "Es una forma directa de ganar grosor al brazo sin obligar a la muñeca a girar durante cada repetición."
    ),
    "reverse_curl": (
        "Con las palmas hacia abajo, el antebrazo limita la carga antes que el bíceps y cambia por completo la sensación del movimiento. "
        "La variante da protagonismo al braquial y a los extensores, con una demanda de agarre más evidente."
    ),
    "supination": (
        "La rotación hacia palma arriba trabaja el control fino del antebrazo mientras el codo permanece como punto estable. "
        "Es un ejercicio pequeño y preciso para mejorar la resistencia de los músculos que orientan la mano."
    ),
    "pronation": (
        "La rotación hacia palma abajo exige que el antebrazo dirija la mano sin que el hombro compense el gesto. "
        "Su recorrido corto permite entrenar la pronación con una carga muy dosificable."
    ),
}


NEW_CONFIGURATIONS: dict[str, str] = {
    "hammer_curl__h_bar": (
        "Barra H con las manos enfrentadas: la muñeca queda en una posición cómoda y los dos brazos comparten una referencia estable. "
        "Buena opción para acumular repeticiones de agarre neutro con una carga fácil de comparar."
    ),
    "hammer_curl__dumbbells": (
        "Mancuernas con agarre neutro: cada brazo encuentra su propia trayectoria y el pulgar permanece arriba durante todo el recorrido. "
        "La libertad de movimiento ayuda a corregir diferencias entre lados mientras el braquial gana protagonismo."
    ),
    "hammer_curl__cable": (
        "Polea con agarre neutro: el cable conserva tensión cuando el codo se acerca a la flexión completa. "
        "Es la versión más fácil de dosificar para buscar una contracción continua sin cargar de más la muñeca."
    ),
    "hammer_curl__band": (
        "Banda con agarre neutro: la resistencia crece al final del recorrido y hace que la parte alta del curl sea la más exigente. "
        "Una alternativa portátil para terminar el brazo sin depender de una estación fija."
    ),
    "hammer_curl__kettlebell": (
        "Kettlebell con la mano en posición neutra: el peso cuelga por debajo del agarre y obliga a estabilizar la muñeca con cuidado. "
        "La carga compacta cambia la sensación del martillo y añade un reto de control al antebrazo."
    ),
    "reverse_curl__h_bar": (
        "Barra H con las palmas hacia abajo: el agarre fijo mantiene la muñeca alineada y permite concentrar la serie en braquial y antebrazo. "
        "Una opción estable para progresar sin que el giro de la barra distraiga del recorrido."
    ),
    "reverse_curl__dumbbells": (
        "Mancuernas con las palmas hacia abajo: cada lado controla su propia carga y el antebrazo trabaja sin esconder desequilibrios. "
        "La trayectoria independiente resulta útil cuando un brazo pierde la posición antes que el otro."
    ),
    "reverse_curl__cable": (
        "Polea con las palmas hacia abajo: la tensión permanece presente mientras el codo se flexiona y el antebrazo sostiene la línea de la mano. "
        "Permite ajustar la carga en pasos pequeños, algo valioso en una variante limitada por el agarre."
    ),
    "reverse_curl__band": (
        "Banda con las palmas hacia abajo: la resistencia es moderada al iniciar y sube cuando el codo se cierra. "
        "La progresión elástica da una forma sencilla de entrenar el braquial y los extensores en casa."
    ),
    "reverse_curl__kettlebell": (
        "Kettlebell con las palmas hacia abajo: el centro de la carga queda bajo la mano y aumenta la exigencia de estabilización del antebrazo. "
        "Es una variante poco habitual para quien busca un agarre más desafiante que el de una mancuerna."
    ),
    "supination__dumbbells": (
        "Mancuerna ligera para girar la palma hacia arriba: el codo queda fijo y la mano recorre un arco corto y controlado. "
        "Permite trabajar cada antebrazo por separado y comparar la movilidad de ambos lados."
    ),
    "supination__cable": (
        "Polea baja para girar la palma hacia arriba: la tensión acompaña el recorrido y evita que la mano quede sin resistencia al final. "
        "La estación facilita ajustes pequeños cuando el objetivo es precisión más que carga absoluta."
    ),
    "pronation__dumbbells": (
        "Mancuerna ligera para girar la palma hacia abajo: el codo actúa como ancla y el antebrazo organiza todo el movimiento. "
        "El trabajo unilateral deja claro qué lado necesita más control o resistencia."
    ),
    "pronation__cable": (
        "Polea baja para girar la palma hacia abajo: la resistencia sigue a la mano durante la rotación y se regula con mucha precisión. "
        "Una opción cómoda para practicar el gesto sin convertirlo en un movimiento de hombro."
    ),
}


DEFINITION_OVERRIDES: dict[str, str] = {
    "chest_supported_row": (
        "El pecho queda apoyado en un banco y los brazos cuelgan libres para llevar la carga hacia el torso. "
        "El apoyo reduce la demanda de la zona lumbar y permite concentrar la serie en la trayectoria de los codos y la espalda alta."
    ),
    "conventional_row": (
        "El torso se inclina desde la cadera mientras la carga viaja hacia la cintura y los codos retroceden. "
        "La posición exige sostener el tronco, pero ofrece una base sólida para desarrollar fuerza y grosor en toda la espalda."
    ),
    "gironda_row": (
        "La variante Gironda permite que el torso acompañe el recorrido con una inclinación amplia y controlada. "
        "El vaivén aumenta la amplitud del tirón y cambia el protagonismo entre dorsales y espalda alta respecto a un remo estricto."
    ),
    "pendlay_row": (
        "Cada repetición nace con la carga apoyada en el suelo, el torso casi horizontal y el tirón decidido. "
        "El reinicio entre repeticiones limita el impulso y convierte el ejercicio en una prueba clara de fuerza de la espalda alta."
    ),
    "seal_row": (
        "El pecho descansa sobre un banco alto y los brazos quedan suspendidos hacia el suelo antes de cada tirón. "
        "Al desaparecer la ayuda de la cadera, la espalda debe producir y frenar el movimiento con una ejecución especialmente limpia."
    ),
    "t_bar_row": (
        "La carga gira alrededor de un anclaje mientras el torso ofrece una base firme para llevar los codos hacia atrás. "
        "El agarre elegido cambia cuánto se comparte el esfuerzo entre dorsal, trapecio y espalda media."
    ),
}


CONFIGURATION_OVERRIDES: dict[str, str] = {
    "sumo_deadlift__dumbbells": "Mancuernas a los costados con una base amplia: la carga deja espacio entre las piernas y la cadera puede acercarse al suelo. Los glúteos y aductores comparten el despegue con una sensación más libre que la barra.",
    "sumo_squat__dumbbells": "Mancuernas delante del cuerpo y pies abiertos: la sentadilla baja entre las piernas y la cadera recibe más participación. Una opción accesible para explorar la postura amplia sin cargar la espalda.",
    "romanian_deadlift__unilateral__dumbbells": "Una mancuerna acompaña la pierna de trabajo mientras la otra se extiende hacia atrás para equilibrar. El isquio de un lado recibe el estiramiento y el glúteo debe mantener la pelvis orientada.",
    "stiff_leg_deadlift__unilateral__dumbbells": "Una mancuerna desciende junto a la pierna de apoyo mientras la pierna libre se aleja. La versión unilateral hace más evidente el estiramiento de cada isquio y exige sostener la pelvis sin girarla.",
    "triceps_pushdown__bilateral__band": "Banda anclada arriba con ambas manos: la resistencia crece cuando los codos se acercan a la extensión completa. Es una alternativa portátil para rematar el tríceps con un final de recorrido más exigente.",
    "lat_pulldown__bilateral__band": "Banda anclada arriba y tirón con ambos brazos: la carga aumenta al acercar las manos al pecho. El recorrido elástico ofrece una opción práctica para entrenar el dorsal sin una máquina.",
    "lat_pulldown__unilateral__machine": "Máquina con una mano y un dorsal a la vez: el apoyo guiado elimina parte del equilibrio mientras cada lado completa su propio tirón. Ideal para comparar fuerza y recorrido entre dorsales.",
    "pullover__unilateral__machine": "Máquina con un brazo: el hombro recorre su arco por separado y el aparato conserva una línea estable. La variante ayuda a encontrar diferencias de movilidad y control entre ambos dorsales.",
    "back_encogimientos__kettlebell": "Kettlebells a los costados: el peso colgante obliga a fijar la escápula mientras los hombros suben. La carga repartida en cada mano añade una exigencia de agarre que no aparece igual con una barra.",
    "back_encogimientos__smith_machine": "Smith con los hombros libres: la guía permite cargar más sin tener que equilibrar la barra. La referencia vertical facilita repetir el encogimiento y concentrar la tensión en el trapecio.",
    "back_jefferson_curl__barbell": "Barra en las manos y espalda que se redondea de forma progresiva: la carga acompaña cada segmento durante el descenso. La versión bilateral permite comparar el recorrido vértebra a vértebra con una resistencia estable.",
    "back_jefferson_curl__cable": "Polea baja durante el redondeo: el cable conserva tensión cuando la espalda cambia de ángulo. La resistencia continua permite usar menos carga y prestar más atención al control del recorrido.",
    "back_jefferson_curl__dumbbells": "Mancuernas en las manos: cada brazo acompaña la curvatura sin depender de una barra que una ambos lados. La carga libre ofrece una entrada más accesible al trabajo segmentado de la espalda.",
    "calf_raise__unilateral__machine": "Máquina con una pierna: cada pantorrilla eleva el cuerpo por separado y la plataforma elimina parte del problema de equilibrio. Es una forma precisa de comparar fuerza y amplitud entre lados.",
    "california_press__barbell": "Barra hacia el pecho con los codos cerca del cuerpo: el tríceps comparte el empuje con el pectoral sin abrir los brazos. La carga bilateral facilita progresar cuando la prioridad es fuerza.",
    "california_press__dumbbells": "Mancuernas hacia el pecho con los codos recogidos: cada brazo controla su propio descenso y evita que el lado fuerte domine. La variante ayuda a equilibrar el empuje sin perder el carácter híbrido del movimiento.",
    "core_dragon_flag_banco_plano__default": "Versión completa desde el banco: el cuerpo sube y baja como una sola pieza, con las rodillas extendidas y el abdomen frenando cada centímetro. La dificultad está en impedir que la pelvis se descuelgue durante el descenso.",
    "flat_chest_fly__cable": "Polea en banco plano: la tensión permanece desde la apertura hasta el cierre y el pectoral no encuentra un punto de descanso claro. La altura del cable permite ajustar la línea del arco sin cambiar el objetivo del ejercicio.",
    "flat_chest_fly__dumbbells": "Mancuernas en banco plano: los brazos dibujan un arco amplio y el pectoral recibe el estiramiento en la parte baja. La gravedad concentra más exigencia cuando las manos se separan del centro.",
    "forearms_curl_muneca_inverso_sentado__cable": "Polea con las palmas hacia abajo: la mano sube contra una tensión que permanece presente durante todo el recorrido. La carga graduable permite trabajar los extensores sin que el antebrazo abandone el apoyo.",
    "forearms_curl_muneca_inverso_sentado__dumbbells": "Mancuernas con las palmas hacia abajo: ambas muñecas pueden moverse con una carga independiente y fácil de colocar. Es la alternativa más sencilla para localizar el trabajo en la cara posterior del antebrazo.",
    "forearms_curl_muneca_sentado__dumbbells": "Mancuernas con los antebrazos apoyados: cada muñeca flexiona por separado y el peso no permite que el codo fabrique impulso. La opción ofrece control fino para corregir diferencias entre manos.",
    "good_morning__bilateral__safety_bar": "Barra de seguridad sobre los hombros: los agarres frontales acercan la carga y hacen más cómoda la posición de las manos. La variante conserva la exigencia de cadera, pero cambia cómo se sostiene el torso.",
    "hip_thrust__bilateral__barbell": "Barra sobre la cadera con ambas piernas: la espalda alta se apoya en el banco y el glúteo empuja la pelvis hasta alinearla. La carga bilateral ofrece la progresión más directa para acumular fuerza en la extensión de cadera.",
    "jm_press__barbell": "Barra hacia el mentón con los codos cerca: el tríceps inicia el empuje desde una posición intermedia entre press y extensión. La barra permite comparar cargas, aunque exige precisión para que el hombro no robe el recorrido.",
    "jm_press__cable": "Polea hacia el mentón: la tensión acompaña el descenso y el ascenso sin depender de un punto muerto. Es una opción útil para mantener el tríceps activo con menos inercia que una barra.",
    "jm_press__dumbbells": "Mancuernas hacia el mentón: cada brazo encuentra su propia línea y el codo puede permanecer más cómodo. La independencia de las cargas ayuda a detectar cuándo un lado pierde la trayectoria.",
    "jm_press__ez_bar": "Barra EZ hacia el mentón: el ángulo de las manos reduce la rotación exigida a la muñeca mientras el tríceps sigue guiando el gesto. Es una variante cómoda para repetir el patrón con control.",
    "military_press__barbell": "Barra desde los hombros hasta arriba: el cuerpo de pie debe transferir la fuerza sin inclinarse hacia atrás. La carga bilateral ofrece una referencia clara para progresar en el empuje vertical.",
    "military_press__dumbbells": "Mancuernas desde los hombros: cada brazo empuja y estabiliza su propia carga, con una trayectoria que puede adaptarse mejor al hombro. La variante descubre diferencias que la barra puede ocultar.",
    "military_press__machine": "Máquina de empuje vertical: el respaldo y la guía reducen la demanda de equilibrio para que el hombro reciba una carga más directa. Es una opción práctica para acumular volumen sin gastar energía en estabilizar la estación.",
    "rear_delt_raise__cable": "Polea para abrir los brazos hacia atrás: la tensión permanece activa cuando el deltoides posterior se acerca a su contracción. La línea del cable permite ajustar el recorrido sin depender solo de la gravedad.",
    "reverse_hyper__machine": "Máquina con la pelvis apoyada: las piernas se desplazan hacia atrás y la cadera produce la extensión mientras el tronco queda descargado. La estación permite trabajar glúteos sin convertir el movimiento en una extensión lumbar.",
    "reverse_lunge__barbell": "Barra y paso atrás: la pierna adelantada recibe la mayor parte de la carga mientras el retroceso deja espacio para bajar con control. La barra permite una progresión bilateral estable.",
    "reverse_lunge__cable": "Polea y paso atrás: el cable mantiene una línea de resistencia continua mientras la pierna delantera frena y vuelve a empujar. La tensión acompaña la zancada sin depender de una carga que descanse sobre la espalda.",
    "reverse_lunge__dumbbells": "Mancuernas y paso atrás: cada mano lleva una carga independiente y la pierna adelantada organiza la subida. La opción combina libertad de movimiento con una demanda de equilibrio fácil de ajustar.",
    "tate_press__cable": "Polea acostado con los codos cerrándose hacia dentro: la tensión continua mantiene al tríceps activo en el tramo final. La resistencia ajustable permite buscar contracción sin depender de una carga libre sobre la cara.",
    "triceps_patada__dumbbells__bilateral": "Mancuernas inclinado con ambos brazos: los codos quedan atrás y la extensión termina con el tríceps completamente acortado. La carga bilateral facilita repetir una trayectoria simétrica sin impulso.",
    "triceps_press_frances__ez_bar": "Barra EZ acostado: el ángulo de las manos suele resultar más cómodo para muñecas y codos mientras la carga desciende hacia la frente. La variante conserva una progresión bilateral clara.",
    "walking_lunge__barbell": "Barra en la espalda y pasos al frente: cada zancada exige frenar, estabilizar y volver a empujar sin perder la línea. La carga bilateral permite convertir el desplazamiento en un trabajo serio de piernas y glúteos.",
    "z_press__barbell": "Barra sentado en el suelo: sin respaldo, el abdomen debe sostener el torso mientras los hombros empujan por encima de la cabeza. La carga bilateral hace visible cualquier pérdida de postura.",
    "z_press__dumbbells": "Mancuernas sentado en el suelo: cada brazo empuja sin que el torso pueda apoyarse y el recorrido se adapta a cada hombro. La variante combina independencia de carga con una demanda alta de estabilidad.",
    "z_press__ez_bar": "Barra EZ sentado en el suelo: el agarre girado ofrece una posición más amable para las manos sin quitar la exigencia del torso libre. Es una opción estable para practicar el empuje vertical sentado.",
    "z_press__kettlebell": "Kettlebell sentado en el suelo: la carga desplazada sobre la mano aumenta el trabajo de estabilización mientras el abdomen mantiene la postura. Es la opción más irregular y exigente de esta familia.",
}

# These are the row entries that exposed the old failure most clearly.  Keep
# each technical option authored in its own voice: the implement, line of
# pull, torso support and grip must explain a real training consequence rather
# than act as a noun swap inside one sentence frame.
CONFIGURATION_OVERRIDES.update({
    "conventional_row__barbell": (
        "Barra frente a los muslos, tronco inclinado y codos hacia la cintura: la carga permite progresar con una referencia estable para toda la espalda. "
        "La barra bilateral facilita comparar cargas, pero exige sostener la inclinación desde la cadera sin convertir el tirón en balanceo."
    ),
    "conventional_row__cable": (
        "Polea baja frente al cuerpo: el cable mantiene tensión mientras el torso conserva una inclinación fija. "
        "La resistencia continua favorece repeticiones controladas y permite ajustar el esfuerzo sin perder la trayectoria hacia la cintura."
    ),
    "conventional_row__dumbbells": (
        "Mancuernas suspendidas bajo los hombros: cada brazo puede elegir una línea ligeramente distinta mientras el tronco permanece inclinado. "
        "La independencia de las cargas ayuda a detectar asimetrías y añade una demanda de estabilización en cada mano."
    ),
    "conventional_row__kettlebell": (
        "Kettlebells colgando entre las piernas: el centro de masa bajo el agarre añade trabajo de muñeca y exige que el torso no se desorganice. "
        "La carga dividida ofrece una trayectoria flexible, aunque el agarre puede limitar la serie antes que la espalda."
    ),
    "conventional_row__machine": (
        "Máquina de remo con el pecho libre: el recorrido guiado reduce la necesidad de equilibrar la carga y deja más energía para la tracción. "
        "El torso permanece inclinado y los codos marcan el camino hacia la cintura sin que la estación decida por completo la posición del tronco."
    ),
    "conventional_row__smith_machine": (
        "Smith con la barra en una trayectoria fija: el guiado reduce el componente de equilibrio mientras el tronco sostiene la posición inclinada. "
        "Permite concentrar la progresión en la extensión del hombro y en la aproximación de los codos a la cintura."
    ),
    "gironda_row__close": (
        "Agarre cerrado en el Gironda: los codos viajan cerca del cuerpo y el dorsal recibe una línea larga de tracción. "
        "El torso acompaña el tirón con un vaivén deliberado; la amplitud procede de una inclinación controlada, no de un rebote."
    ),
    "gironda_row__medium": (
        "Agarre medio en el Gironda: la trayectoria queda entre el tirón hacia la cintura y la apertura hacia la espalda alta. "
        "El torso se inclina y vuelve de forma coordinada, repartiendo el trabajo entre dorsal, romboides y trapecio medio."
    ),
    "gironda_row__wide": (
        "Agarre amplio en el Gironda: los codos se separan y la espalda alta recibe más protagonismo durante el vaivén. "
        "La variante exige que la inclinación del tronco siga siendo amplia pero organizada para no sustituir la tracción por impulso."
    ),
    "pendlay_row__barbell": (
        "Barra apoyada en el suelo y torso casi paralelo: cada repetición comienza desde cero y termina con la carga otra vez separada del cuerpo. "
        "El reinicio elimina el rebote y permite valorar la fuerza de la espalda alta con una posición inicial repetible."
    ),
    "pendlay_row__cable": (
        "Polea baja con el torso horizontal: la empuñadura parte de una posición detenida antes de cada tirón y el cable conserva tensión al volver. "
        "La variante combina el reinicio propio del Pendlay con una resistencia continua que hace visible cualquier pérdida de postura."
    ),
    "pendlay_row__dumbbells": (
        "Dos mancuernas parten del suelo y suben hacia el torso con la espalda paralela a la plataforma. "
        "Cada lado debe producir su propio tirón y volver a apoyar la carga, lo que reduce la ayuda del lado dominante entre repeticiones."
    ),
    "pendlay_row__kettlebell": (
        "Kettlebells apoyadas delante de los pies: el agarre queda bajo y obliga a organizar muñecas, escápulas y tronco antes de despegar. "
        "La carga independiente conserva el reinicio, pero añade una estabilización más cambiante que la barra."
    ),
    "pendlay_row__machine": (
        "Máquina con el tronco casi horizontal y las asas detenidas al inicio: el apoyo del aparato limita la deriva de la trayectoria. "
        "La estación permite practicar el arranque desde una referencia estable y concentrar la fuerza en la espalda alta."
    ),
    "pendlay_row__smith_machine": (
        "Smith desde la posición baja: la barra se detiene entre repeticiones y vuelve a subir por una línea guiada. "
        "El recorrido fijo simplifica el equilibrio, pero mantiene la exigencia de colocar el torso y producir cada tirón sin rebote."
    ),
    "chest_supported_row__cable__high__close": (
        "Polea alta y agarre cerrado con el pecho apoyado: la línea descendente lleva los codos cerca del costado y favorece la tracción del dorsal. "
        "El banco descarga la zona lumbar para que la atención quede en el camino de las manos hacia el torso."
    ),
    "chest_supported_row__cable__high__medium": (
        "Polea alta y agarre medio con el pecho apoyado: los codos bajan con una apertura moderada y combinan dorsal con espalda media. "
        "La resistencia continua mantiene tensión durante el regreso sin permitir que el banco se despegue del pecho."
    ),
    "chest_supported_row__cable__high__wide": (
        "Polea alta y agarre amplio con el pecho apoyado: los codos se abren y la línea de tirón alcanza con mayor claridad la espalda alta. "
        "El apoyo elimina la ayuda de la cadera, de modo que el ancho del agarre se traduce en una diferencia real de trayectoria."
    ),
    "chest_supported_row__cable__low__close": (
        "Polea baja y agarre cerrado con el pecho apoyado: las manos suben hacia la parte baja del torso mientras los codos permanecen próximos. "
        "La combinación favorece una línea de dorsal larga y permite regular la carga sin perder el contacto con el banco."
    ),
    "chest_supported_row__cable__low__medium": (
        "Polea baja y agarre medio con el pecho apoyado: la tracción asciende hacia el torso y los codos recorren una diagonal intermedia. "
        "El cable mantiene resistencia al final del tirón mientras el apoyo limita el balanceo y la extensión lumbar."
    ),
    "chest_supported_row__cable__low__wide": (
        "Polea baja y agarre amplio con el pecho apoyado: los codos se separan y la carga sube hacia la zona media del pecho. "
        "La línea ascendente exige coordinar escápulas y espalda alta sin que la cadera contribuya al movimiento."
    ),
    "chest_supported_row__cable__mid__close": (
        "Polea media y agarre cerrado con el pecho apoyado: la empuñadura llega hacia la cintura con los codos recogidos. "
        "La tensión sostenida permite evaluar el dorsal durante todo el recorrido, incluida la vuelta con los brazos extendidos."
    ),
    "chest_supported_row__cable__mid__medium": (
        "Polea media y agarre medio con el pecho apoyado: los codos siguen una diagonal equilibrada entre el costado y la espalda alta. "
        "El cable y el banco ofrecen una referencia constante para repartir el trabajo entre dorsal y romboides."
    ),
    "chest_supported_row__cable__mid__wide": (
        "Polea media y agarre amplio con el pecho apoyado: la apertura de los codos desplaza el foco hacia romboides y trapecio medio. "
        "La resistencia horizontal exige completar la retracción escapular sin elevar los hombros."
    ),
    "chest_supported_row__dumbbells__close": (
        "Mancuernas y agarre cerrado con el pecho apoyado: los codos descienden junto al cuerpo y cada brazo conserva su propia trayectoria. "
        "La libertad unilateral permite ajustar el recorrido a cada hombro sin devolver la carga a la zona lumbar."
    ),
    "chest_supported_row__dumbbells__medium": (
        "Mancuernas y agarre medio con el pecho apoyado: la apertura moderada de los codos reparte la demanda entre dorsal y espalda media. "
        "Cada mano puede corregir su ángulo, mientras el banco impide que el tronco convierta la serie en un balanceo."
    ),
    "chest_supported_row__dumbbells__wide": (
        "Mancuernas y agarre amplio con el pecho apoyado: los codos se separan para acercar las cargas a la parte alta del torso. "
        "La trayectoria independiente facilita comparar la retracción escapular de ambos lados con la zona lumbar descargada."
    ),
    "chest_supported_row__kettlebell__close": (
        "Kettlebells y agarre cerrado con el pecho apoyado: el peso cuelga bajo la mano y los codos permanecen cerca del costado. "
        "La carga compacta aumenta la estabilización de muñeca mientras el banco mantiene fija la base del tirón."
    ),
    "chest_supported_row__kettlebell__medium": (
        "Kettlebells y agarre medio con el pecho apoyado: la carga desplazada obliga a organizar la muñeca cuando los codos retroceden. "
        "La apertura intermedia distribuye el esfuerzo entre dorsal y espalda media sin permitir ayuda de la cadera."
    ),
    "chest_supported_row__kettlebell__wide": (
        "Kettlebells y agarre amplio con el pecho apoyado: los codos se abren mientras el peso bajo el agarre exige estabilidad en cada mano. "
        "La variante lleva la tracción hacia la espalda alta y hace especialmente importante no elevar los hombros."
    ),
    "chest_supported_row__machine__close": (
        "Máquina y agarre cerrado con el pecho apoyado: el recorrido guiado mantiene los codos junto al cuerpo y reduce la variación entre repeticiones. "
        "La estación deja concentrar la serie en la aproximación de los brazos al torso con la zona lumbar descargada."
    ),
    "chest_supported_row__machine__medium": (
        "Máquina y agarre medio con el pecho apoyado: la guía fija una apertura moderada para repartir el tirón entre dorsal y espalda media. "
        "La resistencia se puede progresar sin que el equilibrio o la cadera cambien la trayectoria."
    ),
    "chest_supported_row__machine__wide": (
        "Máquina y agarre amplio con el pecho apoyado: los codos se separan siguiendo un recorrido predeterminado hacia la espalda alta. "
        "El apoyo y la guía reducen las compensaciones, pero obligan a respetar el arco que marca la estación."
    ),
    "seal_row__barbell": (
        "Barra bajo el banco alto: el pecho queda completamente apoyado y ambos brazos tiran con una referencia común. "
        "La carga bilateral simplifica la progresión, mientras la altura del banco impide usar la cadera para iniciar el remo."
    ),
    "seal_row__dumbbells": (
        "Mancuernas bajo el banco alto: cada brazo sale desde una posición suspendida y vuelve a extenderse sin que el tronco se mueva. "
        "La trayectoria independiente permite ajustar el codo a cada hombro y revela diferencias que una barra puede ocultar."
    ),
    "t_bar_row__machine__close": (
        "Máquina T con agarre cerrado: las asas acercan los codos al cuerpo y la guía mantiene la carga en una línea repetible. "
        "La posición favorece una tracción más dirigida al dorsal sin exigir que el torso equilibre el aparato."
    ),
    "t_bar_row__machine__medium": (
        "Máquina T con agarre medio: el recorrido guiado coloca los codos en una diagonal intermedia entre dorsal y espalda alta. "
        "La estación permite graduar la carga y repetir el ángulo sin que la barra se desplace lateralmente."
    ),
    "t_bar_row__machine__wide": (
        "Máquina T con agarre amplio: las asas separan los codos y llevan el trabajo hacia romboides y trapecio medio. "
        "El guiado limita la deriva, pero la amplitud elegida determina la posición del hombro durante el tirón."
    ),
    "t_bar_row__t_bar__close": (
        "Barra T con agarre cerrado: el anclaje mantiene una trayectoria estable mientras los codos avanzan cerca del costado. "
        "La carga libre del extremo permite progresar con fuerza, aunque exige que el torso sostenga la base sin desplazarse."
    ),
    "t_bar_row__t_bar__medium": (
        "Barra T con agarre medio: el anclaje ofrece una línea diagonal y el codo queda entre el costado y la apertura de la espalda alta. "
        "Es una posición intermedia para repartir el trabajo sin perder la estabilidad que aporta la carga guiada por el pivote."
    ),
    "t_bar_row__t_bar__wide": (
        "Barra T con agarre amplio: las manos abiertas separan los codos y orientan el tirón hacia la espalda alta. "
        "El anclaje permite cargar de forma estable, pero el torso debe permanecer firme para que la apertura no se convierta en impulso."
    ),
})


MUSCLE_LABELS = {
    "abdominals": "el abdomen",
    "adductors": "los aductores",
    "biceps": "el bíceps",
    "calves": "la pantorrilla",
    "core": "el core",
    "deltoid": "el deltoides",
    "erector_spinae": "los erectores espinales",
    "forearm": "el antebrazo",
    "gluteus_maximus": "el glúteo mayor",
    "gluteus_medius": "el glúteo medio",
    "hamstrings": "los isquiosurales",
    "hip_flexors": "los flexores de cadera",
    "latissimus_dorsi": "el dorsal",
    "pectoralis": "el pectoral",
    "quadriceps": "los cuádriceps",
    "rhomboids": "los romboides",
    "trapezius": "el trapecio",
    "triceps": "el tríceps",
}


TECHNIQUE_TAILS = {
    "chest_supported_row": "El pecho permanece en contacto con el banco; los codos viajan según el agarre y la carga vuelve sin despegar el torso.",
    "conventional_row": "La cadera fija la inclinación del torso y los codos llevan la carga hacia la cintura sin convertir el remo en un balanceo.",
    "gironda_row": "El recorrido nace de una inclinación controlada del torso; la espalda acompaña el vaivén sin perder la tensión en los dorsales.",
    "pendlay_row": "Cada repetición empieza con la carga apoyada y el torso casi paralelo al suelo; el reinicio elimina el rebote entre repeticiones.",
    "seal_row": "El pecho queda completamente apoyado y los brazos cuelgan libres; la espalda inicia el tirón sin ayuda de la cadera.",
    "t_bar_row": "La carga permanece anclada mientras el torso ofrece una base firme; los codos suben hacia atrás siguiendo el ancho elegido.",
    "floor_press": "El suelo limita el descenso cuando los brazos llegan a la línea del torso; la subida parte de una pausa estable.",
    "bench_press": "El banco fija la espalda y la carga desciende hacia la parte elegida del pecho antes de volver con los antebrazos alineados.",
    "decline_bench_press": "El respaldo declinado cambia la línea del empuje hacia el pecho inferior y acorta la sensación de recorrido en el hombro.",
    "incline_bench_press": "El respaldo inclinado orienta el empuje hacia la zona alta del pecho; la barra o las manos regresan sin perder el apoyo escapular.",
    "flat_chest_fly": "Los brazos describen un arco amplio sin convertir el gesto en un press; el pecho recibe el estiramiento y se cierra sin rebote.",
    "incline_chest_fly": "El banco inclinado eleva la trayectoria de las manos y exige cerrar el arco sobre la parte alta del pecho sin encoger los hombros.",
    "decline_chest_fly": "El ángulo declinado dirige el arco hacia la zona inferior del pecho; la amplitud se detiene antes de perder la posición del hombro.",
    "reverse_pec_fly": "Los brazos se abren hacia atrás con el torso estable; la variante unilateral permite terminar cada lado sin que el hombro contrario compense.",
    "biceps_curl_bayesian": "El brazo queda detrás del tronco y el codo permanece quieto mientras el antebrazo cierra el ángulo con tensión desde el inicio.",
    "spider_curl": "El pecho apoyado elimina el impulso y deja el brazo colgando; el codo se mantiene bajo el hombro mientras el bíceps acorta.",
    "preacher_curl": "El apoyo del banco detiene el hombro y deja que el codo flexione desde una base estable, con una bajada que no se acelera.",
    "standing_biceps_curl": "El tronco permanece vertical y el brazo acompaña el codo sin adelantarse; la muñeca conserva el agarre elegido.",
    "hammer_curl": "El pulgar apunta hacia arriba y el codo se mantiene cerca del costado; la mano sube sin convertir el gesto en un balanceo de hombro.",
    "reverse_curl": "Las palmas miran al suelo y la muñeca se mantiene firme; el codo flexiona mientras el antebrazo soporta la mayor parte de la limitación.",
    "conventional_deadlift": "La carga sale del suelo cerca de las piernas; cadera y rodillas se extienden juntas hasta que el cuerpo queda alineado.",
    "sumo_deadlift": "La base amplia deja los brazos dentro de las piernas; la cadera se acerca a la carga antes de iniciar una subida vertical.",
    "romanian_deadlift": "La cadera viaja atrás con las rodillas poco flexionadas y la carga roza las piernas; la subida termina al recuperar la línea del tronco.",
    "stiff_leg_deadlift": "Las piernas permanecen casi extendidas y la cadera se desplaza atrás hasta el límite del estiramiento, sin buscar profundidad a costa de la espalda.",
    "good_morning": "La carga descansa sobre el cuerpo mientras la cadera se aleja y el torso se inclina como una unidad; la subida nace de los isquios y glúteos.",
    "hip_abduction": "La pierna se separa sin girar la pelvis; la posición sentada, de pie o unilateral cambia cuánto debe estabilizarse la cadera.",
    "hip_adduction": "La pierna vuelve hacia la línea media con el tronco quieto; el apoyo elegido decide cuánto participa la cadera que sostiene el cuerpo.",
    "hip_extension": "La pelvis se extiende hasta quedar alineada con el tronco; el recorrido termina antes de sustituir el glúteo por una hiperextensión lumbar.",
    "hip_thrust": "La espalda alta apoya en el banco y la pelvis sube hasta quedar alineada; la carga se mantiene sobre la cadera sin perder el apoyo de los pies.",
    "knee_dominant": "El pie permanece completo en contacto con la base y la rodilla sigue la dirección de los dedos mientras el cuerpo baja y vuelve.",
    "unilateral_knee_dominant": "La pierna de trabajo recibe la bajada y la subida; el apoyo libre acompaña sin robar fuerza ni dejar que la pelvis se incline.",
    "knee_flexion": "La rodilla flexiona acercando el talón y vuelve con una fase lenta; la cadera permanece fijada por el banco, la máquina o el apoyo disponible.",
    "plantar_flexion": "El talón desciende para cargar la pantorrilla y luego sube hasta una flexión plantar completa, sin convertir el recorrido en un rebote.",
    "vertical_pull": "Los codos descienden hacia el costado elegido y la carga vuelve arriba sin perder el control del hombro ni encogerlo hacia las orejas.",
    "vertical_push": "El brazo sube en el plano de la variante y el codo termina el empuje; el tronco ofrece una base que no se arquea para ganar altura.",
    "elbow_extension": "El brazo superior queda como referencia y el codo se abre hasta completar la extensión; el hombro no roba el recorrido.",
    "elbow_flexion": "El brazo superior se mantiene estable mientras el antebrazo se acerca al hombro; el agarre cambia qué músculos comparten la flexión.",
    "trunk_flexion": "Las costillas se acercan a la pelvis sin tirar del cuello; la resistencia acompaña la flexión y la vuelta no se convierte en caída.",
    "trunk_rotation": "El torso rota alrededor de la pelvis con la carga cerca; la dirección diagonal decide qué lado inicia y cuál frena el gesto.",
    "anti_extension_trunk": "El cuerpo se alarga sin dejar que la zona lumbar se hunda; la resistencia se combate con costillas y pelvis organizadas.",
    "anti_rotation_trunk": "La carga intenta girar el torso y el abdomen responde manteniendo el pecho orientado; la distancia al anclaje define la dificultad.",
    "wrist_flexion": "El antebrazo queda apoyado y la mano se mueve desde la muñeca; el codo no acompaña para fabricar un recorrido mayor.",
    "wrist_extension": "El antebrazo se fija mientras el dorso de la mano sube contra la resistencia; el recorrido se mantiene corto y limpio.",
}


def _sentences(text: str) -> list[str]:
    return [part.strip() for part in re.split(r"(?<=[.!?])\s+", text.strip()) if part.strip()]


def _without_canonical_name(value: str, canonical_name: str) -> str:
    replacements = {
        "rueda abdominal": "esta rueda",
        "inclinación lateral": "inclinación del tronco",
        "tibial anterior": "parte frontal de la pierna",
        "dragon flag": "este ejercicio",
        "reverse hyper": "este movimiento",
        "hip thrust": "este empuje de cadera",
        "paseo del granjero": "caminata con carga",
        "buenos días": "este ejercicio de cadera",
        "sentadilla frontal": "esta sentadilla",
        "sentadilla sumo": "esta sentadilla amplia",
        "sentadilla hack": "esta sentadilla guiada",
        "press california": "este press de tríceps",
        "jm press": "este press de tríceps",
        "tate press": "este press de tríceps",
        "press francés": "este press de tríceps",
        "jefferson curl": "este movimiento de movilidad",
        "curl de muñeca": "este trabajo de muñeca",
        "curl de isquiosurales": "este trabajo de isquiosurales",
    }
    replacement = replacements.get(canonical_name.casefold().strip(), "este movimiento")
    return re.sub(rf"(?<!\w){re.escape(canonical_name)}(?!\w)", replacement, value, flags=re.IGNORECASE)


def _sanitize_description(value: str) -> str:
    replacements = {
        # Remove copywriter scaffolding left by the legacy prose and repair
        # article collisions introduced when a canonical name is hidden from
        # the visible text.
        r"^Este movimiento(?: de la barra| en polea| de movilidad)?\s*:\s*": "",
        r"\bEl este\b": "Este",
        r"\bLa esta\b": "Esta",
        r"\bEl referencia\b": "La referencia",
        r"\bEl rey indiscutible\b": "La referencia directa",
        r"\bmás más\b": "más",
        r"\bmuy más\b": "más",
        r"\bLa muy popular para trabajar la pierna\b": "Permite trabajar cada pierna por separado",
        r"\bLa muy utilizada para series largas de zancada\b": "Permite acumular series de zancadas",
        r"\bLa muy utilizada para la espalda ancha\b": "Se utiliza para desarrollar la tracción vertical con los codos abiertos",
        r"\bLa muy utilizada para trabajar piernas y glúteos\b": "Permite trabajar piernas y glúteos con apoyo estable",
        r"\bEl muy utilizado para esculpir el pico del brazo\b": "Permite concentrar el trabajo en la flexión del codo",
        r"\bEl muy utilizado para dar forma a la clavícula\b": "Permite cargar la porción superior del pectoral",
        r"\bEl referencia directa para construir glúteos con carga, el muy popular\b": "Una referencia directa para progresar la extensión de cadera",
        r"\bLa muy utilizada\b": "La variante se utiliza",
        r"\bEl muy utilizado\b": "Se utiliza",
        r"\bLa muy popular\b": "La variante se utiliza",
        r"\bEl muy popular\b": "Se utiliza",
        # Replace verdicts and gym slang with observable training effects.
        r"\bLa opción de los que buscan cargar peso de verdad\b": "Facilita progresar con cargas altas",
        r"\bEl remo de los que entrenan fuerza global\b": "El reinicio desde el suelo permite valorar la fuerza de la espalda alta",
        r"\bEl clásico de los que aman el detalle\b": "Permite concentrar el trabajo en el bíceps",
        r"\bLa habitual de los que buscan detalle\b": "Permite reducir el impulso y observar la trayectoria",
        r"\bLa mejor vía para progresar hacia la dominada\b": "Una progresión directa hacia la dominada",
        r"\bque todo el mundo necesita\b": "útil para preparar la tracción escapular",
        r"\bque todo el mundo debería hacer\b": "que puede utilizarse para preparar la tracción escapular",
        r"\bde los entendidos\b": "con una técnica que exige precisión",
        r"\bgimnasio serio\b": "cargas altas",
        r"\bespalda seria\b": "espalda desarrollada",
        r"\bpiernas serias\b": "piernas fuertes",
        r"\bforma más seria de igualar\b": "forma exigente de igualar",
        r"\batletas serios\b": "personas con experiencia",
        r"\ben serio\b": "con una demanda alta",
        r"\bcero compensaciones\b": "menos oportunidades de compensación",
        r"\baislamiento total\b": "un aislamiento mayor",
        r"\bcon hombros felices\b": "sin cargar de más el hombro",
        r"\bhombros felices\b": "hombros sin carga excesiva",
        r"\bfuerza bruta\b": "fuerza de tracción",
        r"\bde principio a fin\b": "durante todo el recorrido",
        r"\badictiv[oa]\b": "exigente",
        r"\bdemoledor\b": "exigente",
        r"\bconfianza\b": "control",
        r"\bseguridad\b": "estabilidad",
        r"\bsegura\b": "estable",
        r"\bseguro\b": "estable",
        r"\bseguras\b": "estables",
        r"\bseguramente\b": "de forma estable",
        r"\bexprimir\b": "cargar",
        r"\bexprim[ae]\b": "carga",
        r"\bpopular\b": "utilizada",
        r"\blegendario\b": "tradicional",
        r"\blegendaria\b": "tradicional",
        r"\bdirecto del legendario\b": "en su versión tradicional",
        r"\bversión pulida\b": "versión controlada",
        r"\bvariante original\b": "variante alternativa",
        r"\bángulo fresco\b": "ángulo distinto",
        r"\bque sorprende\b": "que cambia la línea de resistencia",
        r"\bsorprende a los brazos\b": "cambia la línea de resistencia para el brazo",
        r"\benciende el glúteo\b": "aumenta la tensión del glúteo",
        r"\benciende el abdomen\b": "aumenta la demanda del abdomen",
        r"\benciende los trapecios\b": "aumenta la tensión del trapecio",
        r"\benciende la espalda\b": "aumenta la tensión de la espalda",
        r"\bque enciende\b": "que aumenta la tensión de",
        r"\besculpe\b": "carga",
        r"\besculpir\b": "cargar",
        r"\bdar forma a\b": "desarrollar",
        r"\bpico del bíceps\b": "porción acortada del bíceps",
        r"\bestética\b": "desarrollo muscular",
        r"\bcontracción más intensa que existe\b": "contracción claramente localizada",
        r"\bincomparable\b": "marcada",
        # Subjective heat/pump language is replaced with fatigue or tension.
        r"\blo hace arder\b": "aumenta la fatiga local de",
        r"\bhace arder el\b": "aumenta la fatiga local del",
        r"\bhace arder la\b": "aumenta la fatiga local de la",
        r"\barden\b": "acumulan fatiga local",
        r"\barde\b": "acumula fatiga local",
        r"\bqueman\b": "acumulan fatiga local",
        r"\bquema\b": "aumenta la fatiga local",
        r"\bquemando\b": "acumulando fatiga en",
        r"\bbombeo\b": "tensión continua",
        r"\bse siente clarísima\b": "queda claramente localizada",
        r"\bse siente igual de exigente\b": "mantiene una demanda similar",
        r"\bse siente como ningún otro\b": "mantiene una tensión distinta",
        r"\bse siente muy distinto\b": "cambia claramente la línea de tracción",
        r"\bse siente más fuerte\b": "aumenta la demanda del brazo",
        r"\bque se siente única\b": "con una contracción claramente localizada",
        r"\bse siente al final de la serie\b": "mantiene tensión al final de la serie",
        r"\bse sienten?\b": "se perciben",
        r"\bnatural y estable\b": "estable",
        r"\bmuy natural\b": "más libre",
        r"\bnatural para el hombro\b": "adaptable para el hombro",
        r"\blibre y natural\b": "libre y adaptable",
        r"\bpausa natural\b": "pausa estable",
        r"\bmuy completo\b": "con una demanda amplia",
        r"\bmuy completa\b": "con una demanda amplia",
        r"\baislamiento puro\b": "trabajo localizado",
        r"\bcontracción más pura\b": "contracción más localizada",
        r"\bel dorsal siente\b": "el dorsal recibe",
        r"\bsiente el estiramiento\b": "recibe el estiramiento",
        r"\bfácil de hacer en casa o en el parque\b": "práctico para entrenar fuera del gimnasio",
        r"\bfácil de progresar\b": "con progresión gradual",
        r"\bmuy efectiva\b": "útil para acumular trabajo",
        r"\bmuy efectivo\b": "útil para acumular trabajo",
        r"\bperfecto\b": "útil",
        r"\bperfecta\b": "útil",
        r"\bperfectos\b": "útiles",
        r"\bperfectas\b": "útiles",
        r"\bideal\b": "útil",
        r"\bideales\b": "útiles",
        r"\bclásicos\b": "tradicionales",
        r"\bclásico\b": "tradicional",
        r"\bclásicas\b": "tradicionales",
        r"\bclásica\b": "tradicional",
        r"\bbrutal\b": "exigente",
        r"\bbrutales\b": "exigentes",
        r"\brey indiscutible\b": "referencia directa",
        r"\bEl rey del\b": "Una referencia para el",
        r"\bel rey del\b": "una referencia para el",
        r"\brey de\b": "referencia para",
        r"\breina\b": "referencia",
        r"\bejercicio rey\b": "ejercicio de referencia",
        r"\bde toda la vida\b": "clásico",
        r"\bemociona como pocos ejercicios\b": "permite medir la fuerza de empuje con claridad",
        r"\ba tope\b": "con intensidad",
        r"\bfuerza de verdad\b": "fuerza global",
        r"\bsin pensar en nada más\b": "sin distraerte con el equilibrio",
        r"\bsin pensar en nada\b": "sin distraerte con el equilibrio",
        r"\bno se asustan con nada\b": "ya toleran una demanda alta",
        r"\ben llamas\b": "bajo una tensión intensa",
        r"\bfavorito de todos\b": "muy popular",
        r"\bfavorita de todos\b": "muy popular",
        r"\bfavorito para\b": "muy utilizado para",
        r"\bfavorita para\b": "muy utilizada para",
        r"\bfavorito\b": "habitual",
        r"\bfavorita\b": "habitual",
        r"\bfino\b": "preciso",
        r"\bfina\b": "precisa",
        r"\bamable\b": "tolerante",
        r"\bpuro y duro\b": "directo",
        r"\bquemar\b": "acumular fatiga",
        r"\bardiendo\b": "bajo una tensión intensa",
        r"\badrenalina\b": "demanda",
        r"\bmucho respeto\b": "mucho control",
        r"\btrampas\b": "compensaciones",
        r"\btrampa\b": "compensación",
        r"\bcon ganas\b": "con mayor participación",
        r"\btrabaja de verdad\b": "trabaja sin apoyos externos",
        r"\bvieja escuela\b": "tradicional",
        r"\bfuerza mental\b": "resistencia muscular",
        r"\bmantén\b": "conserva",
        r"\bmantener\b": "conservar",
        r"\bcontrola\b": "dirige",
        r"\bcontrolar\b": "dirigir",
        r"\basegura\b": "refuerza",
        r"\basegurar\b": "reforzar",
        r"\bevita\b": "impide",
        r"\bevitar\b": "impedir",
        r"\bselecciona\b": "elige",
        r"\bconfigura\b": "prepara",
        r"\badopta\b": "usa",
        r"\bejecuta\b": "realiza",
        r"\bbombeando\b": "elevando",
        r"\bforma original\b": "forma alternativa",
        r"\bsorprendente\b": "marcada",
        r"\bse enciende\b": "aumenta su tensión",
        r"\bde una forma que no encontrarás en ningún otro ejercicio\b": "con una demanda sostenida de los aductores",
        r"\bmás controvertido y más respetado entre los que saben\b": "con una técnica que exige progresión y control",
        r"\bestímulo al máximo\b": "tensión durante todo el recorrido",
    }
    for pattern, replacement in replacements.items():
        value = re.sub(pattern, replacement, value, flags=re.IGNORECASE)

    # Some legacy phrases create a second adjective only after an earlier
    # replacement (for example, "favorita" -> "muy utilizada").  Run a
    # final grammar pass after the semantic substitutions so those artifacts
    # cannot reach the reviewable brief source.
    final_replacements = {
        r"\bmás\s+más\b": "más",
        r"\bEl referencia\b": "La referencia",
        r"\bEl muy utilizado\b": "Se utiliza",
        r"\bEl muy popular\b": "Una variante utilizada",
        r"\bLa muy popular para trabajar la pierna\b": "Permite trabajar cada pierna por separado",
        r"\bLa muy utilizada para la espalda ancha\b": "Una variante para trabajar la espalda alta con los codos abiertos",
        r"\bLa muy utilizada para series largas de zancada\b": "Permite acumular series de zancadas",
        r"\bLa muy utilizada\b": "Una variante utilizada",
        r"\bLa muy popular\b": "Una variante utilizada",
        r"\bpara cargar la porción acortada del bíceps\b": "para concentrar la flexión del codo",
        r"\bpara desarrollar la clavícula\b": "para cargar la porción superior del pectoral",
        r"\bse perciben cada fibra\b": "la tensión se concentra durante todo el recorrido",
        r"\bde forma sorprendente\b": "con una demanda de tensión continua",
        r"\bsorprendentemente bueno\b": "con una demanda técnica alta",
        r"\bvariante completa y original\b": "variante amplia y alternativa",
        r"\btrabajo serio\b": "trabajo exigente",
        r"\bmás honesto para el agarre\b": "más directo para el agarre",
        r"\bversión más honesta y difícil del trabajo abdominal\b": "versión exigente del trabajo abdominal, con pocas oportunidades de compensación",
        r"\bhabitual de los que buscan detalle\b": "útil para observar la trayectoria",
        r"\bhabitual de los que buscan acumular fatiga\b": "útil para acumular trabajo con tensión continua",
        r"\bque adoran los que cuidan los hombros\b": "que facilita una posición cómoda para los hombros",
        r"\bhace arder los\b": "aumenta la fatiga local de los",
        r"\bhace arder las\b": "aumenta la fatiga local de las",
        r"\bar(d|de)\b": "acumula fatiga local",
        r"\bdel esta\b": "de esta",
        r"\bel esta\b": "este",
        r"\bclásicos\b": "tradicionales",
        r"\bclásico\b": "tradicional",
        r"\bclásicas\b": "tradicionales",
        r"\bclásica\b": "tradicional",
        r"\bcon sin cargar de más el hombro\b": "sin cargar de más el hombro",
        r"\bmuy efectivos\b": "útiles para acumular trabajo",
        r"\bmuy efectivas\b": "útiles para acumular trabajo",
        r"\bopción muy utilizada\b": "variante utilizada",
        r"\bde una forma marcada\b": "con una demanda marcada",
        r"\bpostura más natural\b": "postura estable",
        r"\bpostura natural\b": "postura estable",
        r"\blibertad de siempre\b": "trayectoria libre",
        r"\bmás famoso del mundo\b": "de referencia para el brazo",
        r"\bdel mundo del fitness\b": "en el entrenamiento de fuerza",
        r"\bmás puro que existe\b": "más directo para",
        r"\bse dispara\b": "aumenta",
        r"\bse encienden\b": "aumentan su tensión",
        r"\benciende el pecho\b": "aumenta la tensión del pecho",
        r"\btrabaja solo\b": "trabaja sin ayuda del otro lado",
        r"\btodo el trabajo\b": "la mayor parte de la carga",
        r"\baísla cada\b": "concentra cada",
        r"\baísla\b": "concentra",
        r"\baislado\b": "concentrado",
        r"\bvariante poco común\b": "variante menos habitual",
        r"\braro\b": "poco habitual",
        r"\bhace llorar a los glúteos\b": "acumula fatiga local en los glúteos",
        r"\bde las películas de artes marciales\b": "con una exigencia alta de control del tronco",
        r"\bpress plano camuflado de estable\b": "press plano con recorrido limitado y estable",
        r"\bsentir el glúteo\b": "localizar el glúteo",
        r"\bsigue a la mano\b": "acompaña a la mano",
        r"\bsigue guiando\b": "continúa guiando",
        r"\bsigue determinando\b": "determina",
        r"\bsigue siendo\b": "permanece",
    }
    for pattern, replacement in final_replacements.items():
        value = re.sub(pattern, replacement, value, flags=re.IGNORECASE)
    value = re.sub(
        r"(?<=[.!?])\s+([a-záéíóúüñ])",
        lambda match: " " + match.group(1).upper(),
        value,
    )
    return value[:1].upper() + value[1:] if value else value


def _lower_first(text: str) -> str:
    return text[:1].lower() + text[1:] if text else text


def _target(profile: dict[str, Any]) -> str:
    labels = [MUSCLE_LABELS.get(item, item.replace("_", " ")) for item in profile.get("primaryMuscles", [])[:2]]
    if not labels:
        return "la musculatura objetivo"
    if len(labels) == 1:
        return labels[0]
    return f"{labels[0]} y {labels[1]}"


def _config_package(definition: dict[str, Any], configuration: dict[str, Any], description: str) -> dict[str, Any]:
    sentences = _sentences(description)
    first = sentences[0] if sentences else description
    second = sentences[1] if len(sentences) > 1 else first
    definition_id = definition["id"]
    profile = configuration["profile"]
    technique_tail = TECHNIQUE_TAILS.get(
        profile.get("movementPatternId"),
        "La trayectoria permanece estable y la vuelta conserva la tensión en la zona que define esta variante.",
    )
    if definition_id in TECHNIQUE_TAILS:
        technique_tail = TECHNIQUE_TAILS[definition_id]
    # The authored sentences are already benefit-bearing prose. Keeping them
    # intact avoids damaging Spanish grammar with one universal sentence frame.
    benefits = [first, second]
    if len(benefits[0].strip()) < 40:
        benefits[0] = f"{benefits[0].rstrip('.')}; el apoyo y la trayectoria mantienen el objetivo de esta configuración."
    if len(benefits[1].strip()) < 40:
        benefits[1] = f"{benefits[1].rstrip('.')}; una diferencia concreta para repartir el esfuerzo durante la serie."
    rationale = f"{first} {second}"
    technique = f"{first} {technique_tail}"
    return {
        "description": description,
        "benefits": benefits,
        "techniqueSummary": technique,
        "variantRationale": rationale,
        "setupCues": [f"Preparación: {first}"],
        "executionCues": [technique],
    }


def _canonical_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def build() -> dict[str, Any]:
    definitions: dict[str, Any] = {}
    seen_definitions: set[str] = set()
    seen_configurations: set[str] = set()
    for path in sorted(FAMILIES.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        for definition in payload["family"]["definitions"]:
            definition_id = definition["id"]
            if definition_id in seen_definitions:
                raise SystemExit(f"duplicate definition: {definition_id}")
            seen_definitions.add(definition_id)
            description = DEFINITION_OVERRIDES.get(definition_id) or DEF_DESC.get(definition_id) or NEW_DEFINITIONS.get(definition_id)
            if not description:
                raise SystemExit(f"missing authored definition brief: {definition_id}")
            description = _sanitize_description(_without_canonical_name(description, definition["canonicalName"]))
            configurations: dict[str, Any] = {}
            for configuration in definition["configurations"]:
                configuration_id = configuration["id"]
                if configuration_id in seen_configurations:
                    raise SystemExit(f"duplicate configuration: {configuration_id}")
                seen_configurations.add(configuration_id)
                copy = CONFIGURATION_OVERRIDES.get(configuration_id) or CFG_DESC.get(configuration_id) or NEW_CONFIGURATIONS.get(configuration_id)
                if not copy:
                    raise SystemExit(f"missing authored configuration brief: {configuration_id}")
                copy = _sanitize_description(_without_canonical_name(copy, definition["canonicalName"]))
                configurations[configuration_id] = _config_package(definition, configuration, copy)
            definitions[definition_id] = {
                "description": description,
                "configurations": configurations,
            }
    if len(definitions) != 196 or len(seen_configurations) != 518:
        raise SystemExit(f"unexpected inventory: definitions={len(definitions)} configurations={len(seen_configurations)}")
    return {
        "schemaVersion": 1,
        "catalogRevision": REVISION,
        "source": "curaduria_v4_descripciones.py + editorial completion for v2-catalog additions",
        "definitions": definitions,
    }


def main() -> int:
    payload = build()
    OUTPUT.write_bytes(_canonical_json(payload))
    print(f"wrote={OUTPUT}")
    print(f"definitions={len(payload['definitions'])}")
    print(f"configurations={sum(len(item['configurations']) for item in payload['definitions'].values())}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
