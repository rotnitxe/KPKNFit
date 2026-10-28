/* ================= CREAR PROGRAMA (v2: el asistente de bienvenida, adaptado) ================= */
const programsBehind=()=>`<div class="scroll" style="padding-top:52px"><div style="display:flex;justify-content:space-between;align-items:center"><div class="ptitle">Programas</div><button class="pill pri sm">${ic('plus',16)}Nuevo</button></div></div>`;
// El asistente real: una página larga, negra y continua; lo confirmado se pliega en filas-resumen editables; vidrio solo
// bajo la cabecera y el botón; progreso blanco sobre gris; símbolos de línea sin caja de tarjeta.
function wzHead(done,total=9){return `<div class="wzh glass"><button class="ib" aria-label="Cerrar">${ic('close')}</button><div class="t"><strong>Nuevo programa</strong><span>Solo entreno · ${done} de ${total}</span></div></div><div class="wzp"><i style="width:${Math.round(done/total*100)}%"></i></div>`}
const sumRow=(k,v,id)=>`<div class="wzsum" data-tag="setup-summary-${id}"><span class="k">${k}</span><span class="v">${v}</span>${ic('edit',16)}</div>`;
const symRow=(sym,t,s,on)=>`<button class="wzopt ${on?'on':''}"><span class="sym">${ic(sym,40)}</span><span class="tx"><strong>${t}</strong>${s?`<span>${s}</span>`:''}</span><span class="rad">${on?ic('check',16):''}</span></button>`;
const wzStep=(t,sub,body)=>`<section class="wzstep"><h2>${t}</h2>${sub?`<p>${sub}</p>`:''}${body}</section>`;
const wzBtn=(t='Continuar',dis=false)=>`<div class="wzbtn glass"><button class="pill pri" style="width:100%;${dis?'opacity:.35':''}">${t}</button></div>`;
const peek=(t)=>`<section class="wzstep peek"><h2>${t}</h2></section>`;
const KNOWN=[['Alias','Matías','alias'],['Edad','34 años','age'],['Altura','1,78 m','height'],['Peso','80,4 kg','weight']];

scene({tab:'crear',id:'cr-new',title:'Nuevo programa (ya aprobado)',html:()=>`${programsBehind()}${sheet(`<h3>Nuevo programa</h3><div class="sub">Elige cómo empezar.</div>
  <div class="opt-grid"><button class="opt wide" style="background:linear-gradient(135deg,#f49a6e22,#ffffff06);border-color:#f49a6e40"><span class="ic" style="color:var(--mus)">${ic('spark')}</span><span class="tx"><strong>A tu medida</strong><span style="display:block">KPKN lo arma con tu material, tus días y tu objetivo</span></span>${ic('fwd',18)}</button>
  <button class="opt"><span class="ic">${ic('book')}</span><strong>Del catálogo</strong><span>Planes conocidos y planes KPKN</span></button><button class="opt"><span class="ic">${ic('upload')}</span><strong>Importar</strong><span>Excel, Word, PDF, texto o una foto</span></button>
  <button class="opt wide"><span class="ic">${ic('plus')}</span><span class="tx"><strong>Desde cero</strong><span style="display:block">Le pones nombre y armas las sesiones tú</span></span>${ic('fwd',18)}</button></div>`)}`,
 notes:{tags:[['keep','Aprobada e implementada'],['fix','La misma hoja en Inicio']],
  p:['La hoja aprobada en el lienzo anterior y ya implementada. «A tu medida» abre <b>el asistente de bienvenida</b> en su modo «solo entreno» (siguientes escenas). Inicio abre esta misma hoja (hoy abre otra distinta).']}});

