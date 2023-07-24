/*
 * Copyright 2020 The Netty Project
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

import java.util.Arrays;

import static io.netty.buffer.PoolThreadCache.*;

/**
 * SizeClasses requires {@code pageShifts} to be defined prior to inclusion,
 * SizeClasses要求在包含之前定义{@code pageShifts}，
 * and it in turn defines:
 * 并且它逐个定义
 * <p>
 *   LOG2_SIZE_CLASS_GROUP: Log of size class count for each size doubling.
 *          每次大小加倍的 大小类型总数的 对数(每组中 size_class 的个数)。 例如 LOG2_SIZE_CLASS_GROUP = 2, 则 size_class_group 为 4.
 *   LOG2_MAX_LOOKUP_SIZE: Log of max size class in the lookup table.
 *          查找表中 最大的 size class 。 LOG2_MAX_LOOKUP_SIZE=12， 则 max_lookup_size 为 4096
 *
 *   sizeClasses: Complete table of [index, log2Group, log2Delta, nDelta, isMultiPageSize,
 *                 isSubPage, log2DeltaLookup] tuples.
 *     index: Size class index.  大小类型 的索引
 *     log2Group: Log of group base size (no deltas added). 组基本大小的对数 。 例如 log2Gourp 为4. 求解为: 组基础大小为32. 因为log2(32)=4
 *     log2Delta: Log of delta to previous size class.  增量的对数。 例如 log2Delta 为4，求解，delta 为 32.
 *     nDelta: Delta multiplier.  增量的倍数
 *     isMultiPageSize: 'yes' if a multiple of the page size, 'no' otherwise.  如果求得size 为page size 的倍数。 则返回true,否则，返回false.
 *     isSubPage: 'yes' if a subpage size class, 'no' otherwise.  如果是 子页大小类型，则返回true，否则，返回false.
 *     log2DeltaLookup: Same as log2Delta if a lookup table size class, 'no'  . 如果是一个查找表大小类型，返回true,否则，返回false.
 *                      otherwise.
 * <p>
 *   nSubpages: Number of subpages size classes.  是 子页 sizeclass 的数量
 *   nSizes: Number of size classes.     sizeClass 的 数量
 *   nPSizes: Number of size classes that are multiples of pageSize.   是pageSize 整数倍 的 sizeClass 的 数量
 *
 *   smallMaxSizeIdx: Maximum small size class index.  small 大小类型 的最大索引值
 *
 *   lookupMaxClass（lookupMaxSize）: Maximum size class included in lookup table.  包含在 查找表中 的size class 的最大值 (是lookupMaxSize属性)
 *   log2NormalMinClass: Log of minimum normal size class.
 * <p>
 *   The first size class and spacing are 1 << LOG2_QUANTUM.
 *
 *   第一个大小类型 和 间隔都是 16
 *
 *   Each group has 1 << LOG2_SIZE_CLASS_GROUP of size classes.
 *
 *   每个组有 4个 大小类型，间隔大小是 16
 *
 *   size = 1 << log2Group + nDelta * (1 << log2Delta)
 *
 *   size 的计算方式  1<<log2Group+nDelta<< log2Delta
 *
 *   The first size class has an unusual encoding, because the size has to be
 *   split between group and delta*nDelta.
 *
 *   If pageShift = 13, sizeClasses looks like this:
 *
 *   (index, log2Group, log2Delta, nDelta, isMultiPageSize, isSubPage, log2DeltaLookup)
 * <p>
 *   ( 0,     4,        4,         0,       no,             yes,        4)
 *   ( 1,     4,        4,         1,       no,             yes,        4)
 *   ( 2,     4,        4,         2,       no,             yes,        4)
 *   ( 3,     4,        4,         3,       no,             yes,        4)
 * <p>
 *   ( 4,     6,        4,         1,       no,             yes,        4)
 *   ( 5,     6,        4,         2,       no,             yes,        4)
 *   ( 6,     6,        4,         3,       no,             yes,        4)
 *   ( 7,     6,        4,         4,       no,             yes,        4)
 * <p>
 *   ( 8,     7,        5,         1,       no,             yes,        5)
 *   ( 9,     7,        5,         2,       no,             yes,        5)
 *   ( 10,    7,        5,         3,       no,             yes,        5)
 *   ( 11,    7,        5,         4,       no,             yes,        5)
 *   ...
 *   ...
 *   ( 72,    23,       21,        1,       yes,            no,        no)
 *   ( 73,    23,       21,        2,       yes,            no,        no)
 *   ( 74,    23,       21,        3,       yes,            no,        no)
 *   ( 75,    23,       21,        4,       yes,            no,        no)
 * <p>
 *   ( 76,    24,       22,        1,       yes,            no,        no)
 *
 *
 * @see PoolArena
 */
