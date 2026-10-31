// Strict visual acceptance for approved Pulido v1 scenes. Never stretches, aligns or masks a mismatch.
// node tools/visual-gate.cjs reference.png app.png --out=review.png --json=metrics.json
// Native captures may use a uniform integer density (e.g. 1080x2640 for 360x880).
// --size=360x880 --min-parity=98 --max-mean=2.5 --thr=28
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { pathToFileURL } = require('node:url');
const args = process.argv.slice(2);
const opt = (name, fallback) => args.find(a => a.startsWith('--' + name + '='))?.slice(name.length + 3) ?? fallback;
const files = args.filter(a => !a.startsWith('--'));
if (files.length !== 2) { console.error('Usage: node tools/visual-gate.cjs reference.png app.png --out=review.png --json=metrics.json'); process.exit(1); }
const [width, height] = opt('size', '360x880').split('x').map(Number);
const threshold = Number(opt('thr', '28'));
const minParity = Number(opt('min-parity', '98'));
const maxMean = Number(opt('max-mean', '2.5'));
if (![width, height].every(n => Number.isInteger(n) && n > 0) || !Number.isFinite(threshold) || threshold < 0 || threshold > 255 ||
    !Number.isFinite(minParity) || minParity < 0 || minParity > 100 || !Number.isFinite(maxMean) || maxMean < 0) throw Error('Invalid acceptance parameters');
const output = path.resolve(opt('out', files[1].replace(/\.png$/i, '') + '-gate.png'));
const json = path.resolve(opt('json', output.replace(/\.png$/i, '') + '.json'));
const root = path.join(process.env.LOCALAPPDATA || '', 'ms-playwright');
const chrome = fs.readdirSync(root).filter(n => n.startsWith('chromium_headless_shell-')).sort().reverse()
    .map(n => path.join(root, n, 'chrome-headless-shell-win64', 'chrome-headless-shell.exe')).find(fs.existsSync);
if (!chrome) throw Error('No Chromium headless shell available');
const encode = file => 'data:image/png;base64,' + fs.readFileSync(file).toString('base64');
const page = `<!doctype html><meta charset="utf-8"><body style="margin:0;background:#000"><canvas id="review" width="${width * 3}" height="${height}"></canvas><pre id="metrics" hidden></pre><script>
(async () => {
  const W=${width}, H=${height}, threshold=${threshold};
  const load = url => new Promise((resolve,reject) => { const i=new Image(); i.onload=()=>resolve(i); i.onerror=()=>reject(Error('Cannot decode image')); i.src=url; });
  try {
    const images=await Promise.all([load(${JSON.stringify(encode(files[0]))}),load(${JSON.stringify(encode(files[1]))})]);
    const dimensions=images.map(i=>({width:i.width,height:i.height}));
    for (const i of images) { const density=i.width/W; if (!Number.isInteger(density) || density < 1 || i.height!==H*density) throw Error('Incompatible viewport: '+i.width+'x'+i.height+'; expected '+W+'x'+H+' at uniform integer density'); }
    const contexts=images.map(i=>{const c=document.createElement('canvas');c.width=W;c.height=H;const x=c.getContext('2d');x.fillStyle='#000';x.fillRect(0,0,W,H);x.imageSmoothingQuality='high';x.drawImage(i,0,0,W,H);return x;});
    const a=contexts[0].getImageData(0,0,W,H).data,b=contexts[1].getImageData(0,0,W,H).data;
    const ctx=document.querySelector('canvas').getContext('2d');ctx.drawImage(contexts[0].canvas,0,0);ctx.drawImage(contexts[1].canvas,W,0);
    const diff=ctx.createImageData(W,H), cells=Array.from({length:48},()=>({pixels:0,bad:0,error:0}));
    let bad=0,error=0;
    for(let p=0;p<W*H;p++) {const k=p*4,d=Math.max(Math.abs(a[k]-b[k]),Math.abs(a[k+1]-b[k+1]),Math.abs(a[k+2]-b[k+2]));
      const hot=d>threshold, col=Math.min(5,Math.floor(p%W*6/W)), row=Math.min(7,Math.floor(Math.floor(p/W)*8/H)), cell=cells[row*6+col];
      bad+=hot;error+=d;cell.pixels++;cell.bad+=hot;cell.error+=d;
      diff.data[k]=hot?255:a[k]*.25;diff.data[k+1]=hot?50:a[k+1]*.25;diff.data[k+2]=hot?50:a[k+2]*.25;diff.data[k+3]=255;
    }
    ctx.putImageData(diff,W*2,0);
    const parity=100*(1-bad/(W*H)),mean=error/(W*H);
    document.querySelector('pre').textContent=JSON.stringify({passed:parity>=${minParity}&&mean<=${maxMean},parityPercent:parity,badPercent:100-parity,meanError:mean,threshold,minParity:${minParity},maxMean:${maxMean},dimensions,viewport:{width:W,height:H},cells:cells.map((c,i)=>({column:i%6,row:Math.floor(i/6),badPercent:100*c.bad/c.pixels,meanError:c.error/c.pixels}))});
  } catch(e) {document.querySelector('pre').textContent=JSON.stringify({passed:false,error:e.message});}
})();</script>`;
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'pulido-gate-'));
let result;
try {
  const html=path.join(profile,'gate.html');fs.writeFileSync(html,page);
  const common=['--headless','--disable-gpu','--hide-scrollbars','--no-sandbox','--force-device-scale-factor=1','--user-data-dir='+profile,'--virtual-time-budget=4000'];
  const run=spawnSync(chrome,[...common,'--dump-dom',pathToFileURL(html).href],{encoding:'utf8',timeout:90000,maxBuffer:16*1024*1024});
  const match=/<pre id="metrics"[^>]*>([\s\S]*?)<\/pre>/.exec(run.stdout || '');
  if(!match) throw Error('No comparison result: '+(run.error?.message || run.stderr?.slice(0,300) || run.status));
  result=JSON.parse(match[1].replace(/&quot;/g,'"').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>'));
  result.reference=path.resolve(files[0]);result.actual=path.resolve(files[1]);
  fs.mkdirSync(path.dirname(json),{recursive:true});
  if(!result.error) {
    fs.mkdirSync(path.dirname(output),{recursive:true});
    const shot=spawnSync(chrome,[...common,'--window-size='+(width*3)+','+height,'--screenshot='+output,pathToFileURL(html).href],{encoding:'utf8',timeout:90000});
    if(shot.status!==0||!fs.existsSync(output)) {result.passed=false;result.error='Cannot save visual evidence';}
    else result.comparison=output;
  }
  fs.writeFileSync(json,JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify({passed:result.passed,parityPercent:result.parityPercent,meanError:result.meanError,error:result.error,json}));
} finally {fs.rmSync(profile,{recursive:true,force:true});}
process.exitCode=result?.passed?0:2;
