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

import java.util.concurrent.locks.ReentrantLock;

import static io.netty.buffer.PoolChunk.RUN_OFFSET_SHIFT;
import static io.netty.buffer.PoolChunk.SIZE_SHIFT;
import static io.netty.buffer.PoolChunk.IS_USED_SHIFT;
import static io.netty.buffer.PoolChunk.IS_SUBPAGE_SHIFT;
import static io.netty.buffer.SizeClasses.LOG2_QUANTUM;

/**
 * 池化子页管理。 理解链表设计
 *
 * 该类由{@link PoolChunk#allocateSubpage(int)}进行管理
 *
 * 需要理解的方法有
 * {@link #allocate()}
 *
 * @param <T>
 */
final class PoolSubpage<T> implements PoolSubpageMetric {

    /**
     * 该页所属的 块
     */
    final PoolChunk<T> chunk;
    /**
     * 元素大小
     */
    final int elemSize;

    /**
     * 页面偏移量
     */
    private final int pageShifts;

    /**
     * 运行偏移量
     */
    private final int runOffset;
    /**
     * 运行大小
     */
    private final int runSize;

    /**
     * 使用位图来标记 状态
     * 16表示的是元素大小
     * runSize/64/16=（除以16表示总共可以容纳的元素个数，按照每行64个元素，总计需要的行数）
     * 构建二维表 n*64
     * {@link #bitmapLength}
     * 0000000000000000000000000000000000000000000000000000000000000000
     * 0000000000000000000000000000000000000000000000000000000000000000
     * 0000000000000000000000000000000000000000000000000000000000000000
     */
    private final long[] bitmap;

    PoolSubpage<T> prev;
    PoolSubpage<T> next;

    /**
     * 销毁标识，创建后默认值是 true.
     */
    boolean doNotDestroy;

    /**
     * 最大元素数量
     * runSize / elemSize
     */
    private int maxNumElems;

    /**
     * 位图长度
     * 按照真实的 {@link #elemSize} 计算出来的长度。
     * runSize/64/elemSize
     */
    private int bitmapLength;

    /**
     * 下一个可用的元素
     */
    private int nextAvail;

    /**
     * 可⽤于分配的内存块个数
     */
    private int numAvail;

    private final ReentrantLock lock = new ReentrantLock();

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /** Special constructor that creates a linked list head
     * 指定用于创建链表头结点
     * */
    PoolSubpage() {
        chunk = null;
        pageShifts = -1;
        runOffset = -1;
        elemSize = -1;
        runSize = -1;
        bitmap = null;
    }

    /**
     *
     * @param head 链表头节点
     * @param chunk 所属的chunk
     * @param pageShifts 13
     * @param runOffset 0(初始化，页面运行偏移量)
     * @param runSize （8192）
     * @param elemSize 16
     */
    PoolSubpage(PoolSubpage<T> head, PoolChunk<T> chunk, int pageShifts, int runOffset, int runSize, int elemSize) {
        this.chunk = chunk;
        this.pageShifts = pageShifts;
        this.runOffset = runOffset;
        this.runSize = runSize;
        this.elemSize = elemSize;
        //bitmap.length=8;  /64/16
        bitmap = new long[runSize >>> 6 + LOG2_QUANTUM];

        doNotDestroy = true;
        if (elemSize != 0) {
            //512
            maxNumElems = numAvail = runSize / elemSize;
            nextAvail = 0;
            //这里将 本页中 可以容纳元素的总量，除以 64 来计算 整个bitmap 数组的长度。 如果有余数，则位图长度继续+1. 这里位图 是 lang 类型的数字。 每个long 按照位来说。
            //在二维表中。也就是每行可以表示64个对象。除以64也就是总共的行数。以此来计算该页面是否被全部使用
            bitmapLength = maxNumElems >>> 6;
            if ((maxNumElems & 63) != 0) {
                bitmapLength ++;
            }
        }
        addToPool(head);
    }

