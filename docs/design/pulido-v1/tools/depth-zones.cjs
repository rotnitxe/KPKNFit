// Mide el error de cada MUESTRA de una hoja de profundidad (cm-som-*, cm-deg-*, cm-vid-*) entre la captura del lienzo y el render de la app.
//   node tools/depth-zones.cjs <id-de-hoja> <lienzo.png> <app.png> [--thr=28] [--json] [--out=ruta.txt]
// Las dos imágenes deben ser de la hoja SIN rótulos ni cabecera (lienzo: build con  .cm-l,.cm-h,.cm-gt{display:none}; app: KpknDepthSheet(showLabels = false) → <id>_nolabel.png).
// Por cada muestra de paridad/cimientos.json da: error medio y máximo (máx. de |ΔR|,|ΔG|,|ΔB| por píxel, 0–255) y % de píxeles sobre el umbral en
//   · «fuera»: la zona que ocupa la sombra alrededor de la muestra (el rectángulo ampliado en su alcance, SIN el interior redondeado), y
//   · «dentro»: el interior de la muestra (realces `inset`, degradados, vidrio).
// Escala las dos imágenes a 360 × 880 con Chrome headless (igual que tools/compare.cjs).
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { pathToFileURL } = require('node:url');
const args = process.argv.slice(2);
const files = args.filter(a => !a.startsWith('--'));
const opt = (k, d) => { const a = args.find(x => x.startsWith('--' + k + '=')); return a ? a.split('=')[1] : d; };
if (files.length < 3) { console.error('Uso: node tools/depth-zones.cjs <id-de-hoja> <lienzo.png> <app.png> [--thr=28] [--json]'); process.exit(1); }
const [id, canvasPng, appPng] = files;
const THR = Number(opt('thr', '28'));
const J = require('../paridad/cimientos.json');

// Regiones de la hoja
const regions = [];
const pushShadow = (n, kind) => {
  const p = (J.sombras.find(s => s.nombre === n) || J.sombrasTexto.find(s => s.nombre === n));
  const h = p.hoja;
  const a = p.alcance + 2, up = (p.sobresale ? p.sobresale.up : a) + 2, down = (p.sobresale ? p.sobresale.down : a) + 2;
  regions.push({ name: n, kind, rect: [h.x, h.y, 140, 84], radius: 24, outer: [h.x - a, h.y - up, 140 + 2 * a, 84 + up + down] });
};
const sheetShadows = J.hojas.sombras.concat(J.hojas.texto).find(h => h.id === id);
if (sheetShadows) for (const n of sheetShadows.items) pushShadow(n, 'sombra');
else if (/^cm-deg-/.test(id)) for (const p of J.degradados.filter(d => d.hoja.hoja === id)) regions.push({ name: p.nombre, kind: 'degradado', rect: [p.hoja.x, p.hoja.y, 140, 84], radius: 24, outer: null });
else if (/^cm-vid-filtros/.test(id)) for (const p of J.filtros.filter(d => d.hoja.hoja === id)) regions.push({ name: p.nombre, kind: 'filtro', rect: [p.hoja.x, p.hoja.y, 140, 84], radius: 24, outer: null });
else if (/^cm-vid-1/.test(id)) for (const p of J.vidrio.filter(d => d.hoja.hoja === id)) regions.push({ name: p.nombre, kind: 'vidrio', rect: [p.hoja.x, p.hoja.y, 312, 72], radius: 24, outer: null });
else { console.error('Hoja desconocida: ' + id); process.exit(1); }

