/* ================= ASISTENTE · Métricas · Sugerencias · Plantillas · Reglas (punto 4 del usuario) ================= */
// Iconos que faltan (retícula 24, trazo 1,75).
Object.assign(P,{
 asUndo:'<path d="M4 9h10a5.5 5.5 0 010 11H8"/><path d="M8 5L4 9l4 4"/>',
 asLock:'<rect x="5" y="10.5" width="14" height="10" rx="3"/><path d="M8 10.5V8a4 4 0 018 0v2.5"/>',
 asArchive:'<rect x="3.5" y="4.5" width="17" height="4.5" rx="1.5"/><path d="M5 9v8a3 3 0 003 3h8a3 3 0 003-3V9M10 13h4"/>',
 asPulse:'<path d="M3 12h4l2-5 4 10 2-5h6"/>',
 asLayers:'<path d="M12 4l8.5 4.5L12 13 3.5 8.5z"/><path d="M3.5 12.5L12 17l8.5-4.5M3.5 16.5L12 21l8.5-4.5"/>',
 asWarn:'<path d="M12 4l9 15.5H3z"/><path d="M12 10v4.5M12 17.2v.01"/>',
 asMinus:'<path d="M6 12h12"/>',
 asSave:'<path d="M5 4.5h11l3 3V19a1.5 1.5 0 01-1.5 1.5h-12A1.5 1.5 0 014 19V6a1.5 1.5 0 011-1.5z"/><path d="M8 4.5v4h7v-4M8 20v-6h8v6"/>',
});
const fm=n=>{const r=Math.round(n*10)/10;return(Number.isInteger(r)?String(r):r.toFixed(1)).replace('.',',')};
const tr1=x=>Math.floor(x*10+1e-9)/10;
const mmss=s=>Math.floor(s/60)+':'+String(s%60).padStart(2,'0');

/* ---------- datos de ejemplo: «Legs II» (la sesión de tu captura h04). Pesos = aporte de cada ejercicio a cada músculo (1 = principal). ---------- */
const AS_EX=[
 {n:'Sentadilla búlgara',sets:4,rpe:8,m:{'Cuádriceps':1,'Glúteos':.5,'Aductores':.5,'Core':.3}},
 {n:'Peso muerto convencional',sets:3,rpe:8.5,m:{'Glúteos':1,'Isquiosurales':.5,'Erectores Espinales':.4,'Antebrazo':.4,'Core':.3}},
 {n:'Prensa de piernas',sets:3,rpe:7.5,m:{'Cuádriceps':1,'Glúteos':.5,'Aductores':.5}},
 {n:'Hip thrust',sets:4,rpe:8,m:{'Glúteos':1,'Isquiosurales':.25,'Aductores':.5,'Core':.3}},
 {n:'Curl femoral tumbado',sets:2,rpe:8.5,m:{'Isquiosurales':1}},
 {n:'Extensión de cuádriceps',sets:2,rpe:9,m:{'Cuádriceps':1}},
];
const AS_ALL=['Cuello','Trapecio','Deltoides','Pectorales','Bíceps','Tríceps','Antebrazo','Dorsales','Erectores Espinales','Core','Abdomen','Glúteos','Aductores','Cuádriceps','Isquiosurales','Pantorrillas','Romboides'];
const AS_WEEK={'Glúteos':22.5,'Cuádriceps':18,'Aductores':11,'Isquiosurales':13.5,'Core':9.6,'Erectores Espinales':3.6,'Antebrazo':3.6};
const asTier=r=>r>=9?'Muy alta':r>=8?'Alta':'Media';
function asMuscles(ex){
 const mp={};
 ex.forEach(e=>Object.entries(e.m).forEach(([k,w])=>{(mp[k]=mp[k]||{n:k,v:0,c:[],pr:[]});mp[k].v+=e.sets*w;mp[k].c.push({n:e.n,sets:e.sets,w,c:e.sets*w});if(w===1)mp[k].pr.push(e.rpe)}));
 return Object.values(mp).map(x=>({...x,v:tr1(x.v),tier:x.pr.length?asTier(x.pr.reduce((a,b)=>a+b,0)/x.pr.length):null})).sort((a,b)=>b.v-a.v);
}
const AS_MUS=asMuscles(AS_EX);
const AS_LIM={sess:10,week:24,rpe:9,exm:6,pat:4,rigid:true};
const AS_TIME=[['Preparación',6,'#5f5c57'],['Ejecución',21,'#f2eee6'],['Descansos',29,'#8e8980'],['Calentamiento',3,'#3a3936']];
const AS_TARGET=50;

/* ---------- plantillas del catálogo (metadatos reales de SessionTemplates.kt; el catálogo real tiene muchas más) ---------- */
const AS_T=[
 {id:'leg-quad',g:'Pierna',n:'Leg Day · Cuádriceps',sh:'Tren inferior énfasis cuádriceps',min:35,ex:6,sets:17,lv:1,mus:'Cuádriceps · Isquios · Glúteos · Aductores',ty:['Piernas'],sp:'Upper/Lower',cv:['relieve','carbon',3]},
 {id:'leg-hinge',g:'Pierna',n:'Leg Day · Isquios',sh:'Tren inferior énfasis isquiosurales',min:35,ex:6,sets:17,lv:1,mus:'Isquios · Glúteos · Aductores · Pantorrillas',ty:['Piernas'],sp:'Upper/Lower',cv:['relieve','musgo',8]},
 {id:'legs-full',g:'Pierna',n:'Legs Day · Completo',sh:'Tren inferior completo',min:36,ex:7,sets:18,lv:1,mus:'Cuádriceps · Isquios · Glúteos · Pantorrillas',ty:['Piernas'],sp:'PPL',cv:['perfil','carbon',5]},
 {id:'glute-isq',g:'Pierna',n:'Glúteo / Isquios',sh:'Tren inferior cadena posterior glúteo',min:35,ex:4,sets:16,lv:1,mus:'Glúteos · Isquiosurales',ty:['Piernas','Glúteos'],sp:'Glúteo',cv:['espiral','carbon',2]},
 {id:'glute-pump',g:'Pierna',n:'Glúteo Pump',sh:'Bombeo estético de glúteos',min:35,ex:4,sets:13,lv:1,mus:'Glúteos · Pantorrillas',ty:['Piernas','Glúteos'],sp:'Glúteo',cv:['ondas','grafito',6]},
 {id:'hf-pierna',g:'Pierna',n:'Pierna · Dosis compacta',sh:'Máquinas y poleas comunes',min:16,ex:4,sets:8,lv:1,mus:'Piernas',ty:['Piernas'],sp:'Dosis compacta',cv:['campo','grafito',9]},
 {id:'hf-posterior',g:'Pierna',n:'Posterior · Dosis compacta',sh:'Máquinas y poleas comunes',min:18,ex:4,sets:8,lv:1,mus:'Cadena posterior',ty:['Piernas','Glúteos'],sp:'Dosis compacta',cv:['campo','musgo',4]},
 {id:'push',g:'Torso',n:'Push Day · PPL',sh:'Empuje hipertrofia clásico',min:35,ex:5,sets:16,lv:1,mus:'Pecho · Hombros · Tríceps',ty:['Torso','Pecho','Hombros'],sp:'PPL',cv:['ondas','noche',7]},
 {id:'pull',g:'Torso',n:'Pull Day · PPL',sh:'Tracciones hipertrofia clásica',min:35,ex:6,sets:16,lv:1,mus:'Espalda · Bíceps · Romboides',ty:['Torso','Espalda'],sp:'PPL',cv:['campo','noche',11]},
 {id:'torso-a',g:'Torso',n:'Torso A · Upper/Lower',sh:'Upper balanceado pecho y espalda',min:36,ex:7,sets:18,lv:1,mus:'Pecho · Espalda · Hombros · Bíceps · Tríceps',ty:['Torso','Pecho','Espalda'],sp:'Upper/Lower',cv:['perfil','noche',12]},
 {id:'pecho-esp',g:'Torso',n:'Pecho / Espalda',sh:'Pecho y espalda antagonistas',min:36,ex:5,sets:14,lv:2,mus:'Pecho · Espalda',ty:['Torso','Pecho','Espalda'],sp:'Antagonistas',cv:['orbitas','noche',13]},
 {id:'torso-liv',g:'Torso',n:'Torso Liviano',sh:'Mantenimiento torso fatiga mínima',min:35,ex:5,sets:19,lv:0,mus:'Pecho · Espalda · Hombros · Brazos',ty:['Torso'],sp:'Upper/Lower',cv:['bruma','grafito',14]},
 {id:'hombro-brazo',g:'Brazos',n:'Hombro / Brazo',sh:'Hombros y brazos estética',min:35,ex:6,sets:20,lv:1,mus:'Hombros · Bíceps · Tríceps',ty:['Brazos','Hombros'],sp:'Antagonistas',cv:['espiral','grafito',15]},
 {id:'fb-base',g:'Full body',n:'Full Body · Base',sh:'Cuerpo completo base principiante',min:35,ex:6,sets:16,lv:0,mus:'Cuerpo completo',ty:['Full body'],sp:'Full body',cv:['orbitas','musgo',16]},
 {id:'minimal',g:'Full body',n:'Minimalista · Fuerza',sh:'Entrenamiento de fuerza minimalista',min:36,ex:4,sets:13,lv:1,mus:'Cuerpo completo',ty:['Full body'],sp:'Full body',cv:['relieve','grafito',17]},
 {id:'sbd',g:'Full body',n:'SBD · Powerlifting',sh:'SBD competitivo volumen moderado',min:41,ex:6,sets:14,lv:2,mus:'Sentadilla · Banca · Peso muerto',ty:['Full body'],sp:'Powerlifting',cv:['perfil','carbon',18]},
];
const nb=s=>s.replace(' · ',' · ');
const AS_LV=['Fácil','Medio','Exigente'];
const AS_PV={
 'leg-quad':{parts:[['Compuestos',[['Sentadilla con barra alta',3,6,8,180,{'Cuádriceps':1,'Glúteos':.5,'Aductores':.5}],['Peso muerto rumano con barra',2,8,8,150,{'Isquiosurales':1,'Glúteos':.5,'Erectores Espinales':.3}]]],['Aislamientos',[['Extensión de cuádriceps en máquina',2,12,8.5,90,{'Cuádriceps':1}],['Curl de isquios con sliders',2,12,8.5,90,{'Isquiosurales':1}],['Aducción sentado en máquina',4,12,8.5,75,{'Aductores':1}],['Elevación de talones en máquina',4,15,8.5,60,{'Pantorrillas':1}]]]],
  d:'Día de Pierna con énfasis en Cuádriceps: sentadilla, bisagra, aislamientos, aductores y gemelos en patrón A-B-A-B.'},
 'glute-pump':{parts:[['Activación y rango',[['Hip thrust con barra',3,12,8,120,{'Glúteos':1,'Isquiosurales':.25}],['Curl de isquios con sliders',4,12,8,90,{'Isquiosurales':1}]]],['Aislamiento estético',[['Patada de glúteo en polea',2,15,8.5,75,{'Glúteos':1}],['Elevación de talones en máquina',4,15,8.5,60,{'Pantorrillas':1}]]]],
  d:'Sesión metabólica y focalizada en bombeo de glúteos e isquiosurales, excelente para cierre semanal.'},
};
const AS_PRE=[['Alto volumen',{series:4,reps:12,rpe:7.5,it:'RPE',rn:75,rl:15,re:45,rr:90}],['Fuerza',{series:5,reps:5,rpe:8.5,it:'RPE',rn:180,rl:30,re:90,rr:180}],['Tiempo corto',{series:3,reps:10,rpe:2,it:'RIR',rn:60,rl:0,re:30,rr:75}],['Entreno equilibrado',{series:3,reps:8,rpe:8,it:'RPE',rn:150,rl:15,re:60,rr:120}]];

/* ---------- piezas ---------- */
const asCover=(t,w)=>`<div class="as-cv" ${w?`style="${w}"`:''}>${coverSvg(t.cv[0],t.cv[1],{seed:t.cv[2]})}</div>`;
const asLvl=l=>`<span class="as-lv"><i>${[0,1,2].map(i=>`<s class="${i<=l?'f':''}"></s>`).join('')}</i>${AS_LV[l]}</span>`;
function asBg(min=59){return `<div class="as-bg" aria-hidden="true"><h1>Legs II</h1><p>Enfoque DL</p><div class="r"><span>6 ejercicios · 18 series</span><span>≈ ${min} min</span></div>
 ${AS_EX.slice(0,3).map(e=>`<div class="x"><i></i><div><b>${e.n}</b><em>${e.sets} series</em></div></div>`).join('')}
 <div class="as-bar pl-glass">${[['train','Añadir'],['spark','Asistente'],['clock',min+' min']].map((x,i)=>`<div class="${i===1?'on':''}">${ic(x[0],22)}<span>${x[1]}</span></div>`).join('')}</div></div>`}
