// Datos sintéticos de ejemplo (no son del usuario). Programa de 6 semanas en 3 bloques, en curso en la semana 4.
const PH={acc:{n:'Acumulación',c:'var(--ph-acc)'},int:{n:'Intensificación',c:'var(--ph-int)'},rea:{n:'Realización',c:'var(--ph-rea)'},del:{n:'Descarga',c:'var(--ph-del)'},com:{n:'Competición',c:'var(--ph-com)'}};
const PROGRAM={name:'Motor y fuerza',goal:'Músculo',weeks:[
  {n:1,ph:'acc',st:'done',name:'Semana de carga'},{n:2,ph:'acc',st:'done'},{n:3,ph:'acc',st:'done'},
  {n:4,ph:'int',st:'now'},{n:5,ph:'int',st:'fut'},{n:6,ph:'del',st:'fut',name:'Descarga'}],
  blocks:[{name:'Bloque 1 · Base',ph:'acc',weeks:[1,2,3],scheme:'Doble progresión'},{name:'Bloque 2 · Fuerza',ph:'int',weeks:[4,5],scheme:'Top set + back-off'},{name:'Bloque 3 · Descarga',ph:'del',weeks:[6],scheme:'Mitad de series, RIR 4'}]};
const MUS={leg:'var(--mus)',push:'var(--ene)',pull:'var(--col)',core:'#c9a8ff',cardio:'var(--done)'};
// Semana 4: miércoles con dos sesiones, sábado opcional.
const WEEK=[
  {d:'LUN',n:27,past:true,s:[{name:'Pierna A',ex:6,min:58,f:['leg','leg','core'],st:'done'}]},
  {d:'MAR',n:28,past:true,s:[{name:'Empuje',ex:6,min:52,f:['push','push','pull'],st:'done'}]},
  {d:'MIÉ',n:29,today:true,s:[{name:'Tirón',ex:5,min:48,f:['pull','pull','core'],st:'now'},{name:'Cardio y potencia',ex:4,min:32,f:['cardio','leg'],st:'todo'}]},
  {d:'JUE',n:30,s:[]},
  {d:'VIE',n:31,s:[{name:'Pierna B',ex:6,min:61,f:['leg','leg','pull'],st:'todo',prop:'+2,5 kg'}]},
  {d:'SÁB',n:1,s:[{name:'Movilidad y core',ex:5,min:25,f:['core'],st:'opt'}]},
  {d:'DOM',n:2,s:[]}];
const SESSION_EX=[
  {n:'Dominadas lastradas',d:'4 × 5–6 · RIR 2 · +12,5 kg'},{n:'Remo con barra',d:'3 × 8–10 · RIR 2 · 70 kg'},
  {n:'Jalón al pecho neutro',d:'3 × 10–12 · RIR 1 · 59 kg'},{n:'Curl inclinado',d:'3 × 10–12 · RIR 1 · 12 kg'},{n:'Rueda abdominal',d:'3 × 8–10 · RIR 2'}];
const LIFTS=[
  {n:'Sentadilla trasera',now:142.5,d:+7.5,st:'up',s:[135,136,138,139,141,142.5],sess:'6 sesiones'},
  {n:'Press banca',now:101,d:+4,st:'up',s:[97,97.5,99,99,100,101],sess:'6 sesiones'},
  {n:'Peso muerto rumano',now:128,d:0,st:'flat',s:[127,128,128.5,127.5,128,128],sess:'3 sem. sin subir'},
  {n:'Press militar',now:58,d:-2.5,st:'down',s:[60.5,61,60,59,58.5,58],sess:'Baja 2 semanas'},
  {n:'Dominadas lastradas',now:null,d:null,st:'new',s:[],sess:'2 sesiones: aún sin tendencia'}];
// Trabajo por semana: series duras hechas / plan, tonelaje (t), minutos.
const WORK=[{w:1,real:62,plan:64,t:11.8,m:218},{w:2,real:66,plan:68,t:12.9,m:231},{w:3,real:58,plan:72,t:12.1,m:205},{w:4,real:26,plan:60,t:5.6,m:106,now:true},{w:5,real:0,plan:56,t:0,m:0},{w:6,real:0,plan:34,t:0,m:0}];
const VOL=[
  {m:'Cuádriceps',done:14,plan:16,mev:8,mrv:20},{m:'Isquiosurales',done:9,plan:10,mev:6,mrv:16},{m:'Glúteos',done:11,plan:12,mev:4,mrv:16},
  {m:'Pectorales',done:12,plan:12,mev:8,mrv:20},{m:'Dorsales',done:15,plan:16,mev:8,mrv:22},{m:'Deltoides',done:16,plan:18,mev:6,mrv:24},
  {m:'Bíceps',done:9,plan:10,mev:6,mrv:20},{m:'Tríceps',done:7,plan:8,mev:6,mrv:18},{m:'Pantorrillas',done:0,plan:0,mev:6,mrv:16}];
