/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosid.redis.state;

import me.ahoo.cosid.redis.RedisIdFactory;
import me.ahoo.cosid.segment.SegmentId;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class SegmentChainId100State {
    public  SegmentId segmentId;

    @Setup
    public void setup() {
        segmentId = RedisIdFactory.INSTANCE.createSegmentChainId(100);
    }

    @TearDown
    public void tearDown() {
        RedisIdFactory.INSTANCE.close();
    }
}
