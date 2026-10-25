"""Рисует главы из out/ftbquests/quests/chapters/*.snbt в PNG для проверки раскладки."""
import sys,os,re,glob
import matplotlib; matplotlib.use('Agg')
import matplotlib.pyplot as plt
sys.path.insert(0,os.path.dirname(__file__))
import snbt
OUT=sys.argv[1]; keys=sys.argv[2:]
for p in sorted(glob.glob('/home/claude/out/ftbquests/quests/chapters/*.snbt')):
    k=os.path.basename(p)[:-5]
    if keys and k not in keys: continue
    d=snbt.parse(open(p).read())
    qs=d['quests']; pos={q['id']:(q['x'],q['y']) for q in qs}
    w=max(x for x,y in pos.values())-min(x for x,y in pos.values())+3; h=max(y for x,y in pos.values())+3
    fig,ax=plt.subplots(figsize=(max(4,w*0.5),max(3,h*0.5)))
    for q in qs:
        for dd in q.get('dependencies',[]):
            if dd in pos: ax.plot([pos[dd][0],q['x']],[pos[dd][1],q['y']],color='#999',lw=0.8,zorder=1)
    for q in qs:
        s=float(q.get('size',1.0)); mk={'hexagon':'h','rsquare':'s','gear':'*'}.get(q.get('shape'),'o')
        ax.scatter([q['x']],[q['y']],s=160*s*s,marker=mk,zorder=2,color='#3a7')
    ax.invert_yaxis(); ax.set_aspect('equal'); ax.set_title(k)
    fig.savefig(f'{OUT}/{k}.png',dpi=60,bbox_inches='tight'); plt.close(fig)
