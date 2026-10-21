# N1 · Resultados del estimador de gasto

## Protocolo fijado antes del ajuste

Banco determinista de 112 días, 32 semillas (1101–1132), evaluación estable en los días 57–112. S1 cruza agua con desviación típica de 0,3 / 0,6 / 1,0 kg y pesaje diario, tres veces por semana e irregular. Las métricas se agregan por escenario y patrón; no se exige precisión individual a cada trayectoria ruidosa.

Umbrales invariables de D7: sesgo absoluto ≤50 kcal/día, MAE ≤150 kcal/día con pesaje diario, cero inversiones de una propuesta semanal por un pesaje aislado de ±0,5 kg, abstención correcta ≥95 % cuando faltan datos y sesgo absoluto de S3 <10 kcal/día.

S2 introduce un escalón de −300 o +250 kcal/día en el día 50. La respuesta media de las 32 semillas debe entrar en el último 10 % del escalón durante siete días consecutivos antes de 42 días, sin sobrepasar el nuevo gasto en más de 100 kcal/día. Se comprueba también sin ruido. Los 42 días presupuestan 21 de ventana, 14 de adaptación de pendiente y siete de estabilización; 100 kcal es menos de la mitad del menor escalón. Estos límites se fijan antes de ejecutar o ajustar el modelo.

La banda debe cubrir el gasto verdadero en al menos el 80 % de los días estables de S1. S6 limita a 100 kcal/día la desviación de la respuesta media durante los 14 días posteriores al cambio de objetivo. S7 exige igualdad exacta de las estimaciones anteriores a una corrección.

Máximo de tres rondas de parámetros. Si persiste un incumplimiento, se conserva el umbral y se documenta como falla conocida en su test específico.

## Parámetros, rondas y resultados

Pendientes de la implementación y las corridas del banco.

## Alcance

El banco verifica contratos y comportamiento bajo un generador conocido. No demuestra precisión fisiológica individual ni permite diagnosticar metabolismo, composición corporal o calidad real de los registros. La EER y el suelo los aporta quien llama. Este paquete no cambia consumidores ni registros existentes.
