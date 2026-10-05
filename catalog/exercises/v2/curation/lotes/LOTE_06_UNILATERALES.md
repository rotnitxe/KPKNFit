# Lote 6: unilaterales — aprobado, aplicado y probado en unidad; UI pendiente de ADB

Fecha de actualización: 2026-10-05; preparación científica original: 2026-10-04. **20 nuevas curaciones y 2 correcciones mínimas de padres ya CURATED: 22 definiciones / 42 configuraciones en 9 familias.** Aplicación y commit aprobados para **43 rutas (41 + 2 de volumen)**. El catálogo, los seis cambios nativos de compatibilidad y los dos de volumen ya se aplicaron. Land, pytest y las suites Kotlin del corte pasaron. `assembleBaseDebug` incluye el catálogo c67. La instalación y la UI no se ejecutaron: el emulador 5554 arrancó, pero el daemon ADB del puerto 5037 rechazó clientes nuevos.

El corte aplicado contiene **124 CURATED / 82 LEGACY**, 206 definiciones, 521 configuraciones y 413 pares definición × implemento. Fuente, asset Android, recurso JVM e iOS son idénticos por bytes: **SHA256 `c67eeb8ff6a8e68988fd3e0396fe8601920d3cdec7a05973fa95bf6085eb7566`, 2.348.607 bytes**. El antes registrado era 104 CURATED / 102 LEGACY, 523 configuraciones, SHA `1c267f408dce0649a07c72125d90f9aedcf5d2926ed6ec5dd7724ccdcf688099`. Las secciones científicas conservan literalmente la propuesta aprobada; los estados reales de aplicación y pruebas se registran por separado abajo.

**Los dos OK humanos están recibidos:** lote/commit de 41 rutas, incluidas las dos correcciones parentales y compatibilidad, más las dos rutas concretas de volumen, total **43**. [Aprobación inicial](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/USER_APPROVAL.json) · [Aprobación de volumen](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/USER_APPROVAL_VOLUME.json). No se requiere repetirlos ni hay autorización de push. Programas ya entregó LIBRE de fuente/índice Git de ROOT. Alimentos terminó gate39 y liberó Gradle. ADB del host sigue ocupado por un daemon previo que no acepta este cliente.

[43 rutas exactas del commit autorizado](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/final_docs_draft/COMMIT_RUTAS.md) · [Todos los músculos/articulaciones antes → después](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/proposal/ANATOMIA.md) · [Textos y técnica completos de las 42 configuraciones](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/proposal/LOTE_06_TEXTOS.md) · [42 briefs visuales](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/proposal/BRIEFS_VISUALES.txt)

## Decisiones humanas incorporadas

- **Somersault búlgara:** barra recibida en la cadera, pie posterior elevado como en la búlgara; no se copia una barra en trapecios ni una recepción frontal. La cadera se mueve durante la repetición.
- **Hack y V-Squat:** orientación habitual, espalda en respaldo; un pie retrocede durante cada repetición, se realiza el descenso/subida y vuelve a su apoyo habitual. No se conserva una postura partida fija durante toda la serie. La imagen congela el fondo después del paso.
- **Retiro autorizado:** exclusivamente `walking_lunge__smith_machine` y `walking_lunge__cable`. Las zancadas normales frontales/inversas con esos implementos permanecen. Las caminando conservan barra, mancuernas y kettlebell y su default con mancuernas.

Las respuestas originales, sin reinterpretarlas, están en [USER_DECISIONS.md](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/USER_DECISIONS.md). Las restricciones se reflejan en técnica, QA y forbidden.

## Anatomía y errores heredados corregidos

Se reemplaza la plantilla común por funciones y contactos de cada montaje: extensión dinámica de cadera y rodilla; ayuda de brazos en TRX; sostén del tronco libre; agarre colgante, recepción anterior o Zercher; respaldo real de máquinas y apoyo unilateral. Las articulaciones dejan de deducirse únicamente del nombre del patrón.

