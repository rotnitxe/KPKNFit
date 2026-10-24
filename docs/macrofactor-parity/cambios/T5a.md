# T5a · Cambios visibles

| Pantalla | Qué cambia | Antes → después | Por qué |
|---|---|---|---|
| Perfil | Bloque nuevo «Progreso» con una fila «Volumen por músculo» (al final de la pantalla, tras el progreso nutricional) | No existía una vista de lo hecho por músculo fuera de un programa → una fila que abre el panel de volumen | Dar entrada al panel desde el perfil; la fila de T5b se añadirá en el mismo bloque |
| Detalle del programa · pestaña Volumen | Enlace discreto «Ver lo que has hecho en 4 semanas» bajo el módulo de volumen | Solo plan frente a real del alcance elegido → además un enlace al panel con los registros de ese programa | El plan sigue donde estaba; lo REGISTRADO por ventana común se ve en el panel. Las cifras de la pestaña no cambian |
| Panel «Volumen por músculo» (pantalla nueva) | Vista Lista: una fila por grupo con series registradas, barra relativa al máximo de la ventana (con las zonas del programa si está calibrado y se ve el promedio) y desglose por ejercicio (series, sesiones, última fecha) | — → ventanas de 1, 4 y 12 semanas, promedio por semana o total, roles Directo / +Secundario / +Estabilizador y ajuste por intensidad | La suma del desglose es la cifra de la fila; misma ventana y misma contabilidad que el volumen del programa |
| Panel «Volumen por músculo» (pantalla nueva) | Vista Cuerpo: frente y espalda coloreados por series registradas con leyenda en su unidad; conmutador Volumen / Recuperación AUGE | La silueta solo existía como ajuste de Músculos (recuperación, dos tonos) → un mapa de solo lectura de los mismos números de la lista | Segunda vista de los mismos datos. «Recuperación AUGE» conserva sus dos tonos (azul desde 80 %, naranja si no) y no se mezcla con la escala de volumen |
| Panel «Volumen por músculo» | Estados honestos: «Sin registros en esta ventana» (sin cifras ni ceros), cero real en el músculo sin series de una ventana con registros, y una línea con los registros sin fecha asignable | — → «sin datos» distinto de «0» en lista y cuerpo (trazo discontinuo frente a trazo liso) | Nada se colorea «perfecto» por ausencia de datos |

Notas
- El ajuste de Músculos de Inicio (`MuscleBodyAdjust`) no cambia: la nueva vista usa su misma geometría, de solo lectura.
- Las zonas del panel salen solo de las recomendaciones del propio programa; a diferencia de la pestaña Volumen no se inventan zonas para Cuello o Romboides.


## Reanudación local · 24-oct-2026

Base `mfp/t5a@7b373aa22`. Regresión amplia del dominio de entrenamiento por el arnés JVM: **1.527 pruebas en 140 clases**, 0 fallos/errores/omisiones; log `C:/kw/logs/mfp-T5a-local-20261024-jvm-1.log`. El arnés no acredita Room ni Compose.

Primera tanda Android: **397 pruebas en 34 clases**, 0 fallos/errores/omisiones; log `C:/kw/logs/mfp-T5a-local-20261024-android-1.log`. Incluye detalle de programa, pantallas/modelos de volumen, runtime de progresión/JSON y render. No hay clases de pruebas bajo `screens/profile` en esta rama; se compiló su entrada de navegación, sin afirmar QA específica de Perfil.

Se generaron 20 PNG al 100/130 % en `C:/kw/shots/mfp/T5a-local-20261024`. Revisión visual parcial de `body`, `empty` y `unassigned` al 130 %. Se detectó truncamiento de «Promedio por semana»; el rótulo pasa a «Media semanal», manteniendo la descripción accesible «Promedio de series por semana». No cambia ningún cálculo ni API compartida. La nueva regresión inspecciona el texto maquetado al 130 %, sin elipsis.

Segunda tanda Android del ajuste visual: **398 pruebas / 34 clases**, 0 fallos/errores/omisiones, incluida la nueva prueba de texto. Log `C:/kw/logs/mfp-T5a-local-20261024-android-2.log`; 20 PNG finales en `C:/kw/shots/mfp/T5a-local-20261024-final`. Revisado `volumen_body_130.png`: «Media semanal» se ve entero. Se usaron dos tandas Android de las tres permitidas en esta reanudación; no hace falta repetir.

No hay APK nuevo instalado ni aceptación de dispositivo. Room intacto y sin constantes prohibidas. No push.
