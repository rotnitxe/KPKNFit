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

const SPECIALTY = new Set([
  'deltoides_press_arnold_mancuernas','deltoides_press_arnold_kettlebell','deltoides_press_arnold_polea',
  'deltoides_press_z_barra_recta','deltoides_press_z_barra_ez','deltoides_press_z_mancuernas','deltoides_press_z_kettlebell',
  'deltoides_press_landmine_unilateral',
  'deltoides_elevaciones_laterales_super_rom_mancuernas','deltoides_elevaciones_laterales_super_rom_polea','deltoides_elevaciones_laterales_super_rom_polea_unilateral',
  'deltoides_aperturas_inversas_maquina_pec_deck',
]);

const groups = {};
for (const e of db) {
  if (!e.id.startsWith('deltoides_')) continue;
  if (SPECIALTY.has(e.id)) continue;
  
  let m = e.id.match(/^(deltoides_press_militar_de_pie)_(barra_recta|barra_ez|barra_neutra)$/);
  if (m) { addGroup('deltoides_press_militar_de_pie', e.id, { implement: m[2] }); continue; }
  
  m = e.id.match(/^(deltoides_press_hombros_de_pie)_(mancuernas|kettlebell|polea|banda)(_unilateral)?$/);
  if (m) { addGroup('deltoides_press_hombros_de_pie', e.id, { equipment: m[2], laterality: m[3] ? 'unilateral' : 'bilateral' }); continue; }
  
  m = e.id.match(/^(deltoides_press_hombros_sentado)_(barra_recta|barra_ez|barra_neutra|mancuernas|kettlebell|polea|maquina|maquina_smith)$/);
  if (m) { addGroup('deltoides_press_hombros_sentado', e.id, { equipment: m[2] }); continue; }
  
  m = e.id.match(/^(deltoides_elevaciones_frontales)_(barra_recta|barra_ez|mancuernas|kettlebell|disco|polea|banda)(_unilateral)?$/);
  if (m) { addGroup('deltoides_elevaciones_frontales', e.id, { equipment: m[2], laterality: m[3] ? 'unilateral' : 'bilateral' }); continue; }
  
  m = e.id.match(/^(deltoides_elevaciones_laterales_de_pie)_(mancuernas|kettlebell|polea|banda|maquina)(_unilateral)?$/);
  if (m) { addGroup('deltoides_elevaciones_laterales_de_pie', e.id, { equipment: m[2], laterality: m[3] ? 'unilateral' : 'bilateral' }); continue; }
  
  m = e.id.match(/^(deltoides_elevaciones_laterales_sentado)_(maquina|mancuernas|kettlebell|polea)(_unilateral)?$/);
  if (m) { addGroup('deltoides_elevaciones_laterales_sentado', e.id, { equipment: m[2], laterality: m[3] ? 'unilateral' : 'bilateral' }); continue; }
  
  m = e.id.match(/^(deltoides_elevaciones_laterales_acostado_banco_plano)_(mancuernas|polea_cruzada|polea_unilateral)$/);
  if (m) { addGroup('deltoides_elevaciones_laterales_acostado_banco_plano', e.id, { equipment: m[2] }); continue; }
  
  m = e.id.match(/^(deltoides_elevaciones_laterales_inclinadas)_mancuerna$/);
  if (m) { addGroup('deltoides_elevaciones_laterales_inclinadas', e.id, { laterality: 'unilateral' }); continue; }
  
  m = e.id.match(/^(deltoides_elevaciones_laterales_recostado)_mancuerna$/);
  if (m) { addGroup('deltoides_elevaciones_laterales_recostado', e.id, { laterality: 'unilateral' }); continue; }
  
  m = e.id.match(/^(deltoides_elevaciones_posteriores_de_pie)_(mancuernas|kettlebell|polea_cruzada|polea_unilateral|banda)$/);
  if (m) { addGroup('deltoides_elevaciones_posteriores_de_pie', e.id, { equipment: m[2] }); continue; }
  
  m = e.id.match(/^(deltoides_elevaciones_posteriores_sentado_banco_plano)_(mancuernas|kettlebell|polea)(_unilateral)?$/);
  if (m) { addGroup('deltoides_elevaciones_posteriores_sentado_banco_plano', e.id, { equipment: m[2], laterality: m[3] ? 'unilateral' : 'bilateral' }); continue; }
  
  m = e.id.match(/^(deltoides_elevaciones_posteriores_pecho_apoyado)_(mancuernas|kettlebell|polea)(_unilateral)?$/);
  if (m) { addGroup('deltoides_elevaciones_posteriores_pecho_apoyado', e.id, { equipment: m[2], laterality: m[3] ? 'unilateral' : 'bilateral' }); continue; }
  
  m = e.id.match(/^(deltoides_elevaciones_posteriores_acostado_banco_plano)_(mancuernas|polea_cruzada|polea_unilateral)$/);
  if (m) { addGroup('deltoides_elevaciones_posteriores_acostado_banco_plano', e.id, { equipment: m[2] }); continue; }
  
  m = e.id.match(/^(deltoides_elevaciones_posteriores_arana)_(mancuernas|kettlebell|polea)$/);
  if (m) { addGroup('deltoides_elevaciones_posteriores_arana', e.id, { equipment: m[2] }); continue; }
  
  console.log('SKIP delt:', e.id);
}

