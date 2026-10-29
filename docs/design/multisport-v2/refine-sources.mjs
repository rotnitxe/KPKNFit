import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=path.dirname(fileURLToPath(import.meta.url));
function removeRange(source,start,end){const a=source.indexOf(start);if(a<0)return source;const b=source.indexOf(end,a);if(b<0)throw new Error('Missing range end '+end);return source.slice(0,a)+source.slice(b);}
let screens=fs.readFileSync(path.join(root,'screens.js'),'utf8');
const names=screens.match(/^const sportNames=.*;$/m)?.[0];
screens=removeRange(screens,'V.home=','V.conflict=');
if(names&&!screens.includes('const sportNames='))screens=screens.replace('V.conflict=',names+'\nV.conflict=');
screens=removeRange(screens,'V.session=','V.transition=');
screens=removeRange(screens,'const cardioBlocks=','V.progress=');
screens=removeRange(screens,'V.training=','V.import=');
fs.writeFileSync(path.join(root,'screens.js'),screens);
let sports=fs.readFileSync(path.join(root,'sports.js'),'utf8');
sports=removeRange(sports,'V.strength=','const complexNames=');
sports=removeRange(sports,'V.cycling=','const trailKm=');
fs.writeFileSync(path.join(root,'sports.js'),sports);
console.log('Removed superseded existing-surface prototypes; retained internal baseline separately.');
