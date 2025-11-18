---
layout: home
title: 通用、灵活、高性能的分布式ID生成器

hero:
  name: "CosId"
  text: "通用、灵活、高性能的分布式ID生成器"
#  tagline: "通用、灵活、高性能的分布式ID生成器"
  image:
    src: /logo.png
    alt: CosId
  actions:
    - theme: brand
      text: 快速上手
      link: /zh/guide/getting-started
    - theme: alt
      text: 简介
      link: /zh/guide/introduction
    - theme: alt
      text: GitHub
      link: https://github.com/Ahoo-Wang/CosId
    - theme: alt
      text: Gitee
      link: https://gitee.com/AhooWang/CosId

features:
- title: 通用
  details: 支持多种类型的分布式ID算法：SnowflakeId、SegmentId、SegmentChainId。 并且支持多种号段分发器、机器号分发器。
- title: 灵活
  details: 通过简单配置即可自定义切换多种算法实现，定制以满足场景需要。
- title: 高性能
  details: 设计极致优化，SegmentChainId 性能可达到近似 AtomicLong 的 TPS 性能:12743W+/s。
---

