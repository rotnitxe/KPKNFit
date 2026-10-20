# Confianza y reserva del contador de repeticiones — 20 de octubre de 2026

Análisis de solo lectura para el orquestador. No se abrieron ni copiaron vídeos; no se ejecutaron Gradle, emuladores, adb ni descargas. No se accedió a `C:/kw/vt-a`. Este informe es el único archivo creado.

La premisa «ninguna ALTA en ajuste y reserva» requiere corregirse: el informe final publicado prueba **0 ALTA en la reserva**, mientras que la reproducción del snapshot del banco devuelve **5 ALTA en AJUSTE**, todas con FULL exacto. Cuatro de esas cinco tienen varias personas según las etiquetas; el detector no activa el veto. Esto demuestra alcanzabilidad matemática, no aceptación del producto ni precisión ≥98 %.

## Método, procedencia y alcance de las cifras

- Leídos ESTADO, GOLDEN_REPORT, GOLDEN_TIMING_HOLDOUT, DECISIONES y el Anexo A de VIDEO_TECHNIQUE_V1; también las pruebas y el dominio pertinente.
- Partición original: 59 ajuste y 38 reserva; x14 es humo previo, de modo que quedan 37 de reserva ciega. Manifiesto congelado: `fd4b2f72d43bd59ada8b8b0af7d0ebbab129629c17e089e75aa46eec33b86c3b`.
- Primero se buscaron desgloses existentes. Los `*.analysis.json` del arnés tienen score/nivel, pero no los cinco componentes ni n_amb. Además corresponden al vídeo completo. No se confundieron con la evaluación recortada de [GoldenReport.evaluate](C:/Users/valen/Documents/KPKNFit/.claude/worktrees/video-technique-rep-counter-ee5b0d/android-native/app/src/test/java/com/example/kpkn/domain/biomechanics/golden/GoldenReport.kt:140).
- Cálculo mediante `python -I -` por stdin. Python cargó por ctypes/JNI las clases puras ya compiladas de `C:/kw/vt-jvm/build/classes/kotlin`, del **16/10**, sin compilar ni escribir. Solo se segmentaron los 59 de ajuste. Se reprodujeron el perfil/material y el recorte anotado del banco; se extrajeron series, ciclos y factores; la fórmula se reimplementó en Python y coincidió con Kotlin con error absoluto <1e-12.
- Se verificaron las 97 etiquetas contra los hashes del manifiesto normalizando CRLF→LF en memoria; las 97 copias del worktree usan CRLF. Los hashes de las 59 trazas de ajuste y sus barras coincidieron byte a byte. El cargador original exige hashes literales y falla con esas etiquetas CRLF: no se modificaron para sortearlo.
- **Las tablas principales son la reproducción del motor congelado, no una ejecución oficial del motor actual de feat/vt-ui.** Después del banco hay cambios en caminar, procedencia de disco y fases. Como comprobación adicional, se reprodujo en Python la máscara actual de caminar: r10 pasa 6→8 FULL y wger_0044 5→6; las ALTA siguen siendo las mismas cinco. Todos los 59 conteos congelados usaban pose, por lo que la política nueva del disco no altera esa elección. No se reprodujo íntegramente el motor actual.
- No se resegmentó ni volvió a puntuar la reserva. Su diagnóstico usa únicamente informes, etiquetas y trazas ya registradas; no se calibraron umbrales con ella.

Resultados congelados de ajuste: 57/59 salidas; **5 ALTA, 11 MEDIA y 41 BAJA con salida**, más dos sin salida. FULL exacto 38/57 y dentro de rango 41/57. Con la máscara actual de caminar, esos dos últimos valores pasan a 40/57 y 43/57, sin cambios en niveles de confianza.

## Componentes por perfil

Medianas entre clips con salida, sin ponderar por duración. Las medianas individuales no se multiplican para obtener una «mediana de score». No hay clips de ajuste BENCH ni ROW; no existe base de ajuste para estimar mejoras específicas de esos perfiles.

