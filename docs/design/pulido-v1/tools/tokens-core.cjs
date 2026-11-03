// (Núcleo de tools/tokens-group.cjs: recoge los valores y los agrupa; el nombrado, la salida y la geometría de las hojas están en tokens-group.cjs.)
// Agrupa los valores de profundidad y movimiento del lienzo en PRESETS (los cimientos de la app: ui/design/depth y ui/design/motion).
//   node tools/inventory.cjs --build=<carpeta>      (genera paridad/_inventory.json)
//   node tools/tokens-group.cjs                     (lee paridad/_inventory.json → paridad/cimientos.json, paridad/CIMIENTOS_GRUPOS.md y el bloque de datos de src/scenes-cm.js)
//   node tools/inventory.cjs --build=<carpeta>      (otra vez: marca cada fila con « → preset <nombre>»)
// Determinista y sin dependencias. Las reglas de agrupación están al principio de cada sección y se repiten en CIMIENTOS_GRUPOS.md.
// Regla de oro: cada preset tiene UN valor canónico exacto, el del miembro MÁS USADO, tal cual (nada de promediar); los demás miembros se parecen a él dentro de la tolerancia.
'use strict';
const fs=require('node:fs');
const path=require('node:path');
const {splitTop,tokens,expandProps,parseColor,fnum,normCurve,resolveVars,DYN_COLOR_VARS}=require('./cssvars.cjs');
const root=path.resolve(__dirname,'..');
const inv=JSON.parse(fs.readFileSync(path.join(root,'paridad','_inventory.json'),'utf8'));
const vars=inv.vars||{};
const norm=v=>String(v).replace(/\s+/g,' ').trim();
const SKIP=new Set(['cm']);
const round=(n,d=3)=>{const k=Math.pow(10,d);return Math.round(n*k)/k};

