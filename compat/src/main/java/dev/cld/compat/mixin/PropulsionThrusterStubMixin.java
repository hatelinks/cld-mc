package dev.cld.compat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

// Gadgets & Gizmos 1.2.10 ставит @Redirect на вызов getMultiblockOxidizerThrustMultiplier(I)F
// внутри addThrusterDetails у турбины Create Propulsion. В Create Propulsion 1.1.3 такого метода нет,
// миксин не находит вызов, и мод createpropulsion падает при загрузке.
// Здесь добавляем сам метод, а вызов в addThrusterDetails вставляет CompatMixinPlugin.preApply.
@Pseudo
@Mixin(targets = "dev.propulsionteam.propulsionsimulated.content.thruster.thruster.ThrusterBlockEntity", remap = false, priority = 500)
public abstract class PropulsionThrusterStubMixin {

    // множитель тяги без окислителя, как будто бонуса нет
    private static float getMultiblockOxidizerThrustMultiplier(int width) {
        return 1.0f;
    }
}
