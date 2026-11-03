/* ================= AJUSTES · SOLO LO ESTRICTAMENTE NECESARIO (punto 22) ================= */
// Un único motor de pantalla: cada escena es un estado inicial del mismo mini-teléfono, así que TODAS las escenas se pueden recorrer
// (abrir subpantallas, hojas, permisos, copias, borrar…) igual que en la app. El texto sale del código real; lo que es propuesta va en las notas.
Object.assign(P,{
 shield:'<path d="M12 3.5l7 2.5v5.5c0 4.2-2.9 7.6-7 9-4.1-1.4-7-4.8-7-9V6z"/><path d="M9 12l2.2 2.2L15.5 10"/>',
 bt:'<path d="M7 7.5l10 9-5 4v-17l5 4-10 9"/>',
 folder:'<path d="M3.5 7a2 2 0 012-2h4l2 2.5h7a2 2 0 012 2V17a2 2 0 01-2 2h-13a2 2 0 01-2-2z"/>',
 db:'<ellipse cx="12" cy="6.5" rx="7" ry="2.8"/><path d="M5 6.5v11c0 1.5 3.1 2.8 7 2.8s7-1.3 7-2.8v-11M5 12c0 1.5 3.1 2.8 7 2.8s7-1.3 7-2.8"/>',
 table:'<rect x="4" y="5" width="16" height="14" rx="2.5"/><path d="M4 10h16M4 14.5h16M10 5v14"/>',
 image:'<rect x="4" y="5" width="16" height="14" rx="3"/><circle cx="9" cy="10" r="1.6"/><path d="M5 17l4.5-4.5 3 3 2.5-2.5 4 4"/>',
 lifebuoy:'<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="3.4"/><path d="M6 6l3.6 3.6M18 6l-3.6 3.6M6 18l3.6-3.6M18 18l-3.6-3.6"/>',
 download:'<path d="M12 4v11M7.5 10.5L12 15l4.5-4.5M5 15v3a2 2 0 002 2h10a2 2 0 002-2v-3"/>',
 pulse:'<path d="M3 12h4l2-5 4 10 2-5h6"/>',
 warn:'<path d="M12 4l9 15.5H3z"/><path d="M12 10v4M12 17v.01"/>',
 okc:'<circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.7 2.7L16 10"/>',
 moon:'<path d="M19 14.5A7.5 7.5 0 019.5 5a7.5 7.5 0 109.5 9.5z"/>',
 fork:'<path d="M7 3.5v7a2 2 0 002 2v8M11 3.5v7a2 2 0 01-2 2M17 3.5c-2 1.5-2.5 4-2.5 6.5H17v10.5"/>',
 gym:'<path d="M3 9v6M6 7v10M18 7v10M21 9v6M6 12h12"/>',
 xmark:'<path d="M7 7l10 10M17 7L7 17"/>',
 lock:'<rect x="5" y="10.5" width="14" height="9.5" rx="3"/><path d="M8.5 10.5V8a3.5 3.5 0 017 0v2.5"/>'
});

const AJ_PERMS=[
 {k:'notif',ic:'bell',t:'Notificaciones',d:'Recordatorios, avisos de descanso y estado de la sesión.',q:'te envíe notificaciones'},
 {k:'mic',ic:'mic',t:'Micrófono',d:'Control por voz durante el entrenamiento y dictado de comidas.',q:'grabe audio'},
 {k:'loc',ic:'pin',t:'Ubicación',d:'Rastreo GPS para sesiones de cardio.',q:'acceda a la ubicación de este dispositivo'},
 {k:'bt',ic:'bt',t:'Bluetooth',d:'Conexión con audífonos y accesorios compatibles.',q:'encuentre dispositivos cercanos y se conecte a ellos'},
 {k:'cam',ic:'camera',t:'Cámara',d:'Fotos de ejercicios, técnica en vídeo y escáner de códigos.',q:'haga fotos y grabe vídeo'},
 {k:'alarm',ic:'clock',t:'Alarmas y batería',d:'Permite alarmas precisas y evita que el sistema detenga los avisos.',sys:true}
];
const AJ_TIMES={w:'Entrenar',b:'Desayuno',l:'Almuerzo',d:'Cena',s:'Dormir'};
const AJ_NOTICES=['Fuentes de KPKN Fit','Open Food Facts · ODbL 1.0','Contenidos de Open Food Facts · DbCL 1.0','USDA FoodData Central · CC0 1.0','ANSES Ciqual 2025 · Licence Ouverte 2.0'];
const AJ_NOTICE_TXT=`Fuentes de los datos de nutrición de KPKN Fit

El catálogo de alimentos combina datos abiertos y los adapta: nombres y alias en español, porciones caseras, conceptos, unidades normalizadas (sodio en mg, etc.), «sin dato» distinto de cero y fichas derivadas de platos por componentes. Ninguna organización de origen avala esta adaptación ni la precisión de sus resultados.

- U.S. Department of Agriculture, Agricultural Research Service. FoodData Central: FNDDS, SR Legacy y Foundation Foods. Dominio público (CC0 1.0).
- ANSES. Table de composition nutritionnelle des aliments Ciqual 2025. Licence Ouverte / Open Licence 2.0 (Etalab).
- Open Food Facts contributors: productos vendidos en Chile. Open Database License (ODbL 1.0); contenidos bajo Database Contents License (DbCL 1.0).`;

const ajInit=(o={})=>Object.assign({route:'main',stack:[],sheet:null,anim:null,unit:'kg',health:true,nutrition:true,
 w:{on:false,t:'18:00'},s:{on:false,t:'22:00'},m:{on:false,b:'08:00',l:'13:00',d:'20:00'},
 perm:{notif:'granted',mic:'granted',loc:'denied',bt:'granted',cam:'granted',alarm:'denied'},denials:{},dlg:null,toast:null,
 sv:{kind:'json',busy:false,fail:false},rs:{kind:'json',step:'pick',busy:false},
 csv:{sel:['MEALS'],busy:false,err:null,fail:false},src:null,mirror:null,time:null,deleting:false},o);
const ajPending=S=>AJ_PERMS.filter(p=>S.perm[p.k]!=='granted').length;
const ajActive=S=>[S.w.on,S.m.on&&S.nutrition,S.s.on].filter(Boolean).length;

/* ---------- piezas ---------- */
const ajTop=()=>`<div class="topbar"><button class="fb glass aj-back" data-a="back" aria-label="Atrás">${ic('back')}</button></div>`;
const ajRow=o=>`<button class="aj-row ${o.cls||''}" data-a="${o.a}"><span class="aj-ic">${ic(o.ic,20)}</span><span class="aj-tx"><strong>${o.t}</strong>${o.s?`<span>${o.s}</span>`:''}</span>${o.v?`<span class="aj-val ${o.vc||''}">${o.vi?'<i></i>':''}${o.v}</span>`:''}${o.end===undefined?ic('fwd',16):o.end}</button>`;
const ajSw=on=>`<span class="aj-sw ${on?'aj-on':''}" aria-hidden="true"><i></i></span>`;
const ajOpt=(a,on,t,s,kind='rad')=>`<button class="aj-opt ${on?'aj-on':''}" data-a="${a}" role="${kind==='rad'?'radio':'checkbox'}" aria-checked="${!!on}"><span class="aj-tx"><strong>${t}</strong>${s?`<span>${s}</span>`:''}</span>${kind==='rad'?'<span class="aj-rad"><i></i></span>':`<span class="aj-chk">${ic('check',14)}</span>`}</button>`;
const ajSheet=(S,inner,cls='')=>`<div class="pl-scrim ${S.anim==='sheet'?'aj-in-scrim':''}" data-a="close"></div><div class="pl-sheet aj-sheet ${cls} ${S.anim==='sheet'?'aj-in-sheet':''}" role="dialog" aria-modal="true">${inner}</div>`;
const ajTime=(v,a)=>`<button class="aj-time" data-a="${a}" aria-label="Cambiar hora, ahora ${v}">${v}</button>`;