const AS_TABS=[['met','Métricas'],['sug','Sugerencias'],['pla','Plantillas'],['reg','Reglas']];
function asTabs(on,badge){return `<nav class="as-tabs" role="tablist" aria-label="Asistente">${AS_TABS.map(([k,l])=>`<button role="tab" data-act="tab" data-tab="${k}" aria-selected="${k===on}" class="${k===on?'on':''}">${l}${badge[k]===undefined?'':badge[k]==='dot'?'<i class="as-nd" aria-label="aviso"></i>':`<span class="as-nb">${badge[k]}</span>`}</button>`).join('')}</nav>`}
function asShell(o){
 const pv=o.pv||'lst',rs=o.rs||'val';
 return `<div class="as-root ${o.anim?'in':''}" data-mall="${o.mall?1:0}">${asBg(o.min||59)}<div class="as-scrim" data-act="scrim"></div>
 <section class="as-sheet ${o.h==='full'?'full':''}" data-tab="${o.tab}" data-pv="${pv}" data-rs="${rs}" role="dialog" aria-label="Asistente de sesión">
  <div class="as-grab" data-act="grab" role="button" aria-label="Cambiar la altura de la hoja"><i></i></div>
  <header class="as-head"><h2>Asistente de sesión</h2><span class="as-sub">${o.sub||'Legs II'}</span></header>
  ${asTabs(o.tab,o.badge||{})}
  <div class="as-body">${AS_TABS.map(([k])=>`<div class="as-pane ${k===o.tab?'on':''}" data-p="${k}" ${k==='pla'?`data-pv="${pv}"`:''} ${k==='reg'?`data-rs="${rs}"`:''}>${o.panes[k]}${k==='reg'?`<div class="as-foot reg"><button class="as-btn" data-act="reg-apply">${ic('check',18)}Aplicar a la sesión</button><div class="as-hint" style="text-align:center">Aplica a los 6 ejercicios y sustituye lo ya escrito</div></div>`:''}${k==='pla'?`<div class="as-foot pla"><button class="as-btn" data-act="tpl-apply">${ic('layers',18)}Aplicar plantilla</button></div>`:''}</div>`).join('')}
  </div></section>
 <button class="as-btn sm as-reopen" data-act="reopen">${ic('spark',16)}Abrir asistente</button></div>`}

/* ---------- MÉTRICAS ---------- */
function asMuscleRow(m,o){
 const lim=o.lim,top=Math.max(AS_MUS[0].v,lim||0)*1.06,over=lim&&m.v>lim;
 const w=v=>(v/top*100).toFixed(1)+'%';
 const bar=over?`<i style="width:${w(lim)}"></i><i class="o" style="left:${w(lim)};width:${((m.v-lim)/top*100).toFixed(1)}%"></i>`:`<i style="width:${w(m.v)}"></i>`;
 const wk=AS_WEEK[m.n],wl=AS_LIM.week;
 return `<div class="as-mrow ${o.open===m.n?'open':''}" role="button" tabindex="0" aria-expanded="${o.open===m.n}" data-act="mrow">
  <div class="as-mtop"><span class="n">${m.n}</span><span class="v ${over?'over':''}">${fm(m.v)}</span><span class="c">${ic('down',16)}</span></div>
  <div class="as-mbar">${bar}${lim?`<u style="left:${w(lim)}"></u>`:''}</div>
  <div class="as-mdet"><div class="as-eye" style="margin-bottom:8px">Qué aporta a ${m.n}</div>
   <div class="as-cl">${m.c.map(c=>`<span class="a">${c.n}</span><span class="b">${c.sets} × ${fm(c.w)}</span><span class="t">${fm(c.c)}</span>`).join('')}<span class="tot"></span><span class="a" style="color:var(--ink);font-weight:600">Total</span><span></span><span class="t">${fm(m.v)}</span></div>
   <div class="as-mchips">${m.tier?`<span>Intensidad ${m.tier.toLowerCase()}</span>`:''}${wk?`<span class="${wk>wl?'w':''}">Semana ${fm(wk)}${o.lim?` de ${wl}`:''} series</span>`:''}${over?`<span class="w">Supera tu límite de ${lim}</span>`:''}</div></div></div>`}
function paneMet(o={}){
 if(o.empty)return `<div class="as-sc">
  <div class="as-fig"><div><b>0</b><span>Ejercicios</span></div><div><b>0</b><span>Series</span></div><div><b>—</b><span>Duración</span></div></div>
  <section class="as-mod"><div class="as-mh"><h4>Volumen por músculo</h4></div>
   <div class="as-empty" style="padding-top:10px"><span class="g" style="width:48px;height:48px">${ic('train',24)}</span><h3>Aún no hay nada que medir</h3><p>Con ejercicios en la sesión verás aquí las series de cada músculo y el reparto del tiempo.</p>
    <button class="as-btn sm" style="margin-top:6px" data-act="go" data-go="pla">${ic('layers',16)}Empezar con una plantilla</button></div></section></div>`;
 const car=o.cardio,tot=car?74:59,ejN=car?7:6,tb=AS_TIME.map(t=>t[0]==='Ejecución'&&car?[t[0],36,t[2]]:t);
 const scale=Math.max(tot,AS_TARGET)*1.06,over=tot-AS_TARGET;
 const lim=o.noLimit?null:AS_LIM.sess;
 const seg=tb.map(t=>`<i style="width:${(t[1]/scale*100).toFixed(2)}%;background:${t[2]}" title="${t[0]}"></i>`).join('');
 const leg=tb.map(t=>`<div><i style="background:${t[2]}"></i><div class="tx"><span>${t[0]}</span><b>${t[1]} min</b></div></div>`).join('');
 const shown=AS_MUS;
 const un=AS_ALL.filter(x=>!AS_MUS.some(m=>m.n===x));
 return `<div class="as-sc">
  <div class="as-fig"><div><b>${ejN}</b><span>Ejercicios</span>${car?'<em>1 de cardio</em>':''}</div><div><b>18</b><span>Series</span>${car?'<em>solo fuerza</em>':''}</div><div><b>${tot}<small>min</small></b><span>Duración</span><em style="color:var(--ene);white-space:nowrap">${over>0?'+'+over+' sobre límite':'dentro del límite'}</em></div></div>
  ${car?`<section class="as-mod"><div class="as-card-cardio"><span class="g">${ic('asPulse',22)}</span><div class="tx"><strong>Cinta de correr</strong><span>Cardio · al final de la sesión</span></div><b>15 min</b></div><div class="as-cap" style="margin-top:10px">El cardio suma tiempo pero no series de músculo.</div></section>`:''}
  <section class="as-mod"><div class="as-mh"><h4>Volumen por músculo</h4></div>
   <div class="as-seg" style="margin-bottom:12px" role="tablist"><button class="${o.all?'':'on'}" data-act="mall" data-m="0">Trabajados · ${AS_MUS.length}</button><button class="${o.all?'on':''}" data-act="mall" data-m="1">Todos · ${AS_ALL.length}</button></div>
   <div class="as-legend"><span>Series efectivas</span>${lim?`<span><i class="as-lt"></i>Tu límite por sesión · ${lim}</span>`:''}</div>
   ${shown.map(m=>asMuscleRow(m,{lim,open:o.open})).join('')}
   <div class="as-un"><div class="as-eye" style="margin-bottom:10px">Sin trabajar en esta sesión · ${un.length}</div><div class="as-flow">${un.map(x=>`<span>${x}</span>`).join('')}</div></div>
  </section>
  <section class="as-mod"><div class="as-mh"><h4>Tiempo</h4><span class="as-st ${over>0?'over':'ok'}">${over>0?`+${over} min sobre tu límite`:'Dentro de tu límite'}</span></div>
   <div class="as-tbw" style="position:relative"><div class="as-tb" style="width:${(tot/scale*100).toFixed(1)}%">${seg}</div><span class="as-tlim" style="left:${(AS_TARGET/scale*100).toFixed(1)}%" title="Tu límite: ${AS_TARGET} min"></span></div>
   <div class="as-tleg">${leg}</div>
   <div class="as-cap" style="margin-top:12px;display:flex;justify-content:space-between;gap:10px"><span><i class="as-nd" style="background:var(--ene);display:inline-block;margin-right:6px"></i>Tu límite · ${AS_TARGET} min</span><button class="as-link" style="min-height:24px" data-act="go" data-go="reg" data-sub="tie">Cambiar ${ic('fwd',14)}</button></div></section></div>`}

/* ---------- SUGERENCIAS ---------- */
function asFx(base,lim,saved,txt){const after=base-saved,sc=Math.max(base,lim)*1.06;
 return `<div class="as-fx ${after<=lim?'ok':''}"><div class="r"><span>Duración</span><b>${base} → <span data-r="after">${after}</span> min</b></div><div class="bar"><i data-r="bar" style="width:${(after/sc*100).toFixed(1)}%"></i><u style="left:${(lim/sc*100).toFixed(1)}%"></u></div><div class="t" data-r="txt">${txt}</div></div>`}
function asDet(d,on){return `<div class="as-di ${on?'on':''}" role="checkbox" aria-checked="${on}" tabindex="0" data-act="chk" data-sec="${d.sec||0}" data-sets="${d.sets||0}"><span class="as-ck">${ic('check',15)}</span><span class="tx">${d.l}</span><span class="as-ef">${d.sec?`<b>−${Math.round(d.sec/60)} min</b>`:''}${d.sets?`<em>−${d.sets} serie</em>`:''}</span></div>`}
function asSugCard(c){
 return `<div class="as-sgw" data-sg="${c.id}"><article class="as-sg" data-base="${c.base||0}" data-lim="${c.lim||0}" data-kind="${c.id}">
  <span class="as-sk">${ic('asLock',13)}Límite que elegiste</span><h3>${c.t}</h3><p class="as-why">${c.why}</p>
  ${c.fx}
  <div class="as-dl">${c.det.map((d,i)=>asDet(d,d.on)).join('')}</div>
  <div class="as-sa"><button class="as-btn" data-act="sg-apply">Aplicar selección <span data-r="n" style="opacity:.6">(${c.det.filter(d=>d.on).length})</span></button><button class="as-txa" data-act="sg-dismiss">Descartar</button></div></article>
  <div class="as-done ap"><span class="m">${ic('check',18)}</span><div class="tx"><strong>Aplicado</strong><span data-r="aps">—</span></div><button class="as-ud" data-act="sg-undo">${ic('asUndo',16)}Deshacer</button></div>
  <div class="as-done ds"><span class="m">${ic('close',16)}</span><div class="tx"><strong>Descartada</strong><span>No volverá a salir mientras no cambie la sesión.</span></div><button class="as-ud" data-act="sg-undo">${ic('asUndo',16)}Deshacer</button></div></div>`}
