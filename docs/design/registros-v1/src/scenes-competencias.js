/* ================= COMPETENCIAS ================= */
const NEXT=MEETS[0],LAST=MEETS[1];
const back=(t='',extra='')=>`<div class="backrow"><button class="ib fill" aria-label="Atrás">${ic('back')}</button><span style="flex:1;font-weight:600;font-size:calc(15px*var(--fs))">${t}</span>${extra}</div>`;
function attGrid(state=ATT,{cur=null,pend=false}={}){return ['S','B','D'].map(k=>`<div class="att"><span class="ln">${LIFTN[k]}</span>${state[k].map((a,i)=>{const c=pend&&a[1]==='x'?'pend':a[1];const isCur=cur&&cur[0]===k&&cur[1]===i;
  return `<button class="ab ${c} ${isCur?'cur':''}" aria-label="${LIFTN[k]}, intento ${i+1}: ${kg(a[0])} kg, ${a[1]==='ok'?'válido':a[1]==='no'?'nulo':'pendiente'}"><b>${kg(a[0])}</b><small>${a[1]==='ok'?'Válido':a[1]==='no'?'Nulo':isCur?'Ahora':'—'}</small></button>`}).join('')}</div>`).join('')}
function meetRow(m){return `<div class="meet">${medal(m.medal,46)}<div class="tx"><strong>${m.name}${m.pr?'<span class="prc">RÉCORD</span>':''}</strong><span>${m.date} · ${m.city}</span></div><div class="tot"><b>${kg(m.total)}</b><em>${kg(m.pts)} ${m.ptsF}</em></div></div>`}
function totalsChart(){const v=[505,540,552.5],w=300,h=86;const X=i=>20+i*(w-40)/(v.length-1),mn=490,mx=560,Y=x=>h-12-(x-mn)/(mx-mn)*(h-26);
  return `<svg class="draw" viewBox="0 0 ${w} ${h}" width="100%" aria-hidden="true"><path class="ln" pathLength="1" d="${v.map((x,i)=>(i?'L':'M')+X(i)+' '+Y(x)).join('')}" fill="none" stroke="#f2eee6" stroke-width="2" stroke-linecap="round"/>${v.map((x,i)=>`<circle cx="${X(i)}" cy="${Y(x)}" r="${i===2?5:3.5}" fill="${i===2?'#f49a6e':'#0a0a0a'}" stroke="${i===2?'#f49a6e':'#f2eee6'}" stroke-width="1.6"/><text x="${X(i)}" y="${Y(x)-10}" fill="#8e8980" font-size="10" text-anchor="middle" font-family="Inter">${kg(x)}</text>`).join('')}</svg>`}

scene({tab:'competencias',id:'c-hub',title:'Competencias',after:drawAfter,html:()=>`<div class="scroll" style="padding-top:52px">
  ${back('',`<button class="ib fill" aria-label="Nueva competencia">${ic('plus')}</button>`)}
  <div class="ptitle">Competencias</div>
  <div class="next-card"><div class="art">${coverSvg('orbitas','noche',{seed:4})}</div><div class="in">
    <div class="lbl" style="color:var(--mus)">Próxima</div><div class="cd" style="margin-top:6px"><b data-count="18">0</b><span>días</span></div>
    <div class="syne" style="font:700 calc(21px*var(--fs))/1.15 Syne;margin-top:6px">${NEXT.name}</div><div class="cap" style="margin-top:4px">${NEXT.date} · ${NEXT.city} · ${NEXT.fed} · ${NEXT.cls}</div>
    <div class="openers">${[['S','192,5'],['B','130'],['D','210']].map(o=>`<div><small>${LIFTN[o[0]].toUpperCase()}</small><b>${o[1]}</b><span>1.er intento</span></div>`).join('')}</div>
    <div style="display:flex;gap:8px;margin-top:14px"><button class="pill pri sm" style="flex:1">Ver plan del día</button><button class="pill sec sm" style="flex:1">${ic('scale',16)}Pesaje: 82,4 kg</button></div></div></div>
  <div class="group-l">Tu historial<span style="text-transform:none;letter-spacing:0">3 competencias</span></div>
  <div class="mod"><div class="lbl">Total en competición</div>${totalsChart()}<div class="cap" style="margin-top:4px">+47,5 kg en un año · mejor IPF GL 79,4</div></div>
  <div class="mod tight">${MEETS.slice(1).map(meetRow).join('')}</div>
  <button class="pill sec" style="width:100%">${ic('plus',18)}Registrar una competencia</button></div>`,
 notes:{tags:[['new','Página propia'],['move','Sale de Perfil › Archivo'],['fix','Separa próxima e historial']],
  p:['<b>Una página «Competencias»</b> que reúne lo que hoy está repartido: el archivo de Perfil, el aviso de Inicio, el módulo del programa y la ruta muerta <code>competitions</code>. Se llega desde Perfil, desde la cuenta atrás de Inicio y desde la portada del programa.',
     'Arriba, <b>la próxima</b> con cuenta atrás que cuenta al abrir, portada de líneas «Órbitas» y los tres primeros intentos. Debajo, <b>tu historial</b>: la línea de tu total a lo largo de las competencias y cada una con su medalla, total, puntos y la marca «Récord» si superó a la anterior.',
     'Arregla el texto del recorrido («las próximas y las pasadas»), que hoy no se cumple: la lista mezclaba planificadas y hechas.']}});

