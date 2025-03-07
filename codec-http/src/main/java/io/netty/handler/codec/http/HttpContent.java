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
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelPipeline;

/**
 * An HTTP chunk which is used for HTTP chunked transfer-encoding.
 * {@link HttpObjectDecoder} generates {@link HttpContent} after
 * {@link HttpMessage} when the content is large or the encoding of the content
 * is 'chunked.  If you prefer not to receive {@link HttpContent} in your handler,
 * place {@link HttpObjectAggregator} after {@link HttpObjectDecoder} in the
 * {@link ChannelPipeline}.
 *
 * http 的分块对象，当http进行分块编码传输时。
 * {@link HttpObjectDecoder} 在{@link HttpMessage} 之后生成{@link HttpContent}.当内容太大或者内容编码被分块。
 * 如果您不希望在处理程序中接收{@link HttpContent}，将 {@link HttpObjectAggregator} 放在{@link ChannelPipeline} 中{@link HttpObjectDecoder} 之后。
 *
 * @see DefaultHttpContent 默认实现
 * @see LastHttpContent
 */
public interface HttpContent extends HttpObject, ByteBufHolder {
    @Override
    HttpContent copy();

    @Override
    HttpContent duplicate();

    @Override
    HttpContent retainedDuplicate();

    @Override
    HttpContent replace(ByteBuf content);

    @Override
    HttpContent retain();

    @Override
    HttpContent retain(int increment);

    @Override
    HttpContent touch();

    @Override
    HttpContent touch(Object hint);
}
