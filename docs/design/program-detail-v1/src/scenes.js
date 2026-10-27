/* ================= PORTADA ================= */
// La barra flotante (atrás · pestañas · ⋯) está siempre arriba; la portada empieza bajo ella y pasa por debajo al hacer scroll.
function bellBtn(n=3){return n?`<button class="ib fill bell" aria-label="Pendiente: ${n}" style="width:52px;height:52px">${ic('bell')}<b>${n}</b></button>`:''}
function heroBlock({status='ACTIVO',dot='var(--done)',primary,secondary,note,pending=3}={}){
  return `<div class="hero">${cover()}<div class="hero-t"><div class="status"><span class="dot" style="background:${dot}"></span>${status}</div>
  <div class="pname">${PROGRAM.name}</div><div class="meta">Músculo · 6 semanas · 3 bloques</div></div></div>
  ${ribbon()}<div class="ribbon-l"><span>Semana <b class="tn">4</b> de 6 · Intensificación</span><span class="cap">Bloque 2 de 3</span></div>
  <div class="hero-acts">${primary||`<button class="pill pri">${ic('play',18)}Empezar · Tirón</button>`}<button class="ib fill" aria-label="Compartir en historias" style="width:52px;height:52px">${ic('share')}</button>${secondary===undefined?bellBtn(pending):secondary}</div>
  ${note===undefined?`<div class="cap" style="margin:10px 4px 0">Hoy también: Cardio y potencia · ≈ 32 min</div>`:note}`;
}
const detailScroll=(inner,extra='')=>`<div class="scroll" style="padding-top:100px"${extra}>${inner}</div>`;
scene({tab:'portada',id:'p-today',title:'Activo, con sesión hoy',html:()=>`${topbar('Semana')}${detailScroll(`${heroBlock()}<div style="height:22px"></div>${weekHead()}${agendaHtml()}`)}`,
 notes:{tags:[['new','Barra flotante: atrás · pestañas · ⋯'],['new','Campana de «Pendiente»'],['new','Portada «Fases»'],['out','Sale: «Sobre este programa»']],
  p:['<b>Arriba, tres piezas de vidrio sueltas</b>: atrás, la píldora de pestañas y ⋯. Siempre visibles; el contenido pasa por debajo, desenfocado. Ya no hay pestañas dentro de la página ni isla con el nombre.',
     'Debajo, la portada: objetivo, duración y bloques en una sola línea, y la cinta de fases. Y justo después, la semana: nada se interpone.',
     '<b>«Pendiente» deja de ser una tarjeta</b>: es una campana junto a la acción del día, con el número en color músculo. No empuja la semana y no usa colores ajenos.',
     'Pausar pasa al ⋯ (ya estaba allí en la propuesta). El lápiz sigue fuera.'],
  listTitle:'Qué sale de la portada y adónde va',
  list:['«Sobre este programa» (procedencia, notas, «Ver más», «Ver cómo funciona») → ⋯ › «Sobre el programa».','«Progreso del programa» → pestaña Progreso.','«Próxima semana · series según el plan» → Progreso › Volumen, alcance «Semana 5».','Los ~8 módulos de aviso → la campana y su hoja.']}});
scene({tab:'portada',id:'p-pending',title:'Campana › Pendiente',html:()=>`${topbar('Semana')}${detailScroll(`${heroBlock()}<div style="height:22px"></div>${weekHead()}`)}
  ${sheet(`<h3>Pendiente</h3><div class="sub">3 cosas esperan tu decisión.</div>
  <div class="mod" style="background:#ffffff08;border-color:#ffffff14;margin:4px 0 6px;padding:14px 16px"><div style="display:flex;gap:12px;align-items:center"><span class="mic" style="width:38px;height:38px;border-radius:50%;background:#ffffff10;display:flex;align-items:center;justify-content:center">${ic('trendUp',19)}</span><div style="flex:1"><strong style="font-weight:600">Sentadilla: +2,5 kg</strong><div class="cap">Cerraste 3 × 5 con RIR 3 dos sesiones seguidas. Viernes · Pierna B.</div></div></div>
  <div style="display:flex;gap:8px;margin-top:12px"><button class="pill pri sm" style="flex:1">Aplicar</button><button class="pill sec sm" style="flex:1">Mantener carga</button></div></div>
  ${mi('target','Falta el TM de press militar','Sin él, sus cargas no se calculan')}
  ${mi('check','Check-in de la semana 3','Responder u omitir')}
  <div class="mgroup">Más adelante</div>${mi('repeat','Fin del programa en 2 semanas','Te preguntaremos cómo seguir')}`)}`,
 notes:{tags:[['keep','Misma lógica de cada aviso'],['new','Colores de KPKN']],p:['La hoja de vidrio de siempre, sin tintes amarillos: iconos en círculo tonal, texto en tinta y el número en color músculo. El primero se resuelve ahí mismo; los demás abren la hoja que abren hoy.','Orden: bloqueantes (test de 1RM, sesiones perdidas, TM que falta), propuestas del motor, preguntas y, al final, lo que llega más adelante.'],
  list2Title:'Dónde más se ve',list2:['En la Semana, la sesión afectada lleva una marca pequeña («+2,5 kg») que abre la misma propuesta.','Al hacer scroll, cuando la campana ya no se ve, ⋯ lleva un punto músculo y «Pendiente» es su primera fila.']}});