scene({tab:'competencias',id:'c-new',title:'Nueva competencia',html:()=>`<div class="scroll" style="padding-top:52px"><div class="ptitle">Competencias</div></div>${sheet(`<h3>Nueva competencia</h3><div class="sub">Dos formas de empezar. Puedes cambiar de una a otra cuando quieras.</div>
  <div class="opt-grid"><button class="opt"><span class="ic">${ic('cal')}</span><strong>Voy a competir</strong><span>La apuntas, preparas intentos y pesaje, y te avisamos</span></button>
  <button class="opt"><span class="ic">${ic('trophy')}</span><strong>Ya competí</strong><span>Registras el resultado en un minuto</span></button></div>
  <div class="mgroup">Disciplina</div>${chips(['Powerlifting','Solo press banca','Solo peso muerto','Otra disciplina'],0)}
  <div class="cap" style="margin-top:10px">«Otra disciplina» (halterofilia, strongman, culturismo, calistenia, armwrestling…) guarda un resultado libre: marca, puesto y fotos.</div>
  <button class="pill pri" style="width:100%;margin-top:18px">Continuar</button>`)}`,
 notes:{tags:[['new','Dos momentos'],['new','Más disciplinas']],
  p:['Hoy solo existe «Registrar powerlifting», que crea directamente el resultado con 100 kg de relleno en cada intento. Ahora hay dos puertas: <b>«Voy a competir»</b> (próxima) y <b>«Ya competí»</b> (resultado). Es la misma ficha: una próxima se convierte en resultado el día de la competencia.',
     '<b>Disciplinas</b>: powerlifting completo (sentadilla, banca y peso muerto), competencias de un solo levantamiento (muy comunes en press banca y peso muerto) y «Otra disciplina» con resultado libre. El asistente de bienvenida ya pregunta «¿Compites?» en esas seis disciplinas: aquí les damos dónde registrar.']}});

