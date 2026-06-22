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

package me.ahoo.cosid.machine;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@Slf4j
public final class LocalHostAddressSupplier implements HostAddressSupplier {
    public static final LocalHostAddressSupplier INSTANCE = new LocalHostAddressSupplier();

    private final CheckedSupplier<String> fallbackHostAddressSupplier;
    private final CheckedSupplier<Iterable<HostAddressCandidate>> hostAddressCandidatesSupplier;

    public LocalHostAddressSupplier() {
        this(() -> InetAddress.getLocalHost().getHostAddress(), LocalHostAddressSupplier::scanHostAddressCandidates);
    }

    LocalHostAddressSupplier(CheckedSupplier<String> fallbackHostAddressSupplier,
                             CheckedSupplier<Iterable<HostAddressCandidate>> hostAddressCandidatesSupplier) {
        this.fallbackHostAddressSupplier = fallbackHostAddressSupplier;
        this.hostAddressCandidatesSupplier = hostAddressCandidatesSupplier;
    }

    @SneakyThrows
    @Override
    public String getHostAddress() {
        String hostAddress = fallbackHostAddressSupplier.get();
        try {
            for (HostAddressCandidate candidate : hostAddressCandidatesSupplier.get()) {
                if (candidate.isIpv4()
                    && !candidate.isLoopback()
                    && !candidate.isAnyLocal()
                ) {
                    hostAddress = candidate.getHostAddress();
                }
            }
        } catch (Exception ex) {
            log.error("Cannot get first non-loopback address", ex);
        }
        return hostAddress;
    }

    private static List<HostAddressCandidate> scanHostAddressCandidates() throws Exception {
        List<HostAddressCandidate> candidates = new ArrayList<>();
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface eachNetworkInterface = networkInterfaces.nextElement();
            if (!eachNetworkInterface.isUp() || eachNetworkInterface.isLoopback()) {
                continue;
            }
            Enumeration<InetAddress> inetAddresses = eachNetworkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress inetAddress = inetAddresses.nextElement();
                candidates.add(new HostAddressCandidate(
                    inetAddress.getHostAddress(),
                    inetAddress instanceof Inet4Address,
                    inetAddress.isLoopbackAddress(),
                    inetAddress.isAnyLocalAddress()
                ));
            }
        }
        return candidates;
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    static final class HostAddressCandidate {
        private final String hostAddress;
        private final boolean ipv4;
        private final boolean loopback;
        private final boolean anyLocal;

        HostAddressCandidate(String hostAddress, boolean ipv4, boolean loopback, boolean anyLocal) {
            this.hostAddress = hostAddress;
            this.ipv4 = ipv4;
            this.loopback = loopback;
            this.anyLocal = anyLocal;
        }

        String getHostAddress() {
            return hostAddress;
        }

        boolean isIpv4() {
            return ipv4;
        }

        boolean isLoopback() {
            return loopback;
        }

        boolean isAnyLocal() {
            return anyLocal;
        }
    }
}
