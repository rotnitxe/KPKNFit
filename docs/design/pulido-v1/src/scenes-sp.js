/* ===== Paquete SUPLEMENTOS · prefijo sp · pestaña suplementos ===== */
Object.assign(P,{
 spCaps:'<g transform="rotate(-40 12 12)"><rect x="3" y="8.5" width="18" height="7" rx="3.5"/><path d="M12 8.5v7"/></g>',
 spSun:'<circle cx="12" cy="12" r="3.6"/><path d="M12 3.5v2M12 18.5v2M3.5 12h2M18.5 12h2M6 6l1.4 1.4M16.6 16.6L18 18M18 6l-1.4 1.4M7.4 16.6L6 18"/>',
 spMoon:'<path d="M19 14.5A7.5 7.5 0 019.5 5a7.5 7.5 0 109.5 9.5z"/>',
 spLeaf:'<path d="M5 19c0-8 5-13 14-14 0 9-5 14-13 14"/><path d="M5 19l8-8"/>',
 spShake:'<path d="M8 8h8l-1 12H9z"/><path d="M9 8V5.5h6V8M10.5 3.5h3M9.6 13h4.8"/>',
 spFish:'<path d="M3.5 12c3-4.5 8-5.5 12-3l3.5-2.5v11L15.5 15c-4 2.5-9 1.5-12-3z"/><circle cx="8.5" cy="11.2" r=".6" fill="currentColor"/>',
 spCube:'<path d="M12 3.5l7.5 4v9L12 20.5l-7.5-4v-9z"/><path d="M12 12l7.5-4.5M12 12L4.5 7.5M12 12v8.5"/>',
 spLyte:'<path d="M12 3.5s5.5 5.8 5.5 9.5a5.5 5.5 0 01-11 0C6.5 9.3 12 3.5 12 3.5z"/><path d="M12.8 10l-2.3 3.4h2.6l-.8 2.8"/>',
 spMulti:'<circle cx="8" cy="9" r="3.5"/><circle cx="16" cy="9.5" r="3"/><circle cx="12" cy="16.5" r="3.5"/>',
 spAtom:'<circle cx="12" cy="12" r="1.5"/><ellipse cx="12" cy="12" rx="8.5" ry="3.6"/><ellipse cx="12" cy="12" rx="8.5" ry="3.6" transform="rotate(60 12 12)"/>',
 spHex:'<path d="M12 3.5l7 4v9l-7 4-7-4v-9z"/>',
 spCitrus:'<circle cx="12" cy="12" r="8"/><path d="M12 4v16M4 12h16"/>',
 spWarn:'<path d="M12 4l9 15.5H3z"/><path d="M12 10v4.5M12 17.2v.01"/>',
});
const MOM=['Al despertar','Con el desayuno','Con la comida','Antes de entrenar','Tras entrenar','Con la cena','Antes de dormir'];
const NOW='12:55';
// Catálogo propuesto (new). Criterio: ISSN (rendimiento) y NIH ODS / EFSA (micronutrientes); sin marcas ni mezclas propietarias.
const CAT=[
 {id:'creatina',n:'Creatina monohidrato',s:'Creatina',g:0,ic:'spCube',u:'g',dose:5,step:1,min:1,max:30,typ:'3–5 g al día',ev:'sólida',m:1,cap:null,capTxt:'Sin techo fijo: 3–5 g al día es lo estudiado.',feeds:'Suma a la creatina del día y a su racha. No cuenta como vitamina ni mineral.',hasNut:false},
 {id:'cafeina',n:'Cafeína',s:'Cafeína',g:0,ic:'bolt',u:'mg',dose:100,step:25,min:25,max:400,typ:'3 mg por kg: 100–200 mg',ev:'sólida',m:3,cap:400,capTxt:'Máximo 400 mg/día (6 mg/kg, tope 400 mg EFSA/FDA).',feeds:'Suma a la cafeína del día, junto con la de tus comidas y bebidas.',hasNut:true},
 {id:'proteina',n:'Proteína en polvo (whey o vegetal)',s:'Proteína',g:0,ic:'spShake',u:'g',dose:25,step:5,min:5,max:60,typ:'20–40 g por toma',ev:'sólida',m:4,cap:null,capTxt:'Sin techo propio: cuenta dentro de tu proteína del día.',feeds:'Suma proteína y calorías a tu día (estimadas por gramo).',hasNut:true},
 {id:'betaalanina',n:'Beta-alanina',s:'Beta-alanina',g:0,ic:'spCaps',u:'g',dose:1.6,step:0.4,min:0.4,max:6.4,typ:'3,2–6,4 g al día, repartidos',ev:'moderada',m:2,cap:null,capTxt:'Hasta 6,4 g/día en lo estudiado. El hormigueo es habitual.',feeds:'Solo registro: no cambia tu huella de nutrientes.',hasNut:false},
 {id:'citrulina',n:'Citrulina (L-citrulina)',s:'Citrulina',g:0,ic:'spCitrus',u:'g',dose:6,step:1,min:1,max:12,typ:'6–8 g antes de entrenar',ev:'moderada',m:3,cap:null,capTxt:'Sin techo fijo: 6–8 g es lo estudiado.',feeds:'Solo registro: no cambia tu huella de nutrientes.',hasNut:false},
 {id:'electrolitos',n:'Electrolitos',s:'Electrolitos',g:0,ic:'spLyte',u:'porción',pl:'porciones',noun:true,dose:1,step:1,min:1,max:4,typ:'1 porción en esfuerzos largos o con calor',ev:'moderada',m:3,cap:null,capTxt:'Depende de la etiqueta; el sodio ya cuenta en tu día.',feeds:'Suma sodio, potasio y magnesio según la etiqueta que configures.',hasNut:true},
 {id:'vitd',n:'Vitamina D',s:'Vitamina D',g:1,ic:'spSun',u:'µg',dose:10,step:5,min:5,max:100,typ:'10–25 µg (400–1.000 UI)',ev:'según necesidad',m:1,cap:100,capTxt:'Límite superior 100 µg/día (NIH, EFSA).',feeds:'Suma a Vitamina D en vitaminas y minerales. Se compara con el límite total.',hasNut:true},
 {id:'omega3',n:'Omega-3 (EPA + DHA)',s:'Omega-3',g:1,ic:'spFish',u:'g',dose:1,step:0.25,min:0.25,max:5,typ:'0,25–2 g de EPA + DHA',ev:'moderada',m:2,cap:5,capTxt:'Hasta 5 g/día de EPA + DHA en suplemento (EFSA).',feeds:'Suma a Omega-3 EPA + DHA en vitaminas y minerales.',hasNut:true},
 {id:'magnesio',n:'Magnesio',s:'Magnesio',g:1,ic:'spAtom',u:'mg',dose:300,step:50,min:50,max:700,typ:'200–400 mg',ev:'según necesidad',m:6,cap:350,capTxt:'Límite 350 mg/día para suplementos (NIH).',feeds:'Suma a Magnesio. Se compara con el límite para suplementos, no con el total.',hasNut:true},
 {id:'zinc',n:'Zinc',s:'Zinc',g:1,ic:'spHex',u:'mg',dose:10,step:5,min:2.5,max:40,typ:'8–15 mg',ev:'según necesidad',m:5,cap:40,capTxt:'Límite superior 40 mg/día, sumando comida y suplementos.',feeds:'Suma a Zinc en vitaminas y minerales. Se compara con el límite total.',hasNut:true},
 {id:'hierro',n:'Hierro',s:'Hierro',g:1,ic:'drop',u:'mg',dose:14,step:2,min:2,max:45,typ:'Solo si tu analítica lo indica',ev:'según necesidad',m:0,cap:45,capTxt:'Límite superior 45 mg/día, sumando comida y suplementos.',feeds:'Suma a Hierro en vitaminas y minerales. Se compara con el límite total.',hasNut:true},
 {id:'multi',n:'Multivitamínico',s:'Multivitamínico',g:1,ic:'spMulti',u:'comprimido',pl:'comprimidos',noun:true,dose:1,step:1,min:1,max:3,typ:'1 comprimido al día, con comida',ev:'según necesidad',m:1,cap:null,capTxt:'Sigue la dosis de la etiqueta.',feeds:'Suma a las vitaminas y minerales de su etiqueta, que rellenas al configurarlo.',hasNut:true},
 {id:'ashwagandha',n:'Ashwagandha (extracto)',s:'Ashwagandha',g:2,ic:'spLeaf',u:'mg',dose:300,step:100,min:100,max:1200,typ:'300–600 mg de extracto',ev:'limitada',m:5,cap:null,capTxt:'Evidencia limitada: sigue la dosis de la etiqueta.',feeds:'Solo registro: no cambia tu huella de nutrientes.',hasNut:false},
 {id:'melatonina',n:'Melatonina',s:'Melatonina',g:2,ic:'spMoon',u:'mg',dose:1,step:0.5,min:0.5,max:5,typ:'0,5–3 mg, 30–60 min antes de dormir',ev:'moderada',m:6,cap:null,capTxt:'Sin techo propio; las dosis bajas bastan.',feeds:'Solo registro: no cambia tu huella de nutrientes.',hasNut:false},
];
const GR=['Rendimiento','Salud','Descanso y estrés'];
const cid=id=>CAT.find(c=>c.id===id);
const nf=(v)=>{const r=Math.round(v*100)/100;const [a,b]=String(r).split('.');return a.replace(/\B(?=(\d{3})+(?!\d))/g,'.')+(b?','+b:'')};
const dd=(c,n)=>c.noun?`${nf(n)} ${n===1?c.u:c.pl}`:`${nf(n)} ${c.u}`;
const uv=(c)=>c.noun?'':` ${c.u}`;
const evT=e=>({'sólida':'Evidencia sólida','moderada':'Evidencia moderada','según necesidad':'Según tu necesidad','limitada':'Evidencia limitada'})[e];
// Estado inicial de un escenario.
const pilaBase=()=>[
 {id:'creatina',dose:5,mom:1,tomas:1,rem:'08:00',times:[{t:'08:40',d:5}]},
 {id:'vitd',dose:10,mom:1,tomas:1,rem:null,times:[{t:'08:40',d:10}]},
 {id:'omega3',dose:1,mom:2,tomas:1,rem:'13:00',times:[]},
 {id:'cafeina',dose:100,mom:3,tomas:1,rem:null,times:[]},
 {id:'proteina',dose:25,mom:4,tomas:1,rem:null,times:[]},
 {id:'magnesio',dose:300,mom:6,tomas:1,rem:'22:30',times:[]},
];
const mk=(o={})=>Object.assign({page:'nutri',pila:pilaBase(),food:95,sheet:null,toast:null,now:null,hint:true,pop:null,detail:'creatina',form:null,week:[1,1,.83,1,.83,1],hq:{}},o);
const sumT=it=>it.times.reduce((a,x)=>a+x.d,0);
const isDone=it=>it.times.length>=it.tomas;
const itOf=(st,id)=>st.pila.find(p=>p.id===id);
const cafT=st=>st.food+sumT(itOf(st,'cafeina')||{times:[]});
const dayTot=(st,id)=>id==='cafeina'?cafT(st):sumT(itOf(st,id)||{times:[]});
const sorted=st=>[...st.pila].sort((a,b)=>a.mom-b.mom);
const homeK=st=>1240+Math.round(sumT(itOf(st,'proteina')||{times:[]})*4.4);
const homeP=st=>67+Math.round(sumT(itOf(st,'proteina')||{times:[]}));

