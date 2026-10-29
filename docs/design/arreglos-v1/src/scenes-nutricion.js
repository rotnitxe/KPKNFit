/* ================= NUTRICIÓN PLANA ================= */
// El caso de las capturas de hoy: «Papas fritas medianas» en la merienda (150 g estimados, 297 kcal).
Object.assign(P,{minus:'<path d="M5 12h14"/>'});
const NPI={p:'var(--np-p)',c:'var(--np-c)',f:'var(--np-f)'};
const npK=x=>fmtN(Math.round(x));
NF.papas={n:'Papas fritas caseras',q:'150 g',g:'150 g',src:'base KPKN',cnt:30,v:[0,.1,.04,2.2,.4,30,0,20,14,0,3.7,15, 15,120,35,1,.5,.5,null,300,700, 3.5,.86,0]};
const npRows=(g=150)=>NF.papas.v.map((a,i)=>{const amt=a==null?null:a*g/150;return {amt,pct:pctOf(i,amt)}});
// Preparaciones con ficha propia (base por 200 g: kcal, P, C, G) y el aceite que admite cada una (gramos por 200 g).
const NP_PREP={
 'Cocido':{b:[174,4,40,.2],oil:null},
 'Al horno':{b:[186,5,42,.2],oil:[['Sin',0],['Poco',5],['Normal',12]]},
 'Salteado':{b:[174,4,40,.2],oil:[['Poco',5],['Normal',12],['Mucho',20]]},
 'Frito':{b:[174,4,37,.2],oil:[['Poco',14],['Normal',24.7],['Mucho',35]]}};
function npCalc(g,prep,oil){const d=NP_PREP[prep],s=g/200,o=d.oil?d.oil[Math.min(oil,d.oil.length-1)][1]*s:0;return {k:d.b[0]*s+o*9,p:d.b[1]*s,c:d.b[2]*s,f:d.b[3]*s+o,ok:o*9}}
const npBar=(p,c,f,h=8)=>`<div class="np-bar" style="height:${h}px"><i style="flex:${p*4};background:${NPI.p}"></i><i style="flex:${c*4};background:${NPI.c}"></i><i style="flex:${f*9};background:${NPI.f}"></i></div>`;
const npMac=(p,c,f)=>`<div class="np-mac">${[[p,'Proteína','p'],[c,'Carbos','c'],[f,'Grasa','f']].map(m=>`<div><b>${npK(m[0])} g</b><span style="--c:${NPI[m[2]]}">${m[1]}</span></div>`).join('')}</div>`;
const npSum=(m,cap='esta comida')=>`<div class="np-sum"><div class="np-mh"><div class="np-k"><b>${npK(m.k)}</b><span>kcal</span></div><span class="cap">${cap}</span></div>${npBar(m.p,m.c,m.f)}${npMac(m.p,m.c,m.f)}</div>`;

