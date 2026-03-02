/**
 * Auditoría completa de ejercicios duplicados.
 * Detecta: mismo nombre, nombres normalizados idénticos, y variantes semánticas cercanas.
 * Ejecutar: npx esbuild scripts/auditExerciseDuplicates.ts --bundle --platform=node --outfile=scripts/audit-out.mjs && node scripts/audit-out.mjs
 */
import { FULL_EXERCISE_LIST } from '../data/exerciseDatabaseMerged';
import { createWriteStream } from 'fs';
import { join } from 'path';

// Normalizar texto: minúsculas, quitar acentos, colapsar espacios, quitar paréntesis y su contenido
function normalize(str: string): string {
  return str
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '') // quitar marcas diacríticas
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\s*\([^)]*\)\s*/g, ' ') // quitar (Barra), (Mancuerna), etc.
    .replace(/\s+/g, ' ')
    .trim();
}

// Base del nombre sin equipo para agrupar variantes
function baseName(name: string): string {
  const n = normalize(name);
  // Quitar sufijos comunes de equipo que a veces están en el nombre
  return n
    .replace(/\s*(barra|mancuerna|polea|maquina|peso corporal|banda|kettlebell|cable)\s*$/i, '')
    .replace(/\s+(en|con|de)\s+(barra|mancuerna|polea|maquina)\s*$/i, '')
    .trim();
}

// Similaridad Jaccard de palabras (para detectar "Press de Banca" vs "Banca Press")
function wordOverlap(a: string, b: string): number {
  const wordsA = new Set(normalize(a).split(/\s+/).filter(Boolean));
  const wordsB = new Set(normalize(b).split(/\s+/).filter(Boolean));
  const intersection = [...wordsA].filter((w) => wordsB.has(w)).length;
  const union = new Set([...wordsA, ...wordsB]).size;
  return union === 0 ? 0 : intersection / union;
}

