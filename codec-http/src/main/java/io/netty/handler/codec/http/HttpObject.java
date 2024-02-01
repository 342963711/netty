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

import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.DecoderResultProvider;

/**
 * http 协议的标记类
 *
 * @see DefaultHttpObject 默认的实现类
 *
 * @see HttpMessage http 请求和响应的统一标记类
 * @see HttpContent http 的内容
 *
 *
 *
 */
public interface HttpObject extends DecoderResultProvider {
    /**
     * @deprecated Use {@link #decoderResult()} instead.
     */
    @Deprecated
    DecoderResult getDecoderResult();
}