// La huella: un radio por nutriente (24), agrupados en vitaminas, minerales y otros. Largo = % de tu día (tope 100 %).
// Comida: los tres que más aportan en tinta plena y con nombre. Día: opacidad por nivel. Topes (sodio, cafeína) huecos.
function npRad(rows,{size=150,day=false,still=false,center=null,label=true,bg='#121212'}={}){
  const c=size/2,r0=size*.19,r1=size*.37,grp=i=>i<12?0:i<21?1:2;
  const top=day?[]:rows.map((r,i)=>[r.pct||0,i]).filter(x=>!NU[x[1]][5]).sort((a,b)=>b[0]-a[0]).slice(0,3).map(x=>x[1]);
  let s='';
  rows.forEach((r,i)=>{const a=(-90+6+grp(i)*12+(i+.5)*13.5)*Math.PI/180,cs=Math.cos(a),sn=Math.sin(a);
    const pt=rr=>`${(c+cs*rr).toFixed(1)} ${(c+sn*rr).toFixed(1)}`;
    s+=`<path d="M${pt(r0)}L${pt(r1)}" stroke="#ffffff1c" stroke-width="1.5" stroke-linecap="round"/>`;
    if(r.amt==null){s+=`<circle cx="${(c+cs*(r0+2)).toFixed(1)}" cy="${(c+sn*(r0+2)).toFixed(1)}" r="2.4" fill="${bg}" stroke="#ffffff73" stroke-width="1" stroke-dasharray="1.3 1.3"/>`;return}
    const pct=r.pct||0;if(pct<=0)return;
    const lim=NU[i][5],over=lim&&pct>100,end=r0+Math.max(3,(r1-r0)*Math.min(1,pct/100));
    const op=over?1:day?(pct>=100?1:pct>=70?.6:.32):(top.includes(i)?1:.45),col=over?'var(--danger)':'var(--ink)';
    const at=still?'':`class="sp" style="animation-delay:${i*22}ms"`;
    s+=`<path ${at} pathLength="1" d="M${pt(r0)}L${pt(end)}" stroke="${col}" stroke-opacity="${op}" stroke-width="5" stroke-linecap="round" fill="none"/>`;
    if(lim&&!over)s+=`<path ${at} pathLength="1" d="M${pt(r0)}L${pt(end)}" stroke="${bg}" stroke-width="2.2" stroke-linecap="round" fill="none"/>`;
    if(label&&top.includes(i)){const lr=r1+9,anc=Math.abs(cs)<.35?'middle':cs>0?'start':'end';s+=`<text x="${(c+cs*lr).toFixed(1)}" y="${(c+sn*lr+4).toFixed(1)}" text-anchor="${anc}">${NU[i][0]}</text>`}
  });
  if(center)s+=`<text class="c1" x="${c}" y="${c+4}" text-anchor="middle">${center[0]}</text><text class="c2" x="${c}" y="${c+19}" text-anchor="middle">${center[1]}</text>`;
  return `<svg class="np-rad" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" role="img" aria-label="Vitaminas y minerales: un radio por nutriente">${s}</svg>`}
const npTop=rows=>`<div class="np-top"><span class="np-tl">Lo que más aporta</span>${rows.map((r,i)=>[r.pct||0,i]).filter(x=>!NU[x[1]][5]).sort((a,b)=>b[0]-a[0]).slice(0,3).map(x=>`<div><span>${NU[x[1]][1]}</span><b>${Math.round(x[0])} %</b></div>`).join('')}</div>`;
const npMicro=(rows,{still=false,cov='1 de 1 alimento con ficha completa',sheetMode=false}={})=>`<div class="${sheetMode?'':'np-mod'}"><div class="np-mh"><span class="ey">Vitaminas y minerales</span><span class="cap">% de tu día</span></div>
  <div class="np-mrow">${npRad(rows,{still,bg:sheetMode?'#141414':'#121212'})}${npTop(rows)}</div>
  <div class="np-cov">${ic('info',15)}<span>${cov}</span>${ic('fwd',15)}</div></div>`;

/* --- Registro --- */
const NP_OPTS=[['Lo estimado',150],['Plato',250],['Puñado',40],['Taza',60]];
function npResult(opt=null,still=false){const g=opt==null?150:NP_OPTS[opt][1],m=npCalc(g,'Frito',1);
  return `<div class="lg np">${lgHead()}${mealChips(2)}
  <div class="write np-write done"><div class="txt"><span class="np-tok">Papas fritas medianas</span></div></div>
  ${opt==null?`<div class="np-pend"><i></i>1 duda por revisar<span>· o guarda con lo estimado</span></div>`:''}
  <div style="height:10px"></div>${npSum(m)}${npMicro(npRows(g),{still})}
  <div class="np-food"><div class="np-fh"><span class="fi">${ic('bowl',20)}</span><div class="tx"><strong>Papas fritas caseras</strong><span>${g} g · ${opt==null?'aprox.':NP_OPTS[opt][0].toLowerCase()}</span></div><div class="k"><b>${opt==null?'≈ ':''}${npK(m.k)}</b><span>kcal</span></div></div>
  ${npBar(m.p,m.c,m.f,4)}
  <div class="np-q"><p>${opt==null?'<i></i>':''}¿Qué porción fue?</p><div class="np-opts">${NP_OPTS.map((o,i)=>`<button data-opt="${i}" class="${i===opt?'on':''}">${o[0]}<small>· ${o[1]} g</small></button>`).join('')}</div></div></div>
  <button class="act np-addfood">${ic('plus',16)}Añadir otro alimento</button></div>
  <div class="np-save"><div class="k"><b>${npK(m.k)} kcal</b><span>1 alimento</span></div><button class="go">Guardar en merienda</button></div>`}
