import { writeFileSync } from 'fs';
import { FULL_EXERCISE_LIST } from '../data/exerciseDatabaseMerged';
import { calculatePersonalizedBatteryTanks, calculateSetBatteryDrain } from '../services/fatigueService';
import { getCanonicalMuscleList } from '../utils/canonicalMuscles';

type InvolvedMuscle = {
  muscle: string;
  role: 'primary' | 'secondary' | 'stabilizer' | 'neutralizer';
  activation?: number;
  emphasis?: string;
};

type ExerciseLike = {
  id: string;
  name: string;
  type?: string;
  category?: string;
  equipment?: string;
  force?: string;
  bodyPart?: string;
  involvedMuscles: InvolvedMuscle[];
  efc?: number;
  cnc?: number;
  ssc?: number;
};

const STOP_WORDS = new Set([
  'a',
  'al',
  'con',
  'contra',
  'de',
  'del',
  'e',
  'el',
  'en',
  'la',
  'las',
  'los',
  'o',
  'para',
  'por',
  'sin',
  'u',
  'un',
  'una',
  'y',
]);

const report: string[] = [];

function normalizeText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

function normalizeMuscleToken(value: string): string {
  const normalized = normalizeText(value);

  if (normalized.includes('pectineo')) return 'Aductores';

  if (normalized.includes('pectoral')) return 'Pectorales';
  if (normalized.includes('dorsal') || normalized.includes('lat') || normalized.includes('romboide') || normalized.includes('redondo mayor')) return 'Dorsales';
  if (normalized.includes('trapecio')) return 'Trapecio';
  if (normalized.includes('erector') || normalized.includes('espalda baja') || normalized.includes('lumbar')) return 'Erectores Espinales';
  if (normalized.includes('deltoide') || normalized.includes('deltoides') || normalized.includes('hombro')) {
    if (normalized.includes('posterior')) return 'Deltoides Posterior';
    if (normalized.includes('medio') || normalized.includes('lateral')) return 'Deltoides Lateral';
    if (normalized.includes('anterior') || normalized.includes('frontal')) return 'Deltoides Anterior';
    return 'Deltoides';
  }
  if (normalized.includes('biceps') && !normalized.includes('femoral')) return 'Biceps';
  if (normalized.includes('triceps')) return 'Triceps';
  if (normalized.includes('antebrazo')) return 'Antebrazo';
  if (normalized.includes('cuadriceps') || normalized.includes('vasto') || normalized.includes('recto femoral')) return 'Cuadriceps';
  if (normalized.includes('isquio') || normalized.includes('femoral') || normalized.includes('semitendinoso') || normalized.includes('semimembranoso')) return 'Isquiosurales';
  if (normalized.includes('gluteo') || normalized.includes('gluteos')) return 'Gluteos';
  if (normalized.includes('aductor')) return 'Aductores';
  if (normalized.includes('pantorrilla') || normalized.includes('gemelo') || normalized.includes('soleo') || normalized.includes('gastrocnemio')) return 'Pantorrillas';
  if (normalized.includes('abdomen') || normalized.includes('abdominal') || normalized.includes('oblicuo') || normalized.includes('transverso') || normalized === 'core') return normalized === 'core' ? 'Core' : 'Abdomen';

  return value;
}

function getUsedMuscles(list: ExerciseLike[]): Map<string, number> {
  const counts = new Map<string, number>();
  for (const exercise of list) {
    for (const entry of exercise.involvedMuscles ?? []) {
      counts.set(entry.muscle, (counts.get(entry.muscle) ?? 0) + 1);
    }
  }
  return new Map([...counts.entries()].sort((a, b) => a[0].localeCompare(b[0])));
}

function getCanonicalCatalog(): Set<string> {
  return new Set(getCanonicalMuscleList());
}

function findNonCanonicalMuscles(list: ExerciseLike[], canonical: Set<string>) {
  const outliers = new Map<string, { count: number; normalized: string }>();

  for (const exercise of list) {
    for (const entry of exercise.involvedMuscles ?? []) {
      if (!canonical.has(entry.muscle)) {
        const prev = outliers.get(entry.muscle);
        outliers.set(entry.muscle, {
          count: (prev?.count ?? 0) + 1,
          normalized: normalizeMuscleToken(entry.muscle),
        });
      }
    }
  }

  return new Map([...outliers.entries()].sort((a, b) => b[1].count - a[1].count || a[0].localeCompare(b[0])));
}

