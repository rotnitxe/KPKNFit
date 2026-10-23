# Editor y catálogo · Decisiones del usuario sobre los prototipos (2026-10-20)

Lienzo: https://claude.ai/artifact/1sjmv6uyMRxEYBHNqFqbyA (46 tableros). Lo no mencionado queda **aprobado**: A4, A5, B4, B6, B7, B9, C2, C3, C4–C7, D1, D2 (diseño), D5–D9, E3–E6, E8, E9, F1, F2, G1–G4.

## Cambios pedidos (ronda R1 de prototipos)

| Tablero | Decisión del usuario | Cómo se aplica |
|---|---|---|
| A1 · Editor | No hay un RIR global: cada ejercicio y cada serie llevan su propio RIR o RPE (las Reglas son otra cosa). | Fuera el chip «Fuerza · RIR 2» de la cabecera. |
| A2 · Sesión vacía | Se puede entrenar una cosa o ambas. El cardio no depende de la fuerza: una sesión puede ser solo de cardio, sin «al inicio» ni «al final». | Tres caminos: Fuerza, Cardio, Fuerza + cardio. La colocación «Al inicio / Al final» solo existe cuando hay fuerza y cardio. Tablero nuevo A2b: sesión solo de cardio en el editor. |
| A3 · Arrastrar | Estable y preciso; mover libremente entre grupos, sacar de un grupo, meter en un grupo y arrastrar a una superserie ya formada. | Mostrar los cuatro destinos con su señal de soltado. |
| A6 · Estados | Se ven dos avisos encima del otro: horrible. | Un solo aviso por estado, sin solapes. |
| B1 · Ejercicio abierto | Bilateral/Unilateral ocupa demasiado; por defecto siempre bilateral. | Pasa al menú ⋯ del ejercicio (B8). En unilateral, una etiqueta discreta en la cabecera del ejercicio. |
| B2 · Foco de serie | Lateralidad, modo y carga inteligente son del ejercicio, no de la serie; solo el descanso varía por serie. Drop-set y rest-pause solo aparecen si la serie está programada a FALLO, precedidos de «¿Vas más allá del fallo?», como mini tarjetas con su configuración, interfaz cuidada. | El foco de serie solo lleva lo de la serie (carga, reps, intensidad, descanso de esta serie, acciones). Lo del ejercicio queda fuera del foco. |
| B3 · Modo RM | En RM no hay RIR ni RPE: solo porcentajes de RM. Las repeticiones no son un rango: son una estimación a ese %RM. Un modo RM «como tiene que ser», no un «Reps 2». | Deslizador de %RM con tres cifras enlazadas y editables: repeticiones estimadas, % RM y peso (el peso también en el deslizador). Editar una recalcula las otras. Apartado claro para el 1RM o un PR (kg × reps) que sirve de referencia. |
| B5 · Unilateral | Nada de I/D. | L/R. |
| B8 · Opciones del ejercicio | Bien. | Recibe «Lateralidad». **Tablero nuevo B8b**: tarjeta de serie EN VIVO con el deslizador de ROM sin cambiar su tamaño ni altura (ley 1). |
| C1 · Superserie | Las superseries no van aparte de los grupos: si está dentro de un grupo, se muestra dentro de él. | C1 y Main muestran la superserie dentro de su grupo. |
| D4 · Plantillas | «Mis plantillas» no se despliega: es un botón. La búsqueda de plantillas va en primer plano. | Buscador arriba; «Mis plantillas» como botón que abre su lista. |
| E1 · Añadir | Sin volumen en porcentajes: aporte a series efectivas según el rol muscular, sin inventar valores. | «Primario · 1 serie», «Secundario · 0,5 series»… con los valores de la app. Igual en F1/F2 por coherencia. |
| E2 · Búsqueda | La altura (de la polea) también se configura ahí mismo, de forma escueta, sencilla y visual. | Selector visual compacto de altura (y agarre) en el resultado, sin pasar por la ficha. |
| E7 · Crear ejercicio | Rediseño + mejorar los motores que detectan ejercicios parecidos; auditoría para mejorar la función: añadir imágenes y generar descripciones automáticamente. | El tablero enseña la detección de parecidos, la imagen y la descripción generada. El motor va a auditoría (ver abajo). |

## Trabajo de motor (fase de implementación, con auditoría previa)

