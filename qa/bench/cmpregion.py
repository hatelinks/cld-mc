"""cmpregion.py <world A> <world B> <cx> <cz> <r>: сравнить блоки и биомы чанков квадрата в region-файлах двух миров."""
import struct, sys, zlib, os

def nbt(b, i, t):
    if t == 1: return b[i], i + 1
    if t == 2: return struct.unpack_from('>h', b, i)[0], i + 2
    if t == 3: return struct.unpack_from('>i', b, i)[0], i + 4
    if t == 4: return struct.unpack_from('>q', b, i)[0], i + 8
    if t == 5: return struct.unpack_from('>f', b, i)[0], i + 4
    if t == 6: return struct.unpack_from('>d', b, i)[0], i + 8
    if t == 7:
        n = struct.unpack_from('>i', b, i)[0]; return bytes(b[i+4:i+4+n]), i + 4 + n
    if t == 8:
        n = struct.unpack_from('>H', b, i)[0]; return b[i+2:i+2+n].decode('utf-8', 'replace'), i + 2 + n
    if t == 9:
        et = b[i]; n = struct.unpack_from('>i', b, i + 1)[0]; i += 5; out = []
        for _ in range(n):
            v, i = nbt(b, i, et); out.append(v)
        return out, i
    if t == 10:
        d = {}
        while True:
            ct = b[i]; i += 1
            if ct == 0: return d, i
            n = struct.unpack_from('>H', b, i)[0]; k = b[i+2:i+2+n].decode('utf-8', 'replace'); i += 2 + n
            d[k], i = nbt(b, i, ct)
    if t == 11:
        n = struct.unpack_from('>i', b, i)[0]; return tuple(struct.unpack_from('>%di' % n, b, i + 4)), i + 4 + 4 * n
    if t == 12:
        n = struct.unpack_from('>i', b, i)[0]; return tuple(struct.unpack_from('>%dq' % n, b, i + 4)), i + 4 + 8 * n
    raise ValueError(t)

def chunk(world, cx, cz):
    f = os.path.join(world, 'region', 'r.%d.%d.mca' % (cx >> 5, cz >> 5))
    if not os.path.exists(f): return None
    with open(f, 'rb') as h: data = h.read()
    o = 4 * ((cx & 31) + (cz & 31) * 32)
    off = int.from_bytes(data[o:o+3], 'big')
    if off == 0: return None
    p = off * 4096; ln = struct.unpack_from('>i', data, p)[0]; comp = data[p+4]
    raw = data[p+5:p+4+ln]
    raw = zlib.decompress(raw) if comp in (2, 1) else raw
    root, _ = nbt(raw, 3 + struct.unpack_from('>H', raw, 1)[0], 10)
    return root

def unpack(sec):
    pal = sec.get('palette', []); d = sec.get('data')
    if not d: return [repr(pal[0])] * 4096 if pal else None
    bits = max(4, (len(pal) - 1).bit_length()); per = 64 // bits; mask = (1 << bits) - 1
    out = []
    for i in range(4096):
        v = d[i // per] & 0xFFFFFFFFFFFFFFFF
        out.append(repr(pal[(v >> ((i % per) * bits)) & mask]))
    return out

A, B, CX, CZ, R = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
same = diff = missing = 0; diffs = []
for x in range(CX - R, CX + R + 1):
    for z in range(CZ - R, CZ + R + 1):
        a, b = chunk(A, x, z), chunk(B, x, z)
        if a is None or b is None or a.get('Status') != b.get('Status'):
            missing += 1; diffs.append((x, z, 'нет/статус', a and a.get('Status'), b and b.get('Status'))); continue
        sa = {s['Y']: s for s in a.get('sections', [])}; sb = {s['Y']: s for s in b.get('sections', [])}
        bad = []
        for y in sorted(set(sa) | set(sb)):
            for key in ('block_states', 'biomes'):
                pa, pb = sa.get(y, {}).get(key), sb.get(y, {}).get(key)
                if pa == pb: continue
                if key == 'block_states' and pa and pb and unpack(pa) == unpack(pb): continue
                if key == 'biomes' and pa and pb and pa.get('palette') == pb.get('palette') and pa.get('data') == pb.get('data'): continue
                bad.append((y, key))
        if bad: diff += 1; diffs.append((x, z, bad[:4]))
        else: same += 1
print('совпало %d, отличается %d, нет в одном из миров %d' % (same, diff, missing))
for d in diffs[:10]: print(' ', d)
