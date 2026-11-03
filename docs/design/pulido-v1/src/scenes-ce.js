/* ===== CARDIO-EDITOR (prefijo ce) · Pulido v1 ===== */
/*ART-START*/
// Ilustraciones a medida de las 12 modalidades de cardio. Lienzo 64 × 64, trazo 2 (a 56 px = 1,75 px, como KPKN Line),
// extremos y uniones redondeados. Dos tintas: la máquina o el lugar (grupo m, tinta crema apagada) y la persona (grupo f, azul cardio).
// La persona se construye siempre igual: cabeza (r 3,4) sobre el cuello, tronco cuello→cadera, brazos hombro→codo→mano y piernas
// cadera→rodilla→pie (muslo ≈ pierna ≈ 9–10, brazo ≈ 7+7). Así las doce comparten proporciones.
const ceFig=(N,H,arms,legs)=>{
 const dx=N[0]-H[0],dy=N[1]-H[1],L=Math.hypot(dx,dy),ux=dx/L,uy=dy/L,r=n=>Math.round(n*10)/10;
 const head=[r(N[0]+ux*4.9),r(N[1]+uy*4.9)],S=[r(N[0]-ux*1.8),r(N[1]-uy*1.8)];
 const p=a=>a.map(q=>q[0]+' '+q[1]).join('L');
 return `<circle cx="${head[0]}" cy="${head[1]}" r="3.4"/><path d="M${N[0]} ${N[1]}L${H[0]} ${H[1]}"/>`+
  arms.map(a=>`<path d="M${S[0]} ${S[1]}L${p(a)}"/>`).join('')+legs.map(l=>`<path d="M${H[0]} ${H[1]}L${p(l)}"/>`).join('');
};
const CE_ART={
 // Cinta: corredor sobre la banda, consola al frente.
 TREADMILL:{m:'<rect x="6" y="51" width="42" height="5" rx="2.5"/><path d="M49 53L53 21M46 21h12"/>',
  f:ceFig([29,21],[24,35],[[[34,27],[39,23]],[[22,27],[17,24]]],[[[30,42],[27,50]],[[21,43],[12,46]]])},
 // Elíptica: pedales con pletina, columna con las asas móviles.
 ELLIPTICAL:{m:'<path d="M6 57v-4a3 3 0 013-3h37a3 3 0 013 3v4z"/><path d="M47 50L44 20M39 20h11"/><path d="M45 23L35 33"/><path d="M10 48h14M29 45h12"/>',
  f:ceFig([28,20],[27,33],[[[33,26],[36,31]]],[[[31,40],[33,44]],[[24,41],[17,47]]])},
 // Remo: monorraíl, volante a la izquierda, asiento, el remero a mitad de la palada.
 ROW_MACHINE:{m:'<path d="M16 52h44M60 52v5M16 52v5"/><circle cx="13" cy="40" r="9"/><path d="M13 40l3-4"/><path d="M26 47l3-7M39 49h12"/><path d="M36 35L22 39"/>',
  f:ceFig([50,33],[44,45],[[[43,35],[36,35]]],[[[35,40],[28,46]]])},
 // Bici estática: volante delantero, manillar y sillín, ciclista inclinado.
 BIKE_STATIONARY:{m:'<path d="M7 58h20M38 58h19"/><circle cx="43" cy="47" r="7"/><path d="M47 58L46 22M46 22h-9"/><path d="M17 58L21 36M15 35h11"/><path d="M21 51L40 48"/><circle cx="30" cy="50" r="2"/>',
  f:ceFig([28,20],[20,33],[[[33,25],[38,22]]],[[[29,39],[30,49]]])},
 // Escaladora: peldaños que bajan, barandilla frontal, la persona sube.
 STAIR_CLIMBER:{m:'<path d="M4 57h12V49h10V41h10V33h9"/><path d="M48 33v24M42 20h12M48 20v13"/>',
  f:ceFig([28,19],[27,32],[[[34,25],[42,21]]],[[[34,36],[32,40]],[[25,40],[21,48]]])},
 // SkiErg: unidad colgada arriba, dos asas con su cuerda; persona de frente tirando hacia abajo.
 SKI_ERG:{m:'<path d="M14 7h36a2 2 0 012 2v6a2 2 0 01-2 2H14a2 2 0 01-2-2V9a2 2 0 012-2z"/><circle cx="32" cy="12" r="3"/><path d="M8 58h48"/><path d="M22 17v10M42 17v10M19 27h6M39 27h6"/>',
  f:'<circle cx="32" cy="29" r="3.4"/><path d="M32 34v13"/><path d="M32 36l-7-3-1-6"/><path d="M32 36l7-3 1-6"/><path d="M32 47l-5 11M32 47l5 11"/>'},
 // Carrera exterior: camino que se pierde en el horizonte, colinas y el corredor.
 RUN_OUTDOOR:{m:'<path d="M3 40l9-8 6 6 8-10 9 11"/><path d="M36 49c-7 3-17 3-33 9"/><path d="M42 44c4-1 10 0 19-2"/><path d="M30 44l-4 4"/>',
  f:ceFig([38,22],[33,36],[[[43,28],[48,24]],[[31,28],[26,25]]],[[[39,43],[36,51]],[[29,44],[20,47]]])},
 // Bici exterior: bici de carretera con el ciclista agachado, y la carretera.
 BIKE_OUTDOOR:{m:'<circle cx="16" cy="46" r="9"/><circle cx="48" cy="46" r="9"/><path d="M16 46h15L26 30M26 30h16M31 46l11-16M42 30l6 16M42 30h6q3 0 3 3"/><path d="M23 29h7"/><path d="M3 58c10-1 20 0 30 1s20 0 28-2"/>',
  f:ceFig([38,21],[27,28],[[[43,26],[47,31]]],[[[36,35],[31,45]]])},
 // Caminata: senderista con mochila, montañas y sendero.
 WALK:{m:'<path d="M30 33l9-15 6 9 5-7 10 13"/><path d="M4 58c14-2 22-8 34-9s14-6 22-8"/>',
  f:ceFig([26,21],[25,36],[[[31,28],[34,34]],[[22,28],[19,34]]],[[[31,43],[33,54]],[[22,44],[15,52]]])+'<path d="M21 22h-5a2 2 0 00-2 2v8a2 2 0 002 2h5"/>'},
 // Air Bike: gran ventilador delantero, asas móviles y ciclista empujando.
 AIR_BIKE:{m:'<circle cx="47" cy="40" r="14"/><circle cx="47" cy="40" r="3"/><path d="M47 37V26M47 43v11M44 38l-10-7M50 42l10 7"/><path d="M47 40L25 46"/><path d="M19 57L23 37M16 36h13"/><path d="M8 58h48"/><path d="M44 40L42 21M37 21h9"/>',
  f:ceFig([31,22],[23,34],[[[36,23],[41,22]]],[[[32,40],[30,50]]])},
 // Cinta curva: banda en cuenco, sin motor ni consola; sprint inclinado.
 CURVED_TREADMILL:{m:'<path d="M5 42c4 11 14 15 28 15 14 0 22-4 26-13"/><path d="M11 50l2-3M19 54l1-3M27 56v-3M36 56v-3M44 54l-1-3M52 50l-2-2"/><path d="M58 44L61 24M53 24h11"/>',
  f:ceFig([35,22],[27,35],[[[41,28],[46,24]],[[29,29],[23,26]]],[[[35,42],[33,51]],[[24,43],[14,47]]])},
 // Trineo: trineo con disco, empujado con el cuerpo muy inclinado.
 SLED:{m:'<path d="M30 57h26c4 0 6-3 4-6"/><path d="M38 57L45 28M54 57L48 28M41 28h10"/><circle cx="50" cy="46" r="5"/><circle cx="50" cy="46" r="1"/>',
  f:ceFig([32,28],[20,41],[[[37,29],[42,29]]],[[[28,48],[24,57]],[[16,48],[8,55]]])},
};
const ceArt=(k,o={})=>{const s=o.size||56,sw=Math.round(Math.min(2.6,1.75+Math.max(0,s-56)*.01)*64/s*100)/100;return `<svg class="ce-art ${o.cls||''}" width="${s}" height="${s}" viewBox="0 0 64 64" fill="none" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><g class="m">${CE_ART[k].m}</g><g class="f">${CE_ART[k].f}</g></svg>`};
/*ART-END*/

/* ---------- Iconos que faltaban (retícula 24, trazo 1,75) ---------- */
Object.assign(P,{
 ceUp:'<path d="M12 19V5M6 11l6-6 6 6"/>',ceDown:'<path d="M12 5v14M6 13l6 6 6-6"/>',
 ceMinus:'<path d="M5 12h14"/>',ceGps:'<path d="M12 21s6-5.6 6-10.5a6 6 0 00-12 0C6 15.4 12 21 12 21z"/><circle cx="12" cy="10.5" r="2.2"/>',
 ceSun:'<circle cx="12" cy="12" r="3.5"/><path d="M12 3.5v2M12 18.5v2M3.5 12h2M18.5 12h2M6 6l1.4 1.4M16.6 16.6L18 18M6 18l1.4-1.4M16.6 7.4L18 6"/>',
 ceVoice:'<path d="M4 10v4h4l5 4V6L8 10z"/><path d="M16.5 9.5a4 4 0 010 5M19 7a7.5 7.5 0 010 10"/>',
 ceVib:'<rect x="8" y="3.5" width="8" height="17" rx="2.5"/><path d="M4 8v8M20 8v8"/>',
 ceBeep:'<path d="M6 16V11a6 6 0 0112 0v5l1.5 2h-15z"/><path d="M10 20.5h4"/>',
 ceChart:'<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
 ceGuide:'<path d="M5 4.5h10a4 4 0 014 4V20H9a4 4 0 01-4-4z"/><path d="M5 16a4 4 0 014-4h10"/>',
 ceBack:'<path d="M15 5l-7 7 7 7"/>',
 ceKey:'<path d="M5 12.5l4.5 4.5L19 7.5"/>',
 ceBs:'<path d="M20 6H9l-5 6 5 6h11zM13 10l4 4M17 10l-4 4"/>',
});

/* ---------- Datos reales de la app (CardioCatalog.kt): las 12 modalidades, en el orden del catálogo ---------- */
const CE_T=[
 {k:'TREADMILL',name:'Cinta de correr',short:'Cinta',desc:'Ritmo y distancia en cinta.',g:'in',gps:0,dist:1,speed:1,incl:1,rpm:0,watts:0,pace:1},
 {k:'ELLIPTICAL',name:'Elíptica',short:'Elíptica',desc:'Trabajo continuo de bajo impacto.',g:'in',gps:0,dist:1,speed:0,incl:0,rpm:1,watts:0,pace:0},
 {k:'ROW_MACHINE',name:'Remo ergómetro',short:'Remo',desc:'Intervalos o trabajo continuo en remo.',g:'in',gps:0,dist:1,speed:0,incl:0,rpm:1,watts:1,pace:0},
 {k:'BIKE_STATIONARY',name:'Bicicleta estática',short:'Bici estática',desc:'Cardio indoor con distancia estimada.',g:'in',gps:0,dist:1,speed:0,incl:0,rpm:1,watts:1,pace:0},
 {k:'STAIR_CLIMBER',name:'Escaladora',short:'Escaladora',desc:'Trabajo continuo en escaladora.',g:'in',gps:0,dist:1,speed:0,incl:0,rpm:0,watts:0,pace:0},
 {k:'SKI_ERG',name:'SkiErg',short:'SkiErg',desc:'Ergómetro de esquí con metros y vatios.',g:'in',gps:0,dist:1,speed:0,incl:0,rpm:0,watts:1,pace:0},
 {k:'RUN_OUTDOOR',name:'Carrera exterior',short:'Carrera exterior',desc:'Carrera con distancia y ritmo registrados por GPS.',g:'out',gps:1,dist:1,speed:1,incl:0,rpm:0,watts:0,pace:1},
 {k:'BIKE_OUTDOOR',name:'Bicicleta exterior',short:'Bici exterior',desc:'Ciclismo con distancia y ritmo registrados por GPS.',g:'out',gps:1,dist:1,speed:1,incl:0,rpm:0,watts:0,pace:0},
 {k:'WALK',name:'Caminata',short:'Caminata',desc:'Caminata continua o por intervalos con GPS.',g:'out',gps:1,dist:1,speed:1,incl:0,rpm:0,watts:0,pace:1},
 {k:'AIR_BIKE',name:'Air Bike',short:'Air Bike',desc:'Bicicleta de brazos y piernas, ideal para sprints.',g:'sp',gps:0,dist:0,speed:0,incl:0,rpm:1,watts:1,pace:0},
 {k:'CURVED_TREADMILL',name:'Cinta curva',short:'Cinta curva',desc:'Cinta autopropulsada para sprints y ritmo manual.',g:'sp',gps:0,dist:1,speed:1,incl:0,rpm:0,watts:0,pace:1},
 {k:'SLED',name:'Trineo (sled)',short:'Trineo',desc:'Empuje o arrastre de trineo por distancia corta.',g:'sp',gps:0,dist:1,speed:0,incl:0,rpm:0,watts:0,pace:0},
];
const CE_G=[['in','EN INTERIOR'],['out','AL AIRE LIBRE'],['sp','SPRINT Y POTENCIA']];
const ceT=k=>CE_T.find(t=>t.k===k);
const CE_ZC=['#8AA3B3','#87C9A5','#CDBB8F','#F49A6E','#E9736C'];            // CardioZoneColors
const CE_ZN=['Recuperación','Base aeróbica','Tempo','Umbral','Máximo'];       // CardioZoneNames
const ceZone=r=>Math.min(5,Math.max(1,Math.floor((r+1)/2)));                  // (nivel + 1) / 2, como cardioSteps
const ceAnchor=r=>r<=2?'Muy suave':r<=4?'Suave':r<=6?'Algo duro':r<=8?'Duro':r===9?'Muy duro':'Máximo';  // CardioPrescriptionFormatter.rpeAnchor
const ceP2=n=>String(n).padStart(2,'0');
const ceM=s=>Math.floor(s/60)+':'+ceP2(s%60);                                  // «1:00», como KpknSetFormat.doseTime
const ceDur=s=>{if(s==null)return 'Libre';const h=Math.floor(s/3600),m=Math.floor(s%3600/60),x=s%60;
 if(h)return x?`${h} h ${m} min ${x} s`:`${h} h ${m} min`;if(m)return x?`${m} min ${x} s`:`${m} min`;return `${x} s`};
