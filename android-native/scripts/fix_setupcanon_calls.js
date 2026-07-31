const fs = require('fs');

// p1_face_encog
let s = fs.readFileSync('apply_catalog_p1_face_encog.js', 'utf8');
s = s.replace(/setupCanon\('deltoides_face_pull','Face Pull','Face Pull\. Ajustable en equipo y lateralidad\.','Face Pull',/g,
  "setupCanon(byId, 'deltoides_face_pull', 'deltoides_face_pull_polea', 'Face Pull', 'Face Pull. Ajustable en equipo y lateralidad.', 'Face Pull',");
s = s.replace(/setupCanon\('back_encogimientos','Encogimientos','Encogimientos de trapecio\. Ajustable en equipo y posición de barra\.','Encogimientos',/g,
  "setupCanon(byId, 'back_encogimientos', 'back_encogimientos_barra_recta', 'Encogimientos', 'Encogimientos de trapecio. Ajustable en equipo y posición de barra.', 'Encogimientos',");
s = s.replace(/setupCanon\('back_encogimientos_kelso','Encogimientos Kelso','Kelso shrugs\. Ajustable en ángulo y equipo\.','Encogimientos Kelso',/g,
  "setupCanon(byId, 'back_encogimientos_kelso', 'back_encogimientos_kelso_banco_plano_mancuernas', 'Encogimientos Kelso', 'Kelso shrugs. Ajustable en ángulo y equipo.', 'Encogimientos Kelso',");
fs.writeFileSync('apply_catalog_p1_face_encog.js', s, 'utf8');
console.log('Fixed p1_face_encog');

// p1_hip_thrust
s = fs.readFileSync('apply_catalog_p1_hip_thrust.js', 'utf8');
s = s.replace(/setupCanon\('glutes_hip_thrust', 'Hip Thrust', 'Hip Thrust\. Ajustable en lateralidad y equipo\.','Hip Thrust',/g,
  "setupCanon(byId, 'glutes_hip_thrust', 'glutes_hip_thrust_barra_recta', 'Hip Thrust', 'Hip Thrust. Ajustable en lateralidad y equipo.', 'Hip Thrust',");
s = s.replace(/setupCanon\('glutes_puente_gluteos','Puente de Glúteos','Puente de glúteos\. Ajustable en lateralidad y equipo\.','Puente de Glúteos',/g,
  "setupCanon(byId, 'glutes_puente_gluteos', 'glutes_puente_gluteos_peso_corporal', 'Puente de Glúteos', 'Puente de glúteos. Ajustable en lateralidad y equipo.', 'Puente de Glúteos',");
s = s.replace(/setupCanon\('glutes_frog_pumps','Frog Pumps','Frog Pumps\. Ajustable en carga\.','Frog Pumps',/g,
  "setupCanon(byId, 'glutes_frog_pumps', 'glutes_frog_pumps_peso_corporal', 'Frog Pumps', 'Frog Pumps. Ajustable en carga.', 'Frog Pumps',");
fs.writeFileSync('apply_catalog_p1_hip_thrust.js', s, 'utf8');
console.log('Fixed p1_hip_thrust');

// p1_talones
s = fs.readFileSync('apply_catalog_p1_talones.js', 'utf8');
s = s.replace(/setupCanon\(canonicalId, canonicalId\.replace\(\/_\/_g, ' '\), setup \+ ' ajustable en chips\.', canonicalId\.replace\(\/_\/_g, ' '\), aspects\)/g,
  "setupCanon(byId, canonicalId, sources[0].id, canonicalId.replace(/_/g, ' '), setup + ' ajustable en chips.', canonicalId.replace(/_/g, ' '), aspects)");
fs.writeFileSync('apply_catalog_p1_talones.js', s, 'utf8');
console.log('Fixed p1_talones');

// p2_deltoides
s = fs.readFileSync('apply_catalog_p2_deltoides.js', 'utf8');
s = s.replace(/setupCanon\(canonId,canonId\.replace\(\/_\/_g,' '\),canonId\.replace\(\/_\/_g,' '\)\+' ajustable en chips\.',canonId\.replace\(\/_\/_g,' '\),aspects\)/g,
  "setupCanon(byId, canonId, sources[0].id, canonId.replace(/_/g,' '), canonId.replace(/_/g,' ')+' ajustable en chips.',canonId.replace(/_/g,' '),aspects)");
fs.writeFileSync('apply_catalog_p2_deltoides.js', s, 'utf8');
console.log('Fixed p2_deltoides');

// p2_triceps
s = fs.readFileSync('apply_catalog_p2_triceps.js', 'utf8');
s = s.replace(/setupCanon\(canonId,canonId\.replace\(\/_\/_g,' '\),canonId\.replace\(\/_\/_g,' '\)\+' ajustable en chips\.',canonId\.replace\(\/_\/_g,' '\),aspects\)/g,
  "setupCanon(byId, canonId, sources[0].id, canonId.replace(/_/g,' '), canonId.replace(/_/g,' ')+' ajustable en chips.',canonId.replace(/_/g,' '),aspects)");
fs.writeFileSync('apply_catalog_p2_triceps.js', s, 'utf8');
console.log('Fixed p2_triceps');

// p3_piernas
s = fs.readFileSync('apply_catalog_p3_piernas.js', 'utf8');
s = s.replace(/setupCanon\(g\.canon, g\.canon\.replace\(\/_\/_g, ' '\), g\.canon\.replace\(\/_\/_g, ' '\) \+ ' ajustable en chips\.', g\.canon\.replace\(\/_\/_g, ' '\), aspects\)/g,
  "setupCanon(byId, g.canon, g.sources[0].id, g.canon.replace(/_/g, ' '), g.canon.replace(/_/g, ' ') + ' ajustable en chips.', g.canon.replace(/_/g, ' '), aspects)");
fs.writeFileSync('apply_catalog_p3_piernas.js', s, 'utf8');
console.log('Fixed p3_piernas');

// catchall
s = fs.readFileSync('apply_catalog_catchall.js', 'utf8');
s = s.replace(/setupCanon\(canonId, canonId\.replace\(\/_\/_g,' '\), canonId\.replace\(\/_\/_g,' '\)\+' ajustable en chips\.', canonId\.replace\(\/_\/_g,' '\), aspects\)/g,
  "setupCanon(byId, canonId, list[0].id, canonId.replace(/_/g,' '), canonId.replace(/_/g,' ')+' ajustable en chips.', canonId.replace(/_/g,' '), aspects)");
fs.writeFileSync('apply_catalog_catchall.js', s, 'utf8');
console.log('Fixed catchall');
