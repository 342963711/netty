/*
 * Copyright 2022 The Netty Project
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

import io.netty.handler.codec.TooLongFrameException;

/**
 * An {@link TooLongFrameException} which is thrown when the length of the
 * line decoded is greater than the allowed maximum.
 *
 * ｛@link TooLongFrameException｝，当解码的行长度大于允许的最大值时引发该异常。
 */
public final class TooLongHttpLineException extends TooLongFrameException {

    private static final long serialVersionUID = 1614751125592211890L;

    /**
     * Creates a new instance.
     */
    public TooLongHttpLineException() {
    }

    /**
     * Creates a new instance.
     */
    public TooLongHttpLineException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public TooLongHttpLineException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public TooLongHttpLineException(Throwable cause) {
        super(cause);
    }
}
