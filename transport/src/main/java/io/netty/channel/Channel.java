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
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.AttributeMap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;


/**
 * A nexus to a network socket or a component which is capable of I/O
 * operations such as read, write, connect, and bind.
 * 与网络套接字或组件的连接，能够进行读、写、连接和绑定等I/O操作。
 *
 * <p>
 * A channel provides a user:
 * channel 为用户提供
 *
 * <ul>
 * <li>the current state of the channel (e.g. is it open? is it connected?),</li>
 * <li>the {@linkplain ChannelConfig configuration parameters} of the channel (e.g. receive buffer size),</li>
 * <li>the I/O operations that the channel supports (e.g. read, write, connect, and bind), and</li>
 * <li>the {@link ChannelPipeline} which handles all I/O events and requests
 *     associated with the channel.</li>
 * </ul>
 * 1.channel 当前的状态（例如打开，连接等）
 * 2.通道的ChannelConfig配置参数。 例如接受缓冲区大小
 * 3.通道支持的所有操作（读，写，连接，绑定）以及 处理与通道相关联的所有I/O事件和请求的 ChannelPipeline
 *
 *
 * <h3>All I/O operations are asynchronous.</h3>
 * 所有的IO 操作都是异步的
 * <p>
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link ChannelFuture} instance which will notify you when the requested I/O
 * operation has succeeded, failed, or canceled.
 *
 * <h3>Channels are hierarchical</h3>
 * 通道是分层的
 * <p>
 * A {@link Channel} can have a {@linkplain #parent() parent} depending on
 * how it was created.  For instance, a {@link SocketChannel}, that was accepted
 * by {@link ServerSocketChannel}, will return the {@link ServerSocketChannel}
 * as its parent on {@link #parent()}.
 * channel 可以有一个父级，取决于它是如何创建的。 例如。被ServerSocketChannel 接受的的 SocketChannel，将返回 ServerSocketChannel 作为其在parent上的返回。
 * <p>
 *
 * The semantics of the hierarchical structure depends on the transport
 * implementation where the {@link Channel} belongs to.  For example, you could
 * write a new {@link Channel} implementation that creates the sub-channels that
 * share one socket connection, as <a href="http://beepcore.org/">BEEP</a> and
 * <a href="https://en.wikipedia.org/wiki/Secure_Shell">SSH</a> do.
 *
 *
 * <h3>Downcast to access transport-specific operations</h3>
 * 向下强转 以访问特定于传输的操作
 *
 * <p>
 * Some transports exposes additional operations that is specific to the
 * transport.  Down-cast the {@link Channel} to sub-type to invoke such
 * operations.  For example, with the old I/O datagram transport, multicast
 * join / leave operations are provided by {@link DatagramChannel}.
 *某些传输会暴露特定于传输的附加操作。
 * 将｛@link Channel｝向下转换为子类型以调用此类操作。
 * 例如，对于旧的I/O数据报传输，多播加入/离开操作由{@link DatagramChannel}提供。
 *
 *
 * <h3>Release resources</h3>
 * 资源释放
 * <p>
 * It is important to call {@link #close()} or {@link #close(ChannelPromise)} to release all
 * resources once you are done with the {@link Channel}. This ensures all resources are
 * released in a proper way, i.e. filehandles.
 *
 * 调用{@link#close（）}或{@link#close（ChannelPromise）}以在完成{@linkChannel}后释放所有资源是很重要的。这样可以确保以正确的方式释放所有资源，
 * 即文件句柄。
 *
 * @see ChannelPipeline
 */
