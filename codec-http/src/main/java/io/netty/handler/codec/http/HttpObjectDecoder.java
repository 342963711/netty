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

import static io.netty.util.internal.ObjectUtil.checkPositive;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.StringUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decodes {@link ByteBuf}s into {@link HttpMessage}s and
 * {@link HttpContent}s.
 *
 * 将ByteBuf 编码为 HttpMessage 和 HttpContent
 *
 *
 * <h3>Parameters that prevents excessive memory consumption</h3>
 * 防止内存过度消耗的参数
 *
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Default value</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code maxInitialLineLength}</td>
 * <td>{@value #DEFAULT_MAX_INITIAL_LINE_LENGTH}</td>
 * <td>The maximum length of the initial line
 *     (e.g. {@code "GET / HTTP/1.0"} or {@code "HTTP/1.0 200 OK"})
 *     If the length of the initial line exceeds this value, a
 *     {@link TooLongHttpLineException} will be raised.</td>
 * </tr>
 * 默认值是4096
 * 初始行的最大长度（例如｛@code“GET/HTTP/1.0”｝或｛@ccode“HTTP/1.0 200 OK”｝）如果初始行的长度超过此值，将引发｛@link TooLongHttpLineException｝。
 *
 * <tr>
 * <td>{@code maxHeaderSize}</td>
 * <td>{@value #DEFAULT_MAX_HEADER_SIZE}</td>
 * <td>The maximum length of all headers.  If the sum of the length of each
 *     header exceeds this value, a {@link TooLongHttpHeaderException} will be raised.</td>
 * </tr>
 *
 * 所有标头的最大长度。如果每个头的长度总和超过此值，则将引发｛@link TooLongHttpHeaderException｝。
 *
 *
 * <tr>
 * <td>{@code maxChunkSize}</td>
 * <td>{@value #DEFAULT_MAX_CHUNK_SIZE}</td>
 * <td>The maximum length of the content or each chunk.  If the content length
 *     (or the length of each chunk) exceeds this value, the content or chunk
 *     will be split into multiple {@link HttpContent}s whose length is
 *     {@code maxChunkSize} at maximum.</td>
 * </tr>
 *
 * 内容或每个区块的最大长度。如果内容长度（或每个区块的长度）超过此值，则内容或区块将被拆分为多个｛@link HttpContent｝，
 * 其长度最大为｛@code maxChunkSize｝。
 * </table>
 *
 *
 *
 * <h3>Parameters that control parsing behavior</h3>
 *
 * 控制解析行为的参数
 *
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Default value</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code allowDuplicateContentLengths}</td>
 * <td>{@value #DEFAULT_ALLOW_DUPLICATE_CONTENT_LENGTHS}</td>
 * <td>When set to {@code false}, will reject any messages that contain multiple Content-Length header fields.
 *     When set to {@code true}, will allow multiple Content-Length headers only if they are all the same decimal value.
 *     The duplicated field-values will be replaced with a single valid Content-Length field.
 *     See <a href="https://tools.ietf.org/html/rfc7230#section-3.3.2">RFC 7230, Section 3.3.2</a>.</td>
 * </tr>
 *
 * 当设置为｛@code false｝时，将拒绝任何包含多个Content-Length标头字段的消息。
 * 当设置为｛@code true｝时，只有当多个Content-Length标头都是相同的十进制值时，才允许使用这些标头。重复的字段值将替换为单个有效的“内容长度”字段。参见RFC 7230，第3.3.2节。
 *
 *
 * <tr>
 * <td>{@code allowPartialChunks}</td>
 * <td>{@value #DEFAULT_ALLOW_PARTIAL_CHUNKS}</td>
 * <td>If the length of a chunk exceeds the {@link ByteBuf}s readable bytes and {@code allowPartialChunks}
 *     is set to {@code true}, the chunk will be split into multiple {@link HttpContent}s.
 *     Otherwise, if the chunk size does not exceed {@code maxChunkSize} and {@code allowPartialChunks}
 *     is set to {@code false}, the {@link ByteBuf} is not decoded into an {@link HttpContent} until
 *     the readable bytes are greater or equal to the chunk size.</td>
 * </tr>
 *
 *如果区块的长度超过｛@link ByteBuf｝的可读字节，并且｛@code allowPartialChunks｝设置为｛@code-true｝，则区块将被拆分为多个｛@linkHttpContent｝。
 * 否则，如果区块大小不超过{@code maxChunkSize}，并且{@code allowPartialChunks}设置为{@code false}，则在可读字节大于或等于区块大小之前，不会将{@link ByteBuf}解码为{@link HttpContent}。
 *
 * </table>
 *
 *
 *
 * <h3>Chunked Content</h3>
 * 块内容
 *
 *
 * If the content of an HTTP message is greater than {@code maxChunkSize} or
 * the transfer encoding of the HTTP message is 'chunked', this decoder
 * generates one {@link HttpMessage} instance and its following
 * {@link HttpContent}s per single HTTP message to avoid excessive memory
 * consumption. For example, the following HTTP message:
 *
 * 如果HTTP消息的内容大于｛@code maxChunkSize｝，或者HTTP消息的传输编码为“分块”，则此解码器会为每条HTTP消息生成一个｛@link HttpMessage｝实例及其以下｛@link HttpContent｝，以避免过多的内存消耗
 *
 * 例如，以下HTTP消息：
 *
 * <pre>
 * GET / HTTP/1.1
 * Transfer-Encoding: chunked
 *
 * 1a
 * abcdefghijklmnopqrstuvwxyz
 * 10
 * 1234567890abcdef
 * 0
 * Content-MD5: ...
 * <i>[blank line]</i>
 * </pre>
 * triggers {@link HttpRequestDecoder} to generate 3 objects:
 * 触发 HttpRequestDecoder 生成三个对象
 * <ol>
 * <li>An {@link HttpRequest},</li>
 * <li>The first {@link HttpContent} whose content is {@code 'abcdefghijklmnopqrstuvwxyz'},</li>
 * <li>The second {@link LastHttpContent} whose content is {@code '1234567890abcdef'}, which marks
 * the end of the content.</li>
 * </ol>
 *
 * If you prefer not to handle {@link HttpContent}s by yourself for your
 * convenience, insert {@link HttpObjectAggregator} after this decoder in the
 * {@link ChannelPipeline}.  However, please note that your server might not
 * be as memory efficient as without the aggregator.
 *
 * 如果为了方便起见，您不想自己处理｛@link HttpContent｝s，请在{@link ChannelPipeline}中的解码器后面插入
 * {@link HttpObjectAggregator}。
 * 但是，请注意，您的服务器可能不像没有聚合器那样具有内存效率。
 *
 * <h3>Extensibility</h3>
 * 可扩展的
 *
 * Please note that this decoder is designed to be extended to implement
 * a protocol derived from HTTP, such as
 * <a href="https://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="https://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 * To implement the decoder of such a derived protocol, extend this class and
 * implement all abstract methods properly.
 *
 * 请注意，此解码器是为实现源自HTTP协议 被设计为可扩展的
 *
 * 例如
 * <a href=“https://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol“>RTSP</a>
 * 和
 * <a href=“https://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol“>ICAP</a>。
 * 要实现这样一个派生协议的解码器，请扩展此类并正确地实现所有抽象方法。
 *
 * @see HttpRequestDecoder
 * @see HttpResponseDecoder
 * @see io.netty.handler.codec.rtsp.RtspDecoder
 * @see io.netty.handler.codec.rtsp.RtspObjectDecoder
 */
public abstract class HttpObjectDecoder extends ByteToMessageDecoder {
    /**
     * //控制解析的默认值
     */

    //协议大小规范默认限制，防止内存被过度使用
    /**
     * 协议第一行长度
     */
    public static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = 4096;
    /**
     * 所有请求的最大长度
     */
    public static final int DEFAULT_MAX_HEADER_SIZE = 8192;
    /**
     * 每个块（chunk） 的最大长度
     */
    public static final int DEFAULT_MAX_CHUNK_SIZE = 8192;


    /**
     * 是否支持分块
     */
    public static final boolean DEFAULT_CHUNKED_SUPPORTED = true;
    /**
     * content_length 是否允许重复
     */
    public static final boolean DEFAULT_ALLOW_DUPLICATE_CONTENT_LENGTHS = false;

    public static final boolean DEFAULT_ALLOW_PARTIAL_CHUNKS = true;

    public static final boolean DEFAULT_VALIDATE_HEADERS = true;
    public static final int DEFAULT_INITIAL_BUFFER_SIZE = 128;


    private final int maxChunkSize;
    private final boolean chunkedSupported;
    private final boolean allowPartialChunks;
    protected final boolean validateHeaders;
    private final boolean allowDuplicateContentLengths;

    //解析暂存缓存区(默认是128)
    private final ByteBuf parserScratchBuffer;
    //两个解析类，一个解析头节点，一个解析初始化行
    private final HeaderParser headerParser;
    private final LineParser lineParser;

    //解析出来的消息
    private HttpMessage message;
    private long chunkSize;
    private long contentLength = Long.MIN_VALUE;
    private final AtomicBoolean resetRequested = new AtomicBoolean();

    // These will be updated by splitHeader(...)
    private AsciiString name;
    private String value;
    private LastHttpContent trailer;

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        try {
            parserScratchBuffer.release();
        } finally {
            super.handlerRemoved0(ctx);
        }
    }

    /**
     * The internal state of {@link HttpObjectDecoder}.
     * <em>Internal use only</em>.
     * 内部状态
     */
    private enum State {
        //跳过控制字符
        SKIP_CONTROL_CHARS,
        //读取初始行
        READ_INITIAL,
        //读取头节点
        READ_HEADER,
        //读取变量长度内容
        READ_VARIABLE_LENGTH_CONTENT,
        //读取固定长度内容
        READ_FIXED_LENGTH_CONTENT,
        //读取块大小
        READ_CHUNK_SIZE,
        //读取块内容
        READ_CHUNKED_CONTENT,
        //读取块分隔符
        READ_CHUNK_DELIMITER,
        //读取块 尾
        READ_CHUNK_FOOTER,
        //坏消息
        BAD_MESSAGE,
        //升级
        UPGRADED
    }

    private State currentState = State.SKIP_CONTROL_CHARS;

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}.
     */
    protected HttpObjectDecoder() {
        this(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_CHUNK_SIZE,
             DEFAULT_CHUNKED_SUPPORTED);
    }

    /**
     * Creates a new instance with the specified parameters.
     * @param maxInitialLineLength 4096
     * @param maxHeaderSize 8192
     * @param maxChunkSize 8192
     * @param chunkedSupported true
     */
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean chunkedSupported) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, chunkedSupported, DEFAULT_VALIDATE_HEADERS);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, chunkedSupported, validateHeaders,
             DEFAULT_INITIAL_BUFFER_SIZE);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders, int initialBufferSize) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, chunkedSupported, validateHeaders, initialBufferSize,
             DEFAULT_ALLOW_DUPLICATE_CONTENT_LENGTHS);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders, int initialBufferSize,
            boolean allowDuplicateContentLengths) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, chunkedSupported, validateHeaders, initialBufferSize,
            allowDuplicateContentLengths, DEFAULT_ALLOW_PARTIAL_CHUNKS);
    }

    /**
     * Creates a new instance with the specified parameters.
     * @param maxInitialLineLength  4096
     * @param maxHeaderSize 8192
     * @param maxChunkSize 8192
     * @param chunkedSupported true
     * @param validateHeaders true
     * @param initialBufferSize 128
     * @param allowDuplicateContentLengths false
     * @param allowPartialChunks true
     */
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders, int initialBufferSize,
            boolean allowDuplicateContentLengths, boolean allowPartialChunks) {
        checkPositive(maxInitialLineLength, "maxInitialLineLength");
        checkPositive(maxHeaderSize, "maxHeaderSize");
        checkPositive(maxChunkSize, "maxChunkSize");

        parserScratchBuffer = Unpooled.buffer(initialBufferSize);
        lineParser = new LineParser(parserScratchBuffer, maxInitialLineLength);
        headerParser = new HeaderParser(parserScratchBuffer, maxHeaderSize);
        this.maxChunkSize = maxChunkSize;
        this.chunkedSupported = chunkedSupported;
        this.validateHeaders = validateHeaders;
        this.allowDuplicateContentLengths = allowDuplicateContentLengths;
        this.allowPartialChunks = allowPartialChunks;
    }

    /**
     * http 协议 进行通用协议内容解析，如果重写，查看是否公用此代码（super.decode(...)）.
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param buffer            the {@link ByteBuf} from which to read data
     * @param out           the {@link List} to which decoded messages should be added
     * @throws Exception
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        //重置请求，默认是false
        if (resetRequested.get()) {
            resetNow();
        }
        //判断当前状态
        switch (currentState) {
        case SKIP_CONTROL_CHARS:
            // Fall-through
        case READ_INITIAL: try {
            ByteBuf line = lineParser.parse(buffer);
            if (line == null) {
                return;
            }
            final String[] initialLine = splitInitialLine(line);
            assert initialLine.length == 3 : "initialLine::length must be 3";

            message = createMessage(initialLine);
            //开始读取Http头
            currentState = State.READ_HEADER;
            // fall-through
        } catch (Exception e) {
            out.add(invalidMessage(buffer, e));
            return;
        }
        case READ_HEADER: try {
            //读取头信息，并且获取下一个处理状态
            State nextState = readHeaders(buffer);
            if (nextState == null) {
                return;
            }
            currentState = nextState;
            switch (nextState) {
            case SKIP_CONTROL_CHARS:
                // fast-path
                // No content is expected.
                out.add(message);
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                resetNow();
                return;
            case READ_CHUNK_SIZE:
                if (!chunkedSupported) {
                    throw new IllegalArgumentException("Chunked messages not supported");
                }
                // Chunked encoding - generate HttpMessage first.  HttpChunks will follow.
                out.add(message);
                return;
            default:
                /**
                 * <a href="https://tools.ietf.org/html/rfc7230#section-3.3.3">RFC 7230, 3.3.3</a> states that if a
                 * request does not have either a transfer-encoding or a content-length header then the message body
                 * length is 0. However for a response the body length is the number of octets received prior to the
                 * server closing the connection. So we treat this as variable length chunked encoding.
                 */
                long contentLength = contentLength();
                if (contentLength == 0 || contentLength == -1 && isDecodingRequest()) {
                    out.add(message);
                    out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                    resetNow();
                    return;
                }

                assert nextState == State.READ_FIXED_LENGTH_CONTENT ||
                        nextState == State.READ_VARIABLE_LENGTH_CONTENT;

                out.add(message);

                if (nextState == State.READ_FIXED_LENGTH_CONTENT) {
                    // chunkSize will be decreased as the READ_FIXED_LENGTH_CONTENT state reads data chunk by chunk.
                    chunkSize = contentLength;
                }

                // We return here, this forces decode to be called again where we will decode the content
                return;
            }
        } catch (Exception e) {
            out.add(invalidMessage(buffer, e));
            return;
        }
        //读取可变内容长度
        case READ_VARIABLE_LENGTH_CONTENT: {
            // Keep reading data as a chunk until the end of connection is reached.
            int toRead = Math.min(buffer.readableBytes(), maxChunkSize);
            if (toRead > 0) {
                ByteBuf content = buffer.readRetainedSlice(toRead);
                out.add(new DefaultHttpContent(content));
            }
            return;
        }
        //读取固定内容长度
        case READ_FIXED_LENGTH_CONTENT: {
            int readLimit = buffer.readableBytes();

            // Check if the buffer is readable first as we use the readable byte count
            // to create the HttpChunk. This is needed as otherwise we may end up with
            // create an HttpChunk instance that contains an empty buffer and so is
            // handled like it is the last HttpChunk.
            //
            // See https://github.com/netty/netty/issues/433
            if (readLimit == 0) {
                return;
            }

            int toRead = Math.min(readLimit, maxChunkSize);
            if (toRead > chunkSize) {
                toRead = (int) chunkSize;
            }
            ByteBuf content = buffer.readRetainedSlice(toRead);
            chunkSize -= toRead;

            if (chunkSize == 0) {
                // Read all content.
                out.add(new DefaultLastHttpContent(content, validateHeaders));
                resetNow();
            } else {
                out.add(new DefaultHttpContent(content));
            }
            return;
        }
        /**
         * everything else after this point takes care of reading chunked content. basically, read chunk size,
         * read chunk, read and ignore the CRLF and repeat until 0
         * 在这一点之后的所有其他内容都要注意阅读大块的内容。基本上，读取区块大小读取区块，读取并忽略CRLF并重复直到0
         */
        case READ_CHUNK_SIZE: try {
            ByteBuf line = lineParser.parse(buffer);
            if (line == null) {
                return;
            }
            int chunkSize = getChunkSize(line.array(), line.arrayOffset() + line.readerIndex(), line.readableBytes());
            this.chunkSize = chunkSize;
            if (chunkSize == 0) {
                currentState = State.READ_CHUNK_FOOTER;
                return;
            }
            currentState = State.READ_CHUNKED_CONTENT;
            // fall-through
        } catch (Exception e) {
            out.add(invalidChunk(buffer, e));
            return;
        }
        case READ_CHUNKED_CONTENT: {
            assert chunkSize <= Integer.MAX_VALUE;
            int toRead = Math.min((int) chunkSize, maxChunkSize);
            if (!allowPartialChunks && buffer.readableBytes() < toRead) {
                return;
            }
            toRead = Math.min(toRead, buffer.readableBytes());
            if (toRead == 0) {
                return;
            }
            HttpContent chunk = new DefaultHttpContent(buffer.readRetainedSlice(toRead));
            chunkSize -= toRead;

            out.add(chunk);

            if (chunkSize != 0) {
                return;
            }
            currentState = State.READ_CHUNK_DELIMITER;
            // fall-through
            //执行下一个节点
        }
        case READ_CHUNK_DELIMITER: {
            final int wIdx = buffer.writerIndex();
            int rIdx = buffer.readerIndex();
            while (wIdx > rIdx) {
                byte next = buffer.getByte(rIdx++);
                if (next == HttpConstants.LF) {
                    currentState = State.READ_CHUNK_SIZE;
                    break;
                }
            }
            buffer.readerIndex(rIdx);
            return;
        }
        case READ_CHUNK_FOOTER: try {
            LastHttpContent trailer = readTrailingHeaders(buffer);
            if (trailer == null) {
                return;
            }
            out.add(trailer);
            resetNow();
            return;
        } catch (Exception e) {
            out.add(invalidChunk(buffer, e));
            return;
        }
        case BAD_MESSAGE: {
            // Keep discarding until disconnection.
            buffer.skipBytes(buffer.readableBytes());
            break;
        }
        case UPGRADED: {
            int readableBytes = buffer.readableBytes();
            if (readableBytes > 0) {
                // Keep on consuming as otherwise we may trigger an DecoderException,
                // other handler will replace this codec with the upgraded protocol codec to
                // take the traffic over at some point then.
                // See https://github.com/netty/netty/issues/2173
                out.add(buffer.readBytes(readableBytes));
            }
            break;
        }
        default:
            break;
        }
    }

    @Override
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        super.decodeLast(ctx, in, out);

        if (resetRequested.get()) {
            // If a reset was requested by decodeLast() we need to do it now otherwise we may produce a
            // LastHttpContent while there was already one.
            resetNow();
        }
        // Handle the last unfinished message.
        if (message != null) {
            boolean chunked = HttpUtil.isTransferEncodingChunked(message);
            if (currentState == State.READ_VARIABLE_LENGTH_CONTENT && !in.isReadable() && !chunked) {
                // End of connection.
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                resetNow();
                return;
            }

            if (currentState == State.READ_HEADER) {
                // If we are still in the state of reading headers we need to create a new invalid message that
                // signals that the connection was closed before we received the headers.
                out.add(invalidMessage(Unpooled.EMPTY_BUFFER,
                        new PrematureChannelClosureException("Connection closed before received headers")));
                resetNow();
                return;
            }

            // Check if the closure of the connection signifies the end of the content.
            boolean prematureClosure;
            if (isDecodingRequest() || chunked) {
                // The last request did not wait for a response.
                prematureClosure = true;
            } else {
                // Compare the length of the received content and the 'Content-Length' header.
                // If the 'Content-Length' header is absent, the length of the content is determined by the end of the
                // connection, so it is perfectly fine.
                prematureClosure = contentLength() > 0;
            }

            if (!prematureClosure) {
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
            }
            resetNow();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpExpectationFailedEvent) {
            switch (currentState) {
            case READ_FIXED_LENGTH_CONTENT:
            case READ_VARIABLE_LENGTH_CONTENT:
            case READ_CHUNK_SIZE:
                reset();
                break;
            default:
                break;
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    protected boolean isContentAlwaysEmpty(HttpMessage msg) {
        if (msg instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) msg;
            final HttpResponseStatus status = res.status();
            final int code = status.code();
            final HttpStatusClass statusClass = status.codeClass();

            // Correctly handle return codes of 1xx.
            //
            // See:
            //     - https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html Section 4.4
            //     - https://github.com/netty/netty/issues/222
            if (statusClass == HttpStatusClass.INFORMATIONAL) {
                // One exception: Hixie 76 websocket handshake response
                return !(code == 101 && !res.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT)
                         && res.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true));
            }

            switch (code) {
            case 204: case 304:
                return true;
            default:
                return false;
            }
        }
        return false;
    }

    /**
     * Returns true if the server switched to a different protocol than HTTP/1.0 or HTTP/1.1, e.g. HTTP/2 or Websocket.
     * Returns false if the upgrade happened in a different layer, e.g. upgrade from HTTP/1.1 to HTTP/1.1 over TLS.
     */
    protected boolean isSwitchingToNonHttp1Protocol(HttpResponse msg) {
        if (msg.status().code() != HttpResponseStatus.SWITCHING_PROTOCOLS.code()) {
            return false;
        }
        String newProtocol = msg.headers().get(HttpHeaderNames.UPGRADE);
        return newProtocol == null ||
                !newProtocol.contains(HttpVersion.HTTP_1_0.text()) &&
                !newProtocol.contains(HttpVersion.HTTP_1_1.text());
    }

    /**
     * Resets the state of the decoder so that it is ready to decode a new message.
     * This method is useful for handling a rejected request with {@code Expect: 100-continue} header.
     */
    public void reset() {
        resetRequested.lazySet(true);
    }

    private void resetNow() {
        HttpMessage message = this.message;
        this.message = null;
        name = null;
        value = null;
        contentLength = Long.MIN_VALUE;
        lineParser.reset();
        headerParser.reset();
        trailer = null;
        if (!isDecodingRequest()) {
            HttpResponse res = (HttpResponse) message;
            if (res != null && isSwitchingToNonHttp1Protocol(res)) {
                currentState = State.UPGRADED;
                return;
            }
        }

        resetRequested.lazySet(false);
        currentState = State.SKIP_CONTROL_CHARS;
    }

    private HttpMessage invalidMessage(ByteBuf in, Exception cause) {
        currentState = State.BAD_MESSAGE;

        // Advance the readerIndex so that ByteToMessageDecoder does not complain
        // when we produced an invalid message without consuming anything.
        in.skipBytes(in.readableBytes());

        if (message == null) {
            message = createInvalidMessage();
        }
        message.setDecoderResult(DecoderResult.failure(cause));

        HttpMessage ret = message;
        message = null;
        return ret;
    }

    private HttpContent invalidChunk(ByteBuf in, Exception cause) {
        currentState = State.BAD_MESSAGE;

        // Advance the readerIndex so that ByteToMessageDecoder does not complain
        // when we produced an invalid message without consuming anything.
        in.skipBytes(in.readableBytes());

        HttpContent chunk = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
        chunk.setDecoderResult(DecoderResult.failure(cause));
        message = null;
        trailer = null;
        return chunk;
    }

    /**
     * 处理Http 头信息
     * @param buffer
     * @return  返回下一个状态
     */
    private State readHeaders(ByteBuf buffer) {
        final HttpMessage message = this.message;
        final HttpHeaders headers = message.headers();

        final HeaderParser headerParser = this.headerParser;
        //使用头解析对象解析，之前解析的字节不会重置
        ByteBuf line = headerParser.parse(buffer);
        if (line == null) {
            return null;
        }
        //获取本次行长度
        int lineLength = line.readableBytes();
        while (lineLength > 0) {
            final byte[] lineContent = line.array();
            final int startLine = line.arrayOffset() + line.readerIndex();
            final byte firstChar = lineContent[startLine];
            if (name != null && (firstChar == ' ' || firstChar == '\t')) {
                //please do not make one line from below code
                //as it breaks +XX:OptimizeStringConcat optimization
                String trimmedLine = langAsciiString(lineContent, startLine, lineLength).trim();
                String valueStr = value;
                value = valueStr + ' ' + trimmedLine;
            } else {
                if (name != null) {
                    headers.add(name, value);
                }
                //解析头 的key 和value
                splitHeader(lineContent, startLine, lineLength);
            }
            //再次从读取的里面解析一行
            line = headerParser.parse(buffer);
            //直到没有头内容
            if (line == null) {
                return null;
            }
            lineLength = line.readableBytes();
        }

        // Add the last header.
        if (name != null) {
            headers.add(name, value);
        }

        // reset name and value fields
        name = null;
        value = null;

        // Done parsing initial line and headers. Set decoder result.
        // 已完成对初始行和标头的分析。设置解码器结果。
        HttpMessageDecoderResult decoderResult = new HttpMessageDecoderResult(lineParser.size, headerParser.size);
        message.setDecoderResult(decoderResult);
        //获取内容长度请求头
        List<String> contentLengthFields = headers.getAll(HttpHeaderNames.CONTENT_LENGTH);
        if (!contentLengthFields.isEmpty()) {
            HttpVersion version = message.protocolVersion();
            boolean isHttp10OrEarlier = version.majorVersion() < 1 || (version.majorVersion() == 1
                    && version.minorVersion() == 0);
            // Guard against multiple Content-Length headers as stated in
            // https://tools.ietf.org/html/rfc7230#section-3.3.2:
            contentLength = HttpUtil.normalizeAndGetContentLength(contentLengthFields,
                    isHttp10OrEarlier, allowDuplicateContentLengths);
            if (contentLength != -1) {
                String lengthValue = contentLengthFields.get(0).trim();
                if (contentLengthFields.size() > 1 || // don't unnecessarily re-order headers
                        !lengthValue.equals(Long.toString(contentLength))) {
                    headers.set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
                }
            }
        }
        //判断Http 版本来进行内容长度的读取，现在主流HTTP 版本 都是1.1
        if (isContentAlwaysEmpty(message)) {
            HttpUtil.setTransferEncodingChunked(message, false);
            return State.SKIP_CONTROL_CHARS;
        } else if (HttpUtil.isTransferEncodingChunked(message)) {
            if (!contentLengthFields.isEmpty() && message.protocolVersion() == HttpVersion.HTTP_1_1) {
                handleTransferEncodingChunkedWithContentLength(message);
            }
            return State.READ_CHUNK_SIZE;
        } else if (contentLength() >= 0) {
            return State.READ_FIXED_LENGTH_CONTENT;
        } else {
            return State.READ_VARIABLE_LENGTH_CONTENT;
        }
    }

    /**
     * Invoked when a message with both a "Transfer-Encoding: chunked" and a "Content-Length" header field is detected.
     * The default behavior is to <i>remove</i> the Content-Length field, but this method could be overridden
     * to change the behavior (to, e.g., throw an exception and produce an invalid message).
     * <p>
     * See: https://tools.ietf.org/html/rfc7230#section-3.3.3
     * <pre>
     *     If a message is received with both a Transfer-Encoding and a
     *     Content-Length header field, the Transfer-Encoding overrides the
     *     Content-Length.  Such a message might indicate an attempt to
     *     perform request smuggling (Section 9.5) or response splitting
     *     (Section 9.4) and ought to be handled as an error.  A sender MUST
     *     remove the received Content-Length field prior to forwarding such
     *     a message downstream.
     * </pre>
     * Also see:
     * https://github.com/apache/tomcat/blob/b693d7c1981fa7f51e58bc8c8e72e3fe80b7b773/
     * java/org/apache/coyote/http11/Http11Processor.java#L747-L755
     * https://github.com/nginx/nginx/blob/0ad4393e30c119d250415cb769e3d8bc8dce5186/
     * src/http/ngx_http_request.c#L1946-L1953
     */
    protected void handleTransferEncodingChunkedWithContentLength(HttpMessage message) {
        message.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
        contentLength = Long.MIN_VALUE;
    }

    private long contentLength() {
        if (contentLength == Long.MIN_VALUE) {
            contentLength = HttpUtil.getContentLength(message, -1L);
        }
        return contentLength;
    }

    private LastHttpContent readTrailingHeaders(ByteBuf buffer) {
        final HeaderParser headerParser = this.headerParser;
        ByteBuf line = headerParser.parse(buffer);
        if (line == null) {
            return null;
        }
        LastHttpContent trailer = this.trailer;
        int lineLength = line.readableBytes();
        if (lineLength == 0 && trailer == null) {
            // We have received the empty line which signals the trailer is complete and did not parse any trailers
            // before. Just return an empty last content to reduce allocations.
            return LastHttpContent.EMPTY_LAST_CONTENT;
        }

        CharSequence lastHeader = null;
        if (trailer == null) {
            trailer = this.trailer = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, validateHeaders);
        }
        while (lineLength > 0) {
            final byte[] lineContent = line.array();
            final int startLine = line.arrayOffset() + line.readerIndex();
            final byte firstChar = lineContent[startLine];
            if (lastHeader != null && (firstChar == ' ' || firstChar == '\t')) {
                List<String> current = trailer.trailingHeaders().getAll(lastHeader);
                if (!current.isEmpty()) {
                    int lastPos = current.size() - 1;
                    //please do not make one line from below code
                    //as it breaks +XX:OptimizeStringConcat optimization
                    String lineTrimmed = langAsciiString(lineContent, startLine, line.readableBytes()).trim();
                    String currentLastPos = current.get(lastPos);
                    current.set(lastPos, currentLastPos + lineTrimmed);
                }
            } else {
                splitHeader(lineContent, startLine, lineLength);
                AsciiString headerName = name;
                if (!HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(headerName) &&
                        !HttpHeaderNames.TRANSFER_ENCODING.contentEqualsIgnoreCase(headerName) &&
                        !HttpHeaderNames.TRAILER.contentEqualsIgnoreCase(headerName)) {
                    trailer.trailingHeaders().add(headerName, value);
                }
                lastHeader = name;
                // reset name and value fields
                name = null;
                value = null;
            }
            line = headerParser.parse(buffer);
            if (line == null) {
                return null;
            }
            lineLength = line.readableBytes();
        }

        this.trailer = null;
        return trailer;
    }

    protected abstract boolean isDecodingRequest();
    protected abstract HttpMessage createMessage(String[] initialLine) throws Exception;
    protected abstract HttpMessage createInvalidMessage();

    private static int getChunkSize(byte[] hex, int start, int length) {
        // byte[] is produced by LineParse::parseLine that already skip ISO CTRL and Whitespace chars
        int result = 0;
        for (int i = 0; i < length; i++) {
            final int digit = StringUtil.decodeHexNibble(hex[start + i]);
            if (digit == -1) {
                // uncommon path
                if (hex[start + i] == ';') {
                    return result;
                }
                throw new NumberFormatException();
            }
            result *= 16;
            result += digit;
        }
        return result;
    }

    private String[] splitInitialLine(ByteBuf asciiBuffer) {
        final byte[] asciiBytes = asciiBuffer.array();

        final int arrayOffset = asciiBuffer.arrayOffset();

        final int startContent = arrayOffset + asciiBuffer.readerIndex();

        final int end = startContent + asciiBuffer.readableBytes();

        final int aStart = findNonSPLenient(asciiBytes, startContent, end);
        final int aEnd = findSPLenient(asciiBytes, aStart, end);

        final int bStart = findNonSPLenient(asciiBytes, aEnd, end);
        final int bEnd = findSPLenient(asciiBytes, bStart, end);

        final int cStart = findNonSPLenient(asciiBytes, bEnd, end);
        final int cEnd = findEndOfString(asciiBytes, Math.max(cStart - 1, startContent), end);

        return new String[]{
                splitFirstWordInitialLine(asciiBytes, aStart, aEnd - aStart),
                splitSecondWordInitialLine(asciiBytes, bStart, bEnd - bStart),
                cStart < cEnd ? splitThirdWordInitialLine(asciiBytes, cStart, cEnd - cStart) : StringUtil.EMPTY_STRING};
    }

    protected String splitFirstWordInitialLine(final byte[] asciiContent, int start, int length) {
        return langAsciiString(asciiContent, start, length);
    }

    protected String splitSecondWordInitialLine(final byte[] asciiContent, int start, int length) {
        return langAsciiString(asciiContent, start, length);
    }

    protected String splitThirdWordInitialLine(final byte[] asciiContent, int start, int length) {
        return langAsciiString(asciiContent, start, length);
    }

    /**
     * This method shouldn't exist: look at https://bugs.openjdk.org/browse/JDK-8295496 for more context
     */
    private static String langAsciiString(final byte[] asciiContent, int start, int length) {
        if (length == 0) {
            return StringUtil.EMPTY_STRING;
        }
        // DON'T REMOVE: it helps JIT to use a simpler intrinsic stub for System::arrayCopy based on the call-site
        if (start == 0) {
            if (length == asciiContent.length) {
                return new String(asciiContent, 0, 0, asciiContent.length);
            }
            return new String(asciiContent, 0, 0, length);
        }
        return new String(asciiContent, 0, start, length);
    }

    private void splitHeader(byte[] line, int start, int length) {
        final int end = start + length;
        int nameEnd;
        final int nameStart = findNonWhitespace(line, start, end);
        // hoist this load out of the loop, because it won't change!
        final boolean isDecodingRequest = isDecodingRequest();
        for (nameEnd = nameStart; nameEnd < end; nameEnd ++) {
            byte ch = line[nameEnd];
            // https://tools.ietf.org/html/rfc7230#section-3.2.4
            //
            // No whitespace is allowed between the header field-name and colon. In
            // the past, differences in the handling of such whitespace have led to
            // security vulnerabilities in request routing and response handling. A
            // server MUST reject any received request message that contains
            // whitespace between a header field-name and colon with a response code
            // of 400 (Bad Request). A proxy MUST remove any such whitespace from a
            // response message before forwarding the message downstream.
            if (ch == ':' ||
                    // In case of decoding a request we will just continue processing and header validation
                    // is done in the DefaultHttpHeaders implementation.
                    //
                    // In the case of decoding a response we will "skip" the whitespace.
                    (!isDecodingRequest && isOWS(ch))) {
                break;
            }
        }

        if (nameEnd == end) {
            // There was no colon present at all.
            throw new IllegalArgumentException("No colon found");
        }
        int colonEnd;
        for (colonEnd = nameEnd; colonEnd < end; colonEnd ++) {
            if (line[colonEnd] == ':') {
                colonEnd ++;
                break;
            }
        }
        name = splitHeaderName(line, nameStart, nameEnd - nameStart);
        final int valueStart = findNonWhitespace(line, colonEnd, end);
        if (valueStart == end) {
            value = StringUtil.EMPTY_STRING;
        } else {
            final int valueEnd = findEndOfString(line, start, end);
            // no need to make uses of the ByteBuf's toString ASCII method here, and risk to get JIT confused
            value = langAsciiString(line, valueStart, valueEnd - valueStart);
        }
    }

    protected AsciiString splitHeaderName(byte[] sb, int start, int length) {
        return new AsciiString(sb, start, length, true);
    }

    private static int findNonSPLenient(byte[] sb, int offset, int end) {
        for (int result = offset; result < end; ++result) {
            byte c = sb[result];
            // See https://tools.ietf.org/html/rfc7230#section-3.5
            if (isSPLenient(c)) {
                continue;
            }
            if (isWhitespace(c)) {
                // Any other whitespace delimiter is invalid
                throw new IllegalArgumentException("Invalid separator");
            }
            return result;
        }
        return end;
    }

    private static int findSPLenient(byte[] sb, int offset, int end) {
        for (int result = offset; result < end; ++result) {
            if (isSPLenient(sb[result])) {
                return result;
            }
        }
        return end;
    }

    private static final boolean[] SP_LENIENT_BYTES;
    private static final boolean[] LATIN_WHITESPACE;

    static {
        // See https://tools.ietf.org/html/rfc7230#section-3.5,
        //这里为了健壮性，包含了所有可能的空格
        SP_LENIENT_BYTES = new boolean[256];
        SP_LENIENT_BYTES[128 + ' '] = true;
        SP_LENIENT_BYTES[128 + 0x09] = true;
        SP_LENIENT_BYTES[128 + 0x0B] = true;
        SP_LENIENT_BYTES[128 + 0x0C] = true;
        SP_LENIENT_BYTES[128 + 0x0D] = true;
        // TO SAVE PERFORMING Character::isWhitespace ceremony，并检测java语言中的空白字符
        LATIN_WHITESPACE = new boolean[256];
        for (byte b = Byte.MIN_VALUE; b < Byte.MAX_VALUE; b++) {
            LATIN_WHITESPACE[128 + b] = Character.isWhitespace(b);
        }
    }

    private static boolean isSPLenient(byte c) {
        // See https://tools.ietf.org/html/rfc7230#section-3.5
        return SP_LENIENT_BYTES[c + 128];
    }

    private static boolean isWhitespace(byte b) {
        return LATIN_WHITESPACE[b + 128];
    }

    private static int findNonWhitespace(byte[] sb, int offset, int end) {
        for (int result = offset; result < end; ++result) {
            byte c = sb[result];
            if (!isWhitespace(c)) {
                return result;
            } else if (!isOWS(c)) {
                // Only OWS is supported for whitespace
                throw new IllegalArgumentException("Invalid separator, only a single space or horizontal tab allowed," +
                        " but received a '" + c + "' (0x" + Integer.toHexString(c) + ")");
            }
        }
        return end;
    }

    private static int findEndOfString(byte[] sb, int start, int end) {
        for (int result = end - 1; result > start; --result) {
            if (!isWhitespace(sb[result])) {
                return result + 1;
            }
        }
        return 0;
    }

    private static boolean isOWS(byte ch) {
        return ch == ' ' || ch == 0x09;
    }

    private static class HeaderParser {
        //解析内容暂存缓冲区
        protected final ByteBuf seq;
        //maxHeaderLength 8192
        protected final int maxLength;
        //
        int size;

        HeaderParser(ByteBuf seq, int maxLength) {
            this.seq = seq;
            this.maxLength = maxLength;
        }

        /**
         * 按照行，每次解析出一行，
         * @param buffer
         * @return
         */
        public ByteBuf parse(ByteBuf buffer) {
            final int readableBytes = buffer.readableBytes();
            final int readerIndex = buffer.readerIndex();
            final int maxBodySize = maxLength - size;
            assert maxBodySize >= 0;
            // adding 2 to account for both CR (if present) and LF
            // don't remove 2L: it's key to cover maxLength = Integer.MAX_VALUE
            final long maxBodySizeWithCRLF = maxBodySize + 2L;
            final int toProcess = (int) Math.min(maxBodySizeWithCRLF, readableBytes);
            //找到到处理的全部内容（从可读索引）
            final int toIndexExclusive = readerIndex + toProcess;
            assert toIndexExclusive >= readerIndex;
            //找到第一个换行符的索引（对于HTTP 来说，头是多行组成的）
            final int indexOfLf = buffer.indexOf(readerIndex, toIndexExclusive, HttpConstants.LF);
            if (indexOfLf == -1) {
                //如果帧长超过限制，抛出异常
                if (readableBytes > maxBodySize) {
                    // TODO: Respond with Bad Request and discard the traffic
                    //    or close the connection.
                    //       No need to notify the upstream handlers - just log.
                    //       If decoding a response, just throw an exception.
                    throw newException(maxLength);
                }
                //如果没有找到换行符，说明需要等待继续接受。
                return null;
            }
            //找到本次处理的单行长度
            final int endOfSeqIncluded;
            if (indexOfLf > readerIndex && buffer.getByte(indexOfLf - 1) == HttpConstants.CR) {
                // Drop CR if we had a CRLF pair
                endOfSeqIncluded = indexOfLf - 1;
            } else {
                endOfSeqIncluded = indexOfLf;
            }
            final int newSize = endOfSeqIncluded - readerIndex;
            //如果行内容为空
            if (newSize == 0) {
                seq.clear();
                buffer.readerIndex(indexOfLf + 1);
                return seq;
            }
            //记录已经解析字节数
            int size = this.size + newSize;
            if (size > maxLength) {
                throw newException(maxLength);
            }
            this.size = size;
            seq.clear();
            //将本次解析内容写入到缓冲区。并返回
            seq.writeBytes(buffer, readerIndex, newSize);
            buffer.readerIndex(indexOfLf + 1);
            return seq;
        }

        public void reset() {
            size = 0;
        }

        /**
         * 帧太长异常
         * @param maxLength
         * @return
         */
        protected TooLongFrameException newException(int maxLength) {
            return new TooLongHttpHeaderException("HTTP header is larger than " + maxLength + " bytes.");
        }
    }

    /**
     * 按照行来进行解析
     */
    private final class LineParser extends HeaderParser {

        LineParser(ByteBuf seq, int maxLength) {
            super(seq, maxLength);
        }

        @Override
        public ByteBuf parse(ByteBuf buffer) {
            // Suppress a warning because HeaderParser.reset() is supposed to be called
            // 重置临时存储处理的数量
            reset();
            final int readableBytes = buffer.readableBytes();
            if (readableBytes == 0) {
                return null;
            }
            final int readerIndex = buffer.readerIndex();
            if (currentState == State.SKIP_CONTROL_CHARS && skipControlChars(buffer, readableBytes, readerIndex)) {
                return null;
            }
            return super.parse(buffer);
        }

        /**
         * 跳过缓冲区 控制字符
         * @param buffer
         * @param readableBytes
         * @param readerIndex
         * @return
         */
        private boolean skipControlChars(ByteBuf buffer, int readableBytes, int readerIndex) {
            assert currentState == State.SKIP_CONTROL_CHARS;
            //
            final int maxToSkip = Math.min(maxLength, readableBytes);
            //找到第一个非控制符的索引，
            final int firstNonControlIndex = buffer.forEachByte(readerIndex, maxToSkip, SKIP_CONTROL_CHARS_BYTES);
            if (firstNonControlIndex == -1) {
                buffer.skipBytes(maxToSkip);
                if (readableBytes > maxLength) {
                    throw newException(maxLength);
                }
                return true;
            }
            // from now on we don't care about control chars
            buffer.readerIndex(firstNonControlIndex);
            //设置开始读取初始化行
            currentState = State.READ_INITIAL;
            return false;
        }

        @Override
        protected TooLongFrameException newException(int maxLength) {
            return new TooLongHttpLineException("An HTTP line is larger than " + maxLength + " bytes.");
        }
    }

    /**
     * iso 标准字符集 采用完整的 1字节，可以表示256个字符。
     */
    private static final boolean[] ISO_CONTROL_OR_WHITESPACE;

    static {
        ISO_CONTROL_OR_WHITESPACE = new boolean[256];
        for (byte b = Byte.MIN_VALUE; b < Byte.MAX_VALUE; b++) {
            // Character.isISOControl(b) 判断的 是  0-31 或者 大于 127 小于 159
            ISO_CONTROL_OR_WHITESPACE[128 + b] = Character.isISOControl(b) || isWhitespace(b);
        }
    }

    /**
     * 判断是否应该跳过控制符
     */
    private static final ByteProcessor SKIP_CONTROL_CHARS_BYTES = new ByteProcessor() {

        @Override
        public boolean process(byte value) {
            return ISO_CONTROL_OR_WHITESPACE[128 + value];
        }
    };

    public static void main(String[] args) {
        byte[] b = new byte[]{13};
        System.out.println(new String(b));
    }
}
