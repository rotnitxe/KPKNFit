/* ================= VIVO-RESTO (lx) · cronómetro y temporizador, modo visual, sesión completada, modo Lista ================= */
// Puntos 16–19 del 29-oct. Todo sale de screens/workout/: WorkoutHeaderBar.kt (cápsula y píldora de tiempo), components/SessionTimeSheet.kt,
// components/SetCardExerciseMediaBack.kt (modo visual), RecordedSessionFinishSheet.kt + WorkoutStorySheet.kt (cierre), LiveSessionListView.kt.
Object.assign(P,{
 lxStop:'<circle cx="12" cy="13.5" r="7.5"/><path d="M12 13.5V9.5M9.5 3.5h5M12 3.5v2.5M18.4 7.1l1.3-1.3"/>',
 lxGlass:'<path d="M7 3.5h10M7 20.5h10M8 3.5v3.2c0 2.3 4 3.9 4 5.3s-4 3-4 5.3v3.2M16 3.5v3.2c0 2.3-4 3.9-4 5.3s4 3 4 5.3v3.2"/>',
 lxExpand:'<path d="M14 4h6v6M10 20H4v-6M20 4l-7 7M4 20l7-7"/>',
 lxFold:'<path d="M20 10h-6V4M4 14h6v6M14 10l7-7M10 14l-7 7"/>',
 lxImage:'<rect x="3.5" y="5" width="17" height="14" rx="3"/><circle cx="9" cy="10" r="1.6"/><path d="M4 17l5-4.5 3.5 3 3-2.5 4.5 4"/>',
 lxNote:'<path d="M6 3.5h8.5L18.5 8v12.5H6z"/><path d="M14 3.5V8h4.5M9 12.5h6M9 16h4"/>',
 lxBack:'<path d="M9 5.5h10a2 2 0 012 2v9a2 2 0 01-2 2H9l-6-6.5z"/><path d="M12.5 9.5l5 5M17.5 9.5l-5 5"/>',
 lxVib:'<rect x="8" y="3.5" width="8" height="17" rx="2.2"/><path d="M4 8v8M20 8v8"/>',
 lxSound:'<path d="M4 9.5h4l5-4v13l-5-4H4z"/><path d="M16.5 9a4 4 0 010 6M19 6.5a7.5 7.5 0 010 11"/>',
 lxRun:'<circle cx="14" cy="4.5" r="1.8"/><path d="M8 21l3-6 3 2 1 4M6 12l3-4 4 1 2 3 3 1M11 15l2-6"/>',
 lxFlag:'<path d="M6 21V4M6 4.5h11l-2.5 4 2.5 4H6"/>',
 lxReset:'<path d="M4 12a8 8 0 102.6-5.9M4 4.5v4.2h4.2"/>',
 lxPlay:'<path d="M8 5.5v13l10.5-6.5z" fill="currentColor" stroke="none"/>',
 lxPause:'<path d="M8.5 5.5v13M15.5 5.5v13" stroke-width="3"/>',
 lxMinus:'<path d="M6 12h12"/>',
 lxRoute:'<circle cx="6" cy="18" r="2"/><circle cx="18" cy="6" r="2"/><path d="M8 18h6a3 3 0 000-6h-4a3 3 0 010-6h6"/>',
 lxFlip:'<path d="M5 9a7 7 0 0112-3.5M19 15a7 7 0 01-12 3.5M17 2.5v3.2h-3.2M7 21.5v-3.2h3.2"/>',
});
const lxI=(n,s=18)=>ic(n,s);
const lxPad=n=>String(n).padStart(2,'0');
const lxPartsCs=ms=>{const t=Math.floor(Math.max(0,ms)/100);const s=Math.floor(t/10);return{m:Math.floor(s/60),s:s%60,d:t%10}};
const lxLap=ms=>{const c=lxPartsCs(ms);return `${c.m}:${lxPad(c.s)},${c.d}`};
const lxRo=ms=>{const c=lxPartsCs(ms);return `<b data-ro>${lxPad(c.m)}:${lxPad(c.s)}</b><i data-rod>,${c.d}</i>`};
const lxClock=s=>{s=Math.max(0,Math.round(s));const h=Math.floor(s/3600),m=Math.floor(s%3600/60);return h?`${h}:${lxPad(m)}:${lxPad(s%60)}`:`${m}:${lxPad(s%60)}`};
const lxNow=()=>performance.now();

/* ---------- fondo: la pantalla de la sesión en vivo (cápsula + tarjeta + barra inferior) ---------- */
const lxPill=(o={})=>{
  const sec=o.ch!=null?`${lxI('lxStop',12)}<span class="lx-num">${o.ch}</span>`:o.tm!=null?`${lxI('lxGlass',12)}<span class="lx-num">${o.tm}</span>`:'';
  return `<div class="lx-pill ${sec?'lx-two':''}" role="button" aria-label="Tiempo"><span class="lx-r1">${lxI('lxStop',15)}<span class="lx-num">${o.time||'5:45'}</span></span>${sec?`<span class="lx-r2">${sec}</span>`:''}</div>`;
};
const lxCap=(o={})=>`<div class="lx-cap ${o.glass?'lx-glass':''}"><div class="lx-t"><strong>${o.title||'Legs II · Semana 1'}</strong><span>${o.sub||'6 ejercicios · 0 de 18 series registradas'}</span></div>${lxPill(o)}<span class="lx-more">${lxI('more',18)}</span></div>`;
const lxDock=(o={})=>`<div class="lx-dock"><div class="lx-sum"><span>SERIES · ${o.done??0} DE 18 ${lxI('down',11)}</span><span>${o.ex||'Peso Muerto…'} ${o.set||'1 / 3'}</span></div>
 <div class="lx-steps">${[1,2,3].map((n,i)=>`<i class="${i===0&&!o.rest?'lx-on':''}">${n}</i>`).join('<u></u>')}<u></u><i>${lxI('fwd',11)}</i></div>
 <div class="lx-act"><span class="lx-nv">${lxI('back',17)}</span><span class="lx-main">${o.rest?lxI('lxPlay',14)+' Terminar descanso':lxI('check',18)+' Registrar serie'}</span><span class="lx-nv">${lxI('fwd',17)}</span></div></div>`;
const lxSeriesBack=(o={})=>`${lxCap(o)}
 <div class="lx-ctx"><div><h2>Peso Muerto<br>Convencional</h2><span>Barra</span></div>${lxI('mic',20)}</div>
 <section class="lx-card"><div class="lx-k"><span>SERIE 1 <em>/ 3</em></span>${lxI('sliders',17)}</div>
  <div class="lx-h"><span>CARGA</span><span class="lx-m">Externa ${lxI('down',11)}</span></div>
  <div class="lx-hero"><b>—</b><small>kg</small><span class="lx-adj"><i>${lxI('lxMinus',16)}</i><i>${lxI('plus',16)}</i></span></div>
  <div class="lx-h"><span>REPETICIONES</span></div><div class="lx-reps">${[2,3,4,5,6].map(n=>`<i class="${n===4?'lx-on':''}">${n}</i>`).join('')}</div>
  <div class="lx-h"><span>ESFUERZO</span><span class="lx-m">Sin marcar ${lxI('down',11)}</span></div><div class="lx-slider"><i></i></div>
  <div class="lx-link"><span>Parciales, ayuda y ROM</span>${lxI('fwd',15)}</div>
  <div class="lx-foot"><span>${lxI('sliders',14)} Avanzado</span><span>${lxI('camera',14)} Modo visual</span><span>${lxI('history',14)}</span></div></section>${lxDock(o)}`;
const lxRestBack=(o={})=>`${lxCap(o)}
 <div class="lx-ctx"><div><h2>Peso Muerto<br>Convencional</h2><span>Barra</span></div>${lxI('mic',20)}</div>
 <section class="lx-card lx-rest"><div class="lx-k"><span>DESCANSO</span>${lxI('lxExpand',16)}</div>
  <div class="lx-ring"><svg viewBox="0 0 160 160" width="160" height="160"><circle cx="80" cy="80" r="70" fill="none" stroke="#ffffff0e" stroke-width="9"/><circle cx="80" cy="80" r="70" fill="none" stroke="#f2eee6" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="${(2*Math.PI*70*.52).toFixed(1)} 999" transform="rotate(-90 80 80)"/></svg><b class="lx-num">1:18</b><span>restante</span></div>
  <div class="lx-chips"><i>−15 s</i><i>${lxI('lxPause',13)}</i><i>+15 s</i></div>
  <div class="lx-nx"><small>SIGUIENTE</small><div><b>Peso Muerto · serie 2</b><span>140 kg × 5</span></div></div></section>${lxDock({...o,rest:true,done:1,set:'2 / 3'})}`;

/* ---------- el reloj: cronómetro de verdad (esfera con segundero y subesfera) ---------- */
function lxDial({goal=0,sub=30}={}){
  let tk='',nm='',st='';
  for(let i=0;i<60;i++){const M=i%5===0;tk+=`<line x1="120" y1="${M?16:19}" x2="120" y2="${M?29:24}" transform="rotate(${i*6} 120 120)" stroke="#f2eee6" stroke-width="${M?2:1}" stroke-opacity="${M?.9:.32}" stroke-linecap="round"/>`}
  [['60',0],['15',90],['45',270]].forEach(([t,a])=>{const x=120+Math.sin(a*Math.PI/180)*74,y=120-Math.cos(a*Math.PI/180)*74;nm+=`<text x="${x.toFixed(1)}" y="${(y+4).toFixed(1)}" text-anchor="middle" font-family="Inter" font-size="11" font-weight="600" fill="#8e8980">${t}</text>`});
  if(sub){for(let i=0;i<6;i++)st+=`<line x1="120" y1="145" x2="120" y2="150" transform="rotate(${i*60} 120 168)" stroke="#f2eee6" stroke-opacity=".65" stroke-width="1.4" stroke-linecap="round"/>`;
    st=`<circle cx="120" cy="168" r="26" fill="#ffffff06" stroke="#ffffff26"/>${st}<g data-sub transform="rotate(0 120 168)"><line x1="120" y1="174" x2="120" y2="150" stroke="#f2eee6" stroke-width="2" stroke-linecap="round"/></g><circle cx="120" cy="168" r="2.6" fill="#f2eee6"/>`}
  const C=2*Math.PI*112;
  return `<svg class="lx-dial" viewBox="0 0 240 240" data-goal="${goal}" data-sub-max="${sub}" aria-hidden="true">
   <circle cx="120" cy="120" r="118" fill="#0d0d0d" stroke="#ffffff14"/><circle cx="120" cy="120" r="112" fill="none" stroke="#ffffff0d" stroke-width="${goal?7:3}"/>
   <circle data-arc cx="120" cy="120" r="112" fill="none" stroke="${goal?'#f2eee6':'#f2eee6'}" stroke-opacity="${goal?.95:.5}" stroke-width="${goal?7:3}" stroke-linecap="round" stroke-dasharray="0 ${C.toFixed(1)}" transform="rotate(-90 120 120)"/>
   ${tk}${nm}${st}
   <g data-hand transform="rotate(0 120 120)"><line x1="120" y1="142" x2="120" y2="22" stroke="#f2eee6" stroke-width="2.2" stroke-linecap="round"/><line x1="120" y1="120" x2="120" y2="146" stroke="#f2eee6" stroke-width="5" stroke-linecap="round"/></g>
   <circle cx="120" cy="120" r="6" fill="#f2eee6"/><circle cx="120" cy="120" r="2.2" fill="#0d0d0d"/></svg>`;
}
function lxDialSet(svg,ms){
  if(!svg)return;const C=2*Math.PI*112,sub=+svg.dataset.subMax||0,goal=+svg.dataset.goal||0;
  const h=svg.querySelector('[data-hand]'),a=svg.querySelector('[data-arc]'),s=svg.querySelector('[data-sub]');
  if(h)h.setAttribute('transform',`rotate(${((ms%60000)/60000*360).toFixed(2)} 120 120)`);
  if(s&&sub)s.setAttribute('transform',`rotate(${((ms/60000%sub)/sub*360).toFixed(2)} 120 168)`);
  if(a){const f=goal?Math.min(1,ms/(goal*60000)):(ms%60000)/60000;a.setAttribute('stroke-dasharray',`${(C*f).toFixed(1)} ${C.toFixed(1)}`);a.setAttribute('stroke',goal&&ms>goal*60000?'#ff9b92':'#f2eee6')}
}
function lxTimerRing(frac,state){
  const C=2*Math.PI*108,r=C*Math.max(0,Math.min(1,frac));
  let tk='';for(let i=0;i<60;i++){const M=i%5===0;tk+=`<line x1="120" y1="${M?26:28}" x2="120" y2="${M?34:31}" transform="rotate(${i*6} 120 120)" stroke="#f2eee6" stroke-opacity="${M?.35:.14}" stroke-width="${M?1.6:1}" stroke-linecap="round"/>`}
  return `<svg class="lx-tring ${state==='done'?'lx-done':''}" viewBox="0 0 240 240" aria-hidden="true"><circle cx="120" cy="120" r="108" fill="none" stroke="#ffffff10" stroke-width="7"/>
   <circle data-tarc cx="120" cy="120" r="108" fill="none" stroke="#f2eee6" stroke-width="7" stroke-linecap="round" stroke-dasharray="${r.toFixed(1)} ${C.toFixed(1)}" transform="rotate(-90 120 120)"/>${tk}</svg>`;
}

