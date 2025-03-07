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

/**
 * Internal primitive priority queue, used by {@link PoolChunk}.
 *
 * 内部原语优先级队列，由{@link PoolChunk} 使用。
 * The implementation is based on the binary heap, as described in Algorithms by Sedgewick and Wayne.
 *
 * 该实现基于二进制堆
 */
final class LongPriorityQueue {
    public static final int NO_VALUE = -1;
    private long[] array = new long[9];
    private int size;

    /**
     * 保证添加的元素，在数组索引上是递增的
     * @param handle
     */
    public void offer(long handle) {
        if (handle == NO_VALUE) {
            throw new IllegalArgumentException("The NO_VALUE (" + NO_VALUE + ") cannot be added to the queue.");
        }
        size++;
        if (size == array.length) {
            // Grow queue capacity.
            array = Arrays.copyOf(array, 1 + (array.length - 1) * 2);
        }
        array[size] = handle;
        lift(size);
    }


    /**
     * 删除元素
     * @param value
     */
    public void remove(long value) {
        for (int i = 1; i <= size; i++) {
            if (array[i] == value) {
                array[i] = array[size--];
                lift(i);
                sink(i);
                return;
            }
        }
    }

    public long peek() {
        if (size == 0) {
            return NO_VALUE;
        }
        return array[1];
    }

    public long poll() {
        if (size == 0) {
            return NO_VALUE;
        }
        long val = array[1];
        array[1] = array[size];
        array[size] = 0;
        size--;
        sink(1);
        return val;
    }

    public boolean isEmpty() {
        return size == 0;
    }


    /**
     * 保证 index 之前的索引位上的元素 是递增的
     * @param index
     */
    private void lift(int index) {
        int parentIndex;
        //保证低索引 对应值是递增的
        while (index > 1 && subord(parentIndex = index >> 1, index)) {
            swap(index, parentIndex);
            index = parentIndex;
        }
    }

    private void sink(int index) {
        int child;
        while ((child = index << 1) <= size) {
            if (child < size && subord(child, child + 1)) {
                child++;
            }
            //
            if (!subord(index, child)) {
                break;
            }
            swap(index, child);
            index = child;
        }
    }

    /**
     * 值越大，优先级越低，需要移位
     * a索引对应的值，大于b索引对应的值，返回true
      */
    private boolean subord(int a, int b) {
        return array[a] > array[b];
    }

    private void swap(int a, int b) {
        long value = array[a];
        array[a] = array[b];
        array[b] = value;
    }
}
