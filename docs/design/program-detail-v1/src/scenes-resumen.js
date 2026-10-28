/* ================= RESUMEN DEL MES («wrapped») ================= */
// Octubre 2026 de «Motor y fuerza» (datos de ejemplo). El 1 de octubre de 2026 es jueves.
const OCT={trained:[6,7,8,10,11,13,14,15,17,20,21,22,27,28],pr:27,missed:[24]};
const lnSvg=(d,w,h,acc='var(--mus)')=>`<svg class="draw" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" aria-hidden="true"><path class="ln" pathLength="1" d="${d}" fill="none" stroke="${acc}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
const sparkD=(v,w,h)=>{const mn=Math.min(...v),mx=Math.max(...v),r=mx-mn||1;return v.map((x,i)=>(i?'L':'M')+(2+i*(w-4)/(v.length-1)).toFixed(1)+' '+(h-3-(x-mn)/r*(h-6)).toFixed(1)).join('')};
function radial(){const ms=[['Dorsales',58],['Deltoides',56],['Cuádriceps',52],['Pectorales',46],['Glúteos',42],['Isquios',36],['Bíceps',34],['Tríceps',28],['Pantorrillas',0]];
  let s='';ms.forEach((m,i)=>{const a=-Math.PI/2+i/ms.length*Math.PI*2;const L=26+m[1]*1.65;const x=150+Math.cos(a)*L,y=140+Math.sin(a)*L;
    s+=`<path class="ln" pathLength="1" d="M150 140L${x.toFixed(1)} ${y.toFixed(1)}" stroke="${i===0?'var(--mus)':'#f2eee6'}" stroke-width="${i===0?3:1.4}" stroke-linecap="round" opacity="${m[1]?1:.3}"/>`;
    const lx=150+Math.cos(a)*(L+14),ly=140+Math.sin(a)*(L+14);s+=`<text x="${lx.toFixed(1)}" y="${(ly+4).toFixed(1)}" font-family="Inter" font-size="10" fill="#8e8980" text-anchor="${Math.cos(a)>.3?'start':Math.cos(a)<-.3?'end':'middle'}">${m[0]}</text>`});
  for(let k=1;k<=4;k++)s+=`<circle class="ln" pathLength="1" cx="150" cy="140" r="${k*28}" fill="none" stroke="#ffffff14"/>`;
  return `<svg class="draw" viewBox="-40 0 380 280" width="100%" style="margin:6px 0 4px" aria-hidden="true">${s}</svg>`}
const SLIDES=[
 {art:['relieve','carbon',5],ey:'Octubre 2026',body:()=>`<div class="wr-h rise d1" style="font-size:calc(44px*var(--fs))">Tu octubre en ${PROGRAM.name}</div><div class="wr-p rise d2">Cuatro semanas de entreno. Esto es lo que pasó.</div>`},
 {ey:'Constancia',body:()=>`<div class="wr-big rise d1"><span data-count="14">0</span><small>sesiones</small></div>
   <div class="cal">${Array.from({length:31+3},(_,i)=>{const d=i-2;if(d<1)return '<span></span>';const on=OCT.trained.includes(d);return `<i class="${on?'on':''} ${d===OCT.pr?'pr':''}" style="animation-delay:${(300+d*45)}ms" title="${d}"></i>`}).join('')}</div>
   <div class="wr-p rise d4"><span data-count="12">0</span> h 40 min entrenando. Solo faltaste una vez, el viernes 24.</div>`},
 {art:['ondas','carbon',2],ey:'Trabajo total',body:()=>`<div class="wr-big rise d1"><span data-count="42.4" data-dec="1">0</span><small>toneladas</small></div>
   <div class="wbars">${[[62,'S1'],[66,'S2'],[58,'S3'],[60,'S4']].map((w,i)=>`<div><i style="height:${w[0]*1.5}px;animation-delay:${400+i*160}ms"></i><span>${w[1]} · ${w[0]}</span></div>`).join('')}</div>
   <div class="wr-p rise d4">246 series duras y 1.986 repeticiones. <span class="wr-acc">+9 %</span> que en septiembre.</div>`},
 {art:['perfil','grafito',3],ey:'Fuerza',body:()=>`<div class="wr-h rise d1">Subiste en 3 de 4 básicos</div>
   <div class="rise d2">${[['Sentadilla trasera',[135,136,138,139,141,142.5],'+7,5'],['Press banca',[97,97.5,99,99,100,101],'+4'],['Dominadas lastradas',[20,21,22,23,24,25],'+5']].map(l=>`<div class="wlift"><strong>${l[0]}</strong>${lnSvg(sparkD(l[1],84,28),84,28)}<b>${l[2]}<span style="font:600 11px Inter;color:#8e8980"> kg</span></b></div>`).join('')}</div>
   <div class="wr-p rise d4" style="margin-top:10px">e1RM de tu mejor serie de cada sesión.</div>`},
 {art:['orbitas','noche',4],ey:'Tu mejor marca',body:()=>`<div class="wr-big rise d1"><span data-count="142.5" data-dec="1">0</span><small>kg</small></div><div class="wr-h rise d2" style="font-size:calc(26px*var(--fs))">Sentadilla trasera</div>
   <div class="wr-p rise d3">120 kg × 5 con RIR 1, el lunes 27. Es tu e1RM más alto: +7,5 kg desde el 6 de octubre.</div>`},
 {ey:'Lo que más trabajaste',body:()=>`${radial()}<div class="wr-h rise d3" style="font-size:calc(28px*var(--fs))">58 series de dorsales</div><div class="wr-p rise d4">Ningún trabajo directo de pantorrillas en todo el mes.</div>`},
 {art:['campo','grafito',7],ey:'Esfuerzo',body:()=>`<div class="wr-big rise d1"><span data-count="1.6" data-dec="1">0</span><small>RIR medio</small></div><div class="wr-h rise d2" style="font-size:calc(24px*var(--fs))">Cerca del fallo, como pedía el plan</div>
   <div class="wr-p rise d3">El plan pedía 2. La semana 3 bajaste a 1,2 con las mismas cargas: fue la más dura del mes.</div>`},
 {art:['relieve','grafito',9],ey:'A vigilar',body:()=>`<div class="wr-h rise d1">Press militar bajó 2,5 kg</div><div class="wr-p rise d2">Y el hombro derecho avisó 3 veces, dos de ellas con press militar. Peso muerto rumano lleva 3 semanas sin subir.</div>
   <div class="wr-p rise d3" style="margin-top:10px">La descarga de la semana 6 es buen momento para ver si se recuperan.</div>`},
 {art:['espiral','carbon',1],ey:'En noviembre',body:()=>`<div class="wr-h rise d1">Fuerza y descarga</div><div class="rise d2" style="display:flex;gap:4px;margin:6px 0 10px">${PROGRAM.weeks.map(w=>`<i style="flex:1;height:6px;border-radius:3px;background:${PH[w.ph].c};opacity:${w.n>=4?1:.25}"></i>`).join('')}</div>
   <div class="wr-p rise d3">Semanas 4 a 6: top set y back-off, y una semana de descarga para llegar fresco al Open regional del 16 de noviembre.</div>`},
 {ey:'Tu octubre',body:()=>`<div class="rise d1" style="display:flex;justify-content:center;margin-bottom:14px">${storyProgreso({w:'200px'})}</div>
   <div class="rise d3" style="display:flex;gap:8px"><button class="pill pri sm" style="flex:1">${ic('share',16)}Compartir</button><button class="pill sec sm" style="flex:1">Ver progreso</button></div>`},
];
function wrapped(i,{interactive=false}={}){const s=SLIDES[i];
  return `<div class="wr" data-i="${i}">${s.art?`<div class="art draw">${coverSvg(s.art[0],s.art[1],{seed:s.art[2]})}</div>`:''}
  <div class="wr-bars">${SLIDES.map((_,k)=>`<i class="${k<i?'done':k===i?'on':''}"></i>`).join('')}</div>
  <div class="wr-top"><span>KPKN · OCTUBRE</span><button class="ib" aria-label="Cerrar" style="color:#f2eee6">${ic('close')}</button></div>
  <div class="wr-body">${s.ey?`<div class="wr-ey rise">${s.ey}</div>`:''}${s.body()}</div>
  ${interactive?'<button class="wr-tap l" aria-label="Anterior"></button><button class="wr-tap r" aria-label="Siguiente"></button>':''}</div>`}
function playWrapped(ph,start=0){
  clearTimeout(window._wrT);let i=start;
  const show=()=>{const old=ph.querySelector('.wr');const tmp=document.createElement('div');tmp.innerHTML=wrapped(i,{interactive:true});const el=tmp.firstElementChild;
    if(old)old.replaceWith(el);else ph.appendChild(el);animateIn(el);
    el.querySelector('.wr-tap.l').onclick=()=>{i=Math.max(0,i-1);show()};el.querySelector('.wr-tap.r').onclick=()=>{if(i<SLIDES.length-1){i++;show()}};
    clearTimeout(window._wrT);if(!reduceMotion()&&i<SLIDES.length-1)window._wrT=setTimeout(()=>{if(document.body.contains(el)){i++;show()}},6000)};
  show();
}
function homeMock(){return `<div class="scroll" style="padding-top:52px"><div class="cap" style="letter-spacing:1.4px;font-weight:600">MIÉRCOLES 29 OCT</div><div class="ptitle" style="margin-top:4px">Buenos días</div>
  <div class="home-notice"><div class="art draw">${coverSvg('relieve','carbon',{seed:5})}</div><div class="in"><div class="ey" style="color:var(--mus)">Nuevo · resumen del mes</div><div style="font:800 calc(22px*var(--fs))/1.08 Syne;letter-spacing:-.5px;margin:8px 0 6px">Tu octubre está listo</div><div class="cap" style="color:var(--muted)">14 sesiones, 42,4 t y una marca nueva.</div>
  <button class="pill pri sm" style="margin-top:14px">${ic('play',14)}Ver</button></div><button class="ib" aria-label="Descartar" style="position:absolute;top:6px;right:6px;color:var(--faint)">${ic('close',18)}</button></div>
  <div class="mod"><div class="mod-h"><span class="ey">Hoy</span><span class="cap">≈ 48 min</span></div><div style="display:flex;align-items:center;gap:12px;margin-top:6px"><div style="flex:1"><div style="font:700 calc(20px*var(--fs)) Syne">Tirón</div><div class="cap">5 ejercicios · Semana 4 · Intensificación</div></div><button class="play" aria-label="Empezar">${ic('play',18)}</button></div></div></div>${navbar()}`}

scene({tab:'resumen',id:'r-home',title:'Aviso en Inicio',after:drawAfter,html:homeMock,
 notes:{tags:[['new','Nuevo: resumen mensual']],p:['El primer día de cada mes aparece en Inicio un módulo con la portada de líneas dibujándose: «Tu octubre está listo», una línea con tres cifras y «Ver». Va arriba, encima de la sesión de hoy, y se puede descartar con la cruz.','Si lo descartas o ya lo viste, desaparece de Inicio y queda en Progreso › «Resúmenes mensuales», donde se guardan todos.','Solo se genera si en el mes hubo al menos 4 sesiones del programa; si no, no hay aviso.']}});
scene({tab:'resumen',id:'r-play',title:'Reproducir (interactivo)',after:ph=>playWrapped(ph),html:()=>'',
 notes:{tags:[['new','Interactivo']],p:['A pantalla completa, como una historia: barras arriba, avance solo cada 6 s, toque a la derecha para seguir y a la izquierda para volver, y cruz para cerrar. Cada pantalla entra con sus líneas dibujándose y sus cifras contando.','Con movimiento reducido no avanza solo y las cifras salen ya puestas.'],
  listTitle:'Las 10 pantallas',list:['Portada del mes.','Constancia: sesiones y calendario del mes (la marca nueva, en color).','Trabajo total: toneladas, series por semana y comparación con el mes anterior.','Fuerza: los básicos que subieron, con su línea.','Tu mejor marca.','Lo que más trabajaste: series por músculo como rayos.','Esfuerzo: RIR medio frente al plan.','A vigilar: lo que baja, lo que se estanca y las molestias.','Lo que viene el mes siguiente.','Tarjeta para compartir.']}});
SLIDES.forEach((s,i)=>scene({tab:'resumen',id:'r-s'+i,title:`${i+1} · ${s.ey}`,after:ph=>animateIn(ph),html:()=>wrapped(i),
 notes:{p:[['Abre con la portada de líneas del mes y una frase. Sin cifras todavía.','Las sesiones cuentan hacia arriba y los días entrenados aparecen uno a uno en el calendario. El día de la marca va en color músculo. La falta se nombra con su día, sin reproche.','Toneladas (solo series con carga externa), una línea por semana que crece y la comparación con el mes anterior si existe.','Solo los básicos con al menos 4 sesiones; cada línea se dibuja de izquierda a derecha.','El e1RM más alto del mes, con la serie que lo produjo.','Un rayo por músculo: su largo son las series del mes. El más trabajado, en color; los que no se tocaron, apagados y nombrados.','RIR medio registrado frente al pedido, y la semana más dura.','Honesto: lo que baja, lo que se estanca y las molestias, con su posible relación. Sin diagnosticar.','La cinta de fases del mes siguiente y lo que trae (incluida la competición si la hay).','La misma tarjeta que la historia «Progreso», lista para compartir, y un acceso a la pestaña Progreso.'][i]],
  list2Title:i===0?'De dónde salen los datos':undefined,list2:i===0?['Los mismos cálculos de la pestaña Progreso, acotados al mes natural.','Nada inventado: si un dato no existe (por ejemplo, el mes anterior), la frase se omite.']:undefined}}));