/* ---------- la hoja «Tiempo»: Sesión · Cronómetro · Temporizador ---------- */
const LX_PRESETS=[30,60,90,120,180,300];
const LX_LAP_DEMO=[41200,38900,44600,40100,39400];
function lxTimeApp(phone,cfg){
  const root=phone.querySelector('.lx-tsheet');if(!root)return;
  const S={tab:cfg.tab||0,
    ch:Object.assign({run:false,t0:0,acc:0,laps:[]},cfg.ch||{}),
    tm:Object.assign({digits:'',total:0,run:false,end:0,left:0,st:'cfg',snd:true,vib:true},cfg.tm||{}),
    ses:Object.assign({base:52*60+10,goal:60,pend:null,alert:1},cfg.ses||{}),t00:lxNow()};
  if(S.ch.run)S.ch.t0=lxNow();
  if(S.tm.st==='run')S.tm.end=lxNow()+S.tm.left*1000;
  const chMs=()=>S.ch.acc+(S.ch.run?lxNow()-S.ch.t0:0);
  const tmLeft=()=>S.tm.st==='run'?Math.max(0,S.tm.end-lxNow())/1000:S.tm.st==='done'?0:S.tm.left;
  const sesMs=()=>S.ses.base*1000+(lxNow()-S.t00);
  const digTotal=()=>{const d=S.tm.digits.padStart(4,'0');return (+d.slice(0,2))*60+(+d.slice(2))};
  const dispDig=()=>{const d=S.tm.digits.padStart(4,'0');return `${d.slice(0,2)}:${d.slice(2)}`};
  const segs=['Sesión','Cronómetro','Temporizador'];
  const seg=()=>`<div class="lx-seg" role="tablist">${segs.map((s,i)=>`<button role="tab" aria-selected="${i===S.tab}" class="${i===S.tab?'lx-on':''}" data-act="tab" data-v="${i}">${s}${i===1&&S.ch.run?'<u></u>':''}${i===2&&S.tm.st==='run'?'<u></u>':''}</button>`).join('')}</div>`;
  const chip=(l,on,act,v,ic_)=>`<button class="lx-chip ${on?'lx-on':''}" data-act="${act}" data-v="${v}" aria-pressed="${!!on}">${ic_?lxI(ic_,15):''}${l}</button>`;
  // --- Sesión
  const paneSes=()=>{
    const ms=sesMs(),g=S.ses.goal,over=g&&ms>g*60000;
    const leftMin=g?Math.max(0,Math.ceil((g*60000-ms)/60000)):null;
    const goals=[30,45,60,90];
    const note=S.ses.pend?`<div class="lx-pend"><p>${S.ses.pend.clear?'Quitar el objetivo. ¿Cómo aplicarlo?':`Objetivo: ${S.ses.pend.min} min. ¿Cómo aplicarlo?`}</p><div><button class="pl-btn lxg lx-sm" data-act="sespend" data-v="0">Solo esta vez</button><button class="pl-btn lx-sm" data-act="sespend" data-v="1">Siempre</button></div></div>`:'';
    const alerts=['Nada','15 y 5 min','Ritmo','Por ejercicio'];
    return `<div class="lx-ses"><div class="lx-sesdial">${lxDial({goal:g,sub:60})}</div>
     <div class="lx-sescol"><div class="lx-ro lx-sm" data-roses>${lxClock(Math.floor(ms/1000))}</div><div class="lx-scap">Tiempo transcurrido${g?` · objetivo ${g} min`:''}</div>
      <div class="lx-pace"><strong>A este ritmo terminas en 58 min</strong><span>Fin estimado 20:41</span>${g?`<span class="${over?'lx-bad':''}">${over?'+':''}${Math.abs(58-g)} min ${58>g?'sobre':'antes de'} tu plan</span>`:''}</div></div></div>
     <div class="lx-sec">Objetivo</div><div class="lx-chips lx-rowfill">${goals.map(m=>chip(m+' min',(S.ses.pend?S.ses.pend.min:S.ses.goal)===m,'sesgoal',m)).join('')}${chip('Otro…',false,'sesgoal',-1)}</div>
     ${S.ses.goal?`<div class="lx-chips lx-tight">${[-5,5,15].map(d=>chip((d>0?'+':'−')+Math.abs(d)+' min',false,'sesdelta',d)).join('')}<button class="lx-link" data-act="sesclear">Quitar objetivo</button></div>`:''}
     ${note}
     <div class="lx-sec">Avisos</div><div class="lx-chips lx-rowfill">${alerts.map((a,i)=>chip(a,S.ses.alert===i,'sesalert',i)).join('')}</div>
     <button class="lx-row"><span class="lx-ic">${lxI('sliders',18)}</span><span class="lx-tx"><strong>Revisar cómo acortar lo pendiente</strong></span>${lxI('fwd',15)}</button>`;
  };
  // --- Cronómetro
  const lapsHtml=()=>{
    const L=S.ch.laps,parts=L.map((t,i)=>t-(i?L[i-1]:0)),cur=chMs()-(L.length?L[L.length-1]:0);
    const best=parts.length>1?Math.min(...parts):-1,worst=parts.length>1?Math.max(...parts):-1;
    const rows=parts.map((p,i)=>({n:i+1,p,t:L[i]})).reverse();
    const live=(S.ch.run||S.ch.acc>0)?`<div class="lx-lap lx-now"><span class="lx-n">Vuelta ${L.length+1}</span><span class="lx-p lx-num" data-lapnow>${lxLap(cur)}</span><span class="lx-tt lx-num" data-lapall>${lxLap(chMs())}</span></div>`:'';
    if(!live&&!rows.length)return `<div class="lx-lapempty">${lxI('lxFlag',16)}<span>Las vueltas guardan el parcial y el total de cada una.</span></div>`;
    return `<div class="lx-laph"><span>VUELTA</span><span>PARCIAL</span><span>TOTAL</span></div><div class="lx-laps">${live}${rows.map(r=>`<div class="lx-lap ${r.p===best?'lx-best':r.p===worst?'lx-worst':''}"><span class="lx-n">Vuelta ${r.n}${r.p===best?'<em>más rápida</em>':r.p===worst?'<em>más lenta</em>':''}</span><span class="lx-p lx-num">${lxLap(r.p)}</span><span class="lx-tt lx-num">${lxLap(r.t)}</span></div>`).join('')}</div>`;
  };
  const paneCh=()=>{
    const ms=chMs(),run=S.ch.run,idle=!run&&S.ch.acc===0;
    return `<div class="lx-ro" data-roch>${lxRo(ms)}</div><div class="lx-dialwrap">${lxDial({sub:30})}</div>
     <div class="lx-btns"><button class="pl-btn lxg" data-act="chlap" ${idle?'disabled':''}>${run?lxI('lxFlag',17)+'Vuelta':idle?lxI('lxFlag',17)+'Vuelta':lxI('lxReset',17)+'Reiniciar'}</button><button class="pl-btn" data-act="chgo">${run?lxI('lxPause',17)+'Pausar':lxI('lxPlay',16)+(S.ch.acc?'Seguir':'Iniciar')}</button></div>
     <div class="lx-lapbox">${lapsHtml()}</div>`;
  };
  // --- Temporizador
  const key=k=>`<button class="lx-key" data-act="key" data-v="${k}" aria-label="${k==='del'?'Borrar':k}">${k==='del'?lxI('lxBack',22):k}</button>`;
  const paneTm=()=>{
    const st=S.tm.st;
    if(st==='cfg'){const tot=digTotal();
      return `<div class="lx-tdisp ${tot?'':'lx-z'}" aria-live="polite"><span>${dispDig()}</span><small>min : s</small></div>
       <div class="lx-chips lx-presets">${LX_PRESETS.map(s=>chip(lxClock(s),tot===s,'preset',s)).join('')}</div>
       <div class="lx-pad">${['1','2','3','4','5','6','7','8','9','00','0','del'].map(key).join('')}</div>
       <div class="lx-chips lx-alerts"><span class="lx-sec lx-inl">Al terminar</span>${chip('Sonido',S.tm.snd,'snd',1,'lxSound')}${chip('Vibración',S.tm.vib,'vib',1,'lxVib')}</div>
       <button class="pl-btn lx-start" data-act="tmstart" ${tot?'':'disabled'}>${lxI('lxPlay',16)}Iniciar${tot?' '+lxClock(tot):''}</button>`;}
    const left=tmLeft(),tot=S.tm.total,end=new Date(Date.now()+left*1000);
    const hhmm=`${lxPad(end.getHours())}:${lxPad(end.getMinutes())}`;
    if(st==='done')return `<div class="lx-ringbox">${lxTimerRing(1,'done')}<div class="lx-rc"><b class="lx-num">0:00</b><span class="lx-done">Se acabó el tiempo</span></div></div>
       <div class="lx-done">${lxI('bell',16)}<span>${S.tm.snd?'Sonido':''}${S.tm.snd&&S.tm.vib?' y ':''}${S.tm.vib?'vibración':''}${!S.tm.snd&&!S.tm.vib?'Sin aviso':''} al terminar</span></div>
       <div class="lx-btns"><button class="pl-btn lxg" data-act="tmclose">Cerrar</button><button class="pl-btn" data-act="tmrepeat">${lxI('lxReset',16)}Repetir ${lxClock(tot)}</button></div>`;
    return `<div class="lx-ringbox">${lxTimerRing(left/Math.max(1,tot),st)}<div class="lx-rc"><b class="lx-num" data-tmleft>${lxClock(Math.ceil(left))}</b><span>${st==='pause'?'En pausa':'restante'}</span></div></div>
       <div class="lx-tinfo">${st==='pause'?'En pausa · de '+lxClock(tot):`Termina a las <b data-tmend>${hhmm}</b> · de ${lxClock(tot)}`}</div>
       <div class="lx-chips lx-adds">${[[15,'+15 s'],[30,'+30 s'],[60,'+1 min']].map(a=>chip(a[1],false,'tmadd',a[0])).join('')}</div>
       <div class="lx-btns"><button class="pl-btn lxg" data-act="tmcancel">Cancelar</button><button class="pl-btn" data-act="tmpause">${st==='pause'?lxI('lxPlay',16)+'Seguir':lxI('lxPause',17)+'Pausar'}</button></div>`;
  };
  const paint=()=>{
    root.querySelector('.lx-seg').outerHTML=seg();
    const pane=root.querySelector('.lx-pane');pane.dataset.tab=S.tab;
    pane.innerHTML=S.tab===0?paneSes():S.tab===1?paneCh():paneTm();
    tick(true);
  };
  const tick=(force)=>{
    if(!root.isConnected)return;
    const ms=chMs();
    if(S.tab===1){const svg=root.querySelector('.lx-dial');lxDialSet(svg,ms);
      const ro=root.querySelector('[data-roch]');if(ro&&(S.ch.run||force)){const c=lxPartsCs(ms);ro.querySelector('[data-ro]').textContent=`${lxPad(c.m)}:${lxPad(c.s)}`;ro.querySelector('[data-rod]').textContent=','+c.d}
      const ln=root.querySelector('[data-lapnow]');if(ln){const L=S.ch.laps;ln.textContent=lxLap(ms-(L.length?L[L.length-1]:0));root.querySelector('[data-lapall]').textContent=lxLap(ms)}}
    if(S.tab===0){const sm=sesMs();lxDialSet(root.querySelector('.lx-dial'),sm);const r=root.querySelector('[data-roses]');if(r)r.textContent=lxClock(Math.floor(sm/1000))}
    if(S.tab===2&&S.tm.st==='run'){const left=tmLeft();
      if(left<=0){S.tm.st='done';paint();return}
      const arc=root.querySelector('[data-tarc]');if(arc){const C=2*Math.PI*108;arc.setAttribute('stroke-dasharray',`${(C*left/Math.max(1,S.tm.total)).toFixed(1)} ${C.toFixed(1)}`)}
      const t=root.querySelector('[data-tmleft]');if(t)t.textContent=lxClock(Math.ceil(left))}
  };
  const loop=()=>{if(!root.isConnected)return;tick(false);requestAnimationFrame(loop)};
  root.addEventListener('click',e=>{
    const b=e.target.closest('[data-act]');if(!b||b.disabled)return;const a=b.dataset.act,v=+b.dataset.v;
    if(a==='tab'){S.tab=v;paint();return}
    if(a==='chgo'){if(S.ch.run){S.ch.acc+=lxNow()-S.ch.t0;S.ch.run=false}else{S.ch.t0=lxNow();S.ch.run=true}paint()}
    else if(a==='chlap'){if(S.ch.run)S.ch.laps.push(chMs());else{S.ch.acc=0;S.ch.laps=[]}paint()}
    else if(a==='key'){const k=b.dataset.v;if(k==='del')S.tm.digits=S.tm.digits.slice(0,-1);else if((S.tm.digits+k).replace(/^0+/,'').length<=4)S.tm.digits=(S.tm.digits+k).replace(/^0+/,'');paint()}
    else if(a==='preset'){const m=Math.floor(v/60),s=v%60;S.tm.digits=String(m*100+s);paint()}
    else if(a==='snd'){S.tm.snd=!S.tm.snd;paint()}else if(a==='vib'){S.tm.vib=!S.tm.vib;paint()}
    else if(a==='tmstart'||a==='tmrepeat'){S.tm.total=a==='tmrepeat'?S.tm.total:digTotal();S.tm.left=S.tm.total;S.tm.st='run';S.tm.end=lxNow()+S.tm.left*1000;paint()}
    else if(a==='tmpause'){if(S.tm.st==='run'){S.tm.left=tmLeft();S.tm.st='pause'}else{S.tm.st='run';S.tm.end=lxNow()+S.tm.left*1000}paint()}
    else if(a==='tmadd'){if(S.tm.st==='run')S.tm.end+=v*1000;else S.tm.left+=v;S.tm.total+=v;paint()}
    else if(a==='tmcancel'||a==='tmclose'){S.tm.st='cfg';paint()}
    else if(a==='sesgoal'){if(v<0){S.ses.pend={min:75}}else S.ses.pend={min:v};paint()}
    else if(a==='sesdelta'){S.ses.pend={min:Math.max(5,(S.ses.pend?S.ses.pend.min:S.ses.goal)+v)};paint()}
    else if(a==='sesclear'){S.ses.pend={clear:true};paint()}
    else if(a==='sespend'){if(S.ses.pend.clear)S.ses.goal=0;else S.ses.goal=S.ses.pend.min;S.ses.pend=null;paint()}
    else if(a==='sesalert'){S.ses.alert=v;paint()}
  });
  paint();requestAnimationFrame(loop);
}
const lxTimeShell=()=>`<div class="pl-scrim"></div><div class="pl-sheet lx-tsheet ${(state.fs||1)>1.15?'lx-big':''}"><div class="lx-seg"></div><div class="lx-pane"></div></div>`;
const lxTimeScene=(id,title,cfg,notes,o={})=>scene({id,tab:'vivo-resto',title,html:()=>lxSeriesBack({glass:false,time:o.time||'52:10'})+lxTimeShell(),after:ph=>lxTimeApp(ph,cfg),notes});

