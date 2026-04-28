const fs = require('fs');
const path = 'C:/Users/valen/Downloads/kpkn-fit-(beta-test)/android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt';
const lines = fs.readFileSync(path, 'utf-8').split('\n');

let depth = 0;
let inString = false;
let inChar = false;
let inLineComment = false;
let inBlockComment = false;

for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  inLineComment = false;
  for (let j = 0; j < line.length; j++) {
    const c = line[j];
    const c2 = j < line.length - 1 ? line[j + 1] : '';

    if (inLineComment) break;
    if (inBlockComment) {
      if (c === '*' && c2 === '/') { inBlockComment = false; j++; continue; }
      continue;
    }
    if (c === '/' && c2 === '*') { inBlockComment = true; j++; continue; }
    if (c === '/' && c2 === '/') { inLineComment = true; continue; }

    if (inChar) {
      if (c === '\\') { j++; continue; }
      if (c === "'") inChar = false;
      continue;
    }
    if (inString) {
      if (c === '\\') { j++; continue; }
      if (c === '"') inString = false;
      continue;
    }

    if (c === '"') { inString = true; continue; }
    if (c === "'") { inChar = true; continue; }

    if (c === '{') depth++;
    if (c === '}') depth--;
  }

  // Log key areas
  if ((i >= 1180 && i <= 1200) || (i >= 1290 && i <= 1296) || (i >= 1340 && i <= 1360) || (i >= 1520 && i <= 1535) || (i >= 1710 && i <= 1730) || (i >= 1870 && i <= 1960)) {
    console.log('L' + (i + 1) + ' d=' + depth + ': ' + line.trim().substring(0, 90));
  }
}
console.log('Final depth at EOF: ' + depth);
