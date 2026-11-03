"""Оформление в стиле aoa, запускается последним: группа «Небо и техника», эпохи Create, цвета глав,
приветствия «Добро пожаловать», формы квестов, случайные награды из таблиц и награды на выбор в финалах."""
import re

SKY = ["sim_physics", "aero_fleet", "gizmos", "submarine", "off_road"]

# эпохи Create: ключ, новое название, порядок, цвет (старое название уходит в подзаголовок)
ERAS = [("create_andesite", "Эпоха андезита", 1, "&7"), ("create_fluids", "Эпоха меди", 1.5, "&c"),
        ("create_brass", "Эпоха латуни", 2, "&e"), ("create_trains", "Эпоха поездов", 2.5, "&3")]

GROUP_COLOR = {"Эра механизмов": "&6", "Небо и техника": "&b", "Хозяйство и путешествия": "&2",
               "Мистическое земледелие": "&d", "Жизнь колонии": "&a", "": "&f"}
CH_COLOR = {"vanilla_progress": "&f", "create_logistics": "&6", "create_contraptions": "&3", "ca_electric": "&e",
            "create_enchant_ch": "&a", "fd_kitchen": "&6", "food_factory": "&6", "food_sweets": "&d", "market": "&e",
            "aero_fleet": "&b", "sim_physics": "&3", "gizmos": "&c", "submarine": "&9", "off_road": "&7"}

# приветствия для глав, где первый квест сразу предметный
WELCOME = {
    "create_brass": ["Латунь открывает точную механику: механизмы точности, механическую руку, чертежи и умные блоки.",
                     "Для латуни нужен смешиватель с подогревом от горелки всполоха."],
    "create_fluids": ["Медь это трубы, насосы, баки и пар. Здесь вы научитесь перекачивать жидкости и запускать паровой двигатель.",
                      "Медный корпус собирает трубы в аккуратные узлы."],
    "create_trains": ["Поезда Create ходят сами по расписанию между станциями. Нужны рельсы, тележки, железнодорожный корпус и станция.",
                      "Ponder на рельсах покажет, как строить повороты и сигналы."],
    "create_logistics": ["Логистика Create: склады, посылки, лягушки-отправщики и заказы через пульт.",
                         "Машины сами доставят нужное туда, где оно требуется."],
    "create_connected_ch": ["Create: Connected добавляет хитрые узлы передачи: муфты, переключатели, инверторы и связанные рычаги."],
    "create_enchant_ch": ["Create Enchantment Industry превращает опыт в жидкость: её можно качать, хранить и тратить на зачарование машинами."],
    "ec_compass": ["Компасы ищут биомы и постройки, обелиски помогают вернуться домой. Удобно перед дальним походом."],
    "ma_altars": ["Алтари Mystical Agriculture: инфузия делает семена и снаряжение, пробуждение усиливает их, души мобов открывают новые семена."],
    "ma_gear": ["Снаряжение из эссенций: от инфериума до супремия. Каждая ступень прочнее и даёт новые свойства."],
    "sb_backpacks": ["Рюкзаки Sophisticated Backpacks носят много вещей, а улучшения сами подбирают, сортируют и пополняют предметы.",
                     "&bB&r открывает рюкзак, который надет."],
    "ss_storage": ["Sophisticated Storage: сундуки и бочки с улучшениями. Их можно увеличивать, не выкладывая вещи."],
}

TABLE = {"create_andesite": "create_early", "vanilla_progress": "ingots", "tutorial": None, "to_know": None}
GROUP_TABLE = {"Эра механизмов": "create_mid", "Небо и техника": "sky", "Хозяйство и путешествия": "food",
               "Мистическое земледелие": "mystic", "Жизнь колонии": "colony"}
KEY_TABLE = {"act_explore": "explore", "act_hunt": "explore", "ec_compass": "explore", "ss_storage": "storage",
             "sd_drawers": "storage", "toms": "storage", "sb_backpacks": "explore", "market": "colony"}
CHOICE = {"Эра механизмов": "choice_tech", "Небо и техника": "choice_tech", "Хозяйство и путешествия": "choice_home",
          "Мистическое земледелие": "choice_mystic", "Жизнь колонии": "choice_colony", "": "choice_home"}

# крупная картинка слева от первого квеста главы, как тематические значки в aoa
DECO = {"create_andesite": "create:textures/item/andesite_alloy.png", "create_fluids": "create:textures/block/copper_casing.png",
        "create_brass": "create:textures/item/brass_ingot.png", "create_trains": "create:textures/item/schedule.png",
        "create_logistics": "create:textures/item/cardboard.png", "create_contraptions": "create:textures/item/super_glue.png",
        "create_misc": "create:textures/item/wrench.png", "create_inv": "create:textures/item/precision_mechanism.png",
        "food_factory": "createfood:textures/item/hamburger.png", "food_sweets": "create_confectionery:textures/item/full_chocolate_bar.png",
        "market": "stockmarket:textures/item/market_remote.png", "act_explore": "minecraft:textures/item/filled_map.png",
        "ec_compass": "minecraft:textures/item/compass_00.png", "vanilla_progress": "minecraft:textures/item/diamond_pickaxe.png",
        "fd_kitchen": "minecraft:textures/item/bread.png", "act_hunt": "minecraft:textures/item/bow.png",
        "act_farm": "minecraft:textures/item/wheat.png", "sb_backpacks": "minecraft:textures/item/bundle.png"}
