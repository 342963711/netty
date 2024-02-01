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

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Handles an I/O event or intercepts an I/O operation, and forwards it to its next handler in
 * its {@link ChannelPipeline}.
 * 处理I/O事件或截获I/O操作，并将其转发到其｛@link ChannelPipeline｝中的下一个处理程序
 *
 * <h3>Sub-types</h3>
 * 子类型
 *
 * <p>
 * {@link ChannelHandler} itself does not provide many methods, but you usually have to implement one of its subtypes:
 * <ul>
 * <li>{@link ChannelInboundHandler} to handle inbound I/O events, and</li>
 * <li>{@link ChannelOutboundHandler} to handle outbound I/O operations.</li>
 * </ul>
 * </p>
 * ChannelHandler 本身不提供很多方法，你需要实现实现一下一个子类
 * ChannelInboundHandler 处理 入站I/O事件，
 * ChannelOutboundHandler 处理出站 I/O操作
 *
 * <p>
 * Alternatively, the following adapter classes are provided for your convenience:
 * <ul>
 * <li>{@link ChannelInboundHandlerAdapter} to handle inbound I/O events,</li>
 * <li>{@link ChannelOutboundHandlerAdapter} to handle outbound I/O operations, and</li>
 * <li>{@link ChannelDuplexHandler} to handle both inbound and outbound events</li>
 * </ul>
 * </p>
 * 相对应的，以下适配类给你提供便利。
 *
 * <p>
 * For more information, please refer to the documentation of each subtype.
 * </p>
 * 更多信息，需要查看对应子类的文档
 *
 * <h3>The context object</h3>
 * 上下文对象
 *
 * <p>
 * A {@link ChannelHandler} is provided with a {@link ChannelHandlerContext}
 * object.  A {@link ChannelHandler} is supposed to interact with the
 * {@link ChannelPipeline} it belongs to via a context object.  Using the
 * context object, the {@link ChannelHandler} can pass events upstream or
 * downstream, modify the pipeline dynamically, or store the information
 * (using {@link AttributeKey}s) which is specific to the handler.
 *
 * 为ChannelHandler 提供了一个 ChannelHandlerContext 对象。 ChannelHandler应该通过上下文问对象与其所属的 channel 管道交互。
 * 使用上下文对象，｛@link ChannelHandler｝可以向上游或下游传递事件，动态修改管道，或存储特定于处理程序的信息（使用｛@linkAttributeKey｝s）。
 *
 *
 * <h3>State management</h3>
 * 状态管理
 *
 * A {@link ChannelHandler} often needs to store some stateful information.
 * The simplest and recommended approach is to use member variables:
 *
 * {@link ChannelHandler｝通常需要存储一些有状态的信息。最简单和推荐的方法是使用成员变量：
 *
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *
 *     <b>private boolean loggedIn;</b>
 *
 *     {@code @Override}
 *     public void channelRead0({@link ChannelHandlerContext} ctx, Message message) {
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) message);
 *             <b>loggedIn = true;</b>
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>loggedIn</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) message));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Because the handler instance has a state variable which is dedicated to
 * one connection, you have to create a new handler instance for each new
 * channel to avoid a race condition where an unauthenticated client can get
 * the confidential information:
 *
 * 因为处理程序实例有一个状态变量，该变量专用于对于一个连接，您必须为每个新的连接创建一个新的处理程序实例
 * 通道，以避免未经身份验证的客户端可以获得机密信息的竞争条件：
 *
 * <pre>
 * // Create a new handler instance per channel.
 * 每一个channel 实例都创建 一个新的 处理程序
 * // See {@link ChannelInitializer#initChannel(Channel)}.
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>new DataServerHandler()</b>);
 *     }
 * }
 *
 * </pre>
 *
 * <h4>Using {@link AttributeKey}s</h4>
 * 使用 AttributeKey
 *
 * Although it's recommended to use member variables to store the state of a
 * handler, for some reason you might not want to create many handler instances.
 * In such a case, you can use {@link AttributeKey}s which is provided by
 * {@link ChannelHandlerContext}:
 *
 * 尽管建议使用成员变量来存储处理程序的状态，但由于某些原因，您可能不想创建许多处理程序实例。
 * 在这种情况下，您可以使用由ChannelHandlerContext提供的｛@link AttributeKey｝
 *
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *     private final {@link AttributeKey}&lt;{@link Boolean}&gt; auth =
 *           {@link AttributeKey#valueOf(String) AttributeKey.valueOf("auth")};
 *
 *     {@code @Override}
 *     public void channelRead({@link ChannelHandlerContext} ctx, Message message) {
 *         {@link Attribute}&lt;{@link Boolean}&gt; attr = ctx.attr(auth);
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>attr.set(true)</b>;
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>Boolean.TRUE.equals(attr.get())</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Now that the state of the handler is attached to the {@link ChannelHandlerContext}, you can add the
 * same handler instance to different pipelines:
 *
 * 现在处理程序的状态已附加到{@link ChannelHandlerContext}，您可以添加不同管道的相同处理程序实例
 *
 * <pre>
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *     private static final DataServerHandler <b>SHARED</b> = new DataServerHandler();
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>SHARED</b>);
 *     }
 * }
 * </pre>
 *
 *
 * <h4>The {@code @Sharable} annotation</h4>
 * Sharable 注解
 *
 * <p>
 * In the example above which used an {@link AttributeKey},
 * you might have noticed the {@code @Sharable} annotation.
 *
 * 在上面使用{@link AttributeKey}的示例中，您可能已经注意到了{@code@Sharable}注释
 *
 * <p>
 * If a {@link ChannelHandler} is annotated with the {@code @Sharable}
 * annotation, it means you can create an instance of the handler just once and
 * add it to one or more {@link ChannelPipeline}s multiple times without
 * a race condition.
 *
 * 如果｛@link ChannelHandler｝使用｛@code@Sharable｝进行注释
 *注释，这意味着您可以只创建一次处理程序的实例，并多次将其添加到一个或多个｛@link ChannelPipeline｝中，而不存在竞争条件。
 *
 * <p>
 * If this annotation is not specified, you have to create a new handler
 * instance every time you add it to a pipeline because it has unshared state
 * such as member variables.
 *
 * 如果未指定此注释，则必须创建一个新的处理程序实例，因为它具有非共享状态，例如成员变量。
 * <p>
 * This annotation is provided for documentation purpose, just like
 * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
 * 此注释是出于文档目的提供的，就像...
 *
 *
 * <h3>Additional resources worth reading</h3>
 * 其他参考资源
 *
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 *
 *
 * 请参阅｛@link ChannelHandler｝，以及｛@link ChannelPipeline｝以了解有关入站和出站操作的更多信息，
 * 它们有哪些基本区别，它们在管道中的流动方式，以及如何在应用程序中处理操作
 *
 * {@link ChannelHandlerAdapter}
 *
 * {@link ChannelInboundHandler}
 * {@link ChannelOutboundHandler}
 *
 * 关于ChannelHandler 所属，可参考 {@link ChannelHandlerContext}
 */
