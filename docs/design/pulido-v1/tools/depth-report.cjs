// Compara TODAS las hojas de profundidad del lienzo con el render de la app y escribe los números.
//   node tools/depth-report.cjs --app=<carpeta con los PNG de DepthSheetRenderTest> [--canvas=shots/dp/cm] [--canvas-nl=shots/dp/cm-nolabel] [--out=shots/dp/report] [--suffix=]
// Por hoja: (1) tools/compare.cjs de la hoja entera (con rótulos y cabecera; imagen lienzo | app | diferencias en <out>/<id>-diff.png) y
//           (2) tools/depth-zones.cjs de la hoja sin rótulos (error de cada muestra: fuera y dentro). Escribe <out>/resumen.txt y <out>/<id>-zonas.txt.
// --suffix=_emul  usa <id>_emul_nolabel.png en lugar de <id>_nolabel.png (hojas de vidrio y filtros con el desenfoque emulado por software).
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const args = process.argv.slice(2);
const opt = (k, d) => { const a = args.find(x => x.startsWith('--' + k + '=')); return a ? a.slice(k.length + 3) : d; };
const root = path.resolve(__dirname, '..');
const app = path.resolve(opt('app', ''));
const canvas = path.resolve(root, opt('canvas', 'shots/dp/cm'));
const canvasNl = path.resolve(root, opt('canvas-nl', 'shots/dp/cm-nolabel'));
const out = path.resolve(root, opt('out', 'shots/dp/report'));
const suffix = opt('suffix', '');
const only = opt('only', '');
fs.mkdirSync(out, { recursive: true });
const J = require('../paridad/cimientos.json');
const ids = [
  ...J.hojas.sombras.map(h => h.id), ...J.hojas.texto.map(h => h.id),
  'cm-vid-1', 'cm-vid-filtros-1', 'cm-deg-1', 'cm-deg-2', 'cm-deg-3', 'cm-deg-4', 'cm-deg-5',
].filter(id => !only || only.split(',').includes(id));
const node = process.execPath;
const lines = [];
for (const id of ids) {
  const pick = n => fs.existsSync(path.join(app, n + '_1x.png')) ? path.join(app, n + '_1x.png') : path.join(app, n + '.png');
  const appFull = pick(id);
  const appNl = pick(id + (suffix ? suffix + '_nolabel' : '_nolabel'));
  let full = '(sin imagen de la app)';
  if (fs.existsSync(appFull)) {
    const r = spawnSync(node, [path.join(root, 'tools/compare.cjs'), path.join(canvas, id + '.png'), appFull, '--out=' + path.join(out, id + '-diff.png')], { encoding: 'utf8', timeout: 180000 });
    full = (r.stdout || r.stderr || '').trim().replace(/ · imagen: .*$/, '');
  }
  let zones = '(sin imagen sin rótulos)';
  if (fs.existsSync(appNl)) {
    const r = spawnSync(node, [path.join(root, 'tools/depth-zones.cjs'), id, path.join(canvasNl, id + '.png'), appNl, '--out=' + path.join(out, id + (suffix || '') + '-zonas.txt')], { encoding: 'utf8', timeout: 180000 });
    zones = (r.stdout || r.stderr || '').trim();
  }
  lines.push(`## ${id}`, 'hoja entera: ' + full, '```', zones, '```', '');
  console.log(`## ${id}\nhoja entera: ${full}\n${zones.split('\n').slice(-1)[0]}`);
}
fs.writeFileSync(path.join(out, 'resumen' + (suffix || '') + '.txt'), lines.join('\n'));
