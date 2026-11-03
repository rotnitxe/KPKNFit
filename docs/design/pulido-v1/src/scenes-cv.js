/* ===== Pulido v1 · CARDIO EN VIVO (prefijo cv · pestaña cardio-vivo) =====
   Rediseño total del espacio de cardio de la sesión en vivo. Una sola máquina de pantallas (CVS) que sirve a todas las escenas:
   cualquier escena es interactiva y continúa por el flujo real: descanso → «Ahora vamos con cardio» → antes de empezar → 3-2-1 →
   en marcha (Ahora · Plan · Datos, pausa, bloqueo) → cardio terminado (RPE real) → «Ahora vamos con fuerza».
   Datos de demostración. Nada se guarda. */
const cvReduced=()=>{try{return matchMedia('(prefers-reduced-motion: reduce)').matches}catch(e){return false}};
Object.assign(P,{
 cvlock:'<rect x="5.5" y="10.5" width="13" height="9.5" rx="2.5"/><path d="M8.5 10.5V8a3.5 3.5 0 017 0v2.5"/>',
 cvskip:'<path d="M6 5.5l9 6.5-9 6.5zM18.5 5.5v13"/>',
 cvpause:'<path d="M9 5.5v13M15 5.5v13" stroke-width="3.2"/>',
 cvplay:'<path d="M8 5.5v13l10.5-6.5z" fill="currentColor" stroke="none"/>',
 cvrun:'<circle cx="14.5" cy="4.5" r="1.8"/><path d="M8 21l3-6 3 2 1 4M6 12l3-4 4 1 2 3 3 1M11 15l2-6"/>',
 cvstairs:'<path d="M4 19h4v-4h4v-4h4V7h4"/>',
 cvspeed:'<path d="M4.5 16a7.5 7.5 0 1115 0"/><path d="M12 16l3.5-4"/>',
 cvincline:'<path d="M4 18h16V9z"/>',
 cvroute:'<circle cx="6" cy="18" r="2"/><circle cx="18" cy="6" r="2"/><path d="M8 18h6a3 3 0 000-6h-4a3 3 0 010-6h6"/>',
 cvsun:'<circle cx="12" cy="12" r="3.6"/><path d="M12 3.5v2M12 18.5v2M3.5 12h2M18.5 12h2M6 6l1.4 1.4M16.6 16.6L18 18M18 6l-1.4 1.4M7.4 16.6L6 18"/>',
 cvpulse:'<path d="M3 12h4l2-5 3 10 2.5-7 1.5 2H21"/>',
 cvheart:'<path d="M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0112 7.4 4.3 4.3 0 0119.5 10c0 5.4-7.5 10-7.5 10z" fill="currentColor" stroke="none"/>',
 cvheartl:'<path d="M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0112 7.4 4.3 4.3 0 0119.5 10c0 5.4-7.5 10-7.5 10z"/>',
 cvarrow:'<path d="M5 12h14M13 6l6 6-6 6"/>',
 cvchev2:'<path d="M7 6l6 6-6 6M13 6l6 6-6 6"/>',
 cvdown:'<path d="M6 9l6 6 6-6"/>',cvup:'<path d="M6 15l6-6 6 6"/>',
 cvdumb:'<path d="M3 9.5v5M6 7v10M18 7v10M21 9.5v5M6 12h12"/>',
 cvtimer:'<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 1.5M9.5 2.5h5"/>',
 cvmore:'<circle cx="5" cy="12" r="1.5" fill="currentColor"/><circle cx="12" cy="12" r="1.5" fill="currentColor"/><circle cx="19" cy="12" r="1.5" fill="currentColor"/>',
 cvmic:'<rect x="9" y="3.5" width="6" height="11" rx="3"/><path d="M5.5 11.5a6.5 6.5 0 0013 0M12 18v2.5"/>',
 cvcheck:'<path d="M5 12.5l4.5 4.5L19 7.5"/>',
 cvedit:'<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
 cvplus:'<path d="M12 5v14M5 12h14"/>',
 cvinfo:'<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8v.01"/>',
 cvwarn:'<path d="M12 4l9 15.5H3z"/><path d="M12 10v4.5M12 17.5v.01"/>',
 cvhist:'<path d="M4 12a8 8 0 102.5-5.8"/><path d="M4 4.5v4h4M12 8v4l3 2"/>',
 cvclose:'<path d="M6 6l12 12M18 6L6 18"/>',
});
const CVZ={1:'#8AA3B3',2:'#87C9A5',3:'#CDBB8F',4:'#F49A6E',5:'#E9736C'};
const CVN=['Recuperación','Base aeróbica','Tempo','Umbral','Máximo'];
const CVH={1:.34,2:.5,3:.66,4:.86,5:1};
const CVMAX=190;
const dc=(n,d=1)=>n.toFixed(d).replace('.',',');
const ck=s=>{s=Math.max(0,Math.round(s));return s>=3600?`${Math.floor(s/3600)}:${String(Math.floor(s%3600/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`:`${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`};
const cvCompact=()=>state.size[1]<800;
const zoneOf=h=>{const p=h*100/CVMAX;return p<60?1:p<70?2:p<80?3:p<90?4:5};
const zrange=z=>[Math.round(CVMAX*(40+z*10)/100),Math.round(CVMAX*(50+z*10)/100)];
const needleOf=h=>{const p=h*100/CVMAX,zi=zoneOf(h)-1,low=50+zi*10,w=Math.min(1,Math.max(0,(p-low)/10));return Math.min(99,Math.max(1,(zi+w)*20))};
const rpeAnchor=n=>n<=2?'Muy suave':n<=4?'Suave':n<=6?'Algo duro':n<=8?'Duro':n===9?'Muy duro':'Máximo';

/* ---------- planes de demostración (todo sale de CardioDetails real: tipo, bloques, consigna, zona) ---------- */
const nor=(()=>{const a=[{k:'Calentamiento',d:600,z:2,v:'7,5 km/h',sp:7.5}];for(let n=1;n<=4;n++){a.push({k:'Trabajo',n,d:240,z:4,v:'12,0 km/h',sp:12,inc:1});if(n<4)a.push({k:'Recuperación',n,d:180,z:2,v:'7,0 km/h',sp:7});}a.push({k:'Vuelta a la calma',d:300,z:1,v:'6,0 km/h',sp:6});return a})();
const CVP={
 nor:{name:'Cinta · Noruego 4×4',ico:'cvrun',kind:'cinta',isl:['Pierna · Semana 1','4 ejercicios · 9 de 10 series'],steps:nor,mixed:true,rpe:8,
   firstLead:'Terminaste la fuerza: 3 ejercicios y 10 series.'},
 esc:{name:'Escaladora',ico:'cvstairs',kind:'escaladora',isl:['Legs II · Semana 1','8 ejercicios · 0 de 18 series'],steps:[{k:'Continuo',d:1200,z:3,v:''}],mixed:false,rpe:6},
 gps:{name:'Carrera suave',ico:'cvrun',kind:'exterior',gps:true,target:5,isl:['Carrera · Semana 3','1 ejercicio · cardio'],steps:[{k:'Continuo',d:1800,z:2,v:'6:10 /km'}],mixed:false,rpe:5},
};
CVP.nor.total=CVP.nor.steps.reduce((a,s)=>a+s.d,0);CVP.esc.total=1200;CVP.gps.total=1800;
const startOf=(pl,i)=>CVP[pl].steps.slice(0,i).reduce((a,s)=>a+s.d,0);
const estKm=(pl,el)=>{let km=0,acc=0;CVP[pl].steps.forEach(s=>{const inB=Math.max(0,Math.min(s.d,el-acc));if(s.sp)km+=s.sp*inB/3600;acc+=s.d});return km};
const stepName=(s)=>s.k+(s.n?' '+s.n:'');

/* ---------- ondas: la técnica de las portadas de programa (crestas apiladas) pero vivas ---------- */
const CVW={ // amp = altura de las crestas · spd = velocidad · freq = frecuencia · jag = aspereza · dim = intensidad del trazo
 idle:{amp:.2,spd:0,freq:1,jag:0,dim:.8,acc:.7},
 warm:{amp:.5,spd:.55,freq:1,jag:0,dim:1,acc:1},
 cont:{amp:.72,spd:.85,freq:1.05,jag:.03,dim:1,acc:1},
 work:{amp:.98,spd:1.15,freq:1.15,jag:.07,dim:1,acc:1},
 recover:{amp:.52,spd:.58,freq:.9,jag:0,dim:1,acc:1},
 cool:{amp:.3,spd:.36,freq:.85,jag:0,dim:.95,acc:1},
 above:{amp:1.2,spd:1.65,freq:1.4,jag:.5,dim:1,acc:1},
 below:{amp:.5,spd:.65,freq:.95,jag:0,dim:1,acc:1},
 pause:{amp:.32,spd:0,freq:1,jag:0,dim:.42,acc:0},
 done:{amp:.26,spd:.22,freq:.8,jag:0,dim:.9,acc:1},
};
function cvWaveD(W,H,p,t,hr){
  const n=p.n,out=[],beat=hr?Math.exp(-(((t*hr/60)%1))*6):0,ph=t*p.spd*2.2;
  const top=H*.36,span=H*.6;
  for(let k=0;k<n;k++){
    const base=top+k*span/(n-1),depth=Math.pow(k/(n-1),1.1);
    const bump=Math.min(H,330)*.5*p.amp*(.22+.78*depth)*(1+.14*beat),sig=W*(.2+.1*depth)*(.85+.25*Math.min(1.4,p.amp)),cx=W/2+(k-n/2)*5;
    let d='',pts=[];
    for(let x=0;x<=W;x+=5){
      const env=Math.exp(-Math.pow((x-cx)/sig,2));
      const w1=Math.sin(x*.05*p.freq+k*.42-ph),w2=Math.sin(x*.021*p.freq-k*.31+ph*.7+1.3),w3=Math.sin(x*.19*p.freq+k*1.1-ph*2.6);
      const shape=.5+.5*(.62*w1+.38*w2);
      const ripple=Math.sin(x*.03+k*.7-ph*.5)*1.4*(.4+p.amp);
      const y=base-env*bump*(shape+p.jag*.55*w3)-ripple;
      pts.push(x.toFixed(0)+' '+y.toFixed(1));
    }
    out.push('M'+pts.join('L'));
  }
  return out;
}
function cvWaveMount(host){
  if(host._cv)return;host._cv=1;
  const W=360,H=+host.dataset.h||200,n=+host.dataset.lines||18,reduced=cvReduced();
  let svg='<svg viewBox="0 0 '+W+' '+H+'" preserveAspectRatio="none" aria-hidden="true">';
  for(let k=0;k<n;k++)svg+='<path class="f" d="" fill="#000"/><path class="l" d="" fill="none" stroke-linecap="round" stroke-linejoin="round"/>';
  svg+='<path class="g" d="" fill="none" stroke-linecap="round" stroke-width="7" opacity=".22"/><path class="a" d="" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  host.innerHTML=svg;
  const fs=host.querySelectorAll('.f'),ls=host.querySelectorAll('.l'),g=host.querySelector('.g'),a=host.querySelector('.a');
  const accK=Math.round(n*.64);
  const cur={...CVW[host.dataset.w||'work']};let t0=null,last=0,tAcc=reduced?2.4:0;
  const intro=document.documentElement.classList.contains('solo')?0:(+host.dataset.intro||0);
  const draw=()=>{
    const p={...cur,n};const hr=+host.dataset.hr||0;
    const ds=cvWaveD(W,H,p,tAcc,hr);
    const zc=getComputedStyle(host).getPropertyValue('--zc').trim()||'#f49a6e';
    ds.forEach((d,k)=>{
      const op=(.08+.42*Math.pow(k/(n-1),1.15))*cur.dim;
      fs[k].setAttribute('d',d+'L'+W+' '+(H+4)+'L0 '+(H+4)+'Z');
      ls[k].setAttribute('d',d);ls[k].setAttribute('stroke','#f2eee6');ls[k].setAttribute('stroke-width',(.8+.5*k/(n-1)).toFixed(2));ls[k].setAttribute('opacity',op.toFixed(2));
    });
    const d=ds[accK];a.setAttribute('d',d);g.setAttribute('d',d);
    a.setAttribute('stroke',zc);g.setAttribute('stroke',zc);a.setAttribute('stroke-width','2.2');a.setAttribute('opacity',cur.acc.toFixed(2));g.style.opacity=(.26*cur.acc).toFixed(2);
    fs[accK].setAttribute('d',d+'L'+W+' '+(H+4)+'L0 '+(H+4)+'Z');
  };
  host._draw=draw;host._cur=cur;
  const frame=(ts)=>{
    if(!host.isConnected)return;
    if(t0==null){t0=ts;last=ts}
    const dt=Math.min(.05,(ts-last)/1000);last=ts;
    const tg=CVW[host.dataset.w||'work'],k=1-Math.exp(-dt*3.2);
    ['amp','spd','freq','jag','dim','acc'].forEach(key=>cur[key]+=(tg[key]-cur[key])*k);
    const ramp=intro?Math.min(1,(ts-t0)/1000/intro):1;const e=1-Math.pow(1-ramp,3);
    const ampKeep=cur.amp;cur.amp=ampKeep*e;
    tAcc+=dt*(cur.spd>.01?1:0);
    draw();cur.amp=ampKeep;
    requestAnimationFrame(frame);
  };
  if(reduced){Object.assign(cur,CVW[host.dataset.w||'work']);draw();}
  else{Object.assign(cur,{...CVW[host.dataset.w||'work'],amp:intro?0:CVW[host.dataset.w||'work'].amp});draw();requestAnimationFrame(frame)}
}
function cvWaveSet(root,state_,hr){root.querySelectorAll('.cv-wave').forEach(h=>{h.dataset.w=state_;if(hr!=null)h.dataset.hr=hr;if(h._draw&&cvReduced()&&h._cur){Object.assign(h._cur,CVW[state_]);h._draw()}})}
function cvMount(root){root.querySelectorAll('.cv-wave').forEach(cvWaveMount)}
const waveHost=(w,h,o={})=>`<div class="cv-wave ${o.full?'full':''}" data-w="${w}" data-h="${h}" data-lines="${o.lines||18}" ${o.hr?`data-hr="${o.hr}"`:''} ${o.intro?`data-intro="${o.intro}"`:''}></div>`;

