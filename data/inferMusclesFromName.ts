// data/inferMusclesFromName.ts
// Infiere involvedMuscles desde nombre y equipment para ejercicios del JSON extendido
// Evita el fallback "General" en el conteo de volumen

type MuscleEntry = { muscle: string; role: 'primary' | 'secondary' | 'stabilizer'; activation: number };

export function inferInvolvedMuscles(
  name: string,
  equipment: string,
  force: string,
  bodyPart: string
): MuscleEntry[] {
  const n = name.toLowerCase();
  const eq = (equipment || '').toLowerCase();

  // --- PIERNA: Sentadillas ---
  if (n.includes('sentadilla')) {
    if (n.includes('frontal') || n.includes('front squat')) {
      return [
        { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
        { muscle: 'Core', role: 'secondary', activation: 0.8 },
        { muscle: 'Glúteos', role: 'secondary', activation: 0.5 },
        { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.5 },
      ];
    }
    if (n.includes('zercher')) {
      return [
        { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
        { muscle: 'Core', role: 'primary', activation: 0.9 },
        { muscle: 'Glúteos', role: 'secondary', activation: 0.6 },
        { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.6 },
      ];
    }
    if (n.includes('hack')) {
      return [
        { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
        { muscle: 'Glúteos', role: 'secondary', activation: 0.4 },
        { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.3 },
      ];
    }
    if (n.includes('goblet')) {
      return [
        { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
        { muscle: 'Core', role: 'secondary', activation: 0.7 },
        { muscle: 'Glúteos', role: 'secondary', activation: 0.5 },
      ];
    }
    if (n.includes('búlgara') || n.includes('bulgar')) {
      return [
        { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
        { muscle: 'Glúteos', role: 'primary', activation: 0.9 },
        { muscle: 'Isquiosurales', role: 'secondary', activation: 0.5 },
      ];
    }
    // Sentadilla trasera / back squat
    return [
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.6 },
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.5 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.4 },
    ];
  }

  // --- PIERNA: Prensa, Zancada, Subida ---
  if (n.includes('prensa') && (n.includes('pierna') || n.includes('piernas'))) {
    return [
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.6 },
      { muscle: 'Isquiosurales', role: 'stabilizer', activation: 0.3 },
    ];
  }
  if (n.includes('zancada') || n.includes('lunge')) {
    return [
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'primary', activation: 0.9 },
      { muscle: 'Isquiosurales', role: 'secondary', activation: 0.5 },
    ];
  }
  if (n.includes('subida') && n.includes('cajón')) {
    return [
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.7 },
    ];
  }
  if (n.includes('extensión') && (n.includes('cuádriceps') || n.includes('cuadriceps'))) {
    return [
      { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
    ];
  }

  // --- PIERNA: Peso muerto, RDL, Bisagra ---
  if (n.includes('peso muerto') || n.includes('deadlift')) {
    return [
      { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 },
      { muscle: 'Isquiosurales', role: 'primary', activation: 0.9 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.7 },
      { muscle: 'Dorsales', role: 'secondary', activation: 0.5 },
      { muscle: 'Trapecio', role: 'stabilizer', activation: 0.5 },
    ];
  }
  if (n.includes('rumano') || n.includes('rdl') || n.includes('stiff')) {
    return [
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.7 },
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.6 },
    ];
  }
  if (n.includes('buenos días') || n.includes('good morning')) {
    return [
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.7 },
      { muscle: 'Erectores Espinales', role: 'stabilizer', activation: 0.6 },
    ];
  }
  if (n.includes('rack pull')) {
    return [
      { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'primary', activation: 0.9 },
      { muscle: 'Dorsales', role: 'secondary', activation: 0.6 },
      { muscle: 'Bíceps', role: 'secondary', activation: 0.5 },
    ];
  }

  // --- PIERNA: Hip thrust, Glúteos ---
  if (n.includes('hip thrust') || n.includes('empuje cadera')) {
    return [
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Isquiosurales', role: 'secondary', activation: 0.5 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.4 },
    ];
  }
  if (n.includes('puente') && n.includes('glúteo')) {
    return [
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Isquiosurales', role: 'secondary', activation: 0.4 },
    ];
  }
  if (n.includes('pull-through') || n.includes('pull through')) {
    return [
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Isquiosurales', role: 'secondary', activation: 0.6 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.5 },
    ];
  }

  // --- PIERNA: Curl femoral, Isquios ---
  if (n.includes('curl') && (n.includes('femoral') || n.includes('isquio') || n.includes('nórdico') || n.includes('nordic'))) {
    return [
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 },
      { muscle: 'Gemelos', role: 'secondary', activation: 0.4 },
    ];
  }
  if (n.includes('ghr') || n.includes('glute ham')) {
    return [
      { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.6 },
      { muscle: 'Gemelos', role: 'stabilizer', activation: 0.4 },
    ];
  }
  if (n.includes('hiperextensión') || n.includes('hiperextension')) {
    return [
      { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.6 },
      { muscle: 'Isquiosurales', role: 'secondary', activation: 0.5 },
    ];
  }
  if (n.includes('reverse hyper')) {
    return [
      { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 },
      { muscle: 'Glúteos', role: 'secondary', activation: 0.7 },
    ];
  }

  // --- PIERNA: Gemelos ---
  if (n.includes('gemelo') || n.includes('pantorrilla') || n.includes('calf') || n.includes('sóleo') || n.includes('soleo')) {
    return [
      { muscle: 'Gastrocnemio', role: 'primary', activation: 1.0 },
      { muscle: 'Sóleo', role: 'secondary', activation: 0.8 },
    ];
  }

  // --- PIERNA: Kettlebell swing ---
  if (n.includes('kettlebell swing') || n.includes('swing')) {
    return [
      { muscle: 'Glúteos', role: 'primary', activation: 1.0 },
      { muscle: 'Isquiosurales', role: 'secondary', activation: 0.6 },
      { muscle: 'Core', role: 'secondary', activation: 0.6 },
      { muscle: 'Deltoides Anterior', role: 'stabilizer', activation: 0.4 },
    ];
  }

  // --- PECHO / EMPUJE (solo grupo "Pectoral", sin porciones) ---
  if (n.includes('press') && (n.includes('banca') || n.includes('pecho') || n.includes('bench'))) {
    return [
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 },
      { muscle: 'Tríceps', role: 'secondary', activation: 0.6 },
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.6 },
      { muscle: 'Trapecio', role: 'stabilizer', activation: 0.3 },
    ];
  }
  if (n.includes('apertura') || n.includes('fly') || n.includes('cruces')) {
    return [
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 },
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.4 },
    ];
  }
  if (n.includes('flexión') || n.includes('flexion') || n.includes('push-up') || n.includes('push up')) {
    return [
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 },
      { muscle: 'Tríceps', role: 'secondary', activation: 0.6 },
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.5 },
      { muscle: 'Abdomen', role: 'stabilizer', activation: 0.5 },
    ];
  }
  if (n.includes('fondo') && !n.includes('entre bancos')) {
    return [
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 },
      { muscle: 'Tríceps', role: 'secondary', activation: 0.8 },
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.6 },
    ];
  }

  // --- ESPALDA / TIRÓN (Dorsales, Trapecio; sin porciones) ---
  if (n.includes('dominada') || n.includes('pull-up') || n.includes('chin-up')) {
    return [
      { muscle: 'Dorsales', role: 'primary', activation: 1.0 },
      { muscle: 'Bíceps', role: 'secondary', activation: 0.6 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.4 },
    ];
  }
  if (n.includes('remo') || n.includes('row')) {
    return [
      { muscle: 'Dorsales', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'secondary', activation: 0.7 },
      { muscle: 'Bíceps', role: 'secondary', activation: 0.5 },
    ];
  }
  if (n.includes('jalón') || n.includes('pulldown') || n.includes('lat pulldown')) {
    return [
      { muscle: 'Dorsales', role: 'primary', activation: 1.0 },
      { muscle: 'Bíceps', role: 'secondary', activation: 0.6 },
    ];
  }
  if (n.includes('face pull') || n.includes('tirón a la cara')) {
    return [
      { muscle: 'Deltoides Posterior', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'secondary', activation: 0.7 },
    ];
  }

  // --- HOMBROS ---
  if (n.includes('press') && (n.includes('militar') || n.includes('hombro') || n.includes('overhead') || n.includes('ohp'))) {
    return [
      { muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 },
      { muscle: 'Tríceps', role: 'secondary', activation: 0.7 },
      { muscle: 'Deltoides Lateral', role: 'secondary', activation: 0.5 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.5 },
    ];
  }
  if (n.includes('elevación') && (n.includes('lateral') || n.includes('laterales'))) {
    return [
      { muscle: 'Deltoides Lateral', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'stabilizer', activation: 0.4 },
    ];
  }
  if (n.includes('elevación') && (n.includes('frontal') || n.includes('front'))) {
    return [
      { muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 },
      { muscle: 'Pectoral', role: 'secondary', activation: 0.4 },
    ];
  }

  // --- BÍCEPS ---
  if (n.includes('curl') && (n.includes('bíceps') || n.includes('biceps') || n.includes('martillo') || n.includes('predicador') || n.includes('concentrado') || n.includes('araña') || n.includes('inclinado') || n.includes('polea') || n.includes('arrastre'))) {
    return [
      { muscle: 'Bíceps', role: 'primary', activation: 1.0 },
      { muscle: 'Braquiorradial', role: 'secondary', activation: 0.5 },
      { muscle: 'Antebrazo', role: 'stabilizer', activation: 0.4 },
    ];
  }

  // --- TRÍCEPS ---
  if (n.includes('extensión') && n.includes('tríceps')) {
    return [
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 },
    ];
  }
  if (n.includes('press francés') || n.includes('skullcrusher') || n.includes('skull crusher')) {
    return [
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 },
      { muscle: 'Antebrazo', role: 'stabilizer', activation: 0.3 },
    ];
  }
  if (n.includes('extensión trasnuca') || n.includes('overhead extension')) {
    return [
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 },
    ];
  }
  if (n.includes('patada') && n.includes('tríceps')) {
    return [
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 },
    ];
  }
  if (n.includes('fondos entre bancos') || n.includes('bench dip')) {
    return [
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 },
      { muscle: 'Pectoral', role: 'secondary', activation: 0.5 },
    ];
  }
  if (n.includes('extensión tate') || n.includes('tate press')) {
    return [
      { muscle: 'Tríceps', role: 'primary', activation: 1.0 },
    ];
  }

  // --- ANTEBRAZO ---
  if (n.includes('curl') && n.includes('muñeca')) {
    return [
      { muscle: 'Antebrazo', role: 'primary', activation: 1.0 },
    ];
  }

  // --- CORE / ABDOMINALES (solo grupo Abdomen) ---
  if (n.includes('plancha') || n.includes('plank')) {
    return [
      { muscle: 'Abdomen', role: 'primary', activation: 1.0 },
    ];
  }
  if (n.includes('rueda abdominal') || n.includes('ab wheel')) {
    return [
      { muscle: 'Abdomen', role: 'primary', activation: 1.0 },
      { muscle: 'Dorsales', role: 'stabilizer', activation: 0.5 },
    ];
  }
  if (n.includes('press pallof') || n.includes('pallof')) {
    return [
      { muscle: 'Abdomen', role: 'primary', activation: 1.0 },
    ];
  }
  if (n.includes('leñador') || n.includes('woodchop')) {
    return [
      { muscle: 'Abdomen', role: 'primary', activation: 1.0 },
    ];
  }
  if (n.includes('elevación') && n.includes('pierna')) {
    return [
      { muscle: 'Abdomen', role: 'primary', activation: 1.0 },
    ];
  }
  if (n.includes('crunch') || n.includes('abdominal')) {
    return [
      { muscle: 'Abdomen', role: 'primary', activation: 1.0 },
    ];
  }

  // --- CARRY / STRONGMAN ---
  if (n.includes('paseo') || n.includes('carry') || n.includes('granjero') || n.includes('farmers')) {
    return [
      { muscle: 'Antebrazo', role: 'primary', activation: 1.0 },
      { muscle: 'Trapecio', role: 'primary', activation: 0.9 },
      { muscle: 'Core', role: 'secondary', activation: 0.7 },
    ];
  }
  if (n.includes('yoke') || n.includes('atlas') || n.includes('tire') || n.includes('neumático') || n.includes('sandbag') || n.includes('barril') || n.includes('keg')) {
    return [
      { muscle: 'Erectores Espinales', role: 'primary', activation: 1.0 },
      { muscle: 'Core', role: 'primary', activation: 0.9 },
      { muscle: 'Trapecio', role: 'secondary', activation: 0.7 },
    ];
  }
  if (n.includes('log press')) {
    return [
      { muscle: 'Deltoides Anterior', role: 'primary', activation: 1.0 },
      { muscle: 'Tríceps', role: 'secondary', activation: 0.7 },
      { muscle: 'Core', role: 'stabilizer', activation: 0.6 },
    ];
  }

  // --- MOVILIDAD (activation baja) ---
  if (n.includes('estiramiento') || n.includes('movilidad') || n.includes('rotación') || n.includes('cat cow') || n.includes('thread the needle') || n.includes('dislocat')) {
    if (n.includes('cadera') || n.includes('hip') || eq.includes('cadera')) {
      return [{ muscle: 'Glúteos', role: 'primary', activation: 0.5 }];
    }
    if (n.includes('hombro') || n.includes('shoulder')) {
      return [{ muscle: 'Deltoides Anterior', role: 'primary', activation: 0.5 }];
    }
    if (n.includes('tobillo') || n.includes('ankle')) {
      return [{ muscle: 'Gemelos', role: 'primary', activation: 0.5 }];
    }
    if (n.includes('muñeca') || n.includes('wrist')) {
      return [{ muscle: 'Antebrazo', role: 'primary', activation: 0.5 }];
    }
    if (n.includes('cuádriceps') || n.includes('quad')) {
      return [{ muscle: 'Cuádriceps', role: 'primary', activation: 0.5 }];
    }
    return [
      { muscle: 'Erectores Espinales', role: 'primary', activation: 0.5 },
      { muscle: 'Core', role: 'secondary', activation: 0.4 },
    ];
  }

  // Fallback por bodyPart/force si no hay match
  if (bodyPart === 'lower') {
    if (force === 'Sentadilla') {
      return [
        { muscle: 'Cuádriceps', role: 'primary', activation: 1.0 },
        { muscle: 'Glúteos', role: 'secondary', activation: 0.6 },
      ];
    }
    if (force === 'Bisagra') {
      return [
        { muscle: 'Isquiosurales', role: 'primary', activation: 1.0 },
        { muscle: 'Glúteos', role: 'secondary', activation: 0.6 },
      ];
    }
  }
  if (bodyPart === 'upper' && (n.includes('press') || n.includes('empuje'))) {
    return [
      { muscle: 'Pectoral', role: 'primary', activation: 1.0 },
      { muscle: 'Tríceps', role: 'secondary', activation: 0.6 },
      { muscle: 'Deltoides Anterior', role: 'secondary', activation: 0.5 },
    ];
  }
  if (bodyPart === 'upper' && (n.includes('remo') || n.includes('tirón') || n.includes('curl'))) {
    return [
      { muscle: 'Dorsales', role: 'primary', activation: 1.0 },
      { muscle: 'Bíceps', role: 'secondary', activation: 0.6 },
    ];
  }

  // Último recurso: Core genérico (mejor que "General")
  return [
    { muscle: 'Core', role: 'primary', activation: 0.8 },
    { muscle: 'Erectores Espinales', role: 'secondary', activation: 0.5 },
  ];
}