function chromeExe() {
  const base = path.join(process.env.LOCALAPPDATA || '', 'ms-playwright');
  const dirs = fs.existsSync(base) ? fs.readdirSync(base).filter(d => d.startsWith('chromium_headless_shell-')).sort().reverse() : [];
  for (const d of dirs) { const p = path.join(base, d, 'chrome-headless-shell-win64', 'chrome-headless-shell.exe'); if (fs.existsSync(p)) return p; }
  throw new Error('No encuentro chrome-headless-shell en ' + base);
}
const b64 = f => fs.readFileSync(f).toString('base64');
const page = `<!doctype html><meta charset="utf-8"><body style="margin:0"><pre id="R" style="display:none"></pre><script>
const W=360,H=880,THR=${THR},REG=${JSON.stringify(regions)};
const load=s=>new Promise(r=>{const i=new Image();i.onload=()=>r(i);i.src=s});
(async()=>{
  const [a,b]=await Promise.all([load('data:image/png;base64,${b64(canvasPng)}'),load('data:image/png;base64,${b64(appPng)}')]);
  const mk=i=>{const c=document.createElement('canvas');c.width=W;c.height=H;const x=c.getContext('2d');x.fillStyle='#000';x.fillRect(0,0,W,H);x.imageSmoothingQuality='high';x.drawImage(i,0,0,W,H);return x.getImageData(0,0,W,H).data};
  const A=mk(a),B=mk(b);
  const inRR=(px,py,r)=>{const [x,y,w,h]=r.rect,R=r.radius;if(px<x||py<y||px>=x+w||py>=y+h)return false;
    const cx=px<x+R?x+R:(px>=x+w-R?x+w-R:px),cy=py<y+R?y+R:(py>=y+h-R?y+h-R:py);const dx=px+.5-cx,dy=py+.5-cy;
    return (px>=x+R&&px<x+w-R)||(py>=y+R&&py<y+h-R)||dx*dx+dy*dy<=R*R};
  const out=[];
  for(const r of REG){
    const o={n:0,s:0,m:0,b:0},i={n:0,s:0,m:0,b:0};
    const box=r.outer?r.outer:r.rect;
    const x0=Math.max(0,Math.floor(box[0])),y0=Math.max(0,Math.floor(box[1])),x1=Math.min(W,Math.ceil(box[0]+box[2])),y1=Math.min(H,Math.ceil(box[1]+box[3]));
    for(let y=y0;y<y1;y++)for(let x=x0;x<x1;x++){
      const k=(y*W+x)*4;const d=Math.max(Math.abs(A[k]-B[k]),Math.abs(A[k+1]-B[k+1]),Math.abs(A[k+2]-B[k+2]));
      const t=inRR(x,y,r)?i:o;t.n++;t.s+=d;if(d>t.m)t.m=d;if(d>THR)t.b++;
    }
    out.push({name:r.name,kind:r.kind,outer:o.n?{mean:o.s/o.n,max:o.m,pct:100*o.b/o.n,n:o.n}:null,inner:i.n?{mean:i.s/i.n,max:i.m,pct:100*i.b/i.n,n:i.n}:null});
  }
  document.getElementById('R').textContent=JSON.stringify(out);document.title='listo';
})();
</script>`;
const prof = fs.mkdtempSync(path.join(os.tmpdir(), 'pl-zones-'));
const tmp = path.join(prof, 'z.html'); fs.writeFileSync(tmp, page);
try {
  const r = spawnSync(chromeExe(), ['--headless', '--disable-gpu', '--no-sandbox', '--allow-file-access-from-files', '--user-data-dir=' + prof, '--virtual-time-budget=6000', '--dump-dom', pathToFileURL(tmp).href], { encoding: 'utf8', timeout: 120000, maxBuffer: 64 * 1024 * 1024 });
  const m = /<pre id="R"[^>]*>([\s\S]*?)<\/pre>/.exec(r.stdout || '');
  if (!m) throw new Error('Sin resultado: ' + (r.stdout || '').slice(0, 300));
  const res = JSON.parse(m[1].replace(/&quot;/g, '"'));
  if (args.includes('--json')) { console.log(JSON.stringify(res, null, 1)); }
  else {
    const f1 = v => v == null ? '   —   ' : v.toFixed(2).padStart(6);
    const lines = [`${id} · umbral ${THR}/255 · error = máx(|ΔR|,|ΔG|,|ΔB|) por píxel`, 'muestra'.padEnd(30) + ' | fuera: medio  máx  %>thr   | dentro: medio  máx  %>thr'];
    let so = 0, no = 0, si = 0, ni = 0;
    for (const e of res) {
      const o = e.outer, i = e.inner;
      if (o) { so += o.mean * o.n; no += o.n; } if (i) { si += i.mean * i.n; ni += i.n; }
      lines.push(e.name.padEnd(30) + ' | ' + (o ? `${f1(o.mean)} ${String(o.max).padStart(4)} ${f1(o.pct)}` : '      —            ') + '   | ' + (i ? `${f1(i.mean)} ${String(i.max).padStart(4)} ${f1(i.pct)}` : '      —'));
    }
    lines.push(`TOTAL ${id}: fuera ${no ? (so / no).toFixed(3) : '—'} · dentro ${ni ? (si / ni).toFixed(3) : '—'}`);
    const txt = lines.join('\n'); console.log(txt);
    const o = opt('out', null); if (o) fs.writeFileSync(o, txt + '\n');
  }
} finally { try { fs.rmSync(prof, { recursive: true, force: true }); } catch (e) { } }
