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

import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static io.netty.util.internal.StringUtil.EMPTY_STRING;
import static io.netty.util.internal.StringUtil.NEWLINE;
import static io.netty.util.internal.StringUtil.simpleClassName;

/**
 * @date 2023/6/6 22:58
 * @author likai
 * @email likai9376@163.com
 * @desc 资源泄露检查器
 * 该类没有其余子类（除了测试的子类）
 *
 * 该类由 {@link ResourceLeakDetectorFactory} 进行初始化管理
 *
 * {@link ResourceLeakDetector#getInitialHint(String) } 可以由自定义的泄露探测器来自定义 泄露资源的一些额外信息
 * {@link ResourceLeakDetector#track(Object)} 对指定的资源进行追踪
 * {@link ResourceLeakTracker#record(Object)} 对资源使用情况进行记录，以便于记录资源最近被使用的位置
 *
 * 内部类包含
 * @see DefaultResourceLeak
 * @see Level
 * @see TraceRecord
 */
public class ResourceLeakDetector<T> {

    private static final String PROP_LEVEL_OLD = "io.netty.leakDetectionLevel";
    private static final String PROP_LEVEL = "io.netty.leakDetection.level";
    private static final Level DEFAULT_LEVEL = Level.SIMPLE;

    //目标记录
    private static final String PROP_TARGET_RECORDS = "io.netty.leakDetection.targetRecords";
    private static final int DEFAULT_TARGET_RECORDS = 4;

    //采样频率
    private static final String PROP_SAMPLING_INTERVAL = "io.netty.leakDetection.samplingInterval";
    // There is a minor performance benefit in TLR if this is a power of 2.
    //如果这是2的幂，那么TLR的性能会有一些小的好处。
    private static final int DEFAULT_SAMPLING_INTERVAL = 128;

    //目标记录数
    private static final int TARGET_RECORDS;

    // 默认 128
    static final int SAMPLING_INTERVAL;

    /**
     * Represents the level of resource leak detection.
     * 表示资源泄漏检测级别。
     */
    public enum Level {
        /**
         * Disables resource leak detection.
         * 禁用资源泄露检测
         */
        DISABLED,
        /**
         * Enables simplistic sampling resource leak detection which reports there is a leak or not,
         * at the cost of small overhead (default).
         *
         * 启用简单的采样资源泄漏检测，以较小的开销为代价报告是否存在泄漏（默认值）。
         */
        SIMPLE,
        /**
         * Enables advanced sampling resource leak detection which reports where the leaked object was accessed
         * recently at the cost of high overhead.
         *
         * 启用高级采样资源泄漏检测，该检测以高开销为代价报告最近访问泄漏对象的位置。
         */
        ADVANCED,
        /**
         * Enables paranoid resource leak detection which reports where the leaked object was accessed recently,
         * at the cost of the highest possible overhead (for testing purposes only).
         *
         * 启用偏执的资源泄漏检测，该检测报告最近访问泄漏对象的位置，并以尽可能高的开销为代价（仅用于测试目的）.
         */
        PARANOID;

        /**
         * Returns level based on string value. Accepts also string that represents ordinal number of enum.
         *
         * @param levelStr - level string : DISABLED, SIMPLE, ADVANCED, PARANOID. Ignores case.
         * @return corresponding level or SIMPLE level in case of no match.
         */
        static Level parseLevel(String levelStr) {
            String trimmedLevelStr = levelStr.trim();
            for (Level l : values()) {
                if (trimmedLevelStr.equalsIgnoreCase(l.name()) || trimmedLevelStr.equals(String.valueOf(l.ordinal()))) {
                    return l;
                }
            }
            return DEFAULT_LEVEL;
        }
    }

