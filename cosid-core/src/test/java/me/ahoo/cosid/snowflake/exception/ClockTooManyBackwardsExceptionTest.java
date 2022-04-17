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
class ClockTooManyBackwardsExceptionTest {
    ClockTooManyBackwardsException clockTooManyBackwardsException;

    @BeforeEach
    void setUp() {
        clockTooManyBackwardsException = new ClockTooManyBackwardsException(
                LocalDateTime.now().minusSeconds(10).toEpochSecond(ZoneOffset.UTC)
                , LocalDateTime.now().minusSeconds(10).toEpochSecond(ZoneOffset.UTC)
                , 1
        );
    }

    @Test
    void getLastTimestamp() {
        Assertions.assertNotNull(clockTooManyBackwardsException.getLastTimestamp());
    }

    @Test
    void getCurrentTimestamp() {
        Assertions.assertNotNull(clockTooManyBackwardsException.getCurrentTimestamp());
    }

    @Test
    void getBrokenThreshold() {
        Assertions.assertEquals(clockTooManyBackwardsException.getBrokenThreshold(),1);
    }
}
