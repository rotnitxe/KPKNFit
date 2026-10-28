/* KPKN · Perfil y Conceptos clave · v1 — prototipo interactivo. Datos de conceptos: los reales de la app (concepts-data.js). */
const $=s=>document.querySelector(s);
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const norm=s=>s.toLowerCase().normalize('NFD').replace(/\p{Mn}+/gu,'');

/* ---------- iconos de línea (24 × 24, trazo 1.8) ---------- */
const P={
  back:'<path d="M15 5l-7 7 7 7"/>',close:'<path d="M6 6l12 12M18 6L6 18"/>',gear:'<circle cx="12" cy="12" r="3.2"/><path d="M12 2.8v2.4M12 18.8v2.4M4.2 7.5l2 1.2M17.8 15.3l2 1.2M4.2 16.5l2-1.2M17.8 8.7l2-1.2"/><circle cx="12" cy="12" r="7"/>',
  camera:'<path d="M4 8h3l2-2.5h6L17 8h3v11H4z"/><circle cx="12" cy="13" r="3.4"/>',edit:'<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
  chev:'<path d="M9 5l7 7-7 7"/>',star:'<path d="M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.8 6.8 19.6l1-5.8-4.3-4.1 5.9-.8z"/>',
  layers:'<path d="M12 4l9 5-9 5-9-5z"/><path d="M3 14l9 5 9-5"/>',trend:'<path d="M4 17l5-5 4 3 7-8"/><path d="M15 7h5v5"/>',
  photo:'<rect x="3.5" y="5" width="17" height="14" rx="2.5"/><circle cx="9" cy="10" r="1.8"/><path d="M20.5 16l-5-5-8 8"/>',cal:'<rect x="4" y="5.5" width="16" height="14.5" rx="2.5"/><path d="M4 10h16M8.5 3.5v4M15.5 3.5v4"/>',
  video:'<rect x="3.5" y="6" width="12.5" height="12" rx="2.5"/><path d="M16 10.5l4.5-2.5v8l-4.5-2.5z"/>',trophy:'<path d="M8 4h8v5a4 4 0 01-8 0z"/><path d="M8 6H5a3 3 0 003 4M16 6h3a3 3 0 01-3 4M12 13v4M8.5 20h7"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',trash:'<path d="M5 7h14M10 4h4M7 7l1 13h8l1-13"/>',search:'<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.2-4.2"/>',
  book:'<path d="M4.5 5.5c3-1.3 5.4-1 7.5.8 2.1-1.8 4.5-2.1 7.5-.8v13c-3-1.3-5.4-1-7.5.8-2.1-1.8-4.5-2.1-7.5-.8z"/><path d="M12 6.3v13"/>',
  ext:'<path d="M14 4h6v6M20 4l-9 9"/><path d="M18 14v5H5V6h5"/>',alert:'<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5v5.5M12 16.2v.3"/>',
  barbell:'<path d="M3 12h18M6 8v8M9 6.5v11M15 6.5v11M18 8v8"/>',flame:'<path d="M12 3.5c3.5 3.7 6 6.4 6 10a6 6 0 01-12 0c0-2.2 1-3.8 2.5-5.2.3 1.8 1.1 2.9 2.3 3.4C10.6 9 11 6.2 12 3.5z"/>',
  recovery:'<path d="M18.5 14.5A7 7 0 019.5 5.5a7 7 0 109 9z"/>',muscle:'<path d="M5 15c0-4 2-8 5-9.5 1.5 1.5 1.4 3.3.3 4.5 3.5-.6 6.8 1.3 8.2 4.5-2.4 3.4-6.7 5-11 4.2C6 18.4 5 17 5 15z"/>',
  movement:'<path d="M5 18A9 9 0 0118 6"/><path d="M13.5 5.2L18 6l-.8 4.5"/><circle cx="5" cy="18" r="1.6"/>',equipment:'<circle cx="12" cy="6.5" r="3"/><path d="M12 9.5V20M8 20h8M9.5 14h5"/>',
  qualities:'<path d="M13 3L5.5 13.5H11L10 21l8-11h-5.5z"/>',calendar:'<rect x="4" y="5.5" width="16" height="14.5" rx="2.5"/><path d="M4 10h16M8 14h3M13 14h3M8 17h3"/>'
};
const ic=(n,s=20,w=1.8)=>`<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${P[n]}</svg>`;

/* Color solo en detalles: tonos apagados por tema (punto, glifo), nunca en fondos completos. */
const CAT={LOAD_MANAGEMENT:{c:'#f49a6e',g:'barbell'},INTENSITY:{c:'#e9736c',g:'flame'},FATIGUE:{c:'#b49ad8',g:'recovery'},HYPERTROPHY:{c:'#8fb3e0',g:'muscle'},MOVEMENT:{c:'#87c9a5',g:'movement'},EQUIPMENT:{c:'#7fc4bc',g:'equipment'},QUALITIES:{c:'#cdbb8f',g:'qualities'},PERIODIZATION:{c:'#9cb0bd',g:'calendar'}};
const catLabel=k=>CATEGORIES.find(c=>c.key==='' + k).label;
const coll=new Intl.Collator('es',{sensitivity:'base'});
const ORDERED=[...CONCEPTS].sort((a,b)=>CATEGORIES.findIndex(c=>c.key===a.cat)-CATEGORIES.findIndex(c=>c.key===b.cat)||coll.compare(a.name,b.name));
const byId=id=>CONCEPTS.find(c=>c.id===id);
const minutes=c=>Math.max(1,Math.round(c.body.join(' ').split(/\s+/).length/200));
/* Misma regla que el «Aprende» de Home: floorMod(epochDay, n) sobre la lista sin ordenar. */
const DAILY=CONCEPTS[((Math.floor(Date.UTC(2026,9,27)/864e5))%CONCEPTS.length+CONCEPTS.length)%CONCEPTS.length];