function findSuspiciousNameCase(list: ExerciseLike[]) {
  const findings: { id: string; name: string; suspiciousWord: string }[] = [];

  for (const exercise of list) {
    const words = exercise.name.split(/\s+/).filter(Boolean);
    for (let index = 0; index < words.length; index += 1) {
      const clean = words[index].replace(/^[([{"']+|[)\]},"'.:;!?]+$/g, '');
      if (!clean) continue;
      const lower = clean.toLowerCase();
      const firstAlpha = clean.match(/[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]/);
      const startsUpper = firstAlpha ? firstAlpha[0] === firstAlpha[0].toUpperCase() : true;
      const isRomanNumeral = /^[IVXLCDM]+$/i.test(clean);
      const isAllCaps = /^[A-ZÁÉÍÓÚÜÑ0-9-]+$/.test(clean);
      if (STOP_WORDS.has(lower) || startsUpper || isRomanNumeral || isAllCaps) continue;
      if (/[a-záéíóúüñ]/.test(clean[0])) {
        findings.push({ id: exercise.id, name: exercise.name, suspiciousWord: clean });
        break;
      }
    }
  }

  return findings;
}

function findExercisesWithoutPrimary(list: ExerciseLike[]) {
  return list
    .filter((exercise) => !(exercise.involvedMuscles || []).some((entry) => entry.role === 'primary'))
    .map((exercise) => ({
      id: exercise.id,
      name: exercise.name,
      muscles: formatMuscleBreakdown(exercise),
    }));
}

function formatMuscleBreakdown(exercise: ExerciseLike): string {
  return exercise.involvedMuscles
    .map((entry) => `${entry.muscle}(${entry.role}${entry.emphasis ? `:${entry.emphasis}` : ''})`)
    .join(', ');
}

function getRepresentativeExercises(list: ExerciseLike[]) {
  const byId = new Map(list.map((exercise) => [exercise.id, exercise]));
  const wanted = [
    'tren_superior_press_banca_plano_barra',
    'tren_superior_press_hombros_sentado_mancuernas',
    'tren_superior_elevaciones_laterales_mancuernas',
    'tren_inferior_sentadilla_barra_alta',
    'tren_inferior_peso_muerto_convencional',
    'tren_inferior_hip_thrust_barra',
    'tren_superior_remo_inclinado_prono_barra',
    'tren_superior_jalon_pecho_prono',
  ];

  return wanted.map((id) => byId.get(id)).filter(Boolean) as ExerciseLike[];
}

function auditRepresentativeDrain(exercises: ExerciseLike[]) {
  const tanks = calculatePersonalizedBatteryTanks({
    athleteScore: { profileLevel: 'Advanced', trainingStyle: 'Bodybuilder' },
    athleteType: 'bodybuilder',
  } as any);

  const scenarios = [
    {
      label: 'hipertrofia moderada',
      set: { completedReps: 8, completedRPE: 8, weight: 100 },
      restTime: 120,
    },
    {
      label: 'fuerza pesada',
      set: { completedReps: 3, completedRPE: 9, weight: 150 },
      restTime: 180,
    },
    {
      label: 'bombeo alto reps',
      set: { completedReps: 18, completedRPE: 8, weight: 40 },
      restTime: 60,
    },
  ];

  return exercises.map((exercise) => ({
    id: exercise.id,
    name: exercise.name,
    drains: scenarios.map((scenario) => ({
      label: scenario.label,
      drain: calculateSetBatteryDrain(scenario.set, exercise as any, tanks, 1, scenario.restTime),
    })),
  }));
}

function pushSection(title: string) {
  report.push('');
  report.push('='.repeat(100));
  report.push(title);
  report.push('='.repeat(100));
}

const fullList = FULL_EXERCISE_LIST as ExerciseLike[];
const canonicalCatalog = getCanonicalCatalog();
const usedMuscles = getUsedMuscles(fullList);
const nonCanonicalMuscles = findNonCanonicalMuscles(fullList, canonicalCatalog);
const suspiciousNameCase = findSuspiciousNameCase(fullList);
const exercisesWithoutPrimary = findExercisesWithoutPrimary(fullList);
const representativeDrain = auditRepresentativeDrain(getRepresentativeExercises(fullList));

report.push(`Ejercicios auditados: ${fullList.length}`);
report.push(`Musculos usados en involvedMuscles: ${usedMuscles.size}`);
report.push(`Musculos canonicos definidos: ${canonicalCatalog.size}`);
report.push(`Musculos fuera del canon: ${nonCanonicalMuscles.size}`);
report.push(`Nombres con casing sospechoso: ${suspiciousNameCase.length}`);
report.push(`Ejercicios sin musculo primario: ${exercisesWithoutPrimary.length}`);

pushSection('1. Musculos fuera del canon');
if (nonCanonicalMuscles.size === 0) {
  report.push('No se detectaron musculos fuera del catalogo canonico.');
} else {
  for (const [muscle, data] of nonCanonicalMuscles) {
    report.push(`${muscle} | usos=${data.count} | sugerido=${data.normalized}`);
  }
}

pushSection('2. Inventario de musculos usados');
for (const [muscle, count] of usedMuscles) {
  report.push(`${muscle} | usos=${count}`);
}

pushSection('3. Nombres con casing sospechoso');
if (suspiciousNameCase.length === 0) {
  report.push('No se detectaron nombres con palabras mayormente en minuscula fuera de stop words.');
} else {
  for (const finding of suspiciousNameCase.slice(0, 200)) {
    report.push(`${finding.id} | ${finding.name} | palabra=${finding.suspiciousWord}`);
  }
}

pushSection('3b. Ejercicios sin musculo primario');
if (exercisesWithoutPrimary.length === 0) {
  report.push('No se detectaron ejercicios sin musculo primario.');
} else {
  for (const exercise of exercisesWithoutPrimary.slice(0, 200)) {
    report.push(`${exercise.id} | ${exercise.name}`);
    report.push(`  ${exercise.muscles || 'Sin involucramiento registrado'}`);
  }
}

pushSection('4. Ejercicios de referencia y su involucramiento');
for (const exercise of getRepresentativeExercises(fullList)) {
  report.push(`${exercise.id} | ${exercise.name}`);
  report.push(`  ${formatMuscleBreakdown(exercise)}`);
}

pushSection('5. Drenaje AUGE por serie en ejercicios representativos');
for (const entry of representativeDrain) {
  report.push(`${entry.id} | ${entry.name}`);
  for (const scenario of entry.drains) {
    const drain = scenario.drain;
    report.push(
      `  ${scenario.label}: muscular=${drain.muscularDrainPct.toFixed(2)}% | cns=${drain.cnsDrainPct.toFixed(2)}% | spinal=${drain.spinalDrainPct.toFixed(2)}%`
    );
  }
}

const outPath = 'scripts/audit-exercise-auge-report.txt';
writeFileSync(outPath, `${report.join('\n')}\n`, 'utf8');
console.log(report.join('\n'));
console.log(`\nReporte guardado en ${outPath}`);
