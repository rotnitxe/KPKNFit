# 00 — Formato del snapshot AOT de MacroFactor (Nutrition 5.8.9 / Workouts 1.4.1)

Todo lo que sigue está **OBSERVADO** en los binarios salvo donde se marque lo contrario. Rutas:
`<Base>` = `C:\Users\valen\AppData\Local\Temp\claude\C--Users-valen-Documents-KPKNFit--claude-worktrees-kpkn-macrofactor-audit-1344ab\c9399af0-2874-4a5d-a3d2-1d801554de64\scratchpad`.

## 1. Versión de Dart / Flutter

| Dato | Valor | Fuente |
|---|---|---|
| Hash del snapshot (cabecera de `libapp.so`, ambas apps) | `0451907c2eaa8467e848c0067bfe8ed4` | offset `0x2d4` (nu) / `0x214` (wk), 32 caracteres hex tras magic+length+kind |
| Cadena de versión del VM en `libflutter.so` (Nutrition, arm64) | `3.13.3 (stable) (Tue Sep 1 01:07:17 2026 -0700) on "android_arm64"` | `lib\nu_libflutter.so` @ `0x1ef689`; el mismo hash aparece en `0x1dec7d` |
| Cadena de versión en `libflutter.so` (Workouts, arm32) | `3.13.3 (stable) (Tue Sep 1 01:07:17 2026 -0700) on "android_arm"` | `lib\wk_libflutter.so` @ `0x19e4fd`; hash en `0x18d99f` |
| build-id de `libflutter.so` | nu `1164bae6165f15f0ecdcba31ceb73ad2ea73038d`; wk `bc2d81d52fa362c469817269236a35a40d52293b` | `out\nu-flutter.json`, `out\wk-flutter.json` (rea identify-flutter-build) |
| `tools/VERSION` del tag `3.13.3` de dart-lang/sdk | CHANNEL stable, MAJOR 3, MINOR 13, PATCH 3 | `re\ref_src\3.13.3\VERSION` |

Conclusión: ambas apps están compiladas con **Dart 3.13.3 stable** (SDK del 1-sep-2026). El hash se calcula en `tools/make_version.py` como MD5 de 15 ficheros del VM (`app_snapshot.cc`, `app_snapshot.h`, `dart.cc`, `dart_api_impl.cc`, `datastream.h`, `image_snapshot.cc/.h`, `object.cc/.h`, `raw_object.cc/.h`, `snapshot.cc/.h`, `symbols.cc/.h`). No reprodujimos el MD5 (hace falta el árbol git exacto con sus finales de línea); la correspondencia versión↔hash se tomó de la cadena `Version::String()` que el propio `libflutter.so` lleva junto al hash. La versión exacta de **Flutter** no está escrita en los binarios; con Dart 3.13.3 (sep-2026) corresponde al Flutter estable de otoño de 2026 (INFERIDO; no se necesita para parsear).

## 2. Layout ELF y del snapshot

`libapp.so` no es el layout clásico de 4 símbolos (`_kDartVmSnapshotData/Instructions`, `_kDartIsolateSnapshotData/Instructions`). Dart 3.13 escribe **un único snapshot unificado** (VM + isolate) con tres símbolos dinámicos: `_kDartSnapshotData`, `_kDartSnapshotText`, `_kDartSnapshotBuildId`. Por eso `rea inspect-dart-aot` fallaba: busca los nombres antiguos.

