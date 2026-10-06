/*!
 * KPKN — overlays de "módulo completado" para el wizard de bienvenida.
 * Sin dependencias. SVG + requestAnimationFrame. Funciona en React, Capacitor/WebView y web.
 *
 *   import { showModuleComplete } from "./kpkn-module-overlay.js";
 *   const ov = showModuleComplete("basicos", { onClose: () => irAlSiguientePaso() });
 *   // tipos: "basicos" | "entreno" | "nutricion" | "rings"
 *   // opciones: title, subtitle, cta ("Continuar" | null), autoClose (ms, 0 = espera al botón),
 *   //           container (por defecto document.body), onClose()
 *   // ov.close() lo cierra; ov.closed es una Promise.
 */

const NS = "http://www.w3.org/2000/svg";
const COLORS = {
  ink: "#F2EEE6", ok: "#43D18C", dim: "#3A3D44",
  columna: "#8FB2FF", musculo: "#F49A6E", energia: "#F7CF73", mente: "#C9B8FF",
};

// ---------- utilidades ----------
const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const lerp = (a, b, t) => a + (b - a) * t;
const seg = (t, a, b) => clamp((t - a) / (b - a));
const eOutCubic = t => 1 - Math.pow(1 - t, 3);
const eInOut = t => (t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
const eOutBack = t => { const c1 = 1.9, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); };
const eInQuad = t => t * t;
const spring = (dt, k = 7, w = 22) => (dt < 0 ? 0 : Math.exp(-k * dt) * Math.cos(w * dt));
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const mixHex = (a, b, t) => { const A = hex(a), B = hex(b); t = clamp(t); return `rgb(${A.map((v, i) => Math.round(lerp(v, B[i], t))).join(",")})`; };
const TAU = Math.PI * 2;
let uid = 0;

function el(tag, attrs = {}, parent) {
  const n = document.createElementNS(NS, tag);
  for (const k in attrs) n.setAttribute(k, attrs[k]);
  if (parent) parent.appendChild(n);
  return n;
}
function set(n, attrs) { for (const k in attrs) n.setAttribute(k, attrs[k]); }

// ---------- la Torre anidada (mismas medidas que el logo) ----------
const BASE = [
  { cy: 61, rx: 38, ry: 12,  off: 2,   dx: 5,   dy: 3.5 },
  { cy: 46, rx: 28, ry: 9,   off: 1.5, dx: 4,   dy: 2.5 },
  { cy: 33, rx: 18, ry: 6.5, off: 1,   dx: 3.5, dy: 2.2 },
];
function discPath(i, p = {}) {
  const b = BASE[i], s = p.s ?? 1, q = p.q ?? 0;
  const cx = 50 + (p.x ?? 0), cy = b.cy + (p.y ?? 0);
  const rx = b.rx * s * (1 + q), ry = b.ry * s * (1 - q);
  const irx = Math.max(.1, rx - b.dx * s), iry = Math.max(.1, ry * (b.ry - b.dy) / b.ry), icy = cy - b.off * s;
  const e = (x, y, a, c) => `M${(x - a).toFixed(2)} ${y.toFixed(2)}a${a.toFixed(2)} ${c.toFixed(2)} 0 1 0 ${(2 * a).toFixed(2)} 0a${a.toFixed(2)} ${c.toFixed(2)} 0 1 0 ${(-2 * a).toFixed(2)} 0Z`;
  return e(cx, cy, rx, ry) + e(cx, icy, irx, iry);
}
function makeTower(parent, x, y, scale = 1) {
  const g = el("g", { transform: `translate(${x} ${y}) scale(${scale})` }, parent);
  const paths = [0, 1, 2].map(() => el("path", { "fill-rule": "evenodd", fill: COLORS.ink }, g));
  return {
    g, paths,
    update(states, fills) {
      paths.forEach((p, i) => {
        const st = states[i] || {};
        p.setAttribute("d", discPath(i, st));
        p.setAttribute("fill", (fills && fills[i]) || COLORS.ink);
        p.style.opacity = String(clamp(st.a ?? 1));
      });
    },
  };
}

