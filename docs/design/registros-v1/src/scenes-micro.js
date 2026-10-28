/* ================= v4 · VITAMINAS Y MINERALES (Nutrición v2) ================= */
// Datos sintéticos de ejemplo. Los 24 nutrientes de la huella, en el orden de la hoja de la app (NutrientGroups.kt).
// ref = DRI de un hombre de 30 años (la app la toma del perfil del plan); el sexto campo marca un tope («del máximo»), no una meta.
const NU=[
 ['A','Vitamina A','µg',900,'v'],['B1','Tiamina','mg',1.2,'v'],['B2','Riboflavina','mg',1.3,'v'],['B3','Niacina','mg',16,'v'],['B6','Vitamina B6','mg',1.3,'v'],
 ['B9','Folato','µg',400,'v'],['B12','Vitamina B12','µg',2.4,'v'],['Co','Colina','mg',550,'v'],['C','Vitamina C','mg',90,'v'],['D','Vitamina D','µg',15,'v'],
 ['E','Vitamina E','mg',15,'v'],['K','Vitamina K','µg',120,'v'],
 ['Ca','Calcio','mg',1000,'m'],['P','Fósforo','mg',700,'m'],['Mg','Magnesio','mg',400,'m'],['Fe','Hierro','mg',8,'m'],['Zn','Zinc','mg',11,'m'],
 ['Se','Selenio','µg',55,'m'],['I','Yodo','µg',150,'m'],['Na','Sodio','mg',2300,'m',1],['K','Potasio','mg',3400,'m'],
 ['Fi','Fibra','g',38,'o'],['ω3','Omega-3 ALA','g',1.6,'o'],['Caf','Cafeína','mg',400,'o',1]];
const fmtN=x=>{if(x==null)return '—';const d=x<1?2:x<10?1:0;let [a,b]=x.toFixed(d).split('.');if(b)b=b.replace(/0+$/,'');a=a.replace(/\B(?=(\d{3})+(?!\d))/g,'.');return b?a+','+b:a};
const pctOf=(i,a)=>a==null?null:a/NU[i][3]*100;
// Vector por ración (null = sin dato; 0 = cero declarado). cnt = nutrientes con dato de los 38 de la app.
const NF={
 salmon:{n:'Salmón a la plancha',q:'1 filete',g:'150 g',src:'USDA · verificado',cnt:37,k:309,p:33,c:0,f:19,
  v:[87,.51,.21,12,.98,51,4.2,137,5.6,15,2.4,.8, 23,378,45,.5,.6,61.5,null,92,576, 0,.15,0]},
 yogur:{n:'Yogur griego natural',q:'1 envase',g:'125 g',src:'KPKN · verificado',cnt:35,k:146,p:11,c:5,f:9,
  v:[34,.03,.29,.26,.08,9,.94,19,.6,.1,.01,.3, 138,169,14,.1,.65,12,null,45,176, 0,.02,0]},
 barrita:{n:'Barrita de avena y chocolate',q:'1 barrita',g:'42 g',src:'Open Food Facts · etiqueta',cnt:8,k:191,p:3,c:29,f:7,
  v:[null,null,null,null,null,null,null,null,null,null,null,null, null,null,null,null,null,null,null,160,null, 2.1,null,null]},
 huevos:{n:'Huevos revueltos',q:'2 huevos',g:'110 g',src:'USDA',cnt:36,
  v:[176,.07,.5,.08,.15,52,1,323,0,2.2,1.2,.3, 62,218,13,1.9,1.4,33,null,156,152, 0,.05,0]},
 pan:{n:'Pan tostado',q:'2 rebanadas',g:'60 g',src:'KPKN',cnt:35,
  v:[0,.25,.14,2.6,.05,30,0,8,0,0,.1,.2, 90,60,15,2.2,.5,15,null,340,70, 1.6,.02,0]},
 aguacate:{n:'Aguacate',q:'½ pieza',g:'70 g',src:'USDA',cnt:36,
  v:[5,.05,.09,1.2,.18,57,0,10,7,0,1.45,14.7, 8,36,20,.4,.45,.3,null,5,341, 4.7,.08,0]},
 cafe:{n:'Café con leche',q:'1 taza',g:'240 ml',src:'estimado',cnt:30,est:true,
  v:[90,.08,.38,.5,.08,12,.9,35,2,.1,.05,.5, 240,190,30,.1,.9,5,30,100,380, 0,.01,64]},
};
const MEAL=['huevos','pan','aguacate','cafe'];
const foodRows=f=>f.v.map((a,i)=>({amt:a,pct:pctOf(i,a),est:a&&f.est?1:0,part:false}));
function mealRows(){return NU.map((_,i)=>{let d=0,e=0,miss=0;MEAL.forEach(k=>{const f=NF[k],a=f.v[i];if(a==null)miss++;else if(f.est)e+=a;else d+=a});
  const amt=miss<MEAL.length?d+e:null;return {amt,pct:pctOf(i,amt),est:amt?e/amt:0,part:miss>0&&amt!=null,miss}})}
