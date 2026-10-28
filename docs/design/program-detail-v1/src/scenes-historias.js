/* ================= HISTORIAS: ENTRENO REALIZADO Y RESUMEN DEL MES ================= */
// Mismo lenguaje que la historia del programa: arte de líneas arriba, texto abajo, un solo acento. 9:16, 1080 × 1920 al exportar.
function storyShell({art='',top='38%',artBottom='42%',ey='',title='',sub='',inner='',foot='Mi programa de entreno',w,photo=false,end=false}){
  return `<div class="story" style="${w?`--sw:${w}`:''}">${photo?'<div class="photo"></div>':''}<div class="bgart draw ${photo?'over':''}" style="inset:0 0 ${artBottom} 0">${art}</div>
  <div class="st-in ${end?'end':''}" style="top:${top}">${ey?`<div class="st-ey">${ey}</div>`:''}${title?`<div class="st-name">${title}</div>`:''}${sub?`<div class="st-meta">${sub}</div>`:''}${inner}
  <div class="st-foot">KPKN<span>${foot}</span></div></div></div>`;
}
const stat=(v,l,u='')=>`<div><b>${v}${u?`<small>${u}</small>`:''}</b><span>${l}</span></div>`;
const WO={name:'Tirón',date:'MIÉ 29 OCT · SEMANA 4',meta:'Motor y fuerza · Intensificación',stats:[['48','Duración','min'],['6.240','Volumen','kg'],['17','Series',''],['1,6','RIR medio','']],
  ups:[['Dominadas lastradas','+2,5 kg'],['Remo con barra','+2,5 kg']],records:1};
// Sesión (antes «General»)
const storyWoSesion=(o={})=>storyShell({art:coverSvg('sesion','carbon',{pr:0}),artBottom:'45%',top:'34%',end:true,ey:WO.date,title:WO.name,sub:WO.meta,w:o.w,foot:'Entreno de hoy',
  inner:`<div class="st-stats">${WO.stats.map(s=>stat(s[0],s[1],s[2])).join('')}</div><div class="st-lifts" style="margin-top:0">${WO.ups.map(l=>`<div><span>${l[0]}</span><b>${l[1]}</b></div>`).join('')}</div>
  <div class="st-badge">${ic('spark',14)}1 marca nueva</div>`});
// Ejercicio: la línea de e1RM del ejercicio como protagonista
function e1rmChart(v,{w=300,h=210}={}){const mn=Math.min(...v)-2,mx=Math.max(...v)+1;const X=i=>16+i*(w-40)/(v.length-1),Y=x=>h-24-(x-mn)/(mx-mn)*(h-60);
  let s='';for(let k=0;k<4;k++){const y=36+k*(h-60)/3;s+=`<line x1="10" x2="${w-10}" y1="${y}" y2="${y}" stroke="#ffffff10"/>`}
  const d=v.map((x,i)=>(i?'L':'M')+X(i).toFixed(1)+' '+Y(x).toFixed(1)).join('');
  s+=`<path d="${d}L${X(v.length-1)} ${h}L${X(0)} ${h}Z" fill="url(#ea)" opacity=".5"/><path class="ln" pathLength="1" d="${d}" fill="none" stroke="#f2eee6" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>`;
  v.forEach((x,i)=>{const last=i===v.length-1;s+=`<circle cx="${X(i)}" cy="${Y(x)}" r="${last?6:3.2}" fill="${last?'#f49a6e':'#0a0a0a'}" stroke="${last?'#f49a6e':'#f2eee6'}" stroke-width="1.6" ${last?'filter="url(#eg)"':''}/>`});
  return `<svg viewBox="0 0 ${w} ${h}" width="100%" height="100%" preserveAspectRatio="xMidYMid meet" aria-hidden="true"><defs><linearGradient id="ea" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f2eee6" stop-opacity=".18"/><stop offset="1" stop-color="#f2eee6" stop-opacity="0"/></linearGradient><filter id="eg" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="4" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>${s}</svg>`}
const storyWoEjercicio=(o={})=>`<div class="story" style="${o.w?`--sw:${o.w}`:''}"><div class="bgart" style="inset:0 0 50% 0;opacity:.16">${coverSvg('relieve','carbon',{seed:8})}</div>
  <div class="st-chart draw">${e1rmChart([135,136,138,139,141,142.5])}</div>
  <div class="st-in" style="top:50%"><div class="st-ey">Sentadilla trasera · e1RM</div><div class="st-big">142,5<small>kg</small></div>
  <div class="st-meta" style="margin-top:2cqw"><span style="color:#f49a6e;font-weight:600">+7,5 kg</span> en 6 sesiones</div>
  <div class="st-lifts"><div><span>Mejor serie de hoy</span><b style="font-size:4.6cqw">120 kg × 5</b></div><div><span>Esfuerzo</span><b style="font-size:4.6cqw">RIR 1</b></div></div>
  <div class="st-foot">KPKN<span>${WO.date.split(' · ')[0]}</span></div></div></div>`;
