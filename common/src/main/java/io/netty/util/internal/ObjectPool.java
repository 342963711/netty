/*
 * Copyright 2019 The Netty Project
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
package io.netty.util.internal;

import io.netty.util.Recycler;

/**
 * Light-weight object pool.
 * 轻量级的 对象池
 *
 * @param <T> the type of the pooled object 。
 */
public abstract class ObjectPool<T> {

    ObjectPool() { }

    /**
     * Get a {@link Object} from the {@link ObjectPool}. The returned {@link Object} may be created via
     * {@link ObjectCreator#newObject(Handle)} if no pooled {@link Object} is ready to be reused.
     *
     * 从 ObjectPool获取 一个对象 。 如果没有池化的Object 准备好被复用，返回的对象应该是通过 {@link ObjectCreator#newObject(Handle)}穿件返回的
     */
    public abstract T get();

    /**
     * Handle for an pooled {@link Object} that will be used to notify the {@link ObjectPool} once it can
     * reuse the pooled {@link Object} again.
     *
     * 池化对象的句柄，一旦池化对象可以被复用。将通知ObjectPool.
     * @see io.netty.util.Recycler.DefaultHandle
     * @see io.netty.util.Recycler.Handle
     * @param <T>
     */
    public interface Handle<T> {
        /**
         * Recycle the {@link Object} if possible and so make it ready to be reused.
         * //如果可能的话，请回收 self,使其可以重复使用
         */
        void recycle(T self);
    }

    /**
     * Creates a new Object which references the given {@link Handle} and calls {@link Handle#recycle(Object)} once
     * it can be re-used.
     *
     * @param <T> the type of the pooled object
     */
    public interface ObjectCreator<T> {

        /**
         * Creates an returns a new {@link Object} that can be used and later recycled via
         * {@link Handle#recycle(Object)}.
         *
         * 创建一个对象，可以被使用并在之后 通过 Handler 来进行对象管理。被管理的资源一般都是
         *
         * ObjectCreator<DemoResource></> objectCreator = new ObjectCreator() {
         *             @Override
         *             public DemoResource newObject(Handle handle) {
         *                 return DemoResource(handle);
         *             }
         * };
         * 将该 创建器 交给 RecyclerObjectPool<DemoResource> obejctPool = new RecyclerObjectPool(objectCreator);
         *
         * @param handle can NOT be null.
         */
        T newObject(Handle<T> handle);
    }

    /**
     * Creates a new {@link ObjectPool} which will use the given {@link ObjectCreator} to create the {@link Object}
     * that should be pooled.
     */
    public static <T> ObjectPool<T> newPool(final ObjectCreator<T> creator) {
        return new RecyclerObjectPool<T>(ObjectUtil.checkNotNull(creator, "creator"));
    }

    /**
     * 对象池ObjectPool的默认实现，
     * 需要 1.ObjectCreator 来进行 对象池的初始化，
     * 需要 2.考虑对象回收机制。也就是 Handle 的实现类（交给 RecyclerObjectPool来实现）
     * @param <T>
     */
    private static final class RecyclerObjectPool<T> extends ObjectPool<T> {

        //回收器，匿名类对 ObjectCreator 进行包装。 可以进行对象创建，同时，创建出来的对象都有Handler 可以进行自身回收
        private final Recycler<T> recycler;

        RecyclerObjectPool(final ObjectCreator<T> creator) {
             recycler = new Recycler<T>() {
                @Override
                protected T newObject(Handle<T> handle) {
                    return creator.newObject(handle);
                }
            };
        }

        @Override
        public T get() {
            return recycler.get();
        }
    }

}
