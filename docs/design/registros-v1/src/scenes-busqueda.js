/* ================= REGISTRO DE COMIDA · BUSCAR Y MENÚS ================= */
const sfield=(t='',ph='Busca un alimento')=>`<div class="searchf">${ic('search',18)}<span class="t">${t||`<span style="color:var(--dim)">${ph}</span>`}${t?'<span class="caret"></span>':''}</span><button class="ib" aria-label="Escanear código">${ic('barcode')}</button></div>`;
const sres=(n,s,k,{star=false,src=''}={})=>`<div class="sres"><div class="tx"><strong>${n}</strong><span>${s}${src?` · <span style="color:var(--faint)">${src}</span>`:''}</span></div><span class="tn" style="font:700 calc(14px*var(--fs)) Syne">${k}</span><button class="ib" aria-label="Favorito" style="width:36px;height:36px;color:${star?'var(--ene)':'var(--dim)'}">${ic(star?'starOn':'star',18)}</button><button class="add" aria-label="Añadir ${n}">${ic('plus',18)}</button></div>`;

scene({tab:'busqueda',id:'s-start',title:'Buscar · al abrir',html:()=>`<div class="lg">${lgHead()}${mealChips()}${seg2(1)}${sfield()}
  <div class="group-l">Recientes</div><div class="mod tight">${sres('Avena en copos','40 g · desayuno de ayer','150')}${sres('Plátano','1 mediano','105',{star:true})}${sres('Leche semidesnatada','200 ml','92')}</div>
  <div class="group-l">Atajos</div><div class="opt-grid">${[['barcode','Escanear','Código de barras'],['bolt','Entrada rápida','Solo calorías y macros'],['book','Recetas','Tus 4 recetas'],['copy','Plantillas','Comidas guardadas']].map(o=>`<button class="opt" style="min-height:92px"><span class="ic">${ic(o[0])}</span><strong>${o[1]}</strong><span>${o[2]}</span></button>`).join('')}</div></div>`,
 notes:{tags:[['new','Recientes'],['move','Recetas y plantillas a atajos']],
  p:['Antes de escribir ya hay algo útil: recientes (no existían) y cuatro atajos. Recetas, plantillas, escáner y entrada rápida estaban repartidos por la hoja, el escáner y la pantalla de Nutrición; ahora están juntos aquí.',
     'El código de barras está dentro del campo, como en cualquier buscador de tienda.']}});

scene({tab:'busqueda',id:'s-loading',title:'Buscando',html:()=>`<div class="lg">${lgHead()}${seg2(1)}${sfield('yogur gri')}<div style="margin-top:12px">${'<div class="skel"></div>'.repeat(5)}</div></div>`,
 notes:{tags:[['fix','Estado de carga']],p:['La búsqueda busca mientras escribes (con 250 ms de espera) y muestra filas de carga. Hoy hay que pulsar «Buscar» y no hay indicador ni manejo de errores.']}});

scene({tab:'busqueda',id:'s-results',title:'Resultados',html:()=>`<div class="lg">${lgHead()}${seg2(1)}${sfield('yogur griego')}
  <div style="display:flex;gap:6px;margin:12px 0 4px">${chips(['Todo','Míos','Con marca','Sin marca'],0,32)}</div>
  <div class="mod tight" style="margin-top:8px">${sres('Yogur griego natural','125 g · 1 envase','146',{star:true,src:'KPKN'})}${sres('Yogur griego 0 %','125 g · 1 envase','72',{src:'Hacendado'})}${sres('Yogur griego con miel','125 g · 1 envase','168',{src:'Danone'})}${sres('Yogur griego casero','Tu receta · 1 bol','210',{src:'Receta'})}${sres('Yogur griego ligero','100 g','58',{src:'Oikos'})}</div>
  <div class="toast glass" style="bottom:28px">${ic('check',18)}Yogur griego natural añadido<button class="act">Deshacer</button></div></div>`,
 notes:{tags:[['fix','Una fila por resultado'],['new','Filtros']],
  p:['Cada resultado en <b>una sola fila</b>: nombre, ración natural y origen, calorías de esa ración, estrella y «+». Hoy la estrella y «Editar alimento propio» ocupan una línea extra debajo de cada resultado y la coincidencia («media/baja») se muestra como texto técnico.',
     '«+» añade con la ración mostrada y avisa con deshacer; tocar la fila abre la ficha para elegir cantidad. Filtros rápidos: todo, mis alimentos, con marca, sin marca.',
     'Un solo verbo en toda la app: <b>«Añadir»</b> (hoy conviven «Agregar» y «Añadir»).']}});

