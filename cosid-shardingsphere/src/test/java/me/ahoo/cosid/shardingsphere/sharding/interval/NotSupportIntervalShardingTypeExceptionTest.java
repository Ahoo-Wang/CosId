package me.ahoo.cosid.shardingsphere.sharding.interval;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * @author : Rocher Kong
 */
class NotSupportIntervalShardingTypeExceptionTest {

    @Test
    void NotSupportIntervalShardingTypeExceptionTest(){
        NotSupportIntervalShardingTypeException NotSupportIntervalShardingTypeException=new NotSupportIntervalShardingTypeException("not support interval exception");
        Assertions.assertEquals(NotSupportIntervalShardingTypeException.getMessage(),"not support interval exception");
    }

    @Test
    void NotSupportIntervalShardingTypeExceptionTest2(){
        NotSupportIntervalShardingTypeException NotSupportIntervalShardingTypeException=new NotSupportIntervalShardingTypeException("not support interval exception",new Throwable());
        Assertions.assertEquals(NotSupportIntervalShardingTypeException.getMessage(),"not support interval exception");
    }

    @Test
    void NotSupportIntervalShardingTypeExceptionTest3(){
        Throwable throwable=new Throwable();
        NotSupportIntervalShardingTypeException NotSupportIntervalShardingTypeException=new NotSupportIntervalShardingTypeException(throwable);
        Assertions.assertEquals(NotSupportIntervalShardingTypeException.getCause(),throwable);
    }
    @Test
    void NotSupportIntervalShardingTypeExceptionTest4(){
        NotSupportIntervalShardingTypeException NotSupportIntervalShardingTypeException=new NotSupportIntervalShardingTypeException("not support interval exception",new Throwable(),true,true);
        Assertions.assertEquals(NotSupportIntervalShardingTypeException.getMessage(),"not support interval exception");
    }

}
