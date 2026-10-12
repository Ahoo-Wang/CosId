# CosId-Jackson 模块

**Jackson** 序列化/反序列化注解插件，相当于隔离了应用API边界内外的 *ID* 使用方式，应用内部使用 `long`、外部使用 `String`，做到了应用无侵入，无感知。

::: danger JavaScript Number 溢出问题

`JavaScript` 的 `Number.MAX_SAFE_INTEGER` 只有**53-bit**，如果直接将63位的 `SnowflakeId` 返回给前端，那么会产生值溢出的情况（所以这里我们应该知道后端传给前端的 `long` 值溢出问题，迟早会出现，只不过`SnowflakeId`出现得更快而已）。 
很显然溢出是不能被接受的，一般可以使用以下处理方案：

- 直接将 `long` 转换成 `String` (`@AsString(AsString.Type.TO_STRING)`)
- 使用 `SnowflakeFriendlyId` 将 `SnowflakeId` 转换成比较友好的字符串表示：`{timestamp}-{machineId}-{sequence} -> 20210623131730192-1-0` (`@AsString(AsString.Type.FRIENDLY_ID)`)
- 自定义 `SnowflakeId` 位分配来缩短 `SnowflakeId` 的位数（**53-bit**）使 **ID** 提供给前端时不溢出(`SafeJavaScriptSnowflakeId`)
- 使用 `Radix62IdConverter` 转换 `long` 类型的 **ID**，并且压缩字符串。(`@AsString(AsString.Type.RADIX)`)

[cosid-jackson](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-jackson) 模块为提供上述方案提供了最小的侵入性。
:::

## 安装

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("me.ahoo.cosid:cosid-jackson:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-jackson</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

## 使用

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