| Perfil | n con salida | c_track | c_snr | c_amp | c_rhy | c_view |
|---|---:|---:|---:|---:|---:|---:|
| BACK_EXTENSION | 1 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 |
| CURL | 8 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 |
| DEADLIFT | 2 | 0,929 | 1,000 | 0,723 | 0,661 | 0,700 |
| DIP | 4 | 0,932 | 1,000 | 0,513 | 0,750 | 1,000 |
| HINGE | 2 | 1,000 | 1,000 | 0,855 | 1,000 | 0,700 |
| HIP_THRUST | 1 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 |
| LEG_CURL | 1 | 0,000 | 0,930 | 0,844 | 0,520 | 1,000 |
| LEG_EXTENSION | 2 | 0,500 | 0,900 | 0,853 | 0,762 | 0,550 |
| LUNGE | 4 | 1,000 | 1,000 | 0,938 | 1,000 | 0,700 |
| MACHINE_SQUAT | 4 | 0,891 | 1,000 | 0,972 | 0,792 | 0,850 |
| OHP | 2 | 0,500 | 1,000 | 0,750 | 0,750 | 1,000 |
| PULL_VERTICAL | 4 | 1,000 | 1,000 | 0,613 | 0,500 | 1,000 |
| PUSH_UP | 4 | 0,830 | 1,000 | 1,000 | 0,597 | 0,700 |
| RAISE | 4 | 1,000 | 1,000 | 0,912 | 1,000 | 1,000 |
| SHRUG | 2 | 1,000 | 1,000 | 0,875 | 1,000 | 1,000 |
| SQUAT | 6 | 1,000 | 1,000 | 0,824 | 0,862 | 1,000 |
| STEP_UP | 2 | 1,000 | 1,000 | 0,750 | 0,750 | 1,000 |
| TRICEPS_EXTENDED_REST | 2 | 0,801 | 1,000 | 1,000 | 1,000 | 0,700 |
| TRICEPS_FLEXED_REST | 2 | 0,906 | 1,000 | 1,000 | 1,000 | 0,400 |
| BENCH | 0 | — | — | — | — | — |
| ROW | 0 | — | — | — | — | — |

## Cada clip de ajuste con salida

`P/D` = personas según etiqueta / máximo de poses detectadas en la ventana. La columna siguiente es la fracción de fotogramas con pose que detecta ≥2. Un máximo de dos es el límite del detector, no el número real de personas. Scores/componentes redondeados a tres decimales solo para mostrar; los niveles se calcularon sin redondear. FULL/punto no sustituye el rango aceptable.

