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

import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;

public final class HttpConstants {

    /**
     * Horizontal space
     * 空格
     */
    public static final byte SP = 32;

    /**
     * Horizontal tab
     * 制表符
     */
    public static final byte HT = 9;

    /**
     * Carriage return
     * 回车
     */
    public static final byte CR = 13;

    /**
     * Equals '='
     * 等于号
     */
    public static final byte EQUALS = 61;

    /**
     * Line feed character
     * 换行符
     */
    public static final byte LF = 10;

    /**
     * Colon ':'
     * 冒号
     */
    public static final byte COLON = 58;

    /**
     * Semicolon ';'
     * 分号
     */
    public static final byte SEMICOLON = 59;

    /**
     * Comma ','
     * 逗号
     */
    public static final byte COMMA = 44;

    /**
     * Double quote '"'
     * 双引号
     */
    public static final byte DOUBLE_QUOTE = '"';

    /**
     * Default character set (UTF-8)
     * 默认字符集
     */
    public static final Charset DEFAULT_CHARSET = CharsetUtil.UTF_8;

    /**
     * Horizontal space
     *
     */
    public static final char SP_CHAR = (char) SP;

    private HttpConstants() {
        // Unused
    }

    public static void main(String[] args) {
        byte[] bytes = "0".getBytes(Charset.forName("utf-8"));
        for(int i=0;i<bytes.length;i++){
            System.out.println(bytes[i]);
        }

        System.out.println((int)'0');


    }
}
