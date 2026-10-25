"""Авиация и транспорт: Create Simulated, Aeronautics, Propulsion, Aeroworks, Compact Flap,
Ornithopter Glider, Offroad, Tracks."""
import lex

G = "Эра механизмов"


def P(ns, *n):
    return [f"{ns}:{x}" for x in n]


def col(ns, suffix, base=None):
    out = [f"{ns}:{c}_{suffix}" for c in lex.COLORS]
    if base:
        out = [f"{ns}:{base}"] + [o for o in out if o != f"{ns}:{base}"]
    return out


def run(b):
    # ================= Физика штуковин (Simulated) =================
    S, s = "sim_physics", "simulated"
    b.chapter(S, "Законы полёта", "Create Simulated — физические штуковины", G, "simulated:physics_assembler", 9,
              intro=("Да пребудет с вами физика", "Основа авиации и транспорта",
                     ["Create Simulated превращает собранную конструкцию в &eфизическое тело&r: она падает, катится, качается и сталкивается.",
                      "На этом построены дирижабли, самолёты и вездеходы из следующих глав.",
                      "Начните с физического сборщика — он «оживляет» постройку."], ["create_contraptions/mechanical_bearing"]
                     if "create_contraptions/mechanical_bearing" in b.ref2q else []))
    b.add(S, P(s, "physics_assembler"), text="Сердце каждой физической штуковины. Поставьте его в постройку, подайте сигнал — и блоки станут единым физическим телом.")
    b.adv(S, "simulated:applied_kinematics", deps=["physics_assembler"])
    b.add(S, P(s, "honey_glue"), text="Мёдоклей отмечает, какие блоки входят в штуковину. Выделите область — блоки склеятся.")
    b.adv(S, "simulated:not_gonna_sugarcoat_it", deps=["honey_glue"], optional=True)
    b.add(S, P(s, "merging_glue"), text="Скрепляющий клей соединяет две отдельные физические штуковины в одну.")
    b.add(S, P(s, "contraption_diagram"), text="Схема показывает массу, центр тяжести и подъёмную силу штуковины — главный инструмент конструктора.")
    b.adv(S, "simulated:measure_once_build_twice", deps=["contraption_diagram"])
    b.add(S, col(s, "nameplate"), k="nameplate", title="Именная табличка", optional=True)
    b.add(S, P(s, "iron_handle", "copper_handle") + col(s, "handle"), k="handle", title="Скоба",
          text="За скобу можно ухватиться и ехать вместе со штуковиной, даже если она летит.")
    b.adv(S, "simulated:get_a_grip", deps=["handle"])
    b.add(S, P(s, "steering_wheel"), text="Штурвал поворачивает подключённые механизмы, пока вы его держите. Так рулят кораблями и машинами.")
    b.add(S, P(s, "throttle_lever"), text="Тяговый рычаг задаёт силу сигнала — удобно для управления скоростью двигателей.")
    b.adv(S, "simulated:unpowered_steering", deps=["steering_wheel"], optional=True)
    b.add(S, P(s, "swivel_bearing"), text="Шарнирный подшипник крутит часть постройки как отдельное физическое тело — для дверей, башен и шарниров.")
    b.adv(S, "simulated:you_spin_me_right_round", deps=["swivel_bearing"], optional=True)
    b.add(S, P(s, "spring"), text="Пружина соединяет два блока упругой связью: подвеска, амортизаторы, батуты.")
    b.add(S, P(s, "torsion_spring"), text="Торсионная пружина возвращает повёрнутую часть в исходное положение.")
    b.add(S, P(s, "rope_coupling", "rope_connector", "rope_winch"), k="ropes", title="Верёвки и лебёдка", mode="set",
          text="Моток верёвки связывает крепления. Лебёдка наматывает верёвку и поднимает груз.")
    b.adv(S, "simulated:learning_the_ropes", deps=["ropes"])
    b.add(S, P(s, "docking_connector"), text="Стыковочный порт соединяет две штуковины в полёте или на земле. Отпускается по сигналу.")
    b.adv(S, "simulated:a_calculated_connection", deps=["docking_connector"], optional=True)
    b.add(S, P(s, "engine_assembly"), text="Двигательный узел — деталь портативного двигателя, собирается последовательной сборкой.")
    b.add(S, col(s, "portable_engine"), k="portable_engine", title="Портативный двигатель",
          text="Небольшой двигатель на топливе: даёт вращение прямо на борту без парового котла.")
    b.adv(S, "simulated:steamless_engine", deps=["portable_engine"])
    b.add(S, P(s, "gyroscopic_mechanism"), text="Механизм гироскопа — компонент датчиков и стабилизаторов.")
    b.add(S, P(s, "gimbal_sensor"), text="Гироскопный датчик выдаёт сигнал при наклоне. С ним штуковина держит равновесие сама.")
    b.add(S, P(s, "altitude_sensor"), text="Сигнал зависит от высоты: можно сделать автопилот по высоте.")
    b.add(S, P(s, "velocity_sensor"), text="Сигнал зависит от скорости штуковины.")
    b.add(S, P(s, "optical_sensor"), text="Видит блок прямо перед собой и подаёт сигнал — датчик препятствий.")
    b.add(S, P(s, "laser_pointer", "laser_sensor"), k="lasers", title="Лазер и лазерный датчик", mode="set",
          text="Лазер светит лучом, датчик подаёт сигнал при попадании — для дальних связей и прицелов.")
    b.add(S, P(s, "navigation_table"), text="Навигационный стол указывает направление к цели — компас для дальних перелётов.")
    b.add(S, P(s, "redstone_magnet"), text="Магнит притягивает другие штуковины, пока на него подаётся сигнал.")
    b.add(S, P(s, "redstone_accumulator", "redstone_inductor"), k="redstone_sim", title="Аккумулятор и индуктор", mode="set")
    b.add(S, P(s, "directional_linked_receiver", "modulating_linked_receiver"), k="receivers", title="Особые приёмники", mode="set")
    b.add(S, P(s, "linked_typewriter"), text="Частотная пишущая машинка: каждая клавиша клавиатуры управляет своей частотой. Удобное управление кораблём.")
    b.add(S, P(s, "analog_transmission", "directional_gearshift"), k="transmissions", title="Передачи штуковин", mode="set")
    b.add(S, P(s, "auger_shaft", "auger_cog"), k="auger", title="Шнек", mode="set",
          text="Шнековый вал перемещает предметы и сыпучие блоки вдоль себя.")
    b.add(S, P(s, "plunger_launcher"), text="Вантузомёт цепляется к штуковине и тянет её к вам.")
    b.add(S, col(s, "symmetric_sail"), k="sym_sail", title="Симметричный парус",
          text="Парус, который работает одинаково в обе стороны — пригодится для крыльев и рулей.")
    b.add(S, P(s, "physics_assembler"), k="finale_sim", title="Конструктор физики", finale=True, deps=["lasers", "navigation_table", "portable_engine", "docking_connector"],
          xp=100, text="Вы освоили физику штуковин. Пора в небо!",
          rewards=[{"type": "xp", "amount": 100}, {"type": "item", "id": "simulated:honey_glue", "count": 2}])

    # ================= Небесный флот (Aeronautics + Propulsion + ...) =================
    A, a, pr = "aero_fleet", "aeronautics", "createpropulsion"
    b.chapter(A, "Небесный флот", "Create Aeronautics и Propulsion — дирижабли и самолёты", G, "aeronautics:propeller_bearing", 10,
              intro=("Всё небо — ваш простор", "Аэростаты, пропеллеры и реактивная тяга",
                     ["Aeronautics добавляет то, что держит штуковину в воздухе: &eоболочки аэростатов&r с горячим воздухом, &eлевитит&r и пропеллеры.",
                      "Propulsion добавляет реактивные двигатели, крылья и топливо.",
                      "Сначала освойте главу «Законы полёта»."], [f"{S}/physics_assembler"]))
    b.add(A, P(a, "aviators_goggles"), text="Очки авиатора показывают подъёмную силу и тягу аэродинамических блоков.")
    b.adv(A, "aeronautics:high_fashion", deps=["aviators_goggles"], optional=True)
    b.add(A, col(a, "envelope", "white_envelope"), k="envelope", title="Оболочка аэростата",
          text="Из оболочек строят герметичный купол. Наполненный горячим воздухом, он поднимает штуковину.")
    b.add(A, col(a, "envelope_encased_shaft", "white_envelope_encased_shaft"), k="envelope_shaft", title="Вал в оболочке", optional=True)
    b.add(A, P(a, "adjustable_burner"), text="Горелка нагревает воздух в оболочке. Сила огня регулируется сигналом — так управляют высотой.")
    b.adv(A, "aeronautics:head_in_the_clouds", deps=["adjustable_burner", "envelope"])
    b.add(A, P(a, "steam_vent"), text="Паровое сопло наполняет оболочку паром от котла вместо огня горелки.")
    b.add(A, P(a, "wooden_propeller"), text="Простой пропеллер: создаёт тягу при вращении. Ставится на подшипник.")
    b.add(A, P(a, "andesite_propeller"), text="Андезитовый пропеллер прочнее и даёт больше тяги.")
    b.adv(A, "aeronautics:for_every_action", deps=["wooden_propeller"])
    b.add(A, P(a, "propeller_bearing"), text="Пропеллерный подшипник собирает из парусов большой винт — главная тяга дирижабля.")
    b.adv(A, "aeronautics:in_thrust_we_trust", deps=["propeller_bearing"])
    b.add(A, P(a, "gyroscopic_propeller_bearing"), text="Гироскопический подшипник держит винт ровно, даже если корпус качается.")
    b.add(A, P(a, "smart_propeller"))
    b.add(A, P(a, "end_stone_powder"), text="Порошок из дроблёного эндерняка — компонент левититовой смеси.")
    b.add(A, P(a, "levitite_blend"), mode="check", title="Левититовая смесь",
          text=["Левититовая смесь — жидкость, которую смешивают в чаше. Залейте её в мир и дождитесь кристаллизации."])
    b.add(A, P(a, "levitite"), text="Левитит — парящий блок: сам держится в воздухе и поднимает постройку без горелок.")
    b.adv(A, "aeronautics:unidentified_floating_object", deps=["levitite"])
    b.add(A, P(a, "pearlescent_levitite"), optional=True)
    b.add(A, P(a, "mounted_potato_cannon"), text="Стационарная картофельная пушка: ставится на корабль и стреляет по сигналу.")
    b.adv(A, "aeronautics:heavier_artillery", deps=["mounted_potato_cannon"], optional=True)
    # --- Propulsion ---
    RU = {
        "thruster": "Реактивный двигатель", "vector_thruster": "Векторный двигатель",
        "liquid_vector_thruster": "Жидкостный векторный двигатель", "ion_thruster": "Ионный двигатель",
        "solid_burner": "Твердотопливная горелка", "liquid_burner": "Жидкостная горелка", "stirling_engine": "Двигатель Стирлинга",
        "wing": "Крыло", "tempered_wing": "Закалённое крыло", "copycat_wing": "Заполняемое крыло",
        "platinum_ore": "Платиновая руда", "raw_platinum": "Рудная платина", "platinum_ingot": "Платиновый слиток",
        "platinum_sheet": "Платиновый лист", "platinum_casing": "Платиновый корпус", "platinum_fluid_tank": "Платиновый бак",
        "pine_resin": "Сосновая смола", "turpentine": "Скипидар", "oxidizer": "Окислитель", "redstone_converter": "Редстоун-преобразователь",
        "redstone_transmission": "Редстоун-трансмиссия", "tilt_adapter": "Адаптер наклона", "cable": "Кабель",
        "cable_spool": "Катушка кабеля", "cable_hub": "Кабельный узел", "cable_relay": "Кабельное реле",
        "auto_glue": "Автоклей", "assembler_stick": "Автосборка", "glued_contraption_mover": "Переносчик штуковин",
        "glued_contraption_cloner": "Клонировщик штуковин", "contraption_remover": "Удалитель штуковин",
        "coral_generator": "Коралловый генератор",
    }

    def pa(n, text=None, **kw):
        return b.add(A, [f"{pr}:{n}"], title=RU.get(n), text=text, **kw)

    pa("platinum_ore", text="Платина — металл Propulsion. Руда встречается глубоко под землёй, в том числе в глубинном сланце.")
    pa("raw_platinum")
    pa("platinum_ingot")
    pa("platinum_sheet", text="Платиновый лист прессуется механическим прессом. Из него делают корпуса и баки двигателей.")
    pa("platinum_casing")
    pa("platinum_fluid_tank", text="Бак для топлива на борту.")
    pa("pine_resin", text="Смола хвойных деревьев — сырьё для скипидара, жидкого топлива двигателей.")
    b.add(A, [f"{pr}:turpentine"], mode="check", title="Скипидар", text=["Скипидар перегоняют из смолы. Это топливо жидкостной горелки и двигателей."])
    b.add(A, [f"{pr}:oxidizer"], mode="check", title="Окислитель", text=["Окислитель усиливает горение топлива в реактивных двигателях."])
    pa("solid_burner", text="Горелка сжигает твёрдое топливо и греет двигатель.")
    pa("liquid_burner", text="Горелка на жидком топливе — работает дольше и сильнее.")
    pa("stirling_engine", text="Двигатель Стирлинга превращает тепло горелки во вращение.")
    pa("thruster", text="Реактивный двигатель создаёт прямую тягу, сжигая топливо из бака.")
    b.adv(A, "createpropulsion:fuel_propulsion", title="Реактивная тяга", deps=["thruster"])
    pa("vector_thruster", text="Векторный двигатель поворачивает сопло по сигналу — тяга в нужную сторону.")
    b.adv(A, "createpropulsion:im_vectoring_it", title="Управляемый вектор", deps=["vector_thruster"], optional=True)
    pa("liquid_vector_thruster")
    pa("cable", text="Кабель передаёт энергию между блоками Propulsion.")
    pa("cable_spool")
    pa("cable_hub")
    pa("cable_relay")
    pa("ion_thruster", text="Ионный двигатель работает на энергии вместо топлива: тихо и без выхлопа.")
    b.adv(A, "createpropulsion:energy_propulsion", title="Электрическая тяга", deps=["ion_thruster"], optional=True)
    pa("wing", text="Крыло создаёт подъёмную силу, когда штуковина движется вперёд. Основа самолёта.")
    pa("tempered_wing")
    b.adv(A, "createpropulsion:mighty_wings", title="Могучие крылья", deps=["tempered_wing"])
    b.add(A, P(pr, "copycat_wing", "copycat_wing_8", "copycat_wing_12"), k="copycat_wing", title="Заполняемые крылья", optional=True)
    pa("tilt_adapter", text="Адаптер наклона превращает наклон штуковины в сигнал.")
    pa("redstone_converter")
    pa("redstone_transmission")
    pa("auto_glue")
    pa("assembler_stick")
    pa("glued_contraption_mover", optional=True)
    pa("glued_contraption_cloner", optional=True)
    pa("contraption_remover", optional=True)
    # --- Aeroworks / Compact Flap ---
    b.add(A, P("aeroworks", "gyroscope"), text="Гироскоп Aeroworks стабилизирует самолёт.")
    b.add(A, P("aeroworks", "joystick"))
    b.add(A, P("compactflap", "compact_flap_bearing"))
    b.add(A, P("compactflap", "analog_compact_flap_bearing"))
    b.add(A, P("compactflap", "compact_swivel_flap", "analog_swivel_bearing"), k="compact_swivel", title="Компактные физические подшипники", mode="set")
    # --- Орнитоптер ---
    o = "createornithopterglider"
    b.add(A, P(o, "rope"), k="glider_rope", title="Верёвка планериста")
    b.add(A, P(o, "elytra_piece"), text="Фрагмент элитр. Из них и сетки собирается орнитоптер-планер.")
    b.add(A, [f"{o}:wing_mesh"] + [f"{o}:wing_mesh_{c}" for c in lex.COLORS if c != "white"], k="wing_mesh", title="Крылатая сетка")
    b.add(A, [f"{o}:ornithopterglider"] + [f"{o}:ornithopterglider_{c}" for c in lex.COLORS if c != "white"],
          k="glider", title="Орнитоптер-планер", text="Личный планер: надевается как элитры и позволяет парить. Можно перекрасить.")
    b.add(A, P(a, "propeller_bearing"), k="finale_air", title="Капитан небес", finale=True,
          deps=["propeller_bearing", "levitite", "thruster", "wing", "glider"], xp=150,
          text="Вы построили собственный флот. Небо принадлежит вам!",
          rewards=[{"type": "xp", "amount": 150}, {"type": "item", "id": "createpropulsion:platinum_ingot", "count": 8}])

    # ================= Вездеходы (Offroad + Tracks) =================
    V = "off_road"
    b.chapter(V, "Колёса и гусеницы", "Create Offroad и Tracks — наземный транспорт", G, "offroad:tire", 11,
              intro=("Колёса и гусеницы", "Машины на физике",
                     ["Offroad и Tracks делают из физической штуковины машину: колёса, гусеницы, подвеска и буры.",
                      "Сначала разберитесь с физикой в главе «Законы полёта»."], [f"{S}/physics_assembler"]))
    b.add(V, P("offroad", "wheel_mount"), text="Колёсное крепление держит колесо на штуковине. По сигналу поворачивает и тормозит его.")
    b.add(V, P("offroad", "small_tire", "tire", "large_tire", "monstrous_tire"), k="tires", title="Шины всех размеров", mode="any",
          text="Чем больше шина, тем лучше она едет по неровностям и тем больше весит.")
    b.add(V, P("offroad", "monstrous_tire"), k="monster_tire", title="Гигантская шина", optional=True)
    b.add(V, P("offroad", "rockcutting_wheel"), text="Бурильное колесо прорезает камень на ходу — для горнодобывающих машин.")
    b.add(V, P("offroad", "borehead_bearing"), text="Буровой подшипник крутит буровую голову из блоков.")
    b.add(V, P("tracks", "track_mount"))
    b.add(V, P("tracks", "small_track_drive_wheel", "track_drive_wheel", "large_track_drive_wheel"), k="drive_wheels",
          title="Ведущие колёса", mode="any")
    b.add(V, P("tracks", "small_suspension_track", "suspension_track", "large_suspension_track"), k="susp_tracks",
          title="Гусеницы с подвеской", mode="any")
    b.add(V, P("tracks", "suspension_key"))
    b.add(V, P("tracks", "track_mount"), k="finale_tracks", title="Танкист", finale=True, deps=["susp_tracks", "drive_wheels", "tires"],
          xp=80, text="Ваша машина пройдёт где угодно.")
