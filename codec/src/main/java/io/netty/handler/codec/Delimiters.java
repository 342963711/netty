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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * A set of commonly used delimiters for {@link DelimiterBasedFrameDecoder}.
 *
 * 一组常用的用于 {@link DelimiterBasedFrameDecoder} 的分割符
 */
public final class Delimiters {

    /**
     * Returns a {@code NUL (0x00)} delimiter, which could be used for
     * Flash XML socket or any similar protocols.
     *
     * 返回｛@code NUL（0x00）｝分隔符，该分隔符可用于
     * Flash XML套接字或任何类似的协议。
     */
    public static ByteBuf[] nulDelimiter() {
        return new ByteBuf[] {
                Unpooled.wrappedBuffer(new byte[] { 0 }) };
    }

    /**
     * Returns {@code CR ('\r')} and {@code LF ('\n')} delimiters, which could
     * be used for text-based line protocols.
     *
     * 返回｛@code CR（'\r'）｝和｛@codeLF（'\n'）}分隔符，它们可以
     * 可用于基于文本的行协议。
     */
    public static ByteBuf[] lineDelimiter() {
        return new ByteBuf[] {
                Unpooled.wrappedBuffer(new byte[] { '\r', '\n' }),
                Unpooled.wrappedBuffer(new byte[] { '\n' }),
        };
    }

    private Delimiters() {
        // Unused
    }
}