Hay **16 cambios del conjunto PRIMARY**, de los cuales **2 están protegidos por referencias en código**: cosaca y zancada inversa Hack. **No cambia ningún dominante ni movementPatternId.** Las otras seis configuraciones protegidas conservan PRIMARY y su orden; los ocho cambios protegidos de glúteo se muestran completos en ANATOMIA.md. La inversa corporal conserva glúteo mayor PRIMARY, conforme al contrato de producto.

| Configuración | PRIMARY antes → propuesta | Protección código / tests |
|---|---|---|
| `quads_sentadilla_cosaca__default` | quadriceps → quadriceps, gluteus_maximus | Sí / No |
| `glutes_step_up_gluteo__default` | gluteus_maximus → gluteus_maximus, quadriceps | No / No |
| `glutes_zancada_cruzada__default` | gluteus_maximus → gluteus_maximus, quadriceps | No / No |
| `quads_sentadilla_bulgara_somersault__default` | quadriceps → quadriceps, gluteus_maximus | No / No |
| `quads_sentadilla_pistola__default` | quadriceps → quadriceps, gluteus_maximus | No / No |
| `quads_sentadilla_pistola_asistida_trx__default` | quadriceps → quadriceps, gluteus_maximus | No / No |
| `quads_step_up_cajon_frontal__default` | quadriceps → quadriceps, gluteus_maximus | No / No |
| `quads_step_up_cajon_zercher__default` | quadriceps → quadriceps, gluteus_maximus | No / No |
| `quads_zancada_caminando_frontal_barra_recta__default` | quadriceps → quadriceps, gluteus_maximus | No / No |
| `quads_zancada_caminando_zercher_barra_recta__default` | quadriceps → quadriceps, gluteus_maximus | No / No |
| `quads_zancada_frontal_zercher__default` | quadriceps → quadriceps, gluteus_maximus | No / No |
| `quads_zancada_inversa_frontal__default` | quadriceps → quadriceps, gluteus_maximus | No / No |
| `quads_zancada_inversa_maquina_hack__default` | quadriceps → quadriceps, gluteus_maximus | Sí / No |
| `quads_zancada_inversa_maquina_v_squat__default` | quadriceps → quadriceps, gluteus_maximus | No / No |
| `quads_zancada_inversa_zercher__default` | quadriceps → quadriceps, gluteus_maximus | No / No |
| `quads_sentadilla_bulgara_jefferson__default` | quadriceps → quadriceps, gluteus_maximus | No / No |

El glúteo medio se justifica como STABILIZER por control frontal en estos montajes; el criterio de PRIMARY para abducción/rotación externa resistida no convierte automáticamente una postura unilateral en abducción dinámica. La asistencia de TRX añade bíceps, dorsal y deltoides SECONDARY por cooperación dinámica y antebrazo STABILIZER por agarre; no se rebaja esa cooperación por falta de EMG específico.

## Ampliación explícita: dos padres ya curados

**`bulgarian_split_squat` (6 configuraciones) y `bulgarian_zercher` (1)** reciben exclusivamente anatomy/sources. Se corrige erectores STABILIZER en sus siete montajes sin respaldo, sostén deltoideo/articular en cargas de manos o recepción anterior y bíceps STABILIZER en kettlebell. En sus dos whys de isquios se elimina la comparación EMG como razón de SECONDARY; se conserva su cooperación extensora y su rol.

La máquina búlgara de esa ficha tiene rodillo de pie y mangos cargados, sin respaldo de torso. Barra posterior y Smith no reciben deltoides/agarre colgante por propagación. Esta reparación evita declarar falsas diferencias de erectores frente a Somersault/Jefferson. **PRIMARY, orden, estado CURATED, identidad, public, technique y visual de ambos padres se conservan**. Sus siete briefs no cambian y no generan una nueva cola de PNG. Esta ampliación respecto a las 20 definiciones del brief original se incluye expresamente en el OK solicitado.

## Herencia y conflictos resueltos

