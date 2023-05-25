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

import io.netty.util.concurrent.OrderedEventExecutor;

/**
 * Will handle all the I/O operations for a {@link Channel} once registered.
 * 一旦注册，将处理Channel 所有的I/O操作
 *
 * One {@link EventLoop} instance will usually handle more than one {@link Channel} but this may depend on
 * implementation details and internals.
 *
 * 一个EventLoop 实例 通常会处理多个Channel，但这可能取决于实现细节和内部结构
 *
 *
 * @see AbstractEventLoop EvenntLoop 接口的基本实现抽象类，该抽象类没有实现，不重要
 * @see io.netty.channel.embedded.EmbeddedEventLoop
 * @see SingleThreadEventLoop EventLoop 接口 的实现 基本抽象类，在单个线程中执行其提交的所有任务。核心实现类
 *
 */
public interface EventLoop extends OrderedEventExecutor, EventLoopGroup {
    @Override
    EventLoopGroup parent();
}
