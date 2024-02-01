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
import io.netty.buffer.ByteBufUtil;

import static io.netty.handler.codec.http.HttpConstants.*;

/**
 * Encodes an {@link HttpResponse} or an {@link HttpContent} into
 * a {@link ByteBuf}.
 *
 * 编码 一个 HttpResponse 或者 HttpContent 到 ByteBuf 中。
 *
 * 用于将服务端的输出的响应进行编码
 *
 * @see io.netty.handler.codec.http.HttpServerCodec.HttpServerResponseEncoder
 */
public class HttpResponseEncoder extends HttpObjectEncoder<HttpResponse> {

    /**
     * 出栈类型判断
     * @param msg
     * @return
     * @throws Exception
     */
    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return super.acceptOutboundMessage(msg) && !(msg instanceof HttpRequest);
    }

    /**
     * 对响应的第一行写入到缓冲区中
     * @param buf
     * @param response
     * @throws Exception
     */
    @Override
    protected void encodeInitialLine(ByteBuf buf, HttpResponse response) throws Exception {
        response.protocolVersion().encode(buf);
        //写入空格
        buf.writeByte(SP);
        //写入状态码
        response.status().encode(buf);
        //写入换行
        ByteBufUtil.writeShortBE(buf, CRLF_SHORT);
    }

    /**
     * 清除Http 头，在编码前
     * @param msg
     * @param isAlwaysEmpty
     */
    @Override
    protected void sanitizeHeadersBeforeEncode(HttpResponse msg, boolean isAlwaysEmpty) {
        if (isAlwaysEmpty) {
            HttpResponseStatus status = msg.status();
            if (status.codeClass() == HttpStatusClass.INFORMATIONAL ||
                    status.code() == HttpResponseStatus.NO_CONTENT.code()) {

                // Stripping Content-Length:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.2
                msg.headers().remove(HttpHeaderNames.CONTENT_LENGTH);

                // Stripping Transfer-Encoding:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.1
                msg.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            } else if (status.code() == HttpResponseStatus.RESET_CONTENT.code()) {

                // Stripping Transfer-Encoding:
                msg.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);

                // Set Content-Length: 0
                // https://httpstatuses.com/205
                msg.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
            }
        }
    }

    /**
     * 根据状态码 来判断 响应 是否有 响应内容
     * @param msg the message to test
     * @return
     */
    @Override
    protected boolean isContentAlwaysEmpty(HttpResponse msg) {
        // Correctly handle special cases as stated in:
        // https://tools.ietf.org/html/rfc7230#section-3.3.3
        HttpResponseStatus status = msg.status();

        if (status.codeClass() == HttpStatusClass.INFORMATIONAL) {
            //判断是否是ws头
            if (status.code() == HttpResponseStatus.SWITCHING_PROTOCOLS.code()) {
                // We need special handling for WebSockets version 00 as it will include an body.
                // Fortunally this version should not really be used in the wild very often.
                // See https://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-00#section-1.2
                return msg.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_VERSION);
            }
            return true;
        }
        return status.code() == HttpResponseStatus.NO_CONTENT.code() ||
                status.code() == HttpResponseStatus.NOT_MODIFIED.code() ||
                status.code() == HttpResponseStatus.RESET_CONTENT.code();
    }
}
