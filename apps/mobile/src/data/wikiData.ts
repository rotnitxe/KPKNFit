import type { WikiMuscle, WikiJoint, WikiTendon, WikiMovementPattern } from '../types/wiki';

export const WIKI_MUSCLES: WikiMuscle[] = [
  { id: 'pectoralis-major', name: 'Pectoral Mayor', description: 'Músculo grande que cubre la parte superior del pecho, responsable de la aducción y rotación interna del húmero.', relatedJoints: ['glenohumeral', 'acromioclavicular'], movementPatterns: ['press-horizontal'] },
  { id: 'latissimus-dorsi', name: 'Dorsal Ancho', description: 'El músculo más grande de la espalda superior, clave en movimientos de tracción y extensión del hombro.', relatedJoints: ['glenohumeral', 'scapulothoracic'], movementPatterns: ['pull-horizontal', 'pull-vertical'] },
  { id: 'deltoid', name: 'Deltoides', description: 'Cubre la articulación del hombro. Se divide en anterior, medio y posterior.', relatedJoints: ['glenohumeral', 'acromioclavicular'], relatedTendons: ['supraspinatus-tendon'], movementPatterns: ['press-vertical', 'press-horizontal'] },
  { id: 'triceps-brachii', name: 'Tríceps Braquial', description: 'Músculo de tres cabezas en la parte posterior del brazo, principal extensor del codo.', relatedJoints: ['elbow', 'glenohumeral'], relatedTendons: ['triceps-tendon'], movementPatterns: ['press-vertical', 'press-horizontal'] },
  { id: 'biceps-brachii', name: 'Bíceps Braquial', description: 'Músculo de dos cabezas en la parte frontal del brazo, responsable de la supinación y flexión del codo.', relatedJoints: ['elbow', 'glenohumeral', 'radio-ulnar'], relatedTendons: ['biceps-long-head', 'distal-biceps-tendon'], movementPatterns: ['pull-horizontal', 'pull-vertical', 'isolation-flexion'] },
  { id: 'rectus-abdominis', name: 'Recto Abdominal', description: 'Músculo central del core que permite la flexión de la columna (crunch).', relatedJoints: ['lumbar-spine'], movementPatterns: ['core-rotation'] },
  { id: 'quadriceps', name: 'Cuádriceps', description: 'Grupo de cuatro músculos en la parte frontal del muslo, responsables de la extensión de la rodilla.', relatedJoints: ['knee', 'hip'], relatedTendons: ['patellar-tendon', 'quadriceps-tendon'], movementPatterns: ['squat', 'lunge'] },
  { id: 'hamstrings', name: 'Isquiosurales', description: 'Grupo posterior del muslo encargado de la flexión de rodilla y extensión de cadera.', relatedJoints: ['knee', 'hip'], movementPatterns: ['hinge', 'lunge'] },
  { id: 'gluteus-maximus', name: 'Glúteo Mayor', description: 'El músculo más potente del cuerpo, principal extensor de la cadera.', relatedJoints: ['hip', 'sacroiliac'], movementPatterns: ['squat', 'hinge', 'lunge'] },
  { id: 'gastrocnemius', name: 'Gastrocnemio (Gemelos)', description: 'Músculo superficial de la pantorrilla, realiza la flexión plantar.', relatedJoints: ['ankle', 'knee'], relatedTendons: ['achilles-tendon'] },
  { id: 'trapezius', name: 'Trapecio', description: 'Músculo grande que mueve, rota y estabiliza la escápula.', relatedJoints: ['scapulothoracic', 'acromioclavicular', 'cervical-spine'], movementPatterns: ['pull-horizontal', 'carry'] },
  { id: 'erector-spinae', name: 'Erector de la Columna', description: 'Grupo muscular que permite la extensión y estabilidad de la espalda.', relatedJoints: ['lumbar-spine', 'cervical-spine'], movementPatterns: ['hinge', 'carry'] },
  { id: 'obliques', name: 'Oblicuos', description: 'Músculos laterales del abdomen responsables de la rotación y flexión lateral.', relatedJoints: ['lumbar-spine'], movementPatterns: ['core-rotation'] },
  { id: 'brachialis', name: 'Braquial Anterior', description: 'Músculo situado debajo del bíceps, potente flexor del codo.', relatedJoints: ['elbow'], movementPatterns: ['isolation-flexion'] },
  { id: 'tibialis-anterior', name: 'Tibial Anterior', description: 'Músculo en la parte frontal de la espinilla, realiza la dorsiflexión del pie.', relatedJoints: ['ankle'] },
  { id: 'soleus', name: 'Sólio', description: 'Situado debajo del gemelo, crucial para la estabilidad y flexión plantar con rodilla flexionada.', relatedJoints: ['ankle'], relatedTendons: ['achilles-tendon'] },
  { id: 'supraspinatus', name: 'Supraspinoso', description: 'Parte del manguito rotador, inicia la abducción del brazo.', relatedJoints: ['glenohumeral'], relatedTendons: ['supraspinatus-tendon'] },
  { id: 'adductors', name: 'Aductores', description: 'Grupo muscular en la parte interna del muslo que cierra las piernas.', relatedJoints: ['hip'] },
  { id: 'forearm-extensors', name: 'Extensores del Antebrazo', description: 'Músculos que extienden la muñeca y los dedos.', relatedJoints: ['wrist', 'elbow'], relatedTendons: ['common-extensor-tendon'] },
  { id: 'forearm-flexors', name: 'Flexores del Antebrazo', description: 'Músculos que flexionan la muñeca y los dedos.', relatedJoints: ['wrist', 'elbow'], relatedTendons: ['common-flexor-tendon'] },
  { id: 'tensor-fasciae-latae', name: 'Tensor de la Fascia Lata', description: 'Músculo pequeño en el lateral de la cadera.', relatedJoints: ['hip', 'knee'], relatedTendons: ['iliotibial-band'] }
];