/* ---------- utilidades de interacción ---------- */
const cvRipple=(btn,e)=>{const r=btn.getBoundingClientRect(),zoom=r.width/(btn.offsetWidth||r.width)||1,s=Math.max(r.width,r.height)*1.2;const sp=document.createElement('span');sp.className='cv-rip';sp.style.cssText=`width:${s/zoom}px;height:${s/zoom}px;left:${((e.clientX-r.left)/zoom)-s/zoom/2}px;top:${((e.clientY-r.top)/zoom)-s/zoom/2}px`;btn.appendChild(sp);setTimeout(()=>sp.remove(),600)};
function cvPress(root){root.addEventListener('pointerdown',e=>{const b=e.target.closest('button,.cv-act');if(b&&!b.disabled&&root.contains(b))cvRipple(b,e)})}
let cvSid=0;
const cvEvery=(root,fn,ms)=>{const sid=root.dataset.sid;const id=setInterval(()=>{if(!root.isConnected||root.dataset.sid!==sid){clearInterval(id);return}fn()},ms);return id};
const cvLater=(root,fn,ms)=>{const sid=root.dataset.sid;setTimeout(()=>{if(root.isConnected&&root.dataset.sid===sid)fn()},ms)};
/* El mando «Desliza» (empezar / continuar): arrastrar el botón hasta el final. */
function cvSwipe(root,onDone){
  const sw=root.querySelector('.cv-sw');if(!sw)return;const kn=sw.querySelector('.kn'),fill=sw.querySelector('.fill');
  let x0=null,dx=0,max=0,done=false;
  const set=v=>{dx=Math.max(0,Math.min(max,v));kn.style.left=(5+dx)+'px';fill.style.width=(62+dx)+'px';const l=sw.querySelector('.slb');if(l)l.style.opacity=1-dx/max*1.3};
  const finish=()=>{if(done)return;done=true;sw.classList.add('done');set(max);setTimeout(onDone,320)};
  kn.addEventListener('pointerdown',e=>{max=sw.clientWidth-60;x0=e.clientX-dx;kn.setPointerCapture(e.pointerId);e.preventDefault()});
  kn.addEventListener('pointermove',e=>{if(x0==null||done)return;const z=sw.getBoundingClientRect().width/sw.offsetWidth||1;set((e.clientX-x0)/z)});
  const up=()=>{if(x0==null||done)return;x0=null;if(dx>=max*.88)finish();else{sw.classList.add('done');set(0);setTimeout(()=>sw.classList.remove('done'),320)}};
  kn.addEventListener('pointerup',up);kn.addEventListener('pointercancel',up);
  kn.setAttribute('tabindex','0');kn.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){max=sw.clientWidth-60;finish()}});
  sw._finish=()=>{max=sw.clientWidth-60;finish()};
}
const swipeHTML=(label)=>`<div class="cv-sw" aria-label="${label}"><div class="fill"></div><div class="slb">${label}</div><span class="chv">${ic('cvchev2',18)}</span><div class="kn" role="slider" aria-label="${label}" aria-valuenow="0">${ic('cvarrow',22)}</div></div>`;

/* ---------- piezas comunes ---------- */
const cvIsland=(t,s,time)=>`<div class="cv-isl pl-glass"><div class="t"><strong>${t}</strong><small>${s}</small></div><span class="cv-tm">${ic('cvtimer',15)}<span data-sclock>${time}</span></span><button class="cv-ib" aria-label="Menú de la sesión">${ic('cvmore',18)}</button></div>`;
const cvSessClock=(root,start)=>{let s=start;const el=root.querySelector('[data-sclock]');if(!el)return;cvEvery(root,()=>{s++;const e=root.querySelector('[data-sclock]');if(e)e.textContent=ck(s)},1000)};
const cvProf=(steps,cur,prog,o={})=>{const tot=steps.reduce((a,s)=>a+s.d,0);const pos=steps.slice(0,Math.max(cur,0)).reduce((a,s)=>a+s.d,0)+(cur>=0?steps[cur].d*prog:0);
  return `<div class="cv-prof${steps.length===1?' one':''}" aria-label="Perfil del plan">${steps.map((s,i)=>`<i class="${i<cur?'d':i===cur?'n':''}" style="flex:${s.d};height:${CVH[s.z]*100}%;--c:${CVZ[s.z]};--d:${.03*i}s"></i>`).join('')}${cur>=0?`<b class="ps" data-ps style="left:${pos/tot*100}%"></b>`:''}</div>`};

