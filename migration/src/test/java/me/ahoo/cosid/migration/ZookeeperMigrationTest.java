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

package me.ahoo.cosid.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.junit.jupiter.api.Test;

/** 09 §5 on ZOOKEEPER: 4.x and 5.0 in the same store. */
class ZookeeperMigrationTest extends MigrationScenarioTest {
    @Override
    TestBackend sourceBackend() {
        return TestBackend.ZOOKEEPER;
    }

    // #1469: plan only reads; ZooKeeper tells its time only by creating a node, so plan estimates W without it.
    @Test
    void planCreatesNoNodeToReadTheClock() throws Exception {
        writer.segment(namespace, "order", 0, 10, 5);
        writer.machines(namespace, 1, 10);
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(source.url().substring("zookeeper://".length()), new RetryOneTime(100))) {
            client.start();
            client.blockUntilConnected();
            int children = client.checkExists().forPath("/cosid").getCversion();
            Plan plan = plan(migration());
            assertThat("no node created or deleted under /cosid", client.checkExists().forPath("/cosid").getCversion(), is(children));
            assertThat(plan.warnings(), hasItem(containsString("a store tells its clock only by writing (ZooKeeper)")));
        }
    }
}
