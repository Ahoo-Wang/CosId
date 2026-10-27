# Axon

`CosIdIdentifierFactory`（artifact `cosid-axon`，包 `me.ahoo.cosid.axon`）以 CosId 文本 ID 提供 Axon 4 的标识符。已在 Axon 4.13 上测试。
Axon 5 没有 `IdentifierFactory`。

Axon 通过 `ServiceLoader` 创建 `IdentifierFactory`，并保存在 JVM 级的静态变量中，因此无法向该工厂传入运行时。
它改为持有一个静态的**绑定**栈，这是 CosId 5 中唯一的静态状态：

- `CosIdIdentifierFactory.bind(runtime, generator)` 压入一个绑定，由最近的绑定提供标识符。
  关闭返回的绑定只移除该绑定本身，因此关闭多个 Spring 上下文中的一个时，其他上下文仍保持绑定。
- Spring Boot 在上下文启动后立即绑定其运行时，在上下文关闭前立即解除绑定。生成器取
  `cosid.axon.generator-name`，否则取系统属性 `cosid.axon`，否则为 `default`。
- 不使用 Spring 时：`try (var binding = CosIdIdentifierFactory.bind(runtime, null)) { … }`，或者在 Axon 接受工厂的地方传入
  `new CosIdIdentifierFactory(runtime, generator)`。
- 没有任何绑定时，工厂抛出 `IllegalStateException`，并说明如何绑定运行时，与 4.x 行为一致。
