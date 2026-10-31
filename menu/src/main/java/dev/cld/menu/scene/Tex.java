package dev.cld.menu.scene;

// Текстура из ресурсов игры в виде ARGB
public final class Tex {
    public final int w, h;
    public final int[] px;

    public Tex(int w, int h, int[] px) {
        this.w = w;
        this.h = h;
        this.px = px;
    }

    // откуда брать текстуры: в игре из ресурс-паков, в превью из jar
    public interface Source {
        Tex get(String path);
    }

    // кадр анимированной текстуры, кадры идут столбиком
    public Tex frame(int i) {
        int n = Math.max(1, h / w);
        int f = Math.floorMod(i, n);
        int[] p = new int[w * w];
        System.arraycopy(px, f * w * w, p, 0, w * w);
        return new Tex(w, w, p);
    }

    public Tex region(int x0, int y0, int rw, int rh) {
        int[] p = new int[rw * rh];
        for (int y = 0; y < rh; y++) System.arraycopy(px, (y0 + y) * w + x0, p, y * rw, rw);
        return new Tex(rw, rh, p);
    }
}
