/**
 * Dataset Processor for KPKN Fit
 * 
 * Processes DATASET_KPKN_TRINIDAD_MASTER.json (19,405 entries) into optimized Kotlin code.
 * Generates DatasetKnowledge.kt with:
 * - TF-IDF inverted index for semantic search
 * - Portion triplets (food, portion, grams)
 * - Context profiles (casino, post-entreno, etc.)
 * - Macro ranges by description type
 * - Vocabulary set for normalization
 * 
 * Usage: node scripts/process-dataset.mjs
 */

import fs from 'fs';
import path from 'path';

const DATASET_PATH = path.join(process.cwd(), 'DATASET_KPKN_TRINIDAD_MASTER.json');
const OUTPUT_PATH = path.join(process.cwd(), 'app/src/main/java/com/example/kpkn/domain/nutrition/DatasetKnowledge.kt');

const SPANISH_STOPWORDS = new Set([
  'de', 'la', 'el', 'con', 'sin', 'a', 'al', 'en', 'por', 'y', 'o', 'un', 'una',
  'unos', 'unas', 'del', 'las', 'los', 'lo', 'para', 'que', 'es', 'su', 'se',
  'no', 'más', 'como', 'le', 'me', 'te', 'mi', 'tu', 'su', 'muy', 'ya', 'si',
  'pero', 'porque', 'cuando', 'donde', 'cual', 'cuales', 'quien', 'quienes',
  'este', 'esta', 'estos', 'estas', 'ese', 'esa', 'esos', 'esas', 'aquel',
  'aquella', 'todos', 'todas', 'todo', 'toda', 'otro', 'otra', 'otros', 'otras',
  'mismo', 'misma', 'mismos', 'mismas', 'cada', 'cualquier', 'cualquiera',
  'sobre', 'entre', 'hasta', 'desde', 'hacia', 'tras', 'durante', 'mediante',
  'según', 'ante', 'bajo', 'contra', 'hay', 'son', 'fue', 'era', 'tiene',
  'tienen', 'puede', 'pueden', 'debe', 'deben', 'calcula', 'los', 'las',
  'cuáles', 'cuáles', 'oficiales', 'para', 'gramos', 'calorías', 'macros',
]);

const CONTEXT_KEYWORDS = {
  'CASINO': ['casino', 'cafetería', 'cafeteria', 'comedor', 'buffet del', 'menú del día', 'menu del dia'],
  'POST_ENTRENO': ['post-entreno', 'post entreno', 'post-entrenamiento', 'recuperación', 'recuperacion', 'post-sentadillas', 'post-pecho', 'post-espalda', 'post-pierna', 'post-brazo'],
  'POWERBUILDER': ['powerbuilder', 'power builder', 'volumen extremo', 'masa extrema', 'desayuno de powerbuilder'],
  'ABUELA_CHILENA': ['abuela chilena', 'abuela', 'contundente', 'plato hondo', 'tazón grande', 'plato rebosante'],
  'OFICINA': ['oficina', 'escritorio', 'trabajo', 'reunión', 'reunion', 'break de oficina'],
  'ESTUDIANTE': ['estudiante', 'universidad', 'facultad', 'campus', 'barato', 'corto de lucas', 'sobrevivencia'],
  'CONDOMINIO': ['condominio', 'edificio', 'departamento', 'casa'],
  'SNACK': ['snack', 'colación', 'colacion', 'merendola', 'merienda', 'tentempié', 'tentempie', 'piscolabis', 'refrigerio'],
  'DESAYUNO': ['desayuno', 'desayunar', 'am', 'mañana', 'mañana'],
  'ALMUERZO': ['almuerzo', 'almorzar', 'mediodía', 'mediodia', 'pm', 'tarde'],
  'CENA': ['cena', 'cenar', 'noche', 'nocturno', 'antes de dormir'],
};

