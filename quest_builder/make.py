"""Полная пересборка: data_base -> specs/*.py -> data -> build.py"""
import glob, importlib.util, os, shutil, subprocess, sys
ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(ROOT, "tools"))
shutil.rmtree(os.path.join(ROOT, "data"), ignore_errors=True)
shutil.copytree(os.path.join(ROOT, "data_base"), os.path.join(ROOT, "data"))
import gen
book = gen.Book()
for p in sorted(glob.glob(os.path.join(ROOT, "specs", "*.py"))):
    spec = importlib.util.spec_from_file_location(os.path.basename(p)[:-3], p)
    m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
    before = sum(len(c["quests"]) for c in book.ch.values())
    m.run(book)
    print(f"{os.path.basename(p)}: +{sum(len(c['quests']) for c in book.ch.values()) - before}")
book.save()
sys.exit(subprocess.call([sys.executable, "-I", os.path.join(ROOT, "build.py")]))
