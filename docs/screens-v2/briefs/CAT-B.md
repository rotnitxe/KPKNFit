# CAT-B · Crear ejercicio y fichas (E7, F1 y F2)

**Worktree:** nuevo desde `feat/screens-v2` cuando la Fase 3 esté integrada (sugerido `C:\kw\u19`, rama `sv2/cat-b`). Trabaja **solo** ahí.

## Qué leer

- `CLAUDE.md` y `docs/screens-v2/DISENO_CONTEXTO.md` (leyes de diseño y método).
- `docs/screens-v2/PLAN_IMPLEMENTACION.md` (esquema de Room congelado).
- `docs/screens-v2/audits/AUD1_motores_editor.md` §3 (motores, medición de 36 nombres y causas raíz).
- `docs/screens-v2/reports/MOT.md` §«Crear ejercicio» (API ya disponible y métricas del motor único).
- `docs/screens-v2/DECISIONES_PROTOTIPOS_2026-10-20.md`, fila E7 y ronda 3.
- `docs/screens-v2/maps/MAPA_editor_catalogo.md`.
- Tableros aprobados en `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit\c23e2d1d-bbd9-499d-b02a-2d07d2b09e95\scratchpad\kpkn_editor_design\project\`: **E7-CrearEjercicio**, **F1-FichaEjercicio** y **F2-FichaVariante**. Son de solo lectura, con la lógica en `<script type="text/x-dc">`.

## Qué construir

El motor de parecidos **ya está hecho en MOT** y no se duplica. La interfaz nueva es la que falta.

1. **E7 · Creador de ejercicio**, conectado a la API existente:
   - `findReferences` / `referenceResult` para enseñar los parecidos antes de crear, con su abstención explicable cuando el nombre es ambiguo o no hay equivalente.
   - selector de foto:
     - del álbum;
     - del almacenamiento privado de la app;
     - reutilizar una imagen compatible de un ejercicio parecido;
     - o la ilustración del patrón cuando no haya foto.
   - `ExerciseCoverResolver.suggestReferenceCover` y `setCover` para la portada; muestra `lastPersistError` si falla el guardado, sin perder lo escrito.
   - descripción **solo** desde una configuración confirmada: nunca se verbalizan inferencias no confirmadas.
   - revisión de la inferencia (material, músculos, patrón) antes de guardar; si algo no se puede afirmar, se dice con honestidad.
2. **F1 · Ficha de ejercicio** y **F2 · Ficha de variante**:
   - con el aspecto del tablero aprobado y los datos reales del catálogo;
   - la portada, la procedencia de la imagen y la abstención cuando falte;
   - sin inventar métricas ni textos.

**Leyes:**
- Refinar, no quitar.
- No inventes opciones, números ni descripciones.
- Objetivos táctiles de al menos 48 dp.
- Paleta, tipografía, radios y espacio de `DISENO_CONTEXTO.md`.
- **Esquema de Room congelado:** no toques entidades, migraciones, `schemas/` ni la versión de `KpknDatabase`.

## Pruebas

- **Robolectric/Compose:**
  - los parecidos reutilizan la búsqueda y su ranking, no un motor paralelo;
  - abstención explicable con un nombre ambiguo y con «XQZ 123»;
  - elegir foto del álbum y del almacenamiento privado, reutilizar imagen compatible y caer en la ilustración del patrón;
  - la descripción solo aparece con configuración confirmada;
  - error de guardado visible y recuperable;
  - F1 y F2 con datos reales, sin métricas inventadas;
  - medidas y objetivos táctiles.
- **Motor:** conserva las métricas de MOT: 36/36 del corpus de AUD1 y, en el corpus fresco, top-1 93,3 % y top-3 97,8 %. No las rebajes ni las maquilles.
- **Renders** de E7, F1 y F2 a 360 × 880 y a 360 × 740, con letra al 100 % y al 130 %, en `C:\kw\shots\catb\`. Ábrelos con Read y compáralos con los tableros.

## Gradle

- Solo con `bash /c/kw/tools/gradle_slot.sh /c/kw/<wt>/android-native "-Dorg.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8" :app:compileBaseDebugKotlin :app:testBaseDebugUnitTest --tests "<clases concretas>"`.
- Lanza en segundo plano con log en `C:/kw/logs/catb-<n>.log` y espera con `timeout 540 bash -c 'until grep -qE "BUILD (SUCCESSFUL|FAILED)" C:/kw/logs/catb-<n>.log; do sleep 30; done'`, repitiendo. Máximo 3 corridas.
- Nunca `--stop`, nunca matar java, nunca `compileBaseDebugAndroidTestKotlin` ni `assembleBaseDebugAndroidTest`, nunca editar `build.gradle.kts` para filtrar.
- `ExerciseCatalogImageCoverageTest` falla por el entorno: ignóralo.

## Commits e informe

- Commits pequeños por tablero, en español, `git add` solo de tus rutas, terminados en `Co-Authored-By: Qoder <noreply@anthropic.com>`.
- Informe `docs/screens-v2/reports/CAT-B.md`: de dónde se partía, cambios por tablero, reutilización del motor, pruebas, métricas, renders y riesgos.
