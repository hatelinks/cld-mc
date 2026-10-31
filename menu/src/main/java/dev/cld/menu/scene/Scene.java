package dev.cld.menu.scene;

// Пиксельный пейзаж меню: небо, светила, облака, слои гор с параллаксом, погода.
// Рисует в Raster без Minecraft, поэтому то же самое собирается в превью вне игры.
public final class Scene {
    // слои гор от дальнего к ближнему, размеры в долях высоты экрана
    private static final float[] BASE = {0.50f, 0.585f, 0.68f, 0.81f};
    private static final float[] AMP = {0.30f, 0.17f, 0.12f, 0.07f};
    private static final float[] SPEED = {0.9f, 2.6f, 6f, 12f};
    private static final float[] FREQ = {0.008f, 0.013f, 0.02f, 0.032f};
    private static final float[] HAZE = {0.42f, 0.30f, 0.16f, 0.04f};

    private static final int DAY_TOP = 0x6F9EF2, DAY_LOW = 0xBCD6F5;
    private static final int NIGHT_TOP = 0x03050C, NIGHT_LOW = 0x121A30;
    private static final int GLOW_TOP = 0x3D2E66, GLOW_LOW = 0xF2894A;
    private static final int[] RAINBOW = {0xE84A3C, 0xF39A2E, 0xF3DD45, 0x5CC25A, 0x4A8FE8, 0x5B4FCC, 0x9452B8};
    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private int[] ground = new int[0];
    private Blocks blocks;
    private Tex sun, moon, cloudMap;

    // текстуры игры: блоки, солнце, луна, карта облаков; без них рисуются простые силуэты
    public void textures(Tex.Source src) {
        blocks = new Blocks(src);
        if (!blocks.ok) blocks = null;
        sun = env(src, "sun");
        moon = env(src, "moon_phases");
        cloudMap = env(src, "clouds");
    }

    private static Tex env(Tex.Source src, String name) {
        try {
            return src.get("minecraft:textures/environment/" + name + ".png");
        } catch (RuntimeException e) {
            return null;
        }
    }
    private float[] vignette = new float[0];
    private int vw, vh;

    public void render(Raster r, Sky sky, Weather wx, double t) {
        int w = r.w, h = r.h;
        float u = h / 270f; // пикселей растра на один пиксель эталонного экрана 480x270
        if (ground.length != w) ground = new int[w];
        float flash = wx.flash(t);
        float over = Col.smooth((wx.cloud - 0.35f) / 0.65f); // насколько небо затянуто
        float light = (0.3f + 0.7f * sky.day) * (1 - wx.dark * 0.45f);
        // снег ночью светлее, отражает луну
        if (sky.season == Season.WINTER) light = Math.max(light, 0.36f);
        light = Math.min(1.15f, light + flash * 0.55f);
        int lightCol = Col.scale(Col.mix(Col.mix(0x8C9CD0, 0xFFFFFF, sky.day), 0xFFC08A, sky.glow * 0.55f * (1 - over)), light);

        int top = Col.mix(NIGHT_TOP, DAY_TOP, sky.day);
        int low = Col.mix(NIGHT_LOW, DAY_LOW, sky.day);
        top = Col.mix(top, GLOW_TOP, sky.glow * 0.45f * (1 - over));
        low = Col.mix(low, GLOW_LOW, sky.glow * 0.8f * (1 - over * 0.8f));
        int cloudy = Col.scale(Col.grey(low, 1f), 0.95f - wx.dark * 0.55f);
        if (wx.kind.snowy()) cloudy = Col.mix(cloudy, Col.scale(0xC8D0DA, 0.25f + 0.75f * sky.day), 0.6f);
        top = Col.mix(top, Col.scale(cloudy, 0.8f), over * 0.85f);
        low = Col.mix(low, cloudy, over * 0.8f);
        low = Col.mix(low, Col.scale(0xC4CCD4, 0.2f + 0.8f * sky.day), wx.fog * 0.35f);
        if (flash > 0) {
            top = Col.mix(top, 0xE8ECFF, flash * 0.45f);
            low = Col.mix(low, 0xE8ECFF, flash * 0.45f);
        }
        int horizon = (int) (h * 0.66f);

        skyGradient(r, top, low, horizon);
        stars(r, sky, wx, t, u, horizon);
        sunAndMoon(r, sky, wx, u, horizon);
        if (wx.rainbow > 0) rainbow(r, sky, wx, u, horizon);
        clouds(r, sky, wx, t, u, low, light, horizon);
        for (Weather.Strike s : wx.strikes) if (!s.near()) farGlow(r, s, t, u, horizon);

        for (int k = 0; k < 4; k++) {
            if (k >= 2 && blocks != null) blockLayer(r, sky, wx, t, u, k, low, lightCol);
            else hills(r, sky, wx, t, u, k, low, lightCol, flash);
            if (k == 1) {
                fogBand(r, wx, t, u, k, low);
                precip(r, wx, t, u, false);
            }
            if (k == 2) {
                fogBand(r, wx, t, u, k, low);
                for (Weather.Strike s : wx.strikes) if (s.near()) bolt(r, s, t, u);
            }
        }
        precip(r, wx, t, u, true);
        if (wx.fog > 0.2f) r.blend(0, 0, w, h, low, (int) ((wx.fog - 0.2f) * 70));
        if (flash > 0.05f) r.blend(0, 0, w, h, 0xF0F4FF, (int) (flash * 60));
        vignette(r);
    }

    // небо

