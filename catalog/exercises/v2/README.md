# Catálogo de ejercicios

Esta carpeta es la única fuente editorial del catálogo nuevo. No se edita el
asset Android generado a mano y no se importan aliases como identidad runtime.

## Estados

- `DRAFT`: candidato pendiente de revisión; nunca puede entrar al asset de
  producción.
- `REVIEWED`: revisado técnicamente, pero aún no aprobado para el corte.
- `APPROVED`: puede compilarse como catálogo ejecutable.

La fuente debe declarar configuraciones explícitas. El compilador rechaza
combinaciones cartesianas, defaults inválidos, opciones sin efecto, IDs
duplicados, metadata requerida ausente y estados distintos de `APPROVED`.
Los ejes se declaran en orden jerárquico (general → particular) y el runtime
solo revela el siguiente nivel compatible. La auditoría de fusiones y ejes está
en [`curation/GROUPING_AUDIT.md`](curation/GROUPING_AUDIT.md).

El inventario de `exercise_database.json` y `exercise_id_aliases.json` solo se
usa como evidencia de candidatos. No se debe copiar automáticamente al
catálogo v2.

## Flujo

1. Curar una familia en `families/`.
2. Registrar cada decisión en `curation/`.
3. Ejecutar `python scripts/compile_exercise_catalog_v2.py --check`.
4. Obtener aprobación explícita de la familia.
5. Generar artefactos únicamente con `--write` cuando todas las definiciones
   de esa revisión estén aprobadas.

La revisión completa `v2-approved-2026-08-02-c` ya fue compilada y el asset
aprobado se distribuye únicamente desde el pipeline determinista. Un fallo de
validación detiene el pipeline y no produce un asset parcialmente válido. Los
archivos legacy permanecen solo bajo `curation/evidence/legacy/` como evidencia
editorial y nunca como fallback runtime.