scene({tab:'portada',id:'p-rest',title:'Día sin sesión',html:()=>`${topbar('Semana')}${detailScroll(`${heroBlock({primary:`<button class="pill sec">${ic('cal',18)}Siguiente: viernes · Pierna B</button>`,pending:0,note:''})}<div style="height:22px"></div>${weekHead()}${agendaHtml()}`)}`,
 notes:{tags:[['keep','Se queda: «Siguiente: día · sesión»']],p:['Sin sesión hoy, la acción pasa a secundaria y lleva al día de la siguiente sesión. Sin pendientes no hay campana. Sin frases de relleno debajo.']}});
scene({tab:'portada',id:'p-paused',title:'Pausado o borrador',html:()=>`${topbar('Semana')}${detailScroll(`${heroBlock({status:'PAUSADO',dot:'var(--ene)',primary:`<button class="pill pri">${ic('play',18)}Reanudar</button>`,pending:0,note:''})}<div style="height:22px"></div>${weekHead()}`)}`,
 notes:{p:['Pausado: la acción principal es «Reanudar»; en borrador, «Activar programa». Mismas funciones que hoy.']}});
scene({tab:'portada',id:'p-comp',title:'Con competición en el plan',html:()=>`${topbar('Semana')}${detailScroll(`${heroBlock()}
  <div class="mod" style="margin-top:18px"><div class="mod-h"><span class="ey">Competición</span><button class="act faint">Ficha${ic('fwd',16)}</button></div>
  <div style="display:flex;align-items:flex-end;gap:16px;margin-top:6px"><div><div class="num">18<small>días</small></div><div class="cap" style="margin-top:6px">Open regional · 16 nov</div><div class="cap">Pesaje 82,5 kg · −0,2 kg/sem</div></div>
  <div style="flex:1;text-align:right"><div class="cap">Intentos</div><div class="tn" style="font:700 15px Syne;margin-top:4px">185 · 120 · 210</div></div></div>
  <button class="pill sec sm" style="width:100%;margin-top:14px">Planificar intentos</button></div>${weekHead()}`)}`,
 notes:{tags:[['keep','Se queda, compacto']],p:['La competición es lo único extra que merece portada: cambia lo que haces estas semanas. «Modo competición» aparece el mismo día y «Revisar actualización de marcas» al terminar.','«Temporada» sale de Program Detail (aprobado).']}});
scene({tab:'portada',id:'p-scroll',title:'Al hacer scroll',scrollTo:470,html:()=>`${topbar('Semana',{dot:true})}${detailScroll(`${heroBlock()}<div style="height:22px"></div>${weekHead()}${agendaHtml()}`)}`,
 notes:{tags:[['new','Vidrio real sobre el contenido']],p:['Las sesiones pasan por debajo de las tres piezas y se ven desenfocadas. Las pestañas siguen a mano en todo momento.','La campana ya quedó arriba: ⋯ lleva un punto músculo mientras haya algo pendiente.','A 130 % de letra la píldora no crece más de un 8 % para que quepan las tres pestañas; si no caben, se recorta la palabra, nunca se solapa.']}});