    // градиент полосами с узором Байера, как в старых играх
    private static void skyGradient(Raster r, int top, int low, int horizon) {
        int bands = 28;
        for (int y = 0; y < r.h; y++) {
            float f = Math.min(1f, y / (float) horizon);
            float p = f * f * 0.35f + f * 0.65f;
            float b = p * bands;
            int i = (int) b;
            float frac = b - i;
            int c0 = Col.mix(top, low, i / (float) bands), c1 = Col.mix(top, low, Math.min(bands, i + 1) / (float) bands);
            int o = y * r.w, row = (y & 3) << 2;
            for (int x = 0; x < r.w; x++) r.px[o + x] = frac * 16 > BAYER[row | (x & 3)] ? c1 : c0;
        }
    }

    private static void stars(Raster r, Sky sky, Weather wx, double t, float u, int horizon) {
        float vis = (1 - sky.day * 1.25f) * (1 - wx.cloud * 0.9f) * (1 - wx.fog * 0.6f);
        if (vis <= 0.02f) return;
        int n = (int) (130 * r.w * r.h / (480f * 270f * u * u));
        for (int i = 0; i < n; i++) {
            int x = (int) (Noise.hash(i, 101) * r.w), y = (int) (Noise.hash(i, 202) * horizon * 0.95);
            float tw = (float) (0.55 + 0.45 * Math.sin(t * (1.2 + Noise.hash(i, 303) * 2.5) + i));
            float fade = 1 - y / (float) horizon * 0.6f;
            int a = (int) (Col.clamp(vis, 0, 1) * tw * fade * 255);
            double kind = Noise.hash(i, 404);
            int c = kind < 0.08 ? 0xFFD9A8 : kind < 0.16 ? 0xB8CCFF : 0xFFFFFF;
            int s = kind > 0.9 ? Math.max(1, Math.round(u)) : 1;
            if (s > 1) r.blend(x, y, x + s, y + s, c, a);
            else r.dot(x, y, c, a);
        }
        // падающая звезда раз в несколько секунд
        long slot = (long) Math.floor(t / 11);
        if (vis > 0.5f && Noise.hash(slot, 505) < 0.45) {
            double dt = t - slot * 11 - Noise.hash(slot, 506) * 9;
            if (dt >= 0 && dt < 0.9) {
                float x0 = (float) (Noise.hash(slot, 507) * r.w * 0.8 + r.w * 0.1);
                float y0 = (float) (Noise.hash(slot, 508) * horizon * 0.4);
                float len = 60 * u, p = (float) (dt / 0.9);
                for (int i = 0; i < 14 * u; i++) {
                    float q = p - i / (14 * u) * 0.25f;
                    if (q < 0) break;
                    int a = (int) (vis * (1 - i / (14 * u)) * (1 - p) * 255);
                    r.dot((int) (x0 + q * len), (int) (y0 + q * len * 0.45f), 0xFFFFFF, a);
                }
            }
        }
    }

    // квадратные солнце и луна, как в Minecraft; у луны настоящая фаза
    private void sunAndMoon(Raster r, Sky sky, Weather wx, float u, int horizon) {
        float vis = 1 - Col.smooth((wx.cloud - 0.5f) / 0.5f) * 0.92f;
        vis *= 1 - wx.fog * 0.5f;
        if (vis < 0.03f) return;
        if (sky.sunQ > -0.12 && sky.sunQ < 1.12) {
            int s = Math.round(11 * u * (1 + sky.glow * 0.25f));
            int x = arcX(r, sky.sunQ) - s / 2, y = arcY(r, sky.sunQ, horizon) - s / 2;
            int core = Col.mix(0xFFF4B8, 0xFFB15A, sky.glow);
            int a = (int) (vis * 255);
            int g1 = s / 2, g2 = s;
            r.blend(x - g2, y - g2, x + s + g2, y + s + g2, core, a / 12);
            r.blend(x - g1, y - g1, x + s + g1, y + s + g1, core, a / 6);
            if (sun != null) {
                additive(r, sun, x - s / 2, y - s / 2, s * 2, vis);
            } else {
                r.blend(x, y, x + s, y + s, core, a);
                int in = Math.max(1, s / 4);
                r.blend(x + in, y + in, x + s - in, y + s - in, 0xFFFFF0, a);
            }
        }
        if (sky.moonQ > -0.1 && sky.moonQ < 1.1 && (sky.sunQ < 0 || sky.sunQ > 1)) {
            int s = Math.round(9 * u);
            int x = arcX(r, sky.moonQ) - s / 2, y = arcY(r, sky.moonQ, horizon) - s / 2;
            int a = (int) (vis * 255);
            r.blend(x - s / 2, y - s / 2, x + s + s / 2, y + s + s / 2, 0xC8D4F0, a / 14);
            double p = sky.moonPhase;
            if (moon != null) {
                // в Minecraft 0 это полнолуние, 4 новолуние
                int ph = ((int) Math.round(p * 8) + 4) % 8;
                int fw = moon.w / 4, fh = moon.h / 2;
                additive(r, moon.region((ph % 4) * fw, (ph / 4) * fh, fw, fh), x - s / 2, y - s / 2, s * 2, vis);
                return;
            }
            for (int i = 0; i < s; i++) {
                float xn = (i + 0.5f) / s;
                boolean lit = p < 0.5 ? xn > 1 - p * 2 : xn < 2 - p * 2;
                for (int j = 0; j < s; j++) {
                    boolean crater = Noise.hash(i / Math.max(1, Math.round(u)) * 7 + j / Math.max(1, Math.round(u)), 77) < 0.18;
                    int c = lit ? (crater ? 0xBFC6D4 : 0xE8ECF4) : 0x23283A;
                    r.dot(x + i, y + j, c, lit ? a : a / 2);
                }
            }
        }
    }

