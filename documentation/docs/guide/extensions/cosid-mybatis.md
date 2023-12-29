# CosId-MyBatis 模块

> [cosid-mybatis](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-mybatis) 拦截**MyBatis**插入(`Insert`)请求,并解析 `@CosId` 注入**分布式ID**。

## 安装

> Kotlin DSL

``` kotlin
    implementation("me.ahoo.cosid:cosid-mybatis:${cosidVersion}")
```

```java
public class Order {

    @CosId(value = "order")
    private Long orderId;
    private Long userId;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
```

```java
@Mapper
public interface OrderRepository {
    @Insert("insert into t_table (id) value (#{id});")
    void insert(LongIdEntity order);

    @Insert({
            "<script>",
            "insert into t_friendly_table (id)",
            "VALUES" +
                    "<foreach item='item' collection='list' open='' separator=',' close=''>" +
                    "(#{item.id})" +
                    "</foreach>",
            "</script>"})
    void insertList(List<FriendlyIdEntity> list);
}
```

```java
        LongIdEntity entity=new LongIdEntity();
        entityRepository.insert(entity);
        /**
         * {
         *   "id": 208796080181248
         * }
         */
        return entity;
```