/* ---------- Piezas ---------- */
const bar=(v,max,{band=null,lim=false,over=false}={})=>`<div class="sp-bar">${band?`<span class="band" style="left:${band[0]/max*100}%;width:${(band[1]-band[0])/max*100}%"></span>`:''}<i class="${over?'over':''}" style="width:${Math.min(100,v/max*100)}%"></i>${lim?'<span class="lim"></span>':''}</div>`;
const countN=(to,from)=>`<span data-count="${to}" data-from="${from===undefined?to:from}">${nf(to)}</span>`;
function totCaf(st){
  const t=cafT(st),over=t>400,sup=sumT(itOf(st,'cafeina')||{times:[]});
  return `<button class="sp-tot" data-a="detail" data-id="cafeina" aria-label="Cafeína del día, ver detalle"><div class="sp-tl"><span class="n">${ic('bolt',15)}Cafeína</span><span class="v ${over?'over':''}">${countN(t,st.cafFrom)}<small> de 400 mg</small></span></div>
  ${bar(t,400,{band:[160,250],over})}
  <div class="sp-sub ${over?'warn':''}">${over?`Pasas el máximo diario en ${nf(t-400)} mg. Es una referencia, no una orden.`:sup>0?`Tus comidas ${nf(st.food)} mg · suplementos ${nf(sup)} mg`:`De tus comidas y bebidas · la franja clara es la útil (160–250 mg)`}</div></button>`;
}
function totCre(st){
  const it=itOf(st,'creatina');if(!it)return '';
  const t=sumT(it),goal=5;
  return `<button class="sp-tot" data-a="detail" data-id="creatina" aria-label="Creatina del día, ver detalle"><div class="sp-tl"><span class="n">${ic('spCube',15)}Creatina</span><span class="v">${countN(t)}<small> de ${goal} g</small></span></div>
  ${bar(t,goal)}<div class="sp-sub">${t>=goal?'Dosis de hoy hecha · 6 días seguidos':'6 días seguidos · hoy suma la dosis'}</div></button>`;
}
function row(st,it){
  const c=cid(it.id),n=it.times.length,done=isDone(it),now=st.now===it.id&&!done,last=it.times[n-1];
  const sub=done?`${dd(c,it.times.reduce((a,x)=>a+x.d,0))}${it.tomas>1?` · ${n} tomas`:''}`:now?`Toca ahora`:n>0?`${n} de ${it.tomas} tomas · última ${last.t}`:MOM[it.mom];
  return `<div class="sp-row ${done?'done':''} ${now?'now':''} ${st.pop===it.id?'pop':''}" data-row="${it.id}">
   <span class="sp-ic">${ic(done?'check':c.ic,20)}</span>
   <button class="sp-tx" data-a="detail" data-id="${it.id}"><strong>${c.s}${it.rem&&!done?`<i class="sp-bell" aria-label="Recordatorio ${it.rem}">${ic('bell',12)}</i>`:''}</strong><span>${sub}</span></button>
   ${done?`<span class="sp-time">${last.t}</span>`:`<button class="sp-take ${now?'now':''} ${st.hq[it.id]?'hold':''}" data-take="${it.id}" aria-label="Registrar ${dd(c,it.dose)} de ${c.s}">${ic('plus',16)}<span class="pl-num">${dd(c,it.dose)}</span></button>`}
  </div>`;
}
function weekStrip(st){
  const L=['J','V','S','D','L','M','X'],tot=st.pila.length||1,td=st.pila.filter(isDone).length/tot;
  return `<div class="sp-wk">${[...st.week,td].map((v,i)=>`<div class="${i===6?'today':''}"><i style="--h:${Math.round(v*100)}%"></i>${L[i]}</div>`).join('')}</div>`;
}
function modulo(st){
  const sup=sorted(st),total=sup.length,done=sup.filter(isDone).length;
  const all=total>0&&done===total;
  const head=`<div class="sp-mh"><div class="l"><span class="ey">Suplementos</span>${total?`<span class="sbt ${all?'ok':''}">${all?ic('check',14)+'Todo tomado':`${done} de ${total} tomados`}</span>`:''}</div><button class="sp-addb" data-a="anadir" aria-label="Añadir suplemento">${ic('plus',20)}</button></div>`;
  if(!total)return `<div class="mod sp-mod">${head}${totCaf(st)}<div class="sp-sep"></div>
   <div class="sp-empty"><h4>Tu pila, en un toque</h4><p>Elige lo que tomas cada día y regístralo con un toque desde aquí o desde Inicio. Puedes cambiar la lista cuando quieras.</p>
   <div class="sp-sugg">${['creatina','cafeina','vitd','omega3','magnesio'].map(id=>{const c=cid(id);const on=!!itOf(st,id);return `<button class="sp-sg ${on?'on':''}" data-a="sugg" data-id="${id}">${ic(c.ic,18)}${c.s}</button>`}).join('')}</div>
   <button class="pl-btn sp-cta" data-a="anadir">${ic('plus',18)}Armar mi pila</button></div></div>`;
  return `<div class="mod sp-mod">${head}${itOf(st,'cafeina')||st.food>0?totCaf(st):''}${totCre(st)}<div class="sp-sep"></div>
   <div>${sup.map(it=>row(st,it)).join('')}</div>
   ${st.hint&&!all?`<div class="sp-hint">${ic('info',14)}Mantén pulsado un botón para ajustar la dosis o la hora.</div>`:''}
   <div class="sp-gl" style="margin-top:8px"><span class="sp-lbl" style="margin:0">Últimos 7 días</span><span class="cap">tu pila por día</span></div>${weekStrip(st)}
   <div class="sp-foot"><button class="sp-link" data-a="anadir">${ic('plus',16)}Añadir suplemento</button><button class="sp-link f" data-a="detail" data-id="${(sup[0]||{}).id||'creatina'}">Historial${ic('fwd',14)}</button></div></div>`;
}
function pgNutri(st){
  return `<div class="sp-pg"><div class="sp-date">MIÉRCOLES 29</div><div class="ptitle" style="margin-top:4px">Nutrición</div>
   <div class="mod sp-stub" style="margin-top:14px"><span>Hoy · 3 comidas</span><b>1.240 kcal</b></div>
   ${modulo(st)}
   <div class="mod sp-stub"><span>Semana</span><b>promedio 2.180 kcal</b></div></div>${navbar(2)}`;
}
/* Inicio */
function ringSvg(k,t,size=96,bump=false){const R=size/2-5,C=2*Math.PI*R,f=Math.min(1,k/t);
  return `<div class="sp-ring ${bump?'bump':''}" style="width:${size}px;height:${size}px"><svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" aria-hidden="true"><circle cx="${size/2}" cy="${size/2}" r="${R}" fill="none" stroke="#ffffff14" stroke-width="5"/><circle cx="${size/2}" cy="${size/2}" r="${R}" fill="none" stroke="var(--ink)" stroke-width="5" stroke-linecap="round" stroke-dasharray="${(C*f).toFixed(1)} ${C.toFixed(1)}" transform="rotate(-90 ${size/2} ${size/2})"/></svg><b>${countN(k,st0.kFrom)}<small>de 2.300</small></b></div>`}