abstract class SizeClasses implements SizeClassesMetric {

    static final int LOG2_QUANTUM = 4;

    private static final int LOG2_SIZE_CLASS_GROUP = 2;

    /**
     * 对应的 max_lookup_size 是 4096。
     * 影响
     * {@link #LOG2_DELTA_LOOKUP_IDX} 的表示。大小超过4096后，标识为0，也就是no.
     */
    private static final int LOG2_MAX_LOOKUP_SIZE = 12;

    private static final int INDEX_IDX = 0;
    private static final int LOG2GROUP_IDX = 1;
    private static final int LOG2DELTA_IDX = 2;
    private static final int NDELTA_IDX = 3;
    private static final int PAGESIZE_IDX = 4;
    private static final int SUBPAGE_IDX = 5;
    private static final int LOG2_DELTA_LOOKUP_IDX = 6;

    private static final byte no = 0, yes = 1;

    /**
     * 默认是8K ，8192，对应的 log2 = 13
     */
    protected final int pageSize;
    /**
     * 也偏移量，如果是8k,对应的大小就是13
     */
    protected final int pageShifts;

    /**
     * 默认大小是16M  16777216， 该值对应的log2 为 24
     */
    protected final int chunkSize;
    protected final int directMemoryCacheAlignment;

    /**
     * sizeClass 的 个数
     */
    final int nSizes;
    //子页数量
    final int nSubpages;
    //页数量
    final int nPSizes;

    /**
     * 查找表中 最大的 size 值
     * 也就是{@link #LOG2_DELTA_LOOKUP_IDX}的值 不为0的 最大 size值。 在以pageShift=13为例
     * lookupMaxSize 的值 为 4096
     */
    final int lookupMaxSize;

    /**
     *  small 大小类型 的最大索引值，
     *  按照16M,8K,来计算的值为。
     *  small的在查找表中的最大索引值为38，对应的size 大小为28K
     */
    final int smallMaxSizeIdx;

    //页查找大小表
    private final int[] pageIdx2sizeTab;

    // lookup table for sizeIdx <= smallMaxSizeIdx
    /**
     * 为索引 <= smallMaxSizeIdx 提供的查找表
     */
    private final int[] sizeIdx2sizeTab;

    // lookup table used for size <= lookupMaxClass
    // spacing is 1 << LOG2_QUANTUM, so the size of array is lookupMaxClass >> LOG2_QUANTU
    /**
     * 用于  大小<=lookupMaxClass的查找表
     * 间距为1<<LOG2_QUANTUM (16)，因此数组的大小为lookupMaxClass>>LOG2_QUANTUM
     * 例如：
     * 存储的数据如下：前面表示的是数组索引，后面是查找表对应的索引。 查找表索引11，12 代表的大小为分别为256， 320。 如果求解300大小对应的查找表索引值。可以通过
     * （300-1）>>4 来快速请的在 间距为16的数组中的索引。 该索引对应的值就是查找表的索引。
     * 14-》11
     * 15-》11
     *
     * 16-》12
     * 17-》12
     * 18-》12
     * 19-》12
     */
    private final int[] size2idxTab;