/* ================= estado y valores derivados del cardio en marcha ================= */
function cvState(o={}){
  const S={plan:'nor',step:5,left:222,hr:158,hr0:158,hrSrc:'watch',hrAge:0,page:0,paused:false,locked:false,ready:false,gps:'ok',km:2.62,pace:312,
    pausedSec:42,drop:null,inZ:132,zs:[78,640,230,510,0],pctZ:82,sets:null,sheet:false,...o};
  if(S.hr!=null&&o.hr0==null)S.hr0=S.hr;
  return S;
}
function cvVals(S){
  const Pn=CVP[S.plan],st=Pn.steps[S.step],elapsed=startOf(S.plan,S.step)+st.d-S.left,multi=Pn.steps.length>1,D={st,multi,Pn,elapsed};
  D.rounds=Pn.steps.filter(s=>s.k==='Trabajo').length;
  D.byKm=!!(Pn.gps&&Pn.target);
  D.time=D.byKm?dc(Math.max(0,Pn.target-S.km),2):ck(S.left);
  D.rem=ck(Pn.total-elapsed);D.act=ck(elapsed);
  D.bar=D.byKm?Math.min(100,S.km/Pn.target*100):(1-S.left/st.d)*100;
  D.pos=elapsed/Pn.total*100;
  D.km=Pn.gps?S.km:(Pn.steps[0].sp?estKm(S.plan,elapsed):null);
  D.kmTxt=D.km==null?'—':(Pn.gps?dc(D.km,1):'≈ '+dc(D.km,1));
  D.kcalTxt='≈ '+Math.round(elapsed/60*(S.plan==='esc'?9.4:S.plan==='nor'?9:7.6));
  D.liveZ=S.hr!=null?zoneOf(S.hr):null;
  D.rel=D.liveZ==null?null:D.liveZ===st.z?'in':D.liveZ>st.z?'above':'below';
  D.zc=CVZ[D.liveZ||st.z];
  D.hrTxt=S.hr==null?'—':String(S.hr);
  D.paceTxt=Pn.gps?ck(S.pace):'—';
  D.wave=S.paused?'pause':D.rel==='above'?'above':D.rel==='below'?'below':({'Calentamiento':'warm','Trabajo':'work','Recuperación':'recover','Vuelta a la calma':'cool','Continuo':st.z>=4?'work':'cont'})[st.k];
  D.pausedTxt=ck(S.pausedSec);D.ageTxt=ck(S.hrAge);
  return D;
}
function cvSub(S,D){
  const st=D.st,Pn=D.Pn,n=Pn.steps.length;
  const txt=D.multi?`<b>${st.k}${st.n&&D.rounds>1?` · Vuelta ${st.n} de ${D.rounds}`:''}</b><span style="color:var(--faint)">·</span><span>Paso ${S.step+1} de ${n}</span>`
    :`<b>${st.k}</b><span style="color:var(--faint)">·</span><span>${Pn.gps?`${dc(Pn.target,0)} km al aire libre`:`${Math.round(Pn.total/60)} min`}</span>`;
  return `<span class="cv-dot"></span>${txt}`;
}
function cvHero(S,D,o={}){
  const st=D.st,Pn=D.Pn,chips=[];const noted=S.gps==='lost'||S.gps==='denied';const hh=D.multi?(cvCompact()?150:176):(cvCompact()?(noted?128:176):232);
  if(st.v)chips.push(`<span>${ic('cvspeed',16)}${Pn.gps?'Objetivo '+st.v:st.v}</span>`);
  if(st.inc!=null)chips.push(`<span>${ic('cvincline',16)}${st.inc} %</span>`);
  if(Pn.gps)chips.push(`<span style="${S.gps!=='ok'?'color:var(--ene);border-color:#f7cf7355':''}">${ic('cvroute',16)}${S.gps==='ok'?'GPS activo':S.gps==='lost'?'Sin señal GPS':S.gps==='paused'?'GPS en pausa':'GPS'}</span>`);
  const sub=D.byKm?`km restantes · de ${dc(Pn.target,2)} km`:D.multi?`restante del paso · quedan <b data-b="rem">${D.rem}</b>`:'tiempo restante';
  return `<div class="cv-hero" style="--hh:${hh}px"><div class="cv-halo"></div>${waveHost(D.wave,o.h||hh,{hr:S.hr&&!S.paused?S.hr:0,lines:cvCompact()?15:18,intro:o.intro})}
    <div class="cv-hin"><div class="cv-tsub">${sub}</div><div class="cv-time ${D.time.length>=6?'xs':D.time.length>=5?'sm':''}" data-b="time">${D.time}</div>${chips.length?`<div class="cv-cue">${chips.join('')}</div>`:''}</div>
    <div class="cv-bar"><i data-bar style="width:${D.bar}%"></i></div></div>`;
}
function cvPulse(S,D){
  const st=D.st,z=st.z,r=Math.max(2,CVP[S.plan].rpe);
  if(S.hr==null){
    const lo=Math.max(1,r-1);
    return `<div class="cv-mod" data-mod="pulse"><div class="cv-row"><div class="cv-hrb"><span class="cv-ey">Pulso</span><span class="cv-hr">${ic('cvheartl',20,'off')}<b class="dim">—</b><span>sin pulso</span></span></div>
      <div class="cv-tgt"><span class="cv-ey">Guía por esfuerzo</span><strong>RPE ${lo}–${r} · ${rpeAnchor(r)}</strong><small>Lo planificado es una guía</small></div></div>
      <div class="cv-tape"><div class="bars">${[1,2,3,4,5,6,7,8,9,10].map(i=>`<i class="${i>=lo&&i<=r?'t':''}" style="background:var(--ink)"></i>`).join('')}</div><div class="lb">${[1,2,3,4,5,6,7,8,9,10].map(i=>`<span class="${i>=lo&&i<=r?'t':''}">${i}</span>`).join('')}</div></div>
      <div class="cv-st mute" style="margin-top:9px">${ic('cvinfo',16)}<span>Sin datos de pulso</span><button class="cv-act" data-act="addhr" style="margin-left:auto">${ic('cvplus',15)}Añadir pulso</button></div></div>`;
  }
  const [lo,hi]=zrange(z),rel=D.rel,wc=CVZ[D.liveZ];
  const man=S.hrSrc==='manual';
  let status;
  if(rel==='above')status=`<div class="cv-st w">${ic('cvdown',17)}<span>Baja el ritmo</span><small>${S.hr} lpm · sobre Z${z}</small></div>`;
  else if(rel==='below')status=`<div class="cv-st w">${ic('cvup',17)}<span>Sube el ritmo</span><small>${S.hr} lpm · bajo Z${z}</small></div>`;
  else if(st.k==='Recuperación')status=`<div class="cv-st">${ic('cvcheck',17)}<span>Recuperando bien</span><small>${S.drop!=null?'−'+S.drop+' lpm en 1 min':'en zona '+z}</small></div>`;
  else if(man)status=`<div class="cv-st">${ic('cvcheck',17)}<span>En tu zona</span><small>anotado hace <span data-b="ageTxt">${ck(S.hrAge)}</span></small></div>`;
  else status=`<div class="cv-st">${ic('cvcheck',17)}<span>En tu zona</span><small><span data-b="inz">${ck(S.inZ)}</span> en Z${z}</small></div>`;
  const left=man?`<button class="cv-hrb" data-act="addhr" aria-label="Actualizar pulso"><span class="cv-ey">Pulso · manual</span><span class="cv-hr">${ic('cvheart',20)}<b data-b="hrTxt">${S.hr}</b><span>lpm</span><span class="ed">${ic('cvedit',13)}</span></span></button>`
    :`<div class="cv-hrb"><span class="cv-ey">Pulso · reloj</span><span class="cv-hr">${ic('cvheart',20)}<b data-b="hrTxt">${S.hr}</b><span>lpm</span></span></div>`;
  return `<div class="cv-mod ${rel==='above'||rel==='below'?'warn':''}" data-mod="pulse" style="--wc:${wc}"><div class="cv-row">${left}
    <div class="cv-tgt"><span class="cv-ey">Objetivo · Z${z}</span><strong>${CVN[z-1]}</strong><small>${lo}–${hi} lpm</small></div></div>
    <div class="cv-tape"><div class="bars">${[1,2,3,4,5].map(i=>`<i class="${i===z?'t':''}" style="background:${CVZ[i]}"></i>`).join('')}</div><b class="nd" data-nd style="left:${needleOf(S.hr)}%"></b><div class="lb">${[1,2,3,4,5].map(i=>`<span class="${i===z?'t':''}">Z${i}</span>`).join('')}</div></div>${status}</div>`;
}
function cvMetrics(S,D){
  const cells=[];
  if(D.Pn.gps){cells.push(['Ritmo',`<span data-b="paceTxt">${D.paceTxt}</span><em>/km</em>`]);cells.push(['Activo',`<span data-b="act">${D.act}</span>`]);}
  else if(D.multi){cells.push(['Distancia',`<span data-b="kmTxt">${D.kmTxt}</span><em>km</em>`]);cells.push(['Energía',`<span data-b="kcalTxt">${D.kcalTxt}</span><em>kcal</em>`]);}
  else{cells.push(['Activo',`<span data-b="act">${D.act}</span>`]);cells.push(['Energía',`<span data-b="kcalTxt">${D.kcalTxt}</span><em>kcal</em>`]);}
  return `<div class="cv-mod cv-met" style="--mc:${cells.length}">${cells.map(c=>`<div><small>${c[0]}</small><b>${c[1]}</b></div>`).join('')}</div>`;
}
function cvPlanMod(S,D){
  const Pn=D.Pn,next=Pn.steps[S.step+1];
  return `<div class="cv-mod"><div class="cv-plh"><span class="cv-ey">${D.multi?'Plan del cardio':'Progreso'}</span><b><span data-b="act">${D.act}</span> <i>/ ${ck(Pn.total)}</i></b></div>${cvProf(Pn.steps,S.step,D.bar/100,{})}
    ${next?`<div class="cv-next"><small>Siguiente</small><i class="cv-chev-d" style="--c:${CVZ[next.z]}"></i><em>${stepName(next)}</em><span>${ck(next.d)}${next.v?' · '+next.v:''}</span></div>`:(D.multi?`<div class="cv-next"><small>Último paso</small><em style="font-weight:400;color:var(--muted)">después guardas el cardio</em></div>`:'')}</div>`;
}
function cvNote(S){
  if(S.gps==='lost')return `<div class="cv-note">${ic('cvwarn',18)}<div class="tx"><b>Sin señal GPS · seguimos con el tiempo</b></div></div>`;
  if(S.gps==='denied')return `<div class="cv-note">${ic('cvroute',18)}<div class="tx"><b>Ubicación no disponible</b></div><button data-act="retrygps">Reintentar permiso</button></div>`;
  return '';
}
function cvPage0(S,D,o={}){
  const compact=cvCompact();
  return cvNote(S)+cvHero(S,D,o)+cvPulse(S,D)+((!compact||!D.multi)?cvMetrics(S,D):'')+(D.multi?cvPlanMod(S,D):'');
}
function cvPage1(S,D){
  const Pn=D.Pn,n=Pn.steps.length;
  return `<div class="cv-ph"><b>Plan</b><span>${n} ${n===1?'paso':'pasos'} · ${Math.round(Pn.total/60)} min</span></div>${cvProf(Pn.steps,S.ready?-1:S.step,D.bar/100,{})}
   <div class="cv-pl">${Pn.steps.map((s,i)=>{const c=S.ready?'':i<S.step?'d':i===S.step?'n':'';return `<div class="cv-r ${c}" style="--c:${CVZ[s.z]};${c==='n'?`--p:${D.bar}%`:''}"><span class="rd"><i>${c==='d'?ic('cvcheck',12):''}</i></span><div class="nm">${stepName(s)}<small>Z${s.z} · ${CVN[s.z-1]}${s.v?' · '+s.v:''}${s.inc!=null?' · '+s.inc+' %':''}</small></div><span class="tt">${ck(s.d)}</span></div>`}).join('')}</div>`;
}
function cvPage2(S,D){
  const Pn=D.Pn,hasHr=S.hr!=null;
  const cells=[['Tiempo activo',`<span data-b="act">${D.act}</span>`,'de '+ck(Pn.total)],
    ['Distancia',`<span data-b="kmTxt">${D.kmTxt}</span>`,Pn.gps?'km · GPS':D.km!=null?'km · cinta (estimada)':'km'],
    ['FC media',hasHr?String(S.hr0-12):'—',hasHr?(S.hrSrc==='manual'?'lpm · manual':'lpm'):'sin pulso'],
    ['Energía',`<span data-b="kcalTxt">${D.kcalTxt}</span>`,'kcal estimadas']];
  const tot=S.zs.reduce((a,b)=>a+b,0)||1;
  const zones=hasHr?`<div class="cv-ey" style="margin-top:18px">Tiempo por zona</div><div class="cv-tz">${[5,4,3,2,1].map((z,i)=>`<div class="z"><b>Z${z}</b><div><i style="width:${S.zs[z-1]/tot*100}%;--c:${CVZ[z]};animation-delay:${.1+i*.06}s"></i></div><span>${ck(S.zs[z-1])}</span></div>`).join('')}</div>
    <div class="cv-st" style="margin-top:12px">${tot>1?ic('cvcheck',17)+`<span>${S.pctZ} % del trabajo en tu zona objetivo</span>`:ic('cvinfo',17)+'<span style="font-weight:500;color:var(--muted)">Aún no hay tiempo medido en zona.</span>'}</div>`
    :`<div class="cv-ey" style="margin-top:18px">Tiempo por zona</div><div class="cv-empty"><p><b style="color:var(--ink);font-weight:600">Sin pulso no hay tiempo en zona.</b> Añade tu pulso a mano para medir cuánto del trabajo cae en tu zona objetivo.</p><button class="cv-act" data-act="addhr">${ic('cvplus',15)}Añadir pulso</button></div>`;
  return `<div class="cv-ph"><b>Datos</b><span>medidos hasta ahora</span></div><div class="cv-cells">${cells.map(c=>`<div class="cv-cell"><small>${c[0]}</small><b>${c[1]}</b><span>${c[2]}</span></div>`).join('')}</div>${zones}`;
}
function cvCtl(S,D){
  if(S.locked)return `<div class="cv-lockrow"><div class="cv-hold" data-hold role="button" aria-label="Mantén para desbloquear"><svg class="ring" viewBox="0 0 70 70"><circle cx="35" cy="35" r="32" fill="none" stroke="#ffffff1a" stroke-width="3"/><circle data-arc cx="35" cy="35" r="32" fill="none" stroke="#f2eee6" stroke-width="3" stroke-linecap="round" stroke-dasharray="0 201" transform="rotate(-90 35 35)"/></svg>${ic('cvlock',24)}</div><p><b>Mantén para desbloquear</b>La pantalla ignora los toques mientras entrenas.</p></div>`;
  const main=S.ready?['start','Empezar','cvplay']:S.paused?['resume','Reanudar','cvplay']:['pause','Pausa','cvpause'];
  const skip=D.multi&&!S.ready;
  return `<div class="cv-ctl"><button class="cv-c" data-act="lock">${ic('cvlock',20)}Bloquear</button><button class="cv-c main" data-act="${main[0]}">${ic(main[2],22)}${main[1]}</button>${skip?`<button class="cv-c" data-act="skip">${ic('cvskip',20)}Saltar paso</button>`:''}</div>`;
}
function cvDockHTML(S,D){
  return `<div class="cv-dock pl-glass" data-dock><div class="cv-tabs" role="tablist" aria-label="Páginas del cardio">${['Ahora','Plan','Datos'].map((n,i)=>`<button role="tab" data-tab="${i}" aria-selected="${i===S.page}" class="${i===S.page?'on':''}">${n}</button>`).join('')}<span class="cv-ind"></span></div><div data-ctlwrap>${cvCtl(S,D)}</div></div>`;
}

/* ================= pantallas del flujo ================= */
const CVS={};
const cvRoot=(name,o={})=>`<div class="cv-root ${o.paused?'paused':''} ${o.locked?'locked':''}" ${cvCompact()?'data-compact':''} data-s="${name}">${CVS[name].html(o)}</div>`;
function cvSwap(root,api,name,o){
  root.classList.add('cv-out');
  const sid=String(++cvSid);root.dataset.sid=sid;
  setTimeout(()=>{
    if(!root.isConnected)return;
    root._h=null;root.classList.remove('paused','locked');
    if(o.paused)root.classList.add('paused');if(o.locked)root.classList.add('locked');
    root.innerHTML=CVS[name].html(o);root.dataset.s=name;root._o=o;
    root.classList.remove('cv-out');root.classList.add('cv-in');
    CVS[name].mount(root,api,o);
    setTimeout(()=>root.classList.remove('cv-in'),420);
  },230);
}
function cvBoot(phone,name,o={}){
  const root=phone.querySelector('.cv-root');if(!root)return;
  if(!root._bound){
    root._bound=1;cvPress(root);
    root.addEventListener('click',e=>{if(root._noclick){e.stopPropagation();e.preventDefault();root._noclick=false;return}root._h&&root._h(e)});
  }
  const api={go(n,o2={}){cvSwap(root,api,n,{...(root._o||{}),...o2})}};
  root._api=api;root._o=o;root.dataset.sid=String(++cvSid);
  CVS[name].mount(root,api,o);
}
const cvToast=(scr,text)=>{const t=document.createElement('div');t.className='cv-toast pl-glass';t.textContent=text;scr.appendChild(t);setTimeout(()=>t.classList.add('out'),1700);setTimeout(()=>t.remove(),2100)};
const cvIslandFor=(plan)=>{const Pn=CVP[plan];return cvIsland(Pn.isl[0],Pn.isl[1],'48:02')};

