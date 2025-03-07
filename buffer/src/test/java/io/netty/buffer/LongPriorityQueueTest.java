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

import io.netty.util.internal.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @date 2023/7/17 15:54
 * @author likai
 * @email likai9376@163.com
 * @desc 测试 优先队列
 */
class LongPriorityQueueTest {
    @Test
    public void mustThrowWhenAddingNoValue() {
        final LongPriorityQueue pq = new LongPriorityQueue();
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                pq.offer(LongPriorityQueue.NO_VALUE);
            }
        });
    }

    @Test
    public void mustReturnValuesInOrder() {
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        int initialValues = tlr.nextInt(5, 30);
        ArrayList<Long> values = new ArrayList<Long>();
        for (int i = 0; i < initialValues; i++) {
            values.add(tlr.nextLong(0, Long.MAX_VALUE));
        }
        LongPriorityQueue pq = new LongPriorityQueue();
        assertTrue(pq.isEmpty());
        for (Long value : values) {
            pq.offer(value);
        }
        Collections.sort(values);
        int valuesToRemove = initialValues / 2;
        ListIterator<Long> itr = values.listIterator();
        for (int i = 0; i < valuesToRemove; i++) {
            assertTrue(itr.hasNext());
            assertThat(pq.poll()).isEqualTo(itr.next());
            itr.remove();
        }
        int moreValues = tlr.nextInt(5, 30);
        for (int i = 0; i < moreValues; i++) {
            long value = tlr.nextLong(0, Long.MAX_VALUE);
            pq.offer(value);
            values.add(value);
        }
        Collections.sort(values);
        itr = values.listIterator();
        while (itr.hasNext()) {
            assertThat(pq.poll()).isEqualTo(itr.next());
        }
        assertTrue(pq.isEmpty());
        assertThat(pq.poll()).isEqualTo(LongPriorityQueue.NO_VALUE);
    }

    @Test
    public void internalRemoveOfAllElements() {
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        int initialValues = tlr.nextInt(5, 30);
        ArrayList<Long> values = new ArrayList<Long>();
        LongPriorityQueue pq = new LongPriorityQueue();
        for (int i = 0; i < initialValues; i++) {
            long value = tlr.nextLong(0, Long.MAX_VALUE);
            pq.offer(value);
            values.add(value);
        }
        for (Long value : values) {
            pq.remove(value);
        }
        assertTrue(pq.isEmpty());
        assertThat(pq.poll()).isEqualTo(LongPriorityQueue.NO_VALUE);
    }

    @Test
    public void internalRemoveMustPreserveOrder() {
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        int initialValues = tlr.nextInt(1, 30);
        ArrayList<Long> values = new ArrayList<Long>();
        LongPriorityQueue pq = new LongPriorityQueue();
        for (int i = 0; i < initialValues; i++) {
            long value = tlr.nextLong(0, Long.MAX_VALUE);
            pq.offer(value);
            values.add(value);
        }

        long toRemove = values.get(values.size() / 2);
        values.remove(toRemove);
        pq.remove(toRemove);

        Collections.sort(values);
        for (Long value : values) {
            assertThat(pq.poll()).isEqualTo(value);
        }
        assertTrue(pq.isEmpty());
        assertThat(pq.poll()).isEqualTo(LongPriorityQueue.NO_VALUE);
    }

    @Test
    public void mustSupportDuplicateValues() {
        LongPriorityQueue pq = new LongPriorityQueue();
        pq.offer(10);
        pq.offer(5);
        pq.offer(6);
        pq.offer(5);
        pq.offer(10);
        pq.offer(10);
        pq.offer(6);
        pq.remove(10);
        assertThat(pq.peek()).isEqualTo(5);
        assertThat(pq.peek()).isEqualTo(5);
        assertThat(pq.poll()).isEqualTo(5);
        assertThat(pq.peek()).isEqualTo(5);
        assertThat(pq.poll()).isEqualTo(5);
        assertThat(pq.peek()).isEqualTo(6);
        assertThat(pq.poll()).isEqualTo(6);
        assertThat(pq.peek()).isEqualTo(6);
        assertThat(pq.peek()).isEqualTo(6);
        assertThat(pq.poll()).isEqualTo(6);
        assertThat(pq.peek()).isEqualTo(10);
        assertThat(pq.poll()).isEqualTo(10);
        assertThat(pq.poll()).isEqualTo(10);
        assertTrue(pq.isEmpty());
        assertThat(pq.poll()).isEqualTo(LongPriorityQueue.NO_VALUE);
        assertThat(pq.peek()).isEqualTo(LongPriorityQueue.NO_VALUE);
    }

    @Test
    public void priorityQueInfo(){

        LongPriorityQueue longPriorityQueue = new LongPriorityQueue();

        longPriorityQueue.offer(1);

        longPriorityQueue.offer(10);

        longPriorityQueue.offer(11);

        long poll = longPriorityQueue.poll();
        System.out.println(poll);
//        pq.offer(100);
//        pq.offer(99);
//        pq.offer(101);
    }
}
