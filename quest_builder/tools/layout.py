"""Автоматическая раскладка квестов главы.

Поток слева направо: колонка = длина самой длинной цепочки зависимостей до квеста.
Внутри колонки квесты ставятся напротив своих родителей (барицентр), затем
раздвигаются, чтобы не перекрываться. Если у квеста много «листьев» (квестов без
продолжения, зависящих только от него), они собираются в аккуратную сетку справа.
Корень главы и квесты-развилки получают особую форму и размер.
"""
import math

DX = 2.0        # шаг между колонками
SUBDX = 1.5     # шаг внутри сетки / подколонки
DY = 1.5        # минимальный шаг по вертикали
MAXC = 7        # квестов в одной подколонке
GRID_MIN = 5    # с какого числа листьев собирать их в сетку
GRID_ROWS = 4   # строк в сетке листьев (до)


def _spread(items, gap):
    """items: список [key, desired_y, size]; раздвигает с сохранением среднего."""
    items.sort(key=lambda t: t[1])
    ys = []
    for i, (_, want, size) in enumerate(items):
        y = want
        if ys:
            prev_y, prev_s = ys[-1][1], items[i - 1][2]
            need = prev_y + gap + (size + prev_s - 2) * 0.5
            y = max(y, need)
        ys.append((items[i][0], y))
    if ys:
        shift = sum(t[1] for t in items) / len(items) - sum(y for _, y in ys) / len(ys)
        ys = [(k, y + shift) for k, y in ys]
    return dict(ys)