// ---------------------------------------------------------------- 1. Recolección (las mismas filas que cuenta tools/inventory.cjs)
// Una «fila» es (paquete, selector, valor): igual que el inventario de paridad. Cada valor distinto recuerda cuántas filas lo usan, quién y en qué orden apareció.
class Tally{
  constructor(){this.map=new Map();this.rowSeen=new Set()}
  add(key,pkg,selector,rowKey){
    const k=pkg+'|'+rowKey+'|'+key;if(this.rowSeen.has(k))return;this.rowSeen.add(k);
    let e=this.map.get(key);if(!e){e={value:key,uses:0,order:this.map.size,pkgs:new Set(),selectors:[]};this.map.set(key,e)}
    e.uses++;e.pkgs.add(pkg);if(e.selectors.length<40)e.selectors.push(pkg+':'+selector);
  }
  list(){return [...this.map.values()].sort((a,b)=>b.uses-a.uses||a.order-b.order)}
}
const T={pair:new Tally(),shadow:new Tally(),text:new Tally(),backdrop:new Tally(),filter:new Tally(),grad:new Tally(),curve:new Tally(),dur:new Tally(),delay:new Tally()};
const animItems=[];                       // una por animación de cada fila: {name,dur,fn,delay,count,dir,fill,pkg,selector}
const animSeen=new Set();
for(const r of inv.rows){
  if(SKIP.has(r.pkg))continue;
  const p=expandProps(r.props,vars);
  const sel=r.selector,pkg=r.pkg;
  if(p['box-shadow']&&p['box-shadow']!=='none')T.shadow.add(norm(p['box-shadow']),pkg,sel,'box-shadow');
  if(p['text-shadow']&&p['text-shadow']!=='none')T.text.add(norm(p['text-shadow']),pkg,sel,'text-shadow');
  const bf=p['backdrop-filter']||p['-webkit-backdrop-filter'];
  if(bf&&bf!=='none')T.backdrop.add(norm(bf),pkg,sel,'backdrop-filter');
  if(p['filter']&&p['filter']!=='none')T.filter.add(norm(p['filter']),pkg,sel,'filter');
  if(p['background-image']&&/gradient\(/.test(p['background-image']))T.grad.add(norm(p['background-image']),pkg,sel,'gradient');
  if(p['animation-name']&&p['animation-name']!=='none'){
    const names=splitTop(p['animation-name']);
    const pick=(k,d)=>{const l=splitTop(p[k]||d);return i=>l[i%l.length]};
    const du=pick('animation-duration','0s'),fn=pick('animation-timing-function','ease'),dl=pick('animation-delay','0s'),ct=pick('animation-iteration-count','1'),di=pick('animation-direction','normal'),fm=pick('animation-fill-mode','none');
    const desc=`animation: ${norm(p['animation-name'])} · ${norm(p['animation-duration']||'')} · ${norm(p['animation-timing-function']||'')} · retardo ${norm(p['animation-delay']||'0s')} · repite ${norm(p['animation-iteration-count']||'1')} · ${norm(p['animation-direction']||'normal')} · ${norm(p['animation-fill-mode']||'none')}`;
    names.forEach((n,i)=>{
      if(n==='none')return;
      const it={name:n,dur:du(i),fn:normCurve(fn(i)),delay:dl(i),count:ct(i),dir:di(i),fill:fm(i),pkg,selector:sel};
      const k=pkg+'|'+sel+'|'+desc+'|'+n;if(animSeen.has(k))return;animSeen.add(k);animItems.push(it);
      T.curve.add(it.fn,pkg,sel,'A|'+desc);T.dur.add(it.dur,pkg,sel,'A|'+desc);T.pair.add(it.fn+' | '+it.dur,pkg,sel,'A|'+desc);
      if(!/^0m?s$/.test(it.delay))T.delay.add(it.delay,pkg,sel,'A|'+desc);
    });
  }
  if(p['transition-property']&&p['transition-property']!=='none'&&!/^0s(, 0s)*$/.test(norm(p['transition-duration']||'0s'))){
    const props=splitTop(p['transition-property']);
    const pick=(k,d)=>{const l=splitTop(p[k]||d);return i=>l[i%l.length]};
    const du=pick('transition-duration','0s'),fn=pick('transition-timing-function','ease'),dl=pick('transition-delay','0s');
    const desc=`transition: ${norm(p['transition-property'])} · ${norm(p['transition-duration']||'')} · ${norm(p['transition-timing-function']||'')} · retardo ${norm(p['transition-delay']||'0s')}`;
    props.forEach((pp,i)=>{
      if(/^0m?s$/.test(du(i)))return;
      T.curve.add(normCurve(fn(i)),pkg,sel,'T|'+desc);T.dur.add(du(i),pkg,sel,'T|'+desc);T.pair.add(normCurve(fn(i))+' | '+du(i),pkg,sel,'T|'+desc);
      if(!/^0m?s$/.test(dl(i)))T.delay.add(dl(i),pkg,sel,'T|'+desc);
    });
  }
}

// ---------------------------------------------------------------- 2. Agrupación genérica
// Se ordena de más a menos usado (empate: el que apareció antes). El primero sin grupo es el canónico del nuevo grupo y absorbe a todos los que
// están dentro de la tolerancia de ÉL (no de otros miembros: no hay cadenas). `delta(canon, miembro)` devuelve null si no encajan o el vector de distancias.
function group(entries,parse,delta){
  const items=entries.map(e=>({...e,parsed:parse(e.value)}));
  const groups=[];
  for(const it of items){
    if(it.assigned)continue;
    const g={canon:it,members:[{...it,dist:zero()}]};
    it.assigned=true;
    if(it.parsed){
      for(const o of items){
        if(o.assigned||!o.parsed)continue;
        const d=delta(it.parsed,o.parsed);
        if(d){o.assigned=true;g.members.push({...o,dist:d})}
      }
    }
    groups.push(g);
  }
  return groups;
}
const zero=()=>({});
const maxDist=members=>{const m={};for(const x of members)for(const [k,v] of Object.entries(x.dist||{}))m[k]=Math.max(m[k]||0,v);return m};
const fmtDist=d=>{
  const names={x:'x',y:'y',blur:'desenfoque',spread:'extensión',alpha:'alfa',chan:'canal',sat:'saturación',bri:'brillo',ang:'ángulo',pos:'posición',pct:'%'};
  const parts=Object.entries(d).filter(([,v])=>v>0).map(([k,v])=>`${names[k]||k} ±${fnum(round(v,3))}${['x','y','blur','spread'].includes(k)?' dp':''}`);
  return parts.length?parts.join(', '):'idéntico';
};

// ---------------------------------------------------------------- 3. Sombras
// Capas: [inset] x y desenfoque extensión color. Dos valores son equivalentes si tienen las mismas capas (mismo orden e igual inset) con x e y ±2 dp,
// desenfoque ±4 dp, extensión ±1 dp, alfa ±0,08 y canales de color ±12 (el color dinámico del módulo, var(--c)…, solo equivale a otro dinámico).
function parseShadow(v){
  const layers=[];
  for(const L of splitTop(v)){
    let inset=false;const nums=[];const col=[];
    for(const t of tokens(L)){
      if(t==='inset')inset=true;
      else if(/^-?[\d.]+(px)?$/.test(t))nums.push(parseFloat(t));
      else col.push(t);
    }
    const c=parseColor(col.join(' '),vars);
    if(!c||nums.length<2)return null;
    layers.push({inset,x:nums[0],y:nums[1],blur:nums[2]||0,spread:nums[3]||0,c});
  }
  return layers.length?layers:null;
}
function shadowDelta(a,b){
  if(a.length!==b.length)return null;
  const d={x:0,y:0,blur:0,spread:0,alpha:0,chan:0};
  for(let i=0;i<a.length;i++){
    const A=a[i],B=b[i];
    if(A.inset!==B.inset||!!A.c.dyn!==!!B.c.dyn)return null;
    d.x=Math.max(d.x,Math.abs(A.x-B.x));d.y=Math.max(d.y,Math.abs(A.y-B.y));
    d.blur=Math.max(d.blur,Math.abs(A.blur-B.blur));d.spread=Math.max(d.spread,Math.abs(A.spread-B.spread));
    d.alpha=Math.max(d.alpha,Math.abs(A.c.a-B.c.a));
    if(!A.c.dyn&&!(A.c.a<0.02&&B.c.a<0.02))d.chan=Math.max(d.chan,Math.abs(A.c.r-B.c.r),Math.abs(A.c.g-B.c.g),Math.abs(A.c.b-B.c.b));
  }
  if(d.x>2||d.y>2||d.blur>4||d.spread>1||d.alpha>0.08+1e-9||d.chan>12)return null;
  return d;
}
const shadowGroups=group(T.shadow.list(),parseShadow,shadowDelta);
const textGroups=group(T.text.list(),parseShadow,shadowDelta);

// ---------------------------------------------------------------- 4. Vidrio y filtros
// Funciones de filtro: blur(px), saturate(n), brightness(n). Equivalentes si son del mismo tipo (backdrop-filter o filter), con desenfoque ±4 dp y
// saturación y brillo ±0,15 (lo que falta vale 1).
function parseFilter(v){
  const f={blur:0,sat:1,bri:1};let any=false;
  for(const t of tokens(v)){
    const m=/^(blur|saturate|brightness)\(([-\d.]+)(px|%)?\)$/.exec(t);
    if(!m)return null;any=true;
    const n=parseFloat(m[2])/(m[3]==='%'?100:1);
    if(m[1]==='blur')f.blur=n;else if(m[1]==='saturate')f.sat=n;else f.bri=n;
  }
  return any?f:null;
}
function filterDelta(a,b){
  const d={blur:Math.abs(a.blur-b.blur),sat:Math.abs(a.sat-b.sat),bri:Math.abs(a.bri-b.bri)};
  return d.blur<=4&&d.sat<=0.15+1e-9&&d.bri<=0.15+1e-9?d:null;
}
const backdropGroups=group(T.backdrop.list(),parseFilter,filterDelta);
const filterGroups=group(T.filter.list(),parseFilter,filterDelta);

// ---------------------------------------------------------------- 5. Curvas
// Todas se llevan a cubic-bezier (ease = .25,.1,.25,1; ease-in = .42,0,1,1; ease-out = 0,0,.58,1; ease-in-out = .42,0,.58,1; linear = 0,0,1,1):
// equivalentes si cada coordenada difiere ≤ 0,05. steps() no se agrupa con nada.
const KW={ease:[.25,.1,.25,1],'ease-in':[.42,0,1,1],'ease-out':[0,0,.58,1],'ease-in-out':[.42,0,.58,1],linear:[0,0,1,1]};
function parseCurve(v){
  if(KW[v])return {b:KW[v],kw:v};
  const m=/^cubic-bezier\(([^)]*)\)$/.exec(v);
  if(m){const b=m[1].split(',').map(parseFloat);if(b.length===4&&b.every(x=>!isNaN(x)))return {b}}
  return null;
}
function curveDelta(a,b){
  let m=0;for(let i=0;i<4;i++)m=Math.max(m,Math.abs(a.b[i]-b.b[i]));
  return m<=0.05+1e-9?{pos:m}:null;
}
const curveGroups=group(T.curve.list(),parseCurve,curveDelta);

