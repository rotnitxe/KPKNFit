# P2-Nb · Fuentes y límites de la referencia de nutrientes

Preparación revisada el 24-oct-2026. La implementación de P2-Nb sigue pendiente; este documento no certifica pruebas de código ni aceptación de dispositivo.

## Fuente operativa

`data/DRI_ADULTOS.csv`, aprobado en la ola, contiene 240 filas: 20 nutrientes y 12 combinaciones de sexo, edad y etapa de vida. Su LEEME delimita qué valores se contrastaron y cuáles no. La futura implementación debe conservar esa tabla y comprobar sus 240 filas, no reconstruir valores de memoria. Las referencias son de las Academias Nacionales de EE. UU./Canadá; no se mezclan con normas de otras jurisdicciones.

## Contrastes primarios de esta reanudación

- [Academias Nacionales: sodio y potasio, actualización de 2019](https://www.nationalacademies.org/news/sodium-and-potassium-dietary-reference-intake-values-updated-in-new-report): sodio AI 1.500 mg en adultos y CDRR 2.300 mg. El CDRR se presenta como techo de referencia, no como una cantidad que haya que alcanzar ni como UL.
- [NIH ODS: magnesio](https://ods.od.nih.gov/factsheets/Magnesium-HealthProfessional/): el UL adulto de 350 mg se refiere a suplementos y medicamentos. No sirve para alertar usando el total de comida más suplementos.
- [NIH ODS: folato](https://ods.od.nih.gov/factsheets/Folate-HealthProfessional/?os=http): el UL corresponde a formas sintéticas de suplementos/alimentos fortificados; no equivale al total de folato en DFE. La URL sin parámetro devolvió 403; se pudo leer la copia oficial indicada.
- [NIH ODS: niacina](https://ods.od.nih.gov/factsheets/Niacin-HealthProfessional/) y [vitamina E](https://ods.od.nih.gov/factsheets/VitaminE-HealthProfessional/): conservar la distinción de formas añadidas/suplementarias. Sus UL no deben producir una alarma sobre la ingesta total indistinta de la app.

No se afirma una revisión clínica por web de las 240 filas. El contraste automático deberá demostrar fidelidad al CSV; esa comprobación es distinta de validar sus fuentes primarias completas.

## Reglas de implementación fijadas por D15/D18

Solo permiten una alerta del total conocido los UL que aplican a ingesta total: calcio, hierro, zinc, selenio, yodo y vitaminas C, D y B6. Magnesio, folato, niacina, vitamina E y vitamina A (UL de retinol) quedan como referencia sin alarma sobre el total indistinto. Sin UL no se inventa un límite.

Cero declarado y ausencia de dato son estados distintos. Una entrada rápida no declara micronutrientes. No modificar el importador, reimportar el catálogo, cambiar Room ni inventar metas para perfiles ausentes o menores de 19 años. La hoja debe identificar las cantidades como conocidas y potencialmente incompletas, con la referencia general y el aviso del brief.
