# QA · Perfil y Conceptos clave v1 (28-oct-2026)

`index.html` pone lado a lado el prototipo aprobado (`../prototipos.html`) y las pantallas reales de Compose (412 dp de ancho,
al 100 % y al 130 % de letra). Los PNG nativos salen de `ProfileConceptsRenderSheetTest`:

```bash
KPKN_RENDER_DIR=<carpeta> bash /c/kw/tools/gradle_slot.sh <repo>/android-native "-Dorg.gradle.jvmargs=-Xmx6g" \
  :app:testBaseDebugUnitTest --tests "com.example.kpkn.render.ProfileConceptsRenderSheetTest"
```

## Verificación

- `:app:assembleBaseDebug` (KPKN Dev, `com.example.kpkn.dbg`), 28-oct 08:20: BUILD SUCCESSFUL.
- 75 tests, 0 fallos: ConceptosClaveIndexTest (13), AprendeCatalogAuditTest (7), DeepLinkRouterTest (13), PlanGlossaryTest (11),
  ProfileTechniqueCardTest (2), ProfileTechniqueStateTest (5), TechniqueHubRenderSheetTest (12) y ProfileConceptsRenderSheetTest (12).
- Enciclopedia: `python -I docs/conceptos-clave/validar.py todos --red` comprueba los 135 artículos nuevos y cada PMID contra NCBI.
- `ConceptosClaveUiTest` (androidTest) no se ha ejecutado: necesita dispositivo.

## Emulador

El usuario pidió QA en emulador (no en el Z Flip) durante la noche. El emulador compartido (Pixel_9_Pro_XL, `emulator-5554`)
arrancaba y aguantaba en reposo, pero se cerró de golpe en los seis intentos de pasar el alta con `WizardHarnessActivity`
(persona `qa`), con GPU «auto» y con `swiftshader_indirect`, y también con 17 GB de RAM y 47 GB de disco libres. Sin el alta
no hay programa activo y `DemoSeedActivity` no siembra, así que las capturas de dispositivo quedan pendientes. Por eso
la prueba visual son los renders nativos de Compose, no capturas del teléfono.

## Ajustes hechos en la QA (3 ciclos)

1. Píldora «Abrir técnica en vídeo»: sin `TextAlign.Center`. Con ancho al contenido, el párrafo se maquetaba al ancho
   máximo y los glifos se salían de su caja al 130 %.
2. Baldosas de «Tu archivo»: `SpaceBetween` en vez de un `Spacer` con peso. Sueltas, se estiraban hasta el alto disponible.
3. Buscador de Conceptos: fondo opaco y banda negra detrás cuando queda fijo. Antes asomaba el texto que pasaba por debajo.
4. Búsqueda: los resultados se ordenan por dónde coincidió (nombre, alias, resumen, tema, cuerpo). Los fragmentos van en una
   sola línea.
5. Lectura: los títulos de más de 32 caracteres (estudios con autor y año) usan Syne 23 en vez de 28.
6. Avatar sin nombre: silueta en lugar de la «U» de «Usuario».
7. `concept_search_field` pasa al campo de texto (antes estaba en el contenedor y no se podía escribir desde los tests).