scene({tab:'competencias',id:'c-next',title:'Próxima: los datos del día',html:()=>`<div class="scroll" style="padding-top:52px">${back('Voy a competir')}
  <div class="steps"><i class="on"></i><i></i><i></i></div>
  <div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px">¿Dónde compites?</div>
  <div class="field"><small>Nombre</small><div>${NEXT.name}</div></div>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px"><div class="field"><small>Fecha</small><div>dom 16 nov</div></div><div class="field"><small>Hora del pesaje</small><div>08:30</div></div></div>
  <div class="field"><small>Lugar</small><div>${NEXT.city}</div></div>
  <div class="field"><small>Federación · puntos</small><div>AEP (IPF) · IPF GL</div></div>
  <div class="mgroup">Categoría</div>${chips(['-74','-83','-93','-105','-120','+120'],1)}
  <div class="mgroup">División y equipo</div>${chips(['Open','Junior','Máster 1'],0)}<div style="height:6px"></div>${chips(['Classic','Rodilleras','Equipado'],0)}
  <div class="mgroup">Te avisamos</div>
  <div class="rowx"><div class="tx"><strong>Una semana antes</strong><span>Revisar pesaje e intentos</span></div><span class="toggle on"></span></div>
  <div class="rowx"><div class="tx"><strong>48 horas antes</strong><span>Plan del día listo</span></div><span class="toggle on"></span></div>
  <div class="rowx"><div class="tx"><strong>Al empezar</strong><span>Con la hora del pesaje</span></div><span class="toggle"></span></div>
  <button class="pill pri" style="width:100%;margin-top:16px">Continuar</button></div>`,
 notes:{tags:[['keep','Mismos datos'],['new','Hora y avisos visibles'],['fix','Pesaje con teclado']],
  p:['Los mismos datos del paso «Evento» de hoy, en una página que se lee de un vistazo. Categoría, división y equipo pasan a chips.',
     '<b>Hora del pesaje</b> sale a la vista: hoy existe en el modelo (<code>startTime</code>) pero ningún alta la pide, y sin ella nunca salta el aviso posterior al resultado.',
     '<b>Los avisos</b> (una semana, 48 h y al empezar) existen y están activos sin que nadie pueda verlos: ahora son interruptores.',
     'El peso del pesaje deja de ser solo botones ±0,1 kg desde 80 kg: se escribe con el teclado numérico.']}});

scene({tab:'competencias',id:'c-plan',title:'Plan de intentos',html:()=>`<div class="scroll" style="padding-top:52px">${back('Plan de intentos')}
  <div class="steps"><i class="on"></i><i class="on"></i><i></i></div>
  <div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px">Tus nueve intentos</div>
  <div class="cap" style="margin-top:6px">Desde tus marcas: sentadilla 200 · banca 135 · peso muerto 217,5</div>
  <div class="mgroup">Estrategia</div>${chips(['Conservadora','Objetivo','Ambiciosa'],1)}
  ${attGrid({S:[[187.5,'x'],[195,'x'],[202.5,'x']],B:[[127.5,'x'],[132.5,'x'],[137.5,'x']],D:[[205,'x'],[212.5,'x'],[222.5,'x']]},{pend:true})}
  <div class="tot-bar"><div><div class="lbl">Total si entran</div><div class="big" style="font-size:calc(40px*var(--fs))">562,5<small>kg</small></div></div><div style="text-align:right"><div class="cap">IPF GL</div><b class="syne" style="font-size:calc(20px*var(--fs))">80,9</b></div></div>
  <div class="mod" style="margin-top:12px"><div class="mod-h"><span class="ey">Pesaje</span><span class="cap">objetivo ≤ 83 kg</span></div><div style="display:flex;align-items:baseline;gap:8px;margin-top:6px"><span class="num">82,4<small>kg</small></span><span class="pchip" style="background:#43d18c1a;color:var(--done)">${ic('check',12)}Dentro</span></div><div class="cap" style="margin-top:6px">Con tu tendencia de las últimas semanas llegas con 0,6 kg de margen.</div></div>
  <button class="pill pri" style="width:100%;margin-top:6px">Guardar plan</button></div>`,
 notes:{tags:[['keep','Mismo planificador'],['fix','No pisa lo escrito a mano']],
  p:['El planificador de hoy (conservadora 88/94/98 %, objetivo 91/96/100 %, ambiciosa 92/97/103 %) rellena los nueve intentos; tocar uno abre el teclado con ±2,5 kg. Los que cambiaste a mano quedan marcados y <b>cambiar de estrategia ya no los sobrescribe</b> sin preguntar (hoy sí).',
     'El total y los puntos se recalculan en vivo. El pesaje usa la proyección que ya existe (<code>WeighInPlanner</code>).',
     'Arregla: hoy se puede guardar un plan sin intentos y el asistente de resultado no muestra el plan; ahora el resultado parte del plan.']}});