| | Nutrition (`nu_libapp.so`, arm64) | Workouts (`wk_libapp.so`, arm32) |
|---|---|---|
| `.rodata` (= `_kDartSnapshotData`) | off/vaddr `0x2c0`, tamaño `0x5fb600` | `0x200`, tamaño `0x61f188` |
| `.text` (= `_kDartSnapshotText`) | vaddr `0x600000`, tamaño `0xf0e8a0` (15,8 MB) | `0x620000`, `0xe8e8f8` |
| `_kDartSnapshotBuildId` (nota GNU) | `0x1c8` (20 bytes) | `0x114` |
| magic `0xdcdcf5f5` | `0x2c0` | `0x200` |
| `length` (int64; `Snapshot::large_length()` = +4) | `0x45ecee` → fin del snapshot agrupado en `0x45efb2` | `0x261f7f` → fin en `0x262183` |
| `kind` (int64) | 2 = `kFullAOT` (enum `kFull, kFullJIT, kFullAOT, kModule, kInvalid`) | 2 |
| cadena de *features* | `product no-asan no-msan no-tsan no-shared_data no-code_comments dwarf_stack_traces arm64 android compressed-pointers` | `… arm android no-compressed-pointers` |
| inicio del stream agrupado (tras el `\0` de features) | `0x369` | `0x2aa` |
| cabecera del stream: num_base_objects, num_objects, num_clusters, instructions_table_len, instruction_table_data_offset | 7, 221 848, 1 721, 12 417, 64 | 7, 199 592, 1 637, 10 813, 64 |
| imagen de datos RO (`Snapshot::DataImage()` = `RoundUp(length,64)`) | `0x45efc0`, tamaño `0x19c900`; contiene **un solo objeto**: la OneByteString con la `InstructionsTable::Data` (57 384 entradas) | `0x2621c0`, tamaño `0x3bd1c8`; contiene la tabla (52 781 entradas) **más** todas las cadenas, PcDescriptors, CodeSourceMap y CompressedStackMaps (en builds sin compressed-pointers esos clusters son "RO data") |
| fase alloc / fill del stream | termina en `0x4f2b8` / `0x443bf9` | `0x47607` / `0x24978c` |
| raíces: tabla de campos estáticos | `0x444ded`, 2 571 entradas (+24 compartidas); 1 689 refs antes (Roots 302+... , tabla de clases 161, ObjectStore 171) → implica 173 stubs en `VM_STUB_CODE_LIST` (163+9 TTS+1 probe) | 2 362 entradas |
| `first_entry_with_code` de la InstructionsTable | 44 967 (de 57 384) | 41 968 (de 52 781) |

Implicaciones observadas:
* `product` + `dwarf_stack_traces` + `no-code_comments`: el precompilador **descarta los objetos `Code`** de la mayoría de funciones (sólo 12 417 de 57 384 unidades de código conservan objeto `Code`) y **el DWARF no está en el APK** (no hay secciones `.debug_*`; los símbolos fueron a un fichero aparte con `--split-debug-info`). Consecuencia: ~75 % del código no tiene nombre de función en el binario.
* Sobreviven 16 271 objetos `Function` (nu) / 15 399 (wk): casi todos closures (9 415), tear-offs (`ImplicitClosureFunction`, 2 747), forwarders dinámicos (`dyn:…`, 1 792), inicializadores de campo (`init:…`, 1 620), `RegularFunction` 427, constructores 52, etc. Sobreviven 2 842 `Field` (nu) y 13 073 clases.
* `Class.functions` y `Class.fields` llegan vacíos (tree shaker); los miembros se reconstruyen por `Function.owner` / `Field.owner`.
* Las cadenas de nombres de clase, librería, script (`package:macrofactor/...dart`), enums, nombres de campo supervivientes, selectores dinámicos y claves JSON están intactas (no hay ofuscación: `--obfuscate` no se usó).

## 3. Método de parseo (enfoque 1, el que funcionó)