def apply(ch, qid):
    qs = ch["quests"]
    ids = [q["id"] for q in qs]
    idset = set(ids)
    par = {}
    for q in qs:
        ps = []
        for d in q.get("deps", []):
            r = qid.get(d) or qid.get(f'{ch["key"]}/{d}')
            if r in idset and r not in ps:
                ps.append(r)
        par[q["id"]] = ps
    kids = {i: [] for i in ids}
    for i, ps in par.items():
        for p in ps:
            kids[p].append(i)

    # формы и размеры (по полному графу)
    roots = [q for q in qs if not par[q["id"]]]
    for q in qs:
        if q.get("shape") or q.get("size"):
            continue
        n = len(kids[q["id"]])
        if q.get("finale"):
            q["shape"], q["size"] = "gear", 2.0
        elif not par[q["id"]] and n and len(roots) <= 3:
            q["shape"], q["size"] = "hexagon", 1.75
        elif n >= 4:
            q["shape"], q["size"] = "rsquare", 1.25
    size = {q["id"]: float(q.get("size", 1.0)) for q in qs}

    # сетки листьев
    blocks = {}
    for p in ids:
        leaves = [k for k in kids[p] if not kids[k] and par[k] == [p] and size[k] <= 1.25]
        if len(leaves) >= GRID_MIN:
            blocks[p] = leaves
    hidden = {k for v in blocks.values() for k in v}
    wpar = {i: list(par[i]) for i in ids if i not in hidden}
    wkids = {i: [k for k in kids[i] if k not in hidden] for i in wpar}
    width = {i: 1 for i in wpar}
    geom = {}
    for p, leaves in blocks.items():
        n = len(leaves)
        cols = max(1, math.ceil(n / GRID_ROWS))
        rows = math.ceil(n / cols)
        v = "__grid_" + p
        wpar[v] = [p]
        wkids[v] = []
        wkids[p].append(v)
        size[v] = (rows - 1) * DY + 1.0
        width[v] = cols
        geom[v] = (p, leaves, rows, cols)
    nodes = list(wpar)
    order = {i: n for n, i in enumerate(nodes)}

    depth = {}

    def dep(i, stack=()):
        if i in depth:
            return depth[i]
        if i in stack:
            return 0
        d = 0 if not wpar[i] else 1 + max(dep(p, stack + (i,)) for p in wpar[i])
        depth[i] = d
        return d

    for i in nodes:
        dep(i)
    layers = {}
    for i in nodes:
        layers.setdefault(depth[i], []).append(i)

    y = {}

    def weight(i, seen):
        if i in seen:
            return 0
        seen.add(i)
        own = [k for k in wkids[i] if depth[k] == depth[i] + 1 and wpar[k] and wpar[k][0] == i]
        return max(size[i] / DY + 0.34, sum(weight(k, seen) for k in own))

    cur = 0.0
    seen = set()
    for i in sorted(layers.get(0, []), key=lambda i: order[i]):
        w = weight(i, seen)
        y[i] = cur + (w - 1) * DY / 2
        cur += w * DY

    for it in range(3):
        for d in sorted(layers):
            if d == 0 and it == 0:
                continue
            items = []
            for i in layers[d]:
                ps = [p for p in wpar[i] if p in y]
                want = (sum(y[p] for p in ps) / len(ps)) if ps and d > 0 else y.get(i, 0.0)
                items.append([i, want + order[i] * 1e-4, size[i]])
            y.update(_spread(items, DY))
        for d in sorted(layers, reverse=True):
            items = []
            for i in layers[d]:
                ch_ = [k for k in wkids[i] if k in y]
                want = (sum(y[k] for k in ch_) / len(ch_)) if ch_ else y[i]
                items.append([i, 0.5 * want + 0.5 * y[i], size[i]])
            y.update(_spread(items, DY))

    # x: колонки, большие колонки переносятся на подколонки
    x = {}
    cx = 0.0
    for d in sorted(layers):
        col = sorted(layers[d], key=lambda i: y[i])
        normal = [i for i in col if i not in geom]
        nsub = max(1, math.ceil(len(normal) / MAXC))
        if nsub == 1:
            for i in col:
                x[i] = cx
        else:
            per = math.ceil(len(normal) / nsub)
            for s in range(nsub):
                chunk = normal[s * per:(s + 1) * per]
                for i in chunk:
                    x[i] = cx + s * SUBDX
                if chunk:
                    y.update(_spread([[i, y[i], size[i]] for i in chunk], DY))
            for i in col:
                if i in geom:
                    x[i] = cx
        wmax = max([nsub] + [width[i] for i in col])
        maxsize = max(size[i] for i in col if i not in geom) if normal else 1.0
        cx += (wmax - 1) * SUBDX + DX + (maxsize - 1) * 0.5

    # привязка к сетке с шагом DY/2 без наложений
    G = DY / 2
    miny = min(y.values()) if y else 0
    placed = {}
    pos = {}
    for i in sorted(nodes, key=lambda i: y[i]):
        qx = round(x[i] * 4) / 4
        want = round((y[i] - miny) / G)
        used = placed.setdefault(qx, [])
        for off in [0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 7, 8, 9, 10, 12, 14, 16, 20, 25, 30, 40]:
            c = want + off
            if all(abs(c - u) * G >= (size[i] + us) / 2 + (DY - 1) - 1e-6 for u, us in used):
                break
        used.append((c, size[i]))
        pos[i] = (qx, c * G)
    # раскладка сеток листьев
    for v, (p, leaves, rows, cols) in geom.items():
        vx, vy = pos[v]
        top = vy - (rows - 1) * DY / 2
        for n, k in enumerate(leaves):
            r, c = n % rows, n // rows
            pos[k] = (vx + c * SUBDX, round((top + r * DY) / G) * G)
    byid = {q["id"]: q for q in qs}
    for i, (px, py) in pos.items():
        if i in byid:
            byid[i]["x"], byid[i]["y"] = px, py
    # страховка от совпадений
    seen_xy = set()
    for q in sorted(qs, key=lambda q: (q["x"], q["y"])):
        while (q["x"], q["y"]) in seen_xy:
            q["y"] += DY
        seen_xy.add((q["x"], q["y"]))
