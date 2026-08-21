---
flags: [room]
stage: construction
---

# Integridad de plantillas, splits y programas avanzados — Android

## Rutas

- `android-native/app/src/main/java/com/example/kpkn/data/{models,sessions,splits,protocols,repository}/`: contratos serializables, clonado, catálogo, aplicación y persistencia.
- `android-native/app/src/main/java/com/example/kpkn/domain/{templates,training}/`: auditoría, selección, materialización y avance de programa puros.
- `android-native/app/src/main/java/com/example/kpkn/screens/{sessioneditor,programdetail}/`: aplicación variante-activa y estados de transición persistidos.
- `android-native/app/src/test/java/com/example/kpkn/`: pruebas de regresión de clonado, catálogo, split, protocolo y lifecycle.

## Impacto

- Solo Android nativo. Los campos JSON nuevos tienen defaults y Room permanece en v23; no se cambian entidades, DAO ni esquemas.
- Las definiciones de terceros sin receta fiel verificable permanecen `HIDDEN_UNVERIFIED`; no existe fallback genérico para protocolos especializados.
- La clonación pasa a ser canónica y sanea resultados runtime, preservando únicamente prescripción. Las variantes y las referencias internas se mantienen coherentes.
- Los programas avanzados se materializan por bloque/semana/día y el avance COMPLEX se persiste de forma idempotente.

## Pruebas

- Tests focalizados: clonador/plantillas, catálogo, split application, protocolos, progresión, transición y repositorio.
- `testBaseDebugUnitTest`, compilación Base/Health, ensamblado Base y posterior instalación BaseDebug.
- QA de emulador: editor de variantes, split por bloque y transición de programa; logcat sin crash/ANR.

## Riesgos

- Worktree compartido con cambios no relacionados: no reset, clean, stash, checkout ni reformateos masivos.
- El JSON existente puede no contener campos nuevos: cada enum/campo nuevo tendrá default seguro y pruebas de compatibilidad.
- La transición de programas activos no puede reescribir sesiones completadas ni avanzar una semana vacía.
- Los datos de terceros no se publican por aproximación editorial.
