PROG · Calibrador en los programas: reglas fieles y APRE (CAL5 + CAL6)

Hecho en `C:\kw\u13`, rama `sv2/programas` (nacida de `feat/screens-v2` @ `373729ae0`, con CAL1 y CAL2). Compila producción y pruebas unitarias; el resultado de cada corrida está en la sección «Pruebas». Las reglas salen de RES1 (`docs/screens-v2/audits/RES1_calibrador_fuentes.md`) y de INV1 (`INV1_amrap_programas.md`); lo que esas fuentes no dan, o marcan como secundario [S] o no verificado [NV], está en «Lo que no se pudo verificar». No se tocó `C:\kw\u11`, `C:\kw\u12`, la interfaz en vivo ni Room.

## Commits (diez, en español, rama `sv2/programas`)

| Commit | Qué cambia |
|---|---|
| `57c63d08d` | Motor común: reglas `RepsOverStandardTm`, `StagedVolume`, `ApreXrm`; `CycleIncrement` con reinicio; `AmrapHit` ampliado; propuestas desde calibradores y desde APRE; arreglo de `collectRepMaxHits` (J&T); motor de autor con evidencia de cierre; funciones puras `Wendler531Progression`, `JuggernautProgression`, `GzclpProgression`, `Apre`; `amrapAsRecord`/`amrapAsCalibrator`; `Cal5TestSupport` y pruebas del motor. |
| `2658a74ad` | 5/3/1 BBB/FSL. |
| `9e55f2ebd` | nSuns 5/3/1 LP. |
| `78b593db4` | Juggernaut 2.0. |
| `3549eb3da` | GZCLP. |
| `146cdc7c7` | Cube, Lilliebridge, The Rippler (AMRAP de registro) y J&T 2.0 (fijado por pruebas). |
| `ee37e6ac9` | Plantillas KPKN `power-12-3`, `power-16-4`, `power-20-5`. |
| `0e64a0c81` | Test de 1RM. |
| `88e5a4f3e` | API pura de vista previa `AmrapProgramPreview`. |
| `59757a1fd` | APRE 3/6/10 (CAL6): protocolo, ficha, portada, pruebas de catálogo y de versiones. |

Más el commit de este informe. Los mensajes de los commits 1, 2, 3, 4, 6 y 10 se reescribieron antes de entregar (sin tocar el contenido de ningún archivo; el árbol final es idéntico) para que la fuente de cada regla coincida con lo que dicen RES1 e INV1.

## Dos carriles de progresión (para entender lo que sigue)

- **Método de autor** (`AuthoredProgressionEngine`): lo que el método original hace siempre al cerrar un ciclo o entrar en una ola. No depende de OFF/PROPONER/AUTO y deja un aviso.
- **Propuestas de rendimiento** (`ProgramAutoregulationEngine`): cambios de TM propuestos desde un AMRAP o un calibrador. Respetan OFF (no propone nada), PROPONER (deja una propuesta pendiente) y AUTO (la aplica).

CAL5 mueve cada programa al carril que le toca según su fuente. Las recetas son un snapshot (`Program.sourceRecipe`): los programas ya activados conservan su receta y su comportamiento anteriores; solo los programas nuevos nacen con las recetas de abajo (por eso suben `contentVersion`).

## Qué cambia en cada programa (antes, después, fuente)

### 5/3/1 BBB y FSL (receta `wendler-531-bbb`/`-fsl`, versión 3)

