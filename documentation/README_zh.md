# CosId 文档

CosId 文档站点的源代码仓库。本仓库包含 [CosId](https://github.com/Ahoo-Wang/CosId) 的文档内容，CosId 是一个通用、灵活、高性能的分布式 ID 生成器。

## 快速开始

```bash
pnpm install
pnpm run docs:dev
```

## 可用脚本

```sh
pnpm run docs:dev   # 启动开发服务器，支持热更新
pnpm run docs:build # 构建生产环境文档
pnpm run docs:preview # 本地预览生产构建
```

## 文档结构

```
docs/
├── en/                      # 英文文档
│   ├── guide/               # 入门指南、教程
│   │   ├── getting-started.md
│   │   ├── introduction.md
│   │   ├── cosid-generator.md
│   │   ├── segment.md
│   │   ├── segment-chain.md
│   │   ├── snowflake.md
│   │   ├── id-converter.md
│   │   ├── provider.md
│   │   ├── advanced/
│   │   ├── extensions/
│   │   ├── faq/
│   │   └── sharding/
│   ├── reference/           # 配置参考
│   │   └── config/
│   ├── public/             # 静态资源（图片、logo 等）
│   └── index.md            # 首页
└── zh/                     # 中文文档
    ├── guide/
    ├── reference/
    ├── public/
    └── index.md
```

## 如何贡献

欢迎贡献文档改进！详情请参阅 CosId 主项目的 [贡献指南](https://github.com/Ahoo-Wang/CosId/blob/main/CONTRIBUTING.md)。

## 链接

- [CosId 主仓库](https://github.com/Ahoo-Wang/CosId)
- [在线文档](https://cosid.ahoo.me/)
- [Gitee 镜像](https://gitee.com/AhooWang/CosId)