    // солнце и луна в игре складываются со светом неба, чёрный фон текстуры не виден
    private static void additive(Raster r, Tex t, int sx, int sy, int size, float k) {
        for (int y = Math.max(0, sy); y < Math.min(r.h, sy + size); y++) {
            int ty = (y - sy) * t.h / size;
            for (int x = Math.max(0, sx); x < Math.min(r.w, sx + size); x++) {
                int c = t.px[ty * t.w + (x - sx) * t.w / size];
                if ((c >>> 24) == 0) continue;
                int d = r.px[y * r.w + x];
                int rr = Math.min(255, (d >> 16 & 255) + (int) ((c >> 16 & 255) * k));
                int gg = Math.min(255, (d >> 8 & 255) + (int) ((c >> 8 & 255) * k));
                int bb = Math.min(255, (d & 255) + (int) ((c & 255) * k));
                r.px[y * r.w + x] = rr << 16 | gg << 8 | bb;
            }
        }
    }

    private static int arcX(Raster r, double q) {
        return (int) (r.w * (0.1 + 0.8 * q));
    }

    private static int arcY(Raster r, double q, int horizon) {
        return (int) (horizon * 0.92 - Math.sin(Math.PI * Math.max(-0.2, Math.min(1.2, q))) * horizon * 0.72);
    }

    // радуга напротив солнца
    private static void rainbow(Raster r, Sky sky, Weather wx, float u, int horizon) {
        double q = sky.sunQ >= 0 && sky.sunQ <= 1 ? sky.sunQ : 0.3;
        float cx = (float) (r.w * (0.9 - 0.8 * q));
        cx = Math.max(r.w * 0.25f, Math.min(r.w * 0.75f, cx));
        float cy = horizon + r.h * 0.12f;
        float rad = r.h * 0.62f;
        float bw = Math.max(1, 2.2f * u);
        float vis = wx.rainbow * (0.12f + 0.3f * sky.day) * (1 - wx.fog * 0.4f);
        int y0 = Math.max(0, (int) (cy - rad - 1));
        for (int y = y0; y < horizon; y++) {
            float dy = y - cy;
            float fadeY = Col.smooth((horizon - y) / (r.h * 0.25f));
            for (int x = (int) (cx - rad - 1); x <= cx + rad + 1; x++) {
                if (x < 0 || x >= r.w) continue;
                float dx = x - cx;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float band = (rad - d) / bw;
                if (band < 0 || band >= RAINBOW.length) continue;
                r.dot(x, y, RAINBOW[(int) band], (int) (vis * fadeY * 255));
            }
        }
    }

    // облака как в Minecraft: плоские прямоугольные, ряды ближе к горизонту мельче и медленнее
    private void clouds(Raster r, Sky sky, Weather wx, double t, float u, int low, float light, int horizon) {
        float cover = 0.22f + wx.cloud * 0.6f;
        int base = Col.mix(0xF6F8FA, 0x5E636B, wx.dark);
        if (wx.kind.snowy()) base = Col.mix(base, 0xD8DEE6, 0.5f);
        base = Col.scale(base, Math.max(0.16f, light));
        base = Col.mix(base, low, 0.12f);
        int under = Col.mix(Col.scale(base, 0.8f), 0xF29A6A, sky.glow * 0.5f * (1 - wx.dark));
        int a = (int) (215 + wx.cloud * 40);
        int rows = 5;
        for (int i = 0; i < rows; i++) {
            float depth = i / (float) (rows - 1);
            float scale = 1f / (1 + depth * 2.2f);
            int y0 = (int) (horizon * (0.04f + 0.6f * (float) Math.pow(depth, 0.75)));
            int th = Math.max(1, Math.round(8 * u * scale));
            int cw = Math.max(2, Math.round(12 * u * scale));
            double scroll = t * (2.5 + wx.wind * 9) * u * scale + i * 977;
            long c0 = (long) Math.floor(scroll / cw);
            int off = (int) (scroll - c0 * (double) cw);
            int ca = (int) (a * (1 - depth * 0.3f));
            int cc = Col.mix(base, low, depth * 0.5f), cu = Col.mix(under, low, depth * 0.5f);
            int top = Col.mix(Col.scale(cc, 1.06f), 0xFFFFFF, 0.1f * sky.day * (1 - wx.dark));
            for (int j = -1; j <= r.w / cw + 1; j++) {
                long cell = c0 + j;
                // облако это отдельные кучки клеток, а не сплошная полоса
                double n = Noise.noise(cell * 0.18, 600 + i) * 0.75 + Noise.noise(cell * 0.6, 650 + i) * 0.25;
                if (cloudMap != null) {
                    // настоящая карта облаков Minecraft, в пасмурную погоду берём несколько строк сразу
                    if (!cloudAt(cell, i * 37 + 11, (int) (wx.cloud * wx.cloud * 7))) continue;
                    n = 1 - cover * (Noise.hash(cell, 640 + i) < 0.4 ? 0.2 : 0.9);
                } else if (n < 1 - cover) continue;
                int x = j * cw - off;
                double core = (n - (1 - cover)) / Math.max(0.05, cover);
                int extra = core > 0.35 ? th / 2 : 0;
                int y = y0 + (int) ((Noise.noise(cell * 0.07, 690 + i) - 0.5) * 10 * u * scale);
                r.blend(x, y - extra, x + cw, y - extra + Math.max(1, th / 4), top, ca);
                r.blend(x, y - extra + Math.max(1, th / 4), x + cw, y + th, cc, ca);
                r.blend(x, y + th, x + cw, y + th + Math.max(1, th / 3), cu, ca);
            }
        }
    }

