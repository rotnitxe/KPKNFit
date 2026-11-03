/* ================= NUTRICIÓN-CUERPO (prefijo nc): dictado de comida + nueva medición de Cuerpo ================= */
Object.assign(P,{
 ncStop:'<rect x="7.5" y="7.5" width="9" height="9" rx="2" fill="currentColor"/>',
 ncWarn:'<path d="M12 4.5l8.5 14.5h-17z"/><path d="M12 10.5v3.8M12 17v.01"/>',
 ncMinus:'<path d="M6 12h12"/>',
 ncMicOff:'<path d="M4 4l16 16"/><path d="M9.2 5.4A3 3 0 0115 6.5v5M9 9v2.5a3 3 0 004.6 2.5M5.5 11.5a6.5 6.5 0 009.7 5.6M18.5 11.5a6.4 6.4 0 01-.4 2.2M12 18v2.5"/>',
 ncWifiOff:'<path d="M4 4l16 16"/><path d="M2.5 9.5a14 14 0 015-3M8.5 13a8 8 0 014-2M12 18.2v.01M16.5 8.5a14 14 0 015 2.5M15.5 13a8 8 0 012 1.3"/>',
 ncChev:'<path d="M9 6l6 6-6 6"/>',
 ncDown:'<path d="M6 9.5l6 6 6-6"/>',
 ncBody:'<circle cx="12" cy="5.8" r="2.6"/><path d="M5.5 20v-5a3.5 3.5 0 013.5-3.5h6a3.5 3.5 0 013.5 3.5v5"/>',
});
const fm=(n,d=1)=>n.toFixed(d).replace('.',',');
const fs1=n=>(n<0?'−':'+')+fm(Math.abs(n));
const ncBr='style="display:block"';
/* ---------- Registro de comida · dictado ---------- */
const NC_WORDS=['Pollo','a','la','plancha,','un','plato','de','arroz','y','una','ensalada'];
const NC_FOODS=[
  {n:'Pechuga de pollo',q:'180 g · a la plancha',k:297,ic:'pan',tok:'Pollo a la plancha'},
  {n:'Arroz blanco',q:'1 plato · 200 g cocido',k:260,ic:'bowl',tok:'un plato de arroz'},
  {n:'Ensalada mixta',q:'1 bol · 150 g',k:95,ic:'avo',tok:'una ensalada'}];
function ncHead(){return `<div class="lg-head"><button class="ib fill" aria-label="Cerrar">${ic('close')}</button><div class="t"><strong>Registrar comida</strong><span>${ic('cal',13)}Hoy · 18:15</span></div><button class="ib fill" aria-label="Ajustes del registro">${ic('sliders')}</button></div>`}
const ncMeals=(on=3)=>`<div class="meal-chips">${['Desayuno','Almuerzo','Merienda','Cena'].map((m,i)=>`<span class="chip ${i===on?'on':''}" style="height:34px">${m}</span>`).join('')}</div>`;
const ncSeg=()=>`<div class="seg2"><button class="on">${ic('edit',16)}Describir</button><button>${ic('search',16)}Buscar</button></div>`;
const ncRepeat=(dim)=>`<div class="${dim?'nc-dim':''}"><div class="group-l">Repetir</div><div class="hrow"><div class="hcard"><strong>Huevo entero (frito)…</strong><span>Cena del domingo · 8…</span></div><div class="hcard"><strong>Pasta (hidratada/co…</strong><span>Almuerzo del doming…</span></div></div></div>`;
// La onda: barras con altura y retardo repartidos (determinista); `flat` = sin voz.
function ncWave(cls='',n=30){const r=rng(7);let s='';for(let i=0;i<n;i++){const env=Math.sin(i/n*Math.PI)*.55+.45;s+=`<i style="--h:${Math.round(18+r()*68*env)};--d:${-Math.round(r()*780)}ms"></i>`}return `<div class="nc-wave ${cls}" aria-hidden="true">${s}</div>`}
const ncTime=s=>`${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`;
const ncStopBtn=(t,run)=>`<button class="nc-stop" data-nc="stop" aria-label="Dejar de dictar"><svg class="rg" width="48" height="48" viewBox="0 0 48 48"><circle class="t" cx="24" cy="24" r="22"/><circle class="p ${run?'run':''}" cx="24" cy="24" r="22" style="--p:${Math.min(1,t/15)}"/></svg><i>${ic('ncStop',22,1.75)}</i></button>`;
const ncDictBtn=(off)=>`<button class="nc-dict ${off?'off':''}" data-nc="dict" aria-label="Dictar">${ic('mic',20)}Dictar</button>`;
const ncAnalyze=(l='Analizar')=>`<button class="nc-an" data-nc="analyze">${ic('spark',16)}${l}</button>`;
function ncTokens(text,mode){ // mode 'rd' = leyendo (subrayado fuerte) / 'done'
  let t=text;NC_FOODS.forEach(f=>{t=t.replace(f.tok,`<span class="u ${mode==='rd'?'rd':''}">${f.tok}</span>`)});return t}
function ncNote(kind){
  const N={
   nohear:{i:'ncMicOff',c:'',t:'No te oí',p:'No me llegó voz. Acércate un poco al teléfono y vuelve a probar.',a:[['pri','mic','Probar otra vez'],['sec','edit','Escribirlo']]},
   unclear:{i:'ncWarn',c:'',t:'No te entendí del todo',p:'Me quedé con lo que está arriba. Sigue dictando desde ahí o termínalo escribiendo.',a:[['pri','mic','Seguir dictando'],['sec','edit','Escribir el resto']]},
   denied:{i:'ncMicOff',c:'mut',t:'Sin permiso del micrófono',p:'Para dictar, activa el micrófono para KPKN en los ajustes de Android. Mientras tanto puedes escribir.',a:[['pri','sliders','Abrir ajustes'],['sec','edit','Escribirlo']]},
   nosvc:{i:'ncMicOff',c:'mut',t:'Este teléfono no tiene dictado por voz',p:'Activa un servicio de reconocimiento de voz en los ajustes de Android o escribe lo que comiste.',a:[['pri','sliders','Abrir ajustes de voz'],['sec','edit','Escribirlo']]},
   offline:{i:'ncWifiOff',c:'mut',t:'Sin conexión',p:'El dictado necesita internet o el español descargado para usarlo sin conexión.',a:[['pri','upload','Descargar voz en español'],['sec','edit','Escribirlo']]}}[kind];
  return `<div class="nc-note" role="alert"><div class="h"><span class="ico ${N.c}">${ic(N.i,20)}</span><div><strong>${N.t}</strong><p>${N.p}</p></div></div><div class="acts">${N.a.map(a=>`<button class="nc-b ${a[0]}" data-nc="${a[2]==='Escribirlo'||a[2]==='Escribir el resto'?'type':'retry'}">${ic(a[1],16)}${a[2]}</button>`).join('')}</div></div>`}
