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

package me.ahoo.cosid.store.tck;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ContractsRanTest {
    @TempDir
    Path dir;

    private void report(Class<?> testClass, Class<?> contract, String skippedCase, String failedCase) throws IOException {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite name=\"" + testClass.getName() + "\">\n");
        for (Method method : contract.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
                continue;
            }
            xml.append("  <testcase name=\"").append(method.getName()).append("()\" classname=\"").append(testClass.getName()).append("\">");
            if (method.getName().equals(skippedCase)) {
                xml.append("<skipped/>");
            }
            if (method.getName().equals(failedCase)) {
                xml.append("<failure message=\"boom\"/>");
            }
            xml.append("</testcase>\n");
        }
        xml.append("</testsuite>\n");
        Files.writeString(dir.resolve("TEST-" + testClass.getName() + ".xml"), xml.toString());
    }

    private List<String> check(Class<?>... contracts) throws IOException {
        return ContractsRan.check(dir, List.of(contracts), getClass().getClassLoader());
    }

    @Test
    void passesWhenEveryCaseRan() throws IOException {
        report(InMemoryRangeStoreTest.class, RangeStoreContract.class, "sealedKeyRejectsReserveAndKeepsItsValue", null);
        report(InMemorySlotLeaseStoreTest.class, SlotLeaseStoreContract.class, null, null);
        assertThat("an optional capability may be skipped", check(RangeStoreContract.class, SlotLeaseStoreContract.class), is(empty()));
    }

    @Test
    void failsWithoutAnImplementation() throws IOException {
        report(InMemoryRangeStoreTest.class, RangeStoreContract.class, null, null);
        assertThat(check(RangeStoreContract.class, SlotLeaseStoreContract.class), contains("SlotLeaseStoreContract: no test class extending it reported"));
        assertThat(ContractsRan.check(dir.resolve("missing"), List.of(RangeStoreContract.class), getClass().getClassLoader()), hasSize(1));
    }

    @Test
    void failsOnASkippedOrFailedCase() throws IOException {
        report(InMemorySlotLeaseStoreTest.class, SlotLeaseStoreContract.class, "renewIsIdempotent", "acquireIsIdempotentForTheSameOwner");
        Files.writeString(dir.resolve("TEST-unknown.xml"), "<testsuite><testcase name=\"x()\" classname=\"no.such.Test\"/></testsuite>");
        List<String> problems = check(SlotLeaseStoreContract.class);
        assertThat(problems, hasSize(2));
        assertThat(problems, hasItem("SlotLeaseStoreContract.renewIsIdempotent: not reported as passed"));
        assertThat(problems, hasItem("SlotLeaseStoreContract.acquireIsIdempotentForTheSameOwner: not reported as passed"));
    }

    @Test
    void aRetryThatPassedCounts() throws IOException {
        report(InMemorySlotLeaseStoreTest.class, SlotLeaseStoreContract.class, null, "renewIsIdempotent");
        report(InMemorySlotLeaseStoreManualClockTest.class, SlotLeaseStoreContract.class, null, null);
        assertThat("another implementation ran it", check(SlotLeaseStoreContract.class), is(empty()));
    }
}
