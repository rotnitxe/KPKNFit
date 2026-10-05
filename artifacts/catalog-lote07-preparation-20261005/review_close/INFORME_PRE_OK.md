# Informe previo al OK — lote 7

Estado: revisión de ciencia, técnica y PNG cerrada en lo que no depende de una respuesta humana. No es aprobación, no está integrado y no hay commit. La revisión congelada `review_clean` no se reescribió.

## Qué se cerró en este turno

- Relectura completa de las 8 descripciones de Hip Thrust y de todos los overrides articulares de talones y tibial. El tibial no tiene overrides por configuración; su base articular ya distingue dorsiflexión, subastragalina estable y rodilla extendida.
- Corrección de un why falso: `hip_thrust__unilateral__machine` decía que los dos pies estaban en la plataforma. Ahora dice un pie en la plataforma y el otro fuera. Lint privado de `hip_thrust`: gate 0, errores 0, avisos 0. Hash de cuerpo nuevo `a5928a4a1606b166fe25739e6d73196257fcfc0ffa7f1b26e0d8c126bd4d0e1b`. El hash viejo de `review_clean` era `6de419790a75f9d04d67de89472e8694c6840799da8566e997169f89c87ed8ce`.
- Manifiestos completos, 0 faltantes y 0 discrepancias: A 216, B 216, C 233, D 221, E 215. Tras la corrección se actualizaron solo los hashes de `fichas/lower_hip_extension_hip_thrust.json` y `author_b.py`.
- 21 PNG abiertos y con hash coincidente. Veredictos en `PNG_QA.md`.
- Prompts de sentada, burro, prensa y la frase de pie libre en `PROMPTS_ESPECIFICOS.md`. Sustituyen el `brief_for` crudo de `equipmentId=machine`. No se generó ninguna imagen.

## Anatomía que se propone

Once definiciones / 37 configuraciones. Tres padres siguen LEGACY enteros y no entran en ese conteo: `glutes_patada_gluteo_lateral`, `hip_abduction`, `hip_adduction`.

- Patada posterior y diagonal: el medio es PRIMARY en la diagonal, después del mayor. La posterior no promueve al medio por postura.
- Puente, Hip Thrust, Frog Pumps y Reverse Hyper: el PRIMARY sigue siendo solo glúteo mayor. Frog no promueve al medio: la apertura no está resistida. Los unilaterales añaden medio STABILIZER.
- Reverse Hyper: erectores SECONDARY y columna lumbar SECONDARY. El apoyo de pecho y abdomen no inmoviliza la pelvis en el borde. Lawrence 2019 midió momento lumbar y movimiento pelvis-tronco. Lawrence 2022 registró erectores y cambio de flexión del tronco con la carga. No se aplica la simplificación del remo Seal. Esta es la propuesta para el OK; no se rebajó para esconder volumen.
- Monster Walk: el dominante y el conjunto PRIMARY pasan del mayor al medio. Es abducción resistida por la banda.
- Copenhague estática y dinámica: aductores como motor, antebrazo de apoyo que no es agarre, core y erectores de sostén. La dinámica no hereda la eficacia de un programa entero.
- Talones: `calves` sigue siendo el único PRIMARY. Sentada no aísla el sóleo. Burro no estira el gemelo por flexionar la cadera. Los cuatro unilaterales añaden glúteo medio STABILIZER, no PRIMARY. El tibial conserva solo `tibialis_anterior` PRIMARY.

Los añadidos SECONDARY y STABILIZER son indirectos. Un exceso PRIMARY, si aparece al integrar, se corrige en la prescripción y no en la anatomía. En este turno no se materializaron semanas.

## Protecciones

Producción, 28 IDs, y tests, 13 IDs, están en `rebase_actual/inventory.json` → `protectedScope`. Ninguna propuesta de los 11 cuerpos cambia el conjunto PRIMARY salvo Monster Walk, que no está en esa lista. Las protegidas de puente, Hip Thrust, Copenhague, talones y tibial conservan su PRIMARY. Hip Thrust unilateral de máquina solo corrigió el why del pie; el rol de cadera sigue PRIMARY y el medio sigue STABILIZER.

## Herencia y variantes

La única relación declarada es `copenhagen_plank_dynamic` hija de `copenhagen_plank`, con `allowedDifferences` vacío. No se propone otra herencia. Pertenecer a la misma familia no hereda músculos.

Dudas que siguen abiertas y no se reinterpretaron:

1. Montaje de la patada lateral con mancuerna. El padre `glutes_patada_gluteo_lateral` sigue LEGACY.
2. Sentido de bilateral de pie en abducción con polea o banda, y en aducción con máquina, polea o banda. Esos IDs no se convirtieron en alternos, no se inventó suspensión y no se retiraron.
3. Talón unilateral con barra libre: no hay demostración primaria exacta. Se conserva como configuración heredada y queda en la cola de variantes dudosas. La Smith de talones se describe por la estación (riel, bloque, barra en hombros) sin afirmar un vídeo visto. Su PNG existe y falla la fase alta.

## Cola gráfica

Detalle en `PNG_QA.md`. Nueve configuraciones tienen un PNG que cumple su propio contrato. El resto de los 21 archivos falla al menos una configuración que lo reutiliza. Faltan PNG de sentada, burro, prensa, tibial, Reverse Hyper, puente corporal y las dos patadas. Generar solo con `PROMPTS_ESPECIFICOS.md` para las tres estaciones especiales y con la frase de un solo pie para cada unilateral.

## Rutas exactas

Cuerpos propuestos, todavía privados:

- `authors/A_kickbacks/fichas/` patada posterior y diagonal
- `authors/B_supported_hip/fichas/lower_hip_extension.json` puente y Reverse Hyper
- `authors/B_supported_hip/fichas/lower_hip_extension_hip_thrust.json` Hip Thrust corregido
- `authors/B_supported_hip/fichas/lower_hip_extension_external_rotation.json` Frog Pumps
- `authors/C_abduction/bodies/glutes_monster_walk_banda.json`
- `authors/D_adduction/fichas/hip_adduction.json` solo las dos Copenhague
- `authors/E_lower_leg/fichas/lower_plantar_flexion.json`
- `authors/E_lower_leg/fichas/lower_ankle_dorsiflexion.json`

No integrar `hip_abduction`, `hip_adduction` ni `glutes_patada_gluteo_lateral`.

Base que hay que preservar: catálogo `c67eeb8ff6a8e68988fd3e0396fe8601920d3cdec7a05973fa95bf6085eb7566`.

## Qué falta para aplicar

Un OK humano de este informe. Sin ese OK no hay land, lint integral de ROOT ni commit. Las dos preguntas de montaje no bloquean los 11 cuerpos ya redactados; sí bloquean curar los tres padres LEGACY.