function addGroup(canonical, id, aspects) {
  if (!groups[canonical]) groups[canonical] = [];
  groups[canonical].push({ id, aspects });
}

for (const [canonId, sources] of Object.entries(groups)) {
  const base = byId.get(sources[0].id);
  const hasEquipment = sources.some(s => s.aspects.equipment);
  const hasLaterality = sources.some(s => s.aspects.laterality);
  
  const aspects = [];
  if (hasEquipment) {
    const equips = [...new Set(sources.map(s => s.aspects.equipment).filter(Boolean))];
    aspects.push(makeAspect('equipment','Equipo','',equips[0],equips.map(eq=>makeOption(base.involvedMuscles,eq,eq.replace(/_/g,' ').replace(/\b\w/g,l=>l.toUpperCase()),'',[]))));
  }
  if (hasLaterality) {
    aspects.push(makeAspect('laterality','Lateralidad','','bilateral',[
      makeOption(base.involvedMuscles,'bilateral','Bilateral','',[]),
      makeOption(base.involvedMuscles,'unilateral','Unilateral','',[]),
    ]));
  }
  
  setupCanon(byId, canonId, sources[0].id, canonId.replace(/_/g,' '), canonId.replace(/_/g,' ')+' ajustable en chips.', canonId.replace(/_/g,' '), aspects);
  
  for (const s of sources) {
    const a = {};
    if (s.aspects.equipment) a.equipment = s.aspects.equipment;
    if (s.aspects.laterality) a.laterality = s.aspects.laterality;
    mergeInto(s.id, canonId, a, canonId, { byId, removeIds, aliasAdds, applied });
  }
}

// Specialty chips
for (const id of SPECIALTY) {
  const e = byId.get(id);
  if (!e) continue;
  if (id.includes('press_arnold')) {
    e.technicalAspects = [makeAspect('equipment','Equipo','','mancuernas',[
      makeOption(e.involvedMuscles,'mancuernas','Mancuernas','',[]),
      makeOption(e.involvedMuscles,'kettlebell','Kettlebell','',[]),
      makeOption(e.involvedMuscles,'polea','Polea','',[]),
    ])];
  } else if (id.includes('press_z')) {
    e.technicalAspects = [makeAspect('equipment','Equipo','','barra_recta',[
      makeOption(e.involvedMuscles,'barra_recta','Barra Recta','',[]),
      makeOption(e.involvedMuscles,'barra_ez','Barra EZ','',[]),
      makeOption(e.involvedMuscles,'mancuernas','Mancuernas','',[]),
      makeOption(e.involvedMuscles,'kettlebell','Kettlebell','',[]),
    ])];
  } else if (id.includes('super_rom')) {
    e.technicalAspects = [makeAspect('equipment','Equipo','','mancuernas',[
      makeOption(e.involvedMuscles,'mancuernas','Mancuernas','',[]),
      makeOption(e.involvedMuscles,'polea','Polea','',[]),
    ]), makeAspect('laterality','Lateralidad','','bilateral',[
      makeOption(e.involvedMuscles,'bilateral','Bilateral','',[]),
      makeOption(e.involvedMuscles,'unilateral','Unilateral','',[]),
    ])];
  }
}

const placements = Object.entries(groups).map(([cId, src]) => [cId, src[0].id]);
const out = rebuildCatalog(db, byId, removeIds, placements);
finalizeAliases(aliases, aliasAdds, removeIds, byId);
saveDb(out); saveAliases(aliases);
console.log('Deltoides:', db.length, '→', out.length, '(-' + removeIds.size + ')');
