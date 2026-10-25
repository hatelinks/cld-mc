import re,sys,os
sys.path.insert(0,os.path.dirname(__file__))
import lex
C=lex.COLORS; W=lex.WOODS+['jaboticaba','ramboutan','chorus','pale_oak']
STONES=['granite','diorite','andesite','calcite','dripstone','deepslate','tuff','asurine','crimsite','limestone','ochrum','scorchia','scoria','veridium']
def family(rid):
    ns,p=rid.split(':',1)
    for c in sorted(C,key=len,reverse=True):
        if p.startswith(c+'_') : return ns+':'+'*_'+p[len(c)+1:],'color'
        if p.endswith('_'+c): return ns+':'+p[:-len(c)-1]+'_*','color'
        if ('_'+c+'_') in p: return ns+':'+p.replace('_'+c+'_','_*_'),'color'
    for w in sorted(W,key=len,reverse=True):
        if p.startswith(w+'_'): return ns+':'+'*_'+p[len(w)+1:],'wood'
        if p.endswith('_'+w): return ns+':'+p[:-len(w)-1]+'_*','wood'
    return rid,None
DECOR=re.compile(r'(_stairs?|_slab|_wall|_bricks?|_pillar|_tiles?|_window(_pane)?|_pane|^layered_|^cut_|^polished_cut|^small_.*bricks|_cobblestone|_bricks_|_planks|_door|_trapdoor|_fence(_gate)?|_button|_pressure_plate|_carpet|_lamp|_glass)')
def is_decor(rid):
    p=rid.split(':')[1]
    if any(p.startswith(s) or ('_'+s) in p for s in STONES) and rid.startswith('create:'): return True
    return bool(DECOR.search(p))
TECH=re.compile(r'(^incomplete_|^creative_|_on$|tab_icon|_pulling_|^empty_.*loader$|_bucket$|music_disc|spawn_egg|_link_block$|^paired_|_cast$|_static$|crossbow_(arrow|firework)|^debug|^dev_)')
def is_tech(rid): return bool(TECH.search(rid.split(':')[1]))
