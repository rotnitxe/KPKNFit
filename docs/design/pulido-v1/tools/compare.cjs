// Compara la captura de una escena del lienzo con el render de la app (hoja de render de Robolectric o captura del Flip) y da un número.
//   node tools/compare.cjs <lienzo.png> <app.png> [--out=diff.png] [--thr=28] [--size=360x880]
// Las dos imágenes se escalan al tamaño del lienzo (por defecto 360×880) y se comparan píxel a píxel con Chrome headless (canvas). Imprime el % de píxeles que
// difieren más del umbral (0–255 por canal), el error medio y la zona (cuadrícula 6×8) que más se aleja, y guarda lienzo | app | diferencias en --out.
// Sirve para la puerta «render coincide» de PROTOCOLO_1A1.md: un número alto no es un fallo por sí solo (las sombras y el vidrio no son idénticos entre CSS y
// Android), pero cada desviación debe quedar explicada junto a la captura lado a lado.
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const args=process.argv.slice(2);
const files=args.filter(a=>!a.startsWith('--'));
const opt=(k,d)=>{const a=args.find(x=>x.startsWith('--'+k+'='));return a?a.split('=')[1]:d};
if(files.length<2){console.error('Uso: node tools/compare.cjs <lienzo.png> <app.png> [--out=diff.png] [--thr=28] [--size=360x880]');process.exit(1)}
const [w,h]=opt('size','360x880').split('x').map(Number),thr=Number(opt('thr','28'));
const out=path.resolve(opt('out',path.join(path.dirname(files[1]),path.basename(files[1],'.png')+'-diff.png')));
function chromeExe(){
  const base=path.join(process.env.LOCALAPPDATA||'','ms-playwright');
  const dirs=fs.existsSync(base)?fs.readdirSync(base).filter(d=>d.startsWith('chromium_headless_shell-')).sort().reverse():[];
  for(const d of dirs){const p=path.join(base,d,'chrome-headless-shell-win64','chrome-headless-shell.exe');if(fs.existsSync(p))return p}
  throw new Error('No encuentro chrome-headless-shell en '+base);
}
const b64=f=>fs.readFileSync(f).toString('base64');
const page=`<!doctype html><meta charset="utf-8"><body style="margin:0;background:#111"><canvas id="o" width="${w*3+40}" height="${h}"></canvas><pre id="R" style="display:none"></pre><script>
const W=${w},H=${h},THR=${thr};
const load=s=>new Promise(r=>{const i=new Image();i.onload=()=>r(i);i.src=s});
(async()=>{
  const [a,b]=await Promise.all([load('data:image/png;base64,${b64(files[0])}'),load('data:image/png;base64,${b64(files[1])}')]);
  const mk=i=>{const c=document.createElement('canvas');c.width=W;c.height=H;const x=c.getContext('2d');x.fillStyle='#000';x.fillRect(0,0,W,H);x.imageSmoothingQuality='high';x.drawImage(i,0,0,W,H);return x};
  const A=mk(a),B=mk(b),da=A.getImageData(0,0,W,H).data,db=B.getImageData(0,0,W,H).data;
  const o=document.getElementById('o').getContext('2d');
  o.drawImage(A.canvas,0,0);o.drawImage(B.canvas,W+20,0);
  const D=o.createImageData(W,H);let bad=0,sum=0;const cell={};
  for(let p=0;p<W*H;p++){const k=p*4;const d=Math.max(Math.abs(da[k]-db[k]),Math.abs(da[k+1]-db[k+1]),Math.abs(da[k+2]-db[k+2]));sum+=d;
    const hot=d>THR;if(hot){bad++;const cx=Math.floor((p%W)/(W/6)),cy=Math.floor(Math.floor(p/W)/(H/8));cell[cx+','+cy]=(cell[cx+','+cy]||0)+1}
    D.data[k]=hot?255:da[k]*0.35;D.data[k+1]=hot?60:da[k+1]*0.35;D.data[k+2]=hot?60:da[k+2]*0.35;D.data[k+3]=255}
  o.putImageData(D,(W+20)*2,0);
  const worst=Object.entries(cell).sort((x,y)=>y[1]-x[1])[0]||['—',0];
  document.getElementById('R').textContent=JSON.stringify({badPct:+(bad/(W*H)*100).toFixed(2),meanErr:+(sum/(W*H)).toFixed(2),worstCell:worst[0],worstPct:+(worst[1]/((W/6)*(H/8))*100).toFixed(1)});
  document.title='listo';
})();
</script>`;
const prof=fs.mkdtempSync(path.join(os.tmpdir(),'pl-cmp-'));
const tmp=path.join(prof,'cmp.html');fs.writeFileSync(tmp,page);
const common=['--headless','--disable-gpu','--no-sandbox','--allow-file-access-from-files','--user-data-dir='+prof];
try{
  const r=spawnSync(chromeExe(),[...common,'--virtual-time-budget=4000','--dump-dom',pathToFileURL(tmp).href],{encoding:'utf8',timeout:90000,maxBuffer:64*1024*1024});
  const m=/<pre id="R"[^>]*>([\s\S]*?)<\/pre>/.exec(r.stdout||'');
  if(!m)throw new Error('No hubo resultado: '+(r.stdout||'').slice(0,200));
  const res=JSON.parse(m[1].replace(/&quot;/g,'"'));
  spawnSync(chromeExe(),[...common,'--hide-scrollbars','--window-size='+(w*3+40)+','+h,'--virtual-time-budget=4000','--screenshot='+out,pathToFileURL(tmp).href],{encoding:'utf8',timeout:90000});
  console.log(`diferencia: ${res.badPct} % de los píxeles (umbral ${thr}/255) · error medio ${res.meanErr}/255 · zona más lejana (col,fila de 6×8): ${res.worstCell} con ${res.worstPct} % · imagen: ${out}`);
}finally{try{fs.rmSync(prof,{recursive:true,force:true})}catch(e){}}
