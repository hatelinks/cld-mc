"""Create 6.0.10: недостающие предметы в существующих главах + глава «Мастерская уюта»."""
import fam
import lex

C = "create:"


def ids(*names):
    return [C + n for n in names]


def colors(suffix, base=None):
    out = [C + f"{c}_{suffix}" for c in lex.COLORS]
    if base:
        out.insert(0, C + base)
    return out


def run(b):
    A, B, F, L, K, T = "create_andesite", "create_brass", "create_fluids", "create_logistics", "create_contraptions", "create_trains"

    # ---- Первые обороты ----
    b.add(A, ids("vertical_gearbox"), text="Угловая передача, повёрнутая вертикально: передаёт вращение вбок, вверх и вниз одновременно.")
    b.add(A, ids("large_water_wheel"), text="Большое колесо даёт больше силы, чем обычное, но крутится медленнее. Ставится в течение воды.")
    b.add(A, ids("propeller"), text="Деталь вентилятора. Сам по себе ничего не делает — идёт в крафт вентилятора и пропеллеров.")
    b.add(A, ids("flywheel"), text="Декоративный маховик на валу: показывает, что механизм работает, и красиво крутится.")
    b.add(A, ids("turntable"), text="Поворачивает стоящих на нём мобов и игроков. Полезен для ферм и шуток над друзьями.")
    b.add(A, ids("crushed_raw_iron", "crushed_raw_copper", "crushed_raw_gold", "crushed_raw_zinc", "crushed_raw_platinum"),
          k="crushed_all", title="Дроблёные руды", count=1,
          text="Руда, пропущенная через дробильные колёса, даёт дроблёный материал. Его промывают или переплавляют — выход больше, чем из простой руды.")
    b.add(A, ids("copper_nugget", "zinc_nugget", "brass_nugget"), k="nuggets", title="Кусочки металлов",
          text="Кусочки получаются при промывке дроблёных руд вентилятором через воду.")
    b.add(A, ids("zinc_block", "raw_zinc_block"), k="zinc_blocks", title="Цинковые блоки")
    b.add(A, ids("wheat_flour"), text="Мука из жерновов. Из неё делают тесто.")
    b.add(A, ids("dough"))
    b.add(A, ids("cinder_flour"), text="Перемолотый незерак. Нужен для красного песка и некоторых рецептов смешивания.")
    b.add(A, ids("tree_fertilizer"), text="Мгновенно выращивает дерево из саженца — удобно для автоматической фермы дерева.")
    b.add(A, ids("red_sand_paper"), text="Улучшенная наждачная бумага. Шлифует быстрее и дольше служит.")
    b.add(A, ids("wooden_bracket", "metal_bracket"), k="brackets", title="Скобы для валов",
          text="Декоративные крепления для валов и труб. Ставятся ПКМ по валу.")
    b.add(A, ids("metal_girder"), text="Металлическая балка: опора для валов и красивый каркас для построек.")

    # ---- Латунный рассвет ----
    b.add(B, ids("rose_quartz"), text="Кварц с редстоуном. После шлифовки становится полированным розовым кварцем — основой электронных ламп.")
    b.add(B, ids("polished_rose_quartz"))
    b.add(B, ids("rose_quartz_block"), optional=True)
    b.add(B, ids("brass_block"), optional=True)
    b.add(B, ids("crafting_blueprint"), text="Чертёж рецепта: вешается на стену и позволяет крафтить по сохранённому рецепту, не раскладывая предметы.")
    b.add(B, ids("crafter_slot_cover"), text="Закрывает слот механического крафтера, чтобы он оставался пустым в рецепте.")
    b.add(B, ids("content_observer"), text="Умный наблюдатель: подаёт сигнал, когда в инвентаре, трубе или конвейере появляется нужный предмет или жидкость.")
    b.add(B, ids("analog_lever"), text="Рычаг с силой сигнала от 0 до 15. Удобен для регуляторов скорости.")
    b.add(B, ids("pulse_repeater", "pulse_extender", "pulse_timer"), k="pulse", title="Импульсные схемы",
          text="Компактные редстоун-схемы: повторитель импульса, удлинитель импульса и генератор импульсов.", mode="set")
    b.add(B, ids("powered_latch", "powered_toggle_latch"), k="latches", title="Редстоуновые триггеры", mode="set",
          text="Запоминают сигнал: обычный включается и выключается разными входами, защёлка переключается каждым импульсом.")
    b.add(B, ids("redstone_contact"), text="Пара контактов подаёт сигнал, когда соприкасается. Часто используется на движущихся механизмах.")

    # ---- Пар и глубина ----
    b.add(F, ids("fluid_valve"), text="Перекрывает трубу по сигналу или вращению.")
    b.add(F, colors("valve_handle", "copper_valve_handle"), k="valve_handle", title="Ручка вентиля",
          text="Поворачивает механизмы на точный угол. Можно перекрашивать.")
    b.add(F, ids("honey", "chocolate"), k="sweet_fluids", title="Мёд и шоколад", mode="check",
          text=["Create добавляет жидкие мёд и шоколад.", "Мёд сливается из ульев трубой, шоколад смешивается в чаше из какао, сахара и молока."])
    b.add(F, ids("bar_of_chocolate"))
    b.add(F, ids("chocolate_glazed_berries", "honeyed_apple", "sweet_roll"), k="sweets", title="Сладости Create", mode="set",
          text="Сладости из заправщика: окуните ягоды, яблоко или рулет в шоколад или мёд.")
    b.add(F, ids("builders_tea"), text="Чай строителя: даёт ускорение, пока вы строите. Наливается в бутылку заправщиком.")
    b.add(F, ids("blaze_cake_base"))
    b.add(F, ids("blaze_cake"), text="Торт для горелки всполоха: разжигает её до супер-нагрева на долгое время.")
    b.add(F, ids("copper_diving_helmet", "copper_diving_boots"), k="diving_set", title="Медный водолазный костюм", mode="set",
          deps=None, text="Шлем и ботинки вместе с баллоном дают дышать под водой и ходить по дну.")
    b.add(F, ids("netherite_backtank", "netherite_diving_helmet", "netherite_diving_boots"), k="netherite_diving",
          title="Лаволазный костюм", mode="set",
          text="Незеритовый комплект позволяет купаться в лаве и не гореть, пока в баллоне есть воздух.")

    # ---- Ожившая механика ----
    b.add(K, ids("clockwork_bearing"), text="Подшипник часов: стрелки собранного механизма показывают текущее игровое время.")
    b.add(K, ids("elevator_pulley"), text="Лифт: поднимает и опускает кабину между этажами по сигналу с контактов.")
    b.add(K, ids("copycat_step"), optional=True)
    b.add(K, colors("seat")[1:], k="seats", title="Сиденья (все цвета)",
          text="Сиденье садит игрока или моба. Если стоит на механизме, едет вместе с ним.")

    # ---- Река посылок ----
    b.add(L, ids("transmitter"), text="Деталь для блоков сети запасов — нужна в рецептах связи и запросов.")
    b.add(L, ids("package_filter"), text="Фильтр по адресу посылки: пропускает только посылки с нужной подписью.")
    b.add(L, ids("redstone_requester"), text="По сигналу редстоуна заказывает со склада заданный набор предметов.")
    b.add(L, ids("cardboard"), text="Картон из древесной массы. Из него делают коробки, броню и меч.")
    b.add(L, ids("pulp"))
    b.add(L, ids("bound_cardboard_block"), optional=True)
    b.add(L, ids("cardboard_helmet", "cardboard_chestplate", "cardboard_leggings", "cardboard_boots"),
          k="cardboard_armor", title="Картонные доспехи", mode="set", optional=True,
          text="Шуточная броня. Защищает плохо, но в ней можно спрятаться от мобов, если присесть.")
    b.add(L, ids("cardboard_sword"), optional=True)
    b.add(L, colors("postbox"), k="postbox", title="Почтовый ящик",
          text="Почтовый ящик принимает посылки от лягушек-отправщиков и раздаёт их по адресу.")
    b.add(L, colors("table_cloth", "brass_table_cloth"), k="table_cloth", title="Скатерть-витрина",
          text="Магазин без продавца: выкладывает товар на стол, а покупатель платит через тикер.")

    # ---- Стальные пути ----
    b.add(T, ids("controller_rail"), text="Рельсы для вагонеток с регулируемой скоростью по сигналу редстоуна.")

    # ---- Мастерская уюта ----
    M = "create_misc"
    b.chapter(M, "Мастерская уюта", "Create — инструменты, гаджеты и декор", "Эра механизмов", C + "wrench", 5.5,
              intro=("Мастерская уюта", "Инструменты и украшения Create",
                     ["Здесь собраны полезные инструменты и гаджеты Create, а также декоративные блоки.",
                      "Глава необязательна для прогресса, но многое тут сильно облегчает жизнь."],
                     []))
    b.add(M, ids("extendo_grip"), text="Длиннохват увеличивает дальность, на которой вы ставите и ломаете блоки.")
    b.add(M, ids("potato_cannon"), text="Стреляет картофелем и другими предметами. Работает от баллона с воздухом.")
    b.add(M, ids("wand_of_symmetry"), text="Повторяет ваши блоки зеркально относительно выбранной плоскости.")
    b.add(M, ids("linked_controller"), text="Пульт с 6 кнопками: каждая включает свою частоту передатчиков сигнала.")
    b.add(M, [C + "brown_toolbox"] + [x for x in colors("toolbox")[1:] if "brown" not in x], k="toolboxes", title="Ящики для инструментов",
          text="Хранит инструменты; нажатие клавиши быстро достаёт нужный, если вы рядом с ящиком.")
    b.add(M, ids("desk_bell"), optional=True)
    b.add(M, ids("cuckoo_clock"), text="Часы с кукушкой: кукуют в полдень и в полночь. Если поставить их рядом с кроватью — будят утром.")
    b.add(M, ids("peculiar_bell", "haunted_bell"), k="bells", title="Колокола", mode="set", optional=True,
          text="Особенный колокол декоративный, а призрачный показывает места, где ночью появляются мобы.")
    b.add(M, ids("placard"), text="Умная рамка подаёт сигнал, если в неё вставить такой же предмет, как в ней показан.")
    b.add(M, ids("experience_block"), optional=True)
    b.add(M, ids("copper_ladder", "brass_ladder", "andesite_ladder", "copper_scaffolding", "brass_scaffolding",
                 "andesite_scaffolding", "copper_bars", "brass_bars", "andesite_bars"),
          k="building", title="Лестницы, подмостки и прутья",
          text="Металлические варианты лестниц, подмостков и решёток. Подходят к стилю фабрики.")
    b.add(M, ids("industrial_iron_block", "weathered_iron_block"), k="iron_blocks", title="Промышленное железо")
    b.add(M, ids("copper_shingles", "copper_tiles", "exposed_copper_shingles", "weathered_copper_shingles",
                 "oxidized_copper_shingles", "waxed_copper_shingles"), k="shingles", title="Медная черепица",
          text="Медная черепица и плитка для крыш. Со временем окисляется, воск сохраняет цвет.")
    b.add(M, ids("framed_glass", "tiled_glass", "horizontal_framed_glass", "vertical_framed_glass"),
          k="glass", title="Узорное стекло")
    b.add(M, ids("oak_window", "spruce_window", "birch_window", "industrial_iron_window", "ornate_iron_window"),
          k="windows", title="Оконные блоки")
    b.add(M, ids("copper_door", "brass_door", "andesite_door", "framed_glass_door"), k="doors", title="Двери Create")
    b.add(M, ids("rose_quartz_lamp"), optional=True)
    b.add(M, ids("andesite_alloy_block"), optional=True)
    stones = [("asurine", "Азурит"), ("crimsite", "Кримзит"), ("limestone", "Известняк"), ("ochrum", "Охрум"),
              ("scorchia", "Скорчия"), ("scoria", "Скория"), ("veridium", "Веридиум")]
    for s, nm in stones:
        b.add(M, ids(s, "cut_" + s, "cut_" + s + "_bricks", "polished_cut_" + s, s + "_pillar"),
              k="stone_" + s, title=lex.name(C + s) if lex.is_ru(C + s) else nm, optional=True,
              text="Камень Create для декора. Встречается в мире слоями и получается смешиванием или дроблением.")
    b.add(M, ids("cut_granite", "cut_diorite", "cut_andesite", "cut_calcite", "cut_dripstone", "cut_deepslate", "cut_tuff"),
          k="cut_vanilla", title="Тёсаный камень", optional=True,
          text="Камнерез превращает обычные камни в тёсаные блоки, кирпичи и колонны в стиле Create.")
