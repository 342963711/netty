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

import io.netty.util.internal.ObjectPool.Handle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * 内存的除了UnpooledDirectByteBuf 非池化的另外一种类型，池化字节缓冲区的抽象基础类
 * 该类的实现类，初始化基本都是通过静态方法来初始化，统一交给{@link PoolArena} 来进行池化管理
 *
 * 该类优化了底层{@link #tmpNioBuf}的操作不便
 * @param <T>
 *
 * @see PooledDirectByteBuf
 * @see PooledUnsafeDirectByteBuf
 * @see PooledHeapByteBuf
 */
abstract class PooledByteBuf<T> extends AbstractReferenceCountedByteBuf {
    //池化的字节缓冲区 需要回收器
    private final Handle<PooledByteBuf<T>> recyclerHandle;
    /**
     * 池块 ，池化的主要管理类,
     * 创建 字节缓冲区不需要，在调用初始化方法的时候需要传递
     * {@link #init0(PoolChunk, ByteBuffer, long, int, int, int, PoolThreadCache)}
     */
    protected PoolChunk<T> chunk;

    /**
     * 记录该内存类 在 PoolChunk 中位图 位置
     */
    protected long handle;

    /**
     * 存储到内存的对象，是一个泛型。 可以是字节数组，也可以是jdk的 ByteBuffer。
     * 是通过{@link #init0(PoolChunk, ByteBuffer, long, int, int, int, PoolThreadCache)}
     * 中的PoolChunk 来进行初始化的。
     * 在应用运行时，真实的类型由PoolChunk来进行决定
     */
    protected T memory;
    /**
     * 内存地址偏移量
     */
    protected int offset;

    /**
     * 内存长度（初次申请大小）
     */
    protected int length;

    /**
     * 内存最大长度，也就是标准大小（该对象可以在maxLength 无需重新执行分配）
     */
    int maxLength;

    /**
     * 线程缓冲,该类所属的线程缓存类
     */
    PoolThreadCache cache;

    /**
     * 临时的 字节缓冲区，初始化之后一直存在直到该对象被回收。
     * 这个类提供了直接了内存操作功能
     */
    ByteBuffer tmpNioBuf;

    /**
     * 分配器
     */
    private ByteBufAllocator allocator;

    @SuppressWarnings("unchecked")
    protected PooledByteBuf(Handle<? extends PooledByteBuf<T>> recyclerHandle, int maxCapacity) {
        super(maxCapacity);
        this.recyclerHandle = (Handle<PooledByteBuf<T>>) recyclerHandle;
    }


    /**
     * 初始化方法，是交给管理 该字节缓冲区 的对象去调用的。
     * {@link PoolChunk#allocate(PooledByteBuf, int, int, PoolThreadCache)}
     * @param chunk
     * @param nioBuffer
     * @param handle 返回在PoolChunk 中的位图索引
     * @param offset 内存偏移量，每个字节索引增加的偏移量
     * @param length
     * @param maxLength
     * @param cache
     */
    void init(PoolChunk<T> chunk, ByteBuffer nioBuffer,
              long handle, int offset, int length, int maxLength, PoolThreadCache cache) {
        init0(chunk, nioBuffer, handle, offset, length, maxLength, cache);
    }

    /**
     * 分配超大对象的时候使用。
     * @param chunk
     * @param length
     */
    void initUnpooled(PoolChunk<T> chunk, int length) {
        init0(chunk, null, 0, 0, length, length, null);
    }

    /**
     * 池化 字节缓冲区 初始化
     * @param chunk
     * @param nioBuffer
     * @param handle
     * @param offset
     * @param length
     * @param maxLength
     * @param cache
     */
    private void init0(PoolChunk<T> chunk, ByteBuffer nioBuffer,
                       long handle, int offset, int length, int maxLength, PoolThreadCache cache) {
        assert handle >= 0;
        assert chunk != null;
        assert !PoolChunk.isSubpage(handle) || chunk.arena.size2SizeIdx(maxLength) <= chunk.arena.smallMaxSizeIdx:
                "Allocated small sub-page handle for a buffer size that isn't \"small.\"";

        chunk.incrementPinnedMemory(maxLength);
        this.chunk = chunk;
        memory = chunk.memory;
        tmpNioBuf = nioBuffer;
        allocator = chunk.arena.parent;
        this.cache = cache;
        this.handle = handle;
        this.offset = offset;
        this.length = length;
        this.maxLength = maxLength;
    }

    /**
     * Method must be called before reuse this {@link PooledByteBufAllocator}
     * 在重用内存分配前必须调用
     */
    final void reuse(int maxCapacity) {
        maxCapacity(maxCapacity);
        resetRefCnt();
        setIndex0(0, 0);
        discardMarks();
    }

    @Override
    public final int capacity() {
        return length;
    }

    @Override
    public int maxFastWritableBytes() {
        return Math.min(maxLength, maxCapacity()) - writerIndex;
    }

    @Override
    public final ByteBuf capacity(int newCapacity) {
        if (newCapacity == length) {
            ensureAccessible();
            return this;
        }
        checkNewCapacity(newCapacity);
        //池化逻辑
        if (!chunk.unpooled) {
            // If the request capacity does not require reallocation, just update the length of the memory.
            if (newCapacity > length) {
                if (newCapacity <= maxLength) {
                    length = newCapacity;
                    return this;
                }
            } else if (newCapacity > maxLength >>> 1 &&
                    (maxLength > 512 || newCapacity > maxLength - 16)) {
                // here newCapacity < length
                length = newCapacity;
                trimIndicesToCapacity(newCapacity);
                return this;
            }
        }

        // Reallocation required.  非池化，需要重新分配
        chunk.decrementPinnedMemory(maxLength);
        chunk.arena.reallocate(this, newCapacity, true);
        return this;
    }

    @Override
    public final ByteBufAllocator alloc() {
        return allocator;
    }

    @Override
    public final ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public final ByteBuf unwrap() {
        return null;
    }

    @Override
    public final ByteBuf retainedDuplicate() {
        return PooledDuplicatedByteBuf.newInstance(this, this, readerIndex(), writerIndex());
    }

    @Override
    public final ByteBuf retainedSlice() {
        final int index = readerIndex();
        return retainedSlice(index, writerIndex() - index);
    }

    @Override
    public final ByteBuf retainedSlice(int index, int length) {
        return PooledSlicedByteBuf.newInstance(this, this, index, length);
    }

    /**
     * 复用之前 的临时缓冲区，如果临时缓冲区为空，则进行初始化{@link #newInternalNioBuffer(Object)}
     * @return
     */
    protected final ByteBuffer internalNioBuffer() {
        ByteBuffer tmpNioBuf = this.tmpNioBuf;
        if (tmpNioBuf == null) {
            this.tmpNioBuf = tmpNioBuf = newInternalNioBuffer(memory);
        } else {
            //该方法并不会擦除缓冲区中的数据
            tmpNioBuf.clear();
        }
        return tmpNioBuf;
    }

    /**
     * 创建内部的字节缓冲
     * @param memory {@link #memory}
     * @return
     */
    protected abstract ByteBuffer newInternalNioBuffer(T memory);

    @Override
    protected final void deallocate() {
        if (handle >= 0) {
            final long handle = this.handle;
            this.handle = -1;
            memory = null;
            chunk.decrementPinnedMemory(maxLength);
            chunk.arena.free(chunk, tmpNioBuf, handle, maxLength, cache);
            tmpNioBuf = null;
            chunk = null;
            cache = null;
            recycle();
        }
    }

    private void recycle() {
        recyclerHandle.recycle(this);
    }

    /**
     * 偏移量+索引位置
     * @param index
     * @return
     */
    protected final int idx(int index) {
        return offset + index;
    }

    /**
     * 创建一个 内部的 字节缓冲区
     * @param index 数据索引
     * @param length 创建长度
     * @param duplicate 是否复制一个
     * @return
     */
    final ByteBuffer _internalNioBuffer(int index, int length, boolean duplicate) {
        index = idx(index);
        ByteBuffer buffer = duplicate ? newInternalNioBuffer(memory) : internalNioBuffer();
        buffer.limit(index + length).position(index);
        return buffer;
    }

    ByteBuffer duplicateInternalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return _internalNioBuffer(index, length, true);
    }

    @Override
    public final ByteBuffer internalNioBuffer(int index, int length) {
        checkIndex(index, length);
        return _internalNioBuffer(index, length, false);
    }

    @Override
    public final int nioBufferCount() {
        return 1;
    }

    @Override
    public final ByteBuffer nioBuffer(int index, int length) {
        return duplicateInternalNioBuffer(index, length).slice();
    }

    @Override
    public final ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] { nioBuffer(index, length) };
    }

    @Override
    public final boolean isContiguous() {
        return true;
    }

    @Override
    public final int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return out.write(duplicateInternalNioBuffer(index, length));
    }

    @Override
    public final int readBytes(GatheringByteChannel out, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = out.write(_internalNioBuffer(readerIndex, length, false));
        readerIndex += readBytes;
        return readBytes;
    }

    /**
     * 检索不修改（get 开头的方法） 读写指针
     * @param index
     * @param out
     * @param position the file position at which the transfer is to begin
     * @param length the maximum number of bytes to transfer
     *
     * @return
     * @throws IOException
     */
    @Override
    public final int getBytes(int index, FileChannel out, long position, int length) throws IOException {
        return out.write(duplicateInternalNioBuffer(index, length), position);
    }

    /**
     * 读取（read） 操作 会影响 读指针
     * @param out
     * @param position the file position at which the transfer is to begin
     * @param length the maximum number of bytes to transfer
     *
     * @return
     * @throws IOException
     */
    @Override
    public final int readBytes(FileChannel out, long position, int length) throws IOException {
        checkReadableBytes(length);
        int readBytes = out.write(_internalNioBuffer(readerIndex, length, false), position);
        readerIndex += readBytes;
        return readBytes;
    }


    /**
     * 将数据 从 in 参数读取到此缓冲区。 这里使用
     * @param index
     * @param in
     * @param length the maximum number of bytes to transfer
     *
     * @return
     * @throws IOException
     */

    @Override
    public final int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        try {
            return in.read(internalNioBuffer(index, length));
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }

    @Override
    public final int setBytes(int index, FileChannel in, long position, int length) throws IOException {
        try {
            return in.read(internalNioBuffer(index, length), position);
        } catch (ClosedChannelException ignored) {
            return -1;
        }
    }
}
