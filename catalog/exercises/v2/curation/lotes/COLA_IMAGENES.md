# Cola de imágenes aprobadas — catálogo de ejercicios

# Lote 5: cola de imágenes aprobada

**COLA APROBADA — 4 de octubre de 2026. Imágenes pendientes, no generadas.** La anatomía y los textos del lote están aprobados y aplicados; se comprobó la igualdad de sus 21 cuerpos compartidos con la integración. Los originales y sus resultados de revisión se conservan.

Cobertura exacta: **46 pares**, incluidos los **3 pares del buenos días sentado apartado / LEGACY**. Los registros A y B documentan **35 PNG realmente vistos** y **11 pares sin imagen**. Este documento combina sus observaciones; no añade una inspección de píxeles del generador.

Resultados: **14 PASA, 21 FALLA, 11 SIN_IMAGEN**. El lote aplicado tiene **43 pares** y **32 entradas de cola aprobadas**: se incluyen FALLA o SIN_IMAGEN. Los tres pares del sentado quedan separados, fuera del lote aplicado.

PASA describe el fotograma observable y no prueba fuerzas, trayectoria completa o dominancia muscular. UNCLEAR de la revisión B se conserva como respuesta original y se normaliza a FALLA para la cola. Una imagen puede FALLAR por su prompt/contacto aunque sus QA parciales pasen. Los apoyos de suelo implícitos en recortes y las condiciones no aplicables se explican en las respuestas.

## Cola aprobada del lote aplicado

### Peso Muerto Convencional — `conventional_deadlift` / `barbell`

**FALLA** — Fotograma bajo, distinto del ascenso intermedio a altura de rodillas exigido en visual.base y promptCore.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_convencional.png`. SHA-256: `eeab8eb48d64a9250ad0e366f7ff3923a987c076dabc401818d18929b4c53f07`.

- **QA2 — FALLA**: ¿El peso está a la altura de las rodillas y separado del suelo? El eje se ve a media espinilla, claramente por debajo de las rodillas; no representa la altura de rodilla elegida.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\hinge_deadlift.json`.

### Peso Muerto Convencional — `conventional_deadlift` / `dumbbells`

**FALLA** — Fotograma de salida baja con mayor flexión de rodilla que el ascenso intermedio solicitado.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_convencional_mancuernas.png`. SHA-256: `5f97f232a518c7b82c55b8d7950fb3e034ba06f736bb67b2563e56ae938b56cc`.

- **QA2 — FALLA**: ¿El peso está a la altura de las rodillas y separado del suelo? Las mancuernas aparecen cerca de tobillos/zapatos, no a la altura de las rodillas.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\hinge_deadlift.json`.

### Peso Muerto Convencional — `conventional_deadlift` / `hex_bar`

**FALLA** — Además de la fase baja, el atleta agarra ASAS ALTAS sobre montantes: contradice asas bajas en técnica, geometría y promptCore.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_convencional_hex_bar.png`. SHA-256: `0ebbcce7ab506ffd6181e1d443401544304032e43e8cef78ad8cf12abba0a1d6`.

- **QA2 — FALLA**: ¿El peso está a la altura de las rodillas y separado del suelo? El marco y los discos están debajo de las rodillas, no nivelados a su altura.
- **QA6 — FALLA**: ¿Si aparece la barra hexagonal, las manos usan las asas bajas a la altura del marco? Las manos agarran asas altas sobre montantes, no las asas a altura del marco.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\hinge_deadlift.json`.

### Peso Muerto Sumo — `sumo_deadlift` / `barbell`

**FALLA** — Postura sumo reconocible, pero el fotograma corresponde a una posición baja en vez de la fase intermedia requerida.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_sumo.png`. SHA-256: `58cd1068c0818ab445156a6d82a99a4d68805db70bcfdc02fbed42ad51d627df`.

- **QA3 — FALLA**: ¿El peso está elevado a la altura de las rodillas y no sobre el suelo? La barra aparece a media espinilla y los discos bajos; no está a altura de rodilla.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\hinge_deadlift.json`.

### Peso Muerto Sumo — `sumo_deadlift` / `dumbbells`

**FALLA** — Identidad sumo coherente; carga demasiado baja para visual.base.phase y promptCore.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_sumo_mancuernas.png`. SHA-256: `9098f27bd062268ee879bb095bcf9f2c20941d879b8967b7615495ef0a395229`.

- **QA3 — FALLA**: ¿El peso está elevado a la altura de las rodillas y no sobre el suelo? Las cabezas están próximas a tobillos/pies, no a altura de rodilla.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\hinge_deadlift.json`.

