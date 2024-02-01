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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.internal.StringUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import static io.netty.buffer.Unpooled.directBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;

/**
 * Encodes an {@link HttpMessage} or an {@link HttpContent} into
 * a {@link ByteBuf}.
 *
 * 编码一个 HttpMessage 或 HttpContent 到 ByteBuf
 *
 * <h3>Extensibility</h3>
 * 扩展性
 *
 * Please note that this encoder is designed to be extended to implement
 * a protocol derived from HTTP, such as
 *
 * <a href="https://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="https://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 * To implement the encoder of such a derived protocol, extend this class and
 * implement all abstract methods properly.
 *
 * 注意：这个编码被设计为可扩展 是为了扩展实现Http的衍生协议
 * 例如 RTSP 和 ICAP 协议实现了 衍生协议编码。
 *
 * @see HttpRequestEncoder
 * @see HttpResponseEncoder
 */
public abstract class HttpObjectEncoder<H extends HttpMessage> extends MessageToMessageEncoder<Object> {
    //回车换行的 两字节表示
    static final int CRLF_SHORT = (CR << 8) | LF;

    private static final int ZERO_CRLF_MEDIUM = ('0' << 16) | CRLF_SHORT;

    private static final byte[] ZERO_CRLF_CRLF = { '0', CR, LF, CR, LF };
    //回车换行
    private static final ByteBuf CRLF_BUF = unreleasableBuffer(
            directBuffer(2).writeByte(CR).writeByte(LF)).asReadOnly();
    // 尾部补充后，进行两次回车换行，也就是有一个空行
    private static final ByteBuf ZERO_CRLF_CRLF_BUF = unreleasableBuffer(
            directBuffer(ZERO_CRLF_CRLF.length).writeBytes(ZERO_CRLF_CRLF)).asReadOnly();

    private static final float HEADERS_WEIGHT_NEW = 1 / 5f;
    private static final float HEADERS_WEIGHT_HISTORICAL = 1 - HEADERS_WEIGHT_NEW;
    private static final float TRAILERS_WEIGHT_NEW = HEADERS_WEIGHT_NEW;
    private static final float TRAILERS_WEIGHT_HISTORICAL = HEADERS_WEIGHT_HISTORICAL;

    /**
     * 编码状态
     */
    //初始化
    private static final int ST_INIT = 0;
    //内容非块
    private static final int ST_CONTENT_NON_CHUNK = 1;
    //内容是块
    private static final int ST_CONTENT_CHUNK = 2;
    //内容总是空的
    private static final int ST_CONTENT_ALWAYS_EMPTY = 3;

    @SuppressWarnings("RedundantFieldInitialization")
    private int state = ST_INIT;

    /**
     * Used to calculate an exponential moving average of the encoded size of the initial line and the headers for
     * a guess for future buffer allocations.
     */
    private float headersEncodedSizeAccumulator = 256;

    /**
     * Used to calculate an exponential moving average of the encoded size of the trailers for
     * a guess for future buffer allocations.
     */
    private float trailersEncodedSizeAccumulator = 256;

    /**
     * 出栈对象
     */
    private final List<Object> out = new ArrayList<Object>();

    private static boolean checkContentState(int state) {
        return state == ST_CONTENT_CHUNK || state == ST_CONTENT_NON_CHUNK || state == ST_CONTENT_ALWAYS_EMPTY;
    }