function ncResult(){const tot=NC_FOODS.reduce((a,f)=>a+f.k,0);
  return `${ncSumCard(tot)}${NC_FOODS.map(f=>`<div class="nc-fr"><div class="h"><span class="fi">${ic(f.ic,22)}</span><div class="tx"><strong>${f.n}</strong><span>${f.q}</span></div><div class="k"><b>${f.k}</b><small>kcal</small></div></div></div>`).join('')}
  <button class="act" style="margin-top:8px">${ic('plus',16)}Añadir otro alimento</button>`}
function ncSumCard(tot){const P_=63,C_=64,G_=15;return `<div class="nc-ks"><div class="k"><b>${tot}</b><span>kcal</span></div><div class="bar"><i style="flex:${P_*4};background:var(--mus)"></i><i style="flex:${C_*4};background:var(--ene)"></i><i style="flex:${G_*9};background:var(--col)"></i></div><div class="nm">${[["Proteína",P_,'var(--mus)'],['Carbos',C_,'var(--ene)'],['Grasa',G_,'var(--col)']].map(m=>`<div><b>${m[1]} g</b><span style="--c:${m[2]}">${m[0]}</span></div>`).join('')}</div></div>`}
/* Una vista de todo el estado del dictado. o.st: rest | listen | noise | proc | result | nohear | unclear | denied | nosvc | offline | ask */
function ncDictView(o){
  const st=o.st,t=o.t||0;
  const text=o.text||'';
  const listening=st==='listen'||st==='noise';
  const words=text?text.split(' '):[];
  const body=words.slice(0,Math.max(0,words.length-(listening&&o.partial?o.partial:0))).join(' ');
  const tail=listening&&o.partial?words.slice(-o.partial).join(' '):'';
  let field,below='';
  if(st==='proc'){
    field=`<div class="nc-write done"><div class="nc-txt sm" style="color:var(--ink);font-size:calc(17px*var(--fs))">${ncTokens(text,'rd')}</div></div>`;
    below=`<div class="nc-ana"><div class="msg"><span class="nc-spin"></span><div>Encontré pollo, arroz y ensalada<small>Estimando cantidades…</small></div></div><div class="nc-sk"></div><div class="nc-sk" style="opacity:.7"></div><div class="nc-sk" style="opacity:.4"></div></div>`;
  }else if(st==='result'){
    field=`<div class="nc-write done" data-nc="edit"><div class="nc-txt sm">${ncTokens(text,'done')}</div></div>`;
    below=ncResult();
  }else{
    const ph=`<span class="ph">¿Qué comiste? Escríbelo como lo contarías</span>`;
    const txt=listening||text?`${body}${tail?(body?' ':'')+`<span class="part">${tail}</span>`:''}${listening?'<span class="caret"></span>':''}`:ph;
    let foot,extra='';
    if(listening){
      foot=`<div class="nc-foot">${ncStopBtn(t,o.run)}${ncWave(st==='noise'?'noise':'')}<span class="nc-time" data-nc="time">${ncTime(t)}</span></div>`;
      extra=st==='noise'?`<div class="nc-status noise">${ic('ncWarn',16)}<span><b>Hay mucho ruido.</b> Acércate al teléfono o ve a un sitio más tranquilo.</span></div>`
        :`<div class="nc-status"><span class="dot"></span><span><b>Escuchando</b>${text?'':' · di lo que comiste, con cantidades'}</span></div>`;
    }else if(['nohear','unclear','denied','nosvc','offline'].includes(st)){
      foot='';extra=ncNote(st);
    }else{
      foot=`<div class="nc-foot">${ncDictBtn(false)}<span class="grow"></span>${text?ncAnalyze():''}</div>`;
    }
    field=`<div class="nc-write ${listening?'live':''} ${['nohear','unclear','nosvc','offline','denied'].includes(st)?'warn':''}"><div class="nc-txt" data-nc="txt">${txt}</div>${foot}${extra}</div>`;
    if(st==='rest'||listening||['nohear','unclear','denied','nosvc','offline'].includes(st))below=ncRepeat(st!=='rest');
  }
  const save=st==='result'?`<div class="savebar glass"><div class="k"><b>${NC_FOODS.reduce((a,f)=>a+f.k,0)}<small>kcal</small></b><span>3 alimentos</span></div><button class="go">Guardar en cena</button></div>`:'';
  return `<div class="lg" data-nc="lg">${ncHead()}${ncMeals(3)}${ncSeg()}${field}${below}</div>${save}`}
const NC_FULL=NC_WORDS.join(' ');
// Escena estática de un estado.
const ncD=(st,o={})=>ncDictView(Object.assign({st},o));
function ncOs(){return `<div class="pl-scrim"></div><div class="nc-os" role="dialog"><span class="m">${ic('mic',30)}</span><h4>¿Permitir que KPKN grabe audio?</h4><button>Solo mientras la app está en uso</button><button>Solo esta vez</button><button>No permitir</button></div>`}
function ncAskSheet(){return `<div class="pl-scrim"></div><div class="pl-sheet nc-psh"><span class="bigmic">${ic('mic',30)}</span><h3>Dictar lo que comiste</h3><p>KPKN usa el micrófono solo mientras dictas y no guarda el audio: el texto va al campo y tú lo revisas antes de guardar.</p><button class="pl-btn">Permitir el micrófono</button><button class="nc-sec">Prefiero escribirlo</button></div>`}

scene({id:'nc-d-vivo',tab:'nutricion',title:'Dictado · pruébalo (interactivo)',
 html:()=>`<div data-nc="host" style="position:absolute;inset:0">${ncD('rest')}</div>`,
 after:(ph)=>{ncDictWire(ph)},
 notes:{img:'h19-voz-comida',imgCap:'Hoy: el círculo del micrófono flota solo y, al pulsarlo, solo sale «No te entendí».',
  tags:[['new','Dictado de verdad'],['out','Sale el círculo suelto'],['keep','Reconocedor del sistema']],
  p:['Toca <b>Dictar</b>: simula escuchar y escribe una frase de ejemplo palabra a palabra. Toca el botón de parar (o espera) para que la lea y la convierta en alimentos. Si lo paras sin haber dicho nada, sale «No te oí».',
     'El micrófono ya no es un círculo suelto: es la acción <b>«Dictar»</b> dentro del campo, junto a «Analizar». Al escuchar, se convierte en el botón de parar en el mismo sitio, con un anillo que se llena en los 15 s del límite (el código corta a 15 s), la onda en vivo y el tiempo.'],
  list:['Escuchando: lo parcial se escribe en el campo; las últimas palabras (aún provisionales) van en gris.','Al terminar entra solo en «Leyendo» y luego en los alimentos, sin un paso intermedio.','Toca el texto resultante para volver a editarlo, o «Dictar» para añadir más.'],listTitle:'Cómo se comporta'}});

