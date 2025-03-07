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

import io.netty.channel.socket.ServerSocketChannel;

/**
 * A {@link Channel} that accepts an incoming connection attempt and creates
 * its child {@link Channel}s by accepting them.  {@link ServerSocketChannel} is
 * a good example.
 *
 * 一个｛@link Channel｝，它接受传入的连接尝试并通过接受它们来创建其子｛@linkChannel｝。
 * ｛@link ServerSocketChannel｝就是一个很好的例子。
 *
 * @see io.netty.channel.epoll.AbstractEpollServerChannel
 * @see io.netty.channel.kqueue.AbstractKQueueServerChannel
 *
 * @see AbstractServerChannel （仅测试）
 *
 *
 * @see ServerSocketChannel
 *
 */
public interface ServerChannel extends Channel {
    // This is a tag interface.
}