const ceCell=s=>s==null?['Libre','']:s>=60&&s%60===0?[String(s/60),'min']:s>=60?[ceM(s),'']:[String(s),'s'];
const ceKmT=k=>String(k).replace('.',',');
const ceCellH=s=>{const [v,u]=ceCell(s);return v+(u?`<em>${u}</em>`:'')};
const ceV=b=>b.v||({A:6,W:10,R:5.5,C:5})[b.t];
const ceCellTxt=s=>{const c=ceCell(s);return c[1]?c[0]+' '+c[1]:c[0]};

/* ---------- Gráfica de bloques: anchura = duración, altura = intensidad (como CardioIntervalChart) ---------- */
const ceB=(t,s,l)=>({t,s,l});   // t: A calentamiento · W esfuerzo · R pausa · C enfriamiento
function ceSeriesBlocks(sr){
 const b=[];if(sr.warmMode==='v'&&sr.warm>0)b.push(ceB('A',sr.warm,3));
 for(let s=0;s<sr.sets;s++){for(let i=0;i<sr.rounds;i++){b.push(ceB('W',sr.work,sr.rpe));if(sr.rest>0)b.push(ceB('R',sr.rest,sr.nature==='A'?3:1))}
  if(s<sr.sets-1&&sr.restSets>0)b.push(ceB('R',sr.restSets,sr.nature==='A'?3:1))}
 if(sr.coolMode==='v'&&sr.cool>0)b.push(ceB('C',sr.cool,2));return b}
const ceTot=bl=>bl.reduce((a,b)=>a+b.s,0);
function ceChart(bl,o={}){
 return `<div class="ce-chart ${o.cls||''}" style="height:${o.h||56}px">${bl.map((b,i)=>`<i class="ce-b ${b.t}${o.sel===i?' sel':''}${o.sel!=null&&o.sel!==i?' dim':''}" ${o.pick?`data-a="bsel" data-i="${i}"`:''} style="flex:${b.s} 1 0;height:${Math.max(16,b.l*10)}%"></i>`).join('')}</div>`}
/* Plantillas reales (CardioFeaturedTemplates + CardioHiitTemplates): su forma y su dosis, tal como las calcula cardioDoseLine. */
const ceRep=(w,wl,r,rl,n,warm,cool)=>{const b=[];if(warm)b.push(ceB('A',warm,3));for(let i=0;i<n;i++){b.push(ceB('W',w,wl));b.push(ceB('R',r,rl))}if(cool)b.push(ceB('C',cool,2));return b};
const CE_TPL=[
 {id:'tabata',n:'Tabata',dose:'8 × 0:20 / 0:10 · RPE 10',bl:ceRep(20,10,10,3,8,300,180),tot:12},
 {id:'3030',n:'30/30',dose:'10 × 0:30 / 0:30 · RPE 9',bl:ceRep(30,8,30,4,10,300,240),tot:0},
 {id:'11',n:"1'/1'",dose:'8 × 1:00 / 1:00 · RPE 9',bl:ceRep(60,9,60,3,8,300,180),tot:0},
 {id:'msit',n:'Micro-SIT',dose:'8 × 0:10 / 1:00 · RPE 10',bl:ceRep(10,10,60,2,8,300,180),tot:0},
 {id:'wing',n:'Wingate',dose:'4 × 0:30 / 4:00 · RPE 10',bl:ceRep(30,10,240,2,4,300,180),tot:0},
 {id:'pyr',n:'Pirámide',dose:'11 bloques · 25 min',bl:[ceB('A',300,3),ceB('W',60,7),ceB('R',60,4),ceB('W',120,8),ceB('R',120,4),ceB('W',180,8),ceB('R',180,4),ceB('W',120,8),ceB('R',120,4),ceB('W',60,7),ceB('C',180,3)],tot:0},
 {id:'z2',n:'Z2 30 min',dose:'30 min · RPE 5',bl:null,tot:0},
];
const CE_PAT=[ // CardioIntervalPrograms.specs
 {n:'Pirámide',d:'Sube 1→5 y vuelve a bajar.',u:[1,2,3,4,5,4,3,2,1]},
 {n:'Pirámide inversa',d:'Pico al inicio y descarga progresiva.',u:[5,4,3,2,1,2,3,4,5]},
 {n:'Escalera',d:'Sube y mantiene el nivel alto.',u:[2,3,4,5,5],w:[2,3,4,5,5]},
 {n:'1:1 constante',d:'Trabajo y recuperación iguales.',u:[7,3,7,3,7,3,7,3],r:1},
 {n:'2:1',d:'Dos partes de trabajo por una de recuperación.',u:[8,3,8,3,8,3],w:[2,1,2,1,2,1],r:1},
 {n:'Fartlek',d:'Libre estructurado con cambios de ritmo.',u:[5,3,8,4,6,9],w:[2,1,1,1,2,1]},
 {n:'Personalizado',d:'Bloques manuales editables.',u:[6]},
];
const cePatBl=p=>p.u.map((l,i)=>ceB(p.r&&i%2===1?'R':'W',(p.w?p.w[i]:1)*60,l*2));

/* ---------- Estado de un cardio (lo que guarda CardioDetails) ---------- */
const ceSeries=o=>Object.assign({warm:180,warmMode:'v',work:30,rest:60,rounds:8,cool:180,coolMode:'v',rpe:9,proto:'HIIT',sets:1,restSets:120,nature:'A',target:'t',tval:null},o||{});
const ceCard=(k,o)=>Object.assign({k,form:0,dur:1200,km:null,gps:ceT(k).gps===1,rpe:8,pace:null,zone:null,ser:ceSeries(),bl:null,bsel:1,rounds:1,beeps:true,voice:true,vib:true,screen:true,hist:null},o||{});
const CE_IRR=()=>[ceB('A',180,3),ceB('W',120,5),ceB('W',180,6),ceB('R',60,3),ceB('W',240,8),ceB('W',240,8),ceB('C',180,2)];  // 7 bloques · 20 min
function ceBlocks(c){return c.form===1?ceSeriesBlocks(c.ser):c.form===2?(c.bl||(c.bl=CE_IRR())):[]}
function ceDose(c){return ceDose0(c).replace(/RPE (d+)/g,'RPE $1')}
function ceDose0(c){
 if(c.form===0){const t=c.km?ceKmT(c.km)+' km':c.dur?ceDur(c.dur):'Libre';const sec=c.pace?ceM(c.pace)+'/km':c.zone?'Z'+c.zone:'RPE '+c.rpe;return t+' · '+sec}
 if(c.form===1){const s=c.ser;const w=s.target==='d'&&s.tval?s.tval+' m':s.target==='k'&&s.tval?s.tval+' kcal':ceM(s.work);return `${s.rounds} × ${w}${s.rest>0?' / '+ceM(s.rest):''}${s.sets>1?' · '+s.sets+' series':''} · RPE ${s.rpe}`}
 const bl=ceBlocks(c);const n=bl.length;return `${n} ${n===1?'bloque':'bloques'}${c.rounds>1?' × '+c.rounds:''} · ${ceDur(ceTot(bl)*c.rounds)}`}
const ceRpeOf=c=>c.form===1?c.ser.rpe:c.rpe;

/* ---------- Piezas ---------- */
const ceSw=on=>`<span class="ce-sw${on?' on':''}"><i></i></span>`;
const ceIb=(ico,a,label,extra='')=>`<button class="ce-ib" data-a="${a}" aria-label="${label}" ${extra}>${ic(ico,18)}</button>`;
P.ceSpark='<path d="M12 3.5l1.9 5.1 5.1 1.9-5.1 1.9L12 17.5l-1.9-5.1L5 10.5l5.1-1.9z"/><path d="M18.5 16.5v3M17 18h3"/>';
function ceTop(){return `<div class="ce-top ce-glass"><span class="ce-tib">${ic('back',22)}</span><div class="ce-day"><span class="ce-arw">${ic('back',20)}</span><span class="ce-daybtn"><span>Jue · Semana 3</span>${ic('down',15)}</span><span class="ce-arw">${ic('fwd',20)}</span></div><span class="ce-tib">${ic('more',22)}</span></div>`}
// Minutos de la sesión: la fuerza de la maqueta (18 min) más cada cardio según su forma.
function ceMin(st){let m=st.strength?18:0;for(const c of st.cards){const t=c.form===0?(c.dur||(c.km?c.km*6*60:1200)):c.form===1?ceTot(ceSeriesBlocks(c.ser)):ceTot(ceBlocks(c))*c.rounds;m+=Math.round(t/60)}return m}
function ceBottom(min,limit=90){
 const sc=min*100,col=!limit?'#8e8980':sc>limit*108?'var(--danger)':sc>=limit*85?'var(--ene)':'var(--done)',frac=Math.min(1,min/limit),C=2*Math.PI*11.5;
 return `<div class="ce-bar ce-glass"><span class="ce-time"><svg width="32" height="32" viewBox="0 0 32 32" aria-hidden="true"><circle cx="16" cy="16" r="11.5" fill="none" stroke="#ffffff1c" stroke-width="3.5"/><circle cx="16" cy="16" r="11.5" fill="none" stroke="${col}" stroke-width="3.5" stroke-linecap="round" stroke-dasharray="${(C*frac).toFixed(1)} ${C.toFixed(1)}" transform="rotate(-90 16 16)"/></svg><span class="tx"><b>${min} min</b><small>de ${limit}</small></span></span><span class="ce-addw"><span class="main">${ic('plus',18)}Añadir</span><i class="sp"></i><span class="chv">${ic('up',18)}</span></span><span class="ce-asi">${ic('ceSpark',22)}<em>3</em></span></div>`}
const ceThumb=(k,s=56)=>`<span class="ce-thumb" style="--ts:${s}px">${ceArt(k,{size:Math.round(s*.84)})}</span>`;
const ceToast=t=>t?`<div class="ce-toast pl-flat" role="status">${t}</div>`:'';

/* ---------- Selectores (duración, tiempo de fase, distancia, magnitudes de bloque) ---------- */
// Un solo control para todo: cifra grande legible (tocarla para escribir), regla con inercia para arrastrar, «−» y «+», atajos y «Aplicar».
// n = posición entera en la regla; to(n) = valor real. Se ajusta a la regla en cada paso, así que no hay valores intermedios.
const ceSecBig=s=>s<60?[String(s),'s']:[ceM(s),'min'];
const CE_CFG={
 dur:{title:'Duración',min:0,max:180,px:9,major:5,tick:n=>n,chips:[10,15,20,30,45,60],chipL:n=>n+' min',
  big:n=>n===0?['Libre','sin objetivo de tiempo']:n<60?[String(n),'min']:[Math.floor(n/60)+':'+ceP2(n%60),'h'],
  hint:n=>n>=60?ceDur(n*60):'',maxMsg:'Máximo 3 h',none:{n:0,l:'Libre'},
  get:c=>Math.round((c.dur||0)/60),set:(c,n)=>{c.dur=n?n*60:null},typed:b=>parseInt(b,10),kb:false},
 work:{title:'Esfuerzo',min:1,max:120,px:8,major:12,tick:n=>ceM(n*5),chips:[4,6,12,24,36],chipL:n=>ceM(n*5),
  big:n=>ceSecBig(n*5),hint:()=>'',maxMsg:'Máximo 10 min',get:c=>c.ser.work/5,set:(c,n)=>{c.ser.work=n*5},typed:b=>{const v=parseInt(b,10);return isNaN(v)?NaN:Math.round((Math.floor(v/100)*60+v%100)/5)},kb:false,mmss:true},
 rest:{title:'Pausa',min:0,max:120,px:8,major:12,tick:n=>ceM(n*5),chips:[2,6,12,24],chipL:n=>ceM(n*5),
  big:n=>n===0?['Sin pausa','']:ceSecBig(n*5),hint:()=>'',maxMsg:'Máximo 10 min',get:c=>c.ser.rest/5,set:(c,n)=>{c.ser.rest=n*5},typed:b=>{const v=parseInt(b,10);return isNaN(v)?NaN:Math.round((Math.floor(v/100)*60+v%100)/5)},mmss:true},
 warm:{title:'Calentamiento',min:0,max:80,px:8,major:4,tick:n=>ceM(n*15),chips:[12,20,40],chipL:n=>ceM(n*15),modes:true,
  big:n=>ceSecBig(n*15),hint:()=>'',maxMsg:'Máximo 20 min',get:c=>c.ser.warm/15,set:(c,n)=>{c.ser.warm=n*15},typed:b=>{const v=parseInt(b,10);return isNaN(v)?NaN:Math.round((Math.floor(v/100)*60+v%100)/15)},mmss:true,mode:c=>c.ser.warmMode,setMode:(c,m)=>{c.ser.warmMode=m}},
 cool:{title:'Enfriamiento',min:0,max:80,px:8,major:4,tick:n=>ceM(n*15),chips:[8,12,20],chipL:n=>ceM(n*15),modes:true,
  big:n=>ceSecBig(n*15),hint:()=>'',maxMsg:'Máximo 20 min',get:c=>c.ser.cool/15,set:(c,n)=>{c.ser.cool=n*15},typed:b=>{const v=parseInt(b,10);return isNaN(v)?NaN:Math.round((Math.floor(v/100)*60+v%100)/15)},mmss:true,mode:c=>c.ser.coolMode,setMode:(c,m)=>{c.ser.coolMode=m}},
 restSets:{title:'Descanso entre series',min:0,max:40,px:10,major:4,tick:n=>ceM(n*15),chips:[4,8,12],chipL:n=>ceM(n*15),
  big:n=>ceSecBig(n*15),hint:()=>'',maxMsg:'Máximo 10 min',get:c=>c.ser.restSets/15,set:(c,n)=>{c.ser.restSets=n*15},typed:b=>{const v=parseInt(b,10);return isNaN(v)?NaN:Math.round((Math.floor(v/100)*60+v%100)/15)},mmss:true},
 bdur:{title:'Duración del bloque',min:1,max:120,px:8,major:12,tick:n=>ceM(n*15),chips:[2,4,8,12,20],chipL:n=>ceM(n*15),
  big:n=>ceSecBig(n*15),hint:()=>'',maxMsg:'Máximo 30 min',get:(c)=>c.bl[c.bsel].s/15,set:(c,n)=>{c.bl[c.bsel].s=n*15},typed:b=>{const v=parseInt(b,10);return isNaN(v)?NaN:Math.round((Math.floor(v/100)*60+v%100)/15)},mmss:true},
 km:{title:'Distancia',min:0,max:500,px:8,major:10,tick:n=>n/10,chips:[10,30,50,100],chipL:n=>ceKmT(n/10)+' km',
  big:n=>n===0?['—','sin objetivo de distancia']:[ceKmT((n/10).toFixed(1)),'km'],hint:()=>'',maxMsg:'Máximo 50 km',none:{n:0,l:'Sin objetivo'},
  get:c=>Math.round((c.km||0)*10),set:(c,n)=>{c.km=n?n/10:null},typed:b=>Math.round(parseFloat(String(b).replace(',','.'))*10),kb:true},
 speed:{title:'Velocidad',min:30,max:250,px:8,major:10,tick:n=>n/10,chips:[60,80,100,120,140],chipL:n=>ceKmT(n/10)+' km/h',
  big:n=>[ceKmT((n/10).toFixed(1)),'km/h'],hint:()=>'',maxMsg:'Entre 3 y 25 km/h',get:c=>Math.round(ceV(c.bl[c.bsel])*10),set:(c,n)=>{c.bl[c.bsel].v=n/10},typed:b=>Math.round(parseFloat(String(b).replace(',','.'))*10),kb:true},
 incl:{title:'Inclinación',min:0,max:200,px:6,major:10,tick:n=>n/10,chips:[0,10,20,50,80],chipL:n=>ceKmT(n/10)+' %',
  big:n=>[ceKmT((n/10).toFixed(1)),'%'],hint:()=>'',maxMsg:'Máximo 20 %',get:c=>Math.round((c.bl[c.bsel].i||1)*10),set:(c,n)=>{c.bl[c.bsel].i=n/10},typed:b=>Math.round(parseFloat(String(b).replace(',','.'))*10),kb:true},
 rpm:{title:'Cadencia',min:40,max:130,px:9,major:10,tick:n=>n,chips:[60,70,80,90,100],chipL:n=>n+' rpm',
  big:n=>[String(n),'rpm'],hint:()=>'',maxMsg:'Entre 40 y 130 rpm',get:c=>c.bl[c.bsel].rpm||80,set:(c,n)=>{c.bl[c.bsel].rpm=n},typed:b=>parseInt(b,10)},
 watts:{title:'Vatios',min:0,max:100,px:9,major:10,tick:n=>n*5,chips:[24,32,40,50],chipL:n=>(n*5)+' W',
  big:n=>[String(n*5),'W'],hint:()=>'',maxMsg:'Máximo 500 W',get:c=>Math.round((c.bl[c.bsel].w||120)/5),set:(c,n)=>{c.bl[c.bsel].w=n*5},typed:b=>Math.round(parseInt(b,10)/5)},
 pace:{title:'Ritmo objetivo',min:60,max:540,px:3,major:30,tick:n=>ceM(n),chips:[300,330,360,390,420],chipL:n=>ceM(n)+' /km',
  big:n=>[ceM(n),'/km'],hint:()=>'',maxMsg:'Entre 1:00 y 9:00 /km',none:{n:0,l:'Sin objetivo'},get:c=>c.pace||360,set:(c,n)=>{c.pace=n||null},typed:b=>{const v=parseInt(b,10);return isNaN(v)?NaN:Math.floor(v/100)*60+v%100},mmss:true},
};
const ceSelBig=(cfg,n)=>{const [b,u]=cfg.big(n);return `<span class="ce-bigw"><span class="ce-big pl-num${/^[A-Za-zÁ—]/.test(b)&&b.length>1?' txt':''}">${b}</span>${u?`<span class="ce-bigu">${u}</span>`:''}</span>`};
function ceRulerHtml(cfg,n){
 const len=cfg.max-cfg.min;let t='';
 for(let i=0;i<=len;i++){const v=cfg.min+i,M=v%cfg.major===0;t+=`<i class="ce-tk${M?' M':''}" style="left:${i*cfg.px}px"></i>`;if(M)t+=`<em class="ce-tl pl-num" style="left:${i*cfg.px}px">${cfg.tick(v)}</em>`}
 return `<div class="ce-ruler" data-px="${cfg.px}" data-min="${cfg.min}" data-max="${cfg.max}" role="slider" aria-label="${cfg.title}"><div class="ce-rt" style="transform:translateX(${-(n-cfg.min)*cfg.px}px)">${t}</div><b class="ce-needle"></b></div>`}
