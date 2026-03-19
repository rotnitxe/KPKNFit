// apps/mobile/src/services/volumeCalculator.ts
// Motor de Cálculo de Volumen KPKN — Ported from PWA
import type { Mesocycle, Session, Program, ExerciseCatalogEntry, AthleteProfileScore, VolumeRecommendation as VolumeRecType } from '../types/workout';
import type { WeeklyVolumeRecommendation, PostSessionFeedback, MuscleVolumeThresholds } from '../types/volume';
import type { MuscleRole } from '@kpkn/shared-types';
import { buildExerciseIndex, findExerciseWithFallback } from '../utils/exerciseIndex';
import { getMuscleDisplayId, normalizeCanonicalMuscle } from '../utils/canonicalMuscles';
import { INITIAL_MUSCLE_GROUP_DATA } from '../data/initialMuscleGroupDatabase';
import { HYPERTROPHY_ROLE_MULTIPLIERS, FATIGUE_ROLE_MULTIPLIERS } from '@kpkn/shared-types';

const PHASE_FACTORS: Record<string, number> = {
  'Acumulación': 1.0,
  'Intensificación': 0.75,
  'Realización': 0.50,
  'Descarga': 0.40,
  'Custom': 1.0
};

const INTENSITY_FACTORS: Record<string, number> = {
  'Failure': 0.6,
  'RIR_High': 1.0,
  'RIR_Low': 1.2
};

export { HYPERTROPHY_ROLE_MULTIPLIERS, FATIGUE_ROLE_MULTIPLIERS };

export const calculateWeeklyVolume = (
  athleteScore: AthleteProfileScore | null | undefined,
  settings: { preferredIntensity?: string; trainingProfile?: string },
  phase: Mesocycle['goal'] = 'Acumulación'
): WeeklyVolumeRecommendation => {
  if (!athleteScore) {
    return {
      minSets: 10,
      maxSets: 20,
      optimalSets: 15,
      type: 'sets',
      reasoning: "Perfil no calibrado. Usando estándar genérico (10-20 series)."
    };
  }

  const { profileLevel, totalScore } = athleteScore;
  const fFase = PHASE_FACTORS[phase] || 1.0;
  const intensityPref = settings?.preferredIntensity || 'RIR_High';
  const fInt = INTENSITY_FACTORS[intensityPref] || 1.0;

  const trainingProfile = settings?.trainingProfile || 'Aesthetics';

  if (trainingProfile === 'Powerlifting') {
    const isAdvanced = profileLevel === 'Advanced';
    const minMonthlyNL = isAdvanced ? 1300 : 1000;
    const maxMonthlyNL = isAdvanced ? 2500 : 1300;

    const weeklyMin = Math.round((minMonthlyNL / 4) * fFase);
    const weeklyMax = Math.round((maxMonthlyNL / 4) * fFase);

    return {
      minSets: weeklyMin,
      maxSets: weeklyMax,
      optimalSets: Math.round((weeklyMin + weeklyMax) / 2),
      type: 'lifts',
      reasoning: `Motor Powerlifting (Sheiko): Perfil ${profileLevel}. Fase ${phase} (${fFase}x).`
    };
  }

  let baseMin = profileLevel === 'Advanced' ? 14 : 10;
  let baseMax = profileLevel === 'Advanced' ? 22 : 14;

  const calcMin = baseMin * fFase * fInt;
  const calcMax = baseMax * fFase * fInt;

  const finalMin = Math.max(1, Math.round(calcMin));
  const finalMax = Math.max(finalMin + 2, Math.round(calcMax));

  return {
    minSets: finalMin,
    maxSets: finalMax,
    optimalSets: Math.round((finalMin + finalMax) / 2),
    type: 'sets',
    reasoning: `Motor Hipertrofia: ${profileLevel} * Fase ${phase} * Intensidad ${intensityPref}.`
  };
};

export const validateSessionVolume = (
  setsInSession: number,
  muscleGroup: string,
  options?: { deficitRegime?: boolean }
): { isValid: boolean; message?: string } => {
  const MAX_PRODUCTIVE_SESSION_SETS = 12;
  const WARNING_THRESHOLD = 10;
  const deficitFactor = options?.deficitRegime ? 0.8 : 1;
  const maxSets = Math.round(MAX_PRODUCTIVE_SESSION_SETS * deficitFactor);
  const warnSets = Math.round(WARNING_THRESHOLD * deficitFactor);

  if (setsInSession > maxSets) {
    return {
      isValid: false,
      message: options?.deficitRegime
        ? `En déficit: ${setsInSession} series de ${muscleGroup} excede el límite recomendado (${maxSets}).`
        : `Volumen Basura: ${setsInSession} series de ${muscleGroup} en una sesión excede el límite productivo (${maxSets}).`
    };
  }

  if (setsInSession >= warnSets) {
    return {
      isValid: true,
      message: options?.deficitRegime
        ? `En déficit: cerca del límite (${setsInSession}/${maxSets}).`
        : `Cerca del límite por sesión (${setsInSession}/${maxSets}).`
    };
  }

  return { isValid: true };
};