// Fotos: tu foto de fondo, con líneas finas encima
const storyWoFotos=(o={})=>storyShell({photo:true,art:coverSvg('relieve','carbon',{seed:4,clear:true}),artBottom:'52%',top:'62%',ey:WO.date,title:WO.name,w:o.w,foot:'Entreno de hoy',
  inner:`<div class="st-inline">${WO.stats.slice(0,3).map(s=>`<span>${s[0]}<small>${s[2]||' series'}</small></span>`).join('<i></i>')}</div>`});

// Resumen del mes
const MES={label:'OCTUBRE 2026',prog:'Motor y fuerza'};
const storyMesResumen=(o={})=>storyShell({art:coverSvg('relieve','carbon',{seed:5}),artBottom:'46%',top:'40%',end:true,ey:MES.label,title:'Mi octubre',sub:MES.prog,w:o.w,foot:'Resumen del mes',
  inner:`<div class="st-stats">${stat('14','Sesiones')+stat('12','Entrenando','h 40')+stat('42,4','Movidas','t')+stat('92','Del plan','%')}</div><div class="st-badge">${ic('spark',14)}Marca: sentadilla 142,5 kg</div>`});
function calSvg(){let s='';const lead=3;for(let d=1;d<=31;d++){const i=d-1+lead,c=i%7,r=Math.floor(i/7);const cx=24+c*42,cy=24+r*42;const on=OCT.trained.includes(d),pr=d===OCT.pr;
  s+=`<circle cx="${cx}" cy="${cy}" r="15" fill="${pr?'#f49a6e':on?'#f2eee6':'none'}" stroke="${on||pr?'none':'#f2eee633'}" stroke-width="1.2" ${pr?'filter="url(#cg)"':''}/>`}
  return `<svg viewBox="0 0 300 236" width="100%" aria-hidden="true"><defs><filter id="cg" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="5" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>${s}</svg>`}
const storyMesConstancia=(o={})=>`<div class="story" style="${o.w?`--sw:${o.w}`:''}"><div class="st-cal">${calSvg()}</div>
  <div class="st-in" style="top:52%"><div class="st-ey">${MES.label} · Constancia</div><div class="st-big">14<small>sesiones</small></div><div class="st-meta" style="margin-top:2.5cqw">12 h 40 min entrenando · 92 % del plan</div>
  <div class="st-lifts"><div><span>Racha más larga</span><b style="font-size:4.6cqw">9 sesiones</b></div></div><div class="st-foot">KPKN<span>Resumen del mes</span></div></div></div>`;
const storyMesFuerza=(o={})=>storyShell({end:true,art:coverSvg('perfil','carbon',{vol:[.55,.62,.7,.8],now:3}),ey:MES.label,title:'Más fuerte',sub:'e1RM al final del mes',w:o.w,foot:'Resumen del mes',
  inner:`<div class="st-lifts">${[['Sentadilla',[135,136,138,139,141,142.5],'+7,5 kg'],['Press banca',[97,97.5,99,99,100,101],'+4 kg'],['Dominadas',[20,21,22,23,24,25],'+5 kg']].map(l=>`<div style="align-items:center"><span style="flex:1">${l[0]}</span><span style="width:22cqw;height:7cqw;display:block;margin-right:4cqw">${`<svg viewBox="0 0 84 28" width="100%" height="100%" class="draw"><path class="ln" pathLength="1" d="${sparkD(l[1],84,28)}" fill="none" stroke="#f2eee6" stroke-width="1.6" stroke-linecap="round"/></svg>`}</span><b style="width:30cqw;text-align:right;color:#f49a6e;white-space:nowrap;font-size:5.2cqw">${l[2]}</b></div>`).join('')}</div>`});
const storyMesMusculos=(o={})=>`<div class="story" style="${o.w?`--sw:${o.w}`:''}"><div class="st-cal" style="top:5%;left:0;right:0">${radial()}</div>
  <div class="st-in end" style="top:56%"><div class="st-ey">${MES.label} · Músculos</div><div class="st-name" style="font-size:9cqw">Lo que más trabajé</div><div class="st-meta">246 series en el mes</div><div class="st-lifts">${[['Dorsales','58'],['Deltoides','56'],['Cuádriceps','52']].map((m,i)=>`<div><span>${m[0]}</span><b style="${i===0?'color:#f49a6e':''}">${m[1]}<span style="font:600 3.4cqw Inter;color:#8e8980"> series</span></b></div>`).join('')}</div>
  <div class="st-foot">KPKN<span>Resumen del mes</span></div></div></div>`;
const STORIES={semana:storySemana,bloques:storyBloques,progreso:storyProgreso,sesion:storyWoSesion,ejercicio:storyWoEjercicio,fotos:storyWoFotos,mes:storyMesResumen,constancia:storyMesConstancia,fuerza:storyMesFuerza,musculos:storyMesMusculos};