- Antes: `CycleIncrement(2,5; 5)`. Un AMRAP corto en la serie «+» proponía bajar el TM un 2,5 % por el carril de rendimiento (INV1 hallazgo 3: se aplicaban a la vez el −2,5 % y la congelación).
- Después: `CycleIncrement(2,5; 5; resetAfterFrozenCycles = 2; resetPercent = 10)`. La serie «+» solo registra repeticiones, récord y e1RM (`e1RM = peso × (1 + 0,0333 × reps)`). Al cerrar el ciclo el TM sube 2,5 kg (banca y press) o 5 kg (sentadilla y peso muerto) si se cumplieron las repeticiones mínimas; si no, se congela sin bajarlo; tras dos ciclos congelados seguidos (cuenta 2, 4, 6… calculada desde los registros, sin estado nuevo) el TM baja un 10 %. El aviso del cambio de ciclo lo dice.
- Fuente: RES1 §3 y §12.6 ([S]: thefitness.wiki, Lift Vault, Boostcamp; sin verificar en los libros de Wendler). Que la serie «+» no mueva el TM, el salto de +5/+10 lb por ciclo y el reinicio de −10 % son de RES1; reiniciar tras DOS ciclos congelados y la forma de contarlos son decisión de producto (CALIBRADOR_V1/CAL2), RES1 no los da.
- Pruebas: `Cal5Wendler531Test` (8).

### nSuns 5/3/1 LP (4 días, versión 3)

- Antes: el primer día era banca de volumen (T1 de 9 series, sin AMRAP) con el press militar como T2 de 8 series sin serie 1+: el TM del press militar nunca progresaba.
- Después: el primer día tiene el press militar pesado como T1 (rampa 5/3/1+ con la 1+ al 95 %) y la banca de volumen de 9 series como complementario. La serie 1+ al 95 % de los cuatro levantamientos pesados mueve el TM con la tabla `AmrapDrivenTm`: 0-1 repeticiones sin subida; 2-3, +2,5 kg; 4-5, +5 kg; 6 o más, +7,5 kg; sin bajadas. Solo cuenta desde el 85 % del TM (`AMRAP_TM_MIN_PERCENT`) y si se levantó al menos el 97,5 % de la carga prescrita (`LIGHTER_LOAD_RATIO`). Va por el carril de rendimiento: respeta OFF/PROPONER/AUTO. Las exenciones de composición se ajustan a la nueva distribución (C1, series de 9, por slot).
- Fuente: RES1 §4 (tabla de thefitness.wiki, [S]; el post original de nSuns no se pudo abrir, [NV]). RES1 §4 da solo la tabla sobre la 1+ al 95 %; qué levantamiento es T1 en cada día sigue el diseño de la hoja de 4 días y no está verificado en la hoja original.
- **Decisión pendiente**: la tabla conservada (2-3: +2,5 kg; 4-5: +5 kg; 6 o más: +7,5 kg) coincide con la implementación de Liftosaur, pero es el extremo ALTO de los rangos de RES1 (2-3: +2,5 kg; 4-5: +2,5 a +5 kg; más de 5: +5 a +7,5 kg). No es la lectura más conservadora; la conservadora sería +2,5 / +2,5 / +5 kg. No lo cambié porque ya estaba así antes de CAL5 y cambiarlo altera programas ya en curso. La tabla tampoco distingue tren superior e inferior (INV1, hueco 4); RES1 §4 tampoco la distingue y no se tocó.
- Pruebas: `Cal5NSunsTest` (5), más `ProtocolRecipeFidelityTest` (banca de 9 series como T2 del día 1, OHP como T1).

### Juggernaut 2.0 (16 semanas, versión 3)

- Antes: `CycleIncrement(2,5; 5; scope = BLOCK)`: el TM subía 5 kg (sentadilla y peso muerto) o 2,5 kg (banca y press) al entrar en cada ola, sin mirar lo rendido; el AMRAP de realización además podía proponer otro cambio. La descripción (`JSS:180`) decía solo «AMRAP de realización que ajusta TM».
- Después: `RepsOverStandardTm(lowerKgPerRep = 2,5; upperKgPerRep = 1,25; maxExtraReps = 10)`: TM nuevo = TM + (repeticiones del AMRAP de realización − estándar de la ola) × 2,5 kg (sentadilla y peso muerto) o × 1,25 kg (banca y press), con un tope de 10 repeticiones extra. Sin superar el estándar el TM queda igual y el aviso de cambio de ola lo dice («TM quizá alto»). Se aplica al entrar en la ola siguiente (`applyAuthoredBlockEntry`), con el AMRAP de realización de la ola que se cierra; el final de la ola 4 (3s) no sube nada. Ya no mueve el TM por el carril de rendimiento (`amrapMovesTm = false`). Descripción y ficha editorial corregidas.
- Fuente: RES1 §5.2 y §12.6. Toda la fórmula (incrementos, estándares 10/8/5/3 y tope de 10 repeticiones extra) es de fuentes secundarias [S] (Liftosaur, Lift Vault, Momentum Training) y [NV] en el libro *Juggernaut Method 2.0*; el tope además no está confirmado ni en esas fuentes.
- Pruebas: `Cal5JuggernautTest` (9) y el test de cierre de ola reescrito en `ProgramProgressCycleCloseTest` con registros reales de AMRAP de realización.