/* ---------- pantallas ---------- */
function ajMain(S){
 const pend=ajPending(S),act=ajActive(S);
 return `<div class="scroll aj-page">${ajTop()}<h1 class="aj-h1">Ajustes</h1>
 <div class="aj-grp">Preferencias</div><div class="aj-list">
  ${ajRow({ic:'ruler',t:'Unidades',a:'open:units',v:S.unit})}
  ${ajRow({ic:'bell',t:'Recordatorios',a:'go:rem',v:act?`${act} ${act===1?'activo':'activos'}`:'Sin activar'})}
 </div>
 <div class="aj-grp">Sistema</div><div class="aj-list">
  ${ajRow({ic:'shield',t:'Permisos',a:'go:perm',v:pend?`${pend} por activar`:'Todo concedido',vc:pend?'aj-wn':'aj-ok',vi:!!pend})}
  ${S.health?ajRow({ic:'heart',t:'Health Connect',a:'hc'}):''}
 </div>
 <div class="aj-grp">Datos</div><div class="aj-list">
  ${ajRow({ic:'folder',t:'Copia y exportación',a:'go:copy'})}
 </div>
 <div class="aj-grp">KPKN</div><div class="aj-list">
  ${ajRow({ic:'lifebuoy',t:'Ayuda y acerca de',a:'go:help'})}
 </div>
 <div class="aj-danger"><div class="aj-list">${ajRow({ic:'trash',t:'Borrar todos los datos',s:'Borra tu progreso, rutinas, nutrición, foto y configuraciones de la app.',a:'open:delete',cls:'aj-bad',end:''})}</div></div>
 </div>`;
}
function ajRem(S){
 const notif=S.perm.notif,alarm=S.perm.alarm;
 let warn='';
 if(notif!=='granted'){
  const blocked=notif==='blocked';
  warn=`<div class="aj-warn ${blocked?'aj-bd':''}"><div class="aj-wr">${ic('warn',22)}<p><b>Las notificaciones están desactivadas.</b> ${blocked?'El sistema ya no pregunta: actívalas en los ajustes del sistema.':'Sin ellas, los recordatorios no llegan.'}</p></div><button class="aj-btn aj-sm ${blocked?'aj-gh':''}" data-a="req:notif">${blocked?'Abrir ajustes del sistema':'Activar notificaciones'}</button></div>`;
 }else if(alarm!=='granted'&&ajActive(S)>0){
  warn=`<div class="aj-warn"><div class="aj-wr">${ic('clock',22)}<p><b>Pueden llegar con retraso.</b> Sin el permiso de alarmas precisas, el sistema decide la hora exacta del aviso.</p></div><button class="aj-btn aj-sm aj-gh" data-a="req:alarm">Abrir ajustes del sistema</button></div>`;
 }
 const head=(k,icn,t,s,on)=>`<button class="aj-mh" data-a="tg:${k}" role="switch" aria-checked="${on}"><span class="aj-ic">${ic(icn,20)}</span><span class="aj-tx"><strong>${t}</strong><span>${s}</span></span>${ajSw(on)}</button>`;
 const line=(lbl,v,a)=>`<div class="aj-sub"><span>${lbl}</span>${ajTime(v,a)}</div>`;
 return `<div class="scroll aj-page">${ajTop()}<h1 class="aj-h1">Recordatorios</h1>${warn}
 <div class="aj-mod">${head('w','gym','Entrenar','Un aviso al día',S.w.on)}${S.w.on?`${line('Hora',S.w.t,'time:w')}<div class="aj-note">Recibirás: <q>¿Ya está todo listo para entrenar?</q></div>`:''}</div>
 ${S.nutrition?`<div class="aj-mod">${head('m','fork','Comidas','Para registrar lo que comes',S.m.on)}${S.m.on?`${line('Desayuno',S.m.b,'time:b')}${line('Almuerzo',S.m.l,'time:l')}${line('Cena',S.m.d,'time:d')}`:''}</div>`:''}
 <div class="aj-mod">${head('s','moon','Dormir','Un aviso para prepararte',S.s.on)}${S.s.on?`${line('Hora',S.s.t,'time:s')}<div class="aj-note">Recibirás: <q>Es hora de prepararse para descansar</q></div>`:''}</div>
 </div>`;
}
function ajPerm(S){
 const anyBlocked=AJ_PERMS.some(p=>S.perm[p.k]==='blocked');
 const rows=AJ_PERMS.map(p=>{
  const st=S.perm[p.k];let end,sub='';
  if(st==='granted')end=`<span class="aj-st">${ic('okc',18)}Concedido</span>`;
  else if(st==='blocked'||p.sys){end=`<span class="aj-btn aj-sm aj-gh">Abrir ajustes</span>`;if(st==='blocked')sub=`<span class="aj-bl">Bloqueado: el sistema ya no pregunta.</span>`}
  else end=`<span class="aj-btn aj-sm">Activar</span>`;
  return `<button class="aj-prow" data-a="req:${p.k}"><span class="aj-ic">${ic(p.ic,20)}</span><strong>${p.t}</strong>${end}<span class="aj-pd">${p.d}${sub}</span></button>`;
 }).join('');
 return `<div class="scroll aj-page">${ajTop()}<h1 class="aj-h1">Permisos</h1>
 <p class="aj-lead">KPKN te los pide también cuando hacen falta. Aquí puedes revisarlos o recuperarlos.</p>
 <div class="aj-list" style="margin-top:14px">${rows}</div>
 ${anyBlocked?`<p class="aj-foot">Si el sistema deja de preguntar, se activa desde Ajustes del sistema › Aplicaciones › KPKN Fit › Permisos. Al volver, KPKN lo comprueba solo.</p>`:''}
 </div>`;
}
function ajCopy(S){
 return `<div class="scroll aj-page">${ajTop()}<h1 class="aj-h1">Copia y exportación</h1>
 <div class="aj-grp">Copia de seguridad</div><div class="aj-list">
  ${ajRow({ic:'download',t:'Guardar copia',s:'Rutinas, historial, nutrición y todo lo tuyo, en un archivo que eliges.',a:'open:save'})}
  ${ajRow({ic:'history',t:'Restaurar copia',s:'Recupera una copia guardada antes.',a:'open:restore'})}
 </div>
 <div class="aj-grp">Exportar</div><div class="aj-list">
  ${ajRow({ic:'table',t:'Hoja de cálculo (CSV)',s:'Comidas, cuerpo, entrenos o tu biblioteca.',a:'open:csv'})}
  ${ajRow({ic:'image',t:'Fotos y vídeos (ZIP)',s:'Los álbumes privados de entrenamiento.',a:'zip'})}
 </div>
 </div>`;
}
function ajHelp(S){
 return `<div class="scroll aj-page">${ajTop()}<h1 class="aj-h1">Ayuda y acerca de</h1>
 <p class="aj-lead">KPKN Pre Release 21 · versión 55</p>
 <div class="aj-list" style="margin-top:18px">
  ${ajRow({ic:'repeat',t:'Volver a ver los recorridos',s:'Las explicaciones de cada pantalla vuelven la próxima vez que entres.',a:'tours',end:''})}
  ${ajRow({ic:'book',t:'Fuentes y licencias',s:'De dónde salen los datos de alimentos y bajo qué licencias se usan.',a:'open:src'})}
  ${ajRow({ic:'pulse',t:'Diagnóstico',s:'Registros técnicos para investigar un fallo.',a:'go:diag',v:S.mirror?'Con carpeta':''})}
 </div>
 </div>`;
}
function ajDiag(S){
 return `<div class="scroll aj-page">${ajTop()}<h1 class="aj-h1">Diagnóstico</h1>
 <p class="aj-lead">KPKN guarda en tu móvil un registro técnico de cada área (entreno, voz, nutrición y app), un archivo por día. Exportarlo sirve para investigar un fallo.</p>
 <div class="aj-list" style="margin-top:14px">${ajRow({ic:'upload',t:'Exportar registros (ZIP)',s:'Todo el registro, en un archivo que eliges.',a:'diagzip',end:''})}</div>
 <div class="aj-grp">Copia en una carpeta</div>
 <div class="aj-mod" style="margin-top:6px;padding:14px 16px;display:flex;flex-direction:column;gap:12px">
  <div style="display:flex;gap:12px;align-items:center"><span class="aj-ic">${ic('folder',20)}</span><span class="aj-tx"><strong style="font-size:calc(15px*var(--fs))">${S.mirror?'Documentos › KPKN':'Sin carpeta externa'}</strong><span>${S.mirror?'Cada registro se copia también aquí.':'Opcional: guarda una segunda copia donde tú quieras.'}</span></span></div>
  <div style="display:flex;gap:8px;flex-wrap:wrap">${S.mirror?`<button class="aj-btn aj-sm aj-gh" data-a="mirror-sync">Sincronizar ahora</button><button class="aj-btn aj-sm aj-ln" data-a="mirror-off">Desvincular</button>`:`<button class="aj-btn aj-sm" data-a="mirror-on">Elegir carpeta</button>`}</div>
 </div></div>`;
}

