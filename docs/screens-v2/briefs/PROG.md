# PROG · Calibrador en los programas: reglas fieles y APRE (CAL5 + CAL6), pista paralela

**Worktree:** `C:\kw\u13`, rama `sv2/programas`, nacida de `feat/screens-v2` @ `373729ae0`. Ya incluye CAL1 y CAL2. Trabaja **solo** ahí.

En paralelo trabajan otros dos agentes:
- uno en `C:\kw\u11`: sesión en vivo y CAL3, que añade la tabla `calibration_decisions` y una vista previa en vivo del efecto del AMRAP según el programa (`LiveAmrapProgramPreview`);
- otro en `C:\kw\u12`: motores del editor.

No toques esas carpetas, ni la interfaz en vivo ni Room.

## Qué leer

- `C:\kw\u13\CLAUDE.md`
- `C:\kw\u13\docs\screens-v2\CALIBRADOR_V1.md`: §0, §4 («Bloque APRE»), §5 completo, §6 (filas CAL5 y CAL6) y §7.
- `C:\kw\u13\docs\screens-v2\audits\INV1_amrap_programas.md`: el estado actual con archivo:línea.
- `C:\kw\u13\docs\screens-v2\audits\RES1_calibrador_fuentes.md`: las reglas originales con sus fuentes, sobre todo §2–§7 y §12.6.
- `C:\kw\u13\docs\screens-v2\reports\CAL1.md` y `CAL2.md`.

## Qué implementar

Tabla de CALIBRADOR_V1 §5, en dominio y datos (`data/protocols/`, `domain/training/`):

1. **5/3/1 BBB/FSL**
   - La serie «+» registra el récord de reps y el e1RM, y **no mueve el TM**.
   - El TM sube +2,5 kg en el tren superior y +5 kg en el inferior por ciclo, si se cumplieron las reps mínimas.
   - Si no se cumplieron, se congela sin el −2,5 %.
   - Reset de −10 % tras dos ciclos seguidos congelados.
2. **nSuns LP**
   - Revisa contra RES1 §4 qué T1 llevan 1+ cada día y corrige el OHP, que hoy no progresa.
   - Tabla de RES1 §4.
3. **Juggernaut 2.0**
   - TM nuevo = TM + (reps − estándar) × 2,5 kg (tren inferior) o 1,25 kg (tren superior), con un tope de 10 reps extra, tras cada semana de realización.
   - Sin superar el estándar, el TM queda igual y se avisa.
   - Corrige la descripción (`JSS:180`).
4. **GZCLP**
   - Etapas del T1: 3×5+ → 2×6+ → 1×10+ → 5RM nuevo × 85 %. La AMRAP del T1 no cambia la carga y tiene un tope de 10.
   - T2 por etapas.
   - El T3 sube cuando la última serie llega a 25 reps.
5. **J&T 2.0**: arregla `collectRepMaxHits` (`PAE:652-654`) para que se dispare con los RM del T1 con %.
6. **Cube, Lilliebridge y Rippler**: revisa el propósito según la fuente. Si es solo registro, marca la serie como AMRAP de registro, explícito y sin efecto vacío.
7. **Plantillas KPKN `power-12-3`, `16-4` y `20-5`**
   - Sus AMRAP pasan a CALIBRATOR con alcance `NEXT_SESSIONS`, por el canal de propuestas (OFF / PROPONER / AUTO).
   - Quita las banderas muertas de las semanas de pico.
8. **Test de 1RM**
   - Pide solo los levantamientos del programa (Smolov: solo sentadilla).
   - Añade el OHP (`OneRmResolution`).
   - Precarga con las series «Test» registradas, con confirmación.
9. **API pura de vista previa**: «qué provocará este AMRAP según su programa» (por ejemplo, «nSuns: TM +5 kg la semana próxima»), en `domain/training/` y sin interfaz. La consumirá la tarjeta en vivo de CAL3. Si CAL3 ya define una firma, adáptate al integrar; documenta la tuya.
10. **APRE 3/6/10 (CAL6)**
    - Protocolo nuevo del catálogo, con ficha, editorial y receta.
    - Usa la tabla de Mann (RES1 §2.2) en %, con redondeo al material:
      - serie 3 → serie 4 en la sesión;
      - serie 4 → XRM de la semana siguiente;
      - las reps con mala técnica (parciales) no cuentan.
    - El XRM se calcula desde el 1RM con la equivalencia de Mann: 10RM = 75 %, 6RM = 83 %, 3RM = 92 %.
    - Prepara también la función pura «Bloque APRE», que crea las cuatro series de un ejercicio a partir de un 1RM, para el editor (CAL4).

## Pruebas

- Una prueba por programa con la regla de su fuente, con números.
- Que se respeten OFF / PROPONER / AUTO.
- La tabla de Mann en sus casos límite.
- Que la semana siguiente use el XRM propuesto.
- Que el test de 1RM pida solo los levantamientos del programa.
- Actualiza las pruebas de catálogo fijadas, como los contratos editoriales y el número de entradas.

## Gradle (importante)

- Solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/u13/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" <tareas>`.
- Hay **una sola ranura global**: espera tu turno.
- Tu Bash corta a los 10 minutos: lanza Gradle **en segundo plano** redirigiendo a un log (por ejemplo, `C:/kw/logs/prog-<n>.log`). Espera con bucles `until grep -qE "BUILD (SUCCESSFUL|FAILED)" log; do sleep 20; done` de 9 minutos como máximo cada uno, repetidos hasta que termine. Nunca dos corridas tuyas a la vez.
- Ejecuta solo `:app:compileBaseDebugKotlin` y `:app:testBaseDebugUnitTest --tests <tus filtros y las clases existentes que tocas>`, sin la batería completa.
- Nunca `--stop` ni matar java. Sin teléfono ni adb. Sin push.

## Commits e informe

- Commits por programa, en español, que terminen con `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Informe en `C:\kw\u13\docs\screens-v2\reports\PROG.md`:
  - qué cambia en cada programa: antes, después y la fuente;
  - las pruebas, con su comando y resultado;
  - lo que no pudiste verificar en la fuente;
  - los riesgos de integración, sobre todo `ProgramAutoregulationEngine` y lo que añade CAL3.
