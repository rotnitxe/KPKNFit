// data/exerciseDatabaseExpansion.ts - Expansión de base de ejercicios (~788 nuevos)
import { ExerciseMuscleInfo } from '../types';

export const EXERCISE_EXPANSION_LIST: ExerciseMuscleInfo[] = [
  // ========== PECHO - Variantes y extensiones ==========
  {
    id: 'db_exp_decline_bench_press', name: 'Press de Banca Declinado', description: 'Inclinación negativa enfatiza pectoral inferior.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.6 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 6, efc: 3.6, cnc: 3.5, ssc: 0.3, variantOf: 'db_bench_press_tng'
  },
  {
    id: 'db_exp_decline_dumbbell_press', name: 'Press Declinado con Mancuernas', description: 'Mayor rango de movimiento que la barra en declive.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.5 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 6, efc: 3.4, cnc: 3.2, ssc: 0.2
  },
  {
    id: 'db_exp_close_grip_bench', name: 'Press de Banca Agarres Cerrado', description: 'Mayor énfasis en tríceps y porción interna del pectoral.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }, { muscle: 'Pectoral', role: 'secondary', activation: 0.7 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Tríceps', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 7, efc: 3.8, cnc: 3.8, ssc: 0.3, variantOf: 'db_bench_press_tng'
  },
  {
    id: 'db_exp_wide_grip_bench', name: 'Press de Banca Agarres Ancho', description: 'Mayor estiramiento del pectoral, menor recorrido.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Pectoral', role: 'secondary', activation: 0.6 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 6, efc: 3.5, cnc: 3.5, ssc: 0.3, variantOf: 'db_bench_press_tng'
  },
  {
    id: 'db_exp_dumbbell_fly_flat', name: 'Aperturas con Mancuernas (Banco Plano)', description: 'Aislamiento del pectoral en banco plano.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.4 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_dumbbell_fly_incline', name: 'Aperturas Inclinadas con Mancuernas', description: 'Aislamiento del pectoral superior.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 5, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_dumbbell_fly_decline', name: 'Aperturas Declinadas con Mancuernas', description: 'Aislamiento del pectoral inferior.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.4 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 5, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_pec_deck', name: 'Pec Deck (Máquina de Aperturas)', description: 'Aislamiento del pectoral con tensión constante.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.4 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_push_up_diamond', name: 'Flexiones Diamante', description: 'Manos juntas bajo el pecho, mayor énfasis en tríceps.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }, { muscle: 'Pectoral', role: 'secondary', activation: 0.7 }, { muscle: 'Abdomen', role: 'stabilizer', activation: 0.5 }],
    subMuscleGroup: 'Tríceps', category: 'Resistencia', type: 'Básico', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 5, efc: 2.8, cnc: 2.5, ssc: 0.1, variantOf: 'db_push_up'
  },
  {
    id: 'db_exp_push_up_wide', name: 'Flexiones con Manos Separadas', description: 'Mayor énfasis en pectoral con agarre amplio.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.5 }, { muscle: 'Abdomen', role: 'stabilizer', activation: 0.5 }],
    subMuscleGroup: 'Pectoral', category: 'Resistencia', type: 'Básico', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 3, efc: 2.5, cnc: 2.0, ssc: 0.1, variantOf: 'db_push_up'
  },
  {
    id: 'db_exp_push_up_decline', name: 'Flexiones Declinadas', description: 'Pies elevados, mayor carga sobre pectoral superior.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.6 }, { muscle: 'Abdomen', role: 'stabilizer', activation: 0.6 }],
    subMuscleGroup: 'Pectoral', category: 'Resistencia', type: 'Básico', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 5, efc: 3.0, cnc: 2.5, ssc: 0.1, variantOf: 'db_push_up'
  },
  {
    id: 'db_exp_push_up_incline', name: 'Flexiones Inclinadas', description: 'Manos elevadas, variante más fácil para principiantes.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 0.9 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.5 }, { muscle: 'Abdomen', role: 'stabilizer', activation: 0.4 }],
    subMuscleGroup: 'Pectoral', category: 'Resistencia', type: 'Básico', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 2.0, cnc: 1.5, ssc: 0.0, variantOf: 'db_push_up'
  },
  {
    id: 'db_exp_push_up_clap', name: 'Flexiones con Palmada (Plyo)', description: 'Explosividad y potencia de tren superior.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'primary', activation: 1.0 }, { muscle: 'Abdomen', role: 'stabilizer', activation: 0.6 }],
    subMuscleGroup: 'Pectoral', category: 'Potencia', type: 'Básico', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 7, efc: 3.5, cnc: 4.0, ssc: 0.2, variantOf: 'db_push_up'
  },
  {
    id: 'db_exp_push_up_pike', name: 'Flexiones Pike (V invertida)', description: 'Mayor énfasis en hombros y core.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Abdomen', role: 'primary', activation: 0.9 }, { muscle: 'Pectoral', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Deltoides Anterior', category: 'Resistencia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 6, efc: 3.0, cnc: 2.8, ssc: 0.2, variantOf: 'db_push_up'
  },
  {
    id: 'db_exp_landmine_press', name: 'Press con Landmine (Unilateral)', description: 'Empuje en arco con barra anclada, rango natural.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 5, efc: 3.2, cnc: 3.0, ssc: 0.2
  },
  {
    id: 'db_exp_landmine_chest_press', name: 'Press de Pecho Landmine (Bilateral)', description: 'Empuje vertical con barra anclada entre las manos.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.7 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 5, efc: 3.3, cnc: 3.0, ssc: 0.2
  },
  {
    id: 'db_exp_chest_press_smith', name: 'Press de Banca en Smith', description: 'Barra guiada, ideal para principiantes o trabajo unilateral.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.6 }, { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Básico', equipment: 'Máquina', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 3.2, cnc: 2.5, ssc: 0.2
  },
  {
    id: 'db_exp_single_arm_chest_press', name: 'Press de Pecho Unilateral (Máquina)', description: 'Corrige desbalances y exige estabilización del core.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'primary', activation: 0.6 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Máquina', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 3.0, cnc: 2.5, ssc: 0.1
  },

  // ========== ESPALDA - Variantes ==========
  {
    id: 'db_exp_pull_up_neutral', name: 'Dominadas con Agarre Neutro', description: 'Palmas enfrentadas, menor estrés en hombros y codos.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.7 }, { muscle: 'Braquial', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Básico', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 7, efc: 3.8, cnc: 3.8, ssc: 0.2, variantOf: 'db_pull_up'
  },
  {
    id: 'db_exp_pull_up_wide', name: 'Dominadas con Agarre Ancho', description: 'Mayor énfasis en amplitud del dorsal.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Terraes Mayor', role: 'secondary', activation: 0.7 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Básico', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 8, efc: 4.0, cnc: 4.0, ssc: 0.2, variantOf: 'db_pull_up'
  },
  {
    id: 'db_exp_pull_up_mixed', name: 'Dominadas con Agarre Mixto', description: 'Una mano prono y otra supino, alternar para equilibrar.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }, { muscle: 'Core', role: 'stabilizer', activation: 0.5 }],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Básico', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 7, efc: 3.9, cnc: 3.9, ssc: 0.2, variantOf: 'db_pull_up'
  },
  {
    id: 'db_exp_lat_pulldown_wide', name: 'Jalón al Pecho con Agarre Ancho', description: 'Énfasis en amplitud del dorsal.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Polea', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.1, variantOf: 'db_lat_pulldown'
  },
  {
    id: 'db_exp_lat_pulldown_close', name: 'Jalón al Pecho con Agarre Cerrado', description: 'Mayor rango y énfasis en bíceps.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.7 }],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Polea', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0.1, variantOf: 'db_lat_pulldown'
  },
  {
    id: 'db_exp_lat_pulldown_neutral', name: 'Jalón al Pecho con Agarre Neutro', description: 'Barra V o asas neutras, amigable con hombros.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Polea', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0.1, variantOf: 'db_lat_pulldown'
  },
  {
    id: 'db_exp_straight_arm_pulldown', name: 'Jalón con Brazos Rectos', description: 'Aislamiento del dorsal con brazos extendidos.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'stabilizer', activation: 0.4 }],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0.0
  },
  {
    id: 'db_exp_t_bar_row', name: 'Remo con Barra T', description: 'Remo horizontal con barra anclada, gran carga en espalda media.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Romboides', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio Medio', role: 'secondary', activation: 0.8 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 6, efc: 4.0, cnc: 3.8, ssc: 1.2
  },
  {
    id: 'db_exp_chest_supported_row', name: 'Remo con Pecho Apoyado en Banco', description: 'Sin carga lumbar, puro trabajo de dorsal y trapecio.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Romboides', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 4, efc: 3.2, cnc: 2.2, ssc: 0.0
  },
  {
    id: 'db_exp_meadows_row', name: 'Remo Meadows', description: 'Remo con barra en landmine, tracción diagonal.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio', role: 'secondary', activation: 0.7 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Barra', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 5, efc: 3.5, cnc: 3.0, ssc: 0.5
  },
  {
    id: 'db_exp_face_pull', name: 'Face Pull (Tirón a la Cara)', description: 'Salud escapular y deltoides posterior.',
    involvedMuscles: [{ muscle: 'Deltoides Posterior', role: 'primary', activation: 1.0 }, { muscle: 'Romboides', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio Inferior', role: 'secondary', activation: 0.8 }],
    subMuscleGroup: 'Deltoides Posterior', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_pullover_dumbbell', name: 'Pull-over con Mancuerna', description: 'Estiramiento máximo del dorsal y transición pectoral.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Pectoral', role: 'secondary', activation: 0.6 }, { muscle: 'Tríceps', role: 'stabilizer', activation: 0.3 }],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 5, efc: 2.5, cnc: 2.0, ssc: 0.2
  },
  {
    id: 'db_exp_superman', name: 'Superman (Hiperextensión de Espalda)', description: 'Extensión de espalda en suelo para erectores y glúteos.',
    involvedMuscles: [{ muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.8 }],
    subMuscleGroup: 'Erectores Espinales', category: 'Resistencia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Extensión', bodyPart: 'full', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0.2
  },
  {
    id: 'db_exp_inverted_row', name: 'Remo Invertido (Peso Corporal)', description: 'Tirón horizontal colgado bajo barra o anillas.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Romboides', role: 'secondary', activation: 0.8 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Dorsales', category: 'Resistencia', type: 'Básico', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 5, efc: 2.8, cnc: 2.5, ssc: 0.1
  },
  {
    id: 'db_exp_barbell_row_underhand', name: 'Remo con Barra (Agarre Supino)', description: 'Mayor rango y énfasis en bíceps.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.7 }, { muscle: 'Romboides', role: 'secondary', activation: 0.7 }],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 8, efc: 4.0, cnc: 3.8, ssc: 1.5, variantOf: 'db_barbell_row'
  },
  {
    id: 'db_exp_cable_row_wide', name: 'Remo en Polea con Agarre Ancho', description: 'Énfasis en espalda media y romboides.',
    involvedMuscles: [{ muscle: 'Romboides', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio Medio', role: 'primary', activation: 1.0 }, { muscle: 'Dorsal Ancho', role: 'secondary', activation: 0.7 }],
    subMuscleGroup: 'Romboides', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Polea', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 3.0, cnc: 2.5, ssc: 0.4, variantOf: 'db_seated_cable_row'
  },

  // ========== HOMBROS - Variantes ==========
  {
    id: 'db_exp_arnold_press', name: 'Press Arnold', description: 'Rotación de hombros durante el press, trabaja las tres porciones.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Lateral', role: 'primary', activation: 0.9 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Deltoides Anterior', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 6, efc: 3.2, cnc: 3.0, ssc: 0.2
  },
  {
    id: 'db_exp_landmine_shoulder_press', name: 'Press Militar Landmine', description: 'Arco natural de movimiento, amigable con hombros.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Lateral', role: 'secondary', activation: 0.6 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Deltoides Anterior', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 5, efc: 3.5, cnc: 3.5, ssc: 0.3
  },
  {
    id: 'db_exp_dumbbell_lateral_raise', name: 'Elevaciones Laterales con Mancuernas', description: 'Aislamiento del deltoides lateral.',
    involvedMuscles: [{ muscle: 'Deltoides Lateral', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Deltoides Lateral', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_dumbbell_front_raise', name: 'Elevaciones Frontales con Mancuernas', description: 'Aislamiento del deltoides anterior.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Deltoides Anterior', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_dumbbell_rear_delt_fly', name: 'Pájaros con Mancuernas (Deltoides Posterior)', description: 'Abducción horizontal para deltoides posterior.',
    involvedMuscles: [{ muscle: 'Deltoides Posterior', role: 'primary', activation: 1.0 }, { muscle: 'Romboides', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Deltoides Posterior', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_cable_lateral_raise', name: 'Elevaciones Laterales en Polea', description: 'Tensión constante en el deltoides lateral.',
    involvedMuscles: [{ muscle: 'Deltoides Lateral', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Deltoides Lateral', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_upright_row_barbell', name: 'Remo al Mentón con Barra', description: 'Trabajo de trapecio y deltoides lateral.',
    involvedMuscles: [{ muscle: 'Trapecio Superior', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Lateral', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'stabilizer', activation: 0.4 }],
    subMuscleGroup: 'Trapecio', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Barra', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 5, efc: 2.5, cnc: 2.2, ssc: 0.2
  },
  {
    id: 'db_exp_upright_row_dumbbell', name: 'Remo al Mentón con Mancuernas', description: 'Mayor rango y menor estrés articular que la barra.',
    involvedMuscles: [{ muscle: 'Trapecio Superior', role: 'primary', activation: 1.0 }, { muscle: 'Deltoides Lateral', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Trapecio', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 5, efc: 2.4, cnc: 2.0, ssc: 0.1
  },
  {
    id: 'db_exp_single_arm_press', name: 'Press Militar Unilateral con Mancuerna', description: 'Corrige desbalances y exige estabilización.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'primary', activation: 0.6 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Deltoides Anterior', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 6, efc: 3.2, cnc: 3.0, ssc: 0.2
  },
  {
    id: 'db_exp_bent_over_lateral_raise', name: 'Elevaciones Laterales Inclinado', description: 'Deltoides posterior con mancuernas.',
    involvedMuscles: [{ muscle: 'Deltoides Posterior', role: 'primary', activation: 1.0 }, { muscle: 'Romboides', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Deltoides Posterior', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 5, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_prone_y_raise', name: 'Elevaciones en Y (Prono)', description: 'Activación de trapecio inferior y serrato.',
    involvedMuscles: [{ muscle: 'Trapecio Inferior', role: 'primary', activation: 1.0 }, { muscle: 'Serrato Anterior', role: 'secondary', activation: 0.8 }, { muscle: 'Deltoides Posterior', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Trapecio', category: 'Movilidad', type: 'Aislamiento', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 4, efc: 1.5, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_scaption', name: 'Scaption (Elevación en Plano Escapular)', description: 'Elevación a 30° del plano frontal, amigable con hombros.',
    involvedMuscles: [{ muscle: 'Deltoides Lateral', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio Superior', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Deltoides Lateral', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.6, cnc: 1.3, ssc: 0.0
  },

  // ========== BÍCEPS - Variantes ==========
  {
    id: 'db_exp_preacher_curl', name: 'Curl en Banco Scott', description: 'Aislamiento del bíceps sin impulso, brazo apoyado.',
    involvedMuscles: [{ muscle: 'Bíceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Barra', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_incline_dumbbell_curl', name: 'Curl con Mancuernas en Banco Inclinado', description: 'Máximo estiramiento del bíceps en la parte baja.',
    involvedMuscles: [{ muscle: 'Bíceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_concentration_curl', name: 'Curl Concentrado', description: 'Curl con codo apoyado contra el muslo interno.',
    involvedMuscles: [{ muscle: 'Bíceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_hammer_curl', name: 'Curl Martillo', description: 'Agarre neutro, trabaja braquial y braquiorradial.',
    involvedMuscles: [{ muscle: 'Braquial', role: 'primary', activation: 1.0 }, { muscle: 'Braquiorradial', role: 'primary', activation: 0.9 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_cable_curl', name: 'Curl en Polea Baja', description: 'Tensión constante en todo el rango.',
    involvedMuscles: [{ muscle: 'Bíceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_reverse_curl', name: 'Curl Inverso (Agarre Prono)', description: 'Énfasis en braquiorradial y antebrazo.',
    involvedMuscles: [{ muscle: 'Braquiorradial', role: 'primary', activation: 1.0 }, { muscle: 'Antebrazo', role: 'primary', activation: 0.8 }],
    subMuscleGroup: 'Antebrazo', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Barra', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_drag_curl', name: 'Curl de Arrastre', description: 'Barra pegado al cuerpo durante el curl, menor momento de fuerza.',
    involvedMuscles: [{ muscle: 'Bíceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Barra', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_21_curl', name: 'Curl 21s', description: '7 reps mitad inferior, 7 mitad superior, 7 completos.',
    involvedMuscles: [{ muscle: 'Bíceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Barra', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 2.0, cnc: 1.8, ssc: 0.0
  },
  {
    id: 'db_exp_ez_bar_curl', name: 'Curl con Barra EZ', description: 'Agarre en ángulo reduce estrés en muñecas.',
    involvedMuscles: [{ muscle: 'Bíceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Barra', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_zottman_curl', name: 'Curl Zottman', description: 'Supino en la subida, prono en la bajada. Trabaja bíceps y antebrazo.',
    involvedMuscles: [{ muscle: 'Bíceps', role: 'primary', activation: 1.0 }, { muscle: 'Braquiorradial', role: 'primary', activation: 0.8 }, { muscle: 'Antebrazo', role: 'secondary', activation: 0.7 }],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 5, efc: 2.0, cnc: 1.8, ssc: 0.0
  },
  {
    id: 'db_exp_cross_body_hammer', name: 'Curl Martillo Cruzado', description: 'Hammer curl llevando la mancuerna hacia el hombro opuesto.',
    involvedMuscles: [{ muscle: 'Braquial', role: 'primary', activation: 1.0 }, { muscle: 'Braquiorradial', role: 'secondary', activation: 0.8 }],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_lying_cable_curl', name: 'Curl en Polea Acostado', description: 'Curl con espalda en banco, brazo perpendicular al suelo.',
    involvedMuscles: [{ muscle: 'Bíceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Bíceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },

  // ========== TRÍCEPS - Variantes ==========
  {
    id: 'db_exp_tricep_pushdown', name: 'Extensión de Tríceps en Polea Alta', description: 'Pushdown con barra o cuerda.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Tríceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_tricep_kickback', name: 'Extensión de Tríceps con Mancuerna (Kickback)', description: 'Extensión con brazo paralelo al suelo.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Tríceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.6, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_overhead_tricep_extension', name: 'Extensión de Tríceps sobre la Cabeza (Mancuerna)', description: 'Cabeza larga del tríceps en máximo estiramiento.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Tríceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.1
  },
  {
    id: 'db_exp_dips_bench', name: 'Fondos en Banco (Tríceps)', description: 'Fondos entre dos bancos, énfasis en tríceps.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }, { muscle: 'Pectoral', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Tríceps', category: 'Resistencia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 4, efc: 2.5, cnc: 2.0, ssc: 0.0, variantOf: 'db_dips'
  },
  {
    id: 'db_exp_skull_crusher_ez', name: 'Press Francés con Barra EZ', description: 'Menor estrés en muñecas que barra recta.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Tríceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 5, efc: 2.2, cnc: 2.0, ssc: 0.2, variantOf: 'db_skull_crusher'
  },
  {
    id: 'db_exp_close_grip_floor_press', name: 'Floor Press con Agarre Cerrado', description: 'Press en suelo, codos tocan al bajar. Aísla tríceps.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }, { muscle: 'Pectoral', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Tríceps', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 5, efc: 3.5, cnc: 3.2, ssc: 0.1
  },
  {
    id: 'db_exp_tate_press', name: 'Tate Press (Extensión en Banco)', description: 'Mancuernas sobre pecho, extensión de codos.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Tríceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_double_dumbbell_extension', name: 'Extensión de Tríceps con Dos Mancuernas', description: 'Ambas manos sujetan una mancuerna sobre la cabeza.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Tríceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.1
  },
  {
    id: 'db_exp_single_arm_pushdown', name: 'Pushdown Unilateral en Polea', description: 'Trabajo unilateral de tríceps.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Tríceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Polea', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_jm_press', name: 'Press JM', description: 'Híbrido entre press cerrado y extensión. Máxima carga en tríceps.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }, { muscle: 'Pectoral', role: 'secondary', activation: 0.4 }],
    subMuscleGroup: 'Tríceps', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 7, efc: 3.2, cnc: 3.0, ssc: 0.2
  },
  {
    id: 'db_exp_lying_tricep_extension', name: 'Extensión de Tríceps Tumbado (Barra)', description: 'Press francés con barra en banco plano.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Tríceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 5, efc: 2.2, cnc: 2.0, ssc: 0.2, variantOf: 'db_skull_crusher'
  },

  // ========== ANTEBRAZO ==========
  {
    id: 'db_exp_wrist_curl', name: 'Curl de Muñeca (Supino)', description: 'Flexión de muñeca para flexores del antebrazo.',
    involvedMuscles: [{ muscle: 'Flexores del Antebrazo', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Antebrazo', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Barra', force: 'Flexión', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1.0, ssc: 0.0
  },
  {
    id: 'db_exp_reverse_wrist_curl', name: 'Curl de Muñeca Inverso', description: 'Extensión de muñeca para extensores del antebrazo.',
    involvedMuscles: [{ muscle: 'Extensores del Antebrazo', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Antebrazo', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Barra', force: 'Extensión', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1.0, ssc: 0.0
  },
  {
    id: 'db_exp_farmer_carry', name: 'Farmer Carry (Caminata con Peso)', description: 'Sujetar peso y caminar. Grip y core.',
    involvedMuscles: [{ muscle: 'Antebrazo', role: 'primary', activation: 1.0 }, { muscle: 'Trapecio', role: 'secondary', activation: 0.8 }, { muscle: 'Core', role: 'stabilizer', activation: 0.7 }],
    subMuscleGroup: 'Antebrazo', category: 'Fuerza', type: 'Accesorio', equipment: 'Mancuerna', force: 'Otro', bodyPart: 'full', chain: 'full',
    setupTime: 2, technicalDifficulty: 3, efc: 3.0, cnc: 2.5, ssc: 0.5
  },
  {
    id: 'db_exp_plate_pinch', name: 'Pinza de Discos', description: 'Sujetar discos entre el pulgar y dedos. Fuerza de agarre.',
    involvedMuscles: [{ muscle: 'Antebrazo', role: 'primary', activation: 1.0 }, { muscle: 'Mano', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Antebrazo', category: 'Fuerza', type: 'Aislamiento', equipment: 'Otro', force: 'Otro', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_wrist_roller', name: 'Rodillo de Muñeca', description: 'Enrollar peso con las muñecas para antebrazo.',
    involvedMuscles: [{ muscle: 'Antebrazo', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Antebrazo', category: 'Resistencia', type: 'Aislamiento', equipment: 'Otro', force: 'Otro', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.2, ssc: 0.0
  },

  // ========== PIERNAS - Variantes adicionales ==========
  {
    id: 'db_exp_curtsy_lunge', name: 'Zancada de Reverencia', description: 'Zancada hacia atrás cruzando la pierna. Mayor énfasis en glúteo medio.',
    involvedMuscles: [{ muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Cuádriceps', role: 'secondary', activation: 0.6 }, { muscle: 'Aductores', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Sentadilla', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 6, efc: 3.2, cnc: 3.0, ssc: 0.4
  },
  {
    id: 'db_exp_lateral_lunge', name: 'Zancada Lateral', description: 'Desplazamiento lateral, trabaja aductores y glúteo medio.',
    involvedMuscles: [{ muscle: 'Aductores', role: 'primary', activation: 1.0 }, { muscle: 'Glúteo Medio', role: 'primary', activation: 0.9 }, { muscle: 'Cuádriceps', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Piernas', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Sentadilla', bodyPart: 'lower', chain: 'full',
    setupTime: 2, technicalDifficulty: 5, efc: 3.0, cnc: 2.8, ssc: 0.3
  },
  {
    id: 'db_exp_frog_pump', name: 'Bomba de Rana (Glúteos)', description: 'Extensión de cadera en suelo con rodillas abiertas.',
    involvedMuscles: [{ muscle: 'Glúteos', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Peso Corporal', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_single_leg_rdl', name: 'Peso Muerto Rumano a Una Pierna', description: 'RDL unilateral, equilibrio y estabilización.',
    involvedMuscles: [{ muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'primary', activation: 0.7 }],
    subMuscleGroup: 'Isquiosurales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 7, efc: 3.8, cnc: 3.5, ssc: 1.0, variantOf: 'db_romanian_deadlift'
  },
  {
    id: 'db_exp_good_morning', name: 'Good Morning con Barra', description: 'Bisagra de cadera con barra en espalda.',
    involvedMuscles: [{ muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 0.9 }, { muscle: 'Erectores Espinales', role: 'primary', activation: 0.8 }],
    subMuscleGroup: 'Isquiosurales', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 8, efc: 4.0, cnc: 4.0, ssc: 1.6
  },
  {
    id: 'db_exp_horizontal_leg_press', name: 'Prensa de Piernas Horizontal', description: 'Plano horizontal, menor carga axial.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Básico', equipment: 'Máquina', force: 'Empuje', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 2, efc: 3.0, cnc: 2.2, ssc: 0.1
  },
  {
    id: 'db_exp_sissy_squat_machine', name: 'Sentadilla Sissy en Máquina', description: 'Aislamiento de cuádriceps asistido por máquina.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0.1, variantOf: 'db_sissy_squat'
  },
  {
    id: 'db_exp_slider_hamstring_curl', name: 'Curl de Isquios con Deslizador', description: 'Curl femoral en suelo con deslizadores o toallas.',
    involvedMuscles: [{ muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'stabilizer', activation: 0.5 }],
    subMuscleGroup: 'Isquiosurales', category: 'Resistencia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Flexión', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 6, efc: 3.2, cnc: 2.5, ssc: 0.0
  },
  {
    id: 'db_exp_glute_bridge', name: 'Puente de Glúteos (Peso Corporal)', description: 'Extensión de cadera en suelo, activación de glúteos.',
    involvedMuscles: [{ muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'secondary', activation: 0.4 }],
    subMuscleGroup: 'Glúteos', category: 'Resistencia', type: 'Básico', equipment: 'Peso Corporal', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_cable_pull_through', name: 'Pull-Through en Polea', description: 'Bisagra de cadera con polea entre las piernas.',
    involvedMuscles: [{ muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Polea', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 3.0, cnc: 2.5, ssc: 0.2
  },
  {
    id: 'db_exp_single_leg_glute_bridge', name: 'Puente de Glúteos a Una Pierna', description: 'Puente unilateral para corregir asimetrías.',
    involvedMuscles: [{ muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'stabilizer', activation: 0.5 }],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0.0
  },
  {
    id: 'db_exp_deficit_reverse_lunge', name: 'Zancada Inversa con Déficit', description: 'Pierna trasera en elevación para mayor rango.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Piernas', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Sentadilla', bodyPart: 'lower', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 6, efc: 3.5, cnc: 3.2, ssc: 0.5, variantOf: 'db_reverse_lunge'
  },
  {
    id: 'db_exp_cossack_squat', name: 'Sentadilla Cosaca', description: 'Sentadilla lateral con una pierna extendida. Movilidad y aductores.',
    involvedMuscles: [{ muscle: 'Aductores', role: 'primary', activation: 1.0 }, { muscle: 'Cuádriceps', role: 'secondary', activation: 0.7 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Piernas', category: 'Movilidad', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Sentadilla', bodyPart: 'lower', chain: 'full',
    setupTime: 1, technicalDifficulty: 6, efc: 2.8, cnc: 2.5, ssc: 0.2
  },
  {
    id: 'db_exp_shrimp_squat', name: 'Sentadilla Camarón', description: 'Sentadilla unilateral agarrándose el pie trasero.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Piernas', category: 'Fuerza', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 8, efc: 3.8, cnc: 3.5, ssc: 0.3
  },
  {
    id: 'db_exp_pistol_squat', name: 'Sentadilla Pistola', description: 'Sentadilla unilateral con la otra pierna extendida al frente.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.6 }, { muscle: 'Core', role: 'stabilizer', activation: 0.7 }],
    subMuscleGroup: 'Piernas', category: 'Fuerza', type: 'Básico', equipment: 'Peso Corporal', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 9, efc: 3.5, cnc: 3.5, ssc: 0.4
  },
  {
    id: 'db_exp_wall_sit', name: 'Sentadilla Isométrica en Pared', description: 'Mantener posición de sentadilla contra la pared.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Cuádriceps', category: 'Resistencia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 2.0, cnc: 1.5, ssc: 0.1
  },
  {
    id: 'db_exp_calf_raise_single', name: 'Elevación de Talones a Una Pierna', description: 'Mayor rango y corrección de asimetrías en pantorrillas.',
    involvedMuscles: [{ muscle: 'Gastrocnemio', role: 'primary', activation: 1.0 }, { muscle: 'Sóleo', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Pantorrillas', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Mancuerna', force: 'Extensión', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 3, efc: 2.0, cnc: 1.5, ssc: 1.0
  },
  {
    id: 'db_exp_leg_curl_standing', name: 'Curl Femoral de Pie (Máquina)', description: 'Curl unilateral en máquina de pie.',
    involvedMuscles: [{ muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Isquiosurales', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Flexión', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 2, efc: 2.0, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_deficit_deadlift', name: 'Peso Muerto con Déficit', description: 'De pie sobre plataforma para mayor rango.',
    involvedMuscles: [{ muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Erectores Espinales', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Erectores Espinales', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 8, efc: 5.0, cnc: 5.0, ssc: 2.0, variantOf: 'db_deadlift'
  },
  {
    id: 'db_exp_block_pull', name: 'Block Pull (Peso Muerto desde Bloques)', description: 'Barra elevada reduce el rango, permite cargas mayores.',
    involvedMuscles: [{ muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 0.9 }, { muscle: 'Isquiosurales', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Erectores Espinales', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Bisagra', bodyPart: 'full', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 6, efc: 4.8, cnc: 4.8, ssc: 1.8, variantOf: 'db_deadlift'
  },
  {
    id: 'db_exp_romanian_deadlift_dumbbell', name: 'Peso Muerto Rumano con Mancuernas', description: 'RDL con mancuernas, permite mayor rango.',
    involvedMuscles: [{ muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Isquiosurales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 5, efc: 4.0, cnc: 3.5, ssc: 1.5, variantOf: 'db_romanian_deadlift'
  },
  {
    id: 'db_exp_sumo_squat', name: 'Sentadilla Sumo', description: 'Pies muy separados, punta hacia afuera. Énfasis en aductores y glúteos.',
    involvedMuscles: [{ muscle: 'Aductores', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 0.9 }, { muscle: 'Cuádriceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Piernas', category: 'Fuerza', type: 'Básico', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'full',
    setupTime: 4, technicalDifficulty: 7, efc: 4.2, cnc: 4.0, ssc: 1.2
  },
  {
    id: 'db_exp_leg_extension_unilateral', name: 'Extensión de Cuádriceps Unilateral', description: 'Trabajo unilateral en máquina de extensiones.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Máquina', force: 'Extensión', bodyPart: 'lower', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.0, ssc: 0.0
  },
  {
    id: 'db_exp_hip_abduction_band', name: 'Abducción de Cadera con Banda', description: 'Abducción lateral para glúteo medio.',
    involvedMuscles: [{ muscle: 'Glúteo Medio', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Banda', force: 'Otro', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.0, ssc: 0.0
  },
  {
    id: 'db_exp_hip_adduction_band', name: 'Aducción de Cadera con Banda', description: 'Aducción contra resistencia de banda.',
    involvedMuscles: [{ muscle: 'Aductores', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Piernas', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Banda', force: 'Otro', bodyPart: 'lower', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.0, ssc: 0.0
  },
  {
    id: 'db_exp_deficit_squat', name: 'Sentadilla con Déficit (Estabilidad)', description: 'De pie sobre superficies elevadas para mayor rango.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Cuádriceps', category: 'Fuerza', type: 'Accesorio', equipment: 'Barra', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 7, efc: 4.2, cnc: 4.0, ssc: 1.4
  },

  // ========== CORE - Variantes ==========
  {
    id: 'db_exp_dead_bug', name: 'Dead Bug', description: 'Extensión alterna de brazo y pierna en suelo. Estabilidad del core.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Transverso Abdominal', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Abdomen', category: 'Estabilidad', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Anti-Extensión', bodyPart: 'full', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_bird_dog', name: 'Bird Dog', description: 'Extensión alterna de brazo y pierna opuestos en cuadrupedia.',
    involvedMuscles: [{ muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Abdomen', category: 'Estabilidad', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Anti-Extensión', bodyPart: 'full', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 3, efc: 1.5, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_bicycle_crunch', name: 'Crunch Bicicleta', description: 'Rotación de codo a rodilla opuesta en suelo.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Oblicuos', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Abdomen', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Rotación', bodyPart: 'full', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_russian_twist', name: 'Giros Rusos', description: 'Rotación de torso con peso o sin peso.',
    involvedMuscles: [{ muscle: 'Oblicuos', role: 'primary', activation: 1.0 }, { muscle: 'Recto Abdominal', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Abdomen', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Mancuerna', force: 'Rotación', bodyPart: 'full', chain: 'full',
    setupTime: 1, technicalDifficulty: 4, efc: 2.0, cnc: 1.8, ssc: 0.0
  },
  {
    id: 'db_exp_side_plank', name: 'Plancha Lateral', description: 'Isométrico lateral para oblicuos.',
    involvedMuscles: [{ muscle: 'Oblicuos', role: 'primary', activation: 1.0 }, { muscle: 'Transverso Abdominal', role: 'primary', activation: 0.8 }],
    subMuscleGroup: 'Abdomen', category: 'Estabilidad', type: 'Aislamiento', equipment: 'Peso Corporal', force: 'Anti-Rotación', bodyPart: 'full', chain: 'full',
    setupTime: 1, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_plank_shoulder_tap', name: 'Plancha con Toque de Hombros', description: 'Plancha alta alternando toques al hombro opuesto.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Transverso Abdominal', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Abdomen', category: 'Estabilidad', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Anti-Rotación', bodyPart: 'full', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 5, efc: 2.2, cnc: 2.0, ssc: 0.0
  },
  {
    id: 'db_exp_v_up', name: 'V-Up', description: 'Elevación simultánea de piernas y torso formando V.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Flexores de Cadera', role: 'secondary', activation: 0.8 }],
    subMuscleGroup: 'Abdomen', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Flexión', bodyPart: 'full', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 6, efc: 2.5, cnc: 2.2, ssc: 0.1
  },
  {
    id: 'db_exp_mountain_climber', name: 'Escalador', description: 'Llevar rodillas al pecho alternadamente en plancha alta.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Flexores de Cadera', role: 'secondary', activation: 0.8 }, { muscle: 'Core', role: 'stabilizer', activation: 0.8 }],
    subMuscleGroup: 'Abdomen', category: 'Resistencia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Flexión', bodyPart: 'full', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 5, efc: 2.8, cnc: 2.5, ssc: 0.0
  },
  {
    id: 'db_exp_hollow_body_hold', name: 'Hollow Body Hold', description: 'Posición hueca con espalda baja pegada al suelo.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Transverso Abdominal', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Abdomen', category: 'Estabilidad', type: 'Aislamiento', equipment: 'Peso Corporal', force: 'Anti-Extensión', bodyPart: 'full', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 5, efc: 2.0, cnc: 1.8, ssc: 0.0
  },
  {
    id: 'db_exp_leg_raise_lying', name: 'Elevaciones de Piernas en Suelo', description: 'Elevar piernas desde el suelo manteniendo la espalda baja pegada.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Flexores de Cadera', role: 'secondary', activation: 0.7 }],
    subMuscleGroup: 'Abdomen', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Peso Corporal', force: 'Flexión', bodyPart: 'full', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 4, efc: 2.0, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_wood_chop_high', name: 'Wood Chop desde Arriba', description: 'Rotación con polea alta o banda.',
    involvedMuscles: [{ muscle: 'Oblicuos', role: 'primary', activation: 1.0 }, { muscle: 'Recto Abdominal', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Abdomen', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Polea', force: 'Rotación', bodyPart: 'full', chain: 'full',
    setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0.1
  },
  {
    id: 'db_exp_wood_chop_low', name: 'Wood Chop desde Abajo', description: 'Rotación con polea baja.',
    involvedMuscles: [{ muscle: 'Oblicuos', role: 'primary', activation: 1.0 }, { muscle: 'Recto Abdominal', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Abdomen', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Polea', force: 'Rotación', bodyPart: 'full', chain: 'full',
    setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0.1
  },
  {
    id: 'db_exp_plank_rock', name: 'Plancha con Balanceo', description: 'Balanceo adelante-atrás en plancha para mayor activación.',
    involvedMuscles: [{ muscle: 'Transverso Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Recto Abdominal', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Abdomen', category: 'Estabilidad', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Anti-Extensión', bodyPart: 'full', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 5, efc: 2.2, cnc: 2.0, ssc: 0.0
  },
  {
    id: 'db_exp_reverse_crunch', name: 'Crunch Inverso', description: 'Elevar cadera llevando rodillas al pecho.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Flexores de Cadera', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Abdomen', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Peso Corporal', force: 'Flexión', bodyPart: 'full', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_crunch_oblique', name: 'Crunch Oblicuo', description: 'Crunch con rotación hacia un lado.',
    involvedMuscles: [{ muscle: 'Oblicuos', role: 'primary', activation: 1.0 }, { muscle: 'Recto Abdominal', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Abdomen', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Peso Corporal', force: 'Rotación', bodyPart: 'full', chain: 'full',
    setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_stability_ball_crunch', name: 'Crunch en Fitball', description: 'Crunch sobre balón de estabilidad.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Oblicuos', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Abdomen', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Otro', force: 'Flexión', bodyPart: 'full', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_sit_up', name: 'Abdominales Clásicos', description: 'Sentarse desde tumbado. Versión tradicional.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Flexores de Cadera', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Abdomen', category: 'Resistencia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Flexión', bodyPart: 'full', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_dragon_flag', name: 'Dragon Flag', description: 'Elevación de cuerpo desde posición colgado, ejercicio avanzado de core.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Transverso Abdominal', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Abdomen', category: 'Fuerza', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Anti-Extensión', bodyPart: 'full', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 9, efc: 4.0, cnc: 3.5, ssc: 0.3
  },

  // ========== KETTLEBELL ==========
  {
    id: 'db_exp_kb_goblet_squat', name: 'Sentadilla Goblet con Kettlebell', description: 'Goblet squat con kettlebell al pecho.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Básico', equipment: 'Kettlebell', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.5, ssc: 0.3
  },
  {
    id: 'db_exp_kb_turkish_getup', name: 'Turkish Get-Up', description: 'Levantarse del suelo con kettlebell extendido. Cuerpo completo.',
    involvedMuscles: [{ muscle: 'Core', role: 'primary', activation: 1.0 }, { muscle: 'Hombros', role: 'primary', activation: 0.9 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Cuerpo Completo', category: 'Fuerza', type: 'Básico', equipment: 'Kettlebell', force: 'Otro', bodyPart: 'full', chain: 'full',
    setupTime: 3, technicalDifficulty: 9, efc: 4.0, cnc: 4.0, ssc: 0.5
  },
  {
    id: 'db_exp_kb_clean', name: 'Kettlebell Clean', description: 'Llevar kettlebell al hombro desde el suelo.',
    involvedMuscles: [{ muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Hombros', role: 'primary', activation: 0.8 }],
    subMuscleGroup: 'Cuerpo Completo', category: 'Potencia', type: 'Básico', equipment: 'Kettlebell', force: 'Empuje', bodyPart: 'full', chain: 'full',
    setupTime: 2, technicalDifficulty: 6, efc: 3.5, cnc: 3.5, ssc: 0.3
  },
  {
    id: 'db_exp_kb_snatch', name: 'Kettlebell Snatch', description: 'Llevar kettlebell sobre la cabeza en un movimiento.',
    involvedMuscles: [{ muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Hombros', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Cuerpo Completo', category: 'Potencia', type: 'Básico', equipment: 'Kettlebell', force: 'Empuje', bodyPart: 'full', chain: 'full',
    setupTime: 2, technicalDifficulty: 7, efc: 4.0, cnc: 4.0, ssc: 0.4
  },
  {
    id: 'db_exp_kb_push_press', name: 'Kettlebell Push Press', description: 'Empuje explosivo con ayuda de piernas.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Deltoides Anterior', category: 'Potencia', type: 'Básico', equipment: 'Kettlebell', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 5, efc: 3.2, cnc: 3.0, ssc: 0.2
  },
  {
    id: 'db_exp_kb_row', name: 'Remo con Kettlebell', description: 'Remo unilateral con kettlebell.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Kettlebell', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 3.0, cnc: 2.5, ssc: 0.2
  },
  {
    id: 'db_exp_kb_floor_press', name: 'Floor Press con Kettlebell', description: 'Press en suelo con kettlebells.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Pectoral', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Kettlebell', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 3.0, cnc: 2.5, ssc: 0.1
  },
  {
    id: 'db_exp_kb_suitcase_carry', name: 'Suitcase Carry con Kettlebell', description: 'Caminar con kettlebell en una mano. Anti-flexión lateral.',
    involvedMuscles: [{ muscle: 'Oblicuos', role: 'primary', activation: 1.0 }, { muscle: 'Antebrazo', role: 'secondary', activation: 0.7 }],
    subMuscleGroup: 'Abdomen', category: 'Fuerza', type: 'Accesorio', equipment: 'Kettlebell', force: 'Anti-Rotación', bodyPart: 'full', chain: 'full',
    setupTime: 2, technicalDifficulty: 4, efc: 2.5, cnc: 2.5, ssc: 0.2
  },
  {
    id: 'db_exp_kb_rack_squat', name: 'Sentadilla con Kettlebells en Rack', description: 'Kettlebells a la altura del hombro, sentadilla frontal.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'stabilizer', activation: 0.6 }],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Básico', equipment: 'Kettlebell', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 5, efc: 3.2, cnc: 2.8, ssc: 0.4
  },
  {
    id: 'db_exp_kb_sumo_deadlift', name: 'Peso Muerto Sumo con Kettlebell', description: 'Sumo deadlift sujetando kettlebell entre las piernas.',
    involvedMuscles: [{ muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Isquiosurales', role: 'primary', activation: 0.8 }],
    subMuscleGroup: 'Glúteos', category: 'Fuerza', type: 'Básico', equipment: 'Kettlebell', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 5, efc: 3.5, cnc: 3.0, ssc: 0.8
  },
  {
    id: 'db_exp_kb_bent_over_row', name: 'Remo Inclinado con Kettlebell', description: 'Remo bilateral o alterno con kettlebells.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Dorsales', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Kettlebell', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 3.0, cnc: 2.5, ssc: 0.3
  },
  {
    id: 'db_exp_kb_lateral_raise', name: 'Elevaciones Laterales con Kettlebell', description: 'Aislamiento deltoides lateral con kettlebell.',
    involvedMuscles: [{ muscle: 'Deltoides Lateral', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Deltoides Lateral', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Kettlebell', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_kb_front_raise', name: 'Elevaciones Frontales con Kettlebell', description: 'Deltoides anterior con kettlebell.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Deltoides Anterior', category: 'Hipertrofia', type: 'Aislamiento', equipment: 'Kettlebell', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_kb_around_the_world', name: 'Around the World con Kettlebell', description: 'Circunducción del kettlebell alrededor del cuerpo.',
    involvedMuscles: [{ muscle: 'Deltoides', role: 'primary', activation: 0.9 }, { muscle: 'Core', role: 'stabilizer', activation: 0.6 }],
    subMuscleGroup: 'Hombros', category: 'Resistencia', type: 'Accesorio', equipment: 'Kettlebell', force: 'Otro', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 5, efc: 2.2, cnc: 2.0, ssc: 0.0
  },

  // ========== BANDAS ELÁSTICAS ==========
  {
    id: 'db_exp_band_chest_press', name: 'Press de Pecho con Banda', description: 'Empuje contra resistencia elástica.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Pectoral', category: 'Resistencia', type: 'Accesorio', equipment: 'Banda', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0.0
  },
  {
    id: 'db_exp_band_row', name: 'Remo con Banda', description: 'Tirón horizontal con banda anclada.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Romboides', role: 'secondary', activation: 0.7 }],
    subMuscleGroup: 'Dorsales', category: 'Resistencia', type: 'Accesorio', equipment: 'Banda', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0.0
  },
  {
    id: 'db_exp_band_bicep_curl', name: 'Curl de Bíceps con Banda', description: 'Curl con resistencia elástica.',
    involvedMuscles: [{ muscle: 'Bíceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Bíceps', category: 'Resistencia', type: 'Aislamiento', equipment: 'Banda', force: 'Tirón', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_band_tricep_pushdown', name: 'Pushdown de Tríceps con Banda', description: 'Extensión de codos contra banda.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Tríceps', category: 'Resistencia', type: 'Aislamiento', equipment: 'Banda', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_band_squat', name: 'Sentadilla con Banda', description: 'Resistencia adicional en sentadilla con banda por encima de rodillas.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 0.8 }],
    subMuscleGroup: 'Cuádriceps', category: 'Hipertrofia', type: 'Básico', equipment: 'Banda', force: 'Sentadilla', bodyPart: 'lower', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 4, efc: 3.0, cnc: 2.5, ssc: 0.4
  },
  {
    id: 'db_exp_band_glute_bridge', name: 'Puente de Glúteos con Banda', description: 'Puente con banda por encima de rodillas para glúteo medio.',
    involvedMuscles: [{ muscle: 'Glúteos', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Banda', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 3, efc: 2.0, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_band_face_pull', name: 'Face Pull con Banda', description: 'Tirón a la cara con resistencia elástica.',
    involvedMuscles: [{ muscle: 'Deltoides Posterior', role: 'primary', activation: 1.0 }, { muscle: 'Romboides', role: 'primary', activation: 0.8 }],
    subMuscleGroup: 'Deltoides Posterior', category: 'Movilidad', type: 'Aislamiento', equipment: 'Banda', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 3, efc: 1.5, cnc: 1.2, ssc: 0.0
  },
  {
    id: 'db_exp_band_monster_walk', name: 'Monster Walk con Banda', description: 'Caminata lateral en cuclillas con banda en piernas.',
    involvedMuscles: [{ muscle: 'Glúteo Medio', role: 'primary', activation: 1.0 }, { muscle: 'Aductores', role: 'secondary', activation: 0.5 }],
    subMuscleGroup: 'Glúteos', category: 'Resistencia', type: 'Accesorio', equipment: 'Banda', force: 'Otro', bodyPart: 'lower', chain: 'posterior',
    setupTime: 1, technicalDifficulty: 3, efc: 2.0, cnc: 1.5, ssc: 0.0
  },
  {
    id: 'db_exp_band_leg_curl', name: 'Curl Femoral con Banda', description: 'Curl de isquios con banda anclada.',
    involvedMuscles: [{ muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Isquiosurales', category: 'Resistencia', type: 'Aislamiento', equipment: 'Banda', force: 'Flexión', bodyPart: 'lower', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0.0
  },
  {
    id: 'db_exp_band_hip_thrust', name: 'Hip Thrust con Banda', description: 'Extensión de cadera con resistencia elástica.',
    involvedMuscles: [{ muscle: 'Glúteos', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Glúteos', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Banda', force: 'Bisagra', bodyPart: 'lower', chain: 'posterior',
    setupTime: 3, technicalDifficulty: 4, efc: 2.5, cnc: 2.0, ssc: 0.0
  },

  // ========== LEVANTAMIENTO OLÍMPICO Y POWERLIFTING ==========
  {
    id: 'db_exp_power_clean', name: 'Power Clean', description: 'Cargada de potencia hasta los hombros.',
    involvedMuscles: [{ muscle: 'Glúteos', role: 'primary', activation: 1.0 }, { muscle: 'Cuádriceps', role: 'primary', activation: 0.9 }, { muscle: 'Trapecio', role: 'primary', activation: 0.8 }],
    subMuscleGroup: 'Cuerpo Completo', category: 'Potencia', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'full', chain: 'full',
    setupTime: 4, technicalDifficulty: 9, efc: 5.0, cnc: 5.0, ssc: 1.0
  },
  {
    id: 'db_exp_clean_and_jerk', name: 'Cargada y Envión', description: 'Levantamiento olímpico completo.',
    involvedMuscles: [{ muscle: 'Cuerpo Completo', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Cuerpo Completo', category: 'Potencia', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'full', chain: 'full',
    setupTime: 5, technicalDifficulty: 10, efc: 5.0, cnc: 5.0, ssc: 1.2
  },
  {
    id: 'db_exp_snatch', name: 'Arrancada (Snatch)', description: 'Levantamiento olímpico en un tiempo.',
    involvedMuscles: [{ muscle: 'Cuerpo Completo', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Cuerpo Completo', category: 'Potencia', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'full', chain: 'full',
    setupTime: 5, technicalDifficulty: 10, efc: 5.0, cnc: 5.0, ssc: 1.2
  },
  {
    id: 'db_exp_high_pull', name: 'High Pull', description: 'Tirón alto, variante del clean sin recibir la barra.',
    involvedMuscles: [{ muscle: 'Trapecio', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'primary', activation: 0.8 }],
    subMuscleGroup: 'Cuerpo Completo', category: 'Potencia', type: 'Accesorio', equipment: 'Barra', force: 'Tirón', bodyPart: 'full', chain: 'posterior',
    setupTime: 4, technicalDifficulty: 7, efc: 4.2, cnc: 4.0, ssc: 0.8
  },
  {
    id: 'db_exp_push_jerk', name: 'Push Jerk', description: 'Envión con empuje de piernas.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Hombros', category: 'Potencia', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 4, technicalDifficulty: 8, efc: 4.5, cnc: 4.5, ssc: 0.8
  },
  {
    id: 'db_exp_squat_jerk', name: 'Squat Jerk', description: 'Recibir la barra en sentadilla durante el envión.',
    involvedMuscles: [{ muscle: 'Cuerpo Completo', role: 'primary', activation: 1.0 }],
    subMuscleGroup: 'Cuerpo Completo', category: 'Potencia', type: 'Básico', equipment: 'Barra', force: 'Empuje', bodyPart: 'full', chain: 'full',
    setupTime: 5, technicalDifficulty: 10, efc: 5.0, cnc: 5.0, ssc: 1.0
  },
  {
    id: 'db_exp_front_rack_position', name: 'Posición de Rack Frontal', description: 'Hold isométrico con barra en rack frontal.',
    involvedMuscles: [{ muscle: 'Cuádriceps', role: 'primary', activation: 0.8 }, { muscle: 'Core', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Cuerpo Completo', category: 'Estabilidad', type: 'Accesorio', equipment: 'Barra', force: 'Otro', bodyPart: 'full', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 6, efc: 3.0, cnc: 3.0, ssc: 0.8
  },
  {
    id: 'db_exp_sots_press', name: 'Sots Press', description: 'Press desde sentadilla profunda.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Cuádriceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Hombros', category: 'Movilidad', type: 'Accesorio', equipment: 'Barra', force: 'Empuje', bodyPart: 'full', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 8, efc: 3.5, cnc: 3.5, ssc: 0.5
  },

  // ========== CALISTENIA Y PESO CORPORAL AVANZADO ==========
  {
    id: 'db_exp_muscle_up', name: 'Muscle-Up', description: 'Transición de dominada a fondo en anillas o barra.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Cuerpo Completo', category: 'Fuerza', type: 'Básico', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 9, efc: 4.5, cnc: 4.5, ssc: 0.2
  },
  {
    id: 'db_exp_l_sit', name: 'L-Sit', description: 'Mantener cuerpo en L sostenido con manos en paralelas o suelo.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Flexores de Cadera', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Abdomen', category: 'Fuerza', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Otro', bodyPart: 'full', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 7, efc: 3.0, cnc: 2.5, ssc: 0.0
  },
  {
    id: 'db_exp_ring_row', name: 'Remo en Anillas', description: 'Remo invertido con anillas ajustables.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Dorsales', category: 'Resistencia', type: 'Básico', equipment: 'Otro', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 5, efc: 2.8, cnc: 2.5, ssc: 0.0
  },
  {
    id: 'db_exp_ring_dip', name: 'Fondos en Anillas', description: 'Mayor inestabilidad que fondos en paralelas.',
    involvedMuscles: [{ muscle: 'Tríceps', role: 'primary', activation: 1.0 }, { muscle: 'Pectoral', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Tríceps', category: 'Fuerza', type: 'Básico', equipment: 'Otro', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 8, efc: 3.8, cnc: 3.5, ssc: 0.2
  },
  {
    id: 'db_exp_ring_push_up', name: 'Flexiones en Anillas', description: 'Mayor inestabilidad que flexiones en suelo.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Pectoral', category: 'Fuerza', type: 'Básico', equipment: 'Otro', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 6, efc: 3.2, cnc: 3.0, ssc: 0.1
  },
  {
    id: 'db_exp_handstand_push_up', name: 'Flexión en Pino', description: 'Press de hombros con peso corporal invertido.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Deltoides Anterior', category: 'Fuerza', type: 'Básico', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 3, technicalDifficulty: 9, efc: 4.0, cnc: 4.0, ssc: 0.3
  },
  {
    id: 'db_exp_planche_lean', name: 'Inclinación de Planche', description: 'Progresión hacia planche, inclinación con manos en suelo.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Cuerpo Completo', category: 'Fuerza', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 8, efc: 3.5, cnc: 3.5, ssc: 0.2
  },
  {
    id: 'db_exp_front_lever', name: 'Front Lever', description: 'Mantener cuerpo horizontal bajo la barra.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Básico', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 9, efc: 4.0, cnc: 4.0, ssc: 0.1
  },
  {
    id: 'db_exp_back_lever', name: 'Back Lever', description: 'Mantener cuerpo horizontal boca abajo bajo la barra.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Básico', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 9, efc: 4.0, cnc: 4.0, ssc: 0.2
  },
  {
    id: 'db_exp_archer_pull_up', name: 'Dominada Arquero', description: 'Dominada con un brazo más extendido que el otro.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 8, efc: 4.0, cnc: 3.8, ssc: 0.2
  },
  {
    id: 'db_exp_commando_pull_up', name: 'Dominada Comando', description: 'Dominadas alternando a cada lado de la barra.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.7 }],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 7, efc: 3.8, cnc: 3.5, ssc: 0.2
  },
  {
    id: 'db_exp_typewriter_pull_up', name: 'Dominada Typewriter', description: 'Movimiento lateral en la parte alta de la dominada.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 8, efc: 4.0, cnc: 3.8, ssc: 0.2
  },
  {
    id: 'db_exp_assisted_pull_up', name: 'Dominada Asistida', description: 'Con banda o máquina de asistencia.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Dorsales', category: 'Resistencia', type: 'Básico', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.1
  },
  {
    id: 'db_exp_hanging_knee_raise', name: 'Elevaciones de Rodillas Colgado', description: 'Variante más fácil que elevaciones de piernas.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Flexores de Cadera', role: 'secondary', activation: 0.7 }],
    subMuscleGroup: 'Abdomen', category: 'Hipertrofia', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Flexión', bodyPart: 'full', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 5, efc: 2.0, cnc: 1.8, ssc: 0.1, variantOf: 'db_hanging_leg_raises'
  },
  {
    id: 'db_exp_toes_to_bar', name: 'Toes to Bar', description: 'Llevar pies a la barra colgado.',
    involvedMuscles: [{ muscle: 'Recto Abdominal', role: 'primary', activation: 1.0 }, { muscle: 'Flexores de Cadera', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Abdomen', category: 'Fuerza', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Flexión', bodyPart: 'full', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 7, efc: 3.0, cnc: 2.5, ssc: 0.2
  },
  {
    id: 'db_exp_wall_walk', name: 'Caminata en Pared', description: 'Flexiones progresando hacia el pino.',
    involvedMuscles: [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 }, { muscle: 'Core', role: 'primary', activation: 0.8 }],
    subMuscleGroup: 'Hombros', category: 'Fuerza', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 2, technicalDifficulty: 6, efc: 3.0, cnc: 2.8, ssc: 0.2
  },
  {
    id: 'db_exp_plyo_push_up', name: 'Flexión Pliométrica', description: 'Flexión con despegue de manos del suelo.',
    involvedMuscles: [{ muscle: 'Pectoral', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'primary', activation: 0.9 }],
    subMuscleGroup: 'Pectoral', category: 'Potencia', type: 'Básico', equipment: 'Peso Corporal', force: 'Empuje', bodyPart: 'upper', chain: 'anterior',
    setupTime: 1, technicalDifficulty: 6, efc: 3.2, cnc: 3.0, ssc: 0.1
  },
  {
    id: 'db_exp_negative_pull_up', name: 'Dominada Negativa', description: 'Bajar lentamente desde la barra para ganar fuerza.',
    involvedMuscles: [{ muscle: 'Dorsal Ancho', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.6 }],
    subMuscleGroup: 'Dorsales', category: 'Fuerza', type: 'Accesorio', equipment: 'Peso Corporal', force: 'Tirón', bodyPart: 'upper', chain: 'posterior',
    setupTime: 2, technicalDifficulty: 5, efc: 3.2, cnc: 2.8, ssc: 0.1
  },
];
