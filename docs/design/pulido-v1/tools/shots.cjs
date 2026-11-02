// QA del lienzo sin navegador visible: Chrome headless (el de Playwright) renderiza prototipos.html a PNG o a JSON.
//   node tools/shots.cjs check [--prefix=ed]                → ejecuta cada escena y lista errores, NaN, undefined, ids repetidos (con prefijo, solo falla por las tuyas)
//   node tools/shots.cjs list                               → ids de todas las escenas
//   node tools/shots.cjs shot <id> [--size=360x880] [--fs=1.3] [--t=<ms>] [--out=ruta.png]   (--t congela las animaciones CSS en ese instante)
//   node tools/shots.cjs gallery <pestaña> [--from=0] [--count=4] [--size=360x880] [--fs=1] [--out=ruta.png]
// Todas aceptan --build=<carpeta> (la de  node build.cjs --out=<carpeta>; sin ella, el prototipos.html de la raíz).
// Los PNG van a shots/ (fuera de git) y se miran con la herramienta Read. Antes: node build.cjs --out=shots/<prefijo>
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const root=path.resolve(__dirname,'..');
const buildArg=process.argv.find(a=>a.startsWith('--build='));
const html=path.join(buildArg?path.resolve(buildArg.slice(8)):root,'prototipos.html');
if(!fs.existsSync(html)){console.error('Falta '+html+': ejecuta antes  node build.cjs --out=<carpeta>');process.exit(1)}
function chromeExe(){
  const base=path.join(process.env.LOCALAPPDATA||'','ms-playwright');
  const dirs=fs.existsSync(base)?fs.readdirSync(base).filter(d=>d.startsWith('chromium_headless_shell-')).sort().reverse():[];
  for(const d of dirs){const p=path.join(base,d,'chrome-headless-shell-win64','chrome-headless-shell.exe');if(fs.existsSync(p))return p}
  throw new Error('No encuentro chrome-headless-shell en '+base);
}
const args=process.argv.slice(2);
const cmd=args[0];
const opt=(k,d)=>{const a=args.find(x=>x.startsWith('--'+k+'='));return a?a.split('=')[1]:d};
const reduced=args.includes('--rm');
function run(extra,query,timeout=90000){
  const url=pathToFileURL(html).href+'?'+query;
  const prof=fs.mkdtempSync(path.join(os.tmpdir(),'pl-chrome-'));
  try{
    const r=spawnSync(chromeExe(),['--headless','--disable-gpu','--hide-scrollbars','--no-sandbox','--allow-file-access-from-files','--force-device-scale-factor=1',...(reduced?['--force-prefers-reduced-motion']:[]),'--user-data-dir='+prof,...extra,url],{encoding:'utf8',timeout,maxBuffer:64*1024*1024});
    return r;
  }finally{try{fs.rmSync(prof,{recursive:true,force:true})}catch(e){}}
}
function dump(query){
  const r=run(['--virtual-time-budget=3000','--dump-dom'],query);
  return r.stdout||'';
}
function pre(domText,id){
  const m=new RegExp('<pre id="'+id+'">([\\s\\S]*?)</pre>').exec(domText);
  if(!m)throw new Error('No hay <pre id="'+id+'"> en la salida (¿error de JavaScript en el lienzo?). Salida: '+domText.slice(0,300));
  const txt=m[1].replace(/&quot;/g,'"').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&');
  return JSON.parse(txt);
}
const shotsDir=path.join(root,'shots');fs.mkdirSync(shotsDir,{recursive:true});
if(cmd==='list'){
  const l=pre(dump('list=1'),'L');
  for(const s of l)console.log(s.tab.padEnd(14)+' '+s.id+(s.doc?'  [doc]':'')+'  — '+s.title);
}else if(cmd==='check'){
  const l=pre(dump('check=1'),'C');
  const prefix=opt('prefix','');
  const bad=l.filter(s=>!s.ok);
  const mine=bad.filter(s=>s.id.startsWith(prefix)),other=bad.filter(s=>!s.id.startsWith(prefix));
  console.log(l.length+' escenas · '+mine.length+' con problemas'+(prefix?' (prefijo «'+prefix+'»)':'')+(other.length?' · '+other.length+' ajenas con problemas':''));
  for(const s of mine)console.log('  ✗ '+s.tab+' / '+s.id+': '+s.err);
  for(const s of other)console.log('  · ajena (no es tuya): '+s.id+': '+s.err);
  process.exit(mine.length?3:0);
}else if(cmd==='shot'){
  const id=args[1];if(!id){console.error('Falta el id de la escena');process.exit(1)}
  const size=opt('size','360x880'),fs_=opt('fs','1'),t_=opt('t','');
  const [w,h]=size.split('x').map(Number);
  const out=opt('out',path.join(shotsDir,id+(fs_!=='1'?'-fs'+fs_:'')+(size!=='360x880'?'-'+size:'')+(t_!==''?'-t'+t_:'')+'.png'));
  const r=run(['--window-size='+w+','+h,'--virtual-time-budget=4500','--screenshot='+out],'solo=1&scene='+encodeURIComponent(id)+'&size='+size+'&fs='+fs_+(t_!==''?'&t='+t_:''));
  console.log(fs.existsSync(out)?out:'No se generó la captura: '+(r.stderr||'').slice(0,300));
}else if(cmd==='gallery'){
  const tab=args[1];if(!tab){console.error('Falta la pestaña');process.exit(1)}
  const size=opt('size','360x880'),fs_=opt('fs','1'),from=Number(opt('from','0')),count=Number(opt('count','4'));
  const [w,h]=size.split('x').map(Number);
  const cols=Math.min(count,Number(opt('cols',String(count))));
  const W=cols*(w+20)+32,H=Math.ceil(count/cols)*(h+52)+32;
  const out=opt('out',path.join(shotsDir,'gal-'+tab+'-'+from+'-'+count+(fs_!=='1'?'-fs'+fs_:'')+'.png'));
  const r=run(['--window-size='+W+','+H,'--virtual-time-budget=5000','--screenshot='+out],'gallery='+encodeURIComponent(tab)+'&from='+from+'&count='+count+'&size='+size+'&fs='+fs_);
  console.log(fs.existsSync(out)?out:'No se generó la captura: '+(r.stderr||'').slice(0,300));
}else{
  console.log('Uso: node tools/shots.cjs check | list | shot <id> | gallery <pestaña>   (ver la cabecera del archivo)');
}
