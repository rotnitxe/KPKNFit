// Laboratorio: pestañas → estados → teléfono + notas. Las escenas se registran en scenes.js con scene({...}).
const TABS=[
  {id:'decisiones',label:'Decisiones',title:'Cómo lo vamos a gestionar',intro:'Decisiones de producto para aprobar antes del detalle: qué tipo de registro de competencia, cómo se crea un programa a medida, qué cambia en el registro de comida y qué se comparte en historias. Cada fila dice qué pasa con lo que existe hoy.'},
  {id:'competencias',label:'Competencias',title:'Competencias de todas tus disciplinas',intro:'Una sola ficha que vive tres momentos: próxima, hoy y resultado. Cubre todas las disciplinas que KPKN entrena con seis formas de resultado: intentos, pruebas con marca, contra el reloj, tarima, combates y partidos.'},
  {id:'crear',label:'Crear programa',title:'Crear un programa, todas sus formas',intro:'Cuatro caminos desde «Nuevo». «A tu medida» reutiliza el asistente de bienvenida tal cual, en una instancia «Nuevo programa»: lo que ya sabemos plegado arriba, las mismas preguntas y un cierre para elegir cuándo empieza.'},
  {id:'comida',label:'Registro · Describir',title:'Registrar comida describiéndola',intro:'Escribes como hablas; al analizar, las palabras se convierten en alimentos con una animación de platos de líneas. Las dudas se resuelven en la propia tarjeta. Al final, el registrador rápido de Inicio, sin salir de la pantalla.'},
  {id:'busqueda',label:'Registro · Buscar',title:'Buscar, escanear y los menús del registro',intro:'Antes de escribir ya ves lo reciente y tus favoritos. Cada resultado se añade con un toque o se abre en su ficha. Todas las hojas del registro con la misma piel.'},
  {id:'historias',label:'Historias',title:'Historias: competencia, cuerpo y nutrición',intro:'El mismo lenguaje que las historias aprobadas del programa: arte de líneas arriba, cifras grandes abajo, un solo acento. La de competencia se exporta también como vídeo corto con la celebración.'},
];
const SCENES=[];
function scene(s){SCENES.push(s)}
const $=s=>document.querySelector(s);
const state={tab:'decisiones',scene:null,size:[393,852],fs:1};
try{const h=location.hash.slice(1);if(TABS.some(t=>t.id===h))state.tab=h}catch(e){}

