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
import java.util.Enumeration;

@Slf4j
public final class LocalHostAddressSupplier implements HostAddressSupplier {
    public static final LocalHostAddressSupplier INSTANCE = new LocalHostAddressSupplier();
    
    @SneakyThrows
    @Override
    public String getHostAddress() {
        String hostAddress = InetAddress.getLocalHost().getHostAddress();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface eachNetworkInterface = networkInterfaces.nextElement();
                if (!eachNetworkInterface.isUp() || eachNetworkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = eachNetworkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress instanceof Inet4Address
                        && !inetAddress.isLoopbackAddress()
                        && !inetAddress.isAnyLocalAddress()
                    ) {
                        hostAddress = inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Cannot get first non-loopback address", ex);
        }
        return hostAddress;
    }
}
