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

package me.ahoo.cosid.proxy.api;

/**
 * A claim of a machine id lease: the arguments of the {@code MachineLeaseStore} claims.
 *
 * @param instanceId    the claiming instance
 * @param stable        whether the instance is stable
 * @param now           the store time from {@code GET machines/v2/time}
 * @param maxMachineId  the largest machine id allowed (not used by a self claim)
 * @param expiredBefore leases renewed at or before this time are expired (only for an expired claim)
 */
public record ClaimRequest(String instanceId, boolean stable, long now, int maxMachineId, long expiredBefore) {
}
