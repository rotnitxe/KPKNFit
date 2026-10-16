# Propuestas aprobadas · sesión en vivo (2026-10-16)

El usuario aprobó TODAS estas pantallas: «Me gustan todas». Hay que aplicarlas al diseño final **siendo fiel**, con sus ajustes
(abajo). Cada `.dc.html` es HTML y CSS legible: de ahí salen las medidas, los colores, los textos y la jerarquía.

| Archivo | Pantalla | Punto del usuario |
|---|---|---|
| `Main.dc.html` | Tarjeta de la serie: carga como héroe, chips iguales, slider de esfuerzo con fuego, botones «Avanzado» y «Modo visual», flechas compactas y relator | 3, 4, 5.1–5.5 |
| `A2-Avanzado.dc.html` | Reverso «Avanzado»: técnica en segmentado, la serie como cadena horizontal (principal → drop 1 → drop 2 → +) y solo el eslabón activo abierto en una fila | 4 |
| `A3-ModoVisual.dc.html` | «Modo visual»: cámara, foto o vídeo, «Ficha» en overlay, miniaturas flotantes y «Ver álbum» | 4 |
| `A4-Album.dc.html` | Álbum del ejercicio: historial por fecha con la serie, kg × reps e intensidad, y borrar con Deshacer | 4 |
| `B1-Cockpit.dc.html` | Cockpit que sube desde la misma barra inferior al deslizar, sin iconos extra ni modo dios | 6, 11 |
| `B2-Etiquetas.dc.html` | Hoja de etiquetas compacta | 7 |
| `B3-Tiempo.dc.html` | Tiempo de sesión: anillo, proyección y duración objetivo | 7 |
| `B4-Ultrarrapido.dc.html` | Ultrarrápido: las series que quedan en losas, con ajuste y registro | 7 |
| `B5-Voz.dc.html` | Modo de voz: dos losas visuales en vez de textos largos | 8 |
| `C1-OpcionesSerie.dc.html` | Opciones de la serie (pulsación larga en el paso de la serie) | 9 |
| `C2-OpcionesEjercicio.dc.html` | Opciones del ejercicio (pulsación larga en el carrusel), con el título a tamaño normal | 10 |
| `C3-Reordenar.dc.html` | Reordenar arrastrando: lo hecho y lo que está en curso, fijo | 10.3 |
| `D1-…D3-Resumen*.dc.html` | Resumen final en 3 secciones deslizables (Hoy · Levantamientos · Notas) con FAB Volver ✓ Compartir | 14, 14.1 |
| `E1-…E3-Historia*.dc.html` | Tarjetas para historias (general, ejercicio y mosaico): vidrio sobre la portada del programa | 14.2 |

## Las leyes que añadió el usuario al aprobar (mandan sobre los prototipos)
1. **La tarjeta de la serie NUNCA cambia de tamaño.** En ningún estado: el anverso, «Avanzado», «Modo visual», con o sin
   teclado, con o sin drops.
   - «Modo visual» cabe en la misma tarjeta.
   - Tiene un botón **«Expandir»**: con una animación de expansión, la tarjeta pasa a ocupar casi toda la pantalla, ENCIMA de todo
     (como overlay). Al plegar, vuelve a su tamaño.
   - El 80 % de cámara del prototipo vale para la vista expandida. En la tarjeta, la cámara usa el espacio que haya.
2. **La intensidad empieza en el mínimo.**
   - El slider de RIR/RPE arranca en la intensidad MÍNIMA de su escala (el extremo izquierdo), no en la mitad.
   - El objetivo del plan (por ejemplo RIR 2) se ve como una marca tenue sobre la pista, sin preseleccionarlo.
   - Los valores de la escala son los que ya tiene la app (el carrusel de hoy), no los del prototipo.
3. **Las acciones del relator van dentro de la frase.**
   - Son un ÉNFASIS dentro de la palabra pulsable: el mismo tamaño y la misma línea que el texto, y se distinguen solo para que se
     vea que se pueden pulsar.
   - No son botones aparte. El conjunto se lee como una sola oración.
4. **El resumen final nunca deja hueco abajo** entre el contenido y los FAB.
   - Sin forzar la altura de la hoja ni que cambie con cada sección: la hoja tiene una altura y cada sección reparte su contenido
     para verse llena.
   - El elemento principal de cada sección (la torre de anillos, los gráficos o el área de notas) crece para ocupar el espacio.
5. **Fieles al prototipo en el ASPECTO, fieles a la app en el CONTENIDO.**
   - El prototipo inventó algunas opciones como ejemplo. Cada opción, acción o dato que la app ya tiene debe estar; las que no
     existen en la app NO se añaden, salvo las que el usuario pidió (abajo).
   - Inventadas en el prototipo: no añadir sin preguntar:
     - «Myo-reps»;
     - los chips «−20 %»/«−30 %» del drop (sí se puede si la app ya sugiere el drop);
     - «Pausar», «Ajustar al plan» y «Avisarme al 90 %» del tiempo;
     - «Como la anterior»;
     - «Prueba de sonido»;
     - las sensaciones y «Para la próxima» de las notas (el área de notas sí);
     - «Saltar ejercicio», «Quitar de la sesión» o «Notas» si no existen ya en las opciones del ejercicio.
   - Pedidas por el usuario, sí se construyen:
     - el álbum por ejercicio con historial y borrar;
     - la ficha en overlay desde «Modo visual»;
     - el cockpit que sube deslizando, sin modo dios;
     - reordenar arrastrando;
     - el slider de esfuerzo con fuego;
     - el resumen en 3 secciones con notas;
     - las 3 tarjetas para compartir.
   - Ante la duda, mantener la opción de la app y avisarlo en el informe.