// El día completo (2.240 kcal registradas): % de la referencia; part = algún alimento sin dato de ese nutriente.
const DAYP=[74,118,142,165,131,88,210,81,64,38,52,96, 92,189,88,142,118,196,34,118,68, 72,84,61];
const DAYPART={9:2,18:6};
const dayRows=()=>DAYP.map((p,i)=>({amt:p*NU[i][3]/100,pct:p,est:i===18?.4:i===12?.12:0,part:!!DAYPART[i],miss:DAYPART[i]||0}));

// La huella: una barra fina por nutriente hasta el 100 % de tu referencia; las tres que más aportan, en acento; los topes, en contorno;
// «sin dato», un trazo punteado a ras de suelo (nunca una barra de 0); la parte estimada, en contorno discontinuo.
function huella(rows,{hi=3,w=330,h=84,legend=true,day=false}={}){
  const gap=14,slot=(w-gap*2)/NU.length,bw=Math.max(4,+(slot*.5).toFixed(1)),top=20,base=h-(legend?18:4),H=base-top;
  const tops=rows.map((r,i)=>[r,i]).filter(([r,i])=>r.pct!=null&&!NU[i][5]).sort((a,b)=>b[0].pct-a[0].pct).slice(0,hi).map(x=>x[1]);
  const gx={v:0,m:gap,o:gap*2};
  let s=`<line x1="0" x2="${w}" y1="${top}" y2="${top}" stroke="#ffffff2e" stroke-dasharray="2 4"/><text x="${w}" y="${top-6}" text-anchor="end">100 %</text><line x1="0" x2="${w}" y1="${base+.5}" y2="${base+.5}" stroke="#ffffff14"/>`;
  rows.forEach((r,i)=>{const n=NU[i],x=i*slot+gx[n[4]]+(slot-bw)/2,cx=(x+bw/2).toFixed(1);
    if(r.pct==null){s+=`<line x1="${cx}" x2="${cx}" y1="${base-2}" y2="${base-14}" stroke="#ffffff38" stroke-width="1.5" stroke-dasharray="1.5 3" stroke-linecap="round"/>`;return}
    const p=Math.min(r.pct,100),bh=Math.max(2.5,p/100*H),y=base-bh,over=r.pct>100,lim=n[5],hot=tops.includes(i);
    const col=lim?(over?'var(--danger)':'#f2eee6'):hot?'var(--mus)':'#f2eee6',op=lim||hot?1:day?(r.pct>=100?.92:.42):.5,eh=bh*(r.est||0);
    s+=`<g class="hb" style="animation-delay:${i*24}ms">`;
    if(lim)s+=`<rect x="${(x+.6).toFixed(1)}" y="${(y+.6).toFixed(1)}" width="${(bw-1.2).toFixed(1)}" height="${(bh-1.2).toFixed(1)}" rx="${bw/2-.6}" fill="none" stroke="${col}" stroke-width="1.2"/>`;
    else{if(bh-eh>0)s+=`<rect x="${x.toFixed(1)}" y="${(y+eh).toFixed(1)}" width="${bw}" height="${(bh-eh).toFixed(1)}" rx="${Math.min(bw/2,(bh-eh)/2).toFixed(1)}" fill="${col}" opacity="${op}"/>`;
      if(eh>0)s+=`<rect x="${(x+.6).toFixed(1)}" y="${(y+.6).toFixed(1)}" width="${(bw-1.2).toFixed(1)}" height="${(eh-.6).toFixed(1)}" rx="${(bw/2-.6).toFixed(1)}" fill="none" stroke="${col}" stroke-width="1.1" stroke-dasharray="2 2" opacity="${Math.max(op,.7)}"/>`}
    if(over&&!lim)s+=`<circle cx="${cx}" cy="${top-4}" r="1.8" fill="${col}" opacity="${op}"/>`;
    s+='</g>';
    if(hot)s+=`<text class="hl" x="${cx}" y="${(over?top-9:y-6).toFixed(1)}" text-anchor="middle">${n[0]}</text>`;});
  if(legend){const mid=g=>{const idx=NU.map((n,i)=>n[4]===g?i:-1).filter(i=>i>=0);return ((idx[0]+idx[idx.length-1]+1)/2*slot+gx[g]).toFixed(1)};
    s+=`<text x="${mid('v')}" y="${h-3}" text-anchor="middle">VITAMINAS</text><text x="${mid('m')}" y="${h-3}" text-anchor="middle">MINERALES</text><text x="${mid('o')}" y="${h-3}" text-anchor="middle">OTROS</text>`}
  return `<svg class="huella" viewBox="0 0 ${w} ${h}" role="img" aria-label="Vitaminas y minerales respecto a tu referencia">${s}</svg>`}
