/* ================= PROGRAM DETAIL · FICHAS, VIDRIO Y 4 SEMANAS ================= */
// Glifos de patrón de movimiento (los de la ficha del asistente), en la retícula 24 de KPKN Line.
Object.assign(P,{
 gSquat:'<path d="M4 6h16"/><path d="M9 6v4l3 3-3 6M15 6v4l-3 3 3 6"/>',
 gPush:'<path d="M4 17h16M12 17V6M8 10l4-4 4 4"/>',
 gPull:'<path d="M4 6h16M12 6v11M8 13l4 4 4-4"/>',
 gHinge:'<path d="M4 19h16M8 19l4-8 7-4"/>',
 gRest:'<path d="M8 12h8"/>'});
// La barra nueva: atrás y las tres pestañas, en vidrio con desenfoque. ⋯ baja a la fila del nombre.
function topbar2(on){return `<div class="topbar"><button class="fb glass" aria-label="Atrás">${ic('back')}</button><div class="ftabs glass" role="tablist">${['Semana','Progreso','Estructura'].map(x=>`<button role="tab" aria-selected="${x===on}" class="${x===on?'on':''}">${x}</button>`).join('')}</div></div>`}
const pdTitle=()=>`<div class="pd-tl"><div class="pname">${PROGRAM.name}</div><button class="ib pd-more" aria-label="Más opciones del programa, hay pendientes">${ic('more')}<i></i></button></div>`;
const pdFix=html=>html.replace(topbar('Semana'),topbar2('Semana')).replace(topbar('Progreso',{dot:true}),topbar2('Progreso')).replace(`<div class="pname">${PROGRAM.name}</div>`,pdTitle());
const pdHtml=id=>SCENES.find(s=>s.id===id).html();

scene({tab:'programas',id:'p-head',title:'Arriba: ⋯ junto al nombre',scrollTo:0,html:()=>pdFix(pdHtml('p-today')),
 notes:{tags:[['move','⋯ baja junto al nombre'],['fix','Pestañas sin «…»']],
  p:['La barra flotante se queda con <b>← y las tres pestañas</b>. Sin ⋯ al lado, «Semana», «Progreso» y «Estructura» ganan 56 px y no se cortan ni con el texto al 130 %.',
     '⋯ baja a la fila del nombre del programa, a la derecha, con su punto naranja cuando hay pendientes. Abre el mismo menú del programa de siempre.']}});

scene({tab:'programas',id:'p-blur',title:'Al bajar: vidrio con desenfoque',scrollTo:300,html:()=>pdFix(pdHtml('p-today')),
 notes:{tags:[['fix','Desenfoque real, como la barra inferior']],
  p:['Hoy las pestañas y los botones flotantes son transparentes: Program Detail no le da a la barra nada que desenfocar, así que el vidrio cae a su versión sin desenfoque.',
     'Aquí la barra es el mismo vidrio que la barra inferior de la app: lo que pasa por debajo (la portada, la agenda) se ve difuminado y oscurecido, y las pestañas se leen siempre.',
     'En el código: el contenido de Program Detail pasa a ser fuente del desenfoque (como en Inicio) y la barra lo usa.']}});

/* --- Progreso: la entrada a tus 4 semanas --- */
// Las 4 semanas de calendario que terminan hoy (lun 6 – dom 2 nov). d = hecha · m = saltada · t = hoy.
const PD_CAL=[['d','d','','d','d','d',''],['d','d','','d','d','d',''],['d','m','','d','m','',''],['d','d','t','','','','']];
const pdEntry=()=>`<div class="mod"><button class="pd-entry"><div class="tx"><strong>Tus últimas 4 semanas</strong><span>14 de 16 sesiones · sentadilla +7,5 kg · 2 récords</span></div><div class="pd-dots" aria-hidden="true">${PD_CAL.flat().map(c=>`<i class="${c==='t'?'':c}"></i>`).join('')}</div>${ic('fwd',18)}</button></div>`;
const PD_OLD_LINK=`<div class="mod tight"><button class="act faint" style="width:100%;justify-content:space-between">Ver lo que has hecho en 4 semanas${ic('fwd',16)}</button></div>`;
scene({tab:'programas',id:'p-progress',title:'Progreso: la entrada',scrollTo:4000,html:()=>pdFix(pdHtml('g-volume')).replace(PD_OLD_LINK,pdEntry()),
 notes:{tags:[['fix','Sustituye al enlace']],
  p:['El enlace «Ver lo que has hecho en 4 semanas» abría otra vez gráficos de volumen. Se sustituye por una tarjeta que dice qué hay dentro: <b>«Tus últimas 4 semanas»</b>, con lo más importante en una línea y un mini calendario de los 28 días.',
     'Abre una página propia, no el panel de volumen.']}});