Parser propio en Python (sin dependencias) portado leyendo el código del VM del tag **3.13.3** (ficheros descargados como texto de sólo lectura en `re\ref_src\3.13.3\`; no se ejecuta nada de ellos):

* `runtime/vm/app_snapshot.cc` → `Deserializer::Deserialize`, `ReadCluster` (tag de cluster = `Read<uint32_t>` varint: bits 12-31 cid, bit 1 canonical, bit 7 deeply-immutable), los `ReadAlloc/ReadFill` de los ~45 tipos de cluster, `ProgramDeserializationRoots::ReadRoots`, `ReadDispatchTable`, `ReadInstructions`.
* `runtime/vm/datastream.h` → codificación varint propia de Dart: `ReadUnsigned` (marcador final 128), `Read<T>` (marcador 192, con signo), `ReadRefId` (grupos de 7 bits big-endian), tipos de 1 byte crudos.
* `runtime/vm/raw_object.h` → orden de campos puntero por clase (`VISIT_FROM`…`to_snapshot(kFullAOT)`), bits de cabecera de objeto (CardRemembered 0, Canonical 1, NotMarked 2, NewOrEvac 3, AlwaysSet 4, OldAndNotRemembered 5, ShallowImmutable 6, DeeplyImmutable 7, SizeTag 8-11, ClassId 12-31, hash 32-63 en 64 bits), `kind_tag_` de Function (5 bits kind + 9 bits recognized + 2 bits async + flags), `kind_bits_` de Field, `state_bits_` de Class.
* `runtime/vm/class_id.h` → numeración de cids (Class=5, Function=7, Field=11, Code=18, ObjectPool=23, Instance=45, Type=49, Closure=57, Mint=61, Double=62, ConstMap=87, Array=90, OneByteString=94 …, kNumPredefinedCids=176).
* `runtime/vm/snapshot.h`, `image_snapshot.{h,cc}`, `object.h` (`InstructionsTable`, offsets de entrada arm64 = 24/8, arm = 16/0), `object_store.h`, `roots.h`, `symbol_list.h`, `stub_code_list.h`, `compiler/assembler/object_pool_builder.h` (bits de entrada del pool: tipo 0-3, patchable 4, snapshot-behavior 5-7), `constants_arm64.h`/`constants_arm.h` (PP = x27 / r5, DISPATCH_TABLE_REG = x21 / r7).

Validación: la fase *alloc* reproduce exactamente `num_objects` (221 848 / 199 592) y la fase *fill* termina sin error y deja el cursor justo antes de las raíces; todas las longitudes leídas en *fill* coinciden con las de *alloc*; las cadenas decodificadas coinciden con el volcado de `strings`; la tabla de campos estáticos se localiza buscando `ReadUnsigned()==max(field_id)+1` (2 571) seguido de refs válidas; la dispatch table (144 800 entradas nu / 127 513 wk) se decodifica con el esquema recent/repeat de `app_snapshot.cc`.

Enfoques descartados antes de llegar a éste: (a) usar `rea inspect-dart-aot` (falla por los símbolos nuevos); (b) leer el código fuente del VM sólo por WebFetch (el modelo que resume limita las citas a 125 caracteres: inviable para portar formatos binarios); por eso se descargaron los `.cc/.h` como texto (ver `99_limites_y_peticiones.md`, punto sobre descargas).

## 4. Scripts (en `re\tools\`)

| Script | Qué hace |
|---|---|
| `dartsnap.py <libapp.so> <dir>` | ELF mínimo + parser completo del snapshot (alloc+fill), InstructionsTable, resolución `code_index` → rango de código. Escribe `snapshot_info.json`. Es un módulo importable. |
| `dump_model.py <libapp.so> <dir> [--filter=package:macrofactor/]` | Modelo legible: `classes.json` (clase → cid, librería, script, super, interfaces, flags, campos, funciones con rango de código, nombres derivados, valores de enum, instancias const), `functions.json`, `libraries.json`, `pool.json` (object pool global), `static_fields.json`, `roots_info.json`. |
| `codescan.py <libapp.so> <dir>` | "Desensamblado ligero" arm64/arm32 por rango de código: cargas del object pool (incl. `ldr d,[PP]` para doubles y `fmov #imm`), inmediatos (`movz/movk`, `movw/movt`), comparaciones con inmediato, `bl`/`b`, llamadas por dispatch table. Nombres derivados para código descartado a partir de tear-offs y forwarders `dyn:`. Escribe `ranges.pickle`. |
| `report.py <libapp.so> <dir_modelo> <salida.json> <prefijos…>` | Digest por sistema (clases + funciones + constantes + llamadas, 1 salto a rangos sin nombre). |
| `digest_text.py <digest.json> [--lib=…] [--class=…]` | Vista de texto compacta de un digest. |

Salidas: `re\out_nu\`, `re\out_wk\` (modelos completos), `re\digests\` (digests A–E en JSON y texto), `re\clases_nu.json`, `re\clases_wk.json`.

## 5. Qué NO se obtiene con este método

* Nombres de los métodos "normales" (código descartado): sólo se recuperan por vías indirectas (tear-off → llamada directa, forwarder `dyn:` → llamada, inicializador `init:`), unas 3 100 por app.
* Nombres de campos de instancia de clases cuyos `Field` fueron eliminados (la mayoría de modelos): se ven los *slots* (`@2`, `@3`…) y sus valores en instancias const, y las claves JSON de `toJson/fromJson` en el pool, pero no el nombre Dart de cada slot.
* El cuerpo de los algoritmos: hace falta desensamblado real (ver `99_limites_y_peticiones.md`).
