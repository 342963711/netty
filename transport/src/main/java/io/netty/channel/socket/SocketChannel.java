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
package io.netty.channel.socket;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;

/**
 * A TCP/IP socket {@link Channel}.
 *
 * TCP、IP 的 套接字管道
 *
 * @see io.netty.channel.socket.nio.NioSocketChannel
 * @see io.netty.channel.epoll.EpollSocketChannel
 * @see io.netty.channel.kqueue.KQueueSocketChannel
 * @see io.netty.channel.socket.oio.OioSocketChannel
 */
public interface SocketChannel extends DuplexChannel {
    @Override
    ServerSocketChannel parent();

    @Override
    SocketChannelConfig config();
    @Override
    InetSocketAddress localAddress();
    @Override
    InetSocketAddress remoteAddress();
}
