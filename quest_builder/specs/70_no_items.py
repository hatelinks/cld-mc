"""Моды без собственных предметов: Create Ultimate Factory, WarNTaxes, Carry On, Climbable Ropes,
Contraption Terminals, Waystones Sable, AtmosphericA."""
NOTE = "Мод без предметов: квест-подсказка, проверить в игре"


def run(b):
    # ================= Create: Ultimate Factory =================
    U = "create_uf"
    b.chapter(U, "Фабрика без шахт", "Create: Ultimate Factory — ресурсы из машин", "Эра механизмов", "minecraft:diamond", 8.7,
              intro=("Фабрика без шахт", "Редкие ресурсы из простых",
                     ["Create: Ultimate Factory добавляет рецепты для машин Create: из дешёвых блоков получаются редкие ресурсы.",
                      "Так можно автоматизировать то, что обычно добывают в Незере, Крае или океане.",
                      "Квест засчитывается, когда нужный предмет у вас в инвентаре."], ["create_andesite/mechanical_press"]
                     if "create_andesite/mechanical_press" in b.ref2q else []))
    rec = [
        ("uf_diamond", "minecraft:diamond", "Алмаз из угля", "Механический пресс над чашей сжимает угольный блок в алмаз."),
        ("uf_blaze_rod", "minecraft:blaze_rod", "Стержень из порошка", "Пресс над чашей сжимает огненный порошок в огненный стержень."),
        ("uf_stones", "create:crimsite", "Камни Create из гравия", "Пресс над чашей: гравий с кусочками металла превращается в кримзит (железо), охрум (золото), веридий (медь) или азурин (цинк)."),
        ("uf_deepslate", "minecraft:deepslate", "Глубинный сланец и туф", "Пресс над чашей уплотняет камень в глубинный сланец, а булыжник — в туф."),
        ("uf_coal", "minecraft:coal", "Уголь из древесного", "Вентилятор через огонь душ превращает древесный уголь в обычный."),
        ("uf_crying", "minecraft:crying_obsidian", "Плачущий обсидиан", "Обсидиан под вентилятором через огонь душ становится плачущим."),
        ("uf_chorus", "minecraft:chorus_fruit", "Хорус из яблока", "Вентилятор через огонь душ превращает яблоко в плод хоруса."),
        ("uf_wither_rose", "minecraft:wither_rose", "Роза иссушения", "Мак под вентилятором через огонь душ становится розой иссушения."),
        ("uf_pearl", "minecraft:ender_pearl", "Жемчуг из эндерняка", "Дробильные колёса иногда дают жемчуг Края из эндерняка."),
        ("uf_skull", "minecraft:wither_skeleton_skull", "Череп из чернита", "Дробильные колёса с небольшим шансом дают череп скелета-иссушителя из чернита."),
        ("uf_heart", "minecraft:heart_of_the_sea", "Сердце моря", "Дробление коралловых блоков иногда даёт сердце моря."),
        ("uf_netherite", "minecraft:netherite_scrap", "Незеритовый лом", "Дробление незерских кирпичей с малым шансом даёт незеритовый лом."),
        ("uf_nautilus", "minecraft:nautilus_shell", "Раковина наутилуса", "Дробление красного песчаника иногда даёт раковину наутилуса."),
        ("uf_glowstone", "minecraft:glowstone_dust", "Светопыль", "Дробление песка душ даёт светопыль."),
        ("uf_redstone", "minecraft:redstone", "Редстоун из кремня", "Механический смешиватель делает редстоун из кремня и незерака."),
        ("uf_gunpowder", "minecraft:gunpowder", "Порох", "Смешиватель делает порох из угля и огненного порошка."),
        ("uf_slime", "minecraft:slime_ball", "Слизь из магмы", "Промывка вентилятором через воду превращает сгусток магмы в слизь."),
        ("uf_blue_ice", "minecraft:blue_ice", "Синий лёд", "Промывка плотного льда даёт синий лёд."),
    ]
    for i, (k, item, t, txt) in enumerate(rec):
        b.add(U, [item], k=k, title=t, text=[txt], optional=i >= 12, xp=25, deps=["intro"], note="Рецепт из Create: Ultimate Factory, описание по рецепту")

    # ================= WarNTaxes =================
    W = "mc_taxes"
    b.chapter(W, "Казна колонии", "WarNTaxes — налоги, казна и войны колоний", "Магия и поселения", "minecraft:emerald", 24,
              intro=("Казна колонии", "Колония платит налоги",
                     ["WarNTaxes добавляет экономику в MineColonies: здания колонии приносят налог, который вы забираете изумрудами.",
                      "Чем больше зданий, выше их уровень и счастливее жители — тем больше доход. На содержание зданий налог тоже тратится.",
                      "&6Команды:&r все команды начинаются с &e/wnt&r. Нажмите Tab после /wnt, чтобы увидеть подсказки."],
                     ["mc_found/townhall"] if "mc_found/townhall" in b.ref2q else []))
    b.custom(W, "check_tax", "Сколько накоплено?", [{"type": "check", "title": "Проверить налоги командой"}], deps=["intro"], xp=20,
             text=["Узнайте, сколько налога накопила колония: команда &e/wnt checktax&r.",
                   "Налог начисляется периодически — каждое здание приносит немного монет."])
    b.add(W, ["minecraft:emerald"], k="claim_tax", title="Первые налоги", count=8, deps=["check_tax"], xp=40,
          text=["Заберите налог командой &e/wnt claimtax&r — он выдаётся изумрудами.",
                "Квест засчитается, когда у вас будет 8 изумрудов."], note=NOTE)
    b.custom(W, "tax_policy", "Налоговая политика", [{"type": "check", "title": "Выбрать налоговую политику"}], deps=["claim_tax"], xp=30,
             text=["Командой &e/wnt taxpolicy&r можно выбрать политику: выше налог — ниже счастье жителей, и наоборот."])
    b.custom(W, "happiness", "Довольные жители", [{"type": "check", "title": "Поднять счастье колонии"}], deps=["claim_tax"], xp=30,
             text=["Счастье жителей увеличивает налог. Еда, жильё, школа, таверна и мистическое место делают жителей счастливее."])
    b.custom(W, "warchest", "Военная казна", [{"type": "check", "title": "Пополнить военную казну"}], deps=["tax_policy"], xp=30,
             text=["Военная казна хранит деньги на случай войны: &e/wnt warchest deposit&r кладёт, &e/wnt warchest withdraw&r забирает."])
    b.custom(W, "guards_tax", "Стража и доход", [{"type": "check", "title": "Построить сторожевые башни"}],
             deps=["claim_tax"] + (["mc_guard/guardtower"] if "mc_guard/guardtower" in b.ref2q else []), xp=30,
             text=["Сторожевые башни повышают доход и защищают колонию от налётов других игроков."])
    b.custom(W, "war_info", "Война и дипломатия", [{"type": "check", "title": "Узнать о войнах"}], deps=["warchest"], xp=30,
             optional=True, text=["На сервере колонии могут объявлять друг другу войну, устраивать набеги, заключать мир и брать вассалов.",
                                  "Подсказки: &e/wnt wars&r — список войн, &e/wnt warstats&r — статистика. Для одиночной игры это необязательно."])

    # ================= Мелкие подсказки в существующих главах =================
    if "supp" in b.ch:
        b.custom("supp", "carry_on", "Своя ноша не тянет", [{"type": "check", "title": "Перенести сундук руками"}], deps=["intro"], xp=15,
                 text=["&6Carry On:&r присядьте и нажмите ПКМ пустыми руками по сундуку, бочке или животному — вы поднимете его вместе с содержимым.",
                       "Удобно для переезда склада и перевозки питомцев."])
    if "toms" in b.ch:
        b.custom("toms", "contraption_terminal", "Терминал на штуковине", [{"type": "check", "title": "Открыть терминал на движущейся штуковине"}],
                 deps=["storage_terminal"], xp=20, optional=True,
                 text=["&6Create Contraption Terminals:&r терминал Tom's Storage работает и на собранной штуковине Create — открывает всё её хранилище.",
                       "Также терминалом удобно смотреть содержимое хранилищ предметов Create."])
    if "aero_fleet" in b.ch:
        b.custom("aero_fleet", "climb_ropes", "По канату на борт", [{"type": "check", "title": "Забраться по верёвке на корабль"}],
                 deps=["intro"], xp=15, optional=True,
                 text=["&6Climbable Ropes:&r по верёвкам Simulated можно лазать, даже если корабль движется.",
                       "В настройках управления есть клавиша, которая фиксирует камеру, пока вы держитесь за верёвку."])
        b.custom("aero_fleet", "ship_waystone", "Обелиск на борту", [{"type": "check", "title": "Поставить путеводный обелиск на корабль"}],
                 deps=["intro"], xp=20, optional=True,
                 text=["&6Waystones Sable:&r путеводный обелиск можно поставить прямо на летающий корабль — телепорт будет к кораблю, где бы он ни был."])
    if "act_explore" in b.ch:
        b.custom("act_explore", "fog_menu", "Туманы и небо", [{"type": "check", "title": "Открыть меню AtmosphericA"}], deps=["intro"], xp=10,
                 optional=True, text=["&6AtmosphericA:&r в каждом биоме свой туман и цвет неба. Клавишу меню настроек тумана можно назначить в управлении."])