function paneSug(v){
 const limits={id:'user_limits',t:'Dentro de tus límites',why:'Pusiste límites rígidos y esta sesión los supera: Glúteos supera tu límite de series efectivas por sesión.',base:0,lim:0,
  fx:`<div class="as-fx"><div class="r"><span>Glúteos <span style="color:var(--faint)">· límite ${AS_LIM.sess}</span></span><b>10,5 → <span data-r="g">10</span></b></div><div class="bar"><i data-r="gbar" style="width:${(10.5/11.2*100).toFixed(1)}%"></i><u style="left:${(10/11.2*100).toFixed(1)}%"></u></div><div class="t" data-r="gtxt">Quitando 1 serie, Glúteos queda en 10: dentro de tu límite.</div></div>`,
  det:[{l:'Quitar 1 serie de Prensa de piernas',sets:1,sec:120,on:true}]};
 const time={id:'time_overage',t:'Ajustar al tiempo que fijaste',why:'La estimación (59 min) supera tus 50 min en 9 min.',base:59,lim:50,
  fx:asFx(59,50,2,'Con las opciones marcadas ahorras 2 min; la sesión seguiría 7 min por encima del objetivo.'),
  det:[{l:'Bajar 15 s de descanso entre series en 2 accesorios',sec:120,on:true},{l:'Unir Curl femoral tumbado con Extensión de cuádriceps en superserie',sec:180,on:false},{l:'Quitar 1 serie de Extensión de cuádriceps',sets:1,sec:120,on:false}]};
 if(v==='load')return `<div class="as-sc"><div class="as-sk-l" aria-busy="true" aria-label="Calculando sugerencias"><i style="width:34%"></i><i style="width:78%;height:16px"></i><i style="width:92%"></i><i style="width:60%"></i><i style="width:100%;height:44px;border-radius:14px;margin-top:6px"></i></div><div class="as-sk-l"><i style="width:40%"></i><i style="width:70%;height:16px"></i><i style="width:88%"></i></div><div class="as-cap" style="text-align:center">Comparando la sesión con tu tiempo y tus límites…</div></div>`;
 if(v==='ok')return `<div class="as-sc"><div class="as-empty" style="padding-top:18px"><span class="g" style="color:var(--done);box-shadow:inset 0 0 0 1.5px #43d18c55">${ic('check',28)}</span><h3>Sin sugerencias para esta sesión</h3><p>Si algo choca con el tiempo o con los límites que elegiste, aparecerá aquí.</p></div>
  <section class="as-mod"><div class="as-mh"><h4>Lo que el asistente revisa</h4></div><div class="as-rv">
   <div class="rw"><div class="tx"><strong>Tu tiempo</strong><span class="s">47 min estimados de 50 · dentro</span></div><button class="go" data-act="go" data-go="reg" data-sub="tie">Cambiar${ic('fwd',14)}</button></div>
   <div class="rw"><div class="tx"><strong>Tus límites rígidos</strong><span class="s">Ninguno activado</span></div><button class="go" data-act="go" data-go="reg" data-sub="lim">Cambiar${ic('fwd',14)}</button></div></div></section></div>`;
 if(v==='note')return `<div class="as-sc"><div class="as-note"><span class="g">${ic('info',18)}</span><div><span class="as-sk">Información</span><p>La estimación (51 min) supera tus 50 min en 1 min. Es una diferencia pequeña: no hay ajuste que proponer.</p></div></div>
  <div class="as-cap" style="padding:0 4px">Solo informa: no hay nada que aplicar. El asistente muestra como mucho tres avisos a la vez.</div></div>`;
 return `<div class="as-sc"><div class="as-cnt">2 avisos · como mucho tres a la vez</div>${asSugCard(limits)}${asSugCard(time)}</div>`}

/* ---------- PLANTILLAS ---------- */
function asTplCard(t){return `<div class="as-tc" role="button" tabindex="0" data-act="tpl-open" data-id="${t.id}">${asCover(t)}<div class="tx"><strong>${nb(t.n)}</strong><div class="sh">${t.sh}</div><div class="as-tm"><b>${t.min} min</b><span>${t.ex} ej.</span><span>${t.sets} series</span>${asLvl(t.lv)}</div><div class="as-fo">${t.mus}</div></div><span class="as-tcc">${ic('fwd',18)}</span></div>`}
function asTplList(S){
 let L=AS_T.filter(t=>{
  if(S.q){const q=S.q.toLowerCase();if(!(t.n+' '+t.sh+' '+t.mus).toLowerCase().includes(q))return false}
  if(S.ty&&!t.ty.includes(S.ty))return false;
  if(S.lv!=null&&t.lv!==S.lv)return false;
  if(S.du&&!(S.du==='c'?t.min<=45:S.du==='m'?t.min>=46&&t.min<=75:t.min>=76))return false;
  return true});
 if(!L.length)return `<div class="as-empty" style="padding-top:24px"><span class="g">${ic('search',24)}</span><h3>Sin resultados</h3><p>${S.q?`Ninguna sesión coincide con «${S.q}» y los filtros activos.`:'Ninguna sesión coincide con los filtros activos.'}</p><div class="as-cap" style="margin-top:-2px">Prueba a limpiar filtros o cambiar el grupo.</div><button class="as-btn sm as-gh" style="margin-top:6px" data-act="tpl-clear">Limpiar filtros</button></div>`;
 const by={};
 const key=t=>S.gr==='nivel'?AS_LV[t.lv]:S.gr==='dur'?(t.min<=45?'Corta (≤45 min)':'Media (46–75 min)'):S.gr==='split'?t.sp:t.g;
 L.forEach(t=>(by[key(t)]=by[key(t)]||[]).push(t));
 return Object.entries(by).map(([k,a])=>`<div class="as-sec ${S.shut&&S.shut[k]?'shut':''}" data-k="${k}"><div class="as-sh" role="button" tabindex="0" data-act="sec"><h5>${k}</h5><span class="n">${a.length}</span><span class="ln"></span><span class="c">${ic('down',18)}</span></div><div class="as-sl">${a.map(asTplCard).join('')}</div></div>`).join('')}
function asFilterPanel(S){return `<div class="as-fp ${S.fp?'open':''}">
  <div><div class="lb"><span>Nivel</span></div><div class="as-seg"><button class="${S.lv==null?'on':''}" data-act="f-lv" data-v="">Todos</button>${AS_LV.map((l,i)=>`<button class="${S.lv===i?'on':''}" data-act="f-lv" data-v="${i}">${l}</button>`).join('')}</div></div>
  <div><div class="lb"><span>Duración</span></div><div class="as-seg"><button class="${!S.du?'on':''}" data-act="f-du" data-v="">Todas</button><button class="${S.du==='c'?'on':''}" data-act="f-du" data-v="c">Corta</button><button class="${S.du==='m'?'on':''}" data-act="f-du" data-v="m">Media</button><button class="${S.du==='l'?'on':''}" data-act="f-du" data-v="l">Larga</button></div><div class="as-cap" style="margin-top:6px">Corta ≤45 min · Media 46–75 · Larga ≥76</div></div>
  <div><div class="lb"><span>Agrupar</span></div><div class="as-seg"><button class="${!S.gr||S.gr==='grupo'?'on':''}" data-act="f-gr" data-v="grupo">Grupo</button><button class="${S.gr==='split'?'on':''}" data-act="f-gr" data-v="split">Rutina</button><button class="${S.gr==='nivel'?'on':''}" data-act="f-gr" data-v="nivel">Nivel</button><button class="${S.gr==='dur'?'on':''}" data-act="f-gr" data-v="dur">Duración</button></div></div></div>`}
const AS_TYPES=['Piernas','Torso','Brazos','Full body','Pecho','Espalda','Hombros','Glúteos'];
function asActive(S){if(S.fp)return '';const a=[];if(S.lv!=null)a.push(['f-lvx','Nivel: '+AS_LV[S.lv]]);if(S.du)a.push(['f-dux','Duración: '+{c:'Corta',m:'Media',l:'Larga'}[S.du]]);return a.map(x=>`<button class="as-chip on" data-act="${x[0]}">${x[1]}${ic('close',14)}</button>`).join('')}
function asNActive(S){return (S.lv!=null?1:0)+(S.du?1:0)}
function asPlaLst(S){const n=asNActive(S);return `<div class="as-sbox"><label class="as-in">${ic('search',18)}<input type="search" data-in="q" placeholder="Buscar plantilla…" value="${S.q||''}" aria-label="Buscar plantilla"></label><button class="as-fb ${S.fp||n?'on':''} ${n?'has':''}" data-act="f-open" aria-label="Filtros de sesión">${ic('filter',20)}<b>${n}</b></button></div>
  <div class="as-chips" role="group" aria-label="Tipo de sesión">${AS_TYPES.map(x=>`<button class="as-chip ${S.ty===x?'on':''}" data-act="f-ty" data-v="${x}">${x}</button>`).join('')}</div>
  ${asFilterPanel(S)}
  <div class="as-flow" data-r="act">${asActive(S)}</div>
  <div class="as-top"><span class="as-res" data-r="res">${S.q||S.ty||S.lv!=null||S.du?(asTplCount(S)===0?'':asTplCount(S)+' resultado'+(asTplCount(S)===1?'':'s')):AS_T.length+' plantillas'}</span><div style="display:flex;gap:4px"><button class="as-link" data-act="mine-open">${ic('book',16)}Mis plantillas · 3</button></div></div>
  <div class="as-tl" data-r="list">${asTplList(S)}</div>`}
function asTplCount(S){return AS_T.filter(t=>(!S.q||(t.n+' '+t.sh+' '+t.mus).toLowerCase().includes(S.q.toLowerCase()))&&(!S.ty||t.ty.includes(S.ty))&&(S.lv==null||t.lv===S.lv)&&(!S.du||(S.du==='c'?t.min<=45:S.du==='m'?t.min>=46&&t.min<=75:t.min>=76))).length}
function asPlaDet(id){
 const t=AS_T.find(x=>x.id===id)||AS_T[0],pv=AS_PV[t.id];if(!pv)return '';
 const mp={};pv.parts.forEach(p=>p[1].forEach(e=>Object.entries(e[5]).forEach(([k,w])=>mp[k]=(mp[k]||0)+e[1]*w)));
 const vol=Object.entries(mp).map(([k,v])=>[k,tr1(v)]).sort((a,b)=>b[1]-a[1]),mx=vol[0][1]*1.06;
 let i=0;
 return `<div class="as-dh"><button class="as-ib" data-act="tpl-back" aria-label="Volver al catálogo">${ic('back',22)}</button><span class="t">Plantillas</span></div>
 <div class="as-hero">${asCover(t)}<div class="tx"><h3>${nb(t.n)}</h3><p>${pv.d}</p></div></div>
 <div class="as-pm"><div><b>${t.min}<small>min</small></b><span>Duración</span></div><div><b>${t.ex}</b><span>Ejerc.</span></div><div><b>${t.sets}</b><span>Series</span></div><div><b style="font:700 calc(15px*var(--fs))/1.25 Inter">${AS_LV[t.lv]}</b><span>Nivel</span></div></div>
 <section class="as-mod"><div class="as-mh"><h4>Ejercicios</h4></div>${pv.parts.map(p=>`<div class="as-pt">${p[0]}</div>${p[1].map(e=>{i++;return `<div class="as-pe"><span class="ix">${i}</span><div class="tx"><strong>${e[0]}</strong></div><div class="d">${e[1]} × ${e[2]}<small>RPE ${fm(e[3])} · ${mmss(e[4])}</small></div></div>`}).join('')}`).join('')}</section>
 <section class="as-mod"><div class="as-mh"><h4>Volumen estimado</h4><span class="as-cap">series efectivas</span></div><div class="as-vb">${vol.map(([k,v])=>`<div class="as-vr"><span class="n">${k}</span><span class="bb"><i style="width:${(v/mx*100).toFixed(1)}%"></i></span><span class="v">${fm(v)}</span></div>`).join('')}</div></section>`}
function asDecision(t){
 const cur={ex:6,sets:18,min:59};const rep={ex:t.ex,sets:t.sets,min:t.min},add={ex:cur.ex+t.ex,sets:cur.sets+t.sets,min:cur.min+t.min};
 const over=add.min-AS_TARGET;
 return `<div class="as-dec" data-r="dec"><div><h3>Aplicar «${nb(t.n)}»</h3><p class="as-q">La sesión ya tiene ejercicios. ¿Qué quieres hacer con la plantilla?</p>
  <button class="as-op on" data-act="dec-mode" data-m="r"><span class="rd"></span><span class="tx"><strong>Reemplazar<span class="as-sg-t">Sugerido</span></strong><span class="d">Sustituye ejercicios, grupos y calentamiento. Conserva el nombre y los datos de la sesión.</span><span class="r"><span><b>${rep.ex}</b> ejercicios</span><span><b>${rep.sets}</b> series</span><span><b>≈${rep.min}</b> min</span></span></span></button>
  <button class="as-op" data-act="dec-mode" data-m="a"><span class="rd"></span><span class="tx"><strong>Añadir al final</strong><span class="d">Suma los grupos y ejercicios de la plantilla a lo que ya tienes.</span><span class="r"><span><b>${add.ex}</b> ejercicios</span><span><b>${add.sets}</b> series</span><span><b>≈${add.min}</b> min</span>${over>0?`<span class="w">+${over} min sobre tu límite</span>`:''}</span></span></button>
  <button class="as-btn" style="margin-top:6px" data-act="dec-go">Aplicar</button><button class="as-txa" data-act="dec-x">Cancelar</button></div></div>`}