// ---------- íconos de línea (cuadrícula 24) ----------
const ICONS = {
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-7.2 8-7.2s8 2.8 8 7.2"/>',
  check: '<path d="M7 12.5l3.2 3.2L17 8.8"/>',
  bed: '<path d="M3 19V6M3 15h18v4M21 15v-2.5A3.5 3.5 0 0 0 17.5 9H11v6"/><circle cx="7" cy="11.5" r="2"/>',
  brain: '<path d="M11.5 4.5C10 3 7.5 3.4 7 5.4 5 5.4 3.8 7.4 4.6 9.2 3 10.2 3 12.8 4.8 13.8 4.2 16 5.8 18 8 17.8 8.6 19.8 11 20.2 11.5 18.6V4.5Z"/><path d="M12.5 4.5C14 3 16.5 3.4 17 5.4 19 5.4 20.2 7.4 19.4 9.2 21 10.2 21 12.8 19.2 13.8 19.8 16 18.2 18 16 17.8 15.4 19.8 13 20.2 12.5 18.6V4.5Z"/><path d="M8 9.5c1 .2 2 1 2 2.5M16 9.5c-1 .2-2 1-2 2.5M7.5 14.5c1-.5 2.2-.4 3 .5M16.5 14.5c-1-.5-2.2-.4-3 .5"/>',
  spine: '<rect x="8.5" y="2.5" width="7" height="3.6" rx="1.8"/><rect x="7.5" y="7.6" width="9" height="3.8" rx="1.9"/><rect x="8" y="12.9" width="8" height="3.6" rx="1.8"/><rect x="9" y="18" width="6" height="3.4" rx="1.7"/>',
  dumbbell: '<path d="M6.5 6.5v11M3.5 9v6M17.5 6.5v11M20.5 9v6M6.5 12h11"/>',
};
function makeIcon(parent, name, color, size = 24, sw = 1.9) {
  const outer = el("g", {}, parent);
  const inner = el("g", { transform: `scale(${size / 24}) translate(-12 -12)`, fill: "none", stroke: color, "stroke-width": sw, "stroke-linecap": "round", "stroke-linejoin": "round" }, outer);
  inner.innerHTML = ICONS[name];
  return {
    outer, inner,
    place(x, y, s = 1, o = 1) { outer.setAttribute("transform", `translate(${x.toFixed(2)} ${y.toFixed(2)}) scale(${s.toFixed(3)})`); outer.style.opacity = String(clamp(o)); },
    color(c) { inner.setAttribute("stroke", c); },
  };
}
function makeBadge(parent, size = 11) {
  const g = el("g", {}, parent);
  const c = el("circle", { r: size, fill: COLORS.ok }, g);
  const ic = el("g", { transform: `scale(${(size * 1.25) / 24}) translate(-12 -12)`, fill: "none", stroke: "#08130D", "stroke-width": 2.8, "stroke-linecap": "round", "stroke-linejoin": "round" }, g);
  ic.innerHTML = ICONS.check;
  return { g, place(x, y, s, o = 1) { g.setAttribute("transform", `translate(${x} ${y}) scale(${Math.max(0, s).toFixed(3)})`); g.style.opacity = String(clamp(o)); } };
}

// ---------- escenas ----------
const SCENES = {};