### Buenos Días — `good_morning` / `machine`

**FALLA** — La QA6 reparada detecta el defecto: apoyo posterior de pelvis/muslos, sin el apoyo anterior elegido. El PNG no acredita una estación real concreta compatible y no se adapta el brief a él.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_buenos_dias_maquina.png`. SHA-256: `d8dc4703e36e4181d5b7a75ec3f5b27acccd237086678963c2ba3dc00fcc314d`.

- **QA6 — FALLA**: ¿Si aparece la máquina, su apoyo anterior contacta con la pelvis y la palanca con la espalda alta? No hay contacto de pelvis con apoyo anterior: el acolchado visible es posterior. La palanca sí toca espalda alta/hombros.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\hinge_good_morning.json`.

### Buenos Días/RDL Zercher — `good_morning_zercher` / `barbell`

**FALLA** — Rehacer el contacto del eje en los pliegues de ambos codos y juntar las manos si se mantiene la ejecución elegida en el brief.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_buenos_dias_zercher.png`. SHA-256: `787978af0df729788187d1206f9d2df769d06ec9cba76efda94febcde667ed12`.

- **QA1 — FALLA**: ¿La barra está apoyada dentro de los dos pliegues del codo y no flota? El eje cruza por delante de los antebrazos, por encima de los pliegues del codo visibles; no dibuja el apoyo en ambos pliegues exigido.
- **QA2 — FALLA**: ¿Las manos están juntas sin agarrar el eje de la barra? Hay dos puños separados; las manos no están juntas como exige la ficha.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\hinge_good_morning.json`.

### Peso Muerto Rumano — `romanian_deadlift` / `hex_bar`

**FALLA** — La flexión visible de rodillas parece mayor que la pequeña flexión pedida y el ángulo no permite resolverla con precisión. Las manos usan asas elevadas sobre el marco, mientras geometry y promptCore exigen asas bajas.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_rumano_hex_bar.png`. SHA-256: `22b8425ecf9b0be7a3714f4b87e4f5d775bfef3a1babe51fd163ffac7bd6c39e`.

- **QA1 — FALLA**: ¿Las rodillas están casi rectas o con una flexión pequeña, sin una sentadilla profunda? La perspectiva no deja confirmar que la flexión visible sea pequeña.

Revisión: `review_b/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\hinge_rdl.json`.

### Peso Muerto Rumano Sumo — `romanian_sumo_deadlift` / `barbell`

**FALLA** — Rodillas claramente dobladas, cadera relativamente baja y torso demasiado erguido para el punto inferior rumano; además eleva cabeza y mirada. Parece un despegue sumo, no la bisagra descendente elegida.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_rumano_sumo.png`. SHA-256: `16dcc21ba3e0cd8ec10429272ab7440c3bd5ac4662c2831a1d58ce56ca8c37ec`.

- **QA1 — FALLA**: ¿Las rodillas están casi rectas o con una flexión pequeña, sin una sentadilla profunda? Rodillas claramente dobladas y cadera baja respecto de la bisagra pedida.
- **QA3 — FALLA**: ¿El torso está inclinado desde la cadera y la carga sigue suspendida en el punto inferior? Carga suspendida, pero torso demasiado erguido para el final de una bisagra rumana.
- **QA4 — FALLA**: ¿La pelvis está detrás del apoyo y la columna conserva una línea neutra? La proyección frontal y la cadera baja no permiten aprobar el retraso pélvico solicitado.

Revisión: `review_b/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\hinge_rdl.json`.

### Peso Muerto Rumano Sumo — `romanian_sumo_deadlift` / `dumbbells`

**FALLA** — Mancuernas suspendidas entre los muslos, pero la postura combina rodillas muy dobladas con torso erguido y mirada elevada; falta la bisagra rumana de cadera atrás.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_rumano_sumo_mancuernas.png`. SHA-256: `6a378efe6a46d40da33d277f76eee1784a8fd81420653fbfbe3f16d23406e369`.

- **QA1 — FALLA**: ¿Las rodillas están casi rectas o con una flexión pequeña, sin una sentadilla profunda? Rodillas muy flexionadas y muslos abiertos como postura de salida sumo.
- **QA3 — FALLA**: ¿El torso está inclinado desde la cadera y la carga sigue suspendida en el punto inferior? Carga suspendida, pero torso erguido en vez de inclinado desde cadera.
- **QA4 — FALLA**: ¿La pelvis está detrás del apoyo y la columna conserva una línea neutra? Cadera baja; no se reconoce suficientemente el retraso pélvico de una bisagra.

Revisión: `review_b/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\hinge_rdl.json`.

### Peso Muerto Rumano Sumo — `romanian_sumo_deadlift` / `hex_bar`

**FALLA** — Postura con rodillas dobladas y torso erguido; base dentro del marco más próxima al ancho de caderas que a la apertura sumo solicitada, y asas elevadas. No demuestra una jaula compatible con una base más ancha que los hombros.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_rumano_sumo_hex_bar.png`. SHA-256: `288b8a3002e5b63ec8f48dfafa7da7c08484e179233060fa9fc6ce5d4c440df9`.