### GZCLP (4 semanas que se repiten, versión 3)

- Antes: `CycleIncrement(2,5; 5)`: el T1 y el T2 cambiaban de volumen y de porcentaje según la semana de la receta y el TM subía el salto fijo por ciclo, hubiera completado el volumen o no; un AMRAP corto podía bajarlo.
- Después: `StagedVolume(2,5; 5; t1AmrapCap = 10; t3RaiseReps = 25)`. La receta trae la etapa 1: T1 5×3+ al 85 % del TM (última serie al máximo), T2 3×10 al 65 %. Si en el ciclo no se completa el volumen base de un levantamiento, el ciclo siguiente pasa a la etapa 2 (T1 6×2+, T2 3×8) y luego a la 3 (T1 10×1+, T2 3×6) con la MISMA carga y sin subir su TM; si se completa, el TM sube 2,5 kg (banca y press) o 5 kg. La etapa se guarda como receta efectiva de la semana (`PlanMaterializer.withEffectiveWeekRecipe`) al cerrar el ciclo. La serie AMRAP del T1 no cambia la carga y cuenta hasta 10 repeticiones. Tras la etapa 3 sin completar, el aviso pide probar un 5RM nuevo y reiniciar al 85 % de ese 5RM. Sin registros de un levantamiento, el TM sube como si lo hubiera completado (comportamiento previo conservado).
- Fuente: RES1 §6 (Lefever, *GZCL Applications & Adaptations*, [P]).
- Pruebas: `Cal5GzclpTest` (13).

### J&T 2.0 (`gzcl-jt-2`)

- Antes: `collectRepMaxHits` (`PAE:652-654`) no se disparaba con el RM semanal del T1 cuando venía con porcentaje (una serie AMRAP «1×10+ al 77,5 %»): `RepMaxAutoregulated` nunca actuaba (INV1 hallazgo 5), solo `AMRAP_TM` bajaba.
- Después: se reconoce la serie al máximo de repeticiones con porcentaje (`isMaxRepsAtPercent`) y las series de test de 1RM (`isOneRmTestSet`); el RM semanal del T1 dispara `RepMaxAutoregulated`. La receta no cambia de contenido (versión 2). El arreglo está en el commit del motor común y se fija con pruebas en el commit de Cube/Lilliebridge/Rippler.
- Fuente: INV1 (`PAE:652-654`); RES1 no recoge J&T. La regla de J&T ya existía; solo se corrigió que se disparara.
- Pruebas: `Cal5JackedTanAndRecordOnlyTest` (6, comparte clase con Cube/Lilliebridge/Rippler).

### Cube, Lilliebridge y The Rippler (versiones 3, 3 y 2)

- Antes: su AMRAP era una serie al máximo sin propósito declarado ni regla de carga (INV1: `None`, no mueve el TM, H-05): un AMRAP de efecto vacío.
- Después: sus AMRAP son AMRAP de registro explícito (`amrapAsRecord`, `SetPurpose.AMRAP` sin efecto): quedan en el historial y en los récords y no cambian la carga. Las descripciones y fichas editoriales lo dicen.
- Fuente: ni RES1 ni INV1 documentan una regla de carga para el AMRAP de estos tres métodos (la hoja del Cube no es pública); se eligió lo más conservador: registrar.

### Plantillas KPKN `power-12-3`, `power-16-4`, `power-20-5` (versión 3)

