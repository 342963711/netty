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

import static io.netty.util.internal.ObjectUtil.checkPositive;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCounted;

/**
 * Common logic for {@link ReferenceCounted} implementations
 *
 * ReferenceCounted 类公共逻辑实现 抽象类
 */
public abstract class ReferenceCountUpdater<T extends ReferenceCounted> {
    /*
     * Implementation notes:
     *
     * For the updated int field:
     *   Even => "real" refcount is (refCnt >>> 1)  。如果引用计数是偶数，真实的引用计数为>>>1 （左移动1位）
     *   Odd  => "real" refcount is 0。 如果应用技术为奇数，真实的引用计数为0
     *
     * (x & y) appears to be surprisingly expensive relative to (x == y). Thus this class uses
     * a fast-path in some places for most common low values when checking for live (even) refcounts,
     * for example: if (rawCnt == 2 || rawCnt == 4 || (rawCnt & 1) == 0) { ...
     *
     *
     * 相对于（x＝＝y），（x&y）显得出奇地昂贵。因此，当检查活着的（偶数）引用计数时，
     * 该类在一些地方对最常见的低值使用快速路径，例如：如果（rawCnt==2||rawCnt=4||（rawCnt&1）==0）｛。。。
     */

    protected ReferenceCountUpdater() { }

    public static long getUnsafeOffset(Class<? extends ReferenceCounted> clz, String fieldName) {
        try {
            if (PlatformDependent.hasUnsafe()) {
                return PlatformDependent.objectFieldOffset(clz.getDeclaredField(fieldName));
            }
        } catch (Throwable ignore) {
            // fall-back
        }
        return -1;
    }

    protected abstract AtomicIntegerFieldUpdater<T> updater();

    protected abstract long unsafeOffset();

    public final int initialValue() {
        return 2;
    }

    /**
     * 设置引用初始值。默认是2
     * @param instance
     */
    public void setInitialValue(T instance) {
        final long offset = unsafeOffset();
        if (offset == -1) {
            updater().set(instance, initialValue());
        } else {
            PlatformDependent.safeConstructPutInt(instance, offset, initialValue());
        }
    }

    /**
     * 奇数返回0， 偶数/2 就是真实引用次数
     * @param rawCnt
     * @return
     */
    private static int realRefCnt(int rawCnt) {
        return rawCnt != 2 && rawCnt != 4 && (rawCnt & 1) != 0 ? 0 : rawCnt >>> 1;
    }

    /**
     * Like {@link #realRefCnt(int)} but throws if refCnt == 0
     */
    private static int toLiveRealRefCnt(int rawCnt, int decrement) {
        if (rawCnt == 2 || rawCnt == 4 || (rawCnt & 1) == 0) {
            return rawCnt >>> 1;
        }
        // odd rawCnt => already deallocated
        throw new IllegalReferenceCountException(0, -decrement);
    }

    private int nonVolatileRawCnt(T instance) {
        // TODO: Once we compile against later versions of Java we can replace the Unsafe usage here by varhandles.
        final long offset = unsafeOffset();
        return offset != -1 ? PlatformDependent.getInt(instance, offset) : updater().get(instance);
    }

    public final int refCnt(T instance) {
        return realRefCnt(updater().get(instance));
    }

    /**
     * 返回true,代表次对象是被引用的。返回false,表示没有被引用
     * @param instance
     * @return
     */
    public final boolean isLiveNonVolatile(T instance) {
        final long offset = unsafeOffset();
        final int rawCnt = offset != -1 ? PlatformDependent.getInt(instance, offset) : updater().get(instance);

        // The "real" ref count is > 0 if the rawCnt is even.
        return rawCnt == 2 || rawCnt == 4 || rawCnt == 6 || rawCnt == 8 || (rawCnt & 1) == 0;
    }

    /**
     * An unsafe operation that sets the reference count directly
     */
    public final void setRefCnt(T instance, int refCnt) {
        updater().set(instance, refCnt > 0 ? refCnt << 1 : 1); // overflow OK here
    }

    /**
     * Resets the reference count to 1
     * 重置引用计数
     */
    public final void resetRefCnt(T instance) {
        updater().set(instance, initialValue());
    }

    public final T retain(T instance) {
        return retain0(instance, 1, 2);
    }

    public final T retain(T instance, int increment) {
        // all changes to the raw count are 2x the "real" change - overflow is OK
        int rawIncrement = checkPositive(increment, "increment") << 1;
        return retain0(instance, increment, rawIncrement);
    }

    // rawIncrement == increment << 1
    private T retain0(T instance, final int increment, final int rawIncrement) {
        int oldRef = updater().getAndAdd(instance, rawIncrement);
        if (oldRef != 2 && oldRef != 4 && (oldRef & 1) != 0) {
            throw new IllegalReferenceCountException(0, increment);
        }
        // don't pass 0!
        if ((oldRef <= 0 && oldRef + rawIncrement >= 0)
                || (oldRef >= 0 && oldRef + rawIncrement < oldRef)) {
            // overflow case
            updater().getAndAdd(instance, -rawIncrement);
            throw new IllegalReferenceCountException(realRefCnt(oldRef), increment);
        }
        return instance;
    }

    public final boolean release(T instance) {
        int rawCnt = nonVolatileRawCnt(instance);
        return rawCnt == 2 ? tryFinalRelease0(instance, 2) || retryRelease0(instance, 1)
                : nonFinalRelease0(instance, 1, rawCnt, toLiveRealRefCnt(rawCnt, 1));
    }

    public final boolean release(T instance, int decrement) {
        int rawCnt = nonVolatileRawCnt(instance);
        int realCnt = toLiveRealRefCnt(rawCnt, checkPositive(decrement, "decrement"));
        return decrement == realCnt ? tryFinalRelease0(instance, rawCnt) || retryRelease0(instance, decrement)
                : nonFinalRelease0(instance, decrement, rawCnt, realCnt);
    }

    private boolean tryFinalRelease0(T instance, int expectRawCnt) {
        return updater().compareAndSet(instance, expectRawCnt, 1); // any odd number will work
    }

    private boolean nonFinalRelease0(T instance, int decrement, int rawCnt, int realCnt) {
        if (decrement < realCnt
                // all changes to the raw count are 2x the "real" change - overflow is OK
                && updater().compareAndSet(instance, rawCnt, rawCnt - (decrement << 1))) {
            return false;
        }
        return retryRelease0(instance, decrement);
    }

    private boolean retryRelease0(T instance, int decrement) {
        for (;;) {
            int rawCnt = updater().get(instance), realCnt = toLiveRealRefCnt(rawCnt, decrement);
            if (decrement == realCnt) {
                if (tryFinalRelease0(instance, rawCnt)) {
                    return true;
                }
            } else if (decrement < realCnt) {
                // all changes to the raw count are 2x the "real" change
                if (updater().compareAndSet(instance, rawCnt, rawCnt - (decrement << 1))) {
                    return false;
                }
            } else {
                throw new IllegalReferenceCountException(realCnt, -decrement);
            }
            Thread.yield(); // this benefits throughput under high contention
        }
    }
}
