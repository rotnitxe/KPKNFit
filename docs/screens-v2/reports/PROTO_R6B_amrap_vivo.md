INFORME R6-B · AMRAP y calibrador
Fecha: 20 de octubre de 2026.

V7-VivoAMRAP.dc.html | Rehecho el registro y el resultado con propósito AMRAP/Calibrador, reserva RIR/Fallo, decisión Aplicar/Conservar y ruta con cargas calibradas; todo el flujo de la serie vive en la tarjeta. Demostración principal: 69 kg × 10 reps → capacidad 92 kg, referencia 88 kg, +4,5 %, S2–S4 de 60 a 62,5 kg. Fórmulas: domain/calculations/Calculations.kt:60,74,90 y screens/workout/WorkoutLoadSuggestionController.kt:706–711. Tamaño final: 29.969 bytes, inferior a 30.000.

FUENTES Y COMPORTAMIENTO
Raíz de las referencias de código: C:\kw\u11\android-native\app\src\main\java\com\example\kpkn\.
Auditoría leída: C:\kw\u11\docs\screens-v2\audits\AUD3_amrap.md, completa; especialmente §1, §5, §8 y §9.

- Capacidad: híbrido de Calculations.kt:60–70, sin el +2,5 % por etiqueta AMRAP. Para 10 reps: C = peso × 36 / (37 − 10), redondeado a una décima. No se suman las reps en reserva a las reps realizadas.
- Ajuste: r = C/B; r ≥ 1,025 → min(r, 1,05); r ≤ 0,97 → max(r, 0,92); el resto → 1. Origen: WorkoutLoadSuggestionController.kt:681,706–711. Se aplica a la base del plan, evitando acumular factores sobre una carga ya calibrada.
- Sin referencia: las series de trabajo del ejemplo son 12 reps a RIR 2. Se usan 14 reps efectivas para la inversa de Calculations.kt:74–105. Con C = 92: 92 / (1 + 14/30) = 62,7 kg antes del ajuste al equipo; propuesta final 62,5 kg.
- Equipo del ejemplo: barra con incremento de 2,5 kg, como pide R6; redondeo al paso más cercano. Hay una implementación de ese redondeo en domain/training/generator/Prescriptions.kt:115.
- TM del AMRAP normal: receta compatible RepTargetDrivenTm, con 0,5 % por rep extra; resta 1 % por rep faltante desde un déficit de 2. Origen: data/protocols/TrainingPlanRecipe.kt:371–375 y domain/training/ProgramAutoregulationEngine.kt:1085–1106. El redondeo de TM al medio kilo procede de ProgramAutoregulationEngine.kt:1194–1202.
- Esfuerzo: pista de 18 px, área táctil de 44 px, degradado, halo y chispas crecientes. RIR 2 / RIR 1 / RIR 0 / Fallo producen 2 / 6 / 11 / 16 partículas con periodos 2,4 / 1,6 / 1,2 / 0,8 s. Origen: screens/workout/EffortScale.kt:82,171–185 y screens/workout/components/EffortFireSlider.kt:114. Se respeta prefers-reduced-motion.

ESTADOS ALCANZADOS CON LOS CONTROLES DEL TABLERO
1. Sube: carga 69, reps 10, RIR 1. C = 92, B = 88, r = 1,04545; +4,5 %. S2–S4: 60 → 62,5 kg. Aplicar actualiza las cargas, añade «calibrada» y actualiza la ruta.
2. Mantiene: cambiar la carga a 66, reps 10, RIR 1. C = 88, r = 1; aparece «Tu capacidad coincide con tu referencia: mantener», con 60 → 60 kg.
3. Baja: cambiar la carga a 60, reps 10, RIR 1. C = 80, r = 0,90909; caída medida del 9,1 %, factor limitado a 0,92. 60 × 0,92 = 55,2; redondeo al equipo → 55 kg.
4. Primera referencia: Configurar AMRAP → Referencia previa → Sin referencia previa; 69 × 10, RIR 1. Aparece «Primera referencia guardada», capacidad 92 kg y propuesta 62,5 kg, sin porcentaje de mejora.
5. No aplicada: sin seleccionar reserva, con RIR 3, con Peso corporal o con Asistida. Se registra la serie y aparece el motivo; no se aplican cargas. También se probó la exclusión por parciales y la admisión de Fallo como esfuerzo válido.
6. Próximas sesiones: elegir ese alcance, registrar y pulsar Guardar propuesta. Aparece «Propuesta guardada para la próxima sesión de este ejercicio». Hoy S2–S4 permanecen a 60 kg; la propuesta conserva 62,5 kg para la próxima sesión.

