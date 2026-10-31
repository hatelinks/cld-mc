package dev.cld.bombs.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cld.bombs.Cfg;
import dev.cld.bombs.Levels;
import net.mehvahdjukaar.supplementaries.common.items.BombItem;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Предмет бомбы: уровень в названии, блеск с высокого уровня, уровень переходит в брошенную бомбу.
@Mixin(BombItem.class)
public abstract class BombItemMixin extends Item {
    private BombItemMixin(Properties p) {
        super(p);
    }

    @Override
    public Component getName(ItemStack stack) {
        Component base = super.getName(stack);
        if (!Cfg.b(Cfg.ENABLED)) return base;
        int l = Levels.of(stack);
        return Component.translatable("item.cld_bombs.leveled", base, l).withStyle(Levels.color(l));
    }

    @Inject(method = "isFoil", at = @At("HEAD"), cancellable = true)
    private void cld_bombs$foil(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Cfg.b(Cfg.ENABLED) && Levels.of(stack) >= Cfg.i(Cfg.GLINT_LEVEL)) cir.setReturnValue(true);
    }

    @WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean cld_bombs$throw(Level level, Entity bomb, Operation<Boolean> op, Level l, Player player, InteractionHand hand) {
        Levels.set(bomb, Levels.of(player.getItemInHand(hand)));
        return op.call(level, bomb);
    }

    @Inject(method = "asProjectile", at = @At("RETURN"))
    private void cld_bombs$dispense(Level level, Position pos, ItemStack stack, Direction dir, CallbackInfoReturnable<Projectile> cir) {
        if (cir.getReturnValue() != null) Levels.set(cir.getReturnValue(), Levels.of(stack));
    }
}