/* ---------- escenas: cronómetro y temporizador (punto 16) ---------- */
lxTimeScene('lx-t-sesion','Tiempo · Sesión (la hoja de hoy, con esfera)',{tab:0},{
  tags:[['keep','todo lo de hoy se conserva'],['new','esfera de cronómetro'],['move','ahora hay tres pestañas']],
  img:'h16-roadmap',imgCap:'Hoy: la píldora «0:47» abre «Tiempo de sesión», solo con cifras y botones.',
  p:['La píldora de la cápsula sigue abriendo la hoja de tiempo. Ahora la hoja tiene <b>tres pestañas</b>: <b>Sesión</b> (lo que ya existía), <b>Cronómetro</b> y <b>Temporizador</b>. Se abre en Sesión, igual que hoy.',
     'Sesión conserva todo lo que la app calcula: tiempo transcurrido, objetivo, proyección de ritmo («A este ritmo terminas en 58 min», fin estimado, minutos sobre o antes del plan), objetivos 30/45/60/90/Otro, ±5/+15 min, «Quitar objetivo», «Solo esta vez / Siempre», los cuatro avisos y «Revisar cómo acortar lo pendiente» (o «Deshacer el recorte»). Lo único que cambia es la presentación: <b>el tiempo vive en una esfera de cronómetro</b> con segundero y subesfera de 60 min, y el arco del bisel avanza hacia el objetivo (se pone rojo si lo superas, como hoy la píldora).'],
  list:['Prueba: toca un objetivo (aparece «¿Cómo aplicarlo?»), «Quitar objetivo» o cambia los avisos.','Esta pestaña puede desplazarse en pantallas bajas: es una lista de ajustes, no lo esencial.']});
lxTimeScene('lx-t-cron-cero','Cronómetro · a cero',{tab:1},{
  tags:[['new','cronómetro libre']],
  p:['Un cronómetro de verdad, independiente del tiempo de la sesión: <b>esfera con segundero que barre</b>, subesfera de 30 min y la cifra grande con décimas (<b>03:24,6</b>). Sirve para lo que hoy no tiene sitio: cronometrar una isométrica, un tempo, el descanso de un compañero.',
     'Dos botones grandes y siempre en el mismo sitio: el de la derecha es el principal (<b>Iniciar · Pausar · Seguir</b>); el de la izquierda cambia con el estado (<b>Vuelta</b> mientras corre, <b>Reiniciar</b> en pausa). A cero, «Vuelta» está desactivado y la lista explica en una línea para qué sirven las vueltas.'],
  list:['Es interactivo: pulsa Iniciar, marca vueltas, pausa y reinicia.','Sin scroll para lo esencial: la esfera se encoge antes que perder los botones; solo la lista de vueltas se desplaza.']});
lxTimeScene('lx-t-cron-vueltas','Cronómetro · corriendo con vueltas',{tab:1,ch:{run:true,acc:2*60000+13400,laps:[41200,80100,124700]}},{
  tags:[['new','vueltas con parcial y total']],
  p:['Corriendo, con tres vueltas guardadas. La fila superior es la <b>vuelta en curso</b> (su parcial cuenta en vivo); debajo, las anteriores con <b>parcial</b> y <b>total</b>. Con dos o más vueltas, la más rápida se marca en verde y la más lenta en coral, con su etiqueta en texto (no solo color).',
     'Cada vuelta guarda el total acumulado y se calcula el parcial por diferencia: nunca se pierde una vuelta por pausar.'],
  list:['La pestaña «Cronómetro» lleva un punto cuando corre, aunque estés en otra pestaña.']});
lxTimeScene('lx-t-cron-pausa','Cronómetro · en pausa',{tab:1,ch:{run:false,acc:4*60000+8300,laps:LX_LAP_DEMO.reduce((a,p)=>{a.push((a.length?a[a.length-1]:0)+p);return a},[])}},{
  tags:[['new','pausa y reinicio']],
  p:['En pausa: el segundero se queda quieto, el botón principal pasa a <b>Seguir</b> y el de la izquierda a <b>Reiniciar</b> (pone todo a cero y borra las vueltas; no pide confirmación porque el cronómetro es desechable, como en un cronómetro real).',
     'La lista conserva las cinco vueltas con sus marcas; si caben más, se desplaza dentro de su caja sin mover la esfera ni los botones.']});
lxTimeScene('lx-t-temp-config','Temporizador · fijar el tiempo',{tab:2,tm:{digits:'130'}},{
  tags:[['new','temporizador']],
  p:['Se fija con <b>teclado numérico</b> (se escribe de derecha a izquierda: 1, 3, 0 = 01:30) y <b>atajos</b> de los tiempos que más se usan entre series (0:30, 1:00, 1:30, 2:00, 3:00, 5:00). Elegí teclado y no rueda porque tres ruedas obligan a hacer scroll fino para un 2:45; el teclado es exacto y cabe en la hoja sin desplazar.',
     '«Al terminar» elige cómo avisa: <b>Sonido</b> y <b>Vibración</b> (ambos activos por defecto). El botón dice el tiempo que va a contar («Iniciar 1:30») y se desactiva a 00:00.'],
  list:['Interactivo: escribe cifras, borra, usa los atajos, cambia los avisos e inicia.','Sin atajo marcado cuando el tiempo no coincide con ninguno.']});
lxTimeScene('lx-t-temp-corriendo','Temporizador · cuenta atrás',{tab:2,tm:{st:'run',total:90,left:47}},{
  tags:[['new','anillo de cuenta atrás']],
  p:['Cuenta atrás con <b>anillo que se vacía</b> en el sentido del reloj, la cifra grande en el centro y, debajo, la hora a la que termina («Termina a las 18:42»). Atajos para el momento: <b>+15 s, +30 s, +1 min</b> (alargan también el total del anillo). <b>Pausar/Seguir</b> y <b>Cancelar</b> (vuelve a fijar el tiempo).',
     'El temporizador se calcula con la hora de fin, no con un contador: sigue exacto si cierras la hoja, cambias de ejercicio o se apaga la pantalla.'],
  list:['Mientras corre, la cápsula de la sesión lo muestra en la segunda línea de su píldora (ver «Con el descanso en marcha»).']});
