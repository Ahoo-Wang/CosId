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

package me.ahoo.cosid.internal;

import org.jspecify.annotations.Nullable;

/**
 * Argument, state and null checks, and the small string helpers they need, without Guava. Messages use {@code %s}
 * placeholders like Guava's {@code Preconditions}.
 *
 * <p>Internal to CosId: not part of the public API.</p>
 *
 * @author ahoo wang
 * @since 4.0.0
 */
public final class Checks {
    
    private Checks() {
    }
    
    /**
     * Checks an argument.
     *
     * @throws IllegalArgumentException with the formatted message if {@code expression} is false
     */
    public static void checkArgument(boolean expression, @Nullable String template, @Nullable Object... args) {
        if (!expression) {
            throw new IllegalArgumentException(lenientFormat(template, args));
        }
    }
    
    /**
     * Checks an argument.
     *
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static void checkArgument(boolean expression) {
        if (!expression) {
            throw new IllegalArgumentException();
        }
    }
    
    /**
     * Checks a state.
     *
     * @throws IllegalStateException with the formatted message if {@code expression} is false
     */
    public static void checkState(boolean expression, @Nullable String template, @Nullable Object... args) {
        if (!expression) {
            throw new IllegalStateException(lenientFormat(template, args));
        }
    }
    
    /**
     * Checks that a reference is not null.
     *
     * @return {@code reference}
     * @throws NullPointerException with the formatted message if {@code reference} is null
     */
    public static <T> T checkNotNull(T reference, @Nullable String template, @Nullable Object... args) {
        if (reference == null) {
            throw new NullPointerException(lenientFormat(template, args));
        }
        return reference;
    }
    
    /**
     * Checks that a reference is not null.
     *
     * @return {@code reference}
     * @throws NullPointerException if {@code reference} is null
     */
    public static <T> T checkNotNull(T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }
    
    public static boolean isNullOrEmpty(@Nullable String string) {
        return string == null || string.isEmpty();
    }
    
    /**
     * Left-pads {@code string} with {@code padChar} to {@code minLength}.
     */
    public static String padStart(String string, int minLength, char padChar) {
        if (string.length() >= minLength) {
            return string;
        }
        StringBuilder builder = new StringBuilder(minLength);
        for (int i = string.length(); i < minLength; i++) {
            builder.append(padChar);
        }
        return builder.append(string).toString();
    }
    
    /**
     * Replaces each {@code %s} in {@code template} with the next argument; arguments left over are appended in square
     * brackets. The same behavior as Guava's {@code Strings.lenientFormat}.
     */
    public static String lenientFormat(@Nullable String template, @Nullable Object... args) {
        String text = String.valueOf(template);
        if (args == null) {
            args = new Object[] {"(Object[])null"};
        }
        StringBuilder builder = new StringBuilder(text.length() + 16 * args.length);
        int templateStart = 0;
        int i = 0;
        while (i < args.length) {
            int placeholderStart = text.indexOf("%s", templateStart);
            if (placeholderStart == -1) {
                break;
            }
            builder.append(text, templateStart, placeholderStart);
            builder.append(args[i++]);
            templateStart = placeholderStart + 2;
        }
        builder.append(text, templateStart, text.length());
        if (i < args.length) {
            builder.append(" [");
            builder.append(args[i++]);
            while (i < args.length) {
                builder.append(", ");
                builder.append(args[i++]);
            }
            builder.append(']');
        }
        return builder.toString();
    }
    
    /**
     * {@code ClassName{name=value, ...}}, the format of Guava's {@code MoreObjects.toStringHelper}.
     */
    public static String toString(Object self, Object... namesAndValues) {
        StringBuilder builder = new StringBuilder(32).append(self.getClass().getSimpleName()).append('{');
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(namesAndValues[i]).append('=').append(namesAndValues[i + 1]);
        }
        return builder.append('}').toString();
    }
    
    /**
     * A factory of daemon threads with the given name.
     */
    public static java.util.concurrent.ThreadFactory daemonThreadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