| Clip | Perfil | FULL/punto | score | track | snr | amp | rhy | view | n_amb | P/D | ≥2 detectadas % | Nivel / causa o veto |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| g01-pexels-8401319 | DIP | 1/2 | 0,725 | 1,000 | 1,000 | 0,500 | 0,500 | 1,000 | 0 | 1/1 | 0,0 | BAJA / low_fps |
| g02-pexels-6739974 | STEP_UP | 4/4 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 1/1 | 0,0 | BAJA / low_fps |
| g03-pexels-4838147 | STEP_UP | 2/2 | 0,725 | 1,000 | 1,000 | 0,500 | 0,500 | 1,000 | 0 | 1/1 | 0,0 | BAJA / low_fps |
| g04-pexels-36539451 | LEG_EXTENSION | 10/12 | 0,000 | 0,000 | 0,800 | 0,812 | 0,525 | 0,400 | 15 | 1/1 | 0,0 | BAJA / low_fps |
| g18-pexels-32709558 | LEG_EXTENSION | 5/5 | 0,542 | 1,000 | 1,000 | 0,894 | 1,000 | 0,700 | 1 | 1/1 | 0,0 | BAJA / low_fps |
| g20-pexels-5245518 | DIP | 3/3 | 0,400 | 1,000 | 1,000 | 1,000 | 1,000 | 0,400 | 0 | 1/1 | 0,0 | BAJA |
| g21-pexels-4367541 | OHP | 2/4 | 0,000 | 0,000 | 1,000 | 0,500 | 0,500 | 1,000 | 2 | 2/2 | 0,7 | BAJA / low_valid_fraction, POLARITY_FLIPPED |
| g23-pexels-6547801 | CURL | 1/4 | 0,000 | 0,000 | 1,000 | 0,500 | 0,500 | 1,000 | 1 | 1/1 | 0,0 | BAJA / low_fps, SIDE_UNKNOWN |
| g25-pexels-8456990 | CURL | 4/4 | 0,781 | 1,000 | 1,000 | 0,920 | 1,000 | 1,000 | 1 | 1/1 | 0,0 | BAJA / low_fps |
| g27-pexels-39347703 | BACK_EXTENSION | 6/6 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 1/1 | 0,0 | BAJA / low_fps |
| r04-pexels-20559800 | DEADLIFT | 4/5 | 0,114 | 0,859 | 1,000 | 0,947 | 0,821 | 0,400 | 5 | 4/2 | 1,9 | BAJA |
| r06-mixkit-735 | PULL_VERTICAL | 1/1 | 0,725 | 1,000 | 1,000 | 0,500 | 0,500 | 1,000 | 0 | 1/1 | 0,0 | BAJA / low_fps |
| r07-pexels-4964650 | PUSH_UP | 5/5 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 1/1 | 0,0 | BAJA / low_fps |
| r10-pexels-37997554 | SQUAT | 6/8 | 0,300 | 0,649 | 1,000 | 0,925 | 0,724 | 1,000 | 4 | 2/2 | 10,8 | BAJA / MULTI_PERSON |
| r11-pexels-4367576 | PUSH_UP | 2/4 | 0,471 | 0,660 | 1,000 | 0,500 | 0,500 | 1,000 | 1 | 1/1 | 0,0 | BAJA / low_fps |
| r12-pexels-16692800 | PULL_VERTICAL | 6/6 | 0,780 | 1,000 | 1,000 | 0,917 | 1,000 | 1,000 | 1 | 1/1 | 0,0 | BAJA / low_fps |
| r14-pexels-4921641 | SQUAT | 2/2 | 0,571 | 0,970 | 1,000 | 0,500 | 0,500 | 1,000 | 1 | 2/2 | 10,8 | MEDIA / MULTI_PERSON |
| r15-pexels-39171909 | MACHINE_SQUAT | 13/13 | 0,496 | 0,812 | 1,000 | 0,944 | 1,000 | 0,700 | 1 | 1/2 | 15,1 | BAJA / low_fps, MULTI_PERSON |
| r19-pexels-5319102 | RAISE | 8/7 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 1/1 | 0,0 | BAJA / low_fps |
| r20-pexels-6767732 | HIP_THRUST | 3/3 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 1/1 | 0,0 | BAJA / low_fps |
| r27-mixkit-11766 | PUSH_UP | 10/10 | 0,240 | 1,000 | 1,000 | 1,000 | 0,000 | 0,400 | 1 | 3/2 | 6,5 | BAJA / low_fps |
| r28-mixkit-49436 | DEADLIFT | 2/2 | 0,725 | 1,000 | 1,000 | 0,500 | 0,500 | 1,000 | 0 | 1/1 | 0,0 | BAJA / low_fps |
| r29-pexels-38433942 | MACHINE_SQUAT | 2/10 | 0,000 | 0,000 | 1,000 | 0,500 | 0,500 | 1,000 | 1 | 1/1 | 0,0 | BAJA / low_valid_fraction |
| wger_0002 | HINGE | 3/3 | 0,365 | 1,000 | 1,000 | 0,709 | 1,000 | 0,400 | 0 | 1/1 | 0,0 | BAJA |
| wger_0003 | HINGE | 4/4 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 1/1 | 0,0 | ALTA |
| wger_0005 | RAISE | 5/5 | 0,925 | 1,000 | 1,000 | 0,750 | 1,000 | 1,000 | 0 | 4/1 | 0,0 | ALTA |
| wger_0007 | RAISE | 4/4 | 0,758 | 1,000 | 1,000 | 0,824 | 1,000 | 1,000 | 1 | 2/1 | 0,0 | MEDIA / SIDE_UNKNOWN |
| wger_0009 | RAISE | 3/3 | 0,284 | 0,622 | 0,780 | 1,000 | 1,000 | 0,400 | 0 | 3/1 | 0,0 | BAJA |
| wger_0011 | OHP | 6/6 | 0,800 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1 | 1/2 | 61,4 | MEDIA / MULTI_PERSON |
| wger_0027 | SQUAT | 2/2 | 0,725 | 1,000 | 1,000 | 0,500 | 0,500 | 1,000 | 0 | 3/2 | 10,0 | MEDIA |
| wger_0028 | SQUAT | 3/3 | 0,917 | 1,000 | 1,000 | 0,722 | 1,000 | 1,000 | 0 | 2/1 | 0,0 | ALTA |
| wger_0029 | LUNGE | 6/6 | 0,317 | 1,000 | 1,000 | 0,968 | 1,000 | 0,400 | 1 | 1/1 | 0,0 | BAJA |
| wger_0031 | LUNGE | 7/7 | 0,311 | 1,000 | 1,000 | 0,908 | 1,000 | 0,400 | 1 | 2/2 | 4,1 | BAJA |
| wger_0032 | LUNGE | 6/6 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 2/2 | 3,1 | ALTA |
| wger_0035 | LEG_CURL | 6/7 | 0,000 | 0,000 | 0,930 | 0,844 | 0,520 | 1,000 | 5 | 1/1 | 0,0 | BAJA / low_valid_fraction |
| wger_0042 | MACHINE_SQUAT | 4/4 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 2/1 | 0,0 | ALTA |
| wger_0044 | MACHINE_SQUAT | 5/6 | 0,282 | 0,969 | 1,000 | 1,000 | 0,584 | 0,400 | 1 | 4/2 | 0,7 | BAJA |
| wger_0048 | CURL | 4/4 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 1/1 | 0,0 | MEDIA / SIDE_UNKNOWN |
| wger_0049 | CURL | 5/6 | 0,800 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1 | 4/2 | 81,8 | BAJA / low_fps, MULTI_PERSON, SIDE_UNKNOWN |
| wger_0050 | CURL | 5/5 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 2/2 | 5,4 | MEDIA / SIDE_UNKNOWN |
| wger_0051 | CURL | 4/4 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 1/1 | 0,0 | MEDIA / SIDE_UNKNOWN |
| wger_0052 | CURL | 3/3 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 3/2 | 42,4 | MEDIA / MULTI_PERSON, SIDE_UNKNOWN |
| wger_0054 | CURL | 5/5 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 2/1 | 0,0 | MEDIA / SIDE_UNKNOWN |
| wger_0061 | TRICEPS_EXTENDED_REST | 5/5 | 0,800 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1 | 2/2 | 2,6 | BAJA / low_fps |
| wger_0063 | TRICEPS_EXTENDED_REST | 4/5 | 0,311 | 0,603 | 1,000 | 1,000 | 1,000 | 0,400 | 0 | 2/2 | 33,5 | BAJA / low_fps, MULTI_PERSON |
| wger_0064 | DIP | 2/5 | 0,000 | 0,000 | 1,000 | 0,500 | 0,500 | 1,000 | 3 | 2/1 | 0,0 | BAJA / low_fps |
| wger_0066 | DIP | 3/4 | 0,638 | 0,864 | 1,000 | 0,527 | 1,000 | 1,000 | 1 | 2/2 | 8,8 | BAJA / low_fps |
| wger_0068 | SHRUG | 4/4 | 0,765 | 1,000 | 1,000 | 0,853 | 1,000 | 1,000 | 1 | 2/1 | 0,0 | MEDIA |
| wger_0069 | SHRUG | 4/4 | 0,775 | 1,000 | 1,000 | 0,897 | 1,000 | 1,000 | 1 | 2/1 | 0,0 | MEDIA |
| wger_0070 | PULL_VERTICAL | 2/4 | 0,364 | 0,394 | 1,000 | 0,500 | 0,500 | 1,000 | 1 | 2/2 | 34,1 | BAJA / MULTI_PERSON |
| wger_0076 | TRICEPS_FLEXED_REST | 4/4 | 0,400 | 1,000 | 1,000 | 1,000 | 1,000 | 0,400 | 0 | 3/1 | 0,0 | BAJA / low_fps |
| wger_0077 | TRICEPS_FLEXED_REST | 5/5 | 0,289 | 0,813 | 1,000 | 1,000 | 1,000 | 0,400 | 1 | 4/1 | 0,0 | BAJA / low_fps |
| x02-pexels-32363351 | PULL_VERTICAL | 5/9 | 0,313 | 1,000 | 1,000 | 0,725 | 0,383 | 1,000 | 4 | 1/1 | 0,0 | BAJA / low_fps |
| x05-pexels-7786192 | SQUAT | 5/5 | 0,640 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 2 | 1/1 | 0,0 | BAJA / low_fps |
| x09-mixkit-45960 | LUNGE | 2/1 | 0,580 | 1,000 | 1,000 | 0,500 | 0,500 | 1,000 | 1 | 1/1 | 0,0 | BAJA / low_fps |
| x12-mixkit-48541 | SQUAT | 6/6 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 1,000 | 0 | 1/1 | 0,0 | BAJA / low_fps |
| x13-mixkit-50761 | PUSH_UP | 4/5 | 0,122 | 0,418 | 1,000 | 1,000 | 0,694 | 0,400 | 3 | 1/2 | 18,4 | BAJA / low_fps, MULTI_PERSON |