export const calculateFractionalVolume = (
  exercises: { muscleRole: 'primary' | 'secondary'; sets: number }[]
): number => {
  let totalFractionalSets = 0;
  exercises.forEach(ex => {
    if (ex.muscleRole === 'primary') {
      totalFractionalSets += ex.sets * 1.0;
    } else {
      totalFractionalSets += ex.sets * 0.5;
    }
  });
  return totalFractionalSets;
};

export const calculateVolumeAdjustment = (
  muscle: string,
  feedbackHistory: PostSessionFeedback[]
): { factor: number; status: 'recovery_debt' | 'optimal' | 'undertraining'; suggestion: string } => {
  if (!feedbackHistory || feedbackHistory.length === 0) {
    return { factor: 1.0, suggestion: '', status: 'optimal' };
  }

  const muscleLogs = feedbackHistory.filter(log => log.feedback && log.feedback[muscle]);
  if (muscleLogs.length === 0) {
    return { factor: 1.0, suggestion: '', status: 'optimal' };
  }

  const recentLogs = muscleLogs
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
    .slice(0, 3);

  let totalDoms = 0;
  let totalStrength = 0;

  recentLogs.forEach(log => {
    const data = log.feedback[muscle];
    totalDoms += data.doms;
    totalStrength += data.strengthCapacity;
  });

  const avgDoms = totalDoms / recentLogs.length;
  const avgStrength = totalStrength / recentLogs.length;

  if (avgDoms >= 3.5 || avgStrength <= 5) {
    return {
      factor: 0.85,
      status: 'recovery_debt',
      suggestion: `Recuperación lenta en ${muscle} (DOMS altos). Sugerimos reducir volumen un 15% esta semana.`
    };
  }

  if (avgDoms <= 1.5 && avgStrength >= 8) {
    return {
      factor: 1.1,
      status: 'undertraining',
      suggestion: `${muscle} recupera sobrado. Podrías tolerar un +10% de volumen o intensidad.`
    };
  }

  return {
    factor: 1.0,
    status: 'optimal',
    suggestion: `Carga óptima para ${muscle}. Mantén el plan.`
  };
};

export const normalizeMuscleGroup = (specificMuscle: string, emphasis?: string): string => {
  if (!specificMuscle) return '';

  const canonicalDisplay = getMuscleDisplayId(specificMuscle, emphasis);
  if (canonicalDisplay) return canonicalDisplay;

  const lower = specificMuscle.toLowerCase().trim();

  if (lower.includes('posterior') && (lower.includes('deltoides') || lower.includes('hombro'))) {
    return 'Deltoides Posterior';
  }
  if ((lower.includes('lateral') || lower.includes('medio')) && (lower.includes('deltoides') || lower.includes('hombro'))) {
    return 'Deltoides Lateral';
  }
  if ((lower.includes('anterior') || lower.includes('frontal')) && (lower.includes('deltoides') || lower.includes('hombro'))) {
    return 'Deltoides Anterior';
  }
  if (lower.includes('deltoides') || lower.includes('hombro')) {
    return 'Deltoides Anterior';
  }

  if (lower.includes('trapecio') || lower.includes('romboides') || lower.includes('alta')) {
    return 'Trapecio';
  }
  if (lower.includes('dorsal') || lower.includes('dorsales') || lower.includes('lat') || lower.includes('redondo')) {
    return 'Dorsales';
  }
  if (lower.includes('erector') || lower.includes('lumbar') || lower.includes('baja')) {
    return 'Erectores Espinales';
  }
  if (lower.includes('espalda')) {
    return 'Dorsales';
  }

  if (lower.includes('tríceps') || lower.includes('triceps')) {
    return 'Tríceps';
  }
  if ((lower.includes('bíceps') || lower.includes('biceps') || lower.includes('braquial')) && !lower.includes('femoral')) {
    return 'Bíceps';
  }
  if (lower.includes('antebrazo')) {
    return 'Antebrazo';
  }

  if (lower.includes('femoral') || lower.includes('semitendinoso') || lower.includes('semimembranoso') || lower.includes('isquio')) {
    return 'Isquiosurales';
  }
  if (lower.includes('cuádriceps') || lower.includes('cuadriceps') || lower.includes('recto femoral') || lower.includes('vasto')) {
    return 'Cuádriceps';
  }
  if (lower.includes('glúteo') || lower.includes('gluteo')) {
    return 'Glúteos';
  }
  if (lower.includes('adductor') || lower.includes('pectíneo')) {
    return 'Aductores';
  }
  if (lower.includes('gemelo') || lower.includes('sóleo') || lower.includes('soleo') || lower.includes('pantorrilla')) {
    return 'Pantorrillas';
  }

  if (lower.includes('cuello') || lower.includes('cervical') || lower.includes('neck')) {
    return 'Cuello';
  }

  if (lower.includes('pectoral') || lower.includes('pecho')) return 'Pectorales';
  if (lower.includes('abdominal') || lower.includes('oblicuo') || lower.includes('core')) return 'Abdomen';

  return specificMuscle.charAt(0).toUpperCase() + specificMuscle.slice(1);
};

