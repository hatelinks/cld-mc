# Проверка без игры: находят ли миксины одного jar свои цели в установленных модах.
# Запуск: python3 -I qa/mixcheck.py <jar с миксинами> <папка mods> [доп. jar, например ваниль и NeoForge из libs]
# Печатает FAIL с причиной: нет класса цели, нет метода или вызова для инжектора, нет поля или метода для @Shadow.
# Отсутствие класса мода, которого нет в сборке, обычно не ошибка: такие миксины выключает плагин мода.
import sys, struct, zipfile, io, json, os, re

def u2(b, o): return struct.unpack_from('>H', b, o)[0]
def u4(b, o): return struct.unpack_from('>I', b, o)[0]

OPLEN = {}
for op in range(256): OPLEN[op] = 1
for op in [0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc]: OPLEN[op] = 2
for op in [0x11, 0x13, 0x14, 0x84, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xbb, 0xbd, 0xc0, 0xc1, 0xc6, 0xc7] + list(range(0x99, 0xa9)): OPLEN[op] = 3
for op in [0xb9, 0xba, 0xc8, 0xc9]: OPLEN[op] = 5
OPLEN[0xc5] = 4

class Cls:
    pass

def parse(data):
    c = Cls()
    o = 8
    n = u2(data, o); o += 2
    cp = [None] * n
    i = 1
    while i < n:
        t = data[o]
        if t == 1:
            l = u2(data, o + 1); cp[i] = (1, data[o + 3:o + 3 + l].decode('utf8', 'replace')); o += 3 + l
        elif t in (3, 4):
            cp[i] = (t, u4(data, o + 1)); o += 5
        elif t in (5, 6):
            cp[i] = (t,); o += 9; i += 1
        elif t in (7, 8, 16, 19, 20):
            cp[i] = (t, u2(data, o + 1)); o += 3
        elif t == 15:
            cp[i] = (t,); o += 4
        else:
            cp[i] = (t, u2(data, o + 1), u2(data, o + 3)); o += 5
        i += 1
    def utf(k): return cp[k][1]
    def clsname(k): return utf(cp[k][1])
    def ref(k):
        e = cp[k]
        nt = cp[e[2]]
        return clsname(e[1]), utf(nt[1]), utf(nt[2])
    c.utf = utf
    o += 2
    c.name = clsname(u2(data, o)); o += 2
    sup = u2(data, o); c.super = clsname(sup) if sup else None; o += 2
    ni = u2(data, o); o += 2
    c.ifaces = [clsname(u2(data, o + 2 * j)) for j in range(ni)]; o += 2 * ni
    def ann_val(o):
        tag = chr(data[o]); o += 1
        if tag in 'BCDFIJSZs':
            k = u2(data, o); o += 2
            v = cp[k][1] if tag == 's' else cp[k][1]
            return v, o
        if tag == 'e':
            return (utf(u2(data, o)), utf(u2(data, o + 2))), o + 4
        if tag == 'c':
            return utf(u2(data, o)), o + 2
        if tag == '@':
            return ann(o)
        if tag == '[':
            m = u2(data, o); o += 2
            arr = []
            for _ in range(m):
                v, o = ann_val(o); arr.append(v)
            return arr, o
        raise Exception(tag)
    def ann(o):
        t = utf(u2(data, o)); m = u2(data, o + 2); o += 4
        d = {'@': t}
        for _ in range(m):
            k = utf(u2(data, o)); o += 2
            v, o = ann_val(o); d[k] = v
        return d, o
    def attrs(o):
        res = {'ann': [], 'code': None}
        m = u2(data, o); o += 2
        for _ in range(m):
            an = utf(u2(data, o)); l = u4(data, o + 2); s = o + 6
            if an in ('RuntimeVisibleAnnotations', 'RuntimeInvisibleAnnotations'):
                k = u2(data, s); p = s + 2
                for _ in range(k):
                    a, p = ann(p); res['ann'].append(a)
            elif an == 'Code':
                cl = u4(data, s + 4)
                res['code'] = (s + 8, cl)
            o = s + l
        return res, o
    c.fields = []
    nf = u2(data, o); o += 2
    for _ in range(nf):
        nm = utf(u2(data, o + 2)); ds = utf(u2(data, o + 4))
        a, o = attrs(o + 6)
        c.fields.append((nm, ds, a['ann']))
    c.methods = []
    nm_ = u2(data, o); o += 2
    for _ in range(nm_):
        nm = utf(u2(data, o + 2)); ds = utf(u2(data, o + 4))
        a, o = attrs(o + 6)
        calls = []
        if a['code']:
            s, l = a['code']; p = s
            while p < s + l:
                op = data[p]
                if op in (0xb6, 0xb7, 0xb8, 0xb9, 0xb2, 0xb3, 0xb4, 0xb5):
                    try: calls.append(ref(u2(data, p + 1)))
                    except Exception: pass
                if op == 0xaa:
                    q = p + 1; q += (4 - (q - s) % 4) % 4
                    lo = struct.unpack_from('>i', data, q + 4)[0]; hi = struct.unpack_from('>i', data, q + 8)[0]
                    p = q + 12 + 4 * (hi - lo + 1); continue
                if op == 0xab:
                    q = p + 1; q += (4 - (q - s) % 4) % 4
                    np_ = struct.unpack_from('>i', data, q + 4)[0]
                    p = q + 8 + 8 * np_; continue
                if op == 0xc4:
                    p += 6 if data[p + 1] == 0x84 else 4; continue
                p += OPLEN[op]
        c.methods.append((nm, ds, a['ann'], calls))
    c.ann, o = attrs(o)
    c.ann = c.ann['ann']
    return c

