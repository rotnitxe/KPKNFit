// data/exerciseDatabaseExpansion.ts
var EXERCISE_EXPANSION_LIST = [
  // ========== PECHO - Variantes y extensiones ==========
  {
    id: "db_exp_decline_bench_press",
    name: "Press de Banca Declinado",
    description: "Inclinaci\xF3n negativa enfatiza pectoral inferior.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 6,
    efc: 3.6,
    cnc: 3.5,
    ssc: 0.3,
    variantOf: "db_bench_press_tng"
  },
  {
    id: "db_exp_decline_dumbbell_press",
    name: "Press Declinado con Mancuernas",
    description: "Mayor rango de movimiento que la barra en declive.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.5 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 6,
    efc: 3.4,
    cnc: 3.2,
    ssc: 0.2
  },
  {
    id: "db_exp_close_grip_bench",
    name: "Press de Banca Agarres Cerrado",
    description: "Mayor \xE9nfasis en tr\xEDceps y porci\xF3n interna del pectoral.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Pectoral", role: "secondary", activation: 0.7 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 7,
    efc: 3.8,
    cnc: 3.8,
    ssc: 0.3,
    variantOf: "db_bench_press_tng"
  },
  {
    id: "db_exp_wide_grip_bench",
    name: "Press de Banca Agarres Ancho",
    description: "Mayor estiramiento del pectoral, menor recorrido.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Pectoral", role: "secondary", activation: 0.6 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 6,
    efc: 3.5,
    cnc: 3.5,
    ssc: 0.3,
    variantOf: "db_bench_press_tng"
  },
  {
    id: "db_exp_dumbbell_fly_flat",
    name: "Aperturas con Mancuernas (Banco Plano)",
    description: "Aislamiento del pectoral en banco plano.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.4 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_dumbbell_fly_incline",
    name: "Aperturas Inclinadas con Mancuernas",
    description: "Aislamiento del pectoral superior.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 5,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_dumbbell_fly_decline",
    name: "Aperturas Declinadas con Mancuernas",
    description: "Aislamiento del pectoral inferior.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.4 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 5,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_pec_deck",
    name: "Pec Deck (M\xE1quina de Aperturas)",
    description: "Aislamiento del pectoral con tensi\xF3n constante.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.4 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 2,
    efc: 1.6,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_push_up_diamond",
    name: "Flexiones Diamante",
    description: "Manos juntas bajo el pecho, mayor \xE9nfasis en tr\xEDceps.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Pectoral", role: "secondary", activation: 0.7 }, { muscle: "Abdomen", role: "stabilizer", activation: 0.5 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Resistencia",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 5,
    efc: 2.8,
    cnc: 2.5,
    ssc: 0.1,
    variantOf: "db_push_up"
  },
  {
    id: "db_exp_push_up_wide",
    name: "Flexiones con Manos Separadas",
    description: "Mayor \xE9nfasis en pectoral con agarre amplio.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.5 }, { muscle: "Abdomen", role: "stabilizer", activation: 0.5 }],
    subMuscleGroup: "Pectoral",
    category: "Resistencia",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 3,
    efc: 2.5,
    cnc: 2,
    ssc: 0.1,
    variantOf: "db_push_up"
  },
  {
    id: "db_exp_push_up_decline",
    name: "Flexiones Declinadas",
    description: "Pies elevados, mayor carga sobre pectoral superior.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }, { muscle: "Abdomen", role: "stabilizer", activation: 0.6 }],
    subMuscleGroup: "Pectoral",
    category: "Resistencia",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 5,
    efc: 3,
    cnc: 2.5,
    ssc: 0.1,
    variantOf: "db_push_up"
  },
  {
    id: "db_exp_push_up_incline",
    name: "Flexiones Inclinadas",
    description: "Manos elevadas, variante m\xE1s f\xE1cil para principiantes.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 0.9 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.5 }, { muscle: "Abdomen", role: "stabilizer", activation: 0.4 }],
    subMuscleGroup: "Pectoral",
    category: "Resistencia",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 2,
    cnc: 1.5,
    ssc: 0,
    variantOf: "db_push_up"
  },
  {
    id: "db_exp_push_up_clap",
    name: "Flexiones con Palmada (Plyo)",
    description: "Explosividad y potencia de tren superior.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Abdomen", role: "stabilizer", activation: 0.6 }],
    subMuscleGroup: "Pectoral",
    category: "Potencia",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 7,
    efc: 3.5,
    cnc: 4,
    ssc: 0.2,
    variantOf: "db_push_up"
  },
  {
    id: "db_exp_push_up_pike",
    name: "Flexiones Pike (V invertida)",
    description: "Mayor \xE9nfasis en hombros y core.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Abdomen", role: "primary", activation: 0.9 }, { muscle: "Pectoral", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Deltoides Anterior",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 6,
    efc: 3,
    cnc: 2.8,
    ssc: 0.2,
    variantOf: "db_push_up"
  },
  {
    id: "db_exp_landmine_press",
    name: "Press con Landmine (Unilateral)",
    description: "Empuje en arco con barra anclada, rango natural.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 5,
    efc: 3.2,
    cnc: 3,
    ssc: 0.2
  },
  {
    id: "db_exp_landmine_chest_press",
    name: "Press de Pecho Landmine (Bilateral)",
    description: "Empuje vertical con barra anclada entre las manos.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.7 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 5,
    efc: 3.3,
    cnc: 3,
    ssc: 0.2
  },
  {
    id: "db_exp_chest_press_smith",
    name: "Press de Banca en Smith",
    description: "Barra guiada, ideal para principiantes o trabajo unilateral.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "B\xE1sico",
    equipment: "M\xE1quina",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 3.2,
    cnc: 2.5,
    ssc: 0.2
  },
  {
    id: "db_exp_single_arm_chest_press",
    name: "Press de Pecho Unilateral (M\xE1quina)",
    description: "Corrige desbalances y exige estabilizaci\xF3n del core.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Core", role: "primary", activation: 0.6 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "M\xE1quina",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 3,
    cnc: 2.5,
    ssc: 0.1
  },
  // ========== ESPALDA - Variantes ==========
  {
    id: "db_exp_pull_up_neutral",
    name: "Dominadas con Agarre Neutro",
    description: "Palmas enfrentadas, menor estr\xE9s en hombros y codos.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.7 }, { muscle: "Braquial", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 7,
    efc: 3.8,
    cnc: 3.8,
    ssc: 0.2,
    variantOf: "db_pull_up"
  },
  {
    id: "db_exp_pull_up_wide",
    name: "Dominadas con Agarre Ancho",
    description: "Mayor \xE9nfasis en amplitud del dorsal.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Terraes Mayor", role: "secondary", activation: 0.7 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 8,
    efc: 4,
    cnc: 4,
    ssc: 0.2,
    variantOf: "db_pull_up"
  },
  {
    id: "db_exp_pull_up_mixed",
    name: "Dominadas con Agarre Mixto",
    description: "Una mano prono y otra supino, alternar para equilibrar.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }, { muscle: "Core", role: "stabilizer", activation: 0.5 }],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 7,
    efc: 3.9,
    cnc: 3.9,
    ssc: 0.2,
    variantOf: "db_pull_up"
  },
  {
    id: "db_exp_lat_pulldown_wide",
    name: "Jal\xF3n al Pecho con Agarre Ancho",
    description: "\xC9nfasis en amplitud del dorsal.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Polea",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 2.8,
    cnc: 2.2,
    ssc: 0.1,
    variantOf: "db_lat_pulldown"
  },
  {
    id: "db_exp_lat_pulldown_close",
    name: "Jal\xF3n al Pecho con Agarre Cerrado",
    description: "Mayor rango y \xE9nfasis en b\xEDceps.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.7 }],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Polea",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 2.8,
    cnc: 2.2,
    ssc: 0.1,
    variantOf: "db_lat_pulldown"
  },
  {
    id: "db_exp_lat_pulldown_neutral",
    name: "Jal\xF3n al Pecho con Agarre Neutro",
    description: "Barra V o asas neutras, amigable con hombros.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Polea",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 2.8,
    cnc: 2.2,
    ssc: 0.1,
    variantOf: "db_lat_pulldown"
  },
  {
    id: "db_exp_straight_arm_pulldown",
    name: "Jal\xF3n con Brazos Rectos",
    description: "Aislamiento del dorsal con brazos extendidos.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Core", role: "stabilizer", activation: 0.4 }],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 2.2,
    cnc: 1.8,
    ssc: 0
  },
  {
    id: "db_exp_t_bar_row",
    name: "Remo con Barra T",
    description: "Remo horizontal con barra anclada, gran carga en espalda media.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Romboides", role: "primary", activation: 1 }, { muscle: "Trapecio Medio", role: "secondary", activation: 0.8 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 6,
    efc: 4,
    cnc: 3.8,
    ssc: 1.2
  },
  {
    id: "db_exp_chest_supported_row",
    name: "Remo con Pecho Apoyado en Banco",
    description: "Sin carga lumbar, puro trabajo de dorsal y trapecio.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Romboides", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 4,
    efc: 3.2,
    cnc: 2.2,
    ssc: 0
  },
  {
    id: "db_exp_meadows_row",
    name: "Remo Meadows",
    description: "Remo con barra en landmine, tracci\xF3n diagonal.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Trapecio", role: "secondary", activation: 0.7 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 5,
    efc: 3.5,
    cnc: 3,
    ssc: 0.5
  },
  {
    id: "db_exp_face_pull",
    name: "Face Pull (Tir\xF3n a la Cara)",
    description: "Salud escapular y deltoides posterior.",
    involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }, { muscle: "Romboides", role: "primary", activation: 1 }, { muscle: "Trapecio Inferior", role: "secondary", activation: 0.8 }],
    subMuscleGroup: "Deltoides Posterior",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_pullover_dumbbell",
    name: "Pull-over con Mancuerna",
    description: "Estiramiento m\xE1ximo del dorsal y transici\xF3n pectoral.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Pectoral", role: "secondary", activation: 0.6 }, { muscle: "Tr\xEDceps", role: "stabilizer", activation: 0.3 }],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 5,
    efc: 2.5,
    cnc: 2,
    ssc: 0.2
  },
  {
    id: "db_exp_superman",
    name: "Superman (Hiperextensi\xF3n de Espalda)",
    description: "Extensi\xF3n de espalda en suelo para erectores y gl\xFAteos.",
    involvedMuscles: [{ muscle: "Erectores Espinales", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.8 }],
    subMuscleGroup: "Erectores Espinales",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Extensi\xF3n",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0.2
  },
  {
    id: "db_exp_inverted_row",
    name: "Remo Invertido (Peso Corporal)",
    description: "Tir\xF3n horizontal colgado bajo barra o anillas.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Romboides", role: "secondary", activation: 0.8 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Dorsales",
    category: "Resistencia",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 5,
    efc: 2.8,
    cnc: 2.5,
    ssc: 0.1
  },
  {
    id: "db_exp_barbell_row_underhand",
    name: "Remo con Barra (Agarre Supino)",
    description: "Mayor rango y \xE9nfasis en b\xEDceps.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.7 }, { muscle: "Romboides", role: "secondary", activation: 0.7 }],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 8,
    efc: 4,
    cnc: 3.8,
    ssc: 1.5,
    variantOf: "db_barbell_row"
  },
  {
    id: "db_exp_cable_row_wide",
    name: "Remo en Polea con Agarre Ancho",
    description: "\xC9nfasis en espalda media y romboides.",
    involvedMuscles: [{ muscle: "Romboides", role: "primary", activation: 1 }, { muscle: "Trapecio Medio", role: "primary", activation: 1 }, { muscle: "Dorsal Ancho", role: "secondary", activation: 0.7 }],
    subMuscleGroup: "Romboides",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Polea",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 3,
    cnc: 2.5,
    ssc: 0.4,
    variantOf: "db_seated_cable_row"
  },
  // ========== HOMBROS - Variantes ==========
  {
    id: "db_exp_arnold_press",
    name: "Press Arnold",
    description: "Rotaci\xF3n de hombros durante el press, trabaja las tres porciones.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Deltoides Lateral", role: "primary", activation: 0.9 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Deltoides Anterior",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 6,
    efc: 3.2,
    cnc: 3,
    ssc: 0.2
  },
  {
    id: "db_exp_landmine_shoulder_press",
    name: "Press Militar Landmine",
    description: "Arco natural de movimiento, amigable con hombros.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Deltoides Lateral", role: "secondary", activation: 0.6 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Deltoides Anterior",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 5,
    efc: 3.5,
    cnc: 3.5,
    ssc: 0.3
  },
  {
    id: "db_exp_dumbbell_lateral_raise",
    name: "Elevaciones Laterales con Mancuernas",
    description: "Aislamiento del deltoides lateral.",
    involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }],
    subMuscleGroup: "Deltoides Lateral",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_dumbbell_front_raise",
    name: "Elevaciones Frontales con Mancuernas",
    description: "Aislamiento del deltoides anterior.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }],
    subMuscleGroup: "Deltoides Anterior",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 1.6,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_dumbbell_rear_delt_fly",
    name: "P\xE1jaros con Mancuernas (Deltoides Posterior)",
    description: "Abducci\xF3n horizontal para deltoides posterior.",
    involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }, { muscle: "Romboides", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Deltoides Posterior",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_cable_lateral_raise",
    name: "Elevaciones Laterales en Polea",
    description: "Tensi\xF3n constante en el deltoides lateral.",
    involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }],
    subMuscleGroup: "Deltoides Lateral",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_upright_row_barbell",
    name: "Remo al Ment\xF3n con Barra",
    description: "Trabajo de trapecio y deltoides lateral.",
    involvedMuscles: [{ muscle: "Trapecio Superior", role: "primary", activation: 1 }, { muscle: "Deltoides Lateral", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "stabilizer", activation: 0.4 }],
    subMuscleGroup: "Trapecio",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 2.5,
    cnc: 2.2,
    ssc: 0.2
  },
  {
    id: "db_exp_upright_row_dumbbell",
    name: "Remo al Ment\xF3n con Mancuernas",
    description: "Mayor rango y menor estr\xE9s articular que la barra.",
    involvedMuscles: [{ muscle: "Trapecio Superior", role: "primary", activation: 1 }, { muscle: "Deltoides Lateral", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Trapecio",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 2.4,
    cnc: 2,
    ssc: 0.1
  },
  {
    id: "db_exp_single_arm_press",
    name: "Press Militar Unilateral con Mancuerna",
    description: "Corrige desbalances y exige estabilizaci\xF3n.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Core", role: "primary", activation: 0.6 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Deltoides Anterior",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 6,
    efc: 3.2,
    cnc: 3,
    ssc: 0.2
  },
  {
    id: "db_exp_bent_over_lateral_raise",
    name: "Elevaciones Laterales Inclinado",
    description: "Deltoides posterior con mancuernas.",
    involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }, { muscle: "Romboides", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Deltoides Posterior",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_prone_y_raise",
    name: "Elevaciones en Y (Prono)",
    description: "Activaci\xF3n de trapecio inferior y serrato.",
    involvedMuscles: [{ muscle: "Trapecio Inferior", role: "primary", activation: 1 }, { muscle: "Serrato Anterior", role: "secondary", activation: 0.8 }, { muscle: "Deltoides Posterior", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Trapecio",
    category: "Movilidad",
    type: "Aislamiento",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 4,
    efc: 1.5,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_scaption",
    name: "Scaption (Elevaci\xF3n en Plano Escapular)",
    description: "Elevaci\xF3n a 30\xB0 del plano frontal, amigable con hombros.",
    involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }, { muscle: "Trapecio Superior", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Deltoides Lateral",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.6,
    cnc: 1.3,
    ssc: 0
  },
  // ========== BÍCEPS - Variantes ==========
  {
    id: "db_exp_preacher_curl",
    name: "Curl en Banco Scott",
    description: "Aislamiento del b\xEDceps sin impulso, brazo apoyado.",
    involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_incline_dumbbell_curl",
    name: "Curl con Mancuernas en Banco Inclinado",
    description: "M\xE1ximo estiramiento del b\xEDceps en la parte baja.",
    involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_concentration_curl",
    name: "Curl Concentrado",
    description: "Curl con codo apoyado contra el muslo interno.",
    involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 1.6,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_hammer_curl",
    name: "Curl Martillo",
    description: "Agarre neutro, trabaja braquial y braquiorradial.",
    involvedMuscles: [{ muscle: "Braquial", role: "primary", activation: 1 }, { muscle: "Braquiorradial", role: "primary", activation: 0.9 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_cable_curl",
    name: "Curl en Polea Baja",
    description: "Tensi\xF3n constante en todo el rango.",
    involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_reverse_curl",
    name: "Curl Inverso (Agarre Prono)",
    description: "\xC9nfasis en braquiorradial y antebrazo.",
    involvedMuscles: [{ muscle: "Braquiorradial", role: "primary", activation: 1 }, { muscle: "Antebrazo", role: "primary", activation: 0.8 }],
    subMuscleGroup: "Antebrazo",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_drag_curl",
    name: "Curl de Arrastre",
    description: "Barra pegado al cuerpo durante el curl, menor momento de fuerza.",
    involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_21_curl",
    name: "Curl 21s",
    description: "7 reps mitad inferior, 7 mitad superior, 7 completos.",
    involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 2,
    cnc: 1.8,
    ssc: 0
  },
  {
    id: "db_exp_ez_bar_curl",
    name: "Curl con Barra EZ",
    description: "Agarre en \xE1ngulo reduce estr\xE9s en mu\xF1ecas.",
    involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_zottman_curl",
    name: "Curl Zottman",
    description: "Supino en la subida, prono en la bajada. Trabaja b\xEDceps y antebrazo.",
    involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }, { muscle: "Braquiorradial", role: "primary", activation: 0.8 }, { muscle: "Antebrazo", role: "secondary", activation: 0.7 }],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 2,
    cnc: 1.8,
    ssc: 0
  },
  {
    id: "db_exp_cross_body_hammer",
    name: "Curl Martillo Cruzado",
    description: "Hammer curl llevando la mancuerna hacia el hombro opuesto.",
    involvedMuscles: [{ muscle: "Braquial", role: "primary", activation: 1 }, { muscle: "Braquiorradial", role: "secondary", activation: 0.8 }],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_lying_cable_curl",
    name: "Curl en Polea Acostado",
    description: "Curl con espalda en banco, brazo perpendicular al suelo.",
    involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  // ========== TRÍCEPS - Variantes ==========
  {
    id: "db_exp_tricep_pushdown",
    name: "Extensi\xF3n de Tr\xEDceps en Polea Alta",
    description: "Pushdown con barra o cuerda.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_tricep_kickback",
    name: "Extensi\xF3n de Tr\xEDceps con Mancuerna (Kickback)",
    description: "Extensi\xF3n con brazo paralelo al suelo.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.6,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_overhead_tricep_extension",
    name: "Extensi\xF3n de Tr\xEDceps sobre la Cabeza (Mancuerna)",
    description: "Cabeza larga del tr\xEDceps en m\xE1ximo estiramiento.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0.1
  },
  {
    id: "db_exp_dips_bench",
    name: "Fondos en Banco (Tr\xEDceps)",
    description: "Fondos entre dos bancos, \xE9nfasis en tr\xEDceps.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Pectoral", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 4,
    efc: 2.5,
    cnc: 2,
    ssc: 0,
    variantOf: "db_dips"
  },
  {
    id: "db_exp_skull_crusher_ez",
    name: "Press Franc\xE9s con Barra EZ",
    description: "Menor estr\xE9s en mu\xF1ecas que barra recta.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 2.2,
    cnc: 2,
    ssc: 0.2,
    variantOf: "db_skull_crusher"
  },
  {
    id: "db_exp_close_grip_floor_press",
    name: "Floor Press con Agarre Cerrado",
    description: "Press en suelo, codos tocan al bajar. A\xEDsla tr\xEDceps.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Pectoral", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 5,
    efc: 3.5,
    cnc: 3.2,
    ssc: 0.1
  },
  {
    id: "db_exp_tate_press",
    name: "Tate Press (Extensi\xF3n en Banco)",
    description: "Mancuernas sobre pecho, extensi\xF3n de codos.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_double_dumbbell_extension",
    name: "Extensi\xF3n de Tr\xEDceps con Dos Mancuernas",
    description: "Ambas manos sujetan una mancuerna sobre la cabeza.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0.1
  },
  {
    id: "db_exp_single_arm_pushdown",
    name: "Pushdown Unilateral en Polea",
    description: "Trabajo unilateral de tr\xEDceps.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 3,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_jm_press",
    name: "Press JM",
    description: "H\xEDbrido entre press cerrado y extensi\xF3n. M\xE1xima carga en tr\xEDceps.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Pectoral", role: "secondary", activation: 0.4 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 7,
    efc: 3.2,
    cnc: 3,
    ssc: 0.2
  },
  {
    id: "db_exp_lying_tricep_extension",
    name: "Extensi\xF3n de Tr\xEDceps Tumbado (Barra)",
    description: "Press franc\xE9s con barra en banco plano.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 2.2,
    cnc: 2,
    ssc: 0.2,
    variantOf: "db_skull_crusher"
  },
  // ========== ANTEBRAZO ==========
  {
    id: "db_exp_wrist_curl",
    name: "Curl de Mu\xF1eca (Supino)",
    description: "Flexi\xF3n de mu\xF1eca para flexores del antebrazo.",
    involvedMuscles: [{ muscle: "Flexores del Antebrazo", role: "primary", activation: 1 }],
    subMuscleGroup: "Antebrazo",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Barra",
    force: "Flexi\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.2,
    cnc: 1,
    ssc: 0
  },
  {
    id: "db_exp_reverse_wrist_curl",
    name: "Curl de Mu\xF1eca Inverso",
    description: "Extensi\xF3n de mu\xF1eca para extensores del antebrazo.",
    involvedMuscles: [{ muscle: "Extensores del Antebrazo", role: "primary", activation: 1 }],
    subMuscleGroup: "Antebrazo",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Barra",
    force: "Extensi\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.2,
    cnc: 1,
    ssc: 0
  },
  {
    id: "db_exp_farmer_carry",
    name: "Farmer Carry (Caminata con Peso)",
    description: "Sujetar peso y caminar. Grip y core.",
    involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }, { muscle: "Trapecio", role: "secondary", activation: 0.8 }, { muscle: "Core", role: "stabilizer", activation: 0.7 }],
    subMuscleGroup: "Antebrazo",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Otro",
    bodyPart: "full",
    chain: "full",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 3,
    cnc: 2.5,
    ssc: 0.5
  },
  {
    id: "db_exp_plate_pinch",
    name: "Pinza de Discos",
    description: "Sujetar discos entre el pulgar y dedos. Fuerza de agarre.",
    involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }, { muscle: "Mano", role: "primary", activation: 1 }],
    subMuscleGroup: "Antebrazo",
    category: "Fuerza",
    type: "Aislamiento",
    equipment: "Otro",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.5,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_wrist_roller",
    name: "Rodillo de Mu\xF1eca",
    description: "Enrollar peso con las mu\xF1ecas para antebrazo.",
    involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }],
    subMuscleGroup: "Antebrazo",
    category: "Resistencia",
    type: "Aislamiento",
    equipment: "Otro",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.5,
    cnc: 1.2,
    ssc: 0
  },
  // ========== PIERNAS - Variantes adicionales ==========
  {
    id: "db_exp_curtsy_lunge",
    name: "Zancada de Reverencia",
    description: "Zancada hacia atr\xE1s cruzando la pierna. Mayor \xE9nfasis en gl\xFAteo medio.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }, { muscle: "Cu\xE1driceps", role: "secondary", activation: 0.6 }, { muscle: "Aductores", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 6,
    efc: 3.2,
    cnc: 3,
    ssc: 0.4
  },
  {
    id: "db_exp_lateral_lunge",
    name: "Zancada Lateral",
    description: "Desplazamiento lateral, trabaja aductores y gl\xFAteo medio.",
    involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }, { muscle: "Gl\xFAteo Medio", role: "primary", activation: 0.9 }, { muscle: "Cu\xE1driceps", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Piernas",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "full",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 3,
    cnc: 2.8,
    ssc: 0.3
  },
  {
    id: "db_exp_frog_pump",
    name: "Bomba de Rana (Gl\xFAteos)",
    description: "Extensi\xF3n de cadera en suelo con rodillas abiertas.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Peso Corporal",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.8,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_single_leg_rdl",
    name: "Peso Muerto Rumano a Una Pierna",
    description: "RDL unilateral, equilibrio y estabilizaci\xF3n.",
    involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "primary", activation: 1 }, { muscle: "Core", role: "primary", activation: 0.7 }],
    subMuscleGroup: "Isquiosurales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 7,
    efc: 3.8,
    cnc: 3.5,
    ssc: 1,
    variantOf: "db_romanian_deadlift"
  },
  {
    id: "db_exp_good_morning",
    name: "Good Morning con Barra",
    description: "Bisagra de cadera con barra en espalda.",
    involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "primary", activation: 0.9 }, { muscle: "Erectores Espinales", role: "primary", activation: 0.8 }],
    subMuscleGroup: "Isquiosurales",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 8,
    efc: 4,
    cnc: 4,
    ssc: 1.6
  },
  {
    id: "db_exp_horizontal_leg_press",
    name: "Prensa de Piernas Horizontal",
    description: "Plano horizontal, menor carga axial.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "B\xE1sico",
    equipment: "M\xE1quina",
    force: "Empuje",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 2,
    efc: 3,
    cnc: 2.2,
    ssc: 0.1
  },
  {
    id: "db_exp_sissy_squat_machine",
    name: "Sentadilla Sissy en M\xE1quina",
    description: "Aislamiento de cu\xE1driceps asistido por m\xE1quina.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 2.2,
    cnc: 1.8,
    ssc: 0.1,
    variantOf: "db_sissy_squat"
  },
  {
    id: "db_exp_slider_hamstring_curl",
    name: "Curl de Isquios con Deslizador",
    description: "Curl femoral en suelo con deslizadores o toallas.",
    involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }, { muscle: "Core", role: "stabilizer", activation: 0.5 }],
    subMuscleGroup: "Isquiosurales",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Flexi\xF3n",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 6,
    efc: 3.2,
    cnc: 2.5,
    ssc: 0
  },
  {
    id: "db_exp_glute_bridge",
    name: "Puente de Gl\xFAteos (Peso Corporal)",
    description: "Extensi\xF3n de cadera en suelo, activaci\xF3n de gl\xFAteos.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }, { muscle: "Isquiosurales", role: "secondary", activation: 0.4 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Resistencia",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.8,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_cable_pull_through",
    name: "Pull-Through en Polea",
    description: "Bisagra de cadera con polea entre las piernas.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }, { muscle: "Isquiosurales", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Polea",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 3,
    cnc: 2.5,
    ssc: 0.2
  },
  {
    id: "db_exp_single_leg_glute_bridge",
    name: "Puente de Gl\xFAteos a Una Pierna",
    description: "Puente unilateral para corregir asimetr\xEDas.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }, { muscle: "Core", role: "stabilizer", activation: 0.5 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 4,
    efc: 2.2,
    cnc: 1.8,
    ssc: 0
  },
  {
    id: "db_exp_deficit_reverse_lunge",
    name: "Zancada Inversa con D\xE9ficit",
    description: "Pierna trasera en elevaci\xF3n para mayor rango.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Piernas",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 6,
    efc: 3.5,
    cnc: 3.2,
    ssc: 0.5,
    variantOf: "db_reverse_lunge"
  },
  {
    id: "db_exp_cossack_squat",
    name: "Sentadilla Cosaca",
    description: "Sentadilla lateral con una pierna extendida. Movilidad y aductores.",
    involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }, { muscle: "Cu\xE1driceps", role: "secondary", activation: 0.7 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Piernas",
    category: "Movilidad",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "full",
    setupTime: 1,
    technicalDifficulty: 6,
    efc: 2.8,
    cnc: 2.5,
    ssc: 0.2
  },
  {
    id: "db_exp_shrimp_squat",
    name: "Sentadilla Camar\xF3n",
    description: "Sentadilla unilateral agarr\xE1ndose el pie trasero.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Piernas",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 8,
    efc: 3.8,
    cnc: 3.5,
    ssc: 0.3
  },
  {
    id: "db_exp_pistol_squat",
    name: "Sentadilla Pistola",
    description: "Sentadilla unilateral con la otra pierna extendida al frente.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }, { muscle: "Core", role: "stabilizer", activation: 0.7 }],
    subMuscleGroup: "Piernas",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 9,
    efc: 3.5,
    cnc: 3.5,
    ssc: 0.4
  },
  {
    id: "db_exp_wall_sit",
    name: "Sentadilla Isom\xE9trica en Pared",
    description: "Mantener posici\xF3n de sentadilla contra la pared.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 2,
    cnc: 1.5,
    ssc: 0.1
  },
  {
    id: "db_exp_calf_raise_single",
    name: "Elevaci\xF3n de Talones a Una Pierna",
    description: "Mayor rango y correcci\xF3n de asimetr\xEDas en pantorrillas.",
    involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }, { muscle: "S\xF3leo", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Pantorrillas",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 3,
    efc: 2,
    cnc: 1.5,
    ssc: 1
  },
  {
    id: "db_exp_leg_curl_standing",
    name: "Curl Femoral de Pie (M\xE1quina)",
    description: "Curl unilateral en m\xE1quina de pie.",
    involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }],
    subMuscleGroup: "Isquiosurales",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Flexi\xF3n",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 2,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_deficit_deadlift",
    name: "Peso Muerto con D\xE9ficit",
    description: "De pie sobre plataforma para mayor rango.",
    involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "primary", activation: 1 }, { muscle: "Erectores Espinales", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Erectores Espinales",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 8,
    efc: 5,
    cnc: 5,
    ssc: 2,
    variantOf: "db_deadlift"
  },
  {
    id: "db_exp_block_pull",
    name: "Block Pull (Peso Muerto desde Bloques)",
    description: "Barra elevada reduce el rango, permite cargas mayores.",
    involvedMuscles: [{ muscle: "Erectores Espinales", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "primary", activation: 0.9 }, { muscle: "Isquiosurales", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Erectores Espinales",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 6,
    efc: 4.8,
    cnc: 4.8,
    ssc: 1.8,
    variantOf: "db_deadlift"
  },
  {
    id: "db_exp_romanian_deadlift_dumbbell",
    name: "Peso Muerto Rumano con Mancuernas",
    description: "RDL con mancuernas, permite mayor rango.",
    involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Isquiosurales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 4,
    cnc: 3.5,
    ssc: 1.5,
    variantOf: "db_romanian_deadlift"
  },
  {
    id: "db_exp_sumo_squat",
    name: "Sentadilla Sumo",
    description: "Pies muy separados, punta hacia afuera. \xC9nfasis en aductores y gl\xFAteos.",
    involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "primary", activation: 0.9 }, { muscle: "Cu\xE1driceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Piernas",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "full",
    setupTime: 4,
    technicalDifficulty: 7,
    efc: 4.2,
    cnc: 4,
    ssc: 1.2
  },
  {
    id: "db_exp_leg_extension_unilateral",
    name: "Extensi\xF3n de Cu\xE1driceps Unilateral",
    description: "Trabajo unilateral en m\xE1quina de extensiones.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.5,
    cnc: 1,
    ssc: 0
  },
  {
    id: "db_exp_hip_abduction_band",
    name: "Abducci\xF3n de Cadera con Banda",
    description: "Abducci\xF3n lateral para gl\xFAteo medio.",
    involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Banda",
    force: "Otro",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.5,
    cnc: 1,
    ssc: 0
  },
  {
    id: "db_exp_hip_adduction_band",
    name: "Aducci\xF3n de Cadera con Banda",
    description: "Aducci\xF3n contra resistencia de banda.",
    involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }],
    subMuscleGroup: "Piernas",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Banda",
    force: "Otro",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.5,
    cnc: 1,
    ssc: 0
  },
  {
    id: "db_exp_deficit_squat",
    name: "Sentadilla con D\xE9ficit (Estabilidad)",
    description: "De pie sobre superficies elevadas para mayor rango.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 7,
    efc: 4.2,
    cnc: 4,
    ssc: 1.4
  },
  // ========== CORE - Variantes ==========
  {
    id: "db_exp_dead_bug",
    name: "Dead Bug",
    description: "Extensi\xF3n alterna de brazo y pierna en suelo. Estabilidad del core.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Transverso Abdominal", role: "primary", activation: 1 }],
    subMuscleGroup: "Abdomen",
    category: "Estabilidad",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Anti-Extensi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_bird_dog",
    name: "Bird Dog",
    description: "Extensi\xF3n alterna de brazo y pierna opuestos en cuadrupedia.",
    involvedMuscles: [{ muscle: "Erectores Espinales", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Abdomen",
    category: "Estabilidad",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Anti-Extensi\xF3n",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 3,
    efc: 1.5,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_bicycle_crunch",
    name: "Crunch Bicicleta",
    description: "Rotaci\xF3n de codo a rodilla opuesta en suelo.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Oblicuos", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Abdomen",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 3,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_russian_twist",
    name: "Giros Rusos",
    description: "Rotaci\xF3n de torso con peso o sin peso.",
    involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }, { muscle: "Recto Abdominal", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Abdomen",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Rotaci\xF3n",
    bodyPart: "full",
    chain: "full",
    setupTime: 1,
    technicalDifficulty: 4,
    efc: 2,
    cnc: 1.8,
    ssc: 0
  },
  {
    id: "db_exp_side_plank",
    name: "Plancha Lateral",
    description: "Isom\xE9trico lateral para oblicuos.",
    involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }, { muscle: "Transverso Abdominal", role: "primary", activation: 0.8 }],
    subMuscleGroup: "Abdomen",
    category: "Estabilidad",
    type: "Aislamiento",
    equipment: "Peso Corporal",
    force: "Anti-Rotaci\xF3n",
    bodyPart: "full",
    chain: "full",
    setupTime: 1,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_plank_shoulder_tap",
    name: "Plancha con Toque de Hombros",
    description: "Plancha alta alternando toques al hombro opuesto.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Transverso Abdominal", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Abdomen",
    category: "Estabilidad",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Anti-Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 5,
    efc: 2.2,
    cnc: 2,
    ssc: 0
  },
  {
    id: "db_exp_v_up",
    name: "V-Up",
    description: "Elevaci\xF3n simult\xE1nea de piernas y torso formando V.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Flexores de Cadera", role: "secondary", activation: 0.8 }],
    subMuscleGroup: "Abdomen",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 6,
    efc: 2.5,
    cnc: 2.2,
    ssc: 0.1
  },
  {
    id: "db_exp_mountain_climber",
    name: "Escalador",
    description: "Llevar rodillas al pecho alternadamente en plancha alta.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Flexores de Cadera", role: "secondary", activation: 0.8 }, { muscle: "Core", role: "stabilizer", activation: 0.8 }],
    subMuscleGroup: "Abdomen",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 5,
    efc: 2.8,
    cnc: 2.5,
    ssc: 0
  },
  {
    id: "db_exp_hollow_body_hold",
    name: "Hollow Body Hold",
    description: "Posici\xF3n hueca con espalda baja pegada al suelo.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Transverso Abdominal", role: "primary", activation: 1 }],
    subMuscleGroup: "Abdomen",
    category: "Estabilidad",
    type: "Aislamiento",
    equipment: "Peso Corporal",
    force: "Anti-Extensi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 5,
    efc: 2,
    cnc: 1.8,
    ssc: 0
  },
  {
    id: "db_exp_leg_raise_lying",
    name: "Elevaciones de Piernas en Suelo",
    description: "Elevar piernas desde el suelo manteniendo la espalda baja pegada.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Flexores de Cadera", role: "secondary", activation: 0.7 }],
    subMuscleGroup: "Abdomen",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Peso Corporal",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 4,
    efc: 2,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_wood_chop_high",
    name: "Wood Chop desde Arriba",
    description: "Rotaci\xF3n con polea alta o banda.",
    involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }, { muscle: "Recto Abdominal", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Abdomen",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Polea",
    force: "Rotaci\xF3n",
    bodyPart: "full",
    chain: "full",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 2.2,
    cnc: 1.8,
    ssc: 0.1
  },
  {
    id: "db_exp_wood_chop_low",
    name: "Wood Chop desde Abajo",
    description: "Rotaci\xF3n con polea baja.",
    involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }, { muscle: "Recto Abdominal", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Abdomen",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Polea",
    force: "Rotaci\xF3n",
    bodyPart: "full",
    chain: "full",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 2.2,
    cnc: 1.8,
    ssc: 0.1
  },
  {
    id: "db_exp_plank_rock",
    name: "Plancha con Balanceo",
    description: "Balanceo adelante-atr\xE1s en plancha para mayor activaci\xF3n.",
    involvedMuscles: [{ muscle: "Transverso Abdominal", role: "primary", activation: 1 }, { muscle: "Recto Abdominal", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Abdomen",
    category: "Estabilidad",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Anti-Extensi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 5,
    efc: 2.2,
    cnc: 2,
    ssc: 0
  },
  {
    id: "db_exp_reverse_crunch",
    name: "Crunch Inverso",
    description: "Elevar cadera llevando rodillas al pecho.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Flexores de Cadera", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Abdomen",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Peso Corporal",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 3,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_crunch_oblique",
    name: "Crunch Oblicuo",
    description: "Crunch con rotaci\xF3n hacia un lado.",
    involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }, { muscle: "Recto Abdominal", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Abdomen",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Peso Corporal",
    force: "Rotaci\xF3n",
    bodyPart: "full",
    chain: "full",
    setupTime: 1,
    technicalDifficulty: 3,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_stability_ball_crunch",
    name: "Crunch en Fitball",
    description: "Crunch sobre bal\xF3n de estabilidad.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Oblicuos", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Abdomen",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Otro",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_sit_up",
    name: "Abdominales Cl\xE1sicos",
    description: "Sentarse desde tumbado. Versi\xF3n tradicional.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Flexores de Cadera", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Abdomen",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.5,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_dragon_flag",
    name: "Dragon Flag",
    description: "Elevaci\xF3n de cuerpo desde posici\xF3n colgado, ejercicio avanzado de core.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Transverso Abdominal", role: "primary", activation: 1 }],
    subMuscleGroup: "Abdomen",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Anti-Extensi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 9,
    efc: 4,
    cnc: 3.5,
    ssc: 0.3
  },
  // ========== KETTLEBELL ==========
  {
    id: "db_exp_kb_goblet_squat",
    name: "Sentadilla Goblet con Kettlebell",
    description: "Goblet squat con kettlebell al pecho.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "B\xE1sico",
    equipment: "Kettlebell",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 2.8,
    cnc: 2.5,
    ssc: 0.3
  },
  {
    id: "db_exp_kb_turkish_getup",
    name: "Turkish Get-Up",
    description: "Levantarse del suelo con kettlebell extendido. Cuerpo completo.",
    involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }, { muscle: "Hombros", role: "primary", activation: 0.9 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Cuerpo Completo",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Kettlebell",
    force: "Otro",
    bodyPart: "full",
    chain: "full",
    setupTime: 3,
    technicalDifficulty: 9,
    efc: 4,
    cnc: 4,
    ssc: 0.5
  },
  {
    id: "db_exp_kb_clean",
    name: "Kettlebell Clean",
    description: "Llevar kettlebell al hombro desde el suelo.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }, { muscle: "Hombros", role: "primary", activation: 0.8 }],
    subMuscleGroup: "Cuerpo Completo",
    category: "Potencia",
    type: "B\xE1sico",
    equipment: "Kettlebell",
    force: "Empuje",
    bodyPart: "full",
    chain: "full",
    setupTime: 2,
    technicalDifficulty: 6,
    efc: 3.5,
    cnc: 3.5,
    ssc: 0.3
  },
  {
    id: "db_exp_kb_snatch",
    name: "Kettlebell Snatch",
    description: "Llevar kettlebell sobre la cabeza en un movimiento.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }, { muscle: "Hombros", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Cuerpo Completo",
    category: "Potencia",
    type: "B\xE1sico",
    equipment: "Kettlebell",
    force: "Empuje",
    bodyPart: "full",
    chain: "full",
    setupTime: 2,
    technicalDifficulty: 7,
    efc: 4,
    cnc: 4,
    ssc: 0.4
  },
  {
    id: "db_exp_kb_push_press",
    name: "Kettlebell Push Press",
    description: "Empuje explosivo con ayuda de piernas.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Deltoides Anterior",
    category: "Potencia",
    type: "B\xE1sico",
    equipment: "Kettlebell",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 3.2,
    cnc: 3,
    ssc: 0.2
  },
  {
    id: "db_exp_kb_row",
    name: "Remo con Kettlebell",
    description: "Remo unilateral con kettlebell.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Kettlebell",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 3,
    cnc: 2.5,
    ssc: 0.2
  },
  {
    id: "db_exp_kb_floor_press",
    name: "Floor Press con Kettlebell",
    description: "Press en suelo con kettlebells.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Kettlebell",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 3,
    cnc: 2.5,
    ssc: 0.1
  },
  {
    id: "db_exp_kb_suitcase_carry",
    name: "Suitcase Carry con Kettlebell",
    description: "Caminar con kettlebell en una mano. Anti-flexi\xF3n lateral.",
    involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }, { muscle: "Antebrazo", role: "secondary", activation: 0.7 }],
    subMuscleGroup: "Abdomen",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Kettlebell",
    force: "Anti-Rotaci\xF3n",
    bodyPart: "full",
    chain: "full",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 2.5,
    cnc: 2.5,
    ssc: 0.2
  },
  {
    id: "db_exp_kb_rack_squat",
    name: "Sentadilla con Kettlebells en Rack",
    description: "Kettlebells a la altura del hombro, sentadilla frontal.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Core", role: "stabilizer", activation: 0.6 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "B\xE1sico",
    equipment: "Kettlebell",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 5,
    efc: 3.2,
    cnc: 2.8,
    ssc: 0.4
  },
  {
    id: "db_exp_kb_sumo_deadlift",
    name: "Peso Muerto Sumo con Kettlebell",
    description: "Sumo deadlift sujetando kettlebell entre las piernas.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }, { muscle: "Isquiosurales", role: "primary", activation: 0.8 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Kettlebell",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 3.5,
    cnc: 3,
    ssc: 0.8
  },
  {
    id: "db_exp_kb_bent_over_row",
    name: "Remo Inclinado con Kettlebell",
    description: "Remo bilateral o alterno con kettlebells.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Kettlebell",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 3,
    cnc: 2.5,
    ssc: 0.3
  },
  {
    id: "db_exp_kb_lateral_raise",
    name: "Elevaciones Laterales con Kettlebell",
    description: "Aislamiento deltoides lateral con kettlebell.",
    involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }],
    subMuscleGroup: "Deltoides Lateral",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Kettlebell",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_kb_front_raise",
    name: "Elevaciones Frontales con Kettlebell",
    description: "Deltoides anterior con kettlebell.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }],
    subMuscleGroup: "Deltoides Anterior",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Kettlebell",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 1.6,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_kb_around_the_world",
    name: "Around the World con Kettlebell",
    description: "Circunducci\xF3n del kettlebell alrededor del cuerpo.",
    involvedMuscles: [{ muscle: "Deltoides", role: "primary", activation: 0.9 }, { muscle: "Core", role: "stabilizer", activation: 0.6 }],
    subMuscleGroup: "Hombros",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Kettlebell",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 2.2,
    cnc: 2,
    ssc: 0
  },
  // ========== BANDAS ELÁSTICAS ==========
  {
    id: "db_exp_band_chest_press",
    name: "Press de Pecho con Banda",
    description: "Empuje contra resistencia el\xE1stica.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Pectoral",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Banda",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 2.2,
    cnc: 1.8,
    ssc: 0
  },
  {
    id: "db_exp_band_row",
    name: "Remo con Banda",
    description: "Tir\xF3n horizontal con banda anclada.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Romboides", role: "secondary", activation: 0.7 }],
    subMuscleGroup: "Dorsales",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Banda",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 2.2,
    cnc: 1.8,
    ssc: 0
  },
  {
    id: "db_exp_band_bicep_curl",
    name: "Curl de B\xEDceps con Banda",
    description: "Curl con resistencia el\xE1stica.",
    involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "B\xEDceps",
    category: "Resistencia",
    type: "Aislamiento",
    equipment: "Banda",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.5,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_band_tricep_pushdown",
    name: "Pushdown de Tr\xEDceps con Banda",
    description: "Extensi\xF3n de codos contra banda.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Resistencia",
    type: "Aislamiento",
    equipment: "Banda",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.5,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_band_squat",
    name: "Sentadilla con Banda",
    description: "Resistencia adicional en sentadilla con banda por encima de rodillas.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "primary", activation: 0.8 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "B\xE1sico",
    equipment: "Banda",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 3,
    cnc: 2.5,
    ssc: 0.4
  },
  {
    id: "db_exp_band_glute_bridge",
    name: "Puente de Gl\xFAteos con Banda",
    description: "Puente con banda por encima de rodillas para gl\xFAteo medio.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Banda",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 3,
    efc: 2,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_band_face_pull",
    name: "Face Pull con Banda",
    description: "Tir\xF3n a la cara con resistencia el\xE1stica.",
    involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }, { muscle: "Romboides", role: "primary", activation: 0.8 }],
    subMuscleGroup: "Deltoides Posterior",
    category: "Movilidad",
    type: "Aislamiento",
    equipment: "Banda",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 1.5,
    cnc: 1.2,
    ssc: 0
  },
  {
    id: "db_exp_band_monster_walk",
    name: "Monster Walk con Banda",
    description: "Caminata lateral en cuclillas con banda en piernas.",
    involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }, { muscle: "Aductores", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Banda",
    force: "Otro",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 3,
    efc: 2,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_exp_band_leg_curl",
    name: "Curl Femoral con Banda",
    description: "Curl de isquios con banda anclada.",
    involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }],
    subMuscleGroup: "Isquiosurales",
    category: "Resistencia",
    type: "Aislamiento",
    equipment: "Banda",
    force: "Flexi\xF3n",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 2.2,
    cnc: 1.8,
    ssc: 0
  },
  {
    id: "db_exp_band_hip_thrust",
    name: "Hip Thrust con Banda",
    description: "Extensi\xF3n de cadera con resistencia el\xE1stica.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Banda",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 4,
    efc: 2.5,
    cnc: 2,
    ssc: 0
  },
  // ========== LEVANTAMIENTO OLÍMPICO Y POWERLIFTING ==========
  {
    id: "db_exp_power_clean",
    name: "Power Clean",
    description: "Cargada de potencia hasta los hombros.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }, { muscle: "Cu\xE1driceps", role: "primary", activation: 0.9 }, { muscle: "Trapecio", role: "primary", activation: 0.8 }],
    subMuscleGroup: "Cuerpo Completo",
    category: "Potencia",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "full",
    chain: "full",
    setupTime: 4,
    technicalDifficulty: 9,
    efc: 5,
    cnc: 5,
    ssc: 1
  },
  {
    id: "db_exp_clean_and_jerk",
    name: "Cargada y Envi\xF3n",
    description: "Levantamiento ol\xEDmpico completo.",
    involvedMuscles: [{ muscle: "Cuerpo Completo", role: "primary", activation: 1 }],
    subMuscleGroup: "Cuerpo Completo",
    category: "Potencia",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "full",
    chain: "full",
    setupTime: 5,
    technicalDifficulty: 10,
    efc: 5,
    cnc: 5,
    ssc: 1.2
  },
  {
    id: "db_exp_snatch",
    name: "Arrancada (Snatch)",
    description: "Levantamiento ol\xEDmpico en un tiempo.",
    involvedMuscles: [{ muscle: "Cuerpo Completo", role: "primary", activation: 1 }],
    subMuscleGroup: "Cuerpo Completo",
    category: "Potencia",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "full",
    chain: "full",
    setupTime: 5,
    technicalDifficulty: 10,
    efc: 5,
    cnc: 5,
    ssc: 1.2
  },
  {
    id: "db_exp_high_pull",
    name: "High Pull",
    description: "Tir\xF3n alto, variante del clean sin recibir la barra.",
    involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "primary", activation: 0.8 }],
    subMuscleGroup: "Cuerpo Completo",
    category: "Potencia",
    type: "Accesorio",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 7,
    efc: 4.2,
    cnc: 4,
    ssc: 0.8
  },
  {
    id: "db_exp_push_jerk",
    name: "Push Jerk",
    description: "Envi\xF3n con empuje de piernas.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Hombros",
    category: "Potencia",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 8,
    efc: 4.5,
    cnc: 4.5,
    ssc: 0.8
  },
  {
    id: "db_exp_squat_jerk",
    name: "Squat Jerk",
    description: "Recibir la barra en sentadilla durante el envi\xF3n.",
    involvedMuscles: [{ muscle: "Cuerpo Completo", role: "primary", activation: 1 }],
    subMuscleGroup: "Cuerpo Completo",
    category: "Potencia",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "full",
    chain: "full",
    setupTime: 5,
    technicalDifficulty: 10,
    efc: 5,
    cnc: 5,
    ssc: 1
  },
  {
    id: "db_exp_front_rack_position",
    name: "Posici\xF3n de Rack Frontal",
    description: "Hold isom\xE9trico con barra en rack frontal.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 0.8 }, { muscle: "Core", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Cuerpo Completo",
    category: "Estabilidad",
    type: "Accesorio",
    equipment: "Barra",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 6,
    efc: 3,
    cnc: 3,
    ssc: 0.8
  },
  {
    id: "db_exp_sots_press",
    name: "Sots Press",
    description: "Press desde sentadilla profunda.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Cu\xE1driceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Hombros",
    category: "Movilidad",
    type: "Accesorio",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 8,
    efc: 3.5,
    cnc: 3.5,
    ssc: 0.5
  },
  // ========== CALISTENIA Y PESO CORPORAL AVANZADO ==========
  {
    id: "db_exp_muscle_up",
    name: "Muscle-Up",
    description: "Transici\xF3n de dominada a fondo en anillas o barra.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Cuerpo Completo",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 9,
    efc: 4.5,
    cnc: 4.5,
    ssc: 0.2
  },
  {
    id: "db_exp_l_sit",
    name: "L-Sit",
    description: "Mantener cuerpo en L sostenido con manos en paralelas o suelo.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Flexores de Cadera", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Abdomen",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 7,
    efc: 3,
    cnc: 2.5,
    ssc: 0
  },
  {
    id: "db_exp_ring_row",
    name: "Remo en Anillas",
    description: "Remo invertido con anillas ajustables.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Dorsales",
    category: "Resistencia",
    type: "B\xE1sico",
    equipment: "Otro",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 2.8,
    cnc: 2.5,
    ssc: 0
  },
  {
    id: "db_exp_ring_dip",
    name: "Fondos en Anillas",
    description: "Mayor inestabilidad que fondos en paralelas.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Pectoral", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Otro",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 8,
    efc: 3.8,
    cnc: 3.5,
    ssc: 0.2
  },
  {
    id: "db_exp_ring_push_up",
    name: "Flexiones en Anillas",
    description: "Mayor inestabilidad que flexiones en suelo.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Pectoral",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Otro",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 6,
    efc: 3.2,
    cnc: 3,
    ssc: 0.1
  },
  {
    id: "db_exp_handstand_push_up",
    name: "Flexi\xF3n en Pino",
    description: "Press de hombros con peso corporal invertido.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Deltoides Anterior",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 9,
    efc: 4,
    cnc: 4,
    ssc: 0.3
  },
  {
    id: "db_exp_planche_lean",
    name: "Inclinaci\xF3n de Planche",
    description: "Progresi\xF3n hacia planche, inclinaci\xF3n con manos en suelo.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Core", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Cuerpo Completo",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 8,
    efc: 3.5,
    cnc: 3.5,
    ssc: 0.2
  },
  {
    id: "db_exp_front_lever",
    name: "Front Lever",
    description: "Mantener cuerpo horizontal bajo la barra.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Core", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 9,
    efc: 4,
    cnc: 4,
    ssc: 0.1
  },
  {
    id: "db_exp_back_lever",
    name: "Back Lever",
    description: "Mantener cuerpo horizontal boca abajo bajo la barra.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Core", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 9,
    efc: 4,
    cnc: 4,
    ssc: 0.2
  },
  {
    id: "db_exp_archer_pull_up",
    name: "Dominada Arquero",
    description: "Dominada con un brazo m\xE1s extendido que el otro.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 8,
    efc: 4,
    cnc: 3.8,
    ssc: 0.2
  },
  {
    id: "db_exp_commando_pull_up",
    name: "Dominada Comando",
    description: "Dominadas alternando a cada lado de la barra.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.7 }],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 7,
    efc: 3.8,
    cnc: 3.5,
    ssc: 0.2
  },
  {
    id: "db_exp_typewriter_pull_up",
    name: "Dominada Typewriter",
    description: "Movimiento lateral en la parte alta de la dominada.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 8,
    efc: 4,
    cnc: 3.8,
    ssc: 0.2
  },
  {
    id: "db_exp_assisted_pull_up",
    name: "Dominada Asistida",
    description: "Con banda o m\xE1quina de asistencia.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Dorsales",
    category: "Resistencia",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 2.8,
    cnc: 2.2,
    ssc: 0.1
  },
  {
    id: "db_exp_hanging_knee_raise",
    name: "Elevaciones de Rodillas Colgado",
    description: "Variante m\xE1s f\xE1cil que elevaciones de piernas.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Flexores de Cadera", role: "secondary", activation: 0.7 }],
    subMuscleGroup: "Abdomen",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 2,
    cnc: 1.8,
    ssc: 0.1,
    variantOf: "db_hanging_leg_raises"
  },
  {
    id: "db_exp_toes_to_bar",
    name: "Toes to Bar",
    description: "Llevar pies a la barra colgado.",
    involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Flexores de Cadera", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Abdomen",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 7,
    efc: 3,
    cnc: 2.5,
    ssc: 0.2
  },
  {
    id: "db_exp_wall_walk",
    name: "Caminata en Pared",
    description: "Flexiones progresando hacia el pino.",
    involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Core", role: "primary", activation: 0.8 }],
    subMuscleGroup: "Hombros",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 6,
    efc: 3,
    cnc: 2.8,
    ssc: 0.2
  },
  {
    id: "db_exp_plyo_push_up",
    name: "Flexi\xF3n Pliom\xE9trica",
    description: "Flexi\xF3n con despegue de manos del suelo.",
    involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "primary", activation: 0.9 }],
    subMuscleGroup: "Pectoral",
    category: "Potencia",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 6,
    efc: 3.2,
    cnc: 3,
    ssc: 0.1
  },
  {
    id: "db_exp_negative_pull_up",
    name: "Dominada Negativa",
    description: "Bajar lentamente desde la barra para ganar fuerza.",
    involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 3.2,
    cnc: 2.8,
    ssc: 0.1
  }
];