function npResultWire(ph){const host=ph.querySelector('.np-host');if(!host)return;
  host.querySelectorAll('[data-opt]').forEach(b=>b.onclick=()=>{const y=host.querySelector('.lg').scrollTop;host.innerHTML=npResult(Number(b.dataset.opt),true);host.querySelector('.lg').scrollTop=y;npResultWire(ph)})}

scene({tab:'registro',id:'r-write',title:'Describir · escrito',html:()=>`<div class="lg np">${lgHead()}${mealChips(2)}${seg2()}
  <div class="write np-write focus"><div class="txt">Papas fritas medianas<span class="caret"></span></div>
  <div class="np-acts"><button class="ib fill" aria-label="Dictar">${ic('mic')}</button><button class="pill pri sm">${ic('spark',16)}Analizar</button></div></div></div>`,
 notes:{img:'hoy_escribir',imgCap:'Hoy: «Enter para analizar» se apila letra a letra en vertical y el botón tapa el texto.',
  tags:[['out','Sale «Enter para analizar»'],['fix','Botón a la derecha, sin tapar']],
  p:['El texto «Enter para analizar» sale. Lo dice la propia tecla Enter del teclado, que pasa a llamarse <b>«Analizar»</b> y analiza al pulsarla.',
     'El campo es plano (superficie 1, borde fino) y el texto crece hacia abajo. Abajo: el micrófono a la izquierda y «Analizar» compacto a la derecha; ninguno tapa lo que escribes.']}});

scene({tab:'registro',id:'r-result',title:'Resultado · con una duda',after:ph=>npResultWire(ph),html:()=>`<div class="np-host">${npResult()}</div>`,
 notes:{img:'hoy_registro',imgCap:'Hoy: anillo con la cifra apretada, aviso en caja amarilla, «porción (150 g)» repite a «Lo estimado» y «1 alimento · 3 g d…» cortado.',
  tags:[['fix','Sin anillo'],['fix','La duda es una línea'],['out','Sale la opción repetida']],
  p:['Arriba, el total en una línea: <b>297 kcal</b>, la barra de macros en tres tonos de tinta y las tres cifras en columnas, todas del mismo tamaño y a su medida.',
     'La duda deja de ser una caja amarilla: es una línea con un punto de acento, «1 duda por revisar · o guarda con lo estimado». En la tarjeta del alimento, el mismo punto junto a la pregunta.',
     'Las opciones van en mayúscula y sin repetir: «porción (150 g)» sale porque vale lo mismo que «Lo estimado». «Seguir con la estimación» sale: guardar ya sigue con lo estimado.',
     'La barra de guardar es plana y no corta nada: «297 kcal · 1 alimento». <b>Toca una porción:</b> la cifra se recalcula y la duda desaparece.']}});

scene({tab:'registro',id:'r-answered',title:'Resultado · duda resuelta',after:ph=>npResultWire(ph),html:()=>`<div class="np-host">${npResult(1,true)}</div>`,
 notes:{tags:[['keep','Mismo resultado, sin acento']],p:['Con la duda resuelta, el acento desaparece de la pantalla: todo queda en tinta. La tarjeta dice la porción elegida y la huella se recalcula con los gramos nuevos.']}});