export const WIKI_JOINTS: WikiJoint[] = [
  { id: 'glenohumeral', name: 'Hombro (Glenohumeral)', description: 'Articulación de gran movilidad pero inestable.', type: 'ball-socket', bodyPart: 'upper', musclesCrossing: ['deltoid', 'supraspinatus', 'pectoralis-major', 'latissimus-dorsi'], tendonsRelated: ['biceps-long-head', 'supraspinatus-tendon'], movementPatterns: ['press-vertical', 'press-horizontal', 'pull-vertical', 'pull-horizontal'], commonInjuries: [] },
  { id: 'elbow', name: 'Codo', description: 'Articulación tipo bisagra que une el húmero con el radio y cúbito.', type: 'hinge', bodyPart: 'upper', musclesCrossing: ['biceps-brachii', 'triceps-brachii', 'brachialis', 'forearm-extensors', 'forearm-flexors'], tendonsRelated: ['triceps-tendon', 'distal-biceps-tendon', 'common-extensor-tendon', 'common-flexor-tendon'], movementPatterns: ['isolation-flexion'], commonInjuries: [] },
  { id: 'wrist', name: 'Muñeca', description: 'Complejo articular que une el antebrazo con la mano.', type: 'condyloid', bodyPart: 'upper', musclesCrossing: ['forearm-extensors', 'forearm-flexors'], tendonsRelated: ['common-extensor-tendon', 'common-flexor-tendon'], movementPatterns: [], commonInjuries: [] },
  { id: 'hip', name: 'Cadera', description: 'Articulación de carga que une la pelvis con el fémur.', type: 'ball-socket', bodyPart: 'lower', musclesCrossing: ['gluteus-maximus', 'adductors', 'quadriceps', 'hamstrings', 'tensor-fasciae-latae'], tendonsRelated: [], movementPatterns: ['squat', 'hinge', 'lunge'], commonInjuries: [] },
  { id: 'knee', name: 'Rodilla', description: 'Articulación más grande del cuerpo, soporta gran parte del peso.', type: 'hinge', bodyPart: 'lower', musclesCrossing: ['quadriceps', 'hamstrings', 'gastrocnemius', 'tensor-fasciae-latae'], tendonsRelated: ['patellar-tendon', 'quadriceps-tendon', 'iliotibial-band'], movementPatterns: ['squat', 'lunge'], commonInjuries: [] },
  { id: 'ankle', name: 'Tobillo', description: 'Une la pierna con el pie.', type: 'hinge', bodyPart: 'lower', musclesCrossing: ['gastrocnemius', 'soleus', 'tibialis-anterior'], tendonsRelated: ['achilles-tendon'], movementPatterns: ['squat', 'lunge'], commonInjuries: [] },
  { id: 'cervical-spine', name: 'Columna Cervical', description: 'Sostiene el cráneo y permite su movilidad.', type: 'pivot', bodyPart: 'spine', musclesCrossing: ['trapezius', 'erector-spinae'], tendonsRelated: [], movementPatterns: [], commonInjuries: [] },
  { id: 'lumbar-spine', name: 'Columna Lumbar', description: 'Zona baja de la espalda, soporta gran carga axial.', type: 'gliding', bodyPart: 'spine', musclesCrossing: ['erector-spinae', 'rectus-abdominis', 'obliques'], tendonsRelated: [], movementPatterns: ['hinge', 'core-rotation', 'carry'], commonInjuries: [] },
  { id: 'scapulothoracic', name: 'Escapulotorácica', description: 'Articulación funcional entre la escápula y el tórax.', type: 'gliding', bodyPart: 'upper', musclesCrossing: ['trapezius', 'latissimus-dorsi'], tendonsRelated: [], movementPatterns: ['pull-horizontal', 'pull-vertical'], commonInjuries: [] },
  { id: 'sacroiliac', name: 'Sacroilíaca', description: 'Conecta el sacro con los huesos de la pelvis.', type: 'gliding', bodyPart: 'spine', musclesCrossing: ['gluteus-maximus'], tendonsRelated: [], movementPatterns: ['hinge'], commonInjuries: [] },
  { id: 'radio-ulnar', name: 'Radiocubital', description: 'Permite la pronación y supinación del antebrazo.', type: 'pivot', bodyPart: 'upper', musclesCrossing: ['biceps-brachii'], tendonsRelated: ['distal-biceps-tendon'], movementPatterns: ['isolation-flexion'], commonInjuries: [] },
  { id: 'acromioclavicular', name: 'Acromioclavicular', description: 'Une la clavícula con el acromion de la escápula.', type: 'gliding', bodyPart: 'upper', musclesCrossing: ['trapezius', 'deltoid', 'pectoralis-major'], tendonsRelated: [], movementPatterns: ['press-vertical', 'press-horizontal'], commonInjuries: [] }
];