/* 1 · DATOS BÁSICOS — la torre se arma, el usuario sube desde ella y se confirma en verde */
SCENES.basicos = {
  title: "Datos básicos guardados", subtitle: "Ya te conocemos un poco más.", textAt: 1.35, ctaAt: 2.1, viewBox: "32 22 136 136",
  build(svg) {
    const ripple = el("ellipse", { cx: 100, cy: 133, fill: "none", stroke: COLORS.ok }, svg);
    const tower = makeTower(svg, 50, 72);
    const user = makeIcon(svg, "user", COLORS.ink, 46, 2);
    const badge = makeBadge(svg, 10.5);
    return t => {
      const glow = Math.max(0, spring(t - 1.55, 2.6, 0)) * .55;
      tower.update([0, 1, 2].map(i => {
        const t0 = .05 + .18 * i, land = t0 + .3;
        let q = .2 * spring(t - land, 6, 20);
        for (let j = i + 1; j < 3; j++) q += .06 * spring(t - (.35 + .18 * j), 7, 22);
        return { y: (1 - eInQuad(seg(t, t0, land))) * -60, q, a: t < t0 ? 0 : 1, s: 1 + .015 * Math.sin(t * 2.4 + i) * seg(t, 2.3, 2.8) };
      }), [0, 1, 2].map(() => mixHex(COLORS.ink, COLORS.ok, glow)));
      const k = seg(t, .75, 1.25), kb = eOutBack(k);
      user.place(100, 56 + (1 - kb) * 24 + Math.sin(t * 2) * .8 * seg(t, 2, 2.5), lerp(.55, 1, kb), k * 1.6);
      user.color(mixHex(COLORS.ink, COLORS.ok, seg(t, 1.25, 1.6)));
      const b = eOutBack(seg(t, 1.4, 1.75));
      badge.place(123, 40, b, b > 0 ? 1 : 0);
      const d = seg(t, 1.5, 2.35);
      set(ripple, { rx: lerp(38, 82, eOutCubic(d)), ry: lerp(12, 27, eOutCubic(d)), "stroke-width": lerp(2.2, .5, d) });
      ripple.style.opacity = d > 0 && d < 1 ? String((1 - d) * .9) : "0";
    };
  },
};

