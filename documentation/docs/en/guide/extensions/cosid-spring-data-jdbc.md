# CosId-Spring-Data-Jdbc Module

The [cosid-spring-data-jdbc](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-spring-data-jdbc) module provides support for the `org.springframework.data.annotation.Id` annotation and supports automatic injection of **distributed IDs**.

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
