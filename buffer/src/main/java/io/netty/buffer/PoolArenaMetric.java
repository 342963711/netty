/*
 * Copyright 2015 The Netty Project
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

import java.util.List;

/**
 * Expose metrics for an arena.
 *
 * @see PoolArena
 * 为一个 PoolArena 暴露指标
 */
public interface PoolArenaMetric extends SizeClassesMetric {

    /**
     * Returns the number of thread caches backed by this arena.
     * 返回 arena 支持的 线程缓存数量
     */
    int numThreadCaches();

    /**
     * Returns the number of tiny sub-pages for the arena.
     *
     * @deprecated Tiny sub-pages have been merged into small sub-pages.
     */
    @Deprecated
    int numTinySubpages();

    /**
     * Returns the number of small sub-pages for the arena.
     *
     * 返回 small 类型的子页数量
     */
    int numSmallSubpages();

    /**
     * Returns the number of chunk lists for the arena.
     *
     * 返回 块列表数
     */
    int numChunkLists();

    /**
     * Returns an unmodifiable {@link List} which holds {@link PoolSubpageMetric}s for tiny sub-pages.
     *
     * @deprecated Tiny sub-pages have been merged into small sub-pages.
     */
    @Deprecated
    List<PoolSubpageMetric> tinySubpages();

    /**
     * Returns an unmodifiable {@link List} which holds {@link PoolSubpageMetric}s for small sub-pages.
     *
     * 返回 包含 small类型的子页的 指标 信息列表
     */
    List<PoolSubpageMetric> smallSubpages();

    /**
     * Returns an unmodifiable {@link List} which holds {@link PoolChunkListMetric}s.
     *
     * 返回 块列表 指标信息 列表
     */
    List<PoolChunkListMetric> chunkLists();

    /**
     * Return the number of allocations done via the arena. This includes all sizes.
     * 返回 分配的数量。这个包含所有的大小
     */
    long numAllocations();

    /**
     * Return the number of tiny allocations done via the arena.
     *
     * @deprecated Tiny allocations have been merged into small allocations.
     */
    @Deprecated
    long numTinyAllocations();

    /**
     * Return the number of small allocations done via the arena.
     * 返回 small 类型分配数量
     */
    long numSmallAllocations();

    /**
     * Return the number of normal allocations done via the arena.
     * 返回正规类型的 分配数量
     */
    long numNormalAllocations();

    /**
     * Return the number of huge allocations done via the arena.
     * 返回 巨型类型 的分配 数量
     */
    long numHugeAllocations();

    /**
     * Return the number of deallocations done via the arena. This includes all sizes.
     *
     * 返回 解除分配的数量。 包含所有的大小
     */
    long numDeallocations();

    /**
     * Return the number of tiny deallocations done via the arena.
     *
     * @deprecated Tiny deallocations have been merged into small deallocations.
     */
    @Deprecated
    long numTinyDeallocations();

    /**
     * Return the number of small deallocations done via the arena.
     *
     * small 类型 的解除分配的数量
     */
    long numSmallDeallocations();

    /**
     * Return the number of normal deallocations done via the arena.
     *
     * 返回 正规类型 解除分配 的数量
     */
    long numNormalDeallocations();

    /**
     * Return the number of huge deallocations done via the arena.
     */
    long numHugeDeallocations();

    /**
     * Return the number of currently active allocations.
     *
     * 当前活跃的 分配数量
     */
    long numActiveAllocations();

    /**
     * Return the number of currently active tiny allocations.
     *
     * @deprecated Tiny allocations have been merged into small allocations.
     */
    @Deprecated
    long numActiveTinyAllocations();

    /**
     * Return the number of currently active small allocations.
     *
     * 当前活跃的小类型分配数量
     */
    long numActiveSmallAllocations();

    /**
     * Return the number of currently active normal allocations.
     * 当前活跃 正规类型 分配数量
     */
    long numActiveNormalAllocations();

    /**
     * Return the number of currently active huge allocations.
     * 当前活跃 巨大类型 分配数量
     */
    long numActiveHugeAllocations();

    /**
     * Return the number of active bytes that are currently allocated by the arena.
     *
     * 当前活跃字节数量
     */
    long numActiveBytes();
}