/* 2 · ENTRENO — sentadilla con barra (el disco es el logo) que se transforma en carrera sobre cinta */
SCENES.entreno = {
  title: "Entreno configurado", subtitle: "Tu plan está listo para empezar.", textAt: 3.1, ctaAt: 3.7, viewBox: "38 74 124 96",
  build(svg) {
    const L = { thigh: 17, shin: 17, torso: 25, uarm: 12.5, farm: 12, head: 6.5 };
    const tread = el("g", {}, svg);
    const deck = el("line", { x1: 56, y1: 153, x2: 150, y2: 153, stroke: COLORS.ink, "stroke-width": 4, "stroke-linecap": "round" }, tread);
    const belt = el("line", { x1: 60, y1: 159, x2: 146, y2: 159, stroke: COLORS.ink, "stroke-width": 1.6, "stroke-linecap": "round", "stroke-dasharray": "5 7", opacity: .55 }, tread);
    const post = el("path", { d: "M146 153L136 108M128 106l14-4", fill: "none", stroke: COLORS.ink, "stroke-width": 4, "stroke-linecap": "round", "stroke-linejoin": "round" }, tread);
    const speed = [0, 1, 2].map(i => el("line", { stroke: COLORS.ok, "stroke-width": 2, "stroke-linecap": "round" }, svg));
    const plate = el("g", { fill: "none", stroke: COLORS.ink, "stroke-width": 2.4 }, svg);
    [12, 8, 4].forEach((r, i) => el("circle", { r, cy: 12 - r }, plate));
    const limb = w => el("path", { fill: "none", "stroke-width": w, "stroke-linecap": "round", "stroke-linejoin": "round" }, svg);
    const backArm = limb(6.5), backLeg = limb(7), torso = limb(8), frontLeg = limb(7.5), frontArm = limb(6.5);
    const head = el("circle", { r: L.head }, svg);
    const add = (a, b, k = 1) => [a[0] + b[0] * k, a[1] + b[1] * k];
    const dir = ang => [Math.sin(ang), Math.cos(ang)]; // ángulo desde la vertical hacia abajo, + = hacia adelante
    function ik(a, b, l1, l2, sign) {
      const dx = b[0] - a[0], dy = b[1] - a[1], d = Math.min(Math.hypot(dx, dy), l1 + l2 - .01);
      const base = Math.atan2(dy, dx), al = Math.acos(clamp((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d), -1, 1));
      return [a[0] + l1 * Math.cos(base + sign * al), a[1] + l1 * Math.sin(base + sign * al)];
    }
    function squat(d) {
      const hip = [100 - 10 * d, 117 + 15 * d], lean = .12 + .55 * d;
      const f1 = [106, 150], f2 = [101, 150];
      const k1 = ik(hip, f1, L.thigh, L.shin, -1), k2 = ik(hip, f2, L.thigh, L.shin, -1);
      const sh = add(hip, [Math.sin(lean), -Math.cos(lean)], L.torso);
      const hd = add(sh, [Math.sin(lean) * .9, -Math.cos(lean)], 10);
      const bar = add(sh, [-4, 1]);
      const h1 = add(bar, [5, 3]), h2 = add(bar, [3, 4]);
      return { hip, k1, f1, k2, f2, sh, hd, bar, h1, h2, e1: ik(sh, h1, L.uarm, L.farm, 1), e2: ik(sh, h2, L.uarm, L.farm, 1) };
    }
    function run(ph) {
      const lean = .2, legs = [0, Math.PI].map(o => {
        const th = .58 * Math.sin(ph + o), bend = .25 + 1.15 * Math.max(0, Math.cos(ph + o));
        const k = dir(th), f = dir(th - bend);
        return { k: [k[0] * L.thigh, k[1] * L.thigh], f: [k[0] * L.thigh + f[0] * L.shin, k[1] * L.thigh + f[1] * L.shin] };
      });
      const low = Math.max(legs[0].f[1], legs[1].f[1]);
      const hip = [98, 148.5 - low - 1.2 * Math.abs(Math.cos(ph))];
      const sh = add(hip, [Math.sin(lean), -Math.cos(lean)], L.torso);
      const arms = [Math.PI, 0].map(o => { const a = -.65 * Math.sin(ph + o); const e = add(sh, dir(a), L.uarm); return { e, h: add(e, dir(a + 1.5), L.farm) }; });
      return {
        hip, k1: add(hip, legs[0].k), f1: add(hip, legs[0].f), k2: add(hip, legs[1].k), f2: add(hip, legs[1].f),
        sh, hd: add(sh, [Math.sin(lean) * .9, -Math.cos(lean)], 10), bar: sh,
        e1: arms[0].e, h1: arms[0].h, e2: arms[1].e, h2: arms[1].h,
      };
    }
    const mixPose = (A, B, t) => { const o = {}; for (const k in A) o[k] = [lerp(A[k][0], B[k][0], t), lerp(A[k][1], B[k][1], t)]; return o; };
    const P = p => `M${p.map(q => q[0].toFixed(2) + " " + q[1].toFixed(2)).join("L")}`;
    return t => {
      const reps = t < 2.2 ? .5 - .5 * Math.cos(TAU * Math.max(0, t - .2) / 1.0) : 0;
      const b = eInOut(seg(t, 2.2, 2.85));
      const pose = b <= 0 ? squat(reps) : mixPose(squat(0), run(TAU * (t - 2.2) / .62), b);
      const col = mixHex(COLORS.ink, COLORS.ok, seg(t, 2.4, 2.95));
      [backArm, backLeg, torso, frontLeg, frontArm].forEach(p => p.setAttribute("stroke", col));
      head.setAttribute("fill", col);
      backLeg.setAttribute("d", P([pose.hip, pose.k2, pose.f2])); backLeg.style.opacity = ".55";
      backArm.setAttribute("d", P([pose.sh, pose.e2, pose.h2])); backArm.style.opacity = ".55";
      torso.setAttribute("d", P([pose.hip, pose.sh]));
      frontLeg.setAttribute("d", P([pose.hip, pose.k1, pose.f1]));
      frontArm.setAttribute("d", P([pose.sh, pose.e1, pose.h1]));
      set(head, { cx: pose.hd[0], cy: pose.hd[1] });
      const pf = seg(t, 2.2, 2.55);
      plate.setAttribute("transform", `translate(${pose.bar[0]} ${pose.bar[1] - pf * 30}) scale(${1 - pf * .6})`);
      plate.style.opacity = String((1 - pf) * seg(t, 0, .3));
      const tr = eOutCubic(seg(t, 2.25, 2.8));
      tread.style.opacity = String(tr);
      tread.setAttribute("transform", `translate(${(1 - tr) * 30} 0)`);
      belt.setAttribute("stroke-dashoffset", String(t * 70));
      speed.forEach((s, i) => {
        const ph = (t * 1.6 + i * .33) % 1, x = 70 - ph * 26, y = 112 + i * 9;
        set(s, { x1: x, y1: y, x2: x + 12 * (1 - ph), y2: y });
        s.style.opacity = String(seg(t, 2.8, 3.1) * Math.sin(Math.PI * ph) * .8);
      });
    };
  },
};