scene({id:'nc-d-reposo',tab:'nutricion',title:'Dictado · en reposo',html:()=>ncD('rest'),
 notes:{tags:[['new','Micrófono dentro del campo'],['keep','Repetir, desayuno/cena, Describir/Buscar']],
  p:['El campo es el vigente (superficie plana, borde fino). La fila de acciones lleva <b>«Dictar»</b> (icono + palabra, 44 dp, zona táctil de 48) a la izquierda y «Analizar» a la derecha cuando hay texto. Tener la palabra evita que parezca de relleno: se entiende qué hace antes de pulsarlo.','Debajo, «Repetir» como hoy, sin cambios.']}});

scene({id:'nc-d-permiso',tab:'nutricion',title:'Permiso · explicación previa',html:()=>ncD('rest')+ncAskSheet(),
 notes:{tags:[['new','Hoja previa al permiso'],['ask','Texto a validar']],
  p:['La primera vez, antes del diálogo de Android, una hoja corta dice <b>para qué</b> y qué no se hace con la voz. «Permitir el micrófono» lanza el diálogo del sistema; «Prefiero escribirlo» cierra la hoja y deja el campo listo.','Hoy el código pide el permiso directamente (<code>RECORD_AUDIO</code>) y, si se rechaza, solo muestra un aviso breve.']}});

scene({id:'nc-d-sistema',tab:'nutricion',title:'Permiso · diálogo de Android',html:()=>ncD('rest')+ncOs(),
 notes:{tags:[['keep','Lo dibuja Android']],p:['Referencia: es el diálogo del sistema, no se diseña. Si el usuario elige «Solo mientras la app está en uso» o «Solo esta vez», la escucha empieza sola (como ya hace el código al conceder). «No permitir» lleva a la escena siguiente.']}});

scene({id:'nc-d-denegado',tab:'nutricion',title:'Permiso denegado',html:()=>ncD('denied'),
 notes:{tags:[['new','Aviso con salida'],['out','Sale el aviso suelto de abajo']],
  p:['Hoy: «Sin permiso del micrófono no puedo dictar.» como aviso que desaparece. Ahora el aviso vive <b>dentro del campo</b> hasta que se resuelve, con dos salidas: «Abrir ajustes» (pantalla de permisos de KPKN en Android) y «Escribirlo» (enfoca el campo).','El tono es neutro, no de error: no es culpa de quien lo usa.']}});

scene({id:'nc-d-escuchando-0',tab:'nutricion',title:'Escuchando · esperando voz',html:()=>ncD('listen',{t:2,run:true}),
 notes:{tags:[['new','Onda y parar'],['new','Anillo de 15 s']],
  p:['Primer instante tras pulsar «Dictar»: el campo muestra el marcador vacío con el cursor, el botón de parar con su anillo (el límite de 15 s que ya impone el código) y la onda con la intensidad del micrófono.','La onda sale de <code>onRmsChanged</code> del reconocedor; hoy no se usa y habría que exponerlo (ver motor).']}});

scene({id:'nc-d-escuchando',tab:'nutricion',title:'Escuchando · se escribe lo que dices',html:()=>ncD('listen',{t:6,text:NC_WORDS.slice(0,8).join(' '),partial:2}),
 notes:{tags:[['new','Transcripción en vivo'],['keep','Resultados parciales']],
  p:['Las palabras ya confirmadas están en tinta y las <b>dos últimas (provisionales)</b> en gris: el reconocedor puede corregirlas hasta cerrar la frase. La coma de «plancha,» sale sola de la unión que ya hace <code>loggerAppendDictation</code>.','Si el campo tenía texto antes, lo nuevo se añade tras una coma.']}});

scene({id:'nc-d-ruido',tab:'nutricion',title:'Escuchando · mucho ruido',html:()=>ncD('noise',{t:5,text:NC_WORDS.slice(0,4).join(' '),partial:1}),
 notes:{tags:[['new','Aviso de ruido'],['ask','Necesita medir el nivel de ruido']],
  p:['Si el nivel de entrada es alto y las palabras llegan con baja confianza, la onda y el aviso pasan a ámbar (color de «atención», no de error) y dicen qué hacer. No corta la escucha.','Depende de leer el nivel del micrófono y la confianza del reconocedor; se puede dejar fuera si no se logra fiable.']}});

scene({id:'nc-d-leyendo',tab:'nutricion',title:'Procesando · leyendo la frase',html:()=>ncD('proc',{text:NC_FULL}),
 notes:{tags:[['keep','«Leyendo» vigente']],
  p:['Al soltar, el texto pasa al modo «Leyendo» que ya existe (subrayado por trozos) y debajo la lectura de lo que se reconoce, con huecos de carga donde irán los alimentos. Sin pantalla intermedia.']}});

scene({id:'nc-d-resultado',tab:'nutricion',title:'Alimentos reconocidos',html:()=>ncD('result',{text:NC_FULL}),after:(ph)=>animateIn(ph),
 notes:{tags:[['keep','Diseño de resultado vigente']],
  p:['Lo dictado se comporta <b>exactamente como lo escrito</b>: el texto se compacta con los trozos reconocidos subrayados, el anillo con macros, una fila por alimento con cantidad y calorías, y «Guardar en cena». Tocar el texto lo reabre; las dudas (aceite, crudo/cocido…) salen igual que hoy en cada alimento.','Los alimentos y valores son un ejemplo.']}});

scene({id:'nc-d-no-oi',tab:'nutricion',title:'No te oí (silencio)',html:()=>ncD('nohear'),
 notes:{tags:[['new','Salida útil'],['out','Sale «No te entendí» sordo']],
  p:['Si pasan unos segundos sin voz (<code>ERROR_NO_MATCH</code> / <code>SPEECH_TIMEOUT</code>) no se dice «No te entendí»: se dice que <b>no llegó voz</b> y se ofrece reintentar con un toque o escribir. El aviso se queda hasta que se elige.']}});

