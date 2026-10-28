/* ================= PIEZAS COMUNES DEL LIENZO «REGISTROS V1» ================= */
// Iconos de línea que faltaban (misma retícula 24 y trazo 1,75 que KpknLineIcons).
Object.assign(P,{
 trophy:'<path d="M8 4h8v5a4 4 0 01-8 0z"/><path d="M8 6H5a2.5 2.5 0 003 4M16 6h3a2.5 2.5 0 01-3 4M12 13v4M8.5 20h7M10 17h4"/>',
 camera:'<rect x="3.5" y="7" width="17" height="12.5" rx="3.5"/><path d="M9 7l1.5-2.5h3L15 7"/><circle cx="12" cy="13" r="3.5"/>',
 barcode:'<path d="M4 6v12M7 6v12M10.5 6v12M13 6v12M16.5 6v12M20 6v12"/>',star:'<path d="M12 4l2.4 5 5.4.6-4 3.7 1.1 5.4L12 16l-4.9 2.7 1.1-5.4-4-3.7 5.4-.6z"/>',
 starOn:'<path d="M12 4l2.4 5 5.4.6-4 3.7 1.1 5.4L12 16l-4.9 2.7 1.1-5.4-4-3.7 5.4-.6z" fill="currentColor"/>',
 mic:'<rect x="9" y="3.5" width="6" height="11" rx="3"/><path d="M5.5 11.5a6.5 6.5 0 0013 0M12 18v2.5"/>',
 egg:'<path d="M12 3.5c3.6 0 6 5.4 6 9.3a6 6 0 01-12 0c0-3.9 2.4-9.3 6-9.3z"/><circle cx="12" cy="13.5" r="2.4"/>',
 bread:'<path d="M5 11a4 4 0 012-7.2h10A4 4 0 0119 11v8.5H5z"/><path d="M9 11v3M15 11v3"/>',
 avo:'<path d="M12 3.5c3 0 5.5 6 5.5 10a5.5 5.5 0 01-11 0c0-4 2.5-10 5.5-10z"/><circle cx="12" cy="14.5" r="2.6"/>',
 cup:'<path d="M5 9h11v5a5 5 0 01-5 5h-1a5 5 0 01-5-5z"/><path d="M16 10.5h1.5a2.5 2.5 0 010 5H16M8 3.5c0 1.5 1 1.5 1 3M11.5 3.5c0 1.5 1 1.5 1 3"/>',
 drop:'<path d="M12 4s5.5 6.1 5.5 9.9a5.5 5.5 0 01-11 0C6.5 10.1 12 4 12 4z"/>',
 pan:'<circle cx="10" cy="13" r="6.5"/><path d="M16.5 13h5"/>',raw:'<path d="M6 6l12 12M6 18c0-6.6 5.4-12 12-12"/>',
 confetti:'<path d="M5 20l3.5-10.5L16 17z"/><path d="M14 4v2M19 9h2M17.5 6.5l1.5-1.5M12 8.5l1-1"/>',
 smile:'<circle cx="12" cy="12" r="8.5"/><path d="M8.5 14a4.5 4.5 0 007 0M9 9.5v.01M15 9.5v.01"/>',
 meh:'<circle cx="12" cy="12" r="8.5"/><path d="M8.5 14.5h7M9 9.5v.01M15 9.5v.01"/>',
 sad:'<circle cx="12" cy="12" r="8.5"/><path d="M8.5 15.5a4.5 4.5 0 017 0M9 9.5v.01M15 9.5v.01"/>',
 wow:'<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="15" r="1.8"/><path d="M8.5 9.5l1.5 1M15.5 9.5l-1.5 1"/>',
 fire:'<path d="M12 20.5a6 6 0 006-6c0-4-3.5-5.5-3.5-10C11 6 8 8.5 8 12c-1.5-.5-2-2-2-2-.5 1.3-.5 2.4-.5 3.7a6.5 6.5 0 006.5 6.8z"/>',
 ruler:'<rect x="3" y="8" width="18" height="8" rx="2"/><path d="M7 8v3M11 8v4M15 8v3M19 8v3"/>',
 qmark:'<circle cx="12" cy="12" r="8.5"/><path d="M9.6 9.5a2.5 2.5 0 114 2c-1 .6-1.6 1.1-1.6 2.3M12 16.5v.01"/>',
 pin:'<path d="M12 21s6-5.6 6-10.5a6 6 0 00-12 0C6 15.4 12 21 12 21z"/><circle cx="12" cy="10.5" r="2.2"/>',
 users:'<circle cx="9" cy="9" r="3.2"/><path d="M3.5 19a5.5 5.5 0 0111 0M16 6.2a3 3 0 010 5.6M18 19a5 5 0 00-2.5-4.3"/>',
});
const radio=(on)=>`<span style="width:20px;height:20px;border-radius:50%;border:2px solid ${on?'var(--ink)':'#ffffff40'};display:inline-flex;align-items:center;justify-content:center;flex-shrink:0">${on?'<i style="width:9px;height:9px;border-radius:50%;background:var(--ink)"></i>':''}</span>`;
const opt=(label,sub,on)=>`<button class="mi" style="min-height:48px">${radio(on)}<span class="tx"><strong style="font-weight:${on?600:500}">${label}</strong>${sub?`<span>${sub}</span>`:''}</span></button>`;
const chips=(arr,on,h=34)=>`<div style="display:flex;gap:6px;flex-wrap:wrap">${arr.map((x,i)=>`<span class="chip ${(Array.isArray(on)?on.includes(i):i===on)?'on':''}" style="height:${h}px">${x}</span>`).join('')}</div>`;
const tg=(k,t)=>`<span class="tag ${k}">${t}</span>`;
const navbar=(on=0)=>`<div class="navbar glass">${[['home','Inicio'],['train','Entreno'],['bowl','Nutrición'],['scale','Cuerpo']].map((n,i)=>`<div class="${i===on?'on':''}">${ic(n[0],22)}<span>${n[1]}</span></div>`).join('')}</div>`;
const medal=(m,size=44,txt='')=>{const d=MEDAL[m]||MEDAL.none;return `<span class="medal ${m==='none'?'none':''}" style="--ms:${size}px;--mc:${d.c};--mc2:${d.c2}">${txt||(m==='oro'?'1':m==='plata'?'2':m==='bronce'?'3':ic('trophy',Math.round(size*.5)))}</span>`};
// Estallido de líneas finas (la celebración en el lenguaje de las portadas): rayos que se dibujan desde el centro.
function burst(n=28,acc='#f7cf73'){let s='';const r=rng(11);for(let i=0;i<n;i++){const a=i/n*Math.PI*2,r0=70+r()*20,r1=150+r()*95;const x0=260+Math.cos(a)*r0,y0=260+Math.sin(a)*r0,x1=260+Math.cos(a)*r1,y1=260+Math.sin(a)*r1;
  s+=`<path class="ln" pathLength="1" d="M${x0.toFixed(1)} ${y0.toFixed(1)}L${x1.toFixed(1)} ${y1.toFixed(1)}" stroke="${i%5===0?acc:'#f2eee6'}" stroke-width="${i%5===0?2.2:1.1}" stroke-linecap="round" opacity="${i%5===0?1:.55}" style="animation-delay:${(i%7)*40}ms"/>`}
  for(let k=0;k<3;k++)s+=`<circle class="ln" pathLength="1" cx="260" cy="260" r="${58+k*26}" fill="none" stroke="#f2eee6" stroke-opacity="${.18-k*.04}" style="animation-delay:${200+k*120}ms"/>`;
  return `<svg class="burst draw" viewBox="0 0 520 520" aria-hidden="true">${s}</svg>`}