- **QA1 — FALLA**: ¿Las rodillas están casi rectas o con una flexión pequeña, sin una sentadilla profunda? Rodillas dobladas y torso alto.
- **QA3 — FALLA**: ¿El torso está inclinado desde la cadera y la carga sigue suspendida en el punto inferior? Marco suspendido, pero falta la inclinación propia de la bisagra seleccionada.
- **QA4 — FALLA**: ¿La pelvis está detrás del apoyo y la columna conserva una línea neutra? La apertura frontal oculta y no confirma una pelvis retrasada.
- **QA5 — FALLA**: ¿Los dos pies están separados en una base ancha y orientados hacia afuera? Los pies caben próximos dentro del marco y no muestran base mayor que los hombros.

Revisión: `review_b/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\hinge_rdl.json`.

### Hiperextensiones a 45° para Glúteos — `glutes_hiperextension_45` / `barbell`

**SIN_IMAGEN** — El inventario no asigna PNG a este par. Se revisó el brief textual, sin afirmar haber visto una imagen.

Sin PNG asignado en el inventario; no se afirma haber visto una imagen.

- **QA1 — SIN_IMAGEN**: ¿El torso está abajo con una cadera visiblemente flexionada y una espalda de perfil estable? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA2 — SIN_IMAGEN**: ¿El borde del acolchado queda debajo del pliegue de cadera? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA3 — SIN_IMAGEN**: ¿Tobillos y pies permanecen sujetos en el banco? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA4 — SIN_IMAGEN**: ¿Las manos sujetan la carga y esta toca su apoyo o cuelga de un agarre real? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA5 — SIN_IMAGEN**: ¿La carga está libre del suelo y los rieles se ven conectados cuando hay Smith? No existe PNG asignado en el inventario; no se evalúa visualmente.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_extension.json`.

### Hiperextensiones a 45° para Glúteos — `glutes_hiperextension_45` / `dumbbells`

**SIN_IMAGEN** — El inventario no asigna PNG a este par. Se revisó el brief textual, sin afirmar haber visto una imagen.

Sin PNG asignado en el inventario; no se afirma haber visto una imagen.

- **QA1 — SIN_IMAGEN**: ¿El torso está abajo con una cadera visiblemente flexionada y una espalda de perfil estable? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA2 — SIN_IMAGEN**: ¿El borde del acolchado queda debajo del pliegue de cadera? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA3 — SIN_IMAGEN**: ¿Tobillos y pies permanecen sujetos en el banco? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA4 — SIN_IMAGEN**: ¿Las manos sujetan la carga y esta toca su apoyo o cuelga de un agarre real? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA5 — SIN_IMAGEN**: ¿La carga está libre del suelo y los rieles se ven conectados cuando hay Smith? No existe PNG asignado en el inventario; no se evalúa visualmente.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_extension.json`.

### Hiperextensiones a 45° para Glúteos — `glutes_hiperextension_45` / `plate`

**SIN_IMAGEN** — El inventario no asigna PNG a este par. Se revisó el brief textual, sin afirmar haber visto una imagen.

Sin PNG asignado en el inventario; no se afirma haber visto una imagen.

- **QA1 — SIN_IMAGEN**: ¿El torso está abajo con una cadera visiblemente flexionada y una espalda de perfil estable? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA2 — SIN_IMAGEN**: ¿El borde del acolchado queda debajo del pliegue de cadera? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA3 — SIN_IMAGEN**: ¿Tobillos y pies permanecen sujetos en el banco? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA4 — SIN_IMAGEN**: ¿Las manos sujetan la carga y esta toca su apoyo o cuelga de un agarre real? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA5 — SIN_IMAGEN**: ¿La carga está libre del suelo y los rieles se ven conectados cuando hay Smith? No existe PNG asignado en el inventario; no se evalúa visualmente.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_extension.json`.

### Hiperextensiones a 45° para Glúteos — `glutes_hiperextension_45` / `smith_machine`

**SIN_IMAGEN** — El inventario no asigna PNG a este par. Se revisó el brief textual, sin afirmar haber visto una imagen.

Sin PNG asignado en el inventario; no se afirma haber visto una imagen.

- **QA1 — SIN_IMAGEN**: ¿El torso está abajo con una cadera visiblemente flexionada y una espalda de perfil estable? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA2 — SIN_IMAGEN**: ¿El borde del acolchado queda debajo del pliegue de cadera? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA3 — SIN_IMAGEN**: ¿Tobillos y pies permanecen sujetos en el banco? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA4 — SIN_IMAGEN**: ¿Las manos sujetan la carga y esta toca su apoyo o cuelga de un agarre real? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA5 — SIN_IMAGEN**: ¿La carga está libre del suelo y los rieles se ven conectados cuando hay Smith? No existe PNG asignado en el inventario; no se evalúa visualmente.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_extension.json`.

