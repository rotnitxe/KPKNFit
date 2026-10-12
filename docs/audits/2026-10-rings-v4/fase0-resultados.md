# Resultados de la fase 0 · red de seguridad y medidas de partida

Todo lo de aquí se reproduce con las pruebas del repositorio (motor REAL, fechas absolutas, reloj explícito). Base: `master` @ `7815e45b6` + paquetes 0.1 a 0.3 (y 1.1) de `feat/rings-v4`.

## 0.1 · Línea base
57 clases / 215 pruebas de `domain/auge`, 0 fallos, 0 omitidas (`baseline.md`). Primer build de pruebas ≈ 9 min; después cada lote tarda ≈ 25 s.

## 0.2 · Máster dorado (`AugeGoldenMasterTest`)
35 casos (A01–A12 una sesión, B01–B07 tipos de atleta, C01–C09 historiales, D01–D04 anclas y evidencia inicial, E01–E03 vista previa) × 5–9 instantes → 272 líneas congeladas en `src/test/resources/auge/golden/expected.txt`.
Regenerar: `AUGE_GOLDEN_UPDATE=1` (escribe `build/auge-golden/expected.txt`, nunca en `src`). Base temporal 2025-03-03; la invariancia al desplazamiento de +364 días y la zona UTC se comprueban en la propia prueba.
Hallazgos que quedan congelados (no corregidos todavía):
- **C08 (recién llegado, 5 sesiones en 15 días): ACWR 1,600 a las 72 h** (F-20). Con el arreglo de 1.4 pasa a 0,857.
- **D04**: una sesión fechada 6 h DESPUÉS del instante evaluado ya cuenta en Energía (77 frente a 100), SNC y Columna (R-10); el músculo sí la filtra. Lo corrige 1.7.
- **D01/D02**: las anclas manuales con fecha futura (+2 h, +3 h) se aplican ya en t=0. La caducidad de 18 h NO está en el motor sino en `AugeRepository.getActiveWellbeingWithManualOverrides` (`AugeRepository.kt:55-66`): se trata en 2.5.
- **Meseta «99 %»**: en casi todos los casos Energía, Columna y músculos muestran 99 desde ≈ 72 h hasta 168 h (truncación; F-25). Lo corrige 1.5.
- Solo hay cuatro perfiles de capacidad entre los siete tipos de atleta (B02 = B03, B06 = B07, B01 ≈ B04).
- E01: el anillo muscular baja 7 puntos con un drenaje de sesión de 59 (promedio de pilares, por diseño).
- Auditoría de reloj: 30 usos en 11 archivos de `domain/auge` (el plan contaba ≈ 23 en 9); alcanzables hoy: `applyMuscularAcwr` (corregido en 1.4), la zona por defecto (`ZoneId.systemDefault()`), la capacidad de respaldo (`:387`), el motor articular (`AugeTtcEngine.kt:211`), nutrición e interferencia.

## 0.3 · Arnés de usuarios sintéticos y sesgo del aprendiz actual
Pruebas: `AugeSyntheticUserInvariantsTest` (9), `AugeLearnerBiasCharacterizationTest` (11). Modo ligero ≈ 45 s; `AUGE_SIM_FULL=1` ≈ 196 s. Interfaz `SimLearner` en `AugeLearnerSimulator.kt` para enchufar el aprendiz nuevo (paquete 3.2).

**Tabla A · inversión sin ruido** (τ_implícito / τ_verdadero; la «sensación» es exactamente la batería del motor con ese τ):

| Canal | 8 h | 12 h | 24 h | 48 h |
|---|---|---|---|---|
| Músculo (τ 24/36/48/72, 4 tamaños de sesión) | ×1,45–3,10 | ×1,23–2,78 | ×1,15–1,79 | ×1,09–2,19 |
| Energía (τ 36) | ×0,45–0,52 | ×0,55–0,66 | ×0,71–0,91 | ×0,95–1,38 |
| Columna (τ 52) | ×1,14–1,49 | ×1,28–1,70 | ×1,05–2,20 | ×1,20–3,46 |

**Tabla B · bucle cerrado** (60 usuarios, 4 sesiones/semana, ruido σ = 4 salvo el último escenario, 30 gestos; error absoluto medio / sesgo del τ aprendido, en horas):

| Escenario | Pectorales (τ 48) | Energía (τ 36) | Columna (τ 52) |
|---|---|---|---|
| τ verdadero = defecto | 8,1 / +7,9 | 1,4 / −0,3 | 36,4 / +36,4 |
| τ verdadero × 1,25 | 4,6 / −2,6 | 6,4 / −6,4 | 27,7 / +27,7 |
| τ verdadero × 0,75 | 18,4 / +18,4 | 7,0 / +7,0 | 51,8 / +51,8 |
| usuario ruidoso σ = 7 | 8,5 / +8,2 | 2,6 / +2,0 | 39,6 / +39,6 |

Corrida larga (20 usuarios, 100 gestos, τ verdadero = defecto): Pectorales +8,0 h a las 8 observaciones propias, +11,7 h a las 30; **Columna +24,3 h a las 8, +35,1 h a las 30 y +54,6 h a los 100 gestos** (el τ aprendido se dispara hacia el tope); Energía ≈ 0 en neto (los sesgos −/+ por tramo horario se compensan: ×0,72 a 8–30 h, ×1,24 a 30–60 h, ×1,90 después).
Entre el 51 % y el 71 % de las actualizaciones alejan τ del valor verdadero (el plan estimaba ≈ 57 %). La «guarda de planta» (sentido = predicho ⇒ no se aprende) descarta ≈ 9,7 % de las observaciones con σ = 4.
F-11 medido: la tasa de aprendizaje depende del contador GLOBAL (Δτ de la 1.ª observación de un músculo: 9,0 h con contador 0 frente a 0,9 h con contador 40) y no hay zona muerta (1 punto de diferencia mueve τ de 72 a 81).

**Invariantes del motor** (modo ligero): rango y suelos 0 violaciones en 20 679; recuperación monótona en el tiempo 0/9 216; ACWR nulo con < 14 días 0/339; ruta rápida = Home 0/105.
**Monotonía del drenaje: 2 violaciones en 4 046 (BUG F-60)**: en un tirón, subir la carga ×0,5 → ×0,75 BAJA el drenaje muscular (66 → 65) porque la receta mezcla los tres canales (`accumulatedDrain` es el promedio de los tres). Lo arregla la receta unificada de 2.2.
**Paridad vista previa ↔ Home (F-23)**: hoy hasta 7 puntos en el anillo muscular tras una sesión pesada (≤ 4 en general, ≤ 3 por músculo); el presupuesto fijado es 8 y el paquete 2.3 lo baja a ≤ 1.

## Implicaciones para el diseño
- El aprendiz nuevo (3.2) debe batir estas cifras con el mismo simulador: Columna es el caso más grave (se desvía sin parar) y Energía el más engañoso (sin deriva neta pero con error de 6–7 h cuando τ ≠ defecto).
- El sesgo de la inversión depende de la hora (8–12 h, 24 h, 48 h): `h(θ)` debe evaluarse con el motor completo en el instante de la observación, no con una fracción restante.
- La unificación de la receta (2.2) no puede conservar el promedio de canales en `accumulatedDrain`: el diminishing será por canal.
