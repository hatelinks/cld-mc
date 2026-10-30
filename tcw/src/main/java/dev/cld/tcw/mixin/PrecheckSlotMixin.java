package dev.cld.tcw.mixin;

import dev.cld.tcw.PrecheckSlot;
import net.minecraft.world.level.levelgen.blockpredicates.StateTestingPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Условия дисков почти всегда и, или, не, или проверка блока: им добавляем поле под готовую проверку.
@Mixin(value = StateTestingPredicate.class, targets = {
        "net.minecraft.world.level.levelgen.blockpredicates.CombiningPredicate",
        "net.minecraft.world.level.levelgen.blockpredicates.NotPredicate"})
public abstract class PrecheckSlotMixin implements PrecheckSlot {
    @Unique private volatile Object tcw$pre;

    @Override
    public Object tcw$precheck() {
        return tcw$pre;
    }

    @Override
    public void tcw$setPrecheck(Object p) {
        tcw$pre = p;
    }
}
