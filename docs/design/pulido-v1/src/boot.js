// Arranque del lienzo «Pulido v1»: parámetros de URL para renderizar sin interfaz (herramientas de QA) y tamaño por defecto.
//   ?solo=1&scene=<id>&size=360x880&fs=1.3   → solo el teléfono (para capturas)
//   ?list=1                                   → JSON con todas las escenas
//   ?check=1                                  → JSON con el resultado de ejecutar cada escena (errores, NaN, undefined)
//   ?gallery=<tab>&from=0&count=4             → las escenas de una pestaña, una al lado de otra, sin notas
(function(){
  const q=new URLSearchParams(location.search);
  // Con ?t=<ms>&solo=1 el tiempo del lienzo es virtual: requestAnimationFrame y performance.now() los mueve __plAdvance, para capturar también el movimiento
  // hecho con JavaScript (ondas, anillos, contadores) en un instante exacto. Debe instalarse ANTES de que las escenas arranquen sus bucles.
  if(q.get('t')!==null&&q.get('t')!==''&&q.get('solo')){
    const vt={now:0,queue:[],id:0};
    window.requestAnimationFrame=cb=>{vt.queue.push({id:++vt.id,cb});return vt.id};
    window.cancelAnimationFrame=id=>{vt.queue=vt.queue.filter(x=>x.id!==id)};
    performance.now=()=>vt.now;
    window.__plAdvance=ms=>{const end=vt.now+ms;while(vt.now<end){vt.now=Math.min(end,vt.now+16.667);const list=vt.queue;vt.queue=[];for(const it of list){try{it.cb(vt.now)}catch(e){}}}};
  }
  // Orden de presentación de la pestaña «Roadmap»: primero «Antes / ahora» con las medidas, luego A, B y la «Ruta».
  {const i=SCENES.findIndex(s=>s.id==='lv-road-medidas');if(i>0){const [s]=SCENES.splice(i,1);const j=SCENES.findIndex(x=>x.tab===s.tab);SCENES.splice(j<0?0:j,0,s)}}
  state.size=[360,880];
  const sz=/^(\d+)x(\d+)$/.exec(q.get('size')||'');
  if(sz)state.size=[+sz[1],+sz[2]];
  if(q.get('fs'))state.fs=+q.get('fs')||1;
  if(q.get('solo')||q.get('gallery'))document.documentElement.classList.add('solo');
  const phoneHtml=(sc,i)=>{
    const [w,h]=state.size;let body='';
    try{body=sc.html()}catch(e){body=`<pre style="color:#ff9b92;padding:44px 16px;white-space:pre-wrap">ERROR ${sc.id}: ${e.message}</pre>`}
    return `<figure class="gal-item"><div class="phone-wrap" style="--pw:${w}px;--ph:${h}px;--pwn:${w}"><div class="phone" data-i="${i}" style="--fs:${state.fs}"><div class="statusbar"><span>12:55</span><span>5G<i></i></span></div><div class="veil"></div>${body}<div class="homebar"></div></div></div><figcaption>${sc.id}</figcaption></figure>`;
  };
  window.addEventListener('DOMContentLoaded',()=>{
    const sel=document.querySelector('#size-select');
    if(sel){const v=state.size.join(',');if(![...sel.options].some(o=>o.value===v)){const op=document.createElement('option');op.value=v;op.textContent=state.size.join(' × ');sel.appendChild(op)}sel.value=v}
    const fsel=document.querySelector('#font-select');if(fsel)fsel.value=String(state.fs);
    // ?t=<ms> congela todas las animaciones y transiciones CSS en ese instante (para comparar fotogramas con la app: puerta 3 de PROTOCOLO_1A1.md).
    if(q.get('t')!==null&&q.get('t')!==''&&q.get('solo')){
      const ms=Number(q.get('t'))||0;
      setTimeout(()=>{for(const a of document.getAnimations()){try{a.pause();a.currentTime=ms}catch(e){}}if(window.__plAdvance)window.__plAdvance(ms)},40);
    }
    if(q.get('list')){
      document.body.innerHTML='<pre id="L">'+JSON.stringify(SCENES.map(s=>({id:s.id,tab:s.tab,title:s.title,doc:!!s.doc})))+'</pre>';
    }else if(q.get('check')){
      const out=[],seen=new Set(),tabs=new Set(TABS.map(t=>t.id));
      for(const sc of SCENES){
        const r={id:sc.id,tab:sc.tab,ok:true};
        if(seen.has(sc.id)){r.ok=false;r.err='id repetido'}seen.add(sc.id);
        if(!tabs.has(sc.tab)){r.ok=false;r.err=(r.err?r.err+'; ':'')+'pestaña inexistente: '+sc.tab}
        try{
          const h=sc.doc?sc.doc():sc.html();
          if(typeof h!=='string'){r.ok=false;r.err='no devuelve texto'}
          else if(/NaN|undefined|\[object Object\]/.test(h)){r.ok=false;r.err=(r.err?r.err+'; ':'')+'contiene NaN/undefined/[object Object]'}
          r.len=h.length;
        }catch(e){r.ok=false;r.err=String(e&&e.message||e)}
        out.push(r);
      }
      document.body.innerHTML='<pre id="C">'+JSON.stringify(out)+'</pre>';
    }else if(q.get('inventory')){
      const pre=document.createElement('pre');pre.id='V';pre.textContent=JSON.stringify(pulidoInventory());
      document.body.textContent='';document.body.appendChild(pre);
    }else if(q.get('gallery')){
      const tab=q.get('gallery');const from=+(q.get('from')||0),count=+(q.get('count')||999);
      const list=SCENES.filter(s=>s.tab===tab&&!s.doc).slice(from,from+count);
      // Los estilos y las fuentes viven dentro del <body>: se quita el laboratorio y se añade la rejilla, sin vaciar el cuerpo.
      const lab=document.getElementById('lab');if(lab)lab.remove();
      const grid=document.createElement('div');grid.className='gal-grid';grid.innerHTML=list.map(phoneHtml).join('');
      document.body.appendChild(grid);
      grid.querySelectorAll('.gal-item .phone').forEach((ph,i)=>{const sc=list[i];ph.querySelectorAll('.scroll').forEach(el=>{if(sc.scrollTo)el.scrollTop=sc.scrollTo});if(sc.after){try{sc.after(ph)}catch(e){}}});
    }
  });
  // Inventario de profundidad y movimiento: de qué escena es cada clase y qué regla CSS (de la base o de un paquete) pone sombras, vidrio, degradados,
  // animaciones, transiciones y transformaciones. Lo consume tools/inventory.cjs para generar paridad/<prefijo>.md.
  function pulidoInventory(){
    const DEPTH=/^(box-shadow|text-shadow|filter|backdrop-filter|-webkit-backdrop-filter|mix-blend-mode|mask-image|-webkit-mask-image|mask-size|clip-path)$/;
    const MOTION=/^(animation-[a-z-]+|transition-[a-z-]+|will-change)$/;
    const GRAD=/(linear|radial|conic)-gradient\(/;
    const scenesByPkg={},classesByScene={};
    for(const sc of SCENES){
      let html='';try{html=sc.doc?sc.doc():sc.html()}catch(e){}
      const set=new Set();
      for(const m of html.matchAll(/class="([^"]*)"/g))for(const c of m[1].split(/\s+/))if(c)set.add(c);
      classesByScene[sc.id]=set;
      const pkg=sc.id.split('-')[0];(scenesByPkg[pkg]=scenesByPkg[pkg]||[]).push(sc.id);
    }
    const pkgClasses={};for(const p in scenesByPkg){const s=new Set();for(const id of scenesByPkg[p])classesByScene[id].forEach(c=>s.add(c));pkgClasses[p]=s}
    const toks=sel=>[...sel.matchAll(/\.([A-Za-z_][\w-]*)/g)].map(m=>m[1]);
    const usedBy=(sel,pkg)=>{const t=toks(sel);if(!t.length)return[];return (scenesByPkg[pkg]||[]).filter(id=>t.some(x=>classesByScene[id].has(x)))};
    const rows=[],keyframes={},vars={};
    const visit=(rule,owner)=>{
      if(rule.type===1){
        const props={},st=rule.style;
        for(let i=0;i<st.length;i++){const n=st[i],v=st.getPropertyValue(n);
          if(n.slice(0,2)==='--'){if(!(n in vars))vars[n]=v.trim();continue}
          if(DEPTH.test(n)||MOTION.test(n)||(/^background-image$/.test(n)&&GRAD.test(v))||(n==='transform'&&v!=='none'))props[n]=v}
        // Un atajo (animation, transition, background) con var() deja vacíos sus valores sueltos: se guarda el texto del atajo y tools/inventory.cjs lo resuelve con `vars`.
        for(const sh of ['animation','transition','background']){const raw=st.getPropertyValue(sh);if(raw&&/var\(/.test(raw)&&(sh!=='background'||GRAD.test(raw)))props['raw:'+sh]=raw}
        if(Object.keys(props).length)rows.push({owner,selector:rule.selectorText,props});
      }else if(rule.type===7){keyframes[rule.name]=rule.cssText}
      else if(rule.cssRules)for(const r of rule.cssRules)visit(r,owner);
    };
    for(const sheet of document.styleSheets){
      const owner=(sheet.ownerNode&&sheet.ownerNode.dataset&&sheet.ownerNode.dataset.pkg)||'base';
      try{for(const r of sheet.cssRules)visit(r,owner)}catch(e){}
    }
    const out=[];
    for(const r of rows){
      if(r.owner!=='base'){out.push({pkg:r.owner,base:false,selector:r.selector,props:r.props,scenes:usedBy(r.selector,r.owner).slice(0,6)})}
      else for(const p in scenesByPkg){const sc=usedBy(r.selector,p);if(sc.length)out.push({pkg:p,base:true,selector:r.selector,props:r.props,scenes:sc.slice(0,6)})}
    }
    return {rows:out,keyframes,vars,scenes:Object.fromEntries(Object.entries(scenesByPkg).map(([p,l])=>[p,l.length]))};
  }
})();
