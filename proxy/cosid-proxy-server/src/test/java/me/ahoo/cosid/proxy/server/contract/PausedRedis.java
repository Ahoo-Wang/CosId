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

package me.ahoo.cosid.proxy.server.contract;

import me.ahoo.cosid.test.container.RedisLauncher;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Pauses the writes of the test Redis, as {@code CLIENT PAUSE ... WRITE} does in a failover, so that every store
 * call of the proxy hangs while reads such as the health check's {@code PING} still answer.
 */
final class PausedRedis implements AutoCloseable {
    private final RedisClient client = RedisClient.create(RedisLauncher.getUri());
    private final StatefulRedisConnection<String, String> connection = client.connect();
    
    PausedRedis(Duration duration) {
        clientCommand("PAUSE", String.valueOf(duration.toMillis()), "WRITE");
    }
    
    private void clientCommand(String... args) {
        CommandArgs<String, String> commandArgs = new CommandArgs<>(StringCodec.UTF8);
        for (String arg : args) {
            commandArgs.add(arg);
        }
        connection.sync().dispatch(CommandType.CLIENT, new StatusOutput<>(StringCodec.UTF8), commandArgs);
    }
    
    /**
     * Sends segment requests that each wait on a Redis write, without waiting for their answers.
     *
     * @return the answers' status codes, as they arrive
     */
    static Flux<Integer> sendSegmentRequests(int port, int count) {
        WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader("Authorization", "Bearer " + ProxyEndToEnd.TOKEN)
            .build();
        return Flux.range(0, count)
            .flatMap(i -> webClient.patch()
                .uri("/segments/paused/order?step=1")
                .exchangeToMono(response -> Mono.just(response.statusCode().value())), count)
            .cache();
    }
    
    @Override
    public void close() {
        try {
            clientCommand("UNPAUSE");
        } finally {
            connection.close();
            client.shutdown();
        }
    }
}