/* --- Ajustar un alimento --- */
const NP_SIZES=[['Pequeña',100],['Mediana',200],['Grande',300],['Extra',400]];
const NP_RACION=150;
function npSheetInner(st){const m=npCalc(st.g,st.prep,st.oil),d=NP_PREP[st.prep],rac=st.units===1;
  const qty=rac?fmtN(Math.round(st.g/NP_RACION*2)/2):String(st.g),unit=rac?(st.g/NP_RACION===1?'ración':'raciones'):'g';
  return `<div class="grab"></div>
  <div class="np-sh-h"><span class="fi">${ic('bowl',20)}</span><div class="tx"><h3>Papas fritas caseras</h3><div class="cap">Coincidencia alta · base KPKN</div></div><button class="act">Cambiar</button></div>
  <div class="group-l">Cantidad</div>
  <div class="np-step"><button data-a="minus" aria-label="Menos">${ic('minus',20)}</button><b>${qty}<small>${unit}</small></b><button data-a="plus" aria-label="Más">${ic('plus',20)}</button></div>
  <div class="np-seg soft" style="margin-top:8px">${['Gramos','Raciones'].map((u,i)=>`<button data-a="unit:${i}" class="${i===st.units?'on':''}">${u}</button>`).join('')}</div>
  <div class="group-l">Tamaño</div>
  <div class="np-seg">${NP_SIZES.map((s,i)=>`<button data-a="size:${i}" class="${s[1]===st.g?'on':''}">${s[0]}<small>${s[1]} g</small></button>`).join('')}</div>
  <div class="group-l">Cómo lo preparaste</div>
  <div class="np-prep">${Object.keys(NP_PREP).map(p=>`<button data-a="prep:${p}" class="${p===st.prep?'on':''}">${p}</button>`).join('')}</div>
  ${d.oil?`<div class="np-oil-h"><span>Aceite</span><b>${m.ok>0?'+'+npK(m.ok)+' kcal':'sin aceite'}</b></div>
  <div class="np-seg soft">${d.oil.map((o,i)=>`<button data-a="oil:${i}" class="${i===st.oil?'on':''}">${o[0]}</button>`).join('')}</div>`:`<div class="np-oil-none">Cocidas en agua: no hay aceite que sumar.</div>`}
  <div class="np-sep"></div>
  <div class="np-mod"><div class="np-mh"><span class="ey">Aporta</span><span class="cap">${st.g} g</span></div>
  <div class="np-k" style="margin-top:10px"><b>${npK(m.k)}</b><span>kcal</span></div>${npBar(m.p,m.c,m.f)}${npMac(m.p,m.c,m.f)}
  <div class="cap" style="margin-top:12px">Toca una cifra para corregirla a mano.</div></div>
  <div class="np-sep"></div>
  <div class="np-mod">${npMicro(npRows(st.g),{still:st.still,cov:'Todos los nutrientes · 30 de 38 con dato',sheetMode:true})}</div>`}
function npSheet(st){return `<div class="np-host"><div class="np-behind">${npResult()}</div></div><div class="scrim np-fade"></div><div class="sheet np-sheet np-rise" data-sheet>${npSheetInner(st)}</div>`}
function npSheetWire(ph,st){const sh=ph.querySelector('[data-sheet]');if(!sh)return;
  if(st.scroll)sh.scrollTop=st.scroll;
  sh.onclick=e=>{const b=e.target.closest('[data-a]');if(!b)return;const [a,v]=b.dataset.a.split(':');
    if(a==='minus')st.g=Math.max(st.units?75:10,st.g-(st.units?75:10));
    if(a==='plus')st.g=st.g+(st.units?75:10);
    if(a==='unit'){st.units=Number(v);if(st.units)st.g=Math.max(75,Math.round(st.g/75)*75)}
    if(a==='size')st.g=NP_SIZES[Number(v)][1];
    if(a==='prep'){st.prep=v;const o=NP_PREP[v].oil;st.oil=o?1:0}
    if(a==='oil')st.oil=Number(v);
    st.still=true;const y=sh.scrollTop;sh.classList.remove('np-rise');sh.innerHTML=npSheetInner(st);sh.scrollTop=y}}
const npSt=(o={})=>({g:200,units:0,prep:'Frito',oil:1,still:false,...o});

