'use strict';
/* Minijuegos de descanso · propuesta propia (iteración 03).
   Cada juego tiene su estado; el reloj de descanso es independiente y manda. */
(()=>{const {S,ic,clock,statusbar,later,every,say,$,$$}=window.KpknProto;
const INK='#f2eee6',MUTED='#b9b5ae',FAINT='#5c5a57',PEACH='#f49a6e',LINE='#ffffff14';
const store={get:k=>{try{return +localStorage.getItem('kpkn-g3-'+k)||0;}catch{return 0;}},set:(k,v)=>{try{localStorage.setItem('kpkn-g3-'+k,v);}catch{}}};
const rnd=(a,b)=>a+Math.random()*(b-a),clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
let loop=null;
const rest={left:92,total:150,paused:false,over:false,extra:0};
function stop(){if(loop){cancelAnimationFrame(loop.raf);loop.off&&loop.off();loop=null;}}
/* ---------- utilidades de dibujo ---------- */
function rr(c,x,y,w,h,r){c.beginPath();c.roundRect(x,y,w,h,r);}
function plate(c,x,y,r,col=INK){c.fillStyle=col;c.beginPath();c.arc(x,y,r,0,7);c.fill();c.fillStyle='#000';c.beginPath();c.arc(x,y,r*.28,0,7);c.fill();c.strokeStyle='#00000055';c.lineWidth=1.5;c.beginPath();c.arc(x,y,r*.68,0,7);c.stroke();}
function dumbbell(c,x,y,w,h){c.fillStyle=INK;rr(c,x,y,w*.24,h,3);c.fill();rr(c,x+w*.76,y,w*.24,h,3);c.fill();c.fillStyle=MUTED;c.fillRect(x+w*.24,y+h*.4,w*.52,h*.2);}
function particles(list,x,y,n,col,sp=160){for(let i=0;i<n;i++){const a=rnd(0,6.28),v=rnd(sp*.3,sp);list.push({x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v-40,life:rnd(.35,.7),col});}}
function stepParticles(list,dt,c){for(let i=list.length-1;i>=0;i--){const p=list[i];p.life-=dt;if(p.life<=0){list.splice(i,1);continue;}p.vy+=600*dt;p.x+=p.vx*dt;p.y+=p.vy*dt;if(c){c.globalAlpha=clamp(p.life*2,0,1);c.fillStyle=p.col;c.fillRect(p.x-1.5,p.y-1.5,3,3);c.globalAlpha=1;}}}
function popups(list,dt,c){for(let i=list.length-1;i>=0;i--){const p=list[i];p.t+=dt;if(p.t>.9){list.splice(i,1);continue;}c.globalAlpha=1-p.t/.9;c.fillStyle=p.col||INK;c.font=`800 ${p.big?18:13}px Syne`;c.textAlign='center';c.fillText(p.s,p.x,p.y-p.t*34);c.globalAlpha=1;}}
/* ---------- 1 · Salta las mancuernas ---------- */
const Runner={id:'runner',name:'Salta las mancuernas',
 init(w,h){const g={w,h,ground:h*.74,x:w*.22,y:0,vy:0,onGround:true,coyote:0,buffer:0,holding:false,jumps:0,chalk:0,speed:250,dist:0,obs:[],picks:[],parts:[],pops:[],next:0,lives:3,inv:0,combo:0,score:0,shake:0,t:0,best:store.get('runner')};g.y=g.ground;return g;},
 press(g){g.buffer=.12;g.holding=true;},release(g){g.holding=false;},
 spawn(g){const kinds=[{k:'db',w:34,h:18},{k:'kb',w:24,h:28},{k:'stack',w:30,h:42}];const lvl=Math.min(2,Math.floor(g.dist/1400));const pick=kinds[Math.floor(rnd(0,lvl+1.2))%3];
  g.obs.push({...pick,x:g.w+20,cleared:false});if(Math.random()<.3&&g.dist>500)g.obs.push({...kinds[0],x:g.w+20+pick.w+rnd(58,78),cleared:false});
  if(Math.random()<.45)g.picks.push({k:Math.random()<.18&&!g.chalk?'chalk':'shake',x:g.w+rnd(90,160),y:g.ground-rnd(70,110),got:false});
  g.next=rnd(.95,1.6)*Math.max(.62,1-g.speed/1400)*(260/g.speed)*1.55;},
 update(g,dt){g.t+=dt;g.speed=Math.min(470,250+g.t*2.4);const dx=g.speed*dt;g.dist+=dx;g.score+=dx*.05;g.inv=Math.max(0,g.inv-dt);g.chalk=Math.max(0,g.chalk-dt);g.shake=Math.max(0,g.shake-dt*30);
  g.buffer=Math.max(0,g.buffer-dt);g.coyote=g.onGround?.09:Math.max(0,g.coyote-dt);
  const maxJ=g.chalk?2:1;if(g.buffer>0&&(g.coyote>0||g.jumps<maxJ)){g.vy=-560;g.onGround=false;g.coyote=0;g.buffer=0;g.jumps++;particles(g.parts,g.x,g.y,5,MUTED,90);}
  const grav=g.vy<0&&g.holding?1500:2900;g.vy+=grav*dt;g.y+=g.vy*dt;if(g.y>=g.ground){if(!g.onGround)particles(g.parts,g.x,g.ground,4,FAINT,70);g.y=g.ground;g.vy=0;g.onGround=true;g.jumps=0;}
  g.next-=dt;if(g.next<=0)this.spawn(g);
  for(const o of g.obs){o.x-=dx;const px=g.x-11,pw=22,ph=34,py=g.y-ph;const hit=px<o.x+o.w-5&&px+pw>o.x+5&&py+ph>g.ground-o.h+4;
   if(hit&&!g.inv&&!o.hit){o.hit=1;g.lives--;g.inv=1.3;g.combo=0;g.shake=S.reduced?0:7;particles(g.parts,g.x,g.y-17,14,PEACH,200);g.pops.push({s:'−1',x:g.x,y:g.y-48,t:0,col:PEACH});if(g.lives<=0)return 'over';}
   if(!o.cleared&&o.x+o.w<px&&!o.hit){o.cleared=true;g.combo++;const gap=(g.ground-o.h)-(g.y);const near=gap<14&&!g.onGround;const m=Math.min(5,1+Math.floor(g.combo/4));const pts=(near?25:10)*m;g.score+=pts;g.pops.push({s:near?`¡Justo! +${pts}`:`+${pts}`,x:o.x+o.w/2,y:g.ground-o.h-14,t:0,big:near});}}
  g.obs=g.obs.filter(o=>o.x>-60);
  for(const p of g.picks){p.x-=dx;p.y+=Math.sin(g.t*5+p.x*.02)*.3;if(!p.got&&Math.abs(p.x-g.x)<20&&Math.abs(p.y-(g.y-17))<26){p.got=true;if(p.k==='chalk'){g.chalk=8;g.pops.push({s:'Doble salto · 8 s',x:p.x,y:p.y-14,t:0,big:1});}else{g.score+=30;g.pops.push({s:'+30',x:p.x,y:p.y-10,t:0});}particles(g.parts,p.x,p.y,10,INK,140);}}
  g.picks=g.picks.filter(p=>p.x>-30&&!p.got);if(g.score>g.best){g.best=Math.floor(g.score);}},
 draw(g,c){const {w,h}=g;c.fillStyle='#000';c.fillRect(0,0,w,h);
  c.strokeStyle='#ffffff09';c.lineWidth=1;for(let i=0;i<7;i++){const x=((i*90-g.dist*.15)%(w+90)+w+90)%(w+90)-45;c.strokeRect(x,g.ground-150,40,150);c.beginPath();c.moveTo(x-6,g.ground-118);c.lineTo(x+46,g.ground-118);c.stroke();}
  c.fillStyle='#ffffff08';for(let i=0;i<14;i++){const x=((i*42-g.dist*.55)%(w+42)+w+42)%(w+42)-21;c.fillRect(x,g.ground+12,24,2);}
  c.fillStyle=LINE;c.fillRect(0,g.ground,w,2);
  const bestX=g.x+(g.best/.05-g.dist)*1;if(g.best>0&&bestX>0&&bestX<w){c.setLineDash([3,4]);c.strokeStyle='#ffffff40';c.beginPath();c.moveTo(bestX,g.ground-60);c.lineTo(bestX,g.ground);c.stroke();c.setLineDash([]);c.fillStyle=FAINT;c.font='600 9px Inter';c.textAlign='center';c.fillText('RÉCORD',bestX,g.ground-66);}
  for(const o of g.obs){c.globalAlpha=o.hit?.35:1;if(o.k==='db')dumbbell(c,o.x,g.ground-o.h,o.w,o.h);else if(o.k==='kb'){c.fillStyle=INK;c.beginPath();c.arc(o.x+o.w/2,g.ground-o.h*.38,o.w*.5,0,7);c.fill();c.strokeStyle=INK;c.lineWidth=4;c.beginPath();c.arc(o.x+o.w/2,g.ground-o.h*.78,o.w*.3,Math.PI,0);c.stroke();}
   else{for(let i=0;i<4;i++){c.fillStyle=i%2?MUTED:INK;rr(c,o.x+(i%2?2:0),g.ground-(i+1)*o.h/4,o.w-(i%2?4:0),o.h/4-2,3);c.fill();}}c.globalAlpha=1;}
  for(const p of g.picks){if(p.k==='chalk'){c.fillStyle=INK;rr(c,p.x-8,p.y-10,16,20,4);c.fill();c.fillStyle='#000';c.font='800 9px Syne';c.textAlign='center';c.fillText('2×',p.x,p.y+3);}else{c.strokeStyle=INK;c.lineWidth=2;rr(c,p.x-6,p.y-9,12,18,3);c.stroke();c.fillStyle=MUTED;c.fillRect(p.x-4,p.y-2,8,8);}}
  if(!(g.inv&&Math.floor(g.inv*12)%2)){const y=g.y,x=g.x,run=g.onGround?Math.sin(g.t*22):0.6;c.fillStyle=g.chalk?PEACH:INK;c.beginPath();c.arc(x,y-30,6,0,7);c.fill();rr(c,x-6,y-24,12,14,4);c.fill();c.strokeStyle=c.fillStyle;c.lineWidth=4;c.lineCap='round';
   c.beginPath();c.moveTo(x-2,y-11);c.lineTo(x-2-run*7,y-1);c.moveTo(x+2,y-11);c.lineTo(x+2+run*7,y-1);c.moveTo(x-5,y-20);c.lineTo(x-10+run*4,y-13);c.moveTo(x+5,y-20);c.lineTo(x+10-run*4,y-13);c.stroke();}
  if(g.chalk){c.fillStyle=PEACH;c.fillRect(g.x-14,g.y-44,28*(g.chalk/8),2);}
  stepParticles(g.parts,1/60,c);popups(g.pops,1/60,c);},
 hud:g=>({score:Math.floor(g.score),lives:g.lives,extra:g.combo>3?`Racha ×${Math.min(5,1+Math.floor(g.combo/4))}`:g.chalk?'Doble salto':'',best:g.best}),
 hint:'Toca para saltar · mantén para saltar más alto'};
/* ---------- 2 · Entre las pesas ---------- */
const Flappy={id:'flappy',name:'Entre las pesas',
 init(w,h){return{w,h,x:w*.3,y:h*.42,vy:0,cols:[],parts:[],pops:[],next:.4,speed:150,lives:3,inv:0,score:0,chain:0,t:0,started:false,best:store.get('flappy'),seed:rnd(0,9),lastGap:h*.42};},
 press(g){g.started=true;g.vy=-360;particles(g.parts,g.x-10,g.y+6,3,FAINT,60);},release(){},
 update(g,dt){g.t+=dt;g.inv=Math.max(0,g.inv-dt);if(!g.started){g.y=g.h*.42+Math.sin(g.t*3)*6;return;}
  g.vy=Math.min(520,g.vy+1250*dt);g.y+=g.vy*dt;const lvl=Math.min(1,g.score/40);g.speed=150+lvl*60;
  g.next-=dt;if(g.next<=0){const gapH=168-lvl*42,mid=clamp(g.lastGap+rnd(-110,110)*(.5+lvl*.5),gapH/2+40,g.h-gapH/2-60);g.lastGap=mid;g.cols.push({x:g.w+30,mid,gapH,move:lvl>.5&&Math.random()<.4?rnd(18,34):0,ph:rnd(0,6),passed:false});g.next=1.55-lvl*.25;}
  for(const c of g.cols){c.x-=g.speed*dt;const mid=c.mid+(c.move?Math.sin(g.t*1.4+c.ph)*c.move:0);c.cur=mid;const inX=g.x+11>c.x&&g.x-11<c.x+34;const out=g.y-11<mid-c.gapH/2||g.y+11>mid+c.gapH/2;
   if(inX&&out&&!g.inv){g.lives--;g.inv=1.6;g.chain=0;g.vy=-120;g.y=clamp(g.y,mid-c.gapH/2+14,mid+c.gapH/2-14);particles(g.parts,g.x,g.y,14,PEACH,180);g.pops.push({s:'−1',x:g.x,y:g.y-20,t:0,col:PEACH});if(g.lives<=0)return 'over';}
   if(!c.passed&&c.x+34<g.x){c.passed=true;const off=Math.abs(g.y-mid);const perfect=off<14;g.chain=perfect?g.chain+1:0;const m=perfect?Math.min(4,1+g.chain):1;g.score+=m;g.pops.push({s:perfect?`Perfecto ×${m}`:'+1',x:g.x,y:g.y-22,t:0,big:perfect});if(perfect)particles(g.parts,g.x,g.y,8,INK,120);}}
  g.cols=g.cols.filter(c=>c.x>-50);if(g.y>g.h-20||g.y<10){if(!g.inv){g.lives--;g.inv=1.6;g.chain=0;particles(g.parts,g.x,g.y,12,PEACH,160);if(g.lives<=0)return 'over';}g.y=clamp(g.y,20,g.h-30);g.vy=-260;}
  if(g.score>g.best)g.best=g.score;},
 draw(g,c){const {w,h}=g;c.fillStyle='#000';c.fillRect(0,0,w,h);const br=(Math.sin(g.t*Math.PI/4)+1)/2;c.fillStyle=`rgba(242,238,230,${.025+br*.03})`;c.beginPath();c.arc(w/2,h*.45,60+br*70,0,7);c.fill();
  c.fillStyle=FAINT;c.font='600 9px Inter';c.textAlign='center';c.fillText(br>.5?'INHALA':'EXHALA',w/2,h*.45+4);
  for(const k of g.cols){const m=k.cur??k.mid,top=m-k.gapH/2,bot=m+k.gapH/2;c.fillStyle='#ffffff18';c.fillRect(k.x+14,0,6,top);c.fillRect(k.x+14,bot,6,h-bot);
   for(let i=0;i<3;i++){c.fillStyle=i===0?INK:MUTED;rr(c,k.x+i*3,top-14-i*12,34-i*6,10,3);c.fill();rr(c,k.x+i*3,bot+4+i*12,34-i*6,10,3);c.fill();}
   if(!k.passed){c.setLineDash([2,4]);c.strokeStyle='#ffffff22';c.beginPath();c.moveTo(k.x,m);c.lineTo(k.x+34,m);c.stroke();c.setLineDash([]);}}
  if(!(g.inv&&Math.floor(g.inv*12)%2)){c.save();c.translate(g.x,g.y);c.rotate(clamp(g.vy/900,-.5,.8));plate(c,0,0,12,g.chain>1?PEACH:INK);c.restore();}
  stepParticles(g.parts,1/60,c);popups(g.pops,1/60,c);
  if(!g.started){c.fillStyle=MUTED;c.font='600 12px Inter';c.textAlign='center';c.fillText('Toca para impulsar el disco',w/2,h*.62);}},
 hud:g=>({score:g.score,lives:g.lives,extra:g.chain>1?`Perfecto ×${Math.min(4,1+g.chain)}`:'',best:g.best}),
 hint:'Toca para impulsar · pasa por el centro para encadenar «Perfecto»'};
/* ---------- 3 · Escuadrón retro ---------- */
const Shooter={id:'shooter',name:'Escuadrón retro',
 init(w,h){return{w,h,x:w/2,y:h-70,tx:w/2,ty:h-70,shots:[],en:[],eb:[],parts:[],pops:[],pw:[],fire:0,wave:0,waveT:1,lives:3,inv:0,score:0,dbl:0,shield:0,graze:0,t:0,shake:0,boss:null,best:store.get('shooter'),drag:null};},
 press(g,p){g.drag={px:p.x,py:p.y,sx:g.tx,sy:g.ty};},move(g,p){if(g.drag){g.tx=clamp(g.drag.sx+(p.x-g.drag.px)*1.15,16,g.w-16);g.ty=clamp(g.drag.sy+(p.y-g.drag.py)*1.15,g.h*.45,g.h-30);}},release(g){g.drag=null;},
 key(g,k,dt){const v=300*dt;if(k.ArrowLeft)g.tx-=v;if(k.ArrowRight)g.tx+=v;if(k.ArrowUp)g.ty-=v;if(k.ArrowDown)g.ty+=v;g.tx=clamp(g.tx,16,g.w-16);g.ty=clamp(g.ty,g.h*.45,g.h-30);},
 spawnWave(g){g.wave++;const n=g.wave;if(n%4===0){g.boss={x:g.w/2,y:-60,hp:40+n*4,max:40+n*4,t:0};g.pops.push({s:'Jefe',x:g.w/2,y:g.h*.3,t:0,big:1});return;}
  const pat=['line','v','snake'][n%3],cnt=6+Math.min(6,n);for(let i=0;i<cnt;i++){const e={hp:1+Math.floor(n/5),t:-i*.22,pat,i,cnt,x:0,y:-30,fire:rnd(1.2,3)};g.en.push(e);}g.pops.push({s:`Oleada ${n}`,x:g.w/2,y:g.h*.3,t:0,big:1});},
 update(g,dt){g.t+=dt;g.inv=Math.max(0,g.inv-dt);g.dbl=Math.max(0,g.dbl-dt);g.shake=Math.max(0,g.shake-dt*30);
  g.x+=(g.tx-g.x)*Math.min(1,dt*14);g.y+=(g.ty-g.y)*Math.min(1,dt*14);
  g.fire-=dt;if(g.fire<=0){g.fire=.17;if(g.dbl){g.shots.push({x:g.x-7,y:g.y-14},{x:g.x+7,y:g.y-14});}else g.shots.push({x:g.x,y:g.y-14});}
  for(const s of g.shots)s.y-=560*dt;g.shots=g.shots.filter(s=>s.y>-10);
  if(!g.en.length&&!g.boss){g.waveT-=dt;if(g.waveT<=0){this.spawnWave(g);g.waveT=1.2;}}
  for(const e of g.en){e.t+=dt;if(e.t<0)continue;const p=e.t;if(e.pat==='line'){e.x=((e.i+.5)/e.cnt)*g.w+Math.sin(p*1.2)*24;e.y=Math.min(70+(e.i%2)*28,p*180-30);}
   else if(e.pat==='v'){const k=e.i-(e.cnt-1)/2;e.x=g.w/2+k*30+Math.sin(p)*30;e.y=Math.min(60+Math.abs(k)*20,p*170-30);}else{e.x=g.w/2+Math.sin(p*2+e.i*.5)*(g.w*.36);e.y=p*60-20+e.i*2;}
   e.fire-=dt;if(e.fire<=0&&g.wave>1&&e.y>0){e.fire=rnd(2.2,4)-Math.min(1.2,g.wave*.08);const a=Math.atan2(g.y-e.y,g.x-e.x);g.eb.push({x:e.x,y:e.y,vx:Math.cos(a)*150,vy:Math.sin(a)*150,gz:false});}}
  if(g.boss){const b=g.boss;b.t+=dt;b.y=Math.min(90,b.y+60*dt);b.x=g.w/2+Math.sin(b.t*.8)*(g.w*.3);if(b.y>=90&&Math.floor(b.t*10)%9===0&&Math.floor((b.t-dt)*10)%9!==0){for(let i=0;i<8;i++){const a=Math.PI/2+(i-3.5)*.22+Math.sin(b.t)*.2;g.eb.push({x:b.x,y:b.y+20,vx:Math.cos(a)*140,vy:Math.sin(a)*140,gz:false});}}}
  for(const s of g.shots){for(const e of g.en){if(e.t>=0&&Math.abs(s.x-e.x)<13&&Math.abs(s.y-e.y)<12){s.y=-99;e.hp--;if(e.hp<=0){e.dead=1;g.score+=10;particles(g.parts,e.x,e.y,9,INK,150);if(Math.random()<.08)g.pw.push({x:e.x,y:e.y,k:Math.random()<.5?'dbl':'shield'});}}}
   if(g.boss&&Math.abs(s.x-g.boss.x)<38&&Math.abs(s.y-g.boss.y)<22){s.y=-99;g.boss.hp--;particles(g.parts,s.x,s.y,2,MUTED,80);if(g.boss.hp<=0){g.score+=200;particles(g.parts,g.boss.x,g.boss.y,40,PEACH,260);g.pops.push({s:'+200',x:g.boss.x,y:g.boss.y,t:0,big:1});g.pw.push({x:g.boss.x,y:g.boss.y,k:'dbl'});g.boss=null;g.shake=S.reduced?0:8;}}}
  g.en=g.en.filter(e=>!e.dead&&e.y<g.h+30);
  for(const b of g.eb){b.x+=b.vx*dt;b.y+=b.vy*dt;const d=Math.hypot(b.x-g.x,b.y-g.y);if(d<9&&!g.inv){b.y=g.h+99;if(g.shield){g.shield=0;g.inv=.8;g.pops.push({s:'Escudo',x:g.x,y:g.y-24,t:0});}else{g.lives--;g.inv=1.5;g.shake=S.reduced?0:7;particles(g.parts,g.x,g.y,16,PEACH,200);if(g.lives<=0)return 'over';}}
   else if(d<22&&!b.gz){b.gz=true;g.graze++;g.score+=5;g.pops.push({s:'Roce +5',x:g.x,y:g.y-26,t:0});}}
  g.eb=g.eb.filter(b=>b.y<g.h+20&&b.y>-20&&b.x>-20&&b.x<g.w+20);
  for(const p of g.pw){p.y+=90*dt;if(Math.hypot(p.x-g.x,p.y-g.y)<20){p.got=1;if(p.k==='dbl'){g.dbl=10;g.pops.push({s:'Disparo doble',x:g.x,y:g.y-30,t:0,big:1});}else{g.shield=1;g.pops.push({s:'Escudo',x:g.x,y:g.y-30,t:0,big:1});}}}
  g.pw=g.pw.filter(p=>!p.got&&p.y<g.h+20);if(g.score>g.best)g.best=g.score;},
 draw(g,c){const {w,h}=g;c.fillStyle='#000';c.fillRect(0,0,w,h);for(let i=0;i<40;i++){const y=((i*53+g.t*(20+i%3*25))%h);c.fillStyle=i%3?'#ffffff12':'#ffffff26';c.fillRect((i*97)%w,y,1.5,1.5);}
  c.fillStyle=INK;for(const s of g.shots)c.fillRect(s.x-1.5,s.y-6,3,9);
  for(const e of g.en){if(e.t<0)continue;c.fillStyle=e.hp>1?MUTED:INK;c.beginPath();c.moveTo(e.x,e.y+9);c.lineTo(e.x-11,e.y-6);c.lineTo(e.x-4,e.y-2);c.lineTo(e.x,e.y-8);c.lineTo(e.x+4,e.y-2);c.lineTo(e.x+11,e.y-6);c.closePath();c.fill();}
  if(g.boss){const b=g.boss;c.fillStyle=INK;rr(c,b.x-38,b.y-16,76,32,10);c.fill();c.fillStyle='#000';c.fillRect(b.x-26,b.y-3,52,6);c.fillStyle=MUTED;c.fillRect(b.x-34,b.y+14,8,10);c.fillRect(b.x+26,b.y+14,8,10);
   c.fillStyle='#ffffff18';c.fillRect(20,8,w-40,4);c.fillStyle=PEACH;c.fillRect(20,8,(w-40)*b.hp/b.max,4);}
  for(const b of g.eb){c.fillStyle=PEACH;c.beginPath();c.arc(b.x,b.y,3.5,0,7);c.fill();}
  for(const p of g.pw){c.strokeStyle=INK;c.lineWidth=2;c.beginPath();c.arc(p.x,p.y,9,0,7);c.stroke();c.fillStyle=INK;c.font='800 9px Syne';c.textAlign='center';c.fillText(p.k==='dbl'?'2×':'E',p.x,p.y+3);}
  if(!(g.inv&&Math.floor(g.inv*12)%2)){c.fillStyle=INK;c.beginPath();c.moveTo(g.x,g.y-14);c.lineTo(g.x-12,g.y+10);c.lineTo(g.x,g.y+5);c.lineTo(g.x+12,g.y+10);c.closePath();c.fill();c.fillStyle=PEACH;c.fillRect(g.x-2,g.y+6,4,4+Math.random()*4);
   if(g.shield){c.strokeStyle='#f2eee680';c.lineWidth=1.5;c.beginPath();c.arc(g.x,g.y,20,0,7);c.stroke();}}
  stepParticles(g.parts,1/60,c);popups(g.pops,1/60,c);},
 hud:g=>({score:g.score,lives:g.lives,extra:g.boss?'Jefe':g.dbl?'Disparo doble':g.wave?`Oleada ${g.wave}`:'',best:g.best}),
 hint:'Arrastra en cualquier punto para mover la nave · dispara sola'};
const GAMES={runner:Runner,flappy:Flappy,shooter:Shooter};
/* ---------- reloj de descanso (manda sobre el juego) ---------- */
function gtop(){const R=44,r=19,c=2*Math.PI*r;return `<div class="gtop glass" role="timer" aria-label="Descanso"><div class="mini-ring"><svg viewBox="0 0 44 44"><circle cx="22" cy="22" r="${r}" fill="none" stroke="#ffffff14" stroke-width="3"/><circle cx="22" cy="22" r="${r}" fill="none" stroke="#f2eee6" stroke-width="3" stroke-linecap="round" stroke-dasharray="${rest.left/rest.total*c} ${c}" transform="rotate(-90 22 22)" data-garc/></svg></div>
 <div class="t"><strong data-gclock>${clock(rest.left)}</strong><span data-gsub>Descanso · siguiente: Sentadilla serie 4</span></div><button class="sb" data-rest="-15" aria-label="Quitar 15 segundos">−15</button><button class="sb" data-rest="pause" aria-label="Pausar descanso">${ic(rest.paused?'play':'pause',13)}</button><button class="sb" data-rest="15" aria-label="Añadir 15 segundos">+15</button></div>`;}
function syncClock(el){const c=2*Math.PI*19;const a=$('[data-garc]',el);if(a)a.setAttribute('stroke-dasharray',`${Math.min(1,rest.left/rest.total)*c} ${c}`);const t=$('[data-gclock]',el);if(t)t.textContent=rest.over?'+'+clock(rest.extra):clock(rest.left);
 const s=$('[data-gsub]',el);if(s)s.textContent=rest.over?'Descanso cumplido · tiempo extra':rest.paused?'Descanso en pausa · el juego también':'Descanso · siguiente: Sentadilla serie 4';const p=$('[data-rest=pause]',el);if(p)p.innerHTML=ic(rest.paused?'play':'pause',13);}
function startRestClock(el,onZero){every(()=>{if(rest.paused)return;if(rest.over){rest.extra++;}else{rest.left--;if(rest.left<=0){rest.left=0;rest.over=true;onZero&&onZero();say('Descanso terminado');}}syncClock(el);},1000);
 el.addEventListener('click',e=>{const b=e.target.closest('[data-rest]');if(!b)return;const v=b.dataset.rest;if(v==='pause'){rest.paused=!rest.paused;say(rest.paused?'Descanso en pausa':'Descanso en marcha');}else if(!rest.over){rest.left=clamp(rest.left+(+v),0,600);rest.total=Math.max(rest.total,rest.left);}syncClock(el);});}
/* ---------- bucle ---------- */
function play(el,G,opts={}){const cv=$('canvas.gcanvas',el),dpr=2,W=cv.clientWidth,H=cv.clientHeight;cv.width=W*dpr;cv.height=H*dpr;const c=cv.getContext('2d');c.scale(dpr,dpr);
 let g=G.init(W,H),state='play',last=performance.now(),keys={},frozen=false,touched=false;const hud=$('[data-ghud]',el),msg=$('[data-gmsg]',el);
 const toLocal=e=>{const r=cv.getBoundingClientRect();return{x:(e.clientX-r.left)/r.width*W,y:(e.clientY-r.top)/r.height*H};};
 cv.onpointerdown=e=>{if(state!=='play'||frozen)return;touched=true;cv.setPointerCapture(e.pointerId);G.press(g,toLocal(e));};cv.onpointermove=e=>G.move&&G.move(g,toLocal(e));cv.onpointerup=()=>G.release(g);
 const kd=e=>{keys[e.key]=1;touched=true;if(e.key===' '||e.key==='ArrowUp'){if(G!==Shooter){e.preventDefault();if(!keys._sp){keys._sp=1;G.press(g,{x:0,y:0});}}}},ku=e=>{keys[e.key]=0;if(e.key===' '||e.key==='ArrowUp'){keys._sp=0;G.release(g);}};
 addEventListener('keydown',kd);addEventListener('keyup',ku);
 const drawHud=()=>{const d=G.hud(g);hud.innerHTML=`<div><b>${d.score.toLocaleString('es')}</b><div>${d.extra||'Récord '+d.best.toLocaleString('es')}</div></div><div class="lives" aria-label="${d.lives} vidas">${[0,1,2].map(i=>`<i class="${i<d.lives?'':'off'}"></i>`).join('')}</div>`;};
 const overlay=(html)=>{const o=document.createElement('div');o.className='gover';o.innerHTML=html;el.appendChild(o);return o;};
 const frame=now=>{const dt=Math.min(.033,(now-last)/1000);last=now;frozen=rest.paused||(rest.over&&!rest.keep);
  if(state==='play'&&!frozen){G.key&&G.key(g,keys,dt);const r=G.update(g,dt);if(r==='over'){state='over';const best=Math.max(store.get(G.id),Math.floor(G.hud(g).score));store.set(G.id,best);
    const o=overlay(`<span class="eyebrow">FIN DE LA PARTIDA</span><strong>${Math.floor(G.hud(g).score).toLocaleString('es')}</strong><p>Récord ${best.toLocaleString('es')}. El descanso sigue corriendo arriba; reiniciar la partida no lo toca.</p><button class="btn primary" style="width:210px" data-again>Otra partida</button><button class="btn ghost" data-close>Cerrar juego</button>`);
    $('[data-again]',o).onclick=()=>{o.remove();g=G.init(W,H);state='play';};$('[data-close]',o).onclick=()=>window.KpknProto.go('g-picker');}}
  c.save();if(g.shake){c.translate(rnd(-g.shake,g.shake),rnd(-g.shake,g.shake));}G.draw(g,c);c.restore();
  if(frozen&&state==='play'){c.fillStyle='#000a';c.fillRect(0,0,W,H);}drawHud();
  msg.innerHTML=rest.paused&&state==='play'?`<strong>En pausa</strong><span>El descanso está en pausa; el juego también.</span>`:(!touched&&opts.hint?`<span>${G.hint}</span>`:'');
  loop.raf=requestAnimationFrame(frame);};
 loop={raf:requestAnimationFrame(frame),off:()=>{removeEventListener('keydown',kd);removeEventListener('keyup',ku);}};
 startRestClock(el,()=>{const o=overlay(`<span class="eyebrow">DESCANSO TERMINADO</span><strong>Siguiente serie</strong><p>Sentadilla · serie 4 · 100 kg × 8. Tu partida queda congelada con ${Math.floor(G.hud(g).score).toLocaleString('es')} puntos.</p><button class="btn primary" style="width:220px">${ic('play',13)} Volver a la serie</button><button class="btn ghost" data-keep>Seguir jugando · cuenta como extra</button>`);
  $('[data-keep]',o).onclick=()=>{o.remove();rest.keep=true;rest.paused=false;};});
}
const gameScreen=G=>statusbar()+gtop()+`<canvas class="gcanvas" aria-label="${G.name}. ${G.hint}"></canvas><div class="ghud" data-ghud></div><div class="gmsg" data-gmsg></div>`;
function preview(cv,G){const dpr=2,W=84,H=84;cv.width=W*dpr;cv.height=H*dpr;const c=cv.getContext('2d');c.scale(dpr,dpr);const k=G.id;let t=0;
 const id=setInterval(()=>{t+=.05;c.fillStyle='#0a0a0a';c.fillRect(0,0,W,H);if(k==='runner'){c.fillStyle='#ffffff20';c.fillRect(0,62,W,2);const x=((W+30)-(t*60)%(W+60));dumbbell(c,x,48,26,14);const j=Math.max(0,Math.sin(t*3))*26;c.fillStyle=INK;c.beginPath();c.arc(24,40-j,5,0,7);c.fill();rr(c,19,45-j,10,12,3);c.fill();}
  else if(k==='flappy'){const x=((W+20)-(t*40)%(W+50));c.fillStyle=MUTED;rr(c,x,0,22,24,4);c.fill();rr(c,x,58,22,30,4);c.fill();plate(c,28,42+Math.sin(t*4)*8,8);}
  else{for(let i=0;i<4;i++){const ex=16+i*18,ey=20+Math.sin(t*2+i)*4;c.fillStyle=INK;c.beginPath();c.moveTo(ex,ey+6);c.lineTo(ex-7,ey-4);c.lineTo(ex+7,ey-4);c.fill();}const sx=42+Math.sin(t*1.5)*22;c.beginPath();c.moveTo(sx,58);c.lineTo(sx-8,72);c.lineTo(sx+8,72);c.fill();c.fillRect(sx-1,40-(t*80)%20,2,6);}},50);
 window.KpknProto.timers.add(id);}
const resetRest=(left=92)=>{rest.left=left;rest.total=150;rest.paused=false;rest.over=false;rest.extra=0;rest.keep=false;};
const SCENES={
 'g-picker':{label:'Selector de juegos',group:'Propuesta',render:()=>{resetRest();return statusbar()+gtop()+`<div class="picker">${[Runner,Flappy,Shooter].map(G=>`<button class="gcard" data-game="${G.id}"><canvas data-prev="${G.id}"></canvas><div><strong>${G.name}</strong><p>${{runner:'Salto con altura variable, doble salto con tiza, «¡Justo!» al rozar y racha que multiplica.',flappy:'Impulsos suaves y huecos que respiran contigo: el fondo marca inhala/exhala. Pasar por el centro encadena «Perfecto».',shooter:'Nave que sigues con el dedo sin taparla, oleadas con patrón, jefe cada cuatro y potenciadores.'}[G.id]}</p><small>Récord ${store.get(G.id).toLocaleString('es')} · 3 vidas</small></div></button>`).join('')}<div style="font-size:10.5px;color:var(--faint);text-align:center;line-height:1.45;margin-top:auto;padding-bottom:20px">El descanso manda: al llegar a cero el juego se congela y vuelves a la serie.</div></div>`;},
  after:el=>{$$('[data-prev]',el).forEach(cv=>preview(cv,GAMES[cv.dataset.prev]));$$('[data-game]',el).forEach(b=>b.onclick=()=>window.KpknProto.go('g-'+b.dataset.game));startRestClock(el);},
  notes:['Tres juegos rehechos, con identidad KPKN: dibujo crema sobre negro y el melocotón solo para el jugador, los golpes y los potenciadores.','Pensados para descansos de 1 a 3 minutos: se entiende en 2 segundos, se reinicia al instante y cada partida dura lo que dura el descanso.','El reloj del descanso vive arriba, siempre visible, con −15, pausa y +15.']},
 'g-runner':{label:'Salta las mancuernas',group:'Juegos',render:()=>{resetRest();return gameScreen(Runner);},after:el=>play(el,Runner,{hint:1}),
  notes:['Salto con altura variable: un toque es un salto corto; mantener, uno alto.','Tolerancias que hacen justo el juego: medio instante de gracia tras dejar el suelo y el toque anticipado se recuerda.','«¡Justo!» al rozar un obstáculo da más puntos. La racha multiplica hasta ×5; un golpe la corta.','La tiza da doble salto 8 s. La línea «Récord» marca dónde terminó tu mejor partida.','Espacio o toque para saltar.']},
 'g-flappy':{label:'Entre las pesas',group:'Juegos',render:()=>{resetRest();return gameScreen(Flappy);},after:el=>play(el,Flappy,{hint:1}),
  notes:['El disco cae suave y cada toque da un impulso corto. Los huecos se generan a distancia alcanzable: nunca hay saltos imposibles.','Pasar cerca del centro (línea punteada) encadena «Perfecto» y multiplica hasta ×4.','Un golpe no termina la partida: quita una vida, devuelve el disco al hueco y da un instante de invulnerabilidad.','El fondo respira en ciclos de 4 s (inhala/exhala) para bajar pulsaciones mientras juegas.']},
 'g-shooter':{label:'Escuadrón retro',group:'Juegos',render:()=>{resetRest();return gameScreen(Shooter);},after:el=>play(el,Shooter,{hint:1}),
  notes:['Control relativo: arrastra en cualquier punto y la nave te sigue sin quedar bajo el dedo. También con flechas.','Disparo automático, oleadas en línea, V y serpiente, y un jefe cada cuatro oleadas.','Potenciadores: disparo doble 10 s y escudo de un golpe. «Roce» suma puntos al esquivar por poco.']},
 'g-pause':{label:'Descanso en pausa congela el juego',group:'Reglas con el reloj',render:()=>{resetRest();rest.paused=true;return gameScreen(Runner);},after:el=>play(el,Runner),
  notes:['Pausar el descanso congela el juego en el acto: no se puede ganar ni perder mientras el reloj está parado.','Reanuda desde el botón del reloj, arriba.']},
 'g-end':{label:'El descanso llega a cero',group:'Reglas con el reloj',render:()=>{resetRest(4);return gameScreen(Flappy);},after:el=>play(el,Flappy,{hint:1}),
  notes:['A los 4 s el descanso termina: el juego se congela y aparece la siguiente serie.','Propuesta a decidir: «Seguir jugando» cuenta como tiempo extra de descanso, visible en el reloj, y nunca registra nada.','Reiniciar la partida nunca reinicia el descanso.']}
};
window.KpknGames={SCENES,stop,GAMES};
})();
