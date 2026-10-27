# Jackson

`cosid-jackson` (package `me.ahoo.cosid.jackson`) brings `@AsString` for **Jackson 3** (`tools.jackson`): a `long` id is written as text and read
back from it, for JavaScript clients that cannot hold 63-bit numbers.

```java
@AsString long id;                                                    // radix 62, 11 digits, padded (as in 4.x)
@AsString(generator = "order") long orderId;                          // the codec of generator "order"
@AsString(codec = Codec.TO_STRING) long a;                            // decimal text
@AsString(radix = 36, width = 13, padStart = false) long b;
@AsString(codec = Codec.FRIENDLY, layout = Preset.SNOWFLAKE_S, zone = "Asia/Shanghai") long c;
@AsString(codec = Codec.FRIENDLY, timeBits = 41, nodeBits = 5, seqBits = 17, epoch = 1700000000000L) long d;
```

- `generator = …` and the inline attributes are exclusive.
- The inline codecs need no module. `generator = …` needs `new CosIdJacksonModule(runtime)` on the `JsonMapper`;
  Spring Boot registers a `CosIdJacksonModule` bean.
- Reading accepts the text, a JSON number the codec could have written, and an empty string as `null`.