function asApplying(t,done,open){
 return `<div class="as-ap ${open?'open':''} ${done?'done':''}" data-r="ap" data-act="ap-skip"><div class="as-rg"><svg viewBox="0 0 84 84"><circle cx="42" cy="42" r="36" fill="none" stroke="#ffffff14" stroke-width="5"/><circle class="sp" cx="42" cy="42" r="36" fill="none" stroke="#f2eee6" stroke-width="5" stroke-linecap="round" stroke-dasharray="60 170"/></svg><span class="ok">${ic('check',40)}</span></div>
  <div><h3 class="t1">Aplicando «${t.n}»…</h3><h3 class="t0">Plantilla aplicada</h3></div><p class="t1">Cambiando ${AS_EX.length} ejercicios por los de la plantilla.</p><p class="t0">Reemplazaste 6 ejercicios por ${t.ex}: ${t.sets} series, ≈${t.min} min.</p>
  <div class="after"><button class="as-btn" data-act="ap-close">Ver mi sesión</button><button class="as-txa" data-act="ap-undo">${ic('asUndo',16)}&nbsp;Deshacer</button></div></div>`}
function asMine(o={}){
 const rows=[['Pierna pesada martes','Disponible · 6 ejercicios',0],['Torso rápido 30','Disponible · 5 ejercicios',0],['Brazos viejo','Archivada',1]];
 return `<div class="as-dh"><button class="as-ib" data-act="mine-back" aria-label="Volver al catálogo">${ic('back',22)}</button><span class="t">Mis plantillas</span><button class="as-link" data-act="save-open" style="margin-right:2px">${ic('asSave',16)}Guardar sesión</button></div>
 ${o.corrupt?`<div class="as-qw">${ic('asWarn',18)}<span>Hay 1 plantilla ilegible en cuarentena. No se aplicará hasta reparar el archivo.</span></div>`:''}
 ${rows.map((r,i)=>`<div class="as-mr ${r[2]?'arch':''}" data-i="${i}"><div class="tx"><strong>${r[0]}</strong><span class="s">${r[1]}</span></div>${r[2]?`<button class="as-btn sm as-gh" data-act="mine-restore">Restaurar</button>`:''}<button class="as-ib" data-act="mine-menu" data-i="${i}" aria-label="Más acciones de ${r[0]}">${ic('more',22)}</button></div>`).join('')}
 <div class="as-menu ${o.menu?'open':''}" data-r="menu" style="top:${o.menu?'212px':'0'}"><button data-act="mine-x">${ic('edit',18)}Editar</button><button data-act="mine-x">${ic('asArchive',18)}Archivar</button><hr><button class="dg" data-act="mine-del">${ic('trash',18)}Borrar</button></div>
 <div class="as-hint">Archivar la saca del catálogo sin borrarla. Borrar es permanente.</div>`}
function asSave(o={}){
 const st=o.state||'idle';
 return `<div class="as-dh"><button class="as-ib" data-act="mine-open" aria-label="Volver">${ic('back',22)}</button><span class="t">Guardar sesión como plantilla</span></div>
 <div class="as-hero" style="gap:12px"><div class="tx"><p>Guarda «Legs II» tal como está: 6 ejercicios, 18 series, ≈59 min. Podrás aplicarla en cualquier sesión.</p></div></div>
 <div class="as-fl"><label>Nombre</label><div class="as-fi ${o.name?'':'ph'} ${st==='err'?'err':''}">${o.name||'Nombre de la plantilla'}</div>${st==='err'?`<div class="as-er">Escribe un nombre para la plantilla.</div>`:''}</div>
 <div class="as-fl"><label>Descripción</label><div class="as-fi ph" style="min-height:84px">Opcional</div></div>
 <button class="as-btn ${st==='sav'?'off':''}" data-act="save-go">${st==='sav'?'Guardando…':'Guardar'}</button><button class="as-txa" data-act="mine-open">Cancelar</button>`}
function panePla(o={}){
 const S={q:o.q||'',ty:o.ty||null,lv:o.lv==null?null:o.lv,du:o.du||'',gr:o.gr||'grupo',fp:!!o.fp,shut:o.shut||{}};
 const pv=o.pv||'lst';
 return `<div class="as-sc" data-S='${JSON.stringify(S)}'>
  <div class="as-pv lst">${asPlaLst(S)}</div>
  <div class="as-pv det" data-r="det">${o.id?asPlaDet(o.id):''}</div>
  <div class="as-pv mine" data-r="mine">${pv==='mine'?asMine(o):pv==='sav'?asSave(o):''}</div></div>
  ${o.id?asDecision(AS_T.find(t=>t.id===o.id)):''}
  ${o.ap?asApplying(AS_T.find(t=>t.id===o.id),o.ap==='done',true):''}`}

/* ---------- REGLAS ---------- */
function asRow(label,sub,right,cls=''){return `<div class="as-rr"><div class="tx"><strong>${label}</strong>${sub?`<span class="s ${cls}">${sub}</span>`:''}</div>${right}</div>`}
const asStp=(k,v,fmtv)=>`<div class="as-stp"><button data-act="step" data-k="${k}" data-d="-1" aria-label="Menos">${ic('asMinus',18)}</button><b data-v="${k}">${fmtv}</b><button data-act="step" data-k="${k}" data-d="1" aria-label="Más">${ic('plus',18)}</button></div>`;
const asPk=(k,s)=>`<button class="as-pk" data-act="rest" aria-label="Cambiar descanso"><span data-v="${k}">${mmss(s)}</span>${ic('fwd',14)}</button>`;
function asLimStatus(L){
 const topM=AS_MUS[0],maxRpe=Math.max(...AS_EX.map(e=>e.rpe)),nRpe=AS_EX.filter(e=>L.rpe!=null&&e.rpe>L.rpe).length;
 const st={};
 st.rpe=L.rpe==null?['','']:nRpe?[`${nRpe} ${nRpe===1?'ejercicio':'ejercicios'} por encima`,'bad']:[`El más alto: ${fm(maxRpe)} · dentro`,'okk'];
 st.exm=L.exm==null?['','']:['El máximo es 3 · dentro','okk'];
 st.sess=L.sess==null?['','']:topM.v>L.sess?[`${topM.n} tiene ${fm(topM.v)}`,'bad']:[`El máximo: ${topM.n} ${fm(topM.v)} · dentro`,'okk'];
 st.week=L.week==null?['','']:AS_WEEK['Glúteos']>L.week?[`Glúteos lleva ${fm(AS_WEEK['Glúteos'])}`,'bad']:[`Glúteos lleva ${fm(AS_WEEK['Glúteos'])} · dentro`,'okk'];
 st.pat=L.pat==null?['','']:['Empuje: 4 · dentro','okk'];
 return st}
function paneRegVal(sc,R){
 const rl=sc==='ci'?'':'';
 const pk=R||{series:3,reps:10,rpe:8,it:'RPE',rn:90,rl:0,re:60,rr:120};
 const chip=(t,on,a,v)=>`<button class="as-chip ${on?'on':''}" data-act="${a}" data-v="${v}">${t}</button>`;
 const itSeg=(k,it)=>`<div class="as-seg" data-it="${k}">${['RPE','RIR','Fallo'].map(x=>`<button class="${it===x?'on':''}" data-act="it" data-k="${k}" data-v="${x}">${x}</button>`).join('')}</div>`;
 return `<div class="as-rp val" data-sc="${sc}">
  <section class="as-mod"><div class="as-mh"><h4>Alcance de las reglas</h4></div>
   <div class="as-seg" role="tablist" aria-label="Alcance de las reglas"><button class="${sc==='all'?'on':''}" data-act="scope" data-v="all">Sesión</button><button class="${sc==='part'?'on':''}" data-act="scope" data-v="part">Grupo</button><button class="${sc==='ci'?'on':''}" data-act="scope" data-v="ci">Comp. y aisl.</button></div>
   <div class="as-pf m-part" style="margin-top:10px">${['Compuestos','Aislamientos'].map((p,i)=>`<button class="as-chip ${i?'':'on'}" data-act="part">${p}</button>`).join('')}</div>
   <div class="as-cap" style="margin-top:10px"><span class="m-main">${sc==='part'?'Las reglas de este grupo se aplican solo a él.':'Series, intensidad y descansos por defecto para toda la sesión.'}</span><span class="m-ci">Una regla para los compuestos y otra para los aislamientos.</span></div></section>
  <section class="as-mod m-main"><div class="as-mh"><h4>Valores de serie</h4></div>${itSeg('it',pk.it)}
   <div style="margin-top:6px">${asRow('Series','',asStp('series',pk.series,pk.series))}${asRow('Repeticiones','',asStp('reps',pk.reps,pk.reps))}<div class="as-rr" data-r="rpe-row" style="${pk.it==='Fallo'?'display:none':''}"><div class="tx"><strong data-r="rpe-l">${pk.it==='RIR'?'RIR':'RPE'}</strong></div>${asStp('rpe',pk.rpe,fm(pk.rpe))}</div></div></section>
  <section class="as-mod m-main"><div class="as-mh"><h4>Descansos</h4></div>
   ${asRow('Entre series','Descanso de series',asPk('rn',pk.rn))}${asRow('Entre lados','Ejercicios unilaterales',asPk('rl',pk.rl))}${asRow('Entre ejercicios','Dentro de una superserie',asPk('re',pk.re))}${asRow('Entre rondas','Al cerrar una ronda de superserie',asPk('rr',pk.rr))}</section>
  <section class="as-mod m-ci"><div class="as-mh"><h4>Compuestos</h4></div>${itSeg('cIt','RPE')}<div style="margin-top:6px">${asRow('Descanso','',asPk('cRest',180))}${asRow('Repeticiones','',asStp('cReps',6,6))}${asRow('RPE','',asStp('cRpe',8,'8'))}</div></section>
  <section class="as-mod m-ci"><div class="as-mh"><h4>Aislamientos</h4></div>${itSeg('iIt','RIR')}<div style="margin-top:6px">${asRow('Descanso','',asPk('iRest',105))}${asRow('Repeticiones','',asStp('iReps',8,8))}${asRow('RIR','',asStp('iRpe',1,'1'))}</div></section>
  <section class="as-mod"><div class="as-fd"><div class="tx"><strong>Aplicar a nuevos elementos</strong><span class="s">Ejercicios, series, lados y superseries nuevas heredan estos valores.</span></div><span class="as-sw on" role="switch" aria-checked="true" tabindex="0" data-act="sw"></span></div></section>
  <section class="as-mod"><div class="as-mh"><h4>Plantillas de reglas</h4></div><div class="as-flow">${AS_PRE.map((p,i)=>`<button class="as-chip" data-act="preset" data-i="${i}">${p[0]}</button>`).join('')}<button class="as-chip as-gh" data-act="rest">${ic('plus',14)}Guardar actuales</button></div><div class="as-cap" style="margin-top:10px">Toca una para precargar los valores. Las tuyas se pueden renombrar o eliminar.</div></section>
  <section class="as-mod as-fold" data-fold><div class="as-fd" data-act="fold" role="button" tabindex="0"><div class="tx"><strong>Modificar intensidad</strong><span class="s">Cambia la intensidad de toda la sesión, o de un músculo.</span></div><span class="c">${ic('down',20)}</span></div>
   <div class="as-fb2"><div class="as-seg">${['RPE','RIR','Fallo','% RM'].map((x,i)=>`<button class="${i?'':'on'}" data-act="im">${x}</button>`).join('')}</div>
    ${asRow('Valor','',asStp('im',8,'8'))}<div><div class="as-eye" style="margin-bottom:8px">Alcance</div><div class="as-flow">${['Toda la sesión','Glúteos','Cuádriceps','Aductores','Isquiosurales'].map((x,i)=>`<button class="as-chip ${i?'':'on'}" data-act="ima">${x}</button>`).join('')}</div></div>
    <button class="as-btn sm" data-act="im-go">Aplicar intensidad</button></div></section></div>`}