lxTimeScene('lx-t-temp-fin','Temporizador · terminado',{tab:2,tm:{st:'done',total:90,left:0}},{
  tags:[['new','aviso al terminar']],
  p:['Al llegar a cero el anillo se completa y late una vez, la cifra queda en 0:00 y aparece <b>«Se acabó el tiempo»</b>. Dice cómo ha avisado (sonido y/o vibración) y ofrece <b>Repetir 1:30</b> (mismo tiempo, un toque) o <b>Cerrar</b> (vuelve a fijar el tiempo).',
     'Si la hoja está cerrada, el aviso es la franja que se ve en «Con el descanso en marcha».']});
scene({id:'lx-t-coexiste',tab:'vivo-resto',title:'Con el descanso en marcha (cápsula y aviso)',
  html:()=>lxRestBack({time:'38:24',ch:'1:12,4',tm:null})+`<div class="lx-banner"><span class="lx-ic">${lxI('lxGlass',20)}</span><div class="lx-tx"><small>TEMPORIZADOR</small><strong>Se acabó el tiempo</strong></div><button class="pl-btn lx-sm">Listo</button></div>`,
  notes:{tags:[['new','cronómetro y temporizador en la cápsula'],['ask','prioridad de avisos']],
   p:['<b>Regla:</b> el cronómetro y el temporizador libres <b>nunca tocan el descanso</b> de la sesión. El descanso sigue mandando en la tarjeta (anillo, ±15 s, siguiente serie); el reloj libre es un asunto aparte que vive en la hoja «Tiempo».',
      '<b>Dónde se ve que algo corre:</b> la píldora de la cápsula crece a dos líneas: arriba el tiempo de sesión (como hoy), debajo, más pequeño, el cronómetro (icono de cronómetro) o el temporizador (icono de reloj de arena) que esté en marcha. Si no corre nada, la píldora es exactamente la de hoy.',
      '<b>Aviso al terminar con la hoja cerrada:</b> una franja de vidrio bajo la cápsula («Se acabó el tiempo» + <b>Listo</b>) que se queda hasta que la cierras (el aviso sonoro y la vibración se repiten tres veces). Tocarla abre la hoja en Temporizador.',
      '<b>Si coinciden</b> el fin del descanso y el del temporizador, suena primero el descanso y el aviso del temporizador entra 1,5 s después: nunca dos sonidos a la vez.'],
   list:['Solo hay un cronómetro y un temporizador libres a la vez.','El cronómetro sigue contando con la pantalla apagada: guarda la hora de inicio, no un contador.','Con «Reducir movimiento» la franja entra sin deslizamiento.']}});
/* ---------- modo visual: Expandir (punto 17) ---------- */
const lxThumbs=(n,sz)=>Array.from({length:n},(_,i)=>`<i class="lx-th" style="--sz:${sz}px;background:linear-gradient(${140+i*30}deg,#3a3f46,#1b1d20)"></i>`).join('');
const lxPillBtn=(label,icon,cls)=>`<button class="lx-tpill ${cls||''}">${icon?lxI(icon,14):''}${label}</button>`;
function lxStage(o={}){
  const big=!!o.big;
  return `<div class="lx-stg ${big?'lx-big':'lx-cmp'}">
   <div class="lx-cam ${o.nocam?'lx-off':''}"><div class="lx-grid"></div>${o.nocam?'<p class="lx-camsg">Activa la cámara para grabar desde esta tarjeta</p>':''}</div>
   ${big?`<div class="lx-modesw"><i class="lx-on">Foto</i><i>Vídeo</i></div>`:lxPillBtn(o.video?'Vídeo':'Foto','','lx-tl')}
   ${big?`<button class="lx-tpill lx-tr">${lxI('info',14)}Ficha</button>`:lxPillBtn('Ficha','','lx-tr')}
   <div class="lx-bl">${big?`<div class="lx-brow"><button class="lx-tpill">Galería</button>${lxThumbs(4,44)}</div><div class="lx-brow"><button class="lx-tpill">Ver álbum</button></div>`
      :`<div class="lx-brow"><i class="lx-cbtn">${lxI('lxImage',16)}</i>${lxThumbs(3,36)}</div><div class="lx-brow"><i class="lx-cbtn">${lxI('book',16)}</i></div>`}</div>
   <div class="lx-shut ${big?'lx-big':''}"><i></i></div></div>`;
}
const lxMediaFace=(o={})=>`<section class="lx-mf ${o.inline?'lx-inline':''}" data-face>
  <div class="lx-mfh"><button class="lx-mb">${lxI('back',14)}<span>Serie 3</span></button><span class="lx-sp"></span><i class="lx-cbtn lx-smb">${lxI('lxFlip',16)}</i><button class="lx-txt">Álbum</button>
   <button class="lx-tpill lx-xp" data-lx="expand" aria-label="Expandir la vista">${lxI('lxExpand',14)}Expandir</button></div>
  ${lxStage({nocam:o.nocam})}
  <div class="lx-mfs"><span>${o.line||'8 completas · 2 parciales'}</span>${o.noan?'':`<button class="lx-lk">Abrir análisis${lxI('fwd',13)}</button>`}</div>
  <button class="pl-btn lxg lx-rev">${lxI('lxRoute',16)}Revisar trayectoria y métricas</button></section>`;
const lxOverlay=(o={})=>`<div class="lx-ovw" data-ovw><div class="lx-ovv"></div>
  <section class="lx-ov"><div class="lx-ovin"><div class="lx-ovh"><div class="lx-ovt"><h3>Modo visual · serie 3</h3><span>95 kg × 6 · RIR 2</span></div>
   <button class="lx-tpill lx-xp" data-lx="fold" aria-label="Plegar a la tarjeta">${lxI('lxFold',14)}Plegar</button></div>
   ${lxStage({big:true,nocam:o.nocam})}
   <div class="lx-ovf"><b>Serie 3 · 95 kg · 6 reps</b><span>La captura queda asociada a esta serie.</span>
    <div class="lx-ovr"><span>${o.line||'8 completas · 2 parciales'}</span>${o.noan?'':'<button class="lx-lk">Ver análisis</button>'}</div>
    <button class="pl-btn lxg" data-lx="fold">Volver al registro</button></div></div></section></div>`;
function lxVisual(phone,startOpen){
  const w=phone.querySelector('[data-ovw]'),ov=w.querySelector('.lx-ov'),face=phone.querySelector('[data-face]');
  const rel=el=>{const p=phone.getBoundingClientRect(),r=el.getBoundingClientRect(),k=p.width/phone.offsetWidth||1;return{l:(r.left-p.left)/k,t:(r.top-p.top)/k,w:r.width/k,h:r.height/k}};
  const tgt=()=>({l:8,t:46,w:phone.offsetWidth-16,h:phone.offsetHeight-46-40});
  const put=(b,rad)=>{ov.style.left=b.l+'px';ov.style.top=b.t+'px';ov.style.width=b.w+'px';ov.style.height=b.h+'px';ov.style.borderRadius=rad+'px'};
  const open=anim=>{
    ov.style.transition='none';put(rel(face),25);w.classList.add('lx-shown');ov.getBoundingClientRect();ov.style.transition='';
    if(anim)requestAnimationFrame(()=>{put(tgt(),30);w.classList.add('lx-open')});
    else{ov.style.transition='none';put(tgt(),30);w.classList.add('lx-open');ov.getBoundingClientRect();ov.style.transition=''}
  };
  const fold=()=>{put(rel(face),25);w.classList.remove('lx-open');setTimeout(()=>w.classList.remove('lx-shown'),340)};
  phone.addEventListener('click',e=>{const b=e.target.closest('[data-lx]');if(!b||b.disabled)return;b.dataset.lx==='expand'?open(true):fold()});
  if(startOpen)open(false);
}
const lxVisBack=()=>lxCap({time:'12:05'})+`<div class="lx-ctx"><div><h2>Peso Muerto<br>Convencional</h2><span>Barra</span></div>${lxI('mic',20)}</div>`;
scene({id:'lx-v-tarjeta',tab:'vivo-resto',title:'Modo visual · tarjeta con «Expandir»',
  html:()=>lxVisBack()+lxMediaFace()+lxDock({done:2,set:'3 / 3'})+lxOverlay(),
  after:ph=>lxVisual(ph,false),
  notes:{tags:[['new','arreglo directo (punto 17)'],['keep','todo lo demás de la cara']],img:'h16-roadmap',imgCap:'Hoy, la cara de la serie. Al girarla al modo visual no hay forma visible de ampliar.',
   p:['La cara «Modo visual» de la tarjeta hoy no trae el botón para expandir: en el código el control existe solo en la vista expandida (el de plegar). <b>Se devuelve «Expandir» a la cara de la tarjeta</b>, escrito con su nombre (icono de flechas + texto, no un glifo suelto) y en la <b>fila de cabecera</b>, a la derecha de «Álbum»: así ampliar y plegar ocupan el mismo lugar en las dos vistas.',
      'Toca <b>Expandir</b>: la tarjeta crece desde su sitio hasta casi toda la pantalla, por encima de todo (velo al 60 %, 320 ms, curva estándar; sin animación con «Reducir movimiento»). <b>Plegar</b>, «Volver al registro» o Atrás la devuelven.'],
   list:['Cabecera de la cara: ‹ Serie 3 · cambiar cámara · Álbum · Expandir.','Zona táctil de 44 dp aunque la píldora mida 32 dp.','Grabando vídeo, «Expandir» queda desactivado (la cámara está cedida al grabador): es el comportamiento actual del código.']}});
scene({id:'lx-v-expandido',tab:'vivo-resto',title:'Modo visual · expandido, con «Plegar»',
  html:()=>lxVisBack()+lxMediaFace()+lxDock({done:2,set:'3 / 3'})+lxOverlay(),
  after:ph=>lxVisual(ph,true),
  notes:{tags:[['keep','vista expandida actual'],['new','«Plegar» con nombre']],
   p:['La vista expandida (casi toda la pantalla, 8 dp de margen lateral, radio 30) con lo que ya tiene: título «Modo visual · serie 3», el detalle de la serie, «Álbum», el escenario grande con selector Foto/Vídeo, «Ficha», «Galería», miniaturas, «Ver álbum» y el disparador, y el pie con la serie asociada, el resultado del análisis y «Volver al registro».',
      'El único cambio: el botón de plegar pasa de un círculo con un glifo a una <b>píldora «Plegar»</b> con icono, gemela de «Expandir».'],
   list:['Toca «Plegar» para ver la animación inversa y «Expandir» para volver.']}});
scene({id:'lx-v-sin-camara',tab:'vivo-resto',title:'Modo visual · sin cámara',
  html:()=>lxVisBack()+lxMediaFace({nocam:true,noan:true,line:'Se guarda en el álbum con la serie 3 · 95 kg × 6 · RIR 2.'})+lxDock({done:2,set:'3 / 3'})+lxOverlay({nocam:true,noan:true,line:'Sin análisis todavía'}),
  after:ph=>lxVisual(ph,false),
  notes:{tags:[['keep','mensaje y línea actuales']],
   p:['Sin permiso de cámara, el escenario dice «Activa la cámara para grabar desde esta tarjeta» y la línea de abajo recuerda dónde se guarda la captura. «Expandir» sigue disponible: la vista expandida muestra el mismo mensaje y permite abrir el álbum y la ficha.']}});
