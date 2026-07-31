const fs = require('fs');

// p1_talones
let s = fs.readFileSync('apply_catalog_p1_talones.js', 'utf8');
s = s.replace(
  "setupCanon(canonicalId, canonicalId.replace(/_/g, ' '), setup + ' ajustable en chips.', canonicalId.replace(/_/g, ' '), aspects)",
  "setupCanon(byId, canonicalId, sources[0].id, canonicalId.replace(/_/g, ' '), setup + ' ajustable en chips.', canonicalId.replace(/_/g, ' '), aspects)"
);
fs.writeFileSync('apply_catalog_p1_talones.js', s, 'utf8');
console.log('Fixed p1_talones');

// p2_deltoides
s = fs.readFileSync('apply_catalog_p2_deltoides.js', 'utf8');
s = s.replace(
  "setupCanon(canonId,canonId.replace(/_/g,' '),canonId.replace(/_/g,' ')+' ajustable en chips.',canonId.replace(/_/g,' '),aspects)",
  "setupCanon(byId, canonId, sources[0].id, canonId.replace(/_/g,' '),canonId.replace(/_/g,' ')+' ajustable en chips.',canonId.replace(/_/g,' '),aspects)"
);
fs.writeFileSync('apply_catalog_p2_deltoides.js', s, 'utf8');
console.log('Fixed p2_deltoides');

// p2_triceps
s = fs.readFileSync('apply_catalog_p2_triceps.js', 'utf8');
s = s.replace(
  "setupCanon(canonId,canonId.replace(/_/g,' '),canonId.replace(/_/g,' ')+' ajustable en chips.',canonId.replace(/_/g,' '),aspects)",
  "setupCanon(byId, canonId, sources[0].id, canonId.replace(/_/g,' '),canonId.replace(/_/g,' ')+' ajustable en chips.',canonId.replace(/_/g,' '),aspects)"
);
fs.writeFileSync('apply_catalog_p2_triceps.js', s, 'utf8');
console.log('Fixed p2_triceps');

// p3_piernas
s = fs.readFileSync('apply_catalog_p3_piernas.js', 'utf8');
s = s.replace(
  "setupCanon(g.canon, g.canon.replace(/_/g, ' '), g.canon.replace(/_/g, ' ') + ' ajustable en chips.', g.canon.replace(/_/g, ' '), aspects)",
  "setupCanon(byId, g.canon, g.sources[0].id, g.canon.replace(/_/g, ' '), g.canon.replace(/_/g, ' ') + ' ajustable en chips.', g.canon.replace(/_/g, ' '), aspects)"
);
fs.writeFileSync('apply_catalog_p3_piernas.js', s, 'utf8');
console.log('Fixed p3_piernas');

// catchall
s = fs.readFileSync('apply_catalog_catchall.js', 'utf8');
s = s.replace(
  "setupCanon(canonId, canonId.replace(/_/g,' '), canonId.replace(/_/g,' ')+' ajustable en chips.', canonId.replace(/_/g,' '), aspects)",
  "setupCanon(byId, canonId, list[0].id, canonId.replace(/_/g,' '), canonId.replace(/_/g,' ')+' ajustable en chips.', canonId.replace(/_/g,' '), aspects)"
);
fs.writeFileSync('apply_catalog_catchall.js', s, 'utf8');
console.log('Fixed catchall');