function ceSelSheet(st,c){
 const s=st.sel,cfg=CE_CFG[s.w],n=s.n;
 const sub={dur:ceT(c.k).name,work:ceT(c.k).name+' · cada esfuerzo',rest:ceT(c.k).name+' · entre esfuerzos',warm:ceT(c.k).name,cool:ceT(c.k).name,restSets:ceT(c.k).name,bdur:ceT(c.k).name+' · bloque '+(c.bsel+1),km:ceT(c.k).name,pace:ceT(c.k).name,speed:'Bloque '+(c.bsel+1),incl:'Bloque '+(c.bsel+1),rpm:'Bloque '+(c.bsel+1),watts:'Bloque '+(c.bsel+1)}[s.w]||'';
 const over=s.typing&&s.err;
 const mode=cfg.modes?cfg.mode(c):'v';
 const modeSeg=cfg.modes?`<div class="ce-seg ce-seg3">${[['v','Con duración'],['free','Libre'],['off','Omitir']].map(m=>`<button class="${(s.mode||mode)===m[0]?'on':''}" data-a="selmode" data-m="${m[0]}">${m[1]}</button>`).join('')}</div>`:'';
 const off=cfg.modes&&(s.mode||mode)!=='v';
 let read;
 if(s.typing){const unit=cfg.mmss?'':cfg.big(Math.max(cfg.min,Math.min(cfg.max,isNaN(n)?cfg.min:n)))[1];const bufShow=s.buf===''?'':(cfg.mmss?ceBufTime(s.buf):s.buf);
  read=`<div class="ce-read typing${over?' err':''}"><span class="ce-bigw"><span class="ce-big pl-num">${bufShow||'0'}<i class="ce-caret"></i></span><span class="ce-bigu">${cfg.mmss?'min':unit}</span></span></div>`}
 else read=`<div class="ce-read"><button class="ce-rb" data-a="selminus" aria-label="Menos" ${off?'disabled':''}>${ic('ceMinus',20)}</button><button class="ce-readv${off?' off':''}" data-a="seltype" aria-label="Escribir el valor">${off?`<span class="ce-bigw"><span class="ce-big txt">${(s.mode||mode)==='free'?'Libre':'Omitido'}</span></span>`:ceSelBig(cfg,n)}</button><button class="ce-rb" data-a="selplus" aria-label="Más" ${off?'disabled':''}>${ic('plus',20)}</button></div>`;
 const hint=over?`<p class="ce-hint err">${cfg.maxMsg}</p>`:`<p class="ce-hint">${off?((s.mode||mode)==='free'?'Sin tiempo fijo: lo cierras tú en el entreno.':'No habrá '+cfg.title.toLowerCase()+'.'):s.typing?'Escribe el valor y toca Listo.':(cfg.hint(n)||'Arrastra la regla, usa − y + o toca la cifra para escribir.')}</p>`;
 const chips=`<div class="ce-chips">${(cfg.none?[cfg.none.n]:[]).concat(cfg.chips).map(v=>`<button class="ce-chip${!off&&v===n?' on':''}" data-a="selchip" data-n="${v}">${v===(cfg.none&&cfg.none.n)&&cfg.none?cfg.none.l:cfg.chipL(v)}</button>`).join('')}</div>`;
 const apply=`<div class="ce-acts"><button class="pl-btn ghost" data-a="selcancel">Cancelar</button><button class="pl-btn" data-a="selapply" ${over?'disabled':''}>Aplicar</button></div>`;
 return `<div class="pl-scrim" data-a="selcancel"></div><div class="pl-sheet ce-sel${s.typing?' kb':''}"><div class="ce-sh-h"><h2 class="pl-h2">${cfg.title}</h2><span class="ce-sh-s">${sub}</span></div>${modeSeg}${read}${hint}${s.typing?'':`<div class="${off?'ce-off':''}">${ceRulerHtml(cfg,n)}${chips}</div>`}${s.typing?'':apply}${s.typing?ceKeypad(cfg,over):''}</div>`}
const ceBufTime=b=>{const d=b.replace(/^0+/,'');if(d.length<=2)return d||'0';return d.slice(0,-2)+':'+d.slice(-2)};
function ceKeypad(cfg,over){
 const keys=['1','2','3','4','5','6','7','8','9',cfg.kb?',':'','0','bs'];
 return `<div class="ce-kb" aria-hidden="true">${keys.map(k=>k===''?'<span></span>':`<button data-a="selkey" data-k="${k}" class="${k==='bs'?'f':''}">${k==='bs'?ic('ceBs',22):k}</button>`).join('')}<button class="ce-kb-ok" data-a="selapply" ${over?'disabled':''} style="grid-column:1/-1">Listo</button></div>`}

/* ---------- Selector de zona de pulso ---------- */
function ceZoneSheet(st,c){
 const z=st.zsel;
 return `<div class="pl-scrim" data-a="zcancel"></div><div class="pl-sheet ce-zs"><div class="ce-sh-h"><h2 class="pl-h2">Zona de pulso</h2><span class="ce-sh-s">${ceT(c.k).name}</span></div><p class="ce-hint" style="margin-top:-2px">Tu pulso objetivo, sobre tu frecuencia máxima. El cardio en vivo te avisa si te sales.</p>
 <div class="ce-zl">${[[0,'Sin objetivo','']].concat([1,2,3,4,5].map(i=>[i,'Z'+i+' · '+CE_ZN[i-1],['< 60 %','60–70 %','70–80 %','80–90 %','> 90 %'][i-1]])).map(r=>`<button class="ce-zr${z===r[0]?' on':''}" data-a="zpick" data-z="${r[0]}"><i class="ce-zd" style="background:${r[0]?CE_ZC[r[0]-1]:'#ffffff26'}"></i><span>${r[1]}</span><em class="pl-num">${r[2]}</em><b class="ce-ck">${z===r[0]?ic('check',18):''}</b></button>`).join('')}</div>
 <div class="ce-acts"><button class="pl-btn ghost" data-a="zcancel">Cancelar</button><button class="pl-btn" data-a="zapply">Aplicar</button></div></div>`}

/* ---------- Protocolo y avisos ---------- */
const ceStepper=(v,a,min,max)=>`<span class="ce-step"><button data-a="${a}" data-d="-1" aria-label="Menos" ${v<=min?'disabled':''}>${ic('ceMinus',18)}</button><b class="pl-num">${v}</b><button data-a="${a}" data-d="1" aria-label="Más" ${v>=max?'disabled':''}>${ic('plus',18)}</button></span>`;
function ceProtoSheet(st,c){
 const s=c.ser;
 return `<div class="pl-scrim" data-a="closeL"></div><div class="pl-sheet ce-proto"><div class="ce-sh-h"><h2 class="pl-h2">Protocolo</h2><span class="ce-sh-s">${ceT(c.k).name} · ${ceDose(c)}</span></div>
 <div class="ce-fld"><span class="pl-eyebrow">Pausa entre esfuerzos</span><div class="ce-seg"><button class="${s.nature==='A'?'on':''}" data-a="nature" data-v="A">Activa</button><button class="${s.nature==='P'?'on':''}" data-a="nature" data-v="P">Pasiva</button></div><p class="ce-hint left">${s.nature==='A'?'Sigues moviéndote suave. La voz dice «Descanso activo, muévete suave».':'Paras del todo. La voz dice «Descanso, alto total».'}</p></div>
 <div class="ce-rows"><div class="ce-r"><span class="ce-rl">Series<small>Repites todo el bloque de rondas</small></span>${ceStepper(s.sets,'sets',1,5)}</div>${s.sets>1?`<button class="ce-r" data-a="sel" data-w="restSets"><span class="ce-rl">Descanso entre series</span><span class="ce-val pl-num">${ceM(s.restSets)}</span></button>`:''}</div>
 <div class="ce-fld"><span class="pl-eyebrow">Cada esfuerzo termina por</span><div class="ce-seg"><button class="${s.target==='t'?'on':''}" data-a="target" data-v="t">Tiempo</button><button class="${s.target==='k'?'on':''}" data-a="target" data-v="k">kcal</button><button class="${s.target==='d'?'on':''}" data-a="target" data-v="d">Distancia</button></div>
 ${s.target==='t'?'<p class="ce-hint left">Por defecto, el tiempo de esfuerzo de la tarjeta.</p>':`<div class="ce-rows" style="margin-top:8px"><button class="ce-r" data-a="tval"><span class="ce-rl">${s.target==='k'?'kcal por esfuerzo':'Metros por esfuerzo'}</span><span class="ce-val pl-num">${s.tval||(s.target==='k'?12:200)}${s.target==='k'?' kcal':' m'}</span></button></div><p class="ce-hint left">Cada esfuerzo termina al llegar a la cifra, no al acabar el tiempo.</p>`}</div>
 <div class="ce-acts"><button class="pl-btn" data-a="closeL" style="flex:1">Listo</button></div></div>`}
function ceAvisosSheet(st,c){
 const ph=c.form>0,ln=(ico,t,sub,on,a,dis)=>`<button class="ce-r ce-sr${dis?' dis':''}" data-a="${dis?'':a}" ${dis?'disabled':''}><span class="ce-ri">${ic(ico,20)}</span><span class="ce-rl">${t}<small>${sub}</small></span>${ceSw(on&&!dis)}</button>`;
 return `<div class="pl-scrim" data-a="closeL"></div><div class="pl-sheet ce-av"><div class="ce-sh-h"><h2 class="pl-h2">Avisos y pantalla</h2><span class="ce-sh-s">${ceT(c.k).name}</span></div>
 <div class="ce-fld"><span class="pl-eyebrow">Avisos de fase</span>
 <div class="ce-rows">${ln('ceBeep','Sonidos','Pitidos al cambiar de fase y cuenta atrás de 3 segundos.',c.beeps,'av-beeps',!ph)}${ln('ceVoice','Voz','Dice la fase, la duración y la consigna del bloque.',c.voice,'av-voice',!ph)}${ln('ceVib','Vibración','Doble vibración al empezar cada esfuerzo.',c.vib,'av-vib',!ph)}</div>
 ${ph?'<p class="ce-hint left">Dependen también de lo que tengas activado en Ajustes.</p>':'<p class="ce-hint left">Los avisos de fase son del cardio por series o por intervalos: en Continuo no hay cambios de fase que avisar.</p>'}</div>
 <div class="ce-fld"><span class="pl-eyebrow">Pantalla</span><div class="ce-rows">${ln('ceSun','Pantalla encendida','No se apaga mientras haces este cardio.',c.screen,'av-screen',false)}</div></div>
 <div class="ce-acts"><button class="pl-btn" data-a="closeL" style="flex:1">Listo</button></div></div>`}
const ceAvSum=c=>{const a=[];if(c.form>0){if(c.beeps)a.push('Sonidos');if(c.voice)a.push('Voz');if(c.vib)a.push('Vibración')}if(c.screen)a.push('Pantalla encendida');return a.length?a.join(' · '):'Todo apagado'};