/* ================= SEMANA ================= */
function sessCard(s,i,multi){
  const cls=s.st==='done'?'done':s.st==='opt'?'optn':s.st==='now'?'now':'';
  const right=s.st==='done'?`<span class="mark ok" aria-label="Hecha">${ic('check',16)}</span>`:s.st==='now'?`<button class="play" aria-label="Empezar ${s.name}">${ic('play',18)}</button>`:s.st==='opt'?`<span class="chip" style="height:26px">Opcional</span>`:'';
  return `<div class="sess ${cls}"><div class="tx">${multi?`<span class="idx">${i+1} DE ${multi}</span>`:''}<strong>${s.name}</strong><div class="ln"><span class="focus">${s.f.map(f=>`<i style="background:${MUS[f]}"></i>`).join('')}</span>${s.ex} ejercicios · ≈ ${s.min} min${s.prop?` <span class="pchip">${ic('trendUp',12)}${s.prop}</span>`:''}</div></div>${right}</div>`;
}
function agendaHtml(week=WEEK,opts={}){
  let out='';let i=0;
  while(i<week.length){
    const d=week[i];
    if(!d.s.length){ // agrupa descansos seguidos
      let j=i;while(j+1<week.length&&!week[j+1].s.length)j++;
      const lbl=j>i?`${d.d.slice(0,3)} ${d.n} – ${week[j].d.slice(0,3)} ${week[j].n}`:'';
      out+=`<div class="day restd ${d.past?'past':''}"><div class="dcol"><small>${d.d}</small><b class="tn">${d.n}</b></div><div class="rest">${j>i?`Descanso · ${j-i+1} días`:'Descanso'}${opts.addOnRest?`<button class="act faint" style="margin-left:auto">${ic('plus',16)}Sesión</button>`:''}</div></div>`;
      i=j+1;continue;
    }
    const multi=d.s.length>1?d.s.length:0;
    const tot=d.s.reduce((a,s)=>a+s.min,0);
    out+=`<div class="day ${d.today?'today':''} ${d.past?'past':''}"><div class="dcol"><small>${d.today?'HOY':d.d}</small><b class="tn">${d.n}</b></div><div class="dsess">${multi?`<div class="dayhead"><span>${multi} sesiones</span><span class="tn">≈ ${Math.floor(tot/60)} h ${tot%60} min</span></div>`:''}${d.s.map((s,k)=>opts.open&&d.today&&k===0?openSess(s):sessCard(s,k,multi)).join('')}</div></div>`;
    i++;
  }
  return `<div class="agenda">${out}</div>`;
}
function openSess(s){
  return `<div class="sess now open"><div style="display:flex;align-items:flex-start;gap:10px"><div class="tx" style="flex:1"><span class="idx">1 DE 2</span><strong style="font-size:calc(19px*var(--fs))">${s.name}</strong><div class="ln"><span class="focus">${s.f.map(f=>`<i style="background:${MUS[f]}"></i>`).join('')}</span>${s.ex} ejercicios · ≈ ${s.min} min · 17 series</div></div><button class="ib" aria-label="Más opciones de la sesión">${ic('more')}</button></div>
  <div style="margin-top:8px">${SESSION_EX.map(e=>`<div class="ex"><strong>${e.n}</strong><button class="sw ib" aria-label="Cambiar ejercicio">${ic('swap',18)}</button><span class="tn">${e.d}</span></div>`).join('')}</div>
  <div class="sess-acts"><button class="pill pri sm">${ic('play',16)}Empezar</button><button class="pill sec sm">${ic('edit',16)}Editar</button></div></div>`;
}
function weekHead(n=4,ph='int',dates='29 oct – 2 nov',stats=true){
  return `<div class="mod"><div class="wk-nav"><button class="ib" aria-label="Semana anterior">${ic('back')}</button><div class="t"><strong>Semana ${n}</strong><span><i class="dot" style="background:${PH[ph].c}"></i>${PH[ph].n} · ${dates}</span></div><button class="ib" aria-label="Semana siguiente">${ic('fwd')}</button></div>
  ${stats?`<div class="wk-stats"><div><b>2/5</b><span>sesiones</span></div><div><b>26/60</b><span>series</span></div><div><b>≈ 4 h 36</b><span>en total</span></div></div>`:''}</div>`;
}
const weekTop=()=>topbar('Semana',{dot:true});
scene({tab:'semana',id:'s-agenda',title:'Agenda de la semana',html:()=>`${weekTop()}<div class="scroll" style="padding-top:100px">${weekHead()}${agendaHtml()}
  <div style="display:flex;gap:8px;margin-top:10px"><button class="pill sec sm" style="flex:1">${ic('plus',16)}Añadir sesión</button><button class="ib fill" aria-label="Más opciones de la semana">${ic('more')}</button></div></div>`,
 notes:{tags:[['new','Nuevo: agenda con fecha a la izquierda'],['new','Varias sesiones por día'],['move','«Fases» → cinta + hoja'],['out','Sale: panel de TM fijo']],
  p:['<b>Navegador de semana</b>: flechas para ir y volver; al tocar el título se abre la hoja de semanas. La cinta de fases de la portada, justo encima, también lleva a cada semana al tocarla (no se repite aquí). Sustituye al módulo «Fases», que pintaba una fila de círculos por bloque y ocupaba media pantalla antes de ver un solo día.',
     '<b>Tres cifras de la semana</b>: sesiones hechas, series hechas de las planificadas y el tiempo total estimado (del estimador real, nunca series × 3).',
     '<b>Agenda</b>: cada día es una fila con la fecha a la izquierda (hoy en color músculo) y sus sesiones apiladas a la derecha. Si hay más de una, el día dice «2 sesiones · ≈ 1 h 20 min» y cada tarjeta lleva «1 de 2». Los descansos seguidos se juntan en una línea («Descanso · 2 días»).',
     'Cada sesión: nombre, puntos de foco muscular, ejercicios y minutos. Estado a la derecha: check si está hecha, ▶ si toca hoy, «Opcional» con borde punteado.'],
  listTitle:'Qué sale de la Semana',list:['Panel de TM por ejercicio (con «Sin procedencia registrada» en cada fila): pasa a Estructura › «Cargas de referencia». Si falta un TM, aparece en «Pendiente».','Propuestas de autorregulación incrustadas: pasan a «Pendiente».','Cabecera de semana con «Cambiar reparto» suelto: pasa al ⋯ de la semana.','Fila de 64 dp por día que había que desplegar para ver qué sesión había: la sesión se ve sin tocar.']}});
scene({tab:'semana',id:'s-open',title:'Sesión desplegada',html:()=>`${weekTop()}<div class="scroll" style="padding-top:100px">${agendaHtml(WEEK.slice(2,5),{open:true})}</div>`,scrollTo:0,
 notes:{tags:[['keep','Mismas acciones que hoy']],p:['Al tocar una sesión se despliega en su sitio: ejercicios con su dosis (series × reps · RIR · carga), «Cambiar ejercicio» en cada uno, «Empezar», «Editar» y ⋯ (restaurar desde el plan, eliminar, mover).','La segunda sesión del día sigue visible debajo: no se pierde el contexto.','«Sesión personalizada · Restaurar desde el plan» se mantiene como línea dentro de la tarjeta cuando aplica.']}});