/* 3 · NUTRICIÓN — alimentos orbitan la torre en perspectiva y se integran a ella */
const FOODS = {
  apple: '<path d="M0-4c-3-2.4-8-1.6-8 3.6 0 4.6 3.6 8.4 6 8.4 1 0 1.4-.6 2-.6s1 .6 2 .6c2.4 0 6-3.8 6-8.4 0-5.2-5-6-8-3.6z" fill="#E8574A"/><path d="M0-4c0-2 1-4 3-5" stroke="#7A4A2A" stroke-width="1.4" fill="none" stroke-linecap="round"/><path d="M1-6c2-2.6 5-2.6 6-1.4-1.6 1.8-4 2.2-6 1.4z" fill="#6BBF59"/>',
  avocado: '<path d="M0-9c3.5 0 4.5 4 5.5 7 1.5 4 2 9-5.5 9s-7-5-5.5-9c1-3 2-7 5.5-7z" fill="#5E9E4A"/><path d="M0-6c2.4 0 3 3 3.8 5.2 1 3 1.2 6.2-3.8 6.2s-4.8-3.2-3.8-6.2C-3-3-2.4-6 0-6z" fill="#D7E8A2"/><circle cx="0" cy="2" r="2.8" fill="#8A5A3B"/>',
  egg: '<path d="M-7-2c0-5 5-7 8-5 3-2 8 0 7 5 3 3 0 8-4 8-2 2-6 2-8 0-4 0-6-4-3-8z" fill="#F7F3EA"/><circle cx=".5" cy="0" r="3.4" fill="#F5B82E"/>',
  carrot: '<path d="M-7 8.5L3.5-4.5c1.6-1.8 4.4-.2 3.5 2L-3 9.6c-1 1.4-3.2 1-4-1.1z" fill="#F08A3C"/><path d="M5.5-4.5l1-4.5M5.5-4.5l4.6-1M5.5-4.5l3.4-3.4" stroke="#6BBF59" stroke-width="1.6" stroke-linecap="round" fill="none"/>',
  fish: '<path d="M-9 0c3-5.5 10-5.5 13 0-3 5.5-10 5.5-13 0z" fill="#8FB2FF"/><path d="M3.5 0l5.5-4.5v9z" fill="#8FB2FF"/><circle cx="-5" cy="-1" r="1.1" fill="#0B0B0B"/>',
  broccoli: '<path d="M-1.6 2h3.2v7.5h-3.2z" fill="#7FB069"/><circle cx="-4.2" cy="-.5" r="4.2" fill="#4E9A47"/><circle cx="4.2" cy="-.5" r="4.2" fill="#4E9A47"/><circle cx="0" cy="-4.5" r="4.8" fill="#5FAE53"/>',
  banana: '<path d="M-8.5-3c2 7 10.5 9 16.5 3 .6-.6 1.5 0 1 .9-5 8.2-15.5 7.2-18.5-3 0-.8.8-1.5 1-.9z" fill="#F7CF73"/>',
};
SCENES.nutricion = {
  title: "Nutrición configurada", subtitle: "Tus metas de alimentación quedaron guardadas.", textAt: 2.65, ctaAt: 3.25, viewBox: "14 46 172 116",
  build(svg) {
    const back = el("g", {}, svg);
    const tower = makeTower(svg, 50, 56);
    const front = el("g", {}, svg);
    const badge = makeBadge(svg, 10.5);
    const orbits = [0, 1, 2].map(k => ({ cy: BASE[k].cy + 56, rx: BASE[k].rx * 2, ry: BASE[k].ry * 2.1, w: [.85, 1.05, 1.3][k] }));
    const list = [["apple", 0, 0], ["fish", 0, 2.1], ["broccoli", 0, 4.2], ["avocado", 1, 1], ["egg", 1, 4.1], ["carrot", 2, .4], ["banana", 2, 3.5]];
    const foods = list.map(([name, o, ph]) => { const g = el("g", {}, back); g.innerHTML = FOODS[name]; return { g, o, ph, inFront: false }; });
    return t => {
      const enter = eOutCubic(seg(t, 0, 1)), merge = eInOut(seg(t, 2.25, 2.8));
      foods.forEach((f, i) => {
        const O = orbits[f.o], a = f.ph + O.w * t;
        const rf = (1 + .9 * (1 - enter)) * lerp(1, .12, merge);
        const x = 100 + O.rx * rf * Math.cos(a), y = O.cy + O.ry * rf * Math.sin(a) - (1 - enter) * 20;
        const depth = (Math.sin(a) + 1) / 2;
        const s = lerp(.72, 1.12, depth) * lerp(1, .25, merge) * 1.35;
        f.g.setAttribute("transform", `translate(${x.toFixed(2)} ${y.toFixed(2)}) scale(${s.toFixed(3)}) rotate(${(Math.sin(t * 1.7 + i) * 12).toFixed(1)})`);
        f.g.style.opacity = String(seg(t, .05 * i, .05 * i + .4) * lerp(.55, 1, depth) * (1 - seg(t, 2.6, 2.8)));
        const wantFront = Math.sin(a) > 0;
        if (wantFront !== f.inFront) { (wantFront ? front : back).appendChild(f.g); f.inFront = wantFront; }
      });
      const g = Math.max(0, spring(t - 2.75, 2.4, 0)) * .7;
      tower.update([0, 1, 2].map(i => ({ s: 1 + .06 * Math.max(0, spring(t - 2.75 - .06 * i, 5, 12)) + .012 * Math.sin(t * 2.2 + i), a: seg(t, .1 * i, .1 * i + .4) })),
        [0, 1, 2].map(() => mixHex(COLORS.ink, COLORS.ok, g)));
      const b = eOutBack(seg(t, 2.8, 3.15));
      badge.place(100, 66, b, b > 0 ? 1 : 0);
    };
  },
};