const topIdx=(rows,n=3)=>rows.map((r,i)=>[r,i]).filter(([r,i])=>r.pct!=null&&!NU[i][5]).sort((a,b)=>b[0].pct-a[0].pct).slice(0,n).map(x=>x[1]);
function highlights(rows,label='Destaca en'){const t=topIdx(rows);const watch=rows.map((r,i)=>[r,i]).filter(([r,i])=>NU[i][5]&&r.pct!=null&&r.pct>=25);
  return `<div class="nhi"><span class="cap" style="background:none;border:0;padding:0 2px 0 0">${label}</span>${t.map(i=>`<span>${NU[i][1]}<b>${Math.round(rows[i].pct)} %</b></span>`).join('')}${watch.map(([r,i])=>`<span class="w">${NU[i][1]}<b>${Math.round(r.pct)} % del máx.</b></span>`).join('')}</div>`}
// Una fila de la lista completa. Sin referencia → solo el total; parcial → «≥»; tope → «del máximo».
function nr({name,val,unit='',pct=null,lim=false,est=0,miss=false,part=false,noref=false,note=''}){
  const over=pct!=null&&pct>100,p=pct==null?0:Math.min(100,pct),dec=p*(1-est);
  const right=miss?'':noref?'<span style="color:var(--dim)">—</span>':lim?`<span style="color:${over?'var(--danger)':'var(--ink)'}">${Math.round(pct)} %<small> del máx.</small></span>`:`${Math.round(pct)} %`;
  return `<div class="nrow ${miss?'miss':''}"><div class="t"><strong>${name}</strong><span class="v">${miss?'sin dato':(part?'≥ ':'')+fmtN(val)+' '+unit}</span><span class="p">${right}</span></div>
  ${miss?'<div class="nbar"></div>':noref?'':`<div class="nbar ${lim?'lim':''} ${over&&lim?'over':''}"><i style="width:${dec.toFixed(1)}%"></i>${est?`<i class="est" style="left:${dec.toFixed(1)}%;width:${(p*est).toFixed(1)}%"></i>`:''}${over&&!lim?'<em></em>':''}<u></u></div>`}
  ${note?`<div class="note">${note}</div>`:''}</div>`}
const nrowI=(i,r,note='')=>nr({name:NU[i][1],val:r.amt,unit:NU[i][2],pct:r.pct,lim:!!NU[i][5],est:r.est,miss:r.amt==null,part:r.part,note:note||(r.part?`${r.miss} ${r.miss===1?'alimento':'alimentos'} sin dato: puede ser más`:'')});
// Una sola fila de grupos que se desliza en horizontal (con letra grande no salta de línea).
const groupSeg=on=>`<div style="display:flex;gap:6px;overflow-x:auto;scrollbar-width:none;margin:8px -18px 4px;padding:0 18px">${['Vitaminas','Minerales','Grasas','Carbos','Otros'].map((x,i)=>`<span class="chip ${i===on?'on':''}" style="height:32px;flex-shrink:0">${x}</span>`).join('')}</div>`;
function fichaTop(f,{n=1}={}){return `<div class="ey">${f.src}</div><h3 style="margin-top:6px">${f.n}</h3>
  <div class="mgroup">Cuánto</div><div class="stepper"><button aria-label="Menos">−</button><b>${n}<small>${f.q.replace(/^1 /,'')} · ${f.g}</small></b><button aria-label="Más">+</button></div>
  <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:6px;margin-top:12px">${[[f.k,'kcal'],[f.p+' g','prot.'],[f.c+' g','carb.'],[f.f+' g','grasa']].map(k=>`<div class="kpi" style="padding:8px 10px"><b style="font-size:calc(16px*var(--fs));margin:0">${k[0]}</b><span>${k[1]}</span></div>`).join('')}</div>`}