- Antes: AMRAP genérico al inicio de cada bloque; las semanas 11 (`power-16-4`) y 16 (`power-20-5`), primera del pico, llevaban la bandera de AMRAP pero el pico sustituye el T1 por top set y back-offs: bandera muerta.
- Después: el AMRAP del inicio de bloque es un CALIBRADOR con alcance `NEXT_SESSIONS` (`amrapAsCalibrator`): su resultado entra por el canal de propuestas del programa (OFF/PROPONER/AUTO) y afecta a las sesiones siguientes. Se retiran las banderas muertas del pico.
- Fuente: diseño KPKN (plantillas propias), CALIBRADOR_V1 §5. Pruebas: `Cal5PowerCalibratorTest` (8).

### Test de 1RM

- Antes: pedía sentadilla, banca y peso muerto aunque el programa no usara alguno; sin press militar; sin aprovechar lo ya registrado.
- Después: `OneRmTestPlan.requiredLifts(recipe)` da los levantamientos del programa (Smolov solo pide sentadilla); `OneRmResolution.overhead1RM` admite el press militar; `ProgramProgressEngine.resolvePendingOneRmTest` valida solo los levantamientos requeridos y fusiona las metas; el diálogo precarga el 1RM de las series con propósito «Test» ya registradas (`OneRmTestPlan.prefill`) y la persona confirma antes de guardar. La interfaz tocada se limita al diálogo del test de 1RM de la ficha del programa (`ProgramDetailScreen`, `ProgramDetailViewModel`, `ProgramOneRmSheet`).
- Pruebas: `Cal5OneRmTestTest` (6).

### API pura de vista previa

`AmrapProgramPreview.preview(recipe: TrainingPlanRecipe?, mode: AutoregulationMode, profile: PowerliftingProfile?, hit: AmrapHit, frozenCyclesBefore: Int = 0): AmrapProgramEffect`, en `domain/training/`. Devuelve `kind` (`TM_CHANGE`, `CYCLE_CLOSE`, `BLOCK_CLOSE`, `NEXT_SET_LOAD`, `NEXT_WEEK_XRM`, `RECORD_ONLY`, `NO_CHANGE`), `timing` (`SAME_SESSION`, `NEXT_WEEK`, `END_OF_CYCLE`, `END_OF_BLOCK`, `NEXT_SESSIONS`, `NONE`), `gate` (`METHOD`, `OFF`, `PROPOSE`, `AUTO`), `headline` con coma decimal («nSuns: TM +5 kg la semana próxima»), `detail`, `warning`, `liftSlot` y `tmDeltaKg`. Reutiliza las funciones de los motores, sin duplicar reglas. Pruebas: `AmrapProgramPreviewTest` (13). CAL3 define `LiveAmrapProgramPreview`; la firma de arriba es la mía: adaptar al integrar.

### APRE 3/6/10 de Mann (protocolo nuevo `apre-mann`, CAL6)

- Protocolo de 12 semanas y 3 días (sentadilla, banca, peso muerto), tres fases de cuatro semanas: APRE 10 (10RM = 75 % del 1RM), APRE 6 (6RM = 83 %) y APRE 3 (3RM = 92 %). TM de la receta = 92 % del 1RM, así que el XRM de cada fase es el 81,5 %, el 90,2 % y el 100 % del TM. Cada levantamiento hace cuatro series: 50 % y 75 % del XRM y dos series al máximo con el XRM (series 3 y 4: AMRAP con mínimo = XRM).
- Tabla de Mann (`Apre.band`), en % de la carga y redondeada al material (`LoadSuggestionEngine.roundLoad`, salto por defecto 2,5 kg). Cada banda vale el punto medio de su rango en libras sobre el ejemplo de 300 lb: bajada grande −2,5 %, bajada pequeña −0,83 %, subida pequeña +2,5 %, subida grande +4,17 %:

  | Versión | Bajada grande | Bajada pequeña | Mantener | Subida pequeña | Subida grande |
  |---|---|---|---|---|---|
  | APRE 10 | 0-6 reps | 7-8 | 9-11 | 12-16 | 17 o más |
  | APRE 6 | 0-2 | 3-4 | 5-7 | 8-12 | 13 o más |
  | APRE 3 | 0-2 | no existe | 3-4 | 5-6 | 7 o más |