/* 4 · RINGS — columna, músculo y energía se encienden con destellos; sueño y mente alrededor */
SCENES.rings = {
  title: "Tus Rings están activos", subtitle: "Músculo, energía y columna, medidos cada día.", textAt: 2.1, ctaAt: 2.7, viewBox: "12 20 176 140",
  build(svg) {
    const id = "kpknGlow" + (++uid);
    const defs = el("defs", {}, svg);
    const f = el("filter", { id, x: "-60%", y: "-60%", width: "220%", height: "220%" }, defs);
    el("feGaussianBlur", { stdDeviation: 3.2 }, f);
    const glow = makeTower(svg, 50, 64); glow.g.setAttribute("filter", `url(#${id})`);
    const tower = makeTower(svg, 50, 64);
    const ringCols = [COLORS.columna, COLORS.musculo, COLORS.energia];
    const icons = [
      { ic: makeIcon(svg, "spine", COLORS.columna, 27), x: 160, y: 104, at: .3 },
      { ic: makeIcon(svg, "dumbbell", COLORS.musculo, 27), x: 140, y: 56, at: .75 },
      { ic: makeIcon(svg, "bed", COLORS.energia, 27), x: 40, y: 104, at: 1.2 },
      { ic: makeIcon(svg, "brain", COLORS.mente, 27), x: 60, y: 56, at: 1.6 },
    ];
    const SP = [[30, 70, 0], [172, 72, 1], [100, 30, 2], [80, 150, 0], [126, 150, 1], [50, 132, 2], [152, 132, 0], [118, 34, 1], [22, 112, 2], [178, 114, 1], [88, 44, 0], [142, 88, 2]];
    const sparks = SP.map(([x, y, c], i) => ({ x, y, ph: i * 1.7, p: el("path", { d: "M0-5L1.2-1.2 5 0 1.2 1.2 0 5-1.2 1.2-5 0-1.2-1.2Z", fill: ringCols[c] }, svg) }));
    return t => {
      const lit = [0, 1, 2].map(i => seg(t, .3 + .45 * i, .7 + .45 * i));
      const flick = [0, 1, 2].map(i => .78 + .22 * Math.sin(t * 9 + i * 2.1) * Math.sin(t * 3.3 + i));
      const st = [0, 1, 2].map(i => ({ s: 1 + .07 * Math.sin(Math.PI * lit[i]) + .012 * Math.sin(t * 2 + i), a: seg(t, 0, .3) }));
      tower.update(st, [0, 1, 2].map(i => mixHex(COLORS.dim, ringCols[i], lit[i])));
      glow.update(st.map((s, i) => ({ ...s, a: lit[i] * flick[i] * .9 })), ringCols);
      icons.forEach((o, i) => {
        const k = eOutBack(seg(t, o.at, o.at + .4));
        o.ic.place(o.x, o.y + Math.sin(t * 2 + i * 1.3) * 2 * seg(t, o.at + .4, o.at + .8), Math.max(0, k), seg(t, o.at, o.at + .2));
      });
      sparks.forEach(s => {
        const tw = Math.pow(Math.max(0, Math.sin(t * 3.4 + s.ph)), 3) * seg(t, .6, 1.2);
        s.p.setAttribute("transform", `translate(${s.x} ${s.y}) scale(${(tw * .9).toFixed(3)}) rotate(${(t * 40 + s.ph * 30).toFixed(1)})`);
        s.p.style.opacity = String(tw);
      });
    };
  },
};