/* --- Tus últimas 4 semanas --- */
const PD_REC=[['Sentadilla trasera','142,5 kg × 5 · mié 22','Mejor serie'],['Press banca','e1RM 117 kg · lun 27','Mejor marca'],['Remo con barra','72,5 kg × 8 · mié 15','Mejor serie']];
const PD_MUS=[['Dorsales','15 series por semana · rango 8–22','En rango',0],['Cuádriceps','14 por semana · rango 8–20','En rango',0],['Pectorales','12 por semana · rango 8–20','En rango',0],['Deltoides','16 por semana · rango 6–24','En rango',0],['Isquiosurales','9 por semana · rango 6–16','Justo',0],['Tríceps','7 por semana · mínimo útil 6','Por debajo',1],['Pantorrillas','Sin trabajo directo en el plan','Sin series',1]];
function pd4w(){const mx=72;return `<div class="topbar"><button class="fb glass" aria-label="Atrás">${ic('back')}</button></div><div class="scroll pd-page">
  <div class="ey">${PROGRAM.name} · semanas 1 a 4</div><div class="pd-h1" style="margin-top:8px">Tus últimas 4 semanas</div><div class="cap">6 – 29 oct · la semana 4 sigue en curso</div>
  <p class="pd-lead">Cumpliste <b>14 de 16 sesiones</b>. Sentadilla y banca suben; <em>el press militar baja desde hace 2 semanas.</em></p>
  <div class="pd-kpis"><div><b><span data-count="14">0</span><small>/16</small></b><span>sesiones</span></div><div><b><span data-count="212">0</span><small>/264</small></b><span>series duras</span></div><div><b><span data-count="42.4" data-dec="1">0</span><small>t</small></b><span>tonelaje</span></div><div><b>12 h 40</b><span>entrenando</span></div></div>
  <div class="pd-sec">Calendario<span>2 saltadas</span></div>
  <div class="mod"><div class="pd-cal">${['L','M','X','J','V','S','D'].map(d=>`<span>${d}</span>`).join('')}${PD_CAL.flat().map(c=>`<i class="${c}"></i>`).join('')}</div>
  <div class="pd-calk"><em>Hecha</em><em class="m">Saltada</em><span style="display:inline-flex;align-items:center;gap:6px"><i style="width:10px;height:10px;border-radius:3px;box-shadow:inset 0 0 0 2px var(--mus);display:inline-block"></i>Hoy</span></div></div>
  <div class="pd-sec">Fuerza<span>mejor serie estimada</span></div>
  <div class="mod tight">${LIFTS.slice(0,4).map(l=>`<div class="pd-lift"><div style="min-width:0"><strong>${l.n}</strong><span>${String(l.now).replace(".",",")} kg · ${l.sess}</span></div><b class="${l.st}">${l.d>0?'+'+fmtN(l.d)+' kg':l.d<0?'−'+fmtN(-l.d)+' kg':'='}</b>${spark(l.s,l.st==='down'?'var(--danger)':'var(--ink)')}</div>`).join('')}</div>
  <div class="pd-sec">Récords<span>3 en 4 semanas</span></div>
  <div class="mod tight">${PD_REC.map(r=>`<div class="pd-pr"><span class="md">${ic('trophy',17)}</span><div class="tx"><strong>${r[0]}</strong><span>${r[1]}</span></div><b>${r[2]}</b></div>`).join('')}</div>
  <div class="pd-sec">Semana a semana<span>series duras · hecho / plan</span></div>
  <div class="pd-weeks">${WORK.slice(0,4).map(w=>`<div><strong>S${w.w}${w.now?' · hoy':''}</strong><div class="bar"><i style="height:${w.real/mx*100}%"></i><u style="bottom:${w.plan/mx*100}%"></u></div><b>${w.real}/${w.plan}</b><span>${fmtN(w.t)} t · ${Math.floor(w.m/60)} h ${String(w.m%60).padStart(2,'0')}</span></div>`).join('')}</div>
  <div class="pd-sec">Músculos<span>media por semana</span></div>
  <div class="mod tight">${PD_MUS.map(m=>`<div class="pd-mus"><div class="tx"><strong style="font-weight:600;display:block">${m[0]}</strong><span class="cap">${m[1]}</span></div><em class="${m[3]?'lo':''}">${m[2]}</em></div>`).join('')}</div>
  <div class="pd-sec">Esfuerzo y molestias</div>
  <div class="mod tight"><div class="pd-mus"><div class="tx"><strong style="font-weight:600;display:block">RIR medio 2,1</strong><span class="cap">El plan pedía 2: vas donde toca</span></div><em>Según el plan</em></div>
  <div class="pd-mus"><div class="tx"><strong style="font-weight:600;display:block">Hombro derecho</strong><span class="cap">2 veces, las dos en la semana 3</span></div><em class="lo">Vigilar</em></div></div>
  <div class="pd-sec">Lo que viene</div>
  <div class="mod"><div class="pd-next"><span class="ph" style="background:${PH.int.c}"></span><div><strong>Semana 5 · Intensificación</strong><span>5 sesiones · 56 series · propuesta: +2,5 kg en sentadilla</span></div></div>
  <div class="pd-next" style="margin-top:14px"><span class="ph" style="background:${PH.del.c}"></span><div><strong>Semana 6 · Descarga</strong><span>Mitad de series, RIR 4 · cierra el programa</span></div></div></div>
  <button class="pill sec" style="width:100%;margin-top:22px">${ic('share',18)}Compartir tus 4 semanas</button></div>`}
