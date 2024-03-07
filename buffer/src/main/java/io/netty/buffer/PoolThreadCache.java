/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;


import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

import io.netty.buffer.PoolArena.SizeClass;
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.ObjectPool;
import io.netty.util.internal.ObjectPool.Handle;
import io.netty.util.internal.ObjectPool.ObjectCreator;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Acts a Thread cache for allocations. This implementation is moduled after
 * <a href="https://people.freebsd.org/~jasone/jemalloc/bsdcan2006/jemalloc.pdf">jemalloc</a> and the descripted
 * technics of
 * <a href="https://www.facebook.com/notes/facebook-engineering/scalable-memory-allocation-using-jemalloc/480222803919">
 * Scalable memory allocation using jemalloc</a>.
 *
 * 充当线程缓冲，与jemalloc 类似，都包含thread cache的特性。
 */
final class PoolThreadCache {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PoolThreadCache.class);
    private static final int INTEGER_SIZE_MINUS_ONE = Integer.SIZE - 1;

    final PoolArena<byte[]> heapArena;
    final PoolArena<ByteBuffer> directArena;

    // Hold the caches for the different size classes, which are small and normal.
    private final MemoryRegionCache<byte[]>[] smallSubPageHeapCaches;
    private final MemoryRegionCache<byte[]>[] normalHeapCaches;
    private final MemoryRegionCache<ByteBuffer>[] smallSubPageDirectCaches;
    private final MemoryRegionCache<ByteBuffer>[] normalDirectCaches;

    //缓存整理频率 8192
    private final int freeSweepAllocationThreshold;
    private final AtomicBoolean freed = new AtomicBoolean();

    private int allocations;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /**
     *
     * @param heapArena 最少使用的 堆区域
     * @param directArena 最少使用的直接内存区域
     * @param smallCacheSize 256
     * @param normalCacheSize 64
     * @param maxCachedBufferCapacity 32*1024 （32K）
     * @param freeSweepAllocationThreshold 8192(),缓存整理频率
     */
    PoolThreadCache(PoolArena<byte[]> heapArena, PoolArena<ByteBuffer> directArena,
                    int smallCacheSize, int normalCacheSize, int maxCachedBufferCapacity,
                    int freeSweepAllocationThreshold) {
        checkPositiveOrZero(maxCachedBufferCapacity, "maxCachedBufferCapacity");
        this.freeSweepAllocationThreshold = freeSweepAllocationThreshold;
        this.heapArena = heapArena;
        this.directArena = directArena;
        if (directArena != null) {
            smallSubPageDirectCaches = createSubPageCaches(
                    smallCacheSize, directArena.numSmallSubpagePools);

            normalDirectCaches = createNormalCaches(
                    normalCacheSize, maxCachedBufferCapacity, directArena);

            directArena.numThreadCaches.getAndIncrement();
        } else {
            // No directArea is configured so just null out all caches
            smallSubPageDirectCaches = null;
            normalDirectCaches = null;
        }
        if (heapArena != null) {
            // Create the caches for the heap allocations
            smallSubPageHeapCaches = createSubPageCaches(
                    smallCacheSize, heapArena.numSmallSubpagePools);

            normalHeapCaches = createNormalCaches(
                    normalCacheSize, maxCachedBufferCapacity, heapArena);

            heapArena.numThreadCaches.getAndIncrement();
        } else {
            // No heapArea is configured so just null out all caches
            smallSubPageHeapCaches = null;
            normalHeapCaches = null;
        }
        // Only check if there are caches in use.
        if ((smallSubPageDirectCaches != null || normalDirectCaches != null
                || smallSubPageHeapCaches != null || normalHeapCaches != null)
                && freeSweepAllocationThreshold < 1) {
            throw new IllegalArgumentException("freeSweepAllocationThreshold: "
                    + freeSweepAllocationThreshold + " (expected: > 0)");
        }
    }

    private static <T> MemoryRegionCache<T>[] createSubPageCaches(
            int cacheSize, int numCaches) {
        if (cacheSize > 0 && numCaches > 0) {
            @SuppressWarnings("unchecked")
            MemoryRegionCache<T>[] cache = new MemoryRegionCache[numCaches];
            for (int i = 0; i < cache.length; i++) {
                // TODO: maybe use cacheSize / cache.length
                cache[i] = new SubPageMemoryRegionCache<T>(cacheSize);
            }
            return cache;
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> MemoryRegionCache<T>[] createNormalCaches(
            int cacheSize, int maxCachedBufferCapacity, PoolArena<T> area) {
        if (cacheSize > 0 && maxCachedBufferCapacity > 0) {
            int max = Math.min(area.chunkSize, maxCachedBufferCapacity);
            // Create as many normal caches as we support based on how many sizeIdx we have and what the upper
            // bound is that we want to cache in general.
            List<MemoryRegionCache<T>> cache = new ArrayList<MemoryRegionCache<T>>() ;
            for (int idx = area.numSmallSubpagePools; idx < area.nSizes && area.sizeIdx2size(idx) <= max ; idx++) {
                cache.add(new NormalMemoryRegionCache<T>(cacheSize));
            }
            return cache.toArray(new MemoryRegionCache[0]);
        } else {
            return null;
        }
    }

    // val > 0

    /**
     * 求 val 的 对数，向下取整。
     * @param val
     * @return
     */
    static int log2(int val) {
        return INTEGER_SIZE_MINUS_ONE - Integer.numberOfLeadingZeros(val);
    }

    /**
     * Try to allocate a small buffer out of the cache. Returns {@code true} if successful {@code false} otherwise
     */
    boolean allocateSmall(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int sizeIdx) {
        return allocate(cacheForSmall(area, sizeIdx), buf, reqCapacity);
    }

    /**
     * Try to allocate a normal buffer out of the cache. Returns {@code true} if successful {@code false} otherwise
     */
    boolean allocateNormal(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int sizeIdx) {
        return allocate(cacheForNormal(area, sizeIdx), buf, reqCapacity);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private boolean allocate(MemoryRegionCache<?> cache, PooledByteBuf buf, int reqCapacity) {
        if (cache == null) {
            // no cache found so just return false here
            return false;
        }
        boolean allocated = cache.allocate(buf, reqCapacity, this);
        if (++ allocations >= freeSweepAllocationThreshold) {
            allocations = 0;
            trim();
        }
        return allocated;
    }

    /**
     * Add {@link PoolChunk} and {@code handle} to the cache if there is enough room.
     * Returns {@code true} if it fit into the cache {@code false} otherwise.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    boolean add(PoolArena<?> area, PoolChunk chunk, ByteBuffer nioBuffer,
                long handle, int normCapacity, SizeClass sizeClass) {
        int sizeIdx = area.size2SizeIdx(normCapacity);
        MemoryRegionCache<?> cache = cache(area, sizeIdx, sizeClass);
        if (cache == null) {
            return false;
        }
        if (freed.get()) {
            return false;
        }
        return cache.add(chunk, nioBuffer, handle, normCapacity);
    }

    private MemoryRegionCache<?> cache(PoolArena<?> area, int sizeIdx, SizeClass sizeClass) {
        switch (sizeClass) {
        case Normal:
            return cacheForNormal(area, sizeIdx);
        case Small:
            return cacheForSmall(area, sizeIdx);
        default:
            throw new Error();
        }
    }

    /// TODO: In the future when we move to Java9+ we should use java.lang.ref.Cleaner.
    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            free(true);
        }
    }

    /**
     *  Should be called if the Thread that uses this cache is about to exist to release resources out of the cache
     */
    void free(boolean finalizer) {
        // As free() may be called either by the finalizer or by FastThreadLocal.onRemoval(...) we need to ensure
        // we only call this one time.
        if (freed.compareAndSet(false, true)) {
            int numFreed = free(smallSubPageDirectCaches, finalizer) +
                    free(normalDirectCaches, finalizer) +
                    free(smallSubPageHeapCaches, finalizer) +
                    free(normalHeapCaches, finalizer);

            if (numFreed > 0 && logger.isDebugEnabled()) {
                logger.debug("Freed {} thread-local buffer(s) from thread: {}", numFreed,
                        Thread.currentThread().getName());
            }

            if (directArena != null) {
                directArena.numThreadCaches.getAndDecrement();
            }

            if (heapArena != null) {
                heapArena.numThreadCaches.getAndDecrement();
            }
        } else {
            // See https://github.com/netty/netty/issues/12749
            checkCacheMayLeak(smallSubPageDirectCaches, "SmallSubPageDirectCaches");
            checkCacheMayLeak(normalDirectCaches, "NormalDirectCaches");
            checkCacheMayLeak(smallSubPageHeapCaches, "SmallSubPageHeapCaches");
            checkCacheMayLeak(normalHeapCaches, "NormalHeapCaches");
        }
    }

    private static void checkCacheMayLeak(MemoryRegionCache<?>[] caches, String type) {
        for (MemoryRegionCache<?> cache : caches) {
            if (!cache.queue.isEmpty()) {
                logger.debug("{} memory may leak.", type);
                return;
            }
        }
    }

    private static int free(MemoryRegionCache<?>[] caches, boolean finalizer) {
        if (caches == null) {
            return 0;
        }

        int numFreed = 0;
        for (MemoryRegionCache<?> c: caches) {
            numFreed += free(c, finalizer);
        }
        return numFreed;
    }

    private static int free(MemoryRegionCache<?> cache, boolean finalizer) {
        if (cache == null) {
            return 0;
        }
        return cache.free(finalizer);
    }

    void trim() {
        trim(smallSubPageDirectCaches);
        trim(normalDirectCaches);
        trim(smallSubPageHeapCaches);
        trim(normalHeapCaches);
    }

    private static void trim(MemoryRegionCache<?>[] caches) {
        if (caches == null) {
            return;
        }
        for (MemoryRegionCache<?> c: caches) {
            trim(c);
        }
    }

    private static void trim(MemoryRegionCache<?> cache) {
        if (cache == null) {
            return;
        }
        cache.trim();
    }

    private MemoryRegionCache<?> cacheForSmall(PoolArena<?> area, int sizeIdx) {
        if (area.isDirect()) {
            return cache(smallSubPageDirectCaches, sizeIdx);
        }
        return cache(smallSubPageHeapCaches, sizeIdx);
    }

    private MemoryRegionCache<?> cacheForNormal(PoolArena<?> area, int sizeIdx) {
        // We need to substract area.numSmallSubpagePools as sizeIdx is the overall index for all sizes.
        int idx = sizeIdx - area.numSmallSubpagePools;
        if (area.isDirect()) {
            return cache(normalDirectCaches, idx);
        }
        return cache(normalHeapCaches, idx);
    }

    private static <T> MemoryRegionCache<T> cache(MemoryRegionCache<T>[] cache, int sizeIdx) {
        if (cache == null || sizeIdx > cache.length - 1) {
            return null;
        }
        return cache[sizeIdx];
    }

    /**
     * Cache used for buffers which are backed by TINY or SMALL size.
     *
     * 用于由TINY或SMALL大小支持的缓冲区的缓存。
     */
    private static final class SubPageMemoryRegionCache<T> extends MemoryRegionCache<T> {
        SubPageMemoryRegionCache(int size) {
            super(size, SizeClass.Small);
        }

        @Override
        protected void initBuf(
                PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, PooledByteBuf<T> buf, int reqCapacity,
                PoolThreadCache threadCache) {
            chunk.initBufWithSubpage(buf, nioBuffer, handle, reqCapacity, threadCache);
        }
    }

    /**
     * Cache used for buffers which are backed by NORMAL size.
     */
    private static final class NormalMemoryRegionCache<T> extends MemoryRegionCache<T> {
        NormalMemoryRegionCache(int size) {
            super(size, SizeClass.Normal);
        }

        @Override
        protected void initBuf(
                PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, PooledByteBuf<T> buf, int reqCapacity,
                PoolThreadCache threadCache) {
            chunk.initBuf(buf, nioBuffer, handle, reqCapacity, threadCache);
        }
    }

    private abstract static class MemoryRegionCache<T> {
        private final int size;
        private final Queue<Entry<T>> queue;
        private final SizeClass sizeClass;
        private int allocations;

        MemoryRegionCache(int size, SizeClass sizeClass) {
            this.size = MathUtil.safeFindNextPositivePowerOfTwo(size);
            queue = PlatformDependent.newFixedMpscQueue(this.size);
            this.sizeClass = sizeClass;
        }

        /**
         * Init the {@link PooledByteBuf} using the provided chunk and handle with the capacity restrictions.
         *
         * 使用提供的区块初始化｛@link PooledByteBuf｝并处理容量限制
         */
        protected abstract void initBuf(PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle,
                                        PooledByteBuf<T> buf, int reqCapacity, PoolThreadCache threadCache);

        /**
         * Add to cache if not already full.
         *
         * 如果还没有满，则添加到缓存
         */
        @SuppressWarnings("unchecked")
        public final boolean add(PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, int normCapacity) {
            Entry<T> entry = newEntry(chunk, nioBuffer, handle, normCapacity);
            boolean queued = queue.offer(entry);
            if (!queued) {
                // If it was not possible to cache the chunk, immediately recycle the entry
                //如果没有缓存 该 块，；立即回收实体
                entry.recycle();
            }
            return queued;
        }

        /**
         * Allocate something out of the cache if possible and remove the entry from the cache.
         *
         * 如果可能的话，从缓存中分配一些内容，并从缓存中删除条目。
         */
        public final boolean allocate(PooledByteBuf<T> buf, int reqCapacity, PoolThreadCache threadCache) {
            Entry<T> entry = queue.poll();
            if (entry == null) {
                return false;
            }
            initBuf(entry.chunk, entry.nioBuffer, entry.handle, buf, reqCapacity, threadCache);
            entry.recycle();

            // allocations 不是线程安全的，这很好，因为它总是只从同一个线程调用。
            // allocations is not thread-safe which is fine as this is only called from the same thread all time.
            ++ allocations;
            return true;
        }

        /**
         * Clear out this cache and free up all previous cached {@link PoolChunk}s and {@code handle}s.
         *
         * 清除此缓存并释放所有以前缓存的｛@link PoolChunk｝s和｛@code handle｝s。
         */
        public final int free(boolean finalizer) {
            return free(Integer.MAX_VALUE, finalizer);
        }


        private int free(int max, boolean finalizer) {
            int numFreed = 0;
            for (; numFreed < max; numFreed++) {
                Entry<T> entry = queue.poll();
                if (entry != null) {
                    freeEntry(entry, finalizer);
                } else {
                    // all cleared
                    return numFreed;
                }
            }
            return numFreed;
        }

        /**
         * Free up cached {@link PoolChunk}s if not allocated frequently enough.
         *
         * 如果没有足够频繁地分配，释放缓存的｛@link PoolChunk｝。
         */
        public final void trim() {
            int free = size - allocations;
            allocations = 0;

            // We not even allocated all the number that are
            if (free > 0) {
                free(free, false);
            }
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private  void freeEntry(Entry entry, boolean finalizer) {
            // Capture entry state before we recycle the entry object.
            PoolChunk chunk = entry.chunk;
            long handle = entry.handle;
            ByteBuffer nioBuffer = entry.nioBuffer;
            int normCapacity = entry.normCapacity;

            if (!finalizer) {
                // recycle now so PoolChunk can be GC'ed. This will only be done if this is not freed because of
                // a finalizer.
                entry.recycle();
            }

            chunk.arena.freeChunk(chunk, handle, normCapacity, sizeClass, nioBuffer, finalizer);
        }


        /**
         * 记录内存信息的实体类
         * @param <T>
         */
        static final class Entry<T> {
            //可回收对象
            final Handle<Entry<?>> recyclerHandle;
            //池块
            PoolChunk<T> chunk;
            //内存对象
            ByteBuffer nioBuffer;
            //内存对象在池块中的 位置信息
            long handle = -1;
            //容量
            int normCapacity;

            Entry(Handle<Entry<?>> recyclerHandle) {
                this.recyclerHandle = recyclerHandle;
            }

            void recycle() {
                chunk = null;
                nioBuffer = null;
                handle = -1;
                recyclerHandle.recycle(this);
            }
        }

        @SuppressWarnings("rawtypes")
        private static Entry newEntry(PoolChunk<?> chunk, ByteBuffer nioBuffer, long handle, int normCapacity) {
            Entry entry = RECYCLER.get();
            entry.chunk = chunk;
            entry.nioBuffer = nioBuffer;
            entry.handle = handle;
            entry.normCapacity = normCapacity;
            return entry;
        }

        @SuppressWarnings("rawtypes")
        private static final ObjectPool<Entry> RECYCLER = ObjectPool.newPool(new ObjectCreator<Entry>() {
            @SuppressWarnings("unchecked")
            @Override
            public Entry newObject(Handle<Entry> handle) {
                return new Entry(handle);
            }
        });
    }
}
