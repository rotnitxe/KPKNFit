/* Playable design prototype. The game never writes to the rest timer. */
(()=>{
'use strict';
const W=360,H=260,ink='#eeeae0',blue='#9ebcdd',peach='#e9a38b';
let game=null,last=0,keys=new Set(),pointer=null;
const api=()=>window.KpknPrototypeV2;
const rand=(a,b)=>a+Math.random()*(b-a);
function start(mode){game={mode,playing:false,ended:false,age:0,score:0,combo:1,lives:3,hurt:0,spawn:1.6,objects:[],shots:[],particles:[],x:66,y:196,vy:0,fire:0,wave:1,power:0};if(mode==='runner')game.objects=[{x:270,y:189,w:23,h:24,type:'weight',passed:false}];if(mode==='flappy'){game.y=115;game.objects=[{x:250,y:75,w:36,h:91,type:'gate',passed:false}];}if(mode==='shooter'){game.x=180;game.y=220;game.objects=[90,180,270].map(x=>({x,baseX:x,y:35,w:21,h:16,type:'enemy',phase:0}));}keys.clear();pointer=null;}
function play(){if(!game)start(api()?.getGame().mode||'runner');game.playing=true;game.ended=false;}
function input(){if(!game?.playing)return;if(game.mode==='runner'&&game.y>=194)game.vy=game.power>0?-390:-345;else if(game.mode==='flappy')game.vy=-205;else if(game.mode==='shooter')game.x=game.x<180?250:110;}
function stop(){if(game)game.playing=false;}
function burst(x,y,color=peach,n=10){for(let i=0;i<n;i++)game.particles.push({x,y,vx:rand(-85,85),vy:rand(-95,45),life:rand(.2,.65),color});}
function hit(x,y){if(game.hurt>0)return;game.lives--;game.hurt=1.45;game.combo=1;burst(x,y);if(game.lives<=0){game.ended=true;game.playing=false;}}
function points(n,x,y){game.score+=n*game.combo;game.combo=Math.min(5,game.combo+1);burst(x,y,blue,5);}
function tick(dt){const g=game;g.age+=dt;g.hurt=Math.max(0,g.hurt-dt);g.power=Math.max(0,g.power-dt);g.spawn-=dt;g.wave=1+Math.floor(g.age/12);
 if(g.mode==='runner'){
  const speed=Math.min(220,118+g.age*2.2);g.vy+=850*dt;g.y+=g.vy*dt;if(g.y>196){g.y=196;g.vy=0;}g.score+=dt*2;
  if(g.spawn<=0){const gap=Math.random()<.24;g.objects.push({x:380,y:gap?211:189,w:gap?44:23,h:gap?12:24,type:gap?'gap':'weight',passed:false});if(Math.random()<.36)g.objects.push({x:410,y:142,w:12,h:12,type:'boost'});g.spawn=rand(1.35,1.8);}
  for(const o of g.objects){o.x-=speed*dt;if(o.type==='boost'&&Math.abs(o.x-g.x)<18&&Math.abs(o.y-g.y)<30){g.power=6;o.x=-100;points(15,g.x,g.y-20);}else if(o.type!=='boost'&&o.x<g.x+12&&o.x+o.w>g.x-8&&g.y>(o.type==='gap'?190:166))hit(g.x,g.y-15);if(!o.passed&&o.x+o.w<g.x&&o.type!=='boost'){o.passed=true;points(10,g.x,g.y-35);}}
 }else if(g.mode==='flappy'){
  const speed=76+Math.min(45,g.age);g.vy+=470*dt;g.y+=g.vy*dt;if(g.y<19||g.y>240){hit(g.x,g.y);g.y=Math.max(23,Math.min(230,g.y));g.vy=-125;}
  if(g.spawn<=0){g.objects.push({x:380,y:rand(65,155),w:36,h:91,type:'gate',passed:false});g.spawn=2.0;}
  for(const o of g.objects){o.x-=speed*dt;if(o.x<g.x+12&&o.x+o.w>g.x-12&&(g.y<o.y+7||g.y>o.y+o.h-7))hit(g.x,g.y);if(!o.passed&&o.x+o.w<g.x){o.passed=true;const precise=Math.abs(g.y-(o.y+o.h/2))<18;points(precise?25:10,g.x,g.y);}}
 }else{
  const velocity=175;g.x+=(keys.has('ArrowRight')?velocity*dt:0)-(keys.has('ArrowLeft')?velocity*dt:0);if(pointer!==null)g.x+=(pointer-g.x)*Math.min(1,dt*15);g.x=Math.max(18,Math.min(W-18,g.x));g.fire-=dt;
  if(g.fire<=0){g.shots.push({x:g.x,y:g.y-13});g.fire=.22;}
  if(g.spawn<=0){const count=Math.min(5,2+g.wave);for(let i=0;i<count;i++)g.objects.push({x:36+i*(288/(count-1)),baseX:36+i*(288/(count-1)),y:-i*16-20,w:21,h:16,type:'enemy',phase:rand(0,6.28)});g.spawn=Math.max(1.3,3-g.wave*.15);}
  for(const o of g.objects){o.y+=(32+g.wave*7)*dt;o.x=o.baseX+Math.sin(g.age*1.7+o.phase)*22;if(Math.abs(o.x-g.x)<22&&Math.abs(o.y-g.y)<21){hit(g.x,g.y);o.dead=true;}if(o.y>H+10){o.dead=true;g.combo=1;}}
  for(const s of g.shots){s.y-=310*dt;for(const o of g.objects){if(!o.dead&&Math.abs(s.x-o.x)<17&&Math.abs(s.y-o.y)<13){o.dead=true;s.y=-100;points(10,o.x,o.y);}}}g.shots=g.shots.filter(s=>s.y>-12);
 }
 g.objects=g.objects.filter(o=>o.x>-60&&!o.dead);for(const p of g.particles){p.x+=p.vx*dt;p.y+=p.vy*dt;p.life-=dt;}g.particles=g.particles.filter(p=>p.life>0);
}
function rect(c,x,y,w,h,color){c.fillStyle=color;c.fillRect(x,y,w,h);}
function weight(c,x,y,w=23,color=ink){rect(c,x,y+8,w,4,color);rect(c,x,y+3,5,14,color);rect(c,x+w-5,y+3,5,14,color);}
function draw(c){const g=game;c.clearRect(0,0,W,H);const bg=c.createLinearGradient(0,0,W,H);bg.addColorStop(0,'#17212b');bg.addColorStop(1,'#090d13');c.fillStyle=bg;c.fillRect(0,0,W,H);
 c.strokeStyle='#ffffff09';c.lineWidth=1;for(let x=0;x<W;x+=30){c.beginPath();c.moveTo(x,0);c.lineTo(x,H);c.stroke();}for(let y=0;y<H;y+=30){c.beginPath();c.moveTo(0,y);c.lineTo(W,y);c.stroke();}
 c.fillStyle='#8b98a6';c.font='8px Inter, sans-serif';c.fillText(g.mode==='runner'?'SALTA · ENCADENA · RECOGE':g.mode==='flappy'?'ENCUENTRA TU RITMO':'OLEADA '+g.wave+' · DISPARO AUTOMÁTICO',14,20);
 if(g.mode==='runner'){
  for(let i=0;i<7;i++){const x=((i*75-g.age*15)%440+440)%440-40;rect(c,x,125-(i%3)*18,25,80+(i%3)*18,'#24303a');rect(c,x+4,129-(i%3)*18,2,65,'#30404c');}
  rect(c,0,216,W,2,'#b4b4ae');rect(c,0,218,W,42,'#10151a');
  for(const o of g.objects){if(o.type==='gap'){rect(c,o.x,214,o.w,46,'#070a0e');rect(c,o.x-3,214,3,6,peach);rect(c,o.x+o.w,214,3,6,peach);}else if(o.type==='boost'){c.strokeStyle=blue;c.beginPath();c.arc(o.x,o.y,10,0,Math.PI*2);c.stroke();rect(c,o.x-2,o.y-5,4,10,ink);rect(c,o.x-5,o.y-2,10,4,ink);}else weight(c,o.x,o.y,o.w,peach);}
  c.save();c.translate(g.x,g.y);c.globalAlpha=g.hurt>0&&Math.floor(g.age*14)%2?.3:1;const stride=g.y>=194?Math.sin(g.age*15)*6:4;rect(c,-6,-29,12,11,ink);rect(c,-7,-17,14,18,ink);rect(c,-13,-14,6,4,ink);rect(c,7,-12,8,4,ink);rect(c,-7,0,5,13+stride,ink);rect(c,3,0,5,13-stride,ink);rect(c,-7,11+stride,10,4,peach);rect(c,3,11-stride,10,4,peach);if(g.power>0){c.strokeStyle=blue;c.beginPath();c.arc(0,-8,30,0,Math.PI*2);c.stroke();}c.restore();
 }else if(g.mode==='flappy'){
  for(const o of g.objects){rect(c,o.x+13,24,10,o.y-24,'#657582');rect(c,o.x+13,o.y+o.h,10,H-o.y-o.h,'#657582');weight(c,o.x,o.y-18,36,peach);weight(c,o.x,o.y+o.h-3,36,peach);c.strokeStyle='#9ebcdd38';c.setLineDash([3,5]);c.beginPath();c.moveTo(o.x,o.y+o.h/2);c.lineTo(o.x+36,o.y+o.h/2);c.stroke();c.setLineDash([]);}
  c.save();c.translate(g.x,g.y);c.rotate(Math.max(-.35,Math.min(.5,g.vy/400)));c.globalAlpha=g.hurt>0&&Math.floor(g.age*14)%2?.3:1;c.fillStyle=ink;c.beginPath();c.ellipse(0,0,13,10,0,0,Math.PI*2);c.fill();rect(c,9,-2,8,4,peach);rect(c,2,-5,3,3,'#141414');rect(c,-17,2,10,4,blue);c.restore();
 }else{
  for(let i=0;i<32;i++){const x=(i*73+11)%W,y=(i*41+g.age*(12+i%4*8))%H;rect(c,x,y,1,2,'#8795a066');}
  for(const o of g.objects){c.save();c.translate(o.x,o.y);rect(c,-10,-7,20,5,peach);rect(c,-15,-2,30,5,peach);rect(c,-11,3,5,5,peach);rect(c,6,3,5,5,peach);rect(c,-5,-2,3,3,'#151515');rect(c,3,-2,3,3,'#151515');c.restore();}
  for(const s of g.shots)rect(c,s.x-1,s.y,2,9,blue);c.save();c.translate(g.x,g.y);c.globalAlpha=g.hurt>0&&Math.floor(g.age*14)%2?.3:1;c.fillStyle=ink;c.beginPath();c.moveTo(0,-16);c.lineTo(15,11);c.lineTo(0,5);c.lineTo(-15,11);c.closePath();c.fill();rect(c,-3,7,6,5+(Math.floor(g.age*12)%2)*5,blue);c.restore();
 }
 for(const p of g.particles){c.globalAlpha=Math.min(1,p.life*2);rect(c,p.x,p.y,3,3,p.color);}c.globalAlpha=1;
 if(!api()?.getRest().running&&g.playing){c.fillStyle='#090d13bb';c.fillRect(0,0,W,H);c.fillStyle=ink;c.font='600 17px Inter';c.textAlign='center';c.fillText('Descanso en pausa',W/2,H/2);c.textAlign='left';}
}
function paint(){if(!game)return;document.querySelectorAll('.game-canvas').forEach(canvas=>{const c=canvas.getContext('2d');c.setTransform(canvas.width/W,0,0,canvas.height/H,0,0);draw(c);});document.querySelectorAll('.v2-game-start').forEach(e=>e.hidden=game.playing||game.ended);document.querySelectorAll('.v2-game-result').forEach(e=>{e.hidden=!game.ended;const s=e.querySelector('span');if(s)s.textContent=Math.floor(game.score)+' puntos · el descanso continúa';});document.querySelectorAll('[data-game-score]').forEach(e=>e.textContent=Math.floor(game.score)+' puntos');document.querySelectorAll('[data-game-combo]').forEach(e=>e.textContent=game.power>0?'Salto ↑ '+Math.ceil(game.power)+' s':'Racha ×'+game.combo);document.querySelectorAll('[data-game-lives]').forEach(e=>e.textContent=game.lives+' vidas');}
function frame(now){const dt=Math.min(.035,(now-last)/1000||0);last=now;if(api()?.getGame().visible){if(!game||game.mode!==api().getGame().mode)start(api().getGame().mode);if(game.playing&&!document.hidden&&api().getRest().running&&api().getRest().seconds>0)tick(dt);paint();}requestAnimationFrame(frame);}
document.addEventListener('keydown',e=>{if(/INPUT|TEXTAREA|SELECT/.test(e.target.tagName)||!api()?.getGame().visible)return;if([' ','ArrowUp','ArrowLeft','ArrowRight'].includes(e.key)){e.preventDefault();keys.add(e.key);if((e.key===' '||e.key==='ArrowUp')&&!e.repeat)input();}});
document.addEventListener('keyup',e=>keys.delete(e.key));
document.addEventListener('pointerdown',e=>{if(!e.target.matches('.game-canvas'))return;e.target.setPointerCapture?.(e.pointerId);if(game?.mode==='shooter'){const r=e.target.getBoundingClientRect();pointer=(e.clientX-r.left)/r.width*W;}else input();});
document.addEventListener('pointermove',e=>{if(game?.mode!=='shooter'||!e.target.matches('.game-canvas')||!e.buttons)return;const r=e.target.getBoundingClientRect();pointer=(e.clientX-r.left)/r.width*W;});
document.addEventListener('pointerup',()=>pointer=null);
window.KpknArcade={start,play,input,stop};start(api()?.getGame().mode||'runner');requestAnimationFrame(frame);
})();
