package dev.cld.bombs;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.mehvahdjukaar.supplementaries.common.entities.BombEntity;
import net.mehvahdjukaar.supplementaries.common.network.ClientBoundParticlePacket;
import net.mehvahdjukaar.supplementaries.reg.ModDamageSources;
import net.mehvahdjukaar.supplementaries.reg.ModSounds;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Взрыв бомбы с уровнем: урон сразу, а кратер вырезается от центра наружу за несколько тиков.
public final class Blast {
    public static final TagKey<Block> IMMUNE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BombsMod.NS, "immune"));
    // прочность обсидиана: всё, что прочнее, не ломается никогда
    private static final float OBSIDIAN = 1200f;
    private static final List<Blast> ACTIVE = new ArrayList<>();
    private static final boolean COLONIES = ModList.get().isLoaded("minecolonies");

    // смещения шара, отсортированные по расстоянию: x,y,z по байту со сдвигом 64
    private static int[] offs = new int[0];
    private static int offsRadius = -1;

    private final ServerLevel level;
    private final Entity source;
    private final double cx, cy, cz;
    private final BlockPos center;
    private final double radius;
    private final int lvl;
    private final boolean breaks;
    private final Explosion dummy;
    private final int end;
    private final long seed;
    private final Long2BooleanOpenHashMap colonyChunks = new Long2BooleanOpenHashMap();
    private final List<ItemStack> drops = new ArrayList<>();
    private int index;

    private Blast(ServerLevel level, Entity source, double x, double y, double z, double radius, int lvl, boolean breaks) {
        this.level = level;
        this.source = source;
        this.cx = x;
        this.cy = y;
        this.cz = z;
        this.center = BlockPos.containing(x, y, z);
        this.radius = radius;
        this.lvl = lvl;
        this.breaks = breaks;
        this.dummy = new Explosion(level, source, x, y, z, (float) radius, false, Explosion.BlockInteraction.DESTROY);
        int r = Mth.ceil(radius + Cfg.d(Cfg.EDGE)) + 1;
        ensure(r);
        this.end = count(r);
        this.seed = level.getRandom().nextLong();
    }

    // Вызывается вместо взрыва Supplementaries.
    public static void explode(BombEntity bomb, ServerLevel level, double x, double y, double z, BombEntity.BombType type, boolean breaks) {
        int lvl = Levels.of(bomb);
        double radius = Levels.radius(type, lvl);
        Blast b = new Blast(level, bomb, x, y, z, radius, lvl, breaks);
        b.effects(type);
        b.hurtEntities(bomb);
        if (breaks) ACTIVE.add(b);
    }

    // недорезанные кратеры при выходе из мира не сохраняются
    public static void clear() {
        ACTIVE.clear();
    }

    public static void tickAll() {
        if (ACTIVE.isEmpty()) return;
        int budget = Cfg.BLOCKS_PER_TICK.get();
        for (int i = 0; i < ACTIVE.size() && budget > 0; ) {
            Blast b = ACTIVE.get(i);
            budget = b.step(budget);
            if (b.index >= b.end) {
                b.finish();
                ACTIVE.remove(i);
            } else {
                i++;
            }
        }
    }

    private void effects(BombEntity.BombType type) {
        Vec3 c = new Vec3(cx, cy, cz);
        // свои частицы Supplementaries ограничены радиусом 8, иначе их слишком много
        NetworkHelper.sendToAllClientPlayersTrackingEntity(source,
                new ClientBoundParticlePacket(c, ClientBoundParticlePacket.Kind.BOMB_EXPLOSION, Mth.ceil(Math.min(radius, 8) * 10)));
        if (radius > 6) {
            RandomSource rnd = level.getRandom();
            int n = Mth.ceil(radius / 3);
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * radius * 0.6;
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx + Math.cos(a) * d, cy + rnd.nextDouble() * radius * 0.3, cz + Math.sin(a) * d, 1, 0, 0, 0, 0);
            }
        }
        float vol = type.explosionVolume() * (float) Math.max(1, radius / 6);
        level.playSound(null, cx, cy, cz, (SoundEvent) ModSounds.BOMB_EXPLOSION.get(), SoundSource.BLOCKS, vol, type.explosionPitch(level.getRandom()));
        if (radius > 10) {
            level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, vol, 0.6f);
        }
    }

    private void hurtEntities(BombEntity bomb) {
        Entity owner = bomb.getOwner();
        DamageSource dmg = ModDamageSources.bombExplosion(level, bomb, owner);
        double r = radius, base = Levels.damage(lvl);
        Vec3 c = new Vec3(cx, cy, cz);
        for (Entity e : level.getEntities(bomb, new AABB(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r))) {
            if (e instanceof BombEntity || e.ignoreExplosion(dummy)) continue;
            double d = Math.sqrt(e.distanceToSqr(c)) / r;
            if (d > 1) continue;
            double impact = (1 - d) * Explosion.getSeenPercent(c, e);
            if (impact <= 0) continue;
            float amount = (float) (base * impact);
            // предметы на земле не сгорают от маленьких бомб
            if (!(e instanceof ItemEntity) || amount > 4) e.hurt(dmg, amount);
            Vec3 dir = e.position().subtract(c);
            if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, 1, 0);
            double push = impact * (1 + lvl * 0.25);
            if (e instanceof LivingEntity le) push *= 1 - le.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(dir.normalize().scale(push)));
            if (e instanceof ServerPlayer sp) sp.hurtMarked = true;
        }
    }

    private int step(int budget) {
        double edge = Cfg.d(Cfg.EDGE);
        double drop = Cfg.d(Cfg.DROP_CHANCE);
        boolean protect = COLONIES && Cfg.b(Cfg.PROTECT_COLONIES);
        boolean obsidian = Levels.breaksObsidian(lvl);
        // смещения считаются от блока центра, сам центр внутри блока
        double fx = cx - center.getX() - 0.5, fy = cy - center.getY() - 0.5, fz = cz - center.getZ() - 0.5;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (budget > 0 && index < end) {
            int p = offs[index++];
            budget--;
            int dx = ((p >> 16) & 0xFF) - 64, dy = ((p >> 8) & 0xFF) - 64, dz = (p & 0xFF) - 64;
            pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
            double ex = dx - fx, ey = dy - fy, ez = dz - fz;
            double d2 = ex * ex + ey * ey + ez * ez;
            double rr = radius + (noise(pos) * 2 - 1) * edge;
            if (rr <= 0 || d2 > rr * rr) continue;
            if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) continue;
            BlockState st = level.getBlockState(pos);
            if (st.isAir()) continue;
            if (!breakable(st, pos, obsidian)) continue;
            if (protect && colony(pos)) continue;
            FluidState fs = st.getFluidState();
            if (!fs.isEmpty() && st.getBlock() instanceof LiquidBlock) continue;
            BlockState repl = fs.isEmpty() ? Blocks.AIR.defaultBlockState() : fs.createLegacyBlock();
            if (drop > 0 && level.getRandom().nextDouble() < drop) {
                BlockEntity be = st.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                for (ItemStack s : Block.getDrops(st, level, pos, be, source, ItemStack.EMPTY)) add(s);
            }
            // край кратера обновляет соседей (вода затекает, песок падает), внутри без обновлений ради скорости
            boolean rim = d2 > (rr - 1.5) * (rr - 1.5);
            level.setBlock(pos, repl, rim ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (st.getBlock() instanceof TntBlock) st.getBlock().wasExploded(level, pos.immutable(), dummy);
        }
        return budget;
    }

    private boolean breakable(BlockState st, BlockPos pos, boolean obsidian) {
        if (st.is(IMMUNE) || st.getDestroySpeed(level, pos) < 0) return false;
        float res = st.getBlock().getExplosionResistance();
        if (res < OBSIDIAN) return true;
        return obsidian && res <= OBSIDIAN;
    }

    private boolean colony(BlockPos pos) {
        long key = ((long) (pos.getX() >> 4) << 32) ^ ((pos.getZ() >> 4) & 0xFFFFFFFFL);
        if (colonyChunks.containsKey(key)) return colonyChunks.get(key);
        boolean in = Colonies.inColony(level, pos);
        colonyChunks.put(key, in);
        return in;
    }

    // Добыча копится и выпадает одной кучей стопок в конце, а не тысячами отдельных предметов.
    private void add(ItemStack s) {
        if (s.isEmpty()) return;
        for (ItemStack d : drops) {
            if (d.getCount() < d.getMaxStackSize() && ItemStack.isSameItemSameComponents(d, s)) {
                int move = Math.min(s.getCount(), d.getMaxStackSize() - d.getCount());
                d.grow(move);
                s.shrink(move);
                if (s.isEmpty()) return;
            }
        }
        if (drops.size() < Cfg.i(Cfg.MAX_DROP_STACKS)) drops.add(s.copy());
    }

    private void finish() {
        RandomSource rnd = level.getRandom();
        for (ItemStack s : drops) {
            double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * radius * 0.5;
            ItemEntity ie = new ItemEntity(level, cx + Math.cos(a) * d, cy + 0.5, cz + Math.sin(a) * d, s);
            ie.setDefaultPickUpDelay();
            level.addFreshEntity(ie);
        }
        drops.clear();
    }

    private double noise(BlockPos p) {
        long h = seed ^ (p.getX() * 3129871L) ^ (p.getZ() * 116129781L) ^ (p.getY() * 42317861L);
        h = h * h * 42317861L + h * 11L;
        h ^= h >>> 29;
        return ((h >>> 16) & 0xFFFF) / 65535.0;
    }

    // Смещения всех блоков шара радиуса r, ближние первыми. Строится один раз на наибольший нужный радиус.
    private static void ensure(int r) {
        if (r <= offsRadius) return;
        r = Math.min(r, 63);
        long[] tmp = new long[(2 * r + 1) * (2 * r + 1) * (2 * r + 1)];
        int n = 0;
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 > r * r) continue;
                    tmp[n++] = ((long) d2 << 32) | ((x + 64) << 16) | ((y + 64) << 8) | (z + 64);
                }
        Arrays.sort(tmp, 0, n);
        int[] o = new int[n];
        for (int i = 0; i < n; i++) o[i] = (int) tmp[i];
        offs = o;
        offsRadius = r;
    }

    // сколько смещений лежит в шаре радиуса r
    private static int count(int r) {
        r = Math.min(r, offsRadius);
        long lim = (long) r * r;
        int lo = 0, hi = offs.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            int p = offs[mid];
            int dx = ((p >> 16) & 0xFF) - 64, dy = ((p >> 8) & 0xFF) - 64, dz = (p & 0xFF) - 64;
            if ((long) dx * dx + dy * dy + dz * dz <= lim) lo = mid + 1; else hi = mid;
        }
        return lo;
    }
}
