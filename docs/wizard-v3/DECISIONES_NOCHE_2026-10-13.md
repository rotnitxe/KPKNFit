# Decisiones que tomé por ti (noche del 12 al 13 de octubre de 2026)

Pediste que no te preguntara y que eligiera lo más recomendado para la app. Aquí están todas, con de qué trata cada una y por
qué. Si quieres cambiar alguna, dímelo y la ajusto.

## Asistente (ola K)
1. **«Brazos» cuenta hasta 2 puntos de prioridad por sesión y el tope total sube de 5 a 6.**
   - De qué trata: bíceps y tríceps se unieron en «Brazos».
   - Por qué: con 1 punto, quien antes priorizaba los dos perdería volumen de brazo.
2. **Dibujos de Brazos, Antebrazos y Espalda: cerré tras 3 rondas.**
   - De qué trata: el tríceps sigue algo marrón a tamaño pequeño.
   - Por qué: es la regla de no iterar sin fin. Ya se lee bien: brazo flexionado con bíceps, y antebrazo con barra.
3. **Variantes de Powerlifting: el título es «Tu estilo en sentadilla y peso muerto» y la banca se llama «Press banca».**
   - Por qué: «Press banca» es el nombre que usa el resto de la app.
4. **Pesajes: el % de grasa y el % de músculo se guardan como medidos, con la misma fecha y hora que el peso.**
   - Por qué: vienen de tu báscula, no de una estimación. Así salen en Cuerpo junto al resto.
5. **«Omitir» de los pesajes va a la izquierda del dock y el check sigue centrado.**
   - Por qué: «Omitir» es la salida secundaria, y el check está en el mismo sitio en todos los pasos.
6. **Ficha de cada ejercicio.**
   - De qué trata:
     - es una hoja que sube desde abajo: imagen del catálogo (o «Sin imagen»), nombre, la descripción del catálogo tal cual,
       músculos (principales, secundarios y estabilizadores) y articulaciones;
     - no lleva porcentajes, porque el catálogo no los tiene;
     - si cambiaste el ejercicio, la ficha es la del nuevo.
   - Por qué: pediste respetar el catálogo sin inventar texto.
7. **Portadas de los 30 programas a medida.**
   - De qué trata:
     - cada perfil tiene sus tres programas con estilos y fondos distintos: noche, color pleno o papel;
     - en los fondos claros, el título va sobre una base negra sólida;
     - en Calistenia cambié dos estilos para no repetir la combinación de otro perfil.
   - Por qué: identidad propia por portada.
8. **Portadas del catálogo.**
   - De qué trata:
     - las 77 portadas tienen identidad propia, con 10 estilos;
     - el estilo va según el carácter del programa: 5/3/1 lleva el número «531», Sheiko y Smolov el grabado, Westside formas planas…;
     - solo el 19,5 % son claras, para que la biblioteca siga siendo negra.
   - Por qué: pediste que cada portada fuera distinta y bonita.
9. **«Revisión y activación» como overlay.**
   - De qué trata:
     - arriba, «TODO LISTO · {nombre}, tu KPKN está listo»;
     - cuatro momentos (Tú, tu programa con su tarjeta, Nutrición y RINGS);
     - abajo, fijos: los avisos, la confirmación y «Activar y entrar a KPKN»;
     - en la revisión desaparece el botón redondo, para que no haya dos botones de activar.
   - Avisos: quité las líneas técnicas («Estado», «Reparto»…) y reescribí el resto en lenguaje llano.
   - Por qué: pediste un cierre que motive y sin la hoja «Cómo funciona».

## Importador de programas
10. **«Clean» y «Snatch» a secas son la cargada y el arranque completos.**
    - De qué trata: «Power clean» y «Hang clean» siguen yendo a su variante.
    - Por qué: es lo que significan en halterofilia.
11. **Las filas «x failure», «x AMRAP» o «x max» sin número se omiten, con aviso.**
    - De qué trata: series y carga de esa fila sí se leen.
    - Por qué: no inventamos repeticiones.
12. **Calculadoras de LiftVault: las cargas salen del 1RM de ejemplo (200), con una nota para cambiarlo.**
    - Por qué: mejor importarlas avisando que no importarlas.
13. **Regla nueva: un archivo que antes se importaba (aunque fuera a medias) nunca puede pasar a «no se importa nada».**
    - Por qué: sin nada importado ni siquiera llegas a la revisión para arreglarlo. Por eso devolví una versión del importador que
      dejaba en cero tres rutinas grandes.
