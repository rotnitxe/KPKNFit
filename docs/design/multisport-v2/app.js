'use strict';
const STORE='kpkn-multisport-v2-review-6';
const PREVIEW_MODE=new URLSearchParams(location.search).has('preview');
if(PREVIEW_MODE)document.body.classList.add('embed-preview');
const INITIAL={
 sports:['strength','running'],levels:{strength:'some',running:'new'},objectives:{strength:'strength',running:'base'},priority:'strength',available:[0,1,3,5],minutes:75,
 equipment:['bar','gym','outside'],activePlaces:['gym','outside'],place:'gym',places:{gym:['bar','rack','db','cable'],home:['bands','db'],outside:['route'],pool:['pool25']},powerliftingPath:'',powerliftingVenue:'home',powerliftingChoice:'',
 windowDay:0,windows:{0:{start:'18:00',end:'19:15',place:'gym'},1:{start:'18:30',end:'19:45',place:'outside'},2:{start:'17:00',end:'18:15',place:'gym'},3:{start:'18:00',end:'19:15',place:'gym'},4:{start:'18:00',end:'19:15',place:'gym'},5:{start:'08:00',end:'09:15',place:'outside'},6:{start:'08:00',end:'09:15',place:'outside'}},runningContext:'after',runningRpe:6,brickT2:2,brickBike:40,brickRun:20,
 classEnabled:true,className:'Grappling',classDiscipline:'external',classDay:2,classStart:'19:00',classEnd:'20:00',
 homeDay:6,alternative:0,calendar:[{id:'force-a',name:'Fuerza A',sport:'strength',day:0,time:'18:00',min:65},{id:'run-a',name:'Running suave',sport:'running',day:1,time:'18:30',min:30},{id:'class',name:'Grappling',sport:'grappling',day:2,time:'19:00',min:60,fixed:true},{id:'force-b',name:'Fuerza B',sport:'strength',day:3,time:'18:30',min:70},{id:'run-b',name:'Rodaje largo',sport:'running',day:5,time:'08:00',min:75}],moving:'force-a',
 load:80,reps:5,rir:5,effortTouched:false,sets:[],cardioBlock:0,cardioTarget:'pace',summaryTab:'today',note:'',progressSport:'strength',chartPoint:6,
 labLoad:80,labReps:5,labIncrement:2.5,ruleSessions:1,labVariant:'competition',labTechnique:'same',complexLoad:60,complexReps:[1,2,1],complexLink:0,complexRounds:5,complexRest:120,complexDone:0,
 skill:1,holdSeconds:8,skillLogs:[],medleyDone:[],medleyTime:93,armSide:'right',armAngle:90,armSeconds:20,armLoad:15,armRpe:7,armGrip:'neutral',armLogs:[],
 amrapReps:102,emomMinute:4,emomDone:[0,1,2,3],forTimeRemaining:18,forTimeFinished:false,forTimeCapped:false,hyroxStage:0,hyroxTransition:30,
 round:0,roundSeconds:180,roundRunning:false,comboHit:0,combos:0,strikingNote:'',position:'guard',transitions:['guard'],rollingMinutes:5,rollingOutcome:'escape',rollingLogs:[],campWeek:2,
 ftp:220,ftpPercent:90,cyclingInterval:1,trailPoint:3,rowSplit:120,rowDistance:500,rowLogs:[],poolLength:25,swimLengths:8,swimStroke:'crawl',swimRest:30,triStage:0,
 climbHold:2,climbAttempts:[1,2],climbNote:'',testType:'sprint',testValue:4.35,sprintTests:[4.48,4.41,4.37],jumpTests:[38.5,39.2,38.8],
 tennis:{points:[3,3],games:[2,2],sets:[0,0],lastPoint:0,history:[],notice:'',tiebreak:false,tie:[0,0]},padel:{points:[3,3],games:[2,2],sets:[0,0],lastPoint:0,history:[],notice:'',tiebreak:false,tie:[0,0]},
 teamMinutes:68,teamRpe:7,teamActions:{shots:2,passes:3,recoveries:5},sensorMode:'fresh',sensorSource:'Banda de pecho',energySelection:'combined',energyManual:180,energyImported:185,energyInclude:'manual',combinedTab:'timeline',competitionMode:'all'
};
let P=structuredClone(INITIAL);
if(!PREVIEW_MODE)try{const saved=JSON.parse(localStorage.getItem(STORE)||'null');if(saved)P=Object.assign(P,saved);}catch(_){/* file:// storage may be disabled; in-memory use remains functional. */}
if(new URLSearchParams(location.search).has('reset')){P=structuredClone(INITIAL);try{const clean=new URL(location.href);clean.searchParams.delete('reset');history.replaceState(null,'',clean);}catch(_){}}
for(const key of ['windowDay','classDay','homeDay'])if(!Number.isInteger(P[key])||P[key]<0||P[key]>6)P[key]=INITIAL[key];
function save(){if(PREVIEW_MODE)return;try{localStorage.setItem(STORE,JSON.stringify(P));}catch(_){}}
function getValue(key){return key.split('.').reduce((o,k)=>o?.[k],P);}
function setValue(key,value){const parts=key.split('.');let obj=P;for(const key of parts.slice(0,-1)){if(obj[key]==null)obj[key]={};obj=obj[key];}obj[parts.at(-1)]=value;}
const NUMERIC=new Set(['minutes','windowDay','classDay','alternative','homeDay','reps','load','cardioBlock','chartPoint','labLoad','labReps','labIncrement','ruleSessions','complexLoad','complexLink','complexRounds','complexRest','skill','holdSeconds','medleyTime','armAngle','armSeconds','armLoad','armRpe','emomMinute','hyroxTransition','comboHit','rollingMinutes','campWeek','ftp','ftpPercent','cyclingInterval','trailPoint','rowSplit','rowDistance','poolLength','swimLengths','swimRest','triStage','climbHold','testValue','teamMinutes','teamRpe','energyManual','energyImported','runningRpe','brickT2','brickBike','brickRun']);
let route='',currentScenario;
function reviewNav(){
 const groups=[...new Set(SCENARIOS.map(s=>s.group))];
 document.getElementById('review-sidebar').innerHTML=`<div class="eyebrow">Propuesta · solo ampliaciones</div><span class="brand">KPKN multideporte</span><div class="review-links"><a href="./gallery.html">Galería visual</a><a href="./QA-INVENTORY.md">Inventario QA</a></div>${groups.map(g=>`<section class="review-group"><h2>${g}</h2>${SCENARIOS.filter(s=>s.group===g).map(s=>`<a href="#${s.id}" class="${s.id===route?'active':''}">${s.title}</a>`).join('')}</section>`).join('')}`;
 if(currentScenario)document.getElementById('review-context').innerHTML=`<div class="eyebrow">Qué se agrega</div><h2>${currentScenario.title}</h2><p>${currentScenario.add||currentScenario.focus}</p><div class="review-rule"></div><div class="eyebrow">Control para revisar</div><p>${currentScenario.control}</p><button data-review="reset">Reiniciar estados locales</button><button data-review="pilot">Cargar caso fuerza + running</button><div class="review-rule"></div><p class="caption">Muestra local de planificación. Los motores nuevos siguen siendo propuestas. Datos y proyecciones de ejemplo. No es implementación Android ni evidencia de hardware o aprobación visual.</p><p class="caption">Las pestañas inferiores conservan el shell KPKN existente. La galería propone únicamente ampliaciones.</p>`;
}
function render(keepScroll=false){
 syncCalendarClass();
 const old=document.querySelector('.app-scroll')?.scrollTop||0;
 const previousRoute=route;
 const openPanels=keepScroll?Array.from(document.querySelectorAll('.app-scroll details')).flatMap((panel,index)=>panel.open?[{index,title:panel.querySelector('summary')?.textContent.trim()||''}]:[]):[];
 const id=decodeURIComponent(location.hash.slice(1))||'wizard-sports';
 route=SCENARIOS.some(s=>s.id===id)?id:'wizard-sports';currentScenario=SCENARIOS.find(s=>s.id===route);
 const renderer=V[route]||(()=>F.root(F.head(currentScenario.title,'Ampliación en revisión')));
 document.getElementById('app').innerHTML=renderer();
 document.title=`${currentScenario.title} · KPKN multideporte`;
 if(keepScroll&&route===previousRoute){
  const panels=Array.from(document.querySelectorAll('.app-scroll details'));
  for(const saved of openPanels){const panel=panels[saved.index];if(panel&&(panel.querySelector('summary')?.textContent.trim()||'')===saved.title)panel.open=true;}
  document.querySelector('.app-scroll').scrollTop=old;
 }
 reviewNav();save();
}
const navigate=id=>{if(location.hash==='#'+id)render();else location.hash=id;};
function toggle(array,value){const at=array.indexOf(value);if(at>=0)array.splice(at,1);else array.push(value);}
function moveSheet(id){
 P.moving=id;const s=P.calendar.find(s=>s.id===id);if(!s)return;
 K.sheet('Mover '+K.esc(s.name),`<p>${s.time} · ${s.min} min</p>${['Lunes 19','Martes 20','Miércoles 21','Jueves 22','Viernes 23','Sábado 24','Domingo 25'].map((d,i)=>F.choice('move-to',d,K.esc(P.classEnabled&&P.classDay===i?P.className+' '+P.classStart+'–'+P.classEnd+' · fijo':P.calendar.filter(x=>x.day===i&&!x.fixed&&x.id!==id).map(x=>x.name).join(' · ')||'Sin sesión'),s.day===i,`data-value="${i}"`)).join('')}`);
}
function moveTo(day){
 const s=P.calendar.find(s=>s.id===P.moving);if(!s)return;
 document.querySelector('.sheet-shade')?.remove();
 P.moveTarget=day;
 if(P.classEnabled&&day===P.classDay&&timeMinutes(s.time)<timeMinutes(P.classEnd)&&timeMinutes(s.time)+s.min+10>timeMinutes(P.classStart)){navigate('conflict');return;}
 const exists=P.calendar.find(x=>x.day===day&&!x.fixed&&x.id!==s.id);
 if(exists){K.sheet('Ya hay otra sesión',`<p>${K.esc(exists.name)} está en ese día. Elige otro hueco o compara alternativas.</p>${F.link('alternative','Comparar alternativas')}`);return;}
 const issue=placementIssue(s.sport,day,s.min,s.time);
 if(issue){K.sheet('Revisa el hueco',`<p>${K.esc(issue)}</p>${F.link('wizard-availability','Revisar ventana y sede')}${F.link('wizard-equipment','Revisar material','pill secondary')}`);return;}
 s.day=day;save();render(true);K.toast('Sesión movida · puedes volver a cambiarla');
}
function points(sport,who){
 const g=P[sport];g.history.push(JSON.stringify({...g,history:[]}));g.lastPoint=who;g.notice='';
 if(g.tiebreak){g.tie[who]++;if(g.tie[who]>=7&&g.tie[who]-g.tie[1-who]>=2){g.sets[who]++;g.games=[0,0];g.tie=[0,0];g.points=[0,0];g.tiebreak=false;g.notice='Set completado';}return;}
 g.points[who]++;
 const golden=sport==='padel',win=golden?g.points[who]>=4:(g.points[who]>=4&&g.points[who]-g.points[1-who]>=2);
 if(win){g.games[who]++;g.points=[0,0];g.notice=who===0?'Juego para ti':'Juego rival';
 if(g.games[who]>=6&&g.games[who]-g.games[1-who]>=2){g.sets[who]++;g.games=[0,0];g.notice='Set completado';}
 else if(g.games[0]===6&&g.games[1]===6){g.tiebreak=true;g.tie=[0,0];g.notice='Tie-break';}}
}
function action(act,el){
 const val=el.dataset.value,key=el.dataset.key;
 switch(act){
  case 'select':setValue(key,NUMERIC.has(key)||key.startsWith('complexReps.')?Number(val):val);if(key==='testType')P.testValue=val==='jump'?39.5:4.35;if(key==='sensorMode'&&route!=='sensors')navigate('sensors');break;
  case 'sport-toggle':toggle(P.sports,val);if(P.priority!=='balanced'&&!P.sports.includes(P.priority))P.priority=P.sports[0]||'balanced';break;
  case 'available-toggle':toggle(P.available,Number(val));P.available.sort();break;
  case 'equipment-toggle':toggle(P.equipment,val);break;
  case 'place-toggle':toggle(P.activePlaces,val);break;
  case 'place-item':toggle(P.places[P.place],val);break;
  case 'power-path':P.powerliftingChoice=val;break;
  case 'power-confirm':P.powerliftingPath=P.powerliftingChoice;if(!P.powerliftingPath){K.toast('Elige un camino para continuar');return;}if(!P.sports.includes('powerlifting')){P.sports=['powerlifting'];P.priority='powerlifting';P.activePlaces=['home'];P.available=[0,3,5];for(const d of P.available)P.windows[d].place='home';}navigate('wizard-equipment');return;
  case 'build-plan':{const result=planForSelection();if(result.unplaced.length||!P.sports.length){K.toast('Revisa los huecos pendientes');return;}P.calendar=result.sessions;navigate('week');return;}
  case 'edit-commitment':K.sheet('Tu compromiso fijo',F.field('Nombre','className',P.className,'text')+'<label class="field">Vincular a disciplina<select data-field="classDiscipline"><option value="external" '+(P.classDiscipline==='external'?'selected':'')+'>Externo / sin objetivo</option>'+Object.entries(sportNames).map(([id,name])=>'<option value="'+id+'" '+(P.classDiscipline===id?'selected':'')+'>'+name+'</option>').join('')+'</select></label><label class="field">Día<select data-field="classDay">'+['Lunes','Martes','Miércoles','Jueves','Viernes','Sábado','Domingo'].map((n,i)=>'<option value="'+i+'" '+(P.classDay===i?'selected':'')+'>'+n+'</option>').join('')+'</select></label><div class="two-fields">'+F.field('Inicio','classStart',P.classStart,'time')+F.field('Fin','classEnd',P.classEnd,'time')+'</div>'+F.btn('commitment-save','Guardar compromiso','pill')+F.btn('class-toggle',P.classEnabled?'Quitar de esta semana':'Incluir en esta semana','text-action'));return;
  case 'commitment-save':if(timeMinutes(P.classEnd)<=timeMinutes(P.classStart)){K.toast('El fin debe ser posterior al inicio');return;}P.className=P.className.trim()||'Clase';P.classEnabled=true;document.querySelector('.sheet-shade')?.remove();break;
  case 'class-toggle':P.classEnabled=!P.classEnabled;document.querySelector('.sheet-shade')?.remove();break;
  case 'fixed-class':navigate('compatibility');return;
  case 'move-session':moveSheet(val);return;
  case 'move-to':moveTo(Number(val));return;
  case 'apply-alternative':{const s=movingSession(),o=alternativeOptions()[P.alternative];if(!s||!o)return;const issue=alternativeIssue(s,o);if(issue){K.toast(issue);return;}const target=alternativeWindow(s,o);s.day=o.day;s.time=o.time;s.place=target.place;if(!P.available.includes(s.day))P.available.push(s.day);P.windows[s.day]=target;navigate('week');K.toast('Hueco confirmado · compromiso conservado');return;}
  case 'load-shortcut':P.load=Number(val);break;
  case 'apply-plan':P.load=80;P.reps=5;K.toast('Carga y reps actualizadas. Confirma tu esfuerzo.');break;
  case 'register-set':if(!P.effortTouched)return;P.sets.push({load:P.load,reps:P.reps,rir:P.rir});P.effortTouched=false;P.rir=5;if(P.sets.length>=3){navigate('transition');return;}K.toast('Serie registrada');break;
  case 'next-block':case 'start-cardio':navigate('running');return;
  case 'previous-set':case 'set-detail':case 'live-history':K.sheet('Tus registros',P.sets.map((s,i)=>F.row('Serie '+(i+1),`${s.load} kg × ${s.reps}`,`RIR ${s.rir}`)).join('')||'<p>Todavía no hay series registradas en este estado local.</p>');return;
  case 'advanced-set':K.sheet('Serie avanzada',`<p>Cadena editable dentro de la misma serie.</p><div class="chain"><button class="chain-link active"><span>Principal</span><strong>${P.load}</strong><span>× ${P.reps}</span></button><i class="chain-arrow"></i><button class="chain-link"><span>Drop 1</span><strong>${Math.round(P.load*.8)}</strong><span>× 8</span></button></div>${F.expert('<p>El registro de la cadena se revisa en los prototipos ya aprobados de la sesión en vivo.</p>')}`);return;
  case 'visual-mode':{const div=document.createElement('div');div.className='visual-expand';div.innerHTML=`<div class="sheet-top"><h3>Modo visual</h3>${F.btn('close-visual',K.icon('Close'),'icon-button','aria-label="Cerrar modo visual"')}</div><div class="visual-figure">${F.glyph('strength')}</div><p class="caption muted">Sentadilla trasera</p>`;document.getElementById('app').append(div);return;}
  case 'close-visual':document.querySelector('.visual-expand')?.remove();return;
  case 'cardio-next':if(P.cardioBlock<2)P.cardioBlock++;else{navigate('summary');return;}break;
  case 'chart-point':P.chartPoint=Math.max(0,Math.min(6,Number(val)));break;
  case 'recalc-lab':break;
  case 'rule-session':P.ruleSessions=Math.min(2,P.ruleSessions+1);break;
  case 'accept-load':if(P.ruleSessions<2||P.labTechnique!=='same')return;P.labLoad+=P.labIncrement;P.ruleSessions=0;K.toast('Incremento aceptado para el siguiente objetivo');break;
  case 'complex-update':break;
  case 'complex-round':P.complexDone=Math.min(P.complexRounds,P.complexDone+1);K.toast(P.complexDone===P.complexRounds?'Complex completado':'Ronda '+P.complexDone+' registrada');break;
  case 'skill-log':P.skillLogs.push({skill:P.skill,seconds:P.holdSeconds});K.toast('Isometría registrada');break;
  case 'medley-toggle':toggle(P.medleyDone,Number(val));break;
  case 'medley-save':if(P.medleyDone.length<3){K.toast('Quedan implementos por completar');return;}K.toast('Medley · 100 m · '+P.medleyTime+' s');return;
  case 'arm-save':P.armLogs.push({side:P.armSide,sec:P.armSeconds,angle:P.armAngle,load:P.armLoad,rpe:P.armRpe,grip:P.armGrip});K.toast('Sostén registrado por lado y ángulo');break;
  case 'amrap-plus':P.amrapReps++;break;case 'amrap-minus':P.amrapReps=Math.max(0,P.amrapReps-1);break;
  case 'emom-complete':toggle(P.emomDone,P.emomMinute);break;
  case 'fortime-rep':P.forTimeRemaining=Math.max(0,P.forTimeRemaining-1);P.forTimeCapped=false;break;
  case 'fortime-finish':P.forTimeCapped=P.forTimeRemaining>0;P.forTimeFinished=!P.forTimeCapped;break;
  case 'hyrox-station':P.hyroxStage=Number(val)*2+1;break;
  case 'hyrox-next':if(P.hyroxStage<15)P.hyroxStage++;else{K.toast('Recorrido completado · 8 km + 8 estaciones');return;}break;
  case 'round-pick':P.round=Number(val);P.roundSeconds=180;P.roundRunning=false;break;
  case 'round-clock':P.roundRunning=!P.roundRunning;break;
  case 'combo-hit':P.comboHit=Number(val);break;
  case 'combo-log':P.combos++;P.comboHit=(P.comboHit+1)%3;break;
  case 'position-pick':if(P.position!==val){P.position=val;P.transitions.push(val);}break;
  case 'rolling-save':P.rollingLogs.push({min:P.rollingMinutes,outcome:P.rollingOutcome,position:P.position});K.toast('Rolling guardado · '+P.rollingMinutes+' min');break;
  case 'mma-class':K.sheet('Clase fija','<p>El contenido y horario de esta clase se conservan. Puedes ajustar los apoyos de fuerza y cardio alrededor de ella.</p>'+F.link('week','Ver la semana'));return;
  case 'cycling-save':K.toast('Objetivo guardado · '+Math.round(P.ftp*P.ftpPercent/100)+' W');return;
  case 'trail-next':P.trailPoint=(P.trailPoint+1)%7;break;
  case 'rowing-update':break;
  case 'row-log':if(P.rowLogs.length>=4){K.toast('Los 4 intervalos ya están completos');return;}P.rowLogs.push({m:P.rowDistance,split:P.rowSplit,sec:Math.round(P.rowDistance/500*P.rowSplit)});break;
  case 'swim-minus':P.swimLengths=Math.max(0,P.swimLengths-1);break;case 'swim-plus':P.swimLengths=Math.min(64,P.swimLengths+1);break;
  case 'tri-next':if(P.triStage<4)P.triStage++;else{K.toast('Recorrido completado · transiciones incluidas');return;}break;
  case 'climb-attempt':P.climbAttempts.push(P.climbHold);break;case 'climb-send':P.climbHold=6;P.climbAttempts.push(6);K.toast('Encadene registrado');break;
  case 'test-add':if(!Number.isFinite(P.testValue)||P.testValue<=0)return;(P.testType==='jump'?P.jumpTests:P.sprintTests).push(P.testValue);break;
  case 'racket-point':points(el.dataset.sport,Number(val));break;
  case 'racket-undo':{const g=P[el.dataset.sport],last=g.history.pop();if(last){const history=g.history;P[el.dataset.sport]={...JSON.parse(last),history};}break;}
  case 'team-action':P.teamActions[val]++;break;
  case 'team-save':K.toast('Partido guardado · '+P.teamMinutes+' min individuales');return;
  case 'use-rpe':K.sheet('Tu esfuerzo','<label class="field">RPE de este bloque<input id="manual-rpe" type="range" min="1" max="10" value="5" aria-label="Esfuerzo percibido del bloque"></label>'+F.btn('save-manual-rpe','Guardar esfuerzo','pill'));return;
  case 'save-manual-rpe':{const v=document.getElementById('manual-rpe').value;document.querySelector('.sheet-shade')?.remove();K.toast('Esfuerzo guardado · RPE '+v);return;}
  case 'sensor-source':K.sheet('Fuente de pulsaciones',F.choice('sensor-band','Banda de pecho','Lectura actual · 138 lpm',P.sensorSource==='Banda de pecho')+F.choice('sensor-manual','Registro manual','Sin señal de pulsaciones',P.sensorSource==='Manual'));return;
  case 'sensor-band':P.sensorSource='Banda de pecho';P.sensorMode='fresh';break;
  case 'sensor-manual':P.sensorSource='Manual';P.sensorMode='lost';break;
  case 'huawei-file':navigate('import');return;
  case 'huawei-manual':K.sheet('Actividad manual',F.field('Tiempo (min)','manualMinutes',30,'number','min="1" max="1440"')+F.field('Distancia (km)','manualDistance',5,'number','min="0" step="0.01"')+F.btn('manual-save','Guardar actividad','pill'));return;
  case 'manual-save':document.querySelector('.sheet-shade')?.remove();K.toast('Actividad manual guardada');return;
  case 'import-skip':K.sheet('Duplicado omitido','<p>Se conserva una actividad: 4,00 km · 25:00.</p>'+F.link('training-energy','Ver origen del gasto'));return;
  case 'import-keep':K.sheet('Actividad aparte','<p>Se conservan ambas actividades. El resumen las distingue por origen para que puedas revisar el posible doble conteo.</p>'+F.link('training-energy','Revisar actividades relacionadas'));return;
  case 'energy-source':P.energyInclude=val;break;
  case 'energy-unlink':P.energyInclude='both';break;
  default:return;
 }
 save();render(true);
}
document.addEventListener('click',e=>{
 const reviewer=e.target.closest('[data-review]');if(reviewer){if(reviewer.dataset.review==='reset'){P=structuredClone(INITIAL);save();render();K.toast('Estados locales reiniciados');}else{P=structuredClone(INITIAL);save();navigate('wizard-sports');render();}return;}
 const nav=e.target.closest('.nav-tab');if(nav){e.preventDefault();const name=nav.querySelector('.nav-label').textContent;if(name==='Training'){navigate('profile');return;}if(name==='Nutrition'){navigate('training-energy');return;}if(name==='Body'){navigate('progress');return;}navigate('wizard-sports');return;}
 const day=e.target.closest('[data-day]');if(day){P.homeDay=Number(day.dataset.day);render(true);return;}
 const el=e.target.closest('[data-act]');if(el){e.preventDefault();action(el.dataset.act,el);}
});
document.addEventListener('keydown',e=>{if((e.key==='Enter'||e.key===' ')&&e.target.matches('g[role="button"],path[role="button"]')){e.preventDefault();action(e.target.dataset.act,e.target);}});
document.addEventListener('input',e=>{
 const key=e.target.dataset.field;if(!key)return;
 if(key==='slide-start'){const n=Number(e.target.value);const width=e.target.getBoundingClientRect().width;document.querySelector('.slide-knob').style.setProperty('--slide',n/100*(width-64)+'px');if(n>=95)navigate('running');return;}
 if(key==='effort'){P.rir=5-Number(e.target.value);P.effortTouched=true;document.getElementById('rir-label').textContent='RIR '+P.rir;document.querySelector('.effort-thumb').style.setProperty('--effort',(5-P.rir)*20);document.querySelector('[data-act="register-set"]').disabled=false;save();return;}
 const input=e.target;let value=input.type==='number'||input.type==='range'||NUMERIC.has(key)?Number(input.value):input.value;
 if(typeof value==='number'){
  if(!Number.isFinite(value))return;
  const lower=input.getAttribute('min'),upper=input.getAttribute('max');
  const min=lower!==null&&lower.trim()!==''&&Number.isFinite(Number(lower))?Number(lower):-Infinity;
  const max=upper!==null&&upper.trim()!==''&&Number.isFinite(Number(upper))?Number(upper):Infinity;
  value=Math.min(max,Math.max(min,value));
 }
 setValue(key,value);save();
 if(key==='minutes'){document.getElementById('minutes-output').textContent=value+' min';}
 if(input.type==='range'&&key!=='minutes'){render(true);}
});
document.addEventListener('change',e=>{if(e.target.closest('.sheet'))return;if(e.target.matches('[data-field]')&&!['note','strikingNote','climbNote'].includes(e.target.dataset.field))render(true);});
let dragged;
document.addEventListener('dragstart',e=>{const s=e.target.closest('[draggable][data-session-id]');if(s){dragged=s.dataset.sessionId;e.dataTransfer.setData('text/plain',dragged);}});
document.addEventListener('dragover',e=>{const d=e.target.closest('[data-drop-day]');if(d){e.preventDefault();d.classList.add('drag-over');}});
document.addEventListener('dragleave',e=>e.target.closest('[data-drop-day]')?.classList.remove('drag-over'));
document.addEventListener('drop',e=>{const d=e.target.closest('[data-drop-day]');if(d&&dragged){e.preventDefault();P.moving=dragged;moveTo(Number(d.dataset.dropDay));dragged=null;}});
setInterval(()=>{if(!P.roundRunning)return;if(P.roundSeconds>0)P.roundSeconds--;else P.roundRunning=false;if(route==='striking'){const el=document.getElementById('round-time');if(el)el.textContent=F.time(P.roundSeconds);document.querySelector('.round-clock')?.style.setProperty('--fill',P.roundSeconds/180*100);}save();},1000);
window.addEventListener('hashchange',()=>{P.roundRunning=false;render();});
render();