/* ---------- hojas ---------- */
function ajSheetBody(S){
 const k=S.sheet;
 if(k==='units')return ajSheet(S,`<h3 class="aj-sh-t">Unidades de peso</h3><p class="aj-sh-s">Cómo ves tu peso corporal, tus marcas y lo que exportas. Tus datos siguen guardados en kg.</p>
  <div>${ajOpt('unit:kg',S.unit==='kg','Kilogramos','kg')}${ajOpt('unit:lb',S.unit==='lb','Libras','lb')}</div>`);
 if(k==='time'){
  const t=S.time,hh=n=>String((n+24)%24).padStart(2,'0'),mm=n=>String(((n%60)+60)%60).padStart(2,'0');
  const col=(c,f)=>`<div class="aj-dcol"><button class="aj-dc" data-a="${c}-" aria-label="Menos">${ic('up',20)}</button><button class="aj-dn aj-far" data-a="${c}-" tabindex="-1">${f(-1)}</button><div class="aj-dn aj-cur" aria-live="polite">${f(0)}</div><button class="aj-dn aj-far" data-a="${c}+" tabindex="-1">${f(1)}</button><button class="aj-dc" data-a="${c}+" aria-label="Más">${ic('down',20)}</button></div>`;
  return ajSheet(S,`<h3 class="aj-sh-t">${AJ_TIMES[t.key]}</h3><p class="aj-sh-s">Hora del aviso, cada día.</p>
   <div class="aj-drum">${col('h',d=>hh(t.h+d))}<span class="aj-dsep">:</span>${col('m',d=>mm(t.m+d*5))}</div>
   <button class="aj-btn aj-fl" data-a="time-ok">Listo</button>`);
 }
 if(k==='save'){
  const v=S.sv;
  return ajSheet(S,`<h3 class="aj-sh-t">Guardar copia</h3><p class="aj-sh-s">Después eliges dónde guardar el archivo.</p>
   <div>${ajOpt('sv:json',v.kind==='json','Copia estándar','Rutinas, historial, nutrición, competiciones y metadatos de medios. No incluye el catálogo de alimentos ni WikiLab: se regeneran.')}${ajOpt('sv:db',v.kind==='db','Copia completa','Toda la base de datos, con el catálogo de alimentos y WikiLab. Recomendada para cambiar de móvil.')}</div>
   <button class="aj-btn aj-fl" data-a="sv-go" ${v.busy?'disabled':''}>${v.busy?'<span class="aj-spin"></span>Guardando…':'Guardar'}</button>`);
 }
 if(k==='restore'){
  const r=S.rs;
  if(r.step==='pick')return ajSheet(S,`<h3 class="aj-sh-t">Restaurar copia</h3><p class="aj-sh-s">¿Qué tipo de archivo vas a restaurar?</p>
   <div>${ajOpt('rs:json',r.kind==='json','Copia estándar','Archivo .json guardado con «Copia estándar».')}${ajOpt('rs:db',r.kind==='db','Copia completa','Archivo .db guardado con «Copia completa».')}</div>
   <button class="aj-btn aj-fl" data-a="rs-pick">Elegir archivo</button>`);
  const json=r.kind==='json';
  return ajSheet(S,`<h3 class="aj-sh-t">¿Restaurar esta copia?</h3>
   <div class="aj-file"><span class="aj-ic">${ic(json?'folder':'db',20)}</span><div><b>${json?'kpkn-backup.json':'kpkn-backup.db'}</b><span>${json?'Copia estándar':'Copia completa'}</span></div></div>
   <p class="aj-sh-s">${json?'Se restaurarán rutinas, historial, nutrición y observaciones originales según el archivo. Si es antiguo, competiciones y datos de contexto no listados se mantienen.':'Se reemplazan todos los datos de este móvil por los del archivo.'} Antes se guarda una copia de rescate por si algo falla.</p>
   <button class="aj-btn aj-fl" data-a="rs-go" ${r.busy?'disabled':''}>${r.busy?'<span class="aj-spin"></span>Restaurando…':'Restaurar'}</button>
   <button class="aj-txt" data-a="close" ${r.busy?'disabled':''}>Cancelar</button>`);
 }
 if(k==='csv'){
  const c=S.csv,doms=[['MEALS','Comidas','Alimentos registrados, hora y estado del día'],['BODY','Cuerpo','Mediciones y unidades de Ajustes'],['WORKOUTS','Entrenos','Sesiones, series, repeticiones y notas'],['LIBRARY','Mi biblioteca','Ejercicios, alimentos y recetas propios']];
  return ajSheet(S,`<div class="aj-sh-bar"><h3 class="aj-sh-t">Exportar datos</h3>${c.busy?'':`<button class="aj-x" data-a="close" aria-label="Cerrar exportación CSV">${ic('close',20)}</button>`}</div>
   <div class="aj-sh-body"><p class="aj-sh-s">Elige los datos que quieres llevar a una hoja de cálculo. Después elegirás dónde guardarlos.</p>
   <div>${doms.map(d=>ajOpt('csv:'+d[0],c.sel.includes(d[0]),d[1],d[2],'chk').replace('class="aj-opt','class="aj-opt'+(c.busy?' dis':'')).replace('<button','<button '+(c.busy?'disabled':''))).join('')}</div>
   <p class="aj-note" style="padding:0">Una selección produce un CSV; varias se agrupan en ZIP. Mi biblioteca incluye dos CSV y siempre se entrega en ZIP.</p></div>
   ${c.err?`<div class="aj-err">${c.err}</div>`:''}
   <button class="aj-btn aj-fl" data-a="csv-go" ${(!c.sel.length||c.busy)?'disabled':''}>${c.busy?'<span class="aj-spin"></span>Preparando…':'Exportar'}</button>`,'aj-tall');
 }
 if(k==='src'){
  const open=S.src;
  if(open===null)return ajSheet(S,`<h3 class="aj-sh-t">Fuentes y licencias</h3><p class="aj-sh-s">Los datos de alimentos de KPKN Fit vienen de estas fuentes abiertas. Toca una para leer su aviso.</p>
   <div>${AJ_NOTICES.map((n,i)=>`<button class="aj-opt" data-a="src:${i}"><span class="aj-tx"><strong>${n}</strong></span>${ic('fwd',16)}</button>`).join('')}</div>`,'aj-tall');
  return ajSheet(S,`<div class="aj-sh-bar"><h3 class="aj-sh-t">${AJ_NOTICES[open]}</h3></div>
   <div class="aj-sh-body">${open===0?`<pre class="aj-mono">${AJ_NOTICE_TXT}</pre>`:`<p class="aj-sh-s">El texto completo de la licencia, tal como viene dentro de la app.</p><div class="aj-skel">${Array.from({length:14},(_,i)=>`<i style="width:${[100,96,92,100,88,97,60,100,94,91,99,72,95,40][i]}%"></i>`).join('')}</div>`}</div>
   <button class="aj-btn aj-gh aj-fl" data-a="src:back">Volver</button>`,'aj-tall');
 }
 if(k==='delete'){
  if(S.deleting)return ajSheet(S,`<h3 class="aj-sh-t">Borrando…</h3><p class="aj-sh-s">No cierres KPKN.</p><div style="display:flex;justify-content:center;padding:26px 0"><span class="aj-spin" style="width:30px;height:30px"></span></div>`);
  return ajSheet(S,`<h3 class="aj-sh-t">¿Borrar todos los datos?</h3><p class="aj-sh-s">Es irreversible. KPKN queda como recién instalada.</p>
   <ul class="aj-lines">${['Rutinas, programas e historial','Nutrición y planes','Progreso, mediciones y foto','Configuración y recordatorios'].map(t=>`<li>${ic('xmark',18)}${t}</li>`).join('')}</ul>
   <ul class="aj-lines aj-keep"><li>${ic('check',18)}No se toca nada de lo que guardaste fuera de KPKN, como tus copias.</li></ul>
   <button class="aj-btn aj-gh aj-sm" data-a="open:save" style="align-self:flex-start">${ic('download',16)}Guardar una copia antes</button>
   <button class="aj-btn aj-bd aj-hold" data-hold data-a="del-go" aria-label="Mantén pulsado para borrar todo"><b></b><span class="aj-hl">Mantén pulsado para borrar</span></button>
   <button class="aj-txt" data-a="close">Cancelar</button>`);
 }
 return '';
}
function ajDialog(S){
 if(!S.dlg)return '';
 const p=AJ_PERMS.find(x=>x.k===S.dlg);
 return `<div class="aj-sysbg ${S.anim==='sheet'?'aj-in-scrim':''}"><div class="aj-sys" role="alertdialog"><span class="aj-sic">${ic(p.ic,30)}</span><p>¿Permitir que KPKN Fit ${p.q}?</p><button data-a="dlg:yes">Permitir</button><button data-a="dlg:no">No permitir</button><small>Diálogo del sistema</small></div></div>`;
}
function ajToast(S){
 if(!S.toast)return '';
 const t=S.toast;
 return `<div class="aj-toast glass ${t.kind?(t.kind==='bad'?'aj-bd':'aj-ok'):''} ${S.toastNew?'aj-in-toast':''}" role="status">${t.kind?ic(t.kind==='bad'?'warn':'okc',20):''}<span>${t.msg}</span></div>`;
}
const ajScreens={main:ajMain,rem:ajRem,perm:ajPerm,copy:ajCopy,help:ajHelp,diag:ajDiag};
const ajCls=S=>'aj-root'+(S.anim==='push'?' aj-in-push':S.anim==='pop'?' aj-in-pop':'');
const ajInner=S=>ajScreens[S.route](S)+ajSheetBody(S)+ajDialog(S)+ajToast(S);
const ajView=S=>`<div class="${ajCls(S)}">${ajInner(S)}</div>`;

