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

package me.ahoo.cosid.store.remote;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The little JSON the client needs, so that {@code store/remote} has no third-party dependency (06-stores.md §2.5):
 * objects, arrays, strings, booleans, {@code null} and numbers. Integral numbers stay exact {@code long}s, so ranges
 * beyond 2^53 lose nothing.
 */
final class Json {
    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    /**
     * Writes an object.
     *
     * @param fields values are {@code String}, {@code Number}, {@code Boolean} or {@code null}
     * @return the JSON text
     */
    static String write(Map<String, ?> fields) {
        StringBuilder out = new StringBuilder(128).append('{');
        boolean first = true;
        for (Map.Entry<String, ?> field : fields.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            string(out, field.getKey());
            out.append(':');
            Object value = field.getValue();
            if (value == null || value instanceof Number || value instanceof Boolean) {
                out.append(value);
            } else {
                string(out, value.toString());
            }
        }
        return out.append('}').toString();
    }

    private static void string(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /**
     * Reads a JSON object.
     *
     * @param text the text
     * @return the object; nested objects are maps, arrays lists, integral numbers {@code Long}, others {@code BigDecimal}
     * @throws IllegalArgumentException if it is not a JSON object
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> readObject(String text) {
        Json json = new Json(text);
        json.skipSpace();
        Object value = json.value();
        json.skipSpace();
        if (json.at != text.length() || !(value instanceof Map)) {
            throw new IllegalArgumentException("Not a JSON object: " + abbreviate(text));
        }
        return (Map<String, Object>) value;
    }

    private static String abbreviate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    private Object value() {
        if (at >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(at);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> readString();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        at++;
        Map<String, Object> object = new LinkedHashMap<>();
        skipSpace();
        if (peek('}')) {
            at++;
            return object;
        }
        while (true) {
            skipSpace();
            if (!peek('"')) {
                throw error("expected a field name");
            }
            final String name = readString();
            skipSpace();
            expect(':');
            skipSpace();
            object.put(name, value());
            skipSpace();
            if (peek(',')) {
                at++;
                continue;
            }
            expect('}');
            return object;
        }
    }

    private List<Object> array() {
        at++;
        List<Object> array = new ArrayList<>();
        skipSpace();
        if (peek(']')) {
            at++;
            return array;
        }
        while (true) {
            skipSpace();
            array.add(value());
            skipSpace();
            if (peek(',')) {
                at++;
                continue;
            }
            expect(']');
            return array;
        }
    }

    private String readString() {
        at++;
        StringBuilder out = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (at >= text.length()) {
                break;
            }
            char escaped = text.charAt(at++);
            switch (escaped) {
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length()) {
                        throw error("bad unicode escape");
                    }
                    out.append((char) Integer.parseInt(text, at, at + 4, 16));
                    at += 4;
                }
                default -> out.append(escaped);
            }
        }
        throw error("unterminated string");
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw error("unexpected token");
        }
        at += word.length();
        return value;
    }

    private Object number() {
        int start = at;
        boolean integral = true;
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c == '.' || c == 'e' || c == 'E') {
                integral = false;
            } else if (!(c == '-' || c == '+' || (c >= '0' && c <= '9'))) {
                break;
            }
            at++;
        }
        if (start == at) {
            throw error("unexpected character");
        }
        String number = text.substring(start, at);
        try {
            return integral ? (Object) Long.parseLong(number) : new BigDecimal(number);
        } catch (NumberFormatException e) {
            throw error("bad number " + number);
        }
    }

    private boolean peek(char c) {
        return at < text.length() && text.charAt(at) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("expected '" + c + "'");
        }
        at++;
    }

    private void skipSpace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private IllegalArgumentException error(String problem) {
        return new IllegalArgumentException("Bad JSON at " + at + " (" + problem + "): " + abbreviate(text));
    }
}
