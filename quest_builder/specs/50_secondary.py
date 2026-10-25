"""Второстепенные моды: Supplementaries, Waystones + Nature's Compass, Tom's Storage,
Mystical Agradditions, мелкие аддоны Create."""
import lex


def P(ns, *n):
    return [f"{ns}:{x}" for x in n]


def col(ns, suffix, base=None):
    out = [f"{ns}:{c}_{suffix}" for c in lex.COLORS]
    if base:
        out = [f"{ns}:{base}"] + [o for o in out if o != f"{ns}:{base}"]
    return out


def run(b):
    # ================= Supplementaries =================
    S, s = "supp", "supplementaries"
    b.chapter(S, "Мелочи жизни", "Supplementaries — полезные мелочи и уют", "Хозяйство и путешествия", "supplementaries:sack", 18,
              intro=("Мелочи жизни", "Полезное и красивое",
                     ["Supplementaries добавляет десятки предметов, которых не хватает в обычной игре: верёвки, мешки, баночки, флюгеры, пушки, фонари и многое другое.",
                      "Квесты идут разделами: быт, приборы, механика, приключения и декор."], []))
    # быт
    b.add(S, P(s, "flax_seeds"), text="Дикий лён растёт в мире. Семена сажают на грядку, урожай — лён.", anchor=True)
    b.add(S, P(s, "flax"), text="Лён — сырьё для верёвок, мешков и ткани.")
    b.add(S, P(s, "rope"), text="Верёвка свисает вниз от блока и по ней можно лазать. ПКМ по верёвке опускает или поднимает её.")
    b.add(S, P(s, "pulley_block"), text="Шкив наматывает и разматывает верёвку или цепь. Работает с Create как лебёдка.")
    b.add(S, P(s, "sack"), text="Мешок — переносное хранилище на 9 слотов. В отличие от рюкзака, его можно ставить как блок.")
    b.add(S, P(s, "jar"), text="Баночка хранит жидкости, мёд, рыбу, светлячков и даже маленьких мобов.")
    b.add(S, P(s, "cage"), text="Клетка ловит мелких и детёнышей мобов и позволяет перевозить их.")
    b.add(S, P(s, "item_shelf"), text="Полка показывает один предмет — красивая витрина.")
    b.add(S, P(s, "pedestal"), optional=True)
    b.add(S, P(s, "planter", "flower_box"), k="planters", title="Горшки и цветочные ящики", mode="set", optional=True)
    b.add(S, P(s, "doormat", "hat_stand", "statue"), k="home_decor", title="Уют дома", mode="any", optional=True)
    b.add(S, P(s, "lunch_basket"), text="Обеденная корзинка хранит еду. Еда достаётся из неё прямо при поедании.")
    b.add(S, P(s, "pancake", "candy", "sugar_cube"), k="sweets_s", title="Сладости", mode="any", optional=True)
    b.add(S, P(s, "fodder"), text="Корм для животных: кормит сразу нескольких и ускоряет рост детёнышей.")
    b.add(S, P(s, "soap"), text="Мыло отмывает краску с шерсти, стекла и других блоков, а ещё снимает воск.", adv="supplementaries:husbandry/soap")
    b.add(S, P(s, "fire_pit"), optional=True)
    b.add(S, P(s, "key", "lock_block"), k="key_lock", title="Ключ и замок", mode="set",
          text="Замок закрывает дверь или сундук: открыть их можно только ключом с тем же названием.")
    # приборы
    b.add(S, P(s, "globe"), text="Глобус показывает карту мира и крутится от удара.", adv="supplementaries:adventure/globe", anchor=True)
    b.add(S, P(s, "altimeter"), text="Высотомер показывает высоту над уровнем моря.")
    b.add(S, P(s, "wind_vane"), text="Флюгер крутится по ветру и подаёт сигнал в грозу.")
    b.add(S, P(s, "clock_block"), text="Часовой блок показывает время и подаёт сигнал по часам.")
    b.add(S, P(s, "hourglass"), text="Песочные часы отсчитывают время и выдают сигнал, пока сыплется песок.")
    b.add(S, P(s, "notice_board", "blackboard"), k="boards", title="Доска объявлений и школьная доска", mode="set",
          text="На доске объявлений вешают книги и карты, на школьной доске рисуют мелом.")
    b.add(S, P(s, "slice_map"), optional=True, text="Слоевая карта показывает мир на выбранной высоте — удобно под землёй.")
    b.add(S, P(s, "crystal_display", "speaker_block"), k="displays", title="Дисплей и динамик", mode="set")
    # механика
    b.add(S, P(s, "bellows"), text="Мехи раздувают огонь в печах (ускоряют плавку) и толкают сущности.", anchor=True)
    b.add(S, P(s, "faucet"), text="Кран выливает жидкость из бочки, баночки или котла — полезен для автоматизации.")
    b.add(S, P(s, "turn_table"), text="Вращатель поворачивает блоки и сущности над собой по сигналу.", adv="supplementaries:husbandry/turn_table")
    b.add(S, P(s, "spring_launcher"), text="Запускатель подбрасывает всё, что стоит над ним.")
    b.add(S, P(s, "crank", "cog_block"), k="crank_cog", title="Шатун и шестерни", mode="set")
    b.add(S, P(s, "slidy_block"), optional=True)
    b.add(S, P(s, "relayer", "redstone_illuminator"), k="redstone_s", title="Редстоун Supplementaries", mode="set")
    b.add(S, P(s, "dispenser_minecart"))
    b.add(S, P(s, "wrench"), text="Гаечный ключ поворачивает блоки, не ломая их.", adv="supplementaries:husbandry/wrench")
    # приключения
    b.add(S, P(s, "slingshot"), text="Рогатка стреляет любыми блоками и предметами.", anchor=True)
    b.add(S, P(s, "bomb"), text="Бомба взрывается при ударе. Ломает блоки слабее динамита.")
    b.add(S, P(s, "cannon"), text="Пушка стреляет ядрами и другими снарядами. Порох задаёт силу выстрела.")
    b.add(S, P(s, "cannonball"))
    b.add(S, P(s, "rope_arrow"), text="Стрела оставляет за собой верёвку — по ней можно забраться наверх.")
    b.add(S, P(s, "flute"), text="Флейта зовёт прирученных животных к вам.")
    b.add(S, P(s, "bubble_blower"), optional=True)
    b.add(S, P(s, "confetti_popper"), optional=True)
    b.add(S, P(s, "jar_boat"), optional=True)
    b.add(S, P(s, "goblet"), adv="supplementaries:nether/goblet", optional=True)
    b.add(S, P(s, "bamboo_spikes"), text="Шипы ранят мобов. Обработанные зельем шипы ещё и отравляют.")
    # свет и декор
    b.add(S, P(s, "sconce", "sconce_soul", "sconce_ender", "sconce_glow", "sconce_lever", "sconce_nether_brass"),
          k="sconces", title="Бра", anchor=True)
    b.add(S, P(s, "candle_holder") + col(s, "candle_holder"), k="candle_holders", title="Подсвечники", optional=True)
    b.add(S, P(s, "stone_lamp", "deepslate_lamp", "blackstone_lamp", "end_stone_lamp"), k="lamps", title="Каменные лампы", optional=True)
    b.add(S, P(s, "lumisene_bottle"), optional=True)
    b.add(S, P(s, "awning") + col(s, "awning"), k="awnings", title="Маркизы", optional=True)
    b.add(S, col(s, "bunting"), k="buntings", title="Флажки", optional=True)
    b.add(S, col(s, "flag"), k="flags", title="Флаги", optional=True)
    b.add(S, P(s, "present", "trapped_present"), k="presents", title="Подарок", optional=True)
    b.add(S, P(s, "timber_frame", "timber_brace", "timber_cross_brace", "daub", "daub_frame"), k="timber", title="Фахверк", optional=True,
          text="Деревянные каркасы и мазня — для домов в стиле фахверк.")
    b.add(S, P(s, "ash_bricks", "stone_tile", "blackstone_tile", "lapis_bricks", "gravel_bricks", "checker_block", "fine_wood"),
          k="decor_blocks", title="Декоративные блоки", optional=True)
    b.add(S, P(s, "gold_door", "gold_trapdoor", "gold_gate", "gold_bars", "iron_gate", "netherite_door", "netherite_trapdoor"),
          k="metal_doors", title="Металлические двери и ворота", optional=True)
    b.add(S, P(s, "feather_block", "flint_block", "flax_block", "soap_block"), k="material_blocks", title="Блоки материалов", optional=True)
    b.add(S, P(s, "wicker_fence"), optional=True)
    b.add(S, P(s, "ash"), optional=True)

    # ================= Waystones + Nature's Compass (в главу компаса) =================
    E, w = "ec_compass", "waystones"
    b.add(E, P("naturescompass", "naturescompass"), text="Природный компас ищет биомы. Выберите биом в меню, и стрелка укажет путь.",
          adv="naturescompass:natures_compass")
    b.add(E, P(w, "warp_dust"), text="Пыль перемещения — основа всех предметов Waystones.", anchor=True)
    b.add(E, P(w, "waystone", "mossy_waystone", "sandy_waystone", "deepslate_waystone", "blackstone_waystone", "end_stone_waystone"),
          k="waystone", title="Путеводный обелиск",
          text="Обелиски встречаются в деревнях и строятся вручную. Активируйте обелиск — и сможете телепортироваться к нему с любого другого.")
    b.add(E, P(w, "warp_stone"), text="Камень перемещения переносит к любому активированному обелиску из инвентаря.")
    b.add(E, P(w, "return_scroll"), text="Свиток возврата — разовый телепорт к последнему обелиску.")
    b.add(E, P(w, "warp_scroll", "blank_scroll"), k="scrolls_w", title="Свитки перемещения", mode="any")
    b.add(E, P(w, "warp_plate"), text="Плита перемещения связывается с другой плитой осколком — встали и переместились.")
    b.add(E, P(w, "deepslate_shard", "dormant_shard"), k="shards", title="Осколки", mode="any", optional=True)
    b.add(E, col(w, "sharestone"), k="sharestone", title="Судьбоносный обелиск",
          text="Обелиски одного цвета связаны в отдельную сеть — для своих маршрутов.", optional=True)
    b.add(E, col(w, "portstone"), k="portstone", title="Портовый камень", optional=True)

    # ================= Tom's Simple Storage =================
    T, t = "toms", "toms_storage"
    b.chapter(T, "Единый склад", "Tom's Simple Storage — сеть хранилищ", "Хозяйство и путешествия", "toms_storage:storage_terminal", 13.5,
              intro=("Единый склад", "Все сундуки в одном окне",
                     ["Tom's Storage соединяет ваши сундуки, бочки и ящики в одну сеть. Терминал показывает всё содержимое сразу и ищет по названию.",
                      "Хорошо сочетается с хранилищами Sophisticated Storage и ящиками Storage Drawers."], ["ss_storage/" + "barrel"]
                     if "ss_storage/barrel" in b.ref2q else []))
    b.add(T, P(t, "inventory_connector"), text="Коннектор — сердце сети: подключается к сундукам вокруг и к кабелям.")
    b.add(T, P(t, "storage_terminal"), text="Терминал показывает все предметы сети. Ставится рядом с коннектором или через кабель.")
    b.add(T, P(t, "trim"), text="Соединитель инвентарей объединяет соседние хранилища в одно для коннектора.")
    b.add(T, P(t, "inventory_cable"), text="Кабель тянет сеть к дальним сундукам.")
    b.add(T, P(t, "inventory_cable_connector"))
    b.add(T, P(t, "inventory_cable_framed", "inventory_cable_connector_framed"), k="framed", title="Кабели в раме", mode="any", optional=True)
    b.add(T, P(t, "crafting_terminal"), text="Терминал крафта: верстак прямо в окне склада, берёт материалы из сети.")
    b.add(T, P(t, "wireless_terminal"), text="Беспроводной терминал открывает склад издалека.")
    b.add(T, P(t, "adv_wireless_terminal"), xp=30)
    b.add(T, P(t, "item_filter", "tag_item_filter", "polymorphic_item_filter"), k="filters", title="Фильтры", mode="any")
    b.add(T, P(t, "inventory_interface", "inventory_proxy"), k="interface", title="Интерфейс и прокси", mode="set",
          text="Интерфейс позволяет воронкам и машинам класть вещи в сеть, прокси — видеть сеть как один сундук.")
    b.add(T, P(t, "basic_inventory_hopper"))
    b.add(T, P(t, "level_emitter"), text="Излучатель уровня подаёт сигнал, когда предметов в сети больше или меньше заданного.")
    b.add(T, P(t, "inventory_configurator"))
    b.add(T, P(t, "filing_cabinet"), text="Картотека хранит множество разных одиночных предметов.")
    b.add(T, P(t, "open_crate"), optional=True)
    b.add(T, P(t, "paint_kit"), optional=True)

    # ================= Mystical Agradditions =================
    A, m = "ma_add", "mysticalagradditions"
    b.chapter(A, "За гранью супремия", "Mystical Agradditions — шестой уровень и особые семена", "Магия и поселения", "mysticalagradditions:insanium_essence", 10.5,
              intro=("За гранью супремия", "Инсаний и особые основы",
                     ["Mystical Agradditions добавляет шестую ступень — &5инсаний&r, особые основания для семян звезды Незера и яйца дракона, а также яблоки, уголь и паксели."],
                     ["ma_gear/supremium_mat"]))
    b.add(A, P(m, "insanium_essence"), text="Инсаниевая эссенция — следующая ступень после супремия. Делается на алтаре инфузии.")
    b.add(A, P(m, "insanium_ingot", "insanium_gemstone"), k="insanium_mat", title="Инсаниевые слиток и самоцвет", mode="set")
    b.add(A, P(m, "insanium_farmland"), text="Инсаниевая пашня — самая быстрая земля.")
    b.add(A, P(m, "insanium_block", "insanium_ingot_block", "insanium_gemstone_block", "insanium_nugget"), k="insanium_blocks",
          title="Инсаниевые блоки", optional=True)
    b.add(A, P(m, *[f"{t}_paxel" for t in ("inferium", "prudentium", "tertium", "imperium", "supremium", "awakened_supremium")]),
          k="paxels", title="Паксели", text="Паксель — кирка, топор и лопата в одном инструменте.")
    b.add(A, P(m, *[f"{t}_apple" for t in ("inferium", "prudentium", "tertium", "imperium", "supremium", "insanium")]),
          k="apples", title="Яблоки ступеней", text="Каждое яблоко даёт набор полезных эффектов. Чем выше ступень, тем сильнее.")
    b.add(A, P(m, *[f"{t}_coal" for t in ("inferium", "prudentium", "tertium", "imperium", "supremium", "insanium")]),
          k="coals", title="Уголь ступеней", text="Топливо, которое горит дольше обычного угля.")
    b.add(A, P(m, "nether_star_shard"), text="Осколок звезды Незера — для основания семян звезды Незера.")
    b.add(A, P(m, "nether_star_crux"), text="Основание ставится под семена особых ресурсов — без него они не растут.")
    b.add(A, P(m, "dragon_egg_chunk"))
    b.add(A, P(m, "dragon_egg_crux"))
    b.add(A, P(m, "awakened_draconium_crux", "gaia_spirit_crux", "neutronium_crux", "nitro_crystal_crux"), k="other_crux",
          title="Другие основания", mode="any", optional=True)

    # ================= Мелкие аддоны Create =================
    W = "create_inv"
    b.chapter(W, "Мастерская изобретателя", "Аддоны Create: Dragons Plus, Aquatic Ambitions, Things and Misc, Power Loader", "Эра механизмов", "create_things_and_misc:radar", 8.5,
              intro=("Мастерская изобретателя", "Маленькие, но полезные аддоны",
                     ["Здесь собраны небольшие аддоны Create: окраска вентилятором, подводные машины, загрузчики чанков и множество гаджетов."],
                     ["create_brass/brass_ingot"] if "create_brass/brass_ingot" in b.ref2q else []))
    d = "create_dragons_plus"
    b.add(W, P(d, "fluid_hatch"), text="Жидкостный люк — компактный вход и выход жидкостей для машин.", anchor=True)
    b.add(W, P(d, "white_dye") + [f"{d}:{c}_dye" for c in lex.COLORS if c != "white"], k="dyes_dp", title="Красители Dragons Plus",
          text="Жидкие красители: вентилятор через них окрашивает предметы массово.")
    b.add(W, P(d, "dragon_breath"), mode="check", title="Жидкое драконье дыхание",
          text=["Драконье дыхание как жидкость: вентилятор через него «эндерит» предметы — особая обработка Dragons Plus."])
    b.add(W, P(d, "blaze_upgrade_smithing_template"), text="Кузнечный шаблон для улучшений всполоха.")
    aq = "create_aquatic_ambitions"
    b.add(W, P(aq, "prismarine_alloy"), text="Призмариновый сплав — материал подводных машин.", anchor=True)
    b.add(W, P(aq, "prismarine_alloy_rod", "prismarine_alloy_block"), k="pa_more", title="Стержень и блок сплава", mode="set")
    b.add(W, P(aq, "mechanical_conduit"))
    b.add(W, P(aq, "calcium_rich_powder", "spiky_shell", "suspicious_rock"), k="aq_misc", title="Морские находки", mode="any")
    pl = "create_power_loader"
    b.add(W, P(pl, "andesite_chunk_loader"), title="Андезитовый загрузчик чанков", anchor=True,
          text="Загрузчик держит чанки загруженными, пока на него подаётся вращение. Фабрика работает, даже когда вы далеко.")
    b.add(W, P(pl, "brass_chunk_loader"), title="Латунный загрузчик чанков", text="Латунный загрузчик держит больше чанков и работает на поездах и штуковинах.")
    tm = "create_things_and_misc"
    RU = {"radar": "Радар", "magnifying_glass": "Лупа", "spout_gun": "Пистолет-заправщик", "sprinkler": "Разбрызгиватель",
          "sprinkler_head": "Головка разбрызгивателя", "card_press": "Пресс для карт", "card_reader": "Считыватель карт",
          "card": "Карта доступа", "empty_card": "Пустая карта", "ticket": "Билет", "empty_ticket": "Пустой билет",
          "portable_whistle": "Портативный свисток", "netherite_portable_whistle": "Незеритовый свисток",
          "sticky_launcher": "Медная липкая катапульта", "brass_sticky_launcher": "Латунная липкая катапульта",
          "sticky_boots_boots": "Липкие ботинки", "blaze_ballon": "Огненный шар", "neon_tube": "Неоновая трубка",
          "brass_speaker": "Латунный динамик", "train_buffer": "Буфер поезда", "train_stop": "Тупиковый упор",
          "vibration_mechanism": "Вибромеханизм", "mending_rune": "Руна починки", "experience_sheet": "Лист опыта",
          "rose_quartz_sheet": "Лист розового кварца", "obsidian_sand_paper": "Обсидиановая наждачка",
          "slime_cake": "Торт из слизи", "diluted_bonemeal": "Разбавленная костная мука", "crushed_magma": "Дроблёная магма",
          "sturdy_sheet_block": "Блок прочных листов", "glue_packaging": "Упаковка клея", "brass_knife": "Латунный нож",
          "copper_knife": "Медный нож", "zinc_knife": "Цинковый нож", "train_sing": "Знак для поезда", "oak_sail": "Деревянный парус"}

    def t_(n, text=None, **kw):
        return b.add(W, [f"{tm}:{n}"], title=RU.get(n), text=text, **kw)

    t_("vibration_mechanism", text="Вибромеханизм — компонент радаров и сигнальных устройств.", anchor=True)
    t_("radar", text="Радар показывает приближающихся мобов и игроков.")
    t_("magnifying_glass")
    t_("rose_quartz_sheet")
    t_("experience_sheet")
    t_("mending_rune", text="Руна починки чинит инструменты опытом.")
    t_("obsidian_sand_paper")
    t_("spout_gun", text="Ручной заправщик: стреляет жидкостью — водой, лавой, мёдом или слизью.")
    t_("sprinkler", text="Разбрызгиватель поливает поля вокруг, ускоряя рост.")
    t_("diluted_bonemeal")
    b.add(W, [f"{tm}:empty_card", f"{tm}:card"], k="cards", title="Карты доступа", mode="set")
    t_("card_press")
    t_("card_reader", text="Считыватель подаёт сигнал только при карте с нужным кодом — замок на двери.")
    b.add(W, [f"{tm}:empty_ticket", f"{tm}:ticket"], k="tickets", title="Билеты на поезд", mode="set")
    t_("portable_whistle", text="Свисток вызывает поезд к станции, где вы стоите.")
    t_("netherite_portable_whistle", optional=True)
    t_("train_buffer")
    t_("train_stop")
    b.add(W, [f"{tm}:train_sing", f"{tm}:redsing", f"{tm}:green_sing", f"{tm}:speed_25"], k="signs", title="Знаки для поезда", optional=True)
    t_("sticky_launcher")
    t_("brass_sticky_launcher")
    t_("sticky_boots_boots", text="Липкие ботинки позволяют ходить по стенам из слизи.")
    t_("blaze_ballon", optional=True)
    t_("neon_tube", optional=True)
    t_("brass_speaker", optional=True)
    b.add(W, [f"{tm}:brass_knife", f"{tm}:copper_knife", f"{tm}:zinc_knife"], k="knives_tm", title="Металлические ножи", optional=True)
    t_("crushed_magma", optional=True)
    t_("slime_cake", optional=True)
    t_("sturdy_sheet_block", optional=True)
    t_("glue_packaging", optional=True)
    b.add(W, [f"{tm}:oak_sail", f"{tm}:spruce_sail", f"{tm}:birch_sail"], k="sails_tm", title="Деревянные паруса", optional=True)
