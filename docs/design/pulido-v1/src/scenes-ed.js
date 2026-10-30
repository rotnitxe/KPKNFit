/* ================= PAQUETE EDITOR (ed) · Pulido v1 ================= */
/* Un solo editor interactivo (lista, tarjeta con series, borrar, ordenar, píldora, barra) y cada escena es un estado suyo. */
Object.assign(P,{
 minus:'<path d="M5 12h14"/>',
 arrowUp:'<path d="M12 19V5M6.5 10.5L12 5l5.5 5.5"/>',arrowDown:'<path d="M12 5v14M6.5 13.5L12 19l5.5-5.5"/>',
 timer:'<circle cx="12" cy="13.5" r="7"/><path d="M12 10v3.8l2.2 1.6M9.5 3.5h5"/>',
 copyAll:'<rect x="8.5" y="8.5" width="11.5" height="11.5" rx="3"/><path d="M5 15.5V7a3 3 0 013-3h8.5"/>',
 dupe:'<rect x="4" y="9" width="11" height="11" rx="3"/><path d="M9 5.5A3 3 0 0112 4h5a3 3 0 013 3v5a3 3 0 01-1.5 2.6"/>',
 sparkle:'<path d="M12 3.5l1.9 5.1 5.1 1.9-5.1 1.9L12 17.5l-1.9-5.1L5 10.5l5.1-1.9z"/><path d="M18.5 16.5v3M17 18h3"/>',
 flame:'<path d="M12 20.5a5.5 5.5 0 005.5-5.5c0-3.6-3-4.8-3.2-8.5-2.8 1.6-4 4-4 6.5-1.3-.4-1.8-1.8-1.8-1.8-.7 1.2-.8 2.2-.8 3.3A4.7 4.7 0 0012 20.5z"/>',
 sideLR:'<path d="M4 8h6M4 16h6M14 8h6M14 16h6"/>',
 rom:'<path d="M5 19L19 5M5 19h7M5 19v-7"/>',
 target2:'<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3.5"/>',
 plusc:'<circle cx="12" cy="12" r="8.5"/><path d="M12 8.5v7M8.5 12h7"/>',
 users2:'<circle cx="9" cy="9" r="3"/><circle cx="16.5" cy="10" r="2.4"/><path d="M3.5 19a5.5 5.5 0 0111 0M15 19a4.5 4.5 0 015.5-4.3"/>',
 layers:'<path d="M12 4l8.5 4.5L12 13 3.5 8.5z"/><path d="M3.5 12.5L12 17l8.5-4.5M3.5 16.5L12 21l8.5-4.5"/>',
 ruler2:'<rect x="3" y="8" width="18" height="8" rx="2"/><path d="M7 8v3M11 8v4M15 8v3M19 8v3"/>',
});
const edFmt=n=>String(n).replace('.',',');
const edRest=s=>`${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`;
const edDays=['Lun','Mar','Mié','Jue','Vie','Sáb','Dom'];
const edDayLetters=['L','M','X','J','V','S','D'];
const edModes=[['REPS','Reps','Repeticiones fijas o un rango, como 8–10.'],['AMRAP','AMRAP','Todas las repeticiones posibles, con un mínimo.'],['RM','RM','La carga como % de tu máximo.'],['SOLO_RPE','Solo RPE','Sin carga fija: la guía el esfuerzo.'],['TIME','Tiempo','Series por duración, como 45 s.'],['DISTANCE','Distancia','Series por distancia recorrida.'],['CUSTOM','Personalizado','Con tu propia unidad de medida.']];
const edModeName=m=>(edModes.find(x=>x[0]===m)||[0,'Reps'])[1];
const edLoadTypes=[['LOAD','Externa','Carga externa','Los kg que mueves. KPKN la estima con tu 1RM.'],['BODY','Corporal','Peso corporal','Sin carga extra: solo tu cuerpo.'],['LASTRE','Lastre','Lastre','Kg que añades a tu cuerpo, según tu récord con lastre.'],['ASSISTED','Asistido','Asistido','Kg que te ayudan, según tu récord asistido.']];

function edSet(o){return Object.assign({reps:5,range:false,rmin:8,rmax:10,ltype:'LOAD',kg:120,lastre:10,assist:25,iu:'RPE',iv:8,rest:null,drop:0,rp:0,myo:false,myoReps:4,myoPause:15,amrapMin:4,calib:false,pct:75,dur:40,dist:200,rpe:8,sides:['L','R']},o||{})}
function edEx(id,name,tag,o){
  const e=Object.assign({id,name,tag,mode:'REPS',rest:180,min:10,star:false,uni:false,open:false,sel:0,glyph:'dumb',sets:[edSet(),edSet(),edSet()],warm:[],mob:[],restOpen:false,order:'LR',shared:true,sideRest:15,rom:false},o||{});
  return e;
}
function edData(){
  return [
    edEx('e1','Peso Muerto Convencional','Barra',{min:11,star:true,sets:[edSet({kg:120}),edSet({kg:120,iv:8}),edSet({kg:115,iu:null})]}),
    edEx('e2','Sentadilla "Belt Squat"','',{min:10,kgBase:90,sets:[edSet({kg:90}),edSet({kg:90}),edSet({kg:90})]}),
    edEx('e3','Hip Thrust','Barra',{min:9,sets:[edSet({kg:100}),edSet({kg:100}),edSet({kg:100})]}),
    edEx('e4','Sentadilla en Máquina Pendular','',{min:10,sets:[edSet({kg:80}),edSet({kg:80}),edSet({kg:80})]}),
    edEx('e5','Patada de Glúteo','Polea',{min:9,uni:true,sets:[edSet({kg:25}),edSet({kg:25}),edSet({kg:25,sides:['L']})]}),
    edEx('e6','Plancha Copenhague Dinámica','',{min:10,mode:'TIME',rest:90,glyph:'clock',sets:[edSet({dur:40,iu:null,ltype:'BODY'}),edSet({dur:40,iu:null,ltype:'BODY'}),edSet({dur:40,iu:null,ltype:'BODY'})]}),
  ];
}
function edPlace(ex){ // una serie de datos de la tarjeta (carga, reps…) en versión corta para la ficha
  return ex;
}
const edRepsTxt=s=>s.range?`${s.rmin}–${s.rmax}`:`${s.reps}`;
const edIntTxt=s=>!s.iu?'':s.iu==='RPE'?`RPE ${edFmt(s.iv)}`:s.iu==='RIR'?`RIR ${s.iv}`:'Fallo';
function edLoadShort(s){
  if(s.ltype==='BODY')return 'Corporal';
  if(s.ltype==='LASTRE')return `+${edFmt(s.lastre)} kg`;
  if(s.ltype==='ASSISTED')return `${edFmt(s.assist)} kg`;
  return `${edFmt(s.kg)} kg`;
}
function edDose(ex){
  const s=ex.sets[0],n=ex.sets.length;let d;
  switch(ex.mode){
    case 'TIME':d=`${n} × ${edRest(s.dur)}`;break;
    case 'DISTANCE':d=`${n} × ${s.dist} m`;break;
    case 'CUSTOM':d=`${n} × ${s.dist} u`;break;
    case 'RM':d=`${n} × ${edFmt(s.pct)} % RM`;break;
    case 'SOLO_RPE':d=`${n} × RPE ${edFmt(s.rpe)}`;break;
    case 'AMRAP':d=`${n} × ${s.amrapMin}+`;break;
    default:d=`${n} × ${edRepsTxt(s)}`;
  }
  if(['REPS','TIME','DISTANCE','CUSTOM'].includes(ex.mode)&&s.iu)d+=` · ${edIntTxt(s)}`;
  return `${d} · ${edRest(ex.rest)}`;
}
function edSlots(ex){
  const out=[];
  ex.sets.forEach((s,i)=>{
    if(!ex.uni){out.push({si:i,side:null});return}
    const order=ex.order==='RL'?['R','L']:['L','R'];
    order.filter(x=>s.sides.includes(x)).forEach(x=>out.push({si:i,side:x}));
  });
  return out;
}
function edChipHTML(ex,slot,i){
  const s=ex.sets[slot.si];const label=slot.side?`${slot.side}${slot.si+1}`:`S${slot.si+1}`;
  let value,detail;
  switch(ex.mode){
    case 'SOLO_RPE':value=`RPE ${edFmt(s.rpe)}`;detail='objetivo';break;
    case 'TIME':value=edRest(s.dur);detail=s.iu?edIntTxt(s):'';break;
    case 'DISTANCE':value=`${s.dist}`;detail=['m',s.iu?edIntTxt(s):''].filter(Boolean).join(' · ');break;
    case 'CUSTOM':value=`${s.dist}`;detail='unidad';break;
    case 'RM':value=edLoadShort(s);detail=`${edFmt(s.pct)} % RM`;break;
    case 'AMRAP':value=edLoadShort(s);detail=`${s.amrapMin}+`;break;
    default:value=edLoadShort(s);detail=[edRepsTxt(s),s.iu?edIntTxt(s):''].filter(Boolean).join(' · ');
  }
  return `<button class="ed-sc ${ex.sel===i?'on':''}" data-a="sel" data-i="${i}" aria-label="${label}: ${value}, ${detail}"><small>${label}</small><b>${value}</b><em>${detail}</em></button>`;
}
const edI=(n,s=20)=>ic(n,s);
function edStepper(f,txt,o={}){
  const {unit='',min=0,max=999,dis=false,small=''}=o;
  return `<div class="ed-st ${o.cls||''}" data-fld="${f}"><button class="ed-stb" data-a="step" data-f="${f}" data-d="-1" aria-label="Menos" ${dis?'disabled':''}>${edI('minus',20)}</button><div class="ed-stv" data-scrub="${f}" role="spinbutton" aria-label="${o.label||f}"><b>${txt}</b>${unit?`<small>${unit}</small>`:''}${small?`<small>${small}</small>`:''}</div><button class="ed-stb" data-a="step" data-f="${f}" data-d="1" aria-label="Más">${edI('plus',20)}</button></div>`;
}