let st0={};
function pgInicio(st){
  st0=st;
  const sup=sorted(st),pend=sup.filter(it=>!isDone(it)),total=sup.length,done=total-pend.length;
  const shown=pend.slice(0,3),rest=pend.length-shown.length,cafIn=!!itOf(st,'cafeina');
  const strip=!total?'':`<div class="sp-hsup"><button class="sp-hsh" data-a="rapida" aria-label="Abrir suplementos de hoy"><span class="l">Suplementos</span><span class="r ${pend.length?'':'ok'}">${pend.length?`${done} de ${total}`:ic('check',14)+'Todo tomado'}${ic('fwd',14)}</span></button>
    ${pend.length?`<div class="sp-hc">${shown.map(it=>{const c=cid(it.id),now=st.now===it.id;return `<button class="sp-hq ${now?'now':''} ${st.hq[it.id]?'hold':''}" data-take="${it.id}" aria-label="Registrar ${dd(c,it.dose)} de ${c.s}">${now?ic('plus',15):''}${c.s}<small>${dd(c,it.dose)}</small></button>`}).join('')}${rest>0?`<button class="sp-hq more" data-a="rapida">+${rest} más</button>`:''}</div>`:''}
    ${cafIn?`<div class="sp-hcaf"><span>Cafeína de hoy</span><b class="${cafT(st)>400?'over':''}">${nf(cafT(st))} / 400 mg</b></div>`:''}</div>`;
  const P1=homeP(st);
  return `<div class="sp-home"><div class="sp-date">MIÉRCOLES 29</div><div class="ptitle" style="margin-top:4px">Hola, Matías</div>
   <div class="mod" style="margin-top:14px"><div class="mod-h"><span class="ey">Hoy en tu entreno</span><span class="cap">Tirón · 48 min</span></div><button class="pill pri sm" style="width:100%;margin-top:10px">${ic('play',16)}Empezar Tirón</button></div>
   <div class="mod"><div class="mod-h"><span class="ey">Nutrición de hoy</span><span class="cap">objetivo 2.300</span></div>
   <div class="sp-day" style="margin-top:12px">${ringSvg(homeK(st),2300,96,st.bump)}<div class="sp-mr">${[['Proteína',P1,170],['Carbos',104,260],['Grasa',38,75]].map(m=>`<div>${m[0]}<b>${nf(m[1])}<small> / ${m[2]} g</small></b><i style="--w:${Math.round(m[1]/m[2]*100)}%"></i></div>`).join('')}</div></div>
   ${strip}
   <button class="sp-reg">${ic('plus',18)}Registrar comida</button></div>
   <div class="mod tight"><div class="sp-hrow"><div class="sp-tx"><strong>Desayuno</strong><span>Avena con plátano · 412 kcal</span></div></div><div class="sp-hrow"><div class="sp-tx"><strong>Almuerzo</strong><span>Sin registrar</span></div></div></div></div>${navbar(0)}`;
}
/* Detalle */
function pgDetalle(st){
  const id=st.detail,c=cid(id),it=itOf(st,id)||{id,dose:c.dose,mom:c.m,tomas:1,rem:null,times:[]},isC=id==='cafeina';
  const r=rng(isC?7:3),days=Array.from({length:30},(_,i)=>{const x=r();return isC?Math.round((x*260+60)/25)*25+(i===12?150:0):(x<.12?0:5)});
  const mx=Math.max(...days,isC?400:5),tot=isC?cafT(st):sumT(it),goal=isC?400:5;
  const cr=id==='creatina';
  const takes=it.times.map((x,i)=>`<div class="sp-tk"><span class="tm">${x.t}</span><div class="tx">${dd(c,x.d)}<small>${isC?'Suplemento':'Registrado con un toque'}</small></div><button data-a="del" data-id="${id}" data-i="${i}">Quitar</button></div>`).join('');
  const food=isC?`<div class="sp-tk"><span class="tm">08:15</span><div class="tx">${nf(st.food)} mg<small>Café con leche · tus comidas</small></div></div>`:'';
  return `<div class="sp-pg noNav"><div class="sp-top"><button class="ib" data-a="back" aria-label="Volver">${ic('back',22)}</button><div class="t">${isC?'Cafeína de hoy':c.s}</div><button class="ib" data-a="edit" data-id="${id}" aria-label="Configurar">${ic('sliders',20)}</button></div>
   <div class="mod"><div class="ey">Hoy</div><div class="sp-hero"><div class="num">${countN(tot)}<small>de ${goal} ${c.u}${isC?' máx.':''}</small></div>${bar(tot,goal,{band:isC?[160,250]:null,over:tot>goal})}</div>
    <div class="sp-stats">${isC?`<div><b>${nf(sumT(it))} mg</b>suplementos</div><div><b>${nf(st.food)} mg</b>comidas</div><div><b>${nf(Math.round(days.reduce((a,b)=>a+b,0)/30/5)*5)} mg</b>media 30 días</div>`:`<div><b>6 días</b>seguidos</div><div><b>27 de 30</b>días este mes</div><div><b>${nf(days.reduce((a,b)=>a+b,0))} g</b>en 30 días</div>`}</div></div>
   <div class="mod"><div class="ey" style="margin-bottom:4px">Tomas de hoy</div>${(takes||food)?food+takes:`<div class="cap" style="padding:10px 0">Aún sin tomas hoy.</div>`}</div>
   <div class="mod"><div class="mod-h"><span class="ey">Últimos 30 días</span><span class="cap">${isC?'mg por día':'g por día'}</span></div>
    <div class="sp-chart">${days.map((v,i)=>`<i class="${v===0?'z':''} ${i===29?'t':''}" style="height:${Math.max(3,v/mx*100)}%"></i>`).join('')}</div><div class="sp-axis"><span>hace 30 días</span><span>hoy</span></div></div>
   <div class="mod tight" style="padding:6px 18px">${cr?`<button class="sp-pr" data-a="creatina"><span class="sp-ic">${ic('target',20)}</span><div class="tx"><strong>Seguimiento de creatina</strong><span class="s">Pauta gradual · día 12 de 28</span><div class="sp-pbar"><i style="width:43%"></i></div></div>${ic('fwd',16)}</button>`:''}
    <div class="sp-feed" style="${cr?'':''}">${ic('info',16)}<span><b>Aporta a tu huella.</b> ${c.feeds}</span></div></div>
   </div><div class="sp-bot"><button class="pl-btn" data-take="${id}">${ic('plus',18)}Registrar ${dd(c,it.dose)}</button></div>`;
}
/* Alta / edición */
function pgAlta(st){
  const f=st.form,c=f.mode==='custom'?{n:'',s:'',ic:'spCaps',u:f.unit,dose:f.dose,step:f.unit==='mg'?50:1,cap:null,capTxt:'Sin techo propio: sigue la dosis de la etiqueta.',feeds:'Solo registro: no cambia tu huella de nutrientes.',noun:false,min:0.5,max:9999,typ:''}:cid(f.id);
  const goal=f.dose*f.tomas,edit=f.mode==='edit',custom=f.mode==='custom';
  const stp=(k)=>`<div class="sp-st"><button class="sp-rb" data-a="step" data-k="${k}" data-v="-1" aria-label="Menos"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"><path d="M5 12h14"/></svg></button><b>${custom?nf(f.dose)+' '+f.unit:dd(c,f.dose)}</b><button class="sp-rb" data-a="step" data-k="${k}" data-v="1" aria-label="Más">${ic('plus',20)}</button></div>`;
  return `<div class="sp-pg noNav" style="padding-bottom:190px"><div class="sp-top"><button class="ib" data-a="back" aria-label="Volver">${ic('back',22)}</button><div class="t">${edit?c.s:custom?'Suplemento propio':'Nuevo suplemento'}</div></div>
   <div class="mod sp-grp"><div class="sp-fr"><span class="k">Nombre</span>${custom?`<span class="val" style="color:var(--dim)">Ej.: Colágeno</span>`:`<span class="val ink">${c.n}</span>`}</div>
    ${custom?`<div class="sp-fr"><span class="k">Unidad</span><div class="sp-seg">${['g','mg','µg','ud'].map(u=>`<button class="${f.unit===u?'on':''}" data-a="unit" data-v="${u}">${u}</button>`).join('')}</div></div>`:`<div class="sp-fr"><span class="k">Dosis típica<small>${evT(c.ev)}</small></span><span class="val">${c.typ}</span></div>`}</div>
   <div class="mod sp-grp"><div class="sp-fr"><span class="k">Dosis habitual<small>lo que registra un toque</small></span>${stp('dose')}</div>
    <div class="sp-fr"><span class="k">Tomas al día</span><div class="sp-seg">${[1,2,3].map(n=>`<button class="${f.tomas===n?'on':''}" data-a="tomas" data-v="${n}">${n}</button>`).join('')}</div></div>
    <div class="sp-fr"><span class="k">Objetivo diario<small>dosis × tomas</small></span><span class="val ink">${custom?nf(goal)+' '+f.unit:dd(c,goal)}</span></div>
    <div class="sp-fr"><span class="k">Techo<small>${c.capTxt}</small></span><span class="val">${c.cap?`${nf(c.cap)} ${c.u}`:'—'}</span></div></div>
   <div class="sp-note">Son referencias generales para adultos sanos. Si estás embarazada, en lactancia o tomas medicación, consúltalo con tu profesional de salud.</div>
   <div class="mod sp-grp"><div class="sp-fr" style="display:block;padding-bottom:12px"><span class="k">Momento</span><div class="sp-chs" style="margin-top:10px">${MOM.map((m,i)=>`<button class="sp-ch sm ${f.mom===i?'on':''}" data-a="mom" data-v="${i}">${m}</button>`).join('')}</div></div>
    <div class="sp-fr"><span class="k">Recordatorio<small>${f.remOn?'Te avisa cada día a esa hora':'Sin aviso: solo lo ves en tu pila'}</small></span><button class="sp-sw ${f.remOn?'on':''}" data-a="rem" role="switch" aria-checked="${f.remOn}" aria-label="Recordatorio"></button></div>
    <div class="sp-fr ${f.remOn?'':'off'}"><span class="k">Hora</span><span class="val ink">${f.rem}${ic('fwd',14)}</span></div></div>
   <div class="mod sp-grp"><div class="sp-feed">${ic('info',16)}<span><b>Aporta a tu huella.</b> ${c.feeds}</span></div><div class="sp-fd ${f.notes?'in':''}" style="height:${f.notes?'auto':'56px'};margin-top:0">${f.notes||'Notas · marca, fórmula, con qué lo tomas…'}</div></div>
   ${edit&&f.id==='creatina'?`<div class="mod sp-grp"><button class="sp-pr" data-a="creatina" style="border-top:0"><span class="sp-ic">${ic('target',20)}</span><div class="tx"><strong>Seguimiento de creatina</strong><span class="s">Pauta gradual · día 12 de 28</span></div>${ic('fwd',16)}</button></div>`:''}
   </div><div class="sp-bot"><button class="pl-btn" data-a="save">${edit?'Guardar cambios':custom?'Crear y añadir a mi pila':'Añadir a mi pila'}</button>${edit?`<button class="pl-btn danger" data-a="baja">Dejar de tomar</button>`:''}</div>`;
}
/* Hojas */
function sheet(st){
  const s=st.sheet;if(!s)return '';
  const pre='<div class="pl-scrim" data-a="close"></div>';
  if(s.k==='ajustar'){
    const c=cid(s.id),it=itOf(st,s.id),W=[['Ahora',NOW],['Hace 30 min','12:25'],['Hace 1 h','11:55'],['Esta mañana','08:30']];
    const cur=dayTot(st,s.id),after=cur+s.d,lim=c.cap||it.dose*it.tomas,over=c.cap&&after>c.cap;
    return pre+`<div class="pl-sheet sp-sheet"><div class="sp-sh-h"><span class="sp-ic" style="width:44px;height:44px">${ic(c.ic,22)}</span><div class="tx"><strong>${c.s}</strong><span>Ajustar esta toma · hoy</span></div></div>
     <div class="sp-big"><button class="sp-rb" data-a="sd" data-v="-1" aria-label="Menos" ${s.d<=c.min?'disabled':''}><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"><path d="M5 12h14"/></svg></button><div class="num">${nf(s.d)}<small>${c.noun?(s.d===1?c.u:c.pl):c.u}</small></div><button class="sp-rb" data-a="sd" data-v="1" aria-label="Más">${ic('plus',22)}</button></div>
     <div class="sp-chs" style="justify-content:center">${[['½',it.dose/2],['Habitual',it.dose],['×2',it.dose*2]].map(x=>`<button class="sp-ch ${Math.abs(s.d-x[1])<1e-9?'on':''}" data-a="sset" data-v="${x[1]}">${x[0]}</button>`).join('')}</div>
     <div class="sp-lbl">Cuándo</div><div class="sp-chs">${W.map((w,i)=>`<button class="sp-ch sm ${s.w===i?'on':''}" data-a="swhen" data-v="${i}">${w[0]}<span class="pl-num" style="opacity:.6;font-weight:500">${w[1]}</span></button>`).join('')}</div>
     <div class="sp-proj"><div class="sp-tl"><span class="n">${c.id==='cafeina'?'Cafeína del día':'Hoy con esta toma'}</span><span class="v ${over?'over':''}">${nf(after)}<small> de ${nf(lim)} ${c.noun?c.pl:c.u}</small></span></div>${bar(after,lim,{band:c.id==='cafeina'?[160,250]:null,over})}</div>
     ${over?`<div class="sp-warn">${ic('spWarn',18)}<span><b>Pasas el máximo diario.</b> ${c.capTxt}</span></div>`:''}
     <div class="sp-acts"><button class="pl-btn ghost" data-a="edit" data-id="${s.id}" style="flex:0 0 auto;padding:0 18px">${ic('sliders',18)}</button><button class="pl-btn" data-a="sreg">Registrar ${dd(c,s.d)}</button></div></div>`;
  }
  if(s.k==='aviso'){
    const c=cid(s.id),after=dayTot(st,s.id)+s.d;
    return pre+`<div class="pl-sheet sp-sheet"><div class="sp-sh-h"><span class="sp-ic" style="width:44px;height:44px;color:var(--danger);background:#ff9b9214">${ic('spWarn',22)}</span><div class="tx"><strong>Esta toma pasa tu máximo</strong><span>${c.s} · ${dd(c,s.d)}</span></div></div>
     <div class="sp-proj"><div class="sp-tl"><span class="n">${c.s} del día</span><span class="v over">${nf(after)}<small> de ${nf(c.cap)} ${c.u}</small></span></div>${bar(after,c.cap,{over:true})}</div>
     <div class="sp-warn"><span>${c.capTxt} Puedes registrarla igual: tú decides.</span></div>
     <div class="sp-acts"><button class="pl-btn ghost" data-a="close">No registrar</button><button class="pl-btn" data-a="avok">Registrar igual</button></div></div>`;
  }
  if(s.k==='anadir'){
    const q=s.cat,list=CAT.filter(c=>q===-1||c.g===q);
    const body=(q===-1?[0,1,2]:[q]).map(g=>{const arr=list.filter(c=>c.g===g);return arr.length?`<div class="sp-gh">${GR[g]}</div>${arr.map(c=>{const on=!!itOf(st,c.id);return `<div class="sp-cr"><span class="sp-ic">${ic(c.ic,20)}</span><button class="sp-tx" data-a="nuevo" data-id="${c.id}"><strong>${c.n}</strong><span>${c.typ}</span><span class="sp-ev">${evT(c.ev)}</span></button><button class="sp-pl ${on?'on':''}" data-a="tpila" data-id="${c.id}" aria-label="${on?'Quitar de mi pila':'Añadir a mi pila'}" aria-pressed="${on}">${ic(on?'check':'plus',20)}</button></div>`}).join('')}`:''}).join('');
    return pre+`<div class="pl-sheet sp-sheet" style="height:86%"><div class="sp-sh-h"><div class="tx"><strong>Añadir suplemento</strong><span>${st.pila.length} en tu pila · toca + para sumarlo con su dosis típica</span></div><button class="ib fill" data-a="close" aria-label="Cerrar">${ic('close',20)}</button></div>
     <div class="sp-search">${ic('search',18)}Buscar suplemento</div>
     <div class="sp-chs cat">${['Todos','Rendimiento','Salud','Descanso'].map((x,i)=>`<button class="sp-ch sm ${q===i-1?'on':''}" data-a="gcat" data-v="${i-1}" style="flex-shrink:0">${x}</button>`).join('')}</div>
     <div class="sp-lst">${body}<div class="sp-cr"><span class="sp-ic">${ic('edit',20)}</span><button class="sp-tx" data-a="propio"><strong>Crear uno propio</strong><span>Nombre, unidad y dosis a tu manera</span></button>${ic('fwd',16)}</div></div></div>`;
  }
  if(s.k==='baja'){
    const c=cid(s.id);
    return pre+`<div class="pl-sheet sp-sheet"><div class="sp-sh-h"><div class="tx"><strong>Dejar de tomar ${c.s.toLowerCase()}</strong><span>Sale de tu pila y de Inicio</span></div></div>
     <div class="sp-warn"><span>Lo que ya registraste <b>se queda</b> en tu historial de comidas y en tus totales. Si tenías recordatorio, se apaga. Podrás volver a añadirlo cuando quieras.</span></div>
     <div class="sp-acts"><button class="pl-btn ghost" data-a="close">Cancelar</button><button class="pl-btn danger" data-a="bajaok">Dejar de tomar</button></div></div>`;
  }
  if(s.k==='rapida'){
    const sup=sorted(st),pend=sup.filter(it=>!isDone(it)).length;
    return pre+`<div class="pl-sheet sp-sheet"><div class="sp-sh-h"><div class="tx"><strong>Suplementos de hoy</strong><span>${pend?`${sup.length-pend} de ${sup.length} tomados`:'Todo tomado'}</span></div><button class="ib fill" data-a="close" aria-label="Cerrar">${ic('close',20)}</button></div>
     <div>${sup.map(it=>row(st,it)).join('')}</div>
     <div class="sp-foot" style="margin-top:0"><button class="sp-link" data-a="anadir">${ic('plus',16)}Añadir suplemento</button><button class="sp-link f" data-a="toNutri">Abrir en Nutrición${ic('fwd',14)}</button></div></div>`;
  }
  if(s.k==='creatina'){
    return pre+`<div class="pl-sheet sp-sheet"><div class="sp-tl2">Seguimiento de creatina</div>
     <div class="sp-pr2"><span style="color:var(--muted)">La creatina satura los músculos tras una carga (~5–7 días) o con 3–5 g/día durante ~3–4 semanas. A partir de ahí suele notarse el efecto ergogénico.</span>
     <b>Con tu peso (72 kg):</b><span style="color:var(--ink)">• Carga: 21,6 g/día × 6 días, luego 3,0 g/día</span><span style="color:var(--ink)">• Gradual: 5 g/día (~28 días)</span></div>
     <div class="sp-seg" style="width:100%">${['Semana de carga','Pauta gradual'].map((x,i)=>`<button style="flex:1" class="${(s.p||1)===i?'on':''}" data-a="proto" data-v="${i}">${x}</button>`).join('')}</div>
     <div class="sp-note" style="padding:0">Basado en ISSN/EFSA. No sustituye orientación médica.</div><button class="pl-btn" data-a="close">Iniciar seguimiento</button></div>`;
  }
  return '';
}
function toast(st){
  if(!st.toast)return '';
  const b=(st.page==='nutri'||st.page==='inicio')&&!st.sheet?96:28;
  return `<div class="toast glass" style="bottom:${b}px" role="status">${ic('check',18)}<span>${st.toast.text}</span><button class="act" data-a="undo">Deshacer</button></div>`;
}
function view(st){
  const pg=st.page==='inicio'?pgInicio(st):st.page==='alta'?pgAlta(st):st.page==='detalle'?pgDetalle(st):pgNutri(st);
  return pg+sheet(st)+toast(st);
}
/* ---------- Controlador ---------- */
function mount(phone,st){
  const root=phone.querySelector('.sp-root');if(!root)return;let timer=null,held=false,tt=null;
  const red=(anim)=>{root.innerHTML=view(st);animN();};
  const animN=()=>{root.querySelectorAll('[data-count]').forEach(el=>{const to=+el.dataset.count,from=+el.dataset.from;if(from===to){return}
    const t0=performance.now(),dur=matchMedia('(prefers-reduced-motion: reduce)').matches?0:600;const step=t=>{const k=dur?Math.min(1,(t-t0)/dur):1;el.textContent=nf(from+(to-from)*(1-Math.pow(1-k,3)));if(k<1)requestAnimationFrame(step)};requestAnimationFrame(step)});st.cafFrom=undefined;st.kFrom=undefined;st.pop=null;st.bump=false};
  const say=(text,undo)=>{st.toast={text,undo};clearTimeout(tt);tt=setTimeout(()=>{st.toast=null;red()},5000)};
  const reg=(id,d,t,confirm)=>{const it=itOf(st,id),c=cid(id);if(!it)return;
    if(confirm&&c.cap&&dayTot(st,id)+d>c.cap){st.sheet={k:'aviso',id,d,t};return red()}
    st.cafFrom=cafT(st);st.kFrom=homeK(st);it.times.push({t:t||NOW,d});st.pop=id;st.bump=id==='proteina';st.sheet=null;st.hint=false;
    say(`${c.s} · ${dd(c,d)} registrado`,()=>{const i=it.times.findIndex(x=>x.t===(t||NOW)&&x.d===d);if(i>-1)it.times.splice(i,1)});red()};
  const form=(id,mode)=>{const it=itOf(st,id),c=cid(id);return {mode,id,dose:it?it.dose:c.dose,tomas:it?it.tomas:1,mom:it?it.mom:c.m,remOn:it?!!it.rem:false,rem:it&&it.rem||'09:00',notes:'',unit:'g'}};
  const stepOf=()=>{const f=st.form;if(f.mode==='custom')return {s:f.unit==='mg'?50:f.unit==='µg'?10:1,lo:1,hi:9999};const c=cid(f.id);return {s:c.step,lo:c.min,hi:c.max}};
  root.addEventListener('pointerdown',e=>{const b=e.target.closest('[data-take]');if(!b)return;held=false;const id=b.dataset.take;st.hq[id]=1;b.classList.add('hold');
    timer=setTimeout(()=>{held=true;delete st.hq[id];const it=itOf(st,id);st.sheet={k:'ajustar',id,d:it.dose,w:0};red()},480)});
  const end=(e)=>{clearTimeout(timer);const b=e.target.closest&&e.target.closest('[data-take]');Object.keys(st.hq).forEach(k=>delete st.hq[k]);root.querySelectorAll('.hold').forEach(x=>x.classList.remove('hold'));
    if(e.type==='pointerup'&&b&&!held){const id=b.dataset.take,it=itOf(st,id);reg(id,it.dose,null,true)}};
  root.addEventListener('pointerup',end);root.addEventListener('pointercancel',end);
  root.addEventListener('contextmenu',e=>{if(e.target.closest('[data-take]'))e.preventDefault()});
  root.addEventListener('click',e=>{
    const tk=e.target.closest('[data-take]');if(tk&&e.detail===0){const it=itOf(st,tk.dataset.take);if(it)reg(it.id,it.dose,null,true);return}
    const a=e.target.closest('[data-a]');if(!a)return;const id=a.dataset.id,v=a.dataset.v,s=st.sheet;
    switch(a.dataset.a){
      case 'close':st.sheet=null;break;
      case 'detail':st.sheet=null;st.page='detalle';st.detail=id;break;
      case 'back':st.page=st.back||'nutri';st.back=null;break;
      case 'toNutri':st.sheet=null;st.page='nutri';break;
      case 'anadir':st.sheet={k:'anadir',cat:-1};break;
      case 'rapida':st.sheet={k:'rapida'};break;
      case 'gcat':s.cat=+v;break;
      case 'sugg':{const it=itOf(st,id);if(it)st.pila.splice(st.pila.indexOf(it),1);else{const c=cid(id);st.pila.push({id,dose:c.dose,mom:c.m,tomas:1,rem:null,times:[]})}break}
      case 'tpila':{const it=itOf(st,id),c=cid(id);if(it){st.pila.splice(st.pila.indexOf(it),1);say(`${c.s} salió de tu pila`,()=>st.pila.push(it))}else{const n={id,dose:c.dose,mom:c.m,tomas:1,rem:null,times:[]};st.pila.push(n);say(`${c.s} añadido a tu pila`,()=>{const i=st.pila.indexOf(n);if(i>-1)st.pila.splice(i,1)})}break}
      case 'nuevo':st.back=st.page;st.sheet=null;st.page='alta';st.form=form(id,itOf(st,id)?'edit':'new');break;
      case 'propio':st.back=st.page;st.sheet=null;st.page='alta';st.form={mode:'custom',id:'propio',dose:1,tomas:1,mom:1,remOn:false,rem:'09:00',notes:'',unit:'g'};break;
      case 'edit':st.back=st.page==='alta'?st.back:st.page;st.sheet=null;st.page='alta';st.form=form(id,'edit');break;
      case 'step':{const f=st.form,o=stepOf();f.dose=Math.min(o.hi,Math.max(o.lo,Math.round((f.dose+o.s*+v)*100)/100));break}
      case 'unit':st.form.unit=v;st.form.dose=v==='mg'?100:1;break;
      case 'tomas':st.form.tomas=+v;break;
      case 'mom':st.form.mom=+v;break;
      case 'rem':{const f=st.form;f.remOn=!f.remOn;break}
      case 'save':{const f=st.form,c=f.mode==='custom'?null:cid(f.id);let it=itOf(st,f.id);
        if(c){if(it){Object.assign(it,{dose:f.dose,tomas:f.tomas,mom:f.mom,rem:f.remOn?f.rem:null})}else{st.pila.push({id:f.id,dose:f.dose,tomas:f.tomas,mom:f.mom,rem:f.remOn?f.rem:null,times:[]})}}
        st.page=st.back||'nutri';st.back=null;say(c?(f.mode==='edit'?`${c.s} guardado`:`${c.s} añadido a tu pila`):'Suplemento creado',null);break}
      case 'baja':st.sheet={k:'baja',id:st.form.id};break;
      case 'bajaok':{const it=itOf(st,s.id),c=cid(s.id);st.pila.splice(st.pila.indexOf(it),1);st.sheet=null;st.page='nutri';st.back=null;say(`${c.s} salió de tu pila`,()=>st.pila.push(it));break}
      case 'sd':{const c=cid(s.id);s.d=Math.min(c.max,Math.max(c.min,Math.round((s.d+c.step*+v)*100)/100));break}
      case 'sset':s.d=+v;break;
      case 'swhen':s.w=+v;break;
      case 'sreg':{const W=['12:55','12:25','11:55','08:30'];reg(s.id,s.d,W[s.w],false);return}
      case 'avok':reg(s.id,s.d,s.t,false);return;
      case 'del':{const it=itOf(st,id);it.times.splice(+a.dataset.i,1);break}
      case 'creatina':st.sheet={k:'creatina',p:1};break;
      case 'proto':s.p=+v;break;
      case 'undo':{if(st.toast&&st.toast.undo)st.toast.undo();st.toast=null;break}
    }
    red();
  });
  document.addEventListener('visibilitychange',()=>{});
}
const wrap=(st,navKey)=>`<div class="sp-root" data-sp="1">${view(st)}</div>`;
const go=(ph,st)=>mount(ph,st);
const ST=(o)=>()=>mk(o);
function sceneSp(id,title,mkState,notes){
  let cur;
  scene({id,tab:'suplementos',title,html:()=>{cur=mkState();return wrap(cur)},after:ph=>{const st=mkState();ph.querySelector('.sp-root').innerHTML=view(st);mount(ph,st)},notes});
}

