// Puerta 1 de PROTOCOLO_1A1.md: ningún paquete se da por terminado mientras su matriz de paridad tenga filas sin cerrar.
//   node tools/paridad-check.cjs <prefijo|--all> [--repo=<raíz del repo>]
// Lee paridad/<prefijo>.md. Cada fila de tabla debe estar en uno de dos estados válidos:
//   [x] con «archivo.kt:línea» en la columna Compose, y ese archivo existe y tiene esa línea (se busca bajo android-native/app/src/main/java/com/example/kpkn/ o como ruta del repo);
//   [~] con «DESV-nn» en la columna Compose, y ese identificador existe en paridad/DESVIACIONES.md (con su captura lado a lado).
// Cualquier otra cosa ([ ], sin evidencia, línea inexistente, desviación sin registrar) falla con código 1 y la lista de filas.
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
const args=process.argv.slice(2);
const repoArg=args.find(a=>a.startsWith('--repo='));
const repo=repoArg?path.resolve(repoArg.slice(7)):path.resolve(root,'../../..');
const javaRoot=path.join(repo,'android-native/app/src/main/java/com/example/kpkn');
const pkgs=args.includes('--all')?fs.readdirSync(path.join(root,'paridad')).filter(f=>/^[a-z]{2}\.md$/.test(f)).map(f=>f.slice(0,-3)):args.filter(a=>!a.startsWith('--'));
if(!pkgs.length){console.error('Uso: node tools/paridad-check.cjs <prefijo|--all>');process.exit(2)}
const desvFile=path.join(root,'paridad','DESVIACIONES.md');
const desv=fs.existsSync(desvFile)?fs.readFileSync(desvFile,'utf8'):'';
const resolveFile=rel=>{
  for(const base of [javaRoot,repo,path.join(repo,'android-native/app/src/main/java'),path.join(repo,'android-native/app/src/test/java/com/example/kpkn')]){
    const p=path.join(base,rel);if(fs.existsSync(p))return p;
    // solo el nombre del archivo: se busca en el árbol
  }
  const name=path.basename(rel);
  const stack=[javaRoot];
  while(stack.length){const d=stack.pop();let ents=[];try{ents=fs.readdirSync(d,{withFileTypes:true})}catch(e){}
    for(const e of ents){const p=path.join(d,e.name);if(e.isDirectory())stack.push(p);else if(e.name===name)return p}}
  return null;
};
let bad=0,total=0;
for(const pkg of pkgs){
  const f=path.join(root,'paridad',pkg+'.md');
  if(!fs.existsSync(f)){console.error('No existe '+f+': ejecuta  node tools/inventory.cjs');bad++;continue}
  let ok=0,dev=0,pend=[];
  for(const ln of fs.readFileSync(f,'utf8').split('\n')){
    if(!ln.startsWith('| '+pkg+'-'))continue;
    const c=ln.split('|').map(s=>s.trim());total++;
    const id=c[1],compose=c[5]||'',estado=c[6]||'';
    if(estado==='[x]'){
      const m=/([\w\/.\-]+\.kt):(\d+)/.exec(compose);
      if(!m){pend.push(id+' · [x] sin archivo:línea');continue}
      const file=resolveFile(m[1]);
      if(!file){pend.push(id+' · no existe '+m[1]);continue}
      const n=fs.readFileSync(file,'utf8').split('\n').length;
      if(Number(m[2])>n||Number(m[2])<1){pend.push(id+' · '+m[1]+' no tiene la línea '+m[2]);continue}
      ok++;
    }else if(estado==='[~]'){
      const m=/DESV-\d+/.exec(compose);
      if(!m||!desv.includes('## '+m[0])){pend.push(id+' · [~] sin desviación registrada en DESVIACIONES.md');continue}
      dev++;
    }else pend.push(id+' · pendiente');
  }
  console.log(`${pkg}: ${ok} hechas · ${dev} desviaciones registradas · ${pend.length} sin cerrar`);
  if(pend.length){bad+=pend.length;for(const p of pend.slice(0,25))console.log('   ✗ '+p);if(pend.length>25)console.log('   … y '+(pend.length-25)+' más')}
}
console.log(bad?`NO SE CIERRA: ${bad} filas sin cerrar de ${total}.`:`Matriz completa: ${total} filas con evidencia.`);
process.exit(bad?1:0);
