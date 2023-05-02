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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.UnstableApi;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Allocates a new receive buffer whose capacity is probably large enough to read all inbound data and small enough
 * not to waste its space.
 *
 * 分配一个新的接收缓冲区，其容量可能足够大，可以读取所有入站数据，也足够小，不会浪费空间。
 */
public interface RecvByteBufAllocator {
    /**
     * Creates a new handle.  The handle provides the actual operations and keeps the internal information which is
     * required for predicting an optimal buffer capacity.
     *
     * 创建一个新的handle，handler 提供实际操作并保存预测最佳缓冲容量所需的内部信息。
     */
    Handle newHandle();

    /**
     * @deprecated Use {@link ExtendedHandle}.
     */
    @Deprecated
    interface Handle {
        /**
         * Creates a new receive buffer whose capacity is probably large enough to read all inbound data and small
         * enough not to waste its space.
         * 创建一个新的接受缓冲区
         */
        ByteBuf allocate(ByteBufAllocator alloc);

        /**
         * Similar to {@link #allocate(ByteBufAllocator)} except that it does not allocate anything but just tells the
         * capacity.
         * 与{@link# allocate（ByteBufAllocator）}类似，只是它不分配任何内容，而只是告诉容量。
         */
        int guess();

        /**
         * Reset any counters that have accumulated and recommend how many messages/bytes should be read for the next
         * read loop.
         * 重置已累积的所有计数器，并建议下一次读取循环应读取多少消息/字节。
         *
         * <p>
         * This may be used by {@link #continueReading()} to determine if the read operation should complete.
         * 这可能被continueReading 使用用于判断读取操作是否应该完成
         * </p>
         *
         * This is only ever a hint and may be ignored by the implementation.
         * 这只是一个提示，可能会被实现忽略。
         *
         * @param config The channel configuration which may impact this object's behavior.
         *               可能影响此对象行为的通道配置
         */
        void reset(ChannelConfig config);

        /**
         * Increment the number of messages that have been read for the current read loop.
         * 增加当前读取循环中已读取的消息数。
         * @param numMessages The amount to increment by.
         */
        void incMessagesRead(int numMessages);

        /**
         * Set the bytes that have been read for the last read operation.
         * 设置上次读取操作已读取的字节数。
         *
         * This may be used to increment the number of bytes that have been read.
         * 这可以用于增加已读取的字节数。
         *
         * @param bytes The number of bytes from the previous read operation. This may be negative if an read error
         * occurs.
         * If a negative value is seen it is expected to be return on the next call to
         * {@link #lastBytesRead()}.
         *
         *              A negative value will signal a termination condition enforced externally
         * to this class and is not required to be enforced in {@link #continueReading()}.
         *
         * 上一次读取操作的字节数。如果发生读取错误，这可能是一个负值。如果出现负数，当下一次调用lastBytesRead 也将返回负数
         * 负值将表示在该类外部强制执行的终止条件，并且不需要在{@link#continueReading（）}中强制执行。
         */
        void lastBytesRead(int bytes);

        /**
         * Get the amount of bytes for the previous read operation.
         * 获取上次读取操作的字节数
         * @return The amount of bytes for the previous read operation.
         */
        int lastBytesRead();

        /**
         * Set how many bytes the read operation will (or did) attempt to read.
         * @param bytes How many bytes the read operation will (or did) attempt to read.
         * 设置读取操作将要城市读取的 字节数量
         */
        void attemptedBytesRead(int bytes);

        /**
         * Get how many bytes the read operation will (or did) attempt to read.
         * @return How many bytes the read operation will (or did) attempt to read.
         */
        int attemptedBytesRead();

        /**
         * Determine if the current read loop should continue.
         * @return {@code true} if the read loop should continue reading. {@code false} if the read loop is complete.
         *
         * 确定当前读取循环是否应继续 .@如果读取循环应继续读取，则返回｛@code true｝。｛@code false｝如果读取循环完成
         */
        boolean continueReading();

        /**
         * The read has completed.
         */
        void readComplete();
    }

    @SuppressWarnings("deprecation")
    @UnstableApi
    interface ExtendedHandle extends Handle {
        /**
         * Same as {@link Handle#continueReading()} except "more data" is determined by the supplier parameter.
         * @param maybeMoreDataSupplier A supplier that determines if there maybe more data to read.
         *
         * 与{@link Handle#continueReading（）}相同，只是“更多数据”由供应商参数决定。@param maybeMoreDataSupplier一个确定是否有更多数据要读取的供应商。
         */
        boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier);
    }

    /**
     * A {@link Handle} which delegates all call to some other {@link Handle}.
     *
     * 一个｛@link handler ｝，它将所有调用委托给其他 handle
     */
    class DelegatingHandle implements Handle {
        private final Handle delegate;

        public DelegatingHandle(Handle delegate) {
            this.delegate = checkNotNull(delegate, "delegate");
        }

        /**
         * Get the {@link Handle} which all methods will be delegated to.
         * @return the {@link Handle} which all methods will be delegated to.
         */
        protected final Handle delegate() {
            return delegate;
        }

        @Override
        public ByteBuf allocate(ByteBufAllocator alloc) {
            return delegate.allocate(alloc);
        }

        @Override
        public int guess() {
            return delegate.guess();
        }

        @Override
        public void reset(ChannelConfig config) {
            delegate.reset(config);
        }

        @Override
        public void incMessagesRead(int numMessages) {
            delegate.incMessagesRead(numMessages);
        }

        @Override
        public void lastBytesRead(int bytes) {
            delegate.lastBytesRead(bytes);
        }

        @Override
        public int lastBytesRead() {
            return delegate.lastBytesRead();
        }

        @Override
        public boolean continueReading() {
            return delegate.continueReading();
        }

        @Override
        public int attemptedBytesRead() {
            return delegate.attemptedBytesRead();
        }

        @Override
        public void attemptedBytesRead(int bytes) {
            delegate.attemptedBytesRead(bytes);
        }

        @Override
        public void readComplete() {
            delegate.readComplete();
        }
    }
}