### Hiperextensión a 45 Zercher para Glúteos — `glutes_hiperextension_45_zercher` / `barbell`

**SIN_IMAGEN** — El inventario no asigna PNG a este par. Se revisó el brief textual, sin afirmar haber visto una imagen.

Sin PNG asignado en el inventario; no se afirma haber visto una imagen.

- **QA1 — SIN_IMAGEN**: ¿La imagen inferior presenta flexión de cadera con perfil lumbar neutro? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA2 — SIN_IMAGEN**: ¿El acolchado queda por debajo del pliegue para permitir el pivote? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA3 — SIN_IMAGEN**: ¿La barra toca ambos pliegues de codo y las manos se juntan sin agarrar su eje? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA4 — SIN_IMAGEN**: ¿Los tobillos están sujetos y los discos no tocan el banco ni el suelo? No existe PNG asignado en el inventario; no se evalúa visualmente.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_extension.json`.

### Pull-Through — `hams_pull_through` / `cable`

**FALLA** — PNG de recepción/bisagra baja en lugar del final de extensión escogido. La cuerda y la polea posterior sí son reconocibles.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_pull_through.png`. SHA-256: `1939eb47219f323590bd86fe5298783ee17138a56bc99d21ee33035dc2fa25e9`.

- **QA3 — FALLA**: ¿Cada mano agarra un extremo y ambas manos quedan bajas delante de la pelvis? Cada mano tiene un extremo, pero las manos están entre los muslos en la bisagra baja, no delante de la pelvis en el final erguido.
- **QA4 — FALLA**: ¿El cuerpo está erguido sobre ambos pies con rodillas suavemente flexionadas? El torso está inclinado y las rodillas bastante dobladas; no muestra el final erguido de la ficha.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_hinge.json`.

### Peso Muerto Convencional en Déficit — `hams_peso_muerto_convencional_deficit` / `barbell`

**FALLA** — Discos claramente suspendidos para el despegue mínimo elegido; la geometría de déficit y la QA4 reparada sí pasan.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_convencional_deficit.png`. SHA-256: `39f832229f96d6350f751488dad8f2eaf5302d6baf133ff66e904160d583788c`.

- **QA3 — FALLA**: ¿Los discos están fuera del escalón y apenas despegados del suelo inferior? Los discos están claramente suspendidos sobre el piso inferior, no apenas despegados.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_hinge_deficit.json`.

### Peso Muerto Piernas Rígidas en Déficit — `hams_peso_muerto_piernas_rigidas_deficit` / `barbell`

**FALLA** — Los pies están en plataforma y la barra se agarra, pero los discos están suspendidos claramente sobre el piso inferior; falta el reposo previo al despegue exigido por phase. La altura del eje sobre los pies no es el motivo del rechazo.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_piernas_rigidas_deficit.png`. SHA-256: `1f9fa446bc0e1cbf59707c870788bc46fe223719186b6c40733e4dbca496b5d3`.

- **QA3 — FALLA**: ¿El fotograma muestra la carga en su apoyo inferior justo antes del despegue? Los discos se ven suspendidos; no reposan en el piso inferior antes del despegue.

Revisión: `review_b/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_hinge_deficit.json`.

### Peso Muerto Sumo en Déficit — `hams_peso_muerto_sumo_deficit` / `barbell`

**FALLA** — Dos pilas separadas en vez de la plataforma ancha elegida y discos altos para el despegue mínimo. El desnivel de pies respecto del piso inferior pasa la QA4 reparada.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_sumo_deficit.png`. SHA-256: `a87bd6b432311f78e6c23bfe9897095f00a768e6f16aedb217e3da98addf7d2b`.

- **QA1 — FALLA**: ¿Los dos pies abiertos tienen toda la planta sobre una plataforma ancha? Cada pie está sobre una pila distinta de discos; no aparecen los dos sobre la plataforma ancha continua especificada.
- **QA3 — FALLA**: ¿Los discos quedan fuera de la plataforma y apenas levantados del suelo inferior? Los discos cargados están suspendidos claramente por encima del piso inferior.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_hinge_deficit.json`.