- Serie 3 → carga de la serie 4 de la misma sesión (`Apre.set4LoadKg`); serie 4 → XRM de la semana siguiente (`Apre.nextWeekXrmKg`, sobre la carga de la serie 4). Las repeticiones con mala técnica no cuentan (`Apre.validReps`). Con «mantener» la carga no se toca. La regla `ApreXrm` propone el XRM nuevo como cambio de TM por el carril de rendimiento (respeta OFF/PROPONER/AUTO) y la semana siguiente parte de él; solo la última AMRAP del ejercicio propone y un AMRAP fuera de APRE se ignora.
- «Bloque APRE» para el editor (CAL4): `Apre.block(oneRmKg, variant, incrementKg): List<ApreBlockSet>`; con un 1RM no positivo devuelve vacío (no se inventan cargas). Serie 1: 50 % del XRM; serie 2: 75 %; serie 3: XRM, `CALIBRATOR`/`SESSION` (mínimo = XRM); serie 4: XRM, `AMRAP`.
- Catálogo: ficha editorial (rango 442), portada, registro en `ProtocolLibrary`, exención C5 documentada (Mann no programa descargas) y un press inclinado ligero en el día de peso muerto para cumplir la política W6 (banca en dos días en powerlifting de 3 días o más). El catálogo pasa a 86 fichas (48 listadas, 23 de terceros).
- Fuente: RES1 §2 (Mann, *Understanding APRE*, Training & Conditioning 2017; Mann et al., J Strength Cond Res 2010), primaria [P].
- Pruebas: `Cal6AprePrescriptionTest` (15): casos límite de las tres tablas, redondeo al material, mala técnica, XRM desde el 1RM, bloque de cuatro series y semana siguiente con el XRM propuesto.

## Pruebas

Comandos (siempre por la ranura, en segundo plano, con log en `C:/kw/logs/prog-<n>.log`):

```
bash /c/kw/tools/gradle_slot.sh /c/kw/u13/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" :app:compileBaseDebugKotlin
bash /c/kw/tools/gradle_slot.sh /c/kw/u13/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" :app:testBaseDebugUnitTest <filtros>
```

Los filtros (`--tests`) están en `C:/kw/logs/prog-test-filters.txt`: `*Cal5*`, `*Cal6*`, `*AmrapProgramPreviewTest` y las clases existentes que toco o que fijan el catálogo (motores de progresión y autorregulación, cierre de ciclo, contrato de recetas, protocolos y su auditoría, fidelidad, exenciones, JSON de recetas, catálogo editorial, biblioteca, portadas, nombres, planes, hoja del 1RM y ficha del programa). No se corrió la batería completa.

| Corrida | Tarea | Resultado |
|---|---|---|
| `prog-1.log` | `:app:compileBaseDebugKotlin` | BUILD SUCCESSFUL en 7 min 51 s |
| `prog-2.log` | `:app:testBaseDebugUnitTest` (filtros) | 708 pruebas, 22 fallos: pruebas de catálogo fijadas (nombres, exenciones, estructura, versiones, número de entradas, comportamiento antiguo de AMRAP) que codificaban las reglas anteriores. Corregidas expresamente con la regla nueva y su fuente. |
| `prog-3.log` | ídem | 711 pruebas, 3 fallos: el registro de consumidores del motor de autor (corregido) y 2 de `ProgramProtocolEngineTest` (ajenos, abajo). |
| `prog-4.log` | ídem, solo `AuthoredProgressionEngineTest`, `ProgramProtocolEngineTest` y `ProgressionConsumerCoverageTest` | 39 pruebas, 2 fallos: solo los 2 de `ProgramProtocolEngineTest` (ajenos, abajo); `AuthoredProgressionEngineTest` y `ProgressionConsumerCoverageTest` pasan completas. Gradle termina en BUILD FAILED únicamente por esos 2 (7 min 2 s). |