/* ---------- Escenas ---------- */
sceneSp('sp-modulo','Módulo en Nutrición · a media mañana',()=>mk({}),
 {tags:[['keep','Cafeína y creatina siguen'],['new','Pila de suplementos'],['new','Registro en un toque'],['out','Sale el medidor de arco']],
  p:['El módulo «Suplementos» conserva lo que hoy da: la <b>cafeína del día frente a su máximo</b> (con la franja útil de 160–250 mg) y la <b>creatina con su dosis y su racha</b>. Pero ahora son dos totales finos en tinta, como el resto de Nutrición, y debajo vive <b>tu pila</b>: lo que tomas cada día.',
     'Cada fila lleva a la derecha un botón con la <b>dosis habitual visible</b>. Un toque registra esa dosis (animación del círculo, cifras que cuentan, toast con «Deshacer»). <b>Mantenerlo pulsado</b> abre el ajuste de dosis y hora. Tocar el nombre abre el detalle. «Toca ahora» solo aparece cuando llega el momento del recordatorio.',
     'Pruébalo: toca «Cafeína 100 mg», «Proteína 25 g»; mantén pulsado alguno; toca «+».'],
  img:'h19-voz-comida',imgCap:'Referencia de hoy: la tarjeta de Nutrición. El módulo de suplementos actual (arco de cafeína y check de creatina) no tiene captura en hoy/.',
  list:['Totales: cafeína suma comidas y suplementos; creatina cuenta su dosis de hoy.','Filas ordenadas por momento del día (desayuno, comida, antes de entrenar…).','Últimos 7 días: una cápsula por día con la parte de la pila que hiciste; hoy en vivo.','La línea de ayuda «Mantén pulsado…» desaparece tras el primer uso.']});