/* ---------- comportamiento ---------- */
function ajWire(phone,mk){
 const root=phone.querySelector('.aj-root');if(!root)return;
 const S=mk();let tid=0,hold=null;
 const flash=(msg,kind)=>{S.toast={msg,kind};S.toastNew=true;clearTimeout(tid);tid=setTimeout(()=>{S.toast=null;draw()},2900)};
 const later=(ms,fn)=>setTimeout(()=>{fn();draw()},ms);
 const go=r=>{S.stack.push(S.route);S.route=r;S.anim='push'};
 const open=k=>{S.sheet=k;S.anim='sheet';if(k==='src')S.src=null;if(k==='restore')S.rs={kind:'json',step:'pick',busy:false};if(k==='save')S.sv.busy=false};
 const grant=k=>{S.perm[k]='granted'};
 const draw=()=>{
  const sc=root.querySelector('.scroll'),y=sc?sc.scrollTop:0,pushed=S.anim==='push'||S.anim==='pop';
  root.className=ajCls(S);root.innerHTML=ajInner(S);
  const n=root.querySelector('.scroll');if(n&&!pushed)n.scrollTop=y;
  S.anim=null;S.toastNew=false;
 };
 const act=(a,el)=>{
  const [k,v]=a.split(':');
  switch(k){
   case 'back':
    if(S.dlg){S.dlg=null;break}
    if(S.sheet){S.sheet=null;break}
    if(S.stack.length){S.route=S.stack.pop();S.anim='pop'}
    break;
   case 'close':if(S.deleting)break;S.sheet=null;break;
   case 'go':go(v);break;
   case 'open':open(v);break;
   case 'hc':flash('Se abre la pantalla de Health Connect, que ya existe.');break;
   case 'unit':S.unit=v;later(280,()=>{S.sheet=null});break;
   case 'tg':{const o=S[v];o.on=!o.on;break}
   case 'time':{const t=v==='w'?S.w.t:v==='s'?S.s.t:S.m[v];const [h,m]=t.split(':').map(Number);S.time={key:v,h,m};open('time');break}
   case 'h-':S.time.h=(S.time.h+23)%24;break;
   case 'h+':S.time.h=(S.time.h+1)%24;break;
   case 'm-':S.time.m=(S.time.m+55)%60;break;
   case 'm+':S.time.m=(S.time.m+5)%60;break;
   case 'time-ok':{const t=String(S.time.h).padStart(2,'0')+':'+String(S.time.m).padStart(2,'0'),key=S.time.key;if(key==='w')S.w.t=t;else if(key==='s')S.s.t=t;else S.m[key]=t;S.sheet=null;break}
   case 'req':{
    const p=AJ_PERMS.find(x=>x.k===v),st=S.perm[v];
    if(st==='granted')break;
    if(p.sys||st==='blocked'){flash('Ajustes del sistema › KPKN Fit › Permisos');later(2600,()=>grant(v))}
    else{S.dlg=v;S.anim='sheet'}
    break}
   case 'dlg':{const kk=S.dlg;S.dlg=null;if(v==='yes')grant(kk);else{S.denials[kk]=(S.denials[kk]||0)+1;S.perm[kk]=S.denials[kk]>=2?'blocked':'denied'}break}
   case 'sv':S.sv.kind=v;break;
   case 'sv-go':{S.sv.busy=true;const db=S.sv.kind==='db';later(1300,()=>{S.sv.busy=false;S.sheet=null;if(S.sv.fail)flash('No se pudo guardar: no se pudo abrir el archivo de destino.','bad');else flash(db?'Copia completa guardada.':'Copia guardada.','ok')});break}
   case 'rs':S.rs.kind=v;break;
   case 'rs-pick':S.rs.step='confirm';break;
   case 'rs-go':{S.rs.busy=true;const db=S.rs.kind==='db';later(1500,()=>{S.rs.busy=false;S.sheet=null;flash(db?'Base de datos restaurada.':'Copia restaurada.','ok')});break}
   case 'csv':{const c=S.csv,i=c.sel.indexOf(v);if(i>=0)c.sel.splice(i,1);else c.sel.push(v);c.err=null;break}
   case 'csv-go':{const c=S.csv;c.busy=true;c.err=null;later(1400,()=>{c.busy=false;if(c.fail){c.err='No se pudo preparar la exportación: error desconocido'}else{S.sheet=null;const n=c.sel.reduce((a,d)=>a+(d==='LIBRARY'?2:1),0);flash(`Exportación completada (${n} ${n===1?'archivo':'archivos'}).`,'ok')}});break}
   case 'zip':flash('Se abre el selector de archivos para guardar kpkn-workout-media.zip.');break;
   case 'tours':flash('Recorridos restablecidos.','ok');break;
   case 'src':if(v==='back')S.src=null;else S.src=+v;break;
   case 'diagzip':flash('Se abre el selector de archivos para guardar el ZIP de registros.');break;
   case 'mirror-on':S.mirror='Documentos › KPKN';flash('Carpeta configurada: Documentos › KPKN.','ok');break;
   case 'mirror-sync':flash('Sincronización iniciada.');break;
   case 'mirror-off':S.mirror=null;break;
   case 'del-go':{S.deleting=true;later(1600,()=>{Object.assign(S,ajInit({anim:null}),{toast:null});S.stack=[];S.route='main';S.sheet=null;flash('La app volvió a cero.','ok')});break}
  }
  draw();
 };
 root.addEventListener('click',e=>{
  const t=e.target.closest('[data-a]');if(!t||t.disabled)return;
  if(t.hasAttribute('data-hold')&&e.detail!==0)return;
  act(t.dataset.a,t);
 });
 root.addEventListener('pointerdown',e=>{
  const t=e.target.closest('[data-hold]');if(!t)return;
  t.classList.add('aj-holding');const l=t.querySelector('.aj-hl');if(l)l.textContent='Sigue pulsando…';
  hold=setTimeout(()=>{hold=null;act(t.dataset.a,t)},1200);
 });
 const stop=e=>{if(e.type==='pointerleave'&&!(e.target.hasAttribute&&e.target.hasAttribute('data-hold')))return;if(!hold)return;clearTimeout(hold);hold=null;const t=root.querySelector('[data-hold]');if(t){t.classList.remove('aj-holding');const l=t.querySelector('.aj-hl');if(l)l.textContent='Mantén pulsado para borrar'}};
 ['pointerup','pointerleave','pointercancel'].forEach(ev=>root.addEventListener(ev,stop,true));
}
function ajScene(def){
 scene({tab:'ajustes',id:def.id,title:def.title,notes:def.notes,
  html:()=>ajView(ajInit(def.init?def.init():{})),
  after:ph=>ajWire(ph,()=>ajInit(def.init?def.init():{}))});
}

