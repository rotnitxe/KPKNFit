/* ================= PORTADAS ================= */
const fig=(st,pl,o={})=>`<figure>${cover('',st,pl,o)}<figcaption><b>${CSTYLES[st]}</b><span>${CPAL[pl].n}</span></figcaption></figure>`;
scene({tab:'portadas',id:'c-gallery',title:'Galería de portadas',doc:()=>`
<h2>Ocho portadas de líneas</h2>
<p>Todas siguen la línea de <b>Relieve</b>: trazos finos sobre fondo oscuro, monocromos, con una sola línea de acento que brilla un poco. El color, cuando aparece, va difuminado detrás (un halo) y nunca en bloques.</p>
<p><b>Relieve</b>: curvas de nivel. <b>Perfil</b>: la carga de cada semana del programa convertida en una cordillera de crestas apiladas. <b>Ondas</b>: líneas que se levantan en el centro, como un pulso. <b>Órbitas</b>: elipses inclinadas alrededor de la torre de KPKN. <b>Campo</b>: trazos que siguen una corriente. <b>Espiral</b>: una vuelta por semana, con la semana actual en acento. <b>Eco</b>: la cifra de semanas repetida en contornos. <b>Bruma</b>: el único estilo con color, todo difuminado, con líneas finas encima.</p>
<div class="cgrid">${fig('relieve','carbon')}${fig('perfil','carbon')}${fig('ondas','grafito')}${fig('orbitas','noche')}${fig('campo','musgo')}${fig('espiral','carbon')}${fig('eco','grafito',{num:'6'})}${fig('bruma','noche')}</div>
<h2>Cinco fondos</h2>
<p>Cuatro oscuros (Carbón, Grafito, Noche y Musgo) y uno claro (Crema) para quien lo prefiera. Cambia el fondo y el tono de las líneas; el acento es siempre uno solo.</p>
<div class="cgrid">${Object.keys(CPAL).map(k=>fig('relieve',k)).join('')}</div>
<div class="cgrid">${Object.keys(CPAL).map(k=>fig('ondas',k)).join('')}</div>
<h2>Con datos de verdad</h2>
<p>Perfil, Espiral y Eco se dibujan con el programa: la altura de cada cresta sigue la carga planificada de cada semana, la espiral da una vuelta por semana y Eco usa su número de semanas. Debajo, Perfil para tres programas distintos.</p>
<div class="cgrid">${fig('perfil','carbon',{vol:[.5,.62,.74,.86,.4,.55,.68,.8,.92,.3],now:2})}${fig('perfil','grafito',{vol:[.7,.7,.7,.7],now:1})}${fig('perfil','noche',{vol:[.3,.45,.6,.9,.95,.6,.2],now:4})}</div>
<h2>Qué cambia en la app</h2>
<p>El editor de portada pasa de 10 estilos de píxeles a estos 8 estilos con 5 fondos, con vista previa (Menús › Editar programa). La foto propia se mantiene. Los programas del asistente reciben Perfil si tienen bloques y Ondas si son una rutina.</p>`});
scene({tab:'portadas',id:'c-list',title:'En la lista de programas',html:()=>programsList(),
 notes:{tags:[['new','Portadas de líneas']],p:['Cada programa con su estilo y fondo. En la tarjeta del programa en curso la portada se recorta a lo ancho sin deformarse.']}});
scene({tab:'portadas',id:'c-hero',title:'En la portada del programa',html:()=>`${topbar('Semana')}${detailScroll(`${heroBlock()}<div style="height:22px"></div>${weekHead()}`)}`,
 notes:{p:['«Perfil» en Carbón: la cordillera sube en el bloque de fuerza y cae en la descarga, la forma de este programa. Un filete fino la separa del negro de la pantalla.']}});