export const calculateUnifiedMuscleVolume = (
  sessions: Session[],
  exerciseList: ExerciseCatalogEntry[]
): { muscleGroup: string; displayVolume: number }[] => {
  const volumeMap: Record<string, number> = {};
  const exIndex = buildExerciseIndex(exerciseList);

  sessions.forEach(session => {
    if (!session) return;

    const allExercises = session.parts && session.parts.length > 0
      ? session.parts.flatMap(p => p.exercises || [])
      : (session.exercises || []);

    allExercises.forEach(exercise => {
      if (!exercise || !exercise.sets) return;

      const validSetsCount = exercise.sets.filter(set =>
        set && !set.isIneffective && ((set.completedReps ?? set.targetReps ?? 0) > 0 || (set.weight ?? 0) > 0)
      ).length;

      if (validSetsCount > 0) {
        const dbInfo = findExerciseWithFallback(exIndex, exercise.exerciseDbId, exercise.name);
        const involvedMuscles = dbInfo?.involvedMuscles || (exercise as any).targetMuscles || [];

        if (involvedMuscles.length > 0) {
          const uniqueMuscleMultipliers = new Map<string, number>();

          involvedMuscles.forEach((m: { muscle: string; role: MuscleRole; emphasis?: string; activation?: number }) => {
            if (!m || !m.muscle) return;
            const muscleName = normalizeMuscleGroup(m.muscle, m.emphasis);
            const multiplier = HYPERTROPHY_ROLE_MULTIPLIERS[m.role] || 0.5;

            const currentMax = uniqueMuscleMultipliers.get(muscleName) || 0;
            if (multiplier > currentMax) {
              uniqueMuscleMultipliers.set(muscleName, multiplier);
            }
          });

          uniqueMuscleMultipliers.forEach((maxMultiplier, muscleName) => {
            if (!volumeMap[muscleName]) {
              volumeMap[muscleName] = 0;
            }
            volumeMap[muscleName] += validSetsCount * maxMultiplier;
          });
        }
      }
    });
  });

  return Object.entries(volumeMap)
    .filter(([muscleGroup]) => muscleGroup !== 'General')
    .map(([muscleGroup, volume]) => ({
      muscleGroup,
      displayVolume: Math.round(volume * 10) / 10
    }))
    .sort((a, b) => b.displayVolume - a.displayVolume);
};

const MUSCLE_AGGREGATION_MAP: Record<string, string[]> = {
  'Cuádriceps': ['cuádriceps', 'vasto-lateral', 'vasto-medial', 'recto-femoral'],
  'Isquiosurales': ['isquiosurales', 'bíceps-femoral', 'semitendinoso', 'semimembranoso'],
  'Glúteos': ['glúteos', 'glúteo-mayor', 'glúteo-medio', 'glúteo-menor'],
  'Pectorales': ['pectoral', 'pectoral-superior', 'pectoral-medio', 'pectoral-inferior'],
  'Bíceps': ['bíceps', 'cabeza-larga-bíceps', 'cabeza-corta-bíceps', 'braquial', 'braquiorradial'],
  'Tríceps': ['tríceps', 'cabeza-larga-tríceps', 'cabeza-lateral-tríceps', 'cabeza-medial-tríceps'],
  'Dorsales': ['espalda', 'dorsal-ancho', 'redondo-mayor'],
  'Trapecio': ['trapecio', 'trapecio-superior', 'trapecio-medio', 'trapecio-inferior', 'romboides'],
  'Espalda Baja': ['erectores-espinales', 'multífidos', 'cuadrado-lumbar'],
  'Erectores Espinales': ['erectores-espinales', 'multifidos', 'cuadrado-lumbar'],
  'Abdomen': ['abdomen', 'recto-abdominal', 'oblicuos', 'transverso-abdominal', 'core'],
  'Pantorrillas': ['pantorrillas', 'gastrocnemio', 'sóleo'],
};