/* ---------- tarjeta abierta ---------- */
function edPrepHTML(ex){
  const parts=[];
  if(ex.mob.length){
    parts.push(`<div><div class="ed-pbh"><b>Movilidad<i>${ex.mob.length}</i></b><button data-a="noop">Total · ${Math.round(ex.mob.reduce((a,m)=>a+m.sec,0)/60)} min ${edI('down',14)}</button></div><div class="ed-chips">${ex.mob.map((m,i)=>`<button class="ed-sc mob" data-a="noop"><small>M${i+1}</small><b>${m.name}</b><em>${m.sec} s</em></button>`).join('')}<button class="ed-sc add" data-a="noop" aria-label="Añadir movilidad">${edI('plus',20)}</button></div></div>`);
  }
  if(ex.warm.length){
    parts.push(`<div><div class="ed-pbh"><b>Aproximación<i>${ex.warm.length}</i></b><button data-a="noop">Descanso común ${edRest(ex.warm[0].rest)} ${edI('down',14)}</button></div><div class="ed-chips">${ex.warm.map((w,i)=>`<button class="ed-sc ${ex.warmSel===i?'on':''}" data-a="wsel" data-i="${i}"><small>A${i+1}</small><b>${w.pct} %</b><em>${w.reps} reps</em></button>`).join('')}<button class="ed-sc add" data-a="noop" aria-label="Añadir aproximación">${edI('plus',20)}</button></div></div>`);
    if(ex.warmSel!=null&&ex.warm[ex.warmSel]){
      const w=ex.warm[ex.warmSel];
      parts.push(`<div class="ed-focus" style="animation:none"><div class="ed-fh"><b>Aproximación ${ex.warmSel+1}</b><div class="nav"><button data-a="wnav" data-d="-1" ${ex.warmSel===0?'disabled':''} aria-label="Aproximación anterior">${edI('back',20)}</button><button data-a="wnav" data-d="1" ${ex.warmSel===ex.warm.length-1?'disabled':''} aria-label="Aproximación siguiente">${edI('fwd',20)}</button></div></div>
        <div class="ed-st2"><div class="ed-sec" style="flex:1"><span class="ed-eyebrow">Carga (%)</span>${edStepper('wpct',w.pct,{label:'Carga en por ciento'})}</div><div class="ed-sec" style="flex:1"><span class="ed-eyebrow">Reps</span>${edStepper('wreps',w.reps,{label:'Repeticiones'})}</div></div>
        <div class="ed-tx2">Sugerida: ${edFmt(Math.round(ex.sets[0].kg*w.pct/100/2.5)*2.5)} kg · entre 10 % y 95 % · al menos 1 repetición</div>
        <div class="ed-acts"><button class="ed-act" data-a="noop" style="color:var(--danger)">Quitar</button><button class="ed-act" data-a="wclose">Listo</button></div></div>`);
    }
  }
  const missing=[];
  if(!ex.mob.length)missing.push(`<button class="ed-dash" data-a="noop">${edI('plus',16)} Movilidad</button>`);
  if(!ex.warm.length)missing.push(`<button class="ed-dash" data-a="noop">${edI('plus',16)} Aproximación</button>`);
  if(missing.length)parts.push(`<div class="ed-prep">${missing.join('')}</div>`);
  return parts.join('');
}
function edCardHTML(ex,st){
  const slots=edSlots(ex);if(ex.sel>=slots.length)ex.sel=slots.length-1;
  const restTxt=ex.uni?`Series ${edRest(ex.rest)} · Lados ${edRest(ex.sideRest)}`:edRest(ex.rest);
  const chips=slots.map((sl,i)=>edChipHTML(ex,sl,i)).join('');
  const missingSide=ex.uni?ex.sets.map((s,i)=>(['L','R'].filter(x=>!s.sides.includes(x)).map(x=>`<button class="ed-sc add" style="width:64px" data-a="addside" data-i="${i}" data-side="${x}" aria-label="Añadir ${x} a la serie ${i+1}"><small style="text-align:center">${x}${i+1}</small>${edI('plus',18)}</button>`).join(''))).join(''):'';
  return `<div class="ed-card">
    ${edPrepHTML(ex)}
    <div class="ed-eyebrow">Series efectivas</div>
    <div class="ed-optbar">
      <button class="ed-chip" data-a="modo" aria-label="Modo del ejercicio: ${edModeName(ex.mode)}">Modo: ${edModeName(ex.mode)} ${edI('down',14)}</button>
      <span class="grow"></span>
      <button class="ed-chip" data-a="rest-ex" aria-label="Descanso del ejercicio">${edI('timer',16)}<span class="tn">${restTxt}</span></button>
      <button class="ed-chip icon" data-a="exmenu" aria-label="Más opciones del ejercicio">${edI('more',20)}</button>
    </div>
    ${ex.uni?`<div class="ed-sides"><button class="ed-chip" data-a="order">Orden ${ex.order==='RL'?'R → L':'L → R'}</button><button class="ed-chip ${ex.shared?'on':''}" data-a="shared">${ex.shared?'Lados iguales':'Lados aparte'}</button><button class="ed-chip" data-a="rest-ex">Entre lados ${edRest(ex.sideRest)}</button></div>`:''}
    <div class="ed-chips" data-chips>${chips}${missingSide}<button class="ed-sc add" data-a="addset" aria-label="Añadir serie">${edI('plus',22)}</button></div>
    ${edFocusHTML(ex,st)}
  </div>`;
}
function edFocusHTML(ex,st){
  const slots=edSlots(ex),sl=slots[ex.sel];if(!sl)return '';
  const s=ex.sets[sl.si],n=ex.sets.length,m=ex.mode;
  const isOpen=(k)=>!!(st.layer&&st.layer.kind===k&&st.layer.ex===ex.id);
  /* — carga — */
  const readFor=()=>{
    if(s.ltype==='BODY')return {t:'Peso corporal',h:'sin carga extra',empty:true};
    if(s.ltype==='LASTRE')return {t:`+${edFmt(s.lastre)}<small>kg</small>`,h:'de lastre · según tu récord'};
    if(s.ltype==='ASSISTED')return {t:`${edFmt(s.assist)}<small>kg</small>`,h:'de ayuda · según tu récord'};
    return {t:`${edFmt(s.kg)}<small>kg</small>`,h:'estimada con tu 1RM'};
  };
  const lt=edLoadTypes.find(x=>x[0]===s.ltype);
  const r=readFor();
  const carga=m==='SOLO_RPE'?'':`<div class="ed-sec"><div class="ed-sech"><span class="ed-eyebrow">Carga</span><span class="hint">${r.h}</span></div>
    <div class="ed-load"><button class="ed-tp" data-a="ctype" aria-expanded="${isOpen('ctype')}" aria-label="Tipo de carga: ${lt[2]}">${lt[1]} ${edI('down',16)}</button>
    <button class="ed-read ${r.empty?'empty':''}" data-a="smart" aria-label="Carga inteligente"><b>${r.t}${m==='RM'?`<small>· ${edFmt(s.pct)} % RM</small>`:''}</b>${edI('fwd',16)}</button></div>
</div>`;
  /* — dosis — */
  let dosis='';
  if(m==='REPS'){
    dosis=`<div class="ed-sec"><div class="ed-sech"><span class="ed-eyebrow">Repeticiones</span><span class="ed-sm ed-sm-wrap" role="group" aria-label="Fijas o rango"><button class="${!s.range?'on':''}" data-a="rmode" data-v="fixed">Fijas</button><button class="${s.range?'on':''}" data-a="rmode" data-v="range">Rango</button></span></div>
      ${s.range?`<div class="ed-st2">${edStepper('rmin',s.rmin,{small:'mín',label:'Repeticiones mínimas'})}${edStepper('rmax',s.rmax,{small:'máx',label:'Repeticiones máximas'})}</div>`:edStepper('reps',s.reps,{label:'Repeticiones'})}</div>`;
  }else if(m==='AMRAP'){
    dosis=`<div class="ed-sec"><div class="ed-sech"><span class="ed-eyebrow">Reps mín.</span><span class="hint">tantas como puedas</span></div><div class="ed-row2">${edStepper('amin',s.amrapMin,{label:'Repeticiones mínimas'})}<button class="ed-amrap" data-a="calib" aria-pressed="${s.calib}">${s.calib?'AMRAP calibrador':'AMRAP'}</button></div></div>`;
  }else if(m==='RM'){
    dosis=`<div class="ed-sec"><div class="ed-sech"><span class="ed-eyebrow">% del RM</span><span class="hint">≈ ${Math.max(1,Math.round(30*(100/s.pct-1)))} reps máx. al ${edFmt(s.pct)} %</span></div>${edStepper('pct',s.pct,{unit:'%',label:'Porcentaje del RM'})}<input class="ed-range" type="range" min="45" max="100" step="1" value="${s.pct}" data-a="pctr" style="--p:${(s.pct-45)/55*100}%" aria-label="Porcentaje del RM"><div class="ed-rangel"><span>45 %</span><span>100 %</span></div></div>`;
  }else if(m==='SOLO_RPE'){
    dosis=`<div class="ed-sec"><div class="ed-sech"><span class="ed-eyebrow">RPE objetivo</span><span class="hint">sin carga fija · esfuerzo de 1 a 10</span></div>${edStepper('rpe',edFmt(s.rpe),{label:'RPE objetivo'})}<input class="ed-range" type="range" min="1" max="10" step="0.5" value="${s.rpe}" data-a="rpet" style="--p:${(s.rpe-1)/9*100}%" aria-label="RPE objetivo"><div class="ed-rangel"><span>1</span><span>10</span></div></div>`;
  }else if(m==='TIME'){
    dosis=`<div class="ed-sec"><div class="ed-sech"><span class="ed-eyebrow">Duración objetivo</span><span class="hint">en vivo · ${edRest(st.timing?st.timing.left:s.dur)}</span></div><div class="ed-st2">${edStepper('dmin',Math.floor(s.dur/60),{small:'min',label:'Minutos'})}${edStepper('dsec',s.dur%60,{small:'s',label:'Segundos'})}</div><button class="ed-lnkb" data-a="probe">${edI(st.timing?'pause':'play',16)} ${st.timing?'Detener prueba':'Probar cronómetro'}</button></div>`;
  }else{
    dosis=`<div class="ed-sec"><div class="ed-sech"><span class="ed-eyebrow">${m==='DISTANCE'?'Distancia (m)':'Unidad'}</span><span class="hint">${m==='DISTANCE'?'metros por serie':'unidad por configurar'}</span></div>${edStepper('dist',s.dist,{unit:m==='DISTANCE'?'m':'',label:'Distancia'})}</div>`;
  }
  /* — intensidad: un solo campo con la unidad dentro — */
  let inten='';
  if(['REPS','TIME','DISTANCE','CUSTOM'].includes(m)){
    if(!s.iu){
      inten=`<div class="ed-sec"><div class="ed-sech"><span class="ed-eyebrow">Intensidad</span><span class="hint">opcional</span></div><button class="ed-ghost" data-a="iadd" style="width:100%">${edI('plus',18)} Añadir intensidad</button></div>`;
    }else{
      const hint=s.iu==='RPE'?'esfuerzo de 1 a 10':s.iu==='RIR'?'reps que te dejas en reserva':'sin valor: hasta no poder más';
      const unit=`<button class="ed-unit" data-a="iunit" aria-expanded="${isOpen('iunit')}" aria-label="Unidad de intensidad: ${s.iu==='FALLO'?'Fallo':s.iu}">${s.iu==='FALLO'?'Fallo':s.iu} ${edI('down',16)}</button>`;
      const body=s.iu==='FALLO'?`<div class="ed-stv mute" style="cursor:default">${edI('flame',18)} Hasta el fallo</div>`:`<button class="ed-stb" data-a="step" data-f="iv" data-d="-1" aria-label="Menos">${edI('minus',20)}</button><div class="ed-stv" data-scrub="iv" role="spinbutton" aria-label="Valor de intensidad"><b>${edFmt(s.iv)}</b></div><button class="ed-stb" data-a="step" data-f="iv" data-d="1" aria-label="Más">${edI('plus',20)}</button>`;
      inten=`<div class="ed-sec"><div class="ed-sech"><span class="ed-eyebrow">Intensidad</span><span class="hint">${hint}</span></div><div class="ed-st" data-fld="iv">${unit}${body}</div>${s.iu==='FALLO'?edTechHTML(s):''}</div>`;
    }
  }
  /* — descanso — */
  const inherited=s.rest==null,rv=inherited?ex.rest:s.rest;
  const rest=`<div class="ed-sec ed-rest"><div class="ed-sech"><span class="ed-eyebrow">Descanso</span>${inherited?`<span class="ed-tag">Igual que el ejercicio</span>`:`<span class="ed-tag own">Propio<button data-a="rreset" aria-label="Usar el descanso del ejercicio">${edI('repeat',14)} Restablecer</button></span>`}</div>
    <div class="ed-st ed-restf ${ex.restOpen?'edit':''}" data-fld="rest"><button class="ed-stb" data-a="step" data-f="rest" data-d="-1" aria-label="Quitar 15 segundos">−15</button><button class="ed-stv" data-scrub="rest" data-a="rpresets" aria-expanded="${ex.restOpen}" aria-label="Descanso ${edRest(rv)}: toca para elegir uno rápido">${edI('timer',18)}<b>${edRest(rv)}</b></button><button class="ed-stb" data-a="step" data-f="rest" data-d="1" aria-label="Sumar 15 segundos">+15</button></div>
    ${ex.restOpen?`<div class="ed-presets">${[30,60,90,120,150,180,240,300].map(v=>`<button class="${rv===v?'on':''}" data-a="rpreset" data-v="${v}">${edRest(v)}</button>`).join('')}${!inherited?`<button class="rs" data-a="rreset">${edI('repeat',14)} ${edRest(ex.rest)} del ejercicio</button>`:''}</div>`:''}</div>`;
  /* — acciones — */
  const acts=`<div class="ed-acts">${n>1?`<button class="ed-act" data-a="apply">${edI('copyAll',16)} Aplicar a todas</button>`:''}<button class="ed-act" data-a="dup">${edI('dupe',16)} Duplicar</button><button class="ed-act more" data-a="smenu" aria-expanded="${isOpen('smenu')}" aria-label="Más acciones de la serie">${edI('more',20)}</button></div>`;
  const head=`<div class="ed-fh"><b>Serie ${sl.si+1} de ${n}${sl.side?` · ${sl.side}`:''}</b><div class="nav"><button data-a="snav" data-d="-1" ${ex.sel===0?'disabled':''} aria-label="Serie anterior">${edI('back',20)}</button><button data-a="snav" data-d="1" ${ex.sel===slots.length-1?'disabled':''} aria-label="Serie siguiente">${edI('fwd',20)}</button></div></div>`;
  return `<div class="ed-focus" data-focus>${head}${carga}${dosis}${inten}${rest}${acts}</div>`;
}
function edTechHTML(s){
  const dsOn=s.drop>0,rpOn=s.rp>0&&!s.myo,myOn=s.myo;
  const row=(k,vals,sel,suffix)=>`<div class="row">${vals.map(v=>`<button class="${sel===v?'on':''}" data-a="techn" data-k="${k}" data-v="${v}">${v}${suffix||''}</button>`).join('')}</div>`;
  let cfg='';
  if(dsOn)cfg=`<div class="ed-tcfg"><span class="ed-tx2">¿Cuántos drops después de la serie normal?</span>${row('drop',[1,2,3],s.drop)}</div>`;
  else if(rpOn)cfg=`<div class="ed-tcfg"><span class="ed-tx2">¿Cuántas mini-series tras la principal?</span>${row('rp',[1,2,3,4,5],s.rp)}<span class="ed-tx2">Pausa fija 0:15 · 3 reps · ${s.rp} miniseries</span></div>`;
  else if(myOn)cfg=`<div class="ed-tcfg"><span class="ed-tx2">¿Cuántas mini-series tras la principal?</span>${row('rp',[1,2,3,4,5],s.rp)}<span class="ed-tx2">Repeticiones por mini-serie</span>${row('myoReps',[3,4,5],s.myoReps)}<span class="ed-tx2">Pausa corta</span>${row('myoPause',[10,15,20,25,30],s.myoPause,' s')}</div>`;
  return `<div class="ed-tech"><b style="font:600 calc(13px*var(--f)) Inter">¿Vas más allá del fallo?</b><div class="ed-techrow"><button class="ed-tq ${dsOn?'on':''}" data-a="tech" data-k="drop"><b>Drop-set</b><span>Sin descanso · ~3 reps</span></button><button class="ed-tq rp ${rpOn?'on':''}" data-a="tech" data-k="rp"><b>Rest-pause</b><span>Pausa 15 s · 3 reps</span></button><button class="ed-tq rp ${myOn?'on':''}" data-a="tech" data-k="myo"><b>Myo-reps</b><span>4 mini-series · 15 s</span></button></div>${cfg}</div>`;
}

