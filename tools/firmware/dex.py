"""Reads the class, method and field signatures out of DEX and ODEX files, and out of jars and
APKs that carry a classes.dex. Standard library only.

Only what the contract check needs: declared members with their descriptors, superclasses, and
the values of static final fields.
"""
import struct
import zipfile

ODEX_MAGIC = b"dey\n"
DEX_MAGIC = b"dex\n"


class _Null:
    """A static field whose value is null, as opposed to None: "no value known" (instance fields)."""

    def __repr__(self):
        return "null"


NULL = _Null()


def _default(descriptor):
    """The value of a static field the DEX leaves out of static_values: its type's default."""
    if descriptor == "Z":
        return False
    if descriptor in ("F", "D"):
        return 0.0
    if descriptor in ("B", "S", "C", "I", "J"):
        return 0
    return NULL


class DexClass:
    def __init__(self, name, superclass):
        self.name = name                # binary name with dots: com.sony.scalar.hardware.CameraEx$LensInfo
        self.superclass = superclass
        self.methods = set()            # "name(params)return", JVM descriptor syntax
        self.fields = {}                # name -> (descriptor, value): static fields always have one
                                        # (NULL for null), instance fields None


def _uleb(data, pos):
    result = 0
    shift = 0
    while True:
        b = data[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if b < 0x80:
            return result, pos
        shift += 7


def _type_to_name(descriptor):
    """Lcom/sony/X; -> com.sony.X"""
    if descriptor.startswith("L") and descriptor.endswith(";"):
        return descriptor[1:-1].replace("/", ".")
    return descriptor


class _Reader:
    def __init__(self, data):
        if data[:4] != DEX_MAGIC:
            raise ValueError("not a dex file")
        self.d = data
        u = lambda off: struct.unpack_from("<I", data, off)[0]
        self.strings = [self._string(u(u(0x3C) + 4 * i)) for i in range(u(0x38))]
        self.types = [self.strings[u(u(0x44) + 4 * i)] for i in range(u(0x40))]
        self.protos = []
        for i in range(u(0x48)):
            off = u(0x4C) + 12 * i
            _shorty, ret, params_off = struct.unpack_from("<III", data, off)
            params = []
            if params_off:
                n = u(params_off)
                params = [self.types[struct.unpack_from("<H", data, params_off + 4 + 2 * k)[0]] for k in range(n)]
            self.protos.append("(" + "".join(params) + ")" + self.types[ret])
        self.fields = []
        for i in range(u(0x50)):
            cls, typ, name = struct.unpack_from("<HHI", data, u(0x54) + 8 * i)
            self.fields.append((self.types[cls], self.types[typ], self.strings[name]))
        self.methods = []
        for i in range(u(0x58)):
            cls, proto, name = struct.unpack_from("<HHI", data, u(0x5C) + 8 * i)
            self.methods.append((self.types[cls], self.strings[name] + self.protos[proto]))
        self.class_defs = [struct.unpack_from("<8I", data, u(0x64) + 32 * i) for i in range(u(0x60))]

    def _string(self, off):
        _, pos = _uleb(self.d, off)
        end = self.d.index(b"\x00", pos)
        return self.d[pos:end].decode("utf-8", errors="replace")

    def _encoded_value(self, pos):
        head = self.d[pos]
        pos += 1
        vtype, arg = head & 0x1F, head >> 5
        if vtype in (0x00, 0x02, 0x03, 0x04, 0x06):  # byte short char int long: sign-extended
            raw = self.d[pos:pos + arg + 1]
            value = int.from_bytes(raw, "little", signed=vtype != 0x03)
            return value, pos + arg + 1
        if vtype in (0x10, 0x11):  # float double: zero-extended to the right
            raw = self.d[pos:pos + arg + 1]
            size = 4 if vtype == 0x10 else 8
            padded = bytes(size - len(raw)) + raw
            return struct.unpack("<f" if size == 4 else "<d", padded)[0], pos + arg + 1
        if vtype in (0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B):  # indices
            idx = int.from_bytes(self.d[pos:pos + arg + 1], "little")
            value = self.strings[idx] if vtype == 0x17 else None
            return value, pos + arg + 1
        if vtype == 0x1C:  # array
            n, pos = _uleb(self.d, pos)
            for _ in range(n):
                _, pos = self._encoded_value(pos)
            return None, pos
        if vtype == 0x1D:  # annotation
            _, pos = _uleb(self.d, pos)
            n, pos = _uleb(self.d, pos)
            for _ in range(n):
                _, pos = _uleb(self.d, pos)
                _, pos = self._encoded_value(pos)
            return None, pos
        if vtype == 0x1E:  # null
            return NULL, pos
        if vtype == 0x1F:  # boolean in the argument
            return bool(arg), pos
        raise ValueError("bad encoded value type 0x%x" % vtype)

    def classes(self):
        out = {}
        for (cls_idx, _access, super_idx, _ifaces, _src, _ann, data_off, static_off) in self.class_defs:
            name = _type_to_name(self.types[cls_idx])
            superclass = _type_to_name(self.types[super_idx]) if super_idx != 0xFFFFFFFF else None
            c = DexClass(name, superclass)
            static_values = []
            if static_off:
                n, pos = _uleb(self.d, static_off)
                for _ in range(n):
                    v, pos = self._encoded_value(pos)
                    static_values.append(v)
            if data_off:
                pos = data_off
                sizes = []
                for _ in range(4):
                    v, pos = _uleb(self.d, pos)
                    sizes.append(v)
                for group in range(2):  # static fields, instance fields
                    idx = 0
                    for k in range(sizes[group]):
                        diff, pos = _uleb(self.d, pos)
                        _acc, pos = _uleb(self.d, pos)
                        idx += diff
                        _, typ, fname = self.fields[idx]
                        if group == 1:
                            value = None
                        elif k < len(static_values):
                            value = static_values[k]
                        else:
                            # DEX drops trailing defaults: 0, false, null.
                            value = _default(typ)
                        c.fields[fname] = (typ, value)
                for group in range(2):  # direct methods, virtual methods
                    idx = 0
                    for _ in range(sizes[2 + group]):
                        diff, pos = _uleb(self.d, pos)
                        _acc, pos = _uleb(self.d, pos)
                        _code, pos = _uleb(self.d, pos)
                        idx += diff
                        c.methods.add(self.methods[idx][1])
            out[name] = c
        return out


def read_dex_bytes(data):
    """Classes in a DEX or ODEX image."""
    if data[:4] == ODEX_MAGIC:
        dex_off, dex_len = struct.unpack_from("<II", data, 8)
        data = data[dex_off:dex_off + dex_len]
    return _Reader(data).classes()


def read_file(path):
    """Classes in a .dex, .odex, or a zip (.jar/.apk) holding classes*.dex. Empty if none."""
    with open(path, "rb") as f:
        head = f.read(4)
    if head in (DEX_MAGIC, ODEX_MAGIC):
        with open(path, "rb") as f:
            return read_dex_bytes(f.read())
    if head[:2] == b"PK":
        out = {}
        try:
            with zipfile.ZipFile(path) as z:
                for n in z.namelist():
                    if n.startswith("classes") and n.endswith(".dex"):
                        out.update(read_dex_bytes(z.read(n)))
        except zipfile.BadZipFile:
            return {}
        return out
    return {}
