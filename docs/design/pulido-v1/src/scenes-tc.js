/* ===== Pulido v1 · TÉCNICA (tc) =====
   El análisis de técnica por vídeo, rediseñado: los datos viven SOBRE el vídeo, el detalle va en una hoja con alturas (tirador)
   y «Analizando» ocupa la pantalla. Una sola pieza para las dos entradas (Perfil y sesión en vivo).
   El vídeo es sintético (SVG): un peso muerto de lado, con un disco de 45 cm visto de frente. Todo se calcula del mismo modelo
   (barra, pose, repeticiones), así que la trayectoria, el esqueleto y las lecturas siempre cuadran entre sí. */
Object.assign(P,{
  tcPrev:'<path d="M6.5 5.5v13M18 6.2l-9 5.8 9 5.8z"/>',
  tcNext:'<path d="M17.5 5.5v13M6 6.2l9 5.8-9 5.8z"/>',
  tcFb:'<path d="M16 7l-5 5 5 5M7.5 7v10"/>',
  tcFf:'<path d="M8 7l5 5-5 5M16.5 7v10"/>',
  tcData:'<path d="M5 19v-7M12 19V5M19 19v-9"/>',
  tcPath:'<path d="M4.5 18.5c4.5 0 3.5-13 7.5-13s3 13 7.5 13"/>',
  tcClean:'<rect x="4" y="5" width="16" height="14" rx="3.5"/>',
  tcWarn:'<path d="M12 4.2l9 15.3H3z"/><path d="M12 10v4M12 17v.01"/>',
  tcCancel:'<circle cx="12" cy="12" r="8.5"/><path d="M9 9l6 6M15 9l-6 6"/>',
  tcPose:'<circle cx="12" cy="5" r="2"/><path d="M12 8v6M12 14l-3.5 6M12 14l3.5 6M7 11l5-3 5 3"/>',
  tcPlate:'<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="3"/><path d="M12 3.5v2.5M12 18v2.5M3.5 12H6M18 12h2.5"/>',
  tcCount:'<path d="M6 8l3-2v12M14 8h4M14 12h4M14 16h4"/>',
  tcSigma:'<path d="M5 18c3 0 3-12 7-12s4 12 7 12"/><path d="M3 20h18"/>',
});
const TCD=14.0;
const TCR=[
  {s:1.0, L:.85,D:.95,k:'F',v:1.00,rom:112,mps:.52,dx:4},
  {s:2.9, L:.90,D:.90,k:'F',v:.96, rom:110,mps:.50,dx:6},
  {s:4.8, L:.95,D:.90,k:'F',v:.91, rom:109,mps:.47,dx:8},
  {s:6.7, L:1.00,D:.90,k:'F',v:.87,rom:107,mps:.45,dx:10},
  {s:8.6, L:.60,D:.70,k:'P',v:.93, rom:64, mps:.43,dx:5,a:.55},
  {s:10.5,L:1.10,D:.95,k:'F',v:.83,rom:104,mps:.43,dx:12},
];
TCR.forEach(r=>{r.e=r.s+r.L+r.D;r.a=r.a||1});
const tcClamp=(x,a,b)=>Math.max(a,Math.min(b,x));
const tcLerp=(a,b,t)=>a+(b-a)*t;
const tcSm=p=>{p=tcClamp(p,0,1);return p*p*(3-2*p)};
const tcDec=(x,d=1)=>x.toFixed(d).replace('.',',');
const tcClock=s=>{s=Math.max(0,Math.floor(s+1e-6));return Math.floor(s/60)+':'+String(s%60).padStart(2,'0')};
function tcRepAt(t,pad=0){for(let i=0;i<TCR.length;i++)if(t>=TCR[i].s-pad&&t<=TCR[i].e+pad)return i;return -1}
function tcH(t){const i=tcRepAt(t);if(i<0)return 0;const r=TCR[i],u=t-r.s;return u<r.L?r.a*tcSm(u/r.L):r.a*(1-tcSm((u-r.L)/r.D))}
function tcSp(t){return Math.abs(tcH(t+.02)-tcH(t-.02))/.04}
let TCSPMAX=0;for(let t=0;t<TCD;t+=.02)TCSPMAX=Math.max(TCSPMAX,tcSp(t));
const tcRel=t=>tcClamp(tcSp(t)/TCSPMAX,0,1);
function tcBar(t){const h=tcH(t),i=tcRepAt(t);let dx=0;if(i>=0){const r=TCR[i];dx=r.dx*Math.sin(Math.PI*tcClamp((t-r.s)/(r.L+r.D),0,1))}return {x:186+dx,y:428-105*h}}
function tcIk(A,H,l1,l2){const dx=H.x-A.x,dy=H.y-A.y,d=Math.min(Math.hypot(dx,dy),l1+l2-.01),a=(l1*l1-l2*l2+d*d)/(2*d),hh=Math.sqrt(Math.max(0,l1*l1-a*a)),ux=dx/d,uy=dy/d;return {x:A.x+ux*a-uy*hh,y:A.y+uy*a+ux*hh}}
function tcPose(t){const e=tcH(t),b=tcBar(t),A={x:168,y:466},H={x:tcLerp(120,172,e),y:tcLerp(388,318,e)},th=(40+48*e)*Math.PI/180;
  const S={x:H.x+85*Math.cos(th),y:H.y-85*Math.sin(th)},K=tcIk(A,H,75,77),hd={x:S.x+36*Math.cos(th+.12),y:S.y-36*Math.sin(th+.12)};
  return {A,K,H,S,hd,W:b,b}}
const tcMix=(a,b,k)=>a.map((v,i)=>Math.round(v+(b[i]-v)*k));
function tcCol(rel){const k=tcClamp((rel-.2)/.7,0,1),c=tcMix([143,178,255],[247,207,115],k);return 'rgb('+c.join(',')+')'}
/* ---------- el vídeo sintético ---------- */
function tcBg(w,h,fy){
  let s='<defs><linearGradient id="tcw" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#232832"/><stop offset="1" stop-color="#12141a"/></linearGradient>'+
  '<linearGradient id="tcwin" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5b82ad" stop-opacity=".62"/><stop offset="1" stop-color="#27394d" stop-opacity=".2"/></linearGradient>'+
  '<radialGradient id="tcvg" cx=".5" cy=".5" r=".78"><stop offset=".5" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".72"/></radialGradient>'+
  '<filter id="tcbl" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="2.6"/></filter><filter id="tcgl" x="-40%" y="-400%" width="180%" height="900%"><feGaussianBlur stdDeviation="4"/></filter></defs>';
  s+=`<rect width="${w}" height="${h}" fill="#0a0b0d"/><rect width="${w}" height="${fy}" fill="url(#tcw)"/>`;
  for(let i=0;i<4;i++){const x=w*(.07+i*.235),ww=w*.19;s+=`<rect x="${x.toFixed(1)}" y="${(fy*.08).toFixed(1)}" width="${ww.toFixed(1)}" height="${(fy*.34).toFixed(1)}" fill="url(#tcwin)"/><path d="M${(x+ww/2).toFixed(1)} ${(fy*.08).toFixed(1)}v${(fy*.34).toFixed(1)}M${x.toFixed(1)} ${(fy*.25).toFixed(1)}h${ww.toFixed(1)}" stroke="#0a0b0d" stroke-width="2.5"/>`}
  for(let i=0;i<3;i++)s+=`<rect x="${(w*(.14+i*.31)).toFixed(1)}" y="3" width="${(w*.2).toFixed(1)}" height="5" rx="2.5" fill="#f2eee6" opacity=".5" filter="url(#tcgl)"/>`;
  s+=`<g opacity=".95" filter="url(#tcbl)"><path d="M${w*.04} ${fy*.62}h${w*.22}v${fy*.34}h-${w*.22}z" fill="#14171b" stroke="#22262c" stroke-width="2"/><path d="M${w*.04} ${fy*.72}h${w*.22}M${w*.04} ${fy*.82}h${w*.22}" stroke="#22262c" stroke-width="2"/>`+
    `<ellipse cx="${w*.84}" cy="${fy*.56}" rx="${w*.05}" ry="${fy*.2}" fill="#15181c"/><circle cx="${w*.84}" cy="${fy*.34}" r="${w*.035}" fill="#181b20"/><ellipse cx="${w*.7}" cy="${fy*.62}" rx="${w*.04}" ry="${fy*.16}" fill="#101215"/></g>`;
  s+=`<rect y="${fy}" width="${w}" height="${h-fy}" fill="#141518"/>`;
  s+=`<path d="M${w*.1} ${fy-2}H${w*.9}L${w*.99} ${fy+(h-fy)*.3}H${w*.01}z" fill="#4a4333" opacity=".95"/><path d="M${w*.1} ${fy-2}H${w*.9}" stroke="#4d4636" stroke-width="2"/>`;
  s+=`<path d="M0 ${h*.9}Q${w*.5} ${h*.83} ${w} ${h*.9}" fill="none" stroke="#ff7a2e" stroke-width="3" stroke-linecap="round" opacity=".95"/><path d="M0 ${h*.9}Q${w*.5} ${h*.83} ${w} ${h*.9}" fill="none" stroke="#ff7a2e" stroke-width="9" opacity=".25" filter="url(#tcgl)"/>`;
  return s}
function tcWorld(o){
  let s='';
  // disco (de frente: se ve como círculo) y mancuerna del fondo
  s+='<g class="tc-plate"><circle r="38" fill="#1b1c20" stroke="#5a5e66" stroke-width="2.4"/><circle r="29" fill="none" stroke="#3d4047" stroke-width="2"/><circle r="12" fill="#2a2c32"/><circle r="6.5" fill="#9a9ea6"/><circle r="3" fill="#d6d9de"/><path d="M-30 -18A35 35 0 0 1 -6 -35" fill="none" stroke="#ffffff1c" stroke-width="3" stroke-linecap="round"/></g>';
  // cuerpo
  s+='<g class="tc-body" stroke-linecap="round" stroke-linejoin="round" fill="none">'+
    '<path class="tc-foot" stroke="#d9d4ca" stroke-width="11" d="M156 466H205"/>'+
    '<line class="tc-shin" stroke="#a3836d" stroke-width="13"/><line class="tc-thigh" stroke="#3d434e" stroke-width="22"/>'+
    '<line class="tc-torso" stroke="#5d6674" stroke-width="30"/><line class="tc-arm" stroke="#a3836d" stroke-width="9"/>'+
    '<circle class="tc-head" r="17" fill="#a3836d" stroke="none"/><path class="tc-hair" d="" fill="#1a1512" stroke="none"/></g>';
  // trayectoria (se pinta por velocidad: lenta azul, rápida ámbar)
  s+='<g class="tc-trail" style="filter:drop-shadow(0 0 3px #000c)">';
  const N=34;
  TCR.forEach((r,i)=>{for(let k=0;k<N;k++){const t0=r.s+(r.e-r.s)*k/N,t1=r.s+(r.e-r.s)*(k+1)/N,a=tcBar(t0),b=tcBar(t1),c=tcCol(tcRel((t0+t1)/2));
    s+=`<path class="tc-seg" data-r="${i}" data-t="${t1.toFixed(3)}" d="M${a.x.toFixed(1)} ${a.y.toFixed(1)}L${b.x.toFixed(1)} ${b.y.toFixed(1)}" stroke="${c}"/>`}});
  s+='</g>';
  s+='<g class="tc-skel"><line class="k1"/><line class="k2"/><line class="k3"/><line class="k4"/><line class="k5"/><circle r="3.4" class="j1"/><circle r="3.4" class="j2"/><circle r="3.4" class="j3"/><circle r="3.4" class="j4"/><circle r="3.4" class="j5"/></g>';
  s+='<g class="tc-dotg"><circle class="dh" r="11" fill="#000" opacity=".4"/><circle class="dd" r="5.4" fill="#f2eee6" stroke="#000" stroke-opacity=".5" stroke-width="1.2"/></g>';
  return s}
function tcSvg(land,cls=''){
  const w=land?640:360,h=land?360:640,fy=land?300:466;
  const g=land?'translate(214.6 11) scale(.62)':'';
  return `<svg class="tc-svg ${cls}" viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid slice" aria-hidden="true">${tcBg(w,h,fy)}<g class="tc-world" ${g?`transform="${g}"`:''}>${tcWorld()}</g></svg>`}
