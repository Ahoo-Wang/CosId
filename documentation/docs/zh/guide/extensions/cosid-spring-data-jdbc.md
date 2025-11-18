# CosId-Spring-Data-Jdbc 模块

[cosid-spring-data-jdbc](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-spring-data-jdbc) 模块提供了对 `org.springframework.data.annotation.Id` 注解的支持，支持自动注入**分布式ID**。

## 安装

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

## 使用

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
```java [named 'id']
    static class NamedIdEntity {
        
        private long id;
        
        public long getId() {
            return id;
        }
        
        public NotFoundEntity setId(int id) {
            this.id = id;
            return this;
        }
    }
```
:::
