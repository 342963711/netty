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

/**
 * Expose metrics for an SizeClasses.
 * 为 SizeClasses 暴露一些指标
 * @see PoolArenaMetric
 * @see SizeClasses
 */
public interface SizeClassesMetric {

    /**
     * Computes size from lookup table according to sizeIdx.
     *
     * 根据sizeIdx从查找表中计算大小。 返回查找表中对应的大小
     * @return size
     */
    int sizeIdx2size(int sizeIdx);

    /**
     * Computes size according to sizeIdx.
     * 根据sizeIdx计算大小。
     * @return size
     */
    int sizeIdx2sizeCompute(int sizeIdx);

    /**
     * Computes size from lookup table according to pageIdx.
     * 根据pageIdx 从 查找表中计算大小
     * @return size which is multiples of pageSize.
     */
    long pageIdx2size(int pageIdx);

    /**
     * Computes size according to pageIdx.
     * 根据pageIdx 计算大小
     * @return size which is multiples of pageSize
     */
    long pageIdx2sizeCompute(int pageIdx);

    /**
     * Normalizes request size up to the nearest size class.
     *
     * 将请求大小规格化为最接近的大小类。
     *
     * @param size request size
     * @return sizeIdx of the size class。
     */
    int size2SizeIdx(int size);

    /**
     * Normalizes request size up to the nearest pageSize class.
     *
     * @param pages multiples of pageSizes
     *
     * @return pageIdx of the pageSize class
     */
    int pages2pageIdx(int pages);

    /**
     * Normalizes request size down to the nearest pageSize class.
     *
     * @param pages multiples of pageSizes
     *
     * @return pageIdx of the pageSize class
     */
    int pages2pageIdxFloor(int pages);

    /**
     * Normalizes usable size that would result from allocating an object with the
     * specified size and alignment.
     *
     * 规整化一个 分配具有指定大小和对齐方式的对象时产生的可用大小。
     *
     * @param size request size
     *
     * @return normalized size
     */
    int normalizeSize(int size);
}
