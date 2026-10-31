package dev.cld.bombs;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

// Бомбы в сундуках (и в любой другой добыче) получают случайный уровень, по умолчанию 1-3.
public final class LootLevels extends LootModifier {
    public static final MapCodec<LootLevels> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, LootLevels::new));

    public LootLevels(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        if (!Cfg.b(Cfg.ENABLED)) return loot;
        int lo = Cfg.i(Cfg.LOOT_MIN), hi = Math.max(lo, Cfg.i(Cfg.LOOT_MAX));
        for (ItemStack s : loot) {
            if (!Levels.isBomb(s) || Levels.of(s) > 1) continue;
            int lvl = lo + ctx.getRandom().nextInt(hi - lo + 1);
            // у каждой бомбы в стопке один уровень, поэтому стопка получает один общий
            Levels.set(s, lvl);
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
