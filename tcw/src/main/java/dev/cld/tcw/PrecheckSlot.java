package dev.cld.tcw;

// Место в самом условии, где лежит его быстрая проверка, чтобы не искать её в общей таблице.
public interface PrecheckSlot {
    Object tcw$precheck();

    void tcw$setPrecheck(Object p);
}
