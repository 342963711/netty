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
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;


/**
 *
 * A list of {@link ChannelHandler}s which handles or intercepts inbound events and outbound operations of a
 * {@link Channel}.  {@link ChannelPipeline} implements an advanced form of the
 * <a href="https://www.oracle.com/technetwork/java/interceptingfilter-142169.html">Intercepting Filter</a> pattern
 * to give a user full control over how an event is handled and how the {@link ChannelHandler}s in a pipeline
 * interact with each other.
 *
 * 处理或截获｛@link Channel｝的入站事件和出站操作的｛@linkChannelHandler｝的列表。
 *
 * ｛@link ChannelPipeline｝实现了<a href=“https://www.oracle.com/technetwork/java/interceptingfilter-142169.html“>
 *     Intercepting Filter</a>模式，让用户完全控制事件的处理方式以及管道中的｛@link ChannelHandler｝如何相互交互。
 *
 *
 * <h3>Creation of a pipeline</h3>
 * 1.创建一个管道
 *
 * Each channel has its own pipeline and it is created automatically when a new channel is created.
 * 每个通道都有自己的管道，并且在创建新通道时会自动创建。
 *
 *
 * <h3>How an event flows in a pipeline</h3>
 * 2.事件如何在管道中流动
 *
 *
 * The following diagram describes how I/O events are processed by {@link ChannelHandler}s in a {@link ChannelPipeline}
 * typically. An I/O event is handled by either a {@link ChannelInboundHandler} or a {@link ChannelOutboundHandler}
 * and be forwarded to its closest handler by calling the event propagation methods defined in
 * {@link ChannelHandlerContext}, such as {@link ChannelHandlerContext#fireChannelRead(Object)} and
 * {@link ChannelHandlerContext#write(Object)}.
 *
 * 下图描述了｛@link ChannelPipeline｝中的｛@linkChannelHandler｝通常如何处理I/O事件。
 * I/O事件由{@link ChannelInboundHandler}或{@link ChannelOutboundHandler}处理，
 *
 * 并通过调用{@link ChannelHandlerContext}中定义的事件传播方法
 * （如{@link ChannelHandlerContext#fireChannelRead（Object)}和{@link ChannelHandlerContext#write（Object)}）转发到最近的处理程序。
 *
 *
 *
 * <pre>
 *                                                 I/O Request
 *                                            via {@link Channel} or
 *                                        {@link ChannelHandlerContext}
 *                                                      |
 *  +---------------------------------------------------+---------------+
 *  |                           ChannelPipeline         |               |
 *  |                                                  \|/              |
 *  |    +---------------------+            +-----------+----------+    |
 *  |    | Inbound Handler  N  |            | Outbound Handler  1  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler N-1 |            | Outbound Handler  2  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  .               |
 *  |               .                                   .               |
 *  | ChannelHandlerContext.fireIN_EVT() ChannelHandlerContext.OUT_EVT()|
 *  |        [ method call]                       [method call]         |
 *  |               .                                   .               |
 *  |               .                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  2  |            | Outbound Handler M-1 |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  |               |                                  \|/              |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |    | Inbound Handler  1  |            | Outbound Handler  M  |    |
 *  |    +----------+----------+            +-----------+----------+    |
 *  |              /|\                                  |               |
 *  +---------------+-----------------------------------+---------------+
 *                  |                                  \|/
 *  +---------------+-----------------------------------+---------------+
 *  |               |                                   |               |
 *  |       [ Socket.read() ]                    [ Socket.write() ]     |
 *  |                                                                   |
 *  |  Netty Internal I/O Threads (Transport Implementation)            |
 *  +-------------------------------------------------------------------+
 * </pre>
 *
 * An inbound event is handled by the inbound handlers in the bottom-up direction as shown on the left side of the
 * diagram.  An inbound handler usually handles the inbound data generated by the I/O thread on the bottom of the
 * diagram.  The inbound data is often read from a remote peer via the actual input operation such as
 * {@link SocketChannel#read(ByteBuffer)}.  If an inbound event goes beyond the top inbound handler, it is discarded
 * silently, or logged if it needs your attention.
 * 入站事件由入站处理程序按自下而上的方向处理，如图左侧所示。
 * 入站处理程序通常处理由图底部的I/O线程生成的入站数据。
 * 入站数据通常是通过实际的输入操作从远程对等端读取的，例如{@link SocketChannel#read（ByteBuffer）}。
 * 如果入站事件超出了顶级入站处理程序，则会将其丢弃
 * 如果需要您的注意，可以静默或记录。
 *
 *
 * <p>
 * An outbound event is handled by the outbound handler in the top-down direction as shown on the right side of the
 * diagram.  An outbound handler usually generates or transforms the outbound traffic such as write requests.
 * If an outbound event goes beyond the bottom outbound handler, it is handled by an I/O thread associated with the
 * {@link Channel}. The I/O thread often performs the actual output operation such as
 * {@link SocketChannel#write(ByteBuffer)}.
 * 出站事件由出站处理程序按自上而下的方向处理，如图右侧所示。
 * 出站处理程序通常生成或转换出站流量，例如写入请求。
 * 如果出站事件超出底部出站处理程序，则由与｛@link Channel｝关联的I/O线程处理。
 * I/O线程通常执行实际的输出操作，例如
 * ｛@link SocketChannel#写入（字节缓冲区）
 *
 * <p>
 * For example, let us assume that we created the following pipeline:
 * 例如，让我们假设我们创建了以下管道：
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * p.addLast("1", new InboundHandlerA());
 * p.addLast("2", new InboundHandlerB());
 * p.addLast("3", new OutboundHandlerA());
 * p.addLast("4", new OutboundHandlerB());
 * p.addLast("5", new InboundOutboundHandlerX());
 * </pre>
 * In the example above, the class whose name starts with {@code Inbound} means it is an inbound handler.
 * The class whose name starts with {@code Outbound} means it is a outbound handler.
 *
 * 在上面的例子中。 以Inbound 开头的类表示的是一个 入站处理程序， 以Outbound开头的类名标识的是一个出站处理程序
 * <p>
 *
 * In the given example configuration, the handler evaluation order is 1, 2, 3, 4, 5 when an event goes inbound.
 * When an event goes outbound, the order is 5, 4, 3, 2, 1.  On top of this principle, {@link ChannelPipeline} skips
 * the evaluation of certain handlers to shorten the stack depth:
 *
 * 在给定的示例配置中，当事件进入时，处理程序评估顺序为1、2、3、4、5。
 * 当事件传出时，顺序为5、4、3、2、1。除此之外，｛@link ChannelPipeline｝跳过对某些处理程序的评估，以缩短堆栈深度：
 *
 * <ul>
 * <li>3 and 4 don't implement {@link ChannelInboundHandler}, and therefore the actual evaluation order of an inbound
 *     event will be: 1, 2, and 5.</li>
 * 3.4没有实现ChannelInboundHandler。 因此一个入站事件的时机处理顺序为，1，2，5
 *
 *
 * <li>1 and 2 don't implement {@link ChannelOutboundHandler}, and therefore the actual evaluation order of a
 *     outbound event will be: 5, 4, and 3.</li>
 *  同理出站时间的实际处理顺序为 5，4，3
 *
 * <li>If 5 implements both {@link ChannelInboundHandler} and {@link ChannelOutboundHandler}, the evaluation order of
 *     an inbound and a outbound event could be 125 and 543 respectively.</li>
 * 因为5同时实现了 ChannelInboundHandler，ChannelOutboundHandler，则入站和出站事件的评估顺序可能分别为125和543。
 * </ul>
 *
 *
 *
 * <h3>Forwarding an event to the next handler</h3>
 * 3.将事件转发到下一个 handler
 *
 * As you might noticed in the diagram shows, a handler has to invoke the event propagation methods in
 * {@link ChannelHandlerContext} to forward an event to its next handler.  Those methods include:
 *
 * 如图所示，处理程序必须调用｛@link ChannelHandlerContext｝中的事件传播方法，才能将事件转发到下一个处理程序。这些方法包括：
 *
 * <ul>
 * <li>Inbound event propagation methods:
 *     <ul>
 *     <li>{@link ChannelHandlerContext#fireChannelRegistered()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelActive()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelRead(Object)}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelReadComplete()}</li>
 *     <li>{@link ChannelHandlerContext#fireExceptionCaught(Throwable)}</li>
 *     <li>{@link ChannelHandlerContext#fireUserEventTriggered(Object)}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelWritabilityChanged()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelInactive()}</li>
 *     <li>{@link ChannelHandlerContext#fireChannelUnregistered()}</li>
 *     </ul>
 * </li>
 * <li>Outbound event propagation methods:
 *     <ul>
 *     <li>{@link ChannelHandlerContext#bind(SocketAddress, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#connect(SocketAddress, SocketAddress, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#write(Object, ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#flush()}</li>
 *     <li>{@link ChannelHandlerContext#read()}</li>
 *     <li>{@link ChannelHandlerContext#disconnect(ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#close(ChannelPromise)}</li>
 *     <li>{@link ChannelHandlerContext#deregister(ChannelPromise)}</li>
 *     </ul>
 * </li>
 * </ul>
 *
 * and the following example shows how the event propagation is usually done:
 *下面的示例显示了事件传播通常是如何进行的
 * <pre>
 * public class MyInboundHandler extends {@link ChannelInboundHandlerAdapter} {
 *     {@code @Override}
 *     public void channelActive({@link ChannelHandlerContext} ctx) {
 *         System.out.println("Connected!");
 *         ctx.fireChannelActive();
 *     }
 * }
 *
 * public class MyOutboundHandler extends {@link ChannelOutboundHandlerAdapter} {
 *     {@code @Override}
 *     public void close({@link ChannelHandlerContext} ctx, {@link ChannelPromise} promise) {
 *         System.out.println("Closing ..");
 *         ctx.close(promise);
 *     }
 * }
 * </pre>
 *
 * <h3>Building a pipeline</h3>
 * 4.构建一个管道
 *
 * <p>
 * A user is supposed to have one or more {@link ChannelHandler}s in a pipeline to receive I/O events (e.g. read) and
 * to request I/O operations (e.g. write and close).  For example, a typical server will have the following handlers
 * in each channel's pipeline, but your mileage may vary depending on the complexity and characteristics of the
 * protocol and business logic:
 *用户应该在管道中有一个或多个{@link ChannelHandler}来接收I/O事件（例如读取）和请求I/O操作（例如写入和关闭）。
 * 例如，一个典型的服务器在每个通道的管道中都有以下处理程序，但您的里程数可能会因协议和业务逻辑的复杂性和特性而异
 *
 * <ol>
 * <li>Protocol Decoder - translates binary data (e.g. {@link ByteBuf}) into a Java object.</li>
 * <li>Protocol Encoder - translates a Java object into binary data.</li>
 * <li>Business Logic Handler - performs the actual business logic (e.g. database access).</li>
 * </ol>
 *
 * and it could be represented as shown in the following example:
 * 它可以被下面的示例所表示
 *
 * <pre>
 * static final {@link EventExecutorGroup} group = new {@link DefaultEventExecutorGroup}(16);
 * ...
 *
 * {@link ChannelPipeline} pipeline = ch.pipeline();
 *
 * pipeline.addLast("decoder", new MyProtocolDecoder());
 * pipeline.addLast("encoder", new MyProtocolEncoder());
 *
 * // Tell the pipeline to run MyBusinessLogicHandler's event handler methods
 * // in a different thread than an I/O thread so that the I/O thread is not blocked by
 * // a time-consuming task.
 * // If your business logic is fully asynchronous or finished very quickly, you don't
 * // need to specify a group.
 * pipeline.addLast(group, "handler", new MyBusinessLogicHandler());
 *
 * 告诉管道在不同于I/O线程的线程中运行MyBusinessLogicHandler的事件处理程序方法，这样I/O线程就不会被耗时的任务阻塞。
 * 如果您的业务逻辑是完全异步的或很快就完成了，则不需要指定组。
 * </pre>
 *
 * Be aware that while using {@link DefaultEventLoopGroup} will offload the operation from the {@link EventLoop} it will
 * still process tasks in a serial fashion per {@link ChannelHandlerContext} and so guarantee ordering. Due the ordering
 * it may still become a bottle-neck. If ordering is not a requirement for your use-case you may want to consider using
 * {@link UnorderedThreadPoolEventExecutor} to maximize the parallelism of the task execution.
 *
 * 请注意，虽然使用｛@link DefaultEventLoopGroup｝将从｛@link EventLoop｝卸载操作，但它仍将按照｛@linkChannelHandlerContext｝以串行方式处理任务，因此保证排序。
 * 由于顺序性，它可能仍然会成为一个瓶颈。
 * 如果您的用例不需要排序，您可能需要考虑使用｛@link UnorderedThreadPoolEventExecutor｝来最大限度地提高任务执行的并行性。
 * <h3>Thread safety</h3>
 * 5.线程安全
 * <p>
 * A {@link ChannelHandler} can be added or removed at any time because a {@link ChannelPipeline} is thread safe.
 * For example, you can insert an encryption handler when sensitive information is about to be exchanged, and remove it
 * after the exchange.
 * 可以随时添加或删除｛@link ChannelHandler｝，因为｛@linkChannelPipeline｝是线程安全的。
 * 例如，您可以在即将交换敏感信息时插入加密处理程序，并在交换后将其删除。
 *
 * 与{@link ChannelHandlerContext} 类似，该接口也是定义了{@link ChannelInboundInvoker} 中的方法，并重新定义返回值是当前接口。
 *
 * 相当于过滤器中 FilterManger,用于管理所有Filter. 在I/O 也就是管理所有的ChannelHandler
 * ,ChannelContext也就是FilterChain.维护所有的ChannelHandler 的调用关系
 * @see DefaultChannelPipeline
 */
