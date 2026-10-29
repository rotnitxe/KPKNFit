/* Motor del prototipo: cada instancia tiene su propio contenido y las acciones cambian de cara. */
var state={id:'reps',face:'front'};

var IT=[
['reps','Serie · modos','Reps','Carga + rueda + esfuerzo',{mode:'REPS',load:100,reps:8,rir:2,pr:1,sub:'Objetivo 8 · tempo 2-0-1'}],
['amrap','Serie · modos','AMRAP','Mínimo y resultado',{mode:'AMRAP',load:100,reps:10,rir:0,min:8,result:'11 reps · e1RM 118,4',sub:'Cuenta al e1RM'}],
['calib','Serie · modos','Calibrador','Sin mínimo, ajusta 1RM',{mode:'CALIB',load:90,reps:8,rir:0,band:'Sin mínimo · ajusta el 1RM',sub:'Serie de calibración'}],
['rm','Serie · modos','RM','Máximo de N repeticiones',{mode:'RM',rm:3,target:'105 kg',reps:3,sub:'Máximo de 3 repeticiones'}],
['solo','Serie · modos','Solo RPE','Sin carga externa',{mode:'SOLO',rpe:'8,5',reps:12,sub:'Peso corporal · dominadas'}],
['time','Serie · modos','Tiempo','Reloj por serie',{mode:'TIME',clock:'00:45',target:'45″',reps:0,sub:'Wall sit'}],
['dist','Serie · modos','Distancia','Cardio y ritmo',{mode:'DIST',km:'2,4',pace:'5′04″',sub:'Cinta · zona 2'}],
['custom','Serie · modos','Personalizado','Unidad propia',{mode:'CUSTOM',value:24,unit:'pasos',sets:3,sub:'Sled push'}],
['uni','Serie · estados','Unilateral','Izquierda / derecha',{mode:'UNI',load:40,reps:10,rir:1,sub:'Zancada búlgara'}],
['done','Serie · estados','Completada','Re-registro sin duplicar',{mode:'DONE',load:100,reps:8,rir:2,band:'Actualiza la serie 3 · no crea otra',sub:'Antes RIR 3'}],
['skip','Serie · estados','Saltada','No cuenta al volumen',{mode:'SKIP',load:100,reps:8,band:'Motivo: falta de tiempo',sub:'Serie omitida'}],
['fail','Serie · estados','Fallida','Error de ejecución',{mode:'FAIL',load:100,reps:6,rir:0,band:'La serie no suma volumen',sub:'Rep 6 de 8'}],
['pr','Serie · estados','Récord','PR de la serie',{mode:'PR',load:105,reps:6,rir:1,pr:1,band:'Nuevo máximo · antes 110,0',sub:'+2,5 kg'}],
['load','Serie · estados','Carga requerida','Bloqueo de registro',{mode:'LOAD',load:'—',reps:8,rir:2,band:'No se puede registrar sin carga',sub:'Elige una carga'}],
['repl','Serie · estados','Volumen reemplazado','Sustitución por técnica',{mode:'REPL',load:100,reps:8,rir:2,band:'Cuenta como volumen reemplazado',sub:'Reemplazada'}],
['tech','Serie · caras','Técnica','Drop, pausa, myo',{mode:'TECH'}],
['media','Serie · caras','Modo visual','Foto, vídeo, ficha, álbum',{mode:'MEDIA'}],
['guided','Serie · caras','Técnica guiada','Panel sobre la tarjeta',{mode:'REPS',load:80,reps:6,rir:1,overlay:'guided'}],
['god','Serie · caras','God mode','Acciones de corrección',{mode:'REPS',load:100,reps:8,rir:2,overlay:'god'}],
['undo','Serie · caras','Undo','Deshacer el último registro',{mode:'REPS',load:100,reps:8,rir:2,overlay:'undo'}],
['blocked','Serie · caras','Back bloqueado','Aviso al intentar salir',{mode:'REPS',load:100,reps:8,rir:2,overlay:'blocked'}],
['mob','Preparación','Movilidad','Reloj, lista, global',{mode:'MOB'}],
['warm','Preparación','Aproximación','Escalera A1–A4',{mode:'WARM'}],
['cardio','Preparación','Cardio','Timer, intervalos, FC',{mode:'CARDIO'}],
['rest','Descanso','Descanso','Anillo y controles',{mode:'REST'}],
['overrun','Descanso','Tiempo extra','Se pasó el descanso',{mode:'REST',overrun:1}],
['mini','Descanso','Mini chip','Descanso minimizado',{mode:'REST',mini:1}],
['game','Descanso','Juego','Descanso interactivo',{mode:'REST',game:1}],
['feedback','Descanso','Feedback','Cierre del ejercicio',{mode:'FEEDBACK'}],
['menu','Hojas','Menú','Acciones globales',{kind:'sheet',t:'Menú de la sesión',d:'Todo lo global vive aquí',rows:[['1','Reordenar ejercicios'],['2','Notas de la sesión'],['3','Hitos del día'],['4','Fotos y medios'],['5','Temporizador global'],['6','Calorías del cardio'],['7','Salir del entreno']]}],
['route','Hojas','Ruta','Bloques de la sesión',{kind:'sheet',t:'Ruta',d:'Pierna · Semana 1',rows:[['1','Movilidad · 6'],['2','Aproximación · 4'],['3','Sentadilla trasera'],['4','Peso muerto rumano'],['5','Prensa de piernas']]}],
['timer','Hojas','Temporizador','Global',{kind:'sheet',t:'Temporizador',d:'Global de la sesión',rows:[['▶','Correr / pausar'],['−','−1 min'],['+','+1 min']]}],
['notes','Hojas','Notas','Se guarda al escribir',{kind:'sheet',t:'Nota de la sesión',d:'Se guarda al escribir',rows:[],input:'Escribe…'}],
['milestones','Hojas','Hitos','Solo los reales',{kind:'sheet',t:'Hitos del día',d:'PR y meta estrella',rows:[['★','PR e1RM · Sentadilla'],['★','Meta estrella · Prensa'],['—','Sin más hitos']]}],
['photos','Hojas','Fotos','Álbum y captura',{kind:'sheet',t:'Fotos y medios',d:'Álbum y captura',rows:[['1','Cámara'],['2','Álbum · 8'],['3','Vídeo técnico · 2'],['4','Compartir historia']]}],
['calories','Hojas','Calorías','Solo cardio',{kind:'sheet',t:'Calorías',d:'Solo cardio',rows:[['—','Fuerza · sin estimación'],['1','Cinta · 2,4 km · 210 kcal'],['2','Método MET · nota al pie']]}],
['options','Hojas','Opciones','Ejercicio',{kind:'sheet',t:'Opciones del ejercicio',d:'Sentadilla trasera',rows:[['1','Cambiar ejercicio'],['2','Saltar ejercicio'],['3','Quitar de la sesión'],['4','Crear superserie'],['5','Ver ficha']]}],
['tags','Hojas','Etiquetas','Del ejercicio',{kind:'sheet',t:'Etiquetas',d:'Del ejercicio, no de la serie',rows:[['◉','Barra libre · actual'],['○','Smith'],['○','Multipower'],['○','Polea baja']]}],
['amrapCfg','Hojas','Config AMRAP','Mínimo y techo',{kind:'sheet',t:'Configurar AMRAP',d:'Objetivo de la serie',rows:[['−','Mínimo 8 reps · +'],['−','Sin techo'],['1','Cuenta al e1RM · sí'],['2','Aviso bajo mínimo · sí']]}],
['discomfort','Hojas','Molestias','Catálogo y buscador',{kind:'sheet',t:'Molestias',d:'Se guarda con la serie',rows:[['○','Sin molestias'],['○','Rodilla anterior'],['○','Lumbar'],['○','Hombro anterior']],input:'Buscar molestia'}],
['history','Hojas','Historial','e1RM por sesión',{kind:'sheet',t:'Historial · Sentadilla',d:'e1RM últimas 8 sesiones',rows:[['1','12 oct · 102,5 × 8 · 110,0'],['2','5 oct · 100 × 8 · 107,4'],['3','28 sep · 100 × 7 · 105,1']]}],
['reorder','Hojas','Reordenar','Orden del día',{kind:'sheet',t:'Reordenar',d:'Cambia el orden del día',rows:[['≡','Sentadilla trasera'],['≡','Peso muerto rumano'],['≡','Prensa de piernas']]}],
['ultrafast','Hojas','Ultra-rápido','Un gesto',{kind:'sheet',t:'Registro ultrarrápido',d:'Una serie, un gesto',rows:[['1','100 × 8 · RIR 2'],['2','105 × 6 · RIR 1'],['3','Otra carga']]}],
['rmRef','Hojas','RM del ejercicio','Referencia',{kind:'sheet',t:'RM del ejercicio',d:'Referencia',rows:[['1','1RM 118,0 kg'],['2','TM 106,2 kg'],['3','Recalcular']]}],
['album','Hojas','Álbum','Medios del ejercicio',{kind:'sheet',t:'Álbum · Sentadilla',d:'Fotos y vídeos',rows:[['1','Antes · hoy'],['2','Serie 2 · hoy'],['3','PR · 12 oct']]}],
['exit','Hojas','Salir','Cierre de sesión',{kind:'sheet',t:'Salir del entreno',d:'Se conserva lo registrado',rows:[['1','Guardar y salir'],['2','Descartar sesión'],['3','Seguir entrenando']]}],
['mesa','Mesa','Mesa','Series en filas',{kind:'mesa',expand:'none'}],
['mesaMedia','Mesa','Mesa · media','Modo visual en la fila',{kind:'mesa',expand:'media'}],
['mesaTech','Mesa','Mesa · técnica','Técnica en la fila',{kind:'mesa',expand:'tech'}],
['mesaInjury','Mesa','Mesa · molestia','Molestia en la fila',{kind:'mesa',expand:'injury'}]
];

