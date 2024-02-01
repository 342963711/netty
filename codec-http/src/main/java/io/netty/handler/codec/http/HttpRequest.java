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
 * An HTTP request.
 *
 * 一个http 的请求
 *
 * <h3>Accessing Query Parameters and Cookie</h3>
 * 访问请求参数和cookie
 *
 *
 *
 *
 * <p>
 * Unlike the Servlet API, a query string is constructed and decomposed by
 * {@link QueryStringEncoder} and {@link QueryStringDecoder}.
 *
 * 不同于 Servlet API. 一个查询字符串的是由{@link QueryStringEncoder} 和 {@link QueryStringDecoder}
 * 的构造和分解
 *
 *
 * {@link io.netty.handler.codec.http.cookie.Cookie} support is also provided
 * separately via {@link io.netty.handler.codec.http.cookie.ServerCookieDecoder},
 *
 * cookie 通过{@link io.netty.handler.codec.http.cookie.ServerCookieDecoder}单独提供支持
 *
 * {@link io.netty.handler.codec.http.cookie.ClientCookieDecoder},
 * {@link io.netty.handler.codec.http.cookie.ServerCookieEncoder},
 * and {@link io.netty.handler.codec.http.cookie.ClientCookieEncoder}.
 *
 *
 *
 *
 * @see HttpResponse
 * @see io.netty.handler.codec.http.cookie.ServerCookieDecoder
 * @see io.netty.handler.codec.http.cookie.ClientCookieDecoder
 * @see io.netty.handler.codec.http.cookie.ServerCookieEncoder
 * @see io.netty.handler.codec.http.cookie.ClientCookieEncoder
 *
 * @see DefaultHttpRequest
 * @see FullHttpRequest
 * @see io.netty.handler.codec.http.multipart.HttpPostRequestEncoder.WrappedHttpRequest
 */
public interface HttpRequest extends HttpMessage {

    /**
     * @deprecated Use {@link #method()} instead.
     */
    @Deprecated
    HttpMethod getMethod();

    /**
     * Returns the {@link HttpMethod} of this {@link HttpRequest}.
     *
     * @return The {@link HttpMethod} of this {@link HttpRequest}
     */
    HttpMethod method();

    /**
     * Set the {@link HttpMethod} of this {@link HttpRequest}.
     */
    HttpRequest setMethod(HttpMethod method);

    /**
     * @deprecated Use {@link #uri()} instead.
     */
    @Deprecated
    String getUri();

    /**
     * Returns the requested URI (or alternatively, path)
     *
     * @return The URI being requested
     */
    String uri();

    /**
     *  Set the requested URI (or alternatively, path)
     */
    HttpRequest setUri(String uri);

    @Override
    HttpRequest setProtocolVersion(HttpVersion version);
}
