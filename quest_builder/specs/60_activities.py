"""Квесты активности: исследование мира, охота на мобов, хозяйство."""
G = "Хозяйство и путешествия"


def biome(b_):
    return {"type": "biome", "id": b_}


def struct(s):
    return {"type": "structure", "id": s}


def kill(e, n, title):
    return {"type": "kill", "id": e, "count": n, "title": title}


def run(b):
    # ================= Исследование =================
    X = "act_explore"
    b.chapter(X, "Дальние горизонты", "Исследование мира: биомы, руины, города", G, "minecraft:filled_map", 19,
              intro=("Дальние горизонты", "Мир большой — посмотрите его",
                     ["Здесь квесты на путешествия. Задачи засчитываются сами, когда вы входите в биом или постройку.",
                      "Компасы из главы «Путеводная стрелка» помогут найти цели быстрее."], ["ec_compass/explorerscompass"]
                     if "ec_compass/explorerscompass" in b.ref2q else []))
    biomes = [
        ("Тёплые края", [("minecraft:desert", "Пустыня"), ("minecraft:savanna", "Саванна"), ("minecraft:badlands", "Бесплодные земли"),
                         ("minecraft:jungle", "Джунгли")]),
        ("Леса и луга", [("minecraft:forest", "Лес"), ("minecraft:birch_forest", "Березняк"), ("minecraft:dark_forest", "Тёмный лес"),
                         ("minecraft:cherry_grove", "Вишнёвая роща"), ("minecraft:flower_forest", "Цветочный лес"),
                         ("minecraft:meadow", "Луг")]),
        ("Холод и горы", [("minecraft:taiga", "Тайга"), ("minecraft:snowy_plains", "Снежная равнина"), ("minecraft:ice_spikes", "Ледяные шипы"),
                          ("minecraft:jagged_peaks", "Зубчатые пики"), ("minecraft:frozen_peaks", "Заснеженные пики")]),
        ("Вода и болота", [("minecraft:swamp", "Болото"), ("minecraft:mangrove_swamp", "Мангровое болото"), ("minecraft:beach", "Пляж"),
                           ("minecraft:mushroom_fields", "Грибные поля"), ("minecraft:deep_ocean", "Глубокий океан"),
                           ("minecraft:warm_ocean", "Тёплый океан")]),
        ("Подземелья", [("minecraft:lush_caves", "Пышные пещеры"), ("minecraft:dripstone_caves", "Карстовые пещеры"),
                        ("minecraft:deep_dark", "Глубокая тьма"), ("yungscavebiomes:frosted_caves", "Морозные пещеры"),
                        ("yungscavebiomes:lost_caves", "Затерянные пещеры")]),
        ("Нижний мир", [("minecraft:nether_wastes", "Пустоши Незера"), ("minecraft:crimson_forest", "Багровый лес"),
                        ("minecraft:warped_forest", "Искажённый лес"), ("minecraft:soul_sand_valley", "Долина песка душ"),
                        ("minecraft:basalt_deltas", "Базальтовые дельты")]),
    ]
    prev = "intro"
    for n, (grp, lst) in enumerate(biomes):
        tasks = [dict(biome(i), title=t) for i, t in lst]
        prev = b.custom(X, f"biomes_{n}", grp, tasks, deps=[prev], xp=30,
                        text=[f"Посетите все биомы: {', '.join(t for _, t in lst)}.",
                              "Природный компас найдёт нужный биом за вас."], sub="Биомы")
    # постройки
    idas = [("castle", "Замок"), ("haunted_manor", "Особняк с привидениями"), ("labyrinth", "Лабиринт"),
            ("pillager_fortress", "Крепость разбойников"), ("necromancers_spire", "Шпиль некроманта"),
            ("abandoned_lighthouse", "Заброшенный маяк"), ("bazaar", "Базар"), ("tudor_pub", "Таверна в стиле Тюдоров"),
            ("treetop_tavern", "Таверна на дереве"), ("frozen_crypt", "Ледяной склеп"), ("ruins_of_the_deep", "Руины глубин"),
            ("tinkers_citadel", "Цитадель мастеров"), ("tree_of_wisdom", "Древо мудрости"), ("snifferhenge", "Нюхачий круг"),
            ("ancient_mines", "Древние шахты"), ("ruined_church", "Разрушенная церковь"), ("collectors_museum", "Музей коллекционера"),
            ("redhorn_guild", "Гильдия Красного Рога"), ("pumpkin_cafe", "Тыквенное кафе"), ("wacky_wares", "Лавка чудес")]
    b.custom(X, "struct_hdr_idas", "Руины и легенды", [{"type": "check", "title": "Начать поиски построек"}], deps=["intro"],
             text=["Integrated Dungeons and Structures разбросал по миру замки, таверны, склепы и лабиринты.",
                   "Каждая задача засчитывается, когда вы входите в постройку."], anchor=True)
    for i, (sid, t) in enumerate(idas):
        b.custom(X, f"idas_{sid}", t, [dict(struct(f"idas:{sid}"), title=t)], xp=40, optional=i >= 8, deps=["struct_hdr_idas"],
                 text=[f"Найдите постройку «{t}».", "Explorer's Compass ищет постройки по названию."], sub="IDAS")
    rs = [("city_nether", "Незерский город"), ("city_overworld", "Город в обычном мире"), ("mansion_birch", "Березовый особняк"),
          ("mansion_desert", "Пустынный особняк"), ("ancient_city_ocean", "Подводный древний город"),
          ("fortress_jungle", "Крепость в джунглях"), ("bastion_underground", "Подземный бастион"), ("pyramid_jungle", "Пирамида джунглей"),
          ("monument_desert", "Пустынный монумент"), ("stronghold_nether", "Крепость Края в Незере")]
    b.custom(X, "struct_hdr_rs", "Новые лица старых построек", [{"type": "check", "title": "Начать поиски"}], deps=["intro"],
             text=["Repurposed Structures добавляет варианты привычных построек для разных биомов: особняки, пирамиды, шахты, города."], anchor=True)
    for i, (sid, t) in enumerate(rs):
        b.custom(X, f"rs_{sid}", t, [dict(struct(f"repurposed_structures:{sid}"), title=t)], xp=40, optional=i >= 5, sub="Repurposed Structures",
                 deps=["struct_hdr_rs"],
                 text=[f"Найдите постройку «{t}»."])
    b.custom(X, "struct_hdr_cs", "Следы инженеров", [{"type": "check", "title": "Начать поиски"}], deps=["intro"],
             text=["Create: Structures Arise добавляет руины фабрик, поезда, воздушные корабли разбойников и мельницы."], anchor=True)
    csa = ["windmill", "createlosttrainstation", "pillagersteampunkairship", "towerofochrum", "crimsite_tower", "create_ruined_castle",
           "darkcastle", "obsidiantemple", "minetrain", "chocolate_pit", "honey_pit", "createcushercrane"]
    for i, a in enumerate(csa):
        aid = f"create_structures_arise:structures/{a}"
        b.adv(X, aid, k=f"csa_{a}", xp=40, optional=i >= 6, deps=["struct_hdr_cs"])
    b.custom(X, "galleon", "Галеон", [dict(struct("supplementaries:galleon"), title="Галеон")], deps=["intro"], xp=50, optional=True,
             text=["Затонувший корабль Supplementaries с сокровищами."])
    b.custom(X, "road_sign", "Дорожный указатель", [{"type": "advancement", "id": "supplementaries:adventure/road_sign"}], deps=["intro"],
             xp=20, optional=True, text=["Указатели у дорог показывают путь к ближайшей деревне."])
    b.custom(X, "farm_struct", "Ферма Farmer's Delight", [dict(struct("farmers_structures:cooks"), title="Дом повара")], deps=["intro"],
             xp=40, optional=True, text=["FarmersStructures добавляет фермы и дома поваров с кухонной утварью Farmer's Delight."])
    b.custom(X, "barbarian_camp", "Лагерь варваров", [dict(struct("minecolonies:barbarian_camp"), title="Лагерь варваров")],
             deps=["intro"], xp=60, optional=True, text=["Лагерь налётчиков MineColonies. Осторожно: там опасно!"])
    b.custom(X, "dims", "Три мира", [{"type": "dimension", "id": "minecraft:the_nether", "title": "Незер"},
                                     {"type": "dimension", "id": "minecraft:the_end", "title": "Край"}],
             deps=["biomes_5"], xp=60, text=["Побывайте в Незере и в Крае."])

    # ================= Охота =================
    H = "act_hunt"
    b.chapter(H, "Тропа охотника", "Сражения с мобами и налётчиками", G, "minecraft:iron_sword", 20,
              intro=("Тропа охотника", "Защитите себя и колонию",
                     ["Квесты на победу над мобами. Счётчик растёт сам, когда вы побеждаете нужных существ.",
                      "&cImproved Mobs&r делает мобов умнее и сильнее со временем — готовьте броню заранее."], []))
    tiers = [
        ("Ночные гости", [("minecraft:zombie", 20, "Зомби"), ("minecraft:skeleton", 20, "Скелеты"), ("minecraft:spider", 10, "Пауки"),
                          ("minecraft:creeper", 10, "Криперы")], 30),
        ("Хозяева тьмы", [("minecraft:enderman", 10, "Эндермены"), ("minecraft:witch", 5, "Ведьмы"), ("minecraft:slime", 10, "Слизни"),
                          ("minecraft:phantom", 5, "Фантомы")], 50),
        ("Разбойники", [("minecraft:pillager", 15, "Разбойники"), ("minecraft:vindicator", 5, "Поборники"), ("minecraft:evoker", 1, "Заклинатель"),
                        ("minecraft:ravager", 1, "Разоритель")], 80),
        ("Огни Незера", [("minecraft:blaze", 10, "Всполохи"), ("minecraft:ghast", 5, "Гасты"), ("minecraft:wither_skeleton", 10, "Скелеты-иссушители"),
                         ("minecraft:piglin_brute", 2, "Пиглины-громилы")], 100),
        ("Глубины", [("minecraft:drowned", 15, "Утопленники"), ("minecraft:guardian", 10, "Стражи"), ("minecraft:elder_guardian", 1, "Древний страж")], 100),
        ("Легенды", [("minecraft:ender_dragon", 1, "Дракон Края"), ("minecraft:wither", 1, "Иссушитель"), ("minecraft:warden", 1, "Хранитель")], 300),
    ]
    prev = "intro"
    for i, (t, lst, xp) in enumerate(tiers):
        prev = b.custom(H, f"hunt_{i}", t, [kill(e, n, f"{tt}: {n}") for e, n, tt in lst], deps=[prev], xp=xp, finale=(i == len(tiers) - 1),
                        text=["Победите: " + ", ".join(f"{tt.lower()} ×{n}" for _, n, tt in lst) + "."])
    raids = [("Варвары", [("barbarian", 10), ("archerbarbarian", 5), ("chiefbarbarian", 1)]),
             ("Пираты", [("pirate", 10), ("archerpirate", 5), ("chiefpirate", 1)]),
             ("Мумии", [("mummy", 10), ("archermummy", 5), ("pharao", 1)]),
             ("Викинги", [("shieldmaiden", 10), ("norsemenarcher", 5), ("norsemenchief", 1)]),
             ("Амазонки", [("amazon", 10), ("amazonspearman", 5), ("amazonchief", 1)]),
             ("Утонувшие пираты", [("drownedpirate", 10), ("drownedarcherpirate", 5), ("drownedchiefpirate", 1)])]
    deps0 = ["mc_guard/guardtower"] if "mc_guard/guardtower" in b.ref2q else ["intro"]
    prev = None
    for i, (t, lst) in enumerate(raids):
        tasks = [kill(f"minecolonies:{e}", n, f"{b_name(e)}: {n}") for e, n in lst]
        prev = b.custom(H, f"raid_{i}", f"Набег: {t.lower()}", tasks, deps=[prev] if prev else ["intro"] + deps0, xp=80,
                        optional=i >= 2,
                        text=[f"Отбейте набег — {t.lower()} нападают на колонию, когда она растёт. Победите налётчиков вместе со стражей."])

    # ================= Хозяйство =================
    Z = "act_farm"
    b.chapter(Z, "Щедрая земля", "Фермерство и кухня: достижения", G, "minecraft:golden_hoe", 21,
              intro=("Щедрая земля", "Работа на земле и у плиты",
                     ["Квесты на достижения Farmer's Delight, Veggies Delight и обычные хозяйственные дела."], ["fd_kitchen/" + "cutting_board"]
                     if "fd_kitchen/cutting_board" in b.ref2q else []))
    fd = ["main/get_fd_seed", "main/craft_knife", "main/use_cutting_board", "main/harvest_straw", "main/plant_rice", "main/get_rich_soil",
          "main/place_organic_compost", "main/get_mushroom_colony", "main/harvest_ropelogged_tomato", "main/place_cooking_pot",
          "main/place_skillet", "main/use_skillet", "main/eat_nourishing_food", "main/place_feast", "main/get_ham",
          "main/hit_raider_with_rotten_tomato", "main/plant_all_crops", "main/obtain_netherite_knife", "main/master_chef"]
    for i, a in enumerate(fd):
        b.adv(Z, f"farmersdelight:{a}", xp=60 if a.endswith(("master_chef", "plant_all_crops")) else 20,
              optional=a in ("main/hit_raider_with_rotten_tomato", "main/obtain_netherite_knife"), deps=[b.anchor_for(Z)])
    vd = ["main/r_first_veggie", "main/r_fruit_veggie", "main/vd_garlic_master", "main/vd_broccoli_master", "main/vd_zucchini_master",
          "main/vd_sweet_potato_master", "main/vd_turnip_master", "main/vd_bellpepper_master", "main/vd_cauliflower_master",
          "main/r_all_veggies"]
    for a in vd:
        b.adv(Z, f"veggiesdelight:{a}", xp=60 if a.endswith("all_veggies") else 20, deps=[b.anchor_for(Z)])
    b.custom(Z, "harvest_big", "Большой урожай", [{"type": "item", "id": "minecraft:wheat", "count": 64},
                                                 {"type": "item", "id": "minecraft:carrot", "count": 64},
                                                 {"type": "item", "id": "minecraft:potato", "count": 64}],
             xp=50, text=["Соберите большой урожай — по 64 пшеницы, моркови и картофеля. Механический комбайн Create очень поможет."])


def b_name(e):
    import lex
    return lex.LANG_RU.get(f"entity.minecolonies.{e}", e)
