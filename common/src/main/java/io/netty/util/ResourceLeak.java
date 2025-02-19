/*
 * Copyright 2013 The Netty Project
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

package io.netty.util;

/**
 * @deprecated please use {@link ResourceLeakTracker} as it may lead to false-positives.
 * 请使用ResourceLeakTracker，因为这个类可能会导致误报
 */
@Deprecated
public interface ResourceLeak {
    /**
     * Records the caller's current stack trace so that the {@link ResourceLeakDetector} can tell where the leaked
     * resource was accessed lastly. This method is a shortcut to {@link #record(Object) record(null)}.
     *
     * 记录调用方的当前堆栈跟踪，以便｛@link ResourceLeakDetector｝能够告诉泄漏的资源最后被访问的位置。
     * 此方法是｛@link#record（Object）record（null）｝的快捷方式。
     */
    void record();

    /**
     * Records the caller's current stack trace and the specified additional arbitrary information
     * so that the {@link ResourceLeakDetector} can tell where the leaked resource was accessed lastly.
     *
     * Records the caller's current stack trace and the specified additional
     * arbitrary information so that the {@link ResourceLeakDetector} can tell where
     * the leaked resource was accessed lastly.
     */
    void record(Object hint);

    /**
     * Close the leak so that {@link ResourceLeakDetector} does not warn about leaked resources.
     *
     * @return {@code true} if called first time, {@code false} if called already
     *
     * 关闭泄漏，以便｛@link ResourceLeakDetector｝不会对泄漏的资源发出警告。
     * @如果第一次调用，则返回｛@code true｝；如果已经调用，则为｛@codefalse｝
     */
    boolean close();
}