/* ---------- modo Lista: desvanecido en los extremos y cápsula de vidrio (punto 19) ---------- */
const LX_EX=[['Peso Muerto Convencional · Barra'],['Sentadilla "Belt Squat"'],['Hip Thrust · Barra'],['Prensa Inclinada'],['Curl Femoral Tumbado'],['Elevación de Gemelos']];
const lxRow=(n)=>`<div class="lx-lr"><span class="lx-ln">${n}</span><span class="lx-lt">Efectiva</span><span class="lx-lv"><b>—</b> kg <em>×</em> <strong>5</strong></span><span class="lx-lrir">—</span><span class="lx-lck">${lxI('check',17)}</span></div>`;
const lxMod=(name,rows)=>`<section class="lx-lm"><div class="lx-lh"><div><h3>${name}</h3><span>Series 0/3</span></div><i>${lxI('up',17)}</i><i>${lxI('more',18)}</i></div>${rows||[1,2,3].map(n=>lxRow(n)).join('')}</section>`;
const lxList=(o={})=>`<div class="lx-list" data-scroll="${o.scroll||0}"><div class="lx-lint"><span>REGISTRO DE LA SESIÓN</span><span class="lx-num">0 / 18</span></div>
  ${LX_EX.map((e,i)=>i===(o.editor??-1)?lxMod(e[0],lxRow(1)+`<div class="lx-ed"><div class="lx-edh"><span>Modo visual · serie 2</span></div>${lxMediaFace({inline:true})}</div>`+lxRow(3)):lxMod(e[0])).join('')}</div>`;
const lxListAfter=(ph,opt)=>{const l=ph.querySelector('.lx-list');if(l)l.scrollTop=+l.dataset.scroll||0;if(opt&&opt.visual)lxVisual(ph,false)};
const lxDims=()=>`<div class="lx-meas" aria-hidden="true">
  <div class="lx-mtop"><span class="ann">Arriba · 24 dp desde y = 0</span></div>
  <div class="lx-mcap"><span class="ann">Isla de vidrio con desenfoque</span></div>
  <div class="lx-mold"><span class="ann lx-bad2">Hoy: 72 dp que acaban a ~40 dp del borde</span></div>
  <div class="lx-mbot"><span class="ann">Abajo · 28 dp hasta el borde físico</span></div></div>`;
const LX_LIST_TAGS=[['keep','lo que ya hace la Lista'],['new','arreglo directo (punto 19)']];
const LX_HOY={img:'h18-lista-vivo',imgCap:'Hoy: la cápsula tapa la lista (no deja pasar nada por debajo) y el desvanecido de abajo empieza a media pantalla.'};
scene({id:'lx-l-reposo',tab:'vivo-resto',title:'Modo Lista · en reposo',
  html:()=>lxList()+lxCap({glass:true,time:'5:45'}),after:ph=>lxListAfter(ph),
  notes:{tags:LX_LIST_TAGS,...LX_HOY,
   p:['Arriba del todo, la lista empieza <b>14 dp por debajo de la cápsula</b>, así que en reposo el primer ejercicio queda entero a la vista (igual que hoy). La cápsula pasa a ser <b>vidrio de verdad</b> (se ve el contenido borroso a través de ella en cuanto haces scroll) y abajo la lista llega hasta el borde de la pantalla: <b>no hay hueco negro</b> entre el último ejercicio y el borde.',
      'El contrato exacto está en «Modo Lista · medidas en dp».']}});
scene({id:'lx-l-scroll',tab:'vivo-resto',title:'Modo Lista · haciendo scroll',
  html:()=>lxList({scroll:330})+lxCap({glass:true,time:'5:45'}),after:ph=>lxListAfter(ph),
  notes:{tags:LX_LIST_TAGS,
   p:['Con la lista desplazada: las filas <b>pasan por debajo de la cápsula</b> y se ven desenfocadas a través del vidrio (antes la cápsula era opaca y la lista se cortaba contra ella). El desvanecido de arriba ocupa solo los <b>24 dp pegados al borde superior</b> (bajo la barra de estado), no la zona de la cápsula; el de abajo son <b>28 dp pegados al borde inferior</b>.',
      'La lista es interactiva: desplázala y mira cómo entran y salen las filas en los dos extremos.']}});
scene({id:'lx-l-medidas',tab:'vivo-resto',title:'Modo Lista · medidas en dp (contrato)',
  html:()=>lxList({scroll:330})+lxCap({glass:true,time:'5:45'})+lxDims(),after:ph=>lxListAfter(ph),
  notes:{tags:[['new','contrato para implementar']],
   p:['Las bandas amarillas son las medidas que se implementan; la banda roja punteada es lo que hay hoy.'],
   listTitle:'Contrato (dp, para cualquier pantalla)',
   list:['<b>La lista ocupa toda la pantalla</b> (sin el padding inferior <code>dockBottomClearance</code> sobre la lista): se dibuja bajo la barra de estado, bajo la cápsula y bajo la barra de gestos.','<b>contentPadding</b>: arriba = alto de la cabecera (barra de estado + cápsula) + 14 dp; abajo = inset de navegación + 24 dp (el último ejercicio puede subir hasta quedar entero sobre el desvanecido).','<b>Desvanecido superior: 24 dp</b>, de alfa 0 en y = 0 a alfa 1 en y = 24 dp. <b>Inferior: 28 dp</b>, de alfa 1 en (alto − 28 dp) a alfa 0 en el borde físico. Hoy: 72 dp que terminan ~40 dp antes del borde.','<b>Cápsula en Lista</b>: la misma geometría (radio 30, 60 dp, 10 dp de margen) pero vidrio: <code>hazeEffect</code> con desenfoque de 22 dp, tinte #1B1B1D al ~68 %, borde blanco al 12 % y reflejo interior. La lista se registra como <code>hazeSource</code> <b>antes</b> de aplicar la máscara (la cápsula desenfoca las filas completas, no las ya desvanecidas).','<b>Tarjetas no cambia</b>: allí la cápsula sigue siendo gris plano con sombra (no hay nada que pase por debajo).','Elementos flotantes de la Lista (descanso minimizado, voz) quedan por encima del desvanecido, a su separación actual del borde.']}});
scene({id:'lx-v-lista',tab:'vivo-resto',title:'Modo visual dentro de la Lista',
  html:()=>lxList({editor:1,scroll:120})+lxCap({glass:true,time:'5:45'})+lxOverlay(),after:ph=>lxListAfter(ph,{visual:true}),
  notes:{tags:[['move','«Ampliar» pasa a ser «Expandir»'],['out','sin botón duplicado']],
   p:['En la Lista, tocar «Modo visual» abre la misma cara que en la tarjeta dentro de la fila (350 dp de alto). Como esa cara ya lleva su propio <b>Expandir</b> en la cabecera, el «Ampliar» suelto que añadía la fila es un duplicado: <b>se quita</b> y la fila solo conserva el título «Modo visual · serie 2».',
      'Un solo nombre para la misma acción (el que usas tú: «Expandir»). El estado expandido y el plegado son los de la tarjeta, por encima de la Lista.']}});
/* ---------- sesión completada (punto 18) ---------- */
const lxFmtN=(v,d=0)=>v.toFixed(d).replace('.',',').replace(/\B(?=(\d{3})+(?!\d))/g,'.');
const LX_BASE={name:'Legs II · Semana 1',date:'Mié 29 oct',min:52,sets:18,planned:18,vol:8420,delta:'+340 kg',hist:[7380,7640,7510,7790,8080],rpe:7.5,rec:0,pr:null,cardio:null,
  rows:[[.62,.68,.72],[.5,.55,.6],[.8,.86,.92],[.42,.45,.48],[.34,.36,.38]],
  mus:[['Glúteos',11.5,9],['Cuádriceps',8,6],['Isquiotibiales',7.5,3],['Espalda baja',4,0],['Aductores',3,3]],
  lifts:[['Hip Thrust',5,'120 kg × 8'],['Peso Muerto',2.5,'160 kg × 5'],['Belt Squat',0,'100 kg × 10']],calib:null,first:false};
