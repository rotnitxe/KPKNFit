export type WikiChainId =
  | 'anterior'
  | 'posterior'
  | 'full'
  | 'upper'
  | 'lower'
  | 'core'
  | 'other';

export interface WikiChainDefinition {
  id: WikiChainId;
  title: string;
  subtitle: string;
  description: string;
  importance: string;
  focusAreas: string[];
}

export const WIKI_CHAIN_DEFINITIONS: WikiChainDefinition[] = [
  {
    id: 'anterior',
    title: 'Cadena Anterior',
    subtitle: 'Empuje frontal y estabilidad del tronco',
    description:
      'La cadena anterior reúne los tejidos que dominan el empuje, la flexión de cadera y el control del torso al frente.',
    importance:
      'Es clave para press, sentadillas, zancadas y para sostener el torso sin colapsar hacia delante.',
    focusAreas: ['Pectoral', 'Cuádriceps', 'Abdominales', 'Deltoides anterior'],
  },
  {
    id: 'posterior',
    title: 'Cadena Posterior',
    subtitle: 'Potencia desde la cadera',
    description:
      'La cadena posterior agrupa los eslabones que disparan la bisagra de cadera, la tracción y la estabilización dorsal.',
    importance:
      'Sostiene el peso muerto, la carrera, los saltos y la protección de la espalda baja.',
    focusAreas: ['Glúteos', 'Isquiosurales', 'Erectores', 'Dorsales'],
  },
  {
    id: 'full',
    title: 'Cuerpo Completo',
    subtitle: 'Sinergia global de empuje y tracción',
    description:
      'Los ejercicios de cuerpo completo exigen coordinación entre tren superior e inferior, con alta demanda de estabilidad.',
    importance:
      'Sirve para movimientos atléticos, gestos de carga y levantamientos donde el tronco transmite fuerza entre segmentos.',
    focusAreas: ['Core', 'Glúteos', 'Pecho', 'Espalda'],
  },
  {
    id: 'upper',
    title: 'Tren Superior',
    subtitle: 'Pecho, espalda, hombros y brazos',
    description:
      'Agrupa los movimientos dominados por la cintura escapular, la tracción y el empuje del tren superior.',
    importance:
      'Es el bloque útil para postura, fuerza funcional y equilibrio entre empujes y tracciones.',
    focusAreas: ['Pecho', 'Espalda', 'Hombros', 'Brazos'],
  },
  {
    id: 'lower',
    title: 'Tren Inferior',
    subtitle: 'Piernas, cadera y tobillo',
    description:
      'Reúne los patrones que dependen de la extensión de cadera, rodilla y tobillo para producir fuerza o absorber carga.',
    importance:
      'Es la base de la potencia atlética, la estabilidad y el gasto metabólico del entrenamiento.',
    focusAreas: ['Cuádriceps', 'Glúteos', 'Isquiosurales', 'Pantorrilla'],
  },
  {
    id: 'core',
    title: 'Core',
    subtitle: 'Estabilidad y transferencia de fuerzas',
    description:
      'El core protege la columna y mantiene la transmisión eficiente entre tren inferior y superior.',
    importance:
      'Sin un core útil, la fuerza se fuga y el cuerpo compensa con patrones menos eficientes o más agresivos.',
    focusAreas: ['Recto abdominal', 'Oblicuos', 'Lumbar', 'Respiración'],
  },
  {
    id: 'other',
    title: 'Otro',
    subtitle: 'Categoría mixta',
    description:
      'Agrupación auxiliar para ejercicios o patrones que no encajan con claridad en las cadenas principales.',
    importance:
      'Sirve como zona de transición o revisión cuando el patrón aún no está clasificado con precisión.',
    focusAreas: ['Mixto', 'Transición', 'Técnica'],
  },
];

export function getWikiChainDefinition(chainId?: string | null): WikiChainDefinition {
  const normalized = (chainId ?? 'posterior').toLowerCase();
  return WIKI_CHAIN_DEFINITIONS.find(item => item.id === normalized) ?? WIKI_CHAIN_DEFINITIONS[1];
}

