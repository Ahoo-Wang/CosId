# CosIdGenerator
> CosId生成器

## 特点介绍

- 全局趋势增加
- 局部单调递增
- 高性能:15,570,085 ops/s(generateAsString),3倍于 ```UUID.randomUUID()```
- 反向解析ID状态（时间戳，机器号，序列号）
- 易于扩展
- 更小的存储空间：15个字符

## 设计
<p align="center">
  <img :src="$withBase('/assets/design/CosIdGenerator.png')" alt="IdGenerator design diagram"/>
</p>

## Radix36CosIdGenerator
>36进制CosId生成器
> 
>[timestamp(44)]-[machineId(20)]-[sequence(16)] = 80 BITS = 17 CHARS=[timestamp(8)]-[machineId(4)]-[sequence(3)].

## Radix62CosIdGenerator
> 62进制CosId生成器
> [timestamp(44)]-[machineId-(20)]-[sequence-(16)] = 80 BITS = 15 CHARS=[timestamp(9)]-[machineId(4)]-[sequence(4)]
