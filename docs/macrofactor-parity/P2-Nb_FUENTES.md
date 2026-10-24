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

## Implementación y fidelidad · 24-oct-2026

La tabla Kotlin se deriva mecánicamente del CSV aprobado, conserva sus 240 combinaciones, y las pruebas leen la copia exacta (SHA-256) y comparan valor, tipo, UL, unidad y URL en ambos bordes de cada grupo de edad. No se ha ampliado la revisión clínica de las fuentes.

Se mapearon las claves reales de `FoodDatabase.kt`, `GlobalFoodEntity.toFoodItem` en `data/db/Entities.kt` y `FoodImporter.usdaEntityOrNull`: el catálogo global solo conserva fibra/sodio/potasio/agua; el estático tiene además algunas vitaminas/minerales. El resto se rotula sin dato en catálogo cuando no hay una ficha propia que lo declare. No se toca el importador ni se reimporta.

Las unidades deben ser compatibles: no se convierten IU a RAE, folato genérico a DFE, ni formas no identificadas de vitamina E o niacina a α-tocoferol/NE. Un dato sin forma comparable se presenta sin comparar, nunca como cero. Una receta guardada no conserva cobertura por ingrediente y nutriente, por lo que sus aportes se marcan conservadoramente estimados. Las entradas rápidas no aportan micronutrientes declarados; una procedencia de baja calidad tampoco pasa a cobertura declarada. La cobertura se pondera por kcal; sin kcal se indica explícitamente que usa recuento de alimentos.

La referencia de perfil usa únicamente edad adulta guardada y la ecuación femenina/masculina registrada en el plan, rotulada como tal; no se infiere sexo de identidad de género ni perfil hormonal. Sin esa información o con ecuación promedio no hay referencia. No se añaden campos de embarazo/lactancia; esas filas se prueban como datos, y un perfil que declara ese estado no recibe la referencia adulta ordinaria de esta hoja.

Los campos heredados `MacroGoals.fiberGoal`, `sugarLimit`, `sodiumLimitMg`, `potassiumGoalMg`, `hydrationGoalMl` conservan su contrato. La hoja nueva usa referencias DRI identificadas, no convierte defaults heredados de Ajustes en decisiones expresas del usuario ni inventa una recomendación para azúcar/agua fuera del CSV. La documentación de código huérfano de P2-D registra sus consumidores efectivos sin borrarlos.

## Validación local cerrada · 24-oct-2026

JVM amplio: 1.317 pruebas en 108 clases, sin fallos; comprobación focal posterior de cobertura/DRI: 12 pruebas verdes. Android final: 330 pruebas en 47 clases, cero fallos, errores u omitidos, diez PNG a 360 dp al 100/130 %. Registro `C:/kw/logs/mfp-P2Nb-local-20261024-android-2.log`; renders `C:/kw/shots/mfp/P2Nb-local-20261024-final`. La primera ronda detectó inicio prematuro del flujo derivado y una cantidad cero en la fixture: corregidos con inicio Lazily y cantidad válida, sin cambiar expectativas ajenas. Pendientes integración completa y QA física de la ola. Room y catálogo intactos.