function paneRegLim(L,o={}){
 const st=asLimStatus(L),topM=AS_MUS[0],bad=L.sess!=null&&topM.v>L.sess;
 const any=['rpe','exm','sess','week','pat'].some(k=>L[k]!=null);
 const row=(k,lab,sub)=>{const v=L[k],s=st[k];return `<div class="as-rr"><div class="tx"><strong>${lab}</strong><span class="s ${v==null?'':s[1]}" data-r="s-${k}">${v==null?sub:s[0]}</span></div>${`<div class="as-stp"><button data-act="lstep" data-k="${k}" data-d="-1" aria-label="Menos">${ic('asMinus',18)}</button><b data-v="l-${k}" class="${v==null?'mu':''}">${v==null?'—':(k==='rpe'?fm(v):v)}</b><button data-act="lstep" data-k="${k}" data-d="1" aria-label="Más">${ic('plus',18)}</button></div>`}</div>`};
 return `<div class="as-rp lim">
  <div data-r="ban">${bad?`<div class="as-bn bad">${ic('asWarn',20)}<div class="tx"><b>${topM.n} tiene ${fm(topM.v)} series</b> y tu máximo es ${L.sess}. ${L.rigid?'Con límites rígidos no podrás guardar la sesión así.':'Te avisaremos al guardar.'}</div><button class="as-link" data-act="go" data-go="sug">Ver</button></div>`:''}</div>
  ${any?'':`<div class="as-hint" style="padding:6px 4px 0;text-align:center">Sin límites. Pon un máximo y el asistente avisará cuando la sesión lo supere.</div>`}
  <section class="as-mod"><div class="as-mh"><h4>Máximos por sesión</h4></div>
   ${row('rpe','RPE máximo','Sin límite')}${row('exm','Ejercicios por músculo','Sin límite')}${row('sess','Series por músculo','Sin límite')}${row('pat','Ejercicios del mismo patrón','Sin límite')}</section>
  <section class="as-mod"><div class="as-mh"><h4>Por semana</h4></div>${row('week','Series por músculo','Sin límite')}</section>
  <section class="as-mod"><div class="as-fd"><div class="tx"><strong>Límites rígidos</strong><span class="s">${L.rigid?'No te dejaré guardar una sesión que los supere, y el asistente propondrá cómo ajustarla.':'Solo te aviso al guardar. El asistente no propone ajustes de límites.'}</span></div><span class="as-sw ${L.rigid?'on':''}" role="switch" aria-checked="${!!L.rigid}" tabindex="0" data-act="rigid"></span></div></section></div>`}
function asRing(est,lim){
 const r=lim?Math.min(1,est/lim):0,C=2*Math.PI*46,col=!lim?'#8e8980':est/lim<.85?'#43d18c':est/lim<=1.08?'#f7cf73':'#ff9b92';
 return `<div class="as-ring"><svg viewBox="0 0 112 112"><circle cx="56" cy="56" r="46" fill="none" stroke="#ffffff1a" stroke-width="9"/>${lim?`<circle data-r="arc" cx="56" cy="56" r="46" fill="none" stroke="${col}" stroke-width="9" stroke-linecap="round" stroke-dasharray="${(C*r).toFixed(1)} ${C.toFixed(1)}" transform="rotate(-90 56 56)"/>`:'<circle data-r="arc" cx="56" cy="56" r="46" fill="none" stroke="transparent" stroke-width="9"/>'}</svg><b style="color:${col}" data-r="rn">${est}<small>min</small></b></div>`}
function paneRegTie(T){
 const est=59,lim=T,gap=lim?est-lim:null,col=!lim?'var(--muted)':est/lim<.85?'var(--done)':est/lim<=1.08?'var(--ene)':'var(--danger)';
 const ch=[30,45,60,90].map(m=>`<button class="as-chip ${lim===m?'on':''}" data-act="tset" data-v="${m}">${m} min</button>`).join('')+`<button class="as-chip ${lim===est?'on':''}" data-act="tset" data-v="${est}">Estimado</button><button class="as-chip ${!lim?'on':''}" data-act="tset" data-v="0">Sin límite</button>`;
 const parts=[['Compuestos',22],['Aislamientos',20]];
 return `<div class="as-rp tie">
  <section class="as-mod"><div class="as-mh"><h4>Reloj de sesión</h4><span class="as-st ${!lim?'neu':est/lim<.85?'ok':est/lim<=1.08?'over':'bad'}" data-r="tst">${!lim?'Sin límite':est/lim<.85?'Dentro':est/lim<=1.08?'Cerca del límite':'Por encima'}</span></div>
   <div class="as-clk"><span data-r="ringw">${asRing(est,lim)}</span><div class="as-cs"><div><span>Estimado</span><b>${est}<small>min</small></b></div><div><span>Límite</span><b data-r="tlim">${lim?lim+'<small>min</small>':'<span style="display:inline;font:600 14px Inter;letter-spacing:0;text-transform:none;color:var(--muted)">Sin límite</span>'}</b></div><div><span>Diferencia</span><b data-r="tgap" style="color:${col}">${gap==null?'—':(gap>0?'+':'')+gap+'<small>min</small>'}</b></div></div></div>
   <div class="as-cap" style="margin-top:14px">Preparación 6 min · Ejecución 21 min · Descansos 29 min · Calentamiento 3 min</div></section>
  <section class="as-mod"><div class="as-mh"><h4>Tiempo de sesión</h4></div><div class="as-cap" style="margin:-4px 0 12px">Cuenta atrás en el entreno. No corta la sesión.</div>
   <div class="as-flow">${ch}</div>
   <div class="as-rr" style="margin-top:8px"><div class="tx"><strong>Presupuesto global</strong></div><button class="as-pk" data-act="rest"><span data-r="tcell">${lim?lim+' min':'Sin límite'}</span>${ic('fwd',14)}</button></div>
   <div class="as-par">${parts.map((p,i)=>`<i style="flex:${p[1]};background:rgba(242,238,230,${i?.5:1})"></i>`).join('')}<i style="flex:8;background:#242427"></i></div>
   <div class="as-cap" style="margin-top:8px">Compuestos 22 min · Aislamientos 20 min · 8 min libres</div></section>
  <div class="as-eye" style="padding-left:4px;margin-bottom:-4px" data-r="coach-h">Para entrar en tu límite</div>
  <div data-r="coach">${asCoach()}</div>
  <section class="as-mod as-fold" data-fold><div class="as-fd" data-act="fold" role="button" tabindex="0"><div class="tx"><strong>Tiempos por grupos y ejercicios</strong><span class="s">Por grupo o ejercicio. El del grupo vale.</span></div><span class="c">${ic('down',20)}</span></div>
   <div class="as-fb2"><button class="as-chip" style="align-self:flex-start" data-act="rest">Repartir global en grupos</button>
    ${parts.map(p=>`<div class="as-tg"><span class="n" style="font-weight:600">${p[0]}</span><button class="as-pk" data-act="rest"><span>${p[1]} min</span>${ic('fwd',14)}</button></div>`).join('')}</div></section></div>`}
function asCoach(){return [['Recortar descansos','Recorta los descansos de monoarticulares (3) y de multiarticulares (2), pero la sesión no llega al límite.',4,'Fatiga ≈'],['Superserie: Curl femoral tumbado + Extensión de cuádriceps','Empareja estaciones compatibles o funciones distintas para recortar descansos muertos.',3,'Fatiga ≈']].map((c,i)=>`<div class="as-tcm" data-i="${i}"><div class="h"><strong>${c[0]}</strong><b>−${c[2]} min</b></div><p>${c[1]}</p><div class="f"><span class="fat">${c[3]}</span></div><div class="bt"><button class="as-btn sm" data-act="coach-ok">Aplicar</button><button class="as-btn sm as-gh" data-act="coach-no">Descartar</button></div></div>`).join('')}
function paneReg(o={}){
 const sub=o.rs||'val',L=o.L||AS_LIM,T=o.T===undefined?AS_TARGET:o.T;
 return `<div class="as-sc" data-st='${JSON.stringify({L})}'>
  <div class="as-seg as-sub-seg" role="tablist" aria-label="Qué reglas"><button class="${sub==='val'?'on':''}" data-act="rsub" data-v="val">Valores</button><button class="${sub==='lim'?'on':''}" data-act="rsub" data-v="lim">Límites${L.sess!=null&&AS_MUS[0].v>L.sess?' <i class="as-nd" style="margin-left:4px"></i>':''}</button><button class="${sub==='tie'?'on':''}" data-act="rsub" data-v="tie">Tiempo</button></div>
  ${paneRegVal(o.sc||'all',o.R)}${paneRegLim(L,o)}${paneRegTie(T)}</div>
  <div class="as-toast" data-r="toast"><span class="m">${ic('check',16)}</span><div class="tx" data-r="toast-t">—</div></div>`}