var TITLES={mob:['Movilidad','Pierna · 6 movimientos'],warm:['Aproximación','Sentadilla trasera · A2 de 4'],cardio:['Cardio','Cinta · intervalos'],rest:['Descanso','Entre series'],overrun:['Descanso','Tiempo extra'],mini:['Descanso','Minimizado'],game:['Descanso','Juego'],feedback:['Feedback','Sentadilla trasera'],mesa:['Pierna · Semana 1','9 ejercicios · 34 series'],mesaMedia:['Pierna · Semana 1','Mesa con modo visual'],mesaTech:['Pierna · Semana 1','Mesa con técnica'],mesaInjury:['Pierna · Semana 1','Mesa con molestia']};
var LABEL={mob:'Hecho · siguiente',warm:'Marcar A2',cardio:'Pausar',rest:'Saltar descanso',overrun:'Saltar',mini:'Abrir descanso',game:'Saltar',feedback:'Guardar feedback',mesa:'Registrar serie',mesaMedia:'Registrar serie',mesaTech:'Registrar tramo',mesaInjury:'Guardar molestia'};

function phone(t,s,inner,label,extra){
 return '<div class="status"><span>1:47</span><span>▮▮▮</span></div><div class="hdr"><div class="circ">‹</div><div class="txt"><div class="t">'+t+'</div><div class="s">'+s+'</div></div><div class="restchip"><span class="ring"></span>1:12</div><div class="circ">⋯</div></div>'+inner+bottom(label)+extra;
}
function bottom(label){
 return '<div class="bottom"><div class="roadhead"><span class="l">Series · 3 de 9</span><span class="r">Sentadilla 3 / 4</span></div><div class="road"><i class="done" style="width:15%"></i><i class="done" style="width:20%"></i><i class="on" style="width:53%"></i><i style="width:12%"></i></div><div class="roadlabels"><span style="width:15%">MOV</span><span style="width:20%">APR</span><span class="on" style="width:53%">SER</span><span style="width:12%">REST</span></div><div class="navrow"><div class="step">‹</div><div class="rec">'+label+'</div><div class="step">›</div></div></div>';
}
function tag(t,cls){return '<span class="tag '+(cls||'')+'" style="margin-left:6px">'+t+'</span>';}
function band(t,cls){return '<div class="band '+(cls||'')+'">'+t+'</div>';}
function wheel(n){return '<div class="wheel"><span>'+(n-2)+'</span><span>'+(n-1)+'</span><span class="c">'+n+'</span><span>'+(n+1)+'</span><span>'+(n+2)+'</span></div><div class="wlab"><span>REPS</span><span>OBJETIVO '+n+'</span></div>';}
function fire(i){return '<div class="fire">'+[0,1,2,3,4].map(function(x,k){return '<div class="'+(k===i?'on':'')+'">'+x+'</div>';}).join('')+'</div><div class="wlab" style="margin-top:3px"><span>RIR</span><span>FALLO</span></div>';}
function kp(){return '<div class="kpills"><div class="kpill">−2,5</div><div class="kpill">Sug <b>102,5</b></div><div class="kpill">+2,5</div></div>';}
function rom(){return '<div class="pillrow"><span class="sp">ROM</span><span class="sp">Vídeo</span><span class="sp">Detalles</span></div>';}
function hist(){return '<div class="hist"><div class="hlab">ÚLTIMAS 4 · e1RM<br><b>112,5 kg</b></div><div class="hbars"><i style="height:34%"></i><i style="height:52%"></i><i style="height:70%"></i><i class="on" style="height:94%"></i></div></div>';}