/* Cardio: sección copiada de CARDIO-EDITOR (ilustraciones, rótulo «CARDIO · AL FINAL», filas con mini barras). */
const edFig=(N,H,arms,legs)=>{
 const dx=N[0]-H[0],dy=N[1]-H[1],L=Math.hypot(dx,dy),ux=dx/L,uy=dy/L,r=n=>Math.round(n*10)/10;
 const head=[r(N[0]+ux*4.9),r(N[1]+uy*4.9)],S=[r(N[0]-ux*1.8),r(N[1]-uy*1.8)];
 const p=a=>a.map(q=>q[0]+' '+q[1]).join('L');
 return `<circle cx="${head[0]}" cy="${head[1]}" r="3.4"/><path d="M${N[0]} ${N[1]}L${H[0]} ${H[1]}"/>`+
  arms.map(a=>`<path d="M${S[0]} ${S[1]}L${p(a)}"/>`).join('')+legs.map(l=>`<path d="M${H[0]} ${H[1]}L${p(l)}"/>`).join('');
};
const ED_ART={
 TREADMILL:{m:'<rect x="6" y="51" width="42" height="5" rx="2.5"/><path d="M49 53L53 21M46 21h12"/>',
  f:edFig([29,21],[24,35],[[[34,27],[39,23]],[[22,27],[17,24]]],[[[30,42],[27,50]],[[21,43],[12,46]]])},
 BIKE_STATIONARY:{m:'<path d="M7 58h20M38 58h19"/><circle cx="43" cy="47" r="7"/><path d="M47 58L46 22M46 22h-9"/><path d="M17 58L21 36M15 35h11"/><path d="M21 51L40 48"/><circle cx="30" cy="50" r="2"/>',
  f:edFig([28,20],[20,33],[[[33,25],[38,22]]],[[[29,39],[30,49]]])},
 STAIR_CLIMBER:{m:'<path d="M4 57h12V49h10V41h10V33h9"/><path d="M48 33v24M42 20h12M48 20v13"/>',
  f:edFig([28,19],[27,32],[[[34,25],[42,21]]],[[[34,36],[32,40]],[[25,40],[21,48]]])}
};
const edArt=(k,size)=>`<svg class="ed-art" width="${size}" height="${size}" viewBox="0 0 64 64" fill="none" stroke-width="${Math.round(Math.min(2.6,1.75+Math.max(0,size-56)*.01)*64/size*100)/100}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><g class="m">${ED_ART[k].m}</g><g class="f">${ED_ART[k].f}</g></svg>`;
Object.assign(P,{edUp:'<path d="M12 19V5M6 11l6-6 6 6"/>',edDown:'<path d="M12 5v14M6 13l6 6 6-6"/>'});
const edCCh=(bl,h)=>`<div class="ed-chart" style="height:${h}px">${bl.map(b=>`<i class="ed-b ${b.t}" style="flex:${b.s} 1 0;height:${Math.max(16,b.l*10)}%"></i>`).join('')}</div>`;
const edRep=(w,wl,r,rl,n,warm,cool)=>{const b=[];if(warm)b.push({t:'A',s:warm,l:3});for(let i=0;i<n;i++){b.push({t:'W',s:w,l:wl});b.push({t:'R',s:r,l:rl})}if(cool)b.push({t:'C',s:cool,l:2});return b};
const edCData=()=>[
  {id:'c1',k:'TREADMILL',name:'Cinta de correr',dose:'10 min · RPE 8',min:10,bl:null},
  {id:'c2',k:'STAIR_CLIMBER',name:'Escaladora',dose:'8 × 0:30 / 0:30 · RPE 9',min:12,bl:edRep(30,9,30,3,8,120,120)},
];
/* ---------- estado y piezas de la pantalla ---------- */
function edState(cfg){
  const st={cfg,exs:edData(),cardio:edCData(),cardioFirst:false,day:3,pending:null,layer:null,limit:90,timing:null,sugg:3,bump:0};
  (cfg.mod||(()=>{}))(st);
  return st;
}
const edTotal=st=>st.exs.filter(e=>!(st.pending&&st.pending.id===e.id)).reduce((a,e)=>a+e.min,0)+st.cardio.reduce((a,c)=>a+c.min,0);
function edTone(m,limit){
  if(!limit)return 'n';
  const sc=m*100;
  if(sc>limit*108)return 'd';
  if(sc>=limit*85)return 'e';
  return 'o';
}
function edBarHTML(st,total){
  total=total==null?edTotal(st):total;
  const tone=edTone(total,st.limit),col={o:'var(--done)',e:'var(--ene)',d:'var(--danger)',n:'#8e8980'}[tone];
  const frac=st.limit?Math.min(1,total/st.limit):0,C=2*Math.PI*11.5;
  const desc=st.limit?`Tiempo estimado ${total} de ${st.limit} minutos`:`Tiempo estimado ${total} minutos`;
  return `<button class="ed-time" data-a="time" aria-label="${desc}"><svg class="ed-rg" width="32" height="32" viewBox="0 0 32 32" aria-hidden="true"><circle cx="16" cy="16" r="11.5" fill="none" stroke="#ffffff1c" stroke-width="3.5"/><circle cx="16" cy="16" r="11.5" fill="none" stroke="${col}" stroke-width="3.5" stroke-linecap="round" stroke-dasharray="${(C*frac).toFixed(1)} ${C.toFixed(1)}" transform="rotate(-90 16 16)" style="transition:stroke-dasharray .5s var(--ed-ease),stroke .3s"/></svg><span class="tx"><b>${total} min</b>${st.limit?`<small>de ${st.limit}</small>`:''}</span></button>
  <div class="ed-addw"><button class="main" data-a="add" aria-label="Añadir ejercicio">${edI('plus',18)}Añadir</button><i class="sp"></i><button class="chv" data-a="addmenu" aria-expanded="${!!(st.layer&&st.layer.kind==='add')}" aria-label="Más formas de añadir">${edI('up',18)}</button></div>
  <button class="ed-asi" data-a="asistente" aria-label="Asistente${st.sugg?`, ${st.sugg} sugerencias`:''}">${edI('sparkle',22)}${st.sugg?`<em>${st.sugg}</em>`:''}</button>`;
}
function edTopHTML(st,back){
  const lbl=`${edDays[st.day]} · Semana ${st.week||3}`;
  return `<button class="ed-ib" data-a="back" aria-label="Atrás">${edI('back',22)}</button>
  <div class="ed-day"><button class="ed-arrow" data-a="dprev" aria-label="Día anterior" ${st.day<=0?'disabled':''}>${edI('back',20)}</button><button class="ed-daybtn" data-a="week" aria-expanded="${!!(st.layer&&st.layer.kind==='day')}" aria-label="Abrir la semana: ${lbl}"><span class="ed-daylbl ${back?'back':''}">${lbl}</span>${edI('down',15)}</button><button class="ed-arrow" data-a="dnext" aria-label="Día siguiente" ${st.day>=6?'disabled':''}>${edI('fwd',20)}</button></div>
  <button class="ed-ib" data-a="more" aria-label="Más opciones">${edI('more',22)}</button>`;
}
function edGlyph(ex){return ic(ex.glyph||'dumb',22)}
function edRowHTML(ex,st,isCardio){
  const open=ex.open&&!isCardio;
  const name=isCardio?ex.name:`${ex.name}${ex.tag?` <i>· ${ex.tag}</i>`:''}${ex.uni?' <i>· Unilateral</i>':''}`;
  const dose=isCardio?ex.dose:edDose(ex);
  const pend=st.pending&&st.pending.id===ex.id;
  return `<div class="ed-ex ${open?'open':''}" data-id="${ex.id}" data-kind="${isCardio?'car':'str'}"><div class="ed-swwrap">
    <div class="ed-del"><div class="ed-delpill">${edI('trash',20)}<span class="lb">Eliminar</span></div></div>
    <div class="ed-row" data-row ${pend?'style="transform:translateX(-110%)"':''}>
      <button class="ed-grip" data-grip aria-label="Arrastra para reordenar ${ex.name}">${edI('grip',20)}</button>
      ${isCardio?`<span class="ed-thumb c">${edArt(ex.k,40)}</span>`:`<div class="ed-thumb">${edGlyph(ex)}</div>`}
      <button class="ed-rt" data-a="${isCardio?'noop':'toggle'}"><strong>${name}</strong><span>${dose}</span>${isCardio&&ex.bl?edCCh(ex.bl,22):''}</button>
      ${isCardio?'':`<button class="ed-ib2 ${ex.star?'on':''}" data-a="star" aria-label="Meta del ejercicio">${edI(ex.star?'starOn':'star',20)}</button>`}
      <button class="ed-ib2" data-a="${isCardio?'noop':'toggle'}" aria-label="${open?'Plegar':'Desplegar'}">${edI('down',20,'chev').replace('class=""','class="chev"')}</button>
    </div>
    <div class="ed-undo ${pend?'show':''}"><b>${isCardio?'Cardio eliminado':'Ejercicio eliminado'}</b><button data-a="undo" aria-label="Deshacer">Deshacer</button><div class="cd"><i></i></div></div>
  </div>
  ${isCardio?'':`<div class="ed-cardw"><div data-card="${ex.id}">${open?edCardHTML(ex,st):''}</div></div>`}<i class="ed-exline"></i></div>`;
}
function edBodyHTML(st){
  const n=st.exs.length,series=st.exs.reduce((a,e)=>a+e.sets.length,0),total=edTotal(st);
  const sum=[`${n} ejercicio${n===1?'':'s'} · ${series} series`,`≈ ${total} min`];
  const STR=`  <div class="ed-mod" data-mod><div class="ed-list" data-list>${st.exs.map(e=>edRowHTML(e,st)).join('')}</div></div>
  <div class="ed-foot"><button class="ed-lnk" data-a="add">${edI('plus',20)} Añadir ejercicio</button><button class="ed-pillb" data-a="group">Nuevo grupo</button></div>
  ${st.groups?st.groups.map(g=>`<div class="ed-gcard"><div class="ed-gh"><span>${g}</span></div><button class="ed-dashadd" data-a="add">${edI('plus',16)} Añadir a ${g}</button></div>`).join(''):''}`,CARD=`  <div class="ed-csec ed-sec-c"><span>CARDIO · ${st.cardioFirst?'AL INICIO':'AL FINAL'}</span><i class="ed-rule"></i><button class="ed-cib" data-a="cmove" aria-label="${st.cardioFirst?'Pasar el cardio después de la fuerza':'Pasar el cardio antes de la fuerza'}">${edI(st.cardioFirst?'edDown':'edUp',18)}</button><button class="ed-cib" data-a="addcardio" aria-label="Añadir cardio">${edI('plus',18)}</button></div>
  <div class="ed-mod" data-cmod><div class="ed-list">${st.cardio.map(c=>edRowHTML(c,st,true)).join('')}</div></div>`;
  return `<div class="ed-head"><div class="ed-eyebrow">Sesión</div><h1>Legs II</h1><div class="ed-desc">Añadir descripción</div><div class="ed-sum" data-sum>${sum.map(x=>`<span>${x}</span>`).join('')}</div></div>
  ${st.cardioFirst?CARD:STR}
  ${st.cardioFirst?STR:CARD}`;
}
function edHTML(cfg){
  const st=edState(cfg);edHTML.map=edHTML.map||{};edHTML.map[cfg.id]=st;
  return `<div class="ed-root" data-edroot="${cfg.id}"><div class="ed-scroll" data-scroll>${edBodyHTML(st)}</div><div class="ed-topfade"></div><div class="ed-top ed-glass" data-top>${edTopHTML(st)}</div><div class="ed-bar ed-glass" data-bar>${edBarHTML(st)}</div><div class="ed-layer" data-layer>${edLayerHTML(st)}</div></div>`;
}

