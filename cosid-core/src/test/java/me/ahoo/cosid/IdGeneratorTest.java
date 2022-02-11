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

package me.ahoo.cosid;

import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeIdStateParser;
import me.ahoo.cosid.snowflake.SafeJavaScriptSnowflakeId;
import me.ahoo.cosid.snowflake.SecondSnowflakeId;
import me.ahoo.cosid.snowflake.SecondSnowflakeIdStateParser;
import me.ahoo.cosid.snowflake.SnowflakeId;

import lombok.var;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author ahoo wang
 * Creation time: 2019/11/21 20:56
 */
public class IdGeneratorTest {
    
    @Test
    public void test() {
        var id = 99191438008389632L;
        var snowflakeIdStateParser = new MillisecondSnowflakeIdStateParser(CosId.COSID_EPOCH, MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT, MillisecondSnowflakeId.DEFAULT_MACHINE_BIT,
            MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT);
        var idState = snowflakeIdStateParser.parse(id);
        Assertions.assertNotNull(idState);
        var idStateOfFriendlyId = snowflakeIdStateParser.parse(idState.getFriendlyId());
        Assertions.assertEquals(idState, idStateOfFriendlyId);
    }
    
    @Test
    public void customize_SnowflakeTest() {
        var idGen = new MillisecondSnowflakeId(CosId.COSID_EPOCH, 41, 5, 10, 1);
        var id = idGen.generate();
        
        var snowflakeIdStateParser = MillisecondSnowflakeIdStateParser.of(idGen);
        var idState = snowflakeIdStateParser.parse(id);
        
        var idStateOfFriendlyId = snowflakeIdStateParser.parse(idState.getFriendlyId());
        Assertions.assertEquals(idState, idStateOfFriendlyId);
    }
    
    @Test
    public void secondSnowflakeIdTestEpoch() {
        
        var idGen = new SecondSnowflakeId(LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond(),
            SecondSnowflakeId.DEFAULT_TIMESTAMP_BIT,
            SecondSnowflakeId.DEFAULT_MACHINE_BIT,
            SecondSnowflakeId.DEFAULT_SEQUENCE_BIT, 1023);
        var snowflakeIdStateParser = SecondSnowflakeIdStateParser.of(idGen);
        var id = idGen.generate();
        var id1 = idGen.generate();
        
        Assertions.assertTrue(id1 > id);
        
        var idState = snowflakeIdStateParser.parse(id);
        Assertions.assertEquals(idState.getTimestamp().toLocalDate(), LocalDate.now());
        var idStateOfFriendlyId = snowflakeIdStateParser.parse(idState.getFriendlyId());
        Assertions.assertEquals(idState, idStateOfFriendlyId);
    }
    
    @Test
    public void secondSnowflakeIdTest() {
        
        var idGen = new SecondSnowflakeId(1023);
        var snowflakeIdStateParser = SecondSnowflakeIdStateParser.of(idGen);
        var id = idGen.generate();
        var id1 = idGen.generate();
        
        Assertions.assertTrue(id1 > id);
        
        var idState = snowflakeIdStateParser.parse(id);
        var idStateOfFriendlyId = snowflakeIdStateParser.parse(idState.getFriendlyId());
        Assertions.assertEquals(idState, idStateOfFriendlyId);
    }
    
    
    @Test
    public void safe_ofSecond() {
        var snowflakeId = SafeJavaScriptSnowflakeId.ofSecond(1);
        var snowflakeIdStateParser = SecondSnowflakeIdStateParser.of(snowflakeId);
        var id = snowflakeId.generate();
        var idState = snowflakeIdStateParser.parse(id);
        var idStateOfFriendlyId = snowflakeIdStateParser.parse(idState.getFriendlyId());
        Assertions.assertEquals(idState, idStateOfFriendlyId);
    }
    
    @Test
    public void safe_ofMillisecond() {
        var snowflakeId = SafeJavaScriptSnowflakeId.ofMillisecond(1);
        var snowflakeIdStateParser = MillisecondSnowflakeIdStateParser.of(snowflakeId);
        var id = snowflakeId.generate();
        var idState = snowflakeIdStateParser.parse(id);
        var idStateOfFriendlyId = snowflakeIdStateParser.parse(idState.getFriendlyId());
        Assertions.assertEquals(idState, idStateOfFriendlyId);
    }
    
    static final int CONCURRENT_THREADS = 30;
    static final int THREAD_REQUEST_NUM = 50000;
    
    @Test
    public void concurrent_generate_step_10() {
        final SnowflakeId idGen = new ClockSyncSnowflakeId(new MillisecondSnowflakeId(1), ClockBackwardsSynchronizer.DEFAULT);
        CompletableFuture<List<Long>>[] completableFutures = new CompletableFuture[CONCURRENT_THREADS];
        int threads = 0;
        while (threads < CONCURRENT_THREADS) {
            completableFutures[threads] = CompletableFuture.supplyAsync(() -> {
                List<Long> ids = new ArrayList<>(THREAD_REQUEST_NUM);
                int requestNum = 0;
                while (requestNum < THREAD_REQUEST_NUM) {
                    requestNum++;
                    long id = idGen.generate();
                    ids.add(id);
                }
                return ids;
            });
            
            threads++;
        }
        CompletableFuture.allOf(completableFutures).thenAccept(nil -> {
            List<Long> totalIds = new ArrayList<>();
            for (CompletableFuture<List<Long>> completableFuture : completableFutures) {
                List<Long> ids = completableFuture.join();
                totalIds.addAll(ids);
            }
            totalIds.sort(Long::compareTo);
            Long lastId = null;
            for (Long currentId : totalIds) {
                if (lastId == null) {
                    lastId = currentId;
                    continue;
                }
                
                Assertions.assertTrue(currentId > lastId);
                lastId = currentId;
            }
            
        }).join();
    }
}