### Peso Muerto Piernas Rígidas — `stiff_leg_deadlift` / `barbell`

**FALLA** — Muestra una bisagra casi extendida de rodilla con la barra agarrada, pero no el contacto de los discos con su apoyo bajo antes del despegue.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_piernas_rigidas.png`. SHA-256: `233c2c2dc373325b18e4ded04c5c0dac2657ec03cf8f4feb5839acc0a6f9ab8a`.

- **QA3 — FALLA**: ¿El fotograma muestra la carga en su apoyo inferior justo antes del despegue? No aparece contacto de los discos con un apoyo inferior; la carga queda suspendida.

Revisión: `review_b/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_hinge_lengthened.json`.

### Peso Muerto Piernas Rígidas — `stiff_leg_deadlift` / `dumbbells`

**FALLA** — Las mancuernas cuelgan a la altura de las tibias y no se ve contacto con el apoyo de partida; ilustra una repetición suspendida, no el inicio apoyado elegido.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_piernas_rigidas_mancuernas.png`. SHA-256: `9c1d105e72ac78338e5e79abfe5c40baaaabf261b8388558eb66668bde189723`.

- **QA3 — FALLA**: ¿El fotograma muestra la carga en su apoyo inferior justo antes del despegue? Las cabezas de las mancuernas quedan suspendidas por encima de los pies; no hay apoyo inicial visible.

Revisión: `review_b/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_hinge_lengthened.json`.

### Peso Muerto Piernas Rígidas — `stiff_leg_deadlift` / `hex_bar`

**FALLA** — El marco está suspendido y no muestra los discos apoyados antes del despegue. Las manos usan asas elevadas aunque la ficha pide las bajas.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_piernas_rigidas_hex_bar.png`. SHA-256: `7c9db8a8f4f707a3855284e3f19938e61122937014073650f7c19cbebf3fbddd`.

- **QA3 — FALLA**: ¿El fotograma muestra la carga en su apoyo inferior justo antes del despegue? Los discos no muestran contacto con su apoyo inferior antes del despegue.

Revisión: `review_b/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_hinge_lengthened.json`.

### Peso Muerto Piernas Rígidas — `stiff_leg_deadlift` / `smith_machine`

**FALLA** — La barra del Smith está a la altura de los muslos con torso poco inclinado y no sobre un tope de arranque bajo; es una fase alta incompatible con el fotograma previo al despegue elegido.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_piernas_rigidas_smith.png`. SHA-256: `23982e586a99f0445b73e74a52d1090d9ac14fb4290db1b87a18c47deea798b2`.

- **QA3 — FALLA**: ¿El fotograma muestra la carga en su apoyo inferior justo antes del despegue? Barra a los muslos, sin contacto con un tope bajo; no es el arranque descrito.

Revisión: `review_b/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_hip_hinge_lengthened.json`.

### Peso Muerto Rumano en Déficit — `hams_peso_muerto_rumano_deficit` / `barbell`

**FALLA** — La plataforma y los discos suspendidos fuera del soporte cumplen la geometría corregida, aunque el eje quede por encima de las plantas. Cabeza levantada y mirada al frente contradicen cuello neutro/mirada al piso del brief.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_peso_muerto_rumano_deficit.png`. SHA-256: `966cf98e87cff052d19d37766373857bc98f1f8a0b637ba2dc91c322b4b35caf`.

Revisión: `review_b/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_romanian_deadlift.json`.

### Hiperextensiones de Espalda Baja — `back_extension_lumbar` / `machine`

**SIN_IMAGEN** — El inventario no asigna PNG a este par. Se revisó el brief textual, sin afirmar haber visto una imagen.

Sin PNG asignado en el inventario; no se afirma haber visto una imagen.

- **QA1 — SIN_IMAGEN**: ¿La imagen muestra el fotograma inferior con una curva visible de la espalda? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA2 — SIN_IMAGEN**: ¿La pelvis toca el acolchado y los tobillos quedan sujetos bajo los rodillos? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA3 — SIN_IMAGEN**: ¿Los brazos están cruzados contra el pecho sin una carga flotante? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA4 — SIN_IMAGEN**: ¿El ángulo de cadera cambia poco y el torso no se representa como una tabla rígida? No existe PNG asignado en el inventario; no se evalúa visualmente.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_spinal_extension.json`.

### Superman en Suelo — `back_superman_suelo` / `bodyweight`