// ---------- overlay ----------
const CSS = `
.kpkn-ov{position:fixed;inset:0;z-index:2147483000;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;padding:24px;box-sizing:border-box;
  background:rgba(6,6,6,.52);-webkit-backdrop-filter:blur(18px) saturate(1.15);backdrop-filter:blur(18px) saturate(1.15);
  opacity:0;transition:opacity .28s ease;font-family:Syne,system-ui,-apple-system,"Segoe UI",sans-serif;color:#F2EEE6;text-align:center;-webkit-tap-highlight-color:transparent}
.kpkn-ov.is-in{opacity:1}
.kpkn-ov--contained{position:absolute}
.kpkn-ov svg{width:min(90%,380px);max-height:52%;height:auto;overflow:visible;display:block}
.kpkn-ov__title{margin:0;font-weight:800;font-size:clamp(20px,6vw,26px);letter-spacing:-.01em;line-height:1.15;text-wrap:balance;opacity:0;transform:translateY(10px);transition:opacity .4s ease,transform .55s cubic-bezier(.2,.9,.3,1.2)}
.kpkn-ov__sub{margin:0;font-weight:500;font-size:14px;line-height:1.4;max-width:30ch;color:rgba(242,238,230,.72);opacity:0;transform:translateY(6px);transition:opacity .4s ease .08s,transform .5s ease .08s}
.kpkn-ov__cta{margin-top:16px;font:600 15px Syne,system-ui,sans-serif;color:#0B0B0B;background:#F2EEE6;border:0;border-radius:999px;padding:12px 28px;min-height:46px;min-width:160px;cursor:pointer;opacity:0;transform:translateY(8px);transition:opacity .35s ease,transform .35s ease}
.kpkn-ov__cta:focus-visible{outline:2px solid #43D18C;outline-offset:3px}
.kpkn-ov.show-text .kpkn-ov__title,.kpkn-ov.show-text .kpkn-ov__sub{opacity:1;transform:none}
.kpkn-ov.show-cta .kpkn-ov__cta{opacity:1;transform:none}
@media (prefers-reduced-motion:reduce){.kpkn-ov,.kpkn-ov *{transition:none!important}}`;