    private boolean cloudAt(long cell, int row, int extra) {
        int x = (int) Math.floorMod(cell, (long) cloudMap.w);
        for (int k = 0; k <= extra; k++) {
            if ((cloudMap.px[((row + k) % cloudMap.h) * cloudMap.w + x] >>> 24) > 0) return true;
        }
        return false;
    }

    // далёкая молния: подсветка облаков у горизонта за горами
    private static void farGlow(Raster r, Weather.Strike s, double t, float u, int horizon) {
        float f = s.flash(t) * 2.2f;
        if (f <= 0.02f) return;
        int cx = (int) (s.x * r.w), cy = (int) (horizon * (0.55f + s.dist * 0.25f));
        int rx = (int) (70 * u), ry = (int) (40 * u);
        for (int y = cy - ry; y < cy + ry; y++) {
            for (int x = cx - rx; x < cx + rx; x++) {
                float dx = (x - cx) / (float) rx, dy = (y - cy) / (float) ry;
                float d = dx * dx + dy * dy;
                if (d < 1) r.dot(x, y, 0xD8DEFF, (int) (f * (1 - d) * 120));
            }
        }
    }

    // горы

    private int height(Raster r, double wx, double t, int k, float u) {
        double f = FREQ[k] / u;
        double n;
        if (k == 0) n = Noise.ridge(wx * f, 11) * 0.75 + Noise.fbm(wx * f * 0.4, 13) * 0.25;
        else n = Noise.fbm(wx * f, k * 17) * 0.8 + Noise.fbm(wx * f * 0.35 + t * 0.003 * k, k * 17 + 9) * 0.2;
        int step = Math.max(1, Math.round(u));
        int y = (int) (r.h * BASE[k] - n * r.h * AMP[k]);
        return y - Math.floorMod(y, step);
    }

    private void hills(Raster r, Sky sky, Weather wx, double t, float u, int k, int low, int lightCol, float flash) {
        Season se = sky.season;
        boolean winter = se == Season.WINTER;
        int col = palette(se, k);
        float haze = Math.min(0.92f, HAZE[k] + wx.fog * (0.32f - k * 0.06f));
        double scroll = t * SPEED[k] * u + k * 5000;
        int step = Math.max(1, Math.round(u));
        int capCol = shadeLit(winter ? 0xEEF3F8 : 0xD8E0EA, lightCol, low, haze);
        int snowLine = (int) (r.h * (winter ? 0.62f : 0.43f));
        for (int x = 0; x < r.w; x++) {
            double wxp = x + scroll;
            int y = height(r, wxp, t, k, u);
            int c = col;
            int rim = rimColour(se, k, c);
            if (k == 0 && y < snowLine) rim = winter ? 0xF4F8FC : 0xE6ECF2;
            int body = shadeLit(c, lightCol, low, haze);
            int top = shadeLit(rim, lightCol, low, haze);
            int deep = shadeLit(Col.scale(c, 0.86f), lightCol, low, haze);
            int y0 = Math.max(0, y), rimH = k == 0 ? (int) (3 * u) : Math.max(1, Math.round((k == 3 ? 3 : 2) * u));
            int deepAt = (int) ((k == 0 ? 30 : 14) * u);
            for (int yy = y0; yy < r.h; yy++) {
                int d = yy - y;
                // снежная шапка дальних гор по строкам, а не по столбцам
                r.px[yy * r.w + x] = d < rimH ? top : k == 0 && yy < snowLine - (int) (Noise.hash(x / step, 31) * 3 * u) ? capCol : d > deepAt ? deep : body;
            }
            if (k == 3) ground[x] = y;
        }
        if (k >= 1) trees(r, sky, wx, t, u, k, scroll, low, lightCol, haze);
        if (k == 3 && (se == Season.SPRING || se == Season.SUMMER)) flowers(r, u, scroll, lightCol, low, haze);
    }