/* ---------- datos de ejemplo del perfil ---------- */
const FULL={name:'Alex Moreno',photo:true,height:'178',weight:'82,5',unit:'kg',gender:'Masculino',
  starred:[{n:'Sentadilla trasera con barra alta',erm:'142,5',goal:'160',p:.89},{n:'Press de banca',erm:'102,5',goal:'120',p:.85},{n:'Peso muerto convencional',erm:'180',goal:null,p:null}],
  goal:{metric:'Meta de peso',start:'88 kg',now:'82,5 kg',target:'78 kg',pct:55},
  top:[{n:'Sentadilla trasera con barra alta',d:'24 sesiones · Barra'},{n:'Press de banca',d:'22 sesiones · Barra'},{n:'Remo en polea baja',d:'15 sesiones · Polea'},{n:'Dominadas',d:'12 sesiones · Peso corporal'}],
  tech:{thumbs:[['5 reps · fiable',''],['Analizando 40 %','q'],['6 reps · revisar','']],summary:'7 vídeos analizados · último el 24 oct',concl:'Sentadilla trasera con barra alta · profundidad estable en las 5 repeticiones'},
  meets:[{t:'Open Regional',d:'14 sep 2026 · AEP · −83 kg',s:'165',b:'110',dl:'200',tot:'475',pts:'GL 78,4'}]};
const EMPTY={name:'',photo:false,height:'',weight:'',unit:'kg',gender:'',starred:[],goal:null,top:[],tech:null,meets:[]};

/* ---------- estado ---------- */
const S={tab:'perfil',scene:'p-full',size:[360,740],fs:1,profile:FULL,sheet:null,edit:null,err:null,
  lib:{view:'home',q:'',topic:null,id:null,fromLink:false,scrollTo:0}};

const SCENES={
  perfil:[
    {id:'p-full',label:'Perfil con datos',set:()=>{S.profile=FULL;S.sheet=null}},
    {id:'p-new',label:'Perfil nuevo, sin datos',set:()=>{S.profile=EMPTY;S.sheet=null}},
    {id:'p-photo',label:'Foto: cambiar o quitar',set:()=>{S.profile=FULL;S.sheet='photo'}},
    {id:'p-edit',label:'Editar perfil',set:()=>{S.profile=FULL;openEdit()}},
    {id:'p-error',label:'Editar perfil · error',set:()=>{S.profile=FULL;openEdit();S.edit.name='';S.err='Escribe un nombre para continuar.'}},
    {id:'p-bottom',label:'Final de la página',set:()=>{S.profile=FULL;S.sheet=null;S.scrollTo='bottom'}},
  ],
  conceptos:[
    {id:'c-home',label:'Biblioteca',set:()=>lib({view:'home'})},
    {id:'c-topic',label:'Tema: Intensidad y esfuerzo',set:()=>lib({view:'topic',topic:'INTENSITY'})},
    {id:'c-search',label:'Búsqueda «fatiga»',set:()=>lib({view:'home',q:'fatiga'})},
    {id:'c-empty',label:'Búsqueda sin resultados',set:()=>lib({view:'home',q:'creatina'})},
    {id:'c-read',label:'Lectura: RIR',set:()=>lib({view:'read',id:'rir'})},
    {id:'c-read-end',label:'Lectura: final (fuentes y relacionados)',set:()=>lib({view:'read',id:'rir',scrollTo:'bottom'})},
    {id:'c-link',label:'Lectura abierta desde Home o el glosario',set:()=>lib({view:'read',id:DAILY.id,fromLink:true})},
  ]};
function lib(o){S.lib={view:'home',q:'',topic:null,id:null,fromLink:false,scrollTo:0,...o}}
function openEdit(){S.sheet='edit';S.err=null;const p=S.profile;S.edit={name:p.name,gender:p.gender||'No especificar',height:p.height,weight:p.weight}}

