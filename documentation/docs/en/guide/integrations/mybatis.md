# MyBatis

`CosIdInterceptor` (artifact `cosid-mybatis`, package `me.ahoo.cosid.mybatis`) fills the `@CosId` field of an entity before an INSERT. It also fills
each entity of a batch passed under `listKey` (default `list`).

```xml
<insert id="insertAll">
    insert into t_order (id, item) values
    <foreach collection="list" item="order" separator=",">(#{order.id}, #{order.item})</foreach>
</insert>
```

It fills only two kinds of parameter, as in 4.x:

| The INSERT's parameter | Filled |
|---|---|
| the entity itself: `insert(Order order)` | yes |
| the collection under `listKey` in MyBatis' parameter map: `insertAll(List<Order> orders)`, `insertAll(@Param("list") List<Order> orders)` | each entity |
| anything else in the parameter map: `insert(@Param("order") Order order)`, a collection under another name | no |

So pass a single entity without `@Param`, and a batch under the configured `listKey`.

With Spring Boot the interceptor is a bean, on whenever MyBatis is on the classpath:

| Property | Default |
|---|---|
| `cosid.mybatis.enabled` | `true` |
| `cosid.mybatis.list-key` | `list` |

Without Spring: `configuration.addInterceptor(new CosIdInterceptor(runtime.filler(), "list"))`.
