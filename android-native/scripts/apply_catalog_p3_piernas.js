const fs = require('fs');
const {
  loadDb, saveDb, loadAliases, saveAliases,
  setupCanon, makeAspect, makeOption, mergeInto, rebuildCatalog, finalizeAliases,
} = require('./catalog_transform_base');

const db = loadDb();
const aliases = loadAliases();
const byId = new Map(db.map(e => [e.id, e]));
const removeIds = new Set();
const aliasAdds = {};
const applied = [];

const groups = [];
function addG(canon, id, aspects) {
  let g = groups.find(x => x.canon === canon);
  if (!g) { g = { canon, sources: [] }; groups.push(g); }
  g.sources.push({ id, aspects });
}

for (const e of db) {
  let m;
  
  // Sentadilla trasera
  m = e.id.match(/^(quads_sentadilla_trasera)_(barra_alta|barra_baja|barra_ssb|mancuernas|kettlebell|maquina_smith)$/);
  if (m) { addG(m[1], e.id, { bar_position: m[2] }); continue; }
  
  // Sentadilla frontal
  m = e.id.match(/^(quads_sentadilla_frontal)_(barra_recta|barra_ssb|mancuernas|kettlebell|maquina_smith)$/);
  if (m) { addG(m[1], e.id, { equipment: m[2] }); continue; }
  
  // Copa
  m = e.id.match(/^(quads_sentadilla_copa)_(mancuerna|kettlebell|disco)$/);
  if (m) { addG(m[1], e.id, { equipment: m[2] }); continue; }
  
  // Prensa
  m = e.id.match(/^(quads_prensa_piernas)_(45|horizontal|vertical|unilateral)_maquina$/);
  if (m) { addG(m[1], e.id, { press_angle: m[2] }); continue; }
  
  // Extensión cuadriceps
  m = e.id.match(/^(quads_extension_cuadriceps)_(maquina|polea|banda)(_unilateral)?$/);
  if (m) { addG(m[1], e.id, { equipment: m[2], laterality: m[3] ? 'unilateral' : 'bilateral' }); continue; }
  
  // Zancadas
  m = e.id.match(/^(quads_zancada_frontal)_(mancuernas|kettlebell|barra_recta|maquina_smith)$/);
  if (m) { addG(m[1], e.id, { equipment: m[2] }); continue; }
  m = e.id.match(/^(quads_zancada_inversa)_(mancuernas|kettlebell|barra_recta|maquina_smith)$/);
  if (m) { addG(m[1], e.id, { equipment: m[2] }); continue; }
  m = e.id.match(/^(quads_zancada_caminando)_(mancuernas|kettlebell|barra_recta)$/);
  if (m) { addG(m[1], e.id, { equipment: m[2] }); continue; }
  
  // Step up
  m = e.id.match(/^(quads_step_up_cajon)_(mancuernas|kettlebell|barra_recta|peso_corporal)$/);
  if (m) { addG(m[1], e.id, { equipment: m[2] }); continue; }
  
  // Peso muerto convencional
  m = e.id.match(/^(hams_peso_muerto_convencional)_(barra_recta|mancuernas|kettlebell|maquina_smith)$/);
  if (m) { addG(m[1], e.id, { equipment: m[2] }); continue; }
  
  // Peso muerto sumo
  m = e.id.match(/^(hams_peso_muerto_sumo)_(barra_recta|mancuernas|kettlebell|maquina_smith)$/);
  if (m) { addG(m[1], e.id, { equipment: m[2] }); continue; }
  
  // Buenos días
  m = e.id.match(/^(hams_buenos_dias)_(barra_recta|barra_ssb|mancuernas|banda|maquina_smith)$/);
  if (m) { addG(m[1], e.id, { equipment: m[2] }); continue; }
  
  // Curl femoral
  m = e.id.match(/^(hams_curl_femoral)_(tumbado_maquina|sentado_maquina|pie_maquina|tumbado_mancuerna|banda|trx)$/);
  if (m) { addG(m[1], e.id, { equipment: m[2] }); continue; }
  
  // Hiperextension 45 base
  m = e.id.match(/^(glutes_hiperextension_45)_(barra|banda|zercher_barra_recta|maquina_smith)$/);
  if (m) { addG(m[1], e.id, { equipment: m[2] }); continue; }
}

for (const g of groups) {
  const base = byId.get(g.sources[0].id);
  const hasEquip = g.sources.some(s => s.aspects.equipment);
  const hasAngle = g.sources.some(s => s.aspects.press_angle);
  const hasLat = g.sources.some(s => s.aspects.laterality);
  const hasBarPos = g.sources.some(s => s.aspects.bar_position);
  
  const aspects = [];
  if (hasBarPos) {
    const vals = [...new Set(g.sources.map(s => s.aspects.bar_position).filter(Boolean))];
    aspects.push(makeAspect('bar_position', 'Posición de Barra', '', vals[0], vals.map(v => makeOption(base.involvedMuscles, v, v.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()), '', []))));
  }
  if (hasAngle) {
    const vals = [...new Set(g.sources.map(s => s.aspects.press_angle).filter(Boolean))];
    aspects.push(makeAspect('press_angle', 'Ángulo de Prensa', '', vals[0], vals.map(v => makeOption(base.involvedMuscles, v, v.charAt(0).toUpperCase() + v.slice(1), '', []))));
  }
  if (hasEquip) {
    const vals = [...new Set(g.sources.map(s => s.aspects.equipment).filter(Boolean))];
    aspects.push(makeAspect('equipment', 'Equipo', '', vals[0], vals.map(v => makeOption(base.involvedMuscles, v, v.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()), '', []))));
  }
  if (hasLat) {
    aspects.push(makeAspect('laterality', 'Lateralidad', '', 'bilateral', [
      makeOption(base.involvedMuscles, 'bilateral', 'Bilateral', '', []),
      makeOption(base.involvedMuscles, 'unilateral', 'Unilateral', '', []),
    ]));
  }
  
  setupCanon(byId, g.canon, g.sources[0].id, g.canon.replace(/_/g, ' '), g.canon.replace(/_/g, ' ') + ' ajustable en chips.', g.canon.replace(/_/g, ' '), aspects);
  
  for (const s of g.sources) {
    const a = {};
    if (s.aspects.bar_position) a.bar_position = s.aspects.bar_position;
    if (s.aspects.press_angle) a.press_angle = s.aspects.press_angle;
    if (s.aspects.equipment) a.equipment = s.aspects.equipment;
    if (s.aspects.laterality) a.laterality = s.aspects.laterality;
    mergeInto(s.id, g.canon, a, g.canon, { byId, removeIds, aliasAdds, applied });
  }
}

const placements = groups.map(g => [g.canon, g.sources[0].id]);
const out = rebuildCatalog(db, byId, removeIds, placements);
finalizeAliases(aliases, aliasAdds, removeIds, byId);
saveDb(out); saveAliases(aliases);
console.log('Piernas/base:', db.length, '→', out.length, '(-' + removeIds.size + ')');
