#!/usr/bin/env node
// Presets de profundidad y movimiento (los cimientos de la app: ui/design/depth y ui/design/motion) a partir del inventario del lienzo.
//   node build.cjs --out=shots/cm && node tools/inventory.cjs --build=shots/cm     (datos de partida: paridad/_inventory.json)
//   node tools/tokens-group.cjs                                                    (paridad/cimientos.json + paridad/CIMIENTOS_GRUPOS.md + bloque de datos de src/scenes-cm.js)
//   node build.cjs --out=shots/cm && node tools/inventory.cjs --build=shots/cm     (marca cada fila con « → preset <nombre>»)
// Determinista y sin dependencias. La recolección y la agrupación (tolerancias) están en tools/tokens-core.cjs; aquí van el nombrado, la geometría
// de las hojas de calibración y la salida. CIMIENTOS_GRUPOS.md repite las reglas y enseña, por preset, su valor canónico exacto, sus miembros, las filas
// del inventario que cubre y la mayor distancia de un miembro al canónico.
'use strict';
const fs=require('node:fs');
const path=require('node:path');
const core=require('./tokens-core.cjs');
const {splitTop,tokens,parseColor,fnum,normCurve}=require('./cssvars.cjs');
const {vars,T,animItems,shadowGroups,textGroups,backdropGroups,filterGroups,curveGroups,durGroups,delayGroups,gradGroups,parseShadow,shadowDelta,parseCurve,parseFilter,parseGradient,fmtDist,maxDist,SCALE,pairs}=core;
const root=path.resolve(__dirname,'..');
const inv=JSON.parse(fs.readFileSync(path.join(root,'paridad','_inventory.json'),'utf8'));
const round=core.round;
const uses=g=>g.members.reduce((a,m)=>a+m.uses,0);
const sels=g=>[...new Set(g.members.flatMap(m=>m.selectors.map(s=>s.replace(/^[a-z]+:/,''))))];

// ---------------------------------------------------------------- Nombres de color (paleta del lienzo)
const PALETTE=[['crema',[242,238,230]],['musculo',[244,154,110]],['energia',[247,207,115]],['hecho',[67,209,140]],['peligro',[255,155,146]],['cardio',[143,178,255]],['hoja',[27,27,29]],['modulo',[17,17,17]],['negro',[0,0,0]],['blanco',[255,255,255]]];
function colorName(c){
  if(!c)return 'color';
  if(c.dyn)return 'zona';
  if(c.a<0.02)return 'transparente';
  let best=null;
  for(const [n,[r,g,b]] of PALETTE){const d=Math.max(Math.abs(c.r-r),Math.abs(c.g-g),Math.abs(c.b-b));if(!best||d<best.d)best={n,d}}
  if(best.d<=24)return best.n;
  return 'gris'+Math.round((c.r+c.g+c.b)/3);
}
const pct=a=>Math.round(a*100);
const cssColor=c=>c.dyn?(c.a<1?`color-mix(in srgb, var(${c.dyn}) ${pct(c.a)}%, transparent)`:`var(${c.dyn})`):(c.a>=1?`rgb(${Math.round(c.r)}, ${Math.round(c.g)}, ${Math.round(c.b)})`:`rgba(${Math.round(c.r)}, ${Math.round(c.g)}, ${Math.round(c.b)}, ${round(c.a,3)})`);

// ---------------------------------------------------------------- Nombres de sombra: papel (selectores) o estructura
// Cada regla se prueba primero contra los selectores del valor canónico y después contra los de los demás miembros; gana la primera regla que encaja.
const SHADOW_ROLES=[
  ['subrayado-musculo',/^\.tok\.on$/],['subrayado-hecho',/^\.tok\.done$/],
  ['hoja',/^\.pl-sheet$|^\.ct-sheet$/],
  ['isla-vidrio',/^\.pl-glass$/],['barra-vidrio',/^\.topbar \.glass$/],
  ['flotante',/^\.pl-flat$/],['modal',/^\.aj-sys$/],
  ['dialogo',/^\.rp-dlg$|^\.ct-dlg$/],['dialogo-vidrio',/^\.tc-vfi$/],
  ['menu',/^\.as-menu$/],['aviso',/^\.as-toast$/],
  ['levantado',/\.lifted$/],['popover',/^\.ed-pop$/],['historia',/^\.story$/],
  ['anillo-actual',/^\.wkc\.now$/],['anillo-doble-actual',/^\.ribbon i\.now$/],
  ['aureola-foco',/^\.ab\.cur$/],['anillo-foco',/^\.lv-car:focus-visible$/],
  ['borde-interior',/^\.as-cv$/],['borde-interior-fuerte',/^\.ct-strip i\.em$/],['borde-exterior',/^\.lv-ethumb$/],
  ['corte-hoja',/^\.ce-asi em$/],['asa-deslizador',/slider-thumb/],['nota',/^\.ann$/],['pie-sube',/^\.as-dec/],
  ['anillo-error',/\.tc-q\.bad/],['anillo-hecho',/\.tc-q\.ok/],['anillo-aviso',/\.tc-q \.tc-qd$/],
];
const kindOf=layers=>{
  if(layers.every(l=>l.c.a<0.02&&!l.c.dyn))return 'nula';
  const outer=layers.filter(l=>!l.inset);
  const drop=outer.some(l=>l.blur>0&&(l.x!==0||l.y!==0));
  if(drop)return 'caida';
  const glow=outer.some(l=>l.blur>0&&l.x===0&&l.y===0);
  if(glow)return 'resplandor';
  if(outer.length)return 'anillo';
  return 'interior';
};
const reachOf=layers=>Math.max(0,...layers.filter(l=>!l.inset).map(l=>Math.max(Math.abs(l.x),Math.abs(l.y))+l.blur+Math.max(l.spread,0)));
const fmtN=n=>String(round(n,2)).replace('.','p');
function structName(layers,kind){
  const col=l=>colorName(l.c);
  const first=layers.find(l=>!(kind==='caida'&&col(l)==='negro'&&layers.length>1&&l!==layers[0]&&false))||layers[0];
  if(kind==='nula')return 'ninguna';
  if(kind==='caida'){const d=layers.find(l=>!l.inset&&l.blur>0)||layers[0];return `caida-${fmtN(d.y)}-${fmtN(d.blur)}`+(layers.length>1?'-capas':'')}
  if(kind==='resplandor'){const g=layers.find(l=>!l.inset&&l.blur>0)||layers[0];return `resplandor-${col(g)}-${fmtN(g.blur)}`+(layers.length>1?'-capas':'')}
  if(kind==='anillo'){const r=layers[layers.length-1];const a=layers[0];return layers.length>1?`anillo-doble-${col(layers[1])}`:`anillo-${col(a)}-${fmtN(a.spread)}`+(a.c.a<0.35&&a.c.a>=0.02?'-suave':'')}
  const i=layers[0];return `interior-${col(i)}-${i.blur===0&&i.y!==0?'linea':fmtN(i.spread)}`+(i.c.a<0.2&&!i.c.dyn?'-tenue':'');
}
function nameShadow(g,layers){
  const canonSel=g.canon.selectors.map(s=>s.replace(/^[a-z]+:/,''));
  const memSel=sels(g);
  for(const [name,re] of SHADOW_ROLES){if(canonSel.some(s=>re.test(s)))return {name,role:true}}
  for(const [name,re] of SHADOW_ROLES){if(memSel.some(s=>re.test(s)))return {name,role:true}}
  return {name:structName(layers,kindOf(layers)),role:false};
}
function dedupeNames(list){
  // list: [{name,u,order}] → nombres únicos: si varios comparten nombre se numeran -1, -2… por uso
  const count=new Map();list.forEach(x=>count.set(x.name,(count.get(x.name)||0)+1));
  const seen=new Map();
  for(const x of list){
    if(count.get(x.name)>1){const n=(seen.get(x.name)||0)+1;seen.set(x.name,n);x.name=`${x.name}-${n}`}
  }
  // por si el sufijo choca con otro nombre ya existente
  const used=new Set();for(const x of list){let n=x.name,i=2;while(used.has(n))n=x.name+'-'+(i++);x.name=n;used.add(n)}
}