    /**
     * Returns the bitmap index of the subpage allocation.
     *
     * 返回子页面分配的位图索引
     */
    long allocate() {
        //如果页面可用数为0，或者已经被销毁，则分配失败
        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }
        //获取位图索引
        final int bitmapIdx = getNextAvail();
        if (bitmapIdx < 0) {
            removeFromPool(); // Subpage appear to be in an invalid state. Remove to prevent repeated errors.
            throw new AssertionError("No next available bitmap index found (bitmapIdx = " + bitmapIdx + "), " +
                    "even though there are supposed to be (numAvail = " + numAvail + ") " +
                    "out of (maxNumElems = " + maxNumElems + ") available indexes.");
        }
        //二维表格中的行
        int q = bitmapIdx >>> 6;
        //对应的使用个数（小于64的值，表示的是第几位被使用，也就是第几列）
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) == 0;
        // 左移运算高于 或 运算。 先标记被使用的位置。然后 使用或运算将值更新到二维 位图中
        bitmap[q] |= 1L << r;

        //当该页不能被分配空间，则从池中进行移除
        if (-- numAvail == 0) {
            removeFromPool();
        }
        //计算该页面的 按照 poolChunk 的规则 进行表示
        return toHandle(bitmapIdx);
    }

    /**
     * @return {@code true} if this subpage is in use.
     *         {@code false} if this subpage is not used by its chunk and thus it's OK to be released.
     */
    boolean free(PoolSubpage<T> head, int bitmapIdx) {
        if (elemSize == 0) {
            return true;
        }
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) != 0;
        bitmap[q] ^= 1L << r;

        setNextAvail(bitmapIdx);

        if (numAvail ++ == 0) {
            addToPool(head);
            /* When maxNumElems == 1, the maximum numAvail is also 1.
             * Each of these PoolSubpages will go in here when they do free operation.
             * If they return true directly from here, then the rest of the code will be unreachable
             * and they will not actually be recycled. So return true only on maxNumElems > 1. */
            if (maxNumElems > 1) {
                return true;
            }
        }

        if (numAvail != maxNumElems) {
            return true;
        } else {
            // Subpage not in use (numAvail == maxNumElems)
            if (prev == next) {
                // Do not remove if this subpage is the only one left in the pool.
                return true;
            }

            // Remove this subpage from the pool if there are other subpages left in the pool.
            doNotDestroy = false;
            removeFromPool();
            return false;
        }
    }

    private void addToPool(PoolSubpage<T> head) {
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }

    private void removeFromPool() {
        assert prev != null && next != null;
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    private void setNextAvail(int bitmapIdx) {
        nextAvail = bitmapIdx;
    }


    private int getNextAvail() {
        //第一次默认值是0，之后nextAvail的值为-1，只有通过 free之后，nextAvail 的值才会改变
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            this.nextAvail = -1;
            return nextAvail;
        }
        return findNextAvail();
    }

    private int findNextAvail() {
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        for (int i = 0; i < bitmapLength; i ++) {
            long bits = bitmap[i];
            //找到没有都没有被标识为1的位图
            if (~bits != 0) {
                //
                return findNextAvail0(i, bits);
            }
        }
        return -1;
    }

    /**
     * 从二维表格中 查找一个可用的位置
     * @param i  位图索引
     * @param bits 索引i 对应的值
     * @return
     */
    private int findNextAvail0(int i, long bits) {
        // 最大元素个数 为 pageSize/elementSize = 8192/16 = 512
        final int maxNumElems = this.maxNumElems;
        // baseVal = i*64
        final int baseVal = i << 6;

        for (int j = 0; j < 64; j ++) {
            if ((bits & 1) == 0) {
                int val = baseVal | j;
                if (val < maxNumElems) {
                    return val;
                } else {
                    break;
                }
            }
            bits >>>= 1;
        }
        return -1;
    }

    private long toHandle(int bitmapIdx) {
        int pages = runSize >> pageShifts;
        return (long) runOffset << RUN_OFFSET_SHIFT
               | (long) pages << SIZE_SHIFT
               | 1L << IS_USED_SHIFT
               | 1L << IS_SUBPAGE_SHIFT
               | bitmapIdx;
    }

    @Override
    public String toString() {
        final boolean doNotDestroy;
        final int maxNumElems;
        final int numAvail;
        final int elemSize;
        if (chunk == null) {
            // This is the head so there is no need to synchronize at all as these never change.
            doNotDestroy = true;
            maxNumElems = 0;
            numAvail = 0;
            elemSize = -1;
        } else {
            chunk.arena.lock();
            try {
                if (!this.doNotDestroy) {
                    doNotDestroy = false;
                    // Not used for creating the String.
                    maxNumElems = numAvail = elemSize = -1;
                } else {
                    doNotDestroy = true;
                    maxNumElems = this.maxNumElems;
                    numAvail = this.numAvail;
                    elemSize = this.elemSize;
                }
            } finally {
                chunk.arena.unlock();
            }
        }

        if (!doNotDestroy) {
            return "(" + runOffset + ": not in use)";
        }

        return "(" + runOffset + ": " + (maxNumElems - numAvail) + '/' + maxNumElems +
                ", offset: " + runOffset + ", length: " + runSize + ", elemSize: " + elemSize + ')';
    }

    @Override
    public int maxNumElements() {
        if (chunk == null) {
            // It's the head.
            return 0;
        }
        chunk.arena.lock();
        try {
            return maxNumElems;
        } finally {
            chunk.arena.unlock();
        }
    }

    @Override
    public int numAvailable() {
        if (chunk == null) {
            // It's the head.
            return 0;
        }

        chunk.arena.lock();
        try {
            return numAvail;
        } finally {
            chunk.arena.unlock();
        }
    }

    @Override
    public int elementSize() {
        if (chunk == null) {
            // It's the head.
            return -1;
        }

        chunk.arena.lock();
        try {
            return elemSize;
        } finally {
            chunk.arena.unlock();
        }
    }

    @Override
    public int pageSize() {
        return 1 << pageShifts;
    }

    void destroy() {
        if (chunk != null) {
            chunk.destroy();
        }
    }

    void lock() {
        lock.lock();
    }

    void unlock() {
        lock.unlock();
    }
}