function hero(c){
 var v,u,side;
 if(c.mode==='TIME'){v=c.clock;u='restante';side='<b>'+c.target+'</b><span>objetivo</span>';}
 else if(c.mode==='DIST'){v=c.km;u='km';side='<b>'+c.pace+'</b><span>ritmo</span>';}
 else if(c.mode==='SOLO'){v=c.rpe;u='RPE';side='<b>× '+c.reps+'</b><span>reps</span>';}
 else if(c.mode==='RM'){v=c.rm;u='RM';side='<b>'+c.target+'</b><span>objetivo</span>';}
 else if(c.mode==='CUSTOM'){v=c.value;u=c.unit;side='<b>× '+c.sets+'</b><span>rondas</span>';}
 else {v=c.load;u='kg';side='<b>RIR '+c.rir+'</b><span>e1RM 112,5</span>';}
 return '<div class="hero"><span class="v">'+v+'</span><span class="u">'+u+'</span><span class="side">'+side+'</span></div><div class="sub">'+(c.sub||'')+'</div>';
}
function body(c){
 if(c.mode==='TIME') return '<div class="field">Iniciar · pausar · reiniciar</div>'+rom()+hist();
 if(c.mode==='DIST') return '<div class="kpills"><div class="kpill">FC 142</div><div class="kpill">kcal 210</div><div class="kpill">Z2</div></div><div class="field" style="margin-top:7px">Distancia manual o GPS</div><div class="mline">Splits 5′04″ · 5′01″ · 5′00″</div>'+hist();
 if(c.mode==='RM') return '<div class="field">Carga objetivo · última vez 102,5</div>'+wheel(c.reps)+fire(0)+rom()+hist();
 if(c.mode==='SOLO') return '<div class="field">Sin carga externa</div>'+wheel(c.reps)+fire(1)+rom()+hist();
 if(c.mode==='CUSTOM') return '<div class="field">Unidad propia · «'+c.unit+'»</div>'+fire(2)+rom()+hist();
 var s=kp()+wheel(c.mode==='AMRAP'?10:c.reps)+fire(c.rir===0?0:(c.rir||2));
 if(c.result) s+='<div class="band ok">'+c.result+'</div>';
 if(c.band) s+=band(c.band,c.mode==='DONE'||c.mode==='PR'?'ok':(c.mode==='SKIP'?'dim':''));
 if(c.mode==='UNI') s='<div class="seg" style="margin-top:2px"><b class="on">Izquierda</b><b>Derecha</b></div>'+s;
 if(c.mode==='FAIL') s='<div class="seg" style="margin-top:2px"><b class="on">Fallo</b><b>Técnica</b><b>Dolor</b><b>Otro</b></div><div class="field" style="margin-top:7px">Motivo opcional</div>'+rom()+hist();
 if(c.mode==='PR') s+='<div class="field" style="margin-top:6px">Foto o vídeo del PR</div>';
 if(c.mode==='SKIP') s='<div class="cs"><div class="field">Recuperar serie</div>'+hist()+'</div>';
 return s+rom()+hist();
}
function acts(list){
 return '<div class="acts">'+list.map(function(a){return '<span data-act="'+a+'">'+a+'</span>';}).join('')+'</div>';
}
function card(c){
 var eyebrow={REPS:'Serie efectiva',AMRAP:'AMRAP',CALIB:'Calibrador',RM:'RM',SOLO:'Solo RPE',TIME:'Tiempo',DIST:'Distancia',CUSTOM:'Personalizado',UNI:'Unilateral',DONE:'Completada',SKIP:'Saltada',FAIL:'Fallida',PR:'Récord',LOAD:'Carga requerida',REPL:'Volumen reemplazado',TECH:'Técnica',MEDIA:'Modo visual'}[c.mode]||'Serie efectiva';
 var head='<div class="head"><span class="dot"></span><span class="eyebrow">'+eyebrow+'</span>'+(c.pr?tag('★ PR','pr'):'')+((c.mode==='DONE'||c.mode==='SKIP'||c.mode==='FAIL')?tag(c.mode==='FAIL'?'rep 6 de 8':c.mode.toLowerCase(),'') : '')+'<span class="meta">3 / 4</span></div>';
 return '<div class="card '+(c.mode==='FAIL'?'fail':'')+'" '+(c.mode==='FAIL'?'style="border-color:rgba(255,155,146,.45)"':'')+'>'+head+hero(c)+'<div class="body">'+body(c)+'</div>'+acts(['Avanzado','Modo visual','Historial'])+'</div>';
}
function techCard(){
 return '<div class="card"><div class="head"><span class="dot"></span><span class="eyebrow">Técnica de la serie</span><span class="meta">3 / 4</span></div>'
 +'<div class="seg"><b class="on">Normal</b><b>Drop</b><b>Pausa</b><b>Myo</b></div>'
 +'<div class="chain"><b>Principal</b><i>→</i><span>Drop 1</span><i>→</i><span style="color:var(--faint)">Añadir</span></div>'
 +'<div class="body"><div class="trow"><span class="nm">Drop 1</span><div class="stepper"><span>−</span><b>80 kg</b><span>+</span></div></div>'
 +'<div class="trow"><span class="nm">Reps del tramo</span><div class="stepper"><span>−</span><b>6</b><span>+</span></div></div>'
 +'<div class="trow"><span class="nm">Descanso entre tramos</span><div class="stepper"><span>−</span><b>15″</b><span>+</span></div></div>'
 +'<div class="field">Nota de la serie</div><div class="pillrow"><span class="sp">Error de ejecución</span></div></div>'
 +acts(['Serie 3','PR: foto o vídeo'])+'</div>';
}
function mediaCard(){
 return '<div class="card"><div class="head"><span class="dot"></span><span class="eyebrow">Modo visual</span><span class="meta">3 / 4</span></div>'
 +'<div class="mtop"><div class="sw"><b class="on">Foto</b><b>Vídeo</b></div><span class="sp" style="margin-left:auto">Ficha</span><span class="sp">Álbum</span></div>'
 +'<div class="mstage"><span class="lbl">Encuadre de la serie</span><div class="shutter"></div></div>'
 +'<div class="thumbs"><i>Antes</i><i>Serie 2</i><i>PR</i><i>+2</i></div><div class="mline">2 fotos · análisis disponible</div>'
 +acts(['Serie 3','Ver álbum','Historial'])+'</div>';
}
function mobCard(){
 return '<div class="card mob"><div class="head"><span class="dot"></span><span class="eyebrow">Movilidad</span><span class="meta">2 / 6</span></div><div class="hero"><span class="v">02:30</span><span class="u">restante</span><span class="side"><b>3 × 40″</b><span>por lado</span></span></div><div class="sub">Sentadilla profunda</div><div class="body">'
 +['Tobillo|40″|done','Sentadilla profunda|3 × 40″|on','Apertura de cadera|8 reps|','Isquios|30″|'].map(function(r){var p=r.split('|');return '<div class="mrow '+p[2]+'"><span class="check">'+(p[2]==='done'?'✓':'')+'</span><span class="nm">'+p[0]+'</span><span class="dose">'+p[1]+'</span></div>';}).join('')
 +'</div>'+acts(['A tu ritmo','Saltar fase'])+'</div>';
}
function warmCard(){
 var rows=[['A1','20 %','20','× 10','60″'],['A2','40 %','60','× 5','90″'],['A3','60 %','75','× 3','90″'],['A4','80 %','90','× 1','120″']];
 return '<div class="card warm"><div class="head"><span class="dot"></span><span class="eyebrow">Aproximación</span><span class="meta">100 kg objetivo</span></div><div class="hero"><span class="v">60</span><span class="u">kg</span><span class="side"><b>× 5</b><span>40 % · 90″</span></span></div><div class="sub">Serie A2 de la escalera</div><div class="body">'
 +rows.map(function(r,i){return '<div class="trow" '+(i===1?'style="background:rgba(255,161,92,.07);border:1px solid rgba(255,161,92,.22);border-radius:9px;padding:4px 5px"':'')+'><span class="nm" style="width:26px;color:var(--amber);font-weight:700">'+r[0]+'</span><span class="nm" style="width:38px;color:var(--faint)">'+r[1]+'</span><span class="nm" style="text-align:right;font-family:Syne;font-weight:700;font-size:16px">'+r[2]+'</span><span class="nm" style="width:38px;text-align:right;color:var(--muted)">'+r[3]+'</span><span class="nm" style="width:30px;text-align:right;color:var(--faint)">'+r[4]+'</span></div>';}).join('')
 +'</div>'+acts(['Añadir serie','Continuar'])+'</div>';
}
function cardioCard(){
 return '<div class="card"><div class="head"><span class="dot"></span><span class="eyebrow">Cardio</span><span class="meta">zona 2</span></div><div class="hero"><span class="v">18:24</span><span class="u">transcurrido</span><span class="side"><b>12,4 km/h</b><span>4′50″/km</span></span></div><div class="sub">Cinta · intervalos 2-1</div><div class="body"><div class="kpills"><div class="kpill">FC 142</div><div class="kpill">210 kcal</div><div class="kpill">Z2</div></div><div class="field" style="margin-top:7px">Intervalos · 4 de 8</div><div class="mline">Splits 5′04″ · 5′01″ · 5′00″</div>'+hist()+'</div>'+acts(['Pausar','Modo visual','Historial'])+'</div>';
}
function restCard(c){
 var extra=c.overrun?'<div class="band">El descanso se pasó · siguiente set puede verse afectado</div>':(c.mini?'<div class="field">Descanso 1:12 · +15 s</div>':(c.game?'<div class="field">Mini-juego activo · récord 18</div>':'<div class="band ok">Sugerido 1:30 · última RIR 2</div>'));
 return '<div class="card"><div class="head"><span class="dot"></span><span class="eyebrow">Descanso</span><span class="meta">entre series</span></div><div class="hero"><span class="v">1:12</span><span class="u">restante</span><span class="side"><b>2:00</b><span>total</span></span></div><div class="sub">Siguiente: serie 4 · 100 × 8</div><div class="body">'+extra+'<div class="field" style="margin-top:7px">−15 s · Saltar · +15 s</div>'+hist()+'</div>'+acts(['Minimizar','Saltar descanso'])+'</div>';
}
function feedbackCard(){
 return '<div class="card"><div class="head"><span class="dot"></span><span class="eyebrow">Feedback del ejercicio</span><span class="meta">4 series</span></div><div class="hero"><span class="v">8</span><span class="u">esfuerzo</span><span class="side"><b>2</b><span>molestias</span></span></div><div class="sub">Sentadilla trasera</div><div class="body"><div class="seg" style="margin-top:2px"><b class="on">Bien</b><b>Regular</b><b>Mal</b></div><div class="field" style="margin-top:7px">Calidad técnica</div><div class="field" style="margin-top:6px">Molestias</div><div class="mline">Se guarda con el ejercicio</div></div>'+acts(['Saltar feedback','Guardar'])+'</div>';
}
function overlay(c){
 var o=c.overlay;
 var data={guided:['Técnica guiada','Drop-set · tramo 1 de 2',['80 kg × 6','Descanso 15″','Siguiente tramo']],god:['God mode','Corrección manual',['Editar serie','Eliminar serie','Forzar completada']],undo:['Deshecho','Serie 3 eliminada',['Rehacer','Ver historial']],blocked:['Salir del entreno','Hay una serie en curso',['Seguir','Guardar y salir']]}[o];
 return card(c)+'<div class="ov"><div class="ovp"><div class="ovt">'+data[0]+'</div><div class="ovd">'+data[1]+'</div>'+data[2].map(function(r){return '<div class="ovr">'+r+'</div>';}).join('')+'<div class="ovb">'+data[2][0]+'</div></div></div>';
}
function sheet(kind,cfg){
 var extra=cfg.input?'<div class="field" style="height:40px;margin-top:8px">'+cfg.input+'</div>':'';
 return '<div class="scrim"></div><div class="sheet"><div class="handle"></div><div class="stitle">'+cfg.t+'</div><div class="sdesc">'+cfg.d+'</div>'+(cfg.rows||[]).map(function(r){return '<div class="srow"><span class="k">'+r[0]+'</span><span>'+r[1]+'</span></div>';}).join('')+extra+'</div>';
}
function mesaRows(exp){
 var s='<div class="tghead"><span class="nm">Sentadilla trasera</span><span class="tag">Barra libre</span><span class="meta">100 kg · 4 series · ⋯</span></div>'
 +'<div class="tr done"><span class="idx">A1</span><span class="ex">20 %</span><span class="ld">20 × 10</span><span class="ef"></span><span class="st">✓</span></div>'
 +'<div class="tr done"><span class="idx">A2</span><span class="ex">40 %</span><span class="ld">60 × 5</span><span class="ef"></span><span class="st">✓</span></div>'
 +'<div class="tr done"><span class="idx">1</span><span class="ex">Efectiva</span><span class="ld">100 × 8</span><span class="ef">RIR 3</span><span class="st">✓</span></div>'
 +'<div class="tr done"><span class="idx">2</span><span class="ex">Efectiva</span><span class="ld">100 × 8</span><span class="ef">RIR 2</span><span class="st">✓</span></div>'
 +'<div class="tr active"><span class="idx">3</span><span class="ex">Efectiva</span><span class="ld">100 × 8</span><span class="ef">RIR 2</span><span class="st">●</span></div>'
 +'<div class="editor"><div class="edrow"><span class="big">100</span><span class="u">kg</span><div class="mini"><span>−2,5</span><span>+2,5</span></div></div>'
 +'<div class="edrow2"><div class="rir"><span>0</span><span>1</span><span class="on">2</span><span>3</span><span>4</span></div><span class="e1">e1RM 112,5</span></div>'
 +'<div class="edacts"><span>Técnica</span><span>ROM</span><span>Molestia</span><span>Nota</span><span>Vídeo</span><span>Foto</span><span>Hist.</span></div>'
 +(exp==='media'?'<div class="mtop" style="margin-top:7px"><div class="sw"><b class="on">Foto</b><b>Vídeo</b></div><span class="sp" style="margin-left:auto">Ficha</span><span class="sp">Álbum</span></div><div class="thumbs"><i>Antes</i><i>Serie 2</i><i>PR</i><i>+2</i></div>':'')
 +(exp==='tech'?'<div class="seg" style="margin-top:7px"><b class="on">Drop</b><b>Pausa</b><b>Myo</b></div><div class="chain"><b>Principal</b><i>→</i><span>Drop 1 · 80 × 6</span></div>':'')
 +(exp==='injury'?'<div class="band" style="margin-top:7px">Molestia: rodilla anterior · hoy</div>':'')
 +'</div>'
 +'<div class="tr"><span class="idx">4</span><span class="ex">Efectiva</span><span class="ld">100 × 8</span><span class="ef">—</span><span class="st">·</span></div>'
 +'<div class="tghead"><span class="nm">Peso muerto rumano</span><span class="tag">Multipower</span><span class="meta">140 kg · 3 series · ⋯</span></div>'
 +'<div class="tr done"><span class="idx">A1</span><span class="ex">60 %</span><span class="ld">80 × 5</span><span class="ef"></span><span class="st">✓</span></div>'
 +'<div class="tr"><span class="idx">1</span><span class="ex">Efectiva</span><span class="ld">140 × 5</span><span class="ef">RIR 2</span><span class="st">·</span></div>'
 +'<div class="tr"><span class="idx">2</span><span class="ex">Efectiva</span><span class="ld">140 × 5</span><span class="ef">—</span><span class="st">·</span></div>';
 return '<div class="table">'+s+'</div>';
}
function render(id){
 var it=null;
 for(var i=0;i<IT.length;i++){if(IT[i][0]===id){it=IT[i];break;}}
 if(!it) it=IT[0];
 var cfg=it[4];
 var t=TITLES[id]||[it[2],'Serie 3 de 4 · 100 kg'];
 var inner='',label=LABEL[id]||'Registrar serie',extra='';
 if(cfg.kind==='sheet'){inner='<div class="zone" style="justify-content:center"><div class="mline">La hoja se abre sobre la tarjeta</div></div>';extra=sheet(id,cfg);}
 else if(cfg.kind==='mesa'){inner=mesaRows(cfg.expand);}
 else if(cfg.mode==='MOB'){inner='<div class="zone">'+mobCard()+'</div>';}
 else if(cfg.mode==='WARM'){inner='<div class="zone">'+warmCard()+'</div>';}
 else if(cfg.mode==='CARDIO'){inner='<div class="zone">'+cardioCard()+'</div>';}
 else if(cfg.mode==='REST'){inner='<div class="zone">'+restCard(cfg)+'</div>';}
 else if(cfg.mode==='FEEDBACK'){inner='<div class="zone">'+feedbackCard()+'</div>';}
 else if(cfg.overlay){inner='<div class="zone">'+overlay(cfg)+'</div>';}
 else {var c=Object.assign({},cfg);if(state.face==='tech'&&state.id===id)inner='<div class="zone">'+techCard()+'</div>';else if(state.face==='media'&&state.id===id)inner='<div class="zone">'+mediaCard()+'</div>';else inner='<div class="zone">'+card(c)+'</div>';}
 document.getElementById('phone').innerHTML=phone(t[0],t[1],inner,label,extra);
 document.querySelectorAll('.it').forEach(function(b){b.classList.toggle('on',b.dataset.go===id);});
 document.querySelectorAll('.acts span').forEach(function(a){
  a.onclick=function(){
   var x=a.dataset.act;
   if(x==='Avanzado'){state.face='tech';}
   else if(x==='Modo visual'){state.face='media';}
   else if(x==='Historial'){document.getElementById('phone').insertAdjacentHTML('beforeend',sheet('history',{t:'Historial · Sentadilla',d:'e1RM últimas 8 sesiones',rows:[['1','12 oct · 102,5 × 8 · 110,0'],['2','5 oct · 100 × 8 · 107,4']]}));return;}
   render(id);
  };
 });
 document.querySelectorAll('.srow').forEach(function(r){r.onclick=function(){location.hash=id;};});
 location.hash=id;
}
var idx=document.getElementById('index');
var groups={};IT.forEach(function(i){(groups[i[1]]=groups[i[1]]||[]).push(i);});
idx.innerHTML=Object.keys(groups).map(function(g){return '<div class="g">'+g+'</div>'+groups[g].map(function(i){return '<button class="it" data-go="'+i[0]+'"><span>'+i[2]+'</span><small>'+i[3]+'</small></button>';}).join('');}).join('');
idx.addEventListener('click',function(e){var b=e.target.closest('.it');if(b){state.face='front';render(b.dataset.go);}});
render(location.hash.replace('#','')||'reps');
