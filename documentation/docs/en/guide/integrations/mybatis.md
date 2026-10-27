# MyBatis

`CosIdInterceptor` (artifact `cosid-mybatis`, package `me.ahoo.cosid.mybatis`) fills the `@CosId` field of an entity before an INSERT. It also fills
each entity of a batch passed under `listKey` (default `list`).

```xml
<insert id="insertAll">
    insert into t_order (id, item) values
    <foreach collection="list" item="order" separator=",">(#{order.id}, #{order.item})</foreach>
</insert>
```

With Spring Boot the interceptor is a bean, on whenever MyBatis is on the classpath:

| Property | Default |
|---|---|
| `cosid.mybatis.enabled` | `true` |
| `cosid.mybatis.list-key` | `list` |

Without Spring: `configuration.addInterceptor(new CosIdInterceptor(runtime.filler(), "list"))`.