/* ---------- Plantillas y patrones ---------- */
function ceTplSheet(st,c){
 const cur=st.tplCur||'';
 return `<div class="pl-scrim" data-a="closeL"></div><div class="pl-sheet ce-tpl"><div class="ce-sh-h"><h2 class="pl-h2">Plantillas</h2><span class="ce-sh-s">Elige una base y ajústala después.</span></div><div class="ce-tpl-s">
 <span class="pl-eyebrow">Plantillas</span><div class="ce-tl2">${CE_TPL.map(t=>`<button class="ce-tc${cur===t.id?' on':''}" data-a="tplpick" data-id="${t.id}"><span class="ce-tc-c">${t.bl?ceChart(t.bl,{h:34,cls:'sp'}):'<div class="ce-flat"></div>'}</span><span class="ce-tc-t"><b>${t.n}</b><small class="pl-num">${t.dose}</small></span>${cur===t.id?ic('check',18):''}</button>`).join('')}</div>
 <span class="pl-eyebrow" style="margin-top:6px">Patrones de intervalo</span><div class="ce-tl2">${CE_PAT.map((p,i)=>`<button class="ce-tc" data-a="patpick" data-i="${i}"><span class="ce-tc-c">${ceChart(cePatBl(p),{h:34,cls:'sp'})}</span><span class="ce-tc-t"><b>${p.n}</b><small>${p.d}</small></span></button>`).join('')}</div></div></div>`}

/* ---------- Hoja «Añadir cardio» / «Cambiar ejercicio de cardio» ---------- */
function ceAddSheet(st,replacing){
 const cur=replacing?st.cards[st.mi]&&st.cards[st.mi].k:null;
 return `<div class="pl-scrim" data-a="closeL"></div><div class="pl-sheet ce-add"><div class="ce-add-h"><span class="pl-eyebrow" style="color:var(--col)">CARDIO</span><h2 class="pl-h2">${replacing?'Cambiar ejercicio de cardio':'Añadir cardio'}</h2><p>${replacing?'Selecciona la nueva modalidad.':'Elige la máquina o el lugar. Después eliges cómo programarlo.'}</p></div>
 <div class="ce-add-s">${CE_G.map(g=>`<div class="ce-grp"><span class="pl-eyebrow">${g[1]}</span><i class="ce-rule"></i></div><div class="ce-tiles">${CE_T.filter(t=>t.g===g[0]).map(t=>`<button class="ce-tile${cur===t.k?' cur':''}" data-a="${replacing?'cpick':'pick'}" data-k="${t.k}"><span class="ce-win">${ceArt(t.k,{size:84})}${t.gps?`<span class="ce-gpsb">${ic('ceGps',12)}GPS en vivo</span>`:''}<span class="ce-badge">${cur===t.k?ic('check',14):ic('plus',14)}</span></span><b>${t.name}</b><small>${t.desc}</small></button>`).join('')}</div>`).join('')}</div></div>`}

/* ---------- La tarjeta de cardio, abierta ---------- */
const ceLinkRow=(ico,t,sub,a)=>`<button class="ce-r ce-lk" data-a="${a}"><span class="ce-ri">${ic(ico,20)}</span><span class="ce-rl">${t}<small>${sub}</small></span>${ic('fwd',16)}</button>`;
const ceValRow=(label,val,w,sub)=>`<button class="ce-r" data-a="sel" data-w="${w}"><span class="ce-rl">${label}${sub?`<small>${sub}</small>`:''}</span><span class="ce-val pl-num">${val}</span></button>`;
function ceRpe(c,r){
 const z=ceZone(r);
 return `<div class="ce-rpe"><div class="ce-rpe-h"><span class="pl-eyebrow">Esfuerzo (RPE)</span><span class="ce-rpe-v"><b class="pl-num">${r}</b> ${ceAnchor(r)}</span></div>
 <div class="ce-meter" data-a="rpe" role="slider" aria-label="RPE" aria-valuemin="1" aria-valuemax="10" aria-valuenow="${r}">${[1,2,3,4,5,6,7,8,9,10].map(i=>`<i data-r="${i}" class="${i<=r?'on':''}" style="height:${14+i*2.4}px;${i<=r?`background:${CE_ZC[ceZone(i)-1]}`:''}"></i>`).join('')}</div>
 <div class="ce-rpe-z"><i style="background:${CE_ZC[z-1]}"></i>Zona ${z} · ${CE_ZN[z-1]}</div></div>`}
function ceContinuo(c,t){
 const [dv,du]=ceCell(c.dur);let h='';
 h+=`<div class="ce-cells"><button class="ce-cell${c.km?' dim':''}" data-a="sel" data-w="dur"><small>Duración</small><b class="pl-num">${dv}${du?`<em>${du}</em>`:''}</b></button>`+(t.dist?`<button class="ce-cell" data-a="sel" data-w="km"><small>Distancia</small><b class="pl-num">${c.km?ceKmT(c.km)+'<em>km</em>':'—'}</b></button>`:'')+`</div>`;
 if(c.km)h+=`<p class="ce-hint left" style="margin-top:-6px">Con distancia, manda la distancia: el tiempo no se lee.</p>`;
 if(t.gps)h+=`<button class="ce-r ce-lk nb" data-a="gps"><span class="ce-ri">${ic('ceGps',20)}</span><span class="ce-rl">Seguir con GPS<small>${c.gps?'Distancia y ritmo en vivo.':'Sin GPS: distancia estimada.'}</small></span>${ceSw(c.gps)}</button>`;
 h+=ceRpe(c,c.rpe);
 h+=`<div class="ce-fld"><span class="pl-eyebrow">Objetivos <i>opcional</i></span><div class="ce-rows">${t.pace?ceValRow('Ritmo',c.pace?ceM(c.pace)+' /km':'—','pace'):''}<button class="ce-r" data-a="zone"><span class="ce-rl">Zona de pulso</span><span class="ce-val pl-num">${c.zone?`<i class="ce-zd" style="background:${CE_ZC[c.zone-1]}"></i>Z${c.zone}`:'—'}</span></button></div></div>`;
 h+=`<div class="ce-rows">${ceLinkRow('ceSun','Avisos y pantalla',ceAvSum(c),'avisos')}</div>`;
 return h}
function ceSeriesUi(c,t){
 const s=c.ser,bl=ceSeriesBlocks(s),tot=ceTot(bl);let h='';
 h+=`<div class="ce-sum">${ceChart(bl,{h:60})}<p class="pl-num"><b>${ceDur(tot)}</b> en total · ${s.rounds*s.sets} ${s.rounds*s.sets===1?'esfuerzo':'esfuerzos'}</p></div>`;
 const mv=(m,v)=>m==='free'?'Libre':m==='off'||!v?'Omitir':ceCellTxt(v);
 h+=`<div class="ce-rows">${ceValRow('Calentamiento',mv(s.warmMode,s.warm),'warm')}${ceValRow('Esfuerzo',ceM(s.work),'work')}${ceValRow('Pausa',s.rest?ceM(s.rest):'Sin pausa','rest')}<div class="ce-r"><span class="ce-rl">Rondas</span>${ceStepper(s.rounds,'rounds',1,99)}</div>${ceValRow('Enfriamiento',mv(s.coolMode,s.cool),'cool')}</div>`;
 h+=ceRpe(c,s.rpe);
 h+=`<div class="ce-rows">${ceLinkRow('sliders','Protocolo',`${s.sets} ${s.sets===1?'serie':'series'} · pausa ${s.nature==='A'?'activa':'pasiva'}${s.target!=='t'?' · por '+(s.target==='k'?'kcal':'metros'):''}`,'proto')}${ceLinkRow('ceSun','Avisos y pantalla',ceAvSum(c),'avisos')}</div>`;
 return h}
function ceInterUi(c,t){
 const bl=ceBlocks(c),i=Math.min(c.bsel,bl.length-1),b=bl[i],tot=ceTot(bl)*c.rounds;let h='';
 h+=`<div class="ce-sum">${ceChart(bl,{h:72,sel:i,pick:true})}<p class="pl-num"><b>${ceDur(tot)}</b> en total · ${bl.length} bloques${c.rounds>1?' × '+c.rounds+' rondas':''}</p><p class="ce-hint left" style="margin:2px 0 0">Toca una barra para editarla. Altura = intensidad, ancho = duración.</p></div>`;
 h+=`<div class="ce-rows"><div class="ce-r"><span class="ce-rl">Rondas del circuito<small>${c.rounds>1?'Se repite '+c.rounds+' veces':'1 vuelta completa'}</small></span>${ceStepper(c.rounds,'rounds2',1,99)}</div></div>`;
 const tn={A:'Calentamiento',W:'Esfuerzo',R:'Pausa',C:'Enfriamiento'};
 h+=`<div class="ce-insp"><div class="ce-insp-h"><b>${tn[b.t]}</b><span class="pl-num">${i+1}/${bl.length}</span><span class="sp"></span>${ceIb('ceBack','bprev','Bloque anterior',i===0?'disabled':'')}${ceIb('fwd','bnext','Bloque siguiente',i===bl.length-1?'disabled':'')}${ceIb('more','bmenu','Más acciones del bloque')}</div>
 <div class="ce-seg ce-seg4">${['A','W','R','C'].map(x=>`<button class="${b.t===x?'on':''}" data-a="btype" data-v="${x}">${tn[x]}</button>`).join('')}</div>
 <div class="ce-bdur"><button class="ce-cell" data-a="sel" data-w="bdur"><small>Duración</small><b class="pl-num">${ceCellH(b.s)}</b></button><button class="ce-pill" data-a="bdurd" data-d="-15">−15 s</button><button class="ce-pill f" data-a="bdurd" data-d="15">+15 s</button></div>
 <div class="ce-cells g2">${t.speed?`<button class="ce-cell" data-a="sel" data-w="speed"><small>Velocidad</small><b class="pl-num">${ceKmT(ceV(b))}<em>km/h</em></b></button>`:''}${t.incl?`<button class="ce-cell" data-a="sel" data-w="incl"><small>Inclinación</small><b class="pl-num">${ceKmT(b.i||1)}<em>%</em></b></button>`:''}${t.rpm?`<button class="ce-cell" data-a="sel" data-w="rpm"><small>Cadencia</small><b class="pl-num">${b.rpm||80}<em>rpm</em></b></button>`:''}${t.watts?`<button class="ce-cell" data-a="sel" data-w="watts"><small>Vatios</small><b class="pl-num">${b.w||120}<em>W</em></b></button>`:''}
 <button class="ce-cell" data-a="brpe"><small>RPE</small><b class="pl-num"><i class="ce-zd" style="background:${CE_ZC[ceZone(b.l)-1]}"></i>${b.l}<em>${ceAnchor(b.l)}</em></b></button><button class="ce-cell" data-a="bzone"><small>Pulso</small><b class="pl-num">${b.z?`<i class="ce-zd" style="background:${CE_ZC[b.z-1]}"></i>Z${b.z}`:'—'}</b></button></div></div>`;
 h+=`<button class="ce-link pad" data-a="badd">${ic('plus',16)}Añadir bloque</button>`;
 h+=`<div class="ce-rows">${ceLinkRow('ceSun','Avisos y pantalla',ceAvSum(c),'avisos')}</div>`;
 return h}
function ceHist(c){
 const h=c.hist;if(!h)return '';
 return `<div class="ce-hist"><span class="pl-eyebrow">Historial</span><p class="pl-num"><b>Última vez</b> · ${h.last}</p><p class="pl-num">Récords · ${h.pr}</p><div class="ce-hist-a"><button class="ce-pill" data-a="useLast">Usar última</button><button class="ce-pill" data-a="progress">Progresar</button></div></div>`}
function ceBody(st,c,i){
 const t=ceT(c.k);
 let h=`<div class="ce-bh"><span class="pl-eyebrow">Forma</span><button class="ce-link" data-a="tpl">Plantillas</button></div><div class="ce-seg">${['Continuo','Series','Intervalos'].map((l,j)=>`<button class="${c.form===j?'on':''}" data-a="form" data-j="${j}">${l}</button>`).join('')}</div>`;
 h+=c.form===0?ceContinuo(c,t):c.form===1?ceSeriesUi(c,t):ceInterUi(c,t);
 h+=ceHist(c);
 return `<div class="ce-bd">${h}</div>`}

/* ---------- La lista del editor con sus dos espacios ---------- */
const CE_STR=[['Peso Muerto Convencional','3 × 5 · RPE 8 · 3:00'],['Hip Thrust · Barra','3 × 5 · RPE 8 · 3:00']];
function ceStrSec(st){
 return `<div class="ce-sec"><span>FUERZA</span><i class="ce-rule"></i></div><div class="ce-mod">${CE_STR.map(r=>`<div class="ce-item"><div class="ce-row"><span class="ce-grip">${ic('grip',20)}</span><div class="ce-rowmain"><span class="ce-thumb">${ic('dumb',26)}</span><span class="ce-rt2"><b>${r[0]}</b><small class="pl-num">${r[1]}</small></span></div><span class="ce-star">${ic('star',20)}</span><span class="ce-chev">${ic('down',20)}</span></div></div>`).join('')}</div><button class="ce-add-t">${ic('plus',16)}Añadir ejercicio</button>`}
function ceRow(st,c,i){
 const t=ceT(c.k),open=st.open===i;
 return `<div class="ce-item${open?' open':''}${st.fresh===i?' fresh':''}" data-i="${i}"><div class="ce-row"><span class="ce-grip">${ic('grip',20)}</span><button class="ce-rowmain" data-a="toggle" data-i="${i}" data-lp="1">${ceThumb(c.k)}<span class="ce-rt2"><b>${t.name}</b><small class="pl-num">${ceDose(c)}</small>${!open&&c.form>0?ceChart(ceBlocks(c),{h:22,cls:'sp'}):''}</span></button><button class="ce-chev" data-a="toggle" data-i="${i}" aria-label="${open?'Plegar':'Desplegar'}">${ic(open?'up':'down',20)}</button></div>${open?ceBody(st,c,i):''}</div>`}
function ceCardioSec(st){
 const both=st.strength;
 const head=`<div class="ce-sec ce-sec-c"><span>CARDIO${both?` · ${st.first?'AL INICIO':'AL FINAL'}`:''}</span><i class="ce-rule"></i>${both?ceIb(st.first?'ceDown':'ceUp','move',st.first?'Pasar el cardio después de la fuerza':'Pasar el cardio antes de la fuerza'):''}${ceIb('plus','add','Añadir cardio')}</div>`;
 const body=st.cards.length?st.cards.map((c,i)=>ceRow(st,c,i)).join(''):`<div class="ce-empty"><span class="ce-empty-a">${ceArt('TREADMILL',{size:72})}</span><b>Todavía sin cardio</b><p>Elige una máquina o un lugar y después decides cómo programarlo.</p><button class="pl-btn" data-a="add">${ic('plus',16)}Elegir cardio</button></div>`;
 return head+`<div class="ce-mod${st.cards.length?'':' empty'}">${body}</div>`}
