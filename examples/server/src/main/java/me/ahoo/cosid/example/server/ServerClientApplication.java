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

package me.ahoo.cosid.example.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * An application on cosid-server: its machine slot and its segments come from the server's HTTP API through the
 * {@code remote} store, so it needs no database driver and no store credentials, only a token.
 */
@SpringBootApplication
public class ServerClientApplication {
    /**
     * Starts the application.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ServerClientApplication.class, args);
    }
}
