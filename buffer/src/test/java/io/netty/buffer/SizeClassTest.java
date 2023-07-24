package io.netty.buffer;

import org.junit.jupiter.api.Test;

/**
 * @author likai
 * @date 2023/6/26 11:46
 * @email likai9376@163.com
 * @desc
 */
public class SizeClassTest {

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
}
