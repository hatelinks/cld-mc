#!/usr/bin/env python3
# Иконки для квестов без предмета: структуры, биомы, измерения, убийства и галочки.
# Без иконки FTB Quests рисует у структуры и биома одну и ту же бумажку, у галочки галочку,
# и глава вроде «Дальних горизонтов» выглядит как ряд одинаковых значков.

STRUCTURE = {
    "minecraft:village_plains": "minecraft:bell",
    "minecraft:mineshaft": "minecraft:rail",
    "minecraft:desert_pyramid": "minecraft:chiseled_sandstone",
    "minecraft:stronghold": "minecraft:ender_eye",
    "minecraft:monument": "minecraft:prismarine",
    "minecraft:mansion": "minecraft:totem_of_undying",
    "minecraft:ancient_city": "minecraft:echo_shard",
    "idas:castle": "minecraft:chiseled_stone_bricks",
    "idas:haunted_manor": "minecraft:cobweb",
    "idas:labyrinth": "minecraft:mossy_stone_bricks",
    "idas:pillager_fortress": "minecraft:crossbow",
    "idas:necromancers_spire": "minecraft:wither_skeleton_skull",
    "idas:abandoned_lighthouse": "minecraft:lantern",
    "idas:bazaar": "minecraft:emerald",
    "idas:tudor_pub": "minecraft:barrel",
    "idas:treetop_tavern": "minecraft:oak_sapling",
    "idas:frozen_crypt": "minecraft:blue_ice",
    "idas:ruins_of_the_deep": "minecraft:trident",
    "idas:tinkers_citadel": "minecraft:anvil",
    "idas:tree_of_wisdom": "minecraft:bookshelf",
    "idas:snifferhenge": "minecraft:sniffer_egg",
    "idas:ancient_mines": "minecraft:raw_iron",
    "idas:ruined_church": "minecraft:cracked_stone_bricks",
    "idas:collectors_museum": "minecraft:item_frame",
    "idas:redhorn_guild": "minecraft:goat_horn",
    "idas:pumpkin_cafe": "minecraft:pumpkin_pie",
    "idas:wacky_wares": "minecraft:brewing_stand",
    "repurposed_structures:city_nether": "minecraft:nether_bricks",
    "repurposed_structures:city_overworld": "minecraft:stone_bricks",
    "repurposed_structures:mansion_birch": "minecraft:birch_log",
    "repurposed_structures:mansion_desert": "minecraft:cut_sandstone",
    "repurposed_structures:ancient_city_ocean": "minecraft:sculk_catalyst",
    "repurposed_structures:fortress_jungle": "minecraft:mossy_cobblestone",
    "repurposed_structures:bastion_underground": "minecraft:gilded_blackstone",
    "repurposed_structures:pyramid_jungle": "minecraft:jungle_log",
    "repurposed_structures:monument_desert": "minecraft:smooth_sandstone",
    "repurposed_structures:stronghold_nether": "minecraft:end_portal_frame",
    "supplementaries:galleon": "minecraft:oak_boat",
    "farmers_structures:cooks": "farmersdelight:cooking_pot",
    "minecolonies:barbarian_camp": "minecraft:iron_axe",
    "integrated_villages:airship_village": "minecraft:feather",
    "integrated_villages:tavern_village": "minecraft:honey_bottle",
    "integrated_villages:pirate_village": "minecraft:spyglass",
    "integrated_villages:mediterranean_village": "minecraft:flower_pot",
    "integrated_villages:oasis_village": "minecraft:cactus",
    "integrated_villages:cabin_village": "minecraft:spruce_log",
    "integrated_villages:clockwork_village": "minecraft:clock",
    "integrated_villages:marketstead_village": "minecraft:emerald_block",
    "integrated_villages:mossy_mounds": "minecraft:moss_block",
    "integrated_villages:kutcha_village": "minecraft:mud_bricks",
    "integrated_villages:sunken_village": "minecraft:kelp",
    "integrated_stronghold:stronghold": "minecraft:ender_eye",
}

BIOME = {
    "minecraft:desert": "minecraft:dead_bush",
    "minecraft:forest": "minecraft:oak_sapling",
    "minecraft:taiga": "minecraft:snowball",
    "minecraft:swamp": "minecraft:lily_pad",
    "minecraft:lush_caves": "minecraft:glow_berries",
    "minecraft:nether_wastes": "minecraft:crimson_fungus",
}

DIMENSION = {
    "minecraft:overworld": "minecraft:grass_block",
    "minecraft:the_nether": "minecraft:netherrack",
    "minecraft:the_end": "minecraft:end_stone",
}

# галочки без предмета: ключ квеста в главе
CHECK = {
    "act_explore/struct_hdr_idas": "minecraft:mossy_stone_bricks",
    "act_explore/struct_hdr_rs": "minecraft:bricks",
    "act_explore/struct_hdr_cs": "create:cogwheel",
    "act_explore/fog_menu": "minecraft:white_stained_glass",
    "aero_fleet/climb_ropes": "minecraft:lead",
    "aero_fleet/ship_waystone": "minecraft:lodestone",
    "create_contraptions/": "create:super_glue",
    "create_fluids/": "minecraft:water_bucket",
    "create_fluids/sweet_fluids": "minecraft:honey_bottle",
    "create_inv/dragon_breath": "minecraft:dragon_breath",
    "create_trains/": "create:track",
    "create_enchant_ch/": "minecraft:enchanted_book",
    "ec_compass/": "minecraft:compass",
    "ma_altars/": "minecraft:amethyst_shard",
    "mc_taxes/check_tax": "minecraft:gold_nugget",
    "mc_taxes/tax_policy": "minecraft:writable_book",
    "mc_taxes/happiness": "minecraft:poppy",
    "mc_taxes/warchest": "minecraft:chest",
    "mc_taxes/guards_tax": "minecraft:iron_sword",
    "mc_taxes/war_info": "minecraft:white_banner",
    "supp/carry_on": "minecraft:chest",
    "toms/contraption_terminal": "create:minecart_contraption",
}


def pick(ch, q, known):
    # иконка для квеста без своей иконки или None (тогда FTB Quests берёт иконку задачи)
    tasks = q.get("tasks") or []
    if not tasks or q.get("icon") or q.get("icon_tex"):
        return None
    t = tasks[0]
    kind = t["type"]
    cand = []
    if kind == "structure":
        cand.append(STRUCTURE.get(t["id"]))
        cand.append(ch.get("icon"))
    elif kind == "biome":
        cand.append(BIOME.get(t["id"]))
        cand.append("minecraft:filled_map")
    elif kind == "dimension":
        cand.append(DIMENSION.get(tasks[-1]["id"]))
    elif kind == "kill":
        mob = t["id"]
        cand.append(mob + "_spawn_egg")
        cand.append("minecraft:iron_sword")
    elif kind == "check" and all(x["type"] == "check" for x in tasks):
        k = q.get("k") or ""
        if k == "finale":
            cand.append(ch.get("icon"))
        cand.append(CHECK.get(f'{ch["key"]}/{k}'))
        cand.append(CHECK.get(f'{ch["key"]}/'))
        cand.append(ch.get("icon"))
    for c in cand:
        if c and known(c):
            return c
    return None
