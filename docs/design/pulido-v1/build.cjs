// Recompone el lienzo «Pulido v1» (29-oct-2026) con las fuentes reales de KPKN.
// Motor de escenas = el de Registros v1 (../registros-v1/src/app.js + base.js); estilos base = Program Detail v1 + extra.css de Registros.
// Cada src/scenes-*.js se envuelve en su propia función: los `const` de un paquete no chocan con los de otro.
// Cada src/*.css (salvo pulido.css) se añade al final, en orden alfabético. Un archivo con error de sintaxis se OMITE y se avisa.
// artifact.html: sin esqueleto, para publicar; prototipos.html: archivo completo. Las capturas de hoy (hoy/*.jpg) van incrustadas.
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'../../..');
const srcDir=path.join(__dirname,'src');
const own=f=>fs.readFileSync(path.join(srcDir,f),'utf8');
const pd=f=>fs.readFileSync(path.join(__dirname,'../program-detail-v1/src',f),'utf8');
const reg=f=>fs.readFileSync(path.join(__dirname,'../registros-v1/src',f),'utf8');
const font=(file,family,weight)=>`@font-face{font-family:${family};font-style:normal;font-weight:${weight};font-display:swap;src:url(data:font/ttf;base64,${fs.readFileSync(path.join(root,'android-native/app/src/main/res/font',file)).toString('base64')}) format('truetype')}`;
const fonts=[font('wizard_body_regular.ttf','Inter',400),font('wizard_body_medium.ttf','Inter',500),font('wizard_body_semibold.ttf','Inter',600),font('syne_bold.ttf','Syne',700),font('syne_extrabold.ttf','Syne',800)].join('\n');
const must=(text,piece,label)=>{if(!text.includes(piece))throw new Error('No encuentro '+label);return text};
const warnings=[];
// El motor de escenas de Registros con nuestras pestañas y la captura de hoy en las notas.
let app=reg('app.js');
app=app.replace(/const TABS=\[[\s\S]*?\n\];/,()=>own('tabs.js').trim());
must(app,'PL_TABS','las pestañas');
const notesP="${(n.p||[]).map(p=>`<p>${p}</p>`).join('')}";
must(app,notesP,'las notas');
app=app.replace(notesP,()=>"${n.img?`<figure class=\"pl-hoy\"><img src=\"${IMG[n.img]}\" alt=\"Hoy en tu teléfono\"><figcaption>${n.imgCap||'Hoy, en tu teléfono.'}</figcaption></figure>`:''}"+notesP);
const IMG={};
const hoyDir=path.join(__dirname,'hoy');
if(fs.existsSync(hoyDir))for(const f of fs.readdirSync(hoyDir).filter(f=>f.endsWith('.jpg')))IMG[f.replace(/\.jpg$/,'')]='data:image/jpeg;base64,'+fs.readFileSync(path.join(hoyDir,f)).toString('base64');
// Escenas: un archivo por paquete, cada uno en su función.
const sceneFiles=fs.readdirSync(srcDir).filter(f=>/^scenes-.*\.js$/.test(f)).sort();
const scenes=[];
for(const f of sceneFiles){
  const code=own(f);
  try{new vm.Script(code,{filename:f})}catch(e){warnings.push('OMITIDO '+f+': '+e.message);continue}
  scenes.push(`;(function(){\n/* ${f} */\n${code}\n})();`);
}
// CSS de paquete.
const cssFiles=fs.readdirSync(srcDir).filter(f=>f.endsWith('.css')&&f!=='pulido.css').sort();
// Cada CSS de paquete va en su propio <style data-pkg="ed"> (el inventario de paridad lo usa para saber de quién es cada regla).
// El primer <style> y el último los abre y cierra template.html.
const css=[pd('styles.css'),reg('extra.css'),own('pulido.css')].join('\n')+cssFiles.map(f=>`\n</style>\n<style data-pkg="${f.replace(/\.css$/,'')}">\n${own(f)}`).join('');
const html=own('template.html')
  .replace('/*__FONTS__*/',()=>fonts)
  .replace('/*__CSS__*/',()=>css)
  .replace('/*__DATA__*/',()=>pd('covers.js')+'\n'+pd('data.js')+'\n'+reg('data.js')+'\nconst IMG='+JSON.stringify(IMG)+';')
  .replace('/*__JS__*/',()=>app)
  .replace('/*__SCENES__*/',()=>[reg('base.js'),...scenes].join('\n'))
  .replace('/*__BOOT__*/',()=>own('boot.js'));
// --out=<carpeta>: cada agente compila a su propia carpeta (p. ej. shots/ed) para no pisarse. Escritura atómica.
const outArg=process.argv.find(a=>a.startsWith('--out='));
const outDir=outArg?path.resolve(outArg.slice(6)):__dirname;
fs.mkdirSync(outDir,{recursive:true});
const put=(name,text)=>{const tmp=path.join(outDir,name+'.tmp'+process.pid);fs.writeFileSync(tmp,text);fs.renameSync(tmp,path.join(outDir,name))};
put('artifact.html',html);
const full=['<!doctype html>','<html lang="es">','<head>','<meta charset="utf-8">','<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">','</head>','<body>',html,'</body>','</html>',''].join('\n');
put('prototipos.html',full);
console.log(path.join(outDir,'prototipos.html')+': '+Buffer.byteLength(full)+' bytes · '+scenes.length+' archivos de escenas · '+cssFiles.length+' css de paquete');
for(const w of warnings)console.error('AVISO '+w);
if(warnings.length&&process.argv.includes('--strict'))process.exit(2);