    private static Level level;

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ResourceLeakDetector.class);

    static {
        final boolean disabled;
        if (SystemPropertyUtil.get("io.netty.noResourceLeakDetection") != null) {
            disabled = SystemPropertyUtil.getBoolean("io.netty.noResourceLeakDetection", false);
            logger.debug("-Dio.netty.noResourceLeakDetection: {}", disabled);
            logger.warn(
                    "-Dio.netty.noResourceLeakDetection is deprecated. Use '-D{}={}' instead.",
                    PROP_LEVEL, Level.DISABLED.name().toLowerCase());
        } else {
            disabled = false;
        }

        Level defaultLevel = disabled? Level.DISABLED : DEFAULT_LEVEL;

        // First read old property name
        String levelStr = SystemPropertyUtil.get(PROP_LEVEL_OLD, defaultLevel.name());

        // If new property name is present, use it
        levelStr = SystemPropertyUtil.get(PROP_LEVEL, levelStr);
        Level level = Level.parseLevel(levelStr);

        TARGET_RECORDS = SystemPropertyUtil.getInt(PROP_TARGET_RECORDS, DEFAULT_TARGET_RECORDS);
        SAMPLING_INTERVAL = SystemPropertyUtil.getInt(PROP_SAMPLING_INTERVAL, DEFAULT_SAMPLING_INTERVAL);

        ResourceLeakDetector.level = level;
        if (logger.isDebugEnabled()) {
            logger.debug("-D{}: {}", PROP_LEVEL, level.name().toLowerCase());
            logger.debug("-D{}: {}", PROP_TARGET_RECORDS, TARGET_RECORDS);
        }
    }

    /**
     * @deprecated Use {@link #setLevel(Level)} instead.
     */
    @Deprecated
    public static void setEnabled(boolean enabled) {
        setLevel(enabled? Level.SIMPLE : Level.DISABLED);
    }

    /**
     * Returns {@code true} if resource leak detection is enabled.
     */
    public static boolean isEnabled() {
        return getLevel().ordinal() > Level.DISABLED.ordinal();
    }

    /**
     * Sets the resource leak detection level.
     */
    public static void setLevel(Level level) {
        ResourceLeakDetector.level = ObjectUtil.checkNotNull(level, "level");
    }

    /**
     * Returns the current resource leak detection level.
     */
    public static Level getLevel() {
        return level;
    }

    /** the collection of active resources
     * 所有活跃资源的集合
     * */
    private final Set<DefaultResourceLeak<?>> allLeaks =
            Collections.newSetFromMap(new ConcurrentHashMap<DefaultResourceLeak<?>, Boolean>());

    /**
     * 引用对象队列
     */
    private final ReferenceQueue<Object> refQueue = new ReferenceQueue<Object>();

    /**
     * 报告的泄露
     */
    private final Set<String> reportedLeaks =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /**
     * 探测的资源类型（class name）
     */
    private final String resourceType;

    /**
     * 采样频率，默认值是128
     */
    private final int samplingInterval;

    /**
     * @deprecated use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}.
     */
    @Deprecated
    public ResourceLeakDetector(Class<?> resourceType) {
        this(simpleClassName(resourceType));
    }

    /**
     * @deprecated use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}.
     */
    @Deprecated
    public ResourceLeakDetector(String resourceType) {
        this(resourceType, DEFAULT_SAMPLING_INTERVAL, Long.MAX_VALUE);
    }

    /**
     * @deprecated Use {@link ResourceLeakDetector#ResourceLeakDetector(Class, int)}.
     * <p>
     * This should not be used directly by users of {@link ResourceLeakDetector}.
     * Please use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class)}
     * or {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}
     *
     * @param maxActive This is deprecated and will be ignored.
     */
    @Deprecated
    public ResourceLeakDetector(Class<?> resourceType, int samplingInterval, long maxActive) {
        this(resourceType, samplingInterval);
    }

    /**
     * This should not be used directly by users of {@link ResourceLeakDetector}.
     * Please use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class)}
     * or {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}
     */
    @SuppressWarnings("deprecation")
    public ResourceLeakDetector(Class<?> resourceType, int samplingInterval) {
        this(simpleClassName(resourceType), samplingInterval, Long.MAX_VALUE);
    }

    /**
     * @deprecated use {@link ResourceLeakDetectorFactory#newResourceLeakDetector(Class, int, long)}.
     * <p>
     * @param maxActive This is deprecated and will be ignored.
     */
    @Deprecated
    public ResourceLeakDetector(String resourceType, int samplingInterval, long maxActive) {
        this.resourceType = ObjectUtil.checkNotNull(resourceType, "resourceType");
        this.samplingInterval = samplingInterval;
    }

    /**
     * Creates a new {@link ResourceLeak} which is expected to be closed via {@link ResourceLeak#close()} when the
     * related resource is deallocated.
     *
     * @return the {@link ResourceLeak} or {@code null}
     * @deprecated use {@link #track(Object)}
     */
    @Deprecated
    public final ResourceLeak open(T obj) {
        return track0(obj);
    }

    /**
     * Creates a new {@link ResourceLeakTracker} which is expected to be closed via
     * {@link ResourceLeakTracker#close(Object)} when the related resource is deallocated.
     *
     * 创建一个新的｛@link ResourceLeakTracker｝，当相关资源被释放时，该资源应通过｛@linkResourceLeaktracker#close（Object）｝关闭。
     *
     * @return the {@link ResourceLeakTracker} or {@code null}
     */
    @SuppressWarnings("unchecked")
    public final ResourceLeakTracker<T> track(T obj) {
        return track0(obj);
    }

    @SuppressWarnings("unchecked")
    private DefaultResourceLeak track0(T obj) {
        Level level = ResourceLeakDetector.level;
        if (level == Level.DISABLED) {
            return null;
        }

        if (level.ordinal() < Level.PARANOID.ordinal()) {
            if ((PlatformDependent.threadLocalRandom().nextInt(samplingInterval)) == 0) {
                //报告之前的泄露信息
                reportLeak();
                //创建资源泄露初始对象
                return new DefaultResourceLeak(obj, refQueue, allLeaks, getInitialHint(resourceType));
            }
            return null;
        }
        reportLeak();
        return new DefaultResourceLeak(obj, refQueue, allLeaks, getInitialHint(resourceType));
    }

    private void clearRefQueue() {
        for (;;) {
            DefaultResourceLeak ref = (DefaultResourceLeak) refQueue.poll();
            if (ref == null) {
                break;
            }
            ref.dispose();
        }
    }

    /**
     * When the return value is {@code true}, {@link #reportTracedLeak} and {@link #reportUntracedLeak}
     * will be called once a leak is detected, otherwise not.
     * 当返回值为true, ｛@link#reportTracedLeak｝和｛@link #reportUntracedLeak} 将在检测到泄漏后调用，否则不调用。
     * @return {@code true} to enable leak reporting.
     */
    protected boolean needReport() {
        return logger.isErrorEnabled();
    }

    /**
     * 报告泄露
     */
    private void reportLeak() {
        if (!needReport()) {
            clearRefQueue();
            return;
        }

        // Detect and report previous leaks.
        for (;;) {
            DefaultResourceLeak ref = (DefaultResourceLeak) refQueue.poll();
            //如果没有资源泄露，则直接结束
            if (ref == null) {
                break;
            }

            //如果处理失败，则不进行通知
            if (!ref.dispose()) {
                continue;
            }
            //获取资源泄露 报告，进行日志打印输出
            String records = ref.getReportAndClearRecords();
            if (reportedLeaks.add(records)) {
                if (records.isEmpty()) {
                    reportUntracedLeak(resourceType);
                } else {
                    reportTracedLeak(resourceType, records);
                }
            }
        }
    }

    /**
     * This method is called when a traced leak is detected. It can be overridden for tracking how many times leaks
     * have been detected.
     * 当检测到跟踪泄漏时，会调用此方法。它可以被覆盖，用于跟踪检测到泄漏的次数。
     */
    protected void reportTracedLeak(String resourceType, String records) {
        logger.error(
                "LEAK: {}.release() was not called before it's garbage-collected. " +
                "See https://netty.io/wiki/reference-counted-objects.html for more information.{}",
                resourceType, records);
    }

    /**
     * This method is called when an untraced leak is detected. It can be overridden for tracking how many times leaks
     * have been detected.
     * 当检测到未跟踪的泄漏时，会调用此方法。它可以被覆盖以跟踪检测到泄漏的次数。
     *
     */
    protected void reportUntracedLeak(String resourceType) {
        logger.error("LEAK: {}.release() was not called before it's garbage-collected. " +
                "Enable advanced leak reporting to find out where the leak occurred. " +
                "To enable advanced leak reporting, " +
                "specify the JVM option '-D{}={}' or call {}.setLevel() " +
                "See https://netty.io/wiki/reference-counted-objects.html for more information.",
                resourceType, PROP_LEVEL, Level.ADVANCED.name().toLowerCase(), simpleClassName(this));
    }

    /**
     * @deprecated This method will no longer be invoked by {@link ResourceLeakDetector}.
     */
    @Deprecated
    protected void reportInstancesLeak(String resourceType) {
    }

    /**
     * Create a hint object to be attached to an object tracked by this record. Similar to the additional information
     * supplied to {@link ResourceLeakTracker#record(Object)}, will be printed alongside the stack trace of the
     * creation of the resource.
     *
     * 创建一个提示对象以附加到 此记录跟踪 的对象。
     * 类似于提供给{@linkResourceLeakTracker#record（Object）}的附加信息，将与创建资源的堆栈跟踪一起打印。
     */
    protected Object getInitialHint(String resourceType) {
        return null;
    }


    /**
     * 内存泄露追踪类，复合管理所有的 资源记录 {@link TraceRecord}
     * 内部是一个单项链表
     * @param <T>
     */
    @SuppressWarnings("deprecation")
    private static final class DefaultResourceLeak<T>
            extends WeakReference<Object> implements ResourceLeakTracker<T>, ResourceLeak {

        @SuppressWarnings("unchecked") // generics and updaters do not mix.
        private static final AtomicReferenceFieldUpdater<DefaultResourceLeak<?>, TraceRecord> headUpdater =
                (AtomicReferenceFieldUpdater)
                        AtomicReferenceFieldUpdater.newUpdater(DefaultResourceLeak.class, TraceRecord.class, "head");

        @SuppressWarnings("unchecked") // generics and updaters do not mix.
        // 通用 和 更新程序不会混用，记录 删除掉的记录数
        private static final AtomicIntegerFieldUpdater<DefaultResourceLeak<?>> droppedRecordsUpdater =
                (AtomicIntegerFieldUpdater)
                        AtomicIntegerFieldUpdater.newUpdater(DefaultResourceLeak.class, "droppedRecords");

        //头部
        @SuppressWarnings("unused")
        private volatile TraceRecord head;
        @SuppressWarnings("unused")
        private volatile int droppedRecords;

        //所有的资源泄露
        private final Set<DefaultResourceLeak<?>> allLeaks;

        //跟踪独享的hash值
        private final int trackedHash;

        DefaultResourceLeak(
                Object referent,
                ReferenceQueue<Object> refQueue,
                Set<DefaultResourceLeak<?>> allLeaks,
                Object initialHint) {
            super(referent, refQueue);

            assert referent != null;

            // Store the hash of the tracked object to later assert it in the close(...) method.
            // It's important that we not store a reference to the referent as this would disallow it from
            // be collected via the WeakReference.
            //存储被跟踪对象的哈希，以便稍后在close（…）方法中断言它。
            //重要的是，我们不要存储对referent的引用，因为这会禁止它通过WeakReference收集
            trackedHash = System.identityHashCode(referent);
            allLeaks.add(this);
            // Create a new Record so we always have the creation stacktrace included.
            // 创建一个新的记录，我们总是有包含 创建的堆栈。 头插法
            headUpdater.set(this, initialHint == null ?
                    new TraceRecord(TraceRecord.BOTTOM) : new TraceRecord(TraceRecord.BOTTOM, initialHint));
            this.allLeaks = allLeaks;
        }

        @Override
        public void record() {
            record0(null);
        }

        @Override
        public void record(Object hint) {
            record0(hint);
        }

        /**
         * This method works by exponentially backing off as more records are present in the stack. Each record has a
         * 1 / 2^n chance of dropping the top most record and replacing it with itself. This has a number of convenient
         * properties:
         *
         * <ol>
         * <li>  The current record is always recorded. This is due to the compare and swap dropping the top most
         *       record, rather than the to-be-pushed record.
         * <li>  The very last access will always be recorded. This comes as a property of 1.
         * <li>  It is possible to retain more records than the target, based upon the probability distribution.
         * <li>  It is easy to keep a precise record of the number of elements in the stack, since each element has to
         *     know how tall the stack is.
         * </ol>
         *
         * 1.始终记录当前记录。这是由于比较和交换删除了最上面的记录，而不是要推送的记录
         * 2.将始终记录最后一次访问。这是1的属性。
         * 3.根据概率分布，可以保留比目标更多的记录
         * 4.很容易对堆栈中的元素数量进行精确记录，因为每个元素都必须知道堆栈的高度。
         *
         * In this particular implementation, there are also some advantages. A thread local random is used to decide
         * if something should be recorded. This means that if there is a deterministic access pattern, it is now
         * possible to see what other accesses occur, rather than always dropping them. Second, after
         * {@link #TARGET_RECORDS} accesses, backoff occurs. This matches typical access patterns,
         * where there are either a high number of accesses (i.e. a cached buffer), or low (an ephemeral buffer), but
         * not many in between.
         *
         * The use of atomics avoids serializing a high number of accesses, when most of the records will be thrown
         * away. High contention only happens when there are very few existing records, which is only likely when the
         * object isn't shared! If this is a problem, the loop can be aborted and the record dropped, because another
         * thread won the race.
         *
         * 在这个特定的实现中，也有一些优点。线程局部随机用来决定是否应该记录某些内容。这意味着，如果存在确定性访问模式，那么现在就是可以查看发生了哪些其他访问，而不是总是丢弃它们。
         * 其次，在TARGET_RECORDS（默认值4）此访问之后，会发生回退。这与经典的访问模式向匹配，存在大量访问（缓存缓冲区）或者少量访问（短暂缓冲区）。两者之间的访问并不多。
         *
         *
         */
        private void record0(Object hint) {
            // Check TARGET_RECORDS > 0 here to avoid similar check before remove from and add to lastRecords
            if (TARGET_RECORDS > 0) {
                TraceRecord oldHead;
                TraceRecord prevHead;
                TraceRecord newHead;
                boolean dropped;
                do {
                    if ((prevHead = oldHead = headUpdater.get(this)) == null) {
                        // already closed.
                        return;
                    }
                    final int numElements = oldHead.pos + 1;
                    //当超过 目录记录数 的时候
                    if (numElements >= TARGET_RECORDS) {
                        //给出一个回退因子
                        final int backOffFactor = Math.min(numElements - TARGET_RECORDS, 30);
                        //根据回退因子进行计算 是否进行记录的删除
                        if (dropped = PlatformDependent.threadLocalRandom().nextInt(1 << backOffFactor) != 0) {
                            prevHead = oldHead.next;
                        }
                    } else {
                        dropped = false;
                    }
                    //创建新的记录, 这里就是头插法，将本次追踪的链路追踪出来
                    newHead = hint != null ? new TraceRecord(prevHead, hint) : new TraceRecord(prevHead);
                } while (!headUpdater.compareAndSet(this, oldHead, newHead));

                if (dropped) {
                    droppedRecordsUpdater.incrementAndGet(this);
                }
            }
        }

        /**
         * 进行处理
         * @return
         */
        boolean dispose() {
            clear();
            return allLeaks.remove(this);
        }

        @Override
        public boolean close() {
            if (allLeaks.remove(this)) {
                // Call clear so the reference is not even enqueued.
                // 调用 clear, 这样引用甚至不会排队
                clear();
                headUpdater.set(this, null);
                return true;
            }
            return false;
        }

        @Override
        public boolean close(T trackedObject) {
            // Ensure that the object that was tracked is the same as the one that was passed to close(...).
            assert trackedHash == System.identityHashCode(trackedObject);

            try {
                return close();
            } finally {
                // This method will do `synchronized(trackedObject)` and we should be sure this will not cause deadlock.
                // It should not, because somewhere up the callstack should be a (successful) `trackedObject.release`,
                // therefore it is unreasonable that anyone else, anywhere, is holding a lock on the trackedObject.
                // (Unreasonable but possible, unfortunately.)
                reachabilityFence0(trackedObject);
            }
        }

         /**
         * Ensures that the object referenced by the given reference remains
         * <a href="package-summary.html#reachability"><em>strongly reachable</em></a>,
         * regardless of any prior actions of the program that might otherwise cause
         * the object to become unreachable; thus, the referenced object is not
         * reclaimable by garbage collection at least until after the invocation of
         * this method.
          *
          * 确保给定引用引用的对象保持强可达性，而不管程序先前的任何操作是否可能导致对象变得不可达；因此，被引用的对象至少在调用该方法之后才能被垃圾回收回收。
         *
         * <p> Recent versions of the JDK have a nasty habit of prematurely deciding objects are unreachable.
         * see: https://stackoverflow.com/questions/26642153/finalize-called-on-strongly-reachable-object-in-java-8
         * The Java 9 method Reference.reachabilityFence offers a solution to this problem.
          * JDK的最新版本有一个坏习惯，就是过早地决定对象是不可访问的。Java 9方法Reference.areachabilityFence为这个问题提供了一个解决方案。
         *
         * <p> This method is always implemented as a synchronization on {@code ref}, not as
         * {@code Reference.reachabilityFence} for consistency across platforms and to allow building on JDK 6-8.
         * <b>It is the caller's responsibility to ensure that this synchronization will not cause deadlock.</b>
         *
         * @param ref the reference. If {@code null}, this method has no effect.
         * @see java.lang.ref.Reference#reachabilityFence
         */
        private static void reachabilityFence0(Object ref) {
            if (ref != null) {
                synchronized (ref) {
                    // Empty synchronized is ok: https://stackoverflow.com/a/31933260/1151521
                }
            }
        }

        @Override
        public String toString() {
            TraceRecord oldHead = headUpdater.get(this);
            return generateReport(oldHead);
        }

        String getReportAndClearRecords() {
            TraceRecord oldHead = headUpdater.getAndSet(this, null);
            return generateReport(oldHead);
        }

        private String generateReport(TraceRecord oldHead) {
            if (oldHead == null) {
                // Already closed
                return EMPTY_STRING;
            }

            final int dropped = droppedRecordsUpdater.get(this);
            //记录重复数
            int duped = 0;

            int present = oldHead.pos + 1;
            // Guess about 2 kilobytes per stack trace  ，推测每个堆栈跟踪2kb 大小
            StringBuilder buf = new StringBuilder(present * 2048).append(NEWLINE);
            buf.append("Recent access records: ").append(NEWLINE);

            int i = 1;
            //用于判断记录是否重复
            Set<String> seen = new HashSet<String>(present);
            for (; oldHead != TraceRecord.BOTTOM; oldHead = oldHead.next) {
                String s = oldHead.toString();
                if (seen.add(s)) {
                    if (oldHead.next == TraceRecord.BOTTOM) {
                        buf.append("Created at:").append(NEWLINE).append(s);
                    } else {
                        buf.append('#').append(i++).append(':').append(NEWLINE).append(s);
                    }
                } else {
                    duped++;
                }
            }

            if (duped > 0) {
                buf.append(": ")
                        .append(duped)
                        .append(" leak records were discarded because they were duplicates")
                        .append(NEWLINE);
            }

            if (dropped > 0) {
                buf.append(": ")
                   .append(dropped)
                   .append(" leak records were discarded because the leak record count is targeted to ")
                   .append(TARGET_RECORDS)
                   .append(". Use system property ")
                   .append(PROP_TARGET_RECORDS)
                   .append(" to increase the limit.")
                   .append(NEWLINE);
            }

            buf.setLength(buf.length() - NEWLINE.length());
            return buf.toString();
        }
    }

    private static final AtomicReference<String[]> excludedMethods =
            new AtomicReference<String[]>(EmptyArrays.EMPTY_STRINGS);

    public static void addExclusions(Class clz, String ... methodNames) {
        Set<String> nameSet = new HashSet<String>(Arrays.asList(methodNames));
        // Use loop rather than lookup. This avoids knowing the parameters, and doesn't have to handle
        // NoSuchMethodException.
        for (Method method : clz.getDeclaredMethods()) {
            if (nameSet.remove(method.getName()) && nameSet.isEmpty()) {
                break;
            }
        }
        if (!nameSet.isEmpty()) {
            throw new IllegalArgumentException("Can't find '" + nameSet + "' in " + clz.getName());
        }
        String[] oldMethods;
        String[] newMethods;
        do {
            oldMethods = excludedMethods.get();
            newMethods = Arrays.copyOf(oldMethods, oldMethods.length + 2 * methodNames.length);
            for (int i = 0; i < methodNames.length; i++) {
                newMethods[oldMethods.length + i * 2] = clz.getName();
                newMethods[oldMethods.length + i * 2 + 1] = methodNames[i];
            }
        } while (!excludedMethods.compareAndSet(oldMethods, newMethods));
    }


    /**
     * 堆栈信息记录
     * 维护一个单项链表的数据结构，来记录每次的堆栈信息
     */
    private static class TraceRecord extends Throwable {
        private static final long serialVersionUID = 6065153674892850720L;

        private static final TraceRecord BOTTOM = new TraceRecord() {
            private static final long serialVersionUID = 7396077602074694571L;

            // Override fillInStackTrace() so we not populate the backtrace via a native call and so leak the
            // Classloader.
            // 重写fillInStackTrace（），这样我们就不会通过本机调用填充回溯，从而泄露Classloader。
            // See https://github.com/netty/netty/pull/10691
            @Override
            public Throwable fillInStackTrace() {
                return this;
            }
        };

        private final String hintString;
        private final TraceRecord next;
        private final int pos;

        TraceRecord(TraceRecord next, Object hint) {
            // This needs to be generated even if toString() is never called as it may change later on.
            //即使 从未调用过toString()也需要生成它，因为它之后可能会更改
            hintString = hint instanceof ResourceLeakHint ? ((ResourceLeakHint) hint).toHintString() : hint.toString();
            this.next = next;
            this.pos = next.pos + 1;
        }

        TraceRecord(TraceRecord next) {
           hintString = null;
           this.next = next;
           this.pos = next.pos + 1;
        }

        // Used to terminate the stack

        /**
         * 用于终止堆栈
         */
        private TraceRecord() {
            hintString = null;
            next = null;
            pos = -1;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder(2048);
            if (hintString != null) {
                buf.append("\tHint: ").append(hintString).append(NEWLINE);
            }

            // Append the stack trace.
            StackTraceElement[] array = getStackTrace();
            // Skip the first three elements.
            out: for (int i = 3; i < array.length; i++) {
                StackTraceElement element = array[i];
                // Strip the noisy stack trace elements.
                String[] exclusions = excludedMethods.get();
                for (int k = 0; k < exclusions.length; k += 2) {
                    // Suppress a warning about out of bounds access
                    // since the length of excludedMethods is always even, see addExclusions()
                    if (exclusions[k].equals(element.getClassName())
                            && exclusions[k + 1].equals(element.getMethodName())) {
                        continue out;
                    }
                }

                buf.append('\t');
                buf.append(element.toString());
                buf.append(NEWLINE);
            }
            return buf.toString();
        }
    }
}
