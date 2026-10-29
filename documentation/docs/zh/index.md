---
layout: home
title: 通用、灵活、高性能的分布式 ID 生成器

hero:
  name: "CosId"
  text: "通用、灵活、高性能的分布式 ID 生成器"
  tagline: "CosId 5：唯一性不依赖任何时钟，一个运行时承载所有策略，只有一个 starter"
  image:
    src: /logo.png
    alt: CosId
  actions:
    - theme: brand
      text: 快速开始
      link: /zh/guide/getting-started
    - theme: alt
      text: 简介
      link: /zh/guide/introduction
    - theme: alt
      text: 从 4.x 迁移
      link: /zh/guide/migration/from-4x
    - theme: alt
      text: 4.x 文档
      link: https://github.com/Ahoo-Wang/CosId/tree/4.x/documentation/docs/zh
    - theme: alt
      text: GitHub
      link: https://github.com/Ahoo-Wang/CosId

features:
- title: 构造即唯一
  icon: 🛡️
  details: "机器号槽位以时间窗口的形式租用：相继持有者的窗口永不重叠，这条规则在存储内部维护。时钟偏差、GC 停顿与故障切换只影响可用性，绝不影响唯一性。"
- title: 全部策略
  icon: 🌐
  details: "Snowflake（任意 Layout，毫秒或秒，JS 安全）、80 位 CosId、按时间预算自动调整步长的号段，以及按年、月、日分期的周期号段。"
- title: 一个运行时
  icon: 🧩
  details: "Spring Boot、cosid-server 与纯 Java 都从同一套配置模型启动同一个 CosIdRuntime；存储只声明一次，可自由混用。"
- title: 五种存储，一份契约
  icon: 🗄️
  details: "JDBC（MySQL、MariaDB、PostgreSQL、SQL Server、H2）、Redis、MongoDB、ZooKeeper 与远程服务实现同样的两个原语，由同一套 TCK 验证。"
- title: 高性能
  icon: 🚀
  details: "热路径上没有锁、没有 I/O：号段 ID 是一次原子加，Snowflake ID 是一个短临界区。5.0 以每项基准都不低于 4.x 作为发布门槛。"
- title: 可观测
  icon: 📈
  details: "19 个 Micrometer 指标、一个槽位租约健康检查，以及一个可描述并试用每个生成器的 Actuator 端点。"
---

::: warning 候选发布版
`5.0.0-rc.x` 是候选发布版。从 RC1 起,Java API、`cosid.*` 配置以及存储格式与协议冻结，之后的 5.x 只做向后兼容的增量。生产环境仍建议等待 GA,在此之前请继续使用 4.x（[4.x 文档](https://github.com/Ahoo-Wang/CosId/tree/4.x/documentation/docs/zh)）。
:::
