package dev.cld.structures;

import net.neoforged.fml.common.Mod;

@Mod("cld_structures")
public final class StructuresMod {
    public StructuresMod() {
        Overlap.loadConfig();
    }
}