const microHead=(right='% de tu día')=>`<div class="mgroup" style="display:flex;justify-content:space-between;align-items:baseline">Vitaminas y minerales<span style="text-transform:none;letter-spacing:0;font-weight:500">${right}</span></div>`;
const fichaFoot=(label='Añadir al almuerzo')=>`<div style="display:flex;gap:8px;margin-top:16px"><button class="pill sec" style="flex:0 0 auto;color:var(--ene)">${ic('starOn',18)}</button><button class="pill pri" style="flex:1">${label}</button></div>`;
// Cobertura de una comida: un punto por alimento (lleno = ficha completa, discontinuo = estimado).
const covDots=()=>`<span class="dots">${MEAL.map(k=>`<i class="${NF[k].est?'e':''}"></i>`).join('')}</span>`;
function mealMicro(){const rows=mealRows();return `<div class="mod" style="margin:10px 0 0;padding:14px 16px 14px"><div class="mod-h"><span class="ey">Vitaminas y minerales</span><span class="cap">% de tu día</span></div>
  <div style="margin-top:8px">${huella(rows,{h:78})}</div>${highlights(rows,'Esta comida')}
  <div class="ncov">${covDots()}<span>3 con ficha completa · el café con leche va estimado</span><span style="flex:1"></span>${ic('fwd',14)}</div></div>`}
function dayRing(){return `<div class="sumcard">${ringSvg(2240,2300).replace('<b>','<b style="font-size:calc(15px*var(--fs))">')}<div class="mac">${[['Proteína',162,'p',.92],['Carbos',228,'c',.8],['Grasa',71,'f',.86]].map(m=>`<div>${m[0]}<i style="--w:${Math.round(m[3]*100)}%;--c:${MACC[m[2]]}"></i><b>${m[1]} g</b></div>`).join('')}</div></div>`}
function dayCounts(){const r=dayRows();let ok=0,near=0,low=0,over=0;r.forEach((x,i)=>{if(NU[i][5]){if(x.pct>100)over++;return}if(x.pct>=100)ok++;else if(x.pct>=70)near++;else low++});
  return `<div class="ncount">${[[ok,'al día'],[near,'cerca'],[low,'por debajo'],[over,'sobre el máx.']].map((k,j)=>`<div class="kpi" style="padding:8px 10px"><b style="font-size:calc(18px*var(--fs));margin:0;${j===3&&k[0]?'color:var(--danger)':''}">${k[0]}</b><span>${k[1]}</span></div>`).join('')}</div>`}

TABS.splice(TABS.findIndex(t=>t.id==='historias'),0,{id:'nutrientes',label:'Vitaminas y minerales',title:'Vitaminas y minerales, en el registro y en la ficha',
  intro:'Nutrición v2 guarda hasta 38 nutrientes por alimento, con su origen (declarado, estimado o sin dato) y los compara con tu referencia DRI y con los topes de la OMS, la FDA y la EFSA. Aquí se ve dónde aparecen: en la ficha de cada alimento, en el registro de una comida, al guardar y en el día. Una sola pieza visual, la huella de nutrientes, y la misma honestidad que el motor: lo que no sabemos nunca cuenta como 0.'});

scene({tab:'nutrientes',id:'n-ficha',title:'Ficha · lo que aporta',after:drawAfter,html:()=>`<div class="lg">${lgHead()}${seg2(1)}${sfield('salmón')}</div>${sheet(fichaTop(NF.salmon)+microHead()+huella(foodRows(NF.salmon))+highlights(foodRows(NF.salmon))+
  `<div class="ncov"><span>${NF.salmon.cnt} de 38 nutrientes con dato</span><span style="flex:1"></span><button class="act" style="margin:0">Ver todos${ic('fwd',14)}</button></div>`+fichaFoot())}`,
 notes:{tags:[['new','Huella de nutrientes'],['new','Destaca en']],
  p:['Debajo de las calorías y los macros, la <b>huella</b>: una barra fina por vitamina, mineral y «otros» (fibra, omega-3 ALA, cafeína), cada una hasta el 100 % de tu referencia diaria (la línea punteada). Las tres que más aporta la ración van en acento y con su sigla; el resto, en tinta tenue. Crece de izquierda a derecha al abrir la ficha.',
     '<b>«Destaca en»</b> lo dice con palabras: Vitamina B12 175 %, Selenio 112 %, Vitamina D 100 %. Un punto encima de la línea marca lo que pasa del 100 %.',
     'Al cambiar la cantidad con −/+ la huella y los porcentajes se mueven a la vez. «Ver todos» abre la lista completa por grupos. El origen («USDA · verificado») y cuántos de los 38 nutrientes tienen dato van siempre a la vista.',
     'La referencia es la de tu perfil del plan (DRI de las Academias Nacionales de EE. UU.). Sin perfil adulto compatible, la huella se dibuja en cantidades relativas y la ficha lo dice; no inventamos un 100 %.']}});

