import json,sys
ids=set(json.load(open('jars/mysticalagriculture.json')))
T=[('inferium','Инфериум','инфериума'),('prudentium','Пруденциум','пруденциума'),('tertium','Терциум','терциума'),
   ('imperium','Империум','империума'),('supremium','Супремиум','супремиума'),('awakened_supremium','Пробуждённый супремиум','пробуждённого супремиума')]
ESS={'inferium':'549472105094A59C','prudentium':'3C1DBE2D71FEE0D6','tertium':'6F8AD82D0D28B68B','imperium':'3A24838EF6D12015','supremium':'20A4C0B01C02F7EC','awakened_supremium':'2CC6F6B609EE5C4A'}
def own(t,names): return [f'mysticalagriculture:{t}_{n}' for n in names if f'mysticalagriculture:{t}_{n}' in ids]
COLS=[
 ('mat','Материалы','Слитки и самоцветы',['ingot','gemstone'],
  ['&6Как работает:&r слиток и самоцвет — основные материалы ступени, из них делают инструменты, броню и блоки.','&6Зачем нужен:&r без них не создать ни одну вещь этой ступени.','&6Как использовать:&r получаются на алтаре инфузии из материалов предыдущей ступени и эссенции.']),
 ('farm','Грядки','Земля и ускорители роста',['farmland','growth_accelerator'],
  ['&6Как работает:&r особая земля ускоряет рост семян, а ускоритель сокращает время созревания рядом стоящих культур.','&6Зачем нужен:&r чем выше ступень, тем быстрее и выгоднее урожай.','&6Как использовать:&r посадите семена на землю нужной ступени и поставьте ускоритель рядом.']),
 ('upg','Улучшения','Улучшение для машин',['upgrade'],
  ['&6Как работает:&r улучшение вставляется в машины Mystical Agriculture и повышает их скорость.','&6Зачем нужен:&r ускоряет автоматизацию.','&6Как использовать:&r поместите в слот улучшений на машине.']),
 ('can','Лейка','Лейка нужной ступени',['watering_can'],
  ['&6Как работает:&r лейка поливает область вокруг и ускоряет рост культур.','&6Зачем нужен:&r полезна пока автоматизации ещё нет.','&6Как использовать:&r зажмите ПКМ рядом с грядками.']),
 ('har','Жатва','Серп и коса',['sickle','scythe'],
  ['&6Как работает:&r серп собирает урожай по области, коса — по ещё большей.','&6Зачем нужен:&r быстрый ручной сбор.','&6Как использовать:&r ЛКМ по зрелой культуре.']),
 ('tool','Инструменты','Меч, кирка, топор, лопата, мотыга',['sword','pickaxe','axe','shovel','hoe'],
  ['&6Как работает:&r полный набор инструментов ступени.','&6Зачем нужен:&r прочнее и эффективнее предыдущих.','&6Как использовать:&r собираются из слитков ступени на верстаке.']),
 ('rng','Снаряжение','Лук, арбалет, удочка, ножницы',['bow','crossbow','fishing_rod','shears'],
  ['&6Как работает:&r дальнобойное и вспомогательное снаряжение ступени.','&6Зачем нужен:&r закрывает все виды деятельности.','&6Как использовать:&r как обычные аналоги.']),
 ('arm','Броня','Полный комплект',['helmet','chestplate','leggings','boots'],
  ['&6Как работает:&r четыре части брони дают защиту выше предыдущей ступени.','&6Зачем нужен:&r полный комплект — надёжная защита.','&6Как использовать:&r соберите все четыре части.']),
 ('stf','Посох','Посох ступени',['staff'],
  ['&6Как работает:&r магический посох ступени.','&6Зачем нужен:&r особое оружие/инструмент мода.','&6Как использовать:&r ПКМ в нужном режиме.']),
]
quests=[]
prev_row=None
for r,(t,name,gen) in enumerate(T):
    prev=None
    for c,(ck,ctitle,csub,names,desc) in enumerate(COLS):
        items=own(t,names)
        if not items: continue
        k=f'{t}_{ck}'
        tasks=[{'type':'item','id':i,'count':1} for i in items]
        deps=[]
        if prev: deps.append(prev)
        else:
            deps.append(ESS[t])
            if prev_row: deps.append(prev_row)
        rewards=[{'type':'xp','amount':10+5*r}]
        if ck in('arm','tool'): rewards.append({'type':'item','id':f'mysticalagriculture:{t}_essence','count':4})
        quests.append({'k':k,'title':f'{name}: {ctitle.lower()}','sub':csub,'desc':desc,'x':c*2.0,'y':r*2.0,
          'icon':f'mysticalagriculture:{t}_chestplate' if ck=='arm' else None,'optional':ck in('stf',),'tasks':tasks,'rewards':rewards,'deps':deps,
          'note':'Описание по общим знаниям о моде, свериться в игре'})
        prev=k
        if ck=='mat': first=k
    prev_row=f'{t}_mat'
quests.append({'k':'master','title':'Мастер мистического снаряжения','sub':'Пробуждённая броня','desc':['Вы прошли все ступени Mystical Agriculture.'],'x':18.0,'y':10.0,'icon':'mysticalagriculture:awakened_supremium_chestplate','optional':False,
 'tasks':[{'type':'item','id':'mysticalagriculture:awakened_supremium_chestplate','count':1}],'rewards':[{'type':'xp','amount':100}],'deps':['awakened_supremium_arm']})
json.dump({'key':'ma_gear','id':'','title':'Ступени мастерства','subtitle':'Mystical Agriculture — снаряжение всех ступеней','group':'Магия и поселения','icon':'mysticalagriculture:supremium_chestplate','order':10,'quests':quests},open('data/ma_gear.json','w'),ensure_ascii=False,indent=1)
print(len(quests))
