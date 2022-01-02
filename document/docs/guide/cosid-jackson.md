# CosId-Jackson 模块

> **Jackson** 序列化/反序列化注解插件

## 安装

> Kotlin DSL

``` kotlin
    implementation("me.ahoo.cosid:cosid-jackson:${cosidVersion}")
```

```java
public class AsStringDto {

    @AsString
    private Long id;

    @AsString(AsString.Type.RADIX)
    private Long radixId;

    @AsString(value = AsString.Type.RADIX, radixPadStart = true)
    private Long radixPadStartId;

    @AsString(value = AsString.Type.RADIX, radixPadStart = true, radixCharSize = 10)
    private Long radixPadStartCharSize10Id;

    @AsString(AsString.Type.FRIENDLY_ID)
    private long friendlyId;

   // getter / setter
}
```

**序列化结果**

```json
{
  "id": "266300479548424192",
  "radixId": "JferHIEYZk",
  "radixPadStartId": "0JferHIEYZk",
  "radixPadStartCharSize10Id": "JferHIEYZk",
  "friendlyId": "20211228202301948-0-0"
}
```