scene({tab:'busqueda',id:'s-detail',title:'Ficha de un alimento',html:()=>`<div class="lg">${lgHead()}${seg2(1)}${sfield('yogur griego')}</div>${sheet(`<div class="ey">KPKN · verificado</div><h3 style="margin-top:6px">Yogur griego natural</h3>
  <div class="mgroup">Cuánto</div><div class="stepper"><button aria-label="Menos">−</button><b>1<small>envase · 125 g</small></b><button aria-label="Más">+</button></div>
  <div class="units">${chips(['Envases','Gramos','Cucharadas'],0,34)}</div>
  <div style="margin-top:14px">${sumCard(146)}</div>
  <div class="mgroup">Más nutrientes</div><div class="ratio"><span>Fibra</span><b>0 g</b></div><div class="ratio"><span>Azúcares</span><b>4,6 g</b></div><div class="ratio"><span>Sodio</span><b>55 mg</b></div>
  <div style="display:flex;gap:8px;margin-top:16px"><button class="pill sec" style="flex:0 0 auto;color:var(--ene)">${ic('starOn',18)}</button><button class="pill pri" style="flex:1">Añadir al desayuno</button></div>`)}`,
 notes:{tags:[['new','La ficha que faltaba']],p:['Hoy no hay ficha: los nutrientes solo se ven al «Ajustar» un alimento ya añadido. Ahora la ficha tiene cantidad en la unidad natural, el anillo de macros, fibra, azúcares y sodio, favorito y «Añadir al desayuno».']}});

scene({tab:'busqueda',id:'s-none',title:'Sin resultados',html:()=>`<div class="lg">${lgHead()}${seg2(1)}${sfield('arepa de choclo')}
  <div style="text-align:center;padding:40px 10px 10px"><svg class="draw go" viewBox="0 0 120 80" width="160" aria-hidden="true"><path class="ln" pathLength="1" d="M10 60 Q60 10 110 60" fill="none" stroke="#f2eee6" stroke-width="1.4" opacity=".6"/><circle class="ln" pathLength="1" cx="60" cy="48" r="16" fill="none" stroke="#f49a6e" stroke-width="1.6"/></svg>
  <div class="syne" style="font:700 calc(20px*var(--fs)) Syne;margin-top:12px">No encontré «arepa de choclo»</div><p class="body">Descríbela y la estimamos, o créala con sus datos.</p></div>
  <button class="pill pri" style="width:100%">${ic('edit',18)}Describirla</button><button class="pill sec" style="width:100%;margin-top:8px">${ic('plus',18)}Crear alimento</button></div>`,
 notes:{tags:[['keep','Mismas salidas']],p:['«Describirla» pasa el texto a la pestaña Describir y lo analiza. «Crear alimento» abre el alimento propio con el nombre ya puesto (hoy esa entrada está duplicada en la búsqueda y en el escáner con textos distintos).']}});