sceneSp('sp-vacio','Primera vez · sin pila',()=>mk({pila:[],hint:true}),
 {tags:[['new','Estado vacío'],['keep','La cafeína de tus comidas se sigue viendo']],
  p:['Sin suplementos configurados, el módulo no se esconde: la cafeína de tus comidas y bebidas sigue contando contra su máximo, como hoy. Debajo, una invitación breve y cinco sugerencias de un toque (creatina, cafeína, vitamina D, omega-3, magnesio) que se encienden y arman la pila al instante.',
     '«Armar mi pila» abre el catálogo completo. Sin tarjeta dentro de tarjeta ni ilustración grande: el vacío es una frase, cinco opciones y un botón.']});
sceneSp('sp-hora','Hora de tomarlo · «Toca ahora»',()=>mk({now:'omega3',pila:(()=>{const p=pilaBase();return p})()}),
 {tags:[['new','Aviso en el módulo'],['ask','Recordatorios por suplemento']],
  p:['Cuando llega la hora del recordatorio, esa fila gana el botón en crema y el texto «Toca ahora · con la comida». Es el único color de énfasis del módulo; no hay alarmas dentro de la pantalla.',
     'Hoy <b>no existen recordatorios de suplementos</b>: solo los de comida (<code>NutritionReminderPlanner</code>, alarma diaria a una hora de reloj). Reutilizar ese mismo motor es lo natural, pero es una función nueva.']});
