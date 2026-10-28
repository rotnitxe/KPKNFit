/* ================= CREAR PROGRAMA ================= */
const CHAPS=['Tú','Dónde y con qué','Qué buscas','Tu semana','Afinar'];
function wzTop(step,half=false){return `<div class="wz-top"><button class="ib fill" aria-label="Atrás">${ic('back')}</button><div class="prog">${CHAPS.map((c,i)=>`<i class="${i<step?'on':i===step&&half?'half':''}"></i>`).join('')}</div><button class="ib" aria-label="Cerrar">${ic('close')}</button></div>`}
const choice=(sym,t,s,on)=>`<button class="choice ${on?'on':''}"><span class="sym">${ic(sym,44)}</span><span class="tx"><strong>${t}</strong>${s?`<span>${s}</span>`:''}</span>${on?`<span class="mark ok">${ic('check',16)}</span>`:''}</button>`;
const wzFoot=(pri='Continuar',sec='')=>`<div class="wz-foot">${sec?`<button class="pill sec" style="flex:0 0 auto">${sec}</button>`:''}<button class="pill pri" style="flex:1">${pri}</button></div>`;
const programsBehind=()=>`<div class="scroll" style="padding-top:52px"><div style="display:flex;justify-content:space-between;align-items:center"><div class="ptitle">Programas</div><button class="pill pri sm">${ic('plus',16)}Nuevo</button></div></div>`;

scene({tab:'crear',id:'cr-new',title:'Nuevo programa (ya aprobado)',html:()=>`${programsBehind()}${sheet(`<h3>Nuevo programa</h3><div class="sub">Elige cómo empezar.</div>
  <div class="opt-grid"><button class="opt wide" style="background:linear-gradient(135deg,#f49a6e22,#ffffff06);border-color:#f49a6e40"><span class="ic" style="color:var(--mus)">${ic('spark')}</span><span class="tx"><strong>A tu medida</strong><span style="display:block">KPKN lo arma con tu material, tus días y tu objetivo</span></span>${ic('fwd',18)}</button>
  <button class="opt"><span class="ic">${ic('book')}</span><strong>Del catálogo</strong><span>Planes conocidos y planes KPKN</span></button><button class="opt"><span class="ic">${ic('upload')}</span><strong>Importar</strong><span>Excel, Word, PDF, texto o una foto</span></button>
  <button class="opt wide"><span class="ic">${ic('plus')}</span><span class="tx"><strong>Desde cero</strong><span style="display:block">Le pones nombre y armas las sesiones tú</span></span>${ic('fwd',18)}</button></div>`)}`,
 notes:{tags:[['keep','Aprobada e implementada'],['fix','La misma hoja en Inicio']],
  p:['La hoja aprobada en el lienzo anterior y ya implementada. Lo nuevo: <b>Inicio abre esta misma hoja</b>. Hoy Inicio abre otra distinta (la biblioteca vieja, sin «A tu medida» y con un «Desde cero» que no pide nombre).',
     'Y la pestaña Entreno, con un programa activo, abre ese programa: «Nuevo» queda a un toque en la lista de Programas (atrás desde el programa) y en el ⋯ de la lista.']}});

scene({tab:'crear',id:'cr-intro',title:'A tu medida · inicio',after:drawAfter,html:()=>`<div class="wz">${wzTop(0)}
  <div style="height:170px;margin:4px -20px 0;position:relative;overflow:hidden">${coverSvg('ondas','carbon',{seed:2})}<div style="position:absolute;inset:0;background:linear-gradient(#0000 40%,#000)"></div></div>
  <h1>Tu programa a medida</h1><p class="lead">Cinco preguntas cortas y KPKN arma tu programa con tu material, tus días y tu objetivo. Unos dos minutos.</p>
  <div class="summary">${CHAPS.map((c,i)=>`<div class="rowx"><span class="mark" style="background:#ffffff0c;color:var(--muted);font:700 12px Syne">${i+1}</span><div class="tx"><strong>${c}</strong><span>${['Experiencia','Lugar y material','Objetivo y si compites','Días, tiempo y energía','Músculos, marcas y cardio · opcional'][i]}</span></div></div>`).join('')}</div>
  <div class="mod" style="margin-top:12px"><div class="mod-h"><span class="ey">Ya sabemos</span><button class="act">Cambiar</button></div><div class="cap" style="margin-top:6px;font-size:calc(13px*var(--fs))">34 años · 1,78 m · 80,4 kg</div></div>
  ${wzFoot('Empezar')}</div>`,
 notes:{tags:[['new','Pantalla completa propia'],['fix','No pide datos de nutrición']],
  p:['«A tu medida» deja de ser el asistente de bienvenida recortado: es una <b>pantalla completa propia</b> con cinco capítulos y una barra de progreso por capítulo. Usa el mismo motor y las mismas preguntas (grafo de pasos, evaluador, generador), así que no hay lógica nueva.',
     'Los datos básicos que ya tenemos <b>no se vuelven a preguntar</b>: se muestran en «Ya sabemos». Hoy, en modo solo entreno, se pide el género y el % de grasa como obligatorios aunque solo sirven para nutrición.',
     'Si dejas el flujo a medias, al volver retoma donde estabas (ya existe el borrador por alcance).']}});

