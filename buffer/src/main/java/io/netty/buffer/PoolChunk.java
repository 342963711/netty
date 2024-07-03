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

import io.netty.util.internal.LongCounter;
import io.netty.util.internal.PlatformDependent;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Description of algorithm for PageRun/PoolSubpage allocation from PoolChunk
 *
 * 从 PoolChunk 分配 PageRun/PoolSubpage 的算法描述
 *
 * Notation: The following terms are important to understand the code
 * > page  - a page is the smallest unit of memory chunk that can be allocated
 * > run   - a run is a collection of pages
 * > chunk - a chunk is a collection of runs
 * > in this code chunkSize = maxPages * pageSize
 *
 * 页-> 页是可以分配的最小内存块单位
 * 运行 -> 是页面集合
 * 块-> 块 是run 的集合
 * chunkSize = maxPages*pageSize
 *
 * To begin we allocate a byte array of size = chunkSize
 * Whenever a ByteBuf of given size needs to be created we search for the first position
 * in the byte array that has enough empty space to accommodate the requested size and
 * return a (long) handle that encodes this offset information, (this memory segment is then
 * marked as reserved so it is always used by exactly one ByteBuf and no more)
 *
 * 首先，我们分配大小为chunkSize的字节数组，每当需要创建给定大小的ByteBuf时，我们在字节数组中搜索具有足够空间以容纳所请求大小的第一个位置，并返回编码了偏移信息的长句柄。
 * （然后将此段内存段标记为保留，以便始终由一个ByteBuf使用）
 *
 * For simplicity all sizes are normalized according to {@link PoolArena#size2SizeIdx(int)} method.
 * This ensures that when we request for memory segments of size > pageSize the normalizedCapacity
 * equals the next nearest size in {@link SizeClasses}.
 *
 * 为了简单，所有大小都根据 PoolArena#size2SizeIdx(int)方法标准化。
 * 这确保了当我们请求大小大于pageSize内存段时，normalizedCapacity（标准化容量）等于SizeClasses中下一个最近的大小
 *
 *  A chunk has the following layout:
 *
 *     /-----------------\
 *     | run             |
 *     |                 |
 *     |                 |
 *     |-----------------|
 *     | run             |
 *     |                 |
 *     |-----------------|
 *     | unalloctated    |
 *     | (freed)         |
 *     |                 |
 *     |-----------------|
 *     | subpage         |
 *     |-----------------|
 *     | unallocated     |
 *     | (freed)         |
 *     | ...             |
 *     | ...             |
 *     | ...             |
 *     |                 |
 *     |                 |
 *     |                 |
 *     \-----------------/
 *
 *
 * handle:
 * -------
 * a handle is a long number, the bit layout of a run looks like:
 * 句柄 是一个长数字，运行的位布局如下
 * oooooooo ooooooos ssssssss ssssssue bbbbbbbb bbbbbbbb bbbbbbbb bbbbbbbb
 *
 * o: runOffset (page offset in the chunk), 15bit   // 块中页面偏移量
 * s: size (number of pages) of this run, 15bit     // 本次运行大小 （页面数量）。
 * u: isUsed?, 1bit                                 //
 * e: isSubpage?, 1bit                              // 是否是子页 1位
 * b: bitmapIdx of subpage, zero if it's not subpage, 32bit // 子页面的bitmap.idx，如果不是子页面，则为0，32位
 *
 * runsAvailMap: 运行有效映射
 * ------
 * a map which manages all runs (used and not in used).
 * 管理所有运行（已使用和未使用）的映射
 * For each run, the first runOffset and last runOffset are stored in runsAvailMap.
 * 对于每次运行，第一个 runOffset 和 最后一个 runOffset 都存在在 runsAvailMap
 * key: runOffset
 * value: handle
 *
 * runsAvail:
 * 运行有效
 * ----------
 * an array of {@link PriorityQueue}.
 * PriorityQueue 的一个数组
 *
 * Each queue manages same size of runs.
 * 每个队列管理相同大小的 运行
 *
 * Runs are sorted by offset, so that we always allocate runs with smaller offset.
 * 运行按照偏移量排序，因此我们总是分配具有较小偏移量的运行。
 *
 * Algorithm:
 * 算法
 * ----------
 *
 *   As we allocate runs, we update values stored in runsAvailMap and runsAvail so that the property is maintained.
 *   在分配运行时，我们更新存储在 runsAvailMap 和 runsAvail中的值，以便维护该属性
 *
 * Initialization -
 *  In the beginning we store the initial run which is the whole chunk.
 *  The initial run:
 *  runOffset = 0
 *  size = chunkSize
 *  isUsed = no
 *  isSubpage = no
 *  bitmapIdx = 0
 *  以上是初始化状态
 *
 * Algorithm: [allocateRun(size)]
 * 分配运行
 * ----------
 * 1) find the first avail run using in runsAvails according to size
 * 2) if pages of run is larger than request pages then split it, and save the tailing run
 *    for later using
 *
 * 1.根据大小在runsAvails查找第一个可用运行
 * 2.如果运行的页面大于请求页面，则将其拆分。保存在run尾部以供以后使用
 *
 * Algorithm: [allocateSubpage(size)]
 * 算法：分配子页
 * ----------
 * 1) find a not full subpage according to size.
 *    if it already exists just return, otherwise allocate a new PoolSubpage and call init()
 *    note that this subpage object is added to subpagesPool in the PoolArena when we init() it
 * 2) call subpage.allocate()
 *
 * 1.根据大小查找不完整的子页面，如果它已经存在，则返回，否则分配一个新的 PoolSubpage 并调用 init()。注意，当我们初始化它时，这个子页面对象被添加到
 * PoolArena的子页面池中
 * 2.调用子页面的分配
 *
 * Algorithm: [free(handle, length, nioBuffer)]
 * 算法，释放空间
 * ----------
 * 1) if it is a subpage, return the slab back into this subpage
 * 2) if the subpage is not used or it is a run, then start free this run
 * 3) merge continuous avail runs
 * 4) save the merged run
 *
 * 1.如果是子页面，将。。返回到此子页面
 * 2.如果子页面未使用或者正在运行，开始释放这个run
 * 3.合并连续可用运行
 * 4.保存并且合并run
 *
 * 注意： PoolChunk 被设计为满二叉树
 * {@link PoolChunk} 的初始化是交给 {@link PoolArena} 来进行处理的
 *
 * 划分管理页面
 */
