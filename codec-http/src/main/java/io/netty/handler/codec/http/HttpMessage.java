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
package io.netty.handler.codec.http;


/**
 * An interface that defines an HTTP message, providing common properties for
 * {@link HttpRequest} and {@link HttpResponse}.
 * Http 消息的标记接口，为HttpRequest 和 HttpResponse 提供公共属性
 *
 *
 * @see HttpResponse
 * @see HttpRequest http 的请求
 * @see FullHttpMessage
 * @see DefaultHttpMessage 默认抽象类实现
 *
 * 提供http版本和http头
 */
public interface HttpMessage extends HttpObject {

    /**
     * @deprecated Use {@link #protocolVersion()} instead.
     */
    @Deprecated
    HttpVersion getProtocolVersion();

    /**
     * Returns the protocol version of this {@link HttpMessage}
     * http 版本
     */
    HttpVersion protocolVersion();

    /**
     * Set the protocol version of this {@link HttpMessage}
     */
    HttpMessage setProtocolVersion(HttpVersion version);

    /**
     * Returns the headers of this message.
     * http 消息头
     */
    HttpHeaders headers();
}