scene({tab:'crear',id:'cr-exp',title:'Capítulo 1 · Tú',html:()=>`<div class="wz">${wzTop(0,true)}<div class="chap">1 · Tú</div><h1>¿Cuánta experiencia tienes?</h1><p class="lead">Decide cuánto volumen y cuánta variedad tendrá tu programa.</p>
  ${choice('spark','Estoy empezando','Menos de 6 meses o nunca con un plan',false)}${choice('repeat','Estoy volviendo','Entrené antes y lo dejé un tiempo',false)}${choice('cal','Ya entreno con constancia','Más de un año con regularidad',true)}${choice('trophy','Tengo experiencia','Varios años, sé lo que me funciona',false)}
  ${wzFoot()}</div>`,
 notes:{tags:[['keep','Mismas cuatro respuestas']],p:['Cada opción con su símbolo animado (los del asistente de bienvenida, sin caja de tarjeta en el producto real) y una línea que dice qué significa. Las preguntas de técnica, constancia, fuerza y movilidad siguen en este capítulo, una por pantalla, con sus tres niveles.']}});

scene({tab:'crear',id:'cr-where',title:'Capítulo 2 · Dónde y con qué',html:()=>`<div class="wz">${wzTop(1,true)}<div class="chap">2 · Dónde y con qué</div><h1>¿Con qué material entrenas?</h1><p class="lead">Marcamos lo típico de un gimnasio. Quita lo que no tengas.</p>
  ${chips(['Gimnasio','En casa','Al aire libre'],0,40)}
  <div class="opt-grid" style="margin-top:14px">${[['dumb','Barra y discos',1],['dumb','Mancuernas',1],['tower','Rack',1],['grip','Poleas',1],['target','Máquinas',1],['link','Bandas',0],['flag','Dominadas',1],['heart','Cardio',1]].map(m=>`<button class="opt" style="min-height:88px;${m[2]?'border-color:#f2eee688;background:#f2eee60c':''}"><span class="ic">${ic(m[0])}</span><strong>${m[1]}</strong><span>${m[2]?'Lo tengo':'No'}</span></button>`).join('')}</div>
  ${wzFoot()}</div>`,
 notes:{tags:[['keep','Mismo material']],p:['Lugar y material en una sola pantalla: el lugar preselecciona el material típico y tú quitas lo que falte. El motor sigue usando el «material efectivo» de hoy.']}});

scene({tab:'crear',id:'cr-goal',title:'Capítulo 3 · Qué buscas',html:()=>`<div class="wz">${wzTop(2,true)}<div class="chap">3 · Qué buscas</div><h1>¿Cuál es tu objetivo?</h1>
  <div class="mgroup">Para todos</div>${choice('dumb','Fuerza y masa muscular','',false)}${choice('heart','Fuerza y cardio','',false)}${choice('spark','Funcional y saludable','',false)}
  <div class="mgroup">Una disciplina</div>${chips(['Powerlifting','Powerbuilding','Culturismo','Calistenia','Halterofilia','Strongman','Armwrestling'],0,38)}
  <div class="mod" style="margin-top:16px;border-color:#f49a6e44"><div class="ey" style="color:var(--mus)">¿Compites?</div>${chips(['Sí, con fecha','Sí, sin fecha','No compito'],0,38)}<div class="field"><small>Fecha y nombre</small><div>16 nov · Open regional</div></div><div class="cap" style="margin-top:8px">Crea también la competencia en «Competencias» y el programa termina con puesta a punto ese día.</div></div>
  ${wzFoot()}</div>`,
 notes:{tags:[['keep','Mismos objetivos'],['new','Enlaza con Competencias']],p:['Objetivo general o disciplina. Si compites con fecha, el programa se arma hacia ese día (pico y puesta a punto, como hoy) y además se crea la competencia como «próxima», con su cuenta atrás.']}});