scene({tab:'crear',id:'cr-wz1',title:'A tu medida · el asistente, adaptado',html:()=>`<div class="wz wzpage">${wzHead(1)}
  <div class="wzlead">Arma tu programa con las mismas preguntas que al empezar en KPKN. Lo que ya sabemos está arriba, plegado.</div>
  <div class="wzsums">${KNOWN.map(k=>sumRow(k[0],k[1],k[2])).join('')}</div>
  ${wzStep('¿Cuánta experiencia tienes?','Decide cuánto volumen y cuánta variedad tendrá tu programa.',symRow('spark','Estoy empezando','',false)+symRow('repeat','Estoy volviendo','',false)+symRow('cal','Ya entreno con constancia','',true)+symRow('trophy','Tengo experiencia','',false))}
  ${peek('¿Dónde entrenas?')}${wzBtn()}</div>`,
 notes:{tags:[['keep','Es el asistente de bienvenida'],['new','Instancia «Nuevo programa»']],
  p:['Se reutiliza <b>el asistente de bienvenida tal cual</b>: la misma página larga negra y continua, los mismos pasos, los mismos símbolos animados sin caja, el vidrio solo bajo la cabecera y el botón, y el progreso blanco sobre gris. Lo que cambia es la instancia:',
     '<b>Cabecera propia</b>: «Nuevo programa · Solo entreno» con la X para salir (el borrador queda guardado y al volver retoma donde estabas; ya existe por alcance). <b>Lo que ya sabemos</b> (alias, edad, altura, peso) aparece plegado arriba como filas-resumen editables, igual que lo confirmado en el asistente, en vez de preguntarse otra vez.',
     'Sin bienvenida, sin prólogo, sin nutrición y sin hito final de alta.'],
  listTitle:'Qué se salta en esta instancia',list:['Género y % de grasa: hoy son obligatorios en «solo entreno» aunque solo sirven para la nutrición. Si faltan, no se piden.','Los pasos de alta (bienvenida, prólogo, intro) que ya no existen en modo solo entreno.','El cierre «Revisión y activación» del alta: lo sustituye «Listo para empezar» (última escena).']}});

scene({tab:'crear',id:'cr-wz2',title:'A tu medida · material',html:()=>`<div class="wz wzpage">${wzHead(3)}
  <div class="wzsums">${sumRow('Lo que ya sabemos','Matías · 34 años · 80,4 kg','basics')}${sumRow('Experiencia','Ya entreno con constancia','exp')}${sumRow('Dónde','Gimnasio','place')}</div>
  ${wzStep('¿Con qué material entrenas?','Marcamos lo típico de un gimnasio. Quita lo que no tengas.',`<div class="wzgrid">${[['dumb','Barra y discos',1],['dumb','Mancuernas',1],['tower','Rack',1],['grip','Poleas',1],['target','Máquinas',1],['link','Bandas',0],['flag','Barra de dominadas',1],['heart','Cardio',1]].map(m=>`<button class="wzsym ${m[2]?'on':''}">${ic(m[0],38)}<span>${m[1]}</span></button>`).join('')}</div>`)}
  ${peek('¿Cuál es tu objetivo?')}${wzBtn()}</div>`,
 notes:{tags:[['keep','Mismo paso del asistente']],p:['Las respuestas confirmadas se pliegan arriba; los datos conocidos se juntan en una sola fila. El material son los símbolos de línea del asistente (barra, mancuerna, rack…) sobre la superficie negra, sin tarjetas: el elegido en tinta, el resto atenuado.']}});

scene({tab:'crear',id:'cr-wz3',title:'A tu medida · objetivo y competencia',html:()=>`<div class="wz wzpage">${wzHead(4)}
  <div class="wzsums">${sumRow('Lo que ya sabemos','Matías · 34 años · 80,4 kg','basics')}${sumRow('Experiencia','Ya entreno con constancia','exp')}${sumRow('Material','Gimnasio completo','gear')}</div>
  ${wzStep('¿Cuál es tu objetivo?','',symRow('dumb','Fuerza y masa muscular','',false)+symRow('heart','Fuerza y cardio','',false)+symRow('spark','Funcional y saludable','',false)+`<div class="wzlbl">Una disciplina</div>`+chips(['Powerlifting','Powerbuilding','Culturismo','Calistenia','Halterofilia','Strongman','Armwrestling'],0,38))}
  ${wzStep('¿Compites en tu disciplina?','',symRow('cal','Sí, tengo fecha','16 nov · Open regional',true)+symRow('trophy','Sí, sin fecha','',false)+symRow('close','No compito','',false))}
  ${wzBtn()}</div>`,
 notes:{tags:[['keep','Mismos pasos'],['new','Crea la competencia']],p:['Igual que en el alta. Lo único nuevo: si compites con fecha, además de armar el programa hacia ese día (pico y puesta a punto, como hoy), se crea la competencia como «próxima» en Competencias.']}});