scene({tab:'nutrientes',id:'n-ficha-vit',title:'Ficha · todos los nutrientes',html:()=>`<div class="lg">${lgHead()}${seg2(1)}${sfield('salmón')}</div>${sheet(`<div style="display:flex;gap:10px;align-items:center"><button class="ib fill" aria-label="Volver">${ic('back')}</button><div style="flex:1"><h3 style="margin:0">Salmón a la plancha</h3><div class="cap">1 filete · 150 g · % de tu día</div></div></div>
  ${groupSeg(0)}${foodRows(NF.salmon).slice(0,12).map((r,i)=>nrowI(i,r,i===3?'Preformada: con la que forma tu cuerpo, el total real suele ser mayor.':'')).join('')}`)}`,
 notes:{tags:[['keep','Los 38 nutrientes, por grupos'],['fix','Una fila, no un párrafo']],
  p:['Los mismos grupos y el mismo orden que la hoja de la app (vitaminas, minerales, grasas, carbohidratos y otros), con chips para saltar entre ellos. Cada nutriente en <b>una fila</b>: nombre, cantidad de esta ración, % de tu referencia y una barra fina hasta el 100 % (la marca del final). Lo que pasa del 100 % llena la barra y lleva una flecha.',
     'La nota de la niacina (la fuente da la preformada; la referencia está en equivalentes) se queda, en una línea bajo su fila.']}});

scene({tab:'nutrientes',id:'n-ficha-min',title:'Ficha · minerales y «sin dato»',html:()=>`<div class="lg">${lgHead()}${seg2(1)}${sfield('salmón')}</div>${sheet(`<div style="display:flex;gap:10px;align-items:center"><button class="ib fill" aria-label="Volver">${ic('back')}</button><div style="flex:1"><h3 style="margin:0">Salmón a la plancha</h3><div class="cap">1 filete · 150 g · % de tu día</div></div></div>
  ${groupSeg(1)}${foodRows(NF.salmon).slice(12,21).map((r,i)=>nrowI(i+12,r,i+12===18?'La ficha no trae yodo. No cuenta como 0: tus totales lo dirán.':i+12===19?'Tope diario: 2.300 mg (CDRR). Se lee como parte del máximo, no como meta.':'')).join('')}`)}`,
 notes:{tags:[['keep','«Sin dato» ≠ 0'],['keep','Topes de guía']],
  p:['<b>Sin dato</b> se escribe así y se dibuja con una línea discontinua, sin barra ni porcentaje: el motor no suma ceros que no existen (regla F4-1 de Nutrición v2).',
     'El sodio (y la cafeína, la grasa saturada, la trans y los azúcares añadidos) tienen <b>tope</b>, no meta: su barra es un contorno y el número dice «del máximo». Si se pasa, el contorno y la cifra van en rojo suave.']}});

scene({tab:'nutrientes',id:'n-ficha-fat',title:'Ficha · grasas y sin referencia',html:()=>`<div class="lg">${lgHead()}${seg2(1)}${sfield('salmón')}</div>${sheet(`<div style="display:flex;gap:10px;align-items:center"><button class="ib fill" aria-label="Volver">${ic('back')}</button><div style="flex:1"><h3 style="margin:0">Salmón a la plancha</h3><div class="cap">1 filete · 150 g · % de tu día</div></div></div>
  ${groupSeg(2)}${[{name:'Grasa saturada',val:3.8,unit:'g',pct:15,lim:true,note:'Tope de la guía: 10 % de la energía (25,6 g con 2.300 kcal) · OMS'},{name:'Grasa monoinsaturada',val:6.1,unit:'g',noref:true},{name:'Grasa poliinsaturada',val:5.2,unit:'g',noref:true},
    {name:'Grasa trans',val:.03,unit:'g',pct:1,lim:true},{name:'Omega-3 EPA + DHA',val:3,unit:'g',noref:true,note:'Sin referencia de ingesta: se muestra el total.'},{name:'Omega-3 ALA',val:.15,unit:'g',pct:9},{name:'Colesterol',val:95,unit:'mg',noref:true}].map(nr).join('')}`)}`,
 notes:{tags:[['keep','Sin referencia, sin barra']],
  p:['Donde no hay referencia de ingesta (monoinsaturada, poliinsaturada, EPA + DHA, colesterol, azúcares totales, alcohol) solo se muestra el total, sin barra ni porcentaje: un 0 % o una barra vacía sugerirían una meta que no existe.',
     'Los topes que dependen de la energía (saturada &lt; 10 %, trans &lt; 1 %, azúcares añadidos &lt; 10 %) dicen de dónde sale la cifra y quién la recomienda, como ya hace la hoja de la app.']}});

