// data/tendonDatabase.ts
import { TendonInfo } from '../types';

export const TENDON_DATABASE: TendonInfo[] = [
  // === HOMBRO ===
  {
    id: 'tendon-supraespinoso',
    name: 'Tendón del Supraespinoso',
    description: 'Tendón del músculo supraespinoso, parte del manguito rotador. Pasa por el espacio subacromial y es el más susceptible a impingement.',
    muscleId: 'supraespinoso',
    jointId: 'glenohumeral',
    commonInjuries: [
      { name: 'Tendinopatía del supraespinoso', description: 'Degeneración del tendón por sobreuso o impingement.', riskExercises: ['db_bench_press_tng', 'db_overhead_press', 'db_dips'], contraindications: ['Evitar elevaciones repetitivas por encima de 90°'], returnProgressions: ['Rotaciones externas', 'Face pulls', 'Progresión a press'] },
    ],
  },
  {
    id: 'tendon-infraespinoso',
    name: 'Tendón del Infraespinoso',
    description: 'Tendón del músculo infraespinoso, parte del manguito rotador. Responsable de la rotación externa del hombro.',
    muscleId: 'infraespinoso',
    jointId: 'glenohumeral',
    commonInjuries: [
      { name: 'Tendinopatía del infraespinoso', description: 'Suele asociarse a desbalances de rotadores.', riskExercises: ['db_bench_press_tng'], contraindications: [], returnProgressions: ['Rotaciones externas', 'Estiramiento de pectoral', 'Face pulls'] },
    ],
  },
  {
    id: 'tendon-bíceps-largo',
    name: 'Tendón de la Cabeza Larga del Bíceps',
    description: 'Tendón de la cabeza larga del bíceps que atraviesa la articulación del hombro. Contribuye a la estabilidad anterior del hombro.',
    muscleId: 'cabeza-larga-bíceps',
    jointId: 'glenohumeral',
    commonInjuries: [
      { name: 'Tendinopatía de la cabeza larga del bíceps', description: 'Dolor en la parte anterior del hombro.', riskExercises: ['db_bench_press_tng', 'Curl con barra'], contraindications: ['Evitar curl con barra en agarre supino en fase aguda'], returnProgressions: ['Curl martillo', 'Rotaciones externas', 'Progresión a curl'] },
    ],
  },

  // === CODO ===
  {
    id: 'tendon-bíceps',
    name: 'Tendón del Bíceps (inserción distal)',
    description: 'Tendón que inserta el bíceps braquial en la tuberosidad radial. Principal flexor del codo.',
    muscleId: 'bíceps',
    jointId: 'codo',
    commonInjuries: [
      { name: 'Tendinopatía del bíceps distal', description: 'Dolor en la parte anterior del codo.', riskExercises: ['Curl con barra', 'Chin-ups'], contraindications: ['Evitar cargas máximas en flexión de codo'], returnProgressions: ['Curl excéntrico', 'Curl martillo', 'Progresión a curl con barra'] },
    ],
  },
  {
    id: 'tendon-tríceps',
    name: 'Tendón del Tríceps',
    description: 'Tendón que inserta el tríceps braquial en el olécranon del cúbito. Principal extensor del codo.',
    muscleId: 'tríceps',
    jointId: 'codo',
    commonInjuries: [
      { name: 'Tendinopatía del tríceps', description: 'Dolor en la parte posterior del codo.', riskExercises: ['Extensión de tríceps', 'Fondos'], contraindications: ['Evitar extensiones con carga pesada'], returnProgressions: ['Extensión excéntrica', 'Progresión gradual'] },
    ],
  },

  // === RODILLA ===
  {
    id: 'tendon-rotuliano',
    name: 'Tendón Rotuliano',
    description: 'Conecta el cuádriceps con la tibia a través de la rótula. Transmite la fuerza de extensión de rodilla.',
    muscleId: 'cuádriceps',
    jointId: 'rodilla',
    commonInjuries: [
      { name: 'Tendinopatía rotuliana (rodilla del saltador)', description: 'Dolor en el tendón por debajo de la rótula.', riskExercises: ['Sentadilla', 'Salto', 'Estocadas'], contraindications: ['Evitar saltos y sentadillas profundas en fase aguda'], returnProgressions: ['Sentadilla isométrica', 'Excéntricos de cuádriceps', 'Progresión a sentadilla'] },
    ],
  },
  {
    id: 'tendon-cuádriceps',
    name: 'Tendón del Cuádriceps',
    description: 'Tendón común del cuádriceps que inserta en el polo superior de la rótula.',
    muscleId: 'cuádriceps',
    jointId: 'rodilla',
    commonInjuries: [
      { name: 'Tendinopatía del cuádriceps', description: 'Dolor por encima de la rótula.', riskExercises: ['Sentadilla', 'Extensión de cuádriceps'], contraindications: [], returnProgressions: ['Extensión isométrica', 'Excéntricos', 'Progresión a sentadilla'] },
    ],
  },

  // === TOBILLO ===
  {
    id: 'tendon-aquiles',
    name: 'Tendón de Aquiles',
    description: 'El tendón más grueso y fuerte del cuerpo. Conecta el gastrocnemio y el sóleo con el calcáneo.',
    muscleId: 'gastrocnemio',
    jointId: 'tobillo',
    commonInjuries: [
      { name: 'Tendinopatía aquílea', description: 'Degeneración del tendón de Aquiles, común en corredores y deportes de salto.', riskExercises: ['Elevación de talones', 'Salto', 'Sentadilla'], contraindications: ['Evitar saltos y carreras en fase aguda'], returnProgressions: ['Excéntricos de sóleo', 'Elevaciones bilaterales', 'Progresión a pliometría'] },
    ],
  },

  // === MANO/MUÑECA ===
  {
    id: 'tendon-flexores-muñeca',
    name: 'Tendones Flexores de la Muñeca',
    description: 'Tendones de los músculos flexores del antebrazo que cruzan la muñeca.',
    muscleId: 'flexores-de-antebrazo',
    jointId: 'muñeca',
    commonInjuries: [
      { name: 'Tendinitis de flexores', description: 'Inflamación por sobreuso o agarre repetitivo.', riskExercises: ['Peso muerto', 'Remo'], contraindications: ['Evitar agarres extremos'], returnProgressions: ['Movilidad de muñeca', 'Fortalecimiento de antebrazo'] },
    ],
  },
  {
    id: 'tendon-extensores-muñeca',
    name: 'Tendones Extensores de la Muñeca',
    description: 'Tendones de los músculos extensores del antebrazo. El extensor radial corto es el afectado en codo de tenista.',
    muscleId: 'extensores-de-antebrazo',
    jointId: 'codo',
    commonInjuries: [
      { name: 'Epicondilitis lateral (codo de tenista)', description: 'Afecta principalmente al extensor radial corto del carpo.', riskExercises: ['Curl con barra', 'Extensión de tríceps'], contraindications: ['Evitar agarre prono repetitivo'], returnProgressions: ['Ejercicios excéntricos', 'Curl martillo', 'Progresión gradual'] },
    ],
  },

  // === CADERA ===
  {
    id: 'tendon-iliopsoas',
    name: 'Tendón del Iliopsoas',
    description: 'Tendón del psoas-ilíaco, principal flexor de cadera.',
    muscleId: 'recto-femoral',
    jointId: 'cadera',
    commonInjuries: [
      { name: 'Tendinopatía del iliopsoas', description: 'Dolor en la parte anterior de la cadera.', riskExercises: ['Sentadilla', 'Estocadas'], contraindications: [], returnProgressions: ['Estiramiento de flexores', 'Fortalecimiento de glúteos', 'Progresión a sentadilla'] },
    ],
  },

  // === ISQUIOSURALES ===
  {
    id: 'tendon-isquiotibiales',
    name: 'Tendones de los Isquiosurales',
    description: 'Tendones que insertan los isquiosurales en la tuberosidad isquiática (origen) y en la tibia/peroné (inserción).',
    muscleId: 'bíceps-femoral',
    jointId: 'rodilla',
    commonInjuries: [
      { name: 'Tirón de isquiosurales', description: 'Lesión muscular o tendinosa en la parte posterior del muslo.', riskExercises: ['Peso muerto rumano', 'Curl nórdico'], contraindications: ['Evitar estiramiento máximo bajo carga'], returnProgressions: ['Curl nórdico excéntrico', 'Puente de glúteo', 'Progresión a RDL'] },
    ],
  },
];
