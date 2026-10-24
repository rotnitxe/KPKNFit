#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
dartsnap.py — Parser propio del snapshot AOT de Dart (formato unificado de Dart 3.13.x:
símbolos _kDartSnapshotData / _kDartSnapshotText) para libapp.so de Flutter.

Portado leyendo runtime/vm/app_snapshot.cc, raw_object.h, class_id.h, datastream.h,
image_snapshot.{h,cc}, snapshot.h y object.h del tag 3.13.3 de dart-lang/sdk.

Sólo lectura: no ejecuta nada del binario. Uso:
    python -I dartsnap.py <libapp.so> <dir_salida>

Produce en <dir_salida>: snapshot_info.json, objects.pickle (volcado interno) y
varios JSON derivados (classes.json, functions.json, libraries.json, pool.json...).
"""
import struct, sys, os, json, pickle, collections

# ----------------------------------------------------------------------------
# Class ids (runtime/vm/class_id.h, 3.13.3)
# ----------------------------------------------------------------------------
_INTERNAL_ONLY = """Class PatchClass Function TypeParameters ClosureData FfiTrampolineData Field Script
Library Namespace KernelProgramInfo WeakSerializationReference WeakArray Code Bytecode Instructions
InstructionsSection InstructionsTable ObjectPool PcDescriptors CodeSourceMap CompressedStackMaps
LocalVarDescriptors ExceptionHandlers Context ContextScope Sentinel SingleTargetCache
MonomorphicSmiableCall CallSiteData UnlinkedCall ICData MegamorphicCache SubtypeTestCache LoadingUnit
Error ApiError LanguageError UnhandledException UnwindError""".split()
_INSTANCE_SINGLETONS = """Instance LibraryPrefix TypeArguments AbstractType Type FunctionType RecordType
TypeParameter FinalizerBase Finalizer NativeFinalizer FinalizerEntry Closure Number Integer Smi Mint
Double Bool Float32x4 Int32x4 Float64x2 Record TypedDataBase TypedData ExternalTypedData TypedDataView
Pointer DynamicLibrary Capability ReceivePort SendPort StackTrace SuspendState RegExp WeakProperty
WeakReference MirrorReference FutureOr UserTag TransferableTypedData""".split()
_TYPED_DATA = """Int8Array Uint8Array Uint8ClampedArray Int16Array Uint16Array Int32Array Uint32Array
Int64Array Uint64Array Float32Array Float64Array Float32x4Array Int32x4Array Float64x2Array""".split()
_TD_ELEM = [1, 1, 1, 2, 2, 4, 4, 8, 8, 4, 8, 16, 16, 16]
_FFI = """NativeFunction Int8 Int16 Int32 Int64 Uint8 Uint16 Uint32 Uint64 Float Double Void Handle Bool
NativeType Struct""".split()

CID_NAMES = ["Illegal", "NativePointer", "FreeListElement", "ForwardingCorpse", "Object"]
CID_NAMES += _INTERNAL_ONLY + _INSTANCE_SINGLETONS
CID_NAMES += ["Map", "ConstMap", "Set", "ConstSet", "Array", "ImmutableArray", "GrowableObjectArray",
              "String", "OneByteString", "TwoByteString", "LinkedHashBase"]
CID_NAMES += ["Ffi" + n for n in _FFI]
for _n in _TYPED_DATA:
    CID_NAMES += ["TypedData" + _n, "TypedData" + _n + "View", "ExternalTypedData" + _n,
                  "UnmodifiableTypedData" + _n + "View"]
CID_NAMES += ["ByteDataView", "UnmodifiableByteDataView", "ByteBuffer", "Null", "Dynamic", "Void", "Never"]
CID = {n: i for i, n in enumerate(CID_NAMES)}
kNumPredefinedCids = len(CID_NAMES)
kFirstTypedDataCid = CID["TypedDataInt8Array"]
kLastTypedDataCid = CID["UnmodifiableTypedDataFloat64x2ArrayView"]
kDeltaEncodedTypedDataCid = CID["NativePointer"]  # app_snapshot.cc:72
kTopLevelCidOffset = (1 << 20)

def is_typed_data_base(c): return kFirstTypedDataCid <= c <= kLastTypedDataCid
def is_typed_data(c): return is_typed_data_base(c) and (c - kFirstTypedDataCid) % 4 == 0
def is_typed_data_view(c): return c == CID["ByteDataView"] or (is_typed_data_base(c) and (c - kFirstTypedDataCid) % 4 == 1)
def is_external_typed_data(c): return is_typed_data_base(c) and (c - kFirstTypedDataCid) % 4 == 2
def is_unmodifiable_view(c): return c == CID["UnmodifiableByteDataView"] or (is_typed_data_base(c) and (c - kFirstTypedDataCid) % 4 == 3)
def td_elem_size(c):
    return _TD_ELEM[(c - kFirstTypedDataCid) // 4]
FFI_TYPE_MARKER_CIDS = set(CID["Ffi" + n] for n in _FFI[1:14])  # Int8..Bool

FUNCTION_KINDS = """RegularFunction ClosureFunction ImplicitClosureFunction GetterFunction SetterFunction
Constructor ImplicitGetter ImplicitSetter ImplicitStaticGetter FieldInitializer MethodExtractor
NoSuchMethodDispatcher InvokeFieldDispatcher IrregexpFunction DynamicInvocationForwarder FfiTrampoline
RecordFieldGetter""".split()
# Function.kind_tag_: KindBits(5) | RecognizedBits(9: BitLength(460-1)) | ModifierBits(2) | bits...
FUNC_KIND_BITS = 5
FUNC_RECOGNIZED_BITS = 9
FUNC_MODIFIER_BITS = 2
FUNC_FLAG_NAMES = """Static Const Abstract Reflectable Visible Debuggable Intrinsic Native External
PolymorphicTarget HasPragma IsSynthetic IsExtensionMember IsExtensionTypeMember IsDynamicallyCallable
Inlinable""".split()
FIELD_FLAG_NAMES = """Const Static Final HasNontrivialInitializer Unboxed Reflectable
InitializerChangedAfterInitialization HasPragma Covariant GenericCovariantImpl IsLate IsExtensionMember
IsExtensionTypeMember NeedsLoadGuard HasInitializer Shared NoSanitizeThread HasDeeplyImmutableType
IsDynamicallyCallable""".split()
CLASS_FLAG_LAYOUT = [("Const", 1), ("Implemented", 1), ("ClassFinalized", 2), ("ClassLoading", 2),
                     ("Abstract", 1), ("SynthesizedClass", 1), ("FieldsMarkedNullable", 1), ("Enum", 1),
                     ("TransformedMixinApplication", 1), ("IsAllocated", 1), ("IsLoaded", 1),
                     ("HasPragma", 1), ("Sealed", 1), ("MixinClass", 1), ("BaseClass", 1),
                     ("InterfaceClass", 1), ("Final", 1), ("IsIsolateUnsendable", 1),
                     ("IsIsolateUnsendableDueToPragma", 1), ("IsDeeplyImmutable", 1),
                     ("IsFutureSubtype", 1), ("CanBeFuture", 1), ("HasDynamicallyExtendableSubtypes", 1),
                     ("IsDeclaredInBytecode", 1)]

def decode_function_kind_tag(v):
    kind = v & ((1 << FUNC_KIND_BITS) - 1)
    rec = (v >> FUNC_KIND_BITS) & ((1 << FUNC_RECOGNIZED_BITS) - 1)
    mod = (v >> (FUNC_KIND_BITS + FUNC_RECOGNIZED_BITS)) & 3
    base = FUNC_KIND_BITS + FUNC_RECOGNIZED_BITS + FUNC_MODIFIER_BITS
    flags = [n for i, n in enumerate(FUNC_FLAG_NAMES) if (v >> (base + i)) & 1]
    return (FUNCTION_KINDS[kind] if kind < len(FUNCTION_KINDS) else "?%d" % kind), rec, mod, flags

def decode_field_kind_bits(v):
    return [n for i, n in enumerate(FIELD_FLAG_NAMES) if (v >> i) & 1]

def decode_class_state_bits(v):
    out = {}
    pos = 0
    for name, w in CLASS_FLAG_LAYOUT:
        val = (v >> pos) & ((1 << w) - 1)
        pos += w
        if val:
            out[name] = val
    return out

# ----------------------------------------------------------------------------
# ELF (mínimo, sin dependencias)
# ----------------------------------------------------------------------------
class Elf:
    def __init__(self, data):
        self.data = data
        assert data[:4] == b"\x7fELF"
        self.is64 = data[4] == 2
        if self.is64:
            (self.e_type, self.e_machine, _, self.e_entry, self.e_phoff, self.e_shoff, _, _, self.e_phentsize,
             self.e_phnum, self.e_shentsize, self.e_shnum, self.e_shstrndx) = struct.unpack_from("<HHIQQQIHHHHHH", data, 16)
        else:
            (self.e_type, self.e_machine, _, self.e_entry, self.e_phoff, self.e_shoff, _, _, self.e_phentsize,
             self.e_phnum, self.e_shentsize, self.e_shnum, self.e_shstrndx) = struct.unpack_from("<HHIIIIIHHHHHH", data, 16)
        self.sections = []
        for i in range(self.e_shnum):
            off = self.e_shoff + i * self.e_shentsize
            if self.is64:
                sh = struct.unpack_from("<IIQQQQIIQQ", data, off)
            else:
                sh = struct.unpack_from("<IIIIIIIIII", data, off)
            self.sections.append(dict(name_idx=sh[0], type=sh[1], flags=sh[2], addr=sh[3], offset=sh[4],
                                      size=sh[5], link=sh[6], info=sh[7], entsize=sh[9]))
        strtab = self.sections[self.e_shstrndx]
        for s in self.sections:
            s["name"] = self._cstr(strtab["offset"] + s["name_idx"])
        self.segments = []
        for i in range(self.e_phnum):
            off = self.e_phoff + i * self.e_phentsize
            if self.is64:
                p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = struct.unpack_from("<IIQQQQQQ", data, off)
            else:
                p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_flags, p_align = struct.unpack_from("<IIIIIIII", data, off)
            self.segments.append(dict(type=p_type, flags=p_flags, offset=p_offset, vaddr=p_vaddr, filesz=p_filesz, memsz=p_memsz, align=p_align))
        self.symbols = {}
        dynsym = [s for s in self.sections if s["name"] == ".dynsym"]
        dynstr = [s for s in self.sections if s["name"] == ".dynstr"]
        if dynsym and dynstr:
            ds, dstr = dynsym[0], dynstr[0]
            ent = 24 if self.is64 else 16
            for i in range(ds["size"] // ent):
                o = ds["offset"] + i * ent
                if self.is64:
                    st_name, st_info, st_other, st_shndx, st_value, st_size = struct.unpack_from("<IBBHQQ", data, o)
                else:
                    st_name, st_value, st_size, st_info, st_other, st_shndx = struct.unpack_from("<IIIBBH", data, o)
                name = self._cstr(dstr["offset"] + st_name)
                if name:
                    self.symbols[name] = dict(value=st_value, size=st_size, shndx=st_shndx)

    def _cstr(self, off):
        e = self.data.index(b"\0", off)
        return self.data[off:e].decode("latin-1")

    def vaddr_to_off(self, va):
        for seg in self.segments:
            if seg["type"] == 1 and seg["vaddr"] <= va < seg["vaddr"] + seg["filesz"]:
                return va - seg["vaddr"] + seg["offset"]
        raise ValueError("vaddr %#x not mapped" % va)

# ----------------------------------------------------------------------------
# ReadStream (runtime/vm/datastream.h)
# ----------------------------------------------------------------------------
class Stream:
    __slots__ = ("d", "p", "end")
    def __init__(self, data, pos, end):
        self.d = data; self.p = pos; self.end = end

    def byte(self):
        b = self.d[self.p]; self.p += 1
        return b

    def read_unsigned(self):
        # ReadUnsigned<T>: end marker 128
        b = self.byte()
        if b > 127:
            return b - 128
        r = 0; s = 0
        while True:
            r |= b << s; s += 7
            b = self.byte()
            if b > 127:
                return r | ((b - 128) << s)

    def read_varint(self, nbytes, signed):
        # Read<T>(kEndByteMarker=192); tipos de 1 byte se leen crudos (Raw<1,T>)
        if nbytes == 1:
            b = self.byte()
            if signed and b >= 128:
                b -= 256
            return b
        bits = 8 * nbytes
        mask = (1 << bits) - 1
        b = self.byte()
        if b > 127:
            v = (b - 192) & mask
        else:
            r = 0; s = 0
            while True:
                r |= b << s; s += 7
                b = self.byte()
                if b > 127:
                    v = (r | (((b - 192) & mask) << s)) & mask
                    break
        if signed and v >= (1 << (bits - 1)):
            v -= (1 << bits)
        return v

    def i8(self): return self.read_varint(1, True)
    def u8(self): return self.read_varint(1, False)
    def i16(self): return self.read_varint(2, True)
    def u16(self): return self.read_varint(2, False)
    def i32(self): return self.read_varint(4, True)
    def u32(self): return self.read_varint(4, False)
    def i64(self): return self.read_varint(8, True)
    def u64(self): return self.read_varint(8, False)
    def f64(self):
        return struct.unpack("<d", struct.pack("<Q", self.read_varint(8, False)))[0]

    def read_ref_id(self):
        v = 0
        while True:
            b = self.byte()
            v = (v << 7) | (b & 0x7f)
            if b & 0x80:
                return v

    def read_bytes(self, n):
        r = self.d[self.p:self.p + n]; self.p += n
        return r

    def align(self, a):
        self.p = (self.p + a - 1) // a * a

    def advance(self, n):
        self.p += n

# ----------------------------------------------------------------------------
# Objetos del snapshot: dicts con 'c' (cid) y campos; las referencias se guardan
# como índices de la tabla refs (ints).
# ----------------------------------------------------------------------------
class Obj(dict):
    __slots__ = ()

BASE_OBJECTS = ["null", "false", "true", "sentinel", "unknown_constant", "non_constant", "optimized_out"]

class Deserializer:
    def __init__(self, data, elf, arch_bits, compressed=None, verbose=True):
        self.data = data
        self.elf = elf
        self.bits = arch_bits            # 32 o 64 (host word)
        self.word = arch_bits // 8
        self.obj_align_log2 = (arch_bits // 32) + 2   # 64-bit: 16 (log2 4); 32-bit: 8 (log2 3)
        self.verbose = verbose
        self.log = []
        sym = elf.symbols["_kDartSnapshotData"]
        self.snap_off = elf.vaddr_to_off(sym["value"])
        self.snap_size = sym["size"]
        tsym = elf.symbols["_kDartSnapshotText"]
        self.text_vaddr = tsym["value"]
        self.text_off = elf.vaddr_to_off(tsym["value"])
        self.text_size = tsym["size"]
        magic, length, kind = struct.unpack_from("<iqq", data, self.snap_off)
        assert magic == -0x23230a0b, "magic 0x%08x" % (magic & 0xffffffff)
        self.length = length + 4     # Snapshot::large_length()
        self.kind = kind
        self.version = data[self.snap_off + 20:self.snap_off + 52].decode()
        fe = data.index(b"\0", self.snap_off + 52)
        self.features = data[self.snap_off + 52:fe].decode()
        feats = self.features.split()
        if compressed is None:
            compressed = ("compressed-pointers" in feats)
        self.compressed = compressed     # compressed-pointers
        self.cword = 4 if compressed else self.word   # kCompressedWordSize
        self.clustered_start = fe + 1
        self.clustered_end = self.snap_off + self.length
        # Imagen de datos RO: Snapshot::DataImage() = Addr() + RoundUp(length, kObjectStartAlignment=64)
        self.data_image_off = self.snap_off + ((self.length + 63) // 64 * 64)
        self.data_image_size = struct.unpack_from("<Q" if arch_bits == 64 else "<I", data, self.data_image_off)[0]
        self.s = Stream(data, self.clustered_start, self.clustered_end)
        self.refs = [None]  # índice 0 no se usa (kUnreachableReference)
        self.clusters = []
        self.code_start_index = 0
        self.code_stop_index = 0
        self.instructions_index = 0
        self.lazy_compile_index = 0
        self.unknown_dart_code_index = 0
        self.codes_by_cluster_index = []

    # --- helpers
    def RU(self): return self.s.read_unsigned()
    def RR(self): return self.s.read_ref_id()
    def next_index(self): return len(self.refs)
    def assign(self, o):
        self.refs.append(o)
        return len(self.refs) - 1

    def ro_object_at(self, offset):
        """Objeto en la imagen de datos RO (sólo builds sin compressed pointers lo usan)."""
        base = self.data_image_off + offset
        d = self.data
        if self.bits == 64:
            tags = struct.unpack_from("<Q", d, base)[0]
        else:
            tags = struct.unpack_from("<I", d, base)[0]
        cid = (tags >> 12) & 0xfffff
        o = Obj(c=cid, ro=offset)
        if cid in (CID["OneByteString"], CID["TwoByteString"]):
            if self.bits == 64:
                length = struct.unpack_from("<I", d, base + 8)[0] >> 1
                dp = base + 16
                hash_ = tags >> 32
            else:
                hash_ = struct.unpack_from("<I", d, base + 4)[0] >> 1
                length = struct.unpack_from("<I", d, base + 8)[0] >> 1
                dp = base + 12
            if cid == CID["OneByteString"]:
                return d[dp:dp + length].decode("latin-1")
            return d[dp:dp + 2 * length].decode("utf-16-le", "replace")
        if cid in (CID["PcDescriptors"], CID["CodeSourceMap"]):
            length = struct.unpack_from("<Q" if self.bits == 64 else "<I", d, base + self.word)[0]
            o["len"] = length
            return o
        if cid == CID["CompressedStackMaps"]:
            fas = struct.unpack_from("<I", d, base + self.word)[0]
            o["len"] = fas >> 2
            return o
        return o

    # --- lectura de clusters
    def read_cluster(self):
        tags = self.s.u32()
        cid = (tags >> 12) & 0xfffff
        canonical = bool(tags & 2)
        immutable = bool(tags & 0x80)
        cl = Obj(cid=cid, canonical=canonical, immutable=immutable, pos=self.s.p)
        if cid >= kNumPredefinedCids or cid == CID["Instance"] or cid in FFI_TYPE_MARKER_CIDS:
            cl["kind"] = "Instance"
        elif is_typed_data_view(cid) or is_unmodifiable_view(cid):
            cl["kind"] = "TypedDataView"
        elif is_external_typed_data(cid):
            cl["kind"] = "ExternalTypedData"
        elif is_typed_data(cid):
            cl["kind"] = "TypedData"
        elif (not self.compressed) and cid in (CID["PcDescriptors"], CID["CodeSourceMap"], CID["CompressedStackMaps"],
                                               CID["OneByteString"], CID["TwoByteString"], CID["String"]):
            cl["kind"] = "ROData"
        elif cid == kDeltaEncodedTypedDataCid:
            cl["kind"] = "DeltaEncodedTypedData"
        elif cid in (CID["Int32x4"], CID["Float32x4"], CID["Float64x2"]):
            cl["kind"] = "Simd128"
        elif cid in (CID["ConstMap"],):
            cl["kind"] = "Map"
        elif cid in (CID["ConstSet"],):
            cl["kind"] = "Set"
        elif cid in (CID["Array"], CID["ImmutableArray"]):
            cl["kind"] = "Array"
        else:
            cl["kind"] = CID_NAMES[cid]
        return cl

    def canonical_layout(self, cl):
        # CanonicalSetDeserializationCluster::BuildCanonicalSetFromLayout (unidad raíz)
        if not cl["canonical"]:
            return
        table_length = self.RU()
        first = self.RU()
        gaps = []
        for i in range(cl["start"] + first, cl["stop"]):
            gaps.append(self.RU())
        cl["canon"] = (table_length, first, len(gaps))

    def alloc_fixed(self, cl, extra=None):
        cl["start"] = self.next_index()
        n = self.RU()
        for i in range(n):
            self.assign(Obj(c=cl["cid"]))
        cl["stop"] = self.next_index()

    def alloc_lengths(self, cl, key="len"):
        cl["start"] = self.next_index()
        n = self.RU()
        for i in range(n):
            o = Obj(c=cl["cid"]); o[key] = self.RU()
            self.assign(o)
        cl["stop"] = self.next_index()

    def read_alloc(self, cl):
        k = cl["kind"]
        cid = cl["cid"]
        if k in ("Class", "TypeParameters", "PatchClass", "Function", "ClosureData", "FfiTrampolineData", "Field",
                 "Script", "Library", "Namespace", "UnlinkedCall", "ICData", "MegamorphicCache", "SubtypeTestCache",
                 "LoadingUnit", "ApiError", "LanguageError", "UnhandledException", "UnwindError", "LibraryPrefix",
                 "Double", "Simd128", "GrowableObjectArray", "TypedDataView", "ExternalTypedData", "StackTrace",
                 "RegExp", "WeakProperty", "Map", "Set"):
            self.alloc_fixed(cl)
        elif k in ("Type", "FunctionType", "RecordType", "TypeParameter"):
            self.alloc_fixed(cl)
            self.canonical_layout(cl)
        elif k == "TypeArguments":
            self.alloc_lengths(cl)
            self.canonical_layout(cl)
        elif k == "Code":
            cl["start"] = self.next_index()
            self.code_start_index = cl["start"]
            n = self.RU()
            for i in range(n):
                self.assign(Obj(c=cid, state_bits=self.s.i32(), deferred=False))
            cl["stop"] = self.next_index()
            self.code_stop_index = cl["stop"]
            cl["dstart"] = self.next_index()
            n2 = self.RU()
            for i in range(n2):
                self.assign(Obj(c=cid, state_bits=self.s.i32(), deferred=True))
            cl["dstop"] = self.next_index()
        elif k in ("ObjectPool", "PcDescriptors", "CodeSourceMap", "CompressedStackMaps", "LocalVarDescriptors",
                   "ExceptionHandlers", "Context", "ContextScope", "Closure", "Record", "TypedData", "Array",
                   "WeakArray", "DeltaEncodedTypedData"):
            self.alloc_lengths(cl)
        elif k == "Instance":
            cl["start"] = self.next_index()
            n = self.RU()
            cl["nfo_words"] = self.s.i32()
            cl["size_words"] = self.s.i32()
            for i in range(n):
                self.assign(Obj(c=cid))
            cl["stop"] = self.next_index()
        elif k == "Mint":
            cl["start"] = self.next_index()
            n = self.RU()
            for i in range(n):
                self.assign(self.s.i64())
            cl["stop"] = self.next_index()
        elif k == "String":
            cl["start"] = self.next_index()
            n = self.RU()
            for i in range(n):
                enc = self.RU()
                self.assign(Obj(c=CID["TwoByteString"] if enc & 1 else CID["OneByteString"], len=enc >> 1))
            cl["stop"] = self.next_index()
            self.canonical_layout(cl)
        elif k == "ROData":
            cl["start"] = self.next_index()
            n = self.RU()
            running = 0
            for i in range(n):
                running += self.RU() << self.obj_align_log2
                self.assign(self.ro_object_at(running))
            cl["stop"] = self.next_index()
            if cid == CID["String"]:
                self.canonical_layout(cl)
        else:
            raise NotImplementedError("alloc %s cid=%d" % (k, cid))

    def read_from_to(self, o, names):
        for nm in names:
            o[nm] = self.RR()

    def read_fill(self, cl):
        k = cl["kind"]; cid = cl["cid"]; s = self.s
        rng = range(cl["start"], cl["stop"])
        R = self.refs
        if k == "Class":
            for i in rng:
                o = R[i]
                self.read_from_to(o, ["name", "functions", "functions_hash_table", "fields", "offset_in_words_to_field",
                                      "interfaces", "script", "library", "type_parameters", "super_type", "constants",
                                      "declaration_type", "invocation_dispatcher_cache"])
                o["id"] = s.i32()
                o["instance_size_words"] = s.i32()
                o["next_field_offset_words"] = s.i32()
                o["type_args_field_offset_words"] = s.i32()
                o["num_type_arguments"] = s.i16()
                o["num_native_fields"] = s.u16()
                o["state_bits"] = s.u32()
                if o["id"] < kTopLevelCidOffset:
                    o["unboxed_fields_bitmap"] = s.read_unsigned()
        elif k == "TypeParameters":
            for i in rng:
                self.read_from_to(R[i], ["names", "flags", "bounds", "defaults"])
        elif k == "TypeArguments":
            for i in rng:
                o = R[i]
                n = s.read_unsigned()
                o["hash"] = s.i32(); o["nullability"] = s.read_unsigned()
                o["instantiations"] = self.RR()
                o["types"] = [self.RR() for _ in range(n)]
        elif k == "PatchClass":
            for i in rng:
                self.read_from_to(R[i], ["wrapped_class", "script"])
        elif k == "Function":
            for i in rng:
                o = R[i]
                self.read_from_to(o, ["name", "owner", "signature", "data"])
                o["code_index"] = s.read_unsigned()
                o["kind_tag"] = s.u32()
        elif k == "ClosureData":
            for i in rng:
                o = R[i]
                o["parent_function"] = self.RR(); o["closure"] = self.RR(); o["packed"] = s.read_unsigned()
        elif k == "FfiTrampolineData":
            for i in rng:
                o = R[i]
                self.read_from_to(o, ["signature_type", "c_signature", "callback_target", "callback_exceptional_return"])
                o["callback_id"] = s.i32(); o["ffi_function_kind"] = s.u8()
        elif k == "Field":
            for i in rng:
                o = R[i]
                self.read_from_to(o, ["name", "owner", "type", "initializer_function"])
                o["kind_bits"] = s.u32()
                o["host_offset_or_field_id"] = self.RR()
        elif k == "Script":
            for i in rng:
                o = R[i]
                self.read_from_to(o, ["url"])
                o["kernel_script_index"] = s.i32()
        elif k == "Library":
            for i in rng:
                o = R[i]
                self.read_from_to(o, ["name", "url", "private_key", "dictionary", "metadata", "toplevel_class",
                                      "used_scripts", "loading_unit", "imports", "exports"])
                o["index"] = s.i32(); o["num_imports"] = s.u16(); o["load_state"] = s.i8(); o["flags"] = s.u8()
        elif k == "Namespace":
            for i in rng:
                self.read_from_to(R[i], ["target"])
        elif k == "Code":
            self.lazy_compile_index = self.RR()
            self.unknown_dart_code_index = self.RR()
            for i in list(rng) + list(range(cl["dstart"], cl["dstop"])):
                o = R[i]
                if not o["deferred"]:
                    pi = s.read_unsigned()
                    o["unchecked_offset"] = pi >> 1
                    o["has_monomorphic_entry"] = bool(pi & 1)
                    o["instr_index"] = self.instructions_index
                    self.instructions_index += 1
                self.read_from_to(o, ["owner", "exception_handlers", "pc_descriptors", "catch_entry",
                                      "inlined_id_to_function", "code_source_map"])
        elif k == "ObjectPool":
            for i in rng:
                o = R[i]
                n = s.read_unsigned()
                entries = []
                for j in range(n):
                    bits = s.u8()
                    typ = bits & 0xf
                    beh = (bits >> 5) & 7
                    if beh != 0:
                        entries.append(("beh%d" % beh, None, bits))
                        continue
                    if typ == 1:      # kTaggedObject
                        entries.append(("obj", self.RR(), bits))
                    elif typ == 0:    # kImmediate
                        entries.append(("imm", s.read_varint(self.word, True), bits))
                    elif typ == 2:    # kNativeFunction
                        entries.append(("native", None, bits))
                    else:
                        raise ValueError("pool entry type %d" % typ)
                o["entries"] = entries
        elif k in ("PcDescriptors", "CodeSourceMap"):
            for i in rng:
                o = R[i]; n = s.read_unsigned(); o["data_off"] = s.p; s.advance(n)
        elif k == "CompressedStackMaps":
            for i in rng:
                o = R[i]; fas = s.read_unsigned(); n = fas >> 2; o["flags"] = fas & 3; o["data_off"] = s.p; s.advance(n)
        elif k == "LocalVarDescriptors":
            for i in rng:
                o = R[i]; n = s.read_unsigned()
                o["names"] = [self.RR() for _ in range(n)]
                for j in range(n):
                    s.i32(); s.i32(); s.i32(); s.i32(); s.i64()
        elif k == "ExceptionHandlers":
            for i in rng:
                o = R[i]
                packed = s.read_unsigned(); n = packed >> 1
                o["handled_types_data"] = self.RR()
                hs = []
                for j in range(n):
                    hs.append((s.u32(), s.i16(), s.i8(), s.i8(), s.i8()))
                o["handlers"] = hs
        elif k == "Context":
            for i in rng:
                o = R[i]; n = s.read_unsigned(); o["parent"] = self.RR(); o["vars"] = [self.RR() for _ in range(n)]
        elif k == "ContextScope":
            for i in rng:
                o = R[i]; n = s.read_unsigned(); o["is_implicit"] = s.u8(); o["slots"] = [self.RR() for _ in range(10 * n)]
        elif k == "UnlinkedCall":
            for i in rng:
                o = R[i]; self.read_from_to(o, ["target_name", "args_descriptor"]); o["can_patch"] = s.u8()
        elif k == "ICData":
            for i in rng:
                o = R[i]; self.read_from_to(o, ["target_name", "args_descriptor", "entries"]); o["state_bits"] = s.i32()
        elif k == "MegamorphicCache":
            for i in rng:
                o = R[i]; self.read_from_to(o, ["target_name", "args_descriptor", "buckets", "mask"]); o["filled"] = s.i32()
        elif k == "SubtypeTestCache":
            for i in rng:
                o = R[i]; o["cache"] = self.RR(); o["num_inputs"] = s.u32(); o["num_occupied"] = s.u32()
        elif k == "LoadingUnit":
            for i in rng:
                o = R[i]; o["parent"] = self.RR(); o["id"] = s.read_varint(self.word, True)
        elif k == "ApiError":
            for i in rng: self.read_from_to(R[i], ["message"])
        elif k == "LanguageError":
            for i in rng:
                o = R[i]; self.read_from_to(o, ["previous_error", "script", "message", "formatted_message"])
                o["token_pos"] = s.i32(); o["report_after_token"] = s.u8(); o["kind"] = s.i8()
        elif k == "UnhandledException":
            for i in rng: self.read_from_to(R[i], ["exception", "stacktrace"])
        elif k == "UnwindError":
            for i in rng:
                o = R[i]; self.read_from_to(o, ["message"]); o["is_user_initiated"] = s.u8()
        elif k == "Instance":
            bitmap = s.read_unsigned()
            cl["unboxed_bitmap"] = bitmap
            hdr = self.word              # Instance::NextFieldOffset() == sizeof(UntaggedObject)
            nfo = cl["nfo_words"] * self.cword
            nreads = self.bits // 32     # ReadWordWith32BitReads
            for i in rng:
                o = R[i]
                fields = []
                off = hdr
                while off < nfo:
                    if (bitmap >> (off // self.cword)) & 1:
                        v = 0
                        for j in range(nreads):
                            v |= s.u32() << (32 * j)
                        fields.append(("u", v))
                    else:
                        fields.append(self.RR())
                    off += self.cword
                o["f"] = fields
        elif k == "LibraryPrefix":
            for i in rng:
                o = R[i]; self.read_from_to(o, ["name", "imports"]); o["num_imports"] = s.u16(); o["is_deferred_load"] = s.u8()
        elif k == "Type":
            for i in rng:
                o = R[i]; self.read_from_to(o, ["type_test_stub", "hash", "arguments"]); o["flags"] = s.read_unsigned()
        elif k == "FunctionType":
            for i in rng:
                o = R[i]
                self.read_from_to(o, ["type_test_stub", "hash", "type_parameters", "result_type", "parameter_types", "named_parameter_names"])
                o["flags"] = s.u8(); o["packed_parameter_counts"] = s.u32(); o["packed_type_parameter_counts"] = s.u16()
        elif k == "RecordType":
            for i in rng:
                o = R[i]; self.read_from_to(o, ["type_test_stub", "hash", "shape", "field_types"]); o["flags"] = s.u8()
        elif k == "TypeParameter":
            for i in rng:
                o = R[i]; self.read_from_to(o, ["type_test_stub", "hash", "owner"])
                o["base"] = s.u16(); o["index"] = s.u16(); o["flags"] = s.u8()
        elif k == "Closure":
            for i in rng:
                o = R[i]; n = s.read_unsigned()
                o["length_and_flags"] = self.RR(); o["hash"] = self.RR(); o["function"] = self.RR()
                o["data"] = [self.RR() for _ in range(n)]
        elif k == "Mint":
            pass
        elif k == "Double":
            for i in rng:
                R[i] = s.f64()
        elif k == "Simd128":
            for i in rng:
                R[i]["raw"] = s.read_bytes(16)
        elif k == "Record":
            for i in rng:
                o = R[i]; shape = s.read_unsigned(); n = shape & 0xffff
                o["shape"] = shape; o["fields"] = [self.RR() for _ in range(n)]
        elif k == "TypedData":
            es = td_elem_size(cid)
            for i in rng:
                o = R[i]; n = s.read_unsigned(); o["len"] = n; o["data_off"] = s.p; s.advance(n * es)
        elif k == "TypedDataView":
            for i in rng:
                self.read_from_to(R[i], ["length", "typed_data", "offset_in_bytes"])
        elif k == "ExternalTypedData":
            es = td_elem_size(cid - 2)
            for i in rng:
                o = R[i]; n = s.read_unsigned(); o["len"] = n
                s.align(8); o["data_off"] = s.p; s.advance(n * es)
        elif k == "DeltaEncodedTypedData":
            for i in rng:
                o = R[i]; enc = s.read_unsigned(); n = enc >> 1
                o["c"] = CID["TypedDataUint32Array"] if enc & 1 else CID["TypedDataUint16Array"]
                vals = []; v = 0
                for j in range(n):
                    v += s.read_unsigned(); vals.append(v)
                o["values"] = vals
        elif k == "StackTrace":
            for i in rng: self.read_from_to(R[i], ["async_link", "code_array", "pc_offset_array"])
        elif k == "RegExp":
            for i in rng:
                o = R[i]; self.read_from_to(o, ["capture_name_map", "pattern", "one_byte", "two_byte", "one_byte_sticky", "two_byte_sticky"])
                o["num_one_byte_registers"] = s.i32(); o["num_two_byte_registers"] = s.i32(); o["flags"] = s.u32()
        elif k == "WeakProperty":
            for i in rng: self.read_from_to(R[i], ["key", "value"])
        elif k in ("Map", "Set"):
            for i in rng: self.read_from_to(R[i], ["type_arguments", "hash_mask", "data", "used_data", "deleted_keys"])
        elif k == "Array":
            for i in rng:
                o = R[i]; n = s.read_unsigned(); o["type_arguments"] = self.RR(); o["e"] = [self.RR() for _ in range(n)]
        elif k == "WeakArray":
            for i in rng:
                o = R[i]; n = s.read_unsigned(); o["e"] = [self.RR() for _ in range(n)]
        elif k == "String":
            for i in rng:
                o = R[i]; enc = s.read_unsigned(); n = enc >> 1
                if enc & 1:
                    R[i] = s.read_bytes(2 * n).decode("utf-16-le", "replace")
                else:
                    R[i] = s.read_bytes(n).decode("latin-1")
        elif k == "GrowableObjectArray":
            for i in rng: self.read_from_to(R[i], ["type_arguments", "length", "data"])
        elif k == "ROData":
            pass
        else:
            raise NotImplementedError("fill %s" % k)

    def deserialize(self):
        s = self.s
        self.num_base_objects = s.read_unsigned()
        self.num_objects = s.read_unsigned()
        self.num_clusters = s.read_unsigned()
        self.instructions_table_len = s.read_unsigned()
        self.instruction_table_data_offset = s.read_unsigned()
        for b in BASE_OBJECTS:
            self.assign(Obj(c=-1, base=b))
        assert self.num_base_objects == len(BASE_OBJECTS), self.num_base_objects
        self.refs[1] = None       # null
        self.refs[2] = False
        self.refs[3] = True
        for i in range(self.num_clusters):
            cl = self.read_cluster()
            self.read_alloc(cl)
            self.clusters.append(cl)
        assert len(self.refs) - 1 == self.num_objects, (len(self.refs) - 1, self.num_objects)
        self.alloc_end = s.p
        for i, cl in enumerate(self.clusters):
            cl["fill_pos"] = s.p
            self.read_fill(cl)
        self.fill_end = s.p
        # Raíces (ProgramDeserializationRoots::ReadRoots): no las necesitamos; registramos la posición.
        self.roots_pos = s.p
        self.read_instructions_table()

    def read_instructions_table(self):
        # OneByteString en la imagen de datos (offset instruction_table_data_offset) con la tabla:
        # struct Data { uint32 canonical_stack_map_entries_offset; uint32 length; uint32 first_entry_with_code; uint32 padding; DataEntry[] }
        base = self.data_image_off + self.instruction_table_data_offset
        dp = base + (16 if self.bits == 64 else 12)
        cs, length, few, pad = struct.unpack_from("<IIII", self.data, dp)
        self.it_first_entry_with_code = few
        self.it_length = length
        self.it_entries = [struct.unpack_from("<II", self.data, dp + 16 + 8 * i) for i in range(length)]
        self.it_data_off = dp

    # --- utilidades de post-proceso
    def entry_offsets(self):
        # Instructions::k*EntryOffsetAOT según arquitectura (object.h 3.13.3)
        if self.elf.e_machine == 0xb7:   # AArch64
            return 8, 24
        if self.elf.e_machine == 0x28:   # ARM
            return 0, 16
        raise ValueError("arquitectura no soportada")

    def code_payload(self, code_obj):
        """(payload_start_vaddr, payload_end_vaddr) para un objeto Code no diferido."""
        idx = self.it_first_entry_with_code + code_obj["instr_index"]
        start = self.text_vaddr + self.it_entries[idx][0]
        end = self.text_vaddr + (self.it_entries[idx + 1][0] if idx + 1 < self.it_length else self.text_size)
        return start, end

    def function_code(self, f):
        """Resuelve code_index de una Function (AOT, unidad raíz)."""
        ci = f["code_index"]
        if ci == 0:
            return ("lazy", None, None)
        ci -= 1
        few = self.it_first_entry_with_code
        if ci < few:
            start = self.text_vaddr + self.it_entries[ci][0]
            end = self.text_vaddr + self.it_entries[ci + 1][0]
            return ("discarded", start, end)
        cluster_index = ci - few
        code_ref = self.code_start_index + cluster_index
        return ("code", code_ref, None)

    def str_at(self, ref):
        o = self.refs[ref] if ref is not None and 0 < ref < len(self.refs) else None
        return o if isinstance(o, str) else None


def load(path, verbose=True):
    data = open(path, "rb").read()
    elf = Elf(data)
    bits = 64 if elf.is64 else 32
    d = Deserializer(data, elf, bits)
    d.deserialize()
    return d


def main():
    path, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    d = load(path)
    info = dict(
        file=os.path.basename(path), elf_machine=hex(d.elf.e_machine), bits=d.bits, compressed_pointers=d.compressed,
        snapshot_data_off=hex(d.snap_off), snapshot_data_size=d.snap_size, snapshot_text_vaddr=hex(d.text_vaddr),
        snapshot_text_size=d.text_size, magic="0xdcdcf5f5", kind=d.kind, version_hash=d.version, features=d.features,
        clustered_start=hex(d.clustered_start), clustered_end=hex(d.clustered_end), length_field=d.length - 4,
        data_image_off=hex(d.data_image_off), data_image_size=d.data_image_size,
        num_base_objects=d.num_base_objects, num_objects=d.num_objects, num_clusters=d.num_clusters,
        instructions_table_len=d.instructions_table_len, instruction_table_data_offset=d.instruction_table_data_offset,
        alloc_end=hex(d.alloc_end), fill_end=hex(d.fill_end), it_first_entry_with_code=d.it_first_entry_with_code,
        cluster_kinds=collections.Counter(c["kind"] for c in d.clusters),
    )
    json.dump(info, open(os.path.join(out, "snapshot_info.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print(json.dumps(info, indent=1, ensure_ascii=False))

if __name__ == "__main__":
    main()
