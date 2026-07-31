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

// Face Pull
const fpBase = byId.get('deltoides_face_pull_polea');
if (fpBase) {
  setupCanon(byId, 'deltoides_face_pull', 'deltoides_face_pull_polea', 'Face Pull', 'Face Pull. Ajustable en equipo y lateralidad.', 'Face Pull',[
    makeAspect('equipment','Equipo','','polea',[
      makeOption(fpBase.involvedMuscles,'polea','Polea','',[]),
      makeOption(fpBase.involvedMuscles,'banda','Banda','',[]),
    ]),
    makeAspect('laterality','Lateralidad','','bilateral',[
      makeOption(fpBase.involvedMuscles,'bilateral','Bilateral','',[]),
      makeOption(fpBase.involvedMuscles,'unilateral','Unilateral','',[['Deltoides (posteriores)','add',0.1]]),
    ]),
  ]);
  mergeInto('deltoides_face_pull_polea','deltoides_face_pull',{equipment:'polea',laterality:'bilateral'},'FacePull',{byId,removeIds,aliasAdds,applied});
  mergeInto('deltoides_face_pull_polea_unilateral','deltoides_face_pull',{equipment:'polea',laterality:'unilateral'},'FacePull',{byId,removeIds,aliasAdds,applied});
  mergeInto('deltoides_face_pull_banda','deltoides_face_pull',{equipment:'banda',laterality:'bilateral'},'FacePull',{byId,removeIds,aliasAdds,applied});
}

// Encogimientos normales
const encBase = byId.get('back_encogimientos_barra_recta');
if (encBase) {
  setupCanon(byId, 'back_encogimientos', 'back_encogimientos_barra_recta', 'Encogimientos', 'Encogimientos de trapecio. Ajustable en equipo y posición de barra.', 'Encogimientos',[
    makeAspect('equipment','Equipo','','barra_recta',[
      makeOption(encBase.involvedMuscles,'barra_recta','Barra Recta','',[]),
      makeOption(encBase.involvedMuscles,'barra_hexagonal','Barra Hexagonal','',[]),
      makeOption(encBase.involvedMuscles,'mancuernas','Mancuernas','',[]),
      makeOption(encBase.involvedMuscles,'kettlebell','Kettlebell','',[]),
      makeOption(encBase.involvedMuscles,'polea','Polea','',[]),
      makeOption(encBase.involvedMuscles,'maquina_smith','Máquina Smith','',[]),
      makeOption(encBase.involvedMuscles,'maquina','Máquina','',[]),
      makeOption(encBase.involvedMuscles,'maquina_barra_t','Máquina Barra T','',[]),
    ]),
    makeAspect('bar_position','Posición de Barra','','front',[
      makeOption(encBase.involvedMuscles,'front','Frontal','',[]),
      makeOption(encBase.involvedMuscles,'behind_neck','Detrás de Nuca','',[['Trapecio','add',0.1]]),
    ]),
  ]);
  [
    ['back_encogimientos_barra_recta',{equipment:'barra_recta',bar_position:'front'}],
    ['back_encogimientos_barra_hexagonal',{equipment:'barra_hexagonal',bar_position:'front'}],
    ['back_encogimientos_tras_nuca_barra',{equipment:'barra_recta',bar_position:'behind_neck'}],
    ['back_encogimientos_mancuernas',{equipment:'mancuernas',bar_position:'front'}],
    ['back_encogimientos_kettlebell',{equipment:'kettlebell',bar_position:'front'}],
    ['back_encogimientos_polea',{equipment:'polea',bar_position:'front'}],
    ['back_encogimientos_maquina_smith',{equipment:'maquina_smith',bar_position:'front'}],
    ['back_encogimientos_maquina',{equipment:'maquina',bar_position:'front'}],
    ['back_encogimientos_maquina_barra_t',{equipment:'maquina_barra_t',bar_position:'front'}],
  ].forEach(([id,aspects])=>mergeInto(id,'back_encogimientos',aspects,'Encogimientos',{byId,removeIds,aliasAdds,applied}));
}

// Kelso
const kelBase = byId.get('back_encogimientos_kelso_banco_plano_mancuernas');
if (kelBase) {
  setupCanon(byId, 'back_encogimientos_kelso', 'back_encogimientos_kelso_banco_plano_mancuernas', 'Encogimientos Kelso', 'Kelso shrugs. Ajustable en ángulo y equipo.', 'Encogimientos Kelso',[
    makeAspect('bench_angle','Ángulo de Banco','','flat',[
      makeOption(kelBase.involvedMuscles,'flat','Plano','',[]),
      makeOption(kelBase.involvedMuscles,'incline','Inclinado','',[['Trapecio','add',0.1]]),
    ]),
    makeAspect('equipment','Equipo','','mancuernas',[
      makeOption(kelBase.involvedMuscles,'mancuernas','Mancuernas','',[]),
      makeOption(kelBase.involvedMuscles,'barra_ez','Barra EZ','',[]),
      makeOption(kelBase.involvedMuscles,'kettlebell','Kettlebell','',[]),
      makeOption(kelBase.involvedMuscles,'polea','Polea','',[]),
      makeOption(kelBase.involvedMuscles,'banda','Banda','',[]),
    ]),
  ]);
  [
    ['back_encogimientos_kelso_banco_plano_mancuernas',{bench_angle:'flat',equipment:'mancuernas'}],
    ['back_encogimientos_kelso_banco_inclinado_mancuernas',{bench_angle:'incline',equipment:'mancuernas'}],
    ['back_encogimientos_kelso_banco_plano_barra_ez',{bench_angle:'flat',equipment:'barra_ez'}],
    ['back_encogimientos_kelso_banco_inclinado_barra_ez',{bench_angle:'incline',equipment:'barra_ez'}],
    ['back_encogimientos_kelso_banco_plano_kettlebell',{bench_angle:'flat',equipment:'kettlebell'}],
    ['back_encogimientos_kelso_banco_inclinado_kettlebell',{bench_angle:'incline',equipment:'kettlebell'}],
    ['back_encogimientos_kelso_polea',{bench_angle:'flat',equipment:'polea'}],
    ['back_encogimientos_kelso_banda',{bench_angle:'flat',equipment:'banda'}],
  ].forEach(([id,aspects])=>mergeInto(id,'back_encogimientos_kelso',aspects,'Kelso',{byId,removeIds,aliasAdds,applied}));
}

const out = rebuildCatalog(db,byId,removeIds,[
  ['deltoides_face_pull','deltoides_face_pull_polea'],
  ['back_encogimientos','back_encogimientos_barra_recta'],
  ['back_encogimientos_kelso','back_encogimientos_kelso_banco_plano_mancuernas'],
]);
finalizeAliases(aliases,aliasAdds,removeIds,byId);
saveDb(out); saveAliases(aliases);
console.log('FacePull+Encogimientos:',db.length,'→',out.length,'(-'+removeIds.size+')');
