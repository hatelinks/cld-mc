package dev.cld.far.client;

import dev.cld.far.gen.TileMesh;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Выборки рельефа тайлов (дорогая часть расчёта): в памяти, сколько влезает в лимит, и на диске по миру.
// С ними смена вида пересобирает сетку за миллисекунды, а повторный вход в мир не считает рельеф заново.
// Папка на диске зависит от сида, настроек мира, версии tcw и формата выборки: старый кэш просто не найдётся.
final class TileCache {
    private static final Logger LOG = LoggerFactory.getLogger("cld_far");
    // поднимать при изменении формата Samples или того, что в них пишется
    private static final int FORMAT = 2;

    private final LinkedHashMap<Long, TileMesh.Samples> mem = new LinkedHashMap<>(256, 0.75f, true);
    private long memBytes, memLimit;
    private volatile File dir;
    private long diskLimit;
    volatile int hits, misses;

    // world: строка, однозначно задающая рельеф (сид, настройки, версия tcw); null, без диска.
    synchronized void configure(String world, long memLimitMb, boolean disk, long diskLimitMb) {
        memLimit = memLimitMb << 20;
        diskLimit = diskLimitMb << 20;
        File d = null;
        if (world != null && disk) {
            d = new File(new File(net.minecraft.client.Minecraft.getInstance().gameDirectory, "cld_far_cache"),
                    Integer.toHexString(world.hashCode()) + "-f" + FORMAT);
            if (!d.equals(dir)) {
                d.mkdirs();
                File dd = d;
                Thread th = new Thread(() -> prune(dd), "cld_far-cache");
                th.setDaemon(true);
                th.setPriority(Thread.MIN_PRIORITY);
                th.start();
            }
        }
        dir = d;
        trim();
    }

    synchronized void clear() {
        mem.clear();
        memBytes = 0;
    }

    // Ключ выборки: тайл и шаг сетки (от шага и детальности гор зависит её размер).
    static long key(long tile, double tile0, int mountains) {
        return tile * 31 + (long) (tile0 * 16) * 7 + mountains;
    }

    TileMesh.Samples get(long key, String name) {
        synchronized (this) {
            TileMesh.Samples s = mem.get(key);
            if (s != null) {
                hits++;
                return s;
            }
        }
        File d = dir;
        if (d != null) {
            File f = new File(d, name);
            if (f.isFile()) {
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new java.io.FileInputStream(f))))) {
                    TileMesh.Samples s = TileMesh.Samples.read(in);
                    put(key, s, null);
                    hits++;
                    return s;
                } catch (Exception e) {
                    f.delete();
                }
            }
        }
        misses++;
        return null;
    }

    void put(long key, TileMesh.Samples s, String name) {
        synchronized (this) {
            TileMesh.Samples old = mem.put(key, s);
            if (old != null) memBytes -= old.bytes();
            memBytes += s.bytes();
            trim();
        }
        File d = dir;
        if (d == null || name == null) return;
        File f = new File(d, name), tmp = new File(d, name + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new java.io.FileOutputStream(tmp))))) {
            s.write(out);
        } catch (Exception e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) tmp.delete();
    }

    private void trim() {
        var it = mem.entrySet().iterator();
        while (memBytes > memLimit && it.hasNext()) {
            Map.Entry<Long, TileMesh.Samples> e = it.next();
            memBytes -= e.getValue().bytes();
            it.remove();
        }
    }

    long memBytes() { return memBytes; }

    // Удалить файлы кэша текущего мира.
    void clearDisk() {
        File d = dir;
        File[] fs = d == null ? null : d.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    // Старые файлы удаляются, пока кэш мира больше лимита (сначала самые давние).
    private void prune(File d) {
        File[] fs = d.listFiles((x, n) -> n.endsWith(".bin"));
        if (fs == null) return;
        long total = 0;
        for (File f : fs) total += f.length();
        if (total <= diskLimit) return;
        Arrays.sort(fs, Comparator.comparingLong(File::lastModified));
        for (File f : fs) {
            if (total <= diskLimit) break;
            total -= f.length();
            f.delete();
        }
        LOG.info("Дальний рельеф: кэш на диске урезан до {} МБ", total >> 20);
    }
}
