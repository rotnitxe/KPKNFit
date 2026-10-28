/* ================= REGISTRO DE COMIDA · DESCRIBIR ================= */
const MACC={p:'var(--mus)',c:'var(--ene)',f:'var(--col)'};
function lgHead(title='Registrar comida'){return `<div class="lg-head"><button class="ib fill" aria-label="Cerrar">${ic('close')}</button><div class="t"><strong>${title}</strong><span>${ic('cal',13)}Hoy · 08:40</span></div><button class="ib fill" aria-label="Ajustes del registro">${ic('sliders')}</button></div>`}
const mealChips=(on=0)=>`<div class="meal-chips">${['Desayuno','Almuerzo','Merienda','Cena'].map((m,i)=>`<span class="chip ${i===on?'on':''}" style="height:34px">${m}</span>`).join('')}</div>`;
const seg2=(on=0)=>`<div class="seg2"><button class="${on===0?'on':''}">${ic('edit',16)}Describir</button><button class="${on===1?'on':''}">${ic('search',16)}Buscar</button></div>`;
function textWithTokens(state){let t=MEAL_TEXT;FOODS.forEach((f,i)=>{const cls=state==='all'?'done':state===i?'on':(typeof state==='number'&&i<state)?'done':'';if(cls)t=t.replace(f.tok,`<span class="tok ${cls}">${f.tok}</span>`)});return t}
// Animación «leyendo tu comida»: un plato que se dibuja y los alimentos detectados que orbitan y caen en él.
function plateAnim(){const icons=[['egg',60,70],['bread',230,62],['avo',52,170],['cup',238,168]];
  return `<svg class="plate" viewBox="0 0 300 250" width="300" height="250" aria-hidden="true">
  <circle class="ln" pathLength="1" cx="150" cy="125" r="78" fill="none" stroke="#f2eee6" stroke-width="1.4" opacity=".75"/>
  <circle class="ln" pathLength="1" cx="150" cy="125" r="58" fill="none" stroke="#f2eee6" stroke-width="1" opacity=".35" style="animation-delay:.25s"/>
  <path class="ln" pathLength="1" d="M60 125a90 90 0 0 1 180 0" fill="none" stroke="#f49a6e" stroke-width="2" stroke-linecap="round" style="animation-delay:.5s;filter:drop-shadow(0 0 6px #f49a6e)"/>
  <g class="orbit"><circle cx="150" cy="40" r="3" fill="#f49a6e"/></g>
  ${icons.map((c,i)=>`<g transform="translate(${c[1]-18} ${c[2]-18})" style="color:#f2eee6"><g class="float${i+1}"><g transform="scale(1.5)"><g fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">${P[c[0]]}</g></g></g></g>`).join('')}
  </svg>`}
function foodCard(f,i,{open=false,answered=false}={}){const mt=f.p*4+f.c*4+f.f*9;const q=f.ask&&!answered;
  return `<div class="food ${q?'ask':''}" style="animation-delay:${150+i*140}ms"><div class="food-h"><span class="fi">${ic(f.ic,24)}</span><div class="tx"><strong>${f.n}</strong><span>${f.q}${f.approx?' · aprox.':''}</span></div><div class="k"><b>${f.approx?'≈ ':''}${f.k}</b><span>kcal</span></div></div>
  <div class="mbar"><i style="flex:${f.p*4};background:${MACC.p}"></i><i style="flex:${f.c*4};background:${MACC.c}"></i><i style="flex:${f.f*9};background:${MACC.f}"></i></div>
  ${q&&f.ask==='oil'?`<div class="q"><p><i></i>¿Las tostaste con aceite o mantequilla?</p><div class="qopts"><span>Nada</span><span>Un poco</span><span>Bastante</span></div><span class="qskip">Seguir con «nada»</span></div>`:''}
  ${q&&f.ask==='milk'?`<div class="q"><p><i></i>¿Qué leche llevaba?</p><div class="qopts"><span>Entera</span><span class="on">Semidesnatada</span><span>Desnatada</span><span>Vegetal</span></div></div>`:''}
  ${open?`<div class="q" style="border-top-color:var(--div)"><div class="sizes">${[['Pequeña',22],['Mediana',30],['Grande',38],['Extra',46]].map((s,j)=>`<button class="${j===1?'on':''}"><i style="width:${s[1]}px;height:${s[1]}px"></i>${s[0]}</button>`).join('')}</div></div>`:''}</div>`}
function sumCard(k=548,c=0){return `<div class="sumcard">${ringSvg(k,2300)}<div class="mac">${[['Proteína',25,'p',.32],['Carbos',46,'c',.43],['Grasa',30,'f',.25]].map(m=>`<div>${m[0]}<i style="--w:${Math.round(m[3]*100)}%;--c:${MACC[m[2]]}"></i><b>${m[1]} g</b></div>`).join('')}</div></div>`}
const saveBar=(k=548,{off=false,label='Guardar en desayuno'}={})=>`<div class="savebar glass"><div class="k"><b>${k} kcal</b><span>${off?'Resuelve 2 dudas o sigue con la estimación':'4 alimentos · 25 g de proteína'}</span></div><button class="go ${off?'off':''}">${label}</button></div>`;

scene({tab:'comida',id:'f-empty',title:'Describir · al abrir',html:()=>`<div class="lg">${lgHead()}${mealChips()}${seg2()}
  <div class="write focus"><div class="txt"><span class="ph">¿Qué comiste? Escríbelo como lo contarías</span><span class="caret"></span></div>
  <div class="acts"><span class="cap">Cantidades, marcas y cómo lo cocinaste</span><button class="ib fill" aria-label="Dictar">${ic('mic')}</button></div></div>
  <div class="group-l">Repetir</div><div class="hrow">${RECENT.map(r=>`<div class="hcard"><strong>${r[0]}</strong><span>${r[1]}</span></div>`).join('')}</div>
  <div class="group-l">Favoritos<span style="text-transform:none;letter-spacing:0" class="act faint">Ver todos</span></div>
  <div class="mod tight">${[['Yogur griego con miel','190 kcal'],['Batido de proteína','160 kcal']].map(f=>`<div class="sres"><span class="fi" style="width:36px;height:36px;border-radius:12px;background:#ffffff0a;display:flex;align-items:center;justify-content:center;color:var(--ene)">${ic('starOn',16)}</span><div class="tx"><strong>${f[0]}</strong><span>${f[1]}</span></div><button class="add" aria-label="Añadir">${ic('plus',18)}</button></div>`).join('')}</div></div>`,
 notes:{tags:[['new','Repetir lo de siempre'],['fix','Favoritos sin tapar el campo']],
  p:['El campo es lo primero, grande y con el teclado abierto; la comida (desayuno, almuerzo…) se elige con chips arriba y la hora sale de la cabecera.',
     'Debajo, <b>«Repetir»</b>: lo que sueles registrar a esta hora y las comidas recientes (hoy no existen recientes). Favoritos con tope de dos filas y «Ver todos»: hoy favoritos, recetas y plantillas se apilan sin tope <b>encima</b> de las dos pestañas.',
     'El micrófono dicta al campo (el reconocedor de voz ya existe en el código y no tenía uso en Nutrición).']}});

scene({tab:'comida',id:'f-typed',title:'Describir · escrito',html:()=>`<div class="lg">${lgHead()}${mealChips()}${seg2()}
  <div class="write focus"><div class="txt">${MEAL_TEXT}<span class="caret"></span></div><div class="acts"><span class="cap">Enter para analizar</span><button class="pill pri sm">${ic('spark',16)}Analizar</button></div></div></div>`,
 notes:{tags:[['fix','Un solo verbo']],p:['Un solo botón: <b>«Analizar»</b>. Hoy el mismo botón dice «Interpretar comida» o «Actualizar y buscar», y este último no busca: vuelve a interpretar. Si cambias el texto después de analizar, el botón dice «Volver a analizar».']}});

scene({tab:'comida',id:'f-ana',title:'Analizando',after:ph=>{animateIn(ph)},html:()=>`<div class="lg">${lgHead()}${mealChips()}
  <div class="write"><div class="txt" style="font-size:calc(17px*var(--fs))">${textWithTokens(2)}</div></div>
  <div class="ana">${plateAnim()}<div class="msg">Encontré huevos y pan<small>Estimando cantidades…</small></div></div></div>`,
 notes:{tags:[['new','Animación de comidas'],['keep','Motor local, sin conexión']],
  p:['Al analizar, un plato de líneas se dibuja y los alimentos aparecen como iconos de línea que flotan alrededor y van cayendo en él. A la vez, <b>las palabras del texto se subrayan</b> en color acento mientras se leen y pasan a verde cuando se convierten en alimento.',
     'Los mensajes dicen lo que de verdad pasa («Encontré huevos y pan», «Estimando cantidades…»), no frases rotatorias genéricas. El motor sigue siendo local y sin conexión: la animación tapa el tiempo de carga del catálogo la primera vez.',
     'Con movimiento reducido: el texto se subraya sin animar y un solo mensaje.']}});

scene({tab:'comida',id:'f-result',title:'Lo que comiste',after:drawAfter,html:()=>`<div class="lg">${lgHead()}${mealChips()}
  <div class="write" style="min-height:0;padding:12px 14px"><div class="txt" style="font-size:calc(14px*var(--fs));color:var(--muted)">${textWithTokens('all')}</div></div>
  <div class="pending">${ic('qmark',16)}2 dudas: tócalas para afinar o guarda con la estimación</div>
  <div style="margin-top:12px">${sumCard()}</div>
  ${FOODS.map((f,i)=>foodCard(f,i)).join('')}
  <button class="act" style="margin-top:8px">${ic('plus',16)}Añadir otro alimento</button></div>${saveBar()}`,
 notes:{tags:[['new','Resumen con anillo'],['fix','Todas las dudas a la vista']],
  p:['Arriba, el anillo de calorías con los tres macros como arcos (cuenta al aparecer). Cada alimento es una tarjeta con su icono, cantidad legible («2 huevos · 110 g»), calorías y una barra de macros.',
     '<b>Las dudas viven en su tarjeta</b>, con fondo cálido y chips de respuesta. Hoy solo se ve la primera duda de la primera tarjeta y las demás quedan ocultas.',
     'Se puede guardar con dudas: cada una tiene su respuesta por defecto («Seguir con “nada”»). Hoy el guardado queda bloqueado con un aviso.']}});

scene({tab:'comida',id:'f-edit',title:'Ajustar un alimento',html:()=>`<div class="lg">${lgHead()}${sumCard()}${FOODS.slice(0,2).map((f,i)=>foodCard(f,i)).join('')}</div>${sheet(`<div style="display:flex;gap:12px;align-items:center"><span class="fi" style="width:48px;height:48px;border-radius:16px;background:#ffffff0a;display:flex;align-items:center;justify-content:center">${ic('egg',26)}</span><div style="flex:1"><h3>Huevos revueltos</h3><div class="cap">Coincidencia alta · base KPKN</div></div><button class="act">Cambiar</button></div>
  <div class="mgroup">Cantidad</div><div class="stepper"><button aria-label="Menos">−</button><b>2<small>huevos</small></b><button aria-label="Más">+</button></div>
  <div class="units">${chips(['Huevos','Gramos','Raciones'],0,34)}</div>
  <div class="mgroup">Cómo lo cocinaste</div><div class="drop3">${[['pan','Con aceite',1],['raw','Sin grasa',0],['drop','Con mantequilla',0]].map(o=>`<button class="${o[2]?'on':''}">${ic(o[0],24)}${o[1]}</button>`).join('')}</div>
  <div class="mgroup">Aporta</div><div style="display:grid;grid-template-columns:repeat(4,1fr);gap:6px">${[['182','kcal'],['13 g','prot.'],['2 g','carb.'],['14 g','grasa']].map(k=>`<div class="kpi" style="padding:8px 10px"><b style="font-size:calc(16px*var(--fs));margin:0">${k[0]}</b><span>${k[1]}</span></div>`).join('')}</div>
  <div class="cap" style="margin-top:8px">Toca una cifra para corregirla a mano.</div>
  <div style="display:flex;gap:8px;margin-top:16px"><button class="pill sec" style="flex:0 0 auto;color:var(--danger);border-color:#ff9b9255">${ic('trash',18)}</button><button class="pill pri" style="flex:1">Listo</button></div>`)}`,
 notes:{tags:[['keep','Mismos ajustes'],['fix','Sin deslizador defectuoso']],
  p:['Tocar una tarjeta abre su hoja: cantidad en la unidad natural del alimento (huevos, rebanadas, tazas) con −/+, y gramos o raciones a un toque. Sustituye al deslizador de 1 g, cuyo tope se fijaba al abrir y dejaba el pulgar al final cuando el valor escrito era mayor.',
     'La cocción (aceite, sin grasa, mantequilla) es una elección visual; los macros se ven en cuatro cifras y se corrigen tocándolas (hoy son botones de ±5 kcal que no hacen nada si el alimento tiene 0 kcal).',
     '«Cambiar» abre las otras coincidencias. La papelera quita el alimento con deshacer.']}});

scene({tab:'comida',id:'f-identity',title:'Duda: ¿es este el alimento?',html:()=>`<div class="lg">${lgHead()}${sumCard()}</div>${sheet(`<div class="ey">Escribiste «tostadas»</div><h3 style="margin-top:6px">¿Cuál se parece más?</h3>
  ${[['Pan de molde tostado','2 rebanadas · 158 kcal','h'],['Pan integral tostado','2 rebanadas · 146 kcal','m'],['Biscote','4 unidades · 152 kcal','m'],['Tostada de pan de barra','2 rebanadas · 190 kcal','m']].map((c,i)=>`<div class="cand">${radio(i===0)}<div class="tx"><strong>${c[0]}</strong><span>${c[1]}</span></div><span class="conf ${c[2]}">${c[2]==='h'?'Muy probable':'Posible'}</span></div>`).join('')}
  <button class="act" style="margin-top:6px">${ic('search',16)}Buscar otro</button>
  <button class="pill pri" style="width:100%;margin-top:14px">Usar este</button>`)}`,
 notes:{tags:[['keep','Duda «identidad»']],p:['La duda de identidad («¿Es este el alimento?» + «Coincidencia aproximada…») como una lista corta de candidatos con su ración y cuánto encaja, en vez de un texto con el nombre técnico de la coincidencia.']}});

scene({tab:'comida',id:'f-clar',title:'Todas las dudas',html:()=>`<div class="lg">${lgHead()}
  <div class="pending">${ic('qmark',16)}Así se ve cada tipo de duda dentro de su tarjeta</div>
  ${[['Arroz','150 g','¿La cantidad era en crudo o cocida?',['En crudo','Cocida'],'raw'],['Ensalada','1 plato','¿Qué llevaba la ensalada?',['Lechuga y tomate','Con atún','César','Otra'],'bowl'],['Tortilla','1 porción','¿De qué era la tortilla?',['De papas','De huevo','Trigo (wrap)','De maíz'],'egg'],['Galletas','1 envase','¿Comiste todo el envase?',['Todo (180 g)','Una porción (30 g)'],'bread'],['Lomo de cerdo','1 pieza','¿Qué corte era?',['Lomo','Solomillo','Chuleta'],'pan']].map((f,i)=>`<div class="food ask" style="animation-delay:${i*120}ms"><div class="food-h"><span class="fi">${ic(f[4],24)}</span><div class="tx"><strong>${f[0]}</strong><span>${f[1]}</span></div></div><div class="q"><p><i></i>${f[2]}</p><div class="qopts">${f[3].map(o=>`<span>${o}</span>`).join('')}</div></div></div>`).join('')}
  <div class="food ask" style="animation-delay:620ms"><div class="food-h"><span class="fi">${ic('drop',24)}</span><div class="tx"><strong>Verduras salteadas</strong><span>1 plato</span></div></div><div class="q"><p><i></i>¿Cuánto aceite tenía?</p><div class="drop3">${[['Nada',0],['Poco',1],['Medio',2],['Mucho',3]].map((o,j)=>`<button class="${j===1?'on':''}" style="padding:10px 2px"><span style="display:flex;gap:2px;height:22px;align-items:flex-end">${Array.from({length:Math.max(1,o[1])},()=>ic('drop',14)).join('')}</span><span style="${o[1]?'':'opacity:.4'}">${o[0]}</span></button>`).join('')}</div></div></div></div>`,
 notes:{tags:[['keep','Los ocho tipos de duda'],['fix','Respuestas marcadas']],
  p:['Los ocho tipos que existen (composición, tortilla, identidad, corte, crudo o cocido, aceite, envase y cantidad declarada) con el mismo patrón: una pregunta corta en la tarjeta y respuestas como chips. El aceite se elige con gotas.',
     'Arregla que hoy los chips de respuesta nunca se ven elegidos (<code>selected=false</code> siempre). La respuesta elegida queda en tinta y la tarjeta pierde el tono cálido con una transición.',
     '«Cantidad declarada» (no pudimos leer tu medida) abre directamente el ajuste de cantidad.']}});

scene({tab:'comida',id:'f-saved',title:'Guardado',html:()=>`<div class="lg">${lgHead()}${sumCard()}</div><div class="savedone">
  <svg class="draw go" viewBox="0 0 120 120" width="140" height="140" aria-hidden="true"><circle class="ln" pathLength="1" cx="60" cy="60" r="50" fill="none" stroke="#43d18c" stroke-width="3"/><path class="ln" pathLength="1" d="M38 62l15 15 30-32" fill="none" stroke="#43d18c" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" style="animation-delay:.5s"/></svg>
  <b>Desayuno guardado</b><span>548 kcal · te quedan 1.752 para hoy</span></div>`,after:drawAfter,
 notes:{tags:[['keep','Check animado']],p:['El check verde que se dibuja (ya existe) con lo que importa justo después de guardar: cuánto te queda del día. Se cierra solo a los 0,8 s.']}});

scene({tab:'comida',id:'f-error',title:'Si algo falla',html:()=>`<div class="lg">${lgHead()}${mealChips()}
  <div class="write"><div class="txt" style="font-size:calc(16px*var(--fs))">Un plato de lentejas de mi abuela con chorizo</div></div>
  <div class="mod" style="margin-top:12px;border-color:#f7cf7333"><div class="ey" style="color:var(--ene)">${ic('info',14)} Lo leí a medias</div><p class="body" style="margin:8px 0 10px">Reconocí las lentejas y el chorizo, pero no las cantidades. Las dejé en una ración normal: revísalas antes de guardar.</p><div style="display:flex;gap:8px"><button class="pill sec sm">Volver a analizar</button><button class="pill sec sm">Buscar a mano</button></div></div>
  ${foodCard({n:'Lentejas guisadas',q:'1 plato · 300 g',k:342,p:20,c:44,f:8,ic:'bowl',approx:true},0)}${foodCard({n:'Chorizo',q:'40 g',k:182,p:10,c:1,f:15,ic:'pan',approx:true},1)}</div>${saveBar(524)}`,
 notes:{tags:[['fix','Sin jerga técnica']],p:['Los cuatro avisos de error de hoy hablan de «dataset de porciones no cargado» o muestran el nombre de la excepción. Ahora dicen qué se reconoció, qué se supuso y qué puedes hacer, con un icono de aviso (no de información). El detalle técnico queda solo en los registros de la app.']}});