const pd4wNotes={tags:[['new','Resumen de verdad']],
  p:['Una página que responde «¿cómo me han ido las últimas 4 semanas?» de arriba abajo: una frase con lo importante, cuatro cifras (sesiones, series duras, tonelaje y tiempo) y el calendario de los 28 días con lo hecho y lo saltado.',
     'Después, lo que ganaste: la fuerza de cada básico con su línea, los récords y cada semana con lo hecho frente al plan. Luego cómo quedó cada músculo, en palabras (en rango, justo, por debajo), sin otro gráfico de volumen.',
     'Cierra con el esfuerzo y las molestias, lo que viene en las dos semanas siguientes y «Compartir tus 4 semanas», que abre la historia de Progreso que ya existe.']};
scene({tab:'programas',id:'p-4w',title:'Tus últimas 4 semanas · arriba',after:drawAfter,html:()=>pd4w(),notes:pd4wNotes});
scene({tab:'programas',id:'p-4w-mid',title:'Tus últimas 4 semanas · fuerza y semanas',scrollTo:560,after:drawAfter,html:()=>pd4w(),notes:pd4wNotes});
scene({tab:'programas',id:'p-4w-end',title:'Tus últimas 4 semanas · final',scrollTo:4000,after:drawAfter,html:()=>pd4w(),notes:pd4wNotes});

/* --- La ficha del asistente, reutilizada --- */
function pfArt(days){let s='';for(let y=24;y<=144;y+=30)s+=`<path d="M0 ${y}H300" stroke="#0b0b0b" stroke-opacity=".14"/>`;
  days.forEach((v,i)=>{const x=24+i*38,h=v?34+v*96:14;s+=`<rect x="${x}" y="${150-h}" width="24" height="${h}" rx="12" fill="${v?'#0b0b0b':'none'}" fill-opacity=".86" stroke="#0b0b0b" stroke-width="1.5" stroke-opacity="${v?1:.45}" stroke-dasharray="${v?'':'3 3'}"/>`});
  return `<svg class="art" width="300" height="160" viewBox="0 0 300 160" aria-hidden="true">${s}</svg>`}
