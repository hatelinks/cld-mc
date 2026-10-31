package dev.cld.menu;

import dev.cld.menu.scene.Ambient;
import dev.cld.menu.scene.Weather;
import dev.cld.menu.scene.WeatherKind;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

// Звуки главного меню: фон природы (синтез), гром рядом и вдалеке, капли дождя, приглушение музыки в непогоду.
// Громкость задаёт ползунок Звуки меню, общая громкость игры тоже действует.
public final class MenuSounds {
    private static final ResourceLocation AMBIENT_ID = ResourceLocation.fromNamespaceAndPath("cld_menu", "menu.ambient");
    private static final RandomSource RND = RandomSource.create();
    private static final Ambient AMBIENT = new Ambient(System.nanoTime());
    private static AmbientSound playing;
    private static int rainTicks;
    private static float music = 1f, musicTarget = 1f;

    private MenuSounds() {}

    // пейзаж меню на экране и мир не загружен
    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null && Landscape.shownRecently();
    }

    static float volume() {
        return Landscape.config().sounds;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        for (Weather.Strike s; (s = STRIKES.poll()) != null; ) thunder(s);
        boolean on = active() && volume() > 0;
        Weather wx = Landscape.currentWeather();
        if (on && wx != null && Landscape.currentSky() != null) {
            AMBIENT.target(wx, Landscape.currentSky());
            if (playing == null || playing.isStopped()) {
                playing = new AmbientSound();
                mc.getSoundManager().play(playing);
            }
            if (wx.rain > 0.15f && --rainTicks <= 0) {
                // настоящие капли дождя игры поверх синтеза
                rainTicks = 8 + RND.nextInt(10);
                play(SoundEvents.WEATHER_RAIN_ABOVE, 0.12f + wx.rain * 0.25f, 0.9f + RND.nextFloat() * 0.2f, 0);
            }
        }
        boolean storm = on && wx != null && (wx.kind == WeatherKind.THUNDER || wx.kind == WeatherKind.RAIN || wx.kind == WeatherKind.BLIZZARD);
        musicTarget = storm ? Landscape.config().musicInStorm : 1f;
        if (Math.abs(music - musicTarget) > 0.01f) {
            music += Math.signum(musicTarget - music) * 0.02f;
            if (Math.abs(music - musicTarget) < 0.02f) music = musicTarget;
            mc.getSoundManager().updateSourceVolume(SoundSource.MUSIC, mc.options.getSoundSourceVolume(SoundSource.MUSIC));
        }
    }

    // множитель громкости музыки, его применяет SoundEngineMixin
    public static float musicFactor() {
        return music;
    }

    // удар молнии: рядом гром громкий и сразу, вдалеке тихий, ниже и с задержкой по расстоянию
    // молния приходит из потока пейзажа, звук запускаем в тике игры
    private static final java.util.Queue<Weather.Strike> STRIKES = new java.util.concurrent.ConcurrentLinkedQueue<>();

    static void strike(Weather.Strike s) {
        STRIKES.add(s);
    }

    private static void thunder(Weather.Strike s) {
        if (!active() || volume() <= 0) return;
        if (s.near()) {
            play(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 0.9f + RND.nextFloat() * 0.2f, 0);
            play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 0.8f + RND.nextFloat() * 0.2f, (int) (s.dist * 10));
        } else {
            int delay = (int) ((1.5 + s.dist * 6) * 20);
            play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.25f + (1 - s.dist) * 0.35f, 0.5f + RND.nextFloat() * 0.15f, delay);
        }
    }

    private static void play(SoundEvent e, float vol, float pitch, int delayTicks) {
        SoundInstance si = new SimpleSoundInstance(e.getLocation(), SoundSource.MASTER, vol * volume(), pitch, RND,
                false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        if (delayTicks > 0) Minecraft.getInstance().getSoundManager().playDelayed(si, delayTicks);
        else Minecraft.getInstance().getSoundManager().play(si);
    }

    // бесконечный поток синтеза; звук в sounds.json только ссылка на файл игры, читается наш поток
    private static final class AmbientSound extends AbstractTickableSoundInstance {
        private float fade;

        AmbientSound() {
            super(SoundEvent.createVariableRangeEvent(AMBIENT_ID), SoundSource.MASTER, RND);
            looping = true;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.01f;
        }

        @Override
        public void tick() {
            boolean on = active() && volume() > 0;
            fade = Math.max(0, Math.min(1, fade + (on ? 0.05f : -0.1f)));
            volume = Math.max(0.0001f, fade * volume());
            if (!on && fade <= 0) stop();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, Sound sound, boolean looping) {
            return CompletableFuture.completedFuture(new Stream());
        }
    }

    private static final class Stream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(Ambient.RATE, 16, 1, true, false);
        private float[] buf = new float[0];

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer read(int size) {
            int samples = Math.max(1, size / 2);
            if (buf.length < samples) buf = new float[samples];
            AMBIENT.fill(buf, samples);
            ByteBuffer out = ByteBuffer.allocateDirect(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < samples; i++) out.putShort((short) Math.round(buf[i] * 32767));
            out.flip();
            return out;
        }

        @Override
        public void close() {}
    }
}