Fallos que no son de este trabajo: `ProgramProtocolEngineTest.nativeSbd_hasExplicitCompetitionRecipes_andStartDay` (línea 248) y `ProgramProtocolEngineTest.native_protocol_hydrates_recorded_program_goals_without_inventing_missing_rm` (línea 268). Comparan `calculateSuggestedLoad` con `1RM × %`; desde CAL1 (`af6e6e546`, `Calculations.kt:257`) esa función usa `referenceTrainingMax`. No toco `calculateSuggestedLoad` ni `kpkn-native-sbd-4`; no los ejecuté sobre la base limpia porque solo se permiten las corridas dirigidas, así que la atribución a CAL1 es por lectura del código, no por ejecución. Quedan sin tocar.

Pruebas nuevas: 83 (`Cal5Wendler531Test` 8, `Cal5NSunsTest` 5, `Cal5JuggernautTest` 9, `Cal5GzclpTest` 13, `Cal5JackedTanAndRecordOnlyTest` 6, `Cal5PowerCalibratorTest` 8, `Cal5OneRmTestTest` 6, `AmrapProgramPreviewTest` 13, `Cal6AprePrescriptionTest` 15), más las actualizaciones de las pruebas fijadas. Cada una comprueba la regla de su fuente con números y que se respeten OFF, PROPONER y AUTO donde la regla pasa por el carril de rendimiento. Las corridas se hicieron sobre el árbol completo antes de dividirlo en commits; el árbol final es idéntico (comprobado por el hash del árbol), pero cada commit intermedio por separado no se compiló.

## Lo que no se pudo verificar en la fuente

- **5/3/1**: toda la regla es [S] (RES1 §3); no se leyó en los libros de Wendler. Reiniciar tras dos ciclos congelados seguidos es decisión de producto.
- **nSuns**: la tabla es [S] y el post original [NV]; los rangos de 4-5 y de más de 5 repeticiones son rangos de kilos y se eligió su extremo alto (ver «Decisión pendiente»); qué levantamiento es T1 cada día no consta en RES1.
- **Juggernaut**: incrementos, estándares y tope de 10 repeticiones extra son [S] y [NV] en el libro; el tope ni siquiera está confirmado en las fuentes secundarias.
- **GZCLP**: la subida del T3 al llegar a 25 repeticiones es solo un aviso, no cambia carga sola; el reinicio tras la etapa 3 avisa de probar un 5RM pero no fija el TM de ese 5RM automáticamente; la «carga algo mayor» (no más de 9 kg) del T2 al volver a 3×10 no está modelada (se conserva la misma carga).
- **Cube, Lilliebridge y Rippler**: ni RES1 ni INV1 recogen regla de carga para su AMRAP (hoja del Cube no pública); queda como registro.
- **APRE (Mann)**: la tabla es [P], pero está en libras y RES1 §2.2 indica que la fuente no da el equivalente en %; la conversión usa los puntos medios sobre el ejemplo de 300 lb (decisión de producto). Los casos que la fuente no define (APRE 10 con menos de 4 repeticiones y APRE 3 con 0) toman la bajada más grande. Que cada fase dure cuatro semanas es decisión de KPKN: Mann periodiza las fases pero no fija su duración. No se pudo confirmar qué protocolo APRE usó el estudio de 2010 (RES1 §2.4).
- **Plantillas `power-*`**: son diseño KPKN; no hay fuente externa que verificar.

## Riesgos de integración

