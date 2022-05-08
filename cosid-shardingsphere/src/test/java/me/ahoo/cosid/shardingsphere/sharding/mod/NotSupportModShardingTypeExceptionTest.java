package me.ahoo.cosid.shardingsphere.sharding.mod;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author : Rocher Kong
 */
class NotSupportModShardingTypeExceptionTest {
    @Test
    void NotSupportModShardingTypeExceptionTest(){
        NotSupportModShardingTypeException NotSupportModShardingTypeException=new NotSupportModShardingTypeException("not support interval exception");
        Assertions.assertEquals(NotSupportModShardingTypeException.getMessage(),"not support interval exception");
    }

    @Test
    void NotSupportModShardingTypeExceptionTest2(){
        NotSupportModShardingTypeException NotSupportModShardingTypeException=new NotSupportModShardingTypeException("not support interval exception",new Throwable());
        Assertions.assertEquals(NotSupportModShardingTypeException.getMessage(),"not support interval exception");
    }

    @Test
    void NotSupportModShardingTypeExceptionTest3(){
        Throwable throwable=new Throwable();
        NotSupportModShardingTypeException NotSupportModShardingTypeException=new NotSupportModShardingTypeException(throwable);
        Assertions.assertEquals(NotSupportModShardingTypeException.getCause(),throwable);
    }
    @Test
    void NotSupportModShardingTypeExceptionTest4(){
        NotSupportModShardingTypeException NotSupportModShardingTypeException=new NotSupportModShardingTypeException("not support interval exception",new Throwable(),true,true);
        Assertions.assertEquals(NotSupportModShardingTypeException.getMessage(),"not support interval exception");
    }

}
