// Rewrites the exercise catalog prose without changing its structural schema.
// Run from android-native: node scripts/apply_catalog_descriptions.js
const { loadDb, saveDb } = require('./catalog_transform_base');
const { PRETTY_NAMES, prettifyId } = require('./catalog_names');

const normalize = value => String(value || '')
  .toLowerCase()
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '');

const prettyTokens = value => {
  const replacements = new Map([
    ['biceps', 'bíceps'], ['triceps', 'tríceps'], ['cuadriceps', 'cuádriceps'],
    ['gluteo', 'glúteo'], ['gluteos', 'glúteos'], ['isquios', 'isquiosurales'],
    ['pectoral', 'pectoral'], ['deltoides', 'deltoides'], ['trapecio', 'trapecio'],
    ['mancuernas', 'mancuernas'], ['mancuerna', 'mancuerna'], ['polea', 'polea'],
    ['sentado', 'sentado'], ['sentada', 'sentada'], ['de_pie', 'de pie'],
  ]);
  return value
    .replace(/_/g, ' ')
    .split(/\s+/)
    .filter(Boolean)
    .map((token, index) => {
      const lower = token.toLowerCase();
      const replacement = replacements.get(lower) || lower;
      if (index > 0 && ['de', 'del', 'en', 'con', 'a', 'y'].includes(replacement)) return replacement;
      return replacement.charAt(0).toUpperCase() + replacement.slice(1);
    })
    .join(' ');
};

function cleanName(exercise) {
  const current = String(exercise.name || '').trim();
  if (!current || /^[a-záéíóúñ]/.test(current)) {
    return PRETTY_NAMES[exercise.id] || prettyTokens(prettifyId(exercise.id));
  }
  return current;
}

function primaryMuscle(exercise) {
  return exercise.involvedMuscles?.find(m => m.role === 'primary')?.muscle ||
    exercise.involvedMuscles?.[0]?.muscle || 'la musculatura objetivo';
}

function canonicalDescription(exercise, name) {
  const existing = String(exercise.description || '').trim();
  if (existing && !normalize(existing).includes('ajustable en chips')) {
    return existing.charAt(0).toUpperCase() + existing.slice(1);
  }
  const muscle = primaryMuscle(exercise).toLowerCase();
  const equipment = String(exercise.equipment || 'carga controlada').trim().toLowerCase();
  const force = String(exercise.force || '').trim().toLowerCase();
  const pattern = force ? ` dentro de un patrón de ${force}` : '';
  return `${name} es un ejercicio${pattern} que trabaja principalmente ${muscle}. Se realiza con ${equipment}, manteniendo un rango controlado, una trayectoria estable y una progresión de carga que no deteriore la técnica.`;
}

function aspectDescription(aspect) {
  const key = normalize(`${aspect.id} ${aspect.name}`);
  if (key.includes('grip') || key.includes('agarre')) return 'El agarre modifica la posición de la muñeca, la línea de fuerza y la participación relativa de los músculos implicados.';
  if (key.includes('implement') || key.includes('equipment') || key.includes('equipamiento')) return 'El implemento cambia la libertad de trayectoria y cómo se reparte la resistencia durante el recorrido.';
  if (key.includes('bench') || key.includes('angle') || key.includes('angulo')) return 'El ángulo modifica la dirección de la resistencia y el énfasis relativo dentro del patrón.';
  if (key.includes('laterality') || key.includes('lateralidad')) return 'La lateralidad define si ambos lados trabajan juntos o si cada lado se controla de forma independiente.';
  if (key.includes('position') || key.includes('posicion')) return 'La posición cambia las demandas de estabilidad y la contribución de los segmentos que fijan el cuerpo.';
  return 'Esta opción técnica modifica la ejecución y cambia la demanda relativa sin alterar el patrón principal del ejercicio.';
}

function optionDescription(aspect, option) {
  const key = normalize(`${aspect.id} ${option.id} ${option.name}`);
  if (key.includes('martillo') || key.includes('hammer')) return 'El agarre martillo mantiene las palmas enfrentadas y suele aumentar la participación del braquial y el braquiorradial.';
  if (key.includes('supino') || key.includes('supination')) return 'El agarre supino coloca las palmas hacia arriba y favorece la participación del bíceps en la flexión del codo.';
  if (key.includes('prono') || key.includes('inverso') || key.includes('pronation')) return 'El agarre pronado reduce la ventaja del bíceps y aumenta la demanda del braquial, braquiorradial y extensores.';
  if (key.includes('unilateral')) return 'La ejecución unilateral permite ajustar cada lado por separado y exige más control de la pelvis y el tronco.';
  if (key.includes('bilateral')) return 'La ejecución bilateral facilita una distribución simétrica de la carga y una progresión sencilla.';
  if (key.includes('inclinado') || key.includes('incline')) return 'El ángulo inclinado suele aumentar la demanda de las fibras superiores del músculo objetivo.';
  if (key.includes('declinado') || key.includes('decline')) return 'El ángulo declinado suele favorecer la porción inferior del músculo objetivo y cambia la línea de empuje.';
  if (key.includes('sentado') || key.includes('seated')) return 'La posición sentada reduce la contribución de las piernas y exige estabilizar la pelvis durante todo el recorrido.';
  if (key.includes('de pie') || key.includes('standing')) return 'La posición de pie requiere estabilizar el tronco y coordinar la fuerza con la cadena inferior.';
  if (key.includes('polea') || key.includes('cable')) return 'La polea mantiene tensión relativamente constante y permite ajustar con precisión la trayectoria.';
  if (key.includes('mancuerna') || key.includes('dumbbell')) return 'Las mancuernas permiten libertad de trayectoria y hacen visible cualquier diferencia entre ambos lados.';
  return `La opción ${option.name.toLowerCase()} modifica la ejecución para cambiar la demanda relativa; conserva un rango cómodo y controlado.`;
}

const db = loadDb();
let changed = 0;
for (const exercise of db) {
  const name = cleanName(exercise);
  const description = canonicalDescription(exercise, name);
  const aspects = (exercise.technicalAspects || []).map(aspect => ({
    ...aspect,
    description: String(aspect.description || '').trim() || aspectDescription(aspect),
    options: (aspect.options || []).map(option => ({
      ...option,
      description: String(option.description || '').trim() || optionDescription(aspect, option),
    })),
  }));
  if (name !== exercise.name || description !== exercise.description || aspects !== exercise.technicalAspects) changed++;
  exercise.name = name;
  exercise.description = description;
  if (aspects.length) exercise.technicalAspects = aspects;
}

// Keep canonical labels unique when two families intentionally share a common
// name (for example Reverse Hyper or Buenos Días).
const byName = new Map();
for (const exercise of db) {
  const key = normalize(exercise.name);
  const siblings = byName.get(key) || [];
  siblings.push(exercise);
  byName.set(key, siblings);
}
for (const siblings of byName.values()) {
  if (siblings.length < 2) continue;
  siblings.forEach((exercise, index) => {
    if (index === 0) return;
    const suffix = primaryMuscle(exercise)
      .replace(/\s+/g, ' ')
      .trim();
    exercise.name = `${exercise.name} · ${suffix || exercise.id}`;
  });
}
saveDb(db);
console.log(`Updated ${changed} exercise records.`);