// data/exerciseDatabaseExpansion2.ts
var EXERCISE_EXPANSION_LIST_2 = [
  // ========== MÁQUINAS ADICIONALES ==========
  { id: "db_exp2_cable_chest_press", name: "Press de Pecho en Polea", description: "Empuje en polea con asas.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp2_hammer_strength_chest", name: "Press de Pecho Hammer Strength", description: "M\xE1quina de press convergente.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp2_cybex_chest_press", name: "Press de Pecho Cybex", description: "M\xE1quina selectorizada de pecho.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 3, cnc: 2, ssc: 0 },
  { id: "db_exp2_cable_incline_fly", name: "Aperturas Inclinadas en Polea", description: "Cruces desde polea baja.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp2_cable_decline_fly", name: "Aperturas Declinadas en Polea", description: "Cruces desde polea alta.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.4 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp2_assisted_dip_machine", name: "Fondos Asistidos (M\xE1quina)", description: "M\xE1quina de fondos con contrapeso.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Pectoral", role: "secondary", activation: 0.6 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }], subMuscleGroup: "Tr\xEDceps", category: "Resistencia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp2_tricep_dip_machine", name: "Fondos de Tr\xEDceps en M\xE1quina", description: "M\xE1quina de fondos para tr\xEDceps.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Pectoral", role: "secondary", activation: 0.5 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.4 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Accesorio", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp2_concentration_curl_machine", name: "Curl Concentrado en M\xE1quina", description: "Curl unilateral en m\xE1quina.", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }, { muscle: "Braquiorradial", role: "secondary", activation: 0.4 }, { muscle: "Antebrazo", role: "stabilizer", activation: 0.3 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp2_iso_lateral_row", name: "Remo Isolateral", description: "Remo con m\xE1quina de brazos independientes.", involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Romboides", role: "secondary", activation: 0.6 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }, { muscle: "Trapecio Medio", role: "stabilizer", activation: 0.4 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp2_low_cable_row", name: "Remo en Polea Baja (Barra)", description: "Remo horizontal con barra en polea baja.", involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Romboides", role: "secondary", activation: 0.7 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }, { muscle: "Trapecio Medio", role: "stabilizer", activation: 0.4 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp2_seal_row", name: "Remo Foca (Seal Row)", description: "Remo tumbado boca abajo en banco alto.", involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Romboides", role: "secondary", activation: 0.7 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }, { muscle: "Trapecio Medio", role: "stabilizer", activation: 0.4 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 4, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0 },
  { id: "db_exp2_stiff_arm_pulldown", name: "Jal\xF3n con Brazos R\xEDgidos", description: "Dorsal con brazos rectos en polea.", involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Core", role: "stabilizer", activation: 0.5 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp2_smith_machine_row", name: "Remo en Smith", description: "Remo con barra en Smith.", involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Romboides", role: "secondary", activation: 0.6 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp2_machine_shoulder_press", name: "Press Militar en M\xE1quina", description: "Press vertical en m\xE1quina selectorizada.", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.7 }, { muscle: "Deltoides Lateral", role: "secondary", activation: 0.5 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp2_arnold_press_machine", name: "Press Arnold en M\xE1quina", description: "Variante en m\xE1quina con rotaci\xF3n.", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }, { muscle: "Deltoides Lateral", role: "secondary", activation: 0.6 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.5 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "Accesorio", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp2_lateral_raise_machine", name: "Elevaciones Laterales en M\xE1quina", description: "M\xE1quina de elevaciones laterales.", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }, { muscle: "Trapecio", role: "stabilizer", activation: 0.4 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp2_vertical_leg_press", name: "Prensa Vertical de Piernas", description: "Prensa con plataforma vertical.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }, { muscle: "Isquiosurales", role: "stabilizer", activation: 0.3 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3.2, cnc: 2.5, ssc: 0.3 },
  { id: "db_exp2_v_squat", name: "Sentadilla V (M\xE1quina)", description: "M\xE1quina de sentadilla en V.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }, { muscle: "Isquiosurales", role: "stabilizer", activation: 0.3 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp2_leg_press_calf", name: "Gemelos en Prensa de Piernas", description: "Extensiones de tobillo en prensa.", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }, { muscle: "S\xF3leo", role: "secondary", activation: 0.8 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0.5 },
  { id: "db_exp2_smith_squat", name: "Sentadilla en Smith", description: "Sentadilla con barra guiada.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }, { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.4 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.8 },
  { id: "db_exp2_smith_deadlift", name: "Peso Muerto en Smith", description: "Deadlift con barra guiada.", involvedMuscles: [{ muscle: "Erectores Espinales", role: "primary", activation: 1 }, { muscle: "Isquiosurales", role: "primary", activation: 0.9 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }, { muscle: "Dorsal Ancho", role: "stabilizer", activation: 0.5 }], subMuscleGroup: "Erectores Espinales", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Bisagra", bodyPart: "full", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 3.8, cnc: 3, ssc: 1.2 },
  { id: "db_exp2_smith_rdl", name: "RDL en Smith", description: "Peso muerto rumano en Smith.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 }, { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.5 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Accesorio", equipment: "M\xE1quina", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 1 },
  { id: "db_exp2_glute_hammer", name: "Hip Thrust en M\xE1quina Hammer", description: "Extensi\xF3n de cadera en m\xE1quina.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }, { muscle: "Isquiosurales", role: "secondary", activation: 0.5 }, { muscle: "Core", role: "stabilizer", activation: 0.4 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Accesorio", equipment: "M\xE1quina", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp2_good_morning_machine", name: "Good Morning en M\xE1quina", description: "Bisagra de cadera en m\xE1quina.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 }, { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }], subMuscleGroup: "Isquiosurales", category: "Fuerza", type: "Accesorio", equipment: "M\xE1quina", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 3.5, cnc: 2.8, ssc: 0.8 },
  { id: "db_exp2_calf_raise_leg_press", name: "Gemelos en Prensa (Punta de Pie)", description: "Solo extensi\xF3n de tobillo en prensa.", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }, { muscle: "S\xF3leo", role: "secondary", activation: 0.8 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp2_ab_crunch_machine", name: "Crunch en M\xE1quina Abdominal", description: "M\xE1quina de crunch con peso.", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }, { muscle: "Oblicuos", role: "secondary", activation: 0.5 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp2_torso_rotation", name: "Rotaci\xF3n de Torso en M\xE1quina", description: "M\xE1quina de rotaci\xF3n para oblicuos.", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }, { muscle: "Transverso Abdominal", role: "secondary", activation: 0.6 }, { muscle: "Recto Abdominal", role: "stabilizer", activation: 0.4 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Rotaci\xF3n", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  // ========== MOVILIDAD Y ESTIRAMIENTO ==========
  { id: "db_exp2_world_greatest_stretch", name: "El Mejor Estiramiento del Mundo", description: "Estiramiento din\xE1mico de cuerpo completo.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 0.5 }, { muscle: "Isquiosurales", role: "primary", activation: 0.5 }], subMuscleGroup: "Piernas", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 4, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp2_hip_rotations", name: "Rotaciones de Cadera", description: "Movilidad de cadera en c\xEDrculos.", involvedMuscles: [{ muscle: "Cadera", role: "primary", activation: 0.5 }], subMuscleGroup: "Piernas", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Rotaci\xF3n", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp2_shoulder_dislocates", name: "Deslocaciones de Hombro (Banda)", description: "Pasar banda o palo por detr\xE1s del cuerpo.", involvedMuscles: [{ muscle: "Hombros", role: "primary", activation: 0.5 }], subMuscleGroup: "Hombros", category: "Movilidad", type: "Accesorio", equipment: "Banda", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp2_hip_hinge_drill", name: "Ejercicio de Bisagra de Cadera", description: "Aprendizaje del patr\xF3n de bisagra.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 0.5 }], subMuscleGroup: "Piernas", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 4, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp2_cat_cow", name: "Postura Gato-Vaca", description: "Movilidad de columna en cuadrupedia.", involvedMuscles: [{ muscle: "Espalda", role: "primary", activation: 0.5 }], subMuscleGroup: "Espalda", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp2_90_90_hip_switch", name: "Cambio de Cadera 90/90", description: "Estiramiento de rotadores de cadera.", involvedMuscles: [{ muscle: "Cadera", role: "primary", activation: 0.5 }], subMuscleGroup: "Piernas", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Rotaci\xF3n", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 4, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp2_ankle_mobility", name: "Movilidad de Tobillo", description: "Ejercicios de movilidad de tobillo.", involvedMuscles: [{ muscle: "Tobillo", role: "primary", activation: 0.5 }], subMuscleGroup: "Pantorrillas", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp2_wrist_mobility", name: "Movilidad de Mu\xF1eca", description: "Ejercicios de movilidad de mu\xF1eca.", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 0.5 }], subMuscleGroup: "Antebrazo", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp2_thread_the_needle", name: "Thread the Needle", description: "Rotaci\xF3n de columna en cuadrupedia.", involvedMuscles: [{ muscle: "Columna", role: "primary", activation: 0.5 }], subMuscleGroup: "Espalda", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Rotaci\xF3n", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 3, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp2_quad_stretch_kneeling", name: "Estiramiento de Cu\xE1driceps Arrodillado", description: "Estiramiento est\xE1tico de cu\xE1driceps.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 0.5 }], subMuscleGroup: "Cu\xE1driceps", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  // ========== PLIOMETRÍA Y SALTO ==========
  { id: "db_exp2_broad_jump", name: "Salto Horizontal", description: "Salto hacia adelante para potencia.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Pliometr\xEDa", type: "Accesorio", equipment: "Peso Corporal", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 4, efc: 3, cnc: 3.5, ssc: 0.2 },
  { id: "db_exp2_squat_jump", name: "Salto en Sentadilla", description: "Salto vertical desde sentadilla.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Pliometr\xEDa", type: "Accesorio", equipment: "Peso Corporal", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 4, efc: 3, cnc: 3.5, ssc: 0.2 },
  { id: "db_exp2_single_leg_hop", name: "Salto a Una Pierna", description: "Saltos unilaterales para equilibrio.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Pliometr\xEDa", type: "Accesorio", equipment: "Peso Corporal", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 5, efc: 3, cnc: 3, ssc: 0.2 },
  { id: "db_exp2_depth_jump", name: "Salto en Profundidad", description: "Salto desde caj\xF3n y rebote.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Pliometr\xEDa", type: "Accesorio", equipment: "Otro", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 2, technicalDifficulty: 6, efc: 3.5, cnc: 4, ssc: 0.3 },
  { id: "db_exp2_tuck_jump", name: "Salto con Rodillas al Pecho", description: "Salto recogiendo rodillas.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Pliometr\xEDa", type: "Accesorio", equipment: "Peso Corporal", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 5, efc: 3.2, cnc: 3.5, ssc: 0.2 },
  { id: "db_exp2_lateral_bound", name: "Salto Lateral", description: "Saltos laterales explosivos.", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Pliometr\xEDa", type: "Accesorio", equipment: "Peso Corporal", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 5, efc: 3, cnc: 3, ssc: 0.1 },
  { id: "db_exp2_skater_jump", name: "Salto de Patinador", description: "Saltos laterales estilo patinaje.", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Pliometr\xEDa", type: "Accesorio", equipment: "Peso Corporal", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 5, efc: 3, cnc: 3, ssc: 0.1 },
  { id: "db_exp2_vertical_jump", name: "Salto Vertical", description: "Salto m\xE1ximo hacia arriba.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Pliometr\xEDa", type: "Accesorio", equipment: "Peso Corporal", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 3, efc: 3, cnc: 3.5, ssc: 0.2 },
  { id: "db_exp2_reactive_box_jump", name: "Salto Reactivo al Caj\xF3n", description: "Salto al caj\xF3n con m\xEDnima pausa abajo.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Pliometr\xEDa", type: "Accesorio", equipment: "Otro", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 2, technicalDifficulty: 5, efc: 3.2, cnc: 3.5, ssc: 0.2 },
  { id: "db_exp2_medicine_ball_throw", name: "Lanzamiento de Bal\xF3n Medicinal", description: "Lanzar bal\xF3n contra pared o al suelo.", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Potencia", type: "Accesorio", equipment: "Otro", force: "Empuje", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 4, efc: 2.5, cnc: 2.5, ssc: 0.1 },
  // ========== STRONGMAN ==========
  { id: "db_exp2_atlas_stone", name: "Piedra de Atlas", description: "Cargar piedra hasta altura de hombros.", involvedMuscles: [{ muscle: "Cuerpo Completo", role: "primary", activation: 1 }], subMuscleGroup: "Cuerpo Completo", category: "Fuerza", type: "B\xE1sico", equipment: "Otro", force: "Bisagra", bodyPart: "full", chain: "full", setupTime: 5, technicalDifficulty: 8, efc: 5, cnc: 5, ssc: 1.5 },
  { id: "db_exp2_log_press", name: "Press con Log", description: "Press overhead con log de strongman.", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Hombros", category: "Fuerza", type: "B\xE1sico", equipment: "Otro", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 4, technicalDifficulty: 7, efc: 4, cnc: 4, ssc: 0.5 },
  { id: "db_exp2_yoke_walk", name: "Caminata con Yoke", description: "Cargar yoke sobre espalda y caminar.", involvedMuscles: [{ muscle: "Erectores Espinales", role: "primary", activation: 1 }], subMuscleGroup: "Erectores Espinales", category: "Fuerza", type: "Accesorio", equipment: "Otro", force: "Otro", bodyPart: "full", chain: "posterior", setupTime: 5, technicalDifficulty: 7, efc: 4.5, cnc: 4.5, ssc: 1.5 },
  { id: "db_exp2_sandbag_carry", name: "Paseo con Saco de Arena", description: "Cargar saco y caminar.", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Cuerpo Completo", category: "Fuerza", type: "Accesorio", equipment: "Otro", force: "Anti-Flexi\xF3n", bodyPart: "full", chain: "full", setupTime: 3, technicalDifficulty: 5, efc: 3.5, cnc: 3.5, ssc: 0.8 },
  { id: "db_exp2_tire_flip", name: "Volteo de Neum\xE1tico", description: "Voltear neum\xE1tico pesado.", involvedMuscles: [{ muscle: "Cuerpo Completo", role: "primary", activation: 1 }], subMuscleGroup: "Cuerpo Completo", category: "Fuerza", type: "B\xE1sico", equipment: "Otro", force: "Empuje", bodyPart: "full", chain: "full", setupTime: 3, technicalDifficulty: 6, efc: 4.5, cnc: 4.5, ssc: 1 },
  { id: "db_exp2_keg_carry", name: "Paseo con Barril", description: "Cargar barril sobre hombro y caminar.", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Cuerpo Completo", category: "Fuerza", type: "Accesorio", equipment: "Otro", force: "Otro", bodyPart: "full", chain: "full", setupTime: 3, technicalDifficulty: 5, efc: 3.5, cnc: 3.5, ssc: 0.5 },
  { id: "db_exp2_farmers_walk", name: "Paseo del Granjero", description: "Sujetar pesas y caminar.", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Fuerza", type: "Accesorio", equipment: "Mancuerna", force: "Otro", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.5 },
  { id: "db_exp2_h\xFAsafell_carry", name: "Paseo H\xFAsafell", description: "Cargar piedra plana sobre pecho y caminar.", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Cuerpo Completo", category: "Fuerza", type: "Accesorio", equipment: "Otro", force: "Anti-Flexi\xF3n", bodyPart: "full", chain: "full", setupTime: 4, technicalDifficulty: 7, efc: 4, cnc: 4, ssc: 0.8 },
  { id: "db_exp2_axle_deadlift", name: "Peso Muerto con Eje", description: "Deadlift con barra de mayor grosor.", involvedMuscles: [{ muscle: "Erectores Espinales", role: "primary", activation: 1 }], subMuscleGroup: "Erectores Espinales", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "full", chain: "posterior", setupTime: 4, technicalDifficulty: 7, efc: 5, cnc: 5, ssc: 2 },
  { id: "db_exp2_conan_wheel", name: "Rueda de Conan", description: "Caminar con rueda de strongman.", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Fuerza", type: "Accesorio", equipment: "Otro", force: "Anti-Rotaci\xF3n", bodyPart: "full", chain: "full", setupTime: 4, technicalDifficulty: 8, efc: 4, cnc: 4, ssc: 0.5 },
  // ========== TRX / SUSPENSIÓN ==========
  { id: "db_exp2_trx_row", name: "Remo en TRX", description: "Remo invertido con TRX.", involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }, { muscle: "Core", role: "stabilizer", activation: 0.5 }], subMuscleGroup: "Dorsales", category: "Resistencia", type: "B\xE1sico", equipment: "Otro", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp2_trx_push_up", name: "Flexiones en TRX", description: "Flexiones con pies en TRX.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }, { muscle: "Abdomen", role: "stabilizer", activation: 0.6 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "B\xE1sico", equipment: "Otro", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 6, efc: 3, cnc: 2.8, ssc: 0 },
  { id: "db_exp2_trx_chest_fly", name: "Aperturas en TRX", description: "Aperturas de pecho con TRX.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }, { muscle: "Deltoides Anterior", role: "secondary", activation: 0.4 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "Aislamiento", equipment: "Otro", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 5, efc: 2.2, cnc: 2, ssc: 0 },
  { id: "db_exp2_trx_face_pull", name: "Face Pull en TRX", description: "Tir\xF3n a la cara con TRX.", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }, { muscle: "Romboides", role: "secondary", activation: 0.7 }, { muscle: "Trapecio Medio", role: "secondary", activation: 0.5 }], subMuscleGroup: "Deltoides Posterior", category: "Resistencia", type: "Aislamiento", equipment: "Otro", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp2_trx_pike", name: "Pike en TRX", description: "Flexi\xF3n pike con pies en TRX.", involvedMuscles: [{ muscle: "Abdomen", role: "primary", activation: 1 }, { muscle: "Flexores de Cadera", role: "secondary", activation: 0.6 }, { muscle: "Tr\xEDceps", role: "stabilizer", activation: 0.5 }], subMuscleGroup: "Abdomen", category: "Fuerza", type: "Accesorio", equipment: "Otro", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 7, efc: 3, cnc: 2.8, ssc: 0 },
  { id: "db_exp2_trx_squat", name: "Sentadilla en TRX", description: "Sentadilla asisti\xE9ndose con TRX.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }, { muscle: "Core", role: "stabilizer", activation: 0.4 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "B\xE1sico", equipment: "Otro", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0.2 },
  { id: "db_exp2_trx_lunge", name: "Zancada en TRX", description: "Zancada con pie trasero en TRX.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "primary", activation: 0.9 }, { muscle: "Isquiosurales", role: "secondary", activation: 0.5 }], subMuscleGroup: "Piernas", category: "Resistencia", type: "Accesorio", equipment: "Otro", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 6, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp2_trx_hamstring_curl", name: "Curl de Isquios en TRX", description: "Curl femoral con pies en TRX.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }, { muscle: "Gemelos", role: "secondary", activation: 0.4 }], subMuscleGroup: "Isquiosurales", category: "Resistencia", type: "Accesorio", equipment: "Otro", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 6, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp2_trx_y_raise", name: "Elevaciones en Y en TRX", description: "Elevaciones en Y con TRX.", involvedMuscles: [{ muscle: "Trapecio Inferior", role: "primary", activation: 1 }, { muscle: "Romboides", role: "secondary", activation: 0.6 }, { muscle: "Deltoides Posterior", role: "secondary", activation: 0.5 }], subMuscleGroup: "Trapecio", category: "Movilidad", type: "Aislamiento", equipment: "Otro", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp2_trx_plank", name: "Plancha en TRX", description: "Plancha con pies en TRX.", involvedMuscles: [{ muscle: "Transverso Abdominal", role: "primary", activation: 1 }, { muscle: "Recto Abdominal", role: "secondary", activation: 0.8 }, { muscle: "Oblicuos", role: "stabilizer", activation: 0.5 }], subMuscleGroup: "Abdomen", category: "Estabilidad", type: "Accesorio", equipment: "Otro", force: "Anti-Extensi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 6, efc: 2.2, cnc: 2, ssc: 0 },
  // ========== MÁS VARIANTES ESPECÍFICAS ==========
  { id: "db_exp2_barbell_curl_ez", name: "Curl con Barra EZ", description: "Curl de b\xEDceps con barra curva.", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }, { muscle: "Braquiorradial", role: "secondary", activation: 0.5 }, { muscle: "Antebrazo", role: "stabilizer", activation: 0.4 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp2_cable_curl_high", name: "Curl en Polea Alta", description: "Curl con polea alta, brazo extendido.", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }, { muscle: "Braquiorradial", role: "secondary", activation: 0.4 }, { muscle: "Antebrazo", role: "stabilizer", activation: 0.3 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp2_resistance_band_curl", name: "Curl con Banda de Resistencia", description: "Curl con banda el\xE1stica.", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }, { muscle: "Braquiorradial", role: "secondary", activation: 0.4 }], subMuscleGroup: "B\xEDceps", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp2_preacher_curl_ez", name: "Curl Scott con Barra EZ", description: "Curl en banco Scott con barra EZ.", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }, { muscle: "Braquiorradial", role: "secondary", activation: 0.4 }, { muscle: "Antebrazo", role: "stabilizer", activation: 0.3 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp2_incline_inner_curl", name: "Curl en Banco Inclinado (Interno)", description: "Curl con mancuernas en banco inclinado.", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }, { muscle: "Braquial", role: "secondary", activation: 0.5 }, { muscle: "Antebrazo", role: "stabilizer", activation: 0.3 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp2_v_bar_pushdown", name: "Pushdown con Barra V", description: "Extensi\xF3n de tr\xEDceps con barra V.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Antebrazo", role: "stabilizer", activation: 0.3 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp2_rope_pushdown", name: "Pushdown con Cuerda", description: "Extensi\xF3n de tr\xEDceps con cuerda.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Antebrazo", role: "stabilizer", activation: 0.3 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp2_lying_cable_extension", name: "Extensi\xF3n de Tr\xEDceps Tumbado en Polea", description: "Extensi\xF3n con polea baja tumbado.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }, { muscle: "Antebrazo", role: "stabilizer", activation: 0.3 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp2_one_arm_row_cable", name: "Remo Unilateral en Polea", description: "Remo a un brazo en polea baja.", involvedMuscles: [{ muscle: "Dorsal Ancho", role: "primary", activation: 1 }, { muscle: "Romboides", role: "secondary", activation: 0.6 }, { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }, { muscle: "Core", role: "stabilizer", activation: 0.4 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 }
];

// data/exerciseDatabaseExpansion3.ts
var EXERCISE_EXPANSION_LIST_3 = [
  // ========== MÁQUINAS - PECHO Y TREN SUPERIOR ==========
  { id: "db_exp3_chest_press_neutral_grip", name: "Press de Pecho Agarre Neutro (M\xE1quina)", description: "Empuje con asas en agarre neutro en m\xE1quina selectorizada.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_incline_chest_press_machine", name: "Press de Pecho Inclinado en M\xE1quina", description: "M\xE1quina de press inclinado para pectoral superior.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_decline_chest_press_machine", name: "Press de Pecho Declinado en M\xE1quina", description: "M\xE1quina de press declinado para pectoral inferior.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_converging_chest_press", name: "Press Convergente de Pecho", description: "M\xE1quina con asas que convergen al extender.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_pec_deck_high_angle", name: "Pec Deck \xC1ngulo Alto", description: "Aperturas en m\xE1quina con asas ajustadas en \xE1ngulo alto.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_pec_deck_low_angle", name: "Pec Deck \xC1ngulo Bajo", description: "Aperturas en m\xE1quina con asas en \xE1ngulo bajo.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_tricep_extension_machine", name: "Extensi\xF3n de Tr\xEDceps en M\xE1quina", description: "Extensi\xF3n de codos en m\xE1quina de tr\xEDceps.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_tricep_dip_assisted", name: "Fondos de Tr\xEDceps Asistidos", description: "Fondos con m\xE1quina de asistencia graduable.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Resistencia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_cable_chest_fly_floor", name: "Cruces en Polea desde el Suelo", description: "Aperturas con poleas bajas desde posici\xF3n baja.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_single_arm_cable_fly", name: "Cruce Unilateral en Polea", description: "Apertura a un brazo en polea para estiramiento.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.6, cnc: 1.2, ssc: 0 },
  // ========== MÁQUINAS - ESPALDA Y REMO ==========
  { id: "db_exp3_high_cable_row", name: "Remo en Polea Alta con Asas", description: "Remo horizontal con polea alta y asas.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_t_bar_row_narrow", name: "Remo T-Bar Agarres Cerrado", description: "Remo en barra T con agarre estrecho.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 3.2, cnc: 2.8, ssc: 0.3 },
  { id: "db_exp3_t_bar_row_wide", name: "Remo T-Bar Agarres Ancho", description: "Remo en barra T con agarre amplio.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 3.2, cnc: 2.8, ssc: 0.3 },
  { id: "db_exp3_pulldown_wide_pronated", name: "Jal\xF3n al Pecho Prono Ancho", description: "Jal\xF3n con agarre prono muy amplio.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_pulldown_narrow_supinated", name: "Jal\xF3n al Pecho Supino Cerrado", description: "Jal\xF3n con agarre supino y manos juntas.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_pulldown_rope", name: "Jal\xF3n con Cuerda", description: "Jal\xF3n al pecho usando cuerda para mayor rango.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_reverse_grip_pulldown", name: "Jal\xF3n Agarre Inverso", description: "Jal\xF3n con agarre supino para \xE9nfasis en b\xEDceps.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  // ========== MÁQUINAS - HOMBROS ==========
  { id: "db_exp3_shoulder_press_behind_neck", name: "Press Militar por Detr\xE1s (M\xE1quina)", description: "Press vertical por detr\xE1s de la nuca en m\xE1quina.", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_seated_lateral_raise", name: "Elevaciones Laterales Sentado (M\xE1quina)", description: "Elevaciones laterales en m\xE1quina sentado.", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_cable_lateral_raise", name: "Elevaciones Laterales en Polea", description: "Elevaciones laterales con polea baja.", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_cable_front_raise", name: "Elevaciones Frontales en Polea", description: "Elevaciones frontales con polea baja.", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_cable_rear_delt_fly", name: "P\xE1jaros en Polea", description: "Aperturas posteriores con polea para deltoides posterior.", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_bent_over_cable_lateral", name: "Elevaciones Laterales Inclinado en Polea", description: "Elevaciones laterales con torso inclinado en polea.", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_single_arm_cable_lateral", name: "Elevaci\xF3n Lateral Unilateral en Polea", description: "Elevaci\xF3n lateral a un brazo en polea baja.", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_face_pull_rope", name: "Face Pull con Cuerda", description: "Tir\xF3n a la cara con cuerda en polea alta.", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_face_pull_ez_bar", name: "Face Pull con Barra EZ", description: "Tir\xF3n a la cara con barra curva.", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_upright_row_cable", name: "Remo al Ment\xF3n en Polea", description: "Remo vertical en polea baja.", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0 },
  // ========== BÍCEPS Y ANTEBRAZO ==========
  { id: "db_exp3_hammer_curl_cable", name: "Curl Martillo en Polea", description: "Curl neutro con polea baja.", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_lying_cable_curl", name: "Curl Tumbado en Polea", description: "Curl en banco con polea baja.", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_reverse_curl", name: "Curl Inverso", description: "Curl con agarre prono para braquiorradial.", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_wrist_curl", name: "Curl de Mu\xF1eca", description: "Flexi\xF3n de mu\xF1eca con barra o mancuerna.", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Flexi\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_reverse_wrist_curl", name: "Curl Inverso de Mu\xF1eca", description: "Extensi\xF3n de mu\xF1eca para antebrazo.", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Extensi\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_pinwheel_curl", name: "Curl Pinwheel", description: "Curl con mancuerna en agarre tipo pinza.", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_zottman_curl", name: "Curl Zottman", description: "Curl con rotaci\xF3n de mu\xF1eca en la fase exc\xE9ntrica.", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 5, efc: 1.8, cnc: 1.5, ssc: 0 },
  // ========== TRÍCEPS VARIANTES ==========
  { id: "db_exp3_skull_crusher_ez", name: "Skull Crusher con Barra EZ", description: "Extensi\xF3n de tr\xEDceps tumbado con barra EZ.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_overhead_cable_extension", name: "Extensi\xF3n de Tr\xEDceps por Encima en Polea", description: "Extensi\xF3n por encima de la cabeza con polea.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_single_arm_overhead_extension", name: "Extensi\xF3n de Tr\xEDceps Unilateral por Encima", description: "Extensi\xF3n a un brazo por encima de la cabeza.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_close_grip_pushdown", name: "Pushdown Agarres Cerrado", description: "Extensi\xF3n de tr\xEDceps con manos muy juntas.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_reverse_grip_pushdown", name: "Pushdown Agarre Inverso", description: "Pushdown con agarre supino para cabeza larga.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_JM_press", name: "JM Press", description: "H\xEDbrido de press y extensi\xF3n para tr\xEDceps.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Fuerza", type: "Accesorio", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 4, technicalDifficulty: 7, efc: 3.2, cnc: 3, ssc: 0.2 },
  { id: "db_exp3_floor_press_close", name: "Press en Suelo Agarres Cerrado", description: "Press en suelo con agarre estrecho para tr\xEDceps.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Fuerza", type: "Accesorio", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 6, efc: 3, cnc: 2.8, ssc: 0.2 },
  { id: "db_exp3_band_tricep_overhead", name: "Extensi\xF3n de Tr\xEDceps con Banda por Encima", description: "Extensi\xF3n por encima con banda el\xE1stica.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_kickback_cable", name: "Patada de Tr\xEDceps en Polea", description: "Kickback con polea baja.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_diamond_push_up_decline", name: "Flexiones Diamante Declinadas", description: "Flexiones diamante con pies elevados.", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 6, efc: 3, cnc: 2.5, ssc: 0.1 },
  // ========== MÁQUINAS - PIERNAS ==========
  { id: "db_exp3_leg_extension_unilateral", name: "Extensi\xF3n de Cu\xE1driceps Unilateral", description: "Extensi\xF3n de pierna a una pierna en m\xE1quina.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_leg_curl_lying", name: "Curl Femoral Tumbado", description: "Curl de isquios en m\xE1quina tumbado.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_leg_curl_seated", name: "Curl Femoral Sentado", description: "Curl de isquios en m\xE1quina sentado.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_leg_curl_standing", name: "Curl Femoral de Pie", description: "Curl de isquios unilateral de pie.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_hack_squat", name: "Sentadilla Hack", description: "Sentadilla en m\xE1quina hack con espalda apoyada.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_hack_squat_reverse", name: "Sentadilla Hack Inversa", description: "Hack squat de espaldas a la m\xE1quina.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_belt_squat", name: "Sentadilla con Cintur\xF3n", description: "Sentadilla con peso en cintur\xF3n, sin carga axial.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 4, efc: 3.5, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_sissy_squat", name: "Sissy Squat", description: "Inclinaci\xF3n hacia atr\xE1s manteniendo rodillas fijas.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 6, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_calf_raise_seated", name: "Elevaci\xF3n de Gemelos Sentado", description: "Gemelos en m\xE1quina sentado para s\xF3leo.", involvedMuscles: [{ muscle: "S\xF3leo", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp3_calf_raise_standing", name: "Elevaci\xF3n de Gemelos de Pie (M\xE1quina)", description: "Gemelos en m\xE1quina de pie.", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0.5 },
  // ========== GLÚTEOS Y CADERA ==========
  { id: "db_exp3_hip_abduction_machine", name: "Abducci\xF3n de Cadera en M\xE1quina", description: "Separar piernas contra resistencia en m\xE1quina.", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_hip_adduction_machine", name: "Aducci\xF3n de Cadera en M\xE1quina", description: "Cerrar piernas contra resistencia en m\xE1quina.", involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }], subMuscleGroup: "Aductores", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_glute_kickback_machine", name: "Patada de Gl\xFAteo en M\xE1quina", description: "Extensi\xF3n de cadera en m\xE1quina de gl\xFAteos.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_cable_hip_abduction", name: "Abducci\xF3n de Cadera en Polea", description: "Abducci\xF3n con polea baja y tobillera.", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cable_hip_adduction", name: "Aducci\xF3n de Cadera en Polea", description: "Aducci\xF3n con polea y tobillera.", involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }], subMuscleGroup: "Aductores", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cable_kickback", name: "Patada de Gl\xFAteo en Polea", description: "Extensi\xF3n de cadera con polea baja.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_frog_pump", name: "Frog Pump", description: "Bombeo de gl\xFAteos con plantas de pies juntas.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_single_leg_hip_thrust", name: "Hip Thrust Unilateral", description: "Hip thrust a una pierna.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Accesorio", equipment: "Peso Corporal", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 5, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_banded_hip_thrust", name: "Hip Thrust con Banda en Rodillas", description: "Hip thrust con banda de resistencia en rodillas.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Accesorio", equipment: "Banda", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_sumo_squat_hold", name: "Sentadilla Sumo Isom\xE9trica", description: "Mantener posici\xF3n sumo baja sin movimiento.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Estabilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  // ========== PESO CORPORAL - VARIANTES ==========
  { id: "db_exp3_archer_push_up", name: "Flexiones Arquero", description: "Flexiones con un brazo extendido lateralmente.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 7, efc: 3.2, cnc: 3, ssc: 0.1 },
  { id: "db_exp3_hindu_push_up", name: "Flexiones Hind\xFAes", description: "Flexiones con movimiento fluido de cadera.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 5, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_spiderman_push_up", name: "Flexiones Spiderman", description: "Flexiones llevando rodilla al codo.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 5, efc: 2.8, cnc: 2.2, ssc: 0.1 },
  { id: "db_exp3_deficit_push_up", name: "Flexiones con D\xE9ficit", description: "Flexiones con manos elevadas para mayor rango.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 5, efc: 3, cnc: 2.5, ssc: 0.1 },
  { id: "db_exp3_pseudo_planche_push_up", name: "Flexiones Pseudo Planche", description: "Flexiones con manos atr\xE1s y cuerpo inclinado.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Calistenia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 8, efc: 3.5, cnc: 3.5, ssc: 0.1 },
  { id: "db_exp3_australian_pull_up", name: "Dominada Australiana", description: "Remo invertido con barra baja.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_archer_pull_up", name: "Dominada Arquero", description: "Dominada con un brazo m\xE1s extendido.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Calistenia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 8, efc: 4, cnc: 4, ssc: 0.2 },
  { id: "db_exp3_typewriter_pull_up", name: "Dominada Typewriter", description: "Dominada moviendo cabeza lateralmente en la barra.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Calistenia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 8, efc: 4, cnc: 4, ssc: 0.2 },
  { id: "db_exp3_commando_pull_up", name: "Dominada Comando", description: "Dominada alternando manos de un lado a otro.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Calistenia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 7, efc: 3.8, cnc: 3.5, ssc: 0.2 },
  // ========== KETTLEBELL ==========
  { id: "db_exp3_kb_goblet_squat", name: "Sentadilla Copa con Kettlebell", description: "Sentadilla sujetando kettlebell al pecho.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Kettlebell", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_kb_swing", name: "Swing con Kettlebell", description: "Swing ruso de dos manos.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Resistencia", type: "B\xE1sico", equipment: "Kettlebell", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 5, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_kb_turkish_get_up", name: "Turkish Get-Up con Kettlebell", description: "Levantamiento turco completo.", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Cuerpo Completo", category: "Estabilidad", type: "B\xE1sico", equipment: "Kettlebell", force: "Otro", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 8, efc: 3.5, cnc: 3, ssc: 0 },
  { id: "db_exp3_kb_snatch", name: "Snatch con Kettlebell", description: "Arrancada unilateral con kettlebell.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Potencia", type: "B\xE1sico", equipment: "Kettlebell", force: "Empuje", bodyPart: "full", chain: "posterior", setupTime: 2, technicalDifficulty: 7, efc: 3.5, cnc: 3.5, ssc: 0 },
  { id: "db_exp3_kb_clean", name: "Clean con Kettlebell", description: "Cargada de kettlebell al hombro.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Potencia", type: "B\xE1sico", equipment: "Kettlebell", force: "Empuje", bodyPart: "full", chain: "posterior", setupTime: 2, technicalDifficulty: 6, efc: 3.2, cnc: 3, ssc: 0 },
  { id: "db_exp3_kb_suitcase_deadlift", name: "Peso Muerto Maleta con Kettlebell", description: "Deadlift unilateral con kettlebell.", involvedMuscles: [{ muscle: "Erectores Espinales", role: "primary", activation: 1 }], subMuscleGroup: "Erectores Espinales", category: "Fuerza", type: "Accesorio", equipment: "Kettlebell", force: "Bisagra", bodyPart: "full", chain: "posterior", setupTime: 2, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.5 },
  { id: "db_exp3_kb_sumo_deadlift", name: "Peso Muerto Sumo con Kettlebell", description: "Deadlift sumo con kettlebell entre piernas.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Fuerza", type: "B\xE1sico", equipment: "Kettlebell", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.3 },
  { id: "db_exp3_kb_rack_squat", name: "Sentadilla Rack con Kettlebell", description: "Sentadilla con kettlebell en rack.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Kettlebell", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_kb_deadlift_double", name: "Peso Muerto Doble Kettlebell", description: "Deadlift con dos kettlebells.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Fuerza", type: "B\xE1sico", equipment: "Kettlebell", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 3.2, cnc: 2.5, ssc: 0.5 },
  { id: "db_exp3_kb_floor_press", name: "Press en Suelo con Kettlebell", description: "Press en suelo con kettlebells.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Accesorio", equipment: "Kettlebell", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0 },
  // ========== MÁS KETTLEBELL Y BANDAS ==========
  { id: "db_exp3_kb_windmill", name: "Molino con Kettlebell", description: "Windmill para movilidad de cadera.", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Movilidad", type: "Accesorio", equipment: "Kettlebell", force: "Rotaci\xF3n", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 7, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_kb_bottoms_up_press", name: "Press Bottoms Up", description: "Press con kettlebell invertida.", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Estabilidad", type: "Accesorio", equipment: "Kettlebell", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 6, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_kb_single_arm_row", name: "Remo Unilateral con Kettlebell", description: "Remo a un brazo con kettlebell.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Kettlebell", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_band_pull_apart", name: "Pull Apart con Banda", description: "Separar banda a la altura del pecho.", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_band_woodchop", name: "Wood Chop con Banda", description: "Rotaci\xF3n diagonal con banda anclada.", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Rotaci\xF3n", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_band_deadlift", name: "Peso Muerto con Banda", description: "Deadlift con banda bajo los pies.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Banda", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.3 },
  { id: "db_exp3_band_shoulder_ext_rot", name: "Rotaci\xF3n Externa de Hombro con Banda", description: "Rotaci\xF3n externa para manguito rotador.", involvedMuscles: [{ muscle: "Manguito Rotador", role: "primary", activation: 1 }], subMuscleGroup: "Hombros", category: "Movilidad", type: "Aislamiento", equipment: "Banda", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_band_shoulder_int_rot", name: "Rotaci\xF3n Interna de Hombro con Banda", description: "Rotaci\xF3n interna con banda para manguito.", involvedMuscles: [{ muscle: "Manguito Rotador", role: "primary", activation: 1 }], subMuscleGroup: "Hombros", category: "Movilidad", type: "Aislamiento", equipment: "Banda", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1, cnc: 1, ssc: 0 },
  // ========== MOVILIDAD ==========
  { id: "db_exp3_mob_hip_flexor_stretch", name: "Estiramiento de Flexores de Cadera", description: "Estiramiento de psoas en posici\xF3n de zancada.", involvedMuscles: [{ muscle: "Psoas", role: "primary", activation: 0.5 }], subMuscleGroup: "Piernas", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_lizard_stretch", name: "Estiramiento Lagarto", description: "Estiramiento profundo de cadera y flexores.", involvedMuscles: [{ muscle: "Psoas", role: "primary", activation: 0.5 }], subMuscleGroup: "Piernas", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 5, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_pigeon_pose", name: "Postura de la Paloma", description: "Estiramiento de gl\xFAteos y rotadores.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 0.5 }], subMuscleGroup: "Gl\xFAteos", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 4, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_frog_stretch", name: "Estiramiento Rana", description: "Estiramiento de aductores en cuadrupedia.", involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 0.5 }], subMuscleGroup: "Aductores", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 5, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_hamstring_standing", name: "Estiramiento de Isquios de Pie", description: "Estiramiento de isquios de pie.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 0.5 }], subMuscleGroup: "Isquiosurales", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_quad_standing", name: "Estiramiento de Cu\xE1driceps de Pie", description: "Estiramiento de cu\xE1driceps de pie.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 0.5 }], subMuscleGroup: "Cu\xE1driceps", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_calf_wall", name: "Estiramiento de Gemelos en Pared", description: "Estiramiento de gemelos apoyado en pared.", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 0.5 }], subMuscleGroup: "Pantorrillas", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_pec_corner", name: "Estiramiento de Pectoral en Esquina", description: "Estiramiento de pectoral en esquina o marco.", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 0.5 }], subMuscleGroup: "Pectoral", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_lat_doorway", name: "Estiramiento de Dorsal en Marco", description: "Estiramiento de dorsal con brazo en marco.", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 0.5 }], subMuscleGroup: "Dorsales", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_spinal_twist", name: "Torsi\xF3n Espinal Supina", description: "Torsi\xF3n de columna tumbado boca arriba.", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 0.5 }], subMuscleGroup: "Abdomen", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Rotaci\xF3n", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_child_pose", name: "Postura del Ni\xF1o", description: "Estiramiento de espalda en posici\xF3n fetal.", involvedMuscles: [{ muscle: "Espalda", role: "primary", activation: 0.5 }], subMuscleGroup: "Espalda", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 1, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_downward_dog", name: "Perro Boca Abajo", description: "Estiramiento de cadena posterior.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 0.5 }], subMuscleGroup: "Piernas", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 3, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_upward_dog", name: "Perro Boca Arriba", description: "Extensi\xF3n de columna y apertura de pecho.", involvedMuscles: [{ muscle: "Erectores Espinales", role: "primary", activation: 0.5 }], subMuscleGroup: "Espalda", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 3, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_cobra", name: "Postura de la Cobra", description: "Extensi\xF3n de columna en dec\xFAbito prono.", involvedMuscles: [{ muscle: "Erectores Espinales", role: "primary", activation: 0.5 }], subMuscleGroup: "Espalda", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_puppy_stretch", name: "Estiramiento del Cachorro", description: "Estiramiento de hombros y espalda alta.", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 0.5 }], subMuscleGroup: "Hombros", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 3, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_sphinx", name: "Postura de la Esfinge", description: "Extensi\xF3n suave de columna en antebrazos.", involvedMuscles: [{ muscle: "Erectores Espinales", role: "primary", activation: 0.5 }], subMuscleGroup: "Espalda", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_seated_forward_fold", name: "Flexi\xF3n Sentada al Frente", description: "Estiramiento de isquios sentado.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 0.5 }], subMuscleGroup: "Isquiosurales", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_mob_figure_four_stretch", name: "Estiramiento Figura 4", description: "Estiramiento de gl\xFAteos y cadera.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 0.5 }], subMuscleGroup: "Gl\xFAteos", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 4, efc: 1, cnc: 1, ssc: 0 },
  // ========== REHABILITACIÓN ==========
  { id: "db_exp3_rehab_wall_slide", name: "Deslizamiento en Pared", description: "Deslizar brazos por pared para hombros.", involvedMuscles: [{ muscle: "Manguito Rotador", role: "primary", activation: 0.5 }], subMuscleGroup: "Hombros", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_rehab_ytw", name: "Y-T-W", description: "Secuencia Y, T y W para esc\xE1pula y hombros.", involvedMuscles: [{ muscle: "Trapecio Inferior", role: "primary", activation: 0.5 }], subMuscleGroup: "Trapecio", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 4, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_rehab_prone_extension", name: "Extensi\xF3n Prona", description: "Extensi\xF3n de brazos tumbado prono para lumbar.", involvedMuscles: [{ muscle: "Erectores Espinales", role: "primary", activation: 0.5 }], subMuscleGroup: "Espalda", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_rehab_bird_dog", name: "Bird Dog", description: "Extensi\xF3n contralateral de brazo y pierna.", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 0.7 }], subMuscleGroup: "Abdomen", category: "Estabilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Anti-Extensi\xF3n", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 3, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_rehab_dead_bug", name: "Dead Bug", description: "Extensi\xF3n contralateral boca arriba.", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 0.7 }], subMuscleGroup: "Abdomen", category: "Estabilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Anti-Extensi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_rehab_clam_shell", name: "Clam Shell", description: "Apertura de rodillas con piernas flexionadas.", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Movilidad", type: "Aislamiento", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_rehab_glute_bridge_hold", name: "Puente de Gl\xFAteos Isom\xE9trico", description: "Mantener puente de gl\xFAteos en posici\xF3n alta.", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Estabilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_rehab_ankle_circles", name: "C\xEDrculos de Tobillo", description: "Movilidad de tobillo en c\xEDrculos.", involvedMuscles: [{ muscle: "Tobillo", role: "primary", activation: 0.5 }], subMuscleGroup: "Pantorrillas", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 1, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_rehab_knee_flexion_slide", name: "Flexi\xF3n de Rodilla Deslizando", description: "Deslizar tal\xF3n para flexionar rodilla.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 0.5 }], subMuscleGroup: "Cu\xE1driceps", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_rehab_heel_slide", name: "Deslizamiento de Tal\xF3n", description: "Deslizar tal\xF3n en suelo para movilidad de rodilla.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 0.5 }], subMuscleGroup: "Cu\xE1driceps", category: "Movilidad", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  // ========== CARDIO Y CONDICIONAMIENTO ==========
  { id: "db_exp3_cardio_burpee", name: "Burpee", description: "Flexi\xF3n, salto y extensi\xF3n completa.", involvedMuscles: [{ muscle: "Cuerpo Completo", role: "primary", activation: 1 }], subMuscleGroup: "Cuerpo Completo", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 5, efc: 4, cnc: 4, ssc: 0.1 },
  { id: "db_exp3_cardio_mountain_climber", name: "Escalador", description: "Alternar rodillas al pecho en plancha.", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 3.5, cnc: 3, ssc: 0 },
  { id: "db_exp3_cardio_jumping_jack", name: "Jumping Jack", description: "Saltos con apertura de piernas y brazos.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Salto", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 2, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_cardio_high_knee", name: "Rodillas Altas", description: "Correr en sitio elevando rodillas.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_cardio_butt_kick", name: "Talon a Gl\xFAteo", description: "Correr en sitio tocando talones a gl\xFAteos.", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_cardio_skater", name: "Patinador en Sitio", description: "Saltos laterales estilo patinador.", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_cardio_switch_lunge", name: "Zancada con Salto", description: "Alternar zancada con salto.", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Piernas", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Salto", bodyPart: "lower", chain: "full", setupTime: 1, technicalDifficulty: 5, efc: 3.5, cnc: 3, ssc: 0.1 },
  { id: "db_exp3_cardio_squat_thrust", name: "Squat Thrust", description: "Transici\xF3n r\xE1pida de pie a plancha y vuelta.", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Cuerpo Completo", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 4, efc: 3.5, cnc: 3, ssc: 0 },
  { id: "db_exp3_cardio_sprawl", name: "Sprawl", description: "Burpee simplificado sin flexi\xF3n completa.", involvedMuscles: [{ muscle: "Cuerpo Completo", role: "primary", activation: 1 }], subMuscleGroup: "Cuerpo Completo", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Empuje", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 4, efc: 3.5, cnc: 3, ssc: 0 },
  { id: "db_exp3_cardio_shadow_boxing", name: "Shadow Boxing", description: "Boxeo al aire para condici\xF3n.", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Hombros", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0 },
  // ========== EXPANSIÓN BULK (generado) ==========
  { id: "db_exp3_bb_ez_bench", name: "Press Banca EZ", description: "Press horizontal barra EZ", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_bb_camber_incline", name: "Press Inclinado Camber", description: "Inclinado barra camber", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 3.2, cnc: 2.8, ssc: 0 },
  { id: "db_exp3_bb_decline_wide", name: "Press Declinado Ancho", description: "Declinado agarre amplio", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_bb_front_squat_high", name: "Sentadilla Frontal Alta", description: "Front squat barra alta", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 6, efc: 3.5, cnc: 3, ssc: 0.5 },
  { id: "db_exp3_bb_safety_squat", name: "Sentadilla Safety Bar", description: "Squat barra seguridad", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_bb_zercher_squat", name: "Sentadilla Zercher", description: "Squat barra en codos", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 5, technicalDifficulty: 7, efc: 3.5, cnc: 3, ssc: 0.3 },
  { id: "db_exp3_bb_snatch_deadlift", name: "Peso Muerto Snatch", description: "Deadlift agarre snatch", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 4, technicalDifficulty: 7, efc: 3.8, cnc: 3.2, ssc: 0.2 },
  { id: "db_exp3_bb_deficit_deadlift", name: "Peso Muerto Deficit", description: "Deadlift desde elevacion", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 4, technicalDifficulty: 6, efc: 3.5, cnc: 3, ssc: 0.2 },
  { id: "db_exp3_bb_rack_pull_high", name: "Rack Pull Alto", description: "Peso muerto soportes altos", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "Accesorio", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 3.5, cnc: 3, ssc: 0 },
  { id: "db_exp3_bb_rack_pull_low", name: "Rack Pull Bajo", description: "Peso muerto soportes bajos", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "Accesorio", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 3.5, cnc: 3, ssc: 0.2 },
  { id: "db_exp3_bb_rdl_stiff", name: "RDL Rigido", description: "RDL rodillas rigidas", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.3 },
  { id: "db_exp3_bb_bent_row_reverse", name: "Remo Inclinado Inverso", description: "Remo agarre supino", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 4, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_bb_upright_wide", name: "Remo Menton Ancho", description: "Remo vertical ancho", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_bb_upright_narrow", name: "Remo Menton Cerrado", description: "Remo vertical cerrado", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_bb_shrug_behind", name: "Encogimiento Atras", description: "Encogimiento barra atras", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bb_shrug_front", name: "Encogimiento Frente", description: "Encogimiento barra frente", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bb_ez_curl_wide", name: "Curl EZ Ancho", description: "Curl barra EZ ancho", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bb_ez_curl_close", name: "Curl EZ Cerrado", description: "Curl barra EZ cerrado", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bb_lying_tricep_ez", name: "Ext Triceps EZ Tumbado", description: "Skull crusher EZ", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_bb_floor_press_close", name: "Press Suelo Cerrado", description: "Press suelo agarre cerrado", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Fuerza", type: "Accesorio", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 6, efc: 3, cnc: 2.8, ssc: 0.2 },
  { id: "db_exp3_mq_chest_press_iso", name: "Press Pecho Isolateral", description: "Maquina press unilateral", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq_incline_iso", name: "Press Inclinado Isolateral", description: "Maquina inclinada unilateral", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq_pec_fly_iso", name: "Pec Deck Unilateral", description: "Aperturas maquina unilateral", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq_lat_pulldown_iso", name: "Jalon Unilateral", description: "Jalon maquina a un brazo", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq_seated_row_iso", name: "Remo Sentado Unilateral", description: "Remo maquina unilateral", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq_shoulder_press_iso", name: "Press Hombro Unilateral", description: "Maquina press hombro unilateral", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq_lateral_raise_iso", name: "Lateral Maquina Unilateral", description: "Elevacion lateral maquina", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq_rear_delt_iso", name: "Dorsal Maquina Unilateral", description: "Maquina deltoides posterior", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq_shrug_iso", name: "Encogimiento Maquina", description: "Encogimiento en maquina", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq_bicep_curl_iso", name: "Curl Maquina Unilateral", description: "Curl biceps maquina", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq_tricep_ext_iso", name: "Ext Triceps Maquina Unilateral", description: "Extension triceps maquina", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq_leg_press_45", name: "Prensa 45 Grados", description: "Prensa de piernas 45", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.3 },
  { id: "db_exp3_mq_leg_press_horizontal", name: "Prensa Horizontal", description: "Prensa piernas horizontal", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.3 },
  { id: "db_exp3_mq_leg_press_single", name: "Prensa Unilateral", description: "Prensa a una pierna", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Accesorio", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq_v_squat", name: "V-Squat", description: "Sentadilla en maquina V", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_mq_leg_ext_single", name: "Extension Cuadriceps Unilateral", description: "Extension pierna unilateral", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq_leg_curl_prone", name: "Curl Femoral Prono", description: "Curl isquios prono maquina", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq_leg_curl_sitting", name: "Curl Femoral Sentado Maquina", description: "Curl isquios sentado", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq_calf_standing", name: "Gemelos Maquina Pie", description: "Gemelos maquina de pie", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0.5 },
  { id: "db_exp3_mq_calf_seated", name: "Gemelos Maquina Sentado", description: "Gemelos maquina sentado", involvedMuscles: [{ muscle: "S\xF3leo", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp3_mq_hip_abductor", name: "Abductor Cadera Maquina", description: "Maquina abduccion cadera", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq_hip_adductor", name: "Aductor Cadera Maquina", description: "Maquina aduccion cadera", involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }], subMuscleGroup: "Aductores", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq_glute_press", name: "Maquina Gluteos", description: "Maquina extension gluteos", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_mq_smith_bench", name: "Press Banca Smith", description: "Press banca en Smith", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_mq_smith_incline", name: "Press Inclinado Smith", description: "Press inclinado Smith", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_mq_smith_squat", name: "Sentadilla Smith", description: "Squat en Smith", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_mq_smith_deadlift", name: "Peso Muerto Smith", description: "Deadlift en Smith", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "M\xE1quina", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.2 },
  { id: "db_exp3_mq_cable_row_seated", name: "Remo Polea Sentado", description: "Remo en polea sentado", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq_chest_fly_cable", name: "Cruces Polea Baja", description: "Cruces polea desde abajo", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cbl_fly_high", name: "Cruce Polea Alta", description: "Cruces polea alta", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cbl_fly_low", name: "Cruce Polea Baja", description: "Cruces polea baja", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cbl_row_vbar", name: "Remo Barra V Polea", description: "Remo con barra V", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_cbl_row_rope", name: "Remo Cuerda Polea", description: "Remo con cuerda", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_cbl_row_single", name: "Remo Unilateral Polea", description: "Remo a un brazo polea", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_cbl_lat_pulldown_v", name: "Jalon Barra V", description: "Jalon con barra V", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_cbl_lat_pulldown_ez", name: "Jalon Barra EZ", description: "Jalon barra curva", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_cbl_straight_arm_ez", name: "Jalon Brazos Rectos EZ", description: "Dorsal brazos rectos EZ", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_cbl_face_pull_v", name: "Face Pull Barra V", description: "Tiron cara barra V", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cbl_lateral_single", name: "Lateral Polea Unilateral", description: "Elevacion lateral polea", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_cbl_front_raise", name: "Frontal Polea", description: "Elevacion frontal polea", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_cbl_shrug", name: "Encogimiento Polea", description: "Encogimiento en polea", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cbl_curl_bar", name: "Curl Polea Barra", description: "Curl biceps barra polea", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cbl_curl_rope", name: "Curl Polea Cuerda", description: "Curl biceps cuerda", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cbl_tricep_vbar", name: "Pushdown Barra V", description: "Pushdown triceps V", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cbl_tricep_rope", name: "Pushdown Cuerda", description: "Pushdown triceps cuerda", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cbl_woodchop_high", name: "Wood Chop Alto", description: "Wood chop polea alta", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cbl_woodchop_low", name: "Wood Chop Bajo", description: "Wood chop polea baja", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_cbl_crunch", name: "Crunch Polea", description: "Crunch con polea alta", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_cbl_leg_curl", name: "Curl Femoral Polea", description: "Curl isquios polea", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bw_push_up_wide", name: "Flexiones Ancho", description: "Flexiones agarre ancho", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0.1 },
  { id: "db_exp3_bw_push_up_close", name: "Flexiones Cerrado", description: "Flexiones agarre cerrado", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0.1 },
  { id: "db_exp3_bw_push_up_sphinx", name: "Flexiones Esfinge", description: "Flexiones codos adelante", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 2.5, cnc: 2, ssc: 0.1 },
  { id: "db_exp3_bw_pike_push_up", name: "Flexiones Pike", description: "Flexiones pike hombros", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 5, efc: 2.8, cnc: 2.2, ssc: 0.1 },
  { id: "db_exp3_bw_decline_push_up", name: "Flexiones Declinadas", description: "Flexiones pies elevados", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.1 },
  { id: "db_exp3_bw_incline_push_up", name: "Flexiones Inclinadas", description: "Flexiones manos elevadas", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 2.5, cnc: 2, ssc: 0.1 },
  { id: "db_exp3_bw_pull_up_wide", name: "Dominadas Ancho", description: "Dominadas agarre ancho", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_bw_pull_up_neutral", name: "Dominadas Neutras", description: "Dominadas agarre neutro", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_bw_chin_up", name: "Dominadas Supinas", description: "Dominadas agarre supino", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_bw_l_sit", name: "L-Sit", description: "Mantener L en barras", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 6, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bw_hanging_leg_raise", name: "Elevaciones Piernas Colgado", description: "Piernas al frente colgado", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 5, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_bw_hanging_knee_raise", name: "Elevaciones Rodillas Colgado", description: "Rodillas al pecho colgado", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bw_plank", name: "Plancha", description: "Plancha frontal", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bw_side_plank", name: "Plancha Lateral", description: "Plancha lateral", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_bw_squat_bodyweight", name: "Sentadilla Peso Corporal", description: "Sentadilla sin peso", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 2.5, cnc: 2, ssc: 0.3 },
  { id: "db_exp3_bw_lunge_forward", name: "Zancada Frontal", description: "Zancada hacia adelante", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0.2 },
  { id: "db_exp3_bw_lunge_lateral", name: "Zancada Lateral", description: "Zancada hacia lateral", involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }], subMuscleGroup: "Aductores", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_bw_single_leg_squat", name: "Sentadilla Unipodal", description: "Sentadilla a una pierna", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 6, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_bw_glute_bridge", name: "Puente Gluteos", description: "Puente de gluteos suelo", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Accesorio", equipment: "Peso Corporal", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_band_chest_press", name: "Press Pecho Banda", description: "Press pecho con banda", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "B\xE1sico", equipment: "Banda", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_band_fly", name: "Cruces Banda", description: "Cruces pecho banda", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_band_row", name: "Remo Banda", description: "Remo con banda", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_band_lat_pulldown", name: "Jalon Banda", description: "Jalon con banda anclada", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Resistencia", type: "B\xE1sico", equipment: "Banda", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_band_shoulder_press", name: "Press Hombro Banda", description: "Press hombro con banda", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Resistencia", type: "B\xE1sico", equipment: "Banda", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_band_lateral", name: "Lateral Banda", description: "Elevaciones laterales banda", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_band_curl", name: "Curl Banda", description: "Curl biceps banda", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_band_tricep_pushdown", name: "Pushdown Banda", description: "Pushdown triceps banda", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_band_squat", name: "Sentadilla Banda", description: "Squat con banda rodillas", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "B\xE1sico", equipment: "Banda", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_band_glute_bridge", name: "Puente Banda", description: "Puente gluteos con banda", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Accesorio", equipment: "Banda", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_band_hip_thrust", name: "Hip Thrust Banda", description: "Hip thrust con banda", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Accesorio", equipment: "Banda", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_band_rdl", name: "RDL Banda", description: "RDL con banda", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0.2 },
  { id: "db_exp3_band_leg_curl", name: "Curl Femoral Banda", description: "Curl isquios banda", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_band_abduction", name: "Abduccion Banda", description: "Abduccion cadera banda", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_band_adduction", name: "Aduccion Banda", description: "Aduccion cadera banda", involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }], subMuscleGroup: "Aductores", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_band_pallof", name: "Pallof Press Banda", description: "Anti-rotacion banda", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_band_crunch", name: "Crunch Banda", description: "Crunch con banda", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Banda", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_band_upright_row", name: "Remo Menton Banda", description: "Remo vertical banda", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_band_good_morning", name: "Good Morning Banda", description: "Good morning banda", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_band_lunge", name: "Zancada Banda", description: "Zancada con banda", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0.2 },
  { id: "db_exp3_band_calf_raise", name: "Gemelos Banda", description: "Gemelos con banda", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp3_band_wrist_curl", name: "Curl Muneca Banda", description: "Flexion muneca banda", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Flexi\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_band_reverse_fly", name: "Lateral Inclinado Banda", description: "Laterales inclinado banda", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_band_chest_squeeze", name: "Aperturas Banda", description: "Aperturas pecho banda", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_band_twist", name: "Rotacion Banda", description: "Rotacion torso banda", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_band_superman", name: "Superman Banda", description: "Extension espalda banda", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_band_monster_walk", name: "Monster Walk", description: "Pasos laterales banda", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_band_clam", name: "Clam Shell Banda", description: "Clam shell banda", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_band_donkey_kick", name: "Patada Gluteo Banda", description: "Patada gluteo banda", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_band_front_raise", name: "Frontal Banda", description: "Elevacion frontal banda", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_band_shrug", name: "Encogimiento Banda", description: "Encogimiento banda", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_band_bicep_21", name: "Curl 21 Banda", description: "Curl 21 con banda", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Banda", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_band_overhead_ext", name: "Ext Triceps Banda", description: "Extension triceps banda", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Resistencia", type: "Aislamiento", equipment: "Banda", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_band_single_arm_row", name: "Remo Unilateral Banda", description: "Remo unilateral banda", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_band_crossover_punch", name: "Golpe Cruzado Banda", description: "Golpes cruzados banda", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Resistencia", type: "Accesorio", equipment: "Banda", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_kb_rack_carry", name: "Marcha Rack", description: "Caminar con KB en rack", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "Accesorio", equipment: "Kettlebell", force: "Otro", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_kb_halo", name: "Halo KB", description: "Circunduccion KB cabeza", involvedMuscles: [{ muscle: "Deltoides", role: "primary", activation: 1 }], subMuscleGroup: "Hombros", category: "Movilidad", type: "Accesorio", equipment: "Kettlebell", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_kb_figure_8", name: "Figure 8 KB", description: "Figura 8 entre piernas", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Kettlebell", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_kb_renegade_row", name: "Remo Renegado KB", description: "Plancha remo KB", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Resistencia", type: "Accesorio", equipment: "Kettlebell", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 6, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_kb_thruster", name: "Thruster KB", description: "Squat y press KB", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "B\xE1sico", equipment: "Kettlebell", force: "Empuje", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 5, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_kb_overhead_squat", name: "Squat Overhead KB", description: "Squat KB arriba", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Estabilidad", type: "B\xE1sico", equipment: "Kettlebell", force: "Sentadilla", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 7, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_kb_goblet_lunge", name: "Zancada Copa KB", description: "Zancada goblet KB", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Accesorio", equipment: "Kettlebell", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_kb_calf_raise", name: "Gemelos KB", description: "Gemelos con KB", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "Kettlebell", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp3_kb_press_single", name: "Press KB Unilateral", description: "Press hombro KB", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "B\xE1sico", equipment: "Kettlebell", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_kb_curl", name: "Curl KB", description: "Curl biceps KB", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Kettlebell", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_kb_tricep_ext", name: "Ext Triceps KB", description: "Extension triceps KB", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Kettlebell", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_kb_bent_row", name: "Remo Inclinado KB", description: "Remo inclinado KB", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Kettlebell", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_kb_front_raise", name: "Frontal KB", description: "Elevacion frontal KB", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Kettlebell", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_kb_lateral_raise", name: "Lateral KB", description: "Elevacion lateral KB", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Aislamiento", equipment: "Kettlebell", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_kb_shrug", name: "Encogimiento KB", description: "Encogimiento con KB", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Kettlebell", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_kb_rdl", name: "RDL KB", description: "RDL con KB", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Accesorio", equipment: "Kettlebell", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_kb_sumo_squat", name: "Sentadilla Sumo KB", description: "Sumo squat KB", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Kettlebell", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_kb_glute_bridge", name: "Puente KB", description: "Puente gluteos KB", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Accesorio", equipment: "Kettlebell", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_kb_high_pull", name: "High Pull KB", description: "Tiron alto KB", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Accesorio", equipment: "Kettlebell", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_kb_around_world", name: "Around World KB", description: "KB alrededor cuerpo", involvedMuscles: [{ muscle: "Deltoides", role: "primary", activation: 1 }], subMuscleGroup: "Hombros", category: "Resistencia", type: "Accesorio", equipment: "Kettlebell", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_kb_deadlift_single", name: "Peso Muerto KB Unilateral", description: "Deadlift KB una mano", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Resistencia", type: "Accesorio", equipment: "Kettlebell", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.5, cnc: 2, ssc: 0.2 },
  { id: "db_exp3_kb_goblet_hold", name: "Sostener Copa KB", description: "Mantener goblet isometrico", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Kettlebell", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_kb_swing_single", name: "Swing KB Unilateral", description: "Swing a una mano", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Resistencia", type: "B\xE1sico", equipment: "Kettlebell", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_kb_snatch_single", name: "Snatch KB Unilateral", description: "Snatch a una mano", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Resistencia", type: "B\xE1sico", equipment: "Kettlebell", force: "Bisagra", bodyPart: "full", chain: "posterior", setupTime: 2, technicalDifficulty: 6, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_kb_clean_single", name: "Clean KB Unilateral", description: "Clean a una mano", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Resistencia", type: "B\xE1sico", equipment: "Kettlebell", force: "Bisagra", bodyPart: "full", chain: "posterior", setupTime: 2, technicalDifficulty: 5, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_kb_tgu_halfloaded", name: "Get Up Medio", description: "Turkish get up parcial", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Estabilidad", type: "Accesorio", equipment: "Kettlebell", force: "Otro", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 6, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_kb_farmers_walk", name: "Marcha Granjero KB", description: "Marcha con KB", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Resistencia", type: "Accesorio", equipment: "Kettlebell", force: "Otro", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_kb_rack_squat_single", name: "Squat Rack KB Unilateral", description: "Squat rack una KB", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Accesorio", equipment: "Kettlebell", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_kb_around_body", name: "Alrededor Cuerpo KB", description: "Pasar KB alrededor cadera", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Kettlebell", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_kb_bottoms_up_carry", name: "Marcha Bottoms Up", description: "Caminar KB invertida", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Estabilidad", type: "Accesorio", equipment: "Kettlebell", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_kb_pullover", name: "Pullover KB", description: "Pullover con KB", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Kettlebell", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_kb_floor_press_single", name: "Press Suelo KB Unilateral", description: "Press suelo KB", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Accesorio", equipment: "Kettlebell", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_kb_upright_row", name: "Remo Menton KB", description: "Remo vertical KB", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Accesorio", equipment: "Kettlebell", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_kb_leg_raise", name: "Elevacion Piernas KB", description: "Piernas con KB entre pies", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Kettlebell", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_kb_windmill_single", name: "Molino KB Unilateral", description: "Windmill a un lado", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Movilidad", type: "Accesorio", equipment: "Kettlebell", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 6, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_kb_double_farmers", name: "Marcha Doble KB", description: "Marcha dos KB", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Resistencia", type: "Accesorio", equipment: "Kettlebell", force: "Otro", bodyPart: "full", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_ab_crunch_basic", name: "Crunch Basico", description: "Crunch clasico suelo", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 1, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_crunch_bicycle", name: "Crunch Bicicleta", description: "Crunch pedaleo", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_crunch_reverse", name: "Crunch Inverso", description: "Crunch piernas al pecho", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_crunch_twist", name: "Crunch Giro", description: "Crunch con rotacion", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_vertical_leg", name: "Piernas Verticales", description: "Piernas arriba suelo", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_v_up", name: "V-Up", description: "V-up completo", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_mountain_climber", name: "Escalador", description: "Mountain climber", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0.1 },
  { id: "db_exp3_ab_dead_bug", name: "Dead Bug", description: "Dead bug controlado", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_bird_dog", name: "Bird Dog", description: "Bird dog alterno", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_superman", name: "Superman", description: "Extension espalda suelo", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_plank_alt", name: "Plancha Alterna", description: "Plancha tocando hombros", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_plank_reach", name: "Plancha Alcance", description: "Plancha con alcance", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_side_plank_dip", name: "Plancha Lateral Dip", description: "Plancha lateral con dip", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_side_plank_raise", name: "Plancha Lateral Pierna", description: "Plancha lateral pierna arriba", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_rollout_wheel", name: "Rollout Rueda", description: "Ab rollout con rueda", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 5, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_ab_rollout_ring", name: "Rollout Anillas", description: "Rollout con anillas", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 6, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_ab_leg_drop", name: "Caida Piernas", description: "Bajar piernas controlado", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_dragon_flag", name: "Dragon Flag", description: "Dragon flag progresion", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 8, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_ab_hollow_body", name: "Hollow Body", description: "Hollow body hold", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_arch_hold", name: "Arch Hold", description: "Arch hold isometrico", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_russian_twist", name: "Giro Ruso", description: "Russian twist", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_woodchop_body", name: "Wood Chop Corporal", description: "Wood chop sin equipo", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_crossover_crunch", name: "Crunch Cruzado", description: "Crunch codo a rodilla", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_scissor_kick", name: "Tijeras", description: "Tijeras piernas", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_flutter_kick", name: "Flutter Kick", description: "Flutter kick", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_toe_touch", name: "Toque Puntas", description: "Toque puntas acostado", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_plank_shoulder_tap", name: "Plancha Toques", description: "Plancha tocar hombros", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_reverse_crunch_bench", name: "Crunch Inverso Banco", description: "Crunch inverso en banco", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_cable_crunch_kneel", name: "Crunch Polea Rodillas", description: "Crunch polea arrodillado", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_cable_crunch_stand", name: "Crunch Polea Pie", description: "Crunch polea de pie", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_pallof_hold", name: "Pallof Hold", description: "Anti-rotacion isometrica", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Polea", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_cable_rotation", name: "Rotacion Polea", description: "Rotacion torso polea", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_hanging_oblique", name: "Obliquos Colgado", description: "Obliquos en barra", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 5, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_ab_stability_ball_crunch", name: "Crunch Fitball", description: "Crunch en fitball", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_stability_ball_rollout", name: "Rollout Fitball", description: "Rollout en fitball", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_sit_up", name: "Sit Up", description: "Sit up clasico", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_v_sit_up", name: "V Sit Up", description: "Sit up en V", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_twist_sit_up", name: "Sit Up Giro", description: "Sit up con giro", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_candlestick", name: "Candelabro", description: "Candelabro piernas", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_heel_tap", name: "Toque Talones", description: "Toque talones acostado", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_dead_bug_single", name: "Dead Bug Unilateral", description: "Dead bug una pierna", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_hip_dip", name: "Hip Dip", description: "Hip dip lateral", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_plank_up_down", name: "Plancha Subir Bajar", description: "Plancha antebrazo a mano", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_body_saw", name: "Body Saw", description: "Body saw en antebrazos", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 5, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_stir_the_pot", name: "Stir the Pot", description: "Circular en fitball", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 5, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_roll_up", name: "Roll Up", description: "Roll up pilates", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_double_leg_stretch", name: "Doble Pierna Estirar", description: "Pilates doble pierna", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_single_leg_stretch", name: "Pierna Simple Estirar", description: "Pilates pierna simple", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_hundred", name: "Cien", description: "Ejercicio los cien", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_bear_crawl", name: "Marcha Oso", description: "Bear crawl", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_quadruped_reach", name: "Cuadrupedia Alcance", description: "Alcance cuadrupedia", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_supine_march", name: "Marcha Supino", description: "Marcha acostado", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_crunch_weighted", name: "Crunch Ponderado", description: "Crunch con disco", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_russian_twist_weighted", name: "Giro Ruso Ponderado", description: "Russian twist con peso", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_side_bend", name: "Flexion Lateral", description: "Flexion lateral con peso", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Otro", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_ab_landmine_twist", name: "Giro Landmine", description: "Rotacion landmine", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 3, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_landmine_press", name: "Press Landmine", description: "Press landmine rotacion", involvedMuscles: [{ muscle: "Core", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Empuje", bodyPart: "full", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_ab_back_extension", name: "Extension Espalda", description: "Extension espalda banco", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "full", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_ghd_sit_up", name: "GHD Sit Up", description: "Sit up GHD", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_ab_ghd_back_ext", name: "GHD Extension", description: "Extension GHD", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "full", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_ab_decline_sit_up", name: "Sit Up Declive", description: "Sit up banco declive", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_incline_leg_raise", name: "Elevacion Piernas Inclinado", description: "Piernas banco inclinado", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_captain_chair", name: "Silla Capitan", description: "Elevaciones silla capitan", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_ab_oblique_crunch_bench", name: "Crunch Obliquos Banco", description: "Crunch obliquos banco", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_lg_bulgarian_split", name: "Zancada Bulgaria", description: "Zancada pie atras elevado", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Accesorio", equipment: "Mancuerna", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 6, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_lg_bulgarian_barbell", name: "Zancada Bulgaria Barra", description: "Bulgarian con barra", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 6, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_lg_front_rack_lunge", name: "Zancada Rack Frontal", description: "Zancada barra frontal", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Fuerza", type: "Accesorio", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 6, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_lg_walking_lunge_db", name: "Zancada Caminando Mancuerna", description: "Zancada caminando DB", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Accesorio", equipment: "Mancuerna", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_lg_walking_lunge_bb", name: "Zancada Caminando Barra", description: "Zancada caminando barra", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_lg_step_up", name: "Subir Escalon", description: "Step up con peso", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Accesorio", equipment: "Mancuerna", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2.5, cnc: 2, ssc: 0.2 },
  { id: "db_exp3_lg_step_up_bb", name: "Subir Escalon Barra", description: "Step up barra", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_lg_pistol_squat", name: "Pistol Squat", description: "Sentadilla unipodal", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 8, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_lg_shrimp_squat", name: "Shrimp Squat", description: "Squat camar\xC3\u0192\xC6\u2019\xC3\u201A\xC2\xB3n", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 7, efc: 2.5, cnc: 2, ssc: 0.2 },
  { id: "db_exp3_lg_cossack_squat", name: "Sentadilla Cosaco", description: "Squat cosaco", involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }], subMuscleGroup: "Aductores", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 5, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_lg_slider_leg_curl", name: "Curl Femoral Slider", description: "Curl isquios con slider", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Resistencia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_lg_nordic_curl", name: "Curl Nordico", description: "Curl nordico exc\xC3\u0192\xC6\u2019\xC3\u201A\xC2\xA9ntrico", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 8, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_lg_good_morning_db", name: "Good Morning Mancuerna", description: "Good morning DB", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Accesorio", equipment: "Mancuerna", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_lg_sldl", name: "Peso Muerto Piernas R\xC3\u0192\xC6\u2019\xC3\u201A\xC2\xADgidas", description: "SDL barra", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_lg_sldl_db", name: "SDL Mancuerna", description: "Peso muerto piernas rigidas DB", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Mancuerna", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_lg_cable_leg_curl", name: "Curl Femoral Polea", description: "Curl isquios polea", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_lg_cable_leg_ext", name: "Extension Cuadriceps Polea", description: "Extension cuadriceps polea", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Extensi\xF3n", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_lg_calf_raise_leg_press", name: "Gemelos Prensa", description: "Gemelos en prensa", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp3_lg_calf_raise_donkey", name: "Gemelos Burro", description: "Gemelos burro", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp3_lg_calf_raise_single_leg", name: "Gemelos Unipodal", description: "Gemelos a una pierna", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0.3 },
  { id: "db_exp3_lg_calf_raise_incline", name: "Gemelos Inclinado", description: "Gemelos en pendiente", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0.5 },
  { id: "db_exp3_lg_hip_thrust_bb", name: "Hip Thrust Barra", description: "Hip thrust barra", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 4, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_lg_hip_thrust_db", name: "Hip Thrust Mancuerna", description: "Hip thrust mancuerna", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "B\xE1sico", equipment: "Mancuerna", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_lg_hip_thrust_single", name: "Hip Thrust Unilateral", description: "Hip thrust una pierna", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Accesorio", equipment: "Peso Corporal", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 5, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_lg_glute_bridge_single", name: "Puente Unilateral", description: "Puente gluteos una pierna", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Accesorio", equipment: "Peso Corporal", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_lg_cable_kickback", name: "Patada Polea", description: "Patada gluteo polea", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_lg_curtsy_lunge", name: "Zancada Reverencia", description: "Zancada curtsy", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Accesorio", equipment: "Mancuerna", force: "Sentadilla", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_lg_lateral_lunge", name: "Zancada Lateral", description: "Zancada lateral", involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }], subMuscleGroup: "Aductores", category: "Hipertrofia", type: "Accesorio", equipment: "Mancuerna", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_lg_skater_squat", name: "Skater Squat", description: "Squat patinador", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "B\xE1sico", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 7, efc: 2.5, cnc: 2, ssc: 0.2 },
  { id: "db_exp3_lg_box_squat", name: "Sentadilla Caja", description: "Squat a caja", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_lg_pause_squat", name: "Sentadilla Pausa", description: "Squat con pausa", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 3.5, cnc: 2.8, ssc: 0.3 },
  { id: "db_exp3_lg_pin_squat", name: "Sentadilla Pin", description: "Squat desde pins", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 3.5, cnc: 2.8, ssc: 0.2 },
  { id: "db_exp3_lg_jump_squat", name: "Salto Sentadilla", description: "Jump squat", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 2.5, cnc: 2, ssc: 0.5 },
  { id: "db_exp3_lg_sumo_squat_bb", name: "Sentadilla Sumo Barra", description: "Sumo squat barra", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_lg_jefferson_squat", name: "Jefferson Squat", description: "Squat Jefferson", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 6, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_lg_sissy_squat_hold", name: "Sissy Squat Pausa", description: "Sissy squat con pausa", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 6, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_lg_terminal_knee_ext", name: "Extension Rodilla Terminal", description: "Extension rodilla sentado", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Rehabilitacion", type: "Aislamiento", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 1, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_lg_leg_press_wide", name: "Prensa Ancha", description: "Prensa piernas ancha", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 3, efc: 3.5, cnc: 2.8, ssc: 0.3 },
  { id: "db_exp3_lg_leg_press_narrow", name: "Prensa Cerrada", description: "Prensa piernas cerrada", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 3, efc: 3.5, cnc: 2.8, ssc: 0.3 },
  { id: "db_exp3_lg_leg_press_single_foot", name: "Prensa Unipodal", description: "Prensa un pie", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Accesorio", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_lg_glute_ham_raise", name: "Glute Ham Raise", description: "GHR en maquina", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 6, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_lg_hip_abduction_standing", name: "Abduccion Pie", description: "Abduccion de pie", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_lg_hip_adduction_standing", name: "Aduccion Pie", description: "Aduccion de pie", involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }], subMuscleGroup: "Aductores", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_lg_squat_narrow_bb", name: "Sentadilla Cerrada Barra", description: "Squat agarre cerrado", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_lg_front_squat_pause", name: "Front Squat Pausa", description: "Front squat pausa abajo", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 6, efc: 3.5, cnc: 3, ssc: 0.5 },
  { id: "db_exp3_lg_deadlift_pause", name: "Peso Muerto Pausa", description: "Deadlift pausa rodilla", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 4, technicalDifficulty: 5, efc: 3.5, cnc: 3, ssc: 0.2 },
  { id: "db_exp3_lg_rdl_pause", name: "RDL Pausa", description: "RDL pausa estiramiento", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 3, cnc: 2.5, ssc: 0.3 },
  { id: "db_exp3_lg_squat_jump_bb", name: "Squat Jump Barra", description: "Jump squat con barra", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "Accesorio", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 2.8, cnc: 2.2, ssc: 0.5 },
  { id: "db_exp3_lg_deficit_rdl", name: "RDL Deficit", description: "RDL desde elevacion", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Accesorio", equipment: "Mancuerna", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 5, efc: 2.8, cnc: 2.2, ssc: 0.3 },
  { id: "db_exp3_lg_single_leg_rdl", name: "RDL Unipodal", description: "RDL una pierna", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Accesorio", equipment: "Mancuerna", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 6, efc: 2.5, cnc: 2, ssc: 0.2 },
  { id: "db_exp3_lg_copenhagen_plank", name: "Plancha Copenhagen", description: "Plancha aduccion", involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }], subMuscleGroup: "Aductores", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 6, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_lg_leg_extension_hold", name: "Extension Cuadriceps Pausa", description: "Extension con pausa", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_lg_leg_curl_hold", name: "Curl Femoral Pausa", description: "Curl isquios pausa", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_lg_wall_sit", name: "Sentada Pared", description: "Isometrico pared", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_lg_hip_hinge_hold", name: "Bisagra Cadera Pausa", description: "Hip hinge isometrico", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Resistencia", type: "Accesorio", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_lg_sumo_deadlift_high_handle", name: "Sumo Deadlift Alto", description: "Sumo deadlift handles altos", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "M\xE1quina", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.2 },
  { id: "db_exp3_lg_trap_bar_deadlift", name: "Peso Muerto Trap Bar", description: "Deadlift hex bar", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 3.8, cnc: 3, ssc: 0.2 },
  { id: "db_exp3_lg_trap_bar_squat", name: "Squat Trap Bar", description: "Squat hex bar", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_lg_goblet_squat_pause", name: "Goblet Pausa", description: "Goblet squat pausa", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Mancuerna", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.3 },
  { id: "db_exp3_lg_box_step_down", name: "Bajar Caja", description: "Step down controlado", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_lg_sliding_lunge", name: "Zancada Deslizante", description: "Lunge con slider", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_lg_leg_press_calf", name: "Gemelos Prensadora", description: "Gemelos en prensadora", involvedMuscles: [{ muscle: "S\xF3leo", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp3_lg_seated_calf_single", name: "Gemelos Sentado Unilateral", description: "Gemelos sentado una pierna", involvedMuscles: [{ muscle: "S\xF3leo", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp3_lg_tibia_raise", name: "Elevacion Tibial", description: "Elevacion tibia anterior", involvedMuscles: [{ muscle: "Tibial Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Resistencia", type: "Aislamiento", equipment: "Peso Corporal", force: "Extensi\xF3n", bodyPart: "lower", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_tr_shrug_db", name: "Encogimiento Mancuerna", description: "Encogimiento con mancuernas", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_tr_shrug_bb_behind", name: "Encogimiento Barra Atras", description: "Encogimiento barra detras", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 3, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_tr_shrug_rotate_db", name: "Encogimiento Rotatorio", description: "Encogimiento con rotacion", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_tr_shrug_hold", name: "Encogimiento Pausa", description: "Encogimiento con pausa", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_tr_shrug_cable", name: "Encogimiento Polea", description: "Encogimiento polea", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_tr_farmers_walk", name: "Marcha Granjero", description: "Marcha con mancuernas", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Resistencia", type: "Accesorio", equipment: "Mancuerna", force: "Otro", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_tr_suitcase_carry", name: "Marcha Maletin", description: "Marcha unilateral", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Resistencia", type: "Accesorio", equipment: "Mancuerna", force: "Otro", bodyPart: "full", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_tr_trap_bar_shrug", name: "Encogimiento Trap Bar", description: "Encogimiento hex bar", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 3, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_tr_rack_pull_shrug", name: "Rack Pull Encogimiento", description: "Rack pull con encogimiento", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Fuerza", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_tr_face_pull_high", name: "Face Pull Alto", description: "Face pull angulo alto", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_af_wrist_curl_db", name: "Curl Muneca Mancuerna", description: "Flexion muneca mancuerna", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Flexi\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_af_wrist_curl_ez", name: "Curl Muneca EZ", description: "Flexion muneca barra EZ", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Flexi\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_af_reverse_wrist_db", name: "Curl Inverso Muneca DB", description: "Extension muneca mancuerna", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Extensi\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_af_reverse_wrist_ez", name: "Curl Inverso Muneca EZ", description: "Extension muneca EZ", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Extensi\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_af_wrist_roller", name: "Rollo Muneca", description: "Rollo de munecas", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Flexi\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_af_plate_pinch", name: "Pinza Disco", description: "Sostener disco con dedos", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Peso Corporal", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 1, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_af_dead_hang", name: "Colgar Muerto", description: "Colgar en barra", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.5, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_af_towel_hang", name: "Colgar Toalla", description: "Colgar con toalla", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Resistencia", type: "Accesorio", equipment: "Peso Corporal", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_af_reverse_curl_ez", name: "Curl Inverso EZ", description: "Curl inverso barra EZ", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_af_radial_deviation", name: "Desviacion Radial", description: "Desviacion radial muneca", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_af_ulnar_deviation", name: "Desviacion Cubital", description: "Desviacion cubital", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Otro", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1, cnc: 1, ssc: 0 },
  { id: "db_exp3_bb2_bench_close", name: "Press Banca Cerrado", description: "Banca agarre cerrado", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_bb2_incline_close", name: "Press Inclinado Cerrado", description: "Inclinado agarre cerrado", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_bb2_ohp_behind", name: "Press Militar Atras", description: "Press por detras nuca", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_bb2_ohp_push", name: "Press Militar Frente", description: "Press militar frente", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.2, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_bb2_bent_row_v", name: "Remo Inclinado V", description: "Remo barra agarre V", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 4, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_bb2_bent_row_wide", name: "Remo Inclinado Ancho", description: "Remo agarre ancho", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 4, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_bb2_curl_lying", name: "Curl Tumbado Barra", description: "Curl tumbado banco", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bb2_skull_crusher", name: "Skull Crusher", description: "Ext triceps tumbado", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_bb2_clean", name: "Clean", description: "Clean power", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "full", chain: "posterior", setupTime: 4, technicalDifficulty: 8, efc: 3.5, cnc: 3, ssc: 0.3 },
  { id: "db_exp3_bb2_power_clean", name: "Power Clean", description: "Power clean", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "full", chain: "posterior", setupTime: 4, technicalDifficulty: 8, efc: 3.5, cnc: 3, ssc: 0.3 },
  { id: "db_exp3_bb2_snatch", name: "Snatch", description: "Snatch completo", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "full", chain: "posterior", setupTime: 4, technicalDifficulty: 9, efc: 3.8, cnc: 3.2, ssc: 0.3 },
  { id: "db_exp3_bb2_high_pull", name: "High Pull", description: "Tiron alto barra", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Fuerza", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_bb2_press_ez_incline", name: "Press EZ Inclinado", description: "Press EZ banco inclinado", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_bb2_landmine_row", name: "Remo Landmine", description: "Remo landmine", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_bb2_landmine_press", name: "Press Landmine", description: "Press landmine vertical", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_bb2_landmine_squat", name: "Squat Landmine", description: "Squat landmine", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_bb2_yates_row", name: "Remo Yates", description: "Remo Yates inclinado", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 4, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_bb2_pendlay_row", name: "Remo Pendlay", description: "Remo Pendlay", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 4, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_bb2_sumo_deadlift", name: "Peso Muerto Sumo", description: "Deadlift sumo", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 4, technicalDifficulty: 6, efc: 3.8, cnc: 3.2, ssc: 0.2 },
  { id: "db_exp3_bb2_conventional_dl", name: "Peso Muerto Convencional", description: "Deadlift convencional", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 4, technicalDifficulty: 6, efc: 3.8, cnc: 3.2, ssc: 0.2 },
  { id: "db_exp3_bb2_front_squat_pause", name: "Front Squat Pausa", description: "Front squat pausa", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 6, efc: 3.5, cnc: 3, ssc: 0.5 },
  { id: "db_exp3_bb2_back_squat_pause", name: "Back Squat Pausa", description: "Back squat pausa", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_bb2_cg_bench", name: "Press Banca CG", description: "Banca close grip", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 3, cnc: 2.8, ssc: 0 },
  { id: "db_exp3_bb2_lying_tricep", name: "Ext Triceps Tumbado", description: "Extension triceps barra", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_bb2_curl_preacher", name: "Curl Predicador Barra", description: "Curl predicador barra", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bb2_incline_curl", name: "Curl Inclinado Barra", description: "Curl banco inclinado", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bb2_21_curl", name: "Curl 21", description: "Curl 21 repeticiones", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bb2_squat_slow", name: "Sentadilla Lenta", description: "Squat tempo lento", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 3.5, cnc: 2.8, ssc: 0.3 },
  { id: "db_exp3_bb2_rdl_slow", name: "RDL Lento", description: "RDL tempo lento", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 3, cnc: 2.5, ssc: 0.3 },
  { id: "db_exp3_bb2_good_morning_seated", name: "Good Morning Sentado", description: "Good morning sentado", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 2.5, cnc: 2, ssc: 0 },
  { id: "db_exp3_bb2_pullover_ez", name: "Pullover EZ", description: "Pullover barra EZ", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_bb2_jefferson_deadlift", name: "Jefferson Deadlift", description: "Deadlift Jefferson", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 4, technicalDifficulty: 6, efc: 3.5, cnc: 2.8, ssc: 0.2 },
  { id: "db_exp3_bb2_suitcase_deadlift", name: "Peso Muerto Maletin", description: "Deadlift unilateral", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Resistencia", type: "Accesorio", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_bb2_snatch_grip_high_pull", name: "Snatch High Pull", description: "High pull agarre snatch", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Fuerza", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 4, technicalDifficulty: 7, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_bb2_romanian_deadlift", name: "RDL Barra", description: "RDL con barra", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.3 },
  { id: "db_exp3_bb2_push_press", name: "Push Press", description: "Push press", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 3.2, cnc: 2.8, ssc: 0.2 },
  { id: "db_exp3_bb2_jerk", name: "Jerk", description: "Jerk desde rack", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Fuerza", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 4, technicalDifficulty: 8, efc: 3.5, cnc: 3, ssc: 0.3 },
  { id: "db_exp3_bb2_bench_slow", name: "Press Banca Lento", description: "Banca tempo lento", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_bb2_decline_skull", name: "Skull Crusher Declive", description: "Skull crusher declive", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 4, technicalDifficulty: 5, efc: 2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_bb2_seated_ohp", name: "Press Sentado", description: "Press militar sentado", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.2, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_bb2_bradford_press", name: "Bradford Press", description: "Bradford press", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_bb2_wide_grip_bench", name: "Press Banca Ancho", description: "Banca agarre muy ancho", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_mq2_chest_press_vertical", name: "Press Pecho Vertical", description: "Maquina press vertical", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_cable_crossover_high", name: "Cruce Polea Alta", description: "Cruces polea alta", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_crossover_mid", name: "Cruce Polea Media", description: "Cruces polea media", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_crossover_low", name: "Cruce Polea Baja", description: "Cruces polea baja", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_fly_incline", name: "Cruce Inclinado Polea", description: "Cruces inclinados polea", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_lat_pulldown_behind", name: "Jalon Atras Nuca", description: "Jalon por detras", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 5, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq2_straight_arm_rope", name: "Jalon Brazos Rectos Cuerda", description: "Dorsal cuerda", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_mq2_seated_row_close", name: "Remo Sentado Cerrado", description: "Remo polea cerrado", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq2_seated_row_wide", name: "Remo Sentado Ancho", description: "Remo polea ancho", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq2_face_pull_dual", name: "Face Pull Doble", description: "Face pull doble polea", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_lateral_low", name: "Lateral Polea Baja", description: "Lateral polea baja", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_cable_rear_delt", name: "Deltoides Posterior Polea", description: "Laterales inclinado polea", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_cable_curl_single", name: "Curl Polea Unilateral", description: "Curl polea un brazo", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_curl_high", name: "Curl Polea Alta", description: "Curl polea alta", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_tricep_ext_single", name: "Ext Triceps Polea Unilateral", description: "Extension triceps un brazo", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_tricep_ext_high", name: "Ext Triceps Polea Alta", description: "Extension triceps polea alta", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_pulldown_single", name: "Jalon Unilateral", description: "Jalon un brazo", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_row_single_low", name: "Remo Polea Baja Unilateral", description: "Remo bajo un brazo", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_shrug_cable", name: "Encogimiento Polea", description: "Encogimiento polea", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_pec_deck_reverse", name: "Pec Deck Inverso", description: "Pec deck inverso deltoides", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_chest_press_iso_low", name: "Press Pecho Isolateral Bajo", description: "Press unilateral bajo", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_chest_press_iso_high", name: "Press Pecho Isolateral Alto", description: "Press unilateral alto", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_cable_pullover", name: "Pullover Polea", description: "Pullover en polea", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_mq2_incline_chest_machine", name: "Press Inclinado Maquina", description: "Maquina inclinada", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_decline_chest_machine", name: "Press Declinado Maquina", description: "Maquina declinada", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_assisted_pull_up", name: "Dominada Asistida", description: "Dominada con asistencia", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Resistencia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_mq2_cable_chest_press", name: "Press Pecho Polea", description: "Press pecho polea", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_cable_row_straight", name: "Remo Polea Barra", description: "Remo polea barra recta", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq2_upright_row_cable", name: "Remo Menton Polea", description: "Remo vertical polea", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2.2, cnc: 1.8, ssc: 0 },
  { id: "db_exp3_mq2_cable_curl_preacher", name: "Curl Predicador Polea", description: "Curl predicador polea", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_overhead_ext", name: "Ext Triceps Overhead Polea", description: "Extension overhead polea", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_lying_cable_curl", name: "Curl Tumbado Polea", description: "Curl tumbado polea", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_woodchop_rotating", name: "Wood Chop Rotacion", description: "Wood chop rotacion", involvedMuscles: [{ muscle: "Oblicuos", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Rotacion", bodyPart: "full", chain: "full", setupTime: 2, technicalDifficulty: 4, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_crunch_kneel", name: "Crunch Polea Rodillas", description: "Crunch polea arrodillado", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_rope_crunch", name: "Crunch Cuerda", description: "Crunch con cuerda", involvedMuscles: [{ muscle: "Recto Abdominal", role: "primary", activation: 1 }], subMuscleGroup: "Abdomen", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Flexi\xF3n", bodyPart: "full", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_leg_curl_standing", name: "Curl Femoral Pie", description: "Curl isquios de pie", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_leg_extension_45", name: "Extension Cuadriceps 45", description: "Extension 45 grados", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_calf_raise_leg_press", name: "Gemelos Prensadora", description: "Gemelos prensa piernas", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp3_mq2_hack_squat_narrow", name: "Hack Squat Cerrado", description: "Hack squat piernas juntas", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_mq2_hack_squat_wide", name: "Hack Squat Ancho", description: "Hack squat piernas anchas", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_mq2_v_squat_narrow", name: "V-Squat Cerrado", description: "V-squat cerrado", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_mq2_glute_ham_raise", name: "GHR Maquina", description: "Glute ham raise", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 6, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_hip_thrust_machine", name: "Hip Thrust Maquina", description: "Hip thrust maquina", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_mq2_back_extension", name: "Extension Espalda Maquina", description: "Extension espalda maquina", involvedMuscles: [{ muscle: "Espalda Baja", role: "primary", activation: 1 }], subMuscleGroup: "Espalda Baja", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "full", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_seated_leg_curl", name: "Curl Femoral Sentado Maq", description: "Curl sentado maquina", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_pull_through", name: "Pull Through", description: "Pull through polea", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_cable_rdl", name: "RDL Polea", description: "RDL en polea", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_mq2_cable_leg_curl", name: "Curl Femoral Polea", description: "Curl isquios polea", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Flexi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_leg_extension", name: "Extension Polea", description: "Extension cuadriceps polea", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Extensi\xF3n", bodyPart: "lower", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_hip_abduction", name: "Abduccion Polea", description: "Abduccion cadera polea", involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_cable_hip_adduction", name: "Aduccion Polea", description: "Aduccion cadera polea", involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }], subMuscleGroup: "Aductores", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Otro", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_cable_glute_kickback", name: "Patada Gluteo Polea", description: "Patada gluteo polea", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_smith_rdl", name: "RDL Smith", description: "RDL en Smith", involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }], subMuscleGroup: "Isquiosurales", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq2_smith_front_squat", name: "Front Squat Smith", description: "Front squat Smith", involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }], subMuscleGroup: "Cu\xE1driceps", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Sentadilla", bodyPart: "lower", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3.5, cnc: 2.8, ssc: 0.5 },
  { id: "db_exp3_mq2_smith_hip_thrust", name: "Hip Thrust Smith", description: "Hip thrust Smith", involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }], subMuscleGroup: "Gl\xFAteos", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Bisagra", bodyPart: "lower", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_wrist_curl", name: "Curl Muneca Polea", description: "Flexion muneca polea", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Flexi\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.2, cnc: 1, ssc: 0 },
  { id: "db_exp3_mq2_cable_reverse_curl", name: "Curl Inverso Polea", description: "Curl inverso polea", involvedMuscles: [{ muscle: "Antebrazo", role: "primary", activation: 1 }], subMuscleGroup: "Antebrazo", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_seated_calf_raise", name: "Gemelos Sentado Maquina", description: "Gemelos sentado maquina", involvedMuscles: [{ muscle: "S\xF3leo", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp3_mq2_standing_calf_raise", name: "Gemelos Pie Maquina", description: "Gemelos maquina pie", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0.5 },
  { id: "db_exp3_mq2_donkey_calf_raise", name: "Gemelos Burro Maquina", description: "Gemelos maquina burro", involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }], subMuscleGroup: "Pantorrillas", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Extensi\xF3n", bodyPart: "lower", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2, cnc: 1.5, ssc: 0.3 },
  { id: "db_exp3_mq2_unilateral_pulldown", name: "Jalon Unilateral Polea", description: "Jalon un brazo polea", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_unilateral_row", name: "Remo Unilateral Polea", description: "Remo un brazo polea", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_incline_bench_machine", name: "Press Inclinado Smith", description: "Inclinado Smith", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_mq2_decline_bench_machine", name: "Press Declinado Smith", description: "Declinado Smith", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_mq2_chest_press_converging", name: "Press Convergente", description: "Maquina convergente", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_shoulder_press_machine", name: "Press Hombro Maquina", description: "Maquina press hombro", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "B\xE1sico", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_lateral_raise_machine", name: "Lateral Maquina", description: "Maquina lateral", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_rear_delt_machine", name: "Deltoides Posterior Maquina", description: "Maquina deltoides posterior", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_bicep_curl_machine", name: "Curl Biceps Maquina", description: "Maquina curl biceps", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_preacher_curl_machine", name: "Curl Predicador Maquina", description: "Maquina predicador", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_tricep_ext_machine", name: "Ext Triceps Maquina", description: "Maquina extension triceps", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "M\xE1quina", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_cable_fly_decline", name: "Cruce Declive Polea", description: "Cruces declive polea", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 4, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_cable_row_parallel", name: "Remo Polea Paralelo", description: "Remo asas paralelas", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq2_low_pulley_row", name: "Remo Polea Baja", description: "Remo polea baja", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq2_high_pulley_row", name: "Remo Polea Alta", description: "Remo polea alta", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0.2 },
  { id: "db_exp3_mq2_cable_shrug", name: "Encogimiento Polea", description: "Encogimiento polea", involvedMuscles: [{ muscle: "Trapecio", role: "primary", activation: 1 }], subMuscleGroup: "Trapecio", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 2, efc: 2, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_landmine_press_ang", name: "Press Landmine Angulo", description: "Press landmine angulado", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Barra", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_landmine_row_ang", name: "Remo Landmine Angulo", description: "Remo landmine angulado", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_t_bar_row_handle", name: "Remo T-Bar Asas", description: "Remo T-bar con asas", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 3, technicalDifficulty: 5, efc: 3.2, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq2_chest_supported_tbar", name: "Remo T-Bar Apoyado", description: "Remo T-bar con apoyo", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "Accesorio", equipment: "Barra", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 4, technicalDifficulty: 4, efc: 3.2, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_mq2_single_arm_pulldown", name: "Jalon Un Brazo", description: "Jalon unilateral", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_cable_curl_concentration", name: "Curl Concentracion Polea", description: "Curl concentracion polea", involvedMuscles: [{ muscle: "B\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "B\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_reverse_pushdown", name: "Pushdown Inverso", description: "Pushdown agarre inverso", involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }], subMuscleGroup: "Tr\xEDceps", category: "Hipertrofia", type: "Aislamiento", equipment: "Polea", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 1, technicalDifficulty: 3, efc: 1.8, cnc: 1.5, ssc: 0 },
  { id: "db_exp3_mq2_close_grip_pulldown", name: "Jalon Cerrado", description: "Jalon agarre cerrado", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq2_wide_grip_pulldown", name: "Jalon Ancho", description: "Jalon agarre ancho", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq2_neutral_grip_pulldown", name: "Jalon Neutro", description: "Jalon agarre neutro", involvedMuscles: [{ muscle: "Dorsales", role: "primary", activation: 1 }], subMuscleGroup: "Dorsales", category: "Hipertrofia", type: "B\xE1sico", equipment: "Polea", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 2, technicalDifficulty: 3, efc: 3, cnc: 2.5, ssc: 0.2 },
  { id: "db_exp3_mq2_incline_db_fly", name: "Pechos Inclinado Mancuerna", description: "Aperturas inclinadas DB", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_flat_db_fly", name: "Pechos Plano Mancuerna", description: "Aperturas plano DB", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 3, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_decline_db_fly", name: "Pechos Declinado Mancuerna", description: "Aperturas declinadas DB", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_db_incline_press", name: "Press Inclinado Mancuerna", description: "Press inclinado DB", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Mancuerna", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_mq2_db_flat_press", name: "Press Plano Mancuerna", description: "Press plano DB", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Mancuerna", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_mq2_db_decline_press", name: "Press Declinado Mancuerna", description: "Press declinado DB", involvedMuscles: [{ muscle: "Pectoral", role: "primary", activation: 1 }], subMuscleGroup: "Pectoral", category: "Hipertrofia", type: "B\xE1sico", equipment: "Mancuerna", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 4, technicalDifficulty: 4, efc: 3, cnc: 2.5, ssc: 0 },
  { id: "db_exp3_mq2_db_shoulder_press", name: "Press Hombro Mancuerna", description: "Press hombro DB", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "B\xE1sico", equipment: "Mancuerna", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 4, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_db_arnold_press", name: "Arnold Press", description: "Arnold press mancuerna", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "B\xE1sico", equipment: "Mancuerna", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 3, technicalDifficulty: 5, efc: 2.8, cnc: 2.2, ssc: 0 },
  { id: "db_exp3_mq2_db_lateral_raise", name: "Lateral Mancuerna", description: "Elevaciones laterales DB", involvedMuscles: [{ muscle: "Deltoides Lateral", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Lateral", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_db_front_raise", name: "Frontal Mancuerna", description: "Elevaciones frontales DB", involvedMuscles: [{ muscle: "Deltoides Anterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Anterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Empuje", bodyPart: "upper", chain: "anterior", setupTime: 2, technicalDifficulty: 2, efc: 1.6, cnc: 1.2, ssc: 0 },
  { id: "db_exp3_mq2_db_bent_lateral", name: "Lateral Inclinado Mancuerna", description: "Laterales inclinado DB", involvedMuscles: [{ muscle: "Deltoides Posterior", role: "primary", activation: 1 }], subMuscleGroup: "Deltoides Posterior", category: "Hipertrofia", type: "Aislamiento", equipment: "Mancuerna", force: "Tir\xF3n", bodyPart: "upper", chain: "posterior", setupTime: 3, technicalDifficulty: 4, efc: 1.6, cnc: 1.2, ssc: 0 }
];

// data/exerciseDatabase.ts
var BASE_EXERCISE_LIST = [
  // =================================================================
  // TREN SUPERIOR
  // =================================================================
  // --- PECHO ---
  {
    id: "db_bench_press_tng",
    name: "Press de Banca (T\xE1ctil / Touch and Go)",
    alias: "Bench Press",
    description: "Ejercicio fundamental para el desarrollo del pectoral, hombros y tr\xEDceps, realizado de forma continua sin pausa en el pecho.",
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Pectoral", role: "secondary", activation: 0.6 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Trapecio", role: "stabilizer", activation: 0.3 },
      { muscle: "Espalda Baja", role: "stabilizer", activation: 0.2 }
    ],
    subMuscleGroup: "Pectoral",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 6,
    transferability: 8,
    injuryRisk: { level: 7, details: "Hombros (impingement), codos." },
    isHallOfFame: true,
    efc: 3.8,
    cnc: 3.8,
    ssc: 0.3,
    resistanceProfile: { curve: "campana", peakTensionPoint: "medio", description: "Mayor tensi\xF3n en el punto medio." }
  },
  {
    id: "db_bench_press_paused",
    name: "Press de Banca (Pausado)",
    description: "Variante con pausa en el pecho. Elimina el reflejo de estiramiento.",
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Trapecio", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Pectoral",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    variantOf: "db_bench_press_tng",
    setupTime: 4,
    technicalDifficulty: 7,
    transferability: 8,
    efc: 4,
    cnc: 4.2,
    ssc: 0.3
  },
  {
    id: "db_incline_bench_press",
    name: "Press de Banca Inclinado",
    description: "Enfatiza la porci\xF3n clavicular (superior) del pectoral.",
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.7 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.5 },
      { muscle: "Trapecio", role: "stabilizer", activation: 0.3 }
    ],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 5,
    technicalDifficulty: 7,
    efc: 3.8,
    cnc: 3.8,
    ssc: 0.2
  },
  {
    id: "db_dumbbell_bench_press",
    name: "Press de Banca con Mancuernas",
    description: "Mayor rango de movimiento y estabilizaci\xF3n que la barra.",
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Core", role: "stabilizer", activation: 0.3 }
    ],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 7,
    efc: 3.5,
    cnc: 3.5,
    ssc: 0.1
  },
  {
    id: "db_incline_dumbbell_press",
    name: "Press Inclinado con Mancuernas",
    description: "Enfatiza el pectoral superior con mayor libertad de movimiento.",
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.7 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.5 },
      { muscle: "Core", role: "stabilizer", activation: 0.3 }
    ],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 8,
    efc: 3.5,
    cnc: 3.5,
    ssc: 0.1
  },
  {
    id: "db_dips",
    name: "Fondos en Paralelas",
    description: "Ejercicio compuesto para pectoral inferior y tr\xEDceps.",
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.8 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 },
      { muscle: "Core", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Pectoral",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 8,
    isHallOfFame: true,
    efc: 3.5,
    cnc: 3.5,
    ssc: 0.2
  },
  {
    id: "db_push_up",
    name: "Flexiones de Brazos",
    description: "Ejercicio de peso corporal fundamental.",
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 },
      { muscle: "Abdomen", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Pectoral",
    category: "Resistencia",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 4,
    efc: 2.5,
    cnc: 2,
    ssc: 0.1
  },
  {
    id: "db_cable_crossover",
    name: "Cruces de Poleas",
    description: "Tensi\xF3n constante en el pectoral, ideal para bombeo.",
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.4 }
    ],
    subMuscleGroup: "Pectoral",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 4,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0
  },
  // --- ESPALDA ---
  {
    id: "db_pull_up",
    name: "Dominadas Pronas",
    description: "Ejercicio rey para la amplitud de espalda.",
    involvedMuscles: [
      { muscle: "Dorsales", role: "primary", activation: 1 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Braquiorradial", role: "secondary", activation: 0.5 },
      { muscle: "Core", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 8,
    isHallOfFame: true,
    efc: 4,
    cnc: 4,
    ssc: 0.2
  },
  {
    id: "db_chin_up",
    name: "Dominadas Supinas",
    description: "Variante con agarre supino, mayor \xE9nfasis en b\xEDceps.",
    involvedMuscles: [
      { muscle: "Dorsales", role: "primary", activation: 1 },
      { muscle: "B\xEDceps", role: "primary", activation: 1 },
      { muscle: "Core", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 7,
    efc: 3.8,
    cnc: 3.8,
    ssc: 0.2
  },
  {
    id: "db_barbell_row",
    name: "Remo con Barra",
    description: "Constructor de densidad de espalda.",
    involvedMuscles: [
      { muscle: "Dorsales", role: "primary", activation: 1 },
      { muscle: "Dorsales", role: "secondary", activation: 0.8 },
      { muscle: "Trapecio", role: "secondary", activation: 0.7 },
      { muscle: "Espalda Baja", role: "stabilizer", activation: 0.8 },
      { muscle: "Isquiosurales", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Dorsales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 9,
    isHallOfFame: true,
    efc: 4.2,
    cnc: 4,
    ssc: 1.6
  },
  {
    id: "db_dumbbell_row",
    name: "Remo con Mancuerna",
    description: "Remo unilateral para corregir desbalances y mayor rango.",
    involvedMuscles: [
      { muscle: "Dorsales", role: "primary", activation: 1 },
      { muscle: "Dorsales", role: "secondary", activation: 0.6 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.5 },
      { muscle: "Core", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 6,
    efc: 3.2,
    cnc: 2.5,
    ssc: 0.6
  },
  {
    id: "db_lat_pulldown",
    name: "Jal\xF3n al Pecho",
    description: "Alternativa a las dominadas en polea.",
    involvedMuscles: [
      { muscle: "Dorsales", role: "primary", activation: 1 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }
    ],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Polea",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 2.8,
    cnc: 2,
    ssc: 0.1
  },
  {
    id: "db_seated_cable_row",
    name: "Remo Sentado en Polea",
    description: "Tir\xF3n horizontal para densidad de espalda media.",
    involvedMuscles: [
      { muscle: "Dorsales", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "primary", activation: 1 },
      { muscle: "Dorsales", role: "secondary", activation: 0.8 },
      { muscle: "Espalda Baja", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Dorsales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Polea",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 3,
    cnc: 2.5,
    ssc: 0.5
  },
  // --- HOMBROS ---
  {
    id: "db_overhead_press",
    name: "Press Militar (Barra)",
    alias: "OHP",
    description: "Press vertical estricto.",
    involvedMuscles: [
      { muscle: "Deltoides Anterior", role: "primary", activation: 1 },
      { muscle: "Deltoides Lateral", role: "secondary", activation: 0.5 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.8 },
      { muscle: "Core", role: "stabilizer", activation: 0.6 },
      { muscle: "Gl\xFAteos", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Deltoides Anterior",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 9,
    isHallOfFame: true,
    efc: 4,
    cnc: 4.2,
    ssc: 1.5
  },
  {
    id: "db_dumbbell_shoulder_press",
    name: "Press de Hombros (Mancuernas)",
    description: "Press vertical con mayor libertad de movimiento.",
    involvedMuscles: [
      { muscle: "Deltoides Anterior", role: "primary", activation: 1 },
      { muscle: "Deltoides Lateral", role: "secondary", activation: 0.6 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.7 },
      { muscle: "Espalda Baja", role: "stabilizer", activation: 0.3 }
    ],
    subMuscleGroup: "Deltoides Anterior",
    category: "Hipertrofia",
    type: "B\xE1sico",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 6,
    efc: 3.2,
    cnc: 3,
    ssc: 1.2
  },
  {
    id: "db_lateral_raise",
    name: "Elevaciones Laterales (Mancuernas)",
    description: "Aislamiento para la cabeza lateral del deltoides.",
    involvedMuscles: [
      { muscle: "Deltoides Lateral", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Deltoides Lateral",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "full",
    setupTime: 1,
    technicalDifficulty: 6,
    efc: 1.5,
    cnc: 1.5,
    ssc: 0.2
  },
  // --- BRAZOS (Bíceps/Tríceps) ---
  {
    id: "db_barbell_curl",
    name: "Curl con Barra Recta",
    description: "Constructor de masa para b\xEDceps.",
    involvedMuscles: [
      { muscle: "B\xEDceps", role: "primary", activation: 1 },
      { muscle: "Antebrazo", role: "stabilizer", activation: 0.4 },
      { muscle: "Core", role: "stabilizer", activation: 0.3 }
    ],
    subMuscleGroup: "B\xEDceps",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Barra",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior",
    efc: 2,
    cnc: 2,
    ssc: 0.3
  },
  {
    id: "db_triceps_pushdown",
    name: "Extensiones de Tr\xEDceps en Polea",
    description: "Aislamiento cabeza lateral/medial.",
    involvedMuscles: [{ muscle: "Tr\xEDceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Tr\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    efc: 1.5,
    cnc: 1.2,
    ssc: 0.1
  },
  {
    id: "db_skull_crusher",
    name: "Press Franc\xE9s",
    description: "Aislamiento cabeza larga.",
    involvedMuscles: [
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 },
      { muscle: "Codos", role: "stabilizer", activation: 0.3 }
    ],
    subMuscleGroup: "Tr\xEDceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Barra",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior",
    efc: 2.2,
    cnc: 2,
    ssc: 0.2
  },
  // --- CORE ---
  {
    id: "db_plank",
    name: "Plancha Abdominal",
    description: "Isom\xE9trico de estabilidad.",
    involvedMuscles: [
      { muscle: "Transverso Abdominal", role: "primary", activation: 1 },
      { muscle: "Recto Abdominal", role: "secondary", activation: 0.8 },
      { muscle: "Hombros", role: "stabilizer", activation: 0.3 }
    ],
    subMuscleGroup: "Abdomen",
    category: "Resistencia",
    type: "Aislamiento",
    equipment: "Peso Corporal",
    force: "Anti-Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior",
    efc: 1.5,
    cnc: 2,
    ssc: 0.1
  },
  {
    id: "db_hanging_leg_raises",
    name: "Elevaciones de Piernas",
    description: "Flexi\xF3n de cadera y columna.",
    involvedMuscles: [
      { muscle: "Recto Abdominal", role: "primary", activation: 1 },
      { muscle: "Flexores de Cadera", role: "secondary", activation: 0.8 },
      { muscle: "Antebrazo", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Abdomen",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Peso Corporal",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior",
    efc: 2.2,
    cnc: 2,
    ssc: 0.2
  },
  {
    id: "db_ab_wheel",
    name: "Rueda Abdominal",
    description: "Anti-extensi\xF3n avanzada.",
    involvedMuscles: [
      { muscle: "Recto Abdominal", role: "primary", activation: 1 },
      { muscle: "Transverso Abdominal", role: "primary", activation: 1 },
      { muscle: "Dorsales", role: "stabilizer", activation: 0.6 },
      { muscle: "Tr\xEDceps", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Abdomen",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Otro",
    force: "Anti-Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior",
    efc: 2.5,
    cnc: 2.5,
    ssc: 0.5
  },
  // =================================================================
  // TREN INFERIOR
  // =================================================================
  // --- 1. SQUAT SPECIALTY & VARIATIONS ---
  {
    id: "db_squat_high_bar",
    name: "Sentadilla Trasera Barra Alta",
    alias: "High Bar Squat",
    description: "La barra descansa sobre los trapecios. Permite un torso m\xE1s vertical y enfatiza los cu\xE1driceps.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteo Mayor", role: "secondary", activation: 0.6 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.4 },
      { muscle: "Abdomen", role: "stabilizer", activation: 0.3 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 8,
    isHallOfFame: true,
    efc: 4.5,
    cnc: 4.5,
    ssc: 1.5
  },
  {
    id: "db_squat_low_bar",
    name: "Sentadilla Trasera Barra Baja",
    alias: "Low Bar Squat",
    description: "La barra descansa sobre los deltoides posteriores. Mayor inclinaci\xF3n del torso, recluta m\xE1s la cadena posterior.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteo Mayor", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.5 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 9,
    isHallOfFame: true,
    efc: 4.8,
    cnc: 5,
    ssc: 1.8
  },
  {
    id: "db_ssb_squat",
    name: "Sentadilla con Barra de Seguridad",
    alias: "Safety Squat",
    description: "La barra empuja el torso hacia adelante, obligando a los erectores espinales y la espalda alta a luchar para no colapsar.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.7 },
      { muscle: "Erectores Espinales", role: "secondary", activation: 0.8 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 7,
    efc: 4.3,
    cnc: 4,
    ssc: 1.4
  },
  {
    id: "db_buffalo_squat",
    name: "Sentadilla Buffalo",
    description: "Barra curva que reduce el estr\xE9s en los hombros y b\xEDceps, permitiendo mantener altas cargas.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteo Mayor", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Piernas",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "full",
    setupTime: 4,
    technicalDifficulty: 7,
    efc: 4.4,
    cnc: 4.4,
    ssc: 1.4
  },
  {
    id: "db_spider_bar_squat",
    name: "Sentadilla con Barra Spider",
    description: "Variante inestable que enfatiza la fuerza del torso y cu\xE1driceps para mantenerse erguido.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Core", role: "primary", activation: 0.8 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 8,
    efc: 4.5,
    cnc: 4.5,
    ssc: 1.5
  },
  {
    id: "db_front_squat",
    name: "Sentadilla Frontal",
    description: "La barra descansa en los deltoides anteriores. Torso muy vertical, gran \xE9nfasis en cu\xE1driceps y core tor\xE1cico.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 },
      { muscle: "Abdomen", role: "stabilizer", activation: 0.8 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 9,
    efc: 4.2,
    cnc: 4.5,
    ssc: 1.2
  },
  {
    id: "db_zercher_squat",
    name: "Sentadilla Zercher",
    description: "La barra se sostiene en el pliegue de los codos. Brutal para el core, la espalda alta y los cu\xE1driceps.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.8 },
      { muscle: "Abdomen", role: "stabilizer", activation: 0.8 },
      { muscle: "B\xEDceps", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 4,
    technicalDifficulty: 8,
    efc: 4.6,
    cnc: 4.8,
    ssc: 1.9
  },
  {
    id: "db_jefferson_squat",
    name: "Sentadilla Jefferson",
    description: "Sentadilla asim\xE9trica con la barra entre las piernas. Desarrolla fuerza en planos raros y estabilidad.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Aductores", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }
    ],
    subMuscleGroup: "Piernas",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "full",
    setupTime: 4,
    technicalDifficulty: 7,
    efc: 3.8,
    cnc: 3.5,
    ssc: 0.8
  },
  {
    id: "db_somersault_squat",
    name: "Sentadilla Somersault",
    description: "Variante rara enfocada en cu\xE1driceps y movilidad, a menudo con soporte manual o Smith.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Otro",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 2.5,
    cnc: 2,
    ssc: 0.2
  },
  {
    id: "db_anderson_squat",
    name: "Sentadilla Anderson",
    description: "Se inicia el movimiento desde los soportes (pines) en la parte baja. Elimina el ciclo de estiramiento-acortamiento.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "full",
    setupTime: 5,
    technicalDifficulty: 6,
    efc: 4.8,
    cnc: 4.8,
    ssc: 1.6
  },
  {
    id: "db_pause_squat",
    name: "Sentadilla con Pausa",
    description: "Pausa en la parte m\xE1s profunda. Elimina el rebote y construye confianza y rigidez.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 },
      { muscle: "Core", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Sentadilla",
    variantOf: "db_squat_high_bar",
    bodyPart: "lower",
    chain: "full",
    setupTime: 4,
    technicalDifficulty: 8,
    efc: 4.8,
    cnc: 4.8,
    ssc: 1.6
  },
  {
    id: "db_box_squat",
    name: "Sentadilla al Caj\xF3n",
    description: "Sentarse en un caj\xF3n para romper la cadena cin\xE9tica conc\xE9ntrica/exc\xE9ntrica. Enfasis en cadera.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.6 },
      { muscle: "Cu\xE1driceps", role: "secondary", activation: 0.5 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Potencia",
    type: "Accesorio",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 5,
    technicalDifficulty: 7,
    efc: 4,
    cnc: 4,
    ssc: 1.6
  },
  {
    id: "db_cambered_squat",
    name: "Sentadilla con Barra Cambered",
    description: "Barra curvada que baja el centro de gravedad y aumenta la inestabilidad.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.6 },
      { muscle: "Core", role: "stabilizer", activation: 0.7 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Piernas",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 8,
    efc: 4.6,
    cnc: 4.5,
    ssc: 1.6
  },
  {
    id: "db_overhead_squat",
    name: "Sentadilla Overhead",
    description: "Sentadilla con la barra sostenida por encima de la cabeza. Requiere movilidad extrema.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Deltoides", role: "stabilizer", activation: 0.8 },
      { muscle: "Trapecio", role: "stabilizer", activation: 0.8 },
      { muscle: "Core", role: "stabilizer", activation: 0.8 }
    ],
    subMuscleGroup: "Cuerpo Completo",
    category: "Movilidad",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "full",
    chain: "full",
    setupTime: 4,
    technicalDifficulty: 10,
    efc: 4.5,
    cnc: 5,
    ssc: 1.4
  },
  {
    id: "db_goblet_squat",
    name: "Sentadilla Goblet",
    description: "Mancuerna o Kettlebell al pecho. Excelente para aprender el patr\xF3n de sentadilla y movilidad.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
      { muscle: "Core", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Movilidad",
    type: "B\xE1sico",
    equipment: "Mancuerna",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 2.5,
    cnc: 2,
    ssc: 0.4
  },
  {
    id: "db_chain_squat",
    name: "Sentadilla con Cadenas",
    description: "Las cadenas a\xF1aden peso a medida que subes (resistencia acomodada).",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Potencia",
    type: "Accesorio",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "full",
    setupTime: 5,
    technicalDifficulty: 7,
    efc: 4.8,
    cnc: 5,
    ssc: 1.7
  },
  {
    id: "db_assisted_squat",
    name: "Sentadilla Asistida",
    description: "Se realiza agarr\xE1ndose a un sistema de suspensi\xF3n o marco para mantener el torso vertical.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Movilidad",
    type: "Accesorio",
    equipment: "Otro",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 2,
    efc: 2,
    cnc: 1.5,
    ssc: 0.2
  },
  {
    id: "db_chair_stand",
    name: "Levantarse de la Silla",
    description: "Sentarse y levantarse de una silla con los brazos cruzados. Test de funcionalidad.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }],
    subMuscleGroup: "Piernas",
    category: "Movilidad",
    type: "B\xE1sico",
    equipment: "Otro",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 1,
    efc: 1.5,
    cnc: 1,
    ssc: 0.1
  },
  {
    id: "db_bosu_squat",
    name: "Sentadilla BOSU",
    description: "Sentadilla sobre la superficie inestable de un BOSU.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Core", role: "primary", activation: 0.8 },
      { muscle: "Pantorrillas", role: "stabilizer", activation: 0.6 },
      { muscle: "Gl\xFAteos", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Piernas",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Otro",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "full",
    setupTime: 2,
    technicalDifficulty: 6,
    efc: 3,
    cnc: 4,
    ssc: 0.5
  },
  // --- 2. DEADLIFT SPECIALTY & VARIATIONS ---
  {
    id: "db_deadlift",
    name: "Peso Muerto Convencional",
    description: "El rey de la cadena posterior. Levantar peso muerto del suelo.",
    involvedMuscles: [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "stabilizer", activation: 0.8 },
      { muscle: "Dorsales", role: "stabilizer", activation: 0.6 },
      { muscle: "Antebrazo", role: "stabilizer", activation: 0.6 }
    ],
    subMuscleGroup: "Erectores Espinales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 8,
    isHallOfFame: true,
    efc: 5,
    cnc: 5,
    ssc: 2
  },
  {
    id: "db_sumo_deadlift",
    name: "Peso Muerto Sumo",
    description: "Pies anchos, manos dentro. M\xE1s \xE9nfasis en caderas y cu\xE1driceps, menos en espalda baja.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Aductores", role: "primary", activation: 1 },
      { muscle: "Cu\xE1driceps", role: "secondary", activation: 0.6 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.5 },
      { muscle: "Trapecio", role: "stabilizer", activation: 0.6 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 9,
    efc: 4.8,
    cnc: 4.8,
    ssc: 1.6
  },
  {
    id: "db_semi_sumo_deadlift",
    name: "Peso Muerto Semi-Sumo",
    description: "H\xEDbrido entre convencional y sumo. Pies ligeramente m\xE1s anchos que hombros.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "primary", activation: 0.8 },
      { muscle: "Cu\xE1driceps", role: "secondary", activation: 0.4 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 8,
    efc: 4.9,
    cnc: 4.9,
    ssc: 1.8
  },
  {
    id: "db_romanian_deadlift",
    name: "Peso Muerto Rumano",
    alias: "RDL",
    description: "Inicio desde arriba, bajar hasta estirar isquios. \xC9nfasis en hipertrofia.",
    involvedMuscles: [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.7 },
      { muscle: "Antebrazo", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Isquiosurales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 6,
    efc: 4.2,
    cnc: 4,
    ssc: 1.8
  },
  {
    id: "db_stiff_leg_deadlift",
    name: "Peso Muerto Piernas R\xEDgidas",
    description: "Desde el suelo con piernas casi rectas. M\xE1ximo estiramiento de isquios.",
    involvedMuscles: [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "secondary", activation: 0.8 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }
    ],
    subMuscleGroup: "Isquiosurales",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 7,
    efc: 4.3,
    cnc: 4,
    ssc: 1.9
  },
  {
    id: "db_deficit_deadlift",
    name: "Peso Muerto con D\xE9ficit",
    description: "Parado sobre una plataforma. Aumenta el rango de movimiento y dificultad inicial.",
    involvedMuscles: [
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Cu\xE1driceps", role: "secondary", activation: 0.6 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.8 }
    ],
    subMuscleGroup: "Erectores Espinales",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 5,
    technicalDifficulty: 9,
    efc: 5,
    cnc: 5,
    ssc: 2
  },
  {
    id: "db_rack_pull",
    name: "Rack Pull / Jal\xF3n de Bloques",
    description: "Peso muerto parcial desde altura de rodillas. Sobrecarga espalda alta y bloqueo.",
    involvedMuscles: [
      { muscle: "Trapecio", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.8 },
      { muscle: "Antebrazo", role: "stabilizer", activation: 0.8 }
    ],
    subMuscleGroup: "Espalda",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 5,
    technicalDifficulty: 6,
    efc: 4.5,
    cnc: 4.5,
    ssc: 2
  },
  {
    id: "db_paused_deadlift",
    name: "Peso Muerto con Pausa",
    description: "Pausa de 1-2 segundos al despegar. Ense\xF1a a mantener la tensi\xF3n.",
    involvedMuscles: [
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Core", role: "stabilizer", activation: 0.8 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.8 }
    ],
    subMuscleGroup: "Erectores Espinales",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 8,
    efc: 5,
    cnc: 5,
    ssc: 2
  },
  {
    id: "db_reverse_band_deadlift",
    name: "Peso Muerto con Bandas Invertidas",
    description: "Bandas ayudan en el despegue. Sobrecarga el bloqueo final.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.8 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }
    ],
    subMuscleGroup: "Espalda",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 5,
    technicalDifficulty: 7,
    efc: 4.8,
    cnc: 5,
    ssc: 2
  },
  {
    id: "db_trap_bar_deadlift",
    name: "Peso Muerto con Barra Hexagonal",
    description: "Barra Hexagonal. H\xEDbrido entre sentadilla y peso muerto.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.6 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Piernas",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Otro",
    force: "Bisagra",
    bodyPart: "full",
    chain: "full",
    setupTime: 4,
    technicalDifficulty: 4,
    efc: 4.5,
    cnc: 4,
    ssc: 1.4
  },
  {
    id: "db_snatch_grip_deadlift",
    name: "Peso Muerto Agarre Arrancada",
    description: "Agarre muy ancho. Aumenta el rango de movimiento y demanda de espalda alta.",
    involvedMuscles: [
      { muscle: "Trapecio", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Dorsales", role: "secondary", activation: 0.6 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.8 }
    ],
    subMuscleGroup: "Espalda",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 8,
    efc: 4.8,
    cnc: 4.5,
    ssc: 1.8
  },
  {
    id: "db_reeves_deadlift",
    name: "Peso Muerto Reeves",
    description: "Agarrando los discos en lugar de la barra. Extremo para agarre y trapecios.",
    involvedMuscles: [
      { muscle: "Trapecio", role: "primary", activation: 1 },
      { muscle: "Antebrazo", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }
    ],
    subMuscleGroup: "Espalda",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 7,
    efc: 4,
    cnc: 3.8,
    ssc: 1.5
  },
  {
    id: "db_good_mornings",
    name: "Buenos D\xEDas",
    description: "Barra en la espalda, flexi\xF3n de cadera. Excelente para isquios y espalda baja.",
    involvedMuscles: [
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Core", role: "stabilizer", activation: 0.6 }
    ],
    subMuscleGroup: "Isquiosurales",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 7,
    efc: 4,
    cnc: 3.5,
    ssc: 2
  },
  {
    id: "db_cambered_good_morning",
    name: "Buenos D\xEDas con Barra Cambered",
    description: "La curvatura de la barra baja el centro de gravedad y crea inestabilidad.",
    involvedMuscles: [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.8 },
      { muscle: "Core", role: "stabilizer", activation: 0.7 }
    ],
    subMuscleGroup: "Isquiosurales",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 8,
    efc: 4.2,
    cnc: 3.8,
    ssc: 2
  },
  {
    id: "db_suspended_good_morning",
    name: "Buenos D\xEDas Suspendidos",
    description: "Se inicia desde cadenas o cintas a una altura espec\xEDfica.",
    involvedMuscles: [
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.8 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }
    ],
    subMuscleGroup: "Espalda Baja",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 5,
    technicalDifficulty: 8,
    efc: 4.5,
    cnc: 4,
    ssc: 2
  },
  {
    id: "db_hyperextensions",
    name: "Hiperextensiones (45\xBA)",
    description: "En banco inclinado. Enfocado en gl\xFAteo y espalda baja.",
    involvedMuscles: [
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.5 }
    ],
    subMuscleGroup: "Espalda Baja",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "M\xE1quina",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 2.5,
    cnc: 2,
    ssc: 0.6
  },
  {
    id: "db_reverse_hyper",
    name: "Hiperextensiones Inversas",
    description: '"Santo grial" de la cadena posterior. Descomprime la columna lumbar.',
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "secondary", activation: 0.8 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.5 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "M\xE1quina",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 4,
    efc: 2.5,
    cnc: 1.5,
    ssc: 0
    // Valor 0.0 porque es descompresión real
  },
  {
    id: "db_cable_pull_through",
    name: "Pull-Through en Polea",
    description: "Ense\xF1a la mec\xE1nica de bisagra de cadera con tensi\xF3n constante.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.6 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 2.5,
    cnc: 2,
    ssc: 0.3
  },
  {
    id: "db_kettlebell_swing",
    name: "Balanceo con Pesa Rusa",
    description: "Movimiento bal\xEDstico de cadera. Potencia y acondicionamiento.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Cardiovascular", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Potencia",
    type: "Accesorio",
    equipment: "Kettlebell",
    force: "Bisagra",
    bodyPart: "full",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 6,
    efc: 3.5,
    cnc: 3,
    ssc: 0.8
  },
  {
    id: "db_prisoner_rdl",
    name: "Peso Muerto Rumano Prisionero",
    description: "Manos detr\xE1s de la cabeza para forzar extensi\xF3n tor\xE1cica. Bisagra de cadera con peso corporal.",
    involvedMuscles: [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Isquiosurales",
    category: "Movilidad",
    type: "B\xE1sico",
    equipment: "Peso Corporal",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 3,
    efc: 2,
    cnc: 1.5,
    ssc: 0.3
  },
  {
    id: "db_bodyweight_hip_thrust",
    name: "Puente de Caderas con Hombros Elevados",
    description: "Espalda alta apoyada en banco. Extensi\xF3n de cadera con peso corporal.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.5 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 2,
    cnc: 1.5,
    ssc: 0.1
  },
  // --- 4. MÁQUINAS Y UNILATERALES ---
  {
    id: "db_leg_press_45",
    name: "Prensa de Piernas",
    description: "Mueve grandes cargas sin fatiga axial. Excelente para hipertrofia de piernas.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "B\xE1sico",
    equipment: "M\xE1quina",
    force: "Empuje",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 2,
    efc: 3.2,
    cnc: 2.5,
    ssc: 0.3
  },
  {
    id: "db_single_leg_press",
    name: "Prensa Inclinada a Una Pierna",
    description: "Corrige asimetr\xEDas de fuerza entre piernas de forma segura.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "M\xE1quina",
    force: "Empuje",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 3,
    efc: 2.8,
    cnc: 2,
    ssc: 0.1
  },
  {
    id: "db_hack_squat",
    name: "Sentadilla Hack (M\xE1quina)",
    description: "Estabilidad total y gran profundidad para aislar cu\xE1driceps.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }, { muscle: "Gl\xFAteos", role: "secondary", activation: 0.4 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "M\xE1quina",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 3.5,
    cnc: 3,
    ssc: 0.4
  },
  {
    id: "db_barbell_hack_squat",
    name: "Sentadilla Hack con Barra",
    description: "Levantamiento de la barra desde el suelo por detr\xE1s de las piernas.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.5 },
      { muscle: "Antebrazo", role: "stabilizer", activation: 0.6 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 7,
    efc: 4,
    cnc: 4,
    ssc: 1
  },
  {
    id: "db_pendulum_squat",
    name: "Sentadilla P\xE9ndulo",
    description: "M\xE1quina que altera la curva de resistencia, permitiendo m\xE1xima flexi\xF3n.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "M\xE1quina",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 3.8,
    cnc: 2.8,
    ssc: 0.5
  },
  {
    id: "db_belt_squat",
    name: "Sentadilla con Cintur\xF3n",
    description: "Carga la cadera y piernas sin comprimir la columna.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 }
    ],
    subMuscleGroup: "Piernas",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "M\xE1quina",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "full",
    setupTime: 3,
    technicalDifficulty: 4,
    efc: 3.8,
    cnc: 2.5,
    ssc: 0
  },
  {
    id: "db_belt_squat_march",
    name: "Marcha en Sentadilla con Cintur\xF3n",
    description: "Destruye los gl\xFAteos y psoas sin cargar la columna vertebral.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Abdomen", role: "secondary", activation: 0.6 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "M\xE1quina",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 4,
    efc: 3,
    cnc: 2,
    ssc: 0
  },
  {
    id: "db_leg_extension",
    name: "Extensiones de Cu\xE1driceps",
    description: "Aislamiento puro para cu\xE1driceps en posici\xF3n acortada.",
    involvedMuscles: [{ muscle: "Cu\xE1driceps", role: "primary", activation: 1 }],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.5,
    cnc: 1,
    ssc: 0
  },
  {
    id: "db_leg_curl_seated",
    name: "Curl Femoral Sentado",
    description: "Aislamiento de isquios en posici\xF3n estirada.",
    involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }],
    subMuscleGroup: "Isquiosurales",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Flexi\xF3n",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 2.2,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_leg_curl_lying",
    name: "Curl Femoral Tumbado",
    description: "Aislamiento cl\xE1sico de isquiosurales.",
    involvedMuscles: [{ muscle: "Isquiosurales", role: "primary", activation: 1 }],
    subMuscleGroup: "Isquiosurales",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Flexi\xF3n",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 2,
    cnc: 1.5,
    ssc: 0
  },
  {
    id: "db_nordic_curl",
    name: "Curl N\xF3rdico",
    description: "Ejercicio exc\xE9ntrico supremo para isquiosurales. Previene lesiones.",
    involvedMuscles: [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 },
      { muscle: "Pantorrillas", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Isquiosurales",
    category: "Fuerza",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Flexi\xF3n",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 7,
    efc: 4.8,
    cnc: 4,
    ssc: 0.5
  },
  {
    id: "db_reverse_nordic",
    name: "Curl N\xF3rdico Inverso",
    description: "Enfocado en la extensi\xF3n de rodilla y estiramiento de cu\xE1driceps.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Abdomen", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Movilidad",
    type: "Accesorio",
    equipment: "Peso Corporal",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 5,
    efc: 3.5,
    cnc: 2.5,
    ssc: 0.1
  },
  {
    id: "db_sissy_squat",
    name: "Sentadilla Sissy",
    description: "Aislamiento de cu\xE1driceps con peso corporal, \xE9nfasis en recto femoral.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Abdomen", role: "stabilizer", activation: 0.6 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Peso Corporal",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 6,
    efc: 2.2,
    cnc: 2,
    ssc: 0.2
  },
  {
    id: "db_bulgarian_split_squat",
    name: "Sentadilla B\xFAlgara",
    description: "Unilateral. Odiada por todos, efectiva como ninguna para gl\xFAteo y cu\xE1driceps.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Core", role: "stabilizer", activation: 0.5 }
    ],
    subMuscleGroup: "Piernas",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 7,
    efc: 3.8,
    cnc: 3.5,
    ssc: 0.8
  },
  {
    id: "db_lunges_walking",
    name: "Zancadas Caminando",
    description: "Din\xE1mico unilateral.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Core", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Piernas",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 3.5,
    cnc: 3.5,
    ssc: 0.6
  },
  {
    id: "db_reverse_lunge",
    name: "Zancada Inversa",
    description: "M\xE1s amable con las rodillas que la zancada frontal. Enfasis en gl\xFAteo.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Core", role: "stabilizer", activation: 0.3 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 4,
    efc: 3.2,
    cnc: 3,
    ssc: 0.5
  },
  {
    id: "db_step_up",
    name: "Subidas al Caj\xF3n",
    description: "Unilateral puro. Brutal para gl\xFAteos si se controla la bajada.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Cu\xE1driceps", role: "secondary", activation: 0.6 },
      { muscle: "Core", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Accesorio",
    equipment: "Mancuerna",
    force: "Empuje",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 3,
    technicalDifficulty: 5,
    efc: 3,
    cnc: 2.5,
    ssc: 0.3
  },
  {
    id: "db_hip_thrust",
    name: "Hip Thrust con Barra",
    description: "El mejor ejercicio para aislar y cargar el gl\xFAteo mayor en acortamiento.",
    involvedMuscles: [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.5 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.3 }
    ],
    subMuscleGroup: "Gl\xFAteos",
    category: "Fuerza",
    type: "B\xE1sico",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 5,
    technicalDifficulty: 5,
    efc: 3.5,
    cnc: 3,
    ssc: 0.5
  },
  {
    id: "db_kas_glute_bridge",
    name: "Puente de Gl\xFAteo KAS",
    description: "Similar al Hip Thrust pero con menor rango, manteniendo la tensi\xF3n constante.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Barra",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 4,
    technicalDifficulty: 4,
    efc: 2.5,
    cnc: 2,
    ssc: 0.2
  },
  {
    id: "db_glute_kickback_cable",
    name: "Patada de Gl\xFAteo en Polea",
    description: "Aislamiento final para gl\xFAteo mayor.",
    involvedMuscles: [{ muscle: "Gl\xFAteos", role: "primary", activation: 1 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 1.8,
    cnc: 1.5,
    ssc: 0.1
  },
  {
    id: "db_cable_abduction",
    name: "Abducci\xF3n de Cadera en Polea",
    description: "Para gl\xFAteo medio y estabilidad.",
    involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "Polea",
    force: "Otro",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 1.5,
    cnc: 1,
    ssc: 0
  },
  {
    id: "db_standing_calf_raise",
    name: "Elevaci\xF3n de Talones De Pie",
    description: "Enfasis en gastrocnemio.",
    involvedMuscles: [
      { muscle: "Gastrocnemio", role: "primary", activation: 1 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.3 }
    ],
    subMuscleGroup: "Pantorrillas",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 2,
    cnc: 1.5,
    ssc: 1.2
  },
  {
    id: "db_seated_calf_raise",
    name: "Elevaci\xF3n de Talones Sentado",
    description: "Enfasis en s\xF3leo.",
    involvedMuscles: [{ muscle: "S\xF3leo", role: "primary", activation: 1 }],
    subMuscleGroup: "Pantorrillas",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 2,
    efc: 1.8,
    cnc: 1,
    ssc: 0
  },
  {
    id: "db_donkey_calf_raise",
    name: "Elevaci\xF3n de Talones Burro",
    description: "Estiramiento m\xE1ximo del gastrocnemio.",
    involvedMuscles: [{ muscle: "Gastrocnemio", role: "primary", activation: 1 }],
    subMuscleGroup: "Pantorrillas",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 2,
    technicalDifficulty: 3,
    efc: 2,
    cnc: 1.2,
    ssc: 0.1
  },
  {
    id: "db_tibialis_raise",
    name: "Elevaciones de Tibial",
    description: "Prevenci\xF3n de shin splints y balance de pierna.",
    involvedMuscles: [{ muscle: "Tibial Anterior", role: "primary", activation: 1 }],
    subMuscleGroup: "Pantorrillas",
    category: "Resistencia",
    type: "Aislamiento",
    equipment: "Peso Corporal",
    force: "Flexi\xF3n",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 1,
    efc: 1.2,
    cnc: 1,
    ssc: 0
  },
  {
    id: "db_sled_push",
    name: "Empuje de Trineo",
    description: "Acondicionamiento y fuerza conc\xE9ntrica pura de piernas.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Cardiovascular", role: "primary", activation: 1 },
      { muscle: "Core", role: "stabilizer", activation: 0.6 }
    ],
    subMuscleGroup: "Piernas",
    category: "Potencia",
    type: "Accesorio",
    equipment: "Otro",
    force: "Empuje",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 4,
    efc: 4,
    cnc: 3.5,
    ssc: 0.2
  },
  {
    id: "db_sled_pull",
    name: "Arrastre de Trineo (Hacia atr\xE1s)",
    description: "Salud de rodillas y bombeo de cu\xE1driceps (Vasto Medial).",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Core", role: "stabilizer", activation: 0.4 }
    ],
    subMuscleGroup: "Cu\xE1driceps",
    category: "Resistencia",
    type: "Accesorio",
    equipment: "Otro",
    force: "Tir\xF3n",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 3,
    technicalDifficulty: 3,
    efc: 3.5,
    cnc: 3,
    ssc: 0.1
  },
  {
    id: "db_box_jump",
    name: "Salto al Caj\xF3n",
    description: "Potencia explosiva de tren inferior.",
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 }
    ],
    subMuscleGroup: "Piernas",
    category: "Pliometr\xEDa",
    type: "Accesorio",
    equipment: "Otro",
    force: "Salto",
    bodyPart: "lower",
    chain: "full",
    setupTime: 2,
    technicalDifficulty: 5,
    efc: 3,
    cnc: 3.5,
    ssc: 0.1
  },
  {
    id: "db_adductor_machine",
    name: "M\xE1quina de Aductores",
    description: "Aislamiento de la cara interna del muslo.",
    involvedMuscles: [{ muscle: "Aductores", role: "primary", activation: 1 }],
    subMuscleGroup: "Piernas",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Otro",
    bodyPart: "lower",
    chain: "anterior",
    setupTime: 1,
    technicalDifficulty: 1,
    efc: 1.5,
    cnc: 1,
    ssc: 0
  },
  {
    id: "db_abductor_machine",
    name: "M\xE1quina de Abductores",
    description: "Aislamiento de gl\xFAteo medio/menor.",
    involvedMuscles: [{ muscle: "Gl\xFAteo Medio", role: "primary", activation: 1 }],
    subMuscleGroup: "Gl\xFAteos",
    category: "Hipertrofia",
    type: "Aislamiento",
    equipment: "M\xE1quina",
    force: "Otro",
    bodyPart: "lower",
    chain: "posterior",
    setupTime: 1,
    technicalDifficulty: 1,
    efc: 1.5,
    cnc: 1,
    ssc: 0
  },
  // =======================================================================
  // 1. EL ECOSISTEMA ZERCHER (Fuerza Funcional y Core)
  // =======================================================================
  {
    id: "ex-zercher-deadlift",
    name: "Peso Muerto Zercher",
    description: "Levantamiento desde el suelo o bloques con la barra en el pliegue de los codos. Demanda extrema de erectores espinales, core y trapecios.",
    type: "B\xE1sico",
    category: "Fuerza",
    equipment: "Barra",
    force: "Bisagra",
    efc: 4.8,
    cnc: 5,
    ssc: 2,
    involvedMuscles: [
      { muscle: "Espalda Baja", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.6 },
      { muscle: "B\xEDceps", role: "stabilizer", activation: 0.3 },
      { muscle: "Abdominales", role: "stabilizer", activation: 0.3 }
    ]
  },
  {
    id: "ex-zercher-lunge",
    name: "Zancada Zercher",
    description: "Zancadas sosteniendo la carga en los codos. Desplaza el centro de gravedad hacia adelante, aniquilando los cu\xE1driceps y el core anterior.",
    type: "Accesorio",
    category: "Hipertrofia",
    equipment: "Barra",
    force: "Sentadilla",
    efc: 4.2,
    cnc: 4.5,
    ssc: 1.5,
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
      { muscle: "Abdominales", role: "stabilizer", activation: 0.3 }
    ]
  },
  {
    id: "ex-zercher-carry",
    name: "Paseo Zercher (Carry)",
    description: "Caminata pesada sosteniendo la barra en los codos. Ejercicio de anti-flexi\xF3n y acondicionamiento metab\xF3lico.",
    type: "Accesorio",
    category: "Resistencia",
    equipment: "Barra",
    force: "Anti-Flexi\xF3n",
    efc: 3.8,
    cnc: 4,
    ssc: 1.8,
    involvedMuscles: [
      { muscle: "Abdominales", role: "primary", activation: 1 },
      { muscle: "Espalda Baja", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.6 },
      { muscle: "B\xEDceps", role: "stabilizer", activation: 0.3 }
    ]
  },
  // =======================================================================
  // 3. VECTORES EN POLEA Y CABLES (Hipertrofia Moderna)
  // =======================================================================
  {
    id: "ex-cable-fly-high-low",
    name: "Cruce de Poleas (Alto a Bajo)",
    description: "Enfocado en las fibras esternales/costales del pectoral (Pectoral).",
    type: "Aislamiento",
    category: "Hipertrofia",
    equipment: "Polea",
    force: "Empuje",
    efc: 2,
    cnc: 1.5,
    ssc: 0,
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 },
      { muscle: "Abdominales", role: "stabilizer", activation: 0.3 }
    ]
  },
  {
    id: "ex-cable-fly-low-high",
    name: "Cruce de Poleas (Bajo a Alto)",
    description: "Enfocado en las fibras claviculares del pectoral (Pectoral).",
    type: "Aislamiento",
    category: "Hipertrofia",
    equipment: "Polea",
    force: "Empuje",
    efc: 2,
    cnc: 1.5,
    ssc: 0,
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 }
    ]
  },
  {
    id: "ex-bayesian-curl",
    name: "Curl Bayesiano en Polea",
    description: "Curl de b\xEDceps de espaldas a la polea. Sobrecarga el b\xEDceps en su posici\xF3n de m\xE1ximo estiramiento.",
    type: "Aislamiento",
    category: "Hipertrofia",
    equipment: "Polea",
    force: "Tir\xF3n",
    efc: 2,
    cnc: 1.5,
    ssc: 0,
    involvedMuscles: [
      { muscle: "B\xEDceps", role: "primary", activation: 1 }
    ]
  },
  {
    id: "ex-cable-overhead-triceps",
    name: "Extensi\xF3n de Tr\xEDceps sobre la Cabeza (Polea)",
    description: "Extensi\xF3n con cuerda o barra V. M\xE1ximo estiramiento de la cabeza larga del tr\xEDceps.",
    type: "Aislamiento",
    category: "Hipertrofia",
    equipment: "Polea",
    force: "Empuje",
    efc: 2,
    cnc: 1.5,
    ssc: 0.1,
    involvedMuscles: [
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 },
      { muscle: "Abdominales", role: "stabilizer", activation: 0.3 }
    ]
  },
  {
    id: "ex-cable-lateral-raise-behind",
    name: "Elevaci\xF3n Lateral en Polea (Por detr\xE1s)",
    description: "Tensi\xF3n constante en el deltoides lateral con un perfil de resistencia superior a la mancuerna.",
    type: "Aislamiento",
    category: "Hipertrofia",
    equipment: "Polea",
    force: "Tir\xF3n",
    efc: 1.8,
    cnc: 1.5,
    ssc: 0,
    involvedMuscles: [
      { muscle: "Deltoides Lateral", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.5 }
    ]
  },
  // =======================================================================
  // 4. MÁQUINAS CONVERGENTES Y AISLAMIENTO (Poco CNC, Mucho EFC)
  // =======================================================================
  {
    id: "ex-machine-chest-press",
    name: "Press de Pecho en M\xE1quina (Convergente)",
    description: "Permite empujar al fallo total sin estabilizadores y con alta convergencia.",
    type: "B\xE1sico",
    category: "Hipertrofia",
    equipment: "M\xE1quina",
    force: "Empuje",
    efc: 3.2,
    cnc: 2.2,
    ssc: 0.1,
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 }
    ]
  },
  {
    id: "ex-machine-row-chest-supported",
    name: "Remo en M\xE1quina (Pecho Apoyado)",
    description: "Elimina la espalda baja de la ecuaci\xF3n. Puro trabajo de dorsal y trapecio.",
    type: "B\xE1sico",
    category: "Hipertrofia",
    equipment: "M\xE1quina",
    force: "Tir\xF3n",
    efc: 3.2,
    cnc: 2,
    ssc: 0,
    involvedMuscles: [
      { muscle: "Dorsal", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.6 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }
    ]
  },
  {
    id: "ex-reverse-pec-deck",
    name: "P\xE1jaros en M\xE1quina (Reverse Pec Deck)",
    description: "Aislamiento puro para la cabeza posterior del hombro y zona escapular.",
    type: "Aislamiento",
    category: "Hipertrofia",
    equipment: "M\xE1quina",
    force: "Tir\xF3n",
    efc: 1.5,
    cnc: 1,
    ssc: 0,
    involvedMuscles: [
      { muscle: "Deltoides Posterior", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.5 }
    ]
  },
  // =======================================================================
  // 5. CALISTENIA / LASTRADOS (Poder Biomecánico)
  // =======================================================================
  {
    id: "ex-weighted-dips",
    name: "Fondos en Paralelas (Lastrados)",
    description: 'El "Press de Banca del tren superior". Empuje vertical-descendente brutal.',
    type: "B\xE1sico",
    category: "Fuerza",
    equipment: "Peso Corporal",
    force: "Empuje",
    efc: 4.2,
    cnc: 4,
    ssc: 0.5,
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 },
      { muscle: "Abdominales", role: "stabilizer", activation: 0.3 }
    ]
  },
  {
    id: "ex-ring-pushups",
    name: "Flexiones en Anillas",
    description: "Flexiones con inestabilidad tridimensional. Exige co-contracci\xF3n de todo el tren superior.",
    type: "Accesorio",
    category: "Hipertrofia",
    equipment: "Otro",
    force: "Empuje",
    efc: 3.8,
    cnc: 4,
    ssc: 0.2,
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Deltoides Anterior", role: "stabilizer", activation: 0.5 },
      { muscle: "Abdominales", role: "stabilizer", activation: 0.4 }
    ]
  },
  // =======================================================================
  // 6. MANCUERNAS ESPECÍFICAS (Brazos y Espalda)
  // =======================================================================
  {
    id: "ex-spider-curl",
    name: "Curl Ara\xF1a (Spider Curl)",
    description: "Curl con pecho apoyado en banco inclinado. Elimina el impulso y a\xEDsla la contracci\xF3n (cabeza corta).",
    type: "Aislamiento",
    category: "Hipertrofia",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    efc: 1.8,
    cnc: 1.5,
    ssc: 0,
    involvedMuscles: [
      { muscle: "B\xEDceps", role: "primary", activation: 1 }
    ]
  },
  {
    id: "ex-jm-press-dumbbells",
    name: "Press JM con Mancuernas",
    description: "H\xEDbrido entre press cerrado y rompecr\xE1neos. Permite sobrecarga masiva del tr\xEDceps.",
    type: "Accesorio",
    category: "Hipertrofia",
    equipment: "Mancuerna",
    force: "Empuje",
    efc: 2.5,
    cnc: 2.5,
    ssc: 0.1,
    involvedMuscles: [
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 },
      { muscle: "Pectoral", role: "secondary", activation: 0.4 }
    ]
  },
  {
    id: "ex-chest-supported-db-row",
    name: "Remo con Mancuernas (Pecho Apoyado Banco)",
    description: "Tracci\xF3n horizontal bilateral que elimina la necesidad de estabilizaci\xF3n lumbar.",
    type: "B\xE1sico",
    category: "Hipertrofia",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    efc: 3.2,
    cnc: 2.2,
    ssc: 0.1,
    involvedMuscles: [
      { muscle: "Dorsal", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.6 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }
    ]
  },
  // =======================================================================
  // 7. BANDAS ELÁSTICAS Y SALUD ARTICULAR
  // =======================================================================
  {
    id: "ex-band-pull-apart",
    name: "Band Pull-Apart",
    description: "Separaci\xF3n de banda el\xE1stica. Salud escapular, postura y activaci\xF3n del deltoides posterior.",
    type: "Aislamiento",
    category: "Movilidad",
    equipment: "Banda",
    force: "Tir\xF3n",
    efc: 1.2,
    cnc: 1,
    ssc: 0,
    involvedMuscles: [
      { muscle: "Deltoides Posterior", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "primary", activation: 1 }
    ]
  },
  {
    id: "ex-spanish-squat",
    name: "Sentadilla Espa\xF1ola (Bandas)",
    description: "Sentadilla soportada con banda anclada tras las rodillas. Carga isom\xE9tricamente el cu\xE1driceps protegiendo el tend\xF3n rotuliano.",
    type: "Accesorio",
    category: "Hipertrofia",
    equipment: "Banda",
    force: "Sentadilla",
    efc: 3,
    cnc: 2,
    ssc: 0.2,
    involvedMuscles: [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }
    ]
  },
  // =======================================================================
  // 8. MEGA-PAQUETE DE EXPANSIÓN v2.2 (Máquinas Premium, Poleas, Barras Altas)
  // =======================================================================
  {
    id: "ex-machine-shoulder-press",
    name: "Press Militar en M\xE1quina Convergente",
    description: "Empuje vertical estabilizado. Permite llegar al fallo seguro sin drenar el SNC equilibrando el cuerpo.",
    type: "B\xE1sico",
    category: "Hipertrofia",
    equipment: "M\xE1quina",
    force: "Empuje",
    efc: 3.5,
    cnc: 2.2,
    ssc: 0.2,
    involvedMuscles: [
      { muscle: "Deltoides Anterior", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Deltoides Lateral", role: "secondary", activation: 0.4 }
    ]
  },
  {
    id: "ex-machine-pullover",
    name: "Pull-over en M\xE1quina",
    description: "Tensi\xF3n constante en el dorsal ancho a trav\xE9s de un rango de movimiento masivo que las mancuernas no pueden replicar.",
    type: "Aislamiento",
    category: "Hipertrofia",
    equipment: "M\xE1quina",
    force: "Tir\xF3n",
    efc: 2.8,
    cnc: 1.5,
    ssc: 0,
    involvedMuscles: [
      { muscle: "Dorsal", role: "primary", activation: 1 },
      { muscle: "Pectoral", role: "secondary", activation: 0.4 },
      { muscle: "Tr\xEDceps", role: "stabilizer", activation: 0.3 }
    ]
  },
  {
    id: "ex-iliac-cable-row",
    name: "Remo Unilateral en Polea Baja (Foco Il\xEDaco)",
    description: "Tracci\xF3n de un solo brazo tirando hacia la cadera con el torso inclinado. Alineaci\xF3n perfecta con las fibras inferiores del dorsal.",
    type: "Accesorio",
    category: "Hipertrofia",
    equipment: "Polea",
    force: "Tir\xF3n",
    efc: 2.5,
    cnc: 1.8,
    ssc: 0.2,
    involvedMuscles: [
      { muscle: "Dorsal", role: "primary", activation: 1 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.4 },
      { muscle: "Oblicuos", role: "stabilizer", activation: 0.5 }
    ]
  },
  {
    id: "ex-crossbody-triceps-extension",
    name: "Extensi\xF3n de Tr\xEDceps Cruzada (Polea)",
    description: "Trabajo unilateral de tr\xEDceps cruzando el cuerpo, respetando el \xE1ngulo natural del codo para m\xE1xima contracci\xF3n.",
    type: "Aislamiento",
    category: "Hipertrofia",
    equipment: "Polea",
    force: "Empuje",
    efc: 1.8,
    cnc: 1.2,
    ssc: 0,
    involvedMuscles: [
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 }
    ]
  },
  {
    id: "ex-cable-y-raises",
    name: "Elevaciones en Y (Polea/Cables)",
    description: "Elevaciones diagonales cruzadas desde polea baja. Trabajo supremo para el trapecio inferior y deltoides lateral/posterior.",
    type: "Aislamiento",
    category: "Movilidad",
    equipment: "Polea",
    force: "Tir\xF3n",
    efc: 1.5,
    cnc: 1.5,
    ssc: 0.1,
    involvedMuscles: [
      { muscle: "Trapecio", role: "primary", activation: 1 },
      { muscle: "Deltoides Lateral", role: "secondary", activation: 0.6 },
      { muscle: "Deltoides Posterior", role: "secondary", activation: 0.6 }
    ]
  },
  {
    id: "ex-cable-crunch",
    name: "Crunch Abdominal en Polea Alta",
    description: "Encogimientos de rodillas usando una cuerda. Permite sobrecarga progresiva real en el abdomen.",
    type: "Accesorio",
    category: "Hipertrofia",
    equipment: "Polea",
    force: "Flexi\xF3n",
    efc: 2.2,
    cnc: 1.5,
    ssc: 0.3,
    involvedMuscles: [
      { muscle: "Abdominales", role: "primary", activation: 1 }
    ]
  },
  {
    id: "ex-larsen-press",
    name: "Larsen Press",
    description: 'Press de banca con las piernas suspendidas rectas en el aire. Elimina el "leg drive", aislando completamente pecho y tr\xEDceps.',
    type: "B\xE1sico",
    category: "Hipertrofia",
    equipment: "Barra",
    force: "Empuje",
    efc: 3.8,
    cnc: 3.5,
    ssc: 0,
    involvedMuscles: [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 }
    ]
  },
  {
    id: "ex-pendlay-row",
    name: "Remo Pendlay",
    description: "Remo estricto con barra que parte desde el suelo en cada repetici\xF3n. Torso paralelo al piso, desarrollando potencia pura de espalda.",
    type: "B\xE1sico",
    category: "Fuerza",
    equipment: "Barra",
    force: "Tir\xF3n",
    efc: 4.2,
    cnc: 4.5,
    ssc: 1.6,
    involvedMuscles: [
      { muscle: "Dorsal", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "primary", activation: 1 },
      { muscle: "Espalda Baja", role: "stabilizer", activation: 0.7 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.4 }
    ]
  },
  {
    id: "ex-b-stance-rdl",
    name: "RDL Postura B (B-Stance)",
    description: "Peso muerto rumano sesgado, donde una pierna hace el 80% del trabajo y la otra solo da equilibrio. Ideal con mancuernas.",
    type: "Accesorio",
    category: "Hipertrofia",
    equipment: "Mancuerna",
    force: "Bisagra",
    efc: 3.8,
    cnc: 3.2,
    ssc: 1,
    involvedMuscles: [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Espalda Baja", role: "stabilizer", activation: 0.4 }
    ]
  },
  {
    id: "ex-kroc-rows",
    name: "Remo Kroc (Mancuerna Pesada)",
    description: 'Remo a una mano con mancuerna, usando pesos masivos a altas repeticiones, permitiendo un ligero impulso corporal ("body english").',
    type: "B\xE1sico",
    category: "Fuerza",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    efc: 4.5,
    cnc: 3.8,
    ssc: 0.8,
    involvedMuscles: [
      { muscle: "Dorsal", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.6 },
      { muscle: "Antebrazo", role: "secondary", activation: 0.8 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }
    ]
  },
  {
    id: "ex-chest-supported-rear-delt",
    name: "P\xE1jaros en Banco Inclinado (Mancuernas)",
    description: "Abducci\xF3n horizontal apoyando el pecho. Evita balanceos e incendia la parte posterior del hombro.",
    type: "Aislamiento",
    category: "Hipertrofia",
    equipment: "Mancuerna",
    force: "Tir\xF3n",
    efc: 1.8,
    cnc: 1.5,
    ssc: 0,
    involvedMuscles: [
      { muscle: "Deltoides Posterior", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.5 }
    ]
  },
  {
    id: "ex-weighted-chinup",
    name: "Dominadas Supinas Lastradas (Chin-ups)",
    description: "Tracci\xF3n vertical con agarre invertido. Mayor reclutamiento de b\xEDceps y estiramiento del dorsal que las dominadas pronas.",
    type: "B\xE1sico",
    category: "Fuerza",
    equipment: "Peso Corporal",
    force: "Tir\xF3n",
    efc: 4.2,
    cnc: 4,
    ssc: 0.2,
    involvedMuscles: [
      { muscle: "Dorsal", role: "primary", activation: 1 },
      { muscle: "B\xEDceps", role: "primary", activation: 1 },
      { muscle: "Abdominales", role: "stabilizer", activation: 0.4 }
    ]
  },
  {
    id: "ex-ghr",
    name: "Glute-Ham Raise (GHR)",
    description: "Flexi\xF3n de rodilla con el peso corporal. Trabaja el isquiosural en ambas articulaciones (cadera y rodilla) simult\xE1neamente.",
    type: "Accesorio",
    category: "Hipertrofia",
    equipment: "M\xE1quina",
    force: "Tir\xF3n",
    efc: 4,
    cnc: 3.2,
    ssc: 0.2,
    involvedMuscles: [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Gemelos", role: "secondary", activation: 0.6 },
      { muscle: "Gl\xFAteos", role: "stabilizer", activation: 0.5 }
    ]
  },
  {
    id: "ex-pallof-press",
    name: "Press Pallof (Banda/Polea)",
    description: "Empuje isom\xE9trico frente al pecho resistiendo la tracci\xF3n lateral. Rey de los ejercicios de anti-rotaci\xF3n del core.",
    type: "Aislamiento",
    category: "Movilidad",
    equipment: "Banda",
    force: "Anti-Rotaci\xF3n",
    efc: 1.5,
    cnc: 1.5,
    ssc: 0.1,
    involvedMuscles: [
      { muscle: "Abdominales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "stabilizer", activation: 0.5 }
    ]
  },
  {
    id: "ex-copenhagen-plank",
    name: "Plancha Copenhague",
    description: "Plancha lateral con la pierna superior apoyada en un banco, cargando masivamente los aductores en isometr\xEDa.",
    type: "Aislamiento",
    category: "Movilidad",
    equipment: "Peso Corporal",
    force: "Anti-Flexi\xF3n",
    efc: 2,
    cnc: 2,
    ssc: 0.1,
    involvedMuscles: [
      { muscle: "Aductores", role: "primary", activation: 1 },
      { muscle: "Abdominales", role: "secondary", activation: 0.8 }
    ]
  },
  // ========== EJERCICIOS DE EXPANSIÓN (~788 nuevos) ==========
  ...EXERCISE_EXPANSION_LIST,
  ...EXERCISE_EXPANSION_LIST_2,
  ...EXERCISE_EXPANSION_LIST_3
];
var DETAILED_EXERCISE_LIST = BASE_EXERCISE_LIST;

// data/inferMusclesFromName.ts
function inferInvolvedMuscles(name, equipment, force, bodyPart) {
  const n = name.toLowerCase();
  const eq = (equipment || "").toLowerCase();
  if (n.includes("sentadilla")) {
    if (n.includes("frontal") || n.includes("front squat")) {
      return [
        { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
        { muscle: "Core", role: "secondary", activation: 0.8 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 },
        { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.5 }
      ];
    }
    if (n.includes("zercher")) {
      return [
        { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
        { muscle: "Core", role: "primary", activation: 0.9 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
        { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }
      ];
    }
    if (n.includes("hack")) {
      return [
        { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.4 },
        { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.3 }
      ];
    }
    if (n.includes("goblet")) {
      return [
        { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
        { muscle: "Core", role: "secondary", activation: 0.7 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.5 }
      ];
    }
    if (n.includes("b\xFAlgara") || n.includes("bulgar")) {
      return [
        { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "primary", activation: 0.9 },
        { muscle: "Isquiosurales", role: "secondary", activation: 0.5 }
      ];
    }
    return [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.5 },
      { muscle: "Core", role: "stabilizer", activation: 0.4 }
    ];
  }
  if (n.includes("prensa") && (n.includes("pierna") || n.includes("piernas"))) {
    return [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
      { muscle: "Isquiosurales", role: "stabilizer", activation: 0.3 }
    ];
  }
  if (n.includes("zancada") || n.includes("lunge")) {
    return [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "primary", activation: 0.9 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.5 }
    ];
  }
  if (n.includes("subida") && n.includes("caj\xF3n")) {
    return [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 }
    ];
  }
  if (n.includes("extensi\xF3n") && (n.includes("cu\xE1driceps") || n.includes("cuadriceps"))) {
    return [
      { muscle: "Cu\xE1driceps", role: "primary", activation: 1 }
    ];
  }
  if (n.includes("peso muerto") || n.includes("deadlift")) {
    return [
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "primary", activation: 0.9 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 },
      { muscle: "Dorsales", role: "secondary", activation: 0.5 },
      { muscle: "Trapecio", role: "stabilizer", activation: 0.5 }
    ];
  }
  if (n.includes("rumano") || n.includes("rdl") || n.includes("stiff")) {
    return [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }
    ];
  }
  if (n.includes("buenos d\xEDas") || n.includes("good morning")) {
    return [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 },
      { muscle: "Erectores Espinales", role: "stabilizer", activation: 0.6 }
    ];
  }
  if (n.includes("rack pull")) {
    return [
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "primary", activation: 0.9 },
      { muscle: "Dorsales", role: "secondary", activation: 0.6 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }
    ];
  }
  if (n.includes("hip thrust") || n.includes("empuje cadera")) {
    return [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.5 },
      { muscle: "Core", role: "stabilizer", activation: 0.4 }
    ];
  }
  if (n.includes("puente") && n.includes("gl\xFAteo")) {
    return [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.4 }
    ];
  }
  if (n.includes("pull-through") || n.includes("pull through")) {
    return [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.6 },
      { muscle: "Core", role: "stabilizer", activation: 0.5 }
    ];
  }
  if (n.includes("curl") && (n.includes("femoral") || n.includes("isquio") || n.includes("n\xF3rdico") || n.includes("nordic"))) {
    return [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Gemelos", role: "secondary", activation: 0.4 }
    ];
  }
  if (n.includes("ghr") || n.includes("glute ham")) {
    return [
      { muscle: "Isquiosurales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
      { muscle: "Gemelos", role: "stabilizer", activation: 0.4 }
    ];
  }
  if (n.includes("hiperextensi\xF3n") || n.includes("hiperextension")) {
    return [
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.5 }
    ];
  }
  if (n.includes("reverse hyper")) {
    return [
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Gl\xFAteos", role: "secondary", activation: 0.7 }
    ];
  }
  if (n.includes("gemelo") || n.includes("pantorrilla") || n.includes("calf") || n.includes("s\xF3leo") || n.includes("soleo")) {
    return [
      { muscle: "Gastrocnemio", role: "primary", activation: 1 },
      { muscle: "S\xF3leo", role: "secondary", activation: 0.8 }
    ];
  }
  if (n.includes("kettlebell swing") || n.includes("swing")) {
    return [
      { muscle: "Gl\xFAteos", role: "primary", activation: 1 },
      { muscle: "Isquiosurales", role: "secondary", activation: 0.6 },
      { muscle: "Core", role: "secondary", activation: 0.6 },
      { muscle: "Deltoides Anterior", role: "stabilizer", activation: 0.4 }
    ];
  }
  if (n.includes("press") && (n.includes("banca") || n.includes("pecho") || n.includes("bench"))) {
    return [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 },
      { muscle: "Trapecio", role: "stabilizer", activation: 0.3 }
    ];
  }
  if (n.includes("apertura") || n.includes("fly") || n.includes("cruces")) {
    return [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.4 }
    ];
  }
  if (n.includes("flexi\xF3n") || n.includes("flexion") || n.includes("push-up") || n.includes("push up")) {
    return [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 },
      { muscle: "Abdomen", role: "stabilizer", activation: 0.5 }
    ];
  }
  if (n.includes("fondo") && !n.includes("entre bancos")) {
    return [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.8 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.6 }
    ];
  }
  if (n.includes("dominada") || n.includes("pull-up") || n.includes("chin-up")) {
    return [
      { muscle: "Dorsales", role: "primary", activation: 1 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Core", role: "stabilizer", activation: 0.4 }
    ];
  }
  if (n.includes("remo") || n.includes("row")) {
    return [
      { muscle: "Dorsales", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.7 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.5 }
    ];
  }
  if (n.includes("jal\xF3n") || n.includes("pulldown") || n.includes("lat pulldown")) {
    return [
      { muscle: "Dorsales", role: "primary", activation: 1 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }
    ];
  }
  if (n.includes("face pull") || n.includes("tir\xF3n a la cara")) {
    return [
      { muscle: "Deltoides Posterior", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "secondary", activation: 0.7 }
    ];
  }
  if (n.includes("press") && (n.includes("militar") || n.includes("hombro") || n.includes("overhead") || n.includes("ohp"))) {
    return [
      { muscle: "Deltoides Anterior", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.7 },
      { muscle: "Deltoides Lateral", role: "secondary", activation: 0.5 },
      { muscle: "Core", role: "stabilizer", activation: 0.5 }
    ];
  }
  if (n.includes("elevaci\xF3n") && (n.includes("lateral") || n.includes("laterales"))) {
    return [
      { muscle: "Deltoides Lateral", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "stabilizer", activation: 0.4 }
    ];
  }
  if (n.includes("elevaci\xF3n") && (n.includes("frontal") || n.includes("front"))) {
    return [
      { muscle: "Deltoides Anterior", role: "primary", activation: 1 },
      { muscle: "Pectoral", role: "secondary", activation: 0.4 }
    ];
  }
  if (n.includes("curl") && (n.includes("b\xEDceps") || n.includes("biceps") || n.includes("martillo") || n.includes("predicador") || n.includes("concentrado") || n.includes("ara\xF1a") || n.includes("inclinado") || n.includes("polea") || n.includes("arrastre"))) {
    return [
      { muscle: "B\xEDceps", role: "primary", activation: 1 },
      { muscle: "Braquiorradial", role: "secondary", activation: 0.5 },
      { muscle: "Antebrazo", role: "stabilizer", activation: 0.4 }
    ];
  }
  if (n.includes("extensi\xF3n") && n.includes("tr\xEDceps")) {
    return [
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 }
    ];
  }
  if (n.includes("press franc\xE9s") || n.includes("skullcrusher") || n.includes("skull crusher")) {
    return [
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 },
      { muscle: "Antebrazo", role: "stabilizer", activation: 0.3 }
    ];
  }
  if (n.includes("extensi\xF3n trasnuca") || n.includes("overhead extension")) {
    return [
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 }
    ];
  }
  if (n.includes("patada") && n.includes("tr\xEDceps")) {
    return [
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 }
    ];
  }
  if (n.includes("fondos entre bancos") || n.includes("bench dip")) {
    return [
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 },
      { muscle: "Pectoral", role: "secondary", activation: 0.5 }
    ];
  }
  if (n.includes("extensi\xF3n tate") || n.includes("tate press")) {
    return [
      { muscle: "Tr\xEDceps", role: "primary", activation: 1 }
    ];
  }
  if (n.includes("curl") && n.includes("mu\xF1eca")) {
    return [
      { muscle: "Antebrazo", role: "primary", activation: 1 }
    ];
  }
  if (n.includes("plancha") || n.includes("plank")) {
    return [
      { muscle: "Abdomen", role: "primary", activation: 1 }
    ];
  }
  if (n.includes("rueda abdominal") || n.includes("ab wheel")) {
    return [
      { muscle: "Abdomen", role: "primary", activation: 1 },
      { muscle: "Dorsales", role: "stabilizer", activation: 0.5 }
    ];
  }
  if (n.includes("press pallof") || n.includes("pallof")) {
    return [
      { muscle: "Abdomen", role: "primary", activation: 1 }
    ];
  }
  if (n.includes("le\xF1ador") || n.includes("woodchop")) {
    return [
      { muscle: "Abdomen", role: "primary", activation: 1 }
    ];
  }
  if (n.includes("elevaci\xF3n") && n.includes("pierna")) {
    return [
      { muscle: "Abdomen", role: "primary", activation: 1 }
    ];
  }
  if (n.includes("crunch") || n.includes("abdominal")) {
    return [
      { muscle: "Abdomen", role: "primary", activation: 1 }
    ];
  }
  if (n.includes("paseo") || n.includes("carry") || n.includes("granjero") || n.includes("farmers")) {
    return [
      { muscle: "Antebrazo", role: "primary", activation: 1 },
      { muscle: "Trapecio", role: "primary", activation: 0.9 },
      { muscle: "Core", role: "secondary", activation: 0.7 }
    ];
  }
  if (n.includes("yoke") || n.includes("atlas") || n.includes("tire") || n.includes("neum\xE1tico") || n.includes("sandbag") || n.includes("barril") || n.includes("keg")) {
    return [
      { muscle: "Erectores Espinales", role: "primary", activation: 1 },
      { muscle: "Core", role: "primary", activation: 0.9 },
      { muscle: "Trapecio", role: "secondary", activation: 0.7 }
    ];
  }
  if (n.includes("log press")) {
    return [
      { muscle: "Deltoides Anterior", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.7 },
      { muscle: "Core", role: "stabilizer", activation: 0.6 }
    ];
  }
  if (n.includes("estiramiento") || n.includes("movilidad") || n.includes("rotaci\xF3n") || n.includes("cat cow") || n.includes("thread the needle") || n.includes("dislocat")) {
    if (n.includes("cadera") || n.includes("hip") || eq.includes("cadera")) {
      return [{ muscle: "Gl\xFAteos", role: "primary", activation: 0.5 }];
    }
    if (n.includes("hombro") || n.includes("shoulder")) {
      return [{ muscle: "Deltoides Anterior", role: "primary", activation: 0.5 }];
    }
    if (n.includes("tobillo") || n.includes("ankle")) {
      return [{ muscle: "Gemelos", role: "primary", activation: 0.5 }];
    }
    if (n.includes("mu\xF1eca") || n.includes("wrist")) {
      return [{ muscle: "Antebrazo", role: "primary", activation: 0.5 }];
    }
    if (n.includes("cu\xE1driceps") || n.includes("quad")) {
      return [{ muscle: "Cu\xE1driceps", role: "primary", activation: 0.5 }];
    }
    return [
      { muscle: "Erectores Espinales", role: "primary", activation: 0.5 },
      { muscle: "Core", role: "secondary", activation: 0.4 }
    ];
  }
  if (bodyPart === "lower") {
    if (force === "Sentadilla") {
      return [
        { muscle: "Cu\xE1driceps", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }
      ];
    }
    if (force === "Bisagra") {
      return [
        { muscle: "Isquiosurales", role: "primary", activation: 1 },
        { muscle: "Gl\xFAteos", role: "secondary", activation: 0.6 }
      ];
    }
  }
  if (bodyPart === "upper" && (n.includes("press") || n.includes("empuje"))) {
    return [
      { muscle: "Pectoral", role: "primary", activation: 1 },
      { muscle: "Tr\xEDceps", role: "secondary", activation: 0.6 },
      { muscle: "Deltoides Anterior", role: "secondary", activation: 0.5 }
    ];
  }
  if (bodyPart === "upper" && (n.includes("remo") || n.includes("tir\xF3n") || n.includes("curl"))) {
    return [
      { muscle: "Dorsales", role: "primary", activation: 1 },
      { muscle: "B\xEDceps", role: "secondary", activation: 0.6 }
    ];
  }
  return [
    { muscle: "Core", role: "primary", activation: 0.8 },
    { muscle: "Erectores Espinales", role: "secondary", activation: 0.5 }
  ];
}

// data/exerciseDatabaseExtended.json
var exerciseDatabaseExtended_default = [
  {
    name: "Sentadilla Trasera",
    equipment: "Barra Alta (High Bar)",
    type: "C",
    efc: 4.5,
    ssc: 1.4,
    id: "ext_sentadilla-trasera",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Trasera",
    equipment: "Barra Baja (Low Bar)",
    type: "C",
    efc: 4.8,
    ssc: 1.6,
    id: "ext_sentadilla-trasera_2",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Trasera",
    equipment: "Con Pausa (2-3 seg)",
    type: "C",
    efc: 4.6,
    ssc: 1.4,
    id: "ext_sentadilla-trasera_3",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Trasera",
    equipment: "Con Cadenas/Bandas",
    type: "C",
    efc: 5,
    ssc: 1.5,
    id: "ext_sentadilla-trasera_4",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Frontal",
    equipment: "Agarre Ol\xEDmpico/Cruzado",
    type: "C",
    efc: 4.2,
    ssc: 1.1,
    id: "ext_sentadilla-frontal",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Frontal",
    equipment: "Con 2 Kettlebells (Rack)",
    type: "C",
    efc: 3.5,
    ssc: 0.9,
    id: "ext_sentadilla-frontal_2",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Zercher",
    equipment: "Barra en codos",
    type: "C",
    efc: 4.5,
    ssc: 1.8,
    id: "ext_sentadilla-zercher",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Core",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Hack",
    equipment: "Barra (desde el suelo)",
    type: "C",
    efc: 4,
    ssc: 1,
    id: "ext_sentadilla-hack",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.4
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Hack",
    equipment: "M\xE1quina (guiada)",
    type: "C",
    efc: 3,
    ssc: 0.4,
    id: "ext_sentadilla-hack_2",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.4
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Prensa de Piernas",
    equipment: "45 Grados (Standard)",
    type: "C",
    efc: 3.2,
    ssc: 0.2,
    id: "ext_prensa-de-piernas",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Isquiosurales",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Prensa de Piernas",
    equipment: "Horizontal (Selectorizada)",
    type: "C",
    efc: 2.5,
    ssc: 0.1,
    id: "ext_prensa-de-piernas_2",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Isquiosurales",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Prensa de Piernas",
    equipment: "Unilateral (Una pierna)",
    type: "C",
    efc: 2.8,
    ssc: 0.1,
    id: "ext_prensa-de-piernas_3",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Isquiosurales",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Goblet",
    equipment: "Mancuerna / Kettlebell",
    type: "C",
    efc: 2.5,
    ssc: 0.3,
    id: "ext_sentadilla-goblet",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Goblet",
    equipment: "Con Pausa / Tempo",
    type: "C",
    efc: 2.8,
    ssc: 0.3,
    id: "ext_sentadilla-goblet_2",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla B\xFAlgara",
    equipment: "Mancuernas (Bulgarian Split)",
    type: "C",
    efc: 3.5,
    ssc: 0.6,
    id: "ext_sentadilla-b\xFAlgara",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla B\xFAlgara",
    equipment: "Barra (Trasera)",
    type: "C",
    efc: 3.8,
    ssc: 0.9,
    id: "ext_sentadilla-b\xFAlgara_2",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Split",
    equipment: "Est\xE1tica (Tijera)",
    type: "C",
    efc: 3,
    ssc: 0.5,
    id: "ext_sentadilla-split",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Zancada (Lunge)",
    equipment: "Hacia atr\xE1s (Reverse)",
    type: "C",
    efc: 3.2,
    ssc: 0.5,
    id: "ext_zancada-lunge",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Zancada",
    equipment: "Hacia adelante (Walking)",
    type: "C",
    efc: 3.5,
    ssc: 0.6,
    id: "ext_zancada",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Zancada",
    equipment: "Lateral (Side Lunge)",
    type: "C",
    efc: 3,
    ssc: 0.4,
    id: "ext_zancada_2",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Zancada",
    equipment: "Cruzada (Curtsy)",
    type: "C",
    efc: 3,
    ssc: 0.4,
    id: "ext_zancada_3",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Subida al Caj\xF3n",
    equipment: "Step-Up (Mancuerna)",
    type: "C",
    efc: 3,
    ssc: 0.4,
    id: "ext_subida-al-caj\xF3n",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Subida al Caj\xF3n",
    equipment: "Step-Up Explosivo",
    type: "C",
    efc: 3.5,
    ssc: 0.4,
    id: "ext_subida-al-caj\xF3n_2",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Sissy",
    equipment: "Peso corporal / Banco",
    type: "A",
    efc: 2,
    ssc: 0.1,
    id: "ext_sentadilla-sissy",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Pistol",
    equipment: "Peso corporal",
    type: "C",
    efc: 3.8,
    ssc: 0.3,
    id: "ext_sentadilla-pistol",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Extensiones Cu\xE1driceps",
    equipment: "M\xE1quina (Bilateral)",
    type: "A",
    efc: 1.5,
    ssc: 0,
    id: "ext_extensiones-cu\xE1driceps",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Extensiones Cu\xE1driceps",
    equipment: "Unilateral",
    type: "A",
    efc: 1.5,
    ssc: 0,
    id: "ext_extensiones-cu\xE1driceps_2",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Belt Squat",
    equipment: "M\xE1quina de cintur\xF3n",
    type: "C",
    efc: 3.5,
    ssc: 0,
    id: "ext_sentadilla-belt-squat",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Safety Bar",
    equipment: "Barra de Seguridad (SSB)",
    type: "C",
    efc: 4.3,
    ssc: 1.3,
    id: "ext_sentadilla-safety-bar",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sentadilla Box Squat",
    equipment: "Al caj\xF3n (Powerlifting)",
    type: "C",
    efc: 4.2,
    ssc: 1.5,
    id: "ext_sentadilla-box-squat",
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Peso Muerto",
    equipment: "Convencional (Barra)",
    type: "C",
    efc: 5,
    ssc: 2,
    id: "ext_peso-muerto",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Peso Muerto",
    equipment: "Sumo",
    type: "C",
    efc: 4.8,
    ssc: 1.7,
    id: "ext_peso-muerto_2",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Peso Muerto",
    equipment: "Rumano (RDL) Barra",
    type: "C",
    efc: 4,
    ssc: 1.6,
    id: "ext_peso-muerto_3",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Peso Muerto",
    equipment: "Rumano con Mancuernas",
    type: "C",
    efc: 3.5,
    ssc: 1.2,
    id: "ext_peso-muerto_4",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Peso Muerto",
    equipment: "Piernas R\xEDgidas (Stiff Leg)",
    type: "C",
    efc: 4.2,
    ssc: 1.8,
    id: "ext_peso-muerto_5",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Peso Muerto",
    equipment: "D\xE9ficit (Sobre disco)",
    type: "C",
    efc: 5,
    ssc: 2,
    id: "ext_peso-muerto_6",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Rack Pull",
    equipment: "Desde rodillas (Bloqueo)",
    type: "C",
    efc: 4.5,
    ssc: 1.9,
    id: "ext_rack-pull",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Trapecio",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Peso Muerto",
    equipment: "Trap Bar (Hexagonal)",
    type: "C",
    efc: 4.2,
    ssc: 1.3,
    id: "ext_peso-muerto_7",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Peso Muerto",
    equipment: "Agarre Snatch (Ancho)",
    type: "C",
    efc: 4.5,
    ssc: 1.7,
    id: "ext_peso-muerto_8",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Buenos D\xEDas",
    equipment: "Barra (De pie)",
    type: "C",
    efc: 4,
    ssc: 2,
    id: "ext_buenos-d\xEDas",
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Buenos D\xEDas",
    equipment: "Sentado (Seated GM)",
    type: "C",
    efc: 3.5,
    ssc: 1.8,
    id: "ext_buenos-d\xEDas_2",
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Buenos D\xEDas",
    equipment: "Barra Cambered/SSB",
    type: "C",
    efc: 4,
    ssc: 1.8,
    id: "ext_buenos-d\xEDas_3",
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Hip Thrust",
    equipment: "Barra (Empuje Cadera)",
    type: "C",
    efc: 3.5,
    ssc: 0.5,
    id: "ext_hip-thrust",
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Hip Thrust",
    equipment: "M\xE1quina",
    type: "C",
    efc: 3,
    ssc: 0.3,
    id: "ext_hip-thrust_2",
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Puente de Gl\xFAteos",
    equipment: "Suelo (Glute Bridge)",
    type: "A",
    efc: 2,
    ssc: 0.1,
    id: "ext_puente-de-gl\xFAteos",
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Pull-Through",
    equipment: "Polea (Cable)",
    type: "A",
    efc: 2.5,
    ssc: 0.2,
    id: "ext_pull-through",
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Kettlebell Swing",
    equipment: "Ruso (Hombros)",
    type: "C",
    efc: 3.5,
    ssc: 0.8,
    id: "ext_kettlebell-swing",
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Kettlebell Swing",
    equipment: "Americano (Arriba)",
    type: "C",
    efc: 3.8,
    ssc: 1,
    id: "ext_kettlebell-swing_2",
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl Femoral",
    equipment: "Tumbado (M\xE1quina)",
    type: "A",
    efc: 2,
    ssc: 0,
    id: "ext_curl-femoral",
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gemelos",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl Femoral",
    equipment: "Sentado (M\xE1quina)",
    type: "A",
    efc: 2.2,
    ssc: 0,
    id: "ext_curl-femoral_2",
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gemelos",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl Femoral",
    equipment: "De pie (Unilateral)",
    type: "A",
    efc: 1.8,
    ssc: 0,
    id: "ext_curl-femoral_3",
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gemelos",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl N\xF3rdico",
    equipment: "Peso corporal",
    type: "C",
    efc: 4.5,
    ssc: 0.5,
    id: "ext_curl-n\xF3rdico",
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gemelos",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "GHR",
    equipment: "Glute Ham Raise",
    type: "C",
    efc: 4,
    ssc: 0.6,
    id: "ext_ghr",
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Gemelos",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Hiperextensiones",
    equipment: "Banco 45 grados",
    type: "A",
    efc: 2.5,
    ssc: 0.8,
    id: "ext_hiperextensiones",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Reverse Hyper",
    equipment: "M\xE1quina",
    type: "A",
    efc: 2.5,
    ssc: 0.1,
    id: "ext_reverse-hyper",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Peso Muerto Unilateral",
    equipment: "Con Mancuerna (SLDL)",
    type: "C",
    efc: 3,
    ssc: 0.8,
    id: "ext_peso-muerto-unilateral",
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press de Banca",
    equipment: "Plano con Barra",
    type: "C",
    efc: 3.8,
    ssc: 0.4,
    id: "ext_press-de-banca",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press de Banca",
    equipment: "Con Mancuernas",
    type: "C",
    efc: 3.2,
    ssc: 0.2,
    id: "ext_press-de-banca_2",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press de Banca",
    equipment: "Inclinado (Barra)",
    type: "C",
    efc: 3.5,
    ssc: 0.4,
    id: "ext_press-de-banca_3",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press de Banca",
    equipment: "Inclinado (Mancuerna)",
    type: "C",
    efc: 3,
    ssc: 0.2,
    id: "ext_press-de-banca_4",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press de Banca",
    equipment: "Declinado (Barra/Manc)",
    type: "C",
    efc: 3.2,
    ssc: 0.3,
    id: "ext_press-de-banca_5",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press de Banca",
    equipment: "Agarre Cerrado",
    type: "C",
    efc: 3.2,
    ssc: 0.4,
    id: "ext_press-de-banca_6",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press de Banca",
    equipment: "M\xE1quina (Vertical)",
    type: "C",
    efc: 2.5,
    ssc: 0.1,
    id: "ext_press-de-banca_7",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press de Suelo",
    equipment: "Floor Press (Barra/Manc)",
    type: "C",
    efc: 3.5,
    ssc: 0.3,
    id: "ext_press-de-suelo",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press Spoto",
    equipment: "Pausa en aire",
    type: "C",
    efc: 3.6,
    ssc: 0.4,
    id: "ext_press-spoto",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press Militar",
    equipment: "De pie con Barra (OHP)",
    type: "C",
    efc: 4,
    ssc: 1.3,
    id: "ext_press-militar",
    involvedMuscles: [
      {
        muscle: "Deltoides Anterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Deltoides Lateral",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press Militar",
    equipment: "Sentado (Barra)",
    type: "C",
    efc: 3.5,
    ssc: 1.5,
    id: "ext_press-militar_2",
    involvedMuscles: [
      {
        muscle: "Deltoides Anterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Deltoides Lateral",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press de Hombros",
    equipment: "Mancuernas (Sentado)",
    type: "C",
    efc: 3,
    ssc: 1.2,
    id: "ext_press-de-hombros",
    involvedMuscles: [
      {
        muscle: "Deltoides Anterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Deltoides Lateral",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press Arnold",
    equipment: "Mancuernas",
    type: "C",
    efc: 3.2,
    ssc: 1,
    id: "ext_press-arnold",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press Landmine",
    equipment: "Unilateral (Mina)",
    type: "C",
    efc: 3,
    ssc: 0.5,
    id: "ext_press-landmine",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press Trasnuca",
    equipment: "Barra (Sentado/Pie)",
    type: "C",
    efc: 3.8,
    ssc: 1.5,
    id: "ext_press-trasnuca",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Fondos (Dips)",
    equipment: "Lastrados",
    type: "C",
    efc: 3.8,
    ssc: 0.2,
    id: "ext_fondos-dips",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Fondos (Dips)",
    equipment: "Peso Corporal",
    type: "C",
    efc: 3,
    ssc: 0.1,
    id: "ext_fondos-dips_2",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Flexiones (Push-ups)",
    equipment: "Cl\xE1sicas",
    type: "C",
    efc: 2,
    ssc: 0.1,
    id: "ext_flexiones-push-ups",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Flexiones",
    equipment: "Pies elevados",
    type: "C",
    efc: 2.5,
    ssc: 0.1,
    id: "ext_flexiones",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Flexiones",
    equipment: "Lastradas/Banda",
    type: "C",
    efc: 3,
    ssc: 0.2,
    id: "ext_flexiones_2",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Aperturas (Flyes)",
    equipment: "Mancuernas (Plano/Inc)",
    type: "A",
    efc: 2,
    ssc: 0.1,
    id: "ext_aperturas-flyes",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Aperturas",
    equipment: "M\xE1quina (Pec Deck)",
    type: "A",
    efc: 1.8,
    ssc: 0,
    id: "ext_aperturas",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Cruces en Polea",
    equipment: "Cable Crossover",
    type: "A",
    efc: 1.8,
    ssc: 0,
    id: "ext_cruces-en-polea",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Elevaciones Laterales",
    equipment: "Mancuernas",
    type: "A",
    efc: 1.5,
    ssc: 0.2,
    id: "ext_elevaciones-laterales",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Elevaciones Laterales",
    equipment: "Polea (Cable)",
    type: "A",
    efc: 1.6,
    ssc: 0.1,
    id: "ext_elevaciones-laterales_2",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Elevaciones Frontales",
    equipment: "Disco / Mancuerna",
    type: "A",
    efc: 1.5,
    ssc: 0.2,
    id: "ext_elevaciones-frontales",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Face Pull",
    equipment: "Polea Alta",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    id: "ext_face-pull",
    involvedMuscles: [
      {
        muscle: "Deltoides Posterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Trapecio",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "P\xE1jaros (Rear Delt)",
    equipment: "Mancuernas / M\xE1quina",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    id: "ext_p\xE1jaros-rear-delt",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Dominadas",
    equipment: "Pronas (Pull-ups)",
    type: "C",
    efc: 4,
    ssc: 0.2,
    id: "ext_dominadas",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Dominadas",
    equipment: "Supinas (Chin-ups)",
    type: "C",
    efc: 3.8,
    ssc: 0.2,
    id: "ext_dominadas_2",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Dominadas",
    equipment: "Neutras",
    type: "C",
    efc: 3.8,
    ssc: 0.2,
    id: "ext_dominadas_3",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Dominadas",
    equipment: "Lastradas",
    type: "C",
    efc: 4.5,
    ssc: 0.3,
    id: "ext_dominadas_4",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Jal\xF3n al Pecho",
    equipment: "Polea (Lat Pulldown)",
    type: "C",
    efc: 2.8,
    ssc: 0.1,
    id: "ext_jal\xF3n-al-pecho",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Jal\xF3n al Pecho",
    equipment: "Agarre Cerrado/V",
    type: "C",
    efc: 2.8,
    ssc: 0.1,
    id: "ext_jal\xF3n-al-pecho_2",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Jal\xF3n al Pecho",
    equipment: "Unilateral",
    type: "C",
    efc: 2.5,
    ssc: 0.1,
    id: "ext_jal\xF3n-al-pecho_3",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Remo con Barra",
    equipment: "Inclinado (Bent Over)",
    type: "C",
    efc: 4,
    ssc: 1.6,
    id: "ext_remo-con-barra",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Remo Pendlay",
    equipment: "Estricto desde suelo",
    type: "C",
    efc: 4.2,
    ssc: 1.8,
    id: "ext_remo-pendlay",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Remo Yates",
    equipment: "Supino / 45 grados",
    type: "C",
    efc: 3.8,
    ssc: 1.5,
    id: "ext_remo-yates",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Remo con Mancuerna",
    equipment: "A una mano (Apoyo)",
    type: "C",
    efc: 3,
    ssc: 0.6,
    id: "ext_remo-con-mancuerna",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Remo Kroc",
    equipment: "Altas Reps / Trampa",
    type: "C",
    efc: 3.5,
    ssc: 0.8,
    id: "ext_remo-kroc",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Remo en Polea",
    equipment: "Sentado (Seated Row)",
    type: "C",
    efc: 2.8,
    ssc: 0.4,
    id: "ext_remo-en-polea",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Remo en Barra T",
    equipment: "T-Bar Row (Pecho apoyado)",
    type: "C",
    efc: 3,
    ssc: 0.3,
    id: "ext_remo-en-barra-t",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Remo en Barra T",
    equipment: "De pie (Landmine)",
    type: "C",
    efc: 3.8,
    ssc: 1.5,
    id: "ext_remo-en-barra-t_2",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Remo Invertido",
    equipment: "Peso Corporal / TRX",
    type: "C",
    efc: 2.5,
    ssc: 0.1,
    id: "ext_remo-invertido",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Pull-Over",
    equipment: "Mancuerna / Polea",
    type: "A",
    efc: 2,
    ssc: 0.3,
    id: "ext_pull-over",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Jal\xF3n Brazos Rectos",
    equipment: "Polea (Straight Arm)",
    type: "A",
    efc: 1.8,
    ssc: 0.1,
    id: "ext_jal\xF3n-brazos-rectos",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Encogimientos",
    equipment: "Barra / Mancuernas",
    type: "A",
    efc: 2.5,
    ssc: 1.2,
    id: "ext_encogimientos",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Remo al Ment\xF3n",
    equipment: "Barra / Polea",
    type: "C",
    efc: 3,
    ssc: 0.5,
    id: "ext_remo-al-ment\xF3n",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl con Barra",
    equipment: "De pie (Barbell Curl)",
    type: "A",
    efc: 2,
    ssc: 0.3,
    id: "ext_curl-con-barra",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl con Barra Z",
    equipment: "EZ Bar Curl",
    type: "A",
    efc: 1.8,
    ssc: 0.3,
    id: "ext_curl-con-barra-z",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl con Mancuernas",
    equipment: "Alterno (Supinado)",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    id: "ext_curl-con-mancuernas",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl Martillo",
    equipment: "Hammer Curl",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    id: "ext_curl-martillo",
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl Predicador",
    equipment: "Banco Scott",
    type: "A",
    efc: 1.8,
    ssc: 0.1,
    id: "ext_curl-predicador",
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl Ara\xF1a",
    equipment: "Spider Curl (Banco Inc)",
    type: "A",
    efc: 1.8,
    ssc: 0.1,
    id: "ext_curl-ara\xF1a",
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl Inclinado",
    equipment: "Banco Inclinado",
    type: "A",
    efc: 1.8,
    ssc: 0.1,
    id: "ext_curl-inclinado",
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl Concentrado",
    equipment: "Sentado",
    type: "A",
    efc: 1.2,
    ssc: 0,
    id: "ext_curl-concentrado",
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl en Polea",
    equipment: "Desde abajo (Cable Curl)",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    id: "ext_curl-en-polea",
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl de Arrastre",
    equipment: "Drag Curl",
    type: "A",
    efc: 1.6,
    ssc: 0.2,
    id: "ext_curl-de-arrastre",
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press Franc\xE9s",
    equipment: "Barra Z (Skullcrusher)",
    type: "A",
    efc: 2.2,
    ssc: 0.1,
    id: "ext_press-franc\xE9s",
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Extensi\xF3n Tr\xEDceps",
    equipment: "Polea (Pushdown) Barra",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    id: "ext_extensi\xF3n-tr\xEDceps",
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Extensi\xF3n Tr\xEDceps",
    equipment: "Polea (Cuerda)",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    id: "ext_extensi\xF3n-tr\xEDceps_2",
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Extensi\xF3n Trasnuca",
    equipment: "Mancuerna / Cable",
    type: "A",
    efc: 1.8,
    ssc: 0.4,
    id: "ext_extensi\xF3n-trasnuca",
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Patada de Tr\xEDceps",
    equipment: "Mancuerna / Cable",
    type: "A",
    efc: 1.2,
    ssc: 0.1,
    id: "ext_patada-de-tr\xEDceps",
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Fondos entre Bancos",
    equipment: "Bench Dips",
    type: "A",
    efc: 2,
    ssc: 0.1,
    id: "ext_fondos-entre-bancos",
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Pectoral",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Extensi\xF3n Tate",
    equipment: "Mancuernas (Suelo/Banco)",
    type: "A",
    efc: 1.8,
    ssc: 0.1,
    id: "ext_extensi\xF3n-tate",
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Curl de Mu\xF1eca",
    equipment: "Barra (Flexi\xF3n/Extensi\xF3n)",
    type: "A",
    efc: 1,
    ssc: 0,
    id: "ext_curl-de-mu\xF1eca",
    involvedMuscles: [
      {
        muscle: "Antebrazo",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Paseo del Granjero",
    equipment: "Heavy Carries",
    type: "C",
    efc: 3.5,
    ssc: 1.5,
    id: "ext_paseo-del-granjero",
    involvedMuscles: [
      {
        muscle: "Antebrazo",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Trapecio",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Plancha Frontal",
    equipment: "RKC / Standard",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    id: "ext_plancha-frontal",
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Plancha Lateral",
    equipment: "Est\xE1tica",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    id: "ext_plancha-lateral",
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Rueda Abdominal",
    equipment: "Ab Wheel Rollout",
    type: "C",
    efc: 2.5,
    ssc: 0.5,
    id: "ext_rueda-abdominal",
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Press Pallof",
    equipment: "Cable / Banda",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    id: "ext_press-pallof",
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Le\xF1ador",
    equipment: "Cable Woodchop",
    type: "C",
    efc: 2,
    ssc: 0.4,
    id: "ext_le\xF1ador",
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Elevaci\xF3n de Piernas",
    equipment: "Colgado (Hanging Raise)",
    type: "C",
    efc: 2.5,
    ssc: 0.2,
    id: "ext_elevaci\xF3n-de-piernas",
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Flexores de Cadera",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Crunch en Polea",
    equipment: "Kneeling Cable Crunch",
    type: "A",
    efc: 1.8,
    ssc: 0.3,
    id: "ext_crunch-en-polea",
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Giros Rusos",
    equipment: "Russian Twist",
    type: "A",
    efc: 1.8,
    ssc: 0.3,
    id: "ext_giros-rusos",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Dead Bug",
    equipment: "Bicho Muerto",
    type: "A",
    efc: 1.2,
    ssc: 0,
    id: "ext_dead-bug",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Paseo del Camarero",
    equipment: "Waiter's Walk (Overhead)",
    type: "C",
    efc: 2.8,
    ssc: 1.2,
    id: "ext_paseo-del-camarero",
    involvedMuscles: [
      {
        muscle: "Antebrazo",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Trapecio",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Sit-Up",
    equipment: "Banco Declinado",
    type: "A",
    efc: 1.8,
    ssc: 0.6,
    id: "ext_sit-up",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Cargada de Potencia",
    equipment: "Power Clean",
    type: "C",
    efc: 4.8,
    ssc: 1.8,
    id: "ext_cargada-de-potencia",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Arrancada",
    equipment: "Power Snatch",
    type: "C",
    efc: 4.8,
    ssc: 1.6,
    id: "ext_arrancada",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Push Press",
    equipment: "Barra",
    type: "C",
    efc: 4.2,
    ssc: 1.5,
    id: "ext_push-press",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "High Pull",
    equipment: "Desde Hang / Suelo",
    type: "C",
    efc: 3.8,
    ssc: 1.2,
    id: "ext_high-pull",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Salto al Caj\xF3n",
    equipment: "Box Jump",
    type: "C",
    efc: 3,
    ssc: 0.1,
    id: "ext_salto-al-caj\xF3n",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Salto de Longitud",
    equipment: "Broad Jump",
    type: "C",
    efc: 3.2,
    ssc: 0.2,
    id: "ext_salto-de-longitud",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Depth Jump",
    equipment: "Ca\xEDda + Salto",
    type: "C",
    efc: 4.5,
    ssc: 0.8,
    id: "ext_depth-jump",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Lanzamiento Bal\xF3n",
    equipment: "Medicine Ball Slam",
    type: "C",
    efc: 2.5,
    ssc: 0.2,
    id: "ext_lanzamiento-bal\xF3n",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Lanzamiento Pecho",
    equipment: "Chest Pass",
    type: "C",
    efc: 2.5,
    ssc: 0.1,
    id: "ext_lanzamiento-pecho",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Kettlebell Snatch",
    equipment: "Unilateral",
    type: "C",
    efc: 3.8,
    ssc: 1,
    id: "ext_kettlebell-snatch",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Empuje de Trineo",
    equipment: "Prowler Push",
    type: "C",
    efc: 4,
    ssc: 0.5,
    id: "ext_empuje-de-trineo",
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Arrastre de Trineo",
    equipment: "Sled Drag (Atr\xE1s)",
    type: "C",
    efc: 3.5,
    ssc: 0.2,
    id: "ext_arrastre-de-trineo",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Battle Ropes",
    equipment: "Ondas",
    type: "C",
    efc: 3,
    ssc: 0.1,
    id: "ext_battle-ropes",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "M\xE1quina de Abducci\xF3n",
    equipment: "Hip Abduction",
    type: "A",
    efc: 1.2,
    ssc: 0,
    id: "ext_m\xE1quina-de-abducci\xF3n",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "M\xE1quina de Aducci\xF3n",
    equipment: "Hip Adduction",
    type: "A",
    efc: 1.2,
    ssc: 0,
    id: "ext_m\xE1quina-de-aducci\xF3n",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Pantorrilla Sentado",
    equipment: "Seated Calf Raise",
    type: "A",
    efc: 1.2,
    ssc: 0,
    id: "ext_pantorrilla-sentado",
    involvedMuscles: [
      {
        muscle: "Gastrocnemio",
        role: "primary",
        activation: 1
      },
      {
        muscle: "S\xF3leo",
        role: "secondary",
        activation: 0.8
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Pantorrilla de Pie",
    equipment: "Standing Calf Raise",
    type: "A",
    efc: 1.5,
    ssc: 0.5,
    id: "ext_pantorrilla-de-pie",
    involvedMuscles: [
      {
        muscle: "Gastrocnemio",
        role: "primary",
        activation: 1
      },
      {
        muscle: "S\xF3leo",
        role: "secondary",
        activation: 0.8
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Remo Cardio",
    equipment: "Concept2 Row",
    type: "C",
    efc: 3.5,
    ssc: 0.8,
    id: "ext_remo-cardio",
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "SkiErg",
    equipment: "Concept2 Ski",
    type: "C",
    efc: 3.2,
    ssc: 0.5,
    id: "ext_skierg",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    name: "Air Bike",
    equipment: "Assault Bike",
    type: "C",
    efc: 4,
    ssc: 0.2,
    id: "ext_air-bike",
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "upper",
    chain: "anterior",
    cnc: 2.5
  },
  {
    id: "ext_press-de-banca-smith-plano",
    name: "Press de Banca",
    equipment: "Smith (Plano)",
    type: "C",
    efc: 3.2,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-de-banca-smith-inclinado",
    name: "Press de Banca",
    equipment: "Smith (Inclinado)",
    type: "C",
    efc: 3,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-de-banca-smith-declinado",
    name: "Press de Banca",
    equipment: "Smith (Declinado)",
    type: "C",
    efc: 3,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-de-banca-polea-baja-unilatera",
    name: "Press de Banca",
    equipment: "Polea Baja (Unilateral)",
    type: "C",
    efc: 2.5,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-de-banca-polea-baja-bilateral",
    name: "Press de Banca",
    equipment: "Polea Baja (Bilateral)",
    type: "C",
    efc: 2.8,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-de-banca-kettlebell-floor-pre",
    name: "Press de Banca",
    equipment: "Kettlebell (Floor Press)",
    type: "C",
    efc: 3,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-de-pecho-m\xE1quina-smith",
    name: "Press de Pecho",
    equipment: "M\xE1quina Smith",
    type: "C",
    efc: 3,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-de-pecho-m\xE1quina-convergente",
    name: "Press de Pecho",
    equipment: "M\xE1quina Convergente",
    type: "C",
    efc: 3.2,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_aperturas-polea-baja",
    name: "Aperturas",
    equipment: "Polea Baja",
    type: "A",
    efc: 1.8,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_aperturas-polea-alta",
    name: "Aperturas",
    equipment: "Polea Alta",
    type: "A",
    efc: 1.8,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_aperturas-kettlebell",
    name: "Aperturas",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_flexiones-con-disco",
    name: "Flexiones",
    equipment: "Con Disco",
    type: "C",
    efc: 2.8,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_flexiones-mancuernas-agarre",
    name: "Flexiones",
    equipment: "Mancuernas (Agarre)",
    type: "C",
    efc: 2.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_flexiones-kettlebell-agarre",
    name: "Flexiones",
    equipment: "Kettlebell (Agarre)",
    type: "C",
    efc: 2.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_fondos-m\xE1quina-asistida",
    name: "Fondos",
    equipment: "M\xE1quina (Asistida)",
    type: "C",
    efc: 2.5,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_fondos-m\xE1quina-lastrada",
    name: "Fondos",
    equipment: "M\xE1quina (Lastrada)",
    type: "C",
    efc: 3.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-militar-smith",
    name: "Press Militar",
    equipment: "Smith",
    type: "C",
    efc: 3.5,
    ssc: 0.8,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Deltoides Anterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Deltoides Lateral",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-militar-polea-baja",
    name: "Press Militar",
    equipment: "Polea Baja",
    type: "C",
    efc: 2.8,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Deltoides Anterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Deltoides Lateral",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-militar-m\xE1quina-convergente",
    name: "Press Militar",
    equipment: "M\xE1quina Convergente",
    type: "C",
    efc: 3.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Deltoides Anterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Deltoides Lateral",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-militar-kettlebell",
    name: "Press Militar",
    equipment: "Kettlebell",
    type: "C",
    efc: 3,
    ssc: 0.8,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Deltoides Anterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Deltoides Lateral",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-de-hombros-smith",
    name: "Press de Hombros",
    equipment: "Smith",
    type: "C",
    efc: 3.2,
    ssc: 0.6,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Deltoides Anterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Deltoides Lateral",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-de-hombros-m\xE1quina-smith",
    name: "Press de Hombros",
    equipment: "M\xE1quina Smith",
    type: "C",
    efc: 3,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Deltoides Anterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Deltoides Lateral",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_elevaciones-laterales-smith-unilateral",
    name: "Elevaciones Laterales",
    equipment: "Smith (Unilateral)",
    type: "A",
    efc: 1.4,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_elevaciones-laterales-kettlebell",
    name: "Elevaciones Laterales",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_elevaciones-frontales-polea",
    name: "Elevaciones Frontales",
    equipment: "Polea",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_elevaciones-frontales-barra",
    name: "Elevaciones Frontales",
    equipment: "Barra",
    type: "A",
    efc: 1.6,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_elevaciones-frontales-kettlebell",
    name: "Elevaciones Frontales",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_elevaciones-frontales-disco",
    name: "Elevaciones Frontales",
    equipment: "Disco",
    type: "A",
    efc: 1.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_face-pull-banda",
    name: "Face Pull",
    equipment: "Banda",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Deltoides Posterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Trapecio",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_face-pull-mancuernas",
    name: "Face Pull",
    equipment: "Mancuernas",
    type: "A",
    efc: 1.4,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Deltoides Posterior",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Trapecio",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_p\xE1jaros-polea-cruzada",
    name: "P\xE1jaros",
    equipment: "Polea Cruzada",
    type: "A",
    efc: 1.6,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_p\xE1jaros-polea-baja",
    name: "P\xE1jaros",
    equipment: "Polea Baja",
    type: "A",
    efc: 1.5,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_p\xE1jaros-kettlebell",
    name: "P\xE1jaros",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_remo-al-ment\xF3n-smith",
    name: "Remo al Ment\xF3n",
    equipment: "Smith",
    type: "C",
    efc: 2.8,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_remo-al-ment\xF3n-mancuernas",
    name: "Remo al Ment\xF3n",
    equipment: "Mancuernas",
    type: "C",
    efc: 2.8,
    ssc: 0.4,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_remo-al-ment\xF3n-kettlebell",
    name: "Remo al Ment\xF3n",
    equipment: "Kettlebell",
    type: "C",
    efc: 2.5,
    ssc: 0.4,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_remo-con-barra-smith",
    name: "Remo con Barra",
    equipment: "Smith",
    type: "C",
    efc: 3.5,
    ssc: 0.8,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_remo-con-barra-polea-baja",
    name: "Remo con Barra",
    equipment: "Polea Baja",
    type: "C",
    efc: 2.8,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_remo-con-mancuerna-kettlebell",
    name: "Remo con Mancuerna",
    equipment: "Kettlebell",
    type: "C",
    efc: 2.8,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_remo-con-mancuerna-polea",
    name: "Remo con Mancuerna",
    equipment: "Polea",
    type: "C",
    efc: 2.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_remo-m\xE1quina-convergente",
    name: "Remo",
    equipment: "M\xE1quina Convergente",
    type: "C",
    efc: 3,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_remo-m\xE1quina-smith",
    name: "Remo",
    equipment: "M\xE1quina Smith",
    type: "C",
    efc: 2.8,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_jal\xF3n-al-pecho-m\xE1quina-convergente",
    name: "Jal\xF3n al Pecho",
    equipment: "M\xE1quina Convergente",
    type: "C",
    efc: 2.5,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_jal\xF3n-al-pecho-barra-recta",
    name: "Jal\xF3n al Pecho",
    equipment: "Barra Recta",
    type: "C",
    efc: 2.8,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_dominadas-con-kettlebell",
    name: "Dominadas",
    equipment: "Con Kettlebell",
    type: "C",
    efc: 4.2,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_dominadas-con-disco",
    name: "Dominadas",
    equipment: "Con Disco",
    type: "C",
    efc: 4.2,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_dominadas-asistidas-m\xE1quina",
    name: "Dominadas",
    equipment: "Asistidas (M\xE1quina)",
    type: "C",
    efc: 2.5,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_pull-over-m\xE1quina",
    name: "Pull-Over",
    equipment: "M\xE1quina",
    type: "A",
    efc: 2.5,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_pull-over-barra",
    name: "Pull-Over",
    equipment: "Barra",
    type: "A",
    efc: 2.2,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_encogimientos-smith",
    name: "Encogimientos",
    equipment: "Smith",
    type: "A",
    efc: 2.5,
    ssc: 1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_encogimientos-polea",
    name: "Encogimientos",
    equipment: "Polea",
    type: "A",
    efc: 2.2,
    ssc: 0.8,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_encogimientos-kettlebell",
    name: "Encogimientos",
    equipment: "Kettlebell",
    type: "A",
    efc: 2.2,
    ssc: 0.8,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_encogimientos-peso-corporal",
    name: "Encogimientos",
    equipment: "Peso Corporal",
    type: "A",
    efc: 1.5,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_sentadilla-trasera-smith",
    name: "Sentadilla Trasera",
    equipment: "Smith",
    type: "C",
    efc: 3.5,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_sentadilla-trasera-polea-belt-squat",
    name: "Sentadilla Trasera",
    equipment: "Polea (Belt Squat)",
    type: "C",
    efc: 3.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_sentadilla-frontal-smith",
    name: "Sentadilla Frontal",
    equipment: "Smith",
    type: "C",
    efc: 3.8,
    ssc: 0.6,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_sentadilla-frontal-polea",
    name: "Sentadilla Frontal",
    equipment: "Polea",
    type: "C",
    efc: 3,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_sentadilla-goblet-kettlebell",
    name: "Sentadilla Goblet",
    equipment: "Kettlebell",
    type: "C",
    efc: 2.5,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_sentadilla-peso-corporal",
    name: "Sentadilla",
    equipment: "Peso Corporal",
    type: "C",
    efc: 2,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_sentadilla-b\xFAlgara-barra",
    name: "Sentadilla B\xFAlgara",
    equipment: "Barra",
    type: "C",
    efc: 3.8,
    ssc: 0.9,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_sentadilla-b\xFAlgara-kettlebell",
    name: "Sentadilla B\xFAlgara",
    equipment: "Kettlebell",
    type: "C",
    efc: 3.2,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_sentadilla-b\xFAlgara-peso-corporal",
    name: "Sentadilla B\xFAlgara",
    equipment: "Peso Corporal",
    type: "C",
    efc: 2.5,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_zancada-barra-trasera",
    name: "Zancada",
    equipment: "Barra (Trasera)",
    type: "C",
    efc: 3.8,
    ssc: 0.8,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_zancada-barra-frontal",
    name: "Zancada",
    equipment: "Barra (Frontal)",
    type: "C",
    efc: 3.5,
    ssc: 0.6,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_zancada-mancuernas",
    name: "Zancada",
    equipment: "Mancuernas",
    type: "C",
    efc: 3.2,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_zancada-kettlebell",
    name: "Zancada",
    equipment: "Kettlebell",
    type: "C",
    efc: 3,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_zancada-polea",
    name: "Zancada",
    equipment: "Polea",
    type: "C",
    efc: 2.8,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_zancada-peso-corporal",
    name: "Zancada",
    equipment: "Peso Corporal",
    type: "C",
    efc: 2.5,
    ssc: 0.4,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_subida-al-caj\xF3n-barra",
    name: "Subida al Caj\xF3n",
    equipment: "Barra",
    type: "C",
    efc: 3.5,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_subida-al-caj\xF3n-kettlebell",
    name: "Subida al Caj\xF3n",
    equipment: "Kettlebell",
    type: "C",
    efc: 3,
    ssc: 0.4,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_subida-al-caj\xF3n-peso-corporal",
    name: "Subida al Caj\xF3n",
    equipment: "Peso Corporal",
    type: "C",
    efc: 2.5,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_prensa-de-piernas-smith-pies-altos",
    name: "Prensa de Piernas",
    equipment: "Smith (Pies Altos)",
    type: "C",
    efc: 3,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Isquiosurales",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_prensa-de-piernas-m\xE1quina-convergente",
    name: "Prensa de Piernas",
    equipment: "M\xE1quina Convergente",
    type: "C",
    efc: 3,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Cu\xE1driceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Isquiosurales",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Sentadilla",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_extensiones-cu\xE1driceps-polea",
    name: "Extensiones Cu\xE1driceps",
    equipment: "Polea",
    type: "A",
    efc: 1.4,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_extensiones-cu\xE1driceps-peso-corporal-sissy",
    name: "Extensiones Cu\xE1driceps",
    equipment: "Peso Corporal (Sissy)",
    type: "A",
    efc: 2,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_peso-muerto-smith",
    name: "Peso Muerto",
    equipment: "Smith",
    type: "C",
    efc: 4.2,
    ssc: 1.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_peso-muerto-rumano-polea",
    name: "Peso Muerto Rumano",
    equipment: "Polea",
    type: "C",
    efc: 3.2,
    ssc: 0.8,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_peso-muerto-rumano-kettlebell",
    name: "Peso Muerto Rumano",
    equipment: "Kettlebell",
    type: "C",
    efc: 3.2,
    ssc: 1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_peso-muerto-rumano-mancuernas",
    name: "Peso Muerto Rumano",
    equipment: "Mancuernas",
    type: "C",
    efc: 3.5,
    ssc: 1.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_peso-muerto-rumano-peso-corporal",
    name: "Peso Muerto Rumano",
    equipment: "Peso Corporal",
    type: "C",
    efc: 2,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Erectores Espinales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Dorsales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Trapecio",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_buenos-d\xEDas-smith",
    name: "Buenos D\xEDas",
    equipment: "Smith",
    type: "C",
    efc: 3.5,
    ssc: 1.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_buenos-d\xEDas-peso-corporal",
    name: "Buenos D\xEDas",
    equipment: "Peso Corporal",
    type: "C",
    efc: 2,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gl\xFAteos",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Erectores Espinales",
        role: "stabilizer",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_hip-thrust-mancuernas",
    name: "Hip Thrust",
    equipment: "Mancuernas",
    type: "C",
    efc: 3,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_hip-thrust-kettlebell",
    name: "Hip Thrust",
    equipment: "Kettlebell",
    type: "C",
    efc: 2.8,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_hip-thrust-polea",
    name: "Hip Thrust",
    equipment: "Polea",
    type: "C",
    efc: 2.8,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_hip-thrust-peso-corporal",
    name: "Hip Thrust",
    equipment: "Peso Corporal",
    type: "C",
    efc: 2,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_curl-femoral-polea",
    name: "Curl Femoral",
    equipment: "Polea",
    type: "A",
    efc: 1.8,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gemelos",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Flexi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_curl-femoral-peso-corporal-nordic",
    name: "Curl Femoral",
    equipment: "Peso Corporal (Nordic)",
    type: "C",
    efc: 4.5,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Isquiosurales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Gemelos",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Flexi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_pull-through-polea-alta",
    name: "Pull-Through",
    equipment: "Polea Alta",
    type: "A",
    efc: 2.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Core",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_puente-de-gl\xFAteos-barra",
    name: "Puente de Gl\xFAteos",
    equipment: "Barra",
    type: "A",
    efc: 2.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_puente-de-gl\xFAteos-mancuerna",
    name: "Puente de Gl\xFAteos",
    equipment: "Mancuerna",
    type: "A",
    efc: 2.2,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_puente-de-gl\xFAteos-kettlebell",
    name: "Puente de Gl\xFAteos",
    equipment: "Kettlebell",
    type: "A",
    efc: 2.2,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_puente-de-gl\xFAteos-peso-corporal",
    name: "Puente de Gl\xFAteos",
    equipment: "Peso Corporal",
    type: "A",
    efc: 2,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gl\xFAteos",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Isquiosurales",
        role: "secondary",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Bisagra",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_pantorrilla-de-pie-smith",
    name: "Pantorrilla de Pie",
    equipment: "Smith",
    type: "A",
    efc: 1.5,
    ssc: 0.4,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gastrocnemio",
        role: "primary",
        activation: 1
      },
      {
        muscle: "S\xF3leo",
        role: "secondary",
        activation: 0.8
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_pantorrilla-de-pie-barra",
    name: "Pantorrilla de Pie",
    equipment: "Barra",
    type: "A",
    efc: 1.5,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gastrocnemio",
        role: "primary",
        activation: 1
      },
      {
        muscle: "S\xF3leo",
        role: "secondary",
        activation: 0.8
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_pantorrilla-de-pie-mancuerna",
    name: "Pantorrilla de Pie",
    equipment: "Mancuerna",
    type: "A",
    efc: 1.4,
    ssc: 0.4,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gastrocnemio",
        role: "primary",
        activation: 1
      },
      {
        muscle: "S\xF3leo",
        role: "secondary",
        activation: 0.8
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_pantorrilla-de-pie-peso-corporal",
    name: "Pantorrilla de Pie",
    equipment: "Peso Corporal",
    type: "A",
    efc: 1.2,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gastrocnemio",
        role: "primary",
        activation: 1
      },
      {
        muscle: "S\xF3leo",
        role: "secondary",
        activation: 0.8
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_pantorrilla-sentado-mancuerna",
    name: "Pantorrilla Sentado",
    equipment: "Mancuerna",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gastrocnemio",
        role: "primary",
        activation: 1
      },
      {
        muscle: "S\xF3leo",
        role: "secondary",
        activation: 0.8
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_pantorrilla-sentado-barra",
    name: "Pantorrilla Sentado",
    equipment: "Barra",
    type: "A",
    efc: 1.3,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gastrocnemio",
        role: "primary",
        activation: 1
      },
      {
        muscle: "S\xF3leo",
        role: "secondary",
        activation: 0.8
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_pantorrilla-polea",
    name: "Pantorrilla",
    equipment: "Polea",
    type: "A",
    efc: 1.4,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Gastrocnemio",
        role: "primary",
        activation: 1
      },
      {
        muscle: "S\xF3leo",
        role: "secondary",
        activation: 0.8
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_curl-con-barra-smith",
    name: "Curl con Barra",
    equipment: "Smith",
    type: "A",
    efc: 1.8,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-con-barra-polea-baja",
    name: "Curl con Barra",
    equipment: "Polea Baja",
    type: "A",
    efc: 1.6,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-con-mancuernas-kettlebell",
    name: "Curl con Mancuernas",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-martillo-polea",
    name: "Curl Martillo",
    equipment: "Polea",
    type: "A",
    efc: 1.5,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-martillo-kettlebell",
    name: "Curl Martillo",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-martillo-barra",
    name: "Curl Martillo",
    equipment: "Barra",
    type: "A",
    efc: 1.6,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-predicador-polea",
    name: "Curl Predicador",
    equipment: "Polea",
    type: "A",
    efc: 1.7,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-predicador-m\xE1quina",
    name: "Curl Predicador",
    equipment: "M\xE1quina",
    type: "A",
    efc: 1.6,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-concentrado-polea",
    name: "Curl Concentrado",
    equipment: "Polea",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-concentrado-kettlebell",
    name: "Curl Concentrado",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "B\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Braquiorradial",
        role: "secondary",
        activation: 0.5
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.4
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-21-barra",
    name: "Curl 21",
    equipment: "Barra",
    type: "A",
    efc: 1.8,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-inverso-barra",
    name: "Curl Inverso",
    equipment: "Barra",
    type: "A",
    efc: 1.6,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_curl-inverso-polea",
    name: "Curl Inverso",
    equipment: "Polea",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Dorsales",
        role: "primary",
        activation: 1
      },
      {
        muscle: "B\xEDceps",
        role: "secondary",
        activation: 0.6
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-franc\xE9s-polea",
    name: "Press Franc\xE9s",
    equipment: "Polea",
    type: "A",
    efc: 2,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-franc\xE9s-mancuerna",
    name: "Press Franc\xE9s",
    equipment: "Mancuerna",
    type: "A",
    efc: 2,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_press-franc\xE9s-kettlebell",
    name: "Press Franc\xE9s",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.8,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Antebrazo",
        role: "stabilizer",
        activation: 0.3
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_extensi\xF3n-tr\xEDceps-smith-trasnuca",
    name: "Extensi\xF3n Tr\xEDceps",
    equipment: "Smith (Trasnuca)",
    type: "A",
    efc: 2,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_extensi\xF3n-tr\xEDceps-mancuerna-unilateral",
    name: "Extensi\xF3n Tr\xEDceps",
    equipment: "Mancuerna (Unilateral)",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_extensi\xF3n-tr\xEDceps-kettlebell",
    name: "Extensi\xF3n Tr\xEDceps",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_patada-de-tr\xEDceps-polea",
    name: "Patada de Tr\xEDceps",
    equipment: "Polea",
    type: "A",
    efc: 1.3,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_patada-de-tr\xEDceps-kettlebell",
    name: "Patada de Tr\xEDceps",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_fondos-entre-bancos-peso-corporal",
    name: "Fondos entre Bancos",
    equipment: "Peso Corporal",
    type: "A",
    efc: 2,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Pectoral",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_fondos-entre-bancos-lastrados",
    name: "Fondos entre Bancos",
    equipment: "Lastrados",
    type: "A",
    efc: 2.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Pectoral",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_extensi\xF3n-tate-kettlebell",
    name: "Extensi\xF3n Tate",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.6,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Tr\xEDceps",
        role: "primary",
        activation: 1
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_jm-press-barra",
    name: "JM Press",
    equipment: "Barra",
    type: "A",
    efc: 2.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_jm-press-smith",
    name: "JM Press",
    equipment: "Smith",
    type: "A",
    efc: 2.3,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_crunch-peso-corporal",
    name: "Crunch",
    equipment: "Peso Corporal",
    type: "A",
    efc: 1.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_crunch-con-disco",
    name: "Crunch",
    equipment: "Con Disco",
    type: "A",
    efc: 1.8,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_crunch-polea-alta",
    name: "Crunch",
    equipment: "Polea Alta",
    type: "A",
    efc: 1.8,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_crunch-m\xE1quina",
    name: "Crunch",
    equipment: "M\xE1quina",
    type: "A",
    efc: 1.7,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_giros-rusos-disco",
    name: "Giros Rusos",
    equipment: "Disco",
    type: "A",
    efc: 1.8,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_giros-rusos-kettlebell",
    name: "Giros Rusos",
    equipment: "Kettlebell",
    type: "A",
    efc: 1.8,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_giros-rusos-peso-corporal",
    name: "Giros Rusos",
    equipment: "Peso Corporal",
    type: "A",
    efc: 1.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_plancha-peso-corporal",
    name: "Plancha",
    equipment: "Peso Corporal",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Anti-Extensi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_plancha-con-disco",
    name: "Plancha",
    equipment: "Con Disco",
    type: "A",
    efc: 1.6,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Anti-Extensi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_plancha-lateral-peso-corporal",
    name: "Plancha Lateral",
    equipment: "Peso Corporal",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Anti-Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_plancha-lateral-con-disco",
    name: "Plancha Lateral",
    equipment: "Con Disco",
    type: "A",
    efc: 1.6,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Anti-Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_dead-bug-peso-corporal",
    name: "Dead Bug",
    equipment: "Peso Corporal",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Anti-Extensi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_dead-bug-con-disco",
    name: "Dead Bug",
    equipment: "Con Disco",
    type: "A",
    efc: 1.3,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Anti-Extensi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_pallof-press-polea",
    name: "Pallof Press",
    equipment: "Polea",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Anti-Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_pallof-press-banda",
    name: "Pallof Press",
    equipment: "Banda",
    type: "A",
    efc: 1.5,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Anti-Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_elevaci\xF3n-de-piernas-peso-corporal",
    name: "Elevaci\xF3n de Piernas",
    equipment: "Peso Corporal",
    type: "C",
    efc: 2.5,
    ssc: 0.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Flexores de Cadera",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_elevaci\xF3n-de-piernas-con-disco",
    name: "Elevaci\xF3n de Piernas",
    equipment: "Con Disco",
    type: "C",
    efc: 2.8,
    ssc: 0.3,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Flexores de Cadera",
        role: "secondary",
        activation: 0.7
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_sit-up-peso-corporal",
    name: "Sit-Up",
    equipment: "Peso Corporal",
    type: "A",
    efc: 1.5,
    ssc: 0.4,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_sit-up-con-disco",
    name: "Sit-Up",
    equipment: "Con Disco",
    type: "A",
    efc: 1.8,
    ssc: 0.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Flexi\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_le\xF1ador-polea-alta",
    name: "Le\xF1ador",
    equipment: "Polea Alta",
    type: "C",
    efc: 2,
    ssc: 0.4,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_le\xF1ador-polea-baja",
    name: "Le\xF1ador",
    equipment: "Polea Baja",
    type: "C",
    efc: 2,
    ssc: 0.4,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Abdomen",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Abdomen",
        role: "secondary",
        activation: 0.8
      },
      {
        muscle: "Abdomen",
        role: "stabilizer",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_paseo-del-granjero-mancuernas",
    name: "Paseo del Granjero",
    equipment: "Mancuernas",
    type: "C",
    efc: 3.5,
    ssc: 1.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Antebrazo",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Trapecio",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_paseo-del-granjero-kettlebell",
    name: "Paseo del Granjero",
    equipment: "Kettlebell",
    type: "C",
    efc: 3.5,
    ssc: 1.5,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Antebrazo",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Trapecio",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_paseo-del-granjero-trap-bar",
    name: "Paseo del Granjero",
    equipment: "Trap Bar",
    type: "C",
    efc: 4,
    ssc: 1.8,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Antebrazo",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Trapecio",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_paseo-del-camarero-kettlebell",
    name: "Paseo del Camarero",
    equipment: "Kettlebell",
    type: "C",
    efc: 2.8,
    ssc: 1.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Antebrazo",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Trapecio",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_paseo-del-camarero-disco",
    name: "Paseo del Camarero",
    equipment: "Disco",
    type: "C",
    efc: 2.5,
    ssc: 1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Antebrazo",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Trapecio",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_suitcase-carry-kettlebell",
    name: "Suitcase Carry",
    equipment: "Kettlebell",
    type: "C",
    efc: 3,
    ssc: 1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Antebrazo",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Trapecio",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Anti-Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_suitcase-carry-mancuerna",
    name: "Suitcase Carry",
    equipment: "Mancuerna",
    type: "C",
    efc: 3,
    ssc: 1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Antebrazo",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Trapecio",
        role: "primary",
        activation: 0.9
      },
      {
        muscle: "Core",
        role: "secondary",
        activation: 0.7
      }
    ],
    category: "Hipertrofia",
    force: "Anti-Rotaci\xF3n",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_abducci\xF3n-de-cadera-polea",
    name: "Abducci\xF3n de Cadera",
    equipment: "Polea",
    type: "A",
    efc: 1.4,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_abducci\xF3n-de-cadera-banda",
    name: "Abducci\xF3n de Cadera",
    equipment: "Banda",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_abducci\xF3n-de-cadera-peso-corporal",
    name: "Abducci\xF3n de Cadera",
    equipment: "Peso Corporal",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_patada-de-gl\xFAteo-polea",
    name: "Patada de Gl\xFAteo",
    equipment: "Polea",
    type: "A",
    efc: 1.8,
    ssc: 0.1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_patada-de-gl\xFAteo-m\xE1quina",
    name: "Patada de Gl\xFAteo",
    equipment: "M\xE1quina",
    type: "A",
    efc: 1.6,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_patada-de-gl\xFAteo-peso-corporal",
    name: "Patada de Gl\xFAteo",
    equipment: "Peso Corporal",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Extensi\xF3n",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_hip-abduction-m\xE1quina",
    name: "Hip Abduction",
    equipment: "M\xE1quina",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_hip-adduction-m\xE1quina",
    name: "Hip Adduction",
    equipment: "M\xE1quina",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_hip-adduction-polea",
    name: "Hip Adduction",
    equipment: "Polea",
    type: "A",
    efc: 1.2,
    ssc: 0,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "lower",
    chain: "anterior"
  },
  {
    id: "ext_cargada-de-potencia-barra",
    name: "Cargada de Potencia",
    equipment: "Barra",
    type: "C",
    efc: 4.8,
    ssc: 1.8,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_cargada-de-potencia-kettlebell",
    name: "Cargada de Potencia",
    equipment: "Kettlebell",
    type: "C",
    efc: 3.5,
    ssc: 1.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_arrancada-barra",
    name: "Arrancada",
    equipment: "Barra",
    type: "C",
    efc: 4.8,
    ssc: 1.6,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_arrancada-kettlebell",
    name: "Arrancada",
    equipment: "Kettlebell",
    type: "C",
    efc: 3.5,
    ssc: 1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Otro",
    bodyPart: "full",
    chain: "anterior"
  },
  {
    id: "ext_push-press-mancuernas",
    name: "Push Press",
    equipment: "Mancuernas",
    type: "C",
    efc: 3.8,
    ssc: 1.2,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_push-press-kettlebell",
    name: "Push Press",
    equipment: "Kettlebell",
    type: "C",
    efc: 3.5,
    ssc: 1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Pectoral",
        role: "primary",
        activation: 1
      },
      {
        muscle: "Tr\xEDceps",
        role: "secondary",
        activation: 0.6
      },
      {
        muscle: "Deltoides Anterior",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_push-jerk-barra",
    name: "Push Jerk",
    equipment: "Barra",
    type: "C",
    efc: 4.5,
    ssc: 1.4,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_push-jerk-kettlebell",
    name: "Push Jerk",
    equipment: "Kettlebell",
    type: "C",
    efc: 3.8,
    ssc: 1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Empuje",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_high-pull-kettlebell",
    name: "High Pull",
    equipment: "Kettlebell",
    type: "C",
    efc: 3.5,
    ssc: 1,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  },
  {
    id: "ext_high-pull-mancuernas",
    name: "High Pull",
    equipment: "Mancuernas",
    type: "C",
    efc: 3.2,
    ssc: 0.8,
    cnc: 2.5,
    involvedMuscles: [
      {
        muscle: "Core",
        role: "primary",
        activation: 0.8
      },
      {
        muscle: "Erectores Espinales",
        role: "secondary",
        activation: 0.5
      }
    ],
    category: "Hipertrofia",
    force: "Tir\xF3n",
    bodyPart: "upper",
    chain: "anterior"
  }
];

// data/exerciseDatabaseMerged.ts
function normalizeExtended(ex) {
  const name = String(ex.name || "");
  const equipment = String(ex.equipment || "");
  const id = String(ex.id || `ext_${name.toLowerCase().replace(/\s+/g, "-").replace(/[^\w-]/g, "")}`);
  let type = String(ex.type || "Accesorio");
  if (type === "C" || type === "c")
    type = "B\xE1sico";
  else if (type === "A" || type === "a")
    type = "Accesorio";
  else if (type === "I" || type === "i")
    type = "Aislamiento";
  else if (!["B\xE1sico", "Accesorio", "Aislamiento"].includes(type))
    type = "Accesorio";
  const n = name.toLowerCase();
  let bodyPart = String(ex.bodyPart || "upper");
  let force = String(ex.force || "Otro");
  if (n.includes("sentadilla") || n.includes("prensa") || n.includes("zancada") || n.includes("pierna") || n.includes("cu\xE1driceps") || n.includes("gl\xFAteo") || n.includes("gemelo") || n.includes("femoral") || n.includes("isquio")) {
    bodyPart = "lower";
    if (n.includes("sentadilla") || n.includes("prensa"))
      force = "Sentadilla";
  }
  if (n.includes("peso muerto") || n.includes("remo") || n.includes("hip thrust") || n.includes("buenos d\xEDas") || n.includes("rumanian") || n.includes("rdl")) {
    bodyPart = "lower";
    force = "Bisagra";
  }
  if (n.includes("press") || n.includes("empuje") || n.includes("flexi\xF3n") || n.includes("fondo"))
    force = "Empuje";
  if (n.includes("dominada") || n.includes("remo") || n.includes("jal\xF3n") || n.includes("tir\xF3n") || n.includes("curl"))
    force = "Tir\xF3n";
  const primary = ex.involvedMuscles || [];
  const involvedMuscles = primary.length > 0 ? primary.map((m) => ({ muscle: m.muscle, role: m.role || "primary", activation: m.activation ?? 1 })) : inferInvolvedMuscles(name, equipment, force, bodyPart);
  const displayName = equipment && equipment.trim() && equipment !== "Otro" ? `${name} (${equipment})` : name;
  return {
    id,
    name: displayName,
    description: String(ex.description || ""),
    involvedMuscles,
    subMuscleGroup: String(ex.subMuscleGroup || ""),
    category: String(ex.category || "Hipertrofia"),
    type,
    equipment: String(ex.equipment || "Otro"),
    force,
    bodyPart,
    chain: String(ex.chain || "anterior"),
    setupTime: typeof ex.setupTime === "number" ? ex.setupTime : 3,
    technicalDifficulty: typeof ex.technicalDifficulty === "number" ? ex.technicalDifficulty : 5,
    transferability: typeof ex.transferability === "number" ? ex.transferability : 6,
    injuryRisk: typeof ex.injuryRisk === "object" && ex.injuryRisk !== null ? ex.injuryRisk : { level: 5, details: "" },
    efc: typeof ex.efc === "number" ? ex.efc : 2.5,
    cnc: typeof ex.cnc === "number" ? ex.cnc : 2.5,
    ssc: typeof ex.ssc === "number" ? ex.ssc : 0.2
  };
}
var extendedExercises = Array.isArray(exerciseDatabaseExtended_default) ? exerciseDatabaseExtended_default.map(normalizeExtended) : [];
function enrichWithOperationalData(ex) {
  const hasCore = ex.involvedMuscles?.some(
    (m) => ["Core", "Abdomen", "Espalda Baja", "Recto Abdominal", "Transverso Abdominal"].includes(m.muscle) && (m.activation || 0) >= 0.3
  );
  const isCompound = ex.type === "B\xE1sico" && ["Barra", "Peso Corporal"].includes(ex.equipment || "");
  const isPull = ex.force === "Tir\xF3n" || ex.force === "Bisagra";
  const isHeavyPull = isPull && (ex.equipment === "Barra" || ex.subMuscleGroup?.toLowerCase().includes("dorsal"));
  return {
    ...ex,
    averageRestSeconds: ex.averageRestSeconds ?? (ex.type === "B\xE1sico" ? 120 : ex.type === "Aislamiento" ? 60 : 90),
    coreInvolvement: ex.coreInvolvement ?? (hasCore ? isCompound ? "high" : "medium" : "low"),
    bracingRecommended: ex.bracingRecommended ?? (isCompound && (ex.force === "Sentadilla" || ex.force === "Bisagra" || ex.force === "Empuje")),
    strapsRecommended: ex.strapsRecommended ?? (isHeavyPull && (ex.name?.toLowerCase().includes("peso muerto") || ex.name?.toLowerCase().includes("remo") || ex.name?.toLowerCase().includes("dominada") || ex.name?.toLowerCase().includes("jal\xF3n"))),
    bodybuildingScore: ex.bodybuildingScore ?? (ex.category === "Hipertrofia" ? ex.type === "B\xE1sico" ? 8 : ex.type === "Aislamiento" ? 7 : 7.5 : 6)
  };
}
var existingIds = new Set(DETAILED_EXERCISE_LIST.map((e) => e.id));
var merged = [...DETAILED_EXERCISE_LIST];
for (const ex of extendedExercises) {
  if (!existingIds.has(ex.id)) {
    existingIds.add(ex.id);
    merged.push(ex);
  }
}
function removeExactDuplicates(list2) {
  const seen = /* @__PURE__ */ new Map();
  for (const ex of list2) {
    const key = JSON.stringify({
      id: ex.id,
      name: ex.name,
      involvedMuscles: ex.involvedMuscles?.map((m) => ({ muscle: m.muscle, role: m.role, activation: m.activation })).sort((a, b) => a.muscle.localeCompare(b.muscle)),
      equipment: ex.equipment,
      type: ex.type
    });
    if (!seen.has(key))
      seen.set(key, ex);
  }
  return Array.from(seen.values());
}
function removeDuplicateNames(list2) {
  const seenNames = /* @__PURE__ */ new Set();
  const aliasMap2 = /* @__PURE__ */ new Map();
  const idToExercise = /* @__PURE__ */ new Map();
  const result = [];
  for (const ex of list2) {
    const nameKey = ex.name.toLowerCase().trim();
    if (seenNames.has(nameKey)) {
      const first = result.find((e) => e.name.toLowerCase().trim() === nameKey);
      if (first)
        aliasMap2.set(ex.id, first.id);
      continue;
    }
    seenNames.add(nameKey);
    idToExercise.set(ex.id, ex);
    result.push(ex);
  }
  return { deduplicated: result, aliasMap: aliasMap2 };
}
var exactDeduped = removeExactDuplicates(merged);
var { deduplicated: nameDeduped, aliasMap } = removeDuplicateNames(exactDeduped);
var FULL_EXERCISE_LIST = nameDeduped.map(enrichWithOperationalData);

// scripts/auditExerciseDuplicates.ts
import { createWriteStream } from "fs";
import { join } from "path";
function normalize(str) {
  return str.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/\s+/g, " ").trim().replace(/\s*\([^)]*\)\s*/g, " ").replace(/\s+/g, " ").trim();
}
function baseName(name) {
  const n = normalize(name);
  return n.replace(/\s*(barra|mancuerna|polea|maquina|peso corporal|banda|kettlebell|cable)\s*$/i, "").replace(/\s+(en|con|de)\s+(barra|mancuerna|polea|maquina)\s*$/i, "").trim();
}
function wordOverlap(a, b) {
  const wordsA = new Set(normalize(a).split(/\s+/).filter(Boolean));
  const wordsB = new Set(normalize(b).split(/\s+/).filter(Boolean));
  const intersection = [...wordsA].filter((w) => wordsB.has(w)).length;
  const union = (/* @__PURE__ */ new Set([...wordsA, ...wordsB])).size;
  return union === 0 ? 0 : intersection / union;
}
function levenshteinSimilarity(a, b) {
  const na = normalize(a);
  const nb = normalize(b);
  if (na === nb)
    return 1;
  const matrix = [];
  for (let i = 0; i <= na.length; i++)
    matrix[i] = [i];
  for (let j = 0; j <= nb.length; j++)
    matrix[0][j] = j;
  for (let i = 1; i <= na.length; i++) {
    for (let j = 1; j <= nb.length; j++) {
      const cost = na[i - 1] === nb[j - 1] ? 0 : 1;
      matrix[i][j] = Math.min(
        matrix[i - 1][j] + 1,
        matrix[i][j - 1] + 1,
        matrix[i - 1][j - 1] + cost
      );
    }
  }
  const maxLen = Math.max(na.length, nb.length);
  return 1 - matrix[na.length][nb.length] / maxLen;
}
var list = FULL_EXERCISE_LIST;
var report = [];
var duplicateGroups = [];
report.push("=".repeat(80));
report.push("AUDITOR\xCDA DE EJERCICIOS DUPLICADOS - KPKN FIT");
report.push("=".repeat(80));
report.push(`Total de ejercicios: ${list.length}`);
report.push("");
var byExactName = /* @__PURE__ */ new Map();
for (const ex of list) {
  const key = ex.name.toLowerCase().trim();
  if (!byExactName.has(key))
    byExactName.set(key, []);
  byExactName.get(key).push(ex);
}
var exactDupes = [...byExactName.entries()].filter(([, arr]) => arr.length > 1);
if (exactDupes.length > 0) {
  report.push("--- 1. DUPLICADOS EXACTOS (mismo nombre) ---");
  for (const [name, arr] of exactDupes) {
    report.push(`  "${name}": ${arr.length} entradas`);
    duplicateGroups.push({
      type: "exact",
      exercises: arr.map((e) => ({ id: e.id, name: e.name, equipment: e.equipment })),
      suggestedCanonical: arr[0].name
    });
    for (const e of arr)
      report.push(`    - ${e.id} | ${e.name}`);
    report.push("");
  }
}
var byNormalized = /* @__PURE__ */ new Map();
for (const ex of list) {
  const key = normalize(ex.name);
  if (!key)
    continue;
  if (!byNormalized.has(key))
    byNormalized.set(key, []);
  byNormalized.get(key).push(ex);
}
var normalizedDupes = [...byNormalized.entries()].filter(([, arr]) => arr.length > 1);
var newNormalized = normalizedDupes.filter(([, arr]) => new Set(arr.map((e) => e.name)).size > 1);
if (newNormalized.length > 0) {
  report.push("--- 2. DUPLICADOS POR NOMBRE NORMALIZADO ---");
  for (const [norm, arr] of newNormalized) {
    const names = [...new Set(arr.map((e) => e.name))];
    if (names.length <= 1)
      continue;
    report.push(`  Normalized: "${norm}" (${arr.length} entradas)`);
    duplicateGroups.push({
      type: "normalized",
      exercises: arr.map((e) => ({ id: e.id, name: e.name, equipment: e.equipment })),
      suggestedCanonical: arr[0].name
    });
    for (const e of arr)
      report.push(`    - ${e.id} | ${e.name}`);
    report.push("");
  }
}
var checked = /* @__PURE__ */ new Set();
var semanticDupes = [];
for (let i = 0; i < list.length; i++) {
  for (let j = i + 1; j < list.length; j++) {
    const a = list[i];
    const b = list[j];
    const pairKey = [a.id, b.id].sort().join("|");
    if (checked.has(pairKey))
      continue;
    checked.add(pairKey);
    const baseA = baseName(a.name);
    const baseB = baseName(b.name);
    if (baseA.length < 4 || baseB.length < 4)
      continue;
    const wordSim = wordOverlap(a.name, b.name);
    const levSim = levenshteinSimilarity(baseA, baseB);
    const score = 0.6 * wordSim + 0.4 * levSim;
    if (score >= 0.85 && a.id !== b.id) {
      semanticDupes.push({ a, b, score });
    }
  }
}
if (semanticDupes.length > 0) {
  report.push("--- 3. POSIBLES DUPLICADOS SEM\xC1NTICOS (similitud >= 85%) ---");
  const seen = /* @__PURE__ */ new Set();
  for (const { a, b, score } of semanticDupes) {
    const key = [a.id, b.id].sort().join("|");
    if (seen.has(key))
      continue;
    seen.add(key);
    report.push(`  Score ${(score * 100).toFixed(0)}%: "${a.name}" <-> "${b.name}"`);
    report.push(`    IDs: ${a.id} | ${b.id}`);
    duplicateGroups.push({
      type: "semantic",
      exercises: [
        { id: a.id, name: a.name, equipment: a.equipment },
        { id: b.id, name: b.name, equipment: b.equipment }
      ],
      suggestedCanonical: a.name
    });
    report.push("");
  }
}
report.push("=".repeat(80));
report.push("RESUMEN");
report.push("=".repeat(80));
report.push(`Duplicados exactos: ${exactDupes.length} grupos`);
report.push(`Duplicados por normalizaci\xF3n: ${newNormalized.length} grupos`);
report.push(`Posibles duplicados sem\xE1nticos: ${semanticDupes.length} pares`);
var totalDupeExercises = [
  ...exactDupes.flatMap(([, arr]) => arr),
  ...newNormalized.flatMap(([, arr]) => arr),
  ...semanticDupes.flatMap(({ a, b }) => [a, b])
];
var uniqueDupeIds = new Set(totalDupeExercises.map((e) => e.id));
report.push(`Ejercicios afectados (IDs \xFAnicos): ${uniqueDupeIds.size}`);
report.push("");
var outPath = join(process.cwd(), "scripts", "audit-exercise-duplicates-report.txt");
var ws = createWriteStream(outPath, { encoding: "utf-8" });
ws.write(report.join("\n"));
ws.end();
console.log(report.join("\n"));
console.log(`
Reporte guardado en: ${outPath}`);
