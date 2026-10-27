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

package me.ahoo.cosid.example.segment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Segment ids on a database: {@code order} (a segment with a radix 62 codec and a prefix) and {@code bill} (a periodic
 * segment, one sequence per day), both on the JDBC store over the application's {@code DataSource}.
 */
@SpringBootApplication
public class SegmentJdbcApplication {
    /**
     * Starts the application.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SegmentJdbcApplication.class, args);
    }
}
