package me.ahoo.cosid;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

/**
 * @author : Rocher Kong
 */
class CosIdTest {
    @Test
    void testStatic(){
        Assertions.assertEquals(CosId.COSID_EPOCH_DATE, LocalDateTime.of(2019, 12, 24, 16, 0));
    }
}