scene({tab:'competencias',id:'c-today',title:'Hoy compites',html:()=>`<div class="scroll" style="padding-top:52px">${back('Hoy compites',`<span class="chip" style="height:28px"><i class="dot" style="background:var(--mus)"></i>En vivo</span>`)}
  <div class="lbl" style="color:var(--mus)">Press banca · 2.º intento</div>
  <div class="big" style="font-size:calc(72px*var(--fs));margin-top:6px">132,5<small>kg</small></div>
  <div class="cap" style="margin-top:4px">Si entra: 3.º a 137,5 · si no: repites 132,5</div>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:16px"><button class="pill" style="height:64px;background:#43d18c;color:#062012;font-size:calc(17px*var(--fs))">${ic('check',22)}Válido</button><button class="pill" style="height:64px;background:#ff9b9226;color:var(--danger);border:1px solid #ff9b9266;font-size:calc(17px*var(--fs))">${ic('close',22)}Nulo</button></div>
  <div class="group-l">La tabla</div>${attGrid({S:[[187.5,'ok'],[195,'ok'],[200,'ok']],B:[[127.5,'ok'],[132.5,'x'],[137.5,'x']],D:[[205,'x'],[212.5,'x'],[222.5,'x']]},{cur:['B',1],pend:true})}
  <div class="mod" style="margin-top:14px"><div class="ey">Calentamiento para el peso muerto</div><div class="cap" style="margin-top:6px;line-height:1.7">60 × 5 · 100 × 3 · 140 × 2 · 170 × 1 · 190 × 1 <span style="color:var(--faint)">(hasta el 92 % del primero)</span></div></div></div>`,
 notes:{tags:[['keep','El «Modo competición» de hoy'],['move','Del programa a la competencia']],
  p:['El modo competición existe, pero dentro de una hoja del programa y sin guardar nada. Ahora vive en la propia competencia el día que toca: el intento actual en grande, dos botones enormes (se usan con tiza y nervios) y lo que viene según entre o no.',
     'Cada toque <b>guarda el resultado</b>: al terminar, la competencia ya está registrada y solo falta el puesto. El calentamiento sale del mismo cálculo de hoy (hasta el 92 % del primer intento).']}});

scene({tab:'competencias',id:'c-log',title:'Registrar el resultado',html:()=>`<div class="scroll" style="padding-top:52px">${back('Ya competí')}
  <div class="steps"><i class="on"></i><i></i><i></i></div>
  <div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px">¿Cómo te fue?</div>
  <div class="cap" style="margin-top:6px">Toca un intento para marcarlo válido o nulo. Mantén pulsado para cambiar el peso.</div>
  ${attGrid()}
  <div class="tot-bar"><div><div class="lbl">Total</div><div class="big" style="font-size:calc(44px*var(--fs))"><span data-count="552.5" data-dec="1">0</span><small>kg</small></div></div><div style="text-align:right"><div class="cap">IPF GL</div><b class="syne" style="font-size:calc(20px*var(--fs));display:block">79,4</b><span class="pchip" style="margin-top:6px">+12,5 kg</span></div></div>
  <button class="pill pri" style="width:100%;margin-top:16px">Continuar</button></div>`,after:drawAfter,
 notes:{tags:[['new','Tres toques por levantamiento'],['fix','El total baja si un válido pasa a nulo']],
  p:['Hoy el registro empieza con 100 kg en cada intento y un ciclo de cuatro estados (pendiente, válido, nulo, saltado) que exige dos toques por intento. Ahora los pesos vienen del plan (o de tus marcas si no hubo plan), un toque alterna <b>válido ↔ nulo</b> y mantener pulsado abre el peso. «Saltado» queda en ese mismo menú.',
     'El total cuenta hacia arriba al cambiar y se compara con tu anterior competencia.',
     '<b>Arregla</b> el fallo del total: hoy, si el último válido de un levantamiento pasa a nulo, el total no baja porque reutiliza el valor guardado.']}});