Sin salida REPS: **g15-pexels-6286166** y **wger_0008** (cero ciclos); su puntuación es cero y no entran en las medianas.

Las cinco ALTA son wger_0003 (4/4, score 1), wger_0005 (5/5, 0,925), wger_0028 (3/3, 0,917), wger_0032 (6/6, 1) y wger_0042 (4/4, 1). Solo wger_0003 tiene una persona anotada; las otras tienen 4, 2, 2 y 2, respectivamente. En wger_0032 la segunda pose aparece en aproximadamente 3,1 % de los fotogramas con pose; en las otras tres el detector nunca registra una segunda. [reps/TrackPreparation.kt](C:/Users/valen/Documents/KPKNFit/.claude/worktrees/video-technique-rep-counter-ee5b0d/android-native/app/src/main/java/com/example/kpkn/domain/biomechanics/reps/TrackPreparation.kt:91) exige **más de 10 %**, por eso permite esas ALTA. Tampoco veta el 10 % exacto de wger_0027.

## Causas ordenadas y alcanzabilidad

Los bloqueos siguientes se solapan; los denominadores son las 57 salidas.

Factores menores que 1: c_track en 18 clips, c_snr en 3, c_amp en 31, c_rhy en 21 y c_view en 15. No son equivalentes a bloqueos individuales. Como intervención aislada, sustituir cada factor por 1, conservando los demás vetos, añade respectivamente **0, 0, 1, 1 y 2 ALTA**. Las promociones por amplitud o ritmo son el mismo wger_0027 (tres personas anotadas); las de vista son g20 y wger_0002, ambos exactos. Los ocho casos de tracking y los quince de vista citados abajo son límites superiores que por sí solos hacen inalcanzable el umbral.