scene({tab:'nutrientes',id:'n-ficha-label',title:'Ficha · solo etiqueta',after:drawAfter,html:()=>`<div class="lg">${lgHead()}${seg2(1)}${sfield('barrita avena')}</div>${sheet(fichaTop(NF.barrita)+microHead()+huella(foodRows(NF.barrita),{hi:0})+
  `<div class="mod" style="margin-top:12px;padding:12px 14px;background:#ffffff06"><div style="display:flex;gap:10px"><span style="color:var(--muted);flex-shrink:0;margin-top:1px">${ic('info',18)}</span><div><div class="body" style="font-size:calc(13px*var(--fs))">La etiqueta declara ${NF.barrita.cnt} de 38 nutrientes: energía, macros, azúcares, fibra y sal. El resto queda <b>sin dato</b> y no cuenta como 0 en tu día.</div>
  <button class="act" style="margin-top:8px">${ic('edit',14)}Completar con una ficha propia</button></div></div></div>`+fichaFoot('Añadir a la merienda'))}`,
 notes:{tags:[['keep','Honestidad del dato'],['keep','Ficha propia']],
  p:['Un producto de Open Food Facts suele traer solo la tabla de la etiqueta. La huella lo enseña sin disimulo: la fibra y el sodio con su barra, todo lo demás con su trazo punteado.',
     'El aviso dice qué trae y qué no, y ofrece completarlo con una ficha propia (la app ya lo sugiere: «una ficha propia puede aportarlo»). No se rellena nada por parecido con otro producto.']}});

scene({tab:'nutrientes',id:'n-reg',title:'Registro · lo que aporta la comida',after:drawAfter,html:()=>`<div class="lg">${lgHead()}${mealChips()}
  <div class="pending">${ic('qmark',16)}2 dudas: tócalas para afinar o guarda con la estimación</div>
  <div style="margin-top:12px">${sumCard()}</div>${mealMicro()}${FOODS.slice(0,2).map((f,i)=>foodCard(f,i)).join('')}</div>${saveBar()}`,
 notes:{tags:[['new','La comida, en vitaminas y minerales'],['keep','Estimado a la vista']],
  p:['Bajo el anillo de calorías, un módulo con la <b>huella de la comida entera</b> (los cuatro alimentos sumados) y lo que más aporta: selenio, riboflavina y B12 en este desayuno. Si un tope pasa de una cuarta parte del día en una sola comida (sodio, cafeína), aparece en «a vigilar».',
     'La parte que viene de un alimento estimado (el café con leche, que lleva «aprox.») se dibuja en contorno discontinuo dentro de su barra, y un punto por alimento resume la cobertura: lleno = ficha completa, discontinuo = estimado.',
     'Al responder una duda (qué leche era) la huella se recalcula al momento. Tocar el módulo abre la lista completa de la comida, con las mismas filas que la ficha.']}});

scene({tab:'nutrientes',id:'n-reg-list',title:'Registro · la comida en detalle',html:()=>`<div class="lg">${lgHead()}${sumCard()}</div>${sheet(`<h3>Vitaminas y minerales</h3><div class="sub">Desayuno · 4 alimentos · % de tu día</div>
  <div class="ncov" style="margin:0 0 6px">${covDots()}<span>3 con ficha completa · 1 estimado</span></div>${groupSeg(1)}
  ${mealRows().slice(12,21).map((r,i)=>nrowI(i+12,r,i+12===18?'Solo lo aporta el café con leche, estimado; los otros tres no traen yodo.':'')).join('')}`)}`,
 notes:{tags:[['keep','Las mismas filas']],
  p:['Las filas de la ficha, para la comida. Si a algún alimento le falta el dato, el total lleva <b>«≥»</b> y la fila dice cuántos faltan: el yodo de este desayuno es una cota inferior y casi todo es estimado.',
     'La parte estimada va rayada dentro de la barra. Tocar una fila abre de qué alimento sale cada cantidad.']}});