scene({id:'nc-d-no-entendi',tab:'nutricion',title:'No te entendí del todo',html:()=>ncD('unclear',{text:'Pollo a la plancha, un plato de'}),
 notes:{tags:[['new','Conserva lo dictado']],
  p:['Si hubo palabras pero el reconocedor falló a medias, <b>no se pierde lo dicho</b>: queda en el campo y se puede seguir dictando (añade a continuación) o terminarlo escribiendo.','Hoy un error borra la sensación de progreso y el texto parcial no se usa.']}});

scene({id:'nc-d-sin-voz',tab:'nutricion',title:'Sin servicio de voz',html:()=>ncD('nosvc'),
 notes:{tags:[['keep','Mensaje del código'],['new','Con salida']],
  p:['Hoy: «Este teléfono no tiene dictado por voz.» El texto se mantiene y se le añade qué hacer. «Dictar» no se esconde (sería desconcertante): queda en el campo pero atenuado hasta que haya servicio.']}});

scene({id:'nc-d-sin-conexion',tab:'nutricion',title:'Sin conexión',html:()=>ncD('offline'),
 notes:{tags:[['new','Aviso de conexión'],['ask','Depende del paquete sin conexión']],
  p:['Se pide el modo sin conexión cuando existe (<code>EXTRA_PREFER_OFFLINE</code>). Si no está descargado el español y no hay internet, se dice claro y se ofrece abrir la descarga del paquete de voz del sistema. «Escribirlo» sigue funcionando: el motor de alimentos es local.']}});

/* ---------- Cuerpo ---------- */
const NC_LAST={kg:64.0,ago:'hace 3 días',days:3};
function ncChart(){const v=[65.1,65.0,64.8,64.9,64.6,64.5,64.6,64.3,64.2,64.3,64.0,64.2],w=300,h=92,mn=61.5,mx=65.5;
  const pt=v.map((x,i)=>[8+i*(w-16)/(v.length-1),h-10-(x-mn)/(mx-mn)*(h-26)]);
  const d=pt.map((p,i)=>(i?'L':'M')+p[0].toFixed(1)+' '+p[1].toFixed(1)).join('');
  const gy=h-10-(62-mn)/(mx-mn)*(h-26);
  return `<svg class="nc-chart" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" aria-label="Tendencia del peso"><defs><linearGradient id="ncg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f2eee6" stop-opacity=".16"/><stop offset="1" stop-color="#f2eee6" stop-opacity="0"/></linearGradient></defs><path d="${d}L${pt[pt.length-1][0]} ${h}L${pt[0][0]} ${h}Z" fill="url(#ncg)"/><path d="M8 ${gy.toFixed(1)}H${w-8}" stroke="#43d18c" stroke-opacity=".5" stroke-dasharray="3 5" stroke-width="1.2"/><path d="${d}" fill="none" stroke="#f2eee6" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke"/>${pt.slice(-1).map(p=>`<circle cx="${p[0]}" cy="${p[1]}" r="4" fill="#f2eee6"/>`).join('')}</svg>`}
function ncHistRows(newRow){const R=[['Lun 26 oct · 07:40','Grasa 18,4 % · Músculo 41,2 %',64.0,0],['Vie 23 oct · 07:35','',64.3,0],['Mié 21 oct · 07:50','Health Connect',64.2,1]];
  const row=(r,i)=>`<div class="nc-hr"><div class="tx"><strong>${r[0]}${r[3]?`<span class="nc-hc">Health Connect</span>`:''}</strong>${r[1]&&!r[3]?`<span>${r[1]}</span>`:''}</div><div class="w">${fm(r[2])}<small>kg</small></div></div>`;
  const nw=newRow?`<div class="nc-hr new"><div class="tx"><strong>Hoy · 18:15</strong></div><div class="w">${fm(newRow)}<small>kg</small></div></div>`:'';
  return `<div class="nc-ml pad" style="padding-left:20px">Octubre 2026</div><div class="pad"><div class="mod tight" style="padding:6px 8px">${nw}${R.map(row).join('')}</div></div>
  <div class="nc-ml" style="padding-left:20px">Septiembre 2026</div><div class="pad"><div class="mod tight" style="padding:6px 8px"><div class="nc-hr"><div class="tx"><strong>Lun 28 sep · 07:42</strong><span>Grasa 19,1 % · Músculo 40,8 %</span></div><div class="w">64,8<small>kg</small></div></div></div></div>`}
function ncScreen(o={}){
  const {empty=false,island=false,fab=true,newRow=0,snack=false,scrollTo=0}=o;
  const wm=empty?`<div class="mod"><div class="nc-empty"><span class="ci">${ic('ncBody',26)}</span><h3>Registra tu primer pesaje</h3><p>Con unos pocos pesajes ves tu tendencia y tu ritmo.</p></div></div>`
  :`<div class="mod"><div class="mod-h"><span class="ey">Peso</span><div class="nc-seg">${['1M','3M','6M','1A','Todo'].map((r,i)=>`<span class="${i===0?'on':''}">${r}</span>`).join('')}</div></div>
   <div class="cap" style="margin-top:12px">Tendencia</div><div class="nc-big">${newRow?fm(newRow-.1):'64,2'}<small>kg</small></div>
   <div class="cap" style="margin-top:4px">${newRow?'Último pesaje: '+fm(newRow)+' kg · hoy':'Último pesaje: 64,0 kg · hace 3 días'}</div>
   <div style="margin-top:8px"><span class="nc-chg">${ic('trendDown',15)}−0,6 kg <span style="font-weight:500;color:var(--faint)">desde el 29 sep</span></span></div>
   <div style="font:600 calc(13.5px*var(--fs)) Inter;margin-top:10px">Ritmo: −0,1 kg por semana</div>
   ${ncChart()}<div class="cap" style="margin-top:6px">Objetivo 62,0 kg</div></div>
   <div class="mod"><div class="mod-h"><span class="ey">Composición</span></div><div style="display:flex;gap:10px;margin-top:10px">${[['Grasa','18,4 %'],['Músculo','41,2 %'],['IMC','21,9']].map(x=>`<div style="flex:1;border-radius:16px;background:#ffffff08;padding:10px 12px"><div class="cap">${x[0]}</div><div style="font:700 calc(17px*var(--fs)) Syne;margin-top:2px;font-variant-numeric:tabular-nums;white-space:nowrap">${x[1]}</div></div>`).join('')}</div></div>`;
  const hist=empty?`<div class="mod"><div class="nc-empty" style="padding:22px 24px"><h3 style="margin-top:0">Aún no hay medidas</h3><p>Cada pesaje y cada medida quedan aquí, ordenados por mes.</p></div></div>`:ncHistRows(newRow);
  return `<div class="nc-bd" data-nc="bd"><div class="nc-disp">Cuerpo</div><div class="pad">${wm}</div>${hist}</div>
  <div class="nc-veil"></div><div class="nc-isl pl-glass ${island?'':'gone'}" data-nc="isl"><span class="ci">${ic('ncBody',20)}</span><div><strong>${newRow?fm(newRow-.1):'64,2'} kg</strong><span>−0,6 kg en 30 días</span></div></div>
  ${navbar(3)}${fab?`<button class="nc-fab pl-glass" data-nc="fab" aria-label="Abrir el registro de medidas">${ic('plus',20,1.75)}Nueva medición</button>`:''}
  ${snack?`<div class="nc-snack glass"><span class="ok">${ic('check',14)}</span>Medición guardada<span class="u">Deshacer</span></div>`:''}`}

