"""Главы под моды, взятые из сборки aoa: Copycats+, убранство поездов, еда Create, торговля,
Gadgets & Gizmos, субмарины и корабли, структуры Integrated Villages и Stronghold."""
import re

import lex

# русские названия для модов без перевода (в jar нет ru_ru)
RU = {
    "createthrusters:thruster": "Двигатель",
    "createthrusters:small_thruster": "Малый двигатель",
    "createthrusters:rcs_thruster": "Маневровый двигатель",
    "createthrusters:ion_thruster": "Ионный двигатель",
    "createthrusters:thruster_lense": "Линза двигателя",
    "createthrusters:industrial_motor": "Промышленный мотор",
    "createthrusters:alternator": "Промышленный генератор",
    "createthrusters:andesite_cable": "Андезитовый кабель",
    "createthrusters:blackstone_alloy": "Чернитовый сплав",
    "createthrusters:blackstone_alloy_block": "Блок чернитового сплава",
    "createthrusters:blackstone_sheet": "Чернитовый лист",
    "createthrusters:computation_mechanism": "Вычислительный механизм",
    "createthrusters:incomplete_computation_mechanism": "Незаконченный вычислительный механизм",
    "createthrusters:analogue_joystick": "Аналоговый джойстик",
    "createthrusters:advanced_contraption_controller": "Продвинутый пульт конструкции",
    "createthrusters:analogue_contraption_controller": "Пульт конструкции Sable",
    "createthrusters:advanced_navigation_table": "Продвинутый штурманский стол",
    "createthrusters:aileron_bearing": "Подшипник элерона",
    "createthrusters:vector_bearing": "Векторный подшипник",
    "createthrusters:thruster_bearing": "Сервоподшипник",
    "createthrusters:ship_coupler": "Сцепка кораблей",
    "createthrusters:physics_goggles": "Умные очки",
    "createthrusters:physics_staff": "Физический посох",
    "createthrusters:claw": "Клешня",
    "createthrusters:scissor_piston": "Ножничный поршень",
    "createthrusters:entity_launcher": "Метатель существ",
    "createthrusters:powered_zipline": "Моторный трос",
    "createthrusters:fuel_oxidizer": "Окислитель топлива",
    "createthrusters:oxidized_solid_fuel": "Окисленное твёрдое топливо",
    "createthrusters:scorching_oxidized_solid_fuel": "Палящее окисленное топливо",
    "createthrusters:propulsion_upgrade_t1": "Улучшение тяги I",
    "createthrusters:propulsion_upgrade_t2": "Улучшение тяги II",
    "createthrusters:propulsion_upgrade_t3": "Улучшение тяги III",
    "createthrusters:propulsion_upgrade_t4": "Улучшение тяги IV",
    "createthrusters:processing_upgrade_smelting_t1": "Улучшение обработки: плавка I",
    "createthrusters:processing_upgrade_smoking_t1": "Улучшение обработки: копчение I",
    "createthrusters:processing_upgrade_haunting_t1": "Улучшение обработки: обдув душ I",
    "createthrusters:variable_transmission": "Вариатор",
    "createthrusters:bidirectional_gearbox": "Умная коробка передач",
    "createthrusters:bi_directional_gearshift": "Двусторонний переключатель передач",
    "createthrusters:double_button": "Двойная кнопка",
    "createthrusters:gyroscope_link": "Продвинутая связь данных",
    "createthrusters:physics_gantry_carriage": "Каретка физического портала",
    "createthrusters:physics_gantry_shaft": "Вал физического портала",
    "create_high_seas:anchor": "Якорь",
    "create_high_seas:boat_engine": "Лодочный мотор",
    "create_high_seas:buoy": "Буй",
    "create_high_seas:oar_of_boat": "Весло",
    "create_high_seas:rudder": "Руль",
    "create_high_seas:seaglide": "Подводный буксир",
    "create_high_seas:white_sail": "Парус",
    "create_high_seas:wind_vane": "Флюгер",
    "create_submarine:barometer": "Барометр давления",
    "create_submarine:command_sub": "Бортовой компьютер",
    "create_submarine:decompression_chamber": "Декомпрессионная камера",
    "create_submarine:oxygen_bucket": "Ведро кислорода",
    "create_submarine:pump_controller": "Пульт насосов",
    "create_submarine:rockcutting_wheel": "Камнерезное колесо",
    "create_submarine:sonar": "Сонар",
    "create_abyss:cookiecutter_shark_spawn_egg": "Яйцо призыва бразильской акулы",
    "createrailwaysnavigator:penalty_anchor": "Штрафной якорь",
    "create_train_parts:pole": "Столб",
    "bellsandwhistles:folding_metro_door": "Складная дверь метро",
    "bellsandwhistles:sliding_metro_door": "Раздвижная дверь метро",
    "integrated_stronghold:disc_fragment_sight": "Осколок пластинки",
    "create_confectionery:bar_of_black_chocolate": "Плитка тёмного шоколада",
    "create_confectionery:bar_of_white_chocolate": "Плитка белого шоколада",
    "create_confectionery:bar_of_ruby_chocolate": "Плитка рубинового шоколада",
    "create_confectionery:bar_of_caramel": "Плитка карамели",
    "create_confectionery:full_chocolate_bar": "Большая плитка шоколада",
    "create_confectionery:chocolate_candy": "Шоколадная конфета",
    "create_confectionery:honey_candy": "Медовая конфета",
    "create_confectionery:candy_cane": "Леденцовая трость",
    "create_confectionery:candy_cane_pickaxe": "Леденцовая кирка",
    "create_confectionery:cocoa_butter": "Какао-масло",
    "create_confectionery:cocoa_powder": "Какао-порошок",
    "create_confectionery:crushed_cocoa": "Дроблёное какао",
    "create_confectionery:gingerdough": "Имбирное тесто",
    "create_confectionery:gingerbread": "Имбирный пряник",
    "create_confectionery:gingerbread_man": "Пряничный человечек",
    "create_confectionery:gingerbread_block": "Пряничный блок",
    "create_confectionery:marshmallow": "Зефир",
    "create_confectionery:marshmallow_on_a_stick": "Зефир на палочке",
    "create_confectionery:caramelized_marshmellow_on_a_stick": "Карамелизованный зефир на палочке",
    "create_confectionery:chocolate_glazed_marshmallow": "Зефир в шоколаде",
    "create_confectionery:hot_chocolate_bottle": "Горячий шоколад",
    "create_confectionery:soothing_hot_chocolate": "Успокаивающий горячий шоколад",
    "create_confectionery:chocolate_bricks": "Шоколадные кирпичи",
    "create_confectionery:caramel_bricks": "Карамельный блок",
    "createcafe:coffee_beans": "Кофейные зёрна",
    "createcafe:coffee_fruit": "Кофейная ягода",
    "createcafe:coffee_seeds": "Семена кофе",
    "createcafe:roasted_coffee_beans": "Обжаренные кофейные зёрна",
    "createcafe:coffee_grounds": "Молотый кофе",
    "createcafe:black_coffee": "Чёрный кофе",
    "createcafe:iced_coffee": "Холодный кофе",
    "createcafe:iced_coffee_cup": "Стакан для холодного кофе",
    "createcafe:iced_coffee_milk": "Холодный кофе с молоком",
    "createcafe:caramel_iced_coffee": "Холодный кофе с карамелью",
    "createcafe:cassava_seeds": "Семена маниоки",
    "createcafe:cassava_root": "Корень маниоки",
    "createcafe:tapioca_flour": "Мука тапиоки",
    "createcafe:raw_boba": "Сырые шарики тапиоки",
    "createcafe:boba": "Шарики тапиоки",
    "createcafe:empty_boba_cup": "Пустой стакан для бабл-ти",
    "createcafe:boba_cup": "Стакан с тапиокой",
    "createcafe:strawberry_milk_tea": "Клубничный бабл-ти",
    "createcafe:apple_milk_tea": "Яблочный бабл-ти",
    "createcafe:oreo": "Печенье с кремом",
    "createcafe:oreo_dough": "Тесто для печенья с кремом",
}


