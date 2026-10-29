'use strict';
document.getElementById('app').innerHTML=K.home();
document.addEventListener('click',event=>{
 const day=event.target.closest('[data-day]');
 if(day){const i=Number(day.dataset.day);document.querySelectorAll('[data-day]').forEach(el=>{const selected=el===day;el.classList.toggle('selected',selected);el.setAttribute('aria-selected',String(selected));});document.querySelector('.week-strip').style.setProperty('--selected',i);document.getElementById('day-hero').innerHTML=K.hero(i);}
 const action=event.target.closest('[data-action]')?.dataset.action;
 if(action==='rings-info')K.sheet('Tus RINGS','<p>Músculos, Energía y Columna muestran tres dimensiones de tu recuperación.</p><div class="divider"></div><p>Los valores del motor pueden ajustarse con tus sensaciones. Sin datos, el canal aparece sin calibrar.</p>');
 if(action==='cancel-session')K.sheet('Sesión en curso','<p>Mixta · Fuerza y cardio</p><a class="pill" href="./index.html#live">Reanudar sesión</a><button class="pill secondary" data-action="close-sheet">Seguir en Home</button>');
 const ring=event.target.closest('[data-ring]')?.dataset.ring;
 if(ring)K.sheet(ring,`<div class="tower-wrap">${K.tower({character:false})}</div><p>${ring}: 100 % · Recuperado</p><a class="pill secondary" href="./index.html#sensors">Ver datos y procedencia</a>`);
});