scene({tab:'ajuste',id:'a-sheet',title:'La hoja · frito',after:ph=>npSheetWire(ph,npSt()),html:()=>npSheet(npSt()),
 notes:{img:'hoy_ajuste',imgCap:'Hoy: «200 g» en Syne enorme, círculos de tamaño, símbolos en «Cómo lo cocinaste» («Con» cortado) y «Variante» repitiendo la elección.',
  tags:[['fix','Cifra a su medida'],['fix','Tamaño segmentado'],['fix','Una sola elección']],
  p:['Hoja plana (#141414), sin degradado ni vidrio. La cantidad va en Inter 26 con su unidad al lado; − y + son botones redondos del mismo tono.',
     '<b>Tamaño</b> deja los círculos: es un control de cuatro tramos con los gramos debajo (Pequeña 100 · Mediana 200 · Grande 300 · Extra 400 g). Si escribes otra cantidad, ningún tramo queda marcado.',
     '<b>Cómo lo preparaste</b> une «Cómo lo cocinaste» y «Variante» en chips de texto, sin símbolos, en un orden fijo: Crudo · Cocido · Al vapor · A la plancha · Al horno · Salteado · Frito. Solo aparecen los que tienen sentido para el alimento: a las papas no se les ofrece «Crudo».',
     '<b>Aceite</b> es una fila aparte que dice cuánto suma («+222 kcal»). <b>Toca cualquier opción:</b> la hoja recalcula.']}});

scene({tab:'ajuste',id:'a-horno',title:'Al horno · el aceite sigue ahí',after:ph=>npSheetWire(ph,npSt({prep:'Al horno',oil:1,still:true})),html:()=>npSheet(npSt({prep:'Al horno',oil:1,still:true})).replace('np-rise','').replace('scrim np-fade','scrim'),
 notes:{tags:[['fix','Arregla: el aceite desaparecía']],
  p:['Hoy, al pasar de «Frito» a «Al horno», «Cómo lo cocinaste» desaparece entero: solo se muestra para fritos o con aceite aplicado.',
     'Aquí la fila de aceite se queda y cambia sus opciones a lo que tiene sentido en el horno: <b>Sin · Poco · Normal</b>. En frito y salteado son Poco · Normal · Mucho.']}});

scene({tab:'ajuste',id:'a-cocido',title:'Cocido · sin aceite',after:ph=>npSheetWire(ph,npSt({prep:'Cocido',oil:0,still:true})),html:()=>npSheet(npSt({prep:'Cocido',oil:0,still:true})).replace('np-rise','').replace('scrim np-fade','scrim'),
 notes:{tags:[['keep','Una línea, no un hueco']],p:['Cuando la preparación no lleva aceite (cocido, al vapor, crudo), la fila se sustituye por una línea que lo dice. Nada salta de sitio.']}});

scene({tab:'ajuste',id:'a-aporta',title:'Aporta y vitaminas',after:ph=>npSheetWire(ph,npSt({still:true,scroll:520})),html:()=>npSheet(npSt({still:true})).replace('np-rise','').replace('scrim np-fade','scrim'),
 notes:{tags:[['fix','Aporta en una línea'],['keep','Corregir a mano']],
  p:['«Aporta» deja las cuatro cajas con cifras enormes: las kcal grandes, la barra de macros y las tres cifras en columnas, igual que el resumen del registro.',
     '«Toca una cifra para corregirla a mano» se queda. Debajo, la huella del alimento y la fila que abre todos los nutrientes.']}});

scene({tab:'ajuste',id:'a-motion',title:'Cómo entra la hoja',after:ph=>{npSheetWire(ph,npSt());const b=document.querySelector('[data-replay]');if(b)b.onclick=()=>{const sh=ph.querySelector('[data-sheet]'),sc=ph.querySelector('.scrim');[sh,sc].forEach(el=>{const c=el===sh?'np-rise':'np-fade';el.classList.remove(c);void el.offsetWidth;el.classList.add(c)})}},html:()=>npSheet(npSt()),
 notes:{tags:[['new','Transición de entrada y salida']],
  p:['<button class="np-replay" data-replay>'+'Repetir la entrada</button>',
     'El fondo se oscurece en <b>220 ms</b>. La hoja sube desde abajo en <b>420 ms</b> con freno suave (cubic-bezier 0.22, 1, 0.36, 1) y su contenido aparece 120 ms después, subiendo 10 px. Al cerrar, lo mismo al revés y algo más rápido.',
     'Vale para todas las hojas del registro y de Nutrición. Con «reducir movimiento» activado, la hoja aparece sin desplazamiento.']}});