const DECISIONS=[
  {t:'Hoy',h:'Propuesta: +2,5 kg en sentadilla',d:'Cerraste 3 × 5 con RIR 3 dos sesiones seguidas.'},
  {t:'Lun 27',h:'Press militar sin cambios',d:'El RIR registrado quedó por debajo del plan; se mantiene la carga.'},
  {t:'Dom 26',h:'Cambio de bloque: Fuerza',d:'Empieza el bloque 2 con top set + back-off.'},
  {t:'Mié 22',h:'Remo con barra → remo en máquina',d:'Cambio de ejercicio que hiciste desde la sesión.'}];

/* Piezas compartidas */
// Portada del programa de ejemplo: estilo «Perfil» (su carga semanal como cordillera) en paleta Carbón. Ver covers.js.
function cover(cls='',style='perfil',pal='carbon',o={}){return `<div class="cover ${cls}">${coverSvg(style,pal,o)}</div>`}
// Barra superior flotante: tres piezas de vidrio sueltas (atrás · pestañas · más). El contenido pasa por debajo.
function topbar(on,{dot=false}={}){return `<div class="topbar"><button class="fb glass" aria-label="Atrás">${ic('back')}</button><div class="ftabs glass" role="tablist">${['Semana','Progreso','Estructura'].map(x=>`<button role="tab" aria-selected="${x===on}" class="${x===on?'on':''}">${x}</button>`).join('')}</div><button class="fb glass" aria-label="Más opciones${dot?', hay pendientes':''}">${ic('more')}${dot?'<i class="fdot"></i>':''}</button></div>`}
function ribbon(weeks=PROGRAM.weeks){return `<div class="ribbon" aria-hidden="true">${weeks.map(w=>`<i class="${w.st==='done'?'done':w.st==='now'?'now':''}" style="background:${PH[w.ph].c}"></i>`).join('')}</div>`}
function miniRib(sel=4){return `<div class="mini-rib" aria-hidden="true">${PROGRAM.weeks.map(w=>`<i class="${w.st==='done'?'done':''} ${w.n===sel?'sel':''}" style="background:${PH[w.ph].c}"></i>`).join('')}</div>`}
function island(title,sub,extra=''){return `<div class="island glass"><button class="ib" aria-label="Atrás">${ic('back')}</button><div class="t"><strong>${title}</strong>${sub?`<span>${sub}</span>`:''}</div>${extra}<button class="ib" aria-label="Más opciones">${ic('more')}</button></div>`}
function tabsSeg(on){const t=['Semana','Progreso','Estructura'];return `<div class="seg" role="tablist">${t.map(x=>`<button class="${x===on?'on':''}">${x}</button>`).join('')}</div>`}
function sheet(inner,{scrim=true}={}){return `${scrim?'<div class="scrim"></div>':''}<div class="sheet"><div class="grab"></div>${inner}</div>`}
function mi(icon,title,sub,cls=''){return `<button class="mi ${cls}"><span class="mic">${ic(icon,19)}</span><span class="tx"><strong>${title}</strong>${sub?`<span>${sub}</span>`:''}</span></button>`}
function spark(vals,color='var(--ink)',w=74,h=28){
  if(vals.length<2)return `<svg width="${w}" height="${h}"><line x1="2" y1="${h/2}" x2="${w-2}" y2="${h/2}" stroke="#ffffff22" stroke-dasharray="3 4"/></svg>`;
  const mn=Math.min(...vals),mx=Math.max(...vals),r=mx-mn||1;
  const pts=vals.map((v,i)=>[2+i*(w-6)/(vals.length-1),h-4-(v-mn)/r*(h-8)]);
  const d=pts.map((p,i)=>(i?'L':'M')+p[0].toFixed(1)+' '+p[1].toFixed(1)).join(' ');
  const last=pts[pts.length-1];
  return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" aria-hidden="true"><path d="${d} L${last[0]} ${h} L2 ${h}Z" fill="${color}" opacity=".12"/><path d="${d}" fill="none" stroke="${color}" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round"/><circle cx="${last[0]}" cy="${last[1]}" r="2.6" fill="${color}"/></svg>`;
}
const kg=v=>v==null?'—':String(v).replace('.',',');