/* ---------- notas por estado ---------- */
const NOTES={
  'p-full':{h:'Perfil: identidad, ficha y progreso',p:['La pantalla deja de ser una pila de tarjetas iguales con títulos en azul. Arriba, la persona: foto grande con anillo crema, nombre en Syne y una ficha de vidrio con estatura, peso y sexo. Debajo, lo que más se consulta (ejercicios estrella y meta corporal) y al final los accesos y el archivo.','Sin barra de Material: atrás y Ajustes son botones de vidrio flotantes; al desplazar aparece la isla con el nombre, como en la sesión en vivo.'],
    keep:['Foto: elegir y quitar','Editar nombre, sexo, estatura y peso (kg o lb) con las mismas validaciones','Datos corporales','Ejercicios estrella: eRM, meta y % hacia la meta; tocar abre su progreso','Progreso nutricional: inicio, ahora, meta y %','Progreso: Volumen por músculo, Progreso por ejercicio y los 4 más entrenados','Técnica en vídeo: 3 recientes, resumen, conclusión y «Abrir»','Álbumes y Hábitos','Archivo de competiciones (registrar, abrir, comparar, álbum)','Ajustes y los dos anclajes del recorrido guiado'],
    nw:['Ficha corporal tocable','Anillo de avance por ejercicio','Pista Inicio → Ahora → Meta','Isla al desplazar']},
  'p-new':{h:'Perfil nuevo',p:['Cada hueco invita a completarlo en su sitio: «Añadir» en la ficha abre el editor, la foto se elige tocando el círculo y cada sección explica en una frase cómo se llena. Los textos son los de hoy.','Técnica y el archivo de competiciones conservan sus estados vacíos actuales («Registrar powerlifting»).'],keep:['Mismos mensajes de estado vacío que la pantalla actual'],nw:['«Añadir» directo en la ficha']},
  'p-photo':{h:'Foto de perfil',p:['Sin foto, tocar el círculo abre la galería directamente, como hoy la cámara. Con foto, abre esta hoja con la hoja del kit de la sesión en vivo (velo, panel #141513, asa, título en Syne y ✕): «Cambiar foto» y «Quitar foto» en rojo.','«Quitar foto» deja de ser un enlace pequeño bajo el nombre.'],keep:['Elegir de la galería','Quitar foto'],nw:['Hoja del kit LiveSheet']},
  'p-edit':{h:'Editar perfil',p:['El editor sale de la página a una hoja: Nombre, Sexo en cuatro opciones a la vista (sin menú desplegable), Estatura en cm y Peso en la unidad de Ajustes. «Guardar» es la acción principal; «Cancelar» y ✕ descartan.','Se guardan los mismos campos con las mismas reglas que hoy (`saveProfile`).'],keep:['Nombre, Sexo (incl. «No especificar»), Estatura, Peso en kg o lb','Cancelar / Guardar'],nw:['Opciones de sexo a la vista','Campos del kit, no OutlinedTextField']},
  'p-error':{h:'Editar perfil · error',p:['Los mensajes de error son los actuales: «Escribe un nombre para continuar.», «La estatura debe ser mayor que cero.» y «El peso debe ser mayor que cero.». Se muestran junto al campo que falla, con su borde en rojo suave.'],keep:['Las tres validaciones de hoy'],nw:['Error junto a su campo']},
  'p-bottom':{h:'Final de la página',p:['Técnica en vídeo conserva sus tres miniaturas con estado, el resumen y la última conclusión. Álbumes y Hábitos pasan a dos mosaicos lado a lado. El archivo de competiciones se mantiene con su lógica (registrar, abrir, comparar dos meets y álbum) y solo adopta la cabecera y el módulo de la página.','La propuesta «Registros v1» (pendiente) cubre el rediseño interno de competiciones; aquí no se toca.'],keep:['Todo lo de técnica, álbumes, hábitos y competiciones'],nw:['Mosaicos Álbumes / Hábitos']},
  'c-home':{h:'Conceptos clave: una biblioteca, no una lista',p:['La lista plana de acordeones pasa a ser una biblioteca con tres niveles: el concepto del día (el mismo que enseña «Aprende» en Home), los 8 temas como mosaicos con su glifo y recuento, y todos los conceptos agrupados por tema con su resumen de una frase completo.','Tocar un concepto abre la lectura a pantalla completa, en vez de desplegarlo dentro de la lista. El buscador queda fijo bajo el título mientras se desplaza.','Prueba el buscador: busca de verdad en los 31 conceptos de la app, por nombre, resumen, texto y tema, sin tildes.'],
    keep:['Los 31 conceptos con su texto completo','Orden: tema pedagógico y nombre A-Z','Búsqueda por nombre, resumen, texto y tema','Enlaces desde Home, el glosario de los planes y kpkn://concepts'],nw:['Concepto del día','Mosaicos de temas','Lectura dedicada','Color de tema solo en punto y glifo']},
  'c-topic':{h:'Un tema',p:['Al tocar un mosaico se abre el tema: glifo en su tono, nombre en Syne, recuento y sus conceptos con el resumen. Al final, el siguiente tema para seguir leyendo sin volver atrás.'],keep:['Agrupación por tema de hoy'],nw:['Página de tema','Siguiente tema']},
  'c-search':{h:'Búsqueda',p:['Mientras se escribe desaparecen el concepto del día y los temas: solo quedan los resultados, agrupados por tema, con la coincidencia resaltada en crema. Si coincide solo en el texto largo, se muestra el fragmento y la marca «En el texto».'],keep:['Misma búsqueda de `searchConceptosClave`'],nw:['Resaltado','Fragmento de la coincidencia','Recuento de resultados']},
  'c-empty':{h:'Sin resultados',p:['Hoy dice «No hay conceptos que coincidan.». Ahora repite la búsqueda, explica qué se puede buscar y ofrece conceptos reales para tocar.'],keep:['El estado vacío'],nw:['Sugerencias tocables','Borrar búsqueda']},
  'c-read':{h:'Lectura',p:['La lectura sustituye al acordeón: tema y minutos de lectura, título en Syne 31, el resumen de una frase como entradilla en crema, el texto en Inter 15 con interlineado amplio y el apartado práctico de cada concepto («En la práctica») en un bloque de vidrio.','Al desplazar aparece la isla con el título y el tema.'],keep:['El texto completo de cada concepto, sin recortes'],nw:['Entradilla','«En la práctica» destacado','Minutos de lectura']},
  'c-read-end':{h:'Final de la lectura',p:['Debajo del texto: las fuentes de cada concepto (ya existen en `ConceptSourceRegistry`, dos por concepto, hoy no se muestran) que abren PubMed en el navegador; «Del mismo tema» con hasta tres conceptos, y Anterior / Siguiente en el orden de la biblioteca.'],keep:[],nw:['Fuentes visibles','Del mismo tema','Anterior / Siguiente'],open:['¿Mostrar las fuentes? El código dice que se ocultaron a propósito en el acordeón; propongo mostrarlas en la lectura porque dan confianza. Si prefieres que no, se quita esa sección.']},
  'c-link':{h:'Abierto desde un enlace',p:['Cuando se llega desde «Leer» en Home, el glosario de un plan o un enlace kpkn://concepts/<id>, la pantalla abre directamente la lectura. ✕ vuelve a donde estabas, como hoy, y al final se ofrece «Ver los 31 conceptos» para entrar en la biblioteca.'],keep:['Ruta concepts?expand=<id> y enlaces profundos'],nw:['Abre en la lectura en vez de en la lista desplegada']},
};

