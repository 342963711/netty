package io.netty.util.concurrent;

import io.netty.util.internal.ThreadExecutorMap;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

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
        Runnable runnable = () -> {
            EventExecutor eventExecutor = ThreadExecutorMap.currentExecutor();
            logger.info("eventExecutor:" + eventExecutor.toString());
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
        submit.addListener(future -> {
            String s = (String) future.get();
            logger.info("执行完毕：{}", s);
        });
        Thread.sleep(10000);
    }

    @Test
    public void testPromise01(){
        DefaultPromise<String> defaultPromise = new DefaultPromise<>(new DefaultEventExecutor());
        defaultPromise.addListener(f->{
            logger.info("处理结果:{}",f.get());
        });
        logger.info("处理完成...");
        defaultPromise.setSuccess("处理结果,成功");
    }


    @Test
    public void testPromise02(){
        DefaultEventExecutor defaultEventExecutor = new DefaultEventExecutor(Executors.newSingleThreadExecutor());
        Promise<String> promise = defaultEventExecutor.newPromise();
        promise.addListener(f->{
            String s = (String)f.get();
            logger.info("监听者获取结果,{}",s);
        });
        logger.info("处理完成，设置处理结果");
        promise.setSuccess("成功");
    }

    @Test
    public void testPromise03(){
        DefaultEventExecutor defaultEventExecutor = new DefaultEventExecutor(Executors.newSingleThreadExecutor());
        Future<String> submit = defaultEventExecutor.submit(() -> {
            logger.info("处理任务...");
            return "处理完成,成功";
        });
        submit.addListener(f->{
            logger.info("监听者处理结果:{}",f.get());
        });
    }




    @Test
    public void testPromise04(){
        DefaultPromise<String> default01 = new DefaultPromise<>(new DefaultEventExecutor());
        DefaultPromise<String> default02 = new DefaultPromise<>(new DefaultEventExecutor());
        default01.addListener(f->{
            logger.info("f1处理结果:{}",f.get());
            default02.setSuccess(f.get()+",f1已经处理完毕");
        });
        default02.addListener(f->{
            logger.info("f2的处理结果为:{}",f.get());
        });
        logger.info("处理完成...");
        default01.setSuccess("处理结果,成功");
    }

    @Test
    public void testPromise05(){
        DefaultPromise<String> default01 = new DefaultPromise<>(new DefaultEventExecutor());
        DefaultPromise<String> default02 = new DefaultPromise<>(new DefaultEventExecutor());
        default01.addListener(f->{
            logger.info("f1处理结果:{}",f.get());
            f.addListener(future->{
                default02.setSuccess("default02 执行完毕...");
            });
        });

        default02.addListener(f->{
            logger.info("f2的处理结果为:{}",f.get());
        });
        logger.info("处理完成...");
        default01.setSuccess("处理结果,成功");
    }
}