final class PoolChunk<T> implements PoolChunkMetric {
    //块中页 位占用长度
    private static final int SIZE_BIT_LENGTH = 15;
    // 是否被使用使用
    private static final int INUSED_BIT_LENGTH = 1;
    //是否时 子页面
    private static final int SUBPAGE_BIT_LENGTH = 1;
    //子页面的bitmap.idx，如果不是子页面，则为0，32位
    private static final int BITMAP_IDX_BIT_LENGTH = 32;

    /**
     * 移位量
     */
    //是否是子页
    static final int IS_SUBPAGE_SHIFT = BITMAP_IDX_BIT_LENGTH;
    //是否使用
    static final int IS_USED_SHIFT = SUBPAGE_BIT_LENGTH + IS_SUBPAGE_SHIFT;
    //本次运行 （页面数量）。
    static final int SIZE_SHIFT = INUSED_BIT_LENGTH + IS_USED_SHIFT;
    //块中页面偏移量
    static final int RUN_OFFSET_SHIFT = SIZE_BIT_LENGTH + SIZE_SHIFT;
    //chunk 所属的 池域
    final PoolArena<T> arena;

    //如果没有偏移量，则与memory 是一样的，是计算memory的基础对象
    final Object base;
    //存储的数据【内存块】
    final T memory;

    /**
     * 是否池化，主要是针对
     */
    final boolean unpooled;