scene({tab:'competencias',id:'c-place',title:'Tu puesto',html:()=>`<div class="scroll" style="padding-top:52px">${back('Ya competí')}
  <div class="steps"><i class="on"></i><i class="on"></i><i></i></div>
  <div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px">¿Subiste al podio?</div>
  <div class="medals">${['oro','plata','bronce'].map((m,i)=>`<button class="${i===0?'on':''}">${medal(m,64)}${MEDAL[m].n}</button>`).join('')}</div>
  <div class="mgroup">Sin podio</div>
  <div style="display:flex;gap:8px;align-items:center"><div class="field" style="flex:1;margin:0"><small>Puesto</small><div>5.º</div></div><span class="chip" style="height:52px;padding:0 16px">Sin puesto</span></div>
  <div class="mgroup">Algo más (opcional)</div>${chips(['Récord nacional','Mejor levantador','Clasificado'],[])}
  <button class="pill pri" style="width:100%;margin-top:20px">Continuar</button><button class="act faint" style="width:100%;justify-content:center">Saltar</button></div>`,
 notes:{tags:[['keep','Medallas y trofeos'],['fix','Ya no es obligatorio'],['fix','Puesto exacto']],
  p:['Las medallas son discos que giran al elegirlos. Fuera del podio se escribe el puesto real: hoy se guarda «4+» y se muestra como «4.º» aunque quedaras décimo.',
     'Los siete trofeos de hoy pasan a «Algo más», que admite varios. El paso <b>deja de ser obligatorio</b> (hoy no se puede guardar sin él).']}});

scene({tab:'competencias',id:'c-feel',title:'Fotos y cómo te sentiste',html:()=>`<div class="scroll" style="padding-top:52px">${back('Ya competí')}
  <div class="steps"><i class="on"></i><i class="on"></i><i class="on"></i></div>
  <div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px">Para recordarlo</div>
  <div class="mgroup">Cómo te sentiste</div><div class="feel">${['sad','meh','smile','wow','fire'].map((f,i)=>`<button class="${i===4?'on':''}" aria-label="${['Mal','Regular','Bien','Muy bien','En llamas'][i]}">${ic(f,26)}</button>`).join('')}</div>
  <div class="mgroup">Fotos y vídeos</div><div class="photos"><i></i><i style="background:linear-gradient(150deg,#2b3340,#121417)"></i><i class="add">${ic('camera',22)}</i></div>
  <div class="mgroup">Una línea (opcional)</div>
  <div class="field"><small>Lo mejor del día</small><div>El 200 de sentadilla, por fin</div></div>
  <div class="field"><small>Para la próxima</small><div class="cap" style="font-size:calc(15px*var(--fs))">Escribe…</div></div>
  <button class="pill pri" style="width:100%;margin-top:18px">${ic('confetti',18)}Guardar y celebrar</button></div>`,
 notes:{tags:[['keep','Álbum'],['new','Diario liviano']],
  p:['El álbum de hoy (hasta 8 fotos o vídeos) más un <b>diario liviano</b>: una escala de cinco caras y dos líneas opcionales. El modelo ya tiene un diario completo (sensación, estado físico y mental, aprendizajes…) que nadie puede rellenar y que, además, se borra al reabrir el registro: usamos tres de sus campos y dejamos de borrarlo.',
     'Las fotos que quitas se borran del almacenamiento (hoy quedan huérfanas).']}});

scene({tab:'competencias',id:'c-cele',title:'¡Celebración!',after:ph=>{animateIn(ph);ph.querySelector('.cele svg')?.classList.add('go')},html:()=>`<div class="cele">${confetti(30)}${burst()}
  <div style="position:absolute;left:50%;top:30%;transform:translate(-50%,-50%)"><div class="spin-medal">${medal('oro',132)}</div></div>
  <div style="position:absolute;left:24px;right:24px;top:52%;text-align:center">
   <div class="lbl rise" style="color:var(--ene);opacity:0;animation:rise .7s .6s forwards">Oro · Copa de otoño</div>
   <div class="big pop-in" style="font-size:calc(70px*var(--fs));margin-top:10px;animation-delay:.8s"><span data-count="552.5" data-dec="1">0</span><small>kg</small></div>
   <div class="body" style="margin-top:8px;opacity:0;animation:rise .7s 1.1s forwards">Tu mejor total: +12,5 kg desde abril.<br>Sentadilla de 200 kg por primera vez.</div></div>
  <div class="wr-foot" style="bottom:28px"><button class="pill pri" style="flex:1">${ic('share',18)}Compartir</button><button class="pill sec" style="flex:1">Ver ficha</button></div></div>`,
 notes:{tags:[['new','Animación de felicitación']],
  p:['Al guardar el resultado: confeti, un estallido de líneas finas (el lenguaje de las portadas) que se dibuja desde el centro, la medalla que entra girando y el total que cuenta hacia arriba. Dura unos dos segundos; con «movimiento reducido» aparece todo quieto.',
     'Las frases las escribe el propio resultado: mejor total, primera vez que pasas una cifra redonda, récord de un levantamiento, puntos. Sin podio también hay celebración: el foco pasa a tus marcas.',
     '«Compartir» abre las historias de la competencia (pestaña Historias).']}});

