package dev.cld.pregen.server;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Общее хранилище снимков чанков: сервер пишет, экран предпросмотра читает. */
public final class MapData {
    private static volatile MapData current = new MapData();

    public final Map<Long, ChunkSample> chunks = new ConcurrentHashMap<>();
    public final Queue<ChunkSample> fresh = new ConcurrentLinkedQueue<>();

    public static MapData get() { return current; }
    public static void reset() { current = new MapData(); }

    public void put(ChunkSample s) {
        chunks.put(ChunkSample.key(s.x, s.z), s);
        fresh.add(s);
    }

    public ChunkSample at(int cx, int cz) { return chunks.get(ChunkSample.key(cx, cz)); }
}
