// apply_catalog_p1_curl.js
// Fusiona la matriz de Curl de Bíceps (174→~12 canónicos + 3 specialty).

const fs = require('fs');
const {
  loadDb, saveDb, loadAliases, saveAliases,
  makeAspect, makeOption,
  mergeInto, rebuildCatalog, finalizeAliases,
} = require('./catalog_transform_base');

const db = loadDb();
const aliases = loadAliases();
const byId = new Map(db.map(e => [e.id, e]));
const removeIds = new Set();
const aliasAdds = {};
const applied = [];

const KEEP = new Set([
  'biceps_curl_zottman_mancuernas',
  'biceps_curl_disco',
  'biceps_curl_trx',
]);

// Setup → { baseId, gripType, implement, laterality, sourceId }
const setupMap = new Map();

for (const e of db) {
  if (!e.id.startsWith('biceps_curl')) continue;
  if (KEEP.has(e.id)) continue;

  const m = e.id.match(/^biceps_curl_([^_]+(?:_[^_]+)*)_(supino|martillo|inverso|supinacion)_(barra_recta|barra_ez|barra_neutra|mancuernas|kettlebell|polea|banda|maquina|disco)(_unilateral)?$/);
  if (!m) {
    // skip already-merged or unmatched (shouldn't happen post-P0)
    console.log('SKIP unmatched:', e.id);
    continue;
  }

  const setup = m[1];
  const grip = m[2];
  const implement = m[3];
  const laterality = m[4] ? 'unilateral' : 'bilateral';
  const canonicalId = `biceps_curl_${setup}`;

  if (!setupMap.has(canonicalId)) {
    setupMap.set(canonicalId, {
      setup,
      base: e,
      sources: [],
    });
  }
  setupMap.get(canonicalId).sources.push({
    id: e.id,
    grip,
    implement,
    laterality,
  });
}

function gripModifiers(baseMuscles, grip) {
  if (grip === 'supino') return [['Bíceps', 'add', 0.1]];
  if (grip === 'martillo') return [['Antebrazo', 'add', 0.1]];
  if (grip === 'inverso') return [['Bíceps', 'add', -0.15], ['Antebrazo', 'add', 0.15]];
  if (grip === 'supinacion') return [['Bíceps', 'add', 0.15]];
  return [];
}

function implementModifiers(baseMuscles, implement) {
  if (implement === 'polea') return [['Bíceps', 'add', 0.05]]; // tensión constante
  if (implement === 'banda') return [['Bíceps', 'add', 0.02]];
  if (implement === 'barra_recta') return [['Bíceps', 'add', -0.02]];
  return [];
}

for (const [canonicalId, info] of setupMap) {
  const base = info.base;
  const c = { ...base, id: canonicalId };
  c.name = `Curl de Bíceps (${info.setup.replace(/_/g, ' ')})`;
  c.description = `Curl de bíceps en setup ${info.setup.replace(/_/g, ' ')}. Ajustable en tipo de agarre, implemento y lateralidad.`;
  c.alias = c.name;

  // Collect all unique values
  const grips = [...new Set(info.sources.map(s => s.grip))];
  const implements = [...new Set(info.sources.map(s => s.implement))];
  const lateralities = [...new Set(info.sources.map(s => s.laterality))];

  c.technicalAspects = [
    makeAspect('grip_type', 'Tipo de agarre', 'Orientación de las manos.', 'supino',
      grips.map(g => makeOption(base.involvedMuscles, g, g.charAt(0).toUpperCase() + g.slice(1), '', gripModifiers(base.involvedMuscles, g))),
    ),
    makeAspect('implement', 'Implemento', 'Equipo utilizado.', 'mancuernas',
      implements.map(imp => makeOption(base.involvedMuscles, imp, imp.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()), '', implementModifiers(base.involvedMuscles, imp))),
    ),
  ];

  if (lateralities.length > 1 || lateralities[0] === 'unilateral') {
    c.technicalAspects.push(
      makeAspect('laterality', 'Lateralidad', 'Bilateral o unilateral.', 'bilateral', [
        makeOption(base.involvedMuscles, 'bilateral', 'Bilateral', 'Ambos brazos.', []),
        makeOption(base.involvedMuscles, 'unilateral', 'Unilateral', 'Un brazo; mayor control y core.', [['Bíceps', 'add', 0.05]]),
      ]),
    );
  }

  byId.set(canonicalId, c);

  for (const s of info.sources) {
    const aspects = { grip_type: s.grip, implement: s.implement };
    if (lateralities.length > 1) aspects.laterality = s.laterality;
    mergeInto(s.id, canonicalId, aspects, `Curl ${info.setup}`, { byId, removeIds, aliasAdds, applied });
  }
}

// Keep specialties intact
for (const id of KEEP) {
  const e = byId.get(id);
  if (!e) continue;
  e.technicalAspects = [
    makeAspect('grip_type', 'Tipo de agarre', '', 'supino', [
      makeOption(e.involvedMuscles, 'supino', 'Supino', '', [['Bíceps', 'add', 0.1]]),
      makeOption(e.involvedMuscles, 'martillo', 'Martillo', '', [['Antebrazo', 'add', 0.1]]),
    ]),
  ];
}

const placements = [...setupMap.keys()].map((cid, i) => {
  const firstSource = setupMap.get(cid).sources[0].id;
  return [cid, firstSource];
});

const out = rebuildCatalog(db, byId, removeIds, placements);
finalizeAliases(aliases, aliasAdds, removeIds, byId);

saveDb(out);
saveAliases(aliases);

const withTA = out.filter(e => e.technicalAspects && e.technicalAspects.length > 0);
console.log('=== P1b Curl de Bíceps ===');
console.log('Filas:', db.length, '→', out.length);
console.log('Eliminadas:', removeIds.size);
console.log('Con technicalAspects:', withTA.length, 'de', out.length);

const root = require('path').join(__dirname, '..');
const auditPath = require('path').join(root, '../docs/EXERCISE_CATALOG_AUDIT.md');
const report = [
  '',
  '## Aplicado — oleada P1b Curl de Bíceps',
  '',
  `- Filas antes: ${db.length}`,
  `- Filas después: ${out.length}`,
  `- Eliminadas/fusionadas: ${removeIds.size}`,
  `- Aliases nuevos: ${Object.keys(aliasAdds).length}`,
  '',
  '### Detalle',
  '',
  ...applied.slice(0, 30).map(l => `- ${l}`),
  `... y ${applied.length - 30} merges más`,
  '',
].join('\n');
fs.appendFileSync(auditPath, report, 'utf8');
console.log('OK — audit actualizado');