- **D2 · Impacto:** analizar el bug por el que **Columna sale siempre en 0** sea cual sea el ejercicio.
- **D3 · Sugerencias:** más robustas e inteligentes: entender el contexto de la sesión y de los ejercicios, y no ser alarmistas (hoy dan muchas sugerencias malas).
- **E7 · Crear ejercicio:** auditoría del motor de parecidos, imágenes y descripción automática.

## Puntos de decisión anteriores (los 10 que planteé)

Quedan aprobados por no mencionarse: hoja única de descanso (B7), creador único de superseries (C2), formulario de crear ejercicio (E7, se rediseña), «Ver en Todos» (E6), confirmar antes de aplicar (E9, B4, G4), diálogos como hojas (B9) y fusión de diálogos (A4), carga de solo lectura en la serie (B2), filas plegadas de Reglas y solo «Copiar» en Transferir (D5, D8), avisos del cardio continuo (C4) y descanso y flechas en G1.

## Ronda 2 de comentarios (2026-10-20, noche)

- **Tercio inferior de la sesión en vivo:** el usuario elige **V1** (línea de sesión, la recomendación). V2 queda descartado.
- **Aproximación y movilidad («H1 y H2»):** «No pueden existir botones afuera de la tarjeta, ni mucho menos que la tarjeta de series de aprox/mov sea de distinto tamaño que la de series efectivas: todas son del mismo tamaño y no pueden tener botones fuera de esa zona, porque en esos espacios van otros apartados».
  - En vivo (H5, H6): la tarjeta de aproximación y la de movilidad miden exactamente lo mismo que la tarjeta de serie efectiva (312 × 425 en el Z Flip) y todo control vive dentro; el espacio de arriba es del relator y el de abajo de la línea de sesión (V1).
  - En el editor (H1, H2): cada aproximación y cada movimiento se ven con el mismo tamaño de ficha que las series efectivas, y sin botones sueltos fuera del ejercicio.
- **AMRAP y AMRAP calibrador:** verificar la infraestructura real y cómo funciona; no puede ser una función vacía, sobre todo el calibrador y el AMRAP normal. → auditoría AUD3 y después implementación.

## Ronda 3 (2026-10-20, tarde): pasar a implementar

- El usuario aprueba **todas las recomendaciones** de las auditorías (AUD1, AUD2, AUD3) y los diseños de los prototipos, una vez aplicadas las correcciones pedidas (R5: aproximación y movilidad del mismo tamaño; R6: AMRAP y calibrador reales, deslizador aprobado en Solo RPE). Después, implementación.
- Decisiones AUD3 tomadas con la recomendación: sin el +2,5 % por etiqueta AMRAP (una sola capacidad en toda la app); topes del ajuste +5 % / −8 % (los de `computeSessionImprovementAdjustment`); calibrar solo con carga externa, reps completas y reserva 0–1 o fallo.
- **El calibrador debe funcionar al 100 %** y, además de estimar la fuerza en ese ejercicio, adaptar cargas e intensidades sugeridas en la sesión en vivo según el rendimiento de los músculos implicados y de patrones de movimiento parecidos o conectados. Propuesta conservadora (decisión de producto de KPKN, no estándar del mercado): mismo ejercicio 100 % del ajuste; misma familia y mismos músculos principales 50 %; las bajadas (rendimiento bajo) se extienden también a ejercicios que comparten músculo principal, las subidas no; siempre como propuesta que la persona aplica o conserva, nunca sobre cargas fijadas a mano.
- **Corrección del usuario (misma tarde):** como no hay evidencia de que el rendimiento en un ejercicio se traslade a otros (RES1 §10), **el AMRAP no cambia nada en los demás ejercicios**. Se descarta la propagación por músculos y patrones; la calibración solo afecta al mismo ejercicio en su contexto. Especificación: `CALIBRADOR_V1.md`.


## Decisión del usuario · 22-oct-2026 · movilidad de sesión

Al contrastar H2 `#grupo` (grupo generado de solo lectura) con el WIP editable de ED-C, se pidió resolver la diferencia. El usuario respondió: «haz lo que necesites hacer y que permita una implementación completa, esa es la prioridad».

Se conserva la edición completa de los grupos de movilidad de sesión: añadir y quitar movimientos, reordenar, cambiar dosis por tiempo o repeticiones y configurar el tiempo global. Es una excepción explícita a la variante de solo lectura del tablero H2. Se utilizan los campos serializados existentes, sin cambiar Room ni atribuir un origen que el modelo no registra. La persistencia se comprueba cerrando y reabriendo una base de prueba aislada. Los HTML congelados no se modifican.
