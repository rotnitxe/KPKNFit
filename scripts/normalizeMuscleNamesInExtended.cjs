/**
 * Normaliza nombres de músculos en exerciseDatabaseExtended.json
 * Reemplaza porciones (Pectoral Superior, etc.) por grupos unificados
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const JSON_PATH = path.join(ROOT, 'data', 'exerciseDatabaseExtended.json');

const MUSCLE_NORMALIZE = {
  'Pectoral Superior': 'Pectoral',
  'Pectoral Medio': 'Pectoral',
  'Pectoral Inferior': 'Pectoral',
  'Dorsal Ancho': 'Dorsales',
  'Trapecio Medio': 'Trapecio',
  'Trapecio Superior': 'Trapecio',
  'Trapecio Inferior': 'Trapecio',
  'Romboides': 'Dorsales',
  'Espalda Alta': 'Trapecio',
  'Recto Abdominal': 'Abdomen',
  'Transverso Abdominal': 'Abdomen',
  'Oblicuos': 'Abdomen',
};

const raw = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
let changed = 0;
for (const ex of raw) {
  const arr = ex.involvedMuscles;
  if (!Array.isArray(arr)) continue;
  for (const m of arr) {
    if (m && m.muscle && MUSCLE_NORMALIZE[m.muscle]) {
      m.muscle = MUSCLE_NORMALIZE[m.muscle];
      changed++;
    }
  }
}

fs.writeFileSync(JSON_PATH, JSON.stringify(raw, null, 2) + '\n', 'utf8');
console.log(`Normalizados ${changed} músculos en exerciseDatabaseExtended.json`);