    // ближние слои из блоков: столбцы травы, земли и камня, вода в низинах, деревья и цветы
    private void blockLayer(Raster r, Sky sky, Weather wx, double t, float u, int k, int low, int lightCol) {
        Season se = sky.season;
        boolean winter = se == Season.WINTER;
        float haze = Math.min(0.92f, HAZE[k] + wx.fog * (0.32f - k * 0.06f));
        blocks.frame(lightCol, low, haze);
        int bs = Math.max(4, Math.round((k == 3 ? 16 : 8) * u));
        double scroll = t * SPEED[k] * u + k * 5000;
        int sea = (int) Math.round(r.h * (1 - BASE[k]) / bs) - (k == 3 ? 2 : 3);
        long b0 = (long) Math.floor(scroll / bs) - 3, b1 = (long) Math.floor((scroll + r.w) / bs) + 3;
        int grass = grassTint(se), water = winter ? 0 : (int) (t * 8);
        float rain = Math.max(wx.rain, wx.rainbow * 0.5f);
        int flowFrame = (int) (t * 6);
        for (long bx = b0; bx <= b1; bx++) {
            int sx = (int) Math.round(bx * bs - scroll);
            int hb = blockHeight(r, bx, t, k, u, bs);
            boolean beach = hb <= sea + (k == 3 ? 0 : 1) && !winter || hb < sea;
            for (int rb = 0; rb < hb; rb++) {
                int sy = r.h - (rb + 1) * bs;
                if (sy >= r.h || sy + bs <= 0) continue;
                int d = hb - 1 - rb;
                String name;
                int tint = 0xFFFFFF;
                if (d == 0) {
                    if (beach) name = "sand";
                    else if (winter) name = Noise.hash(bx, 1350 + k) < 0.55 ? "snow" : "grass_block_snow";
                    else {
                        name = "grass_block_side";
                        tint = grass;
                    }
                } else if (d <= 2) name = beach ? (d == 1 ? "sand" : "sandstone") : "dirt";
                else name = Noise.hash(bx * 131 + rb, 1300 + k) < 0.07 && blocks.tex("coal_ore") != null ? "coal_ore" : "stone";
                Blocks.draw(r, blocks.shade(name, tint, 0, Math.min(d, 4) * 0.07f), sx, sy, bs, 255);
            }
            int top = r.h - hb * bs;
            for (int rb = hb; rb < sea; rb++) {
                int sy = r.h - (rb + 1) * bs;
                boolean surface = rb == sea - 1;
                if (winter && surface) Blocks.draw(r, blocks.shade("ice", 0xFFFFFF, 0, 0), sx, sy, bs, 220);
                else Blocks.draw(r, blocks.shade("water_still", 0x3F76E4, water, 0), sx, sy, bs, 200);
                if (surface) top = sy;
            }
            if (k == 3) for (int x = Math.max(0, sx); x < Math.min(r.w, sx + bs); x++) ground[x] = top;
            if (hb < sea) continue;
            if (winter) {
                // снег лежит слоем поверх блоков, местами сугробы
                int layer = Noise.hash(bx, 1360 + k) < 0.3 ? 6 : 3;
                Blocks.drawTop(r, blocks.shade("snow", 0xFFFFFF, 0, 0), sx, top - bs * layer / 16, bs, layer);
                continue;
            }
            if (beach) continue;
            double p = Noise.hash(bx, 1400 + k);
            if (p < 0.38 && !treeAt(bx, k)) {
                String plant = p < 0.04 ? "poppy" : p < 0.08 ? "dandelion" : p < 0.1 && se != Season.AUTUMN ? "cornflower" : "short_grass";
                if (se == Season.AUTUMN && !plant.equals("short_grass") && p > 0.06) plant = "short_grass";
                int[] tx = blocks.shade(plant, plant.equals("short_grass") ? grass : 0xFFFFFF, 0, 0);
                if (tx == null) tx = blocks.shade("short_grass", grass, 0, 0);
                // трава качается под ветром: сдвиг на пиксель
                int sway = (int) Math.round(Math.sin(t * 2.2 + bx) * wx.wind * bs / 16.0);
                Blocks.draw(r, tx, sx + sway, top - bs, bs, 255);
            }
        }
        // деревья поверх, чтобы листва перекрывала соседние столбцы
        for (long bx = b0; bx <= b1; bx++) {
            if (!treeAt(bx, k)) continue;
            int hb = blockHeight(r, bx, t, k, u, bs);
            if (hb <= sea) continue;
            int sx = (int) Math.round(bx * bs - scroll);
            tree(r, se, bx, k, sx, r.h - hb * bs, bs);
        }
        if (rain > 0.3f && k == 3) puddleShine(r, wx, t, u);
    }

    private boolean treeAt(long bx, int k) {
        double dens = k == 3 ? 0.07 : 0.2;
        if (Noise.hash(bx, 1500 + k) >= dens) return false;
        // не два дерева подряд
        return Noise.hash(bx - 1, 1500 + k) >= dens && Noise.hash(bx - 2, 1500 + k) >= dens;
    }

    private int blockHeight(Raster r, long bx, double t, int k, float u, int bs) {
        double f = FREQ[k] / u * bs;
        double n = Noise.fbm(bx * f * 0.9, k * 17 + 3);
        return (int) Math.round((r.h * (1 - BASE[k]) + (n - 0.45) * r.h * AMP[k] * 1.4) / bs);
    }

    private static int grassTint(Season se) {
        return switch (se) {
            case SPRING -> 0x79C05A;
            case SUMMER -> 0x91BD59;
            case AUTUMN -> 0xB5B550;
            case WINTER -> 0x80B497;
        };
    }

    private void tree(Raster r, Season se, long bx, int k, int sx, int ground, int bs) {
        double kind = Noise.hash(bx, 1600 + k);
        String log, leaves;
        int tint, trunk;
        int[][] crown;
        boolean cherry = se == Season.SPRING && kind > 0.75 && blocks.tex("cherry_leaves") != null;
        if (cherry) {
            log = "cherry_log"; leaves = "cherry_leaves"; tint = 0xFFFFFF; trunk = 4;
            crown = new int[][] {{-2, 2, 3}, {-2, 2, 4}, {-1, 1, 5}};
        } else if (kind < 0.35 || se == Season.WINTER && kind < 0.6) {
            log = "spruce_log"; leaves = "spruce_leaves"; tint = 0x619961; trunk = 6;
            crown = new int[][] {{-2, 2, 2}, {-1, 1, 3}, {-2, 2, 4}, {-1, 1, 5}, {0, 0, 6}, {0, 0, 7}};
        } else if (kind < 0.6) {
            log = "birch_log"; leaves = "birch_leaves"; tint = se == Season.AUTUMN ? 0xE8C33A : 0x80A755; trunk = 5;
            crown = new int[][] {{-2, 2, 3}, {-2, 2, 4}, {-1, 1, 5}, {0, 0, 6}};
        } else {
            log = "oak_log"; leaves = "oak_leaves"; trunk = 4;
            tint = se == Season.AUTUMN ? new int[] {0xE0822B, 0xD9A23A, 0xC4452A, 0x9AAF3A}[(int) (Noise.hash(bx, 1700) * 4)]
                    : se == Season.SPRING ? 0x6BBF3A : 0x59AE30;
            crown = new int[][] {{-2, 2, 2}, {-2, 2, 3}, {-1, 1, 4}, {-1, 1, 5}};
        }
        int[] wood = blocks.shade(log, 0xFFFFFF, 0, 0);
        for (int i = 0; i < trunk; i++) Blocks.draw(r, wood, sx, ground - (i + 1) * bs, bs, 255);
        int[] leaf = blocks.shade(leaves, tint, 0, 0), leafDark = blocks.shade(leaves, tint, 0, 0.18f);
        int[] snow = se == Season.WINTER ? blocks.shade("snow", 0xFFFFFF, 0, 0) : null;
        int topRow = 0;
        for (int[] row : crown) topRow = Math.max(topRow, row[2]);
        for (int[] row : crown) {
            int y = ground - (row[2] + 1) * bs;
            for (int dx = row[0]; dx <= row[1]; dx++) {
                // углы кроны срезаны случайно, как у дубов в игре
                if ((dx == row[0] || dx == row[1]) && Math.abs(dx) == 2 && Noise.hash(bx * 7 + row[2] * 3 + dx, 1800) < 0.5) continue;
                Blocks.draw(r, row[2] < 3 ? leafDark : leaf, sx + dx * bs, y, bs, 255);
                if (snow != null && isTop(crown, row[2], dx)) Blocks.drawTop(r, snow, sx + dx * bs, y - Math.max(1, bs / 8), bs, 3);
            }
        }
    }

