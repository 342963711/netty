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

import io.netty.util.internal.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static java.lang.Math.*;

import java.nio.ByteBuffer;

/**
 * 该类主要是维护 PoolChunk 的 使用率，根据不同的使用率将 PoolChunk 进行漂移管理
 * 主要分为
 * 1.PoolChunk 所属的 PoolChunkList
 *
 * 2.PoolChunk的分配调用
 * 3.PoolChunk的释放调用
 * @param <T>
 */
final class PoolChunkList<T> implements PoolChunkListMetric {
    private static final Iterator<PoolChunkMetric> EMPTY_METRICS = Collections.<PoolChunkMetric>emptyList().iterator();
    /**
     * 该块列表所属于的 池化区域
     */
    private final PoolArena<T> arena;
    /**
     * 下一个块列
     */
    private final PoolChunkList<T> nextList;
    //限制的最小利用率
    private final int minUsage;
    //限制的最大利用率
    private final int maxUsage;
    //可分配的最大容量
    private final int maxCapacity;
    private PoolChunk<T> head;
    private final int freeMinThreshold;
    private final int freeMaxThreshold;

    // This is only update once when create the linked like list of PoolChunkList in PoolArena constructor.
    private PoolChunkList<T> prevList;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    PoolChunkList(PoolArena<T> arena, PoolChunkList<T> nextList, int minUsage, int maxUsage, int chunkSize) {
        assert minUsage <= maxUsage;
        this.arena = arena;
        this.nextList = nextList;
        this.minUsage = minUsage;
        this.maxUsage = maxUsage;
        maxCapacity = calculateMaxCapacity(minUsage, chunkSize);

        // the thresholds are aligned with PoolChunk.usage() logic:
        // 1) basic logic: usage() = 100 - freeBytes * 100L / chunkSize
        //    so, for example: (usage() >= maxUsage) condition can be transformed in the following way:
        //      100 - freeBytes * 100L / chunkSize >= maxUsage
        //      freeBytes <= chunkSize * (100 - maxUsage) / 100
        //      let freeMinThreshold = chunkSize * (100 - maxUsage) / 100, then freeBytes <= freeMinThreshold
        //
        //  2) usage() returns an int value and has a floor rounding during a calculation,
        //     to be aligned absolute thresholds should be shifted for "the rounding step":
        //       freeBytes * 100 / chunkSize < 1
        //       the condition can be converted to: freeBytes < 1 * chunkSize / 100
        //     this is why we have + 0.99999999 shifts. A example why just +1 shift cannot be used:
        //       freeBytes = 16777216 == freeMaxThreshold: 16777216, usage = 0 < minUsage: 1, chunkSize: 16777216

        //     At the same time we want to have zero thresholds in case of (maxUsage == 100) and (minUsage == 100).
        //
        //  1.阈值是与PoolChunk.usage()逻辑保持对齐
        //  基本逻辑： usage(利用率) = 100- freeByte*100/chunkSize
        //  因此，例如 usage(利用率)>maxUsage 的 条件可以被转换为以下方式
        //    100-freeBytes * 100 / chunkSize >=maxUsage
        //    freeBytes <= chunkSize * (100 - maxUsage) / 100
        //    设置 freeMinThreshold = chunkSize * (100 - maxUsage) / 100 。 因为 freeBytes <= freeMinThreshold

        //  2. usage（）返回一个int值，并在计算过程中进行向下取整，
        //
        //空闲最小阈值，如果空闲字节小于该阈值，说明不满足了 maxUsage（当前利用率超过了maxUsage）
        freeMinThreshold = (maxUsage == 100) ? 0 : (int) (chunkSize * (100.0 - maxUsage + 0.99999999) / 100L);
        //空闲最大阈值，如果空闲字节大于该阈值，说明不满足了 minUsage（当前利用率小于了minUsage）
        freeMaxThreshold = (minUsage == 100) ? 0 : (int) (chunkSize * (100.0 - minUsage + 0.99999999) / 100L);
    }

    /**
     * Calculates the maximum capacity of a buffer that will ever be possible to allocate out of the {@link PoolChunk}s
     * that belong to the {@link PoolChunkList} with the given {@code minUsage} and {@code maxUsage} settings.
     *
     * 计算在给定的{@code minUsage}和{@code maxUsage}设置下，
     * 可以从属于{@link PoolChunkList}的{@link PoolChunk}中分配的缓冲区的最大容量。
     */
    private static int calculateMaxCapacity(int minUsage, int chunkSize) {
        minUsage = minUsage0(minUsage);

        if (minUsage == 100) {
            // If the minUsage is 100 we can not allocate anything out of this list.
            // 如果 最小使用量为100，则不能从改列表中分配
            return 0;
        }

        // Calculate the maximum amount of bytes that can be allocated from a PoolChunk in this PoolChunkList.
        //
        // 计算可从此PoolChunkList中的PoolChunk分配的最大字节数。
        //

        // As an example:
        // - If a PoolChunkList has minUsage == 25 we are allowed to allocate at most 75% of the chunkSize because
        //   this is the maximum amount available in any PoolChunk in this PoolChunkList.

        //说明
        // 如果PoolChunkList的minUsage==25，我们最多可以分配chunkSize的75%，因为这是该PoolChunkList中任何PoolChunk中可用的最大数量。
        //
        return  (int) (chunkSize * (100L - minUsage) / 100L);
    }

