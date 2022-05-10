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

package me.ahoo.cosid.benchmark;

import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributor;
import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.test.MockIdGenerator;
import org.openjdk.jmh.annotations.*;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class CosIdBenchmark extends AbstractBenchmark{

    DataSource dataSource;
    SegmentChainId segmentChainId;

    @Setup
    public void setup() {
        dataSource = DataSourceFactory.createDataSource();
        JdbcIdSegmentInitializer jdbcIdSegmentInitializer = new JdbcIdSegmentInitializer(dataSource);
        JdbcIdSegmentDistributor jdbcIdSegmentDistributor = new JdbcIdSegmentDistributor(MockIdGenerator.INSTANCE.generateAsString(), String.valueOf(step), step, dataSource);
        jdbcIdSegmentInitializer.tryInitIdSegment(jdbcIdSegmentDistributor.getNamespacedName(), 0);
        segmentChainId= new SegmentChainId(jdbcIdSegmentDistributor);
    }

    @Benchmark
    public long generate() {
        return segmentChainId.generate();
    }

    @TearDown
    public void tearDown(){
        if (dataSource instanceof Closeable)   {
            try {
                ((Closeable) dataSource).close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
