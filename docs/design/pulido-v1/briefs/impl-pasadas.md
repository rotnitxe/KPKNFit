# Las tres pasadas de un paquete (cada una la hace un agente distinto)

Todas siguen `briefs/impl-COMUN-v2.md`. Un paquete se implementa en su propio worktree (`C:\kw\pu-<prefijo>`, rama `pulido/<prefijo>`, creado desde `feat/pulido-v1`) y se fusiona en `feat/pulido-v1` cuando pasa las puertas.

## Pasada A · estructura, estados y lógica
Construye en la app lo que las escenas del paquete muestran, **sin** profundidad ni movimiento:
- Layout con las medidas EXACTAS del lienzo (en dp), tipografía (familia, peso, tamaño, interlineado), colores, textos, iconos, orden, bordes de 1 px y fondos planos, y TODOS los estados de cada escena (reposo, vacío, error, cargando, hojas, menús, confirmaciones, desactivado, 130 % de texto, 360 × 740).
- Lógica y datos que pide `maps/<paquete>.md` (cambios de motor y de datos), con sus tests; **elimina** lo que la pantalla vieja tiene y el lienzo no; los arreglos directos que ya están en la rama se igualan al lienzo.
- La lógica de los gestos (umbrales, estados, posiciones) sí es de esta pasada; su animación y su respuesta visual son de la pasada B/C. Deja marcado con un comentario `// pulido:<ID>` el sitio de cada fila S, V, G, A, T, X o J de la matriz que toque a este código.
- **Verificación de la pasada**: compila por la cola, tests de las clases tocadas en verde, y una hoja de render (`<Paquete>RenderSheetTest`, modelo: las `*RenderSheetTest` existentes) que dibuje CADA escena del paquete con los datos de muestra del lienzo a 360 × 880 (y 360 × 740 y 130 % en las densas),
  comparada con `node tools/compare.cjs`. Esta pasada no puede igualar las sombras ni el vidrio todavía: el informe separa la diferencia por estructura (tiene que ser casi nula) de la que se explica por profundidad pendiente.

## Pasada B/C · profundidad y movimiento
Cierra las filas **S, V, G** (sombras, vidrio, degradados y resplandores) y **A, T, X, J** (animaciones, transiciones, transformaciones y movimiento en JS) de `paridad/<prefijo>.md`, TODAS, usando los cimientos (`ui/design/depth`, `ui/design/motion`) y los valores exactos de la fila:
- Cada fila acaba `[x]` con `archivo.kt:línea` o `[~]` con `DESV-nn` registrada y captura lado a lado. `node tools/paridad-check.cjs <prefijo> --repo=C:\kw\pulido` debe salir en verde.
- Movimiento: duraciones, curvas y retardos de la fila, no «parecidos». Los gestos responden visualmente como en la ficha de movimiento de la escena. Animaciones infinitas con su periodo exacto; versión estática con movimiento reducido.
- Puertas 2 y 3: re-ejecuta la hoja de render y compara con el lienzo (ahora sí con sombras y vidrio); renderiza los fotogramas de cada animación (0, 25, 50, 100 % de su duración, con `mainClock`) y compara con `node tools/shots.cjs shot <id> --t=<ms>`.

## Pasada D · auditoría independiente
No conoces el código de las pasadas anteriores y no lo defiendes. Haces:
1. `paridad-check` y la lista de filas `[~]` con su desviación: ¿están justificadas y tienen captura?
2. **Muestreo duro**: abre el archivo:línea de cada fila marcada `[x]` de las categorías A, T, X y J (todas) y de un tercio de S, V y G, y comprueba leyendo que ese código implementa EXACTAMENTE lo que dice la fila (valores, duración, curva, retardo). Cada discrepancia es un fallo.
3. Re-ejecuta las hojas de render del paquete y compáralas con el lienzo (todas las escenas) y los fotogramas de movimiento; busca elementos que la app tiene y el lienzo no (se eliminan) y a la inversa.
4. Revisa a mano la accesibilidad, 130 % de texto y 360 × 740.
Entregas: `paridad/<prefijo>-auditoria.md` con veredicto por puerta (1 matriz, 2 render, 3 fotogramas), la lista de fallos con archivo:línea y los números; corriges solo lo evidente y lo anotas; lo demás lo devuelves a la pasada que corresponda. **No aprueba nada sin los números.** La puerta 4 (Flip) la cierra el orquestador.