public interface ChannelPipeline
        extends ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Entry<String, ChannelHandler>> {

    /**
     * Inserts a {@link ChannelHandler} at the first position of this pipeline.
     *
     * @param name     the name of the handler to insert first
     * @param handler  the handler to insert first
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addFirst(String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} at the first position of this pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                 methods
     * @param name     the name of the handler to insert first
     * @param handler  the handler to insert first
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addLast(String name, ChannelHandler handler);

    /**
     * Appends a {@link ChannelHandler} at the last position of this pipeline.
     *
     * @param group    the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                 methods
     * @param name     the name of the handler to append
     * @param handler  the handler to append
     *
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} before an existing handler of this
     * pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                  methods
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert before
     * @param handler   the handler to insert before
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler);

    /**
     * Inserts a {@link ChannelHandler} after an existing handler of this
     * pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}
     *                  methods
     * @param baseName  the name of the existing handler
     * @param name      the name of the handler to insert after
     * @param handler   the handler to insert after
     *
     * @throws NoSuchElementException
     *         if there's no such entry with the specified {@code baseName}
     * @throws IllegalArgumentException
     *         if there's an entry with the same name already in the pipeline
     * @throws NullPointerException
     *         if the specified baseName or handler is {@code null}
     */
    ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler);

    /**
     * Inserts {@link ChannelHandler}s at the first position of this pipeline.
     *
     * @param handlers  the handlers to insert first
     *
     */
    ChannelPipeline addFirst(ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the first position of this pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                  methods.
     * @param handlers  the handlers to insert first
     *
     */
    ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the last position of this pipeline.
     *
     * @param handlers  the handlers to insert last
     *
     */
    ChannelPipeline addLast(ChannelHandler... handlers);

    /**
     * Inserts {@link ChannelHandler}s at the last position of this pipeline.
     *
     * @param group     the {@link EventExecutorGroup} which will be used to execute the {@link ChannelHandler}s
     *                  methods.
     * @param handlers  the handlers to insert last
     *
     */
    ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers);

    /**
     * Removes the specified {@link ChannelHandler} from this pipeline.
     *
     * @param  handler          the {@link ChannelHandler} to remove
     *
     * @throws NoSuchElementException
     *         if there's no such handler in this pipeline
     * @throws NullPointerException
     *         if the specified handler is {@code null}
     */
    ChannelPipeline remove(ChannelHandler handler);

    /**
     * Removes the {@link ChannelHandler} with the specified name from this pipeline.
     *
     * @param  name             the name under which the {@link ChannelHandler} was stored.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler with the specified name in this pipeline
     * @throws NullPointerException
     *         if the specified name is {@code null}
     */
    ChannelHandler remove(String name);

    /**
     * Removes the {@link ChannelHandler} of the specified type from this pipeline.
     *
     * @param <T>           the type of the handler
     * @param handlerType   the type of the handler
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if there's no such handler of the specified type in this pipeline
     * @throws NullPointerException
     *         if the specified handler type is {@code null}
     */
    <T extends ChannelHandler> T remove(Class<T> handlerType);

    /**
     * Removes the first {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeFirst();

    /**
     * Removes the last {@link ChannelHandler} in this pipeline.
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if this pipeline is empty
     */
    ChannelHandler removeLast();

    /**
     * Replaces the specified {@link ChannelHandler} with a new handler in this pipeline.
     *
     * @param  oldHandler    the {@link ChannelHandler} to be replaced
     * @param  newName       the name under which the replacement should be added
     * @param  newHandler    the {@link ChannelHandler} which is used as replacement
     *
     * @return itself

     * @throws NoSuchElementException
     *         if the specified old handler does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified name with a new handler in this pipeline.
     *
     * @param  oldName       the name of the {@link ChannelHandler} to be replaced
     * @param  newName       the name under which the replacement should be added
     * @param  newHandler    the {@link ChannelHandler} which is used as replacement
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler with the specified old name does not exist in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);

    /**
     * Replaces the {@link ChannelHandler} of the specified type with a new handler in this pipeline.
     *
     * @param  oldHandlerType   the type of the handler to be removed
     * @param  newName          the name under which the replacement should be added
     * @param  newHandler       the {@link ChannelHandler} which is used as replacement
     *
     * @return the removed handler
     *
     * @throws NoSuchElementException
     *         if the handler of the specified old handler type does not exist
     *         in this pipeline
     * @throws IllegalArgumentException
     *         if a handler with the specified new name already exists in this
     *         pipeline, except for the handler to be replaced
     * @throws NullPointerException
     *         if the specified old handler or new handler is
     *         {@code null}
     */
    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName,
                                         ChannelHandler newHandler);

    /**
     * Returns the first {@link ChannelHandler} in this pipeline.
     *
     * @return the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler first();

    /**
     * Returns the context of the first {@link ChannelHandler} in this pipeline.
     *
     * @return the context of the first handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext firstContext();

    /**
     * Returns the last {@link ChannelHandler} in this pipeline.
     *
     * @return the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandler last();

    /**
     * Returns the context of the last {@link ChannelHandler} in this pipeline.
     *
     * @return the context of the last handler.  {@code null} if this pipeline is empty.
     */
    ChannelHandlerContext lastContext();

    /**
     * Returns the {@link ChannelHandler} with the specified name in this
     * pipeline.
     *
     * @return the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandler get(String name);

    /**
     * Returns the {@link ChannelHandler} of the specified type in this
     * pipeline.
     *
     * @return the handler of the specified handler type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    <T extends ChannelHandler> T get(Class<T> handlerType);

    /**
     * Returns the context object of the specified {@link ChannelHandler} in
     * this pipeline.
     *
     * @return the context object of the specified handler.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(ChannelHandler handler);

    /**
     * Returns the context object of the {@link ChannelHandler} with the
     * specified name in this pipeline.
     *
     * @return the context object of the handler with the specified name.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(String name);

    /**
     * Returns the context object of the {@link ChannelHandler} of the
     * specified type in this pipeline.
     *
     * @return the context object of the handler of the specified type.
     *         {@code null} if there's no such handler in this pipeline.
     */
    ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType);

    /**
     * Returns the {@link Channel} that this pipeline is attached to.
     *
     * @return the channel. {@code null} if this pipeline is not attached yet.
     */
    Channel channel();

    /**
     * Returns the {@link List} of the handler names.
     */
    List<String> names();

    /**
     * Converts this pipeline into an ordered {@link Map} whose keys are
     * handler names and whose values are handlers.
     */
    Map<String, ChannelHandler> toMap();

    @Override
    ChannelPipeline fireChannelRegistered();

    @Override
    ChannelPipeline fireChannelUnregistered();

    @Override
    ChannelPipeline fireChannelActive();

    @Override
    ChannelPipeline fireChannelInactive();

    @Override
    ChannelPipeline fireExceptionCaught(Throwable cause);

    @Override
    ChannelPipeline fireUserEventTriggered(Object event);

    /**
     * 读取管道中的信息，
     * @param msg 可能是SocketChannel.也可能是bytebuf
     * @return
     */
    @Override
    ChannelPipeline fireChannelRead(Object msg);

    @Override
    ChannelPipeline fireChannelReadComplete();

    @Override
    ChannelPipeline fireChannelWritabilityChanged();

    @Override
    ChannelPipeline flush();
}
