"""Награды и финалы: тематические предметы для квестов без предметной награды
и финальный квест в каждой главе, где его ещё нет. Запускается последним."""
import lex

POOL = {
    "vanilla_progress": [("minecraft:bread", 8), ("minecraft:iron_ingot", 4), ("minecraft:torch", 16), ("minecraft:cooked_beef", 8)],
    "create_andesite": [("create:andesite_alloy", 8), ("create:cogwheel", 4), ("create:shaft", 8)],
    "create_brass": [("create:brass_ingot", 4), ("create:electron_tube", 2), ("create:zinc_ingot", 4)],
    "create_fluids": [("create:fluid_pipe", 8), ("create:copper_sheet", 4)],
    "create_logistics": [("create:andesite_casing", 4), ("create:cardboard", 8)],
    "create_contraptions": [("create:andesite_casing", 4), ("create:super_glue", 1)],
    "create_misc": [("create:andesite_alloy", 8), ("create:brass_ingot", 2)],
    "create_trains": [("create:track", 8), ("create:brass_casing", 2)],
    "create_connected_ch": [("create:andesite_alloy", 8), ("create:brass_ingot", 2)],
    "create_enchant_ch": [("minecraft:experience_bottle", 4), ("minecraft:lapis_lazuli", 8)],
    "create_inv": [("create:brass_ingot", 4), ("create:zinc_ingot", 8), ("create:copper_sheet", 4)],
    "ca_electric": [("createaddition:copper_wire", 8), ("create:copper_sheet", 4)],
    "sim_physics": [("create:andesite_casing", 4), ("create:iron_sheet", 4), ("simulated:honey_glue", 1)],
    "aero_fleet": [("create:iron_sheet", 8), ("create:brass_casing", 2), ("createpropulsion:platinum_ingot", 2)],
    "off_road": [("create:andesite_casing", 4), ("create:iron_sheet", 4)],
    "fd_kitchen": [("minecraft:bread", 8), ("minecraft:cooked_beef", 4), ("farmersdelight:onion", 4)],
    "vd_veggies": [("minecraft:bread", 8), ("minecraft:carrot", 8)],
    "sb_backpacks": [("minecraft:leather", 8), ("minecraft:iron_ingot", 4)],
    "ss_storage": [("minecraft:oak_planks", 16), ("minecraft:iron_ingot", 4)],
    "toms": [("minecraft:chest", 2), ("minecraft:iron_ingot", 4)],
    "sd_drawers": [("minecraft:oak_planks", 16), ("minecraft:iron_ingot", 4)],
    "ec_compass": [("minecraft:bread", 8), ("minecraft:ender_pearl", 1)],
    "supp": [("supplementaries:rope", 16), ("supplementaries:flax", 8), ("minecraft:candle", 4)],
    "act_explore": [("minecraft:cooked_beef", 16), ("minecraft:golden_carrot", 8), ("minecraft:ender_pearl", 2)],
    "act_hunt": [("minecraft:arrow", 32), ("minecraft:cooked_beef", 16), ("minecraft:golden_apple", 1)],
    "act_farm": [("minecraft:bone_meal", 32), ("farmersdelight:rich_soil", 4), ("minecraft:wheat_seeds", 16)],
    "ma_farm": [("minecraft:bone_meal", 16), ("mysticalagriculture:inferium_essence", 8)],
    "ma_altars": [("mysticalagriculture:inferium_essence", 16), ("mysticalagriculture:prudentium_essence", 4)],
    "ma_gear": [("mysticalagriculture:inferium_essence", 16), ("mysticalagriculture:prosperity_shard", 4)],
    "ma_add": [("mysticalagriculture:supremium_essence", 4), ("mysticalagriculture:imperium_essence", 8)],
    "mc_found": [("minecraft:oak_log", 32), ("minecraft:cobblestone", 64), ("minecraft:bread", 16)],
    "mc_craft": [("minecraft:oak_planks", 32), ("minecraft:iron_ingot", 8), ("minecraft:coal", 16)],
    "mc_life": [("minecraft:book", 4), ("minecraft:emerald", 4), ("minecraft:bread", 16)],
    "mc_guard": [("minecraft:arrow", 32), ("minecraft:cooked_beef", 16), ("minecraft:iron_ingot", 8)],
}