/* ---------- en marcha: Ahora · Plan · Datos + dock ---------- */
CVS.live={
  html(o){
    const S=cvState(o),D=cvVals(S),Pn=D.Pn;
    const sets=S.sets?`<div class="cv-sets">${S.sets.map((s,i)=>`<button class="${i===S.setSel?'on':''} ${s.ok?'ok':''}">Serie ${i+1}${s.ok?' · Registrada':''}</button>`).join('')}</div>`:'';
    return `<div class="cv-scr" data-s="live" style="--zc:${D.zc};--beat:${S.hr?(60/S.hr).toFixed(2):2.4}s;--pg:${S.page}">
      <div class="cv-bg"></div>${cvIsland(Pn.isl[0],Pn.isl[1],S.sclock||'48:02')}
      <div class="cv-col"><header class="cv-head"><div class="cv-ttl"><h1>${Pn.name}</h1><button class="cv-ib" aria-label="Modo Voz">${ic('cvmic',20)}</button></div><div class="cv-sub" data-sub>${cvSub(S,D)}</div>${sets}</header>
        <div class="cv-pages"><div class="cv-track">
          <section class="cv-pg ${S.page===0?'on':''}" data-pg="0">${cvPage0(S,D,{intro:o.intro})}</section>
          <section class="cv-pg ${S.page===1?'on':''}" data-pg="1">${cvPage1(S,D)}</section>
          <section class="cv-pg ${S.page===2?'on':''}" data-pg="2">${cvPage2(S,D)}</section>
        </div></div></div>
      <div class="cv-lockcap pl-glass">${ic('cvlock',14)}Pantalla bloqueada</div>
      <div class="cv-veil"><span class="cv-ey">Cardio en pausa</span><div class="big" data-b="pausedTxt">${D.pausedTxt}</div><p data-vp>El tiempo en pausa no cuenta como activo. El paso actual queda en ${ck(S.left)}.</p><button class="cv-btn" data-act="resume">${ic('cvplay',18)}Reanudar</button><button class="cv-btn alt" data-act="finish">Terminar cardio</button></div>
      ${cvDockHTML(S,D)}</div>`;
  },
  mount(root,api,o){
    const S=cvState(o);let D=cvVals(S);const Pn=D.Pn,scr=root.querySelector('.cv-scr'),q=s=>root.querySelector(s),last=Pn.steps.length-1;
    cvMount(root);cvWaveSet(root,D.wave,S.hr&&!S.paused?S.hr:0);
    const bind=()=>{
      D=cvVals(S);
      root.querySelectorAll('[data-b]').forEach(el=>{const k=el.dataset.b;let v=D[k];if(k==='inz')v=ck(S.inZ);if(v!=null&&el.textContent!==String(v))el.textContent=v});
      root.querySelectorAll('[data-bar]').forEach(e=>e.style.width=D.bar+'%');
      root.querySelectorAll('[data-ps]').forEach(e=>e.style.left=D.pos+'%');
      const nd=q('[data-nd]');if(nd&&S.hr!=null)nd.style.left=needleOf(S.hr)+'%';
      const rn=q('.cv-r.n');if(rn)rn.style.setProperty('--p',D.bar+'%');
      const vp=q('[data-vp]');if(vp)vp.textContent='El tiempo en pausa no cuenta como activo. El paso actual queda en '+ck(S.left)+'.';
    };
    const refresh=()=>{
      D=cvVals(S);
      scr.style.setProperty('--zc',D.zc);scr.style.setProperty('--beat',S.hr?(60/S.hr).toFixed(2)+'s':'2.4s');
      q('[data-sub]').innerHTML=cvSub(S,D);
      const p0=q('[data-pg="0"]'),keep=p0.querySelector('.cv-hero .cv-wave');
      p0.innerHTML=cvPage0(S,D);const nw=p0.querySelector('.cv-hero .cv-wave');if(keep&&nw)nw.replaceWith(keep);
      p0.querySelectorAll('.cv-prof i').forEach(i=>i.style.animation='none');
      q('[data-pg="1"]').innerHTML=cvPage1(S,D);q('[data-pg="2"]').innerHTML=cvPage2(S,D);
      cvMount(root);cvWaveSet(root,D.wave,S.hr&&!S.paused?S.hr:0);
      const t=q('.cv-time');if(t){t.classList.remove('flip');void t.offsetWidth;t.classList.add('flip')}
    };
    const ctl=()=>{q('[data-ctlwrap]').innerHTML=cvCtl(S,D);bindHold()};
    const setPaused=b=>{S.paused=b;if(b)S.pausedSec=0;root.classList.toggle('paused',b);D=cvVals(S);ctl();cvWaveSet(root,D.wave,b?0:(S.hr||0));bind()};
    const setLocked=b=>{S.locked=b;root.classList.toggle('locked',b);ctl()};
    function bindHold(){
      const h=q('[data-hold]');if(!h)return;const arc=h.querySelector('[data-arc]'),C=2*Math.PI*32;let t0=null,raf=0;
      const step=()=>{if(t0==null)return;const p=Math.min(1,(performance.now()-t0)/1200);arc.setAttribute('stroke-dasharray',(p*C)+' '+C);if(p>=1){t0=null;setLocked(false);return}raf=requestAnimationFrame(step)};
      h.addEventListener('pointerdown',e=>{t0=performance.now();try{h.setPointerCapture(e.pointerId)}catch(_){}raf=requestAnimationFrame(step)});
      const end=()=>{t0=null;cancelAnimationFrame(raf);arc.setAttribute('stroke-dasharray','0 '+C)};
      h.addEventListener('pointerup',end);h.addEventListener('pointercancel',end);
      h._complete=()=>setLocked(false);
    }
    const hrBase={1:100,2:128,3:142,4:158,5:176};
    const toStep=i=>{S.step=i;S.left=Pn.steps[i].d;S.inZ=0;S.drop=Pn.steps[i].k==='Recuperación'?27:null;if(S.hrSrc==='watch'&&S.hr!=null){S.hr0=hrBase[Pn.steps[i].z]||S.hr0;S.hr=S.hr0}refresh();cvToast(scr,`Paso ${i+1} de ${Pn.steps.length} · ${stepName(Pn.steps[i])}`)};
    const finish=()=>api.go('finish',{plan:S.plan,hr:S.hr,hr0:S.hr0,hrSrc:S.hrSrc,rpeSel:null,saving:false,failed:false,page:0,paused:false,locked:false,ready:false,step:S.step});
    const skip=()=>{if(S.step>=last){finish();return}toStep(S.step+1)};
    // hoja del pulso manual
    const openSheet=()=>{
      if(q('.cv-sheetwrap'))return;
      const w=document.createElement('div');w.className='cv-sheetwrap';
      const upd=S.hrSrc==='manual';
      w.innerHTML=`<div class="pl-scrim cv-scrim"></div><div class="pl-sheet cv-sheet"><div class="cv-sheet-in"><span class="cv-ey">Pulso manual</span><h3>${upd?'Actualizar pulso':'Añadir pulso'}</h3><p>Míralo en la pantalla de la máquina o cuenta tus latidos 15 s en la muñeca y multiplícalos por 4.</p><div class="cv-fld"><label for="cv-hr-in">Pulso (lpm)</label><input id="cv-hr-in" inputmode="numeric" maxlength="3" placeholder="140" value="${o.sheetText||''}" autocomplete="off"></div><div class="cv-hint" data-hint>&nbsp;</div><button class="cv-btn" data-save ${o.sheetText?'':'disabled'}>Guardar pulso</button></div></div>`;
      scr.appendChild(w);
      const inp=w.querySelector('input'),hint=w.querySelector('[data-hint]'),save=w.querySelector('[data-save]'),fld=w.querySelector('.cv-fld');let tried=false;
      const val=()=>{const t=inp.value.trim(),n=+t;return t&&/^\d+$/.test(t)&&n>=40&&n<=230?n:null};
      const msg=()=>{const t=inp.value.trim();if(tried){if(!t)return['Escribe tu pulso en lpm.',1];if(!/^\d+$/.test(t))return['Escribe solo números (lpm).',1];if(val()==null)return['El pulso tiene que estar entre 40 y 230 lpm.',1]}const v=val();if(v!=null){const z=zoneOf(v);return[`Con ${v} lpm estás en zona ${z} · ${CVN[z-1]}.`,0]}return[' ',0]};
      const paint=()=>{const m=msg();hint.textContent=m[0];hint.classList.toggle('bad',!!m[1]);fld.classList.toggle('bad',!!m[1]);save.disabled=!inp.value.trim()};
      inp.addEventListener('input',()=>{inp.value=inp.value.replace(/\D/g,'').slice(0,3);tried=false;paint()});
      const close=()=>{w.classList.add('out');setTimeout(()=>w.remove(),260)};
      w.querySelector('.cv-scrim').addEventListener('click',close);
      save.addEventListener('click',()=>{tried=true;const v=val();if(v==null){paint();return}S.hr=v;S.hr0=v;S.hrSrc='manual';S.hrAge=0;close();refresh();cvToast(scr,'Pulso guardado · '+v+' lpm')});
      paint();if(!o.sheet)setTimeout(()=>{try{inp.focus()}catch(_){}} ,300);
    };
    // páginas
    const setPage=i=>{
      if(i===S.page)return;
      if(S.ready&&i===0){api.go('ready',{ready:true,page:0});return}
      S.page=i;scr.style.setProperty('--pg',i);
      root.querySelectorAll('.cv-tabs button').forEach((b,k)=>{b.classList.toggle('on',k===i);b.setAttribute('aria-selected',k===i)});
      root.querySelectorAll('.cv-pg').forEach((p,k)=>{p.classList.toggle('on',k===i);p.classList.remove('cv-enter');if(k===i){void p.offsetWidth;p.classList.add('cv-enter');p.scrollTop=0}});
    };
    cvPager(root,S,setPage,()=>S.locked);
    bindHold();
    root._h=e=>{
      const b=e.target.closest('[data-act],[data-tab]');if(!b)return;
      if(b.dataset.tab!=null){setPage(+b.dataset.tab);return}
      const a=b.dataset.act;
      if(a==='pause')setPaused(true);else if(a==='resume')setPaused(false);
      else if(a==='lock')setLocked(true);else if(a==='skip')skip();
      else if(a==='start')api.go('count',{plan:S.plan,ready:false});
      else if(a==='finish')finish();else if(a==='addhr')openSheet();
      else if(a==='retrygps'){S.gps='ok';refresh();cvToast(scr,'GPS activo')}
    };
    if(o.sheet)openSheet();
    // reloj
    cvSessClock(root,48*60+2);
    let t=0;
    if(!S.ready)cvEvery(root,()=>{
      t++;
      if(S.paused){S.pausedSec++;bind();return}
      if(S.locked&&false)return;
      S.left=Math.max(0,S.left-1);
      if(Pn.gps&&S.gps==='ok'){S.km+=.0027;S.pace=312+Math.round(Math.sin(t/2.5)*4)}
      if(S.hr!=null&&S.hrSrc==='watch')S.hr=S.hr0+Math.round(Math.sin(t/3)*2);
      if(S.hrSrc==='manual')S.hrAge++;
      if(D.rel==='in')S.inZ++;
      if(S.left<=0){if(S.step<last)toStep(S.step+1);else{finish();return}}
      bind();
    },1000);
    if(o.autoEnd)cvLater(root,finish,o.autoEnd);
  }
};
/* El pager: arrastrar con el dedo sigue al dedo; al soltar, desliza a la página vecina con fundido. Vale en el contenido y en las pestañas del dock. */
function cvPager(root,S,setPage,isLocked){
  const pages=root.querySelector('.cv-pages'),track=root.querySelector('.cv-track'),tabs=root.querySelector('.cv-tabs');
  let x0=null,dx=0,drag=false,pid=null,host=null;
  const z=()=>pages.getBoundingClientRect().width/pages.offsetWidth||1;
  const down=e=>{if(isLocked()||e.target.closest('.cv-act,.cv-note button,input'))return;x0=e.clientX;dx=0;drag=false;host=e.currentTarget;pid=e.pointerId};
  const move=e=>{
    if(x0==null)return;dx=(e.clientX-x0)/z();
    if(!drag&&Math.abs(dx)>10){drag=true;track.classList.add('drag');try{host.setPointerCapture(pid)}catch(_){}}
    if(drag){let off=-S.page*pages.offsetWidth+dx;const min=-2*pages.offsetWidth;if(off>0)off*=.35;if(off<min)off=min+(off-min)*.35;track.style.transform=`translateX(${off}px)`}
  };
  const up=()=>{
    if(x0==null)return;const was=drag;x0=null;
    if(was){track.classList.remove('drag');track.style.transform='';root._noclick=true;setTimeout(()=>{root._noclick=false},60);
      let to=S.page;if(dx<-50)to=Math.min(2,S.page+1);else if(dx>50)to=Math.max(0,S.page-1);setPage(to)}
    drag=false;
  };
  [pages,tabs].forEach(el=>{el.addEventListener('pointerdown',down);el.addEventListener('pointermove',move);el.addEventListener('pointerup',up);el.addEventListener('pointercancel',up)});
}