// ---------------------------------------------------------------- Geometría de las hojas de calibración (dp = píxeles CSS de la escena a 360 × 880)
// Origen: esquina superior izquierda de la hoja (la captura de 360 × 880 sin marco ni barras). Todo en posiciones exactas.
const SHEET={w:360,h:880,bg:'#0B0B0C',surf:'#1B1B1D',sw:140,sh:84,sr:24};
const CLASSES={
  A:{desc:'alcance ≤ 14 dp: rejilla de 2 columnas x 5 filas (x 24 y 196; y 72 + 150·fila); etiqueta debajo de la muestra',cols:[24,196],y0:72,pitch:150,rows:5},
  S:{desc:'alcance > 14 dp: 1 columna; muestra en x 110; la etiqueta (34 dp) va encima y la y de cada fila depende de lo que sobresale la sombra',x:110,y0:60,labelH:34,gap:8,limit:868},
};
const classOf=r=>r<=14?'A':'S';
// Lo que sobresale la sombra por arriba y por abajo de la muestra (capas exteriores)
const extentsOf=layers=>{let up=0,down=0;for(const l of layers){if(l.inset)continue;const e=l.blur+Math.max(l.spread,0);up=Math.max(up,e-l.y);down=Math.max(down,e+l.y)}return {up:Math.max(0,Math.round(up)),down:Math.max(0,Math.round(down))}};
function layoutSheets(items,idBase,titleBase){
  // items: [{name,clase,up,down}] ya ordenados; devuelve hojas con posiciones. Cada clase rellena sus propias hojas en orden.
  const sheets=[];
  const gridList=items.filter(i=>i.clase==='A');
  const g=CLASSES.A,cap=g.cols.length*g.rows;
  for(let s=0;s*cap<gridList.length;s++){
    const sheet={id:`${idBase}-${sheets.length+1}`,titulo:titleBase,clase:'A',items:[]};
    gridList.slice(s*cap,(s+1)*cap).forEach((it,i)=>{
      const col=i%g.cols.length,row=Math.floor(i/g.cols.length);
      const x=g.cols[col],y=g.y0+row*g.pitch;
      it.pos={hoja:sheet.id,x,y,w:SHEET.sw,h:SHEET.sh,lx:x,ly:y+SHEET.sh+10,lw:SHEET.sw};sheet.items.push(it.name);
    });
    sheets.push(sheet);
  }
  const single=items.filter(i=>i.clase==='S'),S=CLASSES.S;
  let sheet=null,cursor=0;
  for(const it of single){
    const sy=()=>cursor+S.labelH+Math.max(0,it.up-20);
    if(!sheet||sy()+SHEET.sh+it.down>S.limit){sheet={id:`${idBase}-${sheets.length+1}`,titulo:titleBase,clase:'S',items:[]};sheets.push(sheet);cursor=S.y0}
    const y=sy();
    it.pos={hoja:sheet.id,x:S.x,y,w:SHEET.sw,h:SHEET.sh,lx:16,ly:y-S.labelH+4,lw:328};sheet.items.push(it.name);
    cursor=y+SHEET.sh+it.down+S.gap;
  }
  return sheets;
}

const hex=c=>c.dyn?'zona':'#'+[c.r,c.g,c.b].map(v=>Math.round(v).toString(16).padStart(2,'0')).join('');
const dsp=n=>String(round(n,2)).replace('.',',');
const abbr=layers=>layers.map(l=>`${l.inset?'int ':''}${dsp(l.x)} ${dsp(l.y)} ${dsp(l.blur)} ${dsp(l.spread)} ${hex(l.c)}/${dsp(round(l.c.a,2))}`).join(' + ');
// ---------------------------------------------------------------- Construir presets de sombras
function buildShadows(groups,textual){
  const out=groups.map(g=>{
    const layers=g.canon.parsed;
    const nm=layers?nameShadow(g,layers):{name:'sombra-'+g.canon.order,role:false};
    const kind=layers?kindOf(layers):'otra';
    return {g,layers,kind,name:nm.name,role:nm.role,u:uses(g),order:g.canon.order,reach:layers?reachOf(layers):0};
  });
  dedupeNames(out);
  return out.map(o=>({
    nombre:o.name,tipo:o.kind,papel:o.role,
    canonico:o.g.canon.value,abreviado:o.layers?abbr(o.layers):o.g.canon.value,
    capas:(o.layers||[]).map(l=>({inset:l.inset,x:l.x,y:l.y,desenfoque:l.blur,extension:l.spread,color:cssColor(l.c),dinamico:!!l.c.dyn})),
    dinamico:(o.layers||[]).some(l=>l.c.dyn),
    alcance:round(o.reach,1),
    clase:classOf(o.reach),sobresale:o.layers?extentsOf(o.layers):{up:0,down:0},
    usos:o.u,
    miembros:o.g.members.map(m=>({valor:m.value,usos:m.uses,distancia:m.dist||{},donde:m.selectors.slice(0,4)})),
    distanciaMax:maxDist(o.g.members),
    donde:sels(o.g).slice(0,8),
    texto:textual,
  }));
}
const shadowPresets=buildShadows(shadowGroups,false);
const textPresets=buildShadows(textGroups,true).map(p=>({...p,nombre:'texto-'+p.nombre.replace(/^caida-/,'')}));
dedupeNames(textPresets.map(p=>({get name(){return p.nombre},set name(v){p.nombre=v}})));

// Orden de las hojas: por tipo y dentro, de más a menos usado. (La clase decide la rejilla.)
const KIND_ORDER=['caida','anillo','interior','resplandor','nula'];
const KIND_TITLE={caida:'Caídas',anillo:'Anillos',interior:'Realces y bordes interiores',resplandor:'Resplandores',nula:'Sin sombra'};
const shadowSheets=[];
for(const k of KIND_ORDER){
  const items=shadowPresets.filter(p=>p.tipo===k).map(p=>({name:p.nombre,clase:p.clase,up:p.sobresale.up,down:p.sobresale.down,ref:p}));
  if(!items.length)continue;
  const sheets=layoutSheets(items,`cm-som-${k}`,KIND_TITLE[k]);
  items.forEach(it=>{it.ref.hoja=it.pos});
  shadowSheets.push(...sheets);
}
// los ids de hoja de cada tipo se numeran por tipo (cm-som-caida-1…): layoutSheets numera con la cuenta de ese tipo
{
  // cada tipo numera sus hojas desde 1 (layoutSheets recibió la lista de ese tipo)
}
const textItems=textPresets.map(p=>({name:p.nombre,clase:'S',up:p.sobresale.up,down:p.sobresale.down,ref:p}));
const textSheets=layoutSheets(textItems,'cm-som-texto','Sombras de texto');
textItems.forEach(it=>{it.ref.hoja=it.pos});