scene({tab:'semana',id:'s-weeks',title:'Hoja de semanas',html:()=>`${weekTop()}<div class="scroll" style="padding-top:100px">${weekHead()}</div>${sheet(`<h3>Semanas</h3><div class="sub">6 semanas en 3 bloques. Mantén pulsada una semana para copiarla, pegarla o editarla.</div>
  ${PROGRAM.blocks.map(b=>`<div class="mgroup" style="color:${PH[b.ph].c}">${b.name} · ${PH[b.ph].n}</div><div class="wkrow" style="margin-top:8px">${b.weeks.map(n=>{const w=PROGRAM.weeks[n-1];return `<span class="wkc ${w.st==='done'?'done':''} ${w.st==='now'?'now':''}">${n}</span>`}).join('')}<span class="wkc add">${ic('plus',16)}</span></div>`).join('')}
  <button class="pill sec sm" style="width:100%;margin-top:20px">Ir a la semana actual</button>`)}`,
 notes:{tags:[['move','El módulo «Fases» vive aquí']],p:['Las filas de círculos por bloque (con «+» para añadir semana y pulsación larga para su menú) se conservan tal cual, pero en una hoja que se abre al tocar el título de la semana. En la pantalla queda solo la cinta.']}});
scene({tab:'semana',id:'s-drag',title:'Mover una sesión de día',html:()=>{const w=JSON.parse(JSON.stringify(WEEK));return `${weekTop()}<div class="scroll" style="padding-top:100px">
  <div class="day"><div class="dcol"><small>HOY</small><b class="tn" style="background:var(--mus);color:#111">29</b></div><div class="dsess">${sessCard(w[2].s[0],0,0)}<div class="sess ghost"><div class="tx"><strong>Cardio y potencia</strong></div></div></div></div>
  <div class="day"><div class="dcol"><small>JUE</small><b class="tn">30</b></div><div class="dsess"><div class="drop"></div><div class="sess dragcard"><div class="tx"><strong>Cardio y potencia</strong><div class="ln">4 ejercicios · ≈ 32 min</div></div><span class="ib">${ic('grip',18)}</span></div></div></div>
  ${agendaHtml(w.slice(4))}</div><div class="ann" style="left:150px;top:300px">Suelta para moverla al jueves</div>`},
 notes:{tags:[['keep','Arrastrar ya existe']],p:['Mantener pulsada una sesión la levanta; la línea de color marca dónde caerá. Funciona también hacia un día de descanso, que se abre para recibirla. Es el mismo arrastre de hoy (<code>onApplySessionsLayout</code>).']}});
scene({tab:'semana',id:'s-heavy',title:'Día con tres sesiones',html:()=>{const w=[{d:'MIÉ',n:29,today:true,s:[{name:'Técnica de arranque',ex:3,min:30,f:['leg','pull'],st:'done'},{name:'Tirón',ex:5,min:48,f:['pull','pull','core'],st:'now'},{name:'Cardio Z2',ex:1,min:40,f:['cardio'],st:'todo'}]},WEEK[3],WEEK[4]];return `${weekTop()}<div class="scroll" style="padding-top:100px">${agendaHtml(w)}</div>`},
 notes:{p:['Con tres sesiones el día crece en vertical; la fecha queda arriba a la izquierda y no se repite. Cada tarjeta mantiene su estado, así se ve de un vistazo qué falta del día.','A 130 % de letra el nombre pasa a elipsis y la línea de datos puede ocupar dos líneas; nada se solapa.']}});
scene({tab:'semana',id:'s-simple',title:'Rutina (una semana base)',html:()=>`${topbar('Semana')}<div class="scroll" style="padding-top:100px">
  <div class="mod"><div class="wk-nav"><button class="ib" aria-label="Semana anterior">${ic('back')}</button><div class="t"><strong>Semana 7</strong><span>3 – 9 nov</span></div><button class="ib" aria-label="Semana siguiente">${ic('fwd')}</button></div>
  <div class="mini-rib">${[1,2,3,4,5,6,7,8,9,10,11,12].map(n=>`<i class="${n<7?'done':''} ${n===7?'sel':''}" style="background:${n===8||n===4?'var(--ph-del)':'var(--ink)'}"></i>`).join('')}</div>
  <div class="wk-stats"><div><b>1/4</b><span>sesiones</span></div><div><b>14/58</b><span>series</span></div><div><b>≈ 3 h 10</b><span>en total</span></div></div></div>
  ${agendaHtml(WEEK.map(d=>({...d,past:d.past,s:d.s.map(s=>({...s,prop:null}))})),{addOnRest:true})}</div>`,
 notes:{tags:[['out','Sale: «Semana que se repite»'],['new','Rutina: se cuentan las semanas']],p:['Un programa de una sola semana base se llama <b>rutina</b>. Se navega igual que uno por bloques: «Semana 7», con flechas para ver qué hiciste las semanas anteriores. Sin frases que expliquen que la semana se repite: se ve.','La cinta marca las semanas especiales de sus ciclos (la descarga cada 4, en verde) sin texto.','En los descansos aparece «+ Sesión».'],
  listTitle:'Avisos que salen',list:['«Semana que se repite» como título, subtítulo y línea de alcance («Una semana que se repite»).','«Semana que se repite · ciclo N» en la cabecera y en Inicio: pasa a «Semana N».','«Ciclo N: puedes planificar una semana de descarga…» (recordatorio fijo en la cabecera).','El aviso «Ciclo activo: …. Ya puedes programar sus sesiones.» (snackbar y notificación).']}});
