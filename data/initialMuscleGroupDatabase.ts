// data/initialMuscleGroupDatabase.ts
import { MuscleGroupInfo } from '../types';

export const INITIAL_MUSCLE_GROUP_DATA: MuscleGroupInfo[] = [
    // --- CATEGORÍAS PRINCIPALES ---
    {
        id: 'pectoral',
        name: 'Pectoral',
        description: 'El pectoral mayor y menor son los principales músculos del pecho, responsables de la aducción, flexión y rotación interna del brazo.',
        importance: {
            movement: 'Fundamental para todos los movimientos de empuje, como empujar una puerta o levantar algo del suelo.',
            health: 'Un pectoral fuerte y flexible contribuye a una buena postura y a la salud del hombro.',
        },
        volumeRecommendations: { mev: '10', mav: '12-20', mrv: '22' },
        coverImage: 'https://images.pexels.com/photos/4162489/pexels-photo-4162489.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=2',
    },
    {
        id: 'espalda',
        name: 'Espalda',
        description: 'Un complejo grupo de músculos que incluye los dorsales, trapecios, romboides y erectores espinales. Son cruciales para la postura y los movimientos de tracción.',
        importance: {
            movement: 'Esencial para tirar de objetos, trepar y mantener una postura erguida.',
            health: 'Una espalda fuerte es la base de una columna vertebral sana y previene el dolor lumbar.',
        },
        volumeRecommendations: { mev: '10', mav: '14-22', mrv: '25' },
        coverImage: 'https://images.pexels.com/photos/14878278/pexels-photo-14878278.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=2',
    },
    {
        id: 'hombros',
        name: 'Hombros',
        description: 'Los deltoides son el principal músculo del hombro, dividido en tres cabezas (anterior, lateral y posterior). Son responsables de la abducción, flexión y extensión del brazo.',
        importance: {
            movement: 'Permiten levantar los brazos en todas las direcciones. Cruciales para la mayoría de movimientos del tren superior.',
            health: 'Unos hombros fuertes y estables son vitales para prevenir lesiones, especialmente en la articulación del hombro, que es muy móvil.',
        },
        volumeRecommendations: { mev: '8', mav: '12-20', mrv: '25' },
    },
    {
        id: 'deltoides', name: 'Deltoides',
        description: 'El músculo deltoides es el que da la forma redondeada al hombro. Está compuesto por tres cabezas: anterior, lateral y posterior, cada una con una función distinta.',
        importance: { movement: 'Responsable de levantar el brazo en todas las direcciones (flexión, abducción, extensión). Esencial para cualquier movimiento por encima de la cabeza.', health: 'Un desarrollo equilibrado de las tres cabezas es crucial para la salud y estabilidad de la articulación del hombro, previniendo lesiones por desbalances.' },
        volumeRecommendations: { mev: '8', mav: '12-20', mrv: '25' },
    },
    {
        id: 'brazos',
        name: 'Brazos',
        description: 'Compuestos principalmente por el bíceps y el tríceps. El bíceps flexiona el codo, mientras que el tríceps lo extiende.',
        importance: {
            movement: 'Involucrados en casi todas las acciones de empuje (tríceps) y tracción (bíceps).',
            health: 'Contribuyen a la salud y estabilidad de las articulaciones del codo y hombro.',
        },
        volumeRecommendations: { mev: '8', mav: '14-20', mrv: '25' },
    },
    {
        id: 'piernas',
        name: 'Piernas',
        description: 'El grupo muscular más grande del cuerpo, incluye cuádriceps, isquiotibiales, glúteos y gemelos. Responsables de la locomoción y la producción de fuerza.',
        importance: {
            movement: 'Fundamentales para caminar, correr, saltar y levantar objetos pesados. Son el motor principal del cuerpo.',
            health: 'Unas piernas fuertes mejoran el metabolismo, la salud cardiovascular y la estabilidad de las rodillas y caderas.',
        },
        volumeRecommendations: { mev: '8', mav: '12-18', mrv: '20' },
    },
     {
        id: 'abdomen',
        name: 'Abdomen',
        description: 'Incluye el recto abdominal y los oblicuos. Son responsables de la flexión y rotación del tronco.',
        importance: {
            movement: 'Permiten doblar el torso y girar. Son clave en la transferencia de fuerza entre el tren superior e inferior.',
            health: 'Un abdomen fuerte ayuda a proteger la columna lumbar y a mantener una buena postura.',
        },
        volumeRecommendations: { mev: '0-4', mav: '10-16', mrv: '20' },
    },
    {
        id: 'core',
        name: 'Core',
        description: 'El Core es un complejo de músculos que estabilizan la columna y la pelvis. No solo incluye los abdominales, sino también la espalda baja, glúteos y el transverso abdominal. Su función principal es la estabilidad y la transferencia de fuerzas.',
        importance: {
            movement: 'Es la base de todo movimiento. Un core fuerte permite transferir eficientemente la fuerza desde el tren inferior al superior, aumentando el rendimiento en todos los levantamientos y deportes.',
            health: 'Un core estable es la mejor protección contra el dolor de espalda baja y las lesiones relacionadas con la inestabilidad de la columna.',
        },
        volumeRecommendations: { mev: '4-6', mav: '10-16', mrv: '20' },
    },
    // --- MÚSCULOS ESPECÍFICOS ---
    // Pecho
    { id: 'pectoral-superior', name: 'Pectoral Superior', description: 'La porción clavicular del pectoral, responsable de la flexión y aducción del hombro en ángulos elevados.', importance: { movement: 'Crucial para empujes inclinados y levantar objetos por encima de la cabeza.', health: 'Un desarrollo equilibrado con el resto del pectoral es clave para la estética y la salud del hombro.'}, volumeRecommendations: { mev: '4-6', mav: '10-12', mrv: '15' }, },
    { id: 'pectoral-medio', name: 'Pectoral Medio', description: 'La porción esternal del pectoral, la más grande y fuerte. Es la principal responsable de la aducción horizontal del brazo (abrazar).', importance: { movement: 'Motor principal en el press de banca plano y otros empujes horizontales.', health: 'Fundamental para la fuerza de empuje general.'}, volumeRecommendations: { mev: '6', mav: '10-16', mrv: '20' }, },
    { id: 'pectoral-inferior', name: 'Pectoral Inferior', description: 'La porción abdominal del pectoral. Se encarga de la aducción y depresión del hombro.', importance: { movement: 'Clave en movimientos de empuje declinado y fondos en paralelas.', health: 'Contribuye al contorno inferior del pecho.'}, volumeRecommendations: { mev: '2-4', mav: '6-10', mrv: '12' }, },
    
    // Espalda
    { id: 'dorsal-ancho', name: 'Dorsal Ancho', description: 'El músculo más grande de la espalda, responsable de la aducción, extensión y rotación interna del brazo. Da la "amplitud" a la espalda.', importance: { movement: 'Es el motor principal en movimientos de tracción vertical como las dominadas y la escalada.', health: 'Contribuye a la estabilidad de la columna y la articulación del hombro.'}, volumeRecommendations: { mev: '8', mav: '12-18', mrv: '22' }, },
    { id: 'redondo-mayor', name: 'Redondo Mayor', description: 'A menudo llamado "el pequeño ayudante del dorsal", asiste en la aducción y extensión del hombro.', importance: { movement: 'Trabaja en sinergia con el dorsal en todos los movimientos de tracción.', health: 'Contribuye a la salud y movilidad de la escápula.'}, volumeRecommendations: { mev: '0', mav: '4-8', mrv: '12' }, },
    { id: 'trapecio', name: 'Trapecio', description: 'Músculo grande y superficial que se extiende desde el cráneo hasta la mitad de la espalda. Se divide en porciones superior, media e inferior, cada una con funciones distintas.', importance: { movement: 'Responsable de elevar, retraer y deprimir la escápula. Esencial para la estabilidad del hombro y movimientos de tracción.', health: 'Unos trapecios fuertes y equilibrados son clave para una buena postura y la prevención de dolores de cuello y hombros.'}, volumeRecommendations: { mev: '4', mav: '12-20', mrv: '25' } },
    { id: 'trapecio-superior', name: 'Trapecio Superior', description: 'Responsable de la elevación de la escápula (encoger los hombros).', importance: { movement: 'Clave para sostener cargas pesadas (paseo del granjero, peso muerto) y en movimientos por encima de la cabeza.', health: 'A menudo está sobreactivo y tenso; es importante equilibrarlo con el fortalecimiento del trapecio inferior.'}, volumeRecommendations: { mev: '0', mav: '8-12', mrv: '20' }, },
    { id: 'trapecio-medio', name: 'Trapecio Medio', description: 'Responsable de la retracción escapular (juntar los omóplatos).', importance: { movement: 'Fundamental para la estabilidad en todos los movimientos de remo y press de banca.', health: 'Esencial para una buena postura y la salud del hombro, contrarrestando la protracción de hombros.'}, volumeRecommendations: { mev: '6', mav: '12-16', mrv: '20' }, },
    { id: 'trapecio-inferior', name: 'Trapecio Inferior', description: 'Responsable de la depresión de la escápula (bajar los omóplatos).', importance: { movement: 'Actúa como estabilizador en presses por encima de la cabeza.', health: 'Crucial para la salud del hombro y una correcta mecánica escapular. A menudo es un músculo débil.'}, volumeRecommendations: { mev: '4', mav: '8-12', mrv: '15' }, },
    { id: 'romboides', name: 'Romboides', description: 'Músculos ubicados entre la columna y la escápula. Su función principal es la retracción escapular.', importance: { movement: 'Trabajan junto al trapecio medio en todos los movimientos de remo.', health: 'Vitales para una postura erguida y la salud del hombro.'}, volumeRecommendations: { mev: '6', mav: '12-16', mrv: '20' }, },
    { id: 'erectores-espinales', name: 'Erectores Espinales', description: 'Grupo de músculos que recorren la columna vertebral. Su función es extender la columna y mantener una postura erguida.', importance: { movement: 'Fundamentales para la estabilización en sentadillas, pesos muertos y cualquier levantamiento pesado.', health: 'Una musculatura espinal fuerte es la mejor defensa contra el dolor de espalda baja.' }, volumeRecommendations: { mev: '0', mav: '4-8', mrv: '12' } },

    // Hombros
    { id: 'deltoides-anterior', name: 'Deltoides Anterior', description: 'La cabeza frontal del hombro. Es responsable de la flexión y rotación interna del brazo.', importance: { movement: 'Principal motor en los presses verticales y actor secundario en los presses horizontales.', health: 'Suele estar sobre-desarrollado en comparación con las otras cabezas, lo que puede causar desbalances posturales.'}, volumeRecommendations: { mev: '0-4', mav: '6-10', mrv: '12' }, },
    { id: 'deltoides-lateral', name: 'Deltoides Lateral', description: 'La cabeza media del hombro. Es responsable de la abducción del brazo (levantarlo hacia el lado).', importance: { movement: 'Permite levantar objetos hacia los lados y es el principal contribuyente a la "anchura" de los hombros.', health: 'Un desarrollo equilibrado es clave para la estética y la salud del hombro.'}, volumeRecommendations: { mev: '6-8', mav: '12-20', mrv: '25' }, },
    { id: 'deltoides-posterior', name: 'Deltoides Posterior', description: 'La cabeza trasera del hombro. Se encarga de la extensión y rotación externa del brazo.', importance: { movement: 'Fundamental en movimientos de tracción horizontal (remos, face pulls).', health: 'Crucial para una buena postura (contrarresta los hombros adelantados) y la estabilidad de la articulación del hombro.'}, volumeRecommendations: { mev: '6-8', mav: '12-20', mrv: '25' }, },

    // Brazos
    { id: 'bíceps', name: 'Bíceps', description: 'El bíceps braquial, ubicado en la parte frontal del brazo. Está compuesto por una cabeza larga y una corta, y su función principal es la flexión del codo y la supinación del antebrazo.', importance: { movement: 'Esencial para todos los movimientos de tracción y para levantar objetos.', health: 'Contribuye a la estabilidad de la articulación del codo.'}, volumeRecommendations: { mev: '8', mav: '14-20', mrv: '22' }, },
    { id: 'cabeza-larga-bíceps', name: 'Cabeza Larga (Bíceps)', description: 'Porción externa del bíceps. Es más activa cuando el brazo está extendido detrás del cuerpo (estiramiento).', importance: { movement: 'Contribuye al "pico" del bíceps. Se enfatiza en ejercicios como el curl inclinado.', health: 'Juega un rol importante en la estabilización de la articulación del hombro.' }, volumeRecommendations: { mev: '4', mav: '8-12', mrv: '15' } },
    { id: 'cabeza-corta-bíceps', name: 'Cabeza Corta (Bíceps)', description: 'Porción interna del bíceps. Se activa más cuando el brazo está por delante del cuerpo.', importance: { movement: 'Añade grosor al bíceps. Se enfatiza en ejercicios como el curl predicador.', health: 'Potente flexor del codo.' }, volumeRecommendations: { mev: '4', mav: '8-12', mrv: '15' } },
    { id: 'braquial', name: 'Braquial', description: 'Músculo ubicado debajo del bíceps. Es un flexor puro del codo, independientemente de la posición del antebrazo.', importance: { movement: 'Contribuye significativamente a la fuerza de flexión del codo, especialmente en agarres neutros o pronos.', health: 'Un braquial desarrollado añade grosor al brazo.'}, volumeRecommendations: { mev: '4-6', mav: '8-12', mrv: '15' }, },
    { id: 'braquiorradial', name: 'Braquiorradial', description: 'Músculo prominente en la parte superior y externa del antebrazo. Flexiona el codo, especialmente con un agarre neutro.', importance: { movement: 'Potente flexor del codo, clave en ejercicios como el curl martillo.', health: 'Contribuye a la fuerza de agarre y la estabilidad de la muñeca.'}, volumeRecommendations: { mev: '2-4', mav: '6-10', mrv: '12' }, },
    { id: 'tríceps', name: 'Tríceps', description: 'El tríceps braquial ocupa toda la parte posterior del brazo y tiene tres cabezas (larga, lateral, medial). Es el principal extensor del codo.', importance: { movement: 'Es el músculo principal en todos los movimientos de empuje (presses). Constituye aproximadamente 2/3 de la masa del brazo.', health: 'Fundamental para la estabilidad del codo y la fuerza de bloqueo.'}, volumeRecommendations: { mev: '6', mav: '10-14', mrv: '18' }, },
    { id: 'cabeza-larga-tríceps', name: 'Cabeza Larga (Tríceps)', description: 'La única cabeza del tríceps que cruza la articulación del hombro. Se estira y trabaja mejor cuando el brazo está por encima de la cabeza.', importance: { movement: 'Clave en extensiones sobre la cabeza (press francés). Añade la mayor parte de la masa a la parte posterior del brazo.', health: 'Ayuda en la extensión y aducción del hombro.' }, volumeRecommendations: { mev: '3-4', mav: '6-10', mrv: '12' } },
    { id: 'cabeza-lateral-tríceps', name: 'Cabeza Lateral (Tríceps)', description: 'Ubicada en la parte exterior del brazo, da la forma de "herradura" al tríceps.', importance: { movement: 'Muy activa en todos los movimientos de press y extensiones con agarre prono.', health: 'Potente extensor del codo.' }, volumeRecommendations: { mev: '2-3', mav: '4-6', mrv: '9' } },
    { id: 'cabeza-medial-tríceps', name: 'Cabeza Medial (Tríceps)', description: 'Ubicada debajo de las otras dos cabezas, es activa en casi todos los movimientos de extensión del codo.', importance: { movement: 'Trabaja en todos los rangos de movimiento, pero se enfatiza con agarres supinos (invertidos).', health: 'Proporciona estabilidad al codo.' }, volumeRecommendations: { mev: '1-2', mav: '3-5', mrv: '7' } },
    { id: 'antebrazo', name: 'Antebrazo', description: 'Conjunto de músculos responsables de los movimientos de la muñeca y los dedos, cruciales para la fuerza de agarre.', importance: { movement: 'Fundamental para sostener la barra o mancuernas en casi todos los ejercicios.', health: 'Un agarre fuerte es un indicador de salud general y previene lesiones.' }, volumeRecommendations: { mev: '2', mav: '6-12', mrv: '16' } },
    { id: 'flexores-de-antebrazo', name: 'Flexores de Antebrazo', description: 'Grupo de músculos en la cara interna del antebrazo, responsables de la flexión de la muñeca y los dedos.', importance: { movement: 'Fundamentales para la fuerza de agarre (grip strength).', health: 'Un agarre fuerte es crucial para progresar en casi todos los ejercicios de espalda y peso muerto.'}, volumeRecommendations: { mev: '2-4', mav: '8-12', mrv: '16' }, },
    { id: 'extensores-de-antebrazo', name: 'Extensores de Antebrazo', description: 'Grupo de músculos en la cara externa del antebrazo, responsables de la extensión de la muñeca y los dedos.', importance: { movement: 'Estabilizan la muñeca en los movimientos de empuje.', health: 'Equilibrar la fuerza con los flexores es clave para prevenir lesiones como el codo de tenista.'}, volumeRecommendations: { mev: '2-4', mav: '8-12', mrv: '16' }, },

    // Piernas
    { id: 'cuádriceps', name: 'Cuádriceps', description: 'Grupo de cuatro músculos en la parte frontal del muslo. Su función principal es la extensión de la rodilla.', importance: { movement: 'Esencial para levantarse de una silla, subir escaleras, correr y saltar.', health: 'Unos cuádriceps fuertes son vitales para la estabilidad y salud de la articulación de la rodilla.'}, volumeRecommendations: { mev: '6', mav: '10-15', mrv: '18' }, },
    { id: 'vasto-lateral', name: 'Vasto Lateral', description: 'La porción externa del cuádriceps. Contribuye significativamente al "barrido" o anchura de la pierna.', importance: { movement: 'Potente extensor de rodilla.', health: 'Ayuda a estabilizar la rótula.'}, volumeRecommendations: { mev: '4', mav: '8-12', mrv: '15' }, },
    { id: 'vasto-medial', name: 'Vasto Medial', description: 'La porción interna del cuádriceps, con forma de "lágrima".', importance: { movement: 'Extensor de rodilla, especialmente crucial en los últimos grados de extensión.', health: 'Fundamental para la estabilidad y el seguimiento correcto de la rótula.'}, volumeRecommendations: { mev: '4', mav: '8-12', mrv: '15' }, },
    { id: 'recto-femoral', name: 'Recto Femoral', description: 'Músculo único del cuádriceps que cruza dos articulaciones: la rodilla y la cadera.', importance: { movement: 'Extiende la rodilla y también flexiona la cadera.', health: 'Su flexibilidad es importante para una correcta mecánica de la pelvis.'}, volumeRecommendations: { mev: '3', mav: '6-10', mrv: '12' }, },
    { id: 'isquiosurales', name: 'Isquiosurales', description: 'Grupo de músculos en la parte posterior del muslo. Son responsables de la flexión de la rodilla y la extensión de la cadera.', importance: { movement: 'Cruciales para correr, esprintar y en la fase de ascenso de las sentadillas y pesos muertos.', health: 'La flexibilidad y fuerza de los isquiotibiales son vitales para la salud de la rodilla y la espalda baja.'}, volumeRecommendations: { mev: '4-6', mav: '8-12', mrv: '16' }, },
    { id: 'bíceps-femoral', name: 'Bíceps Femoral', description: 'Parte externa de los isquiosurales, con una cabeza larga y una corta. Extiende la cadera y flexiona la rodilla.', importance: { movement: 'Potente motor en la extensión de cadera y crucial para la desaceleración al correr.', health: 'Suele ser propenso a lesiones por desgarro en deportes explosivos.'}, volumeRecommendations: { mev: '2-3', mav: '4-6', mrv: '8' }, },
    { id: 'semitendinoso', name: 'Semitendinoso', description: 'Parte interna de los isquiosurales. Extiende la cadera, flexiona la rodilla y ayuda en la rotación interna de la tibia.', importance: { movement: 'Trabaja en conjunto con los otros isquiosurales en todos sus movimientos principales.', health: 'Contribuye a la estabilidad medial de la rodilla.'}, volumeRecommendations: { mev: '1-2', mav: '3-5', mrv: '7' }, },
    { id: 'semimembranoso', name: 'Semimembranoso', description: 'El más profundo de los isquiosurales internos. Extiende la cadera, flexiona la rodilla y rota internamente la tibia.', importance: { movement: 'Potente extensor de cadera y flexor de rodilla.', health: 'Proporciona estabilidad a la parte posterior e interna de la rodilla.'}, volumeRecommendations: { mev: '1-2', mav: '3-5', mrv: '7' }, },
    { id: 'aductores', name: 'Aductores', description: 'Grupo de músculos en la parte interna del muslo. Su función principal es la aducción (acercar la pierna al centro del cuerpo).', importance: { movement: 'Clave en la sentadilla profunda, cambios de dirección y patadas.', health: 'Importantes estabilizadores de la pelvis y la rodilla.'}, volumeRecommendations: { mev: '2-4', mav: '6-10', mrv: '14' }, },
    { id: 'glúteos', name: 'Glúteos', description: 'Compuesto por el glúteo mayor, medio y menor. Es uno de los músculos más fuertes del cuerpo, responsable de la extensión y rotación externa de la cadera.', importance: { movement: 'Motor principal para la extensión de cadera en movimientos como correr, saltar y levantar pesos muertos.', health: 'Unos glúteos fuertes y activos son cruciales para prevenir el dolor de espalda baja y mejorar la estabilidad pélvica.'}, volumeRecommendations: { mev: '4-6', mav: '10-16', mrv: '20' }, },
    { id: 'glúteo-mayor', name: 'Glúteo Mayor', description: 'El músculo más grande y fuerte del cuerpo. Su función principal es la extensión de la cadera.', importance: { movement: 'Potencia movimientos como sentadillas, peso muerto, hip thrusts y sprints.', health: 'Esencial para la estabilidad pélvica y la fuerza general.'}, volumeRecommendations: { mev: '4', mav: '8-12', mrv: '16' }, },
    { id: 'glúteo-medio', name: 'Glúteo Medio', description: 'Ubicado en la parte lateral de la cadera, es el principal abductor de la misma.', importance: { movement: 'Estabiliza la pelvis al caminar, correr o estar sobre una pierna.', health: 'Un glúteo medio débil está asociado con dolor de rodilla y espalda baja ("valgo de rodilla").'}, volumeRecommendations: { mev: '2', mav: '6-10', mrv: '12' }, },
    { id: 'glúteo-menor', name: 'Glúteo Menor', description: 'Ubicado debajo del glúteo medio, asiste en la abducción y estabilización de la cadera.', importance: { movement: 'Trabaja en conjunto con el glúteo medio para la estabilidad pélvica.', health: 'Contribuye a la salud de la articulación de la cadera.'}, volumeRecommendations: { mev: '0', mav: '4-8', mrv: '10' }, },
    { id: 'pantorrillas', name: 'Pantorrillas', description: 'Grupo muscular en la parte posterior de la pierna inferior, compuesto por el gastrocnemio y el sóleo.', importance: { movement: 'Responsables de la flexión plantar (ponerse de puntillas), esencial para caminar, correr y saltar.', health: 'La flexibilidad del sóleo es clave para una buena dorsiflexión de tobillo, necesaria para una sentadilla profunda.'}, volumeRecommendations: { mev: '6-8', mav: '12-16', mrv: '20' }, },
    { id: 'gastrocnemio', name: 'Gastrocnemio', description: 'La parte más visible y superficial de la pantorrilla. Es un músculo biarticular que cruza la rodilla y el tobillo.', importance: { movement: 'Potente flexor plantar, especialmente con la pierna extendida. Contribuye a la potencia en saltos.', health: 'Suele acortarse, lo que puede afectar la movilidad del tobillo.'}, volumeRecommendations: { mev: '4-6', mav: '8-12', mrv: '16' }, },
    { id: 'sóleo', name: 'Sóleo', description: 'Músculo ubicado debajo del gastrocnemio. Es un potente flexor plantar.', importance: { movement: 'Trabaja principalmente cuando la rodilla está flexionada (ej. elevación de talones sentado). Es clave para la resistencia al correr.', health: 'Su flexibilidad es fundamental para una dorsiflexión de tobillo adecuada.'}, volumeRecommendations: { mev: '4-6', mav: '8-12', mrv: '16' }, },
    
    // Abdomen
    { id: 'recto-abdominal', name: 'Recto Abdominal', description: 'El "six-pack". Su función principal es la flexión de la columna vertebral (hacer un crunch).', importance: { movement: 'Permite flexionar el tronco.', health: 'Estabiliza la pelvis y protege los órganos internos.'}, volumeRecommendations: { mev: '0', mav: '10-16', mrv: '20' }, },
    { id: 'oblicuos', name: 'Oblicuos', description: 'Músculos a los lados del abdomen (interno y externo). Responsables de la inclinación lateral y rotación del tronco.', importance: { movement: 'Clave en deportes que implican lanzamientos, giros o golpes.', health: 'Actúan como un corsé natural, protegiendo la columna de fuerzas de rotación.'}, volumeRecommendations: { mev: '0', mav: '8-12', mrv: '16' }, },
    { id: 'transverso-abdominal', name: 'Transverso Abdominal', description: 'El músculo más profundo del abdomen, que actúa como una faja natural para el tronco.', importance: { movement: 'No produce movimiento, pero es el estabilizador más importante del core. Se activa al "meter la guata" o prepararse para un golpe.', health: 'Fundamental para la estabilidad de la columna lumbar y la transferencia de fuerza entre las extremidades.'}, volumeRecommendations: { mev: '0', mav: '6-10', mrv: '12' }, },
    
    // Otros
    { id: 'serrato-anterior', name: 'Serrato Anterior', description: 'Músculo ubicado en la parte lateral del tórax, sobre las costillas. Es responsable de la protracción de la escápula (alejarla de la columna).', importance: { movement: 'Esencial para los movimientos de empuje como los presses y para la mecánica correcta del hombro al levantar el brazo.', health: 'Un serrato débil es una causa común de "escápula alada" y puede contribuir al dolor de hombro.'}, volumeRecommendations: { mev: '0', mav: '6-10', mrv: '14' }, },
    { id: 'tibial-anterior', name: 'Tibial Anterior', description: 'Músculo en la parte frontal de la espinilla. Es el principal responsable de la dorsiflexión (levantar la punta del pie).', importance: { movement: 'Crucial para caminar y correr sin tropezar. Actúa como desacelerador al aterrizar de un salto.', health: 'Un tibial anterior fuerte ayuda a equilibrar las fuerzas en la parte inferior de la pierna, previniendo la periostitis tibial (shin splints).'}, volumeRecommendations: { mev: '0', mav: '6-10', mrv: '12' }, },
    
    // Nuevos Músculos del Core Profundo
    { 
        id: 'multífidos',
        name: 'Multífidos',
        description: 'Son una serie de músculos pequeños y profundos que se extienden a lo largo de la columna vertebral. Actúan como estabilizadores segmentarios, proporcionando rigidez y control a cada vértebra individualmente.',
        importance: {
            movement: 'No son motores primarios del movimiento, sino estabilizadores finos que se activan antes de cualquier movimiento del tronco o las extremidades para proteger la columna.',
            health: 'Una atrofia o disfunción de los multífidos está fuertemente asociada con el dolor de espalda baja crónico. Su entrenamiento es clave para la rehabilitación y prevención de lesiones espinales.'
        },
        volumeRecommendations: { mev: 'N/A', mav: 'N/A', mrv: 'N/A' },
    },
    { 
        id: 'suelo-pélvico',
        name: 'Suelo Pélvico',
        description: 'Es un conjunto de músculos y ligamentos en forma de hamaca que cierran la base de la pelvis. Sostiene los órganos pélvicos y gestiona las presiones intraabdominales.',
        importance: {
            movement: 'Trabaja en sinergia con el diafragma y el transverso abdominal para crear una "caja" estable que transfiere fuerza eficientemente (ej. en una sentadilla pesada).',
            health: 'Fundamental para la continencia urinaria y fecal, la función sexual y el soporte de los órganos. Su debilidad o hipertonicidad puede causar una variedad de problemas.'
        },
        volumeRecommendations: { mev: 'N/A', mav: 'N/A', mrv: 'N/A' },
    },
    { 
        id: 'diafragma',
        name: 'Diafragma',
        description: 'Es el principal músculo de la respiración, un gran domo muscular situado debajo de los pulmones. Al contraerse, desciende y permite la inhalación.',
        importance: {
            movement: 'Juega un rol crucial en la creación de presión intraabdominal (Maniobra de Valsalva), que estabiliza la columna vertebral durante levantamientos pesados.',
            health: 'Una respiración diafragmática correcta mejora la oxigenación, reduce el estrés y promueve una función óptima del core. Es el "techo" del cilindro del core.'
        },
        volumeRecommendations: { mev: 'N/A', mav: 'N/A', mrv: 'N/A' },
    },
];