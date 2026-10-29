// Recompone el lienzo «Arreglos» (Nutrición plana + Program Detail) con las fuentes reales de KPKN. Reutiliza estilos,
// piezas y datos de los lienzos aprobados de Program Detail (../program-detail-v1/src) y Registros (../registros-v1/src).
// artifact.html: sin esqueleto; prototipos.html: archivo completo. Las capturas de hoy (hoy/*.jpg) van incrustadas.
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../../..');
const own=f=>fs.readFileSync(path.join(__dirname,'src',f),'utf8');
const pd=f=>fs.readFileSync(path.join(__dirname,'../program-detail-v1/src',f),'utf8');
const reg=f=>fs.readFileSync(path.join(__dirname,'../registros-v1/src',f),'utf8');
const font=(file,family,weight)=>`@font-face{font-family:${family};font-style:normal;font-weight:${weight};font-display:swap;src:url(data:font/ttf;base64,${fs.readFileSync(path.join(root,'android-native/app/src/main/res/font',file)).toString('base64')}) format('truetype')}`;
const fonts=[font('wizard_body_regular.ttf','Inter',400),font('wizard_body_medium.ttf','Inter',500),font('wizard_body_semibold.ttf','Inter',600),font('syne_bold.ttf','Syne',700),font('syne_extrabold.ttf','Syne',800)].join('\n');
const must=(text,piece,label)=>{if(!text.includes(piece))throw new Error('No encuentro '+label);return text};
// El motor de escenas de Registros con nuestras pestañas y la captura de hoy en las notas.
let app=reg('app.js');
app=app.replace(/const TABS=\[[\s\S]*?\n\];/,()=>own('tabs.js').trim());
must(app,'NP_TABS','las pestañas');
const notesP="${(n.p||[]).map(p=>`<p>${p}</p>`).join('')}";
must(app,notesP,'las notas');
app=app.replace(notesP,()=>"${n.img?`<figure class=\"np-hoy\"><img src=\"${IMG[n.img]}\" alt=\"Hoy en tu teléfono\"><figcaption>${n.imgCap||'Hoy, en tu teléfono.'}</figcaption></figure>`:''}"+notesP);
// Datos de vitaminas y minerales del lienzo v4 (sin su huella ni sus escenas).
const micro=reg('scenes-micro.js');
const microData=must(micro,'// La huella','los datos de la huella').split('// La huella')[0];
const IMG={};
const hoyDir=path.join(__dirname,'hoy');
if(fs.existsSync(hoyDir))for(const f of fs.readdirSync(hoyDir).filter(f=>f.endsWith('.jpg')))IMG[f.replace(/\.jpg$/,'')]='data:image/jpeg;base64,'+fs.readFileSync(path.join(hoyDir,f)).toString('base64');
const html=own('template.html')
  .replace('/*__FONTS__*/',()=>fonts)
  .replace('/*__CSS__*/',()=>pd('styles.css')+'\n'+reg('extra.css')+'\n'+own('arreglos.css'))
  .replace('/*__DATA__*/',()=>pd('covers.js')+'\n'+pd('data.js')+'\n'+reg('data.js')+'\nconst IMG='+JSON.stringify(IMG)+';')
  .replace('/*__JS__*/',()=>app)
  .replace('/*__SCENES__*/',()=>[microData,reg('base.js'),reg('scenes-comida.js'),pd('scenes.js'),own('scenes-reglas.js'),own('scenes-nutricion.js'),own('scenes-programa.js')].join('\n'));
fs.writeFileSync(path.join(__dirname,'artifact.html'),html);
const full=['<!doctype html>','<html lang="es">','<head>','<meta charset="utf-8">','<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">','</head>','<body>',html,'</body>','</html>',''].join('\n');
fs.writeFileSync(path.join(__dirname,'prototipos.html'),full);
console.log('prototipos.html: '+Buffer.byteLength(full)+' bytes');
