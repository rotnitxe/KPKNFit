# CAL7 · aislamiento de Room entre los dos cierres RINGS

El barrido399clases/3135casos detectó un timeout30s del segundo cierre de RINGS, aun drenando ViewModels y el repositorio de programas. AugeRepository conserva un DAO creado por KpknDatabase.getInstance. LiveSessionVmFixture.prepare llama ProgramRepository.initForTests, que cierra KpknDatabase antes de crear la nueva base; el reset de AUGE estaba solo en Before de la prueba, no entre sus dos subrecorridos normal/calibrador.

Se reinicia ese singleton de prueba antes de preparar cada subrecorrido RINGS, igual que al comenzar cada test. Así el segundo ViewModel no recibe un DAO de la base que el fixture anterior cerró. No cambia producción, timeout, grabador, cierre, igualdad de logs ni cálculo RINGS. La causa de cancelación del cierre requiere confirmación en la siguiente corrida; se conserva el diagnóstico detallado del estado agregado en3d503ab6d para cualquier fallo restante.

Preparado en u18, sin ejecutar Gradle/JVM/ADB ni tocar la fuente de u11 bajo prueba. Repetición central obligatoria.

## Confirmación central · 23-oct-2026

La tercera completa Android, fuente b7910feaf, ejecutó Cal7FreeSessionRoomTest y los restantes recorridos CAL7 sin fallos, errores ni omitidas; wrapper global exit1 por seis fallos de otras rutas. Se confirmó en runtime que el reset entre fixtures elimina el timeout del segundo cierre y conserva la igualdad RINGS. Evidencia en int-screen-final-codex-20261023-3, sin ampliar timeout ni modificar producción. No acredita por sí sola QA nativa del cierre.