sceneSp('sp-completo','Día con todo tomado',()=>mk({pila:pilaBase().filter(it=>it.id!=='magnesio').map(it=>({...it,times:[{t:it.mom<=1?'08:40':it.mom===2?'12:30':it.mom===3?'10:00':'11:20',d:it.dose}]})),food:95,week:[1,1,1,1,1,1]}),
 {tags:[['new','Estado completo']],
  p:['Con la pila completa, el subtítulo pasa a «Todo tomado» en verde, desaparece la ayuda de pulsación larga y los botones se sustituyen por la hora de cada toma. La semana se ve entera de tinta.',
     'En Inicio, la franja de suplementos también se cierra con «Todo tomado» (ver pestaña Inicio de esta sección).']});
sceneSp('sp-techo','Techo superado · cafeína',()=>mk({food:336,pila:pilaBase().map(it=>it.id==='cafeina'?{...it,times:[{t:'12:40',d:100}]}:it)}),
 {tags:[['keep','El rojo solo cuando se pasa del máximo'],['new','Mensaje sobrio']],
  p:['Cafeína 436 mg frente a un máximo de 400: la cifra y la barra pasan a rojo y el texto dice cuánto te pasas, sin dramatizar («es una referencia, no una orden»). Lo demás del módulo no cambia de color.',
     'El máximo es el que ya calcula la app (6 mg/kg con tope de 400 mg; 200 mg en embarazo/lactancia; menores de 18, 2,5 mg/kg).']});