/* ----- Regla de peso ----- */
const NC_PER=9;
function ncTicks(v,mini){let s='';const c=260,t=Math.round(v*10),rg=30;
  for(let i=t-rg;i<=t+rg;i++){const x=c+(i-v*10)*NC_PER,maj=i%10===0,mid=i%5===0;
    const y2=maj?42:mid?33:25;
    s+=`<path d="M${x.toFixed(1)} 10V${y2}" stroke="#fff" stroke-opacity="${maj?.62:mid?.38:.2}" stroke-width="${maj?2:1.75}" stroke-linecap="round"/>`;
    if(maj)s+=`<text x="${x.toFixed(1)}" y="62" text-anchor="middle" fill="#8e8980" font-size="11.5" font-family="Inter" font-weight="500" style="font-variant-numeric:tabular-nums lining-nums">${Math.round(i/10)}</text>`}
  return s}
const ncRuleHtml=(v,idle,mini)=>`<div class="nc-rule ${idle?'idle':''}" data-nc="rule" role="slider" aria-label="${mini?'Porcentaje':'Peso'}" tabindex="0"><svg width="520" height="74" viewBox="0 0 520 74" style="position:absolute;left:50%;margin-left:-260px" aria-hidden="true">${ncTicks(v,mini)}</svg><i class="nd"></i></div>`;
// Cablea una regla: pointer + inercia + ajuste a la décima. get/set en décimas continuas.
function ncRuleWire(rule,{v,min,max,onMove,onSettle}){
  const svg=rule.querySelector('svg');let cur=v*10,vel=0,drag=false,lx=0,lt=0,raf=0;
  const lo=min*10,hi=max*10;
  const draw=()=>{svg.innerHTML=ncTicks(cur/10)};
  const emit=fin=>{const q=Math.round(cur)/10;onMove&&onMove(q);if(fin)onSettle&&onSettle(q)};
  const snap=()=>{const tgt=Math.max(lo,Math.min(hi,Math.round(cur))),a=cur,t0=performance.now();cancelAnimationFrame(raf);
    const step=n=>{const k=Math.min(1,(n-t0)/140),e=1-Math.pow(1-k,3);cur=a+(tgt-a)*e;draw();emit(false);if(k<1)raf=requestAnimationFrame(step);else emit(true)};raf=requestAnimationFrame(step)};
  const glide=()=>{let prev=performance.now();cancelAnimationFrame(raf);
    const step=n=>{const dt=Math.min(32,n-prev);prev=n;cur+=vel*dt;vel*=Math.pow(.94,dt/16);
      if(cur<lo||cur>hi){cur=Math.max(lo,Math.min(hi,cur));vel=0}
      draw();emit(false);if(Math.abs(vel)>.0045)raf=requestAnimationFrame(step);else snap()};raf=requestAnimationFrame(step)};
  rule.addEventListener('pointerdown',e=>{drag=true;vel=0;lx=e.clientX;lt=performance.now();cancelAnimationFrame(raf);try{rule.setPointerCapture(e.pointerId)}catch(_){}});
  rule.addEventListener('pointermove',e=>{if(!drag)return;const sc=rule.getBoundingClientRect().width/rule.offsetWidth||1;const now=performance.now(),dx=(e.clientX-lx)/sc;
    cur=Math.max(lo,Math.min(hi,cur-dx/NC_PER));const dt=Math.max(1,now-lt);vel=vel*.6+(-dx/NC_PER/dt)*.4;lx=e.clientX;lt=now;draw();emit(false)});
  const up=()=>{if(!drag)return;drag=false;if(performance.now()-lt>90)vel=0;glide()};
  rule.addEventListener('pointerup',up);rule.addEventListener('pointercancel',up);
  rule.addEventListener('keydown',e=>{if(e.key==='ArrowLeft'||e.key==='ArrowRight'){e.preventDefault();cur+=e.key==='ArrowRight'?1:-1;cur=Math.max(lo,Math.min(hi,cur));draw();emit(true)}});
  return {nudge(d){cancelAnimationFrame(raf);cur=Math.max(lo,Math.min(hi,Math.round(cur)+d));draw();emit(true)},get:()=>Math.round(cur)/10};
}
/* ----- La hoja ----- */
// o: {mode:'new'|'edit'|'ro', first, v, touched, fat, muscle, meas, calendar, future, bad, weird}
function ncRefHtml(o,v){
  if(o.first)return `<span>Primera medición · tu punto de partida</span>`;
  const d=Math.round((v-NC_LAST.kg)*10)/10,weird=Math.abs(d)>2.5;
  return `<span>Último: ${fm(NC_LAST.kg)} kg · ${NC_LAST.ago}</span><span class="nc-dl ${weird?'warn':''}">${d===0?'Igual':fs1(d)+' kg'}</span>`}
function ncWarnHtml(o,v){
  if(o.first)return '';const d=Math.round((v-NC_LAST.kg)*10)/10;if(Math.abs(d)<=2.5)return '';
  return `<div class="nc-warnbox">${ic('ncWarn',18)}<span><b>${d>0?'Sube':'Baja'} ${fm(Math.abs(d))} kg en ${NC_LAST.days} días.</b> Es bastante más de lo habitual. Si es correcto, guárdalo igualmente.</span></div>`}
function ncPct(name,v,w){return `<div class="nc-pcc nc-mini"><span class="nm">${name}</span><div class="v">${fm(v)}<small>%</small></div>${ncRuleHtml(v,false,true)}<span class="x" aria-label="Quitar ${name}">${ic('close',16)}</span></div>`}
function ncCal(){const days=['L','M','X','J','V','S','D'];let c=`<span></span>`.repeat(3);for(let d=1;d<=31;d++)c+=`<span class="${d===29?'on':d>29?'no':''}">${d}</span>`;
  return `<div class="nc-cal"><div class="mh"><span>Octubre 2026</span><span style="display:flex;gap:6px;color:var(--faint)">${ic('back',18)}${ic('fwd',18)}</span></div><div class="g">${days.map(d=>`<span class="d">${d}</span>`).join('')}${c}</div></div>`}