function ceListView(st){
 const c=ceCardioSec(st),s=ceStrSec(st);
 const inner=st.strength?(st.first?c+s:s+c):c;
 return `<div class="scroll ce-sc">${inner}</div>${ceTop()}${ceBottom(ceMin(st))}`}

/* ---------- Menú de pulsación larga de un cardio ---------- */
function ceMenuSheet(st){
 const c=st.cards[st.mi],t=ceT(c.k),both=st.strength;
 const frm=['Continuo','Series','Intervalos'][c.form];
 const row=(ico,t2,sub,a,cls)=>`<button class="ce-mr ${cls||''}" data-a="${a}"><span class="ce-ri">${ic(ico,20)}</span><span class="ce-rl">${t2}${sub?`<small>${sub}</small>`:''}</span></button>`;
 return `<div class="pl-scrim" data-a="closeL"></div><div class="pl-sheet ce-menu"><div class="ce-mh">${ceThumb(c.k,60)}<span class="ce-rl"><b>${t.name}</b><small class="pl-num">${ceDose(c)}</small></span></div>
 <div class="ce-tags"><span>Cardio</span><span>${t.gps?'Exterior · GPS':'Interior'}</span><span>${frm}</span></div>
 <div class="ce-mrs">${row('ceGuide','Ficha del cardio','Qué es, cómo medir el esfuerzo y formatos.','mficha')}${row('swap','Cambiar máquina o lugar','Mantiene tu programación.','mchange')}${row('copy','Duplicar','La copia queda justo debajo.','mdup')}${both?row(st.first?'ceDown':'ceUp',st.first?'Pasar el cardio después de la fuerza':'Pasar el cardio antes de la fuerza',st.first?'Ahora lo haces al inicio de la sesión.':'Ahora lo haces al final de la sesión.','move2'):''}</div>
 <button class="ce-del" data-a="mdel">${ic('trash',20)}Eliminar</button></div>`}
function ceBMenu(st){
 const c=st.cards[st.open];const n=ceBlocks(c).length,i=c.bsel;
 const tn={A:'Calentamiento',W:'Esfuerzo',R:'Pausa',C:'Enfriamiento'};
 const r=(ico,t,sub,a,dis,cls)=>`<button class="ce-mr ${cls||''}" data-a="${a}" ${dis?'disabled':''}><span class="ce-ri">${ic(ico,20)}</span><span class="ce-rl">${t}${sub?`<small>${sub}</small>`:''}</span></button>`;
 return `<div class="pl-scrim" data-a="closeL"></div><div class="pl-sheet ce-menu"><div class="ce-sh-h"><h2 class="pl-h2">Bloque ${i+1} · ${tn[ceBlocks(c)[i].t]}</h2><span class="ce-sh-s">${ceT(c.k).name}</span></div><div class="ce-mrs">${r('copy','Duplicar bloque','Queda justo después.','bdup',false)}${r('ceUp','Mover antes','',"bup",i===0)}${r('ceDown','Mover después','','bdown',i===n-1)}${r('trash','Quitar bloque',n<=1?'Tiene que quedar al menos uno.':'','bdel',n<=1,'danger')}</div></div>`}
const ceUndo=st=>st.undo?`<div class="ce-undo pl-flat"><span>Ejercicio eliminado</span><button data-a="undo">Deshacer</button></div>`:'';

/* ---------- Fichas propias del cardio (contenido nuevo: `new`) ---------- */
// Un texto breve por modalidad real. Los formatos son plantillas que ya existen en la app (CardioFeaturedTemplates).
const CE_GUIDE={
 TREADMILL:{que:'Banda motorizada para correr o caminar con la velocidad y la inclinación que elijas, sin clima ni tráfico. Es la forma más fácil de mantener un ritmo exacto.',
  ent:['Resistencia aeróbica','Piernas','Glúteos con inclinación'],
  esf:'En la cinta manda el ritmo. RPE 5–6 es el paso que te deja hablar a trompicones; RPE 8 es un ritmo que sostienes unos 20 minutos.',
  fm:['z2','11','pyr'],
  tec:['Empieza caminando 2–3 minutos y sube la velocidad poco a poco.','Mira al frente, no a los pies. Hombros sueltos y pasos cortos bajo la cadera.','Con inclinación alta no te agarres a las barras: pierdes trabajo y falseas el gasto.','Baja la velocidad antes de bajarte; no pares la banda en seco.']},
 ELLIPTICAL:{que:'Pedales deslizantes que mueven piernas y brazos sin impacto. Buena para sumar volumen aeróbico cuando rodillas o caderas piden descanso.',
  ent:['Resistencia aeróbica','Piernas','Glúteos','Brazos y espalda'],
  esf:'Manda la resistencia y la cadencia, no la velocidad. RPE 6 es conversación entrecortada; para llegar a RPE 8 sube la resistencia, no solo el ritmo.',
  fm:['z2','3030','11'],
  tec:['Postura erguida: no cargues el peso del cuerpo en las asas.','Empuja con los talones y mantén los pies planos en los pedales.','Pedalea hacia atrás unos minutos para variar el trabajo de glúteo e isquios.','Mira al frente; evita encorvarte sobre la consola.']},
 ROW_MACHINE:{que:'Ergómetro de remo: la fuerza sale de piernas, tronco y brazos en una palada continua. Es de los cardios que más músculo reparte.',
  ent:['Resistencia aeróbica','Piernas','Espalda','Core'],
  esf:'El monitor da el ritmo por 500 m; aquí guíate por el RPE. RPE 6 es una palada larga y controlada; RPE 9 son series cortas con toda la potencia.',
  fm:['z2','3030','pyr'],
  tec:['Primero empujan las piernas, luego se abre el tronco y al final tiran los brazos. Al volver, al revés.','Espalda larga, sin redondear; el tirón termina a la altura del esternón.','Freno (damper) entre 3 y 5: más no es más rápido, solo más pesado.','Agarre relajado y muñecas neutras.']},
 BIKE_STATIONARY:{que:'Bicicleta fija con resistencia regulable y pedaleo de bajo impacto. Ideal para base aeróbica y para intervalos controlados.',
  ent:['Resistencia aeróbica','Cuádriceps','Glúteos'],
  esf:'Combina cadencia (80–100 rpm) y resistencia. RPE 5–6 es el ritmo de base; en los esfuerzos fuertes sube la resistencia sin bajar de unas 70 rpm.',
  fm:['z2','tabata','wing'],
  tec:['Sillín a la altura de la cadera: con el pedal abajo, la rodilla casi extendida.','Espalda larga y manos relajadas en el manillar.','Si te duelen las rodillas, sube el sillín o baja la resistencia.','Pedalea redondo: empuja y tira, sin golpes.']},
 STAIR_CLIMBER:{que:'Escaleras sin fin: subes peldaño a peldaño al ritmo que eliges. Cardio exigente para piernas y glúteos con poco impacto.',
  ent:['Resistencia aeróbica','Glúteos','Cuádriceps','Gemelos'],
  esf:'El pulso sube rápido. RPE 6 con peldaños completos es un buen ritmo de base; los intervalos cortos de RPE 8–9 se notan enseguida en los glúteos.',
  fm:['z2','11','pyr'],
  tec:['Apoya el pie entero en el peldaño, no solo la punta.','Torso erguido, con una ligera inclinación. No te cuelgues de las barras.','Mejor peldaños completos que muy rápidos y cortos.','Empieza con 5 minutos suaves: el pulso sube más de lo que parece.']},
 SKI_ERG:{que:'Ergómetro de esquí de fondo: tiras de dos asas hacia abajo con brazos, tronco y cadera. Reparte el esfuerzo por todo el tren superior y el core.',
  ent:['Resistencia aeróbica','Dorsales','Core','Hombros'],
  esf:'Mide metros y vatios. RPE 6 son tirones fluidos y respirables; RPE 9, bloques de 30–60 segundos con toda la potencia.',
  fm:['3030','tabata','z2'],
  tec:['Empieza arriba con los brazos extendidos; al tirar, flexiona cadera y rodillas como una bisagra, no solo los brazos.','Core firme y espalda larga; no te redondees al final del tirón.','Deja que el cordón vuelva solo: aprovecha la subida para soltar.','Agarre suave: la fuerza viene del cuerpo, no de los dedos.']},
 RUN_OUTDOOR:{que:'Correr al aire libre con distancia y ritmo medidos por GPS. El terreno, el clima y los desniveles añaden variedad (y esfuerzo).',
  ent:['Resistencia aeróbica','Piernas','Core'],
  esf:'El ritmo cambia con la cuesta y el viento: guíate por el RPE y por tu zona de pulso, no solo por el reloj. RPE 5 es poder hablar en frases.',
  fm:['z2','11','pyr'],
  tec:['Pasos cortos y ligeros, aterrizando bajo tu centro de gravedad.','Los primeros 5–10 minutos, suaves; sube el ritmo poco a poco.','Lleva el móvil con el GPS activo, calzado adecuado y buena visibilidad.','En las cuestas acorta el paso y mantén el esfuerzo, no la velocidad.']},
 BIKE_OUTDOOR:{que:'Ciclismo en carretera o camino con distancia y ritmo registrados por GPS. Permite tiradas largas de base aeróbica.',
  ent:['Resistencia aeróbica','Cuádriceps','Glúteos','Gemelos'],
  esf:'El viento y las cuestas mandan: usa el RPE y la zona de pulso. RPE 5–6 es el ritmo de fondo; los tramos de RPE 8, solo en subidas o bloques cortos.',
  fm:['z2','pyr','11'],
  tec:['Casco siempre, y luces si hay poca luz.','Cadencia cómoda (80–95 rpm): cambia de marcha antes de la cuesta.','Codos flexibles y cuello relajado.','Respeta el tráfico y no uses el móvil mientras pedaleas.']},
 WALK:{que:'Caminata continua o por intervalos con GPS. La forma más amable de sumar actividad: sirve de base, de recuperación activa o de cardio principal.',
  ent:['Resistencia aeróbica','Piernas','Glúteos'],
  esf:'RPE 3–4 es conversar sin problema. Subir cuestas o acelerar el paso lleva a RPE 5–6; si puedes cantar, vas muy suave.',
  fm:['z2','11','pyr'],
  tec:['Postura alta, mirada al frente y paso natural.','Brazos sueltos en vaivén y hombros relajados.','Calzado cómodo, y agua si pasas de 45 minutos.','Alterna paso rápido y suave para trabajar más sin cansarte de golpe.']},
 AIR_BIKE:{que:'Bici de brazos y piernas con un ventilador: cuanto más fuerte empujas, más resistencia. Muy buena para sprints cortos y esfuerzos máximos.',
  ent:['Todo el cuerpo','Resistencia','Potencia'],
  esf:'No hay niveles: la resistencia la pones tú. RPE 9–10 durante 10–30 segundos es un sprint de verdad; recupera pedaleando suave.',
  fm:['tabata','msit','wing'],
  tec:['Empieza cada esfuerzo con una arrancada fuerte y mantén la cadencia.','Empuja y tira con los brazos a la vez que pedaleas.','Espalda erguida y agarre firme; no te encorves al fatigarte.','Tras un sprint no pares en seco: sigue pedaleando suave.']},
 CURVED_TREADMILL:{que:'Cinta curva y autopropulsada: la banda se mueve con tus pasos, sin motor. Marcas tú el ritmo, ideal para sprints y para trabajar la técnica de carrera.',
  ent:['Resistencia','Potencia','Piernas','Core'],
  esf:'No hay velocidad fijada: cuanto más empujas, más rápido va. RPE 8–9 en tiradas cortas ya es muy intenso; no lo compares con una cinta motorizada.',
  fm:['3030','tabata','msit'],
  tec:['Apoya el antepié y empuja la banda hacia atrás, con el torso ligeramente inclinado.','Brazos activos: marcan el ritmo.','Entra caminando y acelera; sal igual, nunca de golpe.','Si el pie resbala, acorta el paso y baja la inclinación del torso.']},
 SLED:{que:'Trineo cargado que empujas o arrastras sobre una distancia corta. Trabajo de piernas y potencia con muy poco impacto articular.',
  ent:['Piernas','Glúteos','Core','Potencia'],
  esf:'Se mide por distancia, no por tiempo: tramos de 10–30 m. RPE 8–9 es empujar fuerte esos metros y descansar entre tramos.',
  fm:['msit','3030','tabata'],
  tec:['Torso inclinado hacia delante y brazos extendidos; empuja con las piernas.','Pasos cortos y potentes, sin arrastrar los pies.','Empieza con poco peso y sube cuando la técnica sea limpia.','Descansa lo suficiente entre tramos: manda la calidad.']},
};
const ceTplBy=id=>CE_TPL.find(t=>t.id===id);
const ceFmtMini=t=>t.bl?ceChart(t.bl,{h:30,cls:'sp'}):'<div class="ce-flat"></div>';
function ceFichaView(st,c){
 const t=ceT(c.k),g=CE_GUIDE[c.k];
 return `<div class="scroll ce-fi"><div class="ce-fi-hero"><span class="ce-fi-art">${ceArt(c.k,{size:168})}</span></div>
 <div class="ce-fi-t"><h1 class="pl-h1">${t.name}</h1><div class="ce-tags"><span>Cardio</span><span>${t.gps?'Exterior · GPS':'Interior'}</span></div></div>
 <button class="ce-prog" data-a="progress2"><span class="ce-ri">${ic('ceChart',20)}</span><span class="ce-rl">Ver progreso<small>${c.hist?'Última vez · '+c.hist.last:'Aún sin sesiones de este cardio.'}</small></span>${ic('fwd',16)}</button>
 <section><h2 class="pl-h2">Qué es</h2><p>${g.que}</p></section>
 <section><h2 class="pl-h2">Qué entrena</h2><div class="ce-chips ro">${g.ent.map(e=>`<span class="ce-chip">${e}</span>`).join('')}</div></section>
 <section><h2 class="pl-h2">Cómo medir el esfuerzo</h2><div class="ce-zones">${[1,2,3,4,5].map(z=>`<div class="ce-zone"><i style="background:${CE_ZC[z-1]}"></i><b class="pl-num">Z${z}</b><span>${CE_ZN[z-1]}</span><em class="pl-num">RPE ${z===5?'9–10':(z*2-1)+'–'+(z*2)}</em></div>`).join('')}</div><p>${g.esf}</p></section>
 <section><h2 class="pl-h2">Formatos recomendados</h2><div class="ce-fms">${g.fm.map(id=>{const f=ceTplBy(id);return `<div class="ce-fm">${ceFmtMini(f)}<b>${f.n}</b><small class="pl-num">${f.dose}</small></div>`}).join('')}</div></section>
 <section><h2 class="pl-h2">Técnica y seguridad</h2><ul class="ce-tec">${g.tec.map(x=>`<li>${x}</li>`).join('')}</ul></section></div>
 <button class="ce-x pl-glass" data-a="fclose" aria-label="Cerrar">${ic('close',20)}</button>`}