public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel> {

    /**
     * Returns the globally unique identifier of this {@link Channel}.
     * 返回 这个通道的全局唯一标识
     */
    ChannelId id();

    /**
     * Return the {@link EventLoop} this {@link Channel} was registered to.
     * 返回此通道注册到的 EventLoop
     */
    EventLoop eventLoop();

    /**
     * Returns the parent of this channel.
     * 返回这个通道的父级
     *
     * @return the parent channel.
     *         {@code null} if this channel does not have a parent channel.
     */
    Channel parent();

    /**
     * Returns the configuration of this channel.
     * 返回这个通道的配置
     */
    ChannelConfig config();

    /**
     * Returns {@code true} if the {@link Channel} is open and may get active later
     * 如果｛@link Channel｝处于打开状态并且稍后可能处于活动状态，则返回｛@code true｝
     */
    boolean isOpen();

    /**
     * Returns {@code true} if the {@link Channel} is registered with an {@link EventLoop}.
     * 如果通道已经注册到EventLoop，则返回true
     */
    boolean isRegistered();

    /**
     * Return {@code true} if the {@link Channel} is active and so connected.
     *
     * 如果｛@link Channel｝处于活动状态并且已连接，则返回｛@code true｝。
     */
    boolean isActive();

    /**
     * Return the {@link ChannelMetadata} of the {@link Channel} which describe the nature of the {@link Channel}.
     * 返回｛@link Channel｝的｛@linkChannelMetadata｝，该元数据描述了｛@link Channel｝
     *
     */
    ChannelMetadata metadata();

    /**
     * Returns the local address where this channel is bound to.  The returned
     * {@link SocketAddress} is supposed to be down-cast into more concrete
     * type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * 返回此通道绑定到的本地地址。
     * 返回的｛@link SocketAddress｝应该向下转换为更具体的类型，如｛@link-InetSocketAddress}，以检索详细信息。
     *
     * @return the local address of this channel.
     *         {@code null} if this channel is not bound.
     */
    SocketAddress localAddress();

    /**
     * Returns the remote address where this channel is connected to.  The
     * returned {@link SocketAddress} is supposed to be down-cast into more
     * concrete type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * 返回此通道连接到的远程地址。返回的｛@link SocketAddress｝应该向下转换为更具体的类型，如｛@linkInetSocketAddress}，以检索详细信息。
     *
     * @return the remote address of this channel.
     *         {@code null} if this channel is not connected.
     *         If this channel is not connected but it can receive messages
     *         from arbitrary remote addresses (e.g. {@link DatagramChannel},
     *         use {@link DatagramPacket#recipient()} to determine
     *         the origination of the received message as this method will
     *         return {@code null}.
     *
     * 此通道的远程地址。@code null｝（如果未连接此通道）。
     * 如果此通道未连接，但它可以接收来自任意远程地址的消息（例如，｛@link DatagramChannel｝，
     * 请使用｛@linkDatagramPacket#receivient（）｝来确定接收消息的来源，因为此方法将返回｛@code null｝。
     */
    SocketAddress remoteAddress();

    /**
     * Returns the {@link ChannelFuture} which will be notified when this
     * channel is closed.  This method always returns the same future instance.
     * <p>
     * 返回｛@link ChannelFuture｝，此频道关闭时将通知该future。
     * 此方法总是返回相同的ChannelFuture实例。
     *
     */
    ChannelFuture closeFuture();

    /**
     * Returns {@code true} if and only if the I/O thread will perform the
     * requested write operation immediately.  Any write requests made when
     * this method returns {@code false} are queued until the I/O thread is
     * ready to process the queued write requests.
     * <p></p>
     *
     *返回｛@code true｝
     * 如果且仅当I/O线程将立即执行所请求的写入操作。
     * 当此方法返回｛@code false｝时发出的任何写入请求都将排队，直到I/O线程准备好处理排队的写入请求。
     */
    boolean isWritable();

    /**
     * Get how many bytes can be written until {@link #isWritable()} returns {@code false}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code false} then 0.
     *
     * 获取在｛@link#isWritable（）｝返回｛@code false｝之前可以写入的字节数。
     * 此数量将始终为非负数。如果｛@link#isWritable（）｝为｛@code false｝，则为0。
     */
    long bytesBeforeUnwritable();

    /**
     * Get how many bytes must be drained from underlying buffers until {@link #isWritable()} returns {@code true}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code true} then 0.
     *
     * 获取在｛@link#isWritable（）｝返回｛@code true｝之前必须从底层缓冲区中刷出的字节数。
     * 此数量将始终为非负数。如果｛@link#isWritable（）｝为｛@code true｝，则为0。
     */
    long bytesBeforeWritable();

    /**
     * Returns an <em>internal-use-only</em> object that provides unsafe operations.
     * 返回一个仅供内部使用的 unsafe 操作对象
     *
     */
    Unsafe unsafe();

    /**
     * Return the assigned {@link ChannelPipeline}.
     * 返回分配的 ChannelPipeline
     */
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     *
     * 返回分配的 用于分配 ByteBuf 的 ByteBufAllocator
     */
    ByteBufAllocator alloc();

    @Override
    Channel read();

    @Override
    Channel flush();

    /**
     * <em>Unsafe</em> operations that should <em>never</em> be called from user-code. These methods
     * are only provided to implement the actual transport, and must be invoked from an I/O thread except for the
     * following methods:
     * <ul>
     *   <li>{@link #localAddress()}</li>
     *   <li>{@link #remoteAddress()}</li>
     *   <li>{@link #closeForcibly()}</li>
     *   <li>{@link #register(EventLoop, ChannelPromise)}</li>
     *   <li>{@link #deregister(ChannelPromise)}</li>
     *   <li>{@link #voidPromise()}</li>
     * </ul>
     *
     * 从不应从用户代码中调用<em>Unsafe</em>操作。这些方法仅用于实现实际传输，并且必须从I/O线程调用
     * 以下方法：
     *
     */
    interface Unsafe {

        /**
         * Return the assigned {@link RecvByteBufAllocator.Handle} which will be used to allocate {@link ByteBuf}'s when
         * receiving data.
         * 返回分配的｛@link RecvByteBufAllocator.Handle｝，将用于在接收数据时分配｛@linkByteBuf｝
         *
         */
        RecvByteBufAllocator.Handle recvBufAllocHandle();

        /**
         * Return the {@link SocketAddress} to which is bound local or
         * {@code null} if none.
         * 返回绑定到本地 的SocketAddress，如果没有，则返回 null.
         *
         */
        SocketAddress localAddress();

        /**
         * Return the {@link SocketAddress} to which is bound remote or
         * {@code null} if none is bound yet.
         *
         * 返回bending到远程的 SocketAddress，如果没有，返回null.
         */
        SocketAddress remoteAddress();

        /**
         * Register the {@link Channel} of the {@link ChannelPromise} and notify
         * the {@link ChannelFuture} once the registration was complete.
         * 注册 ChannelPromise 的 Channel ，并在注册完成后，通知ChannelFuture
         *
         */
        void register(EventLoop eventLoop, ChannelPromise promise);

        /**
         * Bind the {@link SocketAddress} to the {@link Channel} of the {@link ChannelPromise} and notify
         * it once its done.
         *
         * 绑定 SocketAddress 到 ChannelPromise 的 channel，一旦完成就通知 ChannelPromise
         */
        void bind(SocketAddress localAddress, ChannelPromise promise);

        /**
         * Connect the {@link Channel} of the given {@link ChannelFuture} with the given remote {@link SocketAddress}.
         * If a specific local {@link SocketAddress} should be used it need to be given as argument. Otherwise just
         * pass {@code null} to it.
         *
         * 将给定｛@link ChannelFuture｝的｛@link Channel｝与给定的远程｛@linkSocketAddress｝连接。
         * 如果应该使用特定的本地｛@link SocketAddress｝，则需要将其作为参数给定。否则，只需将{@code null}传递给它。
         *
         * The {@link ChannelPromise} will get notified once the connect operation was complete.
         */
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

        /**
         * Disconnect the {@link Channel} of the {@link ChannelFuture} and notify the {@link ChannelPromise} once the
         * operation was complete.
         */
        void disconnect(ChannelPromise promise);

        /**
         * Close the {@link Channel} of the {@link ChannelPromise} and notify the {@link ChannelPromise} once the
         * operation was complete.
         */
        void close(ChannelPromise promise);

        /**
         * Closes the {@link Channel} immediately without firing any events.  Probably only useful
         * when registration attempt failed.
         */
        void closeForcibly();

        /**
         * Deregister the {@link Channel} of the {@link ChannelPromise} from {@link EventLoop} and notify the
         * {@link ChannelPromise} once the operation was complete.
         */
        void deregister(ChannelPromise promise);

        /**
         * Schedules a read operation that fills the inbound buffer of the first {@link ChannelInboundHandler} in the
         * {@link ChannelPipeline}.  If there's already a pending read operation, this method does nothing.
         */
        void beginRead();

        /**
         * Schedules a write operation.
         */
        void write(Object msg, ChannelPromise promise);

        /**
         * Flush out all write operations scheduled via {@link #write(Object, ChannelPromise)}.
         */
        void flush();

        /**
         * Return a special ChannelPromise which can be reused and passed to the operations in {@link Unsafe}.
         * It will never be notified of a success or error and so is only a placeholder for operations
         * that take a {@link ChannelPromise} as argument but for which you not want to get notified.
         */
        ChannelPromise voidPromise();

        /**
         * Returns the {@link ChannelOutboundBuffer} of the {@link Channel} where the pending write requests are stored.
         */
        ChannelOutboundBuffer outboundBuffer();
    }
}
