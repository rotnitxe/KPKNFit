'use strict';
/* Infrastructure only. A loaded iframe is not a ready renderer. */
(()=>{
 const grid=document.getElementById('gallery-grid'),tabs=document.getElementById('gallery-tabs');
 if(typeof SCENARIOS==='undefined'||!Array.isArray(SCENARIOS)){
  grid.innerHTML='<section class="gallery-load-error" role="alert"><h2>No se pudo cargar el catálogo</h2><p>Vuelve a intentarlo para abrir las propuestas.</p><button id="gallery-reload" type="button">Reintentar</button></section>';
  document.getElementById('gallery-reload').addEventListener('click',()=>location.reload());return;
 }
 const LIMIT=2,TIMEOUT=30000,MARGIN=160;
 const groups=['Todas',...new Set(SCENARIOS.map(s=>s.group))];
 const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 tabs.innerHTML=groups.map((g,i)=>`<button class="${i===0?'active':''}" data-group="${esc(g)}" aria-pressed="${i===0}">${esc(g)}</button>`).join('');
 grid.innerHTML=SCENARIOS.map((s,i)=>`<article class="gallery-item" data-group="${esc(s.group)}" data-scene="${s.id}"><div class="preview-window" data-state="idle"><div class="preview-host"></div><div class="preview-status" role="status"><strong>Vista en espera</strong><p>Se carga al acercarse a esta propuesta.</p><button type="button" data-preview-retry hidden>Reintentar</button></div></div><div class="gallery-title"><h2>${esc(s.title)}</h2><a href="./index.html#${s.id}" aria-label="Abrir ${esc(s.title)}">Abrir ↗</a></div><div class="eyebrow">${String(i+1).padStart(2,'0')} · Qué se agrega</div><p>${esc(s.add)}</p><p class="control">Control: ${esc(s.control)}</p></article>`).join('');
 const jobs=SCENARIOS.map(scene=>{
  const card=grid.querySelector(`[data-scene="${scene.id}"]`),box=card.querySelector('.preview-window');
  return{scene,card,box,host:box.querySelector('.preview-host'),status:box.querySelector('.preview-status'),state:'idle',near:false,frame:null,token:'',timer:null,attempts:0,detail:''};
 });
 let active=0,maxActiveSeen=0,attempt=0,scheduled=false;
 function publish(){grid.dataset.active=String(active);grid.dataset.maxActiveSeen=String(maxActiveSeen);grid.dataset.limit=String(LIMIT);grid.dataset.frames=String(jobs.filter(job=>job.frame).length);}
 function paint(job,state,detail=''){
  job.state=state;job.detail=detail;job.box.dataset.state=state;
  const headings={idle:'Vista en espera',queued:'Vista en cola',loading:'Cargando vista',ready:'Vista lista',error:'No se pudo iniciar esta vista'};
  job.status.querySelector('strong').textContent=headings[state];
  job.status.querySelector('p').textContent=detail||(state==='queued'?'Espera su turno: máximo dos cargas a la vez.':state==='idle'?'Se cargará al volver a esta propuesta.':'Esperando confirmación del renderer.');
  job.status.querySelector('button').hidden=state!=='error';
  job.status.setAttribute('role',state==='error'?'alert':'status');
  publish();
 }
 function release(job){
  if(job.state==='loading')active--;
  if(job.timer!==null){clearTimeout(job.timer);job.timer=null;}
  job.frame?.remove();job.frame=null;job.token='';
 }
 function stop(job){release(job);paint(job,'idle');}
 function fail(job,detail){release(job);job.box.dataset.errorDetail=detail;paint(job,'error','Esta vista no terminó de abrirse. Reinténtala o usa Abrir para verla completa.');pump();}
 function start(job){
  active++;maxActiveSeen=Math.max(maxActiveSeen,active);job.attempts++;
  job.box.dataset.attempts=String(job.attempts);delete job.box.dataset.errorDetail;
  job.token=`${job.scene.id}-${Date.now()}-${++attempt}`;
  const frame=document.createElement('iframe');job.frame=frame;
  frame.title='Propuesta '+job.scene.title;frame.tabIndex=-1;frame.loading='eager';
  frame.setAttribute('aria-hidden','true');
  paint(job,'loading','Inicializando componentes locales.');
  frame.addEventListener('error',()=>{if(job.state==='loading'&&job.frame===frame)fail(job,'El navegador no pudo abrir el preview. Puedes reintentarlo o abrir la vista completa.');});
  frame.addEventListener('load',()=>{if(job.state==='loading'&&job.frame===frame)paint(job,'loading','Documento abierto; preparando la vista.');});
  job.timer=setTimeout(()=>{if(job.state==='loading'&&job.frame===frame)fail(job,'El renderer no confirmó el inicio en 30 s. Reintenta esta vista.');},TIMEOUT);
  frame.src=`./index.html?preview=1&previewToken=${encodeURIComponent(job.token)}#${job.scene.id}`;
  job.host.append(frame);
 }
 function pump(){
  for(const job of jobs)if(job.near&&!job.card.hidden&&job.state==='idle')paint(job,'queued');
  const pending=jobs.filter(job=>job.state==='queued'&&job.near&&!job.card.hidden).sort((a,b)=>Math.abs(a.box.getBoundingClientRect().top)-Math.abs(b.box.getBoundingClientRect().top));
  while(active<LIMIT&&pending.length)start(pending.shift());
  publish();
 }
 function visibility(job,near){job.near=near&&!job.card.hidden;if(!job.near&&job.state!=='error')stop(job);}
 function refresh(){scheduled=false;for(const job of jobs){const rect=job.box.getBoundingClientRect();visibility(job,!job.card.hidden&&rect.height>0&&rect.bottom>-MARGIN&&rect.top<window.innerHeight+MARGIN);}pump();}
 function schedule(){if(!scheduled){scheduled=true;requestAnimationFrame(refresh);}}
 window.addEventListener('message',event=>{
  const data=event.data;if(!data||data.type!=='kpkn-preview')return;
  if(location.protocol!=='file:'&&event.origin!==location.origin)return;
  const job=jobs.find(item=>['loading','ready'].includes(item.state)&&item.frame?.contentWindow===event.source&&item.token===data.token&&item.scene.id===data.scene);
  if(!job)return;
  if(data.status==='loading'){if(job.state!=='loading')return;job.box.dataset.stage=data.stage||'';paint(job,'loading','Preparando los componentes de la vista');return;}
  if(data.status==='error'){fail(job,data.detail||'El preview informó un error al iniciar.');return;}
  if(data.status!=='ready'||job.state!=='loading')return;
  active--;clearTimeout(job.timer);job.timer=null;paint(job,'ready',data.detail||'Renderer confirmado');pump();
 });
 grid.addEventListener('click',event=>{const button=event.target.closest('[data-preview-retry]');if(!button)return;const card=button.closest('[data-scene]'),job=jobs.find(item=>item.card===card);if(job){release(job);paint(job,'idle');refresh();}});
 tabs.addEventListener('click',event=>{const button=event.target.closest('button');if(!button)return;const group=button.dataset.group;tabs.querySelectorAll('button').forEach(el=>{el.classList.toggle('active',el===button);el.setAttribute('aria-pressed',String(el===button));});for(const job of jobs){job.card.hidden=group!=='Todas'&&job.scene.group!==group;if(job.card.hidden)visibility(job,false);}schedule();});
 if(typeof IntersectionObserver!=='undefined'){
  const observer=new IntersectionObserver(entries=>{for(const entry of entries){const job=jobs.find(item=>item.box===entry.target);if(job)visibility(job,entry.isIntersecting);}pump();},{rootMargin:`${MARGIN}px 0px`});
  jobs.forEach(job=>observer.observe(job.box));
 }else window.addEventListener('scroll',schedule,{passive:true});
 window.addEventListener('resize',schedule,{passive:true});
 publish();
 schedule();
})();
