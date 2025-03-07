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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CombinedChannelDuplexHandler;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;

/**
 * A combination of {@link HttpRequestDecoder} and {@link HttpResponseEncoder}
 * which enables easier server side HTTP implementation.
 *
 * {@link HttpRequestDecoder}和{@link HttpResponseEncoder}的组合,这使得能够更容易地实现服务器端HTTP。
 *
 * @see HttpClientCodec
 */
public final class HttpServerCodec extends CombinedChannelDuplexHandler<HttpRequestDecoder, HttpResponseEncoder>
        implements HttpServerUpgradeHandler.SourceCodec {

    /** A queue that is used for correlating a request and a response. */
    /**
     * 用来关联，请求和响应的队列
     */
    private final Queue<HttpMethod> queue = new ArrayDeque<HttpMethod>();

    /**
     * Creates a new instance with the default decoder options
     * ({@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}).
     */
    public HttpServerCodec() {
        this(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_CHUNK_SIZE);
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
        init(new HttpServerRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize),
                new HttpServerResponseEncoder());
    }

    /**
     * Creates a new instance with the specified decoder options.
     * @param maxInitialLineLength  默认值 4096
     * @param maxHeaderSize 8192
     * @param maxChunkSize 8192
     */
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders) {
        init(new HttpServerRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders),
                new HttpServerResponseEncoder());
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders,
                           int initialBufferSize) {
        init(
          new HttpServerRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize,
                  validateHeaders, initialBufferSize),
          new HttpServerResponseEncoder());
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders,
                           int initialBufferSize, boolean allowDuplicateContentLengths) {
        init(new HttpServerRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders,
                                          initialBufferSize, allowDuplicateContentLengths),
             new HttpServerResponseEncoder());
    }

    /**
     * Creates a new instance with the specified decoder options.
     */
    public HttpServerCodec(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean validateHeaders,
                           int initialBufferSize, boolean allowDuplicateContentLengths, boolean allowPartialChunks) {
        init(new HttpServerRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders,
                                          initialBufferSize, allowDuplicateContentLengths, allowPartialChunks),
             new HttpServerResponseEncoder());
    }

    /**
     * Upgrades to another protocol from HTTP. Removes the {@link HttpRequestDecoder} and
     * {@link HttpResponseEncoder} from the pipeline.
     */
    @Override
    public void upgradeFrom(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(this);
    }

    /**
     * Httpserver 对http请求的解码类
     */
    private final class HttpServerRequestDecoder extends HttpRequestDecoder {

        HttpServerRequestDecoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
            super(maxInitialLineLength, maxHeaderSize, maxChunkSize);
        }

        HttpServerRequestDecoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
                                        boolean validateHeaders) {
            super(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders);
        }

        HttpServerRequestDecoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,

                                        boolean validateHeaders, int initialBufferSize) {
            super(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders, initialBufferSize);
        }

        HttpServerRequestDecoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
                                 boolean validateHeaders, int initialBufferSize, boolean allowDuplicateContentLengths) {
            super(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders, initialBufferSize,
                  allowDuplicateContentLengths);
        }

        HttpServerRequestDecoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
                                 boolean validateHeaders, int initialBufferSize, boolean allowDuplicateContentLengths,
                                 boolean allowPartialChunks) {
            super(maxInitialLineLength, maxHeaderSize, maxChunkSize, validateHeaders, initialBufferSize,
                  allowDuplicateContentLengths, allowPartialChunks);
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
            int oldSize = out.size();
            super.decode(ctx, buffer, out);
            int size = out.size();
            for (int i = oldSize; i < size; i++) {
                Object obj = out.get(i);
                if (obj instanceof HttpRequest) {
                    queue.add(((HttpRequest) obj).method());
                }
            }
        }
    }

    /**
     * httpserver 对 http 响应 的编码类
     */
    private final class HttpServerResponseEncoder extends HttpResponseEncoder {

        private HttpMethod method;

        /**
         * 响应进行编码前 需要清除无用的头节点
         * @param msg
         * @param isAlwaysEmpty
         */
        @Override
        protected void sanitizeHeadersBeforeEncode(HttpResponse msg, boolean isAlwaysEmpty) {
            if (!isAlwaysEmpty && HttpMethod.CONNECT.equals(method)
                    && msg.status().codeClass() == HttpStatusClass.SUCCESS) {
                // Stripping Transfer-Encoding:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.1
                msg.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
                return;
            }

            super.sanitizeHeadersBeforeEncode(msg, isAlwaysEmpty);
        }

        /**
         *
         * @param msg the message to test
         * @return
         */
        @Override
        protected boolean isContentAlwaysEmpty(@SuppressWarnings("unused") HttpResponse msg) {
            //获取当前响应对应的请求方法
            method = queue.poll();
            // 如果是HEAD 方法，表示 响应内容总是空的
            return HttpMethod.HEAD.equals(method) || super.isContentAlwaysEmpty(msg);
        }
    }
}