**13 diferencias nuevas en 6 relaciones**, comparadas contra los defaults reales y con razón/fuente en [inheritance_proposals.json](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/proposal/inheritance_proposals.json):

- Tres zancadas Zercher: bíceps STABILIZER y ausencia de antebrazo colgante frente al default de mancuernas (6 diferencias).
- Step-Up Zercher: las mismas dos diferencias de contacto de codos/manos (2).
- Pistol TRX: bíceps, dorsal y deltoides SECONDARY, más antebrazo STABILIZER frente a la pistol libre (4).
- Somersault búlgara: ausencia de deltoides de tracción inferior frente al padre con dos mancuernas, porque la pelvis recibe directamente la barra (1).

Se rechazan cuatro propuestas que ocultaban omisiones del padre: dos diferencias frontales por deltoides y dos búlgaras por erectores. Las reglas previas, incluidas las dos de la búlgara Zercher ya aprobadas, se preservan exactamente. No se relaja el léxico ni las reglas generales para cerrar el lote.

## Volumen: causa del extra, según el usuario

> Si ESE EXTRA lo aporta un ejercicio donde ESE músculo es secundario/estabilizador, se permite la desviación. Si ESE EXTRA procede de un ejercicio donde ese músculo es PRINCIPAL, debe ajustarse la prescripción.

Los roles se determinan por la función real, antes de calcular series. La aceptación del exceso debe atribuirse a los ejercicios que aportan ese extra; no se sustituye esta regla causal por aprobar solo un subtotal directo. No se degrada PRIMARY ni se cambia dominante para esconder un exceso.

Por serie de trabajo, el catálogo/UI pondera PRIMARY=1, SECONDARY=0,5 y STABILIZER=0,4. Por ejemplo, SECONDARY→PRIMARY añade una serie principal y 0,5 series equivalentes: son mediciones distintas. [Delta preciso por músculo y configuración](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/proposal/volume_delta_per_set.json) contiene ambos cambios por serie. **No es una semana materializada ni aprueba dosis de recetas o programas**; ese gate debe usar el catálogo aplicado y comprobar el origen del extra antes de aceptar prescripciones.

El historial guardado conserva sus antiguas instantáneas musculares. La aplicación introduce un corte de criterio en los gráficos de volumen; este lote no reescribe el historial.

## Corrección de volumen aprobada: dos rutas adicionales

La revisión de consumidores encontró que `TemplateVolumeScaler` concedía tolerancia al aporte de otro músculo mediante `affected != muscle`, aunque ese aporte fuera PRIMARY. Una serie de Cosaca/Hack añadida para cuádriceps puede elevar glúteos PRIMARY; Step-Up glúteo/curtsy puede elevar cuádriceps PRIMARY al cubrir glúteos. Un subtotal directo dentro del techo no acredita por sí solo el origen del extra.

La corrección aprobada obtiene los grupos PRIMARY efectivos por ejercicio, incluidos los overrides. El grupo objetivo y cualquier otro PRIMARY respetan el techo existente; SECONDARY/STABILIZER mantienen la desviación indirecta permitida. No cambia anatomía, pesos, nombres, patrones ni selección de ejercicios. Conserva `ceiling = min(MRV personalizado, MRV de landmarks)` del escalador; no impone 17,5 como techo universal, pues ese valor pertenece a otro flujo. Una base ya excedida no se recorta aquí: deberá evaluarse y ajustar su prescripción tras materializarla.

| Ruta adicional aprobada | Before SHA256 | Candidato SHA256 |
| --- | --- | --- |
| `android-native/app/src/main/java/com/example/kpkn/domain/training/TemplateVolumeScaler.kt` | `efca6951e81f3c3854e53f2e139df50dd763e15e3da38171c8e75301856cf82b` | `a581ca4bb1e55247bd3860f12ebd9b1878e96009edf41dd289a419c81a715235` |
| `android-native/app/src/test/java/com/example/kpkn/domain/training/TemplateVolumeScaleAuditTest.kt` | `a3763f10b13fcba33b9241854e3fdfc204d75054752acace2fa31f1e834d28b5` | `e7ef91120b0ab4851e813e6e52079db44b625d9e117d3d2a9239f9d9e73dcd8c` |

