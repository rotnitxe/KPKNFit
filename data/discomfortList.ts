export interface DiscomfortItem {
  id: string;
  name: string;
  description: string;
  musclesToDrain: string[];
}

export const DISCOMFORT_DATABASE: DiscomfortItem[] = [
  // Hombro
  { id: 'hombro-general', name: 'Dolor de hombro (general)', description: 'Dolor o molestia en la zona del hombro al mover el brazo o cargar peso', musclesToDrain: ['Deltoides', 'Trapecio', 'Pectorales', 'Dorsales', 'Bíceps', 'Tríceps'] },
  { id: 'pinzamiento-hombro', name: 'Pinzamiento de hombro (impingement)', description: 'Dolor al levantar el brazo, sensación de que algo se comprime dentro del hombro', musclesToDrain: ['Deltoides', 'Trapecio', 'Supraespinoso'] },
  { id: 'manguito-rotador', name: 'Dolor en manguito rotador', description: 'Dolor profundo en el hombro al hacer press o rotaciones', musclesToDrain: ['Deltoides', 'Supraespinoso', 'Infraespinoso'] },
  { id: 'inestabilidad-hombro', name: 'Inestabilidad del hombro', description: 'Sensación de que el hombro se sale o se mueve de más', musclesToDrain: ['Deltoides', 'Pectorales', 'Dorsales'] },
  { id: 'dolor-ac', name: 'Dolor en la articulación AC', description: 'Dolor en la parte superior del hombro donde se une la clavícula', musclesToDrain: ['Trapecio', 'Deltoides'] },
  { id: 'bursitis-hombro', name: 'Bursitis de hombro', description: 'Dolor e hinchazón en el hombro, empeora al dormir de ese lado', musclesToDrain: ['Deltoides', 'Trapecio'] },

  // Codo
  { id: 'codo-general', name: 'Dolor de codo (general)', description: 'Molestia en el codo al flexionar, extender o cargar', musclesToDrain: ['Bíceps', 'Tríceps', 'Antebrazo'] },
  { id: 'codo-tenista', name: 'Codo de tenista (epicondilitis lateral)', description: 'Dolor en la parte externa del codo al cerrar el puño o extender la muñeca', musclesToDrain: ['Antebrazo', 'Tríceps'] },
  { id: 'codo-golfista', name: 'Codo de golfista (epicondilitis medial)', description: 'Dolor en la parte interna del codo al agarrar o flexionar', musclesToDrain: ['Bíceps', 'Antebrazo'] },
  { id: 'tendon-biceps', name: 'Dolor en el tendón del bíceps', description: 'Dolor en la parte delantera del codo o hombro al doblar el brazo', musclesToDrain: ['Bíceps'] },
  { id: 'tendon-triceps', name: 'Dolor en el tendón del tríceps', description: 'Dolor en la parte trasera del codo al estirar el brazo', musclesToDrain: ['Tríceps'] },

  // Muñeca y Mano
  { id: 'muñeca', name: 'Dolor de muñeca', description: 'Molestia en la muñeca al agarrar, hacer flexiones o press', musclesToDrain: ['Antebrazo'] },
  { id: 'tunel-carpiano', name: 'Síndrome del túnel carpiano', description: 'Hormigueo o adormecimiento en dedos y mano, sobre todo por la noche', musclesToDrain: ['Antebrazo'] },
  { id: 'dolor-dedos', name: 'Dolor en los dedos', description: 'Molestia al cerrar el puño o agarrar la barra', musclesToDrain: ['Antebrazo'] },

  // Espalda y Columna
  { id: 'espalda-baja', name: 'Dolor de espalda baja (lumbalgia)', description: 'Dolor en la zona lumbar al hacer peso muerto, sentadillas o inclinarte', musclesToDrain: ['Espalda Baja', 'Glúteos', 'Isquiosurales'] },
  { id: 'espalda-media', name: 'Dolor de espalda media (dorsalgia)', description: 'Molestia entre los omóplatos al hacer remos o press', musclesToDrain: ['Dorsales', 'Trapecio', 'Romboides'] },
  { id: 'espalda-alta-cuello', name: 'Dolor de espalda alta / cuello (cervicalgia)', description: 'Rigidez o dolor en el cuello y parte alta de la espalda', musclesToDrain: ['Trapecio', 'Cuello'] },
  { id: 'ciatica', name: 'Ciática', description: 'Dolor que baja por la pierna desde la cadera o glúteo', musclesToDrain: ['Glúteos', 'Isquiosurales', 'Espalda Baja'] },
  { id: 'tension-cuello', name: 'Tensión en el cuello', description: 'Cuello rígido o contracturado, duele al girar la cabeza', musclesToDrain: ['Trapecio', 'Cuello'] },
  { id: 'erectores-espinales', name: 'Molestia en los erectores espinales', description: 'Dolor en los músculos que recorren la columna al cargar', musclesToDrain: ['Espalda Baja', 'Erectores'] },

  // Cadera y Glúteos
  { id: 'cadera-general', name: 'Dolor de cadera (general)', description: 'Molestia en la cadera al sentadilla, correr o abrir las piernas', musclesToDrain: ['Glúteos', 'Cuádriceps', 'Aductores'] },
  { id: 'pinzamiento-cadera', name: 'Pinzamiento de cadera (FAI)', description: 'Dolor en la ingle al flexionar la cadera o hacer sentadillas profundas', musclesToDrain: ['Glúteos', 'Flexores de cadera'] },
  { id: 'piriforme', name: 'Dolor en el piriforme / glúteo profundo', description: 'Dolor profundo en el glúteo que a veces baja por la pierna', musclesToDrain: ['Glúteos', 'Piriforme'] },
  { id: 'bursitis-cadera', name: 'Bursitis de cadera', description: 'Dolor en el lateral de la cadera al subir escaleras o dormir de ese lado', musclesToDrain: ['Glúteos', 'TFL'] },
  { id: 'flexor-cadera', name: 'Dolor en el flexor de la cadera', description: 'Molestia en la parte delantera de la cadera al levantar la rodilla', musclesToDrain: ['Flexores de cadera', 'Cuádriceps'] },

  // Rodilla
  { id: 'rodilla-general', name: 'Dolor de rodilla (general)', description: 'Molestia en la rodilla al flexionar, saltar o cargar', musclesToDrain: ['Cuádriceps', 'Isquiosurales', 'Pantorrillas'] },
  { id: 'rodilla-saltador', name: 'Rodilla de saltador (tendón rotuliano)', description: 'Dolor debajo de la rótula al saltar o hacer sentadillas', musclesToDrain: ['Cuádriceps'] },
  { id: 'banda-it', name: 'Síndrome de la banda iliotibial (IT)', description: 'Dolor en el lateral de la rodilla al correr o hacer sentadillas', musclesToDrain: ['Glúteos', 'TFL'] },
  { id: 'femororrotuliano', name: 'Dolor femororrotuliano', description: 'Dolor alrededor o detrás de la rótula al flexionar la rodilla', musclesToDrain: ['Cuádriceps'] },
  { id: 'meniscos', name: 'Dolor en los meniscos', description: 'Dolor interno o externo de la rodilla al girar o agacharte', musclesToDrain: ['Cuádriceps', 'Isquiosurales'] },
  { id: 'rodilla-posterior', name: 'Dolor detrás de la rodilla', description: 'Molestia en el hueco poplíteo al estirar la pierna', musclesToDrain: ['Isquiosurales', 'Pantorrillas'] },

  // Tobillo y Pie
  { id: 'tobillo', name: 'Dolor de tobillo', description: 'Molestia al caminar, saltar o hacer sentadillas', musclesToDrain: ['Pantorrillas', 'Tibial'] },
  { id: 'aquiles', name: 'Tendinitis de Aquiles', description: 'Dolor en el tendón de la parte trasera del tobillo', musclesToDrain: ['Pantorrillas'] },
  { id: 'fascitis-plantar', name: 'Fascitis plantar', description: 'Dolor en la planta del pie, sobre todo al levantarte', musclesToDrain: ['Pantorrillas', 'Pie'] },
  { id: 'empeine', name: 'Dolor en el empeine', description: 'Molestia en el dorso del pie', musclesToDrain: ['Tibial', 'Pie'] },

  // Músculos específicos
  { id: 'tiron-pectoral', name: 'Tirón en el pectoral', description: 'Dolor agudo en el pecho al hacer press o aperturas', musclesToDrain: ['Pectorales'] },
  { id: 'tiron-isquiotibial', name: 'Tirón en el isquiotibial', description: 'Dolor en la parte trasera del muslo al estirar o correr', musclesToDrain: ['Isquiosurales'] },
  { id: 'tiron-cuadriceps', name: 'Tirón en el cuádriceps', description: 'Dolor en la parte delantera del muslo', musclesToDrain: ['Cuádriceps'] },
  { id: 'tiron-gemelo', name: 'Tirón en el gemelo', description: 'Dolor en la pantorrilla al empujar o saltar', musclesToDrain: ['Pantorrillas'] },
  { id: 'contractura-trapecio', name: 'Contractura en el trapecio', description: 'Nudos o tensión entre el cuello y el hombro', musclesToDrain: ['Trapecio'] },
  { id: 'calambres', name: 'Calambres musculares', description: 'Espasmos o contracciones involuntarias durante o después del entrenamiento', musclesToDrain: [] },

  // Sensaciones
  { id: 'hormigueo', name: 'Hormigueo / Adormecimiento', description: 'Sensación de pinchazos o pérdida de sensibilidad en una zona', musclesToDrain: [] },
  { id: 'clic-doloroso', name: 'Sensación de "clic" o chasquido doloroso', description: 'Ruido o crujido en una articulación que duele', musclesToDrain: [] },
  { id: 'rigidez', name: 'Rigidez articular excesiva', description: 'Dificultad para mover una articulación con normalidad', musclesToDrain: [] },
  { id: 'debilidad-lateral', name: 'Debilidad inusual en un lado', description: 'Un lado del cuerpo se siente más débil que el otro', musclesToDrain: [] },
];

/** Lista de nombres para compatibilidad con componentes que esperan string[] */
export const DISCOMFORT_LIST: string[] = DISCOMFORT_DATABASE.map(d => d.name);