**FALLA** — FALLA del cuello y mirada. Además, no aparece la colchoneta lisa pedida por byImplement/promptCore y el perfil se orienta a la derecha, distinto de la orientación elegida a la izquierda. La postura prona y elevación bilateral son reconocibles.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_back_superman_suelo_bodyweight_batch10.png`. SHA-256: `c92bc17820b59b251fd168819c0d0fef1467d4cb7a627aa6c29a857fc12353d1`.

- **QA4 — FALLA**: ¿La cabeza sigue la línea del torso y la elevación es pequeña? La cara y la mirada se orientan hacia delante con cuello extendido; no siguen la alineación/mirada baja requerida.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_spinal_extension.json`.

### Hiperextensión a 45 Zercher para Espalda Baja — `back_hiperextension_45_zercher_espalda_baja` / `barbell`

**SIN_IMAGEN** — El inventario no asigna PNG a este par. Se revisó el brief textual, sin afirmar haber visto una imagen.

Sin PNG asignado en el inventario; no se afirma haber visto una imagen.

- **QA1 — SIN_IMAGEN**: ¿La espalda aparece redondeada en el fotograma inferior? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA2 — SIN_IMAGEN**: ¿La pelvis sigue sobre el acolchado y los tobillos están sujetos? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA3 — SIN_IMAGEN**: ¿La barra toca ambos pliegues de codo y las manos se juntan sin agarrar el eje? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA4 — SIN_IMAGEN**: ¿Los discos permanecen por encima del suelo junto al tronco? No existe PNG asignado en el inventario; no se evalúa visualmente.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_spinal_extension.json`.

### Jefferson Curl — `back_jefferson_curl` / `barbell`

**SIN_IMAGEN** — El inventario no asigna PNG a este par. Se revisó el brief textual, sin afirmar haber visto una imagen.

Sin PNG asignado en el inventario; no se afirma haber visto una imagen.

- **QA1 — SIN_IMAGEN**: ¿La espalda se ve curvada de arriba abajo en el fotograma inferior? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA2 — SIN_IMAGEN**: ¿Ambos pies están firmes y las rodillas casi extendidas? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA3 — SIN_IMAGEN**: ¿Las manos agarran la carga con los codos largos, sin objetos flotando? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA4 — SIN_IMAGEN**: ¿La carga está suspendida y separada del suelo? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA5 — SIN_IMAGEN**: ¿El cable o los rieles, cuando corresponden, permanecen unidos al implemento? No existe PNG asignado en el inventario; no se evalúa visualmente.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_spinal_flexion.json`.

### Jefferson Curl — `back_jefferson_curl` / `cable`

**SIN_IMAGEN** — El inventario no asigna PNG a este par. Se revisó el brief textual, sin afirmar haber visto una imagen.

Sin PNG asignado en el inventario; no se afirma haber visto una imagen.

- **QA1 — SIN_IMAGEN**: ¿La espalda se ve curvada de arriba abajo en el fotograma inferior? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA2 — SIN_IMAGEN**: ¿Ambos pies están firmes y las rodillas casi extendidas? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA3 — SIN_IMAGEN**: ¿Las manos agarran la carga con los codos largos, sin objetos flotando? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA4 — SIN_IMAGEN**: ¿La carga está suspendida y separada del suelo? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA5 — SIN_IMAGEN**: ¿El cable o los rieles, cuando corresponden, permanecen unidos al implemento? No existe PNG asignado en el inventario; no se evalúa visualmente.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_spinal_flexion.json`.

### Jefferson Curl — `back_jefferson_curl` / `dumbbells`

**SIN_IMAGEN** — El inventario no asigna PNG a este par. Se revisó el brief textual, sin afirmar haber visto una imagen.

Sin PNG asignado en el inventario; no se afirma haber visto una imagen.

- **QA1 — SIN_IMAGEN**: ¿La espalda se ve curvada de arriba abajo en el fotograma inferior? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA2 — SIN_IMAGEN**: ¿Ambos pies están firmes y las rodillas casi extendidas? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA3 — SIN_IMAGEN**: ¿Las manos agarran la carga con los codos largos, sin objetos flotando? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA4 — SIN_IMAGEN**: ¿La carga está suspendida y separada del suelo? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA5 — SIN_IMAGEN**: ¿El cable o los rieles, cuando corresponden, permanecen unidos al implemento? No existe PNG asignado en el inventario; no se evalúa visualmente.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_spinal_flexion.json`.

### Jefferson Curl — `back_jefferson_curl` / `smith_machine`

**SIN_IMAGEN** — El inventario no asigna PNG a este par. Se revisó el brief textual, sin afirmar haber visto una imagen.

