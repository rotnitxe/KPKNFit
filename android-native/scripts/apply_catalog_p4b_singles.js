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

// 8. CURL FEMORAL
c('hams_curl_femoral','hams_curl_femoral_sliders','Curl Femoral','CurlFemoral',[
  A('equipment','Equipo','sliders',[{id:'sliders',name:'Sliders'},{id:'balon_suizo',name:'Balón Suizo'}],mus('hams_curl_femoral_sliders')),
],[
  {id:'hams_curl_femoral_sliders',aspects:{equipment:'sliders'}},
  {id:'hams_curl_femoral_balon_suizo',aspects:{equipment:'balon_suizo'}},
]);

// 9. RUEDA ABDOMINAL
c('core_rueda_abdominal','core_rueda_abdominal_rodillas','Rueda Abdominal','RuedaAbdominal',[
  A('position','Posición','rodillas',[{id:'rodillas',name:'Rodillas'},{id:'de_pie',name:'De Pie'}],mus('core_rueda_abdominal_rodillas')),
],[
  {id:'core_rueda_abdominal_rodillas',aspects:{position:'rodillas'}},
  {id:'core_rueda_abdominal_de_pie',aspects:{position:'de_pie'}},
]);

// 10. ELEVACIÓN PIERNAS
c('core_elevacion_piernas','core_elevacion_piernas_colgado_barra','Elevación Piernas','ElevacionPiernas',[
  A('position','Posición','piernas_colgado',[{id:'piernas_colgado',name:'Piernas Colgado'},{id:'rodillas_colgado',name:'Rodillas Colgado'},{id:'paralelas',name:'Paralelas'},{id:'banco_plano',name:'Banco Plano'}],mus('core_elevacion_piernas_colgado_barra')),
],[
  {id:'core_elevacion_piernas_colgado_barra',aspects:{position:'piernas_colgado'}},
  {id:'core_elevacion_rodillas_colgado_barra',aspects:{position:'rodillas_colgado'}},
  {id:'core_elevacion_piernas_paralelas',aspects:{position:'paralelas'}},
  {id:'core_elevacion_piernas_banco_plano',aspects:{position:'banco_plano'}},
]);

// 11. LENADOR
c('core_lenador_polea','core_lenador_polea_alta_baja','Lenador en Polea','Lenador',[
  A('direction','Dirección','alta_a_baja',[{id:'alta_a_baja',name:'Alta → Baja'},{id:'baja_a_alta',name:'Baja → Alta'}],mus('core_lenador_polea_alta_baja')),
],[
  {id:'core_lenador_polea_alta_baja',aspects:{direction:'alta_a_baja'}},
  {id:'core_lenador_polea_baja_alta',aspects:{direction:'baja_a_alta'}},
]);

// 12. PLANCHA
c('core_plancha','core_plancha_frontal_iso','Plancha','Plancha',[
  A('plank_type','Tipo','frontal',[{id:'frontal',name:'Frontal'},{id:'lateral',name:'Lateral'}],mus('core_plancha_frontal_iso')),
],[
  {id:'core_plancha_frontal_iso',aspects:{plank_type:'frontal'}},
  {id:'core_plancha_lateral_iso',aspects:{plank_type:'lateral'}},
]);

// 13. PINZA DISCOS
c('forearms_pinza_de_discos','forearms_pinza_de_discos_dos_manos','Pinza de Discos','PinzaDiscos',[
  A('laterality','Lateralidad','dos_manos',[{id:'dos_manos',name:'Dos Manos'},{id:'unilateral',name:'Unilateral'}],mus('forearms_pinza_de_discos_dos_manos')),
],[
  {id:'forearms_pinza_de_discos_dos_manos',aspects:{laterality:'dos_manos'}},
  {id:'forearms_pinza_de_discos_unilateral',aspects:{laterality:'unilateral'}},
]);

// 14. HIPEREXTENSION 45 UNI GLUTEOS
c('glutes_hiperextension_45_unilateral','glutes_hiperextension_45_unilateral','Hiperextension 45° Unilateral','Hiper45Uni',[
  A('equipment','Equipo','propio_peso',[{id:'propio_peso',name:'Propio Peso'},{id:'barra',name:'Barra'},{id:'zercher_barra',name:'Zercher Barra'},{id:'maquina_smith',name:'Máquina Smith'}],mus('glutes_hiperextension_45_unilateral')),
],[
  {id:'glutes_hiperextension_45_unilateral',aspects:{equipment:'propio_peso'}},
  {id:'glutes_hiperextension_45_unilateral_barra',aspects:{equipment:'barra'}},
  {id:'glutes_hiperextension_45_unilateral_zercher_barra',aspects:{equipment:'zercher_barra'}},
  {id:'glutes_hiperextension_45_unilateral_zercher_maquina_smith',aspects:{equipment:'maquina_smith'}},
]);