export const WIKI_TENDONS: WikiTendon[] = [
  { id: 'achilles-tendon', name: 'Tendón de Aquiles', description: 'El más grueso y fuerte del cuerpo humano.', muscleId: 'gastrocnemius', jointId: 'ankle', commonInjuries: [] },
  { id: 'patellar-tendon', name: 'Tendón Rotuliano', description: 'Conecta la rótula con la tibia, esencial para la extensión.', muscleId: 'quadriceps', jointId: 'knee', commonInjuries: [] },
  { id: 'biceps-long-head', name: 'Cabeza Larga del Bíceps', description: 'Atraviesa la articulación del hombro.', muscleId: 'biceps-brachii', jointId: 'glenohumeral', commonInjuries: [] },
  { id: 'triceps-tendon', name: 'Tendón del Tríceps', description: 'Inserta el tríceps en el olécranon (codo).', muscleId: 'triceps-brachii', jointId: 'elbow', commonInjuries: [] },
  { id: 'supraspinatus-tendon', name: 'Tendón Supraspinoso', description: 'Sitio común de pinzamiento en el hombro.', muscleId: 'supraspinatus', jointId: 'glenohumeral', commonInjuries: [] },
  { id: 'common-extensor-tendon', name: 'Tendón Extensor Común', description: 'Origen de los extensores de muñeca (Epicóndilo lateral).', muscleId: 'forearm-extensors', jointId: 'elbow', commonInjuries: [] },
  { id: 'common-flexor-tendon', name: 'Tendón Flexor Común', description: 'Origen de los flexores de muñeca (Epicóndilo medial).', muscleId: 'forearm-flexors', jointId: 'elbow', commonInjuries: [] },
  { id: 'quadriceps-tendon', name: 'Tendón Cuadricipital', description: 'Une el cuádriceps a la parte superior de la rótula.', muscleId: 'quadriceps', jointId: 'knee', commonInjuries: [] },
  { id: 'iliotibial-band', name: 'Banda Iliotibial', description: 'Tejido conectivo largo que va por el lateral del muslo.', muscleId: 'tensor-fasciae-latae', jointId: 'knee', commonInjuries: [] },
  { id: 'distal-biceps-tendon', name: 'Tendón del Bíceps Distal', description: 'Inserta el bíceps en el radio.', muscleId: 'biceps-brachii', jointId: 'elbow', commonInjuries: [] }
];

