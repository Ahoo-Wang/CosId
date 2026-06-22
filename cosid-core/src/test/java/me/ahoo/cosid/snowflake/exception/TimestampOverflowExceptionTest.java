package me.ahoo.cosid.snowflake.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author : Rocher Kong
 */
class TimestampOverflowExceptionTest {
    private final TimestampOverflowException timestampOverflowException = new TimestampOverflowException(100, 200, 150);

    @Test
    void getEpoch() {
        assertEquals(100, timestampOverflowException.getEpoch());
    }

    @Test
    void getDiffTimestamp() {
        assertEquals(200, timestampOverflowException.getDiffTimestamp());
    }

    @Test
    void getMaxTimestamp() {
        assertEquals(150, timestampOverflowException.getMaxTimestamp());
    }

    @Test
    void messageIncludesOverflowContext() {
        assertEquals("epoch:[100] - diffTimestamp:[200] can't be greater than maxTimestamp:[150]",
            timestampOverflowException.getMessage());
    }
}
