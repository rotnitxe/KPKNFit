# Prompts GenerateImage

Herramienta: `CallDynamicTool` namespace `cursor`, tool `GenerateImage`.

- `aspect_ratio`: `1:1`
- `description`: ASCII only (sin em-dash, sin tildes raras que rompan el JSON)
- `filename`: `exercise_<slug>_<letra>.png` (nunca el drawable hasta pasar QA)
- `reference_image_paths`: pocas y elegidas. La **primera** ref manda estilo y geometría.

## Qué adjuntar

| Objetivo | Primera ref | Otras |
|---|---|---|
| Técnica / pose | Foto del usuario | Máquina vacía real |
| Estilo catálogo | `exercise_sentadilla_trasera_barra_alta.png` | Foto solo para pose |
| Menos caricatura | Foto + máquina vacía. **No** el PNG cómic actual | — |
| Editar pose buena | Candidato que ya pasó QA de pose | Máquina vacía para la placa |

**Nunca** adjuntar: gen con el mismo fallo, recorte de queja como “copia esto”, PNG actual si el pedido es cambiar estilo.

## Plantilla (técnica + una máquina)

```
Semi-realistic 3D catalog illustration, not a comic, not a gym photo.
No thick black outlines. No cel-shade. Isolated athlete + equipment. Black background.
No logos, no text, blank plates. Dark clothes.

EXERCISE: <nombre en ingles, 1 linea de tecnica>.
Keep the pose and camera of the FIRST photo.

MACHINE: copy the empty machine photo. One foot platform only: a rigid rectangular steel plate with straight edges, part of the frame. Not a front step. Not a second empty ramp. Not melted geometry.

One scene only.
```

## Plantilla (editar, no reinventar)

```
EDIT the first illustration. Keep the same athlete, same pose, same camera, same single platform under the shoes.
Do not add a second plate. Do not move the feet onto a box.

FIX: <un solo problema: plate geometry OR remove comic outlines>.
Still an illustration on black. No logos.
```

Un cambio por gen. Si pides pose + estilo + máquina nueva a la vez, el modelo suelta la técnica.

## Anti-patrones en el prompt

- No pidas “diamond plate texture” si el usuario se quejó de **deformación**. Pide rectángulo rígido, bordes rectos, perspectiva correcta.
- No uses la gen mala como “FAIL example” salvo un overlay de QA; igual suele copiarla.
- Prohibido: `IRONSIDE`, marcas, números en discos.
