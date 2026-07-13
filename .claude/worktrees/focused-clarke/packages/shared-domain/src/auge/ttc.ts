
import { AugeExerciseMetrics } from '@kpkn/shared-types';

const TTC_MAX = 5.0;

/** Valor base por biomecánica */
function getBaseTTC(info: any): number {
  if (!info) return 1.0;
  const name = (info.name || '').toLowerCase();
  const cat = info.category;
  const force = info.force;
  const type = info.type;

  // Balístico/Olímpico: 3.0
  if (cat === 'Potencia' && force === 'Salto') return 3.0;
  if (name.includes('snatch') || name.includes('arrancada') || name.includes('arranque')) return 3.0;
  if (name.includes('clean') && (name.includes('power') || name.includes('cargada'))) return 3.0;
  if (name.includes('jerk') || name.includes('envión')) return 3.0;
  if (name.includes('salto') || name.includes('jump') || name.includes('box')) return 3.0;
  if (cat === 'Pliometría') return 3.0;

  // Compuesto (multiarticular): 2.0
  if (type === 'Básico' || info.tier === 'T1') return 2.0;
  if (type === 'Accesorio' && (info.bodyPart === 'full' || info.bodyPart === 'lower')) return 2.0;

  // Aislamiento (monoarticular): 1.0
  return 1.0;
}

/** Modificador de equipamiento */
function getEquipmentMod(info: any): number {
  if (!info) return 1.0;
  const eq = info.equipment;
  if (eq === 'Máquina' || eq === 'Polea') return 0.8;
  if (eq === 'Peso Corporal' || eq === 'Banda' || eq === 'TRX' || eq === 'Slider') return 1.0;
  if (
    eq === 'Barra' ||
    eq === 'Mancuerna' ||
    eq === 'Kettlebell' ||
    eq === 'Disco' ||
    eq === 'Saco de arena' ||
    eq === 'Trineo' ||
    eq === 'Arnés' ||
    eq === 'Piedra' ||
    eq === 'Neumático' ||
    eq === 'Tronco' ||
    eq === 'Escudo' ||
    eq === 'Eje'
  )
    return 1.2;
  return 1.0;
}

/** Modificador de contracción (isométrico, dinámico, excéntrico, pliométrico) */
function getContractionMod(info: any, setName?: string): number {
  if (!info) return 1.0;
  const name = ((info.name || '') + ' ' + (setName || '')).toLowerCase();
  const cat = info.category;
  const force = info.force;

  // Pliométrico: 2.0
  if (cat === 'Pliometría' || force === 'Salto') return 2.0;
  if (name.includes('salto') || name.includes('jump') || name.includes('bound') || name.includes('hop'))
    return 2.0;

  // Sobrecarga excéntrica: 1.8
  if (name.includes('nórdico') || name.includes('nordic') || name.includes('curl nórdico')) return 1.8;
  if (name.includes('excéntrico') || name.includes('eccentric') || name.includes('excéntrica'))
    return 1.8;

  // Isométrico: 0.5
  if (cat === 'Estabilidad' || cat === 'Movilidad') {
    if (name.includes('plancha') || name.includes('plank')) return 0.5;
    if (name.includes('hold') || name.includes('isométric') || name.includes('pared') || name.includes('wall sit'))
      return 0.5;
  }
  if (name.includes('plancha') || name.includes('plank')) return 0.5;
  if (name.includes('wall sit') || name.includes('sentadilla isométrica')) return 0.5;
  if (name.includes('isométric')) return 0.5;

  // Dinámico estándar: 1.0
  return 1.0;
}

/**
 * Calcula el TTC (Tendon & Tissue Cost) para un ejercicio.
 * Si info.ttc existe en DB, se usa como override (validado y topado).
 */
export function calculateTTC(
  info: any | undefined,
  setName?: string
): number {
  if (!info) return 0;
  if (info.ttc != null && info.ttc > 0) {
    return Math.min(TTC_MAX, info.ttc);
  }
  const base = getBaseTTC(info);
  const equipmentMod = getEquipmentMod(info);
  const contractionMod = getContractionMod(info, setName);
  return Math.min(TTC_MAX, base * equipmentMod * contractionMod);
}

/**
 * Calcula el drenaje tendinoso por set, distribuido entre las baterías articulares.
 */
export function calculateSetTendonDrain(
  set: any,
  info: any | undefined,
  articularWeights: Record<string, number>,
  getEffectiveRPE: (set: any) => number
): Record<string, number> {
  const ttc = calculateTTC(info, (set as any).exerciseName as string);
  const result: Record<string, number> = {};
  
  // Inicializar pesos
  for (const id in articularWeights) { result[id] = 0; }

  if (ttc <= 0) return result;

  const reps = (set as any).completedReps ?? (set as any).targetReps ?? 10;
  const rpe = getEffectiveRPE(set);
  const intensityMult = rpe >= 10 ? 1.4 : rpe >= 8 ? 1.0 : rpe >= 6 ? 0.7 : 0.4;

  // Drenaje base = TTC × reps (normalizado) × intensidad
  const repsFactor = Math.min(1.5, 0.1 + reps / 15);
  const baseDrain = ttc * repsFactor * intensityMult * 2.5; // escalado para batería 0-100

  const totalWeight = Object.values(articularWeights).reduce((a, b) => a + b, 0) || 1;
  for (const id in result) {
    const w = articularWeights[id] || 0;
    result[id] = (baseDrain * (w / totalWeight));
  }
  return result;
}
