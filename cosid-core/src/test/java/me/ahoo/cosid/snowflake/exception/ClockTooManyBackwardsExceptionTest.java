package me.ahoo.cosid.snowflake.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author : Rocher Kong
 */
class ClockTooManyBackwardsExceptionTest {
    private final ClockTooManyBackwardsException clockTooManyBackwardsException = new ClockTooManyBackwardsException(200, 100, 50);

    @Test
    void getLastTimestamp() {
        assertEquals(200, clockTooManyBackwardsException.getLastTimestamp());
    }

    @Test
    void getCurrentTimestamp() {
        assertEquals(100, clockTooManyBackwardsException.getCurrentTimestamp());
    }

    @Test
    void getBrokenThreshold() {
        assertEquals(50, clockTooManyBackwardsException.getBrokenThreshold());
    }

    @Test
    void messageIncludesBackwardsContext() {
        assertEquals("Clock moved backwards too many.  brokenThreshold:[50] | lastTimestamp:[200] | currentTimestamp:[100]",
            clockTooManyBackwardsException.getMessage());
    }
}