scene({tab:'crear',id:'cr-week',title:'Capítulo 4 · Tu semana',html:()=>`<div class="wz">${wzTop(3,true)}<div class="chap">4 · Tu semana</div><h1>¿Qué días puedes entrenar?</h1>
  <div class="dayp">${['L','M','X','J','V','S','D'].map((d,i)=>`<span class="${[0,1,2,4,5].includes(i)?'on':''}">${d}</span>`).join('')}</div><div class="cap" style="margin-top:8px">5 días · puedes cambiarlo luego en la semana</div>
  <div class="mgroup">Tiempo por sesión</div><div class="stepper"><button aria-label="Menos">−</button><b>60<small>min</small></b><button aria-label="Más">+</button></div>
  <div class="mgroup">¿Qué día llegas con más energía?</div>${chips(['Lunes','Martes','Miércoles','Viernes','Sábado'],0,38)}
  ${wzFoot()}</div>`,
 notes:{tags:[['keep','Mismas preguntas'],['fix','1 a 7 días en todos los caminos']],p:['Días, minutos y día con más energía en una pantalla. Arregla una incoherencia: el paso dice «entre 1 y 7» pero desde el catálogo solo se admitía de 1 a 6.']}});

scene({tab:'crear',id:'cr-fine',title:'Capítulo 5 · Afinar (opcional)',html:()=>`<div class="wz">${wzTop(4,true)}<div class="chap">5 · Afinar · opcional</div><h1>¿Algo que priorizar?</h1>
  <div class="mgroup">Músculos (hasta 5)</div>${chips(['Glúteos','Espalda','Hombros','Pecho','Brazos','Pantorrillas','Core'],[0,1])}
  <div class="mgroup">Tus marcas, si las sabes</div>${[['Sentadilla','140 × 5'],['Press banca','100 × 5'],['Peso muerto','—']].map(r=>`<div class="rowx"><div class="tx"><strong>${r[0]}</strong><span>${r[1]==='—'?'Sin dato':'Mejor serie reciente'}</span></div><b class="syne">${r[1]}</b></div>`).join('')}
  ${wzFoot('Ver mi programa','Saltar')}</div>`,
 notes:{tags:[['keep','Mismas preguntas']],p:['Lo que hoy son pasos sueltos al final (músculos, ejercicios a evitar, estilo de armwrestling, variantes, marcas, cardio) se agrupa en un capítulo opcional que se puede saltar entero. Solo aparecen las preguntas que tocan según tus respuestas.']}});

scene({tab:'crear',id:'cr-gen',title:'Armando tu programa',after:drawAfter,html:()=>`<div class="wz" style="display:flex;flex-direction:column;justify-content:center">
  <div style="height:330px;margin:0 -20px;position:relative">${coverSvg('perfil','carbon',{vol:[.55,.62,.7,.8,.66,.4],now:-1})}</div>
  <h1 style="text-align:center">Armando tu programa</h1>
  <div class="wr-p" style="text-align:center;margin:0 auto;max-width:none">${['Eligiendo el reparto para 5 días','Ajustando el volumen a tu experiencia','Comprobando tu material'].map((t,i)=>`<div class="rise" style="opacity:0;animation-delay:${.3+i*.7}s;margin-top:6px">${i<2?ic('check',14):''} ${t}</div>`).join('')}</div></div>`,
 notes:{tags:[['new','Espera con sentido']],p:['Mientras el motor evalúa candidatos (hoy es casi instantáneo, pero con el catálogo grande tarda), la portada del programa se dibuja con la carga de sus semanas y tres frases dicen qué se está decidiendo. Mínimo 1,5 s para que se lea; con movimiento reducido, solo las frases.']}});