/* ---------- cableado común ---------- */
function asWire(phone,cfg={}){
 const $q=(s,r=phone)=>r.querySelector(s),$a=(s,r=phone)=>[...r.querySelectorAll(s)];
 const root=$q('.as-root'),sheet=$q('.as-sheet'),order=['met','sug','pla','reg'];
 const plaSc=$q('.as-pane[data-p=pla] .as-sc'),S=JSON.parse(plaSc.dataset.s||'{}');
 const regSc=$q('.as-pane[data-p=reg] .as-sc'),RS=JSON.parse(regSc.dataset.st).L;
 const R={series:3,reps:10,rpe:8,it:'RPE',rn:90,rl:0,re:60,rr:120,cReps:6,cRpe:8,cRest:180,iRest:105,iReps:8,iRpe:1,im:8};
 let curTpl=cfg.id||'leg-quad',timer=null,target=cfg.T===undefined?AS_TARGET:cfg.T,applied=false,mode='r';
 const tabNow=()=>sheet.dataset.tab;
 function tab(k){
  const a=order.indexOf(tabNow()),b=order.indexOf(k);if(a===b)return;
  $a('.as-pane').forEach(p=>p.classList.remove('on'));const np=$q(`.as-pane[data-p=${k}]`);np.style.setProperty('--dx',(b>a?'12px':'-12px'));void np.offsetWidth;np.classList.add('on');
  sheet.dataset.tab=k;$a('.as-tabs button').forEach(x=>{const on=x.dataset.tab===k;x.classList.toggle('on',on);x.setAttribute('aria-selected',on)});}
 function setPv(v){sheet.dataset.pv=v;$q('.as-pane[data-p=pla]').dataset.pv=v;const sc=plaSc;sc.scrollTop=0}
 function setRs(v){sheet.dataset.rs=v;$q('.as-pane[data-p=reg]').dataset.rs=v;$a('.as-sub-seg button').forEach(b=>b.classList.toggle('on',b.dataset.v===v));regSc.scrollTop=0}
 const rebuildList=()=>{$q('[data-r=list]').innerHTML=asTplList(S);const n=asNActive(S),c=asTplCount(S);const r=$q('[data-r=res]');r.textContent=(S.q||S.ty||S.lv!=null||S.du)?(c===0?'':c+' resultado'+(c===1?'':'s')):AS_T.length+' plantillas';const fb=$q('.as-fb');fb.classList.toggle('has',!!n);fb.querySelector('b').textContent=n;const fp=$q('.as-fp');fp.outerHTML=asFilterPanel(S);$q('[data-r=act]').innerHTML=asActive(S);$q('.as-fb').classList.toggle('on',!!S.fp||!!n)};
 const toast=(t,sub,ms=2200)=>{const el=$q('[data-r=toast]');$q('[data-r=toast-t]').innerHTML=t+(sub?`<span>${sub}</span>`:'');el.classList.add('open');clearTimeout(el._t);el._t=setTimeout(()=>el.classList.remove('open'),ms)};
 const setV=(k,v,f)=>$a(`[data-v="${k}"]`).forEach(e=>{e.textContent=f?f(v):v;e.classList.remove('as-flash');void e.offsetWidth;e.classList.add('as-flash')});
 const limRefresh=()=>{
  const st=asLimStatus(RS);['rpe','exm','sess','week','pat'].forEach(k=>{const el=$q(`[data-r=s-${k}]`);const v=RS[k];el.className='s '+(v==null?'':st[k][1]);el.textContent=v==null?'Sin límite':st[k][0];const b=$q(`[data-v="l-${k}"]`);b.textContent=v==null?'—':(k==='rpe'?fm(v):v);b.classList.toggle('mu',v==null)});
  const top=AS_MUS[0],bad=RS.sess!=null&&top.v>RS.sess;
  $q('[data-r=ban]').innerHTML=bad?`<div class="as-bn bad">${ic('asWarn',20)}<div class="tx"><b>${top.n} tiene ${fm(top.v)} series</b> y tu máximo es ${RS.sess}. ${RS.rigid?'Con límites rígidos no podrás guardar la sesión así.':'Te avisaremos al guardar.'}</div><button class="as-link" data-act="go" data-go="sug">Ver</button></div>`:'';
  if(!cfg.noDot){const tb=$q('.as-tabs button[data-tab=reg]');tb.querySelector('.as-nd')?.remove();if(bad&&RS.rigid)tb.insertAdjacentHTML('beforeend','<i class="as-nd" aria-label="aviso"></i>')}};
 const ringRefresh=()=>{const est=59;$q('[data-r=ringw]').innerHTML=asRing(est,target||null);$q('[data-r=tlim]').innerHTML=target?target+'<small>min</small>':'<span style="display:inline;font:600 14px Inter;letter-spacing:0;text-transform:none;color:var(--muted)">Sin límite</span>';const g=target?est-target:null;const col=!target?'var(--muted)':est/target<.85?'var(--done)':est/target<=1.08?'var(--ene)':'var(--danger)';const e=$q('[data-r=tgap]');e.style.color=col;e.innerHTML=g==null?'—':(g>0?'+':'')+g+'<small>min</small>';$q('[data-r=tcell]').textContent=target?target+' min':'Sin límite';const s=$q('[data-r=tst]');s.className='as-st '+(!target?'neu':est/target<.85?'ok':est/target<=1.08?'over':'bad');s.textContent=!target?'Sin límite':est/target<.85?'Dentro':est/target<=1.08?'Cerca del límite':'Por encima';$a('[data-act=tset]').forEach(b=>b.classList.toggle('on',+b.dataset.v===(target||0)));};
 const sgRecalc=card=>{
  const dets=$a('.as-di',card),on=dets.filter(d=>d.classList.contains('on')),sec=on.reduce((a,d)=>a+ +d.dataset.sec,0),n=on.length;
  card.querySelector('[data-r=n]').textContent='('+n+')';
  card.querySelector('.as-btn').classList.toggle('off',!n);
  if(card.dataset.kind==='time_overage'){const base=59,lim=50,saved=Math.round(sec/60),after=base-saved,sc=Math.max(base,lim)*1.06,fx=card.querySelector('.as-fx');
   fx.querySelector('[data-r=after]').textContent=after;fx.querySelector('[data-r=bar]').style.width=(after/sc*100).toFixed(1)+'%';fx.classList.toggle('ok',after<=lim);
   fx.querySelector('[data-r=txt]').textContent=!n?'Elige qué opciones quieres aplicar.':after<=lim?'Con las opciones marcadas la sesión cabría en tu tiempo.':`Con las opciones marcadas ahorras ${saved} min; la sesión seguiría ${after-lim} min por encima del objetivo.`}
  else{const g=n?10:10.5,fx=card.querySelector('.as-fx');fx.querySelector('[data-r=g]').textContent=fm(g);fx.querySelector('[data-r=gbar]').style.width=(g/11.2*100).toFixed(1)+'%';fx.querySelector('[data-r=gtxt]').textContent=n?'Quitando 1 serie, Glúteos queda en 10: dentro de tu límite.':'Sin cambios, Glúteos sigue en 10,5: por encima de tu límite.';fx.classList.toggle('ok',!!n)}
 };
 $a('.as-sg').forEach(sgRecalc);
 const H={
  tab:t=>tab(t.dataset.tab),
  go:t=>{tab(t.dataset.go);if(t.dataset.sub)setRs(t.dataset.sub)},
  grab:()=>sheet.classList.toggle('full'),
  scrim:()=>{root.classList.remove('in');root.classList.add('closed')},
  reopen:()=>{root.classList.remove('closed','in');void root.offsetWidth;root.classList.add('in')},
  mrow:t=>{if(t.closest('.as-mdet')&&false)return;t.classList.toggle('open');t.setAttribute('aria-expanded',t.classList.contains('open'))},
  mall:t=>{root.dataset.mall=t.dataset.m;$a('[data-act=mall]').forEach(b=>b.classList.toggle('on',b===t))},
  chk:t=>{t.classList.toggle('on');t.setAttribute('aria-checked',t.classList.contains('on'));sgRecalc(t.closest('.as-sg'))},
  'sg-apply':t=>{const card=t.closest('.as-sg'),w=card.closest('.as-sgw'),on=$a('.as-di.on',card),sec=on.reduce((a,d)=>a+ +d.dataset.sec,0);w.querySelector("[data-r=aps]").textContent=`${on.length} ${on.length===1?'cambio':'cambios'} · −${Math.round(sec/60)} min`;w.classList.add('applied')},
  'sg-dismiss':t=>t.closest('.as-sgw').classList.add('dismissed'),
  'sg-undo':t=>t.closest('.as-sgw').classList.remove('applied','dismissed'),
  sec:t=>{const s=t.closest('.as-sec');s.classList.toggle('shut');S.shut=S.shut||{};S.shut[s.dataset.k]=s.classList.contains('shut')},
  'f-lvx':()=>{S.lv=null;rebuildList()},'f-dux':()=>{S.du='';rebuildList()},
  'f-open':()=>{S.fp=!S.fp;$q('[data-r=act]').innerHTML=asActive(S);$q('.as-fp').classList.toggle('open',S.fp);$q('.as-fb').classList.toggle('on',S.fp||!!asNActive(S))},
  'f-ty':t=>{S.ty=S.ty===t.dataset.v?null:t.dataset.v;$a('[data-act=f-ty]').forEach(b=>b.classList.toggle('on',b.dataset.v===S.ty));rebuildList()},
  'f-lv':t=>{S.lv=t.dataset.v===''?null:+t.dataset.v;rebuildList()},'f-du':t=>{S.du=t.dataset.v;rebuildList()},'f-gr':t=>{S.gr=t.dataset.v;rebuildList()},
  'tpl-clear':()=>{S.q='';S.ty=null;S.lv=null;S.du='';$q('[data-in=q]').value='';$a('[data-act=f-ty]').forEach(b=>b.classList.remove('on'));rebuildList()},
  'tpl-open':t=>{const id=t.dataset.id;if(!AS_PV[id]){return}curTpl=id;$q('[data-r=det]').innerHTML=asPlaDet(id);setPv('det');
   const pane=$q('.as-pane[data-p=pla]');pane.querySelectorAll('.as-dec,.as-ap').forEach(e=>e.remove());pane.insertAdjacentHTML('beforeend',asDecision(AS_T.find(x=>x.id===id)))},
  'tpl-back':()=>setPv('lst'),
  'tpl-apply':()=>$q('[data-r=dec]').classList.add('open'),
  'dec-x':()=>$q('[data-r=dec]').classList.remove('open'),
  'dec-mode':t=>{$a('.as-op').forEach(b=>b.classList.toggle('on',b===t));mode=t.dataset.m},
  'dec-go':()=>{const pane=$q('.as-pane[data-p=pla]');$q('[data-r=dec]').classList.remove('open');pane.querySelectorAll('.as-ap').forEach(e=>e.remove());pane.insertAdjacentHTML('beforeend',asApplying(AS_T.find(x=>x.id===curTpl),false,true));clearTimeout(timer);timer=setTimeout(()=>$q('[data-r=ap]')&&$q('[data-r=ap]').classList.add('done'),1300)},
  'ap-skip':t=>{if(!t.classList.contains('done')){clearTimeout(timer);t.classList.add('done')}},
  'ap-close':()=>{$q('[data-r=ap]').classList.remove('open');H.scrim()},
  'ap-undo':()=>{$q('[data-r=ap]').classList.remove('open')},
  'mine-open':()=>{$q('[data-r=mine]').innerHTML=asMine({});setPv('mine')},'mine-back':()=>setPv('lst'),
  'mine-menu':t=>{const m=$q('[data-r=menu]');const r=t.getBoundingClientRect(),pr=plaSc.getBoundingClientRect();m.style.top=(r.bottom-pr.top+plaSc.scrollTop+2)+'px';m.classList.toggle('open')},
  'mine-x':()=>$q('[data-r=menu]').classList.remove('open'),
  'mine-del':()=>{$q('[data-r=menu]').classList.remove('open');const pane=$q('.as-pane[data-p=pla]');pane.querySelectorAll('.as-dec').forEach(e=>e.remove());pane.insertAdjacentHTML('beforeend',`<div class="as-dec open" data-r="dec"><div><h3>Borrar plantilla</h3><p class="as-q">Esta acción es permanente. ¿Eliminar «Pierna pesada martes»?</p><button class="as-btn danger" data-act="dec-x">Borrar</button><button class="as-txa" data-act="dec-x">Cancelar</button></div></div>`)},
  'save-open':()=>{$q('[data-r=mine]').innerHTML=asSave({});setPv('sav')},
  'save-go':()=>{const f=$q('.as-fi');if(f.classList.contains('ph')){f.classList.add('err');if(!f.parentNode.querySelector('.as-er'))f.insertAdjacentHTML('afterend','<div class="as-er">Escribe un nombre para la plantilla.</div>')}},
  rsub:t=>setRs(t.dataset.v),
  scope:t=>{const rp=$q('.as-rp.val');rp.dataset.sc=t.dataset.v;$a('[data-act=scope]').forEach(b=>b.classList.toggle('on',b===t))},
  it:t=>{const k=t.dataset.k;t.parentNode.querySelectorAll('button').forEach(b=>b.classList.toggle('on',b===t));if(k==='it'){R.it=t.dataset.v;$q('[data-r=rpe-row]').style.display=t.dataset.v==='Fallo'?'none':'';$q('[data-r=rpe-l]').textContent=t.dataset.v}},
  step:t=>{const k=t.dataset.k,d=+t.dataset.d,lim={series:[1,12,1],reps:[1,50,1],rpe:[1,10,.5],cReps:[1,50,1],iReps:[1,50,1],cRpe:[1,10,.5],iRpe:[0,10,.5],im:[1,10,.5]}[k]||[0,99,1];R[k]=Math.min(lim[1],Math.max(lim[0],(R[k]||0)+d*lim[2]));setV(k,R[k],['rpe','cRpe','iRpe','im'].includes(k)?fm:null)},
  lstep:t=>{const k=t.dataset.k,d=+t.dataset.d,def={rpe:9,exm:6,sess:10,week:24,pat:4}[k],stp=k==='rpe'?.5:1;let v=RS[k];if(v==null){v=d>0?def:null}else{v=v+d*stp;if(k==='rpe'&&v>10)v=10;if(v<(k==='rpe'?1:1))v=null}RS[k]=v;limRefresh()},
  rigid:t=>{t.classList.toggle('on');RS.rigid=t.classList.contains('on');t.setAttribute('aria-checked',RS.rigid);t.closest('.as-fd').querySelector('.s').textContent=RS.rigid?'No te dejaré guardar una sesión que los supere, y el asistente propondrá cómo ajustarla.':'Solo te aviso al guardar. El asistente no propone ajustes de límites.';limRefresh()},
  sw:t=>{t.classList.toggle('on');t.setAttribute('aria-checked',t.classList.contains('on'))},
  fold:t=>{const f=t.closest('.as-fold');f.classList.toggle('open')},
  preset:t=>{const p=AS_PRE[+t.dataset.i][1];Object.assign(R,p);['series','reps','rn','rl','re','rr'].forEach(k=>setV(k,R[k],k[0]==='r'&&k.length===2?mmss:null));setV('rpe',R.rpe,fm);
   $a('[data-it=it] button').forEach(b=>b.classList.toggle('on',b.dataset.v===p.it));$q('[data-r=rpe-l]').textContent=p.it;$a('.as-chips [data-act=preset]').forEach(b=>b.classList.toggle('on',b===t))},
  rest:t=>{t.classList.remove('as-flash');void t.offsetWidth;t.classList.add('as-flash')},
  im:t=>{t.parentNode.querySelectorAll('button').forEach(b=>b.classList.toggle('on',b===t))},ima:t=>{t.parentNode.querySelectorAll('button').forEach(b=>b.classList.toggle('on',b===t))},
  'im-go':()=>toast('Intensidad aplicada','Toda la sesión · RPE '+fm(R.im)),
  part:t=>{t.parentNode.querySelectorAll('button').forEach(b=>b.classList.toggle('on',b===t))},
  'reg-apply':()=>{if(applied){toast('Sin cambios','Las series ya tenían estos valores.');return}applied=true;toast('Reglas aplicadas a 6 ejercicios','Series, repeticiones, esfuerzo y descansos actualizados.',1500);clearTimeout(timer);timer=setTimeout(()=>H.scrim(),1700)},
  tset:t=>{target=+t.dataset.v||null;ringRefresh()},
  'coach-ok':t=>{const c=t.closest('.as-tcm');c.style.opacity='.4';c.style.pointerEvents='none';toast('Sugerencia aplicada','Se ahorran '+c.querySelector('.h b').textContent.replace('−','')+'.')},
  'coach-no':t=>{t.closest('.as-tcm').remove()},
 };
 phone.addEventListener('click',e=>{const t=e.target.closest('[data-act]');if(!t||!phone.contains(t))return;if(t.dataset.act==='mrow'&&e.target.closest('.as-mdet'))return;const f=H[t.dataset.act];if(f){f(t,e)}});
 phone.addEventListener('keydown',e=>{if((e.key==='Enter'||e.key===' ')&&e.target.matches('[role=button],[role=checkbox],[role=switch]')){e.preventDefault();e.target.click()}});
 phone.addEventListener('input',e=>{if(e.target.matches('[data-in=q]')){S.q=e.target.value;rebuildList()}});
 // arranque: estados preparados por la escena
 if(cfg.dec){const d=$q('[data-r=dec]');d&&d.classList.add('open')}
 if(cfg.menuOpen){const m=$q('[data-r=menu]');if(m){m.classList.add('open')}}
 if(cfg.apTimer===false){}else if(cfg.ap==='run'){timer=setTimeout(()=>{const a=$q('[data-r=ap]');a&&a.classList.add('done')},cfg.apHold?1e9:1400)}
 if(cfg.sgApplied){const w=$q(`[data-sg=${cfg.sgApplied}]`);if(w){const on=$a('.as-di.on',w),sec=on.reduce((a,d)=>a+ +d.dataset.sec,0);w.querySelector("[data-r=aps]").textContent=`${on.length} ${on.length===1?'cambio':'cambios'} · −${Math.round(sec/60)} min`;w.classList.add('applied')}}
 if(cfg.scroll){const sc=$q(`.as-pane.on .as-sc`);if(sc)sc.scrollTop=cfg.scroll}
 limRefresh();
}

