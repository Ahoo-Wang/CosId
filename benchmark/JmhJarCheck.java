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

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Run by the checkJmhJar task of the benchmark projects, on the class path of a JMH jar (java -cp JAR
 * JmhJarCheck.java SERVICE=COUNT...): ServiceLoader must load and instantiate COUNT providers of each SERVICE, the
 * number of the providers that the modules of the JMH class path declare.
 */
public class JmhJarCheck {
    public static void main(String[] args) throws ClassNotFoundException {
        List<String> wrong = new ArrayList<>();
        for (String arg : args) {
            int at = arg.lastIndexOf('=');
            Class<?> service = Class.forName(arg.substring(0, at));
            int expected = Integer.parseInt(arg.substring(at + 1));
            List<String> found = new ArrayList<>();
            for (Object provider : ServiceLoader.load(service)) {
                found.add(provider.getClass().getName());
            }
            System.out.println(service.getName() + ": " + found);
            if (found.size() != expected) {
                wrong.add(service.getName() + " has " + found.size() + " providers, the modules declare " + expected);
            }
        }
        if (!wrong.isEmpty()) {
            wrong.forEach(System.err::println);
            System.exit(1);
        }
    }
}