// 15. SPECIALTY SINGLES
const singles=[
  ['tren_superior_floor_press_barra','equipment','Equipo','barra',[{id:'barra',name:'Barra'}]],
  ['tren_superior_press_banda_resistencia','equipment','Equipo','banda',[{id:'banda',name:'Banda'}]],
  ['tren_superior_aperturas_pec_deck','equipment','Equipo','maquina_pec_deck',[{id:'maquina_pec_deck',name:'Máquina Pec Deck'}]],
  ['triceps_flexiones_esfinge','variant','Variante','esfinge',[{id:'esfinge',name:'Esfinge'}]],
  ['triceps_fondos_entre_bancos','variant','Variante','entre_bancos',[{id:'entre_bancos',name:'Entre Bancos'}]],
  ['back_band_pull_apart','equipment','Equipo','banda',[{id:'banda',name:'Banda'}]],
  ['back_buenos_dias_zercher_barra','load_position','Posición de Carga','zercher_barra',[{id:'zercher_barra',name:'Zercher Barra'}]],
  ['back_superman_suelo','position','Posición','suelo',[{id:'suelo',name:'Suelo'}]],
  ['back_reverse_hyper','equipment','Equipo','peso_corporal',[{id:'peso_corporal',name:'Peso Corporal'}]],
  ['deltoides_press_militar_de_pie','position','Posición','de_pie',[{id:'de_pie',name:'De Pie'}]],
  ['deltoides_press_landmine_unilateral','laterality','Lateralidad','unilateral',[{id:'unilateral',name:'Unilateral'}]],
  ['deltoides_aperturas_inversas_maquina_pec_deck','equipment','Equipo','maquina_pec_deck',[{id:'maquina_pec_deck',name:'Máquina Pec Deck'}]],
  ['quads_zancada_inversa_maquina_v_squat','equipment','Equipo','v_squat',[{id:'v_squat',name:'V-Squat'}]],
  ['glutes_patada_gluteo_polea_diagonal','variant','Variante','diagonal',[{id:'diagonal',name:'Diagonal'}]],
  ['glutes_hiperextension_45_zercher_maquina_smith','equipment','Equipo','maquina_smith',[{id:'maquina_smith',name:'Máquina Smith'}]],
  ['hams_swing_kettlebell_dos_manos','equipment','Equipo','kettlebell',[{id:'kettlebell',name:'Kettlebell'}]],
  ['adductors_plancha_copenhagen_dinamica','variant','Variante','dinamica',[{id:'dinamica',name:'Dinámica'}]],
  ['forearms_curl_muneca_de_pie_tras_espalda_barra','equipment','Equipo','barra',[{id:'barra',name:'Barra'}]],
  ['forearms_paseo_del_granjero_barras_trap','equipment','Equipo','barras_trap',[{id:'barras_trap',name:'Barras Trap'}]],
  ['forearms_enrollamiento_muneca_rodillo','equipment','Equipo','rodillo',[{id:'rodillo',name:'Rodillo'}]],
  ['core_crunch_en_polea_alta','equipment','Equipo','polea_alta',[{id:'polea_alta',name:'Polea Alta'}]],
  ['core_dragon_flag_banco_plano','equipment','Equipo','banco_plano',[{id:'banco_plano',name:'Banco Plano'}]],
  ['tren_superior_flexiones_clasicas','variant','Variante','clasicas',[{id:'clasicas',name:'Clásicas'}]],
  ['tren_superior_flexiones_pies_elevados','variant','Variante','pies_elevados',[{id:'pies_elevados',name:'Pies Elevados'}]],
];
for(const [sid,aid,aname,def,opts] of singles){
  const e=byId.get(sid);
  if(!e)continue;
  e.technicalAspects=[A(aid,aname,def,opts,e.involvedMuscles||[])];
}

const placements=[];const out=rebuildCatalog(db,byId,removeIds,placements);
finalizeAliases(aliases,aliasAdds,removeIds,byId);
saveDb(out);saveAliases(aliases);
const noTA=out.filter(e=>!e.technicalAspects||e.technicalAspects.length===0);
console.log('P4b Singles+Canons:',db.length,'→',out.length,'(-'+removeIds.size+')');
console.log('Con TA:',out.length-noTA.length,'de',out.length);
if(noTA.length){console.log('Sin TA:',noTA.length);noTA.forEach(e=>console.log('  -',e.id));}