/* ================= ESCENAS ================= */
ajScene({id:'aj-main',title:'Ajustes · pantalla principal',
 notes:{tags:[['new','rediseño completo'],['out','de 10 bloques a 6 filas'],['ask','Recordatorios y Unidades, con motor y sin pantalla hoy']],
  p:['Hoy Ajustes es una lista larga de 10 bloques con tarjetas de colores de Material, textos largos y una tarjeta roja de telemetría. Aquí queda <b>una pantalla corta de seis filas</b>, sobre el negro continuo, con el título grande de Syne, un icono de línea por fila y el valor a la derecha. Sin tarjetas, sin textos de relleno.',
     'Todo se puede tocar: <b>Unidades</b> abre su hoja, el resto abre su subpantalla y «Borrar todos los datos» abre la confirmación. El atrás cierra primero la hoja y después la subpantalla.',
     'Lo que ya no está aquí: descanso por defecto, iniciar descanso al registrar, Mi material, la tarjeta de bienvenida y la telemetría a la vista. Cada uno con su razón en la escena <b>Veredictos</b> (la última de la lista).'],
  list:['<b>Unidades</b> y <b>Recordatorios</b>: los dos únicos ajustes que de verdad son globales y hoy no tienen pantalla (ver Veredictos).','<b>Permisos</b> dice cuántos faltan en ámbar; con todo concedido dice «Todo concedido» en verde.','<b>Health Connect</b> solo aparece en la versión con Health Connect (flavor <code>health</code>); en la base la fila no existe.','<b>Borrar todos los datos</b> queda al final, separado y en rojo suave.'],listTitle:'Detalle'}});

ajScene({id:'aj-unidades',title:'Unidades · hoja de elección',init:()=>({sheet:'units'}),
 notes:{tags:[['new','pantalla para un ajuste que ya manda en 14 carpetas'],['ask','kg o lb para tu peso y tus marcas']],
  p:['<code>Settings.weightUnit</code> (kg/lb) ya decide cómo ves el peso corporal (Perfil, Cuerpo), las marcas del programa (1RM / TM) y las exportaciones, pero <b>solo se podía elegir en el asistente de bienvenida</b>. La hoja de exportación CSV incluso dice «unidades de Ajustes»… y Ajustes no tenía ninguna.',
     'Dos opciones con su sigla, sin más. Al elegir, el valor aparece en la fila y la hoja se cierra sola a los 280 ms. Los datos siguen guardados en kg: es solo cómo se muestran (lo dice el propio código de Cuerpo y del inventario).']}});

ajScene({id:'aj-recordatorios',title:'Recordatorios · activos',init:()=>({route:'rem',stack:['main'],w:{on:true,t:'18:00'},m:{on:true,b:'08:00',l:'13:00',d:'20:00'},s:{on:false,t:'22:00'},perm:{notif:'granted',mic:'granted',loc:'granted',bt:'granted',cam:'granted',alarm:'granted'}}),
 notes:{tags:[['new','el motor existe, la pantalla no'],['ask','¿los quieres?']],
  p:['El motor de recordatorios está completo —aviso diario de entrenar, tres de comidas y uno de dormir, con su receptor de arranque y textos reales—, pero <b>nadie puede activarlo</b>: el campo existe en <code>Settings</code> y no tiene ninguna pantalla.',
     'Tres módulos con interruptor (el de la app, 52 × 32). Al encender uno aparece su hora y el texto exacto que recibirás. «Comidas» no aparece si saltaste Nutrición, igual que en el motor.','Toca una hora para cambiarla; los interruptores funcionan.']}});