1. **Cadencia analizada <20 Hz: 31/57.** Fuerza BAJA independientemente del score; 29 la tienen como motivo principal y dos ya caen por fracción válida baja. Toda la reserva registrada queda aproximadamente en 12,5–15 Hz. No basta que el vídeo de origen tenga 25/30 fps. [TechniqueAnalysisPipeline](C:/Users/valen/Documents/KPKNFit/.claude/worktrees/video-technique-rep-counter-ee5b0d/android-native/app/src/main/java/com/example/kpkn/data/media/technique/TechniqueAnalysisPipeline.kt:348) aplica stride 2 por cadencia alta **o recomendación del dispositivo**, incluso si divide una entrada de 25/30 a 12,5/15. Los informes registran CPU y ese stride. No se demostró qué condición concreta del dispositivo originó cada recomendación.
2. **n_amb >0: 31/57.** Prohíbe ALTA, además de multiplicar por 0,8^n. En 28 de esos 31 hay ciclos conservados próximos a umbral o con giro en hueco; no se sostiene que casi todos sean preparación/re-rack. El bucle de prominencias de [reps/RepSegmenter.kt](C:/Users/valen/Documents/KPKNFit/.claude/worktrees/video-technique-rep-counter-ee5b0d/android-native/app/src/main/java/com/example/kpkn/domain/biomechanics/reps/RepSegmenter.kt:1371) puede volver a contar picos descartados por setup/rerack, aunque el primer bucle los excluya. Hace falta conservar el motivo de cada ambigüedad antes de relajar este veto.
3. **Vista no admitida: 15/57.** Trece tienen c_view=0,4 y dos 0,7; ambos valores hacen imposible score≥0,75 aun con los demás factores perfectos. No ocurre siempre fuera de SIDE: OBLIQUE/FRONT valen **1 si pertenecen a countViews**. DIP frontal g20 e HINGE frontal wger_0002 están restringidos por diseño, no por error de clasificación.
4. **Menos de tres FULL: 13/57.** c_amp=c_rhy=0,5 fijan un techo de **0,725**, incluso con tracking/SNR/vista perfectos y sin ambigüedad. Hay también ocho vetos por lado no identificado/lejano, nueve MULTI_PERSON y uno POLARITY_FLIPPED.
5. **Tracking insuficiente: ocho clips tienen c_track<0,5625**, así que √c_track<0,75 por sí solo. En cambio c_snr satura en 1 en 54/57: solo tres quedan entre 0,780 y 0,930. No es el cuello de botella principal.

Las saturaciones son razonables matemáticamente: c_track=1 desde 95 % observado y c_snr=1 desde Afull/σ=30. Con ≥3 FULL regulares, CV de amplitud≤0,10, CV de intervalos≤0,30, vista admitida y sin vetos, **score=1 y ALTA es alcanzable**. Para n_amb≥2 el techo numérico ya es 0,64; con n_amb=1 el techo es 0,8, pero el veto sigue impidiendo ALTA.

La fórmula exterior, pesos, raíces y saturaciones coinciden con el Anexo A. La diferencia concreta es **c_rhy**: el texto dice CV de duración(FULL), mientras [reps/RepConfidence.kt](C:/Users/valen/Documents/KPKNFit/.claude/worktrees/video-technique-rep-counter-ee5b0d/android-native/app/src/main/java/com/example/kpkn/domain/biomechanics/reps/RepConfidence.kt:88) usa intervalos **entre picos FULL consecutivos de la misma mini-serie**; incluye lo ocurrido entre ellos y omite intervalos que cruzan mini-series. El código usa CV muestral (N−1), un neutro 0,5 con pocas observaciones y el veto de lado; no existe una raíz extra. «Una persona» se implementa como ausencia de MULTI_PERSON, no como verificación estricta del número anotado.

## Reserva gastada: explicación sin recalibración

