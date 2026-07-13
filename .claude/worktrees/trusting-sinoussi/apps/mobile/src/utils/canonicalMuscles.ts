// apps/mobile/src/utils/canonicalMuscles.ts
// Normalizador de músculos canónicos — Ported from PWA
import type { CanonicalMuscle } from '@kpkn/shared-types';

const CANONICAL_MUSCLES: CanonicalMuscle[] = [
  'Pectorales', 'Dorsales', 'Trapecio', 'Deltoides', 'Tríceps', 'Bíceps',
  'Antebrazo', 'Abdomen', 'Cuádriceps', 'Isquiosurales', 'Glúteos',
  'Aductores', 'Pantorrillas', 'Core', 'Erectores Espinales', 'Cuello',
];

const EMPHASIS_SYNONYMS: Record<string, string> = {
  frontal: 'anterior', front: 'anterior', lateral: 'medio', medio: 'medio',
  medial: 'medio', side: 'medio', rear: 'posterior', posterior: 'posterior',
  clavicular: 'superior', upper: 'superior', esternal: 'medio', middle: 'medio',
  lower: 'inferior', abdominal: 'inferior', long: 'cabeza larga', larga: 'cabeza larga',
  lateralhead: 'cabeza lateral', medialhead: 'cabeza medial', short: 'cabeza corta',
  corta: 'cabeza corta', mayor: 'mayor', max: 'mayor', 'medio-gluteo': 'medio',
  minor: 'menor', menor: 'menor', gastrocnemio: 'gastrocnemio', soleo: 'sóleo',
  sóleo: 'sóleo', rectofemoral: 'recto femoral', 'recto femoral': 'recto femoral',
};

function normalizeText(value: string | undefined | null): string {
  return (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9\s-]/g, ' ').replace(/\s+/g, ' ').trim();
}

export function normalizeCanonicalMuscle(rawMuscle: string, rawEmphasis?: string) {
  const source = normalizeText(rawMuscle);
  const emphasis = EMPHASIS_SYNONYMS[normalizeText(rawEmphasis)] ?? normalizeText(rawEmphasis);

  if (source.includes('cuello')) return { muscle: 'Cuello' as CanonicalMuscle };
  if (source.includes('aductor')) return { muscle: 'Aductores' as CanonicalMuscle };
  if (source.includes('erector')) return { muscle: 'Erectores Espinales' as CanonicalMuscle };
  if (source === 'core') return { muscle: 'Core' as CanonicalMuscle };
  if (source.includes('abdomen')) return { muscle: 'Abdomen' as CanonicalMuscle };
  if (source.includes('pantorrilla')) return { muscle: 'Pantorrillas' as CanonicalMuscle, emphasis: emphasis === 'gastrocnemio' || emphasis === 'sóleo' ? emphasis : undefined };
  if (source.includes('gluteo')) return { muscle: 'Glúteos' as CanonicalMuscle, emphasis: ['mayor', 'medio', 'menor'].includes(emphasis) ? emphasis : undefined };
  if (source.includes('isquio')) return { muscle: 'Isquiosurales' as CanonicalMuscle };
  if (source.includes('cuadriceps')) return { muscle: 'Cuádriceps' as CanonicalMuscle };
  if (source.includes('antebrazo')) return { muscle: 'Antebrazo' as CanonicalMuscle };
  if (source.includes('tricep')) return { muscle: 'Tríceps' as CanonicalMuscle };
  if (source.includes('bicep')) return { muscle: 'Bíceps' as CanonicalMuscle };
  if (source.includes('deltoide') || source.includes('hombro')) {
    let e = emphasis;
    if (source.includes('posterior')) e = 'posterior';
    else if (source.includes('lateral') || source.includes('medio')) e = 'medio';
    else if (source.includes('anterior')) e = 'anterior';
    return { muscle: 'Deltoides' as CanonicalMuscle, emphasis: ['anterior', 'medio', 'posterior'].includes(e) ? e : undefined };
  }
  if (source.includes('trapecio')) return { muscle: 'Trapecio' as CanonicalMuscle };
  if (source.includes('dorsal') || source.includes('espalda')) return { muscle: 'Dorsales' as CanonicalMuscle };
  if (source.includes('pectoral') || source.includes('pecho')) return { muscle: 'Pectorales' as CanonicalMuscle };

  return { muscle: 'Core' as CanonicalMuscle };
}

export function getMuscleDisplayId(rawMuscle: string, rawEmphasis?: string): string {
  const res = normalizeCanonicalMuscle(rawMuscle, rawEmphasis);
  if (res.muscle === 'Deltoides') {
    if (res.emphasis === 'anterior') return 'Deltoides Anterior';
    if (res.emphasis === 'medio') return 'Deltoides Lateral';
    if (res.emphasis === 'posterior') return 'Deltoides Posterior';
  }
  return res.muscle;
}

const normalizeLookupKey = (value: string | undefined | null): string => {
    return (value || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .trim();
};

export function matchesMuscleTarget(
    rawMuscle: string,
    targetCategory: string,
    rawEmphasis?: string
): boolean {
    const normalizedTarget = normalizeLookupKey(targetCategory);
    const displayId = normalizeLookupKey(getMuscleDisplayId(rawMuscle, rawEmphasis));
    if (displayId === normalizedTarget) return true;

    // Fallback comparison for generic categories
    const canonical = normalizeCanonicalMuscle(rawMuscle, rawEmphasis);
    return normalizeLookupKey(canonical.muscle) === normalizedTarget;
}