sceneSp('sp-aviso','Aviso antes de pasar el máximo',()=>mk({food:310,sheet:{k:'aviso',id:'cafeina',d:100,t:NOW}}),
 {tags:[['new','Aviso al registrar'],['ask','¿Avisar o bloquear?']],
  p:['Si un toque llevara la cafeína (o la vitamina D, el magnesio, el zinc, el hierro o el omega-3) por encima de su techo, una hoja corta lo cuenta y deja <b>registrar igual</b>: la app informa, no manda.',
     '<b>Decisión tuya:</b> propongo avisar y no bloquear. Para bloquear habría que decidir qué hacer con quien ya tomó la dosis y solo quiere anotarla.']});
sceneSp('sp-ajustar','Mantener pulsado · ajustar dosis y hora',()=>mk({sheet:{k:'ajustar',id:'cafeina',d:100,w:0}}),
 {tags:[['new','Ajuste de una toma']],
  p:['Dosis grande con − y +, atajos «½ · Habitual · ×2» y la hora (ahora, hace 30 min, hace 1 h, esta mañana). Abajo, <b>cómo queda el día con esta toma</b> frente a su máximo o a tu objetivo: si te pasas aparece el aviso aquí mismo y el botón sigue activo.',
     'El botón de ajustes (deslizadores) abre la configuración del suplemento. Se puede probar: sube la dosis hasta pasar de 400 mg.']});
sceneSp('sp-anadir','Hoja de añadir · catálogo',()=>mk({sheet:{k:'anadir',cat:-1}}),
 {tags:[['new','Catálogo de 14'],['ask','Alcance de la lista']],
  p:['Catorce suplementos en tres grupos, con su dosis típica y la solidez de la evidencia. El <b>+</b> de la derecha lo añade a tu pila con su dosis habitual (otro toque lo quita); tocar el nombre abre su configuración. «Crear uno propio» cubre lo que no esté.',
     'Criterio: ISSN para rendimiento (creatina, cafeína, proteína, beta-alanina, citrulina) y NIH/EFSA para micronutrientes. Sin marcas ni mezclas de fórmula propietaria, y sin quemadores ni «potenciadores» sin respaldo.'],
  listTitle:'Lo que NO incluí y por qué',list:['BCAA y glutamina: la proteína ya los cubre.','Quemadores de grasa y potenciadores de testosterona: sin evidencia fiable.','Colágeno, probióticos: puedes crearlos con «Crear uno propio».']});