**SQUAT ymove_007 / ymove_011.** La evaluación recorta antes de reconstruir ciclos. En los informes completos existentes hay cinco FULL y las últimas llegadas son **17,44 s / 17,12 s**, fuera de las ventanas anotadas **17,00 s / 16,60 s**, por **440 / 520 ms**. El informe final recortado registra cuatro FULL y un parcial. La regla a=min(aUp,aDn), FULL desde 0,70 ([reps/RepSegmenter.kt](C:/Users/valen/Documents/KPKNFit/.claude/worktrees/video-technique-rep-counter-ee5b0d/android-native/app/src/main/java/com/example/kpkn/domain/biomechanics/reps/RepSegmenter.kt:844)), exige evidencia de vuelta que el recorte elimina. Causa muy probable: discordancia entre etiqueta de ciclo completo y soporte temporal de la ventana; no prueba que el motor deba rebajar 0,70. Los registros de flujo ya muestran cinco fondos y cuatro crestas interiores, compatibles con una última vuelta en el borde. No se cambian esas etiquetas para rescatar la aceptación histórica.

**ROW r02.** Anotado 12 (rango 12–13), motor 5 FULL+1 parcial: error mínimo 7; seis giros detectados, seis anotados sin pareja, MAE de pares 206,7 ms y máximo 263,3 ms. El informe existente registra disco **PENDING, sin semilla y cero muestras**; el bar.gz está vacío. La señal cae a pose hombro–muñeca. En la ventana, hombro+muñeca izquierdos cumplen visibilidad/presencia≥0,5 en **177/269=65,8 %**; derechos en **0/269**. La visibilidad mediana de la muñeca derecha es 0,03. La etiqueta de vista SIDE se convierte en OBLIQUE, que sí permite contar ROW; no explica por sí sola el error. El flujo/disco registrado para etiquetar tiene periodo ≈1,4 s; a 15 Hz caben ≈21 muestras por ciclo, de modo que la frecuencia nominal no obliga a perder la mitad. La explicación principal es señal de pose incompleta/ocluida sin respaldo de disco, posiblemente agravada por selección de armónico y supresión de picos. Esto último no puede demostrarse: no se guardaron periodo, Aref ni motivos de descarte de esa pasada. No se ajustan ACF/prominencia mirando r02.

## Cinco cambios propuestos, por impacto en cobertura

Efectos **aislados**, respecto de las cinco ALTA congeladas; no se suman. «Exacto» significa igualdad con el punto anotado, «rango» la tolerancia de la etiqueta. Ninguna variante constituye validación ≥98 %.

1. **Preservar suficiente cadencia al extraer pose.** Archivo/función: `data/media/technique/TechniqueAnalysisPipeline.kt`, selección de stride en `runFull`, línea 348. Evitar stride 2 cuando llevaría una entrada de 25/30 por debajo de 20 Hz; mantener las puertas de calidad del dominio y explicitar cuando el dispositivo necesita un modo degradado. **Efecto real en trazas existentes: 0 promociones**, porque no pueden inventarse muestras. Contrafactual de retirar solamente el veto low_fps con conteos fijos: **5→11 ALTA**, seis nuevas: g02, g27, r07, r19, r20, x12. Las seis están en rango; r19 da 8 contra el punto 7, por lo que la exactitud puntual sería **10/11=90,9 %**. Ese cálculo es un conjunto de candidatos, no una predicción del efecto de extraer poses nuevas. Riesgo: nuevas muestras cambian ruido y ciclos; no bajar el veto para conseguir cobertura. Prueba sintética: 25/30/60 fps deben conservar 25/30/30 Hz, y un modo realmente degradado a 15 debe seguir sin ALTA bajo la fórmula actual.

2. **Admitir conteo DIP frontal.** Archivo/función: `profiles/AnalysisProfiles.kt`, `buildTable`, perfil DIP, línea 125: añadir FRONT a countViews, conservando las vistas actuales de métricas. Reproducción con ese único cambio en memoria: **5→6 ALTA; nueva g20, 3/3 exacta, cero errores nuevos**. Riesgo: solo un clip de ajuste avala la promoción; no acredita 98 % ni generaliza a ROM frontal. Prueba sintética: tres ciclos verticales de hombros en FRONT cuentan tres; movimientos de brazos sin desplazamiento del torso no generan ciclos.

3. **Distinguir barra bilateral de movimiento alterno.** Archivo/función: `profiles/AnalysisProfileResolver.kt`, `adjusted`, línea 254: para CURL con barra explícitamente bilateral y mecánicamente compartida, usar perSide=false; conservarlo para mancuernas/variantes alternas. Reproducción de la regla mínima «CURL+barbell→perSide=false»: **5→6 ALTA; nueva wger_0054, 5/5 exacta; ningún conteo de ajuste cambia**. Riesgo: esa etiqueta tiene **dos personas**; la promoción no satisface el requisito humano de una persona y debe bloquearse hasta resolver la procedencia/guardia, aunque el conteo sea correcto. No levantar SIDE_UNKNOWN globalmente: hacerlo produciría cuatro ALTA nuevas sin justificar visibilidad del brazo oculto. Prueba sintética: curl bilateral de barra con lado lejano ocluido cuenta una vez; curl alterno de mancuernas conserva conteo por lado y sus vetos.