/* Iconos KPKN Line: retícula 24, trazo 1,75, extremos redondeados. */
const P={
 back:'<path d="M15 5l-7 7 7 7"/>',fwd:'<path d="M9 5l7 7-7 7"/>',down:'<path d="M5 9l7 7 7-7"/>',up:'<path d="M5 15l7-7 7 7"/>',
 more:'<circle cx="5" cy="12" r="1.3" fill="currentColor"/><circle cx="12" cy="12" r="1.3" fill="currentColor"/><circle cx="19" cy="12" r="1.3" fill="currentColor"/>',
 play:'<path d="M8 5.5v13l10.5-6.5z" fill="currentColor" stroke="none"/>',check:'<path d="M5 12.5l4.5 4.5L19 7.5"/>',
 edit:'<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',pause:'<path d="M9 5v14M15 5v14"/>',plus:'<path d="M12 5v14M5 12h14"/>',
 swap:'<path d="M7 7h11l-3-3M17 17H6l3 3"/>',cal:'<rect x="4" y="5.5" width="16" height="14.5" rx="3"/><path d="M8 3.5v4M16 3.5v4M4 10h16"/>',
 flag:'<path d="M6 21V4M6 4.5h11l-2.5 4 2.5 4H6"/>',info:'<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8v.01"/>',
 trendUp:'<path d="M4 17l6-6 4 4 6-7"/><path d="M15 8h5v5"/>',trendDown:'<path d="M4 7l6 6 4-4 6 7"/><path d="M15 16h5v-5"/>',flat:'<path d="M4 12h16M16 8l4 4-4 4"/>',
 grip:'<path d="M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01" stroke-width="2.6"/>',copy:'<rect x="8" y="8" width="12" height="12" rx="3"/><path d="M16 8V6a2 2 0 00-2-2H6a2 2 0 00-2 2v8a2 2 0 002 2h2"/>',
 paste:'<rect x="5" y="4.5" width="14" height="16" rx="3"/><path d="M9 4.5h6v3H9zM9 12h6M9 16h4"/>',trash:'<path d="M5 7h14M9 7V5h6v2M7 7l1 13h8l1-13"/>',
 repeat:'<path d="M5 10V8a3 3 0 013-3h11l-3-3M19 14v2a3 3 0 01-3 3H5l3 3"/>',book:'<path d="M5 4.5h10a4 4 0 014 4V20H9a4 4 0 01-4-4z"/><path d="M5 16a4 4 0 014-4h10"/>',
 sliders:'<path d="M5 7h9M18 7h1M5 17h3M12 17h7"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>',clock:'<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
 bell:'<path d="M6 16V11a6 6 0 0112 0v5l1.5 2h-15z"/><path d="M10 20.5h4"/>',upload:'<path d="M12 15V4M7.5 8.5L12 4l4.5 4.5M5 15v3a2 2 0 002 2h10a2 2 0 002-2v-3"/>',
 link:'<path d="M10 14a4 4 0 005.7 0l3-3a4 4 0 00-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 00-5.7 0l-3 3a4 4 0 005.7 5.7l1-1"/>',
 spark:'<path d="M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z"/>',search:'<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4 4"/>',
 home:'<path d="M4 11l8-6.5 8 6.5V20H4z"/><path d="M10 20v-5h4v5"/>',train:'<path d="M3 9v6M6 7v10M18 7v10M21 9v6M6 12h12"/>',
 bowl:'<path d="M4 12h16a8 8 0 01-16 0z"/><path d="M12 12c0-4 2-6 5-7-1 3-2 5-5 7z"/>',scale:'<rect x="4" y="4" width="16" height="16" rx="4"/><path d="M9 9a4 4 0 016 0l-3 3"/>',
 tower:'<ellipse cx="12" cy="7" rx="4" ry="1.6"/><ellipse cx="12" cy="12" rx="6" ry="2.2"/><ellipse cx="12" cy="17.5" rx="8" ry="2.6"/>',
 target:'<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.5"/><circle cx="12" cy="12" r=".8" fill="currentColor"/>',
 history:'<path d="M4 12a8 8 0 102.5-5.8"/><path d="M4 4.5v4h4M12 8v4l3 2"/>',share:'<path d="M12 4v11M7.5 8.5L12 4l4.5 4.5"/><path d="M6 13v5a2 2 0 002 2h8a2 2 0 002-2v-5"/>',
 eye:'<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="2.8"/>',dumb:'<path d="M3 10v4M6 8v8M18 8v8M21 10v4M6 12h12"/>',
 bolt:'<path d="M13 3L5 13.5h6L10 21l8-10.5h-6z"/>',heart:'<path d="M12 20s-7.5-4.5-7.5-10A4.3 4.3 0 0112 7.5 4.3 4.3 0 0119.5 10c0 5.5-7.5 10-7.5 10z"/>',
 close:'<path d="M6 6l12 12M18 6L6 18"/>',filter:'<path d="M4 6h16M7 12h10M10 18h4"/>',sort:'<path d="M7 4v16M4 17l3 3 3-3M17 20V4M14 7l3-3 3 3"/>',
};
const ic=(n,s=20,cls='')=>`<svg class="${cls}" width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${P[n]||''}</svg>`;