function injectCSS() {
  if (document.getElementById("kpkn-ov-style")) return;
  const s = document.createElement("style"); s.id = "kpkn-ov-style"; s.textContent = CSS; document.head.appendChild(s);
}

export function showModuleComplete(type, opts = {}) {
  const scene = SCENES[type];
  if (!scene) throw new Error(`KPKN overlay: tipo desconocido "${type}"`);
  injectCSS();
  const container = opts.container || document.body;
  const root = document.createElement("div");
  root.className = "kpkn-ov" + (container === document.body ? "" : " kpkn-ov--contained");
  root.setAttribute("role", "dialog"); root.setAttribute("aria-modal", "true");
  const title = opts.title ?? scene.title, sub = opts.subtitle ?? scene.subtitle, cta = opts.cta === undefined ? "Continuar" : opts.cta;
  root.setAttribute("aria-label", title);
  const svg = el("svg", { viewBox: scene.viewBox || "0 0 200 200", "aria-hidden": "true" }, root);
  const h = document.createElement("p"); h.className = "kpkn-ov__title"; h.textContent = title; root.appendChild(h);
  const p = document.createElement("p"); p.className = "kpkn-ov__sub"; p.textContent = sub; root.appendChild(p);
  let btn = null;
  if (cta) { btn = document.createElement("button"); btn.type = "button"; btn.className = "kpkn-ov__cta"; btn.textContent = cta; root.appendChild(btn); }
  container.appendChild(root);

  const update = scene.build(svg);
  const reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  let raf = 0, t0 = 0, done = false, resolve;
  const closed = new Promise(r => (resolve = r));
  function frame(now) {
    if (!t0) t0 = now;
    const t = reduce ? scene.ctaAt + 1 : (now - t0) / 1000;
    update(t);
    if (t >= scene.textAt) root.classList.add("show-text");
    if (t >= scene.ctaAt) { if (!root.classList.contains("show-cta")) { root.classList.add("show-cta"); if (btn) btn.focus({ preventScroll: true }); } }
    if (opts.autoClose && t * 1000 >= opts.autoClose) { close(); return; }
    if (!done) raf = requestAnimationFrame(frame);
  }
  function close(silent) {
    if (done) return; done = true;
    cancelAnimationFrame(raf);
    root.classList.remove("is-in");
    document.removeEventListener("keydown", onKey);
    setTimeout(() => { root.remove(); resolve(); if (!silent && opts.onClose) opts.onClose(); }, 300);
  }
  const onKey = e => { if (e.key === "Escape" || (e.key === "Enter" && root.classList.contains("show-cta"))) close(); };
  document.addEventListener("keydown", onKey);
  if (btn) btn.addEventListener("click", () => close());
  else root.addEventListener("click", () => { if (root.classList.contains("show-text")) close(); });
  requestAnimationFrame(() => root.classList.add("is-in"));
  raf = requestAnimationFrame(frame);
  return { close: () => close(), closeSilently: () => close(true), closed, element: root };
}

export const KPKN_MODULES = Object.keys(SCENES);
if (typeof window !== "undefined") window.KpknCelebrate = { show: showModuleComplete, modules: KPKN_MODULES };
