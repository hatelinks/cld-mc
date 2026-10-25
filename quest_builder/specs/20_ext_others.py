"""Дополнение готовых глав: Farmer's Delight, Veggies Delight, Sophisticated, Storage Drawers,
Create Connected, Crafts & Additions, Mystical Agriculture."""
import lex


def P(ns, *names):
    return [f"{ns}:{n}" for n in names]


def run(b):
    # ---------- Farmer's Delight ----------
    FD, f = "fd_kitchen", "farmersdelight"
    b.add(FD, P(f, "straw"), text="Солома выпадает, если срезать траву или пшеницу ножом. Из неё делают тюки, холст и верёвки.")
    b.add(FD, P(f, "straw_bale"), optional=True)
    b.add(FD, P(f, "tree_bark"), text="Кора снимается топором с брёвен. Ценный компонент для органического компоста.")
    b.add(FD, P(f, "golden_knife", "diamond_knife", "netherite_knife"), k="knives", title="Ножи лучше", mode="any",
          text="Нож — главный инструмент кухни: режет еду на доске и даёт больше мяса с животных. Чем лучше металл, тем дольше служит.")
    b.add(FD, P(f, "cabbage", "tomato", "cabbage_leaf"), k="harvest_fd", title="Урожай фермера", mode="set")
    b.add(FD, P(f, "rice_panicle", "rice_bag", "rice_bale", "cooked_rice"), k="rice_line", title="Рисовая линия", mode="set",
          text="Рис растёт в воде. Метёлки обмолачивают в рис на доске, рис варят в котле.")
    b.add(FD, P(f, "cabbage_crate", "tomato_crate", "onion_crate", "carrot_crate", "potato_crate", "beetroot_crate"),
          k="crates", title="Ящики урожая", text="Ящики компактно хранят по 9 единиц овощей и красиво смотрятся на складе.")
    b.add(FD, P(f, "oak_cabinet", *[f"{w}_cabinet" for w in lex.WOODS if w != "oak"]), k="cabinets_all", title="Кладовые всех пород", optional=True)
    b.add(FD, P(f, "milk_bottle"))
    b.add(FD, P(f, "wheat_dough", "raw_pasta"), k="dough_fd", title="Тесто и паста", mode="set")
    cuts = ["bacon", "chicken_cuts", "cod_slice", "salmon_slice", "mutton_chops", "minced_beef", "beef_patty"]
    b.add(FD, P(f, *cuts), k="cuts", title="Разделка мяса", mode="set",
          text="Нож на разделочной доске режет сырое мясо и рыбу на ломтики и фарш — так из одного куска выходит несколько блюд.")
    cooked = ["cooked_bacon", "cooked_chicken_cuts", "cooked_cod_slice", "cooked_salmon_slice", "cooked_mutton_chops", "smoked_ham"]
    b.add(FD, P(f, *cooked), k="cooked_cuts", title="Жареные ломтики", mode="set")
    groups = [
        ("sandwiches", "Бутерброды", ["egg_sandwich", "bacon_sandwich", "chicken_sandwich", "mutton_wrap"]),
        ("salads", "Салаты", ["mixed_salad", "fruit_salad", "nether_salad"]),
        ("soups", "Супы", ["chicken_soup", "pumpkin_soup", "noodle_soup", "fish_stew", "baked_cod_stew", "bone_broth"]),
        ("mains", "Сытные блюда", ["steak_and_potatoes", "mushroom_rice", "stuffed_potato", "grilled_salmon",
                                   "squid_ink_pasta", "pasta_with_mutton_chop", "roasted_mutton_chops", "vegetable_noodles"]),
        ("rolls", "Роллы", ["kelp_roll", "kelp_roll_slice", "salmon_roll", "cod_roll"]),
        ("feasts", "Праздничный стол", ["roast_chicken_block", "stuffed_pumpkin_block", "honey_glazed_ham_block",
                                        "shepherds_pie_block", "rice_roll_medley_block"]),
        ("desserts", "Десерты", ["sweet_berry_cookie", "chocolate_pie", "melon_popsicle", "glow_berry_custard", "cake_slice",
                                 "apple_pie_slice", "pumpkin_pie_slice", "chocolate_pie_slice", "sweet_berry_cheesecake_slice"]),
        ("drinks", "Напитки", ["apple_cider", "melon_juice"]),
        ("pet_food", "Корм для питомцев", ["dog_food", "horse_feed"]),
    ]
    for k, t, items in groups:
        b.add(FD, P(f, *items), k=k, title=t, mode="any", xp=20,
              text=f"Приготовьте любое блюдо из группы «{t.lower()}». Каждое даёт свой эффект насыщения — попробуйте все!")
    b.add(FD, P(f, "barbecue_stick"))
    b.add(FD, P(f, "full_tatami_mat", "half_tatami_mat"), k="tatami_mats", optional=True)
    b.add(FD, P(f, "safety_net"), text="Сеть гасит падение: прыгайте сверху без урона.")
    b.add(FD, P(f, *[f"{c}_canvas_sign" for c in lex.COLORS]), k="canvas_colors", title="Цветные холщовые таблички", optional=True)

    # ---------- Veggies Delight ----------
    VD, v = "vd_veggies", "veggiesdelight"
    b.add(VD, P(v, "cauliflower_seeds", "turnip_seeds"), k="more_seeds", title="Семена цветной капусты и репы", mode="set")
    b.add(VD, P(v, "garlic_clove", "dandelion_leaf", "cauliflower_floret", "zucchini_slice", "potato_noodle"),
          k="cut_veg", title="Нарезка овощей", mode="set",
          text="Нож на разделочной доске режет овощи на дольки и ломтики для сложных блюд.")
    b.add(VD, P(v, "garlic_crate", "broccoli_crate", "zucchini_crate", "sweet_potato_crate", "turnip_crate",
                "bellpepper_crate", "cauliflower_crate"), k="vd_crates", title="Ящики овощей")
    vg = [
        ("vd_snacks", "Закуски", ["baked_sweet_potato", "roasted_zucchini", "roasted_cauliflower_floret", "smoked_bellpepper",
                                  "cauliflower_bread", "sweet_potato_pancakes"]),
        ("vd_salads", "Салаты", ["broccoli_salad", "coleslaw", "turnip_salad", "dandelion_and_eggs"]),
        ("vd_soups", "Супы и рагу", ["cauliflower_soup", "turnip_beef_stew", "garlic_chicken_stew", "cacciatore"]),
        ("vd_mains", "Горячее", ["garlic_baked_cod", "steak_and_broccoli", "rice_and_vegetables", "garlic_rice_with_cauliflower",
                                 "garlic_stuffed_mushrooms", "shakshouka", "cauliflower_kuku", "potato_noodles", "turnip_mutton_skewer"]),
        ("vd_wraps", "Роллы и бургеры", ["chicken_fajitas_wrap", "vegetables_wrap", "vegetarian_burger", "zucchini_sandwich",
                                         "mhadjeb"]),
        ("vd_veg_patty", "Вегетарианская котлета", ["raw_vegetarian_patty", "cooked_vegetarian_patty"]),
        ("vd_bakes", "Выпечка", ["carrot_cake", "turnip_cake", "zucchini_quiche", "vegan_pizza", "lasagna_pan",
                                 "beetroot_brownie_tray", "stuffed_bellpeppers_block", "stuffed_zucchini_boat"]),
        ("vd_drinks", "Напитки", ["carrot_juice", "dandelion_juice", "turnip_water", "fermented_garlic_honey"]),
    ]
    for k, t, items in vg:
        b.add(VD, P(v, *items), k=k, title=t, mode="any", xp=20,
              text=f"Приготовьте любое блюдо из группы «{t.lower()}».")

    # ---------- Sophisticated Storage ----------
    SS, s = "ss_storage", "sophisticatedstorage"
    b.add(SS, P(s, "copper_barrel", "iron_barrel", "gold_barrel", "diamond_barrel", "netherite_barrel"),
          k="barrels", title="Бочки всех уровней", mode="set",
          text="Бочки работают как сундуки, но ставятся в любую сторону и могут нести рисунок из блоков.")
    b.add(SS, P(s, "copper_shulker_box", "iron_shulker_box", "gold_shulker_box", "diamond_shulker_box", "netherite_shulker_box"),
          k="shulkers", title="Шалкеровые ящики всех уровней", mode="set",
          text="Улучшенные шалкеровые ящики сохраняют содержимое при разрушении — хранилище в кармане.")
    b.add(SS, P(s, "limited_barrel_2", "limited_barrel_3", "limited_barrel_4", "limited_iron_barrel_1",
                "limited_gold_barrel_1", "limited_diamond_barrel_1", "limited_netherite_barrel_1"),
          k="limited_barrels", title="Ограниченные бочки",
          text="Ограниченная бочка — аналог ящика Storage Drawers: хранит 1–4 вида предметов в больших количествах.")
    b.add(SS, P(s, "copper_to_iron_tier_upgrade", "iron_to_gold_tier_upgrade", "gold_to_diamond_tier_upgrade",
                "diamond_to_netherite_tier_upgrade", "basic_to_netherite_tier_upgrade"),
          k="tier_steps", title="Пошаговые расширения", mode="any",
          text="Расширение применяется ПКМ по уже стоящему хранилищу: оно повышает уровень, не вынимая вещей.")
    b.add(SS, P(s, "auto_smelting_upgrade", "auto_smoking_upgrade", "auto_blasting_upgrade"), k="auto_cook",
          title="Автоплавка", mode="any")
    b.add(SS, P(s, "compression_upgrade"))
    b.add(SS, P(s, "stonecutter_upgrade"))
    b.add(SS, P(s, "jukebox_upgrade", "advanced_jukebox_upgrade"), k="jukebox_ss", optional=True)
    b.add(SS, P(s, "alchemy_upgrade"))
    for u in ["pickup", "magnet", "filter", "void", "hopper", "feeding", "compacting", "alchemy"]:
        b.add(SS, P(s, f"advanced_{u}_upgrade"), xp=20)
    b.add(SS, P(s, "stack_upgrade_tier_4", "stack_upgrade_tier_5", "stack_upgrade_omega_tier"), k="stack_high",
          title="Переполнение IV–Ω", mode="set", xp=40)
    b.add(SS, P(s, "stack_upgrade_tier_1_plus"))
    b.add(SS, P(s, "stack_downgrade_tier_1", "stack_downgrade_tier_2", "stack_downgrade_tier_3"), k="downgrade",
          title="Понижение стака", mode="any", optional=True)
    b.add(SS, P(s, "super_packing_tape"))

    # ---------- Sophisticated Backpacks ----------
    SB, sb = "sb_backpacks", "sophisticatedbackpacks"
    b.add(SB, P(sb, "stack_upgrade_starter_tier"))
    b.add(SB, P(sb, "auto_smelting_upgrade", "auto_smoking_upgrade", "auto_blasting_upgrade"), k="auto_cook_sb", title="Автоплавка в рюкзаке")
    for u in ["alchemy", "compacting", "deposit", "feeding", "jukebox", "pump", "refill", "restock", "tool_swapper"]:
        b.add(SB, P(sb, f"advanced_{u}_upgrade"), xp=20)
    b.add(SB, P(sb, "stack_downgrade_tier_1", "stack_downgrade_tier_2", "stack_downgrade_tier_3"), k="downgrade_sb",
          title="Понижение стака", optional=True)

    # ---------- Storage Drawers ----------
    SD, d = "sd_drawers", "storagedrawers"
    b.add(SD, P(d, "obsidian_storage_upgrade"))
    b.add(SD, P(d, "netherite_storage_upgrade"), xp=40)
    b.add(SD, P(d, "one_stack_upgrade"), optional=True)
    b.add(SD, P(d, "compacting_drawers_2", "compacting_half_drawers_2", "compacting_half_drawers_3"), k="compacting_more",
          title="Другие спрессовывающие ящики")
    b.add(SD, P(d, "hopper_upgrade"))
    b.add(SD, P(d, "magnet_upgrade", "magnet_upgrade_2", "magnet_upgrade_3"), k="drawer_magnet", title="Магнитные улучшения", mode="set")
    b.add(SD, P(d, "balance_fill_upgrade"))
    b.add(SD, P(d, "conversion_upgrade"))
    b.add(SD, P(d, "min_redstone_upgrade", "max_redstone_upgrade"), k="redstone_minmax", mode="set", title="Редстоун мин./макс.")
    b.add(SD, P(d, "portability_upgrade"))
    b.add(SD, P(d, "remote_upgrade", "remote_group_upgrade"), k="remote", title="Удалённый доступ", mode="set")
    b.add(SD, P(d, "detached_drawer"))
    b.add(SD, P(d, "personal_key", "priority_key", "shroud_key", "suspend_key"), k="keys_more", title="Особые ключи", mode="set")
    b.add(SD, P(d, "keybutton_drawer", "keybutton_quantify", "keybutton_concealment"), k="keybuttons", title="Кнопки-ключи", mode="any")

    # ---------- Create Connected ----------
    CC, c = "create_connected_ch", "create_connected"
    b.add(CC, P(c, "vertical_parallel_gearbox", "vertical_six_way_gearbox"), k="vertical_gearboxes", mode="set",
          title="Вертикальные передачи")
    b.add(CC, P(c, "copycat_box", "copycat_vertical_step"), k="copycats_more", mode="set", title="Ещё заполняемые блоки")
    b.add(CC, P(c, "fan_seething_catalyst", "fan_withering_catalyst", "fan_enriched_catalyst", "fan_exploding_catalyst",
                "fan_ending_catalyst_dragons_breath", "fan_ending_catalyst_dragon_head"), k="special_catalysts",
          title="Особые катализаторы", mode="any",
          text="Катализатор ставится перед вентилятором и превращает поток воздуха в нужную обработку без открытого огня или жидкости.")
    b.add(CC, P(c, "redstone_link_wildcard"))

    # ---------- Crafts & Additions ----------
    CA, a = "ca_electric", "createaddition"
    b.add(CA, P(a, "iron_wire", "gold_wire"), k="wires", title="Железный и золотой провод", mode="set")
    b.add(CA, P(a, "gold_spool", "electrum_spool"), k="spools", title="Улучшенные катушки", mode="set",
          text="Катушка хранит провод для соединения коннекторов. Разные провода пропускают разный ток.")
    b.add(CA, P(a, "festive_spool"), optional=True)
    b.add(CA, P(a, "electrum_rod", "electrum_nugget", "electrum_block"), k="electrum_more", mode="set", title="Электрум")
    b.add(CA, P(a, "electrum_amulet"))
    b.add(CA, P(a, "small_light_connector"))
    b.add(CA, P(a, "biomass_pellet_block"), optional=True)
    b.add(CA, P(a, "cake_base", "cake_base_baked", "chocolate_cake", "honey_cake"), k="cakes", title="Торты из печи", mode="set",
          optional=True)

    # ---------- Mystical Agriculture ----------
    MA, m = "ma_farm", "mysticalagriculture"
    b.add(MA, P(m, "watering_can"), text="Простая лейка без ступени: поливает небольшую область и ускоряет рост.")
    b.add(MA, P(m, "diamond_sickle", "diamond_scythe"), k="diamond_harvest", title="Алмазный серп и коса", mode="set")
    b.add(MA, P(m, "air_agglomeratio", "water_agglomeratio", "fire_agglomeratio", "nature_agglomeratio", "dye_agglomeratio",
                "nether_agglomeratio", "coral_agglomeratio", "honey_agglomeratio", "prismarine_agglomeratio",
                "end_agglomeratio", "mystical_flower_agglomeratio"), k="agglomeratio_all", title="Все сгустки",
          text="Сгустки — сжатые эссенции. Из них делаются семена сложных ресурсов.")
    b.add(MA, P(m, "experience_droplet", "experience_capsule"), k="xp_ma", title="Капли и капсулы опыта", mode="set")
    b.add(MA, P(m, "soul_dust", "soulium_dust"), k="soul_dusts", title="Пыль душ", mode="set")
    b.add(MA, P(m, "blank_skull", "blank_record"), k="blanks", title="Пустые заготовки", mode="set")
    b.add(MA, P(m, "unattuned_augment"), text="Основа для расширений инструментов и брони Mystical Agriculture.")
    b.add(MA, P(m, "witherproof_block"), optional=True)
    tiers = ["inferium", "prudentium", "tertium", "imperium", "supremium", "awakened_supremium"]
    b.add(MA, P(m, *[f"{t}_block" for t in tiers]), k="ess_blocks", title="Блоки эссенций", mode="any", optional=True)
    b.add(MA, P(m, *[f"{t}_nugget" for t in tiers] + ["prosperity_nugget", "soulium_nugget"]), k="ma_nuggets",
          title="Кусочки металлов", optional=True)
    b.add(MA, P(m, *[f"{t}_ingot_block" for t in tiers]), k="ingot_blocks", title="Блоки слитков", optional=True)
