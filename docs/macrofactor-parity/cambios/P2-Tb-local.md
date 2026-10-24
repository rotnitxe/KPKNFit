# P2-Tb · calentamiento, descarga y controles

Banco 20–250 kg, tres niveles, compuesto pesado/no pesado, inventarios comercial/casa finito/mancuernas/máquina de 5 kg. Primera ejecución: 17 pruebas, un fallo real en el banco: aproximación 18 kg → redondeo 20 kg para trabajo 20 kg. Corrección limitada a reachableWarmupLoad de máquina: el paso inferior real, o pendiente si ni el mínimo cabe. Sin rediseñar rampas ni inventar material. Repetición con las regresiones existentes en marcha.

Topes vigentes: rampa larga hasta cuatro escalones y 195 s de descansos programados; corta hasta dos y 105 s. Las repeticiones y la movilidad/general son adicionales: esto no acredita una duración máxima real de toda la preparación.

Descarga nativa deliberada de volumen: mitad de series (ceil), RIR objetivo 4 (5 en perfiles de velocidad), carga base capturada y rango de reps conservados. Comentario y prueba del runtime/Room fijan el contrato; el RIR registrado sigue siendo independiente, sin prometer que una misma carga/reps produzca un esfuerzo concreto.

Ajustes: descanso 30–300 por pasos 15, auto-inicio y criterio AUGE con descripción de alcance real. Como hoy registrar arrancaba el descanso pese al campo legacy false sin lector, autoStartRestAfterRecording=true preserva el comportamiento por defecto; el antiguo restTimerAutoStart permanece sin reinterpretar backups. El lector nuevo sólo condiciona el arranque ordinario, no las pausas de técnicas. Round-trip JSON y Room y pruebas del recorder añadidos. AUGE por defecto sigue false; habilitarlo no promete suprimir el resto de criterios de descarga.

Harness TrainingSettingsHarnessActivity con defaults/custom y cuatro renders de 360 dp al 100/130. Resultados nativos pendientes de la tanda agrupada.

Validación pura final: `C:/kw/logs/mfp-training-tail-jvm-4.log`, BUILD SUCCESSFUL, 90 pruebas en 10 clases, cero fallos/errores/omitidos. Incluye las regresiones existentes de aproximaciones y viabilidad. La tanda anterior amplió fixtures y detectó que el primer ajuste anulaba el mínimo de máquina; se afinó para preservar 40 solicitado→50 real (trabajo 100), mientras 18 solicitado→15 real (trabajo 20) y mínimos que igualan/superan trabajo quedan pendientes. No se cambiaron expectativas ajenas. Android agrupado pendiente en la cola, `C:/kw/logs/mfp-training-tail-android-1.log`.

## Cierre focal Android · 24-oct-2026

`C:/kw/logs/mfp-training-tail-android-2.log`: BUILD SUCCESSFUL, 129 pruebas en 14 clases, cero fallos/errores/omisiones. 40 PNG finales en `C:/kw/shots/mfp/training-tail-local-20261024-2`. Incluye carga montable/no exacta y altura real de tarjeta, variante registrada Myo/normal, controles/JSON/Room, confirmación real de reemplazo y toque dentro del punto PR. Revisión visual de discos no montables al 130 % y reverso Myo al 100 %: texto sin recortes, cifra prescrita 59 intacta y distribución calculada 60 montable identificada. T5b fuerza 130/asistido 100 y ajustes custom 130 revisados. Pendientes suite integrada y QA de runtime/dispositivo.

La primera tanda compiló todo y ejecutó 125 casos; fallaron dos de T6. Se mantuvo cuerpo como opción válida junto a mancuernas (el criterio excluye barra/máquina) y se corrigió una superposición real: la isla Añadir ya no se presenta en modo Reemplazar. No se tocaron expectativas de tests ajenos. Revisión adicional: la variante se toma de las mini-series ejecutadas, de modo que cambiar Myo a rest-pause normal no deja una etiqueta falsa; AUGE/volumen y técnica base se conservan. El arreglo de calentamiento final coincide con la integración de 3.067 JVM verdes y conserva los redondeos no problemáticos de polea/Smith.