sceneSp('sp-alta','Configurar · alta desde el catálogo',()=>mk({page:'alta',back:'nutri',form:{mode:'new',id:'zinc',dose:10,tomas:1,mom:5,remOn:true,rem:'20:00',notes:'',unit:'g'}}),
 {tags:[['new','Alta'],['new','Recordatorio por suplemento'],['ask','Avisos de seguridad']],
  p:['Una sola página de ajustes con lo necesario: dosis habitual (la de un toque), tomas al día, objetivo diario (dosis × tomas), el techo conocido, momento, recordatorio con su hora y notas. Debajo, <b>qué aporta a tu huella</b>: aquí, Zinc, comparado con su límite total.',
     'La advertencia es una sola frase general y sobria; el techo y su fuente (EFSA/NIH) van en la propia fila. No hay consejos médicos ni diagnósticos.']});
sceneSp('sp-propio','Configurar · suplemento propio',()=>mk({page:'alta',back:'nutri',form:{mode:'custom',id:'propio',dose:1,tomas:1,mom:1,remOn:false,rem:'09:00',notes:'',unit:'g'}}),
 {tags:[['new','Suplemento propio'],['ask','¿Rellenar nutrientes de la etiqueta?']],
  p:['Para lo que no está en el catálogo: nombre, unidad (g, mg, µg o unidades) y dosis. Un suplemento propio solo registra; <b>no suma a la huella de nutrientes</b>.',
     '<b>Pregunta:</b> ¿quieres poder escribir los nutrientes de la etiqueta (o escanearla con el lector de códigos que ya existe) para que sí sumen? Es posible pero es un flujo más grande.']});
sceneSp('sp-edicion','Editar · creatina',()=>mk({page:'alta',back:'nutri',form:{mode:'edit',id:'creatina',dose:5,tomas:1,mom:1,remOn:true,rem:'08:00',notes:'Con el batido del desayuno',unit:'g'}}),
 {tags:[['keep','Seguimiento de creatina'],['new','Dejar de tomar']],
  p:['La misma página, en modo edición. Para la creatina se conserva el <b>seguimiento de saturación</b> (pauta de carga o gradual) como una fila que abre su hoja de siempre. «Dejar de tomar» está al pie, en rojo suave, siempre con confirmación.']});
sceneSp('sp-baja','Dejar de tomar · confirmación',()=>mk({page:'alta',back:'nutri',form:{mode:'edit',id:'creatina',dose:5,tomas:1,mom:1,remOn:true,rem:'08:00',notes:'',unit:'g'},sheet:{k:'baja',id:'creatina'}}),
 {tags:[['new','Baja']],
  p:['La baja <b>no borra nada del historial</b>: cada toma ya es una entrada de comida (hoy se guarda como «Creatina Monohidrato» en el tentempié), así que sus totales se mantienen. Solo sale de la pila y de Inicio, y apaga su recordatorio.']});
sceneSp('sp-detalle','Detalle · creatina',()=>mk({page:'detalle',back:'nutri',detail:'creatina'}),
 {tags:[['new','Historial corto'],['keep','Racha de creatina']],
  p:['Al tocar el nombre de un suplemento: el total de hoy, su racha, las tomas de hoy (con «Quitar» por si te equivocaste), 30 días de un vistazo y qué aporta a tu huella. En la creatina, la fila de seguimiento de saturación. El botón fijo registra la dosis habitual.']});
sceneSp('sp-detalle-cafeina','Detalle · cafeína (comidas + suplementos)',()=>mk({page:'detalle',back:'nutri',detail:'cafeina',pila:pilaBase().map(it=>it.id==='cafeina'?{...it,times:[{t:'12:40',d:100}]}:it)}),
 {tags:[['keep','Total con comidas'],['new','Desglose por origen']],
  p:['La cafeína es el caso especial: su total mezcla lo que ya sabía la app (café, té, bebidas de tus comidas) con los suplementos. El detalle los separa para que se entienda de dónde sale cada miligramo; solo los suplementos se pueden quitar desde aquí (lo demás se edita en su comida).']});
sceneSp('sp-creatina','Seguimiento de creatina · hoja que ya existe',()=>mk({sheet:{k:'creatina',p:1}}),
 {tags:[['keep','Misma hoja y mismos textos']],
  p:['Sin cambios de contenido: se redibuja en el gris plano vigente. Textos y dosis salen de <code>CreatineSaturationOverlay</code> (carga: 0,3 g/kg × 6 días y luego mantenimiento de 3 a 5 g; gradual: 5 g/día durante ~28 días). Se abre la primera vez que registras creatina y desde su detalle.']});
sceneSp('sp-inicio','Inicio · «Nutrición de hoy» con suplementos',()=>mk({page:'inicio',now:null}),
 {tags:[['new','Acceso rápido desde Inicio'],['keep','Botón «Registrar comida»']],
  p:['La tarjeta de Inicio (como la dejó arreglos-v1: anillo fino, tres macros y «Registrar comida») gana una franja <b>Suplementos 2 de 6</b> con los pendientes como botones de un toque, ordenados por momento. Máximo tres; el resto cabe en «+3 más», que abre la lista completa.',
     'Un toque registra la dosis habitual; mantener pulsado abre el ajuste, igual que en Nutrición. Al registrar proteína, <b>el anillo y la barra de proteína se actualizan</b> (con rebote); al registrar cafeína, la línea «Cafeína de hoy» sube. Si no tienes pila, la franja no aparece.']});
sceneSp('sp-inicio-hecho','Inicio · pila completa',()=>mk({page:'inicio',pila:pilaBase().filter(it=>it.id!=='magnesio').map(it=>({...it,times:[{t:'09:00',d:it.dose}]}))}),
 {tags:[['new','Estado completo']],
  p:['Cuando no queda nada por tomar, los botones desaparecen y la franja se reduce a una línea («Todo tomado»); sigue siendo un acceso a la lista. La tarjeta recupera su altura de siempre.']});
sceneSp('sp-rapida','Inicio · lista rápida de hoy',()=>mk({page:'inicio',sheet:{k:'rapida'},now:'omega3'}),
 {tags:[['new','Hoja de suplementos de hoy']],
  p:['Tocar el encabezado «Suplementos» (o «+N más») abre en una hoja la misma pila de Nutrición, con los mismos botones y gestos, sin salir de Inicio. Desde ahí también se puede añadir uno o abrir Nutrición.']});
scene({id:'sp-notif',tab:'suplementos',title:'Recordatorio · notificación',
 html:()=>`<div class="sp-shade"><div class="sp-clk">13:00</div><div class="sp-cd">Miércoles 29 de octubre</div>
  <div class="sp-ntf"><div class="a"><i>${ic('bowl',12)}</i>KPKN Fit · ahora</div><strong>Hora de tu omega-3</strong><p>1 g con la comida. Un toque y queda registrado.</p>
  <div class="b"><button class="f">Más tarde</button><button>Tomar 1 g</button></div></div></div>`,
 notes:{tags:[['new','Notificación con acción'],['ask','¿Con botón de registrar?']],
  p:['El recordatorio dice qué y cuánto, y trae <b>dos acciones</b>: «Tomar 1 g» registra sin abrir la app y «Más tarde» lo pospone una hora. Tocar la notificación abre Nutrición.',
     'Requiere el permiso de notificaciones que la app ya pide para los avisos de comida. «Tomar» desde la notificación es opcional; si lo prefieres, solo abre la app.']}});
