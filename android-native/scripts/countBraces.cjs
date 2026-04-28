const fs = require('fs');
const p = 'C:/Users/valen/Downloads/kpkn-fit-(beta-test)/android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt';
const content = fs.readFileSync(p, 'utf-8');
let opens = 0, closes = 0, inStr = false, inChar = false, inLC = false, inBC = false;
const sq = "'";
for (let i = 0; i < content.length; i++) {
  const c = content[i], c2 = content[i + 1] || '';
  if (inLC) { if (c === '\n') inLC = false; continue; }
  if (inBC) { if (c === '*' && c2 === '/') { inBC = false; i++; } continue; }
  if (c === '/' && c2 === '/') { inLC = true; i++; continue; }
  if (c === '/' && c2 === '*') { inBC = true; i++; continue; }
  if (inChar) { if (c === '\\') i++; if (c === sq) inChar = false; continue; }
  if (inStr) { if (c === '\\') i++; if (c === '"') inStr = false; continue; }
  if (c === '"') { inStr = true; continue; }
  if (c === sq) { inChar = true; continue; }
  if (c === '{') opens++;
  if (c === '}') closes++;
}
console.log('Opens: ' + opens + ', Closes: ' + closes + ', Diff: ' + (opens - closes));
