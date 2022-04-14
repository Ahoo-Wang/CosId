package me.ahoo.cosid;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author : Rocher Kong
 */
class CosIdExceptionTest {

    @Test
    void CosIdExceptionTest(){
        CosIdException cosIdException=new CosIdException("cosId exception");
        Assertions.assertEquals(cosIdException.getMessage(),"cosId exception");
    }

    @Test
    void CosIdExceptionTest2(){
        CosIdException cosIdException=new CosIdException("cosId exception",new Throwable());
        Assertions.assertEquals(cosIdException.getMessage(),"cosId exception");
    }

    @Test
    void CosIdExceptionTest3(){
        Throwable throwable=new Throwable();
        CosIdException cosIdException=new CosIdException(throwable);
        Assertions.assertEquals(cosIdException.getCause(),throwable);
    }
    @Test
    void CosIdExceptionTest4(){
        CosIdException cosIdException=new CosIdException("cosId exception",new Throwable(),true,true);
        Assertions.assertEquals(cosIdException.getMessage(),"cosId exception");
    }
}