4. **Separar contexto del ciclo y ventana de evaluación.** Archivo/función: pruebas `golden/GoldenReport.kt`, `evaluate`, línea 140. Analizar con contexto suficiente y atribuir al intervalo anotado por el giro; exigir evidencia real de vuelta, o informar censura del borde. Decidir esta política con ajuste/sintéticos y fijarla antes de la reserva nueva. Control calculado de analizar el clip completo en ajuste: **0 promociones nuevas; ALTA 5→4**, pierde wger_0005 por menor calidad global; las cuatro restantes son exactas. No es una estimación de la política final de contexto: quitar el recorte sin atribución también empeora casos, y no se recomienda hacerlo sin más. Riesgo: incorporar preparación/re-rack como repeticiones. Prueba sintética: cinco ciclos con quinto giro dentro de ventana y llegada 500 ms después cuentan cinco con contexto real; sin llegada observada se marcan censurados, y un setup fuera de la ventana no suma.

5. **Hacer auditable la confianza y alinear la aceptación con exactitud/personas.** Archivos/funciones: `metrics/TechniqueAnalysisEngine.kt::analyze` y `golden/GoldenReport.kt::render/counts`. Conservar ConfidenceBreakdown, fs efectivo, n_amb con motivos, vetos y procedencia de personas en el informe por clip; publicar ALTA exactas y ALTA en rango por separado. [GoldenReport.counts](C:/Users/valen/Documents/KPKNFit/.claude/worktrees/video-technique-rep-counter-ee5b0d/android-native/app/src/test/java/com/example/kpkn/domain/biomechanics/golden/GoldenReport.kt:229) calcula highCorrect por rango; `OriginalAcceptance` ya contiene una comprobación puntual y debe mantenerse. **0 promociones**. Si además se exige una persona anotada al auditar el banco, las cinco ALTA solo dejan **una elegible**, sin errores puntuales; eso es una auditoría, no una solución runtime basada en etiquetas disponibles solo en pruebas. Riesgo de precisión: no añade errores de conteo y evita presentar tolerancia como exactitud; puede descubrir falsa seguridad del detector. Prueba sintética: score 1 a 15 Hz da BAJA, score 0,8 con n_amb=1 no da ALTA, un rango 7–8 no convierte 8 en exacto frente a punto 7 y una segunda persona conocida bloquea elegibilidad.

También conviene conservar el motivo de ambigüedad en `RepSegmenter.countAmbiguous`; no se propone ignorar preparación/re-rack sin medir su contribución, ni reducir el mínimo de tres FULL. Poner c_amp/c_rhy=1 en series cortas promovería solamente wger_0027 (dos FULL correctas), que tiene tres personas anotadas: no resuelve una ALTA defendible.

## Validación siguiente

La reserva actual queda gastada y su FAIL no se reescribe. Crear una reserva nueva independiente por persona, grabación y fuente, fijando motor/políticas antes de ver salidas; pedir autorización por cada clip que deba descargarse (origen, archivo y tamaño), sin descargar ahora. Incorporar primero a ajuste nuevos ROW y BENCH, hoy ausentes, con seguimiento del disco confirmado por la persona y ciclos rápidos/lentos/oclusiones.

Alternativa con teléfono: grabaciones consentidas a 30/60 fps, trípode, vistas admitidas, una persona, tres o más ciclos, al menos dos segundos reales de contexto antes/después; incluir negativos con personas de fondo, alternancia, oclusiones, parciales y retornos fuera de ventana. Separar personas/sesiones de ajuste y validación, congelar las etiquetas sin conocer las predicciones y evaluar exactitud de conteo por clip, cobertura ALTA y calibración de confianza. No contar repeticiones del mismo clip como muestras independientes.

Cinco aciertos no acreditan 98 %. Para una cota binomial unilateral del 95 % ≥98 % se requieren **al menos 149 ALTA independientes y cero errores**: 0,05^(1/149)≈0,9801. Si hay dependencia entre clips de una misma persona/sesión, ese cálculo no basta; evaluar por grupos y ampliar el banco. Establecer también una cobertura mínima de ALTA para que el motor no cumpla precisión absteniéndose siempre.

## Qué no pudo comprobarse

