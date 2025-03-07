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
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpConstants.SP;

/**
 * Encodes an {@link HttpRequest} or an {@link HttpContent} into
 * a {@link ByteBuf}.
 *
 * 用于将客户端的输出的请求进行编码
 *
 * @see io.netty.handler.codec.http.HttpClientCodec.Encoder
 */
public class HttpRequestEncoder extends HttpObjectEncoder<HttpRequest> {
    //斜杠
    private static final char SLASH = '/';
    private static final char QUESTION_MARK = '?';
    //斜杠+空格
    private static final int SLASH_AND_SPACE_SHORT = (SLASH << 8) | SP;
    //空格+斜杠+空格
    private static final int SPACE_SLASH_AND_SPACE_MEDIUM = (SP << 16) | SLASH_AND_SPACE_SHORT;

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return super.acceptOutboundMessage(msg) && !(msg instanceof HttpResponse);
    }

    /**
     * 网络传输采用大端序
     * POST /rest/webResources/1.0/resources HTTP/1.1  协议初始行
     * @param buf
     * @param request
     * @throws Exception
     */
    @Override
    protected void encodeInitialLine(ByteBuf buf, HttpRequest request) throws Exception {
        ByteBufUtil.copy(request.method().asciiName(), buf);

        String uri = request.uri();

        if (uri.isEmpty()) {
            // Add " / " as absolute path if uri is not present.
            // See https://tools.ietf.org/html/rfc2616#section-5.1.2
            ByteBufUtil.writeMediumBE(buf, SPACE_SLASH_AND_SPACE_MEDIUM);
        } else {
            CharSequence uriCharSequence = uri;
            boolean needSlash = false;
            int start = uri.indexOf("://");
            if (start != -1 && uri.charAt(0) != SLASH) {
                start += 3;
                // Correctly handle query params.
                // See https://github.com/netty/netty/issues/2732
                int index = uri.indexOf(QUESTION_MARK, start);
                if (index == -1) {
                    if (uri.lastIndexOf(SLASH) < start) {
                        needSlash = true;
                    }
                } else {
                    if (uri.lastIndexOf(SLASH, index) < start) {
                        uriCharSequence = new StringBuilder(uri).insert(index, SLASH);
                    }
                }
            }
            buf.writeByte(SP).writeCharSequence(uriCharSequence, CharsetUtil.UTF_8);
            if (needSlash) {
                // write "/ " after uri
                ByteBufUtil.writeShortBE(buf, SLASH_AND_SPACE_SHORT);
            } else {
                buf.writeByte(SP);
            }
        }
        //进行协议编码
        request.protocolVersion().encode(buf);
        //进行回车换行
        ByteBufUtil.writeShortBE(buf, CRLF_SHORT);
    }
}
