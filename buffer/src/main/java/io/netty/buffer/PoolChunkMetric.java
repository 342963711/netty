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

/**
 * Metrics for a chunk.
 * chunk的监控指标
 */
public interface PoolChunkMetric {

    /**
     * Return the percentage of the current usage of the chunk.
     * 返回当前区块当前使用率的百分比
     */
    int usage();

    /**
     * Return the size of the chunk in bytes, this is the maximum of bytes that can be served out of the chunk.
     * 返回块的大小，这是块可以提供的最大字节数
     */
    int chunkSize();

    /**
     * Return the number of free bytes in the chunk.
     * 返回块中的可用字节数
     */
    int freeBytes();
}
