#!/usr/bin/env python3
# Сводка JFR-профиля по потокам: где тратится CPU, по методам и по пакетам (модам).
# jfr print --stack-depth 64 --events jdk.ExecutionSample rec.jfr > samples.txt (без --stack-depth стек режется до 5 кадров)
# jdk.NativeMethodSample: где поток стоит в нативном коде (OpenGL, драйвер), разбирается так же
# python3 -I qa/jfr_top.py samples.txt [сколько строк, 25] [поток, по умолчанию Render thread и Server thread]
import collections
import re
import sys


def group(name):
    # Worker-Main-12 и подобные считаем одним потоком
    return re.sub(r'[-#_ ]?\d+$', '-N', name)


def pkg(frame):
    m = frame.split('(')[0]
    parts = m.split('.')
    if len(parts) < 3:
        return m
    # класс и метод отрезаем, оставляем до 3 уровней пакета
    p = [x for x in parts[:-2]]
    return '.'.join(p[:3]) if p else m


def main():
    path = sys.argv[1]
    top = int(sys.argv[2]) if len(sys.argv) > 2 else 25
    want = sys.argv[3:] or ['Render thread', 'Server thread']
    threads = collections.Counter()
    self_ = collections.defaultdict(collections.Counter)
    incl = collections.defaultdict(collections.Counter)
    pkgs = collections.defaultdict(collections.Counter)
    thread, stack, in_stack = None, [], False

    def flush():
        if thread is None:
            return
        g = group(thread)
        threads[g] += 1
        if not stack:
            return
        self_[g][stack[0]] += 1
        for f in set(stack):
            incl[g][f] += 1
        for p in set(pkg(f) for f in stack):
            pkgs[g][p] += 1

    with open(path, encoding='utf-8', errors='replace') as fh:
        for line in fh:
            s = line.strip()
            if s.startswith('jdk.ExecutionSample') or s.startswith('jdk.NativeMethodSample'):
                flush()
                thread, stack, in_stack = None, [], False
            elif s.startswith('sampledThread'):
                m = re.search(r'"(.*)"', s)
                thread = m.group(1) if m else s
            elif s.startswith('stackTrace = ['):
                in_stack = True
            elif in_stack:
                if s.startswith(']'):
                    in_stack = False
                elif s and s != '...':
                    stack.append(re.sub(r'\s+line:.*$', '', s))
        flush()

    total = sum(threads.values())
    print(f'сэмплов {total}')
    for g, n in threads.most_common(20):
        print(f'  {n:7d} {100 * n / total:5.1f}%  {g}')
    for w in want:
        g = group(w)
        n = threads.get(g, 0)
        if not n:
            continue
        print(f'\n== {w}: {n} сэмплов ==')
        print('-- сам метод (self) --')
        for f, c in self_[g].most_common(top):
            print(f'  {100 * c / n:5.1f}%  {f}')
        print('-- вместе с вызовами (inclusive) --')
        for f, c in incl[g].most_common(top):
            print(f'  {100 * c / n:5.1f}%  {f}')
        print('-- по пакетам --')
        for p, c in pkgs[g].most_common(top):
            print(f'  {100 * c / n:5.1f}%  {p}')


if __name__ == '__main__':
    main()
