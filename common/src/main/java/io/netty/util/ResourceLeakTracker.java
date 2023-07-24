/*
 * Copyright 2016 The Netty Project
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
 * @date 2023/6/12 10:34
 * @author likai
 * @email likai9376@163.com
 * @desc 资源泄露追踪器
 *
 * @see ResourceLeak same this class bug @Deprecated
 *
 * @see io.netty.util.ResourceLeakDetector.DefaultResourceLeak
 */
public interface ResourceLeakTracker<T>  {

    /**
     * Records the caller's current stack trace so that the {@link ResourceLeakDetector} can tell where the leaked
     * resource was accessed lastly. This method is a shortcut to {@link #record(Object) record(null)}.
     *
     * 记录当前调用者的堆栈，以便于 ResourceLeakDetector 告诉资源泄露 最后访问的位置。
     */
    void record();

    /**
     * Records the caller's current stack trace and the specified additional arbitrary information
     * so that the {@link ResourceLeakDetector} can tell where the leaked resource was accessed lastly.
     *
     * 记录当前调用者的堆栈信息并制定附加的任意信息。。以便于 ResourceLeakDetector
     */
    void record(Object hint);

    /**
     * Close the leak so that {@link ResourceLeakTracker} does not warn about leaked resources.
     * After this method is called a leak associated with this ResourceLeakTracker should not be reported.
     *
     * 关闭泄漏，以便｛@link ResourceLeakTracker｝不会对泄漏的资源发出警告。
     * 调用此方法后，不应报告与此ResourceLeakTracker关联的泄漏
     * @return {@code true} if called first time, {@code false} if called already
     */
    boolean close(T trackedObject);
}