/* ---------- antes de empezar (solo cardio, o tras «Ver el plan») ---------- */
CVS.ready={
  html(o){
    const S=cvState({...o,ready:true,step:0,left:CVP[o.plan||'nor'].steps[0].d,hr:null}),D=cvVals(S),Pn=D.Pn,first=Pn.steps[0];
    const hasWatch=!!o.watch;
    const place=Pn.gps?(o.gps==='denied'?'Exterior · falta permiso de ubicación':'Exterior · GPS'):'Interior · sin GPS';
    const scr=`<div class="cv-scr" data-s="ready" style="--zc:${CVZ[first.z]};--pg:0"><div class="cv-bg"></div>${cvIsland(Pn.isl[0],Pn.isl[1],'0:31')}
      <div class="cv-col"><header class="cv-head"><div class="cv-ttl"><h1>${Pn.name}</h1><button class="cv-ib" aria-label="Modo Voz">${ic('cvmic',20)}</button></div><div class="cv-sub"><span class="cv-dot"></span><b>Antes de empezar</b><span style="color:var(--faint)">·</span><span>${Pn.steps.length} ${Pn.steps.length===1?'paso':'pasos'}</span></div></header>
      <div class="cv-pages"><div class="cv-pg on" style="width:100%;padding-bottom:calc(var(--dock) + var(--dockb) - 34px)">
        <div class="cv-hero" style="--hh:${cvCompact()?138:292}px"><div class="cv-halo" style="animation-duration:2.6s"></div>${waveHost('idle',cvCompact()?138:292,{lines:cvCompact()?14:22})}
          <div class="cv-hin"><div class="cv-time ${ck(Pn.total).length>=6?'xs':ck(Pn.total).length>=5?'sm':''}">${ck(Pn.total)}</div><div class="cv-tsub">${Pn.gps?`${dc(Pn.target,0)} km · <b>empieza en Z${first.z}</b>`:`duración · <b>empieza en Z${first.z}</b>`}</div></div></div>
        <div class="cv-mod" style="padding-top:6px;padding-bottom:6px">
          <div class="cv-chk"><span class="ci">${ic('cvpulse',17)}</span><div class="tx">Pulso<small>${hasWatch?'Reloj · listo':'Sin reloj · guía por esfuerzo'}</small></div><span class="st ${hasWatch?'':'mute'}">${hasWatch?ic('cvcheck',14)+'Listo':'RPE'}</span></div>
          <div class="cv-chk"><span class="ci">${ic('cvroute',17)}</span><div class="tx">Lugar<small>${place}</small></div><span class="st ${o.gps==='denied'?'warn':''}">${o.gps==='denied'?ic('cvwarn',14)+'Revisar':ic('cvcheck',14)+'Listo'}</span></div>
          <div class="cv-chk"><span class="ci">${ic('cvsun',17)}</span><div class="tx">Pantalla<small>Siempre encendida</small></div><span class="st">${ic('cvcheck',14)}Listo</span></div></div>
        ${first.v?`<div class="cv-mod" style="display:flex;align-items:center;gap:12px;padding-top:11px;padding-bottom:11px"><span class="ci" style="width:34px;height:34px;border-radius:50%;background:#ffffff0c;display:flex;align-items:center;justify-content:center;color:var(--zc)">${ic('cvspeed',17)}</span><div style="flex:1;font:500 calc(13*var(--u)) Inter">Primer bloque: <b style="font-weight:600">ajusta a ${first.v}</b></div></div>`:''}
        ${Pn.steps.length>1?`<div class="cv-mod" style="padding-top:12px;padding-bottom:10px"><div class="cv-plh"><span class="cv-ey">Plan del cardio</span><button class="cv-link t" data-act="plan">Ver el plan</button></div>${cvProf(Pn.steps,-1,0,{})}</div>`:`<div class="cv-ready-link"><button class="cv-link" data-act="plan">Ver el plan</button></div>`}
      </div></div></div>
      <div class="cv-startdock pl-glass"><button class="cv-btn" data-act="start">${ic('cvplay',20)}Empezar</button><small>Empieza con una cuenta atrás de 3 segundos</small></div></div>`;
    return scr;
  },
  mount(root,api,o){
    cvMount(root);
    root._h=e=>{const b=e.target.closest('[data-act]');if(!b)return;const a=b.dataset.act;
      if(a==='start')api.go('count',{plan:o.plan||'nor',ready:false});
      else if(a==='plan')api.go('live',{plan:o.plan||'nor',ready:true,step:0,left:CVP[o.plan||'nor'].steps[0].d,hr:null,page:1,paused:false,locked:false,gps:'ok',km:0});
    };
    cvSessClock(root,31);
    if(o.auto)cvLater(root,()=>api.go('count',{plan:o.plan||'nor',ready:false,auto:true}),1700);
  }
};

/* ---------- cuenta 3-2-1 ---------- */
CVS.count={
  html(o){
    const Pn=CVP[o.plan||'nor'],f=Pn.steps[0];
    return `<div class="cv-scr" data-s="count" style="--zc:${CVZ[f.z]}"><div class="cv-bg"></div>${cvIsland(Pn.isl[0],Pn.isl[1],'0:31')}
      <div class="cv-hero" style="position:absolute;left:0;right:0;top:140px;height:560px;margin:0"><div class="cv-halo" style="top:50%"></div>${waveHost('warm',560,{lines:24,intro:1.2,full:true})}</div>
      <div class="cv-cnt" role="status"><span class="cv-ey">Empieza en</span><div data-cn><div class="n">${o.start||3}</div></div><p>${f.k} · ${ck(f.d)}${f.v?' · '+f.v:''}</p></div></div>`;
  },
  mount(root,api,o){
    cvMount(root);let n=o.start||3;const cn=root.querySelector('[data-cn]');
    const tick=()=>{
      n--;
      if(n<=0){if(o.loop){n=3}else{api.go('live',{plan:o.plan||'nor',step:0,left:CVP[o.plan||'nor'].steps[0].d,hr:o.watch?118:null,hr0:118,hrSrc:'watch',page:0,paused:false,locked:false,ready:false,gps:'ok',km:0,inZ:0,zs:[0,0,0,0,0],sclock:'0:34',intro:1.4,auto:o.auto,autoEnd:o.auto?7000:0});return}}
      cn.innerHTML=`<div class="n">${n}</div><span class="rg"></span>`;
    };
    cn.insertAdjacentHTML('beforeend','<span class="rg"></span>');
    cvEvery(root,tick,1000);
    root._h=e=>{if(e.target.closest('.cv-cnt')&&!o.loop){n=1;tick()}};
  }
};

/* ---------- descanso que anuncia cardio (pieza existente) ---------- */
CVS.rest={
  html(o){
    const Pn=CVP.nor,R=176,rr=R/2-10,c=2*Math.PI*rr,left=o.left??92;
    return `<div class="cv-scr" data-s="rest" style="--zc:${CVZ[2]}">${cvIsland('Pierna · Semana 1','4 ejercicios · 9 de 10 series','47:12')}
      <div class="cv-col" style="top:104px"><header class="cv-head"><div class="cv-ttl"><h1 style="font-size:calc(22*var(--u))">Sentadilla Trasera con Barra Alta</h1><button class="cv-ib">${ic('cvmic',20)}</button></div></header></div>
      <div class="cv-rest" style="top:${cvCompact()?168:196}px"><div class="cv-rcard"><div style="display:flex;justify-content:space-between"><span class="cv-ey">Descanso</span><span style="color:var(--faint)">${ic('down',16)}</span></div>
        <div class="cv-rring"><svg viewBox="0 0 ${R} ${R}"><circle cx="${R/2}" cy="${R/2}" r="${rr}" fill="none" stroke="#ffffff0d" stroke-width="10"/><circle data-rarc cx="${R/2}" cy="${R/2}" r="${rr}" fill="none" stroke="#f2eee6" stroke-width="3" stroke-linecap="round" stroke-dasharray="${left/150*c} ${c}" transform="rotate(-90 ${R/2} ${R/2})" style="transition:stroke-dasharray .9s linear"/></svg><div><b data-rt>${ck(left)}</b><span>Descansando</span></div></div>
        <div class="cv-rch"><span>−15 s</span><span>${ic('cvpause',13)}</span><span>+15 s</span></div>
        <div class="cv-rnx"><span class="cv-ey">Siguiente</span><div class="r"><b>Cardio · ${Pn.name}</b><em>40 min</em></div><div class="nt">${ic('cvrun',14)}Al terminar el descanso pasas al espacio de cardio</div></div></div>
        <button class="cv-btn" data-act="endrest">${ic('cvplay',16)}Terminar descanso</button></div></div>`;
  },
  mount(root,api,o){
    let left=o.left??92;const rt=root.querySelector('[data-rt]'),arc=root.querySelector('[data-rarc]'),c=2*Math.PI*(176/2-10);
    const go=()=>api.go('transIn',{plan:'nor',auto:o.auto});
    cvEvery(root,()=>{left=Math.max(0,left-(o.auto?9:1));rt.textContent=ck(left);arc.setAttribute('stroke-dasharray',(left/150*c)+' '+c);if(left<=0&&o.auto)go()},o.auto?200:1000);
    cvSessClock(root,47*60+12);
    root._h=e=>{if(e.target.closest('[data-act=endrest]'))go()};
  }
};

/* ---------- «Ahora vamos con cardio» ---------- */
CVS.transIn={
  html(o){
    const Pn=CVP[o.plan||'nor'],f=Pn.steps[0],tot=Pn.total,km=Pn.steps.reduce((a,s)=>a+(s.sp||0)*s.d/3600,0);
    return `<div class="cv-scr" data-s="transIn" style="--zc:${CVZ[f.z]}"><div class="cv-bg"></div>
      <div class="cv-wave full" style="position:absolute;inset:0;-webkit-mask-image:linear-gradient(#0000 14%,#000 50%,#000 86%,#0000);mask-image:linear-gradient(#0000 14%,#000 50%,#000 86%,#0000)" data-w="work" data-h="${state.size[1]}" data-lines="30" data-intro="2.2"></div>
      ${cvIsland(Pn.isl[0],'Fuerza terminada · sigue cardio','48:02')}
      <div class="cv-tr"><div class="cv-route"><span class="nd">${ic('cvdumb',20)}</span><span class="ln"><i></i></span><span class="nd go">${ic(Pn.ico,20)}</span></div>
        <h1 class="cv-big" aria-label="Ahora vamos con cardio"><span>Ahora vamos</span><span>con cardio</span></h1>
        <p class="cv-lead">${Pn.firstLead||''}</p>
        <div class="cv-tc"><div class="cv-tch"><span class="cvi">${ic(Pn.ico,22)}</span><div><strong>${Pn.name}</strong><small>Cardio · ${ck(tot)} · ${Pn.steps.length} pasos</small></div></div>
          <div class="cv-first"><span>Primer bloque: <b style="font-weight:600">${f.k} ${ck(f.d)}</b></span><span><i class="cv-dot"></i>Zona ${f.z}</span></div>
          <div style="font:400 calc(12*var(--u)) Inter;color:var(--muted);margin-top:8px">Ajusta a ${f.v}</div>
          ${cvProf(Pn.steps,-1,0,{})}<div class="tot">Total ${ck(tot)} · ≈ ${dc(km,1)} km</div></div>
        <div style="display:flex;justify-content:center;margin:-6px 0 12px"><button class="cv-link" data-act="plan">Ver el plan</button></div>
        ${swipeHTML('Desliza para empezar')}</div></div>`;
  },
  mount(root,api,o){
    cvMount(root);cvSessClock(root,48*60+2);
    const go=()=>api.go('count',{plan:o.plan||'nor',auto:o.auto});
    cvSwipe(root,go);
    root._h=e=>{if(e.target.closest('[data-act=plan]'))api.go('live',{plan:o.plan||'nor',ready:true,step:0,left:CVP[o.plan||'nor'].steps[0].d,hr:null,page:1,paused:false,locked:false,km:0})};
    if(o.auto)cvLater(root,()=>root.querySelector('.cv-sw')._finish(),2900);
  }
};

