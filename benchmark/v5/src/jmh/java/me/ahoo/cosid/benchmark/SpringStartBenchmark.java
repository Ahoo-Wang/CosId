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

import me.ahoo.cosid.IdGenerator;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.concurrent.TimeUnit;

/**
 * P11: a Spring Boot context started, the first id generated, the context closed; the 5.x starter: \`cosid.machine.static.slot=1\`, a \`default\` snowflake generator; the \`IdGenerator\` bean.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SpringStartBenchmark {
    @Benchmark
    @Threads(1)
    public long startToFirstId_t1() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .properties(
            "cosid.machine.static.slot=1",
            "cosid.generators.default.strategy=snowflake")
            .run()) {
            return context.getBean(IdGenerator.class).generate();
        }
    }

    /**
     * The application: auto-configuration only. The JMH jar keeps one {@code AutoConfiguration.imports} of all the jars
     * it merges (Spring Boot's), so the starter's auto-configurations, those of its imports file, are imported here.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ImportAutoConfiguration({
        me.ahoo.cosid.spring.autoconfigure.CosIdAutoConfiguration.class,
        me.ahoo.cosid.spring.autoconfigure.CosIdMetricsConfiguration.class,
        me.ahoo.cosid.spring.autoconfigure.CosIdActuatorConfiguration.class
    })
    public static class App {
    }
}
