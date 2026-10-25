# P2-Na · Implementado y validado localmente; QA de dispositivo pendiente

| Pantalla | Cambio visible | Motivo |
|---|---|---|
| Registrador → Recetas | Crear, editar, duplicar y borrar recetas; ingredientes con instantáneas, raciones y peso final opcional; vista previa y registro por raciones o gramos | El agua cambia la densidad, nunca los nutrientes del lote; las comidas anteriores no se recalculan |
| Búsqueda y plantillas | Estrella de favorito y acceso inicial a favoritos; las recetas se rotulan por ración | Acceso rápido sin cambiar el orden del buscador ni inventar gramos |
| Registrador y diario | Hora local editable junto a la fecha; editar sin cambios conserva el timestamp anterior; orden por hora dentro de cada grupo | Registrar una comida pasada sin moverla de día ni perder la hora |
| Ajustes → Copia JSON | Preferencias de utensilios, aprendizaje y favoritos en sección opcional v6; exportación por fila con cuarentena | Una fila ilegible no impide respaldar las sanas; las copias antiguas preservan las preferencias locales |

## Evidencia local · 24-oct-2026

Base `mfp/n@00bde8b50`. Sin cambios de entidades, migraciones, esquemas, importador ni catálogo.

- Dominio inicial: 77 pruebas / 4 clases, verdes sin omisiones. Log `C:/kw/logs/mfp-P2Na-local-20261024-jvm-2.log`.
- Android 1: 252 pruebas / 29 clases, verdes. Android 2: 469 / 63. Android 3: **475 / 64**, sin fallos, errores ni omisiones. Log final `C:/kw/logs/mfp-P2Na-local-20261024-android-3.log`.
- Render de la tercera tanda: `C:/kw/shots/mfp/P2Na-local-20261024-final3`, 54 PNG al 100/130 %. Revisión visual parcial; no equivale a QA física.
- La revisión posterior encontró que el puente del logger imponía gramos a una receta por raciones. Corregido con procedencia opcional en `LoggedFood`, conservación de unidad/versión y edición decimal; regresión del recorrido receta → tarjeta → guardado → reapertura y de escalado con micronutrientes e incertidumbre. Este cambio fue posterior a la tercera tanda Android; quedó validado en la tanda adicional descrita abajo.
- La instrucción posterior del usuario exige continuar sin preguntas rutinarias hasta completar la entrega; autoriza las comprobaciones adicionales necesarias.

No se ha construido/instalado un APK nuevo ni probado en dispositivo. La validación local focal está completa; siguen pendientes los criterios integrados y de dispositivo de la ola. No push.

La regresión focal de la corrección pasó: **44 pruebas JVM / 4 clases**, 0 fallos/errores/omisiones. Log `C:/kw/logs/kpknjvm-mfpn-20261024-024525.log`. La primera selección falló al compilar fixtures ajenos; la segunda detectó un acento alterado por la edición, corregido sin modificar la expectativa del test. La tercera pasó. Esta evidencia no sustituye Android ni dispositivo.

## Comprobación final adicional

73 pruebas Android / 9 clases, 0 fallos, errores u omisiones. Log `C:/kw/logs/mfp-P2Na-local-20261024-android-extra.log`; 18 PNG en `C:/kw/shots/mfp/P2Na-local-20261024-extra`. Revisado el logger de media ración al 130 %: conserva cantidad y unidad, etiqueta de procedencia y campo decimal. Incluye recorrido real del logger, edición de raciones/gramos, JSON, repositorio y preferencias. No hace falta repetir esta tanda sin cambios o fallos nuevos.