Sin PNG asignado en el inventario; no se afirma haber visto una imagen.

- **QA1 — SIN_IMAGEN**: ¿La espalda se ve curvada de arriba abajo en el fotograma inferior? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA2 — SIN_IMAGEN**: ¿Ambos pies están firmes y las rodillas casi extendidas? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA3 — SIN_IMAGEN**: ¿Las manos agarran la carga con los codos largos, sin objetos flotando? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA4 — SIN_IMAGEN**: ¿La carga está suspendida y separada del suelo? No existe PNG asignado en el inventario; no se evalúa visualmente.
- **QA5 — SIN_IMAGEN**: ¿El cable o los rieles, cuando corresponden, permanecen unidos al implemento? No existe PNG asignado en el inventario; no se evalúa visualmente.

Revisión: `review_a/images.json`. Brief final: `C:\Users\valen\Documents\KPKNFit\artifacts\catalog-lote05-20261004\integration\fichas\lower_spinal_flexion.json`.

## Pares del lote aplicado que pasan

| Definición | Implemento | Revisor |
|---|---|---|
| `conventional_deadlift` | `smith_machine` | `review_a` |
| `good_morning` | `barbell` | `review_a` |
| `good_morning` | `safety_bar` | `review_a` |
| `good_morning` | `smith_machine` | `review_a` |
| `romanian_deadlift` | `barbell` | `review_b` |
| `romanian_deadlift` | `dumbbells` | `review_b` |
| `romanian_deadlift` | `smith_machine` | `review_b` |
| `romanian_sumo_deadlift` | `smith_machine` | `review_b` |
| `hams_swing_kettlebell_dos_manos` | `kettlebell` | `review_a` |
| `hams_swing_kettlebell_unilateral` | `kettlebell` | `review_a` |
| `hams_peso_muerto_rumano_sumo_deficit` | `barbell` | `review_b` |

## Buenos días sentado: fuera del lote aplicado / LEGACY

La propuesta de cambio de dominante fue apartada. Sus tres PNG se conservaron en la revisión para no perder cobertura del inventario. Estas respuestas corresponden al brief de la propuesta conservada en autoría y no reincorporan esa propuesta ni validan científicamente el dato heredado.

| Definición | Implemento | Resultado | Original |
|---|---|---|---|
| `good_morning_seated` | `barbell` | PASA | `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_buenos_dias_sentado.png` |
| `good_morning_seated` | `safety_bar` | PASA | `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_buenos_dias_sentado_safety_bar.png` |
| `good_morning_seated` | `smith_machine` | PASA | `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_buenos_dias_sentado_smith.png` |

## Registro completo

`artifacts/catalog-lote05-20261004/integration/image_queue.json` conserva los 46 pares, los hashes reales de 35 archivos, todas las QA literales y sus motivos. Para el sentado se identifica la propuesta apartada como fuente. Los registros anteriores A/B permanecen intactos.


# Lote 6: cola de imágenes aprobada

Corte aplicado c67eeb8f… / 521 configuraciones. **32 entradas aprobadas: 10 FALLA y 22 SIN_IMAGEN**; tres PNG PASA quedan fuera. No se generaron imágenes ni se relajó QA. Las dos walking retiradas quedan fuera. Los siete briefs parentales no cambian.

[42 briefs completos](LOTE_06_BRIEFS_VISUALES.txt) · [Informe del lote](LOTE_06_UNILATERALES.md).

### `forward_lunge__barbell`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `forward_lunge__smith_machine`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `forward_lunge__dumbbells`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `forward_lunge__kettlebell`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `forward_lunge__cable`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `glutes_step_up_gluteo__default`

**FALLA** — Mostrar dos mancuernas, una agarrada visiblemente en cada mano con brazos largos; situar el pie libre apenas separado del suelo en el primer tercio. Conservar pie elevado entero, tibia próxima a vertical y flexión de cadera.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_glutes_step_up_gluteo_batch8.png`. SHA-256: `aa1e7275430492ea559387850eac3fc1acc50780007508e39d5e681d1cb579d6`.

### `glutes_zancada_cruzada__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_sentadilla_bulgara_jefferson__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_sentadilla_bulgara_somersault__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_sentadilla_cosaca__default`

**FALLA** — Reponer el fotograma con pierna anatómica izquierda doblada y derecha extendida al costado, conservando la planta izquierda y el talón derecho sobre el suelo y los dedos derechos arriba. No cambiar QA para acomodar este PNG.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_quads_sentadilla_cosaca_batch8.png`. SHA-256: `9c27be0cf98a2f56652806b8aa44d2853c7c68d75a7adbbac86a1af1fe17df87`.

