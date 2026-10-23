# CAL7 · aislamiento de Room entre los dos cierres RINGS

El barrido399clases/3135casos detectó un timeout30s del segundo cierre de RINGS, aun drenando ViewModels y el repositorio de programas. AugeRepository conserva un DAO creado por KpknDatabase.getInstance. LiveSessionVmFixture.prepare llama ProgramRepository.initForTests, que cierra KpknDatabase antes de crear la nueva base; el reset de AUGE estaba solo en Before de la prueba, no entre sus dos subrecorridos normal/calibrador.

Se reinicia ese singleton de prueba antes de preparar cada subrecorrido RINGS, igual que al comenzar cada test. Así el segundo ViewModel no recibe un DAO de la base que el fixture anterior cerró. No cambia producción, timeout, grabador, cierre, igualdad de logs ni cálculo RINGS. La causa de cancelación del cierre requiere confirmación en la siguiente corrida; se conserva el diagnóstico detallado del estado agregado en3d503ab6d para cualquier fallo restante.

Preparado en u18, sin ejecutar Gradle/JVM/ADB ni tocar la fuente de u11 bajo prueba. Repetición central obligatoria.
