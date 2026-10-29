'use strict';
/* Infrastructure only: ordered dependencies and an actual renderer-ready handshake. */
(async()=>{
 const params=new URLSearchParams(location.search),preview=params.has('preview'),token=params.get('previewToken')||'',requested=decodeURIComponent(location.hash.slice(1))||'wizard-sports';
 const target=document.getElementById('app');
 const ownFiles=new Set(['boot.js','kpkn.js','scenarios.js','screens.js','sports.js','new-core.js','app.js']);
 function ownError(filename,stack=''){
  if(filename){try{const url=new URL(filename,location.href),base=new URL('.',location.href);return url.href.startsWith(base.href)&&ownFiles.has(url.pathname.split('/').at(-1));}catch(_){return false;}}
  return Array.from(ownFiles).some(file=>String(stack).includes(new URL(file,location.href).href));
 }
 let failed=false,stage='Inicio';
 function signal(status,detail=''){
  if(preview&&window.parent!==window)window.parent.postMessage({type:'kpkn-preview',status,token,scene:requested,stage,detail},'*');
 }
 function status(detail,error=false){
  const section=document.createElement('section');section.dataset.bootState=error?'error':'loading';section.setAttribute('role',error?'alert':'status');
  section.style.cssText='min-height:100%;display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;padding:32px;gap:14px;background:#000;color:#f2eee6;font:14px/21px Inter,sans-serif';
  const title=document.createElement('strong');title.textContent=error?'No se pudo abrir esta vista':'Abriendo KPKN';
  const copy=document.createElement('p');copy.textContent=detail;copy.style.cssText='color:#c4bfb6;max-width:300px;margin:0';section.append(title,copy);
  if(error){const retry=document.createElement('button');retry.type='button';retry.textContent='Reintentar';retry.style.cssText='min-height:48px;margin-top:10px;padding:12px 24px;border-radius:24px;border:1px solid #f2eee640;background:#f2eee6;color:#000;font:600 14px Inter,sans-serif';retry.addEventListener('click',()=>location.reload());section.append(retry);}
  target.replaceChildren(section);
 }
 function fail(detail){if(failed)return;failed=true;document.documentElement.dataset.rendererReady='false';document.documentElement.dataset.bootError=detail;status('La vista no pudo terminar de abrirse. Puedes volver a intentarlo.',true);signal('error',detail);}
 window.addEventListener('error',event=>{if(event.error&&ownError(event.filename,event.error.stack))fail(`${stage}: ${event.message||'Error de ejecución'}`);});
 window.addEventListener('unhandledrejection',event=>{if(ownError('',event.reason?.stack))fail(`${stage}: ${String(event.reason?.message||event.reason||'Promesa sin resolver')}`);});
 function deadline(promise,ms,label){return new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(new Error(label)),ms);promise.then(value=>{clearTimeout(timer);resolve(value);},error=>{clearTimeout(timer);reject(error);});});}
 function dependency(file,ready){
  stage=file;status('Preparando los componentes de la vista');signal('loading');
  return deadline(new Promise((resolve,reject)=>{
   const script=document.createElement('script');script.src='./'+file;script.async=false;
   script.onerror=()=>reject(new Error('No se pudo cargar '+file));
   script.onload=()=>{try{if(failed)return reject(new Error('Se detuvo el inicio en '+file));if(!ready())return reject(new Error('No se inicializó '+file));resolve();}catch(error){reject(error);}};
   document.body.append(script);
  }),8000,'Sin respuesta al cargar '+file);
 }
 try{
  status('Preparando componentes locales');signal('loading');
  await dependency('kpkn.js',()=>typeof K!=='undefined');
  await dependency('scenarios.js',()=>typeof SCENARIOS!=='undefined'&&Array.isArray(SCENARIOS)&&SCENARIOS.length>0);
  await dependency('screens.js',()=>typeof V!=='undefined'&&typeof F!=='undefined');
  await dependency('sports.js',()=>typeof V.swimming==='function'&&typeof V.mma==='function');
  await dependency('new-core.js',()=>typeof planForSelection==='function'&&typeof V['wizard-sports']==='function');
  await dependency('app.js',()=>typeof render==='function'&&!!target.querySelector('.app-scroll'));
  stage='Renderer, estilos y fuentes';signal('loading');
  for(const name of ['kpkn.css','app.css']){
   const link=Array.from(document.querySelectorAll('link[rel="stylesheet"]')).find(item=>new URL(item.href,location.href).pathname.endsWith('/'+name));
   if(!link)throw new Error('Falta la hoja de estilo '+name);
   if(!link.sheet)await deadline(new Promise((resolve,reject)=>{link.addEventListener('load',resolve,{once:true});link.addEventListener('error',()=>reject(new Error('No se pudo cargar '+name)),{once:true});}),8000,'Sin respuesta de '+name);
  }
  if(document.fonts?.ready)await deadline(document.fonts.ready,8000,'Las fuentes locales no terminaron de cargar');
  if(document.fonts?.check&&(!document.fonts.check('800 28px Syne')||!document.fonts.check('400 15px Inter')))throw new Error('No se pudieron preparar las fuentes locales');
  await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
  if(failed)return;
  const page=target.querySelector('.app-scroll');
  if(!page||!page.children.length||!page.textContent.trim())throw new Error('El renderer no produjo contenido');
  if(typeof route==='undefined'||(preview&&route!==requested))throw new Error('El renderer abrió una ruta distinta de la solicitada');
  document.documentElement.dataset.rendererReady='true';
  signal('ready','Renderer inicializado con contenido y fuentes resueltas');
 }catch(error){fail(error.message||String(error));}
})();
