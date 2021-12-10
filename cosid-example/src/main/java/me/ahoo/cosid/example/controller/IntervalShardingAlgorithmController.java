/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosid.example.controller;

import me.ahoo.cosid.example.entity.interval.DateLogEntity;
import me.ahoo.cosid.example.entity.interval.LocalDateTimeLogEntity;
import me.ahoo.cosid.example.entity.interval.SnowflakeLogEntity;
import me.ahoo.cosid.example.entity.interval.TimestampLogEntity;
import me.ahoo.cosid.example.repository.IntervalShardingAlgorithmRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * @author ahoo wang
 */
@RestController
@RequestMapping("interval")
public class IntervalShardingAlgorithmController {

    private final IntervalShardingAlgorithmRepository algorithmRepository;

    public IntervalShardingAlgorithmController(IntervalShardingAlgorithmRepository algorithmRepository) {
        this.algorithmRepository = algorithmRepository;
    }

    @PostMapping("/insertDate")
    public DateLogEntity insertDate() {
        DateLogEntity log = new DateLogEntity();
        log.setCreateTime(new Date());
        algorithmRepository.insertDate(log);
        return log;
    }

    @PostMapping("/insertTimestamp")
    public TimestampLogEntity insertTimestamp() {
        TimestampLogEntity log = new TimestampLogEntity();
        log.setCreateTime(System.currentTimeMillis());
        algorithmRepository.insertTimestamp(log);
        return log;
    }
    @PostMapping("/insertDateTime")
    public LocalDateTimeLogEntity insertDateTime() {
        LocalDateTimeLogEntity log = new LocalDateTimeLogEntity();
        log.setCreateTime(LocalDateTime.now());
        algorithmRepository.insertDateTime(log);
        return log;
    }

    @PostMapping("/insertSnowflake")
    public SnowflakeLogEntity insertSnowflake() {
        SnowflakeLogEntity log = new SnowflakeLogEntity();
        algorithmRepository.insertSnowflake(log);
        return log;
    }
}
