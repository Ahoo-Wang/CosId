# Jackson

`cosid-jackson`（包 `me.ahoo.cosid.jackson`）为 **Jackson 3**（`tools.jackson`）提供 `@AsString`：`long` ID 以文本写出，并从文本读回，
供无法容纳 63 位数字的 JavaScript 客户端使用。

```java
@AsString long id;                                                    // radix 62, 11 digits, padded (as in 4.x)
@AsString(generator = "order") long orderId;                          // the codec of generator "order"
@AsString(codec = Codec.TO_STRING) long a;                            // decimal text
@AsString(radix = 36, width = 13, padStart = false) long b;
@AsString(codec = Codec.FRIENDLY, layout = Preset.SNOWFLAKE_S, zone = "Asia/Shanghai") long c;
@AsString(codec = Codec.FRIENDLY, timeBits = 41, nodeBits = 5, seqBits = 17, epoch = 1700000000000L) long d;
```

- `generator = …` 与内联属性互斥。
- 内联编解码器不需要注册 module。`generator = …` 需要在 `JsonMapper` 上注册 `new CosIdJacksonModule(runtime)`；
  Spring Boot 会注册一个 `CosIdJacksonModule` Bean。
- 读取时接受文本、该编解码器可能写出的 JSON 数字，空字符串视为 `null`。
