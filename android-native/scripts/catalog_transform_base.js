// catalog_transform_base.js
// Helpers comunes para scripts de transformación del catálogo de ejercicios.

const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const dbPath = path.join(root, 'app/src/main/assets/exercise_database.json');
const aliasPath = path.join(root, 'app/src/main/assets/exercise_id_aliases.json');

function loadDb() {
  return JSON.parse(fs.readFileSync(dbPath, 'utf8'));
}
function saveDb(db) {
  fs.writeFileSync(dbPath, JSON.stringify(db, null, 2) + '\n', 'utf8');
}
function loadAliases() {
  return JSON.parse(fs.readFileSync(aliasPath, 'utf8'));
}
function saveAliases(aliases) {
  fs.writeFileSync(aliasPath, JSON.stringify(aliases, null, 2) + '\n', 'utf8');
}

function setupCanon(byId, id, baseId, name, description, aliasName, aspects) {
  const base = byId.get(baseId);
  if (!base) { console.warn('setupCanon: base not found', baseId); return null; }
  const c = { ...base, id, name, description, alias: aliasName };
  c.technicalAspects = aspects;
  byId.set(id, c);
  return c;
}

function makeAspect(id, name, description, defaultOptionId, options) {
  return { id, name, description, defaultOptionId, options };
}
function makeOption(baseMuscles, id, name, description, modTuples = []) {
  return { id, name, description, modifiers: makeModifiers(baseMuscles, modTuples) };
}

// Modifier builder that finds the closest muscle name in the base involvedMuscles list
function makeModifiers(baseMuscles, mods) {
  const names = new Set(baseMuscles.map(m => m.muscle.toLowerCase()));
  return mods.map(([muscleQuery, type, value]) => {
    const q = muscleQuery.toLowerCase();
    if (names.has(q)) return { muscle: muscleQuery, type, value };
    // find partial match
    const match = baseMuscles.find(m =>
      m.muscle.toLowerCase().includes(q) || q.includes(m.muscle.toLowerCase())
    );
    return match
      ? { muscle: match.muscle, type, value }
      : { muscle: muscleQuery, type, value };
  });
}

function mergeInto(oldId, canonicalId, defaultAspects, note, { byId, removeIds, aliasAdds, applied }) {
  if (!byId.has(oldId) && oldId !== canonicalId) return;
  if (oldId !== canonicalId) {
    removeIds.add(oldId);
    aliasAdds[oldId] = canonicalId;
    applied.push(`MERGE ${oldId} → ${canonicalId} ${JSON.stringify(defaultAspects)} (${note})`);
  }
}

function rebuildCatalog(db, byId, removeIds, newCanonicalsPlacement) {
  const originalOrder = db.map(e => e.id);
  const out = [];
  const emitted = new Set();

  for (const id of originalOrder) {
    for (const [newId, anchor] of newCanonicalsPlacement) {
      if (anchor === id && !emitted.has(newId) && byId.has(newId)) {
        out.push(byId.get(newId));
        emitted.add(newId);
      }
    }
    if (removeIds.has(id)) continue;
    if (!byId.has(id)) continue;
    if (emitted.has(id)) continue;
    out.push(byId.get(id));
    emitted.add(id);
  }
  for (const id of byId.keys()) {
    if (!emitted.has(id) && !removeIds.has(id)) {
      out.push(byId.get(id));
      emitted.add(id);
    }
  }
  return out;
}

function finalizeAliases(aliases, aliasAdds, removeIds, byId) {
  Object.assign(aliases, aliasAdds);
  for (const [k, v] of Object.entries(aliases)) {
    let cur = v;
    const seen = new Set();
    while (aliasAdds[cur] && !seen.has(cur)) {
      seen.add(cur);
      cur = aliasAdds[cur];
    }
    if (removeIds.has(cur) && aliasAdds[cur]) cur = aliasAdds[cur];
    aliases[k] = cur;
  }
  for (const k of Object.keys(aliases)) {
    if (byId.has(k) && !removeIds.has(k)) delete aliases[k];
  }
  return aliases;
}

module.exports = {
  dbPath, aliasPath, root,
  loadDb, saveDb, loadAliases, saveAliases,
  setupCanon, makeAspect, makeOption, makeModifiers,
  mergeInto, rebuildCatalog, finalizeAliases,
};
