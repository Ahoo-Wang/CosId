package me.ahoo.cosid.util;

import java.lang.management.ManagementFactory;

/**
 * @author ahoo wang
 */
public final class Systems {
    private Systems() {
    }


    public static String getCurrentProcessName() {
        return ManagementFactory.getRuntimeMXBean().getName();
    }

    public static long getCurrentProcessId() {
        String processName = getCurrentProcessName();
        String processIdStr = processName.split("@")[0];
        return Long.parseLong(processIdStr);
    }
}
