// data/exerciseDatabase.ts
import { ExerciseMuscleInfo } from '../types';
import { EXERCISE_EXPANSION_LIST } from './exerciseDatabaseExpansion';
import { EXERCISE_EXPANSION_LIST_2 } from './exerciseDatabaseExpansion2';
import { EXERCISE_EXPANSION_LIST_3 } from './exerciseDatabaseExpansion3';

const BASE_EXERCISE_LIST: ExerciseMuscleInfo[] = [
  // =================================================================
  // TREN SUPERIOR
  // =================================================================
  
  // --- PECHO ---
  {
    id: 'db_bench_press_tng', name: 'Press de Banca (Táctil / Touch and Go)', alias: 'Bench Press', description: 'Ejercicio fundamental para el desarrollo del pectoral, hombros y tríceps, realizado de forma continua sin pausa en el pecho.',
    involvedMuscles: [ 
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Pectoral', role: 'secondary', activation: 0.6 }, 
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.6 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.6 },
      { muscle: 'Trapecio', role: 'stabilizer', activation: 0.3 }, { muscle: 'Espalda Baja', role: 'stabilizer', activation: 0.2 }
    ],
    subMuscleGroup: 'Pectoral', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 6, transferability: 8, injuryRisk: { level: 7, details: 'Hombros (impingement), codos.' }, isHallOfFame: true,
    efc: 3.8, cnc: 3.8, ssc: 0.3, resistanceProfile: { curve: 'campana', peakTensionPoint: 'medio', description: 'Mayor tensión en el punto medio.' }
  },
  {
    id: 'db_bench_press_paused', name: 'Press de Banca (Pausado)', description: 'Variante con pausa en el pecho. Elimina el reflejo de estiramiento.',
    involvedMuscles: [ 
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.6 }, 
      { muscle: 'Tríceps', role: 'secondary', activation: 0.6 }, { muscle: 'Trapecio', role: 'stabilizer', activation: 0.4 } 
    ],
    subMuscleGroup: 'Pectoral', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior', variantOf: 'db_bench_press_tng',
    setupTime: 4, technicalDifficulty: 7, transferability: 8, efc: 4.0, cnc: 4.2, ssc: 0.3
  },
  {
    id: 'db_incline_bench_press', name: 'Press de Banca Inclinado', description: 'Enfatiza la porción clavicular (superior) del pectoral.',
    involvedMuscles: [ 
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.7 }, 
      { muscle: 'Tríceps', role: 'secondary', activation: 0.5 }, { muscle: 'Trapecio', role: 'stabilizer', activation: 0.3 } 
    ],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 5, technicalDifficulty: 7, efc: 3.8, cnc: 3.8, ssc: 0.2
  },
  {
    id: 'db_dumbbell_bench_press', name: 'Press de Banca con Mancuernas', description: 'Mayor rango de movimiento y estabilización que la barra.',
    involvedMuscles: [ 
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.6 }, 
      { muscle: 'Tríceps', role: 'secondary', activation: 0.6 }, { muscle: 'Core', role: 'stabilizer', activation: 0.3 } 
    ],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 7, efc: 3.5, cnc: 3.5, ssc: 0.1
  },
  {
    id: 'db_incline_dumbbell_press', name: 'Press Inclinado con Mancuernas', description: 'Enfatiza el pectoral superior con mayor libertad de movimiento.',
    involvedMuscles: [ 
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.7 }, 
      { muscle: 'Tríceps', role: 'secondary', activation: 0.5 }, { muscle: 'Core', role: 'stabilizer', activation: 0.3 } 
    ],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 8, efc: 3.5, cnc: 3.5, ssc: 0.1
  },
  {
    id: 'db_dips', name: 'Fondos en Paralelas', description: 'Ejercicio compuesto para pectoral inferior y tríceps.',
    involvedMuscles: [ 
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.8 }, 
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.6 }, { muscle: 'Core', role: 'stabilizer', activation: 0.4 } 
    ],
    subMuscleGroup: 'Pectoral', category: 'Fuerza', type: 'Básico', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 8, isHallOfFame: true, efc: 3.5, cnc: 3.5, ssc: 0.2
  },
  {
    id: 'db_push_up', name: 'Flexiones de Brazos', description: 'Ejercicio de peso corporal fundamental.',
    involvedMuscles: [ 
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.6 }, 
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.5 }, { muscle: 'Abdomen', role: 'stabilizer', activation: 0.5 } 
    ],
    subMuscleGroup: 'Pectoral', category: 'Resistencia', type: 'Básico', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 4, efc: 2.5, cnc: 2.0, ssc: 0.1
  },
  {
    id: 'db_cable_crossover', name: 'Cruces de Poleas', description: 'Tensión constante en el pectoral, ideal para bombeo.',
    involvedMuscles: [ 
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.4 } 
    ],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },

  // --- ESPALDA ---
  {
    id: 'db_pull_up', name: 'Dominadas Pronas', description: 'Ejercicio rey para la amplitud de espalda.',
    involvedMuscles: [ 
      { muscle: 'Dorsales', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }, 
      { muscle: 'Braquiorradial', role: 'secondary', activation: 0.5 }, { muscle: 'Core', role: 'stabilizer', activation: 0.4 } 
    ],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Básico', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 8, isHallOfFame: true, efc: 4.0, cnc: 4.0, ssc: 0.2
  },
  {
    id: 'db_chin_up', name: 'Dominadas Supinas', description: 'Variante con agarre supino, mayor énfasis en bíceps.',
    involvedMuscles: [ 
      { muscle: 'Dorsales', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'primary', activation: 1.0 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.4 }
    ],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Básico', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 7, efc: 3.8, cnc: 3.8, ssc: 0.2
  },
  {
    id: 'db_barbell_row', name: 'Remo con Barra', description: 'Constructor de densidad de espalda.',
    involvedMuscles: [ 
      { muscle: 'Dorsales', role: 'primary', activation: 1.0 }, { muscle: 'Dorsales', role: 'secondary', activation: 0.8 }, 
      { muscle: 'Trapecio', role: 'secondary', activation: 0.7 }, { muscle: 'Espalda Baja', role: 'stabilizer', activation: 0.8 },
      { muscle: 'Isquiosurales', role: 'stabilizer', activation: 0.5 }
    ],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 9, isHallOfFame: true, efc: 4.2, cnc: 4.0, ssc: 1.6
  },
  {
    id: 'db_dumbbell_row', name: 'Remo con Mancuerna', description: 'Remo unilateral para corregir desbalances y mayor rango.',
    involvedMuscles: [ 
      { muscle: 'Dorsales', role: 'primary', activation: 1.0 }, { muscle: 'Dorsales', role: 'secondary', activation: 0.6 }, 
      { muscle: 'Bíceps', role: 'secondary', activation: 0.5 }, { muscle: 'Core', role: 'stabilizer', activation: 0.4 } 
    ],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 6, efc: 3.2, cnc: 2.5, ssc: 0.6
  },
  {
    id: 'db_lat_pulldown', name: 'Jalón al Pecho', description: 'Alternativa a las dominadas en polea.',
    involvedMuscles: [ 
      { muscle: 'Dorsales', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 } 
    ],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Polea', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.0, ssc: 0.1
  },
  {
    id: 'db_seated_cable_row', name: 'Remo Sentado en Polea', description: 'Tirón horizontal para densidad de espalda media.',
    involvedMuscles: [ 
      { muscle: 'Dorsales', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio', role: 'primary', activation: 1.0 }, 
      { muscle: 'Dorsales', role: 'secondary', activation: 0.8 }, { muscle: 'Espalda Baja', role: 'stabilizer', activation: 0.4 } 
    ],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Polea', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 3.0, cnc: 2.5, ssc: 0.5
  },

  // --- HOMBROS ---
  {
    id: 'db_overhead_press', name: 'Press Militar (Barra)', alias: 'OHP', description: 'Press vertical estricto.',
    involvedMuscles: [ 
      { muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Lateral', role: 'secondary', activation: 0.5 }, 
      { muscle: 'Tríceps', role: 'secondary', activation: 0.8 }, { muscle: 'Core', role: 'stabilizer', activation: 0.6 },
      { muscle: 'Glúteos', role: 'stabilizer', activation: 0.4 }
    ],
    subMuscleGroup: 'Deltoides Anterior', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 9, isHallOfFame: true, efc: 4.0, cnc: 4.2, ssc: 1.5
  },
  {
    id: 'db_dumbbell_shoulder_press', name: 'Press de Hombros (Mancuernas)', description: 'Press vertical con mayor libertad de movimiento.',
    involvedMuscles: [ 
      { muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Lateral', role: 'secondary', activation: 0.6 },
      { muscle: 'Tríceps', role: 'secondary', activation: 0.7 }, { muscle: 'Espalda Baja', role: 'stabilizer', activation: 0.3 }
    ],
    subMuscleGroup: 'Deltoides Anterior', category: 'Hipertrofia', type: 'Básico', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 6, efc: 3.2, cnc: 3.0, ssc: 1.2
  },
  {
    id: 'db_lateral_raise', name: 'Elevaciones Laterales (Mancuernas)', description: 'Aislamiento para la cabeza lateral del deltoides.',
    involvedMuscles: [ 
      { muscle: 'Deltoides Lateral', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio', role: 'stabilizer', activation: 0.4 } 
    ],
    subMuscleGroup: 'Deltoides Lateral', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'full',
    setupTime: 1, technicalDifficulty: 6, efc: 1.5, cnc: 1.5, ssc: 0.2
  },
  // --- BRAZOS (Bíceps/Tríceps) ---
  {
    id: 'db_barbell_curl', name: 'Curl con Barra Recta', description: 'Constructor de masa para bíceps.',
    involvedMuscles: [ 
      { muscle: 'Bíceps', role: 'primary', activation: 1.0 }, { muscle: 'Antebrazo', role: 'stabilizer', activation: 0.4 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.3 }
    ],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Barra', force: 'Tirón', bodyPart: 'upper', chain: 'anterior', efc: 2.0, cnc: 2.0, ssc: 0.3
  },
  {
    id: 'db_triceps_pushdown', name: 'Extensiones de Tríceps en Polea', description: 'Aislamiento cabeza lateral/medial.',
    involvedMuscles: [ { muscle: 'Tríceps', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Tríceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Empuje', bodyPart: 'upper', chain: 'anterior', efc: 1.5, cnc: 1.2, ssc: 0.1
  },
  {
    id: 'db_skull_crusher', name: 'Press Francés', description: 'Aislamiento cabeza larga.',
    involvedMuscles: [ 
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 }, { muscle: 'Codos', role: 'stabilizer', activation: 0.3 } 
    ],
    subMuscleGroup: 'Tríceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior', efc: 2.2, cnc: 2.0, ssc: 0.2
  },

  // --- CORE ---
  {
    id: 'db_plank', name: 'Plancha Abdominal', description: 'Isométrico de estabilidad.',
    involvedMuscles: [ 
      { muscle: 'Transverso Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Recto Abdominal', role: 'secondary', activation: 0.8 },
      { muscle: 'Hombros', role: 'stabilizer', activation: 0.3 }
    ],
    subMuscleGroup: 'Abdomen', category: 'Resistencia', type: 'Aislamiento', equipment: 'Peso Corporal', force: 'Anti-Rotación', bodyPart: 'full', chain: 'anterior', efc: 1.5, cnc: 2.0, ssc: 0.1
  },
  {
    id: 'db_hanging_leg_raises', name: 'Elevaciones de Piernas', description: 'Flexión de cadera y columna.',
    involvedMuscles: [ 
      { muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Flexores de Cadera', role: 'secondary', activation: 0.8 },
      { muscle: 'Antebrazo', role: 'stabilizer', activation: 0.5 }
    ],
    subMuscleGroup: 'Abdomen', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Peso Corporal', force: 'Otro', bodyPart: 'full', chain: 'anterior', efc: 2.2, cnc: 2.0, ssc: 0.2
  },
  {
    id: 'db_ab_wheel', name: 'Rueda Abdominal', description: 'Anti-extensión avanzada.',
    involvedMuscles: [ 
      { muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Transverso Abdominal', role: 'primary', activation: 1.0 },
      { muscle: 'Dorsales', role: 'stabilizer', activation: 0.6 }, { muscle: 'Tríceps', role: 'stabilizer', activation: 0.4 }
    ],
    subMuscleGroup: 'Abdomen', category: 'Fuerza', type: 'Accesorio', equipment: 'Otro', force: 'Anti-Rotación', bodyPart: 'full', chain: 'anterior', efc: 2.5, cnc: 2.5, ssc: 0.5
  },


  // =================================================================
  // TREN INFERIOR
  // =================================================================

  // --- 1. SQUAT SPECIALTY & VARIATIONS ---
  {
    id: 'db_squat_high_bar', name: 'Sentadilla Trasera Barra Alta', alias: 'High Bar Squat', description: 'La barra descansa sobre los trapecios. Permite un torso más vertical y enfatiza los cuádriceps.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteo Mayor', role: 'secondary', activation: 0.6 }, 
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.4 }, { muscle: 'Abdomen', role: 'stabilizer', activation: 0.3 } 
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 8, isHallOfFame: true, efc: 4.5, cnc: 4.5, ssc: 1.5
  },
  {
    id: 'db_squat_low_bar', name: 'Sentadilla Trasera Barra Baja', alias: 'Low Bar Squat', description: 'La barra descansa sobre los deltoides posteriores. Mayor inclinación del torso, recluta más la cadena posterior.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteo Mayor', role: 'primary', activation: 1.0 }, 
      { muscle: 'Isquiosurales', role: 'secondary', activation: 0.5 }, { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.6 } 
    ],
    subMuscleGroup: 'Glúteos', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 9, isHallOfFame: true, efc: 4.8, cnc: 5.0, ssc: 1.8
  },
  {
    id: 'db_ssb_squat', name: 'Sentadilla con Barra de Seguridad', alias: 'Safety Squat', description: 'La barra empuja el torso hacia adelante, obligando a los erectores espinales y la espalda alta a luchar para no colapsar.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio', role: 'secondary', activation: 0.7 }, 
      { muscle: 'Erectores Espinales', role: 'secondary', activation: 0.8 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 } 
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 7, efc: 4.3, cnc: 4.0, ssc: 1.4
  },
  {
    id: 'db_buffalo_squat', name: 'Sentadilla Buffalo', description: 'Barra curva que reduce el estrés en los hombros y bíceps, permitiendo mantener altas cargas.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteo Mayor', role: 'primary', activation: 1.0 },
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.5 }
    ],
    subMuscleGroup: 'Piernas', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'full',
    setupTime: 4, technicalDifficulty: 7, efc: 4.4, cnc: 4.4, ssc: 1.4
  },
  {
    id: 'db_spider_bar_squat', name: 'Sentadilla con Barra Spider', description: 'Variante inestable que enfatiza la fuerza del torso y cuádriceps para mantenerse erguido.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'primary', activation: 0.8 },
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.6 }
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 8, efc: 4.5, cnc: 4.5, ssc: 1.5
  },
  {
    id: 'db_front_squat', name: 'Sentadilla Frontal', description: 'La barra descansa en los deltoides anteriores. Torso muy vertical, gran énfasis en cuádriceps y core torácico.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.6 }, 
      { muscle: 'Abdomen', role: 'stabilizer', activation: 0.8 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 } 
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 9, efc: 4.2, cnc: 4.5, ssc: 1.2
  },
  {
    id: 'db_zercher_squat', name: 'Sentadilla Zercher', description: 'La barra se sostiene en el pliegue de los codos. Brutal para el core, la espalda alta y los cuádriceps.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 }, 
      { muscle: 'Trapecio', role: 'secondary', activation: 0.8 }, { muscle: 'Abdomen', role: 'stabilizer', activation: 0.8 },
      { muscle: 'Bíceps', role: 'stabilizer', activation: 0.5 }
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 8, efc: 4.6, cnc: 4.8, ssc: 1.9
  },
  {
    id: 'db_jefferson_squat', name: 'Sentadilla Jefferson', description: 'Sentadilla asimétrica con la barra entre las piernas. Desarrolla fuerza en planos raros y estabilidad.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Aductores', role: 'primary', activation: 1.0 }, 
      { muscle: 'Glúteos', role: 'secondary', activation: 0.6 } 
    ],
    subMuscleGroup: 'Piernas', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'full',
    setupTime: 4, technicalDifficulty: 7, efc: 3.8, cnc: 3.5, ssc: 0.8
  },
  {
    id: 'db_somersault_squat', name: 'Sentadilla Somersault', description: 'Variante rara enfocada en cuádriceps y movilidad, a menudo con soporte manual o Smith.',
    involvedMuscles: [ { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Otro', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 5, efc: 2.5, cnc: 2.0, ssc: 0.2
  },
  {
    id: 'db_anderson_squat', name: 'Sentadilla Anderson', description: 'Se inicia el movimiento desde los soportes (pines) en la parte baja. Elimina el ciclo de estiramiento-acortamiento.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.6 }
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'full',
    setupTime: 5, technicalDifficulty: 6, efc: 4.8, cnc: 4.8, ssc: 1.6
  },
  {
    id: 'db_pause_squat', name: 'Sentadilla con Pausa', description: 'Pausa en la parte más profunda. Elimina el rebote y construye confianza y rigidez.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.6 }, { muscle: 'Core', role: 'stabilizer', activation: 0.5 }
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Sentadilla', variantOf: 'db_squat_high_bar', bodyPart: 'lower', chain: 'full',
    setupTime: 4, technicalDifficulty: 8, efc: 4.8, cnc: 4.8, ssc: 1.6
  },
  {
    id: 'db_box_squat', name: 'Sentadilla al Cajón', description: 'Sentarse en un cajón para romper la cadena cinética concéntrica/excéntrica. Enfasis en cadera.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'secondary', activation: 0.6 }, 
      { muscle: 'Cuádriceps', role: 'secondary', activation: 0.5 }, { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.5 } 
    ],
    subMuscleGroup: 'Glúteos', category: 'Potencia', type: 'Accesorio', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'posterior',
    setupTime: 5, technicalDifficulty: 7, efc: 4.0, cnc: 4.0, ssc: 1.6
  },
  {
    id: 'db_cambered_squat', name: 'Sentadilla con Barra Cambered', description: 'Barra curvada que baja el centro de gravedad y aumenta la inestabilidad.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'secondary', activation: 0.6 }, 
      { muscle: 'Core', role: 'stabilizer', activation: 0.7 }, { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.5 } 
    ],
    subMuscleGroup: 'Piernas', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 8, efc: 4.6, cnc: 4.5, ssc: 1.6
  },
  {
    id: 'db_overhead_squat', name: 'Sentadilla Overhead', description: 'Sentadilla con la barra sostenida por encima de la cabeza. Requiere movilidad extrema.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides', role: 'stabilizer', activation: 0.8 }, 
      { muscle: 'Trapecio', role: 'stabilizer', activation: 0.8 }, { muscle: 'Core', role: 'stabilizer', activation: 0.8 } 
    ],
    subMuscleGroup: 'Cuerpo Completo', category: 'Movilidad', type: 'Básico', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'full', chain: 'full',
    setupTime: 4, technicalDifficulty: 10, efc: 4.5, cnc: 5.0, ssc: 1.4
  },
  {
    id: 'db_goblet_squat', name: 'Sentadilla Goblet', description: 'Mancuerna o Kettlebell al pecho. Excelente para aprender el patrón de sentadilla y movilidad.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.6 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.5 }
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Movilidad', type: 'Básico', equipment: 'Mancuerna', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 2.5, cnc: 2.0, ssc: 0.4
  },
  {
    id: 'db_chain_squat', name: 'Sentadilla con Cadenas', description: 'Las cadenas añaden peso a medida que subes (resistencia acomodada).',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.6 }
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Potencia', type: 'Accesorio', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'full',
    setupTime: 5, technicalDifficulty: 7, efc: 4.8, cnc: 5.0, ssc: 1.7
  },
  {
    id: 'db_assisted_squat', name: 'Sentadilla Asistida', description: 'Se realiza agarrándose a un sistema de suspensión o marco para mantener el torso vertical.',
    involvedMuscles: [ { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 } ],
    subMuscleGroup: 'Cuádriceps', category: 'Movilidad', type: 'Accesorio', equipment: 'Otro', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 2, efc: 2.0, cnc: 1.5, ssc: 0.2
  },
  {
    id: 'db_chair_stand', name: 'Levantarse de la Silla', description: 'Sentarse y levantarse de una silla con los brazos cruzados. Test de funcionalidad.',
    involvedMuscles: [ { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 } ],
    subMuscleGroup: 'Piernas', category: 'Movilidad', type: 'Básico', equipment: 'Otro', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 1, efc: 1.5, cnc: 1.0, ssc: 0.1
  },
  {
    id: 'db_bosu_squat', name: 'Sentadilla BOSU', description: 'Sentadilla sobre la superficie inestable de un BOSU.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'primary', activation: 0.8 }, 
      { muscle: 'Pantorrillas', role: 'stabilizer', activation: 0.6 }, { muscle: 'Glúteos', role: 'stabilizer', activation: 0.4 } 
    ],
    subMuscleGroup: 'Piernas', category: 'Resistencia', type: 'Accesorio', equipment: 'Otro', force: 'Sentadilla', bodyPart: 'lower', chain: 'full',
    setupTime: 2, technicalDifficulty: 6, efc: 3.0, cnc: 4.0, ssc: 0.5
  },

  // --- 2. DEADLIFT SPECIALTY & VARIATIONS ---
  {
    id: 'db_deadlift', name: 'Peso Muerto Convencional', description: 'El rey de la cadena posterior. Levantar peso muerto del suelo.',
    involvedMuscles: [ 
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, 
      { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio', role: 'stabilizer', activation: 0.8 },
      { muscle: 'Dorsales', role: 'stabilizer', activation: 0.6 }, { muscle: 'Antebrazo', role: 'stabilizer', activation: 0.6 }
    ],
    subMuscleGroup: 'Erectores Espinales', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 8, isHallOfFame: true, efc: 5.0, cnc: 5.0, ssc: 2.0
  },
  {
    id: 'db_sumo_deadlift', name: 'Peso Muerto Sumo', description: 'Pies anchos, manos dentro. Más énfasis en caderas y cuádriceps, menos en espalda baja.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Aductores', role: 'primary', activation: 1.0 }, 
      { muscle: 'Cuádriceps', role: 'secondary', activation: 0.6 }, { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.5 },
      { muscle: 'Trapecio', role: 'stabilizer', activation: 0.6 }
    ],
    subMuscleGroup: 'Glúteos', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 9, efc: 4.8, cnc: 4.8, ssc: 1.6
  },
  {
    id: 'db_semi_sumo_deadlift', name: 'Peso Muerto Semi-Sumo', description: 'Híbrido entre convencional y sumo. Pies ligeramente más anchos que hombros.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, 
      { muscle: 'Erectores Espinales', role: 'primary', activation: 0.8 }, { muscle: 'Cuádriceps', role: 'secondary', activation: 0.4 } 
    ],
    subMuscleGroup: 'Glúteos', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 8, efc: 4.9, cnc: 4.9, ssc: 1.8
  },
  {
    id: 'db_romanian_deadlift', name: 'Peso Muerto Rumano', alias: 'RDL', description: 'Inicio desde arriba, bajar hasta estirar isquios. Énfasis en hipertrofia.',
    involvedMuscles: [ 
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.7 }, { muscle: 'Antebrazo', role: 'stabilizer', activation: 0.5 }
    ],
    subMuscleGroup: 'Isquiosurales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 6, efc: 4.2, cnc: 4.0, ssc: 1.8
  },
  {
    id: 'db_stiff_leg_deadlift', name: 'Peso Muerto Piernas Rígidas', description: 'Desde el suelo con piernas casi rectas. Máximo estiramiento de isquios.',
    involvedMuscles: [ 
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Erectores Espinales', role: 'secondary', activation: 0.8 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.6 }
    ],
    subMuscleGroup: 'Isquiosurales', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 7, efc: 4.3, cnc: 4.0, ssc: 1.9
  },
  {
    id: 'db_deficit_deadlift', name: 'Peso Muerto con Déficit', description: 'Parado sobre una plataforma. Aumenta el rango de movimiento y dificultad inicial.',
    involvedMuscles: [ 
      { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, 
      { muscle: 'Cuádriceps', role: 'secondary', activation: 0.6 }, { muscle: 'Isquiosurales', role: 'secondary', activation: 0.8 } 
    ],
    subMuscleGroup: 'Erectores Espinales', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 5, technicalDifficulty: 9, efc: 5.0, cnc: 5.0, ssc: 2.0
  },
  {
    id: 'db_rack_pull', name: 'Rack Pull / Jalón de Bloques', description: 'Peso muerto parcial desde altura de rodillas. Sobrecarga espalda alta y bloqueo.',
    involvedMuscles: [ 
      { muscle: 'Trapecio', role: 'primary', activation: 1.0 }, { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 }, 
      { muscle: 'Glúteos', role: 'secondary', activation: 0.8 }, { muscle: 'Antebrazo', role: 'stabilizer', activation: 0.8 } 
    ],
    subMuscleGroup: 'Espalda', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 5, technicalDifficulty: 6, efc: 4.5, cnc: 4.5, ssc: 2.0
  },
  {
    id: 'db_paused_deadlift', name: 'Peso Muerto con Pausa', description: 'Pausa de 1-2 segundos al despegar. Enseña a mantener la tensión.',
    involvedMuscles: [ 
      { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, 
      { muscle: 'Core', role: 'stabilizer', activation: 0.8 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.8 } 
    ],
    subMuscleGroup: 'Erectores Espinales', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 8, efc: 5.0, cnc: 5.0, ssc: 2.0
  },
  {
    id: 'db_reverse_band_deadlift', name: 'Peso Muerto con Bandas Invertidas', description: 'Bandas ayudan en el despegue. Sobrecarga el bloqueo final.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio', role: 'secondary', activation: 0.8 },
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.6 }
    ],
    subMuscleGroup: 'Espalda', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 5, technicalDifficulty: 7, efc: 4.8, cnc: 5.0, ssc: 2.0
  },
  {
    id: 'db_trap_bar_deadlift', name: 'Peso Muerto con Barra Hexagonal', description: 'Barra Hexagonal. Híbrido entre sentadilla y peso muerto.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, 
      { muscle: 'Trapecio', role: 'secondary', activation: 0.6 }, { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.5 } 
    ],
    subMuscleGroup: 'Piernas', category: 'Fuerza', type: 'Básico', equipment: 'Otro', force: 'Bisagra', bodyPart: 'full', chain: 'full',
    setupTime: 4, technicalDifficulty: 4, efc: 4.5, cnc: 4.0, ssc: 1.4
  },
  {
    id: 'db_snatch_grip_deadlift', name: 'Peso Muerto Agarre Arrancada', description: 'Agarre muy ancho. Aumenta el rango de movimiento y demanda de espalda alta.',
    involvedMuscles: [ 
      { muscle: 'Trapecio', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, 
      { muscle: 'Dorsales', role: 'secondary', activation: 0.6 }, { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.8 } 
    ],
    subMuscleGroup: 'Espalda', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 8, efc: 4.8, cnc: 4.5, ssc: 1.8
  },
  {
    id: 'db_reeves_deadlift', name: 'Peso Muerto Reeves', description: 'Agarrando los discos en lugar de la barra. Extremo para agarre y trapecios.',
    involvedMuscles: [ 
      { muscle: 'Trapecio', role: 'primary', activation: 1.0 }, { muscle: 'Antebrazo', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.5 }
    ],
    subMuscleGroup: 'Espalda', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 7, efc: 4.0, cnc: 3.8, ssc: 1.5
  },
  {
    id: 'db_good_mornings', name: 'Buenos Días', description: 'Barra en la espalda, flexión de cadera. Excelente para isquios y espalda baja.',
    involvedMuscles: [ 
      { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.6 }
    ],
    subMuscleGroup: 'Isquiosurales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 7, efc: 4.0, cnc: 3.5, ssc: 2.0
  },
  {
    id: 'db_cambered_good_morning', name: 'Buenos Días con Barra Cambered', description: 'La curvatura de la barra baja el centro de gravedad y crea inestabilidad.',
    involvedMuscles: [ 
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, 
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.8 }, { muscle: 'Core', role: 'stabilizer', activation: 0.7 } 
    ],
    subMuscleGroup: 'Isquiosurales', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 8, efc: 4.2, cnc: 3.8, ssc: 2.0
  },
  {
    id: 'db_suspended_good_morning', name: 'Buenos Días Suspendidos', description: 'Se inicia desde cadenas o cintas a una altura específica.',
    involvedMuscles: [ 
      { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'secondary', activation: 0.8 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.6 }
    ],
    subMuscleGroup: 'Espalda Baja', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 5, technicalDifficulty: 8, efc: 4.5, cnc: 4.0, ssc: 2.0
  },
  {
    id: 'db_hyperextensions', name: 'Hiperextensiones (45º)', description: 'En banco inclinado. Enfocado en glúteo y espalda baja.',
    involvedMuscles: [ 
      { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Isquiosurales', role: 'secondary', activation: 0.5 }
    ],
    subMuscleGroup: 'Espalda Baja', category: 'Resistencia', type: 'Accesorio', equipment: 'Máquina', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 3, efc: 2.5, cnc: 2.0, ssc: 0.6
  },
  {
    id: 'db_reverse_hyper', name: 'Hiperextensiones Inversas', description: '"Santo grial" de la cadena posterior. Descomprime la columna lumbar.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Erectores Espinales', role: 'secondary', activation: 0.8 },
      { muscle: 'Isquiosurales', role: 'secondary', activation: 0.5 }
    ],
    subMuscleGroup: 'Glúteos', category: 'Fuerza', type: 'Accesorio', equipment: 'Máquina', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 4, efc: 2.5, cnc: 1.5, ssc: 0.0 // Valor 0.0 porque es descompresión real
  },
  {
    id: 'db_cable_pull_through', name: 'Pull-Through en Polea', description: 'Enseña la mecánica de bisagra de cadera con tensión constante.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'secondary', activation: 0.6 } 
    ],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 3, efc: 2.5, cnc: 2.0, ssc: 0.3
  },
  {
    id: 'db_kettlebell_swing', name: 'Balanceo con Pesa Rusa', description: 'Movimiento balístico de cadera. Potencia y acondicionamiento.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, 
      { muscle: 'Cardiovascular', role: 'primary', activation: 1.0 }, { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.5 } 
    ],
    subMuscleGroup: 'Glúteos', category: 'Potencia', type: 'Accesorio', equipment: 'Kettlebell', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 6, efc: 3.5, cnc: 3.0, ssc: 0.8
  },
  {
    id: 'db_prisoner_rdl', name: 'Peso Muerto Rumano Prisionero', description: 'Manos detrás de la cabeza para forzar extensión torácica. Bisagra de cadera con peso corporal.',
    involvedMuscles: [ 
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.6 }, 
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.4 } 
    ],
    subMuscleGroup: 'Isquiosurales', category: 'Movilidad', type: 'Básico', equipment: 'Peso Corporal', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 3, efc: 2.0, cnc: 1.5, ssc: 0.3
  },
  {
    id: 'db_bodyweight_hip_thrust', name: 'Puente de Caderas con Hombros Elevados', description: 'Espalda alta apoyada en banco. Extensión de cadera con peso corporal.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'secondary', activation: 0.5 } 
    ],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 3, efc: 2.0, cnc: 1.5, ssc: 0.1
  },

  // --- 4. MÁQUINAS Y UNILATERALES ---
  {
    id: 'db_leg_press_45', name: 'Prensa de Piernas', description: 'Mueve grandes cargas sin fatiga axial. Excelente para hipertrofia de piernas.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.6 } 
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Básico', equipment: 'Máquina', force: 'Empuje', bodyPart: 'lower', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 2, efc: 3.2, cnc: 2.5, ssc: 0.3
  },
  {
    id: 'db_single_leg_press', name: 'Prensa Inclinada a Una Pierna', description: 'Corrige asimetrías de fuerza entre piernas de forma segura.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 } 
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Máquina', force: 'Empuje', bodyPart: 'lower', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 3, efc: 2.8, cnc: 2.0, ssc: 0.1
  },
  {
    id: 'db_hack_squat', name: 'Sentadilla Hack (Máquina)', description: 'Estabilidad total y gran profundidad para aislar cuádriceps.',
    involvedMuscles: [ { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.4 } ],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Máquina', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 3.5, cnc: 3.0, ssc: 0.4
  },
  {
    id: 'db_barbell_hack_squat', name: 'Sentadilla Hack con Barra', description: 'Levantamiento de la barra desde el suelo por detrás de las piernas.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio', role: 'secondary', activation: 0.5 }, 
      { muscle: 'Antebrazo', role: 'stabilizer', activation: 0.6 } 
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 7, efc: 4.0, cnc: 4.0, ssc: 1.0
  },
  {
    id: 'db_pendulum_squat', name: 'Sentadilla Péndulo', description: 'Máquina que altera la curva de resistencia, permitiendo máxima flexión.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 } 
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Máquina', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 3.8, cnc: 2.8, ssc: 0.5
  },
  {
    id: 'db_belt_squat', name: 'Sentadilla con Cinturón', description: 'Carga la cadera y piernas sin comprimir la columna.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 } 
    ],
    subMuscleGroup: 'Piernas', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Máquina', force: 'Sentadilla', bodyPart: 'lower', chain: 'full',
    setupTime: 3, technicalDifficulty: 4, efc: 3.8, cnc: 2.5, ssc: 0.0
  },
  {
    id: 'db_belt_squat_march', name: 'Marcha en Sentadilla con Cinturón', description: 'Destruye los glúteos y psoas sin cargar la columna vertebral.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Abdomen', role: 'secondary', activation: 0.6 } 
    ],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Máquina', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior', 
    setupTime: 3, technicalDifficulty: 4, efc: 3.0, cnc: 2.0, ssc: 0.0
  },
  {
    id: 'db_leg_extension', name: 'Extensiones de Cuádriceps', description: 'Aislamiento puro para cuádriceps en posición acortada.',
    involvedMuscles: [ { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Extensión', bodyPart: 'lower', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.0, ssc: 0.0
  },
  {
    id: 'db_leg_curl_seated', name: 'Curl Femoral Sentado', description: 'Aislamiento de isquios en posición estirada.',
    involvedMuscles: [ { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Isquiosurales', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Flexión', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 2, efc: 2.2, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_leg_curl_lying', name: 'Curl Femoral Tumbado', description: 'Aislamiento clásico de isquiosurales.',
    involvedMuscles: [ { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Isquiosurales', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Flexión', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 2, efc: 2.0, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_nordic_curl', name: 'Curl Nórdico', description: 'Ejercicio excéntrico supremo para isquiosurales. Previene lesiones.',
    involvedMuscles: [ 
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 },
      { muscle: 'Pantorrillas', role: 'stabilizer', activation: 0.4 }
    ],
    subMuscleGroup: 'Isquiosurales', category: 'Fuerza', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Flexión', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 7, efc: 4.8, cnc: 4.0, ssc: 0.5
  },
  {
    id: 'db_reverse_nordic', name: 'Curl Nórdico Inverso', description: 'Enfocado en la extensión de rodilla y estiramiento de cuádriceps.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Abdomen', role: 'stabilizer', activation: 0.5 } 
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Movilidad', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Extensión', bodyPart: 'lower', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 5, efc: 3.5, cnc: 2.5, ssc: 0.1
  },
  {
    id: 'db_sissy_squat', name: 'Sentadilla Sissy', description: 'Aislamiento de cuádriceps con peso corporal, énfasis en recto femoral.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Abdomen', role: 'stabilizer', activation: 0.6 } 
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Peso Corporal', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 6, efc: 2.2, cnc: 2.0, ssc: 0.2
  },
  {
    id: 'db_bulgarian_split_squat', name: 'Sentadilla Búlgara', description: 'Unilateral. Odiada por todos, efectiva como ninguna para glúteo y cuádriceps.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.5 }
    ],
    subMuscleGroup: 'Piernas', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 7, efc: 3.8, cnc: 3.5, ssc: 0.8
  },
  {
    id: 'db_lunges_walking', name: 'Zancadas Caminando', description: 'Dinámico unilateral.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.4 }
    ],
    subMuscleGroup: 'Piernas', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 5, efc: 3.5, cnc: 3.5, ssc: 0.6
  },
  {
    id: 'db_reverse_lunge', name: 'Zancada Inversa', description: 'Más amable con las rodillas que la zancada frontal. Enfasis en glúteo.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.3 }
    ],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Sentadilla', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 3.2, cnc: 3.0, ssc: 0.5
  },
  {
    id: 'db_step_up', name: 'Subidas al Cajón', description: 'Unilateral puro. Brutal para glúteos si se controla la bajada.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Cuádriceps', role: 'secondary', activation: 0.6 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.4 }
    ],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'lower', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 5, efc: 3.0, cnc: 2.5, ssc: 0.3
  },
  {
    id: 'db_hip_thrust', name: 'Hip Thrust con Barra', description: 'El mejor ejercicio para aislar y cargar el glúteo mayor en acortamiento.',
    involvedMuscles: [ 
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'secondary', activation: 0.5 },
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.3 }
    ],
    subMuscleGroup: 'Glúteos', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 5, technicalDifficulty: 5, efc: 3.5, cnc: 3.0, ssc: 0.5
  },
  {
    id: 'db_kas_glute_bridge', name: 'Puente de Glúteo KAS', description: 'Similar al Hip Thrust pero con menor rango, manteniendo la tensión constante.',
    involvedMuscles: [ { muscle: 'Glúteos', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Barra', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 4, efc: 2.5, cnc: 2.0, ssc: 0.2
  },
  {
    id: 'db_glute_kickback_cable', name: 'Patada de Glúteo en Polea', description: 'Aislamiento final para glúteo mayor.',
    involvedMuscles: [ { muscle: 'Glúteos', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Extensión', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0.1
  },
  {
    id: 'db_cable_abduction', name: 'Abducción de Cadera en Polea', description: 'Para glúteo medio y estabilidad.',
    involvedMuscles: [ { muscle: 'Glúteo Medio', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Otro', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 3, efc: 1.5, cnc: 1.0, ssc: 0.0
  },
  {
    id: 'db_standing_calf_raise', name: 'Elevación de Talones De Pie', description: 'Enfasis en gastrocnemio.',
    involvedMuscles: [ 
      { muscle: 'Gastrocnemio', role: 'primary', activation: 1.0 }, { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.3 } 
    ],
    subMuscleGroup: 'Pantorrillas', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Extensión', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 2, efc: 2.0, cnc: 1.5, ssc: 1.2
  },
  {
    id: 'db_seated_calf_raise', name: 'Elevación de Talones Sentado', description: 'Enfasis en sóleo.',
    involvedMuscles: [ { muscle: 'Sóleo', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Pantorrillas', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Extensión', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.0, ssc: 0.0
  },
  {
    id: 'db_donkey_calf_raise', name: 'Elevación de Talones Burro', description: 'Estiramiento máximo del gastrocnemio.',
    involvedMuscles: [ { muscle: 'Gastrocnemio', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Pantorrillas', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Extensión', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 3, efc: 2.0, cnc: 1.2, ssc: 0.1
  },
  {
    id: 'db_tibialis_raise', name: 'Elevaciones de Tibial', description: 'Prevención de shin splints y balance de pierna.',
    involvedMuscles: [ { muscle: 'Tibial Anterior', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Pantorrillas', category: 'Resistencia', type: 'Aislamiento', equipment: 'Peso Corporal', force: 'Flexión', bodyPart: 'lower', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 1, efc: 1.2, cnc: 1.0, ssc: 0.0
  },
  {
    id: 'db_sled_push', name: 'Empuje de Trineo', description: 'Acondicionamiento y fuerza concéntrica pura de piernas.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, 
      { muscle: 'Cardiovascular', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'stabilizer', activation: 0.6 } 
    ],
    subMuscleGroup: 'Piernas', category: 'Potencia', type: 'Accesorio', equipment: 'Otro', force: 'Empuje', bodyPart: 'lower', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 4, efc: 4.0, cnc: 3.5, ssc: 0.2
  },
  {
    id: 'db_sled_pull', name: 'Arrastre de Trineo (Hacia atrás)', description: 'Salud de rodillas y bombeo de cuádriceps (Vasto Medial).',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'stabilizer', activation: 0.4 } 
    ],
    subMuscleGroup: 'Cuádriceps', category: 'Resistencia', type: 'Accesorio', equipment: 'Otro', force: 'Tirón', bodyPart: 'lower', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 3, efc: 3.5, cnc: 3.0, ssc: 0.1
  },
  {
    id: 'db_box_jump', name: 'Salto al Cajón', description: 'Potencia explosiva de tren inferior.',
    involvedMuscles: [ 
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 } 
    ],
    subMuscleGroup: 'Piernas', category: 'Pliometría', type: 'Accesorio', equipment: 'Otro', force: 'Salto', bodyPart: 'lower', chain: 'full',
    setupTime: 2, technicalDifficulty: 5, efc: 3.0, cnc: 3.5, ssc: 0.1
  },
  {
    id: 'db_adductor_machine', name: 'Máquina de Aductores', description: 'Aislamiento de la cara interna del muslo.',
    involvedMuscles: [ { muscle: 'Aductores', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Piernas', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Otro', bodyPart: 'lower', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 1, efc: 1.5, cnc: 1.0, ssc: 0.0
  },
  {
    id: 'db_abductor_machine', name: 'Máquina de Abductores', description: 'Aislamiento de glúteo medio/menor.',
    involvedMuscles: [ { muscle: 'Glúteo Medio', role: 'primary', activation: 1.0 } ],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Otro', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 1, efc: 1.5, cnc: 1.0, ssc: 0.0
  },
  // =======================================================================
  // 1. EL ECOSISTEMA ZERCHER (Fuerza Funcional y Core)
  // =======================================================================
  {
    id: 'ex-zercher-deadlift',
    name: 'Peso Muerto Zercher',
    description: 'Levantamiento desde el suelo o bloques con la barra en el pliegue de los codos. Demanda extrema de erectores espinales, core y trapecios.',
    type: 'Básico', category: 'Fuerza', equipment: 'Barra', force: 'Bisagra',
    efc: 4.8, cnc: 5.0, ssc: 2.0, 
    involvedMuscles: [
      { muscle: 'Espalda Baja', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Isquiosurales', role: 'secondary', activation: 0.6 },
      { muscle: 'Bíceps', role: 'stabilizer', activation: 0.3 },
      { muscle: 'Abdominales', role: 'stabilizer', activation: 0.3 }
    ]
  },
  {
    id: 'ex-zercher-lunge',
    name: 'Zancada Zercher',
    description: 'Zancadas sosteniendo la carga en los codos. Desplaza el centro de gravedad hacia adelante, aniquilando los cuádriceps y el core anterior.',
    type: 'Accesorio', category: 'Hipertrofia', equipment: 'Barra', force: 'Sentadilla',
    efc: 4.2, cnc: 4.5, ssc: 1.5,
    involvedMuscles: [
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.6 },
      { muscle: 'Abdominales', role: 'stabilizer', activation: 0.3 }
    ]
  },
  {
    id: 'ex-zercher-carry',
    name: 'Paseo Zercher (Carry)',
    description: 'Caminata pesada sosteniendo la barra en los codos. Ejercicio de anti-flexión y acondicionamiento metabólico.',
    type: 'Accesorio', category: 'Resistencia', equipment: 'Barra', force: 'Anti-Flexión',
    efc: 3.8, cnc: 4.0, ssc: 1.8,
    involvedMuscles: [
      { muscle: 'Abdominales', role: 'primary', activation: 1.0 },
      { muscle: 'Espalda Baja', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'secondary', activation: 0.6 },
      { muscle: 'Bíceps', role: 'stabilizer', activation: 0.3 }
    ]
  },

  // =======================================================================
  // 3. VECTORES EN POLEA Y CABLES (Hipertrofia Moderna)
  // =======================================================================
  {
    id: 'ex-cable-fly-high-low',
    name: 'Cruce de Poleas (Alto a Bajo)',
    description: 'Enfocado en las fibras esternales/costales del pectoral (Pectoral).',
    type: 'Aislamiento', category: 'Hipertrofia', equipment: 'Polea', force: 'Empuje',
    efc: 2.0, cnc: 1.5, ssc: 0.0,
    involvedMuscles: [
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 },
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.5 },
      { muscle: 'Abdominales', role: 'stabilizer', activation: 0.3 }
    ]
  },
  {
    id: 'ex-cable-fly-low-high',
    name: 'Cruce de Poleas (Bajo a Alto)',
    description: 'Enfocado en las fibras claviculares del pectoral (Pectoral).',
    type: 'Aislamiento', category: 'Hipertrofia', equipment: 'Polea', force: 'Empuje',
    efc: 2.0, cnc: 1.5, ssc: 0.0,
    involvedMuscles: [
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 },
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.6 }
    ]
  },
  {
    id: 'ex-bayesian-curl',
    name: 'Curl Bayesiano en Polea',
    description: 'Curl de bíceps de espaldas a la polea. Sobrecarga el bíceps en su posición de máximo estiramiento.',
    type: 'Aislamiento', category: 'Hipertrofia', equipment: 'Polea', force: 'Tirón',
    efc: 2.0, cnc: 1.5, ssc: 0.0,
    involvedMuscles: [
      { muscle: 'Bíceps', role: 'primary', activation: 1.0 }
    ]
  },
  {
    id: 'ex-cable-overhead-triceps',
    name: 'Extensión de Tríceps sobre la Cabeza (Polea)',
    description: 'Extensión con cuerda o barra V. Máximo estiramiento de la cabeza larga del tríceps.',
    type: 'Aislamiento', category: 'Hipertrofia', equipment: 'Polea', force: 'Empuje',
    efc: 2.0, cnc: 1.5, ssc: 0.1,
    involvedMuscles: [
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 },
      { muscle: 'Abdominales', role: 'stabilizer', activation: 0.3 }
    ]
  },
  {
    id: 'ex-cable-lateral-raise-behind',
    name: 'Elevación Lateral en Polea (Por detrás)',
    description: 'Tensión constante en el deltoides lateral con un perfil de resistencia superior a la mancuerna.',
    type: 'Aislamiento', category: 'Hipertrofia', equipment: 'Polea', force: 'Tirón',
    efc: 1.8, cnc: 1.5, ssc: 0.0,
    involvedMuscles: [
      { muscle: 'Deltoides Lateral', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'secondary', activation: 0.5 }
    ]
  },

  // =======================================================================
  // 4. MÁQUINAS CONVERGENTES Y AISLAMIENTO (Poco CNC, Mucho EFC)
  // =======================================================================
  {
    id: 'ex-machine-chest-press',
    name: 'Press de Pecho en Máquina (Convergente)',
    description: 'Permite empujar al fallo total sin estabilizadores y con alta convergencia.',
    type: 'Básico', category: 'Hipertrofia', equipment: 'Máquina', force: 'Empuje',
    efc: 3.2, cnc: 2.2, ssc: 0.1, 
    involvedMuscles: [
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 },
      { muscle: 'Tríceps', role: 'secondary', activation: 0.6 },
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.6 }
    ]
  },
  {
    id: 'ex-machine-row-chest-supported',
    name: 'Remo en Máquina (Pecho Apoyado)',
    description: 'Elimina la espalda baja de la ecuación. Puro trabajo de dorsal y trapecio.',
    type: 'Básico', category: 'Hipertrofia', equipment: 'Máquina', force: 'Tirón',
    efc: 3.2, cnc: 2.0, ssc: 0.0, 
    involvedMuscles: [
      { muscle: 'Dorsal', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'secondary', activation: 0.6 },
      { muscle: 'Bíceps', role: 'secondary', activation: 0.5 }
    ]
  },
  {
    id: 'ex-reverse-pec-deck',
    name: 'Pájaros en Máquina (Reverse Pec Deck)',
    description: 'Aislamiento puro para la cabeza posterior del hombro y zona escapular.',
    type: 'Aislamiento', category: 'Hipertrofia', equipment: 'Máquina', force: 'Tirón',
    efc: 1.5, cnc: 1.0, ssc: 0.0,
    involvedMuscles: [
      { muscle: 'Deltoides Posterior', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'secondary', activation: 0.5 }
    ]
  },

  // =======================================================================
  // 5. CALISTENIA / LASTRADOS (Poder Biomecánico)
  // =======================================================================
  {
    id: 'ex-weighted-dips',
    name: 'Fondos en Paralelas (Lastrados)',
    description: 'El "Press de Banca del tren superior". Empuje vertical-descendente brutal.',
    type: 'Básico', category: 'Fuerza', equipment: 'Peso Corporal', force: 'Empuje',
    efc: 4.2, cnc: 4.0, ssc: 0.5,
    involvedMuscles: [
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 },
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 },
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.6 },
      { muscle: 'Abdominales', role: 'stabilizer', activation: 0.3 }
    ]
  },
  {
    id: 'ex-ring-pushups',
    name: 'Flexiones en Anillas',
    description: 'Flexiones con inestabilidad tridimensional. Exige co-contracción de todo el tren superior.',
    type: 'Accesorio', category: 'Hipertrofia', equipment: 'Otro', force: 'Empuje',
    efc: 3.8, cnc: 4.0, ssc: 0.2, 
    involvedMuscles: [
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 },
      { muscle: 'Tríceps', role: 'secondary', activation: 0.6 },
      { muscle: 'Deltoides Anterior', role: 'stabilizer', activation: 0.5 },
      { muscle: 'Abdominales', role: 'stabilizer', activation: 0.4 }
    ]
  },

  // =======================================================================
  // 6. MANCUERNAS ESPECÍFICAS (Brazos y Espalda)
  // =======================================================================
  {
    id: 'ex-spider-curl',
    name: 'Curl Araña (Spider Curl)',
    description: 'Curl con pecho apoyado en banco inclinado. Elimina el impulso y aísla la contracción (cabeza corta).',
    type: 'Aislamiento', category: 'Hipertrofia', equipment: 'Mancuerna', force: 'Tirón',
    efc: 1.8, cnc: 1.5, ssc: 0.0,
    involvedMuscles: [
      { muscle: 'Bíceps', role: 'primary', activation: 1.0 }
    ]
  },
  {
    id: 'ex-jm-press-dumbbells',
    name: 'Press JM con Mancuernas',
    description: 'Híbrido entre press cerrado y rompecráneos. Permite sobrecarga masiva del tríceps.',
    type: 'Accesorio', category: 'Hipertrofia', equipment: 'Mancuerna', force: 'Empuje',
    efc: 2.5, cnc: 2.5, ssc: 0.1,
    involvedMuscles: [
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 },
      { muscle: 'Pectoral', role: 'secondary', activation: 0.4 }
    ]
  },
  {
    id: 'ex-chest-supported-db-row',
    name: 'Remo con Mancuernas (Pecho Apoyado Banco)',
    description: 'Tracción horizontal bilateral que elimina la necesidad de estabilización lumbar.',
    type: 'Básico', category: 'Hipertrofia', equipment: 'Mancuerna', force: 'Tirón',
    efc: 3.2, cnc: 2.2, ssc: 0.1,
    involvedMuscles: [
      { muscle: 'Dorsal', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'secondary', activation: 0.6 },
      { muscle: 'Bíceps', role: 'secondary', activation: 0.5 }
    ]
  },

  // =======================================================================
  // 7. BANDAS ELÁSTICAS Y SALUD ARTICULAR
  // =======================================================================
  {
    id: 'ex-band-pull-apart',
    name: 'Band Pull-Apart',
    description: 'Separación de banda elástica. Salud escapular, postura y activación del deltoides posterior.',
    type: 'Aislamiento', category: 'Movilidad', equipment: 'Banda', force: 'Tirón',
    efc: 1.2, cnc: 1.0, ssc: 0.0,
    involvedMuscles: [
      { muscle: 'Deltoides Posterior', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'primary', activation: 1.0 }
    ]
  },
  {
    id: 'ex-spanish-squat',
    name: 'Sentadilla Española (Bandas)',
    description: 'Sentadilla soportada con banda anclada tras las rodillas. Carga isométricamente el cuádriceps protegiendo el tendón rotuliano.',
    type: 'Accesorio', category: 'Hipertrofia', equipment: 'Banda', force: 'Sentadilla',
    efc: 3.0, cnc: 2.0, ssc: 0.2, 
    involvedMuscles: [
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.5 }
    ]
  },
  // =======================================================================
  // 8. MEGA-PAQUETE DE EXPANSIÓN v2.2 (Máquinas Premium, Poleas, Barras Altas)
  // =======================================================================
  {
    id: 'ex-machine-shoulder-press',
    name: 'Press Militar en Máquina Convergente',
    description: 'Empuje vertical estabilizado. Permite llegar al fallo seguro sin drenar el SNC equilibrando el cuerpo.',
    type: 'Básico', category: 'Hipertrofia', equipment: 'Máquina', force: 'Empuje',
    efc: 3.5, cnc: 2.2, ssc: 0.2,
    involvedMuscles: [
      { muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 },
      { muscle: 'Tríceps', role: 'secondary', activation: 0.6 },
      { muscle: 'Deltoides Lateral', role: 'secondary', activation: 0.4 }
    ]
  },
  {
    id: 'ex-machine-pullover',
    name: 'Pull-over en Máquina',
    description: 'Tensión constante en el dorsal ancho a través de un rango de movimiento masivo que las mancuernas no pueden replicar.',
    type: 'Aislamiento', category: 'Hipertrofia', equipment: 'Máquina', force: 'Tirón',
    efc: 2.8, cnc: 1.5, ssc: 0.0,
    involvedMuscles: [
      { muscle: 'Dorsal', role: 'primary', activation: 1.0 },
      { muscle: 'Pectoral', role: 'secondary', activation: 0.4 },
      { muscle: 'Tríceps', role: 'stabilizer', activation: 0.3 } 
    ]
  },
  {
    id: 'ex-iliac-cable-row',
    name: 'Remo Unilateral en Polea Baja (Foco Ilíaco)',
    description: 'Tracción de un solo brazo tirando hacia la cadera con el torso inclinado. Alineación perfecta con las fibras inferiores del dorsal.',
    type: 'Accesorio', category: 'Hipertrofia', equipment: 'Polea', force: 'Tirón',
    efc: 2.5, cnc: 1.8, ssc: 0.2,
    involvedMuscles: [
      { muscle: 'Dorsal', role: 'primary', activation: 1.0 },
      { muscle: 'Bíceps', role: 'secondary', activation: 0.4 },
      { muscle: 'Oblicuos', role: 'stabilizer', activation: 0.5 } 
    ]
  },
  {
    id: 'ex-crossbody-triceps-extension',
    name: 'Extensión de Tríceps Cruzada (Polea)',
    description: 'Trabajo unilateral de tríceps cruzando el cuerpo, respetando el ángulo natural del codo para máxima contracción.',
    type: 'Aislamiento', category: 'Hipertrofia', equipment: 'Polea', force: 'Empuje',
    efc: 1.8, cnc: 1.2, ssc: 0.0,
    involvedMuscles: [
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 }
    ]
  },
  {
    id: 'ex-cable-y-raises',
    name: 'Elevaciones en Y (Polea/Cables)',
    description: 'Elevaciones diagonales cruzadas desde polea baja. Trabajo supremo para el trapecio inferior y deltoides lateral/posterior.',
    type: 'Aislamiento', category: 'Movilidad', equipment: 'Polea', force: 'Tirón',
    efc: 1.5, cnc: 1.5, ssc: 0.1,
    involvedMuscles: [
      { muscle: 'Trapecio', role: 'primary', activation: 1.0 }, 
      { muscle: 'Deltoides Lateral', role: 'secondary', activation: 0.6 },
      { muscle: 'Deltoides Posterior', role: 'secondary', activation: 0.6 }
    ]
  },
  {
    id: 'ex-cable-crunch',
    name: 'Crunch Abdominal en Polea Alta',
    description: 'Encogimientos de rodillas usando una cuerda. Permite sobrecarga progresiva real en el abdomen.',
    type: 'Accesorio', category: 'Hipertrofia', equipment: 'Polea', force: 'Flexión',
    efc: 2.2, cnc: 1.5, ssc: 0.3, 
    involvedMuscles: [
      { muscle: 'Abdominales', role: 'primary', activation: 1.0 }
    ]
  },
  {
    id: 'ex-larsen-press',
    name: 'Larsen Press',
    description: 'Press de banca con las piernas suspendidas rectas en el aire. Elimina el "leg drive", aislando completamente pecho y tríceps.',
    type: 'Básico', category: 'Hipertrofia', equipment: 'Barra', force: 'Empuje',
    efc: 3.8, cnc: 3.5, ssc: 0.0,
    involvedMuscles: [
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 },
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 },
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.6 }
    ]
  },
  {
    id: 'ex-pendlay-row',
    name: 'Remo Pendlay',
    description: 'Remo estricto con barra que parte desde el suelo en cada repetición. Torso paralelo al piso, desarrollando potencia pura de espalda.',
    type: 'Básico', category: 'Fuerza', equipment: 'Barra', force: 'Tirón',
    efc: 4.2, cnc: 4.5, ssc: 1.6,
    involvedMuscles: [
      { muscle: 'Dorsal', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'primary', activation: 1.0 },
      { muscle: 'Espalda Baja', role: 'stabilizer', activation: 0.7 },
      { muscle: 'Bíceps', role: 'secondary', activation: 0.4 }
    ]
  },
  {
    id: 'ex-b-stance-rdl',
    name: 'RDL Postura B (B-Stance)',
    description: 'Peso muerto rumano sesgado, donde una pierna hace el 80% del trabajo y la otra solo da equilibrio. Ideal con mancuernas.',
    type: 'Accesorio', category: 'Hipertrofia', equipment: 'Mancuerna', force: 'Bisagra',
    efc: 3.8, cnc: 3.2, ssc: 1.0, 
    involvedMuscles: [
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Espalda Baja', role: 'stabilizer', activation: 0.4 }
    ]
  },
  {
    id: 'ex-kroc-rows',
    name: 'Remo Kroc (Mancuerna Pesada)',
    description: 'Remo a una mano con mancuerna, usando pesos masivos a altas repeticiones, permitiendo un ligero impulso corporal ("body english").',
    type: 'Básico', category: 'Fuerza', equipment: 'Mancuerna', force: 'Tirón',
    efc: 4.5, cnc: 3.8, ssc: 0.8,
    involvedMuscles: [
      { muscle: 'Dorsal', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'secondary', activation: 0.6 },
      { muscle: 'Antebrazo', role: 'secondary', activation: 0.8 }, 
      { muscle: 'Bíceps', role: 'secondary', activation: 0.5 }
    ]
  },
  {
    id: 'ex-chest-supported-rear-delt',
    name: 'Pájaros en Banco Inclinado (Mancuernas)',
    description: 'Abducción horizontal apoyando el pecho. Evita balanceos e incendia la parte posterior del hombro.',
    type: 'Aislamiento', category: 'Hipertrofia', equipment: 'Mancuerna', force: 'Tirón',
    efc: 1.8, cnc: 1.5, ssc: 0.0,
    involvedMuscles: [
      { muscle: 'Deltoides Posterior', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'secondary', activation: 0.5 }
    ]
  },
  {
    id: 'ex-weighted-chinup',
    name: 'Dominadas Supinas Lastradas (Chin-ups)',
    description: 'Tracción vertical con agarre invertido. Mayor reclutamiento de bíceps y estiramiento del dorsal que las dominadas pronas.',
    type: 'Básico', category: 'Fuerza', equipment: 'Peso Corporal', force: 'Tirón', 
    efc: 4.2, cnc: 4.0, ssc: 0.2,
    involvedMuscles: [
      { muscle: 'Dorsal', role: 'primary', activation: 1.0 },
      { muscle: 'Bíceps', role: 'primary', activation: 1.0 },
      { muscle: 'Abdominales', role: 'stabilizer', activation: 0.4 }
    ]
  },
  {
    id: 'ex-ghr',
    name: 'Glute-Ham Raise (GHR)',
    description: 'Flexión de rodilla con el peso corporal. Trabaja el isquiosural en ambas articulaciones (cadera y rodilla) simultáneamente.',
    type: 'Accesorio', category: 'Hipertrofia', equipment: 'Máquina', force: 'Tirón',
    efc: 4.0, cnc: 3.2, ssc: 0.2, 
    involvedMuscles: [
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 },
      { muscle: 'Gemelos', role: 'secondary', activation: 0.6 },
      { muscle: 'Glúteos', role: 'stabilizer', activation: 0.5 }
    ]
  },
  {
    id: 'ex-pallof-press',
    name: 'Press Pallof (Banda/Polea)',
    description: 'Empuje isométrico frente al pecho resistiendo la tracción lateral. Rey de los ejercicios de anti-rotación del core.',
    type: 'Aislamiento', category: 'Movilidad', equipment: 'Banda', force: 'Anti-Rotación',
    efc: 1.5, cnc: 1.5, ssc: 0.1,
    involvedMuscles: [
      { muscle: 'Abdominales', role: 'primary', activation: 1.0 }, 
      { muscle: 'Glúteos', role: 'stabilizer', activation: 0.5 }
    ]
  },
  {
    id: 'ex-copenhagen-plank',
    name: 'Plancha Copenhague',
    description: 'Plancha lateral con la pierna superior apoyada en un banco, cargando masivamente los aductores en isometría.',
    type: 'Aislamiento', category: 'Movilidad', equipment: 'Peso Corporal', force: 'Anti-Flexión',
    efc: 2.0, cnc: 2.0, ssc: 0.1,
    involvedMuscles: [
      { muscle: 'Aductores', role: 'primary', activation: 1.0 },
      { muscle: 'Abdominales', role: 'secondary', activation: 0.8 } 
    ]
  },
  // ========== EJERCICIOS DE EXPANSIÓN (~788 nuevos) ==========
  ...EXERCISE_EXPANSION_LIST,
  ...EXERCISE_EXPANSION_LIST_2,
  ...EXERCISE_EXPANSION_LIST_3
];

export const DETAILED_EXERCISE_LIST: ExerciseMuscleInfo[] = BASE_EXERCISE_LIST;