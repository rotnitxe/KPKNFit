const fs = require('fs');
const path = require('path');
const {
  loadDb, saveDb, loadAliases, saveAliases,
  setupCanon, makeAspect, makeOption, mergeInto, rebuildCatalog, finalizeAliases,
} = require('./catalog_transform_base');

const db = loadDb();
const aliases = loadAliases();
const byId = new Map(db.map(e => [e.id, e]));
const removeIds = new Set();
const aliasAdds = {};
const applied = [];

// Parse all calves exercises
const groups = {};
for (const e of db) {
  if (!e.id.startsWith('calves_elevacion')) continue;
  
  let m = e.id.match(/^calves_elevacion_talones_(de_pie|sentado|donkey)_(unilateral)?_?(.+)$/);
  if (m) {
    const setup = 'talones_' + m[1];
    const unilateral = !!m[2];
    const equip = m[3];
    if (!groups[setup]) groups[setup] = [];
    groups[setup].push({ id: e.id, unilateral, equip });
    continue;
  }
  
  m = e.id.match(/^calves_elevacion_talones_(unilateral)?_?prensa_(.+)$/);
  if (m) {
    const setup = 'talones_prensa';
    const unilateral = !!m[1];
    const angle = m[2];
    if (!groups[setup]) groups[setup] = [];
    groups[setup].push({ id: e.id, unilateral, angle });
    continue;
  }
  
  m = e.id.match(/^calves_elevacion_tibial_anterior_(de_pie|sentado)?_?(unilateral)?_?(.+)$/);
  if (m) {
    const setup = 'tibial_anterior';
    const unilateral = !!m[2];
    const equip = m[3];
    if (!groups[setup]) groups[setup] = [];
    groups[setup].push({ id: e.id, unilateral, equip });
    continue;
  }
  console.log('SKIP', e.id);
}

for (const [setup, sources] of Object.entries(groups)) {
  const first = sources[0];
  const canonicalId = 'calves_' + setup;
  const base = byId.get(first.id);
  const hasUni = sources.some(s => s.unilateral);
  const isPrensa = setup === 'talones_prensa';
  
  const aspects = [];
  
  if (isPrensa) {
    const angles = [...new Set(sources.map(s => s.angle))];
    aspects.push(makeAspect('press_angle', 'Ángulo de Prensa', '', '45', angles.map(a => makeOption(base.involvedMuscles, a, a.charAt(0).toUpperCase() + a.slice(1), '', []))));
  } else {
    const equips = [...new Set(sources.map(s => s.equip))];
    aspects.push(makeAspect('equipment', 'Equipo', '', equips[0], equips.map(eq => makeOption(base.involvedMuscles, eq, eq.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()), '', []))));
  }
  
  if (hasUni) {
    aspects.push(makeAspect('laterality', 'Lateralidad', '', 'bilateral', [
      makeOption(base.involvedMuscles, 'bilateral', 'Bilateral', '', []),
      makeOption(base.involvedMuscles, 'unilateral', 'Unilateral', '', []),
    ]));
  }
  
  setupCanon(byId, canonicalId, sources[0].id, canonicalId.replace(/_/g, ' '), setup + ' ajustable en chips.', canonicalId.replace(/_/g, ' '), aspects);
  
  for (const s of sources) {
    const aspects = {};
    if (isPrensa) aspects.press_angle = s.angle; else aspects.equipment = s.equip;
    if (hasUni) aspects.laterality = s.unilateral ? 'unilateral' : 'bilateral';
    mergeInto(s.id, canonicalId, aspects, setup, { byId, removeIds, aliasAdds, applied });
  }
}

const placements = Object.entries(groups).map(([setup, src]) => ['calves_' + setup, src[0].id]);
const out = rebuildCatalog(db, byId, removeIds, placements);
finalizeAliases(aliases, aliasAdds, removeIds, byId);
saveDb(out); saveAliases(aliases);
console.log('Talones:', db.length, '→', out.length, '(-' + removeIds.size + ')');
