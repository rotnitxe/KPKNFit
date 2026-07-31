const fs = require('fs');
const files = [
  'apply_catalog_p1_face_encog.js',
  'apply_catalog_p1_hip_thrust.js',
  'apply_catalog_p1_talones.js',
  'apply_catalog_p2_deltoides.js',
  'apply_catalog_p2_triceps.js',
  'apply_catalog_p3_piernas.js',
  'apply_catalog_catchall.js',
];

for (const f of files) {
  const s = fs.readFileSync(f, 'utf8');
  const start = s.indexOf('function setupCanon(');
  if (start === -1) { console.log('Already clean:', f); continue; }
  let braceCount = 0;
  let i = start;
  let foundFirst = false;
  for (; i < s.length; i++) {
    if (s[i] === '{') { braceCount++; foundFirst = true; }
    else if (s[i] === '}') { braceCount--; }
    if (foundFirst && braceCount === 0) { i++; break; }
  }
  // include trailing newline(s)
  while (i < s.length && (s[i] === '\n' || s[i] === '\r')) i++;
  const cleaned = s.slice(0, start) + s.slice(i);
  fs.writeFileSync(f, cleaned, 'utf8');
  console.log('Removed local setupCanon from', f);
}