function tcRefs(root){const q=s=>root.querySelector(s);return {shin:q('.tc-shin'),thigh:q('.tc-thigh'),torso:q('.tc-torso'),arm:q('.tc-arm'),head:q('.tc-head'),hair:q('.tc-hair'),plate:q('.tc-plate'),
  dg:q('.tc-dotg'),k:[1,2,3,4,5].map(n=>q('.k'+n)),j:[1,2,3,4,5].map(n=>q('.j'+n)),segs:[...root.querySelectorAll('.tc-seg')]}}
function tcPaint(R,t){
  const p=tcPose(t),L=(el,a,b)=>{el.setAttribute('x1',a.x.toFixed(1));el.setAttribute('y1',a.y.toFixed(1));el.setAttribute('x2',b.x.toFixed(1));el.setAttribute('y2',b.y.toFixed(1))};
  L(R.shin,p.A,p.K);L(R.thigh,p.K,p.H);L(R.torso,p.H,p.S);L(R.arm,p.S,p.W);
  R.head.setAttribute('cx',p.hd.x.toFixed(1));R.head.setAttribute('cy',p.hd.y.toFixed(1));
  R.hair.setAttribute('d',`M${(p.hd.x-15).toFixed(1)} ${(p.hd.y-3).toFixed(1)}a16 16 0 0 1 30 -4q-9 -4 -30 4z`);
  R.plate.setAttribute('transform',`translate(${p.b.x.toFixed(1)} ${p.b.y.toFixed(1)})`);
  R.dg.setAttribute('transform',`translate(${p.b.x.toFixed(1)} ${p.b.y.toFixed(1)})`);
  L(R.k[0],p.A,p.K);L(R.k[1],p.K,p.H);L(R.k[2],p.H,p.S);L(R.k[3],p.S,p.hd);L(R.k[4],p.S,p.W);
  [p.A,p.K,p.H,p.S,p.hd].forEach((q,i)=>{R.j[i].setAttribute('cx',q.x.toFixed(1));R.j[i].setAttribute('cy',q.y.toFixed(1))});
  return p}
function tcPaintTrail(R,sel,t){R.segs.forEach(sg=>{const r=+sg.dataset.r;let o=.15;if(r===sel)o=(+sg.dataset.t)<=t?1:.22;sg.style.opacity=o})}

/* ---------- datos de la muestra (un peso muerto convencional, vídeo vertical de 0:14) ---------- */
function tcQuality(kind){
  if(kind==='ideal')return {id:'ideal',conf:'alta',confW:'Confianza alta',dot:'ok',mps:true,obl:false,lost:false,tips:[]};
  return {id:'lost',conf:'media',confW:'Confianza media',dot:'',mps:false,obl:true,lost:true,tips:['Pon el móvil de lado, perpendicular al movimiento']};
}
const TC_OBS=[
  {t:7.0,rep:3,txt:'En la repetición 4 la cadera subió ≈ 1,4 veces más que la barra al empezar a levantar (el tronco se inclinó 12° más).'},
  {t:12.5,rep:5,txt:'En la repetición 6 la barra rebotó en el suelo (menos de 0,1 s de contacto).'},
];
const TC_LOST_TXT='Se perdió el seguimiento continuo del disco. Se han omitido sus medidas; usamos la pose cuando hay señal. Pulsa Cambiar para volver a marcarlo.';
function tcDetail(i,Q){const r=TCR[i],p=[`Rep ${i+1}`];if(r.k==='P'){p.push('parcial acortada');p.push('≈ 55 % del recorrido')}
  p.push('bajada '+tcDec(r.D)+' s');p.push('subida '+tcDec(r.L)+' s');p.push('ROM '+(Q.obl?'≈ ':'')+r.rom+'°');if(Q.mps)p.push('≈ '+tcDec(r.mps,2)+' m/s');return p.join(' · ')}
const tcSetInfo=(entry)=>entry==='vivo'?'Serie 3 · 80 kg × 6 · 13 oct':'80 kg · 13 oct';
const tcName='Peso muerto convencional';

/* ---------- la pantalla de resultado ---------- */
function tcStripHtml(Q){
  const rom=Q.obl?'≈ 108':'108';
  return `<div class="tc-strip">
   <button data-a="det" aria-label="ROM de la cadera"><b>${rom}<small>°</small></b><span>ROM cadera${Q.obl?' · <em>estimado</em>':''}</span></button>
   <button data-a="det" aria-label="Pérdida de velocidad"><b>≈ 17<small>%</small></b><span>Pérdida de velocidad</span></button>
   <button data-a="det" aria-label="Velocidad media">${Q.mps?'<b>≈ 0,48<small>m/s</small></b><span>Velocidad media</span>':'<b style="color:var(--faint)">—</b><span>Velocidad media · <em>sin escala</em></span>'}</button></div>`}
function tcBandsHtml(){
  return TCR.map((r,i)=>`<i class="tc-band ${r.k==='P'?'part':''}" data-rep="${i}" style="left:${(r.s/TCD*100).toFixed(2)}%;width:${((r.e-r.s)/TCD*100).toFixed(2)}%;height:${(10+r.v*16).toFixed(0)}px"></i>`).join('')+
    TC_OBS.map(o=>`<i class="tc-ob" style="left:${(o.t/TCD*100).toFixed(2)}%"></i>`).join('')+'<i class="tc-head"></i>'}
function tcChartHtml(metric,Q,corr,sel){
  const vals=TCR.map((r,i)=>metric==='vel'?r.v:metric==='rom'?r.rom/112:(r.e-r.s)/2.1);
  const labs=TCR.map((r,i)=>metric==='vel'?(Q.mps?'≈ '+tcDec(r.mps,2)+' m/s':(i===0?'La mejor':Math.round(r.v*100)+' % de la mejor')):metric==='rom'?(Q.obl?'≈ ':'')+r.rom+'°':tcDec(r.e-r.s,1)+' s');
  const kinds=TCR.map((r,i)=>corr[i]||r.k);
  return `<div class="tc-chart" role="group" aria-label="Por repetición">${TCR.map((r,i)=>`<button class="tc-bar ${kinds[i]==='P'?'part':''} ${i===sel?'sel':''}" data-a="bar" data-i="${i}" style="--h:${(vals[i]*68).toFixed(0)}px" aria-label="Repetición ${i+1}"><i style="height:${(vals[i]*68).toFixed(0)}px"></i><b>${labs[i]}</b></button>`).join('')}</div>
   <div class="tc-nums">${TCR.map((r,i)=>`<span class="${i===sel?'sel':''}">${i+1}${kinds[i]==='P'?'·p':''}</span>`).join('')}</div>`}
function tcProposal(corr){let c=0,p=0;TCR.forEach((r,i)=>{const k=corr[i]||r.k;if(k==='P')p++;else c++});return {c,p,txt:c+(p>0?' + '+p:'')+' reps'}}
function tcPlateModule(state,Q,dia){
  const dBtn=`<div class="tc-dia">${[450,400,350].map(m=>`<button class="${dia===m?'on':''}" data-a="dia" data-m="${m}">${m/10}</button>`).join('')}<button class="${[450,400,350].includes(dia)?'':'on'}" data-a="dia-other">${[450,400,350].includes(dia)?'Otro':tcDec(dia/10,1)+' cm'}</button></div>`;
  const th=`<div class="th"><svg viewBox="-60 -60 120 120" width="64" height="64"><rect x="-60" y="-60" width="120" height="120" fill="#0b0c0e"/><circle r="38" fill="#17181b" stroke="#383b41" stroke-width="2.4"/><circle r="29" fill="none" stroke="#2b2e33" stroke-width="2"/><circle r="6.5" fill="#9a9ea6"/>${state==='none'?'':'<circle r="42" fill="none" stroke="#f7cf73" stroke-width="3"/>'}</svg></div>`;
  if(state==='suggested')return `<div class="tc-plate">${th}<div class="tx"><strong>¿Es este el disco?</strong><p>Elige su diámetro y confírmalo para medir la velocidad en m/s.</p></div></div>
   <div style="font-size:calc(12px*var(--fs));color:var(--muted);margin-top:12px">Diámetro del disco, en cm</div>${dBtn}
   <div class="tc-btns"><button class="tc-cta" data-a="plate-ok">Confirmar</button><button class="tc-cta gh" data-a="mark">Ajustar</button></div>`;
  if(state==='confirmed')return `<div class="tc-plate">${th}<div class="tx"><strong>Disco de ${tcDec(dia/10,0)} cm</strong><p>${Q.mps?'Velocidad en m/s':'Con este vídeo no se pudo medir la velocidad en m/s.'}</p></div><button class="tc-link" data-a="mark">Cambiar</button></div>${Q.lost?`<div class="tc-warn">${ic('tcWarn',18)}<span>${TC_LOST_TXT.replace(' Pulsa Cambiar para volver a marcarlo.','')} Pulsa Cambiar para volver a marcarlo.</span></div>`:''}`;
  return `<div class="tc-plate">${th}<div class="tx"><strong>No encontramos el disco</strong><p>Márcalo tú para medir la velocidad en m/s.</p></div></div><div class="tc-btns"><button class="tc-cta" data-a="mark">Marcarlo</button></div>`}
function tcTiles(Q){
  const T=(v,u,l,n,na)=>`<div class="tc-tile ${na?'na':''}"><b>${v}${u?`<small>${u}</small>`:''}</b><div class="l">${l}</div><div class="n">${n}</div></div>`;
  return `<div class="tc-tiles">`+
   T('≈ 5','', 'Repeticiones', Q.id==='ideal'?'Confianza alta':'+≈ 1 parcial · Confianza media · Revisa el conteo')+
   T('11','s','Tiempo bajo tensión','en esta serie')+
   T('1-0-1-0','s','Tempo','Bajada, pausa, subida, pausa')+
   T((Q.obl?'≈ ':'')+'108°','','ROM de la cadera',(Q.obl?'estimado · vista oblicua':'estimado · vista lateral')+' · ± 3° entre repeticiones')+
   T('≈ 17','%','Pérdida de velocidad','comparación relativa · Entre la mejor y la última repetición')+
   (Q.mps?T('≈ 0,48','m/s','Velocidad media','Mejor repetición ≈ 0,52 m/s'):T('—','','Velocidad media','Sin escala física validada',true))+
   T('≈ 5','%','Desvío de la barra','Del recorrido. Respecto del medio pie')+`</div>
   <div class="tc-line">≈ 1 parcial acortada (≈ 55 % del recorrido)</div>`}