scene({tab:'crear',id:'cr-offer',title:'La propuesta',after:drawAfter,html:()=>`<div class="wz">${wzTop(5)}<h1 style="margin-top:8px">Tu programa</h1>
  <div class="offer pick"><div class="art">${cover('','perfil','carbon')}<span class="badge" style="position:absolute;left:12px;top:12px">${ic('spark',13)}Para ti</span></div><div class="in">
   <strong class="syne" style="font-size:calc(22px*var(--fs));letter-spacing:-.5px">Fuerza y músculo · 6 semanas</strong><div class="meta" style="margin-top:4px">3 bloques · 5 días · ≈ 60 min</div>
   ${['Bloques de 6 semanas: ya entrenas con constancia','Tren inferior dos veces: priorizaste glúteos','Sin máquinas de femoral: usamos peso muerto rumano'].map(w=>`<div class="why"><i></i>${w}</div>`).join('')}
   <div class="split7">${[['L','Pierna A'],['M','Empuje'],['X','Tirón'],['J',''],['V','Pierna B'],['S','Torso'],['D','']].map(d=>`<div><small>${d[0]}</small><i class="${d[1]?'on':''}"></i><span>${d[1]||'—'}</span></div>`).join('')}</div>
   <div style="display:flex;gap:8px;margin-top:14px"><button class="pill sec sm" style="flex:1">${ic('eye',16)}Ver detalles</button><button class="pill sec sm" style="flex:1">${ic('repeat',16)}Otra versión</button></div></div></div>
  <div class="group-l">También encaja</div>
  ${[['Rutina · Fuerza y músculo','1 semana base · 5 días','ondas'],['Periodizado · 12 semanas','4 bloques · 5 días','espiral']].map(o=>`<div class="pcard">${cover('',o[2],'carbon')}<div class="tx"><strong>${o[0]}</strong><span>${o[1]}</span></div>${ic('fwd',18)}</div>`).join('')}
  <button class="act faint" style="width:100%;justify-content:center">Ver programas del catálogo (14)</button>
  ${wzFoot('Elegir este programa','Lo haré luego')}</div>`,
 notes:{tags:[['keep','Mismo motor y acciones'],['new','El porqué, a la vista']],
  p:['La propuesta «Para ti» con su portada de líneas, el reparto de la semana y <b>tres razones</b> que salen de tus respuestas (el evaluador ya sabe por qué elige). Debajo, las otras formas que encajan (rutina, bloques, periodizado) y el acceso al catálogo filtrado.',
     'Mismas acciones que hoy: «Ver detalles» (hoja «Cómo funciona»), «Otra versión», «Elegir este programa» y «Lo haré luego».']}});

scene({tab:'crear',id:'cr-activate',title:'Activar',html:()=>`<div class="wz">${wzTop(5)}<h1 style="margin-top:8px">Listo para empezar</h1><p class="lead">Ya tienes «Motor y fuerza» en curso (semana 4 de 6). ¿Qué hacemos con el nuevo?</p>
  ${opt('Empezar ahora','Pausa «Motor y fuerza»; podrás reanudarlo',false)}${opt('Ponerlo en cola','Empieza solo cuando termine el actual',true)}${opt('Guardarlo sin activar','Queda en «Tus programas»',false)}
  <div class="mod" style="margin-top:14px"><div class="mod-h"><span class="ey">Máximos de entrenamiento</span><span class="cap">opcional</span></div><div class="cap" style="margin-top:6px">El programa usa porcentajes. Precargados con tu historial: sentadilla 144, banca 101, peso muerto 171.</div><button class="act" style="margin-top:4px">Revisar máximos</button></div>
  ${wzFoot('Guardar y poner en cola')}</div>`,
 notes:{tags:[['new','Una sola forma de activar'],['fix','Nada se activa sin preguntar']],
  p:['Hoy cada camino activa distinto: el asistente activa siempre (pide «Confirmo la activación» si hay otro), una estructura del catálogo <b>se activa sin preguntar</b>, y «Desde cero», importar y protocolos no activan. Ahora todos terminan aquí: empezar ahora, en cola o guardar.',
     'Si ya no hay programa en curso, la opción por defecto es «Empezar ahora» y el texto lo dice. Los máximos aparecen solo si el programa los usa.']}});

