"""MineColonies + Structurize + Domum Ornamentum: четыре главы о колонии."""
G = "Магия и поселения"
M = "minecolonies:"


def h(name):
    return M + "blockhut" + name


def run(b):
    # ================= Основание колонии =================
    F = "mc_found"
    b.chapter(F, "Первый камень", "MineColonies — основание колонии", G, h("townhall"), 20,
              intro=("Своя колония", "С чего начинается город",
                     ["MineColonies превращает вас в правителя: жители сами строят, добывают, готовят и защищают город.",
                      "Ваша задача — ставить здания, снабжать строителя материалами и развивать колонию.",
                      "&6Как начать:&r скрафтите лагерь или корабль снабжения, поставьте его — внутри будут ратуша и первые вещи."], []))
    b.add(F, [M + "supplycampdeployer", M + "supplychestdeployer"], k="supply", title="Лагерь или корабль снабжения",
          text="Лагерь ставится на суше, корабль — на воде. При установке появляется постройка с сундуком стартовых вещей.",
          adv="minecolonies:minecolonies/root", anchor=True)
    b.add(F, ["structurize:sceptergold"], k="build_tool", title="Жезл строительства",
          text="Главный инструмент MineColonies: выбирает схему здания, показывает её призраком в мире и отдаёт заказ строителю.",
          deps=["supply"])
    b.add(F, [h("townhall")], k="townhall", deps=["build_tool"], adv="minecolonies:minecolonies/place_townhall",
          text="Ратуша — центр колонии. Её установка создаёт колонию и задаёт её границы.")
    b.add(F, [h("builder")], k="builder", deps=["townhall"], adv="minecolonies:minecolonies/start_builder",
          text="Строитель возводит все остальные здания. Без него колония не растёт.")
    b.adv(F, "minecolonies:minecolonies/check_out_guide", deps=["builder"], optional=True)
    b.adv(F, "minecolonies:minecolonies/build_builder", deps=["builder"])
    b.adv(F, "minecolonies:minecolonies/fulfill_request", deps=["builder"],
          text="Строитель просит ресурсы и инструменты. Откройте его хижину и отдайте нужное — или положите на склад.")
    b.adv(F, "minecolonies:minecolonies/build_town_hall", deps=["adv_minecolonies_build_builder"])
    b.add(F, [h("citizen")], k="citizen", deps=["adv_minecolonies_build_builder"], adv="minecolonies:minecolonies/build_citizen",
          text="Дома нужны, чтобы жители не уходили и чтобы приходили новые. Чем выше уровень дома, тем больше жильцов.")
    b.add(F, [h("tavern")], k="tavern", deps=["citizen"], adv="minecolonies:minecolonies/build_tavern", optional=True,
          text="Таверна селит до четырёх жителей и привлекает гостей, которых можно нанять.")
    b.adv(F, "minecolonies:minecolonies/citizen_eat_food_first", deps=["citizen"],
          text="Голодные жители плохо работают. Положите им еду или постройте столовую.")
    b.add(F, [M + "clipboard"], k="clipboard", deps=["builder"], text="Планшет показывает все открытые запросы жителей в одном списке.")
    b.add(F, [M + "resourcescroll"], k="scroll", deps=["builder"],
          text="Свиток ресурсов показывает, каких материалов не хватает строителю для текущей стройки.")
    b.add(F, [M + "build_goggles"], k="goggles_mc", deps=["builder"], optional=True,
          text="Очки строителя подсвечивают границы зданий и колонии.")
    b.add(F, [M + "blockconstructiontape"], k="tape_mc", deps=["builder"], optional=True)
    b.add(F, [M + "assistanthammer_gold"], k="hammer", deps=["builder"], optional=True,
          text="Молот помощника строителя ускоряет работу строителя, если отдать его ему.")
    b.add(F, ["structurize:sceptersteel", "structurize:shapetool", "structurize:caliper"], k="structurize_tools",
          title="Инструменты Structurize", mode="set", deps=["build_tool"], optional=True,
          text=["Инструмент сканирования сохраняет постройку в схему, инструмент форм строит сферы и цилиндры, циркуль измеряет расстояния."])
    b.adv(F, "minecolonies:minecolonies/build_builder_2", deps=["adv_minecolonies_build_builder"])
    b.add(F, [h("warehouse")], k="warehouse", deps=["adv_minecolonies_build_builder_2"], adv="minecolonies:production/build_warehouse",
          text="Склад хранит все ресурсы колонии. Жители берут оттуда нужное сами, а курьеры разносят вещи.")
    b.add(F, [h("deliveryman")], k="courier", deps=["warehouse"], adv="minecolonies:production/build_delivery_person",
          text="Курьер носит вещи со склада рабочим и забирает у них готовую продукцию.")
    b.add(F, [M + "blockminecoloniesrack"], k="rack", deps=["warehouse"],
          text="Полка — блок хранения склада. Каждая полка добавляет места.")
    b.add(F, [M + "blockpostbox", M + "blockstash"], k="post_stash", title="Почтовый ящик и тайник", mode="set", deps=["courier"],
          adv="minecolonies:production/post_and_stash",
          text="Почтовый ящик заказывает вещи со склада прямо к вам, а в тайник вы складываете вещи, которые курьер заберёт на склад.")
    b.add(F, [M + "colony_banner", M + "colonysign"], k="banner", title="Флаг и знак колонии", deps=["townhall"], optional=True)
    b.add(F, [M + "colonymap"], k="map_mc", deps=["townhall"], optional=True)
    b.add(F, [M + "questlog"], k="questlog", deps=["townhall"],
          text="Журнал заданий. Жители иногда предлагают поручения — выполняйте их ради наград и отношений.")
    b.add(F, [M + "simplequarry", M + "mediumquarry"], k="quarry", title="Карьер", deps=["adv_minecolonies_build_builder_2"],
          optional=True, text="Схема карьера: шахтёр копает его сверху вниз и приносит камень и руду.")
    b.add(F, [M + "blockwaypoint"], k="waypoint", deps=["build_tool"], optional=True,
          text="Контрольные точки задают дорожки, по которым ходят жители.")
    prev = "citizen"
    for n in (5, 10, 25, 50, 75):
        r = b.adv(F, f"minecolonies:minecolonies/colony_population_{n}", deps=[prev], xp=20 + n)
        prev = r
    b.adv(F, "minecolonies:minecolonies/build_town_hall_5", deps=["adv_minecolonies_colony_population_25"], xp=150)
    # Domum Ornamentum
    b.add(F, ["domum_ornamentum:architectscutter"], k="cutter", deps=["builder"],
          text="Резак архитектора делает декоративные блоки Domum Ornamentum: рамы, стены, балки из любых двух материалов. Нужен строителю для многих схем.")
    b.add(F, ["domum_ornamentum:" + x for x in ("cream_bricks", "beige_bricks", "brown_bricks", "roan_bricks", "sand_bricks")],
          k="domum_bricks", title="Кирпичи Domum", deps=["cutter"], optional=True)
    b.add(F, ["domum_ornamentum:white_floating_carpet"], k="floating_carpet", title="Парящий ковёр", deps=["cutter"], optional=True)

    # ================= Производство =================
    P = "mc_craft"
    b.chapter(P, "Руки колонии", "MineColonies — ремёсла и производство", G, h("lumberjack"), 21,
              intro=("Руки колонии", "Рабочие места",
                     ["Каждое рабочее здание даёт жителю профессию. Работник берёт инструменты и ресурсы со склада и сам делает продукцию.",
                      "Начните с лесоруба, шахтёра и фермера — они дают основу всех материалов."], [f"{F}/builder"]))

    def hut(k, name, text, deps, adv=None, opt=False, xp=25):
        return b.add(P, [h(name)], k=k, text=text, deps=deps, adv=adv, optional=opt, xp=xp)

    hut("lumberjack", "lumberjack", "Лесоруб рубит деревья и сажает саженцы. Даёт бревна для всех построек.", ["intro"],
        "minecolonies:production/build_lumberjack")
    hut("miner", "miner", "Шахтёр копает шахту вниз уровнями и приносит камень, уголь и руду.", ["intro"],
        "minecolonies:production/build_miner")
    b.adv(P, "minecolonies:production/deep_mine", deps=["miner"], optional=True, xp=60)
    hut("farmer", "farmer", "Фермер возделывает поля. Поставьте рядом поле и выберите семена.", ["intro"],
        "minecolonies:production/build_farmer")
    b.add(P, [h("field")], k="field", deps=["farmer"], text="Поле размечает грядку для фермера. До 5 полей на одного фермера.")
    b.adv(P, "minecolonies:production/max_fields", deps=["field"], optional=True)
    hut("fisherman", "fisherman", "Рыбак ловит рыбу и сокровища у ближайшей воды.", ["intro"], "minecolonies:production/build_fisherman")
    hut("cook", "cook", "Столовая: повар готовит еду и кормит жителей. Сытые жители работают быстрее.", ["farmer", "fisherman"],
        "minecolonies:production/build_cook")
    hut("baker", "baker", "Пекарь печёт хлеб, торты и пироги из пшеницы и других продуктов.", ["farmer"], "minecolonies:production/build_baker")
    hut("kitchen", "kitchen", "Кухня шеф-повара готовит сложные блюда колонии — сытнее обычной еды.", ["cook"], opt=True)
    hut("sawmill", "sawmill", "Плотник на лесопилке крафтит из дерева: доски, двери, сундуки. Ему можно обучить рецепты.",
        ["lumberjack"], "minecolonies:production/build_sawmill")
    b.adv(P, "minecolonies:minecolonies/building_add_recipe_door", deps=["sawmill"], optional=True)
    hut("stonemason", "stonemason", "Каменщик крафтит из камня: кирпичи, ступени, плиты.", ["miner"], "minecolonies:production/build_stonemason")
    hut("smeltery", "smeltery", "Плавильщик переплавляет руду. На высоких уровнях даёт больше слитков из руды.", ["miner"],
        "minecolonies:production/build_smeltery")
    b.adv(P, "minecolonies:production/build_smeltery_3", deps=["smeltery"], optional=True)
    b.adv(P, "minecolonies:production/build_smeltery_5", deps=["adv_production_build_smeltery_3"], optional=True, xp=80)
    hut("stonesmeltery", "stonesmeltery", "Кирпичный двор обжигает камень, глину и стекло.", ["stonemason"],
        "minecolonies:production/build_stone_smeltery")
    hut("crusher", "crusher", "Дробильщик превращает булыжник в гравий, песок и пыль.", ["stonemason"], "minecolonies:production/build_crusher")
    hut("sifter", "sifter", "Просеиватель ищет руду и редкости в гравии, песке и земле. Нужны сетки.", ["crusher"],
        "minecolonies:production/build_sifter")
    hut("blacksmith", "blacksmith", "Кузнец делает инструменты, оружие и броню для жителей.", ["smeltery"],
        "minecolonies:production/build_blacksmith")
    hut("composter", "composter", "Компостёр перерабатывает отходы в компост — лучшее удобрение для полей.", ["farmer"],
        "minecolonies:production/build_composter")
    b.add(P, [M + "barrel_block", M + "composted_dirt"], k="compost_blocks", title="Бочка и компостированная земля", mode="set",
          deps=["composter"], optional=True)
    hut("florist", "florist", "Флорист выращивает цветы — нужны для красителей и украшений.", ["composter"],
        "minecolonies:production/build_florist")
    hut("plantation", "plantation", "Плантатор выращивает тростник, кактусы, бамбук и другие особые растения.", ["farmer"])
    b.add(P, [h("plantationfield")], k="plantationfield", deps=["plantation"])
    hut("beekeeper", "beekeeper", "Пасечник собирает мёд и соты с ульев.", ["florist"])
    for k, nm, t in [("chicken", "chickenherder", "Птичник разводит кур: яйца, перья, мясо."),
                     ("cow", "cowboy", "Скотовод разводит коров: молоко, кожа, говядина."),
                     ("swine", "swineherder", "Свинопас разводит свиней."),
                     ("shepherd", "shepherd", "Пастух разводит овец и стрижёт шерсть."),
                     ("rabbit", "rabbithutch", "Кроликовод разводит кроликов.")]:
        hut(k, nm, t, ["farmer"])
    b.adv(P, "minecolonies:production/build_all_herders", deps=["chicken", "cow", "swine", "shepherd", "rabbit"], xp=60)
    hut("glassblower", "glassblower", "Стеклодув плавит песок в стекло и делает стеклянные панели.", ["stonesmeltery"])
    hut("dyer", "dyer", "Красильщик делает красители и красит шерсть, стекло и терракоту.", ["florist"])
    hut("concrete", "concretemixer", "Бетономешалка делает сухой и твёрдый бетон всех цветов.", ["dyer", "crusher"])
    hut("fletcher", "fletcher", "Стрельник делает стрелы, луки и арбалеты для стражи.", ["sawmill"])
    hut("mechanic", "mechanic", "Механик делает редстоун-устройства: поршни, воронки, рельсы.", ["blacksmith"])
    hut("netherworker", "netherworker", "Работник Нижнего мира уходит в ад через портал и приносит его ресурсы.", ["blacksmith"])
    hut("alchemist", "alchemist", "Алхимик варит зелья и выращивает незерские наросты.", ["netherworker"])
    b.add(P, [M + "sifter_mesh_string", M + "sifter_mesh_flint", M + "sifter_mesh_iron", M + "sifter_mesh_diamond"],
          k="meshes", title="Сетки просеивателя", mode="any", deps=["sifter"], optional=True)

    # ================= Знание и жизнь =================
    K = "mc_life"
    b.chapter(K, "Свет знаний", "MineColonies — обучение, здоровье и чудеса", G, h("university"), 22,
              intro=("Свет знаний", "Сильная и умная колония",
                     ["Образованные и здоровые жители работают лучше. Университет открывает исследования — новые здания и улучшения."],
                     [f"{F}/adv_minecolonies_build_builder_2"]))
    b.add(K, [h("library")], k="library", deps=["intro"], adv="minecolonies:production/build_library",
          text="В библиотеке жители учатся и повышают навыки.")
    b.add(K, [h("school")], k="school", deps=["intro"], text="В школе учатся дети колонии и вырастают умнее.")
    b.add(K, [h("university")], k="university", deps=["library"],
          text="Университет — исследования колонии: новые постройки, бонусы, улучшения стражи и жителей.")
    b.add(K, [h("hospital")], k="hospital", deps=["intro"], text="Лекарь лечит больных жителей. Болезни снижают работоспособность.")
    b.add(K, [h("graveyard")], k="graveyard", deps=["hospital"],
          text="Могильщик хоронит погибших жителей и может воскресить их.")
    b.adv(K, "minecolonies:minecolonies/citizen_bury", deps=["graveyard"], optional=True)
    b.adv(K, "minecolonies:minecolonies/citizen_resurrect", deps=["graveyard"], optional=True, xp=60)
    b.add(K, [h("enchanter")], k="enchanter", deps=["library"], adv="minecolonies:production/build_enchanter",
          text="Чародей зачаровывает книги, поглощая древние тома и опыт рабочих.")
    b.add(K, [h("mysticalsite")], k="mysticalsite", deps=["intro"], adv="minecolonies:minecolonies/build_mysticalsite",
          optional=True, text="Мистическое место ничего не производит, но повышает счастье жителей.")
    b.adv(K, "minecolonies:minecolonies/build_mysticalsite_5", deps=["mysticalsite"], optional=True, xp=100)
    b.add(K, [M + "large_empty_bottle", M + "large_water_bottle", M + "large_soy_milk_bottle"], k="bottles",
          title="Большие бутылки", mode="any", deps=["intro"], optional=True)
    foods = [("mc_soups", "Супы колонистов", ["cabochis", "chicken_broth", "corn_chowder", "eggdrop_soup", "pea_soup",
                                                "potato_soup", "squash_soup", "veggie_soup", "pottage"]),
             ("mc_mains", "Горячее колонистов", ["baked_salmon", "fish_n_chips", "kebab", "mutton_dinner", "spicy_grilled_chicken",
                                                  "fried_rice", "congee", "polenta", "pierogi", "meat_ravioli", "cheese_ravioli",
                                                  "veggie_ravioli", "pasta_plain", "pasta_tomato"]),
             ("mc_bakery", "Выпечка колонистов", ["apple_pie", "manchet_bread", "muffin", "cheese_pizza", "veggie_quiche",
                                                   "tortillas", "plain_cheesecake", "rice_ball"]),
             ("mc_dairy", "Молочная лавка", ["butter", "cheddar_cheese", "feta_cheese", "creamcheese", "yogurt", "yogurt_with_berries",
                                              "tofu", "soysauce", "kimchi", "mint_jelly", "mint_tea", "pepper_hummus"])]
    for k, t, items in foods:
        b.add(K, [M + x for x in items], k=k, title=t, deps=["intro"], optional=True,
              text=f"Блюда колонии. Их готовят повар и шеф-повар — получите любое блюдо из группы «{t.lower()}».")

    # ================= Стража =================
    D = "mc_guard"
    b.chapter(D, "Щит колонии", "MineColonies — стража и оборона", G, h("barracks"), 23,
              intro=("Щит колонии", "Набеги и защита",
                     ["Когда колония растёт, на неё нападают варвары, пираты, мумии и викинги.",
                      "Стражники защищают жителей и границы. Чем больше башен и казарм, тем сильнее армия."], [f"{F}/citizen"]))
    b.add(D, [h("guardtower")], k="guardtower", deps=["intro"], adv="minecolonies:minecolonies/build_guard_tower",
          text="Сторожевая башня нанимает одного стражника: рыцаря, лучника или друида.")
    b.add(D, [M + "scepterguard"], k="scepter", deps=["guardtower"], optional=True,
          text="Жезл стражи задаёт стражнику точку патруля или охраны.")
    b.add(D, [M + "banner_rally_guards"], k="rally", deps=["guardtower"], optional=True,
          text="Вывеска охраны собирает стражей вокруг вас — удобно в походах и при обороне.")
    b.add(D, [h("gatehouse")], k="gatehouse", deps=["guardtower"], text="Застава расширяет защищённую зону и охраняет вход в колонию.")
    b.add(D, [h("barracks")], k="barracks", deps=["guardtower"], adv="minecolonies:military/build_barracks",
          text="Казармы вмещают несколько башен с отрядами стражей.")
    b.adv(D, "minecolonies:military/build_barracks_tower", deps=["barracks"])
    b.adv(D, "minecolonies:military/build_all_barracks_towers", deps=["adv_military_build_barracks_tower"], xp=80)
    b.add(D, [h("archery")], k="archery", deps=["barracks"], adv="minecolonies:military/build_archery",
          text="На стрельбище лучники тренируют меткость.")
    b.add(D, [h("combatacademy")], k="academy", deps=["barracks"], adv="minecolonies:military/build_combat_academy",
          text="В боевой академии рыцари тренируют владение мечом.")
    b.add(D, [M + "spear"], k="spear", deps=["guardtower"], optional=True)
    prev = "guardtower"
    for n in (8, 30, 80):
        prev = b.adv(D, f"minecolonies:military/army_{n}", deps=[prev], xp=30 + n)
    b.add(D, [h("barracks")], k="finale_guard", title="Неприступная колония", finale=True,
          deps=["adv_military_army_30", "academy", "archery", "gatehouse"], xp=150,
          text="Ваша армия готова отразить любой набег.")