scene({tab:'crear',id:'cr-wz-plan',title:'A tu medida · tu programa',after:drawAfter,html:()=>`<div class="wz wzpage">${wzHead(8)}
  <div class="wzsums">${sumRow('Tu semana','5 días · 60 min · L M X V S','week')}</div>
  ${wzStep('Tu programa a medida','',`<div class="offer pick" style="margin-top:6px"><div class="art">${cover('','perfil','carbon')}<span class="badge" style="position:absolute;left:12px;top:12px">${ic('spark',13)}Para ti</span></div><div class="in">
   <strong class="syne" style="font-size:calc(21px*var(--fs));letter-spacing:-.5px">Fuerza y músculo · 6 semanas</strong><div class="meta" style="margin-top:4px">3 bloques · 5 días · ≈ 60 min</div>
   ${['Bloques de 6 semanas: ya entrenas con constancia','Tren inferior dos veces: priorizaste glúteos'].map(w=>`<div class="why"><i></i>${w}</div>`).join('')}
   <div style="display:flex;gap:8px;margin-top:14px"><button class="pill sec sm" style="flex:1">Ver detalles</button><button class="pill sec sm" style="flex:1">Otra versión</button></div></div></div>
   <div class="wzlbl">También encaja</div>${['Rutina · 1 semana base','Periodizado · 12 semanas'].map(o=>`<div class="wzsum"><span class="v" style="flex:1;color:var(--ink)">${o}</span>${ic('fwd',16)}</div>`).join('')}`)}
  ${wzBtn('Elegir este programa')}</div>`,
 notes:{tags:[['keep','Paso PLAN del asistente']],p:['Es el paso PLAN de hoy («Tu programa a medida» o «Elige tu programa» en disciplinas), con sus mismas acciones: «Ver detalles», «Otra versión», elegir o dejarlo para luego.','Dos mejoras que valen también para el alta: la portada de líneas del programa y dos razones que salen de tus respuestas (el evaluador ya sabe por qué elige).']}});

scene({tab:'crear',id:'cr-wz-week',title:'A tu medida · así queda tu semana',html:()=>`<div class="wz wzpage">${wzHead(9)}
  <div class="wzsums">${sumRow('Programa','Fuerza y músculo · 6 semanas','plan')}</div>
  ${wzStep('Así queda tu semana','Mantén pulsada una sesión para moverla de día.',`<div class="wzweek">${[['LUN','Pierna A'],['MAR','Empuje'],['MIÉ','Tirón'],['JUE',''],['VIE','Pierna B'],['SÁB','Torso'],['DOM','']].map(d=>`<div class="wzday"><small>${d[0]}</small>${d[1]?`<span>${d[1]}</span>`:'<em>Descanso</em>'}</div>`).join('')}</div>`)}
  ${wzBtn('Continuar')}</div>`,
 notes:{tags:[['keep','Mismo paso']],p:['El paso «Así queda tu semana» de hoy, con su aviso de material por día si aplica.']}});

scene({tab:'crear',id:'cr-wz-end',title:'A tu medida · listo para empezar',html:()=>`<div class="wz wzpage">${wzHead(9,9)}
  <div class="wzsums">${sumRow('Programa','Fuerza y músculo · 6 semanas','plan')}${sumRow('Semana','5 días · 60 min','week')}</div>
  ${wzStep('Listo para empezar','Ya tienes «Motor y fuerza» en curso (semana 4 de 6). ¿Qué hacemos con el nuevo?',symRow('play','Empezar ahora','Pausa «Motor y fuerza»; podrás reanudarlo',false)+symRow('repeat','Ponerlo en cola','Empieza cuando termine el actual',true)+symRow('book','Guardarlo sin activar','Queda en «Tus programas»',false))}
  ${wzBtn('Guardar y poner en cola')}</div>`,
 notes:{tags:[['new','Cierre propio de esta instancia'],['fix','Nada se activa sin preguntar']],
  p:['Sustituye a «Revisión y activación» del alta (con su «Activar y entrar a KPKN»). Mismo patrón de filas del asistente. Todos los caminos de creación (asistente, catálogo, importar, desde cero) terminan con esta elección: hoy una estructura del catálogo se activa sin preguntar y el resto no activa.','Si no hay programa en curso, la opción por defecto es «Empezar ahora». Al terminar vuelve a Programas (no a Inicio) con el programa nuevo abierto.']}});