// ---------------------------------------------------------------- 6. Duraciones y retardos
// Escala (ms): 90, 120, 160, 200, 240, 280, 320, 400, 520, 700, 1000, 1600, 2400. Un valor se ajusta al escalón más cercano si la diferencia es ≤ 12 % del valor;
// si no, queda como token exacto. Al escalón se llega con el mismo criterio de «canónico = el más usado»: el valor canónico del token es el del escalón,
// salvo que el escalón no exista entre los miembros, en cuyo caso el escalón es el valor exacto de la escala (los números redondos).
const SCALE=[90,120,160,200,240,280,320,400,520,700,1000,1600,2400];
const toMs=t=>{const m=/^(-?[\d.]+)(ms|s)$/.exec(t);return m?Math.round(parseFloat(m[1])*(m[2]==='s'?1000:1)*100)/100:null};
function snap(ms){
  let best=null;
  for(const s of SCALE){const rel=Math.abs(ms-s)/ms;if(rel<=0.12+1e-9&&(!best||rel<best.rel-1e-9))best={s,rel}}
  return best;
}
function groupDurations(list){
  const byToken=new Map();                 // token → grupo
  for(const e of list){
    const ms=toMs(e.value);if(ms===null)continue;
    const sn=snap(ms);
    const key=sn?String(sn.s):'exacto '+fnum(ms);
    let g=byToken.get(key);if(!g){g={ms:sn?sn.s:ms,exact:!sn,members:[],uses:0,order:e.order};byToken.set(key,g)}
    g.members.push({...e,ms,dist:{pct:sn?sn.rel*100:0}});g.uses+=e.uses;g.order=Math.min(g.order,e.order);
  }
  return [...byToken.values()].sort((a,b)=>a.ms-b.ms);
}
const durGroups=groupDurations(T.dur.list());
const delayGroups=groupDurations(T.delay.list());

