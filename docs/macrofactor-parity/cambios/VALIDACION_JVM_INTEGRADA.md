# Regresión pura de la integración · 24-oct-2026

`C:/kw/logs/mfp-integration-jvm-3.log`: BUILD SUCCESSFUL, 3.067 pruebas en 275 clases, cero fallos/errores/omitidos. Se ejecutan domain/training, domain/nutrition, domain/workout y domain/export con sus fixtures por el carril JVM. No acredita Room, Compose, Android ni dispositivo.

La tanda anterior ejecutó 3.066 y detectó un único fallo real: la primera corrección de máquina había cambiado también un redondeo de polea/Smith que no alcanzaba el trabajo (25→22,5). La corrección final sólo actúa cuando el redondeo alcanza la carga de trabajo; conserva el resto, incluido el mínimo físico de 50 kg para aproximación solicitada 40 sobre trabajo 100. El banco conserva 18→15 cuando el trabajo es 20, y añade mínimos que igualan/superan el trabajo y la excepción de barra sola igual al trabajo. Expectativas existentes intactas.

Una selección JVM anterior intentó compilar pruebas de recursos/UI que requieren Android: no ejecutó pruebas y no se cuenta como evidencia de comportamiento. La suite nativa completa de fase 5 sigue siendo necesaria para el cierre.