/* ---------- capa: menús, hojas y avisos ---------- */
function edPopStyle(l){return `left:${l.x}px;top:${l.y}px;width:${l.w}px;--ox:${l.ox||'50%'};--oy:${l.flip?'100%':'0'};--dy:${l.flip?'6px':'-6px'}${l.flip?`;top:auto;bottom:${l.b}px`:''}`}
function edMI(a,icon,t,sub,o={}){return `<button class="ed-mi ${o.cls||''}" data-a="${a}" ${o.d?o.d:''} ${o.dis?'tabindex="-1"':''}>${icon?edI(icon,20):''}<span class="tx"><b>${t}</b>${sub?`<span>${sub}</span>`:''}</span>${o.ck?`<span class="ck">${edI('check',18)}</span>`:''}</button>`}
function edLayerHTML(st){
  const l=st.layer;if(!l)return '';
  const ex=l.ex&&st.exs.find(e=>e.id===l.ex);
  const closeScrim=`<div class="ed-scrim0" data-a="close"></div>`;
  const sheet=(inner)=>`<div class="ed-scrim ${l.out?'out':''}" data-a="close"></div><div class="ed-sheet ${l.out?'out':''}" role="dialog">${inner}</div>`;
  switch(l.kind){
    case 'ctype':{
      const s=ex.sets[edSlots(ex)[ex.sel].si];
      return closeScrim+`<div class="ed-pop" style="${edPopStyle(l)}" role="menu">${edLoadTypes.map(t=>edMI('cset','',t[1]==='Externa'?'Externa':t[1],t[3],{ck:s.ltype===t[0],d:`data-v="${t[0]}"`})).join('')}</div>`;
    }
    case 'iunit':{
      const s=ex.sets[edSlots(ex)[ex.sel].si];
      return closeScrim+`<div class="ed-pop" style="${edPopStyle(l)}" role="menu">${[['RPE','RPE','Esfuerzo de 1 a 10'],['RIR','RIR','Repeticiones que te dejas en reserva'],['FALLO','Fallo','Hasta no poder más']].map(t=>edMI('iset','',t[1],t[2],{ck:s.iu===t[0],d:`data-v="${t[0]}"`})).join('')}</div>`;
    }
    case 'smenu':{
      const slots=edSlots(ex),n=ex.sets.length,sl=slots[ex.sel];
      return closeScrim+`<div class="ed-pop" style="${edPopStyle(l)}" role="menu">${edMI('sup','arrowUp','Subir','',{dis:sl.si===0,cls:sl.si===0?'dis':''})}${edMI('sdown','arrowDown','Bajar','',{dis:sl.si===n-1,cls:sl.si===n-1?'dis':''})}<div class="ed-mdiv"></div>${edMI('sdel','trash','Eliminar serie',n>1?'':'Siempre queda una serie',{cls:`danger ${n>1?'':'dis'}`})}</div>`;
    }
    case 'add':
      return closeScrim+`<div class="ed-pop glassy" style="${edPopStyle(l)}" role="menu">${edMI('addex','dumb','Ejercicio','Del catálogo')}${edMI('addcardio','heart','Cardio','')}${edMI('group','layers','Grupo nuevo','')}</div>`;
    case 'more':
      return closeScrim+`<div class="ed-pop glassy" style="${edPopStyle(l)}" role="menu">${edMI('noop','swap','Transferir','')}${edMI('noop','history','Versiones','')}</div>`;
    case 'day':return edDaySheet(st,l,sheet);
    case 'modo':{
      const sel=l.sel||ex.mode;
      return sheet(`<div><h3>Modo del ejercicio</h3><p>Cómo se prescriben sus series</p></div><div class="ed-modegrid">${edModes.map(m=>edMI('modosel','',m[1],m[2],{ck:sel===m[0],d:`data-v="${m[0]}"`})).join('')}</div><button class="ed-pb" data-a="modoapply">${sel===ex.mode?'Listo':`Aplicar ${edModeName(sel)}`}</button>`);
    }
    case 'exmenu':
      return sheet(`<h3>Opciones del ejercicio</h3><div class="ed-modegrid">${edMI('noop','sideLR','Lateralidad',ex.uni?'Unilateral':'Bilateral')}${['REPS','RM','AMRAP'].includes(ex.mode)?edMI('noop','target2','Bloque APRE','',{}):''}${edMI('rom','rom',ex.rom?'Desactivar medir ROM':'Medir ROM','')}${edMI('noop','link','Relacionar ejercicio','')}${edMI('noop','layers','Superserie','')}${edMI('noop','ruler2','Unidad personalizada','')}${ex.mode!=='SOLO_RPE'?edMI('noop','bolt','Carga inteligente',''):''}${edMI('noop','star','Meta','')}</div>`);
    case 'restex':{
      const v=l.v!=null?l.v:ex.rest,sv=l.sv!=null?l.sv:ex.sideRest;
      const sec=(lbl,val,presets,key)=>`<div class="ed-sec"><span class="ed-eyebrow">${lbl}</span><div class="ed-st ed-restf" data-fld="${key}"><button class="ed-stb" data-a="rxs" data-k="${key}" data-d="-1" aria-label="Quitar 15 segundos">−15</button><div class="ed-stv" data-scrub="${key}">${edI('timer',18)}<b>${edRest(val)}</b></div><button class="ed-stb" data-a="rxs" data-k="${key}" data-d="1" aria-label="Sumar 15 segundos">+15</button></div><div class="ed-presets" style="animation:none">${presets.map(p=>`<button class="${val===p?'on':''}" data-a="rxp" data-k="${key}" data-v="${p}">${p<60?p+' s':edRest(p)}</button>`).join('')}</div></div>`;
      return sheet(`<div><h3>Descanso</h3><p>${ex.uni?'Series y entre lados, en un solo sitio.':'Cuánto esperas antes de la siguiente serie.'}</p></div>${sec(ex.uni?'Series':'Descanso',v,[15,30,45,60,90,120,180],'v')}${ex.uni?sec('Entre lados',sv,[5,10,15,30],'sv'):''}<button class="ed-pb" data-a="rxok">Listo</button>`);
    }
    case 'apply':{
      const c=ex.sets.length-1;
      return sheet(`<h3>Aplicar a todas</h3><p>${c===1?'Otra serie cambiará para quedar igual que esta. ¿Aplicar?':`${c} series cambiarán para quedar iguales que esta. ¿Aplicar?`}</p><div class="ed-btnrow"><button class="ed-pb" data-a="applyok">Aplicar</button><button class="ed-pb ghost" data-a="close">Cancelar</button></div>`);
    }
  }
  return '';
}
function edDaySheet(st,l,sheet){
  const has=[0,2,3,5],comp=[5];
  const wk=edDayLetters.map((d,i)=>`<button class="ed-wd ${has.includes(i)?'has':''} ${i===st.day?'cur':''} ${comp.includes(i)?'comp':''}" data-a="wday" data-i="${i}" aria-label="${edDays[i]}">${comp.includes(i)?'<span class="c">C</span>':''}<i>${d}</i>${i===3?'<span class="n">2</span>':''}</button>`).join('');
  const sess=[['Legs II','Principal',true],['Core y movilidad','',false]];
  const weeks=[['B1 · S1','Adaptación'],['B1 · S2','Adaptación'],['B1 · S3','Acumulación'],['B1 · S4','Descarga']];
  return sheet(`<div><h3>Semana</h3><p>Semana ${st.week||3} · Acumulación</p></div>
    <div class="ed-box" style="padding:10px 12px"><div class="ed-wk">${wk}</div></div>
    <div class="ed-box"><div class="ed-eyebrow">Sesiones de jueves</div>${sess.map(s=>`<button class="ed-radio" data-a="noop">${radio(s[2])}<span class="tx"><b>${s[0]}</b>${s[1]?`<span>${s[1]}</span>`:''}</span></button>${s[2]?'':`<button class="ed-ta" data-a="noop">Marcar como principal</button>`}`).join('')}</div>
    <div class="ed-box"><div class="ed-eyebrow">Variantes de la sesión</div><button class="ed-radio" data-a="noop">${radio(true)}<span class="tx"><b>Original</b></span></button><button class="ed-ta" data-a="noop">Crear sesión derivada</button></div>
    <div class="ed-box"><div class="ed-eyebrow">Semanas del programa</div>${weeks.map((w,i)=>`<button class="ed-radio" data-a="noop">${radio(i===2)}<span class="tx"><b>${w[0]}</b><span>${w[1]}</span></span></button>`).join('')}</div>`);
}

