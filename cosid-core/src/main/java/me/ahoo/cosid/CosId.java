package me.ahoo.cosid;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * @author ahoo wang
 */
public final class CosId {
    public static final String COSID = "cosid";
    public static final String COSID_PREFIX = COSID + ".";
    public static final LocalDateTime PIGEON_EPOCH_DATE;
    /**
     *  1577203200000
     */
    public static final long COSID_EPOCH;
    /**
     *  1577203200
     */
    public static final long COSID_EPOCH_SECOND;

    static {
        PIGEON_EPOCH_DATE = LocalDateTime.of(2019, 12, 24, 16, 0);
        COSID_EPOCH = PIGEON_EPOCH_DATE.toInstant(ZoneOffset.UTC).toEpochMilli();
        COSID_EPOCH_SECOND = COSID_EPOCH / 1000;
    }
}