LOGO = "cldquests:textures/quests/logo.png"

KEYS_RE = re.compile(r"(?<![&\w])(ПКМ|ЛКМ|Shift|Ctrl|Alt|Tab|Esc)(?!\w)")


def run(b):
    for k in SKY:
        if k in b.ch:
            b.ch[k]["group"] = "Небо и техника"

    for key, title, order, color in ERAS:
        if key not in b.ch:
            continue
        c = b.ch[key]
        if c["title"] != title:
            c["subtitle"] = c["title"] + ". " + c.get("subtitle", "")
            c["title"] = title
        c["order"] = order
        c["color"] = color

    for key, c in b.ch.items():
        c.setdefault("color", CH_COLOR.get(key, GROUP_COLOR.get(c["group"], "")))

    for key, t in DECO.items():
        if key in b.ch:
            b.ch[key]["deco"] = [[t, -2.6, 0, 1.8, 1.8]]
    # логотип над началом первой главы каждой эпохи
    if "create_andesite" in b.ch:
        b.ch["create_andesite"]["deco"].append([LOGO, -4.6, -2.8, 6.0, 0.86])

    # приветствия
    for key, lines in WELCOME.items():
        if key not in b.ch:
            continue
        c = b.ch[key]
        root = c["quests"][0]
        if root["tasks"][0]["type"] == "check":
            continue
        refs = {b.ref(c, q) for q in c["quests"]}
        outside = [d for d in root.get("deps", []) if b.norm(key, d) not in refs]
        w = {"k": "welcome", "title": "Добро пожаловать: " + re.sub(r"&.", "", c["title"]), "sub": c.get("subtitle", ""),
             "x": 0, "y": 0, "desc": lines, "icon": c.get("icon"), "optional": False,
             "tasks": [{"type": "check", "title": "Прочитать вступление"}], "rewards": [{"type": "xp", "amount": 10}],
             "deps": outside}
        c["quests"].insert(0, w)
        b.reindex()
        wr = f"{key}/welcome"
        for q in c["quests"][1:]:
            inner = [d for d in q.get("deps", []) if b.norm(key, d) in refs]
            if not inner:
                q["deps"] = [d for d in q.get("deps", []) if d not in outside] + [wr]
        b.reindex()

    # предметы, у которых в самом моде сломана модель (в игре розово-чёрная иконка): иконка квеста другая
    broken = {"create_things_and_misc:blaze_ballon": "minecraft:fire_charge"}
    for c in b.ch.values():
        for q in c["quests"]:
            t = q["tasks"][0] if q["tasks"] else {}
            if t.get("id") in broken and not q.get("icon"):
                q["icon"] = broken[t["id"]]

    # формы, цвета в текстах, награды
    for key, c in b.ch.items():
        refs = [b.ref(c, q) for q in c["quests"]]
        kids = {r: 0 for r in refs}
        for r in refs:
            for d in b.deps.get(r, []):
                if d in kids:
                    kids[d] += 1
        table = TABLE.get(key, KEY_TABLE.get(key, GROUP_TABLE.get(c["group"])))
        cnt = 0
        for q, r in zip(c["quests"], refs):
            first = q is c["quests"][0]
            if q.get("finale"):
                q["shape"], q["size"] = "hexagon", 2.0
                if not q["title"].startswith("&"):
                    q["title"] = "&6&l" + q["title"]
                if not any(x["type"] == "choice" for x in q["rewards"]):
                    q["rewards"].append({"type": "choice", "table": CHOICE.get(c["group"], "choice_home")})
            elif first and q["tasks"][0]["type"] == "check" and c.get("layout", "auto") == "auto":
                q["shape"], q["size"] = "square", 2.0
                if not q["title"].startswith("&"):
                    q["title"] = "&b" + q["title"]
            elif kids[r] >= 4 and not q.get("shape"):
                q["shape"], q["size"] = "gear", 1.5
            q["desc"] = [KEYS_RE.sub(r"&b\1&r", d) if not d.startswith("{image:") else d for d in q.get("desc", [])]
            if not table or q.get("optional") or q.get("finale") or first:
                continue
            if any(x["type"] in ("item", "random", "choice") for x in q["rewards"]):
                continue
            cnt += 1
            if cnt % 2 == 0:
                q["rewards"].append({"type": "random", "table": table})
            elif cnt % 6 == 3:
                q["rewards"].append({"type": "random", "table": "xp"})