function ncSheet(o){
  const mode=o.mode||'new',ro=mode==='ro',v=o.v!=null?o.v:(o.first?70:NC_LAST.kg);
  const touched=o.touched!=null?o.touched:!!(o.weird||mode==='edit'||ro);
  const title=ro?'Medición':mode==='edit'?'Editar medición':'Nueva medición';
  const idle=o.first&&!touched;
  const canSave=!ro&&touched&&!o.future&&!o.bad;
  const meas=[['Cintura','82,5'],['Cadera','96,0'],['Pecho','101,0'],['Brazo','35,5'],['Muslo','58,0'],['Cuello','37,0']];
  const body=`<div class="nc-sb" data-nc="sb">
   <div><h3 class="nc-st">${title}${ro?'<small>Sincronizada con Health Connect. Solo lectura.</small>':''}</h3></div>
   ${ro?`<div style="font:600 calc(15px*var(--fs)) Inter">Mié 21 oct · 07:50</div>`:`<div style="display:flex;gap:8px"><span class="nc-ch" data-nc="cal">${ic('cal',16)}${mode==='edit'?'Lunes 26':'Hoy · jueves 29'}</span><span class="nc-ch ${o.future?'err':''}">${ic('clock',16)}${o.future?'19:45':mode==='edit'?'07:40':'18:15'}</span></div>`}
   ${o.future?`<div class="nc-errbox">La hora no puede ser futura.</div>`:''}
   ${o.calendar?ncCal():''}
   <div><div class="nc-lab">Peso</div>
    ${ro?`<div style="margin-top:6px;font:800 calc(52px*var(--fs))/1 Syne;letter-spacing:-2px">64,2<small style="font:600 16px Inter;letter-spacing:0;color:var(--muted);margin-left:5px">kg</small></div>`
    :`<div class="nc-val"><button class="nc-fine" data-nc="m" aria-label="Menos 0,1">${ic('ncMinus',16)}<b>0,1</b></button><div class="num ${idle?'idle':''}" data-nc="num">${fm(v)}<small>kg</small></div><button class="nc-fine" data-nc="p" aria-label="Más 0,1">${ic('plus',16)}<b>0,1</b></button></div>
     <div class="nc-ref" data-nc="ref">${ncRefHtml(o,v)}</div>
     ${ncRuleHtml(v,idle)}
     <div class="cap" data-nc="hint" style="text-align:center;margin-top:2px;${touched?'display:none':''}">${o.first?'Desliza la regla para elegir el peso.':'Desliza la regla o toca el valor para confirmar el mismo peso.'}</div>`}
   </div>
   <div data-nc="warn">${o.weird?ncWarnHtml(o,v):''}</div>
   ${ro?`${o.fat!=null?`<div class="nc-hcl">% de grasa · ${fm(o.fat)} %</div>`:''}`:
   `${o.fat!=null||o.muscle!=null?`<div class="nc-pc">${o.fat!=null?ncPct('Grasa',o.fat):''}${o.muscle!=null?ncPct('Músculo',o.muscle):''}</div>`:''}
    ${o.fat==null||o.muscle==null?`<div style="display:flex;gap:8px;flex-wrap:wrap"><span class="nc-lab" style="width:100%">Añade más datos</span>${o.fat==null?`<span class="nc-ch add">${ic('plus',16)}Grasa</span>`:''}${o.muscle==null?`<span class="nc-ch add">${ic('plus',16)}Músculo</span>`:''}</div>`:''}
    <div class="nc-disc"><div class="l">Medidas<span>Opcional</span></div><span style="color:var(--faint);${o.meas?'transform:rotate(180deg)':''}">${ic('ncDown',20)}</span></div>
    ${o.meas?`<div class="nc-mg">${meas.map((m,i)=>`<div class="nc-in ${o.bad&&i===3?'err':''}"><small>${m[0]} (cm)</small><b class="${i>3&&!o.full?'e':''}">${i>3&&!o.full?'—':o.bad&&i===3?'355':m[1]}</b></div>`).join('')}</div>${o.bad?`<div class="nc-errbox">Revisa las medidas: deben ser números dentro de rango.</div>`:''}`:''}`}
  </div>
  <div class="nc-sa">${ro?`<button class="pl-btn ghost" style="width:100%">Cerrar</button>`:`<div class="nc-sv ${canSave?'':'off'}" data-nc="sv" role="button">Guardar</div>${mode==='edit'?`<div class="nc-del">Eliminar medición</div>`:''}`}</div>`;
  return `<div class="pl-scrim" data-nc="scrim"></div><div class="pl-sheet nc-sh" data-nc="sheet" role="dialog" aria-label="${title}">${body}</div>`}
// Interacción de la hoja: regla, ±0,1, confirmar tocando el valor, guardar.
function ncSheetWire(ph,o,cb={}){
  const q=s=>ph.querySelector(`[data-nc="${s}"]`);const rule=q('rule');if(!rule)return;
  const v0=o.v!=null?o.v:(o.first?70:NC_LAST.kg);
  let touched=o.touched!=null?o.touched:!!(o.weird||o.mode==='edit');
  const upd=(v,t)=>{if(t)touched=true;const num=q('num'),sv=q('sv'),h=q('hint');
    num.firstChild.nodeValue=fm(v);num.classList.toggle('idle',!!(o.first&&!touched));rule.classList.toggle('idle',!!(o.first&&!touched));
    q('ref').innerHTML=ncRefHtml(o,v);q('warn').innerHTML=ncWarnHtml(o,v);
    if(h)h.style.display=touched?'none':'';
    if(sv)sv.classList.toggle('off',!(touched&&!o.future&&!o.bad))};
  const w=ncRuleWire(rule,{v:v0,min:30,max:250,onMove:v=>upd(v,rule.dataset.t==='1'),onSettle:()=>{}});
  rule.addEventListener('pointerdown',()=>{rule.dataset.t='1';touched=true});
  rule.addEventListener('keydown',()=>{rule.dataset.t='1';touched=true});
  q('m').onclick=()=>{rule.dataset.t='1';w.nudge(-1)};q('p').onclick=()=>{rule.dataset.t='1';w.nudge(1)};
  q('num').onclick=()=>{rule.dataset.t='1';upd(w.get(),true)};
  const sv=q('sv');if(sv)sv.onclick=()=>{if(!sv.classList.contains('off')&&cb.save)cb.save(w.get())};
  const sc=q('scrim');if(sc)sc.onclick=()=>cb.close&&cb.close();
}
const ncSheetScene=(o,bg)=>()=>ncScreen(Object.assign({fab:false,island:false},bg||{}))+ncSheet(o);
const ncAfter=(o)=>(ph)=>ncSheetWire(ph,o);

