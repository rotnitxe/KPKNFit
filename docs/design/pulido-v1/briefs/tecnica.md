# Paquete TÉCNICA · prefijo `tc` · pestaña `tecnica` · `maps/tecnica.md`
Lee antes `BRIEF_COMUN.md`. Punto del usuario: **2**.

## Palabras del usuario (literal)
> 2. El analizador de técnica visual, tanto desde el perfil como de la sesión en vivo deben rediseñarse para que no sea necesario scrollear para ver la información, que esté todo integrado, por ejemplo, que desde el mismo video se muestren los datos con un diseño hermoso. La animación de "analizando" deben verse en primer plano con una animación. Dame prototipos de cómo se verán para aprobarlos antes de implementarlo.

## Dónde está hoy (léelo)
`screens/technique/analysis/` (`TechniqueAnalysisUiState.kt`, `design/CameraTipsModule.kt` — «Para medir mejor» —, el resto de la carpeta),
`screens/workout/WorkoutVideoAnalysisController.kt`, y las entradas: desde **Perfil** (analizar un vídeo de la galería para un ejercicio) y desde la **sesión en
vivo** (modo visual de la tarjeta: grabar/subir el vídeo de la serie). Capturas: `h02-tecnica` (vídeo arriba, controles de reproducción, selector 0,25×/0,5×/1×,
pestañas Trayectoria · Métricas · Repeticiones que obligan a hacer scroll, avisos como «Se perdió el seguimiento continuo del disco… Pulsa Cambiar para volver a
marcarlo», «PARA MEDIR MEJOR»). Lee qué mide el motor (`domain/biomechanics/`, `services/…`, ola «VT»: trayectoria de la barra/disco, velocidad en m/s,
repeticiones, ROM, simetría, estabilidad…, y la confianza/estado de cada métrica); documentación de la ola en `docs/video-technique/` si existe.

## Qué debe contener el prototipo
1. **Pantalla de resultado, todo integrado en el vídeo, sin scroll**: el vídeo ocupa la pantalla y los datos se muestran SOBRE él con diseño hermoso:
   trayectoria dibujada con degradado/estela, velocidad y fase de cada repetición, marcas de repetición en la línea de tiempo, métricas clave como
   chips/lecturas flotantes de vidrio que cambian según el instante, calidad/confianza de la medición, y el detalle completo en una hoja con alturas
   (tirador) en lugar de pestañas con scroll. Reproducción: play/pausa, anterior/siguiente repetición, velocidades 0,25×/0,5×/1×, bucle por repetición,
   modo «solo trayectoria» / «con datos» / «limpio».
2. **«Analizando» en primer plano** con animación propia (no un spinner suelto): fases reales del análisis (detectar, seguir la barra, contar repeticiones,
   calcular métricas…) con progreso honesto, una animación abstracta en el lenguaje de las portadas, y cancelar. Estados: analizando, éxito (cómo entra
   el resultado), fallo parcial, sin repeticiones detectadas, vídeo demasiado corto/oscuro.
3. **Avisos de calidad** («Se perdió el seguimiento continuo del disco…», «Cambiar» para volver a marcar el objetivo) integrados sin ocupar medio
   pantalla; «**Para medir mejor**» (consejos de cámara) como una ayuda previa contextual (antes de grabar) y como pista corta en el resultado.
4. **Una sola pieza para las dos entradas**: desde Perfil (elige ejercicio y vídeo; sin serie asociada) y desde la sesión en vivo (viene con serie y
   ejercicio; «Usar para esta serie»/«Guardar en el registro»). Muestra qué cambia en cada una y cómo se vuelve (cerrar ✕, ⋯).
5. Marcado manual del objetivo («Cambiar»: tocar el disco/barra en un fotograma) como estado propio. Texto 130 %, 360 × 740, vídeo vertical y horizontal.
El vídeo del prototipo es sintético (CSS/SVG: un gimnasio abstracto, una barra con discos que sube y baja siguiendo una trayectoria): que se sienta
real, con la barra moviéndose en bucle y los datos reaccionando.
