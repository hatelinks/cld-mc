package dev.cld.tcw;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.cld.tcw.gen.Caves;
import dev.cld.tcw.gen.Rocks;
import dev.cld.tcw.gen.Terrain;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Значения из нашего столбца рельефа: плотность поверхности и параметры климата для выбора биомов.
 * Сид мира берётся из шума noise: RandomState подставляет в него шум, созданный от сида.
 */
public final class ColumnDensity implements DensityFunction {
    private final Value value;
    private final NoiseHolder noise;
    private final Terrain terrain;
    private final Rocks.Columns cols;

    public ColumnDensity(Value value, NoiseHolder noise) {
        this.value = value;
        this.noise = noise;
        NormalNoise n = noise.noise();
        this.terrain = n == null ? null : Worlds.terrain(Worlds.seedFrom(n));
        Terrain t = terrain;
        this.cols = t == null ? null : (x, z) -> Worlds.column(t, x, z);
    }

    public Value value() { return value; }
    public Terrain terrain() { return terrain; }
    public NoiseHolder noise() { return noise; }

    public static final double SLOPE = 0.06;

    public enum Value implements StringRepresentable {
        SURFACE("surface"), CONTINENTS("continents"), EROSION("erosion"), RIDGES("ridges"),
        TEMPERATURE("temperature"), VEGETATION("vegetation"), DEPTH("depth"), CAVES("caves"), ROCK("rock"), PEAK("peak"), SKY("sky");
        public static final Codec<Value> CODEC = StringRepresentable.fromEnum(Value::values);
        private final String name;
        Value(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public static final KeyDispatchDataCodec<ColumnDensity> CODEC = KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec(i -> i.group(
            Value.CODEC.fieldOf("value").forGetter(ColumnDensity::value),
            NoiseHolder.CODEC.fieldOf("noise").forGetter(ColumnDensity::noise)
    ).apply(i, ColumnDensity::new)));

    @Override
    public double compute(FunctionContext ctx) {
        Terrain t = terrain;
        if (t == null) return 0;
        int x = ctx.blockX(), y = ctx.blockY(), z = ctx.blockZ();
        if (value == Value.ROCK) return t.rocks.combined(x, y, z, cols);
        if (value == Value.PEAK) return peak(t, x, y, z);
        if (value == Value.SKY) return skyTop(t, x, z) - y;
        Terrain.Column c = Worlds.column(t, x, z);
        return switch (value) {
            case SURFACE -> (c.height - y) * SLOPE;
            case CONTINENTS -> t.climate(c, Terrain.P_CONTINENTS, x, y, z);
            case EROSION -> t.climate(c, Terrain.P_EROSION, x, y, z);
            case RIDGES -> t.climate(c, Terrain.P_RIDGES, x, y, z);
            case TEMPERATURE -> t.climate(c, Terrain.P_TEMPERATURE, x, y, z);
            case VEGETATION -> t.climate(c, Terrain.P_VEGETATION, x, y, z);
            case DEPTH -> t.climate(c, Terrain.P_DEPTH, x, y, z);
            case CAVES -> t.caves.density(x, y, z, c);
            default -> 0;
        };
    }

    // верх тонкого пика (выше всех соседей вокруг) пещеры не режут, иначе от иглы остаются висящие куски.
    // Возвращает плотность поверхности там, где столбец защищён, и NO_PEAK в остальных местах
    public static final double NO_PEAK = -1000;
    static final int PEAK_R = 6;
    static final int[] RING = { PEAK_R, 0, -PEAK_R, 0, 0, PEAK_R, 0, -PEAK_R, 4, 4, 4, -4, -4, 4, -4, -4 };

    static double peak(Terrain t, int x, int y, int z) {
        Terrain.Column c = Worlds.column(t, x, z);
        double h = c.height;
        if (y >= h) return NO_PEAK;
        double base = c.peakBase;
        if (Double.isNaN(base)) {
            base = -Double.MAX_VALUE;
            // как только сосед не ниже h, защиты нет (y < h всегда <= base), остальных соседей не считаем
            for (int i = 0; i < RING.length && base < h; i += 2) base = Math.max(base, Worlds.column(t, x + RING[i], z + RING[i + 1]).height);
            // соседи могли вытеснить столбец из кэша, берём заново
            c = Worlds.column(t, x, z);
            c.peakBase = base;
        }
        return y > base ? (h - y) * SLOPE : NO_PEAK;
    }

    // верх, выше которого в узлах сетки шума можно не считать ванильные пещеры: самый высокий из 9 столбцов
    // вокруг (шаг 4, весь соседний узел ячейки) плюс SKY_MARGIN. Ниже значение положительное, выше отрицательное.
    // Ячейки, у которых все углы выше поверхности, остаются воздухом и с упрощённым значением
    public static final int SKY_MARGIN = 24;

    private record Sky(long key, double top) {}
    private volatile Sky sky;

    private double skyTop(Terrain t, int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Sky s = sky;
        if (s != null && s.key == key) return s.top;
        double m = -Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx += 4)
            for (int dz = -4; dz <= 4; dz += 4) m = Math.max(m, Worlds.column(t, x + dx, z + dz).height);
        sky = new Sky(key, m + SKY_MARGIN);
        return m + SKY_MARGIN;
    }

    @Override
    public void fillArray(double[] out, ContextProvider provider) {
        provider.fillAllDirectly(out, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new ColumnDensity(value, visitor.visitNoise(noise)));
    }

    @Override
    public double minValue() {
        return switch (value) {
            case SURFACE -> -800 * SLOPE;
            case ROCK -> -2.5;
            case PEAK -> NO_PEAK;
            case SKY -> -2000;
            case DEPTH -> -7;
            case CAVES -> -3;
            default -> -2;
        };
    }

    @Override
    public double maxValue() {
        return switch (value) {
            case SURFACE, PEAK -> 800 * SLOPE;
            case SKY -> 2000;
            case ROCK -> 2.5;
            case DEPTH -> 7;
            case CAVES -> Caves.SOLID;
            default -> 2;
        };
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
