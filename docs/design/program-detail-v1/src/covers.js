// Portadas generativas KPKN v2: familia de LÍNEAS finas sobre fondos oscuros (línea de «Relieve»).
// Regla: monocromas; el color solo entra difuminado (halo o bruma) o en una única línea de acento.
// SVG 4:5 que recorta (slice) a cualquier proporción sin deformarse.
const CPAL={
  carbon:{bg:'#161616',bg2:'#060606',ln:'#e9e3d8',a:'#f49a6e',n:'Carbón'},
  grafito:{bg:'#1d2024',bg2:'#0b0c0e',ln:'#a3acb6',a:'#f2eee6',n:'Grafito'},
  noche:{bg:'#101830',bg2:'#04070e',ln:'#93a9da',a:'#f7cf73',n:'Noche'},
  musgo:{bg:'#131c17',bg2:'#050907',ln:'#a4bcaa',a:'#f49a6e',n:'Musgo'},
  crema:{bg:'#ece6db',bg2:'#dcd3c4',ln:'#1d1b19',a:'#d9673a',n:'Crema'},
};
const CSTYLES={relieve:'Relieve',perfil:'Perfil',ondas:'Ondas',orbitas:'Órbitas',campo:'Campo',espiral:'Espiral',eco:'Eco',bruma:'Bruma'};
let _cid=0;
function rng(seed){let s=Math.floor(seed)%2147483647||7;return()=>(s=s*16807%2147483647)/2147483647}
const P2=(pts)=>pts.map((p,i)=>(i?'L':'M')+p[0].toFixed(1)+' '+p[1].toFixed(1)).join('');
function coverSvg(style='relieve',pal='carbon',o={}){
  const p=CPAL[pal]||CPAL.carbon,id=++_cid,seed=(o.seed||3)*97+style.length*13,r=rng(seed);
  const W=300,H=375,dark=pal!=='crema';
  let defs=`<filter id="g${id}" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".85" numOctaves="2" stitchTiles="stitch"/><feColorMatrix values="0 0 0 0 .5 0 0 0 0 .5 0 0 0 0 .5 0 0 0 ${dark?.10:.14} 0"/></filter>
   <filter id="w${id}" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="3.2" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
   <filter id="f${id}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="46"/></filter>`;
  const line=(d,acc=false,w=1.15,op=.78)=>`<path class="ln" pathLength="1" d="${d}" fill="none" stroke="${acc?p.a:p.ln}" stroke-width="${acc?2.1:w}" stroke-linecap="round" stroke-linejoin="round" opacity="${acc?1:op}" ${acc?`filter="url(#w${id})"`:''}/>`;
  const halo=(cx,cy,rad,op=.35)=>`<circle cx="${cx}" cy="${cy}" r="${rad}" fill="${p.a}" opacity="${dark?op:op*.6}" filter="url(#f${id})"/>`;
  let body='';
  switch(style){
    case 'relieve':{ // curvas de nivel; una cota en acento
      const cx=150+(r()-.5)*70,cy=190+(r()-.5)*80;body+=halo(cx,cy,70,.28);
      for(let k=1;k<=16;k++){const R=k*13;const pts=[];for(let a=0;a<=72;a++){const t=a/72*Math.PI*2;const rr=R*(1+.2*Math.sin(3*t+k*.55)+.09*Math.sin(5*t+k*1.3)+.04*Math.sin(9*t));pts.push([cx+rr*Math.cos(t),cy+rr*Math.sin(t)*1.18])}
        body+=line(P2(pts)+'Z',k===7)}
      break;}
    case 'perfil':{ // crestas apiladas: la carga semanal del programa como una cordillera
      const vol=o.vol||[.62,.7,.8,.66,.74,.38];const n=vol.length;const prof=x=>{const f=x/W*(n-1);const i=Math.min(n-2,Math.floor(f)),t=f-i;const s=t*t*(3-2*t);return vol[i]*(1-s)+vol[i+1]*s};
      const rows=24;body+=halo(150,150,80,.22);
      for(let k=0;k<rows;k++){const y0=88+k*11;const pts=[];for(let x=0;x<=W;x+=5){const env=Math.exp(-Math.pow((x-150)/95,2));const h=env*prof(x)*(70+k*1.2)*(0.55+.45*Math.sin(k*.9+x*.03+seed))+env*6*r();pts.push([x,y0-Math.abs(h)])}
        const d=P2(pts);body+=`<path d="${d}L${W} ${H}L0 ${H}Z" fill="${p.bg2}" opacity=".96"/>`+line(d,k===(o.now??3)*3+2)}
      break;}
    case 'ondas':{ // líneas que se levantan en el centro, como un pulso
      body+=halo(150,210,60,.2);
      for(let k=0;k<26;k++){const y=50+k*12;const pts=[];for(let x=0;x<=W;x+=4){const env=Math.exp(-Math.pow((x-150-(k-13)*3)/62,2));pts.push([x,y-env*(8+k*.9)*Math.abs(Math.sin(x/8+k*.6+seed*.01))*2.2])}
        body+=`<path d="${P2(pts)}L${W} ${H}L0 ${H}Z" fill="${p.bg2}" opacity=".9"/>`+line(P2(pts),k===15)}
      break;}
    case 'orbitas':{ // órbitas finas e inclinadas alrededor de la torre
      body+=halo(150,190,55,.3);
      body+=`<g transform="rotate(-24 150 190)">`;
      for(let k=0;k<14;k++){const rx=22+k*12,ry=11+k*7;body+=`<ellipse class="ln" pathLength="1" cx="150" cy="190" rx="${rx}" ry="${ry}" fill="none" stroke="${k===8?p.a:p.ln}" stroke-width="${k===8?2:1.05}" opacity="${k===8?1:(.85-k*.035).toFixed(2)}" ${k===8?`filter="url(#w${id})"`:''}/>`}
      const t=2.3;body+=`<circle cx="${150+(22+8*12)*Math.cos(t)}" cy="${190+(11+8*7)*Math.sin(t)}" r="4" fill="${p.a}" filter="url(#w${id})"/></g>`;
      for(let i=0;i<30;i++)body+=`<circle cx="${(r()*W).toFixed(1)}" cy="${(r()*H).toFixed(1)}" r="${(r()*.9+.3).toFixed(1)}" fill="${p.ln}" opacity="${(r()*.4+.15).toFixed(2)}"/>`;
      break;}
    case 'campo':{ // campo de flujo: trazos que siguen una corriente
      body+=halo(90+r()*120,120+r()*140,70,.18);
      const ang=(x,y)=>Math.sin(x*.011+seed*.001)*1.6+Math.cos(y*.013)*1.2;
      for(let i=0;i<70;i++){let x=r()*W,y=r()*H;const pts=[[x,y]];for(let s=0;s<34;s++){const a=ang(x,y);x+=Math.cos(a)*5;y+=Math.sin(a)*5;pts.push([x,y])}
        body+=line(P2(pts),i===23,1,.55)}
      break;}
    case 'espiral':{ // una vuelta por semana; la semana actual en acento
      const weeks=o.weeks||6,now=o.now??3;body+=halo(150,190,60,.25);
      const seg=(w0,w1)=>{const pts=[];for(let t=w0*Math.PI*2;t<=w1*Math.PI*2+.01;t+=.06){const rr=14+t*3.4;pts.push([150+rr*Math.cos(t),190+rr*Math.sin(t)*1.12])}return P2(pts)};
      for(let w=0;w<weeks*2;w++)body+=line(seg(w/2,(w+1)/2),Math.floor(w/2)===now&&w%2===0,1.2,.8);
      break;}
    case 'eco':{ // la cifra del programa repetida en contornos, como un eco
      const t=o.num||'6';
      for(let k=9;k>=0;k--)body+=`<text x="150" y="${292-k*9}" text-anchor="middle" font-family="Inter" font-weight="600" font-size="270" letter-spacing="-8" fill="none" stroke="${k===0?p.a:p.ln}" stroke-width="${k===0?2:1.1}" opacity="${k===0?1:(.7-k*.065).toFixed(2)}" ${k===0?`filter="url(#w${id})"`:''}>${t}</text>`;
      break;}
    case 'sesion':{ // la sesión hecha: una línea por ejercicio, un pico por serie (alto = kg × reps); acento = ejercicio con marca
      const rows=o.rows||[[.7,.75,.8,.85],[.6,.62,.65],[.5,.5,.52],[.35,.36,.38],[.3,.3,.32]],pr=o.pr??0;body+=halo(150,140,70,.22);
      rows.forEach((sets,k)=>{const y0=88+k*30;const n=sets.length;const pts=[];
        for(let x=0;x<=W;x+=3){let h=0;sets.forEach((v,j)=>{const cx=60+j*(180/Math.max(1,n-1));h+=v*70*Math.exp(-Math.pow((x-cx)/(9+k),2))});pts.push([x,y0-h])}
        body+=`<path d="${P2(pts)}L${W} ${H}L0 ${H}Z" fill="${p.bg2}" opacity=".94"/>`+line(P2(pts),k===pr)});
      break;}
    case 'bruma':{ // el único estilo con color: difuminado, y líneas finas encima
      const c=[p.a,'#8fb2ff','#c9a8ff'];
      body+=`<g opacity="${dark?.55:.45}">${[[70,100,110],[240,210,100],[110,330,105]].map((q,i)=>`<circle cx="${q[0]}" cy="${q[1]}" r="${q[2]}" fill="${c[i]}" filter="url(#f${id})"/>`).join('')}</g>`;
      for(let k=0;k<20;k++){const y=40+k*16;const pts=[];for(let x=0;x<=W;x+=6)pts.push([x,y+Math.sin(x*.02+k*.4)*10+Math.sin(x*.05+k)*3]);body+=line(P2(pts),false,1,.42)}
      break;}
  }
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid slice" width="100%" height="100%" style="display:block" aria-hidden="true"><defs>${defs}<linearGradient id="bg${id}" x1="0" y1="0" x2=".3" y2="1"><stop offset="0" stop-color="${p.bg}"/><stop offset="1" stop-color="${p.bg2}"/></linearGradient></defs>${o.clear?"":`<rect width="${W}" height="${H}" fill="url(#bg${id})"/>`}${body}<rect width="${W}" height="${H}" filter="url(#g${id})"/></svg>`;
}
