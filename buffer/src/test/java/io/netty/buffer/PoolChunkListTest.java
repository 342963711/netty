package io.netty.buffer;

import org.junit.jupiter.api.Test;

/**
 * @author likai
 * @date 2023/7/14 13:45
 * @email likai9376@163.com
 * @desc
 */
public class PoolChunkListTest<T> {

    private  PoolChunkList<T> q050;
    private  PoolChunkList<T> q025;
    private  PoolChunkList<T> q000;
    private  PoolChunkList<T> qInit;
    private  PoolChunkList<T> q075;
    private  PoolChunkList<T> q100;

    @Test
    public void testUsage(){
        //minUsage 和 maxUsage 是该保证内存被使用的 使用率标识
        //例如 minUsage=25，maxUsage=75%. 说明该对象中 PoolChunk 的内存使用率 都达到了最低25% 的使用。
        // 也就是 最少已经被使用了25%的内存， 还有最大空闲空间，3M。因为最大使用率是75%，也就是最少也有1M的空闲空间。
        int chunkSize=4*10124*1024;
        //无空闲空间
        q100 = new PoolChunkList<T>(null, null, 100, Integer.MAX_VALUE, chunkSize);
        //最小0M，最大1M的空闲空间，
        q075 = new PoolChunkList<T>(null, q100, 75, 100, chunkSize);
        //最小0M,最大2M的空闲空间
        q050 = new PoolChunkList<T>(null, q075, 50, 100, chunkSize);
        //25-75%, 保证最小1M，最大3M的空闲空间
        q025 = new PoolChunkList<T>(null, q050, 25, 75, chunkSize);
        //50%的使用率，保证最小2M，最大不超过4M的空闲空间
        q000 = new PoolChunkList<T>(null, q025, 1, 50, chunkSize);
        //25% 的使用率 ，以4M为例子，也就是要保证 最小3M的，最大不超过4M的空闲空间
        qInit = new PoolChunkList<T>(null, q000, Integer.MIN_VALUE, 25, chunkSize);
    }
}
