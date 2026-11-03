# Paquete CARDIO-VIVO · prefijo `cv` · pestaña `cardio-vivo` · `maps/cardio-vivo.md`
Lee antes `BRIEF_COMUN.md`. Punto del usuario: **25 (25.1)** — el más exigente de la lista: «un rediseño total».

## Palabras del usuario (literal)
> 25. En la vista de cardio en la sesión en vivo HAY UNA TREMENDA CANTIDAD DE BUGS VISUALES IMPERDONABLES. 25.1 La tarjeta que dice "bloquear" "pausar" está demasiado elevada cuando debería ir más abajo y el contenido se corta antes, como si estuviera hecha para un telefono mas pequeño. El resto de elementos se ve triste y feo. Las animaciones se ven horripilantes, cambialas por algo más animado y abstracto, como las ondas de los diseños de las portadas de programa. El título del ejercicio está duplicado en grande y en pequeño y TODO se ve hecho de mala gana. Quiero un rediseño total. Además al hacer swipe horizontal en esa pidlora inferior me tira a opciones que no se entienden para que MIERDA SON y cuando hago swipes o presiono algo no hay transiciones o efectos de aparecer, necesito un prototipo de esta nueva visualizacion de cardio que realmente llegue al nivel. Hicimos prototitpos previos pero que NO SE APLICARON correctametne, lo mismo para la animación de transición a espacio decardio o fuerza, así que por ahí va la respuesta.

## Dónde está hoy (léelo)
Capturas: `h20-cardio-vivo` (título «Escaladora» duplicado, reloj 19:41 con figura de palitos, guía por esfuerzo RPE, «Sin datos de pulso», una tarjeta inferior
cortada con «Bloquear / Pausa» y tres puntos de páginas). La app hoy: `screens/workout/` (busca «cardio», «Bloquear», «Pausa», `CardioLive…`),
`services/workout/` (motor de cardio, GPS), `screens/sessioneditor/…Cardio…` para qué configura el usuario (forma Continuo/Series/Intervalos, tiempo o
distancia, RPE, GPS exterior).
**Base de diseño ya hecha y aprobada en lo conceptual**: `docs/design/live-session-iteration-03/` (`src/app.js`, `styles.css`; `?tab=cardio` en su
`prototipos.html`; léeme su README) — cardio con identidad Ref02 (negro, carbón, crema; color SOLO en las zonas), espacio propio a pantalla completa,
cuenta 3-2-1, trabajo en zona, por encima de la zona, sin pulso (guía RPE), recuperación, exterior con GPS, páginas Plan y Resumen, pausa, bloqueo con
mantener, cardio terminado con RPE real, y transiciones «Ahora vamos con cardio / con fuerza». El usuario dice que lo aplicado en la app NO coincide con
eso y que además lo de iteración 03 debe llegar a otro nivel. Léelo todo (también `docs/design/live-session-iteration-04/prototipo.html` y
`C:\Users\valen\Desktop\análisis-de-apps-de-cardio-para-kpkn-2026-10-20.md` si existe: análisis de apps de cardio) y rehaz la propuesta ya corregida.
El motor/diseño cardio v2 anterior vive en `C:\kw\u5\docs\design\cardio-v2\` (live.html, transiciones.html) si existe: úsalo como referencia.

## Qué debe contener el prototipo (todo para 360 × 880, comprobando 360 × 740)
1. **Rediseño total del espacio de cardio**: el título del ejercicio UNA sola vez (con jerarquía); cifra principal (tiempo restante/transcurrido o
   distancia) protagonista; métricas secundarias reales (ritmo, pulso si hay, zona, distancia, kcal estimada con «≈»); guía de esfuerzo cuando no hay
   pulso; progreso de la sesión de cardio (perfil de bloques/intervalos); estados vacíos honestos («Sin datos de pulso» con acción para conectar o
   registrar a mano) sin parecer error.
2. **Fondo animado abstracto «como las ondas de las portadas de programa»**: ondas/líneas que reaccionan a la zona de esfuerzo y al ritmo (más
   energía = más amplitud/velocidad), dibujadas con la técnica de `covers.js`; una variante por estado (calentamiento, trabajo, recuperación, pausa,
   por encima de la zona); versión estática para movimiento reducido. Nada de figuras de palitos.
3. **Panel inferior de controles** (Bloquear / Pausa / más) **más abajo y a la altura correcta para el Flip5** (360 × 880): que nada quede cortado ni
   demasiado elevado; en 360 × 740 se compacta con criterio. Las **páginas del panel** (el swipe horizontal): cada página con título/etiqueta visible y
   un propósito claro (p. ej. Controles · Plan · Datos), indicador comprensible, transición animada entre páginas (deslizamiento + fundido), y
   nada que «no se entienda para qué es». Explica en las notas qué hace cada página y qué traía la actual (qué eran esos «tres puntos»).
4. **Transiciones**: fuerza → cardio («Ahora vamos con cardio»: coreografía + deslizar para entrar) y cardio → fuerza; entrada con cuenta 3-2-1;
   pausa; bloqueo (mantener para desbloquear); fin de cardio con RPE real obligatorio. Dibújalas como escenas secuenciales Y como una escena
   interactiva que las encadene (botón «Simular transición»). Efectos de aparición en cada pulsación (nada cambia sin transición).
5. Variantes: cardio por tiempo, por distancia con GPS (exterior), por intervalos (series con trabajo/recuperación), sin pulso, con pulso en zona/fuera
   de zona, sesión mixta (fuerza + cardio) y solo cardio, cardio dentro de la vista Lista.
Entrega además, en el mapa, la **lista de diferencias entre la app actual y este diseño**, elemento por elemento, para que la implementación sea 1:1
(«lo que no esté aquí, se elimina»).