scene({tab:'nutrientes',id:'n-edit',title:'Registro · ajustar un alimento',after:drawAfter,html:()=>`<div class="lg">${lgHead()}${sumCard()}${FOODS.slice(0,2).map((f,i)=>foodCard(f,i)).join('')}</div>${sheet(`<div style="display:flex;gap:12px;align-items:center"><span class="fi" style="width:48px;height:48px;border-radius:16px;background:#ffffff0a;display:flex;align-items:center;justify-content:center">${ic('egg',26)}</span><div style="flex:1"><h3>Huevos revueltos</h3><div class="cap">Coincidencia alta · USDA</div></div><button class="act">Cambiar</button></div>
  <div class="mgroup">Cantidad</div><div class="stepper"><button aria-label="Menos">−</button><b>2<small>huevos</small></b><button aria-label="Más">+</button></div>
  <div class="mgroup">Aporta</div><div style="display:grid;grid-template-columns:repeat(4,1fr);gap:6px">${[['182','kcal'],['13 g','prot.'],['2 g','carb.'],['14 g','grasa']].map(k=>`<div class="kpi" style="padding:8px 10px"><b style="font-size:calc(16px*var(--fs));margin:0">${k[0]}</b><span>${k[1]}</span></div>`).join('')}</div>
  ${microHead()}${huella(foodRows(NF.huevos),{h:74})}${highlights(foodRows(NF.huevos))}
  <div style="display:flex;gap:8px;margin-top:16px"><button class="pill sec" style="flex:0 0 auto;color:var(--danger);border-color:#ff9b9255">${ic('trash',18)}</button><button class="pill pri" style="flex:1">Listo</button></div>`)}`,
 notes:{tags:[['new','Nutrientes de esta cantidad']],
  p:['La hoja de ajuste suma la huella de esa cantidad bajo los macros. Con −/+ cambian a la vez las calorías, los macros y las barras, así se ve qué aporta un huevo más.',
     'Si cambias el alimento por otra coincidencia («Cambiar»), la huella cambia con él: es la forma más rápida de ver que la versión integral trae más fibra y magnesio.']}});

scene({tab:'nutrientes',id:'n-saved',title:'Registro · guardado',html:()=>`<div class="lg">${lgHead()}${sumCard()}</div><div class="savedone">
  <svg class="draw go" viewBox="0 0 120 120" width="140" height="140" aria-hidden="true"><circle class="ln" pathLength="1" cx="60" cy="60" r="50" fill="none" stroke="#43d18c" stroke-width="3"/><path class="ln" pathLength="1" d="M38 62l15 15 30-32" fill="none" stroke="#43d18c" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" style="animation-delay:.5s"/></svg>
  <b>Desayuno guardado</b><span>548 kcal · te quedan 1.752 para hoy</span><span style="margin-top:6px;color:var(--faint)">Lo que más sumó: selenio, riboflavina y B12</span></div>`,after:drawAfter,
 notes:{tags:[['new','Una línea, no un panel']],p:['El check de siempre con una sola línea más: lo que más sumó esa comida. Nada de porcentajes aquí; se cierra solo a los 0,8 s.']}});

scene({tab:'nutrientes',id:'n-home',title:'Inicio · revisar y guardar',after:drawAfter,html:()=>`${homeBase()}<div class="scrim" style="background:#0007"></div><div class="qsheet" style="max-height:82%"><div class="grab"></div>
  <div class="lg-head" style="margin:0 -6px 2px"><div class="t"><strong style="font-size:calc(16px*var(--fs))">Almuerzo · 14:10</strong><span>3 alimentos · 1 duda</span></div><button class="act faint">Abrir completo${ic('fwd',14)}</button></div>
  ${HOME_FOODS.map(homeCard).join('')}
  <div class="nhi" style="margin-top:12px"><span class="cap" style="background:none;border:0;padding:0 2px 0 0">Esta comida</span><span>Niacina<b>151 %</b></span><span>Vitamina B6<b>112 %</b></span><span>Selenio<b>89 %</b></span></div>
  <div style="display:flex;gap:8px;margin-top:12px"><button class="pill sec" style="flex:0 0 auto">${ic('plus',18)}</button><button class="pill pri" style="flex:1">Guardar · 652 kcal</button></div></div>`,
 notes:{tags:[['keep','Media hoja ligera']],p:['En la media hoja de Inicio no cabe la huella: solo la línea «Esta comida» con lo que más aporta. La huella y la lista están en «Abrir completo».']}});

scene({tab:'nutrientes',id:'n-day',title:'Nutrición · el día',after:drawAfter,html:()=>`<div class="scroll" style="padding-top:52px"><div class="cap" style="letter-spacing:1.4px;font-weight:600">MIÉRCOLES 28</div><div class="ptitle" style="margin-top:4px">Nutrición</div>
  <div style="margin-top:14px">${dayRing()}</div>
  <div class="mod" style="margin-top:12px"><div class="mod-h"><span class="ey">Vitaminas y minerales</span><button class="act faint" style="margin:0">Ver todos${ic('fwd',14)}</button></div>
  <div style="margin-top:8px">${huella(dayRows(),{hi:0,day:true})}</div>${dayCounts()}
  <div class="ncov"><span>Cobertura: 86 % de las kcal con dato · 2 alimentos sin ficha</span></div></div>
  <div class="mod tight"><div class="rowx"><div class="tx"><strong>Desayuno</strong><span>Huevos, tostadas, aguacate y café · 548 kcal</span></div></div><div class="rowx"><div class="tx"><strong>Almuerzo</strong><span>Lentejas con chorizo · 652 kcal</span></div></div></div></div>${navbar(2)}`,
 notes:{tags:[['new','Huella del día'],['move','Nutrientes del día a la vista']],
  p:['Hoy «Nutrientes del día» es una hoja que se abre desde Nutrición. Ahora el día tiene un módulo propio bajo el anillo: la huella del día (lo que llega al 100 % en tinta plena, lo que falta en tenue, el sodio pasado en rojo) y cuatro cifras: <b>al día, cerca (70–99 %), por debajo y sobre el máximo</b>.',
     'Debajo, la cobertura honesta que ya calcula el motor (qué parte de las kcal tiene dato declarado) y cuántos alimentos no tienen ficha. «Ver todos» abre la hoja completa.']}});