const STANDALONE_DISPLAY_NAMES: Record<string, string> = {
  'Deltoides Anterior': 'deltoides-anterior',
  'Deltoides Lateral': 'deltoides-lateral',
  'Deltoides Posterior': 'deltoides-posterior',
};

function parseVolumeString(s: string): { min: number; max: number } {
  if (!s || s === 'N/A') return { min: 0, max: 20 };
  const trimmed = s.trim();
  if (trimmed.includes('-')) {
    const [a, b] = trimmed.split('-').map(x => parseInt(x.trim(), 10) || 0);
    return { min: a, max: Math.max(a, b || a) };
  }
  const n = parseInt(trimmed, 10) || 0;
  return { min: n, max: n };
}

export const getVolumeThresholdsForMuscle = (
  muscleDisplayName: string,
  options?: {
    program?: Program | null;
    settings?: { volumeSystem?: string; preferredIntensity?: string; trainingProfile?: string } | null;
    athleteScore?: AthleteProfileScore | null;
    phase?: Mesocycle['goal'];
  }
): MuscleVolumeThresholds => {
  const { program, settings, athleteScore, phase = 'Acumulación' } = options || {};

  const normalizeForMatch = (s: string) =>
    s.toLowerCase().trim().replace(/\s+/g, ' ').normalize('NFD').replace(/[\u0300-\u036f]/g, '');

  if (program?.volumeRecommendations?.length) {
    const displayNorm = normalizeForMatch(muscleDisplayName);
    const rec = program.volumeRecommendations.find((r: any) =>
      normalizeForMatch(r.muscleGroup) === displayNorm ||
      normalizeForMatch(r.muscleGroup).includes(displayNorm) ||
      displayNorm.includes(normalizeForMatch(r.muscleGroup))
    );
    if (rec) {
      const optimal = Math.round((rec.minEffectiveVolume + rec.maxAdaptiveVolume) / 2);
      return {
        min: rec.minEffectiveVolume,
        optimal,
        max: rec.maxRecoverableVolume,
        source: 'program',
        rangeLabel: `${rec.minEffectiveVolume}–${rec.maxRecoverableVolume}`,
      };
    }
  }

  const subIds = STANDALONE_DISPLAY_NAMES[muscleDisplayName]
    ? [STANDALONE_DISPLAY_NAMES[muscleDisplayName]]
    : MUSCLE_AGGREGATION_MAP[muscleDisplayName]
      ? MUSCLE_AGGREGATION_MAP[muscleDisplayName]
      : [muscleDisplayName.toLowerCase().replace(/\s+/g, '-').replace(/í/g, 'i').replace(/á/g, 'a')];

  let bestMin = 0;
  let bestOptimal = 12;
  let bestMax = 20;

  for (const id of subIds) {
    const info = INITIAL_MUSCLE_GROUP_DATA.find(m =>
      m.id === id || normalizeForMatch(m.name) === normalizeForMatch(id)
    );
    if (info?.volumeRecommendations) {
      const { mev, mav, mrv } = info.volumeRecommendations;
      const mevParsed = parseVolumeString(mev);
      const mavParsed = parseVolumeString(mav);
      const mrvParsed = parseVolumeString(mrv);
      const min = mevParsed.max || mevParsed.min;
      const optimal = Math.round((mavParsed.min + mavParsed.max) / 2) || mavParsed.min;
      const max = mrvParsed.max || mrvParsed.min;
      if (max > bestMax) {
        bestMin = min;
        bestOptimal = optimal;
        bestMax = max;
      }
    }
  }

  if (bestMax === 20 && bestMin === 0) {
    const displayNorm = normalizeForMatch(muscleDisplayName);
    const info = INITIAL_MUSCLE_GROUP_DATA.find(m =>
      normalizeForMatch(m.name) === displayNorm ||
      normalizeForMatch(m.name).includes(displayNorm)
    );
    if (info?.volumeRecommendations) {
      const { mev, mav, mrv } = info.volumeRecommendations;
      const mevParsed = parseVolumeString(mev);
      const mavParsed = parseVolumeString(mav);
      const mrvParsed = parseVolumeString(mrv);
      bestMin = mevParsed.max || mevParsed.min;
      bestOptimal = Math.round((mavParsed.min + mavParsed.max) / 2) || mavParsed.min;
      bestMax = mrvParsed.max || mrvParsed.min;
    }
  }

  let finalMin = bestMin;
  let finalOptimal = bestOptimal;
  let finalMax = Math.max(bestMax, bestMin + 2);
  let source: 'program' | 'israetel' | 'kpnk' = 'israetel';
  const useKpnk = settings?.volumeSystem !== 'israetel' && !!athleteScore;

  if (athleteScore && settings && useKpnk) {
    const base = calculateWeeklyVolume(athleteScore, settings, phase || 'Acumulación');
    const scale = base.optimalSets / 15;
    finalMin = Math.max(1, Math.round(bestMin * scale));
    finalOptimal = Math.round(bestOptimal * scale);
    finalMax = Math.max(finalMin + 2, Math.round(bestMax * scale));
    source = 'kpnk';
  }

  return {
    min: finalMin,
    optimal: finalOptimal,
    max: finalMax,
    source,
    rangeLabel: `${finalMin}–${finalMax}`,
  };
};

