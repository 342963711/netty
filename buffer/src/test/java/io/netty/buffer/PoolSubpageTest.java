package io.netty.buffer;

import org.junit.jupiter.api.Test;

/**
 * @author likai
 * @date 2023/6/30 14:54
 * @email likai9376@163.com
 * @desc
 */
public class PoolSubpageTest {


    @Test
    public void testPoolSubpage(){
        PoolSubpage head = new PoolSubpage();
        head.prev = head;
        head.next = head;
        PoolSubpage test = new PoolSubpage(head,null,13,12,1<<13,10);

        int pageSize = test.pageSize();
        int maxNumElements = test.maxNumElements();
        int numAvailable = test.numAvailable();
        System.out.println("pageSize:"+pageSize);
        System.out.println("maxNumElements:"+maxNumElements);
        System.out.println("numAvailable:"+numAvailable);
    }
}