scene({tab:'semana',id:'s-empty',title:'Semana sin sesiones',html:()=>`${weekTop()}<div class="scroll" style="padding-top:100px">${weekHead(5,'int','5 – 9 nov',false)}
  <div class="mod" style="text-align:center;padding:26px 20px"><div class="syne" style="font-size:18px;font-weight:700">Esta semana está vacía</div><p class="body" style="margin:8px 0 16px">Copia una semana anterior o añade sesiones día a día.</p>
  <button class="pill pri sm" style="width:100%">${ic('copy',16)}Copiar la semana 4</button><button class="pill sec sm" style="width:100%;margin-top:8px">${ic('plus',16)}Añadir sesión</button></div></div>`,
 notes:{p:['Hoy una semana vacía muestra siete filas de «Descanso». La propuesta da la salida útil: copiar la semana anterior (ya existe) o añadir una sesión.']}});

/* ================= PROGRESO ================= */
const progTop=()=>topbar('Progreso',{dot:true});
function scopeChips(on='Programa'){return `<div style="display:flex;gap:6px;margin-bottom:12px;overflow:hidden">${['Programa','Bloque 2','Semana 4'].map(x=>`<span class="chip ${x===on?'on':''}">${x}</span>`).join('')}</div>`}
function liftRows(list=LIFTS){return list.map(l=>`<div class="lift"><div style="min-width:0"><strong>${l.n}</strong><span class="st ${l.st}">${l.st==='up'?ic('trendUp',13)+'Sube':l.st==='flat'?ic('flat',13)+'Estancado':l.st==='down'?ic('trendDown',13)+'Baja':'Sin tendencia'}</span><span style="margin-left:6px">${l.sess}</span></div>
  <div>${spark(l.s,l.st==='up'?'var(--done)':l.st==='flat'?'var(--ene)':l.st==='down'?'var(--danger)':'#888')}</div>
  <div class="val"><b>${kg(l.now)}${l.now?'<small style="font:600 11px Inter;color:var(--faint)"> kg</small>':''}</b><em class="${l.st}">${l.d==null?'':l.d>0?'+'+kg(l.d):l.d<0?'−'+kg(-l.d):'±0'}</em></div></div>`).join('')}
scene({tab:'progreso',id:'g-summary',title:'Resumen y fuerza',html:()=>`${progTop()}<div class="scroll" style="padding-top:100px">${scopeChips()}
  <div class="mod"><div class="ey">En 4 semanas</div><div class="headline">Subes en 2 de 4 básicos. Peso muerto rumano lleva 3 semanas parado y el press militar baja.</div>
  <div class="kpis"><div class="kpi"><small>Cumplimiento</small><b>92<span style="font:600 12px Inter"> %</span></b><span>12 de 13 sesiones</span></div><div class="kpi"><small>Series</small><b class="tn">212</b><span>de 264 planificadas</span></div><div class="kpi"><small>Tonelaje</small><b class="tn">42,4<span style="font:600 12px Inter"> t</span></b><span>+9 % vs bloque 1</span></div></div></div>
  <div class="mod"><div class="mod-h"><span class="ey">Fuerza · e1RM</span><button class="act faint">Todos${ic('fwd',16)}</button></div>${liftRows()}<div class="cap" style="margin-top:10px">e1RM = repetición máxima estimada de tu mejor serie de cada sesión.</div></div></div>`,
 notes:{tags:[['new','Nuevo: pestaña Progreso'],['new','Nuevo cálculo: estancamiento'],['out','Sale: «Calibrar», análisis del plan']],
  p:['La pestaña abre con <b>una frase</b> que resume el estado y tres cifras: cumplimiento, series hechas frente a planificadas y tonelaje. Alcance: programa, bloque o semana.',
     '<b>Fuerza</b>: cada ejercicio principal con su e1RM actual, el cambio desde el inicio, una línea por sesión y su estado. Los datos ya existen (<code>ProgramProgressReport.lifts</code> y la línea de <code>ExerciseProgressionAnalytics</code>).',
     '<b>Estancado</b> y <b>Baja</b> son un cálculo nuevo (no existe hoy). Propuesta: «Estancado» si el mejor e1RM de las 3 últimas sesiones del ejercicio no supera en más de un 1 % al de las 3 anteriores, con al menos 3 semanas de datos; «Baja» si cae más de un 2,5 %. Con menos de 4 sesiones: «Sin tendencia», sin inventar.'],
  listTitle:'Qué ejercicios salen aquí',list:['Los marcados como principales en el programa (rol del ejercicio). Si no hay roles, los 5 con más series.','«Todos» abre la lista completa, con buscador.']}});
