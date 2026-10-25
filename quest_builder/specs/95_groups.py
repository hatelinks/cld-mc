"""Группы глав: Mystical Agriculture и MineColonies — отдельные группы."""


def run(b):
    for key, c in b.ch.items():
        if key.startswith("ma_"):
            c["group"] = "Мистическое земледелие"
        elif key.startswith("mc_"):
            c["group"] = "Жизнь колонии"