// ---------------------------------------------------------------- Vidrio y filtros
const BACKDROP_ROLES=[['vidrio-isla',/^\.pl-glass$/],['vidrio-hoja',/^\.glass$/],['vidrio-barra',/^\.topbar \.glass$/],['vidrio-editor',/^\.ed-glass$|^\.ce-glass$/],['vidrio-fondo',/^\.tc-glass$/],['velo-desenfoque',/^\.ct-back$|^\.cv-link$/],['vidrio-interruptor',/^\.cv-sw$/],['velo-suave',/^\.cv-cnt p$/]];
function namePlain(g,roles,fallback){
  const canonSel=g.canon.selectors.map(s=>s.replace(/^[a-z]+:/,'')),memSel=sels(g);
  for(const [n,re] of roles)if(canonSel.some(s=>re.test(s)))return n;
  for(const [n,re] of roles)if(memSel.some(s=>re.test(s)))return n;
  return fallback;
}
function buildFilters(groups,prefix,roles){
  const out=groups.map(g=>{
    const f=g.canon.parsed||{blur:0,sat:1,bri:1};
    const fb=prefix+(f.blur?`-${fnum(f.blur)}`:'')+(f.sat!==1?`-sat${fnum(round(f.sat*100,0))}`:'')+(f.bri!==1?`-bri${fnum(round(f.bri*100,0))}`:'');
    return {g,f,name:namePlain(g,roles,fb),u:uses(g)};
  });
  dedupeNames(out);
  return out.map(o=>({nombre:o.name,canonico:o.g.canon.value,desenfoque:o.f.blur,saturacion:o.f.sat,brillo:o.f.bri,usos:o.u,miembros:o.g.members.map(m=>({valor:m.value,usos:m.uses,distancia:m.dist||{}})),distanciaMax:maxDist(o.g.members),donde:sels(o.g).slice(0,8)}));
}
const backdropPresets=buildFilters(backdropGroups,'vidrio',BACKDROP_ROLES);
const FILTER_ROLES=[['halo-desenfoque',/^\.cv-halo$/],['resplandor-desenfoque',/^\.lx-glow$/],['foto-apagada',/\.story \.photo$/],['video-apagado',/\.tc-vfi \.tc-svg$/],['miniatura-apagada',/\.tc-mkf \.tc-svg$/]];
const filterPresets=buildFilters(filterGroups,'filtro',FILTER_ROLES);
// geometría: 7 franjas de 312 × 72 por hoja de vidrio; filtros: muestras de 140 × 84 en 2 columnas
const GLASS={sx:24,sw:312,sh:72,y0:72,pitch:108,perSheet:7,pattern:'barras y texto fijos (ver cm.css .cm-pat)'};
backdropPresets.forEach((p,i)=>{const s=Math.floor(i/GLASS.perSheet),r=i%GLASS.perSheet;p.hoja={hoja:`cm-vid-${s+1}`,x:GLASS.sx,y:GLASS.y0+r*GLASS.pitch,w:GLASS.sw,h:GLASS.sh,lx:GLASS.sx+10,ly:GLASS.y0+r*GLASS.pitch+8,lw:292}});
const FGRID={cols:[24,196],y0:72,pitch:150,rows:5};
filterPresets.forEach((p,i)=>{const s=Math.floor(i/(FGRID.cols.length*FGRID.rows)),k=i%(FGRID.cols.length*FGRID.rows),col=k%2,row=Math.floor(k/2);p.hoja={hoja:`cm-vid-filtros-${s+1}`,x:FGRID.cols[col],y:FGRID.y0+row*FGRID.pitch,w:SHEET.sw,h:SHEET.sh,lx:FGRID.cols[col],ly:FGRID.y0+row*FGRID.pitch+SHEET.sh+10,lw:SHEET.sw}});

// ---------------------------------------------------------------- Curvas
const CURVE_NAMES={
  'cubic-bezier(0.2, 0.8, 0.2, 1)':['estandar','La de casi todo: sale rápido y se asienta despacio.'],
  'ease':['suave','Estándar del navegador para color, fondo y opacidad.'],
  'linear':['lineal','Barras de progreso, giros continuos y barridos.'],
  'cubic-bezier(0.3, 0.7, 0.2, 1)':['trazo','Dibujo de líneas y anillos (stroke-dashoffset).'],
  'ease-out':['salida','Pulsaciones y retornos cortos.'],
  'cubic-bezier(0.3, 1.5, 0.5, 1)':['resorte','Muelle del editor: rebasa un 15 % y vuelve.'],
  'ease-in-out':['vaiven','Giros y trazos que van y vienen.'],
  'steps(1)':['parpadeo','Parpadeo del cursor (todo o nada).'],
  'cubic-bezier(0.5, 0, 0.9, 0.5)':['cae','Salidas del editor: arranca lento y acelera.'],
  'cubic-bezier(0.4, 0, 1, 1)':['acelera','Cierre de la hoja de la ruta y del velo.'],
  'cubic-bezier(0.4, 0, 0.2, 1)':['material','Estándar de Material (entrada y salida de los fondos).'],
  'cubic-bezier(0.3, 1.6, 0.5, 1)':['resorte-fuerte','Muelle de suplementos: rebasa un 20 %.'],
  'cubic-bezier(0.5, 0, 0.2, 1)':['ruta','Trazado de la ruta del cardio.'],
  'cubic-bezier(0.22, 1.12, 0.36, 1)':['resorte-suave','Apertura de la hoja de la ruta: rebasa un 5 %.'],
};
const curvePresets=curveGroups.map(g=>{
  const [nm,desc]=CURVE_NAMES[g.canon.value]||['curva-'+g.canon.order,''];
  const p=g.canon.parsed;
  return {nombre:nm,descripcion:desc,canonico:g.canon.value,bezier:p&&p.b?p.b:null,palabra:p&&p.kw||null,escalones:g.canon.value.startsWith('steps'),usos:uses(g),
    miembros:g.members.map(m=>({valor:m.value,usos:m.uses,distancia:m.dist||{}})),distanciaMax:maxDist(g.members),donde:sels(g).slice(0,6)};
});
dedupeNames(curvePresets.map(p=>({get name(){return p.nombre},set name(v){p.nombre=v}})));
const curveOf=new Map();curveGroups.forEach((g,i)=>g.members.forEach(m=>curveOf.set(m.value,curvePresets[i].nombre)));

// ---------------------------------------------------------------- Duraciones
const durName=ms=>String(fnum(ms));
const durPresets=durGroups.filter(g=>g.ms>0).map(g=>({nombre:durName(g.ms),ms:g.ms,exacto:g.exact,usos:g.uses,
  miembros:g.members.map(m=>({valor:m.value,usos:m.uses,distanciaPct:round(m.dist.pct||0,1)})),distanciaMax:{pct:Math.max(0,...g.members.map(m=>m.dist.pct||0))}}));
const delayPresets=delayGroups.filter(g=>g.ms>0).map(g=>({nombre:'r'+durName(g.ms),ms:g.ms,exacto:g.exact,usos:g.uses,
  miembros:g.members.map(m=>({valor:m.value,usos:m.uses,distanciaPct:round(m.dist.pct||0,1)})),distanciaMax:{pct:Math.max(0,...g.members.map(m=>m.dist.pct||0))}}));
const durOf=new Map();durGroups.forEach(g=>g.members.forEach(m=>durOf.set(m.value,durName(g.ms))));
// pares curva-duración más usados por curva (para las demostraciones)
const pairByCurve=new Map();
for(const e of pairs.list()){
  const [fn,du]=e.value.split(' | ');const cp=curveOf.get(fn),dn=durOf.get(du);if(!cp||!dn||dn==='0')continue;
  const m=pairByCurve.get(cp)||new Map();m.set(dn,(m.get(dn)||0)+e.uses);pairByCurve.set(cp,m);
}
curvePresets.forEach(p=>{
  const m=pairByCurve.get(p.nombre)||new Map();
  p.duracionesTipicas=[...m.entries()].sort((a,b)=>b[1]-a[1]||(+a[0])-(+b[0])).slice(0,2).map(([d,n])=>({ms:+d,usos:n}));
});