**Cinco tests nuevos / 12 escenarios preparados, NOT_RUN:** Cosaca/Hack y Step-Up glúteo/curtsy con total en/sobre techo y directo aún dentro; llegada exacta al límite y siguiente pasada; aporte SECONDARY/STABILIZER legítimo; override efectivo PRIMARY 0,5 frente a índice stale. El assert del test compacto existente deja de conceder tolerancia PRIMARY y conserva la base sin recortarla. Los fixtures usan los roles del lote aprobados; no cambian las fichas. Los patches y constructores se revisaron estáticamente y las dos rutas ya se copiaron a shared con sus hashes aprobados; Kotlin aún no se compiló ni ejecutó.

[Impacto y consumidores reales](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/volume_impact/IMPACTO_VOLUMEN.md) · [Revisión estática independiente](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/volume_impact/review_native/REVIEW_VOLUME_NATIVE.md) · [Verificación estática de patches](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/volume_impact/review_native/verification.json) · [Recibo main](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/volume_impact/candidates/main_candidate_receipt.json) · [Recibo tests, FQN y escenarios](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/volume_impact/candidates/test_candidate_receipt.json) · [Scope final 43](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/final_docs_draft/commit_scope.json).

La corrección del escalador no acredita Native4/W2 ni una semana real. Los otros checks de subtotal directo y las recetas ya excedidas conservan sus límites documentados. El gate posterior debe atribuir el extra por ejercicio/rol, ajustar la prescripción cuando proceda de PRIMARY y volver a materializar; no rebajar anatomía.

## Variantes, patrones y límites de evidencia

Somersault exacta y los dos montajes guiados se fijan por tus respuestas, con función inferida desde anatomía y movimientos comparables. No hay medición directa de todos los músculos en cada especialidad frontal, Zercher, curtsy, pistol o TRX. EMG, momento neto y modelos de fuerza no se presentan como prueba automática de dominancia.

Step-Up de glúteo conserva su primer PRIMARY por la intención y el montaje descritos: pie adelantado, tibia aproximadamente vertical y tronco inclinado desde cadera; añade cuádriceps PRIMARY por extensión de rodilla. Se conserva `unilateral_hip_dominant` sin afirmar superioridad muscular universal. Jefferson conserva el patrón asimétrico. No hay otros cambios de patrón ni variantes pendientes de aclaración humana en este lote.

La guía ExRx del Step-Up en polea devolvió HTTP403 en la revisión independiente y no se acredita como demo visto. Los frames de otra Hack no demostraron la ejecución y se descartaron; tampoco se usan vídeos con kettlebells como prueba de Jefferson con barra. La transferencia sacroilíaca se limita a inserciones/modelo cadavérico; no son fuerzas ni ángulos medidos en estas variantes. El informe científico separa función anatómica, ejecución observada, definición humana y extrapolación.

[Revisión anatómica independiente y límites](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/review_anatomy/REVIEW.md) · [Cierre de las 22 fichas: PASA](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/review_anatomy/final_re_review.json) · [Fuentes examinadas](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/review_anatomy/sources_review.json)

## PNG y cola de imágenes

**35 pares del lote:** 13 PNG realmente vistos, **3 PASA / 10 FALLA / 22 SIN_IMAGEN**. La cola tiene **32 pares únicos**, excluye los tres aprobados y los dos walking retirados. Se respondieron 120 QA literales: 75 aplicables y 45 no aplicables por implemento. Una condición crítica no observable no se convierte en aprobación.

PASA: pistol corporal, Step-Up barra y Step-Up Smith. Los fallos incluyen soporte posterior no verificable, número/posición de cargas y agarres ocultos. La cosaca muestra una postura válida en el lado anatómico opuesto al fixture literal; FALLA ese contrato, no la validez biomecánica de hacerla al otro lado. No se retocó QA para aprobar el PNG.