/* ---------- render: marco ---------- */
function render(){
  document.querySelectorAll('.tabs button').forEach(b=>b.setAttribute('aria-selected',b.dataset.tab===S.tab));
  const sel=$('#scene-select');sel.innerHTML=SCENES[S.tab].map(s=>`<option value="${s.id}"${s.id===S.scene?' selected':''}>${esc(s.label)}</option>`).join('');
  const n=NOTES[S.scene]||{};
  $('#lab-eyebrow').textContent=S.tab==='perfil'?'PERFIL DEL USUARIO':'CONCEPTOS CLAVE';
  $('#lab-title').textContent=S.tab==='perfil'?'Perfil':'Conceptos clave';
  $('#lab-intro').textContent=S.tab==='perfil'?'Refinado de la pantalla actual: todas sus funciones se conservan, con la identidad de KPKN (negro, carbón, crema, Syne e Inter) y la hoja del kit de la sesión en vivo.':'De lista de acordeones a biblioteca: concepto del día, temas, búsqueda con resaltado y una lectura dedicada. Contiene los 31 conceptos reales de la app.';
  const [w,h]=S.size;
  $('#stage').innerHTML=`<div class="phone-wrap" style="--pw:${w}px;--ph:${h}px;--pwn:${w}"><div class="phone" id="phone" style="--fs:${S.fs}">
    <div class="topfade"></div><div class="statusbar"><span>9:41</span><span>5G<i></i></span></div>
    <div class="screen" id="screen">${S.tab==='perfil'?profilePage():libPage()}</div>
    ${S.tab==='perfil'?profileBar():libBar()}${sheetHtml()}<div class="homebar"></div></div></div>
    <aside class="notes"><div class="eyebrow">${esc(SCENES[S.tab].find(s=>s.id===S.scene)?.label||'')}</div><h2>${esc(n.h||'')}</h2>${(n.p||[]).map(t=>`<p>${esc(t).replace(/`([^`]+)`/g,'<code>$1</code>')}</p>`).join('')}
    ${n.keep&&n.keep.length?`<h3>Se conserva</h3><ul>${n.keep.map(t=>`<li>${esc(t)}</li>`).join('')}</ul>`:''}
    ${n.nw&&n.nw.length?`<h3>Nuevo</h3>${n.nw.map(t=>`<span class="tag new">${esc(t)}</span>`).join('')}`:''}
    ${n.open?`<h3>Decisión abierta</h3>${n.open.map(t=>`<p>${esc(t)}</p>`).join('')}`:''}
    <div class="scene-nav"><button data-step="-1">‹ Anterior</button><button data-step="1">Siguiente ›</button></div></aside>`;
  const sc=$('#screen');
  if(S.scrollTo==='bottom'||S.lib.scrollTo==='bottom'){sc.scrollTop=sc.scrollHeight}
  S.scrollTo=0;S.lib.scrollTo=0;
  sc.addEventListener('scroll',onScroll);onScroll();
  const q=$('#q');if(q&&S.focus){q.focus();q.setSelectionRange(q.value.length,q.value.length)}S.focus=false;
}
function onScroll(){const sc=$('#screen'),bar=$('#topbar');if(!sc||!bar)return;bar.classList.toggle('solid',sc.scrollTop>(S.tab==='perfil'?190:110))}

/* ---------- perfil ---------- */
function profileBar(){const p=S.profile;return `<div class="topbar" id="topbar"><button class="gbtn" aria-label="Volver">${ic('back')}</button>
  <div class="bar-title"><strong>${esc(p.name||'Perfil')}</strong><span>Perfil</span></div><button class="gbtn" aria-label="Ajustes" style="margin-left:auto">${ic('gear')}</button></div>`}
function initials(n){return (n.trim().split(/\s+/).slice(0,2).map(x=>x[0]?.toUpperCase()).join(''))||'U'}
function ringSvg(p){const r=19,c=2*Math.PI*r;return `<svg width="46" height="46" viewBox="0 0 46 46"><circle cx="23" cy="23" r="${r}" fill="none" stroke="#262626" stroke-width="4"/><circle cx="23" cy="23" r="${r}" fill="none" stroke="#f2eee6" stroke-width="4" stroke-linecap="round" stroke-dasharray="${c}" stroke-dashoffset="${c*(1-p)}"/></svg>`}
function sec(label,icon,body,act){return `<section class="section"><div class="sec-head">${icon?`<span style="color:var(--faint)">${ic(icon,15)}</span>`:''}<span class="eyebrow">${label}</span>${act||''}</div>${body}</section>`}
function profilePage(){
  const p=S.profile,named=!!p.name;
  const fv=(lbl,v,u,word)=>`<button data-act="edit" aria-label="${lbl}: ${v||'añadir'}"><span class="lbl">${lbl}</span>${v?`<span class="val${word?' word':''}">${esc(v)}${u?`<small>${u}</small>`:''}</span>`:'<span class="add link">Añadir</span>'}</button>`;
  let h=`<div class="page"><div class="hero">
    <button class="avatar" data-act="${p.photo?'photo':'pick'}" aria-label="${p.photo?'Foto de perfil: cambiar o quitar':'Elegir foto de perfil'}"><div class="pic${p.photo?' photo':''}">${p.photo?'':initials(p.name||'')}</div><span class="badge">${ic('camera',16,2)}</span></button>
    <h2>${named?esc(p.name):'Añade tu nombre'}</h2><div class="sub">${p.photo?'Tu perfil personal':'Personaliza tu foto y tus datos'}</div>
    <button class="secondary" data-act="edit">${ic('edit',15)} Editar perfil</button>
    <div class="ficha glass">${fv('Estatura',p.height,'cm')}${fv('Peso',p.weight,p.unit)}${fv('Sexo',p.gender,'',true)}</div></div>`;
  /* ejercicios estrella */
  h+=sec('Ejercicios estrella','star',`<div class="module">${p.starred.length?p.starred.map(e=>`<button class="row">
      ${e.p!=null?`<div class="ring">${ringSvg(e.p)}<b>${Math.round(e.p*100)}%</b></div>`:`<div class="ibub star-ic">${ic('star',18)}</div>`}
      <div class="grow"><div class="ttl">${esc(e.n)}</div><div class="sub">${e.goal?`Meta ${e.goal} kg · ${Math.round(e.p*100)} % hacia la meta`:'Sin meta RM · define una para ver el avance'}</div></div>
      <div class="erm"><div class="n">${e.erm}</div><div class="u">kg eRM</div></div></button>`).join(''):`<div class="pad hint">Marca ejercicios con estrella desde tus sesiones para seguir aquí su RM y objetivo.</div>`}</div>`);
  /* meta corporal */
  const g=p.goal;
  h+=sec('Progreso nutricional','',`<div class="module goal">${g?`<div class="goal-top"><div><div class="syne t" style="--s:18">${g.metric}</div><div class="hint" style="margin-top:4px">Desde tu plan de nutrición activo</div></div><div class="pct">${g.pct}<span class="pc">%</span><small>completado</small></div></div>
      <div class="track"><div class="fill" style="width:${g.pct}%"></div><div class="end" style="left:0"></div><div class="end meta" style="left:100%"></div><div class="dot" style="left:${g.pct}%"></div></div>
      <div class="goal-vals"><div>Inicio<b>${g.start}</b></div><div>Ahora<b>${g.now}</b></div><div>Meta<b>${g.target}</b></div></div>`:`<div class="hint">Crea una meta corporal para empezar a seguir tu avance.</div>`}</div>`);
  /* progreso */
  h+=sec('Progreso','layers',`<div class="module">
    <button class="row"><div class="ibub">${ic('layers',18)}</div><div class="grow"><div class="ttl">Volumen por músculo</div><div class="sub">Series registradas por músculo en 1, 4 o 12 semanas, con los ejercicios que las explican.</div></div><span class="chev">${ic('chev',18)}</span></button>
    <button class="row"><div class="ibub">${ic('trend',18)}</div><div class="grow"><div class="ttl">Progreso por ejercicio</div><div class="sub">Métricas, récords y sesiones de tus ejercicios más entrenados.</div></div><span class="chev">${ic('chev',18)}</span></button>
    ${p.top.length?`<div class="sublabel">Tus más entrenados</div>${p.top.map((e,i)=>`<button class="row"><div class="rank">${i+1}</div><div class="grow"><div class="ttl">${esc(e.n)}</div><div class="sub">${esc(e.d)}</div></div><span class="chev">${ic('chev',18)}</span></button>`).join('')}`:''}</div>`);
  /* técnica */
  const t=p.tech;
  h+=sec('Técnica en vídeo','video',`<div class="module">${t?`<div class="thumbs">${t.thumbs.map(([b,q])=>`<button class="thumb" aria-label="Abrir análisis: ${b}"><span class="bd ${q}">${b}</span></button>`).join('')}</div>
      <div class="tech-text"><div class="s">${t.summary}</div><div class="c">${esc(t.concl)}</div></div>`:`<div class="tech-text"><div class="c" style="margin:0">Analiza tus series y sigue las tendencias de tu técnica.</div><div class="s" style="margin-top:6px">Graba una serie desde el Modo visual de la sesión o analiza un vídeo de tu galería</div></div>`}
    <div class="pad"><button class="secondary" style="width:100%">${ic('video',16)} Abrir técnica en vídeo</button></div></div>`);
  /* accesos */
  h+=sec('Tu archivo','',`<div class="tiles"><button class="tile module"><div class="ibub">${ic('photo',18)}</div><div><div class="ttl">Álbumes de entrenamiento</div><div class="sub" style="margin-top:5px">Fotos y vídeos de tus series, agrupados por sesión.</div></div><span class="go">Ver álbumes ${ic('chev',14)}</span></button>
    <button class="tile module"><div class="ibub">${ic('cal',18)}</div><div><div class="ttl">Hábitos</div><div class="sub" style="margin-top:5px">Rachas de comidas, pesaje y entrenos, con su calendario del último año.</div></div><span class="go">Ver hábitos ${ic('chev',14)}</span></button></div>`);
  /* competiciones */
  h+=sec('Archivo de competiciones','trophy',`<div class="module meet">${p.meets.length?p.meets.map(m=>`<div class="meet-top"><div class="ibub">${ic('trophy',18)}</div><div class="grow"><div class="ttl">${m.t}</div><div class="sub">${m.d}</div></div><button class="chip">Comparar</button></div>
      <div class="lifts"><div>S<b>${m.s}</b></div><div>B<b>${m.b}</b></div><div>D<b>${m.dl}</b></div><div>TOTAL<b>${m.tot}</b></div></div><div class="hint" style="margin-top:10px">${m.pts} · Elige dos meets para comparar.</div>`).join('')+`<button class="dashed">${ic('plus',16)} Registrar otra</button>`:`<button class="dashed" style="margin:0">${ic('plus',16)} Registrar powerlifting</button>`}</div>`);
  return h+'<div style="height:40px"></div></div>';
}

/* ---------- hojas ---------- */
function sheetHtml(){
  if(S.tab!=='perfil'||!S.sheet)return '';
  if(S.sheet==='photo')return `<div class="veil" data-act="close"><div class="sheet" data-stop><div class="handle"></div><div class="sheet-head"><div class="grow"><h3>Foto de perfil</h3></div><button class="xbtn" data-act="close" aria-label="Cerrar panel">${ic('close',18)}</button></div>
    <div class="sheet-body"><button class="menu-row" data-act="pick"><div class="ibub">${ic('photo',18)}</div><div class="grow"><div class="ttl">Cambiar foto</div><div class="sub">Elige una imagen de tu galería</div></div></button>
    <button class="menu-row danger" data-act="remove"><div class="ibub">${ic('trash',18)}</div><div class="grow"><div class="ttl">Quitar foto</div><div class="sub" style="color:var(--muted)">Vuelven a verse tus iniciales</div></div></button></div></div></div>`;
  const e=S.edit,er=S.err||'',nameErr=/nombre/.test(er),hErr=/estatura/.test(er),wErr=/peso/i.test(er);
  const errNote=m=>`<div class="note-err">${ic('alert',16)}<span>${esc(m)}</span></div>`;
  return `<div class="veil" data-act="close"><div class="sheet" data-stop><div class="handle"></div><div class="sheet-head"><div class="grow"><h3>Editar perfil</h3></div><button class="xbtn" data-act="close" aria-label="Cerrar panel">${ic('close',18)}</button></div>
  <div class="sheet-body">
    <div class="field" style="margin-top:4px"><label for="f-name">Nombre</label><div class="input${nameErr?' err':''}"><input id="f-name" data-f="name" value="${esc(e.name)}" placeholder="Tu nombre"></div>${nameErr?errNote(er):''}</div>
    <div class="field"><label>Sexo</label><div class="choices">${['No especificar','Masculino','Femenino','Otro'].map(o=>`<button class="choice" data-g="${o}" aria-pressed="${e.gender===o}">${o}</button>`).join('')}</div></div>
    <div class="two"><div class="field"><label for="f-h">Estatura</label><div class="input${hErr?' err':''}"><input id="f-h" data-f="height" inputmode="decimal" value="${esc(e.height)}" placeholder="—"><span class="unit">cm</span></div></div>
    <div class="field"><label for="f-w">Peso</label><div class="input${wErr?' err':''}"><input id="f-w" data-f="weight" inputmode="decimal" value="${esc(e.weight)}" placeholder="—"><span class="unit">${S.profile.unit}</span></div></div></div>
    ${hErr||wErr?errNote(er):''}
    <div class="note">La unidad de peso se cambia en Ajustes. Estatura y peso pueden quedar vacíos.</div></div>
  <div class="sheet-foot"><button class="primary" data-act="save">Guardar</button><button class="textbtn" data-act="close">Cancelar</button></div></div></div>`;
}
function save(){
  const e=S.edit,pos=v=>v===''||(+v.replace(',','.')>0);
  if(!e.name.trim())S.err='Escribe un nombre para continuar.';
  else if(!pos(e.height))S.err='La estatura debe ser mayor que cero.';
  else if(!pos(e.weight))S.err='El peso debe ser mayor que cero.';
  else{S.err=null;S.profile={...S.profile,name:e.name.trim(),gender:e.gender==='No especificar'?'':e.gender,height:e.height,weight:e.weight};S.sheet=null}
  render();
}

/* ---------- conceptos ---------- */
function libBar(){const L=S.lib;let title='Conceptos clave',sub=CONCEPTS.length+' conceptos',icon=L.view==='home'||L.fromLink?'close':'back';
  if(L.view==='read'){const c=byId(L.id);title=c.name;sub=catLabel(c.cat)}
  if(L.view==='topic'){title=catLabel(L.topic);sub='Tema'}
  return `<div class="topbar" id="topbar"><button class="gbtn" data-act="${icon==='close'?'exit':'back'}" aria-label="${icon==='close'?'Cerrar':'Volver'}">${ic(icon)}</button><div class="bar-title"><strong>${esc(title)}</strong><span>${esc(sub)}</span></div></div>`}
function hl(text,q){if(!q)return esc(text);const n=norm(text),i=n.indexOf(q);if(i<0)return esc(text);return esc(text.slice(0,i))+'<mark>'+esc(text.slice(i,i+q.length))+'</mark>'+esc(text.slice(i+q.length))}
function snippet(c,q){const full=c.body.join(' '),n=norm(full),i=n.indexOf(q);if(i<0)return null;const a=Math.max(0,full.lastIndexOf(' ',Math.max(0,i-50))),b=full.indexOf(' ',Math.min(full.length,i+q.length+60));return (a>0?'…':'')+hl(full.slice(a,b<0?full.length:b).trim(),q)+'…'}
function crow(c,q){const inName=q&&norm(c.name).includes(q),inShort=q&&norm(c.short).includes(q),inCat=q&&norm(catLabel(c.cat)).includes(q);
  const snip=q&&!inName&&!inShort&&!inCat?snippet(c,q):null;
  return `<button class="row crow" data-open="${c.id}"><div class="grow"><div class="ttl">${hl(c.name,q)}</div><div class="sub">${snip||hl(c.short,q)}</div>${snip?'<span class="where">En el texto</span>':''}</div><span class="chev">${ic('chev',18)}</span></button>`}
function grouped(list,q){let h='';CATEGORIES.forEach(cat=>{const items=list.filter(c=>c.cat===cat.key);if(!items.length)return;
  h+=`<div class="group-head"><span class="cdot" style="--c:${CAT[cat.key].c}"></span><span class="eyebrow">${hl(cat.label,q)} · ${items.length}</span></div><div class="module">${items.map(c=>crow(c,q)).join('')}</div>`});return h}
function search(q){const n=norm(q.trim());if(!n)return ORDERED;return ORDERED.filter(c=>[c.name,c.short,catLabel(c.cat),...c.body].some(t=>norm(t).includes(n)))}
function libPage(){const L=S.lib;
  if(L.view==='read')return readPage(byId(L.id));
  if(L.view==='topic'){const k=L.topic,items=ORDERED.filter(c=>c.cat===k),ix=CATEGORIES.findIndex(c=>c.key===k),nx=CATEGORIES[(ix+1)%CATEGORIES.length];
    return `<div class="page"><div class="topic-hero" style="--c:${CAT[k].c}"><div class="tg">${ic(CAT[k].g,26)}</div><h1>${esc(catLabel(k))}</h1><div class="eyebrow">Tema · ${items.length} conceptos</div></div>
      <div class="module" style="margin-top:20px">${items.map(c=>crow(c,'')).join('')}</div>
      <button class="row module next-topic" data-topic="${nx.key}" style="--c:${CAT[nx.key].c}"><div class="topic" style="padding:0;min-height:0"><div class="tg">${ic(CAT[nx.key].g,18)}</div></div><div class="grow"><div class="eyebrow">Siguiente tema</div><div class="ttl" style="margin-top:3px">${esc(nx.label)}</div></div><span class="chev">${ic('chev',18)}</span></button><div style="height:40px"></div></div>`}
  const q=norm(L.q.trim()),res=search(L.q);
  let h=`<div class="page"><div class="lib-head"><div class="eyebrow">Aprende</div><h1>Conceptos clave</h1><p>Aprende lo que cambia tu entrenamiento. ${CONCEPTS.length} conceptos en ${CATEGORIES.length} temas.</p></div>
    <div class="search"><div class="input">${ic('search',19)}<input id="q" value="${esc(L.q)}" placeholder="Buscar conceptos" aria-label="Buscar conceptos">${L.q?`<button class="xbtn" data-act="clear" aria-label="Limpiar búsqueda" style="width:36px;height:36px">${ic('close',16)}</button>`:''}</div></div>`;
  if(q){
    if(!res.length)return h+`<div class="empty"><div class="ibub">${ic('search',26)}</div><h3>Nada con «${esc(L.q.trim())}»</h3><p>Busca por nombre, sigla o idea: el buscador también mira dentro del texto de cada concepto. Prueba con:</p>
      <div class="sugg">${['rir','volumen-entrenamiento','deload','tension-mecanica'].map(id=>`<button class="chip" data-sugg="${byId(id).name.split(' (')[0]}">${esc(byId(id).name.split(' (')[0])}</button>`).join('')}</div><div class="foot-link"><button class="secondary" data-act="clear">Borrar búsqueda</button></div></div></div>`;
    return h+`<div class="result-count">${res.length} ${res.length===1?'resultado':'resultados'} para «${esc(L.q.trim())}»</div>${grouped(res,q)}<div style="height:40px"></div></div>`;
  }
  const d=DAILY;
  h+=`<section class="section" style="margin-top:22px"><div class="feature glass" style="--c:${CAT[d.cat].c}"><span class="glyph" style="color:${CAT[d.cat].c}">${ic(CAT[d.cat].g,140,1.2)}</span>
    <div class="cat-line"><span class="cdot"></span>Concepto del día · ${esc(catLabel(d.cat))}</div><h3>${esc(d.name)}</h3><p>${esc(d.short)}</p><button class="primary" data-open="${d.id}">${ic('book',17)} Leer · ${minutes(d)} min</button></div></section>`;
  h+=sec('Temas','',`<div class="topics">${CATEGORIES.map(c=>{const n=CONCEPTS.filter(x=>x.cat===c.key).length;return `<button class="topic module" data-topic="${c.key}" style="--c:${CAT[c.key].c}"><div class="tg">${ic(CAT[c.key].g,19)}</div><div><div class="ttl">${esc(c.label)}</div><div class="cnt">${n} conceptos</div></div></button>`}).join('')}</div>`);
  h+=`<section class="section"><div class="sec-head"><span class="eyebrow">Todos los conceptos</span></div>${grouped(ORDERED,'')}</section><div style="height:40px"></div></div>`;
  return h;
}
function readPage(c){const ix=ORDERED.indexOf(c),prev=ORDERED[ix-1],next=ORDERED[ix+1],rel=ORDERED.filter(x=>x.cat===c.cat&&x!==c).slice(0,3),body=c.body.slice(0,-1),practice=c.body[c.body.length-1];
  return `<div class="read" style="--c:${CAT[c.cat].c}"><div class="cat-line"><span class="cdot"></span>${esc(catLabel(c.cat))} · ${minutes(c)} min de lectura</div><h1>${esc(c.name)}</h1><p class="lead">${esc(c.short)}</p><div class="rule"></div>
    <div class="body">${body.map(p=>`<p>${esc(p)}</p>`).join('')}<div class="practice glass"><div class="eyebrow"><span class="cdot"></span>En la práctica</div><p>${esc(practice)}</p></div></div>
    ${sec('Fuentes','book',`<div class="module">${c.sources.map(s=>`<button class="src"><div class="grow"><div class="ttl">${esc(s.title)}</div><div class="sub">${esc(s.publisher)}</div></div><span style="color:var(--faint)">${ic('ext',16)}</span></button>`).join('')}</div>`)}
    ${rel.length?sec('Del mismo tema','',`<div class="module">${rel.map(x=>crow(x,'')).join('')}</div>`):''}
    <div class="pn">${prev?`<button class="module" data-open="${prev.id}"><span class="eyebrow">‹ Anterior</span><span class="ttl">${esc(prev.name)}</span></button>`:'<span></span>'}${next?`<button class="module" data-open="${next.id}"><span class="eyebrow">Siguiente ›</span><span class="ttl">${esc(next.name)}</span></button>`:'<span></span>'}</div>
    ${S.lib.fromLink?`<div class="foot-link"><button class="secondary" data-act="library">${ic('book',16)} Ver los ${CONCEPTS.length} conceptos</button></div>`:''}<div style="height:40px"></div></div>`}

/* ---------- eventos ---------- */
document.addEventListener('click',ev=>{
  const t=ev.target.closest('[data-tab],[data-step],[data-act],[data-open],[data-topic],[data-sugg],[data-g]');
  if(!t){return}
  if(t.dataset.tab){S.tab=t.dataset.tab;S.scene=SCENES[S.tab][0].id;SCENES[S.tab][0].set();return render()}
  if(t.dataset.step){const l=SCENES[S.tab],i=l.findIndex(s=>s.id===S.scene),n=l[(i+ +t.dataset.step+l.length)%l.length];S.scene=n.id;n.set();return render()}
  if(t.dataset.g){S.edit.gender=t.dataset.g;return render()}
  if(t.dataset.open){S.lib={...S.lib,view:'read',id:t.dataset.open,prev:S.lib.view==='read'?S.lib.prev:{...S.lib}};return render()}
  if(t.dataset.topic){S.lib={view:'topic',topic:t.dataset.topic,q:'',id:null,fromLink:false};return render()}
  if(t.dataset.sugg){S.lib.q=t.dataset.sugg;S.focus=true;return render()}
  const a=t.dataset.act;
  if(a==='close'){if(ev.target.closest('[data-stop]')&&t.classList.contains('veil'))return;S.sheet=null;S.err=null;return render()}
  if(a==='edit'){openEdit();return render()}
  if(a==='photo'){S.sheet='photo';return render()}
  if(a==='pick'){S.profile={...S.profile,photo:true};S.sheet=null;return render()}
  if(a==='remove'){S.profile={...S.profile,photo:false};S.sheet=null;return render()}
  if(a==='save')return save();
  if(a==='clear'){S.lib.q='';S.focus=true;return render()}
  if(a==='back'){const L=S.lib;S.lib=L.view==='read'&&L.prev?{...L.prev,prev:null}:{view:'home',q:'',topic:null,id:null,fromLink:false};return render()}
  if(a==='library'){lib({view:'home'});return render()}
});
document.addEventListener('input',ev=>{
  if(ev.target.id==='q'){S.lib.q=ev.target.value;S.focus=true;render()}
  if(ev.target.dataset.f){S.edit[ev.target.dataset.f]=ev.target.value}
});
$('#scene-select').addEventListener('change',e=>{S.scene=e.target.value;SCENES[S.tab].find(s=>s.id===S.scene).set();render()});
$('#size-select').addEventListener('change',e=>{S.size=e.target.value.split(',').map(Number);render()});
$('#font-select').addEventListener('change',e=>{S.fs=+e.target.value;render()});
(()=>{const u=new URLSearchParams(location.search);if(u.get('tab')in SCENES)S.tab=u.get('tab');S.scene=SCENES[S.tab][0].id;
  const sc=SCENES[S.tab].find(s=>s.id===u.get('scene'));if(sc)S.scene=sc.id;
  if(u.get('size')){S.size=u.get('size').split('x').map(Number);$('#size-select').value=S.size.join(',')}
  if(u.get('fs')){S.fs=+u.get('fs');$('#font-select').value=u.get('fs')}
  SCENES[S.tab].find(s=>s.id===S.scene).set();render()})();
