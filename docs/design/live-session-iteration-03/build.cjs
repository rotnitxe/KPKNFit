// Recompone prototipos.html autocontenido con las fuentes reales de KPKN.
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../../..');
const src=f=>fs.readFileSync(path.join(__dirname,'src',f),'utf8');
const font=(file,family,weight)=>`@font-face{font-family:${family};font-style:normal;font-weight:${weight};font-display:swap;src:url(data:font/ttf;base64,${fs.readFileSync(path.join(root,'android-native/app/src/main/res/font',file)).toString('base64')}) format('truetype')}`;
const fonts=[font('wizard_body_regular.ttf','Inter',400),font('wizard_body_semibold.ttf','Inter',600),font('syne_bold.ttf','Syne',700),font('syne_extrabold.ttf','Syne',800)].join('\n');
const html=src('template.html').replace('/*__FONTS__*/',()=>fonts).replace('/*__CSS__*/',()=>src('styles.css')).replace('/*__JS__*/',()=>src('app.js')).replace('/*__GAMES__*/',()=>src('games.js'));
fs.writeFileSync(path.join(__dirname,'prototipos.html'),html);
console.log('prototipos.html: '+Buffer.byteLength(html)+' bytes');
