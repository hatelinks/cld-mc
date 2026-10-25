# Сборка Minecraft 1.21.1 (NeoForge 21.1.248)

Рабочий репозиторий сборки «Create + MineColonies + Mystical Agriculture» для ПК (Prism) и Android (Amethyst).

- `HANDOFF.md` — контекст и текущие задачи.
- `quest_builder/` — генератор квестов FTB Quests: `python3 -I quest_builder/make.py` → `out/ftbquests_config.zip` + `out/checklist.md`.
- `jjtm/` — неофициальный порт датапака JJThunder To The Max (1.20.1) на 1.21.1 для личного использования (лицензия оригинала: All Rights Reserved). `jjtm/build.sh` пересобирает `jjtm/dist/` из оригинального jar в трёх вариантах высоты мира: h2096 (оригинал), h1024 и h512 (`tools/variant.py`: ниже Y 63 без изменений, выше рельеф сжат линейно); `vanilla-1.21.1/` — ванильные файлы для сверки (спавны биомов).
- `pregen/` — свой мод «Предпросмотр мира» (cld_pregen): кнопка «Предпросмотр» на экране создания мира, прегенерация с живой картой (блоки/биомы/высоты), сид, радиус, форма, слизни, структуры, скриншот, «Оставить мир»/«Назад». Сборка без Gradle: `LIBS=<папка с jar NeoForge 21.1.248 из Prism> pregen/build.sh` → `pregen/dist/`.
