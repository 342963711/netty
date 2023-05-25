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
        defaultEventExecutor.execute(runnable);

        Thread.sleep(20000L);
    }

    @Test
    public void testDefaultEventFuture() throws InterruptedException {
        DefaultEventExecutor defaultEventExecutor = new DefaultEventExecutor();
        Future<String> submit = defaultEventExecutor.submit(() -> {
            logger.info("runnable start");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            logger.info("runnable end");
            return "hello";
        });
        submit.addListener(future->{
            Object s = future.get();
            logger.info("执行完毕：{}",s);
        });
        Thread.sleep(10000);
    }
}