AMRAP NORMAL Y DECISIONES COMPROBADAS
- Propósito AMRAP, mínimo 6: 10 reps muestra mínimo superado; 5 reps se conserva como 5, con mínimo no alcanzado y déficit 1. No hay elevación silenciosa al mínimo.
- Sin regla: «Registrado en tu historial y récords» y «Sin regla de progresión: queda registrado».
- Regla por reps, TM 100, 11 reps y mínimo 6: 5 reps extra × 0,5 % = +2,5 % → TM 102,5 kg. Proponer deja pendiente; Aceptar aplica y Ahora no conserva TM 100. OFF no propone; Auto aplica la decisión de la regla en la simulación.
- Con 4 reps, faltan 2: la misma regla propone TM 98 kg.
- Series protegidas: S2 ya hecha y S3 fijada a mano permanecen a 60; solo S4 cambia a 62,5.
- Repetir el registro no acumula el factor. Editar la calibradora invalida el ajuste anterior en las series pendientes; volver a aplicar 70 × 10 deja 62,5 kg, sin multiplicar otra vez. Deshacer y Conservar también se probaron.
- Reps negativas se rechazan sin crear un registro.

MEDIDAS Y VERIFICACIÓN
Playwright con Chromium y el mini runtime de test_b, adaptado solo en memoria para eventos. No se modificó support.js ni se añadieron scripts de prueba al proyecto.
- Lienzo 360 × 880: tarjeta x=24, y=300, 312 × 425; línea inferior y=737, alto 112, final y=849.
- Lienzo 360 × 740: tarjeta x=24, y=184, 312 × 425; línea inferior y=619, alto 112, final y=731.
- Medidas verificadas antes del registro, en el resultado, tras Conservar y en primera referencia. S4 permanece visible en el panel de primera referencia en ambas alturas.
- Sin botones ni avisos en los espacios reservados entre relator y tarjeta o entre tarjeta y línea. Los controles de la serie se paginan y, cuando hace falta, se desplazan dentro de la tarjeta.
- Objetivos táctiles comprobados: ninguno menor de 44 × 44 px. Movimiento reducido: partículas ocultas y animaciones desactivadas.
- Cero errores de página en los recorridos finales. Un solo x-dc y un solo bloque DCLogic; enlaces locales comprobados; sin expresiones inválidas en las búsquedas del template.
- Las fichas B3b–B3f corresponden a la Parte A; no se modificaron ni se certifican sus medidas desde esta entrega.

QUÉ ES PROPUESTA Y QUÉ SIGUE SIN IMPLEMENTARSE EN LA APP
La calibración diferenciada, sus alcances, la primera referencia, Aplicar/Conservar/Deshacer y el registro calibrador sin reserva son la propuesta autorizada de AUD3 §8 y R6. La app auditada todavía no tiene ese contrato operativo; su recorder actual exige esfuerzo. Aquí se implementan como comportamiento del prototipo.
La regla de TM y sus parámetros proceden del código. Mostrar y resolver su propuesta en la tarjeta es una presentación nueva: el circuito nativo descrito por AUD3 opera al cerrar la semana del programa.
Historial, récords, referencia aceptada y propuestas futuras se simulan en el estado del tablero. No se escriben en Room, en el programa real ni en el perfil. Reiniciar ejemplo o recargar restaura la demostración.
Esta primera política calibra solo LOAD. Corporal, asistencia y lastre pueden registrarse, pero no se fabrica para ellos un 1RM en kg; la normalización corporal/lastre no se implementa en este prototipo.
El tope −8 % corresponde al factor anterior al redondeo: en el ejemplo, 55,2 se convierte en 55 kg por el paso de 2,5. No se añadió una segunda fórmula para limitar otra vez el peso redondeado.
«Reiniciar ejemplo» es un control de demostración. No se introdujeron fórmulas de capacidad, bonos AMRAP ni reglas de progresión ajenas a las fuentes indicadas.

Archivos escritos: project\V7-VivoAMRAP.dc.html e INFORME_R6_B.txt. Repositorio consultado solo en lectura; sin Gradle, adb ni teléfono. canvas.json y la Parte A permanecen sin modificaciones de este trabajo.