    private static boolean isTop(int[][] crown, int level, int dx) {
        for (int[] row : crown) if (row[2] == level + 1 && dx >= row[0] && dx <= row[1]) return false;
        return true;
    }

    // после дождя по ближней земле пробегают блики
    private void puddleShine(Raster r, Weather wx, double t, float u) {
        int step = Math.max(2, Math.round(7 * u));
        for (int x = 0; x < r.w; x += step) {
            double p = Noise.noise(x / (40.0 * u) + t * 0.3, 1900);
            if (p < 0.72) continue;
            int y = ground[x];
            if (y <= 0 || y >= r.h) continue;
            r.blend(x, y, x + step / 2, y + Math.max(1, Math.round(u)), 0xDDE8F4, (int) ((p - 0.72) * 300));
        }
    }

    private static int shadeLit(int c, int lightCol, int low, float haze) {
        return Col.mix(Col.mul(c, lightCol), low, haze);
    }

    private static int palette(Season s, int k) {
        return switch (s) {
            case WINTER -> new int[] {0x8C98A8, 0xD6DFE9, 0xDFE7F0, 0xE9EFF6}[k];
            case SPRING -> new int[] {0x6E7E92, 0x5C9148, 0x4F8341, 0x447436}[k];
            case SUMMER -> new int[] {0x6B7A8C, 0x4F7E45, 0x416F37, 0x37612D}[k];
            case AUTUMN -> new int[] {0x6E7686, 0x7C7A40, 0x6F6935, 0x5F5A2C}[k];
        };
    }

    private static int rimColour(Season s, int k, int c) {
        if (s == Season.WINTER) return k == 0 ? 0xF0F4F8 : 0xFAFCFF;
        if (k == 0) return Col.scale(c, 1.12f);
        return Col.scale(Col.mix(c, s == Season.AUTUMN ? 0xC8B04A : 0x8CD45A, 0.35f), 1.15f);
    }

    // ели и лиственные деревья, цвет листвы по сезону
    private void trees(Raster r, Sky sky, Weather wx, double t, float u, int k, double scroll, int low, int lightCol, float haze) {
        Season se = sky.season;
        int cs = Math.round((k == 3 ? 15 : k == 2 ? 11 : 7) * u);
        double dens = k == 3 ? 0.38 : k == 2 ? 0.42 : 0.5;
        float size = k == 3 ? 1f : k == 2 ? 0.66f : 0.4f;
        long c0 = (long) Math.floor(scroll / cs) - 1;
        long c1 = (long) Math.floor((scroll + r.w) / cs) + 1;
        float sway = (float) Math.sin(t * 1.7) * wx.wind;
        for (long cell = c0; cell <= c1; cell++) {
            double hh = Noise.hash(cell, 900 + k);
            if (hh > dens) continue;
            double wpos = cell * cs + Noise.hash(cell, 950 + k) * cs * 0.6;
            int x = (int) Math.round(wpos - scroll);
            int ground = height(r, wpos, t, k, u);
            boolean spruce = Noise.hash(cell, 960 + k) < (se == Season.WINTER ? 0.65 : 0.45);
            float s = size * (float) (0.8 + Noise.hash(cell, 970 + k) * 0.45);
            if (spruce) spruce(r, x, ground, u * s, se, k, lightCol, low, haze, sway);
            else leafy(r, x, ground, u * s, se, k, cell, lightCol, low, haze, sway);
        }
    }

    private void spruce(Raster r, int x, int ground, float s, Season se, int k, int lightCol, int low, float haze, float sway) {
        int px = Math.max(1, Math.round(s));
        int needle = shadeLit(0x24452E, lightCol, low, haze);
        int dark = shadeLit(0x1A3522, lightCol, low, haze);
        int snow = shadeLit(0xF2F6FA, lightCol, low, haze);
        int trunk = shadeLit(0x4A3420, lightCol, low, haze);
        r.fill(x, ground - 2 * px, x + px, ground + px, trunk);
        int tiers = 4;
        for (int i = 0; i < tiers; i++) {
            int half = (tiers - i) * px + px / 2;
            int y = ground - 2 * px - (i + 1) * 2 * px;
            int dx = Math.round(sway * px * i / tiers * 0.6f);
            r.fill(x - half + dx, y, x + px + half + dx, y + 2 * px, i % 2 == 0 ? needle : dark);
            if (se == Season.WINTER) r.fill(x - half + dx + px / 2, y, x + px + half + dx - px / 2, y + Math.max(1, px / 2), snow);
        }
        r.fill(x, ground - 2 * px - tiers * 2 * px - px, x + px, ground - 2 * px - tiers * 2 * px, se == Season.WINTER ? snow : needle);
    }

