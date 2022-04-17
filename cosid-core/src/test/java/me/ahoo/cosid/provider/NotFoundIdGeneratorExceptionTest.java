package me.ahoo.cosid.provider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author : Rocher Kong
 */
class NotFoundIdGeneratorExceptionTest {

    @Test
    void getGeneratorName() {
        NotFoundIdGeneratorException notFoundIdGeneratorException=new NotFoundIdGeneratorException("not found");
        Assertions.assertEquals(notFoundIdGeneratorException.getGeneratorName(),"not found");
    }
}