scene({tab:'nutrientes',id:'n-day-sheet',title:'Nutrientes del día',html:()=>`<div class="scroll" style="padding-top:52px"><div class="ptitle">Nutrición</div></div>${sheet(`<h3>Nutrientes del día</h3><div class="sub">Miércoles 28 · referencia de hombre, 30 años (DRI), según tu plan</div>
  <div class="ncov" style="margin:0 0 4px"><span>86 % de las kcal con dato declarado · el total puede ser mayor</span></div>
  <div class="mgroup" style="color:var(--danger)">A vigilar</div>${nrowI(19,dayRows()[19],'Máximo recomendado: 2.300 mg (CDRR) · AI: 1.500 mg')}
  ${groupSeg(0)}${dayRows().slice(0,12).map((r,i)=>nrowI(i,r)).join('')}`)}`,
 notes:{tags:[['keep','Todo lo que dice hoy'],['fix','Una fila por nutriente']],
  p:['La hoja de hoy, con todo lo que dice (total conocido, completo o parcial, declarados y estimados, cobertura por kcal, RDA/AI, límites superiores con su alcance, topes de la OMS, la FDA y la EFSA, la nota de la niacina y las fuentes y licencias), pero en <b>una fila por nutriente</b> en vez de seis líneas de texto. El detalle va a la hoja de cada nutriente.',
     'Lo que pasa un tope sube arriba, en «A vigilar». Lo parcial lleva «≥» y dice cuántos alimentos faltan (vitamina D: 2).']}});

scene({tab:'nutrientes',id:'n-nutrient',title:'Un nutriente · Hierro',after:drawAfter,html:()=>`<div class="scroll" style="padding-top:52px"><div class="ptitle">Nutrición</div></div>${sheet(`<div class="ey">Mineral · hoy</div><h3 style="margin-top:6px">Hierro</h3>
  <div style="display:flex;align-items:baseline;gap:10px;margin-top:6px"><span class="big" style="font-size:calc(44px*var(--fs))">11,4</span><span style="color:var(--muted)">mg de 8 mg</span><span style="flex:1"></span><span style="font:700 calc(18px*var(--fs)) Syne">142 %</span></div>
  <div class="nbar" style="height:8px;margin-top:10px"><i style="width:100%"></i><em></em><u></u></div>
  <div class="mgroup">De dónde sale</div>
  ${[['Lentejas guisadas',4.9],['Pan tostado',2.2],['Huevos revueltos',1.9],['Espinacas salteadas',1.6],['Otros alimentos (3)',.8]].map(c=>`<div class="nrow"><div class="t"><strong>${c[0]}</strong><span class="v">${fmtN(c[1])} mg</span></div><div class="nbar" style="background:none"><i style="width:${(c[1]/4.9*100).toFixed(0)}%;opacity:.55"></i></div></div>`).join('')}
  <div class="ncov" style="margin-top:12px">${ic('info',14)}<span>El batido de proteína no trae dato de hierro: el total puede ser mayor.</span></div>
  <div class="mgroup">Referencia</div><div class="ratio"><span>RDA</span><b>8 mg</b></div><div class="ratio"><span>Límite superior total</span><b>45 mg</b></div>
  <div class="cap" style="margin-top:10px">Academias Nacionales de EE. UU. No sustituye el consejo de un profesional.</div>`)}`,
 notes:{tags:[['keep','Contribuyentes'],['move','De desplegable a hoja']],
  p:['Hoy «Ver contribuyentes» despliega una lista de texto dentro de la fila. Ahora cada nutriente tiene su hoja: la cifra grande respecto a tu referencia, de qué alimentos sale (con barras relativas, los cinco mayores y «otros»), qué alimento no trae dato y la referencia con su límite superior.',
     'Los mismos datos que ya calcula <code>NutrientCoverage</code>: no hay ningún número nuevo.']}});