const LX_SES={
  normal:LX_BASE,
  record:{...LX_BASE,rec:1,pr:{name:'Peso Muerto Convencional',e1:'182',prev:'177,5'},delta:'+620 kg',hist:[7380,7640,7510,7790,7800],vol:8420,lifts:[['Peso Muerto',5,'160 kg × 5'],['Hip Thrust',5,'120 kg × 8'],['Belt Squat',0,'100 kg × 10']]},
  cardio:{...LX_BASE,name:'Pierna + cardio · Semana 1',min:74,sets:21,planned:21,vol:7260,delta:'−120 kg',hist:[7600,7380,7410,7500,7390],rpe:8,cardio:{type:'Cinta',min:20,km:'2,4',hr:148,kcal:190},
    mus:[['Cuádriceps',9,7],['Glúteos',8,6],['Isquiotibiales',5,3],['Pantorrillas',3,3],['Core',2,0]],lifts:[['Sentadilla',2.5,'110 kg × 6'],['Prensa',0,'260 kg × 10']]},
  corta:{...LX_BASE,name:'Press militar rápido',min:12,sets:3,planned:3,vol:1080,delta:null,hist:[],rpe:null,first:true,rows:[[.6,.66,.72]],
    mus:[['Deltoides',3,3],['Tríceps',1.5,0]],lifts:[]},
  vacia:{...LX_BASE,min:3,sets:0,planned:18,vol:0,delta:null,hist:[],rpe:null,mus:[],lifts:[],rows:[],first:true,empty:true}
};
// La portada sin filtros de ruido ni desenfoques (pesan mucho y el arte solo necesita las líneas).
const lxLite=svg=>svg.replace(/<filter[\s\S]*?<\/filter>/g,'').replace(/<rect[^>]*filter="[^"]*"[^>]*\/>/g,'').replace(/<circle[^>]*filter="[^"]*"[^>]*\/>/g,'').replace(/ filter="url\(#[^"]*\)"/g,'');
// Arte del encabezado: una línea por ejercicio con un pico por serie (el lenguaje de la portada «sesión»), solo trazos, ligero.
function lxRidges(rows,pr){
  const W=360,H=200;let s='';
  rows.forEach((sets,k)=>{const y0=70+k*26,n=sets.length,pts=[];
    for(let x=0;x<=W;x+=4){let h=0;sets.forEach((v,j)=>{const cx=60+j*(240/Math.max(1,n-1));h+=v*62*Math.exp(-Math.pow((x-cx)/(11+k),2))});pts.push((x?'L':'M')+x+' '+(y0-h).toFixed(1))}
    s+=`<path class="ln" pathLength="1" d="${pts.join('')}" fill="none" stroke="${k===pr?'#f49a6e':'#f2eee6'}" stroke-width="${k===pr?2:1.1}" stroke-linecap="round" stroke-linejoin="round" opacity="${k===pr?1:.5-k*.04}"/>`});
  return `<svg viewBox="0 0 ${W} ${H}" width="100%" height="100%" preserveAspectRatio="xMidYMin slice" aria-hidden="true">${s}</svg>`;
}
function lxFinTier(){const eff=state.size[1]*.94/(state.fs||1);return eff>=760?'A':eff>=600?'B':'C'}
function lxRing(frac,center,cls=''){
  const C=2*Math.PI*22;
  return `<svg class="lx-rg ${cls}" viewBox="0 0 56 56" aria-hidden="true"><circle cx="28" cy="28" r="22" fill="none" stroke="#ffffff12" stroke-width="5"/>
   <circle class="lx-rgp" cx="28" cy="28" r="22" fill="none" stroke-width="5" stroke-linecap="round" stroke-dasharray="${(C*Math.max(0,Math.min(1,frac))).toFixed(1)} ${C.toFixed(1)}" ${frac>0?'':'stroke-opacity="0"'} transform="rotate(-90 28 28)" style="--len:${C.toFixed(1)}"/></svg><span class="lx-rgc">${center}</span>`;
}
function lxSpark(pts){
  if(pts.length<2)return '';
  const w=116,h=46,lo=Math.min(...pts),hi=Math.max(...pts),sp=(hi-lo)||1;
  const X=i=>6+i*(w-14)/(pts.length-1),Y=v=>h-8-(v-lo)/sp*(h-18);
  const d=pts.map((v,i)=>(i?'L':'M')+X(i).toFixed(1)+' '+Y(v).toFixed(1)).join('');
  const lx=X(pts.length-1),ly=Y(pts[pts.length-1]);
  return `<svg class="lx-spark draw" viewBox="0 0 ${w} ${h}" aria-hidden="true"><defs><linearGradient id="lxsg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f2eee6" stop-opacity=".2"/><stop offset="1" stop-color="#f2eee6" stop-opacity="0"/></linearGradient></defs>
   <path d="${d}L${lx} ${h}L${X(0)} ${h}Z" fill="url(#lxsg)" class="lx-sparea"/><path class="ln" pathLength="1" d="${d}" fill="none" stroke="#f2eee6" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
   <circle class="lx-spdot" cx="${lx.toFixed(1)}" cy="${ly.toFixed(1)}" r="3.4" fill="#f2eee6"/><circle class="lx-spdot" cx="${lx.toFixed(1)}" cy="${ly.toFixed(1)}" r="7" fill="none" stroke="#f2eee6" stroke-opacity=".3"/></svg>`;
}
function lxBurst(){
  const r=rng(9);let s='';
  for(let i=0;i<16;i++){const a=i/16*Math.PI*2,r0=15+r()*3,r1=24+r()*10;s+=`<path class="ln" pathLength="1" d="M${(30+Math.cos(a)*r0).toFixed(1)} ${(30+Math.sin(a)*r0).toFixed(1)}L${(30+Math.cos(a)*r1).toFixed(1)} ${(30+Math.sin(a)*r1).toFixed(1)}" stroke="#f49a6e" stroke-width="${i%4===0?1.8:1}" stroke-linecap="round" opacity="${i%4===0?1:.6}"/>`}
  return `<svg class="lx-burst draw" viewBox="0 0 60 60" aria-hidden="true">${s}</svg>`;
}
function lxFinSheet(k,o={}){
  const D=LX_SES[k],big=(state.fs||1)>1.15,empty=!!D.empty;
  const roomy=!o.tier&&state.size[1]*.94/(state.fs||1)>=840;
  let T=o.tier||lxFinTier();if(!o.tier&&T==='B'&&(D.pr||D.cardio||o.error))T='C'; // récord, cardio o error ocupan sitio: bajan un nivel
  const tile=(frac,center,label,sub,cls,act)=>`<button class="lx-tile lx-rise" ${act?`data-fa="${act}"`:''} style="--d:${.55+(o.i=(o.i||0)+1)*.07}s"><span class="lx-ringw">${lxRing(frac,center,cls)}</span><b>${label}</b><small>${sub}</small></button>`;
  const hist=D.hist.concat(empty?[]:[D.vol]);
  const effort=o.effort??D.rpe;
  const tiles=[
    tile(empty?0:Math.min(1,D.min/60),`<span class="lx-num">${D.min}</span>`,'Duración','minutos',''),
    tile(D.planned?D.sets/D.planned:0,`<span class="lx-num">${D.sets}</span>`,'Series',`de ${D.planned}`,''),
    tile(effort!=null?effort/10:0,`<span class="lx-num">${effort!=null?String(effort).replace('.',','):'—'}</span>`,'Esfuerzo',effort!=null?'sRPE':'sin valorar','','notes'),
    tile(D.rec?1:0,D.rec?`<span class="lx-num">${D.rec}</span>`:`<span class="lx-num">0</span>`,'Récords',D.rec?'e1RM':'esta vez no','lx-prg'+(D.rec?' lx-on':''))
  ].join('');
  const mx=Math.max(1,...D.mus.map(m=>m[1]));
  const bars=D.mus.slice(0,roomy?5:4).map((m,i)=>`<div class="lx-mu" style="--d:${.7+i*.06}s"><span>${m[0]}</span><i><u style="width:${(m[1]/mx*100).toFixed(0)}%"></u><s style="width:${(m[2]/mx*100).toFixed(0)}%"></s></i><b class="lx-num">${String(m[1]).replace('.',',')}</b></div>`).join('');
  const stack=`<div class="lx-stk">${D.mus.slice(0,5).map((m,i)=>`<i style="flex:${m[1]};opacity:${1-i*.17}"></i>`).join('')}</div><div class="lx-stl">${D.mus.slice(0,3).map(m=>`<span>${m[0]} <b class="lx-num">${String(m[1]).replace('.',',')}</b></span>`).join('')}</div>`;
  const nl=2,pos=D.lifts.filter(l=>l[1]>0).slice(0,nl),eqs=D.lifts.filter(l=>l[1]===0).slice(0,nl-pos.length);
  const lifts=pos.map(l=>`<span class="lx-lc"><i>${lxI('trendUp',14)}</i>${l[0]}<b class="lx-num">+${String(l[1]).replace('.',',')} kg</b></span>`).join('')+eqs.map(l=>`<span class="lx-lc lx-eq">${l[0]}<b class="lx-num">=</b></span>`).join('');
  const delta=D.delta?`<span class="lx-dl ${D.delta[0]==='−'?'lx-dn':''}">${lxI(D.delta[0]==='−'?'trendDown':'trendUp',14)}${D.delta} <small>vs. última vez</small></span>`:`<span class="lx-dl lx-nw">${empty?'Sin series todavía':'Primera vez con esta sesión'}</span>`;
  const pr=D.pr?`<div class="lx-prs lx-rise" style="--d:.95s"><span class="lx-prb">${lxBurst()}${lxI('trophy',20)}</span><div><strong>Récord · ${D.pr.name}</strong><span class="lx-num">e1RM ${D.pr.e1} kg · antes ${D.pr.prev} kg</span></div></div>`:'';
  const car=D.cardio?`<div class="lx-car lx-rise" style="--d:.85s"><span class="lx-cai">${lxI('lxRun',18)}</span><b>${D.cardio.type}</b><span class="lx-num"><i>${D.cardio.min}</i> min</span><span class="lx-num"><i>${D.cardio.km}</i> km</span><span class="lx-num"><i>${D.cardio.hr}</i> lpm</span><span class="lx-num"><i>${D.cardio.kcal}</i> kcal</span></div>`:'';
  const mus=D.mus.length&&!empty?`<div class="lx-musm lx-rise" style="--d:.7s"><div class="lx-mush"><span class="lx-eb">MÚSCULOS</span><em><u></u>directas<u class="lx-lgt"></u>total</em></div><div class="lx-musb">${bars}</div><div class="lx-muss">${stack}</div></div>`:'';
  const noteBadge=o.notesText?`<small>${lxI('check',11)}</small>`:'';
  const saving=!!o.saving,err=!!o.error;
  return `<div class="pl-scrim"></div><section class="pl-sheet lx-fin lx-t${T}${big?' lx-big':''}${(D.pr||D.cardio)?' lx-dense':''}${empty?' lx-emp':''}${o.error?' lx-haserr':''}" data-fin data-tier="${T}">
   <div class="lx-art draw">${lxRidges(D.rows,D.rec?0:-1)}</div>
   <div class="lx-fhd"><div class="lx-fey"><i>${lxI('check',12)}</i>SESIÓN COMPLETADA</div><h2>${D.name}</h2><span>${D.date} · <b class="lx-num">${D.min}</b> min · ${empty?'sin series':D.sets+' series'}</span></div>
   <div class="lx-fbody">
    <div class="lx-dm lx-rise" style="--d:.2s"><div class="lx-vol"><div class="lx-vl"><span class="lx-eb">VOLUMEN</span>
      <div class="lx-vn">${empty?'<b class="lx-num">0</b>':`<b class="lx-num" data-count="${D.vol}">${lxFmtN(D.vol)}</b>`}<small>kg</small></div>${delta}</div>${empty?'':lxSpark(hist)}</div>
     <div class="lx-tiles">${tiles}</div></div>
    ${car}${pr}${mus}
    ${D.lifts.length&&!empty?`<div class="lx-lifts lx-rise" style="--d:.9s"><div>${lifts||'<span class="lx-lc lx-eq">Sin mejoras comparables todavía</span>'}</div></div>`:''}
   </div>
   <div class="lx-fact">
    ${empty?'':`<div class="lx-sto lx-rise" style="--d:1s"><button class="lx-stb" data-fa="story" aria-label="Preparar historia"><span class="lx-sth">${lxStory('gen','42px')}</span><span class="lx-stt"><strong>Preparar historia</strong><span>General · mejoras y duración</span></span>${lxI('fwd',16)}</button>
       <label class="lx-stw"><span>Compartir al guardar</span><i class="lx-sw ${o.share?'lx-on':''}" data-fa="share"><u></u></i></label></div>`}
    <div class="lx-qk lx-rise" style="--d:1.05s"><button data-fa="notes">${lxI('lxNote',17)}Notas${noteBadge}</button><button data-fa="notes">${lxI('fire',17)}Valorar${effort!=null?` <b class="lx-num">${String(effort).replace('.',',')}</b>`:''}</button><button>${lxI('lxImage',17)}Fotos${o.photos?` <b class="lx-num">${o.photos}</b>`:''}</button></div>
    ${err?`<p class="lx-err" role="alert">${lxI('info',15)}No se pudo guardar la sesión. Tu entreno sigue en curso: inténtalo de nuevo.</p>`:''}${empty?`<p class="lx-blk">Registra al menos una serie para terminar o abandona sin guardar.</p>`:''}
    <button class="pl-btn lx-save ${empty?'lx-dis':''}" ${empty||saving?'disabled':''} data-fa="save">${saving?'<i class="lx-spin"></i>Guardando…':'Guardar sesión'}</button>
    <button class="lx-bk ${empty?'lx-pri':''}" ${saving?'disabled':''}>Volver a la sesión</button></div>
   ${lxNotesPanel(o)}</section>`;
}
function lxNotesPanel(o){
  const tasks=[['Estirar cuádriceps 5 min',true],['Subir peso del hip thrust la semana próxima',false]];
  const v=o.effort??7.5;
  return `<div class="lx-np ${o.panel?'lx-npon':''}" data-np><div class="lx-nph"><button class="lx-nback" data-fa="back" aria-label="Volver al resumen">${lxI('back',18)}</button><h3>Anotaciones</h3></div>
   <div class="lx-npb"><div class="lx-npg"><span class="lx-eb">NOTAS</span><div class="lx-ta"><span>${o.notesText||'Nota de la sesión'}</span></div></div>
   <div class="lx-npg"><div class="lx-npr"><span class="lx-eb">ESFUERZO DE LA SESIÓN</span><b class="lx-num" data-ev>${String(v).replace('.',',')}</b></div>
    <div class="lx-scale" data-scale>${Array.from({length:11},(_,i)=>`<i class="${i<=Math.round(v)?'lx-on':''}" data-v="${i}"><u>${i}</u></i>`).join('')}</div><div class="lx-scl"><span>0</span><span>10</span></div></div>
   <div class="lx-npg"><span class="lx-eb">TAREAS</span>${tasks.map(t=>`<div class="lx-tk ${t[1]?'lx-done':''}"><i>${t[1]?lxI('check',13):''}</i><span>${t[0]}</span><em>${lxI('close',15)}</em></div>`).join('')}<div class="lx-tk lx-add"><span>Añadir tarea</span><button>Añadir</button></div></div>
   <button class="lx-row2"><span class="lx-th2">${lxI('lxImage',18)}</span><span>Fotos y medios de la sesión</span>${lxI('fwd',15)}</button></div>
   <button class="pl-btn lx-save" data-fa="back">Listo</button></div>`;
}
function lxFinApp(ph,opt){
  const root=ph.querySelector('[data-fin]');if(!root)return;
  if(document.documentElement.classList.contains('solo'))root.querySelectorAll('[data-count]').forEach(el=>{el.textContent=lxFmtN(+el.dataset.count,+(el.dataset.dec||0));el.removeAttribute('data-count')});
  animateIn(root);
  const np=root.querySelector('[data-np]');
  root.addEventListener('click',e=>{
    const b=e.target.closest('[data-fa]');if(!b)return;const a=b.dataset.fa;
    if(a==='notes')np.classList.add('lx-npon');
    else if(a==='back')np.classList.remove('lx-npon');
    else if(a==='share')b.classList.toggle('lx-on');
  });
  const sc=root.querySelector('[data-scale]');
  if(sc){const set=v=>{sc.querySelectorAll('i').forEach(i=>i.classList.toggle('lx-on',+i.dataset.v<=v));root.querySelector('[data-ev]').textContent=String(v);};
    const hit=ev=>{const r=sc.getBoundingClientRect(),k=r.width/sc.offsetWidth||1,x=(ev.clientX-r.left)/k;set(Math.max(0,Math.min(10,Math.round(x/(sc.offsetWidth/11-.0001)-.5))))};
    let down=false;sc.addEventListener('pointerdown',ev=>{down=true;hit(ev)});sc.addEventListener('pointermove',ev=>{if(down)hit(ev)});window.addEventListener('pointerup',()=>down=false)}
}
const lxFinScene=(id,title,k,notes,o={})=>scene({id,tab:'vivo-resto',title,html:()=>lxSeriesBack({time:'52:10'})+lxFinSheet(k,o),after:ph=>lxFinApp(ph),notes});
const LX_FIN_HOY='<b>Hoy</b> la hoja es un panel con tres pestañas (Resumen, Progreso, Anotaciones) que se desplaza, con un interruptor «Compartir al guardar» y dos botones al pie.';
lxFinScene('lx-f-normal','Sesión completada · normal','normal',{
  tags:[['new','rediseño total, sin scroll'],['keep','todo lo que ofrece hoy']],
  p:['<b>Regla del usuario cumplida:</b> todo lo importante se ve de un vistazo y <b>no hay scroll</b>. Lo que el resumen de hoy calcula (tonelaje y su diferencia con la última vez que hiciste esta sesión, series, duración, sRPE, volumen por músculo con directas e indirectas, mejoras por ejercicio y récords e1RM) se muestra como forma, no como frases: un número grande, una línea de tendencia, cuatro anillos y barras.',
     LX_FIN_HOY+' Las tres pestañas desaparecen: <b>Progreso</b> se funde en el resumen (la línea de volumen, las mejoras y los récords) y <b>Anotaciones</b> pasa a un panel que se abre desde «Notas» o «Valorar» (ver «Anotaciones»).',
     '<b>Entrada (≈ 1,6 s):</b> la hoja sube; las líneas de la portada «sesión» (una por ejercicio, un pico por serie) se dibujan en el encabezado; las cifras cuentan hacia arriba; los anillos se llenan; la línea de volumen se traza y las barras de músculos crecen en cascada. Con «Reducir movimiento» aparece todo ya en su sitio.'],
  listTitle:'Qué es cada cosa',
  list:['<b>Volumen</b>: el tonelaje de la sesión (hoy «TONELAJE») y su diferencia con la última vez de la misma sesión; la línea son tus últimas sesiones iguales (hasta 8).','<b>Anillos</b>: Duración (vuelta de reloj de 60 min, o contra el tiempo objetivo si lo había), Series (hechas de las planificadas), Esfuerzo (sRPE de 0 a 10; toca para valorar) y Récords (e1RM nuevos; coral si hay).','<b>Músculos</b>: series por músculo, la barra clara es el total y la crema las directas (como hoy).','<b>Mejoras</b>: lo que subiste frente a la última vez, ordenado por e1RM.','<b>Preparar historia</b>, <b>Compartir al guardar</b>, <b>Notas</b>, <b>Valorar</b>, <b>Fotos</b>, <b>Guardar sesión</b> y <b>Volver a la sesión</b>: todo lo de hoy, sin quitar nada.']});