/* --- escenas --- */
const finishBehind=()=>`<div class="scroll" style="padding-top:60px"><div class="ey" style="color:var(--done)">Sesión completada</div><div class="ptitle" style="margin:6px 0 4px">Tirón</div><div class="cap">48 min · 17 series · 6.240 kg</div></div>`;
scene({tab:'compartir',id:'sh-wo-sheet',title:'Entreno · hoja de compartir',after:drawAfter,html:()=>`${finishBehind()}${sheet(`<h3>Compartir</h3>
  <div style="display:flex;justify-content:center;margin:8px 0 12px">${storyWoSesion({w:'176px'})}</div>
  <div class="seg" style="margin-bottom:10px"><button class="on">Sesión</button><button>Ejercicio</button><button>Fotos</button></div>
  <div class="mgroup" style="margin-top:4px">Incluir</div><div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:14px">${['Mejoras en kg','Duración','Intensidad'].map(x=>`<span class="chip on" style="height:32px">${ic('check',13)}${x}</span>`).join('')}</div>
  <button class="pill pri" style="width:100%">${ic('share',18)}Compartir en historias</button><button class="pill sec sm" style="width:100%;margin-top:8px">Guardar imagen</button>`)}`,
 notes:{tags:[['keep','Mismas 3 plantillas y opciones'],['new','Estilo de líneas']],p:['Es la hoja actual del resumen de sesión con la misma lógica: plantillas (General pasa a llamarse <b>Sesión</b>, Ejercicio y Fotos), interruptores «Mejoras en kg», «Duración» e «Intensidad», guardar imagen y compartir en historias.','Lo que cambia es la imagen: pasa al lenguaje de las portadas de líneas, igual que la historia del programa.']}});
scene({tab:'compartir',id:'sh-wo-sesion',title:'Entreno · Sesión',after:drawAfter,html:()=>fullStory(storyWoSesion),
 notes:{tags:[['new','Fondo «Sesión»']],p:['El fondo se dibuja con la sesión real: una línea por ejercicio y un pico por serie (su alto es kg × repeticiones). La línea del ejercicio con mejora o marca va en acento.','Debajo: fecha y semana, nombre de la sesión, programa y fase, cuatro cifras (duración, volumen, series e intensidad), las mejoras en kg y la insignia de marca si la hubo. Cada bloque se oculta con su interruptor.']}});
scene({tab:'compartir',id:'sh-wo-ejercicio',title:'Entreno · Ejercicio',after:drawAfter,html:()=>fullStory(storyWoEjercicio),
 notes:{p:['Un ejercicio protagonista (por defecto, el que más mejoró): su línea de e1RM sesión a sesión, con el último punto en acento, la cifra grande, lo ganado y la mejor serie de hoy.']}});
scene({tab:'compartir',id:'sh-wo-fotos',title:'Entreno · Fotos',after:drawAfter,html:()=>fullStory(storyWoFotos),
 notes:{p:['Tu foto de la sesión a pantalla completa (en el prototipo, un degradado en su lugar), con curvas de nivel finas encima y un velo oscuro abajo para el texto: nombre, fecha y tres cifras en una línea.']}});
scene({tab:'compartir',id:'sh-mes-sheet',title:'Resumen del mes · compartir',after:drawAfter,html:()=>`${wrapped(9)}${sheet(`<h3>Compartir tu octubre</h3>
  <div style="display:flex;justify-content:center;margin:8px 0 12px">${storyMesResumen({w:'176px'})}</div>
  <div class="seg" style="margin-bottom:14px"><button class="on">Resumen</button><button>Constancia</button><button>Fuerza</button><button>Músculos</button></div>
  <button class="pill pri" style="width:100%">${ic('share',18)}Compartir en historias</button><button class="pill sec sm" style="width:100%;margin-top:8px">Guardar imagen</button>`)}`,
 notes:{tags:[['new','4 plantillas del mes']],p:['Se abre con «Compartir» en la última pantalla del resumen del mes (y desde cada resumen guardado en Progreso). Cuatro plantillas, una por las pantallas más compartibles: Resumen, Constancia, Fuerza y Músculos.','Igual que en las otras historias: nunca peso corporal, molestias ni notas; «A vigilar» no se comparte.']}});
scene({tab:'compartir',id:'sh-mes-resumen',title:'Mes · Resumen',after:drawAfter,html:()=>fullStory(storyMesResumen),notes:{p:['Cuatro cifras del mes y la mejor marca, sobre el relieve del mes.']}});
scene({tab:'compartir',id:'sh-mes-constancia',title:'Mes · Constancia',after:drawAfter,html:()=>fullStory(storyMesConstancia),notes:{p:['El calendario del mes en círculos: entrenados en crema, el día de la marca en acento con un leve brillo, el resto solo contorno.']}});
scene({tab:'compartir',id:'sh-mes-fuerza',title:'Mes · Fuerza',after:drawAfter,html:()=>fullStory(storyMesFuerza),notes:{p:['Los básicos que subieron, cada uno con su línea del mes y lo ganado en acento. Fondo «Perfil» con las 4 semanas del mes.']}});
scene({tab:'compartir',id:'sh-mes-musculos',title:'Mes · Músculos',after:drawAfter,html:()=>fullStory(storyMesMusculos),notes:{p:['Los rayos de series por músculo como imagen principal, con el más trabajado en acento.']}});
