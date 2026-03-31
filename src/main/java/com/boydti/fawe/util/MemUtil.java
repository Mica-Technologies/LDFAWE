package com.boydti.fawe.util;

import com.boydti.fawe.config.Settings;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MemUtil {

    private static AtomicBoolean memory = new AtomicBoolean(false);

    public static boolean isMemoryFree() {
        return !memory.get();
    }

    public static boolean isMemoryLimited() {
        return memory.get();
    }

    public static boolean isMemoryLimitedSlow() {
        if (memory.get()) {
            System.gc();
            calculateMemory();
            return memory.get();
        }
        return false;
    }

    public static long getUsedBytes() {
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return used;
    }

    public static long getFreeBytes() {
        return Runtime.getRuntime().maxMemory() - getUsedBytes();
    }

    public static int calculateMemory() {
        final long heapMaxSize = Runtime.getRuntime().maxMemory();
        // Free bytes = unused portion of current heap + room for heap to grow
        final long freeBytes = getFreeBytes();
        final int freePercent = (int) ((freeBytes * 100) / heapMaxSize);
        if (freePercent > (100 - Settings.IMP.MAX_MEMORY_PERCENT)) {
            memoryPlentifulTask();
            return Integer.MAX_VALUE;
        }
        return freePercent;
    }

    private static Queue<Runnable> memoryLimitedTasks = new ConcurrentLinkedQueue<>();
    private static Queue<Runnable> memoryPlentifulTasks = new ConcurrentLinkedQueue<>();

    public static void addMemoryLimitedTask(Runnable run) {
        if (run != null)
            memoryLimitedTasks.add(run);
    }

    public static void addMemoryPlentifulTask(Runnable run) {
        if (run != null)
            memoryPlentifulTasks.add(run);
    }

    public static void memoryLimitedTask() {
        System.gc();
        for (Runnable task : memoryLimitedTasks) {
            task.run();
        }
        memory.set(true);
    }

    public static void memoryPlentifulTask() {
        for (Runnable task : memoryPlentifulTasks) {
            task.run();
        }
        memory.set(false);
    }
}