ajScene({id:'aj-recordatorios-vacio',title:'Recordatorios · todo apagado',init:()=>({route:'rem',stack:['main']}),
 notes:{tags:[['keep','estado por defecto']],
  p:['Estado de fábrica: tres interruptores apagados y nada más. Sin horas ni textos hasta que enciendes uno.']}});

ajScene({id:'aj-hora',title:'Elegir hora · hoja',init:()=>({route:'rem',stack:['main'],w:{on:true,t:'18:00'},sheet:'time',time:{key:'w',h:18,m:0}}),
 notes:{tags:[['new','selector de hora propio']],
  p:['Dos columnas con las cifras tabulares de Inter (Syne altera la altura de los dígitos), de 24 horas y minutos de cinco en cinco, con la elegida grande. Se toca arriba/abajo o el número vecino. «Listo» guarda; tocar fuera descarta.',
     'En Compose se hace con un par de <code>LazyColumn</code> con ajuste a la fila central, o con el <code>TimePicker</code> de Material en una hoja gris plana.']}});

ajScene({id:'aj-recordatorios-alarmas',title:'Recordatorios · sin alarmas precisas',init:()=>({route:'rem',stack:['main'],w:{on:true,t:'18:00'},perm:{notif:'granted',mic:'granted',loc:'granted',bt:'granted',cam:'granted',alarm:'denied'}}),
 notes:{tags:[['new','aviso útil donde se necesita']],
  p:['Notificaciones sí, alarmas precisas no. El motor no se detiene: programa alarmas aproximadas (<code>setInexactRepeating</code>), así que el aviso puede llegar con retraso. Lo decimos y damos el botón; abre los ajustes del sistema.']}});

ajScene({id:'aj-recordatorios-permiso',title:'Recordatorios · sin permiso',init:()=>({route:'rem',stack:['main'],w:{on:true,t:'18:00'},perm:{notif:'denied',mic:'granted',loc:'denied',bt:'granted',cam:'granted',alarm:'granted'}}),
 notes:{tags:[['new','aviso útil donde se necesita'],['keep','mismo permiso que hoy']],
  p:['Si las notificaciones no están concedidas, un aviso arriba lo dice y trae su botón. «Activar notificaciones» abre el diálogo del sistema; si ya lo negaste dos veces, el botón pasa a «Abrir ajustes del sistema».',
     'Toca el botón para recorrer los estados: diálogo del sistema → concedido, o negado dos veces → «Abrir ajustes del sistema».']}});

ajScene({id:'aj-permisos',title:'Permisos · con estados',init:()=>({route:'perm',stack:['main']}),
 notes:{tags:[['keep','las mismas 6 filas de hoy'],['new','estados claros y «Abrir ajustes»']],
  p:['Hoy cada fila dice «Concedido» o «Activar» en el mismo texto y la de Health Connect <b>siempre</b> dice «Activar» (su estado está fijo en falso). Aquí cada fila tiene un estado:',
     '<b>Concedido</b> en verde con su marca · <b>Activar</b> (botón crema) abre el diálogo del sistema · tras negarlo dos veces el sistema deja de preguntar y la fila pasa a <b>Bloqueado: Abrir ajustes</b>. «Alarmas y batería» no es un permiso de ejecución: siempre abre los ajustes del sistema.',
     'Health Connect sale de esta lista: es una conexión, no un permiso, y vive en la pantalla principal. Los textos cambian solo donde el uso real lo pedía: el micrófono también hace el dictado de comidas y la cámara también escanea códigos y graba la técnica.'],
  list:['Al volver de los ajustes del sistema la app relee el estado (ya lo hace hoy con <code>ON_RESUME</code>); aquí se simula a los 2,6 s.','Orden por frecuencia de uso, como hoy.'],listTitle:'Detalle'}});

ajScene({id:'aj-permiso-dialogo',title:'Permisos · diálogo del sistema',init:()=>({route:'perm',stack:['main'],dlg:'mic',perm:{notif:'granted',mic:'denied',loc:'denied',bt:'granted',cam:'blocked',alarm:'denied'}}),
 notes:{tags:[['keep','es el del sistema, no nuestro']],
  p:['Lo que sale al tocar «Activar»: el diálogo estándar de Android. «Permitir» concede; «No permitir» una vez deja la fila en «Activar»; la segunda vez la bloquea.','Esta escena arranca con Cámara ya bloqueada para ver el estado «Abrir ajustes» detrás del diálogo.']}});

ajScene({id:'aj-copia',title:'Copia y exportación',init:()=>({route:'copy',stack:['main']}),
 notes:{tags:[['move','los 7 botones de Datos y Backup, en 4 filas'],['out','JSON y .db se funden']],
  p:['Hoy hay dos secciones («Datos» y «Backup») con siete acciones, cuatro de ellas casi iguales (exportar/importar JSON y exportar/importar .db). Aquí quedan <b>cuatro filas con nombres de persona</b>: Guardar copia, Restaurar copia, Hoja de cálculo (CSV) y Fotos y vídeos (ZIP).',
     'Lo que cambia es la forma, no el motor: «Guardar» y «Restaurar» abren una hoja donde eliges entre la copia estándar (JSON) y la completa (.db). Los cuatro caminos de código de hoy se conservan tal cual.']}});

ajScene({id:'aj-guardar',title:'Guardar copia · hoja',init:()=>({route:'copy',stack:['main'],sheet:'save'}),
 notes:{tags:[['move','Exportar backup JSON + Copia completa .db'],['new','textos en lenguaje de persona']],
  p:['Dos opciones con la explicación que hoy está en el cuerpo de cada tarjeta, pero recortada a lo que importa: qué incluye y cuándo conviene. La estándar sirve para el día a día; la completa, «recomendada para cambiar de móvil» (palabras de hoy).',
     'Tras «Guardar» sale el selector de archivos del sistema; aquí solo se ve «Guardando…» y el aviso de resultado. Las acciones de hoy se llaman igual por debajo (<code>exportData</code> y <code>exportDatabaseSnapshot</code>).']}});

ajScene({id:'aj-guardar-error',title:'Guardar copia · con error',init:()=>({route:'copy',stack:['main'],sheet:'save',sv:{kind:'db',busy:false,fail:true}}),
 notes:{tags:[['keep','mismo error que hoy: «No se pudo exportar: …»']],
  p:['Pulsa «Guardar»: tras unos segundos la hoja se cierra y avisa del fallo con el motivo. El mensaje de hoy es un <i>toast</i> de sistema; aquí el mismo aviso en la cápsula de vidrio de la app, con el icono de aviso.']}});

ajScene({id:'aj-restaurar',title:'Restaurar copia · hoja',init:()=>({route:'copy',stack:['main'],sheet:'restore'}),
 notes:{tags:[['move','Importar JSON + Restaurar .db'],['new','la copia .db ahora pide confirmación']],
  p:['Primero eliges qué archivo vas a restaurar; después el selector del sistema; al volver, una <b>confirmación con el nombre del archivo</b> y lo que va a pasar. Hoy el JSON ya pregunta; <b>la copia .db reemplaza la base sin preguntar</b> (el texto lo dice, pero lo hace al tocar). Es la única acción de toda la pantalla que borra datos sin avisar.',
     'El motor no cambia: sigue creando la copia de rescate antes de restaurar y deshaciéndola si algo falla.'],
  list:['Pulsa «Elegir archivo» para ver el paso de confirmación.'],listTitle:'Prueba'}});

