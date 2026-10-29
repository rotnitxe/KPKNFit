/* ================= REGLAS · LO QUE PEDISTE Y LO QUE CAMBIA ================= */
const NP_MAP=[
 ['Los números son demasiado grandes y se ven cortados','Cifras en Inter SemiBold con dígitos tabulares; Syne solo en títulos y en el total de la comida. Si una cifra no cabe, se encoge: nunca se corta.','Todas'],
 ['El selector de tamaño con círculos','Un control segmentado de cuatro tramos con los gramos debajo: Pequeña 100 · Mediana 200 · Grande 300 · Extra 400 g.','Ajustar'],
 ['«Aporta» es horrendo y desordenado','Una línea: las kcal grandes, una barra de macros y tres columnas. Sin cajas.','Ajustar'],
 ['Los gráficos de vitaminas y minerales parecen de Excel','Huella radial en tinta: un radio por nutriente, sin rejilla, sin ejes, sin arcoíris. La misma pieza en el registro, en la hoja del alimento y en el día.','Vitaminas'],
 ['«Cómo lo cocinaste»: símbolos que no se entienden','Chips de texto, sin símbolos, en un orden fijo y lógico.','Ajustar'],
 ['«Variante» duplica «Cómo lo cocinaste» y al elegir «Al horno» desaparece','Una sola elección: «Cómo lo preparaste». El aceite es una fila que se adapta a la preparación y nunca desaparece.','Ajustar'],
 ['Faltan opciones lógicas: frito, salteado, al horno, crudo…','Crudo · Cocido · Al vapor · A la plancha · Al horno · Salteado · Frito. Solo las que tienen sentido para ese alimento.','Ajustar'],
 ['El anillo de calorías con el número apretado','Fuera el anillo en el registro: «297 kcal» en una línea, barra y tres columnas. En Inicio, un anillo fino con la cifra a su medida.','Registro · Inicio'],
 ['Los avisos de otro color se ven feos','La duda es una línea con un punto de acento, no una caja de color.','Registro'],
 ['«Enter para analizar» colapsado en vertical','Sale ese texto. La tecla Enter del teclado dice «Analizar» y el botón queda a la derecha.','Registro'],
 ['Las hojas aparecen bruscas','El fondo se oscurece en 220 ms y la hoja sube en 420 ms con freno suave; su contenido entra un instante después. Al cerrar, al revés.','Ajustar'],
 ['Más plano, menos colorido, más elegante','Una superficie por nivel, sin degradados ni vidrio dentro del registro. Un solo acento.','Todas'],
 ['La barra «¿Qué comiste?» de Inicio que nunca pediste','Sale. La tarjeta «Nutrición de hoy» gana un botón simple «Registrar comida».','Inicio'],
 ['Elegir un plan del catálogo abre una hoja de puro texto','La ficha visual del asistente (la de «Elegir este programa»), con «Configurar este plan».','Program Detail'],
 ['Las pestañas flotantes son transparentes, sin desenfoque','Vidrio con desenfoque real, como la barra inferior.','Program Detail'],
 ['⋯ quita sitio y las pestañas acaban en «…»','⋯ baja a la fila del nombre del programa, con su punto de pendientes. La barra queda: ← y las tres pestañas.','Program Detail'],
 ['«Ver lo que has hecho en 4 semanas» no se entiende','Una tarjeta «Tus últimas 4 semanas» que abre un resumen de verdad: sesiones, fuerza, récords, semana a semana, músculos, esfuerzo y lo que viene.','Program Detail'],
 ['«Sobre el programa» es una hoja de puro texto','La misma ficha visual, en modo lectura, con todo lo que tenía la hoja.','Program Detail'],
];
scene({tab:'decisiones',id:'d-reglas',title:'Reglas y mapa',doc:()=>`
 <h2>Seis reglas para toda Nutrición</h2>
 <p>Valen para el registro de comida, la hoja de un alimento, el día de Nutrición y la tarjeta de Inicio. Lo que no las cumpla, se cambia.</p>
 <div class="np-rules">
  <div><b>Las cifras caben</b><p>Inter SemiBold con dígitos tabulares para todo número. Syne queda para títulos y el total de la comida. Si una cifra no cabe en su sitio, se encoge; nunca se corta ni termina en «…».</p></div>
  <div><b>Plano</b><p>Tres superficies: #121212, #1c1c1c y #2a2a2a. Sin degradados, sin vidrio y sin bordes de color dentro del registro. La profundidad la da el tono, no la sombra.</p></div>
  <div><b>Tinta, no arcoíris</b><p>Proteína, carbos y grasa en tres tonos de la misma tinta. Las vitaminas y minerales, en tinta. El color deja de decorar.</p></div>
  <div><b>Un acento, para lo que espera</b><p>El naranja #f49a6e solo marca una duda que espera tu respuesta. El rojo, solo lo que pasa de un máximo (sodio, cafeína).</p></div>
  <div><b>Las hojas entran</b><p>Fondo que se oscurece, hoja que sube con freno suave y contenido que aparece un instante después. Respeta «reducir movimiento».</p></div>
  <div><b>Una sola forma de decir cómo se cocinó</b><p>«Cómo lo preparaste» reúne lo que hoy reparten «Cómo lo cocinaste» y «Variante». El aceite es una fila aparte que se adapta.</p></div>
 </div>
 <div class="np-sw"><span><i style="background:#121212;box-shadow:inset 0 0 0 1px #ffffff1a"></i>Superficie 1</span><span><i style="background:#1c1c1c"></i>Superficie 2</span><span><i style="background:#2a2a2a"></i>Superficie 3</span><span><i style="background:var(--np-p)"></i>Proteína</span><span><i style="background:var(--np-c)"></i>Carbos</span><span><i style="background:var(--np-f)"></i>Grasa</span><span><i style="background:var(--np-acc)"></i>Duda</span><span><i style="background:var(--danger)"></i>Sobre el máximo</span></div>
 <h2>Lo que pediste → lo que cambia</h2>
 <div class="np-mapw"><table class="np-map"><thead><tr><th>Lo que pediste</th><th>Lo que cambia</th><th>Pestaña</th></tr></thead><tbody>${NP_MAP.map(r=>`<tr><td>${r[0]}</td><td>${r[1]}</td><td>${r[2]}</td></tr>`).join('')}</tbody></table></div>
 <h2>Lo que sale</h2>
 <p>«Enter para analizar». «Seguir con la estimación» (guardar ya sigue con lo estimado). La opción repetida «porción (150 g)» cuando vale lo mismo que «Lo estimado». Los símbolos de «Cómo lo cocinaste». La fila «Variante». «Con mantequilla» (contaba igual que «poco» aceite). «Crudo» en alimentos que no se comen crudos, como las papas fritas. Las cajas de color de los avisos. El anillo de calorías del registro. La barra «¿Qué comiste?» de Inicio. Las hojas de texto de los planes. ⋯ de la barra flotante.</p>
 <p>No sale ninguna función: «Cambiar» el alimento, gramos o raciones, «Toca una cifra para corregirla a mano», la lista completa de nutrientes, el rebote del anillo de Inicio al guardar y la procedencia de cada plan siguen ahí.</p>
 <h2>Lo que tienes que aprobar</h2>
 <div class="np-ask"><b>1 · Vitaminas y minerales: huella radial (A) o cápsulas (B)</b><p>Recomiendo la huella: se lee de un vistazo, ocupa poco y los tres que más aportan llevan su nombre. Las cápsulas están en la última escena de «Vitaminas y minerales».</p></div>
 <div class="np-ask"><b>2 · Macros en tres tonos de tinta</b><p>Proteína clara, carbos gris medio, grasa gris oscuro. Si prefieres conservar un color por macro, iría muy apagado y solo en la barra.</p></div>
 <div class="np-ask"><b>3 · «Con mantequilla» sale</b><p>Hoy cuenta exactamente igual que «poco» aceite. Si quieres distinguir la mantequilla, haría falta su propia grasa en el cálculo.</p></div>
 <div class="np-ask"><b>4 · Program Detail: la ficha en los dos sitios</b><p>Al elegir un plan del catálogo (con «Configurar este plan») y en «Sobre el programa» (solo lectura). Es la ficha del asistente, no una nueva.</p></div>
 <div class="np-ask"><b>5 · Qué cuenta «Tus últimas 4 semanas»</b><p>Las 4 semanas de calendario que terminan hoy, aunque crucen un cambio de bloque. Si el programa lleva menos, cuenta las que haya y lo dice.</p></div>`});