    /**
     * 重写父类的 出栈 方法模板，
     * @param ctx               the {@link ChannelHandlerContext} for which the write operation is made
     * @param msg               the message to write
     * @param promise           the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            if (acceptOutboundMessage(msg)) {
                //对写的内容进行编码
                encode(ctx, msg, out);
                if (out.isEmpty()) {
                    throw new EncoderException(
                            StringUtil.simpleClassName(this) + " must produce at least one message.");
                }
            } else {
                //如果Http 的所有对象都不是，进行下传
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable t) {
            throw new EncoderException(t);
        } finally {
            writeOutList(ctx, out, promise);
        }
    }

    private static void writeOutList(ChannelHandlerContext ctx, List<Object> out, ChannelPromise promise) {
        final int size = out.size();
        try {
            if (size == 1) {
                ctx.write(out.get(0), promise);
            } else if (size > 1) {
                // Check if we can use a voidPromise for our extra writes to reduce GC-Pressure
                // See https://github.com/netty/netty/issues/2525
                if (promise == ctx.voidPromise()) {
                    writeVoidPromise(ctx, out);
                } else {
                    writePromiseCombiner(ctx, out, promise);
                }
            }
        } finally {
            out.clear();
        }
    }

    private static void writeVoidPromise(ChannelHandlerContext ctx, List<Object> out) {
        final ChannelPromise voidPromise = ctx.voidPromise();
        for (int i = 0; i < out.size(); i++) {
            ctx.write(out.get(i), voidPromise);
        }
    }

    private static void writePromiseCombiner(ChannelHandlerContext ctx, List<Object> out, ChannelPromise promise) {
        final PromiseCombiner combiner = new PromiseCombiner(ctx.executor());
        for (int i = 0; i < out.size(); i++) {
            combiner.add(ctx.write(out.get(i)));
        }
        combiner.finish(promise);
    }

    /**
     * 模板方法，根据编码对象的不同，去实现不同的编码器，或者提供一个模板，可以可以重写，并且使用该类进行自定义
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageEncoder} belongs to
     * @param msg           the message to encode to an other one
     * @param out           the {@link List} into which the encoded msg should be added
     *                      needs to do some kind of aggregation
     * @throws Exception
     */
    @Override
    @SuppressWarnings("ConditionCoveredByFurtherCondition")
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        // fast-path for common idiom that doesn't require class-checks
        if (msg == Unpooled.EMPTY_BUFFER) {
            out.add(Unpooled.EMPTY_BUFFER);
            return;
        }
        // The reason why we perform instanceof checks in this order,
        // by duplicating some code and without relying on ReferenceCountUtil::release as a generic release
        // mechanism, is https://bugs.openjdk.org/browse/JDK-8180450.
        // https://github.com/netty/netty/issues/12708 contains more detail re how the previous version of this
        // code was interacting with the JIT instanceof optimizations.
        /**
         * 我们之所以按此顺序执行instanceof检查，
         * 通过复制一些代码，并且不依赖ReferenceCountUtil:：release作为通用发布机制https://bugs.openjdk.org/browse/JDK-8180450.
         * https://github.com/netty/netty/issues/12708包含有关此代码的前一版本如何与JIT优化实例交互的更多详细信息。
         */
        // 如果是完整的消息体
        if (msg instanceof FullHttpMessage) {
            encodeFullHttpMessage(ctx, msg, out);
            return;
        }
        // 如果是HTTP协议的基本内容
        if (msg instanceof HttpMessage) {
            final H m;
            try {
                m = (H) msg;
            } catch (Exception rethrow) {
                ReferenceCountUtil.release(msg);
                throw rethrow;
            }
            //如果协议同时也包含最后的内容，则进行内容编码
            if (m instanceof LastHttpContent) {
                encodeHttpMessageLastContent(ctx, m, out);
            //判断是否也是最后的内容体
            } else if (m instanceof HttpContent) {
                encodeHttpMessageNotLastContent(ctx, m, out);
            } else {
                //如果不包含内容，则只对协议本身进行编码
                encodeJustHttpMessage(ctx, m, out);
            }
        } else {
            //对不是httpMessage 的内容类型进行编码
            encodeNotHttpMessageContentTypes(ctx, msg, out);
        }
    }

    private void encodeJustHttpMessage(ChannelHandlerContext ctx, H m, List<Object> out) throws Exception {
        assert !(m instanceof HttpContent);
        try {
            if (state != ST_INIT) {
                throwUnexpectedMessageTypeEx(m, state);
            }
            final ByteBuf buf = encodeInitHttpMessage(ctx, m);

            assert checkContentState(state);

            out.add(buf);
        } finally {
            ReferenceCountUtil.release(m);
        }
    }

    /**
     * 对HTTP协议的内容进行编码
     * @param state
     * @param ctx
     * @param buf
     * @param content
     * @param trailingHeaders
     * @param out
     */
    private void encodeByteBufHttpContent(int state, ChannelHandlerContext ctx, ByteBuf buf, ByteBuf content,
                                          HttpHeaders trailingHeaders, List<Object> out) {
        switch (state) {
            //如果没有分块
            case ST_CONTENT_NON_CHUNK:
                if (encodeContentNonChunk(out, buf, content)) {
                    break;
                }
                // fall-through!
            case ST_CONTENT_ALWAYS_EMPTY:
                // We allocated a buffer so add it now.
                out.add(buf);
                break;
            case ST_CONTENT_CHUNK:
                // We allocated a buffer so add it now.
                out.add(buf);
                encodeChunkedHttpContent(ctx, content, trailingHeaders, out);
                break;
            default:
                throw new Error();
        }
    }

    /**
     * 对不是最后的内容进行编码
     * @param ctx
     * @param m
     * @param out
     * @throws Exception
     */
    private void encodeHttpMessageNotLastContent(ChannelHandlerContext ctx, H m, List<Object> out) throws Exception {
        assert m instanceof HttpContent;
        assert !(m instanceof LastHttpContent);
        final HttpContent httpContent = (HttpContent) m;
        try {
            if (state != ST_INIT) {
                throwUnexpectedMessageTypeEx(m, state);
            }
            final ByteBuf buf = encodeInitHttpMessage(ctx, m);

            assert checkContentState(state);

            encodeByteBufHttpContent(state, ctx, buf, httpContent.content(), null, out);
        } finally {
            httpContent.release();
        }
    }

    /**
     * 对httpMessage 的最后一个内容进行编码
     * @param ctx
     * @param m
     * @param out
     * @throws Exception
     */
    private void encodeHttpMessageLastContent(ChannelHandlerContext ctx, H m, List<Object> out) throws Exception {
        assert m instanceof LastHttpContent;
        final LastHttpContent httpContent = (LastHttpContent) m;
        try {
            if (state != ST_INIT) {
                throwUnexpectedMessageTypeEx(m, state);
            }
            final ByteBuf buf = encodeInitHttpMessage(ctx, m);

            assert checkContentState(state);

            encodeByteBufHttpContent(state, ctx, buf, httpContent.content(), httpContent.trailingHeaders(), out);

            state = ST_INIT;
        } finally {
            httpContent.release();
        }
    }

    /**
     * 对不是HttpMessage 类型的消息内容类型进行编码
     * @param ctx
     * @param msg
     * @param out
     */
    @SuppressWarnings("ConditionCoveredByFurtherCondition")
    private void encodeNotHttpMessageContentTypes(ChannelHandlerContext ctx, Object msg, List<Object> out) {
        assert !(msg instanceof HttpMessage);
        if (state == ST_INIT) {
            try {
                if (msg instanceof ByteBuf && bypassEncoderIfEmpty((ByteBuf) msg, out)) {
                    return;
                }
                throwUnexpectedMessageTypeEx(msg, ST_INIT);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
        if (msg == LastHttpContent.EMPTY_LAST_CONTENT) {
            state = encodeEmptyLastHttpContent(state, out);
            return;
        }
        if (msg instanceof LastHttpContent) {
            encodeLastHttpContent(ctx, (LastHttpContent) msg, out);
            return;
        }
        if (msg instanceof HttpContent) {
            encodeHttpContent(ctx, (HttpContent) msg, out);
            return;
        }
        if (msg instanceof ByteBuf) {
            encodeByteBufContent(ctx, (ByteBuf) msg, out);
            return;
        }
        if (msg instanceof FileRegion) {
            encodeFileRegionContent(ctx, (FileRegion) msg, out);
            return;
        }
        try {
            throwUnexpectedMessageTypeEx(msg, state);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    /**
     * 对全部的http 进行编码，包含协议基本内容，请求头，请求内容
     * @param ctx
     * @param o
     * @param out
     * @throws Exception
     */
    private void encodeFullHttpMessage(ChannelHandlerContext ctx, Object o, List<Object> out)
            throws Exception {
        assert o instanceof FullHttpMessage;
        final FullHttpMessage msg = (FullHttpMessage) o;
        try {
            if (state != ST_INIT) {
                throwUnexpectedMessageTypeEx(o, state);
            }

            final H m = (H) o;

            final ByteBuf buf = ctx.alloc().buffer((int) headersEncodedSizeAccumulator);
            //进行Http 协议的初始化，
            encodeInitialLine(buf, m);
            //检查编码对象的状态
            final int state = isContentAlwaysEmpty(m) ? ST_CONTENT_ALWAYS_EMPTY :
                    HttpUtil.isTransferEncodingChunked(m) ? ST_CONTENT_CHUNK : ST_CONTENT_NON_CHUNK;
            //编码前对请求头进行清除
            sanitizeHeadersBeforeEncode(m, state == ST_CONTENT_ALWAYS_EMPTY);
            //对HTTP 头 进行编码
            encodeHeaders(m.headers(), buf);
            ByteBufUtil.writeShortBE(buf, CRLF_SHORT);

            headersEncodedSizeAccumulator = HEADERS_WEIGHT_NEW * padSizeForAccumulation(buf.readableBytes()) +
                    HEADERS_WEIGHT_HISTORICAL * headersEncodedSizeAccumulator;
            //对httpContent 进行编码
            encodeByteBufHttpContent(state, ctx, buf, msg.content(), msg.trailingHeaders(), out);
        } finally {
            msg.release();
        }
    }

    private static boolean encodeContentNonChunk(List<Object> out, ByteBuf buf, ByteBuf content) {
        final int contentLength = content.readableBytes();
        if (contentLength > 0) {
            if (buf.writableBytes() >= contentLength) {
                // merge into other buffer for performance reasons
                buf.writeBytes(content);
                out.add(buf);
            } else {
                out.add(buf);
                out.add(content.retain());
            }
            return true;
        }
        return false;
    }

    private static void throwUnexpectedMessageTypeEx(Object msg, int state) {
        throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg)
                + ", state: " + state);
    }

    private void encodeFileRegionContent(ChannelHandlerContext ctx, FileRegion msg, List<Object> out) {
        try {
            assert state != ST_INIT;
            switch (state) {
                case ST_CONTENT_NON_CHUNK:
                    if (msg.count() > 0) {
                        out.add(msg.retain());
                        break;
                    }

                    // fall-through!
                case ST_CONTENT_ALWAYS_EMPTY:
                    // Need to produce some output otherwise an
                    // IllegalStateException will be thrown as we did not write anything
                    // Its ok to just write an EMPTY_BUFFER as if there are reference count issues these will be
                    // propagated as the caller of the encode(...) method will release the original
                    // buffer.
                    // Writing an empty buffer will not actually write anything on the wire, so if there is a user
                    // error with msg it will not be visible externally
                    out.add(Unpooled.EMPTY_BUFFER);
                    break;
                case ST_CONTENT_CHUNK:
                    encodedChunkedFileRegionContent(ctx, msg, out);
                    break;
                default:
                    throw new Error();
            }
        } finally {
            msg.release();
        }
    }

    // Bypass the encoder in case of an empty buffer, so that the following idiom works:
    //
    //     ch.write(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    //
    // See https://github.com/netty/netty/issues/2983 for more information.
    private static boolean bypassEncoderIfEmpty(ByteBuf msg, List<Object> out) {
        if (!msg.isReadable()) {
            out.add(msg.retain());
            return true;
        }
        return false;
    }

    private void encodeByteBufContent(ChannelHandlerContext ctx, ByteBuf content, List<Object> out) {
        try {
            assert state != ST_INIT;
            if (bypassEncoderIfEmpty(content, out)) {
                return;
            }
            encodeByteBufAndTrailers(state, ctx, out, content, null);
        } finally {
            content.release();
        }
    }

    private static int encodeEmptyLastHttpContent(int state, List<Object> out) {
        assert state != ST_INIT;

        switch (state) {
            case ST_CONTENT_NON_CHUNK:
            case ST_CONTENT_ALWAYS_EMPTY:
                out.add(Unpooled.EMPTY_BUFFER);
                break;
            case ST_CONTENT_CHUNK:
                out.add(ZERO_CRLF_CRLF_BUF.duplicate());
                break;
            default:
                throw new Error();
        }
        return ST_INIT;
    }

    private void encodeLastHttpContent(ChannelHandlerContext ctx, LastHttpContent msg, List<Object> out) {
        assert state != ST_INIT;
        assert !(msg instanceof HttpMessage);
        try {
            encodeByteBufAndTrailers(state, ctx, out, msg.content(), msg.trailingHeaders());
            state = ST_INIT;
        } finally {
            msg.release();
        }
    }

    private void encodeHttpContent(ChannelHandlerContext ctx, HttpContent msg, List<Object> out) {
        assert state != ST_INIT;
        assert !(msg instanceof HttpMessage);
        assert !(msg instanceof LastHttpContent);
        try {
            this.encodeByteBufAndTrailers(state, ctx, out, msg.content(), null);
        } finally {
            msg.release();
        }
    }

    private void encodeByteBufAndTrailers(int state, ChannelHandlerContext ctx, List<Object> out, ByteBuf content,
                                          HttpHeaders trailingHeaders) {
        switch (state) {
            case ST_CONTENT_NON_CHUNK:
                if (content.isReadable()) {
                    out.add(content.retain());
                    break;
                }
                // fall-through!
            case ST_CONTENT_ALWAYS_EMPTY:
                out.add(Unpooled.EMPTY_BUFFER);
                break;
            case ST_CONTENT_CHUNK:
                encodeChunkedHttpContent(ctx, content, trailingHeaders, out);
                break;
            default:
                throw new Error();
        }
    }

    /**
     * 对HTTP 分块内容进行编码
     * @param ctx
     * @param content
     * @param trailingHeaders
     * @param out
     */
    private void encodeChunkedHttpContent(ChannelHandlerContext ctx, ByteBuf content, HttpHeaders trailingHeaders,
                                          List<Object> out) {
        final int contentLength = content.readableBytes();
        if (contentLength > 0) {
            //增加编码大小
            addEncodedLengthHex(ctx, contentLength, out);
            //增加编码内容
            out.add(content.retain());
            //增加换行
            out.add(CRLF_BUF.duplicate());
        }
        if (trailingHeaders != null) {
            encodeTrailingHeaders(ctx, trailingHeaders, out);
        } else if (contentLength == 0) {
            // Need to produce some output otherwise an
            // IllegalStateException will be thrown
            out.add(content.retain());
        }
    }

    private void encodeTrailingHeaders(ChannelHandlerContext ctx, HttpHeaders trailingHeaders, List<Object> out) {
        if (trailingHeaders.isEmpty()) {
            out.add(ZERO_CRLF_CRLF_BUF.duplicate());
        } else {
            ByteBuf buf = ctx.alloc().buffer((int) trailersEncodedSizeAccumulator);
            ByteBufUtil.writeMediumBE(buf, ZERO_CRLF_MEDIUM);
            encodeHeaders(trailingHeaders, buf);
            ByteBufUtil.writeShortBE(buf, CRLF_SHORT);
            trailersEncodedSizeAccumulator = TRAILERS_WEIGHT_NEW * padSizeForAccumulation(buf.readableBytes()) +
                    TRAILERS_WEIGHT_HISTORICAL * trailersEncodedSizeAccumulator;
            out.add(buf);
        }
    }

    private ByteBuf encodeInitHttpMessage(ChannelHandlerContext ctx, H m) throws Exception {
        assert state == ST_INIT;

        ByteBuf buf = ctx.alloc().buffer((int) headersEncodedSizeAccumulator);
        // Encode the message.
        encodeInitialLine(buf, m);
        state = isContentAlwaysEmpty(m) ? ST_CONTENT_ALWAYS_EMPTY :
                HttpUtil.isTransferEncodingChunked(m) ? ST_CONTENT_CHUNK : ST_CONTENT_NON_CHUNK;

        sanitizeHeadersBeforeEncode(m, state == ST_CONTENT_ALWAYS_EMPTY);

        encodeHeaders(m.headers(), buf);
        ByteBufUtil.writeShortBE(buf, CRLF_SHORT);

        headersEncodedSizeAccumulator = HEADERS_WEIGHT_NEW * padSizeForAccumulation(buf.readableBytes()) +
                HEADERS_WEIGHT_HISTORICAL * headersEncodedSizeAccumulator;
        return buf;
    }

    /**
     * Encode the {@link HttpHeaders} into a {@link ByteBuf}.
     */
    protected void encodeHeaders(HttpHeaders headers, ByteBuf buf) {
        Iterator<Entry<CharSequence, CharSequence>> iter = headers.iteratorCharSequence();
        while (iter.hasNext()) {
            Entry<CharSequence, CharSequence> header = iter.next();
            HttpHeadersEncoder.encoderHeader(header.getKey(), header.getValue(), buf);
        }
    }

    private static void encodedChunkedFileRegionContent(ChannelHandlerContext ctx, FileRegion msg, List<Object> out) {
        final long contentLength = msg.count();
        if (contentLength > 0) {
            addEncodedLengthHex(ctx, contentLength, out);
            out.add(msg.retain());
            out.add(CRLF_BUF.duplicate());
        } else if (contentLength == 0) {
            // Need to produce some output otherwise an
            // IllegalStateException will be thrown
            out.add(msg.retain());
        }
    }

    //增加编码长度
    private static void addEncodedLengthHex(ChannelHandlerContext ctx, long contentLength, List<Object> out) {
        String lengthHex = Long.toHexString(contentLength);
        ByteBuf buf = ctx.alloc().buffer(lengthHex.length() + 2);
        buf.writeCharSequence(lengthHex, CharsetUtil.US_ASCII);
        ByteBufUtil.writeShortBE(buf, CRLF_SHORT);
        out.add(buf);
    }

    /**
     * Allows to sanitize headers of the message before encoding these.
     *
     * 允许在对邮件标头进行编码之前对其进行清理。
     */
    protected void sanitizeHeadersBeforeEncode(@SuppressWarnings("unused") H msg, boolean isAlwaysEmpty) {
        // noop
    }

    /**
     * Determine whether a message has a content or not. Some message may have headers indicating
     * a content without having an actual content, e.g the response to an HEAD or CONNECT request.
     *  确定消息是否包含内容。
     * 某些消息可能具有指示没有实际内容的内容的报头，例如对HEAD或CONNECT请求的响应。
     * @param msg the message to test
     * @return {@code true} to signal the message has no content 如果为true，表示没有内容
     */
    protected boolean isContentAlwaysEmpty(@SuppressWarnings("unused") H msg) {
        return false;
    }

    @Override
    @SuppressWarnings("ConditionCoveredByFurtherCondition")
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg == Unpooled.EMPTY_BUFFER ||
                msg == LastHttpContent.EMPTY_LAST_CONTENT ||
                msg instanceof FullHttpMessage ||
                msg instanceof HttpMessage ||
                msg instanceof LastHttpContent ||
                msg instanceof HttpContent ||
                msg instanceof ByteBuf || msg instanceof FileRegion;
    }

    /**
     * Add some additional overhead to the buffer. The rational is that it is better to slightly over allocate and waste
     * some memory, rather than under allocate and require a resize/copy.
     *
     * @param readableBytes The readable bytes in the buffer.
     * @return The {@code readableBytes} with some additional padding.
     */
    private static int padSizeForAccumulation(int readableBytes) {
        return (readableBytes << 2) / 3;
    }

    @Deprecated
    protected static void encodeAscii(String s, ByteBuf buf) {
        buf.writeCharSequence(s, CharsetUtil.US_ASCII);
    }

    /**
     * 编码第一行
     * @param buf
     * @param message
     * @throws Exception
     */
    protected abstract void encodeInitialLine(ByteBuf buf, H message) throws Exception;


    public static void main(String[] args) {
//        byte[] array = ZERO_CRLF_CRLF_BUF.array();
        System.out.print("a");
        System.out.print(new String(ZERO_CRLF_CRLF));
        System.out.print("---");
    }
}
