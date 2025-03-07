/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.ReferenceCounted;

/**
 * A packet which is send or receive.
 *
 * 发送或接收的数据包。
 *
 * 主要用于各种协议内容的传输
 *
 * 例如：
 * @see io.netty.handler.codec.http.HttpContent
 */
public interface ByteBufHolder extends ReferenceCounted {

    /**
     * Return the data which is held by this {@link ByteBufHolder}.
     * 返回被 字节缓冲持有的内容
     */
    ByteBuf content();

    /**
     * Creates a deep copy of this {@link ByteBufHolder}.
     * 创建这个对象的深度copy
     */
    ByteBufHolder copy();

    /**
     * Duplicates this {@link ByteBufHolder}. Be aware that this will not automatically call {@link #retain()}.
     *
     * 复制此对象，注意不会调用retain 来增加引用
     */
    ByteBufHolder duplicate();

    /**
     * Duplicates this {@link ByteBufHolder}. This method returns a retained duplicate unlike {@link #duplicate()}.
     *
     * @see ByteBuf#retainedDuplicate()
     */
    ByteBufHolder retainedDuplicate();

    /**
     * Returns a new {@link ByteBufHolder} which contains the specified {@code content}.
     */
    ByteBufHolder replace(ByteBuf content);

    @Override
    ByteBufHolder retain();

    @Override
    ByteBufHolder retain(int increment);

    @Override
    ByteBufHolder touch();

    @Override
    ByteBufHolder touch(Object hint);
}