def latin(line):
    # строка в основном на латинице: значит, чужой текст без перевода
    t = re.sub(r"&.", "", line)
    lat = len(re.findall(r"[A-Za-z]", t))
    cyr = len(re.findall(r"[А-Яа-яЁё]", t))
    return lat > cyr


def ru_only(b, keys):
    for k in keys:
        for q in b.ch[k]["quests"]:
            q["desc"] = [d for d in q.get("desc", []) if not latin(d) or d.startswith("{image:")]


def has(b, r):
    return [r] if r in b.ref2q else []


def items(b, key, rows, deps0, xp=20, opt_from=99):
    # rows: (id, текст или None); первый квест зависит от deps0, дальше авто
    out = []
    for n, (i, txt) in enumerate(rows):
        r = b.add(key, [i], text=[txt] if txt else None, deps=deps0 if n == 0 else None, xp=xp,
                  optional=n >= opt_from)
        if r:
            out.append(r)
    return out


def finale(b, key, title, text, deps, table):
    b.custom(key, "finale", title, [{"type": "check", "title": "Подвести итог главы"}], deps=deps,
             text=[text, "&7Откроется, когда будут выполнены последние квесты главы."],
             rewards=[{"type": "xp", "amount": 200}, {"type": "choice", "table": table}], finale=True, sub="Финал главы")