### `quads_sentadilla_pistola_asistida_trx__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_step_up_cajon_frontal__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_step_up_cajon_zercher__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_zancada_caminando_frontal_barra_recta__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_zancada_caminando_zercher_barra_recta__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_zancada_frontal_zercher__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_zancada_inversa_frontal__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_zancada_inversa_maquina_hack__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_zancada_inversa_maquina_v_squat__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `quads_zancada_inversa_zercher__default`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `reverse_lunge__barbell`

**FALLA** — Mostrar el fondo de la zancada con los dedos posteriores plantados de forma inequívoca sobre el mismo suelo del pie delantero, talón posterior elevado y rodilla posterior cerca del piso sin apoyarla. Conservar la barra en espalda alta y ambos agarres.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_reverse_lunge_barbell_batch9.png`. SHA-256: `aefcb830634d7a49711f576303d112d8c59866ec77b51a3aab6e7bd8f7327983`.

### `reverse_lunge__smith_machine`

**FALLA** — Bajar al fondo y apoyar de forma visible los dedos posteriores sobre el suelo dentro de la Smith, con talón posterior elevado y rodilla trasera cercana al piso sin contacto. Mantener barra sobre espalda alta, dos carros/guías reales y ambos agarres.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_reverse_lunge_smith_machine_batch9.png`. SHA-256: `6de0e94c718e5d95cb7a17c857ee1fc369cb9744917770a8a590a0f238bf1565`.

### `reverse_lunge__dumbbells`

**FALLA** — Mantener las dos mancuernas laterales con ambos agarres visibles y representar el fondo con antepié posterior inequívocamente plantado, talón posterior elevado y rodilla retrasada cerca del piso.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_reverse_lunge_dumbbells_batch9.png`. SHA-256: `73f4bea959df53e19461349735f455d222cbdf74980c40e0797d86d5a9fcc0e1`.

### `reverse_lunge__kettlebell`

**FALLA** — Reponer una sola kettlebell frente al esternón con ambas manos agarrando los lados del asa y codos doblados hacia abajo; mostrar antepié posterior plantado, talón posterior elevado y fondo cerca del suelo.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_reverse_lunge_kettlebell_batch9.png`. SHA-256: `38997b93599436c5d4801c146dded16b501d93065c54226c62785474add4b857`.

### `reverse_lunge__cable`

**FALLA** — Eliminar el escalón frontal. Mostrar el pie delantero entero sobre suelo plano y el fondo con ambas rodillas flexionadas, dedos posteriores plantados y talón posterior elevado. Mantener una asa junto a cadera y polea baja delante.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_reverse_lunge_cable_batch9.png`. SHA-256: `f9163208fe6a061fdbc2eec5cd9ae2c21d7fc7af32fc7b49d3eaa2597afdf105`.

### `reverse_lunge__bodyweight`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `step_up__dumbbells`

**FALLA** — Cambiar el encuadre para que se vea cada mano agarrando su mancuerna de forma inequívoca, con dos brazos largos y cargas fuera de los muslos/bordes. Conservar pie entero en cajón, rodilla flexionada y pie libre detrás en el aire.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_step_up_dumbbells_batch10.png`. SHA-256: `10ef6f448dc8c38bd1e6e7caf8de0462fdf1acea118dfcbe917ef9e84063d3e8`.

### `step_up__kettlebell`

**FALLA** — Reponer una sola kettlebell delante del esternón con ambas palmas rodeando y sosteniendo visiblemente su cuerpo redondo, codos flexionados y asa libre del cajón. Conservar la postura de Step-Up y el pie libre suspendido.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_step_up_kettlebell_batch10.png`. SHA-256: `0114375d4b5f75d54919b6a530540baa2e89070aec9fa80844f7aa017f8ffe72`.

### `step_up__cable`

**FALLA** — Mostrar dos poleas bajas próximas al cajón y dos cables tensos por fuera de las piernas; cada mano debe agarrar su propia asa con brazo largo. Evitar contacto de cables con el borde. Conservar el pie elevado entero y el pie libre suspendido.

Original: `C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\res\drawable-nodpi\exercise_step_up_cable_batch10.png`. SHA-256: `a65a72e7efe1e0b1659032691b5743ed2046323a991e8b02a457c0aa172bfe77`.

### `walking_lunge__barbell`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `walking_lunge__dumbbells`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.

### `walking_lunge__kettlebell`

**MISSING_IMAGE** — Crear el PNG de este par a partir del brief CURATED literal; no existe imagen mapeada.