function renderTabs(){
  $('#tabs').innerHTML=TABS.map(t=>`<button role="tab" data-tab="${t.id}" aria-selected="${t.id===state.tab}">${t.label}</button>`).join('');
  $('#tabs').querySelectorAll('button').forEach(b=>b.onclick=()=>{state.tab=b.dataset.tab;state.scene=null;try{history.replaceState(null,'','#'+state.tab)}catch(e){}render()});
}
function render(){
  renderTabs();
  const tab=TABS.find(t=>t.id===state.tab);
  $('#lab-title').textContent=tab.title;$('#lab-intro').textContent=tab.intro;
  const list=SCENES.filter(s=>s.tab===state.tab);
  const sel=$('#scene-select');
  $('#selectors').style.visibility=list.length&&!list[0].doc?'visible':'hidden';
  if(!state.scene||!list.some(s=>s.id===state.scene))state.scene=list[0]&&list[0].id;
  sel.innerHTML=list.map(s=>`<option value="${s.id}" ${s.id===state.scene?'selected':''}>${s.title}</option>`).join('');
  const sc=list.find(s=>s.id===state.scene);
  const stage=$('#stage');
  if(!sc){stage.innerHTML='';return}
  if(sc.doc){stage.innerHTML=`<article class="doc">${sc.doc()}</article>`;return}
  const [w,h]=state.size;const i=list.indexOf(sc);
  const n=sc.notes||{};
  stage.innerHTML=`<div class="phone-wrap" style="--pw:${w}px;--ph:${h}px;--pwn:${w}"><div class="phone" style="--fs:${state.fs}" role="img" aria-label="${sc.title}">
    <div class="statusbar"><span>12:55</span><span>5G<i></i></span></div><div class="veil"></div>${sc.html()}<div class="homebar"></div></div></div>
    <section class="notes"><div class="eyebrow">${i+1} de ${list.length} · ${tab.label}</div><h2>${sc.title}</h2>
    ${(n.tags||[]).map(t=>`<span class="tag ${t[0]}">${t[1]}</span>`).join('')}
    ${(n.p||[]).map(p=>`<p>${p}</p>`).join('')}
    ${n.list?`<h3>${n.listTitle||'Detalle'}</h3><ul>${n.list.map(l=>`<li>${l}</li>`).join('')}</ul>`:''}
    ${n.list2?`<h3>${n.list2Title||'Además'}</h3><ul>${n.list2.map(l=>`<li>${l}</li>`).join('')}</ul>`:''}
    <div class="scene-nav"><button data-nav="-1" ${i===0?'disabled':''}>← Anterior</button><button data-nav="1" ${i===list.length-1?'disabled':''}>Siguiente →</button></div></section>`;
  stage.querySelectorAll('[data-nav]').forEach(b=>b.onclick=()=>{const j=i+Number(b.dataset.nav);if(list[j]){state.scene=list[j].id;render()}});
  stage.querySelectorAll('.scroll').forEach(el=>{if(sc.scrollTo)el.scrollTop=sc.scrollTo});
  if(sc.after)sc.after(stage.querySelector('.phone'));
}
const reduceMotion=()=>{try{return matchMedia('(prefers-reduced-motion: reduce)').matches}catch(e){return false}};
// Anima un bloque: las líneas [.draw] se dibujan escalonadas y las cifras [data-count] cuentan hacia arriba.
function animateIn(root){
  if(!root)return;
  root.querySelectorAll('.draw').forEach(svg=>{const ls=svg.querySelectorAll('.ln');ls.forEach((l,i)=>{l.style.animationDelay=(i*Math.min(60,1600/ls.length))+'ms'});svg.classList.add('go')});
  root.querySelectorAll('[data-count]').forEach(el=>{const to=Number(el.dataset.count),dec=Number(el.dataset.dec||0),t0=performance.now(),dur=reduceMotion()?0:1100;
    const fmt=v=>v.toFixed(dec).replace('.',',').replace(/\B(?=(\d{3})+(?!\d))/g,'.');
    const step=t=>{const k=dur?Math.min(1,(t-t0)/dur):1;el.textContent=fmt(to*(1-Math.pow(1-k,3)));if(k<1)requestAnimationFrame(step)};requestAnimationFrame(step)});
}
$('#scene-select').onchange=e=>{state.scene=e.target.value;render()};
$('#size-select').onchange=e=>{state.size=e.target.value.split(',').map(Number);render()};
$('#font-select').onchange=e=>{state.fs=Number(e.target.value);render()};
// ?story=<clave de STORIES> pinta solo esa historia a 1080 × 1920, con las líneas ya dibujadas (para exportar la imagen).
// Los estilos y las fuentes viven dentro del cuerpo: se quita el laboratorio, no el cuerpo entero.
window.addEventListener('DOMContentLoaded',()=>{
  const m=/[?&]story=(\w+)/.exec(location.search);
  const sc=/[?&]scene=([\w-]+)/.exec(location.search);
  if(sc){const s=SCENES.find(x=>x.id===sc[1]);if(s){state.tab=s.tab;state.scene=s.id}}
  if(!m)return render();
  $('#lab').remove();
  const d=document.createElement('div');
  d.innerHTML=`<style>html,body{margin:0;background:#000;overflow:hidden}.story{border-radius:0!important;box-shadow:none!important}.draw .ln{stroke-dashoffset:0!important;animation:none!important}</style>${STORIES[m[1]]({w:'1080px'})}`;
  document.body.appendChild(d);
});