function tcResultHtml(c){
  const W=state.size[0],H=state.size[1],Q=tcQuality(c.q),land=!!c.land,corr=c.corr||{};
  const det=c.detent||'peek',mode=c.mode||'datos',entry=c.entry||'vivo';
  const prop=tcProposal(corr),noreps=c.noreps;
  const plateState=Q.lost?'confirmed':'confirmed';
  const modeBtn=(m,ico,lab)=>`<button class="${mode===m?'on':''}" data-a="mode" data-m="${m}" aria-label="${lab}">${ic(ico,20)}</button>`;
  const hdr=noreps?`<div class="l"><div class="e">Sin repeticiones</div><div class="n" style="font:700 calc(17px*var(--fs))/1.2 Syne;letter-spacing:-.3px;margin-top:3px">${noreps}</div><div class="s" style="margin-top:6px">Para medir mejor: graba de lado, con el cuerpo entero a la vista y a 2–4 m</div></div><button class="tc-cta gh" data-a="reanalyze">Reanalizar</button>`:
    `<div class="l"><div class="e">${entry==='vivo'?'Conteo propuesto':'Repeticiones'}</div><div class="n tc-tn" data-prop>${entry==='vivo'?prop.txt.replace(' reps','')+'<small>reps</small>':'≈ 5<small>reps</small>'}</div><div class="s" data-propsub>${tcPropSub(Q,corr,entry)}</div></div>${entry==='vivo'?`<button class="tc-cta" data-a="use">Usar en la serie 3</button>`:`<button class="tc-cta gh" data-a="trends">${ic('trendUp',18)}Ver tendencia</button>`}`;
  const qchip=noreps?'':`<button class="tc-chip tc-q tc-glass ${Q.dot}" data-a="q" aria-label="Calidad de la medición"><i class="tc-qd"></i><span>${Q.confW}</span>${Q.lost?ic('down',16):''}</button>`;
  const hud=`<div class="tc-hud">${qchip}
   <div class="tc-modes tc-glass" role="group" aria-label="Capas del vídeo">${modeBtn('datos','tcData','Con datos')}${modeBtn('tray','tcPath','Solo trayectoria')}${modeBtn('limpio','tcClean','Limpio')}</div>
   <div class="tc-modetip tc-glass" data-modetip></div>
   ${noreps?'':`<div class="tc-chip tc-repchip tc-glass"><div><small>Repetición</small><b class="tc-tn" data-repn>1 de 6</b></div><div class="tc-ph" data-ph><i></i><span>En el suelo</span></div></div>
   <div class="tc-stat" data-stat></div><div class="tc-vtag tc-glass" data-vtag><b>0 %</b><small>de la mejor</small></div>`}
   <div class="tc-obs tc-glass" data-obs><small>Observación</small><span></span></div>
   <div class="tc-speed tc-glass" role="group" aria-label="Velocidad de reproducción">${[['0,25×',.25],['0,5×',.5],['1×',1]].map(x=>`<button class="${x[1]===1?'on':''}" data-a="speed" data-v="${x[1]}">${x[0]}</button>`).join('')}</div></div>`;
  const qcard=noreps?'':`<div class="tc-qcard tc-glass ${c.qopen?'show':''}" data-qcard><button class="tc-qx" data-a="q" aria-label="Cerrar">${ic('close',18)}</button>
    <h5><i class="tc-qd ${Q.dot}"></i>${Q.confW}</h5><div class="sub">${Q.lost?'Revisa el conteo. Estas son las razones:':'La medición es fiable en este vídeo.'}</div>
    ${Q.lost?`<div class="tc-qrow">${ic('tcWarn',18)}<div><strong>Se perdió el seguimiento continuo del disco</strong><span>Se han omitido sus medidas; usamos la pose cuando hay señal.</span></div></div>
    <div class="tc-qrow">${ic('tcPose',18)}<div><strong>ROM estimado · vista oblicua</strong><span>Sin la vista de lado, los ángulos son una estimación (≈).</span></div></div>
    <div class="tc-qrow">${ic('tcSigma',18)}<div><strong>Velocidad relativa</strong><span>Sin el disco calibrado no se da en m/s.</span></div></div>
    <div class="tc-btns" style="margin:4px 0 0"><button class="tc-cta" data-a="mark">Cambiar</button><button class="tc-cta gh" data-a="det">Ver detalle</button></div>
    <div class="tc-qsec">Para medir mejor</div>${Q.tips.map(t=>`<div class="tc-qtip">${ic('info',18)}<span>${t}</span></div>`).join('')}`:''}</div>`;
  const obsRows=TC_OBS.map((o,i)=>`<div class="tc-obsrow"><p>${o.txt}</p><button class="tc-link" data-a="obs" data-i="${i}">Ver</button></div>`).join('');
  const det_sections=noreps?`<div class="tc-sec"><h4>Para medir mejor</h4><div class="tc-tip">${ic('info',18)}<span>Graba de lado, con el cuerpo entero a la vista y a 2–4 m</span></div></div>`:`
   <div class="tc-sec"><h4>Resumen</h4>${tcTiles(Q)}</div>
   <div class="tc-sec"><h4>Observaciones</h4>${obsRows}</div>
   <div class="tc-sec"><h4>Velocidad real con el disco</h4><div data-plate>${tcPlateModule(c.plate||plateState,Q,450)}</div></div>
   ${Q.tips.length?`<div class="tc-sec"><h4>Para medir mejor</h4>${Q.tips.map(t=>`<div class="tc-tip">${ic('info',18)}<span>${t}</span></div>`).join('')}</div>`:''}
   <div class="tc-sec"><h4>Capas del vídeo</h4><div class="tc-layers"><button class="tc-lchip on" data-a="lay" data-l="trail">Trayectoria de barra</button><button class="tc-lchip on" data-a="lay" data-l="skel">Articulaciones</button></div>
     <div class="tc-leg"><span><i style="background:linear-gradient(90deg,#8fb2ff,#f7cf73);width:30px"></i>Trayectoria: lenta → rápida</span><span><i style="background:repeating-linear-gradient(135deg,#f7cf73 0 3px,#f7cf7355 3px 6px)"></i>Parcial</span></div></div>
   <div class="tc-sec" style="padding-bottom:10px"><button class="tc-cta gh" style="width:100%" data-a="trends">${ic('trendUp',18)}Ver tendencia</button></div>`;
  const extra=noreps?`<div class="tc-pc"></div>${det_sections}`:`<div class="tc-pc" data-pc>
     <div class="tc-seg3" role="tablist">${[['vel','Velocidad'],['rom','ROM'],['time','Tiempo']].map(m=>`<button class="${(c.metric||'vel')===m[0]?'on':''}" data-a="metric" data-m="${m[0]}">${m[1]}</button>`).join('')}</div>
     <div data-chart>${tcChartHtml(c.metric||'vel',Q,corr,0)}</div>
     <div class="tc-det"><p data-detail></p><button class="tc-link" data-a="kind" data-k>Marcar parcial</button></div>
     <div class="tc-note" data-note>${Q.mps?'':'Velocidad relativa a la mejor repetición. Sin el disco calibrado no se da en m/s.'}</div></div>${det_sections}`;
  const menu=c.menu?`<div class="tc-scrim" data-a="menu"></div><div class="tc-menu">
    <button data-a="menu">${ic('history',20)}Reanalizar</button><button data-a="trends">${ic('trendUp',20)}Ver tendencia</button><button data-a="mov">${ic('sliders',20)}Elegir el tipo de movimiento</button><hr><button data-a="help">${ic('info',20)}Cómo leer este análisis</button></div>`:'';
  const toast=c.toast?`<div class="tc-toast"><p>${c.toast}</p><button class="tc-ib" data-a="toast" aria-label="Cerrar el aviso">${ic('close',18)}</button></div>`:'';
  const help=c.help?tcHelpSheet():'';
  return `<div class="tc-root tc-r ${land?'tc-land':''} tc-d-${det} tc-m-${mode==='datos'?'datos':mode==='tray'?'tray':'limpio'} ${c.enter?'tc-enter':''} ${noreps?'tc-noreps':''} ${(c.fsx&&c.fsx>=1.2)?'tc-big':''}" style="--W:${W}px;--H:${H}px">
   <div class="tc-vid">${tcSvg(land)}</div>${hud}${qcard}
   <div class="tc-isl tc-glass"><div class="t"><strong>${tcName}</strong><span>${tcSetInfo(entry)}</span></div><button class="tc-ib" data-a="menu" aria-label="Más opciones">${ic('more',22)}</button><button class="tc-ib f" data-a="close" aria-label="Cerrar vista">${ic('close',20)}</button></div>
   <div class="tc-sheet tc-glass" data-sheet><div class="tc-grab" data-grab role="button" aria-label="Detalle del análisis"><i></i></div>
    <div class="tc-hdr" data-hdr>${hdr}</div>${noreps?'':tcStripHtml(Q)}
    <div class="tc-x" data-x>${extra}</div>
    <div class="tc-scrub"><span class="tm tc-tn" data-now>0:00</span><div class="tc-track" data-track>${noreps?'<i class="tc-head"></i>':tcBandsHtml()}</div><span class="tm tc-tn">${tcClock(TCD)}</span></div>
    <div class="tc-ctrl"><div class="grp"><button class="fr" data-a="fb" aria-label="Fotograma anterior">${ic('tcFb',22)}</button><button class="fr" data-a="ff" aria-label="Fotograma siguiente">${ic('tcFf',22)}</button></div>
     <div class="grp"><button data-a="prev" aria-label="Repetición anterior" ${noreps?'disabled':''}>${ic('tcPrev',24)}</button><button class="play" data-a="play" aria-label="Pausar">${ic('pause',26)}</button><button data-a="next" aria-label="Repetición siguiente" ${noreps?'disabled':''}>${ic('tcNext',24)}</button></div>
     <button class="lp" data-a="loop" aria-label="Bucle en la repetición elegida" ${noreps?'disabled':''}>${ic('repeat',22)}</button></div></div>
   ${toast}${menu}${help}</div>`}
function tcPropSub(Q,corr,entry){
  if(entry!=='vivo')return Q.id==='ideal'?'Confianza alta':'+≈ 1 parcial · Confianza media · <b>Revisa el conteo</b>';
  const p=tcProposal(corr),orig=tcProposal({});
  const ch=p.txt!==orig.txt;
  return (Q.id==='ideal'?'Confianza alta':'Confianza media · <b>Revisa el conteo</b>')+(ch?` · Original: ${orig.txt}`:'')}
function tcHelpSheet(){return `<div class="tc-scrim" data-a="help"></div><div class="tc-fs" role="dialog" aria-label="Cómo leer este análisis"><h3>Cómo leer este análisis</h3>
  <p class="c">El análisis sale de tu vídeo: la pose y la barra se estiman fotograma a fotograma.</p>
  <div class="tc-note2"><b>Qué se puede comprobar</b><span>Seleccionar una repetición, desplazar la línea de tiempo, activar capas y consultar las métricas disponibles.</span></div>
  <div class="tc-note2"><b>Unidades y confianza</b><span>Sin escala física validada, la velocidad permanece en unidades relativas. Un dato ausente se muestra como «—»; los valores estimados llevan «≈».</span></div>
  <button class="tc-btn gh" data-a="help" style="margin-top:6px">Entendido</button></div>`}

