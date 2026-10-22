# CAL7 · Punta a punta

**Worktree:** nuevo desde `feat/screens-v2` cuando la Fase 3 y los paquetes CAL4-V, CAL5b y PLAN-OBS estén integrados (sugerido `C:\kw\u23`, rama `sv2/cal7`). Trabaja **solo** ahí.

## Qué leer

- `CLAUDE.md` y `docs/screens-v2/DISENO_CONTEXTO.md`.
- `docs/screens-v2/CALIBRADOR_V1.md` §6 completo.
- `docs/screens-v2/reports/CAL1.md`, `CAL2.md`, `CAL3.md`, `CAL4-V.md`, `CAL5b.md`, `PROG.md` y `PLAN-OBS.md`.
- Los arneses de Robolectric y Room en memoria que ya existan en el repo.

## Qué construir

Este paquete es de **recorridos completos y caracterización**, no de funciones nuevas. Si un recorrido descubre un defecto, se corrige y se prueba.

1. **Sesión libre con calibrador:**
   registrar → aplicar → revertir → terminar → la sesión siguiente parte de la referencia.
2. **Cancelar:** no deja referencia.
3. **Programas gestionados:** 5/3/1, nSuns, Juggernaut, GZCLP y APRE a lo largo de un ciclo o de una semana completa, con OFF, PROPONER y AUTO.
4. **Test de 1RM** solo con los levantamientos del programa, incluido el press militar.
5. **Caracterización de RINGS:** la fatiga de un AMRAP calibrador frente a una serie normal no cambia los anillos de forma inesperada.

**Leyes:**
- Sin propagación: el AMRAP solo afecta al mismo ejercicio en su contexto.
- Nada automático sin control en OFF / PROPONER; AUTO solo donde el programa lo permita.
- No toques el esquema de Room. Si un recorrido exige un dato que no existe, para y anótalo para la v32.
- Refinar, no quitar.

## Pruebas

- Robolectric y Room **en memoria**; nada de teléfono ni emulador.
- Un recorrido por programa y por modo, con aserciones sobre la referencia, las cargas y las series no tocadas.
- La prueba de RINGS compara un AMRAP calibrador con una serie normal del mismo contexto y deja fijado el resultado esperado.
- Documenta los recorridos y sus resultados en el informe.

## Gradle

- Pantallas y Room: `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" :app:testBaseDebugUnitTest --tests "<clases concretas>"`.
- Dominio puro: carril JVM.
- Log en `C:/kw/logs/cal7-<n>.log`, espera con `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)" C:/kw/logs/cal7-<n>.log; do sleep 30; done'`, máximo 3 corridas.
- Nunca `--stop`, nunca matar java, nunca `compileBaseDebugAndroidTestKotlin` ni `assembleBaseDebugAndroidTest`.

## Commits e informe

- Commits pequeños en español, `git add` solo de tus rutas, terminados en `Co-Authored-By: Qoder <noreply@anthropic.com>`.
- Informe `docs/screens-v2/reports/CAL7.md`: recorridos, resultados, defectos encontrados y corregidos, RINGS y riesgos.