const INTENSIFIERS = {
  'GIGANTE': ['gigante', 'gigantesca', 'gigantesco', 'enorme', 'descomunal', 'bestial'],
  'GENEROSO': ['generoso', 'generosa', 'generosos', 'generosas'],
  'COLMADO': ['colmado', 'colmada', 'colmados', 'colmadas', 'hasta el borde', 'rebosante', 'rebosantes', 'lleno', 'llena', 'repleto'],
  'GRANDE': ['grande', 'grandes', 'gran'],
  'PEQUEÑO': ['pequeño', 'pequeña', 'pequeñas', 'pequeños', 'chico', 'chica', 'chicos', 'chicas'],
  'FINO': ['fino', 'fina', 'finos', 'finas', 'delgado', 'delgada', 'delgados', 'delgadas'],
  'GRUESO': ['grueso', 'gruesa', 'gruesos', 'gruesas', 'gordo', 'gorda'],
};

function normalizeText(text) {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function tokenize(text) {
  return normalizeText(text)
    .split(' ')
    .filter(w => w.length >= 2 && !SPANISH_STOPWORDS.has(w));
}

function extractGrams(text) {
  const grams = [];
  const regex = /(\d+(?:[.,]\d+)?)\s*g\b/g;
  let match;
  while ((match = regex.exec(text)) !== null) {
    const val = parseFloat(match[1].replace(',', '.'));
    if (val > 0 && val <= 2000) {
      grams.push(val);
    }
  }
  return grams;
}

function extractMacros(text) {
  const result = { kcal: null, protein: null, fats: null, carbs: null };
  
  const kcalMatch = text.match(/(\d+(?:[.,]\d+)?)\s*kcal/i);
  if (kcalMatch) result.kcal = parseFloat(kcalMatch[1].replace(',', '.'));
  
  const proteinMatch = text.match(/(\d+(?:[.,]\d+)?)\s*[pP]/g);
  if (proteinMatch) {
    const last = proteinMatch[proteinMatch.length - 1];
    const val = parseFloat(last.replace(',', '.'));
    if (val > 0 && val <= 500) result.protein = val;
  }
  
  const fatsMatch = text.match(/(\d+(?:[.,]\d+)?)\s*[gG](?:rasas?|)/g);
  if (fatsMatch) {
    const last = fatsMatch[fatsMatch.length - 1];
    const val = parseFloat(last.replace(',', '.'));
    if (val > 0 && val <= 300) result.fats = val;
  }
  
  const carbsMatch = text.match(/(\d+(?:[.,]\d+)?)\s*[cC](?:arbohidratos?|)/g);
  if (carbsMatch) {
    const last = carbsMatch[carbsMatch.length - 1];
    const val = parseFloat(last.replace(',', '.'));
    if (val > 0 && val <= 500) result.carbs = val;
  }
  
  return result;
}

function classifyInstruction(instruction) {
  const inst = instruction.toLowerCase();
  if (inst.includes('busca en la base de datos') || inst.includes('consulta en la base de datos') || inst.includes('valores nutricionales oficiales')) {
    return 'DATABASE_LOOKUP';
  }
  if (inst.includes('calcula macros') || inst.includes('calcula las calorías') || inst.includes('proporciona datos nutricionales') || inst.includes('dime los macronutrientes')) {
    return 'MACRO_CALC';
  }
  if (inst.includes('describe')) {
    return 'DESCRIBE';
  }
  if (inst.includes('?') || inst.startsWith('qué') || inst.startsWith('cuál') || inst.startsWith('sirven')) {
    return 'QUESTION';
  }
  return 'GENERAL';
}

function detectContexts(instruction) {
  const inst = instruction.toLowerCase();
  const detected = [];
  for (const [context, keywords] of Object.entries(CONTEXT_KEYWORDS)) {
    if (keywords.some(kw => inst.includes(kw))) {
      detected.push(context);
    }
  }
  return detected;
}

function detectIntensifiers(instruction) {
  const inst = instruction.toLowerCase();
  const detected = [];
  for (const [intensifier, keywords] of Object.entries(INTENSIFIERS)) {
    if (keywords.some(kw => inst.includes(kw))) {
      detected.push(intensifier);
    }
  }
  return detected;
}

function extractFoodPortionPairs(instruction) {
  const pairs = [];
  const inst = instruction;
  
  // Pattern: "Xg de food" or "Xg food"
  const gramPattern = /(\d+(?:[.,]\d+)?)\s*g\s+de\s+([a-záéíóúñü\s]+?)(?:\s*,|\s+y\s+|\s*\(|\s*$)/gi;
  let match;
  while ((match = gramPattern.exec(inst)) !== null) {
    const grams = parseFloat(match[1].replace(',', '.'));
    const food = match[2].trim().replace(/\s+/g, ' ');
    if (grams > 0 && grams <= 2000 && food.length >= 2) {
      pairs.push({ food, grams });
    }
  }
  
  // Pattern: "food (Xg)"
  const parenPattern = /([a-záéíóúñü\s]+?)\s*\((\d+(?:[.,]\d+)?)\s*g\)/gi;
  while ((match = parenPattern.exec(inst)) !== null) {
    const grams = parseFloat(match[2].replace(',', '.'));
    const food = match[1].trim().replace(/\s+/g, ' ');
    if (grams > 0 && grams <= 2000 && food.length >= 2) {
      pairs.push({ food, grams });
    }
  }
  
  return pairs;
}

function buildTrigrams(word) {
  if (word.length < 3) return [word];
  const padded = `$$${word}$$`;
  const trigrams = new Set();
  for (let i = 0; i < padded.length - 2; i++) {
    trigrams.add(padded.substring(i, i + 3));
  }
  return Array.from(trigrams);
}

// ─── Main Processing ─────────────────────────────────────────────────────────

console.log('Loading dataset...');
const rawData = fs.readFileSync(DATASET_PATH, 'utf-8');
const dataset = JSON.parse(rawData);
console.log(`Loaded ${dataset.length} entries`);

// Process each entry
const entries = [];
const tfidfDocs = [];
const vocabulary = new Set();
const contextProfiles = {};
const macroRanges = {};
const portionTriplets = [];
const typeCounts = {};

for (let i = 0; i < dataset.length; i++) {
  const item = dataset[i];
  const instruction = item.instruction;
  const output = item.output;
  
  const normalized = normalizeText(instruction);
  const tokens = tokenize(instruction);
  const trigrams = new Set();
  for (const token of tokens) {
    vocabulary.add(token);
    buildTrigrams(token).forEach(t => trigrams.add(t));
  }
  
  const type = classifyInstruction(instruction);
  typeCounts[type] = (typeCounts[type] || 0) + 1;
  
  const contexts = detectContexts(instruction);
  const intensifiers = detectIntensifiers(instruction);
  const grams = extractGrams(output);
  const macros = extractMacros(output);
  const foodPairs = extractFoodPortionPairs(instruction);
  
  entries.push({
    id: i,
    instruction,
    normalized,
    tokens,
    trigrams: Array.from(trigrams),
    type,
    contexts,
    intensifiers,
    grams,
    macros,
    foodPairs,
  });
  
  tfidfDocs.push({ id: i, tokens, trigrams: Array.from(trigrams) });
  
  // Build context profiles
  for (const ctx of contexts) {
    if (!contextProfiles[ctx]) {
      contextProfiles[ctx] = { count: 0, typicalGrams: [], typicalMacros: { kcal: [], protein: [], fats: [], carbs: [] } };
    }
    contextProfiles[ctx].count++;
    if (grams.length > 0) contextProfiles[ctx].typicalGrams.push(...grams);
    if (macros.kcal) contextProfiles[ctx].typicalMacros.kcal.push(macros.kcal);
    if (macros.protein) contextProfiles[ctx].typicalMacros.protein.push(macros.protein);
    if (macros.fats) contextProfiles[ctx].typicalMacros.fats.push(macros.fats);
    if (macros.carbs) contextProfiles[ctx].typicalMacros.carbs.push(macros.carbs);
  }
  
  // Build macro ranges by type
  if (!macroRanges[type]) {
    macroRanges[type] = { count: 0, kcal: [], protein: [], fats: [], carbs: [] };
  }
  macroRanges[type].count++;
  if (macros.kcal) macroRanges[type].kcal.push(macros.kcal);
  if (macros.protein) macroRanges[type].protein.push(macros.protein);
  if (macros.fats) macroRanges[type].fats.push(macros.fats);
  if (macros.carbs) macroRanges[type].carbs.push(macros.carbs);
  
  // Build portion triplets
  for (const pair of foodPairs) {
    portionTriplets.push({
      food: pair.food,
      grams: pair.grams,
      docId: i,
    });
  }
}

// Build TF-IDF index
console.log('Building TF-IDF index...');
const tfidfTokenIndex = {};
const tfidfTrigramIndex = {};
const totalDocs = tfidfDocs.length;

for (const doc of tfidfDocs) {
  const tokenFreq = {};
  for (const token of doc.tokens) {
    tokenFreq[token] = (tokenFreq[token] || 0) + 1;
  }
  
  for (const [tok, freq] of Object.entries(tokenFreq)) {
    const existing = tfidfTokenIndex[tok];
    if (existing === undefined) {
      tfidfTokenIndex[tok] = [];
      tfidfTokenIndex[tok].push({ docId: doc.id, tf: freq / doc.tokens.length });
    } else if (Array.isArray(existing)) {
      existing.push({ docId: doc.id, tf: freq / doc.tokens.length });
    }
  }
  
  for (const trigram of doc.trigrams) {
    if (!tfidfTrigramIndex[trigram]) {
      tfidfTrigramIndex[trigram] = [];
    }
    tfidfTrigramIndex[trigram].push(doc.id);
  }
}

// Calculate IDF
for (const [tok, docs] of Object.entries(tfidfTokenIndex)) {
  const idf = Math.log(totalDocs / docs.length);
  for (const entry of docs) {
    entry.tfidf = entry.tf * idf;
  }
}

// Deduplicate and sort portion triplets
const tripletMap = {};
for (const triplet of portionTriplets) {
  const key = triplet.food.toLowerCase().trim();
  if (!tripletMap[key]) {
    tripletMap[key] = { food: triplet.food, grams: [], count: 0 };
  }
  tripletMap[key].grams.push(triplet.grams);
  tripletMap[key].count++;
}

const dedupedTriplets = Object.values(tripletMap)
  .filter(t => t.count >= 1)
  .map(t => ({
    food: t.food,
    grams: Math.round(t.grams.reduce((a, b) => a + b, 0) / t.grams.length * 10) / 10,
    count: t.count,
  }))
  .sort((a, b) => b.count - a.count)
  .slice(0, 800); // Top 800 most frequent

// Sort TF-IDF indices by tfidf score
for (const [token, docs] of Object.entries(tfidfTokenIndex)) {
  tfidfTokenIndex[token] = docs
    .sort((a, b) => b.tfidf - a.tfidf)
    .slice(0, 15)
    .map(d => ({ docId: d.docId, score: Math.round(d.tfidf * 100) / 100 }));
}

for (const [trigram, docs] of Object.entries(tfidfTrigramIndex)) {
  tfidfTrigramIndex[trigram] = [...new Set(docs)].slice(0, 10);
}

// ─── Generate Kotlin Code ────────────────────────────────────────────────────

console.log('Generating Kotlin code...');

function escapeKotlinString(str) {
  return str
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\$/g, '\\$')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t');
}

function calcRange(values) {
  if (values.length === 0) return [0.0, 0.0, 0.0];
  const sorted = [...values].sort((a, b) => a - b);
  return [sorted[0], sorted[sorted.length - 1], sorted[Math.floor(sorted.length / 2)]];
}

function kotlinList(items, indent = '        ') {
  if (items.length === 0) return 'emptyList()';
  if (items.length <= 5) {
    return `listOf(${items.join(', ')})`;
  }
  return `listOf(\n${indent}    ${items.join(',\n' + indent + '    ')}\n${indent})`;
}

const vocabArray = Array.from(vocabulary).sort().slice(0, 1000); // Top 1000 words

let kotlinCode = `package com.example.kpkn.domain.nutrition

/**
 * DatasetKnowledge — Auto-generated from DATASET_KPKN_TRINIDAD_MASTER.json
 * 
 * Contains:
 * - TF-IDF inverted index for semantic portion retrieval
 * - Portion triplets (food → average grams from dataset)
 * - Context profiles (casino, post-entreno, etc.)
 * - Macro ranges by description type
 * - Vocabulary set for normalization
 * 
 * Generated from ${dataset.length} dataset entries.
 * DO NOT EDIT MANUALLY. Regenerate with: node scripts/process-dataset.mjs
 */
object DatasetKnowledge {

    // ─── Metadata ──────────────────────────────────────────────────────────
    
    const val DATASET_SIZE = ${dataset.length}
    const val VOCABULARY_SIZE = ${vocabArray.length}
    const val TRIPLET_COUNT = ${dedupedTriplets.length}

    // ─── Type Distribution ─────────────────────────────────────────────────
    
    val TYPE_COUNTS: Map<String, Int> = mapOf(
${Object.entries(typeCounts).map(([k, v]) => `        "${k}" to ${v}`).join(',\n')}
    )

    // ─── TF-IDF Token Index ────────────────────────────────────────────────
    // Maps each token to list of (docId, tfidfScore) sorted by relevance
    
    val TFIDF_TOKEN_INDEX: Map<String, String> = mapOf(
${Object.entries(tfidfTokenIndex).slice(0, 800).map(([token, docs]) => {
  const compact = docs.map(d => `${d.docId}:${d.score}`).join(',');
  return `        "${escapeKotlinString(token)}" to "${compact}"`;
}).join(',\n')}
    )

    // ─── TF-IDF Trigram Index ──────────────────────────────────────────────
    // Maps each trigram to comma-separated docIds for fuzzy matching
    
    val TFIDF_TRIGRAM_INDEX: Map<String, String> = mapOf(
${Object.entries(tfidfTrigramIndex).slice(0, 800).map(([trigram, docs]) => {
  return `        "${escapeKotlinString(trigram)}" to "${docs.join(',')}"`;
}).join(',\n')}
    )

    // ─── Portion Triplets (food → average grams) ───────────────────────────
    // Extracted from dataset entries with explicit gram measurements
    
    data class PortionTriplet(
        val food: String,
        val grams: Double,
        val frequency: Int,
    )
    
    val PORTION_TRIPLETS: List<PortionTriplet> = listOf(
${dedupedTriplets.map(t => `        PortionTriplet("${escapeKotlinString(t.food)}", ${t.grams.toFixed(1)}, ${t.count})`).join(',\n')}
    )

    // ─── Context Profiles ──────────────────────────────────────────────────
    // Typical portions and macros for each detected context
    
    data class ContextProfile(
        val count: Int,
        val typicalGrams: List<Double>,
        val typicalKcal: List<Double>,
        val typicalProtein: List<Double>,
        val typicalFats: List<Double>,
        val typicalCarbs: List<Double>,
    )
    
    val CONTEXT_PROFILES: Map<String, ContextProfile> = mapOf(
${Object.entries(contextProfiles).map(([ctx, profile]) => {
  const grams = profile.typicalGrams.slice(0, 20).map(g => g.toFixed(1)).join(', ');
  const kcal = profile.typicalMacros.kcal.slice(0, 20).map(g => g.toFixed(1)).join(', ');
  const protein = profile.typicalMacros.protein.slice(0, 20).map(g => g.toFixed(1)).join(', ');
  const fats = profile.typicalMacros.fats.slice(0, 20).map(g => g.toFixed(1)).join(', ');
  const carbs = profile.typicalMacros.carbs.slice(0, 20).map(g => g.toFixed(1)).join(', ');
  return `        "${ctx}" to ContextProfile(${profile.count}, listOf(${grams}), listOf(${kcal}), listOf(${protein}), listOf(${fats}), listOf(${carbs}))`;
}).join(',\n')}
    )

    // ─── Macro Ranges by Type ──────────────────────────────────────────────
    
    data class MacroRange(
        val count: Int,
        val kcalMin: Double,
        val kcalMax: Double,
        val kcalMedian: Double,
        val proteinMin: Double,
        val proteinMax: Double,
        val proteinMedian: Double,
        val fatsMin: Double,
        val fatsMax: Double,
        val fatsMedian: Double,
        val carbsMin: Double,
        val carbsMax: Double,
        val carbsMedian: Double,
    )
    
    fun calcRange(values: List<Double>): Triple<Double, Double, Double> {
        if (values.isEmpty()) return Triple(0.0, 0.0, 0.0)
        val sorted = values.sorted()
        return Triple(sorted.first(), sorted.last(), sorted[sorted.size / 2])
    }
    
    val MACRO_RANGES: Map<String, MacroRange> = mapOf(
${Object.entries(macroRanges).map(([type, range]) => {
  const kcal = calcRange(range.kcal);
  const protein = calcRange(range.protein);
  const fats = calcRange(range.fats);
  const carbs = calcRange(range.carbs);
  return `        "${type}" to MacroRange(${range.count}, ${kcal[0].toFixed(1)}, ${kcal[1].toFixed(1)}, ${kcal[2].toFixed(1)}, ${protein[0].toFixed(1)}, ${protein[1].toFixed(1)}, ${protein[2].toFixed(1)}, ${fats[0].toFixed(1)}, ${fats[1].toFixed(1)}, ${fats[2].toFixed(1)}, ${carbs[0].toFixed(1)}, ${carbs[1].toFixed(1)}, ${carbs[2].toFixed(1)})`;
}).join(',\n')}
    )

    // ─── Vocabulary Set ────────────────────────────────────────────────────
    // Unique words from dataset for normalization and synonym matching
    
    val VOCABULARY: Set<String> = setOf(
${vocabArray.map(w => `        "${escapeKotlinString(w)}"`).join(',\n')}
    )

    // ─── Context Keywords ──────────────────────────────────────────────────
    
    val CONTEXT_KEYWORDS: Map<String, List<String>> = mapOf(
${Object.entries(CONTEXT_KEYWORDS).map(([ctx, kws]) => {
  return `        "${ctx}" to listOf(${kws.map(k => `"${k}"`).join(', ')})`;
}).join(',\n')}
    )

    // ─── Intensifier Keywords ──────────────────────────────────────────────
    
    val INTENSIFIER_KEYWORDS: Map<String, List<String>> = mapOf(
${Object.entries(INTENSIFIERS).map(([intens, kws]) => {
  return `        "${intens}" to listOf(${kws.map(k => `"${k}"`).join(', ')})`;
}).join(',\n')}
    )

    // ─── Dataset Instructions (for semantic search) ────────────────────────
    // Stored as array for indexed access by docId (top 3000 most useful)
    
    val INSTRUCTIONS: Array<String> = arrayOf(
${entries.filter(e => e.type !== 'DATABASE_LOOKUP').slice(0, 3000).map(e => `        "${escapeKotlinString(e.instruction.substring(0, 200))}"`).join(',\n')}
    )

    // ─── Entry Types ───────────────────────────────────────────────────────
    
    val ENTRY_TYPES: Array<String> = arrayOf(
${entries.filter(e => e.type !== 'DATABASE_LOOKUP').slice(0, 3000).map(e => `        "${e.type}"`).join(',\n')}
    )
}
`;

// Write output
fs.mkdirSync(path.dirname(OUTPUT_PATH), { recursive: true });
fs.writeFileSync(OUTPUT_PATH, kotlinCode, 'utf-8');

console.log(`\n✅ Generated ${OUTPUT_PATH}`);
console.log(`   Dataset entries: ${dataset.length}`);
console.log(`   Vocabulary words: ${vocabArray.length}`);
console.log(`   TF-IDF tokens: ${Object.keys(tfidfTokenIndex).length}`);
console.log(`   TF-IDF trigrams: ${Object.keys(tfidfTrigramIndex).length}`);
console.log(`   Portion triplets: ${dedupedTriplets.length}`);
console.log(`   Context profiles: ${Object.keys(contextProfiles).length}`);
console.log(`   Macro ranges: ${Object.keys(macroRanges).length}`);
console.log(`   Type distribution: ${JSON.stringify(typeCounts)}`);
