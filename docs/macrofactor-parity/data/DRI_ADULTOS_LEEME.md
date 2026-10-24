# DRI_ADULTOS.csv: referencias dietéticas para adultos (insumo de P2-Nb)

**Qué es.** 240 filas = 20 nutrientes × 12 grupos (hombres y mujeres de 19–30, 31–50, 51–70 y > 70; mujeres 19–30 y 31–50 en embarazo y en lactancia). Columnas: `nutriente, unidad, sexo, grupo_edad, tipo(RDA|AI), valor, ul, nota, fuente_url`. Fuente: Ingestas Dietéticas de Referencia de EE. UU. y Canadá (Academias Nacionales, antes IOM) y hojas del NIH Office of Dietary Supplements. Datos públicos; ninguna tabla procede de otra app.

**Cómo se obtuvo.** Investigación de solo lectura de Gemini 3.8 Flash el 21-oct-2026 (`C:\kw\logs\DRI_TABLA.md`) y revisión del orquestador.

**Qué se verificó y qué no (honesto).**
- Estructura (script): 240 filas, 20 nutrientes con 12 filas cada uno, sin duplicados, `valor` numérico, `tipo` ∈ {RDA, AI}, `ul` = `ND` o «número unidad», unidad única por nutriente.
- Valores: el orquestador comparó las 240 filas con las tablas DRI que conoce (los 20 nutrientes coinciden) y confirmó por búsqueda web en fuentes oficiales: sodio (AI 1.500 mg para adultos y CDRR 2.300 mg), potasio (AI 3.400 mg hombres, 2.600 mg mujeres, 2.900 mg embarazo, 2.800 mg lactancia), magnesio (RDA 400–420 hombres, 310–320 mujeres, embarazo 350/360, lactancia 310/320; UL 350 mg solo de suplementos y medicamentos).
- **No verificado:** las URL de `fuente_url` no se abrieron (las páginas del NIH devolvieron 403 a la herramienta de lectura). Los valores de los demás nutrientes se verificaron por conocimiento, no por página. Si algún día importa, comprobar contra la tabla oficial de la ficha de cada nutriente.

**Alcance de cada UL (clave para no dar falsas alarmas).** La app no separa de qué fuente viene un nutriente (comida frente a suplemento), así que el UL solo se puede vigilar cuando aplica a la ingesta total:
| Alcance | Nutrientes |
|---|---|
| Ingesta total (alimentos + suplementos): una alarma es válida si el total CONOCIDO ya lo supera, porque el total real nunca es menor | calcio, hierro, zinc, selenio, yodo, vitamina C, vitamina D, vitamina B6 |
| Solo suplementos / formas añadidas: se muestra como referencia, SIN alarma de exceso | magnesio (suplementos y medicamentos), folato (ácido fólico sintético; el UL está en µg de ácido fólico, no DFE), niacina (formas sintéticas), vitamina E (α-tocoferol suplementario) |
| Solo vitamina A preformada (retinol): referencia, SIN alarma, porque el total en RAE incluye carotenoides | vitamina A |
| Sin UL (`ND`) | fibra, potasio, vitamina K, vitamina B12, tiamina, riboflavina |
| Sodio | sin UL; el CDRR de 2.300 mg/día (columna `nota`) es el «máximo a no superar» de la interfaz; la AI de 1.500 mg es la ingesta adecuada |

**Fibra.** La AI en g/día se calculó con la regla de 14 g por 1.000 kcal; la app usa la cifra de la tabla por sexo y edad (no la reescala por calorías).

**Fuera de la tabla.** Menores de 19 años y perfil sin sexo o sin edad: no hay metas (no se inventan). No se añaden campos de embarazo o lactancia al perfil en esta ola: esas filas quedan en los datos y en el test, pero la interfaz no las ofrece.

**Aviso para la interfaz.** Pie de la hoja: «Referencia general de las Academias Nacionales de EE. UU.; no sustituye el consejo de un profesional.»
