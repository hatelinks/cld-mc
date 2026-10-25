# Сборка Minecraft 1.21.1 (NeoForge 21.1.248)

Рабочий репозиторий сборки «Create + MineColonies + Mystical Agriculture» для ПК (Prism) и Android (Amethyst).

- `HANDOFF.md` — контекст и текущие задачи.
- `quest_builder/` — генератор квестов FTB Quests: `python3 -I quest_builder/make.py` → `out/ftbquests_config.zip` + `out/checklist.md`.
- `jjtm/` — неофициальный порт датапака JJThunder To The Max (1.20.1) на 1.21.1 для личного использования (лицензия оригинала: All Rights Reserved). `jjtm/build.sh` пересобирает `jjtm/dist/` из оригинального jar; `vanilla-1.21.1/` — ванильные файлы для сверки (спавны биомов).
