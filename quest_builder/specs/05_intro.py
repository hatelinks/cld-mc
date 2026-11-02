"""Главы без группы в самом верху: «Обучение» (квесты открываются по одному) и «Полезно знать»
(особенности сборки и моды без предметов). Картинки из ресурспака resourcepack/ (namespace cldquests)."""

LOGO = {"image": "cldquests:textures/quests/logo.png", "width": 6.0, "height": 0.86}
HIDE = {"hide_until_deps_complete": True}


def check(b, key, k, title, sub, text, deps, icon=None, xp=10, rewards=None, flags=None, shape=None, size=None, tex=None):
    r = b.custom(key, k, title, [{"type": "check", "title": "Прочитано"}], deps=deps, text=text, xp=xp, icon=icon, sub=sub,
                 rewards=rewards)
    q = b.ref2q[r][1]
    if flags:
        q["flags"] = dict(flags)
    if shape:
        q["shape"], q["size"] = shape, size or 1.0
    if tex:
        q["icon_tex"] = tex
    return r


def run(b):
    # ================= Обучение =================
    T = "tutorial"
    b.chapter(T, "Обучение", "С чего начать в TelipailoCraft", "", "minecraft:writable_book", -2)
    c = b.ch[T]
    c["color"] = "&b"
    c["layout"] = "manual"
    c["images"] = [dict(LOGO, x=2.0, y=-2.2)]
    w = check(b, T, "welcome", "Добро пожаловать в &b&lTelipailoCraft&r!", "Нажмите на этот квест и прочитайте описание",
              ["Это сборка про &6Create&r, колонии &6MineColonies&r, мистическое земледелие и большой мир &6TelipailoCraft&r.",
               "Книга квестов ведёт по сборке шаг за шагом. Квест открывается, когда выполнены те, от которых он зависит: линии показывают порядок.",
               "Отметьте галочку справа внизу и заберите награду. После этого откроются следующие квесты обучения."],
              [], icon="minecraft:writable_book", rewards=[{"type": "item", "id": "minecraft:bread", "count": 8}], shape="square", size=2.0)
    inv = check(b, T, "inventory", "Инвентарь и команда", "Играть можно вместе",
                ["Нажмите &bE&r, чтобы открыть инвентарь. Слева вверху кнопки книги квестов и команды &6FTB Teams&r.",
                 "В команде квесты общие: если друг выполнил квест, он засчитан всем. Позвать друга можно кнопкой команды.",
                 "&bB&r открывает рюкзак, &bG&r слоты аксессуаров."],
                [w], icon="minecraft:chest", flags=HIDE)
    book = check(b, T, "questbook", "Книга квестов", "Как читать квесты",
                 ["Слева список глав, они собраны в группы. &bTab&r и &bShift+Tab&r листают главы, колесо мыши и &b+&r, &b-&r меняют масштаб.",
                  "&6Квадрат&r это приветствие главы, &6шестерня&r важная веха, &6шестиугольник&r финал главы с большой наградой на выбор.",
                  "Предметные квесты засчитываются сами, когда нужный предмет оказывается у вас в инвентаре. Предметы не забираются.",
                  "Квест можно закрепить (кнопка с булавкой): он будет виден справа на экране."],
                 [w], icon="minecraft:writable_book", flags=HIDE)
    keys = check(b, T, "keys", "Наши клавиши", "Что есть только в этой сборке",
                 ["{image:cldquests:textures/quests/keys.png width:300 height:75 align:center fit:true}",
                  "&bF7&r настройки дальнего рельефа cld_far. &bJ&r карта JourneyMap. &bY&r панель тестирования (если стоит).",
                  "&bW&r над предметом Create в JEI или инвентаре: анимированная инструкция Ponder.",
                  "&bEsc&r, кнопка &6TC Моды&r: настройки всех наших модов в одном месте."],
                 [w], icon="minecraft:compass", flags=HIDE)
    jei = check(b, T, "jei", "Рецепты в JEI", "Как найти любой рецепт",
                ["Справа в инвентаре список предметов &6JEI&r. Наведите на предмет и нажмите &bR&r: рецепт, &bU&r: где используется.",
                 "Поиск &b@create&r показывает предметы одного мода, &b#&r ищет по подсказке. &bA&r добавляет предмет в закладки.",
                 "Если у рецепта несколько вариантов результата, &6Polymorph&r покажет кнопку выбора прямо в верстаке."],
                [w], icon="minecraft:crafting_table", flags=HIDE)
    check(b, T, "finish", "Обучение пройдено", "Дальше сами",
          ["Теперь откройте главу &6Полезно знать&r, а потом начинайте с &6Путь выжившего&r и &6Эпохи андезита&r.",
           "Главы из групп открыты сразу, но удобнее идти сверху вниз."],
          [inv, book, keys, jei], icon="minecraft:nether_star", xp=50, flags=HIDE, shape="hexagon", size=2.0,
          rewards=[{"type": "xp", "amount": 50}, {"type": "random", "table": "food"}])
    pos = {"welcome": (2, 0.5), "inventory": (0, -0.5), "questbook": (1.33, -1.0), "keys": (2.67, -1.0), "jei": (4, -0.5), "finish": (2, 2.5)}
    for q in c["quests"]:
        if q.get("k") in pos:
            q["x"], q["y"] = pos[q["k"]]

    # ================= Полезно знать =================
    K = "to_know"
    b.chapter(K, "Полезно знать", "Особенности сборки и моды без предметов", "", "minecraft:knowledge_book", -1)
    c = b.ch[K]
    c["color"] = "&a"
    c["layout"] = "manual"
    c["images"] = [dict(LOGO, x=3.0, y=-2.2)]
    tips = [
        ("tcw", "Мир TelipailoCraft", "Горы до неба, реки и ледники", "minecraft:grass_block",
         ["Тип мира по умолчанию &6TelipailoCraft&r: высота от -128 до 640, горы с ледниками, реки, пещеры и горные биомы.",
          "&b/locate tcw&r ищет особые места, &b/tcwmap&r рисует карту. Изменения генерации видны только в новых чанках."]),
        ("far", "Дальний рельеф", "Горизонт без лагов", "minecraft:spyglass",
         ["&6cld_far&r рисует горы за пределами прорисовки без загрузки чанков. Настройки на &bF7&r, пресеты и дальность там же.",
          "В пещерах дальний рельеф выключается сам."]),
        ("bombs", "Бомбы с уровнями", "Прокачка порохом", "minecraft:gunpowder",
         ["Бомбы Supplementaries получают уровни от 1 до 10: соедините бомбу с порохом. Чем выше уровень, тем больше кратер.",
          "Бомбы с уровнем попадаются и в сундуках структур."]),
        ("colony", "Колония рядом", "MineColonies", "minecolonies:supplycampdeployer",
         ["Колония MineColonies защищена от монстров и чужих разрушений. Жители строят по схемам, а вы даёте им материалы.",
          "Глава &6Жизнь колонии&r ведёт от лагеря снабжения до большого города."]),
        ("android", "Игра на Android", "Что отличается", "minecraft:redstone_torch",
         ["На телефоне нет Sodium и шейдеров, а моды на физике кораблей (Aeronautics, двигатели, субмарины) нагружают сильнее.",
          "Если FPS низкий: уменьшите дальность в &bF7&r и прорисовку чанков."]),
        ("polymorph", "Polymorph", "Один рецепт, разные результаты", "minecraft:crafting_table",
         ["Если одинаковые ингредиенты дают разные предметы из разных модов, в верстаке появится кнопка выбора результата."]),
        ("corpse", "Могила после смерти", "Corpse", "minecraft:skeleton_skull",
         ["После смерти вещи остаются в теле на месте гибели. Подойдите и нажмите &bПКМ&r, чтобы забрать их. Тело не исчезает."]),
        ("controlling", "Поиск клавиш", "Controlling и Searchables", "minecraft:oak_sign",
         ["В настройках управления есть поиск по названию и по клавише, а конфликты подсвечены. Поиск есть и в других списках настроек."]),
        ("mousetweaks", "Быстрый инвентарь", "Mouse Tweaks", "minecraft:hopper",
         ["Зажмите &bПКМ&r и проведите по слотам, чтобы разложить стопку. &bShift&r и проведите, чтобы перенести много предметов. Колесо мыши перекладывает по одному."]),
        ("enchdesc", "Что делают чары", "Enchantment Descriptions", "minecraft:enchanted_book",
         ["Наведите на зачарованную книгу и зажмите &bShift&r: появится описание каждого зачарования."]),
        ("jep", "Профессии и разведение", "Just Enough Professions и Breeding", "minecraft:emerald",
         ["В JEI есть вкладки: какой блок даёт какую профессию жителю и чем кормить животных для разведения."]),
        ("neruina", "Защита от вылетов", "Neruina", "minecraft:shield",
         ["Если блок или существо ломает тик мира, &6Neruina&r останавливает только его и пишет в чат, вместо вылета игры."]),
        ("waystones", "Путевые камни", "Делаются на механическом крафтере", "waystones:waystone",
         ["Путевые камни в этой сборке собираются на &6механическом крафтере Create&r, обычного рецепта нет.",
          "Найденные в мире камни работают как обычно."]),
    ]
    for n, (k, t, sub, icon, text) in enumerate(tips):
        check(b, K, k, t, sub, text, [], icon=icon if (icon.startswith("minecraft:") or icon in b_ids()) else "minecraft:book")
        q = b.ref2q[f"{K}/{k}"][1]
        q["x"], q["y"] = (n % 5) * 1.5, (n // 5) * 1.5
        if n < 5:
            q["shape"], q["size"] = "gear", 1.25


def b_ids():
    import lex
    return lex.IDS
