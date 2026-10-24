# Validación privada: entrenamiento y respuesta

Base pública verificada: `edc237f66d8474c3a9922296b61940e7030b7f8a`.
Paquete POST-RINGS: 481310 bytes; SHA-256
`c7baf4d85e097ded24e8c0f45fd676d48a3173bcffd564bf17c5990363f44161`.
Los 95 archivos seleccionados y sus hashes se verificaron antes de aplicar el WIP.
El bundle anterior Rings v4 no se importó; el estado previo sigue recuperable.

## Comprobaciones completadas

| Comprobación | Resultado efectivo |
|---|---|
| Python, herramientas del catálogo | 218 pruebas aprobadas, 54,305 s, exit0 |
| Backend final | 19 pruebas y 32 subpruebas aprobadas; una advertencia de deprecación; exit0 |
| Catálogo reproducible | 228 definiciones, 553 configuraciones; `--check` aprobado |
| Gate de retiro | Cero infracciones detectadas; controles positivos y anillas físicas negativos aprobados |
| Swift | 38 archivos modificados sin errores sintácticos; **sin compilación/XCTest** |

Hash canónico del catálogo:
`ce1ed8f4ec0c7476e66a353e567717b86cc896fda797d33ee99bb5c788584ce7`.
El gate mecánico incluye main/debug, iOS, backend y recursos; sus excepciones son
importadores raw aislados, rutas410 y seis líneas de compatibilidad SQLite/encode y clasificación de método
Android/iOS identificadas por SHA-256. Complementa la revisión semántica, no la sustituye.

Room termina en34: preservación de originales, purga de derivados/coefs y propuestas
fisiológicas pendientes. Las propuestas de protocolos se conservan con confirmación.
iOS termina en23 con la misma frontera de datos. No hay destructive fallback.

## Regresión Android

La tanda nativa UI/Room v9 termina con **18 pruebas, cero fallos/errores/omitidas,
cinco XML y exit0**. Comprueba diálogo completo al130 %, edición explícita,
continuación sin respuesta, gráfico/restauración, bienvenida Ejemplo, AUTO con
confirmación y el recorrido Room de disciplina/notas, reinicio y siguiente actividad.
El bucle se resolvió acotando el ancho de ventana independientemente del contenido;
no se amplían timeouts ni se omite la prueba. Se mantuvo Robolectric4.12.2 del repo.

La regresión final `changed-regression-v3` aprobó **2033 pruebas en200 clases,
cero fallos, errores u omisiones; exit0**, junto con `assembleBaseDebug`.
HEAD probado: `b6c35b9b549cc05b1143f1010c28bcf14607fa1c`.
La selección comprende todas las clases modificadas no denominadas Render;
no equivale a ejecutar todo el repositorio. Los XML, selección y comando exacto
se entregan privados. Las tandas previas fallidas quedan como checkpoints y no
se cuentan como aprobación. Las correcciones conservaron los oráculos numéricos
de protocolos con confirmación explícita y retiraron expectativas fisiológicas.

Una tanda posterior de renderizado de los tres componentes aprobó cuatro pruebas,
exit0; produce fixtures nativos, no capturas de teléfono. El gráfico y Ejemplo
son legibles; el diálogo conserva Continuar visible con texto grande. La captura
View.draw del contexto presenta recorte en la parte superior del contenido
desplazable; no acredita ausencia de problemas visuales en un dispositivo.
El QA físico debe comprobar desplazamiento, foco y teclado.

APK Base Debug: `com.example.kpkn.dbg`, etiqueta **KPKN Dev**,
SHA-256 `be0c74696771a39b835fd7b6c1a120bdf9ba95234c919afb8c285ef1b6ffc428`.
`aapt` y `apksigner verify` finalizaron con exit0. En23 DEX no aparecen los
cuatro prefijos de clases retiradas auditados y sí la proyección nueva.
No es una prueba exhaustiva de todos los strings o caminos runtime.
El binario permanece en cloud; la entrega incluye metadatos, sin APK firmado.
No se instaló ni publicó.

## Evidencia conservada por checkpoint

| Checkpoint | Pruebas | Fallos | Errores | Omitidas | Alcance/límite |
|---|---:|---:|---:|---:|---|
| Room33 | 20 | 0 | 0 | 0 | Checkpoint de preservación, exit0 |
| Final-regression-v2 | 505 | 2 | 0 | 0 | Dos fallos UI del diálogo; no aprobación final |
| Final-regression-v3 | 713 | 28 | 0 | 1 | Expectativas retiradas y worker UI interrumpido |
| Confirmation-dialog-v5 | 229 | 3 | 0 | 1 | AUTO antiguo/captura PixelCopy; worker UI interrumpido |

Los oráculos nativos APRE/TM se portan a confirmación explícita: primero se exige
que AUTO heredado no muta, después se confirma y se comprueban los mismos valores,
identidades, auditoría y protección del trabajo ya registrado. Escalados fisiológicos
retirados deben caducar sin cambiar intensidad, volumen o calendario.

Las capturas de gráfico/procedencia y bienvenida «Ejemplo» se generaron mediante
renderizado nativo Robolectric de la vista y se inspeccionaron visualmente a130 %.
PixelCopy no entrega callbacks en este host; no se usa como evidencia exitosa.
Son fixtures de componentes, no capturas de dispositivo ni aprobación de toda la app.

## Reproducir el flujo cloud

Activar `/workspace/kpkn-env/activate.sh`: JDK21 y SDK36/build-tools36.0.0. JDK17
permanece disponible para arneses independientes; no se reanuda el arnés Rings.
Gradle desde `android-native`: `--no-daemon --console=plain --max-workers=2`.
Los tests focalizados usan heap3GiB y un fork concurrente mediante init privado;
la regresión ampliada rota forks para acotar cachés Room/Compose. No modifica
aserciones ni compuertas. No editar fuentes del checkout mientras compile.

Backend, desde raíz: definir `PYTHONPATH` con raíz del repo **y** `backend`, y ejecutar
`/workspace/kpkn-env/backend-venv/bin/python -m pytest backend/tests -q`.
Catálogo: `python -m unittest discover -s scripts/tests -v` y
`python scripts/compile_exercise_catalog_v2.py --check`.
Retiro: `python scripts/verify_post_rings_retirement.py`.

## Gates externos

[EXTERNAL_GATES.md](EXTERNAL_GATES.md) detalla macOS/Xcode/XCTest, contrato Swift,
migraciones iOS con contenedor sintético y QA físico Z Flip bajo `phone_run` y
`phone.lock`. No hay acceso al dispositivo ni al PC desde cloud. La única app de
QA permitida es `com.example.kpkn.dbg`, KPKN Dev; verificar firma, compatibilidad
Room y respaldo recuperable antes de `-r`. No borrar datos ni tocar la app personal.

El dossier conserva las fuentes aportadas como **no verificadas independientemente**:
PubMed/PMC/Crossref/Europe PMC devolvieron denegación del proxy. No acredita revisión
sistemática, GRADE propio ni promesas fisiológicas. No hay build iOS, XCTest,
QA físico o publicación afirmados a partir de comprobaciones sintácticas.