/* ---------- comportamiento del resultado ---------- */
function tcBind(phone,c,rootEl){
  const root=rootEl||phone.querySelector('.tc-root');if(!root||root.dataset.bound)return;root.dataset.bound='1';
  const W=state.size[0],H=state.size[1],Q=tcQuality(c.q),land=!!c.land,noreps=!!c.noreps,entry=c.entry||'vivo';
  const frozen=document.documentElement.classList.contains('solo');
  const q=s=>root.querySelector(s),qa=s=>[...root.querySelectorAll(s)];
  const sheet=q('[data-sheet]'),xEl=q('[data-x]'),hud=q('.tc-hud'),vid=q('.tc-vid');
  const S={t:c.t0!=null?c.t0:3.2,play:!c.paused,speed:1,loop:false,sel:0,det:c.detent||'peek',prevDet:'peek',mode:c.mode||'datos',metric:c.metric||'vel',corr:c.corr?{...c.corr}:{},lay:{trail:true,skel:true},qopen:!!c.qopen,dia:450,lastSel:-1};
  if(S.t>0){S.sel=Math.max(0,tcRepAt(S.t,.5));}
  const R=tcRefs(vid);
  const fs=parseFloat(getComputedStyle(phone).getPropertyValue('--fs'))||1;
  if(fs>=1.2)root.classList.add('tc-big');
  const boxH=land?W*.5625:640;
  // ---- geometría: la hoja manda, el vídeo se acomoda encima ----
  function layout(initial){
    root.classList.add('tc-nt');const style=document.createElement('style');
    root.dataset.d=S.det;
    ['peek','med','full','min'].forEach(d=>root.classList.toggle('tc-d-'+d,d===S.det));
    const prev=xEl.style.height;xEl.style.height='0px';void sheet.offsetHeight;
    const fixed=sheet.offsetHeight,pc=q('[data-pc]'),pch=pc?pc.offsetHeight+4:0;
    let xh=0;if(S.det==='med')xh=Math.min(pch,Math.max(130,H-345-fixed));else if(S.det==='full')xh=Math.max(pch,H-252-fixed);
    if(!initial&&prev){xEl.style.height=prev;void xEl.offsetHeight}
    root.classList.remove('tc-nt');
    xEl.style.height=xh+'px';
    const top=H-(fixed+xh),stage=top-100,compact=S.det==='full';
    root.classList.toggle('tc-compact',compact);
    let vs,vx,vy;
    if(!land){vs=compact?tcClamp((stage-6)/470,.2,.5):tcClamp((stage-16)/470,.26,1);vx=compact?16:(W-360*vs)/2;vy=100;
      root.style.setProperty('--hud-top','100px');root.style.setProperty('--hud-h',Math.max(60,stage)+'px')}
    else{vs=compact?.46:1;const bh=boxH*vs;vy=compact?100:112;vx=compact?16:0;
      root.style.setProperty('--hud-top','100px');root.style.setProperty('--hud-h',Math.max(60,stage)+'px')}
    root.style.setProperty('--vs',vs.toFixed(3));root.style.setProperty('--vx',vx.toFixed(1)+'px');root.style.setProperty('--vy',vy.toFixed(1)+'px');
    root.style.setProperty('--mini',((land?W:360)*vs).toFixed(1)+'px');
    root.classList.toggle('tc-mini',vs<.95);
    S.geo={vs,vx,vy,top,hudTop:100};root.style.setProperty('--tt',Math.max(110,top-72)+'px');
  }
  // ---- estado → pantalla ----
  const setHtml=(sel,h)=>{const e=q(sel);if(e&&e.innerHTML!==h)e.innerHTML=h};
  function paintSel(){
    if(noreps)return;
    const i=S.sel,r=TCR[i],kind=S.corr[i]||r.k;
    qa('.tc-band').forEach(b=>{const k=+b.dataset.rep;b.classList.toggle('sel',k===i);b.classList.toggle('part',(S.corr[k]||TCR[k].k)==='P')});
    setHtml('[data-chart]',tcChartHtml(S.metric,Q,S.corr,i));
    setHtml('[data-detail]',`<b>${tcDetail(i,Q).split(' · ')[0]}</b> · ${tcDetail(i,Q).split(' · ').slice(1).join(' · ')}${S.corr[i]?`<em>Original: ${r.k==='P'?'Parcial':'Completa'} · Revisión: ${kind==='P'?'Parcial':'Completa'}</em>`:''}`);
    const kb=q('[data-k]');kb.textContent=kind==='F'?'Marcar parcial':'Marcar completa';
    const prop=tcProposal(S.corr);
    if(entry==='vivo'){setHtml('[data-prop]',prop.txt.replace(' reps','')+'<small>reps</small>');setHtml('[data-propsub]',tcPropSub(Q,S.corr,entry))}
    qa('.tc-seg3 button').forEach(b=>b.classList.toggle('on',b.dataset.m===S.metric));
    // lecturas de la repetición elegida
    const st=[];if(kind==='P')st.push(`<span class="tc-glass part">Parcial acortada <small>≈ 55 %</small></span>`);
    st.push(`<span class="tc-glass">ROM <small>${Q.obl?'≈ ':''}${r.rom}°</small></span>`,`<span class="tc-glass">Subida <small>${tcDec(r.L)} s</small></span>`,`<span class="tc-glass">Bajada <small>${tcDec(r.D)} s</small></span>`);
    setHtml('[data-stat]',st.join(''));
    setHtml('[data-repn]',`${i+1} de 6`);
    R.segs&&tcPaintTrail(R,i,S.t);S.lastSel=i;
    if(S.geo&&S.det==='med')layout(false);
  }
  const fmtV=t=>{const rel=Math.round(tcRel(t)*100);if(Q.mps){const i=Math.max(0,tcRepAt(t,0));const m=tcRel(t)*.78;return [tcDec(m,2).replace(/^/,'≈ '),'m/s']}return [rel+' %','de la mejor']};
  function paint(){
    const p=tcPaint(R,S.t);
    const ri=tcRepAt(S.t,.55);if(ri>=0&&ri!==S.sel&&!noreps){S.sel=ri;paintSel()}else if(!noreps)tcPaintTrail(R,S.sel,S.t);
    qa('.tc-head').forEach(h=>h.style.left=(S.t/TCD*100)+'%');
    const nowEl=q('[data-now]');nowEl.textContent=tcClock(S.t);
    if(noreps)return;
    const inRep=tcRepAt(S.t),r=TCR[S.sel];let ph='En el suelo',cls='';
    if(inRep>=0){const u=S.t-TCR[inRep].s;ph=u<TCR[inRep].L?'Subida':'Bajada';cls=u<TCR[inRep].L?'up':'dn'}
    const phEl=q('[data-ph]');if(phEl.dataset.v!==ph){phEl.dataset.v=ph;phEl.className='tc-ph '+cls;phEl.querySelector('span').textContent=ph}
    const tag=q('[data-vtag]'),g=S.geo;
    if(g){const k=land?W/640:1,wx=land?214.6+.62*p.b.x:p.b.x,wy=land?11+.62*p.b.y:p.b.y;
      const sx=g.vx+g.vs*k*wx,sy=g.vy+g.vs*k*wy-g.hudTop;
      const v=fmtV(S.t);tag.querySelector('b').textContent=v[0];tag.querySelector('small').textContent=v[1];
      if(!root.classList.contains('tc-compact')){tag.style.left=Math.min(W-52,sx+58)+'px';tag.style.top=Math.max(20,sy-8)+'px'}
      tag.style.opacity=(inRep>=0&&S.mode==='datos'&&tcRel(S.t)>.05)?1:0}
    // observación al pasar por su instante
    const ob=TC_OBS.find(o=>S.t>=o.t&&S.t<o.t+2.4);const obEl=q('[data-obs]');
    if(ob){if(obEl.dataset.i!==String(TC_OBS.indexOf(ob))){obEl.dataset.i=TC_OBS.indexOf(ob);obEl.querySelector('span').textContent=ob.txt}obEl.classList.add('show')}else{obEl.classList.remove('show');obEl.dataset.i=''}
    const lp=q('.lp');lp.classList.toggle('on',S.loop);
  }
  // ---- reproducción ----
  let last=performance.now(),raf=0;
  function tick(now){
    if(!root.isConnected)return;
    const dt=Math.min(.1,(now-last)/1000);last=now;
    if(S.play&&!frozen){S.t+=dt*S.speed;
      if(S.loop&&!noreps){const r=TCR[S.sel];if(S.t>r.e+.15)S.t=r.s-.25}
      if(S.t>=TCD)S.t=0}
    paint();raf=setTimeout(()=>tick(performance.now()),16)}
  function seek(t){S.t=tcClamp(t,0,TCD-.01);const ri=tcRepAt(S.t,.55);if(ri>=0&&!noreps){S.sel=ri;paintSel()}paint()}
  const setPlay=on=>{S.play=on;const b=q('.play');b.innerHTML=ic(on?'pause':'play',26);b.setAttribute('aria-label',on?'Pausar':'Reproducir')};
  setPlay(S.play);
  function setMode(m,tip){
    S.mode=m;root.classList.remove('tc-m-datos','tc-m-tray','tc-m-limpio');root.classList.add('tc-m-'+m);
    qa('.tc-modes button').forEach(b=>b.classList.toggle('on',b.dataset.m===m));
    if(m==='limpio'){if(S.det!=='min')S.prevDet=S.det;S.det='min'}else if(S.det==='min'){S.det=S.prevDet||'peek'}
    layout(false);
    if(tip){const t=q('[data-modetip]');t.textContent={datos:'Con datos',tray:'Solo trayectoria',limpio:'Limpio'}[m];t.classList.add('show');clearTimeout(S.tt);S.tt=setTimeout(()=>t.classList.remove('show'),1500)}
  }
  const dets=['peek','med','full'];
  function setDet(d){if(S.mode==='limpio'){S.mode='datos';setMode('datos');}S.det=d;layout(false)}
  // ---- eventos ----
  root.addEventListener('click',e=>{
    const b=e.target.closest('[data-a]');if(!b)return;const a=b.dataset.a;
    if(a==='play')setPlay(!S.play);
    else if(a==='prev'){const r=TCR[S.sel];seek(S.t>r.s+.7?r.s-.25:(TCR[Math.max(0,S.sel-1)].s-.25));}
    else if(a==='next'){const n=Math.min(TCR.length-1,S.sel+1);seek(TCR[n].s-.25)}
    else if(a==='fb'){setPlay(false);seek(S.t-1/30)}else if(a==='ff'){setPlay(false);seek(S.t+1/30)}
    else if(a==='loop'){S.loop=!S.loop;paint()}
    else if(a==='speed'){S.speed=+b.dataset.v;qa('.tc-speed button').forEach(x=>x.classList.toggle('on',x===b))}
    else if(a==='mode')setMode(b.dataset.m,true);
    else if(a==='q'){S.qopen=!S.qopen;q('[data-qcard]')&&q('[data-qcard]').classList.toggle('show',S.qopen)}
    else if(a==='det'){if(S.qopen){S.qopen=false;q('[data-qcard]').classList.remove('show')}setDet(S.det==='full'?'med':'full')}
    else if(a==='metric'){S.metric=b.dataset.m;paintSel();layout(false)}
    else if(a==='bar'){S.sel=+b.dataset.i;seek(TCR[S.sel].s+.4);S.lastSel=-1;paintSel()}
    else if(a==='kind'){const r=TCR[S.sel];const cur=S.corr[S.sel]||r.k;const nx=cur==='F'?'P':'F';if(nx===r.k)delete S.corr[S.sel];else S.corr[S.sel]=nx;paintSel()}
    else if(a==='obs'){const o=TC_OBS[+b.dataset.i];setDet('med');seek(o.t-.2)}
    else if(a==='lay'){const l=b.dataset.l;S.lay[l]=!S.lay[l];b.classList.toggle('on',S.lay[l]);root.classList.toggle('tc-lay-no'+l,!S.lay[l])}
    else if(a==='dia'){S.dia=+b.dataset.m;qa('.tc-dia [data-a=dia]').forEach(x=>x.classList.toggle('on',x===b));const ob=q('[data-a=dia-other]');if(ob){ob.classList.remove('on');ob.textContent='Otro'}}
    else if(a==='menu'){const has=q('.tc-menu');if(has){has.remove();q('.tc-scrim').remove()}else{root.insertAdjacentHTML('beforeend',`<div class="tc-scrim" data-a="menu"></div><div class="tc-menu"><button data-a="menu">${ic('history',20)}Reanalizar</button><button data-a="trends">${ic('trendUp',20)}Ver tendencia</button><button data-a="mov">${ic('sliders',20)}Elegir el tipo de movimiento</button><hr><button data-a="help">${ic('info',20)}Cómo leer este análisis</button></div>`)}}
    else if(a==='help'){const h=q('.tc-fs');if(h){h.remove();[...root.querySelectorAll('.tc-scrim')].forEach(x=>x.remove())}else{[...root.querySelectorAll('.tc-menu,.tc-scrim')].forEach(x=>x.remove());root.insertAdjacentHTML('beforeend',tcHelpSheet())}}
    else if(a==='toast'){const t=q('.tc-toast');t&&t.remove()}
  });
  // tirador: arrastrar o tocar
  const grab=q('[data-grab]');let gy=null,gd=0;
  grab.addEventListener('pointerdown',e=>{gy=e.clientY;gd=0;grab.setPointerCapture(e.pointerId)});
  grab.addEventListener('pointermove',e=>{if(gy!=null)gd=e.clientY-gy});
  grab.addEventListener('pointerup',e=>{if(gy==null)return;const i=dets.indexOf(S.det==='min'?'peek':S.det);
    if(Math.abs(gd)<8)setDet(dets[(i+1)%3]);else if(gd<-24)setDet(dets[Math.min(2,i+1)]);else if(gd>24)setDet(dets[Math.max(0,i-1)]);gy=null});
  // línea de tiempo: tocar o arrastrar
  const track=q('[data-track]');let dragging=false;
  const scrubTo=e=>{const r=track.getBoundingClientRect(),zoom=r.width/track.offsetWidth||1;seek(tcClamp((e.clientX-r.left)/r.width,0,1)*TCD)};
  track.addEventListener('pointerdown',e=>{dragging=true;track.setPointerCapture(e.pointerId);scrubTo(e)});
  track.addEventListener('pointermove',e=>{if(dragging)scrubTo(e)});
  track.addEventListener('pointerup',()=>dragging=false);
  // arranque
  paintSel();layout(true);
  try{document.fonts&&document.fonts.ready.then(()=>{if(root.isConnected)layout(false)})}catch(e){}
  if(S.mode!=='datos'){const m=S.mode;S.mode='datos';setMode(m,false)}
  paintSel();paint();
  if(S.qopen)q('[data-qcard]')&&q('[data-qcard]').classList.add('show');
  if(c.enter){setTimeout(()=>root.classList.remove('tc-enter'),2600)}
  if(!frozen)raf=setTimeout(()=>tick(performance.now()),16);
  root._tc={S,seek,layout,setDet,setMode};
}
function tcScene(id,tab,title,cfg,notes){
  scene({id,tab,title,html:()=>tcResultHtml(cfg),notes,after:ph=>tcBind(ph,cfg)});
}