1. **`ProgramAutoregulationEngine`: semántica de `amrapMovesTm`/`amrapTmChange` cambiada.** `amrapMovesTm` es verdadero para `AmrapDrivenTm`, `RepTargetDrivenTm`, `ApreXrm` y las reglas o ganchos genéricos restantes, y falso para `CycleIncrement` (5/3/1), `RepsOverStandardTm` (Juggernaut), `StagedVolume` (GZCLP) y `RepMaxAutoregulated` (J&T mide con el e1RM de la serie al máximo). `amrapTmChange`: un AMRAP corto ya no baja el TM con `AmrapDrivenTm`, `CycleIncrement`, `RepsOverStandardTm`, `StagedVolume`, `ApreXrm` ni `RepMaxAutoregulated`; solo lo baja `RepTargetDrivenTm` (con su propia regla de repeticiones faltantes) y el resto de reglas genéricas (−2,5 %). Cualquier código de CAL3 que lea esas funciones para decir «este AMRAP baja/sube tu TM» debe usar `AmrapProgramPreview` o las mismas funciones, no una copia de la semántica antigua.
2. **`AmrapHit` ampliado** con `weightKg`, `purpose`, `calibrationScope`, `declaredRir`, `reachedFailure`, `partialReps`, `configurationId` e `isLastAmrapOfExercise`, todos con valor por defecto al final. CAL3 y CAL5 tocan `ProgramAutoregulationEngine.kt`: es esperable un conflicto de fusión en esa clase y en `collectAmrapHits`.
3. **Canal de calibradores de CAL5 frente a `calibration_decisions` de CAL3.** Los calibradores `NEXT_SESSIONS` de las plantillas `power-*` producen una propuesta `ADJUST_TM` en el carril de rendimiento (`calibratorProposal`/`calibratorDecision`, con `CalibrationEngine`). CAL3 persiste además decisiones en su tabla y las muestra en la tarjeta en vivo: hay que evitar que la misma serie genere una propuesta del programa y una decisión de CAL3 que se acepten o apliquen dos veces (clave por serie/ejercicio/semana).
4. **`AmrapProgramPreview` frente a `LiveAmrapProgramPreview` de CAL3.** Dos firmas distintas para la misma idea; adaptar CAL3 a la mía o poner un adaptador. La mía es pura y no usa Room.
5. **Juggernaut y 5/3/1 son método de autor**: se aplican siempre, aunque el programa esté en OFF. `AmrapProgramPreview` los marca con `gate = METHOD` y la tarjeta en vivo debe decirlo así y no ofrecer «Aceptar/Rechazar». Juggernaut sin AMRAP de realización registrado no cambia el TM y no avisa (no hay dato que comentar).
6. **GZCLP**: la etapa se infiere del volumen base planificado de los ejercicios del ciclo que se cierra y se guarda como receta efectiva de la semana. Si la persona editó a mano el volumen del T1/T2 de ese ciclo, la etapa inferida puede no ser la esperada.
7. **JSON de recetas**: `CycleIncrement` ahora serializa `resetAfterFrozenCycles` y `resetPercent` (con valor por defecto al decodificar, por lo que las recetas guardadas siguen leyéndose); está fijado en `TrainingPlanRecipeJsonCompatTest`. Una versión anterior de la aplicación que lea una receta nueva dependerá de `ignoreUnknownKeys`.
8. **Programas ya activados**: conservan receta y comportamiento anteriores (snapshot); solo cambian los programas nuevos. Las notas del relator (`PlanObserver`) no se han adaptado a las reglas nuevas (`RepsOverStandardTm`, `StagedVolume`, `ApreXrm`) y muestran sus líneas genéricas.
9. **CAL4 y la tabla de Mann.** `Apre.block` pone la serie 3 como `CALIBRATOR`/`SESSION`. La receta de `apre-mann` usa AMRAP en las series 3 y 4 precisamente para que la carga de la serie 4 salga de la tabla de Mann (`Apre.set4LoadKg`) y no del factor de capacidad de CAL2. Si el editor deja un calibrador en la serie 3 y CAL3 lo aplica, la serie 4 tendría dos reglas distintas; definir en CAL4 si en un bloque APRE manda la tabla de Mann o CAL2.
10. **Pruebas ajenas**: los dos fallos de `ProgramProtocolEngineTest` vienen de CAL1 (por lectura del código) y hay que resolverlos en esa pista; no bloquean este trabajo.

## Pendiente

- Decidir la tabla de nSuns (extremo alto de los rangos, como está, o el conservador).
- Integración con CAL3 (puntos 3 y 4) y decisión de CAL4 (punto 9).
- Adaptar las notas del relator a las reglas nuevas.
- Los dos fallos de `ProgramProtocolEngineTest` (CAL1).