scene({tab:'crear',id:'cr-info',title:'Catálogo › Cómo funciona',after:drawAfter,html:()=>`<div class="scroll" style="padding-top:0;padding-left:0;padding-right:0"><div style="height:200px;position:relative;overflow:hidden">${cover('big','eco','grafito',{num:'16'})}<div style="position:absolute;inset:0;background:linear-gradient(#0000 40%,#000)"></div><div style="position:absolute;top:44px;left:10px"><button class="fb glass" aria-label="Atrás">${ic('back')}</button></div></div>
  <div style="padding:0 16px"><div class="lbl">Jim Wendler · Original fiel</div><div class="ptitle" style="margin-top:6px">5/3/1 Boring But Big</div><div class="meta">4 días · 16 semanas · Fuerza</div>
  <div class="group-l">Qué haces</div><p class="body" style="margin:0">Un básico pesado con la onda 5/3/1 y después 5 × 10 del mismo levantamiento al 50–60 %. Ciclos de 4 semanas con descarga.</p>
  <div class="group-l">Tu semana tipo</div><div class="split7" style="margin-top:0">${[['L','Press militar'],['M','Peso muerto'],['X',''],['J','Press banca'],['V','Sentadilla'],['S',''],['D','']].map(d=>`<div><small>${d[0]}</small><i class="${d[1]?'on':''}"></i><span>${d[1]||'—'}</span></div>`).join('')}</div>
  <div class="group-l">Necesitas</div>${chips(['Barra y discos','Rack','Banco'],[])}
  <div class="group-l">Fuente</div><p class="cap" style="margin:0">Basado en «5/3/1 Forever» (2017). No afiliado al autor.</p>
  <button class="pill pri" style="width:100%;margin-top:20px">Configurar este plan</button></div></div>`,
 notes:{tags:[['keep','Mismo contenido'],['new','Página, no hoja']],p:['«Cómo funciona» pasa de hoja a página con portada: qué haces, semana tipo dibujada, material y fuente (notas del método y glosario debajo, plegados).','«Configurar este plan» abre «A tu medida» con el objetivo y los días ya puestos: solo pregunta lo que el plan necesita (capítulos 2, 4 y 5).']}});

scene({tab:'crear',id:'cr-import',title:'Importar',html:()=>`${programsBehind()}${sheet(`<h3>Importar un programa</h3><div class="sub">KPKN lo lee y te deja revisarlo antes de guardarlo.</div>
  ${mi('upload','Elegir archivo','Excel, CSV, Word, texto o PDF')}${mi('paste','Pegar texto','Ej.: Día 1 — Pierna / Sentadilla 4x6 @120kg')}${mi('camera','Foto de tu rutina','Hacer una foto o elegirla de la galería')}
  <div class="pending" style="color:var(--muted);background:#ffffff06;border-color:#ffffff12">${ic('info',16)}Si se parece a un plan del catálogo te lo diremos, por si prefieres la versión de KPKN.</div>`)}`,
 notes:{tags:[['keep','Mismas tres fuentes']],p:['Igual que hoy. Al terminar la revisión, el programa importado pasa por la misma pantalla «Listo para empezar» (hoy se guarda sin activar y sin decirlo).']}});

scene({tab:'crear',id:'cr-tm',title:'Máximos de entrenamiento',html:()=>`<div class="wz">${wzTop(5)}<h1 style="margin-top:8px">Tus máximos</h1><p class="lead">Escribe tu 1RM; el máximo de entrenamiento es el 90 % y con él salen las cargas.</p>
  ${[['Sentadilla','160','144'],['Press banca','112,5','101'],['Peso muerto','190','171'],['Press militar · opcional','','—']].map(r=>`<div class="ratio" style="grid-template-columns:1fr 96px 64px;align-items:center;min-height:58px"><span>${r[0]}</span><div class="field" style="margin:0;padding:8px 12px"><div class="tn" style="margin:0">${r[1]||'<span class="cap">kg</span>'}</div></div><span class="cap tn" style="text-align:right">TM ${r[2]}</span></div>`).join('')}
  <button class="act" style="margin-top:6px">${ic('history',16)}Calcular desde mi historial</button>
  ${wzFoot('Aplicar','Crear sin TM')}</div>`,
 notes:{tags:[['keep','Mismo paso'],['keep','«Crear sin TM» (ya hecho)']],p:['Cada marca muestra su TM en la misma fila. «Crear sin TM» ya se implementó en el paquete anterior.']}});

scene({tab:'crear',id:'cr-reject',title:'Cuando un plan no encaja',html:()=>`<div class="wz">${wzTop(5)}<h1 style="margin-top:8px">Este plan necesita algo que no tienes</h1><p class="lead">«5/3/1 Boring But Big» usa rack y banco, y marcaste que entrenas en casa sin ellos.</p>
  ${mi('check','Confirmar material','Sí tengo rack y banco')}${mi('swap','Ver alternativas','3 planes parecidos con tu material')}${mi('target','Cambiar objetivo','')}${mi('cal','Ajustar a 45 min','Tus sesiones son más cortas que las del plan')}
  ${wzFoot('Ver alternativas')}</div>`,
 notes:{tags:[['keep','Mismos arreglos']],p:['Los avisos del evaluador de hoy («Ya no está entre los planes que corresponden a tus respuestas», «Este plan necesita material que dijiste que no tienes») con el motivo concreto y los mismos arreglos como filas de acción.']}});