/* ---------- cardio terminado: lo medido + RPE real obligatorio ---------- */
CVS.finish={
  html(o){
    const plan=o.plan||'nor',Pn=CVP[plan];
    const cells=plan==='nor'?[['Tiempo activo','40:00','pausa 0:42 aparte',0],['Distancia','≈ 6,0','km · cinta (estimada)',1],['FC media','146','lpm · máx 171',0],['Energía','≈ 372','kcal estimadas',0]]
      :plan==='gps'?[['Tiempo activo','28:44','de 30:00',0],['Distancia','4,6','km · GPS',0],['FC media','—','toca para añadir',2],['Energía','≈ 295','kcal estimadas',0]]
      :[['Tiempo activo','20:00','de 20:00',0],['Distancia','—','toca para añadir',2],['FC media','—','toca para añadir',2],['Energía','≈ 188','kcal estimadas',0]];
    const planned=Pn.rpe;
    const steps=Pn.steps;
    const per=plan==='nor'?[100,94,100,96,100,92,98,78,100]:null; // % dentro del objetivo por paso (solo con pulso)
    const cmp=per?steps.map((s,i)=>{const p=per[i];return `<i style="flex:${s.d};--c:${CVZ[s.z]};opacity:${p>=85?1:p>=60?.7:.45};--d:${i*.04}s">${p<85?p+' %':''}</i>`}).join(''):steps.map((s,i)=>`<i style="flex:${s.d};--c:${CVZ[s.z]};opacity:.25;--d:${i*.04}s"></i>`).join('');
    const cmpTxt=per?'8 de 9 pasos dentro del objetivo · Trabajo 4, al 78 %':'Sin pulso: no hay cumplimiento medido por paso.';
    const sel=o.rpeSel;
    return `<div class="cv-scr" data-s="finish" style="--zc:${CVZ[2]}"><div class="cv-bg"></div>${cvIsland(Pn.isl[0],'Cardio terminado',plan==='gps'?'30:12':'48:02')}
      <div class="cv-fin"><div class="cv-finb">
        <div class="cv-finwave">${waveHost('done',86,{lines:9,intro:1.6})}</div>
        <span class="cv-ey">${Pn.name}</span><h2>Cardio terminado</h2>
        <div class="cv-cells" style="margin-top:14px">${cells.map(c=>`<${c[3]===2?'button':'div'} class="cv-cell ${c[3]===2?'btn':''}" ${c[3]===2?'data-act="edit"':''}><small>${c[0]}</small><b>${c[1]}</b><span>${c[3]===2?ic('cvedit',12):''}${c[2]}</span></${c[3]===2?'button':'div'}>`).join('')}</div>
        <div class="cv-ey" style="margin-top:18px">Cumplimiento por paso</div><div class="cv-cmp">${cmp}</div><div style="margin-top:6px;font:400 calc(11.5*var(--u))/1.4 Inter;color:var(--faint)">${cmpTxt}</div>
        <div class="cv-rl"><span class="cv-ey">¿Cuánto te costó?</span><b data-rl class="${sel?'on':''}">${sel?`RPE ${sel} · ${rpeAnchor(sel)}`:'Sin marcar'}</b></div>
        <div class="cv-rpe" role="radiogroup" aria-label="Esfuerzo real">${[1,2,3,4,5,6,7,8,9,10].map(n=>`<button role="radio" aria-checked="${sel===n}" data-rpe="${n}" class="${sel===n?'on':''} ${n===planned&&sel!==n?'p':''}">${n}</button>`).join('')}</div>
        <div style="margin-top:8px;font:400 calc(11.5*var(--u)) Inter;color:var(--faint)">Esfuerzo real. El plan (${planned}) es una referencia; marca lo que sentiste.</div>
        <div class="cv-err" data-err style="display:${o.failed?'flex':'none'}">${ic('cvwarn',15)}<span>No se pudo guardar. Tus datos se conservan; vuelve a intentarlo.</span></div>
      </div>
      <div class="cv-foot pl-glass" style="margin:0 10px 0;border-radius:30px;padding:10px 10px calc(var(--dockb)*0 + 10px);margin-bottom:var(--dockb)"><button class="cv-btn" data-save ${sel&&!o.saving?'':'disabled'}>${o.saving?'Guardando…':ic('cvcheck',18)+'Guardar cardio'}</button><button class="cv-link" data-act="back">Volver al cardio</button></div></div></div>`;
  },
  mount(root,api,o){
    cvMount(root);cvSessClock(root,48*60+2);
    let sel=o.rpeSel||null,failedOnce=!!o.failed,plan=o.plan||'nor';
    const lbl=root.querySelector('[data-rl]'),save=root.querySelector('[data-save]'),err=root.querySelector('[data-err]');
    const pick=n=>{sel=n;root.querySelectorAll('[data-rpe]').forEach(b=>{const on=+b.dataset.rpe===n;b.classList.toggle('on',on);b.setAttribute('aria-checked',on);if(on)b.classList.remove('p')});lbl.textContent=`RPE ${n} · ${rpeAnchor(n)}`;lbl.classList.add('on');save.disabled=false;err.style.display='none'};
    const doSave=()=>{
      if(!sel)return;save.disabled=true;save.textContent='Guardando…';
      cvLater(root,()=>{
        if(o.failFirst&&!failedOnce){failedOnce=true;err.style.display='flex';save.disabled=false;save.innerHTML=ic('cvcheck',18)+'Guardar cardio';return}
        const mixed=CVP[plan].mixed;
        api.go(mixed?'transOut':'recorded',{plan,rpeSel:sel,auto:o.auto});
      },900);
    };
    root._h=e=>{
      const r=e.target.closest('[data-rpe]');if(r){pick(+r.dataset.rpe);return}
      if(e.target.closest('[data-save]'))doSave();
      else if(e.target.closest('[data-act=back]'))api.go('live',{plan,step:Math.min(o.step??CVP[plan].steps.length-1,CVP[plan].steps.length-1),left:60,hr:o.hr??null,hr0:o.hr0??158,hrSrc:o.hrSrc||'watch',page:0,paused:false,locked:false,ready:false,km:o.km||2.62});
    };
    if(o.auto){cvLater(root,()=>pick(7),1500);cvLater(root,doSave,2300)}
  }
};

/* ---------- cardio registrado ---------- */
CVS.recorded={
  html(o){
    const plan=o.plan||'esc',Pn=CVP[plan],rp=o.rpeSel||7;
    const cells=plan==='nor'?[['Tiempo activo','40:00','registrado'],['Distancia','6,0','km'],['FC media','146','lpm'],['Esfuerzo',''+rp,'RPE · real']]
      :plan==='gps'?[['Tiempo activo','28:44','registrado'],['Distancia','4,6','km'],['FC media','—','lpm'],['Esfuerzo',''+rp,'RPE · real']]
      :[['Tiempo activo','20:00','registrado'],['Distancia','—','km'],['FC media','—','lpm'],['Esfuerzo',''+rp,'RPE · real']];
    return `<div class="cv-scr" data-s="recorded" style="--zc:${CVZ[2]}"><div class="cv-bg"></div>${cvIsland(Pn.isl[0],Pn.isl[1],'1:09:40')}
      <div class="cv-fin"><div class="cv-finb"><div class="cv-finwave" style="height:70px">${waveHost('done',70,{lines:8,intro:1.2})}</div>
        <span class="cv-ey">${Pn.name}</span><div style="display:flex;align-items:center;gap:12px;margin-top:8px"><span class="cv-tick">${ic('cvcheck',22)}</span><h2 style="margin:0;font-size:calc(26*var(--u))">Cardio registrado</h2></div>
        <div class="cv-cells" style="margin-top:16px">${cells.map(c=>`<div class="cv-cell"><small>${c[0]}</small><b>${c[1]}</b><span>${c[2]}</span></div>`).join('')}</div></div>
      <div class="cv-foot pl-glass" style="margin:0 10px var(--dockb);border-radius:30px;padding:10px"><button class="cv-btn alt" data-act="upd">Actualizar cardio</button></div></div></div>`;
  },
  mount(root,api,o){cvMount(root);cvSessClock(root,69*60+40);root._h=e=>{if(e.target.closest('[data-act=upd]'))api.go('finish',{plan:o.plan||'esc',rpeSel:o.rpeSel||7,saving:false,failed:false})}}
};

/* ---------- «Ahora vamos con fuerza» ---------- */
CVS.transOut={
  html(o){
    const Pn=CVP.nor;
    return `<div class="cv-scr" data-s="transOut" style="--zc:#ffffff"><div class="cv-bg" style="background:none"></div>
      <div class="cv-wave full" style="position:absolute;inset:0;-webkit-mask-image:linear-gradient(#0000 10%,#000 40%,#000 80%,#0000);mask-image:linear-gradient(#0000 10%,#000 40%,#000 80%,#0000)" data-w="done" data-h="${state.size[1]}" data-lines="26"></div>
      ${cvIsland(Pn.isl[0],'Cardio hecho · sigue fuerza','1:29:00')}
      <div class="cv-tr"><div class="cv-route"><span class="nd">${ic('cvrun',20)}</span><span class="ln"><i style="background:#f2eee6"></i></span><span class="nd dash">${ic('cvdumb',20)}</span></div>
        <h1 class="cv-big" aria-label="Ahora vamos con fuerza"><span>Ahora vamos</span><span>con fuerza</span></h1>
        <p class="cv-lead">Terminaste el cardio: 40:00 · 6,0 km · 82 % en tu objetivo.</p>
        <div class="cv-nx"><div class="cv-nxc"><div class="cv-tch"><span class="cvi">${ic('cvdumb',22)}</span><div><strong>Sentadilla Trasera con Barra Alta</strong><small>4 series · descanso 2:30</small></div></div>
          <div class="cv-nxr">Primera serie: <b>100 kg × 8</b><span class="tg">RIR 2 · plan</span></div>
          <div class="cv-last">${ic('cvhist',16)}<span>Última vez: <b>100 × 8 · RIR 2</b></span></div>
          <div class="cv-sc"><i class="a">A1</i><i class="a">A2</i><i>1</i><i>2</i><i>3</i><i>4</i></div>
          <div class="cv-fn">Aproximación A1 · 20 kg × 10 antes de la primera serie</div></div></div>
        ${swipeHTML('Desliza para continuar')}</div></div>`;
  },
  mount(root,api,o){
    cvMount(root);cvSessClock(root,89*60);
    cvSwipe(root,()=>api.go('strength',{auto:o.auto}));
    if(o.auto)cvLater(root,()=>root.querySelector('.cv-sw')._finish(),3000);
  }
};
CVS.strength={
  html(o){return `<div class="cv-scr" data-s="strength">${cvIsland('Pierna · Semana 1','4 ejercicios · 11 de 22 series','1:29:04')}
    <div style="position:absolute;left:24px;right:24px;top:200px;text-align:center;display:flex;flex-direction:column;align-items:center;gap:12px"><span class="cv-ey">Fin del recorrido de ejemplo</span><div class="cv-big" style="margin:0;font-size:calc(28*var(--u));letter-spacing:-1px">Sentadilla · serie 1 de 4</div><p style="margin:0;font:400 calc(13*var(--u))/1.5 Inter;color:var(--muted)">Aquí continúa la tarjeta de fuerza de siempre.</p><button class="cv-btn alt sm" data-act="again" style="margin-top:12px">Volver a empezar</button></div></div>`},
  mount(root,api){root._h=e=>{if(e.target.closest('[data-act=again]'))api.go('rest',{})}}
};