public interface ChannelHandler {

    /**
     * Gets called after the {@link ChannelHandler} was added to the actual context and it's ready to handle events.
     *
     * 在将｛@link ChannelHandler｝添加到实际上下文并准备好处理事件后调用。也就是该ChannelHandler 被添加到channelPipeline 之后，
     * 该方法会被调用
     *
     * 当该ChannelHandler 所在的 ctx 被调用的时候，该方法后自行。
     *
     * 可以查看
     * {@link DefaultChannelPipeline#addFirst(ChannelHandler)}
     * {@link DefaultChannelPipeline#callHandlerAdded0(AbstractChannelHandlerContext)}
     */
    void handlerAdded(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called after the {@link ChannelHandler} was removed from the actual context and it doesn't handle events
     * anymore.
     *
     * 在从实际上下文中删除｛@link ChannelHandler｝并且它不再处理事件后调用。
     */
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called if a {@link Throwable} was thrown.
     *
     * @deprecated if you want to handle this event you should implement {@link ChannelInboundHandler} and
     * implement the method there.
     * 如果你想处理这个事件，你应该实现{@link ChannelInboundHandler}并在那里实现方法
     */
    @Deprecated
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    /**
     * Indicates that the same instance of the annotated {@link ChannelHandler}
     * can be added to one or more {@link ChannelPipeline}s multiple times
     * without a race condition.
     * <p>
     * If this annotation is not specified, you have to create a new handler
     * instance every time you add it to a pipeline because it has unshared
     * state such as member variables.
     * <p>
     * This annotation is provided for documentation purpose, just like
     * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
     */
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sharable {
        // no value
    }
}
