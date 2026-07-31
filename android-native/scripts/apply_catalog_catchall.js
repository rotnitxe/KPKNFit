const fs = require('fs');
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

const IMPLEMENTS = ['barra_recta','barra_ez','barra_neutra','barra_hexagonal','barra_ssb','barra_t','barra_fija',
  'mancuernas','mancuerna','kettlebell','kettlebells','polea','banda','disco',
  'maquina','maquina_smith','maquina_hack','maquina_v_squat','maquina_belt_squat','maquina_pec_deck',
  'peso_corporal','lastradas','asistidas','pared','trx','anillas','cuerda'];

function getStem(id) {
  let raw = id;
  if (raw.endsWith('_unilateral')) raw = raw.slice(0, -11);
  const parts = raw.split('_');
  const compound = parts.slice(-2).join('_');
  let imp = null;
  if (IMPLEMENTS.includes(compound)) imp = compound;
  else if (IMPLEMENTS.includes(parts[parts.length-1])) imp = parts[parts.length-1];
  if (!imp) return null;
  let stem = raw.slice(0, raw.length - imp.length - 1);
  if (stem.endsWith('_unilateral')) stem = stem.slice(0, -11);
  return { stem, imp, unilateral: id.endsWith('_unilateral') };
}

const stems = {};
for (const e of db) {
  if (e.technicalAspects) continue;
  const s = getStem(e.id);
  if (!s) continue;
  if (!stems[s.stem]) stems[s.stem] = [];
  stems[s.stem].push({ id: e.id, imp: s.imp, unilateral: s.unilateral });
}

const placements = [];

for (const [stem, list] of Object.entries(stems)) {
  if (list.length < 2) continue;
  
  // Some stems should stay separate (biomechanically distinct patterns)
  const keepStems = ['quads_sentadilla_sissy','quads_sentadilla_pendulo','quads_sentadilla_belt_squat'];
  if (keepStems.includes(stem)) {
    // add minimal chips instead of merging
    for (const item of list) {
      const e = byId.get(item.id);
      if (e) e.technicalAspects = [makeAspect('equipment','Equipo','',item.imp,[makeOption(e.involvedMuscles,item.imp,item.imp,'',[])])];
    }
    continue;
  }
  
  const canonId = stem;
  const base = byId.get(list[0].id);
  const hasUni = list.some(x => x.unilateral || x.id.includes('unilateral'));
  const equips = [...new Set(list.map(x => x.imp))];
  
  const aspects = [];
  aspects.push(makeAspect('equipment','Equipo','',equips[0], equips.map(v => makeOption(base.involvedMuscles, v, v.replace(/_/g,' ').replace(/\b\w/g,l=>l.toUpperCase()),'',[]))));
  
  if (hasUni) {
    aspects.push(makeAspect('laterality','Lateralidad','','bilateral',[
      makeOption(base.involvedMuscles,'bilateral','Bilateral','',[]),
      makeOption(base.involvedMuscles,'unilateral','Unilateral','',[]),
    ]));
  }
  
  setupCanon(byId, canonId, list[0].id, canonId.replace(/_/g,' '), canonId.replace(/_/g,' ')+' ajustable en chips.', canonId.replace(/_/g,' '), aspects);
  
  for (const item of list) {
    const a = { equipment: item.imp };
    if (hasUni) {
      const isUni = item.unilateral || item.id.includes('unilateral');
      a.laterality = isUni ? 'unilateral' : 'bilateral';
    }
    mergeInto(item.id, canonId, a, stem, { byId, removeIds, aliasAdds, applied });
  }
  placements.push([canonId, list[0].id]);
}

// For remaining singletons without TA, add minimal equipment chip if they have a recognizable implement
for (const e of db) {
  if (e.technicalAspects) continue;
  if (removeIds.has(e.id)) continue;
  const s = getStem(e.id);
  if (!s) continue;
  // only if stem had 1 member (we skipped it above)
  if (stems[s.stem] && stems[s.stem].length >= 2) continue; // already handled
  const aspects = [makeAspect('equipment','Equipo','',s.imp,[makeOption(e.involvedMuscles,s.imp,s.imp.replace(/_/g,' ').replace(/\b\w/g,l=>l.toUpperCase()),'',[])])];
  if (e.id.endsWith('_unilateral') || e.id.includes('_unilateral_')) {
    aspects.push(makeAspect('laterality','Lateralidad','','bilateral',[
      makeOption(e.involvedMuscles,'bilateral','Bilateral','',[]),
      makeOption(e.involvedMuscles,'unilateral','Unilateral','',[]),
    ]));
  }
  e.technicalAspects = aspects;
}

const out = rebuildCatalog(db, byId, removeIds, placements);
finalizeAliases(aliases, aliasAdds, removeIds, byId);
saveDb(out); saveAliases(aliases);

const withTA = out.filter(e => e.technicalAspects && e.technicalAspects.length > 0);
console.log('Catch-all:', db.length, '→', out.length, '(-' + removeIds.size + ')');
console.log('Con TA:', withTA.length, 'de', out.length);