    private void leafy(Raster r, int x, int ground, float s, Season se, int k, long cell, int lightCol, int low, float haze, float sway) {
        int px = Math.max(1, Math.round(s));
        int trunk = shadeLit(Noise.hash(cell, 980) < 0.3 ? 0xD8D4C8 : 0x5A4128, lightCol, low, haze);
        int th = 4 * px;
        r.fill(x, ground - th, x + px, ground + px, trunk);
        int dx = Math.round(sway * px * 0.8f);
        if (se == Season.WINTER) {
            // голые ветки со снегом
            int snow = shadeLit(0xF2F6FA, lightCol, low, haze);
            r.fill(x - 2 * px + dx, ground - th - px, x - px + dx, ground - th + px, trunk);
            r.fill(x + px + dx, ground - th - 2 * px, x + 2 * px + dx, ground - th, trunk);
            r.fill(x + dx, ground - th - 3 * px, x + px + dx, ground - th, trunk);
            r.fill(x - 2 * px + dx, ground - th - px - Math.max(1, px / 2), x - px + dx, ground - th - px, snow);
            r.fill(x + px + dx, ground - th - 2 * px - Math.max(1, px / 2), x + 2 * px + dx, ground - th - 2 * px, snow);
            return;
        }
        int leaf = leafColour(se, cell);
        int lit = shadeLit(Col.scale(leaf, 1.15f), lightCol, low, haze);
        int mid = shadeLit(leaf, lightCol, low, haze);
        int dark = shadeLit(Col.scale(leaf, 0.75f), lightCol, low, haze);
        int top = ground - th;
        r.fill(x - 2 * px + dx, top - 3 * px, x + 3 * px + dx, top, mid);
        r.fill(x - px + dx, top - 4 * px, x + 2 * px + dx, top - 3 * px, lit);
        r.fill(x - 2 * px + dx, top - px, x + 3 * px + dx, top, dark);
        if (se == Season.SPRING && Noise.hash(cell, 990) < 0.35) {
            int bloom = shadeLit(0xF4B6CF, lightCol, low, haze);
            r.fill(x - px + dx, top - 3 * px, x + dx, top - 2 * px, bloom);
            r.fill(x + px + dx, top - 2 * px, x + 2 * px + dx, top - px, bloom);
        }
    }

    private static int leafColour(Season se, long cell) {
        double h = Noise.hash(cell, 985);
        return switch (se) {
            case AUTUMN -> h < 0.3 ? 0xD9822B : h < 0.6 ? 0xE3B23C : h < 0.8 ? 0xB5452A : 0x7F8F38;
            case SPRING -> 0x6DB043;
            default -> h < 0.5 ? 0x3E7F2C : 0x4A8A32;
        };
    }

    private void flowers(Raster r, float u, double scroll, int lightCol, int low, float haze) {
        int cs = Math.max(2, Math.round(5 * u));
        int px = Math.max(1, Math.round(u));
        long c0 = (long) Math.floor(scroll / cs), c1 = (long) Math.floor((scroll + r.w) / cs);
        int[] cols = {0xE84A3C, 0xF3DD45, 0xFFFFFF, 0x9C6CD8};
        for (long cell = c0; cell <= c1; cell++) {
            if (Noise.hash(cell, 1100) > 0.12) continue;
            int x = (int) (cell * cs - scroll);
            if (x < 0 || x >= r.w) continue;
            int y = ground[x];
            r.fill(x, y - px, x + px, y, shadeLit(cols[(int) (Noise.hash(cell, 1101) * 4)], lightCol, low, haze));
        }
    }

    // туман между слоями: полоса, которая медленно плывёт
    private void fogBand(Raster r, Weather wx, double t, float u, int k, int low) {
        if (wx.fog < 0.1f) return;
        int y1 = (int) (r.h * BASE[k + 1] + r.h * 0.02f);
        int y0 = y1 - (int) (r.h * (0.1f + wx.fog * 0.12f));
        for (int x = 0; x < r.w; x++) {
            float n = (float) Noise.fbm((x / u + t * (3 + wx.wind * 8)) * 0.012, 1200 + k);
            float a = wx.fog * (0.35f + 0.65f * n) * 0.85f;
            for (int y = Math.max(0, y0); y < Math.min(r.h, y1); y++) {
                float f = (y - y0) / (float) (y1 - y0);
                r.dot(x, y, low, (int) (a * f * 255));
            }
        }
    }

    // осадки и молния

