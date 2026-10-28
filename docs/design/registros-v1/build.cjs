// Recompone el lienzo «Registros v1» con las fuentes reales de KPKN. Reutiliza estilos, portadas y datos del lienzo aprobado
// de Program Detail (../program-detail-v1/src) y suma los suyos. artifact.html: sin esqueleto; prototipos.html: archivo completo.
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../../..');
const own=f=>fs.readFileSync(path.join(__dirname,'src',f),'utf8');
const base=f=>fs.readFileSync(path.join(__dirname,'../program-detail-v1/src',f),'utf8');
const font=(file,family,weight)=>`@font-face{font-family:${family};font-style:normal;font-weight:${weight};font-display:swap;src:url(data:font/ttf;base64,${fs.readFileSync(path.join(root,'android-native/app/src/main/res/font',file)).toString('base64')}) format('truetype')}`;
const fonts=[font('wizard_body_regular.ttf','Inter',400),font('wizard_body_medium.ttf','Inter',500),font('wizard_body_semibold.ttf','Inter',600),font('syne_bold.ttf','Syne',700),font('syne_extrabold.ttf','Syne',800)].join('\n');
const scenes=['base.js','scenes-decisiones.js','scenes-competencias.js','scenes-crear.js','scenes-comida.js','scenes-busqueda.js','scenes-historias.js']
  .filter(f=>fs.existsSync(path.join(__dirname,'src',f))).map(own).join('\n');
const html=own('template.html')
  .replace('/*__FONTS__*/',()=>fonts)
  .replace('/*__CSS__*/',()=>base('styles.css')+'\n'+own('extra.css'))
  .replace('/*__DATA__*/',()=>base('covers.js')+'\n'+base('data.js')+'\n'+own('data.js'))
  .replace('/*__JS__*/',()=>own('app.js'))
  .replace('/*__SCENES__*/',()=>scenes);
fs.writeFileSync(path.join(__dirname,'artifact.html'),html);
const full=['<!doctype html>','<html lang="es">','<head>','<meta charset="utf-8">','<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">','</head>','<body>',html,'</body>','</html>',''].join('\n');
fs.writeFileSync(path.join(__dirname,'prototipos.html'),full);
console.log('prototipos.html: '+Buffer.byteLength(full)+' bytes');