export const VOLUME_DISPLAY_MUSCLES = [
  'Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pectorales', 'Bíceps', 'Tríceps',
  'Dorsales', 'Trapecio', 'Espalda Baja', 'Abdomen', 'Pantorrillas',
  'Deltoides Anterior', 'Deltoides Lateral', 'Deltoides Posterior',
] as const;

export const getIsraetelVolumeRecommendations = (): VolumeRecType[] => {
  const displayMuscles = [
    'Cuadriceps', 'Isquiosurales', 'Gluteos', 'Pectorales', 'Biceps', 'Triceps',
    'Dorsales', 'Trapecio', 'Erectores Espinales', 'Abdomen', 'Pantorrillas',
    'Deltoides Anterior', 'Deltoides Lateral', 'Deltoides Posterior',
  ] as const;

  return displayMuscles.map(muscle => {
    const th = getVolumeThresholdsForMuscle(muscle, { phase: 'Acumulación' });
    return {
      muscleGroup: muscle,
      minEffectiveVolume: th.min,
      maxAdaptiveVolume: th.optimal,
      maxRecoverableVolume: th.max,
      frequencyCap: 4,
    };
  });
};

export const getKpnkVolumeRecommendations = (
  athleteScore: AthleteProfileScore,
  settings: { preferredIntensity?: string; trainingProfile?: string },
  phase: Mesocycle['goal'] = 'Acumulación'
): VolumeRecType[] => {
  const base = calculateWeeklyVolume(athleteScore, settings, phase);
  const scale = base.optimalSets / 15;
  return getIsraetelVolumeRecommendations().map(rec => {
    const newMin = Math.max(1, Math.round(rec.minEffectiveVolume * scale));
    const newOpt = Math.round(rec.maxAdaptiveVolume * scale);
    const newMax = Math.round(rec.maxRecoverableVolume * scale);
    return {
      ...rec,
      minEffectiveVolume: newMin,
      maxAdaptiveVolume: Math.max(newOpt, newMin),
      maxRecoverableVolume: Math.max(newMax, newMin + 2),
    };
  });
};

export function volumeRecommendationsToLimits(
  recs: { muscleGroup: string; minEffectiveVolume: number; maxAdaptiveVolume: number; maxRecoverableVolume: number; frequencyCap?: number }[] | undefined
): Record<string, { maxSession: number; max: number; min?: number }> {
  if (!recs?.length) return {};
  const result: Record<string, { maxSession: number; max: number; min?: number }> = {};
  for (const r of recs) {
    const freq = r.frequencyCap ?? 4;
    const maxSession = Math.min(10, Math.max(4, Math.ceil((r.maxRecoverableVolume ?? 18) / Math.max(1, freq))));
    result[r.muscleGroup] = {
      maxSession,
      max: r.maxRecoverableVolume,
      min: r.minEffectiveVolume,
    };
  }
  return result;
}

export const calculateUnifiedMuscleVolumeFromLogs = (
  logs: any[],
  exerciseList: ExerciseCatalogEntry[]
): { muscleGroup: string; displayVolume: number }[] => {
  const virtualSessions: Session[] = (logs || []).map(log => ({
    id: log.id || '',
    name: log.sessionName || '',
    exercises: (log.completedExercises || []).map((ex: any) => ({
      id: ex.exerciseId || ex.id || '',
      name: ex.exerciseName || ex.name || '',
      exerciseDbId: ex.exerciseDbId,
      sets: ex.sets || [],
      targetMuscles: ex.targetMuscles,
    })),
  }));
  return calculateUnifiedMuscleVolume(virtualSessions, exerciseList);
};
