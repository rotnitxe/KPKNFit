# Verificación · Iteración 02
26 de octubre de 2026. Evidencia del prototipo web, no de Android.

## Revisión ejecutada

- **372 combinaciones de tarjetas:** 62 escenarios × 3 pantallas × 2 escalas de texto.
- Marco idéntico por viewport: 312 × 394; 345 × 470; aproximadamente 363 × 488.
- Comprobación de altura/ancho, contenido contra su contenedor, pie, descendientes con scroll y separación respecto al roadmap. **Cero incidencias en la pasada final.**
- **124 combinaciones de Lista:** los 62 escenarios a 360 × 740 / 130 % y 393 × 852 / 100 %. Sin overflow horizontal ni roadmap en la variante por serie.
- **42 combinaciones de superficies completas:** descanso, feedback, captura/aviso y las tres pestañas del análisis, a los tres tamaños y dos escalas. Sin desbordamientos en la pasada final.
- Sin errores ni advertencias de consola en la revisión final.
- Comprobación de sintaxis JavaScript y reconstrucción del HTML autocontenido.

## Interacciones comprobadas

- Descanso pausado conserva 1:32 al pasar tarjeta → completo → tarjeta. No aparece una isla de descanso.
- Feedback técnico cambiado a 9 conserva su valor al expandir y vuelve al reloj al guardarlo.
- Edición de una captura de voz cancelada conserva las 6 reps originales; falta de RIR impide confirmar; confirmar 8 reps / RIR 2 actualiza la misma entrada y conserva la frase original y los valores anteriores.
- Con Modo Voz apagado, los comandos están deshabilitados y el interruptor de guía hablada puede activarse independientemente.
- Encuadre incompleto impide comenzar. Tras corregirlo, la prueba «Listo» habilita Modo Voz; el comando de inicio muestra GRABANDO y detener abre el análisis.
- Cambiar la repetición 9 a completa actualiza el conteo a 9 + 1. Usarlo modifica el borrador y mantiene el contador de series registradas en 2 de 9.
- Superserie A1 de ronda 2 avanza a A2 de la misma ronda, con 20 segundos de descanso en el ejemplo.
- Cambio de etiqueta presenta una revisión previa y renueva la referencia pendiente a 80 × 8; objetivos de descanso editados a 150 segundos persisten al reabrir la configuración.
- Reemplazo conserva lo realizado y abre el nuevo ejercicio con carga vacía y sin historial heredado.
- Los juegos dibujan y evolucionan con puntuación, rachas, vidas, controles y fin de partida. Comparten la visualización del descanso y no escriben en su temporizador.
- Enlaces directos a los escenarios nuevos comprobados. La galería enlaza con sus demostraciones.

Se corrigieron durante la revisión el solapamiento tarjeta/roadmap en pantalla compacta con texto aumentado, el espacio del feedback completo, la cancelación de borradores de voz, el acceso directo a nuevos escenarios y la actualización del reproductor. Las mediciones finales se repitieron después de esos ajustes.

## Evidencia

- **qa-v2.json:** mediciones y resultados de interacción.
- **capturas-v2/** y **GALERIA.html:** capturas del navegador.
- **prototipos.html:** artefacto autocontenido generado desde las fuentes.
- **fuentes-iteracion-01.zip:** fuentes anteriores preservadas.

## Límites

Los comandos con comillas son botones de simulación, no reconocimiento real. Se verificó el control independiente de guía y la invocación de síntesis web; no se verificó auditivamente la salida del dispositivo. Reproductor, encuadre y análisis usan figura y datos sintéticos.

No se ejecutó Gradle, no se instaló ninguna app ni se modificó código Android. No se validaron cámara, Bluetooth, audio simultáneo, MediaPipe, calidad física de las métricas, persistencia, TalkBack ni muerte del proceso. El zoom CSS y el texto web al 130 % no equivalen a la implementación de fontScale/insets de Android.