    /**
     * store the first page and last page of each avail run
     * 存储每次可用运行的第一页和最后一页
     *
     * key runOffset
     * value : handle
     */
    private final LongLongHashMap runsAvailMap;

    /**
     * manage all avail runs
     * 管理所有可用运行,有序队列 [0...nPages]
     * {@link SizeClasses#nPSizes} 是isMultiPageSize为true 的总数 = runsAvail.length
     * 0 1pages [递增队列]
     *
     * 1 2pages [递增队列]
     */
    private final LongPriorityQueue[] runsAvail;

    private final ReentrantLock runsAvailLock;

    /**
     * manage all subpages in this chunk
     * 管理此块中所有的子页面， Netty中并没有page的定义，直接使用PoolSubpage表示
     * subpages 的长度为  chunkSize >> pageShifts
     *
     */
    private final PoolSubpage<T>[] subpages;

    /**
     * Accounting of pinned memory – memory that is currently in use by ByteBuf instances.
     * bytebuf实例当前使用的内存，
     */
    private final LongCounter pinnedBytes = PlatformDependent.newLongCounter();
    /**
     * 页面大小
     */
    private final int pageSize;
    /**
     * 页面位移量
     */
    private final int pageShifts;

    //块大小
    private final int chunkSize;

    // Use as cache for ByteBuffer created from the memory. These are just duplicates and so are only a container
    // around the memory itself. These are often needed for operations within the Pooled*ByteBuf and so
    // may produce extra GC, which can be greatly reduced by caching the duplicates.
    //
    // This may be null if the PoolChunk is unpooled as pooling the ByteBuffer instances does not make any sense here.

    /**
     * 从内存创建的ByteBuffer 的一个缓存对象。 这些只是副本，因此只是内存本身周围的一个容器。
     *
     * 这些通常用于Pooled * ByteBuf 中的操作等。可能产生额外的 GC，通过缓存副本可以大大减少。
     *
     * 如果PoolChunk未被激活，则这可能为null，因为在这里池化ByteBuffer实例没有任何意义
     */
    private final Deque<ByteBuffer> cachedNioBuffers;

    //空闲字节数
    int freeBytes;


    /**
     * poolChunk 的 数据结构
     */
    //该块 属于的 PoolChunkList
    PoolChunkList<T> parent;
    //链表节点维护
    PoolChunk<T> prev;
    PoolChunk<T> next;

    // TODO: Test if adding padding helps under contention
    //private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /**
     *
     * @param arena
     * @param base ByteBuffer
     * @param memory ByteBuffer
     * @param pageSize 8192
     * @param pageShifts 13  用于快速计算
     * @param chunkSize 4MB
     * @param maxPageIdx 32(在PoolArena中计算出来的nPSizes 字段值，也就是二维表中，总的页面数量)
     */
    @SuppressWarnings("unchecked")
    PoolChunk(PoolArena<T> arena, Object base, T memory, int pageSize, int pageShifts, int chunkSize, int maxPageIdx) {
        unpooled = false;
        this.arena = arena;
        this.base = base;
        this.memory = memory;
        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.chunkSize = chunkSize;
        freeBytes = chunkSize;
        //根据默认值 来创建 每种 类型的页面对应的 队列（pageIdx2SizeTab.length）
        runsAvail = newRunsAvailqueueArray(maxPageIdx);
        runsAvailLock = new ReentrantLock();
        runsAvailMap = new LongLongHashMap(-1);
        //创建页面，总块的大小/页面大小
        subpages = new PoolSubpage[chunkSize >> pageShifts];

        //insert initial run, offset = 0, pages = chunkSize / pageSize
        // 插入初始运行，偏移量=0，pages=chunkSize/pageSize
        int pages = chunkSize >> pageShifts;
        long initHandle = (long) pages << SIZE_SHIFT;
        //初始化 可运行 数据到队列中。
        insertAvailRun(0, pages, initHandle);

        cachedNioBuffers = new ArrayDeque<ByteBuffer>(8);
    }

