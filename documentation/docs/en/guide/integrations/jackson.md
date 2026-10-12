# CosId-Jackson Module

**Jackson** serialization/deserialization annotation plugin, which isolates the *ID* usage methods inside and outside the application API boundary. The application internally uses `long`, externally uses `String`, achieving non-intrusive and imperceptible effects.

::: danger JavaScript Number Overflow Issue

`JavaScript`'s `Number.MAX_SAFE_INTEGER` is only **53-bit**. If a 63-bit `SnowflakeId` is directly returned to the frontend, it will cause value overflow (so we should know that the overflow issue of `long` values passed from backend to frontend will appear sooner or later, but `SnowflakeId` appears faster).

Obviously, overflow is unacceptable. Generally, the following solutions can be used:

- Directly convert `long` to `String` (`@AsString(AsString.Type.TO_STRING)`)
- Use `SnowflakeFriendlyId` to convert `SnowflakeId` to a more friendly string representation: `{timestamp}-{machineId}-{sequence} -> 20210623131730192-1-0` (`@AsString(AsString.Type.FRIENDLY_ID)`)
- Customize `SnowflakeId` bit allocation to shorten the bit length of `SnowflakeId` (**53-bit**) so that **ID** does not overflow when provided to the frontend (`SafeJavaScriptSnowflakeId`)
- Use `Radix62IdConverter` to convert `long` type **ID** and compress the string. (`@AsString(AsString.Type.RADIX)`)

The [cosid-jackson](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-jackson) module provides minimal invasiveness for the above solutions.
:::

## Installation

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

## Usage

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

**Serialization Result**

```json
{
  "id": "266300479548424192",
  "radixId": "JferHIEYZk",
  "radixPadStartId": "0JferHIEYZk",
  "radixPadStartCharSize10Id": "JferHIEYZk",
  "friendlyId": "20211228202301948-0-0"
}
```

