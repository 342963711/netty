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

/**
 * Extensible decoder and its common implementations which deal with the
 * packet fragmentation and reassembly issue found in a stream-based transport
 * such as TCP/IP.
 * 可扩展解码器及其常见实现，用于处理基于流的传输中发现的数据包碎片和重组问题
 *
 * 例如TCP/IP。
 */
package io.netty.handler.codec;