    protected SizeClasses(int pageSize, int pageShifts, int chunkSize, int directMemoryCacheAlignment) {
        int group = log2(chunkSize) - LOG2_QUANTUM - LOG2_SIZE_CLASS_GROUP + 1;

        //generate size classes
        //[index, log2Group, log2Delta, nDelta, isMultiPageSize, isSubPage, log2DeltaLookup]
        short[][] sizeClasses = new short[group << LOG2_SIZE_CLASS_GROUP][7];

        int normalMaxSize = -1;
        // 计算大小的索引值
        int nSizes = 0;
        int size = 0;

        int log2Group = LOG2_QUANTUM;
        int log2Delta = LOG2_QUANTUM;
        int ndeltaLimit = 1 << LOG2_SIZE_CLASS_GROUP; // 限制 ，每4个为一组

        //First small group, nDelta start at 0.
        //first size class is 1 << LOG2_QUANTUM
        for (int nDelta = 0; nDelta < ndeltaLimit; nDelta++, nSizes++) {
            short[] sizeClass = newSizeClass(nSizes, log2Group, log2Delta, nDelta, pageShifts);
            sizeClasses[nSizes] = sizeClass;
            size = sizeOf(sizeClass, directMemoryCacheAlignment);
        }

        log2Group += LOG2_SIZE_CLASS_GROUP;

        //All remaining groups, nDelta start at 1.

        //所有剩余的组，nDelta从1开始。
        for (; size < chunkSize; log2Group++, log2Delta++) {
            for (int nDelta = 1; nDelta <= ndeltaLimit && size < chunkSize; nDelta++, nSizes++) {
                short[] sizeClass = newSizeClass(nSizes, log2Group, log2Delta, nDelta, pageShifts);
                sizeClasses[nSizes] = sizeClass;
                size = normalMaxSize = sizeOf(sizeClass, directMemoryCacheAlignment);
            }
        }

        //chunkSize must be normalMaxSize
        assert chunkSize == normalMaxSize;

        int smallMaxSizeIdx = 0;
        int lookupMaxSize = 0;
        int nPSizes = 0;
        int nSubpages = 0;
        for (int idx = 0; idx < nSizes; idx++) {
            short[] sz = sizeClasses[idx];
            if (sz[PAGESIZE_IDX] == yes) {
                nPSizes++;
            }
            if (sz[SUBPAGE_IDX] == yes) {
                nSubpages++;
                smallMaxSizeIdx = idx;
            }
            if (sz[LOG2_DELTA_LOOKUP_IDX] != no) {
                lookupMaxSize = sizeOf(sz, directMemoryCacheAlignment);
            }
        }
        this.smallMaxSizeIdx = smallMaxSizeIdx;
        this.lookupMaxSize = lookupMaxSize;
        this.nPSizes = nPSizes;
        this.nSubpages = nSubpages;
        //索引数量
        this.nSizes = nSizes;

        this.pageSize = pageSize;
        this.pageShifts = pageShifts;
        this.chunkSize = chunkSize;
        this.directMemoryCacheAlignment = directMemoryCacheAlignment;



        //generate lookup tables，生成查找表
        sizeIdx2sizeTab = newIdx2SizeTab(sizeClasses, nSizes, directMemoryCacheAlignment);
        pageIdx2sizeTab = newPageIdx2sizeTab(sizeClasses, nSizes, nPSizes, directMemoryCacheAlignment);
        size2idxTab = newSize2idxTab(lookupMaxSize, sizeClasses);

        System.out.println("============sizeClasses[init]===============");
        //[index, log2Group, log2Delta, nDelta, isMultiPageSize, isSubPage, log2DeltaLookup]
        System.out.println("[INDEX_IDX, LOG2GROUP_IDX, LOG2DELTA_IDX,NDELTA_IDX, PAGESIZE_IDX, SUBPAGE_IDX, LOG2_DELTA_LOOKUP_IDX]");
        for(int i=0;i<sizeClasses.length;i++){
            System.out.println(Arrays.toString(sizeClasses[i])+"，size:"+sizeIdx2sizeTab[i]);
        }
        System.out.println("============sizeClasses[end]===============");
    }

    /**
     * 大小类，核心计算方法
     * @param index
     * @param log2Group
     * @param log2Delta
     * @param nDelta
     * @param pageShifts
     * @return
     */
    //calculate size class
    private static short[] newSizeClass(int index, int log2Group, int log2Delta, int nDelta, int pageShifts) {
        short isMultiPageSize;
        if (log2Delta >= pageShifts) {
            isMultiPageSize = yes;
        } else {
            int pageSize = 1 << pageShifts;
            int size = calculateSize(log2Group, nDelta, log2Delta);
            //如果 size 是  1<< pageShifts 的整数倍，返回true,
            isMultiPageSize = size == size / pageSize * pageSize? yes : no;
        }

        int log2Ndelta = nDelta == 0? 0 : log2(nDelta);

        byte remove = 1 << log2Ndelta < nDelta? yes : no;

        int log2Size = log2Delta + log2Ndelta == log2Group? log2Group + 1 : log2Group;
        if (log2Size == log2Group) {
            remove = yes;
        }

        short isSubpage = log2Size < pageShifts + LOG2_SIZE_CLASS_GROUP? yes : no;

        int log2DeltaLookup = log2Size < LOG2_MAX_LOOKUP_SIZE ||
                              log2Size == LOG2_MAX_LOOKUP_SIZE && remove == no
                ? log2Delta : no;

        return new short[] {
                (short) index, (short) log2Group, (short) log2Delta,
                (short) nDelta, isMultiPageSize, isSubpage, (short) log2DeltaLookup
        };
    }