/* --- Vitaminas y minerales --- */
const npDayLegend=rows=>{const goal=rows.map((r,i)=>[r,i]).filter(x=>!NU[x[1]][5]);const n=f=>goal.filter(x=>f(x[0].pct||0)).length;const over=rows.map((r,i)=>[r,i]).filter(x=>NU[x[1]][5]&&(x[0].pct||0)>100);
  return `<div class="np-leg"><div><i></i><b>${n(p=>p>=100)}</b>cubiertos</div><div><i style="opacity:.6"></i><b>${n(p=>p>=70&&p<100)}</b>cerca</div><div><i style="opacity:.32"></i><b>${n(p=>p<70)}</b>por debajo</div>${over.map(x=>`<div class="o"><i></i><b>1</b>${NU[x[1]][1]}: sobre el máximo</div>`).join('')}</div>`};
function npDay(){const rows=dayRows(),goal=rows.filter((r,i)=>!NU[i][5]),met=goal.filter(r=>r.pct>=100).length;
  return `<div class="np-mod"><div class="np-mh"><span class="ey">Vitaminas y minerales</span><span class="cap">hoy</span></div>
  <div class="np-mrow">${npRad(rows,{day:true,size:156,center:[`${met}`,`de ${goal.length}`]})}${npDayLegend(rows)}</div>
  <div class="np-cov">${ic('info',15)}<span>Vitamina D y yodo: faltan datos de 2 y 6 alimentos</span>${ic('fwd',15)}</div></div>`}
function npNutriPage(){return `<div class="lg np" style="padding-bottom:120px"><div class="cap" style="letter-spacing:1.4px;font-weight:600">MIÉRCOLES 29</div><div class="ptitle" style="margin-top:4px">Nutrición</div>
  <div class="np-mod" style="margin-top:14px"><div class="np-day">${npRing(2240,2300,104)}<div class="np-mrows">${[['Proteína',162,170],['Carbos',228,260],['Grasa',71,75]].map(m=>`<div>${m[0]}<b>${m[1]}<small> / ${m[2]} g</small></b><i style="--w:${Math.min(100,Math.round(m[1]/m[2]*100))}%"></i></div>`).join('')}</div></div></div>
  ${npDay()}</div>${navbar(2)}`}
function npRing(k,target,size=96,{bump=false}={}){const R=size/2-5,C=2*Math.PI*R,fill=Math.min(1,k/target);
  return `<div class="np-ring ${bump?'bump':''}" style="width:${size}px;height:${size}px"><svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" aria-hidden="true"><circle cx="${size/2}" cy="${size/2}" r="${R}" fill="none" stroke="#ffffff14" stroke-width="5"/><circle cx="${size/2}" cy="${size/2}" r="${R}" fill="none" stroke="var(--ink)" stroke-width="5" stroke-linecap="round" stroke-dasharray="${(C*fill).toFixed(1)} ${C.toFixed(1)}" transform="rotate(-90 ${size/2} ${size/2})"/></svg><b><span data-count="${k}">0</span><small>de ${npK(target)}</small></b></div>`}

scene({tab:'vitaminas',id:'v-meal',title:'En el registro',after:ph=>npResultWire(ph),scrollTo:0,html:()=>`<div class="np-host">${npResult()}</div>`,
 notes:{img:'hoy_registro',imgCap:'Hoy: puntos y barras sobre una rejilla punteada, con «100 %» como eje y tres colores.',
  tags:[['fix','Sin rejilla ni ejes'],['fix','Tinta, sin arcoíris']],
  p:['Una huella radial: un radio por nutriente (los 24 de la hoja, en su orden: vitaminas, minerales y otros, con un hueco entre grupos). Cuanto más largo, más cubre de tu día; el radio lleno es el 100 %.',
     'Los tres que más aportan van en tinta plena y con su nombre; el resto, en tinta suave. A la derecha, esos tres con su porcentaje. Sin dato: un punto discontinuo. Topes (sodio, cafeína): radio hueco, y rojo solo si pasan del máximo.',
     'La fila de abajo dice cuántos alimentos tienen ficha completa y abre la lista entera.']}});