    void prevList(PoolChunkList<T> prevList) {
        assert this.prevList == null;
        this.prevList = prevList;
    }

    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int sizeIdx, PoolThreadCache threadCache) {
        int normCapacity = arena.sizeIdx2size(sizeIdx);
        if (normCapacity > maxCapacity) {
            // Either this PoolChunkList is empty or the requested capacity is larger then the capacity which can
            // be handled by the PoolChunks that are contained in this PoolChunkList.

            // 此PoolChunkList为空，或者请求的容量大于此PoolChunk List中包含的PoolChunk可以处理的容量。
            return false;
        }
        //挨个PoolChunk 进行分配，直到分配成功
        for (PoolChunk<T> cur = head; cur != null; cur = cur.next) {
            if (cur.allocate(buf, reqCapacity, sizeIdx, threadCache)) {
                if (cur.freeBytes <= freeMinThreshold) {
                    remove(cur);
                    nextList.add(cur);
                }
                return true;
            }
        }
        return false;
    }

    boolean free(PoolChunk<T> chunk, long handle, int normCapacity, ByteBuffer nioBuffer) {
        chunk.free(handle, normCapacity, nioBuffer);
        if (chunk.freeBytes > freeMaxThreshold) {
            remove(chunk);
            // Move the PoolChunk down the PoolChunkList linked-list.
            return move0(chunk);
        }
        return true;
    }

    private boolean move(PoolChunk<T> chunk) {
        assert chunk.usage() < maxUsage;

        if (chunk.freeBytes > freeMaxThreshold) {
            // Move the PoolChunk down the PoolChunkList linked-list.
            return move0(chunk);
        }

        // PoolChunk fits into this PoolChunkList, adding it here.
        add0(chunk);
        return true;
    }

    /**
     * Moves the {@link PoolChunk} down the {@link PoolChunkList} linked-list so it will end up in the right
     * {@link PoolChunkList} that has the correct minUsage / maxUsage in respect to {@link PoolChunk#usage()}.
     */
    private boolean move0(PoolChunk<T> chunk) {
        if (prevList == null) {
            // There is no previous PoolChunkList so return false which result in having the PoolChunk destroyed and
            // all memory associated with the PoolChunk will be released.
            assert chunk.usage() == 0;
            return false;
        }
        return prevList.move(chunk);
    }

    //添加一个内存块
    void add(PoolChunk<T> chunk) {
        //如果添加的chunk中空闲字节小于该阈值。则将该块添加到链中的下一个节点（向使用率较高的节点进行漂移）
        if (chunk.freeBytes <= freeMinThreshold) {
            nextList.add(chunk);
            return;
        }
        //如果满足该列表管理的使用率，则添加块到该对象中
        add0(chunk);
    }

    /**
     * Adds the {@link PoolChunk} to this {@link PoolChunkList}.
     *
     * 添加一个 PoolChunk 到该对象中, 并且将该对象中之前的head进行头插法后移
     */
    void add0(PoolChunk<T> chunk) {
        chunk.parent = this;
        if (head == null) {
            head = chunk;
            chunk.prev = null;
            chunk.next = null;
        } else {
            chunk.prev = null;
            chunk.next = head;
            head.prev = chunk;
            head = chunk;
        }
    }

    private void remove(PoolChunk<T> cur) {
        if (cur == head) {
            head = cur.next;
            if (head != null) {
                head.prev = null;
            }
        } else {
            PoolChunk<T> next = cur.next;
            cur.prev.next = next;
            if (next != null) {
                next.prev = cur.prev;
            }
        }
    }

    @Override
    public int minUsage() {
        return minUsage0(minUsage);
    }

    @Override
    public int maxUsage() {
        return min(maxUsage, 100);
    }

    private static int minUsage0(int value) {
        return max(1, value);
    }

    @Override
    public Iterator<PoolChunkMetric> iterator() {
        arena.lock();
        try {
            if (head == null) {
                return EMPTY_METRICS;
            }
            List<PoolChunkMetric> metrics = new ArrayList<PoolChunkMetric>();
            for (PoolChunk<T> cur = head;;) {
                metrics.add(cur);
                cur = cur.next;
                if (cur == null) {
                    break;
                }
            }
            return metrics.iterator();
        } finally {
            arena.unlock();
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        arena.lock();
        try {
            if (head == null) {
                return "none";
            }

            for (PoolChunk<T> cur = head;;) {
                buf.append(cur);
                cur = cur.next;
                if (cur == null) {
                    break;
                }
                buf.append(StringUtil.NEWLINE);
            }
        } finally {
            arena.unlock();
        }
        return buf.toString();
    }

    void destroy(PoolArena<T> arena) {
        PoolChunk<T> chunk = head;
        while (chunk != null) {
            arena.destroyChunk(chunk);
            chunk = chunk.next;
        }
        head = null;
    }
}
