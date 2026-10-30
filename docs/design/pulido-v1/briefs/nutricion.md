# Paquete NUTRICIÓN-CUERPO · prefijo `nc` · pestaña `nutricion` · `maps/nutricion.md`
Lee antes `BRIEF_COMUN.md`. Puntos del usuario: **20, 21**.

## Palabras del usuario (literal)
> 20. Para registrar comida, hay un boton de voz que no funciona, está ahí de relleno, haz que ese modo para dictar funcione realmente.
> 21. Para añadir una nueva medición, el sheet abre un slider de peso que se ve con esquinas negras cortadas, haz ese ajuste para que no se vea de esa forma y además, mejora el diseño de como se ve, y no solo eso, sino que el boton para acceder a este debe ser flotante, igual que el de registrar comida en la pantalla de nutrición.

## Dónde está hoy (léelo)
- Dictado: `screens/nutrition/design/logger/LoggerDictation.kt`, `data/voice/VoiceNutritionRecognizer.kt` y el campo «¿Qué comiste? Escríbelo como lo
  contarías» del registro (`screens/nutrition/design/logger/`). Captura `h19-voz-comida`: al pulsar el micrófono sale «No te entendí. Prueba otra vez o
  escríbelo.». Un agente de código arreglará el funcionamiento (permiso, reconocedor, resultados parciales, análisis); **tú diseñas todos los estados visibles**.
- Nueva medición: busca la pantalla de **Cuerpo** (`screens/body/` o equivalente) y la hoja «Nueva medición» con el slider de peso (regla), y el botón que la
  abre (hoy no es flotante); mira cómo es el botón flotante de «Registrar comida» en Nutrición (`NutritionScreen.kt`, `KpknBottomBar.kt`,
  `KpknIslandVisibility.kt`) para que el de Cuerpo sea su par exacto. Lee qué mediciones existen (peso, % grasa, cintura, perímetros, fotos…).

## Qué debe contener el prototipo
**Dictado** (en el registro de comida, modo «Describir»): reposo (micrófono integrado en el campo, no un círculo suelto), permiso (pedir/denegado/
ajustes), **escuchando** (onda en vivo + transcripción parcial que se escribe en el campo, botón de parar claro), procesando → alimentos reconocidos como
chips/filas con cantidades (sigue el diseño de registro de comida vigente: `docs/design/arreglos-v1` y `registros-v1`), «no te entendí» con una salida útil
(reintentar / escribir) y NO como error sordo, sin conexión/sin servicio de voz, ruido. Idioma español. Interactivo: pulsar el micro simula escuchar y
escribe una frase de ejemplo.
**Cuerpo — nueva medición**: hoja rediseñada (esquinas redondas completas, sin recortes negros: el prototipo debe mostrar bordes y fondos correctos),
tipo de medición, valor con una **regla/slider de peso bien diseñado** (inercia, marcas, valor grande con unidad, ajuste fino ±0,1, último valor y
diferencia), fecha/hora, nota, guardar; y el **botón flotante** (FAB) de «Nueva medición» en la pantalla de Cuerpo, igual en estilo y posición que el de
Nutrición (que sea EXACTAMENTE su par), con su comportamiento al hacer scroll (se compacta/oculta como el de Nutrición). Estados: primera medición (sin
historial), con historial, valor atípico (aviso), guardado con éxito, 360 × 740 y 130 %.