scene({tab:'vitaminas',id:'v-day',title:'El día en Nutrición',after:drawAfter,html:()=>npNutriPage(),
 notes:{tags:[['fix','Misma pieza, modo día']],
  p:['En el día, la huella cambia de lectura: <b>tinta plena</b> lo cubierto (100 % o más), media lo que está cerca (70–99 %) y tenue lo que va por debajo. Al centro, cuántos de tus objetivos están cubiertos.',
     'Al lado, la leyenda con los recuentos y, si algo pasa de su máximo, su nombre en rojo. Abajo, los nutrientes con datos incompletos.',
     'El anillo de calorías del día es fino y monocromo, con la cifra a su medida y «de 2.300» debajo.']}});

function npList(){const rows=dayRows();return `<div class="np-host"><div class="np-behind">${npNutriPage()}</div></div><div class="scrim np-fade"></div><div class="sheet np-sheet np-rise"><div class="grab"></div><h3>Vitaminas y minerales</h3><div class="sub">Hoy · % de la referencia de tu perfil</div>
  ${[['Vitaminas',0,12],['Minerales',12,21],['Otros',21,24]].map(g=>`<div class="group-l" style="margin-top:16px">${g[0]}</div>${rows.slice(g[1],g[2]).map((r,j)=>{const i=g[1]+j,lim=NU[i][5],over=lim&&r.pct>100;
    return `<div class="np-nrow"><div class="t"><strong>${NU[i][1]}</strong><span class="v">${fmtN(r.amt)} ${NU[i][2]}${lim?' · máx.':''}</span><span class="p ${over?'over':''}">${Math.round(r.pct)} %</span></div><div class="np-nbar ${lim?'lim':''} ${r.part?'miss':''}"><i style="width:${Math.min(100,r.pct)}%;${over?'background:var(--danger)':''}"></i></div></div>`}).join('')}`).join('')}</div>`}
scene({tab:'vitaminas',id:'v-list',title:'La lista completa',html:()=>npList(),
 notes:{tags:[['fix','Barras finas en tinta']],p:['La lista de los 24 (y del resto hasta 38 en «Ver todos»): nombre, cantidad y porcentaje en columnas fijas, con una barra fina de tinta debajo. Topes con barra hueca; datos incompletos con barra discontinua; solo lo que pasa del máximo va en rojo.']}});

function npCaps(rows){const w=340,h=96,top=rows.map((r,i)=>[r.pct||0,i]).filter(x=>!NU[x[1]][5]).sort((a,b)=>b[0]-a[0]).slice(0,3).map(x=>x[1]);let s='',x=6;
  rows.forEach((r,i)=>{if(i===12||i===21)x+=8;const ht=62,y0=22,f=r.amt==null?0:Math.min(1,(r.pct||0)/100)*ht;
    s+=`<rect x="${x}" y="${y0}" width="9" height="${ht}" rx="4.5" fill="#ffffff10"/>`;
    if(r.amt==null)s+=`<circle cx="${x+4.5}" cy="${y0+ht-5}" r="2.2" fill="none" stroke="#ffffff73" stroke-dasharray="1.3 1.3"/>`;
    else if(f>0)s+=`<rect class="cf" style="animation-delay:${i*22}ms" x="${x}" y="${(y0+ht-Math.max(f,9)).toFixed(1)}" width="9" height="${Math.max(f,9).toFixed(1)}" rx="4.5" fill="var(--ink)" fill-opacity="${top.includes(i)?1:.45}"/>`;
    if(top.includes(i))s+=`<text class="h" x="${x+4.5}" y="14" text-anchor="middle">${NU[i][0]}</text>`;x+=13});
  s+=`<text x="6" y="${h}">Vitaminas</text><text x="${6+12*13+8}" y="${h}">Minerales</text><text x="${6+21*13+16}" y="${h}">Otros</text>`;
  return `<svg class="np-caps" viewBox="0 0 ${w} ${h+4}" role="img" aria-label="Vitaminas y minerales en cápsulas">${s}</svg>`}