def iter_jars(path, data=None):
    z = zipfile.ZipFile(io.BytesIO(data) if data else path)
    for n in z.namelist():
        if n.endswith('.jar'):
            yield from iter_jars(path + '!' + n, z.read(n))
    yield path, z

index = {}
def add_jar(path):
    for jp, z in iter_jars(path):
        for n in z.namelist():
            if n.endswith('.class') and not n.startswith('META-INF/'):
                cn = n[:-6]
                if cn not in index:
                    index[cn] = (z, n)

cache = {}
def get(cn):
    if cn in cache: return cache[cn]
    e = index.get(cn)
    r = None
    if e:
        try: r = parse(e[0].read(e[1]))
        except Exception as ex: print('parse fail', cn, ex)
    cache[cn] = r
    return r

def hierarchy(cn):
    out = []
    seen = set()
    stack = [cn]
    while stack:
        x = stack.pop()
        if x in seen: continue
        seen.add(x)
        c = get(x)
        if c is None: continue
        out.append(c)
        if c.super: stack.append(c.super)
        stack.extend(c.ifaces)
    return out

INJ = {'Inject', 'Redirect', 'ModifyArg', 'ModifyArgs', 'ModifyVariable', 'ModifyConstant', 'ModifyExpressionValue',
       'WrapOperation', 'WrapWithCondition', 'ModifyReturnValue', 'ModifyReceiver', 'WrapMethod'}

def match_methods(tc, spec):
    m = re.match(r'^(?:L[^;]+;)?([^(]+?)(\*)?(\(.*)?$', spec)
    if not m: return []
    name, star, desc = m.group(1), m.group(2), m.group(3)
    res = []
    for mm in tc.methods:
        if star:
            if not mm[0].startswith(name): continue
        elif mm[0] != name: continue
        if desc and mm[1] != desc: continue
        res.append(mm)
    return res

def at_list(a):
    at = a.get('at')
    if at is None: return []
    return at if isinstance(at, list) else [at]

def check_target_at(methods, at):
    v = at.get('value', '')
    t = at.get('target')
    if v in ('HEAD', 'RETURN', 'TAIL') or not t:
        return len(methods) > 0
    if v in ('INVOKE', 'INVOKE_ASSIGN', 'FIELD', 'NEW'):
        m = re.match(r'^L([^;]+);([^(:]+)(?::)?(.*)$', t)
        if not m:
            return any(methods)
        own, nm, ds = m.group(1), m.group(2), m.group(3)
        for mm in methods:
            for c in mm[3]:
                if c[1] == nm and (not ds or c[2] == ds):
                    return True
        return False
    return len(methods) > 0

def main():
    mj = sys.argv[1]
    for f in sorted(os.listdir(sys.argv[2])):
        if f.endswith('.jar'): add_jar(os.path.join(sys.argv[2], f))
    for e in sys.argv[3:]: add_jar(e)
    add_jar(mj)
    z = zipfile.ZipFile(mj)
    cfgs = [n for n in z.namelist() if n.endswith('.json') and 'mixins' in n]
    for cf in cfgs:
        j = json.loads(z.read(cf))
        pkg = j.get('package', '')
        dreq = j.get('injectors', {}).get('defaultRequire', 0)
        for side in ('mixins', 'client', 'server'):
            for mx in j.get(side, []) or []:
                cn = (pkg + '.' + mx).replace('.', '/')
                mc = get(cn)
                if mc is None:
                    print('NOMIXIN', cn); continue
                targets = []
                for a in mc.ann:
                    if a['@'].endswith('/Mixin;'):
                        for v in a.get('value', []) or []: targets.append(v[1:-1] if v.startswith('L') else v)
                        for v in a.get('targets', []) or []: targets.append(v.replace('.', '/'))
                probs = []
                for t in targets:
                    tc = get(t)
                    if tc is None:
                        probs.append('target class missing ' + t); continue
                    hier = hierarchy(t)
                    for (fn, fd, fa) in mc.fields:
                        for a in fa:
                            if a['@'].endswith('/Shadow;'):
                                if not any(f[0] == fn for h in hier for f in h.fields):
                                    probs.append('shadow field %s' % fn)
                    for (mn, md, ma, _) in mc.methods:
                        for a in ma:
                            an = a['@'].rsplit('/', 1)[-1][:-1]
                            if an == 'Shadow':
                                if not any(m[0] == mn and m[1] == md for h in hier for m in h.methods):
                                    probs.append('shadow method %s%s' % (mn, md))
                            elif an in ('Accessor', 'Invoker'):
                                val = a.get('value')
                                if an == 'Invoker':
                                    nm = val or re.sub(r'^(call|invoke)', '', mn); nm = nm[0].lower() + nm[1:] if not val else nm
                                    if not any(m[0] == nm for h in hier for m in h.methods):
                                        probs.append('invoker %s' % nm)
                                else:
                                    nm = val or re.sub(r'^(get|set|is)', '', mn); nm = nm[0].lower() + nm[1:] if not val else nm
                                    if not any(f[0] == nm for h in hier for f in h.fields):
                                        probs.append('accessor %s' % nm)
                            elif an in INJ:
                                req = a.get('require', dreq)
                                specs = a.get('method', []) or []
                                found = []
                                for s in specs: found += match_methods(tc, s)
                                ok = bool(found)
                                if ok:
                                    for at in at_list(a):
                                        if not check_target_at(found, at): ok = False
                                if not ok and req:
                                    probs.append('%s %s -> %s %s' % (an, mn, specs, [x.get('target') for x in at_list(a)]))
                if probs:
                    print('FAIL', cf, mx, targets)
                    for p in probs: print('   ', p)
main()