// ---------------------------------------------------------------- Degradados
const GRAD_ROLES=[['vidrio-relleno-hoja',/^\.glass$/],['vidrio-relleno-barra',/^\.topbar \.glass$/],['vidrio-relleno-tecnica',/^\.tc-glass$/],['aviso-relleno-error',/^\.tc-toast$/]];
function nameGradient(g,p){
  const role=namePlain(g,GRAD_ROLES,null);if(role)return role;
  if(!p)return 'degradado-'+g.canon.order;
  if(p.multi){return /1px/.test(g.canon.value)?'rejilla':/radial/.test(g.canon.value)?'fondo-compuesto':'compuesto'}
  const st=p.stops,f=st[0].c,l=st[st.length-1].c,n=colorName;
  if(/^repeating/.test(p.kind))return 'rayas-'+n(f);
  if(p.kind==='radial'){
    if(l.a<0.05&&p.head==='closest-side')return 'resplandor-'+n(f.a<0.05?st[1].c:f);
    if(l.a<0.05)return 'resplandor-ancho-'+n(f.dyn?f:st[0].c);
    return 'vineta';
  }
  if(st.length===4&&p.ang===90&&st[1].pos&&st[2].pos&&st[1].pos.v===st[2].pos.v)return 'progreso-barra';
  if(st.length>=3&&p.ang!==null&&(p.ang===90||p.ang===100)&&st[0].c.a<=st[1].c.a)return 'brillo-esqueleto';
  if(st.length===3&&st[0].c.a<0.05&&st[2].c.a<0.05)return 'franja-'+n(st[1].c);
  if(f.a<0.05&&l.a>=0.5)return 'velo-'+n(l)+(st.length>2?'-largo':'');
  if(l.a<0.05&&f.a>=0.5)return 'velo-'+n(f)+'-sale';
  if(st.length===3&&f.a<0.05)return 'velo-'+n(st[st.length-1].c)+'-largo';
  if(Math.abs(f.a-l.a)<0.01&&Math.abs(f.r-l.r)<4&&Math.abs(f.g-l.g)<4&&Math.abs(f.b-l.b)<4)return 'tinte-'+n(f)+'-'+pct(f.a);
  if(f.a<0.2&&st.length===2&&l.a>=0.9)return 'tinte-hacia-'+n(l);
  if(p.ang===90&&f.dyn)return 'barra-'+n(f);
  if(p.ang===90)return 'relleno-horizontal-'+n(f);
  return 'relleno-'+n(f)+(l.a<1?'-suave':'');
}
const gradPresets=gradGroups.map(g=>({g,p:g.canon.parsed,name:nameGradient(g,g.canon.parsed),u:uses(g)}));
dedupeNames(gradPresets);
const gradOut=gradPresets.map(o=>({nombre:o.name,tipo:o.p?(o.p.multi?'compuesto':o.p.kind):'otro',canonico:o.g.canon.value,dinamico:/var\(--/.test(o.g.canon.value),usos:o.u,
  miembros:o.g.members.map(m=>({valor:m.value,usos:m.uses,distancia:m.dist||{}})),distanciaMax:maxDist(o.g.members),donde:sels(o.g).slice(0,6)}));
// Orden de hojas: por familia de nombre (relleno, velo, resplandor, vidrio, brillo, rayas, compuesto…) y dentro por uso
const gradFam=n=>/^(relleno|barra|tinte)/.test(n)?'1-relleno':/^velo/.test(n)?'2-velo':/^(resplandor|vineta)/.test(n)?'3-resplandor':/^(vidrio|aviso)/.test(n)?'4-vidrio':/^(brillo|franja)/.test(n)?'5-brillo':'6-otros';
const gradSorted=[...gradOut].sort((a,b)=>gradFam(a.nombre).localeCompare(gradFam(b.nombre))||b.usos-a.usos);
gradSorted.forEach((p,i)=>{const cap=10,s=Math.floor(i/cap),k=i%cap,col=k%2,row=Math.floor(k/2);p.hoja={hoja:`cm-deg-${s+1}`,x:[24,196][col],y:72+row*150,w:SHEET.sw,h:SHEET.sh,lx:[24,196][col],ly:72+row*150+SHEET.sh+10,lw:SHEET.sw}});

// ---------------------------------------------------------------- @keyframes
function parseKf(text){
  const name=(/@keyframes\s+([\w-]+)/.exec(text)||[])[1];
  const body=text.slice(text.indexOf('{')+1,text.lastIndexOf('}'));
  const steps=[];const re=/([^{}]+)\{([^{}]*)\}/g;let m;
  while((m=re.exec(body))){
    const offs=m[1].split(',').map(s=>s.trim()).map(s=>s==='from'?0:s==='to'?100:parseFloat(s));
    const decl={};for(const d of m[2].split(';')){const i=d.indexOf(':');if(i>0)decl[d.slice(0,i).trim()]=d.slice(i+1).trim()}
    steps.push({offs,decl});
  }
  return {name,steps};
}
const numsIn=(s,re)=>{const m=re.exec(s||'');return m?m[1].split(',').map(x=>parseFloat(x)):null};
function famOf(kf,use){
  const st=kf.steps,P=new Set();st.forEach(s=>Object.keys(s.decl).forEach(k=>P.add(k)));
  const loops=use.some(u=>u.count==='infinite');
  const has=k=>P.has(k);
  const tfs=st.map(s=>s.decl.transform||'').join(' ')+' '+st.map(s=>s.decl.scale?'scale('+s.decl.scale+')':'').join(' ');
  const opacities=st.map(s=>s.decl.opacity).filter(x=>x!==undefined).map(parseFloat);
  const first=st.find(s=>s.offs.includes(0)),last=st.find(s=>s.offs.includes(100));
  const mid=st.filter(s=>s.offs.every(o=>o>0&&o<100));
  if(has('stroke-dashoffset')||has('stroke-dasharray'))return 'dibujo-trazo';
  if(has('stroke-width'))return 'pulso';
  if(has('background-position'))return 'brillo-barrido';
  if(has('box-shadow'))return loops?'pulso-anillo':(st.length>=3?'ignicion':'onda-anillo');
  if(has('filter'))return 'revelado';
  if(has('max-height'))return 'despliegue-fila';
  if(has('width'))return 'barrido-ancho';
  if(has('top')||/calc\(/.test(tfs))return 'barrido-linea';
  if(/perspective/.test(tfs))return 'giro-entrada';
  const rot=/rotate\(/.test(tfs);
  if(rot&&/translate/.test(tfs))return 'caida';
  if(rot&&/scale/.test(tfs))return 'giro-entrada';
  if(rot)return 'giro';
  const only=[...P].every(k=>k==='background'||k==='color');
  if(only)return 'destello';
  if(has('background')&&!has('opacity'))return 'destello';
  // pulsos: solo un escalón intermedio, o inicio = final
  const isPulse=mid.length>0&&(!first||!last||JSON.stringify(first.decl)===JSON.stringify(last.decl));
  if(isPulse){
    if(/translate/.test(tfs))return 'flotar';
    const mx=Math.max(...mid.map(s=>parseFloat(((s.decl.transform||s.decl.scale||'').match(/[\d.]+/)||[1])[0])));
    if(/scale/.test(tfs)&&mx>1&&!has('opacity'))return 'rebote';
    return 'pulso';
  }
  if(!/scaleX\(|scaleY\(/.test(tfs)&&!first&&last&&/scale\(1\)/.test(last.decl.transform||'')&&last.decl.opacity==='0')return 'onda';
  if(loops&&/scale/.test(tfs)&&!/scaleX\(|scaleY\(/.test(tfs))return 'latido';
  const sc=[...(tfs.matchAll(/scale\(([^)]*)\)/g))].map(m=>parseFloat(m[1])).concat([...(tfs.matchAll(/(?:^|\s)(\d+(?:\.\d+)?)(?=\s|$)/g))].map(m=>parseFloat(m[1])));
  const scMax=sc.length?Math.max(...sc):1,scMin=sc.length?Math.min(...sc):1;
  const hasOp=has('opacity');
  if(/scaleX\(/.test(tfs))return 'crece-x';
  if(/scaleY\(/.test(tfs))return 'crece-y';
  if(/translateY\(100%\)|translateY\(105%\)/.test(tfs)){return last&&last.decl.transform&&/100%/.test(last.decl.transform)?'hoja-baja':'hoja-sube'}
  if(/translateY\(60%\)/.test(tfs))return 'entrada-sube';
  if(/translate/.test(tfs)){
    if(st.length>=4)return 'aviso';
    const t=(first||st[0]).decl.transform||'';
    const x=numsIn(t,/translateX\(([^)]*)\)/),y=numsIn(t,/translateY\(([^)]*)\)/);
    const vx=x?x[0]:0,vy=y?y[0]:0;
    const vv=s=>{const m=/var\(--d[xy],\s*(-?[\d.]+)px\)/.exec(s||'');return m?parseFloat(m[1]):null};
    const ax=/translateX/.test(t)?(vv(t)??vx):0,ay=/translateY/.test(t)?(vv(t)??vy):0;
    if(ax>0)return 'entrada-derecha';if(ax<0)return 'entrada-izquierda';if(ay>0)return 'entrada-sube';if(ay<0)return 'entrada-baja';
  }
  if(/scale/.test(tfs)){
    const mx=mid.length?Math.max(...mid.map(s=>parseFloat(((s.decl.transform||s.decl.scale||'').match(/[\d.]+/)||[1])[0]))):1;
    if(mid.length&&mx>1.0&&scMax>1)return hasOp?'rebote-aparece':'rebote';
    if(scMax>2)return 'onda';
    if(scMax>1&&scMin===1)return hasOp?'asentar':'asentar';
    if(scMax>1)return 'asentar';
    if(scMax<=1&&scMin<1)return 'aparece-escala';
  }
  if(has('opacity')&&!has('transform')){
    if(last&&last.decl.opacity==='0')return 'fundido-salida';
    return 'fundido-entrada';
  }
  if(!first&&last&&(last.decl.opacity==='1'||last.decl.transform==='none'))return 'entrada-implicita';
  if(opacities.length&&opacities[opacities.length-1]===0)return 'fundido-salida';
  return 'otra';
}
const FAMILIES={
  'entrada-sube':['Aparece subiendo','entradas'],'entrada-baja':['Aparece bajando','entradas'],'entrada-derecha':['Aparece desde la derecha','entradas'],'entrada-izquierda':['Aparece desde la izquierda','entradas'],
  'entrada-implicita':['Entrada con el estado inicial en la regla','entradas'],'fundido-entrada':['Fundido de entrada','entradas'],'fundido-salida':['Fundido de salida','entradas'],'aviso':['Aviso: entra, espera y sale','entradas'],
  'hoja-sube':['Hoja que sube','hojas'],'hoja-baja':['Hoja que baja','hojas'],
  'rebote':['Rebote (pulsación con rebasado)','pulsaciones'],'rebote-aparece':['Rebote al aparecer','pulsaciones'],'asentar':['Se asienta (parte de más grande)','pulsaciones'],'aparece-escala':['Aparece creciendo','pulsaciones'],
  'pulso':['Pulso de opacidad o grosor (bucle)','bucles'],'pulso-anillo':['Pulso de anillo (bucle)','bucles'],'latido':['Latido','bucles'],'giro':['Giro continuo','bucles'],'brillo-barrido':['Brillo que barre (esqueleto)','bucles'],'flotar':['Flota arriba y abajo','bucles'],
  'dibujo-trazo':['Dibujo de línea o anillo','trazos'],'crece-x':['Crece en horizontal','trazos'],'crece-y':['Crece en vertical','trazos'],'barrido-ancho':['Barrido de ancho','trazos'],'barrido-linea':['Línea que barre','trazos'],
  'despliegue-fila':['Despliegue de fila','trazos'],'destello':['Destello de fondo o color','trazos'],'ignicion':['Ignición (anillo y resplandor)','trazos'],'onda-anillo':['Onda de anillo','trazos'],'onda':['Onda que se expande','trazos'],
  'caida':['Caída con giro','trazos'],'giro-entrada':['Entrada con giro','trazos'],'revelado':['Revelado de vídeo (brillo y desenfoque)','trazos'],'otra':['Sin familia','trazos'],
};
const kfParsed={};for(const [n,t] of Object.entries(inv.keyframes))if(!n.startsWith('cm-')&&!n.startsWith('cm_'))kfParsed[n]=parseKf(t);
const useByName=new Map();for(const it of animItems){const l=useByName.get(it.name)||[];l.push(it);useByName.set(it.name,l)}
const normKf=t=>t.replace(/\s+/g,' ').trim();
const famMap=new Map();
for(const [n,kf] of Object.entries(kfParsed)){
  const f=famOf(kf,useByName.get(n)||[]);
  const e=famMap.get(f)||{familia:f,nombres:[],usos:0};e.nombres.push(n);e.usos+=(useByName.get(n)||[]).length;famMap.set(f,e);
}
const kfPresets=[...famMap.values()].sort((a,b)=>b.usos-a.usos||a.familia.localeCompare(b.familia)).map(f=>{
  const miembros=f.nombres.map(n=>{
    const us=useByName.get(n)||[];
    const combos=new Map();us.forEach(u=>{const k=[u.dur,u.fn,u.delay,u.count,u.dir,u.fill].join('|');combos.set(k,(combos.get(k)||0)+1)});
    return {nombre:n,usos:us.length,css:normKf(inv.keyframes[n]),paquetes:[...new Set(us.map(u=>u.pkg))].sort(),
      parametros:[...combos.entries()].sort((a,b)=>b[1]-a[1]).map(([k,c])=>{const [dur,fn,delay,count,dir,fill]=k.split('|');return {dur,duracion:durOf.get(dur)||dur,curva:curveOf.get(fn)||fn,retardo:delay,repite:count,direccion:dir,relleno:fill,usos:c}})};
  }).sort((a,b)=>b.usos-a.usos||a.nombre.localeCompare(b.nombre));
  const canon=miembros[0];
  const demo=canon.parametros[0]?(()=>{const p=canon.parametros[0];const us=(useByName.get(canon.nombre)||[]).find(u=>u.dur===p.dur&&curveOf.get(u.fn)===p.curva)||{};return {nombre:canon.nombre,dur:p.dur,fn:us.fn||'ease',delay:p.retardo,count:p.repite,dir:p.direccion,fill:p.relleno}})():{nombre:canon.nombre,dur:'0.4s',fn:'ease',delay:'0s',count:'1',dir:'normal',fill:'both'};
  const [desc,escena]=FAMILIES[f.familia]||[f.familia,'trazos'];
  // resumen de parámetros de la familia: duraciones, curvas y repeticiones que usan sus miembros
  const dur=new Map(),cur=new Map(),rep=new Map(),fill=new Map();
  miembros.forEach(m=>m.parametros.forEach(p=>{dur.set(p.duracion,(dur.get(p.duracion)||0)+p.usos);cur.set(p.curva,(cur.get(p.curva)||0)+p.usos);rep.set(p.repite,(rep.get(p.repite)||0)+p.usos);fill.set(p.relleno,(fill.get(p.relleno)||0)+p.usos)}));
  const top=m=>[...m.entries()].sort((a,b)=>b[1]-a[1]).map(([k,n])=>`${k} (${n})`);
  return {familia:f.familia,descripcion:desc,escena,usos:f.usos,miembros,canonico:{nombre:canon.nombre,css:canon.css},demo,
    resumen:{duraciones:top(dur),curvas:top(cur),repeticiones:top(rep),relleno:top(fill)}};
});
const kfLookup={};kfPresets.forEach(f=>f.miembros.forEach(m=>{kfLookup[m.nombre]=f.familia}));

// ---------------------------------------------------------------- «Casi iguales»: pares de presets que no se unieron por poco (para que el usuario decida)
function shadowNear(a,b,k){
  if(a.length!==b.length)return null;
  const lim={x:2*k,y:2*k,blur:4*k,spread:1*k,alpha:0.08*k,chan:12*k};const d={x:0,y:0,blur:0,spread:0,alpha:0,chan:0};
  for(let i=0;i<a.length;i++){
    const A=a[i],B=b[i];if(A.inset!==B.inset||!!A.c.dyn!==!!B.c.dyn)return null;
    d.x=Math.max(d.x,Math.abs(A.x-B.x));d.y=Math.max(d.y,Math.abs(A.y-B.y));d.blur=Math.max(d.blur,Math.abs(A.blur-B.blur));d.spread=Math.max(d.spread,Math.abs(A.spread-B.spread));d.alpha=Math.max(d.alpha,Math.abs(A.c.a-B.c.a));
    if(!A.c.dyn&&!(A.c.a<0.02&&B.c.a<0.02))d.chan=Math.max(d.chan,Math.abs(A.c.r-B.c.r),Math.abs(A.c.g-B.c.g),Math.abs(A.c.b-B.c.b));
  }
  for(const key of Object.keys(lim))if(d[key]>lim[key]+1e-9)return null;
  return d;
}
const nearPairs=[];
for(let i=0;i<shadowGroups.length;i++)for(let j=i+1;j<shadowGroups.length;j++){
  const a=shadowGroups[i].canon.parsed,b=shadowGroups[j].canon.parsed;if(!a||!b)continue;
  const d=shadowNear(a,b,2);if(d&&kindOf(a)!=='nula')nearPairs.push({a:shadowPresets[i].nombre,b:shadowPresets[j].nombre,va:shadowGroups[i].canon.value,vb:shadowGroups[j].canon.value,d});
}

// ---------------------------------------------------------------- lookup para las marcas del inventario
const lookup={sombra:{},texto:{},vidrio:{},degradado:{},curva:{},duracion:{},keyframe:kfLookup};
shadowGroups.forEach((g,i)=>g.members.forEach(m=>{lookup.sombra[m.value]=shadowPresets[i].nombre}));
textGroups.forEach((g,i)=>g.members.forEach(m=>{lookup.texto[m.value]=textPresets[i].nombre}));
backdropGroups.forEach((g,i)=>g.members.forEach(m=>{lookup.vidrio['backdrop-filter: '+m.value]=backdropPresets[i].nombre}));
filterGroups.forEach((g,i)=>g.members.forEach(m=>{lookup.vidrio['filter: '+m.value]=filterPresets[i].nombre}));
gradGroups.forEach((g,i)=>g.members.forEach(m=>{lookup.degradado[m.value]=gradPresets[i].name}));
curveGroups.forEach((g,i)=>g.members.forEach(m=>{lookup.curva[m.value]=curvePresets[i].nombre}));
durGroups.forEach(g=>g.members.forEach(m=>{lookup.duracion[m.value]=durName(g.ms)}));

// ---------------------------------------------------------------- Salida: cimientos.json
const tolerancias={
  sombras:'capas iguales (mismo orden e igual inset); x e y ±2 dp; desenfoque ±4 dp; extensión ±1 dp; alfa ±0,08; canales de color ±12',
  vidrio:'mismo tipo (backdrop-filter o filter); desenfoque ±4 dp; saturación y brillo ±0,15',
  curvas:'cada coordenada de la cubic-bezier ±0,05 (las palabras clave equivalen a su cubic-bezier)',
  duraciones:'escala '+SCALE.join(', ')+' ms; se ajusta al escalón más cercano si la diferencia es ≤ 12 %; si no, token exacto',
  degradados:'mismo tipo y número de paradas; ángulo ±5°; posición ±8 puntos; alfa ±0,08; canales ±12; los de varias capas solo si son idénticos',
};
const dist=arr=>arr.flatMap(p=>p.miembros.map(m=>m.distancia||{}));
const summary={
  sombras:{valores:T.shadow.map.size,presets:shadowPresets.length},textos:{valores:T.text.map.size,presets:textPresets.length},
  vidrio:{valores:T.backdrop.map.size,presets:backdropPresets.length},filtros:{valores:T.filter.map.size,presets:filterPresets.length},
  curvas:{valores:T.curve.map.size,presets:curvePresets.length},duraciones:{valores:T.dur.map.size,presets:durPresets.length},retardos:{valores:T.delay.map.size,presets:delayPresets.length},
  degradados:{valores:T.grad.map.size,presets:gradOut.length},keyframes:{valores:Object.keys(kfParsed).length,presets:kfPresets.length},
};
const cim={version:1,hoja:SHEET,clases:CLASSES,vidrio_geometria:{...GLASS,filtros:FGRID},tolerancias,resumen:summary,
  sombras:shadowPresets,sombrasTexto:textPresets,vidrio:backdropPresets,filtros:filterPresets,curvas:curvePresets,duraciones:durPresets,retardos:delayPresets,degradados:gradOut,keyframes:kfPresets,
  hojas:{sombras:shadowSheets,texto:textSheets},casiIguales:nearPairs.map(p=>({a:p.a,b:p.b,valorA:p.va,valorB:p.vb,distancia:p.d})),lookup};
fs.writeFileSync(path.join(root,'paridad','cimientos.json'),JSON.stringify(cim,null,1));

// ---------------------------------------------------------------- Salida: bloque de datos de src/scenes-cm.js (autocontenido: el lienzo no lee archivos)
const compact=o=>JSON.stringify(o);
const dataBlock={
  hoja:SHEET,clases:CLASSES,glass:GLASS,fgrid:FGRID,
  sombras:[...shadowPresets,...textPresets].map(p=>({n:p.nombre,k:p.tipo,t:p.texto?1:0,c:p.canonico,ab:p.abreviado,u:p.usos,m:p.miembros.length,d:p.dinamico?1:0,a:p.alcance,cl:p.clase,h:p.hoja,dm:fmtDist(p.distanciaMax)})),
  vidrio:backdropPresets.map(p=>({n:p.nombre,c:p.canonico,u:p.usos,m:p.miembros.length,h:p.hoja,dm:fmtDist(p.distanciaMax)})),
  filtros:filterPresets.map(p=>({n:p.nombre,c:p.canonico,u:p.usos,m:p.miembros.length,h:p.hoja,dm:fmtDist(p.distanciaMax)})),
  degradados:gradSorted.map(p=>({n:p.nombre,t:p.tipo,c:p.canonico,u:p.usos,m:p.miembros.length,d:p.dinamico?1:0,h:p.hoja,dm:fmtDist(p.distanciaMax)})),
  curvas:curvePresets.map(p=>({n:p.nombre,c:p.canonico,desc:p.descripcion,u:p.usos,m:p.miembros.length,dt:p.duracionesTipicas,bz:p.bezier,dm:fmtDist(p.distanciaMax)})),
  duraciones:durPresets.map(p=>({n:p.nombre,ms:p.ms,ex:p.exacto?1:0,u:p.usos,m:p.miembros.length,dm:fnum(round(p.distanciaMax.pct,1))+' %'})),
  keyframes:kfPresets.map(f=>({f:f.familia,desc:f.descripcion,esc:f.escena,u:f.usos,n:f.miembros.map(m=>m.nombre),demo:f.demo,res:f.resumen})),
  hojas:{sombras:shadowSheets.map(s=>({id:s.id,t:s.titulo,cl:s.clase,n:s.items})),texto:textSheets.map(s=>({id:s.id,t:s.titulo,cl:s.clase,n:s.items}))},
  resumen:summary,tolerancias,
  grupos:{
    sombras:[...shadowPresets,...textPresets].map(p=>({n:p.nombre,k:p.tipo,c:p.canonico,u:p.usos,mm:p.miembros.map(m=>[m.valor,m.usos]),dm:fmtDist(p.distanciaMax)})),
  },
};
const scenesPath=path.join(root,'src','scenes-cm.js');
if(fs.existsSync(scenesPath)){
  let s=fs.readFileSync(scenesPath,'utf8');
  const a=s.indexOf('/*CM-DATOS-INICIO*/'),b=s.indexOf('/*CM-DATOS-FIN*/');
  if(a>=0&&b>a){
    s=s.slice(0,a)+'/*CM-DATOS-INICIO*/\n// Generado por tools/tokens-group.cjs (no editar a mano): presets, posiciones de las hojas y parámetros de movimiento.\nconst CM='+compact(dataBlock)+';\n'+s.slice(b);
    fs.writeFileSync(scenesPath,s);
  }
}

// ---------------------------------------------------------------- Salida: CIMIENTOS_GRUPOS.md
const L=[];
const esc=s=>String(s).replace(/\|/g,'\\|');
const code=s=>'`'+esc(s)+'`';
L.push('# Cimientos · grupos de presets de profundidad y movimiento','',
 'Generado por `node tools/tokens-group.cjs` desde `paridad/_inventory.json` (el CSS real del lienzo aprobado). **No se inventó ningún valor**: cada preset tiene UN valor canónico exacto, el del miembro más usado, tal cual; los demás miembros se parecen a él dentro de la tolerancia. «Filas» = filas del inventario de paridad (`paridad/<prefijo>.md`) que cubre el preset. La hoja de calibración donde se dibuja cada preset está en la columna «Hoja» (escena del lienzo y posición de la muestra en dp desde la esquina superior izquierda de la hoja de 360 × 880).','');
L.push('## Resumen','','| Familia | Valores distintos | Presets |','|---|---|---|');
for(const [k,t] of [['Sombras (box-shadow)','sombras'],['Sombras de texto','textos'],['Vidrio (backdrop-filter)','vidrio'],['Filtros (filter)','filtros'],['Curvas','curvas'],['Duraciones','duraciones'],['Retardos','retardos'],['Degradados','degradados'],['@keyframes','keyframes']])L.push(`| ${k} | ${summary[t].valores} | ${summary[t].presets} |`);
L.push('');
const maxOf=(arr,k)=>arr.reduce((m,p)=>Math.max(m,(p.distanciaMax&&p.distanciaMax[k])||0),0);
L.push('## Tolerancia máxima realmente aplicada','',
 `- Sombras: x ±${fnum(maxOf(shadowPresets,'x'))} dp, y ±${fnum(maxOf(shadowPresets,'y'))} dp, desenfoque ±${fnum(maxOf(shadowPresets,'blur'))} dp, extensión ±${fnum(maxOf(shadowPresets,'spread'))} dp, alfa ±${fnum(round(maxOf(shadowPresets,'alpha'),3))}, canal ±${fnum(maxOf(shadowPresets,'chan'))} (tope: 2 / 2 / 4 / 1 / 0,08 / 12).`,
 `- Vidrio: desenfoque ±${fnum(maxOf(backdropPresets,'blur'))} dp, saturación ±${fnum(round(maxOf(backdropPresets,'sat'),3))}, brillo ±${fnum(round(maxOf(backdropPresets,'bri'),3))} (tope: 4 / 0,15 / 0,15). Filtros: desenfoque ±${fnum(maxOf(filterPresets,'blur'))} dp, saturación ±${fnum(round(maxOf(filterPresets,'sat'),3))}, brillo ±${fnum(round(maxOf(filterPresets,'bri'),3))}.`,
 `- Curvas: coordenada ±${fnum(round(Math.max(0,...curvePresets.map(p=>p.distanciaMax.pos||0)),3))} (tope 0,05).`,
 `- Duraciones: hasta ${fnum(round(Math.max(0,...durPresets.map(p=>p.distanciaMax.pct)),1))} % (tope 12 %); retardos: hasta ${fnum(round(Math.max(0,...delayPresets.map(p=>p.distanciaMax.pct)),1))} %.`,
 `- Degradados: ángulo ±${fnum(maxOf(gradOut,'ang'))}°, posición ±${fnum(maxOf(gradOut,'pos'))} puntos, alfa ±${fnum(round(maxOf(gradOut,'alpha'),3))}, canal ±${fnum(maxOf(gradOut,'chan'))} (tope 5 / 8 / 0,08 / 12).`,'');
L.push('## Reglas y decisiones de criterio','',
 '- **Canónico = el más usado, tal cual.** Se ordenan los valores distintos de más a menos usados (empate: el que aparece antes en el CSS); el primero sin grupo es el canónico del grupo y absorbe a todo valor sin grupo que esté dentro de la tolerancia *de él*. No hay cadenas (A≈B≈C) ni promedios.',
 '- **Sombras:** mismas capas (mismo orden e igual `inset`). El color dinámico del módulo (`var(--c)`, `var(--zc)`, `var(--wc)`: lo fija cada escena) solo equivale a otro dinámico; su hoja usa un color de muestra (`--c` cardio `#8fb2ff`, `--zc` `#87C9A5`). Una sombra con alfa 0 en todas sus capas es la sombra «ninguna» (destino de transiciones). `text-shadow` va aparte (`texto-*`).',
 '- **Nombres:** por papel visto en los selectores (`hoja`, `isla-vidrio`, `flotante`, `modal`, `dialogo`, `menu`, `aviso`…) y, si el papel no es claro, por estructura (`caida-<y>-<desenfoque>`, `anillo-<color>-<extensión>`, `interior-<color>-…`, `resplandor-<color>-<desenfoque>`). Si dos presets comparten nombre se numeran por uso (`dialogo-1`, `dialogo-2`).',
 '- **Curvas:** las palabras clave se comparan por su `cubic-bezier` (`ease-in` ≈ `cubic-bezier(.4,0,1,1)`, p. ej.); `steps(1)` es suyo. El nombre y la descripción de cada curva salen de dónde se usa.',
 '- **Duraciones:** escala `'+SCALE.join(' · ')+'` ms (el 90 se añadió a la lista del encargo para cubrir 0,09 s). Lo que no encaja a ≤ 12 % queda exacto: '+durPresets.filter(p=>p.exacto).map(p=>p.ms).join(', ')+' ms. Un retardo `0s` no es preset.',
 '- **Degradados:** se clasifican por papel (`relleno-*`, `velo-*` = de transparente a color, `resplandor-*` = radial de borde transparente, `vidrio-relleno-*`, `brillo-esqueleto`, `franja-*`, `rayas-*`, `rejilla`, `compuesto`). Los de varias capas (`background-image` con comas) solo agrupan si son idénticos.',
 '- **@keyframes:** una entrada por familia estructural; el canónico es el `@keyframes` más usado de la familia y la demostración de la hoja usa su combinación de duración y curva más usada. Los `@keyframes` de las hojas de calibración (`cm-*`) no cuentan.',
 '- **Inventario ampliado:** los atajos `animation:`, `transition:` y `background:` escritos con `var()` dejaban vacíos sus valores en el CSS calculado y el inventario los perdía (+32 animaciones, +25 transiciones y +5 degradados; entre ellos el muelle `cubic-bezier(.3,1.5,.5,1)`). `src/boot.js` ahora guarda su texto y `tools/inventory.cjs` los resuelve con las variables del CSS.','');

const shadowTable=(list,title)=>{
  L.push(`## ${title}`,'','| Preset | Tipo | Valor canónico (exacto) | Filas | Miembros que agrupa (filas) | Mayor distancia | Hoja y posición |','|---|---|---|---|---|---|---|');
  for(const p of list){
    const mem=p.miembros.length>1?p.miembros.map(m=>`${code(m.valor)} (${m.usos})`).join('<br>'):'(solo él)';
    const h=p.hoja?`${p.hoja.hoja} · x ${p.hoja.x}, y ${p.hoja.y} · ${p.hoja.w}×${p.hoja.h}`:'';
    L.push(`| **${p.nombre}** | ${p.tipo}${p.dinamico?' · dinámico':''} | ${code(p.canonico)} | ${p.usos} | ${mem} | ${fmtDist(p.distanciaMax)} | ${h} |`);
  }
  L.push('');
};
for(const k of KIND_ORDER){const l=shadowPresets.filter(p=>p.tipo===k);if(l.length)shadowTable(l,`Sombras · ${KIND_TITLE[k].toLowerCase()} (${l.length})`)}
shadowTable(textPresets,`Sombras de texto (${textPresets.length})`);
const filterTable=(list,title)=>{
  L.push(`## ${title}`,'','| Preset | Valor canónico (exacto) | Filas | Miembros | Mayor distancia | Hoja y posición |','|---|---|---|---|---|---|');
  for(const p of list){
    const mem=p.miembros.length>1?p.miembros.map(m=>`${code(m.valor)} (${m.usos})`).join('<br>'):'(solo él)';
    L.push(`| **${p.nombre}** | ${code(p.canonico)} | ${p.usos} | ${mem} | ${fmtDist(p.distanciaMax)} | ${p.hoja.hoja} · x ${p.hoja.x}, y ${p.hoja.y} · ${p.hoja.w}×${p.hoja.h} |`);
  }
  L.push('');
};
filterTable(backdropPresets,`Vidrio · backdrop-filter (${backdropPresets.length})`);
filterTable(filterPresets,`Filtros · filter (${filterPresets.length})`);
L.push(`## Curvas (${curvePresets.length})`,'','| Preset | Valor canónico | Filas | Para qué | Pares curva-duración más usados | Miembros | Mayor distancia |','|---|---|---|---|---|---|---|');
for(const p of curvePresets)L.push(`| **${p.nombre}** | ${code(p.canonico)} | ${p.usos} | ${esc(p.descripcion)} | ${p.duracionesTipicas.map(d=>d.ms+' ms ('+d.usos+')').join(', ')||'—'} | ${p.miembros.length>1?p.miembros.map(m=>`${code(m.valor)} (${m.usos})`).join('<br>'):'(solo él)'} | ${fmtDist(p.distanciaMax)} |`);
L.push('');
const durTable=(list,title)=>{
  L.push(`## ${title}`,'','| Preset | Ms | Filas | Valores que agrupa (filas) | Mayor distancia |','|---|---|---|---|---|');
  for(const p of list)L.push(`| **${p.nombre}**${p.exacto?' (exacto)':''} | ${p.ms} | ${p.usos} | ${p.miembros.map(m=>`${code(m.valor)} (${m.usos})`).join(', ')} | ${fnum(round(p.distanciaMax.pct,1))} % |`);
  L.push('');
};
durTable(durPresets,`Duraciones (${durPresets.length})`);durTable(delayPresets,`Retardos (${delayPresets.length})`);
L.push(`## Degradados (${gradOut.length})`,'','| Preset | Tipo | Valor canónico (exacto) | Filas | Miembros | Mayor distancia | Hoja y posición |','|---|---|---|---|---|---|---|');
for(const p of gradSorted)L.push(`| **${p.nombre}** | ${p.tipo}${p.dinamico?' · dinámico':''} | ${code(p.canonico.length>230?p.canonico.slice(0,227)+'…':p.canonico)} | ${p.usos} | ${p.miembros.length>1?p.miembros.map(m=>`${code(m.valor.length>120?m.valor.slice(0,117)+'…':m.valor)} (${m.usos})`).join('<br>'):'(solo él)'} | ${fmtDist(p.distanciaMax)} | ${p.hoja.hoja} · x ${p.hoja.x}, y ${p.hoja.y} · ${p.hoja.w}×${p.hoja.h} |`);
L.push('');
L.push(`## @keyframes por familia (${kfPresets.length} familias de ${Object.keys(kfParsed).length} @keyframes)`,'','| Familia | Qué hace | Canónico | Agrupa | Duraciones (filas) | Curvas | Repite | Relleno |','|---|---|---|---|---|---|---|---|');
for(const f of kfPresets)L.push(`| **${f.familia}** | ${esc(f.descripcion)} | ${code(f.canonico.nombre)} | ${f.miembros.map(m=>m.nombre).join(', ')} | ${f.resumen.duraciones.slice(0,5).join(', ')} | ${f.resumen.curvas.join(', ')} | ${f.resumen.repeticiones.join(', ')} | ${f.resumen.relleno.join(', ')} |`);
L.push('');
L.push('### Texto exacto del `@keyframes` canónico de cada familia','');
for(const f of kfPresets)L.push(`- **${f.familia}** · ${code(f.canonico.css)}`);
L.push('');
L.push(`## Casi iguales: pares que NO se unieron por poco (${nearPairs.length})`,'','Dentro del doble de la tolerancia pero fuera de ella. **No se arreglan aquí**: son candidatos a unificar si el usuario lo decide (cambiar un valor del lienzo y volver a generar).','','| Preset A | Preset B | Valor A | Valor B | Diferencia |','|---|---|---|---|---|');
for(const p of nearPairs)L.push(`| ${p.a} | ${p.b} | ${code(p.va)} | ${code(p.vb)} | ${fmtDist(p.d)} |`);
L.push('');
fs.writeFileSync(path.join(root,'paridad','CIMIENTOS_GRUPOS.md'),L.join('\n'));
console.log('cimientos: '+Object.entries(summary).map(([k,v])=>`${k} ${v.valores}→${v.presets}`).join(' · '));
console.log('hojas de sombras: '+shadowSheets.map(s=>s.id).join(', ')+' · texto: '+textSheets.map(s=>s.id).join(', '));
console.log('casi iguales: '+nearPairs.length+' · familias de keyframes: '+kfPresets.map(f=>f.familia+'('+f.miembros.length+')').join(' '));