14. **Tablas con una fila por serie: las filas seguidas del mismo ejercicio se juntan en uno.**
    - De qué trata: queda con N series, el rango de repeticiones, la carga mayor y una nota con las cargas. El calentamiento no cuenta.
    - Por qué: es como piensas tu rutina («press banca, 4 series»).
15. **Hoja sin semanas en la que un día se repite («DAY 1» otra vez): empieza una semana nueva.**
    - Por qué: 4 semanas × 6 días no deben salir como una sola semana de 24 días.

## Home
16. **Implementé el rediseño sin esperar tu visto bueno al plan.**
    - Por qué: me lo pediste al irte a dormir. El diseño está en `docs/home-v1/HOME_V1.md`.
17. **Saludo sin signos de exclamación: «Buenos días, Alex».**
    - Por qué: en Syne grande se ve más limpio.
18. **La animación de entrada suena una vez por arranque de la app, no cada vez que vuelves a Inicio.**
    - Por qué: repetirla cansa y retrasa leer los datos.
19. **Home usa los colores, letras y espaciados del asistente; el tema de Material del resto de la app no se toca aún.**
    - Por qué: cambiar el tema global afectaría a todas las pantallas, y dijiste que Program Detail y las demás vendrán con tus
      indicaciones.
20. **Los anillos de macros son los mismos del asistente, con sus colores.**
    - Por qué: lo que viste al terminar el asistente es lo que ves cada día.
21. **Ajuste manual de un RING en Home: dice «Tu ajuste manda sobre la estimación de hoy».**
    - Por qué: es la frase honesta. No prometo que «aprende de tu ajuste» sin haberlo comprobado en el motor.
22. **«Tu semana» (7 días tocables) sustituye al deslizado entre sesiones.**
    - De qué trata: el deslizado solo queda si hay dos sesiones el mismo día.
23. **Sin dato no hay cifra.**
    - De qué trata: un músculo sin dato sale «—» (antes, 100 %), y sin tu altura real no hay IMC ni FFMI (antes se inventaban
      170 cm).
24. **Permisos recomendados.**
    - De qué trata: el título pasa a «Activa tus avisos», con una campana, texto corto y los botones «Ir a Ajustes» y «Ahora no».
    - Por qué: es lo primero que ves en Home al salir del asistente.
25. **Barra inferior: la pestaña activa va en tinta con un punto color músculo; las demás, en gris cálido; sin amarillo.**
26. **Nutrición de hoy.**
    - De qué trata:
      - «+» redondo para registrar;
      - leyenda bajo los anillos y «Hidratos» en vez de «Carbohidratos»;
      - kcal una sola vez, a la derecha;
      - la marca blanca del anillo es tu meta.
    - Por qué: así cabe a 360 dp y con la letra al 130 %.
27. **Cuerpo.**
    - De qué trata: la línea del objetivo solo se dibuja si cabe en la escala de tus pesos, y el mes estimado solo aparece con al
      menos 7 días de datos y si vas hacia la meta.
    - Por qué: no prometer fechas sin datos.
28. **RINGS en Home.**
    - De qué trata:
      - el (i) abre la misma introducción animada del asistente en modo repaso;
      - el detalle oculta músculos sin ninguna sesión, porque el motor les da 100 % por defecto.
29. **La sesión del día (héroe).**
    - De qué trata:
      - el arte del programa ocupa la mitad de arriba y el texto va abajo, sobre negro;
      - «Editar» es un enlace de texto;
      - el aviso de carga solo sale hoy;
      - tocar los anillos de músculos abre el detalle de Músculos de RINGS.
30. **Todo va en la rama de integración del asistente (`feat/wizard-v3`), sin push ni versión nueva.**
    - Por qué: comparten piezas (portadas, RINGS, anillos), y publicar es decisión tuya.

## Pruebas
31. **La prueba en dispositivo fue en el emulador, porque tu Z Flip estaba plegado.**
    - Con la regla acordada, plegado no se usa.
    - Probé las variantes, los músculos, las portadas de «Elige tu programa» y el overlay de activación.
    - El arnés solo crea borradores de Entreno, así que Nutrición y RINGS los comprobé con las hojas de render. Conviene verlos en
      tu teléfono.