    /** Creates a special chunk that is not pooled.
     * 创建 一个 非池化的特殊块。也就是不存在内存画布，只维护  base和 memory.也就是真实的底层存储
     * */
    PoolChunk(PoolArena<T> arena, Object base, T memory, int size) {
        unpooled = true;
        this.arena = arena;
        this.base = base;
        this.memory = memory;
        pageSize = 0;
        pageShifts = 0;
        runsAvailMap = null;
        runsAvail = null;
        runsAvailLock = null;
        subpages = null;
        chunkSize = size;
        cachedNioBuffers = null;
    }

    private static LongPriorityQueue[] newRunsAvailqueueArray(int size) {
        LongPriorityQueue[] queueArray = new LongPriorityQueue[size];
        for (int i = 0; i < queueArray.length; i++) {
            queueArray[i] = new LongPriorityQueue();
        }
        return queueArray;
    }

    /**
     * 添加 可用的 运行 到 runsAvail 和 runsAvailMap中
     * 插入可用运行,在对象初始化，释放内存，分配大内存需要切割时调用
     * @param runOffset 运行偏移量，高15位
     * @param pages  本次插入的运行页面数量，高30位的 低15位 （pages 表示 页面大小的 倍数）
     * @param handle 根据 pages 和  runOffset 计算 出来的 对应的 位标识数值
     */
    private void insertAvailRun(int runOffset, int pages, long handle) {
        //这里是512 倍大小的页面，其实就是请求4MB,大小对应的 pageIdx。也就是31（也就是根据 需要的页面数量 从 二维表中计算 pageIdx）
        int pageIdxFloor = arena.pages2pageIdxFloor(pages);
        //对应页面数量的 队列
        LongPriorityQueue queue = runsAvail[pageIdxFloor];
        //该队列中保存的handle 表示的 size 位，的值都是 pages
        queue.offer(handle);

        //insert first page of run
        //插入运行的第一页
        insertAvailRun0(runOffset, handle);
        if (pages > 1) {
            //insert last page of run
            //插入运行的最后一页
            insertAvailRun0(lastPage(runOffset, pages), handle);
        }
    }

    /**
     * 保存到runsAvailMap .map中的值都是可用于分配的。通过偏移量，查找 handle,找到对应的handle 就知道还有多少页。
     * @param runOffset
     * @param handle
     */
    private void insertAvailRun0(int runOffset, long handle) {
        long pre = runsAvailMap.put(runOffset, handle);
        assert pre == -1;
    }

    private void removeAvailRun(long handle) {
        int pageIdxFloor = arena.pages2pageIdxFloor(runPages(handle));
        runsAvail[pageIdxFloor].remove(handle);
        removeAvailRun0(handle);
    }

    /**
     * （从runsAvailMa中）删除可用 的运行
     * @param handle
     */
    private void removeAvailRun0(long handle) {
        //获取 运行偏移量
        int runOffset = runOffset(handle);
        //获取 对应运行偏移量 对应的页面数量
        int pages = runPages(handle);
        // 删除运行的第一个页面
        //remove first page of run
        runsAvailMap.remove(runOffset);
        //如果 pages >1 表示的是 需要申请连续的块，
        if (pages > 1) {
            //remove last page of run //删除运行的最后一页（因为天剑的时候，也是添加第一个页面，添加最后一个页面）
            runsAvailMap.remove(lastPage(runOffset, pages));
        }
    }

    /**
     * 计算最后一个页
     * @param runOffset
     * @param pages
     * @return
     */
    private static int lastPage(int runOffset, int pages) {
        return runOffset + pages - 1;
    }

    /**
     * 根据运行偏移量，计算运行句柄
     * @param runOffset
     * @return
     */
    private long getAvailRunByOffset(int runOffset) {
        return runsAvailMap.get(runOffset);
    }

    @Override
    public int usage() {
        final int freeBytes;
        if (this.unpooled) {
            freeBytes = this.freeBytes;
        } else {
            runsAvailLock.lock();
            try {
                freeBytes = this.freeBytes;
            } finally {
                runsAvailLock.unlock();
            }
        }
        return usage(freeBytes);
    }