/* ---------- comportamiento ---------- */
const edClamp=(v,a,b)=>Math.min(b,Math.max(a,v));
function edWire(phone,st){
  const root=phone.querySelector('.ed-root');if(!root)return;
  const $=s=>root.querySelector(s);
  const scroll=$('[data-scroll]'),layerEl=$('[data-layer]');
  const W=()=>root.offsetWidth,Hh=()=>root.offsetHeight;
  const k=()=>{const w=root.offsetWidth;return w?root.getBoundingClientRect().width/w:1};
  const rel=el=>{const r=el.getBoundingClientRect(),rr=root.getBoundingClientRect(),z=k();return {l:(r.left-rr.left)/z,t:(r.top-rr.top)/z,w:r.width/z,h:r.height/z,r:(r.right-rr.left)/z,b:(r.bottom-rr.top)/z}};
  const exOf=id=>st.exs.find(e=>e.id===id);
  const cur=ex=>{const sl=edSlots(ex)[ex.sel];return {sl,s:ex.sets[sl.si]}};
  /* --- pintado parcial --- */
  const drawBar=total=>{$('[data-bar]').innerHTML=edBarHTML(st,total)};
  const drawTop=back=>{$('[data-top]').innerHTML=edTopHTML(st,back)};
  const drawLayer=()=>{layerEl.innerHTML=edLayerHTML(st)};
  const updateLayer=()=>{
    const tmp=document.createElement('div');tmp.innerHTML=edLayerHTML(st);
    const ns=tmp.querySelector('.ed-sheet'),os=layerEl.querySelector('.ed-sheet');
    if(ns&&os&&!st.layer.out)os.innerHTML=ns.innerHTML;else layerEl.innerHTML=tmp.innerHTML;
  };
  const drawCard=(ex,anim)=>{
    const c=root.querySelector(`[data-card="${ex.id}"]`);if(!c)return;
    const chips=c.querySelector('[data-chips]'),cl=chips?chips.scrollLeft:0,pr=c.querySelector('.ed-presets'),pl=pr?pr.scrollLeft:0;
    c.innerHTML=ex.open?edCardHTML(ex,st):'';
    const nc=c.querySelector('[data-chips]');if(nc)nc.scrollLeft=cl;
    const np=c.querySelector('.ed-presets');if(np){np.scrollLeft=pl;if(anim==='presets')np.classList.add('anim')}
    const f=c.querySelector('.ed-focus[data-focus]');if(f&&anim===true)f.classList.add('anim');
    const t=c.querySelector('.ed-tcfg');if(t&&anim==='tech')t.classList.add('anim');
  };
  const refreshRow=ex=>{const r=root.querySelector(`.ed-ex[data-id="${ex.id}"] .ed-rt span`);if(r)r.textContent=edDose(ex);const st2=root.querySelector(`.ed-ex[data-id="${ex.id}"] .ed-ib2.on, .ed-ex[data-id="${ex.id}"] [data-a=star]`);};
  const refreshSum=()=>{const n=st.exs.filter(e=>!(st.pending&&st.pending.id===e.id)).length,series=st.exs.filter(e=>!(st.pending&&st.pending.id===e.id)).reduce((a,e)=>a+e.sets.length,0);const el=$('[data-sum]');if(el)el.innerHTML=[`${n} ejercicio${n===1?'':'s'} · ${series} series`,`≈ ${edTotal(st)} min`].map(x=>`<span>${x}</span>`).join('')};
  const drawBody=()=>{const sc=scroll.scrollTop;scroll.innerHTML=edBodyHTML(st);scroll.scrollTop=sc};
  const tickMinutes=(delta)=>{
    drawBar();const t=$('.ed-time');if(!t)return;t.classList.add('tick');
    if(delta){const d=document.createElement('span');d.className='ed-delta'+(delta<0?' neg':'');d.textContent=(delta>0?'+':'−')+Math.abs(delta)+' min';t.appendChild(d);setTimeout(()=>d.remove(),1400)}
    refreshSum();
  };
  /* --- capas --- */
  const closeLayer=(cb)=>{
    const l=st.layer;if(!l)return;
    if(['day','modo','exmenu','restex','apply'].includes(l.kind)){l.out=true;drawLayer();setTimeout(()=>{if(st.layer===l){st.layer=null;drawLayer();if(cb)cb()}},230)}
    else{st.layer=null;drawLayer();if(cb)cb()}
    const ex=l.ex&&exOf(l.ex);if(ex&&ex.open)drawCard(ex);
    drawBar();drawTop();
  };
  const popAt=(btn,w,est,kind,ex)=>{
    const a=rel(btn),Wd=W(),Hd=Hh();
    let x=edClamp(a.r-w,12,Wd-12-w);if(kind==='ctype'||kind==='iunit')x=edClamp(a.l,12,Wd-12-w);
    const flip=a.b+est>Hd-100;
    const l={kind,ex,x,w,flip,ox:`${edClamp(a.l+a.w/2-x,20,w-20)}px`};
    if(flip)l.b=Hd-a.t+8;else l.y=a.b+8;
    return l;
  };
  const openLayer=(l)=>{
    const prev=st.layer;st.layer=l;
    const ex=l.ex&&exOf(l.ex);
    if(prev&&prev.ex&&prev.ex!==l.ex){const pe=exOf(prev.ex);if(pe&&pe.open)drawCard(pe)}
    drawLayer();if(ex&&ex.open)drawCard(ex);drawBar();drawTop();
  };
  /* --- campos numéricos --- */
  const stepField=(ex,f,d)=>{
    const {s}=cur(ex);
    switch(f){
      case 'reps':s.reps=edClamp(s.reps+d,1,100);break;
      case 'rmin':s.rmin=edClamp(s.rmin+d,1,s.rmax);break;
      case 'rmax':s.rmax=edClamp(s.rmax+d,s.rmin,100);break;
      case 'amin':s.amrapMin=edClamp(s.amrapMin+d,1,100);break;
      case 'pct':s.pct=edClamp(s.pct+d,45,100);break;
      case 'rpe':s.rpe=edClamp(s.rpe+d*.5,1,10);break;
      case 'iv':if(s.iu==='RPE')s.iv=edClamp(s.iv+d*.5,1,10);else s.iv=edClamp(s.iv+d,0,5);break;
      case 'dmin':s.dur=edClamp(s.dur+d*60,0,3599);break;
      case 'dsec':s.dur=edClamp(s.dur+d*5,0,3599);break;
      case 'dist':s.dist=edClamp(s.dist+d*(ex.mode==='DISTANCE'?5:1),0,99999);break;
      case 'rest':s.rest=edClamp((s.rest==null?ex.rest:s.rest)+d*15,0,900);break;
      case 'wpct':{const w=ex.warm[ex.warmSel];w.pct=edClamp(w.pct+d*5,10,95);break}
      case 'wreps':{const w=ex.warm[ex.warmSel];w.reps=edClamp(w.reps+d,1,30);break}
    }
    if(f==='rest'||f==='iv'||f==='reps'||f==='rmin'||f==='rmax'||f==='dmin'||f==='dsec'||f==='dist'||f==='amin'||f==='pct'||f==='rpe')refreshRow(ex);
    drawCard(ex);
    const sv=root.querySelector(`[data-card="${ex.id}"] [data-scrub="${f}"]`);if(sv){sv.classList.add('bump');setTimeout(()=>sv.classList.remove('bump'),260)}
  };
  /* --- acciones --- */
  const act=(a,t,e)=>{
    const exEl=t.closest('.ed-ex');let ex=exEl?exOf(exEl.dataset.id):null;if(!ex&&st.layer&&st.layer.ex)ex=exOf(st.layer.ex);
    switch(a){
      case 'noop':return;
      case 'close':return closeLayer();
      case 'toggle':{
        if(!ex)return;
        ex.open=!ex.open;
        if(ex.open&&st.exs.some(o=>o!==ex&&o.open))st.exs.filter(o=>o!==ex&&o.open).forEach(o=>{o.open=false;const el=root.querySelector(`.ed-ex[data-id="${o.id}"]`);el&&el.classList.remove('open')});
        exEl.classList.toggle('open',ex.open);drawCard(ex,true);
        if(!ex.open){const c=root.querySelector(`[data-card="${ex.id}"]`);setTimeout(()=>{if(!ex.open&&c)c.innerHTML=''},320)}
        return;
      }
      case 'star':if(ex){ex.star=!ex.star;t.classList.toggle('on',ex.star);t.innerHTML=edI(ex.star?'starOn':'star',20)}return;
      case 'sel':{ex.sel=+t.dataset.i;drawCard(ex,true);return}
      case 'snav':{ex.sel=edClamp(ex.sel+(+t.dataset.d),0,edSlots(ex).length-1);drawCard(ex,true);return}
      case 'addset':{const last=ex.sets[ex.sets.length-1];ex.sets.push(Object.assign({},last,{sides:['L','R'],drop:0,rp:0,myo:false}));ex.sel=edSlots(ex).length-1;ex.min+=1;refreshRow(ex);drawCard(ex,true);const ch=root.querySelector(`[data-card="${ex.id}"] [data-chips]`);if(ch)ch.scrollLeft=9999;tickMinutes(0);refreshSum();return}
      case 'addside':{const s=ex.sets[+t.dataset.i];s.sides.push(t.dataset.side);ex.sel=edSlots(ex).findIndex(x=>x.si===+t.dataset.i&&x.side===t.dataset.side);drawCard(ex,true);return}
      case 'order':ex.order=ex.order==='RL'?'LR':'RL';drawCard(ex);return;
      case 'shared':ex.shared=!ex.shared;drawCard(ex);return;
      case 'step':{stepField(ex,t.dataset.f,+t.dataset.d);return}
      case 'rmode':{const {s}=cur(ex);const r=t.dataset.v==='range';if(s.range!==r){s.range=r;if(r){s.rmin=Math.max(1,s.reps-2);s.rmax=s.reps}else s.reps=s.rmax}refreshRow(ex);drawCard(ex,true);return}
      case 'ctype':{if(st.layer&&st.layer.kind==='ctype')return closeLayer();openLayer(popAt(t,Math.min(280,W()-24),300,'ctype',ex.id));return}
      case 'cset':{const {s}=cur(ex);s.ltype=t.dataset.v;closeLayer();return}
      case 'smart':{t.animate([{opacity:.4},{opacity:1}],{duration:240});return}
      case 'iunit':{if(st.layer&&st.layer.kind==='iunit')return closeLayer();openLayer(popAt(t,Math.min(280,W()-24),230,'iunit',ex.id));return}
      case 'iset':{const {s}=cur(ex),u=t.dataset.v;if(s.iu==='RPE')s._rpe=s.iv;if(s.iu==='RIR')s._rir=s.iv;if(u==='RPE')s.iv=s._rpe!=null?s._rpe:8;if(u==='RIR')s.iv=s._rir!=null?s._rir:2;s.iu=u;if(u!=='FALLO'){s.drop=0;s.rp=0;s.myo=false}refreshRow(ex);closeLayer();return}
      case 'iadd':{const {s}=cur(ex);s.iu='RPE';s.iv=8;refreshRow(ex);drawCard(ex);return}
      case 'tech':{const {s}=cur(ex),kk=t.dataset.k;
        const was=kk==='drop'?s.drop>0:kk==='rp'?(s.rp>0&&!s.myo):s.myo;
        s.drop=0;s.rp=0;s.myo=false;
        if(!was){if(kk==='drop')s.drop=1;if(kk==='rp')s.rp=2;if(kk==='myo'){s.myo=true;s.rp=4}}
        drawCard(ex,'tech');return}
      case 'techn':{const {s}=cur(ex);s[t.dataset.k]=+t.dataset.v;drawCard(ex);return}
      case 'calib':{const {s}=cur(ex);s.calib=!s.calib;drawCard(ex);return}
      case 'probe':{
        const {s}=cur(ex);
        if(st.timing){clearInterval(st.timing.iv);st.timing=null;drawCard(ex);return}
        st.timing={left:s.dur};
        st.timing.iv=setInterval(()=>{if(!root.isConnected||!st.timing){clearInterval(st.timing&&st.timing.iv);return}st.timing.left--;if(st.timing.left<=0){clearInterval(st.timing.iv);st.timing=null}drawCard(ex)},1000);
        drawCard(ex);return}
      case 'rpresets':{ex.restOpen=!ex.restOpen;drawCard(ex,'presets');return}
      case 'rpreset':{const {s}=cur(ex);s.rest=+t.dataset.v===ex.rest?null:+t.dataset.v;refreshRow(ex);drawCard(ex);return}
      case 'rreset':{const {s}=cur(ex);s.rest=null;ex.restOpen=false;drawCard(ex);return}
      case 'apply':{openLayer({kind:'apply',ex:ex.id});return}
      case 'applyok':{
        const l=st.layer,e2=exOf(l.ex),src=cur(e2).s;
        e2.sets.forEach(o=>{if(o!==src)Object.assign(o,{reps:src.reps,range:src.range,rmin:src.rmin,rmax:src.rmax,ltype:src.ltype,iu:src.iu,iv:src.iv,rest:src.rest,drop:src.drop,rp:src.rp,myo:src.myo,pct:src.pct,dur:src.dur,dist:src.dist,rpe:src.rpe,amrapMin:src.amrapMin})});
        closeLayer(()=>{drawCard(e2);refreshRow(e2);root.querySelectorAll(`[data-card="${e2.id}"] .ed-sc:not(.add)`).forEach(c=>c.classList.add('flash'))});return}
      case 'dup':{const {sl,s}=cur(ex);ex.sets.splice(sl.si+1,0,Object.assign({},s));ex.sel=edSlots(ex).findIndex(x=>x.si===sl.si+1);ex.min+=1;refreshRow(ex);drawCard(ex,true);tickMinutes(0);return}
      case 'smenu':{if(st.layer&&st.layer.kind==='smenu')return closeLayer();openLayer(popAt(t,220,230,'smenu',ex.id));return}
      case 'sup':case 'sdown':{const {sl}=cur(ex),d=a==='sup'?-1:1,j=sl.si+d;if(j<0||j>=ex.sets.length)return;const arr=ex.sets;[arr[sl.si],arr[j]]=[arr[j],arr[sl.si]];ex.sel=edSlots(ex).findIndex(x=>x.si===j);closeLayer();return}
      case 'sdel':{const {sl}=cur(ex);if(ex.sets.length<2)return;ex.sets.splice(sl.si,1);ex.sel=Math.min(ex.sel,edSlots(ex).length-1);ex.min=Math.max(1,ex.min-1);closeLayer(()=>{refreshRow(ex);drawCard(ex,true);tickMinutes(0)});return}
      case 'modo':openLayer({kind:'modo',ex:ex.id,sel:ex.mode});return;
      case 'modosel':st.layer.sel=t.dataset.v;updateLayer();return;
      case 'modoapply':{const l=st.layer,e2=exOf(l.ex);if(l.sel!==e2.mode){e2.mode=l.sel;e2.sel=Math.min(e2.sel,edSlots(e2).length-1);if(e2.mode==='TIME')e2.rest=90}closeLayer(()=>{refreshRow(e2);drawCard(e2,true)});return}
      case 'exmenu':openLayer({kind:'exmenu',ex:ex.id});return;
      case 'rom':{const e2=exOf(st.layer.ex);e2.rom=!e2.rom;updateLayer();return}
      case 'rest-ex':openLayer({kind:'restex',ex:ex.id});return;
      case 'rxs':{const l=st.layer,e2=exOf(l.ex),key=t.dataset.k,d=+t.dataset.d;l[key]=edClamp((l[key]!=null?l[key]:key==='v'?e2.rest:e2.sideRest)+d*(key==='v'?15:5),0,900);updateLayer();return}
      case 'rxp':{const l=st.layer;l[t.dataset.k]=+t.dataset.v;updateLayer();return}
      case 'rxok':{const l=st.layer,e2=exOf(l.ex);if(l.v!=null)e2.rest=l.v;if(l.sv!=null)e2.sideRest=l.sv;closeLayer(()=>{refreshRow(e2);drawCard(e2)});return}
      case 'wsel':ex.warmSel=ex.warmSel===+t.dataset.i?null:+t.dataset.i;drawCard(ex,true);return;
      case 'wnav':ex.warmSel=edClamp(ex.warmSel+(+t.dataset.d),0,ex.warm.length-1);drawCard(ex,true);return;
      case 'wclose':ex.warmSel=null;drawCard(ex);return;
      case 'back':t.animate([{transform:'scale(.9)'},{transform:'none'}],{duration:200});return;
      case 'dprev':case 'dnext':{
        const d=a==='dprev'?-1:1,days=[0,2,3,5];const i=days.indexOf(st.day),j=i+d;if(j<0||j>=days.length)return;
        st.day=days[j];drawTop(d<0);
        const sc=$('[data-scroll]'),box=sc.firstElementChild;
        sc.animate([{opacity:.0,transform:`translateX(${d*40}px)`},{opacity:1,transform:'none'}],{duration:240,easing:'cubic-bezier(.2,.8,.2,1)'});
        return}
      case 'week':if(st.layer&&st.layer.kind==='day')return closeLayer();openLayer({kind:'day'});return;
      case 'wday':{const i=+t.dataset.i;if([0,2,3,5].includes(i)){const back=i<st.day;st.day=i;closeLayer(()=>drawTop(back))}return}
      case 'more':if(st.layer&&st.layer.kind==='more')return closeLayer();openLayer({kind:'more',x:Math.max(12,W()-12-208),y:98,w:208});return;
      case 'addmenu':{if(st.layer&&st.layer.kind==='add')return closeLayer();const bar=rel($('[data-bar]')),btn=rel(t.closest('.ed-addw'));openLayer({kind:'add',x:edClamp(btn.l,12,W()-12-232),w:232,flip:true,b:Hh()-bar.t+8,ox:'60%'});return}
      case 'add':case 'addex':{
        if(st.layer)st.layer=null,drawLayer();
        const n=st.exs.length+1;const e=edEx('n'+n,'Press de Banca','Barra',{min:9,sets:[edSet({kg:70}),edSet({kg:70}),edSet({kg:70})]});
        st.exs.push(e);
        const list=$('[data-list]');list.insertAdjacentHTML('beforeend',edRowHTML(e,st));list.lastElementChild.classList.add('fresh');
        tickMinutes(e.min);return}
      case 'addcardio':{
        if(st.layer)st.layer=null,drawLayer();
        const c={id:'c'+(st.cardio.length+1)+Date.now()%1000,k:'BIKE_STATIONARY',name:'Bicicleta estática',dose:'15 min · RPE 6',min:15,bl:null};st.cardio.push(c);
        const lists=root.querySelectorAll('[data-cmod] .ed-list');const l=lists[0];l.insertAdjacentHTML('beforeend',edRowHTML(c,st,true));l.lastElementChild.classList.add('fresh');
        tickMinutes(c.min);return}
      case 'group':{if(st.layer)st.layer=null,drawLayer();st.groups=(st.groups||[]).concat(['Grupo '+((st.groups||[]).length+1)]);drawBody();return}
      case 'cmove':{st.cardioFirst=!st.cardioFirst;const sc=scroll.scrollTop;drawBody();scroll.scrollTop=sc;return}
      case 'time':case 'asistente':{t.animate([{transform:'scale(.94)'},{transform:'none'}],{duration:220});return}
      case 'undo':{doUndo();return}
    }
  };
  root.addEventListener('click',e=>{
    if(Date.now()-(root.__gest||0)<120)return;
    const t=e.target.closest('[data-a]');if(!t||!root.contains(t))return;
    act(t.dataset.a,t,e);
  });
  root.addEventListener('input',e=>{
    const t=e.target;if(t.dataset.a==='pctr'){const ex=exOf(t.closest('[data-card]').dataset.card),{s}=cur(ex);s.pct=+t.value;t.style.setProperty('--p',(s.pct-45)/55*100+'%');const sp=t.closest('.ed-sec');const hint=t.closest('.ed-card').querySelector('.ed-sec:nth-of-type(2) .hint');refreshRow(ex);const rd=sp.querySelector('.ed-read b');if(rd)rd.innerHTML=rd.innerHTML.replace(/· [\d,]+ % RM/,`· ${edFmt(s.pct)} % RM`)}
    if(t.dataset.a==='rpet'){const ex=exOf(t.closest('[data-card]').dataset.card),{s}=cur(ex);s.rpe=+t.value;t.style.setProperty('--p',(s.rpe-1)/9*100+'%');const v=t.closest('.ed-sec').querySelector('.ed-stv b');if(v)v.textContent=edFmt(s.rpe);refreshRow(ex)}
  });
  root.addEventListener('change',e=>{const t=e.target;if(t.dataset&&(t.dataset.a==='pctr'||t.dataset.a==='rpet')){const ex=exOf(t.closest('[data-card]').dataset.card);drawCard(ex)}});
  /* --- arrastrar el valor para cambiarlo --- */
  root.addEventListener('pointerdown',e=>{
    const sv=e.target.closest('[data-scrub]');if(!sv)return;
    const f=sv.dataset.scrub,card=sv.closest('[data-card]'),exId=card&&card.dataset.card;
    if(!exId||sv.closest('.ed-sheet'))return;
    const ex=exOf(exId);let x0=e.clientX,moved=false;
    const mv=ev=>{const dx=(ev.clientX-x0)/k();if(Math.abs(dx)>=16){moved=true;root.__gest=Date.now();stepField(ex,f,dx>0?1:-1);x0=ev.clientX}};
    const up=()=>{window.removeEventListener('pointermove',mv);window.removeEventListener('pointerup',up);if(moved)root.__gest=Date.now()};
    window.addEventListener('pointermove',mv);window.addEventListener('pointerup',up);
  });
  /* --- borrar deslizando --- */
  const THR=104;
  const rub=a=>a<=THR?a:THR+(a-THR)*.42;
  const pillSet=(wrap,a,armedNow)=>{
    const pill=wrap.querySelector('.ed-delpill');if(!pill)return;
    pill.style.opacity=a>4?1:0;pill.style.width=Math.max(0,a-10)+'px';
    const armed=a>=THR;
    if(armed&&!pill.classList.contains('armed')){pill.classList.add('armed','pop');setTimeout(()=>pill.classList.remove('pop'),280)}
    if(!armed&&pill.classList.contains('armed'))pill.classList.remove('armed');
  };
  const swipeTo=(exEl,dx)=>{const row=exEl.querySelector('[data-row]'),wrap=exEl.querySelector('.ed-swwrap');const a=rub(Math.max(0,-dx));row.classList.remove('snap','out');row.style.transform=`translateX(${-a}px)`;pillSet(wrap,a)};
  const finalize=(ex,exEl)=>{
    clearTimeout(ex._to);const h=exEl.offsetHeight;exEl.style.height=h+'px';exEl.classList.add('gone');exEl.getBoundingClientRect();exEl.style.height='0px';exEl.style.opacity='0';
    setTimeout(()=>{const i=st.exs.indexOf(ex);if(i>=0)st.exs.splice(i,1);const ci=st.cardio.indexOf(ex);if(ci>=0)st.cardio.splice(ci,1);exEl.remove();if(st.pending&&st.pending.id===ex.id)st.pending=null;refreshSum()},280);
  };
  const commitDelete=(exEl)=>{
    const ex=exOf(exEl.dataset.id)||st.cardio.find(c=>c.id===exEl.dataset.id);if(!ex)return;
    if(st.pending&&st.pending.id!==ex.id){const pe=st.pending.ex,pel=root.querySelector(`.ed-ex[data-id="${pe.id}"]`);if(pel)finalize(pe,pel)}
    st.pending={id:ex.id,ex};
    const row=exEl.querySelector('[data-row]'),wrap=exEl.querySelector('.ed-swwrap'),pill=wrap.querySelector('.ed-delpill');
    if(ex.open){ex.open=false;exEl.classList.remove('open')}
    row.classList.remove('snap');row.classList.add('out');row.style.transform=`translateX(-${wrap.offsetWidth+8}px)`;
    pill.style.width=(wrap.offsetWidth-16)+'px';pill.style.transition='width .22s cubic-bezier(.2,.8,.2,1)';
    setTimeout(()=>{
      const u=wrap.querySelector('.ed-undo');u.classList.add('show');pill.style.opacity=0;
      const i=u.querySelector('.cd i');i.style.transition='none';i.style.setProperty('--cd',1);i.getBoundingClientRect();i.style.transition='transform 5s linear';i.style.setProperty('--cd',0);
    },230);
    tickMinutes(-ex.min);
    ex._to=setTimeout(()=>{if(st.pending&&st.pending.id===ex.id){st.pending.done=true;finalize(ex,exEl)}},5230);
  };
  const doUndo=()=>{
    if(!st.pending)return;const ex=st.pending.ex,exEl=root.querySelector(`.ed-ex[data-id="${ex.id}"]`);clearTimeout(ex._to);st.pending=null;
    if(!exEl)return;const row=exEl.querySelector('[data-row]'),wrap=exEl.querySelector('.ed-swwrap'),u=wrap.querySelector('.ed-undo'),pill=wrap.querySelector('.ed-delpill');
    u.classList.remove('show');pill.style.transition='none';pill.style.width='0px';pill.style.opacity=0;pill.classList.remove('armed');
    row.classList.remove('out');row.classList.add('snap');row.style.transform='translateX(0)';
    tickMinutes(ex.min);
  };
  const release=(exEl,a,vel)=>{
    const row=exEl.querySelector('[data-row]'),wrap=exEl.querySelector('.ed-swwrap');
    if(a>=THR||(vel>.9&&a>60))return commitDelete(exEl);
    row.classList.add('snap');row.style.transform='translateX(0)';
    const pill=wrap.querySelector('.ed-delpill');pill.style.transition='width .3s var(--ed-ease),opacity .2s';pillSet(wrap,0);setTimeout(()=>{pill.style.transition=''},320);
  };
  let sw=null;
  root.addEventListener('pointerdown',e=>{
    const row=e.target.closest('[data-row]');if(!row||e.target.closest('[data-grip],[data-scrub]'))return;
    const exEl=row.closest('.ed-ex');if(!exEl||(st.pending&&st.pending.id===exEl.dataset.id))return;
    sw={exEl,x:e.clientX,y:e.clientY,lock:false,t:performance.now(),lastx:e.clientX,lastt:performance.now(),vel:0,id:e.pointerId};
  });
  window.addEventListener('pointermove',e=>{
    if(!sw||e.pointerId!==sw.id)return;
    const dx=(e.clientX-sw.x)/k(),dy=(e.clientY-sw.y)/k();
    if(!sw.lock){
      if(Math.abs(dy)>10&&Math.abs(dy)>Math.abs(dx)){sw=null;return}
      if(dx<-8&&Math.abs(dx)>Math.abs(dy)*1.3){sw.lock=true;try{sw.exEl.querySelector('[data-row]').setPointerCapture(e.pointerId)}catch(_){}}
      else return;
    }
    const now=performance.now();sw.vel=Math.abs(e.clientX-sw.lastx)/Math.max(1,now-sw.lastt)/k();sw.lastx=e.clientX;sw.lastt=now;
    sw.dx=dx;swipeTo(sw.exEl,dx);
  });
  const swEnd=e=>{
    if(!sw||(e&&e.pointerId!==sw.id))return;
    const s=sw;sw=null;if(!s.lock)return;
    root.__gest=Date.now();
    const a=rub(Math.max(0,-(s.dx||0)));release(s.exEl,a,s.vel);
  };
  window.addEventListener('pointerup',swEnd);window.addEventListener('pointercancel',swEnd);
  /* --- arrastrar para ordenar --- */
  let dr=null;
  const dragBegin=(exEl,clientY)=>{
    const list=exEl.parentElement;
    const open=st.exs.find(x=>x.open);
    if(open){open.open=false;const oel=list.querySelector(`.ed-ex[data-id="${open.id}"]`);if(oel){oel.classList.remove('open');const cc=oel.querySelector('[data-card]');if(cc)cc.innerHTML=''}}
    const items=[...list.children].filter(c=>c.classList.contains('ed-ex'));
    const hs=items.map(c=>c.offsetHeight),tops=[];let acc=0;hs.forEach(h=>{tops.push(acc);acc+=h});
    list.style.height=acc+'px';list.classList.add('drag');list.closest('.ed-mod').classList.add('free');
    items.forEach((c,i)=>{c.style.transition='none';c.style.height=hs[i]+'px';c.style.transform=`translateY(${tops[i]}px)`});
    list.getBoundingClientRect();items.forEach(c=>c.style.transition='');
    const idx=items.indexOf(exEl),slot=document.createElement('div');slot.className='ed-slot';slot.style.height=hs[idx]+'px';slot.style.transform=`translateY(${tops[idx]}px)`;list.prepend(slot);
    exEl.classList.add('lifted');const pu=document.createElement('i');pu.className='ed-pulse';exEl.appendChild(pu);
    $('[data-bar]').classList.add('dim');
    const rect=exEl.getBoundingClientRect();
    dr={list,items,hs,total:acc,exEl,idx,slot,grab:clientY-rect.top,y:clientY,target:idx,tops};
    dr.raf=setTimeout(dragLoop,16);
    dragUpdate(clientY);
  };
  const dragUpdate=(clientY)=>{
    if(!dr)return;dr.y=clientY;
    const z=k(),lt=dr.list.getBoundingClientRect().top;
    const top=edClamp((clientY-dr.grab-lt)/z,-dr.hs[dr.idx]*.45,dr.total-dr.hs[dr.idx]*.55);
    dr.exEl.style.transform=`translateY(${top}px)`;
    const hd=dr.hs[dr.idx],center=top+hd/2;
    const others=dr.items.filter((_,i)=>i!==dr.idx);let accm=0,j=0;
    others.forEach(c=>{const h=dr.hs[dr.items.indexOf(c)];if(center>accm+h/2)j++;accm+=h});
    dr.target=j;let a=0,slotTop=0;
    others.forEach((c,n)=>{if(n===j){slotTop=a;a+=hd}c.style.transform=`translateY(${a}px)`;a+=dr.hs[dr.items.indexOf(c)]});
    if(j>=others.length)slotTop=a;
    dr.slot.style.transform=`translateY(${slotTop}px)`;dr.slotTop=slotTop;dr.curTop=top;
  };
  const dragLoop=()=>{
    if(!dr)return;
    const r=scroll.getBoundingClientRect(),z=k(),y=dr.y;
    const topZone=r.top+150*z,botZone=r.bottom-170*z;let v=0;
    if(y<topZone)v=-Math.min(16,(topZone-y)/(150*z)*16);else if(y>botZone)v=Math.min(16,(y-botZone)/(170*z)*16);
    if(v){scroll.scrollTop+=v;dragUpdate(dr.y)}
    dr.raf=setTimeout(dragLoop,16);
  };
  const dragEnd=(done)=>{
    if(!dr)return;const d=dr;clearTimeout(d.raf);
    d.exEl.classList.remove('lifted');d.exEl.classList.add('settle');d.exEl.style.transform=`translateY(${d.slotTop}px)`;
    const p=d.exEl.querySelector('.ed-pulse');if(p)p.remove();
    $('[data-bar]').classList.remove('dim');
    setTimeout(()=>{
      const moved=d.items[d.idx],others=d.items.filter((_,i)=>i!==d.idx);others.splice(d.target,0,moved);
      st.exs=others.map(c=>exOf(c.dataset.id));
      drawBody();dr=null;if(done)done();
    },360);
    dr=Object.assign({},d,{ending:true});
  };
  root.addEventListener('pointerdown',e=>{
    const g=e.target.closest('[data-grip]');if(!g||dr)return;
    const exEl=g.closest('.ed-ex');if(!exEl||exEl.dataset.kind!=='str'||(st.pending))return;
    const sx=e.clientX,sy=e.clientY,pid=e.pointerId;let started=false;
    const timer=setTimeout(()=>{started=true;root.__gest=Date.now();dragBegin(exEl,sy)},210);
    const mv=ev=>{
      if(ev.pointerId!==pid)return;
      if(!started){if(Math.hypot(ev.clientX-sx,ev.clientY-sy)>14){clearTimeout(timer);cleanup()}return}
      dragUpdate(ev.clientY);
    };
    const up=ev=>{clearTimeout(timer);cleanup();if(started){root.__gest=Date.now();dragEnd()}};
    const cleanup=()=>{window.removeEventListener('pointermove',mv);window.removeEventListener('pointerup',up);window.removeEventListener('pointercancel',up)};
    window.addEventListener('pointermove',mv);window.addEventListener('pointerup',up);window.addEventListener('pointercancel',up);
  });
  /* --- demostraciones (botones en las notas) y estados congelados --- */
  const sleep=ms=>new Promise(r=>setTimeout(r,ms));
  const raf=()=>new Promise(r=>setTimeout(r,16));
  const tween=async(ms,fn)=>{const t0=performance.now();for(;;){await raf();const p=Math.min(1,(performance.now()-t0)/ms);fn(1-Math.pow(1-p,3));if(p>=1)break}};
  const API={
    async demoSwipe(){
      let exEl=root.querySelector('.ed-ex[data-kind=str]:not(.gone)');if(!exEl||st.pending)return;
      if(st.exs.find(x=>x.open)){const o=st.exs.find(x=>x.open);o.open=false;root.querySelector(`.ed-ex[data-id="${o.id}"]`).classList.remove('open')}
      scroll.scrollTop=Math.max(0,exEl.offsetTop-200);await sleep(200);
      await tween(520,p=>swipeTo(exEl,-(70)*p));await sleep(420);
      await tween(200,p=>swipeTo(exEl,-(70)*(1-p*.5)));await sleep(200);
      await tween(380,p=>swipeTo(exEl,-(35+125*p)));await sleep(560);
      release(exEl,rub(160),0);
    },
    async demoDrag(){
      if(dr||st.pending)return;
      const list=$('[data-list]'),exEl=list.children[0].classList.contains('ed-slot')?list.children[1]:list.children[0];
      scroll.scrollTop=Math.max(0,exEl.offsetTop-160);await sleep(250);
      const r=exEl.getBoundingClientRect(),y0=r.top+r.height/2;
      dragBegin(exEl,y0);await sleep(420);
      await tween(1100,p=>dragUpdate(y0+p*150*k()));await sleep(450);
      await tween(900,p=>dragUpdate(y0+(150-p*90)*k()));await sleep(350);
      dragEnd();
    },
    freezeSwipe(dx){const exEl=root.querySelector('.ed-ex[data-kind=str]:not(.gone)');swipeTo(exEl,-dx)},
    freezeUndo(){const exEl=root.querySelectorAll('.ed-ex[data-kind=str]')[2];commitDelete(exEl);setTimeout(()=>{const i=exEl.querySelector('.cd i');i.style.transition='none';i.style.setProperty('--cd',.62);clearTimeout(exOf(exEl.dataset.id)._to)},420)},
    freezeDrag(){const list=$('[data-list]');const exEl=list.children[1];const r=exEl.getBoundingClientRect();dragBegin(exEl,r.top+r.height/2);dragUpdate(r.top+r.height/2+r.height*.66*k());clearTimeout(dr.raf)},
    open(kind,sel,w,est){const btn=root.querySelector(sel);if(!btn)return;const ex=st.exs.find(x=>x.open);const l=popAt(btn,w,est,kind,ex&&ex.id);openLayer(l)},
    openSheet(kind,id,extra){openLayer(Object.assign({kind,ex:id},extra||{}))},
  };
  phone.__ed=API;
  const dd=phone.closest('.stage')||document;
  dd.querySelectorAll('[data-ed]').forEach(b=>b.addEventListener('click',()=>{const f=API[b.dataset.ed];f&&f()}));
  /* estado inicial de cada escena */
  const cfg=st.cfg;
  if(cfg.scroll)scroll.scrollTop=cfg.scroll;
  if(cfg.after)cfg.after(API,st,root);
}

