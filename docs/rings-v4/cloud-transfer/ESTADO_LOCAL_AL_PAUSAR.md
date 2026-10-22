> PAUSADO POR EL USUARIO · 22-oct-2026: detener procesos propios por consumo de RAM y no continuar hasta aviso. Canceladas las cinco corridas de este relevo (3.2c-2, 3.5, 2.4, contraste sb, Android 3.3/3.4), retiradas sus entradas de cola y candados. No hay validación completa. Antes de cancelar, 3.2c-2 mostró fallo de convergencia factor 0,75 (mediana 0,2382 > 0,15); no modificar LEARNER_* para aprobarlo. 3.5 y 2.4 conservan WIP sin commit. No relanzar nada automáticamente.

> ACTUALIZACIÓN DEL RELEVO CODEX · 22-oct-2026 (manda sobre las tablas históricas inferiores)
> - 3.3 y 3.4 fusionados mediante --no-ff: c51fc4615 y b02467685. Correcciones de compilación 8a0846a0b/bd8331746. Android pendiente: r4-33-34-android-codex-1.log.
> - Contraste limpio master @8ac57c9d4 relanzado al no existir proceso ni entrada antigua: r4-master-contraste-codex-20261022.log.
> - 3.2c-2 sigue en r4l, sin commit; arnés nuevo r4-32c2-codex-1.log. Se retiró solo su candado huérfano de ayer tras comprobar la ausencia del propietario 1472760.
> - 3.5 se está implementando en r4e, rama codex/rings-v4-model-skill desde b02467685; todavía sin integrar ni validar. β=0,20, ocho pares, guardia por parámetro sin rearmado automático, según brief cerrado.
> - 2.4 conserva su WIP en v3c. Master y el árbol principal permanecen intactos; VT no ha sido avisada; no hay validación de teléfono ni permiso de merge a master.
# Rings v4 · tablero de estado

Lo mantiene el orquestador. Última actualización: **2026-10-21 19:35**. Detalle de decisiones: `DECISIONES_AUTORRESPONDIDAS.md`. Informes por paquete: `reports/<ID>.md`. Convivencia de agentes: `BRIEF_PARALELO.md`.

## AVISO · Codex y Antigravity sin cuota
- **Codex** (21-oct 13:38): sin cuota hasta el **27 de octubre a las 12:38**.
- **Antigravity / Gemini** (21-oct 15:35): «Individual quota reached», cuota de toda la cuenta agotada por cinco agentes en paralelo; renovación estimada entre las 17:36 y las 18:10 (el plazo se mueve). Los cinco agentes Gemini murieron (2.5b, 3.1, 1.9v2, 1.10v2, 3.2b) y **al morir un agente se llevan por delante sus tareas de fondo** (la corrida Android de 1.9v2 en cola y la corrida completa del arnés de 3.1 se perdieron). Su trabajo quedó en cada worktree.
- Mientras tanto cierran con **OpenCode DeepSeek v4.1 Flash (gratis)** los pasos mecánicos de 2.5, 3.1 y 3.2b; el resto espera a Gemini (máx. 2 agentes a la vez cuando vuelva) o a una decisión tuya sobre ejecutores Claude. La máquina está además al límite de RAM (otras sesiones con Android): las colas de Gradle y del arnés se mueven despacio.

## Política de verificación (desde las 14:50)
La cola compartida de Gradle (otras 10 sesiones) lleva horas de espera, así que **el arnés JVM es la compuerta principal** y Android pasa a tandas mías:
- El arnés ya regenera el máster dorado (`-Pr4GoldenUpdate=true`: escribe solo en `android-native/app/build/auge-golden/`) y corre la simulación completa (`-Pr4SimFull=true`). Comprobado en `feat/rings-v4`: el archivo regenerado es **idéntico, byte a byte**, al que produjo Android.
- Los agentes **no lanzan corridas Android** salvo las que ya tenían en cola (3.1: la de Codex `r4-3-1-1.log`; 1.9v2: `r4-1-9-4.log`); terminan con arnés verde + informe y listan las clases que el arnés no cubre.
- Yo reviso el diff, fusiono en `feat/rings-v4` y lanzo UNA tanda Android (`r4-verify.sh`) con todo lo que el arnés no cubre (Robolectric, `screens/*`, `AugeSnapshotCoverageTest`, `AugeWriteMutexTest`, 1.11…). Los paquetes siguientes parten de lo ya fusionado, sin esperar esa tanda.
- Hallazgo de la revisión de 3.1 (14:55): el nuevo códec JSON del caché había perdido `coerceInputValues`, que sí tenía el decodificador original; se devuelve al agente para corregirlo antes de la corrida Android.