ajScene({id:'aj-csv',title:'Exportar a hoja de cálculo',init:()=>({route:'copy',stack:['main'],sheet:'csv'}),
 notes:{tags:[['keep','la hoja de hoy, casi idéntica'],['new','módulo y hoja gris plana']],
  p:['La hoja <code>CsvExportSheet</code> de hoy ya estaba bien pensada: cuatro dominios con casilla, el aviso de ZIP y el botón «Exportar». Se conserva con sus textos y su regla (una selección → un CSV; varias → ZIP; la biblioteca siempre ZIP).',
     'Cambia la presentación: el cuerpo en un único módulo, la hoja gris plana de la app y «Exportar» desactivado cuando no queda nada marcado. «Cuerpo» dice «unidades de Ajustes»: ahora es verdad.',
     'Corrección menor de texto: «Exportación completada (1 archivos)» pasa a «1 archivo».']}});

ajScene({id:'aj-csv-error',title:'Exportar a CSV · con error',init:()=>({route:'copy',stack:['main'],sheet:'csv',csv:{sel:['MEALS','LIBRARY'],busy:false,err:'No se pudo preparar la exportación: error desconocido',fail:false}}),
 notes:{tags:[['keep','mismo error que hoy']],
  p:['El error aparece sobre el botón, en el rojo suave de la app, y no impide volver a intentarlo. Pulsa «Exportar» para ver «Preparando…» (todo bloqueado, sin cerrar mientras trabaja) y el aviso de éxito con el número de archivos.']}});

ajScene({id:'aj-ayuda',title:'Ayuda y acerca de',init:()=>({route:'help',stack:['main']}),
 notes:{tags:[['move','Recorridos, Fuentes y licencias y Telemetría, juntos'],['new','la versión por fin se ve']],
  p:['Tres cosas que hoy estaban sueltas en tres bloques distintos (Nutrición, Recorridos y la tarjeta roja de Telemetría) pasan a un solo lugar: lo que no es una preferencia sino información o ayuda. La versión de la app —que hoy no se ve en ninguna parte— queda arriba.',
     '«Volver a ver los recorridos» es una acción inmediata con su aviso («Recorridos restablecidos»). Los otros dos abren su propia hoja o pantalla.']}});

ajScene({id:'aj-fuentes',title:'Fuentes y licencias',init:()=>({route:'help',stack:['main'],sheet:'src'}),
 notes:{tags:[['keep','mismos 5 avisos, misma lista'],['move','de Nutrición a Ayuda']],
  p:['Se conserva la obligación de atribución: los mismos cinco avisos y su orden. Cambia el contenedor (hoja gris plana en vez de un diálogo de Material) y el lugar (ya no es un ajuste de nutrición). Toca un aviso para leerlo y «Volver» para regresar.',
     'El primer aviso muestra su texto real; para los otros cuatro la app lee el archivo de licencia incluido en el APK (aquí, líneas de lectura).']}});

ajScene({id:'aj-diagnostico',title:'Diagnóstico',init:()=>({route:'diag',stack:['main','help']}),
 notes:{tags:[['move','Telemetría JSONL'],['ask','¿sigue la carpeta externa?'],['out','sale la tarjeta roja y su jerga']],
  p:['La tarjeta roja de hoy mezclaba jerga («Escritura asíncrona en lotes cada 250 ms», rutas internas, «JSONL») con cuatro botones. Aquí queda lo que se hace de verdad: <b>exportar el registro en un ZIP</b> y, opcionalmente, <b>copiarlo a una carpeta</b> (configurar, sincronizar, desvincular).',
     'La carpeta externa solo se puede configurar desde Ajustes (lo dice <code>KpknDiagnosticStorage</code>). Propongo conservarla; si prefieres que el ZIP sea lo único, saldría con ella el ajuste.'],
  list:['Pulsa «Elegir carpeta» para ver el estado con carpeta.'],listTitle:'Prueba'}});

ajScene({id:'aj-borrar',title:'Borrar todos los datos · confirmación',init:()=>({sheet:'delete'}),
 notes:{tags:[['keep','misma acción y mismo texto'],['new','mantener pulsado + guardar copia antes']],
  p:['La acción más destructiva de la app pide más que un «Aceptar»: la hoja enumera <b>qué se borra</b> y <b>qué no se toca</b> (tus copias fuera de KPKN), ofrece «Guardar una copia antes» y el botón rojo exige <b>mantener pulsado 1,2 s</b> (se va llenando). Soltar antes cancela.',
     'Accesible: con TalkBack o teclado, el botón se activa con doble toque; con «reducir movimiento» el relleno es instantáneo pero el tiempo se mantiene. Al terminar, la app vuelve a cero (lo ves al fondo: unidades en kg, recordatorios apagados) y avisa «La app volvió a cero».']}});

