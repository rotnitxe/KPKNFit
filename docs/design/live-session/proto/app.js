/* KPKN · motor del prototipo. Sin dependencias. */

window.addEventListener('error', (ev) => {
  const d = document.createElement('div');
  d.id = 'js-error';
  d.textContent = 'Error del prototipo: ' + (ev.message || 'desconocido');
  d.style.cssText = 'position:fixed;left:12px;bottom:12px;z-index:9999;background:#3a1111;color:#fff;padding:8px 12px;border-radius:8px;font:12px monospace';
  document.body.appendChild(d);
});

const $  = (s, r=document) => r.querySelector(s);
const $$ = (s, r=document) => Array.from(r.querySelectorAll(s));
const esc = (s) => String(s == null ? '' : s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');

const ICONS = {
  book:'M4 5.5A2.5 2.5 0 0 1 6.5 3H11v18H6.5A2.5 2.5 0 0 0 4 20.5zM20 5.5A2.5 2.5 0 0 0 17.5 3H13v18h4.5A2.5 2.5 0 0 1 20 20.5z',
  mic:'M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5 11a7 7 0 0 0 14 0M12 18v3M8 21h8',
  more:'<circle cx="5" cy="12" r="1.15" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.15" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1.15" fill="currentColor" stroke="none"/>',
  timer:'M12 13V9.5M12 13l2.5 1.5M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM9.5 2h5M12 2v3',
  plus:'M12 5v14M5 12h14',
  check:'M5 12.5l4.5 4.5L19 7',
  update:'M4 4v6h6M20 20v-6h-6M5.5 15a7 7 0 0 0 11.8 2.2M18.5 9A7 7 0 0 0 6.7 6.8',
  undo:'M9 14L4 9l5-5M4 9h9a7 7 0 0 1 0 14H7',
  chevronLeft:'M15 5l-7 7 7 7',
  chevronRight:'M9 5l7 7-7 7',
  chevronDown:'M5 9l7 7 7-7',
  close:'M6 6l12 12M18 6L6 18',
  camera:'M4 8h3l2-3h6l2 3h3v11H4zM12 16.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z',
  album:'M3 4h18v16H3zM3 16l5-4 4 3 3-2 6 5M9 8.2a1.2 1.2 0 1 1-2.4 0 1.2 1.2 0 0 1 2.4 0z',
  info:'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 11v5M12 8h.01',
  expand:'M4 9V4h5M20 15v5h-5M4 4l6 6M20 20l-6-6',
  play:'M8 5.5v13l10-6.5z',
  pause:'M8 5h2.5v14H8zM13.5 5H16v14h-2.5z',
  stop:'M6 6h12v12H6z',
  route:'M6 20v-5a4 4 0 0 1 4-4h4a4 4 0 0 0 4-4V4M6 21.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z',
  star:'M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1.1 5.9L12 16.9l-5.3 2.8 1.1-5.9-4.3-4.1 5.9-.8z',
  swap:'M4 8h13l-3-3M20 16H7l3 3',
  history:'M3 12a9 9 0 1 0 3-6.7M3 4v5h5M12 7v5l3 2',
  arrowLeft:'M4 12h16M4 12l6-6M4 12l6 6',
  arrowRight:'M20 12H4M20 12l-6-6M20 12l-6 6',
  video:'M4 7h11v10H4zM15 10l5-3v10l-5-3z',
  flame:'M12 3c3 4 5 6 5 9a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3 1-5 1-8z',
  search:'M10.5 18a7.5 7.5 0 1 0 0-15 7.5 7.5 0 0 0 0 15zM16 16l4.5 4.5',
  body:'M12 6.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM12 7v6M12 9l-3.5 3M12 9l3.5 3M12 13l-3 7M12 13l3 7',
  expand2:'M4 9V4h5M20 15v5h-5M4 4l6 6M20 20l-6-6',
};
function icon(name, size=16){
  const p = ICONS[name] || '';
  return `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${p}</svg>`;
}

let state = {id:null, face:null, sheet:null, annotations:false, motion:false, filter:'all', q:''};
let current = null;

const SHEET_SRC = {
  menu:['LiveSessionSheets.kt:103-108'], route:['LiveSessionSheets.kt:111-119','CockpitSessionSummary.kt:46-154'],
  milestones:['LiveSessionSheets.kt:121','LiveSessionNotesAndMilestones.kt:28-121'], notes:['LiveSessionNotesAndMilestones.kt:134-233'],
  timer:['LiveSessionSheets.kt:138-155'], rmcalc:['WorkoutContextComponents.kt:557-691'],
  calories:['LiveSessionSheets.kt:160-165'], sessiontime:['SessionTimeSheet.kt:32-127'],
  ultrapreview:['UltraFastSheet.kt:33-141'], ultrarecord:['UltraFastRecordSheet.kt:35-108'],
  options:['ExerciseOptionsSheet.kt:43-243'], series:['SeriesTypeSheet.kt:84-412'],
  tags:['WorkoutExerciseSheets.kt:226-502'], reorder:['LiveReorderSheet.kt:98-248'],
  album:['ExerciseAlbumSheet.kt:81-308'], discomfort:['DiscomfortSheet.kt:50-171'],
  history:['WorkoutHistorySheetContent.kt:46-197'], exit:['WorkoutSessionOverlaysHost.kt:123-192'],
  finish:['RecordedSessionFinishSheet.kt:71-217'],
};

/* ── geometría ── */
function sceneSize(s){
  const w = (s.device && s.device.w) || 393;
  const h = (s.device && s.device.h) || 852;
  const cw = Math.min(w - 48, 358);
  return {w, h, cw, ch: cw / (312/425), compact: h < 800};
}
function applyGeometry(s){
  const g = sceneSize(s);
  const dev = $('#device'), pins = $('#pins');
  dev.style.width = g.w + 'px'; dev.style.height = g.h + 'px';
  pins.style.width = g.w + 'px'; pins.style.height = g.h + 'px';
  dev.style.setProperty('--pw', g.w + 'px');
  dev.style.setProperty('--ph', g.h + 'px');
  dev.style.setProperty('--card-w', g.cw + 'px');
  dev.style.setProperty('--card-h', g.ch + 'px');
}

/* ── chrome ── */
function chromeOf(s){ return Object.assign({}, CHROME, s.chrome || {}); }

function statusbar(){
  return `<div class="k-statusbar"><span>1:47</span><div class="sig">
    <div class="bars"><i></i><i></i><i></i><i></i></div>
    <div class="batt"><i></i></div>
  </div></div>`;
}

function header(c){
  const titleChips = (c.chips || []).map(x => `<span class="chip"> · ${esc(x)}</span>`).join('');
  const sub = [c.protocol, c.group, c.session].filter(Boolean).join(' · ');
  let chips = '';
  (c.railChips || []).forEach(ch => {
    if (ch.type === 'timer') {
      chips += `<span class="k-chip"><span class="k-ring">${icon('timer',10)}</span>${esc(ch.label)}${ch.limit ? `<span class="sub"> / ${esc(ch.limit)}</span>` : ''}</span>`;
    } else if (ch.label === 'Superserie') {
      chips += `<span class="k-chip red">${icon('swap',12)}${esc(ch.label)}</span>`;
    } else {
      chips += `<span class="k-chip ${ch.cls||''}">${esc(ch.label)}${ch.dd ? '<span class="dd">▾</span>' : ''}${ch.x ? '<span class="x">×</span>' : ''}</span>`;
    }
  });
  const longTitle = (c.exercise + ' ' + (c.chips||[]).join(' ')).length > 30;
  return `<div class="k-header"><div class="k-header-inner">
    <div class="k-hrow">
      <div class="k-titlecol">
        <div class="k-title ${longTitle?'marquee':''}"><span class="mq">${esc(c.exercise)}${titleChips}</span></div>
        <div class="k-sub">${esc(sub)}</div>
      </div>
      <div class="k-icons">
        <div class="k-icon"><div class="cir">${icon('book',20)}</div></div>
        <div class="k-icon"><div class="cir">${icon('mic',20)}</div></div>
        <div class="k-icon"><div class="cir">${icon('more',20)}</div></div>
      </div>
    </div>
    <div class="k-chips">${chips}</div>
  </div></div>`;
}

function rail(c, d, opts={}){
  const done = !!(d && d.done);
  let steps = '';
  (c.steps || []).forEach(st => {
    const cls = ['k-step'];
    if (st.state === 'active') cls.push('active');
    if (st.state === 'done') cls.push('done');
    if (st.state === 'skipped') cls.push('skipped');
    if (st.calibrated) cls.push('cal');
    const label = st.state === 'done' ? '✓' + st.label : st.label;
    steps += `<div class="${cls.join(' ')}"><div class="k-steppill">${esc(label)}</div>${st.calibrated ? '<span class="cal">calibrada</span>' : ''}</div>`;
  });
  const undo = d && d.undo ? `<div class="k-miniundo" title="${esc(d.undo)}">${icon('undo',18)}</div>` : '';
  const activeIdx = (c.steps || []).findIndex(st => st.state === 'active');
  const shift = Math.max(0, (activeIdx < 0 ? 0 : activeIdx) * 48 - 120);
  return `<div class="k-rail">
    <div class="k-routerow">
      <div class="k-route">${icon('route',20)}<div class="txt"><b>${esc(c.route)}</b><span>${esc(c.routeSub)}</span></div>${icon('chevronRight',16)}</div>
      ${undo}
      <button class="k-addpill" data-act="sheet:series">+</button>
    </div>
    <div class="k-steprow">
      <div class="k-stepcarril"><div class="k-steprail" style="transform:translateX(-${shift}px)">${steps}</div></div>
      <button class="k-record" data-act="record" aria-label="${done ? 'Actualizar serie' : 'Registrar serie'}">${icon(done ? 'update' : 'check', 25)}</button>
    </div>
  </div>`;
}

/* ── piezas de tarjeta ── */
function tagChip(t, cls=''){ return `<span class="k-tag ${cls}">${esc(t)}</span>`; }

function hero(d){
  const load = d.load == null ? 100 : d.load;
  const required = !!d.loadRequired;
  const isDash = load === '—' || required;
  const text = required ? 'Carga a elegir' : String(load);
  const long = text.length >= 7;
  const unit = required ? '' : (d.unit || 'kg');
  let side = '';
  if (d.mode === 'AMRAP') side = `<b>Mín. ${d.amrapMin || 8}</b><span>${d.result ? '11 reps' : 'objetivo'}</span>`;
  else if (d.mode === 'CALIB') side = `<b>Sin mínimo</b><span>ajusta el 1RM</span>`;
  else if (d.mode === 'DONE' || d.done) side = `<b>RIR ${d.rir == null ? 2 : d.rir}</b><span>e1RM 112,5</span>`;
  else side = `<b>RIR ${d.rir == null ? 2 : d.rir}</b><span>e1RM 112,5</span>`;
  const chip = (l,v) => `<div class="k-chip3"><span>${esc(l)}</span><b>${esc(v)}</b></div>`;
  const baseLoad = parseFloat(String(load).replace(',', '.'));
  const sug = d.suggested != null
    ? String(d.suggested).replace('.', ',')
    : (isFinite(baseLoad) && baseLoad > 0 ? String(baseLoad + 2.5).replace('.', ',') : '—');
  return `<div class="k-hero">
    <div class="k-hero-label">${d.solo ? 'PESO CORPORAL' : 'CARGA (kg)'}</div>
    <div class="k-hero-main"><span class="k-hero-val ${long || required ? 'sm' : ''}" style="${required ? 'color:var(--faint)' : ''}">${esc(text)}</span>${unit ? `<span class="k-hero-unit">${esc(unit)}</span>` : ''}<span class="k-hero-side">${side}</span></div>
    <div class="k-chips3">${chip('−2,5','')}${chip('Sugerido', isDash ? '—' : sug)}${chip('+2,5','')}</div>
    <div class="k-hero-foot">${icon('chevronDown',12)} ${d.solo ? 'Corporal' : 'Externa'}</div>
  </div>`;
}

function rmHero(d){
  return `<div class="k-rmhero">
    <div class="k-hero-label">CARGA UTILIZADA · kg</div>
    <div class="top"><span class="big">${esc(d.load || 105)}</span><span class="pct">${esc(d.rmPct || 89)} %</span><span class="k-hero-unit" style="padding-bottom:8px">del 1RM</span></div>
    <div class="sub">Calculado: ${esc(d.target || '105 kg')} · ${esc(d.rm || 3)} reps est.</div>
    <div class="ref"><span>Tu referencia</span><b>1RM 118,0 kg</b></div>
  </div>`;
}

function timeHero(d){
  return `<div class="k-hero" style="justify-content:center;gap:6px">
    <div class="k-hero-label">CRONÓMETRO DE LA SERIE</div>
    <div class="k-hero-main"><span class="k-hero-val">${esc(d.clock || '00:45')}</span><span class="k-hero-side" style="padding-bottom:6px"><b>${esc(d.target || '45″')}</b><span>objetivo</span></span></div>
    <div class="k-hero-foot" style="justify-content:space-between">${icon('stop',12)} Detener <span style="color:var(--faint)">Tiempo libre</span></div>
  </div>`;
}

function compactLoad(d){
  const solo = d.solo || d.mode === 'SOLO';
  const v = d.load == null || d.load === '' ? '—' : d.load;
  return `<div class="k-compactload"><span class="lbl">${solo ? 'PESO CORPORAL' : 'CARGA'}</span><span class="field">${esc(v)}</span><span class="mode">${esc(d.loadMode || (solo ? 'Corporal' : 'Externa'))} ${icon('chevronDown',10)}</span></div>`;
}

function wheel(d){
  const reps = Number(d.reps || 8);
  const vals = [reps-2, reps-1, reps, reps+1, reps+2];
  return `<div class="k-wheel">${vals.map((v,i) => `<span class="k-wcell ${i===2?'c':(i===0||i===4?'dim':'')}">${i===2?`<b>${v}</b>`:v}</span>`).join('')}</div>`;
}

function rmControls(d){
  const pct = d.rmPct || 89;
  return `<div class="k-rmrow"><span class="l">% RM</span><div class="k-slider" style="flex:1;margin:0 10px"><i style="width:${pct}%"></i><b style="left:${pct}%"></b></div><span class="v">${pct} %</span></div>
  <div class="k-rmrow"><span class="l">PESO · kg</span><span class="v">${esc(d.load||105)}</span><span class="dim">calculado</span></div>
  <div class="k-rmrow"><span class="l">REFERENCIA 1RM · kg</span><span class="v" style="font-size:18px">118,0</span></div>`;
}

function effort(d){
  const rir = d.rir;
  const rpe = d.rpe != null ? Number(String(d.rpe).replace(',','.')) : (rir != null ? 10 - Number(rir) : null);
  let fill = 58, idx = 2, right = 'Sin marcar';
  if (d.failure) { fill = 100; idx = 5; right = 'Fallo · todo'; }
  else if (rpe != null) {
    fill = Math.max(6, Math.min(100, rpe * 10));
    idx = Math.max(0, Math.min(5, Math.round((rpe - 5) * 1.2)));
    const words = ['cómodo','firme','exigente','muy duro','al límite','todo'];
    right = (rir != null ? `RIR ${rir}` : `RPE ${String(d.rpe).replace('.',',')}`) + ' · ' + words[idx];
  }
  const labels = ['cómodo','firme','exigente','muy duro','al límite','todo'];
  const plan = d.planRir != null ? Math.max(4, Math.min(96, (10 - d.planRir) * 10)) : 35;
  return `<div class="k-effort">
    <div class="k-effort-head"><span class="k-eyebrow">ESFUERZO</span><span class="r">${esc(right)}</span></div>
    <div class="k-trackwrap"><div class="k-track"><i class="fill" style="width:${fill}%"></i><i class="plan" style="left:${plan}%"></i><i class="thumb" style="left:${fill}%"></i></div></div>
    <div class="k-effort-labels">${labels.map((w,i) => `<span class="${i===idx?'on':''} ${w==='todo'?'fail':''}">${w}</span>`).join('')}</div>
    ${d.hint ? `<div class="k-hint">${esc(d.hint)}</div>` : ''}
  </div>`;
}

function romBlock(d){
  return `<div class="k-rom"><span class="k-eyebrow">ROM</span><div class="k-slider"><i style="width:100%"></i><b style="left:100%"></b></div><span class="val">${esc(d.rom || 100)} %</span></div>`;
}

function statusLine(d){
  if (d.status) return `<div class="k-appstatus ${d.statusErr ? 'err' : ''}">${esc(d.status)}</div>`;
  if (d.loadRequired) return `<div class="k-appstatus">Elige una carga para registrar la serie.</div>`;
  return `<div class="k-appstatus">Registra el resultado realizado con ✓</div>`;
}

function actionsFront(){
  return `<div class="k-actions">
    <button class="k-action" data-act="flip-tech">Avanzado</button>
    <button class="k-action" data-act="flip-media">Modo visual</button>
    <button class="k-action icon" data-act="sheet:history" aria-label="Historial del ejercicio">${icon('history',14)}</button>
  </div>`;
}

function failOverlay(){
  return `<div class="k-failoverlay">
    <div class="t">Marcaste esta serie como fallida</div>
    <div class="d">Quiere decir que tuviste una incomodidad, molestia o deformación de la técnica que te impidió completarla. Ten cuidado y siempre procura usar cargas que puedas manejar.</div>
    <div class="l">Causa o motivo del fallo (opcional):</div>
    <div class="field">Ej. Molestia en hombro, fallo técnico, carga excesiva…</div>
    <div class="chips"><span>Molestia articular</span><span>Fallo técnico</span><span>Carga excesiva</span><span>Pérdida de agarre</span></div>
    <button class="rev" data-act="revert">Revertir</button>
  </div>`;
}

function guided(d, kind){
  if (kind === 'drop') return `<div class="k-guided">
    <div class="eyebrowline">DROP-SET 1/2</div>
    <div class="cp">Sin descanso. Baja el peso y completa ~6 reps.</div>
    <div class="fields"><div class="f">Peso sugerido</div><div class="f">Repeticiones</div></div>
    <div class="sug">Sugerido: 80 kg para ~6 reps</div>
    <div class="skip" data-act="flip-front">Saltar técnica y registrar solo la serie</div>
  </div>`;
  if (kind === 'count') return `<div class="k-guided">
    <div class="eyebrowline">MYO-REPS 1/3</div>
    <div class="cp">Pausa fija de 15 s. Luego harás 4 reps.</div>
    <div class="bigclock">${icon('timer',22)} <b>15 s</b></div>
    <div class="bar"><i></i></div>
    <div class="skip" data-act="flip-front">Saltar técnica y registrar solo la serie</div>
  </div>`;
  return `<div class="k-guided">
    <div class="eyebrowline">REST-PAUSE 2/3</div>
    <div class="cp">Confirma las 4 reps de esta mini-serie.</div>
    <div class="fields"><div class="f">Repeticiones</div></div>
    <div class="skip" data-act="flip-front">Saltar técnica y registrar solo la serie</div>
  </div>`;
}

/* ── caras ── */
function techFace(s){
  const d = s.data || {};
  const segs = ['Normal','Drop-set','Rest-pause','Myo-reps'];
  const segKey = d.segment || 'normal';
  const segMap = {normal:0, drop:1, pause:2, myo:3};
  const seg = segs.map((x,i) => `<button class="${i===segMap[segKey]?'on':''}" data-act="tech:${['normal','drop','pause','myo'][i]}">${x}</button>`).join('');
  const empty = d.empty || segKey === 'normal';
  const chain = (d.chain || []).map((l,i) => `<div class="k-link ${l.on?'on':''}"><span class="t">${esc(l.t)}</span><span class="v">${esc(l.v)}</span><span class="s">${esc(l.s||'')}</span></div>`).join('<span class="arrow">→</span>');
  const head = `<div class="k-facehead"><button class="k-pillbtn" data-act="flip-front">${icon('chevronLeft',16)} Serie 3</button>${d.pr ? '<span class="k-prchip">PR: foto/video</span>' : ''}<span class="eyebrow">AVANZADO</span></div>`;
  let body = '';
  if (empty) {
    body = `<div class="k-chaintitle">CADENA DE LA SERIE</div>
      <div style="font-size:11px;line-height:15px;color:var(--faint);padding:8px 2px">Elige drop-set o rest-pause</div>
      <div style="flex:1"></div>
      <div class="k-note"><div class="lb">NOTA DE LA SERIE</div><div class="k-input">Añadir una nota de esta serie</div></div>
      <div class="k-errorpill">Error de ejecución</div>`;
  } else {
    const open = d.active != null ? d.active : 1;
    const l = (d.chain || [])[open] || {};
    const isPause = segKey === 'pause' || segKey === 'myo';
    body = `<div class="k-chaintitle">CADENA DE LA SERIE</div>
      <div class="k-chain">${chain || '<div class="k-link on"><span class="t">Principal</span><span class="v">100 × 6</span><span class="s">—</span></div>'}<div class="k-addlink">${icon('plus',16)}</div></div>
      <div class="k-stepbox">
        <div class="row"><span class="t">${esc(l.t || (isPause?'Pausa 1':'Drop 1'))}</span><span class="sug">${esc(d.suggested || (isPause?'15 s':'Sugerido 80 kg'))}</span><span class="k-done ${d.doneStep?'on':''}">${icon('check',14)}</span></div>
        <div class="k-stepsrow">
          <div class="k-stepper"><span class="b">−</span><span class="v">${esc(isPause ? (d.reps || '3') : (d.weight || '80'))}<small>${isPause?'reps':'kg'}</small></span><span class="b">+</span></div>
          <div class="k-stepper"><span class="b">−</span><span class="v">${esc(isPause ? (d.secs || '15') : (d.reps || '6'))}<small>${isPause?'s':'reps'}</small></span><span class="b">+</span></div>
        </div>
      </div>
      <div class="k-chaintitle">${segKey==='myo'?'ASÍ SE VEN LOS MYO-REPS':'ASÍ SE VE UN '+(segKey==='pause'?'REST-PAUSE':'DROP-SET')}</div>
      <div class="k-explain">${(d.explanation || ['100 × 6','80 × 6','60 × 8']).map((x,i) => `<span class="blk ${i===(d.explanation||[]).length-1?'last':''}">${esc(x)}</span>`).join('')}</div>
      <div class="k-note"><div class="lb">NOTA DE LA SERIE</div><div class="k-input">Añadir una nota de esta serie</div></div>
      <div class="k-errorpill">Error de ejecución</div>`;
  }
  return `<div class="k-card"><div class="k-face">${head}<div class="k-seg tech">${seg}</div>${body}</div></div>`;
}

function mediaFace(s){
  const d = s.data || {};
  const video = d.mode !== 'photo';
  const analysis = d.analysis || 'idle';
  let foot = `Se guarda en el álbum con la serie 3 · 100 kg · RIR 2.`;
  if (analysis === 'count') foot = `El vídeo cuenta ${d.count||8} · ver análisis`;
  if (analysis === 'unsure') foot = `Vídeo no concluyente · ver por qué`;
  if (analysis === 'analyzing') foot = `Analizando el vídeo de la serie 3… 64 %`;
  const thumbs = (d.thumbs || ['1','2','3']).map((t,i) => `<div class="k-thumb2">${t}${i===0?'<span class="bdg">'+(analysis==='count'?(d.count||8):'?')+'</span>':''}</div>`).join('');
  return `<div class="k-card"><div class="k-face" style="padding:10px">
    <div class="k-facehead" style="height:32px"><button class="k-pillbtn" data-act="flip-front">${icon('chevronLeft',16)} Serie 3</button><button class="k-pillbtn" style="margin-left:auto" data-act="sheet:album">${icon('album',16)} Álbum</button><button class="k-pillbtn icon" aria-label="Expandir">${icon('expand',14)}</button></div>
    <div class="k-mstage">
      <div class="grid"></div>
      <div class="k-mtop"><div class="k-switch"><b class="${!video?'on':''}" data-act="media:photo">Foto</b><b class="${video?'on':''}" data-act="media:video">Vídeo</b></div><span class="k-ficha">${icon('info',14)} Ficha</span></div>
      <div class="k-mbottom">
        <div class="k-gal"><span class="lb">Galería</span><div class="k-thumbs">${thumbs}</div><span style="font-size:10px;color:var(--ink);margin-top:2px">Ver álbum</span></div>
        <div class="k-shutter ${video?'video':''}" data-act="media:capture"><i></i></div>
      </div>
    </div>
    <div class="k-mediafoot"><div class="fit">${video?icon('video',18):icon('camera',18)}</div><div class="ln">${esc(foot)}${analysis==='analyzing'?'<br><b>Cancelar análisis</b>':''}</div></div>
  </div></div>`;
}

/* ── preparación ── */
function prepMob(s){
  const d = s.data || {};
  const view = d.view || 'move';
  const head = `<div class="head"><span class="t">Movilidad</span><span class="k-chip" style="margin-left:auto">${d.view==='global'?esc(d.remaining):'08:12'}</span><span class="k-chip muted" style="margin-left:0">${view==='list'?'Cerrar lista':'Ver lista'}</span></div>`;
  let body = '';
  if (view === 'move') {
    body = `<div class="body">
      <div class="hero"><span class="v">${esc(d.remaining || '0:28')}</span><span class="u">restante</span><span class="side"><b>${esc(d.dose || '3 × 40″')}</b><span>por lado</span></span></div>
      <div class="sub">Sentadilla profunda · Movimiento 2 de 6</div>
      <div class="dosecard"><span class="l">Dosis</span><span class="d">${esc(d.dose || '3 × 40″')}</span><span style="font-size:10.5px;color:var(--muted)">${esc(d.note || 'Talones apoyados, rodillas hacia fuera.')}</span></div>
      <div class="k-minirow"><span class="k-minibtn">${icon('play',12)} Iniciar</span><span class="k-minibtn">+30 s</span><span class="k-minibtn">Reiniciar</span></div>
      <div class="mrows" style="margin-top:2px">
        <div class="k-mrow done"><span class="check">✓</span><span class="nm">Tobillo</span><span class="dose">40″</span></div>
        <div class="k-mrow on"><span class="check"></span><span class="nm">Sentadilla profunda</span><span class="dose">3 × 40″</span></div>
        <div class="k-mrow"><span class="check"></span><span class="nm">Apertura de cadera</span><span class="dose">8 reps</span></div>
        <div class="k-mrow"><span class="check"></span><span class="nm">Isquios</span><span class="dose">30″</span></div>
      </div>
    </div>`;
  } else if (view === 'list') {
    body = `<div class="body"><div class="sub" style="color:var(--ink)">3/6 movimientos hechos</div>
      <div class="mrows">${[['Tobillo','40″','done'],['Sentadilla profunda','3 × 40″','on'],['Apertura de cadera','8 reps',''],['Isquios','30″',''],['Glúteo','40″',''],['Psoas','30″','']].map(r => `<div class="k-mrow ${r[2]}"><span class="check">${r[2]==='done'?'✓':''}</span><span class="nm">${r[0]}</span><span class="dose">${r[1]}${r[2]==='on'?' · en pantalla':''}</span></div>`).join('')}</div></div>`;
  } else if (view === 'global') {
    body = `<div class="body" style="justify-content:center;gap:8px">
      <div class="sub">Objetivo global · 12 min</div>
      <div style="font-family:var(--syne);font-size:48px;line-height:54px;font-weight:700;font-variant-numeric:tabular-nums">${esc(d.remaining || '08:12')}</div>
      <div class="k-prepbtn primary" style="width:100%;flex:none">Pausar</div>
      <div class="k-minirow"><span class="k-minibtn">+30 s</span><span class="k-minibtn">Reiniciar</span></div>
      <div class="k-prepbtn quiet" style="width:100%;flex:none;margin-top:2px">Volver al movimiento</div>
    </div>`;
  } else if (view === 'end') {
    body = `<div class="body" style="justify-content:center;align-items:center;text-align:center;gap:6px">
      <div style="font-family:var(--syne);font-size:22px;line-height:28px;font-weight:700">Movilidad completada</div>
      <div class="sub">Puedes continuar con las aproximaciones.</div>
      <div class="sub" style="color:var(--faint)">6 de 6 movimientos hechos.</div>
      <div class="k-prepbtn primary" style="width:100%;flex:none;margin-top:6px">Continuar con aproximación</div>
    </div>`;
  } else {
    body = `<div class="body" style="justify-content:center;gap:8px">
      <div class="sub">Descanso</div>
      <div style="font-family:var(--syne);font-size:44px;line-height:48px;font-weight:700">${esc(d.remaining || '0:40')}</div>
      <div style="height:5px;border-radius:999px;background:var(--inset);overflow:hidden"><i style="display:block;height:100%;width:66%;background:var(--muscle)"></i></div>
      <div class="k-prepbtn primary" style="width:100%;flex:none;margin-top:4px">Saltar</div>
    </div>`;
  }
  const status = (view==='move'||view==='list') ? `<div class="k-appstatus" style="padding:0 2px 4px">Completa cada movimiento para avanzar.</div>` : '';
  const actions = view==='move' ? `<div class="actions"><button class="k-prepbtn quiet" data-act="prep:skip">Saltar fase</button><button class="k-prepbtn primary" data-act="prep:done">Hecho</button></div>` : '';
  return `<div class="k-card"><div class="k-prep">${head}${body}${status}${actions}</div></div>`;
}

function prepWarm(s){
  const d = s.data || {};
  const rows = (d.rows || []).map((r,i) => `<div class="k-warmrow ${i===d.active?'on':''}"><span class="p">${r[0]}</span><span class="r">${r[1]}</span><span class="kg">${r[2]}</span><span class="x">${r[3]}</span><span class="rest">${r[4]}</span></div>`).join('');
  const skipped = !!d.skipped;
  return `<div class="k-card"><div class="k-prep">
    <div class="head"><span class="t">Aproximación</span><span class="k-chip" style="margin-left:auto">${d.rows ? d.rows.length : 0}/4</span><span class="k-chip muted" style="margin-left:0">${icon('plus',12)}</span></div>
    ${skipped ? `<div class="body" style="justify-content:center;align-items:center;text-align:center;gap:6px"><div style="font-family:var(--syne);font-size:22px;line-height:28px;font-weight:700">Fase omitida</div><div class="sub">Las series efectivas siguen pendientes.</div><div class="k-prepbtn primary" style="width:100%;flex:none;margin-top:6px">Continuar con series</div></div>` : `
    <div class="sub" style="color:var(--muted)">Primera serie efectiva · ${esc(d.anchor || 100)} kg</div>
    <div class="body" style="gap:0">${rows}</div>
    ${d.added ? `<div class="k-loadwarn" style="color:var(--energy);font-size:10px;padding:2px 2px">Demasiadas aproximaciones: máximo recomendado 4 para 2 series efectivas.</div>` : ''}
    <div class="k-appstatus" style="padding:0 2px 4px">${d.added ? 'Aproximación añadida.' : 'Marca cada aproximación al terminar.'}</div>
    <div class="actions"><button class="k-prepbtn quiet" data-act="prep:skip">Saltar fase</button><button class="k-prepbtn primary" data-act="prep:next">Continuar con series</button></div>`}
  </div></div>`;
}

function cardio(s){
  const d = s.data || {};
  const p = ['WORK','RECUPERACIÓN','ZONA 2','EXTERIOR'].includes(d.phase) ? d.phase : 'TRABAJO';
  const bars = [18,30,22,42,26,36,20,44,24,38,30,46,22,34,26,40,18,32,24,36].map((h,i) => `<i style="height:${h}px" class="${i%4===0?'w':''}"></i>`).join('');
  return `<div class="k-card"><div class="k-cardio">
    <div class="head"><span class="k-eyebrow">CARDIO</span><span class="nm">Cinta · intervalos</span></div>
    <div class="phase"><b>Ahora · ${esc(p)}</b><span>Siguiente · ${esc(d.next || 'Recuperación 1:00')}</span></div>
    <div class="ringbox"><div class="k-ringbig"><div class="in"><span>Transcurrido</span><b>${esc(d.time || '18:24')}</b></div></div></div>
    <div class="metrics"><div class="p"><b>${esc(d.hr || '142')}</b><span>FC media</span></div><div class="p"><b>${esc(d.kcal || '210')}</b><span>kcal</span></div><div class="p"><b>${esc(d.dist || '2,4')}</b><span>km</span></div></div>
    <div class="k-chart">${bars}</div>
    <div class="splits">Splits ${esc((d.splits||[]).join(' · '))}</div>
    <div class="k-appstatus" style="padding:0">En curso · 4 realizados · 4 restantes</div>
    <div class="k-actions" style="padding:0;border:0;margin-top:2px"><button class="k-action" data-act="prep:skip">Pausar</button><button class="k-action" data-act="sheet:calories">Finalizar y registrar</button></div>
  </div></div>`;
}

/* ── descanso ── */
function clockSVG(shape, progress){
  const p = Math.max(0, Math.min(1, progress));
  if (shape === 'track') {
    return `<svg viewBox="0 0 120 104" aria-hidden="true">
      <rect x="12" y="20" width="96" height="64" rx="32" fill="none" stroke="#242424" stroke-width="2"/>
      <rect x="12" y="20" width="96" height="64" rx="32" fill="none" stroke="#F2EEE6" stroke-width="2" pathLength="100" stroke-dasharray="${p*100} 100" stroke-linecap="round"/>
      <circle cx="${12 + 96*Math.min(1,p*1.0)}" cy="${20 + 32}" r="2.6" fill="#F2EEE6"/>
    </svg>`;
  }
  if (shape === 'spiral') {
    let d = 'M60 52';
    for (let i=0;i<12;i++){
      const a0 = Math.PI * (i/6), a1 = Math.PI * ((i+1)/6);
      const r0 = 8 + i*5, r1 = 8 + (i+1)*5;
      d += ` L${(60 + r0*Math.cos(a0)).toFixed(1)} ${(52 + r0*Math.sin(a0)).toFixed(1)} L${(60 + r1*Math.cos(a1)).toFixed(1)} ${(52 + r1*Math.sin(a1)).toFixed(1)}`;
    }
    return `<svg viewBox="0 0 120 104" aria-hidden="true">
      <path d="${d}" fill="none" stroke="#242424" stroke-width="2"/>
      <path d="${d}" fill="none" stroke="#F2EEE6" stroke-width="2" pathLength="100" stroke-dasharray="${p*100} 100" stroke-linecap="round"/>
    </svg>`;
  }
  const rings = [[60,78,48,14],[60,39,11,6],[35,26,8,5]];
  const pp = p*3;
  return `<svg viewBox="0 0 120 104" aria-hidden="true">
    ${rings.map((r,i) => {
      const fill = Math.max(0, Math.min(1, pp - i));
      const ang = -Math.PI/2 + fill*Math.PI*2;
      const cx = r[0] + r[2]*Math.cos(ang), cy = r[1] + r[3]*Math.sin(ang);
      return `<ellipse cx="${r[0]}" cy="${r[1]}" rx="${r[2]}" ry="${r[3]}" fill="none" stroke="#242424" stroke-width="2"/>
        <ellipse cx="${r[0]}" cy="${r[1]}" rx="${r[2]}" ry="${r[3]}" fill="none" stroke="#F2EEE6" stroke-width="2" pathLength="100" stroke-dasharray="${fill*100} 100" stroke-linecap="round"/>
        ${fill>0&&fill<1?`<circle cx="${cx.toFixed(1)}" cy="${cy.toFixed(1)}" r="2.6" fill="#F2EEE6"/>`:''}`;
    }).join('')}
  </svg>`;
}

function rest(s){
  const d = s.data || {};
  const extra = d.extra || d.mode === 'extra';
  const label = extra ? 'TIEMPO EXTRA' : 'DESCANSO';
  const mode = d.mode === 'manual' ? '· manual' : (d.mode === 'adaptive' ? '· sugerido' : '· sugerido');
  const time = d.remaining || '1:12';
  return `<div class="k-card"><div class="k-rest">
    <div class="head"><span class="lb">${label}</span><span class="mode">${extra ? '+0:34 sobre 2:00' : `${mode} · de ${d.total||'2:00'}`}</span></div>
    <div class="clockwrap"><div class="k-restclock">${clockSVG(d.shape || 'tower', d.progress == null ? .4 : d.progress)}</div></div>
    <div class="k-rest-time"><b>${esc(time)}</b><span>${extra ? 'sobre 2:00' : 'restante'}</span></div>
    ${extra ? `<div class="k-band center"><span>El descanso se pasó · la siguiente serie puede verse afectada</span></div>` : ''}
    <div class="last"><span class="lb">ÚLTIMA SERIE</span><span style="font-size:9px;color:var(--faint)">RIR 2</span><b>100 × 8</b></div>
    <div class="next"><div class="lb">Siguiente</div><div class="v">Serie 4 · 100 kg × 8</div><div class="e">RIR 2 · 2:00 de descanso</div></div>
    ${d.play !== false && !extra ? `<div class="k-playpill"><span class="c">${icon('play',15)}</span><span>Jugar mientras descansas</span></div>` : ''}
    <div class="ctrl">${extra ? `<div class="b skip" style="flex:1.6">Empezar la serie 4</div><div class="b">+30 s</div>` : `<div class="b" data-act="rest:-15">−15 s</div><div class="b skip" data-act="rest:skip">Saltar</div><div class="b" data-act="rest:+15">+15 s</div>`}</div>
    ${extra ? '<div class="hint">Necesito +30 s</div>' : '<div class="hint">Descanso sugerido · última RIR 2</div>'}
  </div></div>`;
}

function game(s){
  const d = s.data || {};
  if (d.game === 'arcade') {
    return `<div class="k-card"><div class="k-game">
      <div style="display:flex;align-items:baseline;padding:0 2px"><span style="font-family:var(--syne);font-size:17px;font-weight:700">Jugar mientras descansas</span><span style="margin-left:auto;font-size:10px;color:var(--faint)">1:12</span></div>
      <div class="k-sheet .tile" style="display:flex;gap:6px">
        <div style="flex:1;min-height:120px;border-radius:18px;background:#111;border:1px solid rgba(255,255,255,.09);padding:12px"><b style="display:block;font-family:var(--syne);font-size:18px">RUNNER</b><span style="font-size:11px;color:var(--muted)">Salta las mancuernas</span></div>
        <div style="flex:1;min-height:120px;border-radius:18px;background:#111;border:1px solid rgba(255,255,255,.09);padding:12px"><b style="display:block;font-family:var(--syne);font-size:18px">FLAPPY</b><span style="font-size:11px;color:var(--muted)">Entre las pesas</span></div>
      </div>
      <div style="flex:1"></div>
      <div style="height:4px;border-radius:2px;background:linear-gradient(90deg,var(--blue),var(--muscle),var(--energy))"></div>
      <div class="ctrl" style="display:flex;gap:6px"><div class="b" style="flex:1;height:44px;border-radius:999px;background:var(--inset);display:grid;place-items:center;font-size:12px;font-weight:600">Volver al descanso</div><div class="b" style="flex:1;height:44px;border-radius:999px;background:var(--inset);display:grid;place-items:center;font-size:12px;font-weight:600">Otra partida</div></div>
    </div></div>`;
  }
  return `<div class="k-card"><div class="k-game">
    <div class="arena k-arena" style="border-radius:22px">
      <div class="hud"><span>Saltos<br><b>${d.score||18}</b></span><span style="text-align:right">récord ${d.best||24} · ♥×${d.lives||3}<br><span style="color:var(--faint)">Toca para saltar</span></span></div>
      <div class="ground"></div>
      <div class="k-runner"></div>
      <div class="k-obs" style="left:220px"></div>
      <div class="k-obs b" style="left:300px"></div>
      <div class="k-obs c" style="left:150px"></div>
      <div style="position:absolute;left:12px;bottom:10px;font-size:9px;color:var(--faint)">Salta potente · Velocidad</div>
    </div>
    <div style="display:flex;gap:6px"><div style="flex:1;height:44px;border-radius:999px;background:var(--inset);display:grid;place-items:center;font-size:12px;font-weight:600">Saltar descanso</div><div style="width:44px;height:44px;border-radius:999px;background:var(--inset);display:grid;place-items:center">${icon('close',16)}</div></div>
  </div></div>`;
}

function feedback(s){
  const d = s.data || {};
  const sel = d.selected || [];
  const chips = ['Rodilla anterior','Lumbar','Hombro anterior','Cadera','Tobillo','Codo'].map(x => `<span class="dchip ${sel.includes(x)?'on':''}"><i class="pt"></i>${x}</span>`).join('');
  const strip = d.compact ? `<div class="k-fbstrip"><div class="ring"></div><b>1:12</b><span>de 2:00</span><span style="margin-left:auto">Expandir</span></div>` : '';
  return `<div class="k-card"><div class="k-fb">
    ${strip}
    <div class="head"><span class="t">¿Cómo te salió?</span><span>${d.compact?'4 series':'Sentadilla'}</span></div>
    <div class="tech"><div class="fig">${icon('body',20)}</div><div><div class="l">CALIDAD TÉCNICA</div><b>${d.score||8} <small>/ 10 · ${esc(d.word||'buena')}</small></b></div><div class="slider"><i></i></div></div>
    <div class="search">${icon('search',14)} Rodilla, lumbar, hombro…</div>
    <div class="chips">${chips}</div>
    ${d.related ? `<div class="rel"><b>Molestias previas relacionadas</b>${esc(d.history||'la reportaste el 3 de septiembre')} · Comparten zona y ejercicio. <span style="color:var(--green);font-weight:600">Sigue</span></div>` : ''}
    ${d.compact ? '<div class="k-appstatus" style="margin-top:2px">El descanso sigue corriendo · 1:12</div>' : '<div class="k-appstatus" style="margin-top:2px">Se guarda con el ejercicio, no con la serie.</div>'}
    <div class="act"><div class="b inset" data-act="feedback:skip">Saltar${d.compact?' descanso':''}</div><div class="b primary" data-act="feedback:save">Guardar</div></div>
  </div></div>`;
}

/* ── mesa ── */
function mesa(s){
  const d = s.data || {};
  const exp = d.expand || 'none';
  const editor = `<div class="k-meditor">
    <div class="r1"><span class="big">100</span><span class="u">kg</span><div class="mini"><span>−2,5</span><span>+2,5</span></div></div>
    <div class="r2"><div class="k-rir"><span>0</span><span>1</span><span class="on">2</span><span>3</span><span>4</span></div><span class="e1">e1RM 112,5</span></div>
    <div class="acts"><span>Técnica</span><span>ROM</span><span>Molestia</span><span>Nota</span><span>Vídeo</span><span>Foto</span><span>Hist.</span></div>
    ${exp==='media'?`<div style="display:flex;gap:6px;margin-top:8px;align-items:center"><div class="k-switch"><b class="on">Foto</b><b>Vídeo</b></div><span style="margin-left:auto;font-size:9px;color:var(--muted)">Ficha</span><span style="font-size:9px;color:var(--muted)">Álbum</span></div><div class="k-thumbs" style="margin-top:5px"><div class="k-thumb2">3</div><div class="k-thumb2">2</div><div class="k-thumb2">1</div></div>`:''}
    ${exp==='tech'?`<div class="k-seg tech" style="margin-top:8px"><button class="on">Drop</button><button>Pausa</button><button>Myo</button></div><div class="k-chain" style="margin-top:6px"><div class="k-link on"><span class="t">Principal</span><span class="v">100 × 8</span></div><span class="arrow">→</span><div class="k-link"><span class="t">Drop 1</span><span class="v">80 × 6</span></div></div>`:''}
    ${exp==='injury'?`<div class="k-band" style="margin-top:8px"><span>Molestia: rodilla anterior · hoy</span></div>`:''}
  </div>`;
  const row = (idx, ex, ld, ef, st, cls='') => `<div class="k-tr ${cls}"><span class="idx">${idx}</span><span class="ex">${ex}</span><span class="ld">${ld}</span><span class="ef">${ef}</span><span class="st">${st}</span></div>`;
  const groupA = `<div class="k-mgroup"><span class="nm">Sentadilla trasera</span><span class="tag">Barra libre</span><span class="meta">100 kg · 4 series · ⋯</span></div>
    ${row('A1','20 %','20 × 10','','✓','done')}${row('A2','40 %','60 × 5','','✓','done')}
    ${row('1','Efectiva','100 × 8','RIR 3','✓','done')}${row('2','Efectiva','100 × 8','RIR 2','✓','done')}
    ${row('3','Efectiva','100 × 8','RIR 2','●','active')}${editor}${row('4','Efectiva','100 × 8','—','·')}`;
  const groupB = d.superset
    ? `<div class="k-mgroup"><span class="nm">Superserie · Prensa + Curl</span><span class="tag">Ronda 1 / 3</span><span class="meta">2 ejercicios · ⋯</span></div>
       ${row('1','Prensa de piernas','180 × 10','RIR 2','●','active')}${row('2','Curl femoral','60 × 12','RIR 1','·')}${row('3','Prensa de piernas','180 × 10','—','·')}`
    : `<div class="k-mgroup"><span class="nm">Peso muerto rumano</span><span class="tag">Multipower</span><span class="meta">140 kg · 3 series · ⋯</span></div>
       ${row('A1','60 %','80 × 5','','✓','done')}${row('1','Efectiva','140 × 5','RIR 2','·')}${row('2','Efectiva','140 × 5','—','·')}`;
  return `<div class="k-mesa">
    <div class="mtop"><b>Pierna · Semana 1</b><span>9 ejercicios · 34 series</span></div>
    <div class="scroll">${groupA}${groupB}
      <div class="k-mgroup"><span class="nm">Prensa de piernas</span><span class="tag">Máquina</span><span class="meta">180 kg · 4 series · ⋯</span></div>
      ${row('1','Efectiva','180 × 10','RIR 2','·')}${row('2','Efectiva','180 × 10','—','·')}
    </div>
  </div>`;
}

/* ── hojas ── */
function sheet(id){
  const s = SHEETS[id];
  if (!s) return '';
  const tall = id==='route'||id==='reorder'?'tall':'';
  const full = id==='album'||id==='finish'?'full':'';
  let h = `<div class="k-scrim" data-act="close-sheet"></div><div class="k-sheet ${tall} ${full}">`;
  h += `<div class="k-handle"></div><div class="k-sheethead"><div class="st">${esc(s.title)}</div><button class="k-sheetx" data-act="close-sheet" aria-label="Cerrar ${esc(s.title)}">Cerrar</button></div>`;
  if (s.desc) h += `<div class="sd">${esc(s.desc)}</div>`;
  if (s.big) h += `<div style="font-family:var(--syne);font-size:34px;line-height:38px;font-weight:800;margin:2px 0 8px">${esc(s.big)}</div>`;
  if (s.metrics) h += `<div class="metric">${s.metrics.map(m => `<div><b>${esc(m[0])}</b><span>${esc(m[1])}</span></div>`).join('')}</div>`;
  if (s.simple && s.rows) h += s.rows.map(r => `<div class="k-srow"><span class="val" style="font-size:12.5px;font-weight:600">${esc(r[1])}</span><span class="x">${icon('chevronRight',14)}</span></div>`).join('');
  else if (s.rows && s.rows.length) h += s.rows.map(r => `<div class="k-srow"><span class="key">${esc(r[0])}</span><span class="val">${esc(r[1])}</span>${r[2]?`<span class="x ${r[2]==='Hecha'||r[2]==='PR'?'on':''}">${esc(r[2])}</span>`:''}</div>`).join('');
  if (s.toggle) h += `<div class="k-srow"><span class="key">↗</span><span class="val">${esc(s.toggle)}</span><span class="sw on"></span></div>`;
  if (s.sect) h += `<div class="sect">${esc(s.sect)}</div>`;
  if (s.tiles) h += `<div class="tile">${s.tiles.map((t,i) => `<div class="${i===1?'on':''}"><b>${esc(t[0])}</b>${esc(t[1])}</div>`).join('')}</div>`;
  if (s.subs) h += `<div class="sect">Subetiquetas</div>` + s.subs.map(x => `<div class="k-srow"><span class="key">×</span><span class="val">${esc(x)}</span><span class="x">Quitar</span></div>`).join('');
  if (s.fields) h += `<div class="metric" style="margin-top:8px">${s.fields.map(f => `<div><span>${esc(f)}</span><b style="font-size:14px;color:var(--faint)">—</b></div>`).join('')}</div>`;
  if (s.input) h += `<div class="inp">${esc(s.input)}</div>`;
  if (s.hint) h += `<div style="font-size:10px;line-height:14px;color:var(--faint);margin-top:8px">${esc(s.hint)}</div>`;
  if (s.actions && s.actions.length) h += `<div class="rowbtns">${s.actions.map((a,i) => `<div class="b ${i===s.actions.length-1?'primary':''}" data-act="close-sheet">${esc(a)}</div>`).join('')}</div>`;
  h += `</div>`;
  return h;
}

function dialog(kind, s){
  if (kind === 'update') return `<div class="k-scrim"></div><div class="k-dialog"><div class="box">
    <div class="t">Actualizar serie</div><div class="d">Esta serie ya estaba registrada. ¿Quieres actualizarla?</div>
    <div class="rowbtns" style="display:flex;gap:6px;margin-top:10px"><div class="b" style="flex:1;height:40px;border-radius:999px;background:var(--inset);display:grid;place-items:center;font-size:11px;font-weight:700" data-act="close-dialog">Cancelar</div><div class="b" style="flex:1;height:40px;border-radius:999px;background:var(--ink);color:#0B0B0A;display:grid;place-items:center;font-size:11px;font-weight:700" data-act="close-dialog">Actualizar</div></div>
  </div></div>`;
  if (kind === 'blocked') return `<div class="k-scrim"></div><div class="k-dialog"><div class="box">
    <div class="t">¿Qué deseas hacer?</div><div class="d">Puedes pausar y continuar después. Lo registrado se conserva.</div>
    <div class="r">Continuar entrenando</div><div class="r">Terminar hasta acá</div><div class="r">Pausar y salir</div><div class="r" style="color:var(--danger)">Abandonar sin guardar</div>
    <div class="b" data-act="close-dialog">Continuar entrenando</div>
  </div></div>`;
  return '';
}

/* ── fundamentos ── */
function foundations(kind){
  if (kind === 'type') return `<div class="k-found">
    <h2>Tipografía</h2><div class="lead">Syne lleva las cifras y los títulos. Inter, todo lo que se lee. Cifras tabulares siempre.</div>
    <div class="grp"><h3>Roles en vivo</h3><div class="type">
      <div><b>102,5</b><span>Syne Bold · 34/44 · héroe</span></div>
      <div><b class="sm">Sentadilla trasera</b><span>Syne Bold · 20/24 · título</span></div>
      <div><span class="in" style="letter-spacing:1.2px;text-transform:uppercase;font-weight:600">ESFUERZO</span><span>Inter · 10/14 · eyebrow</span></div>
      <div><span class="in">Elige una carga para 8 reps dejando 2 en reserva.</span><span>Inter · 11/16 · caption</span></div>
      <div><span class="in" style="font-weight:600">Registrar serie</span><span>Inter · 13/16 · chip</span></div>
    </div></div>
    <div class="grp"><h3>Reglas</h3><div class="rules"><div>Una cifra y su unidad comparten línea base.</div><div>Nada de unidades en superíndice.</div><div>Al 130 % una fila puede pasar a dos líneas; nada se corta.</div><div>El mínimo de lectura es 11 sp; 7–10 sp solo para estado del carril.</div></div></div>
  </div>`;
  if (kind === 'motion') return `<div class="k-found">
    <h2>Movimiento</h2><div class="lead">El movimiento explica un cambio. No decora. Todo tiene versión reducida.</div>
    <div class="grp"><h3>Tiempos</h3><div class="measure">
      <span>Giro de tarjeta</span><b>420 ms</b><span>Undo</span><b>200 + 260/200</b>
      <span>Reloj Torre/Pista</span><b>4,5 s</b><span>Espiral</span><b>9 s</b>
      <span>Tiempo extra</span><b>1,8 s</b><span>Marquesina</span><b>2500 + viaje</b>
      <span>Pulsación</span><b>0,97</b><span>God mode</span><b>260 / 220</b>
    </div></div>
    <div class="grp"><h3>Reglas</h3><div class="rules"><div>Nada se mueve sin motivo.</div><div>La tarjeta nunca cambia de tamaño al animar.</div><div>Con movimiento reducido: solo fundidos, sin bucles.</div></div></div>
  </div>`;
  return `<div class="k-found">
    <h2>Superficies</h2><div class="lead">Negro puro, módulo #111111 y filete blanco al 7 %. Sin sombras decorativas.</div>
    <div class="grp"><h3>Tokens</h3><div class="swatches">
      <div class="sw" style="background:#000"><span>#000</span></div>
      <div class="sw" style="background:#111"><span>#111</span></div>
      <div class="sw" style="background:#1A1A1A"><span>#1A1A1A</span></div>
      <div class="sw" style="background:#1B1B1B"><span>#1B1B1B</span></div>
      <div class="sw" style="background:#F49A6E"><span>#F49A6E</span></div>
      <div class="sw" style="background:#F7CF73"><span>#F7CF73</span></div>
      <div class="sw" style="background:#43D18C"><span>#43D18C</span></div>
      <div class="sw" style="background:#8FB2FF"><span>#8FB2FF</span></div>
    </div></div>
    <div class="grp"><h3>Geometría</h3><div class="measure">
      <span>Lienzo</span><b>393 × 852</b><span>Cabecera</span><b>119 dp</b>
      <span>Tarjeta</span><b>345 × 470</b><span>Raíl</span><b>112 dp</b>
      <span>Radio tarjeta</span><b>28 dp</b><span>Filete</span><b>1 dp · 7 %</b>
    </div></div>
    <div class="grp"><h3>Reglas</h3><div class="rules"><div>Sin sombras en módulos.</div><div>Sin ripple de Material.</div><div>Dentro de un módulo no hay otra caja.</div></div></div>
  </div>`;
}

/* ── escena completa ── */
function renderPhone(s){
  const g = sceneSize(s);
  const c = chromeOf(s);
  const d = s.data || {};
  const kind = s.kind === 'front' && state.face ? state.face : s.kind;
  let inner = '';

  if (kind === 'found') {
    inner = foundations(s.found);
    return `<div class="k-phone ${g.compact?'compact':''}" style="--pw:${g.w}px;--ph:${g.h}px">${statusbar()}${inner}</div>`;
  }

  let content = '';
  if (kind === 'front') content = frontCard(s);
  else if (kind === 'tech') content = techFace(s);
  else if (kind === 'media') content = mediaFace(s);
  else if (kind === 'prep-mob') content = prepMob(s);
  else if (kind === 'prep-warm') content = prepWarm(s);
  else if (kind === 'cardio') content = cardio(s);
  else if (kind === 'rest') content = rest(s);
  else if (kind === 'game') content = game(s);
  else if (kind === 'feedback') content = feedback(s);
  else if (kind === 'mesa') content = mesa(s);
  else if (kind === 'sheet' || kind === 'dialog' || kind === 'gap') content = frontCard(Object.assign({}, s, {kind:'front', data: Object.assign({mode:'REPS',load:100,reps:8,rir:2,rom:100}, d)}));
  else content = frontCard(s);

  const overlaySheet = state.sheet || s.sheetId;
  const sheetHtml = overlaySheet ? sheet(overlaySheet) : '';
  const dialogHtml = kind === 'dialog' ? dialog(s.dialog, s) : '';
  const gapHtml = kind === 'gap' ? gapDialog(s) : '';
  const floating = `${d.mini ? `<div class="k-minichip"><div class="ring"></div><div class="txt"><span>Descanso</span><b>1:12</b></div><div class="plus">+15 s</div></div>` : ''}${d.undoFull ? `<div class="k-undo">${icon('undo',14)} ${esc(d.undo||'Deshacer')}</div>` : ''}`;
  const showRelator = !['mesa','sheet','dialog','gap','found'].includes(kind);
  let relator = '';
  if (showRelator) {
    const headerH = g.compact ? 95 : 119;
    const railTop = g.h - (g.compact ? 26 : 32) - 112;
    const top = headerH + g.ch + 8;
    const height = Math.max(0, railTop - top - 8);
    let line = 'Serie 3 de 4 · 100 kg × 8 <b>RIR 2</b>';
    if (kind === 'prep-mob') line = 'Movilidad · 3 de 6 movimientos';
    else if (kind === 'prep-warm') line = 'Aproximación · A2 de 4';
    else if (kind === 'cardio') line = 'Cinta · 18:24 · zona 2';
    else if (kind === 'rest') line = 'Descanso · 1:12 restantes';
    else if (kind === 'game') line = 'Descanso · jugando';
    else if (kind === 'feedback') line = 'Feedback del ejercicio · 4 series';
    relator = `<div class="k-relator" style="top:${top}px;height:${height}px"><div class="rlane"><i></i><i></i><i></i></div><div class="rline">${line}</div></div>`;
  }

  inner = `${header(c)}<div class="k-stage">${content}</div>${relator}${rail(c, d, s)}${floating}${sheetHtml}${dialogHtml}${gapHtml}<div class="k-navbar"></div>`;
  return `<div class="k-phone ${g.compact?'compact':''}" style="--pw:${g.w}px;--ph:${g.h}px">${statusbar()}${inner}</div>`;
}

function gapDialog(s){
  const g = s.gap || {};
  return `<div class="k-scrim"></div><div class="k-dialog"><div class="box" style="border-color:rgba(255,155,146,.25)">
    <div style="display:flex;align-items:center;gap:6px;margin-bottom:4px"><span class="badge gap"><i></i>Brecha</span></div>
    <div class="t">${esc(g.title||'No implementado')}</div>
    <div class="d">${esc(g.body||'')}</div>
    <div class="sect" style="margin-top:8px">Sustituto real</div>
    ${(g.replace||[]).map(x => `<div class="r">${esc(x)}</div>`).join('')}
    <div class="b" data-act="close-dialog">Entendido</div>
  </div></div>`;
}

function frontCard(s){
  const d = s.data || {};
  const mode = d.mode || 'REPS';
  const cls = ['k-card'];
  if (d.failed) cls.push('failed');
  let h = `<div class="${cls.join(' ')}"><div class="k-card-inner">`;
  if (d.cluster) h += `<div style="font-size:12px;font-weight:700;color:rgba(255,255,255,.7);padding:8px 16px 0">Cluster set: próximamente</div>`;
  if (mode === 'UNI' || d.side) h += `<div style="padding:6px 0 0"><div class="k-seg"><button class="${(!d.side||d.side==='left')?'on':''}" data-act="side:left">${icon('chevronLeft',16)} Izquierda (L)</button><button class="${d.side==='right'?'on':''}" data-act="side:right">Derecha (R) ${icon('chevronRight',16)}</button></div></div>`;
  if (mode === 'AMRAP' || mode === 'CALIB') h += `<div class="k-amrapbadge"><b>${mode==='CALIB'?'Sin mínimo':('Mínimo '+(d.amrapMin||8))} · ${mode==='CALIB'?'Calibrador':'AMRAP'}</b></div><div style="font-size:11px;line-height:15px;color:rgba(255,255,255,.65);padding:0 16px 3px">${mode==='CALIB'?'Cuenta todas las repeticiones que completes.':'Haz al menos '+(d.amrapMin||8)+'. Cuenta todas las que completes.'}</div>`;
  if (d.replaced) h += `<div class="k-band"><span>${esc(d.replaced)}</span></div>`;
  else if (d.techPause) h += `<div class="k-band center"><span>Pausa técnica · ${esc(d.techPause)}s</span></div>`;
  if (d.loadRequired) h += `<div class="k-loadwarn">Elige una carga para ${esc(d.reps||8)} reps${d.rir!=null?` dejando ${d.rir} en reserva`:''} y registra la serie.</div>`;
  if (mode === 'RM') h += rmHero(d);
  else if (mode === 'TIME') h += timeHero(d);
  else if (mode === 'SOLO' || mode === 'DIST' || mode === 'CUSTOM') h += compactLoad(d);
  else h += hero(d);
  if (mode !== 'RM' && mode !== 'TIME' && mode !== 'SOLO' && mode !== 'DIST' && mode !== 'CUSTOM') h += `<div class="k-divider"></div>`;
  h += `<div class="k-cardbody">`;
  if (mode === 'RM') {
    h += `<div class="k-rephead"><span class="k-eyebrow">REPS EST.</span><span class="k-plus">${icon('plus',12)}</span><span class="k-videochip">PR: 105 kg × 3</span></div>`;
  } else if (mode === 'TIME') {
    h += `<div class="k-rephead"><span class="k-eyebrow">TIEMPO</span><span style="font-size:11px;color:var(--faint)"> · Restan 00:45</span></div>`;
  } else if (mode === 'DIST' || mode === 'CUSTOM') {
    h += `<div class="k-rephead"><span class="k-eyebrow">${mode==='DIST'?'METROS RECORRIDOS':'CANTIDAD REALIZADA'}</span><span style="margin-left:auto;font-size:11px;color:var(--faint)">${mode==='DIST'?'Objetivo 2.400 m':'3 rondas'}</span></div>`;
  } else {
    h += `<div class="k-rephead"><span class="k-eyebrow">REPS</span><span class="k-plus">${icon('plus',12)}</span>${d.rom?'<span class="k-videochip">'+icon('video',12)+' Vídeo</span>':''}</div>`;
  }
  if (mode === 'TIME') h += `<div class="k-fieldrow"><div class="k-fieldbig">0:45</div><div class="k-timerbtn">${icon('stop',22)}</div></div>`;
  else if (mode === 'DIST') h += `<div class="k-fieldrow"><div class="k-fieldbig">2.400</div><div class="k-timerbtn">${icon('play',20)}</div></div><div style="height:2px;border-radius:1px;background:var(--inset);margin:4px 0 2px"><i style="display:block;height:100%;width:72%;background:var(--ink)"></i></div>`;
  else if (mode === 'CUSTOM') h += `<div class="k-fieldbig">24</div><div style="font-size:10px;color:var(--faint);text-align:center">pasos · 3 rondas</div>`;
  else if (mode === 'SOLO') { h += effort(d); h += wheel(d); }
  else if (mode !== 'RM') h += wheel(d);
  if (mode === 'RM') h += rmControls(d);
  if (mode !== 'SOLO') h += effort(d);
  if (d.rom && mode !== 'SOLO') h += romBlock(d);
  if (d.result) h += `<div class="k-band center" style="background:rgba(67,209,140,.08);border-radius:8px;padding:3px 8px"><span style="color:var(--green);text-transform:none;letter-spacing:0;font-size:10.5px">${esc(d.result)}</span></div>`;
  h += statusLine(d);
  h += `</div>${actionsFront()}`;
  h += `</div>`;
  if (d.failed) h += failOverlay();
  if (d.overlay === 'guided-drop') h += guided(d,'drop');
  if (d.overlay === 'guided-count') h += guided(d,'count');
  if (d.overlay === 'guided-reps') h += guided(d,'reps');
  if (d.overlay === 'assisted') h += `<div class="k-assisted"><div class="f">Peso corporal (kg)</div><div class="b">Guardar</div></div>`;
  if (d.overlay === 'result') h += `<div class="k-result">
    <div class="eyebrowline">RESULTADO AMRAP</div>
    <div class="big">${esc(d.reps||11)} <small>reps</small></div>
    <div class="subline">e1RM 118,4 · mínimo ${esc(d.amrapMin||8)}</div>
    <div class="badge">Fallo alcanzado</div>
    <div class="row"><span>Guardar resultado</span><span>Editar</span></div>
  </div>`;
  h += `</div>`;
  return h;
}

/* ── índice e inspector ── */
function visibleScenes(){
  return SCENES.filter(s => {
    if (state.filter !== 'all' && (s.fidelity || (s.kind==='mesa'?'derived':'exact')) !== state.filter) return false;
    if (!state.q) return true;
    const hay = [s.name,s.sub,s.group,PHASES.find(p=>p.n===s.phase).name].join(' ').toLowerCase();
    return hay.includes(state.q.toLowerCase());
  });
}

function renderIndex(){
  const list = $('#index-list');
  const vis = visibleScenes();
  let h = '';
  PHASES.forEach(p => {
    const items = vis.filter(s => s.phase === p.n);
    if (!items.length) return;
    h += `<div class="phase-head"><b>F${p.n}</b>${esc(p.name)}<span>${items.length}</span></div>`;
    let lastGroup = '';
    items.forEach(s => {
      if (s.group !== lastGroup) { lastGroup = s.group; }
      const fid = s.fidelity || (s.kind==='mesa'?'derived':'exact');
      h += `<button class="scene ${s.id===state.id?'on':''}" data-id="${s.id}"><span class="fd ${fid}"></span><span><span class="nm">${esc(s.name)}</span><span class="sb">${esc(s.sub)}</span></span></button>`;
    });
  });
  list.innerHTML = h || '<div style="padding:16px;font-size:10.5px;color:var(--faint)">Sin resultados.</div>';
  $('#count').textContent = `${vis.length} estados`;
}

function renderPhasebar(){
  $('#phasebar').innerHTML = PHASES.map(p => `<button data-p="${p.n}" class="${current && current.phase===p.n?'on':''}"><b>F${p.n}</b>${esc(p.name)}</button>`).join('');
}

function renderInspector(s){
  const p = PHASES.find(x => x.n === s.phase);
  const fid = s.fidelity || (s.kind==='mesa'?'derived':'exact');
  const labels = {exact:'Exacto',derived:'Derivado',gap:'Brecha'};
  const spec = s.spec || B.front;
  let h = `<div class="insp-kicker">FASE ${p.n} · ${esc(p.name.toUpperCase())} · ${esc(s.group.toUpperCase())}</div>`;
  h += `<div class="insp-title">${esc(s.name)}</div><div class="insp-sub">${esc(s.sub)}</div>`;
  h += `<div style="margin-top:9px"><span class="badge ${fid}"><i></i>${labels[fid]}</span></div>`;
  const why = spec.why || (s.kind==='sheet' ? 'Hoja real del host de sesión: una a la vez, con estado conservado y cierre explícito.' : '');
  if (why) h += `<div class="insp-note">${esc(why)}</div>`;
  if (spec.measures && spec.measures.length) h += `<div class="sec"><h3>Medidas</h3><div class="measure">${spec.measures.map(m => `<span>${esc(m[0])}</span><b>${esc(m[1])}</b>`).join('')}</div></div>`;
  if (spec.states && spec.states.length) h += `<div class="sec"><h3>Estados</h3><ul>${spec.states.map(x => `<li>${esc(x)}</li>`).join('')}</ul></div>`;
  if (spec.copy && spec.copy.length) h += `<div class="sec"><h3>Copy literal</h3><div style="display:flex;flex-direction:column;gap:4px">${spec.copy.map(x => `<div class="copyline">${esc(x)}</div>`).join('')}</div></div>`;
  if (s.kind === 'sheet' && s.sheetId && SHEETS[s.sheetId]) {
    const sh = SHEETS[s.sheetId];
    h += `<div class="sec"><h3>Contenido de la hoja</h3><ul><li><b>${esc(sh.title)}</b></li>${(sh.rows||[]).map(r => `<li>${esc(r[0])} · ${esc(r[1])}${r[2]?` · ${esc(r[2])}`:''}</li>`).join('')}</ul></div>`;
  }
  const src = (s.source && s.source.length) ? s.source : (s.sheetId ? SHEET_SRC[s.sheetId] : null);
  if (src && src.length) h += `<div class="sec"><h3>Fuente</h3><div class="src">${src.map(x => `<code>${esc(x)}</code>`).join('')}</div></div>`;
  if (spec.accept && spec.accept.length) h += `<div class="sec accept"><h3>Aceptación</h3><ul>${spec.accept.map(x => `<li>${esc(x)}</li>`).join('')}</ul></div>`;
  if (spec.avoid) h += `<div class="avoid"><b>No dibujar</b><span>${esc(spec.avoid)}</span></div>`;
  h += `<div class="sec"><h3>Documento</h3><div class="src"><code>docs/design/live-session/${esc(p.doc)}</code></div></div>`;
  $('#inspector').innerHTML = h;
}

function renderPins(s){
  const pins = $('#pins');
  if (!state.annotations || !s.annotations || !s.annotations.length) { pins.innerHTML = ''; return; }
  pins.innerHTML = s.annotations.map((a,i) => `<div class="pin ${a.x>250?'right':''}" style="left:${a.x}px;top:${a.y}px">${i+1}<span class="tip">${esc(a.t)}<em>${esc(a.r)}</em></span></div>`).join('');
}

function applyMarquee(){
  const t = $('.k-title.marquee');
  if (!t) return;
  const mq = t.querySelector('.mq');
  if (!mq) return;
  const overflow = Math.max(0, mq.scrollWidth - t.clientWidth);
  t.style.setProperty('--shift', `-${Math.min(overflow + 4, 180)}px`);
  const dur = Math.max(9, Math.min(22, (overflow / 28) + 8));
  t.style.setProperty('--dur', `${dur.toFixed(1)}s`);
}

function renderStage(){
  const s = current;
  applyGeometry(s);
  $('#device').innerHTML = renderPhone(s);
  renderPins(s);
  requestAnimationFrame(applyMarquee);
  const p = PHASES.find(x => x.n === s.phase);
  $('#crumbs').innerHTML = `<b>Fase ${p.n}</b> · ${esc(p.name)} / ${esc(s.group)}`;
  const fid = s.fidelity || (s.kind==='mesa'?'derived':'exact');
  const labels = {exact:'Exacto',derived:'Derivado',gap:'Brecha'};
  $('#fidelity').innerHTML = `<span class="badge ${fid}"><i></i>${labels[fid]}</span>`;
  $('#scene-name').textContent = s.name;
  $('#scene-desc').textContent = `${s.sub} · ${s.id}`;
  renderInspector(s);
}

function select(id, face){
  const s = SCENES.find(x => x.id === id) || SCENES[0];
  current = s;
  state.id = s.id;
  state.face = face || null;
  state.sheet = null;
  history.replaceState(null, '', '#' + s.id);
  renderIndex();
  renderPhasebar();
  renderStage();
  const el = $(`.scene[data-id="${s.id}"]`);
  if (el) el.scrollIntoView({block:'nearest'});
}

/* ── interacción ── */
function refresh(){
  if (!current) return;
  renderStage();
}

document.addEventListener('click', (e) => {
  const indexBtn = e.target.closest('.scene');
  if (indexBtn) { select(indexBtn.dataset.id); return; }
  const phaseBtn = e.target.closest('[data-p]');
  if (phaseBtn) {
    const p = Number(phaseBtn.dataset.p);
    const first = SCENES.find(s => s.phase === p);
    if (first) select(first.id);
    return;
  }
  const zoomBtn = e.target.closest('[data-z]');
  if (zoomBtn) {
    document.documentElement.style.setProperty('--z', zoomBtn.dataset.z);
    $$('#zoom button').forEach(b => b.classList.toggle('on', b === zoomBtn));
    return;
  }
  const act = e.target.closest('[data-act]');
  if (act) { handleAct(act.dataset.act); return; }
});

function handleAct(a){
  if (!a) return;
  if (a.startsWith('sheet:')) {
    state.sheet = a.slice(6);
    if (!current.sheetId) current.sheetId = state.sheet;
    renderStage();
    return;
  }
  if (a === 'close-sheet') { state.sheet = null; if (current.kind === 'sheet') current.sheetId = null; renderStage(); return; }
  if (a === 'close-dialog') { renderStage(); return; }
  if (a === 'flip-tech') { state.face = 'tech'; refresh(); return; }
  if (a === 'flip-media') { state.face = 'media'; refresh(); return; }
  if (a === 'flip-front') { state.face = null; refresh(); return; }
  if (a.startsWith('tech:')) {
    const seg = a.slice(5);
    current.data = Object.assign({}, current.data, {segment:seg, empty:seg==='normal'});
    state.face = 'tech'; refresh(); return;
  }
  if (a.startsWith('media:')) {
    const m = a.slice(6);
    if (m === 'photo' || m === 'video') current.data = Object.assign({}, current.data, {mode:m});
    refresh(); return;
  }
  if (a.startsWith('rest:')) {
    const k = a.slice(5);
    current.data = Object.assign({}, current.data, {remaining: k==='+15' ? '1:27' : (k==='-15' ? '0:57' : '0:00'), progress: k==='+15' ? .48 : (k==='-15' ? .32 : 1)});
    if (k === 'skip') { current.data = Object.assign({}, current.data, {mode:'extra', extra:true, remaining:'+0:00'}); }
    refresh(); return;
  }
  if (a === 'record') {
    current.data = Object.assign({}, current.data, {done:true, undo:'Deshacer registrar serie'});
    refresh(); return;
  }
  if (a === 'revert') {
    current.data = Object.assign({}, current.data, {failed:false});
    refresh(); return;
  }
  if (a.startsWith('side:')) {
    current.data = Object.assign({}, current.data, {side:a.slice(5)});
    refresh(); return;
  }
  if (a === 'prep:skip') { current.data = Object.assign({}, current.data, {skipped:true, view:'end'}); refresh(); return; }
  if (a === 'prep:done') { current.data = Object.assign({}, current.data, {view:'end', done:6, total:6}); refresh(); return; }
  if (a === 'prep:next') { current.data = Object.assign({}, current.data, {view:'end'}); refresh(); return; }
  if (a === 'feedback:skip' || a === 'feedback:save') { state.face = null; refresh(); return; }
}

/* ── arranque ── */
$('#search').addEventListener('input', (e) => { state.q = e.target.value; renderIndex(); });
$('#filters').addEventListener('click', (e) => {
  const b = e.target.closest('.f'); if (!b) return;
  state.filter = b.dataset.f;
  $$('#filters .f').forEach(x => x.classList.toggle('on', x === b));
  renderIndex();
});
$('#toggle-notes').addEventListener('click', (e) => {
  state.annotations = !state.annotations;
  e.currentTarget.setAttribute('aria-pressed', String(state.annotations));
  renderPins(current);
});
$('#toggle-motion').addEventListener('click', (e) => {
  state.motion = !state.motion;
  e.currentTarget.setAttribute('aria-pressed', String(state.motion));
  document.body.classList.toggle('reduced', state.motion);
});
$('#prev').addEventListener('click', () => step(-1));
$('#next').addEventListener('click', () => step(1));
function step(d){
  const vis = visibleScenes();
  const i = vis.findIndex(x => x.id === state.id);
  const n = (i + d + vis.length) % vis.length;
  select(vis[n].id);
}
document.addEventListener('keydown', (e) => {
  if (e.target.matches('input,textarea')) return;
  if (e.key === 'ArrowRight') { e.preventDefault(); step(1); }
  if (e.key === 'ArrowLeft') { e.preventDefault(); step(-1); }
});

const initial = location.hash.replace('#','');
select(SCENES.some(s => s.id === initial) ? initial : 'f3-reps');