/* ---------- escenas ---------- */
function edScene(o){
  scene({id:o.id,tab:'editor',title:o.title,
    html:()=>edHTML(Object.assign({id:o.id},o.cfg||{})),
    notes:o.notes,
    after:(phone)=>{
      const st=edHTML.map[o.id];if(!st)return;
      edWire(phone,st);
      const root=phone.querySelector('.ed-root'),sc=root.querySelector('[data-scroll]');
      if(o.cfg&&o.cfg.focus){
        const el=root.querySelector(`.ed-ex[data-id="${o.cfg.focus}"]`);
        if(el){const z=root.offsetWidth?root.getBoundingClientRect().width/root.offsetWidth:1;sc.scrollTop+=(el.getBoundingClientRect().top-root.getBoundingClientRect().top)/z-(o.cfg.pad==null?102:o.cfg.pad)}
      }
      if(o.go)o.go(phone.__ed,st,root);
    }});
}
const edOpen=(id,sel,fn)=>st=>{const e=st.exs.find(x=>x.id===id);e.open=true;e.sel=sel||0;if(fn)fn(e,st)};
const edTags=(...a)=>({tags:a});

/* 1 · lista y píldora */
edScene({id:'ed-lista',title:'Lista · reposo',
  notes:{tags:[['new','píldora y barra en vidrio'],['keep','mismas filas y funciones']],
    img:'h13-editor-lista',imgCap:'Hoy: la píldora de arriba es opaca y corta el contenido; la barra de abajo parece un texto suelto.',
    p:['El contenido ya no se corta bajo las dos píldoras: pasa por debajo y se ve desenfocado a través del vidrio. Arriba, una sola isla con <b>atrás · ‹ día ▾ › · ⋯</b>; abajo, otra con <b>minutos · Añadir · Asistente</b>.',
       'Todo responde: las flechas cambian de día (el texto entra por el lado del día), tocar el día abre la semana, ⋯ abre su menú, una fila se despliega al tocarla, se borra deslizando y se ordena con el asa. Prueba también «Añadir».'],
    list:['Las filas conservan asa, miniatura, nombre, dosis, estrella de Meta y flecha (las miniaturas reales son imágenes del catálogo; aquí, un glifo).','«Cardio en la sesión · Al inicio / Al final» sigue donde estaba.','Al arrastrar para ordenar, la barra de abajo se atenúa y no recibe toques (así no se suelta nada encima por error).']}});