scene({tab:'busqueda',id:'s-scan',title:'Escanear código',html:()=>`<div class="scan"><div class="frame"><div class="laser"></div></div>
  <div style="position:absolute;top:44px;left:10px;right:10px;display:flex;justify-content:space-between;z-index:2"><button class="fb glass" aria-label="Cerrar">${ic('close')}</button><button class="fb glass" aria-label="Linterna">${ic('bolt')}</button></div>
  <div style="position:absolute;left:0;right:0;top:200px;text-align:center;font-weight:600;z-index:2">Centra el código en el recuadro</div>
  <div class="sheet" style="max-height:none"><div class="grab"></div><div class="ey" style="color:var(--done)">${ic('check',14)} Encontrado · base global</div><h3 style="margin-top:6px">Barrita de avena y chocolate</h3><div class="cap">Nature Valley · 42 g · 191 kcal</div>
  <div style="display:flex;gap:8px;margin-top:14px"><button class="pill sec" style="flex:1">Escanear otro</button><button class="pill pri" style="flex:1">Añadir</button></div></div></div>`,
 notes:{tags:[['keep','Mismo escáner']],p:['El resultado sube desde abajo sin salir de la cámara, para escanear varios seguidos. «Escribe el código» queda en la hoja si no lee. La calidad del dato (propio o de la base global) se dice en una palabra.']}});

scene({tab:'busqueda',id:'s-quick',title:'Entrada rápida',html:()=>`<div class="lg">${lgHead()}${seg2(1)}${sfield()}</div>${sheet(`<h3>Entrada rápida</h3><div class="sub">Para cuando solo sabes las calorías.</div>
  <div class="stepper"><button aria-label="Menos">−</button><b>450<small>kcal</small></b><button aria-label="Más">+</button></div>
  <div class="mgroup">Macros (opcional)</div><div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px">${[['Proteína','30 g'],['Carbos','—'],['Grasa','—']].map(m=>`<div class="field" style="margin:0"><small>${m[0]}</small><div class="tn">${m[1]}</div></div>`).join('')}</div>
  <div class="field"><small>Nombre (opcional)</small><div>Menú del día</div></div>
  <button class="pill pri" style="width:100%;margin-top:16px">Añadir a la comida</button>`)}`,
 notes:{tags:[['keep','Misma entrada rápida']],p:['De pantalla aparte a hoja. Las calorías se escriben con el teclado numérico o con −/+ de 10.']}});

scene({tab:'busqueda',id:'s-settings',title:'Ajustes del registro',html:()=>`<div class="lg">${lgHead()}</div>${sheet(`<h3>Ajustes del registro</h3>
  ${mi('ruler','Mis medidas de casa','Cuánto es tu taza, tu plato o tu cucharón')}${mi('star','Favoritos y recetas','Ordenar y editar')}${mi('history','Olvidar lo aprendido','Las porciones y elecciones que KPKN recuerda de ti','danger')}`)}`,
 notes:{tags:[['keep','Mismos ajustes'],['fix','Olvidar pide confirmación']],p:['«Olvidar lo aprendido» hoy vacía también la comida que estás registrando, sin preguntar. Ahora pide confirmación y no toca el borrador.']}});

scene({tab:'busqueda',id:'s-forget',title:'Confirmar olvidar',html:()=>`<div class="lg">${lgHead()}</div><div class="scrim"></div><div class="sheet" style="bottom:auto;top:230px;left:20px;right:20px;border-radius:28px"><h3>¿Olvidar lo aprendido?</h3><div class="sub">KPKN volverá a preguntarte cómo cocinas, qué leche usas y tus porciones habituales. Tus comidas registradas no cambian.</div><div style="display:flex;gap:8px;margin-top:8px"><button class="pill sec sm" style="flex:1">Cancelar</button><button class="pill sm" style="flex:1;background:#ff9b9226;color:var(--danger)">Olvidar</button></div></div>`,
 notes:{p:['Confirmación con la consecuencia en una frase.']}});