## Resumen
- **Plan en alcance: 29 paquetes** (32 del plan menos 3.6 opcional, 4.1 y 4.2 que dependen de tus datos). **Integrados: 22** (1.11, 1.9v2, 2.5 y 3.1 pendientes de la tanda Android; más 3.2b, 3.2a-1 y 2.6 pasada 1). En curso: 2.5 y los carriles de abajo. Pendientes tras ellos: ver «Grafo».
- Aplazado por tu decisión: backtest (BT1). Fuera de esta entrega: AUGE Pro (13 decisiones tuyas pendientes), 3.6, 4.2, sugerencias S1–S14 salvo «Precisión del modelo» (en 3.5).

## Carriles en marcha
| Carril | Paquete | Agente / modelo | Worktree · rama | Estado |
|---|---|---|---|---|
| A | 2.5 anclas como estado + evidencia multiplicativa | Codex (A) + DeepSeek (cierre de B) | `v3c` · `r4/2-5` | **FUSIONADO 17:25 (`4fbe555c5`)**: arnés 73 clases / 383 pruebas / 0 fallos; máster dorado: solo D04 a 6/12/24 h; informe `reports/2.5.md` |
| B | 3.1 caché v4, migración, anillo de observaciones | Codex + Gemini + DeepSeek (cierre) | `sb` · `r4/3-1` | **FUSIONADO 16:55 (`b848c4607`)**: arnés 74 clases / 384 pruebas / 0 fallos; la compilación Android de producción pasó; informe `reports/3.1.md` |
| C | 1.9v2 textos y umbrales | Gemini (murió sin informe) | `u1` · `r4/1-9v2` | **fusionado en `feat/rings-v4` (`0c8a9a06c`)**; informe `reports/1.9.md` escrito por mí; su corrida Android se perdió: va en la tanda consolidada |
| F | 3.2b núcleo EKF + oráculo independiente | DeepSeek cierra (antes Gemini) | `r4l` · `r4/3-2` | **FUSIONADO 17:37 (`8f06e450e`)**: los 16 vectores del oráculo Python pasan a 1e-9; arnés 75 clases / 389 pruebas / 0 fallos; informe `reports/3.2b.md` |
| G | 3.2a-1 lectura continua del motor | DeepSeek v4.1 Flash | `r4e` · `r4/3-2a` | **FUSIONADO 18:21 (`13eba7dd0`)**: máster idéntico; conformidad entero⇄exacto 0 diferencias en 4 301 lecturas musculares y Energía/Columna cruda, 35 de 400 compuestas con máx. 1 punto; informe `reports/3.2a.md`. Hueco anotado: falta `energyShown` (3.2c-1) |
| H | 3.2c-1 modelo de observación real (`SnapshotObservationModel`), `energyShown`, refuerzo del criterio (c) y referencia EMA congelada | DeepSeek | `r4l` · `r4/3-2` | en curso desde 18:25 (arnés en cola) |
| I | 2.6 pasada 1 (D8 avisos ACWR + D3 SNC por sesión dura) | DeepSeek | `v3c` · `r4/2-6` | **FUSIONADO 19:03 (`082ef7384`)**: arnés 80/425/0; máster: 95 líneas / 235 campos, todos de Energía; D8 cambia el aviso en 3 de 200 instantes; tablas en `reports/2.6-tablas.md` |
| J | 2.6 pasada 2 (A = D5 escalas explícitas sin cambios; B = D1+D2 global de Músculos sin coeficiente articular) | DeepSeek | `v3c` · `r4/2-6` | en curso desde 19:05 |
| K | 1.10v2 «sin datos» no es 100 % | Gemini 3.8 Flash High (cuota de vuelta desde ≈18:20) | `r4n` · `r4/1-10v2` | fusionó `feat/rings-v4`, pruebas de integración de dominio en arnés; sin Android hasta la tanda |
| D | 1.11 creador sin Core por defecto | Antigravity · Gemini 3.8 Flash High | `r4s` · `r4/1-11` | **integrado 13:30** (diff de 7 líneas + prueba nueva); su corrida Android murió con el agente: se verificará en la próxima tanda |
| E | DOC1 README de la auditoría y arquitectura (solo documentación) | OpenCode · DeepSeek v4.1 Flash (gratis) | `r4d` · `r4/5-3a` | **integrado 12:49** (README de la auditoría + arquitectura) |
| T | JVM1 arnés JVM para pruebas puras de AUGE (herramienta, fuera del repo) | Codex Sol 6.1 · máximo | `C:\kw\r4-jvm` | **listo 13:28**: 70 clases / 365 pruebas de AUGE, 0 discrepancias con Android; compilación en frío 202 s, caliente 1,5 s, completo 83 s, una clase 17 s; un arnés por worktree (`C:\kw\r4-jvm-<wt>`); no cubre `screens/`, `data/repository/`, Robolectric/Compose/Room ni 3 clases (`AugeEditorCompletedParityTest`, `AugeSnapshotComputerTest`, `BatteryRoundingTest`) |