scene({tab:'competencias',id:'c-detail',title:'Ficha de competencia',after:drawAfter,html:()=>`<div class="scroll" style="padding-top:0;padding-left:0;padding-right:0">
  <div style="height:230px;position:relative;overflow:hidden">${cover('big','relieve','carbon',{seed:6})}<div style="position:absolute;inset:0;background:linear-gradient(#0000 30%,#000)"></div>
   <div style="position:absolute;top:44px;left:10px;right:10px;display:flex;justify-content:space-between"><button class="fb glass" aria-label="Atrás">${ic('back')}</button><button class="fb glass" aria-label="Más opciones">${ic('more')}</button></div></div>
  <div style="padding:0 16px;margin-top:-56px;position:relative">
   <div style="display:flex;gap:14px;align-items:flex-end">${medal('oro',70)}<div><div class="lbl">21 sep 2026 · Toledo · AEP</div><div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px;margin-top:4px">Copa de otoño</div></div></div>
   <div class="kpis"><div class="kpi"><small>Total</small><b>552,5</b><span>kg</span></div><div class="kpi"><small>IPF GL</small><b>79,4</b><span>puntos</span></div><div class="kpi"><small>Pesaje</small><b>82,6</b><span>kg · -83</span></div></div>
   <div class="group-l">Intentos</div>${attGrid()}
   <div class="group-l">Frente a la anterior</div>
   <div class="mod tight">${[['Sentadilla','200','+5'],['Press banca','135 → 132,5','+0'],['Peso muerto','217,5','+5'],['Total','552,5','+12,5']].map(r=>`<div class="ratio"><span>${r[0]}</span><b class="syne">${r[1]} <span class="${r[2]==='+0'?'flat':'up'}" style="font-size:12px">${r[2]}</span></b></div>`).join('')}</div>
   <div class="group-l">Recuerdos</div><div class="photos"><i></i><i style="background:linear-gradient(150deg,#2b3340,#121417)"></i><i style="background:linear-gradient(150deg,#40302b,#171211)"></i></div>
   <div class="mod" style="margin-top:12px"><div class="ey">${ic('fire',14)} En llamas</div><p class="body" style="margin:8px 0 0">«El 200 de sentadilla, por fin.»</p></div>
   <button class="pill pri" style="width:100%">${ic('share',18)}Compartir en historias</button></div></div>`,
 notes:{tags:[['keep','Ficha de hoy'],['new','Frente a la anterior']],
  p:['Portada de líneas con la medalla encima, tres cifras (total, puntos y pesaje), la tabla de intentos, la comparación automática con la competencia anterior (lo que hoy exige ir a «Comparar» y elegir dos), recuerdos y diario.']}});

scene({tab:'competencias',id:'c-menu',title:'⋯ de una competencia',html:()=>`<div class="scroll" style="padding-top:52px"><div class="ptitle">Copa de otoño</div></div>${sheet(`<div style="display:flex;gap:14px;align-items:center;margin-bottom:6px">${medal('oro',52)}<div><h3>Copa de otoño</h3><div class="cap">21 sep 2026 · 552,5 kg</div></div></div>
  ${mi('edit','Editar resultado','Intentos, puesto, fotos y diario')}${mi('share','Compartir en historias','')}${mi('swap','Comparar con otra','Elige una de tu historial')}
  ${mi('trendUp','Actualizar tus marcas','Usa estos resultados en tu programa')}${mi('upload','Archivar','Sale del historial; no se borra')}${mi('trash','Eliminar','Borra la competencia y sus fotos','danger')}`)}`,
 notes:{tags:[['new','Menú que no existía'],['fix','Archivar archiva']],
  p:['Hoy no hay ningún menú: no se puede borrar ni archivar una competencia. Y el único «archivar» (en el editor del programa) en realidad la <b>borra para siempre</b>. Ahora «Archivar» archiva (estado ARCHIVED, que ya existe) y «Eliminar» pide confirmación y borra también sus fotos.',
     '«Actualizar tus marcas» es la revisión que ya existe (proponer 1RM superiores al programa), con su acceso a la vista.']}});