/* ---------- Motor de la maqueta: una sola aplicación, cada escena es un estado inicial ---------- */
function ceNew(o){
 const st=Object.assign({strength:true,first:false,cards:[],open:null,layer:null,page:null,mi:0,sel:null,zsel:null,toast:'',undo:null,fresh:null,tplCur:''},o||{});
 return st}
function ceView(st){
 let layer='';
 const c=st.cards[st.open!=null?st.open:st.mi];
 if(st.layer==='add')layer=ceAddSheet(st,false);
 else if(st.layer==='change')layer=ceAddSheet(st,true);
 else if(st.layer==='sel')layer=ceSelSheet(st,c);
 else if(st.layer==='zone')layer=ceZoneSheet(st,c);
 else if(st.layer==='proto')layer=ceProtoSheet(st,c);
 else if(st.layer==='avisos')layer=ceAvisosSheet(st,c);
 else if(st.layer==='tpl')layer=ceTplSheet(st,c);
 else if(st.layer==='menu')layer=ceMenuSheet(st);
 else if(st.layer==='bmenu')layer=ceBMenu(st);
 const page=st.page==='ficha'?ceFichaView(st,st.cards[st.mi]):ceListView(st);
 return `<div class="ce-root">${page}${layer}${ceUndo(st)}${ceToast(st.layer?'':st.toast)}</div>`}
const ceHtml=o=>()=>ceView(ceNew(typeof o==='function'?o():o));
function ceCloneSt(o){return typeof o==='function'?o():o}

/* Interacción: delegación de eventos sobre la raíz de la escena. */
function ceRun(ph,mk){
 const st=ceNew(mk());let root=ph.querySelector('.ce-root');if(!root)return;
 let tm=0,lp=0,timer=0;
 const draw=()=>{const sc=root.querySelector('.scroll'),y=sc?sc.scrollTop:0;const ay=root.querySelector('.ce-add-s,.ce-tpl-s');const ay0=ay?ay.scrollTop:0;
  root.outerHTML=ceView(st);root=ph.querySelector('.ce-root');const sc2=root.querySelector('.scroll');if(sc2)sc2.scrollTop=y;const a2=root.querySelector('.ce-add-s,.ce-tpl-s');if(a2)a2.scrollTop=ay0;bind()};
 const say=t=>{st.toast=t;clearTimeout(tm);tm=setTimeout(()=>{st.toast='';draw()},2400)};
 const cur=()=>st.cards[st.open!=null?st.open:st.mi];
 function openSel(w){const c=cur(),cfg=CE_CFG[w];if(w.startsWith('b')||['speed','incl','rpm','watts'].includes(w))ceBlocks(c);st.sel={w,n:cfg.get(c),n0:cfg.get(c),typing:false,buf:'',err:false,mode:null};st.layer='sel'}
 function clampN(cfg,n){return Math.max(cfg.min,Math.min(cfg.max,n))}
 function applySel(){const c=cur(),s=st.sel,cfg=CE_CFG[s.w];
  if(cfg.modes){const m=s.mode||cfg.mode(c);cfg.setMode(c,m);if(m==='v')cfg.set(c,clampN(cfg,s.n));if(m==='v'&&cfg.get(c)===0){cfg.setMode(c,'off')}}
  else cfg.set(c,clampN(cfg,s.n));
  if(s.w==='dur'||s.w==='km'){if(s.w==='dur'&&c.dur===0)c.dur=null}
  st.layer=null;st.sel=null}
 const A={
  toggle:e=>{const i=+e.dataset.i;st.open=st.open===i?null:i;st.mi=i},
  move:()=>{st.first=!st.first;say(st.first?'El cardio se hace antes de la fuerza.':'El cardio se hace después de la fuerza.')},
  move2:()=>{st.first=!st.first;st.layer=null;say(st.first?'El cardio se hace antes de la fuerza.':'El cardio se hace después de la fuerza.')},
  add:()=>{st.layer='add'},closeL:()=>{st.layer=null},
  pick:e=>{const k=e.dataset.k;st.cards.push(ceCard(k,{rpe:7,dur:1200}));st.layer=null;st.fresh=st.cards.length-1;st.open=null;st.mi=st.fresh;say(ceT(k).name+' añadido al cardio.')},
  cpick:e=>{const k=e.dataset.k,c=st.cards[st.mi];c.k=k;if(!ceT(k).dist)c.km=null;c.gps=ceT(k).gps===1;st.layer=null;say('Ahora es '+ceT(k).name+'. Tu programación se mantiene.')},
  form:e=>{const c=cur(),j=+e.dataset.j;if(j===c.form)return;
   if(j===0){const t=ceTot(ceBlocks(c))*(c.form===2?c.rounds:1);c.dur=t>0?t:1200;c.rpe=c.form===1?c.ser.rpe:c.rpe}
   else if(j===1){c.ser=ceSeries()}
   else{const p=CE_PAT[2],tot=Math.max(1200,c.form===0?(c.dur||1200):ceTot(ceSeriesBlocks(c.ser))),w=p.w.reduce((a,b)=>a+b,0);c.bl=p.u.map((l,i)=>ceB('W',Math.round(p.w[i]*tot/w/15)*15,l*2));c.bsel=0;c.rounds=1}
   c.form=j},
  rpe:()=>{},
  sel:e=>{openSel(e.dataset.w)},
  selcancel:()=>{st.layer=null;st.sel=null},
  selapply:()=>{if(st.sel.err)return;if(st.sel.typing){const cfg=CE_CFG[st.sel.w];const v=cfg.typed(st.sel.buf);if(!isNaN(v)&&v>=cfg.min&&v<=cfg.max)st.sel.n=v}applySel()},
  selminus:()=>{const s=st.sel,cfg=CE_CFG[s.w];s.n=clampN(cfg,s.n-1)},selplus:()=>{const s=st.sel,cfg=CE_CFG[s.w];s.n=clampN(cfg,s.n+1)},
  selchip:e=>{st.sel.n=+e.dataset.n;st.sel.mode='v'},
  selmode:e=>{st.sel.mode=e.dataset.m;if(e.dataset.m==='v'&&st.sel.n===0)st.sel.n=CE_CFG[st.sel.w].chips[0]},
  seltype:()=>{st.sel.typing=true;st.sel.buf='';st.sel.err=false},
  selkey:e=>{const s=st.sel,cfg=CE_CFG[s.w],k=e.dataset.k;
   if(k==='bs')s.buf=s.buf.slice(0,-1);else if(k===','){if(!s.buf.includes(','))s.buf+=(s.buf===''?'0':'')+','}else if(s.buf.length<(cfg.mmss?4:cfg.kb?4:3))s.buf+=k;
   const v=s.buf===''?NaN:cfg.typed(s.buf);s.err=!isNaN(v)&&(v>cfg.max||v<cfg.min);if(!isNaN(v)&&!s.err)s.n=v},
  gps:()=>{const c=cur();c.gps=!c.gps},
  zone:()=>{st.zsel=cur().zone||0;st.layer='zone'},zpick:e=>{st.zsel=+e.dataset.z},zcancel:()=>{st.layer=null},zapply:()=>{const c=cur();if(st.zblock){c.bl[c.bsel].z=st.zsel||null;st.zblock=false}else c.zone=st.zsel||null;st.layer=null},
  bzone:()=>{const c=cur();ceBlocks(c);st.zsel=c.bl[c.bsel].z||0;st.zblock=true;st.layer='zone'},
  proto:()=>{st.layer='proto'},avisos:()=>{st.layer='avisos'},tpl:()=>{st.layer='tpl'},
  'av-beeps':()=>{cur().beeps=!cur().beeps},'av-voice':()=>{cur().voice=!cur().voice},'av-vib':()=>{cur().vib=!cur().vib},'av-screen':()=>{cur().screen=!cur().screen},
  sets:e=>{const s=cur().ser;s.sets=Math.max(1,Math.min(5,s.sets+(+e.dataset.d)))},rounds:e=>{const s=cur().ser;s.rounds=Math.max(1,Math.min(99,s.rounds+(+e.dataset.d)))},
  rounds2:e=>{const c=cur();c.rounds=Math.max(1,Math.min(99,c.rounds+(+e.dataset.d)))},
  proto2:()=>{},nature:e=>{cur().ser.nature=e.dataset.v},target:e=>{const s=cur().ser;s.target=e.dataset.v;if(s.target==='t')s.tval=null},
  tval:()=>{const s=cur().ser;s.tval=(s.tval||(s.target==='k'?12:200))+(s.target==='k'?2:50);if(s.tval>(s.target==='k'?80:2000))s.tval=s.target==='k'?4:50},
  tplpick:e=>{const c=cur(),id=e.dataset.id;st.tplCur=id;
   if(id==='z2'){c.form=0;c.dur=1800;c.rpe=5;c.ser=ceSeries()}
   else{const t=ceTplBy(id);if(['pyr'].includes(id)){c.form=2;c.bl=t.bl.map(b=>Object.assign({},b));c.bsel=0;c.rounds=1}
    else{const w=t.bl.find(b=>b.t==='W'),r=t.bl.find(b=>b.t==='R'),n=t.bl.filter(b=>b.t==='W').length,a=t.bl.find(b=>b.t==='A'),cc=t.bl.find(b=>b.t==='C');c.form=1;c.ser=ceSeries({warm:a?a.s:0,work:w.s,rest:r.s,rounds:n,cool:cc?cc.s:0,rpe:id==='tabata'||id==='msit'||id==='wing'?10:9,proto:id==='msit'||id==='wing'?'SIT':'HIIT',warmMode:a?'v':'off',coolMode:cc?'v':'off'})}}
   st.layer=null;say('Plantilla aplicada: '+(ceTplBy(id).n)+'.')},
  patpick:e=>{const c=cur(),p=CE_PAT[+e.dataset.i];c.form=2;c.bl=cePatBl(p).map(b=>Object.assign({},b,{s:Math.max(15,Math.round(b.s/15)*15)}));c.bsel=0;c.rounds=1;st.layer=null;say('Patrón aplicado: '+p.n+'.')},
  bsel:e=>{cur().bsel=+e.dataset.i},bprev:()=>{const c=cur();c.bsel=Math.max(0,c.bsel-1)},bnext:()=>{const c=cur();c.bsel=Math.min(ceBlocks(c).length-1,c.bsel+1)},
  btype:e=>{const c=cur();ceBlocks(c)[c.bsel].t=e.dataset.v},
  bdurd:e=>{const c=cur(),b=ceBlocks(c)[c.bsel];b.s=Math.max(15,b.s+(+e.dataset.d))},
  brpe:()=>{const c=cur(),b=ceBlocks(c)[c.bsel];b.l=b.l>=10?1:b.l+1},
  badd:()=>{const c=cur(),bl=ceBlocks(c);bl.push(ceB('W',60,7));c.bsel=bl.length-1},
  bmenu:()=>{st.layer='bmenu'},
  bdup:()=>{const c=cur(),bl=ceBlocks(c);bl.splice(c.bsel+1,0,Object.assign({},bl[c.bsel]));c.bsel++;st.layer=null;say('Bloque duplicado.')},
  bup:()=>{const c=cur(),bl=ceBlocks(c),i=c.bsel;if(i>0){[bl[i-1],bl[i]]=[bl[i],bl[i-1]];c.bsel--}st.layer=null},
  bdown:()=>{const c=cur(),bl=ceBlocks(c),i=c.bsel;if(i<bl.length-1){[bl[i+1],bl[i]]=[bl[i],bl[i+1]];c.bsel++}st.layer=null},
  bdel:()=>{const c=cur(),bl=ceBlocks(c);bl.splice(c.bsel,1);c.bsel=Math.min(c.bsel,bl.length-1);st.layer=null;say('Bloque quitado.')},
  useLast:()=>{const c=cur();if(c.hist&&c.hist.use)c.hist.use(c);say('Aplicado lo de la última vez.')},progress:()=>{const c=cur();c.dur=(c.dur||1200)+300;say('Progresión aplicada: +5 min.')},
  menu:()=>{},mficha:()=>{st.layer=null;st.page='ficha'},fclose:()=>{st.page=null},progress2:()=>{say('Abre el progreso de este cardio.')},
  mchange:()=>{st.layer='change'},mdup:()=>{const c=st.cards[st.mi];st.cards.splice(st.mi+1,0,JSON.parse(JSON.stringify(c)));st.layer=null;st.open=null;st.fresh=st.mi+1;say('Cardio duplicado.')},
  mdel:()=>{st.undo={c:st.cards.splice(st.mi,1)[0],i:st.mi};st.layer=null;st.open=null;clearTimeout(tm);tm=setTimeout(()=>{st.undo=null;draw()},4200)},
  undo:()=>{if(st.undo){st.cards.splice(st.undo.i,0,st.undo.c);st.undo=null}},
 };
 function bind(){
  root=ph.querySelector('.ce-root');
  root.onclick=ev=>{const el=ev.target.closest('[data-a]');if(!el||el.disabled)return;if(Date.now()-lp<350)return;const f=A[el.dataset.a];if(!f)return;f(el);draw()};
  root.oncontextmenu=ev=>{const el=ev.target.closest('[data-lp]');if(el){ev.preventDefault();st.mi=+el.dataset.i;st.layer='menu';draw()}};
  root.onpointerdown=ev=>{
   const lpEl=ev.target.closest('[data-lp]');if(lpEl){clearTimeout(timer);timer=setTimeout(()=>{lp=Date.now();st.mi=+lpEl.dataset.i;st.layer='menu';draw()},520)}
   const m=ev.target.closest('.ce-meter');if(m){const set=e2=>{const r=m.getBoundingClientRect(),z=ph.getBoundingClientRect().width/ph.offsetWidth||1;const v=Math.max(1,Math.min(10,Math.ceil((e2.clientX-r.left)/(r.width)*10)));const c=cur();if(c.form===1)c.ser.rpe=v;else c.rpe=v;
     m.querySelectorAll('i').forEach((b,i)=>{b.className=i<v?'on':'';b.style.background=i<v?CE_ZC[ceZone(i+1)-1]:''});m.closest('.ce-rpe').querySelector('.ce-rpe-v').innerHTML=`<b class="pl-num">${v}</b> ${ceAnchor(v)}`;m.closest('.ce-rpe').querySelector('.ce-rpe-z').innerHTML=`<i style="background:${CE_ZC[ceZone(v)-1]}"></i>Zona ${ceZone(v)} · ${CE_ZN[ceZone(v)-1]}`};
    set(ev);try{m.setPointerCapture(ev.pointerId)}catch(e){}const mv=e2=>set(e2),up=()=>{m.removeEventListener('pointermove',mv);m.removeEventListener('pointerup',up);draw()};m.addEventListener('pointermove',mv);m.addEventListener('pointerup',up)}
   const rl=ev.target.closest('.ce-ruler');if(rl&&st.sel){const cfg=CE_CFG[st.sel.w],px=cfg.px,rt=rl.querySelector('.ce-rt'),x0=ev.clientX,n0=st.sel.n;let raw=n0;try{rl.setPointerCapture(ev.pointerId)}catch(e){}rt.style.transition='none';
    const rd=ph.querySelector('.ce-read .ce-readv');
    const mv=e2=>{const sc=ph.getBoundingClientRect().width/ph.offsetWidth||1;raw=Math.max(cfg.min,Math.min(cfg.max,n0-(e2.clientX-x0)/sc/px));rt.style.transform=`translateX(${-(raw-cfg.min)*px}px)`;const n=Math.round(raw);if(n!==st.sel.n){st.sel.n=n;st.sel.mode='v';if(rd)rd.innerHTML=ceSelBig(cfg,n)}};
    const up=()=>{rl.removeEventListener('pointermove',mv);rl.removeEventListener('pointerup',up);rt.style.transition='';draw()};rl.addEventListener('pointermove',mv);rl.addEventListener('pointerup',up)}
  };
  root.onpointerup=()=>clearTimeout(timer);root.onpointerleave=()=>clearTimeout(timer);
 }
 root=ph.querySelector('.ce-root');bind();
}
const ceScene=(def,mk)=>scene(Object.assign({tab:'cardio-editor',html:ceHtml(mk),after:ph=>ceRun(ph,mk)},def));

