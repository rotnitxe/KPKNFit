"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getCanonicalMuscleList = getCanonicalMuscleList;
exports.normalizeCanonicalMuscle = normalizeCanonicalMuscle;
exports.normalizeInvolvedMuscles = normalizeInvolvedMuscles;
exports.getMuscleDisplayId = getMuscleDisplayId;
exports.matchesMuscleTarget = matchesMuscleTarget;
exports.getExercisePrimaryDisplayMuscles = getExercisePrimaryDisplayMuscles;
const CANONICAL_MUSCLES = [
    'Pectorales',
    'Dorsales',
    'Trapecio',
    'Deltoides',
    'Tríceps',
    'Bíceps',
    'Antebrazo',
    'Abdomen',
    'Cuádriceps',
    'Isquiosurales',
    'Glúteos',
    'Aductores',
    'Pantorrillas',
    'Core',
    'Erectores Espinales',
];
const EMPHASIS_SYNONYMS = {
    frontal: 'anterior',
    front: 'anterior',
    lateral: 'medio',
    medio: 'medio',
    medial: 'medio',
    side: 'medio',
    rear: 'posterior',
    posterior: 'posterior',
    clavicular: 'superior',
    upper: 'superior',
    esternal: 'medio',
    middle: 'medio',
    lower: 'inferior',
    abdominal: 'inferior',
    long: 'cabeza larga',
    larga: 'cabeza larga',
    lateralhead: 'cabeza lateral',
    medialhead: 'cabeza medial',
    short: 'cabeza corta',
    corta: 'cabeza corta',
    mayor: 'mayor',
    max: 'mayor',
    'medio-gluteo': 'medio',
    minor: 'menor',
    menor: 'menor',
    gastrocnemio: 'gastrocnemio',
    soleo: 'sóleo',
    sóleo: 'sóleo',
    rectofemoral: 'recto femoral',
    'recto femoral': 'recto femoral',
};
function normalizeText(value) {
    return (value || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .replace(/[^a-z0-9\s-]/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}
function normalizeEmphasis(value) {
    const normalized = normalizeText(value);
    if (!normalized)
        return undefined;
    return EMPHASIS_SYNONYMS[normalized] ?? normalized;
}
function inferResolution(rawMuscle, rawEmphasis) {
    const source = normalizeText(rawMuscle);
    const emphasis = normalizeEmphasis(rawEmphasis ?? rawMuscle);
    if (source.includes('pectineo') ||
        source.includes('aductor') ||
        source.includes('adductor') ||
        source.includes('adduct')) {
        return { muscle: 'Aductores' };
    }
    if (source.includes('erector') || source.includes('lumbar') || source.includes('espalda baja') || source.includes('lower back')) {
        return { muscle: 'Erectores Espinales' };
    }
    if (source === 'core') {
        return { muscle: 'Core' };
    }
    if (source.includes('abdomen') ||
        source.includes('abdominal') ||
        source.includes('oblicuo') ||
        source.includes('transverso')) {
        return { muscle: 'Abdomen' };
    }
    if (source.includes('pantorrilla') ||
        source.includes('gemelo') ||
        source.includes('gastrocnemio') ||
        source.includes('soleo') ||
        source.includes('sóleo') ||
        source.includes('calf')) {
        return {
            muscle: 'Pantorrillas',
            emphasis: emphasis === 'gastrocnemio' || emphasis === 'sóleo' ? emphasis : undefined,
        };
    }
    if (source.includes('gluteo') || source.includes('gluteos')) {
        return {
            muscle: 'Glúteos',
            emphasis: emphasis === 'mayor' || emphasis === 'medio' || emphasis === 'menor' ? emphasis : undefined,
        };
    }
    if (source.includes('isquio') ||
        source.includes('hamstring') ||
        source.includes('femoral') ||
        source.includes('semitendinoso') ||
        source.includes('semimembranoso')) {
        return {
            muscle: 'Isquiosurales',
            emphasis: emphasis === 'bíceps femoral' || emphasis === 'semitendinoso' || emphasis === 'semimembranoso' ? emphasis : undefined,
        };
    }
    if (source.includes('cuadriceps') ||
        source.includes('cuadricep') ||
        source.includes('quad') ||
        source.includes('vasto') ||
        source.includes('recto femoral')) {
        return {
            muscle: 'Cuádriceps',
            emphasis: emphasis === 'recto femoral' ? emphasis : undefined,
        };
    }
    if (source.includes('antebrazo') || source.includes('forearm') || source.includes('wrist flexor') || source.includes('wrist extensor')) {
        return { muscle: 'Antebrazo' };
    }
    if (source.includes('tricep') || source.includes('trícep')) {
        return {
            muscle: 'Tríceps',
            emphasis: emphasis === 'cabeza larga' ||
                emphasis === 'cabeza lateral' ||
                emphasis === 'cabeza medial'
                ? emphasis
                : undefined,
        };
    }
    if ((source.includes('bicep') || source.includes('bícep') || source.includes('braquial') || source.includes('braquiorradial')) &&
        !source.includes('femoral')) {
        return {
            muscle: 'Bíceps',
            emphasis: emphasis === 'cabeza larga' ||
                emphasis === 'cabeza corta' ||
                emphasis === 'braquial' ||
                emphasis === 'braquiorradial'
                ? emphasis
                : undefined,
        };
    }
    if (source.includes('deltoide') ||
        source.includes('deltoides') ||
        source.includes('hombro') ||
        source.includes('shoulder')) {
        let resolvedEmphasis = emphasis;
        if (source.includes('posterior'))
            resolvedEmphasis = 'posterior';
        else if (source.includes('lateral') || source.includes('medio') || source.includes('medial'))
            resolvedEmphasis = 'medio';
        else if (source.includes('anterior') || source.includes('frontal'))
            resolvedEmphasis = 'anterior';
        return {
            muscle: 'Deltoides',
            emphasis: resolvedEmphasis === 'anterior' ||
                resolvedEmphasis === 'medio' ||
                resolvedEmphasis === 'posterior'
                ? resolvedEmphasis
                : undefined,
        };
    }
    if (source.includes('trapecio') || source.includes('upper back')) {
        return { muscle: 'Trapecio' };
    }
    if (source.includes('dorsal') ||
        source.includes('lat') ||
        source.includes('lats') ||
        source.includes('romboide') ||
        source.includes('redondo mayor') ||
        source.includes('espalda') ||
        source.includes('back')) {
        return { muscle: 'Dorsales' };
    }
    if (source.includes('pectoral') || source.includes('pecho') || source.includes('chest')) {
        return {
            muscle: 'Pectorales',
            emphasis: emphasis === 'superior' || emphasis === 'medio' || emphasis === 'inferior'
                ? emphasis
                : undefined,
        };
    }
    return { muscle: 'Core' };
}
function resolveTarget(target) {
    const normalized = normalizeText(target);
    if (!normalized)
        return null;
    const broad = inferResolution(target).muscle;
    if (broad === 'Deltoides') {
        if (normalized.includes('posterior'))
            return { broad, specific: 'Deltoides Posterior' };
        if (normalized.includes('lateral') || normalized.includes('medio') || normalized.includes('medial'))
            return { broad, specific: 'Deltoides Lateral' };
        if (normalized.includes('anterior') || normalized.includes('frontal'))
            return { broad, specific: 'Deltoides Anterior' };
    }
    return { broad };
}
function getCanonicalMuscleList() {
    return [...CANONICAL_MUSCLES];
}
function normalizeCanonicalMuscle(rawMuscle, rawEmphasis) {
    return inferResolution(rawMuscle, rawEmphasis);
}
function normalizeInvolvedMuscles(involvedMuscles) {
    return (involvedMuscles ?? []).map((entry) => {
        const normalized = normalizeCanonicalMuscle(entry.muscle, entry.emphasis);
        return {
            ...entry,
            muscle: normalized.muscle,
            ...(normalized.emphasis ? { emphasis: normalized.emphasis } : {}),
        };
    });
}
function getMuscleDisplayId(rawMuscle, rawEmphasis) {
    if (!normalizeText(rawMuscle))
        return '';
    const resolved = normalizeCanonicalMuscle(rawMuscle, rawEmphasis);
    if (resolved.muscle === 'Deltoides') {
        if (resolved.emphasis === 'anterior')
            return 'Deltoides Anterior';
        if (resolved.emphasis === 'medio')
            return 'Deltoides Lateral';
        if (resolved.emphasis === 'posterior')
            return 'Deltoides Posterior';
    }
    return resolved.muscle;
}
function matchesMuscleTarget(rawMuscle, target, rawEmphasis) {
    const targetResolution = resolveTarget(target);
    if (!targetResolution)
        return false;
    const resolved = normalizeCanonicalMuscle(rawMuscle, rawEmphasis);
    if (resolved.muscle !== targetResolution.broad)
        return false;
    if (!targetResolution.specific)
        return true;
    const displayId = getMuscleDisplayId(rawMuscle, rawEmphasis);
    return normalizeText(displayId) === normalizeText(targetResolution.specific);
}
function getExercisePrimaryDisplayMuscles(exercise) {
    const seen = new Set();
    const result = [];
    for (const entry of exercise.involvedMuscles ?? []) {
        if (entry.role !== 'primary')
            continue;
        const displayId = getMuscleDisplayId(entry.muscle, entry.emphasis);
        if (!seen.has(displayId)) {
            seen.add(displayId);
            result.push(displayId);
        }
    }
    return result;
}
