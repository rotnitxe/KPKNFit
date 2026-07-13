const fs = require('fs');
const path = require('path');
const content = fs.readFileSync(path.join(__dirname, '..', 'data', 'exerciseDatabaseCentral.ts'), 'utf8');

const PATTERN_NAMES = [
  'Empuje Horizontal', 'Empuje Vertical', 'Tirón Horizontal', 'Tirón Vertical',
  'Sentadilla', 'Bisagra', 'Empuje Cadera', 'Flexión Codo', 'Extensión Codo',
  'Flexión Rodilla', 'Extensión Tobillo', 'Abducción Cadera', 'Aducción Cadera',
  'Flexión Hombro', 'Elevación Escapular', 'Agarre/Muñeca', 'Core'
];

// Find all mk() call positions and extract IDs
const mkRegex = /mk\(\s*\n?\s*'([^']+)'/g;
const mkCalls = [];
let m;
while ((m = mkRegex.exec(content)) !== null) {
  mkCalls.push({ id: m[1], pos: m.index });
}

console.log('Total mk() calls found:', mkCalls.length);

let tagged = 0;
let untagged = [];

for (const { id, pos } of mkCalls) {
  // Find the opening '(' of mk(
  let openIdx = content.indexOf('(', pos);
  if (openIdx === -1 || openIdx - pos > 20) continue;
  
  // Track paren depth to find closing ')'
  let depth = 1;
  let j = openIdx + 1;
  while (j < content.length && depth > 0) {
    const ch = content[j];
    if (ch === '(') depth++;
    else if (ch === ')') depth--;
    else if (ch === "'" || ch === '"' || ch === '`') {
      const quote = ch;
      j++;
      while (j < content.length && content[j] !== quote) {
        if (content[j] === '\\') j++;
        j++;
      }
    }
    j++;
  }
  
  // j now points past the closing ')'
  const closeIdx = j - 1;
  
  // Get content before the closing ')'
  const beforeClose = content.substring(Math.max(pos, closeIdx - 100), closeIdx);
  
  let found = false;
  for (const p of PATTERN_NAMES) {
    if (beforeClose.includes("'" + p + "'")) {
      found = true;
      break;
    }
  }
  
  if (found) {
    tagged++;
  } else {
    untagged.push(id);
  }
}

console.log('Tagged:', tagged);
console.log('Untagged:', untagged.length);
if (untagged.length > 0) {
  console.log('Untagged exercises:');
  untagged.forEach(id => console.log('  - ' + id));
}
