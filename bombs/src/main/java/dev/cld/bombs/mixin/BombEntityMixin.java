package dev.cld.bombs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cld.bombs.Blast;
import dev.cld.bombs.Cfg;
import dev.cld.bombs.Levels;
import net.mehvahdjukaar.supplementaries.common.entities.BombEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Летящая бомба: свой взрыв вместо взрыва Supplementaries, осколки и подобранная бомба сохраняют уровень.
@Mixin(BombEntity.class)
public abstract class BombEntityMixin {
    @WrapOperation(method = "createExplosion", at = @At(value = "INVOKE",
            target = "Lnet/mehvahdjukaar/supplementaries/common/misc/explosion/BombExplosion;createExplosion(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;DDDLnet/mehvahdjukaar/supplementaries/common/entities/BombEntity$BombType;Z)Lnet/minecraft/world/level/Explosion;"))
    private Explosion cld_bombs$explode(Entity source, ServerLevel level, double x, double y, double z, BombEntity.BombType type, boolean breaks, Operation<Explosion> op) {
        if (!Cfg.b(Cfg.ENABLED)) return op.call(source, level, x, y, z, type, breaks);
        Blast.explode((BombEntity) (Object) this, level, x, y, z, type, breaks);
        return null;
    }

    @WrapOperation(method = "spawnCluster", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean cld_bombs$baby(ServerLevel level, Entity baby, Operation<Boolean> op) {
        Levels.set(baby, Levels.of((Entity) (Object) this));
        return op.call(level, baby);
    }

    @ModifyArg(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private ItemStack cld_bombs$pickup(ItemStack stack) {
        int l = Levels.of((Entity) (Object) this);
        if (l > 1) Levels.set(stack, l);
        return stack;
    }
}