/* ================= ESCENAS ================= */
const ceHist1=()=>({last:'18 min · 3,1 km · 5:48/km · RPE 7',pr:'Ritmo 5:12/km · 6,4 km · 45 min',use:c=>{c.dur=1080}});
const ceThree=()=>[ceCard('TREADMILL',{rpe:8,dur:1200,hist:ceHist1()}),ceCard('STAIR_CLIMBER',{form:1,ser:ceSeries({warm:300,work:60,rest:60,rounds:8,cool:180,rpe:9})}),ceCard('ROW_MACHINE',{form:2,bl:CE_IRR(),rpe:7})];
const ceWith=(o,cards)=>()=>Object.assign({cards:(cards||ceThree)()},o);
const ceSelAt=(w,cards,extra)=>()=>{const st=Object.assign({cards:cards(),open:0,mi:0,layer:'sel'},extra&&extra.st||{});const c=st.cards[0],cfg=CE_CFG[w];if(['bdur','speed','incl','rpm','watts'].includes(w))ceBlocks(c);st.sel=Object.assign({w,n:cfg.get(c),n0:cfg.get(c),typing:false,buf:'',err:false,mode:null},extra&&extra.sel||{});return st};

ceScene({id:'ce-anadir',title:'Añadir cardio · las 12 modalidades',
 notes:{tags:[['new','miniaturas a medida'],['keep','mismas modalidades y grupos'],['move','«+» más discreto, GPS como etiqueta']],img:'h06-anadir-cardio',imgCap:'Hoy: iconos genéricos, textos cortados con «…» y un «+» en cada disco.',
  p:['La hoja enseña las 12 modalidades reales, agrupadas como hoy (en interior, al aire libre, sprint y potencia), pero cada una con su ilustración: la cinta con la persona corriendo, la elíptica con sus asas, el remo con el volante y la bancada, la bici fija, la escaladora con peldaños, el SkiErg con las dos asas, el camino, la bici de carretera, el sendero, el ventilador del Air Bike, la banda curva y el trineo.',
  'Dos columnas en vez de tres: la ilustración respira, el nombre cabe entero y la descripción se lee sin «…». El GPS pasa a una etiqueta sobre la ilustración (sale solo en las tres de exterior). Toca una para añadirla: la hoja se cierra y aparece en el espacio de cardio.'],
  list:['Cada modalidad cabe en una tarjeta de 2 columnas; con el texto al 130 % la tarjeta crece en alto, nada se corta.','El «+» sigue marcando que tocar la tarjeta la añade; es pequeño y no compite con la ilustración.','Misma hoja gris plana que el resto (Ref02), sin vidrio.'],listTitle:'Detalle'}},
 ceWith({layer:'add',open:null}));

ceScene({id:'ce-cambiar',title:'Cambiar ejercicio de cardio',
 notes:{tags:[['keep','misma hoja, título «Cambiar ejercicio de cardio»'],['new','marca de la modalidad actual']],
  p:['Se abre desde «Cambiar máquina o lugar» del menú de pulsación larga. Es la misma hoja con el texto «Selecciona la nueva modalidad.». La modalidad que ya tiene el ejercicio sale resaltada con una marca (propuesta mía: hoy la hoja no dice cuál es la actual).','Al elegir otra, el cardio conserva su programación (forma, tiempos, RPE); solo cambia la máquina, el nombre y la ilustración. Si la nueva no admite distancia, la distancia se quita; si el GPS no aplica, se desactiva: es lo que hace hoy `withCardioItem`.']}},
 ceWith({layer:'change',mi:1,open:null}));

scene({id:'ce-ilustraciones-ref',tab:'cardio-editor',title:'Las 12 ilustraciones, a tamaño real',
 html:()=>`<div class="scroll ce-gal">${CE_T.map(t=>`<div class="ce-gal-i">${ceArt(t.k,{size:96})}<span>${t.name}</span></div>`).join('')}</div>`,
 notes:{tags:[['new','SVG a medida'],['keep','trazo 2 en lienzo 64 (1,75 px a 56 px)']],
  p:['Todas comparten lienzo 64 × 64, trazo redondeado de 2 y las mismas proporciones de figura (cabeza r 3,4, tronco ≈ 14, muslo y pierna ≈ 9–10). Dos tintas: la máquina o el lugar en crema apagada y la persona en el azul de cardio. En Compose son dos listas de trazos (`machine`, `person`) por modalidad, con `StrokeCap.Round`.',
  'Se usan en la hoja de añadir (84 px), en las filas del editor (56 px), en el menú (60 px) y en la ficha (168 px). Para los iconos pequeños de otras pantallas (24 px) se mantiene el icono de línea actual.']}});

ceScene({id:'ce-lista',title:'El editor con fuerza y cardio',
 notes:{tags:[['new','sin «Añadir cardio» dentro del módulo'],['move','«Al inicio / Al final» pasa al rótulo CARDIO'],['out','selector grande «Cardio en la sesión»'],['out','chip «Al final» dentro de la tarjeta']],img:'h13-editor-lista',imgCap:'Hoy: un selector grande de «Al inicio / Al final» sobre la lista y «Añadir cardio» dentro del módulo.',
  p:['Cada espacio lleva su rótulo (FUERZA, CARDIO) y su módulo. El de cardio dice dónde está: «CARDIO · AL FINAL», y a su derecha tiene dos botones redondos y pequeños: una flecha que lo pasa antes (o después) de la fuerza y un «+» que abre «Añadir cardio». Sin botón gigante, sin texto «Añadir cardio» dentro del módulo.',
  'Mantén pulsada una fila de cardio para abrir su menú propio; toca la fila (o la flecha) para desplegarla. Prueba la flecha del rótulo: el espacio de cardio sube sobre la fuerza y un aviso dice qué significa («El cardio se hace antes de la fuerza»).'],
  list:['La función no se pierde: sigue habiendo un solo ajuste de orden, solo que vive donde se entiende (en el rótulo del espacio que mueve) y no ocupa una fila.','Con solo cardio no hay fuerza que ordenar: desaparece la flecha (ver «Solo cardio»).','Quien añade cardio lo hace desde el «+» del rótulo, desde el «Añadir» de la barra inferior (si el paquete del editor lo convierte en menú: Ejercicio · Cardio) o desde el menú de pulsación larga.'],listTitle:'Detalle'}},
 ceWith({}));

ceScene({id:'ce-solo',title:'Solo cardio y espacio vacío',
 notes:{tags:[['keep','mismo estado vacío de hoy'],['new','sin texto «Añadir cardio» dentro del módulo']],
  p:['Una sesión de solo cardio no tiene fuerza que ordenar: el rótulo dice solo «CARDIO», sin flecha de orden. Si elegiste cardio en la sesión vacía y aún no hay ninguno, el módulo no se queda en blanco con un texto suelto: dice qué falta y ofrece un botón «Elegir cardio» (este es el único sitio donde un botón de alta vive dentro del módulo, porque no hay ninguna tarjeta que lo contradiga).','Quita el cardio con «Eliminar» del menú y verás este estado; el aviso «Ejercicio eliminado · Deshacer» aparece abajo, como en fuerza.']}},
 ()=>({strength:false,cards:[]}));

const ceOpen=(k,form,extra)=>()=>Object.assign({cards:[ceCard(k,Object.assign({form},extra||{}))],open:0,mi:0},{});
ceScene({id:'ce-continuo',scrollTo:300,title:'Tarjeta abierta · Continuo',
 notes:{tags:[['new','tarjeta rediseñada'],['out','segundo título «18 min · RPE 8»'],['move','Avanzado repartido'],['keep','Forma · tiempo/distancia · RPE · GPS · ritmo · pulso']],img:'h07-cardio-tarjeta',imgCap:'Hoy: dosis repetida en grande, chip «Al final», plantillas, forma, series, RPE, Avanzado y «Añadir cardio».',
  p:['La cabecera de la fila ya dice la dosis (20 min · RPE 8): no se repite en grande dentro. Debajo, en este orden: la forma (Continuo · Series · Intervalos) con «Plantillas» a su lado, las dos magnitudes principales en celdas, el GPS si es de exterior, el esfuerzo (RPE) y los objetivos opcionales (ritmo, zona de pulso). Al final, un solo acceso a «Avisos y pantalla».',
  'El esfuerzo deja de ser un deslizador con puntitos: diez barras que crecen, coloreadas con las zonas reales del cardio en vivo (Recuperación, Base aeróbica, Tempo, Umbral, Máximo). Se arrastra el dedo por encima. Con distancia puesta, la tarjeta avisa de que manda la distancia (así lo calcula la dosis hoy).'],
  list:['Cambia de forma con el control segmentado: Series e Intervalos tienen su propia escena y los datos se conservan como hoy.','Ritmo solo aparece en cinta, cinta curva, carrera y caminata (en el resto no tiene sentido); el motor lo ignora en el cardio por series.','Toca «Duración» o «Distancia»: se abre el selector nuevo.'],listTitle:'Detalle'}},
 ()=>{const st=ceOpen('TREADMILL',0,{rpe:8,dur:1200,hist:ceHist1()})();return Object.assign(st,{cards:[st.cards[0],ceCard('STAIR_CLIMBER',{form:1}),ceCard('ROW_MACHINE',{form:2,bl:CE_IRR()})]})});

ceScene({id:'ce-continuo-gps',scrollTo:300,title:'Continuo · carrera exterior con GPS',
 notes:{tags:[['keep','celda GPS de exterior'],['new','interruptor con explicación']],
  p:['En las tres modalidades de exterior aparece «Seguir con GPS» (hoy una celda «GPS · Sí/No»). Es un interruptor con una línea que dice qué cambia: con GPS, distancia y ritmo en vivo; sin GPS, distancia estimada. En interior no aparece, igual que hoy.','Con distancia y ritmo objetivo puestos la dosis dice «5 km · 5:30/km», como hoy.']}},
 ()=>({cards:[ceCard('RUN_OUTDOOR',{form:0,dur:1800,km:5,pace:330,rpe:6,hist:{last:'28 min · 5 km · 5:36/km · RPE 6',pr:'Ritmo 4:58/km · 10 km · 58 min'}})],open:0,mi:0}));

ceScene({id:'ce-series',scrollTo:300,title:'Tarjeta abierta · Series',
 notes:{tags:[['new','cinco filas claras'],['move','chips Omitir/Libre → dentro del selector'],['move','series, pausa activa y objetivo → hoja «Protocolo»'],['out','chip HIIT/SIT, Ritmo/FC/vatios/velocidad/inclinación/RPM de Avanzado']],
  p:['La forma por series se lee de un vistazo: la gráfica de barras arriba (anchura = duración, altura = intensidad) con el total, y debajo cinco filas con su valor: Calentamiento, Esfuerzo, Pausa, Rondas (con − y +) y Enfriamiento. Cada valor abre el selector de tiempo; «Omitir» y «Libre» del calentamiento y el enfriamiento viven dentro de su selector, no como chips sueltos.','Lo que casi nadie toca (número de series, descanso entre series, pausa activa o pasiva, terminar el esfuerzo por kcal o metros) queda en una fila «Protocolo» con su resumen, que abre una hoja. «Avisos y pantalla» abre otra. La tarjeta pasa de más de quince controles a la vista a diez (más el historial, si lo hay).'],
  list:['Todo lo que había en Series sigue existiendo: nada se quita, se reparte.','La «Distancia» de la forma por series desaparece de esta forma: el cardio en vivo solo la usa en continuo (en series la ignora, salvo los hitos de GPS).','Pulsa los controles: las barras se recalculan.'],listTitle:'Detalle'}},
 ceOpen('STAIR_CLIMBER',1,{ser:ceSeries({warm:300,work:60,rest:60,rounds:8,cool:180,rpe:9}),hist:{last:'8 × 1:00 / 1:00 · RPE 9 · 24 min',pr:'Tiempo 30 min · 10 × 0:45 / 1:00'}}));