/* ---------- Veredictos (documento) ---------- */
const AJ_V={out:'<span class="tag out">SE ELIMINA</span>',keep:'<span class="tag keep">SE QUEDA</span>',move:'<span class="tag move">SE MUEVE</span>',nw:'<span class="tag new">NUEVO</span>',ask:'<span class="tag ask">PREGUNTA</span>'};
const AJ_ROWS=[
 ['Tarjeta «Lo esencial para que KPKN funcione»',AJ_V.out,'Texto de relleno: repite los títulos de abajo y dice que «no hay ajustes técnicos ocultos». Sin consumidor.'],
 ['Descanso por defecto (−15 / +15 s)',AJ_V.out,'Cada ejercicio fija su descanso en el editor de sesiones, que ya escribe un valor al crear (ExerciseEditorExpanded, SessionEditorSessionHelpers). Solo era respaldo de <code>restTime = null</code>: pasa a una constante interna de 90 s. Sin migración; el campo queda en el JSON. Quien lo tuviera en otro valor verá 90 en ejercicios sin descanso propio.'],
 ['Iniciar descanso al registrar una serie',AJ_V.out,'Un único lector (WorkoutSetRecorder:958) y por defecto activo: pasa a ser siempre así. Las pausas guiadas de técnicas no cambian.'],
 ['Mi material',AJ_V.move,'<b>No se pierde el dato, se pierde la pantalla.</b> El motor lo usa de verdad (progresión redondea a discos reales, cargas de máquina, calculadora de discos, sustituciones: ProgramRepository, WorkoutViewModel, PlateCalculator…). El asistente ya pregunta lo mismo (pasos EQUIPMENT e INVENTORY_BARBELL / PLATES / DUMBBELLS / KETTLEBELLS / MACHINES) y guarda en el mismo campo. Al quitar la pantalla el motor sigue igual; lo que se pierde es corregir un disco sin reabrir el asistente (pregunta 1). Salen la ruta y 4 archivos (600+ líneas).'],
 ['Fuentes y licencias (Nutrición)',AJ_V.keep+AJ_V.move,'Atribución obligatoria (ODbL, DbCL, CC0, Etalab). Pasa a «Ayuda y acerca de»: no es un ajuste de nutrición.'],
 ['Permisos: notificaciones, micrófono, ubicación, Bluetooth, cámara',AJ_V.keep,'Único sitio para recuperar un permiso negado. Los cinco tienen consumidor real (ver inventario). Subpantalla propia con estados.'],
 ['Permiso: Health Connect',AJ_V.keep+AJ_V.move,'Es una conexión, no un permiso; hoy siempre dice «Activar». Fila en la pantalla principal (solo flavor health).'],
 ['Permiso: Recordatorios y temporizadores',AJ_V.keep,'Alarmas precisas + batería. Renombrado «Alarmas y batería». Siempre abre los ajustes del sistema.'],
 ['Recorridos · Repetir el recorrido',AJ_V.keep+AJ_V.move,'Una sección entera para una fila. Pasa a «Ayuda y acerca de».'],
 ['Datos · Exportar álbumes (ZIP)',AJ_V.keep,'Fila en «Copia y exportación».'],
 ['Backup · Exportar JSON / Copia completa .db',AJ_V.keep+AJ_V.move,'Dos filas casi iguales → «Guardar copia» con una hoja de elección.'],
 ['Backup · Importar JSON / Restaurar .db',AJ_V.keep+AJ_V.move,'Dos filas → «Restaurar copia». La .db gana confirmación (hoy reemplaza la base sin preguntar).'],
 ['Backup · Exportar datos (CSV)',AJ_V.keep,'Fila + su hoja actual, con presentación nueva.'],
 ['Telemetría JSONL (tarjeta roja)',AJ_V.move,'A «Diagnóstico». Salen la jerga y las rutas internas; quedan exportar ZIP y la carpeta externa (pregunta 3).'],
 ['Eliminar todos los datos',AJ_V.keep,'Al final de Ajustes, con confirmación que enumera qué se borra, ofrece guardar copia y pide mantener pulsado.'],
 ['Unidades kg / lb (sin pantalla hoy)',AJ_V.nw,'Manda en 14 carpetas y solo se elegía en el asistente. La hoja CSV ya habla de «unidades de Ajustes».'],
 ['Recordatorios: entrenar, comidas, dormir (sin pantalla hoy)',AJ_V.nw+AJ_V.ask,'Motor completo (alarmas, receptor de arranque, textos) que nadie puede activar. Pregunta 2.']
];
const AJ_HIDDEN=[
 ['Sonido y vibración (soundsEnabled, hapticFeedbackEnabled, hapticIntensity)','No se muestran','Ya obedecen el modo del timbre del teléfono (SystemAudioHelper). Un interruptor más dentro de la app repetiría el del sistema.'],
 ['Idioma (appLanguage)','No se muestra','Hay 0 textos de pantalla en los recursos de idioma: toda la interfaz está escrita en español dentro del código. Un selector no cambiaría casi nada.'],
 ['Voz: verbosidad, perfil de ruido, velocidad, sugerir cargas, AEC','No se muestran','Sin escritor en toda la app. El modo de captura (manos libres / música) ya se elige en la propia sesión. Pregunta 4.'],
 ['Vista de la sesión, técnica por vídeo','No se repiten','Se cambian donde se usan (sesión en vivo; hub de técnica).'],
 ['intensityMetric, appTheme, themePrimaryColor, enableAnimations, reducedMotionMode, workoutLoggerMode, sessionCompactView, sessionAutoAdvanceFields, showTimeSaverPrompt, gymName, sleepTargetHours, smartSleepEnabled, apiProvider, apiKeys, aiTemperature, aiMaxTokens, aiFallbackEnabled, useApiForDescriptions, oneRMFormula, defaultVolumeSystem, restTimerAutoStart','Campos huérfanos','Cero lectores fuera del modelo y del backup. Se quedan en el JSON por compatibilidad; retirarlos es una limpieza aparte.']
];
scene({tab:'ajustes',id:'aj-veredictos',title:'Veredictos: qué se queda, qué se mueve, qué sale',doc:()=>`
 <h2>De 10 bloques y unos 24 controles a 6 filas</h2>
 <p>Hoy Ajustes edita solo <b>2 de los ~70 campos</b> de configuración de la app; todo lo demás son acciones (permisos, copias, borrar). <code>SettingsListItems.kt</code> (462 líneas de interruptores, desplegables y deslizadores) no lo usa nadie: código muerto. La nueva pantalla conserva únicamente lo que solo tiene sentido en un sitio global: <b>unidades, recordatorios, permisos, conexiones, copia y exportación, ayuda y borrar todo</b>.</p>
 <h2>Lo que hay hoy → qué pasa con ello</h2>
 <div class="dtable-wrap"><table class="aj-doc-t"><thead><tr><th>Hoy en la app</th><th>Veredicto</th><th>Por qué y qué pasa al cambiarlo</th></tr></thead><tbody>${AJ_ROWS.map(r=>`<tr><td>${r[0]}</td><td>${r[1]}</td><td>${r[2]}</td></tr>`).join('')}</tbody></table></div>
 <h2>Campos que existen en el motor y NO se muestran</h2>
 <div class="dtable-wrap"><table class="aj-doc-t"><thead><tr><th>Campo</th><th>Veredicto</th><th>Razón</th></tr></thead><tbody>${AJ_HIDDEN.map(r=>`<tr><td>${r[0]}</td><td><span class="tag out">${r[1]}</span></td><td>${r[2]}</td></tr>`).join('')}</tbody></table></div>
 <h2>Lo que tienes que decidir</h2>
 <div class="aj-ask"><b>1 · Mi material: solo en el asistente, o un acceso desde el Perfil</b><p>Recomiendo solo el asistente (ya pregunta barra, discos, mancuernas, kettlebells y máquinas). Si luego compras unos discos y quieres corregirlo sin crear un programa, haría falta un acceso desde el Perfil, no desde Ajustes. Dime si lo quieres.</p></div>
 <div class="aj-ask"><b>2 · Recordatorios: ¿pantalla nueva o nada?</b><p>El motor está completo y sin pantalla. Si no los quieres, esa fila y su subpantalla se quitan sin tocar nada más (y los recordatorios dejan de existir para siempre).</p></div>
 <div class="aj-ask"><b>3 · Diagnóstico: ¿mantienes la carpeta externa?</b><p>Solo se configura desde Ajustes. Si el ZIP te basta, sale la carpeta; si usas el espejo para tus pruebas, se queda.</p></div>
 <div class="aj-ask"><b>4 · Voz</b><p>Verbosidad, ruido y velocidad de voz tienen motor y ninguna pantalla. Recomiendo dejarlos en su valor por defecto. Si quieres un bloque «Voz», serían tres filas dentro de Preferencias.</p></div>
 <div class="aj-ask"><b>5 · Quien cambió el descanso por defecto lo pierde</b><p>Sin migración: su valor no se vuelve a leer y los ejercicios sin descanso usan 90 s. Si prefieres respetarlo, se podría escribir su valor una vez en los ejercicios que no tengan descanso antes de retirar el campo.</p></div>
 <h2>Qué exige implementarlo</h2>
 <p>Sin cambios de motor ni de Room: todo son preferencias que ya existen en <code>Settings</code> (JSON, sin migración). Salen <code>TrainingSettingsBlock</code>, <code>equipment/*</code> (ruta <code>EquipmentInventory</code>) y los lectores de <code>restTimerDefaultSeconds</code> y <code>autoStartRestAfterRecording</code> pasan a constantes. Se añaden la hora en <code>Settings</code> (ya existe como cadena HH:mm) y la hoja de confirmación de restaurar .db.</p>`});