lxFinScene('lx-f-record','Sesión completada · con récord','record',{
  tags:[['new','celebración contenida']],
  p:['Cuando la sesión trae un récord de e1RM, aparece <b>una sola franja en coral</b> bajo los números: trofeo con un pequeño estallido de líneas finas que se dibujan (el mismo gesto de las portadas, nada de confeti) y la cifra («e1RM 182 kg · antes 177,5 kg»). El anillo de Récords se cierra en coral. Es el único sitio de la hoja con color de acento.',
     'Si hay más de un récord, la franja nombra el mayor y el anillo cuenta el total; los demás se ven en «Mejoras». Los datos son los hitos <code>pr_e1rm</code> que ya calcula la sesión.']});
lxFinScene('lx-f-cardio','Sesión completada · con cardio','cardio',{
  tags:[['new','el cardio ahora cuenta en el cierre']],
  p:['Hoy el cierre ignora el cardio (el tonelaje y las series son de fuerza). En una sesión mixta aparece <b>una fila de cardio</b> bajo los anillos, con el icono en azul de cardio: máquina, minutos, distancia, pulso medio y kilocalorías, lo que ya se guarda en cada serie de cardio (<code>timeSeconds</code>, <code>distanceKm</code>, <code>avgHeartRate</code>, <code>calories</code>).',
     'Los anillos de Duración y Series siguen siendo de toda la sesión; el volumen y los músculos, solo de la fuerza. Una sesión solo de cardio no muestra volumen ni músculos: la fila de cardio ocupa su sitio.'],
  list:['Cada dato se omite si la sesión no lo midió (sin reloj, sin pulso).']});
lxFinScene('lx-f-corta','Sesión completada · sesión corta','corta',{
  tags:[['new','primera vez sin comparación']],
  p:['Sesión corta y sin historial (12 min, 3 series, un ejercicio): no hay con qué comparar, así que en lugar de una diferencia vacía dice <b>«Primera vez con esta sesión»</b> y no se dibuja la línea (una línea de un solo punto no dice nada). El esfuerzo queda en «—» con «toca para valorar». Los músculos son los que trabajó.',
     'La hoja encoge sola: sin la franja de mejoras ni la línea, el contenido se reparte más aire.']});
lxFinScene('lx-f-vacia','Sesión completada · sin series registradas','vacia',{
  tags:[['keep','mismo bloqueo de hoy'],['new','salida clara']],
  p:['Sin ninguna serie registrada no se puede guardar (<code>isFinishBlockedForEmptySession</code>). Hoy solo se ve «Guardar» apagado y un aviso pequeño. Aquí: los anillos vacíos, el mensaje exacto de la app («Registra al menos una serie para terminar o abandona sin guardar.») y <b>«Volver a la sesión» como botón principal</b>, porque es la única salida lógica. «Guardar sesión» queda visible pero apagado (38 %).',
     'No hay historia que compartir ni músculos que enseñar: se ocultan.']});
lxFinScene('lx-f-guardando','Sesión completada · guardando','normal',{
  tags:[['keep','estado «Guardando…» de hoy']],
  p:['Mientras guarda (<code>saving</code>), el botón principal muestra un indicador y «Guardando…», todo lo demás queda inactivo y «Volver a la sesión» se apaga para no interrumpir. Al terminar la hoja se cierra y, si «Compartir al guardar» estaba activo, se abre el reparto de la historia.']},{saving:true,share:true});
lxFinScene('lx-f-error','Sesión completada · error al guardar','normal',{
  tags:[['keep','mensaje de hoy']],
  p:['Si guardar falla, se queda en la hoja con el texto exacto de la app («No se pudo guardar la sesión. Tu entreno sigue en curso: inténtalo de nuevo.») en rojo suave sobre el botón, que vuelve a estar activo. Nunca se enseña el texto técnico de la excepción.']},{error:true});
scene({id:'lx-f-notas',tab:'vivo-resto',title:'Sesión completada · anotaciones (notas, valorar, tareas)',
  html:()=>lxSeriesBack({time:'52:10'})+lxFinSheet('normal',{panel:true,notesText:'Buen día. El hip thrust se movió rápido; bajar a 90 s de descanso la próxima.',photos:3}),after:ph=>lxFinApp(ph),
  notes:{tags:[['move','antes: pestaña «Anotaciones»'],['keep','notas, tareas, esfuerzo, fotos']],
   p:['Lo que hoy es la tercera pestaña pasa a ser un <b>panel que se desliza desde la derecha</b> sobre la propia hoja (se abre con «Notas», «Valorar» o tocando el anillo de Esfuerzo; «‹» o «Listo» lo cierran). Misma altura, sin cambiar de hoja.',
      '<b>Esfuerzo de la sesión (sRPE 0–10)</b> deja de ser once cajitas planas: una escala de barras que suben, que se arrastra con el dedo (el valor sale grande arriba a la derecha) y que se ve reflejada al instante en el anillo del resumen. Toca la barra marcada otra vez para dejarlo sin valorar.',
      'Notas, tareas (marcar, quitar, añadir) y «Fotos y medios de la sesión» son los de hoy. Las tareas largas se desplazan dentro del panel; lo demás cabe.'],
   list:['Es interactivo: arrastra la escala, abre y cierra el panel.','La insignia ✓ de «Notas» indica que ya hay texto.']}});
lxFinScene('lx-f-nivel-b','Sesión completada · nivel B (pantalla baja)','normal',{
  tags:[['new','degradación sin scroll']],
  p:['La hoja no hace scroll nunca: <b>se degrada por niveles según el alto útil dividido por la escala de texto del sistema</b>. Esta escena fuerza el nivel B para que lo veas a cualquier tamaño; en 360 × 740 al 100 % se activa solo.',
     '<b>Nivel A</b> (≥ 760 dp efectivos, p. ej. 360 × 880 al 100 %): todo. <b>Nivel B</b> (600–759, p. ej. 360 × 740): los músculos pasan a una barra apilada con los tres primeros, las mejoras a dos etiquetas y la miniatura de la historia se encoge. <b>Nivel C</b> (&lt; 600, p. ej. 360 × 740 al 130 %): ver la escena siguiente.',
     'Lo que se esconde sigue accesible: músculos y mejoras salen en el historial de la sesión una vez guardada.'],
  list:['Medidas efectivas = alto de la hoja ÷ escala de texto.','Con texto al 130 % (a partir de 1,15) se oculta además la línea de volumen y los subtítulos de los anillos: no hay sitio sin apretar los números.','Los botones mantienen ≥ 44 dp en los tres niveles.']},{tier:'B'});
lxFinScene('lx-f-nivel-c','Sesión completada · nivel C (mínimo)','normal',{
  tags:[['new','lo esencial siempre cabe']],
  p:['El nivel más ajustado (por debajo de 600 dp efectivos: 360 × 740 con texto al 130 %, o pantallas muy bajas). <b>Se quitan músculos, mejoras y la línea de volumen; se queda lo esencial</b>: nombre, volumen con su diferencia, los cuatro anillos, la historia con su interruptor, Notas / Valorar / Fotos, Guardar y Volver. El módulo de datos crece para ocupar el aire que deja lo que se esconde.']},{tier:'C'});
/*__FIN_SCENES3__*/