edScene({id:'ed-lista-scroll',title:'Lista · con scroll (vidrio)',cfg:{scroll:300},
  notes:{tags:[['new','vidrio con desenfoque real']],
    p:['Con la lista desplazada se ve lo que pide el punto 12: las filas pasan <b>por debajo</b> de la isla superior y de la barra inferior, desenfocadas y con algo de saturación, sin que nada quede cortado en seco.',
       'Para que el reloj y los iconos del sistema se lean siempre, hay un degradado negro muy corto solo en la franja de estado; la isla no lleva velo.'],
    list:['Isla superior: 56 px de alto, radio 30, fondo #1B1B1D al 60 %, desenfoque 24 px, filete blanco al 13 %.','Barra inferior: 64 px, mismas propiedades; la lista tiene 128 px de margen inferior para que el último elemento quede libre.','Con «Reducir transparencia» activado en Android, ambas pasan a gris plano #1B1B1D (sin desenfoque).']}});
edScene({id:'ed-dia',title:'Selector de día (semana)',
  go:(a)=>a.openSheet('day'),
  notes:{tags:[['keep','contenido de la hoja de hoy'],['new','píldora de día con flechas animadas']],
    p:['Tocar el día abre la hoja de la semana, con lo mismo que tiene hoy: los siete días (con punto si hay sesión, «C» si hay competencia, número si hay varias), las sesiones del día con «Marcar como principal», las variantes y las semanas del programa.',
       'La píldora rota la flecha ▾ mientras la hoja está abierta. Cuando cambias de día con las flechas, el texto entra deslizando 220 ms por el lado del día y el contenido lo sigue.'],
    list:['Datos de muestra: los nombres de sesiones y semanas son de ejemplo.','Cerrar con el fondo, con el gesto de bajar o eligiendo un día.']}});
edScene({id:'ed-pildora-mas',title:'Píldora · menú «⋯»',cfg:{scroll:330},
  go:(a,st,root)=>root.querySelector('[data-a=more]').click(),
  notes:{tags:[['keep','Transferir y Versiones']],
    p:['El menú de los tres puntos conserva sus dos opciones, «Transferir» y «Versiones», en un popover de vidrio anclado al botón. Detrás sigue pasando la lista.']}});

/* 2 · barra inferior */
edScene({id:'ed-anadir',title:'Barra · «Añadir» desplegado',
  go:(a,st,root)=>root.querySelector('[data-a=addmenu]').click(),
  notes:{tags:[['new','Añadir con menú'],['move','«Reglas» pasa al Asistente'],['keep','Asistente con su número']],
    img:'h07-cardio-tarjeta',imgCap:'Hoy: un anillo con un número suelto, «Añadir» y dos textos sin forma.',
    p:['<b>Añadir</b> es un botón partido: el cuerpo abre el catálogo de ejercicios (igual que hoy, un toque) y la flecha ▴ despliega las otras formas de añadir: <b>Ejercicio</b>, <b>Cardio</b> y <b>Grupo nuevo</b>, que hoy solo existen como enlaces al final de la lista.',
       '<b>Asistente</b> pasa a un círculo con su icono y la cantidad de sugerencias; «Reglas» ya no está en la barra porque pasa a ser la 4.ª pestaña del Asistente.',
       'El tiempo deja de ser un anillo con un número: es un anillo de progreso con <b>«81 min · de 90»</b> al lado. Prueba «Ejercicio» o «Cardio» y mira cómo suben los minutos y cambia el color.'],
    list:['Color del anillo (misma lógica que hoy): verde por debajo del 85 % del límite, amarillo hasta el 108 %, rojo por encima, gris si no hay límite.','Si Espacio de cardio o de fuerza no existen, el menú ofrece «Espacio de cardio» / «Fuerza» (como las píldoras actuales del final de la lista).','Tocar los minutos abre el tiempo de la sesión; hoy vive en la hoja de Reglas (pestaña Tiempo), que desaparece de la barra: ver dudas.']}});

scene({id:'ed-barra',tab:'editor',title:'Barra · estados del tiempo',
  html:()=>{
    const mk=(label,total,limit,sugg)=>`<div class="it"><div class="ed-eyebrow">${label}</div><div style="padding:14px 0;border-radius:24px;background:linear-gradient(100deg,#f49a6e55,#8fb2ff44 55%,#43d18c33)"><div class="ed-bar ed-glass" style="position:relative;left:auto;right:auto;bottom:auto;margin:0 -4px">${edBarHTML({limit,sugg,layer:null},total)}</div></div></div>`;
    return `<div class="ed-root"><div class="ed-scroll"><div class="ed-spec">${mk('Sin ejercicios',0,null,0)}${mk('En reposo · 3 sugerencias',52,90,3)}${mk('Cerca del límite (85 % o más)',80,90,3)}${mk('Límite superado (más del 108 %)',100,90,3)}${mk('Sin límite de duración',59,null,0)}</div></div><div class="ed-topfade"></div></div>`;
  },
  notes:{tags:[['new','minutos legibles'],['ask','Asistente solo con icono']],
    p:['Los estados del tiempo en la barra, de arriba abajo: sin ejercicios (0 min, sin límite), en reposo con tres sugerencias, cerca del límite, límite superado y sin límite. Detrás de cada isla hay un fondo de color solo para que se vea el vidrio. El anillo se llena según el límite y el número que lo acompaña dice los minutos de verdad.',
       'Cómo se comporta al hacer scroll: <b>no se esconde</b>. El editor es una superficie de trabajo y esconder «Añadir» al desplazarse obliga a volver a buscarlo. Con el teclado abierto baja fuera de la vista; al arrastrar una fila se atenúa.'],
    list:['Misma altura que hoy: 64 px; objetivos táctiles de 48 px.','Al 130 % de letra, el texto de minutos crece y «Añadir» cede ancho; el círculo del Asistente no cambia.']}});

/* 3 · borrar deslizando */
edScene({id:'ed-borrar',title:'Borrar · deslizar (en vivo)',cfg:{scroll:60},
  notes:{tags:[['new','gesto rehecho'],['keep','5 s para deshacer']],
    img:'h12-swipe-eliminar',imgCap:'Hoy: la fila se parte, el fondo se corta y el aviso aparece en otro módulo.',
    p:['<button class="ed-demo" data-ed="demoSwipe">▶ Ver el gesto completo</button>',
       '<b>Reposo</b>: la fila no muestra nada. <b>Arrastre parcial</b>: la fila sigue al dedo y, detrás, crece una píldora oscura desde el borde derecho con la papelera; la papelera aún va sola. <b>Umbral superado</b> (104 px): la píldora se vuelve rosa, la papelera salta un 22 %, aparece la palabra «Eliminar» y hay un tic de vibración. Más allá del umbral la fila ofrece resistencia (no llega a salirse).',
       '<b>Salida</b>: al soltar, la fila sale entera hacia la izquierda en 220 ms y la píldora rellena todo el hueco. <b>Aviso</b>: sobre ese mismo hueco aparece «Ejercicio eliminado · Deshacer» con la cuenta atrás de 5 s en una línea fina que se vacía. <b>Colapso</b>: al acabar la cuenta, la fila se pliega suavemente (260 ms) y las de abajo suben. «Deshacer» devuelve la fila deslizándola de vuelta con rebote.',
       'Arrastra de verdad cualquier fila hacia la izquierda con el ratón o el dedo.'],
    list:['Si sueltas antes del umbral (sin velocidad), la fila vuelve con un rebote suave (340 ms).','Solo hay un borrado pendiente a la vez, como hoy: borrar otro confirma el anterior.','Accesibilidad: acción «Eliminar ejercicio» para TalkBack; el aviso se anuncia como región educada.','Los minutos de la barra bajan al borrar y suben si deshaces.']}});
edScene({id:'ed-borrar-arrastre',title:'Borrar · arrastre parcial',cfg:{scroll:60},
  go:(a)=>a.freezeSwipe(60),
  notes:{tags:[['new','estado congelado']],p:['Arrastre parcial (60 px): la fila va con el dedo; detrás, la píldora oscura con la papelera crece desde el borde. Todavía no se activa nada: soltar aquí devuelve la fila a su sitio.']}});
edScene({id:'ed-borrar-umbral',title:'Borrar · umbral superado',cfg:{scroll:60},
  go:(a)=>a.freezeSwipe(150),
  notes:{tags:[['new','estado congelado']],p:['Umbral superado: la píldora se enciende en el color de peligro, la papelera salta y aparece la palabra «Eliminar». Es la única señal fuerte de todo el gesto, por eso el resto es discreto.']}});
edScene({id:'ed-borrar-aviso',title:'Borrar · aviso con cuenta atrás',cfg:{scroll:60},
  go:(a)=>a.freezeUndo(),
  notes:{tags:[['new','el aviso ocupa el hueco de la fila'],['keep','Deshacer 5 s']],p:['El aviso queda donde estaba la fila, con la misma altura, y su cuenta atrás es una línea de 2 px que se vacía (aquí, al 62 %). Con movimiento reducido, la línea queda quieta a media altura y el colapso es un fundido.']}});

/* 4 · ordenar */
edScene({id:'ed-ordenar',title:'Ordenar · arrastrar (en vivo)',cfg:{scroll:60},
  notes:{tags:[['new','gesto rehecho'],['keep','asa a la izquierda']],
    img:'h13-editor-lista',imgCap:'Hoy: sin hueco ni asentamiento; la fila se vuelve casi transparente mientras se arrastra.',
    p:['<button class="ed-demo" data-ed="demoDrag">▶ Ver el gesto completo</button>',
       '<b>Levantar</b>: mantén pulsada el asa (200 ms). La fila sube a una capa gris #1B1B1D, escala 1,025 con un pequeño rebote, gana sombra y un anillo de tinta que se expande una vez (la vibración visual del tic háptico). <b>Hueco</b>: donde estaba la fila queda un hueco punteado y las demás se apartan 260 ms para abrir el sitio al que irá. <b>Autoscroll</b>: cerca del borde de arriba o de abajo, la lista se desplaza sola, más rápido cuanto más cerca. <b>Soltar</b>: la fila se asienta en el hueco con rebote (340 ms), la sombra se apaga y la barra de abajo recupera su opacidad.',
       'Si hay un ejercicio desplegado al levantar otro, se pliega para poder ver todas las filas a la vez.'],
    list:['Las filas se miden de verdad (nombres de 1 o 2 líneas): el hueco tiene siempre la altura exacta de la fila levantada.','Cancelar: soltar fuera de la lista deja todo como estaba.','Soltar sobre un grupo o fuera de grupo sigue funcionando como hoy (no se dibuja aquí).']}});
edScene({id:'ed-ordenar-levantado',title:'Ordenar · fila levantada',cfg:{scroll:60},
  go:(a)=>a.freezeDrag(),
  notes:{tags:[['new','estado congelado']],p:['Fila levantada a media bajada: la fila sube sobre las demás, el hueco punteado marca dónde caerá y la barra de abajo se atenúa.']}});

/* 5 · series */
const edSerieBase={focus:'e1',pad:96};
edScene({id:'ed-serie',title:'Serie · tarjeta abierta',cfg:Object.assign({},edSerieBase,{mod:edOpen('e1',1)}),
  notes:{tags:[['new','una sola fila por dato'],['keep','todas las opciones de hoy']],
    img:'h14-apre',imgCap:'Hoy: Modo, Descanso y ⋯ en píldoras desiguales; dentro de la serie, cuatro botones de carga y tres de intensidad.',
    p:['La serie abierta se lee de arriba abajo, sin cajas dentro de cajas: <b>Carga</b> (tipo + valor), <b>Repeticiones</b>, <b>Intensidad</b> (unidad dentro del campo), <b>Descanso</b> y <b>acciones</b>. Cada dato lleva su rótulo y una frase corta que explica de dónde sale.',
       '<b>Carga</b>: por defecto «Externa»; los otros tres tipos viven en el menú de esa misma píldora. La cifra grande es la estimada con la carga inteligente y es un botón: lleva a esa hoja (el módulo «Carga inteligente» de hoy se integra aquí).',
       '<b>Repeticiones</b>: un campo con − y +, y el valor se arrastra de lado a lado para cambiarlo rápido. «Fijas | Rango» es un conmutador pequeño en la misma línea.',
       '<b>Intensidad</b>: un solo campo; la unidad (RPE, RIR o Fallo) está dentro, a la izquierda, y se cambia tocándola. <b>Descanso</b>: un campo con −15 s / +15 s y, al tocar el tiempo, atajos de 0:30 a 5:00.'],
    list:['Series: las mismas fichas de 85 × 76 px; «+ Añadir serie» pasa a ser la última ficha (punteada).','Aplicar a todas · Duplicar son botones con icono; Subir, Bajar y Eliminar viven en «⋯» (Eliminar, en rosa y separado).','Toca cualquier ficha o usa las flechas para pasar de serie; las filas de abajo cambian su dosis a la vez.']}});