ceScene({id:'ce-intervalos',scrollTo:300,title:'Tarjeta abierta · Intervalos',
 notes:{tags:[['move','tres vistas del mismo dato → una sola gráfica tocable'],['keep','duplicar, mover y quitar bloque'],['new','magnitudes según la máquina']],
  p:['Hoy hay tres representaciones de los bloques (gráfica, «peldaños del circuito» y una fila por bloque) más el inspector. Aquí queda la gráfica tocable y el inspector del bloque elegido, que lleva ‹ › para recorrer los bloques y un «⋯» con Duplicar, Mover antes, Mover después y Quitar (las acciones de las filas, intactas).','El inspector muestra solo lo que esa máquina admite: la cinta, velocidad e inclinación; la elíptica, cadencia; el remo, cadencia y vatios; etc. RPE y pulso (zona) siempre. Las celdas de ritmo por bloque desaparecen en las máquinas con velocidad: cuando hay velocidad, el cardio en vivo muestra la velocidad y no el ritmo.'],
  list:['«Plantillas» abre la hoja con las 7 plantillas y los 7 patrones (pirámide, escalera, fartlek…).','Las barras estrechas se eligen por cercanía al tocar y con ‹ ›, así el tacto no depende del ancho de la barra.'],listTitle:'Detalle'}},
 ()=>({cards:[ceCard('TREADMILL',{form:2,bl:CE_IRR(),bsel:1})],open:0,mi:0}));

/* Selectores */
ceScene({id:'ce-duracion',title:'Selector de duración (minutos)',
 notes:{tags:[['new','sustituye a la rueda «Min»'],['keep','Libre, 0–180 min, Cancelar / Aplicar']],img:'h10-cardio-duracion',imgCap:'Hoy: una rueda de tres filas con «Min» y una cifra apagada.',
  p:['Una cifra grande y legible (tocar para escribir), una regla que se arrastra con el dedo (cada marca es un minuto, cada cinco lleva número), «−» y «+» para ajustar de uno en uno y atajos de 10, 15, 20, 30, 45 y 60 minutos. A partir de una hora la cifra pasa a h:mm. «Libre» (sin objetivo de tiempo) sigue existiendo como atajo.',
  'Es el mismo control para todo: tiempo de calentamiento, esfuerzo y pausa (en m:ss), distancia, velocidad, inclinación, cadencia y vatios; solo cambian la escala, los atajos y la unidad. Arrastra la regla, toca los atajos y los botones.'],
  list:['Rango: 0–180 min, como hoy.','Estados: normal, «Libre» (valor 0), escribiendo, error por valor fuera de rango y desactivado cuando el calentamiento es «Libre» u «Omitir».'],listTitle:'Estados'}},
 ceSelAt('dur',()=>[ceCard('STAIR_CLIMBER',{dur:1080,rpe:8}),ceCard('TREADMILL')]));

ceScene({id:'ce-duracion-teclado',title:'Selector · escribiendo con teclado',
 notes:{tags:[['new','entrada numérica'],['keep','mismos límites que la rueda']],
  p:['Tocar la cifra la convierte en un campo con teclado numérico (en la app, el teclado del sistema con `KeyboardType.Number`; aquí un teclado de maqueta para poder probar). Si te pasas del rango, la cifra se pone en el color de error, aparece «Máximo 3 h» y «Listo» se desactiva. En m:ss se teclea al estilo del microondas: 1, 3, 0 = 1:30.']}},
 ceSelAt('dur',()=>[ceCard('STAIR_CLIMBER',{dur:1080,rpe:8})],{sel:{typing:true,buf:'25',n:25}}));

ceScene({id:'ce-duracion-error',title:'Selector · valor fuera de rango',
 notes:{tags:[['new','estado de error']],p:['Escribir 250 minutos supera el máximo de 3 h: la cifra se pone en rojo suave, el texto de ayuda dice «Máximo 3 h» y «Listo» queda desactivado hasta corregirlo.']}},
 ceSelAt('dur',()=>[ceCard('STAIR_CLIMBER',{dur:1080,rpe:8})],{sel:{typing:true,buf:'250',n:25,err:true}}));

ceScene({id:'ce-esfuerzo',title:'Selector de tiempo de esfuerzo (m:ss)',
 notes:{tags:[['new','mismo control, escala de 5 s'],['keep','5 s – 10 min']],
  p:['Para Esfuerzo y Pausa la regla avanza de 5 en 5 segundos (cada marca larga es un minuto) y los atajos son los de las plantillas reales: 0:20, 0:30, 1:00, 2:00, 3:00. La pausa admite 0 («Sin pausa»).']}},
 ceSelAt('work',()=>[ceCard('STAIR_CLIMBER',{form:1,ser:ceSeries({warm:300,work:60,rest:60,rounds:8,cool:180,rpe:9})})],{sel:{}}));

ceScene({id:'ce-calentamiento',title:'Selector · calentamiento con Libre y Omitir',
 notes:{tags:[['move','Omitir y Libre: de chips sueltos a modos del selector'],['keep','mismas tres opciones']],
  p:['Hoy, bajo «Calentamiento» y «Enfriamiento» hay dos chips «Omitir» y «Libre» que se confunden con el valor. Aquí son tres modos arriba del selector: «Con duración», «Libre» (sin tiempo fijo, lo cierras tú) y «Omitir». Al elegir Libre u Omitir, la regla se apaga y la cifra lo dice.']}},
 ceSelAt('warm',()=>[ceCard('STAIR_CLIMBER',{form:1,ser:ceSeries({warm:300,work:60,rest:60,rounds:8,cool:180,rpe:9})})],{sel:{}}));

ceScene({id:'ce-calentamiento-libre',title:'Selector · calentamiento en modo Libre',
 notes:{tags:[['new','estado desactivado']],p:['En modo Libre u Omitir la regla y los atajos se apagan (desactivados, no ocultos) para que se vea que el valor no cuenta.']}},
 ceSelAt('warm',()=>[ceCard('STAIR_CLIMBER',{form:1,ser:ceSeries({warm:300,work:60,rest:60,rounds:8,cool:180,rpe:9})})],{sel:{mode:'free'}}));

ceScene({id:'ce-distancia',title:'Selector de distancia',
 notes:{tags:[['new','mismo control'],['keep','0–50 km con décimas']],
  p:['La distancia avanza de 100 en 100 metros (cada marca larga es un kilómetro) con atajos de 1, 3, 5 y 10 km (propuesta mía) y «Sin objetivo». Hoy son dos ruedas separadas (km y décimas).']}},
 ceSelAt('km',()=>[ceCard('RUN_OUTDOOR',{km:5,dur:1800,rpe:6})],{sel:{}}));

ceScene({id:'ce-zona',title:'Selector de zona de pulso',
 notes:{tags:[['new','zonas en vez de % FC'],['keep','mismo dato (targetHrPercent)']],
  p:['Hoy «FC» pide un porcentaje (50–100). El cardio en vivo ya piensa en cinco zonas con nombre y color, así que el selector las ofrece tal cual con su rango de % de tu frecuencia máxima. Se guarda el mismo dato (un porcentaje: el punto medio de la zona).']}},
 ()=>({cards:[ceCard('TREADMILL',{zone:2})],open:0,mi:0,layer:'zone',zsel:2}));

ceScene({id:'ce-protocolo',title:'Hoja «Protocolo»',
 notes:{tags:[['move','antes en Avanzado'],['keep','series · descanso · pausa activa · objetivo por kcal o m'],['out','chip HIIT/SIT (solo cambiaba el RPE por defecto)']],
  p:['Aquí viven los ajustes del cardio por series que el motor sí usa: pausa activa o pasiva (cambia lo que dice la voz y la intensidad de la pausa), número de series (1–5) con su descanso entre series y el objetivo con el que termina cada esfuerzo: tiempo, kcal o metros (corta el esfuerzo al llegar a la cifra). Desaparece el chip HIIT/SIT: el cardio en vivo no distingue uno de otro (dice «¡Sprint!» en los dos); solo servía para poner el RPE en 9 o en 10, y eso ya lo hace el control de esfuerzo.']}},
 ()=>({cards:[ceCard('STAIR_CLIMBER',{form:1,ser:ceSeries({warm:300,work:60,rest:60,rounds:8,cool:180,rpe:9,sets:2})})],open:0,mi:0,layer:'proto'}));

ceScene({id:'ce-avisos',title:'Hoja «Avisos y pantalla»',
 notes:{tags:[['move','antes chips Beeps · Voz · Vibra · Pantalla'],['keep','las cuatro opciones'],['new','una línea que explica cada una']],img:'h08-cardio-avanzado',imgCap:'Hoy: «Ritmo» y «FC» vacíos, tres chips apagados y un texto que los explica.',
  p:['Cada aviso con su icono, qué hace y su interruptor. Sonidos, voz y vibración los lee el cardio en vivo al cambiar de fase y en la cuenta atrás; «Pantalla encendida» evita que se apague mientras entrenas. Los avisos de fase solo existen en Series e Intervalos.']}},
 ()=>({cards:[ceCard('STAIR_CLIMBER',{form:1,ser:ceSeries({warm:300,work:60,rest:60,rounds:8,cool:180,rpe:9})})],open:0,mi:0,layer:'avisos'}));

ceScene({id:'ce-avisos-continuo',title:'Avisos y pantalla · en Continuo',
 notes:{tags:[['new','estado desactivado explicado']],p:['En Continuo no hay cambios de fase: las tres filas de avisos aparecen apagadas con la explicación («Los avisos de fase son del cardio por series o por intervalos»). La pantalla encendida sí vale para cualquier cardio.']}},
 ()=>({cards:[ceCard('TREADMILL')],open:0,mi:0,layer:'avisos'}));

ceScene({id:'ce-plantillas',title:'Hoja «Plantillas»',
 notes:{tags:[['move','antes carrusel dentro de la tarjeta'],['keep','las 7 plantillas'],['move','los 7 patrones de intervalo aquí también']],img:'h07-cardio-tarjeta',imgCap:'Hoy: un carrusel de plantillas cortado por el borde, dentro de la tarjeta.',
  p:['Las plantillas pasan de ser un carrusel cortado a una hoja con sus formas a la vista: Tabata, 30/30, 1\'/1\', Micro-SIT, Wingate, Pirámide y Z2 30 min, con la dosis que calcula la app. Debajo, los siete patrones del modo Intervalos (pirámide, pirámide inversa, escalera, 1:1, 2:1, fartlek, personalizado), que hoy están escondidos en la forma Intervalos. Elegir una aplica y cierra.']}},
 ()=>({cards:[ceCard('TREADMILL',{form:1})],open:0,mi:0,layer:'tpl',tplCur:'11'}));

/* Menú y ficha */
ceScene({id:'ce-menu',title:'Menú de pulsación larga de un cardio',
 notes:{tags:[['new','acciones propias del cardio'],['out','+ Aproximación'],['out','+ Movilidad'],['out','Crear superserie'],['move','Descripción → ficha']],img:'h09-cardio-longpress',imgCap:'Hoy: las opciones de un ejercicio de fuerza, con «1 × 8 · 2:00» de subtítulo.',
  p:['Cinco acciones que existen y funcionan en cardio: Ficha del cardio (la nueva ficha propia), Cambiar máquina o lugar (conserva la programación), Duplicar, Pasar el cardio antes o después de la fuerza (solo si la sesión tiene ambos espacios; es el mismo ajuste de orden del rótulo) y Eliminar (con «Deshacer»).',
  'Qué se va y por qué: «+ Aproximación» y «+ Movilidad» guardan series de calentamiento y movilidad que el cardio en vivo nunca reproduce, así que no hacían nada (el calentamiento del cardio es la fase «Calentamiento» de Series e Intervalos). «Crear superserie» no aplica: el cardio se hace en su propio espacio a pantalla completa, no encadenado con fuerza. La cabecera dice la dosis real del cardio, no «1 × 8 · 2:00».'],
  list:['La descripción de una línea pasa a la ficha: el menú queda sin scroll.','Mantén pulsada una fila de cardio en la maqueta para abrirlo (o clic derecho).'],listTitle:'Detalle'}},
 ()=>({cards:ceThree(),mi:1,layer:'menu'}));

ceScene({id:'ce-menu-eliminado',title:'Cardio eliminado · «Deshacer»',
 notes:{tags:[['keep','borrado blando con Deshacer, como en fuerza']],p:['«Eliminar» no pide confirmación: la fila desaparece y un aviso abajo ofrece «Deshacer» unos segundos, igual que al deslizar un ejercicio de fuerza.']}},
 ()=>{const cards=ceThree();const gone=cards.splice(1,1)[0];return {cards,undo:{c:gone,i:1}}});

ceScene({id:'ce-ficha',title:'Ficha del cardio · Escaladora',
 notes:{tags:[['new','ficha propia, con contenido nuevo'],['out','«Sin imagen revisada para esta configuración»']],img:'h11-cardio-ficha',imgCap:'Hoy: un título, «Sin imagen revisada…» y «Ver progreso». Nada más.',
  p:['La ficha tiene su espacio: la ilustración de la modalidad, qué es, qué entrena, cómo medir el esfuerzo (las cinco zonas reales con su RPE y una línea específica de la máquina), tres formatos recomendados (plantillas que ya existen, con su forma) y consejos de técnica y seguridad. «Ver progreso» sube arriba con su última sesión.',
  'Los textos de las 12 modalidades están en el documento «Textos de las 12 fichas». Son contenido nuevo (`new`): no existe nada parecido en la app.']}},
 ()=>({cards:[ceCard('STAIR_CLIMBER',{hist:ceHist1()})],mi:0,page:'ficha'}));

ceScene({id:'ce-ficha-exterior',title:'Ficha del cardio · Carrera exterior',
 notes:{tags:[['new','contenido'],['keep','mismo diseño en las 12']],p:['La misma ficha para una modalidad de exterior: cambian la ilustración, las etiquetas (Exterior · GPS), el texto y los formatos. Sin historial todavía, «Ver progreso» lo dice.']}},
 ()=>({cards:[ceCard('RUN_OUTDOOR')],mi:0,page:'ficha'}));

scene({id:'ce-fichas-textos',tab:'cardio-editor',title:'Textos de las 12 fichas',doc:()=>{
 const t=CE_T.map(t=>{const g=CE_GUIDE[t.k];return `<tr><td><b>${t.name}</b></td><td>${g.que}</td><td>${g.ent.join(', ')}</td><td>${g.esf}</td><td>${g.fm.map(id=>ceTplBy(id).n).join(', ')}</td><td>${g.tec.map(x=>'· '+x).join('<br>')}</td></tr>`}).join('');
 return `<h2>Contenido de las fichas del cardio (nuevo)</h2><p>Un texto breve por modalidad real. Todo es contenido nuevo; los formatos recomendados son plantillas que ya existen en la app. Revísalos: se implementarán tal cual.</p><div class="dtable-wrap"><table class="dtable"><thead><tr><th>Modalidad</th><th>Qué es</th><th>Qué entrena</th><th>Esfuerzo</th><th>Formatos</th><th>Técnica y seguridad</th></tr></thead><tbody>${t}</tbody></table></div>`}});