// Gráfica de e1RM de la plantilla «Ejercicio» (línea que se dibuja, último punto en acento).
function lxChart(v){const w=300,h=210,mn=Math.min(...v)-2,mx=Math.max(...v)+1,X=i=>16+i*(w-40)/(v.length-1),Y=x=>h-24-(x-mn)/(mx-mn)*(h-60);let s='';
  for(let k=0;k<4;k++){const y=36+k*(h-60)/3;s+=`<line x1="10" x2="${w-10}" y1="${y}" y2="${y}" stroke="#ffffff10"/>`}
  const d=v.map((x,i)=>(i?'L':'M')+X(i).toFixed(1)+' '+Y(x).toFixed(1)).join('');
  s+=`<path class="ln" pathLength="1" d="${d}" fill="none" stroke="#f2eee6" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>`;
  v.forEach((x,i)=>{const last=i===v.length-1;s+=`<circle cx="${X(i)}" cy="${Y(x)}" r="${last?6:3.2}" fill="${last?'#f49a6e':'#0a0a0a'}" stroke="${last?'#f49a6e':'#f2eee6'}" stroke-width="1.6"/>`});
  return `<svg viewBox="0 0 ${w} ${h}" width="100%" height="100%" preserveAspectRatio="xMidYMid meet" aria-hidden="true">${s}</svg>`}
function lxStory(kind,w,opt){
  const o=Object.assign({dur:true,imp:true,rpe:true,ex:0},opt||{});
  const sty=w?`--sw:${w}`:'';
  const EX=[['Hip Thrust','120 kg × 8','RIR 2','+5 kg','e1RM','158','+8 kg'],['Peso Muerto','160 kg × 5','RIR 1','+2,5 kg','e1RM','182','+4,5 kg'],['Belt Squat','100 kg × 10','RIR 2','=','e1RM','133','+0 kg']][o.ex]||[];
  if(kind==='ej')return `<div class="story" style="${sty}"><div class="bgart" style="inset:0 0 50% 0;opacity:.16">${lxLite(coverSvg('relieve','carbon',{seed:8}))}</div>
    <div class="st-chart draw">${lxChart([150,151,153,154,156,158].map(v=>v*(EX[5]/158)))}</div>
    <div class="st-in" style="top:50%"><div class="st-ey">${EX[0]} · e1RM</div><div class="st-big">${EX[5]}<small>kg</small></div>${o.imp?`<div class="st-meta" style="margin-top:2cqw"><span style="color:#f49a6e;font-weight:600">${EX[6]}</span> en 6 sesiones</div>`:''}
    <div class="st-lifts"><div><span>Mejor serie de hoy</span><b style="font-size:4.6cqw">${EX[1]}</b></div><div><span>Esfuerzo</span><b style="font-size:4.6cqw">${EX[2]}</b></div></div><div class="st-foot">KPKN<span>MIÉ</span></div></div></div>`;
  if(kind==='fo')return `<div class="story" style="${sty}"><div class="photo"></div><div class="bgart draw over" style="inset:0 0 52% 0">${lxLite(coverSvg('relieve','carbon',{seed:4,clear:true}))}</div>
    <div class="st-in" style="top:62%"><div class="st-ey">MIÉ 29 OCT · SEMANA 1</div><div class="st-name">Legs II</div><div class="st-inline">${o.dur?'<span>52<small> min</small></span><i></i>':''}<span>8.420<small> kg</small></span><i></i><span>18<small> series</small></span></div><div class="st-foot">KPKN<span>Entreno de hoy</span></div></div></div>`;
  return `<div class="story" style="${sty}"><div class="bgart draw" style="inset:0 0 42% 0">${lxLite(coverSvg('sesion','carbon',{rows:LX_BASE.rows,pr:0}))}</div>
    <div class="st-in end" style="top:34%"><div class="st-ey">MIÉ 29 OCT · SEMANA 1</div><div class="st-name">Legs II</div><div class="st-meta">Motor y fuerza · Intensificación</div>
    <div class="st-stats">${o.dur?'<div><b>52<small>min</small></b><span>Duración</span></div>':''}<div><b>8.420<small>kg</small></b><span>Volumen</span></div><div><b>18</b><span>Series</span></div>${o.rpe?'<div><b>1,6</b><span>RIR medio</span></div>':''}</div>
    ${o.imp?'<div class="st-lifts" style="margin-top:0"><div><span>Hip Thrust</span><b>+5 kg</b></div><div><span>Peso Muerto</span><b>+2,5 kg</b></div></div>':''}<div class="st-foot">KPKN<span>Entreno de hoy</span></div></div></div>`;
}
// Hoja «Compartir» (WorkoutStorySheet): vista previa grande, plantillas con miniatura, opciones y las dos acciones.
function lxStorySheet(o={}){
  const t=o.tpl||'gen',tpls=[['gen','General'],['ej','Ejercicio'],['fo','Fotos']];
  const opt={dur:o.dur!==false,imp:o.imp!==false,rpe:o.rpe!==false,ex:o.ex||0};
  const exs=['Hip Thrust','Peso Muerto','Belt Squat'];
  return `<div class="pl-scrim"></div><section class="pl-sheet lx-ss" data-ss data-tpl="${t}">
   <div class="lx-ssh"><h2>Compartir</h2><button class="lx-x" aria-label="Volver al resumen">${lxI('close',20)}</button></div>
   <div class="lx-sprev"><div class="lx-glow"></div><div class="lx-spv" data-prev>${o.fail?'<span class="lx-sfail">'+lxI('info',24)+'</span>':o.rendering?'<span class="lx-spn"></span>':lxStory(t,'100%',opt)}</div></div>
   <div class="lx-tpl" role="tablist">${tpls.map(x=>`<button role="tab" aria-selected="${x[0]===t}" class="${x[0]===t?'lx-on':''}" data-tp="${x[0]}"><span class="lx-tpt">${lxStory(x[0],'30px',{})}</span><b>${x[1]}</b></button>`).join('')}</div>
   <div class="lx-exp" data-exp ${t==='ej'?'':'hidden'}>${exs.map((e,i)=>`<button class="lx-chip ${i===opt.ex?'lx-on':''}" data-ex="${i}">${e}</button>`).join('')}</div>
   <div class="lx-inc"><span class="lx-eb">INCLUIR</span><div class="lx-chips" data-inc>
     <button class="lx-chip ${opt.imp?'lx-on':''}" data-tg="imp" ${t==='fo'?'hidden':''}>Mejoras en kg</button>
     <button class="lx-chip ${opt.dur?'lx-on':''}" data-tg="dur" ${t==='ej'?'hidden':''}>Duración</button>
     <button class="lx-chip ${opt.rpe?'lx-on':''}" data-tg="rpe" ${t==='gen'?'':'hidden'}>sRPE declarado</button></div></div>
   <p class="lx-smsg" data-msg>${o.msg||''}</p>
   <div class="lx-sact"><button class="lx-dl2" data-sv aria-label="Guardar imagen" title="Guardar imagen" ${o.fail?'disabled':''}>${lxI('upload',22)}</button><button class="pl-btn" data-sh ${o.fail?'disabled':''}>${lxI('share',17)}Compartir en historias</button></div></section>`;
}
function lxStoryApp(ph){
  const root=ph.querySelector('[data-ss]');if(!root)return;
  const S={tpl:root.dataset.tpl,dur:true,imp:true,rpe:true,ex:0};
  const prev=root.querySelector('[data-prev]'),msg=root.querySelector('[data-msg]');
  let tm=0;
  const draw=(delay)=>{prev.innerHTML='<span class="lx-spn"></span>';clearTimeout(tm);tm=setTimeout(()=>{prev.innerHTML=lxStory(S.tpl,'100%',S);animateIn(prev)},delay)};
  const sync=()=>{
    root.dataset.tpl=S.tpl;
    root.querySelectorAll('[data-tp]').forEach(b=>{const on=b.dataset.tp===S.tpl;b.classList.toggle('lx-on',on);b.setAttribute('aria-selected',on)});
    root.querySelector('[data-exp]').hidden=S.tpl!=='ej';
    root.querySelector('[data-tg="imp"]').hidden=S.tpl==='fo';root.querySelector('[data-tg="dur"]').hidden=S.tpl==='ej';root.querySelector('[data-tg="rpe"]').hidden=S.tpl!=='gen';
    ['imp','dur','rpe'].forEach(k=>root.querySelector('[data-tg="'+k+'"]').classList.toggle('lx-on',S[k]));
    root.querySelectorAll('[data-ex]').forEach(b=>b.classList.toggle('lx-on',+b.dataset.ex===S.ex));
  };
  root.addEventListener('click',e=>{
    const tp=e.target.closest('[data-tp]'),tg=e.target.closest('[data-tg]'),ex=e.target.closest('[data-ex]');
    if(tp){S.tpl=tp.dataset.tp;sync();draw(380);msg.textContent=''}
    else if(tg){S[tg.dataset.tg]=!S[tg.dataset.tg];sync();draw(260)}
    else if(ex){S.ex=+ex.dataset.ex;sync();draw(260)}
    else if(e.target.closest('[data-sv]'))msg.textContent='Imagen guardada en Imágenes/KPKN';
  });
  animateIn(root);
}
const lxStScene=(id,title,o,notes)=>scene({id,tab:'vivo-resto',title,html:()=>lxFinBack()+lxStorySheet(o),after:ph=>lxStoryApp(ph),notes});
const lxFinBack=()=>lxSeriesBack({time:'52:10'});
lxStScene('lx-f-historia','Compartir en historias · General',{tpl:'gen'},{
  tags:[['new','rediseño de la hoja'],['keep','las tres plantillas y las opciones']],
  p:['La hoja que se abre con «Preparar historia» (o con el interruptor de «Compartir al guardar»). Hoy es una banda de texto con una vista previa pequeña; aquí <b>la vista previa es la protagonista</b> (9:16 a casi todo el alto, con un resplandor suave detrás), las plantillas son <b>miniaturas de la propia historia</b> en lugar de tres palabras, y las opciones son los mismos tres interruptores de hoy.',
     'Acciones como hoy: <b>Guardar imagen</b> (a Imágenes/KPKN; muestra «Imagen guardada en Imágenes/KPKN») y <b>Compartir en historias</b> (abre el selector de Android para mandarla a Instagram u otra app). Sin scroll: la vista previa se encoge antes de empujar nada.',
     'Interactivo: cambia de plantilla, activa o quita opciones y elige el ejercicio; la vista previa se regenera con un breve estado de carga.'],
  list:['<b>General</b>: nombre y fecha, duración, volumen, series, RIR medio y las dos mayores mejoras. Opciones: Mejoras en kg · Duración · sRPE declarado.','<b>Ejercicio</b>: la gráfica de e1RM de un ejercicio a elegir. Opciones: Mejoras en kg. Aparece la fila para elegir ejercicio.','<b>Fotos</b>: tu primera foto de la sesión a pantalla completa con líneas finas encima; solo existe si hay fotos. Opciones: Duración.','Tocar la vista previa la abre a pantalla completa (como hoy).']});
lxStScene('lx-f-historia-ej','Compartir en historias · Ejercicio',{tpl:'ej',ex:1},{tags:[['keep','plantilla Ejercicio']],p:['La plantilla «Ejercicio» con su fila para elegir qué ejercicio cuenta la historia (por omisión, el de mayor mejora). Se esconden las opciones que no aplican (Duración y sRPE).']});
lxStScene('lx-f-historia-fotos','Compartir en historias · Fotos',{tpl:'fo'},{tags:[['keep','plantilla Fotos']],p:['«Fotos» usa la primera foto de la sesión como fondo, con las líneas de la portada encima y las tres cifras en una fila. Si la sesión no tiene fotos, la plantilla no aparece en el selector (hoy ocurre igual).']});
lxStScene('lx-f-historia-error','Compartir en historias · error',{tpl:'gen',msg:'No se pudo crear la historia. Inténtalo de nuevo.',fail:true},{tags:[['keep','mensaje de hoy']],p:['Si la imagen no se puede generar, la vista previa se queda vacía con el mensaje de la app («No se pudo crear la historia. Inténtalo de nuevo.») y «Guardar imagen» y «Compartir en historias» quedan desactivados hasta que cambie algo.']});
/*__END__*/