scene({tab:'busqueda',id:'s-measures',title:'Medidas de casa',html:()=>`<div class="lg">${lgHead()}</div>${sheet(`<h3>Mis medidas de casa</h3><div class="sub">Cuando escribas «una taza» o «un plato», usaremos estas cantidades.</div>
  ${[['Taza','240 ml'],['Vaso','250 ml'],['Plato','300 g'],['Plato hondo','350 ml'],['Bol','400 ml'],['Cucharada','15 ml'],['Cucharadita','5 ml'],['Cucharón','120 ml'],['Copa','150 ml']].map(m=>`<div class="rowx" style="min-height:50px"><div class="tx"><strong style="font-weight:500">${m[0]}</strong></div><span class="chip tn" style="height:34px">${m[1]}</span></div>`).join('')}
  <div style="display:flex;gap:8px;margin-top:12px"><button class="pill sec sm" style="flex:1">Restablecer</button><button class="pill pri sm" style="flex:1">Guardar</button></div>`)}`,
 notes:{tags:[['keep','Las nueve medidas']],p:['Mismas nueve medidas; cada una se toca para escribir su cantidad.']}});

scene({tab:'busqueda',id:'s-date',title:'Fecha y hora',html:()=>`<div class="lg">${lgHead()}</div>${sheet(`<h3>¿Cuándo fue?</h3>
  ${chips(['Ahora','Hace 1 h','Hoy al mediodía','Ayer'],0,36)}
  <div style="display:grid;grid-template-columns:repeat(7,1fr);gap:4px;text-align:center;margin-top:14px" class="tn">${['L','M','X','J','V','S','D'].map(d=>`<span class="cap">${d}</span>`).join('')}${Array.from({length:35},(_,i)=>{const n=i-2;const on=n===27;const inm=n>=1&&n<=31;const fut=n>27;return `<span style="height:36px;display:flex;align-items:center;justify-content:center;border-radius:50%;${on?'background:var(--ink);color:#111;font-weight:600':''};color:${on?'#111':fut?'#ffffff22':inm?'var(--ink)':'transparent'}">${inm?n:''}</span>`}).join('')}</div>
  <div class="mgroup">Hora</div><div style="display:flex;justify-content:center;gap:10px;align-items:center"><span class="big" style="font-size:calc(40px*var(--fs))">08</span><span class="big" style="font-size:calc(40px*var(--fs))">:</span><span class="big" style="font-size:calc(40px*var(--fs))">40</span></div>
  <button class="pill pri" style="width:100%;margin-top:14px">Listo</button>`)}`,
 notes:{tags:[['fix','Hora sin escribir HH:mm']],p:['Atajos (ahora, hace una hora, mediodía, ayer), calendario sin días futuros y la hora con rueda. Hoy elegir el día cierra la hoja y reinicia la hora, y la hora es un campo de texto «HH:mm».']}});

scene({tab:'busqueda',id:'s-rowmenu',title:'⋯ de una comida registrada',html:()=>`<div class="scroll" style="padding-top:52px"><div class="ptitle">Nutrición</div></div>${sheet(`<h3>Desayuno</h3><div class="sub">08:40 · 548 kcal · 4 alimentos</div>
  ${mi('edit','Editar','')}${mi('copy','Duplicar','En la misma comida')}${mi('cal','Copiar a…','Hoy, mañana o los próximos días')}${mi('swap','Mover a…','Otra comida u otro día')}${mi('star','Guardar como plantilla','Para repetirla con un toque')}${mi('trash','Eliminar','Se puede deshacer','danger')}`)}`,
 notes:{tags:[['keep','Mismas acciones']],p:['El menú de hoy con iconos y una línea por acción. Eliminar mantiene el deshacer.']}});

scene({tab:'busqueda',id:'s-discard',title:'¿Descartar?',html:()=>`<div class="lg">${lgHead()}${sumCard()}</div><div class="scrim"></div><div class="sheet" style="bottom:auto;top:250px;left:20px;right:20px;border-radius:28px"><h3>¿Descartar esta comida?</h3><div class="sub">Tienes 4 alimentos sin guardar.</div><div style="display:flex;gap:8px;margin-top:8px"><button class="pill sec sm" style="flex:1">Seguir editando</button><button class="pill sm" style="flex:1;background:#ff9b9226;color:var(--danger)">Descartar</button></div></div>`,
 notes:{tags:[['keep','Igual que hoy']],p:['Mismo diálogo con la piel KPKN (hoy usa la de Material).']}});