/* ---------- «Analizando» en primer plano ---------- */
let tcUid=0;
function tcSignalPath(w,h){let d='';for(let i=0;i<=140;i++){const t=i/140*TCD,x=t/TCD*w,y=h-6-tcH(t)*(h-14);d+=(i?'L':'M')+x.toFixed(1)+' '+y.toFixed(1)}return d}
function tcPhaseState(p,queued,upd){
  if(queued&&p===0)return {pose:0,bar:0,reps:0,met:0};
  if(upd)return {reps:p>=60?2:1,met:p>=100?2:p>=60?1:0};
  return {pose:p>=95?2:1,bar:p>=95?2:1,reps:p>=98?2:p>=95?1:0,met:p>=100?2:p>=98?1:0};
}
function tcAnHtml(c){
  const pct=c.pct||0,queued=!!c.queued,upd=!!c.updating,wait=queued&&pct===0,uid=++tcUid,W=312,SH=74;
  const phs=upd?[['reps','Contando repeticiones'],['met','Calculando métricas']]:[['pose','Detectando tu pose'],['bar','Siguiendo la barra y el disco'],['reps','Contando repeticiones'],['met','Calculando métricas']];
  const st=tcPhaseState(pct,queued,upd);
  const cap=wait?'En cola: empieza en cuanto el móvil esté libre.':upd?'Se recalcula con los datos que ya tenemos.':'Analizando repeticiones y amplitud.';
  const d=tcSignalPath(W,SH);
  const marks=TCR.map((r,i)=>{const t=r.s+r.L,x=t/TCD*W,y=SH-6-r.a*(SH-14);return `<g class="mk" data-i="${i}" opacity="0"><circle cx="${x.toFixed(1)}" cy="${(y-11).toFixed(1)}" r="9" fill="#f2eee6"/><text x="${x.toFixed(1)}" y="${(y-7.2).toFixed(1)}" text-anchor="middle" font-family="Inter" font-weight="700" font-size="10.5" fill="#111">${i+1}</text></g>`}).join('');
  return `<div class="tc-root"><div class="tc-an ${queued?'queued':''} ${upd?'upd':''}" data-an>
   <div class="tc-ant"><div class="t"><div class="e">${upd?'Actualizando':wait?'En cola':'Analizando'}</div><strong>${tcName}</strong></div><button class="tc-ib f" data-a="close" aria-label="Cerrar: el análisis sigue en segundo plano">${ic('close',20)}</button></div>
   <div class="tc-vf"><div class="tc-vfw"><div class="tc-vfi">${tcSvg(false,'')}<i class="tc-sweep"></i><div class="tc-vfl tc-tn" data-fr>0:00 / ${tcClock(TCD)}</div></div><i class="tc-br a"></i><i class="tc-br b"></i><i class="tc-br c"></i><i class="tc-br d"></i></div></div>
   <div class="tc-sig"><svg viewBox="0 0 ${W} ${SH}" preserveAspectRatio="none" aria-hidden="true"><defs><clipPath id="tcc${uid}"><rect class="cl" x="0" y="-20" width="${(pct/100*W).toFixed(1)}" height="${SH+40}"/></clipPath><linearGradient id="tcg${uid}" x1="0" x2="1"><stop offset="0" stop-color="#8fb2ff"/><stop offset="1" stop-color="#f7cf73"/></linearGradient></defs>
     <path d="M0 ${SH-6}H${W}" stroke="#ffffff1f" stroke-width="1"/><path d="${TCR.map(r=>`M${((r.s)/TCD*W).toFixed(1)} ${SH-2}v4`).join('')}" stroke="#ffffff30"/>
     <path class="live" d="${d}" fill="none" stroke="${wait?'#ffffff33':`url(#tcg${uid})`}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" ${wait?'':`clip-path="url(#tcc${uid})"`} vector-effect="non-scaling-stroke"/>${wait?'':marks}</svg><i class="tc-sd" data-sd></i></div>
   <div class="tc-pct"><b class="tc-tn" data-pct>${wait?'<span style="font-size:calc(30px*var(--fs));letter-spacing:-.8px">En cola</span>':`${pct}<small>%</small>`}</b><p>${cap}</p></div>
   <div class="tc-pb"><i data-pb style="width:${pct}%"></i></div>
   <div class="tc-phs" data-phs>${phs.map(p=>{const s=st[p[0]];return `<div class="${s===2?'done':s===1?'act':''}" data-ph="${p[0]}"><i>${s===2?ic('check',11):''}</i>${p[1]}</div>`}).join('')}</div>
   <div class="tc-anf"><div class="c">Puedes salir: sigue en segundo plano.</div><button class="tc-btn danger" data-a="cancel">${ic('tcCancel',20)}Cancelar análisis</button></div>
  </div></div>`}
function tcAnBind(phone,c){
  const an=phone.querySelector('.tc-an');if(!an||an.dataset.bound)return;an.dataset.bound='1';
  const frozen=document.documentElement.classList.contains('solo');
  const R=tcRefs(an.querySelector('.tc-vfi'));let pct=frozen&&c.still!=null?c.still:(c.pct||0);const q=s=>an.querySelector(s);
  const upd=!!c.updating,queued=!!c.queued;
  function paint(){
    const t=queued&&pct===0?1.0:upd?3.3:tcClamp(pct/100*TCD,0,TCD-.05);
    tcPaint(R,t);R.segs.forEach(sg=>{const r=+sg.dataset.r;sg.style.opacity=(queued&&pct===0)?0:(+sg.dataset.t<=t||upd)?.92:0});
    if(an.querySelector('[data-fr]'))q('[data-fr]').textContent=tcClock(t)+' / '+tcClock(TCD);
    const cl=q('.cl');if(cl)cl.setAttribute('width',(upd?312:pct/100*312).toFixed(1));
    const sd=q('[data-sd]');if(sd){const show=!upd&&!(queued&&pct===0)&&pct<95;sd.style.display=show?'block':'none';sd.style.left=(pct/100*100)+'%';sd.style.top=((74-6-tcH(t)*(74-14))/74*100)+'%'}
    if(!(queued&&pct===0)){q('[data-pct]').innerHTML=`${Math.floor(pct)}<small>%</small>`;q('[data-pb]').style.width=pct+'%'}
    const st=tcPhaseState(Math.floor(pct),queued,upd);
    an.querySelectorAll('[data-ph]').forEach(e=>{const s=st[e.dataset.ph];const cls=s===2?'done':s===1?'act':'';if(e.className!==cls){e.className=cls;e.querySelector('i').innerHTML=s===2?ic('check',11):''}});
    // las repeticiones se «cuentan» una a una entre el 95 y el 98 %
    an.querySelectorAll('.mk').forEach(m=>{const i=+m.dataset.i;m.setAttribute('opacity',(upd||pct>=95+i*.5)?1:0)});
  }
  paint();
  if(frozen||queued&&pct===0)return;
  let last=performance.now();
  function step(now){
    if(!an.isConnected)return;const dt=Math.min(.1,(now-last)/1000);last=now;
    const rate=upd?100/5.5:pct<95?95/(c.dur||10.5):5/1.9;pct=Math.min(100,pct+rate*dt);paint();
    if(pct>=100){setTimeout(finish,650);return}setTimeout(()=>step(performance.now()),16)}
  function finish(){
    if(!an.isConnected)return;an.classList.add('out');const old=an.parentNode,cfg={entry:c.entry||'vivo',q:'lost',detent:'peek',t0:.4,enter:true};
    old.insertAdjacentHTML('afterend',tcResultHtml(cfg));const nr=old.nextElementSibling;tcBind(phone,cfg,nr);setTimeout(()=>old.remove(),900)}
  setTimeout(()=>step(performance.now()),16);
}

/* ---------- estados con el vídeo quieto y una tarjeta ---------- */
function tcStillHtml(c){
  const card=`<div class="tc-card tc-glass"><div class="ico ${c.err?'err':''}">${ic(c.icon||'info',24)}</div><h3>${c.title}</h3><p>${c.text}</p>${c.btn?`<div class="acts"><button class="tc-btn" data-a="act">${c.btn}</button></div>`:''}</div>`;
  return `<div class="tc-root"><div class="tc-still">${tcSvg(false)}</div><div class="tc-playbig tc-glass" aria-hidden="true">${ic('play',30)}</div>
   <div class="tc-isl tc-glass"><div class="t"><strong>${c.name||tcName}</strong><span>${c.sub||tcSetInfo('vivo')}</span></div>${c.menu?`<button class="tc-ib" data-a="menu" aria-label="Más opciones">${ic('more',22)}</button>`:''}<button class="tc-ib f" data-a="close" aria-label="Cerrar vista">${ic('close',20)}</button></div>${card}</div>`}
function tcStillBind(phone){const root=phone.querySelector('.tc-root');const R=tcRefs(root);tcPaint(R,3.3);R.segs.forEach(s=>s.style.opacity=0);
  root.querySelectorAll('.tc-skel,.tc-dotg').forEach(e=>e.style.display='none')}
function tcStill(id,title,cfg,notes){scene({id,tab:'tecnica',title,html:()=>tcStillHtml(cfg),notes,after:ph=>tcStillBind(ph)})}

/* ---------- marcar el disco a mano ---------- */
function tcOtherDlg(val){return `<div class="tc-scrim" style="z-index:40"></div><div class="tc-dlg" role="dialog" aria-label="Diámetro del disco"><h3>Diámetro del disco</h3><p>En centímetros, de 10 a 70.</p><div class="tc-field" data-fld><input data-in value="${val}" inputmode="decimal" aria-label="Diámetro en centímetros"><small>cm</small></div><div class="tc-err" data-err></div><div class="tc-dacts"><button data-a="cancel">Cancelar</button><button class="p" data-a="accept">Aceptar</button></div></div>`}
function tcMarkHtml(c){
  const dia=c.dia||450;
  const dlg=c.other?tcOtherDlg(c.val||'42,5'):'';
  return `<div class="tc-root"><div class="tc-mk">
   <div class="tc-mkt"><strong>Marca el disco</strong><button class="tc-ib f" data-a="close" aria-label="Cerrar sin guardar">${ic('close',20)}</button></div>
   <div class="tc-mkf" data-mkf>${tcSvg(false)}<div class="tc-circ" data-circ><i></i><i class="h"></i></div><div class="tc-loupe" data-loupe></div></div>
   <div class="tc-mkb"><p class="c">Arrastra el círculo hasta el disco y pellizca para ajustar su tamaño.</p>
    <div class="cap">Diámetro del disco, en cm</div>
    <div class="tc-dia" style="margin:0" data-dia>${[450,400,350].map(m=>`<button class="${dia===m?'on':''}" data-a="dia" data-m="${m}">${m/10}</button>`).join('')}<button class="${[450,400,350].includes(dia)?'':'on'}" data-a="other">${[450,400,350].includes(dia)?'Otro':tcDec(dia/10,1)+' cm'}</button></div>
    <button class="tc-btn" data-a="save" style="margin-top:6px">Guardar</button></div>${dlg}</div></div>`}
function tcMarkBind(phone,c){
  const root=phone.querySelector('.tc-root'),f=root.querySelector('[data-mkf]'),circ=root.querySelector('[data-circ]'),lp=root.querySelector('[data-loupe]');
  const svg=f.querySelector('.tc-svg'),R=tcRefs(svg);tcPaint(R,1.0);R.segs.forEach(s=>s.style.opacity=0);svg.querySelectorAll('.tc-skel,.tc-dotg').forEach(e=>e.style.display='none');
  let box,cx,cy,r,drag=null;
  function geom(){const fr=f.getBoundingClientRect(),zoom=fr.width/f.offsetWidth||1,sr=svg.getBoundingClientRect();box={fw:f.offsetWidth,fh:f.offsetHeight,sw:sr.width/zoom,sh:sr.height/zoom,sl:(sr.left-fr.left)/zoom,st:(sr.top-fr.top)/zoom,zoom,fl:fr.left,ft:fr.top}}
  function place(){circ.style.width=circ.style.height=(r*2)+'px';circ.style.left=(cx-r)+'px';circ.style.top=(cy-r)+'px'}
  function init(){geom();const k=box.sh/640;const px=box.sl+186*k,py=box.st+428*k;r=38*k*.9;cx=px+(c.off===0?0:9*k);cy=py+(c.off===0?0:-7*k);place()}
  init();
  const loupe=on=>{lp.classList.toggle('show',on);if(!on)return;if(!lp.firstChild){const cl=svg.cloneNode(true);cl.style.cssText='position:absolute;left:0;top:0;filter:none';lp.appendChild(cl)}
    const k=box.sh/640,z=3,cl=lp.firstChild;cl.style.width=(box.sw*z)+'px';cl.style.height=(box.sh*z)+'px';cl.style.left=(46-(cx-box.sl)*z)+'px';cl.style.top=(46-(cy-box.st)*z)+'px';
    lp.style.left=tcClamp(cx-46,4,box.fw-96)+'px';lp.style.top=Math.max(4,cy-r-110)+'px'};
  const pos=e=>({x:(e.clientX-box.fl)/box.zoom,y:(e.clientY-box.ft)/box.zoom});
  f.addEventListener('pointerdown',e=>{geom();const p=pos(e);const inside=Math.hypot(p.x-cx,p.y-cy)<=r+16;drag={dx:inside?cx-p.x:0,dy:inside?cy-p.y:0};if(!inside){cx=p.x;cy=p.y}f.setPointerCapture(e.pointerId);place();loupe(true)});
  f.addEventListener('pointermove',e=>{if(!drag)return;const p=pos(e);cx=tcClamp(p.x+drag.dx,0,box.fw);cy=tcClamp(p.y+drag.dy,0,box.fh);place();loupe(true)});
  f.addEventListener('pointerup',()=>{drag=null;loupe(false)});
  f.addEventListener('wheel',e=>{e.preventDefault();r=tcClamp(r*(e.deltaY<0?1.06:.94),14,Math.min(box.fw,box.fh)/2);place()},{passive:false});
  root.addEventListener('click',e=>{const b=e.target.closest('[data-a]');if(!b)return;const a=b.dataset.a;
    if(a==='dia'){root.querySelectorAll('[data-dia] [data-a=dia]').forEach(x=>x.classList.toggle('on',x===b));const o=root.querySelector('[data-dia] [data-a=other]');o.classList.remove('on');o.textContent='Otro'}
    else if(a==='other'){root.insertAdjacentHTML('beforeend',tcOtherDlg('42,5'))}
    else if(a==='cancel'){root.querySelector('.tc-dlg')&&root.querySelector('.tc-dlg').remove();root.querySelectorAll('.tc-scrim').forEach(x=>x.remove())}
    else if(a==='accept'){const v=parseFloat(String(root.querySelector('[data-in]').value).replace(',','.'));if(v>=10&&v<=70){const o=root.querySelector('[data-dia] [data-a=other]');root.querySelectorAll('[data-dia] [data-a=dia]').forEach(x=>x.classList.remove('on'));o.classList.add('on');o.textContent=tcDec(v,1)+' cm';root.querySelector('.tc-dlg').remove();root.querySelectorAll('.tc-scrim').forEach(x=>x.remove())}}});
  root.addEventListener('input',e=>{if(!e.target.matches('[data-in]'))return;const v=parseFloat(String(e.target.value).replace(',','.')),bad=e.target.value!==''&&!(v>=10&&v<=70);
    root.querySelector('[data-err]').textContent=bad?'Entre 10 y 70 cm':'';root.querySelector('[data-fld]').classList.toggle('err',bad);root.querySelector('[data-a=accept]').style.opacity=(e.target.value!==''&&!bad)?1:.4});
}

/* ---------- hoja «¿Qué movimiento es?» ---------- */
const TC_MOVS=[['Peso muerto','Convencional o sumo'],['Sentadilla','Con barra, mancuernas o peso corporal'],['Press de banca','Con barra o mancuernas'],['Press militar','De pie o sentado'],['Remo','Con barra, mancuerna o polea'],
  ['Dominadas y jalones','Dominadas y jalón al pecho'],['Zancada','Zancadas y sentadilla búlgara'],['Peso muerto rumano','Rumano y buenos días'],['Empuje de cadera','Con barra o en máquina'],['Curl de bíceps','Con barra, mancuernas o polea'],['Tríceps sobre la cabeza','Francés y extensión sobre la cabeza'],['Tríceps en polea','Polea y patada'],
  ['Elevaciones','Laterales o frontales'],['Flexiones','Con el peso del cuerpo'],['Fondos','En paralelas o entre bancos'],['Sentadilla en máquina','En máquina guiada'],['Subida al cajón','Con cajón o banco'],['Extensión de espalda','En banco de hiperextensiones'],['Extensión de piernas','En máquina de cuádriceps'],['Curl femoral','En máquina de femorales'],['Gemelos de pie','De pie, en máquina o con peso'],['Gemelos sentado','En máquina, sentado'],['Encogimientos','Con barra o mancuernas'],['Aperturas','Con mancuernas, poleas o máquina']];
function tcMovHtml(c){
  const n=c.all?TC_MOVS.length:5,sel=c.sel||0;
  const rows=TC_MOVS.slice(0,n).map((m,i)=>`<button class="tc-radio ${i===sel?'on':''}" data-a="pick" data-i="${i}" role="radio" aria-checked="${i===sel}"><span class="rd"></span><span><strong>${m[0]}</strong><span>${m[1]}</span></span></button>`).join('');
  return `<div class="tc-root"><div class="tc-still">${tcSvg(false)}</div>
   <div class="tc-isl tc-glass"><div class="t"><strong>${tcName}</strong><span>${tcSetInfo('vivo')}</span></div><button class="tc-ib" data-a="menu" aria-label="Más opciones">${ic('more',22)}</button><button class="tc-ib f" data-a="close" aria-label="Cerrar vista">${ic('close',20)}</button></div>
   <div class="tc-scrim" style="z-index:38"></div><div class="tc-fs" style="max-height:calc(var(--H) - 110px)"><h3>¿Qué movimiento es?</h3><p class="c">No reconocemos este ejercicio. Elige el que más se parezca y medimos su técnica.</p>
   <div data-list role="radiogroup" aria-label="Movimientos" style="overflow-y:auto;scrollbar-width:none;${c.all?'max-height:340px;':''}">${rows}</div>
   <button class="tc-tx" data-a="toggle">${c.all?'Ver menos':'Ver los 24 movimientos'}</button><button class="tc-btn" data-a="go">Analizar</button></div></div>`}
function tcMovBind(phone,c){
  const root=phone.querySelector('.tc-root'),R=tcRefs(root);tcPaint(R,3.3);R.segs.forEach(s=>s.style.opacity=0);root.querySelectorAll('.tc-skel,.tc-dotg').forEach(e=>e.style.display='none');
  root.style.setProperty('--H',state.size[1]+'px');
  let sel=c.sel||0,all=!!c.all;
  root.addEventListener('click',e=>{const b=e.target.closest('[data-a]');if(!b)return;
    if(b.dataset.a==='pick'){sel=+b.dataset.i;root.querySelectorAll('.tc-radio').forEach((x,i)=>{x.classList.toggle('on',i===sel);x.setAttribute('aria-checked',i===sel)})}
    else if(b.dataset.a==='toggle'){all=!all;const l=root.querySelector('[data-list]');l.innerHTML=TC_MOVS.slice(0,all?24:5).map((m,i)=>`<button class="tc-radio ${i===sel?'on':''}" data-a="pick" data-i="${i}"><span class="rd"></span><span><strong>${m[0]}</strong><span>${m[1]}</span></span></button>`).join('');l.style.maxHeight=all?'340px':'';b.textContent=all?'Ver menos':'Ver los 24 movimientos'}})}

/* ---------- estados del disco (el módulo «Velocidad real con el disco») ---------- */
function tcDiscHtml(){
  const Q1=tcQuality('lost'),Q2=tcQuality('ideal');
  const sec=(lab,inner)=>`<div class="tc-lab">${lab}</div><div class="tc-plm" style="background:#ffffff08;border-color:#ffffff14">${inner}</div>`;
  return `<div class="tc-root"><div class="tc-pl" data-pl>
   <div class="pl-eyebrow" style="margin:6px 4px 4px">Detalle · Velocidad real con el disco</div>
   ${sec('1 · Disco hallado, sin confirmar',tcPlateModule('suggested',Q1,450))}
   ${sec('2 · Confirmado, en m/s',tcPlateModule('confirmed',Q2,450))}
   ${sec('3 · Confirmado, seguimiento interrumpido',tcPlateModule('confirmed',Q1,450))}
   ${sec('4 · Sin disco',tcPlateModule('none',Q1,450))}
   ${sec('Avisos de la escala (cada uno con su causa)',`<div class="tc-warn" style="margin:0">${ic('tcWarn',18)}<span>El disco se ve ovalado: la cámara no estaba de frente a la barra.</span></div><div class="tc-warn">${ic('tcWarn',18)}<span>Se descartó un disco que no cuadraba con tu posición.</span></div><div class="tc-warn">${ic('tcWarn',18)}<span>No se pudo corregir el paralaje: el desvío respecto del medio pie no se muestra.</span></div><div class="tc-warn">${ic('tcWarn',18)}<span>El tamaño del disco no cuadra con tu cuerpo: revisa su diámetro.</span></div><div class="tc-warn">${ic('tcWarn',18)}<span>No podemos comprobar el seguimiento completo del disco. Pulsa Cambiar para volver a marcarlo y recalcular sus medidas.</span></div>`)}
  </div></div>`}

/* ---------- ayuda previa: «Para medir mejor» antes de grabar ---------- */
function tcCamHtml(c){
  const tips=['Graba de lado, con el cuerpo entero a la vista y a 2–4 m','Coloca el móvil a la altura de la barra y de frente al disco','Que solo salgas tú en el encuadre'];
  return `<div class="tc-root"><div class="tc-cam">${tcSvg(false)}
   <svg class="tc-guide" viewBox="0 0 360 880" preserveAspectRatio="none" aria-hidden="true"><g fill="none" stroke="#f2eee6" stroke-width="2" stroke-linecap="round" opacity=".75"><path d="M44 336v-30h30M316 336v-30h-30M44 676v30h30M316 676v30h-30"/></g></svg>
   <div class="tc-isl tc-glass"><div class="t"><strong>Modo visual · serie 3</strong><span>Serie 3 · 80 kg · 6 reps</span></div><button class="tc-ib f" data-a="close" aria-label="Cerrar">${ic('close',20)}</button></div>
   <div class="tc-ptip tc-glass" data-tip><h5><span>Para medir mejor</span></h5>${tips.map(t=>`<div class="tc-tip">${ic('info',18)}<span>${t}</span></div>`).join('')}<button class="tc-btn gh" data-a="ok" style="margin-top:8px;height:46px">Entendido</button></div>
   <div class="tc-rec"><button class="tc-chip tc-glass" style="position:static;height:40px;display:${c.open?'none':'inline-flex'}" data-a="tip">${ic('info',16)}Para medir mejor</button><div class="tc-tabs2 tc-glass"><span>Foto</span><span class="on">Vídeo</span></div><div class="tc-recb" role="button" aria-label="Grabar"><i></i></div></div></div></div>`}
function tcCamBind(phone,c){
  const root=phone.querySelector('.tc-root'),R=tcRefs(root);tcPaint(R,1.0);R.segs.forEach(s=>s.style.opacity=0);root.querySelectorAll('.tc-skel,.tc-dotg').forEach(e=>e.style.display='none');
  const tip=root.querySelector('[data-tip]'),chip=root.querySelector('[data-a=tip]');
  tip.style.transition='opacity .25s,transform .3s';
  const set=o=>{tip.style.opacity=o?1:0;tip.style.pointerEvents=o?'auto':'none';tip.style.transform=o?'none':'translateY(14px)';chip.style.display=o?'none':'inline-flex'};
  set(!!c.open);
  root.addEventListener('click',e=>{const b=e.target.closest('[data-a]');if(!b)return;if(b.dataset.a==='tip')set(true);if(b.dataset.a==='ok')set(false)})}

/* ---------- Perfil: elegir vídeo y ejercicio ---------- */
function tcImpHtml(c){
  const st=c.st||'listo',has=st!=='vacio';
  const thumb=`<div class="tc-thumb">${tcSvg(false)}<b>0:14</b></div>`;
  const recents=['Peso muerto convencional','Sentadilla trasera','Press de banca','Remo con barra'];
  return `<div class="tc-root"><div class="tc-im"><div style="padding:44px 16px"><div style="height:56px;display:flex;align-items:center"><span class="tc-ib">${ic('back',22)}</span></div><div class="pl-h1" style="margin-top:6px">Técnica en vídeo</div><div style="height:52px;border-radius:26px;background:var(--ink);margin-top:20px;opacity:.45;color:#111;font:600 15px Inter;display:flex;align-items:center;justify-content:center">Analizar un vídeo de la galería</div></div></div>
   <div class="tc-scrim" style="z-index:38"></div><div class="tc-fs" data-imp><h3>Analizar un vídeo</h3>
   ${has?`<div class="tc-imv">${thumb}<div style="flex:1;min-width:0"><div style="font-size:calc(13.5px*var(--fs));font-weight:600">Vídeo seleccionado · 0:14 min</div><div style="font-size:calc(12px*var(--fs));color:var(--faint);margin-top:3px">Vertical · 30 fps</div>${st==='guardando'?'':`<button class="tc-link" style="padding:0;height:40px;margin-top:2px" data-a="video">Cambiar vídeo</button>`}</div></div>`:
     `<p class="c">Elige un vídeo de tu galería de hasta 10 minutos.</p><button class="tc-btn gh" data-a="video">${ic('upload',20)}Elegir vídeo</button>`}
   <div class="tc-lbl">¿Qué ejercicio es?</div>
   <div class="tc-fch" data-ex>${recents.map((r,i)=>`<button class="${(has&&i===(c.ex||0))?'on':''}" data-a="ex" data-i="${i}" ${st==='guardando'?'disabled':''}>${r}</button>`).join('')}<button data-a="cat" ${st==='guardando'?'disabled':''}>${ic('search',16)}Buscar en el catálogo</button></div>
   <div class="tc-lbl">Carga (opcional)</div><div class="tc-kg"><div class="fl ${c.kg?'':'ph'}" data-kg>${c.kg||'Opcional'}</div><span style="color:var(--muted);font-size:calc(14px*var(--fs))">kg</span></div>
   ${c.err?`<div style="color:var(--danger);font-size:calc(13px*var(--fs))">${c.err}</div>`:''}
   <button class="tc-btn" data-a="go" ${(!has||st==='guardando')?'disabled':''} style="margin-top:6px">${st==='guardando'?'Guardando y preparando…':'Analizar'}</button></div></div>`}
function tcImpBind(phone,c){
  const root=phone.querySelector('.tc-root'),R=tcRefs(root);tcPaint(R,3.3);R.segs.forEach(s=>s.style.opacity=0);root.querySelectorAll('.tc-skel,.tc-dotg').forEach(e=>e.style.display='none');
  root.addEventListener('click',e=>{const b=e.target.closest('[data-a=ex]');if(!b)return;root.querySelectorAll('[data-a=ex]').forEach(x=>x.classList.toggle('on',x===b))})}

/* ---------- cargando (esqueleto) ---------- */
function tcLoadHtml(){
  return `<div class="tc-root"><div class="tc-isl tc-glass"><div class="t"><div class="tc-sk" style="width:150px;height:14px"></div><div class="tc-sk" style="width:110px;height:10px;margin-top:7px"></div></div><button class="tc-ib f" data-a="close" aria-label="Cerrar vista">${ic('close',20)}</button></div>
   <div class="tc-vid" style="--vy:100px;transform:translate(0,100px);background:#0d0d0e"><div class="tc-sk" style="position:absolute;inset:0;border-radius:0"></div></div>
   <div class="tc-sheet tc-glass"><div class="tc-grab"><i></i></div><div class="tc-sk" style="width:60%;height:26px;margin-top:6px"></div><div class="tc-sk" style="width:80%;height:12px;margin-top:10px"></div>
   <div style="display:flex;gap:10px;margin:16px 0"><div class="tc-sk" style="flex:1;height:48px"></div><div class="tc-sk" style="flex:1;height:48px"></div><div class="tc-sk" style="flex:1;height:48px"></div></div><div class="tc-sk" style="height:40px"></div><div class="tc-sk" style="height:58px;margin-top:10px;border-radius:29px"></div></div></div>`}
/* ===== registro de las escenas ===== */
const TCH2='h02-tecnica';
scene({id:'tc-perfil-importar',tab:'tecnica',title:'Perfil · analizar un vídeo de la galería',html:()=>tcImpHtml({st:'listo',kg:'80'}),after:ph=>tcImpBind(ph,{}),notes:{
  tags:[['keep','hoja real'],['move','vídeo con miniatura']],
  p:['La entrada desde el Perfil («Técnica en vídeo» → «Analizar un vídeo de la galería»): hoja plana con la <b>miniatura</b> del vídeo elegido y su duración, el ejercicio (los recientes y «Buscar en el catálogo») y la carga opcional. «Analizar» guarda una copia y abre «Analizando» a pantalla completa; al terminar, el mismo resultado de la sesión en vivo, sin serie asociada.',
     'Estados reales: sin vídeo («Elige un vídeo de tu galería de hasta 10 minutos» y «Elegir vídeo»), leyendo («Leyendo el vídeo…»), guardando («Guardando y preparando…», todo bloqueado) y error en rojo.']}});
scene({id:'tc-perfil-importar-vacio',tab:'tecnica',title:'Perfil · sin vídeo elegido',html:()=>tcImpHtml({st:'vacio'}),after:ph=>tcImpBind(ph,{}),notes:{tags:[['keep','texto real']],p:['Antes de elegir el vídeo.']}});
scene({id:'tc-perfil-guardando',tab:'tecnica',title:'Perfil · guardando y preparando',html:()=>tcImpHtml({st:'guardando',kg:'80'}),after:ph=>tcImpBind(ph,{}),notes:{tags:[['keep','texto real']],p:['La copia del vídeo se está confirmando: la hoja no se puede cerrar.']}});
scene({id:'tc-grabar',tab:'tecnica',title:'Antes de grabar · «Para medir mejor»',html:()=>tcCamHtml({open:true}),after:ph=>tcCamBind(ph,{open:true}),notes:{
  tags:[['move','antes: solo al final del análisis'],['new','ayuda previa en el grabador']],
  p:['«Para medir mejor» sale <b>antes</b> de grabar, donde sirve: una tarjeta de cristal sobre la vista previa de la cámara con los consejos del motor que aplican al ejercicio (aquí, con barra: de lado y cuerpo entero a 2–4 m, a la altura de la barra y de frente al disco, y que solo salgas tú). Se cierra con «Entendido» y queda como una etiqueta pequeña sobre el botón de grabar para volver a abrirla. Las esquinas del encuadre son una guía, sin texto.',
     'En el resultado, esos mismos consejos aparecen como pista corta dentro de la tarjeta de calidad (escena «Calidad de la medición»).']}});
scene({id:'tc-analizando',tab:'tecnica',title:'Analizando · en primer plano',html:()=>tcAnHtml({pct:46}),after:ph=>tcAnBind(ph,{pct:0,still:46}),notes:{
  tags:[['new','animación propia'],['keep','progreso real del motor'],['new','Cancelar análisis'],['ask','fases']],img:TCH2,imgCap:'Hoy: un módulo «Análisis» con una barra, sobre el vídeo.',
  p:['El análisis ocupa la pantalla. Lo que se ve es <b>lo que de verdad pasa</b>: el motor recorre el vídeo fotograma a fotograma, así que el visor enseña <b>el fotograma que se está procesando ahora</b> (su hora, su pose, la estela de la barra que ya ha medido) y, debajo, la señal «altura de la barra» se dibuja de izquierda a derecha al mismo ritmo que el porcentaje. Al 95 % acaba el recorrido y las repeticiones se <b>cuentan una a una</b> sobre la señal.',
     'Cuatro fases, con el estado real: «Detectando tu pose» y «Siguiendo la barra y el disco» van a la vez (es una sola pasada); «Contando repeticiones» y «Calculando métricas» llegan al final. <b>Cancelar análisis</b> detiene el trabajo (el vídeo se conserva y queda «Aún sin analizar»); ✕ solo sale, y el análisis sigue en segundo plano como hoy.',
     'Esta escena corre sola de 0 a 100 en unos 12 s y entra en el resultado; vuelve a elegirla para repetirla.']}});
scene({id:'tc-exito',tab:'tecnica',title:'Éxito · cómo entra el resultado',html:()=>tcAnHtml({pct:100}),after:ph=>tcAnBind(ph,{pct:93,dur:1,still:100}),notes:{
  tags:[['new','transición']],p:['Al llegar al 100 % las cuatro fases se marcan hechas, el visor se abre hasta ocupar la pantalla (el vídeo pierde su velo y su desenfoque), la hoja sube desde abajo y las lecturas de cristal entran una a una: calidad, modos, repetición, ROM y tiempos, velocidad. Con «reducir movimiento» no hay desplazamientos: solo aparece el resultado.']}});
scene({id:'tc-cola',tab:'tecnica',title:'En cola',html:()=>tcAnHtml({pct:0,queued:true}),after:ph=>tcAnBind(ph,{pct:0,queued:true}),notes:{
  tags:[['keep','texto real']],p:['«En cola: empieza en cuanto el móvil esté libre.» No hay porcentaje (aún no empezó): la línea de la señal respira en gris y las cuatro fases esperan.']}});
scene({id:'tc-actualizando',tab:'tecnica',title:'Actualizando el análisis',html:()=>tcAnHtml({pct:62,updating:true}),after:ph=>tcAnBind(ph,{pct:20,updating:true,still:62}),notes:{
  tags:[['keep','recalcular con un motor nuevo']],p:['Cuando el motor mejora, los análisis viejos se recalculan sin volver a leer el vídeo: «Se recalcula con los datos que ya tenemos.» La señal ya está completa y solo hay dos fases: contar repeticiones y calcular métricas.']}});
tcScene('tc-resultado-vivo','tecnica','Resultado · desde la sesión en vivo',{entry:'vivo',q:'lost',detent:'peek',t0:6.15},{
  tags:[['new','rediseño'],['keep','mismos datos del motor'],['move','sin pestañas ni scroll']],img:TCH2,imgCap:'Hoy: vídeo pequeño arriba, mandos, tres pestañas y un panel que obliga a hacer scroll.',
  p:['<b>Todo sobre el vídeo.</b> El vídeo ocupa la pantalla; la trayectoria se pinta por velocidad (azul lenta, ámbar rápida) y deja estela de la repetición en curso; el esqueleto y el punto de la barra siguen al lifter. Las lecturas son de cristal y cambian según el instante: <b>fase</b> (subida / bajada / en el suelo), <b>velocidad instantánea</b> pegada a la barra, y ROM y tiempos de <b>la repetición en curso</b>.',
     'Abajo, una sola hoja con alturas (tirador): <b>conteo propuesto</b> y la acción «Usar en la serie 3» siempre a la vista, tres cifras clave, la línea de tiempo con una banda por repetición (alto = velocidad, rayada = parcial, punto ámbar = observación) y los mandos. Esta muestra es tu caso real: peso muerto, vista oblicua, seguimiento del disco interrumpido, confianza media.'],
  list:['Hoja en <b>3 alturas + «limpio»</b>: tocar o arrastrar el tirador (o tocar una cifra). Los mandos nunca se mueven.','Línea de tiempo: toca o arrastra; las bandas saltan a la repetición.','«Con datos · Solo trayectoria · Limpio» arriba a la derecha; 0,25× · 0,5× · 1× abajo a la derecha del vídeo.','Fotograma ‹ › y repetición ◁ ▷ en los mandos; bucle sobre la repetición elegida.'],listTitle:'Qué responde'});
tcScene('tc-resultado-perfil','tecnica','Resultado · desde el Perfil',{entry:'perfil',q:'lost',detent:'peek',t0:8.1},{
  tags:[['keep','misma pieza'],['move','cambia cabecera y acción']],
  p:['La misma pantalla. En el Perfil no hay serie asociada: la cabecera de la isla lleva la <b>carga y la fecha</b> («80 kg · 13 oct») en lugar de «Serie 3 · 80 kg × 6 · 13 oct», y la acción «Usar en la serie» desaparece (hoy tampoco existe fuera de una sesión). En su lugar queda «Ver tendencia» del ejercicio, que hoy es un botón al final de «Métricas».',
     'Se vuelve con ✕ (a la ficha del Perfil, o a la sesión en vivo si se abrió desde allí). ⋯ conserva Reanalizar, Ver tendencia y Elegir el tipo de movimiento.']});
tcScene('tc-repeticion','tecnica','Por repetición · altura media',{entry:'vivo',q:'lost',detent:'med',t0:6.0,corr:{}},{
  tags:[['move','antes: pestaña «Repeticiones»'],['keep','corregir completa/parcial']],
  p:['Subir la hoja a media altura enseña <b>«Por repetición»</b>: velocidad, ROM o tiempo, barras tocables (saltan a esa repetición en el vídeo), la línea de detalle tal como la redacta el motor y «Marcar parcial / Marcar completa». El vídeo se acomoda encima, siempre entero, y sigue en marcha.',
     'Prueba a marcar una repetición como parcial: el <b>conteo propuesto</b> de arriba cambia («5 + 1» pasa a «4 + 2») y muestra «Original: 5 + 1», como hoy. La nota «Velocidad relativa…» se queda porque sin disco calibrado no hay m/s.']});
tcScene('tc-detalle','tecnica','Detalle completo · altura máxima',{entry:'vivo',q:'lost',detent:'full',t0:5.2},{
  tags:[['move','el único scroll: el detalle de consulta'],['ask','¿aceptas ese scroll?']],
  p:['A altura máxima el vídeo se queda en un <b>minirreproductor</b> a la izquierda (sigue en marcha, con su fase y su velocidad al lado) y la hoja ofrece todo lo demás: resumen completo (las 7 cifras del motor: repeticiones, tiempo bajo tensión, tempo, ROM, pérdida de velocidad, velocidad media y desvío), observaciones con «Ver», el módulo del disco, los consejos y las capas. Es consulta de detalle, no la información clave: eso ya se vio sin scroll.']});
tcScene('tc-resultado-ideal','tecnica','Resultado · medición fiable (m/s)',{entry:'vivo',q:'ideal',detent:'peek',t0:4.1,plate:'confirmed'},{
  tags:[['keep','datos reales cuando hay disco confirmado'],['new','lectura instantánea en m/s']],
  p:['Con el disco confirmado y su seguimiento ininterrumpido, la velocidad sale en <b>≈ m/s</b> (siempre con «≈», como pide la regla de honestidad) y la confianza es alta: no hay avisos ni tarjeta de calidad, solo el punto verde. La etiqueta pegada a la barra pasa de «% de la mejor» a «m/s».']});
tcScene('tc-solo-trayectoria','tecnica','Modo «solo trayectoria»',{entry:'vivo',q:'lost',detent:'peek',mode:'tray',t0:7.4},{
  tags:[['keep','capa Trayectoria de barra']],p:['Solo la estela de la barra y su punto: se apagan el esqueleto y todas las lecturas para mirar el recorrido sin ruido. La hoja se queda en su sitio con los mandos.']});
tcScene('tc-limpio','tecnica','Modo «limpio»',{entry:'vivo',q:'lost',detent:'peek',mode:'limpio',t0:7.4},{
  tags:[['new','modo limpio']],p:['El vídeo sin ninguna capa. La hoja se recoge a lo mínimo (línea de tiempo y mandos) para que el vídeo ocupe lo máximo; las bandas de repetición siguen en la línea de tiempo. Salir: tocar cualquier otro modo o el tirador.']});
tcScene('tc-horizontal','tecnica','Vídeo horizontal',{entry:'vivo',q:'lost',detent:'peek',land:true,t0:6.15},{
  tags:[['keep','mismos controles']],p:['El vídeo horizontal ocupa todo el ancho (16:9) y la hoja arranca a media altura, porque debajo del vídeo hay sitio de sobra. Las lecturas de la repetición (ROM, tiempos) van en la hoja; sobre el vídeo quedan la calidad, los modos, la fase y la velocidad.']});
tcScene('tc-calidad','tecnica','Calidad de la medición · aviso y consejos',{entry:'vivo',q:'lost',detent:'peek',t0:5.0,qopen:true,paused:true},{
  tags:[['move','antes: avisos dentro de «Métricas»'],['new','una sola tarjeta de calidad']],img:TCH2,imgCap:'Hoy: «Se perdió el seguimiento continuo del disco…» y «Para medir mejor» al final de una pestaña con scroll.',
  p:['La <b>etiqueta de confianza</b> (siempre visible arriba a la izquierda, con el punto en verde, ámbar o rojo) se abre en una tarjeta de cristal sobre el vídeo: los <b>avisos</b> con su causa (disco perdido, ROM estimado, velocidad relativa), el botón <b>Cambiar</b> para volver a marcar el disco y, abajo, <b>«Para medir mejor»</b> con los consejos del motor (hasta 3). Nada ocupa media pantalla; se cierra con ✕ o al tocar la etiqueta.',
     'El texto de «disco perdido» es el del motor, partido en título y cuerpo; «Pulsa Cambiar para volver a marcarlo» pasa a ser el propio botón.']});
tcScene('tc-aviso','tecnica','Aviso: no se pudo guardar',{entry:'vivo',q:'lost',detent:'peek',t0:3.55,toast:'No se pudo guardar el disco. Inténtalo de nuevo.'},{
  tags:[['keep','mismo texto del motor']],p:['El aviso de que una acción no se guardó («No se pudo guardar el disco…», «…el movimiento», «…pedir el análisis») pasa a una pastilla roja bajo la isla, con su cierre; hoy es un módulo más de la columna. Otro texto real: «No se pudo actualizar el análisis. Pulsa Reintentar. Ocultamos los resultados anteriores hasta actualizarlos.»']});
scene({id:'tc-marcar',tab:'tecnica',title:'Marcar el disco a mano («Cambiar»)',html:()=>tcMarkHtml({}),after:ph=>tcMarkBind(ph,{}),notes:{
  tags:[['keep','mismo diálogo y textos'],['new','tocar el fotograma coloca el círculo'],['new','lupa al arrastrar']],
  p:['Pantalla completa sobre negro: el fotograma con el disco, un círculo ámbar que se arrastra (o se coloca tocando el fotograma) y se redimensiona (pellizco; aquí, la rueda del ratón), y una <b>lupa</b> que aparece al arrastrar. Debajo, el diámetro en cm (45 · 40 · 35 · Otro) y «Guardar». Es el mismo diálogo «Marca el disco» de hoy.',
     'Al guardar se recalculan las medidas con ese disco; si no se puede guardar, el aviso de la escena anterior.']}});
scene({id:'tc-marcar-otro',tab:'tecnica',title:'Marcar el disco · «Otro» diámetro',html:()=>tcMarkHtml({other:true,dia:425}),after:ph=>tcMarkBind(ph,{}),notes:{
  tags:[['keep','diálogo real']],p:['«Otro» abre el diálogo «Diámetro del disco» (de 10 a 70 cm); fuera de rango muestra «Entre 10 y 70 cm» y desactiva «Aceptar». Prueba a escribir 5 o 90.']}});
scene({id:'tc-disco',tab:'tecnica',title:'El disco: los cuatro estados y sus avisos',html:tcDiscHtml,notes:{
  tags:[['move','vive en el detalle completo'],['keep','textos y estados reales']],
  p:['El módulo «Velocidad real con el disco» con sus tres estados reales —hallado sin confirmar («¿Es este el disco?», chips de diámetro, Confirmar / Ajustar), confirmado («Disco de 45 cm» + «Velocidad en m/s» o la razón de que no la haya + Cambiar) y sin disco («Márcalo tú»)— y los avisos de la escala, cada uno con su causa. Aquí se ve el módulo suelto; en la app es una sección de la hoja a altura máxima.']}});
tcScene('tc-menu','tecnica','Menú ⋯',{entry:'vivo',q:'lost',detent:'peek',t0:3.55,menu:true},{
  tags:[['keep','Reanalizar · Ver tendencia · Elegir el tipo de movimiento'],['move','«Cómo leer este análisis» entra en el menú']],
  p:['El menú anclado a ⋯ tiene las tres filas de hoy (cada una solo cuando aplica). El botón ⓘ suelto que hoy flota sobre el vídeo pasa al menú como «Cómo leer este análisis»: así no tapa el vídeo y la isla queda más limpia.']});
tcScene('tc-ayuda','tecnica','Cómo leer este análisis',{entry:'vivo',q:'lost',detent:'peek',t0:3.55,help:true},{
  tags:[['keep','texto real']],p:['La hoja de ayuda de hoy, con sus dos notas («Qué se puede comprobar» y «Unidades y confianza»), sobre el vídeo.']});
scene({id:'tc-elegir-movimiento',tab:'tecnica',title:'¿Qué movimiento es?',html:()=>tcMovHtml({}),after:ph=>tcMovBind(ph,{}),notes:{
  tags:[['move','antes: módulo dentro del scroll'],['keep','5 movimientos + «Ver los 24»']],
  p:['Para ejercicios propios o no reconocidos (y desde «Elegir el tipo de movimiento»): una hoja plana sobre el vídeo, con la conjetura del motor arriba y preseleccionada, otros cuatro frecuentes y «Ver los 24 movimientos». «Analizar» lanza el análisis con ese perfil.']}});
tcStill('tc-sin-analizar','Aún sin analizar',{icon:'play',title:'Este vídeo aún no está analizado',text:'Contamos las repeticiones y medimos la técnica en unos segundos.',btn:'Analizar'},{
  tags:[['keep','texto real']],p:['El vídeo sin análisis (por ejemplo, uno anterior a activar el análisis automático). El vídeo se ve completo detrás; la tarjeta de cristal dice lo que pasa y ofrece «Analizar», que abre «Analizando».']});
tcStill('tc-fallo','No se pudo analizar',{icon:'info',err:true,title:'No pudimos analizar este vídeo',text:'El móvil se quedó sin memoria al analizarlo.',btn:'Reintentar',menu:true},{
  tags:[['keep','causas reales']],p:['Causas que el motor distingue: «No encontramos el archivo del vídeo.», «El móvil se quedó sin memoria al analizarlo.», «No se pudo guardar el resultado. Comprueba el espacio libre del móvil.» y «Algo falló durante el análisis.». «Reintentar» vuelve a «Analizando».']});
tcStill('tc-sin-soporte','Ejercicio sin soporte',{icon:'info',title:'Los ejercicios de core aún no se analizan',text:'El vídeo se guarda igual, pero su técnica no se mide.',name:'Plancha frontal',sub:'13 oct'},{
  tags:[['keep','motivo real']],p:['El motivo es el texto de «no soportado» (olímpicos, isométricos, core, agarre, cuello, solo excéntricos, movimiento que no se ve de lado, caminar…). Sin botón: no hay nada que reintentar.']});
tcStill('tc-sin-informe','Informe perdido',{icon:'info',title:'No encontramos el informe de este análisis',text:'Se puede volver a calcular desde el vídeo.',btn:'Reanalizar',menu:true},{
  tags:[['keep','texto real']],p:['El análisis figura como terminado pero su informe no se puede leer: se ofrece recalcularlo.']});
scene({id:'tc-sin-video',tab:'tecnica',title:'No encontramos este vídeo',html:()=>`<div class="tc-root"><div class="tc-isl tc-glass"><div class="t"><strong>${tcName}</strong><span>${tcSetInfo('vivo')}</span></div><button class="tc-ib f" data-a="close" aria-label="Cerrar vista">${ic('close',20)}</button></div><div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;padding:0 40px;text-align:center"><div class="pl-ico" style="width:56px;height:56px">${ic('info',26)}</div><div class="pl-h2">No encontramos este vídeo</div><div style="color:var(--muted);font-size:calc(13.5px*var(--fs));line-height:1.45">Puede que se haya borrado de la galería de la serie.</div></div></div>`,notes:{
  tags:[['keep','estado vacío real']],p:['El archivo ya no está en la galería de la serie.']}});