// Levenshtein simplificado para similitud de cadenas
function levenshteinSimilarity(a: string, b: string): number {
  const na = normalize(a);
  const nb = normalize(b);
  if (na === nb) return 1;
  const matrix: number[][] = [];
  for (let i = 0; i <= na.length; i++) matrix[i] = [i];
  for (let j = 0; j <= nb.length; j++) matrix[0][j] = j;
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

interface DuplicateGroup {
  type: 'exact' | 'normalized' | 'semantic';
  exercises: { id: string; name: string; equipment?: string; source?: string }[];
  suggestedCanonical?: string;
}

const list = FULL_EXERCISE_LIST;
const report: string[] = [];
const duplicateGroups: DuplicateGroup[] = [];

report.push('='.repeat(80));
report.push('AUDITORÍA DE EJERCICIOS DUPLICADOS - KPKN FIT');
report.push('='.repeat(80));
report.push(`Total de ejercicios: ${list.length}`);
report.push('');

// 1. Duplicados exactos (mismo nombre caso-insensitive)
const byExactName = new Map<string, typeof list>();
for (const ex of list) {
  const key = ex.name.toLowerCase().trim();
  if (!byExactName.has(key)) byExactName.set(key, []);
  byExactName.get(key)!.push(ex);
}
const exactDupes = [...byExactName.entries()].filter(([, arr]) => arr.length > 1);
if (exactDupes.length > 0) {
  report.push('--- 1. DUPLICADOS EXACTOS (mismo nombre) ---');
  for (const [name, arr] of exactDupes) {
    report.push(`  "${name}": ${arr.length} entradas`);
    duplicateGroups.push({
      type: 'exact',
      exercises: arr.map((e) => ({ id: e.id, name: e.name, equipment: e.equipment })),
      suggestedCanonical: arr[0].name,
    });
    for (const e of arr) report.push(`    - ${e.id} | ${e.name}`);
    report.push('');
  }
}

// 2. Duplicados por nombre normalizado (sin acentos, sin paréntesis de equipo)
const byNormalized = new Map<string, typeof list>();
for (const ex of list) {
  const key = normalize(ex.name);
  if (!key) continue;
  if (!byNormalized.has(key)) byNormalized.set(key, []);
  byNormalized.get(key)!.push(ex);
}
const normalizedDupes = [...byNormalized.entries()].filter(([, arr]) => arr.length > 1);
// Solo grupos donde hay nombres originales distintos (ej. "Press de Banca" vs "Press De Banca")
const newNormalized = normalizedDupes.filter(([, arr]) => new Set(arr.map((e) => e.name)).size > 1);
if (newNormalized.length > 0) {
  report.push('--- 2. DUPLICADOS POR NOMBRE NORMALIZADO ---');
  for (const [norm, arr] of newNormalized) {
    const names = [...new Set(arr.map((e) => e.name))];
    if (names.length <= 1) continue; // ya cubierto en exactos
    report.push(`  Normalized: "${norm}" (${arr.length} entradas)`);
    duplicateGroups.push({
      type: 'normalized',
      exercises: arr.map((e) => ({ id: e.id, name: e.name, equipment: e.equipment })),
      suggestedCanonical: arr[0].name,
    });
    for (const e of arr) report.push(`    - ${e.id} | ${e.name}`);
    report.push('');
  }
}

// 3. Duplicados semánticos (nombres muy similares, mismo ejercicio conceptual)
const checked = new Set<string>();
const semanticDupes: { a: (typeof list)[0]; b: (typeof list)[0]; score: number }[] = [];
for (let i = 0; i < list.length; i++) {
  for (let j = i + 1; j < list.length; j++) {
    const a = list[i];
    const b = list[j];
    const pairKey = [a.id, b.id].sort().join('|');
    if (checked.has(pairKey)) continue;
    checked.add(pairKey);
    const baseA = baseName(a.name);
    const baseB = baseName(b.name);
    if (baseA.length < 4 || baseB.length < 4) continue;
    const wordSim = wordOverlap(a.name, b.name);
    const levSim = levenshteinSimilarity(baseA, baseB);
    const score = 0.6 * wordSim + 0.4 * levSim;
    if (score >= 0.85 && a.id !== b.id) {
      semanticDupes.push({ a, b, score });
    }
  }
}
if (semanticDupes.length > 0) {
  report.push('--- 3. POSIBLES DUPLICADOS SEMÁNTICOS (similitud >= 85%) ---');
  const seen = new Set<string>();
  for (const { a, b, score } of semanticDupes) {
    const key = [a.id, b.id].sort().join('|');
    if (seen.has(key)) continue;
    seen.add(key);
    report.push(`  Score ${(score * 100).toFixed(0)}%: "${a.name}" <-> "${b.name}"`);
    report.push(`    IDs: ${a.id} | ${b.id}`);
    duplicateGroups.push({
      type: 'semantic',
      exercises: [
        { id: a.id, name: a.name, equipment: a.equipment },
        { id: b.id, name: b.name, equipment: b.equipment },
      ],
      suggestedCanonical: a.name,
    });
    report.push('');
  }
}

// Resumen
report.push('='.repeat(80));
report.push('RESUMEN');
report.push('='.repeat(80));
report.push(`Duplicados exactos: ${exactDupes.length} grupos`);
report.push(`Duplicados por normalización: ${newNormalized.length} grupos`);
report.push(`Posibles duplicados semánticos: ${semanticDupes.length} pares`);
const totalDupeExercises = [
  ...exactDupes.flatMap(([, arr]) => arr),
  ...newNormalized.flatMap(([, arr]) => arr),
  ...semanticDupes.flatMap(({ a, b }) => [a, b]),
];
const uniqueDupeIds = new Set(totalDupeExercises.map((e) => e.id));
report.push(`Ejercicios afectados (IDs únicos): ${uniqueDupeIds.size}`);
report.push('');

// Escribir reporte
const outPath = join(process.cwd(), 'scripts', 'audit-exercise-duplicates-report.txt');
const ws = createWriteStream(outPath, { encoding: 'utf-8' });
ws.write(report.join('\n'));
ws.end();
console.log(report.join('\n'));
console.log(`\nReporte guardado en: ${outPath}`);