def run(b):
    for rid, nm in RU.items():
        ns, p = rid.split(":", 1)
        lex.LANG_RU[f"item.{ns}.{p}"] = nm
    keys = []

    # ================= Copycats+ =================
    C = "cc_copycats"
    if "copycats:copycat_block" in lex.IDS:
        keys.append(C)
        b.chapter(C, "Блоки-подражатели", "Copycats+: любые формы из любых блоков", "Эра механизмов", "copycats:copycat_slope", 5.6,
                  intro=("Блоки-подражатели", "Форма от одного блока, вид от другого",
                         ["&6Copycats+&r добавляет десятки заполняемых блоков: склоны, балки, ступени, двери, люки, заборы, шестерни.",
                          "Поставьте заполняемый блок и нажмите по нему &bПКМ&r любым блоком: он примет его вид. Гаечный ключ снимает облицовку.",
                          "Так можно сделать красивый корабль или дом без сотни разных блоков из разных модов."],
                         has(b, "create_andesite/andesite_alloy")))
        rows = [("copycats:copycat_block", None), ("copycats:copycat_slab", None), ("copycats:copycat_stairs", None),
                ("copycats:copycat_slope", None), ("copycats:copycat_vertical_slope", None), ("copycats:copycat_beam", None),
                ("copycats:copycat_board", None), ("copycats:copycat_vertical_step", None), ("copycats:copycat_layer", None),
                ("copycats:copycat_catwalk", None), ("copycats:copycat_door", None), ("copycats:copycat_trapdoor", None),
                ("copycats:copycat_fence", None), ("copycats:copycat_wall", None), ("copycats:copycat_ladder", None),
                ("copycats:copycat_pane", None), ("copycats:copycat_shaft", "Вал, который выглядит как любой блок: передаёт вращение, как обычный."),
                ("copycats:copycat_cogwheel", "Шестерня под вид любого блока."),
                ("copycats:copycat_fluid_pipe", "Труба для жидкостей, спрятанная под облицовкой."),
                ("copycats:copycat_ghost_block", "Призрачный блок: выглядит как блок, но сквозь него можно пройти.")]
        rs = items(b, C, rows, ["intro"], opt_from=14)
        finale(b, C, "Мастер облицовки", "Вы освоили заполняемые блоки: теперь любая форма может выглядеть как угодно.", rs[-6:-2], "choice_tech")

    # ================= Убранство поездов =================
    T = "tr_parts"
    if "create_train_parts:crossing" in lex.IDS or "bellsandwhistles:headlight" in lex.IDS:
        keys.append(T)
        b.chapter(T, "Вагоны и вокзалы", "Train Parts, Railways Navigator, Bells & Whistles", "Эра механизмов", "bellsandwhistles:headlight", 6.2,
                  intro=("Вагоны и вокзалы", "Поезд должен быть красивым",
                         ["Три мода для поездов Create: &6Train Parts&r (ступени, трапы, сдвижные окна, шлагбаум), "
                          "&6Bells & Whistles&r (прожекторы, путеочистители, поручни, двери метро, платформы) и "
                          "&6Create Railways Navigator&r (навигатор маршрутов и табло на станциях).",
                          "Навигатор в руке строит маршрут между станциями с пересадками, а табло показывает ближайшие поезда."],
                         has(b, "create_trains/railway_casing") or [b.root("create_trains")] if "create_trains" in b.ch else []))
        rows = [("create_train_parts:train_step_andesite", "Ступени у двери вагона: удобно заходить с низкой платформы."),
                ("create_train_parts:train_slide_andesite", "Трап выдвигается, когда поезд стоит на станции."),
                ("create_train_parts:andesite_sliding_window", None),
                ("create_train_parts:crossing", "Шлагбаум закрывается сигналом редстоуна, например от сигнала поезда."),
                ("bellsandwhistles:headlight", None), ("bellsandwhistles:andesite_pilot", None),
                ("bellsandwhistles:andesite_grab_rails", None), ("bellsandwhistles:andesite_door_step", None),
                ("bellsandwhistles:metro_casing", None), ("bellsandwhistles:metro_window", None),
                ("bellsandwhistles:sliding_metro_door", "Раздвижная дверь метро: открывается, когда поезд останавливается."),
                ("bellsandwhistles:station_platform", None),
                ("createrailwaysnavigator:navigator", "Навигатор: выберите станцию назначения, и он покажет поезда и пересадки."),
                ("createrailwaysnavigator:advanced_display", "Табло для станции или вагона: показывает ближайшие поезда и следующую остановку."),
                ("createrailwaysnavigator:train_station_clock", None)]
        rs = items(b, T, rows, ["intro"], opt_from=12)
        finale(b, T, "Образцовая дорога", "Вагоны с подножками, табло на станциях и навигатор в кармане.", rs[-5:-1], "choice_tech")

    # ================= Еда Create =================
    F = "food_factory"
    if "createfood:butter" in lex.IDS:
        keys.append(F)
        b.chapter(F, "Фабрика вкуса", "Create Food: кухня на машинах Create", "Хозяйство и путешествия", "createfood:hamburger", 11.2,
                  intro=("Фабрика вкуса", "Еда, которую готовят машины",
                         ["&6Create Food&r добавляет больше тысячи блюд, и почти все их делают машины Create: жернова мелют, "
                          "смешиватель месит тесто, пресс раскатывает, механическая рука кладёт начинку, вентилятор печёт.",
                          "Смотрите рецепты в JEI: у многих блюд несколько шагов, их удобно собрать на конвейере."],
                         has(b, "create_andesite/mechanical_press") + has(b, "fd_kitchen/cooking_pot")))
        rows = [("createfood:salt", "Соль: основа для многих рецептов."), ("createfood:butter", None), ("createfood:cheese_slice", None),
                ("createfood:pizza_dough", None), ("createfood:cheese_pizza", None), ("createfood:bun", None),
                ("createfood:ground_beef", None), ("createfood:hamburger", None), ("createfood:cheeseburger", None),
                ("createfood:pasta", None), ("createfood:macaroni_bowl", None), ("createfood:waffle", None),
                ("createfood:cake_base", None), ("createfood:cheesecake", None), ("createfood:berry_pie", None),
                ("createfood:sausages", None), ("createfood:beef_taco", None), ("createfood:potato_chips", None),
                ("createfood:onion_rings", None), ("createfood:ration_box", "Паёк: сытная еда в дорогу.")]
        rs = items(b, F, rows, ["intro"], opt_from=14)
        finale(b, F, "Шеф машинной кухни", "Ваши машины готовят пиццу, бургеры и торты.", rs[-6:-2], "choice_home")

    S = "food_sweets"
    if "create_confectionery:marshmallow" in lex.IDS or "createcafe:black_coffee" in lex.IDS or "create_winery:cider" in lex.IDS:
        keys.append(S)
        b.chapter(S, "Сладости и напитки", "Confectionery, Cafe, Winery", "Хозяйство и путешествия", "create_confectionery:full_chocolate_bar", 11.3,
                  intro=("Сладости и напитки", "Шоколад, кофе и вино",
                         ["&6Create Confectionery&r: какао, шоколад трёх видов, карамель, зефир и пряники. Шоколад течёт по трубам и разливается в формы.",
                          "&6Create Cafe&r: кофе и бабл-ти с шариками тапиоки, десятки вкусов.",
                          "&6Create Winery&r: виноград, сусло, вино и сидр, выдержка в винном погребе."],
                         has(b, "create_fluids/mechanical_pump") or has(b, "create_andesite/mechanical_press")))
        rows = [("create_confectionery:crushed_cocoa", None), ("create_confectionery:cocoa_butter", None),
                ("create_confectionery:cocoa_powder", None), ("create_confectionery:full_chocolate_bar", None),
                ("create_confectionery:chocolate_candy", None), ("create_confectionery:marshmallow", None),
                ("create_confectionery:chocolate_glazed_marshmallow", None), ("create_confectionery:gingerbread_man", None),
                ("create_confectionery:hot_chocolate_bottle", None), ("create_confectionery:candy_cane_pickaxe", None)]
        rs = items(b, S, rows, ["intro"], opt_from=8)
        b.anchors[S] = b.root(S)
        rows = [("createcafe:coffee_beans", "Кофейные зёрна растут на кусте кофе, семена ищите в JEI."),
                ("createcafe:roasted_coffee_beans", None), ("createcafe:coffee_grounds", None), ("createcafe:black_coffee", None),
                ("createcafe:iced_coffee_milk", None), ("createcafe:tapioca_flour", None), ("createcafe:boba", None),
                ("createcafe:strawberry_milk_tea", None)]
        rs += items(b, S, rows, ["intro"], opt_from=6)
        b.anchors[S] = b.root(S)
        rows = [("create_winery:red_grapes", "Виноград растёт на кусте, его можно найти в мире или в JEI."),
                ("create_winery:grape_juice", None), ("create_winery:merlot", None), ("create_winery:wine_cellar", None),
                ("create_winery:apple_juice", None), ("create_winery:cider", None), ("create_winery:champaign", None)]
        rs += items(b, S, rows, ["intro"], opt_from=5)
        finale(b, S, "Кондитер и сомелье", "Шоколад, кофе и вино делаются сами.", [r for r in rs if not b.ref2q[r][1].get("optional")][-3:], "choice_home")

    # ================= Торговля =================
    M = "market"
    if "stockmarket:market_terminal" in lex.IDS or "trading_floor:trading_depot" in lex.IDS:
        keys.append(M)
        b.chapter(M, "Рынок и лавки", "Stock Market, Trading Floor, Tradeworks", "Хозяйство и путешествия", "stockmarket:market_terminal", 16,
                  intro=("Рынок и лавки", "Торговля на машинах Create",
                         ["&6Stock Market&r: биржевой терминал, где цены на товары меняются по спросу и предложению.",
                          "&6Trading Floor&r: торговое депо, в которое ставится житель, и механика Create торгует с ним сама.",
                          "&6Tradeworks&r: стеллажи и скатерти для витрин, чтобы игроки могли покупать товары друг у друга."],
                         has(b, "create_logistics/" + "stock_link") or has(b, "create_brass/brass_casing")))
        rows = [("trading_floor:trading_depot", "Поставьте депо рядом с жителем: механическая рука или конвейер подаёт товар, и депо проводит сделку."),
                ("stockmarket:market_terminal", "Терминал биржи: продавайте и покупайте товары по текущей цене."),
                ("stockmarket:market_remote", "Пульт биржи: торговать можно не подходя к терминалу."),
                ("tradeworks:oak_shelf", None), ("tradeworks:andesite_metal_shelf", None), ("tradeworks:red_inverted_table_cloth", None)]
        rs = items(b, M, rows, ["intro"], opt_from=4)
        finale(b, M, "Торговый дом", "Депо, биржа и витрины работают на вас.", rs[:3], "choice_home")

    # ================= Gadgets & Gizmos =================
    G = "gizmos"
    if "createthrusters:thruster" in lex.IDS:
        keys.append(G)
        b.chapter(G, "Двигатели и гаджеты", "Gadgets & Gizmos: тяга, моторы и пульты для кораблей", "Небо и техника", "createthrusters:thruster", 10.5,
                  intro=("Двигатели и гаджеты", "Реактивная тяга для кораблей",
                         ["&6Gadgets & Gizmos&r (Create Thrusters) добавляет кораблям Aeronautics двигатели на топливе, маневровые сопла, "
                          "моторы и генераторы, пульты, подшипники для рулей и много мелких устройств.",
                          "Двигателю нужно топливо из окислителя. Мощность растёт с улучшениями тяги.",
                          "&cТяжёлый мод: на Android следите за FPS."],
                         has(b, "aero_fleet/intro") or ([b.root("aero_fleet")] if "aero_fleet" in b.ch else [])))
        rows = [("createthrusters:blackstone_alloy", "Чернитовый сплав: основной материал мода."),
                ("createthrusters:blackstone_sheet", None),
                ("createthrusters:computation_mechanism", "Вычислительный механизм: собирается по шагам, как механизм точности."),
                ("createthrusters:fuel_oxidizer", "Окислитель делает топливо для двигателей."),
                ("createthrusters:oxidized_solid_fuel", None),
                ("createthrusters:small_thruster", "Малый двигатель толкает корабль в сторону, куда смотрит сопло."),
                ("createthrusters:thruster", None), ("createthrusters:rcs_thruster", "Маневровые сопла поворачивают корабль на месте."),
                ("createthrusters:propulsion_upgrade_t1", None), ("createthrusters:industrial_motor", None),
                ("createthrusters:alternator", None), ("createthrusters:andesite_cable", None),
                ("createthrusters:analogue_joystick", None), ("createthrusters:advanced_contraption_controller", None),
                ("createthrusters:aileron_bearing", None), ("createthrusters:vector_bearing", None),
                ("createthrusters:ship_coupler", None), ("createthrusters:physics_goggles", None),
                ("createthrusters:ion_thruster", None), ("createthrusters:claw", None), ("createthrusters:scissor_piston", None),
                ("createthrusters:entity_launcher", None), ("createthrusters:powered_zipline", None)]
        rs = items(b, G, rows, ["intro"], xp=25, opt_from=17)
        finale(b, G, "Инженер тяги", "Двигатели, моторы и пульты собраны: корабль летит куда скажете.", rs[10:16][-4:], "choice_tech")

    # ================= Субмарины и корабли =================
    U = "submarine"
    if "create_submarine:ballast_tank" in lex.IDS:
        keys.append(U)
        b.chapter(U, "Под водой и на волнах", "Create Submarine и High Seas", "Небо и техника", "create_submarine:submarine_propeller", 10.7,
                  intro=("Под водой и на волнах", "Корабли, которые плавают и ныряют",
                         ["&6Create Submarine&r: балластные цистерны набирают воду и корабль погружается, гребной винт и водомёт двигают его, "
                          "кислород подаётся диффузором, сонар показывает дно.",
                          "&6Create High Seas&r: паруса, руль, якорь, лодочный мотор и буи для надводных кораблей.",
                          "&cТяжёлый мод: на Android следите за FPS."],
                         has(b, "sim_physics/intro") or ([b.root("sim_physics")] if "sim_physics" in b.ch else [])))
        rows = [("create_submarine:ballast_tank", None), ("create_submarine:ballast_vent", None),
                ("create_submarine:submarine_propeller", None), ("create_submarine:water_thruster", None),
                ("create_submarine:copper_pressurizer", None), ("create_submarine:electrolyzer", None),
                ("create_submarine:oxygene_diffuser", None), ("create_submarine:sonar", None), ("create_submarine:floater", None),
                ("create_submarine:steel_cable", None), ("create_submarine:industrial_alarm", None),
                ("create_submarine:underwater_mine", None)]
        rs = items(b, U, rows, ["intro"], xp=25, opt_from=10)
        b.anchors[U] = b.root(U)
        rows = [("create_high_seas:white_sail", None), ("create_high_seas:rudder", None), ("create_high_seas:anchor", None),
                ("create_high_seas:boat_engine", None), ("create_high_seas:buoy", None), ("create_high_seas:seaglide", None)]
        rs2 = items(b, U, rows, ["intro"], xp=20, opt_from=4)
        finale(b, U, "Капитан глубин", "Подлодка ныряет, а парусник ходит по волнам.", rs[6:9] + rs2[2:4], "choice_tech")

    # ================= Структуры Integrated Villages и Stronghold =================
    X = "act_explore"
    if X in b.ch:
        villages = [("airship_village", "Деревня воздушных кораблей", "Корабли висят высоко над землёй. Ищите их в небе: снизу видны канаты и паруса."),
                    ("tavern_village", "Деревня с таверной", None), ("pirate_village", "Пиратская деревня", None),
                    ("mediterranean_village", "Средиземноморская деревня", None), ("oasis_village", "Деревня в оазисе", None),
                    ("cabin_village", "Деревня хижин", None), ("clockwork_village", "Деревня часовщиков", "Механизмы Create в этой деревне работают по-настоящему."),
                    ("marketstead_village", "Рыночная деревня", None), ("mossy_mounds", "Мшистые холмы", None),
                    ("kutcha_village", "Деревня кутча", None), ("sunken_village", "Затонувшая деревня", None)]
        prev = b.root(X)
        for n, (sid, t, txt) in enumerate(villages):
            b.custom(X, f"iv_{sid}", t, [{"type": "structure", "id": f"integrated_villages:{sid}", "title": t}],
                     deps=[prev], xp=40, optional=n >= 3, sub="Integrated Villages",
                     text=[txt or "Найдите эту деревню и войдите в неё: квест засчитается сам.",
                           "&7Деревни Integrated Villages заменяют обычные деревни Minecraft."])
            if n == 0:
                prev = f"{X}/iv_{sid}"
        b.custom(X, "iv_stronghold", "Новая крепость Края", [{"type": "structure", "id": "integrated_stronghold:stronghold", "title": "Крепость Края"}],
                 xp=60, sub="Integrated Stronghold",
                 text=["Integrated Stronghold заменяет обычную крепость Края: она больше, с ловушками и сокровищницами.",
                       "Портал в Край по-прежнему внутри. Глаз Края укажет дорогу."])

    ru_only(b, keys)