scene({id:'nc-c-pantalla',tab:'nutricion',title:'Cuerpo · el botón flotante (interactivo)',
 html:()=>`<div data-nc="host" style="position:absolute;inset:0">${ncScreen()}</div>`,
 after:(ph)=>ncBodyWire(ph),
 notes:{tags:[['new','Botón flotante «Nueva medición»'],['move','El «+» de la cabecera y de la isla sale'],['keep','Módulos de Cuerpo sin cambios']],
  p:['Desliza hacia arriba: la cabecera «Cuerpo» sale y aparece la <b>isla superior</b> (peso y variación), como hoy. Toca <b>«Nueva medición»</b>: abre la hoja y el botón se esconde mientras la hoja está abierta (igual que el de Nutrición, que sigue <code>KpknModalTracker</code>). Guarda y verás la fila nueva resaltada, el aviso «Medición guardada» con «Deshacer» y el botón de vuelta.',
     'Es el <b>par exacto</b> de «Registrar comida»: píldora de vidrio de 56 dp, «+» de 20, 8 dp entre el icono y el texto, 22 dp a los lados, centrada, 12 dp sobre la barra inferior (<code>padding(bottom = 84.dp)</code> sobre la barra de gestos, igual en ambas). Hoy el «+» está dos veces: suelto en la cabecera y en un círculo en la isla; ambos pasan al botón flotante.'],
  list:['Nutrición no se compacta al hacer scroll: se queda fija y solo se esconde bajo hojas y diálogos. El de Cuerpo hace lo mismo para ser su par exacto.','«Registrar medidas» (nombre de accesibilidad hoy) pasa a «Nueva medición», el texto visible.'],listTitle:'Comportamiento'}});
scene({id:'nc-c-isla',tab:'nutricion',title:'Cuerpo · con scroll (isla arriba)',html:()=>ncScreen({island:true}),
 after:(ph)=>{const b=ph.querySelector('[data-nc=bd]');if(b)b.scrollTop=300},
 notes:{tags:[['move','La isla ya no lleva «+»']],p:['Cabecera fuera de la vista: aparece la isla con «64,2 kg» y la variación. Pierde su «+» (igual que la isla de Nutrición, que dice «Sin “+”: el registro vive en la isla de abajo»). El botón flotante sigue debajo.']}});
scene({id:'nc-c-vacio',tab:'nutricion',title:'Cuerpo · sin mediciones',html:()=>ncScreen({empty:true}),
 notes:{tags:[['out','Sale el botón del vacío'],['keep','Textos de vacío']],p:['Con el botón flotante, el botón «Registrar pesaje» dentro del estado vacío sobra (dos botones para lo mismo). Los textos «Registra tu primer pesaje» y «Aún no hay medidas» se mantienen; el flotante es la única acción.']}});

scene({id:'nc-s-primera',tab:'nutricion',title:'Hoja · primera medición',html:ncSheetScene({first:true},{empty:true}),after:ncAfter({first:true}),
 notes:{tags:[['new','Hoja y regla rediseñadas'],['out','Esquinas negras']],
  p:['<b>Causa del recorte de hoy</b>: la regla del asistente se pinta sobre un rectángulo propio de fondo negro con esquinas rectas; dentro de una hoja gris con esquinas redondas se ve como un bloque cortado. Aquí la regla <b>no tiene caja</b>: los ticks se pintan directos sobre la superficie de la hoja y se desvanecen en los extremos con una máscara, así no hay borde que cortar. La hoja tiene las esquinas superiores de 28 dp y baja hasta el borde.',
     'Sin historial: no hay referencia, la cifra está atenuada y «Guardar» desactivado hasta tocar la regla (la posición inicial no es una respuesta). Prueba: arrastra la regla con inercia, o usa ±0,1.'],
  list:['Cifra grande Syne 52 con unidad; el valor atenuado pasa a tinta al primer toque.','±0,1 a ambos lados (48 dp): ajuste fino sin precisión de dedo.','Tocar el valor lo confirma (como en el asistente).','Marcas: kilo (con número), medio kilo, décima; la aguja en tinta.'],listTitle:'La regla'}});
scene({id:'nc-s-historial',tab:'nutricion',title:'Hoja · con historial',html:ncSheetScene({v:64},{}),after:ncAfter({v:64}),
 notes:{tags:[['new','Último valor y diferencia']],p:['Arranca en el último peso (64,0 kg). Debajo de la cifra: «Último: 64,0 kg · hace 3 días» y la diferencia en vivo (ahora «Igual»). La diferencia es neutra de color: que subir o bajar sea bueno depende del objetivo (lo decide el módulo «Peso», no la hoja).','«Guardar» espera a que muevas la regla o toques el valor; el texto lo dice.']}});
scene({id:'nc-s-atipico',tab:'nutricion',title:'Hoja · valor atípico',html:ncSheetScene({v:68.5,weird:true},{}),after:ncAfter({v:68.5,weird:true}),
 notes:{tags:[['new','Aviso de valor atípico'],['ask','Umbral a decidir']],p:['Si la diferencia con el último pesaje es mayor de 2,5 kg (propuesta), la diferencia se tiñe de ámbar y aparece un aviso que <b>no bloquea</b>: «Si es correcto, guárdalo igualmente». Es ámbar (atención), no rojo (error).','Hoy no hay ningún aviso de este tipo. Propuesta de umbral: más de 2,5 kg frente a un pesaje de hace 7 días o menos; con pesajes más antiguos no se avisa.']}});
scene({id:'nc-s-extras',tab:'nutricion',title:'Hoja · grasa, músculo y medidas',html:ncSheetScene({v:64.2,touched:true,fat:18.4,muscle:41.2,meas:true},{}),after:ncAfter({v:64.2,touched:true,fat:18.4,muscle:41.2,meas:true}),
 notes:{tags:[['keep','Mismos datos que hoy'],['new','Chips «Añade más datos»']],p:['Los tipos de dato son los de hoy: peso (obligatorio), % de grasa y % de músculo con su propia regla (opcionales, se quitan con la ✕) y las seis medidas en cm (cintura, cadera, pecho, brazo, muslo, cuello). La hoja desplaza su contenido y «Guardar» queda fijo abajo. Las dos reglas pequeñas comparten el mismo trato (sin caja, con máscara).','Sin dato, «Grasa» y «Músculo» son chips de «+». Las medidas siguen plegadas hasta tocarlas.']}});