function pfDay(d,on){return `<div class="pf-day ${d.rest?'rest':''}"><span class="d">${d.d}</span><span class="c">${ic(d.g||'gRest',20)}</span><div class="tx"><strong>${d.t}</strong>${d.s?`<span>${d.s}</span>`:''}</div>${d.ex?ic(on?'up':'down',18):''}</div>${on&&d.ex?`<div class="pf-exl">${d.ex.map(e=>`<div><span>${e[0]}</span><b>${e[1]}</b></div>`).join('')}</div>`:''}`}
function pfFicha(f){return `<div class="pf" data-pf><button class="pf-x" aria-label="Cerrar">${ic('close')}</button>
  <div class="pf-cover" style="--acc:${f.acc}"><div class="bg"></div>${pfArt(f.art)}<div class="fadeb"></div><div class="ey">${f.ey}</div><div class="t"><h2>${f.name}</h2><div class="meta">${f.meta}</div></div></div>
  <div class="pf-body" style="--acc:${f.acc}"><p class="pf-desc">${f.desc}</p><span class="pf-more">Ver más</span>
  <div class="pf-chips">${f.chips.map(c=>`<span>${c}</span>`).join('')}</div>
  <h4>Ejercicios principales</h4>${f.ex.map(e=>`<div class="pf-ex"><span class="g">${ic(e[0],20)}</span><div class="tx"><strong>${e[1]}</strong><span>${e[2]}</span></div></div>`).join('')}
  <h4>Estructura</h4><div class="pf-tl">${f.blocks.map(b=>`<div><i></i><strong>${b[0]}</strong><span>${b[1]}</span></div>`).join('')}</div>
  <h4>Tu semana</h4>${f.week.map((d,i)=>pfDay(d,i===f.open)).join('')}
  ${f.extra||''}
  <h4>Por qué este programa</h4>${f.why.map(w=>`<div class="pf-why">${ic('check',18)}<span>${w}</span></div>`).join('')}
  <h4>Ten en cuenta</h4>${f.care.map(c=>`<div class="pf-note">${c}</div>`).join('')}
  <div class="pf-attr">${f.attr}</div>${f.after||''}</div></div>
  ${f.cta?`<div class="pf-foot"><p>Podrás modificarlo libremente después.</p><button class="pill pri">${f.cta}</button></div>`:''}`}
const PHAT={acc:'#f49a6e',ey:`${ic('book',13)}PLAN DEL CATÁLOGO`,name:'PHAT',meta:'5 días · ~75 min · Avanzado',art:[.9,.85,0,.7,.75,.7,0],
 desc:'<b>Power Hypertrophy Adaptive Training.</b> Dos días de fuerza con básicos pesados y tres de hipertrofia con más series, en la misma semana. Cada músculo se entrena dos veces: una pesada y otra con volumen.',
 chips:['Fuerza + hipertrofia','Gimnasio completo','Cada músculo 2 × semana'],
 ex:[['gPull','Remo con barra','Potencia · 3 × 3–5'],['gPull','Dominadas lastradas','Potencia · 2 × 6–10'],['gPush','Press banca con mancuernas','Potencia · 3 × 3–5'],['gSquat','Sentadilla','Potencia · 3 × 3–5'],['gHinge','Peso muerto rumano','Potencia · 3 × 5–8'],['gPush','Press militar con mancuernas','Potencia · 3 × 6–10']],
 blocks:[['Potencia','Lunes y martes: pocas reps, cargas altas'],['Descanso','Miércoles'],['Hipertrofia','Jueves a sábado: 8–20 reps']],
 week:[{d:'Lun',g:'gPull',t:'Tren superior · potencia',s:'7 ejercicios · ~75 min',ex:[['Remo con barra','3 × 3–5'],['Dominadas lastradas','2 × 6–10'],['Press banca con mancuernas','3 × 3–5'],['Fondos lastrados','2 × 6–10'],['Press militar con mancuernas','3 × 6–10'],['Curl con barra','3 × 6–10'],['Press francés','3 × 6–10']]},
  {d:'Mar',g:'gSquat',t:'Tren inferior · potencia',s:'6 ejercicios · ~70 min',ex:[]},{d:'Mié',t:'Descanso',rest:true},
  {d:'Jue',g:'gPull',t:'Espalda y hombros · hipertrofia',s:'7 ejercicios · ~80 min',ex:[]},{d:'Vie',g:'gSquat',t:'Tren inferior · hipertrofia',s:'7 ejercicios · ~75 min',ex:[]},
  {d:'Sáb',g:'gPush',t:'Pecho y brazos · hipertrofia',s:'8 ejercicios · ~80 min',ex:[]},{d:'Dom',t:'Descanso',rest:true}],open:0,
 why:['Fuerza y tamaño a la vez, sin alternar programas.','Cada músculo dos veces por semana: una pesada y otra con volumen.','Los días de potencia marcan las cargas de los de hipertrofia.'],
 care:['Exigente: 5 días y sesiones de unos 75 minutos.','Pensado para quien ya entrena con constancia hace más de un año.','Los días de potencia piden descansos largos, de 3 a 5 minutos.'],
 attr:'Layne Norton · Biolayne, 2016.',cta:'Configurar este plan'};