export const WIKI_MOVEMENT_PATTERNS: WikiMovementPattern[] = [
  { id: 'squat', name: 'Sentadilla / Dominancia de Rodilla', description: 'Patrón de empuje vertical del tren inferior.', forceTypes: ['vertical'], chainTypes: ['closed'], primaryMuscles: ['quadriceps', 'gluteus-maximus'], primaryJoints: ['knee', 'hip'], exampleExercises: ['Sentadilla con barra', 'Prensa de piernas'] },
  { id: 'hinge', name: 'Bisagra de Cadera', description: 'Dominancia de cadera con mínimo movimiento de rodilla.', forceTypes: ['vertical', 'posterior'], chainTypes: ['closed'], primaryMuscles: ['gluteus-maximus', 'hamstrings', 'erector-spinae'], primaryJoints: ['hip'], exampleExercises: ['Peso Muerto', 'Hip Thrust'] },
  { id: 'press-horizontal', name: 'Empuje Horizontal', description: 'Empujar una carga alejándola del pecho.', forceTypes: ['horizontal'], chainTypes: ['open'], primaryMuscles: ['pectoralis-major', 'deltoid', 'triceps-brachii'], primaryJoints: ['glenohumeral', 'elbow'], exampleExercises: ['Press de Banca', 'Flexiones'] },
  { id: 'press-vertical', name: 'Empuje Vertical', description: 'Empujar una carga por encima de la cabeza.', forceTypes: ['vertical'], chainTypes: ['open'], primaryMuscles: ['deltoid', 'triceps-brachii'], primaryJoints: ['glenohumeral', 'elbow'], exampleExercises: ['Press Militar'] },
  { id: 'pull-horizontal', name: 'Tracción Horizontal', description: 'Acercar una carga hacia el torso.', forceTypes: ['horizontal'], chainTypes: ['open'], primaryMuscles: ['latissimus-dorsi', 'trapezius', 'biceps-brachii'], primaryJoints: ['glenohumeral', 'elbow'], exampleExercises: ['Remo con barra', 'Remo con mancuerna'] },
  { id: 'pull-vertical', name: 'Tracción Vertical', description: 'Acercar una carga desde arriba del cuerpo.', forceTypes: ['vertical'], chainTypes: ['open'], primaryMuscles: ['latissimus-dorsi', 'biceps-brachii'], primaryJoints: ['glenohumeral', 'elbow'], exampleExercises: ['Dominadas', 'Jalón al pecho'] },
  { id: 'lunge', name: 'Zancada / Unilateral', description: 'Patrón de empuje con piernas en posición asimétrica.', forceTypes: ['vertical'], chainTypes: ['closed'], primaryMuscles: ['quadriceps', 'gluteus-maximus'], primaryJoints: ['knee', 'hip'], exampleExercises: ['Estocadas', 'Sentadilla Búlgara'] },
  { id: 'core-rotation', name: 'Rotación / Anti-rotación', description: 'Movimientos que involucran girar el torso o resistir el giro.', forceTypes: ['rotational'], chainTypes: ['mixed'], primaryMuscles: ['obliques', 'rectus-abdominis'], primaryJoints: ['lumbar-spine'], exampleExercises: ['Russian Twist', 'Pallof Press'] },
  { id: 'carry', name: 'Carga / Locomoción', description: 'Desplazar una carga de un punto A a un punto B.', forceTypes: ['axial'], chainTypes: ['closed'], primaryMuscles: ['trapezius', 'erector-spinae', 'core'], primaryJoints: ['spine', 'hip', 'knee', 'ankle'], exampleExercises: ['Farmer Walk'] },
  { id: 'isolation-flexion', name: 'Aislamiento Flexión', description: 'Movimientos de una sola articulación para flexores.', forceTypes: ['single-joint'], chainTypes: ['open'], primaryMuscles: ['biceps-brachii'], primaryJoints: ['elbow'], exampleExercises: ['Curl de bíceps'] }
];

export function normalizeWikiText(value: string): string {
  return value
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
}

export function searchWiki<T extends { name: string; description: string }>(items: T[], query: string): T[] {
  if (!query) return items;
  const normalizedQuery = normalizeWikiText(query);
  return items.filter((item) => {
    const name = normalizeWikiText(item.name);
    const desc = normalizeWikiText(item.description);
    return name.includes(normalizedQuery) || desc.includes(normalizedQuery);
  });
}
