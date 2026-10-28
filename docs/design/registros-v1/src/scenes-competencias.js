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
  <div class="group-l">Tu historial<span style="text-transform:none;letter-spacing:0">4 · 2 disciplinas</span></div>
  <div class="mod"><div class="lbl">Total en competición</div>${totalsChart()}<div class="cap" style="margin-top:4px">+47,5 kg en un año · mejor IPF GL 79,4</div></div>
  <div class="mod tight"><div class="meet"><span class="medal none" style="--ms:46px">${ic('heart',22)}</span><div class="tx"><strong>Media maratón de Valencia<span class="prc">MP</span></strong><span>26 oct 2026 · 21,1 km</span></div><div class="tot"><b>1:38:12</b><em>4:39 /km</em></div></div>${MEETS.slice(1).map(meetRow).join('')}</div>
  <button class="pill sec" style="width:100%">${ic('plus',18)}Registrar una competencia</button></div>`,
 notes:{tags:[['new','Página propia'],['move','Sale de Perfil › Archivo'],['fix','Separa próxima e historial']],
  p:['<b>Una página «Competencias»</b> que reúne lo que hoy está repartido: el archivo de Perfil, el aviso de Inicio, el módulo del programa y la ruta muerta <code>competitions</code>. Se llega desde Perfil, desde la cuenta atrás de Inicio y desde la portada del programa.',
     'Arriba, <b>la próxima</b> con cuenta atrás que cuenta al abrir, portada de líneas «Órbitas» y los tres primeros intentos. Debajo, <b>tu historial</b>: la línea de tu total a lo largo de las competencias y cada una con su medalla, total, puntos y la marca «Récord» si superó a la anterior.',
     'Arregla el texto del recorrido («las próximas y las pasadas»), que hoy no se cumple: la lista mezclaba planificadas y hechas.']}});

scene({tab:'competencias',id:'c-new',title:'Nueva competencia: la disciplina',html:()=>`<div class="scroll" style="padding-top:52px"><div class="ptitle">Competencias</div></div>${sheet(`<h3>Nueva competencia</h3>
  <div class="seg" style="margin:4px 0 12px"><button class="on">Voy a competir</button><button>Ya competí</button></div>
  <div class="mgroup">Fuerza</div><div class="disc">${[['dumb','Powerlifting',1],['bolt','Halterofilia'],['tower','Strongman'],['flag','Calistenia'],['grip','Armwrestling'],['eye','Culturismo']].map(d=>`<button class="${d[2]?'on':''}"><span class="ic">${ic(d[0],22)}</span>${d[1]}</button>`).join('')}</div>
  <div class="mgroup">Resistencia</div><div class="disc">${[['heart','Carrera'],['pin','Trail'],['repeat','Ciclismo'],['drop','Natación'],['swap','Remo'],['target','Triatlón'],['fire','Hyrox'],['bolt','CrossFit']].map(d=>`<button><span class="ic">${ic(d[0],22)}</span>${d[1]}</button>`).join('')}</div>
  <div class="mgroup">Combate</div><div class="disc">${['Boxeo','Kickboxing','Muay thai','BJJ','Lucha','Judo','MMA'].map(d=>`<button><span class="ic">${ic('users',22)}</span>${d}</button>`).join('')}</div>
  <div class="mgroup">Más</div><div class="disc">${[['flag','Escalada'],['bolt','Velocidad'],['up','Saltos'],['target','Tenis'],['target','Pádel'],['users','Equipo'],['plus','Otra']].map(d=>`<button><span class="ic">${ic(d[0],22)}</span>${d[1]}</button>`).join('')}</div>
  <button class="pill pri" style="width:100%;margin-top:18px">Continuar</button>`)}`,
 notes:{tags:[['new','Todas las disciplinas de KPKN'],['new','Dos momentos']],
  p:['«Voy a competir» (próxima) o «Ya competí» (resultado) y la disciplina. Están <b>todas las que KPKN entrena</b> (las 29 del modelo de deportes), agrupadas. Arriba salen primero las de tus programas.',
     'Cada disciplina usa una de <b>seis formas de resultado</b> (pestaña Decisiones): intentos de carga, pruebas con marca, contra el reloj, jueceo en tarima, combates y partidos. Así cada deporte se registra como se vive, sin una pantalla distinta para cada uno.']}});
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

scene({tab:'competencias',id:'c-run-next',title:'Carrera · próxima (maratón)',after:drawAfter,html:()=>`<div class="scroll" style="padding-top:52px">${back('Voy a competir')}
  <div class="next-card" style="margin-top:4px"><div class="art">${coverSvg('ondas','noche',{seed:3})}</div><div class="in" style="padding-top:120px">
   <div class="lbl" style="color:var(--mus)">Maratón de Valencia · 6 dic</div><div class="cd" style="margin-top:6px"><b>38</b><span>días</span></div></div></div>
  <div class="mod"><div class="mod-h"><span class="ey">Tu objetivo</span><button class="act">Cambiar</button></div><div class="big" style="font-size:calc(40px*var(--fs));margin-top:6px">3:29:00</div><div class="cap" style="margin-top:4px">Ritmo 4:57 /km · tu media maratón (1:38:12) predice 3:24–3:31</div></div>
  <div class="mod"><div class="ey">Plan de ritmos</div>${[['0–10 km','5:02 /km','Empieza contenido'],['10–30 km','4:56 /km','Ritmo objetivo'],['30–42,2 km','4:52 /km','Si vas bien']].map(r=>`<div class="ratio" style="grid-template-columns:1fr auto;align-items:center"><span>${r[0]}<span class="cap" style="display:block">${r[2]}</span></span><b class="syne tn">${r[1]}</b></div>`).join('')}</div>
  <div class="mod tight"><div class="rowx"><div class="tx"><strong>Avisos</strong><span>1 semana · 48 h · salida a las 8:15</span></div><span class="toggle on"></span></div></div></div>`,
 notes:{tags:[['new','Contra el reloj']],p:['Para carrera, trail, ciclismo, natación, remo y triatlón. La próxima guarda distancia, objetivo de tiempo y un plan de ritmos por tramos. La predicción usa tu última marca registrada en una distancia parecida (el seguimiento de deportes ya guarda sesiones de carrera).','En trail se suma el desnivel; en triatlón, los tres segmentos y las transiciones.']}});

scene({tab:'competencias',id:'c-run',title:'Carrera · resultado',html:()=>`<div class="scroll" style="padding-top:52px">${back('Ya competí')}
  <div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px">Media maratón de Valencia</div><div class="cap" style="margin-top:4px">26 oct · 21,1 km</div>
  <div class="mgroup">Tu tiempo</div><div class="stepper" style="justify-content:center"><b style="font-size:calc(40px*var(--fs))">1:38:12</b></div>
  <div class="kpis"><div class="kpi"><small>Ritmo</small><b>4:39</b><span>/km</span></div><div class="kpi"><small>General</small><b>1.204</b><span>de 9.880</span></div><div class="kpi"><small>Categoría</small><b>187</b><span>M35</span></div></div>
  <div class="mod" style="margin-top:12px"><div class="mod-h"><span class="ey">Por kilómetro</span><span class="cap">del reloj o a mano</span></div><div class="splits">${[4.52,4.45,4.41,4.40,4.38,4.39,4.37,4.36,4.40,4.38,4.41,4.39,4.36,4.35,4.38,4.42,4.40,4.37,4.33,4.30,4.21].map((v,i)=>`<i class="${i===20?'f':''}" style="height:${Math.round((5-v)/0.8*100)}%"></i>`).join('')}</div><div class="cap" style="margin-top:6px">Último km el más rápido: 4:21</div></div>
  <div class="tot-bar" style="margin-top:0"><div><div class="lbl">Marca personal</div><div class="syne" style="font:800 calc(22px*var(--fs)) Syne">−3:41</div></div><span class="pchip">${ic('spark',12)}Nueva MP</span></div>
  <button class="pill pri" style="width:100%;margin-top:14px">Continuar</button></div>`,
 notes:{tags:[['new','Contra el reloj']],p:['Tiempo (rueda de horas, minutos y segundos), ritmo calculado, posiciones general y por categoría y, si quieres, los parciales por kilómetro (importados del reloj si la sesión está en KPKN, o a mano). Marca personal por distancia automática.','Después: el mismo puesto opcional, fotos, diario y la celebración.']}});

scene({tab:'competencias',id:'c-bb',title:'Culturismo · preparación y resultado',html:()=>`<div class="scroll" style="padding-top:52px">${back('Regional · Men’s Physique')}
  <div class="next-card" style="margin-top:4px"><div class="art">${coverSvg('bruma','noche',{seed:4})}</div><div class="in" style="padding-top:110px"><div class="lbl" style="color:var(--mus)">Faltan</div><div class="cd" style="margin-top:6px"><b>9</b><span>semanas · peak week en la 9</span></div></div></div>
  <div class="mod"><div class="mod-h"><span class="ey">Hacia la tarima</span><span class="cap">de tu pestaña Cuerpo</span></div><div style="display:flex;gap:16px;margin-top:8px"><div><div class="num">78,6<small>kg</small></div><div class="cap">hoy</div></div><div><div class="num" style="color:var(--muted)">75<small>kg</small></div><div class="cap">en tarima</div></div><div><div class="num up" style="font-size:calc(20px*var(--fs))">−0,4<small>kg/sem</small></div><div class="cap">ritmo necesario</div></div></div></div>
  <div class="group-l">Resultado</div>
  <div class="mod tight">${[['Categoría','Men’s Physique · -178 cm'],['Rondas','Prejuicio y final'],['Puesto','2.º de 14']].map(r=>`<div class="ratio"><span>${r[0]}</span><b>${r[1]}</b></div>`).join('')}</div>
  <div class="photos"><i></i><i style="background:linear-gradient(150deg,#2b3340,#121417)"></i><i class="add">${ic('camera',22)}</i></div></div>`,
 notes:{tags:[['new','Jueceo en tarima']],p:['Culturismo y calistenia freestyle. La próxima cuenta las semanas de preparación hasta la peak week y enlaza con tu peso de la pestaña Cuerpo (ritmo de pérdida necesario hasta el peso de tarima). El resultado es categoría, rondas, puesto (o puntuación de jueces en calistenia) y fotos de tarima, protagonistas de su historia.','Las fotos de tarima son de la competencia, no del registro corporal (sigue abierta la pregunta sobre fotos de progreso).']}});

scene({tab:'competencias',id:'c-combat',title:'Combate · resultado',html:()=>`<div class="scroll" style="padding-top:52px">${back('Ya competí')}
  <div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px">Open de BJJ · -82,3 kg</div><div class="cap" style="margin-top:4px">Azul · adulto · pesaje 81,9 kg</div>
  <div class="mgroup">Combates</div><div class="mod tight">${[['w','Octavos','Puntos 6–2'],['w','Cuartos','Sumisión · mataleón · 3:12'],['w','Semifinal','Ventaja'],['l','Final','Puntos 0–2']].map(b=>`<div class="bout"><span class="r ${b[0]}">${b[0]==='w'?'G':'P'}</span><div class="tx"><strong>${b[1]}</strong><span>${b[2]}</span></div>${ic('fwd',16)}</div>`).join('')}</div>
  <button class="act" style="margin-top:4px">${ic('plus',16)}Añadir combate</button>
  <div class="tot-bar"><div><div class="lbl">Balance</div><div class="big" style="font-size:calc(40px*var(--fs))">3–1</div></div>${medal('plata',54)}</div>
  <button class="pill pri" style="width:100%;margin-top:14px">Continuar</button></div>`,
 notes:{tags:[['new','Combates']],p:['Boxeo, kickboxing, muay thai, BJJ, lucha, judo, MMA y armwrestling. Cada combate: ronda, ganado o perdido, método (puntos, KO, sumisión, decisión…) y, si quieres, rival y asalto. En armwrestling se añade el brazo. El balance y la medalla salen solos.','La próxima guarda categoría de peso y pesaje, igual que en powerlifting.']}});

scene({tab:'competencias',id:'c-mixed',title:'Hyrox · pruebas con marca',html:()=>`<div class="scroll" style="padding-top:52px">${back('Ya competí')}
  <div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px">Hyrox Madrid · Open</div>
  <div class="mgroup">Tiempo total</div><div class="big" style="font-size:calc(44px*var(--fs))">1:12:48</div>
  <div class="mod" style="margin-top:12px">${[['8 × 1 km','36:10'],['SkiErg','4:21'],['Sled push','3:48'],['Sled pull','5:02'],['Burpee broad jump','4:40'],['Remo','4:35'],['Farmers carry','1:52'],['Sandbag lunges','4:11'],['Wall balls','5:39']].map(r=>`<div class="ratio" style="grid-template-columns:1fr auto;align-items:center"><span>${r[0]}</span><b class="syne tn">${r[1]}</b></div>`).join('')}</div>
  <div class="cap">Strongman y CrossFit usan este mismo formato: cada prueba con su marca (tiempo, kg, repeticiones o metros) y, si hay, sus puntos.</div></div>`,
 notes:{tags:[['new','Pruebas con marca']],p:['Hyrox, CrossFit y strongman: una lista de pruebas, cada una con su unidad. Para Hyrox la plantilla trae las ocho estaciones y los ocho kilómetros; para CrossFit, los WODs del evento; para strongman, las pruebas que añadas.']}});

scene({tab:'competencias',id:'c-lift',title:'Halterofilia y calistenia lastrada',html:()=>`<div class="scroll" style="padding-top:52px">${back('Ya competí')}
  <div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px">Copa de halterofilia</div><div class="cap" style="margin-top:4px">-81 kg · Sinclair</div>
  ${['Arrancada','Dos tiempos'].map((l,j)=>`<div class="att" style="grid-template-columns:96px repeat(3,1fr)"><span class="ln">${l}</span>${(j?[[130,'ok'],[136,'ok'],[140,'no']]:[[100,'ok'],[105,'no'],[105,'ok']]).map(a=>`<button class="ab ${a[1]}"><b>${a[0]}</b><small>${a[1]==='ok'?'Válido':'Nulo'}</small></button>`).join('')}</div>`).join('')}
  <div class="tot-bar"><div><div class="lbl">Total</div><div class="big" style="font-size:calc(44px*var(--fs))">241<small>kg</small></div></div><div style="text-align:right"><div class="cap">Sinclair</div><b class="syne" style="font-size:calc(20px*var(--fs))">318,6</b></div></div></div>`,
 notes:{tags:[['new','Intentos de carga']],p:['La misma tabla de intentos de powerlifting sirve para halterofilia (arrancada y dos tiempos, Sinclair), competencias de un solo levantamiento y street lifting de calistenia (dominada y fondo lastrados, muscle-up).']}});

scene({tab:'competencias',id:'c-match',title:'Partidos y escalada',html:()=>`<div class="scroll" style="padding-top:52px">${back('Ya competí')}
  <div class="syne" style="font:800 calc(26px*var(--fs))/1.05 Syne;letter-spacing:-.8px">Torneo de pádel del club</div><div class="cap" style="margin-top:4px">Con Álex · 2.ª categoría</div>
  <div class="mgroup">Partidos</div><div class="mod tight">${[['w','Grupo','6–3 6–4'],['w','Cuartos','7–5 6–2'],['l','Semifinal','4–6 6–7']].map(b=>`<div class="bout"><span class="r ${b[0]}">${b[0]==='w'?'G':'P'}</span><div class="tx"><strong>${b[1]}</strong><span class="tn">${b[2]}</span></div></div>`).join('')}</div>
  <div class="group-l">Escalada · bloque</div><div class="mod tight"><div class="ratio"><span>Tops</span><b>4 / 5</b></div><div class="ratio"><span>Zonas</span><b>5 / 5</b></div><div class="ratio"><span>Puesto</span><b>3.º de 22</b></div></div></div>`,
 notes:{tags:[['new','Partidos y tops']],p:['Tenis, pádel y deportes de equipo: partidos con marcador y ronda alcanzada. Escalada: tops, zonas e intentos por bloque o vía, y puesto. Saltos y velocidad usan «contra el reloj» con marca en metros o segundos.']}});