[Revisión visual y controles](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/review_visual/REVIEW.md) · [32 entradas con geometría, técnica, prompt y cambio exacto](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/proposal/image_queue.json)

| Configuración en cola | Motivo | Cambio requerido |
|---|---|---|
| `forward_lunge__barbell` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `forward_lunge__smith_machine` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `forward_lunge__dumbbells` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `forward_lunge__kettlebell` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `forward_lunge__cable` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `glutes_step_up_gluteo__default` | FALLA | Mostrar dos mancuernas, una agarrada visiblemente en cada mano con brazos largos; situar el pie libre apenas separado del suelo en el primer tercio. Conservar pie elevado entero, tibia próxima a vertical y flexión de cadera. |
| `glutes_zancada_cruzada__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_sentadilla_bulgara_jefferson__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_sentadilla_bulgara_somersault__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_sentadilla_cosaca__default` | FALLA | Reponer el fotograma con pierna anatómica izquierda doblada y derecha extendida al costado, conservando la planta izquierda y el talón derecho sobre el suelo y los dedos derechos arriba. No cambiar QA para acomodar este PNG. |
| `quads_sentadilla_pistola_asistida_trx__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_step_up_cajon_frontal__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_step_up_cajon_zercher__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_zancada_caminando_frontal_barra_recta__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_zancada_caminando_zercher_barra_recta__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_zancada_frontal_zercher__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_zancada_inversa_frontal__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_zancada_inversa_maquina_hack__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_zancada_inversa_maquina_v_squat__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `quads_zancada_inversa_zercher__default` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `reverse_lunge__barbell` | FALLA | Mostrar el fondo de la zancada con los dedos posteriores plantados de forma inequívoca sobre el mismo suelo del pie delantero, talón posterior elevado y rodilla posterior cerca del piso sin apoyarla. Conservar la barra en espalda alta y ambos agarres. |
| `reverse_lunge__smith_machine` | FALLA | Bajar al fondo y apoyar de forma visible los dedos posteriores sobre el suelo dentro de la Smith, con talón posterior elevado y rodilla trasera cercana al piso sin contacto. Mantener barra sobre espalda alta, dos carros/guías reales y ambos agarres. |
| `reverse_lunge__dumbbells` | FALLA | Mantener las dos mancuernas laterales con ambos agarres visibles y representar el fondo con antepié posterior inequívocamente plantado, talón posterior elevado y rodilla retrasada cerca del piso. |
| `reverse_lunge__kettlebell` | FALLA | Reponer una sola kettlebell frente al esternón con ambas manos agarrando los lados del asa y codos doblados hacia abajo; mostrar antepié posterior plantado, talón posterior elevado y fondo cerca del suelo. |
| `reverse_lunge__cable` | FALLA | Eliminar el escalón frontal. Mostrar el pie delantero entero sobre suelo plano y el fondo con ambas rodillas flexionadas, dedos posteriores plantados y talón posterior elevado. Mantener una asa junto a cadera y polea baja delante. |
| `reverse_lunge__bodyweight` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `step_up__dumbbells` | FALLA | Cambiar el encuadre para que se vea cada mano agarrando su mancuerna de forma inequívoca, con dos brazos largos y cargas fuera de los muslos/bordes. Conservar pie entero en cajón, rodilla flexionada y pie libre detrás en el aire. |
| `step_up__kettlebell` | FALLA | Reponer una sola kettlebell delante del esternón con ambas palmas rodeando y sosteniendo visiblemente su cuerpo redondo, codos flexionados y asa libre del cajón. Conservar la postura de Step-Up y el pie libre suspendido. |
| `step_up__cable` | FALLA | Mostrar dos poleas bajas próximas al cajón y dos cables tensos por fuera de las piernas; cada mano debe agarrar su propia asa con brazo largo. Evitar contacto de cables con el borde. Conservar el pie elevado entero y el pie libre suspendido. |
| `walking_lunge__barbell` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `walking_lunge__dumbbells` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |
| `walking_lunge__kettlebell` | MISSING_IMAGE | Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada. |

Los siete briefs parentales se reemiten solo como referencia del alcance anatómico adicional; no cambian. Este trabajo no genera ni modifica imágenes.

## Compatibilidad de los retiros

La propuesta Kotlin remapea cada ID retirado a la zancada frontal del mismo implemento, con definitionId, configurationId y performanceProfileId coherentes. Conserva receta, ocurrencia, modificadores y nombres personalizados; las parejas de identidad incorrectas siguen siendo inválidas. El retiro estructural conserva defaults, nombres, searchTerms, revisión y las tres caminando válidas.

El script publicable usa un plan de hashes y se niega ante cambios o salida adulterada; sus **8 pruebas pasaron**, incluyendo idempotencia, pérdida de una sola variante, identidad de implementos y abortos previos a escritura. [Pruebas privadas publicables](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/proposed/publication/tests.log) · [Seis propuestas Kotlin y hashes](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/proposed/native/manifest.json).

La [revisión estática independiente](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/review_native/REVIEW_NATIVE.md) no detectó un bug de producción. Se corrigió en la propuesta una brecha del fixture de pruebas: ahora compara el perfil completo de la zancada, sus IDs/región/unilateralidad y la resolución de las cuatro variantes frontales/inversas Smith/polea conservadas. [Recibo del refinamiento privado](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/proposal/native_refinement_receipt.json). Los otros cinco candidatos no cambian.

**Kotlin de este corte ya compiló y ejecutó las suites dirigidas y `testBaseDebugUnitTest`.** Los conteos contractuales quedan en 521 configuraciones y el pin sigue en el asset final c67. No se cambia ranking ni subcadenas de búsqueda. El comentario previo del reconciliador y el TODO ajeno quedan fuera del commit.

## Recursos y ejecución por etapas

**Programas entregó LIBRE de ROOT_SOURCE y ROOT_GIT_INDEX a las 04:27:38 UTC del 2026-10-05**, con HEAD de relevo `5e264cda426dd51c837fb73a6aea11ff5150132d`. [Recibo de liberación](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/shared_release.json). La captura de 43 rutas y las bases previas validaron 200 hashes congelados, seis bases nativas y dos de volumen. Se aplicaron retiro/splice de 22 cuerpos, seis candidatos nativos y dos de volumen; el pipeline de land terminó con exit 0.

**Alimentos terminó gate39** en el checkout aislado e733 y dejó libres los tres candados de Gradle. ROOT ejecutó Gradle sobre c67. El emulador `Pixel_9_Pro_XL` del puerto 5554 informó boot completado y no pudo registrarse en el daemon ADB del puerto 5037. La evidencia antigua SHA1c/523 no acredita este runtime. El APK nuevo sí contiene c67.

[Captura previa](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/before_manifest.json) · [Retiro/splice y candidatos aplicados](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/retire_splice_receipt.json) · [Land exit 0](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/land_exit.json) · [Log de land](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/land.log) · [Paridad de los cuatro archivos](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/runtime_parity.json) · [Coordinación por recurso](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/RUN_STATE.md). · [Gate estricto postland READY](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/gate_strict.log) · [Audit global postland: 2222/96](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/global_audit.json) · [Exit del audit](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/global_audit_exit.json)

Pytest posterior al land ya terminó: **214 passed en el resumen**, exit 0; JUnit registra `tests=376`, `failures=0`, `errors=0`, `skipped=0`. Se informan ambos contadores sin equipararlos ni atribuir subtests por inferencia. Comando real: `uv run --offline --no-project --with pytest --with pydantic python -X utf8 -m pytest scripts/tests backend/tests/test_exercises_catalog_v2.py`. [Log final](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/python_tests_uv2.log) · [Exit final](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/python_exit_uv2.json) · [JUnit final](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/python_junit.xml).

Los dos intentos previos se conservan como incidencias de entorno: falta de pytest (exit 1) y falta de pydantic al recoger tests (exit 2). [Primer intento](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/python_exit.json) · [Segundo intento](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/python_exit_uv.json) · [Colección fallida archivada](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/approved_land/python_collection_failure_uv.xml). Esos intentos no se presentan como PASS ni sustituyen el resultado final.

## Validación y aplicación pendiente

| Evidencia | Resultado |
|---|---|
| Lint integral final, ronda 2 de máximo 3 | 22 defs / 42 cfg; gate=0, errores=0, avisos=0. Ronda 1 archivada con sus fallos editoriales. |
| Fuentes scoped | 324 citas / 47 URLs; proof sin faltantes; 43 abstracts independientes y siete capítulos anatómicos leídos. |
| Auditoría extendida privada y audit global postland | Privado: 2654→2222 errores, 116→96 avisos. Postland real: comando exit 0, 2222 ERROR/96 WARN y cero hallazgos para las 22 definiciones del lote. Son hallazgos pendientes del resto; exit 0 no aprueba toda la ciencia del catálogo. |
| Integridad previa a la aplicación | Capture de 43 rutas PASSED: 200 hashes congelados y ocho bases (6 nativas + 2 volumen) validados antes de mutar. Los 184 cuerpos ajenos se preservaron en la propuesta. No se afirma que los before hashes sigan siendo los archivos actuales tras el land. |
| Revisión limpia | Ciencia PASA 22; visual sin hallazgos abiertos de brief, con cola de PNG explícita. |
| Python del retiro | 8/8 PASARON en fixtures privados. |
| Lectura estática de compatibilidad | Seis propuestas leídas; sin bug de producción detectado; brecha de cobertura corregida solo en el candidato de tests. Kotlin posterior: compilado y ejecutado. |
| Land / merge / gate / compiler / pin en shared | PASSED, land exit 0; gate READY; lint scoped 0 errores/0 avisos; compiler 206 definiciones/521 configuraciones; pin al SHA final c67eeb8f… . Paridad de cuatro archivos comprobada. |
| Pytest scripts/backend tras aplicar | PASSED: 214 en resumen, exit 0; JUnit tests=376/failures=0/errors=0/skipped=0. Dos intentos fallidos de entorno archivados por separado. |
| testBaseDebugUnitTest / materialización de programas | Suite completa: 6348 tests, 1 fallo preexistente de `WorkoutSnapshotCommitTest`, 0 errores, 2 omitidos. Volumen 9/9. Materialización: 0 excesos de directo PRIMARY; solo Cosaca se prescribe entre las 16 promociones. |
| Build / instalación / UI del lote 6 | `assembleBaseDebug` PASSED. APK SHA-256 `fff8cca968de1ae594213de4a0cc075304abb8f58315ea75db93fe0f565d277c`; el catálogo interno es c67. Instalación y MainActivity NOT_RUN: daemon ADB 5037 inaccesible. |
| Commit / push | Commit acotado a las 43 rutas en este cierre. Push NOT_AUTHORIZED. |

Ya se completaron capture, retiro/splice, land, pytest, suites Kotlin, materialización y `assembleBaseDebug`. La instalación y la apertura de MainActivity siguen pendientes del daemon ADB. El commit cubre las 43 rutas autorizadas y conserva el comentario previo del reconciliador y el TODO ajeno fuera del índice. Push no está autorizado. La verificación UI final del catálogo completo sigue siendo un gate independiente.

Los OK ya registrados satisfacen el requisito del plan: «Cada lote cierra con un informe; se aplica y se hace commit solo con OK». Se conservan los recibos inicial y complementario sin volver a pedir aprobación del mismo alcance. [Plan original](<C:/Users/valen/.codex/attachments/08ae1170-7e7f-422f-a97c-52219c3c76a5/Texto pegado.txt>).

[Recibo de integridad y estados separados](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/proposal/scope_integrity.json) · [Log del lint que gobierna el lote](C:/Users/valen/Documents/KPKNFit/artifacts/catalog-lote06-preparation-20261004/integration/integration_round_02/lint.log)
