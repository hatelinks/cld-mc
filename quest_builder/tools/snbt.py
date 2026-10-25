"""Минимальный парсер SNBT (формат, который пишет наш генератор)."""
import re

TOK = re.compile(r'\s*(?:(?P<str>"(?:\\.|[^"\\])*")|(?P<p>[{}\[\]:,])|(?P<w>[^\s{}\[\]:,"]+))')


def tokenize(s):
    pos = 0
    out = []
    while pos < len(s):
        m = TOK.match(s, pos)
        if not m or m.end() == pos:
            if s[pos:].strip() == "":
                break
            raise ValueError("bad snbt at %d: %r" % (pos, s[pos:pos + 40]))
        pos = m.end()
        if m.group("str") is not None:
            out.append(("s", bytes(m.group("str")[1:-1], "utf-8").decode("unicode_escape").encode("latin-1").decode("utf-8")
                        if False else m.group("str")[1:-1].replace('\\"', '"').replace("\\\\", "\\")))
        elif m.group("p"):
            out.append(("p", m.group("p")))
        else:
            out.append(("w", m.group("w")))
    return out


def parse(s):
    toks = tokenize(s)
    i = 0

    def val():
        nonlocal i
        t, v = toks[i]
        if t == "s":
            i += 1
            return v
        if t == "w":
            i += 1
            return word(v)
        if v == "{":
            i += 1
            d = {}
            while toks[i] != ("p", "}"):
                if toks[i] == ("p", ","):
                    i += 1
                    continue
                k = toks[i][1]
                i += 1
                assert toks[i] == ("p", ":"), toks[i]
                i += 1
                d[k] = val()
            i += 1
            return d
        if v == "[":
            i += 1
            a = []
            while toks[i] != ("p", "]"):
                if toks[i] == ("p", ","):
                    i += 1
                    continue
                a.append(val())
            i += 1
            return a
        raise ValueError(toks[i])

    def word(w):
        if w == "true":
            return True
        if w == "false":
            return False
        m = re.fullmatch(r"(-?\d+)([Ll]?)", w)
        if m:
            return int(m.group(1))
        m = re.fullmatch(r"(-?\d+(?:\.\d+)?)[dDfF]", w)
        if m:
            return float(m.group(1))
        return w

    return val()
