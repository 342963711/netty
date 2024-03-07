package io.netty.buffer;

import org.assertj.core.api.Assert;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static io.netty.buffer.PoolThreadCache.log2;
import static io.netty.buffer.SizeClasses.LOG2_QUANTUM;
import static io.netty.buffer.SizeClasses.LOG2_SIZE_CLASS_GROUP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author likai
 * @date 2023/6/26 11:46
 * @email likai9376@163.com
 * @desc
 */
public class SizeClassTest {


    /**
     * 对数的计算公式校验
     * 主要用于求解 根据给出的 chunkSize ,计算出 分组的 移位
     */
    @Test
    public void testGroup(){
        for(int chunkSize=64;chunkSize<Integer.MAX_VALUE&&chunkSize>0;chunkSize*=2) {
            int group01 = log2(chunkSize) - LOG2_QUANTUM - LOG2_SIZE_CLASS_GROUP + 1;
            //todo lk,这里理解不了总大小/每个条目的大小/每组条目数 = 分组数量
            // 那么如何计算每个条目的大小。
            int group02 = log2(chunkSize / 16 / 4) + 1;
            assertTrue(group01==group02);
        }
    }

    /**
     * 测试如果计算每个条目的值
     */
    @Test
    public void testNewSizeClass(){
        short[] shorts = SizeClasses.newSizeClass(0, 4, 4, 0, 13);
        System.out.println(Arrays.toString(shorts));
    }

    @Test
    public void testNewSizeClasses(){
        //测试 64 大小的，如何生成 SizeClasses
        SizeClasses sizeClasses = new SizeClasses(8192, 13, 128, 0){};
    }

    @Test
    public void testSizeIdx2sizeCompute(){
        SizeClasses sizeClasses = new SizeClasses(8192,13,4*1024*1024,0) {
        };
        int i = sizeClasses.sizeIdx2size(7);
        int i1 = sizeClasses.sizeIdx2sizeCompute(7);
        assertTrue(i==i1);
    }

    @Test
    public void sizeClass(){
        SizeClasses sizeClasses = new SizeClasses(8192,13,4*1024*1024,0) {
        };

        int i = sizeClasses.size2SizeIdx(1024);
//        System.out.println("==================");
//        System.out.println(i);
//        System.out.println("------------------------------");
//        System.out.println("nSizes:"+sizeClasses.nSizes);
//        System.out.println("nPSizes:"+sizeClasses.nPSizes);
//        System.out.println("nSubpages:"+sizeClasses.nSubpages);
//
//        System.out.println("smallMaxSizeIdx:"+sizeClasses.smallMaxSizeIdx);

        int i1 = sizeClasses.size2SizeIdx(28672);
        System.out.println(i1);

    }







    /**
     * 理解计算 对数近似值
     */
    @Test
    public void testLog(){
//        for(int i=1;i<Integer.MAX_VALUE;i++) {
//            assertTrue(Integer.numberOfTrailingZeros(i)==31 - Integer.numberOfLeadingZeros(i),"不相等:"+i);
//        }

//        int i = Integer.numberOfTrailingZeros(3);
//        int b = 31 - Integer.numberOfLeadingZeros(3);
    }
}
