const fs = require('fs');
const {
  loadDb, saveDb, loadAliases, saveAliases,
  setupCanon, makeAspect, makeOption, mergeInto, rebuildCatalog, finalizeAliases,
} = require('./catalog_transform_base');
const { prettifyId } = require('./catalog_names');

const db = loadDb();
const aliases = loadAliases();
const byId = new Map(db.map(e => [e.id, e]));
const removeIds = new Set();
const aliasAdds = {};
const applied = [];

const SPECIALTY = new Set([
  'triceps_jm_press_barra_ez','triceps_jm_press_mancuernas',
  'triceps_tate_press_mancuernas','triceps_tate_press_polea',
  'triceps_katana_polea','triceps_katana_polea_unilateral','triceps_katana_mancuerna','triceps_katana_kettlebell','triceps_katana_banda','triceps_katana_barra_ez',
  'triceps_crossbody_polea','triceps_crossbody_polea_unilateral','triceps_crossbody_mancuerna','triceps_crossbody_kettlebell','triceps_crossbody_banda',
  'triceps_press_california_barra_recta','triceps_press_california_barra_ez','triceps_press_california_mancuernas',
  'triceps_extension_pjr_mancuerna','triceps_rolling_extension_mancuernas','triceps_rolling_extension_barra_ez',
  'triceps_extension_trx','triceps_flexiones_esfinge','triceps_extension_barra_fija','triceps_fondos_entre_bancos',
  'triceps_extension_maquina','triceps_overhead_maquina','triceps_press_maquina',
]);

const groups = {};
function addGroup(c, id, a) { if(!groups[c])groups[c]=[]; groups[c].push({id,aspects:a}); }

for(const e of db){
  if(!e.id.startsWith('triceps_'))continue;
  if(SPECIALTY.has(e.id))continue;
  let m;
  
  m=e.id.match(/^(triceps_press_frances)_(inclinado|declinado|suelo)?_?(barra_recta|barra_ez|barra_neutra|mancuernas|kettlebell|polea|banda)(_unilateral)?$/);
  if(m){ addGroup('triceps_press_frances', e.id, {bench_angle:m[2]||'flat', equipment:m[3], laterality:m[4]?'unilateral':'bilateral'}); continue; }
  
  m=e.id.match(/^(triceps_overhead)_(barra_recta|barra_ez|barra_neutra|mancuerna|kettlebell|polea|banda|disco)(_unilateral)?$/);
  if(m){ addGroup('triceps_overhead', e.id, {equipment:m[2], laterality:m[3]?'unilateral':'bilateral'}); continue; }
  
  m=e.id.match(/^(triceps_patada)_(mancuerna|polea|kettlebell|banda)(_unilateral)?$/);
  if(m){ addGroup('triceps_patada', e.id, {equipment:m[2], laterality:m[3]?'unilateral':'bilateral'}); continue; }
  
  m=e.id.match(/^(triceps_pushdown)_(polea|banda)(_unilateral)?$/);
  if(m){ addGroup('triceps_pushdown', e.id, {equipment:m[2], laterality:m[3]?'unilateral':'bilateral'}); continue; }
  
  console.log('SKIP tri:', e.id);
}

for(const [canonId,sources] of Object.entries(groups)){
  const base=byId.get(sources[0].id);
  const hasEquip=sources.some(s=>s.aspects.equipment);
  const hasAngle=sources.some(s=>s.aspects.bench_angle);
  const hasLat=sources.some(s=>s.aspects.laterality);
  const aspects=[];
  if(hasAngle){
    const vals=[...new Set(sources.map(s=>s.aspects.bench_angle).filter(Boolean))];
    aspects.push(makeAspect('bench_angle','Ángulo','',vals[0],vals.map(v=>makeOption(base.involvedMuscles,v,v.charAt(0).toUpperCase()+v.slice(1),'',[]))));
  }
  if(hasEquip){
    const vals=[...new Set(sources.map(s=>s.aspects.equipment).filter(Boolean))];
    aspects.push(makeAspect('equipment','Equipo','',vals[0],vals.map(v=>makeOption(base.involvedMuscles,v,v.replace(/_/g,' ').replace(/\b\w/g,l=>l.toUpperCase()),'',[]))));
  }
  if(hasLat){
    aspects.push(makeAspect('laterality','Lateralidad','','bilateral',[
      makeOption(base.involvedMuscles,'bilateral','Bilateral','',[]),
      makeOption(base.involvedMuscles,'unilateral','Unilateral','',[]),
    ]));
  }
  setupCanon(byId, canonId, sources[0].id, prettifyId(canonId), prettifyId(canonId)+' ajustable en chips.', prettifyId(canonId), aspects);
  for(const s of sources){
    const a={}; if(s.aspects.bench_angle)a.bench_angle=s.aspects.bench_angle; if(s.aspects.equipment)a.equipment=s.aspects.equipment; if(s.aspects.laterality)a.laterality=s.aspects.laterality;
    mergeInto(s.id,canonId,a,canonId,{byId,removeIds,aliasAdds,applied});
  }
}

// Minimal specialty chips
for(const id of SPECIALTY){
  const e=byId.get(id); if(!e)continue;
  if(id.includes('jm_press')){
    e.technicalAspects=[makeAspect('equipment','Equipo','','barra_ez',[makeOption(e.involvedMuscles,'barra_ez','Barra EZ','',[]),makeOption(e.involvedMuscles,'mancuernas','Mancuernas','',[])])];
  }else if(id.includes('tate_press')){
    e.technicalAspects=[makeAspect('equipment','Equipo','','mancuernas',[makeOption(e.involvedMuscles,'mancuernas','Mancuernas','',[]),makeOption(e.involvedMuscles,'polea','Polea','',[])])];
  }else if(id.includes('katana')){
    e.technicalAspects=[makeAspect('equipment','Equipo','','polea',[makeOption(e.involvedMuscles,'polea','Polea','',[]),makeOption(e.involvedMuscles,'mancuerna','Mancuerna','',[]),makeOption(e.involvedMuscles,'kettlebell','Kettlebell','',[]),makeOption(e.involvedMuscles,'banda','Banda','',[]),makeOption(e.involvedMuscles,'barra_ez','Barra EZ','',[])])];
  }else if(id.includes('crossbody')){
    e.technicalAspects=[makeAspect('equipment','Equipo','','polea',[makeOption(e.involvedMuscles,'polea','Polea','',[]),makeOption(e.involvedMuscles,'mancuerna','Mancuerna','',[]),makeOption(e.involvedMuscles,'kettlebell','Kettlebell','',[]),makeOption(e.involvedMuscles,'banda','Banda','',[])])];
  }else if(id.includes('press_california')){
    e.technicalAspects=[makeAspect('equipment','Equipo','','barra_recta',[makeOption(e.involvedMuscles,'barra_recta','Barra Recta','',[]),makeOption(e.involvedMuscles,'barra_ez','Barra EZ','',[]),makeOption(e.involvedMuscles,'mancuernas','Mancuernas','',[])])];
  }
}

const placements=Object.entries(groups).map(([cId,src])=>[cId,src[0].id]);
const out=rebuildCatalog(db,byId,removeIds,placements);
finalizeAliases(aliases,aliasAdds,removeIds,byId);
saveDb(out); saveAliases(aliases);
console.log('Triceps:',db.length,'→',out.length,'(-'+removeIds.size+')');
