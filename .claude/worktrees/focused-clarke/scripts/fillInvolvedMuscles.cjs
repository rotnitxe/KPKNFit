/**
 * Rellena involvedMuscles vacíos en exerciseDatabaseExtended.json
 * usando la misma lógica que inferInvolvedMuscles + normalizeExtended
 */
const fs = require('fs');
const path = require('path');
const esbuild = require('esbuild');

const ROOT = path.join(__dirname, '..');
const JSON_PATH = path.join(ROOT, 'data', 'exerciseDatabaseExtended.json');
const INFER_PATH = path.join(ROOT, 'data', 'inferMusclesFromName.ts');
const TEMP_PATH = path.join(__dirname, 'temp-infer.cjs');

// Compilar inferMusclesFromName.ts
esbuild.buildSync({
  entryPoints: [INFER_PATH],
  outfile: TEMP_PATH,
  format: 'cjs',
  platform: 'node',
  bundle: true,
});

const { inferInvolvedMuscles } = require(TEMP_PATH);
fs.unlinkSync(TEMP_PATH);

function getCorrectedBodyPartAndForce(name, bodyPart, force) {
  const n = name.toLowerCase();
  let bp = bodyPart;
  let f = force;
  if (n.includes('sentadilla') || n.includes('prensa') || n.includes('zancada') || n.includes('pierna') || n.includes('cuádriceps') || n.includes('glúteo') || n.includes('gemelo') || n.includes('femoral') || n.includes('isquio')) {
    bp = 'lower';
    if (n.includes('sentadilla') || n.includes('prensa')) f = 'Sentadilla';
  }
  if (n.includes('peso muerto') || n.includes('remo') || n.includes('hip thrust') || n.includes('buenos días') || n.includes('rumanian') || n.includes('rdl')) {
    bp = 'lower';
    f = 'Bisagra';
  }
  if (n.includes('press') || n.includes('empuje') || n.includes('flexión') || n.includes('fondo')) f = 'Empuje';
  if (n.includes('dominada') || n.includes('remo') || n.includes('jalón') || n.includes('tirón') || n.includes('curl')) f = 'Tirón';
  return [bp, f];
}

const raw = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
let updated = 0;
for (const ex of raw) {
  const arr = ex.involvedMuscles;
  if (!Array.isArray(arr) || arr.length === 0) {
    const name = String(ex.name || '');
    const equipment = String(ex.equipment || '');
    const [bodyPart, force] = getCorrectedBodyPartAndForce(
      name,
      String(ex.bodyPart || 'upper'),
      String(ex.force || 'Otro')
    );
    ex.involvedMuscles = inferInvolvedMuscles(name, equipment, force, bodyPart);
    updated++;
  }
}

fs.writeFileSync(JSON_PATH, JSON.stringify(raw, null, 2) + '\n', 'utf8');
console.log(`Actualizados ${updated} ejercicios con involvedMuscles en exerciseDatabaseExtended.json`);
