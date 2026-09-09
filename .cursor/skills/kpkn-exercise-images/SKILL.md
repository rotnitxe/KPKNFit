---
name: kpkn-exercise-images
description: >-
  Genera y corrige PNGs de técnica de ejercicios KPKN Fit (catálogo drawable-nodpi,
  recorte rembg, instalación en emulador). Usar cuando el usuario pida imágenes de
  ejercicios, PNGs de técnica, sentadillas, hack invertida, GenerateImage de
  catálogo, o cuando hable de caricatura, AI slop, pisadera, máquina deformada,
  o exercise_*.png.
---

# Imágenes de técnica KPKN

Una imagen por (definición × implemento). No reabrir lookup ni otros ejercicios salvo que el usuario lo pida.

## Archivos

| Qué | Dónde |
|---|---|
| PNG final | `android-native/app/src/main/res/drawable-nodpi/exercise_<slug>.png` |
| Candidatos | `assets/exercise_<slug>_<letra>.png` (no pises el drawable hasta QA) |
| Lookup | `android-native/app/src/main/java/com/example/kpkn/data/exercises/ExerciseTechniqueImageLookup.kt` |
| Tests lookup | `android-native/app/src/test/java/com/example/kpkn/data/exercises/ExerciseTechniqueImageLookupTest.kt` |
| Estilo catálogo | `drawable-nodpi/exercise_sentadilla_trasera_barra_alta.png` |

Nombre: `exercise_<movimiento>_<variante>_<implemento>.png`, snake_case, sin tildes.

## Flujo

1. **Leer la petición literal.** Foto adjunta del usuario = técnica. Recorte de queja = qué está mal. No reinterpretar.
2. **Cargar refs.** Estilo catálogo + foto de gym (si hay) + máquina vacía real (si hay). **No adjuntar** el PNG malo ni gens fallidas como estilo: copian el error.
3. **Generar candidatos** con `GenerateImage` (namespace `cursor`), `aspect_ratio` `1:1`, prompt ASCII. Filename con sufijo `_a`, `_b`. Ver [prompts.md](prompts.md).
4. **QA visual antes de recortar.** Recortar zona de contacto (pies, barra, pad). Un inspector **limpio** (subagente sin el historial del error). Ver [qa.md](qa.md). Si falla, regenerar; no “arreglar” en texto.
5. **Recorte:** `python .cursor/skills/kpkn-exercise-images/scripts/cutout.py <candidato.png> --out <drawable>`
6. **Instalar** en el emulador `device` (`installBaseDebug` + force-stop + start). No en físico salvo pedido.

## Estilo

- Ilustración **semi-realista de catálogo** (render 3D / pintura digital).
- **No** foto de gym. **No** cómic: sin contornos negros gruesos, sin cel-shade naranja, sin músculos de caricatura.
- Fondo negro en la gen; el recorte deja RGBA. Esquinas alfa 0.
- Ropa oscura **sin logos**. Placas en blanco. Cero texto (`IRONSIDE`, `45LB`, Nike).
- Una sola escena: atleta + equipo. Sin damero, sin sala de gym, sin segunda viñeta.

Si el usuario dice **caricatura**: no uses el PNG actual como primera ref de estilo; pide “no outlines, painted 3D, natural anatomy”.

Si dice **pisadera / piso raro / deforme / AI slop**: es **geometría** (placa torcida, derretida, cajón suelto, dos superficies). No “arreglar el patrón”.

## Sesgos del generador (no negociar)

- **Dos pisaderas:** escalón plano adelante + rampa vacía atrás. Los pies tienen que ir en **la placa real de la máquina**, una sola.
- Adjuntar la gen mala **reproduce** el fallo.
- Vista trasera 3/4 de hack invertida inventa un felpudo frontal. Preferir cámara de la foto del usuario.
- Máquina inventada (prensa, péndulo, hip thrust) ≠ hack de dos rieles a ~45°.
- El caption de `Read` sobre imágenes **miente** si el chat ya habló del error: marca “el error” hasta en la foto buena. QA con subagente estéril.

Detalle y ejemplos: [qa.md](qa.md), [prompts.md](prompts.md).

## Alcance

- Solo los slugs que el usuario nombró.
- Lookup solo si el PNG es nuevo o cambió el mapeo.
- Tras PNG en drawable: reinstalar emulador (regla de relanzar APK).
