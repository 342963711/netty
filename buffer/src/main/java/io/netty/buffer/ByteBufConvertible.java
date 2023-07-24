/*
 * Copyright 2022 The Netty Project
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

/**
 * An interface that can be implemented by any object that know how to turn itself into a {@link ByteBuf}.
 * All {@link ByteBuf} classes implement this interface, and return themselves.
 *
 * 一个接口，可以由任何知道如何将自己变成{@link ByteBuf}的对象实现。
 *
 * 所有｛@link ByteBuf｝类都实现了这个接口，并返回它们自己。
 * @see ByteBuf
 */
public interface ByteBufConvertible {
    /**
     * Turn this object into a {@link ByteBuf}.
     * This does <strong>not</strong> increment the reference count of the {@link ByteBuf} instance.
     * The conversion or exposure of the {@link ByteBuf} must be idempotent, so that this method can be called
     * either once, or multiple times, without causing any change in program behaviour.
     *
     * 将此对象转换为｛@link ByteBuf｝。
     * 这不会</strong>增加｛@link ByteBuf｝实例的引用计数。
     *
     * ｛@link ByteBuf｝的转换或暴露必须是幂等的，这样才能调用此方法
     * 一次或多次，而不会导致程序行为发生任何变化。
     * @return A {@link ByteBuf} instance from this object.
     */
    ByteBuf asByteBuf();
}
