package me.ahoo.cosid.snowflake.exception;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author : Rocher Kong
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimestampOverflowExceptionTest {
    TimestampOverflowException timestampOverflowException;

    @BeforeEach
    void setUp(){
        timestampOverflowException=new TimestampOverflowException(   LocalDateTime.now().minusSeconds(10).toEpochSecond(ZoneOffset.UTC)
                , LocalDateTime.now().minusSeconds(10).toEpochSecond(ZoneOffset.UTC)
                , 1);
    }

    @Test
    void getEpoch() {
        Assertions.assertNotNull(timestampOverflowException.getEpoch());
    }

    @Test
    void getDiffTimestamp() {
        Assertions.assertNotNull(timestampOverflowException.getDiffTimestamp());
    }

    @Test
    void getMaxTimestamp() {
        Assertions.assertNotNull(timestampOverflowException.getMaxTimestamp());
    }
}
