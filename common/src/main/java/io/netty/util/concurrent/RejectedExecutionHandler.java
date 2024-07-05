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
package io.netty.util.concurrent;

/**
 * Similar to {@link java.util.concurrent.RejectedExecutionHandler} but specific to {@link SingleThreadEventExecutor}.
 *
 * 与jdk 的 RejectedExecutionHandler 类似，但是指定的是SingleThreadEventExecutor 类型
 */
public interface RejectedExecutionHandler {

    /**
     * Called when someone tried to add a task to {@link SingleThreadEventExecutor} but this failed due capacity
     * restrictions.
     *
     * 当有人试图将任务添加到｛@link SingleThreadEventExecutor｝，但由于容量限制而失败时调用。
     */
    void rejected(Runnable task, SingleThreadEventExecutor executor);
}