No se ejecutaron el motor actual completo, Gradle ni pruebas nuevas, ni se inspeccionaron vídeos, teléfono, rendimiento térmico o precisión física. Los sintéticos anteriores son propuestas, no tests ejecutados. No se reconstruyeron intermediarios de la reserva, ni se conoce cuánto de su error corresponde a ACF frente a huecos; no se propone calibrarlo con ella. No hay etiquetas temporales de fases suficientes para resolver 50 ms ni suficientes ALTA independientes para demostrar 98 %. Tampoco se puede inferir la exactitud real de r19 entre 7 y 8 a partir de su rango.

## Fórmula Python reproducida y líneas Kotlin

Las entradas a esta función proceden de las trazas y la segmentación congelada, no de despejar el score redondeado del arnés. Correspondencia: [reps/RepConfidence.kt](C:/Users/valen/Documents/KPKNFit/.claude/worktrees/video-technique-rep-counter-ee5b0d/android-native/app/src/main/java/com/example/kpkn/domain/biomechanics/reps/RepConfidence.kt:73) (tracking/SNR); línea 82 (FULL y amplitud); 88–97 (intervalos y ritmo); 99–107 (vista y score); 109–125 (vetos y nivel); 132–138 (CV muestral). Constantes: [metrics/AnalysisTuning.kt](C:/Users/valen/Documents/KPKNFit/.claude/worktrees/video-technique-rep-counter-ee5b0d/android-native/app/src/main/java/com/example/kpkn/domain/biomechanics/metrics/AnalysisTuning.kt:204) hasta línea 235. El cómputo de n_amb viene del segmentador, no de la función de confianza.

```python
from math import sqrt, log10, isnan
def clamp(x):
    return 0.0 if isnan(x) else min(1.0, max(0.0, x))

def cv(values):
    if len(values) < 2: return 0.0
    mean = sum(values) / len(values)
    if not mean > 0.0: return 0.0
    return sqrt(sum((x-mean)**2 for x in values)/(len(values)-1))/mean

def confidence(valid_fraction, a_full, sigma, fulls, fs,
               view_kind, count_views, n_amb, flags, side_mode,
               polarity_flipped=False):
    # fulls: ciclos FULL, en orden, con amplitudeFraction, peak, miniSet.
    ct = clamp((valid_fraction - .70)/.25)
    cs = (clamp(log10(a_full/sigma/5)/log10(6)) if sigma > 0
          else (1.0 if a_full > 0 else 0.0))
    ca = (clamp(1-(cv([c["amplitudeFraction"] for c in fulls])-.10)/.30)
          if len(fulls) >= 3 else .5)
    intervals = [(b["peak"]-a["peak"])/fs
                 for a,b in zip(fulls,fulls[1:])
                 if a["miniSet"] == b["miniSet"]]
    cr = (clamp(1-(cv(intervals)-.30)/.50)
          if len(fulls) >= 3 and len(intervals) >= 2 else .5)
    cvw = 1.0 if view_kind in count_views else (.7 if view_kind == "OBLIQUE" else .4)
    score = sqrt(ct)*cvw*(.45*cs+.30*ca+.25*cr)*(.8**n_amb)
    forced = ("no_full_reps" if not fulls else
              "low_valid_fraction" if valid_fraction < .60 else
              "low_fps" if fs < 20 else None)
    side_cap = side_mode == "NEAR_ONLY" or bool(
        flags & {"SIDE_UNKNOWN", "FAR_SIDE_WORKING"})
    allowed = (n_amb == 0 and not side_cap and not polarity_flipped and
               not flags & {"LEGACY_TRACK", "MULTI_PERSON", "POLARITY_FLIPPED"})
    level = ("BAJA" if forced else "ALTA" if score >= .75 and allowed
             else "MEDIA" if score >= .45 else "BAJA")
    return ct, cs, ca, cr, cvw, score, level, forced
```

Hashes de procedencia (el snapshot se leyó directamente, sin copiar clases):

- `android-native/app/src/main/java/com/example/kpkn/domain/biomechanics/reps/RepConfidence.kt`: `f45a7236083c77edff57096a8b8f4f39c032f9cc98f37a3be7883310f83db121`
- `C:/kw/vt-jvm/build/classes/kotlin/main/com/example/kpkn/domain/biomechanics/reps/RepConfidence.class`: `2058dec5ab339065d668399f1cda0a50aaf72da80045072b585e8424e2b9ffdc`
- `C:/kw/vt-jvm/build/classes/kotlin/main/com/example/kpkn/domain/biomechanics/reps/RepSegmenter.class`: `0c712a2ef12103800b2029b6b22a86ddcd3c6d33a70dbdd1dce828df385c91c4`
- `C:/kw/vt-jvm/build/classes/kotlin/main/com/example/kpkn/domain/biomechanics/reps/CountSeriesBuilder.class`: `afeb3d38f9812368a1f3f589465a4dec8b6230aec9682830a21dee1dedd8af5e`

