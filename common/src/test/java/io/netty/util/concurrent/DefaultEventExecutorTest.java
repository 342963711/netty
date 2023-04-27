package io.netty.util.concurrent;

import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.ThreadExecutorMap;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.Test;

import java.util.Timer;
import java.util.concurrent.TimeUnit;

/**
 * @author likai
 * @date 2023/4/26 18:27
 * @email likai9376@163.com
 * @desc
 */
public class DefaultEventExecutorTest {

    InternalLogger logger = InternalLoggerFactory.getInstance(DefaultEventExecutorTest.class);


    @Test
    public void testDefaultEventExecutorInit() throws InterruptedException {
        DefaultEventExecutor defaultEventExecutor = new DefaultEventExecutor();
        Runnable runnable  = ()->{
            EventExecutor eventExecutor = ThreadExecutorMap.currentExecutor();
            logger.info("eventExecutor:"+eventExecutor.toString());
            logger.info("hello");
        };
        defaultEventExecutor.submit(runnable);

        Thread.sleep(20000L);
    }
}