scene({tab:'progreso',id:'g-work',title:'Trabajo total',scrollTo:0,html:()=>{const mx=72;return `${progTop()}<div class="scroll" style="padding-top:100px">${scopeChips()}
  <div class="mod"><div class="mod-h"><span class="ey">Series duras por semana</span><span class="cap tn">212 / 264</span></div>
  <div class="bars">${WORK.map(w=>`<div class="b"><div class="plan" style="height:${w.plan/mx*100}%"></div><div class="real" style="height:${w.real/mx*100}%;background:${w.now?'var(--mus)':PH[PROGRAM.weeks[w.w-1].ph].c};opacity:${w.now?1:.85}"></div></div>`).join('')}</div>
  <div class="bars-l">${WORK.map(w=>`<span class="${w.now?'on':''}">S${w.w}</span>`).join('')}</div>
  <div class="legend"><span><i class="dot" style="background:var(--mus)"></i>Hechas</span><span><i class="dot" style="border:1px dashed #fff6"></i>Planificadas</span></div>
  <div class="cap" style="margin-top:10px">Semana 3: 14 series menos de lo planificado (faltó la sesión del sábado).</div></div>
  <div class="mod"><div class="ey">Trabajo acumulado</div><div class="kpis"><div class="kpi"><small>Tonelaje</small><b class="tn">42,4 t</b><span>kg × reps</span></div><div class="kpi"><small>Repeticiones</small><b class="tn">1.986</b><span>efectivas</span></div><div class="kpi"><small>Tiempo</small><b class="tn">12 h 40</b><span>13 sesiones</span></div></div>
  <div class="div"></div><div class="mod-h"><span class="cap">Tonelaje por semana</span><span class="cap tn">t</span></div>
  <div style="display:flex;gap:6px;align-items:flex-end;height:44px;margin-top:6px">${WORK.map(w=>`<div style="flex:1;height:${Math.max(w.t/13*100,3)}%;border-radius:4px;background:${w.t?'#f2eee6':'#ffffff10'};opacity:${w.now?1:.55}"></div>`).join('')}</div></div></div>`},
 notes:{tags:[['new','Nuevo: trabajo hecho frente al plan']],p:['Lo que se hizo de verdad cada semana, sobre lo planificado en punteado. Así se ve a la vez la carga del bloque y lo que se dejó de hacer.','<b>Serie dura</b> = serie de trabajo registrada (sin calentamiento ni aproximación), la misma convención que el conteo de volumen.','El tonelaje usa solo series con carga externa; las de peso corporal cuentan en series y repeticiones.'],
  list2Title:'De dónde sale',list2:['Registros de sesión del programa (<code>WorkoutLog</code>), por semana del ciclo en curso.','Plan: series de la semana materializada, incluidas las opcionales confirmadas.']}}) ;
scene({tab:'progreso',id:'g-volume',title:'Volumen por músculo',html:()=>{const mx=26;return `${progTop('Semana 4')}<div class="scroll" style="padding-top:100px">${scopeChips('Semana 4')}
  <div class="mod"><div class="mod-h"><span class="ey">Series por músculo</span><div class="seg" style="padding:3px;width:150px"><button class="on" style="height:30px;font-size:12px">Hecho</button><button style="height:30px;font-size:12px">Plan</button></div></div>
  <button class="act faint" style="margin-top:2px">${ic('sliders',16)}Rangos: perfil del asistente${ic('fwd',14)}</button>
  ${VOL.map(v=>{const c=v.done<v.mev||v.done>v.mrv?'var(--danger)':'var(--ink)';return `<div class="vrow"><div class="vrow-h"><strong>${v.m}</strong><b>${v.done}<span class="cap"> / ${v.plan}</span></b></div>
  <div class="vbar"><div class="z" style="left:${v.mev/mx*100}%;width:${(v.mrv-v.mev)/mx*100}%"></div><div class="f" style="width:${Math.max(v.done/mx*100,1)}%;background:${c};opacity:.85"></div><div class="tk" style="left:${v.plan/mx*100}%"></div></div>
  ${v.done<v.mev?`<div class="cap" style="color:var(--danger)">${v.plan===0?'No hay trabajo directo en el plan':'Por debajo del mínimo útil ('+v.mev+')'}</div>`:''}</div>`}).join('')}
  <div class="legend"><span><i class="dot" style="background:#43d18c55"></i>Rango útil</span><span><i style="width:2px;height:12px;background:#f2eee6aa;display:inline-block"></i>Plan</span><span>Indirectas: ½</span></div></div>
  <div class="mod tight"><button class="act faint" style="width:100%;justify-content:space-between">Ver lo que has hecho en 4 semanas${ic('fwd',16)}</button></div></div>`},
 notes:{tags:[['keep','Se queda: series por músculo'],['new','Nuevo: hecho frente a plan'],['out','Sale: «Calibrar»']],
  p:['La vista actual cuenta lo <b>planificado</b>. Para progreso importa lo <b>hecho</b>: la barra es lo registrado, la marca blanca lo planificado y la franja verde el rango útil del músculo. «Plan» enseña la vista de hoy.',
     'Alcances como hoy (programa, bloque, semana) con «Semana 5» para ver la siguiente: sustituye a «Próxima semana · series según el plan» de la portada.',
     'Al tocar un músculo se despliega su desglose (cabezas y ejercicios), como ahora. El conmutador de indirectas pasa a una línea de la leyenda.',
     'Arriba, «Rangos» dice de dónde salen los rangos y abre su hoja. Reemplaza a «Tu volumen · Objetivos de planificación», a la invitación «Sin calibrar» y al botón «Calibrar».']}});