    private void precip(Raster r, Weather wx, double t, float u, boolean near) {
        float area = r.w * r.h / (u * u * 480f * 270f);
        if (wx.rain > 0.02f) {
            int n = (int) (wx.rain * (near ? 260 : 360) * area);
            int len = Math.max(2, Math.round((near ? 9 : 5) * u));
            float slant = 0.25f + wx.wind * 0.5f;
            int a = near ? 110 : 60;
            int c = near ? 0xB8C8DA : 0x9EAEC2;
            float sp = (near ? 340 : 230) * u;
            for (int i = 0; i < n; i++) {
                double s = Noise.hash(i, near ? 11 : 12);
                double v = sp * (0.85 + s * 0.3);
                double fall = Noise.hash(i, 13) * r.h + t * v;
                float y = (float) floorMod(fall, r.h + len) - len;
                float x = (float) floorMod(Noise.hash(i, 14) * r.w - fall * slant, r.w);
                for (int j = 0; j < len; j++) r.dot((int) (x - j * slant), (int) y + j, c, a);
            }
            if (near) splashes(r, wx, t, u);
        }
        if (wx.snow > 0.02f) {
            int n = (int) (wx.snow * (near ? 200 : 320) * area);
            float sp = (near ? 26 : 14) * u * (1 + wx.wind * 0.6f);
            float drift = wx.wind * (near ? 90 : 50) * u;
            int sz = near ? Math.max(1, Math.round(u * 1.4f)) : Math.max(1, Math.round(u * 0.7f));
            for (int i = 0; i < n; i++) {
                double v = sp * (0.7 + Noise.hash(i, 21) * 0.6);
                double y = floorMod(Noise.hash(i, 22) * r.h + t * v, r.h + 4) - 2;
                double sw = Math.sin(t * (0.8 + Noise.hash(i, 23)) + i) * 4 * u;
                double x = floorMod(Noise.hash(i, 24) * r.w + t * drift * (0.8 + Noise.hash(i, 25) * 0.4) + sw, r.w);
                r.blend((int) x, (int) y, (int) x + sz, (int) y + sz, 0xF4F8FC, near ? 230 : 150);
            }
            if (near && wx.wind > 0.7f) {
                // порывы метели: длинные белые росчерки
                int m = (int) (60 * area * (wx.wind - 0.6f));
                for (int i = 0; i < m; i++) {
                    double v = 260 * u * (0.7 + Noise.hash(i, 31) * 0.6);
                    int y = (int) (Noise.hash(i, 32) * r.h);
                    int x = (int) floorMod(Noise.hash(i, 33) * r.w + t * v, r.w + 40 * u) - (int) (20 * u);
                    int l = (int) ((10 + Noise.hash(i, 34) * 20) * u);
                    r.blend(x, y, x + l, y + Math.max(1, Math.round(u * 0.5f)), 0xFFFFFF, 70);
                }
            }
        }
    }

    // брызги на ближнем склоне
    private void splashes(Raster r, Weather wx, double t, float u) {
        int n = (int) (wx.rain * 60 * r.w / (480f * u));
        int px = Math.max(1, Math.round(u * 0.6f));
        for (int i = 0; i < n; i++) {
            double life = 0.25;
            double ph = t / life + Noise.hash(i, 41);
            long gen = (long) Math.floor(ph);
            double p = ph - gen;
            int x = (int) (Noise.hash(i * 977 + gen, 42) * r.w);
            if (x < 0 || x >= r.w) continue;
            int y = ground[x] - px;
            int a = (int) ((1 - p) * 160);
            int spread = (int) (p * 3 * u);
            r.blend(x - spread, y - (int) (p * 2 * u), x - spread + px, y - (int) (p * 2 * u) + px, 0xD0DCEA, a);
            r.blend(x + spread, y - (int) (p * 2 * u), x + spread + px, y - (int) (p * 2 * u) + px, 0xD0DCEA, a);
        }
    }

    // ближняя молния с ветками и ореолом
    private void bolt(Raster r, Weather.Strike s, double t, float u) {
        float f = s.flash(t);
        if (f <= 0.05f) return;
        java.util.Random rnd = new java.util.Random(s.seed);
        int x = (int) (s.x * r.w);
        int groundY = height(r, x + t * SPEED[2] * u + 2 * 5000, t, 2, u);
        int a = (int) (Math.min(1, f) * 255);
        int px = Math.max(1, Math.round(u));
        segment(r, rnd, x, 0, groundY, px, a, u, 3);
    }

    private void segment(Raster r, java.util.Random rnd, int x, int y, int end, int px, int a, float u, int depth) {
        while (y < end) {
            int len = (int) ((3 + rnd.nextInt(7)) * u);
            r.blend(x - px * 2, y, x + px * 3, y + len, 0xB8C4FF, a / 5);
            r.blend(x, y, x + px, y + len, 0xF6F8FF, a);
            y += len;
            x += (rnd.nextBoolean() ? -1 : 1) * px * (1 + rnd.nextInt(3));
            if (depth > 0 && rnd.nextFloat() < 0.18f) {
                int bend = y + (int) ((end - y) * (0.2f + rnd.nextFloat() * 0.3f));
                segment(r, rnd, x, y, Math.min(end, bend), px, a / 2, u, depth - 1);
            }
        }
    }

    // лёгкое затемнение краёв и верха под логотипом
    private void vignette(Raster r) {
        if (vw != r.w || vh != r.h) {
            vw = r.w;
            vh = r.h;
            vignette = new float[r.w * r.h];
            for (int y = 0; y < r.h; y++) {
                for (int x = 0; x < r.w; x++) {
                    float dx = (x - r.w / 2f) / (r.w / 2f), dy = (y - r.h / 2f) / (r.h / 2f);
                    float v = 1 - Math.max(0, dx * dx * 0.5f + dy * dy * 0.5f - 0.3f) * 0.35f;
                    float topDark = 1 - Math.max(0, 0.35f - y / (float) r.h) * 0.4f;
                    vignette[y * r.w + x] = v * topDark;
                }
            }
        }
        int[] p = r.px;
        for (int i = 0; i < p.length; i++) {
            float k = vignette[i];
            if (k >= 0.999f) continue;
            int c = p[i];
            p[i] = (int) ((c >> 16 & 255) * k) << 16 | (int) ((c >> 8 & 255) * k) << 8 | (int) ((c & 255) * k);
        }
    }

    private static double floorMod(double a, double m) {
        double v = a % m;
        return v < 0 ? v + m : v;
    }
}