function confetti(n=26){const r=rng(5);const cs=['#f7cf73','#f49a6e','#f2eee6','#8fb2ff','#43d18c'];let s='';for(let i=0;i<n;i++)s+=`<i style="left:${(r()*100).toFixed(1)}%;background:${cs[i%5]};animation-delay:${Math.round(r()*900)}ms;width:${4+Math.round(r()*6)}px;height:${8+Math.round(r()*10)}px;border-radius:${i%3?2:6}px"></i>`;return `<div class="confetti">${s}</div>`}
// Anillo de calorías con los tres macros como arcos.
function ringSvg(k,target,parts=[[.32,'var(--mus)'],[.43,'var(--ene)'],[.25,'var(--col)']]){const R=36,C=2*Math.PI*R;let off=0,s=`<circle cx="43" cy="43" r="${R}" fill="none" stroke="#ffffff10" stroke-width="7"/>`;const fill=Math.min(1,k/target);
  parts.forEach(p=>{const len=C*fill*p[0];s+=`<circle cx="43" cy="43" r="${R}" fill="none" stroke="${p[1]}" stroke-width="7" stroke-dasharray="${len-3} ${C}" stroke-dashoffset="${-off}" transform="rotate(-90 43 43)" stroke-linecap="round"/>`;off+=len});
  return `<div class="ring"><svg viewBox="0 0 86 86" width="86" height="86" aria-hidden="true">${s}</svg><b><span data-count="${k}">0</span><small>kcal</small></b></div>`}
// Historias (mismo marco aprobado que las del programa).
function storyShell({art='',top='38%',artBottom='42%',ey='',title='',sub='',inner='',foot='KPKN',w,photo=false,end=false,extra=''}){
  return `<div class="story" style="${w?`--sw:${w}`:''}">${photo?'<div class="photo"></div>':''}<div class="bgart draw ${photo?'over':''}" style="inset:0 0 ${artBottom} 0">${art}</div>${extra}
  <div class="st-in ${end?'end':''}" style="top:${top}">${ey?`<div class="st-ey">${ey}</div>`:''}${title?`<div class="st-name">${title}</div>`:''}${sub?`<div class="st-meta">${sub}</div>`:''}${inner}
  <div class="st-foot">KPKN<span>${foot}</span></div></div></div>`}
const stat=(v,l,u='')=>`<div><b>${v}${u?`<small>${u}</small>`:''}</b><span>${l}</span></div>`;
const fullStory=(fn,o)=>`<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:#000">${fn({w:'100%',...o})}</div>`;
const drawAfter=ph=>animateIn(ph);
const sparkPath=(v,w,h)=>{const mn=Math.min(...v),mx=Math.max(...v),r=mx-mn||1;return v.map((x,i)=>(i?'L':'M')+(2+i*(w-4)/(v.length-1)).toFixed(1)+' '+(h-3-(x-mn)/r*(h-6)).toFixed(1)).join('')};