scene({id:'nc-s-errores',tab:'nutricion',title:'Hoja · hora futura y medida fuera de rango',html:ncSheetScene({v:64.2,touched:true,future:true,bad:true,meas:true,full:true},{}),after:ncAfter({v:64.2,touched:true,future:true,bad:true,meas:true,full:true}),
 notes:{tags:[['keep','Validaciones de hoy']],p:['Dos bloqueos que ya existen: la hora no puede ser futura y las medidas deben estar en rango. El campo afectado se marca en rojo y «Guardar» se desactiva. Las frases son las del código.']}});
scene({id:'nc-s-fecha',tab:'nutricion',title:'Hoja · elegir el día',html:ncSheetScene({v:64.2,touched:true,calendar:true},{}),after:ncAfter({v:64.2,touched:true,calendar:true}),
 notes:{tags:[['keep','Calendario hasta hoy']],p:['El día abre el calendario del mes (no se puede elegir el futuro: los días tras el 29 van apagados). La hora abre el reloj del sistema (diálogo nativo, no se dibuja).']}});
scene({id:'nc-s-editar',tab:'nutricion',title:'Hoja · editar medición',html:ncSheetScene({mode:'edit',v:64,fat:18.4,touched:true},{}),after:ncAfter({mode:'edit',v:64,fat:18.4,touched:true}),
 notes:{tags:[['keep','Editar y eliminar']],p:['Al tocar una fila del historial: misma hoja con «Editar medición» y, bajo «Guardar», «Eliminar medición» en rojo. «Guardar» se activa solo si cambia algo.']}});
scene({id:'nc-s-eliminar',tab:'nutricion',title:'Confirmar eliminar',html:()=>ncScreen({fab:false})+ncSheet({mode:'edit',v:64,fat:18.4,touched:true})+`<div class="pl-scrim" style="z-index:62"></div><div class="nc-dlg" role="alertdialog"><h4>¿Eliminar esta medición?</h4><p>Se borran el peso y las medidas de este día y hora. Podrás deshacerlo justo después.</p><div class="r"><span class="c">Cancelar</span><span class="d">Eliminar</span></div></div>`,
 notes:{tags:[['keep','Texto del código']],p:['Confirmación con las frases de hoy; tras eliminar sale «Deshacer».']}});
scene({id:'nc-s-hc',tab:'nutricion',title:'Hoja · de Health Connect (solo lectura)',html:ncSheetScene({mode:'ro',v:64.2,fat:18.0},{}),
 notes:{tags:[['keep','Solo lectura']],p:['Una medición sincronizada desde Health Connect no se edita: la hoja solo la muestra y tiene «Cerrar».']}});
scene({id:'nc-c-guardado',tab:'nutricion',title:'Cuerpo · medición guardada',html:()=>ncScreen({newRow:64.5,snack:true}),after:(ph)=>{const b=ph.querySelector('[data-nc=bd]');if(b)b.scrollTop=250},
 notes:{tags:[['keep','Aviso con Deshacer'],['new','Fila nueva resaltada']],p:['Al guardar, la hoja se cierra y la fila nueva entra resaltada un instante al principio del mes; el peso y la tendencia se actualizan; el aviso «Medición guardada» ofrece «Deshacer» y queda sobre el botón flotante, que vuelve.']}});

// Cuerpo interactivo: isla por scroll, FAB abre hoja, guardar vuelve con la fila nueva.
function ncBodyWire(ph){
  const host=ph.querySelector('[data-nc=host]');
  const mount=(opt)=>{host.innerHTML=ncScreen(opt);const bd=host.querySelector('[data-nc=bd]'),isl=host.querySelector('[data-nc=isl]'),fab=host.querySelector('[data-nc=fab]');
    if(opt.scrollTo)bd.scrollTop=opt.scrollTo;
    bd.addEventListener('scroll',()=>isl.classList.toggle('gone',bd.scrollTop<90));
    fab.addEventListener('click',()=>{fab.classList.add('gone');host.insertAdjacentHTML('beforeend',ncSheet({v:NC_LAST.kg}));const o={v:NC_LAST.kg};
      ncSheetWire(host,o,{close:close,save:v=>{close();mount({newRow:v,snack:true,scrollTo:0});setTimeout(()=>{const s=host.querySelector('.nc-snack');if(s)s.remove()},3200)}})});
    const close=()=>{host.querySelectorAll('[data-nc=scrim],[data-nc=sheet]').forEach(e=>e.remove());fab.classList.remove('gone')};
  };
  mount({});
}
// Dictado interactivo: reposo → escuchando (se escribe) → leyendo → resultado; parar en vacío = «No te oí».
function ncDictWire(ph){
  const host=ph.querySelector('[data-nc=host]');let text='',timer=null,tick=null,t=0;
  const show=(st,extra)=>{host.innerHTML=ncDictView(Object.assign({st,text,t},extra||{}));bind()};
  const stopAll=()=>{clearInterval(timer);clearInterval(tick)};
  const process=()=>{stopAll();if(!text){show('nohear');return}show('proc');setTimeout(()=>show('result'),1700)};
  const listen=()=>{stopAll();t=0;show('listen',{run:true,partial:0});let i=text?NC_WORDS.length:0;
    tick=setInterval(()=>{t++;const e=host.querySelector('[data-nc=time]');if(e)e.textContent=ncTime(t)},1000);
    timer=setInterval(()=>{if(i<NC_WORDS.length){i++;text=NC_WORDS.slice(0,i).join(' ');const x=host.querySelector('[data-nc=txt]');
        const w=text.split(' '),n=Math.min(2,w.length);x.innerHTML=`${w.slice(0,w.length-n).join(' ')}${w.length>n?' ':''}<span class="part">${w.slice(-n).join(' ')}</span><span class="caret"></span>`}
      else{process()}},400)};
  const bind=()=>{
    host.querySelectorAll('[data-nc=dict],[data-nc=retry]').forEach(b=>b.onclick=()=>{if(text&&host.querySelector('[data-nc=edit]'))text='';listen()});
    const s=host.querySelector('[data-nc=stop]');if(s)s.onclick=process;
    const a=host.querySelector('[data-nc=analyze]');if(a)a.onclick=process;
    host.querySelectorAll('[data-nc=type]').forEach(b=>b.onclick=()=>{text=text||'';show('rest')});
    const e=host.querySelector('[data-nc=edit]');if(e)e.onclick=()=>show('rest');
  };
  bind();
}