# финал: (название, текст, большие награды)
FINALE = {
    "vanilla_progress": ("Покоритель миров", "Вы прошли весь путь обычного Minecraft: от первого дерева до Края.",
                         [("minecraft:diamond", 4), ("minecraft:golden_apple", 2)]),
    "create_andesite": ("Мастер вращения", "Андезитовая эпоха позади: валы, шестерни, жернова и прессы работают на вас.",
                        [("create:andesite_alloy", 32), ("create:brass_ingot", 4)]),
    "create_brass": ("Латунный инженер", "Вы освоили латунь, механизмы точности и чертежи.",
                     [("create:precision_mechanism", 2), ("create:brass_ingot", 16)]),
    "create_fluids": ("Повелитель пара", "Трубы, насосы, баки и паровые двигатели — всё под контролем.",
                      [("create:fluid_tank", 4), ("create:copper_sheet", 16)]),
    "create_logistics": ("Почтмейстер", "Посылки, лягушки-отправщики и склады работают без вашего участия.",
                         [("create:cardboard", 32), ("create:brass_casing", 4)]),
    "create_contraptions": ("Душа машины", "Ваши механизмы сами двигаются, копают и строят.",
                            [("create:super_glue", 2), ("create:andesite_casing", 16)]),
    "create_misc": ("Уютная мастерская", "Инструменты и украшения Create собраны.", [("create:brass_ingot", 8)]),
    "create_trains": ("Начальник дороги", "Поезда ходят по расписанию между станциями.",
                      [("create:track", 32), ("create:railway_casing", 8)]),
    "create_connected_ch": ("Мастер передач", "Все хитрые передачи и узлы Create: Connected освоены.",
                            [("create:brass_ingot", 16)]),
    "create_enchant_ch": ("Хранитель знаний", "Опыт течёт по трубам, а чары накладываются автоматически.",
                          [("minecraft:experience_bottle", 16)]),
    "create_inv": ("Изобретатель", "Все маленькие аддоны Create освоены.", [("create:brass_ingot", 8), ("create:zinc_ingot", 16)]),
    "ca_electric": ("Электрификация", "Генераторы, провода и аккумуляторы — у вас есть электричество.",
                    [("createaddition:copper_spool", 2), ("create:copper_sheet", 16)]),
    "fd_kitchen": ("Шеф-повар", "Ваша кухня готовит любые блюда.", [("farmersdelight:rich_soil", 16), ("minecraft:golden_carrot", 16)]),
    "vd_veggies": ("Огородник", "Все овощи Veggies Delight выращены и приготовлены.", [("minecraft:golden_carrot", 16)]),
    "sb_backpacks": ("Странник налегке", "Рюкзак с улучшениями стал вашим лучшим спутником.", [("minecraft:diamond", 2)]),
    "ss_storage": ("Хранитель сокровищ", "Сундуки, бочки и улучшения — склад в идеальном порядке.", [("minecraft:diamond", 2)]),
    "toms": ("Единый склад", "Все сундуки соединены в одну сеть.", [("minecraft:diamond", 1), ("minecraft:iron_ingot", 16)]),
    "sd_drawers": ("Порядок во всём", "Ящики, контроллер и ключи — склад рассортирован.", [("minecraft:diamond", 2)]),
    "ec_compass": ("Первооткрыватель", "Компасы и обелиски приведут вас куда угодно.", [("minecraft:ender_pearl", 4)]),
    "supp": ("Хозяин мелочей", "Вы попробовали полезные мелочи Supplementaries.", [("supplementaries:rope", 32)]),
    "act_explore": ("Великий путешественник", "Вы повидали биомы, руины и города этого мира.",
                    [("minecraft:diamond", 4), ("minecraft:ender_pearl", 8)]),
    "act_farm": ("Щедрый урожай", "Ваша ферма и кухня работают в полную силу.", [("minecraft:golden_carrot", 32)]),
    "ma_farm": ("Мистический садовник", "Ресурсы растут на ваших грядках.", [("mysticalagriculture:prudentium_essence", 16)]),
    "ma_altars": ("Хранитель алтарей", "Инфузия, пробуждение и души подчинены вам.", [("mysticalagriculture:imperium_essence", 8)]),
    "ma_add": ("За гранью", "Инсаний и особые основания освоены.", [("mysticalagradditions:insanium_essence", 4)]),
    "mc_found": ("Отец-основатель", "Колония стоит, растёт и снабжается.", [("minecraft:emerald", 8), ("minecraft:iron_ingot", 16)]),
    "mc_craft": ("Город мастеров", "Все ремёсла колонии работают.", [("minecraft:emerald", 8)]),
    "mc_life": ("Просвещённая колония", "Жители учатся, лечатся и счастливы.", [("minecraft:emerald", 8), ("minecraft:golden_apple", 1)]),
}


def ok(i):
    return i.startswith("minecraft:") or i in lex.IDS


def run(b):
    # ---------- обычные квесты ----------
    for key, c in b.ch.items():
        pool = [(i, n) for i, n in POOL.get(key, []) if ok(i)]
        if not pool:
            continue
        n = 0
        cnt = 0
        for q in c["quests"]:
            if q.get("optional") or q.get("finale") or any(r["type"] == "item" for r in q["rewards"]):
                continue
            cnt += 1
            if cnt % 4 == 0:
                i, k = pool[n % len(pool)]
                n += 1
                q["rewards"].append({"type": "item", "id": i, "count": k})
    # ---------- финалы ----------
    for key, (title, text, rew) in FINALE.items():
        if key not in b.ch:
            continue
        c = b.ch[key]
        for q in c["quests"]:
            if q.get("k") == "master":
                q["finale"] = True
        if any(q.get("finale") for q in c["quests"]):
            continue
        # зависимости: самые «глубокие» обязательные квесты главы
        refs = [b.ref(c, q) for q in c["quests"] if not q.get("optional")]
        depth = {}

        def d(r, stack=()):
            if r in depth:
                return depth[r]
            if r in stack:
                return 0
            ps = [p for p in b.deps.get(r, []) if b.ref2q.get(p, ("",))[0] == key]
            depth[r] = 0 if not ps else 1 + max(d(p, stack + (r,)) for p in ps)
            return depth[r]

        for r in refs:
            d(r)
        has_child = {p for r in refs for p in b.deps.get(r, [])}
        leaves = [r for r in refs if r not in has_child]
        leaves.sort(key=lambda r: -depth.get(r, 0))
        deps = leaves[:4]
        rewards = [{"type": "xp", "amount": 200}] + [{"type": "item", "id": i, "count": k} for i, k in rew if ok(i)]
        b.custom(key, "finale", title, [{"type": "check", "title": "Подвести итог главы"}], deps=deps,
                 text=[text, "&7Откроется, когда будут выполнены последние квесты главы. Отметьте галочку и заберите награду."],
                 rewards=rewards, finale=True, sub="Финал главы")
