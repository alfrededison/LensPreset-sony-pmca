"""Reads the public API of compiled Java class files: the contract the app's Sony stubs declare.
Standard library only."""
import os
import struct

ACC_PUBLIC = 0x0001
ACC_PRIVATE = 0x0002
ACC_SYNTHETIC = 0x1000
ACC_BRIDGE = 0x0040


class StubClass:
    def __init__(self, name):
        self.name = name     # binary name with dots
        self.methods = set()  # "name(params)return"
        self.fields = {}      # name -> (descriptor, constant value or None)


def _read(data):
    pos = 10
    (count,) = struct.unpack_from(">H", data, 8)
    cp = [None] * count
    i = 1
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            (n,) = struct.unpack_from(">H", data, pos)
            cp[i] = ("utf8", data[pos + 2:pos + 2 + n].decode("utf-8", errors="replace"))
            pos += 2 + n
        elif tag == 3:
            cp[i] = ("int", struct.unpack_from(">i", data, pos)[0])
            pos += 4
        elif tag == 4:
            cp[i] = ("float", struct.unpack_from(">f", data, pos)[0])
            pos += 4
        elif tag in (5, 6):
            cp[i] = ("long" if tag == 5 else "double", struct.unpack_from(">q" if tag == 5 else ">d", data, pos)[0])
            pos += 8
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = ({7: "class", 8: "string"}.get(tag, "other"), struct.unpack_from(">H", data, pos)[0])
            pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag == 15:
            pos += 3
        else:
            raise ValueError("bad constant pool tag %d" % tag)
        i += 1

    def utf8(idx):
        return cp[idx][1]

    _access, this_idx = struct.unpack_from(">HH", data, pos)
    pos += 6
    (n_ifaces,) = struct.unpack_from(">H", data, pos)
    pos += 2 + 2 * n_ifaces
    cls = StubClass(utf8(cp[this_idx][1]).replace("/", "."))

    def members(pos, is_field):
        (n,) = struct.unpack_from(">H", data, pos)
        pos += 2
        for _ in range(n):
            access, name_idx, desc_idx, n_attrs = struct.unpack_from(">HHHH", data, pos)
            pos += 8
            value = None
            for _ in range(n_attrs):
                attr_name, length = struct.unpack_from(">HI", data, pos)
                if is_field and utf8(attr_name) == "ConstantValue":
                    (vidx,) = struct.unpack_from(">H", data, pos + 6)
                    kind, v = cp[vidx]
                    value = utf8(v) if kind == "string" else v
                pos += 6 + length
            name = utf8(name_idx)
            if access & (ACC_PRIVATE | ACC_SYNTHETIC | ACC_BRIDGE) or name == "<clinit>":
                continue
            if is_field:
                cls.fields[name] = (utf8(desc_idx), value)
            else:
                cls.methods.add(name + utf8(desc_idx))
        return pos

    pos = members(pos, True)
    members(pos, False)
    return cls


def read_dir(root):
    """Every class under a directory of compiled stubs, by binary name."""
    out = {}
    for d, _, files in os.walk(root):
        for f in files:
            if f.endswith(".class"):
                with open(os.path.join(d, f), "rb") as fh:
                    c = _read(fh.read())
                out[c.name] = c
    return out