// ---------------------------------------------------------------- 7. Degradados
function parseGradient(v0){
  const v=resolveVars(v0,vars,DYN_COLOR_VARS);
  const layers=splitTop(v);
  if(layers.length!==1)return {multi:true,raw:v};
  const m=/^(repeating-)?(linear|radial|conic)-gradient\((.*)\)$/.exec(v);if(!m)return null;
  const parts=splitTop(m[3]);
  let head='';
  const first=parts[0];
  if(/^(to |\d+(\.\d+)?deg|circle|ellipse|closest|farthest|\d+%.*at |.*\bat\b)/.test(first)&&!/^(rgb|#|transparent|var)/.test(first)){head=first;parts.shift()}
  const stops=[];
  for(const s of parts){
    const tk=tokens(s);let pos=null;const ct=[];
    for(const t of tk){if(/^-?[\d.]+(%|px)$/.test(t)&&!/^rgb/.test(t)&&ct.length)pos={v:parseFloat(t),u:t.endsWith('%')?'%':'px'};else ct.push(t)}
    const c=parseColor(ct.join(' '),vars);if(!c)return null;
    stops.push({c,pos});
  }
  const ang=/^(-?[\d.]+)deg$/.exec(head);
  return {kind:(m[1]||'')+m[2],head,ang:ang?parseFloat(ang[1]):null,stops};
}
function gradDelta(a,b){
  if(a.multi||b.multi)return a.raw===b.raw?{}:null;
  if(a.kind!==b.kind||a.stops.length!==b.stops.length)return null;
  const d={ang:0,pos:0,alpha:0,chan:0};
  if(a.ang!==null||b.ang!==null){if(a.ang===null||b.ang===null)return null;d.ang=Math.abs(a.ang-b.ang)}
  else if(a.head!==b.head)return null;
  for(let i=0;i<a.stops.length;i++){
    const A=a.stops[i],B=b.stops[i];
    if(!!A.pos!==!!B.pos)return null;
    if(A.pos){if(A.pos.u!==B.pos.u)return null;d.pos=Math.max(d.pos,Math.abs(A.pos.v-B.pos.v))}
    if(!!A.c.dyn!==!!B.c.dyn)return null;
    d.alpha=Math.max(d.alpha,Math.abs(A.c.a-B.c.a));
    if(!A.c.dyn&&!(A.c.a<0.02&&B.c.a<0.02))d.chan=Math.max(d.chan,Math.abs(A.c.r-B.c.r),Math.abs(A.c.g-B.c.g),Math.abs(A.c.b-B.c.b));
  }
  if(d.ang>5||d.pos>8||d.alpha>0.08+1e-9||d.chan>12)return null;
  return d;
}
const gradGroups=group(T.grad.list(),parseGradient,gradDelta);

module.exports={vars,norm,round,T,animItems,shadowGroups,textGroups,backdropGroups,filterGroups,curveGroups,durGroups,delayGroups,gradGroups,parseShadow,shadowDelta,parseCurve,parseFilter,parseGradient,fmtDist,maxDist,SCALE,toMs,pairs:T.pair};