const MOTOR={acc:'var(--done)',ey:`${ic('flag',13)}TU PROGRAMA · ACTIVO`,name:PROGRAM.name,meta:'Músculo · 6 semanas · 3 bloques',art:[.8,.75,.65,0,.85,.3,0],
 desc:'<b>Seis semanas en tres bloques:</b> base con doble progresión, fuerza con top set y back-off, y descarga. Cinco días con dos sesiones el miércoles y movilidad opcional el sábado.',
 chips:['Barra, rack, banco, polea, dominadas','Sesión más larga ≈ 61 min','6 oct – 16 nov'],
 ex:LIFTS.map((l,i)=>[['gSquat','gPush','gHinge','gPush','gPull'][i],l.n,l.now?`${fmtN(l.now)} kg · ${l.sess}`:l.sess]),
 blocks:PROGRAM.blocks.map(b=>[b.name.replace('Bloque ','B'),`Semanas ${b.weeks.join(', ')} · ${b.scheme}`]),
 week:[{d:'Lun',g:'gSquat',t:'Pierna A',s:'6 ejercicios · 58 min',ex:[]},{d:'Mar',g:'gPush',t:'Empuje',s:'6 ejercicios · 52 min',ex:[]},
  {d:'Mié',g:'gPull',t:'Tirón · y Cardio y potencia',s:'5 + 4 ejercicios · 48 + 32 min',ex:SESSION_EX.map(e=>{const p=e.d.split(' · ');return [e.n,p.slice(0,2).join(' · ')]})},
  {d:'Jue',t:'Descanso',rest:true},{d:'Vie',g:'gSquat',t:'Pierna B',s:'6 ejercicios · 61 min',ex:[]},{d:'Sáb',g:'gRest',t:'Movilidad y core',s:'Opcional · 25 min',ex:[]},{d:'Dom',t:'Descanso',rest:true}],open:2,
 extra:`<h4>Cargas y cambios</h4><div class="pf-ex"><span class="g">${ic('target',20)}</span><div class="tx"><strong>Cargas de referencia</strong><span>1 por resolver</span></div></div><div class="pf-ex"><span class="g">${ic('swap',20)}</span><div class="tx"><strong>Remo con barra → remo en máquina</strong><span>22 oct · cambio manual</span></div></div>`,
 why:['Sube tus básicos con una progresión que cambia de método por bloque.','Volumen en el rango útil de cada músculo, con una descarga al final.','Cada propuesta de carga sale de lo que registras.'],
 care:['El miércoles tiene dos sesiones: puedes hacerlas juntas o separadas.','La descarga de la semana 6 baja a la mitad las series: no la saltes.'],
 attr:'Plan propio de KPKN, generado por el asistente el 1 oct.',after:`<button class="pill sec sm" style="width:100%;margin-top:18px">Ver cómo funciona</button>`};
const pfScroll=y=>ph=>{const p=ph.querySelector('[data-pf]');if(p)p.scrollTop=y};
const pfCatNotes={tags:[['fix','Sustituye a la hoja de texto'],['keep','La ficha del asistente']],
  p:['Al tocar un plan del catálogo ya no sale la hoja de texto: sale la <b>misma ficha</b> que usa el asistente para «Elegir este programa», con su portada en el color del objetivo, título, días, duración y nivel.',
     'Debajo, en este orden: la descripción en 4 líneas con «Ver más», los ejercicios principales con su glifo, la estructura, tu semana día por día (se despliega para ver series, reps y RIR), por qué este programa, qué tener en cuenta y la procedencia.',
     'Abajo, fijo: «Podrás modificarlo libremente después.» y <b>«Configurar este plan»</b>, que sigue al configurador de siempre.']};
scene({tab:'programas',id:'p-catalog',title:'Plan del catálogo · ficha',after:pfScroll(0),html:()=>pfFicha(PHAT),notes:pfCatNotes});
scene({tab:'programas',id:'p-catalog-week',title:'Plan del catálogo · tu semana',after:pfScroll(700),html:()=>pfFicha(PHAT),notes:pfCatNotes});
const pfAboutNotes={tags:[['fix','Sustituye a la hoja de texto'],['keep','Todo lo que tenía la hoja']],
  p:['«Sobre el programa» (en ⋯) abre la misma ficha en <b>modo lectura</b>: sin botón de elegir, con tu programa tal como está.',
     'Conserva todo lo que tenía la hoja: la descripción, el material, la sesión más larga, el calendario, las cargas por resolver, los cambios registrados, el origen y «Ver cómo funciona».']};
scene({tab:'programas',id:'p-about',title:'Sobre el programa · ficha',after:pfScroll(0),html:()=>pfFicha(MOTOR),notes:pfAboutNotes});
scene({tab:'programas',id:'p-about-end',title:'Sobre el programa · final',after:pfScroll(4000),html:()=>pfFicha(MOTOR),notes:pfAboutNotes});