scene({tab:'progreso',id:'g-adh',title:'Cumplimiento, esfuerzo y molestias',html:()=>`${progTop()}<div class="scroll" style="padding-top:100px">${scopeChips()}
  <div class="mod"><div class="mod-h"><span class="ey">Cumplimiento</span><span class="num" style="font-size:20px">92<small>%</small></span></div>
  <div class="adh" style="grid-template-columns:repeat(6,1fr)">${[[1,1,1,1,1],[1,1,1,1,1],[1,1,1,1,0],[1,1,2,2,2],[3,3,3,3,3],[3,3,3]].map((w,i)=>`<div>${w.map(x=>`<i class="${x===1?'ok':x===0?'miss':'fut'}"></i>`).join('')}<span>S${i+1}</span></div>`).join('')}</div>
  <div class="div"></div><div class="cap">Más omitidos: <b style="color:var(--muted)">Elevaciones laterales</b> (4 veces) · <b style="color:var(--muted)">Gemelo de pie</b> (3)</div></div>
  <div class="mod"><div class="mod-h"><span class="ey">Esfuerzo registrado</span><span class="cap">RIR medio</span></div>
  ${[['Semana 1','2,1','2'],['Semana 2','1,8','2'],['Semana 3','1,2','2'],['Semana 4','1,0','1']].map(r=>`<div class="ratio" style="grid-template-columns:1fr auto auto"><span>${r[0]}</span><span class="cap">plan ${r[2]}</span><b class="tn" style="font:700 14px Syne;${r[1]==='1,2'?'color:var(--ene)':''}">${r[1]}</b></div>`).join('')}
  <div class="cap" style="margin-top:8px">En la semana 3 entrenaste más cerca del fallo de lo pedido. Si se repite con cargas iguales, es señal de fatiga.</div></div>
  <div class="mod"><div class="mod-h"><span class="ey">Molestias</span><span class="cap">4 semanas</span></div>
  <div class="ratio" style="grid-template-columns:1fr auto"><span>Hombro derecho</span><b class="tn">3</b><span class="cap" style="grid-column:1/-1">Coincide con press militar (2) y fondos (1)</span></div>
  <div class="ratio" style="grid-template-columns:1fr auto"><span>Lumbar</span><b class="tn">1</b><span class="cap" style="grid-column:1/-1">Coincide con peso muerto rumano (1)</span></div></div>
  <div class="mod"><div class="mod-h"><span class="ey">Decisiones del sistema</span><button class="act faint">Todas${ic('fwd',16)}</button></div><div class="tl">${DECISIONS.slice(0,3).map(d=>`<div><strong>${d.h}</strong><span>${d.t} · ${d.d}</span></div>`).join('')}</div></div></div>`,
 notes:{tags:[['keep','Se quedan: molestias y decisiones'],['new','Nuevo: RIR medio frente al plan']],p:['<b>Cumplimiento</b>: una columna por semana y un cuadro por sesión (hecha, faltó, futura). Las semanas futuras no penalizan, igual que hoy. Debajo, los ejercicios que más se saltan (ya existe en <code>AdherenceAnalytics</code>).',
  '<b>Esfuerzo registrado</b>: RIR medio de la semana frente al pedido. Junto al e1RM es la mejor señal de fatiga que tenemos sin inventar: misma carga con menos RIR = peor recuperación.',
  '<b>Molestias</b> y su asociación con ejercicios salen del motor de molestias (hoy en el resumen de Volumen).','<b>Decisiones</b>: las tres últimas en línea de tiempo; «Todas» abre la hoja de siempre. Sustituye a la cifra suelta «Decisiones 0» de la portada.']}});