/* ====================== ESCENAS ====================== */
const asPanes=(o={})=>({met:paneMet(o.met),sug:paneSug(o.sug),pla:panePla(o.pla),reg:paneReg(o.reg)});
const asScene=(id,title,tab,o,notes,wire={},extra={})=>scene({id,tab:'asistente',title,notes,
 html:()=>asShell({tab,h:o.h,anim:o.anim,badge:o.badge||{sug:2,reg:'dot'},pv:(o.pla||{}).pv,rs:(o.reg||{}).rs,panes:asPanes(o),mall:o.met&&o.met.all,min:o.min}),
 after:ph=>{asWire(ph,{noDot:!(o.badge===undefined||o.badge.reg),...wire,...(o.pla||{}),id:(o.pla||{}).id||wire.id});animateIn(ph)},...extra});

/* 1 · entrada */
asScene('as-entrada','Abrir la hoja (animación)','met',{h:'med',anim:true,met:{}},{
 tags:[['keep','la hoja ya existe'],['new','cuatro pestañas']],
 p:['<b>Una hoja con cuatro pestañas</b>: Métricas, Sugerencias, Plantillas y Reglas. «Contenido» pasa a llamarse «Métricas» y las reglas de sesión dejan la barra inferior del editor para vivir aquí.',
 'Entra como el resto de hojas aprobadas: el fondo se oscurece en 220 ms, la hoja sube en 420 ms con freno suave y su contenido aparece un instante después. Con «reducir movimiento» solo se funde.',
 'Toca el fondo para cerrarla y «Abrir asistente» para repetir la entrada. Toca el asa para pasar de <b>media altura</b> a <b>altura completa</b>: la altura se conserva al cambiar de pestaña (no salta).'],
 list:['El título y la fila de pestañas se quedan fijos; solo el contenido se desplaza.','Pestañas: texto sobre una línea fina con un indicador en tinta. Caben en 360 px y a 130 % (la letra de las pestañas se frena en 1,08).','Un contador en «Sugerencias» (cuántos avisos hay) y un punto en «Reglas» (una regla choca con la sesión) las unen entre sí.'],
 listTitle:'Detalle',img:'h04-asistente-contenido',imgCap:'Hoy: tres pestañas, título suelto y la hoja a media altura fija.'});
/* 2 · métricas media */
asScene('as-met-media','Métricas · hoja media','met',{h:'med',met:{}},{
 tags:[['new','cifras y tiempo en módulos'],['keep','mismos datos que hoy']],
 p:['Lo que hoy es texto suelto se convierte en <b>módulos de datos</b>: las tres cifras (ejercicios, series, duración) juntas en un contenedor con separadores, y debajo el reparto del tiempo.',
 'El <b>tiempo</b> ya lo calcula la app (preparación, ejecución, descansos, calentamiento) pero hoy solo se ve en una línea dentro de Reglas. Aquí es una barra con tu límite marcado: si lo superas, el estado dice cuánto.'],
 list:['Cifras en Syne con dígitos de misma altura; «59 min» no se parte.','El límite de tiempo es el que fijaste en Reglas › Tiempo; «Cambiar» te lleva allí.']});
/* 3 · métricas completa + detalle */
asScene('as-met-completa','Métricas · hoja completa y detalle de un músculo','met',{h:'full',met:{open:'Glúteos'}},{
 tags:[['new','volumen por músculo rediseñado'],['new','detalle al tocar'],['ask','fuente única de volumen']],
 p:['<b>Volumen por músculo</b>, ordenado de más a menos series. Cada fila: nombre, cifra alineada a la derecha (sin partirse nunca: «10,5») y una barra fina en tinta. La paleta de músculos de la app es neutra a propósito (sin arcoíris), así que el color solo aparece con significado: <b>lo que pasa de tu límite se tiñe de ámbar</b>.',
 'La marca vertical de cada barra es <b>tu límite de series por sesión</b> (Reglas › Límites): es el único dato de referencia que existe hoy. El motor tiene un tipo para umbrales MEV/MAV/MRV pero hoy lo devuelve vacío, así que no dibujo zonas inventadas.',
 'Tocar una fila despliega <b>qué ejercicios aportan</b> (series × peso del músculo en ese ejercicio = lo que suma), la intensidad (Media/Alta/Muy alta/Máxima, que la app ya calcula) y cómo va la semana frente a tu límite semanal.'],
 list:['Las filas suman exactamente lo que dice la cifra: el desglose usa el mismo cálculo (series efectivas × contribución).','«Trabajados · 7 / Todos · 17» añade los músculos sin trabajar como una lista de etiquetas atenuadas, no como 10 filas vacías.','La semana usa las sesiones de la semana que el asistente ya recibe (es lo que valida el límite semanal al guardar).'],listTitle:'Detalle'},
 {scroll:0});
/* 4 · métricas todos */
asScene('as-met-todos','Métricas · todos los músculos','met',{h:'full',met:{all:true}},{
 tags:[['new','ver todos']],
 p:['Con «Todos» aparecen debajo los músculos que la sesión no toca, como etiquetas atenuadas: sirve para ver de un vistazo qué falta sin llenar la pantalla de filas a cero.'],
 list:['Los 17 músculos son los que reconoce el cálculo de volumen de la app (Cuello, Trapecio, Deltoides, Pectorales…).']},{scroll:360});
/* 5 · cardio */
asScene('as-met-cardio','Métricas · sesión con cardio','met',{h:'full',met:{cardio:true,noLimit:false}},{
 tags:[['new','tarjeta de cardio'],['ask','cardio en las cifras']],
 p:['Con un cardio al final, «Ejercicios» y «Duración» lo cuentan (7 y 74 min) y «Series» sigue siendo solo fuerza (18). Cada cifra lo aclara en una línea pequeña para que nadie piense que falta una serie.',
 'Una tarjeta propia muestra el cardio (tipo, posición y minutos) y recuerda que no suma series de músculo, que es lo que hace el cálculo de volumen hoy.'],
 list:['Hoy la hoja suma el cardio en «Ejercicios» sin decirlo: propongo mantener la suma y aclararla.','La tarjeta de cardio es nueva; sus datos (tipo, minutos) ya existen en el ejercicio.']});
/* 6 · vacía */
asScene('as-met-vacia','Métricas · sesión vacía','met',{h:'med',met:{empty:true},badge:{}},{
 tags:[['new','estado vacío']],
 p:['Hoy una sesión vacía deja tres ceros y un hueco. Aquí las cifras quedan en 0 y el módulo de volumen explica qué verás cuando haya ejercicios, con dos salidas: empezar desde una plantilla (cambia a la pestaña Plantillas) o cerrar y añadir ejercicios.'],
 list:['Los textos del estado vacío son propuestos por mí.']});
/* 7 · sugerencias lista */
asScene('as-sug-lista','Sugerencias · aplicar, descartar y deshacer','sug',{h:'full',sug:'list',badge:{sug:2,reg:'dot'}},{
 tags:[['keep','mismas dos reglas del motor'],['new','efecto en vivo'],['new','Descartar y Deshacer']],
 p:['Hoy el motor propone dos tipos de aviso: «Dentro de tus límites» (solo si tienes límites rígidos) y «Ajustar al tiempo que fijaste». Cada uno es una tarjeta con <b>qué pasa</b>, <b>cómo quedaría</b> y las <b>casillas</b> de cada cambio, con lo que ahorra cada una (el motor ya mide el efecto de cada acción).',
 'La caja «Duración» se recalcula al marcar o desmarcar: hoy el texto solo dice lo que pasa con las casillas por defecto. «Aplicar selección» cuenta cuántas hay marcadas y se apaga si no hay ninguna.',
 'Al aplicar, la tarjeta se pliega en una línea con <b>Deshacer</b>; <b>Descartar</b> la oculta con el mismo Deshacer. Ambos son nuevos: el motor aplica sobre una sesión inmutable, así que guardar la anterior para volver atrás es barato.'],
 list:['Etiqueta «Límite que elegiste» (real): la restricción es tuya, no una opinión del asistente.','El texto de cada casilla ya no lleva «· ahorra 2 min»: el ahorro sale en su propia columna.','Como mucho tres avisos a la vez (techo del motor).'],listTitle:'Detalle'});
asScene('as-sug-aplicada','Sugerencias · tras aplicar','sug',{h:'full',sug:'list',badge:{sug:1,reg:'dot'}},{
 tags:[['new','Deshacer']],p:['La tarjeta aplicada se convierte en una línea: qué cambió, cuánto ahorra y <b>Deshacer</b>. La otra sigue esperando.']},{sgApplied:'time_overage'});
/* 8 · todo en orden */
asScene('as-sug-vacio','Sugerencias · todo en orden','sug',{h:'med',sug:'ok',badge:{},min:47},{
 tags:[['keep','texto real'],['new','lo que revisa']],
 p:['El estado vacío conserva el texto real de la app («Sin sugerencias para esta sesión…») y añade lo que sí está comprobando: tu tiempo y tus límites rígidos, con su estado actual y un atajo a Reglas para cambiarlos.',
 'Son las dos únicas reglas que existen en el asistente hoy (límites rígidos y presupuesto de tiempo): no invento comprobaciones.']});
asScene('as-sug-nota','Sugerencias · solo información','sug',{h:'med',sug:'note',badge:{}},{
 tags:[['keep','nota informativa']],p:['Cuando el exceso es pequeño o ningún ajuste cambia el tiempo, el motor da una nota sin acción. Se muestra con la etiqueta «Información», sin botones.']});
asScene('as-sug-carga','Sugerencias · calculando','sug',{h:'med',sug:'load',badge:{}},{
 tags:[['keep','esqueleto de carga']],p:['Mientras el análisis se calcula, un esqueleto con la forma de una tarjeta. Con «reducir movimiento» el destello se queda quieto.']});
/* 9 · plantillas */
asScene('as-pla-catalogo','Plantillas · catálogo','pla',{h:'full',pla:{}},{
 tags:[['new','tarjetas-ficha'],['keep','mismos filtros y agrupaciones']],
 p:['Cada plantilla es una <b>tarjeta con portada</b> (las líneas de los programas), nombre, una línea propia, y sus datos de verdad: minutos, ejercicios, series y nivel con tres marcas (Fácil, Medio, Exigente), más los músculos que trabaja. Las portadas hacen distintas a las que hoy repiten el mismo párrafo.',
 'Buscador arriba con un botón de filtros (con contador); debajo, los tipos de sesión como chips (Piernas, Torso, Brazos…). Las secciones se pliegan tocando su título y dicen cuántas plantillas tienen.'],
 list:['La línea de cada tarjeta es la descripción corta real. Si una plantilla no la tiene (las «Dosis compacta»), se usa un dato propio (equipo) en vez del párrafo genérico repetido.','Muestra 16 de ejemplo; el catálogo real tiene unas 32 solo en Pierna.','«Mis plantillas · 3» y «Guardar sesión» pasan a la fila de resultados.'],listTitle:'Detalle',
 img:'h05-asistente-plantillas',imgCap:'Hoy: 32 tarjetas con el mismo texto repetido y sin portada.'});
asScene('as-pla-filtros','Plantillas · filtros activos','pla',{h:'full',pla:{fp:true,ty:'Piernas',lv:1}},{
 tags:[['keep','nivel, duración y agrupar'],['new','panel de filtros']],
 p:['Los tres desplegables de hoy («Agrupar», «Nivel», «Duración») pasan a un panel con controles segmentados: se ve todo lo elegido de golpe, con las opciones reales (Fácil/Medio/Exigente; Corta ≤45, Media 46–75, Larga ≥76; Grupo, Rutina, Nivel, Duración).',
 'Toca los chips y el panel: la lista responde.']});
