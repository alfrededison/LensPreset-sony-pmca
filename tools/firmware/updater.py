"""Takes FirmwareData_*.dat out of Sony's newer Windows updaters, which fwtool.py cannot open.

fwtool knows updaters whose payload is a "_winzip_" PE section or an LZH archive after the last
section. Later updaters ("Firmware Updater Starter" 1.1.8, e.g. Update_ILCE5100V310.exe) append an
uncompressed archive instead, worked out from the A5100's 3.10 updater:

    FILETIME, FILETIME, u64 (2), u32 group count
    per group:  u32 name length (UTF-16 units), u32 entry count, name (e.g. "Resource\\")
    per entry:  u32 name length, u32 size, name, FILETIME
    then every entry's bytes, in table order

fwtool reads the FirmwareData .dat on its own, so this only finds and copies it. Standard library only.
"""
import shutil
import struct

DAT_MAGIC = b"\x89UFU\r\n\x1a\n"


def _end_of_sections(f):
    f.seek(0)
    head = f.read(4096)
    if head[:2] != b"MZ":
        return None
    pe = struct.unpack_from("<I", head, 0x3C)[0]
    f.seek(pe)
    hdr = f.read(24)
    if hdr[:4] != b"PE\0\0":
        return None
    nsec, = struct.unpack_from("<H", hdr, 6)
    optsize, = struct.unpack_from("<H", hdr, 20)
    f.seek(pe + 24 + optsize)
    end = 0
    for _ in range(nsec):
        sec = f.read(40)
        _, _, rsize, roff = struct.unpack_from("<IIII", sec, 8)
        end = max(end, roff + rsize)
    return end


def entries(f):
    """[(group, name, offset, size)] of the appended archive, or [] when the file has none."""
    end = _end_of_sections(f)
    if end is None:
        return []
    f.seek(0, 2)
    total = f.tell()
    f.seek(end)
    head = f.read(28)
    if len(head) < 28:
        return []
    _, ngroups = struct.unpack_from("<QI", head, 16)
    if not 0 < ngroups < 64:
        return []
    table = []
    try:
        for _ in range(ngroups):
            nlen, count = struct.unpack("<II", f.read(8))
            if nlen > 260 or count > 10000:
                return []
            group = f.read(2 * nlen).decode("utf-16le")
            for _ in range(count):
                nlen, size = struct.unpack("<II", f.read(8))
                if nlen > 260:
                    return []
                name = f.read(2 * nlen).decode("utf-16le")
                f.read(8)
                table.append((group, name, size))
    except (struct.error, UnicodeDecodeError):
        return []
    offset = f.tell()
    out = []
    for group, name, size in table:
        if offset + size > total:
            return []
        out.append((group, name, offset, size))
        offset += size
    return out


def extract_dat(exe_path, out_path):
    """Copies the archive's FirmwareData*.dat to out_path. Returns its archive name, or None."""
    with open(exe_path, "rb") as f:
        for _group, name, offset, size in entries(f):
            if not (name.startswith("FirmwareData") and name.lower().endswith(".dat")):
                continue
            f.seek(offset)
            if f.read(len(DAT_MAGIC)) != DAT_MAGIC:
                continue
            f.seek(offset)
            with open(out_path, "wb") as out:
                remaining = size
                while remaining:
                    chunk = f.read(min(remaining, 1 << 20))
                    if not chunk:
                        raise IOError("truncated updater")
                    out.write(chunk)
                    remaining -= len(chunk)
            return name
    return None


def build(files, groups=None):
    """An updater-shaped file holding `files` ([(name, bytes)]), for tests."""
    stub = bytearray(512)
    stub[0:2] = b"MZ"
    struct.pack_into("<I", stub, 0x3C, 0x80)
    stub[0x80:0x84] = b"PE\0\0"
    struct.pack_into("<H", stub, 0x86, 1)       # one section
    struct.pack_into("<H", stub, 0x94, 0)       # no optional header
    sec = 0x98
    stub[sec:sec + 8] = b".text\0\0\0"
    struct.pack_into("<IIII", stub, sec + 8, 0, 0, 0, 512)  # raw size 0 at offset 512
    groups = groups or [("Resource\\", files)]
    table = bytes(16) + struct.pack("<QI", 2, len(groups))
    data = b""
    for gname, gfiles in groups:
        table += struct.pack("<II", len(gname), len(gfiles)) + gname.encode("utf-16le")
        for name, content in gfiles:
            table += struct.pack("<II", len(name), len(content)) + name.encode("utf-16le") + bytes(8)
            data += content
    return bytes(stub) + table + data + b"trailer"
