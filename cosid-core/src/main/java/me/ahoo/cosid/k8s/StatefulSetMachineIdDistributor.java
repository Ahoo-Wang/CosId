package me.ahoo.cosid.k8s;

import com.google.common.base.Preconditions;
import me.ahoo.cosid.MachineIdDistributor;

/**
 * @author ahoo wang
 */
public class StatefulSetMachineIdDistributor implements MachineIdDistributor {
    public static final MachineIdDistributor INSTANCE = new StatefulSetMachineIdDistributor();
    public static final String HOSTNAME_KEY = "HOSTNAME";

    @Override
    public int distribute() {
        String hostName = System.getenv(HOSTNAME_KEY);
        Preconditions.checkNotNull(hostName, "HOSTNAME can not be null.");
        int lastSplitIdx = hostName.lastIndexOf("-");

        String idStr = hostName.substring(lastSplitIdx + 1);
        return Integer.parseInt(idStr);
    }

}
