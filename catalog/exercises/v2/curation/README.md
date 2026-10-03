# Registro de curación

Cada archivo de esta carpeta debe explicar las decisiones de una familia:

- candidatos revisados;
- padre, configuración, especialidad o descarte;
- compatibilidades permitidas;
- metadata y fuentes;
- impacto en templates, protocolos, AUGE, splits y reemplazos;
- aprobación explícita.

No se permite marcar una familia como `APPROVED` mientras falte una decisión,
una configuración por defecto o una referencia de evidencia.

## Fichas (fuente única de autoría)

`fichas/<familyId>.json` es lo único que se edita para cambiar el copy público o
la anatomía de un ejercicio. `scripts/catalog_v2_apply_fichas.py` lo copia a
`../source/families/`. El flujo, el formato y las reglas están en
[`EDITORIAL_GUIDE.md`](EDITORIAL_GUIDE.md); el estado vigente y la prueba de no
uso de los campos retirados, en [`STATUS.md`](STATUS.md).

Cómo se escribe una ficha: [`AUTHORING_FICHA.md`](AUTHORING_FICHA.md). El estado
`CURATED` exige, además del copy, los bloques `technique`, `anatomy`, `visual` y
`sources`; `LEGACY` conserva el copy heredado hasta que le toque su lote.

Herramientas (todas en `scripts/`, de solo lectura salvo la que aplica):

- `catalog_v2_show.py <definitionId>` — la ficha y su anatomía, legibles.
- `catalog_v2_sources.py lookup|abstract|verify` — buscar, leer y comprobar fuentes en PubMed.
- `catalog_v2_ficha_lint.py --definitions a,b` — valida una ficha antes de aplicarla.
- `catalog_v2_land.py --definitions a,b` — aplica, fusiona, pasa el gate, audita, compila y mueve el hash.
- `catalog_v2_visual_brief.py <definitionId>` — el brief con el que se genera la imagen de demostración.

Mediciones: [`QUALITY_BASELINE.md`](QUALITY_BASELINE.md) es la de antes de
retirar campos; [`QUALITY_POST_F1.md`](QUALITY_POST_F1.md) es la del catálogo
actual, campos retirados ya fuera.