scene({tab:'competencias',id:'c-marks',title:'Actualizar tus marcas',html:()=>`<div class="scroll" style="padding-top:52px"><div class="ptitle">Copa de otoño</div></div>${sheet(`<div class="ey">Después de competir</div><h3 style="margin-top:6px">Tus marcas suben</h3><div class="sub">Tu programa «Motor y fuerza» usa tus máximos para calcular las cargas. Con estos resultados, las semanas que faltan quedarían así:</div>
  ${[['Sentadilla','195','200'],['Press banca','132,5','132,5'],['Peso muerto','212,5','217,5']].map(r=>`<div class="ratio" style="grid-template-columns:1fr auto auto;align-items:center"><span>${r[0]}</span><span class="cap tn">${r[1]} →</span><b class="syne ${r[1]===r[2]?'':'up'}">${r[2]}</b></div>`).join('')}
  <button class="pill pri" style="width:100%;margin-top:16px">Usar en mi programa</button><button class="act faint" style="width:100%;justify-content:center">Ahora no</button>`)}`,
 notes:{tags:[['keep','Misma revisión']],p:['La hoja «Revisar actualización de marcas» de hoy, con la diferencia de cada levantamiento a la vista antes de confirmar.']}});

scene({tab:'competencias',id:'c-compare',title:'Comparar dos competencias',html:()=>`<div class="scroll" style="padding-top:52px">${back('Comparar')}
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px">${[MEETS[1],MEETS[2]].map(m=>`<div class="mod tight" style="margin:0;text-align:center">${medal(m.medal,40)}<div style="font-weight:600;margin-top:8px;font-size:calc(13px*var(--fs))">${m.name}</div><div class="cap">${m.date}</div></div>`).join('')}</div>
  <div class="mod" style="margin-top:12px">${[['Sentadilla',200,195],['Press banca',135,132.5],['Peso muerto',217.5,212.5],['Total',552.5,540],['IPF GL',79.4,77.9],['Pesaje',82.6,82.1]].map(r=>`<div class="ratio" style="grid-template-columns:1fr 70px 70px;align-items:center"><span>${r[0]}</span><b class="syne tn" style="text-align:right">${kg(r[1])}</b><span class="cap tn" style="text-align:right">${kg(r[2])}</span></div>`).join('')}</div>
  <div class="cap">Toca una competencia de arriba para cambiarla.</div></div>`,
 notes:{tags:[['keep','Comparativa de hoy']],p:['La comparativa existente, en una página con las dos fichas arriba y las cifras alineadas.']}});

scene({tab:'competencias',id:'c-other',title:'Otra disciplina',html:()=>`<div class="scroll" style="padding-top:52px">${back('Ya competí')}
  <div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px">Strongman · Copa local</div>
  <div class="mgroup">Pruebas</div>
  ${[['Yoke','20 m','11,8 s'],['Log press','Máximo','110 kg'],['Peso muerto eje','Repeticiones','9 reps']].map(p=>`<div class="rowx"><div class="tx"><strong>${p[0]}</strong><span>${p[1]}</span></div><b class="syne">${p[2]}</b></div>`).join('')}
  <button class="act" style="margin-top:4px">${ic('plus',16)}Añadir prueba</button>
  <div class="mgroup">Puesto</div><div class="medals">${['oro','plata','bronce'].map((m,i)=>`<button class="${i===1?'on':''}">${medal(m,48)}${MEDAL[m].n}</button>`).join('')}</div>
  <button class="pill pri" style="width:100%;margin-top:18px">Continuar</button></div>`,
 notes:{tags:[['new','Resultado libre']],p:['Para halterofilia, strongman, culturismo, calistenia o armwrestling: pruebas con su marca y unidad (kg, s, m, reps o puesto), medalla, fotos y diario. Usa los campos que el modelo ya tiene (marca, tiempo, distancia, unidad) y que hoy no tienen pantalla.']}});
