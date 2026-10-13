/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosid.zookeeper;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.machine.lease.MachineLeaseStore;
import me.ahoo.cosid.util.CallTimeout;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * {@link MachineLeaseStore} on ZooKeeper, with versioned writes and multi-operation transactions as the compare-and-set.
 *
 * <p>It uses the same nodes as the 3.4 machine id distributor, so instances running either one can share a
 * namespace during an upgrade:</p>
 * <ul>
 *     <li>{@code /cosid/{namespace}/__itc_idx/{instanceId}}: {@code machineId|lastTimestamp} of the lease the instance holds</li>
 *     <li>{@code /cosid/{namespace}/__revert/{machineId}}: {@code machineId|lastTimestamp} of a released machine id</li>
 *     <li>{@code /cosid/{namespace}/__counter}: the number of machine ids ever handed out (a Curator atomic integer)</li>
 *     <li>{@code /cosid/{namespace}/__stable/{instanceId}}: marks a stable instance, whose lease is never taken over</li>
 * </ul>
 *
 * <p>The nodes keep the 3.4 format, {@code machineId|lastTimestamp}, which 3.4 parses strictly. The last timestamp
 * stays the expiry check and the version, as in 3.4; it never decreases and is at least every renewal and watermark,
 * so a claim starts after it. A lease still carries its own watermark and renewal time: renewing leaves the watermark
 * alone and reports the store time it renewed at, while the node is written with a last timestamp that covers both.
 * Do not add fields for them: 3.4 parses the node data strictly, and the last timestamp already covers both, since
 * expiry and takeover are anchored on it.</p>
 *
 * <p>ZooKeeper has no server clock, so {@link #serverTime()} is this host's clock: keep the hosts' clocks in sync. Every
 * call is bounded by the {@link CallTimeout}, since Curator has no per-operation timeout.</p>
 *
 * @author ahoo wang
 * @since 3.4.0
 */
public class ZookeeperMachineLeaseStore implements MachineLeaseStore {
    /**
     * Upper bound of attempts when concurrent instances race for the same nodes.
     */
    static final int MAX_ATTEMPTS = 32;
    
    private final CuratorFramework curatorFramework;
    private final CallTimeout callTimeout;
    
    public ZookeeperMachineLeaseStore(CuratorFramework curatorFramework, CallTimeout callTimeout) {
        this.curatorFramework = Checks.checkNotNull(curatorFramework, "curatorFramework can not be null!");
        this.callTimeout = Checks.checkNotNull(callTimeout, "callTimeout can not be null!");
        Checks.checkArgument(callTimeout.isEnabled(), "callTimeout must be enabled: the lease adapter holds a lock during each call!");
    }
    
    private static String namespacePath(String namespace, String node) {
        return Checks.lenientFormat("/%s/%s/%s", CosId.COSID, namespace, node);
    }
    
    private static String instancePath(String namespace, String instanceId) {
        return ZKPaths.makePath(namespacePath(namespace, "__itc_idx"), instanceId);
    }
    
    private static String revertPath(String namespace) {
        return namespacePath(namespace, "__revert");
    }
    
    private static String stablePath(String namespace, String instanceId) {
        return ZKPaths.makePath(namespacePath(namespace, "__stable"), instanceId);
    }
    
    private static long nextTimestamp(long stored, long target) {
        return Math.max(stored + 1, target);
    }
    
    private static byte[] toBytes(int machineId, long lastTimestamp) {
        return MachineState.of(machineId, lastTimestamp).toStateString().getBytes(StandardCharsets.UTF_8);
    }
    
    private <T> T call(String operation, Callable<T> call) {
        return callTimeout.call(operation, () -> {
            try {
                return call.call();
            } catch (RuntimeException runtimeException) {
                throw runtimeException;
            } catch (Exception exception) {
                throw new CosIdException(exception.getMessage(), exception);
            }
        });
    }
    
    /**
     * A node read with its version, to be changed only if nobody changed it since.
     */
    private static final class Node {
        private final String path;
        private final String name;
        private final MachineState state;
        private final int version;
        
        private Node(String path, String name, MachineState state, int version) {
            this.path = path;
            this.name = name;
            this.state = state;
            this.version = version;
        }
    }
    
    private Optional<Node> read(String path) throws Exception {
        Stat stat = new Stat();
        try {
            byte[] data = curatorFramework.getData().storingStatIn(stat).forPath(path);
            MachineState state = MachineState.of(new String(data, StandardCharsets.UTF_8));
            return Optional.of(new Node(path, ZKPaths.getNodeFromPath(path), state, stat.getVersion()));
        } catch (KeeperException.NoNodeException noNodeException) {
            return Optional.empty();
        }
    }
    
    /**
     * The children of {@code parentPath} with a machine id up to {@code maxMachineId}, smallest machine id first.
     */
    private List<Node> readChildren(String parentPath, int maxMachineId) throws Exception {
        List<String> children;
        try {
            children = curatorFramework.getChildren().forPath(parentPath);
        } catch (KeeperException.NoNodeException noNodeException) {
            return List.of();
        }
        List<Node> nodes = new ArrayList<>(children.size());
        for (String child : children) {
            Optional<Node> node = read(ZKPaths.makePath(parentPath, child));
            if (node.isPresent() && node.get().state.getMachineId() <= maxMachineId) {
                nodes.add(node.get());
            }
        }
        nodes.sort(Comparator.comparingInt(node -> node.state.getMachineId()));
        return nodes;
    }
    
    private void ensureParent(String path) throws Exception {
        try {
            curatorFramework.create().creatingParentsIfNeeded().forPath(ZKPaths.getPathAndNode(path).getPath(), new byte[0]);
        } catch (KeeperException.NodeExistsException ignored) {
            // already created.
        }
    }
    
    /**
     * Record whether the instance is stable, before it takes a machine id: the mark is in place by the time its lease
     * could look expired. An instance no longer configured as stable loses the mark.
     */
    private void markStable(String namespace, InstanceId instanceId) throws Exception {
        String path = stablePath(namespace, instanceId.getInstanceId());
        if (instanceId.isStable()) {
            try {
                curatorFramework.create().creatingParentsIfNeeded().forPath(path, new byte[0]);
            } catch (KeeperException.NodeExistsException ignored) {
                // already marked.
            }
            return;
        }
        try {
            curatorFramework.delete().forPath(path);
        } catch (KeeperException.NoNodeException ignored) {
            // not marked.
        }
    }
    
    /**
     * Atomically delete {@code from} (only at the version that was read) and create {@code toPath}.
     *
     * @return {@code false} if another client changed or removed {@code from}, or created {@code toPath}, first
     */
    private boolean move(Node from, String toPath, byte[] data) throws Exception {
        ensureParent(toPath);
        try {
            curatorFramework.transaction().forOperations(
                curatorFramework.transactionOp().delete().withVersion(from.version).forPath(from.path),
                curatorFramework.transactionOp().create().forPath(toPath, data)
            );
            return true;
        } catch (KeeperException.NoNodeException | KeeperException.BadVersionException | KeeperException.NodeExistsException conflictException) {
            return false;
        }
    }
    
    private boolean setData(Node node, byte[] data) throws Exception {
        try {
            curatorFramework.setData().withVersion(node.version).forPath(node.path, data);
            return true;
        } catch (KeeperException.NoNodeException | KeeperException.BadVersionException conflictException) {
            return false;
        }
    }
    
    /**
     * The lease after a claim: the last timestamp just written is the watermark (it is at least the previous one and
     * {@code now}) and the version.
     */
    private static Lease claimed(String namespace, InstanceId instanceId, int machineId, long lastTimestamp, long now) {
        return new Lease(namespace, instanceId, machineId, lastTimestamp, now, lastTimestamp);
    }
    
    @Override
    public Optional<Lease> claimSelf(String namespace, InstanceId instanceId, long now) {
        return call("claimSelf", () -> {
            markStable(namespace, instanceId);
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                Optional<Node> self = read(instancePath(namespace, instanceId.getInstanceId()));
                if (self.isEmpty()) {
                    return Optional.empty();
                }
                int machineId = self.get().state.getMachineId();
                long timestamp = nextTimestamp(self.get().state.getLastTimeStamp(), now);
                if (setData(self.get(), toBytes(machineId, timestamp))) {
                    return Optional.of(claimed(namespace, instanceId, machineId, timestamp, now));
                }
            }
            return Optional.empty();
        });
    }
    
    @Override
    public Optional<Lease> claimReleased(String namespace, InstanceId instanceId, int maxMachineId, long now) {
        return call("claimReleased", () -> {
            markStable(namespace, instanceId);
            String toPath = instancePath(namespace, instanceId.getInstanceId());
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                List<Node> released = readChildren(revertPath(namespace), maxMachineId);
                if (released.isEmpty()) {
                    return Optional.empty();
                }
                for (Node node : released) {
                    int machineId = node.state.getMachineId();
                    long timestamp = nextTimestamp(node.state.getLastTimeStamp(), now);
                    if (move(node, toPath, toBytes(machineId, timestamp))) {
                        return Optional.of(claimed(namespace, instanceId, machineId, timestamp, now));
                    }
                }
            }
            return Optional.empty();
        });
    }
    
    @Override
    public Optional<Lease> claimNew(String namespace, InstanceId instanceId, int maxMachineId, long now) {
        return call("claimNew", () -> {
            markStable(namespace, instanceId);
            String counterPath = namespacePath(namespace, "__counter");
            String toPath = instancePath(namespace, instanceId.getInstanceId());
            ensureParent(toPath);
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                Stat stat = new Stat();
                byte[] counter;
                try {
                    counter = curatorFramework.getData().storingStatIn(stat).forPath(counterPath);
                } catch (KeeperException.NoNodeException noNodeException) {
                    try {
                        curatorFramework.create().creatingParentsIfNeeded().forPath(counterPath, ByteBuffer.allocate(Integer.BYTES).putInt(0).array());
                    } catch (KeeperException.NodeExistsException ignored) {
                        // created concurrently.
                    }
                    continue;
                }
                // The count of machine ids handed out is the next machine id; Curator's atomic integer encoding.
                int machineId = counter == null || counter.length < Integer.BYTES ? 0 : ByteBuffer.wrap(counter).getInt();
                if (machineId > maxMachineId) {
                    return Optional.empty();
                }
                try {
                    curatorFramework.transaction().forOperations(
                        curatorFramework.transactionOp().setData().withVersion(stat.getVersion())
                            .forPath(counterPath, ByteBuffer.allocate(Integer.BYTES).putInt(machineId + 1).array()),
                        curatorFramework.transactionOp().create().forPath(toPath, toBytes(machineId, now))
                    );
                    return Optional.of(claimed(namespace, instanceId, machineId, now, now));
                } catch (KeeperException.BadVersionException | KeeperException.NodeExistsException conflictException) {
                    // Another instance took the machine id first.
                }
            }
            return Optional.empty();
        });
    }
    
    @Override
    public Optional<Lease> claimExpired(String namespace, InstanceId instanceId, int maxMachineId, long expiredBefore, long now) {
        return call("claimExpired", () -> {
            markStable(namespace, instanceId);
            String toPath = instancePath(namespace, instanceId.getInstanceId());
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                List<Node> expired = new ArrayList<>();
                for (Node node : readChildren(namespacePath(namespace, "__itc_idx"), maxMachineId)) {
                    if (node.state.getLastTimeStamp() <= expiredBefore
                        && curatorFramework.checkExists().forPath(stablePath(namespace, node.name)) == null) {
                        expired.add(node);
                    }
                }
                if (expired.isEmpty()) {
                    return Optional.empty();
                }
                for (Node node : expired) {
                    int machineId = node.state.getMachineId();
                    long timestamp = nextTimestamp(node.state.getLastTimeStamp(), now);
                    // The versioned delete fails if the owner renewed after the read, so a live lease is never taken over.
                    if (move(node, toPath, toBytes(machineId, timestamp))) {
                        return Optional.of(claimed(namespace, instanceId, machineId, timestamp, now));
                    }
                }
            }
            return Optional.empty();
        });
    }
    
    /**
     * The instance node of the lease, if it still holds the same machine id and version.
     */
    private Optional<Node> current(Lease lease) throws Exception {
        return read(instancePath(lease.getNamespace(), lease.getInstanceId().getInstanceId()))
            .filter(node -> node.state.getMachineId() == lease.getMachineId() && node.state.getLastTimeStamp() == lease.getVersion());
    }
    
    @Override
    public Optional<Lease> renew(Lease lease, long now) {
        return call("renew", () -> {
            Optional<Node> node = current(lease);
            if (node.isEmpty()) {
                return Optional.empty();
            }
            long timestamp = nextTimestamp(lease.getVersion(), Math.max(now, lease.getWatermark()));
            if (!setData(node.get(), toBytes(lease.getMachineId(), timestamp))) {
                return Optional.empty();
            }
            return Optional.of(new Lease(lease.getNamespace(), lease.getInstanceId(), lease.getMachineId(), lease.getWatermark(), now, timestamp));
        });
    }
    
    @Override
    public boolean release(Lease lease, long watermark) {
        return call("release", () -> {
            Optional<Node> node = current(lease);
            if (node.isEmpty()) {
                return false;
            }
            byte[] data = toBytes(lease.getMachineId(), nextTimestamp(lease.getVersion(), Math.max(watermark, lease.getWatermark())));
            if (lease.isStable()) {
                return setData(node.get(), data);
            }
            String toPath = ZKPaths.makePath(revertPath(lease.getNamespace()), String.valueOf(lease.getMachineId()));
            if (!move(node.get(), toPath, data)) {
                return false;
            }
            markStable(lease.getNamespace(), lease.getInstanceId());
            return true;
        });
    }
}