/* ---------- cardio en la vista Lista ---------- */
CVS.list={
  html(o){
    const Pn=CVP.nor,done=!!o.done;
    const grp=(t,sub,rows)=>`<div class="cv-lg"><h4>${t}<small>${sub}</small></h4>${rows.map((r,i)=>`<div class="cv-lr"><em>${r[0]}</em><span>${r[1]}</span><b>${r[2]}</b><i>${ic('cvcheck',13)}</i></div>`).join('')}</div>`;
    return `<div class="cv-scr" data-s="list" style="--zc:${CVZ[2]}">${cvIsland(Pn.isl[0],`4 ejercicios · ${done?10:9} de 10 series`,'46:30')}
      <div class="cv-list"><div style="display:flex;justify-content:space-between;margin:2px 2px 10px"><span class="cv-ey">Registro de la sesión</span><span class="cv-ey">${done?10:9} / 10</span></div>
        ${grp('Sentadilla Trasera con Barra Alta','4 series',[['1','Efectiva','100 × 8'],['2','Efectiva','100 × 8'],['3','Efectiva','100 × 8'],['4','Efectiva','100 × 8']])}
        ${grp('Peso Muerto Rumano','3 series',[['1','Efectiva','140 × 5'],['2','Efectiva','140 × 5'],['3','Efectiva','140 × 5']])}
        <div class="cv-lcard" data-card><div class="cv-tch"><span class="cvi">${ic(Pn.ico,22)}</span><div style="flex:1;min-width:0"><strong>${Pn.name}</strong><small>Cardio · 40 min · 9 pasos · Z1 → Z4</small></div></div>${cvProf(Pn.steps,-1,0,{})}
          <button class="cv-btn" data-act="open" style="width:100%;margin-top:12px;height:50px;${done?'background:#ffffff14;color:var(--ink)':''}">${done?ic('cvcheck',16)+'Cardio registrado · abrir':ic('cvplay',16)+'Empezar cardio'}</button></div>
      </div></div>`;
  },
  mount(root,api,o){cvSessClock(root,46*60+30);root._h=e=>{if(e.target.closest('[data-act=open]'))api.go(o.done?'finish':'transIn',{plan:'nor',watch:true,rpeSel:7})}}
};
/* ---------- laboratorio de ondas: una por estado ---------- */
CVS.lab={
  html(){
    const T=[['idle','Antes de empezar','Quietas y apagadas: esperan.',2],['warm','Calentamiento','Suaves y lentas.',2],['work','Trabajo','Amplias y rápidas.',4],['recover','Recuperación','Bajan y se alargan.',2],['cool','Vuelta a la calma','Casi planas.',1],['above','Por encima de la zona','Ásperas, rápidas y en coral.',5],['below','Por debajo de la zona','Flojas para el objetivo.',3],['pause','En pausa','Congeladas y apagadas.',3]];
    return `<div class="cv-scr" data-s="lab" style="--zc:${CVZ[2]}">${cvIsland('Ondas del cardio','Una por estado · así reaccionan','48:02')}
      <div class="cv-lab">${T.map(t=>`<div class="cv-wt" style="--zc:${CVZ[t[3]]}">${waveHost(t[0],cvCompact()?100:112,{lines:12})}<div class="cap">${t[1]}<small>${t[2]}</small></div><span class="zt"><i class="cv-dot" style="background:${CVZ[t[3]]}"></i>Z${t[3]}</span></div>`).join('')}</div></div>`;
  },
  mount(root){cvMount(root);cvSessClock(root,48*60+2)}
};

/* ================= escenas ================= */
const cvScene=(id,title,name,o,notes)=>scene({id,tab:'cardio-vivo',title,html:()=>cvRoot(name,typeof o==='function'?o():o),notes,after:ph=>cvBoot(ph,name,typeof o==='function'?o():o)});
const T_=(k,t)=>[k,t];
const IMG0={img:'h20-cardio-vivo',imgCap:'Hoy: el título «Escaladora» dos veces, figura de palitos, la tarjeta de controles demasiado arriba y cortando contenido, y tres puntos sin nombre.'};
const SIM='<button class="cv-simbtn" id="cv-sim">Simular transición</button>';

/* 1 · el recorrido completo, encadenado */
scene({id:'cv-flujo',tab:'cardio-vivo',title:'Recorrido completo · fuerza → cardio → fuerza',html:()=>cvRoot('rest',{left:92,watch:true}),
  after:ph=>{
    cvBoot(ph,'rest',{left:92,watch:true});
    const b=document.getElementById('cv-sim');
    if(b)b.onclick=()=>{const root=ph.querySelector('.cv-root');if(root&&root._api)root._api.go('rest',{left:92,watch:true,auto:true})};
  },
  notes:{tags:[T_('new','rediseño total'),T_('keep','mismo recorrido que la app')],
    p:[`Este es el recorrido entero de una sesión mixta, encadenado: <b>descanso → «Ahora vamos con cardio» → antes de empezar → 3-2-1 → cardio en marcha → cardio terminado → «Ahora vamos con fuerza»</b>.`,
      `Pulsa <b>Simular transición</b> y lo recorre solo (la cuenta atrás y el cardio van acelerados). O úsalo a mano: terminar el descanso, deslizar para empezar, pausar, bloquear, saltar paso, marcar el RPE y guardar. <b>Todas las escenas de esta pestaña son interactivas y continúan por este mismo recorrido.</b>`,SIM,
      `Una sesión solo de cardio salta las dos transiciones: entra directo a «Antes de empezar».`],
    listTitle:'Qué cambia frente a hoy',list:['Cada pulsación responde: onda de contacto en los botones, deslizamientos y fundidos entre páginas, pausa que desenfoca, bloqueo que atenúa, avisos que aparecen y se retiran.','Las ondas de fondo cambian con la zona y el ritmo (ver «Ondas del cardio»).','Las dos transiciones comparten una sola coreografía: una línea une los dos espacios y se enciende en la zona.']}});

/* 2 · transiciones y entrada */
cvScene('cv-descanso','Descanso que anuncia cardio','rest',{left:92,watch:true},
  {tags:[T_('keep','pieza existente'),T_('ask','tarjeta de descanso de otro paquete')],
   p:['La tarjeta de descanso ya dice qué viene después: «Al terminar el descanso pasas al espacio de cardio». Se mantiene tal cual; solo está dibujada aquí para abrir el recorrido.','La transición no se dispara al registrar la última serie de fuerza, sino al <b>terminar el descanso</b> (si no, el descanso la taparía).']});
cvScene('cv-trans-in','Ahora vamos con cardio','transIn',{plan:'nor',watch:true},
  {tags:[T_('new','coreografía nueva'),T_('keep','mismo contenido')],
   p:['Solo en sesiones que mezclan fuerza y cardio. Coreografía (≈ 2,2 s): las ondas del cardio se levantan por toda la pantalla, una línea une la fuerza con el cardio y se enciende en la zona del primer bloque, el titular sube palabra por palabra y la tarjeta del primer bloque aparece desde abajo. Espera a que deslices.',
     '<b>Desliza para empezar</b>: el botón arrastra la barra (hay que llegar casi al final); suelta antes y vuelve. Al terminar pasa a la cuenta 3-2-1. «Ver el plan» abre la página Plan sin empezar.'],
   listTitle:'Contenido real (sin cambios)',list:['Titular «Ahora vamos con cardio» y resumen de lo hecho: «Terminaste la fuerza: 3 ejercicios y 10 series».','Tarjeta: ejercicio, «Cardio · 40:00 · 9 pasos», primer bloque con su zona, «Ajusta a 7,5 km/h», perfil del plan y total con distancia estimada.','Con «Reducir movimiento» todo aparece ya colocado, sin desplazamientos.','Se guarda que esta transición ya se vio para no repetirla al recuperar la sesión.']});
cvScene('cv-listo','Antes de empezar · solo cardio','ready',{plan:'esc',watch:false},
  {tags:[T_('new','rediseño'),T_('keep','mismas comprobaciones')],
   p:['Así se entra a una sesión que empieza por cardio (o tras «Ver el plan»). Sustituye la figura de palitos por las ondas, quietas y apagadas hasta que empiezas: el espacio está «esperando».',
      'Las tres comprobaciones salen del código (Pulso · Lugar · Pantalla). El botón <b>Empezar</b> vive en el panel de abajo, a la altura del pulgar, con la cuenta atrás avisada debajo.'],
   listTitle:'Cambios',list:['Título del ejercicio una sola vez (en la cabecera), no otra vez dentro de la tarjeta.','Cada comprobación lleva un estado a la derecha (Listo / RPE / Revisar), antes solo texto.','El hueco muerto bajo los contenidos desaparece: lo que sobra se queda en negro, el botón no flota a media pantalla.']});
cvScene('cv-listo-gps','Antes de empezar · exterior sin permiso','ready',{plan:'gps',watch:true,gps:'denied'},
  {tags:[T_('new','estado de aviso')],
   p:['Cardio exterior con reloj y sin permiso de ubicación: «Exterior · falta permiso de ubicación», marcado en ámbar como «Revisar». Se puede empezar igualmente: irá por cronómetro, sin distancia (ver la escena «GPS · sin permiso»).']});
cvScene('cv-cuenta','Cuenta atrás 3-2-1','count',{plan:'nor',loop:true},
  {tags:[T_('new','rediseño')],
   p:['Cada número entra grande y se asienta con un anillo que se expande en la zona del primer bloque, mientras las ondas ya se despiertan al fondo. Debajo, lo que empieza: «Calentamiento · 10:00 · 7,5 km/h».','En esta escena la cuenta se repite en bucle; en el recorrido real pasa al cardio al llegar a 0 (o al tocar el número).']});

/* 3 · cardio en marcha: Ahora */
const SW=(extra)=>({plan:'nor',watch:true,...extra});
cvScene('cv-calentamiento','Calentamiento · en tu zona','live',SW({step:0,left:420,hr:118,hr0:118,inZ:95,zs:[40,100,0,0,0]}),
  {tags:[T_('new','rediseño total')],
   p:['Ondas suaves y lentas (Z2). Una sola cifra protagonista —lo que queda del paso— con «quedan 31:40» al lado; las consignas de la máquina (7,5 km/h) en dos fichas sobre las ondas; el pulso con su objetivo y la cinta de zonas en un módulo; plan y siguiente paso abajo.']});
cvScene('cv-trabajo','Trabajo · en tu zona','live',SW({}),
  {...IMG0,tags:[T_('new','rediseño total'),T_('keep','mismos datos que la app')],
   p:['La escena de referencia. <b>El título del ejercicio aparece una sola vez</b> (cabecera, con el micrófono de Modo Voz); debajo, una línea de contexto con el punto de la zona: «Trabajo · Vuelta 3 de 4 · Paso 6 de 9».',
      'La cifra del paso manda (80 px). Las ondas están hechas con la técnica de las portadas de programa y están vivas: <b>más zona = más amplitud y velocidad</b>, y laten al ritmo del pulso real. El acento de la línea y el punto de la cabecera toman el color de la zona en la que <i>estás</i>.',
      'Módulos con criterio: <b>Pulso</b> (lpm + objetivo + cinta con aguja + estado), <b>Distancia · FC media · Energía</b> (estimadas con «≈») y <b>Plan</b> (perfil con posición y siguiente paso). Abajo, el panel de vidrio: <b>Ahora · Plan · Datos</b> con indicador que se desliza, y Bloquear · Pausa · Saltar paso, pegado al borde inferior.'],
   listTitle:'Pruébalo',list:['Desliza el contenido o las pestañas: las páginas siguen al dedo y se asientan con fundido.','Pausa, bloquea (mantén 1,2 s para desbloquear), salta paso: cada acción tiene su transición.','El reloj corre de verdad: la cifra, el perfil y la aguja se actualizan.']});
cvScene('cv-sobre-zona','Trabajo · por encima de la zona','live',SW({hr:176,hr0:176,inZ:40,zs:[78,640,230,470,40],pctZ:74}),
  {tags:[T_('new','aviso con color de zona')],
   p:['172+ lpm sobre una zona 4: las ondas se vuelven ásperas, rápidas y en <b>coral (Z5)</b>; el módulo del pulso se enmarca con ese color y dice «Baja el ritmo · 176 lpm · sobre Z4». El aviso tiene histéresis (un pico de un segundo no cambia nada), como hoy.']});
cvScene('cv-bajo-zona','Trabajo · por debajo de la zona','live',SW({hr:141,hr0:141,inZ:20,zs:[78,640,330,330,0],pctZ:61}),
  {tags:[T_('new','aviso con color de zona')],
   p:['Pulso por debajo del objetivo: ondas flojas y «Sube el ritmo · 141 lpm · bajo Z4», enmarcado en el color de la zona Z3 en la que estás.']});
