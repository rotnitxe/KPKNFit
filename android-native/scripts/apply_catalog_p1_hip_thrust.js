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

const htBase = byId.get('glutes_hip_thrust_barra_recta');
if (htBase) {
  setupCanon(byId, 'glutes_hip_thrust', 'glutes_hip_thrust_barra_recta', 'Hip Thrust', 'Hip Thrust. Ajustable en lateralidad y equipo.', 'Hip Thrust', [
    makeAspect('laterality', 'Lateralidad', '', 'bilateral', [
      makeOption(htBase.involvedMuscles, 'bilateral', 'Bilateral', '', []),
      makeOption(htBase.involvedMuscles, 'unilateral', 'Unilateral', '', [['Glúteo Mayor', 'add', 0.1]]),
    ]),
    makeAspect('equipment', 'Equipo', '', 'barra_recta', [
      makeOption(htBase.involvedMuscles, 'barra_recta', 'Barra Recta', '', []),
      makeOption(htBase.involvedMuscles, 'mancuerna', 'Mancuerna', '', []),
      makeOption(htBase.involvedMuscles, 'kettlebell', 'Kettlebell', '', []),
      makeOption(htBase.involvedMuscles, 'disco', 'Disco', '', []),
      makeOption(htBase.involvedMuscles, 'polea', 'Polea', '', [['Glúteo Mayor', 'add', 0.05]]),
      makeOption(htBase.involvedMuscles, 'banda', 'Banda', '', []),
      makeOption(htBase.involvedMuscles, 'maquina_smith', 'Máquina Smith', '', []),
      makeOption(htBase.involvedMuscles, 'maquina', 'Máquina', '', []),
      makeOption(htBase.involvedMuscles, 'peso_corporal', 'Peso Corporal', '', [['Glúteo Mayor', 'add', -0.1]]),
    ]),
  ]);
  [
    ['glutes_hip_thrust_barra_recta',{laterality:'bilateral',equipment:'barra_recta'}],
    ['glutes_hip_thrust_mancuerna',{laterality:'bilateral',equipment:'mancuerna'}],
    ['glutes_hip_thrust_kettlebell',{laterality:'bilateral',equipment:'kettlebell'}],
    ['glutes_hip_thrust_disco',{laterality:'bilateral',equipment:'disco'}],
    ['glutes_hip_thrust_polea',{laterality:'bilateral',equipment:'polea'}],
    ['glutes_hip_thrust_banda',{laterality:'bilateral',equipment:'banda'}],
    ['glutes_hip_thrust_maquina_smith',{laterality:'bilateral',equipment:'maquina_smith'}],
    ['glutes_hip_thrust_maquina',{laterality:'bilateral',equipment:'maquina'}],
    ['glutes_hip_thrust_unilateral_barra_recta',{laterality:'unilateral',equipment:'barra_recta'}],
    ['glutes_hip_thrust_unilateral_mancuerna',{laterality:'unilateral',equipment:'mancuerna'}],
    ['glutes_hip_thrust_unilateral_kettlebell',{laterality:'unilateral',equipment:'kettlebell'}],
    ['glutes_hip_thrust_unilateral_polea',{laterality:'unilateral',equipment:'polea'}],
    ['glutes_hip_thrust_unilateral_maquina_smith',{laterality:'unilateral',equipment:'maquina_smith'}],
    ['glutes_hip_thrust_unilateral_peso_corporal',{laterality:'unilateral',equipment:'peso_corporal'}],
  ].forEach(([id,aspects])=>mergeInto(id,'glutes_hip_thrust',aspects,'HipThrust',{byId,removeIds,aliasAdds,applied}));
}

const puenteBase = byId.get('glutes_puente_gluteos_peso_corporal');
if (puenteBase) {
  setupCanon(byId, 'glutes_puente_gluteos', 'glutes_puente_gluteos_peso_corporal', 'Puente de Glúteos', 'Puente de glúteos. Ajustable en lateralidad y equipo.', 'Puente de Glúteos',[
    makeAspect('laterality','Lateralidad','','bilateral',[
      makeOption(puenteBase.involvedMuscles,'bilateral','Bilateral','',[]),
      makeOption(puenteBase.involvedMuscles,'unilateral','Unilateral','',[['Core','add',0.1]]),
    ]),
    makeAspect('equipment','Equipo','','peso_corporal',[
      makeOption(puenteBase.involvedMuscles,'barra_recta','Barra Recta','',[]),
      makeOption(puenteBase.involvedMuscles,'mancuerna','Mancuerna','',[]),
      makeOption(puenteBase.involvedMuscles,'disco','Disco','',[]),
      makeOption(puenteBase.involvedMuscles,'peso_corporal','Peso Corporal','',[['Glúteo Mayor','add',-0.05]]),
      makeOption(puenteBase.involvedMuscles,'maquina_smith','Máquina Smith','',[]),
    ]),
  ]);
  [
    ['glutes_puente_gluteos_barra_recta',{laterality:'bilateral',equipment:'barra_recta'}],
    ['glutes_puente_gluteos_mancuerna',{laterality:'bilateral',equipment:'mancuerna'}],
    ['glutes_puente_gluteos_disco',{laterality:'bilateral',equipment:'disco'}],
    ['glutes_puente_gluteos_peso_corporal',{laterality:'bilateral',equipment:'peso_corporal'}],
    ['glutes_puente_gluteos_unilateral_peso_corporal',{laterality:'unilateral',equipment:'peso_corporal'}],
    ['glutes_puente_gluteos_maquina_smith',{laterality:'bilateral',equipment:'maquina_smith'}],
    ['glutes_puente_gluteos_unilateral_maquina_smith',{laterality:'unilateral',equipment:'maquina_smith'}],
  ].forEach(([id,aspects])=>mergeInto(id,'glutes_puente_gluteos',aspects,'Puente',{byId,removeIds,aliasAdds,applied}));
}

const frogBase = byId.get('glutes_frog_pumps_peso_corporal');
if (frogBase) {
  setupCanon(byId, 'glutes_frog_pumps', 'glutes_frog_pumps_peso_corporal', 'Frog Pumps', 'Frog Pumps. Ajustable en carga.', 'Frog Pumps',[
    makeAspect('equipment','Equipo','','peso_corporal',[
      makeOption(frogBase.involvedMuscles,'peso_corporal','Peso Corporal','',[]),
      makeOption(frogBase.involvedMuscles,'disco','Disco','',[]),
    ]),
  ]);
  mergeInto('glutes_frog_pumps_peso_corporal','glutes_frog_pumps',{equipment:'peso_corporal'},'Frog',{byId,removeIds,aliasAdds,applied});
  mergeInto('glutes_frog_pumps_disco','glutes_frog_pumps',{equipment:'disco'},'Frog',{byId,removeIds,aliasAdds,applied});
}

const out = rebuildCatalog(db,byId,removeIds,[
  ['glutes_hip_thrust','glutes_hip_thrust_barra_recta'],
  ['glutes_puente_gluteos','glutes_puente_gluteos_peso_corporal'],
  ['glutes_frog_pumps','glutes_frog_pumps_peso_corporal'],
]);
finalizeAliases(aliases,aliasAdds,removeIds,byId);
saveDb(out); saveAliases(aliases);
console.log('HipThrust+Puente+Frog:',db.length,'→',out.length,'(-'+removeIds.size+')');
