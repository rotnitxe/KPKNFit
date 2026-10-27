// Recompone el prototipo autocontenido con las fuentes reales de KPKN.
// artifact.html: contenido sin esqueleto (el publicador añade doctype/head/body). prototipos.html: archivo local completo.
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../../..');
const src=f=>fs.readFileSync(path.join(__dirname,'src',f),'utf8');
const font=(file,family,weight)=>`@font-face{font-family:${family};font-style:normal;font-weight:${weight};font-display:swap;src:url(data:font/ttf;base64,${fs.readFileSync(path.join(root,'android-native/app/src/main/res/font',file)).toString('base64')}) format('truetype')}`;
const fonts=[font('wizard_body_regular.ttf','Inter',400),font('wizard_body_medium.ttf','Inter',500),font('wizard_body_semibold.ttf','Inter',600),font('syne_bold.ttf','Syne',700),font('syne_extrabold.ttf','Syne',800)].join('\n');
const html=src('template.html').replace('/*__FONTS__*/',()=>fonts).replace('/*__CSS__*/',()=>src('styles.css')).replace('/*__DATA__*/',()=>src('covers.js')+'\n'+src('data.js')).replace('/*__JS__*/',()=>src('app.js')).replace('/*__SCENES__*/',()=>['scenes.js','scenes-programas.js','scenes-portadas.js','scenes-estructura.js','scenes-compartir.js','scenes-resumen.js','scenes-historias.js','scenes-menus.js','scenes-decisiones.js'].filter(f=>fs.existsSync(path.join(__dirname,'src',f))).map(src).join('\n'));
fs.writeFileSync(path.join(__dirname,'artifact.html'),html);
const full=['<!doctype html>','<html lang="es">','<head>','<meta charset="utf-8">','<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">','</head>','<body>',html,'</body>','</html>',''].join('\n');
fs.writeFileSync(path.join(__dirname,'prototipos.html'),full);
console.log('prototipos.html: '+Buffer.byteLength(full)+' bytes');