scene({tab:'progreso',id:'g-lift',title:'Detalle de un ejercicio',html:()=>{const v=[127,128,128.5,127.5,128,128,127.5,128];const w=330,h=130,mn=126,mx=130;const pts=v.map((x,i)=>[14+i*(w-28)/(v.length-1),h-18-(x-mn)/(mx-mn)*(h-36)]);return `${progTop()}<div class="scroll" style="padding-top:100px">${liftRows(LIFTS.slice(0,3))}</div>${sheet(`<div class="ey">Peso muerto rumano</div><h3 style="margin-top:6px">Estancado 3 semanas</h3><div class="sub">El mejor e1RM de las 3 últimas sesiones (128 kg) no supera al de las 3 anteriores (128,5 kg).</div>
  <svg width="100%" viewBox="0 0 ${w} ${h}" style="margin-top:4px" aria-label="e1RM por sesión"><line x1="10" x2="${w-10}" y1="${h-18}" y2="${h-18}" stroke="#ffffff14"/>${[127,128,129].map(y=>`<line x1="10" x2="${w-10}" y1="${h-18-(y-mn)/(mx-mn)*(h-36)}" y2="${h-18-(y-mn)/(mx-mn)*(h-36)}" stroke="#ffffff0a"/><text x="${w-8}" y="${h-21-(y-mn)/(mx-mn)*(h-36)}" fill="#8e8980" font-size="9" text-anchor="end" font-family="Inter">${y}</text>`).join('')}
  <rect x="${pts[5][0]-8}" y="6" width="${pts[7][0]-pts[5][0]+16}" height="${h-24}" rx="8" fill="#f7cf7314"/><path d="${pts.map((p,i)=>(i?'L':'M')+p[0]+' '+p[1]).join(' ')}" fill="none" stroke="#f7cf73" stroke-width="2"/>${pts.map(p=>`<circle cx="${p[0]}" cy="${p[1]}" r="3" fill="#111" stroke="#f7cf73" stroke-width="1.6"/>`).join('')}
  ${pts.map((p,i)=>i%2===0?`<text x="${p[0]}" y="${h-4}" fill="#8e8980" font-size="9" text-anchor="middle" font-family="Inter">S${Math.floor(i/2)+1}</text>`:'').join('')}</svg>
  <div class="mgroup">Mejores series</div>
  <div class="ratio" style="grid-template-columns:1fr auto auto"><span>Hoy</span><span class="cap">100 kg × 8 · RIR 2</span><b class="tn" style="font:700 14px Syne">128</b></div>
  <div class="ratio" style="grid-template-columns:1fr auto auto"><span>Lun 20</span><span class="cap">100 kg × 8 · RIR 3</span><b class="tn" style="font:700 14px Syne">128,5</b></div>
  <div class="mgroup">Lectura</div><p class="body" style="margin:0">Misma carga y mismas repeticiones con un RIR menos: cuesta más que hace dos semanas. Puede ser fatiga acumulada; la descarga de la semana 6 debería ayudar.</p>
  <div style="display:flex;gap:8px;margin-top:16px"><button class="pill sec sm" style="flex:1">Historial completo</button><button class="pill sec sm" style="flex:1">Cambiar ejercicio</button></div>`)}`},
 notes:{tags:[['new','Nuevo: hoja de ejercicio']],p:['Toca un ejercicio y se abre su línea por sesión, con la ventana de estancamiento sombreada, sus mejores series y una lectura en lenguaje claro.','La lectura solo junta hechos que existen (carga, repeticiones, RIR y fase del plan). No diagnostica ni promete.','«Historial completo» lleva a la ficha del ejercicio que ya existe (<code>ExerciseProgress</code>, ventanas de 1 mes a todo).'],
  list2Title:'Para decidir',list2:['¿Ofrecer una acción directa ante estancamiento (p. ej. «Cambiar ejercicio» o «Bajar 5 % y reconstruir»)? Propongo solo «Cambiar ejercicio» por ahora: la otra crearía lógica de progresión nueva.']}});
scene({tab:'progreso',id:'g-ranges',title:'Hoja de rangos de volumen',html:()=>`${progTop('Semana 4')}<div class="scroll" style="padding-top:100px"></div>${sheet(`<h3>Rangos de volumen</h3><div class="sub">Las series semanales por músculo que KPKN usa para colorear el volumen y preparar propuestas.</div>
  <div class="mgroup">De dónde salen</div>
  ${mi('spark','Perfil del asistente','Lo que respondiste al empezar: estilo, técnica, constancia, fuerza y movilidad')}
  <div class="mgroup">Cambiarlos</div>
  ${mi('sliders','Elegir mis propios rangos','Mínimo y máximo por músculo, con la referencia general al lado')}
  ${mi('edit','Revisar mis respuestas','Abre las mismas preguntas del asistente; cambia el perfil de todos tus programas')}
  <div class="mgroup">Ajustar con tus datos</div>
  ${mi('history','Propuestas del ciclo 1','2 músculos con cambios propuestos tras 5 semanas respondidas · Revisar')}
  <button class="act faint" style="margin-top:8px">Usar la referencia general</button>`)}`,
 notes:{tags:[['out','Sale: cuestionario «Calibrar» duplicado'],['keep','Se quedan: rangos propios y propuestas']],p:['Una sola hoja para todo lo de rangos. Dice de dónde salen, deja elegir rangos propios por músculo (ya existe) y muestra las propuestas basadas en tus semanas (ya existe).','«Revisar mis respuestas» no abre un cuestionario nuevo: reutiliza el paso del asistente, que es la misma función de cálculo. Ver la asesoría en Decisiones.']}});
scene({tab:'progreso',id:'g-empty',title:'Programa recién empezado',html:()=>`${progTop()}<div class="scroll" style="padding-top:100px">${scopeChips()}
  <div class="mod"><div class="ey">Semana 1</div><div class="headline">Aún no hay tendencia. Con 2 sesiones de cada ejercicio empezamos a comparar.</div>
  <div class="kpis"><div class="kpi"><small>Cumplimiento</small><b>2/5</b><span>sesiones</span></div><div class="kpi"><small>Series</small><b class="tn">34</b><span>de 64</span></div><div class="kpi"><small>Tonelaje</small><b class="tn">6,1 t</b><span>esta semana</span></div></div></div>
  <div class="mod"><div class="ey">Fuerza · e1RM</div>${liftRows(LIFTS.map(l=>({...l,st:'new',d:null,s:l.s.slice(0,1),sess:'1 sesión'})).slice(0,4))}</div></div>`,
 notes:{p:['Sin datos suficientes, la pestaña lo dice y enseña lo que sí hay (sesiones, series, tonelaje y la primera marca). Nada de gráficos vacíos ni porcentajes inventados.']}});
