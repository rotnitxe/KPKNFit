// Genera los catálogos de Kotlin de profundidad (Fase 0.2) a partir de paridad/cimientos.json (el catálogo cerrado del lienzo).
//   node tools/depth-kotlin-gen.cjs --out=<carpeta ui/design/depth>
// Escribe: KpknShadowCatalog.kt (enum KpknShadow y KpknTextShadow), KpknGradientCatalog.kt (enum KpknGradient) y KpknDepthSheetData.kt (posiciones y rótulos de las hojas cm-*).
// NO se editan a mano esos tres archivos: se regeneran. La lógica (dibujo, calibración) está en KpknShadows.kt, KpknGradients.kt, KpknGlassPresets.kt y KpknDepthSheet.kt.
const fs = require('node:fs');
const path = require('node:path');
const J = require('../paridad/cimientos.json');
const outArg = process.argv.find(a => a.startsWith('--out='));
if (!outArg) { console.error('Falta --out=<carpeta>'); process.exit(1); }
const OUT = path.resolve(outArg.slice(6));

const pascal = n => n.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join('');
const num = v => { const s = String(+v); return s; };
const dpLit = v => { const n = +v; if (Number.isInteger(n)) return n < 0 ? `(${n}).dp` : `${n}.dp`; return n < 0 ? `(${n}).dp` : `${n}.dp`; };
const f = v => { const s = String(+v); return (s.includes('.') ? s : s + '.0') + 'f'; };
const hex2 = n => n.toString(16).padStart(2, '0').toUpperCase();
// rgb(a)(...) | #hex | transparent → Color(0xAARRGGBB)
function color(str) {
  str = str.trim();
  let r, g, b, a = 1;
  let m;
  if ((m = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/.exec(str))) { r = +m[1]; g = +m[2]; b = +m[3]; a = m[4] === undefined ? 1 : +m[4]; }
  else if (str === 'transparent') { r = 0; g = 0; b = 0; a = 0; }
  else if ((m = /^#([0-9a-f]{3,8})$/i.exec(str))) {
    let h = m[1]; if (h.length === 3) h = h.split('').map(c => c + c).join(''); if (h.length === 4) h = h.split('').map(c => c + c).join('');
    r = parseInt(h.slice(0, 2), 16); g = parseInt(h.slice(2, 4), 16); b = parseInt(h.slice(4, 6), 16); a = h.length === 8 ? parseInt(h.slice(6, 8), 16) / 255 : 1;
  } else throw new Error('color no reconocido: ' + str);
  const A = Math.round(a * 255);
  return `Color(0x${hex2(A)}${hex2(Math.round(r))}${hex2(Math.round(g))}${hex2(Math.round(b))})`;
}
const header = (what) => `// GENERADO por tools/depth-kotlin-gen.cjs desde paridad/cimientos.json (${what}). No se edita a mano: se regenera.\n`;

/* ------------------------------------------------------------ SOMBRAS */
const SAMPLE = { c: '0xFF8FB2FF', zc: '0xFF87C9A5' };
function layerKt(l, dynRole) {
  let col, dyn = '';
  if (l.dinamico) {
    col = 'Color.Unspecified';
    const m = /(\d+(?:\.\d+)?)%/.exec(l.color);
    dyn = `, dynamicAlpha = ${m ? f(+m[1] / 100) : '1f'}`;
  } else col = color(l.color);
  const parts = [];
  if (+l.x) parts.push(`x = ${dpLit(l.x)}`);
  if (+l.y) parts.push(`y = ${dpLit(l.y)}`);
  if (+l.desenfoque) parts.push(`blur = ${dpLit(l.desenfoque)}`);
  if (+l.extension) parts.push(`spread = ${dpLit(l.extension)}`);
  parts.push(`color = ${col}`);
  if (l.inset) parts.push('inset = true');
  return `KpknShadowLayer(${parts.join(', ')}${dyn})`;
}
const kindKt = { caida: 'Caida', anillo: 'Anillo', interior: 'Interior', resplandor: 'Resplandor', nula: 'Nula' };
const seen = new Set();
let shadowEntries = J.sombras.map(s => {
  const id = pascal(s.nombre); if (seen.has(id)) throw new Error('nombre repetido ' + id); seen.add(id);
  let sample = 'null';
  if (s.dinamico) { const v = /var\(--(zc|c)\)/.exec(s.canonico); sample = SAMPLE[v ? v[1] : 'zc']; if (!v) sample = SAMPLE.zc; sample = `Color(${sample})`; }
  const layers = s.capas.map(l => '        ' + layerKt(l)).join(',\n');
  const doc = `    /** \`${s.nombre}\` · ${s.usos} filas · \`${s.canonico.replace(/\*\//g, '* /')}\` · hoja ${s.hoja.hoja} (${s.hoja.x}, ${s.hoja.y}). */`;
  return `${doc}\n    ${id}(\n        "${s.nombre}", KpknShadowKind.${kindKt[s.tipo]}, ${sample},\n${layers},\n    )`;
});
let textEntries = J.sombrasTexto.map(s => {
  const id = pascal(s.nombre);
  const layers = s.capas.map(l => '        ' + layerKt(l)).join(',\n');
  return `    /** \`${s.nombre}\` · \`${s.canonico}\` · hoja ${s.hoja.hoja} (${s.hoja.x}, ${s.hoja.y}). */\n    ${id}(\n        "${s.nombre}",\n${layers},\n    )`;
});
fs.writeFileSync(path.join(OUT, 'KpknShadowCatalog.kt'), `${header('65 sombras y 3 sombras de texto')}package com.example.kpkn.ui.design.depth

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Las ${J.sombras.length} sombras del catálogo del lienzo (caída, anillo, interior, resplandor y la «ninguna»). Cada caso es el valor canónico del
 * preset (el del miembro más usado); la tolerancia del catálogo (±2 dp, ±4 dp de desenfoque, ±0,067 de alfa) solo sirve para agrupar: para dibujar
 * el VALOR EXACTO de una fila de la matriz, parte de [spec] y cámbialo (\`preset.spec.copyLayer(0) { it.copy(y = 10.dp) }\`).
 * Los casos de color dinámico (\`sampleColor != null\`) reciben el color por parámetro en [spec].
 */
enum class KpknShadow(
    /** Nombre EXACTO del preset en \`paridad/cimientos.json\`. */
    val catalogName: String,
    val kind: KpknShadowKind,
    /** Color de muestra de la hoja del lienzo (\`--c\` o \`--zc\`); null si el preset no lleva color dinámico. */
    val sampleColor: Color?,
    vararg canonicalLayers: KpknShadowLayer,
) {
${shadowEntries.join(',\n\n')};

    /** Capas canónicas, en el orden del CSS (la primera se pinta encima). */
    val canonical: List<KpknShadowLayer> = canonicalLayers.toList()

    /** True si el preset lleva un color del módulo (\`--c\`, \`--zc\`, \`--wc\`) que se pasa por parámetro. */
    val isDynamic: Boolean get() = sampleColor != null

    /** La spec canónica; en los dinámicos usa el color de muestra de la hoja. */
    val spec: KpknShadowSpec get() = spec(Color.Unspecified)

    /** La spec con el [color] del módulo en las capas dinámicas (\`Color.Unspecified\` = el de muestra). Los presets fijos lo ignoran. */
    fun spec(color: Color): KpknShadowSpec = KpknShadowSpec(canonical).withDynamicColor(color.takeIf { it != Color.Unspecified } ?: sampleColor)

    companion object {
        /** Busca un preset por su nombre exacto del catálogo. */
        fun fromCatalogName(name: String): KpknShadow? = entries.firstOrNull { it.catalogName == name }
    }
}

/**
 * Las ${J.sombrasTexto.length} sombras de texto (\`text-shadow\`) del catálogo. Se usan con [KpknShadowedText] o con [KpknTextShadow.style].
 */
enum class KpknTextShadow(
    val catalogName: String,
    vararg canonicalLayers: KpknShadowLayer,
) {
${textEntries.join(',\n\n')};

    /** Capas del CSS (la primera se pinta encima). */
    val layers: List<KpknShadowLayer> = canonicalLayers.toList()
}
`);

/* ------------------------------------------------------------ DEGRADADOS */
function splitTop(s) {
  const out = []; let depth = 0, cur = '';
  for (const ch of s) {
    if (ch === '(') depth++; if (ch === ')') depth--;
    if (ch === ',' && depth === 0) { out.push(cur.trim()); cur = ''; } else cur += ch;
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
}
function parseStop(t) {
  t = t.trim();
  let m = /^(rgba?\([^)]*\)|#[0-9a-fA-F]+|transparent)\s*(?:(-?[\d.]+)(%|px))?$/.exec(t);
  if (!m) throw new Error('stop no reconocido: ' + t);
  const c = color(m[1]);
  if (!m[2]) return `KpknGradStop(${c})`;
  if (m[3] === '%') return `KpknGradStop(${c}, position = ${f(+m[2] / 100)})`;
  return `KpknGradStop(${c}, px = ${f(+m[2])})`;
}
function parseLayer(css) {
  const m = /^(repeating-linear-gradient|linear-gradient|radial-gradient)\((.*)\)$/s.exec(css.trim());
  if (!m) throw new Error('capa no reconocida: ' + css);
  const kind = m[1], args = splitTop(m[2]);
  const first = args[0], isCfg = !/rgb|#|transparent/.test(first) || /^\s*to |deg$/.test(first);
  let cfg = null, stops = args;
  if (isCfg) { cfg = first; stops = args.slice(1); }
  const stopsKt = stops.map(parseStop).join(', ');
  if (kind === 'radial-gradient') {
    let rx = 0.5, ry = 0.5, cx = 0.5, cy = 0.5;
    if (cfg && !/^closest-side$/.test(cfg)) {
      const mm = /^([\d.]+)%\s+([\d.]+)%\s+at\s+([\d.]+)%\s+([\d.]+)%$/.exec(cfg);
      if (!mm) throw new Error('radial no reconocido: ' + cfg);
      rx = +mm[1] / 100; ry = +mm[2] / 100; cx = +mm[3] / 100; cy = +mm[4] / 100;
    }
    return `KpknGradientLayer.Radial(rx = ${f(rx)}, ry = ${f(ry)}, cx = ${f(cx)}, cy = ${f(cy)}, stops = listOf(${stopsKt}))`;
  }
  let ang = 180;
  if (cfg) {
    const a = /^(-?[\d.]+)deg$/.exec(cfg);
    if (a) ang = +a[1]; else if (cfg === 'to top') ang = 0; else if (cfg === 'to bottom') ang = 180; else throw new Error('angulo no reconocido: ' + cfg);
  }
  return `KpknGradientLayer.Linear(angleDeg = ${f(ang)}, stops = listOf(${stopsKt})${kind === 'repeating-linear-gradient' ? ', repeating = true' : ''})`;
}
// Los cinco degradados con color de módulo / variable se escriben a mano (fábricas con parámetros).
const DYN = {
  'resplandor-ancho-zona': `{ a -> KpknGradientSpec(KpknGradientLayer.Radial(rx = 1.2f, ry = 0.52f, cx = 0.5f, cy = 0.24f, stops = listOf(KpknGradStop(a.zone.copy(alpha = a.zone.alpha * 0.14f)), KpknGradStop(a.zone.copy(alpha = 0f), position = 0.66f)))) }`,
  'resplandor-zona': `{ a -> KpknGradientSpec(KpknGradientLayer.Radial(rx = 0.5f, ry = 0.5f, cx = 0.5f, cy = 0.5f, stops = listOf(KpknGradStop(a.zone), KpknGradStop(a.zone.copy(alpha = 0f))))) }`,
  'relleno-horizontal-blanco': `{ a -> KpknGradientSpec(KpknGradientLayer.Linear(angleDeg = 90f, stops = listOf(KpknGradStop(Color(0x88FFFFFF)), KpknGradStop(a.zone)))) }`,
  'relleno-horizontal-crema-1': `{ a -> KpknGradientSpec(KpknGradientLayer.Linear(angleDeg = 90f, stops = listOf(KpknGradStop(a.ink, position = 0f), KpknGradStop(a.ink, position = a.progress), KpknGradStop(Color(0x1FFFFFFF), position = a.progress), KpknGradStop(Color(0x1FFFFFFF), position = 1f)))) }`,
  'relleno-horizontal-cardio': `{ a -> KpknGradientSpec(KpknGradientLayer.Linear(angleDeg = 90f, stops = listOf(KpknGradStop(a.cardio), KpknGradStop(a.energy)))) }`,
};
const tileOf = { rejilla: '24.dp' };
const gSeen = new Set();
const gradEntries = J.degradados.map(g => {
  const id = pascal(g.nombre); if (gSeen.has(id)) throw new Error('degradado repetido ' + id); gSeen.add(id);
  const doc = `    /** \`${g.nombre}\` · ${g.usos} filas · hoja ${g.hoja.hoja} (${g.hoja.x}, ${g.hoja.y}). */`;
  if (DYN[g.nombre]) return `${doc}\n    ${id}("${g.nombre}", ${DYN[g.nombre]})`;
  const layers = splitTop(g.canonico).map(parseLayer);
  const tile = tileOf[g.nombre] ? `, tile = ${tileOf[g.nombre]}` : '';
  return `${doc}\n    ${id}(\n        "${g.nombre}",\n        KpknGradientSpec(\n${layers.map(l => '            ' + l).join(',\n')}${tile ? ',\n            tile = ' + tileOf[g.nombre] : ''},\n        ),\n    )`;
});
fs.writeFileSync(path.join(OUT, 'KpknGradientCatalog.kt'), `${header('43 degradados')}package com.example.kpkn.ui.design.depth

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Los ${J.degradados.length} degradados canónicos del catálogo del lienzo (velo-*, relleno-*, resplandor-*, vidrio-relleno-*, brillo-esqueleto-*, rayas-*, rejilla, fondo-compuesto-*, vineta…).
 * Los cinco con color de módulo o variable (\`resplandor-ancho-zona\`, \`resplandor-zona\`, \`relleno-horizontal-blanco\`, \`relleno-horizontal-crema-1\`,
 * \`relleno-horizontal-cardio\`) toman sus colores de [KpknGradientArgs].
 */
enum class KpknGradient private constructor(
    /** Nombre EXACTO del preset en \`paridad/cimientos.json\`. */
    val catalogName: String,
    private val build: (KpknGradientArgs) -> KpknGradientSpec,
) {
${gradEntries.join(',\n\n')};

    private constructor(catalogName: String, spec: KpknGradientSpec) : this(catalogName, { _ -> spec })

    /** La spec del degradado; los dinámicos usan [args] (por defecto, los colores de muestra de la hoja del lienzo). */
    fun spec(args: KpknGradientArgs = KpknGradientArgs()): KpknGradientSpec = build(args)

    companion object {
        fun fromCatalogName(name: String): KpknGradient? = entries.firstOrNull { it.catalogName == name }
    }
}
`);

/* ------------------------------------------------------------ DATOS DE LAS HOJAS */
const esc = s => String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$');
const short = (s, n) => s.length > n ? s.slice(0, n - 1) + '…' : s;
const byName = o => Object.fromEntries(o.map(p => [p.nombre, p]));
const SOM = byName(J.sombras), TXT = byName(J.sombrasTexto);
const sheets = [];
// sombras
const groups = { caida: [], anillo: [], interior: [], resplandor: [], nula: [], texto: [] };
for (const h of J.hojas.sombras) { const k = h.id.replace(/^cm-som-/, '').replace(/-\d+$/, ''); groups[k].push(h); }
for (const h of J.hojas.texto) groups.texto.push(h);
const TIT = { caida: 'Sombras · caídas', anillo: 'Sombras · anillos', interior: 'Sombras · realces y bordes interiores', resplandor: 'Sombras · resplandores', nula: 'Sombras · sin sombra', texto: 'Sombras de texto' };
const sampleLine = (p, x, y, lx, ly, lw, u, m, text, shadowId, kind) => `            KpknDepthSample("${esc(p)}", ${x}f, ${y}f, ${lx}f, ${ly}f, ${lw}f, ${u}, ${m}, "${esc(text)}", ${shadowId})`;
for (const k of ['caida', 'anillo', 'interior', 'resplandor', 'nula', 'texto']) {
  groups[k].forEach((h, i) => {
    const isText = k === 'texto';
    const items = h.items.map(n => {
      const p = isText ? TXT[n] : SOM[n]; const hh = p.hoja;
      const text = (p.abreviado || p.canonico);
      return sampleLine(n, hh.x, hh.y, hh.lx, hh.ly, hh.lw, p.usos, p.miembros.length, text, isText ? `KpknTextShadow.${pascal(n)}` : `KpknShadow.${pascal(n)}`);
    });
    sheets.push({ id: h.id, kind: isText ? 'TextShadow' : 'Shadow', title: TIT[k], sub: `${i + 1}/${groups[k].length} · muestras 140 × 84 dp, radio 24`, items });
  });
}
// vidrio
const vids = {}; J.vidrio.forEach(p => { (vids[p.hoja.hoja] = vids[p.hoja.hoja] || []).push(p); });
Object.keys(vids).forEach((id, i, a) => sheets.push({
  id, kind: 'Glass', title: 'Vidrio · backdrop-filter', sub: `${i + 1}/${a.length} · franjas de 312 × 72 dp`,
  items: vids[id].map(p => `            KpknDepthSample("${p.nombre}", ${p.hoja.x}f, ${p.hoja.y}f, ${p.hoja.lx}f, ${p.hoja.ly}f, ${p.hoja.lw}f, ${p.usos}, 1, "${esc(p.canonico)}", KpknGlassPreset.${pascal(p.nombre)})`),
}));
const fils = {}; J.filtros.forEach(p => { (fils[p.hoja.hoja] = fils[p.hoja.hoja] || []).push(p); });
Object.keys(fils).forEach((id, i, a) => sheets.push({
  id, kind: 'Filter', title: 'Filtros · filter', sub: `${i + 1}/${a.length} · muestras de 140 × 84 dp`,
  items: fils[id].map(p => `            KpknDepthSample("${p.nombre}", ${p.hoja.x}f, ${p.hoja.y}f, ${p.hoja.lx}f, ${p.hoja.ly}f, ${p.hoja.lw}f, ${p.usos}, 1, "${esc(p.canonico)}", KpknFilterPreset.${pascal(p.nombre)})`),
}));
const grads = {}; J.degradados.forEach(p => { (grads[p.hoja.hoja] = grads[p.hoja.hoja] || []).push(p); });
Object.keys(grads).sort().forEach((id, i, a) => sheets.push({
  id, kind: 'Gradient', title: 'Degradados y resplandores', sub: `${i + 1}/${a.length} · muestras 140 × 84 dp, radio 24`,
  items: grads[id].map(p => `            KpknDepthSample("${p.nombre}", ${p.hoja.x}f, ${p.hoja.y}f, ${p.hoja.lx}f, ${p.hoja.ly}f, ${p.hoja.lw}f, ${p.usos}, 1, "${esc(short(p.canonico, 92))}", KpknGradient.${pascal(p.nombre)})`),
}));
const ORDER = ['cm-som-caida', 'cm-som-anillo', 'cm-som-interior', 'cm-som-resplandor', 'cm-som-nula', 'cm-som-texto', 'cm-vid-1', 'cm-vid-filtros', 'cm-deg'];
const sheetsKt = sheets.map(s => `        KpknDepthSheetDef(\n            "${s.id}", KpknDepthSheetKind.${s.kind}, "${esc(s.title)}", "${esc(s.sub)}",\n            listOf(\n${s.items.join(',\n')},\n            ),\n        )`).join(',\n');
fs.writeFileSync(path.join(OUT, 'KpknDepthSheetData.kt'), `${header('posiciones y rótulos de las hojas cm-som-*, cm-vid-1, cm-vid-filtros-1 y cm-deg-1…5')}package com.example.kpkn.ui.design.depth

/** Datos de las hojas de calibración de profundidad del lienzo (360 × 880 dp). Los usa [KpknDepthSheet]. */
internal object KpknDepthSheetData {
    val sheets: List<KpknDepthSheetDef> = listOf(
${sheetsKt},
    )
    fun byId(id: String): KpknDepthSheetDef = sheets.first { it.id == id }
}
`);
console.log('ok: ' + J.sombras.length + ' sombras, ' + J.sombrasTexto.length + ' de texto, ' + J.degradados.length + ' degradados, ' + sheets.length + ' hojas -> ' + OUT);
