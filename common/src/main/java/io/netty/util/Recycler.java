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

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.ObjectPool;
import io.netty.util.internal.ObjectPool.Handle;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jctools.queues.MessagePassingQueue;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.util.internal.PlatformDependent.newMpscQueue;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Light-weight object pool based on a thread-local stack.
 * 基于本地线程栈 的轻量 对象池
 *
 * 池对象创建，回收，核心控制类
 *
 * 回收的三大类
 * @see ObjectPool#get()
 * @see ObjectPool.Handle#recycle(Object)
 * @see ObjectPool.ObjectCreator#newObject(ObjectPool.Handle) ;
 *
 * 默认实现类：
 * @see ObjectPool.RecyclerObjectPool() 方法中的匿名类实现
 *
 * @param <T> the type of the pooled object ， 池对象的类型
 */
public abstract class Recycler<T> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Recycler.class);

    //创建空的回收类
    private static final Handle<?> NOOP_HANDLE = new Handle<Object>() {
        @Override
        public void recycle(Object object) {
            // NOOP
        }

        @Override
        public String toString() {
            return "NOOP_HANDLE";
        }
    };

    /**
     * 默认使用4k实例
     */
    private static final int DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD = 4 * 1024; // Use 4k instances as default.

    /**
     * 每个线程默认最大容量 默认是4k
     */
    private static final int DEFAULT_MAX_CAPACITY_PER_THREAD;

    /**
     * 比例. 默认是8
     */
    private static final int RATIO;

    /**
     * 每个线程的 默认队列 中的块数量。 默认是 32
     */
    private static final int DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD;

    /**
     * 默认 false,
     */
    private static final boolean BLOCKING_POOL;

    /**
     * true,仅批次fastThreadLocal
     */
    private static final boolean BATCH_FAST_TL_ONLY;

    static {
        // In the future, we might have different maxCapacity for different object types.
        // e.g. io.netty.recycler.maxCapacity.writeTask
        //      io.netty.recycler.maxCapacity.outboundBuffer
        int maxCapacityPerThread = SystemPropertyUtil.getInt("io.netty.recycler.maxCapacityPerThread",
                SystemPropertyUtil.getInt("io.netty.recycler.maxCapacity", DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD));
        if (maxCapacityPerThread < 0) {
            maxCapacityPerThread = DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD;
        }

        DEFAULT_MAX_CAPACITY_PER_THREAD = maxCapacityPerThread;
        DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD = SystemPropertyUtil.getInt("io.netty.recycler.chunkSize", 32);

        // By default, we allow one push to a Recycler for each 8th try on handles that were never recycled before.
        // This should help to slowly increase the capacity of the recycler while not be too sensitive to allocation
        // bursts.
        /**
         * 默认情况下，我们允许每8次尝试以前从未回收过的手柄，就向回收器推送一次。
         *
         * 这应该有助于缓慢增加回收器的容量，同时对分配突发不太敏感。
         **/
        RATIO = max(0, SystemPropertyUtil.getInt("io.netty.recycler.ratio", 8));

        BLOCKING_POOL = SystemPropertyUtil.getBoolean("io.netty.recycler.blocking", false);
        BATCH_FAST_TL_ONLY = SystemPropertyUtil.getBoolean("io.netty.recycler.batchFastThreadLocalOnly", true);

        if (logger.isDebugEnabled()) {
            if (DEFAULT_MAX_CAPACITY_PER_THREAD == 0) {
                logger.debug("-Dio.netty.recycler.maxCapacityPerThread: disabled");
                logger.debug("-Dio.netty.recycler.ratio: disabled");
                logger.debug("-Dio.netty.recycler.chunkSize: disabled");
                logger.debug("-Dio.netty.recycler.blocking: disabled");
                logger.debug("-Dio.netty.recycler.batchFastThreadLocalOnly: disabled");
            } else {
                logger.debug("-Dio.netty.recycler.maxCapacityPerThread: {}", DEFAULT_MAX_CAPACITY_PER_THREAD);
                logger.debug("-Dio.netty.recycler.ratio: {}", RATIO);
                logger.debug("-Dio.netty.recycler.chunkSize: {}", DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD);
                logger.debug("-Dio.netty.recycler.blocking: {}", BLOCKING_POOL);
                logger.debug("-Dio.netty.recycler.batchFastThreadLocalOnly: {}", BATCH_FAST_TL_ONLY);
            }
        }
    }

    /**
     * 每个线程容量 4k
     */
    private final int maxCapacityPerThread;

    //interval = max(0, ratio);  初始化默认值是 8
    private final int interval;

    /**
     * 块大小，默认是 32
     *
     */
    private final int chunkSize;

    /**
     * 每个线程都存储一个 LocalPool
     */
    private final FastThreadLocal<LocalPool<T>> threadLocal = new FastThreadLocal<LocalPool<T>>() {
        @Override
        protected LocalPool<T> initialValue() {
            // 默认值 分别是 4k, 8, 32
            return new LocalPool<T>(maxCapacityPerThread, interval, chunkSize);
        }

        @Override
        protected void onRemoval(LocalPool<T> value) throws Exception {
            super.onRemoval(value);
            MessagePassingQueue<DefaultHandle<T>> handles = value.pooledHandles;
            value.pooledHandles = null;
            value.owner = null;
            handles.clear();
        }
    };

    protected Recycler() {
        this(DEFAULT_MAX_CAPACITY_PER_THREAD);
    }

    protected Recycler(int maxCapacityPerThread) {
        this(maxCapacityPerThread, RATIO, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD);
    }

    /**
     * @deprecated Use one of the following instead:
     * {@link #Recycler()}, {@link #Recycler(int)}, {@link #Recycler(int, int, int)}.
     */
    @Deprecated
    @SuppressWarnings("unused") // Parameters we can't remove due to compatibility.
    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor) {
        this(maxCapacityPerThread, RATIO, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD);
    }

    /**
     * @deprecated Use one of the following instead:
     * {@link #Recycler()}, {@link #Recycler(int)}, {@link #Recycler(int, int, int)}.
     */
    @Deprecated
    @SuppressWarnings("unused") // Parameters we can't remove due to compatibility.
    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor,
                       int ratio, int maxDelayedQueuesPerThread) {
        this(maxCapacityPerThread, ratio, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD);
    }

    /**
     * @deprecated Use one of the following instead:
     * {@link #Recycler()}, {@link #Recycler(int)}, {@link #Recycler(int, int, int)}.
     */
    @Deprecated
    @SuppressWarnings("unused") // Parameters we can't remove due to compatibility.
    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor,
                       int ratio, int maxDelayedQueuesPerThread, int delayedQueueRatio) {
        this(maxCapacityPerThread, ratio, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD);
    }

    /**
     *
     * @param maxCapacityPerThread 4096
     * @param ratio 8
     * @param chunkSize 32
     */
    protected Recycler(int maxCapacityPerThread, int ratio, int chunkSize) {
        interval = max(0, ratio);
        if (maxCapacityPerThread <= 0) {
            this.maxCapacityPerThread = 0;
            this.chunkSize = 0;
        } else {
            this.maxCapacityPerThread = max(4, maxCapacityPerThread);
            this.chunkSize = max(2, min(chunkSize, this.maxCapacityPerThread >> 1));
        }
    }

    @SuppressWarnings("unchecked")
    public final T get() {
        if (maxCapacityPerThread == 0) {
            return newObject((Handle<T>) NOOP_HANDLE);
        }
        //是一个消费端。
        LocalPool<T> localPool = threadLocal.get();
        //申请一个handler，返回的handler 中持有的对象肯定是可以被使用的。（否则为空）
        /**
         * @see DefaultHandle#recycle(Object) 如果创建对象，不执行该方法，则存储 Handler的数组会越来越大
         */
        DefaultHandle<T> handle = localPool.claim();
        T obj;
        if (handle == null) {
            //创建一个handle。（如果不满足频率，为空）
            handle = localPool.newHandle();
            if (handle != null) {
                //开始创建对象，并委托给handle
                obj = newObject(handle);
                handle.set(obj);
            } else {
                obj = newObject((Handle<T>) NOOP_HANDLE);
            }
        } else {
            obj = handle.get();
        }

        return obj;
    }

    /**
     * @deprecated use {@link Handle#recycle(Object)}.
     */
    @Deprecated
    public final boolean recycle(T o, Handle<T> handle) {
        if (handle == NOOP_HANDLE) {
            return false;
        }

        handle.recycle(o);
        return true;
    }

    @VisibleForTesting
    final int threadLocalSize() {
        LocalPool<T> localPool = threadLocal.getIfExists();
        return localPool == null ? 0 : localPool.pooledHandles.size() + localPool.batch.size();
    }

    /**
     * @param handle can NOT be null. 用于管理创建的对象
     * 创建的具体实现，委托给对象创建器。在业务层获取对象时候，去判断是否应该创建对象
     * @see ObjectPool.ObjectCreator 去实现
     *
     */
    protected abstract T newObject(Handle<T> handle);

    /**
     * 因兼容性问题，不能修改
     * @param <T>
     */
    @SuppressWarnings("ClassNameSameAsAncestorName") // Can't change this due to compatibility.
    public interface Handle<T> extends ObjectPool.Handle<T>  { }

    /**
     * 默认的 回收处理
     * @param <T>
     */
    private static final class DefaultHandle<T> implements Handle<T> {
        //初始化为可回收状态，不可被再次使用。
        private static final int STATE_CLAIMED = 0;

        //标记为已回收，可以被使用
        private static final int STATE_AVAILABLE = 1;

        private static final AtomicIntegerFieldUpdater<DefaultHandle<?>> STATE_UPDATER;
        static {
            AtomicIntegerFieldUpdater<?> updater = AtomicIntegerFieldUpdater.newUpdater(DefaultHandle.class, "state");
            //noinspection unchecked
            STATE_UPDATER = (AtomicIntegerFieldUpdater<DefaultHandle<?>>) updater;
        }

        private volatile int state; // State is initialised to STATE_CLAIMED (aka. 0) so they can be released.

        //通过该对象来进行回收
        private final LocalPool<T> localPool;
        //持有的对象
        private T value;

        /**
         * 回收器操作 委托给 LocalPool 去实现
         * @param localPool
         */
        DefaultHandle(LocalPool<T> localPool) {
            this.localPool = localPool;
        }

        /**
         * 回收对象，回收的对象一定是与当前回收操作类持有的对象是同一个对象
         * @param object
         */
        @Override
        public void recycle(Object object) {
            if (object != value) {
                throw new IllegalArgumentException("object does not belong to handle");
            }
            localPool.release(this);
        }

        /**
         * 设置回收器 管理的对象
         * @return
         */
        T get() {
            return value;
        }

        /**
         * 设置回收器管理的对象
         * @param value
         */
        void set(T value) {
            this.value = value;
        }

        /**
         * 标记为 被使用
         */
        void toClaimed() {
            assert state == STATE_AVAILABLE;
            state = STATE_CLAIMED;
        }

        /**
         * 标记为可使用
         */
        void toAvailable() {
            int prev = STATE_UPDATER.getAndSet(this, STATE_AVAILABLE);
            if (prev == STATE_AVAILABLE) {
                throw new IllegalStateException("Object has been recycled already.");
            }
        }
    }

    /**
     * 本地池, 主要是管理 DefaultHandle 的创建
     *
     * MessagePassingQueue 队列的消费实现
     * @param <T>
     */
    private static final class LocalPool<T> implements MessagePassingQueue.Consumer<DefaultHandle<T>> {
        /**
         * 比例间隔
         */
        private final int ratioInterval;

        /**
         * 块 数量
         */
        private final int chunkSize;

        /**
         * 该队列中 回收器的对象 都是可以被使用的状态
         */
        private final ArrayDeque<DefaultHandle<T>> batch;

        private volatile Thread owner;
        /**
         * 消息传递队列，多生产者，单消费者
         */
        private volatile MessagePassingQueue<DefaultHandle<T>> pooledHandles;

        /**
         * 比例 计数
         */
        private int ratioCounter;

        /**
         *
         * @param maxCapacity 4k
         * @param ratioInterval 8
         * @param chunkSize 32
         */
        @SuppressWarnings("unchecked")
        LocalPool(int maxCapacity, int ratioInterval, int chunkSize) {
            this.ratioInterval = ratioInterval;
            this.chunkSize = chunkSize;
            batch = new ArrayDeque<DefaultHandle<T>>(chunkSize);
            Thread currentThread = Thread.currentThread();
            owner = !BATCH_FAST_TL_ONLY || currentThread instanceof FastThreadLocalThread ? currentThread : null;
            if (BLOCKING_POOL) {
                //一般是为了测试
                pooledHandles = new BlockingMessageQueue<DefaultHandle<T>>(maxCapacity);
            } else {
                pooledHandles = (MessagePassingQueue<DefaultHandle<T>>) newMpscQueue(chunkSize, maxCapacity);
            }
            //每隔一段时间启动，这样第一个将被回收
            ratioCounter = ratioInterval; // Start at interval so the first one will be recycled.
        }

        DefaultHandle<T> claim() {
            MessagePassingQueue<DefaultHandle<T>> handles = pooledHandles;
            if (handles == null) {
                return null;
            }
            //如果可用为空
            if (batch.isEmpty()) {
                //进行消费
                handles.drain(this, chunkSize);
            }
            //再次获取一个回收handle.
            DefaultHandle<T> handle = batch.pollFirst();
            //获取到的handle不为空
            if (null != handle) {
                //标记handle中对象状态为被使用
                handle.toClaimed();
            }
            //从队列中返回handle
            return handle;
        }

        /**
         * 回收池中一个对象
         * @param handle
         */
        void release(DefaultHandle<T> handle) {
            //回收器中对象的状态更改为可用
            handle.toAvailable();
            Thread owner = this.owner;
            if (owner != null && Thread.currentThread() == owner && batch.size() < chunkSize) {
                //放入到 可用队列中
                accept(handle);
            } else if (owner != null && owner.getState() == Thread.State.TERMINATED) {
                //如果线程状态异常，清理资源，GC
                this.owner = null;
                pooledHandles = null;
            } else {
                // 添加到 MessagePassingQueue 中
                MessagePassingQueue<DefaultHandle<T>> handles = pooledHandles;
                if (handles != null) {
                    //添加到消费队列中
                    handles.relaxedOffer(handle);
                }
            }
        }

        //用于创建新的 DefaultHandle 对象，
        DefaultHandle<T> newHandle() {
            if (++ratioCounter >= ratioInterval) {
                ratioCounter = 0;
                return new DefaultHandle<T>(this);
            }
            return null;
        }

        /**
         * 可以处理 回收操作
         * @param e not {@code null}
         */
        @Override
        public void accept(DefaultHandle<T> e) {
            batch.addLast(e);
        }
    }

    /**
     * This is an implementation of {@link MessagePassingQueue}, similar to what might be returned from
     * {@link PlatformDependent#newMpscQueue(int)}, but intended to be used for debugging purpose.
     * The implementation relies on synchronised monitor locks for thread-safety.
     * The {@code fill} bulk operation is not supported by this implementation.
     *
     * 这是｛@link MessagePassingQueue｝的实现，类似于从｛@link PlatformDependent#newMpscQueue（int）｝，但用于调试目的。
     * 该实现依赖于同步监视器锁来实现线程安全。此实现不支持｛@code fill｝批量操作。
     */
    private static final class BlockingMessageQueue<T> implements MessagePassingQueue<T> {
        private final Queue<T> deque;
        private final int maxCapacity;

        BlockingMessageQueue(int maxCapacity) {
            this.maxCapacity = maxCapacity;
            // This message passing queue is backed by an ArrayDeque instance,
            // made thread-safe by synchronising on `this` BlockingMessageQueue instance.
            // Why ArrayDeque?
            // We use ArrayDeque instead of LinkedList or LinkedBlockingQueue because it's more space efficient.
            // We use ArrayDeque instead of ArrayList because we need the queue APIs.
            // We use ArrayDeque instead of ConcurrentLinkedQueue because CLQ is unbounded and has O(n) size().
            // We use ArrayDeque instead of ArrayBlockingQueue because ABQ allocates its max capacity up-front,
            // and these queues will usually have large capacities, in potentially great numbers (one per thread),
            // but often only have comparatively few items in them.
            deque = new ArrayDeque<T>();
        }

        @Override
        public synchronized boolean offer(T e) {
            if (deque.size() == maxCapacity) {
                return false;
            }
            return deque.offer(e);
        }

        @Override
        public synchronized T poll() {
            return deque.poll();
        }

        @Override
        public synchronized T peek() {
            return deque.peek();
        }

        @Override
        public synchronized int size() {
            return deque.size();
        }

        @Override
        public synchronized void clear() {
            deque.clear();
        }

        @Override
        public synchronized boolean isEmpty() {
            return deque.isEmpty();
        }

        @Override
        public int capacity() {
            return maxCapacity;
        }

        @Override
        public boolean relaxedOffer(T e) {
            return offer(e);
        }

        @Override
        public T relaxedPoll() {
            return poll();
        }

        @Override
        public T relaxedPeek() {
            return peek();
        }

        @Override
        public int drain(Consumer<T> c, int limit) {
            T obj;
            int i = 0;
            for (; i < limit && (obj = poll()) != null; i++) {
                c.accept(obj);
            }
            return i;
        }

        @Override
        public int fill(Supplier<T> s, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int drain(Consumer<T> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int fill(Supplier<T> s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void drain(Consumer<T> c, WaitStrategy wait, ExitCondition exit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fill(Supplier<T> s, WaitStrategy wait, ExitCondition exit) {
            throw new UnsupportedOperationException();
        }
    }
}
