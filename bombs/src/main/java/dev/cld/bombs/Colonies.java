package dev.cld.bombs;

import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

// Отдельный класс, чтобы без MineColonies он не загружался.
final class Colonies {
    private Colonies() {}

    static boolean inColony(Level level, BlockPos pos) {
        try {
            return IColonyManager.getInstance().isCoordinateInAnyColony(level, pos);
        } catch (Throwable t) {
            return false;
        }
    }
}