Cola de lanzamiento (cuando se integre lo anterior): 3.2 (tras 3.1) → 3.3 → 3.4 → 3.5 en el carril del aprendiz; 2.6 y luego 2.4 (tras 2.5) en el carril del motor; 1.10v2 (tras 1.9v2) en el carril de interfaz; 5.1–5.3 al final.

## Grafo de lo que falta
- 2.5 → **2.6** (cambios de forma; regenera el máster dorado) → 2.4 (proyección; mide sobre la forma final).
- 3.1 → **3.2** (aprendiz EKF) → 3.3 (gesto manual) ∥ 3.4 (rendimiento) → **3.5** (habilidad, guardia, tarjeta «Precisión del modelo»).
- 1.9v2 → 1.10v2 (mismo carril: tocan los mismos archivos de Home/readiness).
- Cierre: 5.1 regresión amplia (incluye contraste con `master` de `RingsResultStepTest` y `VideoAnalysisRepositoryTest`) → 5.2 QA en el Flip → 5.3 documentación.

## Integrados en `feat/rings-v4`
Fase 0: 0.1, 0.2, 0.3 · Fase 1: 1.1, 1.2a, 1.2b, 1.3, 1.4, 1.5, 1.5b, 1.6, 1.7, 1.8, 1.12a, 1.12b, 1.12c · M0 (master fusionado) · Fase 2: 2.1, 2.2a, 2.2b1, 2.2b2, 2.3, **2.5 (17:25)** · Fase 3: **3.1 (16:55)** · **1.9v2 (15:15)** · 1.11 (13:30, verificación pendiente) · DOC1 (índice de la auditoría y arquitectura, parte de 5.3).

## Tanda Android
- Tanda 1 (18:36): la **producción compiló entera** (1.9v2, 2.5, 3.1, 3.2b y 3.2a-1) y cayó al compilar las PRUEBAS por 3 errores de tipo en `RingsUiCopyConsistencyTest` (corregidos, `7f0c42871`). Tanda 2 enviada a las 19:04 (`r4-batch-2.log`), en cola; mientras espera solo se fusionan paquetes cuyas pruebas nuevas ya compilan en el arnés (dominio).
- Arnés de integración (punta de `feat/rings-v4` a las 19:03): **81 clases / 428 pruebas / 0 fallos**. Con 3.2c-1 (20:00): 82 clases / 432 / 0; con 2.6 pasada 2 (21:15): 83 clases / 434 / 0.
- **Tanda 2 (corrió 20:45–21:07, `r4-batch-2.log`, árbol sin 2.6 pasada 2): 3 459 pruebas, 48 fallos.** 22 son los heredados de `reports/M0-fallos-previos.txt`. De los 26 restantes: (a) **Rings, arreglo en curso (DeepSeek en `r4v`)**: `AugeWriteMutexTest` ×5 (la aserción del aviso del día se evalúa antes de que el recálculo lo guarde: carrera de orden), `SessionEditorAugeCharacterizationTest` ×4 (re-ancla por D3, solo cns), `RingsUiCopyConsistencyTest` ×1 (esperaba «Algo cansado»; la producción y `RingAdjustMathTest` dicen «Algo cargada»), `RingAdjustTest` ×3 (palabras antiguas de los diales; 1.9v2 las unificó en `RecoveryBands`). (b) **En contraste con `master` limpio** (`r4-master-contraste.log`, worktree `sb` en `8ac57c9d4`): `RingsResultStepTest`, `RingsIntroStepTest`, `VideoAnalysisRepositoryTest`, `LiveExerciseOptionsUiTest` ×2, `LiveOptionsLargeFontTest` ×3, `SetExecutionCardCompactTest` ×2, `CatalogDetailContentTest`, `ExerciseCatalogImageCoverageTest` ×2 (código ajeno a Rings o textos de otras sesiones; `SetExecutionCard.kt` sí lo tocó 1.12 con un parámetro opcional).
- Tanda 3 (`r4-batch-3.log`, `r4n`, paquete 1.10v2 con 3 arreglos del revisor DeepSeek) en cola. La siguiente tanda integrada (feat + 2.6 pasada 2 + 3.2c-2 + arreglos de pruebas + 1.10v2) se enviará cuando estén todos fusionados.

## Verificaciones
- `r4-rev-23m` (árbol con 2.3): 513 clases / 4 833 pruebas / 2 fallos ajenos a AUGE (`VideoAnalysisRepositoryTest` solo falla en la corrida conjunta; `RingsResultStepTest` pendiente de contrastar con `master`).
