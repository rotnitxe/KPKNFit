// Utilidades de CSS compartidas por tools/inventory.cjs y tools/tokens-group.cjs (sin dependencias).
//  - splitTop / tokens: partir texto CSS por comas o espacios fuera de paréntesis.
//  - resolveVars: sustituye var(--x, respaldo) con las variables que declara el CSS del lienzo (`vars` del inventario).
//  - expandProps: los atajos `animation`, `transition` y `background` con var() dejan vacíos sus valores sueltos en el CSSOM; boot.js guarda su texto
//    (`raw:animation`…) y aquí se convierten en los mismos valores sueltos que tendrían sin var(), para que el inventario no pierda esas filas.
//  - parseColor: rgb(), rgba(), #hex, transparent, var() sin resolver (color dinámico del módulo) y color-mix() con un color dinámico.
'use strict';
const splitTop=(s,sep=',')=>{
  const out=[];let depth=0,cur='';
  for(const ch of String(s)){
    if(ch==='(')depth++;else if(ch===')')depth--;
    if(ch===sep&&depth===0){out.push(cur.trim());cur=''}else cur+=ch;
  }
  if(cur.trim()!==''||out.length)out.push(cur.trim());
  return out;
};
const tokens=s=>{
  const out=[];let depth=0,cur='';
  for(const ch of String(s).trim()){
    if(ch==='(')depth++;else if(ch===')')depth--;
    if(/\s/.test(ch)&&depth===0){if(cur)out.push(cur);cur=''}else cur+=ch;
  }
  if(cur)out.push(cur);
  return out;
};
const VAR_RE=/var\(\s*(--[\w-]+)\s*(?:,((?:[^()]|\([^()]*\))*))?\)/;
function resolveVars(str,vars,keep){
  let s=String(str);
  for(let i=0;i<8;i++){
    const m=VAR_RE.exec(s);if(!m)break;
    const name=m[1],fb=m[2]!==undefined?m[2].trim():null;
    let rep=null;
    if(fb!==null&&fb!=='')rep=fb;            // con respaldo: ese es el valor por defecto de la escena (la variable se pone en línea por elemento)
    else if(!(keep&&keep.has(name))&&vars&&vars[name]!==undefined&&vars[name]!=='')rep=vars[name];
    if(rep===null){
      // Sin valor: se deja la referencia escrita como marcador para no volver a encontrarla.
      s=s.slice(0,m.index)+'\u0001'+name+'\u0002'+s.slice(m.index+m[0].length);
    }else s=s.slice(0,m.index)+rep+s.slice(m.index+m[0].length);
  }
  return s.replace(/\u0001(--[\w-]+)\u0002/g,'var($1)');
}
// 0.5 / .5 / 5e-1 → «0.5»; los tiempos y las curvas se escriben como los escribe el CSS calculado del navegador.
const fnum=n=>{const r=Math.round(n*10000)/10000;return String(r)};
const normTime=t=>{const m=/^(-?[\d.]+)(ms|s)$/.exec(t);return m?fnum(parseFloat(m[1]))+m[2]:t};
const normCurve=c=>{
  const m=/^cubic-bezier\(([^)]*)\)$/.exec(c);
  if(m)return 'cubic-bezier('+m[1].split(',').map(x=>fnum(parseFloat(x))).join(', ')+')';
  return c.replace(/\s+/g,' ');
};
const isTime=t=>/^-?[\d.]+(ms|s)$/.test(t);
const isCurve=t=>/^(ease|ease-in|ease-out|ease-in-out|linear|step-start|step-end)$/.test(t)||/^(cubic-bezier|steps|linear)\(/.test(t);
function parseAnimationShorthand(raw,vars){
  const out={name:[],dur:[],fn:[],delay:[],count:[],dir:[],fill:[],play:[]};
  for(const seg of splitTop(resolveVars(raw,vars))){
    let name='none',dur='0s',fn='ease',delay='0s',count='1',dir='normal',fill='none',play='running',nt=0;
    for(const t of tokens(seg)){
      if(isTime(t)){if(nt++===0)dur=normTime(t);else delay=normTime(t)}
      else if(isCurve(t))fn=normCurve(t);
      else if(t==='infinite'||/^\d*\.?\d+$/.test(t))count=t;
      else if(/^(normal|reverse|alternate|alternate-reverse)$/.test(t))dir=t;
      else if(/^(none|forwards|backwards|both)$/.test(t)&&name!=='none')fill=t;
      else if(/^(running|paused)$/.test(t))play=t;
      else if(name==='none')name=t;
      else if(/^(none|forwards|backwards|both)$/.test(t))fill=t;
    }
    out.name.push(name);out.dur.push(dur);out.fn.push(fn);out.delay.push(delay);out.count.push(count);out.dir.push(dir);out.fill.push(fill);out.play.push(play);
  }
  return out;
}
function parseTransitionShorthand(raw,vars){
  const out={prop:[],dur:[],fn:[],delay:[]};
  for(const seg of splitTop(resolveVars(raw,vars))){
    let prop='all',dur='0s',fn='ease',delay='0s',nt=0;
    for(const t of tokens(seg)){
      if(isTime(t)){if(nt++===0)dur=normTime(t);else delay=normTime(t)}
      else if(isCurve(t))fn=normCurve(t);
      else if(t!=='normal'&&t!=='allow-discrete')prop=t;
    }
    out.prop.push(prop);out.dur.push(dur);out.fn.push(fn);out.delay.push(delay);
  }
  return out;
}
// Devuelve una copia de props donde lo que estaba en un atajo con var() aparece como valores sueltos y donde los var() de las sombras, filtros y degradados están resueltos.
function expandProps(props,vars){
  const p={...props};
  const empty=k=>p[k]===undefined||p[k]==='';
  if(p['raw:animation']&&empty('animation-name')){
    const a=parseAnimationShorthand(p['raw:animation'],vars);
    p['animation-name']=a.name.join(', ');p['animation-duration']=a.dur.join(', ');p['animation-timing-function']=a.fn.join(', ');p['animation-delay']=a.delay.join(', ');
    p['animation-iteration-count']=a.count.join(', ');p['animation-direction']=a.dir.join(', ');p['animation-fill-mode']=a.fill.join(', ');p['animation-play-state']=a.play.join(', ');
  }
  if(p['raw:transition']&&empty('transition-property')){
    const t=parseTransitionShorthand(p['raw:transition'],vars);
    p['transition-property']=t.prop.join(', ');p['transition-duration']=t.dur.join(', ');p['transition-timing-function']=t.fn.join(', ');p['transition-delay']=t.delay.join(', ');
  }
  if(p['raw:background']&&empty('background-image'))p['background-image']=String(p['raw:background']).replace(/\s+/g,' ').trim();
  delete p['raw:animation'];delete p['raw:transition'];delete p['raw:background'];
  return p;
}
// ---------- colores ----------
// Variables de color que cada escena fija por elemento (el color del módulo, de la zona…): no se sustituyen por su valor por defecto, son «color dinámico».
const DYN_COLOR_VARS=new Set(['--zc','--c','--wc']);
const clamp=(x,a,b)=>Math.min(b,Math.max(a,x));
function parseColor(str,vars){
  let s=resolveVars(String(str).trim(),vars,DYN_COLOR_VARS).trim();
  let m;
  if(s==='transparent')return {r:0,g:0,b:0,a:0};
  if((m=/^rgba?\(([^)]*)\)$/.exec(s))){
    const parts=m[1].split(/[\s,/]+/).filter(Boolean).map(x=>x.endsWith('%')?parseFloat(x)/100:parseFloat(x));
    return {r:parts[0],g:parts[1],b:parts[2],a:parts.length>3?parts[3]:1};
  }
  if((m=/^#([0-9a-f]{3,8})$/i.exec(s))){
    let h=m[1];if(h.length<=4)h=h.split('').map(c=>c+c).join('');
    const n=i=>parseInt(h.slice(i,i+2),16);
    return {r:n(0),g:n(2),b:n(4),a:h.length===8?Math.round(n(6)/255*1000)/1000:1};
  }
  // color-mix(in srgb, <color> N%, transparent) con un color dinámico → mismo color con el alfa N%
  if((m=/^color-mix\(\s*in\s+srgb\s*,\s*(.+?)\s+(\d+(?:\.\d+)?)%\s*,\s*transparent\s*\)$/.exec(s))){
    const inner=parseColor(m[1],vars);if(inner){return {...inner,a:Math.round(inner.a*parseFloat(m[2]))/100}}
  }
  if((m=/^var\((--[\w-]+)\)$/.exec(s)))return {dyn:m[1],a:1};
  return null;
}
const pad=n=>String(Math.round(n));
function fmtColor(c){
  if(!c)return '?';
  if(c.dyn)return c.a<1?`color-mix(in srgb, var(${c.dyn}) ${Math.round(c.a*100)}%, transparent)`:`var(${c.dyn})`;
  const a=Math.round(c.a*1000)/1000;
  return a>=1?`rgb(${pad(c.r)}, ${pad(c.g)}, ${pad(c.b)})`:`rgba(${pad(c.r)}, ${pad(c.g)}, ${pad(c.b)}, ${a})`;
}
module.exports={DYN_COLOR_VARS,splitTop,tokens,resolveVars,expandProps,parseColor,fmtColor,fnum,normTime,normCurve,isTime,isCurve,clamp};
