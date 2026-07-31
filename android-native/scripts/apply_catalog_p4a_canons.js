const fs=require('fs');
const{loadDb,saveDb,loadAliases,saveAliases,setupCanon,makeAspect,makeOption,mergeInto,rebuildCatalog,finalizeAliases}=require('./catalog_transform_base');
const db=loadDb();const aliases=loadAliases();
const byId=new Map(db.map(e=>[e.id,e]));
const removeIds=new Set();const aliasAdds={};const applied=[];
function c(id,baseId,name,alias,aspects,sources){
  if(!byId.has(baseId))return;
  const real=sources.filter(s=>byId.has(s.id));
  if(real.length<2)return;
  setupCanon(byId,id,baseId,name,name+'. Ajustable en chips.',alias,aspects);
  for(const s of real)mergeInto(s.id,id,s.aspects||{},alias,{byId,removeIds,aliasAdds,applied});
}
function A(id,name,def,opts,mus){return makeAspect(id,name,'',def,opts.map(o=>makeOption(mus,o.id,o.name,'',[])));}
const mus=id=>byId.get(id).involvedMuscles;

// 1. FONDOS
c('tren_superior_fondos','tren_superior_fondos_paralelas','Fondos','Fondos',[
  A('equipment','Equipo','paralelas',[{id:'paralelas',name:'Paralelas'},{id:'lastrado',name:'Lastrado'},{id:'anillas',name:'Anillas'}],mus('tren_superior_fondos_paralelas')),
],[
  {id:'tren_superior_fondos_paralelas',aspects:{equipment:'paralelas'}},
  {id:'tren_superior_fondos_lastrados',aspects:{equipment:'lastrado'}},
  {id:'tren_superior_fondos_anillas',aspects:{equipment:'anillas'}},
]);

// 2. CRUCE POLEAS
c('tren_superior_cruce_poleas','tren_superior_cruce_poleas_altas','Cruce de Poleas','CrucePoleas',[
  A('cable_height','Altura Polea','altas',[{id:'altas',name:'Altas'},{id:'bajas',name:'Bajas'}],mus('tren_superior_cruce_poleas_altas')),
],[
  {id:'tren_superior_cruce_poleas_altas',aspects:{cable_height:'altas'}},
  {id:'tren_superior_cruce_poleas_bajas',aspects:{cable_height:'bajas'}},
]);

// 3. HIPEREXTENSIONES BACK
c('back_hiperextensiones','back_hiperextensiones_45','Hiperextensiones','Hiperextensiones',[
  A('equipment','Equipo','45',[{id:'45',name:'45°'},{id:'horizontal',name:'Horizontal'},{id:'45_barra',name:'45° Barra'},{id:'ghd',name:'GHD'}],mus('back_hiperextensiones_45')),
],[
  {id:'back_hiperextensiones_45',aspects:{equipment:'45'}},
  {id:'back_hiperextensiones_horizontales',aspects:{equipment:'horizontal'}},
  {id:'back_hiperextensiones_45_barra',aspects:{equipment:'45_barra'}},
  {id:'back_hiperextensiones_ghd',aspects:{equipment:'ghd'}},
]);

// 4. REVERSE HYPER — merge into existing canon
[['glutes_reverse_hyper_banco',{equipment:'banco'}],['glutes_reverse_hyper_unilateral_banco',{equipment:'banco',laterality:'unilateral'}]].forEach(([sid,asp])=>{
  if(byId.has(sid))mergeInto(sid,'glutes_reverse_hyper',asp,'ReverseHyper',{byId,removeIds,aliasAdds,applied});
});

// 5. SISSY SQUAT
c('quads_sentadilla_sissy','quads_sentadilla_sissy_libre','Sentadilla Sissy','SissySquat',[
  A('equipment','Equipo','libre',[{id:'libre',name:'Libre'},{id:'maquina_pendulo',name:'Máquina Péndulo'},{id:'prensa',name:'Prensa'},{id:'banco',name:'Banco'}],mus('quads_sentadilla_sissy_libre')),
],[
  {id:'quads_sentadilla_sissy_libre',aspects:{equipment:'libre'}},
  {id:'quads_sentadilla_sissy_maquina_pendulo',aspects:{equipment:'maquina_pendulo'}},
  {id:'quads_sentadilla_sissy_prensa',aspects:{equipment:'prensa'}},
  {id:'quads_sentadilla_sissy_banco',aspects:{equipment:'banco'}},
]);

// 6. BULGARIA MÁQUINAS
c('quads_sentadilla_bulgara_maquina','quads_sentadilla_bulgara_maquina_belt_squat','Bulgaria en Máquina','BulgariaMaquina',[
  A('equipment','Equipo','belt_squat',[{id:'belt_squat',name:'Belt Squat'},{id:'v_squat',name:'V-Squat'}],mus('quads_sentadilla_bulgara_maquina_belt_squat')),
],[
  {id:'quads_sentadilla_bulgara_maquina_belt_squat',aspects:{equipment:'belt_squat'}},
  {id:'quads_sentadilla_bulgara_maquina_v_squat',aspects:{equipment:'v_squat'}},
]);

// 7. GLUTE-HAM RAISE
c('hams_glute_ham_raise','hams_glute_ham_raise_ghd','Glute-Ham Raise','GHR',[
  A('laterality','Lateralidad','bilateral',[{id:'bilateral',name:'Bilateral'},{id:'unilateral',name:'Unilateral'}],mus('hams_glute_ham_raise_ghd')),
],[
  {id:'hams_glute_ham_raise_ghd',aspects:{laterality:'bilateral'}},
  {id:'hams_glute_ham_raise_unilateral_ghd',aspects:{laterality:'unilateral'}},
]);

const placements=[];const out=rebuildCatalog(db,byId,removeIds,placements);
finalizeAliases(aliases,aliasAdds,removeIds,byId);
saveDb(out);saveAliases(aliases);
console.log('P4a Canons:',db.length,'→',out.length,'(-'+removeIds.size+')');