// Inventario de profundidad y movimiento del lienzo → paridad/<prefijo>.md, paridad/_tokens.md y paridad/_resumen.md.
// Saca del CSS REAL del lienzo cada sombra, desenfoque, degradado, animación, transición y transformación (más el movimiento hecho en JavaScript de
// cada src/scenes-*.js), para que implementar «1:1» no dependa de que alguien se acuerde de las animaciones y las sombras: cada fila es algo que la app
// debe hacer igual, y no se borra: se marca [x] con archivo:línea, o [~] con la desviación y una captura lado a lado.
//   node tools/inventory.cjs [--build=<carpeta con prototipos.html>]
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const {expandProps,splitTop}=require('./cssvars.cjs');
const root=path.resolve(__dirname,'..');
const buildArg=process.argv.find(a=>a.startsWith('--build='));
const html=path.join(buildArg?path.resolve(buildArg.slice(8)):root,'prototipos.html');
if(!fs.existsSync(html)){console.error('Falta '+html+': ejecuta antes  node build.cjs');process.exit(1)}
function chromeExe(){
  const base=path.join(process.env.LOCALAPPDATA||'','ms-playwright');
  const dirs=fs.existsSync(base)?fs.readdirSync(base).filter(d=>d.startsWith('chromium_headless_shell-')).sort().reverse():[];
  for(const d of dirs){const p=path.join(base,d,'chrome-headless-shell-win64','chrome-headless-shell.exe');if(fs.existsSync(p))return p}
  throw new Error('No encuentro chrome-headless-shell en '+base);
}
function dumpInventory(){
  const prof=fs.mkdtempSync(path.join(os.tmpdir(),'pl-inv-'));
  try{
    const r=spawnSync(chromeExe(),['--headless','--disable-gpu','--no-sandbox','--allow-file-access-from-files','--user-data-dir='+prof,'--virtual-time-budget=6000','--dump-dom',pathToFileURL(html).href+'?inventory=1'],{encoding:'utf8',timeout:180000,maxBuffer:256*1024*1024});
    const m=/<pre id="V">([\s\S]*?)<\/pre>/.exec(r.stdout||'');
    if(!m)throw new Error('No salió el inventario (¿error de JavaScript en el lienzo?): '+(r.stdout||'').slice(0,300));
    return JSON.parse(m[1].replace(/&quot;/g,'"').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&'));
  }finally{try{fs.rmSync(prof,{recursive:true,force:true})}catch(e){}}
}
const data=dumpInventory();
const norm=v=>String(v).replace(/\s+/g,' ').trim();
const CATS={S:'Sombras',V:'Vidrio, desenfoques y filtros',G:'Degradados y resplandores',A:'Animaciones (@keyframes)',T:'Transiciones',X:'Transformaciones, máscaras y mezcla',J:'Movimiento y estilos hechos en JavaScript'};
const byPkg={};
// El paquete «cm» (Cimientos) son las hojas de calibración de los propios presets: no es una pantalla de la app ni cuenta para el catálogo.
const SKIP_PKG=new Set(['cm']);
const vars=data.vars||{};
for(const k of Object.keys(data.keyframes))if(k.startsWith('cm-')||k.startsWith('cm_'))delete data.keyframes[k];
for(const r of data.rows){
  if(SKIP_PKG.has(r.pkg))continue;
  const pkg=r.pkg,list=(byPkg[pkg]=byPkg[pkg]||{rows:[],seen:new Set(),kf:new Set()});
  const add=(cat,desc,extra)=>{const key=cat+'|'+r.selector+'|'+desc;if(list.seen.has(key))return;list.seen.add(key);list.rows.push({cat,selector:r.selector,desc,base:r.base,scenes:r.scenes,...extra})};
  const p=expandProps(r.props,vars);
  if(p['box-shadow']&&p['box-shadow']!=='none')add('S','box-shadow: '+norm(p['box-shadow']),{prop:'box-shadow',value:norm(p['box-shadow'])});
  if(p['text-shadow']&&p['text-shadow']!=='none')add('S','text-shadow: '+norm(p['text-shadow']),{prop:'text-shadow',value:norm(p['text-shadow'])});
  const bf=p['backdrop-filter']||p['-webkit-backdrop-filter'];
  if(bf&&bf!=='none')add('V','backdrop-filter: '+norm(bf),{prop:'backdrop-filter',value:norm(bf)});
  if(p['filter']&&p['filter']!=='none')add('V','filter: '+norm(p['filter']),{prop:'filter',value:norm(p['filter'])});
  if(p['background-image']&&/gradient\(/.test(p['background-image']))add('G','background-image: '+norm(p['background-image']),{prop:'gradient',value:norm(p['background-image'])});
  if(p['animation-name']&&p['animation-name']!=='none'){
    const names=norm(p['animation-name']).split(',').map(s=>s.trim());names.forEach(n=>list.kf.add(n));
    add('A',`animation: ${norm(p['animation-name'])} · ${norm(p['animation-duration']||'')} · ${norm(p['animation-timing-function']||'')} · retardo ${norm(p['animation-delay']||'0s')} · repite ${norm(p['animation-iteration-count']||'1')} · ${norm(p['animation-direction']||'normal')} · ${norm(p['animation-fill-mode']||'none')}`,{prop:'animation',value:norm(p['animation-timing-function']||''),dur:norm(p['animation-duration']||''),delay:norm(p['animation-delay']||''),names,fns:splitTop(p['animation-timing-function']||''),durs:splitTop(p['animation-duration']||'')});
  }
  if(p['transition-property']&&p['transition-property']!=='none'&&!/^0s(, 0s)*$/.test(norm(p['transition-duration']||'0s')))
    add('T',`transition: ${norm(p['transition-property'])} · ${norm(p['transition-duration']||'')} · ${norm(p['transition-timing-function']||'')} · retardo ${norm(p['transition-delay']||'0s')}`,{prop:'transition',value:norm(p['transition-timing-function']||''),dur:norm(p['transition-duration']||''),delay:norm(p['transition-delay']||''),fns:splitTop(p['transition-timing-function']||''),durs:splitTop(p['transition-duration']||'')});
  if(p['transform']&&p['transform']!=='none')add('X','transform: '+norm(p['transform']),{prop:'transform',value:norm(p['transform'])});
  for(const k of ['mix-blend-mode','mask-image','-webkit-mask-image','mask-size','clip-path','will-change'])if(p[k]&&p[k]!=='none'&&p[k]!=='normal'&&p[k]!=='auto')add('X',k+': '+norm(p[k]),{prop:k,value:norm(p[k])});
}
// Movimiento y estilos hechos en JavaScript: se lee cada src/scenes-<prefijo>.js
const JS_PATTERNS=[[/\.animate\(/,'WAAPI .animate()'],[/requestAnimationFrame/,'requestAnimationFrame'],[/setInterval\(/,'setInterval'],[/setTimeout\(/,'setTimeout'],[/\.style\.(transition|animation|transform|boxShadow|filter|opacity)\b/,'estilo en línea desde JS'],[/(box-shadow|text-shadow|backdrop-filter|filter|animation|transition)\s*:\s*[^;"`]+/,'declaración CSS dentro del marcado']];
for(const f of fs.readdirSync(path.join(root,'src')).filter(f=>/^scenes-.*\.js$/.test(f))){
  const pkg=f.replace(/^scenes-|\.js$/g,'');
  if(pkg==='aa-resumen'||SKIP_PKG.has(pkg))continue;
  const lines=fs.readFileSync(path.join(root,'src',f),'utf8').split('\n');
  const list=(byPkg[pkg]=byPkg[pkg]||{rows:[],seen:new Set(),kf:new Set()});
  lines.forEach((ln,i)=>{
    for(const [re,label] of JS_PATTERNS){
      const m=re.exec(ln);
      if(m){const snippet=norm(ln).slice(0,150);const key='J|'+f+'|'+i;if(list.seen.has(key))break;list.seen.add(key);list.rows.push({cat:'J',selector:`${f}:${i+1}`,desc:label+' · '+snippet,base:false,scenes:[]});break}
    }
  });
}
// Salida
const outDir=path.join(root,'paridad');fs.mkdirSync(outDir,{recursive:true});
const today=new Date().toISOString().slice(0,10);
// Al regenerar se conservan «Compose» y «Estado» de las filas que siguen existiendo (clave: selector + valor) y nunca se toca DESVIACIONES.md.
const previous=pkg=>{
  const f=path.join(outDir,pkg+'.md'),m=new Map();
  if(!fs.existsSync(f))return m;
  for(const ln of fs.readFileSync(f,'utf8').split('\n')){
    if(!ln.startsWith('| '+pkg+'-'))continue;
    const c=ln.split('|').map(x=>x.trim());
    m.set(c[2].replace(/`/g,'').replace(/ \(base\)$/,'')+'|'+c[3].replace(/ → preset .*$/,''),{compose:c[5]||'',estado:c[6]||'[ ]'});
  }
  return m;
};
// Marcas de preset: si existe paridad/cimientos.json (node tools/tokens-group.cjs), cada fila de sombra, vidrio, degradado, animación o transición lleva
// al final de «Valor exacto» el sufijo « → preset <nombre>». previous() lo ignora al fusionar estados, así que no se pierde ningún [x].
const cimPath=path.join(outDir,'cimientos.json');
const CIM=fs.existsSync(cimPath)?JSON.parse(fs.readFileSync(cimPath,'utf8')):null;
const uniq=a=>[...new Set(a.filter(Boolean))];
const mark=r=>{
  if(!CIM||!CIM.lookup)return '';
  const L=CIM.lookup;let labels=[];
  if(r.cat==='S')labels=[(L.sombra[r.value])||(L.texto[r.value])];
  else if(r.cat==='V')labels=[L.vidrio[r.prop+': '+r.value]];
  else if(r.cat==='G')labels=[L.degradado[r.value]];
  else if(r.cat==='A'||r.cat==='T'){
    const fams=r.cat==='A'?uniq((r.names||[]).map(n=>L.keyframe[n])):[];
    const fns=uniq((r.fns||[]).map(c=>L.curva[c]));
    const durs=uniq((r.durs||[]).map(d=>L.duracion[d]));
    labels=[...fams,...(fns.length?['curva '+fns.join('/')]:[]),...(durs.length?['dur '+durs.join('/')]:[])];
  }
  labels=uniq(labels);
  return labels.length?' → preset '+labels.join(' · '):'';
};
const summary=[];const tokenStats={shadow:new Map(),glass:new Map(),ease:new Map(),dur:new Map(),grad:new Map()};
const bump=(map,key,pkg)=>{const e=map.get(key)||{n:0,pkgs:new Set()};e.n++;e.pkgs.add(pkg);map.set(key,e)};
for(const pkg of Object.keys(byPkg).sort()){
  const L=byPkg[pkg];if(!L.rows.length)continue;
  const counts={};const idn={};
  const body=[];const prev=previous(pkg);
  for(const cat of Object.keys(CATS)){
    const rows=L.rows.filter(r=>r.cat===cat).sort((a,b)=>a.selector.localeCompare(b.selector));
    counts[cat]=rows.length;if(!rows.length)continue;
    body.push(`## ${CATS[cat]} (${rows.length})`,'','| ID | Dónde (selector o archivo:línea) | Valor exacto | Escenas | Compose (archivo:línea) | Estado |','|---|---|---|---|---|---|');
    for(const r of rows){
      idn[cat]=(idn[cat]||0)+1;const id=`${pkg}-${cat}${String(idn[cat]).padStart(2,'0')}`;
      const old=prev.get(r.selector.replace(/\|/g,'\\|')+'|'+r.desc.replace(/\|/g,'\\|'))||{compose:'',estado:'[ ]'};
      body.push(`| ${id} | \`${r.selector.replace(/\|/g,'\\|')}\`${r.base?' (base)':''} | ${(r.desc+mark(r)).replace(/\|/g,'\\|')} | ${(r.scenes||[]).join(', ')||'—'} | ${old.compose} | ${old.estado} |`);
      if(r.prop==='box-shadow'||r.prop==='text-shadow')bump(tokenStats.shadow,r.value,pkg);
      else if(r.prop==='backdrop-filter'||r.prop==='filter')bump(tokenStats.glass,r.prop+': '+r.value,pkg);
      else if(r.prop==='gradient')bump(tokenStats.grad,r.value,pkg);
      else if(r.prop==='animation'||r.prop==='transition'){if(r.value)bump(tokenStats.ease,r.value,pkg);if(r.dur)bump(tokenStats.dur,r.dur+(r.delay&&r.delay!=='0s'?' / retardo '+r.delay:''),pkg)}
    }
    body.push('');
  }
  const kf=[...L.kf].filter(n=>data.keyframes[n]);
  if(kf.length){body.push(`## @keyframes usados (${kf.length})`,'');for(const n of kf)body.push('```css',norm(data.keyframes[n]),'```','');}
  const head=[`# Paridad de profundidad y movimiento · \`${pkg}\` (${today})`,'',
    'Generado por `node tools/inventory.cjs` desde el CSS real del lienzo aprobado. **Cada fila es algo que el diseño hace y que la app debe hacer igual**: misma forma, mismos valores.',
    'Las filas no se borran. Estado: `[ ]` pendiente · `[x]` hecho (en «Compose», archivo:línea de la implementación) · `[~]` desviación (la razón y una captura lado a lado). «(base)» = regla de las hojas comunes que usan las escenas de este paquete.',
    'Una fila de la tabla de **Vidrio** o de **Sombras** que no pasa por los cimientos de `ui/design/depth` o de `ui/design/motion` (`docs/design/pulido-v1/PROTOCOLO_1A1.md`) es una desviación.','',
    `Resumen: ${Object.keys(CATS).map(c=>`${CATS[c].split(' ')[0].toLowerCase()} ${counts[c]||0}`).join(' · ')} · escenas del paquete: ${data.scenes[pkg]||0}`,''];
  fs.writeFileSync(path.join(outDir,pkg+'.md'),head.concat(body).join('\n'));
  summary.push({pkg,counts,scenes:data.scenes[pkg]||0});
}
const topN=(map,n)=>[...map.entries()].sort((a,b)=>b[1].n-a[1].n).slice(0,n);
const tk=['# Tokens de profundidad y movimiento del lienzo (catálogo finito para los cimientos)','',
  'Los valores distintos que usa el lienzo aprobado. Los **cimientos** (`ui/design/depth` y `ui/design/motion`) implementan cada uno una vez; los paquetes solo los usan.','',
  `## Sombras (${tokenStats.shadow.size} valores distintos)`,'','| Usos | Paquetes | Valor |','|---|---|---|',...topN(tokenStats.shadow,60).map(([v,e])=>`| ${e.n} | ${[...e.pkgs].join(' ')} | \`${v.replace(/\|/g,'\\|')}\` |`),'',
  `## Vidrio, desenfoques y filtros (${tokenStats.glass.size} valores distintos)`,'','| Usos | Paquetes | Valor |','|---|---|---|',...topN(tokenStats.glass,40).map(([v,e])=>`| ${e.n} | ${[...e.pkgs].join(' ')} | \`${v.replace(/\|/g,'\\|')}\` |`),'',
  `## Curvas de animación y transición (${tokenStats.ease.size} distintas)`,'','| Usos | Paquetes | Curva |','|---|---|---|',...topN(tokenStats.ease,40).map(([v,e])=>`| ${e.n} | ${[...e.pkgs].join(' ')} | \`${v.replace(/\|/g,'\\|')}\` |`),'',
  `## Duraciones y retardos (${tokenStats.dur.size} distintos)`,'','| Usos | Paquetes | Duración |','|---|---|---|',...topN(tokenStats.dur,40).map(([v,e])=>`| ${e.n} | ${[...e.pkgs].join(' ')} | \`${v}\` |`),'',
  `## Degradados (${tokenStats.grad.size} distintos; los 30 más usados)`,'','| Usos | Paquetes | Valor |','|---|---|---|',...topN(tokenStats.grad,30).map(([v,e])=>`| ${e.n} | ${[...e.pkgs].join(' ')} | \`${v.replace(/\|/g,'\\|').slice(0,200)}\` |`),''];
fs.writeFileSync(path.join(outDir,'_tokens.md'),tk.join('\n'));
const tot={};for(const s of summary)for(const c of Object.keys(CATS))tot[c]=(tot[c]||0)+(s.counts[c]||0);
const rs=['# Resumen del inventario de paridad','',`Generado ${today}. Total de filas: **${Object.values(tot).reduce((a,b)=>a+b,0)}**.`,'','| Paquete | Escenas | '+Object.values(CATS).map(c=>c.split(' ')[0]).join(' | ')+' |','|---|---|'+Object.keys(CATS).map(()=>'---').join('|')+'|',...summary.map(s=>`| ${s.pkg} | ${s.scenes} | ${Object.keys(CATS).map(c=>s.counts[c]||0).join(' | ')} |`),`| **Total** | | ${Object.keys(CATS).map(c=>tot[c]||0).join(' | ')} |`,''];
fs.writeFileSync(path.join(outDir,'_resumen.md'),rs.join('\n'));
// Los datos en bruto (reglas CSS con sombras, vidrio, degradados, animaciones…) para las herramientas que agrupan o comparan; se regenera, no va a git.
fs.writeFileSync(path.join(outDir,'_inventory.json'),JSON.stringify(data));
console.log('paridad/: '+summary.length+' paquetes · '+Object.entries(tot).map(([c,n])=>CATS[c].split(' ')[0].toLowerCase()+' '+n).join(' · '));
console.log('distintos: sombras '+tokenStats.shadow.size+' · vidrio/filtros '+tokenStats.glass.size+' · curvas '+tokenStats.ease.size+' · duraciones '+tokenStats.dur.size+' · degradados '+tokenStats.grad.size+' · @keyframes '+Object.keys(data.keyframes).length);