edScene({id:'ed-serie-carga',title:'Serie · menú de tipo de carga',cfg:Object.assign({},edSerieBase,{mod:edOpen('e1',1)}),
  go:(a)=>a.open('ctype','[data-a=ctype]',280,300),
  notes:{tags:[['new','4 botones → 1 menú'],['keep','Externa · Corporal · Lastre · Asistido']],
    p:['Los cuatro botones enormes se sustituyen por <b>«Externa ⌄»</b>. Al tocarla se despliega el menú con las otras tres, cada una con una línea que dice qué cambia en el campo de carga:',
       'Qué cambia al elegir cada una (lo que ya hace el motor): <b>Externa</b> estima kg con tu 1RM. <b>Corporal</b> no usa kg (la carga es 0 y la cifra pasa a decir «Peso corporal»). <b>Lastre</b> estima kg añadidos a partir de tu récord con lastre. <b>Asistido</b> estima kg de ayuda a partir de tu récord asistido.',
       'Pruébalo: al elegir otro tipo, la cifra y la frase de la derecha cambian al instante, y la ficha de la serie también.'],
    list:['El menú se abre hacia abajo; si no cabe, hacia arriba.','Las descripciones son texto nuevo, hecho a partir de cómo calcula cada tipo.']}});
edScene({id:'ed-serie-rango',title:'Serie · repeticiones en rango',cfg:Object.assign({},edSerieBase,{mod:edOpen('e1',1,e=>{const s=e.sets[1];s.range=true;s.rmin=8;s.rmax=10;s.iu='RIR';s.iv=2})}),
  notes:{tags:[['new','rango con dos campos'],['keep','8–10 como hoy']],
    p:['Al pasar a <b>Rango</b>, el campo se parte en dos: <b>mín</b> y <b>máx</b>, cada uno con sus − y + (el mínimo nunca supera al máximo). Se guarda igual que hoy: máximo como objetivo y el rango aparte; la ficha y la fila muestran «8–10».',
       'Volver a «Fijas» deja el máximo como número fijo. El teclado sigue disponible: tocar una cifra la edita («8-10» también se entiende).'],
    list:['Aquí la intensidad está en RIR 2, para ver otra unidad.']}});
edScene({id:'ed-serie-intensidad',title:'Serie · unidad de intensidad',cfg:Object.assign({},edSerieBase,{mod:edOpen('e1',1)}),
  go:(a)=>a.open('iunit','[data-a=iunit]',280,230),
  notes:{tags:[['new','RPE / RIR / Fallo dentro del campo']],
    img:'h14-apre',imgCap:'Hoy hay un segmentado «RPE · RIR · Fallo» a todo el ancho, más la celda del valor, más «+ Intensidad».',
    p:['Decisión: <b>una unidad conmutable dentro del campo</b>. El campo muestra «RPE ⌄ − 8 +»; al tocar la unidad se abre un menú de tres opciones con una línea cada una (RPE: esfuerzo de 1 a 10 · RIR: reps en reserva · Fallo: hasta no poder más).',
       'Por qué no un deslizador ni una rueda: el RPE necesita medios puntos y el RIR enteros, el teclado ya se usa para escribirlos y el campo con − / + es el mismo patrón que Repeticiones y Descanso. Cambiar de unidad mantiene lo coherente: RPE 8 ↔ RIR 2 (como hace hoy el código).',
       'Sin intensidad (opcional) el campo es un botón punteado «Añadir intensidad», como el «+ Intensidad» de hoy.'],
    list:['Pasos: RPE de 0,5 en 0,5 (1–10); RIR de 1 en 1 (0–5).','Con unidad Fallo no hay valor y se muestra «Hasta el fallo» con una llama.']}});
edScene({id:'ed-serie-fallo',title:'Serie · Fallo y técnicas',cfg:Object.assign({},edSerieBase,{pad:-120,mod:edOpen('e1',0,e=>{const s=e.sets[0];s.iu='FALLO';s.drop=2})}),
  notes:{tags:[['keep','«¿Vas más allá del fallo?»'],['new','se integra en el campo de intensidad']],
    p:['Al elegir <b>Fallo</b> el campo ya no tiene valor y, debajo, aparece lo que hoy aparece: «¿Vas más allá del fallo?» con <b>Drop-set</b>, <b>Rest-pause</b> y <b>Myo-reps</b> (tres tarjetas de igual tamaño, cada una con su resumen), y al elegir una, su configuración: drops 1–3, mini-series 1–5, y para Myo-reps las repeticiones (3–5) y la pausa corta (10–30 s).',
       'Es el único caso en que el panel crece; al salir de Fallo se pliega y las técnicas se quitan, como hoy.']}});
edScene({id:'ed-serie-descanso',title:'Serie · descanso editándose',cfg:Object.assign({},edSerieBase,{pad:-250,mod:edOpen('e1',1,e=>{e.sets[1].rest=120;e.restOpen=true})}),
  notes:{tags:[['new','descanso interactivo'],['keep','propio de la serie o heredado']],
    img:'h14-apre',imgCap:'Hoy: «Descanso de esta serie» con un − y un + sueltos y el tiempo como texto.',
    p:['El descanso es un campo igual que los demás: <b>−15 · ⏱ 2:00 · +15</b>. Se arrastra el tiempo de lado a lado para ajustarlo (cada 16 px, 15 s), o se toca para abrir los <b>atajos</b> 0:30 · 1:00 · 1:30 · 2:00 · 2:30 · 3:00 · 4:00 · 5:00.',
       'El rótulo de arriba a la derecha dice de dónde viene: <b>«Igual que el ejercicio»</b> si la serie hereda, o <b>«Propio · Restablecer»</b> si la serie lo cambió; el último atajo (con el valor del ejercicio) también restablece.'],
    list:['El descanso del ejercicio (la píldora ⏱ de arriba) abre una hoja con los mismos controles: ver escena «Descanso del ejercicio».']}});
edScene({id:'ed-serie-acciones',title:'Serie · menú de acciones',cfg:Object.assign({},edSerieBase,{pad:-250,mod:edOpen('e1',1)}),
  go:(a)=>a.open('smenu','[data-a=smenu]',220,200),
  notes:{tags:[['new','acciones con forma'],['keep','Aplicar · Duplicar · Subir · Bajar · Eliminar']],
    img:'h14-apre',imgCap:'Hoy, al abrir una serie, cinco textos sueltos.',
    p:['Las acciones se reparten por jerarquía: las dos de uso frecuente, <b>Aplicar a todas</b> y <b>Duplicar</b>, son botones con icono y forma de pastilla; <b>Subir</b> y <b>Bajar</b> pasan a un menú «⋯» y, <b>separada por una línea</b>, <b>Eliminar serie</b> en rosa (sin fondo de alarma: se distingue sin gritar).',
       'Subir se desactiva en la primera serie, Bajar en la última, y Eliminar cuando solo queda una («Siempre queda una serie»).'],
    list:['Duplicar solo aparece si la pantalla permite transformar el ejercicio, como hoy.','Eliminar sigue siendo inmediato, como hoy; proponer «Serie eliminada · Deshacer» sería una función nueva (ver dudas).']}});
edScene({id:'ed-serie-aplicar',title:'Serie · confirmar «Aplicar a todas»',cfg:Object.assign({},edSerieBase,{pad:-250,mod:edOpen('e1',1)}),
  go:(a)=>a.openSheet('apply','e1'),
  notes:{tags:[['keep','misma confirmación']],p:['«Aplicar a todas» pregunta antes de copiar esta serie sobre las demás y dice cuántas cambian. Al confirmar, las fichas parpadean una vez para que se vea el efecto. Si ninguna serie cambiaría, la acción no hace nada (como hoy).']}});

/* 6 · modos y hojas del ejercicio */
edScene({id:'ed-modo',title:'Modo del ejercicio (hoja)',cfg:Object.assign({},edSerieBase,{mod:edOpen('e1',1)}),
  go:(a)=>a.openSheet('modo','e1',{sel:'REPS'}),
  notes:{tags:[['keep','los 7 modos de hoy']],
    p:['La hoja de modos conserva sus siete opciones con la línea que dice qué es cada una. Elegir uno solo lo marca; el botón de abajo confirma («Listo» si no cambia nada, «Aplicar AMRAP» si cambia). Pruébalo: al aplicar un modo, la serie de abajo cambia de forma.']}});
edScene({id:'ed-serie-amrap',title:'Serie · modo AMRAP',cfg:Object.assign({},edSerieBase,{mod:edOpen('e1',1,e=>{e.mode='AMRAP'})}),
  notes:{tags:[['keep','mínimo + etiqueta AMRAP']],p:['En AMRAP el campo es «Reps mín.» y a su lado va la etiqueta AMRAP (tocarla alterna «AMRAP calibrador», como en la serie real). No hay intensidad: el esfuerzo es llegar al límite.']}});
edScene({id:'ed-serie-rm',title:'Serie · modo RM',cfg:Object.assign({},edSerieBase,{mod:edOpen('e1',1,e=>{e.mode='RM'})}),
  notes:{tags:[['keep','% del RM con deslizador']],p:['En RM la carga es un porcentaje de tu máximo: el campo «% del RM» y el deslizador de 45 a 100 % mueven lo mismo, y la cifra de carga y las reps máximas estimadas se recalculan. Mismo patrón de campo que el resto; el deslizador conserva la tinta de la app.']}});
edScene({id:'ed-serie-solorpe',title:'Serie · modo Solo RPE',cfg:Object.assign({},edSerieBase,{mod:edOpen('e1',1,e=>{e.mode='SOLO_RPE'})}),
  notes:{tags:[['keep','sin carga fija']],p:['Solo RPE no tiene carga ni intensidad aparte: el campo «RPE objetivo» (con deslizador de 1 a 10 en pasos de 0,5) es todo lo que hace falta. Desaparecen el tipo de carga y la estimación, como hoy.']}});
edScene({id:'ed-serie-tiempo',title:'Serie · modo Tiempo',cfg:{focus:'e6',pad:96,mod:edOpen('e6',0)},
  notes:{tags:[['keep','minutos y segundos + probar cronómetro']],p:['En Tiempo la dosis son dos campos, <b>min</b> y <b>s</b> (los segundos de 5 en 5), y debajo «Probar cronómetro»: cuenta atrás de verdad con la duración puesta, para ver cómo se cronometrará en vivo. La intensidad y el tipo de carga siguen disponibles.',
    'El descanso de este ejercicio (1:30) aparece como el de las demás series.']}});
edScene({id:'ed-serie-distancia',title:'Serie · modo Distancia',cfg:{focus:'e6',pad:96,mod:edOpen('e6',0,e=>{e.mode='DISTANCE'})},
  notes:{tags:[['keep','metros por serie']],p:['Distancia y Personalizado usan un solo campo de valor (en metros de 5 en 5; en Personalizado, la unidad que definas en «Unidad personalizada»). El resto de la serie es igual.']}});

/* 7 · preparación, lados y hojas del ejercicio */
edScene({id:'ed-preparacion',title:'Aproximación y movilidad',
  cfg:{focus:'e1',pad:96,mod:edOpen('e1',1,e=>{e.warm=[{pct:40,reps:5,rest:60},{pct:60,reps:3,rest:60}];e.warmSel=0;e.mob=[{name:'Cadera',sec:45},{name:'Tobillo',sec:60}]})},
  notes:{tags:[['keep','mismos bloques y fichas'],['new','mismas piezas que la serie']],
    p:['La preparación vive dentro del ejercicio, como hoy: <b>Movilidad</b> y <b>Aproximación</b> son bloques con su contador y su ajuste común en la cabecera («Total · 2 min», «Descanso común 1:00»), con las mismas fichas de 85 × 76 que las series. Lo que falta sigue siendo una píldora punteada.',
       'La aproximación abierta (A1) usa los mismos campos con − y + que la serie (carga en % y reps), con la carga sugerida debajo y «Quitar · Listo».'],
    list:['Máximo recomendado de aproximaciones según las series efectivas, como hoy.','Datos de muestra (los nombres de movilidad son de ejemplo).']}});
edScene({id:'ed-unilateral',title:'Ejercicio unilateral (L / R)',
  cfg:{focus:'e5',pad:96,mod:edOpen('e5',1)},
  notes:{tags:[['keep','lados, orden y descanso entre lados']],
    p:['Un ejercicio unilateral duplica cada serie en dos fichas, L y R (sin rojo ni azul), y falta una: «R3» aparece como ficha punteada para añadirla. Debajo de las opciones, la fila de <b>Lados</b>: orden L → R / R → L, <b>lados iguales / aparte</b> (si son iguales, lo que editas en uno se copia al otro) y <b>Entre lados 0:15</b>, que abre la hoja de descanso encadenada.',
       'La píldora de descanso del ejercicio dice «Series 3:00 · Lados 0:15».']}});
edScene({id:'ed-opciones',title:'Opciones del ejercicio (⋯)',cfg:Object.assign({},edSerieBase,{mod:edOpen('e1',1)}),
  go:(a)=>a.openSheet('exmenu','e1'),
  notes:{tags:[['keep','las 8 opciones de hoy']],
    p:['El menú «⋯» del ejercicio reúne lo que no cabe en la barra: lateralidad, bloque APRE (solo en Reps, RM y AMRAP), medir ROM (se activa y desactiva ahí mismo), relacionar ejercicio, superserie, unidad personalizada, carga inteligente (no en Solo RPE) y meta. Cada fila lleva su icono y, la lateralidad, su estado.']}});
edScene({id:'ed-descanso-ejercicio',title:'Descanso del ejercicio (hoja)',cfg:Object.assign({},edSerieBase,{mod:edOpen('e1',1)}),
  go:(a)=>a.openSheet('restex','e1'),
  notes:{tags:[['new','mismo campo que la serie'],['keep','atajos de hoy']],
    p:['La hoja del descanso del ejercicio usa el mismo campo −15 · tiempo · +15 que la serie, con los atajos que tiene hoy (0:15 · 0:30 · 0:45 · 1:00 · 1:30 · 2:00 · 3:00). En un ejercicio unilateral añade «Entre lados» con sus atajos (5 · 10 · 15 · 30 s) en la misma hoja.']}});