    private static int[] newIdx2SizeTab(short[][] sizeClasses, int nSizes, int directMemoryCacheAlignment) {
        int[] sizeIdx2sizeTab = new int[nSizes];

        for (int i = 0; i < nSizes; i++) {
            short[] sizeClass = sizeClasses[i];
            sizeIdx2sizeTab[i] = sizeOf(sizeClass, directMemoryCacheAlignment);
        }
        return sizeIdx2sizeTab;
    }

    /**
     * 计算大小
     * @param log2Group
     * @param nDelta
     * @param log2Delta
     * @return
     */
    private static int calculateSize(int log2Group, int nDelta, int log2Delta) {
        return (1 << log2Group) + (nDelta << log2Delta);
    }

    /**
     * 计算每个条目的大小，并且进行对齐
     * @param sizeClass
     * @param directMemoryCacheAlignment
     * @return
     */
    private static int sizeOf(short[] sizeClass, int directMemoryCacheAlignment) {
        int log2Group = sizeClass[LOG2GROUP_IDX];
        int log2Delta = sizeClass[LOG2DELTA_IDX];
        int nDelta = sizeClass[NDELTA_IDX];

        int size = calculateSize(log2Group, nDelta, log2Delta);

        return alignSizeIfNeeded(size, directMemoryCacheAlignment);
    }

    private static int[] newPageIdx2sizeTab(short[][] sizeClasses, int nSizes, int nPSizes,
                                            int directMemoryCacheAlignment) {
        int[] pageIdx2sizeTab = new int[nPSizes];
        int pageIdx = 0;
        for (int i = 0; i < nSizes; i++) {
            short[] sizeClass = sizeClasses[i];
            if (sizeClass[PAGESIZE_IDX] == yes) {
                pageIdx2sizeTab[pageIdx++] = sizeOf(sizeClass, directMemoryCacheAlignment);
            }
        }
        return pageIdx2sizeTab;
    }

    private static int[] newSize2idxTab(int lookupMaxSize, short[][] sizeClasses) {
        int[] size2idxTab = new int[lookupMaxSize >> LOG2_QUANTUM];
        int idx = 0;
        int size = 0;

        for (int i = 0; size <= lookupMaxSize; i++) {
            int log2Delta = sizeClasses[i][LOG2DELTA_IDX];
            int times = 1 << log2Delta - LOG2_QUANTUM;

            while (size <= lookupMaxSize && times-- > 0) {
                size2idxTab[idx++] = i;
                size = idx + 1 << LOG2_QUANTUM;
            }
        }
        return size2idxTab;
    }

    /**
     * 从查找表中 找到 大小
     * @param sizeIdx
     * @return
     */
    @Override
    public int sizeIdx2size(int sizeIdx) {
        return sizeIdx2sizeTab[sizeIdx];
    }

