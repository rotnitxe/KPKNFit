# KPKN: consentimiento y verificación de integración

El control de diagnóstico podía volver a escribir preferencias personales mientras se restauraban datos o se cambiaba de cuenta. El editor ahora captura un contexto y un escritor durante su vida; los editores antiguos se rechazan después de reabrir. AccountGraph pausa los diagnósticos durante mantenimiento. El código de producto cambió sólo en tres archivos. Health Connect sigue retirado; GPS se conserva.

La reproducción original falló y se conserva. El arreglo pasó nueve pruebas dirigidas, incluida esa misma aserción. Debug y AndroidTest compilaron. En API36, el cliente real contra Firebase local pasó A→B→A conservando A en Room, ocultando B y rechazando el repositorio retirado. Tres fases de mantenimiento pasaron en procesos distintos. La UI real de consentimiento conservó tanto activar como desactivar tras aperturas en frío; el estado final quedó desactivado.

La suite final seleccionó las 896 clases compiladas y los 7747 métodos inventariados: 8092 casos, cero fallos/errores y 2 omisiones de replay de voz por falta de corpus real. Todos los demás métodos emitieron XML verificable. Las pruebas gráficas son productores de PNG sin aserciones funcionales. Lint final tuvo cero errores, 614 advertencias y 39 hints; R8 minificó correctamente. No es aprobación de runtime Release firmado ni de lanzamiento.

APK Debug: `f765a31a97dfa4214208cf859425544118c9eb79eaf3dfc7dae47cc5d73685fe`. Fuentes Android sin cambios entre huellas anterior/final. El APK no contiene clases de Health Connect; permisos y servicios GPS/voz conservados. Los tests de migración 20–30, cuota SQLite, búsqueda y SAF anteriores tienen su propio APK/hashes y límites, documentados en DELIVERY.json.

La revisión estática cubre 572/814 rutas en 82/131 archivos; no demuestra cobertura universal. Continúan abiertos corpus de voz, dos dispositivos, SDK Google/Play, hardware/16KB, iOS, CI, beta y publicación. La integración en master se acredita exclusivamente mediante su recibo posterior, con hashes del WIP preservados. No hubo push ni deploy.

Evidencia: [DELIVERY.json](DELIVERY.json), [cobertura JVM](jvm-final-coverage.json), [evidencia nativa](native-followup-43.json), [lint/R8](release-checks-final-41.json), [huella de fuentes](source-fingerprint-final-delivery.json), [matriz estática](semantic-followup.json).
