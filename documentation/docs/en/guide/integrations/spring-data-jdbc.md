# CosId-Spring-Data-Jdbc Module

The [cosid-spring-data-jdbc](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-spring-data-jdbc) module provides support for the `org.springframework.data.annotation.Id` annotation and supports automatic injection of **distributed IDs**.

::: warning 4.0 requires `cosid.jdbc.enabled=true`
Since 4.0 the integration is off by default. With the starter, set `cosid.jdbc.enabled=true`, or CosId does not fill `@Id` and `@CosId` fields of Spring Data JDBC entities (an INFO at startup names them).
:::

## Installation

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("me.ahoo.cosid:cosid-spring-data-jdbc:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-spring-data-jdbc</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::


::: tip `cosid-accessor`
`@CosId` and the id field accessors are in the `me.ahoo.cosid:cosid-accessor` artifact (since 4.0), which this module brings in. Use it directly only to fill `@CosId` fields without MyBatis, Spring Data JDBC or the starter.
:::

## Usage

::: code-group
```java [@Id]
    static class IdEntity {
        @Id
        private long id;
        
        public long getId() {
            return id;
        }
        
        public IdEntity setId(int id) {
            this.id = id;
            return this;
        }
    }
```
```java [@CosId]
    static class IdEntity {
        @CosId
        private long id;
        
        public long getId() {
            return id;
        }
        
        public IdEntity setId(int id) {
            this.id = id;
            return this;
        }
    }
```
:::