scene({tab:'crear',id:'cr-info',title:'Catálogo › Cómo funciona',after:drawAfter,html:()=>`<div class="scroll" style="padding-top:0;padding-left:0;padding-right:0"><div style="height:200px;position:relative;overflow:hidden">${cover('big','eco','grafito',{num:'16'})}<div style="position:absolute;inset:0;background:linear-gradient(#0000 40%,#000)"></div><div style="position:absolute;top:44px;left:10px"><button class="fb glass" aria-label="Atrás">${ic('back')}</button></div></div>
  <div style="padding:0 16px"><div class="lbl">Jim Wendler · Original fiel</div><div class="ptitle" style="margin-top:6px">5/3/1 Boring But Big</div><div class="meta">4 días · 16 semanas · Fuerza</div>
  <div class="group-l">Qué haces</div><p class="body" style="margin:0">Un básico pesado con la onda 5/3/1 y después 5 × 10 del mismo levantamiento al 50–60 %. Ciclos de 4 semanas con descarga.</p>
  <div class="group-l">Tu semana tipo</div><div class="split7" style="margin-top:0">${[['L','Press militar'],['M','Peso muerto'],['X',''],['J','Press banca'],['V','Sentadilla'],['S',''],['D','']].map(d=>`<div><small>${d[0]}</small><i class="${d[1]?'on':''}"></i><span>${d[1]||'—'}</span></div>`).join('')}</div>
  <div class="group-l">Necesitas</div>${chips(['Barra y discos','Rack','Banco'],[])}
  <button class="pill pri" style="width:100%;margin-top:20px">Configurar este plan</button></div></div>`,
 notes:{tags:[['keep','Mismo contenido']],p:['«Configurar este plan» abre el mismo asistente adaptado con el objetivo y los días ya preseleccionados (como hoy con <code>planId</code>), y el plan elegido en el paso PLAN.']}});

scene({tab:'crear',id:'cr-import',title:'Importar',html:()=>`${programsBehind()}${sheet(`<h3>Importar un programa</h3><div class="sub">KPKN lo lee y te deja revisarlo antes de guardarlo.</div>
  ${mi('upload','Elegir archivo','Excel, CSV, Word, texto o PDF')}${mi('paste','Pegar texto','Ej.: Día 1 — Pierna / Sentadilla 4x6 @120kg')}${mi('camera','Foto de tu rutina','Hacer una foto o elegirla de la galería')}`)}`,
 notes:{tags:[['keep','Mismas tres fuentes']],p:['Igual que hoy; al terminar la revisión pasa por «Listo para empezar».']}});

scene({tab:'crear',id:'cr-tm',title:'Máximos de entrenamiento',html:()=>`<div class="wz wzpage">${wzHead(9,9)}${wzStep('Tus máximos','Escribe tu 1RM; el máximo de entrenamiento es el 90 % y con él salen las cargas.',
  [['Sentadilla','160','144'],['Press banca','112,5','101'],['Peso muerto','190','171'],['Press militar · opcional','','—']].map(r=>`<div class="ratio" style="grid-template-columns:1fr 96px 64px;align-items:center;min-height:58px"><span>${r[0]}</span><div class="field" style="margin:0;padding:8px 12px"><div class="tn" style="margin:0">${r[1]||'<span class="cap">kg</span>'}</div></div><span class="cap tn" style="text-align:right">TM ${r[2]}</span></div>`).join('')+`<button class="act" style="margin-top:6px">${ic('history',16)}Calcular desde mi historial</button>`)}
  <div class="wzbtn glass" style="display:flex;gap:8px"><button class="pill sec">Crear sin TM</button><button class="pill pri" style="flex:1">Aplicar</button></div></div>`,
 notes:{tags:[['keep','Mismo paso']],p:['Si el programa elegido usa porcentajes, este paso entra en la misma página antes de «Listo para empezar».']}});
