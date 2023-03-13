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

/**
 * Abstraction of a byte buffer - the fundamental data structure
 * to represent a low-level binary and text message.
 * 字节缓冲区的抽象，表示低级二进制和文本消息的基本数据结构
 *
 * Netty uses its own buffer API instead of NIO {@link java.nio.ByteBuffer} to
 * represent a sequence of bytes.
 * Netty使用自己的缓冲区API而不是NIO｛@linkjava.NIO.ByteBuffer｝来表示字节序列。
 *
 * This approach has significant advantage over using {@link java.nio.ByteBuffer}.
 * 与使用｛@linkjava.nio.ByteBuffer｝相比，这种方法具有显著的优势。
 *
 * Netty's new buffer type, {@link io.netty.buffer.ByteBuf}, has been designed from ground
 * up to address the problems of {@link java.nio.ByteBuffer} and to meet the
 * daily needs of network application developers.
 *
 * Netty 的新缓冲区，为了从根本上解决ByteBuffer的问题和满足挽留过应用程序开源人员的日常需求而开发的
 *
 * To list a few cool features:
 * <ul>
 *   <li>.You can define your buffer type if necessary //如果需要，可以定义你的buffer类型</li>
 *   <li>Transparent zero copy is achieved by built-in composite buffer type.// 通过 内置复合缓冲区类型实现透明的零copy</li>
 *   <li>A dynamic buffer type is provided out-of-the-box, whose capacity is
 *       expanded on demand, just like {@link java.lang.StringBuffer}. // 自动库容</li>
 *   <li>There's no need to call the {@code flip()} method anymore. //不在需要调用 filp() 方法</li>
 *   <li>It is often faster than {@link java.nio.ByteBuffer}. //通常比 Bytebuffer 更快</li>
 * </ul>
 *
 * <h3>Extensibility</h3>
 * 可扩展性
 *
 * {@link io.netty.buffer.ByteBuf} has rich set of operations
 * optimized for rapid protocol implementation.  For example,
 * {@link io.netty.buffer.ByteBuf} provides various operations
 * for accessing unsigned values and strings and searching for certain byte
 * sequence in a buffer.  You can also extend or wrap existing buffer type
 * to add convenient accessors.  The custom buffer type still implements
 * {@link io.netty.buffer.ByteBuf} interface rather than
 * introducing an incompatible type.
 *
 *
 * <h3>Transparent Zero Copy</h3>
 * 零copy
 *
 * To lift up the performance of a network application to the extreme, you need
 * to reduce the number of memory copy operation.  You might have a set of
 * buffers that could be sliced and combined to compose a whole message.  Netty
 * provides a composite buffer which allows you to create a new buffer from the
 * arbitrary number of existing buffers with no memory copy.  For example, a
 * message could be composed of two parts; header and body.  In a modularized
 * application, the two parts could be produced by different modules and
 * assembled later when the message is sent out.
 * <pre>
 * +--------+----------+
 * | header |   body   |
 * +--------+----------+
 * </pre>
 * If {@link java.nio.ByteBuffer} were used, you would have to create a new big
 * buffer and copy the two parts into the new buffer.   Alternatively, you can
 * perform a gathering write operation in NIO, but it restricts you to represent
 * the composite of buffers as an array of {@link java.nio.ByteBuffer}s rather
 * than a single buffer, breaking the abstraction and introducing complicated
 * state management.  Moreover, it's of no use if you are not going to read or
 * write from an NIO channel.
 * <pre>
 * // The composite type is incompatible with the component type.
 * //NIO 的复合类型与自建类型不兼容
 * ByteBuffer[] message = new ByteBuffer[] { header, body };
 * </pre>
 * By contrast, {@link io.netty.buffer.ByteBuf} does not have such
 * caveats because it is fully extensible and has a built-in composite buffer
 * type.
 * 相比之下，NIO，没有这样的警告 因为它是完全可扩展的，并且具有内置的复合缓冲区。
 * <pre>
 *
 *
 * // The composite type is compatible with the component type.
 * 复合类型跟组件类型是兼容的
 * {@link io.netty.buffer.ByteBuf} message = {@link io.netty.buffer.Unpooled}.wrappedBuffer(header, body);
 *
 * // Therefore, you can even create a composite by mixing a composite and an
 * // ordinary buffer.
 * {@link io.netty.buffer.ByteBuf} messageWithFooter = {@link io.netty.buffer.Unpooled}.wrappedBuffer(message, footer);
 * //因此你甚至可以创建一个复合对象 （通过一个混合复合和普通缓冲区来）
 *
 * // Because the composite is still a {@link io.netty.buffer.ByteBuf}, you can access its content
 * // easily, and the accessor method will behave just like it's a single buffer
 * // even if the region you want to access spans over multiple components.  The
 * // unsigned integer being read here is located across body and footer.
 * messageWithFooter.getUnsignedInt(
 *     messageWithFooter.readableBytes() - footer.readableBytes() - 1);
 * </pre>
 *
 * <h3>Automatic Capacity Extension</h3>
 * 自动容量扩展
 *
 * Many protocols define variable length messages, which means there's no way to
 * determine the length of a message until you construct the message or it is
 * difficult and inconvenient to calculate the length precisely.  It is just
 * like when you build a {@link java.lang.String}. You often estimate the length
 * of the resulting string and let {@link java.lang.StringBuffer} expand itself
 * on demand.
 * 许多协议定义了可变长度消息，这意味着无法确定消息的长度，直到构造消息或精确计算长度既困难又不方便。
 *
 * 这就像构建一个｛@linkjava.lang.String｝时一样。您通常会估计得到的字符串的长度，然后让｛@link java.lang.SStringBuffer｝自行扩展需要
 *
 * <pre>
 * // A new dynamic buffer is created.  Internally, the actual buffer is created
 * // lazily to avoid potentially wasted memory space.
 * {@link io.netty.buffer.ByteBuf} b = {@link io.netty.buffer.Unpooled}.buffer(4);
 *
 * // When the first write attempt is made, the internal buffer is created with
 * // the specified initial capacity (4).
 * b.writeByte('1');
 *
 * b.writeByte('2');
 * b.writeByte('3');
 * b.writeByte('4');
 *
 * // When the number of written bytes exceeds the initial capacity (4), the
 * // internal buffer is reallocated automatically with a larger capacity.
 * b.writeByte('5');
 * </pre>
 *
 * <h3>Better Performance</h3>
 * 更好的性能
 * Most frequently used buffer implementation of
 * {@link io.netty.buffer.ByteBuf} is a very thin wrapper of a
 * byte array (i.e. {@code byte[]}).  Unlike {@link java.nio.ByteBuffer}, it has
 * no complicated boundary check and index compensation, and therefore it is
 * easier for a JVM to optimize the buffer access.  More complicated buffer
 * implementation is used only for sliced or composite buffers, and it performs
 * as well as {@link java.nio.ByteBuffer}.
 */
package io.netty.buffer;
