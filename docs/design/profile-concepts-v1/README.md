# KPKN · Perfil y Conceptos clave · v1 (propuesta para revisión)

27 de octubre de 2026. Petición del usuario: «Necesito que ahora hagamos un rediseño del perfil del usuario y también de los conceptos claves, que tienen como pantalla una lista sin más.»

Abre `prototipos.html` (autocontenido, con las fuentes reales de KPKN y los 31 conceptos reales de la app). Para recomponerlo: `node build.cjs` (los conceptos salen de `src/concepts-data.js`, extraído de `TrainingConceptsData.kt` y `ConceptSourceRegistry.kt`).

URL directas: `prototipos.html?tab=perfil&scene=p-edit`, `?tab=conceptos&scene=c-read`; también `&fs=1.3` (texto al 130 %) y `&size=393x852`. El buscador y la navegación de Conceptos funcionan de verdad.

## Inventario de hoy (nada de esto se pierde)

### Perfil — `screens/profile/ProfileScreen.kt` (`ProfileViewModel`), rutas `profile` y `competitions`

| Bloque | Qué muestra y qué hace |
|---|---|
| Barra | «Perfil», atrás, Ajustes (ancla del recorrido `PROFILE_AJUSTES`) |
| Identidad | Foto 88 dp o iniciales; botón de cámara → galería; «Quitar foto»; nombre o «Añade tu nombre»; lápiz → edición |
| Edición en línea | Nombre, Sexo (menú: No especificar / Masculino / Femenino / Otro), Estatura (cm), Peso (kg o lb según Ajustes); Cancelar / Guardar; tres errores de validación |
| Datos corporales | Sexo, Estatura, Peso o «Pendiente» |
| Ejercicios estrella | Por ejercicio: eRM, meta, barra y «% hacia la meta»; toca → progreso del ejercicio; estado vacío |
| Álbumes | Texto + «Ver álbumes» |
| Técnica en vídeo | 3 miniaturas recientes con estado, resumen, última conclusión, «Abrir técnica en vídeo»; estado vacío de dos frases |
| Hábitos | Texto + «Ver hábitos» |
| Archivo de competiciones | `ProfileCompetitionsArchive` (ancla `PROFILE_COMPETICION`): fichas de meets, comparar dos, álbum, registrar |
| Progreso nutricional | Meta (peso / grasa / músculo): Inicio, Ahora, Meta, barra y %; tres estados vacíos |
| Progreso | Volumen por músculo, Progreso por ejercicio y los 4 ejercicios más entrenados |

### Conceptos clave — `screens/home/ConceptosClaveScreen.kt` + `ConceptoClaveAccordion.kt`, ruta `concepts?expand=<id>`

- Barra con ✕, «Conceptos Clave» y «Aprende lo que cambia tu entrenamiento».
- Buscador (`searchConceptosClave`: nombre, resumen, texto y tema, sin tildes) con «Limpiar».
- Lista de 31 acordeones agrupados por los 8 temas (orden pedagógico, nombre A-Z): tema, nombre, 3 líneas recortadas, «Leer más» / «Leer menos».
- Abrir desde Home («Aprende» → «Leer» y «Ver todos»), desde el glosario de los planes y por `kpkn://concepts/<id>`: despliega y desplaza hasta el concepto.
- Estado vacío: «No hay conceptos que coincidan.».
- Datos sin usar en pantalla: color e icono de cada tema, resumen corto en la lista, apartado práctico de cada concepto (va pegado al final del texto) y dos fuentes por concepto (`ConceptSourceRegistry`, ocultas a propósito en el acordeón).

## Qué propone el prototipo

**Perfil.** Sin barra de Material: atrás y Ajustes en botones de vidrio; al desplazar aparece la isla con el nombre (la de la sesión en vivo). Héroe con foto de 112 dp y anillo crema, nombre en Syne, «Editar perfil» y una ficha de vidrio (Estatura · Peso · Sexo) que se toca para editar o «Añadir». Ejercicios estrella con anillo de avance y el eRM en Syne a la derecha. Meta corporal con % grande y la pista Inicio → Ahora → Meta. Progreso en filas con icono y «Tus más entrenados» numerados. Técnica igual, en un módulo. Álbumes y Hábitos en dos mosaicos. Competiciones: mismo componente y lógica; solo adopta cabecera y módulo de la página (su interior lo cubre la propuesta Registros v1, pendiente). La edición y la foto pasan a hojas del kit de la sesión en vivo (`LiveSheet`).

**Conceptos clave.** Biblioteca: título en Syne, buscador fijo de vidrio, concepto del día (el mismo de Home), 8 mosaicos de tema con glifo y recuento, y todos los conceptos agrupados con su resumen completo. Página de tema con «Siguiente tema». Búsqueda con resaltado, recuento y fragmento cuando la coincidencia está solo en el texto. Vacío con sugerencias reales. Lectura a pantalla completa: tema y minutos, título, entradilla (resumen), texto, «En la práctica» en vidrio, Fuentes (abren PubMed), «Del mismo tema» y Anterior / Siguiente. Desde un enlace se abre directo en la lectura y ✕ vuelve al origen.

Color solo en detalles: tonos apagados por tema (punto y glifo); el resto, negro, carbón, crema y grises.

## Decisiones para el usuario

1. **Aprobar el prototipo** (o pedir cambios) antes de tocar Kotlin.
2. **Fuentes visibles en la lectura** (recomendado: sí). El código las ocultó a propósito en el acordeón.
3. **Desde un enlace, abrir la lectura** y que ✕ vuelva al origen (recomendado), en vez de abrir la biblioteca con el concepto desplegado.

## Plan de implementación (tras la aprobación)

- `screens/profile/ProfileScreen.kt`: reescribir la UI con el mismo `ProfileViewModel` (sin cambios de lógica ni de Room). Hojas Foto y Editar con el kit `LiveSheet` (se promueve a `internal` compartido o se reutiliza su panel sin tocar la sesión en vivo). Anclas del recorrido intactas.
- `screens/home/ConceptosClaveScreen.kt`: biblioteca, tema, búsqueda y lectura como estados internos de la misma ruta (`concepts?expand=<id>` abre la lectura). `ConceptoClaveAccordion` queda sin uso y se elimina.
- `domain/concepts/ConceptosClaveIndex.kt`: añadir concepto del día compartido con Home, relacionados, anterior/siguiente y coincidencia (nombre / resumen / tema / texto) para el resaltado; tests JVM.
- `data/wikilab/TrainingConceptsData.kt`: exponer el apartado práctico por separado (sin cambiar el texto).
- No se tocan AUGE, el flavor health, Programas / Program Detail ni el interior de competiciones.
- Verificación: `assembleBaseDebug`, tests, capturas nativas en KPKN Dev (`com.example.kpkn.dbg`), tope de 3 ciclos de QA.