asScene('as-pla-vacio','Plantillas · sin resultados','pla',{h:'full',pla:{fp:false,du:'m'}},{
 tags:[['keep','texto real del vacío']],
 p:['Con «Media (46–75 min)» no hay ninguna plantilla (hoy todas rondan los 35 min). El vacío conserva el texto de la app y añade <b>Limpiar filtros</b> como botón.']});
asScene('as-pla-vista','Plantillas · vista previa antes de aplicar','pla',{h:'full',pla:{pv:'det',id:'leg-quad'}},{
 tags:[['new','vista previa'],['keep','datos de la plantilla']],
 p:['Tocar una tarjeta abre su <b>ficha</b> dentro de la hoja (no un acordeón): portada, descripción, las cuatro cifras, los ejercicios por grupo con series × reps, esfuerzo y descanso, y el <b>volumen estimado por músculo</b> (el mismo componente de barras de Métricas).',
 'La barra inferior se queda fija con «Aplicar plantilla». Hoy el desplegable de la tarjeta enseña lo mismo en una lista de etiquetas: «Glúteos: 2,5 series».'],
 list:['El prototipo detalla dos plantillas (Leg Day · Cuádriceps y Glúteo Pump); el resto de tarjetas abrirían su ficha igual.','Existe el tipo «TemplatePreview» (volumen, aviso, duración) en el modelo del asistente; hoy el motor no lo rellena.']});
asScene('as-pla-aplicar','Plantillas · Reemplazar o añadir','pla',{h:'full',pla:{pv:'det',id:'leg-quad',dec:true}},{
 tags:[['keep','las dos opciones reales'],['new','resultado de cada una']],
 p:['Si la sesión ya tiene ejercicios, la app pregunta qué hacer. Aquí la pregunta enseña <b>cómo quedaría</b> la sesión con cada opción (ejercicios, series, minutos) y avisa si «Añadir» te pasa de tu límite de tiempo.',
 '«Reemplazar» es la sugerida (así lo trae hoy la app).'],
 list:['Las descripciones salen del código: Reemplazar sustituye ejercicios, grupos y calentamiento y conserva la identidad de la sesión; Añadir suma al final.','Los totales son sumas simples; el tiempo es aproximado.']},{},{});
asScene('as-pla-aplicando','Plantillas · aplicando','pla',{h:'full',pla:{pv:'det',id:'leg-quad',ap:'run'}},{
 tags:[['new','estado aplicando']],
 p:['Mientras se aplica, la ficha se cubre con un estado claro. Toca la pantalla para avanzar al resultado.'],list:['«Deshacer» es nuevo (ver Sugerencias).']},{ap:'run',apHold:true,id:'leg-quad'});
asScene('as-pla-aplicada','Plantillas · aplicada','pla',{h:'full',pla:{pv:'det',id:'leg-quad',ap:'done'}},{
 tags:[['new','resultado']],p:['Confirma qué pasó: «Reemplazaste 6 ejercicios por 6: 17 series, ≈35 min», con «Ver mi sesión» (cierra la hoja) y «Deshacer».']},{id:'leg-quad'});
asScene('as-pla-mias','Mis plantillas','pla',{h:'full',pla:{pv:'mine',corrupt:true,menu:true}},{
 tags:[['keep','archivar, editar, borrar'],['new','menú ⋯']],
 p:['«Mis plantillas» hoy son filas con cuatro botones de texto («Archivar», «Editar», «Borrar»…). Aquí cada una tiene su estado, un <b>Aplicar</b> directo y un menú ⋯ con Editar, Archivar y Borrar (Borrar pide confirmación, con el texto real: «Esta acción es permanente»).',
 'La nota ámbar de la plantilla ilegible en cuarentena se conserva.']},{menuOpen:true});
asScene('as-pla-guardar','Guardar sesión como plantilla','pla',{h:'full',pla:{pv:'sav',state:'err',name:''}},{
 tags:[['keep','nombre y descripción'],['new','como pantalla de la hoja']],
 p:['El diálogo «Guardar sesión como plantilla» (nombre y descripción) es ahora una pantalla de la propia hoja, con el error real si falta el nombre y el estado «Guardando…».']});
/* 10 · reglas */
asScene('as-reg-valores','Reglas · valores y descansos','reg',{h:'full',reg:{rs:'val'},badge:{sug:2,reg:'dot'}},{
 tags:[['keep','todas las reglas de hoy'],['new','filas con − y +']],
 p:['Las reglas de sesión dejan su hoja y pasan a esta pestaña, con la misma piel. Dentro, tres apartados: <b>Valores</b> (lo que hoy es «Reglas»), <b>Límites</b> y <b>Tiempo</b> (lo que hoy es la pestaña «Tiempo»).',
 'Series, repeticiones y RPE/RIR son filas con − y + (las celdas con teclado numérico se conservan al tocar la cifra); los cuatro descansos abren el selector de descanso de la app. Las plantillas de reglas son chips: tócalas y mira cómo cambian los valores.',
 '«Aplicar a la sesión» queda fija abajo, solo en Valores (hoy aparece también en Tiempo, donde no hace nada de lo que se ve). Al aplicar, la hoja se cierra, como hoy.'],
 list:['Alcance: Sesión · Grupo · Comp. y aisl. (cada uno cambia lo que ves).','«Modificar intensidad» sigue plegado, con sus modos y alcance por músculo.','El preset «Tiempo corto» guarda hoy un RPE 8,0 con tipo RIR (saldría «RIR 8»): aquí lo muestro como RIR 2; hay que corregirlo.'],listTitle:'Detalle'});
asScene('as-reg-compuestos','Reglas · compuestos y aislados','reg',{h:'full',reg:{rs:'val',sc:'ci'}},{
 tags:[['keep','una regla para cada tipo']],
 p:['Con el alcance «Comp. y aisl.» aparecen dos módulos: Compuestos y Aislamientos, cada uno con su descanso, repeticiones, intensidad y valor.']});
asScene('as-reg-limites','Reglas · límites que chocan con la sesión','reg',{h:'full',reg:{rs:'lim'},badge:{sug:2,reg:'dot'}},{
 tags:[['new','estado de cada límite'],['keep','los cinco límites y «rígidos»']],
 p:['Cada límite muestra <b>cómo está la sesión frente a él</b>: «dentro» en verde o lo que lo supera en ámbar. Si choca, un aviso arriba lo dice con las palabras de la app («Glúteos tiene 10,5 series») y lleva a Sugerencias.',
 'Los límites rígidos tienen ahora su explicación real: con ellos activados no se guarda una sesión que los supere y el asistente propone ajustes; sin ellos, solo se avisa al guardar.',
 'Prueba − y +: la sesión se vuelve a comparar al momento.'],
 list:['Hoy los límites están escondidos en una fila plegada dentro de «Plantillas».','Máximos por sesión y por semana van separados.']});
asScene('as-reg-limites-vacio','Reglas · sin límites','reg',{h:'med',reg:{rs:'lim',L:{sess:null,week:null,rpe:null,exm:null,pat:null,rigid:false}},badge:{}},{
 tags:[['new','estado vacío']],p:['Sin límites, cada fila lo dice y una línea invita a poner uno. + empieza en un valor razonable (RPE 9, 6 ejercicios, 10 series…); − en el mínimo lo quita.']});
asScene('as-reg-tiempo','Reglas · tiempo','reg',{h:'full',reg:{rs:'tie'}},{
 tags:[['keep','la pestaña Tiempo'],['ask','dos motores de tiempo']],
 p:['La pestaña Tiempo de hoy, con la misma información: el reloj de sesión (anillo, estimado, límite, diferencia), los atajos de presupuesto (30, 45, 60, 90, estimado, sin límite), el reparto por grupo y las sugerencias «Para entrar en tu límite» con su fatiga. Los chips cambian el anillo en vivo.',
 'Atención: estas sugerencias las genera otro motor (el «time coach») distinto del que alimenta la pestaña Sugerencias, y las dos hablan de ahorrar tiempo. Las dejo donde están hoy; unirlas es una decisión tuya.']});
/* documento · qué cambia y qué hay que decidir (va el último: el selector de estados se oculta si el primero es un documento) */
const AS_MAP=[
 ['«Contenido» (3 cifras + barras de texto suelto)','Métricas','Tres cifras en un módulo, tiempo desglosado con tu límite, volumen por músculo con cifra alineada, tu límite marcado y detalle al tocar.'],
 ['Sugerencias (módulos con casillas)','Sugerencias','Mismas dos reglas del motor; el efecto de cada casilla se ve y se recalcula; Descartar y Deshacer.'],
 ['Plantillas (32 tarjetas con el mismo párrafo)','Plantillas','Tarjetas-ficha con portada y datos propios; ficha de vista previa; Reemplazar/Añadir con el resultado de cada una; Mis plantillas.'],
 ['Hoja «Reglas y tiempo» (botón de la barra inferior)','Reglas','Dentro del asistente: Valores · Límites · Tiempo. El botón de la barra inferior del editor se quita (paquete Editor).'],
];
scene({tab:'asistente',id:'as-resumen',title:'Qué cambia y qué decidir',doc:()=>`
 <h2>De tres pestañas a cuatro, con una sola piel</h2>
 <p>El asistente de sesión es una hoja con título y pestañas fijos y contenido que se desplaza. Las reglas de sesión (hoy una hoja aparte, abierta desde la barra inferior del editor) entran como cuarta pestaña. Todo lo que hacía cada pantalla sigue estando; cambia cómo se presenta.</p>
 <div class="dtable-wrap"><table class="dtable"><thead><tr><th>Hoy</th><th>Pasa a</th><th>Cómo queda</th></tr></thead><tbody>${AS_MAP.map(r=>`<tr><td>${r[0]}</td><td>${r[1]}</td><td>${r[2]}</td></tr>`).join('')}</tbody></table></div>
 <h2>Lo que el motor ya calcula (y por eso se enseña)</h2>
 <p>Series efectivas por músculo, con el aporte de cada ejercicio; intensidad media por músculo (Media, Alta, Muy alta, Máxima); desglose del tiempo (preparación, ejecución, descansos, calentamiento); tu límite de tiempo y tus límites de series, RPE, ejercicios y patrón; el efecto medido de cada ajuste del asistente.</p>
 <h2>Lo que NO existe y por tanto no he dibujado</h2>
 <p>Veredicto, puntuación, equilibrio entre empuje y tirón, frecuencia por músculo, fatiga y zonas de volumen (mínimo, máximo recuperable): el informe del asistente trae los campos pero hoy los devuelve vacíos. Si los quieres en Métricas, hay que construir ese cálculo primero.</p>
 <h2>Lo que tienes que decidir</h2>
 <div class="as-ask"><b>1 · Dos motores de tiempo</b><p>Las sugerencias de Sugerencias («Ajustar al tiempo que fijaste») y las de Reglas › Tiempo («Para entrar en tu límite») salen de motores distintos y se pisan. Las dejo donde están hoy. Recomiendo unirlas en una sola lista dentro de Sugerencias más adelante.</p></div>
 <div class="as-ask"><b>2 · Fuente única del volumen</b><p>La cifra por músculo (la de siempre) pondera por el mayor papel de cada ejercicio; el cálculo «directo, secundario, estabilizador» del editor suma los tres. Dan números distintos. Uso el primero y su desglose por ejercicio suma exactamente la cifra. Si prefieres el segundo, las cifras cambian.</p></div>
 <div class="as-ask"><b>3 · Deshacer y Descartar</b><p>No existen hoy. Aplicar sobre una sesión inmutable permite guardar la anterior sin coste; lo dibujo porque aplicar sin poder volver atrás es lo que más frena a pulsar «Aplicar».</p></div>
 <div class="as-ask"><b>4 · «Aplicar» solo en Valores</b><p>Hoy la barra «Aplicar» aparece también en Tiempo. Allí no hace lo que se ve (los límites y el tiempo se guardan al editarlos). La dejo solo en Valores.</p></div>
 <div class="as-ask"><b>5 · Preset «Tiempo corto»</b><p>Guarda un esfuerzo de 8,0 con tipo RIR, que se leería «RIR 8». Lo muestro como RIR 2; hay que corregir el dato al implementar.</p></div>
 <h2>Cambios de motor que exigiría implementarlo</h2>
 <p>Ninguno para Métricas, Plantillas y Reglas (todo sale de datos y funciones existentes). Sugerencias necesita guardar la sesión anterior para Deshacer y recalcular al marcar casillas (suma de los efectos ya medidos). La vista previa de plantilla necesita rellenar el tipo «TemplatePreview» que hoy llega vacío.</p>`});
