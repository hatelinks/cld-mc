package dev.cld.tcw;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.cld.tcw.gen.Caves;
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

    public ColumnDensity(Value value, NoiseHolder noise) {
        this.value = value;
        this.noise = noise;
        NormalNoise n = noise.noise();
        this.terrain = n == null ? null : Worlds.terrain(Worlds.seedFrom(n));
    }

    public Value value() { return value; }
    public NoiseHolder noise() { return noise; }

    public static final double SLOPE = 0.06;

    public enum Value implements StringRepresentable {
        SURFACE("surface"), CONTINENTS("continents"), EROSION("erosion"), RIDGES("ridges"),
        TEMPERATURE("temperature"), VEGETATION("vegetation"), DEPTH("depth"), CAVES("caves");
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
        };
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
        return value == Value.SURFACE ? -800 * SLOPE : value == Value.DEPTH ? -7 : value == Value.CAVES ? -3 : -2;
    }

    @Override
    public double maxValue() {
        return value == Value.SURFACE ? 800 * SLOPE : value == Value.DEPTH ? 7 : value == Value.CAVES ? Caves.SOLID : 2;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
