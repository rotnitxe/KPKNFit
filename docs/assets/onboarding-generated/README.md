# Activos editoriales del onboarding

Estos cuatro PNG fueron generados con la herramienta integrada de generación de imágenes para el carrusel inicial. Las capturas de `Desktop/INSPO WIZARDS` se inspeccionaron como referencia de composición, pero no se copiaron ni se incorporaron al APK.

| Recurso | Uso | Resolución original | Origen |
| --- | --- | ---: | --- |
| `onboarding_p1_welcome.png` | Bienvenida y entrenamiento | 941 × 1672 | Fotografía generada, prompt P1 del plan aprobado |
| `onboarding_p2_training.png` | Equipo y preparación | 1672 × 941 | Fotografía generada, prompt P2 del plan aprobado |
| `onboarding_p3_food.png` | Alimentación opcional | 1370 × 1148 | Fotografía generada, prompt P3 del plan aprobado |
| `onboarding_p4_recovery.png` | Recuperación y rings | 1024 × 1536 | Fotografía generada, prompt P4 del plan aprobado |

Los originales se conservan en este directorio. Las mismas composiciones se empaquetan localmente en `android-native/app/src/main/res/drawable-nodpi/` y se recortan en Compose con `ContentScale.Crop` para evitar decodificar una segunda variante por slide.

Revisión visual: se descartaron las 48 capturas entregadas como material de producto; se conservaron solo patrones de alto nivel (fondo oscuro, jerarquía editorial, tarjetas, progreso y CTA persistente). Los cuatro activos no contienen texto de interfaz, logotipos ni métricas inventadas.