    private int usage(int freeBytes) {
        if (freeBytes == 0) {
            return 100;
        }

        int freePercentage = (int) (freeBytes * 100L / chunkSize);
        if (freePercentage == 0) {
            return 99;
        }
        return 100 - freePercentage;
    }

    /**
     * 核心方法，执行分配，计算内存句柄，
     *
     * 根据 分配空间大小不同，有分为 allocateSubpage 和 allocateRun
     * @param buf 创建 还为初始化的 池对象
     * @param reqCapacity 请求分配容量
     * @param sizeIdx 请求分配容量 的标准大小 对应的二维表索引
     * @param cache 线程本地变量
     * @return 当且仅当分配成功 ，返回成功
     */
    boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int sizeIdx, PoolThreadCache cache) {
        final long handle;

        if (sizeIdx <= arena.smallMaxSizeIdx) {
            // small
            handle = allocateSubpage(sizeIdx);
            if (handle < 0) {
                return false;
            }
            assert isSubpage(handle);
        } else {
            // normal
            // runSize must be multiple of pageSize
            // runSize必须是pageSize的倍数,这里可以从二维表格中查出，除了small之外，肯定其余的sizeIdx 对应的大小都是pageSize的倍数
            int runSize = arena.sizeIdx2size(sizeIdx);
            //分配运行时
            handle = allocateRun(runSize);
            if (handle < 0) {
                return false;
            }
            assert !isSubpage(handle);
        }

        ByteBuffer nioBuffer = cachedNioBuffers != null? cachedNioBuffers.pollLast() : null;
        //分配完毕之后，进行字节缓冲区初始化
        initBuf(buf, nioBuffer, handle, reqCapacity, cache);
        return true;
    }


    /**
     * 分配一个运行 句柄用来表示内存 所在的句柄
     * 获取可用运行句柄。（同时从可用运行标识中删除）
     * 如果该可用运行句柄 表示的页数>本次运行需要的业务，则从新计算下次可用运行，并添加到runsAvail 中
     *
     *
     * @param runSize 运行大小【满足二维表格中的 isMultiPageSize==1】
     * @return 返回 运行大小 对应的运行句柄
     */
    private long allocateRun(int runSize) {
        int pages = runSize >> pageShifts;
        //查找页面大小倍数 对应的 查找表索引
        int pageIdx = arena.pages2pageIdx(pages);

        runsAvailLock.lock();
        try {
            //find first queue which has at least one big enough run
            // 查找至少有一个足够大的运行的第一个队列，查找到可以满足本次分配的 页索引
            int queueIdx = runFirstBestFit(pageIdx);
            if (queueIdx == -1) {
                return -1;
            }
            //get run with min offset in this queue【在队里中获取最小的运行偏移量】
            LongPriorityQueue queue = runsAvail[queueIdx];
            long handle = queue.poll();

            assert handle != LongPriorityQueue.NO_VALUE && !isUsed(handle) : "invalid handle: " + handle;
            // 删除 handle 对应 的run(从runsAvailMap中删除)
            removeAvailRun0(handle);

            if (handle != -1) {
                //并根据pages（需要页数）计算本次运行句柄。
                handle = splitLargeRun(handle, pages);
            }
            //计算运行句柄 对应的 内存大小
            int pinnedSize = runSize(pageShifts, handle);
            //空闲字节减少
            freeBytes -= pinnedSize;
            return handle;
        } finally {
            runsAvailLock.unlock();
        }
    }

    /**
     * 计算出 能满足请求容量的最小标准大小（elemSize）。计算出来runSize 是 pageSize和elemSize的最小公倍数。可以进行多次分配这种容量大小的请求
     * @param sizeIdx
     * @return
     */
    private int calculateRunSize(int sizeIdx) {
        int maxElements = 1 << pageShifts - SizeClasses.LOG2_QUANTUM;
        int runSize = 0;
        int nElements;

        final int elemSize = arena.sizeIdx2size(sizeIdx);

        //find lowest common multiple of pageSize and elemSize
        //查找pageSize和elemSize的最小公倍数
        do {
            runSize += pageSize;
            nElements = runSize / elemSize;
        } while (nElements < maxElements && runSize != nElements * elemSize);

        while (nElements > maxElements) {
            runSize -= pageSize;
            nElements = runSize / elemSize;
        }

        assert nElements > 0;
        assert runSize <= chunkSize;
        assert runSize >= elemSize;

        return runSize;
    }

    /**
     * 查找出最小的pageIdx （pageIdx 有对应 运行队列的）
     * @param pageIdx
     * @return
     */
    private int runFirstBestFit(int pageIdx) {
        //如果 还未进行初始化，返回最大页的索引
        if (freeBytes == chunkSize) {
            return arena.nPSizes - 1;
        }
        for (int i = pageIdx; i < arena.nPSizes; i++) {
            LongPriorityQueue queue = runsAvail[i];
            if (queue != null && !queue.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 拆分大的运行，返回对应需要页数的运行句柄
     * @param handle 页面标识 位图标示
     * @param needPages 需要的页面数量
     * @return 从handle 中分配 出 needPages 后，重新计算的handle
     *
     * 1.计算handle 的size 位，isUsed 位
     * 2.保存 handle 剩余 可用的信息（通过 insertAvailRun 方法）【如果有的话】
     * 3.返回从 handle 中 分配出 needPages 后的 handle
     */
    private long splitLargeRun(long handle, int needPages) {
        assert needPages > 0;
        //此handle 标识的 页面数量需要大于申请的页面数量
        int totalPages = runPages(handle);
        assert needPages <= totalPages;
        //剩余可用页面
        int remPages = totalPages - needPages;

        //剩余页面大于0（remainder）
        if (remPages > 0) {
            //获取到 运行 现在的开始偏移量
            int runOffset = runOffset(handle);

            // keep track of trailing unused pages for later use
            // 跟踪尾随未使用的页面以备日后使用（当前运行偏移量+将要被使用的，作为开始偏移量，结束偏移量保持不变）
            int availOffset = runOffset + needPages;
            long availRun = toRunHandle(availOffset, remPages, 0);
            // 保存 availRun 可用运行
            insertAvailRun(availOffset, remPages, availRun);

            // not avail，返回 本次 需要needPages 后 的 运行，并标记为正在使用。
            return toRunHandle(runOffset, needPages, 1);
        }
        //剩余页面不大于0，直接标记为使用
        //mark it as used
        handle |= 1L << IS_USED_SHIFT;
        return handle;
    }

    /**
     * Create / initialize a new PoolSubpage of normCapacity. Any PoolSubpage created / initialized here is added to
     * subpage pool in the PoolArena that owns this PoolChunk
     *
     * 创建/初始化normCapacity的新PoolSubpage。在此处创建/初始化的任何PoolSubpage都会添加到拥有此PoolChunk的PoolArena中的子页面池中
     *
     * @param sizeIdx sizeIdx of normalized size
     *
     * @return index in memoryMap
     */
    private long allocateSubpage(int sizeIdx) {
        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.
        PoolSubpage<T> head = arena.findSubpagePoolHead(sizeIdx);
        head.lock();
        try {
            //allocate a new run, runSize(8192)，计算sizeIdx对应的elementSize 与 pageSize 的最小公倍数
            int runSize = calculateRunSize(sizeIdx);
            //runSize must be multiples of pageSize， 根据本次运行大小计算运行句柄
            long runHandle = allocateRun(runSize);
            if (runHandle < 0) {
                return -1;
            }
            //runOffset(0)，本次运行的的偏移量
            int runOffset = runOffset(runHandle);
            assert subpages[runOffset] == null;
            //元素大小
            int elemSize = arena.sizeIdx2size(sizeIdx);
            //根据runSize 和 elemSize 可计算出子页能存储多少个元素
            PoolSubpage<T> subpage = new PoolSubpage<T>(head, this, pageShifts, runOffset,
                               runSize(pageShifts, runHandle), elemSize);

            subpages[runOffset] = subpage;
            //计算本次在分配使用的的bitmap (子页使用位图来表示可以已分配的元素个数)
            return subpage.allocate();
        } finally {
            head.unlock();
        }
    }

    /**
     * Free a subpage or a run of pages When a subpage is freed from PoolSubpage, it might be added back to subpage pool
     * of the owning PoolArena. If the subpage pool in PoolArena has at least one other PoolSubpage of given elemSize,
     * we can completely free the owning Page so it is available for subsequent allocations
     *
     * @param handle handle to free
     */
    void free(long handle, int normCapacity, ByteBuffer nioBuffer) {
        int runSize = runSize(pageShifts, handle);
        if (isSubpage(handle)) {
            int sizeIdx = arena.size2SizeIdx(normCapacity);
            PoolSubpage<T> head = arena.findSubpagePoolHead(sizeIdx);

            int sIdx = runOffset(handle);
            PoolSubpage<T> subpage = subpages[sIdx];

            // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
            // This is need as we may add it back and so alter the linked-list structure.
            head.lock();
            try {
                assert subpage != null && subpage.doNotDestroy;
                if (subpage.free(head, bitmapIdx(handle))) {
                    //the subpage is still used, do not free it
                    return;
                }
                assert !subpage.doNotDestroy;
                // Null out slot in the array as it was freed and we should not use it anymore.
                subpages[sIdx] = null;
            } finally {
                head.unlock();
            }
        }

        //start free run
        runsAvailLock.lock();
        try {
            // collapse continuous runs, successfully collapsed runs
            // will be removed from runsAvail and runsAvailMap
            long finalRun = collapseRuns(handle);

            //set run as not used
            finalRun &= ~(1L << IS_USED_SHIFT);
            //if it is a subpage, set it to run
            finalRun &= ~(1L << IS_SUBPAGE_SHIFT);

            insertAvailRun(runOffset(finalRun), runPages(finalRun), finalRun);
            freeBytes += runSize;
        } finally {
            runsAvailLock.unlock();
        }

        if (nioBuffer != null && cachedNioBuffers != null &&
            cachedNioBuffers.size() < PooledByteBufAllocator.DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK) {
            cachedNioBuffers.offer(nioBuffer);
        }
    }

    private long collapseRuns(long handle) {
        return collapseNext(collapsePast(handle));
    }

    private long collapsePast(long handle) {
        for (;;) {
            int runOffset = runOffset(handle);
            int runPages = runPages(handle);

            long pastRun = getAvailRunByOffset(runOffset - 1);
            if (pastRun == -1) {
                return handle;
            }

            int pastOffset = runOffset(pastRun);
            int pastPages = runPages(pastRun);

            //is continuous
            if (pastRun != handle && pastOffset + pastPages == runOffset) {
                //remove past run
                removeAvailRun(pastRun);
                handle = toRunHandle(pastOffset, pastPages + runPages, 0);
            } else {
                return handle;
            }
        }
    }

    private long collapseNext(long handle) {
        for (;;) {
            int runOffset = runOffset(handle);
            int runPages = runPages(handle);

            long nextRun = getAvailRunByOffset(runOffset + runPages);
            if (nextRun == -1) {
                return handle;
            }

            int nextOffset = runOffset(nextRun);
            int nextPages = runPages(nextRun);

            //is continuous
            if (nextRun != handle && runOffset + runPages == nextOffset) {
                //remove next run
                removeAvailRun(nextRun);
                handle = toRunHandle(runOffset, runPages + nextPages, 0);
            } else {
                return handle;
            }
        }
    }

    private static long toRunHandle(int runOffset, int runPages, int inUsed) {
        return (long) runOffset << RUN_OFFSET_SHIFT
               | (long) runPages << SIZE_SHIFT
               | (long) inUsed << IS_USED_SHIFT;
    }

    void initBuf(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity,
                 PoolThreadCache threadCache) {
        if (isSubpage(handle)) {
            initBufWithSubpage(buf, nioBuffer, handle, reqCapacity, threadCache);
        } else {
            int maxLength = runSize(pageShifts, handle);
            buf.init(this, nioBuffer, handle, runOffset(handle) << pageShifts,
                    reqCapacity, maxLength, arena.parent.threadCache());
        }
    }

    /**
     * 使用子页来进行 缓冲区的初始化
     * @param buf
     * @param nioBuffer
     * @param handle
     * @param reqCapacity
     * @param threadCache
     */
    void initBufWithSubpage(PooledByteBuf<T> buf, ByteBuffer nioBuffer, long handle, int reqCapacity,
                            PoolThreadCache threadCache) {
        int runOffset = runOffset(handle);
        int bitmapIdx = bitmapIdx(handle);

        PoolSubpage<T> s = subpages[runOffset];
        assert s.doNotDestroy;
        assert reqCapacity <= s.elemSize : reqCapacity + "<=" + s.elemSize;

        int offset = (runOffset << pageShifts) + bitmapIdx * s.elemSize;
        buf.init(this, nioBuffer, handle, offset, reqCapacity, s.elemSize, threadCache);
    }

    void incrementPinnedMemory(int delta) {
        assert delta > 0;
        pinnedBytes.add(delta);
    }

    void decrementPinnedMemory(int delta) {
        assert delta > 0;
        pinnedBytes.add(-delta);
    }

    @Override
    public int chunkSize() {
        return chunkSize;
    }

    @Override
    public int freeBytes() {
        if (this.unpooled) {
            return freeBytes;
        }
        runsAvailLock.lock();
        try {
            return freeBytes;
        } finally {
            runsAvailLock.unlock();
        }
    }

    public int pinnedBytes() {
        return (int) pinnedBytes.value();
    }

    @Override
    public String toString() {
        final int freeBytes;
        if (this.unpooled) {
            freeBytes = this.freeBytes;
        } else {
            runsAvailLock.lock();
            try {
                freeBytes = this.freeBytes;
            } finally {
                runsAvailLock.unlock();
            }
        }

        return new StringBuilder()
                .append("Chunk(")
                .append(Integer.toHexString(System.identityHashCode(this)))
                .append(": ")
                .append(usage(freeBytes))
                .append("%, ")
                .append(chunkSize - freeBytes)
                .append('/')
                .append(chunkSize)
                .append(')')
                .toString();
    }

    void destroy() {
        arena.destroyChunk(this);
    }

    /**
     * 计算 页面 在该块中 的页面偏移量，高15位。64位右移49位
     * @param handle
     * @return
     */
    static int runOffset(long handle) {
        return (int) (handle >> RUN_OFFSET_SHIFT);
    }

    /**
     * 根据运行句柄 计算 运行大小
     * @param pageShifts
     * @param handle
     * @return
     */
    static int runSize(int pageShifts, long handle) {
        return runPages(handle) << pageShifts;
    }

    /**
     * 计算 该 handle 对应的 标识页面数量的 值，也就是 高30位中的低15位 。也就是页面数量
     * @param handle
     * @return
     */
    static int runPages(long handle) {
        return (int) (handle >> SIZE_SHIFT & 0x7fff);
    }

    static boolean isUsed(long handle) {
        return (handle >> IS_USED_SHIFT & 1) == 1L;
    }

    static boolean isRun(long handle) {
        return !isSubpage(handle);
    }

    //查看子页标识位
    static boolean isSubpage(long handle) {
        return (handle >> IS_SUBPAGE_SHIFT & 1) == 1L;
    }

    /**
     *  子页面的bitmap.idx，如果不是子页面，则为0，32位
      */
    static int bitmapIdx(long handle) {
        return (int) handle;
    }
}
