package dev.cld.compat.mixin;

import java.util.List;
import java.util.Set;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Решает, какие правки нужны при этом наборе модов
public class CompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOG = LoggerFactory.getLogger("cld_compat");
    private static final String STUB = "getMultiblockOxidizerThrustMultiplier";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".PropulsionThrusterStubMixin")) {
            // нужна только пока Gadgets & Gizmos стоит вместе со старой Create Propulsion
            boolean gg = modLoaded("createthrusters");
            boolean cp = modLoaded("createpropulsion");
            boolean has = cp && hasMethod(targetClassName, STUB);
            boolean apply = gg && cp && !has;
            LOG.info("cld_compat: заглушка для Gadgets & Gizmos и Create Propulsion {} (gg={}, cp={}, метод уже есть={})",
                    apply ? "включена" : "не нужна", gg, cp, has);
            return apply;
        }
        if (mixinClassName.endsWith(".WntNoSleepMixin")) {
            boolean apply = modLoaded("minecolonytax");
            LOG.info("cld_compat: War 'n Taxes без 5 секунд сна при старте мира {}", apply ? "включено" : "не нужно");
            return apply;
        }
        return true;
    }

    private static boolean modLoaded(String id) {
        try {
            return LoadingModList.get().getModFileById(id) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasMethod(String className, String name) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(className);
            for (MethodNode m : node.methods) {
                if (m.name.equals(name)) return true;
            }
            return false;
        } catch (Throwable t) {
            // класс не прочитать, значит и чинить нечего
            LOG.warn("cld_compat: не удалось прочитать {}", className, t);
            return true;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!mixinClassName.endsWith(".PropulsionThrusterStubMixin")) return;
        // вставляем в начало addThrusterDetails вызов заглушки, его и перехватит Gadgets & Gizmos
        // (вызывается только для подсказки в очках инженера, результат выбрасываем)
        for (MethodNode m : targetClass.methods) {
            if (!m.name.equals("addThrusterDetails")) continue;
            InsnList call = new InsnList();
            call.add(new InsnNode(Opcodes.ICONST_1));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, targetClass.name, STUB, "(I)F", false));
            call.add(new InsnNode(Opcodes.POP));
            m.instructions.insert(call);
            m.maxStack = Math.max(m.maxStack, 1);
            LOG.info("cld_compat: вызов {} добавлен в {}.addThrusterDetails{}", STUB, targetClassName, m.desc);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