scene({id:'tc-cargando',tab:'tecnica',title:'Cargando',html:tcLoadHtml,notes:{tags:[['keep','esqueleto']],p:['Mientras se abre el vídeo y se lee el informe: el esqueleto reserva el sitio del vídeo y de la hoja para que la pantalla no salte cuando llegan los datos.']}});
tcScene('tc-sin-repeticiones','tecnica','Sin repeticiones detectadas',{entry:'vivo',q:'lost',detent:'peek',t0:4.0,noreps:'No encontramos repeticiones en el vídeo',paused:true},{
  tags:[['keep','motivo real'],['move','Reanalizar a la vista']],p:['El análisis terminó pero no contó repeticiones: la hoja dice el motivo en lugar de cifras (nunca ceros de relleno) y, por la confianza, el consejo general «Graba de lado, con el cuerpo entero a la vista y a 2–4 m». «Reanalizar» (hoy solo en ⋯) sube a la hoja porque es lo que la persona querrá hacer. Motivos reales: «No encontramos repeticiones en el vídeo», «El vídeo es demasiado corto para contar repeticiones», «No vimos a nadie en el vídeo», «No pudimos calcular la escala del cuerpo», «El movimiento fue demasiado pequeño para contarlo», «El encuadre o el desplazamiento durante la serie impidieron contarla».']});
tcScene('tc-corto','tecnica','Vídeo demasiado corto',{entry:'vivo',q:'lost',detent:'peek',t0:4.0,noreps:'El vídeo es demasiado corto para contar repeticiones',paused:true},{
  tags:[['keep','motivo real']],p:['Mismo estado con el motivo «demasiado corto».']});
tcScene('tc-sin-persona','tecnica','No se ve a nadie (vídeo oscuro o mal encuadrado)',{entry:'vivo',q:'lost',detent:'peek',t0:4.0,noreps:'No vimos a nadie en el vídeo',paused:true},{
  tags:[['ask','el motor no distingue «oscuro»']],p:['El motor no mide la luz: si el vídeo es demasiado oscuro o la persona queda fuera, el motivo que da es «No vimos a nadie en el vídeo». No he inventado un texto de «vídeo oscuro»: habría que añadir esa detección al motor.']});