scene({tab:'vitaminas',id:'v-alt',title:'Opción B · cápsulas',html:()=>{const rows=npRows(150);return `<div class="lg np">${lgHead()}${mealChips(2)}<div class="np-mod" style="margin-top:4px"><div class="np-mh"><span class="ey">Vitaminas y minerales</span><span class="cap">% de tu día</span></div><div style="margin-top:14px">${npCaps(rows)}</div>
  <div class="np-top" style="margin-top:14px">${npTop(rows).replace('<div class="np-top">','').replace(/<\/div>$/,'')}</div><div class="np-cov">${ic('info',15)}<span>1 de 1 alimento con ficha completa</span>${ic('fwd',15)}</div></div></div>`},
 notes:{tags:[['ask','Alternativa a la huella']],p:['Si prefieres algo más lineal: una cápsula por nutriente, que se llena de abajo arriba hasta el 100 %. Mismas reglas de tinta, topes y datos que faltan. Ocupa todo el ancho y se lee peor de un vistazo; por eso recomiendo la huella.']}});

/* --- Inicio --- */
function npHome({bump=false}={}){const k=bump?1892:1240;return `<div class="home np"><div class="cap" style="letter-spacing:1.4px;font-weight:600">MIÉRCOLES 29</div><div class="ptitle" style="margin-top:4px">Hola, Matías</div>
  <div class="mod" style="margin-top:14px"><div class="mod-h"><span class="ey">Hoy en tu entreno</span><span class="cap">Tirón · 48 min</span></div><button class="pill pri sm" style="width:100%;margin-top:10px">${ic('play',16)}Empezar Tirón</button></div>
  <div class="np-mod"><div class="np-mh"><span class="ey">Nutrición de hoy</span><span class="cap">objetivo 2.300</span></div>
  <div class="np-day" style="margin-top:12px">${npRing(k,2300,96,{bump})}<div class="np-mrows">${[['Proteína',bump?92:67,170],['Carbos',bump?150:104,260],['Grasa',bump?52:38,75]].map(m=>`<div>${m[0]}<b>${m[1]}<small> / ${m[2]} g</small></b><i style="--w:${Math.round(m[1]/m[2]*100)}%"></i></div>`).join('')}</div></div>
  <button class="np-add">${ic('plus',18)}Registrar comida</button></div>
  <div class="mod tight"><div class="rowx"><div class="tx"><strong>Desayuno</strong><span>Avena con plátano · 412 kcal</span></div></div><div class="rowx"><div class="tx"><strong>Almuerzo</strong><span>${bump?'Pollo con arroz y ensalada · 652 kcal':'Sin registrar'}</span></div></div></div></div>${navbar(0)}`}
scene({tab:'inicio',id:'i-home',title:'Nutrición de hoy, con su botón',after:drawAfter,html:()=>npHome(),
 notes:{tags:[['out','Sale la barra «¿Qué comiste?»'],['new','Botón «Registrar comida»']],
  p:['La barra flotante «¿Qué comiste? Escríbelo aquí» sale de Inicio.',
     'La tarjeta «Nutrición de hoy» gana un botón simple, plano y a todo el ancho: <b>«＋ Registrar comida»</b>. Abre el registro completo con la comida que toca por la hora.',
     'La tarjeta pasa a las reglas de Nutrición: anillo fino en tinta con la cifra a su medida, y proteína, carbos y grasa como filas con barra fina.']}});
scene({tab:'inicio',id:'i-bump',title:'Al volver de guardar',after:drawAfter,html:()=>npHome({bump:true}),
 notes:{tags:[['keep','Se queda el rebote']],p:['Al guardar una comida y volver a Inicio, el anillo rebota y las cifras cuentan hasta el total nuevo, como hoy.']}});