cvScene('cv-recuperacion','Recuperación activa','live',SW({step:6,left:130,hr:128,hr0:128,drop:27,inZ:50}),
  {tags:[T_('keep','mismo texto'),T_('new','ondas de recuperación')],
   p:['Ondas que bajan y se alargan. El estado pasa a «Recuperando bien · −27 lpm en 1 min» (solo cuando hay más de un minuto de paso y el pulso ha bajado). El siguiente paso (Trabajo 4, Z4) ya está preparado abajo.']});
cvScene('cv-continuo','Escaladora · continuo, sin pulso','live',{plan:'esc',step:0,left:1181,hr:null,hrSrc:null,page:0,inZ:0,zs:[0,0,0,0,0]},
  {...IMG0,tags:[T_('new','estado vacío honesto'),T_('keep','guía por esfuerzo')],
   p:['<b>El caso de tu captura</b>, rehecho: «Escaladora» una vez; reloj de 19:41 en grande; sin figura de palitos; ondas de Z3 (el plan marca RPE 5–6).',
      'Sin pulso no parece un error: el módulo muestra «—  sin pulso», la <b>guía por esfuerzo</b> (RPE 5–6 · Algo duro, «lo planificado es una guía») sobre la cinta de diez tramos y una sola acción clara, <b>Añadir pulso</b>, que abre la hoja del pulso manual.',
      'Un cardio continuo no tiene «siguiente paso» ni «Saltar paso»: el panel solo trae Bloquear y Pausa (hoy tampoco enseña lo que no hace nada).'],
   listTitle:'Honestidad de datos',list:['Sin distancia ni pulso no se muestran celdas vacías: solo Activo y Energía (≈, necesita peso corporal).','«Conectar reloj» no se dibuja: hoy no existe ese flujo dentro del cardio en vivo (ver dudas en el mapa).']});
cvScene('cv-pulso-manual','Pulso anotado a mano','live',{plan:'esc',step:0,left:748,hr:150,hr0:150,hrSrc:'manual',hrAge:30,page:0,inZ:200,zs:[0,0,200,0,0],pctZ:100},
  {tags:[T_('keep','función existente')],
   p:['Sin reloj, el pulso que anotas se enseña igual que el del reloj —con su zona y la aguja— y lleva su antigüedad en tiempo activo («manual · hace 0:30»). Tocarlo lo actualiza. Cuenta en la FC media final, pero no reparte «tiempo en zona» (una lectura suelta no es tiempo).']});
cvScene('cv-hoja-pulso','Hoja · añadir pulso a mano','live',{plan:'esc',step:0,left:1181,hr:null,hrSrc:null,page:0,sheet:true,sheetText:'150'},
  {tags:[T_('keep','mismos textos y reglas'),T_('new','hoja con la piel de las hojas')],
   p:['Campo de lpm (40–230), vista previa de la zona («Con 150 lpm estás en zona 3 · Tempo.») y «Guardar pulso». Escribe un valor fuera de rango y pulsa Guardar para ver el mensaje de error; al guardar, el pulso aparece en el módulo con un aviso.']});
cvScene('cv-series','Varias series de cardio','live',{plan:'esc',step:0,left:600,hr:null,hrSrc:null,page:0,sets:[{ok:true},{}],setSel:1,inZ:0,zs:[0,0,0,0,0]},
  {tags:[T_('new','selector reubicado')],
   p:['Un ejercicio de cardio con varias series las enseña como un selector bajo el título («Serie 1 · Registrada», «Serie 2»), en el mismo lenguaje de píldoras en vez de los chips de Material de hoy.']});

/* 4 · exterior con GPS */
cvScene('cv-gps','Exterior · por distancia con GPS','live',{plan:'gps',step:0,left:760,hr:null,hrSrc:null,page:0,gps:'ok',km:2.62,watch:false,inZ:0,zs:[0,0,0,0,0]},
  {tags:[T_('keep','distancia objetivo → km restantes'),T_('new','ritmo como métrica')],
   p:['Con distancia objetivo y GPS, la cifra grande son los <b>km que faltan</b> («2,38 · km restantes · de 5,00 km») y la barra mide la distancia. El <b>ritmo actual</b> pasa de ocupar el hueco del pulso a ser una métrica con nombre (5:12 /km); la ficha «GPS activo» y el objetivo de ritmo van sobre las ondas.']});
cvScene('cv-gps-sin-senal','GPS · sin señal','live',{plan:'gps',step:0,left:760,hr:null,hrSrc:null,page:0,gps:'lost',km:2.62,inZ:0,zs:[0,0,0,0,0]},
  {tags:[T_('new','aviso'),T_('keep','estado SIGNAL_LOST')],
   p:['La señal se pierde: aviso en ámbar arriba («Seguimos contando el tiempo; la distancia se retoma al recuperar la señal»), la ficha pasa a «Sin señal GPS» y el ritmo muestra «última lectura».']});
cvScene('cv-gps-permiso','GPS · sin permiso','live',{plan:'gps',step:0,left:1500,hr:null,hrSrc:null,page:0,gps:'denied',km:0,inZ:0,zs:[0,0,0,0,0]},
  {tags:[T_('new','aviso con acción'),T_('keep','«Ubicación no disponible · reintentar permiso»')],
   p:['Hoy es un texto suelto de 11 px bajo el panel. Ahora es un aviso en la página con su botón «Reintentar permiso» (al pulsarlo pasa a GPS activo, como si lo concedieras).']});

/* 5 · páginas */
cvScene('cv-plan','Página Plan','live',SW({page:1}),
  {tags:[T_('new','con nombre y propósito'),T_('keep','mismos pasos')],
   p:['<b>Qué eran los tres puntos de hoy:</b> el indicador de tres páginas del contenido (Ahora, Plan, Resumen), sin nombre y a ras de los botones; deslizar el panel cambiaba de página sin explicar adónde. Ahora cada página tiene <b>nombre visible</b> en las pestañas del panel, un indicador que se desliza y un título propio.',
      '<b>Plan</b> = todos los pasos con su zona y consigna: el perfil de bloques arriba con la posición, y la lista como una línea de tiempo (hechos atenuados con ✓, el actual resaltado con su progreso, los siguientes normales). Es la única lista con scroll, porque es repetible.']});
cvScene('cv-datos','Página Datos','live',SW({page:2}),
  {tags:[T_('new','rename Resumen → Datos'),T_('keep','solo datos medidos; energía con ≈')],
   p:['<b>Datos</b> = lo medido hasta ahora: tiempo activo, distancia (≈ si es de cinta), FC media y energía estimada, y el tiempo por zona con el porcentaje del trabajo dentro de tu zona objetivo. Se llama «Resumen» hoy; «Datos» dice mejor lo que es en mitad del entreno.']});
cvScene('cv-datos-sin-pulso','Datos · sin pulso','live',{plan:'esc',step:0,left:748,hr:null,hrSrc:null,page:2,inZ:0,zs:[0,0,0,0,0]},
  {tags:[T_('new','estado vacío con acción')],
   p:['Sin pulso no hay tiempo por zona. En lugar de cinco barras a 0:00 hay un bloque punteado que lo explica y deja <b>Añadir pulso</b> a un toque.']});

/* 6 · pausa y bloqueo */
cvScene('cv-pausa','Pausa','live',SW({paused:true,pausedSec:42}),
  {tags:[T_('keep','mismos textos'),T_('new','desenfoque y ondas congeladas')],
   p:['La pausa desenfoca el contenido y deja las ondas congeladas y apagadas detrás. El tiempo en pausa corre aparte («no cuenta como activo») y el paso actual queda en lo que faltaba. <b>Reanudar</b> (también en el panel) devuelve las ondas a su zona con un fundido; <b>Terminar cardio</b> pasa a «Cardio terminado» y conserva lo hecho (se puede volver).']});
cvScene('cv-bloqueo','Pantalla bloqueada','live',SW({locked:true}),
  {tags:[T_('keep','mantener 1,2 s'),T_('new','aviso visible')],
   p:['El contenido se atenúa, las pestañas se apagan y sale una etiqueta «Pantalla bloqueada». El panel pasa a <b>mantener para desbloquear</b>: el anillo se llena durante 1,2 s. Con TalkBack, la acción accesible desbloquea al instante (igual que hoy).']});

/* 7 · cierre */
cvScene('cv-terminado','Cardio terminado · guardar con RPE real','finish',{plan:'nor',watch:true},
  {tags:[T_('keep','RPE real obligatorio'),T_('new','rediseño')],
   p:['Lo medido en cuatro celdas, el cumplimiento por paso (ancho = duración, color = zona; los pasos por debajo del 85 % llevan su porcentaje) y la pregunta «¿Cuánto te costó?». <b>Guardar cardio está apagado hasta marcar el RPE</b>; al marcarlo se lee «RPE 7 · Duro». La raya bajo el número es el RPE del plan, solo una referencia.',
      'Arriba, unas ondas que se aquietan: el cardio ha terminado.'],
   listTitle:'Pruébalo',list:['Marca un RPE y guarda: el botón pasa a «Guardando…» y continúa a «Ahora vamos con fuerza» (sesión mixta) o a «Cardio registrado».','«Volver al cardio» retoma el cardio sin perder nada.']});
cvScene('cv-terminado-vacio','Cardio terminado · sin pulso ni distancia','finish',{plan:'esc',hr:null,watch:false},
  {tags:[T_('keep','toca para añadir'),T_('new','celdas editables con borde')],
   p:['Sin pulso ni distancia, esas celdas se vuelven botones con borde y un lápiz («toca para añadir», abre la rueda de FC media o de distancia como hoy). El cumplimiento por paso dice honestamente «Sin pulso: no hay cumplimiento medido por paso.».']});
cvScene('cv-terminado-error','Cardio terminado · no se pudo guardar','finish',{plan:'nor',watch:true,rpeSel:7,failed:true},
  {tags:[T_('keep','mensaje del código')],
   p:['Si el guardado falla: «No se pudo guardar. Tus datos se conservan; vuelve a intentarlo.» El botón vuelve a estar disponible. (Pulsa Guardar para ver el primer intento: aquí ya viene fallido.)']});
cvScene('cv-registrado','Cardio registrado','recorded',{plan:'esc',rpeSel:6},
  {tags:[T_('keep','mismas cuatro celdas'),T_('new','rediseño')],
   p:['Lo guardado, con el esfuerzo real, y «Actualizar cardio» para corregir. Si el cardio estaba en sesión mixta, antes se ve la transición a fuerza.']});
cvScene('cv-trans-out','Ahora vamos con fuerza','transOut',{plan:'nor',watch:true},
  {tags:[T_('new','coreografía nueva'),T_('keep','mismo contenido')],
   p:['Al terminar el cardio no hay descanso: aparece enseguida. El hilo conductor es el mismo, al revés: las ondas se aquietan, la línea une el cardio con una mancuerna punteada y la tarjeta presenta <b>la siguiente serie de fuerza tal como se verá</b> (ejercicio, primera serie y esfuerzo del plan, última vez, series con la aproximación).',
      'Resume el cardio con datos medidos: «Terminaste el cardio: 40:00 · 6,0 km · 82 % en tu objetivo.»']});

/* 8 · lista y ondas */
cvScene('cv-lista','Cardio en la vista Lista','list',{},
  {tags:[T_('keep','bloque propio, no filas de series'),T_('ask','la lista es de otro paquete')],
   p:['En Lista, el cardio es un bloque con su perfil y un botón que abre su espacio; no se descompone en filas de series. Mismo contenido que hoy («Cardio · 40 min · 9 pasos · Z1 → Z4»). Desde aquí se entra por la misma transición de sesión mixta. Si ya está registrado, el botón dice «Cardio registrado · abrir» y reabre «Cardio terminado».']});
cvScene('cv-ondas','Ondas del cardio · una por estado','lab',{},
  {tags:[T_('new','lenguaje de las portadas, en movimiento')],
   p:['Las ondas usan la técnica de la portada «Ondas»: crestas apiladas que se tapan unas a otras, trazo crema y <b>una sola línea con color</b>, la de la zona. Los parámetros cambian por estado (amplitud, velocidad, aspereza); al pasar de uno a otro se funden, no saltan.',
      'Con «Reducir movimiento» se dibuja una sola pose fija con sentido (misma amplitud del estado, sin animar).'],
   listTitle:'Reglas',list:['Amplitud y velocidad crecen con la zona; el ritmo (cadencia) manda la velocidad.','Con pulso real, la cresta late a ese ritmo; sin pulso, el halo respira lento.','Color solo de la zona; en pausa se apaga.']});

