/* ================= COMPARTIR EN HISTORIAS ================= */
// Imagen 9:16 (1080 × 1920 al exportar). Arte de líneas arriba, nombre y datos del programa abajo.
const SPLIT=[['L','Pierna A',.8],['M','Empuje',.7],['X','Tirón · Cardio',1],['J','',0],['V','Pierna B',.85],['S','Movilidad',.4],['D','',0]];
function storyFrame(inner,{style='perfil',pal='carbon',ey='Programa · 6 semanas',w}={}){
  return `<div class="story" style="${w?`--sw:${w}`:''}"><div class="bgart draw">${coverSvg(style,pal)}</div><div class="st-in"><div class="st-ey">${ey}</div><div class="st-name">${PROGRAM.name}</div>${inner}
  <div class="st-foot">KPKN<span>Mi programa de entreno</span></div></div></div>`;
}
const storySemana=(o={})=>storyFrame(`<div class="st-meta">Músculo · 5 días por semana</div><div class="st-rows">${SPLIT.map(d=>`<div class="${d[2]?'':'off'}"><b>${d[0]}</b><span>${d[1]||'Descanso'}</span><i style="width:${Math.round(d[2]*100)}%"></i></div>`).join('')}</div>`,o);
const storyBloques=(o={})=>storyFrame(`<div class="st-meta">Músculo · 3 bloques</div><div class="st-blocks"><div class="bar">${PROGRAM.weeks.map(w=>`<i style="flex:1;background:${PH[w.ph].c};opacity:.85"></i>`).join('')}</div>
  ${PROGRAM.blocks.map(b=>`<div class="row"><b>${b.name.split('· ')[1]}</b><span>${b.weeks.length} sem · ${b.scheme}</span></div>`).join('')}</div>`,{style:'espiral',...o});
const storyProgreso=(o={})=>storyFrame(`<div class="st-meta">4 semanas · 92 % de cumplimiento</div><div class="st-lifts">${[['Sentadilla','+7,5 kg'],['Press banca','+4 kg'],['Dominadas','+5 kg']].map(l=>`<div><span>${l[0]}</span><b>${l[1]}</b></div>`).join('')}</div>`,{style:'relieve',ey:'Octubre · progreso',...o});
const fullStory=(fn,o)=>`<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:#000">${fn({w:'100%',...o})}</div>`;
const drawAfter=ph=>animateIn(ph);

scene({tab:'compartir',id:'sh-entry',title:'Botón en la portada',after:drawAfter,html:()=>`${topbar('Semana')}${detailScroll(`${heroBlock()}<div style="height:22px"></div>${weekHead()}`)}<div class="ann" style="right:86px;top:268px">Compartir en historias</div>`,
 notes:{tags:[['new','Nuevo: compartir']],p:['Un botón de icono junto a la acción del día, al lado de la campana. También está en ⋯ › «Compartir en historias» y en el ⋯ de cada programa de la lista.','Abre una hoja con la vista previa de la historia y sus tres plantillas.']}});
scene({tab:'compartir',id:'sh-sheet',title:'Hoja de compartir',after:drawAfter,html:()=>`${topbar('Semana')}${detailScroll(heroBlock())}${sheet(`<h3>Compartir en historias</h3>
  <div style="display:flex;justify-content:center;margin:10px 0 14px">${storySemana({w:'190px'})}</div>
  <div class="seg" style="margin-bottom:10px"><button class="on">Semana</button><button>Bloques</button><button>Progreso</button></div>
  <div style="display:flex;gap:8px;justify-content:center;margin-bottom:14px">${['perfil','relieve','ondas','orbitas','espiral'].map((s,i)=>`<span style="width:36px;height:45px;border-radius:9px;overflow:hidden;position:relative;${i===0?'outline:2px solid var(--ink);outline-offset:2px':''}">${coverSvg(s,'carbon')}</span>`).join('')}</div>
  <button class="pill pri" style="width:100%">${ic('share',18)}Compartir en Instagram</button>
  <div style="display:flex;gap:8px;margin-top:8px"><button class="pill sec sm" style="flex:1">Guardar imagen</button><button class="pill sec sm" style="flex:1">Más apps</button></div>`)}`,
 notes:{tags:[['new','Tres plantillas'],['keep','Portada del programa por defecto']],p:['Vista previa a tamaño real reducido, con las líneas dibujándose. Tres plantillas: <b>Semana</b> (reparto por día), <b>Bloques</b> (fases y método de cada bloque) y <b>Progreso</b> (solo si hay al menos 4 semanas de datos).','El fondo es la portada del programa; se puede cambiar por otro estilo de líneas solo para la historia.','«Compartir en Instagram» abre directamente la cámara de historias con la imagen de fondo; «Más apps» abre el menú de compartir del sistema. Si Instagram no está instalado, solo sale «Compartir».'],
  list2Title:'Qué nunca sale en la imagen',list2:['Pesos corporales, molestias, notas o fechas exactas de entreno.','Cargas absolutas, salvo en la plantilla Progreso, que muestra solo lo ganado (+kg).']}});
scene({tab:'compartir',id:'sh-semana',title:'Historia · Semana',after:drawAfter,html:()=>fullStory(storySemana),
 notes:{p:['El reparto semanal como barras verticales: la altura es el trabajo planificado del día y debajo va el nombre de la sesión. Los descansos quedan como un trazo corto.','Un día con dos sesiones las junta en una barra con «Tirón · Cardio».']}});
scene({tab:'compartir',id:'sh-bloques',title:'Historia · Bloques',after:drawAfter,html:()=>fullStory(storyBloques),
 notes:{p:['La cinta de fases a lo ancho y cada bloque con sus semanas y su método. Fondo «Espiral»: una vuelta por semana.']}});
scene({tab:'compartir',id:'sh-progreso',title:'Historia · Progreso',after:drawAfter,html:()=>fullStory(storyProgreso),
 notes:{p:['Solo lo ganado en los básicos y el cumplimiento. Es la misma tarjeta con la que termina el resumen del mes.']}});
