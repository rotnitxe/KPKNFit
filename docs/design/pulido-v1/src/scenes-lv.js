// Paquete VIVO-TARJETA (lv): repeticiones en carrusel, esfuerzo con calor, roadmap rediseñado y ROM conectado.
// Todo el CSS vive en src/lv.css. Datos de la sesión: «Legs II · Semana 1» (la de las capturas del usuario).
Object.assign(P,{
  minus:'<path d="M5.5 12h13"/>',
  list:'<path d="M9 6h11M9 12h11M9 18h11"/><circle cx="4.6" cy="6" r=".9" fill="currentColor"/><circle cx="4.6" cy="12" r=".9" fill="currentColor"/><circle cx="4.6" cy="18" r=".9" fill="currentColor"/>',
  timer:'<circle cx="12" cy="13.5" r="7"/><path d="M12 10v3.8l2.2 1.4M9.5 3.5h5"/>',
  romIc:'<path d="M4 19h16M4 19L15.5 6.5"/><path d="M11 19a7 7 0 00-1.8-4.6"/>',
  adv:'<path d="M5 7h9M18 7h1M5 17h3M12 17h7"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>',
  refresh:'<path d="M20 11a8 8 0 00-14-4.6L4 9M4 4v5h5M4 13a8 8 0 0014 4.6L20 15M20 20v-5h-5"/>',
  chevd:'<path d="M6 9.5l6 6 6-6"/>',
  star:'<path d="M12 4l2.4 5 5.4.6-4 3.7 1.1 5.4L12 16l-4.9 2.7 1.1-5.4-4-3.7 5.4-.6z"/>',
  chevu:'<path d="M6 14.5l6-6 6 6"/>',
  img:'<rect x="4" y="5" width="16" height="14" rx="3"/><circle cx="9" cy="10" r="1.6"/><path d="M5 17l5-4.5 3 2.5 3-3 3 3.5"/>',
});
const LV_EX=[
  {n:'Peso Muerto Convencional',sub:'Barra',short:'Peso Muerto',next:'Sentad.'},
  {n:'Sentadilla "Belt Squat"',sub:'',short:'Sentadilla',next:'Hip Thrust'},
  {n:'Hip Thrust',sub:'Barra',short:'Hip Thrust',next:'Sentad.'},
  {n:'Sentadilla en Máquina Pendulum',sub:'',short:'Pendulum',next:'Patada'},
  {n:'Patada de Glúteo',sub:'Polea',short:'Patada',next:'Plancha'},
  {n:'Plancha Copenhague Dinámica',sub:'',short:'Plancha',next:null},
];
const lvJ=o=>JSON.stringify(o).replace(/'/g,'&#39;');
const lvC=n=>String(n).replace('.',',');
const lvCheck=(s=14,w=2.6)=>`<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path class="ck" d="M5 12.5l4.5 4.5L19 7.5"/></svg>`;
function lvDims(roadH=112,roadB=22){
  const [W,H]=state.size;
  const cardH=Math.max(374,Math.min(430,H-190-roadB-roadH-44-14));
  const tight=cardH<404||W<350;
  const space=H-roadB-roadH-200;
  const top=200+Math.max(0,(space-cardH-40)/2);
  return {W,H,cardH,tight,top:Math.round(top),roadB};
}
/* ============================ PIEZAS DE LA TARJETA ============================ */
function lvRepsB(c){
  const cfg={min:null,max:null,start:5,plan:null,amrap:false,fail:false,N:50,...c};
  return `<div class="lv-repsb"><div class="lv-bh"><span class="lv-lab">${cfg.label||'REPETICIONES'}</span><span class="r" data-r></span></div>
  <div class="lv-car${cfg.fail?' dis':''}" tabindex="${cfg.fail?-1:0}" role="slider" aria-label="Repeticiones" aria-valuemin="0" aria-valuemax="${cfg.N}" aria-valuenow="${cfg.start}" data-cfg='${lvJ(cfg)}'><i class="lv-car-base"></i><i class="lv-car-rail"></i><i class="lv-car-lens"></i></div></div>`;
}
function lvEffB(c={}){
  return `<div class="lv-effb" data-cfg='${lvJ({plan:16,start:null,...c})}'><div class="lv-bh"><span class="lv-lab">ESFUERZO</span><span class="lv-evalue"></span></div>
  <div class="lv-eff" tabindex="0" role="slider" aria-label="Esfuerzo" aria-valuemin="0" aria-valuemax="21"><div class="lv-etrack"><i class="lv-efill"></i></div><i class="lv-eplan"></i><canvas class="lv-fx"></canvas><b class="lv-ethumb"></b></div>
  <div class="lv-leg"><span class="a">RPE 0</span><span class="p">RPE 8 · plan</span><span class="f">Fallo</span></div></div>`;
}
function lvTimeB(c={}){
  return `<div class="lv-repsb"><div class="lv-bh"><span class="lv-lab">TIEMPO</span><span class="r" style="gap:4px;color:var(--lv-mut)">${ic('play',12)}<span>0:00</span></span></div>
  <div style="display:flex;align-items:center;gap:12px;height:49px"><div style="display:flex;align-items:baseline;gap:6px;flex:1"><span style="font:800 36px/1 Syne;letter-spacing:-1.5px;font-feature-settings:'lnum' 1,'tnum' 1">${c.v||45}</span><span style="font:400 calc(12px*var(--fs)) Inter;color:var(--lv-fnt)">s</span></div>
  <div class="lv-stp"><b style="width:41px;height:40px">${ic('minus',16)}</b><b style="width:41px;height:40px">${ic('plus',16)}</b></div></div></div>`;
}
function lvRomB(c={}){
  const v=c.v??100;
  return `<div class="lv-rom" data-cfg='${lvJ({v})}'><div class="lvl"><small>ROM</small><b data-rom-val>${v} %</b></div>
  <div class="lv-rsl" tabindex="0" role="slider" aria-label="Rango de movimiento" aria-valuemin="10" aria-valuemax="100" aria-valuenow="${v}"><div class="lv-rtrack"><i class="lv-rfill"></i></div><b class="lv-rthumb"></b><div class="lv-rtip"></div></div>
  <button class="more" aria-label="Abrir detalle del registro">Detalle ${ic('fwd',12)}${c.dot?'<i></i>':''}</button></div>`;
}
function lvDetB(c={}){
  return `<div class="lv-det" role="button"><span>${c.text||'Parciales, ayuda y ROM'}</span>${ic('fwd',12)}</div>`;
}
function lvActions(){
  return `<div class="lv-actions"><span>${ic('adv',15)}<em class="tx">Avanzado</em></span><span>${ic('camera',15)}<em class="tx">Modo visual</em></span><span class="h" aria-label="Historial del ejercicio">${ic('history',17)}</span></div>`;
}
function lvCard(o){
  const L=o.load||{};
  return `<div class="lv-card" data-card>
  <div class="lv-kick"><span data-kick>${o.purpose||'SERIE '+(o.n||1)}<i> / ${o.total||3}</i></span><span class="tune" aria-label="Configurar esta serie">${ic('adv',17)}</span></div>
  <div class="lv-load"><div class="lv-lrow"><span class="lv-lab">${L.label||'CARGA'}</span><span class="lv-mode">${L.mode||'Externa'}${ic('down',11)}</span></div>
    <div class="lv-hero"><div class="v"><span class="n ${L.dash?'dash':''}">${L.dash?'—':(L.val||'120')}</span>${L.dash?'':'<span class="u">kg</span>'}</div><div class="lv-stp"><b>${ic('minus',16)}</b><b>${ic('plus',16)}</b></div></div>
    <div class="lv-ref"><span>${L.ref||'Anterior · 120 × 5 · RIR 2'}</span><span class="h">${ic('history',16)}</span></div></div>
  <div class="lv-body">${o.qty}${o.eff||lvEffB()}${o.det||lvDetB()}</div>${lvActions()}</div>`;
}
/* ============================ ROADMAP ============================ */
// Modelo: {ex, sets:[{st:'p'|'d'|'s',rom}], act:'s0'|'rest'|'p0'|'next', prep:[{k,st}], rest:{t:'2:41',f:.3}|null, next:'Sentad.', done, total, pend:[], mode:'record'|'update'|'rest', label}
function lvWindow(nodes,max=7){
  if(nodes.length<=max)return {nodes,fl:false,fr:false};
  const next=nodes[nodes.length-1].t==='next'?nodes[nodes.length-1]:null;
  const core=next?nodes.slice(0,-1):nodes;
  const size=max-(next?1:0);
  let a=core.findIndex(n=>n.act);if(a<0)a=0;
  const start=Math.max(0,Math.min(a-2,core.length-size));
  const end=Math.min(core.length,start+size);
  return {nodes:core.slice(start,end).concat(next?[next]:[]),fl:start>0,fr:end<core.length};
}
function lvNodes(m){
  const out=[];
  (m.prep||[]).forEach((p,i)=>out.push({t:'prep',k:'p'+i,lab:p.k,st:p.st,act:m.act==='p'+i}));
  m.sets.forEach((s,i)=>{
    out.push({t:'set',k:'s'+i,i,st:s.st,rom:s.rom,act:m.act==='s'+i,just:m.just===i});
    if(m.rest&&m.rest.after===i)out.push({t:'rest',k:'rest',act:m.act==='rest',time:m.rest.t,f:m.rest.f});
  });
  if(m.next)out.push({t:'next',k:'next',lab:m.next,act:m.act==='next'});
  return out;
}
function lvPill(n){
  const act=n.act?' isa':'';
  if(n.t==='rest')return `<button class="lv-pill isr" data-k="rest" aria-label="Descanso, restan ${n.time}"><i class="dr" style="width:${Math.round((1-n.f)*100)}%"></i><span data-rest-t>${n.time}</span></button>`;
  if(n.t==='next')return `<button class="lv-pill next${act}" data-k="next" aria-label="Ejercicio siguiente: ${n.lab}"><span class="nm">${n.lab}</span>${ic('fwd',13)}</button>`;
  if(n.t==='prep'){
    const done=n.st==='d';
    return `<button class="lv-pill prep${act}${done?' done':''}" data-k="${n.k}" aria-label="${n.lab==='Mov'?'Movilidad':'Aproximaciones'}${done?', completada':''}">${done?lvCheck(13):''}<span>${n.lab}</span></button>`;
  }
  const num=n.i+1;
  const done=n.st==='d',skip=n.st==='s';
  const cls=`lv-pill${act}${done?' done':''}${skip?' skip':''}${n.just?' just':''}`;
  const gauge=done&&n.rom!=null?`<span class="gg"><i style="width:${n.rom}%"></i></span>`:'';
  let inner;
  if(n.act)inner=`${done?lvCheck(13):''}<span>Serie ${num}</span>`;
  else if(done)inner=`${lvCheck(14)}${gauge}`;
  else if(skip)inner=`<span>–</span>`;
  else inner=`<span>${num}</span>`;
  return `<button class="${cls}" data-k="${n.k}" aria-label="Serie ${num}${done?', registrada'+(n.rom!=null?', ROM '+n.rom+' %':''):skip?', omitida':''}">${inner}</button>`;
}
function lvRoadInner(m){
  const w=lvWindow(lvNodes(m));
  const p=m.total?Math.min(1,m.done/m.total):0;
  const pend=(m.pend||[]);
  const ex={...LV_EX[m.ex||0],...(m.exName?{n:m.exName}:{})};
  const mode=m.mode||'record';
  const label=m.label||(mode==='rest'?'Terminar descanso':mode==='update'?'Actualizar serie':'Registrar serie');
  const icon=mode==='rest'?ic('play',15):mode==='update'?ic('refresh',16):lvCheck(17,2.2);
  return `<div class="lv-rh"><button class="lv-route" aria-label="Abrir ruta de la sesión">${ic('list',16)}<span class="nm">${pend.length?'Ruta':ex.n}</span>${ic('chevd',13)}</button>${pend.map(x=>`<button class="lv-pend">${x}</button>`).join('')}
    <span class="lv-prog" aria-label="Avance de la sesión ${Math.round(p*100)} %"><svg width="18" height="18" viewBox="0 0 18 18"><circle cx="9" cy="9" r="7" fill="none" stroke="#ffffff1a" stroke-width="2"/><circle class="arc" cx="9" cy="9" r="7" fill="none" stroke="#f2eee6" stroke-width="2" stroke-linecap="round" stroke-dasharray="44" stroke-dashoffset="${(44*(1-p)).toFixed(1)}" transform="rotate(-90 9 9)"/></svg><span><b data-done>${m.done}</b><i>/${m.total}</i></span></span></div>
  <div class="lv-rail" role="group" aria-label="Series del ejercicio">${(w.fl?'<span class="lv-gap" aria-hidden="true">···</span>':'')+w.nodes.map((n,i)=>(w.fr&&n.t==='next'?'<span class="lv-gap" aria-hidden="true">···</span>':'')+lvPill(n)).join('')}</div>
  <div class="lv-rb"><button class="lv-nav" aria-label="Paso anterior"><b>${ic('back',18)}</b></button><button class="lv-rec lvm-${mode}${m.off?' off':''}"><i class="wipe"></i>${icon}<span>${label}</span></button><button class="lv-nav" aria-label="Paso siguiente"><b>${ic('fwd',18)}</b></button></div>`;
}
const lvRoad=(m)=>`<div class="lv-road" data-road='${lvJ(m)}'>${lvRoadInner(m)}</div>`;
const lvSets=(s)=>s.split('').map(c=>({st:c}));
function lvM(o){return {ex:0,sets:lvSets('ppp'),act:'s0',next:'Sentad.',done:0,total:18,...o}}
/* ============================ MARCO ============================ */
function lvScreen(o={}){
  const d=lvDims(o.roadH||112,o.roadB||22);
  const ex={...LV_EX[o.ex||0],...(o.exName?{n:o.exName,sub:o.exSub||''}:{})};
  const done=o.done??0;
  return `<div class="lv ${d.tight?'lv-tight':''} ${o.cls||''}" style="--lvh:${d.cardH}px;--lv2-b:${d.roadB}px">
  <div class="lv-island"><div class="t"><b>Legs II · Semana 1</b><span data-islet>${o.isl||'6 ejercicios · '+done+' de 18 series registradas'}</span></div><div class="lv-timer">${ic('timer',16)}<span>${o.time||'0:21'}</span></div><div class="lvib">${ic('more',20)}</div></div>
  <div class="lv-title"><div style="flex:1;min-width:0"><h1 data-title>${ex.n}</h1>${ex.sub?`<small data-sub>${ex.sub}</small>`:'<small data-sub></small>'}</div><div class="mic">${ic('mic',22)}</div></div>
  <div class="lv-cardwrap" style="top:${d.top}px;height:${d.cardH}px"><div class="lv-ghost g2" style="top:${d.cardH-60}px;height:100px;padding-bottom:2px">Serie 3 · 5 reps</div><div class="lv-ghost" style="top:${d.cardH-60}px;height:86px">Serie 2 · 5 reps</div>${o.card}</div>
  ${o.road===false?'':(o.roadHtml||lvRoad(o.m||lvM({done})))}
  ${o.over||''}</div>`;
}
const lvStd=(o={})=>{const {card,...rest}=o;return lvScreen({card:lvCard({qty:lvRepsB({min:4,max:6,plan:5,start:5}),...(card||{})}),...rest})};
/* ============================ ENGINES ============================ */
const lvReduce=()=>{try{return matchMedia('(prefers-reduced-motion: reduce)').matches}catch(e){return false}};
const lvLerp=(a,b,t)=>a+(b-a)*t;
const lvHex=h=>[parseInt(h.slice(1,3),16),parseInt(h.slice(3,5),16),parseInt(h.slice(5,7),16)];
const lvMix=(a,b,t)=>{const x=lvHex(a),y=lvHex(b);return 'rgb('+x.map((v,i)=>Math.round(lvLerp(v,y[i],t))).join(',')+')'};
/* ---- carrusel de repeticiones ---- */
function lvCarousel(el){
  const cfg=JSON.parse(el.dataset.cfg);
  const CELL=54,N=cfg.N,lens=el.querySelector('.lv-car-lens'),rail=el.querySelector('.lv-car-rail');
  const head=el.closest('.lv-repsb').querySelector('[data-r]');
  const W=el.clientWidth||279;
  const its=[];
  for(let i=0;i<=N;i++){const it=document.createElement('div');it.className='lv-car-it';it.innerHTML='<span>'+i+'</span>';el.insertBefore(it,lens);its.push(it)}
  const inRange=i=>cfg.min==null?false:(i>=cfg.min&&(cfg.max==null||i<=cfg.max));
  let pos=cfg.start,val=Math.round(pos);
  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  function head_(){
    if(!head)return;
    if(cfg.fail){head.innerHTML='Serie fallida';head.className='r';return}
    const v=val;
    if(cfg.amrap){
      const d=v-cfg.min;head.className='r'+(d>=0?' in':'');head.innerHTML=(d>=0?ic('check',12)+' ':'')+'Mínimo '+cfg.min+(d<0?' · −'+(-d):'');return;
    }
    if(cfg.min==null){head.innerHTML='';return}
    const plan=cfg.min===cfg.max?String(cfg.min):cfg.min+'–'+cfg.max;
    if(v>=cfg.min&&v<=cfg.max){head.className='r in';head.innerHTML=ic('check',12)+' Plan '+plan}
    else{head.className='r';head.innerHTML='Plan '+plan+' · '+(v<cfg.min?'−'+(cfg.min-v):'+'+(v-cfg.max))}
    lens.classList.toggle('below',v<cfg.min);
  }
  function draw(){
    const c=W/2;
    for(let i=0;i<=N;i++){
      const d=i-pos,ad=Math.abs(d);
      const it=its[i];
      if(ad>3.2){if(it._h!==1){it.style.visibility='hidden';it._h=1}continue}
      if(it._h===1){it.style.visibility='visible';it._h=0}
      const s=ad<=1?1-.36*ad:ad<=2?.64-.16*(ad-1):.48-.1*(ad-2);
      let a=ad<=1?1-.38*ad:ad<=2?.62-.34*(ad-1):.28-.28*Math.min(1,ad-2);
      const rng=inRange(i);
      const dim=cfg.min!=null&&!rng&&!cfg.fail;
      if(dim)a*=lvLerp(1,.62,clamp(ad/.6,0,1));
      const col=cfg.fail?(ad<.5?'#ff9b92':'#8e8980'):ad<.62?lvMix('#111111','#f2eee6',clamp((ad-.3)/.32,0,1)):(rng||cfg.min==null?'#f2eee6':'#8e8980');
      it.style.transform='translate3d('+(c+d*CELL-CELL/2).toFixed(1)+'px,0,0)';
      const sp=it.firstChild;
      sp.style.transform='scale('+s.toFixed(3)+')';sp.style.opacity=clamp(a,0,1).toFixed(3);sp.style.color=col;
    }
    if(cfg.min!=null&&!cfg.fail){
      const x0=c+(cfg.min-.5-pos)*CELL+7;
      const w=cfg.amrap?(N-cfg.min+1)*CELL:(cfg.max-cfg.min+1)*CELL-14;
      rail.style.transform='translateX('+x0.toFixed(1)+'px)';rail.style.width=w.toFixed(1)+'px';
      rail.classList.toggle('open',!!cfg.amrap);
    }else rail.style.display='none';
  }
  function setVal(v){if(v!==val){val=v;el.setAttribute('aria-valuenow',v);head_();lens.classList.add('tk');clearTimeout(lens._t);lens._t=setTimeout(()=>lens.classList.remove('tk'),90)}}
  function snapFx(){lens.classList.remove('pulse');void lens.offsetWidth;if(!lvReduce())lens.classList.add('pulse')}
  let anim=null;
  function go(target,ms){
    const from=pos,t0=performance.now();target=clamp(target,0,N);
    if(lvReduce()||ms<=0){pos=target;setVal(target);draw();snapFx();return}
    cancelAnimationFrame(anim);
    const step=t=>{const k=Math.min(1,(t-t0)/ms),e=1-Math.pow(1-k,3);pos=lvLerp(from,target,e);setVal(Math.round(pos));draw();if(k<1)anim=requestAnimationFrame(step);else{pos=target;draw();snapFx()}};
    anim=requestAnimationFrame(step);
  }
  if(cfg.fail)lens.classList.add('fail');
  head_();draw();
  if(cfg.fail)return;
  let drag=false,sx=0,sp=0,moved=false,lastX=0,lastT=0,vel=0;
  el.addEventListener('pointerdown',e=>{cancelAnimationFrame(anim);drag=true;moved=false;sx=lastX=e.clientX;sp=pos;lastT=performance.now();vel=0;try{el.setPointerCapture(e.pointerId)}catch(_){}});
  el.addEventListener('pointermove',e=>{
    if(!drag)return;const dx=e.clientX-sx;if(Math.abs(dx)>3)moved=true;
    if(!moved)return;
    const raw=sp-dx/CELL;pos=raw<0?raw*.35:raw>N?N+(raw-N)*.35:raw;
    const now=performance.now(),dt=Math.max(1,now-lastT);vel=lvLerp(vel,-(e.clientX-lastX)/CELL/dt,.35);lastX=e.clientX;lastT=now;
    setVal(clamp(Math.round(pos),0,N));draw();
  });
  const end=e=>{
    if(!drag)return;drag=false;
    if(!moved){const x=e.clientX-el.getBoundingClientRect().left;go(Math.round(pos+(x-W/2)/CELL),200);return}
    const proj=pos+clamp(vel*230,-6,6);
    go(Math.round(proj),clamp(180+90*Math.abs(Math.round(proj)-pos),200,520));
  };
  el.addEventListener('pointerup',end);el.addEventListener('pointercancel',end);
  el.addEventListener('keydown',e=>{if(e.key==='ArrowRight'||e.key==='ArrowUp'){e.preventDefault();go(Math.round(pos)+1,160)}else if(e.key==='ArrowLeft'||e.key==='ArrowDown'){e.preventDefault();go(Math.round(pos)-1,160)}});
  let wt=null;
  el.addEventListener('wheel',e=>{if(Math.abs(e.deltaX)<=Math.abs(e.deltaY))return;e.preventDefault();cancelAnimationFrame(anim);pos=clamp(pos+e.deltaX/CELL/2,0,N);setVal(Math.round(pos));draw();clearTimeout(wt);wt=setTimeout(()=>go(Math.round(pos),160),120)},{passive:false});
}
/* ---- esfuerzo con calor ---- */
const LV_FIRE=[ // [nivel, color del extremo caliente, mitad, desvanecido del tramo, resplandor px, alfa]
  [2,'#eccfb4','#e9c9a8',.26,7,.16],[3,'#f4b88f','#f2b080',.40,10,.28],[4,'#f49a6e','#f7cf73',.54,13,.42],[5,'#fff1c8','#f06a3e',.70,16,.6]];
function lvFireAt(L){
  if(L<2)return null;
  for(let i=0;i<LV_FIRE.length-1;i++){const a=LV_FIRE[i],b=LV_FIRE[i+1];if(L<=b[0]){const t=(L-a[0])/(b[0]-a[0]);return {tip:lvMix(a[1],b[1],t),mid:lvMix(a[2],b[2],t),tail:lvLerp(a[3],b[3],t),blur:lvLerp(a[4],b[4],t),alpha:lvLerp(a[5],b[5],t)}}}
  const z=LV_FIRE[LV_FIRE.length-1];return {tip:z[1],mid:z[2],tail:z[3],blur:z[4],alpha:z[5]};
}
const lvWord=i=>i===21?'todo':i*.5<6.5?'cómodo':i*.5<7.5?'firme':i*.5<8.5?'exigente':i*.5<9.5?'muy duro':'al límite';
const lvLevel=i=>i==null?0:i===21?5:Math.max(0,Math.min(4,i*.5-6));
const lvPcount=L=>{const c=[0,0,2,6,11,16],k=Math.max(0,Math.min(5,L)),lo=Math.min(4,Math.floor(k));return Math.round(c[lo]+(c[lo+1]-c[lo])*(k-lo))};
const lvPperiod=L=>{const p=[2.4,1.6,1.2,.8],k=Math.max(2,Math.min(5,L))-2,lo=Math.min(2,Math.floor(k));return p[lo]+(p[lo+1]-p[lo])*(k-lo)};
function lvEffort(root){
  const cfg=JSON.parse(root.dataset.cfg);
  const eff=root.querySelector('.lv-eff'),fill=root.querySelector('.lv-efill'),thumb=root.querySelector('.lv-ethumb'),planEl=root.querySelector('.lv-eplan'),cv=root.querySelector('canvas');
  const val=root.querySelector('.lv-evalue'),legA=root.querySelector('.leg .a')||root.querySelector('.lv-leg .a'),legP=root.querySelector('.lv-leg .p'),legF=root.querySelector('.lv-leg .f');
  const phone=root.closest('.lv');
  const W=eff.clientWidth||279,H=44,INSET=12.5,N=22,PLAN=cfg.plan;
  const still=lvReduce()||!!root.closest('.lv-static');
  const X=i=>INSET+(i/(N-1))*(W-2*INSET);
  let idx=cfg.start;
  const PADX=14,dpr=2;cv.width=(W+2*PADX)*dpr;cv.height=H*dpr;const g=cv.getContext('2d');g.scale(dpr,dpr);g.translate(PADX,0);
  planEl.style.left=X(PLAN)+'px';
  function legend(){
    const pw=legP.offsetWidth,fw=legF.offsetWidth,aw=legA.offsetWidth;
    legP.style.left=Math.min(Math.max(X(PLAN)-pw/2,aw+8),W-fw-8-pw)+'px';
  }
  let Ld=lvLevel(idx),Lt=Ld,flash=0,flashK=1,sparks=[],parts=[],tprev=null,clock=0,raf=null,started=false;
  function paintStatic(){
    const L=Lt,f=lvFireAt(L),x=X(idx==null?0:idx);
    thumb.style.left=x+'px';
    if(idx==null){fill.style.width='0';fill.style.background='';fill.style.boxShadow='none'}
    else{
      fill.style.width=x+'px';
      if(f){const a=Math.max(0,(1-f.tail)*100);fill.style.background=`linear-gradient(90deg,#e6e1d5 0,#e6e1d5 ${a.toFixed(1)}%,${f.mid} ${(a+(100-a)*.55).toFixed(1)}%,${f.tip} 100%)`;fill.style.boxShadow=`0 0 ${f.blur.toFixed(1)}px rgba(244,154,110,${f.alpha.toFixed(2)})`}
      else{fill.style.background='';fill.style.boxShadow='none'}
    }
    const hot=f?Math.min(1,(L-1.5)/3):0;
    thumb.style.background=idx==null?'#f2eee6':lvMix('#f2eee6',L>=5?'#fff1c8':'#f49a6e',Math.min(1,Math.max(0,(L-1.5)/2.2)));
    thumb.style.boxShadow=f?`0 0 0 1px #ffffff1c,0 0 ${(6+L*3.2).toFixed(0)}px rgba(244,154,110,${(.2+hot*.55).toFixed(2)})`:'0 0 0 1px #ffffff1c';
    if(idx==null)val.innerHTML='Sin marcar '+ic('down',11);
    else{
      const lab=idx===21?'Fallo':'RPE '+lvC(idx*.5);
      val.innerHTML=`<b>${lab}</b><em>· ${lvWord(idx)}</em>${ic('down',11)}`;
    }
    val.className='lv-evalue'+(L>=5?' max':L>=3?' hot':'');
    eff.setAttribute('aria-valuenow',idx==null?0:idx);
    eff.setAttribute('aria-valuetext',idx==null?'Sin marcar':(idx===21?'Fallo':'RPE '+lvC(idx*.5))+', '+lvWord(idx));
  }
  function frame(t){
    if(!cv.isConnected){return}
    const dt=tprev==null?.016:Math.min(.05,(t-tprev)/1000);tprev=t;clock+=dt;
    Ld+=(Lt-Ld)*Math.min(1,dt*7);if(Math.abs(Lt-Ld)<.005)Ld=Lt;
    draw(dt);
    raf=requestAnimationFrame(frame);
  }
  function draw(dt){
    g.clearRect(-PADX,0,W+2*PADX,H);
    if(idx==null)return;
    const L=Ld,x=X(idx),y=H/2;
    const f=lvFireAt(L);
    if(!f&&!flash&&!parts.length)return;
    // resplandor a lo largo del relleno y halo del pulgar (siempre dentro de la banda de 44 dp)
    const br=1+.08*Math.sin(clock*(L>=5?5.2:2.6));
    if(f){
      g.save();g.filter='blur(6px)';
      g.fillStyle=`rgba(244,154,110,${Math.min(.8,f.alpha*.95).toFixed(3)})`;g.fillRect(Math.max(4,x-120),y-2.5,Math.max(0,Math.min(116,x-4)),5);
      g.fillStyle=`rgba(255,226,176,${Math.min(.9,f.alpha*1.15*br).toFixed(3)})`;g.beginPath();g.arc(x,y,(8+L*1.5)*br,0,6.283);g.fill();
      g.restore();
    }
    // reflejo que viaja por el relleno hacia el pulgar (calor en movimiento)
    if(!still&&f&&L>=3&&x>40){
      const sp=.5+(L-3)*.28,ph=(clock*sp)%1,hx=lvLerp(8,x-8,ph),ha=Math.sin(Math.PI*ph)*(.1+.06*L);
      g.save();g.filter='blur(3.5px)';g.fillStyle=`rgba(255,236,206,${ha.toFixed(3)})`;g.fillRect(hx-14,y-3,28,6);g.restore();
    }
    // brasas: suben desde el relleno, siempre dentro de la banda
    const cnt=lvPcount(L);
    if(!still&&L>=1.9){
      const per=lvPperiod(L),rate=cnt/per;
      if(Math.random()<rate*dt*1.5&&parts.length<cnt+4){
        const span=Math.min(x-4,150);
        parts.push({x:x-Math.pow(Math.random(),1.7)*span,life:per*(.55+Math.random()*.4),t:0,rise:7+Math.random()*(4.5+L*1.25),sz:1.1+Math.random()*(1.2+L*.4),dr:(Math.random()-.5)*9,c:['#f7cf73','#f49a6e','#ffe2b0','#f06a3e'][Math.floor(Math.random()*(L>=4?4:3))]});
      }
    }
    for(let i=parts.length-1;i>=0;i--){
      const p=parts[i];p.t+=dt;const k=p.t/p.life;if(k>=1){parts.splice(i,1);continue}
      const e=1-Math.pow(1-k,2),px=p.x+p.dr*e,py=y-4-p.rise*e;
      g.globalAlpha=Math.sin(Math.PI*k)*.95;g.fillStyle=p.c;g.beginPath();g.arc(px,py,p.sz*(1-.35*k),0,6.283);g.fill();
    }
    g.globalAlpha=1;
    // destello vertical sobre el pulgar (cerca del fallo)
    if(!still&&L>=4){
      const fl=(Math.sin(clock*13)+Math.sin(clock*7.3+1)+2)/4,h=(L-3.2)*5.2*(.65+.35*fl);
      const gr=g.createLinearGradient(0,y-13,0,y-13-h);gr.addColorStop(0,`rgba(255,226,176,${(.5+.3*fl).toFixed(2)})`);gr.addColorStop(1,'rgba(244,154,110,0)');
      g.fillStyle=gr;g.beginPath();g.moveTo(x-2.6,y-13);g.quadraticCurveTo(x,y-13-h*1.15,x+2.6,y-13);g.fill();
    }
    // destello de llegada: anillo que se expande + chispas cortas
    if(flash>0){
      const k=1-flash,r=8+13*(1-Math.pow(1-k,2.2));
      g.strokeStyle=`rgba(255,235,200,${(flash*.85*flashK).toFixed(3)})`;g.lineWidth=1.6;g.beginPath();g.arc(x,y,r,0,6.283);g.stroke();
      const rg=g.createRadialGradient(x,y,0,x,y,21);rg.addColorStop(0,`rgba(255,241,200,${(flash*.7*flashK).toFixed(3)})`);rg.addColorStop(1,'rgba(255,241,200,0)');g.fillStyle=rg;g.fillRect(x-22,1,44,H-2);
      g.strokeStyle=`rgba(255,226,176,${(flash*flashK).toFixed(3)})`;g.lineWidth=1.3;
      sparks.forEach(s=>{const a=s.a,r0=10+k*14,r1=r0+s.l*(1-k);g.beginPath();g.moveTo(x+Math.cos(a)*r0,y+Math.sin(a)*r0*.8);g.lineTo(x+Math.cos(a)*r1,y+Math.sin(a)*r1*.8);g.stroke()});
      flash=Math.max(0,flash-dt/.46);
    }
  }
  function select(i,fx=true){
    const prev=idx;idx=Math.max(0,Math.min(N-1,i));
    Lt=lvLevel(idx);
    if(fx){
      thumb.classList.remove('pop');void thumb.offsetWidth;if(!still)thumb.classList.add('pop');
      if(!still&&idx!==prev){
        if(idx===21){flash=1;flashK=1;sparks=Array.from({length:9},(_,k)=>({a:-Math.PI*.95+Math.PI*.9*k/8+(Math.random()-.5)*.2,l:9+Math.random()*7}))}
        else if(idx>=19&&(prev==null||prev<19)){flash=.8;flashK=.55;sparks=Array.from({length:5},(_,k)=>({a:-Math.PI*.9+Math.PI*.8*k/4,l:6+Math.random()*4}))}
      }
    }
    paintStatic();legend();
    if(still){Ld=Lt;draw(0.016)}
  }
  function fromX(clientX){const r=eff.getBoundingClientRect(),x=(clientX-r.left)*(W/r.width);return Math.max(0,Math.min(N-1,Math.round((x-INSET)/(W-2*INSET)*(N-1))))}
  let drag=false;
  eff.addEventListener('pointerdown',e=>{drag=true;try{eff.setPointerCapture(e.pointerId)}catch(_){};select(fromX(e.clientX))});
  eff.addEventListener('pointermove',e=>{if(!drag)return;const i=fromX(e.clientX);if(i!==idx)select(i)});
  const up=()=>{drag=false};eff.addEventListener('pointerup',up);eff.addEventListener('pointercancel',up);
  eff.addEventListener('keydown',e=>{const c=idx==null?0:idx;if(e.key==='ArrowRight'||e.key==='ArrowUp'){e.preventDefault();select(idx==null?0:c+1)}else if(e.key==='ArrowLeft'||e.key==='ArrowDown'){e.preventDefault();select(c-1)}});
  legF.addEventListener('click',()=>select(21));
  // arranque: con valor inicial no se anima el destello (es el estado de reposo); el fuego sí late
  paintStatic();legend();Ld=Lt;
  if(still){draw(0.016)}else{for(let k=0;k<110;k++){clock+=1/60;draw(1/60)}raf=requestAnimationFrame(frame)}
  root._select=select;
}
/* ---- ROM (tarjeta y hoja) ---- */
const lvRomWord=v=>v>=95?'Completo':v>=65?'Casi completo':'Parcial';
function lvRom(root){
  const cfg=JSON.parse(root.dataset.cfg);
  const sl=root.querySelector('.lv-rsl'),fillE=root.querySelector('.lv-rfill'),thumb=root.querySelector('.lv-rthumb'),tip=root.querySelector('.lv-rtip'),out=root.querySelector('[data-rom-val]');
  const MARKS=[50,80,100],MIN=10,MAX=100,PAD=10;
  let W=sl.clientWidth||120;
  const X=v=>PAD+(v-MIN)/(MAX-MIN)*(W-2*PAD);
  const ticks=MARKS.map(m=>{const t=document.createElement('i');t.className='lv-rtick';sl.insertBefore(t,thumb);return t});
  let v=cfg.v;
  function paint(){
    if(sl.clientWidth)W=sl.clientWidth;
    ticks.forEach((t,i)=>{t.style.left=X(MARKS[i])+'px'});
    thumb.style.left=X(v)+'px';fillE.style.width=X(v)+'px';if(out)out.textContent=v+' %';
    ticks.forEach((t,i)=>t.classList.toggle('on',X(MARKS[i])<X(v)-3));
    sl.setAttribute('aria-valuenow',v);sl.setAttribute('aria-valuetext',v+' %, '+lvRomWord(v));
    tip.style.left=X(v)+'px';tip.textContent=v+' % · '+lvRomWord(v);
    document.querySelectorAll('[data-rom-link]').forEach(e=>{e.textContent=v});
    const z=v>=95?100:v>=65?80:50;
    (root.parentElement||root).querySelectorAll('[data-rv]').forEach(b=>b.classList.toggle('on',+b.dataset.rv===z));
  }
  function set(nv,fx,bc=true){
    nv=Math.round(nv/5)*5;MARKS.forEach(m=>{if(Math.abs(nv-m)<=3)nv=m});nv=Math.max(MIN,Math.min(MAX,nv));
    if(nv!==v&&fx&&!lvReduce()){thumb.classList.remove('pop');void thumb.offsetWidth;thumb.classList.add('pop')}
    v=nv;paint();
    if(bc){const lvr=root.closest('.lv');if(lvr)lvr.querySelectorAll('.lv-rom').forEach(o=>{if(o!==root&&o._set)o._set(v)})}
  }
  const fromX=cx=>{const r=sl.getBoundingClientRect();return MIN+((cx-r.left)*(W/r.width)-PAD)/(W-2*PAD)*(MAX-MIN)};
  let drag=false;
  sl.addEventListener('pointerdown',e=>{drag=true;try{sl.setPointerCapture(e.pointerId)}catch(_){};tip.classList.add('on');set(fromX(e.clientX),true)});
  sl.addEventListener('pointermove',e=>{if(drag)set(fromX(e.clientX),true)});
  const up=()=>{drag=false;setTimeout(()=>tip.classList.remove('on'),550)};sl.addEventListener('pointerup',up);sl.addEventListener('pointercancel',up);
  sl.addEventListener('keydown',e=>{if(e.key==='ArrowRight'||e.key==='ArrowUp'){e.preventDefault();set(v+5,true)}else if(e.key==='ArrowLeft'||e.key==='ArrowDown'){e.preventDefault();set(v-5,true)}});
  (root.parentElement||root).querySelectorAll('[data-rv]').forEach(b=>b.addEventListener('click',()=>set(+b.dataset.rv,true)));
  paint();
  root._set=nv=>set(nv,false,false);
}
/* ---- interruptores ---- */
function lvSwitches(ph){
  ph.querySelectorAll('.lv-sw').forEach(sw=>{
    const toggle=()=>{const on=!sw.classList.contains('on');sw.classList.toggle('on',on);sw.setAttribute('aria-checked',on);
      const tg=sw.dataset.link;
      if(tg==='rom'){ph.querySelector('.lv').classList.toggle('lv-rom-on',on);ph.querySelectorAll('.lv-rowrap[data-rom]').forEach(r=>{r.style.height=on?r.scrollHeight+'px':'0px';r.style.opacity=on?1:0});
        ph.querySelectorAll('[data-rom-chip]').forEach(c=>{c.innerHTML=on?' · <span class="rm">ROM</span>':''})}
    };
    sw.addEventListener('click',toggle);
    sw.addEventListener('keydown',e=>{if(e.key===' '||e.key==='Enter'){e.preventDefault();toggle()}});
  });
}
/* ---- roadmap con vida ---- */
function lvRoadWire(ph){
  const road=ph.querySelector('.lv-road');if(!road)return;
  const m0=JSON.parse(road.dataset.road);
  if(!m0.live)return;
  const S={ex:m0.ex||0,sets:LV_EX.map(()=>[0,0,0]),cur:0,rest:null,done:0};
  const TOTAL=18;
  let timer=null,toast=null;
  const nextPending=e=>S.sets[e].findIndex(x=>!x);
  function model(){
    const e=S.ex,ex=LV_EX[e];
    const sets=S.sets[e].map((x,i)=>({st:x?'d':'p'}));
    const mm={ex:e,sets,act:S.rest?'rest':'s'+S.cur,next:ex.next||null,done:S.done,total:TOTAL,just:S.just};
    if(S.rest)mm.rest={after:S.rest.after,t:S.rest.label,f:S.rest.f};
    mm.mode=S.rest?'rest':(S.just!=null?'record':(S.sets[e][S.cur]?'update':'record'));
    return mm;
  }
  function sync(mm,anim){
    road.dataset.road=lvJ({...mm,live:1});
    road.innerHTML=lvRoadInner(mm);
    const lv=ph.querySelector('.lv');
    lv.querySelector('[data-islet]').textContent='6 ejercicios · '+S.done+' de '+TOTAL+' series registradas';
    lv.querySelector('[data-title]').textContent=LV_EX[S.ex].n;
    lv.querySelector('[data-sub]').textContent=LV_EX[S.ex].sub;
    const k=lv.querySelector('[data-kick]');if(k)k.innerHTML='SERIE '+(S.cur+1)+'<i> / 3</i>';
    if(anim){const rb=road.querySelector('.lv-rec');rb.classList.add('swap')}
    lvFit(road);
  }
  function msg(t){
    if(toast)toast.remove();toast=document.createElement('div');
    toast.style.cssText='position:absolute;left:24px;right:24px;bottom:150px;z-index:90;padding:12px 14px;border-radius:16px;background:#242426;box-shadow:0 10px 30px #000c;font:500 12.5px/1.4 Inter;color:#f2eee6';
    toast.textContent=t;ph.querySelector('.lv').appendChild(toast);setTimeout(()=>{if(toast){toast.remove();toast=null}},3800);
  }
  function startRest(after){
    S.rest={after,left:10,label:'3:00',f:0};
    clearInterval(timer);
    const t0=performance.now();
    timer=setInterval(()=>{
      if(!road.isConnected){clearInterval(timer);return}
      const k=Math.min(1,(performance.now()-t0)/10000),sec=Math.max(0,Math.round(180*(1-k)));
      S.rest.f=k;S.rest.label=Math.floor(sec/60)+':'+String(sec%60).padStart(2,'0');
      const dr=road.querySelector('.dr'),tt=road.querySelector('[data-rest-t]');
      if(dr)dr.style.width=((1-k)*100)+'%';if(tt)tt.textContent=S.rest.label;
      if(k>=1)clearInterval(timer);
    },200);
  }
  function endRest(){
    clearInterval(timer);const after=S.rest.after;S.rest=null;
    const np=nextPending(S.ex);
    if(np>=0){S.cur=np}
    else if(S.ex<LV_EX.length-1){S.ex++;S.cur=Math.max(0,nextPending(S.ex))}
    sync(model(),true);
  }
  function record(){
    if(S.rest){endRest();return}
    const e=S.ex;
    if(S.sets[e][S.cur]){msg('Serie actualizada con los valores de la tarjeta.');return}
    const rb=road.querySelector('.lv-rec');rb.classList.remove('lvgo');void rb.offsetWidth;if(!lvReduce())rb.classList.add('lvgo');
    S.sets[e][S.cur]=1;S.done++;S.just=S.cur;
    if(S.done>=TOTAL){sync(model(),false);msg('Última serie de la sesión: aquí se abre la hoja de sesión completada.');return}
    const lastOfEx=nextPending(e)<0;
    setTimeout(()=>{S.just=null;startRest(S.cur);sync(model(),true)},lvReduce()?0:520);
    sync(model(),false);
  }
  function go(k){
    if(S.rest){clearInterval(timer);S.rest=null}
    if(k==='next'){if(S.ex<LV_EX.length-1){S.ex++;S.cur=Math.max(0,nextPending(S.ex))}}
    else if(k[0]==='s'){S.cur=+k.slice(1)}
    sync(model(),true);
  }
  function step(dir){
    if(S.rest){clearInterval(timer);S.rest=null}
    const n=S.cur+dir;
    if(n>=0&&n<3){S.cur=n}
    else if(dir>0&&S.ex<LV_EX.length-1){S.ex++;S.cur=0}
    else if(dir<0&&S.ex>0){S.ex--;S.cur=2}
    sync(model(),true);
  }
  road.addEventListener('click',e=>{
    const b=e.target.closest('button');if(!b)return;
    if(b.classList.contains('lv-rec'))return record();
    if(b.classList.contains('lv-pill'))return go(b.dataset.k);
    if(b.classList.contains('lv-nav')){const prev=b.querySelector('svg path').getAttribute('d').startsWith('M15');return step(prev?-1:1)}
    if(b.classList.contains('lv-route'))return msg('Abre «Recorrido de la sesión»: ejercicios, series y «Añadir ejercicio» (escena «Ruta»).');
  });
  sync(model(),false);
}
/* ---- texto que se reduce antes de cortarse (como el botón del roadmap de la app: 13 → 10 sp) ---- */
function lvFit(scope){
  scope.querySelectorAll('.lv-rec span,.lv-pend,.lv-route .nm').forEach(el=>{
    el.style.fontSize='';
    const base=parseFloat(getComputedStyle(el).fontSize),min=base*(10/14);
    let f=base;
    while(el.scrollWidth>el.clientWidth+0.5&&f>min){f-=.5;el.style.fontSize=f+'px'}
  });
}
/* ---- cableado general ---- */
function lvWire(ph){
  const fsv=parseFloat(getComputedStyle(ph).getPropertyValue('--fs'))||1;
  const root=ph.querySelector('.lv');if(root&&fsv>1.2)root.classList.add('lv-big');
  ph.querySelectorAll('.lv-car').forEach(lvCarousel);
  ph.querySelectorAll('.lv-effb').forEach(lvEffort);
  ph.querySelectorAll('.lv-rom').forEach(lvRom);
  lvSwitches(ph);
  lvRoadWire(ph);
  lvFit(ph);
}
/* ============================ ESCENAS ============================ */
const lvTagNew=['new','propuesta'],lvTagKeep=['keep','ya existe'];
/* ---------- 1 · repeticiones ---------- */
scene({id:'lv-reps-rango',tab:'vivo-tarjeta',title:'Repeticiones · carrusel con el rango del plan',
  html:()=>lvStd(),after:lvWire,
  notes:{tags:[lvTagNew,['move','sustituye a los 5 círculos']],img:'h15-reps-selector',imgCap:'Hoy: cinco círculos con Syne (cifras de distinta altura) y sin gesto de carrusel.',
    p:['Arrastra las cifras (o usa la rueda horizontal o las flechas): el valor se queda siempre en el centro. Toca un vecino y se centra.',
       'Aquí el plan es «4–6 reps»: el riel crema bajo las cifras es el rango; las cifras dentro del rango van en crema y las de fuera se atenúan. A la derecha del rótulo, «Plan 4–6» se enciende con un ✓ cuando estás dentro y dice «−1» o «+2» cuando no.'],
    listTitle:'Detalle',list:['Cifras Inter con tabulares y alineadas (lining + tnum): se acabó el desnivel de Syne.','Centro: bloque crema de 56 × 41 con la cifra en 32; vecinos al 64 % y 46 % de tamaño y con opacidad decreciente; los bordes se funden.','Imán al centro con pequeño rebote al encajar («háptico visual») y un tic más corto por cada cifra que cruzas. Con movimiento reducido: sin rebote.','Misma altura que hoy (rótulo 21 + banda 46): la tarjeta no cambia de tamaño.','La entrada exacta sigue en «Detalle del registro → Repeticiones completas».']}});
scene({id:'lv-reps-fijo',tab:'vivo-tarjeta',title:'Repeticiones · plan fijo y por debajo del plan',
  html:()=>lvScreen({card:lvCard({qty:lvRepsB({min:5,max:5,plan:5,start:4})})}),after:lvWire,
  notes:{tags:[lvTagNew],p:['El plan de tu programa es «3 × 5»: el rango se reduce a un solo valor y el riel se vuelve una marca corta bajo el 5. Con 4 repeticiones (como en tu captura) el centro lleva un trazo melocotón debajo y el rótulo dice «Plan 5 · −1».','El melocotón es el acento de la sesión: ya lo usa la app para «por debajo del plan» en los carruseles.']}});
scene({id:'lv-reps-amrap',tab:'vivo-tarjeta',title:'Repeticiones · AMRAP con mínimo',
  html:()=>lvScreen({card:lvCard({purpose:'AMRAP',qty:lvRepsB({min:8,max:null,amrap:true,plan:8,start:11}),load:{ref:'Anterior · 100 × 12 · RIR 0'}})}),after:lvWire,
  notes:{tags:[lvTagNew,lvTagKeep],p:['En una serie AMRAP, todo lo que está a partir del mínimo cuenta como «dentro»: el riel sale del mínimo y se pierde hacia la derecha. El rótulo conserva el «Mínimo 8» que ya muestra la tarjeta y le añade el ✓ o el «−N».']}});
scene({id:'lv-reps-fallo',tab:'vivo-tarjeta',title:'Repeticiones · serie marcada como fallida',
  html:()=>lvScreen({card:lvCard({qty:lvRepsB({min:4,max:6,plan:5,start:0,fail:true})})}),after:lvWire,
  notes:{tags:[lvTagKeep],p:['Cuando la serie se marca como fallida el carrusel se bloquea en 0 (igual que hoy: `enabled = !isFailedSet`), el bloque central pasa a `danger` tenue y el rótulo dice «Serie fallida». El riel del plan desaparece: ya no se compara nada.']}});
/* ---------- 2 · esfuerzo ---------- */
scene({id:'lv-esf-vivo',tab:'vivo-tarjeta',title:'Esfuerzo · el riel se calienta (interactivo)',
  html:()=>lvStd({card:{eff:lvEffB({start:null})}}),after:lvWire,
  notes:{tags:[lvTagNew,['keep','«Sin marcar» y marca del plan']],img:'h15-reps-selector',imgCap:'Hoy: el pulgar solo cambia a melocotón al pasar el 80 %.',
    p:['Arrastra el pulgar de izquierda a derecha. Hasta RPE 7 el riel es el crema de siempre. En RPE 8 (el plan) aparece un resplandor suave y dos brasas; en 9 sube la intensidad y el borde del relleno vibra; en 9,5–10 una lengua de luz corta sobre el pulgar; al llegar a «Fallo» hay un destello único (anillo + chispas) y después el halo respira.',
       'Todo ocurre dentro de la banda táctil de 44 dp del slider: nada se sale ni toca otros elementos. No hay confeti ni llamas dibujadas, solo luz y brasas pequeñas.'],
    listTitle:'Cómo se construye',list:['Mismos niveles que ya calcula el motor de la app (`effortFireLevel`: 0 en RPE ≤ 6 → 4 en RPE 10 → 5 en Fallo; `effortParticleCount` 0/0/2/6/11/16; periodo de brasa 2,4 → 0,8 s).','El tono pasa de crema a melocotón y a ámbar claro: es el mismo acento de la sesión.','La palabra («exigente», «muy duro», «al límite», «todo») sale de `effortWord`; se muestra junto al valor.','Movimiento reducido: sin brasas, sin vibración y sin destello; quedan el degradado y el halo fijos (escena «Esfuerzo · movimiento reducido»).']}});
scene({id:'lv-esf-escala',tab:'vivo-tarjeta',title:'Esfuerzo · la escala de calor, de RPE 6 a Fallo',
  html:()=>`<div class="lv"><div class="lv-island"><div class="t"><b>Escala de calor</b><span>Los mismos niveles del motor, uno bajo otro</span></div></div>
   <div class="lv-lvls">${[[12,'RPE 6','sin calor'],[14,'RPE 7','sin calor'],[16,'RPE 8 · plan','resplandor suave'],[18,'RPE 9','brasas y borde vivo'],[20,'RPE 10','destello de luz'],[21,'Fallo','destello + halo que respira']].map(a=>`<div class="lv-lvl"><div class="cap"><b>${a[1]}</b><span>${a[2]}</span></div>${lvEffB({start:a[0]}).replace('<div class="lv-bh"><span class="lv-lab">ESFUERZO</span><span class="lv-evalue"></span></div>','<span class="lv-evalue" style="display:none"></span>').replace(/<div class="lv-leg">.*?<\/div>/,'<div class="lv-leg" style="visibility:hidden;height:2px"><span class="a">RPE 0</span><span class="p">x</span><span class="f">Fallo</span></div>')}</div>`).join('')}</div></div>`,
  after:lvWire,
  notes:{tags:[lvTagNew],p:['Para aprobar el efecto sin arrastrar: seis estados fijos del mismo slider, en vivo. Se ve la progresión: nada (6–7), un resplandor (8), brasas (9), luz sobre el pulgar (10) y el máximo en Fallo.']}});
scene({id:'lv-esf-fallo',tab:'vivo-tarjeta',title:'Esfuerzo · en Fallo',
  html:()=>lvStd({card:{eff:lvEffB({start:21})}}),after:lvWire,
  notes:{tags:[lvTagNew],p:['Estado de reposo en «Fallo»: el valor se vuelve ámbar, el relleno llega al extremo con el degradado más caliente y el halo del pulgar respira. El destello de llegada solo se dispara cuando eliges Fallo, no al abrir la tarjeta (usa «Esfuerzo · el riel se calienta» y toca «Fallo» para verlo).']}});
scene({id:'lv-esf-reducido',tab:'vivo-tarjeta',title:'Esfuerzo · movimiento reducido (RPE 9,5)',
  html:()=>lvStd({cls:'lv-static',card:{eff:lvEffB({start:19})}}),after:lvWire,
  notes:{tags:[lvTagNew],p:['Con «Reducir movimiento» del sistema, el calor se dice sin moverse: degradado del relleno, resplandor y color del valor y del pulgar, todos fijos y a la intensidad del nivel. Sin brasas, sin vibración y sin destello.']}});
/* ---------- 4 · ROM ---------- */
scene({id:'lv-rom-editor',tab:'vivo-tarjeta',title:'ROM · opción del ejercicio en el editor',
  html:()=>lvEditor(),after:lvWire,
  notes:{tags:[lvTagNew,['move','«Medir ROM» → interruptor «Registrar ROM»']],p:['La opción vive donde ya vive: «⋯ → Opciones del ejercicio» (ExerciseOptionsBar), pero como interruptor, no como acción que cierra el menú. Se llama igual que en la sesión en vivo («Registrar ROM»); hoy el menú dice «Medir ROM» y la hoja en vivo «Registrar ROM».',
     'Al activarla, la línea resumen del ejercicio gana «· ROM» (ya existía en `SessionEditorUiUtils`) y desde ese momento la tarjeta de TODAS sus series lleva el deslizador. Es el mismo dato (`Exercise.trackRom`) que se enciende desde la hoja en vivo.']}});
scene({id:'lv-rom-tarjeta',tab:'vivo-tarjeta',title:'ROM · deslizador en la tarjeta (peso muerto)',
  html:()=>lvScreen({card:lvCard({qty:lvRepsB({min:4,max:6,plan:5,start:4}),det:lvRomB({v:80,dot:true}),load:{ref:'Anterior · 120 × 5 · ROM 100 %'}})}),after:lvWire,
  notes:{tags:[lvTagNew,['keep','la tarjeta no cambia de tamaño']],img:'h17-rom',imgCap:'Hoy: el interruptor del detalle no enciende nada en la tarjeta.',
    p:['El deslizador ocupa el hueco de la fila «Parciales, ayuda y ROM›» (36 dp, +9 sobre los 27 de la fila; el resto sale del aire entre bloques). Izquierda: «ROM» y el valor. Centro: deslizador de 10 a 100 % de 5 en 5, con marcas en 50, 80 y 100 que imantan. Derecha: «Detalle ›», que abre la hoja de parciales y ayuda; el punto indica que hay parciales o ayuda anotados.',
       'Al arrastrar aparece el nombre de la zona sobre el pulgar: «Parcial» (< 65 %), «Casi completo» (65–94 %) y «Completo» (≥ 95 %). Por defecto 100 %: se ve y se registra tal cual, nunca oculto.'],
    listTitle:'Hoy hay un fallo',list:['La tarjeta que usas (`legacyModeLayout = true`) NO pinta `RomBlock`: el deslizador solo existía en un diseño que ya no se usa. Por eso activar el ROM no hacía nada visible.','En la hoja, «Registrar ROM» cambiaba solo el valor de esa serie y no el ejercicio, así que el editor y la tarjeta nunca coincidían.']}});
scene({id:'lv-rom-hoja',tab:'vivo-tarjeta',title:'ROM · hoja «Detalle del registro»',
  html:()=>lvScreen({card:lvCard({qty:lvRepsB({min:4,max:6,plan:5,start:4}),det:lvDetB()+lvRomB({v:100}).replace('class="lv-rom"','class="lv-rom lv-rom-only"')}),over:lvDetailSheet(false)}),after:lvWire,
  notes:{tags:[lvTagNew,['move','el interruptor pasa a ser del ejercicio'],['ask','alcance: solo hoy o también el programa']],p:['«Registrar ROM» enciende o apaga el ROM de TODO el ejercicio (no solo de la serie) y la tarjeta de detrás cambia al instante: la fila «Parciales, ayuda y ROM›» se convierte en el deslizador. Al activarlo aparece un deslizador con las tres zonas rotuladas y el campo numérico exacto (el de hoy, «ROM · %»).',
     'Alcance: lo que se enciende aquí vale para esta sesión; el aviso «Guardar también en el programa» deja la opción fijada en el editor (misma pregunta de alcance que ya usan las demás acciones de estructura de la sesión).']}});
scene({id:'lv-rom-hoja-on',tab:'vivo-tarjeta',title:'ROM · hoja con el ROM activo',
  html:()=>lvScreen({cls:'lv-rom-on',card:lvCard({qty:lvRepsB({min:4,max:6,plan:5,start:4}),det:lvDetB()+lvRomB({v:80,dot:true}).replace('class="lv-rom"','class="lv-rom lv-rom-only"')}),over:lvDetailSheet(true)}),after:lvWire,
  notes:{tags:[lvTagNew],p:['Estado con el ROM activado: el deslizador de la hoja (con «Parcial · Casi completo · Completo») y el campo exacto en %. El valor es el mismo que el del deslizador de la tarjeta.']}});
scene({id:'lv-rom-corporal',tab:'vivo-tarjeta',title:'ROM · peso corporal (dominadas)',
  html:()=>lvScreen({exName:'Dominadas',exSub:'Peso corporal',card:lvCard({qty:lvRepsB({min:6,max:8,plan:7,start:7}),det:lvRomB({v:70}),load:{label:'CARGA',mode:'Corporal',dash:true,ref:'Corporal · corporal 82 kg'}}),m:lvM({exName:'Dominadas',sets:lvSets('ppp'),next:'Remo'})}),after:lvWire,
  notes:{tags:[lvTagNew],p:['Mismo hueco y mismo deslizador. En peso corporal el ROM es especialmente útil (media dominada, dominada completa): la carga es «—» porque no hay carga externa, y el ROM es lo que distingue una serie de otra.']}});
scene({id:'lv-rom-tiempo',tab:'vivo-tarjeta',title:'ROM · ejercicio por tiempo (isométrico)',
  html:()=>lvScreen({exName:'Sentadilla isométrica',exSub:'Pared',card:lvCard({qty:lvTimeB({v:45}),det:lvRomB({v:90}),load:{label:'CARGA',mode:'Corporal',dash:true,ref:'Corporal · corporal 82 kg'}}),m:lvM({exName:'Sentadilla isométrica',next:'Plancha'})}),after:lvWire,
  notes:{tags:[lvTagNew],p:['En una serie por tiempo la tarjeta ya deja más aire (el valor mide 49 dp y no 71): el deslizador entra igual. El ROM de un isométrico es la profundidad a la que se sostiene la posición.']}});
scene({id:'lv-rom-descanso',tab:'vivo-tarjeta',title:'ROM · serie registrada (resumen y roadmap)',
  html:()=>lvScreen({done:1,time:'4:12',card:lvRestCard({rom:80}),roadHtml:lvRoad(lvM({sets:[{st:'d',rom:80},{st:'p'},{st:'p'}],act:'rest',rest:{after:0,t:'2:41',f:.27},mode:'rest',done:1}))}),after:lvWire,
  notes:{tags:[lvTagNew],p:['La serie registrada con ROM se ve en tres sitios: en el resumen del descanso («120 × 4 · RPE 8 · ROM 80 %», el mismo formato de `restRef02LastSetText`), en el roadmap (la píldora hecha lleva una barrita de ROM en el borde inferior: llena = 100 %) y en el historial.']}});
scene({id:'lv-rom-historial',tab:'vivo-tarjeta',title:'ROM · historial del ejercicio',
  html:()=>lvStd({over:lvHistorySheet()}),after:lvWire,
  notes:{tags:[lvTagNew],p:['«Historial del ejercicio» muestra el ROM en cada serie desplegada («120 × 4 · RIR 2 · ROM 80 %») y, en la línea de la sesión, solo cuando la mejor serie no fue completa. El mismo texto se usa en la hoja de historial por fecha del ejercicio.']}});
/* ============================ PIEZAS DE ESCENA ============================ */
function lvRestCard(o){
  const rom=o.rom!=null?' · ROM '+o.rom+' %':'';
  return `<div class="lv-card" style="background:#111"><div class="lv-rest"><div class="lv-kick" style="margin:13px 0 0"><span>DESCANSO</span><span class="tune">${ic('up',16)}</span></div>
  <div class="eclipse-l"><svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="46" fill="none" stroke="#ffffff10" stroke-width="1.4"/><circle cx="50" cy="50" r="46" fill="none" stroke="#f2eee6" stroke-width="2" stroke-linecap="round" stroke-dasharray="${(289*.27).toFixed(1)} 289"/></svg><div><b>2:41</b><small>Descansando</small></div></div>
  <div class="lv-rctl"><b>−15</b><b class="m">${ic('pause',16)}</b><b>+15</b></div>
  <div class="lv-last">${ic('history',18)}<div><small>Última serie</small><b>120 × 4 · RPE 8${rom}</b></div></div>
  <div class="lv-next"><small>SIGUIENTE</small><b>Peso Muerto Convencional · serie 2</b><span>120 kg × 5</span></div></div></div>`;
}
function lvEditor(){
  return `<div class="lv lv-ed-wrap"><div class="lv-edtop"><span class="lvib">${ic('back',18)}</span><span class="pill">${ic('back',14)}Jue${ic('chevd',14)}${ic('fwd',14)}</span><span class="lvib">${ic('more',18)}</span></div>
  <div class="lv-ed"><div class="lv-edmod">
    <div class="lv-edrow"><span class="gr">${ic('grip',16)}</span><span class="ph">${ic('img',22)}</span><div class="tx"><b>Peso Muerto Convencional · Barra</b><small>3 × 5 · RPE 8 · 3:00<span data-rom-chip></span></small></div><span class="lvst">${ic('star',20)}</span><span class="cv">${ic('chevu',20)}</span></div>
    <div style="padding:2px 0 8px"><div class="lv-chips" style="padding:6px 0 4px"><span class="lv-chip">Modo: Reps ${ic('chevd',12)}</span><span class="lv-chip">Descanso 3:00</span><span class="lv-chip" style="padding:0 11px">${ic('more',18)}</span></div></div>
    <div class="lv-edrow"><span class="gr">${ic('grip',16)}</span><span class="ph">${ic('img',22)}</span><div class="tx"><b>Sentadilla "Belt Squat"</b><small>3 × 5 · RPE 8 · 3:00</small></div><span class="lvst">${ic('star',20)}</span><span class="cv">${ic('chevd',20)}</span></div>
    <div class="lv-edrow"><span class="gr">${ic('grip',16)}</span><span class="ph">${ic('img',22)}</span><div class="tx"><b>Hip Thrust · Barra</b><small>3 × 5 · RPE 8 · 3:00</small></div><span class="lvst">${ic('star',20)}</span><span class="cv">${ic('chevd',20)}</span></div></div></div>
  <div class="pl-scrim"></div><div class="lv-sh" style="max-height:calc(100% - 150px)"><div class="hd"></div><div class="tt"><h2>Opciones del ejercicio</h2><span class="x">${ic('close',18)}</span></div>
  <div class="bd" style="padding-bottom:26px">
   <div class="lv-menu"><span class="tx">Lateralidad: Bilateral</span></div>
   <div class="lv-menu"><span class="tx">Bloque APRE</span></div>
   <div class="lv-menu" style="padding:8px 0"><span class="tx">Registrar ROM<small>Añade un deslizador de ROM a la tarjeta de cada serie de este ejercicio.</small></span><span class="lv-sw" data-link="rom" role="switch" aria-checked="false" aria-label="Registrar ROM" tabindex="0"></span></div>
   <div class="lv-menu"><span class="tx">Relacionar ejercicio</span></div>
   <div class="lv-menu"><span class="tx">Superserie</span></div>
   <div class="lv-menu"><span class="tx">Unidad personalizada</span></div>
   <div class="lv-menu"><span class="tx">Carga inteligente</span></div>
   <div class="lv-menu"><span class="tx">Meta</span></div></div></div></div>`;
}
function lvDetailSheet(on){
  return `<div class="pl-scrim"></div><div class="lv-sh"><div class="hd"></div><div class="tt"><h2>Detalle del registro</h2><span class="x">${ic('close',18)}</span></div>
  <div class="bd" style="overflow-y:auto;scrollbar-width:none"><p class="sub" style="margin-bottom:6px">Peso Muerto Convencional · Barra · serie 1</p><p class="sub" style="color:var(--lv-fnt);font-size:calc(12.5px*var(--fs))">Las repeticiones parciales y con ayuda quedan separadas de las completas.</p>
   <div class="lv-srow"><span>Repeticiones completas</span><span class="in">4</span></div>
   <div class="lv-srow"><span>Repeticiones parciales</span><span class="in ph">0</span></div>
   <div class="lv-srow"><span>Repeticiones con ayuda</span><span class="in ph">0</span></div>
   <div class="lv-note"><span>4 completas</span></div>
   <div class="lv-srow" style="padding:8px 0"><span>Registrar ROM<small>En todas las series de este ejercicio</small></span><span class="lv-sw${on?' on':''}" data-link="rom" role="switch" aria-checked="${on}" aria-label="Registrar ROM" tabindex="0"></span></div>
   <div class="lv-rowrap" data-rom style="height:${on?'auto':'0'};opacity:${on?1:0}"><div class="lv-srow col"><div class="row2"><span>ROM</span><span class="in" style="min-width:84px"><span data-rom-link>${on?80:100}</span>&nbsp;%</span></div>
     ${lvRomB({v:on?80:100}).replace('class="lv-rom"','class="lv-rom" style="display:block;height:auto;margin-top:2px"').replace(/<div class="lvl">.*?<\/div>/,'').replace(/<button class="more".*?<\/button>/,'')}
     <div class="lv-zch" role="group" aria-label="Zonas del ROM"><button data-rv="50">Parcial</button><button data-rv="80">Casi completo</button><button data-rv="100">Completo</button></div>
     <div class="row2" style="padding-top:10px;border-top:1px solid #ffffff0f;margin-top:6px"><span style="font:400 calc(14px*var(--fs)) Inter">Guardar también en el programa<small style="display:block;font:400 calc(12px*var(--fs))/1.35 Inter;color:var(--lv-fnt);margin-top:2px">Si no, vale solo para esta sesión</small></span><span class="lv-sw" role="switch" aria-checked="false" aria-label="Guardar también en el programa" tabindex="0"></span></div></div></div>
   <div class="lv-menu" style="gap:12px">${ic('edit',18)}<span class="tx">Nota de serie</span>${ic('fwd',16)}</div></div>
  <div class="ft"><div class="ok">Listo</div></div></div>`;
}
function lvHistorySheet(){
  return `<div class="pl-scrim"></div><div class="lv-sh"><div class="hd"></div><div class="tt"><h2>Historial del ejercicio</h2><span class="x">${ic('close',18)}</span></div>
  <div class="bd"><p class="sub">Peso Muerto Convencional · Sin etiqueta</p><div class="lv-hbars" aria-hidden="true"><i style="height:62%"></i><i style="height:70%"></i><i style="height:78%"></i><i style="height:86%"></i><i style="height:100%"></i></div>
   <div class="lv-hl"><div class="r1"><div class="l"><b>Hace 3 días</b><small>RIR 2</small></div><span class="v">120 × 5</span><span class="u">Usar</span></div>
     <div class="sl">120 × 5 · RIR 2</div><div class="sl"><em>120 × 4 · RIR 2 · ROM 80 %</em></div><div class="sl">120 × 5 · RIR 1</div></div>
   <div class="lv-hl"><div class="r1"><div class="l"><b>Semana pasada</b><small>RPE 8</small></div><span class="v">117,5 × 5</span><span class="u">Usar</span></div></div>
   <div class="lv-hl"><div class="r1"><div class="l"><b>Hace 2 semanas</b><small>RPE 8,5</small></div><span class="v">115 × 5</span><span class="u">Usar</span></div></div></div>
  <div class="ft"><div class="ok">Listo</div></div></div>`;
}

/* ================================================================================================================
   ROADMAP v2 · A «Calma» (112 dp, dos bandas) y B «Con ruta visible» (132 dp, + tira de ruta) · hoja «Ruta»
   Todos los tiempos y curvas están en lv.css y repetidos en la «Ficha de profundidad y movimiento» de cada escena.
   ================================================================================================================ */
const LV2_BASE=[{n:'Peso Muerto Convencional'},{n:'Sentadilla "Belt Squat"'},{n:'Hip Thrust · Barra'},{n:'Sentadilla en Máquina Pendulum'},{n:'Patada de Glúteo · Polea'},{n:'Plancha Copenhague Dinámica'},{n:'Caminadora',k:'c',min:15}].map(s=>({sets:s.k==='c'?0:3,...s}));
const LV2_BASE14=['Peso Muerto Convencional','Sentadilla "Belt Squat"','Hip Thrust · Barra','Sentadilla en Máquina Pendulum','Prensa de piernas','Curl femoral tumbado','Extensión de cuádriceps','Elevación de talones de pie','Abducción de cadera en máquina','Buenos días con barra','Zancadas caminando','Hiperextensión a 45°','Patada de Glúteo · Polea','Plancha Copenhague Dinámica'].map(n=>({n,sets:3}));
function lvR2M(o={}){
  const base=o.base||LV2_BASE;
  const spec=o.reg||[];
  const steps=base.map((s,i)=>({...s,reg:s.k==='c'?[]:Array.from({length:s.sets},(_,j)=>(spec[i]||'')[j]==='d')}));
  return {steps,ex:o.ex||0,cur:o.cur??0,rest:o.rest||null,prep:o.prep||null,pend:o.pend||[],label:o.label||null,v:o.v||'A'};
}
const lvR2Sum=m=>({total:m.steps.reduce((a,s)=>a+s.sets,0),done:m.steps.reduce((a,s)=>a+s.reg.filter(Boolean).length,0)});
const lvR2V=want=>want==='B'&&state.size[1]>=820?'B':'A';
const lvR2H=v=>v==='B'?132:112;
const lvR2Strength=m=>m.steps.map((s,i)=>s.k==='c'?-1:i).filter(i=>i>=0);
function lvR2Groups(m){
  const out=[];let i=0;
  while(i<m.steps.length){
    const s=m.steps[i];let j=i;
    if(s.g){while(j+1<m.steps.length&&m.steps[j+1].g===s.g)j++}
    out.push({i,j,mem:m.steps.slice(i,j+1)});i=j+1;
  }
  return out;
}
function lvR2Strip(m){
  return `<div class="lv2-strip" role="group" aria-label="Ruta de la sesión">${lvR2Groups(m).map(g=>{
    const w=g.mem.reduce((a,x)=>a+(x.k==='c'?2:x.sets),0),sets=g.mem.reduce((a,x)=>a+x.sets,0),d=g.mem.reduce((a,x)=>a+x.reg.filter(Boolean).length,0);
    const cur=m.ex>=g.i&&m.ex<=g.j,c=g.mem[0].k==='c';
    return `<button class="lv2-seg${cur?' cur':''}${c?' c':''}" style="--w:${w}" data-i="${g.i}" aria-label="${g.mem.map(x=>x.n).join(' + ')}${c?', cardio':', '+d+' de '+sets+' series'}${cur?', actual':''}"><i style="--p:${sets?(d/sets*100).toFixed(1):0}%"></i>${g.mem.length>1?g.mem.slice(1).map((_,k)=>`<s style="left:${((k+1)/g.mem.length*100).toFixed(1)}%"></s>`).join(''):''}</button>`;
  }).join('')}</div>`;
}
function lvR2B1(m,fx){
  const st=m.steps[m.ex],N=st.sets,{total,done}=lvR2Sum(m);
  const isReg=!m.prep&&!m.rest&&st.reg[m.cur];
  const lastSet=m.cur===N-1,order=lvR2Strength(m),lastEx=m.ex===order[order.length-1];
  let lead;
  if(m.rest)lead=`<b class="pe">${ic('timer',15)}<span data-rt>Descanso ${m.rest.t}</span></b>`;
  else if(m.prep)lead=`<b class="${m.pend.length?'shr':''}"><span class="lg">${m.prep.label} ${m.prep.i} de ${m.prep.n}</span><span class="sm">${m.prep.label.slice(0,5)}. ${m.prep.i}/${m.prep.n}</span></b>`;
  else lead=`<b>Serie ${m.cur+1} de ${N}</b>`;
  let tail;
  if(m.pend.length)tail=m.pend.map(p=>`<span class="lv2-pend">${p}</span>`).join('');
  else if(isReg)tail=`<span class="lv2-tag ok">${lvCheck(11,3)}registrada</span>`;
  else if(!m.rest&&!m.prep&&lastSet)tail=`<span class="lv2-tag">${lastEx?'serie final':'última serie'}</span>`;
  else tail=`<span class="nm">${st.n}</span>`;
  const p=total?done/total:0,C=56.55,t=(C*(1-p)).toFixed(2),f=(C*(1-(fx.pdone!=null?fx.pdone:p))).toFixed(2);
  const ring=`<svg width="22" height="22" viewBox="0 0 22 22" aria-hidden="true"><circle cx="11" cy="11" r="9" fill="none" stroke="#ffffff1a" stroke-width="2.5"/><circle class="lv2-arc${fx.rec?' an':''}" cx="11" cy="11" r="9" fill="none" stroke="#f2eee6" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="${C}" style="--f:${f};--t:${t};stroke-dashoffset:${t}" transform="rotate(-90 11 11)"/></svg>`;
  return `<button class="lv2-b1" aria-label="Abrir la ruta de la sesión. ${done} de ${total} series registradas"><i class="lv2-drain" style="width:${m.rest?((1-m.rest.f)*100).toFixed(1):0}%"></i><span class="lv2-t ${fx.slide||''}">${lead}<em class="sep">·</em>${tail}</span><span class="lv2-prog">${ring}<span class="lv2-num"><span class="${fx.rec?'roll':''}">${done}</span></span><i>/${total}</i></span><span class="lv2-ch">${ic('chevu',14)}</span></button>`;
}
function lvR2B2(m,fx){
  const st=m.steps[m.ex],order=lvR2Strength(m);
  const first=m.ex===order[0]&&m.cur===0&&!m.prep,last=m.ex===order[order.length-1]&&m.cur===st.sets-1;
  const isReg=!m.prep&&!m.rest&&st.reg[m.cur];
  const mode=m.rest?'rest':(isReg&&!fx.rec)?'update':'record';
  const label=m.label||(mode==='rest'?'Terminar descanso':mode==='update'?'Actualizar serie':'Registrar serie');
  const icon=mode==='rest'?ic('play',16):mode==='update'?ic('refresh',17):lvCheck(18,2.4);
  const lastOfEx=!m.prep&&m.cur===st.sets-1&&!last;
  return `<div class="lv2-b2"><button class="lv2-nav" aria-label="Paso anterior"${first?' disabled':''}><b>${ic('back',20)}</b></button><button class="lv2-rec${fx.go?' lv2go':''}" data-mode="${mode}"><i class="wipe"></i>${icon}<span>${label}</span></button><button class="lv2-nav" aria-label="${lastOfEx?'Siguiente ejercicio':'Paso siguiente'}"${last?' disabled':''}><b>${ic('fwd',20)}</b></button></div>`;
}
function lvR2Inner(m,fx={}){
  return lvR2B1(m,fx)+(m.v==='B'?lvR2Strip(m):'')+lvR2B2(m,fx);
}
const lvR2=m=>`<div class="lv2 ${m.v}" data-m='${lvJ(m)}'>${lvR2Inner(m)}</div>`;
/* ---- hoja «Ruta» ---- */
function lvR2Ruta(m,o={}){
  const {total,done}=lvR2Sum(m),p=total?done/total:0;
  let num=0,cardioShown=false;
  const rows=lvR2Groups(m).map(g=>{
    const first=g.mem[0],c=first.k==='c';num++;
    const nn=String(num).padStart(2,'0');
    const sets=g.mem.reduce((a,x)=>a+x.sets,0),d=g.mem.reduce((a,x)=>a+x.reg.filter(Boolean).length,0);
    const cur=m.ex>=g.i&&m.ex<=g.j,isDone=sets>0&&d>=sets,grp=g.mem.length>1;
    const title=grp?`Superserie de ${g.mem.length}`:first.n;
    const status=c?`Cardio · ${first.min} min`:grp?`${first.sets} rondas · ${d}/${sets} series`:`${d}/${sets} series${isDone?' · hecho':''}`;
    const row=`<div class="lv2-rr${cur?' cur':''}${isDone?' done':''}${c?' c':''}"><button class="go" data-go="${g.i}"><span class="n">${nn}</span><span class="tx">${title}<small>${status}</small></span></button>${cur?'<span class="aq">Aquí</span>':`<button class="ir" data-go="${g.i}" aria-label="Ir a ${grp?g.mem.map(x=>x.n).join(' y '):first.n}">Ir${ic('fwd',13)}</button>`}<span class="mo" aria-label="Opciones">${ic('more',16)}</span></div>`;
    const chips=cur&&!c?`<div class="lv2-chs">${first.reg.map((r,k)=>`<span data-set="${k}" class="${k===m.cur?'cur':''}" style="--i:${k}">${grp?'R':'S'}${k+1}${r?' '+lvCheck(12,3):''}</span>`).join('')}</div>${o.extras&&!grp?'<div class="lv2-xt">S1 · 120 kg · calibrada</div>':''}`:'';
    const mem=grp?`<div class="mem">${g.mem.map((x,k)=>`<div><em>A${k+1}</em>${x.n}</div>`).join('')}</div>`:'';
    const head=c&&!cardioShown?(cardioShown=true,'<div class="lv2-cd">CARDIO</div>'):'';
    return head+(grp?`<div class="lv2-grp${cur?' cur':''}">${row}${mem}${chips}</div>`:row+chips);
  }).join('');
  const ton=(done*600).toLocaleString('es-CL');
  return `<div class="lv2-ruta${o.open?' open':''}${o.nt?' nt':''}"><div class="lv2-scrim"></div><div class="lv2-sheet" role="dialog" aria-label="Recorrido de la sesión"><div class="hd"></div><div class="tt"><h2>Recorrido de la sesión</h2><span class="x" aria-label="Cerrar panel">${ic('close',18)}</span></div><div class="bd">
  <p class="lv2-sub">Seleccionar permite consultar o editar. No registra ni omite una serie.</p>
  <div class="lv2-pg"><div class="r1"><span><b>${done}</b> de ${total} series</span><span>${Math.round(p*100)} %</span></div><div class="bar"><i style="--p:${(p*100).toFixed(1)}%"></i></div></div>
  ${rows}
  <div class="lv2-mn" style="margin-top:10px">${ic('grip',18)}<span>Reordenar pendientes</span></div><div class="lv2-mn">${ic('plus',18)}<span>Añadir ejercicio</span></div>
  <div class="lv2-sec">RESUMEN</div>
  <div class="lv2-met"><div><small>Registros</small><b>${done} / ${total}</b></div><div><small>Tonelaje</small><b>${ton} <u>kg</u></b></div><div><small>RIR medio</small><b>${done?'2,1':'—'}</b></div><div><small>Tiempo</small><b>${done?32:0} <u>min</u></b></div></div>
  <div class="lv2-pace">Ritmo: a este paso terminarías hacia las 19:42, dentro del objetivo de 70 min.</div></div></div></div>`;
}
/* ---- pantalla completa con el roadmap v2 ---- */
function lvR2Screen(m,o={}){
  const v=lvR2V(m.v);m={...m,v};
  const d0=lvDims(lvR2H(v),state.size[1]<=760?14:22);
  const ex=m.steps[m.ex];
  const card=lvCard({n:m.cur+1,total:ex.sets,qty:o.qty||lvRepsB({min:4,max:6,plan:5,start:5}),...(o.card||{})});
  const {done}=lvR2Sum(m);
  const {total:tot}=lvR2Sum(m);
  return lvScreen({exName:ex.n,exSub:o.sub??'Barra',done,isl:`${m.steps.filter(x=>x.k!=='c').length} ejercicios · ${done} de ${tot} series registradas`,time:o.time,card,roadHtml:lvR2(m)+lvR2Ruta(m,{open:!!o.open,nt:!!o.open,extras:o.extras}),roadH:lvR2H(v),roadB:d0.roadB,cls:o.cls});
}
/* ---- cableado ---- */
function lvR2Wire(ph){
  const root=ph.querySelector('.lv2');if(!root)return;
  const lv=ph.querySelector('.lv'),ruta=ph.querySelector('.lv2-ruta');
  let m=JSON.parse(root.dataset.m),timer=null,toast=null;
  const say=t=>{if(toast)toast.remove();toast=document.createElement('div');toast.className='lv2-toast';toast.textContent=t;lv.appendChild(toast);setTimeout(()=>{if(toast){toast.remove();toast=null}},3600)};
  function patch(fx){
    const tmp=document.createElement('div');tmp.innerHTML=lvR2Inner(m,fx);
    root.querySelector('.lv2-b1').replaceWith(tmp.querySelector('.lv2-b1'));
    root.querySelector('.lv2-b2').replaceWith(tmp.querySelector('.lv2-b2'));
    const a=root.querySelector('.lv2-strip'),b=tmp.querySelector('.lv2-strip');
    if(a&&b)a.querySelectorAll('.lv2-seg').forEach((s,i)=>{const n=b.children[i];s.className=n.className;s.style.cssText=n.style.cssText;s.firstChild.style.cssText=n.firstChild.style.cssText});
    lvFit2(root);
    const {done,total}=lvR2Sum(m);const isl=lv.querySelector('[data-islet]');if(isl)isl.textContent=m.steps.filter(x=>x.k!=='c').length+' ejercicios · '+done+' de '+total+' series registradas';
    const h=lv.querySelector('[data-title]');if(h)h.textContent=m.steps[m.ex].n;
    const k=lv.querySelector('[data-kick]');if(k)k.innerHTML='SERIE '+(m.cur+1)+'<i> / '+m.steps[m.ex].sets+'</i>';
  }
  function openRuta(){ruta.innerHTML=lvR2Ruta(m,{}).replace(/^<div class="lv2-ruta">|<\/div>$/g,'');ruta.className='lv2-ruta';void ruta.offsetWidth;ruta.classList.add('open')}
  const closeRuta=()=>{ruta.classList.remove('nt');ruta.classList.remove('open')};
  function nextTarget(){
    const order=lvR2Strength(m);
    for(const i of order){const k=m.steps[i].reg.findIndex(r=>!r);if(k>=0)return {ex:i,cur:k}}
    return null;
  }
  function endRest(slide){
    clearInterval(timer);m.rest=null;
    const t=nextTarget();if(t){m.ex=t.ex;m.cur=t.cur}else say('Sesión completa: aquí se abre la hoja de sesión completada.');
    patch({slide:slide||'sr'});
  }
  function startRest(){
    m.rest={t:'3:00',f:0};patch({slide:'su'});
    const t0=performance.now();clearInterval(timer);
    timer=setInterval(()=>{
      if(!root.isConnected){clearInterval(timer);return}
      const k=Math.min(1,(performance.now()-t0)/10000),sec=Math.max(0,Math.round(180*(1-k)));
      m.rest.f=k;m.rest.t=Math.floor(sec/60)+':'+String(sec%60).padStart(2,'0');
      const dr=root.querySelector('.lv2-drain'),tt=root.querySelector('[data-rt]');
      if(dr)dr.style.width=((1-k)*100)+'%';if(tt)tt.textContent='Descanso '+m.rest.t;
      if(k>=1)clearInterval(timer);
    },200);
  }
  function primary(){
    if(m.rest){endRest();return}
    if(m.prep){const was=lvR2Sum(m).done;m.prep.i++;if(m.prep.i>m.prep.n){m.prep=null;m.pend=[];m.label=null}else m.label='Registrar aproximación A'+m.prep.i;patch({go:1,slide:'su'});return}
    const st=m.steps[m.ex];
    if(st.reg[m.cur]){say('Serie actualizada con los valores de la tarjeta.');return}
    const before=lvR2Sum(m);st.reg[m.cur]=true;
    patch({rec:1,go:1,pdone:before.total?before.done/before.total:0});
    const after=lvR2Sum(m);
    if(after.done>=after.total){say('Última serie de la sesión: aquí se abre la hoja de sesión completada.');return}
    setTimeout(()=>{if(root.isConnected)startRest()},550);
  }
  function step(dir){
    if(m.prep)return;
    clearInterval(timer);m.rest=null;
    const order=lvR2Strength(m);
    const pos=order.flatMap(i=>m.steps[i].reg.map((_,k)=>[i,k]));
    let idx=pos.findIndex(x=>x[0]===m.ex&&x[1]===m.cur)+dir;
    if(idx<0)return;
    if(idx>=pos.length){if(m.steps.some(s=>s.k==='c'))say('Después viene el cardio: tiene su propio espacio a pantalla completa, sin roadmap.');return}
    m.ex=pos[idx][0];m.cur=pos[idx][1];patch({slide:dir>0?'sr':'sl'});
  }
  function jump(i){
    const s=m.steps[i];
    if(s.k==='c'){say('El cardio es un paso propio: se abre su espacio a pantalla completa.');return}
    clearInterval(timer);m.rest=null;m.prep=null;m.pend=[];m.label=null;m.ex=i;const k=s.reg.findIndex(r=>!r);m.cur=k>=0?k:0;patch({slide:'su'});
  }
  root.addEventListener('click',e=>{
    const b=e.target.closest('button');if(!b)return;
    if(b.classList.contains('lv2-b1'))return openRuta();
    if(b.classList.contains('lv2-rec'))return primary();
    if(b.classList.contains('lv2-nav'))return step(b.querySelector('svg path').getAttribute('d').startsWith('M15')?-1:1);
    if(b.classList.contains('lv2-seg')&&e.detail===0)return jump(+b.dataset.i);
  });
  const strip=root.querySelector('.lv2-strip');
  if(strip){
    let down=false,prov=null;
    const segs=()=>[...strip.querySelectorAll('.lv2-seg')];
    const at=x=>{let best=null,bd=1e9;segs().forEach(sg=>{const r=sg.getBoundingClientRect(),c=(r.left+r.right)/2,d=x>=r.left&&x<=r.right?0:Math.min(Math.abs(x-r.left),Math.abs(x-r.right));if(d<bd||(d===bd&&Math.abs(x-c)<1e9)){bd=d;best=sg}});return best};
    const inside=(x,y)=>{const r=strip.getBoundingClientRect(),cy=(r.top+r.bottom)/2;return x>=r.left-16&&x<=r.right+16&&Math.abs(y-cy)<=22};
    const mark=sg=>{
      if(!sg||sg===prov)return;
      segs().forEach(o=>o.classList.remove('prov','tick'));
      prov=sg;sg.classList.add('prov');void sg.offsetWidth;sg.classList.add('tick');
      try{if(navigator.vibrate)navigator.vibrate(10)}catch(_){}
      const nm=root.querySelector('.lv2-t .nm');if(nm){const st=m.steps[+sg.dataset.i];nm.textContent=st.n}
    };
    strip.addEventListener('pointerdown',e=>{down=true;prov=null;strip.classList.add('scrub');try{strip.setPointerCapture(e.pointerId)}catch(_){}mark(at(e.clientX))});
    strip.addEventListener('pointermove',e=>{if(down)mark(at(e.clientX))});
    const end=e=>{
      if(!down)return;down=false;strip.classList.remove('scrub');
      const sg=prov;prov=null;
      segs().forEach(o=>o.classList.remove('prov','tick'));
      if(sg&&e.type==='pointerup'&&inside(e.clientX,e.clientY))jump(+sg.dataset.i);else patch({});
    };
    strip.addEventListener('pointerup',end);strip.addEventListener('pointercancel',end);
  }
  root.addEventListener('contextmenu',e=>{if(e.target.closest('.lv2-b1')){e.preventDefault();say('Pulsación larga: opciones del ejercicio.')}});
  if(ruta)ruta.addEventListener('click',e=>{
    if(e.target.closest('.x')||e.target.classList.contains('lv2-scrim'))return closeRuta();
    const go=e.target.closest('[data-go]');if(go){closeRuta();return jump(+go.dataset.go)}
    const set=e.target.closest('[data-set]');if(set){m.cur=+set.dataset.set;m.rest=null;clearInterval(timer);closeRuta();patch({slide:'su'});return}
    if(e.target.closest('.mo'))say('Opciones del ejercicio: reemplazar, omitir, superserie…');
  });
  lvFit2(root);
}
function lvFit2(root){root.querySelectorAll('.lv2-rec span').forEach(el=>{el.style.fontSize='';const base=parseFloat(getComputedStyle(el).fontSize),min=base*(10/15);let f=base;while(el.scrollWidth>el.clientWidth+.5&&f>min){f-=.5;el.style.fontSize=f+'px'}})}


/* ============ FICHAS DE PROFUNDIDAD Y MOVIMIENTO (cada valor está en lv.css; el JS solo cambia clases y textos) ============ */
const LV2_F=[
  'ISLA · 328 × 112 dp (B: 132 dp). Margen lateral 16 dp; inferior 22 dp (14 dp en pantallas bajas). Radio 28 dp. Relleno plano #1B1B1D. Borde 1 dp #FFFFFF10. Sin degradado, sin vidrio, sin resplandor.',
  'SOMBRAS DE LA ISLA · (1) desplazamiento 0 / 10 dp, desenfoque 28 dp, rgba(0,0,0,.70). (2) contacto: 0 / 2 dp, desenfoque 6 dp, rgba(0,0,0,.50). Ambas hacia abajo; no hay sombra interior.',
  'RELLENO INTERIOR · 12 dp por lado (11 dp + el borde). Banda 1 de 32 dp, hueco de 8 dp, banda 2 de 48 dp. Nada toca el borde.',
  'BANDA 1 · sin fondo en reposo, radio 16 dp, relleno interior horizontal 8 dp. Zona táctil de 44 dp (6 dp de más arriba y abajo). Pulsada: fondo rgba(242,238,230,.07), 90 ms, (.2,.8,.2,1), sin retardo; al soltar vuelve en 90 ms. Pulsación larga (400 ms): opciones del ejercicio.',
  'TEXTO BANDA 1 · «Serie 2 de 3»: Inter 600, 14 sp, #F2EEE6, cifras tabulares. Separador «·»: 13 sp #5F5C57. Nombre: Inter 400, 13 sp, #B9B5AE, una línea con puntos suspensivos (lo último que cede). Etiqueta («registrada», «última serie»): alto 22 dp, radio 11 dp, Inter 600 12 sp, fondo #FFFFFF14 (registrada: #43D18C1F con texto #43D18C).',
  'PROGRESO · anillo de 22 dp, trazo 2,5 dp, extremos redondos, pista #FFFFFF1A, arco #F2EEE6 que empieza arriba; «4» Inter 600 13 sp y «/18» Inter 400 13 sp #92908A (cifras tabulares); chevron de 14 dp #92908A.',
  'BOTÓN PRINCIPAL · alto 48 dp, radio 24 dp, #F2EEE6, texto #111111 Inter 600 15 sp (se reduce hasta 10 sp antes de cortarse), icono de 18 dp. Sombra: 0 / 4 dp, desenfoque 14 dp, rgba(0,0,0,.35). Pulsado: escala .97 y fondo #E4DFD6 en 90 ms, (.2,.8,.2,1); al soltar, 160 ms, misma curva. Desactivado: alfa .38.',
  'BOTONES ‹ › · zona de 44 × 44 dp con círculo visible de 40 dp #FFFFFF0D, icono de 20 dp #B9B5AE. Pulsado: fondo #FFFFFF1A en 90 ms, (.2,.8,.2,1). Desactivado: alfa .38.',
  'REGISTRAR SERIE · (1) barrido verde #87C9A5 alfa .9 sobre el botón: ancho 0 → 100 % en 550 ms, (.2,.8,.2,1), se desvanece en el último 30 % (385 → 550 ms); (2) el ✓ se dibuja (trazo 24 → 0) en 360 ms, retardo 60 ms, (.2,.8,.2,1); (3) la cifra de progreso entra con desplazamiento vertical 60 % → 0 y alfa 0 → 1 en 260 ms, (.2,.8,.2,1); (4) el anillo avanza del valor anterior al nuevo en 700 ms, (.2,.8,.2,1). Durante el barrido el texto de la banda 1 dice «registrada» (etiqueta verde). A los 550 ms la banda 1 pasa a «Descanso» con entrada vertical.',
  'DESLIZAR ‹ › · el texto nuevo de la banda 1 entra con desplazamiento horizontal (∓14 dp → 0, según la dirección) y alfa 0 → 1 en 220 ms, (.2,.8,.2,1). No hay animación de salida: el texto anterior se sustituye.',
  'CAMBIO DE EJERCICIO · el texto entra con desplazamiento vertical (8 dp → 0) y alfa 0 → 1 en 220 ms, (.2,.8,.2,1); la tarjeta de arriba se cambia con su animación de siempre.',
  'MOVIMIENTO REDUCIDO · sin barrido, sin desplazamientos y sin ✓ dibujado: cambios de valor y de color en 0 ms. El estado se dice igual por texto («registrada», «Descanso 2:41»).',
  'SOLO DEMOSTRACIÓN · en el prototipo el descanso corre a 18× (180 s en 10 s) para poder verlo.',
];
const LV2_FB=[
  'ISLA B · 328 × 132 dp. Relleno 12 dp, banda 1 de 30 dp, hueco de 8 dp, TIRA de 14 dp, hueco de 8 dp, banda 2 de 48 dp. Solo en pantallas de alto ≥ 820 dp; por debajo se usa A.',
  'TIRA · un segmento por ejercicio con ancho proporcional a sus series (el cardio pesa 2); separación 4 dp. Segmento normal: alto 8 dp, radio 4 dp, pista #FFFFFF14, relleno #43D18C. Segmento actual: alto 14 dp, radio 7 dp, pista #FFFFFF26, relleno #F2EEE6, resplandor 0 / 0 dp, desenfoque 10 dp, rgba(242,238,230,.22). Cardio: pista rgba(143,178,255,.22), relleno #8FB2FF. Superserie: un solo segmento con divisor de 1 dp #1B1B1D entre miembros.',
  'TIRA · movimiento · al cambiar de ejercicio, alto, pista y resplandor cambian en 280 ms, (.2,.8,.2,1); el relleno crece (ancho) en 420 ms, (.2,.8,.2,1) al registrar. Zona táctil de cada segmento: 44 dp de alto (15 dp por encima y por debajo, con prioridad sobre las bandas). Tocar = ir a ese ejercicio.',
  'TIRA · RECORRER (pulsar y deslizar) · al pulsar, la tira pasa a modo «recorrido»: el segmento bajo el dedo es el PROVISIONAL (alto 18 dp, radio 9 dp, pista #FFFFFF40, aro inset 1,5 dp #F2EEE6, resplandor 0 / 0 dp, desenfoque 12 dp, rgba(242,238,230,.30)); el actual pierde su resplandor y baja a pista #FFFFFF1A; cambios de alto/color en 120 ms, (.2,.8,.2,1). Cada vez que el dedo cambia de segmento: pulso visible de 80 ms, escala (1,12 en ancho, 1,30 en alto) → 1, (.2,.8,.2,1), y vibración corta de 10 ms (HapticFeedbackType.TextHandleMove / vibración de 10 ms). Mientras dura, la banda 1 dice el nombre del ejercicio provisional (sin animación).',
  'TIRA · SOLTAR · al levantar el dedo dentro de la tira (zona de 44 dp de alto, 16 dp de margen lateral) se salta a ese ejercicio con la entrada de 220 ms ya descrita; si se suelta fuera, o el gesto se cancela, vuelve al ejercicio actual sin saltar (el texto de la banda 1 se restituye en 0 ms). Un toque sin deslizar equivale a recorrer un solo segmento y soltar. Movimiento reducido: sin pulso ni escala; solo cambia el resaltado y se mantiene la vibración de 10 ms.',
];
const LV2_FR=[
  'HOJA · mismo lenguaje de las hojas de la sesión: alto 84 % de la pantalla, radio superior 28 dp, relleno plano #1B1B1D, sombra 0 / −14 dp, desenfoque 44 dp, rgba(0,0,0,.80), luz superior inset 0 / 1 dp #FFFFFF10. Asa 33 × 3 dp #FFFFFF30. Velo rgba(0,0,0,.66).',
  'ENTRADA · la hoja sube de translateY(100 %) a 0 en 420 ms con curva de muelle suave cubic-bezier(.22,1.12,.36,1) (rebasa ≈ 3 % y asienta), sin retardo; el velo pasa de alfa 0 a 1 en 240 ms, (.2,.8,.2,1). Los chips de series del ejercicio actual aparecen escalonados: alfa 0 → 1 y desplazamiento 6 dp → 0, 220 ms, (.2,.8,.2,1), retardo 160 ms + 40 ms por chip.',
  'SALIDA · la hoja baja en 260 ms con (.4,0,1,1); el velo, 200 ms con (.4,0,1,1). Cierra la ✕, tocar el velo o arrastrar hacia abajo. Al elegir un ejercicio la hoja se cierra y el roadmap cambia con su entrada de 220 ms.',
  'FILAS · alto mínimo 56 dp; filete superior 1 dp #FFFFFF0F; número Syne 700 16 sp (actual #F2EEE6, hecho #43D18C, pendiente #92908A); nombre Inter 400 13 sp; estado Inter 400 12 sp. Ejercicio actual: etiqueta «Aquí» (alto 26 dp, radio 13 dp, #F2EEE6, texto #111111 600 12 sp). Los demás: «Ir ›» de 44 dp táctiles. «⋯» de 40 × 44 dp. Cardio: número y rótulo #8FB2FF. Superserie: miembros con filete vertical de 2 dp #FFFFFF2A.',
];
const lvF=(v,extra=[],ruta=false)=>[...LV2_F,...(v==='B'?LV2_FB:[]),...(ruta?LV2_FR:[]),...extra];
const lvR2Notes=(v,p,extra=[],list=null,img=null)=>({tags:[lvTagNew,...(v==='B'?[['ask','solo ≥ 820 dp']]:[])],p,
  ...(list?{listTitle:'Medidas (dp)',list}:{}),listTitle2:'Ficha de profundidad y movimiento',list2:lvF(v,extra),...(img?{img,imgCap:'Hoy, en tu teléfono.'}:{})});
const LV2_MEDA=['Margen exterior 12 dp por lado (11 dp + borde de 1)','Banda 1: 32 dp · toda ella es el botón de la ruta (zona táctil 44 dp)','Hueco entre bandas: 8 dp','Banda 2: 48 dp (‹ 44 · principal 48 · › 44, huecos de 8)','Total: 12 + 32 + 8 + 48 + 12 = 112 dp'];
const LV2_MEDB=['Margen exterior 12 dp por lado','Banda 1: 30 dp · Tira: 14 dp · Banda 2: 48 dp','Huecos: 8 dp entre bandas y tira','Total: 12 + 30 + 8 + 14 + 8 + 48 + 12 = 132 dp'];
/* ============ ESCENAS ============ */
const lvB6=(i,c,regs,o={})=>lvR2M({ex:i,cur:c,reg:regs,...o});
const LV2_TIME={qty:lvTimeB({v:45}),card:{load:{label:'CARGA',mode:'Corporal',dash:true,ref:'Corporal · corporal 82 kg'}}};
function lvR2Def(v){
  const A=v==='A';const P=A?'a':'b';
  const mk=(suffix,title,m,o,p,extra,list)=>scene({id:`lv-road-${P}-${suffix}`,tab:'roadmap',title:`${A?'A «Calma»':'B «Con ruta visible»'} · ${title}`,
    html:()=>lvR2Screen({...m(),v},o||{}),after:ph=>{lvWire(ph);lvR2Wire(ph)},notes:lvR2Notes(v,p,extra||[],list,suffix==='reposo'?'h16-roadmap':null)});
  mk('reposo','reposo (pantalla completa, interactivo)',()=>lvB6(1,1,['ddd','d']),{sub:''},
    [A?'Dos bandas y nada más. Arriba, una línea que dice dónde estás —«Serie 2 de 3 · Sentadilla "Belt Squat"»— y el avance de la sesión («4/18» con su anillo); toda la banda es el botón de la ruta. Abajo, ‹ [✓ Registrar serie] ›: el botón es lo único que brilla.':'Igual que A, más una tira de ruta entre las dos bandas: un segmento por ejercicio, ancho según sus series, relleno según lo registrado; el actual, más alto y en crema. Tocar un segmento lleva a ese ejercicio. Pensada para el Flip (880 dp), donde sobra hueco entre la pila de la tarjeta y el roadmap.',
     'Pruébalo: «Registrar serie» (barrido verde, ✓, cifra que rueda y anillo que crece) abre el descanso; «Terminar descanso» pasa a la serie siguiente y, al acabar el ejercicio, al siguiente. ‹ › recorren las series y cruzan de ejercicio. Tocar la banda 1 abre «Ruta».'+(A?' En A no hay tira: el recorrido es con ‹ › y con la «Ruta» (decisión consciente: en pantallas bajas se pierde el arrastre por el riel de hoy).':' Pulsa y desliza por la tira para recorrer los ejercicios: el segmento bajo el dedo se agranda y se resalta; al soltar, saltas a él (si sueltas fuera de la tira, vuelves al actual). Con alto < 820 dp esta escena se ve como A.')],
    ['Estado de arranque: 4/18, serie 2 de 3 del ejercicio 2.'],A?LV2_MEDA:LV2_MEDB);
  mk('registrada','serie ya registrada',()=>lvB6(1,0,['ddd','d']),{sub:''},['Al volver a una serie registrada (‹ o desde la ruta) la banda 1 lo dice con una etiqueta verde y el botón pasa a «Actualizar serie» con el icono de refrescar.'],['Etiqueta «registrada»: 22 dp de alto, verde #43D18C sobre #43D18C1F.']);
  mk('ultima','última serie del ejercicio',()=>lvB6(1,2,['ddd','dd']),{sub:''},['En la tercera de tres, la etiqueta «última serie» sustituye al nombre; › lleva al ejercicio siguiente (su descripción hablada cambia a «Siguiente ejercicio»).'],['Etiqueta «última serie»: 22 dp, #FFFFFF14, texto #F2EEE6.']);
  mk('ultimo','último ejercicio de la sesión',()=>lvB6(5,2,['ddd','ddd','ddd','ddd','ddd','dd']),{sub:'',...LV2_TIME},['Sin ejercicio siguiente: › queda desactivado (alfa .38) y la etiqueta dice «serie final». Registrar la última serie abre la hoja de sesión completada (otro paquete).'],['› desactivado: alfa .38, sin respuesta táctil.']);
  mk('descanso','descanso activo',()=>lvB6(1,0,['ddd','d'],{rest:{t:'2:41',f:.27}}),{sub:''},['El descanso vive en la banda 1: «Descanso 2:41» en melocotón y un fondo melocotón que se vacía de derecha a izquierda. El botón principal pasa a «Terminar descanso ▶». El progreso de la sesión se conserva a la derecha.'],['Fondo de la banda en descanso: rgba(244,154,110,.16), ancho = tiempo restante, se actualiza cada 200 ms con transición de 250 ms lineal. Texto #F49A6E.']);
  mk('prep','preparación (aproximaciones)',()=>lvB6(1,0,['ddd'],{prep:{label:'Aproximación',i:1,n:2},pend:['Estado previo'],label:'Registrar aproximación A1'}),{sub:''},['En preparación la banda 1 dice «Aproximación 1 de 2» y, si hay acciones pendientes («Estado previo», «Terminar»), ocupan el sitio del nombre. El texto contextual del botón se reduce de 15 a 10 sp antes de cortarse.'],['Pastilla de acción pendiente: alto 22 dp, radio 11 dp, #FFFFFF14, Inter 600 12 sp.']);
  const m10=()=>lvR2M({base:[{...LV2_BASE[0],sets:10},...LV2_BASE.slice(1)],ex:0,cur:5,reg:['ddddd','','','','','','']});
  mk('10series','ejercicio con 10 series',m10,{sub:'Barra'},['«Serie 6 de 10» cabe en la misma banda: el roadmap ya no depende de cuántas series haya (antes, una ventana de 7 puntos con ···).'+(A?'':' En B el segmento del ejercicio mide 10/25 del ancho.')],['Sin ventana ni puntos: el texto es el único indicador de la serie.']);
  const m14=()=>lvR2M({base:LV2_BASE14,ex:8,cur:2,reg:['ddd','ddd','ddd','ddd','ddd','ddd','ddd','ddd','dd']});
  mk('14ej','sesión con 14 ejercicios',m14,{sub:''},['Con 14 ejercicios (42 series) el progreso dice «26/42»'+(A?'.':' y la tira reparte 14 segmentos de unos 18 dp de ancho con 4 dp de aire, aún tocables a 44 dp de alto.')],['Segmento mínimo: 3 series ≈ 18 dp de ancho.']);
}
lvR2Def('A');lvR2Def('B');
/* ---- Ruta ---- */
const lvRutaM=()=>{const m=lvR2M({ex:2,cur:1,reg:['ddd','ddd','d']});m.steps[3].g='A';m.steps[4].g='A';return m};
scene({id:'lv-road-ruta',tab:'roadmap',title:'Ruta · hoja inferior (común a A y B)',
  html:()=>lvR2Screen(lvRutaM(),{open:true,sub:"Barra",extras:true}),after:ph=>{lvWire(ph);lvR2Wire(ph)},
  notes:{tags:[lvTagNew,['keep','conserva todo lo del despliegue de hoy']],img:'h16-roadmap',imgCap:'Hoy el chevron de «SERIES · 0 DE 18» abre «Recorrido de la sesión».',
    p:['Se abre tocando la banda 1 (en A y en B) y es la hoja de hoy («Recorrido de la sesión») ordenada: avance de la sesión arriba; una fila por ejercicio con su estado —hecho en verde, «Aquí» en el actual, «Ir ›» en los demás—; el actual desplegado con sus series; la superserie agrupada (miembros A1, A2 y sus rondas); el cardio como paso propio bajo su rótulo.',
       'Conserva todo lo que ofrece hoy: ir a un ejercicio o a una serie concreta (no registra ni omite), «⋯» con las opciones del ejercicio o de la superserie, series con descripciones extra (calibrada, rest-pause), «Reordenar pendientes», «Añadir ejercicio» y el «Resumen» (registros, tonelaje, RIR medio, tiempo y ritmo). Ejemplo con superserie: ejercicios 4 y 5 agrupados.'],
    listTitle2:'Ficha de profundidad y movimiento',list2:lvF('A',['Prueba: toca «Ir ›» o un chip de serie y la hoja se cierra yendo ahí; toca el velo o la ✕ para cerrar.'],true)}});
scene({id:'lv-road-ruta-14',tab:'roadmap',title:'Ruta · sesión con 14 ejercicios (con scroll)',
  html:()=>lvR2Screen(lvR2M({base:LV2_BASE14,ex:8,cur:2,reg:['ddd','ddd','ddd','ddd','ddd','ddd','ddd','ddd','dd']}),{open:true,sub:''}),
  after:ph=>{lvWire(ph);lvR2Wire(ph);const bd=ph.querySelector('.lv2-sheet .bd');if(bd)bd.scrollTop=380},
  notes:{tags:[lvTagNew],p:['Con 14 ejercicios la lista desplaza dentro de la hoja; el avance, el título y la ✕ quedan fijos. Se abre con el ejercicio actual a la vista (aquí, desplazada 380 dp).'],
    listTitle2:'Ficha de profundidad y movimiento',list2:lvF('A',['Desplazamiento: inercia nativa; sin animación propia.'],true)}});
/* ---- Antes / ahora ---- */
const lvSpecBox=(rows,k)=>`<div class="lv2-spec">${rows.map(r=>r[0]==='p'?`<div class="padx" style="height:${r[1]*k}px">${r[2]||''}</div>`:r[0]==='g'?`<div class="gapx" style="height:${r[1]*k}px">${r[2]||''}</div>`:`<div class="bandx" style="height:${r[1]*k}px;margin:0 ${12*k*.55}px">${r[2]}</div>`).join('')}</div>`;
scene({id:'lv-road-medidas',tab:'roadmap',title:'Antes / ahora · medidas en dp de cada banda',
  doc:()=>`<div class="lv2-doc"><div><h3>Hoy</h3><p>Tres bandas apretadas en 112 dp.</p><img src="${IMG['h16-roadmap']}" alt="Roadmap de hoy" style="width:268px;height:190px;object-fit:cover;object-position:50% 100%"><div class="lv2-dim">Relleno 8 / 9 dp · cabecera <b>22</b> dp · riel <b>26</b> dp (píldoras de 24) · botones <b>44</b> dp · huecos de <b>0 a 4</b> dp · texto de 10 sp en la cabecera.</div></div>
  <div><h3>A «Calma» · 112 dp</h3><p>Dos bandas. Una acción.</p>${lvSpecBox([['p',12,'12 dp'],['b',32,'Banda 1 · 32 dp · «Serie 2 de 3 · …» + «4/18»'],['g',8,'8 dp'],['b',48,'Banda 2 · 48 dp · ‹ Registrar serie ›'],['p',12,'12 dp']],1.5)}<div class="lv2-dim">Margen ≥ <b>12</b> dp · hueco <b>8</b> dp · táctil <b>44</b> dp · texto <b>≥ 12</b> sp.<br>12 + 32 + 8 + 48 + 12 = <b>112</b> dp (igual que hoy).</div></div>
  <div><h3>B «Con ruta visible» · 132 dp</h3><p>Solo con alto ≥ 820 dp.</p>${lvSpecBox([['p',12,'12 dp'],['b',30,'Banda 1 · 30 dp'],['g',8,'8 dp'],['b',14,'Tira · 14 dp'],['g',8,'8 dp'],['b',48,'Banda 2 · 48 dp'],['p',12,'12 dp']],1.5)}<div class="lv2-dim">12 + 30 + 8 + 14 + 8 + 48 + 12 = <b>132</b> dp (+20 dp: tira y un hueco).<br>En el Flip (880 dp) el hueco entre la pila de la tarjeta y el roadmap pasa de 38 a 28 dp.</div></div></div>`,
  notes:{tags:[lvTagNew],p:['Las medidas de cada banda de A y de B frente al roadmap de hoy.'],listTitle:'Medidas (dp)',list:[...LV2_MEDA.map(x=>'A · '+x),...LV2_MEDB.map(x=>'B · '+x)],listTitle2:'Ficha de profundidad y movimiento',list2:lvF('B')}});
