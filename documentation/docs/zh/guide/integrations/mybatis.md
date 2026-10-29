# MyBatis

`CosIdInterceptor`（artifact `cosid-mybatis`，包 `me.ahoo.cosid.mybatis`）在 INSERT 之前填充实体的 `@CosId` 字段。对于以 `listKey`（默认 `list`）
传入的批量参数，它也会填充其中的每个实体。

```xml
<insert id="insertAll">
    insert into t_order (id, item) values
    <foreach collection="list" item="order" separator=",">(#{order.id}, #{order.item})</foreach>
</insert>
```

与 4.x 相同，它只填充两种参数：

| INSERT 的参数 | 是否填充 |
|---|---|
| 实体本身：`insert(Order order)` | 是 |
| MyBatis 参数 Map 中 `listKey` 下的集合：`insertAll(List<Order> orders)`、`insertAll(@Param("list") List<Order> orders)` | 逐个填充 |
| 参数 Map 中的其他值：`insert(@Param("order") Order order)`、以其他名字传入的集合 | 否 |

所以单个实体不要加 `@Param`，批量插入使用配置的 `listKey`。

使用 Spring Boot 时拦截器是一个 Bean，只要 MyBatis 位于 classpath 上就启用：

| 属性 | 默认值 |
|---|---|
| `cosid.mybatis.enabled` | `true` |
| `cosid.mybatis.list-key` | `list` |

不使用 Spring 时：`configuration.addInterceptor(new CosIdInterceptor(runtime.filler(), "list"))`。