    /**
     * 根据 sizeIdx 计算大小
     * @param sizeIdx
     * @return
     */
    @Override
    public int sizeIdx2sizeCompute(int sizeIdx) {
        int group = sizeIdx >> LOG2_SIZE_CLASS_GROUP;
        int mod = sizeIdx & (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        int groupSize = group == 0? 0 :
                1 << LOG2_QUANTUM + LOG2_SIZE_CLASS_GROUP - 1 << group;

        int shift = group == 0? 1 : group;
        int lgDelta = shift + LOG2_QUANTUM - 1;
        int modSize = mod + 1 << lgDelta;

        return groupSize + modSize;
    }

    /**
     * 直接从查找表中返回大小，
     * @param pageIdx
     * @return
     */
    @Override
    public long pageIdx2size(int pageIdx) {
        return pageIdx2sizeTab[pageIdx];
    }

    /**
     * 根据 pageIdx 计算大小。 与{@link #sizeIdx2sizeCompute(int)} 不同的是，一个使用 pageShifts.一个使用LOG2_QUANTUM
     * @param pageIdx
     * @return
     */
    @Override
    public long pageIdx2sizeCompute(int pageIdx) {
        int group = pageIdx >> LOG2_SIZE_CLASS_GROUP;
        int mod = pageIdx & (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        long groupSize = group == 0? 0 :
                1L << pageShifts + LOG2_SIZE_CLASS_GROUP - 1 << group;

        int shift = group == 0? 1 : group;
        int log2Delta = shift + pageShifts - 1;
        int modSize = mod + 1 << log2Delta;

        return groupSize + modSize;
    }

    /**
     * 根据大小计算 对应到大小类中 查找表的索引
     * @param size request size
     * @return
     */
    @Override
    public int size2SizeIdx(int size) {
        if (size == 0) {
            return 0;
        }
        if (size > chunkSize) {
            return nSizes;
        }

        size = alignSizeIfNeeded(size, directMemoryCacheAlignment);

        if (size <= lookupMaxSize) {
            //size-1 / MIN_TINY
            return size2idxTab[size - 1 >> LOG2_QUANTUM];
        }

        int x = log2((size << 1) - 1);
        int shift = x < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1
                ? 0 : x - (LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM);

        int group = shift << LOG2_SIZE_CLASS_GROUP;

        int log2Delta = x < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1
                ? LOG2_QUANTUM : x - LOG2_SIZE_CLASS_GROUP - 1;

        int mod = size - 1 >> log2Delta & (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        return group + mod;
    }


    /**
     * 计算页面 对应到 的页面表的索引
     * @param pages multiples of pageSizes
     *
     * @return
     */
    @Override
    public int pages2pageIdx(int pages) {
        return pages2pageIdxCompute(pages, false);
    }

    /**
     * 计算 页面表的 页面索引。向下取整
     * @param pages multiples of pageSizes ,页面大小的倍数
     *              例如：pages =2 也就是 2倍大小的页面，就是请求实际大小为（2*8129） 大小的 pageIdx
     *
     * @return
     */
    @Override
    public int pages2pageIdxFloor(int pages) {
        return pages2pageIdxCompute(pages, true);
    }

    private int pages2pageIdxCompute(int pages, boolean floor) {
        int pageSize = pages << pageShifts;
        if (pageSize > chunkSize) {
            return nPSizes;
        }

        int x = log2((pageSize << 1) - 1);

        int shift = x < LOG2_SIZE_CLASS_GROUP + pageShifts
                ? 0 : x - (LOG2_SIZE_CLASS_GROUP + pageShifts);

        int group = shift << LOG2_SIZE_CLASS_GROUP;

        int log2Delta = x < LOG2_SIZE_CLASS_GROUP + pageShifts + 1?
                pageShifts : x - LOG2_SIZE_CLASS_GROUP - 1;

        int mod = pageSize - 1 >> log2Delta & (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        int pageIdx = group + mod;

        if (floor && pageIdx2sizeTab[pageIdx] > pages << pageShifts) {
            pageIdx--;
        }

        return pageIdx;
    }

    /**
     * 四舍五入到对齐的最近倍数。
     * @param size
     * @param directMemoryCacheAlignment
     * @return
     */
    // Round size up to the nearest multiple of alignment.
    private static int alignSizeIfNeeded(int size, int directMemoryCacheAlignment) {
        if (directMemoryCacheAlignment <= 0) {
            return size;
        }
        int delta = size & directMemoryCacheAlignment - 1;
        return delta == 0? size : size + directMemoryCacheAlignment - delta;
    }


    /**
     * 规整 请求大小
     * @param size request size
     *
     * @return
     */
    @Override
    public int normalizeSize(int size) {
        if (size == 0) {
            return sizeIdx2sizeTab[0];
        }
        size = alignSizeIfNeeded(size, directMemoryCacheAlignment);
        if (size <= lookupMaxSize) {
            int ret = sizeIdx2sizeTab[size2idxTab[size - 1 >> LOG2_QUANTUM]];
            assert ret == normalizeSizeCompute(size);
            return ret;
        }
        return normalizeSizeCompute(size);
    }

    private static int normalizeSizeCompute(int size) {
        int x = log2((size << 1) - 1);
        int log2Delta = x < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1
                ? LOG2_QUANTUM : x - LOG2_SIZE_CLASS_GROUP - 1;
        int delta = 1 << log2Delta;
        int delta_mask = delta - 1;
        return size + delta_mask & ~delta_mask;
    }
}
