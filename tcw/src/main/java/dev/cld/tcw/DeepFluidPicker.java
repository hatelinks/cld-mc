package dev.cld.tcw;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;

// Глобальные жидкости глубокого мира: лава у дна, выше вода до уровня моря.
// Отдельный класс, чтобы AquiferMixin знал, что над морем тут всегда воздух.
public final class DeepFluidPicker implements Aquifer.FluidPicker {
    public final int limit, sea;
    private final Aquifer.FluidStatus lava, water;

    public DeepFluidPicker(int lavaLevel, int sea, BlockState fluid) {
        this.sea = sea;
        this.limit = Math.min(lavaLevel, sea);
        this.lava = new Aquifer.FluidStatus(lavaLevel, Blocks.LAVA.defaultBlockState());
        this.water = new Aquifer.FluidStatus(sea, fluid);
    }

    @Override
    public Aquifer.FluidStatus computeFluid(int x, int y, int z) {
        return y < limit ? lava : water;
    }
}
