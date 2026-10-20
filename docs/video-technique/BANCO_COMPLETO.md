# Banco completo: los 1.000 vídeos en todas las pruebas (VT11, D41)

Orden del usuario (20-oct): «Tenemos un banco de 1000 vídeos. Te ordeno a que los uses todos para las pruebas». Exige además que la
función sea USABLE y con la fiabilidad de las apps del mercado. El listón por métrica sale de `MERCADO_FIABILIDAD.md`, la
investigación del 20-oct.

## Qué hay
- **1.076 clips etiquetados** en `C:\kw\vt-bench\labels\<id>.json`: el banco del usuario
  (`artifacts\exercise-video-corpus-20261014\01_videos\`) y 76 públicos con licencia (`g*`, `r*` y `x*`, en `C:\kw\vt-bench\dl\`).
  - El banco del usuario: Kaggle 418, UCF101 480, wger 77 y YMove 25.
  - Cada etiqueta trae `usable`, `unusableReasons`, `suggestedProfile`, `view`, `people`, `reps` (con `acceptableRange`),
    `repTimesS` y `activeWindowS`.
- **Útiles: 513.** Kaggle 214, UCF 202, wger 30, YMove 18 y públicos 49.
  - Por vista: de lado 180, de frente 126, oblicua 108, de espaldas 55 y tumbado 44.
  - Por perfil: flexiones 105, banca 86, dominada y jalón 68, sentadilla 65, curl 42 y el resto por debajo de 25.
- **No útiles: 563.**
  - Ejercicio que v1 no analiza: 285.
  - Cámara en movimiento: 152.
  - Persona cortada: 32.
  - Sin ningún ciclo completo: 31.
  - Zancada caminando: 29.
  - Cortes de edición: unos 14.
  - Varias personas: 13.
  - No es el ejercicio indicado: 10.
- **Trazas actuales:** 513 (97 compatibles y 416 externas), extraídas a 12,5 o 15 Hz por el fallo que corrige M1. No sirven para la
  medición final: tras M1 se vuelve a extraer TODO.

## Diseño: todos los vídeos cuentan, cada uno para lo que sirve
| Grupo | Clips | Qué se mide | Fallo grave |
|---|---|---|---|
| G1 · útiles | 513 | serie exacta, dentro del rango y error; reparto ALTA/MEDIA/BAJA; precisión de cada nivel; por perfil, vista, fuente y fps | un número ofrecido (ALTA o MEDIA) que está mal |
| G2 · problemas con ejercicio soportado | 278 | si se abstiene: cuántas veces ofrece un número y cuántas acierta; la zancada caminando debe salir sin reps | ALTA con un número equivocado |
| G3 · ejercicio no soportado | 285 | que diga «aún no se analiza» y no ofrezca ningún número | cualquier número ofrecido |

- **La cifra comparable con el mercado** es G1 con encuadre bueno: la vista que admite el perfil, cuerpo entero y una persona. Es lo
  que pide el grabador de la app. Las demás se publican aparte, sin mezclarlas.
- **Con D40, MEDIA también ofrece «Usar ≈ N».** Por eso la precisión de MEDIA cuenta tanto como la de ALTA.

## Honestidad: desarrollo y examen
- **Antes de extraer** se congela un examen del 20 % del banco, por grupos: el vídeo original en Kaggle, el grupo `gXX` en UCF y el
  clip en el resto. Lleva su hash.
  - Ese 20 % TAMBIÉN se usa: es el examen final.
  - Nunca se mira clip a clip ni se ajusta con él, y se evalúa una sola vez, al final.
- **El 80 % restante es de desarrollo:** diagnóstico, arreglos y ajuste, con validación cruzada por grupos.
- **Por qué:** un motor ajustado con los mismos vídeos con los que se le califica parece mejor de lo que es. El número que se
  publica es el del examen.

## Pasos
1. **M1** (en marcha): la pose nunca baja de 20 Hz.
2. **VT11a, herramientas** (Codex, sin emulador; brief `briefs/VT11a.md`):
   - el manifiesto congelado de los 1.076 clips, con los grupos y el examen;
   - el conductor de la extracción por lotes, que puede reanudarse;
   - el evaluador del banco completo en el arnés JVM (`C:\kw\vt-jvm`), probado con las trazas viejas.
3. **APK de depuración** con M1, e **instalación en el AVD `Medium_Phone`**.
4. **Extracción** de los 1.076 clips en el emulador: lotes de 12 y unas 3–6 h. La lanza el orquestador.
   - Las trazas van a `C:\kw\vt-bench\full-20261020\traces\`, fuera del repo (D33).
   - Al terminar: apagar el emulador y borrar sus `*.qcow2` de datos, `snapshots` y `tmpAdbCmds`.
5. **Medición de partida** en el desarrollo (80 %), con un informe agregado: `BANCO_COMPLETO_RESULTADOS.md`.
6. **Rondas del motor** (como mucho 3), con Codex.
   - Cada una: arreglos → repetición en JVM sobre las trazas (minutos, sin volver a extraer) → informe.
   - Si un cambio toca la pose o el decodificador, hay que volver a extraer.
   - Prioridad, según el diagnóstico:
     - el recorte del final de la serie;
     - la ambigüedad (`n_amb`);
     - las vistas admitidas (fondos y dominadas de frente);
     - el curl con barra;
     - la pérdida de señal;
     - la cámara en movimiento y la zancada caminando.
7. **Examen final** con el 20 %, una sola vez, y comparación con el listón del mercado.
8. **Teléfono:** tiempo de análisis, calor, 30 y 60 fps reales y m/s con un desplazamiento conocido. El banco no tiene verdad de m/s
   ni de fases a nivel de fotograma.

## Recursos
- **Gradle:**
  - el arnés JVM, por `KPKN_SLOT_LANE=jvm` (1 GB);
  - el APK, por la cola Android;
  - nunca dos compilaciones Android mías a la vez.
- **Emulador:**
  - uno solo, `Medium_Phone`, con los vídeos subidos por lotes y borrados tras cada lote;
  - durante la extracción no arranca ningún otro.
- **Disco:** las trazas pesan poco, unos MB por clip. Los vídeos nunca entran en el repo.
