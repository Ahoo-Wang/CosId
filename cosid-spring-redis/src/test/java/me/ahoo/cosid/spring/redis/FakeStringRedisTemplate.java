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

package me.ahoo.cosid.spring.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

final class FakeStringRedisTemplate extends StringRedisTemplate {
    private final Map<String, Long> values = new LinkedHashMap<>();
    private final ValueOperations<String, String> valueOperations = valueOperations();
    private final Queue<Object> scriptResults = new ArrayDeque<>();
    private final List<SetIfAbsentCall> setIfAbsentCalls = new ArrayList<>();
    private final List<SetCall> setCalls = new ArrayList<>();
    private final List<IncrementCall> incrementCalls = new ArrayList<>();
    private final List<ScriptCall<?>> scriptCalls = new ArrayList<>();

    @Override
    public ValueOperations<String, String> opsForValue() {
        return valueOperations;
    }

    @Override
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        scriptCalls.add(new ScriptCall<>(script, keys, args));
        @SuppressWarnings("unchecked")
        T result = (T) scriptResults.poll();
        return result;
    }

    void setValue(String key, long value) {
        values.put(key, value);
    }

    Long getValue(String key) {
        return values.get(key);
    }

    void enqueueScriptResult(Object result) {
        scriptResults.add(result);
    }

    List<SetIfAbsentCall> getSetIfAbsentCalls() {
        return Collections.unmodifiableList(setIfAbsentCalls);
    }

    List<SetCall> getSetCalls() {
        return Collections.unmodifiableList(setCalls);
    }

    List<IncrementCall> getIncrementCalls() {
        return Collections.unmodifiableList(incrementCalls);
    }

    List<ScriptCall<?>> getScriptCalls() {
        return Collections.unmodifiableList(scriptCalls);
    }

    private ValueOperations<String, String> valueOperations() {
        InvocationHandler handler = this::invokeValueOperation;
        Object proxy = Proxy.newProxyInstance(
            ValueOperations.class.getClassLoader(),
            new Class<?>[]{ValueOperations.class},
            handler
        );
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> typedProxy = (ValueOperations<String, String>) proxy;
        return typedProxy;
    }

    private Object invokeValueOperation(Object proxy, Method method, Object[] args) {
        String methodName = method.getName();
        if (method.getDeclaringClass().equals(Object.class)) {
            return invokeObjectMethod(methodName);
        }
        switch (methodName) {
            case "setIfAbsent":
                return setIfAbsent((String) args[0], (String) args[1]);
            case "set":
                set((String) args[0], (String) args[1]);
                return null;
            case "increment":
                return increment((String) args[0], (Long) args[1]);
            case "get":
                Long value = values.get((String) args[0]);
                return value == null ? null : String.valueOf(value);
            case "getOperations":
                return this;
            default:
                throw new UnsupportedOperationException("Unsupported ValueOperations method: " + methodName);
        }
    }

    private Object invokeObjectMethod(String methodName) {
        switch (methodName) {
            case "toString":
                return "FakeValueOperations";
            case "hashCode":
                return System.identityHashCode(this);
            case "equals":
                return false;
            default:
                throw new UnsupportedOperationException("Unsupported Object method: " + methodName);
        }
    }

    private Boolean setIfAbsent(String key, String value) {
        setIfAbsentCalls.add(new SetIfAbsentCall(key, value));
        if (values.containsKey(key)) {
            return false;
        }
        values.put(key, Long.parseLong(value));
        return true;
    }

    private void set(String key, String value) {
        setCalls.add(new SetCall(key, value));
        values.put(key, Long.parseLong(value));
    }

    private Long increment(String key, long delta) {
        incrementCalls.add(new IncrementCall(key, delta));
        long next = values.getOrDefault(key, 0L) + delta;
        values.put(key, next);
        return next;
    }

    static final class SetIfAbsentCall {
        private final String key;
        private final String value;

        private SetIfAbsentCall(String key, String value) {
            this.key = key;
            this.value = value;
        }

        String getKey() {
            return key;
        }

        String getValue() {
            return value;
        }
    }

    static final class SetCall {
        private final String key;
        private final String value;

        private SetCall(String key, String value) {
            this.key = key;
            this.value = value;
        }

        String getKey() {
            return key;
        }

        String getValue() {
            return value;
        }
    }

    static final class IncrementCall {
        private final String key;
        private final long delta;

        private IncrementCall(String key, long delta) {
            this.key = key;
            this.delta = delta;
        }

        String getKey() {
            return key;
        }

        long getDelta() {
            return delta;
        }
    }

    static final class ScriptCall<T> {
        private final RedisScript<T> script;
        private final List<String> keys;
        private final Object[] args;

        private ScriptCall(RedisScript<T> script, List<String> keys, Object[] args) {
            this.script = script;
            this.keys = List.copyOf(keys);
            this.args = args.clone();
        }

        RedisScript<T> getScript() {
            return script;
        }

        List<String> getKeys() {
            return keys;
        }

        Object[] getArgs() {
            return args.clone();
        }
    }
}
