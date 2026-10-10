# KPKN: reportes de fallos y sesión de datos

Tres carreras reproducidas permitían solicitar un envío después de que volviera la pausa, recrear la marca fatal con admisión cerrada y omitir esa escritura al drenar mantenimiento. El controlador ahora vincula reportes y marca a un escritor de la sesión, serializa pausa/registro/adjunto y carga el consentimiento en IO con contexto personal fijo. Comprueba el commit de la marca pendiente y deshabilita un adjunto que falla. El código de producto cambia sólo en CrashReportingController.kt.

Fuentes validadas: `2b9853e840925c9bb2f95cf0f5c5e5223a8eadcc`. APK construido sobre `3a8af329a8f940630ed8b19bb7d392d27068bd04` más el mismo delta de fuentes, antes del commit; la huella verifica los bytes y su correspondencia con los cuatro archivos del commit. No se atribuye al APK un identificador Git que no llevaba al construirse.

| Comprobación nueva | Resultado y alcance |
|---|---|
| Regresión original | 4/4 PASS; las mismas aserciones que produjeron tres fallos antes del arreglo. |
| Release JVM | 24/24 PASS, doce casos en API26/34. FirebaseApp y Crashlytics simulados; DEBUG real de Release sin mutarlo. |
| Integración Debug | 16/16 PASS en seis clases. El intento previo de once casos y sus dos selectores incorrectos se conserva. |
| UI pública de Cuenta | Activar y desactivar persisten tras PID 17593 y 17740; final desactivado. |
| Mantenimiento nativo | Recuperación del registro y tres fases con PID 17936, 18163, 18289: terminar/descartar, respaldo/restauración, borrado y cambio sintético a invitado. |
| Build/lint/R8 | Debug y AndroidTest compilan; lint sin errores y R8 pasa. No es runtime Release firmado. |
| Distribución | APK y manifiestos Debug/Release sin Health Connect; GPS, cámara y voz conservados. Alineación nativa pasa; runtime 16 KiB pendiente. |

APK Debug: `dedbfb6702a00642d437a5dc471c1d8cfdad4f0ed193c25db9d42c241b19d025`. Test APK: `3a3465015b5caad4f8aefcfc8300f561b051926ce3c5282d01acb13fcd5009f8`. La regresión Release se añade a CI con fuentes aisladas sólo durante esa invocación, conservando los tests del Wizard que necesitan Debug. YAML/argumentos verificados localmente; CI remoto no ejecutado.

La revisión estática pasa a 574/814 candidatos en 84/131 fuentes con alguna fila revisada. Se añaden dos fronteras de callbacks UI; no se modifica el Wizard ni el selector y no se acredita su QA funcional. Quedan 240 candidatos originales por revisar, además de lectores/cachés/SDK y puertas dinámicas.

El drenaje demostrado comprende escrituras personales y solicitudes al SDK; no cancela transferencias ya emitidas ni acredita todos los trabajos internos de Crashlytics. I04 y el plan integral siguen parciales. La suite histórica de 8.092 casos (8.090 PASS, dos omisiones reales de voz) se mantiene como antecedente: no se presenta como reejecución del parche actual. La integración se acreditará mediante el recibo posterior de master, con WIP y HEAD paralelos preservados.

Evidencia principal: [DELIVERY-crash-session.json](DELIVERY-crash-session.json), [huella](source-fingerprint-crash-session.json), [reproducción](crash-session-guard-reproduction.json), [regresión ampliada](crash-lifecycle-expanded-07/TEST-com.example.kpkn.data.diagnostics.CrashReportLifecycleAdversarialTest.xml), [UI](crash-native-consent-11/result.json), [mantenimiento](../stability-verification/crash-integrated-12/result.json), [CI](ci-crash-gate-validation.json).
