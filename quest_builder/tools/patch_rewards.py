import json,glob,sys
sys.path.insert(0,'.')
# количества
pool={
 'vanilla_progress':[('minecraft:bread',8),('minecraft:iron_ingot',4),('minecraft:torch',16),('minecraft:cooked_beef',8),('minecraft:diamond',1),('minecraft:golden_apple',1)],
 'create_andesite':[('create:andesite_alloy',8),('create:cogwheel',4),('create:shaft',8)],
 'create_brass':[('create:brass_ingot',4),('create:electron_tube',2),('create:precision_mechanism',1)],
 'create_fluids':[('create:fluid_pipe',8),('create:copper_casing',2)],
 'create_logistics':[('create:andesite_casing',4),('create:chute',4)],
 'create_contraptions':[('create:andesite_casing',4),('create:super_glue',1)],
 'create_trains':[('create:track',8),('create:brass_casing',2)],
 'ca_electric':[('createaddition:copper_wire',8),('createaddition:capacitor',1)],
 'fd_kitchen':[('minecraft:bread',8),('minecraft:cooked_beef',4)],
 'vd_veggies':[('minecraft:bread',8),('minecraft:carrot',8)],
 'ma_farm':[('minecraft:bone_meal',16),('mysticalagriculture:inferium_essence',8)],
 'ma_altars':[('mysticalagriculture:inferium_essence',16),('mysticalagriculture:prudentium_essence',4)],
 'sd_drawers':[('minecraft:oak_planks',16),('minecraft:iron_ingot',4)],
 'ss_storage':[('minecraft:oak_planks',16),('minecraft:iron_ingot',4)],
 'sb_backpacks':[('minecraft:leather',8),('minecraft:iron_ingot',4)],
 'ec_compass':[('minecraft:compass',1),('minecraft:bread',8)],
 'create_connected_ch':[('create:andesite_alloy',8),('create:brass_ingot',2)],
 'create_enchant_ch':[('minecraft:experience_bottle',4),('minecraft:lapis_lazuli',8)],
}
for p in glob.glob('data/*.json'):
    d=json.load(open(p)); k=d['key']; pl=pool.get(k,[])
    n=0
    for i,q in enumerate(d['quests']):
        for t in q['tasks']:
            if t['type']=='item' and t['count']>8: t['count']=8
        # награда: каждый 4-й квест (не стартовый)
        if pl and i%4==3 and not any(r['type']=='item' for r in q['rewards']):
            rid,c=pl[n%len(pl)]; n+=1
            q['rewards'].append({'type':'item','id':rid,'count':c})
    json.dump(d,open(p,'w'),ensure_ascii=False,indent=1)
